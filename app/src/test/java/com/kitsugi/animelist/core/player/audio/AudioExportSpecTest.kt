package com.kitsugi.animelist.core.player.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioExportSpecTest {
    @Test fun `language normalization and filenames`() {
        assertEquals("tr", AudioExportSpec.language("tur-TR"))
        assertEquals("ja", AudioExportSpec.language("JPN_jp"))
        assertEquals("tr", AudioExportSpec.language("Türkçe"))
        assertEquals("tr", AudioExportSpec.language("Turkish"))
        assertEquals("en", AudioExportSpec.language("English"))
        assertEquals("", AudioExportSpec.language("und"))
        assertEquals("Hello_World", AudioExportSpec.filenamePart("  Hello/World  ", "untitled"))
    }

    @Test fun `source uses only the row's proven external URL or its video URL`() {
        val video = "https://video.example/movie.mp4"
        val audio = "https://audio.example/voice.m4a"
        val selected = AudioTrackIdentity("tr", "Dub", audio)
        assertEquals(audio, AudioExportSpec.source(video, selected))
        assertEquals(video, AudioExportSpec.source(video, AudioTrackIdentity("tr", null)))
        assertThrows(IllegalArgumentException::class.java) {
            AudioExportSpec.source(null, AudioTrackIdentity("tr", null))
        }
    }

    @Test fun `physical track language mismatch and duplicate language fail closed`() {
        val one = listOf(AudioExportSpec.Candidate(5, "eng"))
        assertEquals(5, AudioExportSpec.select(one, "en"))
        assertThrows(AudioTrackSelectionException::class.java) { AudioExportSpec.select(one, "tr") }
        val unknown = listOf(AudioExportSpec.Candidate(3, "und"))
        assertEquals(3, AudioExportSpec.select(unknown, "tr")) // A separately identified audio URL is sufficient.
        assertThrows(AudioTrackSelectionException::class.java) {
            AudioExportSpec.select(unknown, "tr", requireKnownLanguage = true) // But not an ambiguous shared video.
        }
        val many = listOf(AudioExportSpec.Candidate(4, "eng"), AudioExportSpec.Candidate(2, "tur"))
        assertEquals(2, AudioExportSpec.select(many, "tr-TR"))
        assertThrows(IllegalArgumentException::class.java) { AudioExportSpec.select(many, null) }
        assertThrows(IllegalArgumentException::class.java) {
            AudioExportSpec.select(many + AudioExportSpec.Candidate(8, "tur"), "tr")
        }
    }

    @Test fun `manifest needs a unique name only when language cannot distinguish`() {
        val tr = AudioTrackIdentity("tr", "Dublaj")
        assertFalse(AudioExportSpec.needsManifestName(tr, listOf(tr, AudioTrackIdentity("en", null))))
        assertTrue(AudioExportSpec.needsManifestName(tr, listOf(tr, AudioTrackIdentity("tr", "Orijinal"))))
        val dub = tr.copy(externalUrl = "https://cdn.example/audio.m3u8")
        assertFalse(AudioExportSpec.needsManifestName(dub, listOf(dub, tr)))
        assertTrue(AudioExportSpec.needsManifestName(dub, listOf(dub, dub.copy(name = "Alternate"))))
    }

    @Test fun `plugin transport overrides an opaque or misleading CDN filename`() {
        assertEquals("hls", AudioExportTransport.known("https://cdn.example/media.mp4", "hls"))
        assertEquals("dash", AudioExportTransport.known("https://cdn.example/manifest?token=secret", "dash"))
        assertEquals("hls", AudioExportTransport.known("https://cdn.example/master.m3u8?token=secret", null))
        assertFalse(AudioExportTransport.needsSniff("https://cdn.example/one-time.mp4?token=secret", null))
        assertTrue(AudioExportTransport.needsSniff("https://cdn.example/master.txt?token=secret", null))
        assertTrue(AudioExportTransport.needsSniff("https://cdn.example/get_video?token=one-time", "progressive"))
        assertTrue(AudioExportTransport.needsSniff("https://cdn.example/master.txt?token=one-time", "progressive"))
        assertEquals("hls", AudioExportTransport.known("https://cdn.example/master.m3u8", "progressive"))
        assertEquals("flv", AudioExportTransport.known("https://cdn.example/get_video.flv?sig=abc", null))
        assertFalse(AudioExportTransport.requiresSafeCopy(mapOf("Referer" to "https://site.example/")))
        assertTrue(AudioExportTransport.requiresSafeCopy(mapOf("Authorization" to "Bearer private")))
        assertTrue(AudioExportTransport.requiresSafeCopy(mapOf("X-Sp" to "private")))
    }

    @Test fun `DASH empty relative URL keeps the base file`() {
        assertEquals("https://cdn.example/voice.mp4",
            AudioExportAddress.resolve("https://cdn.example/voice.mp4", ""))
        assertEquals("https://cdn.example/voice.mp4?next=1",
            AudioExportAddress.resolve("https://cdn.example/voice.mp4?old=1", "?next=1"))
        assertThrows(IllegalArgumentException::class.java) {
            AudioExportAddress.resolve("https://cdn.example/master.m3u8", "http://localhost/internal")
        }
    }

    @Test fun `cross origin header filtering does not forward credentials`() {
        val headers = mapOf("Authorization" to "Bearer private", "Cookie" to "session=private",
            "Referer" to "https://provider.example/", "User-Agent" to "Kitsugi",
            "Range" to "bytes=0-9", "Accept-Encoding" to "gzip")
        val origin = "https://media.example:443/master.m3u8"
        val same = AudioExportAddress.headersFor(origin, "https://media.example/part.ts", headers)
        assertEquals("Bearer private", same["Authorization"])
        assertFalse(same.containsKey("Range"))
        assertFalse(same.containsKey("Accept-Encoding")) // Byte-range positions require identity bytes.
        val different = AudioExportAddress.headersFor(origin, "https://cdn.example/part.ts", headers)
        assertFalse(different.containsKey("Authorization"))
        assertFalse(different.containsKey("Cookie"))
        assertEquals("Kitsugi", different["User-Agent"])
    }
}
