package com.kitsugi.animelist.core.player

import org.junit.Assert.*
import org.junit.Test

class HdrSampleSafetyTest {
    @Test fun `HEVC rewriting cannot touch AVC or AV1 streams`() {
        assertTrue(HdrSampleSafety.isHevcTrack("video/hevc", null))
        assertTrue(HdrSampleSafety.isHevcTrack("video/dolby-vision", "DVHE.07.06"))
        assertTrue(HdrSampleSafety.isHevcTrack("video/dolby-vision", "dvh1.05.06"))
        assertFalse(HdrSampleSafety.isHevcTrack("video/dolby-vision", "dvav.09.01"))
        assertFalse(HdrSampleSafety.isHevcTrack("video/dolby-vision", "dav1.10.06"))
        assertFalse(HdrSampleSafety.isHevcTrack("video/dolby-vision", null))
        assertFalse(HdrSampleSafety.isHevcTrack("video/avc", "avc1.640028"))
    }
}
