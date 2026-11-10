package com.kitsugi.animelist.data.cloudstream.extractors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * v2.4.304 — KitsugiCdnJwExtractor (Close/Rapid CDN ailesi) saf mantık testleri.
 * Android bağımlılığı yok: deşifre, varyant seçimi ve altyazı ayrıştırma JVM'de doğrulanır.
 */
class KitsugiCdnJwExtractorTest {

    @Test
    fun parseVariants_keepsAllQualitiesAndExtensionlessUris() {
        val variants = KitsugiCdnJwExtractor.parseVariants(
            "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000000,RESOLUTION=1280x720\nlow.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1920x1080\n/high?token=abc",
            "https://cdn.example/video/master.txt"
        )
        assertEquals(listOf(720, 1080), variants.map { it.height })
        assertEquals("https://cdn.example/video/low.m3u8", variants[0].url)
        assertEquals("https://cdn.example/high?token=abc", variants[1].url)
    }

    @Test
    fun parseVariants_unknownResolutionStaysUnknownAndDuplicatesRemoved() {
        val variants = KitsugiCdnJwExtractor.parseVariants(
            "https://cdn.example/720p.m3u8\nhttps://cdn.example/720p.m3u8\nhttps://cdn.example/other.m3u8",
            "https://cdn.example/master.txt"
        )
        assertEquals(2, variants.size)
        assertEquals(720, variants[0].height)
        assertNull(variants[1].height)
    }

    @Test
    fun parseVariants_mediaSegmentsAndErrorPagesAreNotQualities() {
        assertTrue(KitsugiCdnJwExtractor.parseVariants("#EXTM3U\n#EXTINF:6,\nsegment.ts", "https://cdn.example/hls/master.txt").isEmpty())
        assertTrue(KitsugiCdnJwExtractor.parseVariants("<html>blocked</html>", "https://cdn.example/master.txt").isEmpty())
    }

    // ── origin / header ────────────────────────────────────────────────────────

    @Test
    fun originOf_subdomainAndPath() {
        assertEquals(
            "https://closeload.filmmakinesi.to",
            KitsugiCdnJwExtractor.originOf("https://closeload.filmmakinesi.to/embed/abc?x=1")
        )
        assertEquals("https://rapidrame.com", KitsugiCdnJwExtractor.originOf("https://rapidrame.com/e/1"))
        assertNull(KitsugiCdnJwExtractor.originOf("bozuk url"))
    }

    @Test
    fun headersFor_pinsOriginAndReferer() {
        val h = KitsugiCdnJwExtractor.headersFor("https://n1.rapidrame.com")
        assertEquals("https://n1.rapidrame.com", h["Origin"])
        assertEquals("https://n1.rapidrame.com/", h["Referer"])
        assertTrue(h["User-Agent"]!!.isNotBlank())
    }

    // ── master playlist varyant seçimi ─────────────────────────────────────────

    @Test
    fun pickVariant_prefersHighestStreamInfResolution() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:RESOLUTION=1280x720,BANDWIDTH=2500000
            720/index.m3u8
            #EXT-X-STREAM-INF:RESOLUTION=1920x1080,BANDWIDTH=5000000
            1080/index.m3u8
            bare/480p.m3u8
        """.trimIndent()
        assertEquals(
            "https://cdn.x/hls/a/1080/index.m3u8",
            KitsugiCdnJwExtractor.pickVariant(master, "https://cdn.x/hls/a/master.txt")
        )
    }

    @Test
    fun pickVariant_bareLinesUseNameScore() {
        val master = "https://cdn.x/v/480p.m3u8\nhttps://cdn.x/v/1080p.m3u8\nhttps://cdn.x/v/720p.m3u8"
        assertEquals(
            "https://cdn.x/v/1080p.m3u8",
            KitsugiCdnJwExtractor.pickVariant(master, "https://cdn.x/v/master.txt")
        )
    }

    @Test
    fun pickVariant_relativeLinesAreAbsolutized() {
        val master = "#EXTM3U\n../b/index.m3u8"
        assertEquals(
            "https://cdn.x/hls/b/index.m3u8",
            KitsugiCdnJwExtractor.pickVariant(master, "https://cdn.x/hls/a/master.txt")
        )
    }

    @Test
    fun pickVariant_garbageReturnsNull() {
        assertNull(KitsugiCdnJwExtractor.pickVariant("<html>just a moment</html>", "https://cdn.x/master.txt"))
    }

    // ── altyazı ayrıştırma ─────────────────────────────────────────────────────

    @Test
    fun parseSubtitleTracks_filtersNonCaptionKinds() {
        val html = """
            <script>
            jwplayer("player").setup({ tracks: [
              { "file": "https:\/\/cdn.x\/sub\/tr.vtt", "label": "Türkçe", "kind": "captions" },
              { "file": "https:\/\/cdn.x\/thumb\/vtt.vtt", "label": "thumbs", "kind": "thumbnails" },
              { "file": "relative.vtt", "label": "Yerel", "kind": "captions" }
            ] });
            </script>
        """.trimIndent()
        val subs = KitsugiCdnJwExtractor.parseSubtitleTracks(html)
        assertEquals(1, subs.size)
        assertEquals("Türkçe", subs[0].first)
        assertEquals("https://cdn.x/sub/tr.vtt", subs[0].second)
    }

    // ── dc_ native deşifre (sentetik tur) ──────────────────────────────────────

    /**
     * Sitenin şifrelemesini TERSTEN çalıştırarak sentetik bir embed sayfası üretir ve
     * [KitsugiCdnJwExtractor.decryptNative]'nin aynı adresi geri kazandığını doğrular.
     */
    @Test
    fun decryptNative_roundTripsSyntheticPayload() {
        val plain = "https://cdn.playmix.uno/hls/04c0b9c2/master.txt"
        val magicNum = 399756995L
        val magicOffset = 5
        val rotShift = 13

        // 1) modulo mix (deşifrenin son adımının tersi)
        val mixed = StringBuilder()
        for (i in plain.indices) {
            mixed.append(((plain[i].code + (magicNum % (i + magicOffset))) % 256).toInt().toChar())
        }
        // 2) rot tersi (harfleri -13)
        val unrot = StringBuilder()
        for (c in mixed) {
            when (c) {
                in 'a'..'z' -> {
                    var ch = c.code - rotShift
                    if (ch < 'a'.code) ch += 26
                    unrot.append(ch.toChar())
                }
                in 'A'..'Z' -> {
                    var ch = c.code - rotShift
                    if (ch < 'A'.code) ch += 26
                    unrot.append(ch.toChar())
                }
                else -> unrot.append(c)
            }
        }
        // 3) reverse tersi = reverse
        val reversed = unrot.toString().reversed()
        // 4) atob tersi = base64 encode (ISO-8859-1 baytları)
        val payload = Base64.getEncoder()
            .encodeToString(reversed.toByteArray(Charsets.ISO_8859_1))
            .trimEnd('=')

        val html = """
            <html><head><script type="text/javascript">
            function dc_a1b2(_0x1) {
                var r = _0x1.join("");
                r = atob(r);
                r = r.split("").reverse().join("");
                r = r.replace(/./g, function (o) { return String.fromCharCode(o.charCodeAt(0) + 13); });
                return r;
            }
            function d1x() { var out = ""; for (var i = 0; i < 64; i++) { out += String.fromCharCode((i + 399756995 % (i + 5)) % 256); } return out; }
            var streamUrl = dc_a1b2(["$payload"]);
            </script></head><body></body></html>
        """.trimIndent()

        assertEquals(plain, KitsugiCdnJwExtractor.decryptNative(html))
    }

    @Test
    fun decryptNative_noScriptReturnsNull() {
        assertNull(KitsugiCdnJwExtractor.decryptNative("<html>just a moment</html>"))
    }
}
