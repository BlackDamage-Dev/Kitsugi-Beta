package com.kitsugi.animelist.ui.components

import android.os.Handler
import com.kitsugi.animelist.ui.utils.uniqueLazyKeys
import android.os.Looper
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowRgb565
import coil3.request.crossfade
import coil3.size.Precision
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.remote.JikanSearchResult
import com.kitsugi.animelist.data.remote.KitsugiBangumiDetailClient
import com.kitsugi.animelist.data.remote.KitsugiEpisodeRatingsRepository
import com.kitsugi.animelist.data.remote.KitsugiHeroArtworkResolver
import com.kitsugi.animelist.data.remote.KitsugiIdResolver
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalIsTvDevice
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import com.kitsugi.animelist.ui.utils.tvClickable
import com.kitsugi.animelist.utils.PreferenceHelpers.getDisplayTitle
import com.kitsugi.animelist.utils.copyOnDoubleTap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private val heroMainHandler by lazy(LazyThreadSafetyMode.PUBLICATION) { Handler(Looper.getMainLooper()) }

/**
 * Vitrin ambient (dekoratif arka plan) katmanının bulanıklık yarıçapı.
 * Keskin görselin arkasında kalan alanı yumuşak bir ortama çevirir.
 */
private val HERO_AMBIENT_BLUR = 30.dp

// Image callbacks can run during drawing; never mutate layout/state from that frame.
private fun postAfterHeroFrame(block: () -> Unit) {
    heroMainHandler.post(block)
}

private suspend fun fetchHeroLogo(item: JikanSearchResult): String? = try {
    val stableId = item.malId
    val logoTitles = listOfNotNull(item.title, item.titleEnglish, item.titleJapanese, item.titleRomaji)
        .filter { it.isNotBlank() }.distinct()
    val isMovie = KitsugiEpisodeRatingsRepository.prefersMovieLogo(
        type = item.type,
        subtitle = item.subtitle,
        title = listOfNotNull(item.title, item.titleEnglish).joinToString(" | ")
    )
    val season = KitsugiEpisodeRatingsRepository.determineTargetSeason(
        tmdbSeason = null,
        title = item.title,
        titleEnglish = item.titleEnglish,
        synonyms = emptyList()
    ).takeIf { it > 1 }
    val primaryLogo = when {
        item.source.equals("tmdb", ignoreCase = true) -> {
            val tmdbId = item.tmdbId ?: stableId.takeIf { it > 0 }
            tmdbId?.takeIf { it > 0 }?.let { KitsugiEpisodeRatingsRepository.getLogoUrl(it, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles) }
        }
        item.source.equals("anilist", ignoreCase = true) -> {
            if (stableId in 100_000_001..199_999_999) {
                KitsugiEpisodeRatingsRepository.getLogoUrlByAniListId(
                    aniListId = stableId - 100_000_000,
                    fallbackMalId = item.realMalId,
                    seasonNumber = season,
                    isMovie = isMovie,
                    expectedTitles = logoTitles
                )
            } else {
                val malId = item.realMalId?.takeIf { it in 1..99_999_999 }
                    ?: stableId.takeIf { it in 1..99_999_999 }
                malId?.let { KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(it, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles) }
            }
        }
        item.source.equals("kitsu", ignoreCase = true) -> {
            val kitsuLogo = if (stableId in 300_000_001..399_999_999) {
                KitsugiEpisodeRatingsRepository.getLogoUrlByKitsuId(stableId - 300_000_000, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles)
            } else null
            kitsuLogo?.takeIf { it.isNotBlank() }
                ?: item.realMalId?.takeIf { it in 1..99_999_999 }
                    ?.let { KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(it, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles) }
        }
        item.source.equals("simkl", ignoreCase = true) -> {
            val cross = com.kitsugi.animelist.data.remote.KitsugiSimklDetailClient.resolveSimklCrossIds(
                simklId = stableId,
                mediaType = item.type,
                hintTmdbId = item.tmdbId,
                hintMalId = item.realMalId
            )
            val simklTitles = (logoTitles + listOfNotNull(cross.title)).distinct()
            val effectiveIsMovie = isMovie || cross.isMovie
            val realMalId = item.realMalId?.takeIf { it in 1..99_999_999 && it != stableId }
                ?: cross.malId?.takeIf { it in 1..99_999_999 && it != stableId }
            val tmdbId = item.tmdbId?.takeIf { it > 0 }
                ?: cross.tmdbId?.takeIf { it > 0 }
            tmdbId?.let { KitsugiEpisodeRatingsRepository.getLogoUrl(it, seasonNumber = season, isMovie = effectiveIsMovie, expectedTitles = simklTitles) }
                ?.takeIf { it.isNotBlank() }
                ?: realMalId?.let { KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(it, fallbackAniListId = cross.aniListId, seasonNumber = season, isMovie = effectiveIsMovie, expectedTitles = simklTitles) }
                    ?.takeIf { it.isNotBlank() }
                ?: cross.aniListId?.takeIf { it > 0 }?.let { KitsugiEpisodeRatingsRepository.getLogoUrlByAniListId(it, fallbackMalId = realMalId, seasonNumber = season, isMovie = effectiveIsMovie, expectedTitles = simklTitles) }
                    ?.takeIf { it.isNotBlank() }
                ?: cross.kitsuId?.takeIf { it > 0 }?.let { KitsugiEpisodeRatingsRepository.getLogoUrlByKitsuId(it, seasonNumber = season, isMovie = effectiveIsMovie, expectedTitles = simklTitles) }
                    ?.takeIf { it.isNotBlank() }
        }
        item.source.equals("bangumi", ignoreCase = true) || item.source.equals("bgm", ignoreCase = true) -> {
            // Bangumi kimliği 500M+ stableId'dir; MAL olarak kesinlikle kullanma.
            val cross = KitsugiBangumiDetailClient.resolveCrossIds(stableId, item.type)
            val realMalId = item.realMalId?.takeIf { it in 1..99_999_999 } ?: cross.malId
            val malLogo = realMalId?.let { KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(it, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles) }
                ?.takeIf { it.isNotBlank() }
            val aniListLogo = if (malLogo.isNullOrBlank()) {
                cross.aniListId?.takeIf { it > 0 }
                    ?.let { KitsugiEpisodeRatingsRepository.getLogoUrlByAniListId(it, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles) }
                    ?.takeIf { it.isNotBlank() }
            } else null
            malLogo ?: aniListLogo ?: cross.tmdbId?.takeIf { it > 0 }
                ?.let { KitsugiEpisodeRatingsRepository.getLogoUrl(it, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles) }
        }
        item.source.equals("jikan", ignoreCase = true) ||
            item.source.equals("mal", ignoreCase = true) ||
            item.source.equals("myanimelist", ignoreCase = true) -> {
            stableId.takeIf { it in 1..99_999_999 }
                ?.let { KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(it, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles) }
        }
        item.source.equals("shikimori", ignoreCase = true) -> {
            val realMalId = item.realMalId?.takeIf { it in 1..99_999_999 }
                ?: withContext(kotlinx.coroutines.Dispatchers.IO) {
                    KitsugiIdResolver.resolveMalIdFromShikimori(stableId)
                }
            realMalId?.let { KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(it, seasonNumber = season, isMovie = isMovie, expectedTitles = logoTitles) }
        }
        else -> null
    }
    if (!primaryLogo.isNullOrBlank()) {
        primaryLogo
    } else {
        val resolvedArt = runCatching { KitsugiHeroArtworkResolver.resolveArtwork(item) }.getOrNull()
        val fallbackTmdbId = resolvedArt?.resolvedTmdbId?.takeIf { it > 0 }
        val fallbackMalId = resolvedArt?.resolvedMalId?.takeIf { it in 1..99_999_999 }
        val fallbackIsMovie = resolvedArt?.isMovie ?: isMovie
        fallbackTmdbId?.let {
            KitsugiEpisodeRatingsRepository.getLogoUrl(
                tmdbId = it,
                seasonNumber = season,
                isMovie = fallbackIsMovie,
                expectedTitles = emptyList()
            )
        }?.takeIf { it.isNotBlank() }
            ?: fallbackMalId?.let {
                KitsugiEpisodeRatingsRepository.getLogoUrlByMalId(
                    malId = it,
                    seasonNumber = season,
                    isMovie = fallbackIsMovie,
                    expectedTitles = logoTitles
                )
            }?.takeIf { it.isNotBlank() }
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}

/**
 * A real, keyed pager: artwork, logo and metadata are children of the SAME page.
 * There is no invisible pager, hand-dispatched drag, stacked crossfade or content parallax.
 * Its height depends only on device geometry, never on an item's image or loading state.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KitsugiHeroSection(
    items: List<JikanSearchResult>,
    alreadyInList: (JikanSearchResult) -> Boolean,
    onInfoClick: (JikanSearchResult) -> Unit,
    modifier: Modifier = Modifier,
    titleLanguage: String = "ENGLISH",
    scoreFormat: String = "POINT_10",
    hideScores: Boolean = false,
    showAnimeLogos: Boolean = false,
    isVisible: Boolean = true,
    blurAdultMedia: Boolean = false,
    /** Explore's admission gate has already decoded and verified these backdrop URLs. */
    artworkPrepared: Boolean = false
) {
    val context = LocalContext.current
    val videoItems = remember(items) { items.filter(::isHomeHeroMedia).distinctBy(::homeHeroItemKey) }
    // Other callers (e.g. extension discovery) use the same strict gate, not their posters.
    val artworkLoader = remember(context.applicationContext) { HomeHeroArtworkLoader(context) }
    var preparedItems by remember(videoItems) { mutableStateOf<List<JikanSearchResult>>(emptyList()) }
    var preparing by remember(videoItems) { mutableStateOf(!artworkPrepared && videoItems.isNotEmpty()) }
    LaunchedEffect(videoItems, artworkPrepared) {
        if (!artworkPrepared) {
            preparing = videoItems.isNotEmpty()
            val permits = Semaphore(3)
            preparedItems = coroutineScope {
                videoItems.map { item -> async { permits.withPermit { artworkLoader.prepare(item) } } }
                    .awaitAll().filterNotNull()
            }
            preparing = false
        }
    }
    val admittedItems = remember(videoItems, preparedItems, artworkPrepared) {
        (if (artworkPrepared) videoItems else preparedItems).mapNotNull { item ->
            homeHeroBackdropUrl(item)?.let { url ->
                if (url == item.backdropUrl) item else item.copy(backdropUrl = url)
            }
        }
    }
    if (admittedItems.isEmpty()) {
        if (preparing) KitsugiShimmerHeroSection(modifier = modifier)
        return
    }

    // Source completions must not change page indices under a finger or during a snap.
    var heroItems by remember { mutableStateOf(admittedItems) }
    val config = LocalConfiguration.current
    val isLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE ||
        config.screenWidthDp > config.screenHeightDp
    val isTablet = minOf(config.screenWidthDp, config.screenHeightDp) >= 600
    val isTv = LocalIsTvDevice.current
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    // Tüm ölçüler cihaz geometrisinden türetilir (bkz. HeroVitrinLayout). Görselin
    // oranı, decode durumu veya sayfa değişimi hiçbir ölçüyü kaydıramaz.
    val layout = heroVitrinLayout(
        screenWidthDp = config.screenWidthDp.toFloat(),
        screenHeightDp = config.screenHeightDp.toFloat(),
        isLandscape = isLandscape,
        isTablet = isTablet,
        isTv = isTv,
        fontScale = fontScale
    )
    val heroHeight = layout.bandHeightDp.dp
    val isStacked = layout.mode == HeroVitrinMode.STACKED
    val horizontalPadding = if (isTv) 48.dp else if (isTablet) 28.dp else if (isLandscape) 24.dp else 16.dp
    val contentWidthFraction = if (isLandscape || isTv) 0.56f else 1f
    val contentMaxWidth = if (isTv) 640.dp else if (isTablet) 600.dp else 520.dp
    val titleSlotHeight = (if (isLandscape) 64.dp else 84.dp) * fontScale
    val accent = LocalKitsugiAccent.current
    val background = KitsugiColors.Background
    val pagerState = rememberPagerState(pageCount = { heroItems.size })
    val scope = rememberCoroutineScope()
    val latestOnInfoClick by rememberUpdatedState(onInfoClick)
    val pageInteractionSource = remember { MutableInteractionSource() }
    val isPressed by pageInteractionSource.collectIsPressedAsState()
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    var isFocused by remember { mutableStateOf(false) }
    var isTouchDown by remember { mutableStateOf(false) }
    val logoCache = remember { mutableMapOf<String, String>() }
    val logoPermits = remember { Semaphore(3) }
    var autoDirection by remember { mutableIntStateOf(1) }
    LaunchedEffect(admittedItems) {
        snapshotFlow { !pagerState.isScrollInProgress && !isDragged && !isPressed && !isTouchDown }.first { it }
        heroItems = admittedItems
    }

    fun openCurrentItem() {
        heroItems.getOrNull(pagerState.currentPage)?.let { latestOnInfoClick(it) }
    }
    fun goToPage(index: Int) {
        scope.launch {
            pagerState.animateScrollToPage(index, animationSpec = tween(500, easing = FastOutSlowInEasing))
        }
    }

    // ── Otomatik kaydırma: son etkileşimden 5 sn sonra bir sonraki sayfa ────
    // KÖK NEDEN (eski bug): effect anahtarlarında pagerState.isScrollInProgress
    // (ve settledPage) vardı. animateScrollToPage başlar başlamaz isScrollInProgress
    // true oluyor, anahtar değiştiği için LaunchedEffect İPTAL ediliyor ve animasyon
    // daha ilk karelerde ölüyordu → vitrin kendi kendine hiç kayamıyordu.
    // Yeni sürüm: yalnızca kararlı girdilere (sayfa sayısı, görünürlük) bağlı tek
    // bir döngü. Pager/etkileşim durumu döngü içinde OKUNUR, asla anahtar yapılmaz;
    // kullanıcı dokunur, sürükler veya sayfa değiştirirse 5 sn'lik sayaç sıfırlanır.
    val heroItemCount = heroItems.size
    LaunchedEffect(heroItemCount, isVisible) {
        if (heroItemCount <= 1 || !isVisible) return@LaunchedEffect
        var lastSettledPage = pagerState.settledPage
        while (true) {
            var idleMs = 0L
            while (idleMs < HERO_AUTOPLAY_DELAY_MS) {
                delay(HERO_AUTOPLAY_TICK_MS)
                val settled = pagerState.settledPage
                val interacting = isPressed || isDragged || isTouchDown || isFocused ||
                    pagerState.isScrollInProgress
                if (interacting || settled != lastSettledPage) {
                    lastSettledPage = settled
                    idleMs = 0L
                } else {
                    idleMs += HERO_AUTOPLAY_TICK_MS
                }
            }
            val current = pagerState.settledPage.coerceIn(0, heroItemCount - 1)
            autoDirection = homeHeroAutoDirection(current, heroItemCount, autoDirection)
            if (autoDirection != 0) {
                try {
                    // Only ever travel ONE page. No last -> first multi-page rewind or boundary wrap.
                    pagerState.animateScrollToPage(
                        current + autoDirection,
                        animationSpec = tween(650, easing = FastOutSlowInEasing)
                    )
                } catch (cancelled: CancellationException) {
                    // Kullanıcı animasyonu yarıda ele geçirmiş olabilir; yalnızca
                    // bu effect gerçekten iptal edildiyse çık.
                    currentCoroutineContext().ensureActive()
                }
            }
            lastSettledPage = pagerState.settledPage
        }
    }

    Box(
        modifier = modifier.testTag("home_hero").fillMaxWidth().height(heroHeight).clipToBounds()
            .pointerInput(pagerState) {
                // Observe only: consume NOTHING and leave drag/fling entirely to Pager.
                // A title's double-tap handler may consume its down event; even then,
                // autoplay and source updates must wait until the finger is released.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    isTouchDown = true
                    try {
                        while (true) {
                            if (awaitPointerEvent(PointerEventPass.Initial).changes.none { it.pressed }) break
                        }
                    } finally {
                        isTouchDown = false
                    }
                }
            }
            .then(
                if (isTv) Modifier
                    .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                            Key.DirectionLeft -> if (pagerState.currentPage > 0) {
                                goToPage(pagerState.currentPage - 1)
                                true
                            } else false
                            Key.DirectionRight -> if (pagerState.currentPage < heroItems.lastIndex) {
                                goToPage(pagerState.currentPage + 1)
                                true
                            } else false
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                openCurrentItem()
                                true
                            }
                            else -> false
                        }
                    }
                    .focusable()
                    .border(3.dp, if (isFocused) accent else Color.Transparent, RoundedCornerShape(24.dp))
                else Modifier
            ).background(background)
    ) {
        // Pager anahtarları da benzersiz olmak ZORUNDA: vitrin listesinde aynı yapım iki kez
        // yer alırsa (farklı raflardan birleşen öğeler) "Key was already used" çökmesi olur.
        val heroPageKeys = remember(heroItems) { heroItems.uniqueLazyKeys { homeHeroItemKey(it) } }
        HorizontalPager(
            state = pagerState,
            key = { page -> heroPageKeys.getOrNull(page) ?: "hero_page_$page" },
            beyondViewportPageCount = 1,
            userScrollEnabled = !isTv,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapAnimationSpec = tween(450, easing = FastOutSlowInEasing)
            ),
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = heroItems[page]
            val title = item.getDisplayTitle(titleLanguage)
            val identity = homeHeroItemKey(item)
            val backdrop = requireNotNull(item.backdropUrl)
            // Ekran geometrisine göre otomatik görsel türü: dikey telefon bandı dardır
            // ve yatay backdrop sığmaz; sayfanın yüksek çözünürlüklü POSTERİ bandı tam
            // kaplar. Poster yüklenemez veya decode edilince gerçekten dikey çıkmazsa
            // sayfa kendi kendine backdrop şeridine döner (geri çağrılar aşağıda).
            // Yatay/TV/tablette her zaman yatay backdrop kullanılır.
            val posterCandidate = homeHeroPosterUrl(item)
            var posterUsable by remember(posterCandidate) { mutableStateOf(posterCandidate != null) }
            var posterAspect by remember(posterCandidate) { mutableStateOf<Float?>(null) }
            val usePoster = layout.prefersPoster && posterCandidate != null && posterUsable
            val sharpUrl = if (usePoster) posterCandidate!! else backdrop
            var logoUrl by remember(identity) { mutableStateOf<String?>(logoCache[identity]) }
            val wantsLogo = shouldShowHeroLogo(item, showAnimeLogos)
            LaunchedEffect(identity, wantsLogo) {
                logoUrl = if (!wantsLogo) null else {
                    logoCache[identity] ?: logoPermits.withPermit { fetchHeroLogo(item) }
                        ?.takeIf { it.isNotBlank() }?.also { logoCache[identity] = it }
                }
            }
            Box(
                modifier = Modifier.testTag("home_hero_page:$identity").fillMaxSize().clipToBounds().then(
                    if (isTv) Modifier else Modifier.tvClickable(
                        scaleOnFocus = false,
                        interactionSource = pageInteractionSource
                    ) { latestOnInfoClick(item) }
                )
            ) {
                // ── 1) AMBIENT (Nuvio yaklaşımı) ──────────────────────────────────
                // Aynı görselin BULANIK kopyası bandın tamamını kaplar. Vitrin hiçbir
                // zaman boş kutu, yarım görsel veya bulanık olmayan hayalet kopya
                // göstermez; art arda kalan alan kasıtlı bir ortam gibi okunur.
                // Coil'de disk cache anahtarı yalnızca URL'dir: bu katman ek bir indirme
                // yapmaz, aynı baytları küçük çözünürlükte decode edip bulanıklaştırır.
                if (isStacked && !usePoster) {
                    KitsugiNsfwImage(
                        model = backdrop,
                        contentDescription = null,
                        isAdult = item.isAdult,
                        blurAdultMedia = blurAdultMedia,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        ambientBlur = HERO_AMBIENT_BLUR,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Ambient katmanı koyulaştır: metin okunur kalır, renkleri kaybolmaz.
                    // Bu örtü keskin görselin ALTINDA kalır; görselin parlaklığı korunur.
                    Box(Modifier.fillMaxSize().background(background.copy(alpha = 0.55f)))
                }
                // ── 2) KESKİN GÖRSEL ─────────────────────────────────────────────
                // Görsel kutuya TAMAMEN kaplar (Crop). Kutunun oranı cihaz
                // geometrisinden geldiği için kırpma sabittir: 16:9 görsel neredeyse
                // tamamen, 3:1 banner yatay ortadan kırpılmış olarak görünür. Artık
                // "Fit" ile yükleme/kenar boşluğu yoktur.
                // Dikey posterde bant tam kaplanır; odak üst yarıda (yüzler kesilmez)
                // ve gerçek poster oranı decode olunca kırpma buna göre inceltilir.
                KitsugiNsfwImage(
                    model = sharpUrl,
                    contentDescription = title,
                    isAdult = item.isAdult,
                    blurAdultMedia = blurAdultMedia,
                    contentScale = ContentScale.Crop,
                    alignment = if (usePoster) {
                        heroImageAlignment(
                            chosenIsPoster = true,
                            heroAspect = layout.bandAspect,
                            imageAspect = posterAspect
                        )
                    } else {
                        Alignment.TopCenter
                    },
                    highQuality = true,
                    onLoadingFailed = if (usePoster) {
                        { postAfterHeroFrame { posterUsable = false } }
                    } else {
                        null
                    },
                    onImageAspectResolved = if (usePoster) {
                        { aspect ->
                            postAfterHeroFrame {
                                posterAspect = aspect
                                // "Poster" gerçekten dikey değilse yanıltıcı etiketlenmiş
                                // demektir: sayfayı backdrop şeridine geri çevir.
                                if (!isVerticalHomeHeroPosterAspect(aspect)) posterUsable = false
                            }
                        }
                    } else {
                        null
                    },
                    modifier = if (isStacked && !usePoster) {
                        Modifier.fillMaxWidth().height(layout.artworkHeightDp.dp)
                    } else {
                        Modifier.fillMaxSize()
                    }
                )
                // ── 3) SCRIM ─────────────────────────────────────────────────────
                // Nuvio Mobile: dikey gradyan. Nuvio TV: yatay (sol) gradyan + alt şerit.
                if (isStacked && !usePoster) {
                    // Görsel şeridinin alt kenarı metin yuvasına yumuşak geçiş yapar.
                    val artworkFraction = (layout.artworkHeightDp / layout.bandHeightDp)
                        .coerceIn(0.15f, 0.9f)
                    Box(Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            (artworkFraction * 0.62f) to Color.Transparent,
                            artworkFraction to background.copy(alpha = 0.32f),
                            1f to background.copy(alpha = 0.74f)
                        )
                    ))
                    // Durum çubuğu için üstte hafif bir koyulaştırma.
                    Box(Modifier.fillMaxWidth().height(48.dp).background(
                        Brush.verticalGradient(
                            0f to background.copy(alpha = 0.26f),
                            1f to Color.Transparent
                        )
                    ))
                } else {
                    Box(Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.26f to Color.Transparent,
                            0.58f to background.copy(alpha = 0.46f),
                            0.80f to background.copy(alpha = 0.84f),
                            1f to background
                        )
                    ))
                    if (usePoster) {
                        // Dikey posterde görsel durum çubuğunun altına kadar uzanır:
                        // saat/pil simgeleri açık renkli posterlerde de okunur kalsın.
                        Box(Modifier.fillMaxWidth().height(48.dp).background(
                            Brush.verticalGradient(
                                0f to background.copy(alpha = 0.26f),
                                1f to Color.Transparent
                            )
                        ))
                    }
                    if (isLandscape || isTv) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(0.62f).background(
                            Brush.horizontalGradient(
                                listOf(
                                    background.copy(alpha = 0.82f),
                                    background.copy(alpha = 0.42f),
                                    Color.Transparent
                                )
                            )
                        ))
                    }
                }
                Column(
                    modifier = Modifier.align(Alignment.BottomStart)
                        .fillMaxWidth(contentWidthFraction).widthIn(max = contentMaxWidth)
                        .padding(start = horizontalPadding, end = horizontalPadding, bottom = 44.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isLandscape) 4.dp else 6.dp)
                ) {
                    Row(
                        modifier = Modifier.height(28.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.inline_featured_20f57e4), color = accent,
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black,
                            maxLines = 1
                        )
                        KitsugiSourceNamePill(source = item.source)
                        if (alreadyInList(item)) {
                            Box(Modifier.clip(CircleShape).background(KitsugiColors.AccentGreen.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)) {
                                Text(
                                    text = stringResource(R.string.inline_in_list_85c8d82),
                                    color = KitsugiColors.AccentGreen,
                                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    HeroTitleSlot(
                        title = title,
                        logoUrl = logoUrl.takeIf { wantsLogo },
                        isLandscape = isLandscape,
                        modifier = Modifier.fillMaxWidth().height(titleSlotHeight),
                        onClick = { latestOnInfoClick(item) }
                    )
                    // Empty subtitles/ratings still reserve their slot. A logo completing,
                    // a missing score, or a long title cannot push another page's metadata.
                    Box(Modifier.fillMaxWidth().height(if (isLandscape) 22.dp else 40.dp)) {
                        Text(
                            text = item.subtitle, color = KitsugiColors.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = if (isLandscape) 1 else 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(Modifier.fillMaxWidth().height(20.dp)) {
                        Text(
                            text = buildHeroMeta(item, scoreFormat, hideScores), color = KitsugiColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (heroItems.size > 1) {
            // Bar sayısı = gerçek içerik sayısı (kayan pencere YOK). Yuvalar satırı
            // eşit paylaşır; içerik çoğaldıkça barlar incelir ama sayı asla eksik
            // gösterilmez. Satır genişliği ekran geometrisinden hesaplanır.
            val indices = homeHeroIndicatorIndices(heroItems.size)
            val spacingDp = heroIndicatorSpacingDp(heroItems.size)
            val rowWidthDp = heroIndicatorRowWidthDp(
                itemCount = heroItems.size,
                availableWidthDp = layout.screenWidthDp - horizontalPadding.value * 2f
            )
            val frameColor = KitsugiColors.TextPrimary
            Row(
                modifier = Modifier.align(Alignment.BottomStart).padding(start = horizontalPadding, bottom = 16.dp)
                    .width(rowWidthDp.dp),
                horizontalArrangement = Arrangement.spacedBy(spacingDp.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                indices.forEach { index ->
                    Box(
                        modifier = Modifier.weight(1f).height(13.dp)
                            .then(if (isTv) Modifier else Modifier.tvClickable(shape = CircleShape) { goToPage(index) })
                            .drawBehind {
                                val active = heroPageVisibility(pagerState, index)
                                if (active > 0.02f) {
                                    val stroke = 1.5.dp.toPx()
                                    val inset = stroke / 2f
                                    drawRoundRect(
                                        color = frameColor.copy(alpha = active),
                                        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                                        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(stroke)
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.fillMaxWidth().padding(horizontal = 3.dp).height(7.dp).clip(CircleShape)
                            .background(accent).graphicsLayer {
                                val active = heroPageVisibility(pagerState, index)
                                alpha = 0.30f + 0.70f * active
                                scaleX = 0.40f + 0.60f * active
                            })
                    }
                }
            }
        }
    }
}

/** Text and logo occupy exactly the same box. Until the correct logo decodes, text stays visible. */
@Composable
internal fun HeroTitleSlot(
    title: String,
    logoUrl: String?,
    isLandscape: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var loaded by remember(logoUrl) { mutableStateOf(false) }
    val logoAlpha by animateFloatAsState(if (loaded) 1f else 0f, tween(180), label = "heroLogoAlpha")
    val latestOnClick by rememberUpdatedState(onClick)
    val onTitleTap = remember { { latestOnClick() } }
    val copyGesture = Modifier.copyOnDoubleTap(context, title, onTitleTap)
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Text(
            text = title, color = KitsugiColors.TextPrimary,
            style = if (isLandscape) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = 1f - logoAlpha }.then(copyGesture)
        )
        if (!logoUrl.isNullOrBlank()) {
            val request = remember(context, logoUrl) {
                ImageRequest.Builder(context)
                    .data(logoUrl)
                    .allowRgb565(false)
                    .size(1280, 384)
                    .precision(Precision.INEXACT)
                    .memoryCacheKey("home-hero-logo:$logoUrl")
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = request, contentDescription = title,
                contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                modifier = Modifier.fillMaxSize().padding(end = 16.dp).graphicsLayer { alpha = logoAlpha }.then(copyGesture),
                onSuccess = { postAfterHeroFrame { loaded = true } },
                onError = { postAfterHeroFrame { loaded = false } }
            )
        }
    }
}
