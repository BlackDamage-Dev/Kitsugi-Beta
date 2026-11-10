package com.kitsugi.animelist.core.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "İndirilen dosya cihazda oynamıyorsa bir anlamı yok" kuralının testleri.
 *
 * Varyant seçimi cihazın decoder envanterine göre yapılmalı: kaynak AV1/HEVC +
 * AC-3 sunsa bile, cihaz destekliyorsa yüksek kalite, desteklemiyorsa **oynatılabilir**
 * olan varyant seçilmeli.
 */
class DeviceCompatibilitySelectionTest {

    private fun master(vararg variants: String): HlsMasterPlaylist {
        val lines = StringBuilder("#EXTM3U\n")
        variants.forEach { lines.append(it).append("\n") }
        return HlsPlaylistParser.parseMaster(lines.toString(), "https://cdn.example.com/master.m3u8")!!
    }

    /** Yalnızca H.264 + AAC destekleyen tipik bir telefon. */
    private val h264Only: (String?) -> Boolean = { codecs ->
        val c = codecs?.lowercase().orEmpty()
        !c.contains("av01") && !c.contains("hvc") && !c.contains("hev") && !c.contains("ec-3") && !c.contains("ac-3")
    }

    /** AV1 desteklemeyen ama HEVC destekleyen cihaz. */
    private val hevcOnly: (String?) -> Boolean = { codecs ->
        val c = codecs?.lowercase().orEmpty()
        !c.contains("av01")
    }

    @Test
    fun `cihaz AV1 desteklemiyorsa H264 varyanti secilir`() {
        val m = master(
            "#EXT-X-STREAM-INF:BANDWIDTH=9000000,RESOLUTION=1920x1080,CODECS=\"av01.0.05M.08,mp4a.40.2\"",
            "av1/index.m3u8",
            "#EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720,CODECS=\"avc1.4d401f,mp4a.40.2\"",
            "h264/index.m3u8"
        )
        val chosen = HlsPlaylistParser.selectVariant(m) { codecs -> h264Only(codecs) }
        assertNotNull(chosen)
        assertEquals("https://cdn.example.com/h264/index.m3u8", chosen!!.url)
        // AV1 varyantı en yüksek bitrate'e sahip olsa bile seçilmemeli.
        assertTrue(chosen.bandwidth < 9_000_000L)
    }

    @Test
    fun `cihaz AV1 destekliyorsa en yuksek kalite AV1 secilir`() {
        val m = master(
            "#EXT-X-STREAM-INF:BANDWIDTH=9000000,RESOLUTION=1920x1080,CODECS=\"av01.0.05M.08,mp4a.40.2\"",
            "av1/index.m3u8",
            "#EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720,CODECS=\"avc1.4d401f,mp4a.40.2\"",
            "h264/index.m3u8"
        )
        val chosen = HlsPlaylistParser.selectVariant(m) { true }
        assertEquals("https://cdn.example.com/av1/index.m3u8", chosen?.url)
    }

    @Test
    fun `hevc destekleyen cihaz AV1 yerine HEVC alir`() {
        val m = master(
            "#EXT-X-STREAM-INF:BANDWIDTH=9000000,CODECS=\"av01.0.05M.08,mp4a.40.2\"",
            "av1.m3u8",
            "#EXT-X-STREAM-INF:BANDWIDTH=4000000,CODECS=\"hvc1.1.6.L93.B0,mp4a.40.2\"",
            "hevc.m3u8",
            "#EXT-X-STREAM-INF:BANDWIDTH=2000000,CODECS=\"avc1.4d401f,mp4a.40.2\"",
            "h264.m3u8"
        )
        val chosen = HlsPlaylistParser.selectVariant(m) { codecs -> hevcOnly(codecs) }
        assertEquals("https://cdn.example.com/hevc.m3u8", chosen?.url)
    }

    @Test
    fun `CODECS bilgisi yoksa engellemeyiz`() {
        val m = master(
            "#EXT-X-STREAM-INF:BANDWIDTH=3000000",
            "a.m3u8",
            "#EXT-X-STREAM-INF:BANDWIDTH=1000000",
            "b.m3u8"
        )
        val chosen = HlsPlaylistParser.selectVariant(m) { false }
        // CODECS yok → desteği bilmiyoruz, en yüksek kalite seçilir.
        assertEquals("https://cdn.example.com/a.m3u8", chosen?.url)
    }

    @Test
    fun `hicbir varyant desteklenmiyorsa allowUnsupported ile en iyisi doner`() {
        val m = master(
            "#EXT-X-STREAM-INF:BANDWIDTH=9000000,CODECS=\"av01.0.05M.08\"",
            "av1.m3u8"
        )
        val withFallback = HlsPlaylistParser.selectVariant(m, allowUnsupported = true) { false }
        assertEquals("https://cdn.example.com/av1.m3u8", withFallback?.url)

        val strict = HlsPlaylistParser.selectVariant(m, allowUnsupported = false) { false }
        assertEquals(null, strict)
    }

    @Test
    fun `ses only varyantlar yine atlanir`() {
        val m = master(
            "#EXT-X-STREAM-INF:BANDWIDTH=3000000,CODECS=\"mp4a.40.2\"",
            "audio.m3u8",
            "#EXT-X-STREAM-INF:BANDWIDTH=1000000,RESOLUTION=640x360,CODECS=\"avc1.42e01e,mp4a.40.2\"",
            "video.m3u8"
        )
        val chosen = HlsPlaylistParser.selectVariant(m) { true }
        assertEquals("https://cdn.example.com/video.m3u8", chosen?.url)
    }

    // ── Codec kimlik çözümleme ───────────────────────────────────────────

    @Test
    fun `codec listesi cozme dogru calisir`() {
        assertFalse(HlsPlaylistParser.splitCodecs("avc1.4d401f,mp4a.40.2").isEmpty())
        assertEquals(2, HlsPlaylistParser.splitCodecs("avc1.4d401f, mp4a.40.2").size)
        assertEquals(2, HlsPlaylistParser.splitCodecs("avc1.4d401f,mp4a.40.2").size)
        assertTrue(HlsPlaylistParser.splitCodecs("").isEmpty())
        assertTrue(HlsPlaylistParser.splitCodecs(null).isEmpty())
    }

    @Test
    fun `varyantta video ve ses codec alanlari doluyor`() {
        val m = master(
            "#EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080,CODECS=\"hvc1.1.6.L93.B0,ec-3\"",
            "hevc.m3u8"
        )
        val v = m.variants.first()
        assertEquals("hvc1.1.6.l93.b0", v.videoCodec)
        assertEquals("ec-3", v.audioCodec)
    }

    // ── Kapsayıcı / uzantı ───────────────────────────────────────────────

    @Test
    fun `kapsayici uzantisi dogru turetilir`() {
        assertEquals("ts", DeviceCodecSupport.extensionFor("ts"))
        assertEquals("ts", DeviceCodecSupport.extensionFor("m2ts"))
        assertEquals("mp4", DeviceCodecSupport.extensionFor("m4s"))
        assertEquals("mp4", DeviceCodecSupport.extensionFor(null))
        assertEquals("mp4", DeviceCodecSupport.extensionFor(""))
    }

    @Test
    fun `bilinmeyen codec destegi engellemez`() {
        // null → karar veremiyoruz; false → kesin desteklenmiyor.
        assertEquals(null, DeviceCodecSupport.isCodecStringSupported("xyz9.0"))
        assertEquals(null, DeviceCodecSupport.isCodecStringSupported(null))
    }

    @Test
    fun `dosya adi uzantisi kapsayiciya uyar`() {
        val mp4 = DownloadExportNaming.exportFileName("Naruto", 12)
        val ts = DownloadExportNaming.exportFileName("Naruto", 12, "ts")
        assertEquals("Naruto_Bölüm_12.mp4", mp4)
        assertEquals("Naruto_Bölüm_12.ts", ts)
        assertEquals("Naruto_Bölüm_12", DownloadExportNaming.exportBaseName("Naruto", 12, "ts"))
    }

    @Test
    fun `mime adlari kullaniciya anlasilir cevrilir`() {
        assertEquals("H.264", DownloadVerifier.friendlyName("video/avc"))
        assertEquals("H.265/HEVC", DownloadVerifier.friendlyName("video/hevc"))
        assertEquals("AV1", DownloadVerifier.friendlyName("video/av01"))
        assertEquals("E-AC-3", DownloadVerifier.friendlyName("audio/eac3"))
        assertEquals("AC-3", DownloadVerifier.friendlyName("audio/ac3"))
    }
}
