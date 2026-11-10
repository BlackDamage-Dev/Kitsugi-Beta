package com.kitsugi.animelist.ui.screens.explore.mobile

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.SuccessResult
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import com.kitsugi.animelist.ui.components.LocalShimmerBrush
import com.kitsugi.animelist.ui.components.LocalShimmerOffset
import com.kitsugi.animelist.ui.components.rememberKitsugiShimmerBrush
import com.kitsugi.animelist.ui.screens.explore.ExplorePlatform
import com.kitsugi.animelist.ui.theme.KitsugiDarkColors
import com.kitsugi.animelist.ui.theme.KitsugiLightColors
import com.kitsugi.animelist.ui.theme.LocalBlurAdultMedia
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.LocalKitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiOnAccent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * İŞ 2 — Mobil Keşfet görsel bileşenleri (V2) cihaz testleri.
 *
 * AĞSIZDIR: Tüm görsel istekleri singleton'a takılan sabit interceptor ile
 * kısa devre yapılır (fixture bitmap döner, hiçbir fetch/decode çalışmaz);
 * fixture URL'leri ayrıca çözümlenemez `.invalid` alanındadır. Shimmer saati
 * sabit State ile dondurulur → deterministik kareler.
 *
 * NOT: Bu testler JDK/Android SDK/cihaz gerektirir; teslim ortamında bunlar
 * yoksa ÇALIŞTIRILAMAZ ve raporda "bloklu" olarak işaretlenir (geçti denmez).
 */
@RunWith(AndroidJUnit4::class)
class MobileExploreVisualsTest {

    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var previousLoader: ImageLoader? = null

    @OptIn(DelicateCoilApi::class)
    @Before
    fun installOfflineImageLoader() {
        previousLoader = SingletonImageLoader.get(context)
        val fixtureBitmap = Bitmap.createBitmap(4, 6, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(android.graphics.Color.DKGRAY) }
        val offline = ImageLoader.Builder(context)
            .components {
                add(
                    Interceptor { chain ->
                        SuccessResult(
                            image = fixtureBitmap.asImage(),
                            request = chain.request,
                            dataSource = DataSource.MEMORY,
                        )
                    },
                )
            }
            .build()
        SingletonImageLoader.setUnsafe(offline)
    }

    @OptIn(DelicateCoilApi::class)
    @After
    fun restoreImageLoader() {
        previousLoader?.let { SingletonImageLoader.setUnsafe(it) }
    }

    private fun str(resId: Int): String = context.getString(resId)

    private fun item(
        id: Int = 1,
        title: String = "Fixture $id",
        english: String? = "English $id",
        japanese: String? = "ネイティブ$id",
        image: String? = "https://fixture.invalid/poster_$id.jpg",
        backdrop: String? = null,
        genres: List<String> = listOf("Action"),
        year: Int? = 2024,
        score: Int? = 8,
        adult: Boolean = false,
    ) = JikanSearchResult(
        malId = id,
        title = title,
        subtitle = "",
        type = MediaType.TvShow,
        total = null,
        score = score,
        isAdult = adult,
        imageUrl = image,
        year = year,
        source = "tmdb",
        titleEnglish = english,
        titleJapanese = japanese,
        backdropUrl = backdrop,
        genres = genres,
    )

    private fun entry(score: Int? = 9) = MediaEntry(
        id = 501, title = "Entry", type = MediaType.TvShow,
        status = WatchStatus.Completed, score = score, progress = 3, total = 12,
    )

    /** Sabit konfigürasyon + tema + dondurulmuş shimmer ile izole mobil kurulum. */
    private fun setMobile(
        widthDp: Int = 384,
        heightDp: Int = 683,
        landscape: Boolean = false,
        fontScale: Float = 1f,
        light: Boolean = false,
        accent: Color = Color(0xFFC8F4EF),
        content: @Composable () -> Unit,
    ) {
        val config = Configuration().apply {
            screenWidthDp = widthDp
            screenHeightDp = heightDp
            orientation = if (landscape) Configuration.ORIENTATION_LANDSCAPE
            else Configuration.ORIENTATION_PORTRAIT
        }
        compose.setContent {
            val scheme = if (light) KitsugiLightColors else KitsugiDarkColors
            val baseDensity = LocalDensity.current
            MaterialTheme {
                CompositionLocalProvider(
                    LocalConfiguration provides config,
                    LocalDensity provides Density(baseDensity.density, fontScale),
                    LocalKitsugiColors provides scheme,
                    LocalKitsugiAccent provides accent,
                    LocalKitsugiOnAccent provides Color(0xFF0B1220),
                    LocalBlurAdultMedia provides false,
                    LocalShimmerBrush provides rememberKitsugiShimmerBrush(),
                    LocalShimmerOffset provides remember { mutableStateOf(0f) },
                ) {
                    // LocalConfiguration alone does not change Compose layout constraints.
                    // Actual size remains bounded by the test device's available window.
                    Box(Modifier.size(widthDp.dp, heightDp.dp).background(scheme.background)) { content() }
                }
            }
        }
    }

    @Composable
    private fun shelf(
        results: List<JikanSearchResult> = listOf(item(1), item(2), item(3)),
        isLoading: Boolean = false,
        source: ExplorePlatform = ExplorePlatform.TMDB,
        onSeeAll: (() -> Unit)? = {},
        alreadyInList: (JikanSearchResult) -> Boolean = { false },
        getMediaEntry: (JikanSearchResult) -> MediaEntry? = { null },
        onItemClick: (JikanSearchResult) -> Unit = {},
        onLongClick: (JikanSearchResult) -> Unit = {},
        titleLanguage: String = "ROMAJI",
        scoreFormat: String = "POINT_10",
        hideScores: Boolean = false,
        blurAdultMedia: Boolean = false,
    ) {
        MobileExploreShelf(
            title = "Trend",
            source = source,
            results = results,
            isLoading = isLoading,
            alreadyInList = alreadyInList,
            getMediaEntry = getMediaEntry,
            onItemClick = onItemClick,
            onLongClickItem = onLongClick,
            onSeeAllClick = onSeeAll,
            titleLanguage = titleLanguage,
            scoreFormat = scoreFormat,
            hideScores = hideScores,
            blurAdultMedia = blurAdultMedia,
        )
    }

    // ── HEADER ───────────────────────────────────────────────────────────────

    @Test
    fun headerShowsBrandLocalizedAllAndRealActionSlot() {
        var sourceClicks = 0
        var actionClicks = 0
        setMobile {
            MobileExploreHeader(
                selectedPlatform = ExplorePlatform.ALL,
                onSourceClick = { sourceClicks++ },
                actions = {
                    Text("E2E_ACTION", modifier = Modifier.clickable { actionClicks++ })
                },
            )
        }
        compose.onNodeWithText(str(R.string.mobile_explore_brand)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.kesfet)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.mobile_explore_source_all)).assertIsDisplayed()
        compose.onNodeWithTag(MobileExploreTestTags.SOURCE_PILL).performClick()
        compose.runOnIdle { assertEquals(1, sourceClicks) }
        compose.onNodeWithText("E2E_ACTION").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, actionClicks) }
    }

    @Test
    fun headerShowsSelectedSourceShortName() {
        setMobile {
            MobileExploreHeader(ExplorePlatform.TMDB, {}, {})
        }
        compose.onNodeWithText("TMDB").assertIsDisplayed()
    }

    // ── TABS ─────────────────────────────────────────────────────────────────

    @Test
    fun tabsRenderAllFourLabelsAndReportEveryIndex() {
        var selected by mutableStateOf(0)
        val reported = mutableListOf<Int>()
        setMobile {
            MobileExploreTabs(selected, { reported += it; selected = it })
        }
        listOf(
            R.string.mobile_explore_tab_series,
            R.string.mobile_explore_tab_movies,
            R.string.mobile_explore_tab_anime,
            R.string.mobile_explore_tab_manga,
        ).forEach { compose.onNodeWithText(str(it)).assertIsDisplayed() }
        for (index in 0..3) {
            compose.onNodeWithTag(MobileExploreTestTags.TAB_PREFIX + index).performClick()
            compose.onNodeWithTag(MobileExploreTestTags.TAB_PREFIX + index).assertIsSelected()
        }
        compose.runOnIdle { assertEquals(listOf(0, 1, 2, 3), reported) }
    }

    @Test
    fun tabsInvalidIndexDoesNotCrashOrSelect() {
        setMobile { MobileExploreTabs(99, {}) }
        compose.onNodeWithTag(MobileExploreTestTags.TAB_PREFIX + 0).assertIsNotSelected()
        compose.onNodeWithTag(MobileExploreTestTags.TAB_PREFIX + 3).assertIsDisplayed()
    }

    @Test
    fun tabsNegativeIndexDoesNotCrash() {
        setMobile { MobileExploreTabs(-5, {}) }
        compose.onNodeWithTag(MobileExploreTestTags.TAB_PREFIX + 1).assertIsNotSelected()
    }

    // ── HERO ─────────────────────────────────────────────────────────────────

    @Test
    fun heroEmptyShowsPlaceholderAndDisabledButton() {
        var clicks = 0
        setMobile { MobileExploreHero(null, false, false, { clicks++ }) }
        compose.onNodeWithText(str(R.string.mobile_explore_hero_caption)).assertIsDisplayed()
        compose.onNodeWithTag(MobileExploreTestTags.HERO_CTA).assertIsNotEnabled()
        compose.onNodeWithTag(MobileExploreTestTags.HERO).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, clicks) }
    }

    @Test
    fun heroLoadingShowsShimmerAndDisabledButton() {
        var clicks = 0
        setMobile { MobileExploreHero(item(backdrop = "https://fixture.invalid/wide.jpg"), true, false, { clicks++ }) }
        compose.onNodeWithTag(MobileExploreTestTags.HERO_LOADING).assertIsDisplayed()
        compose.onNodeWithTag(MobileExploreTestTags.HERO_CTA).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, clicks) }
    }

    @Test
    fun heroFilledInceleButtonCallsItem() {
        val hero = item(id = 7, backdrop = "https://fixture.invalid/wide_7.jpg")
        var opened: JikanSearchResult? = null
        setMobile { MobileExploreHero(hero, false, false, { opened = it }) }
        compose.onNodeWithText(str(R.string.mobile_explore_hero_cta)).assertIsDisplayed()
        compose.onNodeWithTag(MobileExploreTestTags.HERO_CTA).performClick()
        compose.runOnIdle { assertEquals(hero, opened) }
    }

    @Test
    fun heroBodyClickCallsSameItem() {
        val hero = item(id = 8)
        var opened: JikanSearchResult? = null
        setMobile { MobileExploreHero(hero, false, false, { opened = it }) }
        compose.onNodeWithContentDescription(hero.title).assertExists()
        compose.onNodeWithTag(MobileExploreTestTags.HERO).performClick()
        compose.runOnIdle { assertEquals(hero, opened) }
    }

    @Test
    fun heroAdultBlurStillRendersAndClicks() {
        val hero = item(id = 9, adult = true)
        var opened: JikanSearchResult? = null
        setMobile { MobileExploreHero(hero, false, true, { opened = it }) }
        compose.onNodeWithTag(MobileExploreTestTags.HERO).assertIsDisplayed()
        compose.onNodeWithTag(MobileExploreTestTags.HERO_CTA).performClick()
        compose.runOnIdle { assertEquals(hero, opened) }
    }

    @Test
    fun heroKeepsApprovedWideAspectRatio() {
        setMobile { MobileExploreHero(item(), false, false, {}) }
        val bounds = compose.onNodeWithTag(MobileExploreTestTags.HERO).fetchSemanticsNode().boundsInRoot
        val ratio = bounds.width / bounds.height
        assertTrue("hero ratio=$ratio", ratio in 2.2f..2.4f)
    }

    // ── SHELF başlık/kaynak/Tümünü gör ───────────────────────────────────────

    @Test
    fun shelfShowsTitleSourceAndSeeAllCallback() {
        var seeAll = 0
        setMobile { shelf(onSeeAll = { seeAll++ }) }
        compose.onNodeWithText("Trend").assertIsDisplayed()
        compose.onNodeWithText("TMDB").assertIsDisplayed()
        compose.onNodeWithText(str(R.string.mobile_explore_see_all)).assertIsDisplayed()
        compose.onNodeWithTag(MobileExploreTestTags.SEE_ALL).performClick()
        compose.runOnIdle { assertEquals(1, seeAll) }
    }

    @Test
    fun shelfSeeAllNullIsHidden() {
        setMobile { shelf(onSeeAll = null) }
        compose.onNodeWithTag(MobileExploreTestTags.SEE_ALL).assertDoesNotExist()
    }

    @Test
    fun shelfAllSourceShowsLocalizedAllWithoutCrash() {
        setMobile { shelf(source = ExplorePlatform.ALL) }
        compose.onNodeWithText(str(R.string.mobile_explore_source_all)).assertIsDisplayed()
    }

    // ── SHELF kart etkileşimleri ─────────────────────────────────────────────

    @Test
    fun cardClickAndLongClickDeliverTheSameResult() {
        val items = listOf(item(11), item(12))
        var clicked: JikanSearchResult? = null
        var longed: JikanSearchResult? = null
        setMobile { shelf(results = items, onItemClick = { clicked = it }, onLongClick = { longed = it }) }
        compose.onAllNodesWithTag(MobileExploreTestTags.CARD)[0].performClick()
        compose.runOnIdle { assertEquals(items[0], clicked) }
        compose.onAllNodesWithTag(MobileExploreTestTags.CARD)[1].performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(items[1], longed) }
    }

    @Test
    fun shelfLoadingEmptyShowsShimmer() {
        setMobile { shelf(results = emptyList(), isLoading = true) }
        compose.onNodeWithTag(MobileExploreTestTags.SHELF_LOADING).assertIsDisplayed()
        compose.onAllNodesWithTag(MobileExploreTestTags.CARD).assertCountEquals(0)
    }

    @Test
    fun shelfStaleDataSurvivesWhileLoading() {
        setMobile { shelf(isLoading = true) }
        compose.onAllNodesWithTag(MobileExploreTestTags.CARD).assertCountEquals(3)
        compose.onNodeWithTag(MobileExploreTestTags.SHELF_LOADING).assertDoesNotExist()
    }

    @Test
    fun shelfSwipeSnapsFirstVisibleCardToLeadingContentPadding() {
        setMobile(widthDp = 320) { shelf(results = (1..20).map { item(it, image = null) }) }
        val list = compose.onNodeWithTag(MobileExploreTestTags.SHELF_LIST)
        list.performTouchInput { swipeLeft() }
        compose.waitForIdle()
        val listBounds = list.fetchSemanticsNode().boundsInRoot
        val first = compose.onAllNodesWithTag(MobileExploreTestTags.CARD)
            .fetchSemanticsNodes().minByOrNull { it.boundsInRoot.left }
        assertTrue("No visible card after swipe", first != null)
        val expectedStart = listBounds.left + 13f * compose.density.density
        val actualStart = first!!.boundsInRoot.left
        assertTrue("card=$actualStart expected=$expectedStart", abs(actualStart - expectedStart) <= 2f * compose.density.density)
    }

    @Test
    fun posterGeometryMatchesReferenceAtNormalDensity() {
        setMobile(widthDp = 384) { shelf(results = listOf(item(101), item(102))) }
        val cards = compose.onAllNodesWithTag(MobileExploreTestTags.CARD).fetchSemanticsNodes()
        assertEquals(2, cards.size)
        val scale = compose.density.density
        val first = cards[0].boundsInRoot
        val second = cards[1].boundsInRoot
        assertTrue("width=${first.width / scale}", abs(first.width / scale - 84f) < 1.5f)
        assertTrue("height=${first.height / scale}", abs(first.height / scale - 135f) < 1.5f)
        assertTrue("gap=${(second.left - first.right) / scale}", abs((second.left - first.right) / scale - 7f) < 1.5f)
    }

    @Test
    fun shelfScrollSurvivesTranslatedTitleChange() {
        var title by mutableStateOf("Trend")
        val items = (1..12).map { item(it, image = null) }
        setMobile {
            MobileExploreShelf(
                title = title, source = ExplorePlatform.TMDB, results = items,
                isLoading = false, alreadyInList = { false }, getMediaEntry = { null },
                onItemClick = {}, onLongClickItem = {}, onSeeAllClick = null,
                titleLanguage = "ROMAJI", scoreFormat = "POINT_10", hideScores = true,
                blurAdultMedia = false,
            )
        }
        compose.onNodeWithTag(MobileExploreTestTags.SHELF_LIST).performScrollToIndex(8)
        compose.onNodeWithContentDescription(items[8].title).assertIsDisplayed()
        compose.runOnIdle { title = "Trending" }
        compose.onNodeWithContentDescription(items[8].title).assertIsDisplayed()
    }

    @Test
    fun shelfEmptyNotLoadingLeavesPolicyToParent() {
        setMobile { shelf(results = emptyList(), isLoading = false) }
        compose.onNodeWithTag(MobileExploreTestTags.SHELF).assertDoesNotExist()
    }

    // ── Kart içerik kuralları ────────────────────────────────────────────────

    @Test
    fun missingArtworkShowsVisibleTitleFallback() {
        setMobile { shelf(results = listOf(item(id = 20, title = "Görünür Başlık", image = null))) }
        compose.onNodeWithText("Görünür Başlık").assertIsDisplayed()
    }

    @Test
    fun missingGenreAndYearSkipSeparatorEntirely() {
        setMobile { shelf(results = listOf(item(id = 21, genres = emptyList(), year = null))) }
        compose.onNodeWithText("•", substring = true).assertDoesNotExist()
    }

    @Test
    fun yearOnlyMetaHasNoDanglingSeparator() {
        setMobile { shelf(results = listOf(item(id = 22, genres = emptyList(), year = 2023))) }
        compose.onNodeWithText("2023").assertIsDisplayed()
        compose.onNodeWithText("•", substring = true).assertDoesNotExist()
    }

    @Test
    fun genreAndYearMetaKeepsRealYear() {
        setMobile { shelf(results = listOf(item(id = 23, year = 2025))) }
        compose.onNodeWithText("2025", substring = true).assertIsDisplayed()
    }

    private fun scoreDescription(text: String): String = context.getString(R.string.mobile_explore_score_cd, text)

    @Test
    fun inListStatusIsAccessibleWithoutAVisualBadge() {
        setMobile { shelf(results = listOf(item(24)), alreadyInList = { true }, hideScores = true) }
        compose.onNodeWithTag(MobileExploreTestTags.CARD_STATUS).assertDoesNotExist()
        compose.onNodeWithTag(MobileExploreTestTags.CARD).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, str(R.string.mobile_explore_in_list)),
        )
    }

    @Test
    fun mediaEntryStatusIsAccessibleWithoutAVisualBadge() {
        setMobile { shelf(results = listOf(item(25)), getMediaEntry = { entry() }, hideScores = true) }
        compose.onNodeWithTag(MobileExploreTestTags.CARD_STATUS).assertDoesNotExist()
        compose.onNodeWithTag(MobileExploreTestTags.CARD).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, str(WatchStatus.Completed.labelRes)),
        )
    }

    @Test
    fun scoreIsAccessibleButNotDrawnOverPoster() {
        setMobile { shelf(results = listOf(item(26, score = 8))) }
        compose.onNodeWithTag(MobileExploreTestTags.CARD_SCORE).assertDoesNotExist()
        compose.onNodeWithText("8/10", substring = true).assertDoesNotExist()
        compose.onNodeWithTag(MobileExploreTestTags.CARD).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, scoreDescription("8/10")),
        )
    }

    @Test
    fun providerHundredPointScoreWithStarPreferenceDoesNotDrawStars() {
        setMobile { shelf(results = listOf(item(81, score = 85)), scoreFormat = "POINT_5") }
        compose.onNodeWithTag(MobileExploreTestTags.CARD_SCORE).assertDoesNotExist()
        compose.onNodeWithText("★", substring = true).assertDoesNotExist()
        compose.onNodeWithTag(MobileExploreTestTags.CARD).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, scoreDescription("9/10")),
        )
    }

    @Test
    fun hideScoresRemovesScoreFromAccessibilityToo() {
        setMobile { shelf(results = listOf(item(27, score = 8)), hideScores = true) }
        compose.onNodeWithTag(MobileExploreTestTags.CARD_SCORE).assertDoesNotExist()
        compose.onNodeWithTag(MobileExploreTestTags.CARD).assert(
            SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription),
        )
    }

    @Test
    fun point100FormatIsRespectedInAccessibility() {
        setMobile { shelf(results = listOf(item(28, score = 8)), scoreFormat = "POINT_100") }
        compose.onNodeWithTag(MobileExploreTestTags.CARD).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, scoreDescription("80")),
        )
    }

    @Test
    fun entryScoreTakesPriorityInAccessibility() {
        setMobile { shelf(results = listOf(item(29, score = 5)), getMediaEntry = { entry(score = 9) }) }
        compose.onNodeWithTag(MobileExploreTestTags.CARD).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                str(WatchStatus.Completed.labelRes) + ", " + scoreDescription("9/10"),
            ),
        )
    }

    @Test
    fun titleLanguageSwitchesVisibleFallbackTitle() {
        var language by mutableStateOf("ENGLISH")
        setMobile {
            shelf(
                results = listOf(item(id = 30, title = "Romaji Ad", english = "English Name", japanese = "日本語題", image = null)),
                titleLanguage = language,
            )
        }
        compose.onNodeWithText("English Name").assertIsDisplayed()
        compose.runOnIdle { language = "NATIVE" }
        compose.onNodeWithText("日本語題").assertIsDisplayed()
    }

    @Test
    fun adultBlurCardStillClicks() {
        val adult = item(id = 31, adult = true)
        var clicked: JikanSearchResult? = null
        setMobile { shelf(results = listOf(adult), blurAdultMedia = true, onItemClick = { clicked = it }) }
        compose.onAllNodesWithTag(MobileExploreTestTags.CARD)[0].performClick()
        compose.runOnIdle { assertEquals(adult, clicked) }
    }

    // ── Tema / ekran boyutu / erişilebilirlik ────────────────────────────────

    @Test
    fun lightThemeWithDifferentAccentRenders() {
        setMobile(light = true, accent = Color(0xFFFF66CC)) {
            Column {
                MobileExploreHeader(ExplorePlatform.ALL, {}, {})
                MobileExploreTabs(0, {})
                MobileExploreHero(item(), false, false, {})
            }
        }
        compose.onNodeWithText(str(R.string.kesfet)).assertIsDisplayed()
        compose.onNodeWithTag(MobileExploreTestTags.TAB_PREFIX + 0).assertIsSelected()
        compose.onNodeWithTag(MobileExploreTestTags.HERO).assertIsDisplayed()
    }

    @Test
    fun darkDefaultThemeRenders() {
        setMobile {
            Column {
                MobileExploreTabs(2, {})
                shelf()
            }
        }
        compose.onNodeWithTag(MobileExploreTestTags.TAB_PREFIX + 2).assertIsSelected()
        compose.onNodeWithText("Trend").assertIsDisplayed()
    }

    private fun setFullExplore(widthDp: Int, heightDp: Int, landscape: Boolean = false, fontScale: Float = 1f) {
        setMobile(widthDp = widthDp, heightDp = heightDp, landscape = landscape, fontScale = fontScale) {
            Column {
                MobileExploreHeader(ExplorePlatform.ALL, {}, {})
                MobileExploreTabs(0, {})
                MobileExploreHero(item(backdrop = "https://fixture.invalid/wide.jpg"), false, false, {})
                shelf(results = (1..6).map { item(it) })
            }
        }
    }

    @Test
    fun narrow320KeepsTabsReachable() {
        setFullExplore(320, 640)
        compose.onNodeWithTag(MobileExploreTestTags.HERO).assertIsDisplayed()
        compose.onNodeWithTag(MobileExploreTestTags.TAB_PREFIX + 3).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun width360RendersAllTabs() {
        setFullExplore(360, 740)
        compose.onNodeWithTag(MobileExploreTestTags.TAB_PREFIX + 3).assertIsDisplayed()
        compose.onNodeWithTag(MobileExploreTestTags.HERO).assertIsDisplayed()
    }

    @Test
    fun width384ShowsFourCardsAndPartOfFifth() {
        setFullExplore(384, 683)
        compose.onAllNodesWithTag(MobileExploreTestTags.CARD)[3].assertIsDisplayed()
    }

    @Test
    fun tabletWidthRendersWithoutCrash() {
        setFullExplore(600, 960)
        compose.onNodeWithTag(MobileExploreTestTags.HERO).assertIsDisplayed()
        compose.onNodeWithTag(MobileExploreTestTags.SEE_ALL).assertIsDisplayed()
    }

    @Test
    fun landscapeKeepsPortraitPosters() {
        setFullExplore(844, 390, landscape = true)
        val bounds = compose.onAllNodesWithTag(MobileExploreTestTags.CARD)[0].fetchSemanticsNode().boundsInRoot
        assertTrue("poster dikey kalmalı: $bounds", bounds.height > bounds.width)
    }

    @Test
    fun largeFontTabsRemainReachableAndClickable() {
        var selected = -1
        setMobile(widthDp = 320, heightDp = 640, fontScale = 1.5f) {
            MobileExploreTabs(0, { selected = it })
        }
        compose.onNodeWithTag(MobileExploreTestTags.TAB_PREFIX + 3).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(3, selected) }
    }

    @Test
    fun pillAndCtaMeet48dpTouchTargets() {
        setMobile {
            Column {
                MobileExploreHeader(ExplorePlatform.ALL, {}, {})
                MobileExploreHero(item(), false, false, {})
            }
        }
        val density = compose.density.density
        val minPx = 48f * density
        val pill = compose.onNodeWithTag(MobileExploreTestTags.SOURCE_PILL).fetchSemanticsNode().boundsInRoot
        val cta = compose.onNodeWithTag(MobileExploreTestTags.HERO_CTA).fetchSemanticsNode().boundsInRoot
        assertTrue("pill height=${pill.height} min=$minPx", pill.height >= minPx - 0.5f)
        assertTrue("cta height=${cta.height} min=$minPx", cta.height >= minPx - 0.5f)
        assertTrue("pill width=${pill.width}", pill.width >= minPx - 0.5f)
        assertTrue("cta width=${cta.width}", cta.width >= minPx - 0.5f)
    }
}
