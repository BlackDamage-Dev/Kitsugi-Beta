package com.kitsugi.animelist

import androidx.compose.runtime.Composable
import com.kitsugi.animelist.ui.screens.settings.SettingsScreen
import kotlinx.coroutines.launch
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

/**
 * Holds the full SettingsScreen(...) call. Split from SettingsScreenContent to keep
 * each JVM method under the 64 KB bytecode limit.
 */
@Composable
internal fun SettingsScreenExtras(ctx: SettingsContext) {
    SettingsScreen(params = ctx.buildSettingsParams())
}

// ─── Non-Composable Parameter Builder ─────────────────────────────────────────────

internal fun SettingsContext.buildSettingsParams() =
    com.kitsugi.animelist.ui.screens.settings.SettingsScreenParameters(
        general = com.kitsugi.animelist.ui.screens.settings.GeneralSettings(
            selectedThemeId = appSettings.selectedThemeId,
            showAdultContent = appSettings.showAdultContent,
            blurAdultMedia = appSettings.blurAdultMedia,
            selectedListLayoutId = appSettings.selectedListLayoutId,
            selectedHomeLayoutId = appSettings.selectedHomeLayoutId,
            onHomeLayoutSelected = { onHomeLayoutSelected(it) },
            titleLanguage = appSettings.titleLanguage,
            scoreFormat = appSettings.scoreFormat,
            scoreStep = appSettings.scoreStep,
            staffNameLanguage = appSettings.staffNameLanguage,
            separatedListStyle = appSettings.separatedListStyle,
            showLowPriority = appSettings.showLowPriority,
            priorityColorsJson = appSettings.priorityColorsJson,
            fuzzySearchEnabled = appSettings.fuzzySearchEnabled,
            separateNovelsManga = appSettings.separateNovelsManga,
            hideScores = appSettings.hideScores,
            showAnimeLogos = appSettings.showAnimeLogos,
            onThemeSelected = { onThemeSelected(it) },
            onAdultContentChanged = { onAdultContentChanged(it) },
            onBlurAdultMediaChanged = { onBlurAdultMediaChanged(it) },
            onListLayoutSelected = { onListLayoutSelected(it) },
            onTitleLanguageSelected = { onTitleLanguageSelected(it) },
            onScoreFormatSelected = { onScoreFormatSelected(it) },
            onHideScoresChanged = { onHideScoresChanged(it) },
            onShowAnimeLogosChanged = { onShowAnimeLogosChanged(it) },
            // ─── AniHyou paritesi: eksik içerik/liste ayarları ─────────────
            onStaffNameLanguageSelected = { onStaffNameLanguageSelected(it) },
            onScoreStepSelected = { onScoreStepSelected(it) },
            onSeparatedListStyleChanged = { onSeparatedListStyleChanged(it) },
            onShowLowPriorityChanged = { onShowLowPriorityChanged(it) },
            onPriorityColorsChanged = { onPriorityColorsChanged(it) },
            onFuzzySearchEnabledChanged = { onFuzzySearchEnabledChanged(it) },
            onSeparateNovelsMangaChanged = { onSeparateNovelsMangaChanged(it) },
            // ─── Liste ekleme otomasyonları ────────────────────────────────
            autoDateOnStatusChange = appSettings.autoDateOnStatusChange,
            onAutoDateOnStatusChangeChanged = { onAutoDateOnStatusChangeChanged(it) },
            autoProgressOnComplete = appSettings.autoProgressOnComplete,
            onAutoProgressOnCompleteChanged = { onAutoProgressOnCompleteChanged(it) },
            defaultAddStatus = appSettings.defaultAddStatus,
            onDefaultAddStatusSelected = { onDefaultAddStatusSelected(it) },
            themeMode = appSettings.themeMode,
            onThemeModeSelected = { onThemeModeSelected(it) },
            amoledBlack = appSettings.amoledBlack,
            onAmoledBlackChanged = { onAmoledBlackChanged(it) },
            cardFramesEnabled = appSettings.cardFramesEnabled,
            onCardFramesEnabledChanged = { onCardFramesEnabledChanged(it) },
            customAccentColor = appSettings.customAccentColor,
            onCustomAccentColorChanged = { onCustomAccentColorChanged(it) },
            customAccentColor2 = appSettings.customAccentColor2,
            onCustomAccentColor2Changed = { onCustomAccentColor2Changed(it) },
            customAccentGradientAngle = appSettings.customAccentGradientAngle,
            onCustomAccentGradientAngleChanged = { onCustomAccentGradientAngleChanged(it) },
            defaultTab = appSettings.defaultTab,
            onDefaultTabSelected = { onDefaultTabSelected(it) },
            appLanguage = appSettings.appLanguage,
            onAppLanguageSelected = { onAppLanguageSelected(it) },
            fixedNavBar = appSettings.fixedNavBar,
            onFixedNavBarChanged = { onFixedNavBarChanged(it) },
            airingNotificationsEnabled = appSettings.airingNotificationsEnabled,
            onAiringNotificationsChanged = { onAiringNotificationsChanged(it) },
            aniListNotificationsEnabled = appSettings.aniListNotificationsEnabled,
            onAniListNotificationsChanged = { onAniListNotificationsChanged(it) },
            malNotificationsEnabled = appSettings.malNotificationsEnabled,
            onMalNotificationsChanged = { onMalNotificationsChanged(it) },
            simklNotificationsEnabled = appSettings.simklNotificationsEnabled,
            onSimklNotificationsChanged = { onSimklNotificationsChanged(it) },
            kitsuNotificationsEnabled = appSettings.kitsuNotificationsEnabled,
            onKitsuNotificationsChanged = { onKitsuNotificationsChanged(it) },
            shikimoriNotificationsEnabled = appSettings.shikimoriNotificationsEnabled,
            onShikimoriNotificationsChanged = { onShikimoriNotificationsChanged(it) },
            tmdbNotificationsEnabled = appSettings.tmdbNotificationsEnabled,
            onTmdbNotificationsChanged = { onTmdbNotificationsChanged(it) },
            bangumiNotificationsEnabled = appSettings.bangumiNotificationsEnabled,
            onBangumiNotificationsChanged = { onBangumiNotificationsChanged(it) },
            isAniListConnected = authViewModel.isAniListConnected,
            isMalConnected = authViewModel.isMalConnected,
            isSimklConnected = authViewModel.isSimklConnected,
            isKitsuConnected = authViewModel.isKitsuConnected,
            isShikimoriConnected = authViewModel.isShikimoriConnected,
            isBangumiConnected = authViewModel.isBangumiConnected,
            isTmdbConnected = authViewModel.isTmdbConnected,
            notificationInterval = appSettings.notificationInterval,
            onNotificationIntervalChanged = { onNotificationIntervalChanged(it) },
            splashAnimationEnabled = appSettings.splashAnimationEnabled,
            splashSoundEnabled = appSettings.splashSoundEnabled,
            onSplashAnimationEnabledChanged = { onSplashAnimationEnabledChanged(it) },
            onSplashSoundEnabledChanged = { onSplashSoundEnabledChanged(it) },
            searchHistoryEnabled = appSettings.searchHistoryEnabled,
            onSearchHistoryEnabledChanged = { onSearchHistoryEnabledChanged(it) },
            // ─── T1-07 – Manga Okuyucu Varsayılan Ayarları ───────────────────────
            mangaReadingMode = appSettings.mangaReadingMode,
            mangaColorFilter = appSettings.mangaColorFilter,
            mangaFitMode = appSettings.mangaFitMode,
            mangaBrightness = appSettings.mangaBrightness,
            onMangaReadingModeSelected = { onMangaReadingModeSelected(it) },
            onMangaColorFilterSelected = { onMangaColorFilterSelected(it) },
            onMangaFitModeSelected = { onMangaFitModeSelected(it) },
            onMangaBrightnessChanged = { onMangaBrightnessChanged(it) },
            autoUpdateCheckEnabled = appSettings.autoUpdateCheckEnabled,
            onAutoUpdateCheckEnabledChanged = { onAutoUpdateCheckEnabledChanged(it) },
            onCheckForUpdatesClick = { onCheckForUpdatesClick() },
            customImageDownloadUri = appSettings.customImageDownloadUri,
            onCustomImageDownloadUriChanged = { onCustomImageDownloadUriChanged(it) },
            videoDownloadUri = appSettings.videoDownloadUri,
            onVideoDownloadUriChanged = { onVideoDownloadUriChanged(it) },
            downloaderPreference = appSettings.downloaderPreference,
            onDownloaderPreferenceSelected = { onDownloaderPreferenceSelected(it) }
        ),
        profile = com.kitsugi.animelist.ui.screens.settings.ProfileSettings(
            profileName = appSettings.profileName,
            listTitle = appSettings.listTitle,
            anilistUsername = appSettings.anilistUsername,
            malUsername = appSettings.malUsername,
            profileImageUri = appSettings.profileImageUri,
            bannerImageUri = appSettings.bannerImageUri,
            totalEntryCount = mediaEntries.size,
            backupText = backupText,
            importText = importText,
            importMode = importMode,
            isAniListConnected = authViewModel.isAniListConnected,
            isMalConnected = authViewModel.isMalConnected,
            isAniListImportRunning = authViewModel.isAniListImportRunning,
            isMalImportRunning = authViewModel.isMalImportRunning,
            isSimklConnected = authViewModel.isSimklConnected,
            simklUsername = appSettings.simklUsername,
            isSimklImportRunning = authViewModel.isSimklImportRunning,
            isSimklSessionExpired = authViewModel.isSimklSessionExpired,
            isAniListSessionExpired = authViewModel.isAniListSessionExpired,
            isMalSessionExpired = authViewModel.isMalSessionExpired,
            isKitsuSessionExpired = authViewModel.isKitsuSessionExpired,
            isShikimoriSessionExpired = authViewModel.isShikimoriSessionExpired,
            isBangumiSessionExpired = authViewModel.isBangumiSessionExpired,
            onProfileInfoSave = { name, title -> onProfileInfoSave(name, title) },
            onPickProfileImageClick = onPickProfileImageClick,
            onPickBannerImageClick = onPickBannerImageClick,
            onClearProfileImageClick = { onClearProfileImageClick() },
            onClearBannerImageClick = { onClearBannerImageClick() },
            onAniListAuthClick = { onAniListAuthClick() },
            onMalAuthClick = { onMalAuthClick() },
            onMalReconnectClick = { onMalReconnectClick() },
            malUnifiedStatus = authViewModel.malUnifiedStatus,
            onMalUnifiedFixClick = { authViewModel.ensureMalUnifiedSession() },
            onMalUnifiedProbe = { authViewModel.probeMalUnifiedSession() },
            onAniListImportClick = { onAniListImportClick() },
            onMalImportClick = { onMalImportClick() },
            onSimklAuthClick = { onSimklAuthClick() },
            onSimklImportClick = { onSimklImportClick() },
            isTmdbConnected = authViewModel.isTmdbConnected,
            tmdbUsername = authViewModel.tmdbUsername,
            isTmdbImportRunning = authViewModel.isTmdbImportRunning,
            onTmdbAuthClick = { onTmdbAuthClick() },
            onTmdbImportClick = { authViewModel.importTmdbWatchlist(mediaEntries, mediaRepository) },
            syncEnabledTmdb = appSettings.syncEnabledTmdb,
            onSyncEnabledTmdbChanged = { onSyncEnabledTmdbChanged(it) },
            onDeleteAllEntries = { onDeleteAllEntries() },
            onCreateBackupText = { onCreateBackupText() },
            onImportBackupText = { onImportBackupText() },
            onClearBackupText = { appViewModel.clearBackupText() },
            onClearImportText = { appViewModel.clearImportText() },
            onExportBackupFileClick = onExportBackupFileClick,
            onImportBackupFileClick = onImportBackupFileClick,
            isCrossSyncRunning = authViewModel.isCrossSyncRunning,
            crossSyncState = authViewModel.crossSyncState,
            onCrossSyncClick = { onCrossSyncClick() },
            onCrossSyncCancel = { authViewModel.cancelCrossSync() },
            onImportModeChange = { appViewModel.updateImportMode(it) },
            onImportTextChange = { appViewModel.updateImportText(it) },
            syncEnabledAnilist = appSettings.syncEnabledAnilist,
            syncEnabledMal = appSettings.syncEnabledMal,
            syncEnabledSimkl = appSettings.syncEnabledSimkl,
            onSyncEnabledAnilistChanged = { onSyncEnabledAnilistChanged(it) },
            onSyncEnabledMalChanged = { onSyncEnabledMalChanged(it) },
            onSyncEnabledSimklChanged = { onSyncEnabledSimklChanged(it) },
            isKitsuConnected = authViewModel.isKitsuConnected,
            kitsuUsername = appSettings.kitsuUsername,
            isKitsuImportRunning = authViewModel.isKitsuImportRunning,
            onKitsuAuthClick = { authViewModel.disconnectExternalAccount("kitsu") },
            onKitsuImportClick = { authViewModel.importKitsuList(mediaEntries, mediaRepository, com.kitsugi.animelist.data.sync.ListRefreshMode.USER_FORCE) },
            syncEnabledKitsu = appSettings.syncEnabledKitsu,
            onSyncEnabledKitsuChanged = { onSyncEnabledKitsuChanged(it) },
            onLoginKitsu = { username, password, onComplete ->
                authViewModel.loginKitsu(
                    username = username,
                    password = password,
                    onSuccess = { onComplete(true, null) },
                    onError = { onComplete(false, it) }
                )
            },
            isShikimoriConnected = authViewModel.isShikimoriConnected,
            shikimoriUsername = appSettings.shikimoriUsername,
            isShikimoriImportRunning = authViewModel.isShikimoriImportRunning,
            onShikimoriAuthClick = { authViewModel.disconnectExternalAccount("shikimori") },
            onShikimoriImportClick = { authViewModel.importShikimoriList(mediaEntries, mediaRepository, com.kitsugi.animelist.data.sync.ListRefreshMode.USER_FORCE) },
            syncEnabledShikimori = appSettings.syncEnabledShikimori,
            onSyncEnabledShikimoriChanged = { onSyncEnabledShikimoriChanged(it) },
            onLoginShikimori = { clientId, clientSecret, authCode, onComplete ->
                authViewModel.loginShikimori(
                    clientId = clientId,
                    clientSecret = clientSecret,
                    authCode = authCode,
                    onSuccess = { onComplete(true, null) },
                    onError = { onComplete(false, it) }
                )
            },
            isBangumiConnected = authViewModel.isBangumiConnected,
            bangumiUsername = appSettings.bangumiUsername,
            isBangumiImportRunning = authViewModel.isBangumiImportRunning,
            onBangumiAuthClick = { authViewModel.disconnectExternalAccount("bangumi") },
            onBangumiImportClick = { authViewModel.importBangumiList(mediaEntries, mediaRepository, com.kitsugi.animelist.data.sync.ListRefreshMode.USER_FORCE) },
            syncEnabledBangumi = appSettings.syncEnabledBangumi,
            onSyncEnabledBangumiChanged = { onSyncEnabledBangumiChanged(it) },
            onLoginBangumi = { clientId, clientSecret, authCode, onComplete ->
                authViewModel.loginBangumi(
                    clientId = clientId,
                    clientSecret = clientSecret,
                    authCode = authCode,
                    onSuccess = { onComplete(true, null) },
                    onError = { onComplete(false, it) }
                )
            }
        ),
        player = com.kitsugi.animelist.ui.screens.settings.PlayerSettings(
            playerPreference = appSettings.playerPreference,
            isAutoplayEnabled = appSettings.isAutoplayEnabled,
            skipIntroDurationSec = appSettings.skipIntroDurationSec,
            defaultSubtitleSize = appSettings.defaultSubtitleSize,
            defaultSubtitleColor = appSettings.defaultSubtitleColor,
            subtitleBold = appSettings.subtitleBold,
            subtitleOutlineEnabled = appSettings.subtitleOutlineEnabled,
            defaultAudioBoost = appSettings.defaultAudioBoost,
            defaultAudioDelayMs = appSettings.defaultAudioDelayMs,
            minBufferMs = appSettings.minBufferMs,
            maxBufferMs = appSettings.maxBufferMs,
            bufferForPlaybackMs = appSettings.bufferForPlaybackMs,
            bufferForPlaybackAfterRebufferMs = appSettings.bufferForPlaybackAfterRebufferMs,
            backBufferDurationMs = appSettings.backBufferDurationMs,
            dv7HandlingMode = appSettings.dv7HandlingMode,
            stripHdr10PlusSei = appSettings.stripHdr10PlusSei,
            preferredSubtitleLanguages = appSettings.preferredSubtitleLanguages,
            addonSubtitleStartupMode = appSettings.addonSubtitleStartupMode,
            onPlayerPreferenceSelected = { playerSettingsViewModel.updatePlayerPreference(it) },
            onAutoplayEnabledChanged = { playerSettingsViewModel.updateAutoplayEnabled(it) },
            onSkipIntroDurationSecSelected = { playerSettingsViewModel.updateSkipIntroDurationSec(it) },
            onDefaultSubtitleSizeSelected = { playerSettingsViewModel.updateDefaultSubtitleSize(it) },
            onDefaultSubtitleColorSelected = { playerSettingsViewModel.updateDefaultSubtitleColor(it) },
            onSubtitleBoldChanged = { playerSettingsViewModel.updateSubtitleBold(it) },
            onSubtitleOutlineEnabledChanged = { playerSettingsViewModel.updateSubtitleOutline(it) },
            onDefaultAudioBoostSelected = { playerSettingsViewModel.updateDefaultAudioBoost(it) },
            onDefaultAudioDelayMsSelected = { playerSettingsViewModel.updateDefaultAudioDelayMs(it) },
            onPreferredSubtitleLanguagesSelected = { playerSettingsViewModel.updatePreferredSubtitleLanguages(it) },
            onAddonSubtitleStartupModeSelected = { playerSettingsViewModel.updateAddonSubtitleStartupMode(it) },
            onBufferSettingsChanged = { min, max, playback, rebuffer, back -> playerSettingsViewModel.updateBufferSettings(min, max, playback, rebuffer, back) },
            onDv7HandlingModeSelected = { playerSettingsViewModel.updateDv7HandlingMode(it) },
            onStripHdr10PlusSeiChanged = { playerSettingsViewModel.updateStripHdr10PlusSei(it) },
            qualityProfileJson = appSettings.qualityProfileJson,
            onQualityProfileSelected = { playerSettingsViewModel.updateQualityProfileJson(it) },
            parallelRangeEnabled = appSettings.parallelRangeEnabled,
            onParallelRangeEnabledChanged = { playerSettingsViewModel.updateParallelRangeEnabled(it) },
            frameRateMatchingMode = appSettings.frameRateMatchingMode,
            resolutionMatchingEnabled = appSettings.resolutionMatchingEnabled,
            onFrameRateMatchingModeSelected = { playerSettingsViewModel.updateFrameRateMatchingMode(it) },
            onResolutionMatchingEnabledChanged = { playerSettingsViewModel.updateResolutionMatchingEnabled(it) },
            gestureVolumeEnabled = appSettings.gestureVolumeEnabled,
            gestureBrightnessEnabled = appSettings.gestureBrightnessEnabled,
            gestureZoomEnabled = appSettings.gestureZoomEnabled,
            doubleTapSeekSeconds = appSettings.doubleTapSeekSeconds,
            holdSpeedMultiplier = appSettings.holdSpeedMultiplier,
            gestureScrollSensitivity = appSettings.gestureScrollSensitivity,
            onGestureVolumeEnabledChanged = { playerSettingsViewModel.updateGestureVolumeEnabled(it) },
            onGestureBrightnessEnabledChanged = { playerSettingsViewModel.updateGestureBrightnessEnabled(it) },
            onGestureZoomEnabledChanged = { playerSettingsViewModel.updateGestureZoomEnabled(it) },
            onDoubleTapSeekSecondsSelected = { playerSettingsViewModel.updateDoubleTapSeekSeconds(it) },
            onHoldSpeedMultiplierSelected = { playerSettingsViewModel.updateHoldSpeedMultiplier(it) },
            onGestureScrollSensitivityChanged = { playerSettingsViewModel.updateGestureScrollSensitivity(it) },
            previewSeekbarEnabled = appSettings.previewSeekbarEnabled,
            onPreviewSeekbarEnabledChanged = { playerSettingsViewModel.updatePreviewSeekbarEnabled(it) },
            preferredExternalPlayerPackage = appSettings.preferredExternalPlayerPackage,
            onPreferredExternalPlayerPackageSelected = { playerSettingsViewModel.updatePreferredExternalPlayerPackage(it) },
            // T1.1 – Görüntü Oranı
            aspectMode = runCatching { com.kitsugi.animelist.core.player.PlayerAspectMode.valueOf(appSettings.aspectMode) }.getOrDefault(com.kitsugi.animelist.core.player.PlayerAspectMode.ORIGINAL),
            onAspectModeSelected = { playerSettingsViewModel.updateAspectMode(it) },
            liveHelperEnabled = appSettings.liveHelperEnabled,
            onLiveHelperEnabledChanged = { playerSettingsViewModel.updateLiveHelperEnabled(it) },
            enableAssExtractor = appSettings.enableAssExtractor,
            onEnableAssExtractorChanged = { playerSettingsViewModel.updateEnableAssExtractor(it) },
            // ─── T2.6 – Oynatıcı Başlık / Medya Bilgisi Görünürlüğü ────────────────
            showPlayerTitle = appSettings.showPlayerTitle,
            onShowPlayerTitleChanged = { onShowPlayerTitleChanged(it) },
            showPlayerResolution = appSettings.showPlayerResolution,
            onShowPlayerResolutionChanged = { onShowPlayerResolutionChanged(it) },
            showMediaInfo = appSettings.showMediaInfo,
            onShowMediaInfoChanged = { onShowMediaInfoChanged(it) },
            // ─── T1-01 – StillWatching + PostPlayMode + AutoplaySessionLimit ──────────
            stillWatchingEnabled = appSettings.stillWatchingEnabled,
            onStillWatchingEnabledChanged = { playerSettingsViewModel.updateStillWatchingEnabled(it) },
            stillWatchingThresholdMinutes = appSettings.stillWatchingThresholdMinutes,
            onStillWatchingThresholdMinutesChanged = { playerSettingsViewModel.updateStillWatchingThresholdMinutes(it) },
            postPlayMode = appSettings.postPlayMode,
            onPostPlayModeChanged = { playerSettingsViewModel.updatePostPlayMode(it) },
            autoplaySessionLimit = appSettings.autoplaySessionLimit,
            onAutoplaySessionLimitChanged = { playerSettingsViewModel.updateAutoplaySessionLimit(it) },
            // ─── T1-03 – Ses Gelişmiş ────────────────────────────────────────────────
            gainBoostDb = appSettings.gainBoostDb,
            onGainBoostDbChanged = { playerSettingsViewModel.updateGainBoostDb(it) },
            subtitleDelayMs = appSettings.subtitleDelayMs,
            onSubtitleDelayMsChanged = { playerSettingsViewModel.updateSubtitleDelayMs(it) },
            // ─── T1-04 – Dekoder Önceliği (Telefon) ──────────────────────────────────
            decoderPriority = appSettings.decoderPriority,
            onDecoderPriorityChanged = { playerSettingsViewModel.updateDecoderPriority(it) }
        ),
        addon = com.kitsugi.animelist.ui.screens.settings.AddonSettings(
            addons = addonsList,
            debridToken = addonViewModel.debridToken,
            repos = reposList,
            repoPlugins = addonViewModel.repoPluginsState,
            repoLoadingState = addonViewModel.repoLoadingState,
            onAddAddon = { addonViewModel.addAddon(it) },
            onToggleAddon = { addon, enabled -> onToggleAddon(addon, enabled) },
            onDeleteAddon = { addonViewModel.deleteAddon(it) },
            onSaveDebridToken = { addonViewModel.saveDebridToken(it) },
            onAddRepo = { addonViewModel.addRepo(it) },
            onDeleteRepo = { addonViewModel.deleteRepo(it) },
            onFetchRepoPlugins = { addonViewModel.fetchRepoPlugins(it) },
            onInstallPlugin = { plugin, callback -> onInstallPlugin(plugin, callback) },
            onInstallAllPlugins = { repoUrl, repoName, plugins -> onInstallAllPlugins(repoUrl, repoName, plugins) },
            onUpdateAllPlugins = { repoUrl, repoName, plugins -> onUpdateAllPlugins(repoUrl, repoName, plugins) },
            bulkInstallRepoUrl = addonViewModel.bulkInstallRepoUrl,
            bulkInstallRepoName = addonViewModel.bulkInstallRepoName,
            bulkInstallDone = addonViewModel.bulkInstallDone,
            bulkInstallTotal = addonViewModel.bulkInstallTotal,
            bulkInstallCurrentName = addonViewModel.bulkInstallCurrentName,
            bulkInstallResultMessage = addonViewModel.bulkInstallResultMessage,
            onClearBulkInstallResult = { addonViewModel.clearBulkInstallResult() },
            csPlugins = csPluginsList,
            onToggleCsPlugin = { plugin, enabled -> onToggleCsPlugin(plugin, enabled) },
            // NOT: Bu iki satır eksik olduğu için telefon Ayarlar > Eklentiler & Kaynaklar
            // ekranında çöp kutusu simgesine basmak hiçbir şey yapmıyordu (varsayılan no-op
            // callback çağrılıyordu); kurulu eklentiler toplu da silinemiyordu.
            onUninstallCsPlugin = { plugin -> addonViewModel.uninstallCsPlugin(plugin) },
            onUninstallAllCsPlugins = { addonViewModel.uninstallAllCsPlugins() },
            useGithubProxy = appSettings.useGithubProxy,
            onUseGithubProxyChanged = { enabled -> onUseGithubProxyChanged(enabled) },
            isCheckingUpdates = addonViewModel.isCheckingUpdates,
            availableUpdatesCount = addonViewModel.availableUpdatesCount,
            onCheckForUpdates = { addonViewModel.checkForPluginUpdates() },
            onUpdateAllPendingPlugins = { addonViewModel.updateAllPendingPlugins() },
            onRefreshRepo = { repoUrl -> addonViewModel.fetchRepoPlugins(repoUrl, force = true) }
        ),
        manga = com.kitsugi.animelist.ui.screens.settings.MangaSettings(
            mangaSources = mangaViewModel.mangaSources,
            onInstallMangaExtension = { mangaViewModel.installMangaExtension(it) },
            onDeleteMangaExtension = { mangaViewModel.deleteMangaExtension(it) },
            onDeleteMangaExtensions = { sources, done -> mangaViewModel.deleteMangaExtensions(sources, done) },
            onToggleMangaSource = { source, enabled -> mangaViewModel.toggleMangaSource(source, enabled) },
            // Manga > Kurulu > Toplu Sil: callback'ler doğrudan ViewModel'e bağlı.
            // (CS eklentilerinde bu iki satır eksik olduğu için çöp kutusu ölüydü;
            //  aynı hata burada tekrarlanmasın.)
            mangaBulkDelete = com.kitsugi.animelist.ui.components.addons.MangaBulkDeleteState(
                isDeleting = mangaViewModel.mangaIsBulkDeleting,
                hiddenBuiltInCount = mangaViewModel.mangaBulkDeleteHiddenBuiltInCount,
                onDeleteAll = { mangaViewModel.deleteAllMangaExtensions() },
                onRestoreBuiltIns = { mangaViewModel.restoreBulkDeletedMangaBuiltIns() }
            ),
            mangaRepos = mangaViewModel.mangaReposState,
            mangaRepoExtensions = mangaViewModel.mangaRepoExtensionsState,
            mangaRepoLoadingState = mangaViewModel.mangaRepoLoadingState,
            onAddMangaRepo = { mangaViewModel.addMangaRepo(it) },
            onDeleteMangaRepo = { mangaViewModel.deleteMangaRepo(it) },
            onFetchMangaRepo = { mangaViewModel.fetchMangaRepo(it) },
            onInstallMangaApk = { ext, cb -> onInstallMangaApk(ext, cb) },
            onInstallAllMangaExtensions = { repoUrl, list -> onInstallAllMangaExtensions(repoUrl, list) },
            onUpdateAllMangaExtensions = { repoUrl, list -> onUpdateAllMangaExtensions(repoUrl, list) },
            mangaBulkInstallRepoUrl = mangaViewModel.mangaBulkInstallRepoUrl,
            mangaBulkInstallDone = mangaViewModel.mangaBulkInstallDone,
            mangaBulkInstallTotal = mangaViewModel.mangaBulkInstallTotal,
            mangaBulkInstallCurrentName = mangaViewModel.mangaBulkInstallCurrentName,
            onGetInstalledMangaVersionCode = { mangaViewModel.getInstalledVersionCode(it) },
            onGetInstalledMangaVersion = { mangaViewModel.getInstalledMangaExtensionVersion(it) },
            onGetMangaSourceHealthStatus = { mangaViewModel.getSourceHealthStatus(it) },
            onGetMangaSourceRuntimeStats = { mangaViewModel.getSourceRuntimeStats(it) },
            onGetMangaConfiguredDomain = { mangaViewModel.getConfiguredSourceDomain(it) },
            onGetMangaConfiguredBaseUrl = { mangaViewModel.getConfiguredSourceBaseUrl(it) },
            onSetMangaSourceDomain = { src, dom -> onSetMangaSourceDomain(src, dom) },
            onGetMangaSourceUserAgent = { mangaViewModel.getSourceUserAgentOverride(it) },
            onGetMangaSourceSlowdownEnabled = { mangaViewModel.getSourceSlowdownEnabled(it) },
            onSetMangaSourceUserAgent = { src, ua -> onSetMangaSourceUserAgent(src, ua) },
            onSetMangaSourceSlowdownEnabled = { src, env -> onSetMangaSourceSlowdownEnabled(src, env) },
            onResetMangaSourceDiagnostics = { mangaViewModel.resetSourceDiagnostics(it) },
            onClearAllMangaSourceDiagnostics = { mangaViewModel.clearAllSourceDiagnostics() },
            onIsMangaSourceBusy = { mangaViewModel.isSourceBusy(it) },
            onQuickCheckMangaSource = { mangaViewModel.quickCheckSource(it) },
            onRefreshMangaSourceMirror = { mangaViewModel.refreshSourceMirror(it) },
            onClearMangaSourceMirror = { mangaViewModel.clearSourceMirror(it) },
            mangaSourceStateReport = mangaViewModel.mangaSourceStateReport,
            onOpenMangaSourceHealthScreen = { onOpenMangaSourceHealthScreen() },
            onForceCheckMangaUpdates = { mangaViewModel.forceCheckMangaUpdates(it) },
            untrustedRepoToConfirm = mangaViewModel.untrustedRepoToConfirm,
            untrustedSignatureToConfirm = mangaViewModel.untrustedSignatureToConfirm,
            onConfirmUntrustedRepo = { mangaViewModel.addMangaRepo(it, force = true) },
            onDismissUntrustedRepo = { mangaViewModel.clearUntrustedRepoConfirm() },
            onConfirmUntrustedSignature = { ext, hash -> mangaViewModel.installMangaApk(ext, force = true) },
            onDismissUntrustedSignature = { mangaViewModel.clearUntrustedSignatureConfirm() }
        ),
        integrations = com.kitsugi.animelist.ui.screens.settings.IntegrationsSettings(
            dnsChoice = appSettings.dnsChoice,
            onDnsChoiceSelected = { onDnsChoiceSelected(it) },
            tmdbEnabled = appSettings.tmdbEnabled,
            onTmdbEnabledChanged = { onTmdbEnabledChanged(it) },
            tmdbApiKey = appSettings.tmdbUserApiKey,
            onTmdbApiKeyChanged = { onTmdbApiKeyChanged(it) },
            tmdbModernHomeEnabled = appSettings.tmdbModernHomeEnabled,
            onTmdbModernHomeEnabledChanged = { onTmdbModernHomeEnabledChanged(it) },
            tmdbEnrichContinueWatching = appSettings.tmdbEnrichContinueWatching,
            onTmdbEnrichContinueWatchingChanged = { onTmdbEnrichContinueWatchingChanged(it) },
            tmdbLanguage = appSettings.tmdbLanguage,
            onTmdbLanguageChanged = { onTmdbLanguageChanged(it) },
            tmdbUseArtwork = appSettings.tmdbUseArtwork,
            onTmdbUseArtworkChanged = { onTmdbUseArtworkChanged(it) },
            tmdbUseBasicInfo = appSettings.tmdbUseBasicInfo,
            onTmdbUseBasicInfoChanged = { onTmdbUseBasicInfoChanged(it) },
            tmdbUseDetails = appSettings.tmdbUseDetails,
            onTmdbUseDetailsChanged = { onTmdbUseDetailsChanged(it) },
            tmdbUseReleaseDates = appSettings.tmdbUseReleaseDates,
            onTmdbUseReleaseDatesChanged = { onTmdbUseReleaseDatesChanged(it) },
            tmdbUseCredits = appSettings.tmdbUseCredits,
            onTmdbUseCreditsChanged = { onTmdbUseCreditsChanged(it) },
            tmdbUseProductions = appSettings.tmdbUseProductions,
            onTmdbUseProductionsChanged = { onTmdbUseProductionsChanged(it) },
            tmdbUseNetworks = appSettings.tmdbUseNetworks,
            onTmdbUseNetworksChanged = { onTmdbUseNetworksChanged(it) },
            tmdbUseEpisodes = appSettings.tmdbUseEpisodes,
            onTmdbUseEpisodesChanged = { onTmdbUseEpisodesChanged(it) },
            tmdbUseTrailers = appSettings.tmdbUseTrailers,
            onTmdbUseTrailersChanged = { onTmdbUseTrailersChanged(it) },
            tmdbUseMoreLikeThis = appSettings.tmdbUseMoreLikeThis,
            onTmdbUseMoreLikeThisChanged = { onTmdbUseMoreLikeThisChanged(it) },
            tmdbUseCollections = appSettings.tmdbUseCollections,
            onTmdbUseCollectionsChanged = { onTmdbUseCollectionsChanged(it) },
            mdbListEnabled = appSettings.mdbListEnabled,
            onMdbListEnabledChanged = { onMdbListEnabledChanged(it) },
            mdbListApiKey = appSettings.mdbListApiKey,
            onMdbListApiKeyChanged = { onMdbListApiKeyChanged(it) },
            mdbListShowImdb = appSettings.mdbListShowImdb,
            onMdbListShowImdbChanged = { onMdbListShowImdbChanged(it) },
            mdbListShowTomatoes = appSettings.mdbListShowTomatoes,
            onMdbListShowTomatoesChanged = { onMdbListShowTomatoesChanged(it) },
            mdbListShowMetacritic = appSettings.mdbListShowMetacritic,
            onMdbListShowMetacriticChanged = { onMdbListShowMetacriticChanged(it) },
            mdbListShowAudience = appSettings.mdbListShowAudience,
            onMdbListShowAudienceChanged = { onMdbListShowAudienceChanged(it) },
            mdbListShowLetterboxd = appSettings.mdbListShowLetterboxd,
            onMdbListShowLetterboxdChanged = { onMdbListShowLetterboxdChanged(it) },
            mdbListShowTmdb = appSettings.mdbListShowTmdb,
            onMdbListShowTmdbChanged = { onMdbListShowTmdbChanged(it) },
            mdbListShowTrakt = appSettings.mdbListShowTrakt,
            onMdbListShowTraktChanged = { onMdbListShowTraktChanged(it) },
            aniSkipEnabled = appSettings.aniSkipEnabled,
            onAniSkipEnabledChanged = { onAniSkipEnabledChanged(it) },
            aniSkipAutoSkip = appSettings.aniSkipAutoSkip,
            onAniSkipAutoSkipChanged = { onAniSkipAutoSkipChanged(it) },
            animeSkipClientId = appSettings.animeSkipClientId,
            onAnimeSkipClientIdChanged = { onAnimeSkipClientIdChanged(it) },
            autoTranslateEnabled = appSettings.autoTranslateEnabled,
            onAutoTranslateEnabledChanged = { onAutoTranslateEnabledChanged(it) },
            preferredTranslator = appSettings.preferredTranslator,
            onPreferredTranslatorSelected = { onPreferredTranslatorSelected(it) },
            translateSourceLanguage = appSettings.translateSourceLanguage,
            onTranslateSourceLanguageSelected = { onTranslateSourceLanguageSelected(it) },
            translateTargetLanguage = appSettings.translateTargetLanguage,
            onTranslateTargetLanguageSelected = { onTranslateTargetLanguageSelected(it) },
            onOpenStats = { onOpenStats() },
            onOpenFavourites = { onOpenFavourites() },
            onOpenAbout = { onOpenAbout() },
            fanartTvEnabled = appSettings.fanartTvEnabled,
            onFanartTvEnabledChanged = { onFanartTvEnabledChanged(it) },
            fanartTvApiKey = appSettings.fanartTvApiKey,
            onFanartTvApiKeyChanged = { onFanartTvApiKeyChanged(it) }
        ),
        download = com.kitsugi.animelist.ui.screens.settings.DownloadSettings(
            customImageDownloadUri = appSettings.customImageDownloadUri,
            onCustomImageDownloadUriChanged = { onCustomImageDownloadUriChanged(it) },
            videoDownloadUri = appSettings.videoDownloadUri,
            onVideoDownloadUriChanged = { onVideoDownloadUriChanged(it) },
            downloaderPreference = appSettings.downloaderPreference,
            onDownloaderPreferenceSelected = { onDownloaderPreferenceSelected(it) },
            downloadOnlyOverWifi = appSettings.downloadOnlyOverWifi,
            onDownloadOnlyOverWifiChanged = { onDownloadOnlyOverWifiChanged(it) },
            downloadSpeedLimit = appSettings.downloadSpeedLimit,
            onDownloadSpeedLimitChanged = { onDownloadSpeedLimitChanged(it) },
            saveChaptersAsCBZ = appSettings.saveChaptersAsCBZ,
            onSaveChaptersAsCBZChanged = { onSaveChaptersAsCBZChanged(it) },
            splitTallImages = appSettings.splitTallImages,
            onSplitTallImagesChanged = { onSplitTallImagesChanged(it) },
            numberOfDownloads = appSettings.numberOfDownloads,
            onNumberOfDownloadsSelected = { onNumberOfDownloadsSelected(it) },
            removeAfterMarkedAsRead = appSettings.removeAfterMarkedAsRead,
            onRemoveAfterMarkedAsReadChanged = { onRemoveAfterMarkedAsReadChanged(it) },
            removeAfterReadSlots = appSettings.removeAfterReadSlots,
            onRemoveAfterReadSlotsSelected = { onRemoveAfterReadSlotsSelected(it) },
            removeBookmarkedChapters = appSettings.removeBookmarkedChapters,
            onRemoveBookmarkedChaptersChanged = { onRemoveBookmarkedChaptersChanged(it) },
            downloadFillermarkedItems = appSettings.downloadFillermarkedItems,
            onDownloadFillermarkedItemsChanged = { onDownloadFillermarkedItemsChanged(it) },
            removeExcludeCategories = appSettings.removeExcludeCategories,
            onRemoveExcludeCategoriesChanged = { onRemoveExcludeCategoriesChanged(it) },
            removeExcludeAnimeCategories = appSettings.removeExcludeAnimeCategories,
            onRemoveExcludeAnimeCategoriesChanged = { onRemoveExcludeAnimeCategoriesChanged(it) },
            downloadNewEpisodes = appSettings.downloadNewEpisodes,
            onDownloadNewEpisodesChanged = { onDownloadNewEpisodesChanged(it) },
            downloadNewChapters = appSettings.downloadNewChapters,
            onDownloadNewChaptersChanged = { onDownloadNewChaptersChanged(it) },
            downloadNewEpisodeCategories = appSettings.downloadNewEpisodeCategories,
            onDownloadNewEpisodeCategoriesChanged = { onDownloadNewEpisodeCategoriesChanged(it) },
            downloadNewChapterCategories = appSettings.downloadNewChapterCategories,
            onDownloadNewChapterCategoriesChanged = { onDownloadNewChapterCategoriesChanged(it) },
            downloadNewEpisodeCategoriesExclude = appSettings.downloadNewEpisodeCategoriesExclude,
            onDownloadNewEpisodeCategoriesExcludeChanged = { onDownloadNewEpisodeCategoriesExcludeChanged(it) },
            downloadNewChapterCategoriesExclude = appSettings.downloadNewChapterCategoriesExclude,
            onDownloadNewChapterCategoriesExcludeChanged = { onDownloadNewChapterCategoriesExcludeChanged(it) },
            autoDownloadWhileWatching = appSettings.autoDownloadWhileWatching,
            onAutoDownloadWhileWatchingSelected = { onAutoDownloadWhileWatchingSelected(it) },
            autoDownloadWhileReading = appSettings.autoDownloadWhileReading,
            onAutoDownloadWhileReadingSelected = { onAutoDownloadWhileReadingSelected(it) },
            useExternalDownloader = appSettings.useExternalDownloader,
            onUseExternalDownloaderChanged = { onUseExternalDownloaderChanged(it) },
            externalDownloaderSelection = appSettings.externalDownloaderSelection,
            onExternalDownloaderSelectionSelected = { onExternalDownloaderSelectionSelected(it) },
            downloadNewUnreadChaptersOnly = appSettings.downloadNewUnreadChaptersOnly,
            onDownloadNewUnreadChaptersOnlyChanged = { onDownloadNewUnreadChaptersOnlyChanged(it) },
            downloadNewUnseenEpisodesOnly = appSettings.downloadNewUnseenEpisodesOnly,
            onDownloadNewUnseenEpisodesOnlyChanged = { onDownloadNewUnseenEpisodesOnlyChanged(it) },
            subtitleDownloadLanguages = appSettings.subtitleDownloadLanguages,
            onSubtitleDownloadLanguagesChanged = { onSubtitleDownloadLanguagesChanged(it) }
        ),
        onOpenDownloads = {
            navState.navigateToDetail(DetailScreen.Downloads)
        },
        onOpenWatchHistory = {
            navState.navigateToDetail(DetailScreen.WatchHistory)
        },
        onExplorePlugin = { pluginName ->
            navState.navigateToDetail(DetailScreen.AddonExplore(pluginName))
        },
        onOpenPluginPicker = {
            navState.navigateToDetail(DetailScreen.PluginPicker)
        },
        onOpenAbout = {
            onOpenAbout()
        },
        onSubPageOpenChange = { isOpen ->
            navState.isSettingsSubPageOpen = isOpen
        },
        requestedSettingsRoute = appViewModel.requestedSettingsRoute,
        onSettingsRouteConsumed = { appViewModel.consumeSettingsRoute(it) }
    )

// ─── Non-Composable SettingsContext Extension Functions ────────────────────────────

internal fun SettingsContext.onHomeLayoutSelected(homeLayout: String) {
    coroutineScope.launch {
        settingsDataStore.setSelectedHomeLayoutId(homeLayout)
        appViewModel.showSnackbarMessage(KitsugiStrings.get(R.string.ana_sayfa_duzeni_guncellendi))
    }
}

internal fun SettingsContext.onProfileInfoSave(name: String, title: String) {
    coroutineScope.launch {
        settingsDataStore.setProfileInfo(name, title, appSettings.anilistUsername)
        appViewModel.showSnackbarMessage(KitsugiStrings.get(R.string.profil_guncellendi))
    }
}

internal fun SettingsContext.onClearProfileImageClick() {
    coroutineScope.launch {
        settingsDataStore.setProfileImageUri("")
        appViewModel.showSnackbarMessage(KitsugiStrings.get(R.string.profil_resmi_kaldirildi))
    }
}

internal fun SettingsContext.onClearBannerImageClick() {
    coroutineScope.launch {
        settingsDataStore.setBannerImageUri("")
        appViewModel.showSnackbarMessage(KitsugiStrings.get(R.string.afis_resmi_kaldirildi))
    }
}

internal fun SettingsContext.onAniListAuthClick() {
    if (authViewModel.isAniListConnected) authViewModel.disconnectExternalAccount("anilist")
    else authViewModel.startExternalAuth("anilist")
}

internal fun SettingsContext.onMalAuthClick() {
    if (authViewModel.isMalConnected) authViewModel.disconnectExternalAccount("mal")
    else authViewModel.startMalOAuthWebSession()
}

internal fun SettingsContext.onMalReconnectClick() {
    authViewModel.reconnectMalWebSession()
}

internal fun SettingsContext.onSimklAuthClick() {
    if (authViewModel.isSimklConnected) authViewModel.disconnectExternalAccount("simkl")
    else authViewModel.startExternalAuth("simkl")
}

internal fun SettingsContext.onTmdbAuthClick() {
    if (authViewModel.isTmdbConnected) authViewModel.disconnectExternalAccount("tmdb")
    else authViewModel.startExternalAuth("tmdb")
}

internal fun SettingsContext.onAdultContentChanged(show: Boolean) {
    coroutineScope.launch {
        settingsDataStore.setShowAdultContent(show)
        com.kitsugi.animelist.data.cloudstream.CsStreamRunner.setShowAdultContent(show)
        appViewModel.showSnackbarMessage(if (show) KitsugiStrings.get(R.string.r18_icerikler_gosteriliyor) else KitsugiStrings.get(R.string.r18_icerikler_gizlendi))
    }
}

internal fun SettingsContext.onBlurAdultMediaChanged(enabled: Boolean) {
    coroutineScope.launch {
        settingsDataStore.setBlurAdultMedia(enabled)
        appViewModel.showSnackbarMessage(if (enabled) KitsugiStrings.get(R.string.r18_gorseller_blurlanacak) else KitsugiStrings.get(R.string.r18_gorseller_blurlanmayacak))
    }
}

internal fun SettingsContext.onFixedNavBarChanged(enabled: Boolean) {
    coroutineScope.launch {
        settingsDataStore.setFixedNavBar(enabled)
        appViewModel.showSnackbarMessage(if (enabled) "Sabit alt bar etkin" else KitsugiStrings.get(R.string.sabit_alt_bar_devre_disi))
    }
}

private fun SettingsContext.hasAnyLinkedNotificationAccount(): Boolean =
    authViewModel.isAniListConnected || authViewModel.isMalConnected || authViewModel.isSimklConnected ||
        authViewModel.isKitsuConnected || authViewModel.isShikimoriConnected || authViewModel.isBangumiConnected

internal fun SettingsContext.onAiringNotificationsChanged(enabled: Boolean) {
    coroutineScope.launch {
        if (enabled && !hasAnyLinkedNotificationAccount()) {
            appViewModel.showSnackbarMessage(KitsugiStrings.get(R.string.settings_notifications_connect_any_account))
            return@launch
        }
        settingsDataStore.setAiringNotificationsEnabled(enabled)
        appViewModel.showSnackbarMessage(if (enabled) KitsugiStrings.get(R.string.yayin_bildirimleri_etkinlestirildi) else KitsugiStrings.get(R.string.yayin_bildirimleri_devre_disi_birakildi))
        com.kitsugi.animelist.core.notifications.NotificationScheduler.reconcile(context)
    }
}

internal fun SettingsContext.onAniListNotificationsChanged(enabled: Boolean) {
    coroutineScope.launch {
        if (enabled && !authViewModel.isAniListConnected) {
            appViewModel.showSnackbarMessage(
                KitsugiStrings.get(R.string.settings_notifications_connect_account, "AniList")
            )
            return@launch
        }
        settingsDataStore.setAniListNotificationsEnabled(enabled)
        appViewModel.showSnackbarMessage(if (enabled) KitsugiStrings.get(R.string.anilist_bildirimleri_etkinlestirildi) else KitsugiStrings.get(R.string.anilist_bildirimleri_devre_disi_birakildi))
        com.kitsugi.animelist.core.notifications.NotificationScheduler.reconcile(context)
    }
}

internal fun SettingsContext.onMalNotificationsChanged(enabled: Boolean) {
    coroutineScope.launch {
        if (enabled && !authViewModel.isMalConnected) {
            appViewModel.showSnackbarMessage(
                KitsugiStrings.get(R.string.settings_notifications_connect_account, "MyAnimeList")
            )
            return@launch
        }
        settingsDataStore.setMalNotificationsEnabled(enabled)
        appViewModel.showSnackbarMessage(if (enabled) KitsugiStrings.get(R.string.myanimelist_bildirimleri_etkinlestirildi) else KitsugiStrings.get(R.string.myanimelist_bildirimleri_devre_disi_birakildi))
        com.kitsugi.animelist.core.notifications.NotificationScheduler.reconcile(context)
    }
}

internal fun SettingsContext.onSimklNotificationsChanged(enabled: Boolean) {
    coroutineScope.launch {
        if (enabled && !authViewModel.isSimklConnected) {
            appViewModel.showSnackbarMessage(
                KitsugiStrings.get(R.string.settings_notifications_connect_account, "Simkl")
            )
            return@launch
        }
        settingsDataStore.setSimklNotificationsEnabled(enabled)
        appViewModel.showSnackbarMessage(if (enabled) KitsugiStrings.get(R.string.simkl_bildirimleri_etkinlestirildi) else KitsugiStrings.get(R.string.simkl_bildirimleri_devre_disi_birakildi))
        com.kitsugi.animelist.core.notifications.NotificationScheduler.reconcile(context)
    }
}

internal fun SettingsContext.onKitsuNotificationsChanged(enabled: Boolean) {
    coroutineScope.launch {
        if (enabled && !authViewModel.isKitsuConnected) {
            appViewModel.showSnackbarMessage(
                KitsugiStrings.get(R.string.settings_notifications_connect_account, "Kitsu")
            )
            return@launch
        }
        settingsDataStore.setKitsuNotificationsEnabled(enabled)
        appViewModel.showSnackbarMessage(if (enabled) KitsugiStrings.get(R.string.kitsu_bildirimleri_etkinlestirildi) else KitsugiStrings.get(R.string.kitsu_bildirimleri_devre_disi_birakildi))
        com.kitsugi.animelist.core.notifications.NotificationScheduler.reconcile(context)
    }
}

internal fun SettingsContext.onShikimoriNotificationsChanged(enabled: Boolean) {
    coroutineScope.launch {
        if (enabled && !authViewModel.isShikimoriConnected) {
            appViewModel.showSnackbarMessage(
                KitsugiStrings.get(R.string.settings_notifications_connect_account, "Shikimori")
            )
            return@launch
        }
        settingsDataStore.setShikimoriNotificationsEnabled(enabled)
        appViewModel.showSnackbarMessage(if (enabled) KitsugiStrings.get(R.string.shikimori_bildirimleri_etkinlestirildi) else KitsugiStrings.get(R.string.shikimori_bildirimleri_devre_disi_birakildi))
        com.kitsugi.animelist.core.notifications.NotificationScheduler.reconcile(context)
    }
}

internal fun SettingsContext.onTmdbNotificationsChanged(enabled: Boolean) {
    coroutineScope.launch {
        if (enabled && !authViewModel.isTmdbConnected) {
            appViewModel.showSnackbarMessage(
                KitsugiStrings.get(R.string.settings_notifications_connect_account, "TMDB")
            )
            return@launch
        }
        settingsDataStore.setTmdbNotificationsEnabled(enabled)
        appViewModel.showSnackbarMessage(
            if (enabled) KitsugiStrings.get(R.string.tmdb_bildirimleri_etkinlestirildi)
            else KitsugiStrings.get(R.string.tmdb_bildirimleri_devre_disi_birakildi)
        )
        com.kitsugi.animelist.core.notifications.NotificationScheduler.reconcile(context)
    }
}

internal fun SettingsContext.onBangumiNotificationsChanged(enabled: Boolean) {
    coroutineScope.launch {
        if (enabled && !authViewModel.isBangumiConnected) {
            appViewModel.showSnackbarMessage(
                KitsugiStrings.get(R.string.settings_notifications_connect_account, "Bangumi")
            )
            return@launch
        }
        settingsDataStore.setBangumiNotificationsEnabled(enabled)
        appViewModel.showSnackbarMessage(
            if (enabled) KitsugiStrings.get(R.string.bangumi_bildirimleri_etkinlestirildi)
            else KitsugiStrings.get(R.string.bangumi_bildirimleri_devre_disi_birakildi)
        )
        com.kitsugi.animelist.core.notifications.NotificationScheduler.reconcile(context)
    }
}

internal fun SettingsContext.onNotificationIntervalChanged(minutes: Int) {
    coroutineScope.launch {
        settingsDataStore.setNotificationInterval(minutes)
        appViewModel.showSnackbarMessage(KitsugiStrings.get(R.string.bildirim_sikligi_x_dakika_olarak_ayarlandi, minutes))
        com.kitsugi.animelist.core.notifications.NotificationScheduler.reconcile(context)
    }
}


internal fun SettingsContext.onSplashAnimationEnabledChanged(enabled: Boolean) {
    coroutineScope.launch {
        settingsDataStore.setSplashAnimationEnabled(enabled)
        appViewModel.showSnackbarMessage(if (enabled) KitsugiStrings.get(R.string.acilis_animasyonu_etkin) else KitsugiStrings.get(R.string.acilis_animasyonu_devre_disi))
    }
}

internal fun SettingsContext.onSplashSoundEnabledChanged(enabled: Boolean) {
    coroutineScope.launch {
        settingsDataStore.setSplashSoundEnabled(enabled)
        appViewModel.showSnackbarMessage(if (enabled) KitsugiStrings.get(R.string.acilis_sesi_etkin) else KitsugiStrings.get(R.string.acilis_sesi_devre_disi))
    }
}

internal fun SettingsContext.onToggleAddon(addon: com.kitsugi.animelist.data.local.ManagedAddonEntity, enabled: Boolean) {
    addonViewModel.toggleAddon(addon, enabled)
}

internal fun SettingsContext.onInstallPlugin(plugin: com.kitsugi.animelist.data.remote.CsPlugin, callback: ((Boolean) -> Unit)?) {
    addonViewModel.installPlugin(plugin, callback)
}

internal fun SettingsContext.onInstallAllPlugins(repoUrl: String, repoName: String, plugins: List<com.kitsugi.animelist.data.remote.CsPlugin>) {
    addonViewModel.installAllPlugins(repoUrl, repoName, plugins, addonsList, csPluginsList)
}

internal fun SettingsContext.onUpdateAllPlugins(repoUrl: String, repoName: String, plugins: List<com.kitsugi.animelist.data.remote.CsPlugin>) {
    addonViewModel.updateAllPlugins(repoUrl, repoName, plugins, csPluginsList)
}

internal fun SettingsContext.onToggleCsPlugin(plugin: com.kitsugi.animelist.data.local.CsPluginEntity, enabled: Boolean) {
    addonViewModel.toggleCsPlugin(plugin, enabled)
}

internal fun SettingsContext.onInstallMangaApk(ext: com.kitsugi.animelist.data.remote.MangaExtensionInfo, cb: ((Boolean) -> Unit)?) {
    mangaViewModel.installMangaApk(ext, cb)
}

internal fun SettingsContext.onInstallAllMangaExtensions(repoUrl: String, list: List<com.kitsugi.animelist.data.remote.MangaExtensionInfo>) {
    mangaViewModel.installAllMangaExtensions(repoUrl, list)
}

internal fun SettingsContext.onUpdateAllMangaExtensions(repoUrl: String, list: List<com.kitsugi.animelist.data.remote.MangaExtensionInfo>) {
    mangaViewModel.updateAllMangaExtensions(repoUrl, list)
}

internal fun SettingsContext.onSetMangaSourceDomain(src: com.kitsugi.animelist.data.manga.MangaSource, dom: String?) {
    mangaViewModel.setSourceDomainOverride(src, dom)
}

internal fun SettingsContext.onSetMangaSourceUserAgent(src: com.kitsugi.animelist.data.manga.MangaSource, ua: String?) {
    mangaViewModel.setSourceUserAgentOverride(src, ua)
}

internal fun SettingsContext.onSetMangaSourceSlowdownEnabled(src: com.kitsugi.animelist.data.manga.MangaSource, env: Boolean) {
    mangaViewModel.setSourceSlowdownEnabled(src, env)
}

internal fun SettingsContext.onOpenMangaSourceHealthScreen() {
    navState.openMangaSourceHealth()
}

internal fun SettingsContext.onAniListImportClick() {
    authViewModel.importAniListAnimeList(mediaEntries, mediaRepository, com.kitsugi.animelist.data.sync.ListRefreshMode.USER_FORCE)
}

internal fun SettingsContext.onMalImportClick() {
    authViewModel.importMalAnimeList(mediaEntries, mediaRepository, com.kitsugi.animelist.data.sync.ListRefreshMode.USER_FORCE)
}

internal fun SettingsContext.onSimklImportClick() {
    authViewModel.importSimklList(mediaEntries, mediaRepository, com.kitsugi.animelist.data.sync.ListRefreshMode.USER_FORCE)
}

internal fun SettingsContext.onThemeSelected(themeId: String) {
    appViewModel.updateTheme(themeId, settingsDataStore)
}

internal fun SettingsContext.onListLayoutSelected(layoutId: String) {
    appViewModel.updateListLayout(layoutId, settingsDataStore)
}

internal fun SettingsContext.onTitleLanguageSelected(lang: String) {
    appViewModel.updateTitleLanguage(lang, settingsDataStore)
}

// ─── AniHyou paritesi: eksik içerik/liste ayarları handler'ları ──────────────

internal fun SettingsContext.onStaffNameLanguageSelected(lang: String) {
    coroutineScope.launch {
        settingsDataStore.setStaffNameLanguage(lang)
        appViewModel.showSnackbarMessage(KitsugiStrings.get(R.string.ekip_karakter_adi_dili_guncellendi))
    }
}

internal fun SettingsContext.onScoreStepSelected(step: String) {
    coroutineScope.launch {
        settingsDataStore.setScoreStep(step)
        appViewModel.showSnackbarMessage(KitsugiStrings.get(R.string.puanlama_adimlari_x, step))
    }
}

internal fun SettingsContext.onSeparatedListStyleChanged(separated: Boolean) {
    coroutineScope.launch {
        settingsDataStore.setSeparatedListStyle(separated)
        appViewModel.showSnackbarMessage(if (separated) KitsugiStrings.get(R.string.ayrilmis_liste_tarzi_etkin) else KitsugiStrings.get(R.string.duz_liste_tarzi_etkin))
    }
}

internal fun SettingsContext.onShowLowPriorityChanged(show: Boolean) {
    coroutineScope.launch {
        settingsDataStore.setShowLowPriority(show)
        appViewModel.showSnackbarMessage(if (show) KitsugiStrings.get(R.string.dusuk_oncelik_rozetleri_gosteriliyor) else KitsugiStrings.get(R.string.dusuk_oncelik_rozetleri_gizlendi))
    }
}

internal fun SettingsContext.onPriorityColorsChanged(json: String) {
    coroutineScope.launch {
        settingsDataStore.setPriorityColorsJson(json)
        appViewModel.showSnackbarMessage(if (json.isBlank()) KitsugiStrings.get(R.string.oncelik_renkleri_varsayilana_dondu) else KitsugiStrings.get(R.string.oncelik_renkleri_guncellendi))
    }
}

/**
 * Liste ekleme otomasyonu: duruma göre otomatik başlangıç/bitiş tarihi.
 * Kapatıldığında düzenleyici hiçbir tarihi kendiliğinden doldurmaz — tamamen elle kalır.
 */
internal fun SettingsContext.onAutoDateOnStatusChangeChanged(enabled: Boolean) {
    coroutineScope.launch {
        settingsDataStore.setAutoDateOnStatusChange(enabled)
        appViewModel.showSnackbarMessage(
            if (enabled) KitsugiStrings.get(R.string.otomatik_tarih_acildi)
            else KitsugiStrings.get(R.string.otomatik_tarih_kapatildi)
        )
    }
}

/**
 * Liste ekleme otomasyonu: "Tamamlandı"ya çekilince ilerlemenin toplam bölüme
 * tamamlanması (AniList paritesi). Toplam bölüm bilinmeyen kayıtlarda etkisizdir.
 */
internal fun SettingsContext.onAutoProgressOnCompleteChanged(enabled: Boolean) {
    coroutineScope.launch {
        settingsDataStore.setAutoProgressOnComplete(enabled)
        appViewModel.showSnackbarMessage(
            if (enabled) KitsugiStrings.get(R.string.otomatik_ilerleme_acildi)
            else KitsugiStrings.get(R.string.otomatik_ilerleme_kapatildi)
        )
    }
}

/**
 * Hızlı ekleme (kart üzerindeki "+" rozeti) için varsayılan kayıt durumu.
 * "İzliyorum" seçildiğinde otomatik tarih kuralı da devreye girer: yapım doğrudan
 * bugünün başlangıç tarihiyle, panel açılmadan listeye girer.
 */
internal fun SettingsContext.onDefaultAddStatusSelected(status: String) {
    coroutineScope.launch {
        settingsDataStore.setDefaultAddStatus(status)
        appViewModel.showSnackbarMessage(
            when (status) {
                "WATCHING"  -> KitsugiStrings.get(R.string.varsayilan_durum_izliyorum)
                "COMPLETED" -> KitsugiStrings.get(R.string.varsayilan_durum_izledim)
                else        -> KitsugiStrings.get(R.string.varsayilan_durum_izleyecegim)
            }
        )
    }
}

internal fun SettingsContext.onFuzzySearchEnabledChanged(enabled: Boolean) {
    coroutineScope.launch {
        settingsDataStore.setFuzzySearchEnabled(enabled)
        appViewModel.showSnackbarMessage(if (enabled) "Fuzzy arama etkin" else KitsugiStrings.get(R.string.fuzzy_arama_devre_disi))
    }
}

internal fun SettingsContext.onSeparateNovelsMangaChanged(separate: Boolean) {
    coroutineScope.launch {
        settingsDataStore.setSeparateNovelsManga(separate)
        appViewModel.showSnackbarMessage(if (separate) KitsugiStrings.get(R.string.romanlar_mangalardan_ayrildi) else "Romanlar mangalarla birlikte")
    }
}

internal fun SettingsContext.onScoreFormatSelected(format: String) {
    appViewModel.updateScoreFormat(format, settingsDataStore)
}

internal fun SettingsContext.onHideScoresChanged(hide: Boolean) {
    appViewModel.updateHideScores(hide, settingsDataStore)
}

internal fun SettingsContext.onShowAnimeLogosChanged(show: Boolean) {
    appViewModel.updateShowAnimeLogos(show, settingsDataStore)
}

internal fun SettingsContext.onDeleteAllEntries() {
    appViewModel.deleteAllEntries(mediaRepository)
}

internal fun SettingsContext.onCreateBackupText() {
    appViewModel.createBackupText(mediaEntries)
}

internal fun SettingsContext.onImportBackupText() {
    appViewModel.importBackupText(mediaEntries, mediaRepository)
}

internal fun SettingsContext.onDnsChoiceSelected(choice: Int) {
    appViewModel.updateDnsChoice(choice, settingsDataStore)
}

internal fun SettingsContext.onTmdbEnabledChanged(enabled: Boolean) {
    coroutineScope.launch { settingsDataStore.setTmdbEnabled(enabled) }
}

internal fun SettingsContext.onTmdbApiKeyChanged(key: String) {
    coroutineScope.launch { settingsDataStore.setTmdbUserApiKey(key) }
}

internal fun SettingsContext.onTmdbModernHomeEnabledChanged(enabled: Boolean) {
    coroutineScope.launch { settingsDataStore.setTmdbModernHomeEnabled(enabled) }
}

internal fun SettingsContext.onTmdbEnrichContinueWatchingChanged(enabled: Boolean) {
    coroutineScope.launch { settingsDataStore.setTmdbEnrichContinueWatching(enabled) }
}

internal fun SettingsContext.onTmdbLanguageChanged(lang: String) {
    coroutineScope.launch { settingsDataStore.setTmdbLanguage(lang) }
}

internal fun SettingsContext.onTmdbUseArtworkChanged(use: Boolean) {
    coroutineScope.launch { settingsDataStore.setTmdbUseArtwork(use) }
}

internal fun SettingsContext.onTmdbUseBasicInfoChanged(use: Boolean) {
    coroutineScope.launch { settingsDataStore.setTmdbUseBasicInfo(use) }
}

internal fun SettingsContext.onTmdbUseDetailsChanged(use: Boolean) {
    coroutineScope.launch { settingsDataStore.setTmdbUseDetails(use) }
}

internal fun SettingsContext.onTmdbUseReleaseDatesChanged(use: Boolean) {
    coroutineScope.launch { settingsDataStore.setTmdbUseReleaseDates(use) }
}

internal fun SettingsContext.onTmdbUseCreditsChanged(use: Boolean) {
    coroutineScope.launch { settingsDataStore.setTmdbUseCredits(use) }
}

internal fun SettingsContext.onTmdbUseProductionsChanged(use: Boolean) {
    coroutineScope.launch { settingsDataStore.setTmdbUseProductions(use) }
}

internal fun SettingsContext.onTmdbUseNetworksChanged(use: Boolean) {
    coroutineScope.launch { settingsDataStore.setTmdbUseNetworks(use) }
}

internal fun SettingsContext.onTmdbUseEpisodesChanged(use: Boolean) {
    coroutineScope.launch { settingsDataStore.setTmdbUseEpisodes(use) }
}

internal fun SettingsContext.onTmdbUseTrailersChanged(use: Boolean) {
    coroutineScope.launch { settingsDataStore.setTmdbUseTrailers(use) }
}

internal fun SettingsContext.onTmdbUseMoreLikeThisChanged(use: Boolean) {
    coroutineScope.launch { settingsDataStore.setTmdbUseMoreLikeThis(use) }
}

internal fun SettingsContext.onTmdbUseCollectionsChanged(use: Boolean) {
    coroutineScope.launch { settingsDataStore.setTmdbUseCollections(use) }
}

internal fun SettingsContext.onMdbListEnabledChanged(enabled: Boolean) {
    coroutineScope.launch { settingsDataStore.setMdbListEnabled(enabled) }
}

internal fun SettingsContext.onMdbListApiKeyChanged(key: String) {
    coroutineScope.launch { settingsDataStore.setMdbListApiKey(key) }
}

internal fun SettingsContext.onMdbListShowImdbChanged(show: Boolean) {
    coroutineScope.launch { settingsDataStore.setMdbListShowImdb(show) }
}

internal fun SettingsContext.onMdbListShowTomatoesChanged(show: Boolean) {
    coroutineScope.launch { settingsDataStore.setMdbListShowTomatoes(show) }
}

internal fun SettingsContext.onMdbListShowMetacriticChanged(show: Boolean) {
    coroutineScope.launch { settingsDataStore.setMdbListShowMetacritic(show) }
}

internal fun SettingsContext.onMdbListShowAudienceChanged(show: Boolean) {
    coroutineScope.launch { settingsDataStore.setMdbListShowAudience(show) }
}

internal fun SettingsContext.onMdbListShowLetterboxdChanged(show: Boolean) {
    coroutineScope.launch { settingsDataStore.setMdbListShowLetterboxd(show) }
}

internal fun SettingsContext.onMdbListShowTmdbChanged(show: Boolean) {
    coroutineScope.launch { settingsDataStore.setMdbListShowTmdb(show) }
}

internal fun SettingsContext.onMdbListShowTraktChanged(show: Boolean) {
    coroutineScope.launch { settingsDataStore.setMdbListShowTrakt(show) }
}

internal fun SettingsContext.onAniSkipEnabledChanged(enabled: Boolean) {
    coroutineScope.launch { settingsDataStore.setAniSkipEnabled(enabled) }
}

internal fun SettingsContext.onAniSkipAutoSkipChanged(autoSkip: Boolean) {
    coroutineScope.launch { settingsDataStore.setAniSkipAutoSkip(autoSkip) }
}

internal fun SettingsContext.onAnimeSkipClientIdChanged(clientId: String) {
    coroutineScope.launch { settingsDataStore.setAnimeSkipClientId(clientId) }
}

internal fun SettingsContext.onAutoTranslateEnabledChanged(enabled: Boolean) {
    coroutineScope.launch { settingsDataStore.setAutoTranslateEnabled(enabled) }
}

internal fun SettingsContext.onPreferredTranslatorSelected(translator: String) {
    coroutineScope.launch { settingsDataStore.setPreferredTranslator(translator) }
}

internal fun SettingsContext.onTranslateSourceLanguageSelected(lang: String) {
    coroutineScope.launch { settingsDataStore.setTranslateSourceLanguage(lang) }
}

internal fun SettingsContext.onTranslateTargetLanguageSelected(lang: String) {
    coroutineScope.launch { settingsDataStore.setTranslateTargetLanguage(lang) }
}

internal fun SettingsContext.onThemeModeSelected(mode: String) {
    coroutineScope.launch { settingsDataStore.setThemeMode(mode) }
}

internal fun SettingsContext.onAmoledBlackChanged(enabled: Boolean) {
    coroutineScope.launch { settingsDataStore.setAmoledBlack(enabled) }
}

internal fun SettingsContext.onCardFramesEnabledChanged(enabled: Boolean) {
    coroutineScope.launch { settingsDataStore.setCardFramesEnabled(enabled) }
}

internal fun SettingsContext.onCustomAccentColorChanged(color: Int) {
    coroutineScope.launch { settingsDataStore.setCustomAccentColor(color) }
}

internal fun SettingsContext.onCustomAccentColor2Changed(color: Int) {
    coroutineScope.launch { settingsDataStore.setCustomAccentColor2(color) }
}

internal fun SettingsContext.onCustomAccentGradientAngleChanged(angle: Int) {
    coroutineScope.launch { settingsDataStore.setCustomAccentGradientAngle(angle) }
}

internal fun SettingsContext.onDefaultTabSelected(tab: String) {
    coroutineScope.launch { settingsDataStore.setDefaultTab(tab) }
}

internal fun SettingsContext.onAppLanguageSelected(lang: String) {
    coroutineScope.launch { settingsDataStore.setAppLanguage(lang) }
    LocaleCache.updateLocale(context, lang)
}

internal fun SettingsContext.onOpenStats() {
    navState.navigateToDetail(com.kitsugi.animelist.DetailScreen.Stats)
}

internal fun SettingsContext.onOpenFavourites() {
    navState.navigateToDetail(com.kitsugi.animelist.DetailScreen.Favourites)
}

internal fun SettingsContext.onOpenAbout() {
    navState.navigateToDetail(com.kitsugi.animelist.DetailScreen.About)
}

internal fun SettingsContext.onCrossSyncClick() {
    authViewModel.syncPlatforms(mediaRepository)
}

internal fun SettingsContext.onSearchHistoryEnabledChanged(enabled: Boolean) {
    coroutineScope.launch {
        settingsDataStore.setSearchHistoryEnabled(enabled)
    }
}

// ─── T2.6 – Oynatıcı Başlık / Medya Bilgisi Görünürlüğü ────────────────
internal fun SettingsContext.onShowPlayerTitleChanged(show: Boolean) {
    coroutineScope.launch { settingsDataStore.setShowPlayerTitle(show) }
}

internal fun SettingsContext.onShowPlayerResolutionChanged(show: Boolean) {
    coroutineScope.launch { settingsDataStore.setShowPlayerResolution(show) }
}

internal fun SettingsContext.onShowMediaInfoChanged(show: Boolean) {
    coroutineScope.launch { settingsDataStore.setShowMediaInfo(show) }
}

// ─── T1-07 – Manga Okuyucu Varsayılan Ayarları ───────────────────────────────
internal fun SettingsContext.onMangaReadingModeSelected(mode: String) {
    coroutineScope.launch {
        settingsDataStore.setMangaReadingMode(mode)
        appViewModel.showSnackbarMessage("Manga okuma modu: $mode")
    }
}

internal fun SettingsContext.onMangaColorFilterSelected(filter: String) {
    coroutineScope.launch {
        settingsDataStore.setMangaColorFilter(filter)
        appViewModel.showSnackbarMessage("Renk filtresi: $filter")
    }
}

internal fun SettingsContext.onMangaFitModeSelected(fitMode: String) {
    coroutineScope.launch {
        settingsDataStore.setMangaFitMode(fitMode)
        appViewModel.showSnackbarMessage(KitsugiStrings.get(R.string.sigdirma_modu_x, fitMode))
    }
}

internal fun SettingsContext.onMangaBrightnessChanged(brightness: Float) {
    coroutineScope.launch {
        settingsDataStore.setMangaBrightness(brightness)
    }
}

internal fun SettingsContext.onAutoUpdateCheckEnabledChanged(enabled: Boolean) {
    coroutineScope.launch {
        settingsDataStore.setAutoUpdateCheckEnabled(enabled)
        appViewModel.showSnackbarMessage(if (enabled) KitsugiStrings.get(R.string.otomatik_guncelleme_kontrolu_acildi) else KitsugiStrings.get(R.string.otomatik_guncelleme_kontrolu_kapatildi))
    }
}

internal fun SettingsContext.onCheckForUpdatesClick() {
    updateViewModel.checkForUpdates(silent = false, force = true)
}

internal fun SettingsContext.onCustomImageDownloadUriChanged(uri: String) {
    coroutineScope.launch {
        settingsDataStore.setCustomImageDownloadUri(uri)
        appViewModel.showSnackbarMessage(if (uri.isNotBlank()) KitsugiStrings.get(R.string.indirme_klasoru_guncellendi) else KitsugiStrings.get(R.string.indirme_klasoru_varsayilana_sifirlandi))
    }
}

internal fun SettingsContext.onSyncEnabledAnilistChanged(enabled: Boolean) {
    coroutineScope.launch {
        settingsDataStore.setSyncEnabledAnilist(enabled)
        appViewModel.showSnackbarMessage(if (enabled) KitsugiStrings.get(R.string.anilist_esitlemesi_acildi) else KitsugiStrings.get(R.string.anilist_esitlemesi_kapatildi))
    }
}

internal fun SettingsContext.onSyncEnabledMalChanged(enabled: Boolean) {
    coroutineScope.launch {
        settingsDataStore.setSyncEnabledMal(enabled)
        appViewModel.showSnackbarMessage(if (enabled) KitsugiStrings.get(R.string.myanimelist_esitlemesi_acildi) else KitsugiStrings.get(R.string.myanimelist_esitlemesi_kapatildi))
    }
}

internal fun SettingsContext.onSyncEnabledSimklChanged(enabled: Boolean) {
    coroutineScope.launch {
        settingsDataStore.setSyncEnabledSimkl(enabled)
        appViewModel.showSnackbarMessage(if (enabled) KitsugiStrings.get(R.string.simkl_esitlemesi_acildi) else KitsugiStrings.get(R.string.simkl_esitlemesi_kapatildi))
    }
}

internal fun SettingsContext.onSyncEnabledKitsuChanged(enabled: Boolean) {
    coroutineScope.launch {
        settingsDataStore.setSyncEnabledKitsu(enabled)
        appViewModel.showSnackbarMessage(if (enabled) KitsugiStrings.get(R.string.kitsu_esitlemesi_acildi) else KitsugiStrings.get(R.string.kitsu_esitlemesi_kapatildi))
    }
}

internal fun SettingsContext.onSyncEnabledShikimoriChanged(enabled: Boolean) {
    coroutineScope.launch {
        settingsDataStore.setSyncEnabledShikimori(enabled)
        appViewModel.showSnackbarMessage(if (enabled) KitsugiStrings.get(R.string.shikimori_esitlemesi_acildi) else KitsugiStrings.get(R.string.shikimori_esitlemesi_kapatildi))
    }
}

internal fun SettingsContext.onSyncEnabledBangumiChanged(enabled: Boolean) {
    coroutineScope.launch {
        settingsDataStore.setSyncEnabledBangumi(enabled)
        appViewModel.showSnackbarMessage(if (enabled) KitsugiStrings.get(R.string.bangumi_esitlemesi_acildi) else KitsugiStrings.get(R.string.bangumi_esitlemesi_kapatildi))
    }
}

internal fun SettingsContext.onSyncEnabledTmdbChanged(enabled: Boolean) {
    coroutineScope.launch {
        settingsDataStore.setSyncEnabledTmdb(enabled)
        appViewModel.showSnackbarMessage(
            if (enabled) KitsugiStrings.get(R.string.tmdb_esitlemesi_acildi)
            else KitsugiStrings.get(R.string.tmdb_esitlemesi_kapatildi)
        )
        com.kitsugi.animelist.core.notifications.NotificationScheduler.reconcile(context)
    }
}

internal fun SettingsContext.onFanartTvEnabledChanged(enabled: Boolean) {
    coroutineScope.launch {
        settingsDataStore.setFanartTvEnabled(enabled)
        appViewModel.showSnackbarMessage(
            context.getString(if (enabled) R.string.fanart_tv_enabled_message else R.string.fanart_tv_disabled_message)
        )
    }
}

internal fun SettingsContext.onFanartTvApiKeyChanged(key: String) {
    coroutineScope.launch { settingsDataStore.setFanartTvApiKey(key) }
}

internal fun SettingsContext.onVideoDownloadUriChanged(uri: String) {
    coroutineScope.launch {
        settingsDataStore.setVideoDownloadUri(uri)
        appViewModel.showSnackbarMessage(if (uri.isNotBlank()) KitsugiStrings.get(R.string.video_indirme_klasoru_guncellendi) else KitsugiStrings.get(R.string.video_indirme_klasoru_varsayilana_sifirlandi))
    }
}

internal fun SettingsContext.onDownloaderPreferenceSelected(preference: String) {
    coroutineScope.launch {
        settingsDataStore.setDownloaderPreference(preference)
        appViewModel.showSnackbarMessage(KitsugiStrings.get(R.string.indirici_tercihi_guncellendi))
    }
}

internal fun SettingsContext.onDownloadOnlyOverWifiChanged(enabled: Boolean) {
    coroutineScope.launch { settingsDataStore.setDownloadOnlyOverWifi(enabled) }
}
internal fun SettingsContext.onDownloadSpeedLimitChanged(limit: Int) {
    coroutineScope.launch { settingsDataStore.setDownloadSpeedLimit(limit) }
}
internal fun SettingsContext.onSaveChaptersAsCBZChanged(enabled: Boolean) {
    coroutineScope.launch { settingsDataStore.setSaveChaptersAsCBZ(enabled) }
}
internal fun SettingsContext.onSplitTallImagesChanged(enabled: Boolean) {
    coroutineScope.launch { settingsDataStore.setSplitTallImages(enabled) }
}
internal fun SettingsContext.onNumberOfDownloadsSelected(slots: Int) {
    coroutineScope.launch { settingsDataStore.setNumberOfDownloads(slots) }
}
internal fun SettingsContext.onRemoveAfterMarkedAsReadChanged(enabled: Boolean) {
    coroutineScope.launch { settingsDataStore.setRemoveAfterMarkedAsRead(enabled) }
}
internal fun SettingsContext.onRemoveAfterReadSlotsSelected(slots: Int) {
    coroutineScope.launch { settingsDataStore.setRemoveAfterReadSlots(slots) }
}
internal fun SettingsContext.onRemoveBookmarkedChaptersChanged(enabled: Boolean) {
    coroutineScope.launch { settingsDataStore.setRemoveBookmarkedChapters(enabled) }
}
internal fun SettingsContext.onDownloadFillermarkedItemsChanged(enabled: Boolean) {
    coroutineScope.launch { settingsDataStore.setDownloadFillermarkedItems(enabled) }
}
internal fun SettingsContext.onRemoveExcludeCategoriesChanged(categories: Set<String>) {
    coroutineScope.launch { settingsDataStore.setRemoveExcludeCategories(categories) }
}
internal fun SettingsContext.onRemoveExcludeAnimeCategoriesChanged(categories: Set<String>) {
    coroutineScope.launch { settingsDataStore.setRemoveExcludeAnimeCategories(categories) }
}
internal fun SettingsContext.onDownloadNewEpisodesChanged(enabled: Boolean) {
    coroutineScope.launch { settingsDataStore.setDownloadNewEpisodes(enabled) }
}
internal fun SettingsContext.onDownloadNewChaptersChanged(enabled: Boolean) {
    coroutineScope.launch { settingsDataStore.setDownloadNewChapters(enabled) }
}
internal fun SettingsContext.onDownloadNewEpisodeCategoriesChanged(categories: Set<String>) {
    coroutineScope.launch { settingsDataStore.setDownloadNewEpisodeCategories(categories) }
}
internal fun SettingsContext.onDownloadNewChapterCategoriesChanged(categories: Set<String>) {
    coroutineScope.launch { settingsDataStore.setDownloadNewChapterCategories(categories) }
}
internal fun SettingsContext.onDownloadNewEpisodeCategoriesExcludeChanged(categories: Set<String>) {
    coroutineScope.launch { settingsDataStore.setDownloadNewEpisodeCategoriesExclude(categories) }
}
internal fun SettingsContext.onDownloadNewChapterCategoriesExcludeChanged(categories: Set<String>) {
    coroutineScope.launch { settingsDataStore.setDownloadNewChapterCategoriesExclude(categories) }
}
internal fun SettingsContext.onAutoDownloadWhileWatchingSelected(episodes: Int) {
    coroutineScope.launch { settingsDataStore.setAutoDownloadWhileWatching(episodes) }
}
internal fun SettingsContext.onAutoDownloadWhileReadingSelected(chapters: Int) {
    coroutineScope.launch { settingsDataStore.setAutoDownloadWhileReading(chapters) }
}
internal fun SettingsContext.onUseExternalDownloaderChanged(enabled: Boolean) {
    coroutineScope.launch { settingsDataStore.setUseExternalDownloader(enabled) }
}
internal fun SettingsContext.onExternalDownloaderSelectionSelected(selection: String) {
    coroutineScope.launch { settingsDataStore.setExternalDownloaderSelection(selection) }
}
internal fun SettingsContext.onDownloadNewUnreadChaptersOnlyChanged(enabled: Boolean) {
    coroutineScope.launch { settingsDataStore.setDownloadNewUnreadChaptersOnly(enabled) }
}
internal fun SettingsContext.onDownloadNewUnseenEpisodesOnlyChanged(enabled: Boolean) {
    coroutineScope.launch { settingsDataStore.setDownloadNewUnseenEpisodesOnly(enabled) }
}
internal fun SettingsContext.onSubtitleDownloadLanguagesChanged(langs: String) {
    coroutineScope.launch { settingsDataStore.setSubtitleDownloadLanguages(langs) }
}

internal fun SettingsContext.onUseGithubProxyChanged(enabled: Boolean) {
    coroutineScope.launch {
        settingsDataStore.setUseGithubProxy(enabled)
        appViewModel.showSnackbarMessage(
            if (enabled) "GitHub vekil sunucu etkin (jsDelivr)" else KitsugiStrings.get(R.string.github_vekil_sunucu_devre_disi)
        )
    }
}

