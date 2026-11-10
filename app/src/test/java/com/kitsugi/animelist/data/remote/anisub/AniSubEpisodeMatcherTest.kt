package com.kitsugi.animelist.data.remote.anisub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AniSubEpisodeMatcherTest {

    // ---- extractEpisode: gerçek fansub dosya adları ----

    @Test
    fun coalgirlsPackageNaming_extractsEpisodeTwo() {
        val name = "[Coalgirls]_Shingeki_no_Kyojin_02_(1280x720_Blu-ray_FLAC)_[A071754C].ass"
        assertEquals(2, AniSubEpisodeMatcher.extractEpisode(name, "Shingeki no Kyojin"))
    }

    @Test
    fun subspleaseNaming_extractsEpisode() {
        val name = "[SubsPlease] Sousou no Frieren - 05 (1080p) [3CB81308].ass"
        assertEquals(5, AniSubEpisodeMatcher.extractEpisode(name, "Sousou no Frieren"))
    }

    @Test
    fun seasonEpisodePattern_extractsSeasonAndEpisode() {
        val name = "Shingeki.No.Kyojin.S02E12.MULTi.1080p.10bit.BluRay.x265-ASKO.srt"
        val ref = AniSubEpisodeMatcher.extractEpisodeRef(name)
        assertEquals(2, ref?.season)
        assertEquals(12, ref?.episode)
    }

    @Test
    fun underscoreSeasonEpisode_stillMatched() {
        assertEquals(5, AniSubEpisodeMatcher.extractEpisode("Anime_Adi_S01E05_720p.ass"))
    }

    @Test
    fun oneByPattern_extractsEpisode() {
        assertEquals(5, AniSubEpisodeMatcher.extractEpisode("Anime Adi 1x05 [HD].ass"))
    }

    @Test
    fun numberInsideTitle_isNotEpisode() {
        val name = "[Group] 86 -Eighty Six- 07 [1080p].ass"
        assertEquals(7, AniSubEpisodeMatcher.extractEpisode(name, "86 Eighty Six"))
    }

    @Test
    fun fourDigitEpisode_supported() {
        assertEquals(1071, AniSubEpisodeMatcher.extractEpisode("One Piece - 1071 [1080p].ass", "One Piece"))
    }

    @Test
    fun yearAndResolution_AreNotEpisode() {
        assertNull(AniSubEpisodeMatcher.extractEpisode("Film Adi (2023) [BluRay 1080p].ass", "Film Adi"))
    }

    @Test
    fun halfEpisode_isNotFullEpisode() {
        assertNull(AniSubEpisodeMatcher.extractEpisode("Anime - 12.5 [1080p].ass"))
    }

    @Test
    fun turkishBolumPrefix_supported() {
        assertEquals(9, AniSubEpisodeMatcher.extractEpisode("Anime Adi Bölüm 09.ass"))
    }

    // ---- pickEpisodeFile ----

    private val packageFiles = listOf(
        "[Coalgirls]_Shingeki_no_Kyojin_01_(1280x720_Blu-ray_FLAC)_[3CB81308].ass",
        "[Coalgirls]_Shingeki_no_Kyojin_02_(1280x720_Blu-ray_FLAC)_[A071754C].ass",
        "[Coalgirls]_Shingeki_no_Kyojin_03_(1280x720_Blu-ray_FLAC)_[FB1A088F].ass"
    )

    @Test
    fun pickEpisodeFile_selectsMatchingFile() {
        val picked = AniSubEpisodeMatcher.pickEpisodeFile(packageFiles, 2, "Shingeki no Kyojin")
        assertTrue(picked!!.contains("_02_"))
    }

    @Test
    fun pickEpisodeFile_noFallbackToWrongEpisode() {
        assertNull(AniSubEpisodeMatcher.pickEpisodeFile(packageFiles, 7, "Shingeki no Kyojin"))
    }

    @Test
    fun pickEpisodeFile_singleUnnumberedMovieFileAccepted() {
        val picked = AniSubEpisodeMatcher.pickEpisodeFile(listOf("Film Adi Turkce.ass"), 1, "Film Adi")
        assertEquals("Film Adi Turkce.ass", picked)
    }

    @Test
    fun pickEpisodeFile_singleWrongNumberRejected() {
        assertNull(AniSubEpisodeMatcher.pickEpisodeFile(listOf("Anime - 03.ass"), 2, "Anime"))
    }

    @Test
    fun pickEpisodeFile_prefersAssOverSrt() {
        val picked = AniSubEpisodeMatcher.pickEpisodeFile(
            listOf("Anime - 05.srt", "Anime - 05.ass"), 5, "Anime"
        )
        assertEquals("Anime - 05.ass", picked)
    }

    @Test
    fun pickEpisodeFile_nullEpisodePicksFirstRanked() {
        val picked = AniSubEpisodeMatcher.pickEpisodeFile(
            listOf("Film.srt", "Film.ass"), null, "Film"
        )
        assertEquals("Film.ass", picked)
    }

    // ---- release uygunluğu ----

    @Test
    fun contentTypeBolum_isStrict() {
        assertFalse(
            AniSubEpisodeMatcher.isReleaseCompatibleWithEpisode("Bölüm: 03", null, null, 2)
        )
        assertTrue(
            AniSubEpisodeMatcher.isReleaseCompatibleWithEpisode("Bölüm: 03", null, null, 3)
        )
    }

    @Test
    fun contentTypePaket_alwaysCompatible() {
        assertTrue(AniSubEpisodeMatcher.isReleaseCompatibleWithEpisode("Paket", null, null, 9))
    }

    @Test
    fun episodeDescriptionRange_checked() {
        assertTrue(AniSubEpisodeMatcher.isReleaseCompatibleWithEpisode("Paket", "1-3", null, 2))
        assertTrue(AniSubEpisodeMatcher.isReleaseCompatibleWithEpisode("Bölüm", "1-3", null, 2))
        assertFalse(AniSubEpisodeMatcher.isReleaseCompatibleWithEpisode("Bölüm", "1-3", null, 5))
    }

    @Test
    fun releaseNameSeasonEpisode_checked() {
        val release = "Shingeki No Kyojin (2013) S02E12 1080p Bluray x265-ASKO"
        assertTrue(AniSubEpisodeMatcher.isReleaseCompatibleWithEpisode("Bölüm: 12", null, release, 12, 2))
        assertFalse(AniSubEpisodeMatcher.isReleaseCompatibleWithEpisode(null, null, release, 11, 2))
    }

    @Test
    fun unknownMeta_isNotExcluded() {
        assertTrue(AniSubEpisodeMatcher.isReleaseCompatibleWithEpisode(null, null, "ClauseOver", 4))
    }

    @Test
    fun yearRangeIsNotEpisodeRange() {
        assertNull(AniSubEpisodeMatcher.parseEpisodeRange("2013-2024"))
        assertEquals(1..13, AniSubEpisodeMatcher.parseEpisodeRange("1-13"))
    }

    // ---- başlık eşleştirme ----

    @Test
    fun titleMatching_romajiAndEnglish() {
        assertTrue(
            AniSubEpisodeMatcher.titleMatches(
                "Attack on Titan", "Shingeki no Kyojin", "Attack on Titan"
            )
        )
        assertTrue(
            AniSubEpisodeMatcher.titleMatches(
                "Shingeki no Kyojin", "Shingeki no Kyojin", "Attack on Titan"
            )
        )
        assertFalse(
            AniSubEpisodeMatcher.titleMatches(
                "Naruto Shippuuden", "Shingeki no Kyojin", "Attack on Titan"
            )
        )
    }

    @Test
    fun searchVariants_includeMainTitle() {
        val variants = AniSubEpisodeMatcher.searchQueryVariants(
            "Shingeki no Kyojin: The Final Season - Kanketsu-hen"
        )
        assertTrue(variants.contains("Shingeki no Kyojin"))
        assertEquals("Shingeki no Kyojin: The Final Season - Kanketsu-hen", variants.first())
    }

    // ---- uzantı ----

    @Test
    fun subtitleExtensionsRecognized() {
        assertTrue(AniSubEpisodeMatcher.isSubtitleFileName("a/b/x.ASS"))
        assertTrue(AniSubEpisodeMatcher.isSubtitleFileName("x.srt"))
        assertFalse(AniSubEpisodeMatcher.isSubtitleFileName("Fontlar/Adobe.ttf"))
        assertFalse(AniSubEpisodeMatcher.isSubtitleFileName("Beni Oku.txt"))
    }
}
