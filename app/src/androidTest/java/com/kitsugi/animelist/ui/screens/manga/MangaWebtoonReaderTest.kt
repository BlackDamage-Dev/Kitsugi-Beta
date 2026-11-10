package com.kitsugi.animelist.ui.screens.manga

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kitsugi.animelist.data.manga.*
import com.kitsugi.animelist.data.manga.loader.MangaCache
import com.kitsugi.animelist.data.manga.loader.MangaImageTileDecoder
import com.kitsugi.animelist.data.manga.loader.MangaPageLoaderV2
import com.kitsugi.animelist.ui.screens.manga.components.MangaWebtoonReader
import com.kitsugi.animelist.ui.screens.manga.reader.MangaWebtoonState
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Device-only layout/gesture/decoder regressions. These are NOT exercised by the CLI harness. */
@RunWith(AndroidJUnit4::class)
class MangaWebtoonReaderTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = mutableListOf<File>()
    private var loader: MangaPageLoaderV2? = null
    private lateinit var state: MangaWebtoonState
    private lateinit var scope: CoroutineScope

    private fun image(index: Int, height: Int): MangaPage {
        val file = File.createTempFile("webtoon-test", ".png", context.cacheDir).also(files::add)
        val bitmap = Bitmap.createBitmap(100, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(if (index == 0) AndroidColor.RED else AndroidColor.BLUE)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return MangaPage(index, file.toURI().toString(), status = MangaPageStatus.Ready).apply {
            cachedImageFile = file
            imageInfo = MangaImageTileDecoder.readHeader(file)
            stream = { file.inputStream() }
        }
    }

    private fun show(pages: List<MangaPage>, heightDp: Int = 700) {
        val source = object : MangaSource {
            override val name = "Reader device fixture"
            override val baseUrl = "https://fixture.invalid"
            override val lang = "en"
            override suspend fun fetchPopularManga(page: Int) = error("No network in fixture")
            override suspend fun fetchSearchManga(page: Int, query: String) = error("No network in fixture")
            override suspend fun fetchMangaDetails(mangaUrl: String) = error("No network in fixture")
            override suspend fun fetchChapterList(mangaUrl: String) = emptyList<MangaChapter>()
            override suspend fun fetchPageList(chapter: MangaChapter) = pages
        }
        val pageLoader = MangaPageLoaderV2(context, source, MangaCache(context)).also { loader = it }
        state = MangaWebtoonState(LazyListState()).apply { layout = WebtoonLayout(pages.map { it.imageInfo }) }
        compose.setContent {
            scope = rememberCoroutineScope()
            MaterialTheme {
                Box(Modifier.width(320.dp).height(heightDp.dp)) {
                    MangaWebtoonReader(pages, state, pageLoader)
                }
            }
        }
        compose.waitForIdle()
    }

    @After fun cleanUp() { loader?.recycle(); files.forEach { it.delete() } }

    @Test fun adjacent_images_share_width_and_have_no_injected_gap() {
        show(listOf(image(0, 100), image(1, 100)))
        val first = compose.onNodeWithTag("webtoon_tile_0:0").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("webtoon_tile_1:0").fetchSemanticsNode().boundsInRoot
        assertEquals(first.width, second.width, .1f)
        assertEquals(first.bottom, second.top, .1f)
    }

    @Test fun a_long_image_has_only_one_scroll_owner_and_drag_moves_the_chapter() {
        show(listOf(image(0, 5000), image(1, 100)), heightDp = 500)
        compose.onAllNodes(hasScrollAction()).assertCountEquals(1)
        compose.onNodeWithTag("webtoon_strip").performTouchInput { swipeUp(durationMillis = 400) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(state.position().fraction > 0f)
            assertEquals(0, state.position().pageIndex)
            assertTrue(state.listState.firstVisibleItemIndex > 0 || state.listState.firstVisibleItemScrollOffset > 0)
        }
    }

    @Test fun the_footer_remains_reachable_when_the_whole_strip_is_zoomed() {
        show(listOf(image(0, 5000), image(1, 100)), heightDp = 500)
        compose.runOnIdle { state.zoomBy(2f) }
        compose.waitForIdle()
        compose.runOnIdle { scope.launch { state.listState.scrollToItem(state.layout.tiles.size) } }
        compose.waitForIdle()
        compose.onNodeWithText(context.getString(com.kitsugi.animelist.R.string.reader_chapter_end)).assertIsDisplayed()
        compose.runOnIdle { assertTrue(state.lastImageBottomVisible()) }
    }

    @Test fun native_region_decoder_reads_a_strip_not_a_full_tall_bitmap() = runBlocking {
        val page = image(0, 20000)
        val layout = WebtoonLayout(listOf(page.imageInfo))
        val decoder = MangaImageTileDecoder()
        val bitmap = decoder.decode(page.cachedImageFile!!, layout.tiles[120], 320)
        assertTrue(bitmap.width <= WebtoonLayout.MAX_DECODE_EDGE)
        assertTrue(bitmap.height <= WebtoonLayout.MAX_DECODE_EDGE)
        assertEquals(AndroidColor.RED, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
        assertTrue(bitmap.height < page.imageInfo!!.height)
        decoder.clear()
    }
}
