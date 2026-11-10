package com.kitsugi.animelist.core.player.audio

import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FlvAudioExportTest {
    private fun tag(type: Int, body: ByteArray, timestamp: Int = 0): ByteArray =
        ByteArrayOutputStream().apply {
            write(type)
            write(body.size shr 16 and 255); write(body.size shr 8 and 255); write(body.size and 255)
            write(timestamp shr 16 and 255); write(timestamp shr 8 and 255); write(timestamp and 255)
            write(timestamp ushr 24); write(byteArrayOf(0, 0, 0))
            write(body)
            val previous = 11 + body.size
            write(byteArrayOf((previous shr 24).toByte(), (previous shr 16).toByte(),
                (previous shr 8).toByte(), previous.toByte()))
        }.toByteArray()

    private fun flv(vararg tags: ByteArray): ByteArray =
        byteArrayOf('F'.code.toByte(), 'L'.code.toByte(), 'V'.code.toByte(), 1, 5,
            0, 0, 0, 9, 0, 0, 0, 0) + tags.fold(byteArrayOf()) { acc, bytes -> acc + bytes }

    private inline fun withFiles(bytes: ByteArray, block: (File, File) -> Unit) {
        val source = File.createTempFile("flv-source-test-", ".flv")
        val out = File.createTempFile("flv-audio-test-", ".aac")
        try { source.writeBytes(bytes); block(source, out) }
        finally { source.delete(); out.delete() }
    }

    @Test fun `AAC FLV removes video and emits valid ADTS frame`() {
        val raw = byteArrayOf(0x11, 0x22, 0x33, 0x44)
        val data = flv(tag(9, byteArrayOf(1, 2, 3, 4)),
            tag(8, byteArrayOf(0xaf.toByte(), 0, 0x12, 0x10)),
            tag(8, byteArrayOf(0xaf.toByte(), 1) + raw, 100))
        withFiles(data) { source, output ->
            val result = FlvAudioExport.export(source, output)
            assertEquals("aac", result.extension)
            assertArrayEquals(FlvAudioExport.adtsHeader(byteArrayOf(0x12, 0x10), 4) + raw,
                output.readBytes())
            assertEquals(11L, output.length())
        }
    }

    @Test fun `MP3 FLV preserves only MP3 frames`() {
        val frame = byteArrayOf(0xff.toByte(), 0xfb.toByte(), 0x90.toByte(), 0x64, 1, 2, 3)
        withFiles(flv(tag(18, byteArrayOf(1, 2)), tag(8, byteArrayOf(0x2f) + frame))) { source, output ->
            assertEquals("mp3", FlvAudioExport.export(source, output).extension)
            assertArrayEquals(frame, output.readBytes())
        }
    }

    @Test fun `FLV rejects missing audio codec changes and truncated tags`() {
        withFiles(flv(tag(9, byteArrayOf(1)))) { source, output ->
            assertThrows(IllegalArgumentException::class.java) { FlvAudioExport.export(source, output) }
        }
        val bad = flv(tag(8, byteArrayOf(0xaf.toByte(), 0, 0x12, 0x10)),
            tag(8, byteArrayOf(0xaf.toByte(), 1, 0x11)))
        withFiles(bad.copyOf(bad.size - 2)) { source, output ->
            assertThrows(IllegalArgumentException::class.java) { FlvAudioExport.export(source, output) }
        }
        withFiles(flv(tag(8, byteArrayOf(0x2f, 0xff.toByte(), 0xfb.toByte(), 0x90.toByte(), 0x64)),
            tag(8, byteArrayOf(0xaf.toByte(), 1, 0x11)))) { source, output ->
            assertThrows(IllegalArgumentException::class.java) { FlvAudioExport.export(source, output) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            FlvAudioExport.adtsHeader(byteArrayOf(0x2b, 0x90.toByte()), 4) // HE-AAC is not AAC-LC.
        }
    }
}
