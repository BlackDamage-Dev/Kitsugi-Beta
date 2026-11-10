package com.kitsugi.animelist.ui.screens.fullscreen

import com.kitsugi.animelist.data.repository.StreamSource
import com.kitsugi.animelist.ui.screens.fullscreen.controls.components.sheets.buildQualityOptions
import com.kitsugi.animelist.ui.screens.search.components.safeYear
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QualitySheetOptionsTest {

    private fun testSource(addonName: String, quality: String? = null, qualityValue: Int? = null) = StreamSource(
        addonName = addonName,
        name = "Stream",
        title = "Title",
        url = "https://example.com/video.mp4",
        infoHash = null,
        fileIndex = null,
        quality = quality,
        qualityValue = qualityValue,
    )

    private fun createSearchResponse(
        name: String,
        url: String = "https://example.com/anime/1",
        year: Int? = null,
        type: TvType = TvType.Anime
    ): SearchResponse {
        val cls = Class.forName("com.lagradost.cloudstream3.AnimeSearchResponse")
        val ctor = cls.constructors
            .filter { !it.isSynthetic && it.parameterTypes.none { p -> p.name.contains("DefaultConstructorMarker") } }
            .firstOrNull { it.parameterCount >= 3 }
            ?: cls.constructors.firstOrNull { it.parameterCount >= 3 }
            ?: throw IllegalStateException("Could not find AnimeSearchResponse constructor")
        val args = Array<Any?>(ctor.parameterCount) { null }
        val paramTypes = ctor.parameterTypes
        for (i in 0 until ctor.parameterCount) {
            val pType = paramTypes[i]
            when {
                i == 0 -> args[i] = name
                i == 1 -> args[i] = url
                i == 2 -> args[i] = "DummyProvider"
                i == 3 && pType.isAssignableFrom(type.javaClass) -> args[i] = type
                pType == java.lang.Integer.TYPE -> args[i] = 0
                pType == java.lang.Boolean.TYPE -> args[i] = false
                pType == java.lang.Long.TYPE -> args[i] = 0L
                pType == java.lang.Float.TYPE -> args[i] = 0f
                pType == java.lang.Double.TYPE -> args[i] = 0.0
                pType == java.util.Set::class.java -> args[i] = emptySet<Any>()
                pType == java.util.Map::class.java -> args[i] = emptyMap<Any, Any>()
                else -> args[i] = null
            }
        }

        val instance = ctor.newInstance(*args) as SearchResponse

        if (year != null) {
            try {
                val field = cls.getDeclaredField("year")
                field.isAccessible = true
                field.set(instance, year)
            } catch (_: Exception) {}
        }

        return instance
    }

    @Test
    fun `buildQualityOptions returns empty list when streamSources is empty`() {
        val options = buildQualityOptions(emptyList())
        assertTrue(options.isEmpty())
    }

    @Test
    fun `buildQualityOptions returns only auto when no sources declare resolution`() {
        val sources = listOf(
            testSource(addonName = "PluginA", quality = null, qualityValue = null),
            testSource(addonName = "PluginB", quality = "unknown", qualityValue = null),
        )
        val options = buildQualityOptions(sources)
        assertEquals(1, options.size)
        assertTrue(options[0].isAuto)
        assertEquals(-1, options[0].qualityValue)
    }

    @Test
    fun `buildQualityOptions groups sources by resolution in descending order`() {
        val sources = listOf(
            testSource(addonName = "PluginA", quality = "720p", qualityValue = 720),
            testSource(addonName = "PluginB", quality = "1080p", qualityValue = 1080),
            testSource(addonName = "PluginC", quality = "1080p", qualityValue = 1080),
            testSource(addonName = "PluginD", quality = "480p", qualityValue = 480),
        )
        val options = buildQualityOptions(sources)
        // 1 Auto + 3 distinct qualities (1080p, 720p, 480p)
        assertEquals(4, options.size)

        // First is Auto, pointing to index 1 (PluginB with 1080p)
        assertTrue(options[0].isAuto)
        assertEquals(1, options[0].targetSourceIndex)

        // Then 1080p
        assertEquals(1080, options[1].qualityValue)
        assertEquals(2, options[1].sourceCount)
        assertEquals(1, options[1].targetSourceIndex)

        // Then 720p
        assertEquals(720, options[2].qualityValue)
        assertEquals(1, options[2].sourceCount)
        assertEquals(0, options[2].targetSourceIndex)

        // Then 480p
        assertEquals(480, options[3].qualityValue)
        assertEquals(1, options[3].sourceCount)
        assertEquals(3, options[3].targetSourceIndex)
    }

    @Test
    fun `safeYear reflection extracts year correctly`() {
        val response = createSearchResponse("Test Anime", year = 2026)
        assertEquals(2026, response.safeYear())
    }
}
