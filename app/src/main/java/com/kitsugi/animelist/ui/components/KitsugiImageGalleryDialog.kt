package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.utils.uniqueItems
import com.kitsugi.animelist.ui.theme.gradient.Text

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.data.remote.GalleryItem
import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.ui.utils.localizedLabel
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.utils.KitsugiImageDownloadHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KitsugiImageGalleryDialog(
    galleryItems: List<GalleryItem>,
    initialIndex: Int = 0,
    initialCategory: GalleryCategory? = galleryItems.getOrNull(initialIndex)?.category,
    /** Galeri açılışta tek bir kaynağa (TMDB / Fanart.tv / AniList…) filtrelensin mi? */
    initialSource: String? = null,
    title: String,
    isAdult: Boolean = false,
    allowDownload: Boolean = true,
    /** Galeriden indirdiğimiz kopyanın (veya yerel dosyanın) silinmesine izin verilir mi? */
    allowDelete: Boolean = true,
    /** Silme başarılı olduğunda çağrılır — çağıran taraf listesinden bu URL'leri düşürür. */
    onItemsDeleted: (List<String>) -> Unit = {},
    onDismiss: () -> Unit
) {
    if (galleryItems.isEmpty()) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // İndirme index'i: "zaten indirilmiş" rozetini göstermek ve tekrar
    // indirmeyi önlemek için dinlenir. Açılışta arka planda tazelenir.
    val downloadedUrls by KitsugiImageDownloadHelper.downloadedUrls.collectAsState()
    LaunchedEffect(Unit) {
        KitsugiImageDownloadHelper.refreshDownloadedUrls(context)
        // Çökme raporu için iz: hangi galeri, kaç görsel
        try {
            com.kitsugi.animelist.core.diagnostics.KitsugiSessionSupervisor
                .noteScreen("Galeri: $title (${galleryItems.size} görsel)")
        } catch (_: Throwable) {}
    }

    // ── Kaynak (source) grupları ───────────────────────────────────────────
    // Galeri birden fazla API'den beslenir (TMDB, Fanart.tv, AniList, Jikan…).
    // "Kaynaklar" ve "Detaylar" bölümlerinde kaynak listesi gösterilir;
    // seçilen kaynak pager'ı ve kategori sekmelerini birlikte daraltır.
    val availableSources = remember(galleryItems) {
        galleryItems
            .map { it.source.trim() }
            .filter { it.isNotBlank() }
            .groupBy { it.lowercase() }
            .map { (_, sameSource) ->
                GallerySourceGroup(source = sameSource.first(), count = sameSource.size)
            }
            // Alfabetik: detay sayfasındaki "Resimler" seçicisiyle aynı sıra.
            .sortedBy { it.source.lowercase() }
    }

    var selectedSourceFilter by remember(initialSource, availableSources) {
        mutableStateOf(
            initialSource?.trim()?.takeIf { it.isNotBlank() }?.let { wanted ->
                availableSources.firstOrNull { it.source.equals(wanted, ignoreCase = true) }?.source
            }
        )
    }

    // Kaynak filtresi uygulanmış küme — kategori sekmeleri ve sayıları bunun üzerinden hesaplanır.
    val sourceScopedItems = remember(galleryItems, selectedSourceFilter) {
        val source = selectedSourceFilter
        if (source == null) galleryItems
        else galleryItems.filter { it.source.equals(source, ignoreCase = true) }
    }

    // We only show category tabs if there are multiple categories.
    val availableCategories = remember(sourceScopedItems) {
        val cats = sourceScopedItems.map { it.category }.distinct()
        if (cats.size > 1) {
            listOf(null) + cats.sortedBy { it.ordinal } // null representing "All" (Tümü)
        } else {
            emptyList()
        }
    }

    var selectedCategoryFilter by remember(initialCategory, initialIndex) {
        mutableStateOf<GalleryCategory?>(initialCategory)
    }

    // Seçili kategori yeni kaynakta bulunmuyorsa "Tümü"ne dön — aksi hâlde pager boş kalırdı.
    val effectiveCategoryFilter = selectedCategoryFilter?.takeIf { cat ->
        sourceScopedItems.any { it.category == cat }
    }
    LaunchedEffect(selectedCategoryFilter, sourceScopedItems) {
        if (selectedCategoryFilter != null && effectiveCategoryFilter == null) {
            selectedCategoryFilter = null
        }
    }

    // Filtered items based on selected category + source
    val filteredItems = remember(sourceScopedItems, effectiveCategoryFilter) {
        if (effectiveCategoryFilter == null) {
            sourceScopedItems
        } else {
            sourceScopedItems.filter { it.category == effectiveCategoryFilter }
        }
    }

    val resolvedInitialPage = remember(galleryItems, initialIndex, filteredItems) {
        val initialItem = galleryItems.getOrNull(initialIndex)
        if (initialItem != null) {
            val idx = filteredItems.indexOf(initialItem)
            if (idx >= 0) idx else 0
        } else {
            0
        }
    }

    val pagerState = rememberPagerState(initialPage = resolvedInitialPage.coerceIn(0, maxOf(0, filteredItems.size - 1)), pageCount = { filteredItems.size })

    var isFirstLaunch by remember { mutableStateOf(true) }

    // Reset to page 0 if selectedCategoryFilter / selectedSourceFilter changes to avoid index
    // bounds error, ignoring first launch
    LaunchedEffect(selectedCategoryFilter, selectedSourceFilter) {
        if (isFirstLaunch) {
            isFirstLaunch = false
        } else {
            if (filteredItems.isNotEmpty()) {
                pagerState.scrollToPage(0)
            }
        }
    }

    val accentColor = LocalKitsugiAccent.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Galeride gezinme izi (çökme raporunda görünür)
    LaunchedEffect(pagerState.currentPage, filteredItems.size) {
        try {
            com.kitsugi.animelist.core.diagnostics.KitsugiSessionSupervisor
                .noteAction("galeri gezinme: sayfa ${pagerState.currentPage + 1}/${filteredItems.size}")
        } catch (_: Throwable) {}
    }

    // Slide-up and fade transitions state
    var isAnimatedVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isAnimatedVisible = true
    }

    val dismissWithAnimation = {
        scope.launch {
            isAnimatedVisible = false
            delay(280)
            onDismiss()
        }
    }

    // Permission launcher
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            val currentItem = filteredItems.getOrNull(pagerState.currentPage)
            if (currentItem != null) {
                KitsugiImageDownloadHelper.downloadImage(context, currentItem.url, title)
            }
        } else {
            android.widget.Toast.makeText(context, context.getString(R.string.gallery_storage_permission_denied), android.widget.Toast.LENGTH_SHORT).show()
        }
    }


    // PERF: Boştayken bile tüm diyaloğu (arka plan, 4K görsel katmanları,
    // küçük resim şeritleri, rozetler) saniyede 60-120 kez yeniden compose eden sonsuz
    // rememberInfiniteTransition kaldırıldı; sabit parlaklık sıfır kare maliyetiyle kullanılır.
    val downloadGlow = 1f

    //  • Yerel dosya (file:// / content://) ise buton hiç gösterilmez — içerik zaten cihazda.
    //  • allowDownload=false ise buton hiç gösterilmez (örn. İndirilenler ekranı).
    //  • Daha önce indirilmişse buton "indirildi" (tik) durumunda gösterilir.
    val currentActionItem = filteredItems.getOrNull(pagerState.currentPage)
    val isLocalImage = currentActionItem?.url?.let {
        it.startsWith("file://") || it.startsWith("content://")
    } == true
    val showDownloadButton = allowDownload && !isLocalImage
    val isAlreadyDownloaded = !isLocalImage &&
        currentActionItem != null &&
        downloadedUrls.contains(currentActionItem.url)

    val onDownload = {
        val currentItem = filteredItems.getOrNull(pagerState.currentPage)
        if (currentItem != null) {
            val url = currentItem.url
            when {
                url.startsWith("file://") || url.startsWith("content://") -> {
                    android.widget.Toast.makeText(context, context.getString(R.string.image_already_on_device), android.widget.Toast.LENGTH_SHORT).show()
                }
                downloadedUrls.contains(url) -> {
                    android.widget.Toast.makeText(context, context.getString(R.string.image_already_downloaded), android.widget.Toast.LENGTH_SHORT).show()
                }
                KitsugiImageDownloadHelper.hasWritePermission(context) -> {
                    KitsugiImageDownloadHelper.downloadImage(context, url, title)
                }
                else -> {
                    launcher.launch(KitsugiImageDownloadHelper.getRequiredPermissions())
                }
            }
        }
    }

    val onShare = {
        val currentItem = filteredItems.getOrNull(pagerState.currentPage)
        if (currentItem != null) {
            KitsugiImageDownloadHelper.shareImage(context, currentItem.url, title)
        }
    }

    // ── SİLME (3. buton) ───────────────────────────────────────────────────
    //  • İndirilmiş bir kopya varsa (index'te duruyor) veya galeri yerel bir dosyayı
    //    (file:// / content://) gösteriyorsa buton aktiftir — dosya yerinden silinir.
    //  • Henüz indirilmemiş uzak görsellerde buton pasif görünür; dokununca neden
    //    silinemediği kısa mesajla anlatılır (kullanıcı "buton var ama çalışmıyor"
    //    demesin diye sessiz bırakılmadı).
    val currentDeleteUrl = currentActionItem?.url
    var canDeleteCurrent by remember { mutableStateOf(false) }
    var pendingDeleteUrls by remember { mutableStateOf<List<String>?>(null) }
    var isDeleting by remember { mutableStateOf(false) }

    // Scoped Storage: uygulamanın sahibi olmadığı MediaStore kayıtları (ör. uygulama
    // yeniden kurulduktan sonra kalan eski görseller) yalnızca sistem onayıyla silinir.
    var pendingConsentUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    val systemConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val urls = pendingConsentUrls
        pendingConsentUrls = emptyList()
        if (result.resultCode == Activity.RESULT_OK) {
            KitsugiImageDownloadHelper.refreshDownloadedUrls(context)
            if (urls.isNotEmpty()) onItemsDeleted(urls)
        } else {
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.downloads_delete_cancelled),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    LaunchedEffect(currentDeleteUrl, downloadedUrls, allowDelete) {
        if (!allowDelete || currentDeleteUrl.isNullOrBlank()) {
            canDeleteCurrent = false
        } else {
            canDeleteCurrent = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                KitsugiImageDownloadHelper.hasDeletableCopy(context, currentDeleteUrl)
            }
        }
    }

    // Liste küçülürse (silme sonrası çağıran taraf öğeleri düşürür) sayfa sınırda kalmasın
    LaunchedEffect(filteredItems.size) {
        if (filteredItems.isEmpty()) return@LaunchedEffect
        if (pagerState.currentPage > filteredItems.size - 1) {
            pagerState.scrollToPage(filteredItems.size - 1)
        }
    }

    val confirmDelete = { urls: List<String> ->
        if (urls.isNotEmpty() && !isDeleting) {
            isDeleting = true
            scope.launch {
                var deleted = 0
                var lastError: String? = null
                val consentUris = mutableListOf<Uri>()
                var consentRequested = false
                urls.forEach { url ->
                    when (val outcome = KitsugiImageDownloadHelper.deleteImageForUrl(context, url)) {
                        is KitsugiImageDownloadHelper.GalleryDeleteOutcome.Success -> deleted++
                        is KitsugiImageDownloadHelper.GalleryDeleteOutcome.Failed ->
                            lastError = outcome.message
                        is KitsugiImageDownloadHelper.GalleryDeleteOutcome.RequiresSystemConsent -> {
                            lastError = outcome.message
                            consentUris += outcome.uris
                        }
                        KitsugiImageDownloadHelper.GalleryDeleteOutcome.NothingToDelete -> Unit
                    }
                }
                // Silinemeyenler yalnızca sistem onayı bekliyorsa onay diyaloğunu aç.
                if (consentUris.isNotEmpty()) {
                    val sender = KitsugiImageDownloadHelper.systemDeleteRequestSender(
                        context,
                        consentUris.distinct()
                    )
                    if (sender != null) {
                        pendingConsentUrls = urls
                        consentRequested = runCatching {
                            systemConsentLauncher.launch(IntentSenderRequest.Builder(sender).build())
                        }.isSuccess
                    }
                }
                // İndirme rozetleri ve indeks zaten güncellendi; galeriyi tazeleriz.
                KitsugiImageDownloadHelper.refreshDownloadedUrls(context)
                isDeleting = false
                pendingDeleteUrls = null
                when {
                    deleted > 0 -> {
                        onItemsDeleted(urls)
                        android.widget.Toast.makeText(
                            context,
                            if (deleted > 1) context.getString(R.string.gallery_deleted_multiple, deleted)
                                else context.getString(R.string.gallery_deleted_single),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    // Sistem onayı açıldıysa ayrıca hata toast'ı gösterme.
                    consentRequested -> Unit
                    lastError != null ->
                        android.widget.Toast.makeText(context, lastError, android.widget.Toast.LENGTH_LONG).show()
                    else ->
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.gallery_no_downloaded_copy),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                }
            }
        }
    }

    val onRequestDelete = {
        val url = currentDeleteUrl
        when {
            url.isNullOrBlank() -> Unit
            canDeleteCurrent -> pendingDeleteUrls = listOf(url)
            else -> android.widget.Toast.makeText(
                context,
                context.getString(R.string.gallery_not_downloaded),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    Dialog(
        onDismissRequest = { dismissWithAnimation() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogView = androidx.compose.ui.platform.LocalView.current
        if (!dialogView.isInEditMode && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            androidx.compose.runtime.SideEffect {
                runCatching {
                    dialogView.isForceDarkAllowed = false
                    dialogView.rootView?.isForceDarkAllowed = false
                }
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = isAnimatedVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = 300, easing = EaseOutCubic)
                ) + fadeIn(animationSpec = tween(300)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(durationMillis = 280, easing = EaseInCubic)
                ) + fadeOut(animationSpec = tween(280)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    KitsugiColors.Surface.copy(alpha = 0.97f),
                                    KitsugiColors.Background.copy(alpha = 0.99f)
                                ),
                                radius = 1800f
                            )
                        )
                ) {
                    // Ambient glow
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        accentColor.copy(alpha = 0.04f),
                                        Color.Transparent,
                                        accentColor.copy(alpha = 0.03f)
                                    )
                                )
                            )
                    )

                    if (isLandscape) {
                        // ── LANDSCAPE LAYOUT (fanart.tv style) ─────────────────────────
                        GalleryLandscapeLayout(
                            filteredItems = filteredItems,
                            availableCategories = availableCategories,
                            selectedCategoryFilter = selectedCategoryFilter,
                            onCategorySelected = { selectedCategoryFilter = it },
                            pagerState = pagerState,
                            title = title,
                            accentColor = accentColor,
                            downloadGlow = downloadGlow,
                            onDownload = onDownload,
                            onShare = onShare,
                            onDismiss = { dismissWithAnimation() },
                            density = density,
                            sourceScopedItems = sourceScopedItems,
                            sources = availableSources,
                            selectedSource = selectedSourceFilter,
                            onSourceSelected = { selectedSourceFilter = it },
                            isAdult = isAdult,
                            showDownloadButton = showDownloadButton,
                            isAlreadyDownloaded = isAlreadyDownloaded,
                            allowDelete = allowDelete,
                            deleteActive = canDeleteCurrent,
                            onRequestDelete = onRequestDelete
                        )
                    } else {
                        // ── PORTRAIT LAYOUT (existing behaviour) ───────────────────────
                        GalleryPortraitLayout(
                            filteredItems = filteredItems,
                            availableCategories = availableCategories,
                            selectedCategoryFilter = selectedCategoryFilter,
                            onCategorySelected = { selectedCategoryFilter = it },
                            pagerState = pagerState,
                            title = title,
                            accentColor = accentColor,
                            downloadGlow = downloadGlow,
                            onDownload = onDownload,
                            onShare = onShare,
                            onDismiss = { dismissWithAnimation() },
                            density = density,
                            sourceScopedItems = sourceScopedItems,
                            sources = availableSources,
                            selectedSource = selectedSourceFilter,
                            onSourceSelected = { selectedSourceFilter = it },
                            isAdult = isAdult,
                            showDownloadButton = showDownloadButton,
                            isAlreadyDownloaded = isAlreadyDownloaded,
                            allowDelete = allowDelete,
                            deleteActive = canDeleteCurrent,
                            onRequestDelete = onRequestDelete
                        )
                    }
                }
            }
        }
    }

    // ── Silme onayı ────────────────────────────────────────────────────────
    val pendingDelete = pendingDeleteUrls
    if (allowDelete && !pendingDelete.isNullOrEmpty()) {
        val targetLabel = remember(pendingDelete) {
            KitsugiImageDownloadHelper.describeDeleteTarget(context, pendingDelete.first())
        }
        KitsugiConfirmDialog(
            title = stringResource(R.string.gallery_delete_image_title),
            message = stringResource(R.string.gallery_delete_image_message, targetLabel.orEmpty()),
            confirmText = stringResource(R.string.gallery_delete_confirm),
            dismissText = stringResource(R.string.vazgec),
            isDestructive = true,
            onConfirm = { confirmDelete(pendingDelete) },
            onDismiss = { pendingDeleteUrls = null }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LANDSCAPE LAYOUT  –  fanart.tv style
//   Left: vertical thumbnail rail
//   Center: full image + left/right nav arrows
//   Right: detail panel (source, category, language, resolution, etc.)
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryLandscapeLayout(
    filteredItems: List<GalleryItem>,
    availableCategories: List<GalleryCategory?>,
    selectedCategoryFilter: GalleryCategory?,
    onCategorySelected: (GalleryCategory?) -> Unit,
    pagerState: androidx.compose.foundation.pager.PagerState,
    title: String,
    accentColor: Color,
    downloadGlow: Float,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    density: androidx.compose.ui.unit.Density,
    sourceScopedItems: List<GalleryItem>,
    /** Tüm galerideki kaynak grupları (Kaynak filtresi listesi için). */
    sources: List<GallerySourceGroup> = emptyList(),
    /** Etkin kaynak filtresi — null ise tüm kaynaklar görünür. */
    selectedSource: String? = null,
    onSourceSelected: (String?) -> Unit = {},
    isAdult: Boolean = false,
    showDownloadButton: Boolean = true,
    isAlreadyDownloaded: Boolean = false,
    allowDelete: Boolean = true,
    deleteActive: Boolean = false,
    onRequestDelete: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val currentItem = filteredItems.getOrNull(pagerState.currentPage)
    Row(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 18.dp)
    ) {

        // ── LEFT: Vertical thumbnail rail ─────────────────────────────────────
        val thumbRailState = rememberLazyListState()

        LaunchedEffect(pagerState.currentPage) {
            val viewportHeight = thumbRailState.layoutInfo.viewportEndOffset - thumbRailState.layoutInfo.viewportStartOffset
            if (viewportHeight > 0) {
                val itemHeightPx = with(density) { 72.dp.roundToPx() }
                val targetOffset = (viewportHeight - itemHeightPx) / 2
                thumbRailState.animateScrollToItem(pagerState.currentPage, -targetOffset)
            } else {
                thumbRailState.animateScrollToItem(pagerState.currentPage)
            }
        }

        Box(
            modifier = Modifier
                .width(84.dp)
                .fillMaxHeight()
                .background(KitsugiColors.Background.copy(alpha = 0.85f))
                .border(
                    width = 1.dp,
                    color = KitsugiColors.Border.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(0.dp)
                )
        ) {
            LazyColumn(
                state = thumbRailState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                itemsIndexed(filteredItems) { index, item ->
                    val isSelected = pagerState.currentPage == index
                    val thumbScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.05f else 0.92f,
                        animationSpec = spring(dampingRatio = 0.65f),
                        label = "ls_thumb_scale_$index"
                    )
                    val thumbAlpha by animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0.45f,
                        label = "ls_thumb_alpha_$index"
                    )

                    val thumbModel = GalleryThumbnailImagePolicy.rememberThumbnailImageRequest(
                        url = item.url,
                        widthDp = 62.dp,
                        heightDp = 80.dp
                    )
                    Box(
                        modifier = Modifier
                            .graphicsLayer(scaleX = thumbScale, scaleY = thumbScale, alpha = thumbAlpha)
                    ) {
                        KitsugiNsfwImage(
                            model = thumbModel,
                            contentDescription = stringResource(R.string.gallery_thumbnail_description, index),
                            isAdult = isAdult,
                            modifier = Modifier
                                .size(width = 62.dp, height = 80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) accentColor else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .tvClickable(shape = RoundedCornerShape(8.dp)) {
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                },
                            contentScale = ContentScale.Crop
                        )
                        // Active indicator bar at left edge
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                                    .background(accentColor)
                                    .align(Alignment.CenterStart)
                                    .offset(x = (-8).dp)
                            )
                        }
                    }
                }
            }
        }

        // ── CENTER: Main image with nav arrows ────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // Category filter bar at top (compact)
            Column(modifier = Modifier.fillMaxSize()) {
                if (availableCategories.isNotEmpty() || selectedSource != null) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, top = 6.dp, end = 12.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Etkin kaynak filtresi — dokununca temizlenir.
                        if (selectedSource != null) {
                            item(key = "active_source_chip") {
                                GalleryActiveSourceChip(
                                    source = selectedSource,
                                    accentColor = accentColor,
                                    onClick = { onSourceSelected(null) }
                                )
                            }
                        }
                        itemsIndexed(availableCategories) { _, cat ->
                            val isSelected = selectedCategoryFilter == cat
                            val count = if (cat == null) sourceScopedItems.size else sourceScopedItems.count { it.category == cat }
                            val label = if (cat == null) stringResource(R.string.action_all_count, count) else "${cat.localizedLabel()} ($count)"
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (isSelected) accentColor else KitsugiColors.SurfaceSoft)
                                    .tvClickable(shape = RoundedCornerShape(999.dp)) { onCategorySelected(cat) }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) KitsugiColors.Background else KitsugiColors.TextPrimary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Pager image
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        pageSpacing = 8.dp,
                        verticalAlignment = Alignment.CenterVertically
                    ) { page ->
                        val item = filteredItems.getOrNull(page)
                        if (item != null) {
                            GalleryImagePage(
                                imageUrl = item.url,
                                title = title,
                                page = page,
                                pagerState = pagerState,
                                isAdult = isAdult
                            )
                        }
                    }

                    // Left navigation arrow
                    if (pagerState.currentPage > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 8.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.82f))
                                .border(1.dp, KitsugiColors.Border.copy(alpha = 0.6f), CircleShape)
                                .tvClickable(shape = CircleShape) {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.ChevronLeft,
                                contentDescription = stringResource(R.string.onceki),
                                tint = KitsugiColors.TextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Right navigation arrow
                    if (pagerState.currentPage < filteredItems.size - 1) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 8.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.82f))
                                .border(1.dp, KitsugiColors.Border.copy(alpha = 0.6f), CircleShape)
                                .tvClickable(shape = CircleShape) {
                                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.ChevronRight,
                                contentDescription = stringResource(R.string.gallery_next_image),
                                tint = KitsugiColors.TextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Bottom: page counter
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (filteredItems.size > 1) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${pagerState.currentPage + 1}",
                                color = accentColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "/ ${filteredItems.size}",
                                color = KitsugiColors.TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // ── RIGHT: Detail panel (fanart.tv right side) ────────────────────────
        Box(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .background(KitsugiColors.Surface.copy(alpha = 0.94f))
                .border(
                    width = 1.dp,
                    color = KitsugiColors.Border.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(0.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                // Top action row (Download | Share | Close)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Download — yerel dosyalarda gizli; zaten indirilmişse tik işareti
                    if (showDownloadButton) {
                        val downloadBtnColor =
                            if (isAlreadyDownloaded) KitsugiColors.AccentGreen else accentColor
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    color = downloadBtnColor.copy(alpha = 0.18f * downloadGlow),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .border(1.dp, downloadBtnColor.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                .tvClickable(shape = RoundedCornerShape(10.dp), onClick = onDownload),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isAlreadyDownloaded) Icons.CheckCircle else Icons.Download,
                                contentDescription = stringResource(if (isAlreadyDownloaded) R.string.gallery_downloaded_status else R.string.gallery_download_action),
                                tint = downloadBtnColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Share / Send (Animated Fly)
                    KitsugiFlySendIconButton(
                        onClick = onShare,
                        size = 36.dp,
                        contentDescription = stringResource(R.string.paylas)
                    )

                    // Silme Butonu — indirilen kopyayı (veya yerel dosyayı) kaldırır.
                    if (allowDelete) {
                        KitsugiGalleryDeleteButton(
                            size = 36.dp,
                            iconSize = 18.dp,
                            corner = 10.dp,
                            active = deleteActive,
                            glow = downloadGlow,
                            onClick = onRequestDelete
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Close
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = KitsugiColors.SurfaceStrong.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .border(1.dp, KitsugiColors.Border, RoundedCornerShape(10.dp))
                            .tvClickable(shape = RoundedCornerShape(10.dp), onClick = { onDismiss() }),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Close,
                            contentDescription = stringResource(R.string.kapat),
                            tint = KitsugiColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Title
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    color = accentColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Image,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            text = title,
                            color = KitsugiColors.TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Detail tabs: Details / Fans / Comments — mimic fanart.tv
                var detailTab by remember { mutableStateOf(0) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(stringResource(R.string.gallery_tab_details), stringResource(R.string.gallery_tab_sources)).forEachIndexed { idx, tabLabel ->
                        val sel = detailTab == idx
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (sel) accentColor.copy(alpha = 0.15f) else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (sel) accentColor.copy(alpha = 0.5f) else KitsugiColors.Border.copy(alpha = 0.4f),
                                    RoundedCornerShape(8.dp)
                                )
                                .tvClickable(shape = RoundedCornerShape(8.dp)) { detailTab = idx }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = tabLabel,
                                color = if (sel) accentColor else KitsugiColors.TextMuted,
                                fontSize = 11.sp,
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(KitsugiColors.Border.copy(alpha = 0.4f))
                )

                // Panel content
                if (detailTab == 0) {
                    // ── Details tab ──
                    val langInfo = currentItem?.let { formatLanguage(it.language) }
                    val seasonLabel = currentItem?.let { formatGallerySeason(it.season) }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        if (currentItem != null) {
                            // Source
                            val badgeBg = when (currentItem.source.lowercase()) {
                                "tmdb"      -> Color(0xFFFFB800)
                                "fanart.tv" -> Color(0xFF9C27B0)
                                "anilist"   -> Color(0xFF02A9FF)
                                "simkl"     -> Color(0xFFE50914)
                                "kitsu"     -> Color(0xFFE35A02)
                                "shikimori" -> Color(0xFF4C86C8)
                                "bangumi"   -> Color(0xFFF09199)
                                "jikan", "jikan (mal)", "mal" -> Color(0xFF2E51A2)
                                else        -> accentColor
                            }

                            item {
                                DetailRow(
                                    label = stringResource(R.string.gallery_field_source),
                                    value = null,
                                    badge = {
                                        val logoRes = KitsugiPlatformLogos.resFor(currentItem.source)
                                        if (logoRes != null) {
                                            KitsugiPlatformLogo(
                                                platformId = currentItem.source,
                                                size = 28.dp,
                                                cornerRadius = 6.dp
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .background(badgeBg)
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Text(
                                                    text = currentItem.source,
                                                    color = if (currentItem.source.equals("tmdb", ignoreCase = true)) Color.Black else Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                )
                            }

                            // Category
                            val catIcon = galleryCategoryIcon(currentItem.category)
                            item {
                                DetailRow(label = stringResource(R.string.gallery_field_type), value = currentItem.category.localizedLabel(), icon = catIcon)
                            }

                            // Language
                            if (langInfo != null) {
                                item {
                                    DetailRow(label = stringResource(R.string.gallery_field_language), value = "${langInfo.first} ${langInfo.second}")
                                }
                            } else if (currentItem.language == null &&
                                (currentItem.category == GalleryCategory.BACKDROP ||
                                 currentItem.category == GalleryCategory.LOGO ||
                                 currentItem.category == GalleryCategory.CLEARART)
                            ) {
                                item {
                                    DetailRow(label = stringResource(R.string.gallery_field_language), value = stringResource(R.string.gallery_textless))
                                }
                            }

                            if (seasonLabel != null) {
                                item {
                                    DetailRow(label = stringResource(R.string.gallery_field_season), value = seasonLabel)
                                }
                            }

                            // Resolution
                            val resStr = formatResolution(currentItem.width, currentItem.height)
                            if (resStr != null) {
                                item {
                                    DetailRow(label = stringResource(R.string.gallery_field_size), value = resStr)
                                }
                            }

                            // Description
                            if (!currentItem.description.isNullOrBlank() &&
                                currentItem.description != "TMDB Poster" &&
                                currentItem.description != "TMDB Arka Plan" &&
                                currentItem.description != "TMDB Logo"
                            ) {
                                item {
                                    DetailRow(label = stringResource(R.string.aciklama), value = currentItem.description)
                                }
                            }

                            // API'ye özgü detay satırları (örn. Fanart.tv: Ad, IMDb ID)
                            currentItem.details.forEach { (label, value) ->
                                item {
                                    DetailRow(label = label, value = value)
                                }
                            }

                            // Separator
                            item { Spacer(modifier = Modifier.height(8.dp)) }

                            // Page indicator
                            item {
                                DetailRow(
                                    label = stringResource(R.string.gallery_field_page),
                                    value = "${pagerState.currentPage + 1} / ${filteredItems.size}"
                                )
                            }
                        }
                    }
                } else {
                    // ── Kaynaklar sekmesi ──
                    //  Seçilen kaynak görsel ızgarasını ve pager'ı birlikte daraltır.
                    //  Kullanıcı tercihi doğrultusunda bu sekmede resim bulunmaz; yalnızca kaynaklar listelenir.
                    val totalImages = remember(sources) { sources.sumOf { it.count } }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item(key = "source_section_title") {
                            GallerySourceSectionTitle(stringResource(R.string.gallery_tab_sources))
                        }
                        if (sources.isNotEmpty()) {
                            item(key = "source_all") {
                                GallerySourceRow(
                                    label = stringResource(R.string.gallery_source_all),
                                    source = null,
                                    count = totalImages,
                                    selected = selectedSource == null,
                                    onClick = { onSourceSelected(null) }
                                )
                            }
                            uniqueItems(sources, key = { group -> "source_${group.source.lowercase()}" }) { group ->
                                GallerySourceRow(
                                    label = group.source,
                                    source = group.source,
                                    count = group.count,
                                    selected = group.source.equals(selectedSource, ignoreCase = true),
                                    onClick = { onSourceSelected(group.source) }
                                )
                            }
                        } else {
                            item(key = "no_sources") {
                                Text(
                                    text = stringResource(R.string.gallery_no_sources_found),
                                    color = KitsugiColors.TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PORTRAIT LAYOUT  –  original behaviour
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryPortraitLayout(
    filteredItems: List<GalleryItem>,
    availableCategories: List<GalleryCategory?>,
    selectedCategoryFilter: GalleryCategory?,
    onCategorySelected: (GalleryCategory?) -> Unit,
    pagerState: androidx.compose.foundation.pager.PagerState,
    title: String,
    accentColor: Color,
    downloadGlow: Float,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    density: androidx.compose.ui.unit.Density,
    sourceScopedItems: List<GalleryItem>,
    /** Tüm galerideki kaynak grupları (Kaynak filtresi listesi için). */
    sources: List<GallerySourceGroup> = emptyList(),
    /** Etkin kaynak filtresi — null ise tüm kaynaklar görünür. */
    selectedSource: String? = null,
    onSourceSelected: (String?) -> Unit = {},
    isAdult: Boolean = false,
    showDownloadButton: Boolean = true,
    isAlreadyDownloaded: Boolean = false,
    allowDelete: Boolean = true,
    deleteActive: Boolean = false,
    onRequestDelete: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()

    // Dikey modda ayrıntı paneli için yer yoktur (yatay modun sağ paneli gibi);
    // bu yüzden tüm detaylar "Detaylar" butonuyla açılan alt levhada toplanır.
    var showDetailsSheet by remember { mutableStateOf(false) }
    val currentPortraitItem = filteredItems.getOrNull(pagerState.currentPage)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header (üst)
        KitsugiGalleryHeader(
            title = title,
            currentPage = pagerState.currentPage,
            totalPages = filteredItems.size,
            accentColor = accentColor,
            downloadGlow = downloadGlow,
            onDownload = onDownload,
            onShare = onShare,
            onDismiss = onDismiss,
            isLandscape = false,
            showDownloadButton = showDownloadButton,
            isAlreadyDownloaded = isAlreadyDownloaded,
            showDetailsButton = currentPortraitItem != null,
            onShowDetails = { showDetailsSheet = true },
            allowDelete = allowDelete,
            deleteActive = deleteActive,
            onRequestDelete = onRequestDelete
        )

        // Categories filter (if multiple categories available)
        if (availableCategories.isNotEmpty() || selectedSource != null) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Etkin kaynak filtresi — dokununca temizlenir.
                if (selectedSource != null) {
                    item(key = "active_source_chip") {
                        GalleryActiveSourceChip(
                            source = selectedSource,
                            accentColor = accentColor,
                            onClick = { onSourceSelected(null) }
                        )
                    }
                }
                itemsIndexed(availableCategories) { _, cat ->
                    val isSelected = selectedCategoryFilter == cat
                    val count = if (cat == null) sourceScopedItems.size else sourceScopedItems.count { it.category == cat }
                    val label = if (cat == null) stringResource(R.string.action_all_count, count) else "${cat.localizedLabel()} ($count)"

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (isSelected) accentColor else KitsugiColors.SurfaceSoft)
                            .tvClickable(shape = RoundedCornerShape(999.dp)) {
                                onCategorySelected(cat)
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) KitsugiColors.Background else KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Main Pager & Image area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 16.dp,
                verticalAlignment = Alignment.CenterVertically
            ) { page ->
                val item = filteredItems.getOrNull(page)
                if (item != null) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        GalleryImagePage(
                            imageUrl = item.url,
                            title = title,
                            page = page,
                            pagerState = pagerState,
                            isAdult = isAdult
                        )

                        // Source, Category & Metadata badge overlay on the page
                        FlowRow(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.88f))
                                .border(1.dp, KitsugiColors.Border.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val badgeBg = when (item.source.lowercase()) {
                                "tmdb"      -> Color(0xFFFFB800)
                                "fanart.tv" -> Color(0xFF9C27B0)
                                "anilist"   -> Color(0xFF02A9FF)
                                "simkl"     -> Color(0xFFE50914)
                                "kitsu"     -> Color(0xFFE35A02)
                                "shikimori" -> Color(0xFF4C86C8)
                                "bangumi"   -> Color(0xFFF09199)
                                "jikan", "jikan (mal)", "mal" -> Color(0xFF2E51A2)
                                else        -> accentColor
                            }

                            // 1. Kaynak Rozeti
                            val logoRes = KitsugiPlatformLogos.resFor(item.source)
                            if (logoRes != null) {
                                KitsugiPlatformLogo(
                                    platformId = item.source,
                                    size = 22.dp,
                                    cornerRadius = 5.dp
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(badgeBg)
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = item.source,
                                        color = if (item.source.equals("tmdb", ignoreCase = true)) Color.Black else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // 2. Kategori Rozeti
                            val catIcon = galleryCategoryIcon(item.category)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(KitsugiColors.SurfaceSoft)
                                    .border(1.dp, KitsugiColors.Border, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                KitsugiEmojiLabel(
                                    label = item.category.localizedLabel(),
                                    color = KitsugiColors.TextPrimary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    iconSize = 11.dp,
                                    icon = catIcon
                                )
                            }

                            // 2b. Sezon Rozeti
                            val seasonLabel = formatGallerySeason(item.season)
                            if (seasonLabel != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(KitsugiColors.SurfaceSoft)
                                        .border(1.dp, KitsugiColors.Border, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = seasonLabel,
                                        color = KitsugiColors.TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // 3. Dil Bilgisi
                            val langInfo = formatLanguage(item.language)
                            if (langInfo != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(KitsugiColors.SurfaceSoft)
                                        .border(1.dp, KitsugiColors.Border, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "${langInfo.first} ${langInfo.second}",
                                        color = KitsugiColors.TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            } else if (item.language == null && (item.category == GalleryCategory.BACKDROP || item.category == GalleryCategory.LOGO || item.category == GalleryCategory.CLEARART)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(KitsugiColors.SurfaceSoft.copy(alpha = 0.6f))
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.gallery_textless),
                                        color = KitsugiColors.TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                }
                            }

                            // 4. Çözünürlük Bilgisi
                            val resStr = formatResolution(item.width, item.height)
                            if (resStr != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(KitsugiColors.SurfaceSoft)
                                        .border(1.dp, KitsugiColors.Border, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 7.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = resStr,
                                        color = KitsugiColors.TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // 5. Özel Açıklama (varsa)
                            if (!item.description.isNullOrBlank() &&
                                item.description != "TMDB Poster" &&
                                item.description != "TMDB Arka Plan" &&
                                item.description != "TMDB Logo") {
                                Text(
                                    text = item.description,
                                    color = KitsugiColors.TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Thumbnail Strip (alt)
        if (filteredItems.size > 1) {
            val listState = rememberLazyListState()

            LaunchedEffect(pagerState.currentPage) {
                val listInfo = listState.layoutInfo
                val viewportWidth = listInfo.viewportEndOffset - listInfo.viewportStartOffset
                if (viewportWidth > 0) {
                    val itemWidthPx = with(density) { 52.dp.roundToPx() }
                    val targetOffset = (viewportWidth - itemWidthPx) / 2
                    listState.animateScrollToItem(pagerState.currentPage, -targetOffset)
                } else {
                    listState.animateScrollToItem(pagerState.currentPage)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                KitsugiColors.Background.copy(alpha = 0.9f),
                                KitsugiColors.Surface.copy(alpha = 0.96f)
                            )
                        )
                    )
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp, top = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                LazyRow(
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    itemsIndexed(filteredItems) { index, item ->
                        val isSelected = pagerState.currentPage == index
                        val thumbScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.12f else 0.88f,
                            animationSpec = spring(dampingRatio = 0.6f),
                            label = "thumb_scale_$index"
                        )
                        val thumbAlpha by animateFloatAsState(
                            targetValue = if (isSelected) 1f else 0.38f,
                            label = "thumb_alpha_$index"
                        )

                        val thumbModel = GalleryThumbnailImagePolicy.rememberThumbnailImageRequest(
                            url = item.url,
                            widthDp = 48.dp,
                            heightDp = 64.dp
                        )
                        Box(
                            modifier = Modifier
                                .graphicsLayer(scaleX = thumbScale, scaleY = thumbScale, alpha = thumbAlpha)
                        ) {
                            KitsugiNsfwImage(
                                model = thumbModel,
                                contentDescription = stringResource(R.string.gallery_thumbnail_description, index),
                                isAdult = isAdult,
                                modifier = Modifier
                                    .size(width = 48.dp, height = 64.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        width = if (isSelected) 2.dp else 0.dp,
                                        color = if (isSelected) accentColor else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .tvClickable(shape = RoundedCornerShape(10.dp)) {
                                        scope.launch { pagerState.animateScrollToPage(index) }
                                    },
                                contentScale = ContentScale.Crop
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .width(16.dp)
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(accentColor)
                                        .align(Alignment.BottomCenter)
                                        .offset(y = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Dikey mod detay levhası — yatay modun sağ panelinin uyarlanmış hâli ──
    if (showDetailsSheet && currentPortraitItem != null) {
        GalleryDetailsSheet(
            item = currentPortraitItem,
            page = pagerState.currentPage,
            total = filteredItems.size,
            sources = sources,
            selectedSource = selectedSource,
            onSourceSelected = { source ->
                // Kaynak seçimi galeriyi filtreler; levha kapanır ki sonuç hemen görünsün.
                showDetailsSheet = false
                onSourceSelected(source)
            },
            onDismiss = { showDetailsSheet = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PORTRAIT DETAILS SHEET
//   Dikey (telefonsu) modda yatay düzenin sağ ayrıntı paneline yer yoktur.
//   Bu alt levha aynı bilgileri mantıksal olarak uyarlar: Kaynak, Tür, Dil,
//   Boyut, API detayları (örn. Fanart.tv Ad / IMDb ID), Açıklama ve Sayfa.
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryDetailsSheet(
    item: GalleryItem,
    page: Int,
    total: Int,
    sources: List<GallerySourceGroup> = emptyList(),
    selectedSource: String? = null,
    onSourceSelected: (String?) -> Unit = {},
    onDismiss: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val badgeBg = when (item.source.lowercase()) {
        "tmdb"      -> Color(0xFFFFB800)
        "fanart.tv" -> Color(0xFF9C27B0)
        "anilist"   -> Color(0xFF02A9FF)
        "simkl"     -> Color(0xFFE50914)
        "kitsu"     -> Color(0xFFE35A02)
        "shikimori" -> Color(0xFF4C86C8)
        "bangumi"   -> Color(0xFFF09199)
        "jikan", "jikan (mal)", "mal" -> Color(0xFF2E51A2)
        else        -> accentColor
    }

    val catIcon = galleryCategoryIcon(item.category)

    val langInfo = formatLanguage(item.language)
    val resStr = formatResolution(item.width, item.height)

    // Ayrıntı levhasının kaydırılabilir içeriği: yükseklik HER KOŞULDA sınırlı tutulur.
    // Sheet penceresi sınırsız (Infinity) dikey kısıt verirse `verticalScroll` şu hatayla
    // uygulamayı kapatıyordu: "Vertically scrollable component was measured with an
    // infinity maximum height constraints". `KitsugiSheetOrDialog` ve `WikiItemSheet`
    // aynı korumayı `heightIn(max = ...)` ile uygular (galeri çökme raporu 2026-10-13).
    val maxContentHeight = LocalConfiguration.current.screenHeightDp.dp * 0.85f

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = KitsugiColors.Surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(KitsugiColors.TextMuted.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxContentHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.gallery_details_title),
                color = KitsugiColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))

            // Kaynak (logo + ad)
            DetailRow(label = stringResource(R.string.gallery_field_source), value = null, badge = {
                val logoRes = KitsugiPlatformLogos.resFor(item.source)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (logoRes != null) {
                        KitsugiPlatformLogo(
                            platformId = item.source,
                            size = 28.dp,
                            cornerRadius = 6.dp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(badgeBg)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = item.source,
                                color = if (item.source.equals("tmdb", ignoreCase = true)) Color.Black else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            })

            // Tür
            DetailRow(label = stringResource(R.string.gallery_field_type), value = item.category.localizedLabel(), icon = catIcon)

            // Sezon
            val seasonLabel = formatGallerySeason(item.season)
            if (seasonLabel != null) {
                DetailRow(label = stringResource(R.string.gallery_field_season), value = seasonLabel)
            }

            // Dil
            if (langInfo != null) {
                DetailRow(label = stringResource(R.string.gallery_field_language), value = "${langInfo.first} ${langInfo.second}")
            } else if (item.language == null &&
                (item.category == GalleryCategory.BACKDROP ||
                 item.category == GalleryCategory.LOGO ||
                 item.category == GalleryCategory.CLEARART)
            ) {
                DetailRow(label = stringResource(R.string.gallery_field_language), value = stringResource(R.string.gallery_textless))
            }

            // Boyut (API/kaynak sağladıysa)
            if (resStr != null) {
                DetailRow(label = stringResource(R.string.gallery_field_size), value = resStr)
            }

            // API'ye özgü detay satırları (örn. Fanart.tv: Ad, IMDb ID)
            item.details.forEach { (label, value) ->
                DetailRow(label = label, value = value)
            }

            // Açıklama
            if (!item.description.isNullOrBlank() &&
                item.description != "TMDB Poster" &&
                item.description != "TMDB Arka Plan" &&
                item.description != "TMDB Logo"
            ) {
                DetailRow(label = stringResource(R.string.aciklama), value = item.description)
            }

            Spacer(Modifier.height(4.dp))
            DetailRow(label = stringResource(R.string.gallery_field_page), value = "${page + 1} / $total")

            // ── Kaynak (source) filtresi ──────────────────────────────────────
            // Detay sayfasındaki "Resimler" levhasının kaynak bölümüyle aynı dil:
            // her satır logo + kaynak adı + görsel sayısı; dokununca galeri o kaynağa
            // iner (levha kapanır, sonuç hemen görünür). Tek kaynak varsa bölüm gizli.
            if (sources.size > 1) {
                Spacer(Modifier.height(10.dp))
                GallerySourceSectionTitle(stringResource(R.string.gallery_field_source))
                Spacer(Modifier.height(2.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GallerySourceRow(
                        label = stringResource(R.string.gallery_source_all),
                        source = null,
                        count = sources.sumOf { it.count },
                        selected = selectedSource == null,
                        onClick = { onSourceSelected(null) }
                    )
                    sources.forEach { group ->
                        GallerySourceRow(
                            label = group.source,
                            source = group.source,
                            count = group.count,
                            selected = group.source.equals(selectedSource, ignoreCase = true),
                            onClick = { onSourceSelected(group.source) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Detail row helper for the right panel
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DetailRow(
    label: String,
    value: String?,
    badge: (@Composable () -> Unit)? = null,
    icon: ImageVector? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = KitsugiColors.TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(3.dp))
        if (badge != null) {
            badge()
        } else if (value != null) {
            KitsugiEmojiLabel(
                label = value,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                iconSize = 12.dp,
                icon = icon
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(KitsugiColors.Border.copy(alpha = 0.25f))
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// KAYNAK (SOURCE) FİLTRESİ — yardımcılar
//   Galeri tek bir kaynağa indirilebilir. Kaynak listesi hem yatay modun sağ
//   panelindeki "Kaynaklar" sekmesinde hem de dikey modun detay levhasında
//   aynı görünümle (logo + ad + sayı) çizilir.
// ─────────────────────────────────────────────────────────────────────────────
/** Bir kaynaktan gelen görsel sayısı (küçük/büyük harf farkı gözetilmez). */
private data class GallerySourceGroup(val source: String, val count: Int)

@Composable
private fun GallerySourceSectionTitle(text: String) {
    Text(
        text = text,
        color = KitsugiColors.TextMuted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun GallerySourceRow(
    label: String,
    source: String?,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accent = LocalKitsugiAccent.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent.copy(alpha = 0.14f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) accent.copy(alpha = 0.45f) else KitsugiColors.Border.copy(alpha = 0.35f),
                shape = RoundedCornerShape(10.dp)
            )
            .tvClickable(shape = RoundedCornerShape(10.dp), onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (source == null) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = if (selected) accent else Color(0xFFFFB800),
                modifier = Modifier.size(20.dp)
            )
        } else {
            KitsugiPlatformLogo(platformId = source, size = 20.dp, cornerRadius = 5.dp)
        }
        Text(
            text = label,
            color = if (selected) accent else KitsugiColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = count.toString(),
            color = if (selected) accent else KitsugiColors.TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Galeride etkin kaynak filtresini gösteren küçük çip — dokununca filtre kalkar. */
@Composable
private fun GalleryActiveSourceChip(
    source: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accentColor.copy(alpha = 0.16f))
            .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
            .tvClickable(shape = RoundedCornerShape(999.dp), onClick = onClick)
            .padding(start = 6.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        KitsugiPlatformLogo(platformId = source, size = 16.dp, cornerRadius = 4.dp)
        Text(
            text = source,
            color = accentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = stringResource(R.string.gallery_clear_source_filter),
            tint = accentColor,
            modifier = Modifier.size(13.dp)
        )
    }
}

@JvmName("KitsugiImageGalleryDialogFromUrls")
@Composable
fun KitsugiImageGalleryDialog(
    imageUrls: List<String>,
    initialIndex: Int = 0,
    title: String,
    isAdult: Boolean = false,
    allowDownload: Boolean = true,
    allowDelete: Boolean = true,
    onItemsDeleted: (List<String>) -> Unit = {},
    onDismiss: () -> Unit
) {
    val items = remember(imageUrls) {
        imageUrls.map { GalleryItem(url = it, source = "Kitsugi", category = GalleryCategory.OTHER) }
    }
    KitsugiImageGalleryDialog(
        galleryItems = items,
        initialIndex = initialIndex,
        title = title,
        isAdult = isAdult,
        allowDownload = allowDownload,
        allowDelete = allowDelete,
        onItemsDeleted = onItemsDeleted,
        onDismiss = onDismiss
    )
}

// Custom gesture detector helper to allow page swipes when not zoomed in
suspend fun PointerInputScope.detectTransformGesturesCustom(
    onGesture: (pan: Offset, zoom: Float) -> Unit,
    shouldConsume: () -> Boolean
) {
    awaitEachGesture {
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop

        awaitFirstDown()
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    pan += panChange

                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    val panMotion = pan.getDistance()

                    if (zoomMotion > touchSlop || panMotion > touchSlop) {
                        pastTouchSlop = true
                    }
                }

                if (pastTouchSlop) {
                    onGesture(panChange, zoomChange)
                    if (shouldConsume()) {
                        event.changes.forEach {
                            if (it.positionChanged()) {
                                it.consume()
                            }
                        }
                    }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryImagePage(
    imageUrl: String,
    title: String,
    page: Int,
    pagerState: androidx.compose.foundation.pager.PagerState,
    isAdult: Boolean = false
) {
    var zoomScale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(pagerState.currentPage) {
        zoomScale = 1f
        offset = Offset.Zero
    }

    val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
    val isZoomed = zoomScale > 1f

    val dominoScale = if (isZoomed) 1f else (1f - (abs(pageOffset) * 0.15f)).coerceIn(0.75f, 1f)
    val dominoAlpha = if (isZoomed) 1f else (1f - (abs(pageOffset) * 0.45f)).coerceIn(0.35f, 1f)
    val dominoTranslationX = if (isZoomed) 0f else (pageOffset * 60.dp.value)
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (zoomScale > 1f) {
                                zoomScale = 1f
                                offset = Offset.Zero
                            } else {
                                zoomScale = 2.5f
                                offset = Offset.Zero
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGesturesCustom(
                        onGesture = { pan, zoom ->
                            val newZoom = (zoomScale * zoom).coerceIn(1f, 5f)
                            zoomScale = newZoom
                            if (newZoom > 1f) {
                                val maxX = (widthPx * (newZoom - 1f)) / 2f
                                val maxY = (heightPx * (newZoom - 1f)) / 2f
                                offset = Offset(
                                    (offset.x + pan.x).coerceIn(-maxX, maxX),
                                    (offset.y + pan.y).coerceIn(-maxY, maxY)
                                )
                            } else {
                                offset = Offset.Zero
                            }
                        },
                        shouldConsume = { zoomScale > 1f }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // +18 içeriklerde her koşulda bulanıklık (cihaz desteklemiyorsa bitmap blur)
            // BELLEK KORUMASI: 4K/6K TMDB/Fanart görsellerini tam çözünürlükte decode etmek
            // sayfa başına 50-96 MB tüketip DequeueBuffer timeout + RenderThread SIGSEGV
            // üretir; 1440x2160 INEXACT sınırı görsel kaliteyi korurken belleği %80 düşürür.
            val galleryContext = LocalContext.current
            val boundedGalleryModel = remember(imageUrl, galleryContext) {
                coil3.request.ImageRequest.Builder(galleryContext)
                    .data(imageUrl)
                    .size(1440, 2160)
                    .precision(coil3.size.Precision.INEXACT)
                    .build()
            }
            KitsugiNsfwImage(
                model = boundedGalleryModel,
                contentDescription = stringResource(R.string.gallery_image_accessibility, title, page),
                isAdult = isAdult,
                modifier = Modifier
                    .fillMaxSize(0.88f)
                    .graphicsLayer {
                        val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                        val zoomed = zoomScale > 1f
                        val dominoScale = if (zoomed) 1f else (1f - (abs(pageOffset) * 0.15f)).coerceIn(0.75f, 1f)
                        val dominoAlpha = if (zoomed) 1f else (1f - (abs(pageOffset) * 0.45f)).coerceIn(0.35f, 1f)
                        val dominoTranslationX = if (zoomed) 0f else (pageOffset * 60f)
                        scaleX = zoomScale * dominoScale
                        scaleY = zoomScale * dominoScale
                        translationX = offset.x + dominoTranslationX
                        translationY = offset.y
                        alpha = dominoAlpha
                    }
                    .clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun KitsugiGalleryHeader(
    title: String,
    currentPage: Int,
    totalPages: Int,
    accentColor: Color,
    downloadGlow: Float,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    isLandscape: Boolean,
    showDownloadButton: Boolean = true,
    isAlreadyDownloaded: Boolean = false,
    showDetailsButton: Boolean = false,
    onShowDetails: () -> Unit = {},
    allowDelete: Boolean = true,
    deleteActive: Boolean = false,
    onRequestDelete: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        // Glassmorphism header background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            KitsugiColors.Surface.copy(alpha = if (isLandscape) 0.82f else 0.90f),
                            Color.Transparent
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sol: Başlık + Sayfa göstergesi
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // İkon kutusu
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = accentColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = accentColor.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Image,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = title,
                            color = KitsugiColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (totalPages > 1) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${currentPage + 1}",
                                    color = accentColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "/ $totalPages",
                                    color = KitsugiColors.TextMuted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Sağ: Butonlar
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Detaylar Butonu — dikey modda ayrıntı levhasını açar
                    // (yatay modda bu bilgi zaten sağ panelde durur)
                    if (showDetailsButton) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = accentColor.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = accentColor.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .tvClickable(shape = RoundedCornerShape(12.dp), onClick = onShowDetails),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Info,
                                contentDescription = stringResource(R.string.gallery_details_accessibility),
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // İndirme Butonu — accent renkli, animasyonlu
                    // Yerel dosyalarda gizli; zaten indirilmişse tik işareti
                    if (showDownloadButton) {
                        val downloadBtnColor =
                            if (isAlreadyDownloaded) KitsugiColors.AccentGreen else accentColor
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = downloadBtnColor.copy(alpha = 0.18f * downloadGlow),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = downloadBtnColor.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .tvClickable(shape = RoundedCornerShape(12.dp), onClick = onDownload),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isAlreadyDownloaded) Icons.CheckCircle else Icons.Download,
                                contentDescription = stringResource(if (isAlreadyDownloaded) R.string.gallery_downloaded_status else R.string.gallery_download_action),
                                tint = downloadBtnColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Paylaşım Butonu — accent dokunuşlu glassmorphism
                    // Share / Send (Animated Fly)
                    KitsugiFlySendIconButton(
                        onClick = onShare,
                        size = 40.dp,
                        contentDescription = stringResource(R.string.paylas)
                    )

                    // Silme Butonu — indirilen kopyayı (veya yerel dosyayı) kaldırır.
                    if (allowDelete) {
                        KitsugiGalleryDeleteButton(
                            size = 40.dp,
                            iconSize = 20.dp,
                            corner = 12.dp,
                            active = deleteActive,
                            glow = downloadGlow,
                            onClick = onRequestDelete
                        )
                    }

                    // Kapatma Butonu — nötr, SurfaceSoft tabanlı
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = KitsugiColors.SurfaceStrong.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = KitsugiColors.Border,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .tvClickable(shape = RoundedCornerShape(12.dp), onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Close,
                            contentDescription = stringResource(R.string.kapat),
                            tint = KitsugiColors.TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun formatGallerySeason(season: String?): String? {
    val raw = season?.trim()?.takeIf { it.isNotBlank() && !it.equals("all", true) && it != "0" } ?: return null
    val number = raw.toIntOrNull()
    return if (number != null && number > 0) stringResource(R.string.gallery_season_number, number)
    else stringResource(R.string.gallery_season_raw, raw)
}

@Composable
private fun formatLanguage(code: String?): Pair<String, String>? {
    if (code.isNullOrBlank() || code == "00") return null
    val normalized = code.trim().lowercase().substringBefore('-').substringBefore('_')
    return when (normalized) {
        "tr" -> Pair("🇹🇷", stringResource(R.string.pd_lang_tr))
        "en", "eng", "ing" -> Pair("🇬🇧", stringResource(R.string.gallery_language_english))
        "ja" -> Pair("🇯🇵", stringResource(R.string.gallery_language_japanese))
        "de" -> Pair("🇩🇪", stringResource(R.string.pd_lang_de))
        "fr" -> Pair("🇫🇷", stringResource(R.string.pd_lang_fr))
        "es" -> Pair("🇪🇸", stringResource(R.string.pd_lang_es))
        "it" -> Pair("🇮🇹", stringResource(R.string.pd_lang_it))
        "ko" -> Pair("🇰🇷", stringResource(R.string.pd_lang_ko))
        "zh" -> Pair("🇨🇳", stringResource(R.string.pd_lang_zh))
        "ru" -> Pair("🇷🇺", stringResource(R.string.pd_lang_ru))
        "pt" -> Pair("🇵🇹", stringResource(R.string.pd_lang_pt))
        else -> Pair("🌐", code.uppercase())
    }
}

private fun formatResolution(width: Int?, height: Int?): String? {
    if (width == null || height == null || width <= 0 || height <= 0) return null
    val tag = when {
        width >= 3840 || height >= 2160 -> "4K"
        width >= 2560 || height >= 1440 -> "2K"
        width >= 1920 || height >= 1080 -> "FHD"
        width >= 1280 || height >= 720  -> "HD"
        else -> null
    }
    return if (tag != null) "$width×$height ($tag)" else "$width×$height"
}

/**
 * Galeri "Sil" butonu — İndir / Paylaş ile aynı görsel dilde (kare cam kutu),
 * kırmızı uyarı dokunuşuyla. Dikey (40.dp başlık) ve yatay (36.dp yan panel)
 * düzenlerde AYNI bileşen kullanılır; boyut/ikon/köşe dışarıdan gelir.
 *
 * @param active true → indirilmiş bir kopya var, buton dolu kırmızı görünür;
 *               false → silinecek kopya yok, buton soluk görünür (dokununca
 *               neden silinemediği kısa mesajla söylenir).
 */
@Composable
internal fun KitsugiGalleryDeleteButton(
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    corner: androidx.compose.ui.unit.Dp,
    active: Boolean,
    glow: Float = 1f,
    contentDescription: String = "",
    onClick: () -> Unit
) {
    val danger = Color(0xFFFF3B3B)
    val shape = RoundedCornerShape(corner)
    val container = if (active) danger.copy(alpha = 0.20f * glow) else KitsugiColors.SurfaceStrong.copy(alpha = 0.72f)
    val stroke = if (active) danger.copy(alpha = 0.55f) else KitsugiColors.Border.copy(alpha = 0.45f)
    val tint = if (active) danger else KitsugiColors.TextMuted

    Box(
        modifier = Modifier
            .size(size)
            .background(color = container, shape = shape)
            .border(1.dp, stroke, shape)
            .tvClickable(shape = shape, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Delete,
            contentDescription = contentDescription.ifBlank { stringResource(R.string.gallery_delete_downloaded_accessibility) },
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

