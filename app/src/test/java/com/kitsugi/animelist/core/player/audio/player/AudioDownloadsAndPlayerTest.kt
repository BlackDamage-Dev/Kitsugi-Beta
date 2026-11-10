package com.kitsugi.animelist.core.player.audio.player

import com.kitsugi.animelist.core.download.DownloadExportNaming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDownloadsAndPlayerTest {

    @Test
    fun `parseAudioFileName extracts anime episode language and format from AudioExportWorker name`() {
        val parsed = DownloadExportNaming.parseAudioFileName("Date A Live - Bölüm 4.tr Türkçe.m4a")
        assertEquals("Date A Live", parsed.animeTitle)
        assertEquals(4, parsed.episode)
        assertEquals("Date A Live • Bölüm 4", parsed.displayTitle)
        assertEquals("tr Türkçe", parsed.trackLabel)
        assertEquals("TR", parsed.languageBadge)
        assertEquals("m4a", parsed.extension)
    }

    @Test
    fun `parseAudioFileName handles duplicate MediaStore counter and movie export`() {
        val dup = DownloadExportNaming.parseAudioFileName("Solo Leveling - Bölüm 12.ja Japanese (1).eac3")
        assertEquals("Solo Leveling", dup.animeTitle)
        assertEquals(12, dup.episode)
        assertEquals("JA", dup.languageBadge)
        assertEquals("eac3", dup.extension)

        val movie = DownloadExportNaming.parseAudioFileName("Suzume.en English Dub.flac")
        assertEquals("Suzume", movie.animeTitle)
        assertNull(movie.episode)
        assertEquals("EN", movie.languageBadge)
        assertEquals("flac", movie.extension)
    }

    @Test
    fun `parseAudioFileName preserves arbitrary music filenames without false language split`() {
        val song = DownloadExportNaming.parseAudioFileName("01. Unravel - TK.opus")
        assertNull(song.episode)
        assertEquals("opus", song.extension)
        assertTrue(song.displayTitle.contains("Unravel"))
    }

    @Test
    fun `isAudioFileName recognizes all standard and exotic audio formats and scopes webm properly`() {
        listOf(
            "track.mp3", "track.m4a", "track.aac", "track.flac", "track.wav",
            "track.ogg", "track.opus", "track.mka", "track.eac3", "track.ac3",
            "track.dts", "track.thd", "track.amr", "track.wma", "track.ape",
            "track.wv", "track.aiff", "track.caf", "track.dsf"
        ).forEach { name ->
            assertTrue("Expected audio file: $name", AudioFormatSupport.isAudioFileName(name))
        }

        // .webm is video in general folders, but audio inside Kitsugi/Audio (Opus/Vorbis export)
        assertFalse(AudioFormatSupport.isAudioFileName("episode.webm", allowWebmAsAudio = false))
        assertTrue(AudioFormatSupport.isAudioFileName("episode.ja.webm", allowWebmAsAudio = true))
        assertFalse(AudioFormatSupport.isAudioFileName("episode.mp4", allowWebmAsAudio = true))
    }

    @Test
    fun `prefersMpvDirectly routes exotic containers to libmpv while keeping standard formats on Media3`() {
        assertTrue(AudioFormatSupport.prefersMpvDirectly("ost.wma"))
        assertTrue(AudioFormatSupport.prefersMpvDirectly("lossless.ape"))
        assertTrue(AudioFormatSupport.prefersMpvDirectly("album.wv"))
        assertTrue(AudioFormatSupport.prefersMpvDirectly("surround.dts"))
        assertTrue(AudioFormatSupport.prefersMpvDirectly("master.aiff"))
        assertTrue(AudioFormatSupport.prefersMpvDirectly("sacd.dsf"))

        assertFalse(AudioFormatSupport.prefersMpvDirectly("dub.m4a"))
        assertFalse(AudioFormatSupport.prefersMpvDirectly("dub.mp3"))
        assertFalse(AudioFormatSupport.prefersMpvDirectly("dub.aac"))
        assertFalse(AudioFormatSupport.prefersMpvDirectly("dub.flac"))
        assertFalse(AudioFormatSupport.prefersMpvDirectly("dub.eac3"))
        assertFalse(AudioFormatSupport.prefersMpvDirectly("dub.webm"))
    }

    @Test
    fun `queue navigation obeys repeat OFF ALL ONE and shuffle order`() {
        // Repeat OFF at end of queue stops on autoAdvance
        assertNull(
            AudioFormatSupport.resolveNextIndex(
                currentIndex = 2,
                queueSize = 3,
                repeatMode = AudioRepeatMode.OFF,
                shuffleOrder = null,
                autoAdvance = true
            )
        )

        // Repeat ALL wraps to start
        assertEquals(
            0,
            AudioFormatSupport.resolveNextIndex(
                currentIndex = 2,
                queueSize = 3,
                repeatMode = AudioRepeatMode.ALL,
                shuffleOrder = null,
                autoAdvance = true
            )
        )

        // Repeat ONE repeats same track on autoAdvance, but advances on manual skipNext
        assertEquals(
            1,
            AudioFormatSupport.resolveNextIndex(
                currentIndex = 1,
                queueSize = 3,
                repeatMode = AudioRepeatMode.ONE,
                shuffleOrder = null,
                autoAdvance = true
            )
        )
        assertEquals(
            2,
            AudioFormatSupport.resolveNextIndex(
                currentIndex = 1,
                queueSize = 3,
                repeatMode = AudioRepeatMode.ONE,
                shuffleOrder = null,
                autoAdvance = false
            )
        )

        // Shuffle order [1, 3, 0, 2]
        val shuffle = listOf(1, 3, 0, 2)
        assertEquals(
            3,
            AudioFormatSupport.resolveNextIndex(
                currentIndex = 1,
                queueSize = 4,
                repeatMode = AudioRepeatMode.OFF,
                shuffleOrder = shuffle,
                autoAdvance = true
            )
        )
        assertEquals(
            1,
            AudioFormatSupport.resolvePrevIndex(
                currentIndex = 3,
                queueSize = 4,
                repeatMode = AudioRepeatMode.OFF,
                shuffleOrder = shuffle
            )
        )
    }

    @Test
    fun `stream specs badges and duration formatting produce clean UI labels`() {
        val specs = AudioStreamSpecs(
            codec = "audio/eac3",
            sampleRateHz = 48_000,
            channelCount = 6,
            bitrateKbps = 640,
            container = "eac3"
        )
        assertEquals(listOf("E-AC-3", "48 kHz", "5.1", "640 kbps"), specs.summaryBadges("EAC3"))
        assertEquals("04:07", AudioFormatSupport.formatDuration(247_000L))
        assertEquals("1:02:09", AudioFormatSupport.formatDuration(3_729_000L))
    }
}
