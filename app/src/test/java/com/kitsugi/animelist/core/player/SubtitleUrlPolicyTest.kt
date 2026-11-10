package com.kitsugi.animelist.core.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleUrlPolicyTest {
    @Test fun remoteYifyAddonCannotAccessStremioLocalProxy() {
        val provider = "https://2ecbbd610840-yifysubtitles.baby-beamup.club/turkish/manifest.json"
        assertFalse(SubtitleUrlPolicy.isReachableForProvider(provider, "http://127.0.0.1:11470/subtitles.vtt?from=foo"))
        assertFalse(SubtitleUrlPolicy.isReachableForProvider(provider, "http://[::1]/subtitle.srt"))
        assertFalse(SubtitleUrlPolicy.isReachableForProvider(provider, "file:///data/local/private"))
        assertTrue(SubtitleUrlPolicy.isReachableForProvider(provider, "https://example.test/subtitle.srt"))
        assertTrue(SubtitleUrlPolicy.isReachableForProvider("http://127.0.0.1:7000/manifest.json", "http://127.0.0.1:7000/subtitle.srt"))
    }
}
