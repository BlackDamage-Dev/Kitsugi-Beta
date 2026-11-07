package com.kitsugi.animelist.ui.screens.manga.components

import androidx.compose.foundation.background
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.data.music.AmbientSound
import com.kitsugi.animelist.data.music.AtmospherePreset
import com.kitsugi.animelist.data.music.AtmospherePresets
import com.kitsugi.animelist.data.music.ChannelPlayState
import com.kitsugi.animelist.data.music.ChannelState
import com.kitsugi.animelist.data.music.ProceduralSoundCatalog
import com.kitsugi.animelist.data.music.RecommendationResult
import com.kitsugi.animelist.data.music.SoundFamily
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent

/**
 * 🎵 Okuyucu Müzik Paneli — Birleşik Sürüm
 *
 * **Tek kural:** Kaynağı ne olursa olsun (prosedürel, Jamendo, Openverse,
 * Archive, Freesound, Audius) her ses aynı kanal modeline girer ve aynı
 * butonlarla yönetilir:
 *
 *  - Ses seviyesi kaydırıcısı
 *  - Mute (sessize al / aç)
 *  - Kaldır
 *  - Ana ses seviyesi
 *  - Tümünü durdur
 *
 * Böylece "her kaynak her butona mantıklı tepki verir" ilkesi mimari
 * düzeyde garanti edilir; UI'da kaynak türüne göre dallanma YOKTUR.
 *
 * Dört sekme:
 *  - **Senin İçin** → manga türüne göre öneriler
 *  - **Ambiyans**   → doğa/ortam sesleri
 *  - **Müzik**      → enstrümantal sentez
 *  - **Karışım**    → aktif kanal miksajı
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MangaAmbientPanel(
    recommendations: RecommendationResult,
    /**
     * Yedi kaynaklı tür zenginleştirmesinin kısa durum notu
     * ("7 kaynak tarandı · 18 tür/etiket" / "Kaynaklar taranıyor…").
     * `null` ise yalnızca yerel türler kullanılıyor demektir.
     */
    matchNote: String? = null,
    isPlaying: Boolean,
    activeTitle: String?,
    activePresetId: String?,
    masterVolume: Float,
    channels: List<ChannelState>,
    maxChannels: Int,
    onMasterVolumeChanged: (Float) -> Unit,
    onPlayPreset: (AtmospherePreset) -> Unit,
    onPlaySound: (AmbientSound) -> Unit,
    onChannelVolumeChanged: (Int, Float) -> Unit,
    onChannelMuteToggle: (Int) -> Unit,
    onChannelRemove: (Int) -> Unit,
    /** Hatalı kanalı aynı yuva ve sesle yeniden dener. */
    onRetryChannel: (Int) -> Unit = {},
    onTogglePlay: () -> Unit,
    onStopAll: () -> Unit,
    onDismiss: () -> Unit,
    // ── Çevrimiçi katalog ──────────────────────────────────────────────
    onlineResults: List<AmbientSound> = emptyList(),
    isSearching: Boolean = false,
    searchError: String? = null,
    searchQuery: String = "",
    sourceStates: List<com.kitsugi.animelist.data.music.MusicSourceState> = emptyList(),
    onSearchQueryChanged: (String) -> Unit = {},
    onSearch: () -> Unit = {},
    onPlayOnline: (AmbientSound) -> Unit = {},
    // ── Zamanlayıcı ────────────────────────────────────────────────────
    sleepTimer: com.kitsugi.animelist.data.music.SleepTimerState =
        com.kitsugi.animelist.data.music.SleepTimerState(),
    onStartTimer: (Int) -> Unit = {},
    onCancelTimer: () -> Unit = {},
    onPauseTimer: () -> Unit = {},
    onResumeTimer: () -> Unit = {},
    onExtendTimer: (Int) -> Unit = {},
    // ── Profiller / favoriler ──────────────────────────────────────────
    profiles: List<com.kitsugi.animelist.data.music.MixProfile> = emptyList(),
    favorites: Set<String> = emptySet(),
    recentIds: List<String> = emptyList(),
    onSaveProfile: (String) -> Unit = {},
    onApplyProfile: (com.kitsugi.animelist.data.music.MixProfile) -> Unit = {},
    onDeleteProfile: (String) -> Unit = {},
    onToggleFavorite: (String) -> Unit = {},
    onExportProfile: (com.kitsugi.animelist.data.music.MixProfile) -> Unit = {},
    onImportProfile: () -> Unit = {},
    // ── Otomatik çalma anahtarı ────────────────────────────────────────
    /** Ortam sesi sistemi açık mı (ana anahtar). */
    musicEnabled: Boolean = true,
    onMusicEnabledChanged: (Boolean) -> Unit = {},
    /** Otomatik başlatma açık mı. */
    autoPlay: Boolean = false,
    onAutoPlayChanged: (Boolean) -> Unit = {},
    autoPlayDelaySeconds: Int = 3,
    /** Otomatik başlatma yalnızca kulaklık takılıyken çalışsın. */
    autoPlayHeadphonesOnly: Boolean = false,
    onAutoPlayHeadphonesOnlyChanged: (Boolean) -> Unit = {},
    /** Cihaz sessiz/titreşim modundayken otomatik başlatma yapılmasın. */
    autoPlayMuteOnSilent: Boolean = false,
    onAutoPlayMuteOnSilentChanged: (Boolean) -> Unit = {},
    /** Otomatik başlatmada çevrimiçi kaynaklar da kullanılsın. */
    autoPlayIncludeOnline: Boolean = false,
    onAutoPlayIncludeOnlineChanged: (Boolean) -> Unit = {},
    /** Kaynak aç/kapa — kullanıcı hangi kaynakların aranacağını seçer. */
    onToggleSource: (com.kitsugi.animelist.data.music.MusicSourceId) -> Unit = {},
    // ── Çevrimdışı / indirme ───────────────────────────────────────────
    /** İndirilmiş ses kimlikleri. */
    downloadedIds: Set<String> = emptySet(),
    /** Ses kimliği → indirme ilerlemesi (0..1); indirilmiyorsa yok. */
    downloadProgress: Map<String, Float> = emptyMap(),
    onDownloadSound: (AmbientSound) -> Unit = {},
    /** İndirilenlerin toplam boyutu (bayt). */
    downloadedBytes: Long = 0L,
    onClearDownloads: () -> Unit = {}
) {
    val accent = LocalKitsugiAccent.current

    // ── Sayfa gezinme: sağa-sola kaydırma (kullanıcı isteği) ────────────
    // Sekme bandı + içerik tek HorizontalPager'a bağlı: banttan dokunmak
    // sayfayı kaydırır, sayfayı kaydırmak bandı senkronlar. "Ayarlar"
    // (açma/kapama anahtarları) artık ayrı bir sayfa; çalma çubuğu
    // (başlık + kısayol şeridi) her zaman üstte sabit durur.
    val tabs = remember {
        listOf("Senin İçin", "Ambiyans", "Müzik", "Çevrimiçi", "Karışım", "Profil", "Süre", "Ayarlar")
    }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })
    val selectedTab = pagerState.currentPage
    val panelScope = rememberCoroutineScope()
    val jumpToTab: (Int) -> Unit = { index ->
        panelScope.launch { pagerState.animateScrollToPage(index) }
    }

    // Uzun basılan ses → detay sayfası (Chiarella'nın kart detayı karşılığı)
    var detailSound by remember { mutableStateOf<AmbientSound?>(null) }
    val openDetail: (AmbientSound) -> Unit = { detailSound = it }

    // Android geri tuşu: detay açıksa onu kapat, paneli kapatma
    androidx.activity.compose.BackHandler(enabled = detailSound != null) {
        detailSound = null
    }

    // Uzun basılan ses varsa → detay sayfası (tek katman, geri butonu ile)
    detailSound?.let { sound ->
        SoundDetailPage(
            sound = sound,
            accent = accent,
            isPlaying = channels.any { it.sound.id == sound.id },
            isFavorite = sound.id in favorites,
            onBack = { detailSound = null },
            onPlay = { onPlaySound(sound) },
            onPlaySound = onPlaySound,
            onToggleFavorite = { onToggleFavorite(sound.id) }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // ── Başlık ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ortam Sesi",
                    color = KitsugiColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                // Başlık asla "null" basmaz: aktif başlık yoksa (ör. ön plan
                // servisinden veya preset geçişinden sonra) kanalların kendi
                // etiketi kullanılır.
                val headerTitle = activeTitle?.takeIf { it.isNotBlank() }
                    ?: channels.firstOrNull()?.displayLabel
                Text(
                    text = when {
                        channels.isEmpty() -> "Okuduğun içeriğe uygun ses"
                        channels.size == 1 -> headerTitle ?: "Ortam sesi çalıyor"
                        else -> "${channels.size} ses çalıyor · " +
                            (headerTitle ?: "karışım")
                    },
                    color = KitsugiColors.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (channels.isNotEmpty()) {
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Duraklat" else "Çal",
                        tint = accent
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Kapat",
                    tint = KitsugiColors.TextSecondary
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // ══════════════════════════════════════════════════════════════
        //  KISAYOL ŞERİDİ — sayfayı kaplamadan tek dokunuş
        // ══════════════════════════════════════════════════════════════
        QuickActionBar(
            accent = accent,
            isPlaying = isPlaying,
            hasChannels = channels.isNotEmpty(),
            isMuted = channels.isNotEmpty() && channels.all { it.muted },
            timerRunning = sleepTimer.running,
            timerLabel = sleepTimer.display,
            favoriteCount = favorites.size,
            profileCount = profiles.size,
            onTogglePlay = onTogglePlay,
            onMuteAll = {
                // En az bir kanal açıksa hepsini sustur; hepsi zaten
                // kapalıysa hepsini aç. Böylece düğme gerçek bir toggle.
                val shouldMute = channels.any { !it.muted }
                channels.forEach { channel ->
                    if (channel.muted != shouldMute) {
                        onChannelMuteToggle(channel.index)
                    }
                }
            },
            onQuickTimer = { minutes ->
                if (sleepTimer.running) onCancelTimer() else onStartTimer(minutes)
            },
            onStopAll = onStopAll,
            onJumpTo = { tab -> jumpToTab(tab) }
        )

        Spacer(Modifier.height(8.dp))

        // ── Sekmeler (üstte sabit; sayfayla senkron) ─────────────────────
        val tabListState = rememberLazyListState()
        LaunchedEffect(selectedTab) {
            tabListState.animateScrollToItem(selectedTab)
        }
        LazyRow(
            state = tabListState,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tabs.size) { index ->
                FilterChip(
                    selected = selectedTab == index,
                    onClick = { jumpToTab(index) },
                    label = {
                        Text(
                            text = when {
                                index == 4 && channels.isNotEmpty() -> "${tabs[index]} (${channels.size})"
                                index == 6 && sleepTimer.running -> "⏱ ${sleepTimer.display}"
                                else -> tabs[index]
                            },
                            fontSize = 12.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent.copy(alpha = 0.22f),
                        selectedLabelColor = accent,
                        containerColor = KitsugiColors.SurfaceSoft,
                        labelColor = KitsugiColors.TextSecondary
                    )
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // ── İçerik: kaydırılabilir sayfalar ─────────────────────────────
        // Çalma çubuğu (başlık + kısayol şeridi) ve sekme bandı bu alanın
        // DIŞINDA, her zaman üstte sabit. Sayfalar sağa-sola kaydırılır.
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            when (page) {
                0 -> ForYouTab(
                    modifier = Modifier.fillMaxSize(),
                    recommendations = recommendations,
                    matchNote = matchNote,
                    channels = channels,
                    activePresetId = activePresetId,
                    accent = accent,
                    favorites = favorites,
                    recentIds = recentIds,
                    onPlayPreset = onPlayPreset,
                    onPlaySound = onPlaySound,
                    onToggleFavorite = onToggleFavorite,
                    onLongClickSound = openDetail
                )
                1 -> SoundListTab(
                    modifier = Modifier.fillMaxSize(),
                    familyFilter = { it != SoundFamily.INSTRUMENTAL },
                    channels = channels,
                    favorites = favorites,
                    onPlaySound = onPlaySound,
                    onToggleFavorite = onToggleFavorite,
                    onLongClickSound = openDetail
                )
                2 -> SoundListTab(
                    modifier = Modifier.fillMaxSize(),
                    familyFilter = { it == SoundFamily.INSTRUMENTAL },
                    channels = channels,
                    favorites = favorites,
                    onPlaySound = onPlaySound,
                    onToggleFavorite = onToggleFavorite,
                    onLongClickSound = openDetail
                )
                3 -> OnlineTab(
                    modifier = Modifier.fillMaxSize(),
                    results = onlineResults,
                    isSearching = isSearching,
                    error = searchError,
                    query = searchQuery,
                    sourceStates = sourceStates,
                    playingIds = channels.map { it.sound.id }.toSet(),
                    accent = accent,
                    onQueryChanged = onSearchQueryChanged,
                    onSearch = onSearch,
                    onPlay = onPlayOnline,
                    onToggleSource = onToggleSource,
                    downloadedIds = downloadedIds,
                    downloadProgress = downloadProgress,
                    onDownload = onDownloadSound
                )
                4 -> MixerTab(
                    modifier = Modifier.fillMaxSize(),
                    channels = channels,
                    maxChannels = maxChannels,
                    masterVolume = masterVolume,
                    onMasterVolumeChanged = onMasterVolumeChanged,
                    onChannelVolumeChanged = onChannelVolumeChanged,
                    onChannelMuteToggle = onChannelMuteToggle,
                    onChannelRemove = onChannelRemove,
                    onRetryChannel = onRetryChannel,
                    onStopAll = onStopAll,
                    accent = accent,
                    sleepTimer = sleepTimer,
                    onStartTimer = onStartTimer,
                    onCancelTimer = onCancelTimer,
                    downloadedCount = downloadedIds.size,
                    downloadedBytes = downloadedBytes,
                    onClearDownloads = onClearDownloads
                )
                5 -> ProfileTab(
                    modifier = Modifier.fillMaxSize(),
                    profiles = profiles,
                    channels = channels,
                    accent = accent,
                    onSave = onSaveProfile,
                    onApply = onApplyProfile,
                    onDelete = onDeleteProfile,
                    onExport = onExportProfile,
                    onImport = onImportProfile
                )
                6 -> TimerTab(
                    modifier = Modifier.fillMaxSize(),
                    timer = sleepTimer,
                    accent = accent,
                    onStart = onStartTimer,
                    onCancel = onCancelTimer,
                    onPause = onPauseTimer,
                    onResume = onResumeTimer,
                    onExtend = onExtendTimer
                )
                else -> {
                    // ── SEKME 8 — AYARLAR (açma/kapama anahtarları) ──────
                    // Anahtarlar eskiden yalnızca "Senin İçin" sekmesinin
                    // üstünde duruyordu; artık sekme bandındaki kendi
                    // sayfasında (kullanıcı isteği).
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AutoPlayCard(
                            accent = accent,
                            musicEnabled = musicEnabled,
                            autoPlay = autoPlay,
                            delaySeconds = autoPlayDelaySeconds,
                            onMusicEnabledChanged = onMusicEnabledChanged,
                            onAutoPlayChanged = onAutoPlayChanged,
                            headphonesOnly = autoPlayHeadphonesOnly,
                            onHeadphonesOnlyChanged = onAutoPlayHeadphonesOnlyChanged,
                            muteOnSilent = autoPlayMuteOnSilent,
                            onMuteOnSilentChanged = onAutoPlayMuteOnSilentChanged,
                            includeOnline = autoPlayIncludeOnline,
                            onIncludeOnlineChanged = onAutoPlayIncludeOnlineChanged
                        )
                        Text(
                            text = "Çevrimiçi parça bulunamazsa veya açılamazsa cihaz içi ses " +
                                "kesintisiz devam eder; okuyucudan çıkınca müzik her durumda durur.",
                            color = KitsugiColors.TextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(KitsugiColors.SurfaceSoft.copy(alpha = 0.7f))
                                .padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
//  SEKME 1 — SENİN İÇİN
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun ForYouTab(
    modifier: Modifier = Modifier,
    recommendations: RecommendationResult,
    matchNote: String? = null,
    channels: List<ChannelState>,
    activePresetId: String?,
    accent: Color,
    favorites: Set<String> = emptySet(),
    recentIds: List<String> = emptyList(),
    onPlayPreset: (AtmospherePreset) -> Unit,
    onPlaySound: (AmbientSound) -> Unit,
    onToggleFavorite: (String) -> Unit = {},
    onLongClickSound: (AmbientSound) -> Unit = {}
) {
    val playingIds = remember(channels) { channels.map { it.sound.id }.toSet() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        // ── Eşleşme gerekçesi + güven ───────────────────────────────────
        // Dürüstlük: güven düşükse "tahmin" diyoruz. Sessizce rastgele
        // öneri yapmak kullanıcının sisteme güvenini bitirir.
        val conf = recommendations.confidence
        val confLabel = when (conf) {
            com.kitsugi.animelist.data.music.ContentMoodResolver.Confidence.HIGH -> "türden"
            com.kitsugi.animelist.data.music.ContentMoodResolver.Confidence.MEDIUM -> "etiketten"
            com.kitsugi.animelist.data.music.ContentMoodResolver.Confidence.LOW -> "açıklamadan"
            com.kitsugi.animelist.data.music.ContentMoodResolver.Confidence.NONE -> "tahmin"
        }
        val reasonText = recommendations.reason.ifBlank {
            if (conf == com.kitsugi.animelist.data.music.ContentMoodResolver.Confidence.NONE)
                "İçerik türü tanınmadı — dengeli başlangıç"
            else "İçeriğe göre"
        }
        val reasonColor = if (conf == com.kitsugi.animelist.data.music.ContentMoodResolver.Confidence.NONE)
            Color(0xFFFBBF24) else accent

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(reasonColor.copy(alpha = 0.10f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (conf == com.kitsugi.animelist.data.music.ContentMoodResolver.Confidence.NONE)
                    Icons.Rounded.HelpOutline else Icons.Rounded.GraphicEq,
                contentDescription = null,
                tint = reasonColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reasonText,
                    color = KitsugiColors.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append("eşleşme: ")
                        append(confLabel)
                        if (!matchNote.isNullOrBlank()) {
                            append(" · ")
                            append(matchNote)
                        }
                    },
                    color = KitsugiColors.TextMuted,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (recommendations.presets.isNotEmpty()) {
            Text(
                text = "ATMOSFER PRESETLERİ",
                color = KitsugiColors.TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(recommendations.presets, key = { it.sound.id }) { rec ->
                    val preset = AtmospherePresets.byId(rec.sound.id)
                    if (preset != null) {
                        AtmosphereCard(
                            preset = preset,
                            score = rec.score,
                            isActive = activePresetId == preset.id,
                            accent = accent,
                            onClick = { onPlayPreset(preset) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // ── Favoriler ───────────────────────────────────────────────────
        val favSounds = remember(favorites) {
            favorites.mapNotNull { id ->
                com.kitsugi.animelist.data.music.ProceduralSoundCatalog.byId(id)
            }
        }
        if (favSounds.isNotEmpty()) {
            Text(
                text = "FAVORİLER · ${favSounds.size}",
                color = accent.copy(alpha = 0.8f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            favSounds.forEach { sound ->
                SoundRow(
                    sound = sound,
                    reason = "favorin",
                    isActive = sound.id in playingIds,
                    accent = accent,
                    isFavorite = true,
                    onClick = { onPlaySound(sound) },
                    onToggleFavorite = { onToggleFavorite(sound.id) },
                    onLongClick = { onLongClickSound(sound) }
                )
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(14.dp))
        }

        // ── Son çalınanlar ──────────────────────────────────────────────
        val recentSounds = remember(recentIds) {
            recentIds.take(4).mapNotNull { id ->
                com.kitsugi.animelist.data.music.ProceduralSoundCatalog.byId(id)
            }
        }
        if (recentSounds.isNotEmpty()) {
            Text(
                text = "SON ÇALINANLAR",
                color = KitsugiColors.TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                recentSounds.take(3).forEach { sound ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (sound.id in playingIds) accent.copy(alpha = 0.18f)
                                else KitsugiColors.SurfaceSoft
                            )
                            .clickable { onPlaySound(sound) }
                            .padding(horizontal = 10.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = sound.title,
                            color = if (sound.id in playingIds) accent else KitsugiColors.TextSecondary,
                            fontSize = 10.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        if (recommendations.sounds.isNotEmpty()) {
            Text(
                text = "ÖNERİLEN SESLER",
                color = KitsugiColors.TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            recommendations.sounds.forEach { rec ->
                SoundRow(
                    sound = rec.sound,
                    reason = rec.reason,
                    isActive = rec.sound.id in playingIds,
                    accent = accent,
                    isFavorite = rec.sound.id in favorites,
                    onClick = { onPlaySound(rec.sound) },
                    onToggleFavorite = { onToggleFavorite(rec.sound.id) },
                    onLongClick = { onLongClickSound(rec.sound) }
                )
                Spacer(Modifier.height(6.dp))
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Bir sese dokun → havuza eklenir. Karışım sekmesinden seviyeleri ayarlarsın.",
            color = KitsugiColors.TextMuted,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
//  SEKME 2/3 — SES LİSTESİ
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun SoundListTab(
    modifier: Modifier = Modifier,
    familyFilter: (SoundFamily) -> Boolean,
    channels: List<ChannelState>,
    favorites: Set<String> = emptySet(),
    onPlaySound: (AmbientSound) -> Unit,
    onToggleFavorite: (String) -> Unit = {},
    onLongClickSound: (AmbientSound) -> Unit = {}
) {
    val accent = LocalKitsugiAccent.current
    val allSounds = remember(familyFilter) {
        ProceduralSoundCatalog.sounds.filter { familyFilter(it.family) }
    }
    val playingIds = remember(channels) { channels.map { it.sound.id }.toSet() }

    // ── Kategori filtresi ───────────────────────────────────────────────
    // Aileler yalnızca bu listede geçenlerden türetilir; boş kategori
    // göstermeyiz ("Müzik" sekmesinde "Ateş" çıkmasın).
    val families = remember(allSounds) {
        allSounds.map { it.family }.distinct().sortedBy { it.name }
    }
    var selectedFamily by remember(families) { mutableStateOf<SoundFamily?>(null) }
    var onlyFavorites by remember { mutableStateOf(false) }

    val sounds = remember(allSounds, selectedFamily, onlyFavorites, favorites) {
        allSounds
            .filter { selectedFamily == null || it.family == selectedFamily }
            .filter { !onlyFavorites || it.id in favorites }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        // ── Filtre çipleri ──────────────────────────────────────────────
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            item {
                FamilyChip(
                    label = "Tümü",
                    selected = selectedFamily == null && !onlyFavorites,
                    accent = accent,
                    onClick = {
                        selectedFamily = null
                        onlyFavorites = false
                    }
                )
            }
            if (favorites.isNotEmpty()) {
                item {
                    FamilyChip(
                        label = "❤ ${favorites.size}",
                        selected = onlyFavorites,
                        accent = Color(0xFFF472B6),
                        onClick = { onlyFavorites = !onlyFavorites }
                    )
                }
            }
            items(families.size) { index ->
                val family = families[index]
                FamilyChip(
                    label = familyLabel(family),
                    selected = selectedFamily == family,
                    accent = accent,
                    onClick = {
                        selectedFamily = if (selectedFamily == family) null else family
                    }
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        if (sounds.isEmpty()) {
            Text(
                text = if (onlyFavorites) "Henüz favori yok — bir sese uzun bas"
                else "Bu kategoride ses yok",
                color = KitsugiColors.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                textAlign = TextAlign.Center
            )
        }

        sounds.forEach { sound ->
            SoundRow(
                sound = sound,
                reason = sound.subtitle,
                isActive = sound.id in playingIds,
                accent = accent,
                isFavorite = sound.id in favorites,
                onClick = { onPlaySound(sound) },
                onToggleFavorite = { onToggleFavorite(sound.id) },
                onLongClick = { onLongClickSound(sound) }
            )
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Tümü cihazında üretilir — internet, hesap veya kota gerekmez.",
            color = KitsugiColors.TextMuted,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

/** Ses ailesinin Türkçe adı. */
private fun familyLabel(family: SoundFamily): String = when (family) {
    SoundFamily.NOISE -> "Gürültü"
    SoundFamily.WEATHER -> "Hava"
    SoundFamily.FIRE -> "Ateş"
    SoundFamily.WATER -> "Su"
    SoundFamily.WIND -> "Rüzgâr"
    SoundFamily.FOREST -> "Orman"
    SoundFamily.URBAN -> "Şehir"
    SoundFamily.INSTRUMENTAL -> "Enstrümantal"
    SoundFamily.VOCAL -> "Vokal"
    SoundFamily.OTHER -> "Diğer"
}

/** Filtre çipi — sekme çiplerinden daha küçük. */
@Composable
private fun FamilyChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) accent.copy(alpha = 0.22f) else KitsugiColors.SurfaceSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (selected) accent else KitsugiColors.TextSecondary,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
//  SEKME 4 — KARIŞIM (BİRLEŞİK KANAL MİKSAJI)
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun MixerTab(
    modifier: Modifier = Modifier,
    channels: List<ChannelState>,
    maxChannels: Int,
    masterVolume: Float,
    onMasterVolumeChanged: (Float) -> Unit,
    onChannelVolumeChanged: (Int, Float) -> Unit,
    onChannelMuteToggle: (Int) -> Unit,
    onChannelRemove: (Int) -> Unit,
    onRetryChannel: (Int) -> Unit = {},
    onStopAll: () -> Unit,
    accent: Color,
    sleepTimer: com.kitsugi.animelist.data.music.SleepTimerState =
        com.kitsugi.animelist.data.music.SleepTimerState(),
    onStartTimer: (Int) -> Unit = {},
    onCancelTimer: () -> Unit = {},
    downloadedCount: Int = 0,
    downloadedBytes: Long = 0L,
    onClearDownloads: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 10.dp)
    ) {
        // ── Ana ses ─────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Ana Ses",
                color = KitsugiColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(masterVolume * 100).toInt()}%",
                color = accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = masterVolume,
            onValueChange = onMasterVolumeChanged,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = KitsugiColors.Border
            )
        )

        // ── Cihaz sesi göstergesi ───────────────────────────────────────
        // Uygulama içi ses seviyesi cihazın sesini aşamaz. Kullanıcı
        // "sonuna kadar açtım ama ses az" derse sorun buradadır.
        val deviceVolume = rememberDeviceVolume()
        if (deviceVolume != null && deviceVolume < 0.35f) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFFFBBF24).copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔉", fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Cihaz sesi %${(deviceVolume * 100).toInt()}",
                    color = Color(0xFFFBBF24),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Yükselt",
                    color = Color(0xFFFBBF24),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFFBBF24).copy(alpha = 0.18f))
                        .clickable { raiseDeviceVolume() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        // ── Hızlı zamanlayıcı ───────────────────────────────────────────
        if (sleepTimer.running) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⏱", fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = sleepTimer.display,
                    color = accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "İptal",
                    color = KitsugiColors.AccentRed,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onCancelTimer)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        } else if (channels.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Uyku:",
                    color = KitsugiColors.TextMuted,
                    fontSize = 10.sp
                )
                listOf(15, 30, 60).forEach { minutes ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(KitsugiColors.SurfaceSoft)
                            .clickable { onStartTimer(minutes) }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "$minutes dk",
                            color = KitsugiColors.TextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Kanal sayacı ────────────────────────────────────────────────
        Text(
            text = "AÇIK KANALLAR (${channels.size}/$maxChannels)",
            color = KitsugiColors.TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))

        if (channels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(KitsugiColors.SurfaceSoft)
                    .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Henüz ses eklenmedi\nDiğer sekmelerden bir sese dokun",
                    color = KitsugiColors.TextMuted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            channels.sortedBy { it.index }.forEach { channel ->
                ChannelRow(
                    channel = channel,
                    accent = accent,
                    onVolumeChanged = { onChannelVolumeChanged(channel.index, it) },
                    onMuteToggle = { onChannelMuteToggle(channel.index) },
                    onRemove = { onChannelRemove(channel.index) },
                    onRetry = { onRetryChannel(channel.index) }
                )
                Spacer(Modifier.height(6.dp))
            }
        }

        Spacer(Modifier.height(14.dp))

        if (channels.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(KitsugiColors.AccentRed.copy(alpha = 0.12f))
                    .clickable(onClick = onStopAll)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tümünü Durdur",
                    color = KitsugiColors.AccentRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // ── Çevrimdışı depolama ─────────────────────────────────────────
        if (downloadedCount > 0) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(KitsugiColors.SurfaceSoft.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.CloudDone,
                    contentDescription = null,
                    tint = Color(0xFF34D399),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$downloadedCount ses çevrimdışı",
                        color = KitsugiColors.TextPrimary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = formatBytes(downloadedBytes),
                        color = KitsugiColors.TextMuted,
                        fontSize = 9.sp
                    )
                }
                Text(
                    text = "Temizle",
                    color = KitsugiColors.AccentRed,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onClearDownloads)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/** Bayt → okunabilir boyut. */
private fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "0 B"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
}

/**
 * Tek bir kanal satırı — **kaynak türünden bağımsız**.
 *
 * Prosedürel ses de, uzak akış da aynı görsel/etkileşimle yönetilir.
 */
@Composable
private fun ChannelRow(
    channel: ChannelState,
    accent: Color,
    onVolumeChanged: (Float) -> Unit,
    onMuteToggle: () -> Unit,
    onRemove: () -> Unit,
    onRetry: (() -> Unit)? = null
) {
    val stateColor = when (channel.state) {
        ChannelPlayState.PLAYING -> Color(0xFF34D399)
        ChannelPlayState.BUFFERING -> Color(0xFFFBBF24)
        ChannelPlayState.ERROR -> KitsugiColors.AccentRed
        ChannelPlayState.PAUSED -> KitsugiColors.TextMuted
        ChannelPlayState.IDLE -> KitsugiColors.TextMuted
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (channel.muted) KitsugiColors.SurfaceSoft.copy(alpha = 0.5f)
                else KitsugiColors.SurfaceSoft
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Durum noktası
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(stateColor)
            )
            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.sound.title,
                    color = if (channel.muted) KitsugiColors.TextMuted
                    else KitsugiColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (channel.sound.subtitle.isNotBlank()) {
                    Text(
                        text = channel.sound.subtitle,
                        color = KitsugiColors.TextMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Mute
            IconButton(onClick = onMuteToggle, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (channel.muted) Icons.Rounded.VolumeOff
                    else Icons.Rounded.VolumeUp,
                    contentDescription = if (channel.muted) "Sesi aç" else "Sessize al",
                    tint = if (channel.muted) KitsugiColors.AccentRed else accent,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Kaldır
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Kaldır",
                    tint = KitsugiColors.TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Ses seviyesi
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = channel.volume,
                onValueChange = onVolumeChanged,
                modifier = Modifier.weight(1f),
                enabled = !channel.muted,
                colors = SliderDefaults.colors(
                    thumbColor = if (channel.muted) KitsugiColors.TextMuted else accent,
                    activeTrackColor = if (channel.muted) KitsugiColors.Border else accent,
                    inactiveTrackColor = KitsugiColors.Border
                )
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (channel.muted) "—" else "${(channel.volume * 100).toInt()}%",
                color = KitsugiColors.TextMuted,
                fontSize = 10.sp,
                modifier = Modifier.width(32.dp)
            )
        }

        // Hata mesajı + yeniden deneme
        //
        // Eskiden hatalı kanal yalnızca kırmızı nokta gösteriyordu;
        // kullanıcının tek çıkışı kanalı kaldırıp yeniden eklemekti.
        channel.errorMessage?.let { msg ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = msg,
                    color = KitsugiColors.AccentRed,
                    fontSize = 9.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (channel.state == ChannelPlayState.ERROR && onRetry != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Yeniden dene",
                        color = accent,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onRetry() }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
//  ORTAK BİLEŞENLER
// ─────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SoundRow(
    sound: AmbientSound,
    reason: String,
    isActive: Boolean,
    accent: Color,
    isFavorite: Boolean = false,
    /** İndirilmiş mi (çevrimdışı çalınabilir). */
    isDownloaded: Boolean = false,
    /** Devam eden indirme ilerlemesi (0..1), null = indirilmiyor. */
    downloadProgress: Float? = null,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) accent.copy(alpha = 0.16f) else KitsugiColors.SurfaceSoft)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick?.let {
                    {
                        haptic.performHapticFeedback(
                            androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                        )
                        it()
                    }
                }
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = sound.title,
                color = if (isActive) accent else KitsugiColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (reason.isNotBlank()) {
                Text(
                    text = reason,
                    color = KitsugiColors.TextMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // ── İndirme / çevrimdışı göstergesi ─────────────────────────────
        if (onDownload != null) {
            when {
                isDownloaded -> Icon(
                    imageVector = Icons.Rounded.CloudDone,
                    contentDescription = "İndirildi — çevrimdışı çalınabilir",
                    tint = Color(0xFF34D399),
                    modifier = Modifier
                        .size(17.dp)
                        .padding(end = 4.dp)
                )
                downloadProgress != null -> CircularProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.size(15.dp),
                    color = accent,
                    strokeWidth = 1.5.dp,
                    trackColor = KitsugiColors.Border
                )
                else -> IconButton(
                    onClick = onDownload,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudDownload,
                        contentDescription = "İndir — çevrimdışı dinle",
                        tint = KitsugiColors.TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (isFavorite) "Favoriden çıkar" else "Favoriye ekle",
                tint = if (isFavorite) Color(0xFFF472B6) else KitsugiColors.TextMuted,
                modifier = Modifier.size(17.dp)
            )
        }
        Icon(
            imageVector = if (isActive) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isActive) "Durdur" else "Çal",
            tint = accent.copy(alpha = 0.75f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun AtmosphereCard(
    preset: AtmospherePreset,
    score: Float,
    isActive: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(112.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isActive) accent.copy(alpha = 0.22f) else KitsugiColors.SurfaceSoft
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = preset.emoji, fontSize = 26.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = preset.name,
            color = if (isActive) accent else KitsugiColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "%${(score * 100).toInt()} uyum",
            color = KitsugiColors.TextMuted,
            fontSize = 9.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
//  SEKME 5 — ÇEVRİMİÇİ KATALOG (Kaynak Bazlı)
// ─────────────────────────────────────────────────────────────────────────

/**
 * Çevrimiçi kaynaklardan gelen sesler.
 *
 * Listeleme **kaynak bazlı gruplanır** ve her kaynağın durumu görünür:
 *
 *  🟢 yeşil  → etkin + anahtar geçerli (veya anahtarsız)
 *  🟡 sarı   → etkin ama anahtar girilmemiş
 *  🔴 kırmızı→ anahtar var ama doğrulanamadı
 *  ⚪ gri    → kullanıcı kapattı
 *
 * Sonuç kartları kaynaktan bağımsızdır: hepsi aynı "ekle" davranışıyla
 * kanal havuzuna girer, dolayısıyla mute/volume/kaldır butonları çalışır.
 */
@Composable
private fun OnlineTab(
    modifier: Modifier = Modifier,
    results: List<AmbientSound>,
    isSearching: Boolean,
    error: String?,
    query: String,
    sourceStates: List<com.kitsugi.animelist.data.music.MusicSourceState>,
    playingIds: Set<String>,
    accent: Color,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onPlay: (AmbientSound) -> Unit,
    onToggleSource: (com.kitsugi.animelist.data.music.MusicSourceId) -> Unit = {},
    downloadedIds: Set<String> = emptySet(),
    downloadProgress: Map<String, Float> = emptyMap(),
    onDownload: (AmbientSound) -> Unit = {}
) {
    val grouped = remember(results) { results.groupBy { it.sourceId } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        // ── İndirilenler ────────────────────────────────────────────────
        if (downloadedIds.isNotEmpty()) {
            Text(
                text = "İNDİRİLENLER · ${downloadedIds.size} (çevrimdışı çalışır)",
                color = Color(0xFF34D399),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Uçak modunda, metroda, internetsiz ortamda çalarlar. " +
                    "Karışım sekmesinden diğer seslerle birlikte kullan.",
                color = KitsugiColors.TextMuted,
                fontSize = 9.sp,
                lineHeight = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // ── Arama ───────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ses ara (yağmur, piyano, orman…)", fontSize = 12.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    unfocusedBorderColor = KitsugiColors.Border,
                    cursorColor = accent,
                    focusedTextColor = KitsugiColors.TextPrimary,
                    unfocusedTextColor = KitsugiColors.TextPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onSearch,
                enabled = !isSearching,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Ara",
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Hangi kaynak ne sunuyor ─────────────────────────────────────
        var showComparison by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(KitsugiColors.SurfaceSoft.copy(alpha = 0.6f))
                .clickable { showComparison = !showComparison }
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📊", fontSize = 11.sp)
            Spacer(Modifier.width(7.dp))
            Text(
                text = "Hangi kaynak ne sunuyor?",
                color = KitsugiColors.TextSecondary,
                fontSize = 10.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (showComparison) Icons.Rounded.ExpandLess
                else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = KitsugiColors.TextMuted,
                modifier = Modifier.size(15.dp)
            )
        }

        if (showComparison) {
            Spacer(Modifier.height(6.dp))
            com.kitsugi.animelist.data.music.MusicSourceCapability.ranked.forEach { cap ->
                SourceCapabilityRow(cap = cap, accent = accent)
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = "Puan: çeşitlilik %35 · kalite %30 · erişilebilirlik %35",
                color = KitsugiColors.TextMuted,
                fontSize = 8.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        // ── Kaynak durumları ────────────────────────────────────────────
        Text(
            text = "KAYNAKLAR",
            color = KitsugiColors.TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))

        sourceStates.filter { it.def.id != com.kitsugi.animelist.data.music.MusicSourceId.BUILTIN }
            .forEach { state ->
                SourceStatusRow(
                    state = state,
                    onToggle = { onToggleSource(state.def.id) }
                )
                Spacer(Modifier.height(4.dp))
            }

        // ── Sonuçlar ────────────────────────────────────────────────────
        Spacer(Modifier.height(12.dp))

        when {
            isSearching -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = accent,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Kaynaklar taranıyor…", color = KitsugiColors.TextSecondary, fontSize = 12.sp)
                }
            }
            error != null && results.isEmpty() -> {
                Text(
                    text = error,
                    color = KitsugiColors.TextMuted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp)
                )
            }
            results.isEmpty() -> {
                Text(
                    text = "Yukarıdaki kutudan bir arama yap.\nAnahtarsız kaynaklar (Openverse, Archive) hemen çalışır.",
                    color = KitsugiColors.TextMuted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp)
                )
            }
            else -> {
                grouped.forEach { (sourceId, sounds) ->
                    val def = com.kitsugi.animelist.data.music.MusicSources.byId(sourceId)
                    Text(
                        text = (def?.displayName ?: sourceId.name).uppercase() + " · ${sounds.size}",
                        color = KitsugiColors.TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                    )
                    sounds.forEach { sound ->
                        OnlineSoundRow(
                            sound = sound,
                            isPlaying = sound.id in playingIds,
                            accent = accent,
                            isDownloaded = sound.id in downloadedIds,
                            downloadProgress = downloadProgress[sound.id],
                            onPlay = { onPlay(sound) },
                            onDownload = { onDownload(sound) }
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

/**
 * Kaynak durumu satırı — renk noktası + ad + kota/anahtar bilgisi.
 */
@Composable
private fun SourceStatusRow(
    state: com.kitsugi.animelist.data.music.MusicSourceState,
    onToggle: () -> Unit = {}
) {
    val dotColor = when (state.status) {
        com.kitsugi.animelist.data.music.SourceStatus.READY -> Color(0xFF34D399)
        com.kitsugi.animelist.data.music.SourceStatus.UNCHECKED -> Color(0xFF60A5FA)
        com.kitsugi.animelist.data.music.SourceStatus.NO_KEY -> Color(0xFFFBBF24)
        com.kitsugi.animelist.data.music.SourceStatus.INVALID_KEY -> KitsugiColors.AccentRed
        com.kitsugi.animelist.data.music.SourceStatus.DISABLED -> KitsugiColors.TextMuted
    }
    val label = when (state.status) {
        com.kitsugi.animelist.data.music.SourceStatus.READY ->
            if (state.def.keyless) "anahtarsız · hazır" else "hazır"
        com.kitsugi.animelist.data.music.SourceStatus.UNCHECKED -> "anahtar girildi · doğrulanmadı"
        com.kitsugi.animelist.data.music.SourceStatus.NO_KEY -> "anahtar girilmedi"
        com.kitsugi.animelist.data.music.SourceStatus.INVALID_KEY -> "anahtar geçersiz"
        com.kitsugi.animelist.data.music.SourceStatus.DISABLED -> "kapalı"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(KitsugiColors.SurfaceSoft.copy(alpha = 0.6f))
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = state.def.displayName,
            color = if (state.enabled) KitsugiColors.TextPrimary else KitsugiColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "· $label",
            color = KitsugiColors.TextMuted,
            fontSize = 10.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (state.def.nonCommercialOnly) {
            Text(
                text = "NC",
                color = Color(0xFFFBBF24),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(6.dp))
        }
        // Anahtar gerekiyor ama yok → doğrudan edinme sayfasına git
        if (state.status == com.kitsugi.animelist.data.music.SourceStatus.NO_KEY &&
            state.def.signupUrl.isNotBlank()
        ) {
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            Text(
                text = "Anahtar al →",
                color = Color(0xFF60A5FA),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF60A5FA).copy(alpha = 0.15f))
                    .clickable {
                        runCatching { uriHandler.openUri(state.def.signupUrl) }
                    }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
            Spacer(Modifier.width(6.dp))
        }
        KitsugiSwitch(
            checked = state.enabled,
            onCheckedChange = { onToggle() },
            accent = Color(0xFF34D399)
        )
    }
}

/**
 * Çevrimiçi bir sesin satırı — atıf bilgisiyle birlikte.
 */
@Composable
private fun OnlineSoundRow(
    sound: AmbientSound,
    isPlaying: Boolean,
    accent: Color,
    isDownloaded: Boolean = false,
    downloadProgress: Float? = null,
    onPlay: () -> Unit,
    onDownload: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isPlaying) accent.copy(alpha = 0.16f) else KitsugiColors.SurfaceSoft)
            .clickable(onClick = onPlay)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sound.title,
                    color = if (isPlaying) accent else KitsugiColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (sound.subtitle.isNotBlank()) {
                    Text(
                        text = sound.subtitle,
                        color = KitsugiColors.TextMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // ── İndirme ─────────────────────────────────────────────────
            when {
                isDownloaded -> Icon(
                    imageVector = Icons.Rounded.CloudDone,
                    contentDescription = "İndirildi — çevrimdışı çalınabilir",
                    tint = Color(0xFF34D399),
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(17.dp)
                )
                downloadProgress != null -> CircularProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(15.dp),
                    color = accent,
                    strokeWidth = 1.5.dp,
                    trackColor = KitsugiColors.Border
                )
                else -> IconButton(
                    onClick = onDownload,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudDownload,
                        contentDescription = "İndir — çevrimdışı dinle",
                        tint = KitsugiColors.TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Durdur" else "Çal",
                tint = accent.copy(alpha = 0.75f),
                modifier = Modifier.size(20.dp)
            )
        }
        // Atıf — lisans gereği gösterilir
        sound.attributionLine()?.let { line ->
            Text(
                text = line,
                color = KitsugiColors.TextMuted.copy(alpha = 0.7f),
                fontSize = 8.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
//  SEKME 6 — PROFİL (Karışım kaydet / yükle)
// ─────────────────────────────────────────────────────────────────────────

/**
 * Kaydedilmiş karışımlar.
 *
 * Chiarella'nın "profiller" özelliği: kullanıcı bir kez düzen kurar,
 * adlandırır, sonra tek dokunuşla geri yükler. Profil yalnızca
 * **ses kimliği + seviye + mute** saklar — kaynak türü değil.
 */
@Composable
private fun ProfileTab(
    modifier: Modifier = Modifier,
    profiles: List<com.kitsugi.animelist.data.music.MixProfile>,
    channels: List<ChannelState>,
    accent: Color,
    onSave: (String) -> Unit,
    onApply: (com.kitsugi.animelist.data.music.MixProfile) -> Unit,
    onDelete: (String) -> Unit,
    onExport: (com.kitsugi.animelist.data.music.MixProfile) -> Unit = {},
    onImport: () -> Unit = {}
) {
    var showNameField by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        // ── Şu anki karışımı kaydet ─────────────────────────────────────
        if (channels.isNotEmpty()) {
            if (!showNameField) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.14f))
                        .clickable { showNameField = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Bu karışımı kaydet",
                            color = accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${channels.size} kanal · sonra tek dokunuşla geri yükle",
                            color = KitsugiColors.TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            } else {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Karışım adı (Yağmurlu gece…)", fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accent,
                            unfocusedBorderColor = KitsugiColors.Border,
                            cursorColor = accent,
                            focusedTextColor = KitsugiColors.TextPrimary,
                            unfocusedTextColor = KitsugiColors.TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                onSave(name)
                                name = ""
                                showNameField = false
                            }
                        ) {
                            Text("Kaydet", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = {
                                name = ""
                                showNameField = false
                            }
                        ) {
                            Text("Vazgeç", color = KitsugiColors.TextMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // ── Kayıtlı profiller ───────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "KAYITLI KARIŞIMLAR",
                color = KitsugiColors.TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .clickable(onClick = onImport)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentPaste,
                    contentDescription = null,
                    tint = accent.copy(alpha = 0.85f),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text("İçe aktar", color = accent, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(6.dp))

        if (profiles.isEmpty()) {
            Text(
                text = if (channels.isEmpty())
                    "Önce birkaç ses aç, sonra karışımı kaydet."
                else
                    "Henüz kayıt yok — yukarıdan ilk karışımını kaydet.",
                color = KitsugiColors.TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            profiles.forEach { profile ->
                ProfileRow(
                    profile = profile,
                    accent = accent,
                    onApply = { onApply(profile) },
                    onDelete = { onDelete(profile.id) },
                    onExport = { onExport(profile) }
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun ProfileRow(
    profile: com.kitsugi.animelist.data.music.MixProfile,
    accent: Color,
    onApply: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(KitsugiColors.SurfaceSoft)
            .clickable(onClick = onApply)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.name,
                color = KitsugiColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = profile.summary,
                color = KitsugiColors.TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onApply, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Yükle",
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }
        IconButton(onClick = onExport, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = "Paylaş",
                tint = KitsugiColors.TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Sil",
                tint = KitsugiColors.TextMuted,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
//  SEKME 7 — UYKU ZAMANLAYICISI
// ─────────────────────────────────────────────────────────────────────────

/**
 * Uyku zamanlayıcısı.
 *
 * Üç uygulamada da (Chiarella, MixNotice, Metiq) bulunan özellik:
 *  - Hazır süreler (5–120 dk)
 *  - Özel süre (5 dk – 4 saat)
 *  - Kapanış fade'i (aniden kesmez)
 *  - Duraklat / devam / +10 dk
 */
@Composable
private fun TimerTab(
    modifier: Modifier = Modifier,
    timer: com.kitsugi.animelist.data.music.SleepTimerState,
    accent: Color,
    onStart: (Int) -> Unit,
    onCancel: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onExtend: (Int) -> Unit
) {
    var customMinutes by remember { mutableIntStateOf(30) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        if (timer.running) {
            // ── Aktif sayac ─────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(accent.copy(alpha = 0.13f))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "UYKU ZAMANLAYICISI",
                    color = accent.copy(alpha = 0.8f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = timer.display,
                    color = if (timer.paused) KitsugiColors.TextMuted else KitsugiColors.TextPrimary,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Light
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (timer.paused) "duraklatıldı"
                    else "bitince ${timer.fadeSeconds} sn içinde kısılarak susar",
                    color = KitsugiColors.TextMuted,
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { timer.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = accent,
                    trackColor = KitsugiColors.Border
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimerChip(
                        label = if (timer.paused) "Devam" else "Duraklat",
                        accent = accent,
                        onClick = { if (timer.paused) onResume() else onPause() }
                    )
                    TimerChip(label = "+10 dk", accent = accent, onClick = { onExtend(10) })
                    TimerChip(label = "İptal", accent = KitsugiColors.AccentRed, onClick = onCancel)
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── Hazır süreler ───────────────────────────────────────────────
        Text(
            text = "HAZIR SÜRELER",
            color = KitsugiColors.TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))

        val presets = com.kitsugi.animelist.data.music.SleepTimerState.PRESETS_MINUTES
        // 4'lü ızgara
        presets.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { minutes ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(KitsugiColors.SurfaceSoft)
                            .clickable { onStart(minutes) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (minutes >= 60) "${minutes / 60} sa" else "$minutes dk",
                            color = KitsugiColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                // Eksik kalan hücreleri doldur
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(10.dp))

        // ── Özel süre ───────────────────────────────────────────────────
        Text(
            text = "ÖZEL SÜRE",
            color = KitsugiColors.TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${customMinutes} dk",
                color = accent,
                fontSize = 22.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.width(76.dp)
            )
            Slider(
                value = customMinutes.toFloat(),
                onValueChange = { customMinutes = it.toInt() },
                valueRange = com.kitsugi.animelist.data.music.SleepTimerState.MIN_CUSTOM_MINUTES.toFloat()..
                    com.kitsugi.animelist.data.music.SleepTimerState.MAX_CUSTOM_MINUTES.toFloat(),
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = KitsugiColors.Border
                )
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimerChip(label = "Başlat", accent = accent, onClick = { onStart(customMinutes) })
            TimerChip(label = "+5", accent = KitsugiColors.TextSecondary, onClick = { if (customMinutes < 240) customMinutes += 5 })
            TimerChip(label = "−5", accent = KitsugiColors.TextSecondary, onClick = { if (customMinutes > 5) customMinutes -= 5 })
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = "Süre bitince ses aniden kesilmez: son ${timer.fadeSeconds} saniyede " +
                "kademeli olarak kısılır, sonra durur. Uykuya dalarken rahatsız etmez.",
            color = KitsugiColors.TextMuted,
            fontSize = 10.sp,
            lineHeight = 14.sp
        )
    }
}

@Composable
private fun TimerChip(label: String, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(text = label, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

// ─────────────────────────────────────────────────────────────────────────
//  SES DETAY SAYFASI (uzun basınca açılır)
// ─────────────────────────────────────────────────────────────────────────

/**
 * Bir sesin detay kartı.
 *
 * Chiarella'daki "karta uzun bas → açıklama + ideal anlar + bununla iyi
 * gider" akışının karşılığı. Kullanıcı sesi tanımadan eklemek zorunda
 * kalmaz; ne zaman işe yaradığını okur.
 */
@Composable
private fun SoundDetailPage(
    sound: AmbientSound,
    accent: Color,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onPlaySound: (AmbientSound) -> Unit = {},
    onToggleFavorite: () -> Unit
) {
    val meta = remember(sound.id) { SoundMeta.forSound(sound) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // ── Başlık satırı ───────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Geri",
                    tint = KitsugiColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "SES DETAYI",
                color = KitsugiColors.TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Favori",
                    tint = if (isFavorite) Color(0xFFF472B6) else KitsugiColors.TextMuted,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Kahraman başlık ─────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = meta.emoji, fontSize = 26.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sound.title,
                    color = KitsugiColors.TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = meta.category,
                    color = accent,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── Açıklama ────────────────────────────────────────────────────
        Text(
            text = meta.description,
            color = KitsugiColors.TextSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )

        Spacer(Modifier.height(16.dp))

        // ── İdeal anlar ─────────────────────────────────────────────────
        Text(
            text = "İDEAL ANLAR",
            color = KitsugiColors.TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(7.dp))
        meta.bestFor.forEach { moment ->
            Row(
                modifier = Modifier.padding(vertical = 3.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.7f))
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = moment,
                    color = KitsugiColors.TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Bununla iyi gider ───────────────────────────────────────────
        val pairs = remember(sound.id) { SoundMeta.pairsWith(sound) }
        if (pairs.isNotEmpty()) {
            Text(
                text = "BUNUNLA İYİ GİDER",
                color = KitsugiColors.TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pairs.forEach { other ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(KitsugiColors.SurfaceSoft)
                            .clickable { onPlaySound(other) }
                            .padding(horizontal = 10.dp, vertical = 9.dp)
                    ) {
                        Text(text = SoundMeta.emojiOf(other), fontSize = 16.sp)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = other.title,
                            color = KitsugiColors.TextSecondary,
                            fontSize = 10.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── Atıf (uzak sesler için) ─────────────────────────────────────
        sound.attributionLine()?.let { line ->
            Text(
                text = "Atıf: $line",
                color = KitsugiColors.TextMuted.copy(alpha = 0.75f),
                fontSize = 9.sp,
                lineHeight = 13.sp
            )
            Spacer(Modifier.height(14.dp))
        }

        // ── Çal butonu ──────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (isPlaying) accent.copy(alpha = 0.22f) else accent)
                .clickable(onClick = onPlay)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = if (isPlaying) accent else Color.Black,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isPlaying) "Kaldır" else "Karışıma ekle",
                    color = if (isPlaying) accent else Color.Black,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(10.dp))
    }
}

/**
 * Ses meta verisi — açıklama, kategori, ideal anlar.
 *
 * Katalogda `AmbientSound` yalnızca teknik bilgi taşıyor; kullanıcıya
 * gösterilecek "ne zaman işe yarar" bilgisi burada türetiliyor. Aile
 * (family) ve ruh hâline (mood) göre eşleniyor.
 */
private object SoundMeta {

    data class Meta(
        val emoji: String,
        val category: String,
        val description: String,
        val bestFor: List<String>
    )

    fun forSound(sound: AmbientSound): Meta {
        // Aile + isim eşleşmesine göre zengin açıklama
        val title = sound.title.lowercase()

        return when {
            title.contains("yağmur") || title.contains("rain") -> Meta(
                emoji = "🌧",
                category = "Hava · Yağmur",
                description = "Sabit beyaz gürültü bandı. Konuşmayı ve çevre sesini " +
                    "maskeler; düşünmeyi kolaylaştırır. Uykuya geçişte en çok " +
                    "tercih edilen ses türü.",
                bestFor = listOf(
                    "Uykuya dalarken (düşük seste)",
                    "Ders/çalışma sırasında dikkat dağıtıcıları örtmek",
                    "Gergin bir sahnede atmosfer kurmak"
                )
            )
            title.contains("orman") || title.contains("forest") -> Meta(
                emoji = "🌲",
                category = "Doğa · Orman",
                description = "Kuş cıvıltısı ve yaprak hışırtısı içeren katmanlı doğa " +
                    "kaydı. Açık havada olma hissi verir; kapalı mekânda bunaltıyı azaltır.",
                bestFor = listOf(
                    "Sakin/romantik sahneler",
                    "Dinlenme molaları",
                    "Sabah okumaları"
                )
            )
            title.contains("ateş") || title.contains("fire") -> Meta(
                emoji = "🔥",
                category = "Doğa · Ateş",
                description = "Çıtırtı ve alçak frekanslı uğultu. Sıcaklık ve güvenlik " +
                    "hissi verir; kış sahnelerine, dinlenme anlarına uyar.",
                bestFor = listOf(
                    "Sıcak/duygusal sahneler",
                    "Kış akşamı okumaları",
                    "Uyku öncesi gevşeme"
                )
            )
            title.contains("piyano") || title.contains("piano") -> Meta(
                emoji = "🎹",
                category = "Müzik · Piyano",
                description = "Tek enstrümanlı, sakin piyano. Söz yok — okumayı " +
                    "bölmez. Duygusal sahnelerin altını çizer.",
                bestFor = listOf(
                    "Dramatik/dokunaklı sahneler",
                    "Odaklanarak okuma",
                    "Arka planda sakin müzik"
                )
            )
            sound.family == com.kitsugi.animelist.data.music.SoundFamily.INSTRUMENTAL -> Meta(
                emoji = "🎵",
                category = "Müzik · Enstrümantal",
                description = "Enstrümantal parça. Söz içermediği için okuma akışını " +
                    "bozmaz; ritmi sahnenin temposuna uyarlayabilirsin.",
                bestFor = listOf(
                    "Uzun okuma seansları",
                    "Aksiyon sahnelerinde tempo",
                    "Arka plan dolgusu"
                )
            )
            else -> Meta(
                emoji = "🎧",
                category = "Ambiyans",
                description = "Prosedürel olarak cihazında üretilen ortam sesi. " +
                    "İnternet gerektirmez, telif riski yoktur, döngüde kesinti olmaz.",
                bestFor = listOf(
                    "Okuma sırasında atmosfer",
                    "Dikkat dağıtıcıları maskeleme",
                    "Sahne ruhunu destekleme"
                )
            )
        }
    }

    /** Ruh hâli yakınlığına göre iki eşleşen ses döner. */
    fun pairsWith(sound: AmbientSound): List<AmbientSound> {
        val catalog = com.kitsugi.animelist.data.music.ProceduralSoundCatalog.sounds
        return catalog
            .filter { it.id != sound.id }
            .sortedByDescending { other ->
                // Aynı aile → yüksek puan; farklı aile → ruh hâli yakınlığı
                var score = 0f
                if (other.family != sound.family) score += 0.5f
                score -= kotlin.math.abs(other.mood.energy - sound.mood.energy) * 0.3f
                score -= kotlin.math.abs(other.mood.valence - sound.mood.valence) * 0.2f
                score
            }
            .take(2)
    }

    fun emojiOf(sound: AmbientSound): String = forSound(sound).emoji
}


/**
 * Cihazın medya ses seviyesini okur (0..1). Bilinemezse null.
 *
 * Uygulama içi ses ile cihaz sesi çarpılır; kullanıcı neden ses
 * duymadığını anlayamadığında bu gösterge sebebi ortaya koyar.
 */
@Composable
private fun rememberDeviceVolume(): Float? {
    val context = androidx.compose.ui.platform.LocalContext.current
    var volume by remember { mutableStateOf<Float?>(null) }

    androidx.compose.runtime.DisposableEffect(context) {
        val manager = context.getSystemService(android.content.Context.AUDIO_SERVICE)
            as? android.media.AudioManager
        DeviceVolumeHolder.attach(context)
        if (manager == null) {
            volume = null
        } else {
            volume = manager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() /
                manager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                    .coerceAtLeast(1).toFloat()
        }
        onDispose { }
    }
    return volume
}

/**
 * Cihazın medya sesini maksimuma yakın bir seviyeye çeker.
 *
 * Chiarella'daki "100'e taşı" karşılığı. `FLAG_SHOW_UI` ile sistem ses
 * çubuğu görünür — kullanıcı ne olduğunu anlar.
 *
 * `MODIFY_AUDIO_SETTINGS` izni gerekmez: kendi uygulamamız adına
 * `setStreamVolume` çağırmak bu izni gerektiriyor. Bu yüzden bunun yerine
 * **ses yükseltme tuşunu simüle** etmiyoruz; `AudioManager` üzerinden
 * doğrudan ayarlamayı deniyoruz ve başarısız olursa sessizce geçiyoruz.
 */
private fun raiseDeviceVolume() {
    // Bu fonksiyon Composable dışından çağrıldığı için context'i holder'da tutuyoruz.
    DeviceVolumeHolder.raise()
}

/** Context'i tutan küçük yardımcı — Composable dışı erişim için. */
internal object DeviceVolumeHolder {
    private var appContext: android.content.Context? = null

    fun attach(context: android.content.Context) {
        appContext = context.applicationContext
    }

    fun raise() {
        val ctx = appContext ?: return
        val manager = ctx.getSystemService(android.content.Context.AUDIO_SERVICE)
            as? android.media.AudioManager ?: return
        runCatching {
            val max = manager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            val target = (max * 0.85f).toInt().coerceAtLeast(1)
            manager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, target, 0)
        }
    }
}


// ─────────────────────────────────────────────────────────────────────────
//  KISAYOL ŞERİDİ
// ─────────────────────────────────────────────────────────────────────────

/**
 * ⚡ Tek satırlık kısayol şeridi
 *
 * **Neden:** 7 sekme + uzun listeler = sayfa çok yer kaplıyor. Kullanıcı
 * en sık yaptığı 6 işi sekme değiştirmeden yapabilmeli.
 *
 * Yerleşim (tek satır, ~52 dp):
 * ```
 * [▶/⏸] [🔇] [⏱ 30dk] [⏹] · · · [❤ 3] [💾 2]
 * ```
 *
 * Sağdaki iki sayaç birer **kısayol**tır: dokununca ilgili sekmeye atlar.
 * Böylece "favorilerim nerede" araması gerekmez.
 *
 * Yükseklik sabit ve küçük — 90° döndürülmüş telefonda bile yer kalır.
 */
@Composable
private fun QuickActionBar(
    accent: Color,
    isPlaying: Boolean,
    hasChannels: Boolean,
    isMuted: Boolean,
    timerRunning: Boolean,
    timerLabel: String,
    favoriteCount: Int,
    profileCount: Int,
    onTogglePlay: () -> Unit,
    onMuteAll: () -> Unit,
    onQuickTimer: (Int) -> Unit,
    onStopAll: () -> Unit,
    onJumpTo: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(KitsugiColors.SurfaceSoft.copy(alpha = 0.75f))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // ── Oynat / duraklat ────────────────────────────────────────────
        QuickIcon(
            icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            label = if (isPlaying) "Duraklat" else "Çal",
            enabled = hasChannels,
            tint = if (hasChannels) accent else KitsugiColors.TextMuted,
            accent = accent,
            onClick = onTogglePlay
        )

        // ── Tümünü sustur ───────────────────────────────────────────────
        QuickIcon(
            icon = if (isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
            label = if (isMuted) "Aç" else "Sustur",
            enabled = hasChannels,
            tint = if (isMuted) Color(0xFFFBBF24) else KitsugiColors.TextSecondary,
            accent = accent,
            onClick = onMuteAll
        )

        // ── Hızlı zamanlayıcı ───────────────────────────────────────────
        PillAction(
            text = if (timerRunning) timerLabel else "30 dk",
            icon = null,
            active = timerRunning,
            accent = accent,
            onClick = {
                if (timerRunning) onJumpTo(6) else onQuickTimer(30)
            }
        )

        // ── Tümünü durdur ───────────────────────────────────────────────
        QuickIcon(
            icon = Icons.Rounded.Stop,
            label = "Durdur",
            enabled = hasChannels,
            tint = if (hasChannels) KitsugiColors.AccentRed else KitsugiColors.TextMuted,
            accent = accent,
            onClick = onStopAll
        )

        Spacer(Modifier.width(8.dp))

        // ── Favori sayacı → sekme kısayolu ──────────────────────────────
        if (favoriteCount > 0) {
            PillAction(
                text = "$favoriteCount",
                icon = Icons.Filled.Favorite,
                active = false,
                accent = Color(0xFFF472B6),
                onClick = { onJumpTo(0) }
            )
        }

        // ── Profil sayacı → sekme kısayolu ──────────────────────────────
        if (profileCount > 0) {
            PillAction(
                text = "$profileCount",
                icon = Icons.Rounded.Bookmark,
                active = false,
                accent = accent,
                onClick = { onJumpTo(5) }
            )
        }
    }
}

/**
 * Kısayol şeridindeki ikon düğmesi.
 *
 * TV'de D-pad ile odaklanabilir: `onFocusChanged` ile kenarlık çizilir.
 * Dokunmatik cihazlarda bu dal hiç tetiklenmez, görsel değişiklik olmaz.
 */
@Composable
private fun QuickIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    tint: Color,
    accent: Color,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(
                when {
                    focused -> accent.copy(alpha = 0.30f)
                    enabled -> accent.copy(alpha = 0.13f)
                    else -> Color.Transparent
                }
            )
            .then(
                if (focused) Modifier.border(1.5.dp, accent, RoundedCornerShape(9.dp))
                else Modifier
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** Kısayol şeridindeki metin+ikon hapı. */
@Composable
private fun PillAction(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    active: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(
                when {
                    focused -> accent.copy(alpha = 0.30f)
                    active -> accent.copy(alpha = 0.22f)
                    else -> accent.copy(alpha = 0.10f)
                }
            )
            .then(
                if (focused) Modifier.border(1.5.dp, accent, RoundedCornerShape(9.dp))
                else Modifier
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = text,
            color = accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
//  AÇ / KAPA + OTOMATİK ÇALMA ANAHTARLARI
// ─────────────────────────────────────────────────────────────────────────

/**
 * 🎛️ İki anahtarlı kontrol kartı
 *
 * ```
 * ┌───────────────────────────────────────────────┐
 * │ Ortam sesi                        [ ●——— ]    │
 * │ Okurken içeriğe uygun ses çalsın              │
 * │ ─────────────────────────────────────────     │
 * │ Otomatik başlat                   [ ———● ]    │
 * │ Okuyucu açılınca 3 sn sonra başlar            │
 * └───────────────────────────────────────────────┘
 * ```
 *
 * **Neden iki anahtar:** Kullanıcı "otomatik çalma"yı kapatıp elle
 * seçmeye devam edebilmeli. Tek anahtar olsaydı, otomatiği kapatmak
 * paneli tamamen devre dışı bırakırdı.
 *
 * İkinci anahtar, birincisi kapalıyken **devre dışı** görünür — ama
 * gizlenmez (kullanıcı varlığını bilsin).
 */
@Composable
private fun AutoPlayCard(
    accent: Color,
    musicEnabled: Boolean,
    autoPlay: Boolean,
    delaySeconds: Int,
    onMusicEnabledChanged: (Boolean) -> Unit,
    onAutoPlayChanged: (Boolean) -> Unit,
    headphonesOnly: Boolean = false,
    onHeadphonesOnlyChanged: (Boolean) -> Unit = {},
    muteOnSilent: Boolean = false,
    onMuteOnSilentChanged: (Boolean) -> Unit = {},
    includeOnline: Boolean = false,
    onIncludeOnlineChanged: (Boolean) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KitsugiColors.SurfaceSoft.copy(alpha = 0.85f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        // ── Ana anahtar ─────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ortam sesi",
                    color = KitsugiColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (musicEnabled)
                        "Okurken içeriğe uygun ses çalınabilir"
                    else
                        "Tüm ses özellikleri kapalı",
                    color = KitsugiColors.TextMuted,
                    fontSize = 10.sp
                )
            }
            KitsugiSwitch(
                checked = musicEnabled,
                onCheckedChange = onMusicEnabledChanged,
                accent = accent
            )
        }

        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(KitsugiColors.Border.copy(alpha = 0.5f))
        )
        Spacer(Modifier.height(10.dp))

        // ── Otomatik başlatma ───────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Otomatik başlat",
                    color = if (musicEnabled) KitsugiColors.TextPrimary
                    else KitsugiColors.TextMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = when {
                        !musicEnabled -> "Önce ortam sesini aç"
                        autoPlay -> "Okuyucu açılınca ${delaySeconds} sn sonra başlar"
                        else -> "Sen dokunana kadar sessiz kalır"
                    },
                    color = KitsugiColors.TextMuted,
                    fontSize = 10.sp
                )
            }
            KitsugiSwitch(
                checked = autoPlay && musicEnabled,
                onCheckedChange = { onAutoPlayChanged(it) },
                accent = accent,
                enabled = musicEnabled
            )
        }

        // ── Otomatik başlatma ayrıntıları ───────────────────────────────
        //
        // Bu üç anahtar **daha önce yoktu**: ilk ikisi `AppSettings`
        // içinde tanımlıydı ama hiçbir ekrandan değiştirilemiyor ve
        // hiçbir yerde okunmuyordu (ölü ayar). Üçüncüsü ise otomatik
        // başlatmanın çevrimiçi kaynakları hiç kullanmaması sorununu
        // çözüyor.
        val detailsEnabled = musicEnabled && autoPlay
        androidx.compose.animation.AnimatedVisibility(visible = detailsEnabled) {
            Column(modifier = Modifier.padding(top = 6.dp)) {
                AutoPlayDetailRow(
                    title = "Sadece kulaklıkta",
                    subtitle = "Kulaklık takılı değilse otomatik başlamaz",
                    checked = headphonesOnly,
                    accent = accent,
                    onCheckedChange = onHeadphonesOnlyChanged
                )
                AutoPlayDetailRow(
                    title = "Sessiz modda çalma",
                    subtitle = "Cihaz sessiz/titreşimdeyken bekle",
                    checked = muteOnSilent,
                    accent = accent,
                    onCheckedChange = onMuteOnSilentChanged
                )
                AutoPlayDetailRow(
                    title = "Çevrimiçi kaynakları kullan",
                    subtitle = if (includeOnline)
                        "İçeriğe uygun gerçek parça aranır (veri kullanır)"
                    else
                        "Yalnızca cihaz içi sesler — anında ve çevrimdışı",
                    checked = includeOnline,
                    accent = accent,
                    onCheckedChange = onIncludeOnlineChanged
                )
            }
        }
    }
}

/**
 * Otomatik başlatma ayrıntı satırı — başlık + kısa açıklama + anahtar.
 *
 * `AutoPlayCard` içindeki ana satırlardan daha küçük (11sp) çünkü bunlar
 * ikincil ayarlar.
 */
@Composable
private fun AutoPlayDetailRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = KitsugiColors.TextMuted,
                fontSize = 9.5.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        KitsugiSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            accent = accent
        )
    }
}

/**
 * Tema uyumlu anahtar.
 *
 * Material3 `Switch` kullanmak yerine kendi çizimimiz: `Switch`'in
 * varsayılan renkleri temamızın `SurfaceSoft`/`Border` paletiyle
 * uyuşmuyor ve gereğinden büyük duruyor.
 */
@Composable
private fun KitsugiSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accent: Color,
    enabled: Boolean = true
) {
    val trackWidth = 40.dp
    val trackHeight = 22.dp
    val thumbSize = 16.dp

    Box(
        modifier = Modifier
            .width(trackWidth)
            .height(trackHeight)
            .clip(RoundedCornerShape(trackHeight / 2))
            .background(
                when {
                    !enabled -> KitsugiColors.Border.copy(alpha = 0.35f)
                    checked -> accent
                    else -> KitsugiColors.Border
                }
            )
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(thumbSize)
                .clip(CircleShape)
                .background(
                    if (enabled) Color.White
                    else Color.White.copy(alpha = 0.6f)
                )
        )
    }
}

/**
 * 📊 Kaynak kapasite satırı
 *
 * "Hangi kaynak bana ne verir" sorusuna dürüst cevap: güçlü yön +
 * zayıf yön + yıldız puanı. Hiçbir kaynak "en iyi" değil — her birinin
 * parladığı yer farklı.
 */
@Composable
private fun SourceCapabilityRow(
    cap: com.kitsugi.animelist.data.music.MusicSourceCapability.Capability,
    accent: Color
) {
    val def = com.kitsugi.animelist.data.music.MusicSources.byId(cap.sourceId)
    val stars = com.kitsugi.animelist.data.music.MusicSourceCapability.stars(cap)
    val summary = com.kitsugi.animelist.data.music.MusicSourceCapability.summary(cap)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(KitsugiColors.SurfaceSoft.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = def?.displayName ?: cap.sourceId.name,
                color = KitsugiColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stars,
                color = Color(0xFFFBBF24),
                fontSize = 9.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = cap.catalogSize,
                color = KitsugiColors.TextMuted,
                fontSize = 8.sp
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = summary,
            color = accent.copy(alpha = 0.9f),
            fontSize = 9.sp
        )
        Spacer(Modifier.height(2.dp))
        // Zayıf yön — dürüstlük
        Text(
            text = "⚠ ${cap.weakness}",
            color = KitsugiColors.TextMuted,
            fontSize = 8.sp,
            lineHeight = 11.sp
        )
    }
}
