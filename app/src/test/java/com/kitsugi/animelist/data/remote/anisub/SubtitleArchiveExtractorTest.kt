package com.kitsugi.animelist.data.remote.anisub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleArchiveExtractorTest {

    @Test
    fun sniffZipMagic() {
        val kind = SubtitleArchiveExtractor.sniffKind(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00))
        assertEquals(SubtitleArchiveExtractor.ArchiveKind.ZIP, kind)
    }

    @Test
    fun sniffRarMagic() {
        val kind = SubtitleArchiveExtractor.sniffKind(
            byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00)
        )
        assertEquals(SubtitleArchiveExtractor.ArchiveKind.RAR, kind)
    }

    @Test
    fun sniffSevenZipMagic() {
        val kind = SubtitleArchiveExtractor.sniffKind(
            byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)
        )
        assertEquals(SubtitleArchiveExtractor.ArchiveKind.SEVEN_ZIP, kind)
    }

    @Test
    fun sniffPlainAssText() {
        val bytes = "[Script Info]\nTitle: Ornek\n".toByteArray(Charsets.UTF_8)
        assertEquals(SubtitleArchiveExtractor.ArchiveKind.PLAIN_TEXT, SubtitleArchiveExtractor.sniffKind(bytes))
    }

    @Test
    fun sniffPlainSrtText() {
        val bytes = "1\n00:00:01,000 --> 00:00:03,000\nMerhaba\n".toByteArray(Charsets.UTF_8)
        assertEquals(SubtitleArchiveExtractor.ArchiveKind.PLAIN_TEXT, SubtitleArchiveExtractor.sniffKind(bytes))
    }

    @Test
    fun sanitizeFlattensPaths() {
        assertEquals("bolum 05.ass", SubtitleArchiveExtractor.sanitizeFileName("../../gizli/bolum 05.ass"))
        assertEquals("x.srt", SubtitleArchiveExtractor.sanitizeFileName("Klasor\\Alt\\x.srt"))
        assertNull(SubtitleArchiveExtractor.sanitizeFileName("__MACOSX/._x.ass"))
    }
}
