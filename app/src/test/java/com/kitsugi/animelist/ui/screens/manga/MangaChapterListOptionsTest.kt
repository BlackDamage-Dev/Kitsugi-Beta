package com.kitsugi.animelist.ui.screens.manga

import com.kitsugi.animelist.data.manga.MangaChapter
import org.junit.Assert.*
import org.junit.Test

class MangaChapterListOptionsTest {
    private fun row(n: Float, completed: Boolean = false, date: Long = 0) =
        MangaDetailViewModel.ChapterRow(
            MangaChapter("/chapter/$n", "Bölüm $n", n, uploadDate = date),
            isCompleted = completed
        )

    private fun select(
        rows: List<MangaDetailViewModel.ChapterRow>,
        query: String = "",
        sort: MangaChapterSort = MangaChapterSort.Oldest,
        filter: MangaChapterReadFilter = MangaChapterReadFilter.All
    ) = filterMangaChapters(rows, "Test manga", query, sort, filter)

    @Test fun sortsNumericallyAndKeepsSourceNavigationOrder() {
        val rows = listOf(row(10f), row(2f), row(1f), row(2.5f))
        assertEquals(listOf(1f, 2f, 2.5f, 10f), select(rows).map { it.chapter.chapterNumber })
        assertEquals(listOf(10f, 2.5f, 2f, 1f), select(rows, sort = MangaChapterSort.Newest).map { it.chapter.chapterNumber })
        assertEquals(listOf(10f, 2f, 1f, 2.5f), rows.map { it.chapter.chapterNumber })
    }

    @Test fun recognizesNumbersWhenTheSourceOmitsThem() {
        val rows = listOf("Bölüm 10", "Bölüm 2").map { name ->
            MangaDetailViewModel.ChapterRow(MangaChapter(name, name))
        }
        assertEquals(listOf("Bölüm 2", "Bölüm 10"), select(rows).map { it.chapter.name })
    }

    @Test fun combinesSearchAndReadFilterWithoutDuplicatingRows() {
        val rows = listOf(row(42f, true), row(42.5f), row(5f))
        assertEquals(listOf(42.5f), select(rows, " 42 ", filter = MangaChapterReadFilter.Unread).map { it.chapter.chapterNumber })
        assertEquals(listOf(42f), select(rows, "42", filter = MangaChapterReadFilter.Read).map { it.chapter.chapterNumber })
        assertEquals(2, select(rows, "42").size)
        assertTrue(select(rows, "missing").isEmpty())
        assertEquals(3, select(rows).size)
    }

    @Test fun updatedSortUsesUploadDateAndEmptyListsAreSafe() {
        val rows = listOf(row(1f, date = 50), row(2f, date = 200), row(3f, date = 100))
        assertEquals(listOf(2f, 3f, 1f), select(rows, sort = MangaChapterSort.Updated).map { it.chapter.chapterNumber })
        assertTrue(select(emptyList()).isEmpty())
    }
}
