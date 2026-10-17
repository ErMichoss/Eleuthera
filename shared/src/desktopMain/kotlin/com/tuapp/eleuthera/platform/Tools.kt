package com.tuapp.eleuthera.platform

import com.tuapp.eleuthera.playback.Ffmpeg
import java.io.File
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Programas externos que necesita la app de escritorio, y su instalación automática en
 * DesktopPaths.dataDir/bin (no hace falta instalar nada a mano ni tocar el PATH):
 *
 * - yt-dlp: buscar y descargar. Se actualiza solo en cada arranque (como en Android).
 * - ffmpeg + ffprobe: reproducir, leer etiquetas y convertir el audio descargado.
 * - Deno: yt-dlp lo usa para resolver los vídeos de YouTube. Opcional: sin él algunas
 *   descargas pueden fallar, pero el resto de la app funciona.
 *
 * Si el sistema ya tiene alguno instalado (PATH, winget, Homebrew...) se usa ese.
 */
object Tools {

    data class State(
        val ytDlp: String? = null,
        val ffmpeg: String? = null,
        val deno: String? = null,
        /** Qué se está haciendo ahora ("Descargando ffmpeg…"), o null. */
        val working: String? = null,
        /** 0..1 durante una descarga; null si no se sabe. */
        val progress: Float? = null,
        val error: String? = null,
    ) {
        val ready get() = ytDlp != null && ffmpeg != null
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    val binDir: File get() = File(DesktopPaths.dataDir, "bin").apply { mkdirs() }
    private val cacheDir: File get() = File(DesktopPaths.dataDir, "cache/tools").apply { mkdirs() }

    private val exe = if (DesktopPaths.isWindows) ".exe" else ""
    private val arm = System.getProperty("os.arch").lowercase().let { "aarch64" in it || "arm64" in it }
    private val mutex = Mutex()

    @Volatile private var ytDlpPath: String? = null
    @Volatile private var denoPath: String? = null

    /** Ruta de yt-dlp, o null si no está en ninguna parte. */
    fun ytDlp(): String? = ytDlpPath ?: locate("yt-dlp", ytDlpKnown()).also { ytDlpPath = it }

    /** Ruta de Deno, o null. */
    fun deno(): String? = denoPath ?: locate("deno", denoKnown()).also { denoPath = it }

    /** Si es nuestra copia (en bin/), que es la que sabemos que está al día. */
    fun isBundled(path: String?): Boolean = path != null && File(path).parentFile == binDir

    /**
     * Comprueba que estén yt-dlp y ffmpeg y descarga lo que falte. Se llama al arrancar y
     * desde el botón "Reintentar". Nunca lanza excepciones: los fallos van a [state].
     */
    suspend fun ensureInstalled() {
        mutex.withLock { ensureInstalledLocked() }
    }

    private suspend fun ensureInstalledLocked() {
        withContext(Dispatchers.IO) {
            _state.update { it.copy(error = null) }
            try {
                if (Ffmpeg.find() == null) installFfmpeg()
                val yt = ytDlp()
                if (yt == null) installYtDlp() else if (isBundled(yt)) updateYtDlp()
                if (deno() == null) {
                    // Opcional: si falla, se sigue sin él.
                    runCatching { installDeno() }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: e.toString()) }
            } finally {
                ytDlpPath = null
                denoPath = null
                _state.update {
                    it.copy(ytDlp = ytDlp(), ffmpeg = Ffmpeg.find(), deno = deno(), working = null, progress = null)
                }
            }
        }
    }

    /** "yt-dlp -U" sobre nuestra copia. Devuelve lo que contesta (p. ej. "yt-dlp is up to date"). */
    suspend fun updateYtDlp(): String = withContext(Dispatchers.IO) {
        val yt = ytDlp()?.takeIf { isBundled(it) } ?: return@withContext "yt-dlp no lo gestiona la app"
        _state.update { it.copy(working = "Actualizando yt-dlp…", progress = null) }
        try {
            exec(listOf(yt, "-U"), timeoutSeconds = 120).lines().lastOrNull { it.isNotBlank() }.orEmpty()
        } catch (e: Exception) {
            "No se pudo actualizar yt-dlp: ${e.message}"
        } finally {
            _state.update { it.copy(working = null) }
        }
    }

    // ---- Instalación ----

    private fun installYtDlp() {
        val asset = when {
            DesktopPaths.isWindows -> "yt-dlp.exe"
            DesktopPaths.isMac -> "yt-dlp_macos"
            arm -> "yt-dlp_linux_aarch64"
            else -> "yt-dlp_linux"
        }
        val dest = File(binDir, "yt-dlp$exe")
        download("https://github.com/yt-dlp/yt-dlp/releases/latest/download/$asset", dest, "yt-dlp")
        dest.setExecutable(true)
        ytDlpPath = null
    }

    private fun installFfmpeg() {
        val btbn = "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest"
        when {
            DesktopPaths.isWindows -> {
                val zip = File(cacheDir, "ffmpeg.zip")
                val name = if (arm) "ffmpeg-master-latest-winarm64-gpl.zip" else "ffmpeg-master-latest-win64-gpl.zip"
                download("$btbn/$name", zip, "ffmpeg")
                unzip(zip, setOf("ffmpeg.exe", "ffprobe.exe"))
                zip.delete()
            }
            DesktopPaths.isMac -> {
                // No hay compilaciones oficiales para Mac; estas son las que enlaza ffmpeg.org.
                val arch = if (arm) "arm64" else "amd64"
                for (tool in listOf("ffmpeg", "ffprobe")) {
                    val zip = File(cacheDir, "$tool.zip")
                    download("https://ffmpeg.martin-riedl.de/redirect/latest/macos/$arch/release/$tool.zip", zip, tool)
                    unzip(zip, setOf(tool))
                    zip.delete()
                }
            }
            else -> {
                val archive = File(cacheDir, "ffmpeg.tar.xz")
                val name = if (arm) "ffmpeg-master-latest-linuxarm64-gpl.tar.xz" else "ffmpeg-master-latest-linux64-gpl.tar.xz"
                download("$btbn/$name", archive, "ffmpeg")
                val out = File(cacheDir, "ffmpeg-extract").apply { deleteRecursively(); mkdirs() }
                _state.update { it.copy(working = "Descomprimiendo ffmpeg…", progress = null) }
                exec(listOf("tar", "-xJf", archive.absolutePath, "-C", out.absolutePath), timeoutSeconds = 300)
                for (tool in listOf("ffmpeg", "ffprobe")) {
                    val found = out.walkTopDown().firstOrNull { it.isFile && it.name == tool }
                        ?: error("El paquete de ffmpeg no trae $tool")
                    found.copyTo(File(binDir, tool), overwrite = true).setExecutable(true)
                }
                out.deleteRecursively()
                archive.delete()
            }
        }
        if (Ffmpeg.find() == null) {
            error(
                "No se pudo instalar ffmpeg. Instálalo a mano " +
                        when {
                            DesktopPaths.isWindows -> "(winget install Gyan.FFmpeg)"
                            DesktopPaths.isMac -> "(brew install ffmpeg)"
                            else -> "(sudo apt install ffmpeg)"
                        } + " y pulsa Reintentar."
            )
        }
    }

    private fun installDeno() {
        val target = when {
            DesktopPaths.isWindows -> "x86_64-pc-windows-msvc"
            DesktopPaths.isMac -> if (arm) "aarch64-apple-darwin" else "x86_64-apple-darwin"
            else -> if (arm) "aarch64-unknown-linux-gnu" else "x86_64-unknown-linux-gnu"
        }
        val zip = File(cacheDir, "deno.zip")
        download("https://github.com/denoland/deno/releases/latest/download/deno-$target.zip", zip, "Deno")
        unzip(zip, setOf("deno$exe"))
        zip.delete()
        denoPath = null
    }

    // ---- Utilidades ----

    private val http: HttpClient by lazy {
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)   // GitHub redirige a su CDN
            .connectTimeout(Duration.ofSeconds(20))
            .proxy(ProxySelector.getDefault())
            .build()
    }

    /** Descarga [url] en [dest], informando del progreso en [state]. */
    private fun download(url: String, dest: File, label: String) {
        _state.update { it.copy(working = "Descargando $label…", progress = 0f) }
        val request = HttpRequest.newBuilder(URI(url)).header("User-Agent", "Eleuthera").GET().build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() !in 200..299) {
            response.body().close()
            error("No se pudo descargar $label (HTTP ${response.statusCode()})")
        }
        val total = response.headers().firstValueAsLong("content-length").orElse(-1L)
        dest.parentFile?.mkdirs()
        val part = File(dest.path + ".part")
        response.body().use { input ->
            part.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                var done = 0L
                var lastPct = -1
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    done += n
                    if (total > 0) {
                        val pct = (done * 100 / total).toInt()
                        if (pct != lastPct) {
                            lastPct = pct
                            _state.update { it.copy(progress = pct / 100f) }
                        }
                    }
                }
            }
        }
        dest.delete()
        if (!part.renameTo(dest)) {
            part.copyTo(dest, overwrite = true)
            part.delete()
        }
    }

    /** Saca de [zip] a bin/ los archivos con esos nombres, estén en la carpeta que estén. */
    private fun unzip(zip: File, names: Set<String>) {
        _state.update { it.copy(working = "Descomprimiendo…", progress = null) }
        val pending = names.toMutableSet()
        ZipInputStream(zip.inputStream().buffered()).use { z ->
            while (true) {
                val entry = z.nextEntry ?: break
                val name = entry.name.substringAfterLast('/')
                if (!entry.isDirectory && name in pending) {
                    val dest = File(binDir, name)
                    dest.outputStream().use { z.copyTo(it) }
                    dest.setExecutable(true)
                    pending -= name
                }
            }
        }
        if (pending.isNotEmpty()) error("El archivo descargado no trae ${pending.joinToString()}")
    }

    private fun locate(name: String, known: List<File>): String? {
        File(binDir, name + exe).takeIf { it.isFile && it.canExecute() }?.let { return it.absolutePath }
        if (runs(name)) return name
        return known.firstOrNull { it.isFile && it.canExecute() }?.absolutePath
    }

    private fun ytDlpKnown(): List<File> {
        val env = System.getenv()
        return when {
            DesktopPaths.isWindows -> listOfNotNull(
                env["LOCALAPPDATA"]?.let { File(it, "Microsoft/WinGet/Links/yt-dlp.exe") },
                env["USERPROFILE"]?.let { File(it, "scoop/shims/yt-dlp.exe") },
            )
            DesktopPaths.isMac -> listOf(File("/opt/homebrew/bin/yt-dlp"), File("/usr/local/bin/yt-dlp"))
            else -> listOf(File("/usr/bin/yt-dlp"), File("/usr/local/bin/yt-dlp"))
        }
    }

    private fun denoKnown(): List<File> {
        val home = System.getProperty("user.home")
        return listOfNotNull(
            File(home, ".deno/bin/deno$exe"),
            File("/opt/homebrew/bin/deno").takeIf { DesktopPaths.isMac },
            File("/usr/local/bin/deno").takeIf { !DesktopPaths.isWindows },
        )
    }

    private fun runs(command: String): Boolean = runCatching {
        val p = ProcessBuilder(command, "--version").redirectErrorStream(true).start()
        p.inputStream.readAllBytes()
        p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0
    }.getOrDefault(false)

    private fun exec(command: List<String>, timeoutSeconds: Long): String {
        val p = ProcessBuilder(command).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            error("${command.first()} no terminó a tiempo")
        }
        if (p.exitValue() != 0) error(out.lines().lastOrNull { it.isNotBlank() } ?: "código ${p.exitValue()}")
        return out
    }
}