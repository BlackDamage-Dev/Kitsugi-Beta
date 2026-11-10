package com.kitsugi.animelist.ui.tv.settings

import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.data.fandom.FandomRepository
import com.kitsugi.animelist.data.fandom.FandomWikiPrefs
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.KitsugiTvTokens
import com.kitsugi.animelist.ui.utils.KitsugiScrollDefaults
import com.kitsugi.animelist.ui.utils.dpadVerticalFastScroll
import com.kitsugi.animelist.ui.utils.tvClickable
import com.kitsugi.animelist.ui.app.AddonViewModel
import com.kitsugi.animelist.ui.app.AppViewModel
import com.kitsugi.animelist.ui.app.AuthViewModel
import com.kitsugi.animelist.data.local.MediaEntryRepository
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.ui.components.KitsugiTvQrLoginDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun TvSettingsScreen(
    addonViewModel: AddonViewModel = viewModel(),
    appViewModel: AppViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel(),
    settingsDataStore: SettingsDataStore,
    mediaRepository: MediaEntryRepository,
    mediaEntries: List<MediaEntry>,
    onNavigateToAddons: () -> Unit = {},
    onNavigateToMangaExtension: () -> Unit = {},
    onNavigateToMangaSourceHealth: () -> Unit = {},
    /** Manga kitaplığını açar (mobil ile aynı ekran). */
    onNavigateToMangaLibrary: () -> Unit = {},
    onNavigateToCompanion: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by settingsDataStore.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
    // Fandom Wiki tercihleri ayrı bir depoda (FandomWikiPrefs) tutulur.
    FandomWikiPrefs.ensureLoaded(context)
    val fandomPrefs by FandomWikiPrefs.stateFlow.collectAsStateWithLifecycle()

    // Auth durumunu LaunchedEffect ile yükle
    LaunchedEffect(Unit) { authViewModel.refreshAuthState() }
    val isAniListConnected = authViewModel.isAniListConnected
    val isMalConnected = authViewModel.isMalConnected
    val isSimklConnected = authViewModel.isSimklConnected
    val isAniListImportRunning = authViewModel.isAniListImportRunning
    val isMalImportRunning = authViewModel.isMalImportRunning
    val isSimklImportRunning = authViewModel.isSimklImportRunning
    val isCrossSyncRunning = authViewModel.isCrossSyncRunning

    var selectedTab by remember { mutableStateOf(0) }
    var showTvQrDialog by remember { mutableStateOf(false) }

    // FocusRequester: sol panel → sağ içerik paneli arası DPAD geçişi
    val contentPanelFocusRequester = remember { FocusRequester() }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = KitsugiTvTokens.Spacing.screenHorizontal,
                vertical = KitsugiTvTokens.Spacing.screenVertical
            ),
        horizontalArrangement = Arrangement.spacedBy(KitsugiTvTokens.Spacing.screenHorizontal)
    ) {
        // Left Column: Categories
        Column(
            modifier = Modifier
                .width(KitsugiTvTokens.Layout.sidebarExpandedWidth - 40.dp)
                .fillMaxHeight()
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(KitsugiTvTokens.Spacing.gridRowGap)
        ) {
            Text(
                text = stringResource(R.string.tab_settings),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = KitsugiTvTokens.Spacing.contentPadding)
            )

            val categories = listOf(stringResource(R.string.tv_appearance), stringResource(R.string.tv_stream_addons), stringResource(R.string.settings_player_title), stringResource(R.string.settings_section_account))
            categories.forEachIndexed { index, title ->
                var isFocused by remember { mutableStateOf(false) }
                val isSelected = selectedTab == index

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else Color.Transparent,
                            RoundedCornerShape(KitsugiTvTokens.Spacing.sm)
                        )
                        .focusProperties {
                            right = contentPanelFocusRequester
                        }
                        .tvClickable(shape = RoundedCornerShape(KitsugiTvTokens.Spacing.sm)) { selectedTab = index }
                        .onFocusChanged {
                            isFocused = it.isFocused
                            if (it.isFocused) {
                                selectedTab = index
                            }
                        }
                        .padding(
                            horizontal = KitsugiTvTokens.Spacing.contentPadding,
                            vertical = KitsugiTvTokens.Spacing.md
                        )
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        val tvSpec = KitsugiScrollDefaults.rememberTvCenteredSpec()
        val settingsListState = rememberLazyListState()

        // Right Column: Content panel based on selectedTab
        CompositionLocalProvider(LocalBringIntoViewSpec provides tvSpec) {
            LazyColumn(
                state = settingsListState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .focusRequester(contentPanelFocusRequester)
                    .focusRestorer()
                    .focusGroup()
                    .background(Color.White.copy(alpha = 0.03f), KitsugiTvTokens.Shapes.dialog as RoundedCornerShape)
                    .padding(KitsugiTvTokens.Spacing.rowGap)
                    .dpadVerticalFastScroll(scrollableState = settingsListState),
                verticalArrangement = Arrangement.spacedBy(KitsugiTvTokens.Spacing.itemGap)
            ) {
                when (selectedTab) {
                    0 -> {
                        item {
                            Text(
                                text = stringResource(R.string.tv_appearance_settings),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        item {
                            TvSettingsToggleRow(
                                title = stringResource(R.string.tv_show_adult),
                                description = stringResource(R.string.tv_show_adult_desc),
                                checked = settings.showAdultContent,
                                onCheckedChange = { checked ->
                                    scope.launch { settingsDataStore.setShowAdultContent(checked) }
                                }
                            )
                        }

                        item {
                            TvSettingsToggleRow(
                                title = stringResource(R.string.settings_hide_scores),
                                description = stringResource(R.string.tv_hide_scores_desc),
                                checked = settings.hideScores,
                                onCheckedChange = { checked ->
                                    scope.launch { settingsDataStore.setHideScores(checked) }
                                }
                            )
                        }

                        item {
                            TvSettingsToggleRow(
                                title = stringResource(R.string.tv_show_logos),
                                description = stringResource(R.string.tv_show_logos_desc),
                                checked = settings.showAnimeLogos,
                                onCheckedChange = { checked ->
                                    scope.launch { settingsDataStore.setShowAnimeLogos(checked) }
                                }
                            )
                        }

                        // ── Fandom Wiki (karakter detayındaki Wiki sekmesi) ──
                        item {
                            Text(
                                text = stringResource(R.string.fandom_settings_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }

                        item {
                            TvSettingsToggleRow(
                                title = stringResource(R.string.fandom_settings_enabled),
                                description = stringResource(R.string.fandom_settings_enabled_desc),
                                checked = fandomPrefs.enabled,
                                onCheckedChange = { checked ->
                                    FandomWikiPrefs.update(context) { it.copy(enabled = checked) }
                                }
                            )
                        }

                        if (fandomPrefs.enabled) {
                            item {
                                TvSettingsToggleRow(
                                    title = stringResource(R.string.fandom_settings_name),
                                    description = stringResource(R.string.fandom_settings_name_desc),
                                    checked = fandomPrefs.nameFallback,
                                    onCheckedChange = { checked ->
                                        FandomWikiPrefs.update(context) { it.copy(nameFallback = checked) }
                                    }
                                )
                            }

                            item {
                                TvSettingsToggleRow(
                                    title = stringResource(R.string.fandom_settings_spoiler),
                                    description = stringResource(R.string.fandom_settings_spoiler_desc),
                                    checked = fandomPrefs.spoilerGuard,
                                    onCheckedChange = { checked ->
                                        FandomWikiPrefs.update(context) { it.copy(spoilerGuard = checked) }
                                    }
                                )
                            }

                            item {
                                TvSettingsToggleRow(
                                    title = stringResource(R.string.fandom_settings_turkish),
                                    description = stringResource(R.string.fandom_settings_turkish_desc),
                                    checked = fandomPrefs.preferTurkish,
                                    onCheckedChange = { checked ->
                                        FandomWikiPrefs.update(context) { it.copy(preferTurkish = checked) }
                                    }
                                )
                            }
                        }

                        item {
                            TvSettingsActionRow(
                                title = stringResource(R.string.fandom_settings_clear_cache),
                                description = stringResource(R.string.fandom_settings_clear_cache_desc),
                                actionText = stringResource(R.string.fandom_settings_clear_action),
                                onClick = {
                                    scope.launch {
                                        FandomRepository.clearAll(context)
                                        android.widget.Toast.makeText(
                                            context,
                                            context.getString(R.string.fandom_settings_cache_cleared),
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        }

                        item {
                            val layoutText = when (settings.selectedHomeLayoutId) {
                                "classic" -> stringResource(R.string.option_home_layout_classic)
                                "modern" -> stringResource(R.string.option_home_layout_modern)
                                "grid" -> stringResource(R.string.option_home_layout_grid)
                                else -> stringResource(R.string.option_home_layout_classic)
                            }
                            TvSettingsActionRow(
                                title = stringResource(R.string.tv_home_layout),
                                description = stringResource(R.string.tv_home_layout_desc, layoutText),
                                actionText = stringResource(R.string.tv_change),
                                onClick = {
                                    val nextLayout = when (settings.selectedHomeLayoutId) {
                                        "classic" -> "modern"
                                        "modern" -> "grid"
                                        else -> "classic"
                                    }
                                    scope.launch { settingsDataStore.setSelectedHomeLayoutId(nextLayout) }
                                }
                            )
                        }

                        item {
                            val langText = when (settings.titleLanguage) {
                                "ENGLISH" -> stringResource(R.string.option_title_lang_english)
                                "NATIVE" -> stringResource(R.string.option_title_lang_native)
                                "JAPANESE_STAFF" -> stringResource(R.string.option_title_lang_japanese_staff)
                                else -> stringResource(R.string.option_title_lang_romaji)
                            }
                            TvSettingsActionRow(
                                title = stringResource(R.string.settings_anime_title_language),
                                description = stringResource(R.string.tv_title_lang_desc, langText),
                                actionText = stringResource(R.string.tv_change),
                                onClick = {
                                    val nextLang = when (settings.titleLanguage) {
                                        "ROMAJI" -> "ENGLISH"
                                        "ENGLISH" -> "NATIVE"
                                        "NATIVE" -> "JAPANESE_STAFF"
                                        else -> "ROMAJI"
                                    }
                                    scope.launch { settingsDataStore.setTitleLanguage(nextLang) }
                                }
                            )
                        }

                        item {
                            val tmdbLangText = when (settings.tmdbLanguage.lowercase()) {
                                "tr" -> stringResource(R.string.pd_lang_tr)
                                "en" -> "English"
                                "ja" -> "日本語"
                                else -> settings.tmdbLanguage.uppercase()
                            }
                            TvSettingsActionRow(
                                title = stringResource(R.string.settings_movies_title_language),
                                description = stringResource(R.string.tv_tmdb_lang_desc, tmdbLangText),
                                actionText = stringResource(R.string.tv_change),
                                onClick = {
                                    val nextLang = when (settings.tmdbLanguage.lowercase()) {
                                        "tr" -> "en"
                                        "en" -> "ja"
                                        else -> "tr"
                                    }
                                    scope.launch { settingsDataStore.setTmdbLanguage(nextLang) }
                                }
                            )
                        }

                        item {
                            val formatText = when (settings.scoreFormat) {
                                "POINT_100" -> "100 Puan"
                                "POINT_10_DECIMAL" -> stringResource(R.string.tv_score_10)
                                "POINT_5" -> stringResource(R.string.tv_score_5)
                                "POINT_3" -> stringResource(R.string.tv_score_3)
                                "STARS" -> stringResource(R.string.option_score_stars)
                                else -> "10 Puan"
                            }
                            TvSettingsActionRow(
                                title = stringResource(R.string.tv_score_format),
                                description = stringResource(R.string.tv_score_format_desc, formatText),
                                actionText = stringResource(R.string.tv_change),
                                onClick = {
                                    val nextFormat = when (settings.scoreFormat) {
                                        "POINT_10" -> "POINT_100"
                                        "POINT_100" -> "POINT_10_DECIMAL"
                                        "POINT_10_DECIMAL" -> "POINT_5"
                                        "POINT_5" -> "POINT_3"
                                        "POINT_3" -> "STARS"
                                        else -> "POINT_10"
                                    }
                                    scope.launch { settingsDataStore.setScoreFormat(nextFormat) }
                                }
                            )
                        }
                    }
                    1 -> {
                        item {
                            Text(
                                text = stringResource(R.string.tv_stream_addons),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        item {
                            TvSettingsToggleRow(
                                title = stringResource(R.string.tv_tmdb_enrich),
                                description = stringResource(R.string.tv_tmdb_enrich_desc),
                                checked = settings.tmdbEnabled,
                                onCheckedChange = { checked ->
                                    scope.launch { settingsDataStore.setTmdbEnabled(checked) }
                                }
                            )
                        }

                        item {
                            TvSettingsToggleRow(
                                title = stringResource(R.string.tv_aniskip),
                                description = stringResource(R.string.tv_aniskip_desc),
                                checked = settings.aniSkipEnabled,
                                onCheckedChange = { checked ->
                                    scope.launch { settingsDataStore.setAniSkipEnabled(checked) }
                                }
                            )
                        }

                        item {
                            TvSettingsActionRow(
                                title = stringResource(R.string.tv_addon_pool),
                                description = stringResource(R.string.tv_addon_pool_desc),
                                actionText = stringResource(R.string.tv_manage),
                                onClick = onNavigateToAddons
                            )
                        }

                        item {
                            TvSettingsActionRow(
                                title = stringResource(R.string.tv_manga_addons),
                                description = stringResource(R.string.tv_manga_addons_desc),
                                actionText = stringResource(R.string.tv_manage),
                                onClick = onNavigateToMangaExtension
                            )
                        }

                        item {
                            TvSettingsActionRow(
                                title = stringResource(R.string.tv_manga_health),
                                description = stringResource(R.string.tv_manga_health_desc),
                                actionText = stringResource(R.string.tv_view),
                                onClick = onNavigateToMangaSourceHealth
                            )
                        }

                        item {
                            // Manga kitaplığı: TV'de de takip edilen serilere erişim.
                            TvSettingsActionRow(
                                title = stringResource(R.string.manga_library_title),
                                description = stringResource(R.string.manga_library_empty_body),
                                actionText = stringResource(R.string.tv_view),
                                onClick = onNavigateToMangaLibrary
                            )
                        }

                        item {
                            TvSettingsActionRow(
                                title = stringResource(R.string.tv_phone_manage),
                                description = stringResource(R.string.tv_phone_manage_desc),
                                actionText = stringResource(R.string.tv_start),
                                onClick = onNavigateToCompanion
                            )
                        }
                    }
                    2 -> {
                        item {
                            Text(
                                text = stringResource(R.string.pd_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        item {
                            val playerText = when (settings.playerPreference.uppercase()) {
                                "EXTERNAL" -> stringResource(R.string.player_btn_external)
                                else -> stringResource(R.string.tv_mpv_player)
                            }
                            TvSettingsActionRow(
                                title = stringResource(R.string.tv_player_pref),
                                description = stringResource(R.string.tv_player_pref_desc, playerText),
                                actionText = stringResource(R.string.tv_change),
                                onClick = {
                                    val nextPreference = when (settings.playerPreference.uppercase()) {
                                        "MPV" -> "EXTERNAL"
                                        else -> "MPV"
                                    }
                                    scope.launch { settingsDataStore.setPlayerPreference(nextPreference) }
                                }
                            )
                        }

                        if (settings.playerPreference.uppercase() == "EXTERNAL") {
                            val availablePlayers = com.kitsugi.animelist.core.player.ExternalPlayerPackages.players
                            val currentIndex = availablePlayers.indexOfFirst { it.packageName == settings.preferredExternalPlayerPackage }.coerceAtLeast(0)
                            val currentExternalPlayerName = availablePlayers[currentIndex].name
                            item {
                                TvSettingsActionRow(
                                    title = stringResource(R.string.player_preferred_external),
                                    description = stringResource(R.string.tv_external_player_desc, currentExternalPlayerName),
                                    actionText = stringResource(R.string.tv_change),
                                    onClick = {
                                        val nextIndex = (currentIndex + 1) % availablePlayers.size
                                        val nextPlayer = availablePlayers[nextIndex]
                                        scope.launch {
                                            settingsDataStore.setPreferredExternalPlayerPackage(nextPlayer.packageName)
                                        }
                                    }
                                )
                            }
                        }

                        item {
                            TvSettingsToggleRow(
                                title = stringResource(R.string.tv_auto_play),
                                description = stringResource(R.string.tv_auto_next_desc),
                                checked = settings.isAutoplayEnabled,
                                onCheckedChange = { checked ->
                                    scope.launch { settingsDataStore.setAutoplayEnabled(checked) }
                                }
                            )
                        }

                        item {
                            TvSettingsToggleRow(
                                title = stringResource(R.string.tv_sub_bold),
                                description = stringResource(R.string.tv_sub_bold_desc),
                                checked = settings.subtitleBold,
                                onCheckedChange = { checked ->
                                    scope.launch { settingsDataStore.setSubtitleBold(checked) }
                                }
                            )
                        }

                        // Secure DNS Configuration (Cycle through options)
                        item {
                            val dnsText = when (settings.dnsChoice) {
                                1 -> "Cloudflare DoH"
                                2 -> "Google DoH"
                                3 -> "AdGuard DoH"
                                else -> stringResource(R.string.player_system_default)
                            }
                            TvSettingsActionRow(
                                title = stringResource(R.string.tv_doh),
                                description = stringResource(R.string.tv_dns_desc, dnsText),
                                actionText = stringResource(R.string.tv_change),
                                onClick = {
                                    val nextChoice = (settings.dnsChoice + 1) % 4
                                    appViewModel.updateDnsChoice(nextChoice, settingsDataStore)
                                }
                            )
                        }

                        item {
                            val skipDur = settings.skipIntroDurationSec
                            TvSettingsActionRow(
                                title = stringResource(R.string.tv_intro_skip),
                                description = stringResource(R.string.tv_intro_skip_desc, skipDur),
                                actionText = stringResource(R.string.tv_change),
                                onClick = {
                                    val nextSkip = when (skipDur) {
                                        0 -> 5
                                        5 -> 10
                                        10 -> 15
                                        15 -> 30
                                        30 -> 60
                                        60 -> 90
                                        else -> 0
                                    }
                                    scope.launch { settingsDataStore.setSkipIntroDurationSec(nextSkip) }
                                }
                            )
                        }

                        item {
                            val subSize = settings.defaultSubtitleSize
                            TvSettingsActionRow(
                                title = stringResource(R.string.sub_default_size),
                                description = stringResource(R.string.tv_sub_size_desc, subSize),
                                actionText = stringResource(R.string.tv_change),
                                onClick = {
                                    val nextSize = when (subSize) {
                                        12 -> 14
                                        14 -> 16
                                        16 -> 18
                                        18 -> 20
                                        20 -> 24
                                        24 -> 12
                                        else -> 16
                                    }
                                    scope.launch { settingsDataStore.setDefaultSubtitleSize(nextSize) }
                                }
                            )
                        }

                        item {
                            val prefLang = settings.preferredSubtitleLanguages
                            val prefLangText = when (prefLang) {
                                "tr,en" -> stringResource(R.string.tv_sub_lang_tr_en)
                                "tr" -> stringResource(R.string.tv_sub_lang_tr)
                                "en" -> stringResource(R.string.tv_sub_lang_en)
                                else -> prefLang
                            }
                            TvSettingsActionRow(
                                title = stringResource(R.string.tv_pref_lang),
                                description = stringResource(R.string.tv_pref_lang_desc, prefLangText),
                                actionText = stringResource(R.string.tv_change),
                                onClick = {
                                    val nextLang = when (prefLang) {
                                        "tr,en" -> "tr"
                                        "tr" -> "en"
                                        else -> "tr,en"
                                    }
                                    scope.launch { settingsDataStore.setPreferredSubtitleLanguages(nextLang) }
                                }
                            )
                        }

                        item {
                            val startupMode = settings.addonSubtitleStartupMode
                            val startupModeText = when (startupMode) {
                                "ALL_SUBTITLES" -> stringResource(R.string.sub_load_all)
                                "PREFERRED_ONLY" -> stringResource(R.string.sub_load_preferred)
                                else -> startupMode
                            }
                            TvSettingsActionRow(
                                title = stringResource(R.string.sub_load_mode),
                                description = stringResource(R.string.tv_sub_startup_desc, startupModeText),
                                actionText = stringResource(R.string.tv_change),
                                onClick = {
                                    val nextMode = when (startupMode) {
                                        "ALL_SUBTITLES" -> "PREFERRED_ONLY"
                                        else -> "ALL_SUBTITLES"
                                    }
                                    scope.launch { settingsDataStore.setAddonSubtitleStartupMode(nextMode) }
                                }
                            )
                        }

                        item {
                            val afrText = when (settings.frameRateMatchingMode) {
                                com.kitsugi.animelist.data.settings.FrameRateMatchingMode.START -> stringResource(R.string.tv_sub_startup_only_start)
                                com.kitsugi.animelist.data.settings.FrameRateMatchingMode.START_STOP -> stringResource(R.string.player_afr_start_stop)
                                else -> stringResource(R.string.player_afr_off)
                            }
                            TvSettingsActionRow(
                                title = stringResource(R.string.tv_afr),
                                description = stringResource(R.string.tv_afr_desc, afrText),
                                actionText = stringResource(R.string.tv_change),
                                onClick = {
                                    val nextMode = when (settings.frameRateMatchingMode) {
                                        com.kitsugi.animelist.data.settings.FrameRateMatchingMode.OFF -> com.kitsugi.animelist.data.settings.FrameRateMatchingMode.START
                                        com.kitsugi.animelist.data.settings.FrameRateMatchingMode.START -> com.kitsugi.animelist.data.settings.FrameRateMatchingMode.START_STOP
                                        com.kitsugi.animelist.data.settings.FrameRateMatchingMode.START_STOP -> com.kitsugi.animelist.data.settings.FrameRateMatchingMode.OFF
                                    }
                                    scope.launch { settingsDataStore.setFrameRateMatchingMode(nextMode) }
                                }
                            )
                        }

                        item {
                            TvSettingsToggleRow(
                                title = stringResource(R.string.tv_res_match),
                                description = stringResource(R.string.tv_res_match_desc),
                                checked = settings.resolutionMatchingEnabled,
                                onCheckedChange = { checked ->
                                    scope.launch { settingsDataStore.setResolutionMatchingEnabled(checked) }
                                }
                            )
                        }



                        // ─── T1-10 · TV Parity – Ek Oynatıcı Ayarları ───────────────────────

                        item {
                            TvSettingsToggleRow(
                                title = stringResource(R.string.tv_still_watching),
                                description = stringResource(R.string.tv_still_watching_desc),
                                checked = settings.stillWatchingEnabled,
                                onCheckedChange = { checked ->
                                    scope.launch { settingsDataStore.setStillWatchingEnabled(checked) }
                                }
                            )
                        }

                        if (settings.stillWatchingEnabled) {
                            item {
                                val thresholdMin = settings.stillWatchingThresholdMinutes
                                TvSettingsActionRow(
                                    title = stringResource(R.string.player_inactivity_threshold),
                                    description = stringResource(R.string.tv_threshold_desc, thresholdMin),
                                    actionText = stringResource(R.string.tv_change),
                                    onClick = {
                                        val nextMin = when (thresholdMin) {
                                            10 -> 15
                                            15 -> 20
                                            20 -> 30
                                            30 -> 45
                                            45 -> 60
                                            else -> 10
                                        }
                                        scope.launch { settingsDataStore.setStillWatchingThresholdMinutes(nextMin) }
                                    }
                                )
                            }
                        }

                        item {
                            val postPlayText = when (settings.postPlayMode) {
                                "AUTOPLAY" -> stringResource(R.string.tv_auto_play)
                                "PROMPT" -> "Sor"
                                "MANUAL" -> "Manuel"
                                else -> stringResource(R.string.tv_auto_play)
                            }
                            TvSettingsActionRow(
                                title = stringResource(R.string.tv_post_play),
                                description = stringResource(R.string.tv_post_play_desc, postPlayText),
                                actionText = stringResource(R.string.tv_change),
                                onClick = {
                                    val nextMode = when (settings.postPlayMode) {
                                        "AUTOPLAY" -> "PROMPT"
                                        "PROMPT" -> "MANUAL"
                                        else -> "AUTOPLAY"
                                    }
                                    scope.launch { settingsDataStore.setPostPlayMode(nextMode) }
                                }
                            )
                        }

                        item {
                            val sessionLimit = settings.autoplaySessionLimit
                            val sessionText = if (sessionLimit == 0) stringResource(R.string.player_limit_unlimited) else stringResource(R.string.tv_session_n, sessionLimit)
                            TvSettingsActionRow(
                                title = stringResource(R.string.tv_session_limit),
                                description = stringResource(R.string.tv_session_limit_desc, sessionText),
                                actionText = stringResource(R.string.tv_change),
                                onClick = {
                                    val nextLimit = when (sessionLimit) {
                                        0 -> 1
                                        1 -> 2
                                        2 -> 3
                                        3 -> 5
                                        5 -> 10
                                        else -> 0
                                    }
                                    scope.launch { settingsDataStore.setAutoplaySessionLimit(nextLimit) }
                                }
                            )
                        }

                        item {
                            TvSettingsToggleRow(
                                title = stringResource(R.string.tv_preview_seek),
                                description = stringResource(R.string.tv_preview_seek_desc),
                                checked = settings.previewSeekbarEnabled,
                                onCheckedChange = { checked ->
                                    scope.launch { settingsDataStore.setPreviewSeekbarEnabled(checked) }
                                }
                            )
                        }



                        item {
                            TvSettingsToggleRow(
                                title = stringResource(R.string.settings_splash_animation_and_sound),
                                description = if (settings.splashAnimationEnabled) stringResource(R.string.tv_splash_on_desc) else stringResource(R.string.tv_splash_off_desc),
                                checked = settings.splashAnimationEnabled,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        settingsDataStore.setSplashAnimationEnabled(checked)
                                        settingsDataStore.setSplashSoundEnabled(checked)
                                    }
                                }
                            )
                        }
                    }
                    3 -> {
                        item {
                            Text(
                                text = stringResource(R.string.tv_section_account),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        // 1. AniList
                        item {
                            if (isAniListConnected) {
                                TvSettingsActionRow(
                                    title = stringResource(R.string.tv_account_anilist, settings.anilistUsername.ifBlank { stringResource(R.string.tv_connected) }),
                                    description = if (isAniListImportRunning) stringResource(R.string.tv_syncing) else stringResource(R.string.tv_import_anilist_desc),
                                    actionText = if (isAniListImportRunning) stringResource(R.string.tv_transferring) else stringResource(R.string.tv_sync),
                                    onClick = {
                                        if (!isAniListImportRunning) {
                                            authViewModel.importAniListAnimeList(mediaEntries, mediaRepository)
                                        }
                                    }
                                )
                            } else {
                                TvSettingsActionRow(
                                    title = stringResource(R.string.tv_connect_anilist),
                                    description = stringResource(R.string.tv_connect_qr_desc),
                                    actionText = stringResource(R.string.tv_connect),
                                    onClick = {
                                        showTvQrDialog = true
                                    }
                                )
                            }
                        }

                        if (isAniListConnected) {
                            item {
                                TvSettingsActionRow(
                                    title = stringResource(R.string.tv_disconnect_anilist),
                                    description = stringResource(R.string.tv_disconnect_anilist_desc),
                                    actionText = stringResource(R.string.tv_disconnect),
                                    onClick = {
                                        authViewModel.disconnectExternalAccount("anilist")
                                    }
                                )
                            }
                        }

                        // 2. MyAnimeList
                        item {
                            if (isMalConnected) {
                                TvSettingsActionRow(
                                    title = stringResource(R.string.tv_account_mal, settings.malUsername.ifBlank { stringResource(R.string.tv_connected) }),
                                    description = if (isMalImportRunning) stringResource(R.string.tv_syncing) else stringResource(R.string.tv_import_mal_desc),
                                    actionText = if (isMalImportRunning) stringResource(R.string.tv_transferring) else stringResource(R.string.tv_sync),
                                    onClick = {
                                        if (!isMalImportRunning) {
                                            authViewModel.importMalAnimeList(mediaEntries, mediaRepository)
                                        }
                                    }
                                )
                            } else {
                                TvSettingsActionRow(
                                    title = stringResource(R.string.tv_connect_mal),
                                    description = stringResource(R.string.tv_connect_qr_desc),
                                    actionText = stringResource(R.string.tv_connect),
                                    onClick = {
                                        showTvQrDialog = true
                                    }
                                )
                            }
                        }

                        if (isMalConnected) {
                            item {
                                TvSettingsActionRow(
                                    title = stringResource(R.string.tv_disconnect_mal),
                                    description = stringResource(R.string.tv_disconnect_mal_desc),
                                    actionText = stringResource(R.string.tv_disconnect),
                                    onClick = {
                                        authViewModel.disconnectExternalAccount("mal")
                                    }
                                )
                            }
                        }

                        // 3. Simkl
                        item {
                            if (isSimklConnected) {
                                TvSettingsActionRow(
                                    title = stringResource(R.string.tv_account_simkl, settings.simklUsername.ifBlank { stringResource(R.string.tv_connected) }),
                                    description = if (isSimklImportRunning) stringResource(R.string.tv_syncing) else stringResource(R.string.tv_import_simkl_desc),
                                    actionText = if (isSimklImportRunning) stringResource(R.string.tv_transferring) else stringResource(R.string.tv_sync),
                                    onClick = {
                                        if (!isSimklImportRunning) {
                                            authViewModel.importSimklList(mediaEntries, mediaRepository)
                                        }
                                    }
                                )
                            } else {
                                TvSettingsActionRow(
                                    title = stringResource(R.string.tv_connect_simkl),
                                    description = stringResource(R.string.tv_connect_qr_desc),
                                    actionText = stringResource(R.string.tv_connect),
                                    onClick = {
                                        showTvQrDialog = true
                                    }
                                )
                            }
                        }

                        if (isSimklConnected) {
                            item {
                                TvSettingsActionRow(
                                    title = stringResource(R.string.tv_disconnect_simkl),
                                    description = stringResource(R.string.tv_disconnect_simkl_desc),
                                    actionText = stringResource(R.string.tv_disconnect),
                                    onClick = {
                                        authViewModel.disconnectExternalAccount("simkl")
                                    }
                                )
                            }
                        }

                        // 4. Çift Yönlü Eşitleme
                        if (isAniListConnected && isMalConnected) {
                            item {
                                TvSettingsActionRow(
                                    title = stringResource(R.string.tv_cross_sync),
                                    description = if (isCrossSyncRunning) stringResource(R.string.tv_cross_sync_running) else stringResource(R.string.tv_cross_sync_desc),
                                    actionText = if (isCrossSyncRunning) stringResource(R.string.tv_syncing2) else stringResource(R.string.tv_sync),
                                    onClick = {
                                        if (!isCrossSyncRunning) {
                                            authViewModel.syncPlatforms(mediaRepository)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }



    if (showTvQrDialog) {
        KitsugiTvQrLoginDialog(
            authViewModel = authViewModel,
            onDismiss = { showTvQrDialog = false }
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvSettingsActionRow(
    title: String,
    description: String,
    actionText: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isFocused) Color.White.copy(alpha = 0.08f) else Color.Transparent,
                KitsugiTvTokens.Shapes.posterCard as RoundedCornerShape
            )
            .tvClickable(shape = KitsugiTvTokens.Shapes.posterCard as RoundedCornerShape) { onClick() }
            .onFocusChanged { isFocused = it.isFocused }
            .padding(KitsugiTvTokens.Spacing.contentPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }

        Spacer(modifier = Modifier.width(KitsugiTvTokens.Spacing.contentPadding))

        Box(
            modifier = Modifier
                .width(100.dp)
                .height(36.dp)
                .clip(KitsugiTvTokens.Shapes.chip as RoundedCornerShape)
                .background(Color.White.copy(alpha = 0.1f))
                .border(
                    1.dp,
                    Color.White.copy(alpha = 0.2f),
                    KitsugiTvTokens.Shapes.chip as RoundedCornerShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = actionText,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvSettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isFocused) Color.White.copy(alpha = 0.08f) else Color.Transparent,
                KitsugiTvTokens.Shapes.posterCard as RoundedCornerShape
            )
            .tvClickable(shape = KitsugiTvTokens.Shapes.posterCard as RoundedCornerShape) { onCheckedChange(!checked) }
            .onFocusChanged { isFocused = it.isFocused }
            .padding(KitsugiTvTokens.Spacing.contentPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }

        Spacer(modifier = Modifier.width(KitsugiTvTokens.Spacing.contentPadding))

        com.kitsugi.animelist.ui.theme.gradient.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
    }
}

