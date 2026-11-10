package com.kitsugi.animelist.ui.screens.mylist

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MyListFilterHelpersTest {

    private val entries = listOf(
        entry(
            id = 1,
            title = "Eski Anime",
            english = "Old Anime",
            type = MediaType.Anime,
            status = WatchStatus.Completed,
            score = 9,
            year = 1998,
            favorite = true
        ),
        entry(
            id = 2,
            title = "Yeni Manga",
            type = MediaType.Manga,
            status = WatchStatus.Planned,
            score = null,
            year = 2025
        ),
        entry(
            id = 3,
            title = "Tekrar",
            type = MediaType.Anime,
            status = WatchStatus.Repeating,
            score = 4,
            year = 2016,
            repeating = true
        )
    )

    @Test
    fun filters_mediaTypeScoreAndYear_withSharedContract() {
        val result = filter(
            type = "anime",
            score = "high",
            year = "classic"
        )

        assertEquals(listOf(1), result.map { it.id })
    }

    @Test
    fun favoritesPseudoStatus_andLocalizedTitleSearch_areSupported() {
        val favorites = filter(status = "favorites")
        val englishSearch = filter(query = "old anime")

        assertEquals(listOf(1), favorites.map { it.id })
        assertEquals(listOf(1), englishSearch.map { it.id })
    }

    @Test
    fun repeatingExtra_acceptsRepeatingStatus() {
        val result = filter(extra = "repeating")

        assertEquals(listOf(3), result.map { it.id })
    }

    @Test
    fun statusGrouping_usesTheSameStableOrderAsListem() {
        val groups = groupMyListEntriesByStatus(entries)

        assertEquals(
            listOf(WatchStatus.Repeating, WatchStatus.Planned, WatchStatus.Completed),
            groups.map { it.first }
        )
        assertTrue(groups.all { it.second.isNotEmpty() })
    }

    @Test
    fun sortByScore_keepsHighestScoreFirst() {
        val result = applySort(entries, "score")

        assertEquals(listOf(1, 3, 2), result.map { it.id })
    }

    // ── Eklenme tarihi sıralaması (2026-10-23 düzeltmesi) ────────────────────

    @Test
    fun newestSort_ordersByAddedAtNotLocalRowId() {
        // Yerel id, toplu içe aktarımda API sayfa sırasıdır; ekleme sırası DEĞİL.
        // Yüksek id'li ama erken eklenmiş kayıt "Son eklenen"de üste çıkmamalı.
        val list = listOf(
            entry(id = 90, title = "İlk eklenen", addedAt = 1_000L),
            entry(id = 10, title = "Son eklenen", addedAt = 3_000L),
            entry(id = 50, title = "Ortada eklenen", addedAt = 2_000L)
        )

        assertEquals(listOf(10, 50, 90), applySort(list, "newest").map { it.id })
        assertEquals(listOf(90, 50, 10), applySort(list, "oldest").map { it.id })
    }

    @Test
    fun unknownAddedAt_sinksToEndInBothDirections() {
        val list = listOf(
            entry(id = 1, title = "Bilinmiyor", addedAt = 0L),
            entry(id = 2, title = "Eski", addedAt = 100L),
            entry(id = 3, title = "Yeni", addedAt = 200L)
        )

        assertEquals(listOf(3, 2, 1), applySort(list, "newest").map { it.id })
        assertEquals(listOf(2, 3, 1), applySort(list, "oldest").map { it.id })
    }

    @Test
    fun missingAddedAt_fallsBackToUpdatedAtInsteadOfUnknownBucket() {
        // Kaynak eklenme zamanı vermiyor (MAL/Bangumi) → elde tek zaman damgası
        // `updatedAt`. Bu satırlar "bilinmiyor" kovasına düşüp sıralamayı
        // bozmamalı; gerçek tarihi olan satırlarla birlikte sıralanmalı.
        val list = listOf(
            entry(id = 1, title = "Vekil tarihli", addedAt = 0L, updatedAt = 300L),
            entry(id = 2, title = "Gerçek tarihli", addedAt = 200L),
            entry(id = 3, title = "Hiç tarihi yok", addedAt = 0L, updatedAt = 0L)
        )

        assertEquals(listOf(1, 2, 3), applySort(list, "newest").map { it.id })
        assertEquals(listOf(2, 1, 3), applySort(list, "oldest").map { it.id })
    }

    @Test
    fun unknownAddedAt_isOrderedByTitleForDeterministicOutputAcrossSources() {
        // Yerel `id` kaynak başına farklı uzaydan gelir (Simkl 150M+, Kitsu 300M+).
        // "Tümü" sekmesinde aynı kovadaki satırlar ada göre sıralanmalı ki sıra
        // kaynak karışımına göre değişmesin.
        val list = listOf(
            entry(id = 900, title = "C Kayıt", addedAt = 0L),
            entry(id = 5, title = "A Kayıt", addedAt = 0L),
            entry(id = 150_000_321, title = "B Kayıt", addedAt = 0L)
        )

        assertEquals(listOf(5, 150_000_321, 900), applySort(list, "newest").map { it.id })
        assertEquals(listOf(5, 150_000_321, 900), applySort(list, "oldest").map { it.id })
    }

    @Test
    fun addedPeriodGrouping_usesUpdatedAtFallback() {
        val zone = java.time.ZoneId.systemDefault()
        val now = java.time.LocalDate.of(2026, 10, 23).atTime(12, 0).atZone(zone).toEpochSecond()
        val list = listOf(
            // addedAt boş, updatedAt bugün → "Bugün" bölümüne düşmeli.
            entry(id = 1, title = "Vekil", addedAt = 0L, updatedAt = now),
            entry(id = 2, title = "Gerçek", addedAt = now - 86_400L),
            entry(id = 3, title = "Bilinmiyor", addedAt = 0L, updatedAt = 0L)
        )

        val groups = groupMyListEntriesByAddedPeriod(list, nowEpochSeconds = now)

        assertEquals(
            listOf(MyListAddedPeriod.TODAY, MyListAddedPeriod.YESTERDAY, MyListAddedPeriod.UNKNOWN),
            groups.map { it.first }
        )
    }

    @Test
    fun scoreAsc_keepsUnratedLast() {
        val list = listOf(
            entry(id = 1, title = "Puansız", score = null),
            entry(id = 2, title = "Düşük", score = 3),
            entry(id = 3, title = "Yüksek", score = 9)
        )

        assertEquals(listOf(2, 3, 1), applySort(list, "score_asc").map { it.id })
        assertEquals(listOf(3, 2, 1), applySort(list, "score").map { it.id })
    }

    @Test
    fun progressSort_usesCompletionRatioNotRawEpisodes() {
        val list = listOf(
            entry(id = 1, title = "Bitti", progress = 12, total = 12),
            entry(id = 2, title = "Uzun yol", progress = 100, total = 1000),
            entry(id = 3, title = "Yarıda", progress = 6, total = 12),
            entry(id = 4, title = "Toplamı bilinmiyor", progress = 500, total = null)
        )

        // Oran: %100, %50, %10; toplamı bilinmeyen sonda.
        assertEquals(listOf(1, 3, 2, 4), applySort(list, "progress").map { it.id })
    }

    @Test
    fun updatedSort_normalizesMillisecondTimestamps() {
        // Bangumi updatedAt'yi milisaniye yazar; normalize edilmezse eski Bangumi
        // kaydı (×1000 büyük değer) daha yeni saniye-damgalı kaydı ezerdi.
        val list = listOf(
            entry(id = 1, title = "Yeni (saniye)", updatedAt = 1_700_000_000L),
            entry(id = 2, title = "Eski (milisaniye)", updatedAt = 1_600_000_000_000L)
        )

        assertEquals(listOf(1, 2), applySort(list, "updated_desc").map { it.id })
    }

    @Test
    fun titleSort_usesDisplayedTitleForSelectedLanguage() {
        val list = listOf(
            entry(id = 1, title = "Zzz Romaji", english = "Apple Title"),
            entry(id = 2, title = "Aaa Romaji", english = "Zebra Title")
        )

        // Başlık dili verilmezse depolanan başlık, verilirse EKRANDAKİ başlık sıralanır.
        assertEquals(listOf(2, 1), applySort(list, "title").map { it.id })
        assertEquals(
            listOf(1, 2),
            applySort(list, "title", titleLanguage = "ENGLISH").map { it.id }
        )
    }

    // ── Düz akış / durum bölümü kararı ───────────────────────────────────────

    @Test
    fun explicitSort_disablesStatusSections() {
        // Varsayılan gezinme: bölümler açık.
        assertEquals(
            false,
            myListUsesFlatSortedLayout(MY_LIST_SORT_DEFAULT, "all", separatedListStyle = true)
        )
        // Kullanıcı sıralama seçti: global sıra kazanır, bölümler kapanır.
        assertEquals(
            true,
            myListUsesFlatSortedLayout("newest", "all", separatedListStyle = true)
        )
        assertEquals(
            true,
            myListUsesFlatSortedLayout("score", "all", separatedListStyle = true)
        )
        // Tek durum filtresi: varsayılan sıralamada bile düz (tek bölüm başlığı gürültü).
        assertEquals(
            true,
            myListUsesFlatSortedLayout(MY_LIST_SORT_DEFAULT, "completed", separatedListStyle = true)
        )
        // Ayrılmış tarz kapalı: her zaman düz.
        assertEquals(
            true,
            myListUsesFlatSortedLayout(MY_LIST_SORT_DEFAULT, "all", separatedListStyle = false)
        )
    }

    // ── Eklenme dönemi bölümleri ─────────────────────────────────────────────

    @Test
    fun addedPeriodGrouping_bucketsByAgeInSeenOrder() {
        val zone = java.time.ZoneId.systemDefault()
        val now = java.time.LocalDate.of(2026, 10, 23)
            .atTime(12, 0)
            .atZone(zone)
            .toEpochSecond()
        val day = 86_400L
        val list = listOf(
            entry(id = 1, title = "Bugün", addedAt = now),
            entry(id = 2, title = "Dün", addedAt = now - day),
            entry(id = 3, title = "Bu hafta", addedAt = now - 3 * day),
            entry(id = 4, title = "Geçen ay", addedAt = now - 40 * day),
            entry(id = 5, title = "Bilinmiyor", addedAt = 0L)
        )

        val groups = groupMyListEntriesByAddedPeriod(list, nowEpochSeconds = now)

        assertEquals(
            listOf(
                MyListAddedPeriod.TODAY,
                MyListAddedPeriod.YESTERDAY,
                MyListAddedPeriod.THIS_WEEK,
                MyListAddedPeriod.LAST_MONTH,
                MyListAddedPeriod.UNKNOWN
            ),
            groups.map { it.first }
        )
        assertEquals(listOf(1, 2, 3, 4, 5), groups.flatMap { it.second }.map { it.id })
    }

    @Test
    fun searchDoesNotMatchSourceSubtitleTypeStatusOrSyntheticIds() {
        val sourceHeavyEntries = listOf(
            entry(
                id = 10,
                title = "Bleach",
                type = MediaType.Manga,
                status = WatchStatus.Completed,
                score = 8,
                year = 2001
            ).copy(source = "shikimori", subtitle = "Manga • 2001", malId = 400_086_123),
            entry(
                id = 11,
                title = "86 - Eighty Six",
                type = MediaType.Anime,
                status = WatchStatus.Watching,
                score = 9,
                year = 2021
            ).copy(source = "bangumi", subtitle = "bangumi", malId = 500_111_222)
        )

        fun runQuery(query: String) = filterMyListEntries(
            entries = sourceHeavyEntries,
            searchQuery = query,
            selectedStatusFilterId = "all",
            selectedTypeFilterId = "all",
            selectedFavoriteFilterId = "all",
            selectedScoreFilterId = "all",
            selectedYearFilterId = "all",
            selectedExtraFilterId = "all"
        ).map { it.id }

        assertTrue(runQuery("shikimori").isEmpty())
        assertTrue(runQuery("bangumi").isEmpty())
        assertTrue(runQuery("manga").isEmpty())
        assertTrue(runQuery("completed").isEmpty())
        // "86" yalnızca başlığı gerçekten 86 olan yapımı bulmalı; malId=400086123 eşleşmemeli.
        assertEquals(listOf(11), runQuery("86"))
    }

    @Test
    fun searchSupportsPunctuationMultiTokenTurkishCjkAndAliasesWithoutFalseMatches() {
        val catalog = listOf(
            entry(
                id = 20,
                title = "Kaijuu 8-gou",
                english = "Kaiju No. 8",
                type = MediaType.Anime,
                status = WatchStatus.Watching,
                score = 8,
                year = 2024
            ).copy(titleJapanese = "怪獣8号"),
            entry(
                id = 21,
                title = "Kaijuu 8-gou",
                english = null,
                type = MediaType.Anime,
                status = WatchStatus.Watching,
                score = 8,
                year = 2024
            ),
            entry(
                id = 22,
                title = "Isekai Ojisan",
                english = "Uncle from Another World",
                type = MediaType.Anime,
                status = WatchStatus.Completed,
                score = 7,
                year = 2022
            )
        )
        val aliases = mapOf(21 to setOf("Kaiju No. 8", "怪兽8号"))

        fun runQuery(query: String) = filterMyListEntries(
            entries = catalog,
            searchQuery = query,
            selectedStatusFilterId = "all",
            selectedTypeFilterId = "all",
            selectedFavoriteFilterId = "all",
            selectedScoreFilterId = "all",
            selectedYearFilterId = "all",
            selectedExtraFilterId = "all",
            searchAliasesByEntryId = aliases
        ).map { it.id }

        assertEquals(listOf(20, 21), runQuery("kaiju no 8"))
        assertEquals(listOf(20, 21), runQuery("kaiju 8"))
        assertTrue("Numeric token 2 must not match 8", runQuery("kaiju 2").isEmpty())
        assertEquals(listOf(22), runQuery("İsekai"))
        assertEquals(listOf(20, 21), runQuery("kaıju no 8"))
        // CJK sorgusu yalnızca ilgili CJK başlıkları bulmalı; boş needle hatasıyla tüm listeyi döndürmemeli.
        assertEquals(listOf(20), runQuery("怪獣8号"))
        assertEquals(listOf(21), runQuery("怪兽8号"))
        // Tam 4 haneli yıl eşleşir, 2 haneli parça eşleşmez.
        assertEquals(listOf(20, 21), runQuery("2024"))
        assertTrue(runQuery("24").isEmpty())
    }

    private fun filter(
        query: String = "",
        status: String = "all",
        type: String = "all",
        favorite: String = "all",
        score: String = "all",
        year: String = "all",
        extra: String = "all"
    ): List<MediaEntry> = filterMyListEntries(
        entries = entries,
        searchQuery = query,
        selectedStatusFilterId = status,
        selectedTypeFilterId = type,
        selectedFavoriteFilterId = favorite,
        selectedScoreFilterId = score,
        selectedYearFilterId = year,
        selectedExtraFilterId = extra
    )

    private fun entry(
        id: Int,
        title: String,
        english: String? = null,
        type: MediaType = MediaType.Anime,
        status: WatchStatus = WatchStatus.Watching,
        score: Int? = null,
        year: Int = 2020,
        favorite: Boolean = false,
        repeating: Boolean = false,
        progress: Int = 0,
        total: Int? = null,
        addedAt: Long = 0L,
        updatedAt: Long = 0L
    ) = MediaEntry(
        id = id,
        title = title,
        titleEnglish = english,
        type = type,
        status = status,
        score = score,
        progress = progress,
        total = total,
        isFavorite = favorite,
        isRepeating = repeating,
        year = year,
        addedAt = addedAt,
        updatedAt = updatedAt
    )
}
