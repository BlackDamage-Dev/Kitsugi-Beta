package com.kitsugi.animelist.core.player

import androidx.media3.common.MimeTypes
import com.kitsugi.animelist.core.player.dvmkv.DolbyVisionCompatibility
import org.junit.Assert.*
import org.junit.Test

class DolbyVisionProfileSafetyTest {
    @Test fun `base layer mapping requires known profile 7`() {
        DolbyVisionCompatibility.setMapDv7ToHevcEnabled(true)
        try {
            assertTrue(DolbyVisionCompatibility.shouldMapDolbyVisionProfile7(MimeTypes.VIDEO_DOLBY_VISION, "dvhe.07.06"))
            assertFalse(DolbyVisionCompatibility.shouldMapDolbyVisionProfile7(MimeTypes.VIDEO_DOLBY_VISION, "dvhe.05.06"))
            assertFalse(DolbyVisionCompatibility.shouldMapDolbyVisionProfile7(MimeTypes.VIDEO_DOLBY_VISION, "dvhe.08.06"))
            assertFalse(DolbyVisionCompatibility.shouldMapDolbyVisionProfile7(MimeTypes.VIDEO_DOLBY_VISION, null))
            assertFalse(DolbyVisionCompatibility.shouldMapDolbyVisionProfile7(MimeTypes.VIDEO_H265, "dvhe.07.06"))
        } finally {
            DolbyVisionCompatibility.setMapDv7ToHevcEnabled(false)
        }
    }

    @Test fun `matroska stripping is per instance and limited to profile 7`() {
        val strip = DolbyVisionMatroskaTransformer(DolbyVisionConversionConfig(active = false), stripRpuOnly = true)
        val passthrough = DolbyVisionMatroskaTransformer(DolbyVisionConversionConfig(active = false))
        assertTrue(strip.useHevcBaseLayer("dvhe.07.06"))
        assertTrue(strip.shouldTransform("dvhe.07.06", null))
        for (codec in listOf("dvhe.05.06", "dvhe.08.06", "dvhe.unknown", "hvc1.2.4")) {
            assertFalse(strip.useHevcBaseLayer(codec))
            assertFalse(strip.shouldTransform(codec, null))
        }
        assertFalse(strip.useHevcBaseLayer(null))
        assertFalse(passthrough.useHevcBaseLayer("dvhe.07.06"))
        assertNull(strip.onDolbyVisionBlockAdditionalData(byteArrayOf(1), 0, null))
    }
}
