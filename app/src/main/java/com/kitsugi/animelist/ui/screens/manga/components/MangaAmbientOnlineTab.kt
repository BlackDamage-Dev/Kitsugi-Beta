package com.kitsugi.animelist.ui.screens.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.ambient.AmbientAudioDownloadManager
import com.kitsugi.animelist.data.ambient.AmbientAudioDownloadStatus
import com.kitsugi.animelist.data.ambient.AmbientAudioMode
import com.kitsugi.animelist.data.ambient.AmbientAudioSource
import com.kitsugi.animelist.data.ambient.AmbientAudioTrack
import com.kitsugi.animelist.data.ambient.AmbientQueueRules
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.utils.kitsugiVerticalScroll

/**
 * 🎧 "Çevrimiçi" sekmesi — **eski sekmeli panel düzeni** içinde çok
 * kaynaklı ortam sesi motoru.
 *
 * Görsel dil v2.4.466 paneliyle aynıdır (indirilenler şeridi, arama,
 * "Hangi kaynak ne sunuyor?", KAYNAKLAR anahtarları, sonuç kartları).
 * Yalnızca veri kaynağı yeni motordur: 11 kaynak (YouTube Music, Jamendo,
 * AnimeThemes + 8 anahtarsız katalog), Media3
 * kuyruğu ile sıralı otomatik çalma ve çevrimdışı indirme.
 */
@Composable
internal fun AmbientOnlineTab(
    controller: MangaAmbientOnlineController,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val downloadStates by AmbientAudioDownloadManager.downloads.collectAsState()
    val uriHandler = LocalUriHandler.current
    val downloadedCount = remember(downloadStates) {
        downloadStates.count { it.value.status == AmbientAudioDownloadStatus.COMPLETED }
    }

    // Uygulama yeniden açıldığında daha önce indirilen dosyaları geri yükle:
    // aksi hâlde "çevrimdışı çalışır" vaadi tutulmaz (v2.4.471 davranışı korunur).
    LaunchedEffect(controller.tracks.map { it.id }) {
        controller.tracks.forEach { track ->
            AmbientAudioDownloadManager.restoreSaved(context, track)
        }
    }

    // Son çalınanlar geçmişini güncelle: aynı parçaların tekrar tekrar
    // önerilmesini engeller (sıralı otomatik çalmada çeşitlilik sağlar).
    LaunchedEffect(controller.playback.isPlaying, controller.playback.currentTrack?.id) {
        if (controller.playback.isPlaying) {
            controller.playback.currentTrack?.let(controller::recordPlayed)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .kitsugiVerticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        // ── Çalan / sıradaki parça ───────────────────────────────────────
        ActiveAmbientTrackBar(controller = controller, accent = accent)

        // ── İndirilenler ────────────────────────────────────────────────
        if (downloadedCount > 0) {
            Text(
                text = "İNDİRİLENLER · $downloadedCount (çevrimdışı çalışır)",
                color = Color(0xFF34D399),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Uçak modunda, metroda, internetsiz ortamda çalarlar. " +
                    "Çevrimiçi akışı kapatsan bile indirilmiş parçalar sırayla çalmaya devam eder.",
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
                value = controller.query,
                onValueChange = controller::onQueryChanged,
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
                onClick = { controller.search() },
                enabled = !controller.isSearching,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f))
            ) {
                if (controller.isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = accent,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = stringResource(R.string.ambient_audio_search),
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // ── YouTube Music kısayolu ──────────────────────────────────────
        // Telifli OST'leri uygulama içinde akıtmak yerine, kullanıcının kendi
        // YouTube Music hesabında (Premium varsa arka planda) doğru aramayı
        // açar. Böylece ne ToS ihlali ne de sürekli kırılan bir çıkarıcı olur.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    val term = controller.query.ifBlank { controller.titleQuery }
                    if (term.isNotBlank()) {
                        runCatching {
                            uriHandler.openUri(
                                "https://music.youtube.com/search?q=" +
                                    java.net.URLEncoder.encode(term, "UTF-8")
                            )
                        }
                    }
                },
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "YouTube Music'te ara ↗",
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "kendi hesabınla açılır",
                color = KitsugiColors.TextMuted,
                fontSize = 9.sp
            )
        }

        Spacer(Modifier.height(8.dp))

        // ── Mod: otomatik / müzik / ambiyans ────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AmbientAudioMode.entries.forEach { item ->
                val label = when (item) {
                    AmbientAudioMode.AUTO -> stringResource(R.string.ambient_audio_auto)
                    AmbientAudioMode.MUSIC -> stringResource(R.string.ambient_audio_music)
                    AmbientAudioMode.AMBIENCE -> stringResource(R.string.ambient_audio_ambience)
                }
                FilterChip(
                    selected = controller.mode == item,
                    onClick = { controller.setMode(item) },
                    label = { Text(label, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent.copy(alpha = 0.22f),
                        selectedLabelColor = accent,
                        containerColor = KitsugiColors.SurfaceSoft,
                        labelColor = KitsugiColors.TextSecondary
                    )
                )
            }
            // Kuyruğu karıştır: sıralı otomatik çalmanın "en alakalı önce"
            // düzenini korur, ancak çalma sırasını rastgeleleştirir.
            FilterChip(
                selected = controller.shuffleEnabled,
                onClick = { controller.setShuffle(!controller.shuffleEnabled) },
                label = { Text("Karıştır", fontSize = 11.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
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

        Spacer(Modifier.height(8.dp))

        // ── Oynatma seçenekleri: tekrar · sıralı devam · sıraya dön ─────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val repeatLabel = when (controller.repeatMode) {
                AmbientQueueRules.REPEAT_ONE -> "Parça tekrarı"
                AmbientQueueRules.REPEAT_ALL -> "Liste tekrarı"
                else -> "Tekrar kapalı"
            }
            FilterChip(
                selected = controller.repeatMode != AmbientQueueRules.REPEAT_OFF,
                onClick = { controller.cycleRepeatMode() },
                label = { Text(repeatLabel, fontSize = 11.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = if (controller.repeatMode == AmbientQueueRules.REPEAT_ONE) {
                            Icons.Rounded.RepeatOne
                        } else {
                            Icons.Rounded.Repeat
                        },
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accent.copy(alpha = 0.22f),
                    selectedLabelColor = accent,
                    containerColor = KitsugiColors.SurfaceSoft,
                    labelColor = KitsugiColors.TextSecondary
                )
            )
            FilterChip(
                selected = controller.continuousPlay,
                onClick = { controller.setContinuousPlay(!controller.continuousPlay) },
                label = { Text("Sıralı devam", fontSize = 11.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.PlaylistPlay,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accent.copy(alpha = 0.22f),
                    selectedLabelColor = accent,
                    containerColor = KitsugiColors.SurfaceSoft,
                    labelColor = KitsugiColors.TextSecondary
                )
            )
            if (controller.canResumeAutoQueue) {
                FilterChip(
                    selected = false,
                    onClick = { controller.resumeAutoQueue() },
                    label = { Text("Sıraya dön", fontSize = 11.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Replay,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = KitsugiColors.SurfaceSoft,
                        labelColor = accent
                    )
                )
            }
        }
        Text(
            text = if (controller.continuousPlay) {
                "Sıralı devam açık: seçtiğin parçadan sonra da benzer parçalar kendiliğinden sıraya girer."
            } else {
                "Sıralı devam kapalı: seçtiğin parça bitince durur."
            },
            color = KitsugiColors.TextMuted,
            fontSize = 9.sp,
            lineHeight = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        Spacer(Modifier.height(8.dp))

        // ── Başlıkla ara + en iyi eşleşmeyi sırayla çal ─────────────────
        Button(
            onClick = { controller.playBestForManga() },
            enabled = !controller.isSearching && controller.enabledSources.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = accent.copy(alpha = 0.9f),
                contentColor = Color(0xFF10151B),
                disabledContainerColor = KitsugiColors.SurfaceSoft,
                disabledContentColor = KitsugiColors.TextMuted
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (controller.isSearching) {
                CircularProgressIndicator(
                    color = Color(0xFF10151B),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = stringResource(
                    if (controller.onlinePlaybackEnabled) R.string.ambient_audio_auto_play
                    else R.string.ambient_audio_auto_search
                ),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(4.dp))

        // ── Çevrimiçi akış oynatımı ─────────────────────────────────────
        AmbientSwitchRow(
            title = stringResource(R.string.ambient_audio_online_playback),
            subtitle = if (controller.onlinePlaybackEnabled) {
                "Parçalar internetten akış olarak çalar; indirme gerekmez."
            } else {
                "Kapalı · yalnızca indirilmiş dosyalar çalınır (veri kullanmaz)."
            },
            checked = controller.onlinePlaybackEnabled,
            accent = accent,
            onCheckedChange = controller::setOnlinePlaybackEnabled
        )

        Spacer(Modifier.height(10.dp))

        // ── Hangi kaynak ne sunuyor ─────────────────────────────────────
        var showComparison by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(KitsugiColors.SurfaceSoft.copy(alpha = 0.6f))
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
            IconButton(onClick = { showComparison = !showComparison }) {
                Icon(
                    imageVector = if (showComparison) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = KitsugiColors.TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (showComparison) {
            Spacer(Modifier.height(4.dp))
            AmbientAudioSource.entries.forEach { source ->
                val label = sourceName(source)
                Text(
                    text = "• $label — ${sourceCapability(source)}",
                    color = KitsugiColors.TextMuted,
                    fontSize = 9.5.sp,
                    lineHeight = 13.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Sonuçlar ────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when {
                    controller.isSearching -> stringResource(R.string.ambient_audio_results)
                    controller.titleMatchCount + controller.contextMatchCount > 0 -> stringResource(
                        R.string.ambient_audio_queue_counts,
                        controller.titleMatchCount,
                        controller.contextMatchCount
                    )
                    controller.usedCache -> stringResource(R.string.ambient_audio_cached)
                    else -> stringResource(R.string.ambient_audio_results)
                },
                color = KitsugiColors.TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (controller.tracks.isNotEmpty() && !controller.isSearching) {
                TextButton(onClick = { controller.playNextCandidate() }) {
                    Text(
                        text = stringResource(R.string.ambient_audio_another),
                        color = accent,
                        fontSize = 10.sp
                    )
                }
            }
        }

        if (controller.warnings.isNotEmpty()) {
            controller.warnings.forEach { warning ->
                Text(
                    text = warning,
                    color = Color(0xFFFFCC80),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }

        if (controller.tracks.isEmpty() && !controller.isSearching && controller.warnings.isEmpty()) {
            Text(
                text = stringResource(R.string.ambient_audio_empty_hint),
                color = KitsugiColors.TextMuted,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )
        }

        controller.tracks.forEach { track ->
            AmbientTrackRow(
                track = track,
                controller = controller,
                accent = accent,
                downloadStatus = downloadStates[track.id]?.status,
                downloadProgress = downloadStates[track.id]?.progress,
                downloadConverted = downloadStates[track.id]?.converted == true,
                downloadMessage = downloadStates[track.id]?.message,
                onOpenSource = { url -> runCatching { uriHandler.openUri(url) } }
            )
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ActiveAmbientTrackBar(
    controller: MangaAmbientOnlineController,
    accent: Color,
) {
    val playback = controller.playback
    val active = playback.currentTrack
    if (active == null) {
        playback.error?.let { error ->
            Text(error, color = Color(0xFFFFB4AB), fontSize = 10.sp)
        }
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                text = active.title,
                color = KitsugiColors.TextPrimary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (playback.queueLength > 1) {
                    "${active.creator} · ${playback.queuePosition}/${playback.queueLength} · sırayla çalıyor"
                } else {
                    "${active.creator} · ${active.sourceLabel}"
                },
                color = KitsugiColors.TextMuted,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { playback.skipToPrevious() }, enabled = playback.hasPreviousTrack) {
            Icon(
                Icons.Rounded.SkipPrevious,
                contentDescription = stringResource(R.string.ambient_audio_skip_previous),
                tint = KitsugiColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(onClick = { playback.togglePlayback() }) {
            Icon(
                imageVector = if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(R.string.ambient_audio_play),
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }
        IconButton(onClick = { playback.skipToNext() }, enabled = playback.hasNextTrack) {
            Icon(
                Icons.Rounded.SkipNext,
                contentDescription = stringResource(R.string.ambient_audio_skip_next),
                tint = KitsugiColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(onClick = { playback.stop() }) {
            Icon(
                Icons.Rounded.Stop,
                contentDescription = stringResource(R.string.ambient_audio_stop),
                tint = KitsugiColors.TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun AmbientTrackRow(
    track: AmbientAudioTrack,
    controller: MangaAmbientOnlineController,
    accent: Color,
    downloadStatus: AmbientAudioDownloadStatus?,
    downloadProgress: Float?,
    downloadConverted: Boolean,
    downloadMessage: String? = null,
    onOpenSource: (String) -> Unit,
) {
    val context = LocalContext.current
    val isSelected = controller.playback.currentTrack?.id == track.id
    val isQueued = track.id in controller.playback.queuedTrackIds
    val isDownloaded = downloadStatus == AmbientAudioDownloadStatus.COMPLETED
    val isPlayable = controller.onlinePlaybackEnabled || isDownloaded

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) accent.copy(alpha = 0.16f)
                else KitsugiColors.SurfaceSoft.copy(alpha = 0.65f)
            )
            .padding(start = 11.dp, end = 6.dp, top = 8.dp, bottom = 2.dp)
    ) {
        Text(
            text = track.title,
            color = KitsugiColors.TextPrimary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val duration = if (track.provider.equals("radio_browser", ignoreCase = true)) {
            stringResource(R.string.ambient_audio_live_radio)
        } else {
            track.durationMs?.let(::formatAmbientDuration)
                ?: stringResource(R.string.ambient_audio_duration_unknown)
        }
        Text(
            text = "${track.creator} · ${track.sourceLabel} · $duration",
            color = KitsugiColors.TextMuted,
            fontSize = 9.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 1.dp)
        )
        if (downloadStatus == AmbientAudioDownloadStatus.FAILED && !downloadMessage.isNullOrBlank()) {
            Text(
                text = downloadMessage,
                color = Color(0xFFFFB4AB),
                fontSize = 9.sp,
                lineHeight = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (track.landingUrl.startsWith("https://")) {
                TextButton(
                    onClick = { onOpenSource(track.landingUrl) },
                    modifier = Modifier.heightIn(min = 40.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ambient_audio_open_source),
                        color = accent,
                        fontSize = 8.5.sp
                    )
                }
            } else {
                Spacer(Modifier.height(40.dp))
            }
            Spacer(Modifier.weight(1f))

            // İndir / çevrimdışı kaydet
            // YouTube işaretçisi de indirilebilir: adres indirme anında çözülür ve
            // dosya cihaza kalıcı yazılır (imzalı adresin süresi dolması etkilemez).
            val canDownload = track.isDownloadable &&
                !track.provider.equals("radio_browser", ignoreCase = true)
            // İndirilemeyen parçada düğme gizlenmez: soluk simge, nedeni bildirimle söyler.
            if (!canDownload && !track.provider.equals("radio_browser", ignoreCase = true)) {
                val reason = AmbientQueueRules.downloadRefusalReason(track).orEmpty()
                IconButton(
                    onClick = { Toast.makeText(context, reason, Toast.LENGTH_SHORT).show() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudOff,
                        contentDescription = reason,
                        tint = KitsugiColors.TextMuted.copy(alpha = 0.6f),
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            if (canDownload) {
                val downloading = downloadStatus != null && downloadStatus in setOf(
                    AmbientAudioDownloadStatus.QUEUED,
                    AmbientAudioDownloadStatus.DOWNLOADING,
                    AmbientAudioDownloadStatus.CONVERTING,
                    AmbientAudioDownloadStatus.SAVING
                )
                IconButton(
                    onClick = { controller.download(track) },
                    enabled = downloadStatus == null || downloadStatus == AmbientAudioDownloadStatus.FAILED,
                    modifier = Modifier.size(40.dp)
                ) {
                    if (downloading) {
                        CircularProgressIndicator(
                            color = accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = when {
                                isDownloaded -> Icons.Rounded.DownloadDone
                                downloadStatus == AmbientAudioDownloadStatus.FAILED -> Icons.Rounded.Refresh
                                else -> Icons.Rounded.CloudDownload
                            },
                            contentDescription = when {
                                isDownloaded -> if (downloadConverted) {
                                    stringResource(R.string.ambient_audio_downloaded_converted)
                                } else {
                                    stringResource(R.string.ambient_audio_downloaded)
                                }
                                downloadStatus == AmbientAudioDownloadStatus.FAILED ->
                                    stringResource(R.string.ambient_audio_download_retry)
                                else -> stringResource(R.string.ambient_audio_download)
                            },
                            tint = if (isDownloaded) Color(0xFF34D399) else KitsugiColors.TextSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                if (downloading && downloadProgress != null) {
                    Text(
                        text = "%${(downloadProgress * 100).toInt()}",
                        color = KitsugiColors.TextMuted,
                        fontSize = 8.sp
                    )
                }
            }

            // "Bir daha önerme": kalıcı kara listeye ekler ve listeden çıkarır.
            IconButton(
                onClick = { controller.blockTrack(track) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Bu parçayı bir daha önerme",
                    tint = KitsugiColors.TextMuted,
                    modifier = Modifier.size(15.dp)
                )
            }

            IconButton(
                onClick = { controller.addToQueue(track) },
                enabled = isPlayable && !isQueued,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (isQueued) Icons.Rounded.PlaylistAddCheck else Icons.Rounded.PlaylistAdd,
                    contentDescription = stringResource(
                        if (isQueued) R.string.ambient_audio_in_queue
                        else R.string.ambient_audio_add_to_queue
                    ),
                    tint = if (isPlayable) accent else KitsugiColors.TextMuted,
                    modifier = Modifier.size(19.dp)
                )
            }

            IconButton(
                onClick = { controller.playTrack(track) },
                enabled = isPlayable,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.ambient_audio_play),
                    tint = if (isPlayable) accent else KitsugiColors.TextMuted,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
}

@Composable
private fun AmbientSourceToggleRow(
    source: AmbientAudioSource,
    checked: Boolean,
    accent: Color,
    onToggle: () -> Unit,
) {
    val dotColor = when (source) {
        AmbientAudioSource.RADIO_BROWSER -> Color(0xFFFBBF24)
        AmbientAudioSource.AUDIUS -> Color(0xFF60A5FA)
        else -> Color(0xFF34D399)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(KitsugiColors.SurfaceSoft.copy(alpha = 0.45f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (checked) dotColor else KitsugiColors.TextMuted)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = sourceName(source),
                color = if (checked) KitsugiColors.TextPrimary else KitsugiColors.TextMuted,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = sourceCapability(source),
                color = KitsugiColors.TextMuted,
                fontSize = 8.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF10151B),
                checkedTrackColor = accent,
                uncheckedThumbColor = KitsugiColors.TextMuted,
                uncheckedTrackColor = KitsugiColors.Border
            )
        )
    }
}

/**
 * Ayarlar sekmesine eklenen "internetten otomatik çalma" kartı.
 *
 * Kullanıcı isteği: eski panel düzeni korunacak **ve** internetten otomatik
 * çalma anahtarları yine bu panelin Ayarlar sekmesinde olacak.
 */
@Composable
internal fun AmbientOnlineSettingsCard(
    controller: MangaAmbientOnlineController,
    accent: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KitsugiColors.SurfaceSoft.copy(alpha = 0.85f))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = "İnternetten otomatik çalma",
            color = KitsugiColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "Okuduğun içeriğin adıyla arama yapılır, en alakalı sonuç ve " +
                "benzerleri sırayla kendiliğinden çalar. Yukarıdaki \"Otomatik " +
                "başlat\" anahtarından bağımsızdır (yalnızca 'Ortam sesi' açık olmalı).",
            color = KitsugiColors.TextMuted,
            fontSize = 10.sp,
            lineHeight = 13.sp
        )

        Spacer(Modifier.height(8.dp))
        AmbientSwitchRow(
            title = "Açılışta otomatik çal",
            subtitle = if (controller.autoPlayOnline) {
                "Okuyucu açılınca manga adıyla aranır, en alakalı parça sırayla çalar"
            } else {
                "Kapalı · sen dokunana kadar sessiz kalır"
            },
            checked = controller.autoPlayOnline,
            accent = accent,
            onCheckedChange = controller::setAutoPlayOnline
        )
        AmbientSwitchRow(
            title = stringResource(R.string.ambient_audio_online_playback),
            subtitle = if (controller.onlinePlaybackEnabled) {
                "Akış açık · internetten çalar"
            } else {
                "Kapalı · yalnızca indirilmiş dosyalar"
            },
            checked = controller.onlinePlaybackEnabled,
            accent = accent,
            onCheckedChange = controller::setOnlinePlaybackEnabled
        )

        Spacer(Modifier.height(6.dp))
        Text(
            text = "KAYNAKLAR",
            color = KitsugiColors.TextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        AmbientAudioSource.entries.chunked(2).forEach { rowSources ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowSources.forEach { source ->
                    val selected = source in controller.enabledSources
                    FilterChip(
                        selected = selected,
                        onClick = { controller.toggleSource(source) },
                        modifier = Modifier.weight(1f),
                        label = {
                            Text(
                                text = sourceName(source),
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                if (rowSources.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(4.dp))
        }

        // Kaynak gizliliği ve Jamendo notu (Çevrimiçi sekmesinden buraya taşındı).
        if (!controller.jamendoConfigured) {
            Text(
                text = "Jamendo atlandı: kişisel Client ID girilmedi (Harici Entegrasyonlar → Müzik).",
                color = Color(0xFFFBBF24),
                fontSize = 9.sp,
                lineHeight = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Manga adı seçili ses kaynaklarına gönderilir; bölüm adı, özet metni, " +
                "sayfa içeriği ve hesap bilgisi gönderilmez. Sonuçlar cihazda 12 saat " +
                "önbelleğe alınır, Radio Browser canlı istasyon sunar.",
            color = KitsugiColors.TextMuted,
            fontSize = 9.sp,
            lineHeight = 12.sp
        )

        // "Bir daha önerme" listesi: varsa temizleme kısayolu gösterilir.
        if (controller.blockedIds.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${controller.blockedIds.size} parça bir daha önerilmiyor",
                    color = KitsugiColors.TextMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { controller.clearBlocked() }) {
                    Text("Temizle", color = accent, fontSize = 10.5.sp)
                }
            }
        }
    }
}

@Composable
private fun AmbientSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
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
                fontSize = 9.5.sp,
                lineHeight = 12.sp
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF10151B),
                checkedTrackColor = accent,
                uncheckedThumbColor = KitsugiColors.TextMuted,
                uncheckedTrackColor = KitsugiColors.Border
            )
        )
    }
}

@Composable
private fun sourceName(source: AmbientAudioSource): String = when (source) {
    AmbientAudioSource.FREESOUND -> stringResource(R.string.ambient_audio_source_freesound)
    AmbientAudioSource.WIKIMEDIA_AUDIO -> stringResource(R.string.ambient_audio_source_wikimedia)
    AmbientAudioSource.INTERNET_ARCHIVE -> stringResource(R.string.ambient_audio_source_archive)
    AmbientAudioSource.AUDIUS -> stringResource(R.string.ambient_audio_source_audius)
    AmbientAudioSource.WIKIMEDIA_COMMONS -> stringResource(R.string.ambient_audio_source_commons)
    AmbientAudioSource.CCMIXTER -> stringResource(R.string.ambient_audio_source_ccmixter)
    AmbientAudioSource.FREE_TO_USE -> stringResource(R.string.ambient_audio_source_freetouse)
    AmbientAudioSource.RADIO_BROWSER -> stringResource(R.string.ambient_audio_source_radiobrowser)
    AmbientAudioSource.JAMENDO -> "Jamendo"
    AmbientAudioSource.ANIMETHEMES -> "Anime OP/ED"
    AmbientAudioSource.YOUTUBE -> "YouTube Music"
}

private fun sourceCapability(source: AmbientAudioSource): String = when (source) {
    AmbientAudioSource.FREESOUND -> "CC kayıtlar · anahtarsız Openverse üzerinden"
    AmbientAudioSource.WIKIMEDIA_AUDIO -> "Vikipedi ses dosyaları · anahtarsız"
    AmbientAudioSource.INTERNET_ARCHIVE -> "Kamu malı arşiv · anahtarsız"
    AmbientAudioSource.AUDIUS -> "Merkeziyetsiz müzik · anahtarsız"
    AmbientAudioSource.WIKIMEDIA_COMMONS -> "Özgür medya arşivi · anahtarsız"
    AmbientAudioSource.CCMIXTER -> "Remiks/CC müzik · anahtarsız"
    AmbientAudioSource.FREE_TO_USE -> "Ücretsiz kullanım kataloğu · anahtarsız"
    AmbientAudioSource.RADIO_BROWSER -> "Canlı radyo istasyonları · anahtarsız"
    AmbientAudioSource.JAMENDO -> "Kişisel Client ID gerekir · akış (indirilemez)"
    AmbientAudioSource.ANIMETHEMES -> "Animenin kendi OP/ED şarkısı · indirilebilir"
    AmbientAudioSource.YOUTUBE -> "Tüm YouTube kataloğu · akış (indirilemez)"
}

private fun formatAmbientDuration(durationMs: Long): String {
    val seconds = (durationMs / 1_000L).coerceAtLeast(0L)
    val minutes = seconds / 60L
    val remainingSeconds = seconds % 60L
    return "%d:%02d".format(minutes, remainingSeconds)
}
