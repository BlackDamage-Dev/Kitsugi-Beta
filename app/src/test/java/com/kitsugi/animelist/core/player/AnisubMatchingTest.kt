package com.kitsugi.animelist.core.player

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test

class AnisubMatchingTest {
    @Test fun exactIdWinsOverSimilarTitles() {
        assertTrue(AnisubMatching.matchesMedia(108465, 108465, "Mushoku Tensei", emptyList()))
        assertFalse(AnisubMatching.matchesMedia(108465, 127720, "Mushoku Tensei", listOf("Mushoku Tensei")))
        assertFalse(AnisubMatching.matchesMedia(null, null, "Mushoku Tensei", listOf("Mushoku Tensei Part 2")))
        assertFalse(AnisubMatching.matchesMedia(null, 108465, "Mushoku Tensei", listOf("Mushoku Tensei")))
        assertFalse(AnisubMatching.matchesMedia(108465, null, "Mushoku Tensei", listOf("Mushoku Tensei")))
    }
    @Test fun actualApiEpisodeFieldsAreUsedInsteadOf1080pReleaseName() {
        assertEquals(true, AnisubMatching.episodeMatches("Bölüm: 06", null, 6))
        assertEquals(false, AnisubMatching.episodeMatches("Bölüm: 06", null, 1))
        assertEquals(true, AnisubMatching.episodeMatches("Paket", "1-11", 3))
        assertEquals(false, AnisubMatching.episodeMatches("Paket", "1-11", 12))
        assertNull(AnisubMatching.episodeMatches("Bölüm", null, 6))
    }
    @Test fun packagesAreRecognised() {
        assertTrue(AnisubMatching.isPackage("Paket"))
        assertFalse(AnisubMatching.isPackage("Bölüm: 06"))
    }
    @Test fun codecAndReleaseDigitsNeverImpersonateEpisodeOne() {
        val name = "[Sokudo] Mushoku Tensei Jobless Reincarnation 12 [1080p BD][AV1][dual audio].ass"
        assertFalse(AnisubMatching.matchesFileEpisode(name, 1))
        assertTrue(AnisubMatching.matchesFileEpisode(name, 12))
        assertFalse(AnisubMatching.matchesFileEpisode("[Group] Title S01E02 [1080p].ass", 1))
        assertTrue(AnisubMatching.matchesFileEpisode("[Group] Title - 01 [720p].ass", 1))
        assertFalse(AnisubMatching.matchesFileEpisode("[Group] Title [AV1] [1080p].ass", 1))
    }
    @Test fun singleReleaseFallbackRecognizesDifferentBareEpisode() {
        assertEquals(12, AnisubMatching.fileEpisode("[Sokudo] Mushoku Tensei 12 [1080p][AV1].ass"))
        assertNull(AnisubMatching.fileEpisode("subtitle [1080p][AV1].ass"))
        // Actual file extracted from AniSub release 3607 on 2026-11-09.
        assertEquals(6, AnisubMatching.fileEpisode("FX 06.ass"))
        assertFalse(AnisubMatching.matchesFileEpisode("FX 06.ass", 1))
    }
    @Test fun ambiguousArchiveEpisodeIsNotChosenByFileOrder() {
        val files = listOf("Show S01E01.ass", "Show S02E01.ass")
        assertNull(AnisubMatching.uniqueEpisodeFile(files, 1))
        assertEquals("Show S02E01.ass", AnisubMatching.uniqueEpisodeFile(listOf("Show S02E01.ass", "Show S02E02.ass"), 1))
    }
}
