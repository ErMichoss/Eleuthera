package com.tuapp.eleuthera.playback

import com.tuapp.eleuthera.platform.DesktopPaths
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Dónde está ffmpeg. Primero se busca en la carpeta de la app (bin/), donde lo dejará el
 * descargador de la fase 4; si no, se usa el que tenga instalado el sistema.
 */
object Ffmpeg {
    private val exe = if (DesktopPaths.isWindows) "ffmpeg.exe" else "ffmpeg"

    /**
     * Ruta de ffmpeg, o null si no está en ninguna parte. Orden de búsqueda:
     * 1. bin/ de la carpeta de la app (donde lo pondrá el descargador).
     * 2. El PATH del sistema.
     * 3. Las carpetas donde lo dejan los instaladores habituales (winget, Homebrew, apt...).
     *    Así se encuentra aunque la app se abriera antes de instalarlo y su PATH esté viejo.
     */
    fun find(): String? {
        found?.let { if (it == "ffmpeg" || File(it).canExecute()) return it }
        val path = File(DesktopPaths.dataDir, "bin/$exe").takeIf { it.canExecute() }?.absolutePath
            ?: "ffmpeg".takeIf { runs(it) }
            ?: knownLocations().firstOrNull { it.isFile && it.canExecute() }?.absolutePath
        found = path
        return path
    }

    /**
     * Última ruta encontrada. Se guarda para no lanzar "ffmpeg -version" cada vez (la
     * biblioteca llama a ffmpeg miles de veces); solo se recuerda si se encontró.
     */
    @Volatile private var found: String? = null

    private fun knownLocations(): List<File> {
        val env = System.getenv()
        return when {
            DesktopPaths.isWindows -> buildList {
                env["LOCALAPPDATA"]?.let { local ->
                    // winget: acceso directo en Links y la instalación real en Packages.
                    add(File(local, "Microsoft/WinGet/Links/ffmpeg.exe"))
                    File(local, "Microsoft/WinGet/Packages").listFiles()
                        ?.filter { it.name.contains("FFmpeg", ignoreCase = true) }
                        ?.forEach { pkg ->
                            pkg.walkTopDown().maxDepth(4)
                                .filter { it.name.equals("ffmpeg.exe", ignoreCase = true) }
                                .forEach(::add)
                        }
                }
                env["ProgramFiles"]?.let { add(File(it, "ffmpeg/bin/ffmpeg.exe")) }
                add(File("C:/ffmpeg/bin/ffmpeg.exe"))
                env["ChocolateyInstall"]?.let { add(File(it, "bin/ffmpeg.exe")) }
                env["USERPROFILE"]?.let { add(File(it, "scoop/shims/ffmpeg.exe")) }
            }
            DesktopPaths.isMac -> listOf(File("/opt/homebrew/bin/ffmpeg"), File("/usr/local/bin/ffmpeg"))
            else -> listOf(File("/usr/bin/ffmpeg"), File("/usr/local/bin/ffmpeg"), File("/snap/bin/ffmpeg"))
        }
    }

    private fun runs(command: String): Boolean = runCatching {
        val p = ProcessBuilder(command, "-version").redirectErrorStream(true).start()
        p.inputStream.readAllBytes()
        p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0
    }.getOrDefault(false)

    private val DURATION = Regex("""Duration:\s*(\d+):(\d+):(\d+(?:\.\d+)?)""")

    /**
     * Lo que ffmpeg cuenta de un archivo: sus etiquetas (claves en minúsculas: title, artist,
     * album, album_artist...), su duración y si trae carátula incrustada.
     */
    class Probe(val tags: Map<String, String>, val durationMs: Long, val hasCover: Boolean) {
        fun tag(vararg keys: String): String? =
            keys.firstNotNullOfOrNull { k -> tags[k]?.trim()?.takeIf { it.isNotEmpty() } }
    }

    /** Lee la cabecera de [file] (no decodifica el audio: tarda unas décimas). null si falla. */
    fun probe(file: File): Probe? {
        val ffmpeg = find() ?: return null
        val out = runCatching { output(ffmpeg, "-hide_banner", "-nostdin", "-i", file.absolutePath) }
            .getOrNull() ?: return null
        if (!out.contains("Input #0")) return null   // no es un archivo multimedia
        return Probe(
            tags = parseTags(out),
            durationMs = parseDuration(out),
            hasCover = out.contains("(attached pic)"),
        )
    }

    /** Duración de [file] en ms (0 si no se sabe). */
    fun durationMs(file: File): Long = probe(file)?.durationMs ?: 0

    /**
     * Guarda en [out] (jpg) la carátula de [source] reducida a [size] px como mucho. [source]
     * puede ser una canción con carátula incrustada o una imagen (cover.jpg de la carpeta).
     */
    fun extractCover(source: File, out: File, size: Int = 300): Boolean {
        val ffmpeg = find() ?: return false
        out.parentFile?.mkdirs()
        val tmp = File(out.parentFile, out.name + ".tmp.jpg")
        val ok = runCatching {
            output(
                ffmpeg, "-hide_banner", "-nostdin", "-y", "-loglevel", "error",
                "-i", source.absolutePath,
                "-an", "-map", "0:v:0", "-frames:v", "1",
                "-vf", "scale=$size:$size:force_original_aspect_ratio=decrease",
                "-q:v", "3", tmp.absolutePath,
            )
            tmp.isFile && tmp.length() > 0
        }.getOrDefault(false)
        if (ok) tmp.renameTo(out) else tmp.delete()
        return ok && out.isFile
    }

    private fun output(vararg command: String, timeoutSeconds: Long = 20): String {
        val p = ProcessBuilder(*command).redirectErrorStream(true).start()
        val text = p.inputStream.bufferedReader(Charsets.UTF_8).readText()
        if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) p.destroyForcibly()
        return text
    }

    private fun parseDuration(out: String): Long {
        val m = DURATION.find(out) ?: return 0
        val (h, min, s) = m.destructured
        return (h.toLong() * 3600 + min.toLong() * 60) * 1000 + (s.toDouble() * 1000).toLong()
    }

    /**
     * Las líneas "clave : valor" de los bloques "Metadata:". Primero vienen las del archivo y
     * luego las de cada pista (en opus/ogg las etiquetas van en la pista); manda la primera.
     */
    private fun parseTags(out: String): Map<String, String> {
        val tags = LinkedHashMap<String, String>()
        var blockIndent = -1
        for (line in out.lineSequence()) {
            val indent = line.indexOfFirst { it != ' ' }.let { if (it < 0) line.length else it }
            if (line.trim() == "Metadata:") {
                blockIndent = indent
                continue
            }
            if (blockIndent >= 0 && indent > blockIndent) {
                val sep = line.indexOf(':')
                if (sep > 0) {
                    val key = line.substring(0, sep).trim().lowercase()
                    val value = line.substring(sep + 1).trim()
                    if (key.isNotEmpty() && value.isNotEmpty()) tags.putIfAbsent(key, value)
                }
            } else {
                blockIndent = -1
            }
        }
        return tags
    }
}