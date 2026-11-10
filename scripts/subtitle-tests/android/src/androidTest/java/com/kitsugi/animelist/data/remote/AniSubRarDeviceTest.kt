package com.kitsugi.animelist.data.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kitsugi.animelist.core.player.AnisubMatching
import com.kitsugi.animelist.core.player.SubtitleEncoding
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AniSubRarDeviceTest {
    @Test fun extractsRealAniSubRarWithProductionAndroidCode() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bytes = instrumentation.context.assets.open("anisub-3607.rar").use { it.readBytes() }
        val dir = File(instrumentation.targetContext.cacheDir, "real-rar-test")
        dir.deleteRecursively()
        assertTrue(dir.mkdirs())
        try {
            val files = AniSubArchive.extractRar(bytes, dir)
            assertTrue("Real AniSub archive has no ASS file", files.any { it.extension.equals("ass", true) })
            val match = files.single { it.name == "FX 06.ass" }
            assertTrue(match.readText().contains("[Script Info]", ignoreCase = true))
            assertTrue(AnisubMatching.matchesFileEpisode(match.name, 6))
            assertFalse(AnisubMatching.matchesFileEpisode(match.name, 1))
        } finally {
            dir.deleteRecursively()
        }
    }
    @Test fun decodesRealTurkishOpenSubtitlesFileOnAndroid() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bytes = instrumentation.context.assets.open("opensubtitles-95005.srt").use { it.readBytes() }
        val decoded = SubtitleEncoding.toUtf8(bytes, language = "tur").toString(Charsets.UTF_8)
        assertTrue("Turkish characters were not decoded correctly", decoded.contains("açıklar mısınız"))
        assertFalse("Subtitle contains replacement characters", decoded.contains('\uFFFD'))
    }
}
