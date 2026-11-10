package com.kitsugi.animelist.ui.screens.mylist

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MyListSearchIndexTest {
    private val entries = listOf(
        entry(1, "Shingeki no Kyojin").copy(
            titleEnglish = "Attack on Titan", titleJapanese = "進撃の巨人", year = 2013, malId = 16498
        ),
        entry(2, "Naruto").copy(subtitle = "Ninja macerası"),
        entry(3, "Re:ＺＥＲＯ − Starting Life").copy(source = "mal")
    )

    @Test
    fun preservesTitleAliasesNativeTextAndPunctuationSearch() {
        val index = MyListSearchIndex.build(entries)
        for ((query, expected) in listOf(
            "attack on titan" to listOf(1), "進撃" to listOf(1),
            "2013" to listOf(1), "rezero" to listOf(3), ":" to listOf(3)
        )) {
            assertEquals(query, expected, search(index, query, fuzzy = false).map { it.id })
        }
    }

    /**
     * v2.4.352 sözleşmesi: kaynak (`source`), tür, durum, alt başlık ve sentetik MAL kimliği
     * başlık aramasına KATILMAZ — bunlar yanlış pozitif ("mal" → her MAL kaydı) üretiyordu.
     * Bu test o kararı sabitler; meta alan yeniden aramaya eklenirse burada kırılır.
     */
    @Test
    fun metadataFieldsAreNotSearchedAsTitleNoise() {
        val index = MyListSearchIndex.build(entries)
        assertTrue("MAL kimliği aramada kullanılmamalı", search(index, "16498", fuzzy = false).isEmpty())
        assertTrue("Alt başlık aramada kullanılmamalı", search(index, "ninja", fuzzy = false).isEmpty())
        assertTrue("Kaynak adı aramada kullanılmamalı", search(index, "mal", fuzzy = false).isEmpty())
    }

    @Test
    fun shortQueriesDoNotRunFuzzySubsequenceOrEditDistance() {
        val index = MyListSearchIndex.build(entries)
        assertEquals(listOf(3), search(index, "re").map { it.id })
        assertEquals(listOf(2), search(index, "na").map { it.id })
        assertFalse(MyListSearchQuery("r", fuzzySearchEnabled = true).canMatchFuzzy)
        assertFalse(MyListSearchQuery("re", fuzzySearchEnabled = true).canMatchFuzzy)
    }

    @Test
    fun nativeOrPunctuationOnlyQueryDoesNotBecomeAnEmptyFuzzyWildcard() {
        val index = MyListSearchIndex.build(entries)
        assertEquals(listOf(1), search(index, "進撃").map { it.id })
        assertTrue(search(index, "不存在").isEmpty())
        assertTrue(search(index, "!@#").isEmpty())
        assertEquals(entries, search(index, "   "))
    }

    @Test
    fun keepsAcronymsAndTyposWhenFuzzyIsEnabled() {
        val index = MyListSearchIndex.build(entries)
        assertEquals(listOf(1), search(index, "aot").map { it.id })
        assertEquals(listOf(2), search(index, "narxto").map { it.id })
        assertTrue(search(index, "aot", fuzzy = false).isEmpty())
        assertTrue(search(index, "narxto", fuzzy = false).isEmpty())
    }

    @Test
    fun normalizationIsIndependentOfTurkishDefaultLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val index = MyListSearchIndex.build(listOf(entry(1, "INITIAL D")))
            assertEquals(listOf(1), search(index, "initial", fuzzy = false).map { it.id })
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun documentsArePositionAlignedEvenWhenProviderIdsCollide() {
        val duplicateIds = listOf(entry(1, "Naruto"), entry(1, "Bleach").copy(source = "mal"))
        val index = MyListSearchIndex.build(duplicateIds)
        assertEquals("Bleach", search(index, "bleach", fuzzy = false).single().title)
        assertEquals("Naruto", search(index, "naruto", fuzzy = false).single().title)
    }

    @Test
    fun aStaleIndexIsNotUsedForChangedRowsWithTheSameId() {
        val index = MyListSearchIndex.build(entries)
        val updated = listOf(entry(1, "Bleach"))
        assertEquals(updated, filter(updated, "bleach", index, fuzzy = false))
        assertTrue(filter(updated, "attack", index, fuzzy = false).isEmpty())
    }

    @Test
    fun indexedAndOneOffFiltersAgreeForAllSearchModes() {
        val index = MyListSearchIndex.build(entries)
        for (query in listOf("", "re", "aot", "narxto", "進撃", "rezero", "2013", "!@#")) {
            for (fuzzy in listOf(true, false)) {
                assertEquals("$query / $fuzzy", filter(entries, query, null, fuzzy), search(index, query, fuzzy))
            }
        }
    }

    @Test
    fun bandedDistanceAndReusedBuffersAgreeWithFullLevenshtein() {
        val random = Random(20261022)
        repeat(100) {
            val needle = random.word(3 + random.nextInt(10))
            val query = MyListSearchQuery(needle, fuzzySearchEnabled = true)
            val tolerance = when { needle.length <= 4 -> 1; needle.length <= 8 -> 2; else -> 3 }
            repeat(50) {
                val hay = random.word(1 + random.nextInt(16))
                val expected = hay.contains(needle) || isSubsequence(hay, needle) || distance(hay, needle) <= tolerance
                assertEquals("$hay / $needle", expected, query.matchesFuzzy(MyListFuzzyText(hay)))
            }
        }
    }

    @Test
    fun preparationAndFilteringCooperateWithCancellation() {
        val large = List(2_603) { entry(it, "Library title $it") }
        var checks = 0
        try {
            MyListSearchIndex.build(large) {
                if (++checks == 3) throw CancellationException("superseded")
            }
            throw AssertionError("Preparation ignored cancellation")
        } catch (_: CancellationException) {
            assertEquals(3, checks)
        }
    }

    @Test
    fun filtersStillApplyBeforeSearchAndKeepTheirInputOrder() {
        val rows = listOf(
            entry(1, "Naruto").copy(isFavorite = true, score = 9, year = 1999, isPrivate = true),
            entry(2, "Naruto Manga").copy(type = MediaType.Manga, score = 9, year = 1999),
            entry(3, "Naruto Shippuden").copy(isFavorite = true, score = 7, year = 2007)
        )
        val result = filterMyListEntries(
            rows, "naruto", "favorites", "anime", "all", "high", "classic", "private"
        )
        assertEquals(listOf(rows.first()), result)
        assertFalse(rows[1].isFavorite)
    }

    private fun search(index: MyListSearchIndex, query: String, fuzzy: Boolean = true) =
        filter(index.entries, query, index, fuzzy)

    private fun filter(rows: List<MediaEntry>, query: String, index: MyListSearchIndex?, fuzzy: Boolean) =
        filterMyListEntries(
            rows, query, "all", "all", "all", "all", "all", "all",
            fuzzySearchOverride = fuzzy
        )

    private fun entry(id: Int, title: String) = MediaEntry(
        id = id, title = title, type = MediaType.Anime, status = WatchStatus.Planned,
        score = null, progress = 0, total = null, source = "anilist"
    )

    private fun Random.word(length: Int) = buildString { repeat(length) { append("abcdefg"[nextInt(7)]) } }

    private fun isSubsequence(hay: String, needle: String): Boolean {
        var index = 0
        for (char in hay) if (index < needle.length && char == needle[index]) index++
        return index == needle.length
    }

    private fun distance(a: String, b: String): Int {
        val rows = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) rows[i][0] = i
        for (j in 0..b.length) rows[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            rows[i][j] = minOf(rows[i - 1][j] + 1, rows[i][j - 1] + 1,
                rows[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        }
        return rows[a.length][b.length]
    }
}
