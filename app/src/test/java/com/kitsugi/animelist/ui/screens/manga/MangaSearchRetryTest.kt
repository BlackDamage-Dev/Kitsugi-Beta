package com.kitsugi.animelist.ui.screens.manga

import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.data.manga.MangaPage
import com.kitsugi.animelist.data.manga.MangaSource
import com.kitsugi.animelist.data.manga.MangaSourceResult
import org.junit.Assert.assertEquals
import org.junit.Test

class MangaSearchRetryTest {
    private class FakeSource(override val pkgName: String) : MangaSource {
        override val name = pkgName
        override val lang = "tr"
        override val baseUrl = "https://example.test"
        override suspend fun fetchPopularManga(page: Int): MangaSourceResult = error("not used")
        override suspend fun fetchSearchManga(page: Int, query: String): MangaSourceResult = error("not used")
        override suspend fun fetchMangaDetails(mangaUrl: String): MangaDetails = error("not used")
        override suspend fun fetchChapterList(mangaUrl: String): List<MangaChapter> = error("not used")
        override suspend fun fetchPageList(chapter: MangaChapter): List<MangaPage> = error("not used")
    }

    @Test fun retrySelectsFailedSourceWithoutConsultingCooldown() {
        val mangaTr = FakeSource("eu.kanade.tachiyomi.extension.tr.mangatr")
        val other = FakeSource("other")
        val states = listOf(MangaSourceFetchState(mangaTr, error = "captcha"), MangaSourceFetchState(other))
        assertEquals(listOf(mangaTr), retrySearchTargets(states, listOf(mangaTr, other), mangaTr))
        assertEquals(listOf(mangaTr), retrySearchTargets(states, listOf(mangaTr, other), null))
        assertEquals(emptyList<MangaSource>(), retrySearchTargets(states, listOf(mangaTr, other), other))
    }

    @Test fun retryCanReachAnEnabledSourceWhenAutomaticCandidatesAreEmpty() {
        val mangaTr = FakeSource("eu.kanade.tachiyomi.extension.tr.mangatr")
        assertEquals(listOf(mangaTr), retrySearchTargets(emptyList(), listOf(mangaTr), null))
    }
}
