package com.kitsugi.animelist.ui.screens.mylist

import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MyListSearchPipelineTest {
    private val entries = listOf(
        entry(1, "Naruto", 9, WatchStatus.Watching),
        entry(2, "Naruto Shippuden", 7, WatchStatus.Completed),
        entry(3, "Re:Zero", null, WatchStatus.Planned)
    )

    @Test
    fun rapidTypingComputesOnlyTheLastQueryAndRunsOffTheCollectorThread() = runBlocking {
        val dispatcher = RecordingDispatcher()
        val collectorThread = Thread.currentThread()
        val index = MyListSearchIndex.build(entries)
        val results = withTimeout(5_000) {
            flowOf(request("r"), request("re"), request("rezero"))
                .searchMyList(index, debounceMillis = 30, dispatcher = dispatcher).toList()
        }
        assertEquals(listOf("rezero"), results.map { it.request.query })
        assertEquals(listOf(3), results.single().entries.map { it.id })
        assertEquals(1, dispatcher.executions.get())
        assertTrue(dispatcher.threads.all { it !== collectorThread })
    }

    @Test
    fun clearingCancelsPendingTypingAndDoesNotWaitForTheDebounce() = runBlocking {
        val index = MyListSearchIndex.build(entries)
        val results = withTimeout(3_000) {
            flowOf(request("missing"), request(""))
                .searchMyList(index, debounceMillis = 60_000).toList()
        }
        assertEquals(1, results.size)
        assertEquals("", results.single().request.query)
        assertEquals(entries, results.single().entries)
    }

    @Test
    fun changedFiltersUseTheLatestRequestAndPublishGroupsAtomically() = runBlocking {
        val firstResult = CompletableDeferred<Unit>()
        val index = MyListSearchIndex.build(entries)
        val results = withTimeout(5_000) {
            flow {
                emit(request("naruto"))
                firstResult.await()
                emit(request("naruto").copy(score = "high"))
            }.searchMyList(index, debounceMillis = 10)
                .onEach { firstResult.complete(Unit) }.toList()
        }
        assertEquals(listOf(1, 2), results.first().entries.map { it.id })
        assertEquals(listOf(1), results.last().entries.map { it.id })
        results.forEach { result ->
            assertEquals(result.entries.toSet(), result.groupedEntries.flatMap { it.second }.toSet())
            assertTrue(result.groupedEntries.all { (status, rows) -> rows.all { it.status == status } })
        }
    }

    @Test
    fun aSupersededDispatchedSearchCannotPublishStaleResults() = runBlocking {
        val queue = Channel<Runnable>(Channel.UNLIMITED)
        val dispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                check(queue.trySend(block).isSuccess)
            }
        }
        val requests = MutableStateFlow(request("naruto"))
        val observedNewRequest = CompletableDeferred<Unit>()
        val results = Channel<MyListSearchResult>(Channel.UNLIMITED)
        val index = MyListSearchIndex.build(entries)
        val collection = launch {
            requests.onEach {
                if (it.query == "rezero") observedNewRequest.complete(Unit)
            }.searchMyList(index, debounceMillis = 0, dispatcher = dispatcher)
                .collect { results.send(it) }
        }
        try {
            withTimeout(5_000) {
                val obsolete = queue.receive()
                requests.value = request("rezero")
                // Wait until transformLatest actually observes/cancels the old request.
                observedNewRequest.await()
                obsolete.run()
                queue.receive().run()
                val result = results.receive()
                assertEquals("rezero", result.request.query)
                assertEquals(listOf(3), result.entries.map { it.id })
                assertTrue(results.tryReceive().isFailure)
            }
        } finally {
            collection.cancel()
            // Cancellation may dispatch a continuation through the test dispatcher.
            while (true) (queue.tryReceive().getOrNull() ?: break).run()
            collection.join()
        }
    }

    @Test
    fun newInputStopsAnAlreadyRunningCpuScanBeforeScanningTheWholeLibrary() = runBlocking {
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val gateEnabled = java.util.concurrent.atomic.AtomicBoolean(false)
        val pass = AtomicInteger()
        val visits = java.util.concurrent.ConcurrentHashMap<Int, AtomicInteger>()
        val rows = List(2_603) { entry(it, "Library title $it", null, WatchStatus.Planned) }
        val tracked = object : AbstractList<MediaEntry>() {
            override val size: Int get() = rows.size
            override fun get(index: Int): MediaEntry {
                if (gateEnabled.get()) {
                    if (index == 0) pass.incrementAndGet()
                    val currentPass = pass.get()
                    visits.computeIfAbsent(currentPass) { AtomicInteger() }.incrementAndGet()
                    if (currentPass == 1 && index == 40) {
                        entered.countDown()
                        check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    }
                }
                return rows[index]
            }
        }
        val index = MyListSearchIndex.build(tracked)
        gateEnabled.set(true)
        val requests = MutableStateFlow(request("obsolete"))
        val observedNewRequest = CompletableDeferred<Unit>()
        val results = Channel<MyListSearchResult>(Channel.UNLIMITED)
        val collection = launch {
            requests.onEach {
                if (it.query == "2602") observedNewRequest.complete(Unit)
            }.searchMyList(index, debounceMillis = 0).collect { results.send(it) }
        }
        try {
            withTimeout(10_000) {
                val started = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    entered.await(5, java.util.concurrent.TimeUnit.SECONDS)
                }
                assertTrue(started)
                requests.value = request("2602")
                observedNewRequest.await()
                release.countDown()
                val result = results.receive()
                assertEquals("2602", result.request.query)
                assertEquals(listOf(2_602), result.entries.map { it.id })
                assertTrue("Obsolete scan must stop at the next cancellation checkpoint",
                    visits.getValue(1).get() <= 65)
                assertTrue(results.tryReceive().isFailure)
            }
        } finally {
            release.countDown()
            collection.cancel()
            collection.join()
        }
    }

    @Test
    fun libraryUpdatesAndDetailReturnDoNotDebounceAnAlreadyAppliedQuery() = runBlocking {
        val index = MyListSearchIndex.build(entries)
        val results = withTimeout(3_000) {
            flowOf(request("naruto")).searchMyList(
                index, debounceMillis = 60_000, initialQuery = "naruto"
            ).toList()
        }
        assertEquals(listOf(1, 2), results.single().entries.map { it.id })
    }

    @Test
    fun anEmptyLoadingSnapshotCannotClobberSavedScrollWhenRowsArrive() {
        val scope = MyListSearchScope(1, "newest", showAdultContent = true)
        val loadingRows = emptyList<MediaEntry>()
        val emptyResult = MyListSearchResult(request(""), loadingRows, emptyList())
        val cached = MyListSearchUiCache(scope, loadingRows, emptyResult)
        val loading = myListSearchUiState(cached, entries, scope)
        assertSame(emptyResult, loading.result)
        assertFalse(loading.contentReady)

        val readyResult = MyListSearchResult(request(""), entries, groupMyListEntriesByStatus(entries))
        val ready = myListSearchUiState(MyListSearchUiCache(scope, entries, readyResult), entries, scope)
        assertTrue(ready.contentReady)
        assertSame(readyResult, ready.result)
        assertFalse(myListSearchUiState(cached, entries.map { it.copy() }, scope).contentReady)
    }

    @Test
    fun cachedResultsAreNotShownUnderAnotherSourceAdultOrLanguageScope() {
        val scope = MyListSearchScope(1, "newest", showAdultContent = true, localeTag = "tr-TR")
        val result = MyListSearchResult(request(""), entries, groupMyListEntriesByStatus(entries))
        val cached = MyListSearchUiCache(scope, entries, result)
        for (changedScope in listOf(scope.copy(tabIndex = 6), scope.copy(showAdultContent = false),
            scope.copy(localeTag = "en-US"))) {
            val state = myListSearchUiState(cached, entries, changedScope)
            assertSame(MyListSearchResult.EMPTY, state.result)
            assertFalse(state.contentReady)
        }
        val sorting = myListSearchUiState(cached, entries, scope.copy(sortId = "title"))
        assertSame(result, sorting.result)
        assertFalse(sorting.contentReady)
    }

    @Test
    fun effectKeysRestartPreparationForEqualButNewSourceSnapshots() {
        val equalSnapshot = entries.map { it.copy() }
        assertEquals(entries, equalSnapshot)
        val key = MyListSearchEntriesKey(entries)
        assertEquals(key, MyListSearchEntriesKey(entries))
        assertEquals(key.hashCode(), MyListSearchEntriesKey(entries).hashCode())
        assertFalse(key == MyListSearchEntriesKey(equalSnapshot))
    }

    @Test
    fun unchangedLibraryReusesPreparedDocumentsButChangedRowsAndSortInvalidateThem() = runBlocking {
        val first = prepareMyListSearch(entries, "newest")
        val equalSnapshot = prepareMyListSearch(entries.map { it.copy() }, "newest", cached = first)
        assertSame(first.index, equalSnapshot.index)

        val updated = entries.map { if (it.id == 1) it.copy(titleEnglish = "New Alias") else it }
        val changed = prepareMyListSearch(updated, "newest", cached = first)
        val sorted = prepareMyListSearch(entries, "title", cached = first)
        assertNotSame(first.index, changed.index)
        assertNotSame(first.index, sorted.index)
        val changedLocale = prepareMyListSearch(entries, "newest", cached = first, localeTag = "en-US")
        assertNotSame(first.index, changedLocale.index)
        val result = flowOf(request("new alias")).searchMyList(changed.index, 0).toList().single()
        assertEquals(listOf(1), result.entries.map { it.id })
    }

    @Test
    fun sortingOnceBeforeSearchKeepsEverySortContractsRelativeOrder() = runBlocking {
        val sorts = listOf("newest", "oldest", "title", "title_desc", "score", "score_asc",
            "progress", "progress_asc", "favorites", "start_date_desc", "start_date_asc",
            "end_date_desc", "end_date_asc", "year_desc", "year_asc", "updated_desc",
            "updated_asc", "repeat_desc", "repeat_asc", "priority_desc", "priority_asc")
        for (sort in sorts) {
            val prepared = prepareMyListSearch(entries, sort)
            val result = flowOf(request("naruto")).searchMyList(prepared.index, 0).toList().single()
            assertEquals(sort, applySort(entries.filter { it.title.startsWith("Naruto") }, sort), result.entries)
        }
    }

    @Test
    fun changingFuzzyPreferenceInvalidatesTheSearchWithoutRebuildingDocuments() = runBlocking {
        val firstResult = CompletableDeferred<Unit>()
        val index = MyListSearchIndex.build(entries)
        val results = flow {
            emit(request("narxto").copy(fuzzySearchEnabled = true))
            firstResult.await()
            emit(request("narxto"))
        }.searchMyList(index, 0).onEach { firstResult.complete(Unit) }.toList()
        assertFalse(results.first().entries.isEmpty())
        assertTrue(results.last().entries.isEmpty())
    }

    @Test
    fun searchesA2603EntryLibraryWithoutChangingTheInputOrLosingSortOrder() = runBlocking {
        val large = List(2_602) { entry(it, "Library title $it", null, WatchStatus.Planned) } +
            entry(5_000, "Re:Zero", 9, WatchStatus.Watching)
        val prepared = prepareMyListSearch(large, "newest")
        val result = flowOf(request("rezero")).searchMyList(prepared.index, 0).toList().single()
        assertEquals(listOf(5_000), result.entries.map { it.id })
        assertEquals(2_603, large.size)
        assertEquals(0, large.first().id)
    }

    private fun request(query: String) = MyListSearchRequest(query, fuzzySearchEnabled = false)
    private fun entry(id: Int, title: String, score: Int?, status: WatchStatus) = MediaEntry(
        id = id, title = title, type = MediaType.Anime, status = status,
        score = score, progress = 0, total = null
    )

    private class RecordingDispatcher : CoroutineDispatcher() {
        val executions = AtomicInteger()
        val threads = java.util.concurrent.CopyOnWriteArrayList<Thread>()
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            Dispatchers.Default.dispatch(context) {
                executions.incrementAndGet()
                threads.add(Thread.currentThread())
                block.run()
            }
        }
    }
}
