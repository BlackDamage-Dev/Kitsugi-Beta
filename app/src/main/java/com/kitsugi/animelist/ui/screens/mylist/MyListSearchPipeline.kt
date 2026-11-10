@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.kitsugi.animelist.ui.screens.mylist

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.withContext

internal const val MY_LIST_SEARCH_DEBOUNCE_MS = 180L

internal data class MyListSearchRequest(
    val query: String,
    val status: String = "all",
    val type: String = "all",
    val favorite: String = "all",
    val score: String = "all",
    val year: String = "all",
    val extra: String = "all",
    val fuzzySearchEnabled: Boolean = true
)

internal data class MyListSearchScope(
    val tabIndex: Int,
    val sortId: String,
    val showAdultContent: Boolean,
    val localeTag: String = ""
)

/**
 * Compose effect keys normally compare Lists structurally. Readiness below intentionally
 * compares snapshot references, so equal-but-new lists must still restart preparation.
 * Otherwise a source/tab change can leave the result collector permanently waiting for
 * an index whose source reference will never be updated. Also keeps key checks O(1).
 */
internal class MyListSearchEntriesKey(private val entries: List<MediaEntry>) {
    override fun equals(other: Any?): Boolean =
        other is MyListSearchEntriesKey && other.entries === entries

    override fun hashCode(): Int = System.identityHashCode(entries)
}

internal class PreparedMyListSearch(
    val sourceEntries: List<MediaEntry>,
    val sortId: String,
    val index: MyListSearchIndex,
    val localeTag: String
)

internal class MyListSearchResult(
    val request: MyListSearchRequest,
    val entries: List<MediaEntry>,
    val groupedEntries: List<Pair<WatchStatus, List<MediaEntry>>>
) {
    companion object {
        val EMPTY = MyListSearchResult(MyListSearchRequest(""), emptyList(), emptyList())
    }
}

internal class MyListSearchUiCache(
    val scope: MyListSearchScope,
    val sourceEntries: List<MediaEntry>,
    val result: MyListSearchResult
)

internal class MyListSearchUiState(val result: MyListSearchResult, val contentReady: Boolean) {
    companion object {
        val EMPTY = MyListSearchUiState(MyListSearchResult.EMPTY, contentReady = false)
    }
}

/** Keep drawing the previous result, but never save/restore scroll against loading rows. */
internal fun myListSearchUiState(
    cached: MyListSearchUiCache?,
    entries: List<MediaEntry>,
    scope: MyListSearchScope
): MyListSearchUiState {
    if (cached == null || cached.scope.tabIndex != scope.tabIndex ||
        cached.scope.showAdultContent != scope.showAdultContent || cached.scope.localeTag != scope.localeTag
    ) return MyListSearchUiState.EMPTY
    return MyListSearchUiState(
        result = cached.result,
        contentReady = cached.sourceEntries === entries && cached.scope == scope
    )
}

/** Sorting and document preparation depend on the library/sort, NEVER on each letter. */
internal suspend fun prepareMyListSearch(
    entries: List<MediaEntry>,
    sortId: String,
    cached: PreparedMyListSearch? = null,
    localeTag: String = "",
    dispatcher: CoroutineDispatcher = Dispatchers.Default
): PreparedMyListSearch = withContext(dispatcher) {
    val jobContext = currentCoroutineContext()
    jobContext.ensureActive()
    // Equality can traverse a large list, so even cache validation stays off Main.
    if (cached != null && cached.sortId == sortId && cached.localeTag == localeTag && cached.sourceEntries == entries) {
        return@withContext PreparedMyListSearch(entries, sortId, cached.index, localeTag)
    }
    val sorted = applySort(entries, sortId)
    val index = MyListSearchIndex.build(sorted) { jobContext.ensureActive() }
    PreparedMyListSearch(entries, sortId, index, localeTag)
}

/**
 * Raw input reaches the field immediately. Only result calculation is debounced. Unlike
 * debounce().mapLatest(), transformLatest cancels an in-flight CPU search as soon as the
 * NEXT letter arrives, not after its debounce expires. Empty queries/filter-only changes
 * have no typing delay. The last complete result remains on screen until its replacement.
 */
internal fun Flow<MyListSearchRequest>.searchMyList(
    index: MyListSearchIndex,
    debounceMillis: Long = MY_LIST_SEARCH_DEBOUNCE_MS,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    initialQuery: String? = null
): Flow<MyListSearchResult> = flow {
    var previousQuery: String? = initialQuery
    emitAll(this@searchMyList.transformLatest { request ->
        val queryChanged = request.query != previousQuery
        previousQuery = request.query
        if (request.query.isNotBlank() && queryChanged) delay(debounceMillis)
        val result = withContext(dispatcher) {
            val jobContext = currentCoroutineContext()
            val checkActive = { jobContext.ensureActive() }
            val entries = filterMyListEntries(
                entries = index.entries,
                searchQuery = request.query,
                selectedStatusFilterId = request.status,
                selectedTypeFilterId = request.type,
                selectedFavoriteFilterId = request.favorite,
                selectedScoreFilterId = request.score,
                selectedYearFilterId = request.year,
                selectedExtraFilterId = request.extra,
                // İsteğin kendi tercihi kullanılır: kullanıcı arayüzdeki "bulanık arama"
                // anahtarını değiştirdiğinde sonuç gerçekten değişmeli (global ayar okuması
                // bu alanı yok sayıyordu).
                fuzzySearchOverride = request.fuzzySearchEnabled,
                // İptal noktası: yeni tuş geldiğinde eski tarama tüm kütüphaneyi taramaz.
                checkActive = checkActive
            )
            val groups = groupMyListEntriesByStatus(entries)
            jobContext.ensureActive()
            MyListSearchResult(request, entries, groups)
        }
        emit(result)
    }.buffer(0))
}
