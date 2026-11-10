package com.kitsugi.animelist.data.repository

import com.kitsugi.animelist.core.player.SubtitleInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [StreamInfoResolver] — "uydurma bilgi yok" garantisinin testleri.
 */
class StreamInfoResolverTest {

    private fun source(
        name: String = "",
        title: String = "",
        url: String? = "https://cdn.example.com/video/index.m3u8",
        addonName: String = "TestAddon",
        quality: String? = null,
        qualityValue: Int? = null,
        providerAudioKind: String? = null,
        subtitles: List<SubtitleInput> = emptyList()
    ) = StreamSource(
        addonName = addonName,
        name = name,
        title = title,
        url = url,
        infoHash = null,
        fileIndex = null,
        quality = quality,
        qualityValue = qualityValue,
        providerAudioKind = providerAudioKind,
        subtitles = subtitles
    )

    // ── Kalite ───────────────────────────────────────────────────────────────

    @Test
    fun `CloudStream Unknown degeri 400p olarak gosterilmez`() {
        // Qualities.Unknown.value == 400 — bu bir çözünürlük değildir.
        assertNull(StreamInfoResolver.csQualityHeightOrNull(400))
        val info = StreamInfoResolver.resolveQuality(source(qualityValue = 400, title = "Doraemon 001~400"))
        assertNull(info.label)
        assertFalse(info.isKnown)
        assertEquals(StreamInfoOrigin.NONE, info.origin)
    }

    @Test
    fun `kanit yoksa kalite bilinmiyor olarak kalir`() {
        val info = StreamInfoResolver.resolveQuality(source(title = "Doraemon Bölüm 1"))
        assertNull(info.label)
        assertEquals(StreamInfoOrigin.NONE, info.origin)
    }

    @Test
    fun `saglayicinin bildirdigi kalite kullanilir`() {
        val info = StreamInfoResolver.resolveQuality(source(qualityValue = 1080))
        assertEquals("1080p", info.label)
        assertEquals(StreamInfoOrigin.PROVIDER, info.origin)
    }

    @Test
    fun `dosya adindaki acik etiket okunur`() {
        assertEquals(1080, StreamInfoResolver.parseHeightFromText("Anime S01E01 1080p WEB-DL"))
        assertEquals(720, StreamInfoResolver.parseHeightFromText("[720p] Bokusatsu Tenshi"))
        assertEquals(2160, StreamInfoResolver.parseHeightFromText("Movie 4K HDR"))
        assertEquals(1080, StreamInfoResolver.parseHeightFromText("stream 1920x1080 h264"))
    }

    @Test
    fun `belirsiz tokenlar kalite uretmez`() {
        // "HD"/"SD" bir çözünürlük değildir; yıl/bölüm sayıları da öyle.
        assertNull(StreamInfoResolver.parseHeightFromText("Film HD sürüm"))
        assertNull(StreamInfoResolver.parseHeightFromText("Doraemon (2005) 001~400"))
        assertNull(StreamInfoResolver.parseHeightFromText("index-v1-a1.m3u8"))
    }

    @Test
    fun `olculen cozunurluk saglayici bilgisini ezer`() {
        val measured = MeasuredStreamInfo(variantHeights = listOf(360, 720, 1080), isAdaptive = true)
        val info = StreamInfoResolver.resolveQuality(source(qualityValue = 480), measured)
        assertEquals("1080p", info.label)
        assertEquals(StreamInfoOrigin.MEASURED, info.origin)
        assertTrue(info.isAdaptive)
    }

    // ── Dil ──────────────────────────────────────────────────────────────────

    @Test
    fun `kanit yoksa dil bilinmiyor`() {
        val info = StreamInfoResolver.resolveLang(source(name = "AnimeciX • Sibnet", addonName = "AnimeciX"))
        assertEquals(StreamAudioKind.UNKNOWN, info.kind)
        assertEquals(StreamInfoOrigin.NONE, info.origin)
        assertFalse(info.isKnown)
    }

    @Test
    fun `turkce eklenti olmasi altyazili anlamina gelmez`() {
        val info = StreamInfoResolver.resolveLang(source(addonName = "Dizilla", name = "Dizilla • VidMoly"))
        assertEquals(StreamAudioKind.UNKNOWN, info.kind)
    }

    @Test
    fun `sub alt dizesi yanlis eslesmez`() {
        // "Subaru" / "Submarine" içinde geçen 'sub' altyazı kanıtı değildir.
        assertNull(StreamInfoResolver.parseLangKindFromText("Re Zero Subaru Edition"))
        assertNull(StreamInfoResolver.parseLangKindFromText("Dubai Gezisi"))
    }

    @Test
    fun `acik etiketler dogru okunur`() {
        assertEquals(StreamAudioKind.SUB, StreamInfoResolver.parseLangKindFromText("Türkçe Altyazılı"))
        assertEquals(StreamAudioKind.DUB, StreamInfoResolver.parseLangKindFromText("TR Dublaj"))
        assertEquals(StreamAudioKind.DUAL, StreamInfoResolver.parseLangKindFromText("Dual Audio 1080p"))
        assertEquals(StreamAudioKind.SUB, StreamInfoResolver.parseLangKindFromText("[Eng Sub] Episode 1"))
    }

    @Test
    fun `saglayici DubStatus bilgisi oncelikli`() {
        val info = StreamInfoResolver.resolveLang(source(providerAudioKind = "dub", title = "Bölüm 1"))
        assertEquals(StreamAudioKind.DUB, info.kind)
        assertEquals(StreamInfoOrigin.PROVIDER, info.origin)
    }

    @Test
    fun `gercek altyazi dosyalari altyazi kaniti sayilir`() {
        val info = StreamInfoResolver.resolveLang(
            source(subtitles = listOf(SubtitleInput(url = "https://x/a.vtt", name = "Türkçe", lang = "tr")))
        )
        assertEquals(StreamAudioKind.SUB, info.kind)
        assertEquals(listOf("TR"), info.subtitleLanguages)
        assertEquals(StreamInfoOrigin.PROVIDER, info.origin)
    }

    @Test
    fun `olculen ses dilleri raporlanir`() {
        val measured = MeasuredStreamInfo(audioLanguages = listOf("tr", "ja"))
        val info = StreamInfoResolver.resolveLang(source(), measured)
        assertEquals(StreamInfoOrigin.MEASURED, info.origin)
        assertEquals(listOf("TR", "JA"), info.audioLanguages)
        assertEquals(StreamAudioKind.DUAL, info.kind)
    }

    // ── Boyut ────────────────────────────────────────────────────────────────

    @Test
    fun `kucuk dosya boyutu sifir megabayt olarak gosterilmez`() {
        assertEquals("350 KB", StreamInfoResolver.formatBytes(350_000L))
        assertEquals("999 B", StreamInfoResolver.formatBytes(999L))
        assertEquals("2 MB", StreamInfoResolver.formatBytes(2_000_000L))
    }

    @Test
    fun `boyut yalnizca kanit varsa gosterilir`() {
        assertNull(StreamInfoResolver.resolveSizeLabel(source(title = "Bölüm 1")))
        assertEquals("1.4 GB", StreamInfoResolver.resolveSizeLabel(source(title = "Episode 1.4 GB")))
        assertEquals(
            "2.00 GB",
            StreamInfoResolver.resolveSizeLabel(source(), MeasuredStreamInfo(sizeBytes = 2_000_000_000L))
        )
    }
}
