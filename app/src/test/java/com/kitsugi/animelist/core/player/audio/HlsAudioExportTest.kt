package com.kitsugi.animelist.core.player.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsAudioExportTest {
    private val base = "https://media.example/path/master.m3u8"

    @Test fun `quoted attributes and separate rendition with its own URI`() {
        val content = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="a",NAME="Turkish, Dub",LANGUAGE="tr",URI="tr.m3u8"
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="a",NAME="English",LANGUAGE="en",URI="en.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=340000,AUDIO="a"
            video.m3u8
        """.trimIndent()
        val tr = AudioTrackIdentity("tr", "Turkish, Dub")
        val result = HlsAudioExport.selectMaster(content, base, tr, listOf(tr, AudioTrackIdentity("en", "English")))!!
        assertTrue(result.isSeparateRendition)
        assertEquals("https://media.example/path/tr.m3u8", result.url)
        assertEquals("Turkish, Dub", HlsAudioExport.attributes("NAME=\"Turkish, Dub\",LANGUAGE=tr")["NAME"])
        assertThrows(IllegalArgumentException::class.java) {
            HlsAudioExport.attributes("NAME=\"Turkish, Dub\" junk,LANGUAGE=tr")
        }
    }

    @Test fun `muxed group picks a video variant only when its audio matches`() {
        val content = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="tr",NAME="Turkish",LANGUAGE="tr"
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="en",NAME="English",LANGUAGE="en"
            #EXT-X-STREAM-INF:BANDWIDTH=1000,AUDIO="en"
            en.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2000,AUDIO="tr"
            tr.m3u8
        """.trimIndent()
        val tr = AudioTrackIdentity("tr", null)
        val picked = HlsAudioExport.selectMaster(content, base, tr,
            listOf(tr, AudioTrackIdentity("en", null)))!!
        assertFalse(picked.isSeparateRendition)
        assertEquals("https://media.example/path/tr.m3u8", picked.url)
    }

    @Test fun `sole HLS rendition with conflicting declared language is not relabeled`() {
        val master = "#EXTM3U\n#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"a\",NAME=\"English\",LANGUAGE=\"en\",URI=\"en.m3u8\"\n#EXT-X-STREAM-INF:BANDWIDTH=1000,AUDIO=\"a\"\nvideo.m3u8"
        val tr = AudioTrackIdentity("tr", "Turkish")
        assertThrows(RuntimeException::class.java) {
            HlsAudioExport.selectMaster(master, base, tr, listOf(tr))
        }
    }

    @Test fun `same language renditions without a unique name are not guessed`() {
        val content = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="a",NAME="A",LANGUAGE="tr",URI="a.m3u8"
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="a",NAME="B",LANGUAGE="tr",URI="b.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=1000,AUDIO="a"
            video.m3u8
        """.trimIndent()
        assertThrows(RuntimeException::class.java) {
            HlsAudioExport.selectMaster(content, base, AudioTrackIdentity("tr", null),
                listOf(AudioTrackIdentity("tr", "A"), AudioTrackIdentity("tr", "B")))
        }
    }

    @Test fun `external audio URL is not confused with embedded video rows`() {
        val master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=32000\naudio.m3u8"
        val independent = AudioTrackIdentity(null, null, "https://media.example/voice/master.m3u8")
        val picked = HlsAudioExport.selectMaster(master, base, independent,
            listOf(independent, AudioTrackIdentity("en", "Video audio")))!!
        assertEquals("https://media.example/path/audio.m3u8", picked.url)
        // Two differently named rows on the SAME external playlist must not guess a variant.
        assertThrows(RuntimeException::class.java) {
            HlsAudioExport.selectMaster(master, base, independent,
                listOf(independent, independent.copy(name = "Other")))
        }
    }

    @Test fun `query-only HLS segment retains the playlist file path`() {
        val playlist = "#EXTM3U\n#EXTINF:1,\n?chunk=0\n#EXT-X-ENDLIST"
        val parts = HlsAudioExport.plan(playlist, "https://media.example/path/voice.ts?token=old", true).parts
        assertEquals("https://media.example/path/voice.ts?chunk=0", parts.single().url)
    }

    @Test fun `finite byte ranges resolve relative to current URL and next offset`() {
        val content = """
            #EXTM3U
            #EXT-X-MEDIA-SEQUENCE:5
            #EXT-X-BYTERANGE:10@0
            #EXTINF:2.0,
            seg.aac
            #EXT-X-BYTERANGE:6
            #EXTINF:2.0,
            seg.aac
            #EXT-X-ENDLIST
        """.trimIndent()
        val parts = HlsAudioExport.plan(content, base, true).parts
        assertEquals(2, parts.size)
        assertEquals("https://media.example/path/seg.aac", parts[0].url)
        assertEquals(AudioExportHttp.Range(0, 9), parts[0].range)
        assertEquals(AudioExportHttp.Range(10, 15), parts[1].range)
        assertEquals(5L, parts[0].sequence)
        assertEquals(6L, parts[1].sequence)
    }

    @Test fun `AES 128 uses media sequence for default IV`() {
        val content = """
            #EXTM3U
            #EXT-X-MEDIA-SEQUENCE:7
            #EXT-X-KEY:METHOD=AES-128,URI="key.bin"
            #EXTINF:4.0,
            segment.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        val part = HlsAudioExport.plan(content, base, false).parts.single()
        assertEquals("https://media.example/path/key.bin", part.keyUrl)
        assertArrayEquals(ByteArray(15) + byteArrayOf(7), part.iv)
    }

    @Test fun `missing variant URI cannot steal the following audio group`() {
        val master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=10,AUDIO=\"en\"\n" +
            "#EXT-X-STREAM-INF:BANDWIDTH=20,AUDIO=\"tr\"\ntr.m3u8"
        assertThrows(RuntimeException::class.java) {
            HlsAudioExport.selectMaster(master, base, AudioTrackIdentity("en", null),
                listOf(AudioTrackIdentity("en", null), AudioTrackIdentity("tr", null)))
        }
    }

    @Test fun `invalid media sequence cannot silently change AES IV`() {
        val content = "#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:bad\n#EXTINF:2,\nseg.ts\n#EXT-X-ENDLIST"
        assertThrows(RuntimeException::class.java) { HlsAudioExport.plan(content, base, false) }
    }

    @Test fun `master session DRM is rejected before fetching renditions`() {
        val master = "#EXTM3U\n#EXT-X-SESSION-KEY:METHOD=SAMPLE-AES,URI=\"key\"\n" +
            "#EXT-X-STREAM-INF:BANDWIDTH=1000\nvideo.m3u8"
        assertThrows(IllegalArgumentException::class.java) {
            HlsAudioExport.selectMaster(master, base, AudioTrackIdentity(null, null),
                listOf(AudioTrackIdentity(null, null)))
        }
    }

    @Test fun `live and discontinuous playlists and non identity DRM are rejected`() {
        val segment = "#EXTM3U\n#EXTINF:2,\nseg.ts"
        assertThrows(IllegalArgumentException::class.java) { HlsAudioExport.plan(segment, base, false) }
        assertThrows(IllegalArgumentException::class.java) {
            HlsAudioExport.plan("$segment\n#EXT-X-DISCONTINUITY\n#EXT-X-ENDLIST", base, false)
        }
        assertThrows(RuntimeException::class.java) {
            HlsAudioExport.plan("#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"key\"\n" +
                "#EXTINF:2,\nseg.ts\n#EXT-X-ENDLIST", base, false)
        }
    }
}
