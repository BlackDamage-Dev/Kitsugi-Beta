package com.kitsugi.animelist.data.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Bölüm geçiş sırası testleri.
 *
 * Kullanıcı şikâyeti: okuyucudaki "sonraki bölüm" düğmesi, kaynak listeyi artan
 * sırada verdiğinde ters çalışıyordu (eski kod listeyi hep yeniden-eskiye kabul
 * ediyordu). Artık sıra numaraya göre kurulur.
 */
class MangaChapterSequenceTest {

    /** Kaynak listesi yeniden eskiye (yaygın durum). */
    private val descending = listOf("ch3" to 3f, "ch2" to 2f, "ch1" to 1f)

    /** Kaynak listesi eskiden yeniye (sorunlu durum). */
    private val ascending = listOf("ch1" to 1f, "ch2" to 2f, "ch3" to 3f)

    @Test
    fun `reading order is ascending regardless of source order`() {
        assertEquals(listOf("ch1", "ch2", "ch3"), MangaChapterSequence.readingOrder(descending))
        assertEquals(listOf("ch1", "ch2", "ch3"), MangaChapterSequence.readingOrder(ascending))
    }

    @Test
    fun `next chapter moves forward in both orderings`() {
        assertEquals("ch2", MangaChapterSequence.nextUrl(descending, "ch1"))
        assertEquals("ch2", MangaChapterSequence.nextUrl(ascending, "ch1"))
    }

    @Test
    fun `previous chapter moves backward in both orderings`() {
        assertEquals("ch1", MangaChapterSequence.previousUrl(descending, "ch2"))
        assertEquals("ch1", MangaChapterSequence.previousUrl(ascending, "ch2"))
    }

    @Test
    fun `no next chapter at the end`() {
        assertNull(MangaChapterSequence.nextUrl(descending, "ch3"))
        assertNull(MangaChapterSequence.nextUrl(ascending, "ch3"))
    }

    @Test
    fun `no previous chapter at the beginning`() {
        assertNull(MangaChapterSequence.previousUrl(descending, "ch1"))
        assertNull(MangaChapterSequence.previousUrl(ascending, "ch1"))
    }

    @Test
    fun `unknown current chapter returns null`() {
        assertNull(MangaChapterSequence.nextUrl(ascending, "yok"))
        assertNull(MangaChapterSequence.previousUrl(ascending, "yok"))
    }

    @Test
    fun `unnumbered chapters keep source order at the end`() {
        val chapters = listOf("duyuru" to -1f, "ch2" to 2f, "ch1" to 1f)
        val ordered = MangaChapterSequence.readingOrder(chapters)
        assertEquals(listOf("ch1", "ch2", "duyuru"), ordered)
    }

    @Test
    fun `falls back to reversed source order when nothing is numbered`() {
        val chapters = listOf("a" to -1f, "b" to -1f, "c" to -1f)
        // Numarasız listede kaynak sırası yeniden-eskiye kabul edilir.
        assertEquals(listOf("c", "b", "a"), MangaChapterSequence.readingOrder(chapters))
        assertEquals("b", MangaChapterSequence.nextUrl(chapters, "c"))
    }

    @Test
    fun `empty list is safe`() {
        assertEquals(emptyList<String>(), MangaChapterSequence.readingOrder(emptyList()))
        assertNull(MangaChapterSequence.nextUrl(emptyList(), "x"))
        assertNull(MangaChapterSequence.previousUrl(emptyList(), "x"))
    }

    @Test
    fun `decimal chapter numbers sort numerically`() {
        val chapters = listOf("ch10" to 10f, "ch9.5" to 9.5f, "ch9" to 9f)
        assertEquals(listOf("ch9", "ch9.5", "ch10"), MangaChapterSequence.readingOrder(chapters))
    }

    // ─── Resolver ile birlikte (uçtan uca) ────────────────────────────────────

    @Test
    fun `turkish chapter names resolve and sort`() {
        // Kaynakta numara yok; adlar "Bölüm N" biçiminde. Okuyucu bu adları
        // MangaChapterNumberResolver ile çözer, sonra sıraya dizer.
        val raw = listOf("bolum12" to "Bölüm 12", "bolum2" to "Bölüm 2", "bolum1" to "Bölüm 1")
        val withNumbers = raw.map { (url, name) ->
            url to MangaChapterNumberResolver.resolve(mangaTitle = "Test", chapterName = name, declaredNumber = -1f)
        }
        assertEquals(listOf("bolum1", "bolum2", "bolum12"), MangaChapterSequence.readingOrder(withNumbers))
        assertEquals("bolum2", MangaChapterSequence.nextUrl(withNumbers, "bolum1"))
    }

    @Test
    fun `english chapter names resolve and sort`() {
        val raw = listOf("c10" to "Chapter 10", "c9" to "Chapter 9", "c9_5" to "Bölüm 9.5")
        val withNumbers = raw.map { (url, name) ->
            url to MangaChapterNumberResolver.resolve(mangaTitle = "Test", chapterName = name, declaredNumber = -1f)
        }
        assertEquals(listOf("c9", "c9_5", "c10"), MangaChapterSequence.readingOrder(withNumbers))
    }
}
