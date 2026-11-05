package com.kitsugi.animelist.data.repository

import com.kitsugi.animelist.data.remote.StreamBehaviorHintsDto
import com.kitsugi.animelist.data.remote.StreamResponseItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stremio stream nesnesi → [StreamSource] dönüşümü.
 *
 * NEDEN ÖNEMLİ: Detay sayfası ile izleme ekranı farklı dönüşümler kullanıyordu; boyut,
 * dosya adı ve seeder bilgisi bir ekranda geliyor, diğerinde kayboluyordu. Artık TEK
 * dönüşüm noktası var ([toStreamSource]) ve bu testler onun sözleşmesini korur.
 */
class StremioStreamMappingTest {

    @Test
    fun `behaviorHints boyutu ve dosya adi tasinir`() {
        val item = StreamResponseItem(
            name = "Torrentio\n1080p",
            title = "The.Mentalist.S06E17.1080p.WEB-DL\n👤 124 💾 1.7 GB ⚙ YTS",
            url = null,
            infoHash = "ABCDEF0123456789",
            fileIdx = 3,
            sources = listOf("tracker:udp://tracker.example:1337/announce"),
            behaviorHints = StreamBehaviorHintsDto(
                filename = "The.Mentalist.S06E17.1080p.WEB-DL.mkv",
                videoSize = 1_700_000_000L
            )
        )

        val stream = item.toStreamSource(addonName = "Torrentio")

        assertEquals(1_700_000_000L, stream.videoSizeBytes)
        assertEquals("The.Mentalist.S06E17.1080p.WEB-DL.mkv", stream.fileName)
        assertEquals(3, stream.fileIndex)
        assertEquals(124, stream.swarmStats.seeders)
        assertEquals("YTS", stream.swarmStats.providerTag)
        assertEquals("1080p", stream.quality)
        assertTrue(stream.isTorrent)
    }

    @Test
    fun `json seeder alani metinden onceliklidir`() {
        val item = StreamResponseItem(
            name = "Addon",
            title = "👤 10 💾 900 MB",
            url = null,
            infoHash = "hash",
            sources = null,
            behaviorHints = null,
            seeders = 250,
            peers = 40
        )
        val stream = item.toStreamSource(addonName = "Addon")
        assertEquals(250, stream.swarmStats.seeders)
        assertEquals(40, stream.swarmStats.leechers)
    }

    @Test
    fun `bilgi yoksa hicbir deger uydurulmaz`() {
        val item = StreamResponseItem(
            name = null,
            title = null,
            url = "https://cdn.example.com/video/master.m3u8",
            infoHash = null,
            sources = null,
            behaviorHints = null
        )
        val stream = item.toStreamSource(addonName = "Addon")
        assertNull(stream.videoSizeBytes)
        assertNull(stream.swarmStats.seeders)
        assertNull(stream.swarmStats.leechers)
        assertEquals("Bilinmeyen Akış", stream.name)
        assertEquals("İsimsiz Dosya", stream.title)
    }

    @Test
    fun `metadata metni dosya adini one alir`() {
        val stream = StreamSource(
            addonName = "A",
            name = "A • 1080p",
            title = "Dosya",
            url = null,
            infoHash = "hash",
            fileIndex = null,
            fileName = "Movie.2024.2160p.4K.mkv"
        )
        assertTrue(stream.metadataText.startsWith("Movie.2024.2160p.4K.mkv"))
        assertEquals(2160, StreamInfoResolver.parseHeightFromText(stream.fileName))
    }
}
