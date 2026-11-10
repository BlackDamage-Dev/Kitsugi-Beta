package com.kitsugi.animelist.ui.screens.mylist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.kitsugi.animelist.model.MediaEntry

/** UI adapter only: all full-list work lives in the cancellable Default-dispatcher pipeline. */
@Composable
internal fun rememberMyListSearchState(
    entries: List<MediaEntry>,
    scope: MyListSearchScope,
    request: MyListSearchRequest,
    viewModel: MyListViewModel
): MyListSearchUiState {
    val latestRequest by rememberUpdatedState(request)
    val entriesKey = MyListSearchEntriesKey(entries)
    val prepared by produceState<PreparedMyListSearch?>(
        initialValue = viewModel.cachedPreparedSearch,
        entriesKey,
        scope.sortId,
        scope.localeTag
    ) {
        val computed = prepareMyListSearch(
            entries, scope.sortId, viewModel.cachedPreparedSearch, localeTag = scope.localeTag
        )
        viewModel.cachedPreparedSearch = computed
        value = computed
    }
    val snapshot by produceState<MyListSearchUiCache?>(
        initialValue = viewModel.cachedSearchResult,
        prepared,
        entriesKey,
        scope
    ) {
        val ready = prepared ?: return@produceState
        // A changed source/sort can still be preparing. Never search the previous tab's
        // index or publish its entries under the new tab's label.
        if (ready.sourceEntries !== entries || ready.sortId != scope.sortId ||
            ready.localeTag != scope.localeTag
        ) return@produceState
        snapshotFlow { latestRequest }.searchMyList(
            ready.index,
            initialQuery = value?.result?.request?.query
        ).collect { result ->
            val computed = MyListSearchUiCache(scope, ready.sourceEntries, result)
            viewModel.cachedSearchResult = computed
            value = computed
        }
    }
    return myListSearchUiState(snapshot, entries, scope)
}
