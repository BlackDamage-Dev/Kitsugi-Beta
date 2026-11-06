package com.kitsugi.animelist.core.player

import org.junit.Assert.*
import org.junit.Test

class OpenSubtitlesHasherRangeTest {
    @Test fun requiresExactPartialResponse() {
        assertTrue(OpenSubtitlesHasher.validRange(206, "bytes 0-65535/200000", 0, 65535, 200000))
        assertFalse(OpenSubtitlesHasher.validRange(200, null, 0, 65535, 200000))
        assertFalse(OpenSubtitlesHasher.validRange(206, "bytes 1-65536/200000", 0, 65535, 200000))
    }

    @Test fun skipsAdaptivePlaylistsAndLocalTorrentProxyUrls() {
        assertTrue(OpenSubtitlesHasher.canProbeUrl("https://cdn.example.com/videos/episode01.mkv"))
        assertTrue(OpenSubtitlesHasher.canProbeUrl("https://cdn.example.com/stream/12345?token=abc"))
        assertFalse(OpenSubtitlesHasher.canProbeUrl("https://cdn.example.com/hls/master.m3u8?sig=1"))
        assertFalse(OpenSubtitlesHasher.canProbeUrl("https://cdn.example.com/dash/manifest.mpd"))
        assertFalse(OpenSubtitlesHasher.canProbeUrl("http://127.0.0.1:11470/stream/0/video.mkv"))
        assertFalse(OpenSubtitlesHasher.canProbeUrl("http://localhost:8080/torrent/piece.mp4"))
        assertFalse(OpenSubtitlesHasher.canProbeUrl("file:///sdcard/Download/ep1.mkv"))
    }
}
