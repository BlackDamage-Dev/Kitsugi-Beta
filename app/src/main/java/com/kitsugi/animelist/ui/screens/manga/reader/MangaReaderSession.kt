package com.kitsugi.animelist.ui.screens.manga.reader

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kitsugi.animelist.data.manga.*
import com.kitsugi.animelist.ui.screens.manga.MangaReaderViewModel
import com.kitsugi.animelist.ui.screens.manga.components.readerSpreadCount
import com.kitsugi.animelist.ui.screens.manga.components.readerSpreadForPage
import com.kitsugi.animelist.ui.screens.manga.components.readerSpreadLast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Navigation shared by touch, keyboard, TV, page slider and chapter chooser. */
class MangaReaderSession(
    private val viewModel: MangaReaderViewModel,
    private val scope: CoroutineScope,
    val pager: PagerState,
    val webtoon: MangaWebtoonState,
) {
    var autoScroll by mutableStateOf(false)
    var resetZoomSignal by mutableIntStateOf(0)
    internal var restoredMode by mutableStateOf<ReadingMode?>(null)
    internal var anchoredWidth by mutableIntStateOf(0)
    internal var anchor = WebtoonLayout.Position(0)
    private var navigation: Job? = null
    private val state get() = viewModel.uiState.value
    internal val useSpreads: Boolean
        get() = state.doublePage && (state.readingMode == ReadingMode.LeftToRight || state.readingMode == ReadingMode.RightToLeft)
    internal val coverAlone: Boolean
        get() = useSpreads && state.coverAlone
    val isReady: Boolean get() = restoredMode == state.readingMode && !state.isLoadingPages && state.settingsLoaded
    val background: Color get() = when (state.webtoon.background) {
        "White" -> Color.White
        "DarkGray" -> Color(0xFF202024)
        else -> Color.Black
    }

    fun stop() { autoScroll = false }

    fun advance(direction: Int, repeat: Boolean = false) {
        stop()
        if (!isReady) return
        navigation?.cancel()
        navigation = scope.launch {
            if (state.readingMode == ReadingMode.Webtoon) {
                // Never skip the unread remainder of a tall image or auto-cross a chapter boundary.
                webtoon.advance(direction, state.webtoon.scrollPercent, animate = !repeat)
            } else {
                val target = pager.currentPage + direction
                when {
                    target in 0 until pager.pageCount -> pager.animateScrollToPage(target)
                    target >= pager.pageCount -> viewModel.goToNextChapter()
                    target < 0 -> viewModel.goToPreviousChapter()
                }
            }
        }
    }

    fun seek(pageIndex: Int) {
        stop()
        if (!isReady) return
        val pages = viewModel.pages.value
        if (pages.isEmpty()) return
        val target = pageIndex.coerceIn(pages.indices)
        navigation?.cancel()
        navigation = scope.launch {
            viewModel.loader.loadPage(pages[target])
            if (state.readingMode == ReadingMode.Webtoon) webtoon.scrollToPage(target)
            else pager.scrollToPage(readerSpreadForPage(target, useSpreads, coverAlone))
        }
    }

    fun lastPage() {
        stop()
        if (!isReady) return
        if (state.readingMode == ReadingMode.Webtoon) {
            navigation?.cancel()
            navigation = scope.launch {
                viewModel.pages.value.lastOrNull()?.let(viewModel.loader::loadPage)
                webtoon.listState.scrollToItem(webtoon.layout.tiles.size)
            }
        } else seek((viewModel.pages.value.size - 1).coerceAtLeast(0))
    }

    fun resetZoom() { webtoon.resetZoom(); resetZoomSignal++ }
}

@Composable
fun rememberMangaReaderSession(
    viewModel: MangaReaderViewModel,
    ui: MangaReaderViewModel.UiState,
    pages: List<MangaPage>,
    overlayOpen: Boolean,
): MangaReaderSession {
    val scope = rememberCoroutineScope()
    val useSpreads = ui.doublePage && (ui.readingMode == ReadingMode.LeftToRight || ui.readingMode == ReadingMode.RightToLeft)
    val coverAlone = useSpreads && ui.coverAlone
    val count by rememberUpdatedState(readerSpreadCount(pages.size, useSpreads, coverAlone))
    val session = key(ui.currentChapter.url) {
        val pager = rememberPagerState { count }
        val list = rememberLazyListState()
        remember(viewModel) { MangaReaderSession(viewModel, scope, pager, MangaWebtoonState(list)) }
    }
    val images = pages.map { it.imageInfo }
    val layout = remember(pages, images) { WebtoonLayout(images) }
    SideEffect { session.webtoon.layout = layout }

    // Guard BOTH observers during initial restore, chapter changes and mode transitions. An idle
    // hidden pager must never overwrite webtoon progress with its stale page zero.
    LaunchedEffect(session, ui.readingMode, useSpreads, coverAlone, ui.isLoadingPages, ui.settingsLoaded) {
        if (ui.isLoadingPages || !ui.settingsLoaded || pages.isEmpty()) return@LaunchedEffect
        val page = ui.currentPageIndex.coerceIn(pages.indices)
        val fraction = viewModel.currentPageOffset
        session.restoredMode = null
        session.stop()
        viewModel.loader.loadPage(pages[page])
        if (ui.readingMode == ReadingMode.Webtoon) {
            snapshotFlow { session.webtoon.contentWidthPx > 0 && session.webtoon.layout.images.size == pages.size }.first { it }
            session.webtoon.scrollToPage(page)
            if (fraction > 0f) {
                snapshotFlow { pages[page].imageInfo != null || pages[page].status == MangaPageStatus.Error }.first { it }
                // Header changes can recompose the layout on the next frame.
                snapshotFlow { session.webtoon.layout.images.getOrNull(page) == pages[page].imageInfo }.first { it }
                session.webtoon.scrollToPage(page, fraction)
            }
            session.anchoredWidth = session.webtoon.contentWidthPx
            session.anchor = WebtoonLayout.Position(page, fraction)
        } else session.pager.scrollToPage(readerSpreadForPage(page, useSpreads, coverAlone))
        session.restoredMode = ui.readingMode
    }

    LaunchedEffect(session, ui.readingMode, pages) {
        if (ui.readingMode == ReadingMode.Webtoon) return@LaunchedEffect
        snapshotFlow { session.isReady to session.pager.settledPage }
            .distinctUntilChanged().collect { (ready, page) ->
                if (ready && pages.isNotEmpty()) {
                    val actualPage = readerSpreadLast(page, pages.size, session.useSpreads, session.coverAlone).coerceIn(pages.indices)
                    viewModel.onPageChanged(ui.currentChapter.url, actualPage, pages.size)
                    viewModel.loader.onPageChanged(actualPage)
                }
            }
    }

    // Keep the same source-image point through rotation and side-padding changes.
    LaunchedEffect(session, session.webtoon.contentWidthPx) {
        val width = session.webtoon.contentWidthPx
        if (ui.readingMode == ReadingMode.Webtoon && session.isReady && width > 0 && width != session.anchoredWidth) {
            val position = session.anchor
            session.stop()
            session.restoredMode = null
            session.webtoon.scrollToPage(position.pageIndex, position.fraction)
            session.anchoredWidth = width
            session.restoredMode = ui.readingMode
        }
    }

    LaunchedEffect(session, ui.readingMode, pages) {
        if (ui.readingMode != ReadingMode.Webtoon) return@LaunchedEffect
        snapshotFlow {
            val ready = session.isReady && session.webtoon.contentWidthPx == session.anchoredWidth && session.webtoon.isLayoutSettled()
            val position = session.webtoon.position()
            val complete = pages.lastOrNull()?.status == MangaPageStatus.Ready && session.webtoon.lastImageBottomVisible()
            Triple(ready, position, complete)
        }.distinctUntilChanged().collect { (ready, position, complete) ->
            if (ready) {
                session.anchor = position
                viewModel.onPageChanged(ui.currentChapter.url, position.pageIndex, pages.size, position.fraction, complete)
            }
        }
    }
    LaunchedEffect(session, ui.readingMode, pages) {
        if (ui.readingMode != ReadingMode.Webtoon) return@LaunchedEffect
        snapshotFlow {
            val visible = session.webtoon.listState.layoutInfo.visibleItemsInfo
                .mapNotNull { session.webtoon.layout.tiles.getOrNull(it.index)?.pageIndex }
            Triple(session.isReady, visible.minOrNull(), visible.maxOrNull())
        }.distinctUntilChanged().collect { (ready, first, last) ->
            if (ready && first != null && last != null) viewModel.loader.onPageChanged(first, last)
        }
    }
    LaunchedEffect(session) {
        session.webtoon.listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) session.stop() }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(session, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                session.stop()
                viewModel.flushProgress()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); session.stop(); viewModel.flushProgress() }
    }
    LaunchedEffect(overlayOpen) { if (overlayOpen) session.stop() }
    val density = LocalDensity.current.density
    LaunchedEffect(session, session.autoScroll, ui.readingMode, ui.webtoon.autoScrollDpPerSecond) {
        if (!session.autoScroll || ui.readingMode != ReadingMode.Webtoon || !session.isReady || overlayOpen) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (session.autoScroll && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            val now = withFrameNanos { it }
            val seconds = ((now - previous) / 1_000_000_000f).coerceIn(0f, .05f)
            previous = now
            if (!session.webtoon.listState.canScrollForward) { session.stop(); break }
            val visible = session.webtoon.listState.layoutInfo.visibleItemsInfo
            // Wait at an unloaded strip instead of auto-skipping unseen content during slow downloads.
            val waiting = visible.any { item ->
                session.webtoon.layout.tiles.getOrNull(item.index)?.let { pages.getOrNull(it.pageIndex)?.status != MangaPageStatus.Ready } == true
            }
            if (!waiting && !session.webtoon.isTransforming) {
                session.webtoon.listState.scrollBy(ui.webtoon.autoScrollDpPerSecond * density * seconds / session.webtoon.zoom)
            }
        }
        session.stop()
    }
    return session
}
