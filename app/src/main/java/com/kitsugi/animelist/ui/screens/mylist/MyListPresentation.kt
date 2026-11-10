package com.kitsugi.animelist.ui.screens.mylist

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.WatchStatus
import java.util.IdentityHashMap

/** A single snapshot is shared by the source picker and every pager page. */
internal class MyListSourceIndex(
    val inputEntries: List<MediaEntry>,
    val showAdultContent: Boolean,
    val allEntries: List<MediaEntry>,
    val entriesByTab: List<List<MediaEntry>>
)

internal fun indexMyListSources(entries: List<MediaEntry>, showAdultContent: Boolean): MyListSourceIndex {
    val all = ArrayList<MediaEntry>(entries.size)
    val byTab = List(MY_LIST_TAB_COUNT) { ArrayList<MediaEntry>() }
    for (entry in entries) {
        if (!showAdultContent && entry.isAdult) continue
        all.add(entry)
        val tab = myListTabIndexForSource(entry.source) ?: continue
        if (myListSourceMatchesTab(tab, entry.source, entry.malId)) byTab[tab].add(entry)
    }
    return MyListSourceIndex(entries, showAdultContent, all, byTab)
}

internal class MyListLibraryState(
    val sourceIndex: MyListSourceIndex,
    val combinedLibrary: CombinedMyListLibrary?
)

/** The combined entries and badges must always come from the same grouping pass. */
internal class CombinedMyListLibrary(
    val sourceIndex: MyListSourceIndex,
    val items: List<MyListLibraryItem>,
    val entries: List<MediaEntry>,
    val sourceBadgesByEntryId: Map<Int, List<String>>
)

internal data class MyListFilters(
    val query: String,
    val status: String,
    val type: String,
    val favorite: String,
    val score: String,
    val year: String,
    val extra: String,
    val sort: String,
    val fuzzySearchEnabled: Boolean = true
)

internal class MyListPagePresentation(
    /** The source's own rows, used to distinguish an empty library from an empty result. */
    val sourceEntries: List<MediaEntry>,
    /** Input to the filter/status sheet (global search is supported outside Bangumi). */
    val selectedEntries: List<MediaEntry>,
    val visibleEntries: List<MediaEntry>,
    val groupedEntries: List<Pair<WatchStatus, List<MediaEntry>>>
)

internal class MyListPresentation(
    val sourceIndex: MyListSourceIndex,
    val combinedLibrary: CombinedMyListLibrary?,
    val filters: MyListFilters,
    /** Null means the combined library is still being prepared, not that it is empty. */
    val pages: List<MyListPagePresentation?>
)

/**
 * Prepare *every* page with the same filters and sort before it enters the pager viewport.
 * This also keeps the expensive search/sort/group work off the Compose thread. Source tabs
 * sharing the global-search input reuse the same result rather than filtering it six times.
 */
internal fun prepareMyListPresentation(
    sourceIndex: MyListSourceIndex,
    combinedLibrary: CombinedMyListLibrary?,
    filters: MyListFilters,
    checkCancelled: () -> Unit = {}
): MyListPresentation {
    val processed = IdentityHashMap<List<MediaEntry>, Pair<List<MediaEntry>, List<Pair<WatchStatus, List<MediaEntry>>>>>()
    val pages = (0 until MY_LIST_TAB_COUNT).map { tab ->
        checkCancelled()
        val sourceEntries = if (tab == MY_LIST_ALL_TAB_INDEX) {
            combinedLibrary?.entries ?: return@map null
        } else {
            sourceIndex.entriesByTab[tab]
        }
        val selectedEntries = when {
            tab == MY_LIST_ALL_TAB_INDEX -> sourceEntries
            filters.query.isNotBlank() && tab != MY_LIST_BANGUMI_TAB_INDEX -> sourceIndex.allEntries
            else -> sourceEntries
        }
        val (visible, grouped) = processed.getOrPut(selectedEntries) {
            val filtered = filterMyListEntries(
                entries = selectedEntries,
                searchQuery = filters.query,
                selectedStatusFilterId = filters.status,
                selectedTypeFilterId = filters.type,
                selectedFavoriteFilterId = filters.favorite,
                selectedScoreFilterId = filters.score,
                selectedYearFilterId = filters.year,
                selectedExtraFilterId = filters.extra,
                fuzzySearchEnabled = filters.fuzzySearchEnabled,
                checkCancelled = checkCancelled
            )
            checkCancelled()
            val sorted = applySort(filtered, filters.sort)
            sorted to groupMyListEntriesByStatus(sorted)
        }
        MyListPagePresentation(sourceEntries, selectedEntries, visible, grouped)
    }
    return MyListPresentation(sourceIndex, combinedLibrary, filters, pages)
}
