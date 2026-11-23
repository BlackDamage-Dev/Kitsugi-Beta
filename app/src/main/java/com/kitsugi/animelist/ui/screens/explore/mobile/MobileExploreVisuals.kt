package com.kitsugi.animelist.ui.screens.explore.mobile

import android.content.res.Configuration
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.ui.components.KitsugiNsfwImage
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.ui.components.KitsugiShimmerBlock
import com.kitsugi.animelist.ui.components.LocalShimmerBrush
import com.kitsugi.animelist.ui.components.rememberKitsugiShimmerBrush
import com.kitsugi.animelist.ui.icons.KitsugiIcons
import com.kitsugi.animelist.ui.navigation.NuvioCircularActionButton
import com.kitsugi.animelist.ui.navigation.NuvioGlassPillSurface
import com.kitsugi.animelist.ui.screens.explore.ExplorePlatform
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults
import com.kitsugi.animelist.ui.utils.kitsugiLazyKey
import com.kitsugi.animelist.ui.utils.localizedLabel
import com.kitsugi.animelist.utils.PreferenceHelpers
import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayTitle
import com.kitsugi.animelist.utils.toTurkishGenre
import kotlinx.coroutines.delay

/**
 * NuvioMobile-inspired Mobile/Tablet Discover (Keşfet) Visual Components.
 *
 * Adapts Kitsugi's 7-source Explore experience (AniList, MAL, TMDB, Simkl, Kitsu, Shikimori,
 * Bangumi + ALL) and 4 media tabs (Diziler / Filmler / Anime / Manga) to NuvioMobile's visual
 * language in both Portrait and Landscape orientations:
 *  - [MobileExploreHeader] : Frosted glass floating top bar (Source Pill + Actions, flanking
 *    the TopCenter floating nav bar in landscape, unified top chrome in portrait)
 *  - [MobileExploreTabs]   : Frosted glass category filter pills (Diziler / Filmler / Anime / Manga)
 *  - [MobileExploreHero]   : Full-bleed edge-to-edge swipeable Hero Carousel with auto-scroll,
 *    cinematic gradient fade, metadata + source/score badge, CTA pill & expanding pagination dots
 *  - [MobileExploreShelf]  : Clean Nuvio shelf header with circular `>` See All pill, 16:9 landscape
 *    Continue Watching cards (`SIMKL_CONTINUE`), and 2:3 rounded portrait poster cards.
 */

private const val NuvioPosterAspect = 0.675f
private const val NuvioLandscapeCardAspect = 16f / 9f
private val NuvioPosterRadius = 12.dp
private val NuvioHeroBottomRadius = 28.dp
private val NuvioPosterShape = RoundedCornerShape(NuvioPosterRadius)
private val NuvioHeroShape = RoundedCornerShape(
    topStart = 0.dp,
    topEnd = 0.dp,
    bottomStart = NuvioHeroBottomRadius,
    bottomEnd = NuvioHeroBottomRadius,
)
private const val HeroAutoScrollIntervalMs = 8_000L

/** Instrumented testlerin kullandığı kararlı test etiketleri. */
internal object MobileExploreTestTags {
    const val HEADER = "mobile_explore_header"
    const val SOURCE_PILL = "mobile_explore_source_pill"
    const val TABS = "mobile_explore_tabs"
    const val TAB_PREFIX = "mobile_explore_tab_"
    const val HERO = "mobile_explore_hero"
    const val HERO_CTA = "mobile_explore_hero_cta"
    const val HERO_LOADING = "mobile_explore_hero_loading"
    const val SHELF = "mobile_explore_shelf"
    const val SHELF_LOADING = "mobile_explore_shelf_loading"
    const val SHELF_LIST = "mobile_explore_shelf_list"
    const val SEE_ALL = "mobile_explore_see_all"
    const val CARD = "mobile_explore_card"
    const val CARD_SCORE = "mobile_explore_card_score"
    const val CARD_STATUS = "mobile_explore_card_status"
}

/** Kaynak görünen adı: ALL yerelleştirilmiş "Tümü"/"All", diğerleri kısa marka adı. */
@Composable
private fun exploreSourceLabel(source: ExplorePlatform): String =
    if (source == ExplorePlatform.ALL) stringResource(R.string.mobile_explore_source_all)
    else source.shortName

@Composable
private fun exploreSourceIdLabel(sourceId: String): String = when (sourceId.lowercase()) {
    "anilist" -> ExplorePlatform.AniList.shortName
    "mal", "jikan" -> ExplorePlatform.MAL.shortName
    "tmdb" -> ExplorePlatform.TMDB.shortName
    "simkl" -> ExplorePlatform.SIMKL.shortName
    "kitsu" -> ExplorePlatform.KITSU.shortName
    "shikimori" -> ExplorePlatform.SHIKIMORI.shortName
    "bangumi" -> ExplorePlatform.BANGUMI.shortName
    else -> sourceId.uppercase()
}

@Composable
private fun shelfHorizontalPadding(): Dp {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return if (widthDp >= 600) 24.dp else 16.dp
}

@Composable
private fun posterCardWidth(): Dp {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return when {
        widthDp >= 840 -> 136.dp
        widthDp >= 600 -> 126.dp
        else -> 112.dp
    }
}

@Composable
private fun continueCardWidth(): Dp {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return when {
        widthDp >= 840 -> 272.dp
        widthDp >= 600 -> 248.dp
        else -> 224.dp
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 1) HEADER — Nuvio tarzı yüzen cam üst çubuk (Dikey & Yatay uyumlu)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun MobileExploreHeader(
    selectedPlatform: ExplorePlatform,
    onSourceClick: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    isLandscapeTopBar: Boolean = false,
    overHero: Boolean = true,
) {
    val accent = LocalKitsugiAccent.current
    val config = LocalConfiguration.current
    val isLightTheme = KitsugiColors.Background.luminance() > 0.5f
    val useLightTextOverHero = overHero || !isLightTheme
    val titleColor = if (useLightTextOverHero) Color.White else KitsugiColors.TextPrimary
    val horizontalPad = if (isLandscapeTopBar || config.screenWidthDp >= 600) 20.dp else 14.dp

    if (isLandscapeTopBar) {
        // Yatay / Tablet düzeni:
        // Sol: Cam Kaynak Seçici (7 kaynak + Tümü) (+ geniş ekranda KITSUGI markası)
        // Orta: AppRoot içindeki TopCenter NuvioFloatingNavBar için açık alan
        // Sağ: Kitsugi aksiyon butonları (Geçmiş, İndirme, Rastgele, Bildirim)
        Row(
            modifier = modifier
                .fillMaxWidth()
                .testTag(MobileExploreTestTags.HEADER)
                .padding(horizontal = horizontalPad, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NuvioExploreSourcePill(
                    selectedPlatform = selectedPlatform,
                    onClick = onSourceClick,
                    overHero = overHero,
                )
                if (config.screenWidthDp >= 840) {
                    Column {
                        Text(
                            text = stringResource(R.string.mobile_explore_brand),
                            color = accent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.6.sp,
                            maxLines = 1,
                        )
                        Text(
                            text = stringResource(R.string.kesfet),
                            color = titleColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                content = actions,
            )
        }
    } else if (config.screenWidthDp < 480) {
        // Kompakt dikey telefon (320–479dp):
        // Satır 1: KITSUGI markası + sağda dairesel cam aksiyonlar
        // Satır 2: Keşfet başlığı + sağda cam kaynak seçici (uzun kaynak adları bile asla kesilmez)
        Column(
            modifier = modifier
                .fillMaxWidth()
                .testTag(MobileExploreTestTags.HEADER)
                .padding(horizontal = horizontalPad, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.mobile_explore_brand),
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                    maxLines = 1,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    content = actions,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.kesfet),
                    color = titleColor,
                    fontSize = 22.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                NuvioExploreSourcePill(
                    selectedPlatform = selectedPlatform,
                    onClick = onSourceClick,
                    overHero = overHero,
                )
            }
        }
    } else {
        // Geniş dikey / katlanabilir tablet (>= 480dp): tek satır ferah düzen
        Row(
            modifier = modifier
                .fillMaxWidth()
                .testTag(MobileExploreTestTags.HEADER)
                .padding(horizontal = horizontalPad, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.mobile_explore_brand),
                    color = accent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.kesfet),
                    color = titleColor,
                    fontSize = 20.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            NuvioExploreSourcePill(
                selectedPlatform = selectedPlatform,
                onClick = onSourceClick,
                overHero = overHero,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                content = actions,
            )
        }
    }
}

@Composable
private fun NuvioExploreSourcePill(
    selectedPlatform: ExplorePlatform,
    onClick: () -> Unit,
    overHero: Boolean,
) {
    val accent = LocalKitsugiAccent.current
    val isLightTheme = KitsugiColors.Background.luminance() > 0.5f
    val useDarkGlass = overHero || !isLightTheme
    val label = exploreSourceLabel(selectedPlatform)

    Box(
        modifier = Modifier
            .testTag(MobileExploreTestTags.SOURCE_PILL)
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.explore_source_platform_select),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        NuvioGlassPillSurface(
            modifier = Modifier.height(38.dp),
            shape = CircleShape,
            overHero = overHero,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (selectedPlatform != ExplorePlatform.ALL) {
                    KitsugiPlatformLogo(
                        platformId = selectedPlatform.name,
                        size = 16.dp,
                    )
                }
                Text(
                    text = label,
                    color = if (useDarkGlass) Color.White else KitsugiColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = KitsugiIcons.ChevronDown,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2) TABS — Nuvio cam kategori çipleri (Diziler / Filmler / Anime / Manga)
// ─────────────────────────────────────────────────────────────────────────────

private val MobileTabLabels = listOf(
    R.string.mobile_explore_tab_series,
    R.string.mobile_explore_tab_movies,
    R.string.mobile_explore_tab_anime,
    R.string.mobile_explore_tab_manga,
)

@Composable
internal fun MobileExploreTabs(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    overHero: Boolean = true,
) {
    val accentBrush = KitsugiColors.AccentBrush
    val onAccent = KitsugiColors.OnAccent
    val isLightTheme = KitsugiColors.Background.luminance() > 0.5f
    val useDarkGlass = overHero || !isLightTheme
    val horizontalPad = shelfHorizontalPadding()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(MobileExploreTestTags.TABS)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = horizontalPad, vertical = if (isLandscape) 2.dp else 3.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MobileTabLabels.forEachIndexed { index, labelRes ->
            val selected = index == selectedIndex
            val label = stringResource(labelRes)
            val unselectedBg = if (useDarkGlass) {
                Color(0xFF18191D).copy(alpha = 0.64f)
            } else {
                KitsugiColors.Surface.copy(alpha = 0.90f)
            }
            val unselectedBorder = if (useDarkGlass) {
                Color.White.copy(alpha = 0.18f)
            } else {
                KitsugiColors.Border
            }
            val unselectedText = if (useDarkGlass) {
                Color.White.copy(alpha = 0.88f)
            } else {
                KitsugiColors.TextSecondary
            }

            Box(
                modifier = Modifier
                    .testTag(MobileExploreTestTags.TAB_PREFIX + index)
                    .heightIn(min = if (isLandscape) 30.dp else 32.dp)
                    .clip(CircleShape)
                    .then(
                        if (selected) {
                            Modifier.background(brush = accentBrush, shape = CircleShape)
                        } else {
                            Modifier
                                .background(unselectedBg, CircleShape)
                                .border(0.85.dp, unselectedBorder, CircleShape)
                        }
                    )
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { onTabSelected(index) },
                    )
                    .padding(
                        horizontal = if (isLandscape) 13.dp else 15.dp,
                        vertical = if (isLandscape) 5.dp else 6.dp,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (selected) onAccent else unselectedText,
                    fontSize = if (isLandscape) 12.sp else 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3) HERO — NuvioMobile tam genişlikli (edge-to-edge) Hero Carousel
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MobileExploreHero(
    item: JikanSearchResult?,
    isLoading: Boolean,
    blurAdultMedia: Boolean,
    onItemClick: (JikanSearchResult) -> Unit,
    modifier: Modifier = Modifier,
    heroItems: List<JikanSearchResult> = remember(item) { listOfNotNull(item) },
    titleLanguage: String = "ROMAJI",
    scoreFormat: String = "POINT_10",
    hideScores: Boolean = false,
) {
    val config = LocalConfiguration.current
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isWideLayout = isLandscape || config.screenWidthDp >= 600
    val isCompactLandscape = isLandscape && config.screenHeightDp < 480
    val heroHeight = remember(config.screenHeightDp, isWideLayout, isCompactLandscape) {
        when {
            isCompactLandscape -> (config.screenHeightDp * 0.76f).dp.coerceIn(280.dp, 330.dp)
            isWideLayout -> (config.screenHeightDp * 0.60f).dp.coerceIn(340.dp, 440.dp)
            else -> (config.screenHeightDp * 0.52f).dp.coerceIn(356.dp, 450.dp)
        }
    }

    val effectiveItems = remember(item, heroItems) {
        if (heroItems.isNotEmpty()) heroItems
        else listOfNotNull(item)
    }
    val hasActiveItems = effectiveItems.isNotEmpty() && !isLoading
    val backgroundColor = KitsugiColors.Background

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { effectiveItems.size.coerceAtLeast(1) },
    )
    val firstItemKey = effectiveItems.firstOrNull()?.kitsugiLazyKey("hero_head", 0)
    LaunchedEffect(firstItemKey) {
        if (pagerState.currentPage != 0 && effectiveItems.isNotEmpty()) {
            runCatching { pagerState.scrollToPage(0) }
        }
    }

    // Auto-scroll carousel like NuvioMobile's HomeHeroSection (8s interval)
    LaunchedEffect(pagerState, effectiveItems.size, isLoading) {
        if (effectiveItems.size <= 1 || isLoading) return@LaunchedEffect
        while (true) {
            delay(HeroAutoScrollIntervalMs)
            if (!pagerState.isScrollInProgress) {
                val nextPage = (pagerState.currentPage + 1) % effectiveItems.size
                runCatching { pagerState.animateScrollToPage(nextPage) }
            }
        }
    }

    val activeItem = effectiveItems.getOrNull(pagerState.currentPage)
        ?: effectiveItems.firstOrNull()
        ?: item
    val activeTitle = remember(activeItem, titleLanguage) {
        activeItem?.getDisplayTitle(titleLanguage)?.takeIf { it.isNotBlank() } ?: activeItem?.title
    }
    val fallbackCaption = stringResource(R.string.mobile_explore_hero_caption)
    val heroContentDescription = activeTitle ?: fallbackCaption

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
            .testTag(MobileExploreTestTags.HERO)
            .clip(NuvioHeroShape)
            .background(KitsugiColors.SurfaceStrong)
            .semantics { contentDescription = heroContentDescription }
            .clickable(
                enabled = hasActiveItems && activeItem != null,
                role = Role.Button,
                onClick = { activeItem?.let(onItemClick) },
            ),
    ) {
        when {
            isLoading -> {
                KitsugiShimmerBlock(
                    brush = LocalShimmerBrush.current ?: rememberKitsugiShimmerBrush(),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(MobileExploreTestTags.HERO_LOADING),
                    cornerRadius = 0.dp,
                )
            }
            effectiveItems.isNotEmpty() -> {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    key = { idx ->
                        effectiveItems.getOrNull(idx)?.kitsugiLazyKey("hero_slide", idx)
                            ?: "hero_slide_empty_$idx"
                    },
                ) { pageIndex ->
                    val slide = effectiveItems.getOrNull(pageIndex) ?: return@HorizontalPager
                    var backdropFailed by remember(slide.backdropUrl) { mutableStateOf(false) }
                    var posterFailed by remember(slide.imageUrl) { mutableStateOf(false) }
                    val primaryBackdrop = slide.backdropUrl?.takeIf { it.isNotBlank() && !backdropFailed }
                    val fallbackPoster = slide.imageUrl?.takeIf { it.isNotBlank() && !posterFailed }
                    val artUrl = primaryBackdrop ?: fallbackPoster
                    if (artUrl != null) {
                        KitsugiNsfwImage(
                            model = artUrl,
                            contentDescription = null,
                            isAdult = slide.isAdult,
                            blurAdultMedia = blurAdultMedia,
                            onLoadingFailed = {
                                if (primaryBackdrop != null) backdropFailed = true
                                else posterFailed = true
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            KitsugiColors.SurfaceElevated,
                                            KitsugiColors.SurfaceStrong,
                                            backgroundColor,
                                        )
                                    )
                                )
                        )
                    }
                }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    KitsugiColors.SurfaceElevated,
                                    KitsugiColors.SurfaceStrong,
                                )
                            )
                        )
                )
            }
        }

        // Üst yüzen bar için okunabilirlik gölgesi (top scrim)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.58f),
                        0.26f to Color.Black.copy(alpha = 0.20f),
                        0.46f to Color.Transparent,
                    )
                )
        )

        // Alt sinematik geçiş:
        // Koyu/AMOLED temada NuvioMobile gibi arka plana yumuşakça erir;
        // Aydınlık temada beyaz başlık/rozetlerin her zaman yüksek kontrastlı kalması için
        // yuvarlatılmış hero kartı içinde koyu sinematik scrim uygular.
        val isLightTheme = backgroundColor.luminance() > 0.5f
        val bottomFadeColor = if (isLightTheme) Color(0xFF0C0E13) else backgroundColor
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.32f to Color.Transparent,
                        0.64f to bottomFadeColor.copy(alpha = if (isLightTheme) 0.58f else 0.65f),
                        0.86f to bottomFadeColor.copy(alpha = if (isLightTheme) 0.88f else 0.94f),
                        1f to bottomFadeColor.copy(alpha = if (isLightTheme) 0.95f else 1f),
                    )
                )
        )

        val contentHorizontalPad = if (isWideLayout) 24.dp else 18.dp
        val contentBottomPad = when {
            isCompactLandscape -> 12.dp
            isWideLayout -> 18.dp
            else -> 14.dp
        }
        val columnAlignment = if (isWideLayout) Alignment.Start else Alignment.CenterHorizontally
        val boxAlignment = if (isWideLayout) Alignment.BottomStart else Alignment.BottomCenter

        Column(
            modifier = Modifier
                .align(boxAlignment)
                .fillMaxWidth(if (isWideLayout) 0.74f else 1f)
                .padding(horizontal = contentHorizontalPad)
                .padding(bottom = contentBottomPad),
            horizontalAlignment = columnAlignment,
            verticalArrangement = Arrangement.spacedBy(if (isCompactLandscape) 5.dp else 7.dp),
        ) {
            // Başlık
            Text(
                text = activeTitle ?: fallbackCaption,
                color = Color.White,
                fontSize = when {
                    isCompactLandscape -> 20.sp
                    isWideLayout -> 27.sp
                    else -> 22.sp
                },
                lineHeight = when {
                    isCompactLandscape -> 23.sp
                    isWideLayout -> 31.sp
                    else -> 26.sp
                },
                fontWeight = FontWeight.Black,
                maxLines = if (isCompactLandscape) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (isWideLayout) TextAlign.Start else TextAlign.Center,
            )

            // Nuvio metadata satırı: Tür • Kategori • Yıl
            if (activeItem != null && !isLoading) {
                val typeLabel = activeItem.type.localizedLabel()
                val firstGenre = activeItem.genres.firstOrNull()?.takeIf { it.isNotBlank() }?.toTurkishGenre()
                val yearText = activeItem.year?.takeIf { it > 0 }?.toString()
                val metaParts = listOfNotNull(typeLabel, firstGenre, yearText)
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString("  •  "),
                        color = Color.White.copy(alpha = 0.84f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = if (isWideLayout) TextAlign.Start else TextAlign.Center,
                    )
                }
            }

            // Kaynak + Puan rozeti + Nuvio "İncele" aksiyon hapı
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (activeItem != null && !isLoading) {
                    val sourceBadge = exploreSourceIdLabel(activeItem.source)
                    val scoreLabel = if (!hideScores) {
                        val normalized = mobileExploreDisplayScore(activeItem.score)
                        if (normalized != null) {
                            PreferenceHelpers.formatScore(normalized, scoreFormat, false).takeIf { it.isNotBlank() }
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .border(0.85.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = sourceBadge,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        if (scoreLabel != null) {
                            Text(
                                text = "|",
                                color = Color.White.copy(alpha = 0.40f),
                                fontSize = 11.sp,
                            )
                            Icon(
                                imageVector = KitsugiIcons.Star,
                                contentDescription = null,
                                tint = KitsugiColors.AccentYellow,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = scoreLabel,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                }

                // Nuvio yüksek kontrastlı hap CTA butonu (min 48dp dokunma alanı)
                Box(
                    modifier = Modifier
                        .testTag(MobileExploreTestTags.HERO_CTA)
                        .heightIn(min = 48.dp)
                        .widthIn(min = 48.dp)
                        .clickable(
                            enabled = hasActiveItems && activeItem != null,
                            role = Role.Button,
                            onClick = { activeItem?.let(onItemClick) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    val ctaFill = if (hasActiveItems) KitsugiColors.AccentBrush else SolidColor(Color.White.copy(alpha = 0.20f))
                    val ctaTextColor = if (hasActiveItems) KitsugiColors.OnAccent else Color.White.copy(alpha = 0.55f)
                    Row(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(CircleShape)
                            .background(ctaFill)
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.mobile_explore_hero_cta),
                            color = ctaTextColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        Icon(
                            imageVector = KitsugiIcons.ChevronRight,
                            contentDescription = null,
                            tint = ctaTextColor,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }

            // Nuvio genişleyen sayfa göstergesi (pagination dots)
            if (effectiveItems.size > 1 && !isLoading) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    repeat(effectiveItems.size) { index ->
                        val isCurrent = index == pagerState.currentPage
                        val dotWidth by animateDpAsState(
                            targetValue = if (isCurrent) 24.dp else 7.dp,
                            animationSpec = tween(durationMillis = 240),
                            label = "hero_dot_width",
                        )
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(dotWidth)
                                .clip(CircleShape)
                                .background(
                                    if (isCurrent) Color.White else Color.White.copy(alpha = 0.36f)
                                )
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4) SHELF — Nuvio raf başlığı + dairesel `>` Tümünü gör + 16:9 / 2:3 kartlar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun MobileExploreShelf(
    title: String,
    source: ExplorePlatform,
    results: List<JikanSearchResult>,
    isLoading: Boolean,
    alreadyInList: (JikanSearchResult) -> Boolean,
    getMediaEntry: (JikanSearchResult) -> MediaEntry?,
    onItemClick: (JikanSearchResult) -> Unit,
    onLongClickItem: (JikanSearchResult) -> Unit,
    onSeeAllClick: (() -> Unit)?,
    titleLanguage: String,
    scoreFormat: String,
    hideScores: Boolean,
    blurAdultMedia: Boolean,
    modifier: Modifier = Modifier,
    isContinueWatching: Boolean = false,
) {
    if (results.isEmpty() && !isLoading) return
    val rowState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val snapFling = KitsugiScrollDefaults.rememberRowSnapFling(rowState)
    val horizontalPad = shelfHorizontalPadding()
    val cardSpacing = if (isContinueWatching) 12.dp else if (horizontalPad >= 24.dp) 14.dp else 11.dp
    val showSkeleton = results.isEmpty() && isLoading

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(MobileExploreTestTags.SHELF),
    ) {
        // Nuvio Shelf Header: Kompakt kalın başlık + kaynak rozeti + sağda dairesel `>` butonu
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPad)
                .padding(bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    color = KitsugiColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(KitsugiColors.SurfaceElevated)
                        .border(0.75.dp, KitsugiColors.Border.copy(alpha = 0.65f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = exploreSourceLabel(source),
                        color = KitsugiColors.TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }

            if (onSeeAllClick != null) {
                NuvioCircularActionButton(
                    icon = KitsugiIcons.ChevronRight,
                    contentDescription = stringResource(R.string.mobile_explore_see_all),
                    onClick = onSeeAllClick,
                    visualSize = 34.dp,
                    iconSize = 18.dp,
                    modifier = Modifier.testTag(MobileExploreTestTags.SEE_ALL),
                )
            }
        }

        if (showSkeleton) {
            val shimmer = LocalShimmerBrush.current ?: rememberKitsugiShimmerBrush()
            val placeholderWidth = if (isContinueWatching) continueCardWidth() else posterCardWidth()
            val placeholderAspect = if (isContinueWatching) NuvioLandscapeCardAspect else NuvioPosterAspect
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MobileExploreTestTags.SHELF_LOADING),
                contentPadding = PaddingValues(horizontal = horizontalPad),
                horizontalArrangement = Arrangement.spacedBy(cardSpacing),
                userScrollEnabled = false,
            ) {
                items(5, key = { "shimmer_$it" }) {
                    Column(modifier = Modifier.width(placeholderWidth)) {
                        KitsugiShimmerBlock(
                            brush = shimmer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(placeholderAspect),
                            cornerRadius = NuvioPosterRadius,
                        )
                        if (!isContinueWatching) {
                            Spacer(Modifier.height(7.dp))
                            KitsugiShimmerBlock(
                                brush = shimmer,
                                modifier = Modifier
                                    .fillMaxWidth(0.76f)
                                    .height(11.dp),
                                cornerRadius = 4.dp,
                            )
                        }
                    }
                }
            }
        } else {
            LazyRow(
                state = rowState,
                flingBehavior = snapFling,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MobileExploreTestTags.SHELF_LIST),
                contentPadding = PaddingValues(horizontal = horizontalPad),
                horizontalArrangement = Arrangement.spacedBy(cardSpacing),
            ) {
                itemsIndexed(
                    items = results,
                    key = { index, item -> item.kitsugiLazyKey("mobile_shelf", index) },
                    contentType = { _, _ -> if (isContinueWatching) "mobile_continue_card" else "mobile_poster_card" },
                ) { _, item ->
                    if (isContinueWatching) {
                        MobileExploreContinueCard(
                            item = item,
                            alreadyInList = alreadyInList(item),
                            mediaEntry = getMediaEntry(item),
                            titleLanguage = titleLanguage,
                            scoreFormat = scoreFormat,
                            hideScores = hideScores,
                            blurAdultMedia = blurAdultMedia,
                            onClick = { onItemClick(item) },
                            onLongClick = { onLongClickItem(item) },
                        )
                    } else {
                        MobileExplorePosterCard(
                            item = item,
                            alreadyInList = alreadyInList(item),
                            mediaEntry = getMediaEntry(item),
                            titleLanguage = titleLanguage,
                            scoreFormat = scoreFormat,
                            hideScores = hideScores,
                            blurAdultMedia = blurAdultMedia,
                            onClick = { onItemClick(item) },
                            onLongClick = { onLongClickItem(item) },
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5) CONTINUE WATCHING 16:9 KARTI — NuvioMobile HomeContinueWatchingSection
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MobileExploreContinueCard(
    item: JikanSearchResult,
    alreadyInList: Boolean,
    mediaEntry: MediaEntry?,
    titleLanguage: String,
    scoreFormat: String,
    hideScores: Boolean,
    blurAdultMedia: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val accent = LocalKitsugiAccent.current
    val cardWidth = continueCardWidth()
    val displayTitle = remember(item, titleLanguage) {
        item.getDisplayTitle(titleLanguage).ifBlank { item.title }
    }
    var backdropFailed by remember(item.backdropUrl) { mutableStateOf(false) }
    var posterFailed by remember(item.imageUrl) { mutableStateOf(false) }
    val primaryBackdrop = item.backdropUrl?.takeIf { it.isNotBlank() && !backdropFailed }
    val fallbackPoster = item.imageUrl?.takeIf { it.isNotBlank() && !posterFailed }
    val artUrl = primaryBackdrop ?: fallbackPoster
    val statusText = when {
        mediaEntry != null -> mediaEntry.status.localizedLabel()
        alreadyInList -> stringResource(R.string.mobile_explore_in_list)
        else -> null
    }
    val rawScore = if (hideScores) null else (mediaEntry?.score ?: item.score)
    val scoreText = remember(rawScore, scoreFormat, hideScores) {
        val normalized = mobileExploreDisplayScore(rawScore)
        if (normalized != null) PreferenceHelpers.formatScore(normalized, scoreFormat, hideScores).takeIf { it.isNotBlank() }
        else null
    }
    val scoreAccess = scoreText?.let { stringResource(R.string.mobile_explore_score_cd, it) }
    val stateText = listOfNotNull(statusText, scoreAccess).joinToString(", ")

    val progressFraction = remember(mediaEntry) {
        val total = mediaEntry?.total ?: 0
        val prog = mediaEntry?.progress ?: 0
        if (total > 0 && prog > 0) (prog.toFloat() / total.toFloat()).coerceIn(0.04f, 1f) else null
    }
    val badgeLabel = remember(mediaEntry, item.year) {
        val prog = mediaEntry?.progress ?: 0
        val total = mediaEntry?.total
        when {
            prog > 0 && total != null && total > 0 -> "$prog / $total"
            prog > 0 -> "#$prog"
            item.year != null && item.year > 0 -> item.year.toString()
            else -> null
        }
    }
    val subtitleLine = remember(item.genres, item.year, item.source) {
        val genre = item.genres.firstOrNull()?.takeIf { it.isNotBlank() }?.toTurkishGenre()
        val year = item.year?.takeIf { it > 0 }?.toString()
        listOfNotNull(genre, year).joinToString(" • ")
    }

    Box(
        modifier = Modifier
            .width(cardWidth)
            .aspectRatio(NuvioLandscapeCardAspect)
            .testTag(MobileExploreTestTags.CARD)
            .clip(NuvioPosterShape)
            .background(KitsugiColors.Surface)
            .border(0.75.dp, KitsugiColors.Border.copy(alpha = 0.55f), NuvioPosterShape)
            .semantics {
                contentDescription = displayTitle
                if (stateText.isNotEmpty()) stateDescription = stateText
            }
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        if (artUrl != null) {
            KitsugiNsfwImage(
                model = artUrl,
                contentDescription = null,
                isAdult = item.isAdult,
                blurAdultMedia = blurAdultMedia,
                onLoadingFailed = {
                    if (primaryBackdrop != null) backdropFailed = true
                    else posterFailed = true
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(KitsugiColors.SurfaceElevated, KitsugiColors.SurfaceStrong)
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = KitsugiIcons.PlayCircle,
                    contentDescription = null,
                    tint = KitsugiColors.TextMuted.copy(alpha = 0.55f),
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        // Alt okunabilirlik gradyanı
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Black.copy(alpha = 0.25f),
                        1f to Color.Black.copy(alpha = 0.90f),
                    )
                )
        )

        // Sağ üst köşe durum/bölüm hapı
        if (badgeLabel != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .border(0.75.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                Text(
                    text = badgeLabel,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }

        // Sol alt başlık & alt bilgi + alt ilerleme çubuğu
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = displayTitle,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitleLine.isNotEmpty()) {
                Text(
                    text = subtitleLine,
                    color = Color.White.copy(alpha = 0.76f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (progressFraction != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.28f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressFraction)
                            .clip(CircleShape)
                            .background(accent),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 6) POSTER KARTI — NuvioMobile temiz 2:3 poster + alt başlık & meta satırı
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MobileExplorePosterCard(
    item: JikanSearchResult,
    alreadyInList: Boolean,
    mediaEntry: MediaEntry?,
    titleLanguage: String,
    scoreFormat: String,
    hideScores: Boolean,
    blurAdultMedia: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val cardWidth = posterCardWidth()
    val displayTitle = remember(item, titleLanguage) {
        item.getDisplayTitle(titleLanguage).ifBlank { item.title }
    }
    val metaLine = remember(item.genres, item.year) {
        val genre = item.genres.firstOrNull()?.takeIf { it.isNotBlank() }?.toTurkishGenre()
        val year = item.year?.takeIf { it > 0 }?.toString()
        listOfNotNull(genre, year).joinToString(" • ")
    }
    val rawScore = if (hideScores) null else (mediaEntry?.score ?: item.score)
    val scoreText = remember(rawScore, scoreFormat, hideScores) {
        val normalized = mobileExploreDisplayScore(rawScore)
        if (normalized != null) PreferenceHelpers.formatScore(normalized, scoreFormat, hideScores).takeIf { it.isNotBlank() }
        else null
    }
    val statusText = when {
        mediaEntry != null -> mediaEntry.status.localizedLabel()
        alreadyInList -> stringResource(R.string.mobile_explore_in_list)
        else -> null
    }
    val scoreAccess = scoreText?.let { stringResource(R.string.mobile_explore_score_cd, it) }
    val stateText = listOfNotNull(statusText, scoreAccess).joinToString(", ")

    val artUrl = remember(item.imageUrl, item.backdropUrl) {
        item.imageUrl?.takeIf { it.isNotBlank() } ?: item.backdropUrl?.takeIf { it.isNotBlank() }
    }
    var artFailed by remember(artUrl) { mutableStateOf(false) }
    val showImage = artUrl != null && !artFailed

    Column(
        modifier = Modifier
            .width(cardWidth)
            .testTag(MobileExploreTestTags.CARD)
            .semantics {
                contentDescription = displayTitle
                if (stateText.isNotEmpty()) stateDescription = stateText
            }
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        // Temiz, engelsiz 2:3 Nuvio poster çerçevesi
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(NuvioPosterAspect)
                .clip(NuvioPosterShape)
                .background(KitsugiColors.Surface)
                .border(0.75.dp, KitsugiColors.Border.copy(alpha = 0.55f), NuvioPosterShape),
        ) {
            if (showImage) {
                KitsugiNsfwImage(
                    model = artUrl,
                    contentDescription = null,
                    isAdult = item.isAdult,
                    blurAdultMedia = blurAdultMedia,
                    onLoadingFailed = { artFailed = true },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(KitsugiColors.SurfaceElevated, KitsugiColors.SurfaceStrong)
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = KitsugiIcons.PlayCircle,
                        contentDescription = null,
                        tint = KitsugiColors.TextMuted.copy(alpha = 0.55f),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Poster altı temiz başlık (NuvioPosterCard tarzı)
        Text(
            text = displayTitle,
            color = KitsugiColors.TextPrimary,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (metaLine.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = metaLine,
                color = KitsugiColors.TextMuted,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
