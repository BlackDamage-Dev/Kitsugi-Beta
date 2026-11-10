package com.kitsugi.animelist.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DASH `.mpd` → video kalite seçenekleri.
 *
 * DASH'te her kalite ayrı URL değildir; seçim motorun çözünürlük tavanıyla uygulanır,
 * bu yüzden [QualityOption.switchUrl] boş olmalıdır.
 */
class DashManifestTest {

    private val mpd = """
        <?xml version="1.0" encoding="utf-8"?>
        <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="PT1H32M">
          <Period>
            <AdaptationSet mimeType="video/mp4" segmentAlignment="true" startWithSAP="1">
              <Representation id="v0" bandwidth="800000" width="854" height="480" codecs="avc1.42c01e"/>
              <Representation id="v1" bandwidth="2500000" width="1280" height="720" codecs="avc1.4d401f"/>
              <Representation id="v2" bandwidth="5200000" width="1920" height="1080" codecs="avc1.640028"/>
            </AdaptationSet>
            <AdaptationSet mimeType="audio/mp4" lang="tr">
              <Representation id="a0" bandwidth="128000" codecs="mp4a.40.2"/>
            </AdaptationSet>
          </Period>
        </MPD>
    """.trimIndent()

    @Test
    fun `lists video representations from highest to lowest`() {
        val options = DashManifest.parse(mpd)

        assertEquals(3, options.size)
        assertEquals(listOf("1080p", "720p", "480p"), options.map { it.label })
        assertEquals(listOf(1080, 720, 480), options.map { it.height })
        assertEquals(5200000L, options.first().bandwidth)
        // DASH seçimi URL değiştirmez → motor tavanı kullanılır.
        assertNull(options.first().switchUrl)
    }

    @Test
    fun `audio adaptation set never appears as video quality`() {
        val options = DashManifest.parse(mpd)
        assertTrue(options.none { it.label == "128000" })
        assertTrue(options.none { it.height == null && it.bandwidth == 128000L })
    }

    @Test
    fun `audio codec representation inside video set is skipped`() {
        val manifest = """
            <MPD>
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <Representation id="v1" bandwidth="2500000" height="720" codecs="avc1.4d401f"/>
                  <Representation id="a1" bandwidth="128000" codecs="mp4a.40.2"/>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        val options = DashManifest.parse(manifest)

        assertEquals(1, options.size)
        assertEquals("720p", options.single().label)
    }

    @Test
    fun `same height keeps highest bandwidth`() {
        val manifest = """
            <MPD>
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <Representation id="a" bandwidth="1500000" height="720"/>
                  <Representation id="b" bandwidth="3100000" height="720"/>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        val options = DashManifest.parse(manifest)

        assertEquals(1, options.size)
        assertEquals(3100000L, options.single().bandwidth)
    }

    @Test
    fun `single line minified manifest is parsed too`() {
        val minified = """<MPD><Period><AdaptationSet mimeType="video/mp4"><Representation id="v1" bandwidth="2500000" width="1280" height="720" codecs="avc1.4d401f"/></AdaptationSet></Period></MPD>"""

        val options = DashManifest.parse(minified)

        assertEquals(1, options.size)
        assertEquals("720p", options.single().label)
        assertEquals(2500000L, options.single().bandwidth)
    }

    @Test
    fun `non dash body yields empty list`() {
        assertTrue(DashManifest.parse("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nx.m3u8").isEmpty())
        assertTrue(DashManifest.parse("").isEmpty())
    }

    @Test
    fun `quality option key is stable for the same quality`() {
        val a = QualityOption(label = "720p", height = 720, bandwidth = 2_500_000L)
        val b = QualityOption(label = "720p", height = 720, bandwidth = 2_500_000L)
        assertEquals(a.key, b.key)
        assertEquals(a, b)
    }
}
