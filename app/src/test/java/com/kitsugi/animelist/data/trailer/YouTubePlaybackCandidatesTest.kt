package com.kitsugi.animelist.data.trailer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubePlaybackCandidatesTest {
    @Test
    fun unsolvedNChallengeAndExcessiveResolutionAreDeprioritized() {
        val sorted = YouTubePlaybackCandidates.sortedVideos(
            listOf(
                video(url = "https://v/4k", height = 2160),
                video(url = "https://v/n", height = 1080, hasN = true),
                video(url = "https://v/720", height = 720),
                video(url = "https://v/1080", height = 1080)
            )
        )

        assertEquals(listOf("https://v/1080", "https://v/720", "https://v/4k", "https://v/n"), sorted.map { it.url })
    }

    @Test
    fun maintainedClientPriorityBeatsContainerPreference() {
        val sorted = YouTubePlaybackCandidates.sortedVideos(
            listOf(
                video(client = "ios", priority = 2, url = "https://v/ios", ext = "mp4"),
                video(client = "tv", priority = 0, url = "https://v/tv", ext = "webm")
            )
        )

        assertEquals("https://v/tv", sorted.first().url)
    }

    @Test
    fun adaptivePairsNeverMixSignaturesFromDifferentClients() {
        val tvVideo = video(client = "tv", priority = 0, url = "https://v/tv")
        val visionVideo = video(client = "vision", priority = 1, url = "https://v/vision")
        val visionAudio = audio(client = "vision", priority = 1, url = "https://a/vision")

        val pairs = YouTubePlaybackCandidates.adaptivePairs(
            videos = listOf(visionVideo, tvVideo),
            audios = listOf(visionAudio)
        )

        assertEquals(1, pairs.size)
        assertEquals(visionVideo to visionAudio, pairs.single())
    }

    @Test
    fun orderedSourcesKeepClientHeadersAndIndependentFallbacks() {
        val manifest = YouTubeManifestCandidate(
            client = "tv",
            priority = 0,
            manifestUrl = "https://manifest/tv",
            width = 1920,
            height = 1080,
            bandwidth = 4_000_000
        )
        val video = video(client = "vision", priority = 1, url = "https://v/vision")
        val audio = audio(client = "vision", priority = 1, url = "https://a/vision")
        val progressive = video(client = "tv", priority = 0, url = "https://v/progressive")

        val sources = YouTubePlaybackCandidates.buildOrderedSources(
            manifests = listOf(manifest),
            adaptiveVideos = listOf(video),
            adaptiveAudios = listOf(audio),
            progressive = listOf(progressive),
            headersForClient = { client -> mapOf("User-Agent" to "ua-$client") }
        )

        assertEquals(
            listOf("https://manifest/tv", "https://v/vision", "https://v/progressive"),
            sources.map { it.videoUrl }
        )
        assertEquals("ua-tv", sources[0].requestHeaders["User-Agent"])
        assertEquals("ua-vision", sources[1].requestHeaders["User-Agent"])
        assertEquals("https://a/vision", sources[1].audioUrl)
        assertEquals(null, sources[2].audioUrl)
    }

    @Test
    fun sourceListIsDeduplicatedAndBounded() {
        val manifests = (0 until 6).map { index ->
            YouTubeManifestCandidate(
                client = "c$index",
                priority = index,
                manifestUrl = "https://manifest/$index",
                width = 1280,
                height = 720,
                bandwidth = 2_000_000
            )
        }
        val sources = YouTubePlaybackCandidates.buildOrderedSources(
            manifests = manifests + manifests.first(),
            adaptiveVideos = emptyList(),
            adaptiveAudios = emptyList(),
            progressive = emptyList(),
            headersForClient = { emptyMap() }
        )

        assertEquals(4, sources.size)
        assertEquals(sources.size, sources.map { it.videoUrl }.toSet().size)
        assertTrue(sources.none { it.alternatives.isNotEmpty() })
    }

    private fun video(
        client: String = "tv",
        priority: Int = 0,
        url: String,
        height: Int = 1080,
        hasN: Boolean = false,
        ext: String = "mp4"
    ) = YouTubeStreamCandidate(
        client = client,
        priority = priority,
        url = url,
        score = height * 1_000_000.0,
        hasN = hasN,
        itag = "137",
        height = height,
        fps = 30,
        ext = ext
    )

    private fun audio(client: String, priority: Int, url: String) = YouTubeStreamCandidate(
        client = client,
        priority = priority,
        url = url,
        score = 128_000.0,
        hasN = false,
        itag = "140",
        height = 0,
        fps = 0,
        ext = "m4a"
    )
}
