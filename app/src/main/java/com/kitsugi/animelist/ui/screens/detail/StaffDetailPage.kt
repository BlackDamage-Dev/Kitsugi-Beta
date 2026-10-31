@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.kitsugi.animelist.ui.screens.detail

import com.kitsugi.animelist.data.platform.PlatformErrorCode
import com.kitsugi.animelist.ui.components.PlatformErrorExplanationBlock
import com.kitsugi.animelist.ui.components.rememberDetailStickyHeaderVisible

import androidx.compose.runtime.CompositionLocalProvider
import com.kitsugi.animelist.ui.components.LocalKitsugiGalleryLoading
import com.kitsugi.animelist.ui.components.LocalKitsugiTranslationLoading

import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.kitsugi.animelist.ui.components.KitsugiPullToRefreshIndicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.rememberCoroutineScope
import com.kitsugi.animelist.data.remote.JikanApiClient
import com.kitsugi.animelist.data.remote.GalleryItem
import com.kitsugi.animelist.data.remote.galleryPersonLabel
import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.data.remote.displayPersonName
import com.kitsugi.animelist.data.remote.KitsugiStaffDetail
import com.kitsugi.animelist.data.local.TranslationManager
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.LocalIsTv
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.utils.KitsugiTranslateUtils.openTranslator
import com.kitsugi.animelist.ui.components.KitsugiMarkdownText
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import com.kitsugi.animelist.data.remote.DetailCache
import com.kitsugi.animelist.ui.components.KitsugiImageGalleryDialog
import com.kitsugi.animelist.ui.components.KitsugiPageEnter
import com.kitsugi.animelist.ui.components.KitsugiCinematicLoadingScreen
import com.kitsugi.animelist.utils.copyOnDoubleTap
import com.kitsugi.animelist.utils.toFriendlySourceLabel
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import com.kitsugi.animelist.data.fandom.FandomCharacterQueries
import com.kitsugi.animelist.ui.screens.detail.wiki.CharacterWikiTabSlot
import com.kitsugi.animelist.ui.screens.detail.wiki.FandomCharacterWikiViewModel

sealed interface StaffDetailState {
    object Loading : StaffDetailState
    data class Error(
        val message: String,
        val platformErrorCode: com.kitsugi.animelist.data.platform.PlatformErrorCode? = null,
    ) : StaffDetailState
    data class Success(val detail: KitsugiStaffDetail) : StaffDetailState
}

@Composable
fun StaffDetailPage(
    staffId: Int,
    source: String,
    onBackClick: () -> Unit,
    onCharacterClick: (characterId: Int, characterSource: String, name: String?, imageUrl: String?) -> Unit,
    onMediaClick: (mediaId: Int, mediaType: String, mediaSource: String) -> Unit,
    name: String? = null,
    imageUrl: String? = null,
    titleLanguage: String = "ENGLISH",
    staffNameLanguage: String = com.kitsugi.animelist.data.settings.KitsugiContentPrefs.staffNameLanguage,
    preferredTranslator: String = "DEFAULT"
) {
    val accentColor = LocalKitsugiAccent.current
    val context = LocalContext.current

    // Obtain ViewModel
    val viewModel: StaffDetailViewModel = viewModel(key = "staff_${source}_${staffId}")
    // Fandom Wiki: kişi, çalıştığı yapımların wiki'sinde adıyla aranır (karakter sayfasıyla aynı akış).
    val wikiViewModel: FandomCharacterWikiViewModel = viewModel(key = "fandom_staff_${source}_${staffId}")
    val wikiPrefs by wikiViewModel.prefs.collectAsState()
    val galleryLoading by viewModel.galleryLoading.collectAsState()
    val translationLoading by viewModel.translationLoading.collectAsState()
    // Otomatik çeviri sürerken sayfadaki çeviri butonları galeri butonuyla aynı animasyonu gösterir.
    CompositionLocalProvider(
        LocalKitsugiGalleryLoading provides galleryLoading,
        LocalKitsugiTranslationLoading provides translationLoading
    ) {

    // Load staff in ViewModel
    LaunchedEffect(staffId, source) {
        viewModel.loadStaff(staffId, source, name)
    }

    // Collect states from ViewModel
    val state by viewModel.state.collectAsState()
    val translatedBio by viewModel.translatedBio.collectAsState()
    val showBioOriginal by viewModel.showBioOriginal.collectAsState()
    val isFavourite by viewModel.isFavourite.collectAsState()
    val isAniListSource = source.lowercase() == "anilist"
    val isAniListConnected = remember { com.kitsugi.animelist.data.auth.ExternalAuthManager.getAniListToken(context) != null }
    val isBangumiSource = source.trim().lowercase() == "bangumi" || source.trim().lowercase() == "bgm"
    val isBangumiConnected = remember { com.kitsugi.animelist.data.auth.BangumiAuthStore.isConnected(context) }
    val showFavouriteButton = if (isBangumiSource) isBangumiConnected else (isAniListSource || isAniListConnected)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KitsugiColors.Background)
    ) {
        when (val currentState = state) {
            is StaffDetailState.Loading -> {
                KitsugiCinematicLoadingScreen(
                    title = name.orEmpty(),
                    imageUrl = imageUrl,
                    source = source,
                    onBackClick = onBackClick
                )
            }
            is StaffDetailState.Error -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 12.dp, top = 24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = KitsugiColors.TextPrimary
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = currentState.message,
                            color = KitsugiColors.AccentRed,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )

                        currentState.platformErrorCode?.let { code ->
                            Spacer(modifier = Modifier.height(12.dp))
                            PlatformErrorExplanationBlock(
                                code = code,
                                tint = KitsugiColors.AccentRed,
                                modifier = Modifier.padding(horizontal = 8.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(
                            onClick = {
                                viewModel.retry()
                            }
                        ) {
                            Text(stringResource(R.string.action_retry_label), color = accentColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            is StaffDetailState.Success -> {
                val rawDetail = currentState.detail
                val detail = remember(rawDetail, staffNameLanguage) {
                    rawDetail.copy(
                        // Japonca alt satır yalnızca özgün dil seçiliyken gösterilir; diğer dillerde
                        // ad seçilen dile göre (İngilizce → Romaji → özgün son çare) çözülür.
                        nativeName = rawDetail.nativeName.takeIf {
                            staffNameLanguage == "NATIVE" || staffNameLanguage == "JAPANESE_STAFF"
                        },
                        name = displayPersonName(
                            rawDetail.name, rawDetail.romanizedName, rawDetail.nativeName, staffNameLanguage, rawDetail.englishName
                        ),
                        characterRoles = rawDetail.characterRoles.map { role ->
                            role.copy(
                                characterName = displayPersonName(
                                    role.characterName,
                                    role.characterRomanizedName,
                                    role.characterNativeName,
                                    staffNameLanguage,
                                    role.characterEnglishName
                                )
                            )
                        }
                    )
                }
                val isRefreshing by viewModel.isRefreshing.collectAsState()
                val pullRefreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.forceRefresh() },
                    modifier = Modifier.fillMaxSize(),
                    state = pullRefreshState,
                    indicator = {
                        KitsugiPullToRefreshIndicator(
                            state = pullRefreshState,
                            isRefreshing = isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                ) {
                val listState = rememberLazyListState()
                val showFloatingHeader = rememberDetailStickyHeaderVisible(
                    listState = listState,
                    showAtIndex = 1,
                    hideAtIndex = 0
                )
                // Wiki çözümlemesi ham detayla yapılır (ad gösterim tercihinden bağımsız).
                LaunchedEffect(rawDetail, source, staffId) {
                    wikiViewModel.bindStaff(rawDetail, source, staffId)
                }
                // Sekme görünürlüğü çözümlemeyi beklemez: ad + yapım varsa sekme vardır.
                val wikiTabPossible = wikiPrefs.enabled && remember(rawDetail, source, staffId) {
                    FandomCharacterQueries.fromStaff(rawDetail, source, staffId) != null
                }
                val tabs = buildList {
                    add(stringResource(R.string.staff_tab_about))
                    add(stringResource(R.string.staff_tab_characters))
                    add(stringResource(R.string.staff_tab_works))
                    if (wikiTabPossible) add(stringResource(R.string.fandom_staff_tab_wiki))
                }
                @OptIn(ExperimentalFoundationApi::class)
                val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })
                // Wiki sekmesi kaldırılırsa (ayar kapatıldıysa) geçersiz sayfada kalınmaz.
                LaunchedEffect(tabs.size) {
                    if (pagerState.currentPage >= tabs.size) {
                        pagerState.scrollToPage((tabs.size - 1).coerceAtLeast(0))
                    }
                }
                val selectedTab = pagerState.currentPage
                val coroutineScope = rememberCoroutineScope()
                val isTv = LocalIsTv.current
                val tabListState = rememberLazyListState()
                var activeGalleryItems by remember { mutableStateOf<List<GalleryItem>>(emptyList()) }
                var activeGalleryIndex by remember { mutableStateOf(0) }
                val rawGalleryItems by viewModel.galleryItems.collectAsState()
                // Galeri etiketi (ad) seçilen başlık diline göre çözülür: İngilizce → Romaji → Latin alternatif.
                val galleryLabel = remember(rawDetail, titleLanguage) {
                    galleryPersonLabel(
                        titleLanguage = titleLanguage,
                        name = rawDetail.name,
                        romanized = rawDetail.romanizedName,
                        native = rawDetail.nativeName,
                        english = rawDetail.englishName,
                        alternatives = rawDetail.alternativeNames
                    )
                }
                val galleryItems = remember(rawGalleryItems, galleryLabel, detail.imageUrl, source) {
                    val localizedItems = rawGalleryItems.map { item ->
                        if (item.description != null) item.copy(description = galleryLabel) else item
                    }
                    ensureStaffHeroGalleryItem(
                        galleryItems = localizedItems,
                        imageUrl = detail.imageUrl,
                        source = source.toFriendlySourceLabel(),
                        description = galleryLabel
                    )
                }
                val dialogGalleryItems = if (activeGalleryItems.any { it.category == GalleryCategory.PERSON }) {
                    // If the user opens the hero before aggregation finishes, keep the dialog
                    // live-bound to this list so extra images appear as soon as they arrive.
                    galleryItems
                } else {
                    activeGalleryItems
                }
                val openGallery: (List<GalleryItem>, Int) -> Unit = { items, index ->
                    if (items.isNotEmpty()) {
                        activeGalleryItems = items
                        activeGalleryIndex = index.coerceIn(items.indices)
                    }
                }
                val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                // TV odak highway
                val leftPanelFocusRequester = remember { FocusRequester() }
                val tabBarFocusRequester = remember { FocusRequester() }

                if (isLandscape) {
                    val configuration = LocalConfiguration.current
                    val screenWidth = configuration.screenWidthDp
                    val leftPanelWeight = when {
                        screenWidth >= 1200 -> 0.28f
                        screenWidth >= 840  -> 0.32f
                        else                -> 0.38f
                    }
                    val rightPanelWeight = 1f - leftPanelWeight
                    // ── LANDSCAPE: Sol hero/bio paneli + Sağ tab paneli ──
                    KitsugiPageEnter {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                // Sol panel: StaffDetailLeftPanel
                                StaffDetailLeftPanel(
                                    detail = detail,
                                    source = source,
                                    staffId = staffId,
                                    accentColor = accentColor,
                                    isFavourite = isFavourite,
                                    showFavouriteButton = showFavouriteButton,
                                    galleryItems = galleryItems,
                                    leftPanelFocusRequester = leftPanelFocusRequester,
                                    tabBarFocusRequester = tabBarFocusRequester,
                                    onBackClick = onBackClick,
                                    onToggleFavourite = { viewModel.toggleFavourite() },
                                    onGalleryClick = openGallery,
                                    modifier = Modifier.weight(leftPanelWeight)
                                )
                                // Sağ panel
                                Column(
                                    modifier = Modifier.weight(rightPanelWeight).fillMaxSize()
                                ) {
                                    LaunchedEffect(selectedTab) {
                                        tabListState.animateScrollToItem(selectedTab)
                                    }
                                    // Full-width tab row — each tab fills equally (AniHyou style)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(tabBarFocusRequester)
                                            .focusProperties { left = leftPanelFocusRequester }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        tabs.forEachIndexed { index, title ->
                                            val isSelected = selectedTab == index
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .background(if (isSelected) accentColor else KitsugiColors.Surface)
                                                    .tvClickable(shape = RoundedCornerShape(999.dp)) { coroutineScope.launch { pagerState.animateScrollToPage(index) } }
                                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(title, color = if (isSelected) KitsugiColors.Background else KitsugiColors.TextSecondary, style = MaterialTheme.typography.labelLarge, fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                    @OptIn(ExperimentalFoundationApi::class)
                                    HorizontalPager(
                                        state = pagerState,
                                        userScrollEnabled = !isTv,
                                        modifier = Modifier.fillMaxSize()
                                    ) { page ->
                                    Column(
                                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        when (page) {
                                            0 -> {
                                                StaffAboutTabContent(
                                                    detail = detail,
                                                    translatedBio = translatedBio,
                                                    preferredTranslator = preferredTranslator,
                                                    accentColor = accentColor,
                                                    onGalleryClick = openGallery,
                                                    onTranslateClick = { viewModel.translateBio() },
                                                    showOriginalBio = showBioOriginal,
                                                    onToggleOriginalBio = { viewModel.toggleBioOriginal() }
                                                )
                                            }
                                            1 -> {
                                                StaffCharacterRolesTabContent(
                                                    pagingActive = page == pagerState.settledPage,
                                                    detail = detail,
                                                    titleLanguage = titleLanguage,
                                                    onCharacterClick = onCharacterClick,
                                                    onMediaClick = onMediaClick
                                                )
                                            }
                                            2 -> {
                                                StaffMediaWorksTabContent(
                                                    pagingActive = page == pagerState.settledPage,
                                                    detail = detail,
                                                    titleLanguage = titleLanguage,
                                                    onMediaClick = onMediaClick
                                                )
                                            }
                                            3 -> CharacterWikiTabSlot(
                                                viewModel = wikiViewModel,
                                                accentColor = accentColor,
                                                onOpenGallery = openGallery,
                                                unavailableMessageRes = R.string.fandom_unavailable_staff
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(32.dp))
                                    }
                                    } // end HorizontalPager
                                }
                            }
                            if (activeGalleryItems.isNotEmpty()) {
                                KitsugiImageGalleryDialog(
                                    galleryItems = dialogGalleryItems,
                                    initialIndex = activeGalleryIndex,
                                    title = detail.name,
                                    onDismiss = { activeGalleryItems = emptyList() }
                                )
                            }
                        }
                    }
                } else {
                    // ── PORTRAIT: Mevcut LazyColumn düzeni ──
                KitsugiPageEnter {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Hero section
                            item(key = "hero") {
                                StaffPortraitHeroSection(
                                    detail = detail,
                                    source = source,
                                    staffId = staffId,
                                    accentColor = accentColor,
                                    isFavourite = isFavourite,
                                    showFavouriteButton = showFavouriteButton,
                                    galleryItems = galleryItems,
                                    onBackClick = onBackClick,
                                    onToggleFavourite = { viewModel.toggleFavourite() },
                                    onGalleryOpen = openGallery
                                )
                            }

                            // Tabs row
                            stickyHeader(key = "tabs") {
                                LaunchedEffect(selectedTab) {
                                    if (listState.firstVisibleItemIndex > 1) {
                                        listState.scrollToItem(1)
                                    }
                                }
                                StaffDetailStickyHeader(
                                    detail = detail,
                                    source = source,
                                    staffId = staffId,
                                    tabs = tabs,
                                    selectedTab = selectedTab,
                                    showFloatingHeader = showFloatingHeader,
                                    isFavourite = isFavourite,
                                    showFavouriteButton = showFavouriteButton,
                                    galleryItems = galleryItems,
                                    accentColor = accentColor,
                                    onBackClick = onBackClick,
                                    onToggleFavourite = { viewModel.toggleFavourite() },
                                    onGalleryOpen = openGallery,
                                    onTabSelected = { index ->
                                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                    }
                                )
                            }


                            // Tab content rendering
                            item(key = "content") {
                                val pageHeights = remember { androidx.compose.runtime.mutableStateMapOf<Int, Int>() }
                                val screenHeightDp = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp

                                @OptIn(ExperimentalFoundationApi::class)
                                HorizontalPager(
                                    state = pagerState,
                                    userScrollEnabled = !isTv,
                                    beyondViewportPageCount = 0,
                                    pageSpacing = 12.dp,
                                    verticalAlignment = Alignment.Top,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .layout { measurable, constraints ->
                                            val minPagerHeightPx = (screenHeightDp - 64).dp.roundToPx().coerceAtLeast(0)
                                            val placeable = measurable.measure(
                                                constraints.copy(
                                                    minHeight = minPagerHeightPx,
                                                    maxHeight = androidx.compose.ui.unit.Constraints.Infinity
                                                )
                                            )
                                            val cp = pagerState.currentPage
                                            val offset = pagerState.currentPageOffsetFraction
                                            val tp = when {
                                                offset > 0f -> cp + 1
                                                offset < 0f -> cp - 1
                                                else -> cp
                                            }
                                            val curH = pageHeights[cp] ?: 0
                                            val tgtH = pageHeights[tp] ?: curH
                                            val interpPx = when {
                                                curH > 0 && tgtH > 0 ->
                                                    (curH + (tgtH - curH) * kotlin.math.abs(offset)).toInt()
                                                curH > 0 -> curH
                                                else -> 0
                                            }
                                            val height = (if (interpPx > 0) interpPx.coerceAtLeast(minPagerHeightPx) else placeable.height)
                                                .coerceIn(minPagerHeightPx, 16_000_000)
                                            layout(placeable.width, height) {
                                                placeable.placeRelative(0, 0)
                                            }
                                        }
                                        .clipToBounds()
                                ) { page ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 600.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 20.dp)
                                                .onGloballyPositioned { coordinates ->
                                                    com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults
                                                        .recordStablePageHeight(pageHeights, page, coordinates.size.height)
                                                }
                                        ) {
                                            when (page) {
                                                0 -> {
                                                    StaffAboutTabContent(
                                                        detail = detail,
                                                        translatedBio = translatedBio,
                                                        preferredTranslator = preferredTranslator,
                                                        accentColor = accentColor,
                                                        onGalleryClick = openGallery,
                                                        onTranslateClick = { viewModel.translateBio() },
                                                        showOriginalBio = showBioOriginal,
                                                        onToggleOriginalBio = { viewModel.toggleBioOriginal() }
                                                    )
                                                }
                                                1 -> {
                                                    StaffCharacterRolesTabContent(
                                                        pagingActive = page == pagerState.settledPage,
                                                        detail = detail,
                                                        titleLanguage = titleLanguage,
                                                        onCharacterClick = onCharacterClick,
                                                        onMediaClick = onMediaClick
                                                    )
                                                }
                                                2 -> {
                                                    StaffMediaWorksTabContent(
                                                        pagingActive = page == pagerState.settledPage,
                                                        detail = detail,
                                                        titleLanguage = titleLanguage,
                                                        onMediaClick = onMediaClick
                                                    )
                                                }
                                                3 -> CharacterWikiTabSlot(
                                                    viewModel = wikiViewModel,
                                                    accentColor = accentColor,
                                                    onOpenGallery = openGallery,
                                                    unavailableMessageRes = R.string.fandom_unavailable_staff
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(50.dp))
                                        }
                                    }
                                } // end HorizontalPager
                            }
                        }

                        if (activeGalleryItems.isNotEmpty()) {
                            KitsugiImageGalleryDialog(
                                galleryItems = dialogGalleryItems,
                                initialIndex = activeGalleryIndex,
                                title = detail.name,
                                onDismiss = { activeGalleryItems = emptyList() }
                            )
                        }


                    }
                }
            } // end else (portrait)
                } // end PullToRefreshBox
        } // end Success
    } // end when
} // end outer Box
    }
} // end StaffDetailPage

