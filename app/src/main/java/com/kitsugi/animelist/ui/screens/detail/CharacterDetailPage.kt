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
import com.kitsugi.animelist.data.remote.galleryPersonLabel
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.foundation.shape.CircleShape
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.IconButton
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
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
import com.kitsugi.animelist.data.remote.displayPersonName
import com.kitsugi.animelist.data.remote.KitsugiCharacterDetail
import com.kitsugi.animelist.data.local.TranslationManager
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.LocalIsTv
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.utils.KitsugiTranslateUtils.openTranslator
import com.kitsugi.animelist.ui.components.KitsugiMarkdownText
import com.kitsugi.animelist.data.remote.DetailCache
import com.kitsugi.animelist.ui.components.KitsugiImageGalleryDialog
import com.kitsugi.animelist.ui.components.KitsugiPageEnter
import com.kitsugi.animelist.ui.components.KitsugiCinematicLoadingScreen
import com.kitsugi.animelist.utils.copyOnDoubleTap
import com.kitsugi.animelist.utils.toFriendlySourceLabel
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import com.kitsugi.animelist.data.remote.GalleryItem
import com.kitsugi.animelist.data.remote.GalleryCategory
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import com.kitsugi.animelist.data.fandom.FandomCharacterQueries
import com.kitsugi.animelist.ui.screens.detail.wiki.CharacterWikiTabSlot
import com.kitsugi.animelist.ui.components.KitsugiPullToRefreshIndicator
import androidx.activity.compose.BackHandler
import com.kitsugi.animelist.ui.screens.detail.wiki.FandomWikiReaderSheet
import com.kitsugi.animelist.ui.screens.detail.wiki.FandomReaderRenderState
import com.kitsugi.animelist.data.fandom.FandomUrls
import com.kitsugi.animelist.ui.screens.detail.wiki.FandomCharacterWikiViewModel

sealed interface CharacterDetailState {
    object Loading : CharacterDetailState
    data class Error(
        val message: String,
        val platformErrorCode: com.kitsugi.animelist.data.platform.PlatformErrorCode? = null,
    ) : CharacterDetailState
    data class Success(val detail: KitsugiCharacterDetail) : CharacterDetailState
}

/** Karakter detay sekmeleri. Sayfa içeriği sıra numarası yerine bu anahtarla seçilir. */
private enum class CharacterTabKey { ABOUT, APPEARANCES, VOICE_ACTORS, WIKI }

@Composable
fun CharacterDetailPage(
    characterId: Int,
    source: String,
    onBackClick: () -> Unit,
    onMediaClick: (mediaId: Int, mediaType: String, mediaSource: String) -> Unit,
    onStaffClick: (staffId: Int, source: String, name: String?, imageUrl: String?) -> Unit,
    onCharacterClick: (characterId: Int, characterSource: String, name: String?, imageUrl: String?) -> Unit,
    name: String? = null,
    imageUrl: String? = null,
    titleLanguage: String = "ENGLISH",
    staffNameLanguage: String = com.kitsugi.animelist.data.settings.KitsugiContentPrefs.staffNameLanguage,
    preferredTranslator: String = "DEFAULT",
    isRealMediaRole: Boolean = false
) {
    val accentColor = LocalKitsugiAccent.current
    val context = LocalContext.current

    // TMDB person IDs can be shared by several cast roles; keep each requested role's state isolated.
    val viewModelKey = remember(source, characterId, name, isRealMediaRole) {
        "character_${com.kitsugi.animelist.data.remote.CharacterDetailIdentity.fetchKey(source, characterId, name, isRealMediaRole)}"
    }
    val viewModel: CharacterDetailViewModel = viewModel(key = viewModelKey)
    // Fandom Wiki: karakterin wiki sayfası arka planda çözülür (kimlik önce, isim son çare);
    // "Wiki" sekmesi yalnızca sayfa bulunursa en sona eklenir.
    val wikiViewModel: FandomCharacterWikiViewModel = viewModel(key = "fandom_$viewModelKey")
    val galleryLoading by viewModel.galleryLoading.collectAsState()
    val translationLoading by viewModel.translationLoading.collectAsState()
    // Otomatik çeviri sürerken sayfadaki çeviri butonları galeri butonuyla aynı animasyonu gösterir.
    CompositionLocalProvider(
        LocalKitsugiGalleryLoading provides galleryLoading,
        LocalKitsugiTranslationLoading provides translationLoading
    ) {

    // Load character in ViewModel — kartta gösterilen görsel (imageUrl) ipucu olarak
    // iletilir; kaynakta görsel yoksa detay sayfası resimsiz kalmaz.
    LaunchedEffect(characterId, source, name, imageUrl, isRealMediaRole) {
        viewModel.loadCharacter(characterId, source, name, hintImageUrl = imageUrl, isRealMediaRole = isRealMediaRole)
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
            is CharacterDetailState.Loading -> {
                KitsugiCinematicLoadingScreen(
                    title = name.orEmpty(),
                    imageUrl = imageUrl,
                    source = source,
                    onBackClick = onBackClick
                )
            }
            is CharacterDetailState.Error -> {
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
            is CharacterDetailState.Success -> {
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
                        voiceActors = rawDetail.voiceActors.map { va ->
                            va.copy(name = displayPersonName(va.name, va.romanizedName, va.nativeName, staffNameLanguage, va.englishName))
                        }
                    )
                }
                // Wiki çözümlemesi ham detayla yapılır (yerel ad gösterim tercihinden bağımsız).
                // PERF: Wiki durum akışları (sections, translations, reader vb.) kök sayfada değil
                // yalnızca Wiki sekmesi açıkken CharacterWikiTabSlot(viewModel = ...) içinde toplanır;
                // arka planda Fandom çözümleme/çeviri sürerken diğer sekmeler yeniden compose olmaz.
                LaunchedEffect(rawDetail, source, characterId) {
                    wikiViewModel.bind(rawDetail, source, characterId)
                }
                LaunchedEffect(wikiViewModel) {
                    wikiViewModel.state
                        .mapNotNull { (it as? com.kitsugi.animelist.ui.screens.detail.wiki.FandomCharacterWikiViewModel.UiState.Ready)?.page }
                        .distinctUntilChanged()
                        .collect { page ->
                            viewModel.onWikiCharacterPageResolved(page)
                        }
                }
                val wikiPrefs by wikiViewModel.prefs.collectAsState()
                // Sekme görünürlüğü çözümlemeyi beklemez: kimlik/ad varsa sekme vardır.
                val wikiTabPossible = wikiPrefs.enabled && remember(rawDetail, source, characterId) {
                    FandomCharacterQueries.from(rawDetail, source, characterId) != null
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
                val tabAbout      = stringResource(R.string.character_tab_about)
                val tabAppearances = stringResource(R.string.character_tab_appearances)
                val tabVoiceActors = stringResource(R.string.character_tab_voice_actors)
                val tabWiki = stringResource(R.string.character_tab_wiki)
                val tabKeys = buildList {
                    add(CharacterTabKey.ABOUT)
                    add(CharacterTabKey.APPEARANCES)
                    if (source.lowercase() != "tmdb" || !isRealMediaRole) add(CharacterTabKey.VOICE_ACTORS)
                    // Wiki sekmesi en sonda ama sayfa açılır açılmaz görünür: çözümleme sürerken
                    // iskelet gösterilir, böylece sekme sonradan belirip sekmeleri kaydırmaz.
                    if (wikiTabPossible) add(CharacterTabKey.WIKI)
                }
                val tabs = tabKeys.map { key ->
                    when (key) {
                        CharacterTabKey.ABOUT -> tabAbout
                        CharacterTabKey.APPEARANCES -> tabAppearances
                        CharacterTabKey.VOICE_ACTORS -> tabVoiceActors
                        CharacterTabKey.WIKI -> tabWiki
                    }
                }
                @OptIn(ExperimentalFoundationApi::class)
                val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })
                // Wiki sekmesi kaldırılırsa (ör. "Yanlışsa bildir") geçersiz sayfada kalınmaz.
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
                val galleryItems = remember(rawGalleryItems, galleryLabel) {
                    rawGalleryItems.map { item ->
                        if (item.description != null) item.copy(description = galleryLabel) else item
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
                    // ── LANDSCAPE: Sol hero paneli + Sağ tab paneli ──
                    KitsugiPageEnter {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                // Sol panel: CharacterDetailLeftPanel
                                CharacterDetailLeftPanel(
                                    detail = detail,
                                    source = source,
                                    characterId = characterId,
                                    accentColor = accentColor,
                                    isFavourite = isFavourite,
                                    showFavouriteButton = showFavouriteButton,
                                    galleryItems = galleryItems,
                                    leftPanelFocusRequester = leftPanelFocusRequester,
                                    tabBarFocusRequester = tabBarFocusRequester,
                                    onBackClick = onBackClick,
                                    onToggleFavourite = { viewModel.toggleFavourite() },
                                    onGalleryClick = { items, idx ->
                                        if (!galleryLoading) activeGalleryItems = items
                                        activeGalleryIndex = idx
                                    },
                                    modifier = Modifier.weight(leftPanelWeight)
                                )

                                // Sağ panel: Tab seçici + içerik
                                Column(
                                    modifier = Modifier
                                        .weight(rightPanelWeight)
                                        .fillMaxSize()
                                ) {
                                    // Tab row
                                    LaunchedEffect(selectedTab) {
                                        tabListState.animateScrollToItem(selectedTab)
                                    }
                                    LazyRow(
                                        state = tabListState,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(tabBarFocusRequester)
                                            .focusProperties { left = leftPanelFocusRequester }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(tabs.size) { index ->
                                            val title = tabs[index]
                                            val isSelected = selectedTab == index
                                            val bgColor = if (isSelected) accentColor else KitsugiColors.Surface
                                            val textColor = if (isSelected) KitsugiColors.Background else KitsugiColors.TextSecondary
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .background(bgColor)
                                                    .tvClickable(shape = RoundedCornerShape(999.dp)) { coroutineScope.launch { pagerState.animateScrollToPage(index) } }
                                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = title,
                                                    color = textColor,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    // Tab content - swipeable HorizontalPager
                                    @OptIn(ExperimentalFoundationApi::class)
                                    HorizontalPager(
                                        state = pagerState,
                                        userScrollEnabled = !isTv,
                                        modifier = Modifier.fillMaxSize()
                                    ) { page ->
                                    val currentTab = tabs.getOrNull(page)
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        when (tabKeys.getOrNull(page)) {
                                            CharacterTabKey.ABOUT -> {
                                                CharacterAboutTabContent(
                                                    detail = detail,
                                                    translatedBio = translatedBio,
                                                    preferredTranslator = preferredTranslator,
                                                    accentColor = accentColor,
                                                    onGalleryClick = { items, idx ->
                                                        if (!galleryLoading) activeGalleryItems = items
                                                        activeGalleryIndex = idx
                                                    },
                                                    onTranslateClick = { viewModel.translateBio() },
                                                    showOriginalBio = showBioOriginal,
                                                    onToggleOriginalBio = { viewModel.toggleBioOriginal() }
                                                )
                                            }
                                            CharacterTabKey.APPEARANCES -> {
                                                CharacterAppearancesTabContent(
                                                    pagingActive = page == pagerState.settledPage,
                                                    detail = detail,
                                                    titleLanguage = titleLanguage,
                                                    onMediaClick = onMediaClick
                                                )
                                            }
                                            CharacterTabKey.VOICE_ACTORS -> {
                                                CharacterVoiceActorsTabContent(
                                                    pagingActive = page == pagerState.settledPage,
                                                    detail = detail,
                                                    onStaffClick = onStaffClick
                                                )
                                            }
                                            CharacterTabKey.WIKI -> CharacterWikiTabSlot(
                                                viewModel = wikiViewModel,
                                                accentColor = accentColor,
                                                onOpenGallery = { items, idx ->
                                                    activeGalleryItems = items
                                                    activeGalleryIndex = idx
                                                }
                                            )

                                            null -> Unit
                                        }
                                        Spacer(modifier = Modifier.height(32.dp))
                                    }
                                    } // end HorizontalPager
                                }
                            }

                            if (activeGalleryItems.isNotEmpty()) {
                                KitsugiImageGalleryDialog(
                                    galleryItems = activeGalleryItems,
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
                            // Hero image section
                            item(key = "hero") {
                                CharacterPortraitHeroSection(
                                    detail = detail,
                                    source = source,
                                    characterId = characterId,
                                    accentColor = accentColor,
                                    isFavourite = isFavourite,
                                    isAniListSource = isAniListSource,
                                    galleryItems = galleryItems,
                                    onBackClick = onBackClick,
                                    onToggleFavourite = { viewModel.toggleFavourite() },
                                    onGalleryOpen = { items, idx ->
                                        if (!galleryLoading) activeGalleryItems = items
                                        activeGalleryIndex = idx
                                    }
                                )
                            }
                            // Tabs sticky header
                            stickyHeader(key = "tabs") {
                                CharacterDetailStickyHeader(
                                    detail = detail,
                                    source = source,
                                    characterId = characterId,
                                    tabs = tabs,
                                    selectedTab = selectedTab,
                                    showFloatingHeader = showFloatingHeader,
                                    isFavourite = isFavourite,
                                    showFavouriteButton = showFavouriteButton,
                                    galleryItems = galleryItems,
                                    tabListState = tabListState,
                                    accentColor = accentColor,
                                    onBackClick = onBackClick,
                                    onToggleFavourite = { viewModel.toggleFavourite() },
                                    onGalleryOpen = { items, idx ->
                                        if (!galleryLoading) activeGalleryItems = items
                                        activeGalleryIndex = idx
                                    },
                                    onTabSelected = { coroutineScope.launch { pagerState.animateScrollToPage(it) } }
                                )
                            }

                            // Tab contents - swipeable HorizontalPager
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
                                        val currentTab = tabs.getOrNull(page)
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 20.dp)
                                                .onGloballyPositioned { coordinates ->
                                                    com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults
                                                        .recordStablePageHeight(pageHeights, page, coordinates.size.height)
                                                }
                                        ) {
                                            when (tabKeys.getOrNull(page)) {
                                                CharacterTabKey.ABOUT -> {
                                                    CharacterAboutTabContent(
                                                        detail = detail,
                                                        translatedBio = translatedBio,
                                                        preferredTranslator = preferredTranslator,
                                                        accentColor = accentColor,
                                                        onGalleryClick = { items, idx ->
                                                            if (!galleryLoading) activeGalleryItems = items
                                                            activeGalleryIndex = idx
                                                        },
                                                        onTranslateClick = { viewModel.translateBio() },
                                                        showOriginalBio = showBioOriginal,
                                                        onToggleOriginalBio = { viewModel.toggleBioOriginal() }
                                                    )
                                                }
                                                CharacterTabKey.APPEARANCES -> {
                                                    CharacterAppearancesTabContent(
                                                        pagingActive = page == pagerState.settledPage,
                                                        detail = detail,
                                                        titleLanguage = titleLanguage,
                                                        onMediaClick = onMediaClick
                                                    )
                                                }
                                                CharacterTabKey.VOICE_ACTORS -> {
                                                    CharacterVoiceActorsTabContent(
                                                        pagingActive = page == pagerState.settledPage,
                                                        detail = detail,
                                                        onStaffClick = onStaffClick
                                                    )
                                                }
                                                CharacterTabKey.WIKI -> CharacterWikiTabSlot(
                                                    viewModel = wikiViewModel,
                                                    accentColor = accentColor,
                                                    onOpenGallery = { items, idx ->
                                                        activeGalleryItems = items
                                                        activeGalleryIndex = idx
                                                    }
                                                )

                                                null -> Unit
                                            }
                                            Spacer(modifier = Modifier.height(50.dp))
                                        }
                                    }
                                } // end HorizontalPager
                            }
                        }

                        if (activeGalleryItems.isNotEmpty()) {
                            KitsugiImageGalleryDialog(
                                galleryItems = activeGalleryItems,
                                initialIndex = activeGalleryIndex,
                                title = detail.name,
                                onDismiss = { activeGalleryItems = emptyList() }
                            )
                        }                    }
                }
                } // end else (portrait)
                } // end PullToRefreshBox
            }
        }
    }
    }
}
