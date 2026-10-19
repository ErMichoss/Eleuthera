package com.tuapp.eleuthera.data.metadata

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lee letras no sincronizadas de ID3 y las etiquetas ©lyr de archivos MP4/M4A. */
@Singleton
class EmbeddedLyricsReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun read(uri: Uri): String? = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.buffered()?.use(::readEmbeddedLyrics)
            ?: error("No se pudo abrir el archivo para leer sus letras.")
    }

    private fun readEmbeddedLyrics(input: InputStream): String? {
        input.mark(12)
        val signature = ByteArray(12)
        if (!input.readFully(signature)) return null
        input.reset()
        return when {
            signature.copyOfRange(0, 3).contentEquals(ID3) -> readId3Lyrics(input)
            signature.copyOfRange(4, 8).decodeToString() == "ftyp" -> readMp4Lyrics(input)
            else -> null
        }
    }

    private fun readId3Lyrics(input: InputStream): String? {
        val header = ByteArray(10)
        if (!input.readFully(header) || !header.copyOfRange(0, 3).contentEquals(ID3)) return null

        val version = header[3].toInt() and 0xff
        if (version !in 2..4) return null
        val flags = header[5].toInt() and 0xff
        var remaining = synchsafeInt(header, 6)
        if (remaining < 0) return null

        if (flags and EXTENDED_HEADER != 0) {
            val extendedHeader = ByteArray(4)
            if (remaining < extendedHeader.size || !input.readFully(extendedHeader)) return null
            val size = if (version == 4) synchsafeInt(extendedHeader, 0)
            else bigEndianInt(extendedHeader, 0)
            val bytesToSkip = if (version == 4) size - 4 else size
            if (bytesToSkip < 0 || bytesToSkip > remaining - 4) return null
            input.skipFully(bytesToSkip.toLong())
            remaining -= 4 + bytesToSkip
        }

        val frameHeaderSize = if (version == 2) 6 else 10
        while (remaining >= frameHeaderSize) {
            val frameHeader = ByteArray(frameHeaderSize)
            if (!input.readFully(frameHeader)) return null
            remaining -= frameHeaderSize

            val frameIdLength = if (version == 2) 3 else 4
            if (frameHeader[0] == 0.toByte()) return null
            val frameId = frameHeader.decodeToString(0, frameIdLength)
            val frameSize = when (version) {
                2 -> ((frameHeader[3].toInt() and 0xff) shl 16) or
                    ((frameHeader[4].toInt() and 0xff) shl 8) or
                    (frameHeader[5].toInt() and 0xff)
                3 -> bigEndianInt(frameHeader, 4)
                else -> synchsafeInt(frameHeader, 4)
            }
            if (frameSize < 0 || frameSize > remaining) return null

            if (frameId == "USLT" || frameId == "ULT") {
                if (frameSize > MAX_LYRICS_BYTES) return null
                val payload = ByteArray(frameSize)
                if (!input.readFully(payload)) return null
                val lyrics = decodeLyrics(payload, version, frameHeader)
                if (!lyrics.isNullOrBlank()) return lyrics
                remaining -= frameSize
                continue
            }

            input.skipFully(frameSize.toLong())
            remaining -= frameSize
        }
        return null
    }

    private fun decodeLyrics(payload: ByteArray, version: Int, frameHeader: ByteArray): String? {
        if (payload.size < 4) return null
        val frameUnsynchronized = version == 4 &&
            frameHeader.size >= 10 &&
            (frameHeader[9].toInt() and FRAME_UNSYNCHRONIZATION) != 0
        val decodedPayload = if (frameUnsynchronized) payload.deunsynchronize() else payload
        val encoding = decodedPayload[0].toInt() and 0xff
        val charset = when (encoding) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> return null
        }
        var textStart = 4
        val wide = encoding == 1 || encoding == 2
        while (textStart < decodedPayload.size) {
            if (decodedPayload[textStart] == 0.toByte() &&
                (!wide || (textStart + 1 < decodedPayload.size && decodedPayload[textStart + 1] == 0.toByte()))
            ) {
                textStart += if (wide) 2 else 1
                break
            }
            textStart += if (wide) 2 else 1
        }
        if (textStart > decodedPayload.size) return null

        return decodedPayload.copyOfRange(textStart, decodedPayload.size).toString(charset).trim()
    }

    private fun readMp4Lyrics(input: InputStream): String? =
        readMp4Children(input, null, ROOT_ATOM)

    private fun readMp4Children(
        input: InputStream,
        contentSize: Long?,
        parentType: String,
    ): String? {
        var remaining = contentSize ?: Long.MAX_VALUE
        if (parentType == "meta") {
            if (remaining < 4) return null
            input.skipFully(4)
            remaining -= 4
        }

        while (remaining >= 8) {
            val header = ByteArray(8)
            if (!input.readFully(header)) return null
            var atomSize = unsignedInt(header, 0)
            val atomType = header.copyOfRange(4, 8).toString(Charsets.ISO_8859_1)
            var headerSize = 8L
            if (atomSize == 1L) {
                val extendedSize = ByteArray(8)
                if (!input.readFully(extendedSize)) return null
                atomSize = longFromBytes(extendedSize)
                headerSize = 16
            } else if (atomSize == 0L) {
                if (contentSize == null) return null
                atomSize = remaining
            }
            if (atomSize < headerSize || atomSize > remaining) return null
            val payloadSize = atomSize - headerSize

            val lyrics = when {
                parentType == "ilst" && atomType == MP4_LYRICS ->
                    readMp4Children(input, payloadSize, "lyrics")
                parentType == "lyrics" && atomType == "data" ->
                    readMp4Data(input, payloadSize)
                atomType in MP4_CONTAINERS ->
                    readMp4Children(input, payloadSize, atomType)
                else -> {
                    input.skipFully(payloadSize)
                    null
                }
            }
            remaining -= atomSize
            if (!lyrics.isNullOrBlank()) return lyrics
        }
        if (contentSize != null && remaining > 0) input.skipFully(remaining)
        return null
    }

    private fun readMp4Data(input: InputStream, payloadSize: Long): String? {
        if (payloadSize < 8) {
            input.skipFully(payloadSize)
            return null
        }
        input.skipFully(8)
        val textSize = payloadSize - 8
        if (textSize > MAX_LYRICS_BYTES) {
            input.skipFully(textSize)
            return null
        }
        val text = ByteArray(textSize.toInt())
        if (!input.readFully(text)) return null
        return text.toString(Charsets.UTF_8).trim()
    }

    private fun bigEndianInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)

    private fun unsignedInt(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xff) shl 24) or
            ((bytes[offset + 1].toLong() and 0xff) shl 16) or
            ((bytes[offset + 2].toLong() and 0xff) shl 8) or
            (bytes[offset + 3].toLong() and 0xff)

    private fun longFromBytes(bytes: ByteArray): Long {
        var result = 0L
        for (byte in bytes) {
            if (result < 0) return -1
            result = (result shl 8) or (byte.toLong() and 0xff)
        }
        return result
    }

    private fun synchsafeInt(bytes: ByteArray, offset: Int): Int {
        val a = bytes[offset].toInt() and 0xff
        val b = bytes[offset + 1].toInt() and 0xff
        val c = bytes[offset + 2].toInt() and 0xff
        val d = bytes[offset + 3].toInt() and 0xff
        if ((a or b or c or d) and 0x80 != 0) return -1
        return (a shl 21) or (b shl 14) or (c shl 7) or d
    }

    private fun InputStream.readFully(bytes: ByteArray): Boolean {
        var offset = 0
        while (offset < bytes.size) {
            val count = read(bytes, offset, bytes.size - offset)
            if (count < 0) return false
            if (count == 0) {
                val next = read()
                if (next < 0) return false
                bytes[offset++] = next.toByte()
            } else {
                offset += count
            }
        }
        return true
    }

    private fun InputStream.skipFully(count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                if (read() < 0) error("El archivo de audio contiene metadatos incompletos.")
                remaining--
            }
        }
    }

    private fun ByteArray.deunsynchronize(): ByteArray {
        val result = ByteArray(size)
        var inputIndex = 0
        var outputIndex = 0
        while (inputIndex < size) {
            result[outputIndex++] = this[inputIndex]
            if (this[inputIndex] == 0xff.toByte() &&
                inputIndex + 1 < size && this[inputIndex + 1] == 0.toByte()
            ) {
                inputIndex += 2
            } else {
                inputIndex++
            }
        }
        return result.copyOf(outputIndex)
    }

    private companion object {
        val ID3 = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte())
        const val ROOT_ATOM = "root"
        const val MP4_LYRICS = "\u00a9lyr"
        val MP4_CONTAINERS = setOf("moov", "udta", "meta", "ilst")
        const val EXTENDED_HEADER = 0x40
        const val FRAME_UNSYNCHRONIZATION = 0x02
        const val MAX_LYRICS_BYTES = 1_048_576
    }
}
