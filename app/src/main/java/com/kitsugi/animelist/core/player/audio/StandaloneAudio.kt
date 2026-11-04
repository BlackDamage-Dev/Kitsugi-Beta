package com.kitsugi.animelist.core.player.audio

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.RandomAccessFile

/** Preserve a separately identified, genuine FLAC/WAV audio file without transcoding. */
internal object StandaloneAudio {
    suspend fun copy(context: Context, source: String, headers: Map<String, String>, output: File): AudioDemuxer.Result {
        when (Uri.parse(source).scheme?.lowercase()) {
            "http", "https" -> output.outputStream().buffered().use { out ->
                AudioExportHttp.append(source, source, headers, null, out, output.parentFile!!, 0)
            }
            "content", "file", null -> {
                val input = if (source.startsWith('/')) java.io.FileInputStream(source)
                    else context.contentResolver.openInputStream(Uri.parse(source)) ?: error("Ses dosyası açılamadı")
                input.use { stream -> output.outputStream().buffered().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var count = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = stream.read(buffer)
                        if (read < 0) break
                        require(count + read <= AudioExportHttp.MAX_TEMP_BYTES) { "Ses dosyası çok büyük" }
                        out.write(buffer, 0, read)
                        count += read
                    }
                } }
            }
            else -> error("Ses dosyası adres türü desteklenmiyor")
        }
        return validate(output)
    }

    /** Pure signature/container validation; callable without an Android device in unit tests. */
    fun validate(output: File): AudioDemuxer.Result {
        require(output.length() >= 44) { "Ses dosyası eksik" }
        RandomAccessFile(output, "r").use { f ->
            val magic = ByteArray(12).also { f.readFully(it) }
            if (magic.copyOfRange(0, 4).contentEquals("fLaC".toByteArray())) {
                // Walk the metadata chain: a STREAMINFO header alone is NOT a valid FLAC file.
                var offset = 4L
                var last = false
                var blocks = 0
                while (!last) {
                    require(++blocks <= 128 && offset + 4 <= f.length()) { "FLAC meta verisi eksik" }
                    f.seek(offset)
                    val flag = f.readUnsignedByte()
                    val size = (f.readUnsignedByte() shl 16) or (f.readUnsignedByte() shl 8) or f.readUnsignedByte()
                    require(blocks != 1 || ((flag and 0x7f) == 0 && size == 34)) { "FLAC STREAMINFO geçersiz" }
                    offset += 4L + size
                    require(offset + 16 <= f.length()) { "FLAC ses karesi eksik" }
                    last = flag and 0x80 != 0
                }
                f.seek(offset)
                require(f.readUnsignedByte() == 0xff && f.readUnsignedByte() and 0xfc == 0xf8) {
                    "FLAC ses karesi geçersiz"
                }
                return AudioDemuxer.Result("flac", "audio/flac")
            }
            require(magic.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
                magic.copyOfRange(8, 12).contentEquals("WAVE".toByteArray())) { "Bağımsız FLAC/WAV bulunamadı" }
            fun read32(): Long {
                val bytes = ByteArray(4).also { f.readFully(it) }
                return (bytes[0].toLong() and 255) or ((bytes[1].toLong() and 255) shl 8) or
                    ((bytes[2].toLong() and 255) shl 16) or ((bytes[3].toLong() and 255) shl 24)
            }
            f.seek(4)
            val riffSize = read32()
            require(riffSize >= 36 && riffSize + 8 <= f.length()) { "WAV boyutu geçersiz" }
            f.seek(12)
            var hasFormat = false
            var hasData = false
            while (f.filePointer + 8 <= riffSize + 8) {
                val id = ByteArray(4).also { f.readFully(it) }.toString(Charsets.US_ASCII)
                val size = read32()
                val next = f.filePointer + size + (size and 1)
                require(size >= 0 && next <= riffSize + 8) { "WAV bölümü eksik" }
                if (id == "fmt ") hasFormat = size >= 16
                if (id == "data" && size > 0) hasData = true
                f.seek(next)
            }
            require(hasFormat && hasData) { "WAV biçimi veya ses verisi eksik" }
            return AudioDemuxer.Result("wav", "audio/wav")
        }
    }
}
