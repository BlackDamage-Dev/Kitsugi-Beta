package com.kitsugi.animelist.ui.screens.detail
import androidx.compose.ui.graphics.vector.ImageVector
import com.kitsugi.animelist.ui.components.KitsugiButton
import com.kitsugi.animelist.ui.components.PlatformErrorCodeChip
import com.kitsugi.animelist.ui.components.PlatformErrorExplanationBlock
import com.kitsugi.animelist.data.platform.PlatformErrorCode

import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.ui.unit.sp
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.ui.components.KitsugiIntegrationsSettingsDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

import androidx.compose.ui.unit.Dp
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogo
import com.kitsugi.animelist.ui.components.KitsugiPlatformLogos
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.utils.toFriendlySourceLabel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.ui.platform.LocalContext
import com.kitsugi.animelist.utils.copyToClipboard
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

/**
 * Shared pill/chip composable used across all detail pages within this package.
 * Consolidates the previously duplicated private definitions in StaffDetailComponents,
 * CharacterDetailComponents, KitsugiDetailHeroSection, and StudioDetailPage.
 */
@Composable
internal fun DetailPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = text,
                color = color,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black
            )
        }
    }
}

/**
 * Logo + isim içeren kaynak pill'i — detay sayfalarında kaynağı GÖRSEL olarak gösterir.
 * Platformun orijinal logosu ve dostça adı (örn. "Bangumi", "MyAnimeList", "Shikimori")
 * birlikte çizilir; böylece tüm kaynaklarda isim+logo tutarlı görünür.
 */
@Composable
internal fun DetailSourcePill(
    source: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val label = source.toFriendlySourceLabel()
    val hasLogo = KitsugiPlatformLogos.resFor(source) != null
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (hasLogo) {
                KitsugiPlatformLogo(platformId = source, size = 16.dp, cornerRadius = 3.dp)
            }
            Text(
                text = label,
                color = color,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black
            )
        }
    }
}

/**
 * Platform logo rozeti — Karakter, Seslendirmen ve Detay sayfalarında
 * kaynak platformun resmi orijinal logosunu gösterir.
 */
@Composable
internal fun DetailPlatformBadge(
    source: String,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp
) {
    val logoRes = KitsugiPlatformLogos.resFor(source)
    if (logoRes != null) {
        val badgeColor = when (source.lowercase().trim()) {
            "kitsu" -> Color(0xFFFD755C)
            "anilist" -> Color(0xFF02A9FF)
            "mal", "jikan" -> Color(0xFF2E51A2)
            "shikimori" -> Color(0xFF8E44AD)
            "bangumi", "bgm" -> Color(0xFFF09199)
            "simkl" -> Color(0xFFE21926)
            "tmdb" -> Color(0xFFFFB800)
            "animethemes", "animethemes.moe" -> Color(0xFF2EC4B6)
            else -> KitsugiColors.SurfaceSoft
        }
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(badgeColor.copy(alpha = 0.18f))
                .border(1.dp, badgeColor.copy(alpha = 0.40f), RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            KitsugiPlatformLogo(
                platformId = source,
                size = size,
                cornerRadius = 4.dp
            )
        }
    } else {
        val accentColor = LocalKitsugiAccent.current
        DetailPill(
            text = source.toFriendlySourceLabel().uppercase(),
            color = accentColor,
            modifier = modifier
        )
    }
}

/**
 * Minnak kopyala butonu — karakter/seslendirmen sayfasındaki "Diğer İsimler"
 * çiplerindeki küçük kopyala ikonunun aynısı. Ayrıntı sayfalarında kopyalanabilir
 * her metnin yanına iliştirilmek üzere paylaşılır.
 */
@Composable
internal fun KitsugiMiniCopyButton(
    text: String,
    modifier: Modifier = Modifier
) {
    if (text.isBlank()) return
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { copyToClipboard(context, text) },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.ContentCopy,
            contentDescription = stringResource(R.string.action_copy),
            tint = accentColor.copy(alpha = 0.85f),
            modifier = Modifier.size(13.dp)
        )
    }
}

/**
 * Shared info-row composable (label + value) used across detail pages.
 * Değerin yanında küçük bir kopyala butonu taşır.
 *
 * PERF: Satır başına SelectionContainer sarmalayıcısı kaldırıldı.
 * Her InfoRow için ayrı SelectionManager + 3 pointerInput hareket algılayıcısı
 * oluşturulması (sekme başına 15-20 satır = ~60 gesture node) kaydırma sırasında
 * dokunma olaylarını yavaşlatıyor ve üstte zaten SelectionContainer bulunan
 * karakter/ekip sekmelerinde iç içe seçim yöneticisi çakışmasına yol açıyordu.
 * Kopyalama işlevi satır sonundaki KitsugiMiniCopyButton ile tek dokunuşta sağlanır.
 */
@Composable
internal fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = KitsugiColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = value,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            KitsugiMiniCopyButton(text = value)
        }
    }
}

internal data class AiringInfo(val episode: Int?, val targetEpoch: Long?, val rawText: String?)

internal fun parseNextAiring(raw: String): AiringInfo {
    val parsed = com.kitsugi.animelist.utils.NextAiringFormat.parse(raw)
    return AiringInfo(parsed.episode, parsed.epoch, parsed.legacyText)
}

@Composable
internal fun rememberAiringCountdownText(nextAiring: String?): String {
    if (nextAiring.isNullOrBlank()) return ""
    val parsed = remember(nextAiring) { com.kitsugi.animelist.utils.NextAiringFormat.parse(nextAiring) }
    var displayText by remember(parsed) { mutableStateOf("") }

    if (parsed.isMachineFormat) {
        val targetEpoch = parsed.epoch!!

        LaunchedEffect(parsed) {
            while (true) {
                // Tüm kaynaklarda aynı tarihli biçim:
                // "Bölüm 2, 2026-10-15 tarihinde yayında (6 gün sonra)"
                displayText = com.kitsugi.animelist.utils.NextAiringFormat.detailText(parsed)
                val remaining = targetEpoch - System.currentTimeMillis() / 1000L
                if (remaining <= 0) break
                val delayTime = if (remaining >= 86400) 60000L else 10000L
                kotlinx.coroutines.delay(delayTime)
            }
        }
    } else {
        displayText = parsed.legacyText ?: ""
    }
    return displayText
}

@Composable
internal fun AiringCountdownCard(
    nextAiring: String,
    modifier: Modifier = Modifier
) {
    val displayText = rememberAiringCountdownText(nextAiring)

    if (displayText.isBlank()) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KitsugiColors.Surface)
            .border(1.dp, KitsugiColors.Accent.copy(0.15f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Icon Container
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(KitsugiColors.Accent.copy(0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Schedule,
                    contentDescription = null,
                    tint = KitsugiColors.Accent,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = stringResource(R.string.yaklasan_yayin),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = KitsugiColors.Accent
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = KitsugiColors.TextPrimary
                )
            }
        }
    }
}

@Composable
internal fun DataUnavailableScreen(
    title: String,
    onBackClick: () -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Kaynak hatasının `KTS-…` kodu; varsa neden ve çözüm açıklamasıyla birlikte gösterilir. */
    platformErrorCode: PlatformErrorCode? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KitsugiColors.Background)
            .padding(24.dp)
    ) {
        // Back Button
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
        ) {
            Icon(
                imageVector = Icons.ArrowBack,
                contentDescription = stringResource(R.string.geri),
                tint = KitsugiColors.TextPrimary
            )
        }

        // Center Warning Panel
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Styled Warning Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(KitsugiColors.AccentRed.copy(0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.SearchOff,
                    contentDescription = null,
                    tint = KitsugiColors.AccentRed,
                    modifier = Modifier.size(40.dp)
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = KitsugiColors.TextPrimary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Text(
                text = stringResource(R.string.inline_media_details_are_unavailable_righ_c0921d2),
                style = MaterialTheme.typography.bodyMedium,
                color = KitsugiColors.TextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            platformErrorCode?.let { code ->
                PlatformErrorExplanationBlock(
                    code = code,
                    tint = KitsugiColors.AccentRed,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Premium Kitsugi Button
            KitsugiButton(
                onClick = onRetryClick,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "Tekrar Dene",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** Sekme hata bloğunun altında kaynak hata kodu rozeti; kod yoksa hiçbir şey çizmez. */
@Composable
internal fun DetailTabErrorCodeChip(
    platformErrorCode: PlatformErrorCode?,
    modifier: Modifier = Modifier,
) {
    platformErrorCode?.let { code ->
        PlatformErrorCodeChip(code = code, modifier = modifier)
    }
}

sealed class SynopsisState {
    data object Loading : SynopsisState()
    data object Error : SynopsisState()
    data class Success(val text: String) : SynopsisState()
}

internal data class DetailSettingsSlice(
    val blurAdultMedia: Boolean = false,
    val preferredTranslator: String = "DEFAULT",
    val mdbListShowImdb: Boolean = true,
    val mdbListShowTomatoes: Boolean = true,
    val mdbListShowMetacritic: Boolean = true,
    val mdbListShowAudience: Boolean = false,
    val mdbListShowLetterboxd: Boolean = false,
    val mdbListShowTmdb: Boolean = false,
    val mdbListShowTrakt: Boolean = false
)

/**
 * Detay sayfalarının ihtiyaç duyduğu dar ayar dilimini toplar.
 * 100+ alanlı AppSettings akışının tamamını kök detay sayfasında dinleyip alakasız
 * ayar yazımlarında tüm sayfayı yeniden compose etmeyi önler.
 */
@Composable
internal fun rememberDetailSettingsSlice(
    settingsDataStore: SettingsDataStore?
): DetailSettingsSlice? {
    if (settingsDataStore == null) return null
    val sliceFlow = remember(settingsDataStore) {
        settingsDataStore.settingsFlow
            .map { s ->
                DetailSettingsSlice(
                    blurAdultMedia = s.blurAdultMedia,
                    preferredTranslator = s.preferredTranslator,
                    mdbListShowImdb = s.mdbListShowImdb,
                    mdbListShowTomatoes = s.mdbListShowTomatoes,
                    mdbListShowMetacritic = s.mdbListShowMetacritic,
                    mdbListShowAudience = s.mdbListShowAudience,
                    mdbListShowLetterboxd = s.mdbListShowLetterboxd,
                    mdbListShowTmdb = s.mdbListShowTmdb,
                    mdbListShowTrakt = s.mdbListShowTrakt
                )
            }
            .distinctUntilChanged()
    }
    return sliceFlow.collectAsState(initial = DetailSettingsSlice()).value
}

@Composable
fun DetailIntegrationsSettingsDialog(
    settingsDataStore: SettingsDataStore,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit
) {
    val settingsState by settingsDataStore.settingsFlow.collectAsState(initial = null)
    val resolved = settingsState ?: return
    DetailIntegrationsSettingsDialog(
        settingsDataStore = settingsDataStore,
        settingsState = resolved,
        coroutineScope = coroutineScope,
        onDismiss = onDismiss
    )
}

@Composable
fun DetailIntegrationsSettingsDialog(
    settingsDataStore: SettingsDataStore,
    settingsState: AppSettings,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit
) {
    KitsugiIntegrationsSettingsDialog(
        tmdbEnabled = settingsState.tmdbEnabled,
        onTmdbEnabledChanged = { coroutineScope.launch { settingsDataStore.setTmdbEnabled(it) } },
        tmdbApiKey = settingsState.tmdbUserApiKey,
        onTmdbApiKeyChanged = { coroutineScope.launch { settingsDataStore.setTmdbUserApiKey(it) } },
        tmdbModernHomeEnabled = settingsState.tmdbModernHomeEnabled,
        onTmdbModernHomeEnabledChanged = { coroutineScope.launch { settingsDataStore.setTmdbModernHomeEnabled(it) } },
        tmdbEnrichContinueWatching = settingsState.tmdbEnrichContinueWatching,
        onTmdbEnrichContinueWatchingChanged = { coroutineScope.launch { settingsDataStore.setTmdbEnrichContinueWatching(it) } },
        tmdbLanguage = settingsState.tmdbLanguage,
        onTmdbLanguageChanged = { coroutineScope.launch { settingsDataStore.setTmdbLanguage(it) } },
        tmdbUseArtwork = settingsState.tmdbUseArtwork,
        onTmdbUseArtworkChanged = { coroutineScope.launch { settingsDataStore.setTmdbUseArtwork(it) } },
        tmdbUseBasicInfo = settingsState.tmdbUseBasicInfo,
        onTmdbUseBasicInfoChanged = { coroutineScope.launch { settingsDataStore.setTmdbUseBasicInfo(it) } },
        tmdbUseDetails = settingsState.tmdbUseDetails,
        onTmdbUseDetailsChanged = { coroutineScope.launch { settingsDataStore.setTmdbUseDetails(it) } },
        tmdbUseReleaseDates = settingsState.tmdbUseReleaseDates,
        onTmdbUseReleaseDatesChanged = { coroutineScope.launch { settingsDataStore.setTmdbUseReleaseDates(it) } },
        tmdbUseCredits = settingsState.tmdbUseCredits,
        onTmdbUseCreditsChanged = { coroutineScope.launch { settingsDataStore.setTmdbUseCredits(it) } },
        tmdbUseProductions = settingsState.tmdbUseProductions,
        onTmdbUseProductionsChanged = { coroutineScope.launch { settingsDataStore.setTmdbUseProductions(it) } },
        tmdbUseNetworks = settingsState.tmdbUseNetworks,
        onTmdbUseNetworksChanged = { coroutineScope.launch { settingsDataStore.setTmdbUseNetworks(it) } },
        tmdbUseEpisodes = settingsState.tmdbUseEpisodes,
        onTmdbUseEpisodesChanged = { coroutineScope.launch { settingsDataStore.setTmdbUseEpisodes(it) } },
        tmdbUseTrailers = settingsState.tmdbUseTrailers,
        onTmdbUseTrailersChanged = { coroutineScope.launch { settingsDataStore.setTmdbUseTrailers(it) } },
        tmdbUseMoreLikeThis = settingsState.tmdbUseMoreLikeThis,
        onTmdbUseMoreLikeThisChanged = { coroutineScope.launch { settingsDataStore.setTmdbUseMoreLikeThis(it) } },
        tmdbUseCollections = settingsState.tmdbUseCollections,
        onTmdbUseCollectionsChanged = { coroutineScope.launch { settingsDataStore.setTmdbUseCollections(it) } },
        
        mdbListEnabled = settingsState.mdbListEnabled,
        onMdbListEnabledChanged = { coroutineScope.launch { settingsDataStore.setMdbListEnabled(it) } },
        mdbListApiKey = settingsState.mdbListApiKey,
        onMdbListApiKeyChanged = { coroutineScope.launch { settingsDataStore.setMdbListApiKey(it) } },
        mdbListShowImdb = settingsState.mdbListShowImdb,
        onMdbListShowImdbChanged = { coroutineScope.launch { settingsDataStore.setMdbListShowImdb(it) } },
        mdbListShowTomatoes = settingsState.mdbListShowTomatoes,
        onMdbListShowTomatoesChanged = { coroutineScope.launch { settingsDataStore.setMdbListShowTomatoes(it) } },
        mdbListShowMetacritic = settingsState.mdbListShowMetacritic,
        onMdbListShowMetacriticChanged = { coroutineScope.launch { settingsDataStore.setMdbListShowMetacritic(it) } },
        mdbListShowAudience = settingsState.mdbListShowAudience,
        onMdbListShowAudienceChanged = { coroutineScope.launch { settingsDataStore.setMdbListShowAudience(it) } },
        mdbListShowLetterboxd = settingsState.mdbListShowLetterboxd,
        onMdbListShowLetterboxdChanged = { coroutineScope.launch { settingsDataStore.setMdbListShowLetterboxd(it) } },
        mdbListShowTmdb = settingsState.mdbListShowTmdb,
        onMdbListShowTmdbChanged = { coroutineScope.launch { settingsDataStore.setMdbListShowTmdb(it) } },
        mdbListShowTrakt = settingsState.mdbListShowTrakt,
        onMdbListShowTraktChanged = { coroutineScope.launch { settingsDataStore.setMdbListShowTrakt(it) } },
        
        aniSkipEnabled = settingsState.aniSkipEnabled,
        onAniSkipEnabledChanged = { coroutineScope.launch { settingsDataStore.setAniSkipEnabled(it) } },
        aniSkipAutoSkip = settingsState.aniSkipAutoSkip,
        onAniSkipAutoSkipChanged = { coroutineScope.launch { settingsDataStore.setAniSkipAutoSkip(it) } },
        animeSkipClientId = settingsState.animeSkipClientId,
        onAnimeSkipClientIdChanged = { coroutineScope.launch { settingsDataStore.setAnimeSkipClientId(it) } },
        fanartTvEnabled = settingsState.fanartTvEnabled,
        onFanartTvEnabledChanged = { coroutineScope.launch { settingsDataStore.setFanartTvEnabled(it) } },
        fanartTvApiKey = settingsState.fanartTvApiKey,
        onFanartTvApiKeyChanged = { coroutineScope.launch { settingsDataStore.setFanartTvApiKey(it) } },
        musicEnabled = settingsState.musicEnabled,
        onMusicEnabledChanged = { coroutineScope.launch { settingsDataStore.setMusicEnabled(it) } },
        musicAutoRecommend = settingsState.musicAutoRecommend,
        onMusicAutoRecommendChanged = { coroutineScope.launch { settingsDataStore.setMusicAutoRecommend(it) } },
        musicAutoPlay = settingsState.musicAutoPlay,
        onMusicAutoPlayChanged = { coroutineScope.launch { settingsDataStore.setMusicAutoPlay(it) } },
        musicAutoPlayDelay = settingsState.musicAutoPlayDelaySeconds,
        onAutoPlayDelayChanged = { coroutineScope.launch { settingsDataStore.setMusicAutoPlayDelay(it) } },
        musicContinueOnExit = settingsState.musicContinueOnExit,
        onMusicContinueOnExitChanged = { coroutineScope.launch { settingsDataStore.setMusicContinueOnExit(it) } },
        musicMasterVolume = settingsState.musicMasterVolume,
        onMusicMasterVolumeChanged = { coroutineScope.launch { settingsDataStore.setMusicMasterVolume(it) } },
        musicChannelVolume = settingsState.musicChannelVolume,
        onMusicChannelVolumeChanged = { coroutineScope.launch { settingsDataStore.setMusicChannelVolume(it) } },
        musicAmbientVolume = settingsState.musicAmbientVolume,
        onMusicAmbientVolumeChanged = { coroutineScope.launch { settingsDataStore.setMusicAmbientVolume(it) } },
        musicJamendoEnabled = settingsState.musicJamendoEnabled,
        onMusicJamendoEnabledChanged = { coroutineScope.launch { settingsDataStore.setMusicJamendoEnabled(it) } },
        musicJamendoClientId = settingsState.musicJamendoClientId,
        onMusicJamendoClientIdChanged = { coroutineScope.launch { settingsDataStore.setMusicJamendoClientId(it) } },
        musicFreesoundEnabled = settingsState.musicFreesoundEnabled,
        onMusicFreesoundEnabledChanged = { coroutineScope.launch { settingsDataStore.setMusicFreesoundEnabled(it) } },
        musicFreesoundApiKey = settingsState.musicFreesoundApiKey,
        onMusicFreesoundApiKeyChanged = { coroutineScope.launch { settingsDataStore.setMusicFreesoundApiKey(it) } },
        musicOpenverseEnabled = settingsState.musicOpenverseEnabled,
        onMusicOpenverseEnabledChanged = { coroutineScope.launch { settingsDataStore.setMusicOpenverseEnabled(it) } },
        musicOpenverseToken = settingsState.musicOpenverseToken,
        onMusicOpenverseTokenChanged = { coroutineScope.launch { settingsDataStore.setMusicOpenverseToken(it) } },
        musicArchiveEnabled = settingsState.musicArchiveEnabled,
        onMusicArchiveEnabledChanged = { coroutineScope.launch { settingsDataStore.setMusicArchiveEnabled(it) } },
        musicAudiusEnabled = settingsState.musicAudiusEnabled,
        onMusicAudiusEnabledChanged = { coroutineScope.launch { settingsDataStore.setMusicAudiusEnabled(it) } },
        musicAudiusApiKey = settingsState.musicAudiusApiKey,
        onMusicAudiusApiKeyChanged = { coroutineScope.launch { settingsDataStore.setMusicAudiusApiKey(it) } },
        onDismiss = onDismiss
    )
}

/**
 * "Bilgiler" kartı satırı (etiket + değer). Tüm ayrıntı sayfalarında (kütüphane girdisi
 * ve API sonucu) ortak kullanılır.
 *
 * - Normal satırlarda değerin yanında kopyala butonu bulunur.
 * - [names] doluysa (ör. "Diğer Adlar") her isim kendi kopyala butonuyla ayrı ayrı gösterilir;
 *   tek bir butonla hepsini birden kopyalamaz.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailInfoValueRow(
    label: String,
    value: String,
    names: List<String> = emptyList(),
    onValueClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = KitsugiColors.TextMuted,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.4f).padding(top = 6.dp)
        )
        if (names.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.weight(0.6f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                names.forEach { name ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(KitsugiColors.Background.copy(alpha = 0.55f))
                            .padding(start = 10.dp, top = 2.dp, bottom = 2.dp, end = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = name,
                            color = KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        KitsugiMiniCopyButton(text = name)
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.weight(0.6f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val valueColor = if (onValueClick != null) LocalKitsugiAccent.current else KitsugiColors.TextPrimary
                val valueModifier = Modifier
                    .weight(1f, fill = false)
                    .let {
                        if (onValueClick != null) {
                            it.clip(RoundedCornerShape(4.dp))
                                .tvClickable(shape = RoundedCornerShape(4.dp), onClick = onValueClick)
                        } else {
                            it
                        }
                    }
                Text(
                    text = value,
                    color = valueColor,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (onValueClick != null) FontWeight.Bold else FontWeight.Normal,
                    modifier = valueModifier
                )
                KitsugiMiniCopyButton(text = value)
            }
        }
    }
}

/** Ayrıntı modelindeki eş anlamlı adları temizler (boş/"null"/tekrar edenleri atar). */
internal fun cleanDetailSynonyms(synonyms: List<String>): List<String> =
    synonyms.map { it.trim() }.filter { it.isNotBlank() && it != "null" }.distinct()

internal const val DETAIL_TAB_PAGE_CHUNK_SIZE = 40

/**
 * HorizontalPager (Constraints.Infinity) içindeki çok büyük sekme listelerini (ör. 100-600
 * karakter, görünüm, seslendirme rolü veya ilişki) tek seferde compose etmekten koruyan
 * hafif aralık seçici şerit ("1 - 40", "41 - 80", ...). +0 XML ID kuralına tam uyumludur.
 */
@Composable
internal fun DetailChunkSelectorRow(
    totalCount: Int,
    chunkSize: Int = DETAIL_TAB_PAGE_CHUNK_SIZE,
    selectedChunk: Int,
    onSelectChunk: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (totalCount <= chunkSize) return
    val chunkCount = (totalCount + chunkSize - 1) / chunkSize
    val accentColor = LocalKitsugiAccent.current
    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(
            count = chunkCount,
            key = { chunkIdx -> "chunk_$chunkIdx" }
        ) { chunkIdx ->
            val start = chunkIdx * chunkSize + 1
            val end = minOf((chunkIdx + 1) * chunkSize, totalCount)
            val isSelected = chunkIdx == selectedChunk
            val bgColor = if (isSelected) accentColor else KitsugiColors.SurfaceSoft.copy(alpha = 0.65f)
            val textColor = if (isSelected) KitsugiColors.Background else KitsugiColors.TextSecondary
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(bgColor)
                    .tvClickable(shape = RoundedCornerShape(999.dp)) { onSelectChunk(chunkIdx) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$start - $end",
                    color = textColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold
                )
            }
        }
    }
}

