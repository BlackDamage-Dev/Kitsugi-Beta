package com.kitsugi.animelist.core.player.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvSubtitlePrefetcherTest {

    @Test
    fun `yalnizca http ve https uzak sayilir`() {
        assertTrue(MpvSubtitlePrefetcher.isRemote("https://subs5.strem.io/en/download/file/1"))
        assertTrue(MpvSubtitlePrefetcher.isRemote("  HTTP://example.com/a.srt"))
        assertFalse(MpvSubtitlePrefetcher.isRemote("/data/user/0/app/cache/a.srt"))
        assertFalse(MpvSubtitlePrefetcher.isRemote("content://media/external/file/12"))
        assertFalse(MpvSubtitlePrefetcher.isRemote("fd://42"))
    }

    @Test
    fun `bilinen uzanti korunur, sorgu ve parca atilir`() {
        assertEquals("vtt", MpvSubtitlePrefetcher.extensionFor("https://x.y/sub/tr.VTT?token=abc#t=1"))
        assertEquals("ass", MpvSubtitlePrefetcher.extensionFor("https://x.y/a/b/episode01.ass"))
        assertEquals("vtt", MpvSubtitlePrefetcher.extensionFor("https://x.y/a.webvtt"))
    }

    @Test
    fun `uzantisiz veya bilinmeyen uzanti srt olur`() {
        // Stremio OpenSubtitles adresleri uzantı taşımaz.
        assertEquals("srt", MpvSubtitlePrefetcher.extensionFor("https://subs5.strem.io/en/download/subencoding-stremio-utf8/src-api/file/1957363404"))
        assertEquals("srt", MpvSubtitlePrefetcher.extensionFor("https://x.y/download.php?id=5"))
        assertEquals("srt", MpvSubtitlePrefetcher.extensionFor("https://x.y/file.exe"))
    }

    @Test
    fun `dosya adi URL'ye ozgu, kararlı ve guvenli`() {
        val a = MpvSubtitlePrefetcher.fileNameFor("https://x.y/a.srt")
        assertEquals(a, MpvSubtitlePrefetcher.fileNameFor("https://x.y/a.srt"))
        assertNotEquals(a, MpvSubtitlePrefetcher.fileNameFor("https://x.y/b.srt"))
        assertTrue(a.matches(Regex("[0-9a-f]{24}\\.srt")))
    }
}
