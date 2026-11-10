package com.kitsugi.animelist.core.download

import com.kitsugi.animelist.data.model.AnimeDownload
import com.kitsugi.animelist.data.model.rehydrated
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Küçük video (klip) indirmelerinin **saf** (Android'e bağlı olmayan) kurallarını doğrular:
 * tekilleştirme anahtarı, dosya adı üretimi ve eski kayıtların şema uyumluluğu.
 */
class ClipDownloadTest {

    // ── Tekilleştirme anahtarı ────────────────────────────────────────────────

    @Test
    fun `sorgu parametreleri tekilleştirme anahtarini degistirmez`() {
        val plain = ClipDownloadSource.identityKey("https://cdn.example.com/clip.mp4")
        val withToken = ClipDownloadSource.identityKey("https://cdn.example.com/clip.mp4?token=abc&t=12")
        assertEquals(plain, withToken)
    }

    @Test
    fun `farkli yollar farkli anahtar uretir`() {
        assertNotEquals(
            ClipDownloadSource.identityKey("https://cdn.example.com/a.mp4"),
            ClipDownloadSource.identityKey("https://cdn.example.com/b.mp4")
        )
    }

    // ── Dosya adı üretimi ─────────────────────────────────────────────────────

    @Test
    fun `dosya adinda yasak karakterler temizlenir`() {
        val base = DownloadExportNaming.clipBaseName(
            mediaTitle = "The Mentalist: Sezon 1/2",
            clipTitle = "Kamera Arkası \"Behind\"?",
            index = 1
        )
        assertFalse(base.contains(":"))
        assertFalse(base.contains("/"))
        assertFalse(base.contains("\""))
        assertFalse(base.contains("?"))
        assertTrue(base.endsWith("_1"))
    }

    @Test
    fun `ayni adli videolar sira ekiyle ayrilir`() {
        val first = DownloadExportNaming.clipBaseName("Dizi", "Fragman", 1)
        val second = DownloadExportNaming.clipBaseName("Dizi", "Fragman", 2)
        assertNotEquals(first, second)
        assertTrue(first.endsWith("_1"))
        assertTrue(second.endsWith("_2"))
    }

    @Test
    fun `bos basliklar guvenli varsayilan alir`() {
        val base = DownloadExportNaming.clipBaseName("", "", 1)
        assertTrue(base.isNotBlank())
        assertFalse(base.startsWith("_"))
    }

    @Test
    fun `uzanti guvenli hale getirilir`() {
        assertEquals("klip.mp4", DownloadExportNaming.clipFileName("klip", "mp4"))
        assertEquals("klip.ts", DownloadExportNaming.clipFileName("klip", "TS"))
        assertEquals("klip.mp4", DownloadExportNaming.clipFileName("klip", ""))
    }

    // ── Şema uyumluluğu ───────────────────────────────────────────────────────

    @Test
    fun `eski kayitlar bolum indirmesi olarak kalir`() {
        val legacy = AnimeDownload(
            animeId = "123",
            animeTitle = "Naruto",
            posterUrl = null,
            episode = 3,
            season = 1,
            url = "https://example.com/ep3.m3u8",
            quality = "1080p"
        )
        val restored = legacy.rehydrated()
        assertEquals(AnimeDownload.Kind.EPISODE, restored.kind)
        assertFalse(restored.isClip)
        assertEquals(3, restored.episode)
        // copy() eski kayıtlarda çökmemeli (non-null alanlar dolduruldu).
        assertEquals(AnimeDownload.Status.COMPLETED, restored.copy(status = AnimeDownload.Status.COMPLETED).status)
    }

    @Test
    fun `klip kaydi isClip doner`() {
        val clip = AnimeDownload(
            animeId = "clip_1",
            animeTitle = "The Mentalist",
            posterUrl = null,
            episode = 0,
            season = 0,
            url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            quality = "Klipler",
            kind = AnimeDownload.Kind.CLIP,
            clipKey = "clip|yt:dQw4w9WgXcQ",
            clipTitle = "Kamera Arkası"
        )
        assertTrue(clip.isClip)
        assertTrue(clip.rehydrated().isClip)
        assertEquals("Kamera Arkası", clip.rehydrated().clipTitle)
    }
}
