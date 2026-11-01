package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.ui.icons.KitsugiArt
import com.kitsugi.animelist.ui.icons.KitsugiArtIcon

import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke

import android.content.Context
import android.content.res.Configuration
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.data.repository.AddonStreamRepository
import com.kitsugi.animelist.data.repository.StreamSource
import com.kitsugi.animelist.data.remote.DebridResolver
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

enum class DebridCacheState {
    CACHED,
    NOT_CACHED,
    P2P
}

fun getCacheState(stream: StreamSource): DebridCacheState {
    val nameLower = stream.name.lowercase(Locale.ROOT)
    val titleLower = stream.title.lowercase(Locale.ROOT)
    
    return when {
        nameLower.contains("[rd+]") || nameLower.contains("rd+") || nameLower.contains("cached") ||
        titleLower.contains("[rd+]") || titleLower.contains("rd+") ||
        nameLower.contains("[tb+]") || nameLower.contains("tb+") ||
        nameLower.contains("[pm+]") || nameLower.contains("pm+") ||
        nameLower.contains("[ad+]") || nameLower.contains("ad+") -> DebridCacheState.CACHED
        
        nameLower.contains("[rd~]") || nameLower.contains("download") ||
        titleLower.contains("[rd~]") || titleLower.contains("download") -> DebridCacheState.NOT_CACHED
        
        else -> DebridCacheState.P2P
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitsugiStreamSelectorBottomSheet(
    malId: Int?,
    aniListId: Int?,
    season: Int = 1,
    episodeNumber: Int,
    mediaTitle: String,
    isMovie: Boolean = false,
    onStreamSelected: (url: String, title: String, source: StreamSource) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { AddonStreamRepository(context) }
    // Shared LazyListState – used by the portrait stream list and wired to
    // KitsugiSheetOrDialog.innerScrollState so drag-to-dismiss is blocked mid-scroll.
    val streamListState = rememberLazyListState()

    var isLoading by remember { mutableStateOf(true) }
    var streams by remember { mutableStateOf<List<StreamSource>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var resolvingSource by remember { mutableStateOf<StreamSource?>(null) }
    var activeStreamJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val cancelResolving = {
        activeStreamJob?.cancel()
        activeStreamJob = null
        resolvingSource = null
    }

    BackHandler(enabled = resolvingSource != null) {
        cancelResolving()
    }
    var showP2pConsentDialog by remember { mutableStateOf(false) }
    var pendingP2pStream by remember { mutableStateOf<StreamSource?>(null) }

    val resolveAndSelectStream: (StreamSource) -> Unit = { stream ->
        val isTorrent = stream.isTorrent
        val hasDebrid = !DebridResolver(context).getApiKey().isNullOrBlank()
        val p2pEnabled = com.kitsugi.animelist.core.p2p.P2pSettingsRepository.isP2pEnabled()

        if (isTorrent && !hasDebrid && !p2pEnabled) {
            if (!com.kitsugi.animelist.core.p2p.P2pSettingsRepository.isConsentGranted()) {
                pendingP2pStream = stream
                showP2pConsentDialog = true
            } else {
                com.kitsugi.animelist.core.p2p.P2pSettingsRepository.setP2pEnabled(true)
            }
        }

        if (!isTorrent || hasDebrid || com.kitsugi.animelist.core.p2p.P2pSettingsRepository.isP2pEnabled()) {
            activeStreamJob?.cancel()
            resolvingSource = stream
            activeStreamJob = coroutineScope.launch {
                val resolvedUrl = try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        repository.resolveStreamUrl(stream)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                resolvingSource = null
                if (resolvedUrl != null && !resolvedUrl.startsWith("magnet:", ignoreCase = true) && !resolvedUrl.startsWith("torrent://", ignoreCase = true)) {
                    onStreamSelected(resolvedUrl, stream.title, stream)
                } else {
                    errorMessage = if (isTorrent) KitsugiStrings.get(R.string.p2p_akis_motoru_baslatilamadi_veya_torrent_cozumlenemedi) else KitsugiStrings.get(R.string.akis_linki_cozumlenemedi)
                }
            }
        }
    }

    if (showP2pConsentDialog) {
        com.kitsugi.animelist.ui.components.p2p.P2pConsentDialog(
            onDismiss = {
                showP2pConsentDialog = false
                pendingP2pStream = null
            },
            onConsentApproved = {
                showP2pConsentDialog = false
                val pending = pendingP2pStream
                pendingP2pStream = null
                if (pending != null) {
                    resolveAndSelectStream(pending)
                }
            }
        )
    }

    // Fetch streams
    LaunchedEffect(malId, aniListId, season, episodeNumber, isMovie) {
        isLoading = true
        errorMessage = null
        try {
            val fetched = repository.getStreamsForEpisode(
                malId = malId,
                aniListId = aniListId,
                season = season,
                episode = episodeNumber,
                isMovie = isMovie
            )
            streams = fetched
            if (fetched.isEmpty()) {
                errorMessage = KitsugiStrings.get(R.string.herhangi_bir_akis_kaynagi_bulunamadi_eklentilerinizi_veya_realde)
            }
        } catch (e: Exception) {
            errorMessage = KitsugiStrings.get(R.string.akislar_yuklenirken_bir_hata_olustu_x, e.localizedMessage)
        } finally {
            isLoading = false
        }
    }

    KitsugiSheetOrDialog(
        onDismiss = onDismiss,
        innerScrollState = streamListState
    ) {
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isLandscape) {
            // Landscape: Header+Tabs sol, Akış listesi sağ
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Sol panel: başlık + kapatma + addon sekmeleri
                Column(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mediaTitle,
                                color = KitsugiColors.TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.bolum_episodenumber_akis_secenekleri, episodeNumber),
                                color = KitsugiColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        IconButton(
                            onClick = LocalDismissAnimated.current,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = KitsugiColors.SurfaceStrong
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Close,
                                contentDescription = stringResource(R.string.kapat),
                                tint = KitsugiColors.TextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!isLoading && errorMessage == null && resolvingSource == null) {
                        val addonNames = remember(streams) {
                            listOf(KitsugiStrings.get(R.string.notif_filter_all)) + streams.map { it.addonName }.distinct()
                        }
                        var selectedAddonLandscape by remember { mutableStateOf("Tümü") }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            addonNames.forEach { addon ->
                                val count = if (addon == "Tümü") streams.size else streams.count { it.addonName == addon }
                                val isSelected = selectedAddonLandscape == addon
                                val backgroundColor = if (isSelected) LocalKitsugiAccent.current else KitsugiColors.SurfaceStrong
                                val contentColor = if (isSelected) Color.White else KitsugiColors.TextPrimary

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(backgroundColor)
                                        .tvClickable(shape = RoundedCornerShape(12.dp), onClick = { selectedAddonLandscape = addon })
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = addon,
                                            color = contentColor,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(if (isSelected) Color.White.copy(alpha = 0.2f) else KitsugiColors.Surface.copy(alpha = 0.6f))
                                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = count.toString(),
                                                color = if (isSelected) Color.White else KitsugiColors.TextSecondary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Dikey ayırıcı
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(KitsugiColors.Border)
                )

                // Sağ panel: akış listesi
                Box(modifier = Modifier.weight(0.6f)) {
                    when {
                        isLoading -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                KitsugiPlasmaLoader(size = 44.dp)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.inline_scanning_extensions_959d892),
                                    color = KitsugiColors.TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        errorMessage != null -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = errorMessage ?: "",
                                    color = KitsugiColors.AccentRed,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                        resolvingSource != null -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                KitsugiPlasmaLoader(size = 46.dp)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.link_cozumleniyor),
                                    color = KitsugiColors.TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.inline_connecting_through_realdebrid_d1107e6),
                                    color = KitsugiColors.TextSecondary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        else -> {
                            val addonNamesR = remember(streams) {
                                listOf(KitsugiStrings.get(R.string.notif_filter_all)) + streams.map { it.addonName }.distinct()
                            }
                            var selectedAddonR by remember { mutableStateOf("Tümü") }
                            val filteredStreamsR = remember(streams, selectedAddonR) {
                                if (selectedAddonR == "Tümü") streams else streams.filter { it.addonName == selectedAddonR }
                            }
                            val landscapeListState = rememberLazyListState()
                            LazyColumn(
                                state = landscapeListState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(filteredStreamsR) { stream ->
                                    StreamItemRow(
                                        stream = stream,
                                        onClick = { resolveAndSelectStream(stream) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Portrait: mevcut dikey düzen
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mediaTitle,
                            color = KitsugiColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.bolum_episodenumber_akis_secenekleri, episodeNumber),
                            color = KitsugiColors.TextSecondary,
                            fontSize = 14.sp
                        )
                    }

                    IconButton(
                        onClick = LocalDismissAnimated.current,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = KitsugiColors.SurfaceStrong
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Close,
                            contentDescription = stringResource(R.string.kapat),
                            tint = KitsugiColors.TextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        KitsugiPlasmaLoader(size = 50.dp)
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = stringResource(R.string.inline_scanning_extensions_and_searching__a6ec7db),
                            color = KitsugiColors.TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else if (errorMessage != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = KitsugiColors.AccentRed,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            lineHeight = 20.sp
                        )
                    }
                } else if (resolvingSource != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        KitsugiPlasmaLoader(size = 52.dp)
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = stringResource(R.string.link_cozumleniyor),
                            color = KitsugiColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.inline_getting_a_download_link_through_4599c4e),
                            color = KitsugiColors.TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = cancelResolving,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, KitsugiColors.SurfaceStrong),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = KitsugiColors.TextPrimary
                            )
                        ) {
                            Icon(Icons.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.iptal), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    val addonNames = remember(streams) {
                        listOf(KitsugiStrings.get(R.string.notif_filter_all)) + streams.map { it.addonName }.distinct()
                    }
                    var selectedAddon by remember { mutableStateOf("Tümü") }
                    val filteredStreams = remember(streams, selectedAddon) {
                        if (selectedAddon == "Tümü") streams else streams.filter { it.addonName == selectedAddon }
                    }

                    val tabScrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(tabScrollState)
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        addonNames.forEach { addon ->
                            val count = if (addon == "Tümü") streams.size else streams.count { it.addonName == addon }
                            val isSelected = selectedAddon == addon
                            val backgroundColor = if (isSelected) LocalKitsugiAccent.current else KitsugiColors.SurfaceStrong
                            val contentColor = if (isSelected) Color.White else KitsugiColors.TextPrimary

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(backgroundColor)
                                    .tvClickable(shape = RoundedCornerShape(12.dp), onClick = { selectedAddon = addon })
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = addon,
                                        color = contentColor,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (isSelected) Color.White.copy(alpha = 0.2f) else KitsugiColors.Surface.copy(alpha = 0.6f))
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = count.toString(),
                                            color = if (isSelected) Color.White else KitsugiColors.TextSecondary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        state = streamListState,
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredStreams) { stream ->
                            StreamItemRow(
                                stream = stream,
                                onClick = { resolveAndSelectStream(stream) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamItemRow(
    stream: StreamSource,
    onClick: () -> Unit
) {
    val parsedInfo = remember(stream.title) { parseStreamTitle(stream.title) }
    val accentColor = LocalKitsugiAccent.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .tvClickable(shape = RoundedCornerShape(16.dp), onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = KitsugiColors.SurfaceStrong.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Quality and Addon label
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Quality badge — yalnızca bilinen kalite gösterilir
                    if (parsedInfo.quality.isNotBlank()) {
                        val qualityColor = when (parsedInfo.quality) {
                            "4K", "2160p" -> KitsugiColors.AccentRed
                            "1080p" -> KitsugiColors.AccentBlue
                            "720p" -> KitsugiColors.AccentGreen
                            else -> KitsugiColors.SurfaceStrong
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(qualityColor.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = parsedInfo.quality,
                                color = if (qualityColor == KitsugiColors.SurfaceStrong) KitsugiColors.TextSecondary else qualityColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Addon Name Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(KitsugiColors.AccentPurple.copy(alpha = 0.1f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stream.addonName,
                            color = KitsugiColors.AccentPurple,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Cache Status Badge — yalnızca torrent/debrid akışları için anlamlı
                    if (stream.isTorrent) {
                        val cacheState = remember(stream) { getCacheState(stream) }
                        val (cacheText, cacheColor) = when (cacheState) {
                            DebridCacheState.CACHED -> "Önbellekte" to KitsugiColors.AccentGreen
                            DebridCacheState.NOT_CACHED -> "İndirilecek" to KitsugiColors.AccentOrange
                            DebridCacheState.P2P -> "Torrent (P2P)" to KitsugiColors.AccentBlue
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(cacheColor.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = cacheText,
                                color = cacheColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (parsedInfo.size.isNotBlank()) {
                        Text(
                            text = parsedInfo.size,
                            color = KitsugiColors.TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Title
                Text(
                    text = stream.title,
                    color = KitsugiColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Extra details like dual audio or seeders
                if (stream.name.isNotBlank()) {
                    Text(
                        text = stream.name.trim(),
                        color = KitsugiColors.TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Play Icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(accentColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                KitsugiArtIcon(art = KitsugiArt.Play, contentDescription = stringResource(R.string.oynat), size = 20.dp)
            }
        }
    }
}

private data class ParsedStreamInfo(
    val quality: String,
    val size: String
)

private fun parseStreamTitle(title: String): ParsedStreamInfo {
    // Kalite yalnızca başlıkta AÇIKÇA yazıyorsa gösterilir; aksi halde boş bırakılır
    // (eskiden varsayılan olarak "HD" yazılıyordu — bu yanlış bilgiydi).
    val quality = com.kitsugi.animelist.data.repository.StreamInfoResolver
        .parseHeightFromText(title)
        ?.let { com.kitsugi.animelist.data.repository.StreamInfoResolver.heightToLabel(it) }
        ?: ""

    // Size regex like "1.2 GB" or "750 MB" or "1.2gb"
    val sizeRegex = Regex("""(\d+(?:\.\d+)?\s*(?:gb|mb|gib|mib))""", RegexOption.IGNORE_CASE)
    val match = sizeRegex.find(title)
    val size = match?.value?.uppercase(Locale.ROOT) ?: ""

    return ParsedStreamInfo(quality, size)
}
