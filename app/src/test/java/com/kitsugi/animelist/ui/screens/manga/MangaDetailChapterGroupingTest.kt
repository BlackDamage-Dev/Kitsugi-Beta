package com.kitsugi.animelist.ui.screens.manga

import com.kitsugi.animelist.data.manga.MangaChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaDetailChapterGroupingTest {

    @Test
    fun `same chapter number from different scanlators are grouped into single group`() {
        val rows = listOf(
            MangaDetailViewModel.ChapterRow(
                chapter = MangaChapter(url = "ch1_anime_who", name = "Bölüm 1", chapterNumber = 1f, scanlator = "AnimeWho")
            ),
            MangaDetailViewModel.ChapterRow(
                chapter = MangaChapter(url = "ch1_webtoon_tr", name = "Bölüm 1", chapterNumber = 1f, scanlator = "WebtoonTR")
            ),
            MangaDetailViewModel.ChapterRow(
                chapter = MangaChapter(url = "ch2_anime_who", name = "Bölüm 2", chapterNumber = 2f, scanlator = "AnimeWho")
            )
        )

        val groups = MangaDetailViewModel.groupChapterRows(rows, "Test Manga")
        assertEquals(2, groups.size)

        val group1 = groups[0]
        assertEquals("n:1.0", group1.key)
        assertEquals("Bölüm 1", group1.title)
        assertTrue(group1.isMultiple)
        assertEquals(2, group1.variants.size)
        assertEquals("AnimeWho", group1.variants[0].chapter.scanlator)
        assertEquals("WebtoonTR", group1.variants[1].chapter.scanlator)

        val group2 = groups[1]
        assertEquals("n:2.0", group2.key)
        assertFalse(group2.isMultiple)
        assertEquals(1, group2.variants.size)
    }

    @Test
    fun `unassigned chapter numbers resolve from title`() {
        val rows = listOf(
            MangaDetailViewModel.ChapterRow(
                chapter = MangaChapter(url = "ch42_a", name = "Bölüm 42 - Karşılaşma", chapterNumber = -1f, scanlator = "Grup A")
            ),
            MangaDetailViewModel.ChapterRow(
                chapter = MangaChapter(url = "ch42_b", name = "Bölüm 42", chapterNumber = -1f, scanlator = "Grup B")
            )
        )

        val groups = MangaDetailViewModel.groupChapterRows(rows, "Manga")
        assertEquals(1, groups.size)
        assertTrue(groups[0].isMultiple)
        assertEquals(2, groups[0].variants.size)
    }

    @Test
    fun `oneshots and unresolvable numbers do not merge`() {
        val rows = listOf(
            MangaDetailViewModel.ChapterRow(
                chapter = MangaChapter(url = "oneshot_1", name = "Oneshot", chapterNumber = -1f, scanlator = "Grup 1")
            ),
            MangaDetailViewModel.ChapterRow(
                chapter = MangaChapter(url = "oneshot_2", name = "Oneshot", chapterNumber = -1f, scanlator = "Grup 2")
            )
        )

        val groups = MangaDetailViewModel.groupChapterRows(rows, "Manga")
        assertEquals(2, groups.size)
        assertFalse(groups[0].isMultiple)
        assertFalse(groups[1].isMultiple)
    }

    @Test
    fun `completion and in progress state calculation across variants`() {
        val completedRow = MangaDetailViewModel.ChapterRow(
            chapter = MangaChapter(url = "ch1_a", name = "Bölüm 1", chapterNumber = 1f, scanlator = "A"),
            isCompleted = true
        )
        val inProgressRow = MangaDetailViewModel.ChapterRow(
            chapter = MangaChapter(url = "ch1_b", name = "Bölüm 1", chapterNumber = 1f, scanlator = "B"),
            isCompleted = false,
            lastPageIndex = 5,
            totalPages = 20,
            isInProgress = true
        )

        val groupNotAllCompleted = MangaDetailViewModel.ChapterGroup(
            key = "n:1.0",
            title = "Bölüm 1",
            variants = listOf(completedRow, inProgressRow)
        )
        assertFalse(groupNotAllCompleted.isCompleted)
        assertTrue(groupNotAllCompleted.isInProgress)

        val allCompletedGroup = MangaDetailViewModel.ChapterGroup(
            key = "n:1.0",
            title = "Bölüm 1",
            variants = listOf(completedRow, completedRow.copy(chapter = inProgressRow.chapter))
        )
        assertTrue(allCompletedGroup.isCompleted)
        assertFalse(allCompletedGroup.isInProgress)
    }
}
