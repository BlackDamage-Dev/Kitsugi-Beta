package com.kitsugi.animelist.ui.screens.history

import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.local.WatchHistoryManager
import com.kitsugi.animelist.data.model.WatchHistoryEntry
import com.kitsugi.animelist.ui.components.KitsugiScrollToTopFab
import com.kitsugi.animelist.ui.screens.fullscreen.KitsugiFullscreenPlayerActivity
import com.kitsugi.animelist.ui.screens.stream.KitsugiStreamActivity
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.utils.tvClickable
import com.kitsugi.animelist.utils.KitsugiStrings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Okuma geçmişi satırı + hedef mangaya dönüş için gereken kaynak/link bilgisi. */
/**
 * Birleşik **Geçmiş** ekranı: izlenen bölümler ve okunan manga/novel bölümleri.
 *
 * ## Tasarım notları (2026-11-06)
 * - İzleme geçmişi daha önce olduğu gibi [WatchHistoryManager] (SharedPreferences + Gson)
 *   üzerinden okunur; dokunma davranışı (direkt oynatma / kaynak taramasına dönüş)
 *   **birebir korunmuştur**.
 * - Okunan içerikler Room'daki `manga_read_history` tablosundan gelir ve kaynağına
 *   (MangaciX, Şımarık Fansub …) göre rozetlenir; dokununca manga detay sayfası açılır,
 *   oradan "Devam Et" ile kaldığı sayfaya dönülür.
 * - Üstteki "Tümü / İzlenen / Okunan" çipleri iki türü ayırır; "Tümü" görünümünde
 *   bölüm başlıkları (İzlenen · Okunan) ile gruplanır.
 * - Aşağı kaydırınca Keşfet ile aynı "yukarı çık" düğmesi belirir.
 */
@Composable
fun WatchHistoryScreen(
    onBack: () -> Unit,
    /**
     * Okunan bir bölüme dokunulduğunda manga detay sayfasını açmak için kullanılır.
     * Verilmezse (ör. ayrı Activity'den açıldığında) kullanıcıya yalnızca bilgi gösterilir.
     */
    onOpenManga: ((source: String, mangaUrl: String, mangaTitle: String, coverUrl: String?) -> Unit)? = null
) {
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    val viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory(context))
    val uiState by viewModel.uiState.collectAsState()

    val selectedTab = uiState.selectedTab
    val watched = uiState.watched
    val readItems = uiState.read
    val readLoading = uiState.isLoadingRead

    var clearDialog by remember { mutableStateOf<HistoryTab?>(null) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Sekme değişiminde okuma geçmişi tazelenir (oynatıcıdan yeni kayıt gelmiş olabilir).
    LaunchedEffect(selectedTab) {
        if (selectedTab != HistoryTab.Watched) viewModel.refreshRead()
    }

    val showWatched = selectedTab != HistoryTab.Read
    val showRead = selectedTab != HistoryTab.Watched
    val watchedItems = if (showWatched) watched else emptyList()
    val readVisible = if (showRead) readItems else emptyList()
    val isEmpty = watchedItems.isEmpty() && readVisible.isEmpty() && !readLoading

    // Yukarı çık düğmesi: Keşfet sayfasındaki bileşen birebir kullanılır.
    val showFab = listState.firstVisibleItemIndex > 1 ||
        listState.firstVisibleItemScrollOffset > 400

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KitsugiColors.Background)
            .statusBarsPadding()
    ) {
        // ── Top Bar ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.ArrowBack,
                    contentDescription = stringResource(R.string.geri),
                    tint = KitsugiColors.TextPrimary
                )
            }
            Text(
                text = stringResource(R.string.history_title),
                color = KitsugiColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )
            AnimatedVisibility(
                visible = watched.isNotEmpty() || readItems.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                IconButton(onClick = { clearDialog = selectedTab }) {
                    Icon(
                        imageVector = Icons.DeleteSweep,
                        contentDescription = stringResource(R.string.tumunu_temizle),
                        tint = KitsugiColors.TextMuted
                    )
                }
            }
        }

        // ── Tür sekmeleri (Tümü / İzlenen / Okunan) ────────────────────────────
        HistoryTabsRow(
            selected = selectedTab,
            watchedCount = watched.size,
            readCount = readItems.size,
            accentColor = accentColor,
            onSelect = { viewModel.selectTab(it) }
        )

        HorizontalDivider(color = KitsugiColors.Border)

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                readLoading && readItems.isEmpty() && watched.isEmpty() -> {
                    // İlk açılışta kısa yükleme penceresi — boş mesajını erken göstermeyiz.
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader(size = 44.dp)
                    }
                }

                isEmpty -> HistoryEmptyState(
                    tab = selectedTab,
                    accentColor = accentColor
                )

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (selectedTab == HistoryTab.All && watchedItems.isNotEmpty()) {
                        item(key = "section_watched") {
                            HistorySectionHeader(
                                title = stringResource(R.string.history_section_watched),
                                count = watchedItems.size,
                                icon = Icons.PlayCircle,
                                accentColor = accentColor
                            )
                        }
                    }
                    items(
                        items = watchedItems,
                        key = { "watch_${it.animeId}_${it.episode}_${it.watchedAtMs}" }
                    ) { entry ->
                        WatchHistoryItem(
                            entry = entry,
                            accentColor = accentColor,
                            onClick = {
                                val savedUrl = entry.streamUrl
                                val isExpired = !savedUrl.isNullOrBlank() && (
                                    System.currentTimeMillis() - entry.watchedAtMs > 30 * 60 * 1000 ||
                                        entry.watchedAtMs < com.kitsugi.animelist.KitsugiApplication.APP_LAUNCH_TIME
                                    )
                                if (!savedUrl.isNullOrBlank() && !isExpired) {
                                    // ── Direkt oynatma ──────────────
                                    KitsugiFullscreenPlayerActivity.startWithStreamUrls(
                                        context = context,
                                        videoUrl = savedUrl,
                                        title = KitsugiStrings.get(
                                            R.string.title_bolum_episode,
                                            entry.animeTitle,
                                            entry.episode
                                        ),
                                        headers = entry.streamHeaders,
                                        subtitles = emptyList(),
                                        malId = entry.malId,
                                        aniListId = entry.aniListId,
                                        tmdbId = entry.tmdbId,
                                        season = entry.season,
                                        episode = entry.episode,
                                        animeTitle = entry.animeTitle,
                                        posterUrl = entry.posterUrl,
                                        isMovie = entry.isMovie,
                                        cs3Url = entry.cs3Url,
                                        cs3ApiName = entry.cs3ApiName,
                                        resumePositionMs = entry.positionMs
                                    )
                                } else {
                                    // ── Geri dönüş: tüm kaynakları tara ────────────────────────
                                    if (!entry.source.isNullOrBlank()) {
                                        context.getSharedPreferences(
                                            "KitsugiStreamPrefs",
                                            android.content.Context.MODE_PRIVATE
                                        )
                                            .edit()
                                            .putString("last_addon_name", entry.source)
                                            .apply()
                                    }
                                    KitsugiStreamActivity.start(
                                        context = context,
                                        malId = entry.malId,
                                        aniListId = entry.aniListId,
                                        tmdbId = entry.tmdbId,
                                        episode = entry.episode,
                                        season = entry.season,
                                        isMovie = entry.isMovie,
                                        title = entry.animeTitle,
                                        posterUrl = entry.posterUrl,
                                        isAutoplay = true,
                                        cs3Url = entry.cs3Url,
                                        cs3ApiName = entry.cs3ApiName
                                    )
                                }
                            },
                            onDelete = {
                                WatchHistoryManager.remove(entry.animeId, entry.episode)
                            }
                        )
                    }

                    if (selectedTab == HistoryTab.All && readVisible.isNotEmpty()) {
                        item(key = "section_read") {
                            HistorySectionHeader(
                                title = stringResource(R.string.history_section_read),
                                count = readVisible.size,
                                icon = Icons.MenuBook,
                                accentColor = KitsugiColors.AccentTeal
                            )
                        }
                    }
                    items(
                        items = readVisible,
                        key = { "read_${it.entity.id}_${it.entity.updatedAt}" }
                    ) { item ->
                        ReadHistoryItemCard(
                            item = item,
                            onOpen = {
                                val open = onOpenManga
                                if (open != null) {
                                    // Kaldığı yeri koru: aynı bölümün ilerlemesi detay
                                    // sayfasındaki "Devam Et" hedefini belirler.
                                    open(
                                        item.entity.sourceName,
                                        item.entity.mangaUrl,
                                        item.entity.mangaTitle,
                                        item.entity.thumbnailUrl
                                    )
                                } else {
                                    // Detay sayfasına köprü verilmemiş (ayrı Activity): kullanıcıyı
                                    // sessiz bırakma — hangi kaynakta olduğunu söyle.
                                    android.widget.Toast.makeText(
                                        context,
                                        "${item.entity.mangaTitle} · ${item.entity.chapterName}",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            onDelete = { viewModel.removeRead(item) }
                        )
                    }
                }
            }

            // ── "Yukarı Çık" FAB — Keşfet ile aynı bileşen ─────────────────────
            KitsugiScrollToTopFab(
                visible = showFab,
                isLandscape = false,
                bottomBarScrollState = null,
                fallbackBottomBarVisible = false,
                onClick = {
                    // Aynı anda iki animasyon çalışırsa ikincisi iptal edip listeyi
                    // yarım yolda bırakıyordu; tek bir iş ile en üste dönülür.
                    scope.launch { listState.animateScrollToItem(0) }
                },
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }

    // ── Temizleme onayı (sekme bazlı) ─────────────────────────────────────────
    clearDialog?.let { tab ->
        val title = when (tab) {
            HistoryTab.Read -> stringResource(R.string.history_clear_read_title)
            HistoryTab.Watched -> stringResource(R.string.history_clear_watched_title)
            HistoryTab.All -> stringResource(R.string.history_clear_all_title)
        }
        val message = when (tab) {
            HistoryTab.Read -> stringResource(R.string.history_clear_read_confirm)
            HistoryTab.Watched -> stringResource(R.string.history_clear_watched_confirm)
            HistoryTab.All -> stringResource(R.string.history_clear_all_confirm)
        }
        AlertDialog(
            onDismissRequest = { clearDialog = null },
            containerColor = KitsugiColors.Surface,
            titleContentColor = KitsugiColors.TextPrimary,
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = { Text(message, color = KitsugiColors.TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clear(tab)
                        clearDialog = null
                    }
                ) {
                    Text(
                        stringResource(R.string.temizle),
                        color = KitsugiColors.AccentRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { clearDialog = null }) {
                    Text(stringResource(R.string.iptal), color = accentColor)
                }
            }
        )
    }
}

// ─── Üst sekmeler ────────────────────────────────────────────────────────────

@Composable
private fun HistoryTabsRow(
    selected: HistoryTab,
    watchedCount: Int,
    readCount: Int,
    accentColor: Color,
    onSelect: (HistoryTab) -> Unit
) {
    val tabs = listOf(
        Triple(HistoryTab.All, stringResource(R.string.history_tab_all), watchedCount + readCount),
        Triple(HistoryTab.Watched, stringResource(R.string.history_tab_watched), watchedCount),
        Triple(HistoryTab.Read, stringResource(R.string.history_tab_read), readCount)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEach { (tab, label, count) ->
            val isSelected = selected == tab
            val shape = RoundedCornerShape(999.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(
                        if (isSelected) accentColor.copy(alpha = 0.18f)
                        else KitsugiColors.SurfaceSoft
                    )
                    .tvClickable(shape = shape) { onSelect(tab) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (count > 0) "$label ($count)" else label,
                    color = if (isSelected) accentColor else KitsugiColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun HistorySectionHeader(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
        Text(
            text = title,
            color = KitsugiColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "($count)",
            color = KitsugiColors.TextMuted,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun HistoryEmptyState(tab: HistoryTab, accentColor: Color) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Icon(
                imageVector = Icons.History,
                contentDescription = null,
                tint = KitsugiColors.TextMuted,
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = stringResource(R.string.history_empty_title),
                color = KitsugiColors.TextMuted,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = when (tab) {
                    HistoryTab.Read -> stringResource(R.string.history_empty_read)
                    HistoryTab.Watched -> stringResource(R.string.history_empty_watched)
                    HistoryTab.All -> stringResource(R.string.history_empty_body)
                },
                color = KitsugiColors.TextMuted.copy(alpha = 0.75f),
                fontSize = 13.sp
            )
        }
    }
}

// ─── İzleme geçmişi satırı (davranışı korunmuştur) ───────────────────────────

@Composable
private fun HistoryBadge(
    text: String,
    color: Color,
    bgColor: Color,
    bgAlpha: Float
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor.copy(alpha = bgAlpha))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HistoryThumbnail(
    url: String?,
    contentDescription: String?,
    fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector,
    width: Int = 58,
    height: Int = 86,
    accentColor: Color
) {
    Box(
        modifier = Modifier
            .size(width = width.dp, height = height.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(KitsugiColors.SurfaceStrong)
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, KitsugiColors.Background.copy(alpha = 0.5f)),
                            startY = 40f
                        )
                    )
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(fallbackIcon, null, tint = KitsugiColors.TextMuted, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun WatchHistoryItem(
    entry: WatchHistoryEntry,
    accentColor: Color,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(entry.watchedAtMs) {
        SimpleDateFormat("d MMM yyyy · HH:mm", Locale.getDefault())
            .format(Date(entry.watchedAtMs))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(KitsugiColors.SurfaceSoft, RoundedCornerShape(14.dp))
            .tvClickable(shape = RoundedCornerShape(14.dp), onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HistoryThumbnail(
            url = entry.posterUrl,
            contentDescription = entry.animeTitle,
            fallbackIcon = Icons.Movie,
            accentColor = accentColor
        )

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = entry.animeTitle,
                color = KitsugiColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!entry.streamName.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.inline_stream_1_s_038cf7a, entry.streamName),
                    color = accentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!entry.streamTitle.isNullOrBlank()) {
                Text(
                    text = entry.streamTitle,
                    color = KitsugiColors.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                HistoryBadge(
                    text = if (entry.isMovie) "Film" else "S${entry.season} · E${entry.episode}",
                    color = accentColor,
                    bgColor = accentColor,
                    bgAlpha = 0.15f
                )

                if (!entry.source.isNullOrBlank()) {
                    HistoryBadge(
                        text = entry.source,
                        color = KitsugiColors.AccentPurple,
                        bgColor = KitsugiColors.AccentPurple,
                        bgAlpha = 0.12f
                    )
                }

                val isExpired = remember(entry.streamUrl, entry.watchedAtMs) {
                    !entry.streamUrl.isNullOrBlank() &&
                        (System.currentTimeMillis() - entry.watchedAtMs > 2 * 60 * 60 * 1000)
                }
                if (!entry.streamUrl.isNullOrBlank() && !isExpired) {
                    HistoryBadge(
                        text = stringResource(R.string.aninda),
                        color = KitsugiColors.AccentGreen,
                        bgColor = KitsugiColors.AccentGreen,
                        bgAlpha = 0.13f
                    )
                }

                if (!entry.quality.isNullOrBlank()) {
                    val qualityColor = when {
                        entry.quality.contains("4K", ignoreCase = true) ||
                            entry.quality.contains("2160", ignoreCase = true) -> KitsugiColors.AccentRed
                        entry.quality.contains("1080", ignoreCase = true) -> KitsugiColors.AccentBlue
                        entry.quality.contains("720", ignoreCase = true) -> KitsugiColors.AccentGreen
                        else -> KitsugiColors.AccentOrange
                    }
                    HistoryBadge(
                        text = entry.quality,
                        color = qualityColor,
                        bgColor = qualityColor,
                        bgAlpha = 0.15f
                    )
                }
            }

            if (entry.durationMs > 0L && entry.positionMs > 0L) {
                val progress = entry.positionMs.toFloat() / entry.durationMs.toFloat()
                val remainingMin = ((entry.durationMs - entry.positionMs) / 60000L).coerceAtLeast(0L)
                val remainingText = if (remainingMin > 0) "$remainingMin dk kaldı" else "Az süre kaldı"

                com.kitsugi.animelist.ui.components.ContinueWatchingProgressLabel(
                    episodeLabel = "İlerleme",
                    progress = progress,
                    remainingText = remainingText,
                    accentColor = accentColor,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.AccessTime, null, tint = KitsugiColors.TextMuted, modifier = Modifier.size(11.dp))
                Text(text = dateStr, color = KitsugiColors.TextMuted, fontSize = 11.sp)
            }
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Delete,
                contentDescription = stringResource(R.string.action_delete),
                tint = KitsugiColors.TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ─── Okuma geçmişi satırı ────────────────────────────────────────────────────

/**
 * Okunan bölüm satırı — izleme geçmişiyle **aynı görsel dil**, içerik ise manga:
 * kapak, seri adı, bölüm adı, kaynak rozeti, kaldığı sayfa / ilerleme ve tarih.
 */
@Composable
private fun ReadHistoryItemCard(
    item: HistoryViewModel.ReadRow,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val entity = item.entity
    val accent = KitsugiColors.AccentTeal
    val dateStr = remember(entity.updatedAt) {
        SimpleDateFormat("d MMM yyyy · HH:mm", Locale.getDefault())
            .format(Date(entity.updatedAt))
    }
    val progress = if (entity.totalPages > 0) {
        ((entity.lastPageIndex + 1).toFloat() / entity.totalPages.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(KitsugiColors.SurfaceSoft, RoundedCornerShape(14.dp))
            .tvClickable(shape = RoundedCornerShape(14.dp), onClick = onOpen)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HistoryThumbnail(
            url = entity.thumbnailUrl,
            contentDescription = entity.mangaTitle,
            fallbackIcon = Icons.MenuBook,
            accentColor = accent
        )

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = entity.mangaTitle,
                color = KitsugiColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.history_read_chapter_label, entity.chapterName),
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (entity.sourceName.isNotBlank()) {
                    HistoryBadge(
                        text = entity.sourceName,
                        color = KitsugiColors.AccentPurple,
                        bgColor = KitsugiColors.AccentPurple,
                        bgAlpha = 0.12f
                    )
                }
                HistoryBadge(
                    text = if (entity.isCompleted) {
                        stringResource(R.string.history_read_completed)
                    } else {
                        stringResource(R.string.history_read_in_progress)
                    },
                    color = if (entity.isCompleted) KitsugiColors.AccentGreen else KitsugiColors.AccentOrange,
                    bgColor = if (entity.isCompleted) KitsugiColors.AccentGreen else KitsugiColors.AccentOrange,
                    bgAlpha = 0.14f
                )
                if (entity.totalPages > 0) {
                    HistoryBadge(
                        text = stringResource(
                            R.string.history_read_progress,
                            entity.lastPageIndex + 1,
                            entity.totalPages
                        ),
                        color = KitsugiColors.TextSecondary,
                        bgColor = KitsugiColors.SurfaceStrong,
                        bgAlpha = 0.6f
                    )
                }
            }

            // İlerleme çubuğu — izleme geçmişindeki "kaldığı yer" mantığının manga karşılığı.
            if (entity.totalPages > 0 && !entity.isCompleted) {
                com.kitsugi.animelist.ui.components.ContinueWatchingProgressLabel(
                    episodeLabel = stringResource(R.string.history_read_in_progress),
                    progress = progress,
                    remainingText = stringResource(
                        R.string.manga_page_of_total,
                        entity.lastPageIndex + 1,
                        entity.totalPages
                    ),
                    accentColor = accent,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.AccessTime, null, tint = KitsugiColors.TextMuted, modifier = Modifier.size(11.dp))
                Text(text = dateStr, color = KitsugiColors.TextMuted, fontSize = 11.sp)
            }
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Delete,
                contentDescription = stringResource(R.string.history_remove_read_entry),
                tint = KitsugiColors.TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
