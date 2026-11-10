package com.kitsugi.animelist.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AniSubSubtitleMatcherTest {
    @Test
    fun `release ranges and batch names include requested episode`() {
        assertTrue(AniSubSubtitleMatcher.releaseMatchesEpisode("My Anime 01-24 Batch", 18))
        assertTrue(AniSubSubtitleMatcher.releaseMatchesEpisode("My Anime Season 2 Pack", 8))
        assertTrue(AniSubSubtitleMatcher.releaseMatchesEpisode("My Anime Bölüm 12", 12))
        assertFalse(AniSubSubtitleMatcher.releaseMatchesEpisode("My Anime Episode 12", 2))
    }

    @Test
    fun `filename matcher recognizes common anime episode conventions`() {
        assertTrue(AniSubSubtitleMatcher.filenameMatchesEpisode("[Group] Anime - 12 [1080p].ass", 12, "Anime"))
        assertTrue(AniSubSubtitleMatcher.filenameMatchesEpisode("Anime.S02E03.srt", 3, "Anime"))
        assertTrue(AniSubSubtitleMatcher.filenameMatchesEpisode("Anime 2x07.vtt", 7, "Anime"))
        assertFalse(AniSubSubtitleMatcher.filenameMatchesEpisode("Anime - 13.ass", 12, "Anime"))
    }

    @Test
    fun `archive selection returns every matching subtitle but never an arbitrary batch file`() {
        val dir = createTempDir(prefix = "anisub-matcher-")
        try {
            val files = listOf("Anime - 04.ass", "Anime - 04.srt", "Anime - 05.ass")
                .map { File(dir, it).apply { writeText("subtitle") } }
            val selected = AniSubSubtitleMatcher.selectFiles(files, 4, "Anime", "Anime complete batch")
            assertEquals(setOf("Anime - 04.ass", "Anime - 04.srt"), selected.map { it.name }.toSet())
            assertTrue(AniSubSubtitleMatcher.selectFiles(files, 8, "Anime", "Anime batch").isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `single generic subtitle is accepted only when release matches requested episode`() {
        val dir = createTempDir(prefix = "anisub-single-")
        try {
            val subtitle = File(dir, "subtitle.ass").apply { writeText("subtitle") }
            assertEquals(listOf(subtitle), AniSubSubtitleMatcher.selectFiles(listOf(subtitle), 7, "Anime", "Anime Episode 7"))
            assertTrue(AniSubSubtitleMatcher.selectFiles(listOf(subtitle), 7, "Anime", "Anime Episode 8").isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }
}
