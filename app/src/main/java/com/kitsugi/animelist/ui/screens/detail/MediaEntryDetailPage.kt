@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.kitsugi.animelist.ui.screens.detail

import com.kitsugi.animelist.ui.components.LocalKitsugiGalleryLoading
import com.kitsugi.animelist.ui.components.LocalKitsugiTranslationLoading
import com.kitsugi.animelist.ui.components.KitsugiDetailHeaderActions
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.wrapContentHeight
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.IconButton
import com.kitsugi.animelist.utils.rememberScrollVisibilityState
import com.kitsugi.animelist.utils.rememberScrollConnection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.style.TextOverflow
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import coil3.compose.AsyncImage
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.utils.labelRes
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.data.local.TranslationManager
import com.kitsugi.animelist.data.remote.DetailCache
import com.kitsugi.animelist.data.remote.JikanApiClient
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.data.remote.KitsugiCharacter
import com.kitsugi.animelist.data.remote.KitsugiEpisodeRatingsRepository
import com.kitsugi.animelist.data.remote.KitsugiMediaDetail
import com.kitsugi.animelist.data.remote.KitsugiRelation
import com.kitsugi.animelist.data.remote.KitsugiReview
import com.kitsugi.animelist.data.remote.KitsugiStaff
import com.kitsugi.animelist.data.remote.KitsugiStreamingEpisode
import com.kitsugi.animelist.data.remote.KitsugiStats
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.data.remote.toStreamMediaIds
import com.kitsugi.animelist.data.fandom.FandomMediaQueries
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.CompositionLocalProvider
import com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults
import com.kitsugi.animelist.ui.utils.dpadVerticalFastScroll
import com.kitsugi.animelist.ui.components.KitsugiPageEnter
import com.kitsugi.animelist.ui.components.KitsugiCinematicLoadingScreen
import com.kitsugi.animelist.ui.components.KitsugiEpisodeOptionsDialog
import com.kitsugi.animelist.ui.components.KitsugiImageGalleryDialog
import com.kitsugi.animelist.ui.components.KitsugiStreamSelectorBottomSheet
import com.kitsugi.animelist.ui.screens.fullscreen.KitsugiFullscreenPlayerActivity
import com.kitsugi.animelist.ui.screens.stream.KitsugiStreamActivity
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.utils.KitsugiTranslateUtils.openTranslator
import com.kitsugi.animelist.data.local.MangaMappingEntity
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.data.settings.AppSettings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsugi.animelist.ui.screens.detail.wiki.FandomMediaWikiViewModel
import com.kitsugi.animelist.ui.screens.detail.wiki.MediaWikiTabSlot
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import com.kitsugi.animelist.ui.theme.LocalIsTv
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.remember
import com.kitsugi.animelist.utils.parseToMediaType
import com.kitsugi.animelist.data.remote.GalleryItem
import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.utils.KitsugiStrings

@Composable
fun MediaEntryDetailPage(
    entry: MediaEntry,
    onBackClick: () -> Unit,
    onIncrementProgressClick: () -> Unit,
    onToggleFavoriteClick: () -> Unit,
    onSynopsisLoaded: (String) -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRelationClick: (JikanSearchResult) -> Unit,
    onCharacterClick: (KitsugiCharacter) -> Unit,
    onStaffClick: (Int, String, String?, String?) -> Unit,
    onStudioClick: (Int, String, String?, String?) -> Unit,
    onMagazineClick: (Int, String) -> Unit = { _, _ -> },
    onUserProfileClick: (Int, String, String?, String) -> Unit = { _, _, _, _ -> },
    onSearchQuery: (String) -> Unit = {},
    onSearchByGenre: (String) -> Unit = {},
    onSearchByTag: (com.kitsugi.animelist.data.remote.KitsugiTag) -> Unit = {},
    onSearchBySeason: (com.kitsugi.animelist.ui.screens.search.DetailSeasonMetadata) -> Unit = {},
    titleLanguage: String = "ENGLISH",
    /** Kişi/karakter adı dili — başlık dilinden bağımsız ayar (bkz. [ApiResultDetailPage]). */
    staffNameLanguage: String = com.kitsugi.animelist.data.settings.KitsugiContentPrefs.staffNameLanguage,
    scoreFormat: String = "POINT_10",
    hideScores: Boolean = false,
    showAnimeLogos: Boolean = false,
    blurAdultMedia: Boolean = false,
    onReadMangaClick: ((MangaMappingEntity?) -> Unit)? = null,
    /**
     * "Oku" (eşleme yok) → kaynak araması.
     *
     * Gönderilen liste: **dil sırasına göre** aday başlıklar (seçili arama dili
     * önce, kalanlar yedek). Dil kararı tek yerde — `AppRoot` — verilir.
     */
    onSearchMangaByTitles: ((List<String>) -> Unit)? = null,
    preferredTranslator: String = "DEFAULT",
    mdbListShowImdb: Boolean = true,
    mdbListShowTomatoes: Boolean = true,
    mdbListShowMetacritic: Boolean = true,
    mdbListShowAudience: Boolean = false,
    mdbListShowLetterboxd: Boolean = false,
    mdbListShowTmdb: Boolean = false,
    mdbListShowTrakt: Boolean = false,
    settingsDataStore: SettingsDataStore? = null
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val accentColor = LocalKitsugiAccent.current
    val isTv = LocalIsTv.current
    val isTvDevice = com.kitsugi.animelist.ui.theme.LocalIsTvDevice.current
    val apiClient = remember { JikanApiClient() }

    val isSourceAniList = entry.source.lowercase() == "anilist"
    val isAniListConnected = remember { com.kitsugi.animelist.data.auth.ExternalAuthManager.getAniListToken(context) != null }
    val showFavouriteButton = isSourceAniList || isAniListConnected

    // Obtain ViewModel
    val viewModel: MediaEntryDetailViewModel = viewModel(key = "entry_${entry.source}_${entry.id}")
    // Fandom Wiki (Faz 2): yapım wiki'si sekmesi sayfa açılır açılmaz vardır; eşleşme arka planda çözülür.
    val wikiViewModel: FandomMediaWikiViewModel = viewModel(key = "fandom_media_${entry.source}_${entry.id}")
    val wikiPrefs by wikiViewModel.prefs.collectAsState()

    val settingsSlice = rememberDetailSettingsSlice(settingsDataStore)
    val mdbImdbId by remember(viewModel) {
        viewModel.mdbListRatings.map { it?.imdbId }.distinctUntilChanged()
    }.collectAsState(initial = null)
    var showIntegrationsDialog by remember { mutableStateOf(false) }

    // Load entry in ViewModel
    LaunchedEffect(entry.id, entry.source, entry.malId, showAnimeLogos) {
        viewModel.loadEntry(entry, showAnimeLogos)
    }

    LaunchedEffect(entry.id, entry.source, entry.malId, entry.type, entry.synopsis) {
        viewModel.onStoredSynopsisChanged(entry)
    }

    // PERF: Sekmeye özel akışlar (bölüm puanları, karakterler, ekip, öneriler, ilişkiler,
    // istatistikler, yorumlar, özet çevirisi, MDBList) ilgili sekme slotlarının kendi
    // RestartGroup'unda toplanır; arka plan ağ yanıtları kök sayfayı yeniden compose etmez.
    val detailState by viewModel.detailState.collectAsState()
    val detailLoading by viewModel.detailLoading.collectAsState()
    val detailErrorCode by viewModel.detailErrorCode.collectAsState()
    val mangaMapping by viewModel.mangaMapping.collectAsState()
    val originalSynopsis by viewModel.originalSynopsis.collectAsState()
    val logoUrl by viewModel.logoUrl.collectAsState()
    val resolvedTmdbId by viewModel.resolvedTmdbId.collectAsState()
    val targetSeason by viewModel.targetSeason.collectAsState()
    val galleryItems by viewModel.galleryItems.collectAsState()
    val galleryLoading by viewModel.galleryLoading.collectAsState()
    val translationLoading by viewModel.translationLoading.collectAsState()
    // Otomatik çeviri sürerken sayfadaki çeviri butonları galeri butonuyla aynı animasyonu gösterir.
    CompositionLocalProvider(
        LocalKitsugiGalleryLoading provides galleryLoading,
        LocalKitsugiTranslationLoading provides translationLoading
    ) {
    val pageResetTrigger by viewModel.pageResetTrigger.collectAsState()

    // Sadece detay verisi hazır olana kadar yükleme ekranı göster.
    // Galeri (fanart.tv) arka planda yüklenirken sayfa zaten açık kalır.
    val isLoading = detailLoading

    val displayEntry = remember(entry, detailState, resolvedTmdbId) {
        val detail = detailState
        if (detail != null) {
            entry.copy(
                title = detail.titleRomaji ?: (if (entry.title.isBlank() || entry.title == KitsugiStrings.get(R.string.loading)) (detail.title ?: entry.title) else entry.title),
                titleEnglish = detail.titleEnglish ?: entry.titleEnglish,
                titleJapanese = (detail.titleJapanese ?: detail.titleNative) ?: entry.titleJapanese,
                imageUrl = if (!detail.imageUrl.isNullOrBlank()) detail.imageUrl else entry.imageUrl,
                total = entry.total ?: detail.total,
                isAdult = entry.isAdult || detail.isAdult,
                tmdbId = entry.tmdbId ?: detail.tmdbId ?: resolvedTmdbId
            )
        } else {
            entry
        }
    }

    // Wiki sorgusu: kimlikler netleştikçe (gerçek MAL/TMDB) sorgu yeniden kurulur.
    val wikiQuery = remember(displayEntry, detailState, resolvedTmdbId, mdbImdbId) {
        val imdbId = mdbImdbId
            ?: detailState?.externalLinks?.firstOrNull { it.site.contains("IMDb", ignoreCase = true) || it.url.contains("imdb.com", ignoreCase = true) }
                ?.url?.let { Regex("(?i)tt[0-9]{5,12}").find(it)?.value }
        val wikidataId = detailState?.externalLinks
            ?.firstOrNull { it.site.contains("Wikidata", ignoreCase = true) || it.url.contains("wikidata.org", ignoreCase = true) }
            ?.url?.let { Regex("(?i)Q[0-9]+").find(it)?.value }
        FandomMediaQueries.fromMedia(
            source = displayEntry.source,
            // `id` is the local Room row; provider identity lives in `malId` and the explicit ID fields.
            mediaId = displayEntry.malId,
            malId = detailState?.realMalId,
            aniListId = displayEntry.aniListEntryId,
            tmdbId = displayEntry.tmdbId ?: detailState?.tmdbId ?: resolvedTmdbId,
            simklId = displayEntry.simklId,
            isManga = displayEntry.type == MediaType.Manga,
            isMovie = displayEntry.type == MediaType.Movie,
            isTvShow = displayEntry.type == MediaType.TvShow,
            releaseYear = displayEntry.year ?: detailState?.year,
            imdbId = imdbId,
            wikidataId = wikidataId,
            format = detailState?.format,
            rawFormat = detailState?.rawFormat,
            subtitle = displayEntry.subtitle,
            titles = (
                listOfNotNull(
                    displayEntry.titleEnglish,
                    displayEntry.title,
                    displayEntry.titleJapanese,
                    detailState?.titleNative,
                    detailState?.titleJapanese,
                    detailState?.titleRomaji,
                    detailState?.titleEnglish,
                    detailState?.titleTurkish
                ) + detailState?.synonyms.orEmpty()
            )
        )
    }
    LaunchedEffect(wikiQuery, wikiPrefs.enabled, wikiPrefs.nameFallback) {
        if (wikiPrefs.enabled) {
            kotlinx.coroutines.delay(220L)
        }
        wikiViewModel.bind(wikiQuery.takeIf { wikiPrefs.enabled })
    }

    val externalUrl = remember(displayEntry) { buildExternalUrl(displayEntry) }

    val onMediaClick: (Int, String, String) -> Unit = { mediaId, mediaType, mediaSource ->
        val searchType = mediaType.parseToMediaType()
        val searchResult = JikanSearchResult(
            malId = mediaId,
            title = context.getString(R.string.yukleniyor),
            subtitle = "",
            type = searchType,
            total = null,
            score = null,
            isAdult = false,
            imageUrl = null,
            year = null,
            source = mediaSource
        )
        onRelationClick(searchResult)
    }

    // GalleryItem-based dialog state (Fanart.tv + multi-source)
    var activeGalleryItems by remember { mutableStateOf<List<GalleryItem>>(emptyList()) }
    var activeGalleryIndex by remember { mutableStateOf(0) }
    var activeGalleryCategory by remember { mutableStateOf<GalleryCategory?>(null) }
    var activeEpisodeForOptions by remember { mutableStateOf<KitsugiStreamingEpisode?>(null) }

    val listState = rememberLazyListState()
    val tabListState = rememberLazyListState()
    val density = LocalDensity.current
    // TV odak highway — sol panel ↔ sağ panel tab bar
    val leftPanelFocusRequester = remember { FocusRequester() }
    val tabBarFocusRequester = remember { FocusRequester() }

    // State for tabs
    val isAnime = displayEntry.type == MediaType.Anime
    val hasTvEpisodes = isAnime || displayEntry.type == MediaType.TvShow
    val tabs = buildList {
        addAll(listOf(
            stringResource(R.string.detail_tab_info),
            stringResource(R.string.detail_tab_images),
            stringResource(R.string.detail_tab_videos),
            stringResource(R.string.detail_tab_characters),
            stringResource(R.string.detail_tab_staff),
            stringResource(R.string.detail_tab_recommendations),
            stringResource(R.string.detail_tab_relations),
            stringResource(R.string.detail_tab_charts),
            stringResource(R.string.detail_tab_reviews)
        ))
        if (hasTvEpisodes) add(stringResource(R.string.tv_detail_episodes))
        if (wikiPrefs.enabled) add(stringResource(R.string.character_tab_wiki))
    }
    val wikiPageIndex = if (wikiPrefs.enabled) tabs.size - 1 else -1
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })
    // KASMA FIX (2026-11-05): `pagerState.currentPage` kompozisyonda okunmuyor.
    // Okunduğunda her sekme geçişinde bu ekranın tamamı (pageContent lambda'ları dahil)
    // yeniden kompoze oluyor, bu da pager içindeki görünür sekmeyi de yeniden
    // kompoze etmeye zorluyordu. Sekme değişimi artık snapshotFlow ile dinleniyor.
    val currentPageFlow = remember(pagerState) {
        snapshotFlow { pagerState.currentPage }.distinctUntilChanged()
    }

    // Yeni bir entry'ye geçildiğinde tab'ı sıfırla — geri tuşuyla dönüşlerde SIFIRLAMAZ.
    // lastProcessedTrigger, Composition'da yaşayan son işlenmiş trigger değerini saklar.
    // Sayfa yeniden kompoze edildiğinde (back-stack pop) değer değişmediği için sıfırlama olmaz.
    var lastProcessedTrigger by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(pageResetTrigger) {
        if (pageResetTrigger > 0 && pageResetTrigger != lastProcessedTrigger) {
            lastProcessedTrigger = pageResetTrigger
            pagerState.scrollToPage(0)
        }
    }

    // Call loadTab when tab changes
    LaunchedEffect(displayEntry.id, detailState?.realMalId, detailState?.tmdbId, resolvedTmdbId, detailState == null, currentPageFlow) {
        currentPageFlow.collect { page ->
            if (page == wikiPageIndex) return@collect
            detailDataTabIndexForPage(page)?.let { dataTabIndex ->
                viewModel.loadTab(dataTabIndex, displayEntry, detailState?.realMalId)
            }
        }
    }

    // Propagate original synopsis back to UI if requested
    LaunchedEffect(originalSynopsis) {
        val synopsis = originalSynopsis
        if (!synopsis.isNullOrBlank()) {
            onSynopsisLoaded(synopsis)
        }
    }

    KitsugiPageEnter {
        DetailPageScaffold(
            title = displayEntry.title,
            isLoading = isLoading,
            isError = !detailLoading && detailState == null,
            isRefreshing = detailLoading,
            onRefresh = { viewModel.loadEntry(displayEntry, showAnimeLogos, forceRefresh = true) },
            onBackClick = onBackClick,
            tabs = tabs,
            pagerState = pagerState,
            listState = listState,
            tabListState = tabListState,
            loadingScreen = {
                KitsugiCinematicLoadingScreen(
                    title = displayEntry.title,
                    imageUrl = displayEntry.imageUrl,
                    logoUrl = logoUrl,
                    isAdult = displayEntry.isAdult,
                    blurAdultMedia = blurAdultMedia,
                    source = displayEntry.source,
                    onBackClick = onBackClick,
                    platformErrorCode = detailErrorCode,
                )
            },
            errorScreen = {
                DataUnavailableScreen(
                    title = displayEntry.title,
                    onBackClick = onBackClick,
                    onRetryClick = { viewModel.loadEntry(displayEntry, showAnimeLogos, forceRefresh = true) },
                    platformErrorCode = detailErrorCode,
                )
            },
            leftPanel = { lpFocus, tbFocus ->
                EntryDetailLeftPanel(
                    entry = displayEntry,
                    detailState = detailState,
                    logoUrl = logoUrl,
                    galleryItems = galleryItems,
                    mangaMapping = mangaMapping,
                    resolvedTmdbId = resolvedTmdbId,
                    showAnimeLogos = showAnimeLogos,
                    showFavouriteButton = showFavouriteButton,
                    titleLanguage = titleLanguage,
                    blurAdultMedia = blurAdultMedia,
                    onBackClick = onBackClick,
                    onIncrementProgressClick = onIncrementProgressClick,
                    onToggleFavoriteClick = onToggleFavoriteClick,
                    onEditClick = onEditClick,
                    onDeleteClick = onDeleteClick,
                    onReadMangaClick = { mapping ->
                        if (mapping == null) {
                            // Eşleme yok → "Oku" ekranda seçili başlık diline göre arar.
                            onSearchMangaByTitles?.invoke(
                                com.kitsugi.animelist.data.manga.MangaSearchQueryBuilder.buildQueries(
                                    titleLanguage = titleLanguage,
                                    searchLanguage = com.kitsugi.animelist.data.manga.MangaSearchLanguage.fromId(titleLanguage),
                                    followTitleLanguage = true,
                                    localTitle = displayEntry.title,
                                    englishTitle = displayEntry.titleEnglish,
                                    romajiTitle = displayEntry.title,
                                    nativeTitle = displayEntry.titleJapanese
                                )
                            )
                        } else {
                            onReadMangaClick?.invoke(mapping)
                        }
                    },
                    onUnlinkMangaClick = { viewModel.deleteMangaMapping(displayEntry.id) },
                    onGalleryOpen = { items, idx ->
                        activeGalleryCategory = items.getOrNull(idx)?.category ?: GalleryCategory.POSTER
                        if (!galleryLoading) activeGalleryItems = items
                        activeGalleryIndex = idx
                    },
                    leftPanelFocusRequester = lpFocus,
                    tabBarFocusRequester = tbFocus,
                    targetSeason = targetSeason
                )
            },
            floatingHeaderActions = {
                // Paylaş · Favori · Galeri → ortak üst çubuk aksiyon satırı
                KitsugiDetailHeaderActions(
                    accentColor = accentColor,
                    onShareClick = {
                        val url = com.kitsugi.animelist.utils.ShareUtils.buildMediaUrl(displayEntry)
                        com.kitsugi.animelist.utils.ShareUtils.shareText(context, displayEntry.title, url)
                    },
                    showFavourite = showFavouriteButton,
                    isFavourite = displayEntry.isFavorite,
                    onFavouriteClick = onToggleFavoriteClick,
                    showGallery = galleryItems.isNotEmpty() || !displayEntry.imageUrl.isNullOrBlank(),
                    galleryLoading = galleryLoading,
                    onGalleryClick = {
                        val clickedUrl = displayEntry.imageUrl
                        val index = if (!clickedUrl.isNullOrBlank()) {
                            galleryItems.indexOfFirst { item ->
                                item.url == clickedUrl || 
                                item.url.substringAfterLast("/") == clickedUrl.substringAfterLast("/")
                            }
                        } else -1
                        if (index >= 0) {
                            activeGalleryCategory = GalleryCategory.POSTER
                            if (!galleryLoading) activeGalleryItems = galleryItems
                            activeGalleryIndex = index
                        } else if (!clickedUrl.isNullOrBlank()) {
                            val coverItem = GalleryItem(
                                url = clickedUrl,
                                category = GalleryCategory.POSTER,
                                source = displayEntry.source
                            )
                            activeGalleryCategory = GalleryCategory.POSTER
                            if (!galleryLoading) activeGalleryItems = listOf(coverItem) + galleryItems
                            activeGalleryIndex = 0
                        } else {
                            val posterIndex = galleryItems.indexOfFirst { it.category == GalleryCategory.POSTER }.coerceAtLeast(0)
                            activeGalleryCategory = GalleryCategory.POSTER
                            if (!galleryLoading) activeGalleryItems = galleryItems
                            activeGalleryIndex = posterIndex
                        }
                    }
                )
            },
            portraitTopItems = { _, lpFocus, tbFocus ->
                item(key = "hero") {
                    EntryDetailLeftPanel(
                        entry = displayEntry,
                        detailState = detailState,
                        logoUrl = logoUrl,
                        galleryItems = galleryItems,
                        mangaMapping = mangaMapping,
                        resolvedTmdbId = resolvedTmdbId,
                        showAnimeLogos = showAnimeLogos,
                        showFavouriteButton = showFavouriteButton,
                        titleLanguage = titleLanguage,
                        blurAdultMedia = blurAdultMedia,
                        onBackClick = onBackClick,
                        onIncrementProgressClick = onIncrementProgressClick,
                        onToggleFavoriteClick = onToggleFavoriteClick,
                        onEditClick = onEditClick,
                        onDeleteClick = onDeleteClick,
                        onReadMangaClick = { mapping ->
                            if (mapping == null) {
                                // Eşleme yok → "Oku" ekranda seçili başlık diline göre arar.
                                onSearchMangaByTitles?.invoke(
                                    com.kitsugi.animelist.data.manga.MangaSearchQueryBuilder.buildQueries(
                                        titleLanguage = titleLanguage,
                                        searchLanguage = com.kitsugi.animelist.data.manga.MangaSearchLanguage.fromId(titleLanguage),
                                        followTitleLanguage = true,
                                        localTitle = displayEntry.title,
                                        englishTitle = displayEntry.titleEnglish,
                                        romajiTitle = displayEntry.title,
                                        nativeTitle = displayEntry.titleJapanese
                                    )
                                )
                            } else {
                                onReadMangaClick?.invoke(mapping)
                            }
                        },
                        onUnlinkMangaClick = { viewModel.deleteMangaMapping(displayEntry.id) },
                        onGalleryOpen = { items, idx ->
                            activeGalleryCategory = items.getOrNull(idx)?.category ?: GalleryCategory.POSTER
                            if (!galleryLoading) activeGalleryItems = items
                            activeGalleryIndex = idx
                        },
                        leftPanelFocusRequester = lpFocus,
                        tabBarFocusRequester = tbFocus,
                        targetSeason = targetSeason
                    )
                }
            },
            pageContent = { page ->
                if (page == wikiPageIndex) {
                    MediaWikiTabSlot(
                        viewModel = wikiViewModel,
                        accentColor = accentColor,
                        onOpenGallery = { items, index ->
                            activeGalleryCategory = items.getOrNull(index)?.category ?: GalleryCategory.OTHER
                            activeGalleryItems = items
                            activeGalleryIndex = index
                        }
                    )
                } else when (page) {
                    0 -> {
                        EntryOverviewTabSlot(
                            viewModel = viewModel,
                            entry = displayEntry,
                            detail = detailState,
                            originalSynopsis = originalSynopsis,
                            externalUrl = externalUrl,
                            onSearchQuery = onSearchQuery,
                            onStudioClick = onStudioClick,
                            onStaffClick = onStaffClick,
                            onMagazineClick = onMagazineClick,
                            onGenreClick = onSearchByGenre,
                            onTagClick = onSearchByTag,
                            onSearchBySeason = onSearchBySeason,
                            preferredTranslator = preferredTranslator,
                            settingsSlice = settingsSlice,
                            mdbListShowImdb = mdbListShowImdb,
                            mdbListShowTomatoes = mdbListShowTomatoes,
                            mdbListShowMetacritic = mdbListShowMetacritic,
                            mdbListShowAudience = mdbListShowAudience,
                            mdbListShowLetterboxd = mdbListShowLetterboxd,
                            mdbListShowTmdb = mdbListShowTmdb,
                            mdbListShowTrakt = mdbListShowTrakt,
                            onSettingsClick = if (settingsDataStore != null) {
                                { showIntegrationsDialog = true }
                            } else null,
                            galleryItems = galleryItems,
                            galleryLoading = galleryLoading,
                            onOpenGalleryItems = { items, index ->
                                if (!galleryLoading) activeGalleryItems = items
                                activeGalleryIndex = index
                            }
                        )
                    }
                    1 -> {
                        // Resimler sekmesi — tüm galeri öğeleri (Fanart.tv, TMDB, AniList posterler)
                        Column(
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 0.dp, vertical = 14.dp)
                        ) {
                            if (galleryLoading && galleryItems.isEmpty()) {
                                DetailGalleryLoadingCard()
                            } else if (galleryItems.isNotEmpty()) {
                                DetailGalleryCard(
                                    items = galleryItems,
                                    isAdult = displayEntry.isAdult,
                                    onItemClick = { index ->
                                        if (!galleryLoading) activeGalleryItems = galleryItems
                                        activeGalleryIndex = index
                                    },
                                    onOpenGallery = { category ->
                                        val startIndex = if (category == null) 0
                                        else galleryItems.indexOfFirst { it.category == category }.coerceAtLeast(0)
                                        activeGalleryCategory = category
                                        if (!galleryLoading) activeGalleryItems = galleryItems
                                        activeGalleryIndex = startIndex
                                    },
                                    onOpenSourceGallery = { source ->
                                        val sourceItems = galleryItems.filter { it.source.equals(source, ignoreCase = true) }
                                        if (sourceItems.isNotEmpty()) {
                                            activeGalleryCategory = sourceItems.first().category
                                            if (!galleryLoading) activeGalleryItems = sourceItems
                                            activeGalleryIndex = 0
                                        }
                                    }
                                )
                            } else {
                                // Galeri yüklenirken veya boşken bilgi mesajı
                                androidx.compose.foundation.layout.Box(
                                    modifier = androidx.compose.ui.Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (galleryLoading) stringResource(R.string.ui_images_loading) else stringResource(R.string.ui_no_image_available),
                                        color = KitsugiColors.TextMuted,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                    2 -> {
                        // KASMA/ÇÖKME FIX (2026-11-05): gereksiz `selectedTab` kontrolü
                        // kaldırıldı; `page == 2` zaten Videolar sekmesidir.
                        EntryVideoTabSlot(
                            viewModel = viewModel,
                            detail = detailState,
                            mediaTitle = displayEntry.title,
                            primarySource = entry.source
                        )
                    }
                    3 -> EntryCharactersTabSlot(
                        viewModel = viewModel,
                        onCharacterClick = onCharacterClick,
                        onStaffClick = onStaffClick,
                        onMediaClick = onMediaClick,
                        titleLanguage = titleLanguage,
                        staffNameLanguage = staffNameLanguage
                    )
                    4 -> EntryStaffTabSlot(
                        viewModel = viewModel,
                        onStaffClick = onStaffClick,
                        titleLanguage = titleLanguage,
                        staffNameLanguage = staffNameLanguage
                    )
                    5 -> EntryRecommendationsTabSlot(
                        viewModel = viewModel,
                        titleLanguage = titleLanguage,
                        blurAdultMedia = blurAdultMedia,
                        onRelationClick = onRelationClick
                    )
                    6 -> EntryRelationsTabSlot(
                        viewModel = viewModel,
                        titleLanguage = titleLanguage,
                        blurAdultMedia = blurAdultMedia,
                        onRelationClick = onRelationClick
                    )
                    7 -> EntryStatsTabSlot(viewModel = viewModel)
                    8 -> EntryReviewsTabSlot(
                        viewModel = viewModel,
                        source = displayEntry.source,
                        externalId = if (displayEntry.source.equals("simkl", true)) {
                            displayEntry.simklId?.takeIf { it > 0 } ?: displayEntry.id
                        } else {
                            displayEntry.malId ?: displayEntry.id
                        },
                        mediaType = displayEntry.type,
                        apiClient = apiClient,
                        tmdbId = displayEntry.tmdbId ?: detailState?.tmdbId ?: resolvedTmdbId,
                        realMalId = detailState?.realMalId ?: displayEntry.malId?.takeIf { m ->
                            m in 1 until 100_000_000 &&
                                !displayEntry.source.equals("shikimori", true) &&
                                !displayEntry.source.equals("bangumi", true) &&
                                !displayEntry.source.equals("tmdb", true) &&
                                (!displayEntry.source.equals("simkl", true) || m != displayEntry.simklId)
                        },
                        titleLanguage = titleLanguage,
                        onUserProfileClick = onUserProfileClick,
                        preferredTranslator = preferredTranslator
                    )
                    9 -> EntryEpisodesTabSlot(
                        viewModel = viewModel,
                        entry = displayEntry,
                        detailState = detailState,
                        targetSeason = targetSeason,
                        onEpisodeOptionsRequested = { episode ->
                            activeEpisodeForOptions = episode
                        }
                    )
                }
            }
        )
    }


        if (activeGalleryItems.isNotEmpty()) {
            KitsugiImageGalleryDialog(
                galleryItems = activeGalleryItems,
                initialIndex = activeGalleryIndex,
                initialCategory = activeGalleryCategory ?: activeGalleryItems.getOrNull(activeGalleryIndex)?.category,
                title = displayEntry.title,
                isAdult = displayEntry.isAdult,
                onDismiss = { 
                    activeGalleryItems = emptyList()
                    activeGalleryCategory = null
                }
            )
        }

        activeEpisodeForOptions?.let { ep ->
            val streamIds = displayEntry.toStreamMediaIds(
                resolvedRealMalId = detailState?.realMalId,
                resolvedTmdbId = displayEntry.tmdbId ?: detailState?.tmdbId ?: resolvedTmdbId
            )
            KitsugiEpisodeOptionsDialog(
                animeTitle = displayEntry.title,
                episodeNumber = ep.episodeNumber,
                episodeTitle = ep.title,
                originalUrl = ep.url,
                siteName = ep.site,
                malId = streamIds.malId,
                aniListId = streamIds.aniListId,
                tmdbId = streamIds.tmdbId,
                bangumiId = streamIds.bangumiId,
                imdbId = detailState?.externalLinks?.firstOrNull { it.url.contains("imdb.com", ignoreCase = true) }
                    ?.url?.let { Regex("(?i)tt[0-9]{5,12}").find(it)?.value?.lowercase() },
                posterUrl = displayEntry.imageUrl,
                titleEnglish = detailState?.titleEnglish,
                titleRomaji = detailState?.titleRomaji,
                titleNative = detailState?.titleNative,
                synonyms = detailState?.synonyms.orEmpty(),
                startYear = displayEntry.year,
                isMovie = displayEntry.type == com.kitsugi.animelist.model.MediaType.Movie,
                mediaType = displayEntry.type,
                seasonNumber = ep.seasonNumber ?: targetSeason,
                onDismiss = { activeEpisodeForOptions = null }
            )
        }

        if (showIntegrationsDialog && settingsDataStore != null) {
            DetailIntegrationsSettingsDialog(
                settingsDataStore = settingsDataStore,
                coroutineScope = coroutineScope,
                onDismiss = { showIntegrationsDialog = false }
            )
        }
    }
    }

@Composable
private fun EntryOverviewTabSlot(
    viewModel: MediaEntryDetailViewModel,
    entry: MediaEntry,
    detail: com.kitsugi.animelist.data.remote.KitsugiMediaDetail?,
    originalSynopsis: String?,
    externalUrl: String?,
    onSearchQuery: (String) -> Unit,
    onStudioClick: (Int, String, String?, String?) -> Unit,
    onStaffClick: (Int, String, String?, String?) -> Unit,
    onMagazineClick: (Int, String) -> Unit,
    onGenreClick: (String) -> Unit,
    onTagClick: (com.kitsugi.animelist.data.remote.KitsugiTag) -> Unit,
    onSearchBySeason: (com.kitsugi.animelist.ui.screens.search.DetailSeasonMetadata) -> Unit,
    preferredTranslator: String,
    settingsSlice: DetailSettingsSlice?,
    mdbListShowImdb: Boolean,
    mdbListShowTomatoes: Boolean,
    mdbListShowMetacritic: Boolean,
    mdbListShowAudience: Boolean,
    mdbListShowLetterboxd: Boolean,
    mdbListShowTmdb: Boolean,
    mdbListShowTrakt: Boolean,
    onSettingsClick: (() -> Unit)?,
    galleryItems: List<GalleryItem>,
    galleryLoading: Boolean,
    onOpenGalleryItems: (List<GalleryItem>, Int) -> Unit
) {
    val synopsisState by viewModel.synopsisState.collectAsState()
    val translatedSynopsis by viewModel.translatedSynopsis.collectAsState()
    val showSynopsisOriginal by viewModel.showSynopsisOriginal.collectAsState()
    val mdbListRatings by viewModel.mdbListRatings.collectAsState()
    val mdbListLoading by viewModel.mdbListLoading.collectAsState()

    val rawSynopsisForToggle = originalSynopsis
    val translatedForToggle = translatedSynopsis
    val synopsisHasTranslation = translatedForToggle != null && !rawSynopsisForToggle.isNullOrBlank() &&
        !com.kitsugi.animelist.utils.translationMatchesSource(rawSynopsisForToggle, translatedForToggle)
    val displayedSynopsisState = if (showSynopsisOriginal && !originalSynopsis.isNullOrBlank()) {
        SynopsisState.Success(originalSynopsis)
    } else {
        synopsisState
    }

    EntryDetailOverviewTab(
        entry = entry,
        detail = detail,
        synopsisState = displayedSynopsisState,
        originalSynopsis = originalSynopsis,
        externalUrl = externalUrl,
        onSearchQuery = onSearchQuery,
        onStudioClick = onStudioClick,
        onStaffClick = onStaffClick,
        onMagazineClick = onMagazineClick,
        onGenreClick = onGenreClick,
        onTagClick = onTagClick,
        onSearchBySeason = onSearchBySeason,
        preferredTranslator = preferredTranslator,
        mdbListRatings = mdbListRatings,
        mdbListLoading = mdbListLoading,
        mdbListShowImdb = settingsSlice?.mdbListShowImdb ?: mdbListShowImdb,
        mdbListShowTomatoes = settingsSlice?.mdbListShowTomatoes ?: mdbListShowTomatoes,
        mdbListShowMetacritic = settingsSlice?.mdbListShowMetacritic ?: mdbListShowMetacritic,
        mdbListShowAudience = settingsSlice?.mdbListShowAudience ?: mdbListShowAudience,
        mdbListShowLetterboxd = settingsSlice?.mdbListShowLetterboxd ?: mdbListShowLetterboxd,
        mdbListShowTmdb = settingsSlice?.mdbListShowTmdb ?: mdbListShowTmdb,
        mdbListShowTrakt = settingsSlice?.mdbListShowTrakt ?: mdbListShowTrakt,
        onSettingsClick = onSettingsClick,
        onImageGalleryRequest = { urls, index ->
            onOpenGalleryItems(
                urls.map { url -> GalleryItem(url = url, category = GalleryCategory.OTHER, source = "Açıklama") },
                index
            )
        },
        galleryItems = galleryItems,
        galleryLoading = galleryLoading,
        onGalleryItemRequest = onOpenGalleryItems,
        onTranslateClick = { viewModel.translateSynopsis(entry) },
        synopsisHasTranslation = synopsisHasTranslation,
        showSynopsisOriginal = showSynopsisOriginal,
        onToggleSynopsisOriginal = { viewModel.toggleSynopsisOriginal() }
    )
}

@Composable
private fun EntryCharactersTabSlot(
    viewModel: MediaEntryDetailViewModel,
    onCharacterClick: (KitsugiCharacter) -> Unit,
    onStaffClick: (Int, String, String?, String?) -> Unit,
    onMediaClick: (Int, String, String) -> Unit,
    titleLanguage: String,
    staffNameLanguage: String
) {
    val state by viewModel.charactersState.collectAsState()
    CharactersTabContent(
        state = state,
        onCharacterClick = onCharacterClick,
        onStaffClick = onStaffClick,
        onMediaClick = onMediaClick,
        titleLanguage = titleLanguage,
        staffNameLanguage = staffNameLanguage
    )
}

@Composable
private fun EntryStaffTabSlot(
    viewModel: MediaEntryDetailViewModel,
    onStaffClick: (Int, String, String?, String?) -> Unit,
    titleLanguage: String,
    staffNameLanguage: String
) {
    val state by viewModel.staffState.collectAsState()
    StaffTabContent(
        state = state,
        onStaffClick = onStaffClick,
        titleLanguage = titleLanguage,
        staffNameLanguage = staffNameLanguage
    )
}

@Composable
private fun EntryRecommendationsTabSlot(
    viewModel: MediaEntryDetailViewModel,
    titleLanguage: String,
    blurAdultMedia: Boolean,
    onRelationClick: (JikanSearchResult) -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.recommendationsState.collectAsState()
    RecommendationsTabContent(
        state = state,
        titleLanguage = titleLanguage,
        blurAdultMedia = blurAdultMedia,
        onRecommendationClick = { rel ->
            val typeLabel = context.getString(rel.mediaType.labelRes())
            onRelationClick(
                JikanSearchResult(
                    malId = rel.malId,
                    title = rel.title,
                    subtitle = "${rel.relationType}, $typeLabel",
                    type = rel.mediaType,
                    total = null,
                    score = null,
                    isAdult = rel.isAdult,
                    imageUrl = rel.imageUrl,
                    year = null,
                    realMalId = rel.malId,
                    source = rel.source,
                    titleEnglish = rel.titleEnglish,
                    titleJapanese = rel.titleJapanese,
                    titleRomaji = rel.titleRomaji,
                    bangumiSubjectType = rel.bangumiSubjectType
                )
            )
        }
    )
}

@Composable
private fun EntryRelationsTabSlot(
    viewModel: MediaEntryDetailViewModel,
    titleLanguage: String,
    blurAdultMedia: Boolean,
    onRelationClick: (JikanSearchResult) -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.relationsState.collectAsState()
    RelationsTabContent(
        state = state,
        titleLanguage = titleLanguage,
        blurAdultMedia = blurAdultMedia,
        onRelationClick = { rel ->
            val typeLabel = context.getString(rel.mediaType.labelRes())
            onRelationClick(
                JikanSearchResult(
                    malId = rel.malId,
                    title = rel.title,
                    subtitle = "${rel.relationType}, $typeLabel",
                    type = rel.mediaType,
                    total = null,
                    score = null,
                    isAdult = rel.isAdult,
                    imageUrl = rel.imageUrl,
                    year = null,
                    realMalId = rel.malId,
                    source = rel.source,
                    titleEnglish = rel.titleEnglish,
                    titleJapanese = rel.titleJapanese,
                    titleRomaji = rel.titleRomaji,
                    bangumiSubjectType = rel.bangumiSubjectType
                )
            )
        }
    )
}

@Composable
private fun EntryStatsTabSlot(viewModel: MediaEntryDetailViewModel) {
    val state by viewModel.statsState.collectAsState()
    StatsTabContent(state = state)
}

@Composable
private fun EntryReviewsTabSlot(
    viewModel: MediaEntryDetailViewModel,
    source: String,
    externalId: Int,
    mediaType: MediaType,
    apiClient: JikanApiClient,
    tmdbId: Int?,
    realMalId: Int?,
    titleLanguage: String,
    onUserProfileClick: (Int, String, String?, String) -> Unit,
    preferredTranslator: String
) {
    val state by viewModel.reviewsState.collectAsState()
    ReviewsTabContent(
        state = state,
        source = source,
        externalId = externalId,
        mediaType = mediaType,
        apiClient = apiClient,
        tmdbId = tmdbId,
        realMalId = realMalId,
        titleLanguage = titleLanguage,
        onUserProfileClick = onUserProfileClick,
        preferredTranslator = preferredTranslator
    )
}

@Composable
private fun EntryEpisodesTabSlot(
    viewModel: MediaEntryDetailViewModel,
    entry: MediaEntry,
    detailState: com.kitsugi.animelist.data.remote.KitsugiMediaDetail?,
    targetSeason: Int,
    onEpisodeOptionsRequested: (KitsugiStreamingEpisode) -> Unit
) {
    val state by viewModel.episodesState.collectAsState()
    val episodeRatings by viewModel.episodeRatings.collectAsState()
    EntryDetailEpisodesTab(
        entry = entry,
        detailState = detailState,
        state = state,
        episodeRatings = episodeRatings,
        targetSeason = targetSeason,
        onSeasonSelected = { newSeason ->
            viewModel.setTargetSeason(newSeason, entry)
        },
        onEpisodeOptionsRequested = onEpisodeOptionsRequested
    )
}



internal fun buildExternalUrl(entry: MediaEntry): String? =
    com.kitsugi.animelist.utils.ShareUtils.buildMediaUrl(entry)

internal fun progressUnit(entry: MediaEntry): String {
    return when (entry.type) {
        MediaType.Anime -> "bölüm"
        MediaType.Manga -> "chapter"
        else -> "bölüm"
    }
}

internal fun entryProgressText(entry: MediaEntry): String {
    val totalText = entry.total?.toString() ?: "?"
    return "${entry.progress}/$totalText ${progressUnit(entry)}"
}

internal fun scoreText(entry: MediaEntry): String {
    return if (entry.score == null) {
        "-"
    } else {
        "${entry.score}/10"
    }
}

@Composable
private fun EntryVideoTabSlot(
    viewModel: MediaEntryDetailViewModel,
    detail: com.kitsugi.animelist.data.remote.KitsugiMediaDetail?,
    mediaTitle: String,
    primarySource: String
) {
    val videoItems by viewModel.videoItems.collectAsState()
    val videosLoading by viewModel.videosLoading.collectAsState()
    KitsugiMediaVideosTab(
        detail = detail,
        mediaTitle = mediaTitle,
        primarySource = primarySource,
        aggregatedVideos = videoItems,
        isLoading = videosLoading
    )
}


