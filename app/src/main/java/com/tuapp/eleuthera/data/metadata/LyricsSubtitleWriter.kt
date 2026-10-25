package com.tuapp.eleuthera.data.metadata

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import com.tuapp.eleuthera.data.settings.LibrarySettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class LyricsSubtitleWriter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val librarySettings: LibrarySettings,
) {
    class StaleTrackException : IOException("La canción cambió antes de guardar sus subtítulos.")

    suspend fun readLocal(uri: Uri): String? = withContext(Dispatchers.IO) {
        val destination = destination(uri)
        val baseName = destination.displayName.substringBeforeLast('.', destination.displayName)
        val externalLyrics = try {
            destination.read("$baseName.lrc")?.takeIf(String::isNotBlank)
                ?: destination.read("$baseName.srt")?.takeIf(String::isNotBlank)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "No se pudieron leer subtítulos junto al audio $uri; se probará la copia local.", e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "Sin permiso para leer subtítulos junto al audio $uri; se probará la copia local.", e)
            null
        }
        externalLyrics ?: readPrivateSidecar(uri, baseName, "lrc")
            ?: readPrivateSidecar(uri, baseName, "srt")
    }

    suspend fun save(
        uri: Uri,
        lyrics: String,
        isStillCurrent: () -> Boolean,
    ): String = withContext(Dispatchers.IO) {
        ensureCurrent(isStillCurrent)
        val destination = destination(uri)
        val baseName = destination.displayName.substringBeforeLast('.', destination.displayName)
        val cues = parseLrc(lyrics)
        val outputs = buildList {
            add(SubtitleOutput("$baseName.lrc", "text/plain", lyrics.trim() + "\n"))
            if (cues.isNotEmpty()) {
                add(SubtitleOutput("$baseName.srt", "application/x-subrip", cues.toSrt()))
            }
        }
        if (uri.authority == "media") {
            val saved = outputs.filter { output ->
                ensureCurrent(isStillCurrent)
                val file = privateSidecarFile(uri, output.name)
                if (file.isFile) false else {
                    writePrivateSidecar(file, output.contents)
                    true
                }
            }
            return@withContext if (saved.isEmpty()) {
                "Ya existía una letra local en los datos de Eleuthera."
            } else {
                "Letra guardada en los datos de Eleuthera: " +
                    saved.joinToString(" y ") { ".${it.extension}" } +
                    " (Android no permite crear subtítulos externos dentro de Música)."
            }
        }
        val saved = outputs.mapNotNull { output ->
            ensureCurrent(isStillCurrent)
            if (destination.exists(output.name)) null
            else {
                destination.write(output, isStillCurrent)
                output.extension
            }
        }
        if (saved.isEmpty()) "Ya existía una letra local junto al archivo."
        else "Letra guardada junto al archivo: ${saved.joinToString(" y ") { ".$it" }}."
    }

    private suspend fun destination(uri: Uri): SubtitleDestination =
        if (uri.authority == "media") mediaStoreDestination(uri)
        else documentTreeDestination(uri)

    private fun readPrivateSidecar(uri: Uri, baseName: String, extension: String): String? {
        val file = privateSidecarFile(uri, "$baseName.$extension")
        return if (file.isFile) file.readText(Charsets.UTF_8).takeIf(String::isNotBlank) else null
    }

    private fun privateSidecarFile(uri: Uri, name: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(uri.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(File(File(context.filesDir, PRIVATE_SIDECAR_DIRECTORY), digest), name)
    }

    private fun writePrivateSidecar(file: File, contents: String) {
        val parent = file.parentFile ?: throw IOException("No se pudo determinar la carpeta de subtítulos.")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("No se pudo crear la carpeta local de subtítulos.")
        }
        val temporary = File(parent, "${file.name}.tmp")
        try {
            temporary.writeText(contents, Charsets.UTF_8)
            if (!temporary.renameTo(file)) {
                throw IOException("No se pudo guardar el archivo local ${file.name}.")
            }
        } finally {
            if (temporary.exists() && !temporary.delete()) {
                Log.w(TAG, "No se pudo eliminar el temporal ${temporary.name}.")
            }
        }
    }

    private suspend fun mediaStoreDestination(uri: Uri): SubtitleDestination {
        val resolver = context.contentResolver
        val info = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) null else {
                cursor.getString(0) to cursor.getString(1)
            }
        } ?: throw IOException("No se pudo localizar el archivo multimedia en el almacenamiento.")
        val (displayName, relativePath) = info
        if (displayName.isNullOrBlank() || relativePath.isNullOrBlank()) {
            throw IOException("No se pudo determinar la carpeta del archivo multimedia.")
        }
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        return object : SubtitleDestination {
            override val displayName = displayName

            override fun exists(name: String): Boolean =
                resolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                    arrayOf(relativePath, name),
                    null,
                )?.use { it.moveToFirst() }
                    ?: throw IOException("No se pudo comprobar si ya existe el archivo $name.")

            override fun read(name: String): String? {
                val cursor = resolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                    arrayOf(relativePath, name),
                    null,
                ) ?: throw IOException("No se pudo comprobar si existe el archivo $name.")
                val candidate = cursor.use {
                    if (it.moveToFirst()) {
                        android.content.ContentUris.withAppendedId(collection, it.getLong(0))
                    } else {
                        null
                    }
                } ?: return null
                return resolver.openInputStream(candidate)?.bufferedReader(Charsets.UTF_8)?.use {
                    it.readText()
                } ?: throw IOException("No se pudo leer el archivo $name.")
            }

            override fun write(output: SubtitleOutput, isStillCurrent: () -> Boolean) {
                ensureCurrent(isStillCurrent)
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, output.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, output.mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val outputUri = resolver.insert(collection, values)
                    ?: throw IOException("No se pudo crear el archivo ${output.name}.")
                ensureCurrent(isStillCurrent)
                resolver.openOutputStream(outputUri)?.bufferedWriter(Charsets.UTF_8)?.use {
                    it.write(output.contents)
                } ?: throw IOException("No se pudo escribir el archivo ${output.name}.")
                ensureCurrent(isStillCurrent)
                val published = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                check(resolver.update(outputUri, published, null, null) > 0) {
                    "No se pudo publicar el archivo ${output.name}."
                }
            }
        }
    }

    private suspend fun documentTreeDestination(uri: Uri): SubtitleDestination {
        val resolver = context.contentResolver
        val path = DocumentsContract.findDocumentPath(resolver, uri)
            ?: throw IOException("No se pudo localizar la carpeta del archivo multimedia.")
        val folder = librarySettings.folders.first().firstOrNull {
            it.treeUri.authority == uri.authority &&
                DocumentsContract.getTreeDocumentId(it.treeUri) == path.rootId
        } ?: throw IOException("No hay permiso de escritura para la carpeta de este archivo.")
        val parentId = path.path.dropLast(1).lastOrNull() ?: path.rootId
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(folder.treeUri, parentId)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folder.treeUri, parentId)

        return object : SubtitleDestination {
            override val displayName: String =
                queryDisplayName(uri) ?: throw IOException("No se pudo leer el nombre del archivo multimedia.")

            override fun exists(name: String): Boolean =
                resolver.query(
                    childrenUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    nameColumn >= 0 && generateSequence { if (cursor.moveToNext()) cursor.getString(nameColumn) else null }
                        .any { it.equals(name, ignoreCase = true) }
                } ?: throw IOException("No se pudo comprobar si ya existe el archivo $name.")

            override fun read(name: String): String? {
                val cursor = resolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    ),
                    null,
                    null,
                    null,
                ) ?: throw IOException("No se pudo comprobar si existe el archivo $name.")
                val candidate = cursor.use {
                    val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (idColumn < 0 || nameColumn < 0) {
                        throw IOException("No se pudo localizar el archivo $name.")
                    }
                    generateSequence {
                        if (cursor.moveToNext()) cursor.getString(idColumn) to cursor.getString(nameColumn) else null
                    }.firstOrNull { it.second.equals(name, ignoreCase = true) }
                        ?.let { (documentId, _) ->
                            DocumentsContract.buildDocumentUriUsingTree(folder.treeUri, documentId)
                        }
                } ?: return null
                return resolver.openInputStream(candidate)?.bufferedReader(Charsets.UTF_8)?.use {
                    it.readText()
                } ?: throw IOException("No se pudo leer el archivo $name.")
            }

            override fun write(output: SubtitleOutput, isStillCurrent: () -> Boolean) {
                ensureCurrent(isStillCurrent)
                val outputUri = DocumentsContract.createDocument(
                    resolver,
                    parentUri,
                    output.mimeType,
                    output.name,
                ) ?: throw IOException("No se pudo crear el archivo ${output.name}.")
                ensureCurrent(isStillCurrent)
                resolver.openOutputStream(outputUri)?.bufferedWriter(Charsets.UTF_8)?.use {
                    it.write(output.contents)
                } ?: throw IOException("No se pudo escribir el archivo ${output.name}.")
                ensureCurrent(isStillCurrent)
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private fun parseLrc(text: String): List<SubtitleCue> =
        text.lineSequence().flatMap { line ->
            val timestamps = TIMESTAMP.findAll(line).toList()
            val lyric = TIMESTAMP.replace(line, "").trim()
            if (lyric.isBlank()) return@flatMap emptySequence()
            timestamps.asSequence().mapNotNull { match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                val seconds = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
                if (seconds > 59) return@mapNotNull null
                val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0
                SubtitleCue((minutes * 60 + seconds) * 1_000 + fraction, lyric)
            }
        }.sortedBy { it.startMs }.toList()
            .groupBy { it.startMs }
            .map { (startMs, sameTimeCues) ->
                SubtitleCue(startMs, sameTimeCues.map { it.text }.distinct().joinToString("\n"))
            }

    private fun lyricsFromSrt(text: String): String =
        text.lineSequence()
            .filterNot { it.isBlank() || it.trim().toIntOrNull() != null || SRT_TIMESTAMP.matches(it.trim()) }
            .joinToString("\n")
            .trim()

    private fun List<SubtitleCue>.toSrt(): String = mapIndexed { index, cue ->
        val nextStart = getOrNull(index + 1)?.startMs
        val endMs = if (nextStart != null && nextStart > cue.startMs) {
            minOf(nextStart, cue.startMs + MAX_CUE_DURATION_MS)
        } else {
            cue.startMs + LAST_CUE_DURATION_MS
        }
        "${index + 1}\n${cue.startMs.toSrtTimestamp()} --> ${endMs.toSrtTimestamp()}\n${cue.text}"
    }.joinToString("\n\n", postfix = "\n")

    private fun Long.toSrtTimestamp(): String {
        val hours = this / 3_600_000
        val minutes = this / 60_000 % 60
        val seconds = this / 1_000 % 60
        val millis = this % 1_000
        return "${hours.toString().padStart(2, '0')}:" +
            "${minutes.toString().padStart(2, '0')}:" +
            "${seconds.toString().padStart(2, '0')}," +
            millis.toString().padStart(3, '0')
    }

    private fun ensureCurrent(isStillCurrent: () -> Boolean) {
        if (!isStillCurrent()) {
            throw StaleTrackException()
        }
    }

    private interface SubtitleDestination {
        val displayName: String
        fun exists(name: String): Boolean
        fun read(name: String): String?
        fun write(output: SubtitleOutput, isStillCurrent: () -> Boolean)
    }

    private data class SubtitleOutput(
        val name: String,
        val mimeType: String,
        val contents: String,
    ) {
        val extension: String get() = name.substringAfterLast('.')
    }

    private data class SubtitleCue(val startMs: Long, val text: String)

    private companion object {
        const val TAG = "LyricsSubtitleWriter"
        const val PRIVATE_SIDECAR_DIRECTORY = "lyrics-sidecars"
        val TIMESTAMP = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
        val SRT_TIMESTAMP = Regex("""\d{2}:\d{2}:\d{2},\d{3}\s+-->\s+\d{2}:\d{2}:\d{2},\d{3}""")
        const val MAX_CUE_DURATION_MS = 7_000L
        const val LAST_CUE_DURATION_MS = 5_000L
    }
}
