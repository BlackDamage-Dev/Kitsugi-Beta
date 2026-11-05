package com.kitsugi.animelist.data.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bölüm sıralama/filtre motoru testleri.
 * Kullanıcı şikâyeti: "bölüm sayısı sıralaması düzeltilsin, sayfa başı 1'den başlasın".
 */
class MangaChapterOrderingTest {

    private data class Row(
        val name: String,
        val number: Float,
        val scanlator: String? = null,
        val completed: Boolean = false
    )

    private fun rows() = listOf(
        Row("Bölüm 137 - Chu Chu", 137f, "Garcia Fansub"),
        Row("Bölüm 134 - Sıradan Mutluluk", 134f, "Garcia Fansub"),
        Row("Bölüm 107 - Okula Saldırı", 107f, "Şımarık Fansub, Tempest Fansub"),
        Row("Bölüm 106 - Ateş", 106f, "Şımarık Fansub", completed = true),
        Row("Duyuru", -1f, null)
    )

    private val number: (Row) -> Float = { it.number }
    private val completed: (Row) -> Boolean = { it.completed }
    private val scanlator: (Row) -> String? = { it.scanlator }

    @Test
    fun newestFirstKeepsSourceOrder() {
        val out = MangaChapterOrdering.apply(rows(), ChapterFilter.All, ChapterSort.NewestFirst)
        assertEquals(137f, out.first().number, 0.001f)
    }

    @Test
    fun ascendingNumbersStartFromOneAndPutUnknownLast() {
        val out = MangaChapterOrdering.apply(rows(), ChapterFilter.All, ChapterSort.NumberAsc, number = number)
        assertEquals(listOf(106f, 107f, 134f, 137f), out.map { it.number }.dropLast(1))
        assertEquals("Numarası bilinmeyen en sonda kalmalı", -1f, out.last().number, 0.001f)
    }

    @Test
    fun descendingNumbersPutUnknownLast() {
        val out = MangaChapterOrdering.apply(rows(), ChapterFilter.All, ChapterSort.NumberDesc, number = number)
        assertEquals(137f, out.first().number, 0.001f)
        assertEquals(-1f, out.last().number, 0.001f)
    }

    @Test
    fun unreadFilterDropsCompleted() {
        val out = MangaChapterOrdering.apply(
            rows(), ChapterFilter.Unread, ChapterSort.NewestFirst,
            isCompleted = completed
        )
        assertFalse(out.any { it.completed })
        assertEquals(4, out.size)
    }

    @Test
    fun scanlatorFilterMatchesMultiGroupValues() {
        assertTrue(MangaChapterOrdering.containsScanlator("Şımarık Fansub, Tempest Fansub", "Tempest Fansub"))
        assertTrue(MangaChapterOrdering.containsScanlator("A/B", "b"))
        assertFalse(MangaChapterOrdering.containsScanlator("Garcia Fansub", "Şımarık Fansub"))
    }

    @Test
    fun scanlatorFilterAppliesOnList() {
        val out = MangaChapterOrdering.apply(
            rows(), ChapterFilter.All, ChapterSort.NewestFirst,
            scanlatorFilter = "Şımarık Fansub", scanlator = scanlator
        )
        assertEquals(2, out.size)
    }

    @Test
    fun scanlatorsAreDistinctAndSorted() {
        val list = MangaChapterOrdering.scanlatorsOf(
            listOf("Garcia Fansub", "Şımarık Fansub", null, " Garcia Fansub ", "")
        )
        assertEquals(2, list.size)
    }

    @Test
    fun hideMiscRemovesNumberlessEntries() {
        val out = MangaChapterOrdering.apply(
            rows(), ChapterFilter.HideMisc, ChapterSort.NewestFirst,
            number = number, scanlator = scanlator
        )
        assertTrue(out.none { it.number < 0f })
    }

    // ─── Kaynak artan sırada verirse "en yeni üstte" düzeltilir ───────────────

    @Test
    fun newestFirstFixesSourcesThatReturnAscendingOrder() {
        // Bazı kaynaklar 1 → N sırası veriyor; "en yeni üstte" seçiliyken 1. bölüm
        // üstte görünüyordu. Artık numara yönü algılanıp düzeltiliyor.
        val ascending = listOf(
            Row("Bölüm 1", 1f),
            Row("Bölüm 2", 2f),
            Row("Bölüm 3", 3f),
            Row("Bölüm 4", 4f)
        )
        val out = MangaChapterOrdering.apply(
            ascending, ChapterFilter.All, ChapterSort.NewestFirst, number = number
        )
        assertEquals(4f, out.first().number, 0.001f)
        assertEquals(1f, out.last().number, 0.001f)
    }

    @Test
    fun newestFirstKeepsDescendingSourceUntouched() {
        val descending = listOf(Row("Bölüm 4", 4f), Row("Bölüm 3", 3f), Row("Bölüm 2", 2f))
        val out = MangaChapterOrdering.apply(
            descending, ChapterFilter.All, ChapterSort.NewestFirst, number = number
        )
        assertEquals(listOf(4f, 3f, 2f), out.map { it.number })
    }

    @Test
    fun newestFirstDoesNotGuessWithTooFewNumbers() {
        // 2 satırdan az/az sayıda numara varsa kaynak sırasına dokunulmaz.
        val two = listOf(Row("Bölüm 1", 1f), Row("Bölüm 2", 2f))
        val out = MangaChapterOrdering.apply(two, ChapterFilter.All, ChapterSort.NewestFirst, number = number)
        assertEquals(listOf(1f, 2f), out.map { it.number })
    }

    @Test
    fun newestFirstIgnoresUnnumberedRows() {
        val mixed = listOf(
            Row("Bölüm 1", 1f),
            Row("Bölüm 2", 2f),
            Row("Duyuru", -1f),
            Row("Bölüm 3", 3f)
        )
        val out = MangaChapterOrdering.apply(mixed, ChapterFilter.All, ChapterSort.NewestFirst, number = number)
        assertEquals(3f, out.first().number, 0.001f)
    }
}
