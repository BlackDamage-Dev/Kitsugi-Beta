package com.kitsugi.animelist.ui.screens.manga

import com.kitsugi.animelist.data.manga.ChapterRecognition
import java.util.Locale

internal enum class MangaChapterSort { Oldest, Newest, Updated }
internal enum class MangaChapterReadFilter { All, Unread, Read }

/** Shared single search/filter pipeline; never mutates the source's navigation order. */
internal fun filterMangaChapters(
    chapters: List<MangaDetailViewModel.ChapterRow>,
    mangaTitle: String,
    query: String,
    sort: MangaChapterSort,
    readFilter: MangaChapterReadFilter
): List<MangaDetailViewModel.ChapterRow> {
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    val filtered = chapters.filter { row ->
        val matchesRead = when (readFilter) {
            MangaChapterReadFilter.All -> true
            MangaChapterReadFilter.Unread -> !row.isCompleted
            MangaChapterReadFilter.Read -> row.isCompleted
        }
        matchesRead && (normalizedQuery.isEmpty() ||
            row.chapter.name.lowercase(Locale.ROOT).contains(normalizedQuery) ||
            row.chapter.chapterNumber.takeIf { it >= 0 }?.toString()?.contains(normalizedQuery) == true)
    }
    fun number(row: MangaDetailViewModel.ChapterRow) = ChapterRecognition.parseChapterNumber(
        mangaTitle, row.chapter.name, row.chapter.chapterNumber.toDouble()
    )
    return when (sort) {
        MangaChapterSort.Oldest -> filtered.sortedBy { number(it) }
        MangaChapterSort.Newest -> filtered.sortedByDescending { number(it) }
        MangaChapterSort.Updated -> filtered.sortedByDescending { it.chapter.uploadDate }
    }
}
