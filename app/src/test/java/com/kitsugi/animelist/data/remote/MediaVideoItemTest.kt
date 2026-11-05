package com.kitsugi.animelist.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaVideoItemTest {

    @Test
    fun extractCanonicalYouTubeIdSupportsAllCommonFormats() {
        assertEquals("dQw4w9WgXcQ", extractCanonicalYouTubeId("dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", extractCanonicalYouTubeId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&feature=youtu.be"))
        assertEquals("dQw4w9WgXcQ", extractCanonicalYouTubeId("https://youtu.be/dQw4w9WgXcQ?t=12"))
        assertEquals("dQw4w9WgXcQ", extractCanonicalYouTubeId("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?enablejsapi=1"))
        assertEquals("dQw4w9WgXcQ", extractCanonicalYouTubeId("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
        assertNull(extractCanonicalYouTubeId("https://v.animethemes.moe/KaijuNo8-OP1.webm"))
        assertNull(extractCanonicalYouTubeId(null))
    }

    @Test
    fun canonicalVideoSourceNormalizesKnownProvidersAndPreservesPluginNames() {
        assertEquals("mal", canonicalVideoSource("Jikan (MAL)"))
        assertEquals("mal", canonicalVideoSource("myanimelist"))
        assertEquals("anilist", canonicalVideoSource("AniList"))
        assertEquals("tmdb", canonicalVideoSource("themoviedb"))
        assertEquals("animethemes", canonicalVideoSource("animethemes.moe"))
        assertEquals("shikimori", canonicalVideoSource("Shikimori"))
        assertEquals("kitsu", canonicalVideoSource("Kitsu"))
        assertEquals("simkl", canonicalVideoSource("SIMKL"))
        assertEquals("Diziwatch", canonicalVideoSource("Diziwatch"))
    }

    @Test
    fun mergeAndOrderMediaVideosDeduplicatesSameYouTubeIdAndPreservesAllSources() {
        val candidates = listOf(
            MediaVideoItem(
                key = "seed",
                title = "Fragmanı İzle",
                videoUrl = "https://www.youtube.com/watch?v=abc123XYZ89",
                category = MediaVideoCategory.TRAILER,
                source = "anilist"
            ),
            MediaVideoItem(
                key = "mal_pv1",
                title = "PV 1 - Official Main Trailer",
                videoUrl = "https://youtu.be/abc123XYZ89",
                category = MediaVideoCategory.TRAILER,
                source = "mal"
            ),
            MediaVideoItem(
                key = "kitsu_pv",
                title = "Trailer",
                videoUrl = "https://www.youtube.com/embed/abc123XYZ89",
                category = MediaVideoCategory.TRAILER,
                source = "kitsu"
            )
        )

        val merged = mergeAndOrderMediaVideos(candidates, preferredSource = "mal")
        assertEquals(1, merged.size)
        val item = merged.single()
        assertEquals("PV 1 - Official Main Trailer", item.title)
        assertEquals("mal", item.source)
        assertEquals(listOf("mal", "anilist", "kitsu"), item.sources)
    }

    @Test
    fun mergeAndOrderMediaVideosDropsUnplayableTextDuplicateWhenAnimeThemesStreamExists() {
        val candidates = listOf(
            MediaVideoItem(
                key = "at_op1",
                title = "\"Abyss\" by YUNGBLUD (OP1)",
                videoUrl = "https://v.animethemes.moe/KaijuNo8-OP1.webm",
                category = MediaVideoCategory.OPENING,
                source = "animethemes"
            ),
            MediaVideoItem(
                key = "mal_text_op1",
                title = "1: \"Abyss\" by YUNGBLUD (eps 1-12)",
                videoUrl = null,
                category = MediaVideoCategory.OPENING,
                source = "mal"
            )
        )

        val merged = mergeAndOrderMediaVideos(candidates, preferredSource = "mal")
        assertEquals(1, merged.size)
        assertEquals("https://v.animethemes.moe/KaijuNo8-OP1.webm", merged.single().videoUrl)
        assertEquals(listOf("animethemes"), merged.single().sources)
    }

    @Test
    fun tmdbStructuredVideosMergesLocalizedEnglishAndSeasonWithoutDuplicates() = runBlocking {
        val items = TmdbMediaDetailClient.fetchStructuredVideos(
            tmdbId = 999,
            isMovie = false,
            apiKey = "test-key",
            language = "tr-TR",
            seasonNumber = 2,
            executeGet = { url ->
                when {
                    "/season/2/videos" in url -> """{"results":[{"site":"YouTube","type":"Trailer","key":"season2Key11","name":"Season 2 PV"}]}"""
                    "language=tr-TR" in url -> """{"results":[{"site":"YouTube","type":"Trailer","key":"sharedKey001","name":"Türkçe Fragman"}]}"""
                    "language=en-US" in url -> """{"results":[
                        {"site":"YouTube","type":"Trailer","key":"sharedKey001","name":"Official Trailer"},
                        {"site":"YouTube","type":"Opening Credits","key":"opKey000002","name":"NCOP"},
                        {"site":"YouTube","type":"Featurette","key":"featKey0003","name":"Behind the Studio"}
                    ]}"""
                    else -> null
                }
            }
        )

        assertEquals(4, items.size)
        val shared = items.first { extractCanonicalYouTubeId(it.videoUrl) == "sharedKey001" }
        assertTrue(shared.title.contains("Türkçe Fragman"))
        assertEquals(MediaVideoCategory.TRAILER, shared.category)
        assertEquals(
            MediaVideoCategory.OPENING,
            items.first { extractCanonicalYouTubeId(it.videoUrl) == "opKey000002" }.category
        )
        assertEquals(
            MediaVideoCategory.EXTRA,
            items.first { extractCanonicalYouTubeId(it.videoUrl) == "featKey0003" }.category
        )
        assertTrue(items.all { it.source == "tmdb" })
    }

    @Test
    fun mediaVideoItemHandlesNullAlternateUrlsGracefully() {
        // Crash regression test for Task A13 (2026-11-05):
        // Ensure alternateUrls = null does not throw NPE on instantiation or merge
        val itemWithNull = MediaVideoItem(
            key = "test_key",
            title = "Test Title",
            videoUrl = "https://example.com/video.mp4",
            category = MediaVideoCategory.OPENING,
            source = "test",
            alternateUrls = null
        )

        assertTrue(itemWithNull.safeAlternateUrls.isEmpty())

        val merged = mergeAndOrderMediaVideos(listOf(itemWithNull))
        assertEquals(1, merged.size)
        assertTrue(merged.first().safeAlternateUrls.isEmpty())
    }

    @Test
    fun seedFromDetailReturnsEmptyListOnNullGracefully() {
        val result = KitsugiMediaVideoAggregator.seedFromDetail(null, "anilist")
        assertTrue(result.isEmpty())
    }
}
