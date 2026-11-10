package com.kitsugi.animelist.ui.screens.mylist

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MyListPresentationTest {
    private fun entry(id: Int, source: String, title: String, status: WatchStatus = WatchStatus.Completed,
                      malId: Int? = null, adult: Boolean = false) = MediaEntry(
        id = id, source = source, title = title, status = status, type = MediaType.Anime,
        score = null, progress = 0, total = null, malId = malId, isAdult = adult
    )

    private fun filters(query: String = "", status: String = "all", sort: String = "title") =
        MyListFilters(query, status, "all", "all", "all", "all", "all", sort, fuzzySearchEnabled = false)

    private fun combined(index: MyListSourceIndex): CombinedMyListLibrary {
        val items = groupMyListEntries(index.allEntries)
        return CombinedMyListLibrary(index, items, items.map { it.entry }, items.associate { it.entry.id to it.sourceIds })
    }

    @Test
    fun incomingPagesAreFilteredAndSortedBeforeSelection() {
        val rows = listOf(
            entry(1, "anilist", "Zulu"), entry(2, "anilist", "Alpha"),
            entry(3, "mal", "Delta"), entry(4, "mal", "Bravo"),
            entry(5, "mal", "Not completed", WatchStatus.Planned)
        )
        val index = indexMyListSources(rows, showAdultContent = true)
        val presentation = prepareMyListPresentation(index, combined(index), filters(status = "completed"))
        val ani = presentation.pages[MY_LIST_ANILIST_TAB_INDEX]!!
        val mal = presentation.pages[MY_LIST_MAL_TAB_INDEX]!!
        assertEquals(listOf("Alpha", "Zulu"), ani.visibleEntries.map { it.title })
        assertEquals(listOf("Bravo", "Delta"), mal.visibleEntries.map { it.title })
        assertEquals(mal.visibleEntries, mal.groupedEntries.single().second)
        // There is no selected-tab argument: the exact same page data is shown during
        // the drag and after the pager settles.
        assertEquals(2, mal.visibleEntries.size)
    }

    @Test
    fun sourceIndexUsesAliasesAndNeverIncludesInvalidBangumiOrHiddenAdultRows() {
        val rows = listOf(
            entry(1, "Jikan (MAL)", "Mal alias"), entry(2, "shiki", "Shiki alias"),
            entry(3, "bgm.tv", "Verified", malId = 500_000_003),
            entry(4, "bangumi", "Unverified"), entry(5, "kitsu", "Adult", adult = true)
        )
        val hidden = indexMyListSources(rows, showAdultContent = false)
        assertEquals(listOf(1), hidden.entriesByTab[MY_LIST_MAL_TAB_INDEX].map { it.id })
        assertEquals(listOf(2), hidden.entriesByTab[MY_LIST_SHIKIMORI_TAB_INDEX].map { it.id })
        assertEquals(listOf(3), hidden.entriesByTab[MY_LIST_BANGUMI_TAB_INDEX].map { it.id })
        assertFalse(hidden.allEntries.any { it.id == 5 })
        assertTrue(indexMyListSources(rows, true).entriesByTab[MY_LIST_KITSU_TAB_INDEX].any { it.id == 5 })
    }

    @Test
    fun sourcePagesCanLoadWhileCombinedLibraryIsStillPreparing() {
        val index = indexMyListSources(listOf(entry(1, "mal", "One")), true)
        val pending = prepareMyListPresentation(index, null, filters())
        assertNull(pending.pages[MY_LIST_ALL_TAB_INDEX])
        assertNotNull(pending.pages[MY_LIST_MAL_TAB_INDEX])
        assertEquals(1, pending.pages[MY_LIST_MAL_TAB_INDEX]!!.visibleEntries.size)
        val ready = prepareMyListPresentation(index, combined(index), filters())
        assertEquals(1, ready.pages[MY_LIST_ALL_TAB_INDEX]!!.visibleEntries.size)
    }

    @Test
    fun globalSearchRemainsAvailableExceptForBangumi() {
        val rows = listOf(entry(1, "anilist", "Match"), entry(2, "mal", "Match Two"),
            entry(3, "bangumi", "Match Bangumi", malId = 500_000_003))
        val index = indexMyListSources(rows, true)
        val presentation = prepareMyListPresentation(index, combined(index), filters(query = "match"))
        assertEquals(setOf(1, 2, 3), presentation.pages[MY_LIST_MAL_TAB_INDEX]!!.visibleEntries.map { it.id }.toSet())
        assertEquals(listOf(3), presentation.pages[MY_LIST_BANGUMI_TAB_INDEX]!!.visibleEntries.map { it.id })
    }

    @Test
    fun cancelledPreparationStopsWhileScanningALargeLibrary() {
        val rows = (1..5_000).map { entry(it, "mal", "Title $it") }
        val index = indexMyListSources(rows, true)
        var checks = 0
        var cancelled = false
        try {
            prepareMyListPresentation(index, null, filters(query = "no match")) {
                if (++checks == 4) throw java.util.concurrent.CancellationException("superseded")
            }
        } catch (_: java.util.concurrent.CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertEquals(4, checks)
    }

    @Test
    fun largeLibraryIsIndexedAndAllRowsRemainReachable() {
        val rows = (1..5_000).map { entry(it, if (it % 2 == 0) "anilist" else "mal", "Title $it") }
        val index = indexMyListSources(rows, true)
        val pending = prepareMyListPresentation(index, null, filters(sort = "newest"))
        assertEquals(2_500, pending.pages[MY_LIST_ANILIST_TAB_INDEX]!!.visibleEntries.size)
        assertEquals(2_500, pending.pages[MY_LIST_MAL_TAB_INDEX]!!.visibleEntries.size)
        assertEquals(5_000, index.allEntries.size)
    }
}
