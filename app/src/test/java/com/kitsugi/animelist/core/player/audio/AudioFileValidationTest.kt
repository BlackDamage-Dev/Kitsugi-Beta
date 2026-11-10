package com.kitsugi.animelist.core.player.audio

import android.media.AudioFormat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AudioFileValidationTest {
    @Test fun `PCM 8 16 24 and float are converted to signed PCM16`() {
        assertArrayEquals(byteArrayOf(0, -128, 0, 0, 0, 127),
            AudioPcmExport.toPcm16(byteArrayOf(0, -128, -1), AudioFormat.ENCODING_PCM_8BIT))
        assertArrayEquals(byteArrayOf(0, -128, -1, 127),
            AudioPcmExport.toPcm16(byteArrayOf(0, -128, -1, 127), AudioFormat.ENCODING_PCM_16BIT))
        assertArrayEquals(byteArrayOf(0, -128, -1, 127),
            AudioPcmExport.toPcm16(byteArrayOf(0, 0, -128, -1, -1, 127), AudioFormat.ENCODING_PCM_24BIT_PACKED))
        val floats = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putFloat(-1f).putFloat(1f).array()
        assertArrayEquals(byteArrayOf(1, -128, -1, 127),
            AudioPcmExport.toPcm16(floats, AudioFormat.ENCODING_PCM_FLOAT))
        assertThrows(IllegalArgumentException::class.java) {
            AudioPcmExport.toPcm16(byteArrayOf(0), AudioFormat.ENCODING_PCM_16BIT)
        }
    }

    @Test fun `generated WAV header has a consistent RIFF size and PCM format`() {
        val bytes = AudioPcmExport.waveHeader(channels = 2, rate = 48_000, dataSize = 8)
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals(44, header.getInt(4))
        assertEquals("WAVEfmt ", String(bytes, 8, 8))
        assertEquals(1, header.getShort(20).toInt())
        assertEquals(2, header.getShort(22).toInt())
        assertEquals(48_000, header.getInt(24))
        assertEquals(192_000, header.getInt(28))
        assertEquals(8, header.getInt(40))
        val surround = AudioPcmExport.waveHeader(channels = 6, rate = 48_000, dataSize = 12)
        val surroundHeader = ByteBuffer.wrap(surround).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(6, surroundHeader.getShort(22).toInt())
        assertEquals(576_000, surroundHeader.getInt(28))
        assertEquals(12, surroundHeader.getShort(32).toInt())
        assertThrows(IllegalArgumentException::class.java) { AudioPcmExport.waveHeader(2, 48_000, 3) }
        assertThrows(IllegalArgumentException::class.java) { AudioPcmExport.waveHeader(9, 48_000, 18) }
    }

    private fun temp(data: ByteArray): File = File.createTempFile("kitsugi-audio-test", ".bin").also { it.writeBytes(data) }

    @Test fun `standalone FLAC must include a metadata chain and a frame sync`() {
        val valid = "fLaC".toByteArray() + byteArrayOf(0x80.toByte(), 0, 0, 34) +
            ByteArray(34) + byteArrayOf(0xff.toByte(), 0xf8.toByte()) + ByteArray(20)
        val file = temp(valid)
        try {
            assertEquals("flac", StandaloneAudio.validate(file).extension)
            file.writeBytes(valid.copyOfRange(0, 42) + ByteArray(20))
            assertThrows(IllegalArgumentException::class.java) { StandaloneAudio.validate(file) }
        } finally { file.delete() }
    }

    @Test fun `standalone WAV must declare complete fmt and nonempty data chunks`() {
        val buffer = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(40).put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(8000)
        buffer.putInt(16000).putShort(2).putShort(16)
        buffer.put("data".toByteArray()).putInt(4).putShort(0).putShort(0)
        val file = temp(buffer.array())
        try {
            assertEquals("wav", StandaloneAudio.validate(file).extension)
            file.writeBytes(buffer.array().copyOfRange(0, 44))
            assertThrows(IllegalArgumentException::class.java) { StandaloneAudio.validate(file) }
        } finally { file.delete() }
    }
}
