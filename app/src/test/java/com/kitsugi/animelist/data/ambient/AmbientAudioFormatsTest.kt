package com.kitsugi.animelist.data.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientAudioFormatsTest {
    @Test
    fun `supported audio types normalize extensions and MIME values`() {
        val expected = mapOf(
            "mp3" to "audio/mpeg",
            "ogg" to "audio/ogg",
            "oga" to "audio/ogg",
            "opus" to "audio/opus",
            "flac" to "audio/flac",
            "m4a" to "audio/mp4",
            "aac" to "audio/aac",
            "wav" to "audio/wav",
            "aiff" to "audio/aiff",
            "amr" to "audio/amr",
            "3gp" to "audio/3gpp",
            "wma" to "audio/x-ms-wma",
            "ac3" to "audio/ac3",
            "eac3" to "audio/eac3",
            "webm" to "audio/webm",
            "ape" to "audio/ape",
            "wv" to "audio/wavpack",
            "tta" to "audio/tta",
            "caf" to "audio/x-caf",
            "au" to "audio/basic",
            "mka" to "audio/x-matroska",
        )

        expected.forEach { (extension, mimeType) ->
            assertEquals(extension, AmbientAudioFormats.canonicalType(extension))
            assertEquals(if (extension == "oga") "ogg" else extension, AmbientAudioFormats.canonicalType(mimeType))
            assertEquals(mimeType, AmbientAudioFormats.mimeType(extension))
            assertTrue(AmbientAudioFormats.isDirectAudioFile(extension))
        }
    }

    @Test
    fun `AAC conversion is attempted for uncommon or device-unsupported formats only`() {
        assertFalse(AmbientAudioFormats.shouldAttemptAacConversion("mp3", true))
        assertFalse(AmbientAudioFormats.shouldAttemptAacConversion("audio/mpeg", null))
        assertTrue(AmbientAudioFormats.shouldAttemptAacConversion("mp3", false))
        assertTrue(AmbientAudioFormats.shouldAttemptAacConversion("flac", true))
        assertTrue(AmbientAudioFormats.shouldAttemptAacConversion("opus", null))
    }

    @Test
    fun `HLS is supported for playback but never offered as an offline file`() {
        assertEquals("m3u8", AmbientAudioFormats.canonicalType("application/vnd.apple.mpegurl"))
        assertEquals("m3u8", AmbientAudioFormats.fileExtension("hls"))
        assertFalse(AmbientAudioFormats.isDirectAudioFile("m3u8"))
        assertFalse(AmbientAudioFormats.isDirectAudioFile("pdf"))
        assertEquals("", AmbientAudioFormats.canonicalType("application/x-unknown"))
    }

    @Test
    fun `known audio-only and radio formats can pass candidate filtering when their stream is safe`() {
        val plan = AmbientAudioPlanner.createPlan(AmbientAudioContext(title = "A manga"))
        AmbientAudioFormats.supportedPlaybackTypes.forEach { format ->
            val isHls = format in setOf("hls", "m3u8")
            val track = AmbientAudioTrack(
                id = "provider:$format",
                title = "Background instrumental",
                creator = "Composer",
                streamUrl = if (isHls) "https://radio.example/live.m3u8" else "https://audio.example/track.$format",
                landingUrl = "https://example.org/track",
                provider = if (isHls) "radio_browser" else "internet_archive",
                source = if (isHls) "radio_browser" else "internet_archive",
                durationMs = if (isHls) null else 120_000L,
                fileType = format,
            )
            assertTrue("$format should be recognized", AmbientAudioPlanner.isSafeCandidate(track))
        }
    }
}
