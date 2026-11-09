package com.kitsugi.animelist.core.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleEpisodeValidatorTest {
    @Test fun rejectsExplicitWrongEpisodeEvenWhenProviderMetadataClaimsMatch() {
        assertFalse(SubtitleEpisodeValidator.isCompatible("series", "tt2560140:1:1", 1, 1,
            "Attack.on.Titan.S01E06.1080p.srt", "Attack.on.Titan.S01E01"))
        assertFalse(SubtitleEpisodeValidator.isCompatible("series", "tmdb:1429:2:1", 1, 1, null, null))
        assertFalse(SubtitleEpisodeValidator.isCompatible("series", "tt2560140:2:1", 2, 2, null, null))
        assertTrue(SubtitleEpisodeValidator.isCompatible("series", "tt2560140:2:1", 2, 1,
            "Attack.on.Titan.S02E01.1080p.srt", null))
    }

    @Test fun isolatedNumbersAndOtherIdFamiliesAreNotMisinterpreted() {
        assertTrue(SubtitleEpisodeValidator.isCompatible("series", "tt2560140:2:1", null, null,
            "[Anime Time] Attack on Titan - 26 [1080p].srt", null))
        assertTrue(SubtitleEpisodeValidator.isCompatible("series", "kitsu:8671:1", null, null,
            "Attack.on.Titan.S02E01.srt", null))
        assertTrue(SubtitleEpisodeValidator.isCompatible("movie", "tt12345", null, null, "Film.S01E02.srt", null))
    }
}
