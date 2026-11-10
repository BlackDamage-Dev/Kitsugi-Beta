package com.kitsugi.animelist.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KitsugiAnimeThemesClientTest {

    @Test
    fun noCandidateYieldsNoLink() {
        assertNull(KitsugiAnimeThemesClient.pickBestThemeVideoLink(emptyList()))
        assertNull(
            KitsugiAnimeThemesClient.pickBestThemeVideoLink(
                listOf(candidate(link = "  "))
            )
        )
    }

    @Test
    fun cleanVideoWinsOverBurnedInSubtitles() {
        val link = KitsugiAnimeThemesClient.pickBestThemeVideoLink(
            listOf(
                candidate(link = "https://a.animethemes.moe/op1-subbed.webm", isSubbed = true, resolution = 1080),
                candidate(link = "https://a.animethemes.moe/op1.webm", isSubbed = false, resolution = 480)
            )
        )
        assertEquals("https://a.animethemes.moe/op1.webm", link)
    }

    @Test
    fun mp4IsPreferredBecauseMedia3DecodesH264Everywhere() {
        val link = KitsugiAnimeThemesClient.pickBestThemeVideoLink(
            listOf(
                candidate(link = "https://a.animethemes.moe/op1.webm", isNc = true, resolution = 1080),
                candidate(link = "https://a.animethemes.moe/op1.mp4", isNc = true, resolution = 720)
            )
        )
        assertEquals("https://a.animethemes.moe/op1.mp4", link)
    }

    @Test
    fun creditlessWebVersionBeatsHeavierBluRayRemux() {
        val link = KitsugiAnimeThemesClient.pickBestThemeVideoLink(
            listOf(
                candidate(link = "https://a.animethemes.moe/op1-bd.webm", isNc = false, resolution = 1080, source = "BD"),
                candidate(link = "https://a.animethemes.moe/op1.webm", isNc = true, resolution = 720, source = "WEB")
            )
        )
        assertEquals("https://a.animethemes.moe/op1.webm", link)
    }

    @Test
    fun resolutionAbove1080IsRankedBehindPlayableSizes() {
        val link = KitsugiAnimeThemesClient.pickBestThemeVideoLink(
            listOf(
                candidate(link = "https://a.animethemes.moe/op1-4k.webm", isNc = true, resolution = 2160, source = "WEB"),
                candidate(link = "https://a.animethemes.moe/op1.webm", isNc = true, resolution = 720, source = "WEB")
            )
        )
        assertEquals("https://a.animethemes.moe/op1.webm", link)
    }

    @Test
    fun highestPlayableResolutionWinsWithinTheSameSource() {
        val link = KitsugiAnimeThemesClient.pickBestThemeVideoLink(
            listOf(
                candidate(link = "https://a.animethemes.moe/op1-480.webm", isNc = true, resolution = 480),
                candidate(link = "https://a.animethemes.moe/op1-1080.webm", isNc = true, resolution = 1080),
                candidate(link = "https://a.animethemes.moe/op1-720.webm", isNc = true, resolution = 720)
            )
        )
        assertEquals("https://a.animethemes.moe/op1-1080.webm", link)
    }

    @Test
    fun mp4DetectionIgnoresQueryParameters() {
        assertTrue("https://a.animethemes.moe/op1.mp4?token=1".isMp4Link())
        assertTrue("https://a.animethemes.moe/op1.MP4".isMp4Link())
        assertTrue(!"https://a.animethemes.moe/op1.webm".isMp4Link())
    }

    @Test
    fun rankedLinksKeepEveryPlayableFallbackInOrder() {
        val ranked = KitsugiAnimeThemesClient.rankThemeVideoLinks(
            listOf(
                candidate(link = "https://a.animethemes.moe/op1.webm", isNc = true, resolution = 1080),
                candidate(link = "https://a.animethemes.moe/op1.mp4", isNc = true, resolution = 720),
                candidate(link = "https://a.animethemes.moe/op1-480.webm", isNc = true, resolution = 480),
                candidate(link = "https://a.animethemes.moe/op1.mp4", isNc = true, resolution = 720)
            )
        )
        // Birincil: en iyi dosya (mp4/720p). Yedekler hafiften ağıra: 480p önce gelir,
        // çünkü yedek yalnızca cihaz birinci dosyayı çözemediğinde deneniyor.
        assertEquals(
            listOf(
                "https://a.animethemes.moe/op1.mp4",
                "https://a.animethemes.moe/op1-480.webm",
                "https://a.animethemes.moe/op1.webm"
            ),
            ranked
        )
        // Zincirin ilk elemanı tek-adres API'siyle aynı olmalı (geriye dönük uyum).
        assertEquals(ranked.first(), KitsugiAnimeThemesClient.pickBestThemeVideoLink(
            listOf(
                candidate(link = "https://a.animethemes.moe/op1.webm", isNc = true, resolution = 1080),
                candidate(link = "https://a.animethemes.moe/op1.mp4", isNc = true, resolution = 720)
            )
        ))
    }

    private fun candidate(
        link: String,
        resolution: Int = 720,
        isSubbed: Boolean = false,
        isNc: Boolean = true,
        source: String = "WEB"
    ) = ThemeVideoCandidate(
        link = link,
        resolution = resolution,
        isSubbed = isSubbed,
        isNc = isNc,
        source = source
    )
}
