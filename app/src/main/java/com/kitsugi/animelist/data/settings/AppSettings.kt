package com.kitsugi.animelist.data.settings

import com.kitsugi.animelist.ui.screens.fullscreen.AudioChannels

/**
 * Uygulama ayarları grup modelleri ve birleşik [AppSettings] cephesi.
 *
 * MİMARİ KORUMA (Dalvik DEX `3rc` 255-Yazmaç Sınırı):
 * Dalvik `invoke-direct/range` ve `invoke-static/range` buyruklarındaki yazmaç sayacı (`AA`)
 * 8-bit işaretsiz tamsayıdır (`0..255`). Tek bir `data class` yapıcısında ~240+ alan bulunduğunda
 * Kotlin'in ürettiği sentetik varsayılan yapıcı ve `copy$default` metodu 255 yazmaç sınırını aşar
 * (`261 & 0xFF == 5`) ve Android ART `java.lang.VerifyError` fırlatarak uygulamayı açılış anında
 * çökertir. Alanlar 8 alt gruba ayrılarak her yapıcının yazmaç sayısı < 50'de tutulur, tüm 248
 * özellik ise `AppSettings` üzerinde sıfır ek bellekli hesaplanan `val` olarak korunur.
 */

data class ProfileAndListSettings(
    val profileName: String = "Profilim",
    val listTitle: String = "Anime & Manga Listem",
    val anilistUsername: String = "",
    val anilistProfileImageUri: String = "",
    val anilistBannerImageUri: String = "",
    val malUsername: String = "",
    val malProfileImageUri: String = "",
    val malBannerImageUri: String = "",
    val simklUsername: String = "",
    val simklProfileImageUri: String = "",
    val simklBannerImageUri: String = "",
    val kitsuUsername: String = "",
    val kitsuProfileImageUri: String = "",
    val shikimoriUsername: String = "",
    val shikimoriProfileImageUri: String = "",
    val bangumiUsername: String = "",
    val bangumiProfileImageUri: String = "",
    val profileImageUri: String = "",
    val bannerImageUri: String = "",
    val titleLanguage: String = "ENGLISH",
    val scoreFormat: String = "POINT_10",
    val scoreStep: String = "1",
    val staffNameLanguage: String = "ENGLISH",
    val separatedListStyle: Boolean = true,
    val showLowPriority: Boolean = false,
    val priorityColorsJson: String = "",
    val fuzzySearchEnabled: Boolean = true,
    val separateNovelsManga: Boolean = false,
    val autoDateOnStatusChange: Boolean = true,
    val autoProgressOnComplete: Boolean = true,
    val defaultAddStatus: String = "PLANNED",
    val hideScores: Boolean = false,
    val showAnimeLogos: Boolean = false,
    val accountBackupEnabled: Boolean = true,
    val accountAddonSyncEnabled: Boolean = true,
    val accountSearchSyncEnabled: Boolean = true,
    val accountNotificationSyncEnabled: Boolean = true,
    val syncEnabledAnilist: Boolean = false,
    val syncEnabledMal: Boolean = false,
    val syncEnabledSimkl: Boolean = false,
    val syncEnabledKitsu: Boolean = false,
    val syncEnabledShikimori: Boolean = false,
    val syncEnabledBangumi: Boolean = false,
    val syncEnabledTmdb: Boolean = false
)

data class ThemeAndAppUiSettings(
    val selectedThemeId: String = "mint",
    val showAdultContent: Boolean = false,
    val blurAdultMedia: Boolean = false,
    val selectedListLayoutId: String = "comfortable",
    val selectedHomeLayoutId: String = "classic",
    val themeMode: String = "FOLLOW_SYSTEM",
    val amoledBlack: Boolean = false,
    val cardFramesEnabled: Boolean = false,
    val customAccentColor: Int = 0,
    val customAccentColor2: Int = 0,
    val customAccentGradientAngle: Int = 135,
    val defaultTab: String = "LAST_USED",
    val lastUsedTab: String = "Explore",
    val appLanguage: String = "system",
    val fixedNavBar: Boolean = false,
    val searchHistoryEnabled: Boolean = true,
    val splashAnimationEnabled: Boolean = true,
    val splashSoundEnabled: Boolean = true,
    val autoUpdateCheckEnabled: Boolean = true
)

data class PlayerPlaybackSettings(
    val playerPreference: String = "MPV",
    val isAutoplayEnabled: Boolean = true,
    val skipIntroDurationSec: Int = 5,
    val defaultAudioBoost: Float = 0.0f,
    val defaultAudioDelayMs: Long = 0L,
    val minBufferMs: Int = 15_000,
    val maxBufferMs: Int = 45_000,
    val bufferForPlaybackMs: Int = 3_500,
    val bufferForPlaybackAfterRebufferMs: Int = 8_000,
    val backBufferDurationMs: Int = 0,
    val frameRateMatchingMode: FrameRateMatchingMode = FrameRateMatchingMode.OFF,
    val resolutionMatchingEnabled: Boolean = false,
    val decoderPriority: Int = 0,
    val dv7HandlingMode: Dv7HandlingMode = Dv7HandlingMode.AUTO,
    val stripHdr10PlusSei: Boolean = false,
    val aspectMode: String = "ORIGINAL",
    val gainBoostDb: Float = 0f,
    val pipEnabled: Boolean = true,
    val audioDelayPerRouteJson: String = "{}",
    val qualityProfileJson: String = "",
    val stillWatchingEnabled: Boolean = true,
    val stillWatchingThresholdMinutes: Int = 90,
    val postPlayMode: String = "AUTO_PLAY_NEXT",
    val autoplaySessionLimit: Int = 0,
    val parallelRangeEnabled: Boolean = false,
    val previewSeekbarEnabled: Boolean = true,
    val preferredExternalPlayerPackage: String = "",
    val showPlayerTitle: Boolean = true
)

data class PlayerOverlaySettings(
    val showPlayerResolution: Boolean = true,
    val showMediaInfo: Boolean = true,
    val titleLimitType: String = "NONE",
    val liveHelperEnabled: Boolean = false,
    val enableAssExtractor: Boolean = false,
    val sleepTimerSeconds: Int = 0,
    val volumeBoostCap: Int = 200,
    val audioChannels: AudioChannels = AudioChannels.AutoSafe,
    val playerStatisticsPage: Int = 0,
    val playerSpeed: Float = 1.0f,
    val scrobbleThreshold: Float = 0.85f,
    val showLoadingCircle: Boolean = true,
    val showCurrentChapter: Boolean = true,
    val playerTimeToDisappearMs: Int = 5000,
    val panelOpacity: Int = 95,
    val reducePlayerMotion: Boolean = false,
    val showSystemStatusBar: Boolean = false,
    val playerFullscreenCutout: Boolean = true,
    val invertDurationTimer: Boolean = false,
    val displayVolumeAsPercentage: Boolean = true,
    val rememberPlayerBrightness: Boolean = false,
    val playerBrightnessValue: Float = -1f,
    val rememberPlayerVolume: Boolean = false,
    val playerVolumeValue: Float = -1f,
    val preserveWatchingPosition: Boolean = true,
    val defaultPlayerOrientation: String = "SENSOR_LANDSCAPE",
    val pipEpisodeToasts: Boolean = true,
    val pipOnExit: Boolean = true,

    // ─── Otomatik liste takibi (oynatıcı → liste) — DENEYSEL, VARSAYILAN KAPALI ─
    /**
     * Oynatma başladığında içerik otomatik olarak "İzleniyor"a çekilsin mi?
     *
     * **Varsayılan `false`.** Deneysel özellik: kullanıcı Ayarlar → Deneysel bölümünden
     * elle açıp onay penceresini kabul etmedikçe hiçbir liste/geçmiş yazımı yapılmaz.
     */
    val autoTrackEnabled: Boolean = false,
    /** Listede olmayan içerik oynatılınca otomatik eklensin mi? */
    val autoTrackAddNew: Boolean = true,
    /** "Bırakıldı" işaretli kayıtlar otomatik takiple ezilmesin. */
    val autoTrackRespectDropped: Boolean = true,
    /** Bitişte küçük "hızlı puanla" çubuğu çıksın mı? */
    val quickRateEnabled: Boolean = true,
    /** Çubuğun ekranda kalma süresi (saniye). */
    val quickRateSeconds: Int = 20,
    /** Otomatik değişiklikler bağlı tüm platformlara yayılsın mı? */
    val autoTrackSyncSources: Boolean = true,
    /** Otomatik takip için en kısa video süresi (saniye) — klipler/fragmanlar hariç. */
    val autoTrackMinSeconds: Int = 60
)

data class MpvAndSubtitleSettings(
    val defaultSubtitleSize: Int = 16,
    val subtitleFontSize: Int = 45,
    val defaultSubtitleColor: Int = 0xFFFFFFFF.toInt(),
    val subtitleBold: Boolean = false,
    val subtitleOutlineEnabled: Boolean = true,
    val subtitleDelayMs: Long = 0L,
    val preferredSubtitleLanguages: String = "tr",
    val addonSubtitleStartupMode: String = "PREFERRED_ONLY",
    val subtitleSpeed: Float = 1f,
    val videoFiltersJson: String = "{}",
    val speedPresets: String = "0.25,0.5,0.75,1,1.25,1.5,1.75,2,2.5,3,3.5,4",
    val preferredAudioLanguages: String = "tr,ja,en",
    val subtitleWhitelist: String = "",
    val subtitleBlacklist: String = "",
    val audioPitchCorrection: Boolean = true,
    val mpvGpuRenderer: String = "gpu",
    val mpvHwdecMode: String = "auto-safe",
    /**
     * Dolby Vision içerikte vo/hwdec'i otomatik düzeltir: gpu-next + (P5/bilinmeyen
     * profilde) yazılım çözme. Kapatılırsa kullanıcının kendi MPV ayarları aynen kalır.
     */
    val mpvDolbyVisionAutoFix: Boolean = true,
    val mpvDebandMode: String = "none",
    val mpvForceYuv420p: Boolean = false,
    val mpvDemuxerCacheMb: Int = 64,
    val secondarySubtitleTrackId: Int = -1,
    val secondarySubtitleDelayMs: Long = 0L,
    val subtitleItalic: Boolean = false,
    val subtitleJustification: String = "center",
    val subtitleBackgroundColor: Int = 0,
    val subtitleShadowOffset: Float = 1.5f,
    val subtitleBorderColor: Int = 0xFF000000.toInt(),
    val subtitleBorderSize: Float = 1.5f,
    val subtitleFont: String = "Sans Serif",
    val subtitleBorderStyle: String = "outline-and-shadow",
    val subtitleFontScale: Float = 1.0f,
    val subtitlePos: Int = 100,
    val subtitleOverrideAss: Boolean = false,
    val screenshotSubtitles: Boolean = true,
    val mpvConf: String = "",
    val mpvInput: String = "",
    val mpvUserFilesEnabled: Boolean = false
)

data class GestureAndSkipSettings(
    val aniSkipEnabled: Boolean = true,
    val aniSkipAutoSkip: Boolean = false,
    val aniSkipDisableOnChapters: Boolean = false,
    val animeSkipClientId: String = "",
    val gestureVolumeEnabled: Boolean = true,
    val gestureBrightnessEnabled: Boolean = true,
    val gestureZoomEnabled: Boolean = true,
    val gestureScrollSensitivity: Float = 1.0f,
    val doubleTapSeekSeconds: Int = 10,
    val holdSpeedMultiplier: Float = 2.0f,
    val swipeVolumeBrightnessSides: Boolean = true,
    val horizontalSeekGestureEnabled: Boolean = true,
    val preciseSeeking: Boolean = false,
    val showSeekBarOnGesture: Boolean = true,
    val leftDoubleTapGesture: String = "SEEK",
    val centerDoubleTapGesture: String = "PLAY_PAUSE",
    val rightDoubleTapGesture: String = "SEEK",
    val enableSkipIntro: Boolean = true,
    val enableNetflixStyleIntroSkip: Boolean = false,
    val waitingTimeIntroSkip: Int = 5,
    val mediaPreviousGesture: String = "SWITCH",
    val mediaPlayPauseGesture: String = "PLAY_PAUSE",
    val mediaNextGesture: String = "SWITCH",
    val defaultIntroLength: Int = 85
)

data class IntegrationsAndNotificationsSettings(
    val dnsChoice: Int = 0,
    val useGithubProxy: Boolean = false,
    val tmdbEnabled: Boolean = true,
    val tmdbUserApiKey: String = "",
    val tmdbModernHomeEnabled: Boolean = true,
    val tmdbEnrichContinueWatching: Boolean = true,
    val tmdbLanguage: String = "tr",
    val tmdbUseArtwork: Boolean = true,
    val tmdbUseBasicInfo: Boolean = true,
    val tmdbUseDetails: Boolean = true,
    val tmdbUseReleaseDates: Boolean = true,
    val tmdbUseCredits: Boolean = true,
    val tmdbUseProductions: Boolean = true,
    val tmdbUseNetworks: Boolean = true,
    val tmdbUseEpisodes: Boolean = true,
    val tmdbUseTrailers: Boolean = true,
    val tmdbUseMoreLikeThis: Boolean = true,
    val tmdbUseCollections: Boolean = true,
    val mdbListApiKey: String = "",
    val mdbListEnabled: Boolean = false,
    val mdbListShowImdb: Boolean = true,
    val mdbListShowTomatoes: Boolean = true,
    val mdbListShowAudience: Boolean = false,
    val mdbListShowMetacritic: Boolean = true,
    val mdbListShowLetterboxd: Boolean = false,
    val mdbListShowTmdb: Boolean = false,
    val mdbListShowTrakt: Boolean = false,
    val fanartTvApiKey: String = "",
    val fanartTvEnabled: Boolean = true,
    val wikiPageTheme: String = "APP",
    val wikiTextZoom: Int = 100,
    /** Wiki açılır sayfası son seçilen düzeni hatırlar; varsayılan MOBİL düzendir. */
    val wikiDesktopLayout: Boolean = false,
    val wikiAdBlockDisabledHosts: Set<String> = emptySet(),
    val autoTranslateEnabled: Boolean = false,
    val preferredTranslator: String = "DEFAULT",
    val translateSourceLanguage: String = "auto",
    val translateTargetLanguage: String = "tr",
    val airingNotificationsEnabled: Boolean = false,
    val aniListNotificationsEnabled: Boolean = false,
    val malNotificationsEnabled: Boolean = false,
    val simklNotificationsEnabled: Boolean = false,
    val kitsuNotificationsEnabled: Boolean = false,
    val shikimoriNotificationsEnabled: Boolean = false,
    val tmdbNotificationsEnabled: Boolean = false,
    /**
     * Bangumi bildirimleri. Bangumi'nin bildirim ucu yok; kayıtlar "在 看" koleksiyonu
     * + yayın takvimi eşleşmesinden üretilir. Diğer kaynaklar gibi ayrı anahtar olması
     * kullanıcının Bangumi'yi tek başına açıp kapatabilmesini sağlar.
     */
    val bangumiNotificationsEnabled: Boolean = false,
    val notificationInterval: Int = 180
)

data class DownloadAndMangaSettings(
    val mangaReadingMode: String = "RightToLeft",
    val mangaColorFilter: String = "Normal",
    val mangaFitMode: String = "FitScreen",
    val mangaBrightness: Float = 1.0f,
    val customImageDownloadUri: String = "",
    val videoDownloadUri: String = "",
    val downloaderPreference: String = "INTERNAL",
    val downloadOnlyOverWifi: Boolean = true,
    val downloadSpeedLimit: Int = 0,
    val saveChaptersAsCBZ: Boolean = true,
    val splitTallImages: Boolean = true,
    val numberOfDownloads: Int = 1,
    val removeAfterMarkedAsRead: Boolean = false,
    val removeAfterReadSlots: Int = -1,
    val removeBookmarkedChapters: Boolean = false,
    val downloadFillermarkedItems: Boolean = false,
    val removeExcludeCategories: Set<String> = emptySet(),
    val removeExcludeAnimeCategories: Set<String> = emptySet(),
    val downloadNewEpisodes: Boolean = false,
    val downloadNewChapters: Boolean = false,
    val downloadNewEpisodeCategories: Set<String> = emptySet(),
    val downloadNewChapterCategories: Set<String> = emptySet(),
    val downloadNewEpisodeCategoriesExclude: Set<String> = emptySet(),
    val downloadNewChapterCategoriesExclude: Set<String> = emptySet(),
    val autoDownloadWhileWatching: Int = 0,
    val autoDownloadWhileReading: Int = 0,
    val useExternalDownloader: Boolean = false,
    val externalDownloaderSelection: String = "",
    val downloadNewUnreadChaptersOnly: Boolean = false,
    val downloadNewUnseenEpisodesOnly: Boolean = false,
    val subtitleDownloadLanguages: String = "tr"
)

/**
 * Klavye ve gamepad destekli oynatıcı kısayolları.
 *
 * Tek bir grup altında toplanmasının nedeni, `AppSettings` cephesinin Dalvik
 * 255-yazmaç sınırına dokunmadan genişleyebilmesi (bkz. dosya başındaki mimari not).
 *
 * [PlayerShortcutAction] adları `disabledShortcuts` içinde **virgülle ayrılmış**
 * tutulur; `Set<String>` DataStore'da doğrudan saklanamıyor.
 * [customBindings] biçimi: `"shift+keycode22=F,keycode66=PLAY_PAUSE"` — küçük ve
 * insan tarafından okunabilir, bozulursa sessizce yok sayılır.
 */
data class PlayerKeyboardSettings(
    /** Özel klavye kısayolları açık mı? Kapalıyken oynatıcı klavye olaylarını MPV'ye bırakır. */
    val keyboardEnabled: Boolean = true,
    /** Harici klavye/gamepad takılıyken kısayollar devreye girsin mi (otomatik algılama). */
    val autoDetectExternalKeyboard: Boolean = true,
    /** Tuş basımında aksiyon göstergesi (küçük OSD) çizilsin mi? */
    val keyboardOsdEnabled: Boolean = true,
    /** Gösterge süresi çarpanı (0,5× … 2×). Varsayılan 1×. */
    val keyboardOsdDurationScale: Float = 1f,
    /** Tam OSD (üst/alt bar) klavyeyle açılsın mı? Varsayılan HAYIR: sadece gösterge. */
    val keyboardOpensFullOsd: Boolean = false,
    /** Kısayol yardım ekranı (?) açılabilsin mi? */
    val keyboardHelpEnabled: Boolean = true,
    /** Kullanıcının kapattığı eylemler, ör. "EXIT,SCREENSHOT". */
    val disabledShortcuts: String = "",
    /** Kullanıcının özel tuş eşlemeleri (boşsa varsayılan tablo geçerlidir). */
    val customBindings: String = "",
    /** Gamepad (oyun kolu) kısayolları açık mı? */
    val gamepadEnabled: Boolean = true,
    /** Normal arama adımı (sn) — ← / → tuşları. Varsayılan 5 sn. */
    val keyboardSeekStepSeconds: Int = 5,
    /** Hassas arama adımı (sn) — Shift + ← / →. Varsayılan 1 sn. */
    val keyboardPreciseSeekSeconds: Int = 1,
    /** Uzun arama adımı (sn) — ↑ / ↓. Varsayılan 60 sn. */
    val keyboardLargeSeekSeconds: Int = 60,
    /** Tuşa basılı tutulduğunda arama adımının katlanarak hızlanması. */
    val keyboardHoldToAccelerate: Boolean = true
)

data class AppSettings(
    val profile: ProfileAndListSettings = ProfileAndListSettings(),
    val themeUi: ThemeAndAppUiSettings = ThemeAndAppUiSettings(),
    val playback: PlayerPlaybackSettings = PlayerPlaybackSettings(),
    val playerUi: PlayerOverlaySettings = PlayerOverlaySettings(),
    val mpvSubtitle: MpvAndSubtitleSettings = MpvAndSubtitleSettings(),
    val gestures: GestureAndSkipSettings = GestureAndSkipSettings(),
    val integrations: IntegrationsAndNotificationsSettings = IntegrationsAndNotificationsSettings(),
    val downloads: DownloadAndMangaSettings = DownloadAndMangaSettings(),
    val keyboard: PlayerKeyboardSettings = PlayerKeyboardSettings()
) {
    val selectedThemeId: String get() = themeUi.selectedThemeId
    val showAdultContent: Boolean get() = themeUi.showAdultContent
    val blurAdultMedia: Boolean get() = themeUi.blurAdultMedia
    val selectedListLayoutId: String get() = themeUi.selectedListLayoutId
    val profileName: String get() = profile.profileName
    val listTitle: String get() = profile.listTitle
    val anilistUsername: String get() = profile.anilistUsername
    val anilistProfileImageUri: String get() = profile.anilistProfileImageUri
    val anilistBannerImageUri: String get() = profile.anilistBannerImageUri
    val malUsername: String get() = profile.malUsername
    val malProfileImageUri: String get() = profile.malProfileImageUri
    val malBannerImageUri: String get() = profile.malBannerImageUri
    val simklUsername: String get() = profile.simklUsername
    val simklProfileImageUri: String get() = profile.simklProfileImageUri
    val simklBannerImageUri: String get() = profile.simklBannerImageUri
    val kitsuUsername: String get() = profile.kitsuUsername
    val kitsuProfileImageUri: String get() = profile.kitsuProfileImageUri
    val shikimoriUsername: String get() = profile.shikimoriUsername
    val shikimoriProfileImageUri: String get() = profile.shikimoriProfileImageUri
    val bangumiUsername: String get() = profile.bangumiUsername
    val bangumiProfileImageUri: String get() = profile.bangumiProfileImageUri
    val profileImageUri: String get() = profile.profileImageUri
    val bannerImageUri: String get() = profile.bannerImageUri
    val titleLanguage: String get() = profile.titleLanguage
    val scoreFormat: String get() = profile.scoreFormat
    val scoreStep: String get() = profile.scoreStep
    val staffNameLanguage: String get() = profile.staffNameLanguage
    val separatedListStyle: Boolean get() = profile.separatedListStyle
    val showLowPriority: Boolean get() = profile.showLowPriority
    val priorityColorsJson: String get() = profile.priorityColorsJson
    val fuzzySearchEnabled: Boolean get() = profile.fuzzySearchEnabled
    val separateNovelsManga: Boolean get() = profile.separateNovelsManga
    val autoDateOnStatusChange: Boolean get() = profile.autoDateOnStatusChange
    val autoProgressOnComplete: Boolean get() = profile.autoProgressOnComplete
    val defaultAddStatus: String get() = profile.defaultAddStatus
    val hideScores: Boolean get() = profile.hideScores
    val showAnimeLogos: Boolean get() = profile.showAnimeLogos
    val playerPreference: String get() = playback.playerPreference
    val isAutoplayEnabled: Boolean get() = playback.isAutoplayEnabled
    val skipIntroDurationSec: Int get() = playback.skipIntroDurationSec
    val defaultSubtitleSize: Int get() = mpvSubtitle.defaultSubtitleSize
    val subtitleFontSize: Int get() = mpvSubtitle.subtitleFontSize
    val defaultSubtitleColor: Int get() = mpvSubtitle.defaultSubtitleColor
    val subtitleBold: Boolean get() = mpvSubtitle.subtitleBold
    val subtitleOutlineEnabled: Boolean get() = mpvSubtitle.subtitleOutlineEnabled
    val defaultAudioBoost: Float get() = playback.defaultAudioBoost
    val defaultAudioDelayMs: Long get() = playback.defaultAudioDelayMs
    val minBufferMs: Int get() = playback.minBufferMs
    val maxBufferMs: Int get() = playback.maxBufferMs
    val bufferForPlaybackMs: Int get() = playback.bufferForPlaybackMs
    val bufferForPlaybackAfterRebufferMs: Int get() = playback.bufferForPlaybackAfterRebufferMs
    val backBufferDurationMs: Int get() = playback.backBufferDurationMs
    val mangaReadingMode: String get() = downloads.mangaReadingMode
    val mangaColorFilter: String get() = downloads.mangaColorFilter
    val mangaFitMode: String get() = downloads.mangaFitMode
    val mangaBrightness: Float get() = downloads.mangaBrightness
    val dnsChoice: Int get() = integrations.dnsChoice
    val useGithubProxy: Boolean get() = integrations.useGithubProxy
    val tmdbEnabled: Boolean get() = integrations.tmdbEnabled
    val tmdbUserApiKey: String get() = integrations.tmdbUserApiKey
    val tmdbModernHomeEnabled: Boolean get() = integrations.tmdbModernHomeEnabled
    val tmdbEnrichContinueWatching: Boolean get() = integrations.tmdbEnrichContinueWatching
    val tmdbLanguage: String get() = integrations.tmdbLanguage
    val tmdbUseArtwork: Boolean get() = integrations.tmdbUseArtwork
    val tmdbUseBasicInfo: Boolean get() = integrations.tmdbUseBasicInfo
    val tmdbUseDetails: Boolean get() = integrations.tmdbUseDetails
    val tmdbUseReleaseDates: Boolean get() = integrations.tmdbUseReleaseDates
    val tmdbUseCredits: Boolean get() = integrations.tmdbUseCredits
    val tmdbUseProductions: Boolean get() = integrations.tmdbUseProductions
    val tmdbUseNetworks: Boolean get() = integrations.tmdbUseNetworks
    val tmdbUseEpisodes: Boolean get() = integrations.tmdbUseEpisodes
    val tmdbUseTrailers: Boolean get() = integrations.tmdbUseTrailers
    val tmdbUseMoreLikeThis: Boolean get() = integrations.tmdbUseMoreLikeThis
    val tmdbUseCollections: Boolean get() = integrations.tmdbUseCollections
    val mdbListApiKey: String get() = integrations.mdbListApiKey
    val mdbListEnabled: Boolean get() = integrations.mdbListEnabled
    val mdbListShowImdb: Boolean get() = integrations.mdbListShowImdb
    val mdbListShowTomatoes: Boolean get() = integrations.mdbListShowTomatoes
    val mdbListShowAudience: Boolean get() = integrations.mdbListShowAudience
    val mdbListShowMetacritic: Boolean get() = integrations.mdbListShowMetacritic
    val mdbListShowLetterboxd: Boolean get() = integrations.mdbListShowLetterboxd
    val mdbListShowTmdb: Boolean get() = integrations.mdbListShowTmdb
    val mdbListShowTrakt: Boolean get() = integrations.mdbListShowTrakt
    val fanartTvApiKey: String get() = integrations.fanartTvApiKey
    val fanartTvEnabled: Boolean get() = integrations.fanartTvEnabled
    val aniSkipEnabled: Boolean get() = gestures.aniSkipEnabled
    val aniSkipAutoSkip: Boolean get() = gestures.aniSkipAutoSkip
    val aniSkipDisableOnChapters: Boolean get() = gestures.aniSkipDisableOnChapters
    val animeSkipClientId: String get() = gestures.animeSkipClientId
    val wikiPageTheme: String get() = integrations.wikiPageTheme
    val wikiTextZoom: Int get() = integrations.wikiTextZoom
    val wikiDesktopLayout: Boolean get() = integrations.wikiDesktopLayout
    val wikiAdBlockDisabledHosts: Set<String> get() = integrations.wikiAdBlockDisabledHosts
    val autoTranslateEnabled: Boolean get() = integrations.autoTranslateEnabled
    val preferredTranslator: String get() = integrations.preferredTranslator
    val translateSourceLanguage: String get() = integrations.translateSourceLanguage
    val translateTargetLanguage: String get() = integrations.translateTargetLanguage
    val selectedHomeLayoutId: String get() = themeUi.selectedHomeLayoutId
    val frameRateMatchingMode: FrameRateMatchingMode get() = playback.frameRateMatchingMode
    val resolutionMatchingEnabled: Boolean get() = playback.resolutionMatchingEnabled
    val decoderPriority: Int get() = playback.decoderPriority
    val dv7HandlingMode: Dv7HandlingMode get() = playback.dv7HandlingMode
    val stripHdr10PlusSei: Boolean get() = playback.stripHdr10PlusSei
    val themeMode: String get() = themeUi.themeMode
    val amoledBlack: Boolean get() = themeUi.amoledBlack
    val cardFramesEnabled: Boolean get() = themeUi.cardFramesEnabled
    val customAccentColor: Int get() = themeUi.customAccentColor
    val customAccentColor2: Int get() = themeUi.customAccentColor2
    val customAccentGradientAngle: Int get() = themeUi.customAccentGradientAngle
    val defaultTab: String get() = themeUi.defaultTab
    val lastUsedTab: String get() = themeUi.lastUsedTab
    val appLanguage: String get() = themeUi.appLanguage
    val accountBackupEnabled: Boolean get() = profile.accountBackupEnabled
    val accountAddonSyncEnabled: Boolean get() = profile.accountAddonSyncEnabled
    val accountSearchSyncEnabled: Boolean get() = profile.accountSearchSyncEnabled
    val accountNotificationSyncEnabled: Boolean get() = profile.accountNotificationSyncEnabled
    val fixedNavBar: Boolean get() = themeUi.fixedNavBar
    val aspectMode: String get() = playback.aspectMode
    val gainBoostDb: Float get() = playback.gainBoostDb
    val subtitleDelayMs: Long get() = mpvSubtitle.subtitleDelayMs
    val preferredSubtitleLanguages: String get() = mpvSubtitle.preferredSubtitleLanguages
    val addonSubtitleStartupMode: String get() = mpvSubtitle.addonSubtitleStartupMode
    val gestureVolumeEnabled: Boolean get() = gestures.gestureVolumeEnabled
    val gestureBrightnessEnabled: Boolean get() = gestures.gestureBrightnessEnabled
    val gestureZoomEnabled: Boolean get() = gestures.gestureZoomEnabled
    val gestureScrollSensitivity: Float get() = gestures.gestureScrollSensitivity
    val doubleTapSeekSeconds: Int get() = gestures.doubleTapSeekSeconds
    val holdSpeedMultiplier: Float get() = gestures.holdSpeedMultiplier
    val swipeVolumeBrightnessSides: Boolean get() = gestures.swipeVolumeBrightnessSides
    val horizontalSeekGestureEnabled: Boolean get() = gestures.horizontalSeekGestureEnabled
    val preciseSeeking: Boolean get() = gestures.preciseSeeking
    val pipEnabled: Boolean get() = playback.pipEnabled
    val audioDelayPerRouteJson: String get() = playback.audioDelayPerRouteJson
    val qualityProfileJson: String get() = playback.qualityProfileJson
    val airingNotificationsEnabled: Boolean get() = integrations.airingNotificationsEnabled
    val aniListNotificationsEnabled: Boolean get() = integrations.aniListNotificationsEnabled
    val malNotificationsEnabled: Boolean get() = integrations.malNotificationsEnabled
    val simklNotificationsEnabled: Boolean get() = integrations.simklNotificationsEnabled
    val kitsuNotificationsEnabled: Boolean get() = integrations.kitsuNotificationsEnabled
    val shikimoriNotificationsEnabled: Boolean get() = integrations.shikimoriNotificationsEnabled
    val tmdbNotificationsEnabled: Boolean get() = integrations.tmdbNotificationsEnabled
    val bangumiNotificationsEnabled: Boolean get() = integrations.bangumiNotificationsEnabled
    val notificationInterval: Int get() = integrations.notificationInterval
    val searchHistoryEnabled: Boolean get() = themeUi.searchHistoryEnabled
    val stillWatchingEnabled: Boolean get() = playback.stillWatchingEnabled
    val stillWatchingThresholdMinutes: Int get() = playback.stillWatchingThresholdMinutes
    val postPlayMode: String get() = playback.postPlayMode
    val autoplaySessionLimit: Int get() = playback.autoplaySessionLimit
    val parallelRangeEnabled: Boolean get() = playback.parallelRangeEnabled
    val previewSeekbarEnabled: Boolean get() = playback.previewSeekbarEnabled
    val preferredExternalPlayerPackage: String get() = playback.preferredExternalPlayerPackage
    val showPlayerTitle: Boolean get() = playback.showPlayerTitle
    val showPlayerResolution: Boolean get() = playerUi.showPlayerResolution
    val showMediaInfo: Boolean get() = playerUi.showMediaInfo
    val titleLimitType: String get() = playerUi.titleLimitType
    val liveHelperEnabled: Boolean get() = playerUi.liveHelperEnabled
    val enableAssExtractor: Boolean get() = playerUi.enableAssExtractor
    val splashAnimationEnabled: Boolean get() = themeUi.splashAnimationEnabled
    val splashSoundEnabled: Boolean get() = themeUi.splashSoundEnabled
    val autoUpdateCheckEnabled: Boolean get() = themeUi.autoUpdateCheckEnabled
    val customImageDownloadUri: String get() = downloads.customImageDownloadUri
    val videoDownloadUri: String get() = downloads.videoDownloadUri
    val downloaderPreference: String get() = downloads.downloaderPreference
    val syncEnabledAnilist: Boolean get() = profile.syncEnabledAnilist
    val syncEnabledMal: Boolean get() = profile.syncEnabledMal
    val syncEnabledSimkl: Boolean get() = profile.syncEnabledSimkl
    val syncEnabledKitsu: Boolean get() = profile.syncEnabledKitsu
    val syncEnabledShikimori: Boolean get() = profile.syncEnabledShikimori
    val syncEnabledBangumi: Boolean get() = profile.syncEnabledBangumi
    val syncEnabledTmdb: Boolean get() = profile.syncEnabledTmdb
    val subtitleSpeed: Float get() = mpvSubtitle.subtitleSpeed
    val videoFiltersJson: String get() = mpvSubtitle.videoFiltersJson
    val speedPresets: String get() = mpvSubtitle.speedPresets
    val preferredAudioLanguages: String get() = mpvSubtitle.preferredAudioLanguages
    val subtitleWhitelist: String get() = mpvSubtitle.subtitleWhitelist
    val subtitleBlacklist: String get() = mpvSubtitle.subtitleBlacklist
    val audioPitchCorrection: Boolean get() = mpvSubtitle.audioPitchCorrection
    val mpvGpuRenderer: String get() = mpvSubtitle.mpvGpuRenderer
    val mpvHwdecMode: String get() = mpvSubtitle.mpvHwdecMode
    val mpvDolbyVisionAutoFix: Boolean get() = mpvSubtitle.mpvDolbyVisionAutoFix
    val mpvDebandMode: String get() = mpvSubtitle.mpvDebandMode
    val mpvForceYuv420p: Boolean get() = mpvSubtitle.mpvForceYuv420p
    val mpvDemuxerCacheMb: Int get() = mpvSubtitle.mpvDemuxerCacheMb
    val secondarySubtitleTrackId: Int get() = mpvSubtitle.secondarySubtitleTrackId
    val secondarySubtitleDelayMs: Long get() = mpvSubtitle.secondarySubtitleDelayMs
    val subtitleItalic: Boolean get() = mpvSubtitle.subtitleItalic
    val subtitleJustification: String get() = mpvSubtitle.subtitleJustification
    val subtitleBackgroundColor: Int get() = mpvSubtitle.subtitleBackgroundColor
    val subtitleShadowOffset: Float get() = mpvSubtitle.subtitleShadowOffset
    val subtitleBorderColor: Int get() = mpvSubtitle.subtitleBorderColor
    val subtitleBorderSize: Float get() = mpvSubtitle.subtitleBorderSize
    val subtitleFont: String get() = mpvSubtitle.subtitleFont
    val subtitleBorderStyle: String get() = mpvSubtitle.subtitleBorderStyle
    val subtitleFontScale: Float get() = mpvSubtitle.subtitleFontScale
    val subtitlePos: Int get() = mpvSubtitle.subtitlePos
    val subtitleOverrideAss: Boolean get() = mpvSubtitle.subtitleOverrideAss
    val sleepTimerSeconds: Int get() = playerUi.sleepTimerSeconds
    val volumeBoostCap: Int get() = playerUi.volumeBoostCap
    val audioChannels: AudioChannels get() = playerUi.audioChannels
    val playerStatisticsPage: Int get() = playerUi.playerStatisticsPage
    val playerSpeed: Float get() = playerUi.playerSpeed
    val scrobbleThreshold: Float get() = playerUi.scrobbleThreshold

    // ─── Otomatik liste takibi ────────────────────────────────────────────────
    val autoTrackEnabled: Boolean get() = playerUi.autoTrackEnabled
    val autoTrackAddNew: Boolean get() = playerUi.autoTrackAddNew
    val autoTrackRespectDropped: Boolean get() = playerUi.autoTrackRespectDropped
    val quickRateEnabled: Boolean get() = playerUi.quickRateEnabled
    val quickRateSeconds: Int get() = playerUi.quickRateSeconds
    val autoTrackSyncSources: Boolean get() = playerUi.autoTrackSyncSources
    val autoTrackMinSeconds: Int get() = playerUi.autoTrackMinSeconds
    val showLoadingCircle: Boolean get() = playerUi.showLoadingCircle
    val showCurrentChapter: Boolean get() = playerUi.showCurrentChapter
    val playerTimeToDisappearMs: Int get() = playerUi.playerTimeToDisappearMs
    val panelOpacity: Int get() = playerUi.panelOpacity
    val reducePlayerMotion: Boolean get() = playerUi.reducePlayerMotion
    val showSystemStatusBar: Boolean get() = playerUi.showSystemStatusBar
    val playerFullscreenCutout: Boolean get() = playerUi.playerFullscreenCutout
    val invertDurationTimer: Boolean get() = playerUi.invertDurationTimer
    val displayVolumeAsPercentage: Boolean get() = playerUi.displayVolumeAsPercentage
    val showSeekBarOnGesture: Boolean get() = gestures.showSeekBarOnGesture
    val rememberPlayerBrightness: Boolean get() = playerUi.rememberPlayerBrightness
    val playerBrightnessValue: Float get() = playerUi.playerBrightnessValue
    val rememberPlayerVolume: Boolean get() = playerUi.rememberPlayerVolume
    val playerVolumeValue: Float get() = playerUi.playerVolumeValue
    val leftDoubleTapGesture: String get() = gestures.leftDoubleTapGesture
    val centerDoubleTapGesture: String get() = gestures.centerDoubleTapGesture
    val rightDoubleTapGesture: String get() = gestures.rightDoubleTapGesture
    val enableSkipIntro: Boolean get() = gestures.enableSkipIntro
    val enableNetflixStyleIntroSkip: Boolean get() = gestures.enableNetflixStyleIntroSkip
    val waitingTimeIntroSkip: Int get() = gestures.waitingTimeIntroSkip
    val screenshotSubtitles: Boolean get() = mpvSubtitle.screenshotSubtitles
    val preserveWatchingPosition: Boolean get() = playerUi.preserveWatchingPosition
    val defaultPlayerOrientation: String get() = playerUi.defaultPlayerOrientation
    val pipEpisodeToasts: Boolean get() = playerUi.pipEpisodeToasts
    val pipOnExit: Boolean get() = playerUi.pipOnExit
    val mpvConf: String get() = mpvSubtitle.mpvConf
    val mpvInput: String get() = mpvSubtitle.mpvInput
    val mpvUserFilesEnabled: Boolean get() = mpvSubtitle.mpvUserFilesEnabled
    val mediaPreviousGesture: String get() = gestures.mediaPreviousGesture
    val mediaPlayPauseGesture: String get() = gestures.mediaPlayPauseGesture
    val mediaNextGesture: String get() = gestures.mediaNextGesture
    val defaultIntroLength: Int get() = gestures.defaultIntroLength
    val downloadOnlyOverWifi: Boolean get() = downloads.downloadOnlyOverWifi
    val downloadSpeedLimit: Int get() = downloads.downloadSpeedLimit
    val saveChaptersAsCBZ: Boolean get() = downloads.saveChaptersAsCBZ
    val splitTallImages: Boolean get() = downloads.splitTallImages
    val numberOfDownloads: Int get() = downloads.numberOfDownloads
    val removeAfterMarkedAsRead: Boolean get() = downloads.removeAfterMarkedAsRead
    val removeAfterReadSlots: Int get() = downloads.removeAfterReadSlots
    val removeBookmarkedChapters: Boolean get() = downloads.removeBookmarkedChapters
    val downloadFillermarkedItems: Boolean get() = downloads.downloadFillermarkedItems
    val removeExcludeCategories: Set<String> get() = downloads.removeExcludeCategories
    val removeExcludeAnimeCategories: Set<String> get() = downloads.removeExcludeAnimeCategories
    val downloadNewEpisodes: Boolean get() = downloads.downloadNewEpisodes
    val downloadNewChapters: Boolean get() = downloads.downloadNewChapters
    val downloadNewEpisodeCategories: Set<String> get() = downloads.downloadNewEpisodeCategories
    val downloadNewChapterCategories: Set<String> get() = downloads.downloadNewChapterCategories
    val downloadNewEpisodeCategoriesExclude: Set<String> get() = downloads.downloadNewEpisodeCategoriesExclude
    val downloadNewChapterCategoriesExclude: Set<String> get() = downloads.downloadNewChapterCategoriesExclude
    val autoDownloadWhileWatching: Int get() = downloads.autoDownloadWhileWatching
    val autoDownloadWhileReading: Int get() = downloads.autoDownloadWhileReading
    val useExternalDownloader: Boolean get() = downloads.useExternalDownloader
    val externalDownloaderSelection: String get() = downloads.externalDownloaderSelection
    val downloadNewUnreadChaptersOnly: Boolean get() = downloads.downloadNewUnreadChaptersOnly
    val downloadNewUnseenEpisodesOnly: Boolean get() = downloads.downloadNewUnseenEpisodesOnly
    val subtitleDownloadLanguages: String get() = downloads.subtitleDownloadLanguages

    // ── Klavye / gamepad kısayolları ─────────────────────────────────────────
    val keyboardEnabled: Boolean get() = keyboard.keyboardEnabled
    val autoDetectExternalKeyboard: Boolean get() = keyboard.autoDetectExternalKeyboard
    val keyboardOsdEnabled: Boolean get() = keyboard.keyboardOsdEnabled
    val keyboardOsdDurationScale: Float get() = keyboard.keyboardOsdDurationScale
    val keyboardOpensFullOsd: Boolean get() = keyboard.keyboardOpensFullOsd
    val keyboardHelpEnabled: Boolean get() = keyboard.keyboardHelpEnabled
    val disabledShortcuts: String get() = keyboard.disabledShortcuts
    val customBindings: String get() = keyboard.customBindings
    val gamepadEnabled: Boolean get() = keyboard.gamepadEnabled
    val keyboardSeekStepSeconds: Int get() = keyboard.keyboardSeekStepSeconds
    val keyboardPreciseSeekSeconds: Int get() = keyboard.keyboardPreciseSeekSeconds
    val keyboardLargeSeekSeconds: Int get() = keyboard.keyboardLargeSeekSeconds
    val keyboardHoldToAccelerate: Boolean get() = keyboard.keyboardHoldToAccelerate
}

enum class FrameRateMatchingMode {
    OFF,
    START,
    START_STOP
}

/**
 * Dolby Vision Profile 7 stream'lerinin nasıl işleneceğini belirler.
 */
enum class Dv7HandlingMode {
    AUTO,
    OFF,
    DV81_LIBDOVI,
    HDR10_BASE_LAYER,
    STRIP_DV
}
