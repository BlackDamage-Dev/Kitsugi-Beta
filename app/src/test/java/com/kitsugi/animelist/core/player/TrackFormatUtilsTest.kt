package com.kitsugi.animelist.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackFormatUtilsTest {

    @Test
    fun `subtitle codec and mime types produce canonical tags`() {
        assertEquals("SRT", TrackFormatUtils.subtitleFormat(codec = "subrip"))
        assertEquals("VTT", TrackFormatUtils.subtitleFormat(mimeType = "text/vtt"))
        assertEquals("TTML", TrackFormatUtils.subtitleFormat(mimeType = "application/ttml+xml"))
        assertEquals("PGS", TrackFormatUtils.subtitleFormat(codec = "hdmv_pgs_subtitle"))
        assertEquals("VobSub", TrackFormatUtils.subtitleFormat(codec = "dvd_subtitle"))
        assertEquals("MicroDVD", TrackFormatUtils.subtitleFormat(codec = "microdvd"))
        assertEquals("ARIB_CAPTION", TrackFormatUtils.subtitleFormat(codec = "arib_caption"))
    }

    @Test
    fun `source disambiguates ASS and SSA without overriding real codec`() {
        assertEquals("SSA", TrackFormatUtils.subtitleFormat(codec = "ass", source = "/tmp/addon.ssa"))
        assertEquals("ASS", TrackFormatUtils.subtitleFormat(codec = "ass", source = "/tmp/addon.ass"))
        assertEquals(
            "SRT",
            TrackFormatUtils.subtitleFormat(codec = "subrip", source = "https://cdn.invalid/wrong-name.ass")
        )
    }

    @Test
    fun `known extension is fallback and unknown URL is not guessed as SRT`() {
        assertEquals("SRT", TrackFormatUtils.subtitleFormat(source = "https://cdn.invalid/a.srt?token=1#x"))
        assertEquals("SAMI", TrackFormatUtils.subtitleFormat(source = "file:///storage/emulated/0/TR.SMI"))
        assertEquals("SUB", TrackFormatUtils.subtitleFormat(declaredFormat = "sub"))
        assertNull(TrackFormatUtils.subtitleFormat(source = "https://addon.invalid/subtitle.php?id=42"))
        assertNull(TrackFormatUtils.subtitleFormat(codec = null, mimeType = null, source = "fd://17"))
    }

    @Test
    fun `audio codecs profiles and containers are recognized`() {
        assertEquals("AAC", TrackFormatUtils.audioFormat(codec = "mp4a.40.2"))
        assertEquals("E-AC-3", TrackFormatUtils.audioFormat(codec = "eac3"))
        assertEquals("E-AC-3 JOC", TrackFormatUtils.audioFormat(codec = "eac3-joc"))
        assertEquals("TrueHD", TrackFormatUtils.audioFormat(codec = "truehd"))
        assertEquals("DTS-HD", TrackFormatUtils.audioFormat(codec = "dtshd"))
        assertEquals("FLAC", TrackFormatUtils.audioFormat(source = "content://audio/Turkish.FLAC"))
        assertEquals("Opus", TrackFormatUtils.audioFormat(mimeType = "audio/opus"))
        assertEquals("M4A", TrackFormatUtils.audioFormat(source = "/audio/eng.m4a"))
        assertEquals("M4A", TrackFormatUtils.audioFormat(declaredFormat = "m4a"))
        assertEquals("IAMF", TrackFormatUtils.audioFormat(codec = "iamf"))
    }

    @Test
    fun `subtitle signatures detect extensionless addon responses`() {
        assertEquals("vtt", detect("WEBVTT\n\n00:00.000 --> 00:02.000\nMerhaba"))
        assertEquals("ass", detect("[Script Info]\n[V4+ Styles]\n[Events]"))
        assertEquals("ssa", detect("[Script Info]\n[V4 Styles]\n[Events]"))
        assertEquals("ttml", detect("<?xml version=\"1.0\"?><tt xmlns=\"http://www.w3.org/ns/ttml\"></tt>"))
        assertEquals("srt", detect("1\n00:00:01,000 --> 00:00:03,000\nMerhaba"))
        assertEquals("sub", detect("{1}{75}Merhaba"))
        assertEquals("smi", detect("<SAMI><BODY><SYNC Start=1000>Merhaba</BODY></SAMI>"))
        assertNull(detect("<html><body>Not a subtitle</body></html>"))
    }

    @Test
    fun `UTF16 subtitle signature is decoded`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            "WEBVTT\n\n00:00.000 --> 00:02.000\nSelam".toByteArray(Charsets.UTF_16LE)
        assertEquals("vtt", TrackFormatUtils.detectSubtitleContentFormat(bytes))
    }

    private fun detect(value: String): String? =
        TrackFormatUtils.detectSubtitleContentFormat(value.toByteArray(Charsets.UTF_8))
}
