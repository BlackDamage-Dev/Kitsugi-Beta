@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.kitsugi.animelist.ui.screens.detail
import com.kitsugi.animelist.ui.components.KitsugiButton
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.CompositionLocalProvider
import com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults
import com.kitsugi.animelist.ui.utils.dpadVerticalFastScroll
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.remember
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.kitsugi.animelist.utils.parseToMediaType
import com.kitsugi.animelist.utils.PreferenceHelpers
import com.kitsugi.animelist.utils.MediaTitleResolver
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.IconButton
import com.kitsugi.animelist.ui.theme.gradient.Icon
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
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.data.remote.DetailCache
import com.kitsugi.animelist.data.remote.ApiSearchSelection
import com.kitsugi.animelist.data.remote.JikanApiClient
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.data.remote.toStreamMediaIds
import com.kitsugi.animelist.data.remote.canonicalMediaSourceId
import com.kitsugi.animelist.data.remote.matchesInSource
import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayTitle
import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayScore
import com.kitsugi.animelist.data.remote.KitsugiCharacter
import com.kitsugi.animelist.data.remote.KitsugiEpisodeRatingsRepository
import com.kitsugi.animelist.data.remote.KitsugiMediaDetail
import com.kitsugi.animelist.data.remote.KitsugiRelation
import com.kitsugi.animelist.data.remote.KitsugiReview
import com.kitsugi.animelist.data.remote.KitsugiScoreStat
import com.kitsugi.animelist.data.remote.KitsugiStaff
import com.kitsugi.animelist.data.remote.KitsugiStats
import com.kitsugi.animelist.data.remote.KitsugiStudio
import com.kitsugi.animelist.data.remote.KitsugiStreamingEpisode
import com.kitsugi.animelist.data.local.TranslationManager
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.utils.KitsugiTranslateUtils.openTranslator
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.ui.components.KitsugiPageEnter
import com.kitsugi.animelist.ui.components.KitsugiCinematicLoadingScreen
import com.kitsugi.animelist.ui.components.KitsugiEpisodeOptionsDialog
import com.kitsugi.animelist.ui.components.KitsugiImageGalleryDialog
import com.kitsugi.animelist.data.remote.GalleryItem
import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.ui.components.KitsugiStreamSelectorBottomSheet
import com.kitsugi.animelist.ui.screens.fullscreen.KitsugiFullscreenPlayerActivity
import com.kitsugi.animelist.ui.screens.stream.KitsugiStreamActivity
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.ui.components.KitsugiIntegrationsSettingsDialog
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.AlertDialog
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kitsugi.animelist.data.fandom.FandomMediaQueries
import com.kitsugi.animelist.ui.screens.detail.wiki.FandomMediaWikiViewModel
import com.kitsugi.animelist.ui.screens.detail.wiki.MediaWikiTabSlot
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import com.kitsugi.animelist.ui.theme.LocalIsTv
import com.kitsugi.animelist.utils.KitsugiStrings

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApiResultDetailPage(
    result: JikanSearchResult,
    existingEntry: MediaEntry?,
    /**
     * Detay zenginleştirmesi (realMalId / tmdbId) tamamlandıktan SONRA da liste kontrolü
     * yapılabilmesi için geri çağrı. Yoksa buton "Listeye Ekle" derken ekleme akışı
     * "zaten listende var" diyebiliyordu (kimlikler tıklama anında daha yeni çözülmüş oluyor).
     */
    findExistingEntry: ((JikanSearchResult) -> MediaEntry?)? = null,
    onBackClick: () -> Unit,
    onAddClick: (ApiSearchSelection) -> Unit,
    /** "Düzenle": kayıt + sayfanın kaynağı (bkz. [ApiDetailLeftPanel.onEditClick]). */
    onEditClick: (MediaEntry, String) -> Unit = { _, _ -> },
    onRelationClick: (JikanSearchResult) -> Unit,
    onCharacterClick: (KitsugiCharacter) -> Unit,
    onStaffClick: (Int, String, String?, String?) -> Unit,
    onStudioClick: (Int, String, String?, String?) -> Unit,
    onMagazineClick: (Int, String) -> Unit = { _, _ -> },
    onSearchQuery: (String) -> Unit = {},
    onSearchByGenre: (String) -> Unit = {},
    onSearchByTag: (com.kitsugi.animelist.data.remote.KitsugiTag) -> Unit = {},
    onSearchBySeason: (com.kitsugi.animelist.ui.screens.search.DetailSeasonMetadata) -> Unit = {},
    titleLanguage: String = "ENGLISH",
    /**
     * Kişi/karakter adı dili (`ROMAJI` / `ENGLISH` / `NATIVE`). Ayar ekranındaki ayrı
     * "personel adı dili" tercihidir; sekmelere açıkça geçirilir ki başlık dili ile
     * karışmasın ve ayar değiştiğinde tablo yeniden çizilsin.
     */
    staffNameLanguage: String = com.kitsugi.animelist.data.settings.KitsugiContentPrefs.staffNameLanguage,
    scoreFormat: String = "POINT_10",
    hideScores: Boolean = false,
    showAnimeLogos: Boolean = false,
    isAniListConnected: Boolean = false,
    isMalConnected: Boolean = false,
    isSimklConnected: Boolean = false,
    isBangumiConnected: Boolean = false,
    isTmdbConnected: Boolean = false,
    onLoginAniList: () -> Unit = {},
    onLoginMal: () -> Unit = {},
    onLoginSimkl: () -> Unit = {},
    onLoginBangumi: () -> Unit = {},
    onReadMangaClick: (() -> Unit)? = null,
    mdbListShowImdb: Boolean = true,
    mdbListShowTomatoes: Boolean = true,
    mdbListShowMetacritic: Boolean = true,
    mdbListShowAudience: Boolean = false,
    mdbListShowLetterboxd: Boolean = false,
    mdbListShowTmdb: Boolean = false,
    mdbListShowTrakt: Boolean = false,
    settingsDataStore: SettingsDataStore? = null,
    onUserProfileClick: (userId: Int, username: String, avatarUrl: String?, source: String) -> Unit = { _, _, _, _ -> },
    onToggleFavoriteClick: ((ApiSearchSelection) -> Unit)? = null
) {
    val accentColor = LocalKitsugiAccent.current
    val isTv = LocalIsTv.current
    val isTvDevice = com.kitsugi.animelist.ui.theme.LocalIsTvDevice.current
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val apiClient = remember { JikanApiClient() }

    val settingsSlice = rememberDetailSettingsSlice(settingsDataStore)
    val blurAdultMedia = settingsSlice?.blurAdultMedia ?: false
    var showIntegrationsDialog by remember { mutableStateOf(false) }
    var showAuthWarningDialog by remember { mutableStateOf(false) }

    // Obtain ViewModel
    val viewModel: ApiResultDetailViewModel = viewModel(key = "api_${result.source}_${result.malId}_${result.type.name}")
    // Fandom Wiki (Faz 2): arama/keşfet sonucu detayında da wiki sekmesi vardır.
    val wikiViewModel: FandomMediaWikiViewModel = viewModel(key = "fandom_media_${result.source}_${result.malId}")
    val wikiPrefs by wikiViewModel.prefs.collectAsState()

    // Load result in ViewModel
    LaunchedEffect(result.source, result.malId, result.type, showAnimeLogos) {
        viewModel.loadResult(result, showAnimeLogos)
    }

    // PERF: Yalnızca sayfa iskeleti, vitrin ve sekme çubuğunun gerçekten ihtiyaç duyduğu
    // akışlar kök kapsamda dinlenir. Sekmeye özel akışlar (bölüm puanları, karakterler,
    // ekip, öneriler, ilişkiler, istatistikler, yorumlar, özet çevirisi, MDBList) ilgili
    // sekme slotlarının kendi RestartGroup'unda toplanır; böylece arka plan ağ yanıtları
    // geldiğinde tüm detay sayfası yeniden compose olmaz.
    val detailState by viewModel.detailState.collectAsState()
    val detailLoading by viewModel.detailLoading.collectAsState()
    val detailErrorCode by viewModel.detailErrorCode.collectAsState()
    val logoUrl by viewModel.logoUrl.collectAsState()
    val resolvedTmdbId by viewModel.resolvedTmdbId.collectAsState()
    val targetSeason by viewModel.targetSeason.collectAsState()
    val mdbImdbId by remember(viewModel) {
        viewModel.mdbListRatings.map { it?.imdbId }.distinctUntilChanged()
    }.collectAsState(initial = null)
    val galleryLoading by viewModel.galleryLoading.collectAsState()
    // Vitrin görsel planı sayfa açılmadan ÖNCE çözülür (TMDB/Fanart.tv en kaliteli sıra).
    val heroArtwork by viewModel.heroArtwork.collectAsState()
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

    val displayResult = remember(result, detailState, titleLanguage) {
        val detail = detailState
        if (detail != null) {
            val effTitleEnglish = detail.titleEnglish?.takeIf { it.isNotBlank() } ?: result.titleEnglish
            val effTitleJapanese = (detail.titleJapanese ?: detail.titleNative)?.takeIf { it.isNotBlank() } ?: result.titleJapanese
            val effTitleRomaji = detail.titleRomaji?.takeIf { it.isNotBlank() }
            val rawTitle = if (result.title.isBlank() || result.title == KitsugiStrings.get(R.string.loading)) (detail.title ?: result.title) else result.title
            val chosenTitle = if (MediaTitleResolver.isLatinPreferredSource(result.source)) {
                // TMDB/Simkl: yerelleştirilmiş (Türkçe) → İngilizce → Romaji. CJK başlık ekrana düşmez;
                // yalnızca kullanıcı açıkça "NATIVE" seçtiyse orijinal başlık gösterilir.
                when (titleLanguage) {
                    "NATIVE", "JAPANESE_STAFF" -> effTitleJapanese ?: rawTitle
                    "ENGLISH" -> MediaTitleResolver.resolveEnglish(rawTitle, effTitleEnglish, effTitleRomaji, effTitleJapanese) ?: rawTitle
                    else -> MediaTitleResolver.resolve(rawTitle, effTitleEnglish, effTitleRomaji, effTitleJapanese)
                }
            } else {
                // Bangumi'de English ve romaji farklı alanlardır. Önceki kod her tercih için
                // romaji seçiyor, English/Native ayarını başlık ekranında etkisiz bırakıyordu.
                PreferenceHelpers.getDisplayTitle(
                    title = rawTitle,
                    titleEnglish = effTitleEnglish,
                    titleJapanese = effTitleJapanese,
                    titleLanguage = titleLanguage,
                    titleRomaji = effTitleRomaji
                )
            }

            result.copy(
                title = chosenTitle,
                titleEnglish = effTitleEnglish,
                titleJapanese = effTitleJapanese,
                titleRomaji = effTitleRomaji,
                // Liste kayıtlarında tür her zaman taşınmaz (Kitsu/Shikimori listeleri "Anime"
                // derler). Detayın bildiği tür (ör. Shikimori `kind=movie`) hero etiketini,
                // bölüm sayacını ve film/dizi akışlarını doğru türe çeker.
                type = detail.type ?: result.type,
                imageUrl = if (!detail.imageUrl.isNullOrBlank()) detail.imageUrl else result.imageUrl,
                score = result.score ?: detail.score,
                year = result.year ?: detail.year,
                total = result.total ?: detail.total,
                isAdult = result.isAdult || detail.isAdult,
                realMalId = result.realMalId ?: detail.realMalId,
                tmdbId = result.tmdbId ?: detail.tmdbId
            )
        } else {
            result
        }
    }

    val externalUrl = buildExternalUrl(displayResult)
    val malCrossUrl = buildMalCrossUrl(displayResult)

    val sourceId = remember(displayResult.source) { canonicalMediaSourceId(displayResult.source) }
    val isSourceAniList = sourceId == "anilist"
    val isSourceBangumi = sourceId == "bangumi"
    val isConnected = remember(
        displayResult,
        sourceId,
        isAniListConnected,
        isMalConnected,
        isSimklConnected,
        isBangumiConnected,
        isTmdbConnected
    ) {
        val hasRealMalId = displayResult.realMalId != null ||
            (displayResult.malId != null && displayResult.malId > 0 && displayResult.malId < 100_000_000)
        when {
            // Provider membership takes precedence over the media type. A Bangumi film belongs
            // to the Bangumi list, not to Simkl merely because it is a movie.
            sourceId == "bangumi" -> isBangumiConnected
            sourceId == "anilist" -> isAniListConnected || (isMalConnected && hasRealMalId)
            sourceId == "mal" || sourceId == "jikan" -> isMalConnected || isAniListConnected
            sourceId == "simkl" || sourceId == "tmdb" || sourceId == "tmdb_account" -> isSimklConnected || isTmdbConnected
            displayResult.type == MediaType.TvShow || displayResult.type == MediaType.Movie -> isSimklConnected || isTmdbConnected
            else -> true
        }
    }

    val showFavouriteButton = !isSourceBangumi && (isSourceAniList || isAniListConnected)
    // Buton durumu ("Düzenle" / "Listeye Ekle") yalnızca aynı sağlayıcının üyeliğini temsil eder.
    val effectiveExistingEntry = existingEntry?.takeIf { it.matchesInSource(displayResult) }
        ?: findExistingEntry?.invoke(displayResult)?.takeIf { it.matchesInSource(displayResult) }
    val isFavorite = effectiveExistingEntry?.isFavorite ?: (detailState?.isFavourite ?: false)

    // Wiki sorgusu: kimlikler (gerçek MAL/TMDB) detayla birlikte netleşir.
    val wikiQuery = remember(result, displayResult, detailState, resolvedTmdbId, mdbImdbId) {
        val imdbId = mdbImdbId
            ?: detailState?.externalLinks?.firstOrNull { it.site.contains("IMDb", ignoreCase = true) || it.url.contains("imdb.com", ignoreCase = true) }
                ?.url?.let { Regex("(?i)tt[0-9]{5,12}").find(it)?.value }
        val wikidataId = detailState?.externalLinks
            ?.firstOrNull { it.site.contains("Wikidata", ignoreCase = true) || it.url.contains("wikidata.org", ignoreCase = true) }
            ?.url?.let { Regex("(?i)Q[0-9]+").find(it)?.value }
        FandomMediaQueries.fromMedia(
            source = sourceId,
            mediaId = displayResult.malId,
            malId = displayResult.realMalId ?: detailState?.realMalId,
            tmdbId = displayResult.tmdbId ?: detailState?.tmdbId ?: resolvedTmdbId,
            isManga = displayResult.type == MediaType.Manga,
            isMovie = displayResult.type == MediaType.Movie,
            isTvShow = displayResult.type == MediaType.TvShow,
            releaseYear = displayResult.year ?: detailState?.year,
            imdbId = imdbId,
            wikidataId = wikidataId,
            format = detailState?.format,
            rawFormat = detailState?.rawFormat,
            subtitle = displayResult.subtitle,
            titles = (
                listOfNotNull(
                    displayResult.titleEnglish,
                    displayResult.title,
                    displayResult.titleRomaji,
                    displayResult.titleJapanese,
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

    // State for tabs
    val isAnime = result.type == MediaType.Anime
    val hasTvEpisodes = isAnime || result.type == MediaType.TvShow
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

    var activeEpisodeForOptions by remember { mutableStateOf<KitsugiStreamingEpisode?>(null) }
    var showWatchDialog by remember { mutableStateOf(false) }
    var watchEpisodeInput by remember { mutableStateOf("") }
    var showWatchStreamSelector by remember { mutableStateOf<Int?>(null) }

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

    val allImages = remember(displayResult.imageUrl, detailState?.pictures) {
        buildList {
            if (!displayResult.imageUrl.isNullOrBlank()) {
                add(displayResult.imageUrl)
            }
            detailState?.pictures?.let { addAll(it) }
        }.distinct()
    }
    var activeGalleryItems by remember { mutableStateOf<List<GalleryItem>>(emptyList()) }
    var activeGalleryIndex by remember { mutableStateOf(0) }
    var activeGalleryCategory by remember { mutableStateOf<GalleryCategory?>(null) }
    val galleryItems by viewModel.galleryItems.collectAsState()

    val listState = rememberLazyListState()
    val tabListState = rememberLazyListState()
    val density = LocalDensity.current
    // TV odak highway — sol panel ↔ sağ panel tab bar
    val leftPanelFocusRequester = remember { FocusRequester() }
    val tabBarFocusRequester = remember { FocusRequester() }

    // Yeni bir sonuç'a geçildiğinde tab'ı sıfırla — geri tuşuyla dönüşlerde SIFIRLAMAZ.
    var lastProcessedTrigger by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(pageResetTrigger) {
        if (pageResetTrigger > 0 && pageResetTrigger != lastProcessedTrigger) {
            lastProcessedTrigger = pageResetTrigger
            pagerState.scrollToPage(0)
        }
    }

    // Call loadTab when tab changes
    LaunchedEffect(
        result.source,
        result.malId,
        result.type,
        displayResult.realMalId,
        detailState?.tmdbId,
        resolvedTmdbId,
        detailState == null,
        currentPageFlow
    ) {
        currentPageFlow.collect { page ->
            if (page != wikiPageIndex) {
                detailDataTabIndexForPage(page)?.let { dataTabIndex ->
                    viewModel.loadTab(dataTabIndex, result, displayResult.realMalId)
                }
            }
        }
    }

    LaunchedEffect(detailLoading) {
        if (!detailLoading) {
            try {
                leftPanelFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore focus request failures if views are not yet attached
            }
        }
    }

    val synopsisForSave = detailState?.synopsis

    KitsugiPageEnter {
        DetailPageScaffold(
            title = displayResult.title,
            isLoading = isLoading,
            isError = !detailLoading && detailState == null,
            isRefreshing = detailLoading,
            onRefresh = { viewModel.loadResult(displayResult, showAnimeLogos, forceRefresh = true) },
            onBackClick = onBackClick,
            tabs = tabs,
            pagerState = pagerState,
            listState = listState,
            tabListState = tabListState,
            loadingScreen = {
                KitsugiCinematicLoadingScreen(
                    title = displayResult.title,
                    imageUrl = displayResult.imageUrl,
                    logoUrl = logoUrl,
                    isAdult = displayResult.isAdult,
                    blurAdultMedia = blurAdultMedia,
                    source = displayResult.source,
                    onBackClick = onBackClick,
                    platformErrorCode = detailErrorCode,
                )
            },
            errorScreen = {
                DataUnavailableScreen(
                    title = displayResult.title,
                    onBackClick = onBackClick,
                    onRetryClick = { viewModel.loadResult(result, showAnimeLogos) },
                    platformErrorCode = detailErrorCode,
                )
            },
            leftPanel = { lpFocus, tbFocus ->
                ApiDetailLeftPanel(
                    displayResult = displayResult,
                    detailState = detailState,
                    resolvedTmdbId = resolvedTmdbId,
                    galleryItems = galleryItems,
                    existingEntry = effectiveExistingEntry,
                    isConnected = isConnected,
                    isFavorite = isFavorite,
                    showFavouriteButton = showFavouriteButton,
                    synopsisForSave = synopsisForSave,
                    titleLanguage = titleLanguage,
                    scoreFormat = scoreFormat,
                    hideScores = hideScores,
                    showAnimeLogos = showAnimeLogos,
                    logoUrl = logoUrl,
                    blurAdultMedia = blurAdultMedia,
                    onBackClick = onBackClick,
                    onAddClick = onAddClick,
                    onEditClick = onEditClick,
                    onToggleFavoriteClick = onToggleFavoriteClick,
                    onReadMangaClick = onReadMangaClick,
                    onGalleryOpen = { items, idx ->
                        activeGalleryCategory = items.getOrNull(idx)?.category ?: GalleryCategory.POSTER
                        if (!galleryLoading) activeGalleryItems = items
                        activeGalleryIndex = idx
                    },
                    onShowAuthWarning = { showAuthWarningDialog = true },
                    leftPanelFocusRequester = lpFocus,
                    tabBarFocusRequester = tbFocus,
                    targetSeason = targetSeason,
                    heroArtwork = heroArtwork
                )
            },
            floatingHeaderActions = {
                // Paylaş · Favori · Galeri → ortak üst çubuk aksiyon satırı
                KitsugiDetailHeaderActions(
                    accentColor = accentColor,
                    onShareClick = {
                        // Bağlantı çözülemezse de paylaşım açılır; başlık bağlantısız gider
                        // (kullanıcıya 404 bir adres göndermekten iyidir).
                        val url = buildExternalUrl(displayResult)
                        com.kitsugi.animelist.utils.ShareUtils.shareText(context, displayResult.title, url)
                    },
                    showFavourite = showFavouriteButton && onToggleFavoriteClick != null,
                    isFavourite = isFavorite,
                    onFavouriteClick = {
                        onToggleFavoriteClick?.invoke(
                            ApiSearchSelection(result = displayResult, synopsis = synopsisForSave)
                        )
                    },
                    showGallery = galleryItems.isNotEmpty() || !displayResult.imageUrl.isNullOrBlank(),
                    galleryLoading = galleryLoading,
                    onGalleryClick = {
                        val clickedUrl = displayResult.imageUrl
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
                                source = displayResult.source
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
                    ApiDetailLeftPanel(
                        displayResult = displayResult,
                        detailState = detailState,
                        resolvedTmdbId = resolvedTmdbId,
                        galleryItems = galleryItems,
                        existingEntry = effectiveExistingEntry,
                        isConnected = isConnected,
                        isFavorite = isFavorite,
                        showFavouriteButton = showFavouriteButton,
                        synopsisForSave = synopsisForSave,
                        titleLanguage = titleLanguage,
                        scoreFormat = scoreFormat,
                        hideScores = hideScores,
                        showAnimeLogos = showAnimeLogos,
                        logoUrl = logoUrl,
                        blurAdultMedia = blurAdultMedia,
                        onBackClick = onBackClick,
                        onAddClick = onAddClick,
                        onEditClick = onEditClick,
                        onToggleFavoriteClick = onToggleFavoriteClick,
                        onReadMangaClick = onReadMangaClick,
                        onGalleryOpen = { items, idx ->
                            activeGalleryCategory = items.getOrNull(idx)?.category ?: GalleryCategory.POSTER
                            if (!galleryLoading) activeGalleryItems = items
                            activeGalleryIndex = idx
                        },
                        onShowAuthWarning = { showAuthWarningDialog = true },
                        leftPanelFocusRequester = lpFocus,
                        tabBarFocusRequester = tbFocus,
                        targetSeason = targetSeason,
                        heroArtwork = heroArtwork
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
                        ApiOverviewTabSlot(
                            viewModel = viewModel,
                            result = result,
                            displayResult = displayResult,
                            detail = detailState,
                            isDetailLoading = detailLoading,
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
                            onSearchQuery = onSearchQuery,
                            onStudioClick = onStudioClick,
                            onStaffClick = onStaffClick,
                            onMagazineClick = onMagazineClick,
                            onGenreClick = onSearchByGenre,
                            onTagClick = onSearchByTag,
                            onSearchBySeason = onSearchBySeason,
                            galleryItems = galleryItems,
                            galleryLoading = galleryLoading,
                            onOpenGalleryItems = { items, idx ->
                                if (!galleryLoading) activeGalleryItems = items
                                activeGalleryIndex = idx
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
                                    isAdult = displayResult.isAdult,
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
                        // kaldırıldı. `page == 2` zaten Videolar sekmesidir; kontrol
                        // yüzünden sekme geçişi sırasında içerik bir kare boyunca boş
                        // kalıyor ve bu ekranın tamamının yeniden kompoze olmasına yol
                        // açan bir state okuması yapılıyordu.
                        ApiVideoTabSlot(
                            viewModel = viewModel,
                            detail = detailState,
                            mediaTitle = displayResult.title,
                            primarySource = result.source
                        )
                    }
                    3 -> ApiCharactersTabSlot(
                        viewModel = viewModel,
                        onCharacterClick = onCharacterClick,
                        onStaffClick = onStaffClick,
                        onMediaClick = onMediaClick,
                        titleLanguage = titleLanguage,
                        staffNameLanguage = staffNameLanguage
                    )
                    4 -> ApiStaffTabSlot(
                        viewModel = viewModel,
                        onStaffClick = onStaffClick,
                        titleLanguage = titleLanguage,
                        staffNameLanguage = staffNameLanguage
                    )
                    5 -> ApiRecommendationsTabSlot(
                        viewModel = viewModel,
                        titleLanguage = titleLanguage,
                        blurAdultMedia = blurAdultMedia,
                        onRelationClick = onRelationClick
                    )
                    6 -> ApiRelationsTabSlot(
                        viewModel = viewModel,
                        titleLanguage = titleLanguage,
                        blurAdultMedia = blurAdultMedia,
                        onRelationClick = onRelationClick
                    )
                    7 -> ApiStatsTabSlot(viewModel = viewModel)
                    8 -> ApiReviewsTabSlot(
                        viewModel = viewModel,
                        source = result.source,
                        externalId = result.malId,
                        mediaType = displayResult.type,
                        apiClient = apiClient,
                        tmdbId = displayResult.tmdbId ?: detailState?.tmdbId ?: resolvedTmdbId,
                        realMalId = detailState?.realMalId ?: displayResult.realMalId ?: result.malId.takeIf { m ->
                            m in 1 until 100_000_000 &&
                                !result.source.equals("shikimori", true) &&
                                !result.source.equals("bangumi", true) &&
                                !result.source.equals("tmdb", true) &&
                                !result.source.equals("simkl", true)
                        },
                        titleLanguage = titleLanguage,
                        onUserProfileClick = onUserProfileClick,
                        preferredTranslator = settingsSlice?.preferredTranslator ?: "DEFAULT"
                    )
                    9 -> ApiEpisodesTabSlot(
                        viewModel = viewModel,
                        result = result,
                        displayResult = displayResult,
                        detailState = detailState,
                        resolvedTmdbId = resolvedTmdbId,
                        targetSeason = targetSeason,
                        titleLanguage = titleLanguage,
                        onEpisodeOptionsRequested = { episode ->
                            activeEpisodeForOptions = episode
                        }
                    )
                }
            }
        )
    }

    activeEpisodeForOptions?.let { ep ->
        val streamIds = displayResult.toStreamMediaIds(
            resolvedRealMalId = detailState?.realMalId ?: displayResult.realMalId,
            resolvedTmdbId = detailState?.tmdbId ?: resolvedTmdbId ?: displayResult.tmdbId
        )
        KitsugiEpisodeOptionsDialog(
            animeTitle = displayResult.getDisplayTitle(titleLanguage),
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
            mediaType = displayResult.type,
            posterUrl = displayResult.imageUrl,
            titleEnglish = displayResult.titleEnglish,
            titleRomaji = detailState?.titleRomaji,
            titleNative = detailState?.titleNative,
            synonyms = detailState?.synonyms.orEmpty(),
            startYear = displayResult.year,
            isMovie = displayResult.type == MediaType.Movie,
            seasonNumber = ep.seasonNumber ?: targetSeason,
            onDismiss = { activeEpisodeForOptions = null }
        )
    }

    // İzle - bölüm numarası dialog
    if (showWatchDialog) {
        val accentColor = LocalKitsugiAccent.current
        AlertDialog(
            onDismissRequest = { showWatchDialog = false },
            containerColor = KitsugiColors.Surface,
            shape = RoundedCornerShape(24.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.PlayArrow,
                        contentDescription = null,
                        tint = accentColor
                    )
                    com.kitsugi.animelist.ui.theme.gradient.Text(
                        text = displayResult.title,
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    com.kitsugi.animelist.ui.theme.gradient.Text(
                        text = stringResource(R.string.hangi_bolumu_izlemek_istiyorsunuz),
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = watchEpisodeInput,
                        onValueChange = { watchEpisodeInput = it.filter { c -> c.isDigit() } },
                        label = { com.kitsugi.animelist.ui.theme.gradient.Text(stringResource(R.string.bolum_numarasi)) },
                        placeholder = { com.kitsugi.animelist.ui.theme.gradient.Text(stringResource(R.string.orn_1)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = KitsugiColors.Border,
                            focusedLabelColor = accentColor,
                            cursorColor = accentColor,
                            focusedTextColor = KitsugiColors.TextPrimary,
                            unfocusedTextColor = KitsugiColors.TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                val epNum = watchEpisodeInput.toIntOrNull()
                KitsugiButton(
                    onClick = {
                        if (epNum != null && epNum > 0) {
                            showWatchDialog = false
                            val streamIds = displayResult.toStreamMediaIds(
                                resolvedRealMalId = detailState?.realMalId ?: displayResult.realMalId,
                                resolvedTmdbId = detailState?.tmdbId ?: resolvedTmdbId ?: displayResult.tmdbId
                            )
                            KitsugiStreamActivity.start(
                                context = context,
                                malId = streamIds.malId,
                                aniListId = streamIds.aniListId,
                                tmdbId = streamIds.tmdbId,
                                bangumiId = streamIds.bangumiId,
                                mediaType = displayResult.type,
                                episode = epNum,
                                season = targetSeason,
                                isMovie = displayResult.type == MediaType.Movie,
                                title = displayResult.title,
                                posterUrl = displayResult.imageUrl,
                                titleEnglish = displayResult.titleEnglish,
                                titleRomaji = detailState?.titleRomaji,
                                titleNative = detailState?.titleNative,
                                synonyms = detailState?.synonyms.orEmpty(),
                                startYear = displayResult.year,
                                // Detay sayfasının bildiği IMDb: akış tarafı bunu ID çözümünde önceliklendirir.
                                imdbId = detailState?.externalLinks
                                    ?.firstOrNull { it.url.contains("imdb.com", ignoreCase = true) }
                                    ?.url?.let { Regex("(?i)tt[0-9]{5,12}").find(it)?.value }
                            )
                        }
                    },
                    enabled = (epNum ?: 0) > 0
                ) {
                    com.kitsugi.animelist.ui.theme.gradient.Text(stringResource(R.string.izle), color = KitsugiColors.Background, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWatchDialog = false }) {
                    com.kitsugi.animelist.ui.theme.gradient.Text(stringResource(R.string.iptal), color = KitsugiColors.TextSecondary)
                }
            }
        )
    }

    if (showAuthWarningDialog) {
        val platformName = when {
            isSourceBangumi -> "Bangumi"
            isSourceAniList -> "AniList"
            else -> "MyAnimeList"
        }
        AlertDialog(
            onDismissRequest = { showAuthWarningDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.inline_account_link_required_cc67382),
                    color = KitsugiColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.inline_an_active_1_s_account_d64f2c7, platformName),
                    color = KitsugiColors.TextSecondary
                )
            },
            confirmButton = {
                KitsugiButton(
                    onClick = {
                        showAuthWarningDialog = false
                        when {
                            isSourceBangumi -> onLoginBangumi()
                            isSourceAniList -> onLoginAniList()
                            else -> onLoginMal()
                        }
                    }
                ) {
                    Text(stringResource(R.string.baglan), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAuthWarningDialog = false }
                ) {
                    Text(stringResource(R.string.iptal), color = KitsugiColors.TextMuted)
                }
            },
            containerColor = KitsugiColors.Surface,
            shape = RoundedCornerShape(18.dp)
        )
    }

    if (activeGalleryItems.isNotEmpty()) {
        KitsugiImageGalleryDialog(
            galleryItems = activeGalleryItems,
            initialIndex = activeGalleryIndex,
            initialCategory = activeGalleryCategory ?: activeGalleryItems.getOrNull(activeGalleryIndex)?.category,
            title = displayResult.title,
            isAdult = displayResult.isAdult,
            onDismiss = {
                activeGalleryItems = emptyList()
                activeGalleryCategory = null
            }
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
} // ApiResultDetailPage

@Composable
private fun ApiOverviewTabSlot(
    viewModel: ApiResultDetailViewModel,
    result: JikanSearchResult,
    displayResult: JikanSearchResult,
    detail: KitsugiMediaDetail?,
    isDetailLoading: Boolean,
    settingsSlice: DetailSettingsSlice?,
    mdbListShowImdb: Boolean,
    mdbListShowTomatoes: Boolean,
    mdbListShowMetacritic: Boolean,
    mdbListShowAudience: Boolean,
    mdbListShowLetterboxd: Boolean,
    mdbListShowTmdb: Boolean,
    mdbListShowTrakt: Boolean,
    onSettingsClick: (() -> Unit)?,
    onSearchQuery: (String) -> Unit,
    onStudioClick: (Int, String, String?, String?) -> Unit,
    onStaffClick: (Int, String, String?, String?) -> Unit,
    onMagazineClick: (Int, String) -> Unit,
    onGenreClick: (String) -> Unit,
    onTagClick: (com.kitsugi.animelist.data.remote.KitsugiTag) -> Unit,
    onSearchBySeason: (com.kitsugi.animelist.ui.screens.search.DetailSeasonMetadata) -> Unit,
    galleryItems: List<GalleryItem>,
    galleryLoading: Boolean,
    onOpenGalleryItems: (List<GalleryItem>, Int) -> Unit
) {
    val context = LocalContext.current
    val translatedSynopsis by viewModel.translatedSynopsis.collectAsState()
    val showSynopsisOriginal by viewModel.showSynopsisOriginal.collectAsState()
    val mdbListRatings by viewModel.mdbListRatings.collectAsState()
    val mdbListLoading by viewModel.mdbListLoading.collectAsState()

    val rawSynopsisForToggle = detail?.synopsis
    val translatedForToggle = translatedSynopsis
    val synopsisHasTranslation = translatedForToggle != null && rawSynopsisForToggle != null &&
        !com.kitsugi.animelist.utils.translationMatchesSource(rawSynopsisForToggle, translatedForToggle)
    val displaySynopsis = if (showSynopsisOriginal) rawSynopsisForToggle
    else translatedForToggle?.takeIf { synopsisHasTranslation } ?: rawSynopsisForToggle

    ApiDetailOverviewTab(
        result = result,
        detail = detail,
        displaySynopsis = displaySynopsis,
        isDetailLoading = isDetailLoading,
        onSearchQuery = onSearchQuery,
        onStudioClick = onStudioClick,
        onStaffClick = onStaffClick,
        onMagazineClick = onMagazineClick,
        onGenreClick = onGenreClick,
        onTagClick = onTagClick,
        onSearchBySeason = onSearchBySeason,
        onTranslateClick = {
            val raw = detail?.synopsis
            if (!raw.isNullOrBlank()) context.openTranslator(raw, settingsSlice?.preferredTranslator ?: "DEFAULT")
        },
        synopsisHasTranslation = synopsisHasTranslation,
        showSynopsisOriginal = showSynopsisOriginal,
        onToggleSynopsisOriginal = { viewModel.toggleSynopsisOriginal() },
        onCopyClick = {
            val text = displaySynopsis ?: detail?.synopsis
            if (!text.isNullOrBlank()) {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("synopsis", text))
                android.widget.Toast.makeText(context, KitsugiStrings.get(R.string.toast_copied_clipboard), android.widget.Toast.LENGTH_SHORT).show()
            }
        },
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
                urls.map { url ->
                    GalleryItem(url = url, category = GalleryCategory.POSTER, source = displayResult.source)
                },
                index
            )
        },
        galleryItems = galleryItems,
        galleryLoading = galleryLoading,
        onGalleryItemRequest = onOpenGalleryItems
    )
}

@Composable
private fun ApiCharactersTabSlot(
    viewModel: ApiResultDetailViewModel,
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
private fun ApiStaffTabSlot(
    viewModel: ApiResultDetailViewModel,
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
private fun ApiRecommendationsTabSlot(
    viewModel: ApiResultDetailViewModel,
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
private fun ApiRelationsTabSlot(
    viewModel: ApiResultDetailViewModel,
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
private fun ApiStatsTabSlot(viewModel: ApiResultDetailViewModel) {
    val state by viewModel.statsState.collectAsState()
    StatsTabContent(state = state)
}

@Composable
private fun ApiReviewsTabSlot(
    viewModel: ApiResultDetailViewModel,
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
private fun ApiEpisodesTabSlot(
    viewModel: ApiResultDetailViewModel,
    result: JikanSearchResult,
    displayResult: JikanSearchResult,
    detailState: KitsugiMediaDetail?,
    resolvedTmdbId: Int?,
    targetSeason: Int,
    titleLanguage: String,
    onEpisodeOptionsRequested: (KitsugiStreamingEpisode) -> Unit
) {
    val state by viewModel.episodesState.collectAsState()
    val episodeRatings by viewModel.episodeRatings.collectAsState()
    val streamIds = displayResult.toStreamMediaIds(
        resolvedRealMalId = detailState?.realMalId ?: displayResult.realMalId,
        resolvedTmdbId = detailState?.tmdbId ?: resolvedTmdbId ?: displayResult.tmdbId
    )
    ApiDetailEpisodesTab(
        state = state,
        episodeRatings = episodeRatings,
        targetSeason = targetSeason,
        totalSeasons = detailState?.totalSeasons,
        streamIds = streamIds,
        mediaType = displayResult.type,
        displayTitle = displayResult.getDisplayTitle(titleLanguage),
        displaySource = displayResult.source,
        displayImageUrl = displayResult.imageUrl,
        displayTitleEnglish = displayResult.titleEnglish ?: detailState?.titleEnglish,
        displayTitleRomaji = detailState?.titleRomaji ?: displayResult.title,
        displayTitleNative = detailState?.titleNative ?: detailState?.titleJapanese ?: displayResult.titleJapanese,
        displaySynonyms = detailState?.synonyms.orEmpty(),
        imdbId = detailState?.externalLinks?.firstOrNull { it.url.contains("imdb.com", ignoreCase = true) }
            ?.url?.let { Regex("(?i)tt[0-9]{5,12}").find(it)?.value?.lowercase() },
        displayYear = displayResult.year,
        isMovie = displayResult.type == MediaType.Movie,
        isAdult = displayResult.isAdult,
        onSeasonSelected = { newSeason ->
            viewModel.setTargetSeason(newSeason, result, displayResult.realMalId)
        },
        onEpisodeOptionsRequested = onEpisodeOptionsRequested
    )
}

/**
 * Kaynak platforma göre doğru URL oluşturur.
 * - MAL/Jikan → myanimelist.net/anime|manga/{malId}
 * - AniList   → anilist.co/anime|manga/{aniListId}  (malId >= 100_000_000 ise offset'ten çıkar)
 * - TMDB      → themoviedb.org/movie|tv/{tmdbId}
 * - Diğer     → null
 */
private fun buildExternalUrl(result: JikanSearchResult): String? {
    return com.kitsugi.animelist.utils.ShareLinks.mediaUrl(
        source = result.source,
        storedId = result.malId,
        type = result.type,
        tmdbId = result.tmdbId,
        realMalId = result.realMalId
    )
}

// buildMalCrossUrl: artık kullanılmıyor (platform-specific buton mantığı buildExternalUrl'e taşındı)
@Suppress("unused")
private fun buildMalCrossUrl(result: JikanSearchResult): String? {
    if (result.source.lowercase() != "anilist") return null
    val realMalId = result.realMalId ?: return null
    return when (result.type) {
        MediaType.Anime -> "https://myanimelist.net/anime/$realMalId"
        MediaType.Manga -> "https://myanimelist.net/manga/$realMalId"
        else -> null
    }
}

@Composable
private fun ApiVideoTabSlot(
    viewModel: ApiResultDetailViewModel,
    detail: KitsugiMediaDetail?,
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