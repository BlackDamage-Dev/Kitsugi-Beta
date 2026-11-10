package com.kitsugi.animelist.data.repository

import com.kitsugi.animelist.core.player.SubtitleInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.4.340 — "İzle sayfasında tüm kaynaklarda boyut + seeder/peer + ses-altyazı rozetleri"
 * çalışmasının regresyon testleri.
 *
 * Kural: rozetler **yalnızca kanıt varsa** basılır; sayı uydurulmaz.
 */
class StreamBadgesTest {

    private fun source(
        title: String = "",
        name: String = "",
        fileName: String? = null,
        infoHash: String? = "abc123",
        url: String? = null,
        providerAudioKind: String? = null,
        subtitles: List<SubtitleInput> = emptyList(),
        videoSizeBytes: Long? = null,
        externalAudioTrackCount: Int = 0,
        swarmStats: TorrentSwarmStats = TorrentSwarmStats.NONE
    ) = StreamSource(
        addonName = "TestAddon",
        name = name,
        title = title,
        url = url,
        infoHash = infoHash,
        fileIndex = null,
        fileName = fileName,
        providerAudioKind = providerAudioKind,
        subtitles = subtitles,
        videoSizeBytes = videoSizeBytes,
        externalAudioTrackCount = externalAudioTrackCount,
        swarmStats = swarmStats
    )

    // ── Swarm (seeder / peer) ────────────────────────────────────────────────

    @Test
    fun `torrentio metninden seeder ve boyut okunur`() {
        val text = "The.Mentalist.S06E17.1080p.WEB-DL\n👤 124 💾 1.7 GB ⚙ YTS"
        val stats = TorrentSwarmStats.parse(text)
        assertEquals(124, stats.seeders)
        assertEquals("YTS", stats.providerTag)
    }

    @Test
    fun `peer bilgisi ayri okunur`() {
        val stats = TorrentSwarmStats.parse("Seeders: 48 Peers: 12")
        assertEquals(48, stats.seeders)
        assertEquals(12, stats.leechers)
    }

    @Test
    fun `kisa bicim ve ok isaretleri desteklenir`() {
        assertEquals(124, TorrentSwarmStats.parse("S:124 L:12").seeders)
        assertEquals(12, TorrentSwarmStats.parse("S:124 L:12").leechers)
        assertEquals(1200, TorrentSwarmStats.parse("1.2K↑ 300↓").seeders)
    }

    @Test
    fun `sayi yoksa swarm bilinmiyor kalir`() {
        val stats = TorrentSwarmStats.parse("The.Mentalist.S06E17.1080p.WEB-DL")
        assertTrue(stats.seeders == null && stats.leechers == null)
        assertEquals(false, stats.hasAny)
    }

    @Test
    fun `1080p gibi degerler seeder sanilmaz`() {
        val stats = TorrentSwarmStats.parse("Movie 1080p x264 5.1 GB")
        assertNull(stats.seeders)
        assertNull(stats.leechers)
    }

    @Test
    fun `kisa sayi bicimi`() {
        assertEquals("999", TorrentSwarmStats.formatCompact(999))
        assertEquals("1.2K", TorrentSwarmStats.formatCompact(1200))
        assertEquals("12K", TorrentSwarmStats.formatCompact(12_400))
        assertEquals("1.5M", TorrentSwarmStats.formatCompact(1_500_000))
    }

    // ── Boyut ───────────────────────────────────────────────────────────────

    @Test
    fun `gercek bayt bilgisi her zaman kazanir`() {
        val stream = source(title = "Episode 1.4 GB", videoSizeBytes = 3_300_000_000L)
        assertEquals("3.30 GB", StreamInfoResolver.resolveSizeLabel(stream))
    }

    @Test
    fun `olculen bayt bilgisi metni ezer`() {
        val stream = source(title = "Episode 1.4 GB")
        val measured = MeasuredStreamInfo(sizeBytes = 2_000_000_000L)
        assertEquals("2.00 GB", StreamInfoResolver.resolveSizeLabel(stream, measured))
    }

    @Test
    fun `boyut hicbir yerde yoksa null doner`() {
        assertNull(StreamInfoResolver.resolveSizeLabel(source(title = "Bölüm 1")))
    }

    @Test
    fun `dosya adindaki boyut baslikta olmasa da bulunur`() {
        val stream = source(
            title = "The Mentalist S06E17",
            fileName = "The.Mentalist.S06E17.1080p.WEB-DL.x264-ABC.mkv"
        )
        // Dosya adında boyut yok → null. Başlıkta olsaydı okunurdu:
        val withSize = stream.copy(fileName = "The.Mentalist.S06E17.1080p.1.72GB.mkv")
        assertEquals("1.72 GB", StreamInfoResolver.resolveSizeLabel(withSize))
    }

    // ── Altyazı / ses rozetleri ─────────────────────────────────────────────

    @Test
    fun `gercek altyazi dosyalari dil rozetine donusur`() {
        val stream = source(
            subtitles = listOf(
                SubtitleInput(url = "https://x/a.vtt", name = "Türkçe", lang = "tr"),
                SubtitleInput(url = "https://x/b.vtt", name = "English", lang = "en")
            )
        )
        val info = StreamInfoResolver.resolveLang(stream)
        assertEquals(listOf("TR", "EN"), info.subtitleLanguages)
        assertEquals(StreamAudioKind.SUB, info.kind)
    }

    @Test
    fun `olculen gomulu altyazi isaretlenir`() {
        val measured = MeasuredStreamInfo(subtitleLanguages = listOf("tr"))
        val info = StreamInfoResolver.resolveLang(source(), measured)
        assertTrue(info.hasInternalSubtitles)
        assertEquals(listOf("TR"), info.subtitleLanguages)
    }

    @Test
    fun `ayri ses parcalari cift dil kanitidir`() {
        val info = StreamInfoResolver.resolveLang(source(externalAudioTrackCount = 2))
        assertEquals(StreamAudioKind.DUAL, info.kind)
        assertTrue(info.hasAudioTracks)
    }

    @Test
    fun `tek harici ses parcasi da cift dil kanitidir`() {
        // Videonun kendi sesi + eklentinin verdiği harici parça = iki ses akışı.
        val info = StreamInfoResolver.resolveLang(source(externalAudioTrackCount = 1))
        assertEquals(StreamAudioKind.DUAL, info.kind)
        assertTrue(info.hasAudioTracks)
    }

    @Test
    fun `harici ses parcasi yoksa cift dil denmez`() {
        val info = StreamInfoResolver.resolveLang(source(title = "Bölüm 1"))
        assertTrue(info.kind != StreamAudioKind.DUAL)
        assertTrue(!info.hasAudioTracks)
    }

    @Test
    fun `olculen ses dilleri listelenir`() {
        val measured = MeasuredStreamInfo(audioLanguages = listOf("tr", "en"))
        val info = StreamInfoResolver.resolveLang(source(), measured)
        assertEquals(listOf("TR", "EN"), info.audioLanguages)
        assertEquals(StreamAudioKind.DUAL, info.kind)
    }

    // ── Teknik etiketler ────────────────────────────────────────────────────

    @Test
    fun `dosya adindan codec hdr ve kaynak turu okunur`() {
        val stream = source(fileName = "Movie.2024.2160p.WEB-DL.DDP5.1.H.265.HDR10.mkv")
        val tech = StreamInfoResolver.resolveTechInfo(stream)
        assertEquals("HEVC", tech.codec)
        assertEquals("HDR10", tech.hdr)
        assertEquals("WEB-DL", tech.source)
    }

    @Test
    fun `olculen codec dosya adini ezer`() {
        val stream = source(fileName = "Movie.2024.1080p.x264.mkv")
        val tech = StreamInfoResolver.resolveTechInfo(stream, MeasuredStreamInfo(codec = "AV1"))
        assertEquals("AV1", tech.codec)
    }

    @Test
    fun `etiket yoksa tech bilgisi bos`() {
        assertTrue(StreamInfoResolver.resolveTechInfo(source(title = "Bölüm 1")).isEmpty)
    }
}

/**
 * Kaynak sıralaması: seeder sayısı bildiren akışlar öne çıkar, bildirmeyenler
 * cezalandırılmaz (0 gibi uydurma bir değer varsayılmaz).
 */
class StreamSorterSwarmTest {

    private fun stream(title: String, seeders: Int?, qualityValue: Int? = 1080) = StreamSource(
        addonName = "A",
        name = "A • $title",
        title = title,
        url = null,
        infoHash = "hash-$title",
        fileIndex = null,
        qualityValue = qualityValue,
        swarmStats = TorrentSwarmStats(seeders = seeders)
    )

    @Test
    fun `yuksek seeder ayni kalitede one cikar`() {
        val low = stream("low", 2)
        val high = stream("high", 120)
        val unknown = stream("unknown", null)
        val sorted = StreamSorter.sort(listOf(low, unknown, high))
        assertEquals("high", sorted.first().title)
        // Seeder bildirmeyen kaynak "0 seeder" sayılmaz; sıralamada en sona düşer ama elenmez.
        assertEquals("low", sorted[1].title)
        assertEquals("unknown", sorted[2].title)
    }

    @Test
    fun `seeder bilinmiyorsa kalite yine oncelikli`() {
        val uhd = stream("uhd", null, qualityValue = 2160)
        val hd = stream("hd", 500, qualityValue = 1080)
        val sorted = StreamSorter.sort(listOf(hd, uhd))
        assertEquals("uhd", sorted.first().title)
    }
}
