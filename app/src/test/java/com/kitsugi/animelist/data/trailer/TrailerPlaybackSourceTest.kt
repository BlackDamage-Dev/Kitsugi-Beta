package com.kitsugi.animelist.data.trailer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Yedek akış listesi, "video oynamıyor" hatasının düzeltmesinin çekirdeğidir:
 * oynatıcı birincil akışı açamazsa bu listeden sırayla ilerler.
 */
class TrailerPlaybackSourceTest {

    @Test
    fun primaryStreamIsIndexZero() {
        val source = TrailerPlaybackSource(
            videoUrl = "https://primary.example/a.m3u8",
            audioUrl = null,
            alternateVideoUrls = listOf("https://alt.example/b.mp4"),
            alternateAudioUrls = listOf(null)
        )
        assertEquals(2, source.totalSources)
        assertEquals("https://primary.example/a.m3u8" to null, source.sourceAt(0))
        assertEquals("https://alt.example/b.mp4" to null, source.sourceAt(1))
    }

    @Test
    fun alternateAudioIsMatchedByIndex() {
        val source = TrailerPlaybackSource(
            videoUrl = "https://primary.example/v.mp4",
            audioUrl = "https://primary.example/a.m4a",
            alternateVideoUrls = listOf("https://alt.example/v.mp4", "https://alt.example/p.mp4"),
            alternateAudioUrls = listOf("https://alt.example/a.m4a", null)
        )
        assertEquals("https://alt.example/a.m4a", source.sourceAt(1)?.second)
        assertEquals("https://alt.example/p.mp4" to null, source.sourceAt(2))
    }

    @Test
    fun outOfRangeIndexReturnsNull() {
        val source = TrailerPlaybackSource(videoUrl = "https://primary.example/a.mp4")
        assertNull(source.sourceAt(1))
        assertEquals(1, source.totalSources)
    }

    @Test
    fun singleStreamSourceHasOneCandidate() {
        val source = TrailerPlaybackSource(videoUrl = "https://cdn.example/theme.webm")
        assertTrue(source.alternateVideoUrls.isEmpty())
        assertEquals("https://cdn.example/theme.webm", source.sourceAt(0)?.first)
    }

    @Test
    fun candidatesAreFlatDistinctAndNeverBlank() {
        val source = TrailerPlaybackSource(
            videoUrl = "https://cdn/primary",
            alternatives = listOf(
                TrailerPlaybackSource(videoUrl = "https://cdn/alternate"),
                TrailerPlaybackSource(videoUrl = "https://cdn/primary"),
                TrailerPlaybackSource(videoUrl = " ")
            )
        )

        val candidates = source.playbackCandidates()

        assertEquals(listOf("https://cdn/primary", "https://cdn/alternate"), candidates.map { it.videoUrl })
        assertTrue(candidates.all { it.alternatives.isEmpty() })
    }

    @Test
    fun expiryIncludesAlternativeAudioAndVideoUrls() {
        val source = TrailerPlaybackSource(
            videoUrl = "https://cdn/video?expire=1900000300",
            audioUrl = "https://cdn/audio?expire=1900000400",
            alternatives = listOf(
                TrailerPlaybackSource(
                    videoUrl = "https://cdn/alternate?expire=1900000500",
                    audioUrl = "https://cdn/alternate-audio?expire=1900000200"
                )
            )
        )

        assertEquals(1_900_000_200L, source.earliestYouTubeExpiryEpochSeconds())
    }

    @Test
    fun orderedListBecomesOnePrimaryWithAllOtherCandidatesAsAlternatives() {
        val sources = listOf(
            TrailerPlaybackSource(videoUrl = "https://cdn/1"),
            TrailerPlaybackSource(videoUrl = "https://cdn/2"),
            TrailerPlaybackSource(videoUrl = "https://cdn/3")
        )

        val primary = sources.asPrimaryWithAlternatives()!!

        assertEquals("https://cdn/1", primary.videoUrl)
        assertEquals(listOf("https://cdn/2", "https://cdn/3"), primary.alternatives.map { it.videoUrl })
        assertEquals(3, primary.playbackCandidates().size)
    }
}
