package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.LinearProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Text

import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.utils.kitsugiVerticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.data.remote.MdbListPlan
import com.kitsugi.animelist.data.remote.MdbListQuotaCenter
import kotlinx.coroutines.launch

data class TmdbLanguage(val code: String, val displayName: String)

val AVAILABLE_TMDB_LANGUAGES = listOf(
    TmdbLanguage("en", "English"),
    TmdbLanguage("tr", "Türkçe"),
    TmdbLanguage("ja", "日本語"),
    TmdbLanguage("fr", "Français"),
    TmdbLanguage("de", "Deutsch"),
    TmdbLanguage("es", "Español"),
    TmdbLanguage("it", "Italiano"),
    TmdbLanguage("ru", "Русский"),
    TmdbLanguage("zh", "中文")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitsugiIntegrationsSettingsDialog(
    tmdbEnabled: Boolean,
    onTmdbEnabledChanged: (Boolean) -> Unit,
    tmdbApiKey: String,
    onTmdbApiKeyChanged: (String) -> Unit,
    tmdbModernHomeEnabled: Boolean,
    onTmdbModernHomeEnabledChanged: (Boolean) -> Unit,
    tmdbEnrichContinueWatching: Boolean,
    onTmdbEnrichContinueWatchingChanged: (Boolean) -> Unit,
    tmdbLanguage: String,
    onTmdbLanguageChanged: (String) -> Unit,
    tmdbUseArtwork: Boolean,
    onTmdbUseArtworkChanged: (Boolean) -> Unit,
    tmdbUseBasicInfo: Boolean,
    onTmdbUseBasicInfoChanged: (Boolean) -> Unit,
    tmdbUseDetails: Boolean,
    onTmdbUseDetailsChanged: (Boolean) -> Unit,
    tmdbUseReleaseDates: Boolean,
    onTmdbUseReleaseDatesChanged: (Boolean) -> Unit,
    tmdbUseCredits: Boolean,
    onTmdbUseCreditsChanged: (Boolean) -> Unit,
    tmdbUseProductions: Boolean,
    onTmdbUseProductionsChanged: (Boolean) -> Unit,
    tmdbUseNetworks: Boolean,
    onTmdbUseNetworksChanged: (Boolean) -> Unit,
    tmdbUseEpisodes: Boolean,
    onTmdbUseEpisodesChanged: (Boolean) -> Unit,
    tmdbUseTrailers: Boolean,
    onTmdbUseTrailersChanged: (Boolean) -> Unit,
    tmdbUseMoreLikeThis: Boolean,
    onTmdbUseMoreLikeThisChanged: (Boolean) -> Unit,
    tmdbUseCollections: Boolean,
    onTmdbUseCollectionsChanged: (Boolean) -> Unit,
    mdbListEnabled: Boolean,
    onMdbListEnabledChanged: (Boolean) -> Unit,
    mdbListApiKey: String,
    onMdbListApiKeyChanged: (String) -> Unit,
    mdbListShowImdb: Boolean,
    onMdbListShowImdbChanged: (Boolean) -> Unit,
    mdbListShowTomatoes: Boolean,
    onMdbListShowTomatoesChanged: (Boolean) -> Unit,
    mdbListShowMetacritic: Boolean,
    onMdbListShowMetacriticChanged: (Boolean) -> Unit,
    mdbListShowAudience: Boolean,
    onMdbListShowAudienceChanged: (Boolean) -> Unit,
    mdbListShowLetterboxd: Boolean,
    onMdbListShowLetterboxdChanged: (Boolean) -> Unit,
    mdbListShowTmdb: Boolean,
    onMdbListShowTmdbChanged: (Boolean) -> Unit,
    mdbListShowTrakt: Boolean,
    onMdbListShowTraktChanged: (Boolean) -> Unit,
    aniSkipEnabled: Boolean,
    onAniSkipEnabledChanged: (Boolean) -> Unit,
    aniSkipAutoSkip: Boolean,
    onAniSkipAutoSkipChanged: (Boolean) -> Unit,
    animeSkipClientId: String,
    onAnimeSkipClientIdChanged: (String) -> Unit,
    fanartTvEnabled: Boolean,
    onFanartTvEnabledChanged: (Boolean) -> Unit,
    fanartTvApiKey: String,
    onFanartTvApiKeyChanged: (String) -> Unit,
    // ─── Okuyucu Ortam Sesi (Ambient) ───────────────────────────────────────
    musicEnabled: Boolean = true,
    onMusicEnabledChanged: (Boolean) -> Unit = {},
    musicAutoRecommend: Boolean = true,
    onMusicAutoRecommendChanged: (Boolean) -> Unit = {},
    musicAutoPlay: Boolean = false,
    onMusicAutoPlayChanged: (Boolean) -> Unit = {},
    musicAutoPlayDelay: Int = 3,
    onAutoPlayDelayChanged: (Int) -> Unit = {},
    musicContinueOnExit: Boolean = false,
    onMusicContinueOnExitChanged: (Boolean) -> Unit = {},
    musicMasterVolume: Float = 0.8f,
    onMusicMasterVolumeChanged: (Float) -> Unit = {},
    musicChannelVolume: Float = 0.5f,
    onMusicChannelVolumeChanged: (Float) -> Unit = {},
    musicAmbientVolume: Float = 0.6f,
    onMusicAmbientVolumeChanged: (Float) -> Unit = {},
    musicJamendoEnabled: Boolean = false,
    onMusicJamendoEnabledChanged: (Boolean) -> Unit = {},
    musicJamendoClientId: String = "",
    onMusicJamendoClientIdChanged: (String) -> Unit = {},
    musicFreesoundEnabled: Boolean = false,
    onMusicFreesoundEnabledChanged: (Boolean) -> Unit = {},
    musicFreesoundApiKey: String = "",
    onMusicFreesoundApiKeyChanged: (String) -> Unit = {},
    musicOpenverseEnabled: Boolean = true,
    onMusicOpenverseEnabledChanged: (Boolean) -> Unit = {},
    musicOpenverseToken: String = "",
    onMusicOpenverseTokenChanged: (String) -> Unit = {},
    musicArchiveEnabled: Boolean = true,
    onMusicArchiveEnabledChanged: (Boolean) -> Unit = {},
    musicAudiusEnabled: Boolean = false,
    onMusicAudiusEnabledChanged: (Boolean) -> Unit = {},
    musicAudiusApiKey: String = "",
    onMusicAudiusApiKeyChanged: (String) -> Unit = {},
    embeddedMode: Boolean = false,
    initialPage: Int = 0,
    onDismiss: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, 4), pageCount = { 5 })
    val scope = rememberCoroutineScope()
    LaunchedEffect(initialPage) {
        val targetPage = initialPage.coerceIn(0, 4)
        if (pagerState.currentPage != targetPage) pagerState.animateScrollToPage(targetPage)
    }

    val tmdbScrollState = rememberScrollState()
    val mdbListScrollState = rememberScrollState()
    val aniSkipScrollState = rememberScrollState()
    val fanartTvScrollState = rememberScrollState()
    val musicScrollState = rememberScrollState()

    val activeScrollState = when (pagerState.currentPage) {
        0 -> tmdbScrollState
        1 -> mdbListScrollState
        2 -> aniSkipScrollState
        3 -> fanartTvScrollState
        else -> musicScrollState
    }

    KitsugiSheetOrDialog(
        onDismiss = onDismiss,
        fullScreen = true,
        embeddedMode = embeddedMode,
        innerColumnScrollState = activeScrollState,
        sheetGesturesEnabled = false
    ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = if (embeddedMode) 4.dp else 8.dp)
            ) {
                if (!embeddedMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.integrations_title),
                            color = KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Close, contentDescription = stringResource(R.string.action_close), tint = KitsugiColors.TextSecondary)
                        }
                    }
                }
                KitsugiSettingsSegmentedTabs(
                    tabs = listOf(
                        KitsugiSettingsTabOption(stringResource(R.string.integrations_tab_tmdb), Icons.Movie),
                        KitsugiSettingsTabOption(stringResource(R.string.integrations_tab_mdblist), Icons.Star),
                        KitsugiSettingsTabOption(stringResource(R.string.integrations_tab_skip), Icons.SkipNext),
                        KitsugiSettingsTabOption("Fanart.tv", Icons.Image),
                        KitsugiSettingsTabOption(stringResource(R.string.integrations_tab_music), Icons.MusicNote)
                    ),
                    selectedIndex = pagerState.currentPage,
                    accentColor = accentColor,
                    onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } }
                )
            }

            // Body
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) { page ->
                when (page) {
                    0 -> TmdbSettingsTab(
                        enabled = tmdbEnabled,
                        onEnabledChanged = onTmdbEnabledChanged,
                        apiKey = tmdbApiKey,
                        onApiKeyChanged = onTmdbApiKeyChanged,
                        modernHomeEnabled = tmdbModernHomeEnabled,
                        onModernHomeEnabledChanged = onTmdbModernHomeEnabledChanged,
                        enrichContinueWatching = tmdbEnrichContinueWatching,
                        onEnrichContinueWatchingChanged = onTmdbEnrichContinueWatchingChanged,
                        language = tmdbLanguage,
                        onLanguageChanged = onTmdbLanguageChanged,
                        useArtwork = tmdbUseArtwork,
                        onUseArtworkChanged = onTmdbUseArtworkChanged,
                        useBasicInfo = tmdbUseBasicInfo,
                        onUseBasicInfoChanged = onTmdbUseBasicInfoChanged,
                        useDetails = tmdbUseDetails,
                        onUseDetailsChanged = onTmdbUseDetailsChanged,
                        useReleaseDates = tmdbUseReleaseDates,
                        onUseReleaseDatesChanged = onTmdbUseReleaseDatesChanged,
                        useCredits = tmdbUseCredits,
                        onUseCreditsChanged = onTmdbUseCreditsChanged,
                        useProductions = tmdbUseProductions,
                        onUseProductionsChanged = onTmdbUseProductionsChanged,
                        useNetworks = tmdbUseNetworks,
                        onUseNetworksChanged = onTmdbUseNetworksChanged,
                        useEpisodes = tmdbUseEpisodes,
                        onUseEpisodesChanged = onTmdbUseEpisodesChanged,
                        useTrailers = tmdbUseTrailers,
                        onUseTrailersChanged = onTmdbUseTrailersChanged,
                        useMoreLikeThis = tmdbUseMoreLikeThis,
                        onUseMoreLikeThisChanged = onTmdbUseMoreLikeThisChanged,
                        useCollections = tmdbUseCollections,
                        onUseCollectionsChanged = onTmdbUseCollectionsChanged,
                        accentColor = accentColor,
                        scrollState = tmdbScrollState
                    )
                    1 -> MdbListSettingsTab(
                        enabled = mdbListEnabled,
                        onEnabledChanged = onMdbListEnabledChanged,
                        apiKey = mdbListApiKey,
                        onApiKeyChanged = onMdbListApiKeyChanged,
                        showImdb = mdbListShowImdb,
                        onShowImdbChanged = onMdbListShowImdbChanged,
                        showTomatoes = mdbListShowTomatoes,
                        onShowTomatoesChanged = onMdbListShowTomatoesChanged,
                        showMetacritic = mdbListShowMetacritic,
                        onShowMetacriticChanged = onMdbListShowMetacriticChanged,
                        showAudience = mdbListShowAudience,
                        onShowAudienceChanged = onMdbListShowAudienceChanged,
                        showLetterboxd = mdbListShowLetterboxd,
                        onShowLetterboxdChanged = onMdbListShowLetterboxdChanged,
                        showTmdb = mdbListShowTmdb,
                        onShowTmdbChanged = onMdbListShowTmdbChanged,
                        showTrakt = mdbListShowTrakt,
                        onShowTraktChanged = onMdbListShowTraktChanged,
                        accentColor = accentColor,
                        scrollState = mdbListScrollState
                    )
                    2 -> AniSkipSettingsTab(
                        enabled = aniSkipEnabled,
                        onEnabledChanged = onAniSkipEnabledChanged,
                        autoSkip = aniSkipAutoSkip,
                        onAutoSkipChanged = onAniSkipAutoSkipChanged,
                        animeSkipClientId = animeSkipClientId,
                        onAnimeSkipClientIdChanged = onAnimeSkipClientIdChanged,
                        accentColor = accentColor,
                        scrollState = aniSkipScrollState
                    )
                    3 -> FanartTvSettingsTab(
                        enabled = fanartTvEnabled,
                        onEnabledChanged = onFanartTvEnabledChanged,
                        apiKey = fanartTvApiKey,
                        onApiKeyChanged = onFanartTvApiKeyChanged,
                        accentColor = accentColor,
                        scrollState = fanartTvScrollState
                    )
                    4 -> MusicSettingsTab(
                        enabled = musicEnabled,
                        onEnabledChanged = onMusicEnabledChanged,
                        autoRecommend = musicAutoRecommend,
                        onAutoRecommendChanged = onMusicAutoRecommendChanged,
                        autoPlay = musicAutoPlay,
                        onAutoPlayChanged = onMusicAutoPlayChanged,
                        autoPlayDelay = musicAutoPlayDelay,
                        onAutoPlayDelayChanged = onAutoPlayDelayChanged,
                        continueOnExit = musicContinueOnExit,
                        onContinueOnExitChanged = onMusicContinueOnExitChanged,
                        masterVolume = musicMasterVolume,
                        onMasterVolumeChanged = onMusicMasterVolumeChanged,
                        channelVolume = musicChannelVolume,
                        onChannelVolumeChanged = onMusicChannelVolumeChanged,
                        ambientVolume = musicAmbientVolume,
                        onAmbientVolumeChanged = onMusicAmbientVolumeChanged,
                        jamendoEnabled = musicJamendoEnabled,
                        onJamendoEnabledChanged = onMusicJamendoEnabledChanged,
                        jamendoClientId = musicJamendoClientId,
                        onJamendoClientIdChanged = onMusicJamendoClientIdChanged,
                        freesoundEnabled = musicFreesoundEnabled,
                        onFreesoundEnabledChanged = onMusicFreesoundEnabledChanged,
                        freesoundApiKey = musicFreesoundApiKey,
                        onFreesoundApiKeyChanged = onMusicFreesoundApiKeyChanged,
                        openverseEnabled = musicOpenverseEnabled,
                        onOpenverseEnabledChanged = onMusicOpenverseEnabledChanged,
                        openverseToken = musicOpenverseToken,
                        onOpenverseTokenChanged = onMusicOpenverseTokenChanged,
                        archiveEnabled = musicArchiveEnabled,
                        onArchiveEnabledChanged = onMusicArchiveEnabledChanged,
                        audiusEnabled = musicAudiusEnabled,
                        onAudiusEnabledChanged = onMusicAudiusEnabledChanged,
                        audiusApiKey = musicAudiusApiKey,
                        onAudiusApiKeyChanged = onMusicAudiusApiKeyChanged,
                        accentColor = accentColor,
                        scrollState = musicScrollState
                    )
                }
            }

            // Dialog mode owns its own dismissal control; embedded settings pages use the shared back bar.
            if (!embeddedMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_ok), color = accentColor, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TmdbSettingsTab(
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    apiKey: String,
    onApiKeyChanged: (String) -> Unit,
    modernHomeEnabled: Boolean,
    onModernHomeEnabledChanged: (Boolean) -> Unit,
    enrichContinueWatching: Boolean,
    onEnrichContinueWatchingChanged: (Boolean) -> Unit,
    language: String,
    onLanguageChanged: (String) -> Unit,
    useArtwork: Boolean,
    onUseArtworkChanged: (Boolean) -> Unit,
    useBasicInfo: Boolean,
    onUseBasicInfoChanged: (Boolean) -> Unit,
    useDetails: Boolean,
    onUseDetailsChanged: (Boolean) -> Unit,
    useReleaseDates: Boolean,
    onUseReleaseDatesChanged: (Boolean) -> Unit,
    useCredits: Boolean,
    onUseCreditsChanged: (Boolean) -> Unit,
    useProductions: Boolean,
    onUseProductionsChanged: (Boolean) -> Unit,
    useNetworks: Boolean,
    onUseNetworksChanged: (Boolean) -> Unit,
    useEpisodes: Boolean,
    onUseEpisodesChanged: (Boolean) -> Unit,
    useTrailers: Boolean,
    onUseTrailersChanged: (Boolean) -> Unit,
    useMoreLikeThis: Boolean,
    onUseMoreLikeThisChanged: (Boolean) -> Unit,
    useCollections: Boolean,
    onUseCollectionsChanged: (Boolean) -> Unit,
    accentColor: Color,
    scrollState: ScrollState = rememberScrollState()
) {
    var tempKey by remember(apiKey) { mutableStateOf(apiKey) }
    var keyVisible by remember { mutableStateOf(false) }
    var keyValidating by remember { mutableStateOf(false) }
    var keyValidationPassed by remember(apiKey) { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
    var showLanguageDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .kitsugiVerticalScroll(scrollState)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        KitsugiSettingsSection(title = stringResource(R.string.tmdb_title)) {
            KitsugiSettingsSwitchItem(
                title = stringResource(R.string.etkinlestir),
                description = stringResource(R.string.tmdb_desc),
                icon = Icons.Movie,
                iconColor = accentColor,
                checked = enabled,
                onCheckedChange = onEnabledChanged
            )

            if (enabled) {
                KitsugiSettingsDivider()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    OutlinedTextField(
                        value = tempKey,
                        onValueChange = {
                            tempKey = it
                            keyValidationPassed = null
                        },
                        label = { Text(stringResource(R.string.tmdb_api_key)) },
                        placeholder = { Text(stringResource(R.string.tmdb_api_key_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !keyValidating,
                        singleLine = true,
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { keyVisible = !keyVisible }) {
                                    Icon(
                                        imageVector = if (keyVisible) Icons.VisibilityOff else Icons.Visibility,
                                        contentDescription = stringResource(
                                            if (keyVisible) R.string.integration_hide_key else R.string.integration_show_key
                                        ),
                                        tint = KitsugiColors.TextSecondary
                                    )
                                }
                                if (tempKey.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            tempKey = ""
                                            keyValidationPassed = null
                                            onApiKeyChanged("")
                                        },
                                        enabled = !keyValidating
                                    ) {
                                        Icon(
                                            imageVector = Icons.Close,
                                            contentDescription = stringResource(R.string.action_clear),
                                            tint = KitsugiColors.TextSecondary
                                        )
                                    }
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = KitsugiColors.Border,
                            cursorColor = accentColor,
                            focusedTextColor = KitsugiColors.TextPrimary,
                            unfocusedTextColor = KitsugiColors.TextPrimary,
                            focusedLabelColor = accentColor,
                            unfocusedLabelColor = KitsugiColors.TextSecondary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Text(
                        text = stringResource(R.string.integration_shared_tmdb_key_info),
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    OutlinedButton(
                        onClick = {
                            keyValidating = true
                            keyValidationPassed = null
                            scope.launch {
                                val valid = runCatching {
                                    com.kitsugi.animelist.data.remote.TmdbApiClient.validateApiKey(tempKey)
                                }.getOrDefault(false)
                                if (valid) onApiKeyChanged(tempKey.trim())
                                keyValidationPassed = valid
                                keyValidating = false
                            }
                        },
                        enabled = !keyValidating,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor)
                    ) {
                        if (keyValidating) {
                            KitsugiPlasmaLoader(size = 18.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.action_validate_save))
                    }
                    keyValidationPassed?.let { passed ->
                        Text(
                            text = stringResource(
                                if (passed) R.string.integration_key_validated else R.string.integration_key_failed
                            ),
                            color = if (passed) Color(0xFF76D49B) else KitsugiColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.tmdb_modern_home),
                    description = stringResource(R.string.tmdb_modern_home_desc),
                    icon = Icons.Movie,
                    iconColor = accentColor,
                    checked = modernHomeEnabled,
                    onCheckedChange = onModernHomeEnabledChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.tmdb_enrich_continue_watching),
                    description = stringResource(R.string.tmdb_enrich_continue_watching_desc),
                    icon = Icons.Movie,
                    iconColor = accentColor,
                    checked = enrichContinueWatching,
                    onCheckedChange = onEnrichContinueWatchingChanged
                )

                KitsugiSettingsDivider()

                val currentLangName = AVAILABLE_TMDB_LANGUAGES.find { it.code == language }?.displayName ?: language.uppercase()
                KitsugiSettingsListItem(
                    title = stringResource(R.string.tmdb_language),
                    description = stringResource(R.string.tmdb_language_desc),
                    value = currentLangName,
                    icon = Icons.Movie,
                    iconColor = accentColor,
                    onClick = { showLanguageDialog = true }
                )
            }
        }

        if (enabled) {
            KitsugiSettingsSection(title = stringResource(R.string.tmdb_detail_blocks)) {
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.tmdb_artwork),
                    description = stringResource(R.string.tmdb_artwork_desc),
                    icon = Icons.Movie,
                    iconColor = accentColor,
                    checked = useArtwork,
                    onCheckedChange = onUseArtworkChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.tmdb_basic_info),
                    description = stringResource(R.string.tmdb_basic_info_desc),
                    icon = Icons.Movie,
                    iconColor = accentColor,
                    checked = useBasicInfo,
                    onCheckedChange = onUseBasicInfoChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.tmdb_details),
                    description = stringResource(R.string.tmdb_details_desc),
                    icon = Icons.Movie,
                    iconColor = accentColor,
                    checked = useDetails,
                    onCheckedChange = onUseDetailsChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.tmdb_release_dates),
                    description = stringResource(R.string.tmdb_release_dates_desc),
                    icon = Icons.Movie,
                    iconColor = accentColor,
                    checked = useReleaseDates,
                    onCheckedChange = onUseReleaseDatesChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.tmdb_credits),
                    description = stringResource(R.string.tmdb_credits_desc),
                    icon = Icons.Movie,
                    iconColor = accentColor,
                    checked = useCredits,
                    onCheckedChange = onUseCreditsChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.tmdb_productions),
                    description = stringResource(R.string.tmdb_productions_desc),
                    icon = Icons.Movie,
                    iconColor = accentColor,
                    checked = useProductions,
                    onCheckedChange = onUseProductionsChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.tmdb_networks),
                    description = stringResource(R.string.tmdb_networks_desc),
                    icon = Icons.Movie,
                    iconColor = accentColor,
                    checked = useNetworks,
                    onCheckedChange = onUseNetworksChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.tmdb_episodes),
                    description = stringResource(R.string.tmdb_episodes_desc),
                    icon = Icons.Movie,
                    iconColor = accentColor,
                    checked = useEpisodes,
                    onCheckedChange = onUseEpisodesChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.tmdb_trailers),
                    description = stringResource(R.string.tmdb_trailers_desc),
                    icon = Icons.Movie,
                    iconColor = accentColor,
                    checked = useTrailers,
                    onCheckedChange = onUseTrailersChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.tmdb_more_like_this),
                    description = stringResource(R.string.tmdb_more_like_this_desc),
                    icon = Icons.Movie,
                    iconColor = accentColor,
                    checked = useMoreLikeThis,
                    onCheckedChange = onUseMoreLikeThisChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.tmdb_collections),
                    description = stringResource(R.string.tmdb_collections_desc),
                    icon = Icons.Movie,
                    iconColor = accentColor,
                    checked = useCollections,
                    onCheckedChange = onUseCollectionsChanged
                )
            }
        }
    }

    if (showLanguageDialog) {
        LanguageSelectionDialog(
            title = stringResource(R.string.tmdb_language_dialog_title),
            selectedLanguage = language,
            onLanguageSelected = {
                onLanguageChanged(it)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false }
        )
    }
}

@Composable
private fun LanguageSelectionDialog(
    title: String,
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = KitsugiColors.TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .kitsugiVerticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AVAILABLE_TMDB_LANGUAGES.forEach { lang ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tvClickable(shape = RoundedCornerShape(8.dp)) {
                                onLanguageSelected(lang.code)
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = lang.displayName,
                            color = if (lang.code == selectedLanguage) LocalKitsugiAccent.current else KitsugiColors.TextPrimary,
                            fontWeight = if (lang.code == selectedLanguage) FontWeight.Bold else FontWeight.Normal
                        )
                        if (lang.code == selectedLanguage) {
                            Icon(
                                    imageVector = Icons.Check,
                                    contentDescription = null,
                                    tint = LocalKitsugiAccent.current,
                                    modifier = Modifier.size(16.dp)
                                )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close), color = LocalKitsugiAccent.current)
            }
        },
        containerColor = KitsugiColors.Surface,
        textContentColor = KitsugiColors.TextPrimary
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MdbListSettingsTab(
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    apiKey: String,
    onApiKeyChanged: (String) -> Unit,
    showImdb: Boolean,
    onShowImdbChanged: (Boolean) -> Unit,
    showTomatoes: Boolean,
    onShowTomatoesChanged: (Boolean) -> Unit,
    showMetacritic: Boolean,
    onShowMetacriticChanged: (Boolean) -> Unit,
    showAudience: Boolean,
    onShowAudienceChanged: (Boolean) -> Unit,
    showLetterboxd: Boolean,
    onShowLetterboxdChanged: (Boolean) -> Unit,
    showTmdb: Boolean,
    onShowTmdbChanged: (Boolean) -> Unit,
    showTrakt: Boolean,
    onShowTraktChanged: (Boolean) -> Unit,
    accentColor: Color,
    scrollState: ScrollState = rememberScrollState()
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .kitsugiVerticalScroll(scrollState)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        KitsugiSettingsSection(title = stringResource(R.string.mdblist_title)) {
            KitsugiSettingsSwitchItem(
                title = stringResource(R.string.etkinlestir),
                description = stringResource(R.string.mdblist_desc),
                icon = Icons.Movie,
                iconColor = accentColor,
                checked = enabled,
                onCheckedChange = onEnabledChanged
            )

            if (enabled) {
                KitsugiSettingsDivider()

                var showApiKeyDialog by remember { mutableStateOf(false) }

                KitsugiSettingsListItem(
                    title = stringResource(R.string.mdblist_api_key),
                    description = stringResource(R.string.mdblist_api_key_desc),
                    value = maskApiKey(apiKey, stringResource(R.string.value_not_set)),
                    icon = Icons.Key,
                    iconColor = accentColor,
                    onClick = { showApiKeyDialog = true }
                )
                if (apiKey.isBlank()) {
                    Text(
                        text = stringResource(R.string.integration_mdb_key_required),
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                } else {
                    // Kota, anahtara özgüdür: anahtar değişince panel kendini yeniden bağlar.
                    KitsugiSettingsDivider()
                    MdbListQuotaPanel(
                        apiKey = apiKey,
                        accentColor = accentColor
                    )
                }

                if (showApiKeyDialog) {
                    MdbListApiKeyValidationDialog(
                        currentValue = apiKey,
                        onSave = { key ->
                            onApiKeyChanged(key)
                            showApiKeyDialog = false
                            // Anahtar değişti: panelin ilk karesinde eski anahtarın kotası
                            // görünmesin diye merkez hemen yeni anahtara bağlanır.
                            if (key.isBlank()) {
                                MdbListQuotaCenter.clear()
                            } else {
                                MdbListQuotaCenter.bind(null, key)
                            }
                        },
                        onDismiss = { showApiKeyDialog = false },
                        accentColor = accentColor
                    )
                }
            }
        }

        if (enabled) {
            KitsugiSettingsSection(title = stringResource(R.string.mdblist_score_services)) {
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.mdblist_imdb),
                    description = stringResource(R.string.mdblist_imdb_desc),
                    icon = Icons.Movie,
                    iconColor = accentColor,
                    checked = showImdb,
                    onCheckedChange = onShowImdbChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.mdblist_rotten_tomatoes),
                    description = stringResource(R.string.mdblist_rotten_tomatoes_desc),
                    icon = Icons.Star,
                    iconColor = accentColor,
                    checked = showTomatoes,
                    onCheckedChange = onShowTomatoesChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.mdblist_audience),
                    description = stringResource(R.string.mdblist_audience_desc),
                    icon = Icons.Star,
                    iconColor = accentColor,
                    checked = showAudience,
                    onCheckedChange = onShowAudienceChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.mdblist_metacritic),
                    description = stringResource(R.string.mdblist_metacritic_desc),
                    icon = Icons.Star,
                    iconColor = accentColor,
                    checked = showMetacritic,
                    onCheckedChange = onShowMetacriticChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.mdblist_letterboxd),
                    description = stringResource(R.string.mdblist_letterboxd_desc),
                    icon = Icons.Movie,
                    iconColor = accentColor,
                    checked = showLetterboxd,
                    onCheckedChange = onShowLetterboxdChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.mdblist_tmdb_score),
                    description = stringResource(R.string.mdblist_tmdb_score_desc),
                    icon = Icons.Movie,
                    iconColor = accentColor,
                    checked = showTmdb,
                    onCheckedChange = onShowTmdbChanged
                )

                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.mdblist_trakt),
                    description = stringResource(R.string.mdblist_trakt_desc),
                    icon = Icons.Star,
                    iconColor = accentColor,
                    checked = showTrakt,
                    onCheckedChange = onShowTraktChanged
                )
            }
        }
    }
}

/** Kota sayılarını yerel binlik ayırıcısıyla yazar (kurallı: 1.000 / 25.000). */
private fun formatQuotaCount(value: Int): String =
    java.text.NumberFormat.getIntegerInstance().format(value)

/** Sıfırlanma anını (epoch saniye) kullanıcının yerel saat diliminde kısa biçimde yazar. */
private fun formatQuotaReset(epochSeconds: Long?): String? {
    val epoch = epochSeconds?.takeIf { it > 0L } ?: return null
    return runCatching {
        java.time.Instant.ofEpochSecond(epoch)
            .atZone(java.time.ZoneId.systemDefault())
            .format(
                java.time.format.DateTimeFormatter.ofPattern(
                    "d MMM HH:mm",
                    java.util.Locale.getDefault()
                )
            )
    }.getOrNull()
}

/**
 * MDBList API anahtarının günlük istek kotasını gösterir.
 *
 * Tasarım notları:
 * - Limit **API'den** okunur; ücretsiz (1.000) ve ücretli (10.000+) hesaplar aynı kodla
 *   çalışır, hiçbir yerde sabit sayı varsayılmaz.
 * - Anahtar değişirse panel yeni anahtarın kaydını yükler; eski anahtarın sayısı gösterilmez.
 * - Kota bitince yüzde çubuğu yerine kırmızı bir uyarı bloğu çizilir ve puanların ne zaman
 *   yeniden yükleneceği yerel saatle söylenir.
 */
@Composable
private fun MdbListQuotaPanel(
    apiKey: String,
    accentColor: Color
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storedQuota by MdbListQuotaCenter.quota.collectAsState()
    // Günlük sıfırlanma geçtiyse eski "0" değeri değil, yenilenmiş kota gösterilir.
    val quota = remember(storedQuota) { storedQuota?.rolledOver() }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(apiKey) {
        if (apiKey.isBlank()) return@LaunchedEffect
        MdbListQuotaCenter.bind(context, apiKey)
        refreshing = true
        // Tazeleme isteğinin kendisi de kotadan düşer: otomatik doğrulama seyrek tutulur,
        // kullanıcı isterse yenile düğmesiyle anında günceller.
        MdbListQuotaCenter.refresh(apiKey, maxAgeMillis = 5L * 60_000L)
        refreshing = false
    }

    val exhausted = quota?.isExhausted == true
    val known = quota?.isKnown == true

    // Kota dolduğu anda tek seferlik uyarı: detay kartı ve bu panel aynı bayrağı paylaştığı
    // için iki ayrı balon çıkmaz.
    LaunchedEffect(exhausted, quota?.resetAtEpochSeconds) {
        if (exhausted && MdbListQuotaCenter.consumeExhaustedNotice()) {
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.mdblist_quota_exhausted_toast),
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
    val statusColor = when {
        exhausted -> KitsugiColors.AccentRed
        quota?.isLow == true -> KitsugiColors.AccentOrange
        else -> accentColor
    }
    val resetLabel = formatQuotaReset(quota?.resetAtEpochSeconds)

    val refreshQuota: () -> Unit = {
        scope.launch {
            refreshing = true
            MdbListQuotaCenter.refresh(apiKey, maxAgeMillis = 0L)
            refreshing = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Speed,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.mdblist_quota_title),
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(8.dp))
            QuotaPlanBadge(plan = quota?.plan ?: MdbListPlan.UNKNOWN, accentColor = accentColor)
            Spacer(Modifier.weight(1f))
            if (refreshing) {
                KitsugiPlasmaLoader(size = 16.dp)
            } else {
                IconButton(
                    onClick = refreshQuota,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.mdblist_quota_refresh),
                        tint = KitsugiColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        when {
            exhausted -> MdbListQuotaExhaustedNotice(resetLabel = resetLabel)

            known -> {
                Text(
                    text = stringResource(
                        R.string.mdblist_quota_remaining,
                        formatQuotaCount(quota?.remaining ?: 0),
                        formatQuotaCount(quota?.limit ?: 0)
                    ),
                    color = statusColor,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                LinearProgressIndicator(
                    progress = { quota?.remainingFraction ?: 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = statusColor,
                    trackColor = KitsugiColors.SurfaceStrong
                )
                if (resetLabel != null) {
                    Text(
                        text = stringResource(R.string.mdblist_quota_reset, resetLabel),
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            refreshing -> Text(
                text = stringResource(R.string.mdblist_quota_loading),
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )

            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.mdblist_quota_unknown),
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = refreshQuota) {
                    Text(
                        text = stringResource(R.string.mdblist_quota_refresh),
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun QuotaPlanBadge(plan: MdbListPlan, accentColor: Color) {
    if (plan == MdbListPlan.UNKNOWN) return
    val label = stringResource(
        if (plan == MdbListPlan.SUPPORTER) R.string.mdblist_quota_plan_supporter
        else R.string.mdblist_quota_plan_free
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accentColor.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = accentColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** Günlük kota bittiğinde gösterilen kırmızı uyarı bloğu. */
@Composable
private fun MdbListQuotaExhaustedNotice(resetLabel: String?) {
    val warningColor = KitsugiColors.AccentRed
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(warningColor.copy(alpha = 0.14f))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Rounded.WarningAmber,
            contentDescription = null,
            tint = warningColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.mdblist_quota_exhausted_title),
                color = warningColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (resetLabel != null) {
                    stringResource(R.string.mdblist_quota_exhausted_desc, resetLabel)
                } else {
                    stringResource(R.string.mdblist_quota_exhausted_desc_unknown)
                },
                color = KitsugiColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun AniSkipSettingsTab(
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    autoSkip: Boolean,
    onAutoSkipChanged: (Boolean) -> Unit,
    animeSkipClientId: String,
    onAnimeSkipClientIdChanged: (String) -> Unit,
    accentColor: Color,
    scrollState: ScrollState = rememberScrollState()
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .kitsugiVerticalScroll(scrollState)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        KitsugiSettingsSection(title = stringResource(R.string.aniskip_title)) {
            KitsugiSettingsSwitchItem(
                title = stringResource(R.string.etkinlestir),
                description = stringResource(R.string.aniskip_desc),
                icon = Icons.SkipNext,
                iconColor = accentColor,
                checked = enabled,
                onCheckedChange = onEnabledChanged
            )

            if (enabled) {
                KitsugiSettingsDivider()

                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.aniskip_auto_skip),
                    description = stringResource(R.string.aniskip_auto_skip_desc),
                    icon = Icons.SkipNext,
                    iconColor = accentColor,
                    checked = autoSkip,
                    onCheckedChange = onAutoSkipChanged
                )
                Text(
                    text = stringResource(R.string.integration_skip_sources_info),
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        if (enabled) {
            KitsugiSettingsSection(title = stringResource(R.string.animeskip_integration)) {
                var showClientIdDialog by remember { mutableStateOf(false) }

                KitsugiSettingsListItem(
                    title = stringResource(R.string.animeskip_client_id),
                    description = stringResource(R.string.animeskip_client_id_desc),
                    value = maskApiKey(animeSkipClientId, stringResource(R.string.animeskip_default)),
                    icon = Icons.Key,
                    iconColor = accentColor,
                    onClick = { showClientIdDialog = true }
                )

                if (showClientIdDialog) {
                    AnimeSkipClientIdValidationDialog(
                        currentValue = animeSkipClientId,
                        onSave = { clientId ->
                            onAnimeSkipClientIdChanged(clientId)
                            showClientIdDialog = false
                        },
                        onDismiss = { showClientIdDialog = false },
                        accentColor = accentColor
                    )
                }
            }
        }
    }
}

private fun maskApiKey(key: String, placeholder: String): String {
    val trimmed = key.trim()
    if (trimmed.isBlank()) return placeholder
    return if (trimmed.length <= 4) "••••" else "••••••${trimmed.takeLast(4)}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MdbListApiKeyValidationDialog(
    currentValue: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    accentColor: Color
) {
    var value by remember(currentValue) { mutableStateOf(currentValue) }
    var keyVisible by remember { mutableStateOf(false) }
    var validating by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.mdblist_api_dialog_title),
                color = KitsugiColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.api_key_dialog_desc),
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(R.string.mdblist_api_key)) },
                    placeholder = { Text(stringResource(R.string.mdblist_api_key_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !validating,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = KitsugiColors.Border,
                        cursorColor = accentColor,
                        focusedTextColor = KitsugiColors.TextPrimary,
                        unfocusedTextColor = KitsugiColors.TextPrimary,
                        focusedLabelColor = accentColor,
                        unfocusedLabelColor = KitsugiColors.TextSecondary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { keyVisible = !keyVisible }) {
                                Icon(
                                    imageVector = if (keyVisible) Icons.VisibilityOff else Icons.Visibility,
                                    contentDescription = stringResource(
                                        if (keyVisible) R.string.integration_hide_key else R.string.integration_show_key
                                    ),
                                    tint = KitsugiColors.TextSecondary
                                )
                            }
                            if (value.isNotEmpty()) {
                                IconButton(onClick = { value = "" }) {
                                    Icon(
                                        imageVector = Icons.Close,
                                        contentDescription = stringResource(R.string.action_clear),
                                        tint = KitsugiColors.TextSecondary
                                    )
                                }
                            }
                        }
                    }
                )
            }
        },
        confirmButton = {
            KitsugiButton(
                onClick = {
                    if (value.isBlank()) {
                        onSave("")
                    } else {
                        validating = true
                        scope.launch {
                            val valid = com.kitsugi.animelist.data.remote.MdbListClient.validateApiKey(value)
                            validating = false
                            if (valid) {
                                onSave(value.trim())
                                android.widget.Toast.makeText(context, context.getString(R.string.api_key_validated), android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                android.widget.Toast.makeText(context, context.getString(R.string.api_key_invalid), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                enabled = !validating
            ) {
                if (validating) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KitsugiPlasmaLoader(size = 18.dp)
                        Text(stringResource(R.string.action_validating))
                    }
                } else {
                    Text(stringResource(R.string.action_validate_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !validating) {
                Text(stringResource(R.string.action_cancel), color = KitsugiColors.TextMuted)
            }
        },
        containerColor = KitsugiColors.Surface,
        textContentColor = KitsugiColors.TextPrimary
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnimeSkipClientIdValidationDialog(
    currentValue: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    accentColor: Color
) {
    var value by remember(currentValue) { mutableStateOf(currentValue) }
    var validating by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.animeskip_client_id_dialog_title),
                color = KitsugiColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.animeskip_client_id_dialog_desc),
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(R.string.animeskip_client_id_label)) },
                    placeholder = { Text(stringResource(R.string.animeskip_default)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = KitsugiColors.Border,
                        cursorColor = accentColor,
                        focusedTextColor = KitsugiColors.TextPrimary,
                        unfocusedTextColor = KitsugiColors.TextPrimary,
                        focusedLabelColor = accentColor,
                        unfocusedLabelColor = KitsugiColors.TextSecondary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    trailingIcon = {
                        if (value.isNotEmpty()) {
                            IconButton(onClick = { value = "" }) {
                                Icon(
                                    imageVector = Icons.Close,
                                    contentDescription = stringResource(R.string.action_clear),
                                    tint = KitsugiColors.TextSecondary
                                )
                            }
                        }
                    }
                )
            }
        },
        confirmButton = {
            KitsugiButton(
                onClick = {
                    if (value.isBlank()) {
                        onSave("")
                    } else {
                        validating = true
                        scope.launch {
                            val valid = com.kitsugi.animelist.data.remote.AnimeSkipClient.validateClientId(value)
                            validating = false
                            if (valid) {
                                onSave(value)
                                android.widget.Toast.makeText(context, context.getString(R.string.client_id_validated), android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                android.widget.Toast.makeText(context, context.getString(R.string.client_id_invalid), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                enabled = !validating
            ) {
                if (validating) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KitsugiPlasmaLoader(size = 18.dp)
                        Text(stringResource(R.string.action_validating))
                    }
                } else {
                    Text(stringResource(R.string.action_validate_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !validating) {
                Text(stringResource(R.string.action_cancel), color = KitsugiColors.TextMuted)
            }
        },
        containerColor = KitsugiColors.Surface,
        textContentColor = KitsugiColors.TextPrimary
    )
}

@Composable
private fun FanartTvSettingsTab(
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    apiKey: String,
    onApiKeyChanged: (String) -> Unit,
    accentColor: Color,
    scrollState: ScrollState = rememberScrollState()
) {
    var tempKey by remember(apiKey) { mutableStateOf(apiKey) }
    var keyVisible by remember { mutableStateOf(false) }
    var keyValidating by remember { mutableStateOf(false) }
    var keyValidationPassed by remember(apiKey) { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .kitsugiVerticalScroll(scrollState)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        KitsugiSettingsSection(title = stringResource(R.string.fanart_settings_title)) {
            KitsugiSettingsSwitchItem(
                title = stringResource(R.string.etkinlestir),
                description = stringResource(R.string.fanart_settings_desc),
                icon = Icons.Movie,
                iconColor = accentColor,
                checked = enabled,
                onCheckedChange = onEnabledChanged
            )

            if (enabled) {
                KitsugiSettingsDivider()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    OutlinedTextField(
                        value = tempKey,
                        onValueChange = {
                            tempKey = it
                            keyValidationPassed = null
                        },
                        label = { Text(stringResource(R.string.fanart_api_key)) },
                        placeholder = { Text(stringResource(R.string.fanart_api_key_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !keyValidating,
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            unfocusedBorderColor = KitsugiColors.Border,
                            cursorColor = accentColor,
                            focusedTextColor = KitsugiColors.TextPrimary,
                            unfocusedTextColor = KitsugiColors.TextPrimary,
                            focusedLabelColor = accentColor,
                            unfocusedLabelColor = KitsugiColors.TextSecondary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { keyVisible = !keyVisible }) {
                                    Icon(
                                        imageVector = if (keyVisible) Icons.VisibilityOff else Icons.Visibility,
                                        contentDescription = stringResource(
                                            if (keyVisible) R.string.integration_hide_key else R.string.integration_show_key
                                        ),
                                        tint = KitsugiColors.TextSecondary
                                    )
                                }
                                if (tempKey.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            tempKey = ""
                                            keyValidationPassed = null
                                            onApiKeyChanged("")
                                        },
                                        enabled = !keyValidating
                                    ) {
                                        Icon(
                                            imageVector = Icons.Close,
                                            contentDescription = stringResource(R.string.action_clear),
                                            tint = KitsugiColors.TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    )
                    Text(
                        text = stringResource(R.string.integration_shared_fanart_key_info),
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    OutlinedButton(
                        onClick = {
                            keyValidating = true
                            keyValidationPassed = null
                            scope.launch {
                                val valid = runCatching {
                                    com.kitsugi.animelist.data.remote.FanartApiClient.validateApiKey(tempKey)
                                }.getOrDefault(false)
                                if (valid) onApiKeyChanged(tempKey.trim())
                                keyValidationPassed = valid
                                keyValidating = false
                            }
                        },
                        enabled = !keyValidating,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor)
                    ) {
                        if (keyValidating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = accentColor,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.action_validate_save))
                    }
                    keyValidationPassed?.let { passed ->
                        Text(
                            text = stringResource(
                                if (passed) R.string.integration_key_validated else R.string.integration_key_failed
                            ),
                            color = if (passed) Color(0xFF76D49B) else KitsugiColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}



@Composable
private fun MusicSettingsTab(
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    autoRecommend: Boolean,
    onAutoRecommendChanged: (Boolean) -> Unit,
    autoPlay: Boolean = false,
    onAutoPlayChanged: (Boolean) -> Unit = {},
    autoPlayDelay: Int = 3,
    onAutoPlayDelayChanged: (Int) -> Unit = {},
    continueOnExit: Boolean,
    onContinueOnExitChanged: (Boolean) -> Unit,
    masterVolume: Float,
    onMasterVolumeChanged: (Float) -> Unit,
    channelVolume: Float,
    onChannelVolumeChanged: (Float) -> Unit,
    ambientVolume: Float,
    onAmbientVolumeChanged: (Float) -> Unit,
    jamendoEnabled: Boolean,
    onJamendoEnabledChanged: (Boolean) -> Unit,
    jamendoClientId: String,
    onJamendoClientIdChanged: (String) -> Unit,
    freesoundEnabled: Boolean,
    onFreesoundEnabledChanged: (Boolean) -> Unit,
    freesoundApiKey: String,
    onFreesoundApiKeyChanged: (String) -> Unit,
    openverseEnabled: Boolean,
    onOpenverseEnabledChanged: (Boolean) -> Unit,
    openverseToken: String,
    onOpenverseTokenChanged: (String) -> Unit,
    archiveEnabled: Boolean,
    onArchiveEnabledChanged: (Boolean) -> Unit,
    audiusEnabled: Boolean,
    onAudiusEnabledChanged: (Boolean) -> Unit,
    audiusApiKey: String,
    onAudiusApiKeyChanged: (String) -> Unit,
    accentColor: Color,
    scrollState: ScrollState = rememberScrollState()
) {
    // Açık lisans kaynakları ve internetten otomatik çalma, okuyucu
    // paneliyle **aynı** SharedPreferences dosyasını kullanır (v2.4.480).
    val ambientAppContext = LocalContext.current.applicationContext
    val ambientPreferences = remember(ambientAppContext) {
        com.kitsugi.animelist.data.ambient.AmbientAudioPreferences(ambientAppContext)
    }
    var autoPlayOnline by remember { mutableStateOf(ambientPreferences.autoPlayOnline) }
    var enabledAmbientSources by remember { mutableStateOf(ambientPreferences.enabledSources()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .kitsugiVerticalScroll(scrollState)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ── Bölüm 1: Yerleşik motor ─────────────────────────────────────
        KitsugiSettingsSection(
            title = stringResource(R.string.music_panel_title),
            subtitle = stringResource(R.string.music_panel_subtitle)
        ) {
            KitsugiSettingsSwitchItem(
                title = stringResource(R.string.music_settings_enabled),
                description = stringResource(R.string.music_settings_enabled_desc),
                icon = Icons.Rounded.MusicNote,
                iconColor = accentColor,
                checked = enabled,
                onCheckedChange = onEnabledChanged
            )

            if (enabled) {
                KitsugiSettingsDivider()
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.music_settings_auto_recommend),
                    description = stringResource(R.string.music_settings_auto_recommend_desc),
                    icon = Icons.Rounded.AutoAwesome,
                    iconColor = KitsugiColors.AccentPurple,
                    checked = autoRecommend,
                    onCheckedChange = onAutoRecommendChanged
                )

                KitsugiSettingsDivider()
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.music_settings_auto_play),
                    description = stringResource(R.string.music_settings_auto_play_desc),
                    icon = Icons.Rounded.PlayArrow,
                    iconColor = KitsugiColors.AccentTeal,
                    checked = autoPlay,
                    onCheckedChange = onAutoPlayChanged
                )

                if (autoPlay) {
                    KitsugiSettingsDivider()
                    // Bekleme süresi — 0..30 sn
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = stringResource(R.string.music_settings_auto_play_delay, autoPlayDelay),
                            color = KitsugiColors.TextPrimary,
                            fontSize = 13.sp
                        )
                        Text(
                            text = stringResource(R.string.music_settings_auto_play_delay_desc),
                            color = KitsugiColors.TextMuted,
                            fontSize = 11.sp
                        )
                        androidx.compose.material3.Slider(
                            value = autoPlayDelay.toFloat(),
                            onValueChange = { onAutoPlayDelayChanged(it.toInt()) },
                            valueRange = 0f..30f,
                            steps = 29,
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = accentColor,
                                activeTrackColor = accentColor
                            )
                        )
                    }
                }

                KitsugiSettingsDivider()
                KitsugiSettingsSwitchItem(
                    title = "İnternetten otomatik çal",
                    description = "Okuyucu açılınca manga adıyla aranır, en alakalı parça ve " +
                        "benzerleri sırayla kendiliğinden çalar. Kaynaklar aşağıda seçilir.",
                    icon = Icons.Rounded.MusicNote,
                    iconColor = accentColor,
                    checked = autoPlayOnline,
                    onCheckedChange = { checked ->
                        autoPlayOnline = checked
                        ambientPreferences.autoPlayOnline = checked
                    }
                )

                KitsugiSettingsDivider()
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.music_settings_continue_on_exit),
                    description = stringResource(R.string.music_settings_continue_on_exit_desc),
                    icon = Icons.Rounded.PlayCircle,
                    iconColor = KitsugiColors.AccentTeal,
                    checked = continueOnExit,
                    onCheckedChange = onContinueOnExitChanged
                )

                KitsugiSettingsDivider()

                // Ses seviyeleri
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.music_volume_title),
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    MusicVolumeSlider(
                        label = stringResource(R.string.music_mixer_master),
                        value = masterVolume,
                        onValueChange = onMasterVolumeChanged,
                        accentColor = accentColor
                    )
                    MusicVolumeSlider(
                        label = stringResource(R.string.music_mixer_music),
                        value = channelVolume,
                        onValueChange = onChannelVolumeChanged,
                        accentColor = accentColor
                    )
                    MusicVolumeSlider(
                        label = stringResource(R.string.music_mixer_ambient),
                        value = ambientVolume,
                        onValueChange = onAmbientVolumeChanged,
                        accentColor = accentColor
                    )
                }
            }
        }

        // ── Bölüm 2: Kişisel API anahtarları ────────────────────────────
        if (enabled) {
            KitsugiSettingsSection(
                title = stringResource(R.string.music_source_section_keys),
                subtitle = stringResource(R.string.music_source_desc)
            ) {
                // Jamendo
                MusicSourceKeyItem(
                    sourceId = com.kitsugi.animelist.data.music.MusicSourceId.JAMENDO,
                    enabled = jamendoEnabled &&
                        com.kitsugi.animelist.data.ambient.AmbientAudioSource.JAMENDO in enabledAmbientSources,
                    onEnabledChanged = { value ->
                        onJamendoEnabledChanged(value)
                        // Ortam sesi motoru da aynı anahtarı görsün (v2.4.480).
                        val next = if (value) {
                            enabledAmbientSources + com.kitsugi.animelist.data.ambient.AmbientAudioSource.JAMENDO
                        } else {
                            enabledAmbientSources - com.kitsugi.animelist.data.ambient.AmbientAudioSource.JAMENDO
                        }
                        if (next.isNotEmpty()) {
                            enabledAmbientSources = next
                            ambientPreferences.setEnabledSources(next)
                        }
                    },
                    currentKey = jamendoClientId,
                    onKeyChanged = onJamendoClientIdChanged,
                    accentColor = accentColor
                )
                KitsugiSettingsDivider()
                // Freesound
                MusicSourceKeyItem(
                    sourceId = com.kitsugi.animelist.data.music.MusicSourceId.FREESOUND,
                    enabled = freesoundEnabled,
                    onEnabledChanged = onFreesoundEnabledChanged,
                    currentKey = freesoundApiKey,
                    onKeyChanged = onFreesoundApiKeyChanged,
                    accentColor = accentColor
                )
                KitsugiSettingsDivider()
                // Openverse (anahtarsız çalışır, token opsiyonel)
                MusicSourceKeyItem(
                    sourceId = com.kitsugi.animelist.data.music.MusicSourceId.OPENVERSE,
                    enabled = openverseEnabled,
                    onEnabledChanged = onOpenverseEnabledChanged,
                    currentKey = openverseToken,
                    onKeyChanged = onOpenverseTokenChanged,
                    accentColor = accentColor
                )
                KitsugiSettingsDivider()
                // Audius
                MusicSourceKeyItem(
                    sourceId = com.kitsugi.animelist.data.music.MusicSourceId.AUDIUS,
                    enabled = audiusEnabled,
                    onEnabledChanged = onAudiusEnabledChanged,
                    currentKey = audiusApiKey,
                    onKeyChanged = onAudiusApiKeyChanged,
                    accentColor = accentColor
                )
                KitsugiSettingsDivider()
                // Internet Archive (anahtarsız)
                MusicKeylessSourceItem(
                    sourceId = com.kitsugi.animelist.data.music.MusicSourceId.ARCHIVE,
                    enabled = archiveEnabled,
                    onEnabledChanged = onArchiveEnabledChanged,
                    accentColor = accentColor
                )
            }

            // ── Bölüm 3: Açık lisans / anahtarsız yeni kaynaklar ────────
            // Okuyucu panelindeki Çevrimiçi sekmesinin KAYNAKLAR listesiyle
            // birebir aynı 8 kaynağın ortak anahtarlarıdır (v2.4.480).
            KitsugiSettingsSection(
                title = "Ortam sesi kaynakları",
                subtitle = "Jamendo dışındaki kaynaklar anahtar gerektirmez. Burada kapatılan " +
                    "kaynak okuyucudaki otomatik aramaya da katılmaz."
            ) {
                val ambientSourceLabels = listOf(
                    com.kitsugi.animelist.data.ambient.AmbientAudioSource.JAMENDO to
                        ("Jamendo" to "Kişisel Client ID gerekir; anahtarı yukarıdaki Jamendo kartından gir. Yalnızca akış."),
                    com.kitsugi.animelist.data.ambient.AmbientAudioSource.FREESOUND to
                        ("Freesound · Openverse" to "CC ses kayıtları; Openverse üzerinden anahtarsız taranır."),
                    com.kitsugi.animelist.data.ambient.AmbientAudioSource.WIKIMEDIA_AUDIO to
                        ("Wikimedia audio · Openverse" to "Vikipedi/Vikiveri ses dosyaları."),
                    com.kitsugi.animelist.data.ambient.AmbientAudioSource.INTERNET_ARCHIVE to
                        ("Internet Archive" to "Kamu malı arşiv kayıtları; kota pratikte yok."),
                    com.kitsugi.animelist.data.ambient.AmbientAudioSource.AUDIUS to
                        ("Audius" to "Merkeziyetsiz müzik platformu; anahtarsız da çalışır."),
                    com.kitsugi.animelist.data.ambient.AmbientAudioSource.WIKIMEDIA_COMMONS to
                        ("Wikimedia Commons" to "Özgür lisanslı medya arşivi."),
                    com.kitsugi.animelist.data.ambient.AmbientAudioSource.CCMIXTER to
                        ("ccMixter" to "Creative Commons remiks ve enstrümantal müzik."),
                    com.kitsugi.animelist.data.ambient.AmbientAudioSource.FREE_TO_USE to
                        ("Free To Use" to "Ücretsiz kullanım kataloğu."),
                    com.kitsugi.animelist.data.ambient.AmbientAudioSource.RADIO_BROWSER to
                        ("Radio Browser" to "Canlı radyo istasyonları; parça süresi yoktur."),
                    com.kitsugi.animelist.data.ambient.AmbientAudioSource.ANIMETHEMES to
                        ("Anime OP/ED" to "Animenin kendi açılış/kapanış şarkısı (AnimeThemes); " +
                            "doğrudan ses dosyası, indirilebilir, anahtarsız."),
                    com.kitsugi.animelist.data.ambient.AmbientAudioSource.YOUTUBE to
                        ("YouTube Music" to "Tüm YouTube kataloğu (OST, OP/ED, cover). Akış " +
                            "çalma anında çözülür; anahtarsız, indirilemez.")
                )
                ambientSourceLabels.forEachIndexed { index, entry ->
                    val (source, texts) = entry
                    val (label, description) = texts
                    if (index > 0) KitsugiSettingsDivider()
                    KitsugiSettingsSwitchItem(
                        title = label,
                        description = description,
                        icon = Icons.Rounded.MusicNote,
                        iconColor = accentColor,
                        checked = source in enabledAmbientSources,
                        onCheckedChange = { checked ->
                            val next = if (checked) enabledAmbientSources + source
                            else enabledAmbientSources - source
                            if (next.isNotEmpty()) {
                                enabledAmbientSources = next
                                ambientPreferences.setEnabledSources(next)
                                if (source == com.kitsugi.animelist.data.ambient.AmbientAudioSource.JAMENDO) {
                                    onJamendoEnabledChanged(checked)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Ses seviyesi kaydırıcısı — etiket + yüzde göstergesi ile.
 */
@Composable
private fun MusicVolumeSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    accentColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = KitsugiColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(76.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = KitsugiColors.Border
            )
        )
        Text(
            text = "${(value * 100).toInt()}%",
            color = KitsugiColors.TextMuted,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(40.dp)
        )
    }
}

/**
 * Anahtar gerektiren bir kaynak kartı: anahtar alanı + doğrula + lisans notu.
 */
@Composable
private fun MusicSourceKeyItem(
    sourceId: com.kitsugi.animelist.data.music.MusicSourceId,
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    currentKey: String,
    onKeyChanged: (String) -> Unit,
    accentColor: Color
) {
    val def = com.kitsugi.animelist.data.music.MusicSources.byId(sourceId) ?: return
    var tempKey by remember(currentKey) { mutableStateOf(currentKey) }
    var keyVisible by remember { mutableStateOf(false) }
    var validating by remember { mutableStateOf(false) }
    var result by remember(currentKey) { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth()) {
        KitsugiSettingsSwitchItem(
            title = def.displayName,
            description = when {
                def.optionalKey -> "İsteğe bağlı — anahtarsız da çalışır, anahtar limiti artırır"
                def.keyless -> stringResource(R.string.music_source_keyless_note)
                else -> stringResource(R.string.music_source_key_hint)
            },
            icon = Icons.Rounded.Key,
            iconColor = accentColor,
            checked = enabled,
            onCheckedChange = onEnabledChanged
        )

        // Anahtar alanı: zorunlu VEYA opsiyonel kaynaklarda gösterilir.
        // Yalnızca gerçekten anahtarsız kaynaklarda (Archive) gizlenir.
        if (enabled && (!def.keyless || def.optionalKey)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = tempKey,
                    onValueChange = {
                        tempKey = it
                        result = null
                    },
                    label = {
                        Text(
                            when {
                                def.requiresClientId -> "Client ID"
                                def.optionalKey -> "API Key (isteğe bağlı)"
                                else -> "API Key"
                            }
                        )
                    },
                    placeholder = { Text(def.keyHint) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !validating,
                    singleLine = true,
                    visualTransformation = if (keyVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { keyVisible = !keyVisible }) {
                                Icon(
                                    imageVector = if (keyVisible) Icons.Rounded.VisibilityOff
                                    else Icons.Rounded.Visibility,
                                    contentDescription = stringResource(
                                        if (keyVisible) R.string.integration_hide_key
                                        else R.string.integration_show_key
                                    ),
                                    tint = KitsugiColors.TextSecondary
                                )
                            }
                            if (tempKey.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        tempKey = ""
                                        result = null
                                        onKeyChanged("")
                                    },
                                    enabled = !validating
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.music_source_clear),
                                        tint = KitsugiColors.TextSecondary
                                    )
                                }
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = KitsugiColors.Border,
                        cursorColor = accentColor,
                        focusedTextColor = KitsugiColors.TextPrimary,
                        unfocusedTextColor = KitsugiColors.TextPrimary,
                        focusedLabelColor = accentColor,
                        unfocusedLabelColor = KitsugiColors.TextSecondary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                // ── Anahtar edinme yardımı ──────────────────────────────
                // "API Key nedir, nereden alınır?" sorusunu yanıtlar.
                // Kullanıcı uygulamadan çıkmadan siteye gider.
                // Anahtar alanı olan her kaynakta yardım kartı gösterilir
                if (!def.keyless || def.optionalKey) {
                    ApiKeyHelpCard(
                        def = def,
                        accentColor = accentColor
                    )
                    Spacer(Modifier.height(8.dp))
                }

                OutlinedButton(
                    onClick = {
                        validating = true
                        result = null
                        scope.launch {
                            val ok = com.kitsugi.animelist.data.music.MusicSourceValidator
                                .validate(sourceId, tempKey)
                            if (ok) onKeyChanged(tempKey.trim())
                            result = ok
                            validating = false
                        }
                    },
                    enabled = !validating && tempKey.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor)
                ) {
                    if (validating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = accentColor,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.music_source_validate))
                }

                result?.let { ok ->
                    Text(
                        text = stringResource(
                            if (ok) R.string.music_source_validated
                            else R.string.music_source_failed
                        ),
                        color = if (ok) Color(0xFF76D49B) else KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Lisans notu — şeffaflık için her zaman gösterilir
                if (def.notes.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.music_source_license_note) + ": " + def.notes,
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}

/**
 * Anahtar gerektirmeyen kaynak kartı (Internet Archive).
 */
@Composable
private fun MusicKeylessSourceItem(
    sourceId: com.kitsugi.animelist.data.music.MusicSourceId,
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    accentColor: Color
) {
    val def = com.kitsugi.animelist.data.music.MusicSources.byId(sourceId) ?: return
    Column(modifier = Modifier.fillMaxWidth()) {
        KitsugiSettingsSwitchItem(
            title = def.displayName,
            description = def.notes,
            icon = Icons.Rounded.Public,
            iconColor = accentColor,
            checked = enabled,
            onCheckedChange = onEnabledChanged
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
//  API ANAHTARI EDİNME YARDIMI
// ─────────────────────────────────────────────────────────────────────────

/**
 * 🔑 "Anahtarım yok, nereden alacağım?"
 *
 * Sorun: kullanıcı ekranda "API Key" alanı görüyor ama anahtarı yok.
 * Önceden yalnızca ham bir URL metni vardı — kopyalamak/taramak
 * gerekiyordu ve çoğu kişi bu adımda vazgeçiyordu.
 *
 * Çözüm: üç katmanlı yardım
 *  1. **"Anahtar al" butonu** → tarayıcıda doğrudan ilgili sayfa açılır
 *  2. **Adım adım talimat** → 1-2-3 numaralı, o kaynağa özel
 *  3. **Nasıl görünür** → anahtarın biçimi (uzunluk/ön ek) ki kullanıcı
 *     yanlış şeyi kopyalamadığını anlasın
 *
 * Ayrıca "anahtar gerekmez" durumu ve süre/limit bilgisi de burada.
 */
@Composable
private fun ApiKeyHelpCard(
    def: com.kitsugi.animelist.data.music.MusicSourceDef,
    accentColor: Color
) {
    // Kullanıcı adımları isterse açar — varsayılan kapalı, yer kaplamasın
    var expanded by remember { mutableStateOf(false) }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val context = androidx.compose.ui.platform.LocalContext.current

    val guide = remember(def.id) { ApiKeyGuide.of(def.id) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accentColor.copy(alpha = 0.08f))
            .padding(12.dp)
    ) {
        // ── Başlık + ana buton ──────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Key,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Anahtarın yok mu?",
                    color = KitsugiColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = guide.freeNote,
                    color = KitsugiColors.TextMuted,
                    fontSize = 9.sp,
                    lineHeight = 12.sp
                )
            }
        }

        Spacer(Modifier.height(9.dp))

        // ── Butonlar ────────────────────────────────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (def.signupUrl.isNotBlank()) {
                // ANA BUTON: siteye git
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(accentColor)
                        .clickable {
                            runCatching { uriHandler.openUri(def.signupUrl) }
                                .onFailure {
                                    // Tarayıcı yoksa panoya kopyala
                                    val clipboard = context.getSystemService(
                                        android.content.Context.CLIPBOARD_SERVICE
                                    ) as? android.content.ClipboardManager
                                    clipboard?.setPrimaryClip(
                                        android.content.ClipData.newPlainText(
                                            "Kitsugi anahtar adresi",
                                            def.signupUrl
                                        )
                                    )
                                    android.widget.Toast.makeText(
                                        context,
                                        "Adres panoya kopyalandı",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                        }
                        .padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInNew,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = guide.actionLabel,
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // İkincil: adım adım anlat
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(accentColor.copy(alpha = 0.16f))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess
                    else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "Nasıl?",
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // ── Adım adım talimat ───────────────────────────────────────────
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            guide.steps.forEachIndexed { index, step ->
                Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(15.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = accentColor,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = step,
                        color = KitsugiColors.TextSecondary,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            // Anahtar biçimi — yanlış şeyi kopyalamayı önler
            Text(
                text = "Biçim: ${guide.formatHint}",
                color = KitsugiColors.TextMuted,
                fontSize = 9.sp
            )
            Spacer(Modifier.height(4.dp))
            // Adresi elle kopyalamak isteyenler için (düşük kontrast, küçük)
            Text(
                text = def.signupUrl,
                color = KitsugiColors.TextMuted.copy(alpha = 0.6f),
                fontSize = 8.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        val clipboard = context.getSystemService(
                            android.content.Context.CLIPBOARD_SERVICE
                        ) as? android.content.ClipboardManager
                        clipboard?.setPrimaryClip(
                            android.content.ClipData.newPlainText("adres", def.signupUrl)
                        )
                        android.widget.Toast.makeText(
                            context, "Adres kopyalandı", android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    .padding(vertical = 2.dp)
            )
        }
    }
}

/**
 * Kaynağa özel anahtar edinme rehberi.
 *
 * Her kaynağın arayüzü farklı olduğu için talimatlar ayrı yazılmalı.
 * Genel bir "siteye git, kayıt ol" metni işe yaramaz.
 */
private object ApiKeyGuide {

    data class Guide(
        val actionLabel: String,
        val freeNote: String,
        val steps: List<String>,
        val formatHint: String
    )

    fun of(sourceId: com.kitsugi.animelist.data.music.MusicSourceId): Guide =
        when (sourceId) {

            // ── Jamendo ─────────────────────────────────────────────────
            // Doğrulandı: devportal.jamendo.com/signup → Create App →
            // "Client ID" (Client Secret DEĞİL — o OAuth2 için)
            com.kitsugi.animelist.data.music.MusicSourceId.JAMENDO -> Guide(
                actionLabel = "Jamendo'da anahtar al",
                freeNote = "Ücretsiz · ayda 35.000 istek · ticari olmayan",
                steps = listOf(
                    "Butona dokun → Jamendo geliştirici portalı açılır",
                    "Hesabınla giriş yap (yoksa 'Sign up')",
                    "Jamendo API Kullanım Şartları'nı kabul et",
                    "'Create App' → ad: Kitsugi, açıklama: kişisel kullanım",
                    "Uygulama sayfasında **Client ID**'yi kopyala",
                    "Buraya yapıştır → 'Doğrula'"
                ),
                formatHint = "32 karakter sayı-harf. Client Secret DEĞİL, Client ID."
            )

            // ── Freesound ───────────────────────────────────────────────
            // Doğrulandı: freesound.org/apiv2/apply → form → anahtar
            // ANINDA görünür, onay beklemesi YOK
            com.kitsugi.animelist.data.music.MusicSourceId.FREESOUND -> Guide(
                actionLabel = "Freesound'da anahtar al",
                freeNote = "Ücretsiz · günde 2.000 istek · anahtar anında verilir",
                steps = listOf(
                    "Butona dokun → freesound.org API sayfası açılır",
                    "Ücretsiz hesap aç veya giriş yap (hesap yoksa önce kayıt)",
                    "Formu doldur: uygulama adı 'Kitsugi', kısa açıklama",
                    "Kullanım şartlarını işaretle",
                    "'Apply for a new credential' düğmesine bas",
                    "Sayfada tablo çıkar — 'Client secret / API key' sütunundaki değeri kopyala",
                    "Buraya yapıştır → 'Doğrula'"
                ),
                formatHint = "~24 karakter harf-rakam. Onay beklemez, anında oluşur."
            )

            // ── Audius ──────────────────────────────────────────────────
            // Doğrulandı: audius.co/settings → Developer Apps
            // Anahtarsız da çalışır, sadece limit artar
            com.kitsugi.animelist.data.music.MusicSourceId.AUDIUS -> Guide(
                actionLabel = "Audius'ta anahtar al",
                freeNote = "Anahtar OPSİYONEL — anahtarsız da çalışır",
                steps = listOf(
                    "⚠️ Anahtar gerekmiyor: Audius anahtarsız çalışır",
                    "Daha yüksek limit için butona dokun",
                    "Audius hesabına giriş yap (yoksa kayıt ol)",
                    "Ayarlar → **Developer Apps** bölümüne in",
                    "'Create new app' → ad: Kitsugi",
                    "Oluşan **API Key**'i kopyala (API Secret'ı değil)",
                    "Buraya yapıştır → 'Doğrula'"
                ),
                formatHint = "uzun harf-rakam dizisi. Yalnızca API Key; Secret'ı verme."
            )

            // ── Openverse ───────────────────────────────────────────────
            // Doğrulandı: OAuth2 client_id + client_secret → Bearer token
            // Anahtarsız: 20/dk, 200/gün. Kayıtlı: 100/dk, 10.000/gün
            com.kitsugi.animelist.data.music.MusicSourceId.OPENVERSE -> Guide(
                actionLabel = "Openverse'da uygulama kaydet",
                freeNote = "Anahtarsız çalışır · kayıtla limit 500 kat artar",
                steps = listOf(
                    "⚠️ Anahtar gerekmiyor: günde 200 istek ücretsiz",
                    "Daha yüksek limit (10.000/gün) için butona dokun",
                    "Formu doldur: ad, açıklama, e-posta",
                    "Kayıt sonrası **client_id** ve **client_secret** verilir",
                    "E-postanı doğrula (yoksa anonim limit geçerli kalır)",
                    "Buraya client_id'yi yapıştır → 'Doğrula'"
                ),
                formatHint = "OAuth2 client_id. Token değil — kalıcı kimlik."
            )

            else -> Guide(
                actionLabel = "Siteye git",
                freeNote = "Anahtar gerekmez",
                steps = listOf("Bu kaynak için anahtar gerekmiyor."),
                formatHint = "—"
            )
        }
}
