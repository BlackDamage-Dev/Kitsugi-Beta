package com.kitsugi.animelist.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import com.kitsugi.animelist.ui.screens.search.SearchHistoryItem
import com.kitsugi.animelist.ui.screens.search.SearchPlatform
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.screens.fullscreen.AudioChannels
import org.json.JSONArray
import org.json.JSONObject

private val Context.settingsDataStore by preferencesDataStore(
    name = "Kitsugi_settings"
)

class SettingsDataStore(
    private val context: Context
) {
    // Explicit portable-only allowlist. Never copy API keys, SAF/file URIs, caches or device flags.
    private val cloudKeys = setOf(
        "selected_theme_id", "show_adult_content", "blur_adult_media", "selected_list_layout_id",
        "profile_name", "list_title", "title_language", "score_format", "score_step",
        "staff_name_language", "separated_list_style", "show_low_priority", "priority_colors_json",
        "fuzzy_search_enabled", "separate_novels_manga", "hide_scores",
        "show_anime_logos", "player_preference", "is_autoplay_enabled", "skip_intro_duration_sec",
        "default_subtitle_size", "default_subtitle_color", "subtitle_bold", "subtitle_outline_enabled",
        "manga_reading_mode", "manga_color_filter", "manga_fit_mode", "search_history_enabled",
        "manga_search_title_language", "manga_search_follow_title_language",
        "tmdb_language"
    )

    val cloudSettingsFlow: Flow<Map<String, Any>> = context.settingsDataStore.data.map { prefs ->
        prefs.asMap().entries.filter { it.key.name in cloudKeys }.associate { it.key.name to it.value }
    }

    suspend fun restoreCloudSettings(values: Map<String, Any>) {
        context.settingsDataStore.edit { prefs ->
            values.forEach { (name, value) ->
                if (name !in cloudKeys) return@forEach
                // Require the locally declared type, rather than trusting a remote type tag.
                when (name) {
                    "show_adult_content", "blur_adult_media", "hide_scores", "show_anime_logos",
                    "is_autoplay_enabled", "subtitle_bold", "subtitle_outline_enabled",
                    "search_history_enabled", "separated_list_style", "show_low_priority",
                    "fuzzy_search_enabled", "separate_novels_manga",
                    "manga_search_follow_title_language" -> if (value is Boolean) prefs[booleanPreferencesKey(name)] = value
                    "skip_intro_duration_sec", "default_subtitle_size", "default_subtitle_color" ->
                        if (value is Int) prefs[intPreferencesKey(name)] = value
                    else -> if (value is String) prefs[stringPreferencesKey(name)] = value
                }
            }
        }
    }

    private object Keys {
        val SubtitleSpeed = floatPreferencesKey("player_parity_subtitleSpeed")
        val VideoFiltersJson = stringPreferencesKey("player_parity_videoFiltersJson")
        val SpeedPresets = stringPreferencesKey("player_parity_speedPresets")
        val PreferredAudioLanguages = stringPreferencesKey("player_parity_preferredAudioLanguages")
        val SubtitleWhitelist = stringPreferencesKey("player_parity_subtitleWhitelist")
        val SubtitleBlacklist = stringPreferencesKey("player_parity_subtitleBlacklist")
        val AudioPitchCorrection = booleanPreferencesKey("player_parity_audioPitchCorrection")

        val SelectedThemeId = stringPreferencesKey("selected_theme_id")
        val ShowAdultContent = booleanPreferencesKey("show_adult_content")
        val BlurAdultMedia = booleanPreferencesKey("blur_adult_media")
        val SelectedListLayoutId = stringPreferencesKey("selected_list_layout_id")
        val ProfileName = stringPreferencesKey("profile_name")
        val ListTitle = stringPreferencesKey("list_title")
        val AniListUsername = stringPreferencesKey("anilist_username")
        val AniListProfileImageUri = stringPreferencesKey("anilist_profile_image_uri")
        val AniListBannerImageUri = stringPreferencesKey("anilist_banner_image_uri")
        val MalUsername = stringPreferencesKey("mal_username")
        val MalProfileImageUri = stringPreferencesKey("mal_profile_image_uri")
        val MalBannerImageUri = stringPreferencesKey("mal_banner_image_uri")
        val SimklUsername = stringPreferencesKey("simkl_username")
        val SimklProfileImageUri = stringPreferencesKey("simkl_profile_image_uri")
        val SimklBannerImageUri = stringPreferencesKey("simkl_banner_image_uri")
        val ProfileImageUri = stringPreferencesKey("profile_image_uri")
        val BannerImageUri = stringPreferencesKey("banner_image_uri")
        val SearchHistory = stringPreferencesKey("search_history_json")
        // AniHyou'dan uyarlama: başlık dili (Romaji / İngilizce / Japonca)
        val TitleLanguage = stringPreferencesKey("title_language")
        // AniHyou'dan uyarlama: puanlama formatı (POINT_10 / POINT_100 / POINT_5 / POINT_3)
        val ScoreFormat = stringPreferencesKey("score_format")
        // AniHyou'dan uyarlama: puanlama adımları ("1" / "0.5")
        val ScoreStep = stringPreferencesKey("score_step")
        // AniHyou'dan uyarlama: ekip & karakter adı dili (ROMAJI / ENGLISH / NATIVE)
        val StaffNameLanguage = stringPreferencesKey("staff_name_language")
        // AniHyou'dan uyarlama: ayrılmış liste tarzı
        val SeparatedListStyle = booleanPreferencesKey("separated_list_style")
        // AniHyou'dan uyarlama: düşük öncelik rozetini göster
        val ShowLowPriority = booleanPreferencesKey("show_low_priority")
        // AniHyou'dan uyarlama: öncelik renk tohumları JSON'ı
        val PriorityColorsJson = stringPreferencesKey("priority_colors_json")
        // AniHyou'dan uyarlama: fuzzy arama
        val FuzzySearchEnabled = booleanPreferencesKey("fuzzy_search_enabled")
        // AniHyou'dan uyarlama: roman/manga ayrımı
        val SeparateNovelsManga = booleanPreferencesKey("separate_novels_manga")
        // Liste ekleme otomasyonları: duruma göre otomatik tarih / otomatik ilerleme
        val AutoDateOnStatusChange = booleanPreferencesKey("auto_date_on_status_change")
        val AutoProgressOnComplete = booleanPreferencesKey("auto_progress_on_complete")
        // Hızlı ekleme (kart "+" rozeti) için varsayılan durum: PLANNED / WATCHING / COMPLETED
        val DefaultAddStatus = stringPreferencesKey("default_add_status")
        // MoeList'ten uyarlama: puanları gizle
        val HideScores = booleanPreferencesKey("hide_scores")
        val ShowAnimeLogos = booleanPreferencesKey("show_anime_logos")
        val PlayerPreference = stringPreferencesKey("player_preference")
        val IsAutoplayEnabled = booleanPreferencesKey("is_autoplay_enabled")
        val SkipIntroDurationSec = intPreferencesKey("skip_intro_duration_sec")
        val DefaultSubtitleSize = intPreferencesKey("default_subtitle_size")
        val DefaultSubtitleColor = intPreferencesKey("default_subtitle_color")
        val SubtitleBold = booleanPreferencesKey("subtitle_bold")
        val SubtitleOutlineEnabled = booleanPreferencesKey("subtitle_outline_enabled")
        val DefaultAudioBoost = floatPreferencesKey("default_audio_boost")
        val DefaultAudioDelayMs = longPreferencesKey("default_audio_delay_ms")
        val MinBufferMs = intPreferencesKey("min_buffer_ms")
        val MaxBufferMs = intPreferencesKey("max_buffer_ms")
        val BufferForPlaybackMs = intPreferencesKey("buffer_for_playback_ms")
        val BufferForPlaybackAfterRebufferMs = intPreferencesKey("buffer_for_playback_after_rebuffer_ms")
        val BackBufferDurationMs = intPreferencesKey("back_buffer_duration_ms")
        val MangaReadingMode = stringPreferencesKey("manga_reading_mode")
        val MangaColorFilter = stringPreferencesKey("manga_color_filter")
        val MangaFitMode = stringPreferencesKey("manga_fit_mode")
        val MangaBrightness = floatPreferencesKey("manga_brightness")
        /** Kaynak arama dili: ENGLISH / ROMAJI / NATIVE / LOCAL. */
        val MangaSearchTitleLanguage = stringPreferencesKey("manga_search_title_language")
        /** Arama dili genel başlık dilini takip etsin mi. */
        val MangaSearchFollowTitleLanguage = booleanPreferencesKey("manga_search_follow_title_language")
        val DnsChoice = intPreferencesKey("dns_choice")
        // ─── GitHub Vekil Sunucu ───────────────────────────────────────────────
        val UseGithubProxy = booleanPreferencesKey("use_github_proxy")
        // ─── Harici API Entegrasyonları ─────────────────────────────────────────
        // TMDB kullanıcı API anahtarı (boşsa dahili anahtar devreye girer)
        val TmdbEnabled = booleanPreferencesKey("tmdb_enabled")
        val TmdbUserApiKey = stringPreferencesKey("tmdb_user_api_key")
        val TmdbModernHomeEnabled = booleanPreferencesKey("tmdb_modern_home_enabled")
        val TmdbEnrichContinueWatching = booleanPreferencesKey("tmdb_enrich_continue_watching")
        val TmdbLanguage = stringPreferencesKey("tmdb_language")
        val TmdbUseArtwork = booleanPreferencesKey("tmdb_use_artwork")
        val TmdbUseBasicInfo = booleanPreferencesKey("tmdb_use_basic_info")
        val TmdbUseDetails = booleanPreferencesKey("tmdb_use_details")
        val TmdbUseReleaseDates = booleanPreferencesKey("tmdb_use_release_dates")
        val TmdbUseCredits = booleanPreferencesKey("tmdb_use_credits")
        val TmdbUseProductions = booleanPreferencesKey("tmdb_use_productions")
        val TmdbUseNetworks = booleanPreferencesKey("tmdb_use_networks")
        val TmdbUseEpisodes = booleanPreferencesKey("tmdb_use_episodes")
        val TmdbUseTrailers = booleanPreferencesKey("tmdb_use_trailers")
        val TmdbUseMoreLikeThis = booleanPreferencesKey("tmdb_use_more_like_this")
        val TmdbUseCollections = booleanPreferencesKey("tmdb_use_collections")
        // MDBList çoklu puan zenginleştirme
        val MdbListApiKey = stringPreferencesKey("mdblist_api_key")
        val MdbListEnabled = booleanPreferencesKey("mdblist_enabled")
        val MdbListShowImdb = booleanPreferencesKey("mdblist_show_imdb")
        val MdbListShowTomatoes = booleanPreferencesKey("mdblist_show_tomatoes")
        val MdbListShowAudience = booleanPreferencesKey("mdblist_show_audience")
        val MdbListShowMetacritic = booleanPreferencesKey("mdblist_show_metacritic")
        val MdbListShowLetterboxd = booleanPreferencesKey("mdblist_show_letterboxd")
        val MdbListShowTmdb = booleanPreferencesKey("mdblist_show_tmdb")
        val MdbListShowTrakt = booleanPreferencesKey("mdblist_show_trakt")
        // Fanart.tv yüksek kaliteli logo/backdrop/poster kaynağı
        val FanartTvApiKey = stringPreferencesKey("fanart_tv_api_key")
        val FanartTvEnabled = booleanPreferencesKey("fanart_tv_enabled")
        // AniSkip intro/outro atlama
        val AniSkipEnabled = booleanPreferencesKey("aniskip_enabled")
        val AniSkipAutoSkip = booleanPreferencesKey("aniskip_auto_skip")
        val AniSkipDisableOnChapters = booleanPreferencesKey("aniskip_disable_on_chapters")
        val AnimeSkipClientId = stringPreferencesKey("animeskip_client_id")
        // Açıklama Otomatik Çevirisi
        val WikiPageTheme = stringPreferencesKey("wiki_page_theme")
        val WikiTextZoom = intPreferencesKey("wiki_text_zoom")
        /** Sayfa görünümü: true = masaüstü düzeni, false = mobil düzen (varsayılan). */
        val WikiDesktopLayout = booleanPreferencesKey("wiki_desktop_layout")
        val WikiAdBlockDisabledHosts = stringSetPreferencesKey("wiki_adblock_disabled_hosts")
        val AutoTranslateEnabled = booleanPreferencesKey("auto_translate_enabled")
        val PreferredTranslator = stringPreferencesKey("preferred_translator")
        val TranslateSourceLanguage = stringPreferencesKey("translate_source_language")
        val TranslateTargetLanguage = stringPreferencesKey("translate_target_language")
        // TV ana sayfa yerleşim düzeni (classic / modern / grid)
        val SelectedHomeLayoutId = stringPreferencesKey("selected_home_layout_id")
        val FrameRateMatchingMode = stringPreferencesKey("frame_rate_matching_mode")
        val ResolutionMatchingEnabled = booleanPreferencesKey("resolution_matching_enabled")
        val DecoderPriority = intPreferencesKey("decoder_priority")
        val Dv7HandlingMode = stringPreferencesKey("dv7_handling_mode")
        val StripHdr10PlusSei = booleanPreferencesKey("strip_hdr10_plus_sei")
        val ThemeMode = stringPreferencesKey("theme_mode")
        val AmoledBlack = booleanPreferencesKey("amoled_black")
        val CardFramesEnabled = booleanPreferencesKey("card_frames_enabled")
        val CustomAccentColor = intPreferencesKey("custom_accent_color")
        val CustomAccentColor2 = intPreferencesKey("custom_accent_color_2")
        val CustomAccentGradientAngle = intPreferencesKey("custom_accent_gradient_angle")
        val DefaultTab = stringPreferencesKey("default_tab")
        val LastUsedTab = stringPreferencesKey("last_used_tab")
        val AppLanguage = stringPreferencesKey("app_language")
        val AccountBackupEnabled = booleanPreferencesKey("account_backup_enabled")
        val AccountAddonSyncEnabled = booleanPreferencesKey("account_addon_sync_enabled")
        val AccountSearchSyncEnabled = booleanPreferencesKey("account_search_sync_enabled")
        val AccountNotificationSyncEnabled = booleanPreferencesKey("account_notification_sync_enabled")
        val FixedNavBar = booleanPreferencesKey("fixed_nav_bar")
        val AspectMode = stringPreferencesKey("aspect_mode")
        val GainBoostDb = floatPreferencesKey("gain_boost_db")
        val SubtitleDelayMs = longPreferencesKey("subtitle_delay_ms")
        val PreferredSubtitleLanguages = stringPreferencesKey("preferred_subtitle_languages")
        val AddonSubtitleStartupMode = stringPreferencesKey("addon_subtitle_startup_mode")
        // ─── Gesture (T2.1 + T2.7 + TASK_050) ──────────────────────────────────
        val GestureVolumeEnabled = booleanPreferencesKey("gesture_volume_enabled")
        val GestureBrightnessEnabled = booleanPreferencesKey("gesture_brightness_enabled")
        val GestureZoomEnabled = booleanPreferencesKey("gesture_zoom_enabled")
        /** TASK_050 — Dikey swipe hassasiyeti (0.5…2.0) */
        val GestureScrollSensitivity = floatPreferencesKey("gesture_scroll_sensitivity")
        val DoubleTapSeekSeconds = intPreferencesKey("double_tap_seek_seconds")
        val HoldSpeedMultiplier = floatPreferencesKey("hold_speed_multiplier")
        val PlayerSpeed = floatPreferencesKey("pref_player_speed")
        // ─── PIP (T2.3) ──────────────────────────────────────────────────────
        val PipEnabled = booleanPreferencesKey("pip_enabled")
        // ─── Audio Route Delay (T1.3) ─────────────────────────────────────────
        val AudioDelayPerRouteJson = stringPreferencesKey("audio_delay_per_route_json")
        // ─── Quality Profile (T2.4) ────────────────────────────────────────
        val QualityProfileJson = stringPreferencesKey("quality_profile_json")
        // ─── Airing Notifications (T3.3) ─────────────────────────────────────
        val AiringNotificationsEnabled = booleanPreferencesKey("airing_notifications_enabled")
        val AniListNotificationsEnabled = booleanPreferencesKey("anilist_notifications_enabled")
        val MalNotificationsEnabled = booleanPreferencesKey("mal_notifications_enabled")
        val SimklNotificationsEnabled = booleanPreferencesKey("simkl_notifications_enabled")
        val KitsuNotificationsEnabled = booleanPreferencesKey("kitsu_notifications_enabled")
        val ShikimoriNotificationsEnabled = booleanPreferencesKey("shikimori_notifications_enabled")
        val TmdbNotificationsEnabled = booleanPreferencesKey("tmdb_notifications_enabled")
        val BangumiNotificationsEnabled = booleanPreferencesKey("bangumi_notifications_enabled")
        val NotificationInterval = intPreferencesKey("notification_interval")
        val SearchHistoryEnabled = booleanPreferencesKey("search_history_enabled")
        // ─── T1.7 – StillWatching + PostPlayMode + AutoplaySessionRules ──────
        val StillWatchingEnabled = booleanPreferencesKey("still_watching_enabled")
        val StillWatchingThresholdMinutes = intPreferencesKey("still_watching_threshold_minutes")
        val PostPlayMode = stringPreferencesKey("post_play_mode")
        val AutoplaySessionLimit = intPreferencesKey("autoplay_session_limit")
        // ─── T1.9 – Paralel Aralık İndirme ─────────────────────
        val ParallelRangeEnabled = booleanPreferencesKey("parallel_range_enabled")
        // ─── T2.2 – Önizleme Seekbar ───────────────────────────
        val PreviewSeekbarEnabled = booleanPreferencesKey("preview_seekbar_enabled")
        // ─── T2.5 – Harici Oynatıcı Tercihi ──────────────────────────────────
        val PreferredExternalPlayerPackage = stringPreferencesKey("preferred_external_player_package")
        //  T2.6  Oynatc Balk / Medya Bilgisi Grnrl 
        val ShowPlayerTitle = booleanPreferencesKey("show_player_title")
        val ShowPlayerResolution = booleanPreferencesKey("show_player_resolution")
        val ShowMediaInfo = booleanPreferencesKey("show_media_info")
        val TitleLimitType = stringPreferencesKey("title_limit_type")
        val SplashAnimationEnabled = booleanPreferencesKey("splash_animation_enabled")
        val SplashSoundEnabled = booleanPreferencesKey("splash_sound_enabled")
        val LiveHelperEnabled = booleanPreferencesKey("live_helper_enabled")
        val EnableAssExtractor = booleanPreferencesKey("enable_ass_extractor")
        val AutoUpdateCheckEnabled = booleanPreferencesKey("auto_update_check_enabled")
        val CustomImageDownloadUri = stringPreferencesKey("custom_image_download_uri")
        val VideoDownloadUri = stringPreferencesKey("video_download_uri")
        val DownloaderPreference = stringPreferencesKey("downloader_preference")
        val SyncEnabledAnilist = booleanPreferencesKey("sync_enabled_anilist")
        val SyncEnabledMal = booleanPreferencesKey("sync_enabled_mal")
        val SyncEnabledSimkl = booleanPreferencesKey("sync_enabled_simkl")
        val SyncEnabledKitsu = booleanPreferencesKey("sync_enabled_kitsu")
        val SyncEnabledShikimori = booleanPreferencesKey("sync_enabled_shikimori")
        val SyncEnabledBangumi = booleanPreferencesKey("sync_enabled_bangumi")
        val SyncEnabledTmdb = booleanPreferencesKey("sync_enabled_tmdb")
        val KitsuUsername = stringPreferencesKey("kitsu_username")
        val KitsuProfileImageUri = stringPreferencesKey("kitsu_profile_image_uri")
        val ShikimoriUsername = stringPreferencesKey("shikimori_username")
        val ShikimoriProfileImageUri = stringPreferencesKey("shikimori_profile_image_uri")
        val BangumiUsername = stringPreferencesKey("bangumi_username")
        val BangumiProfileImageUri = stringPreferencesKey("bangumi_profile_image_uri")
        // ─── MPV Gelişmiş Oynatıcı Ayarları ───────────────────────────────────────
        val MpvGpuRenderer = stringPreferencesKey("mpv_gpu_renderer")
        val MpvHwdecMode = stringPreferencesKey("mpv_hwdec_mode")
        val MpvDolbyVisionAutoFix = booleanPreferencesKey("mpv_dolby_vision_auto_fix")
        val MpvDebandMode = stringPreferencesKey("mpv_deband_mode")
        val MpvForceYuv420p = booleanPreferencesKey("mpv_force_yuv420p")
        val MpvDemuxerCacheMb = intPreferencesKey("mpv_demuxer_cache_mb")
        // ─── İkincil Altyazı ────────────────────────────────────────────────────
        val SecondarySubtitleTrackId = intPreferencesKey("secondary_subtitle_track_id")
        val SecondarySubtitleDelayMs = longPreferencesKey("secondary_subtitle_delay_ms")
        // ─── Altyazı Gelişmiş Stil ───────────────────────────────────────────────────
        val SubtitleItalic = booleanPreferencesKey("subtitle_italic")
        val SubtitleJustification = stringPreferencesKey("subtitle_justification")
        val SubtitleBackgroundColor = intPreferencesKey("subtitle_background_color")
        val SubtitleShadowOffset = floatPreferencesKey("subtitle_shadow_offset")
        val SubtitleBorderColor = intPreferencesKey("subtitle_border_color")
        val SubtitleBorderSize = floatPreferencesKey("subtitle_border_size")
        val SubtitleFontSize = intPreferencesKey("subtitle_font_size")
        val SubtitleFont = stringPreferencesKey("subtitle_font")
        val SubtitleBorderStyle = stringPreferencesKey("subtitle_border_style")
        val SubtitleFontScale = floatPreferencesKey("subtitle_font_scale")
        val SubtitlePos = intPreferencesKey("subtitle_pos")
        val SubtitleOverrideAss = booleanPreferencesKey("subtitle_override_ass")
        // ─── Uyku Zamanlayıcısı ──────────────────────────────────────────────────
        val SleepTimerSeconds = intPreferencesKey("sleep_timer_seconds")
        // ─── Ses Boost Sınırı ─────────────────────────────────────────────────────
        val VolumeBoostCap = intPreferencesKey("volume_boost_cap")
        // ─── Gesture genisleme ───────────────────────────────────────────────────
        val SwipeVolumeBrightnessSides = booleanPreferencesKey("swipe_volume_brightness_sides")
        val HorizontalSeekGestureEnabled = booleanPreferencesKey("horizontal_seek_gesture_enabled")
        val PreciseSeeking = booleanPreferencesKey("precise_seeking")
        val AudioChannelsConfig = stringPreferencesKey("pref_audio_channels_config")
        val PlayerStatisticsPage = intPreferencesKey("pref_player_statistics_page")
        val ScrobbleThreshold = floatPreferencesKey("pref_scrobble_threshold")
        // ─── v2.4.341: Otomatik liste takibi (oynatıcı → liste köprüsü) ───────────
        /** Kullanıcı deneysel özelliği açtığını onayladı mı? */
        val AutoTrackExperimentalConsent = booleanPreferencesKey("auto_track_experimental_consent")
        val AutoTrackEnabled = booleanPreferencesKey("auto_track_enabled")
        val AutoTrackAddNew = booleanPreferencesKey("auto_track_add_new")
        val AutoTrackRespectDropped = booleanPreferencesKey("auto_track_respect_dropped")
        val QuickRateEnabled = booleanPreferencesKey("quick_rate_enabled")
        val QuickRateSeconds = intPreferencesKey("quick_rate_seconds")
        val AutoTrackSyncSources = booleanPreferencesKey("auto_track_sync_sources")
        val AutoTrackMinSeconds = intPreferencesKey("auto_track_min_seconds")
        val ShowLoadingCircle = booleanPreferencesKey("pref_show_loading_circle")
        val ShowCurrentChapter = booleanPreferencesKey("pref_show_current_chapter")
        val PlayerTimeToDisappearMs = intPreferencesKey("pref_player_time_to_disappear_ms")
        val PanelOpacity = intPreferencesKey("pref_panel_opacity")
        val ReducePlayerMotion = booleanPreferencesKey("pref_reduce_player_motion")
        val ShowSystemStatusBar = booleanPreferencesKey("pref_show_system_status_bar")
        val PlayerFullscreenCutout = booleanPreferencesKey("pref_player_fullscreen_cutout")
        val InvertDurationTimer = booleanPreferencesKey("pref_invert_duration_timer")
        val DisplayVolumeAsPercentage = booleanPreferencesKey("pref_display_volume_as_percentage")
        val ShowSeekBarOnGesture = booleanPreferencesKey("pref_show_seek_bar_on_gesture")
        val RememberPlayerBrightness = booleanPreferencesKey("pref_remember_player_brightness")
        val PlayerBrightnessValue = floatPreferencesKey("pref_player_brightness_value")
        val RememberPlayerVolume = booleanPreferencesKey("pref_remember_player_volume")
        val PlayerVolumeValue = floatPreferencesKey("pref_player_volume_value")
        val LeftDoubleTapGesture = stringPreferencesKey("pref_left_double_tap_gesture")
        val CenterDoubleTapGesture = stringPreferencesKey("pref_center_double_tap_gesture")
        val RightDoubleTapGesture = stringPreferencesKey("pref_right_double_tap_gesture")
        val EnableSkipIntro = booleanPreferencesKey("pref_enable_skip_intro")
        val EnableNetflixStyleIntroSkip = booleanPreferencesKey("pref_enable_netflix_style_intro_skip")
        val WaitingTimeIntroSkip = intPreferencesKey("pref_waiting_time_intro_skip")
        val ScreenshotSubtitles = booleanPreferencesKey("pref_screenshot_subtitles")
        // ─── Klavye / gamepad oynatıcı kısayolları ───────────────────────────
        val KeyboardEnabled = booleanPreferencesKey("player_keyboard_enabled")
        val KeyboardAutoDetect = booleanPreferencesKey("player_keyboard_auto_detect")
        val KeyboardOsdEnabled = booleanPreferencesKey("player_keyboard_osd_enabled")
        val KeyboardOsdDurationScale = floatPreferencesKey("player_keyboard_osd_duration_scale")
        val KeyboardOpensFullOsd = booleanPreferencesKey("player_keyboard_opens_full_osd")
        val KeyboardHelpEnabled = booleanPreferencesKey("player_keyboard_help_enabled")
        val KeyboardDisabledShortcuts = stringPreferencesKey("player_keyboard_disabled_shortcuts")
        val KeyboardCustomBindings = stringPreferencesKey("player_keyboard_custom_bindings")
        val KeyboardGamepadEnabled = booleanPreferencesKey("player_keyboard_gamepad_enabled")
        val KeyboardSeekStepSeconds = intPreferencesKey("player_keyboard_seek_step_seconds")
        val KeyboardPreciseSeekSeconds = intPreferencesKey("player_keyboard_precise_seek_seconds")
        val KeyboardLargeSeekSeconds = intPreferencesKey("player_keyboard_large_seek_seconds")
        val KeyboardHoldToAccelerate = booleanPreferencesKey("player_keyboard_hold_to_accelerate")
        val PreserveWatchingPosition = booleanPreferencesKey("pref_preserve_watching_position")
        val DefaultPlayerOrientation = stringPreferencesKey("pref_default_player_orientation")
        val PipEpisodeToasts = booleanPreferencesKey("pref_pip_episode_toasts")
        val PipOnExit = booleanPreferencesKey("pref_pip_on_exit")
        // ─── Advanced mpv conf/input (Aniyomi AdvancedPlayerPreferences paritesi) ──
        val MpvConf = stringPreferencesKey("pref_mpv_conf")
        val MpvInput = stringPreferencesKey("pref_mpv_input")
        val MpvUserFilesEnabled = booleanPreferencesKey("mpv_scripts")
        // ─── Media gesture mapping (Aniyomi GesturePreferences) ──────────────────
        val MediaPreviousGesture = stringPreferencesKey("pref_media_previous")
        val MediaPlayPauseGesture = stringPreferencesKey("pref_media_playpause")
        val MediaNextGesture = stringPreferencesKey("pref_media_next")
        val DefaultIntroLength = intPreferencesKey("pref_default_intro_length")

        // ─── Yeni İndirme Ayarları (Aniyomi Uyumlu) ──────────────────────────────────
        val DownloadOnlyOverWifi = booleanPreferencesKey("pref_download_only_over_wifi_key")
        val DownloadSpeedLimit = intPreferencesKey("download_speed_limit")
        val SaveChaptersAsCBZ = booleanPreferencesKey("save_chapter_as_cbz")
        val SplitTallImages = booleanPreferencesKey("split_tall_images")
        val NumberOfDownloads = intPreferencesKey("download_slots")
        val RemoveAfterMarkedAsRead = booleanPreferencesKey("pref_remove_after_marked_as_read_key")
        val RemoveAfterReadSlots = intPreferencesKey("remove_after_read_slots")
        val RemoveBookmarkedChapters = booleanPreferencesKey("pref_remove_bookmarked")
        val DownloadFillermarkedItems = booleanPreferencesKey("pref_download_fillermarked")
        val RemoveExcludeCategories = stringSetPreferencesKey("remove_exclude_categories")
        val RemoveExcludeAnimeCategories = stringSetPreferencesKey("remove_exclude_anime_categories")
        val DownloadNewEpisodes = booleanPreferencesKey("download_new_episode")
        val DownloadNewChapters = booleanPreferencesKey("download_new")
        val DownloadNewEpisodeCategories = stringSetPreferencesKey("download_new_anime_categories")
        val DownloadNewChapterCategories = stringSetPreferencesKey("download_new_categories")
        val DownloadNewEpisodeCategoriesExclude = stringSetPreferencesKey("download_new_anime_categories_exclude")
        val DownloadNewChapterCategoriesExclude = stringSetPreferencesKey("download_new_categories_exclude")
        val AutoDownloadWhileWatching = intPreferencesKey("auto_download_while_watching")
        val AutoDownloadWhileReading = intPreferencesKey("auto_download_while_reading")
        val UseExternalDownloader = booleanPreferencesKey("use_external_downloader")
        val ExternalDownloaderSelection = stringPreferencesKey("external_downloader_selection")
        val DownloadNewUnreadChaptersOnly = booleanPreferencesKey("download_new_unread_chapters_only")
        val DownloadNewUnseenEpisodesOnly = booleanPreferencesKey("download_new_unread_episodes_only")
        // ── Altyazı İndirme Dil Filtresi ──────────────────────────────────────
        val SubtitleDownloadLanguages = stringPreferencesKey("subtitle_download_languages")

        // ─── Kaynak Seçimi Kalıcılığı (Keşfet / Arama / Listem) ───────────────────
        // Kullanıcının bir ekranda seçtiği kaynak, uygulama kapatılıp açıldığında
        // yeniden kaybolmasın diye saklanır.
        val LastExplorePlatform = stringPreferencesKey("last_explore_platform")
        val LastSearchTab = stringPreferencesKey("last_search_tab")
        val LastSearchEngine = stringPreferencesKey("last_search_engine")
        val LastSearchScope = stringPreferencesKey("last_search_scope")
        val LastSearchPlatform = stringPreferencesKey("last_search_platform")
        val LastSearchMediaType = stringPreferencesKey("last_search_media_type")
        // Not: Listem sekmesi "Kitsugi_list_filters" (SharedPreferences) içinde
        // AppViewModel.loadFilters/updateMyListTabIndex tarafından zaten kalıcı tutuluyor;
        // manga kaynak durumu ise Room (MangaSourceStateDao) üzerinden saklanıyor.
    }

    // ─── Kaynak seçimi kalıcılığı ───────────────────────────────────────────────
    // Bu değerler akış (settingsFlow) İÇİNDE değildir: ekran her yeniden kurulduğunda
    // "ayar değişti" sinyali üretip gereksiz veri yenilemesine yol açmamak için
    // ayrı ve dar kapsamlı akışlar olarak okunur.

    /** Keşfet ekranının son seçili kaynağı (ExplorePlatform.name). */
    val lastExplorePlatformFlow: Flow<String> = kotlinx.coroutines.flow.flow {
        context.settingsDataStore.data.collect { preferences ->
            emit(preferences[Keys.LastExplorePlatform] ?: "")
        }
    }

    suspend fun setLastExplorePlatform(platformName: String) {
        if (platformName.isBlank()) return
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.LastExplorePlatform] = platformName
        }
    }

    /** Arama ekranının sekme/platform/tür seçimi (ileriye dönük okuma için saklanır). */
    suspend fun setLastSearchSelection(tabName: String, platformName: String, mediaTypeName: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.LastSearchTab] = tabName
            preferences[Keys.LastSearchPlatform] = platformName
            preferences[Keys.LastSearchMediaType] = mediaTypeName
        }
    }

    /** Arama ekranının son seçili kaynak motoru (SearchSourceEngine.name). */
    val lastSearchEngineFlow: Flow<String> = kotlinx.coroutines.flow.flow {
        context.settingsDataStore.data.collect { preferences ->
            emit(preferences[Keys.LastSearchEngine] ?: "")
        }
    }

    /** Arama ekranının son seçili kapsamı (SearchScope.name). */
    val lastSearchScopeFlow: Flow<String> = kotlinx.coroutines.flow.flow {
        context.settingsDataStore.data.collect { preferences ->
            emit(preferences[Keys.LastSearchScope] ?: "")
        }
    }

    suspend fun setLastSearchEngineAndScope(engineName: String, scopeName: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.LastSearchEngine] = engineName
            preferences[Keys.LastSearchScope] = scopeName
        }
    }

    val settingsFlow: Flow<AppSettings> = kotlinx.coroutines.flow.flow {
        context.settingsDataStore.data.collect { preferences ->
            runCatching {
                val anim = preferences[Keys.SplashAnimationEnabled] ?: true
                val sound = preferences[Keys.SplashSoundEnabled] ?: true
                context.getSharedPreferences("kitsugi_splash_cache", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("splash_animation_enabled", anim)
                    .putBoolean("splash_sound_enabled", sound)
                    .apply()
            }
            val parsedSettings = AppSettings(
                profile = ProfileAndListSettings(
                    profileName = preferences[Keys.ProfileName] ?: "Profilim",
                    listTitle = preferences[Keys.ListTitle] ?: "Anime & Manga Listem",
                    anilistUsername = preferences[Keys.AniListUsername] ?: "",
                    anilistProfileImageUri = preferences[Keys.AniListProfileImageUri] ?: "",
                    anilistBannerImageUri = preferences[Keys.AniListBannerImageUri] ?: "",
                    malUsername = preferences[Keys.MalUsername] ?: "",
                    malProfileImageUri = preferences[Keys.MalProfileImageUri] ?: "",
                    malBannerImageUri = preferences[Keys.MalBannerImageUri] ?: "",
                    simklUsername = preferences[Keys.SimklUsername] ?: "",
                    simklProfileImageUri = preferences[Keys.SimklProfileImageUri] ?: "",
                    simklBannerImageUri = preferences[Keys.SimklBannerImageUri] ?: "",
                    kitsuUsername = preferences[Keys.KitsuUsername] ?: "",
                    kitsuProfileImageUri = preferences[Keys.KitsuProfileImageUri] ?: "",
                    shikimoriUsername = preferences[Keys.ShikimoriUsername] ?: "",
                    shikimoriProfileImageUri = preferences[Keys.ShikimoriProfileImageUri] ?: "",
                    bangumiUsername = preferences[Keys.BangumiUsername] ?: "",
                    bangumiProfileImageUri = preferences[Keys.BangumiProfileImageUri] ?: "",
                    profileImageUri = preferences[Keys.ProfileImageUri] ?: "",
                    bannerImageUri = preferences[Keys.BannerImageUri] ?: "",
                    titleLanguage = (preferences[Keys.TitleLanguage] ?: "ENGLISH").let { if (it == "ROMAJI") "ENGLISH" else it },
                    scoreFormat = preferences[Keys.ScoreFormat] ?: "POINT_10",
                    scoreStep = preferences[Keys.ScoreStep] ?: "1",
                    staffNameLanguage = (preferences[Keys.StaffNameLanguage] ?: "ENGLISH").let { if (it == "ROMAJI") "ENGLISH" else it },
                    separatedListStyle = preferences[Keys.SeparatedListStyle] ?: true,
                    showLowPriority = preferences[Keys.ShowLowPriority] ?: false,
                    priorityColorsJson = preferences[Keys.PriorityColorsJson] ?: "",
                    fuzzySearchEnabled = preferences[Keys.FuzzySearchEnabled] ?: true,
                    separateNovelsManga = preferences[Keys.SeparateNovelsManga] ?: false,
                    autoDateOnStatusChange = preferences[Keys.AutoDateOnStatusChange] ?: true,
                    autoProgressOnComplete = preferences[Keys.AutoProgressOnComplete] ?: true,
                    defaultAddStatus = preferences[Keys.DefaultAddStatus] ?: "PLANNED",
                    hideScores = preferences[Keys.HideScores] ?: false,
                    showAnimeLogos = preferences[Keys.ShowAnimeLogos] ?: false,
                    accountBackupEnabled = preferences[Keys.AccountBackupEnabled] ?: true,
                    accountAddonSyncEnabled = preferences[Keys.AccountAddonSyncEnabled] ?: true,
                    accountSearchSyncEnabled = preferences[Keys.AccountSearchSyncEnabled] ?: true,
                    accountNotificationSyncEnabled = preferences[Keys.AccountNotificationSyncEnabled] ?: true,
                    syncEnabledAnilist = preferences[Keys.SyncEnabledAnilist] ?: true,
                    syncEnabledMal = preferences[Keys.SyncEnabledMal] ?: true,
                    syncEnabledSimkl = preferences[Keys.SyncEnabledSimkl] ?: true,
                    syncEnabledKitsu = preferences[Keys.SyncEnabledKitsu] ?: true,
                    syncEnabledShikimori = preferences[Keys.SyncEnabledShikimori] ?: true,
                    syncEnabledBangumi = preferences[Keys.SyncEnabledBangumi] ?: true,
                    syncEnabledTmdb = preferences[Keys.SyncEnabledTmdb] ?: true
                ),
                themeUi = ThemeAndAppUiSettings(
                    selectedThemeId = preferences[Keys.SelectedThemeId] ?: "mint",
                    showAdultContent = preferences[Keys.ShowAdultContent] ?: false,
                    blurAdultMedia = preferences[Keys.BlurAdultMedia] ?: false,
                    selectedListLayoutId = preferences[Keys.SelectedListLayoutId] ?: "comfortable",
                    selectedHomeLayoutId = preferences[Keys.SelectedHomeLayoutId] ?: "classic",
                    themeMode = preferences[Keys.ThemeMode] ?: "FOLLOW_SYSTEM",
                    amoledBlack = preferences[Keys.AmoledBlack] ?: false,
                    cardFramesEnabled = preferences[Keys.CardFramesEnabled] ?: false,
                    customAccentColor = preferences[Keys.CustomAccentColor] ?: 0,
                    customAccentColor2 = preferences[Keys.CustomAccentColor2] ?: 0,
                    customAccentGradientAngle = preferences[Keys.CustomAccentGradientAngle] ?: 135,
                    defaultTab = preferences[Keys.DefaultTab] ?: "LAST_USED",
                    lastUsedTab = preferences[Keys.LastUsedTab] ?: "Explore",
                    appLanguage = preferences[Keys.AppLanguage] ?: "system",
                    fixedNavBar = preferences[Keys.FixedNavBar] ?: false,
                    searchHistoryEnabled = preferences[Keys.SearchHistoryEnabled] ?: true,
                    splashAnimationEnabled = preferences[Keys.SplashAnimationEnabled] ?: true,
                    splashSoundEnabled = preferences[Keys.SplashSoundEnabled] ?: true,
                    autoUpdateCheckEnabled = preferences[Keys.AutoUpdateCheckEnabled] ?: true
                ),
                playback = PlayerPlaybackSettings(
                    playerPreference = preferences[Keys.PlayerPreference] ?: "MPV",
                    isAutoplayEnabled = preferences[Keys.IsAutoplayEnabled] ?: true,
                    skipIntroDurationSec = preferences[Keys.SkipIntroDurationSec] ?: 5,
                    defaultAudioBoost = preferences[Keys.DefaultAudioBoost] ?: 0.0f,
                    defaultAudioDelayMs = preferences[Keys.DefaultAudioDelayMs] ?: 0L,
                    minBufferMs = preferences[Keys.MinBufferMs] ?: 15_000,
                    maxBufferMs = preferences[Keys.MaxBufferMs] ?: 45_000,
                    bufferForPlaybackMs = preferences[Keys.BufferForPlaybackMs] ?: 2_500,
                    bufferForPlaybackAfterRebufferMs = preferences[Keys.BufferForPlaybackAfterRebufferMs] ?: 3_000,
                    backBufferDurationMs = preferences[Keys.BackBufferDurationMs] ?: 0,
                    frameRateMatchingMode = runCatching {
                        com.kitsugi.animelist.data.settings.FrameRateMatchingMode.valueOf(
                            preferences[Keys.FrameRateMatchingMode] ?: "OFF"
                        )
                    }.getOrDefault(com.kitsugi.animelist.data.settings.FrameRateMatchingMode.OFF),
                    resolutionMatchingEnabled = preferences[Keys.ResolutionMatchingEnabled] ?: false,
                    decoderPriority = preferences[Keys.DecoderPriority] ?: 0,
                    dv7HandlingMode = runCatching {
                        com.kitsugi.animelist.data.settings.Dv7HandlingMode.valueOf(
                            preferences[Keys.Dv7HandlingMode] ?: "AUTO"
                        )
                    }.getOrDefault(com.kitsugi.animelist.data.settings.Dv7HandlingMode.AUTO),
                    stripHdr10PlusSei = preferences[Keys.StripHdr10PlusSei] ?: false,
                    aspectMode = preferences[Keys.AspectMode] ?: "ORIGINAL",
                    gainBoostDb = preferences[Keys.GainBoostDb] ?: 0f,
                    pipEnabled = preferences[Keys.PipEnabled] ?: true,
                    audioDelayPerRouteJson = preferences[Keys.AudioDelayPerRouteJson] ?: "{}",
                    qualityProfileJson = preferences[Keys.QualityProfileJson] ?: "",
                    stillWatchingEnabled = preferences[Keys.StillWatchingEnabled] ?: true,
                    stillWatchingThresholdMinutes = preferences[Keys.StillWatchingThresholdMinutes] ?: 90,
                    postPlayMode = preferences[Keys.PostPlayMode] ?: "AUTO_PLAY_NEXT",
                    autoplaySessionLimit = preferences[Keys.AutoplaySessionLimit] ?: 0,
                    parallelRangeEnabled = preferences[Keys.ParallelRangeEnabled] ?: false,
                    previewSeekbarEnabled = preferences[Keys.PreviewSeekbarEnabled] ?: true,
                    preferredExternalPlayerPackage = preferences[Keys.PreferredExternalPlayerPackage] ?: "",
                    showPlayerTitle = preferences[Keys.ShowPlayerTitle] ?: true
                ),
                playerUi = PlayerOverlaySettings(
                    showPlayerResolution = preferences[Keys.ShowPlayerResolution] ?: true,
                    showMediaInfo = preferences[Keys.ShowMediaInfo] ?: true,
                    titleLimitType = preferences[Keys.TitleLimitType] ?: "NONE",
                    liveHelperEnabled = preferences[Keys.LiveHelperEnabled] ?: false,
                    enableAssExtractor = preferences[Keys.EnableAssExtractor] ?: false,
                    sleepTimerSeconds = preferences[Keys.SleepTimerSeconds] ?: 0,
                    volumeBoostCap = preferences[Keys.VolumeBoostCap] ?: 200,
                    audioChannels = runCatching {
                        AudioChannels.valueOf(preferences[Keys.AudioChannelsConfig] ?: "AutoSafe")
                    }.getOrDefault(AudioChannels.AutoSafe),
                    playerStatisticsPage = preferences[Keys.PlayerStatisticsPage] ?: 0,
                    playerSpeed = preferences[Keys.PlayerSpeed] ?: 1.0f,
                    scrobbleThreshold = (preferences[Keys.ScrobbleThreshold] ?: 0.85f).coerceIn(0.1f, 1.0f),
                    showLoadingCircle = preferences[Keys.ShowLoadingCircle] ?: true,
                    showCurrentChapter = preferences[Keys.ShowCurrentChapter] ?: true,
                    playerTimeToDisappearMs = (preferences[Keys.PlayerTimeToDisappearMs] ?: 5000).coerceIn(1000, 30000),
                    panelOpacity = (preferences[Keys.PanelOpacity] ?: 95).coerceIn(40, 100),
                    reducePlayerMotion = preferences[Keys.ReducePlayerMotion] ?: false,
                    showSystemStatusBar = preferences[Keys.ShowSystemStatusBar] ?: false,
                    playerFullscreenCutout = preferences[Keys.PlayerFullscreenCutout] ?: true,
                    invertDurationTimer = preferences[Keys.InvertDurationTimer] ?: false,
                    displayVolumeAsPercentage = preferences[Keys.DisplayVolumeAsPercentage] ?: true,
                    rememberPlayerBrightness = preferences[Keys.RememberPlayerBrightness] ?: false,
                    playerBrightnessValue = preferences[Keys.PlayerBrightnessValue] ?: -1f,
                    rememberPlayerVolume = preferences[Keys.RememberPlayerVolume] ?: false,
                    playerVolumeValue = preferences[Keys.PlayerVolumeValue] ?: -1f,
                    preserveWatchingPosition = preferences[Keys.PreserveWatchingPosition] ?: true,
                    defaultPlayerOrientation = preferences[Keys.DefaultPlayerOrientation] ?: "SENSOR_LANDSCAPE",
                    pipEpisodeToasts = preferences[Keys.PipEpisodeToasts] ?: true,
                    pipOnExit = preferences[Keys.PipOnExit] ?: true,
                    autoTrackEnabled = preferences[Keys.AutoTrackEnabled] ?: false,
                    autoTrackAddNew = preferences[Keys.AutoTrackAddNew] ?: true,
                    autoTrackRespectDropped = preferences[Keys.AutoTrackRespectDropped] ?: true,
                    quickRateEnabled = preferences[Keys.QuickRateEnabled] ?: true,
                    quickRateSeconds = preferences[Keys.QuickRateSeconds] ?: 20,
                    autoTrackSyncSources = preferences[Keys.AutoTrackSyncSources] ?: true,
                    autoTrackMinSeconds = preferences[Keys.AutoTrackMinSeconds] ?: 60
                ),
                mpvSubtitle = MpvAndSubtitleSettings(
                    defaultSubtitleSize = preferences[Keys.DefaultSubtitleSize] ?: 16,
                    subtitleFontSize = (preferences[Keys.SubtitleFontSize] ?: 45).coerceIn(1, 100),
                    defaultSubtitleColor = preferences[Keys.DefaultSubtitleColor] ?: 0xFFFFFFFF.toInt(),
                    subtitleBold = preferences[Keys.SubtitleBold] ?: false,
                    subtitleOutlineEnabled = preferences[Keys.SubtitleOutlineEnabled] ?: true,
                    subtitleDelayMs = preferences[Keys.SubtitleDelayMs] ?: 0L,
                    preferredSubtitleLanguages = preferences[Keys.PreferredSubtitleLanguages] ?: "tr",
                    addonSubtitleStartupMode = preferences[Keys.AddonSubtitleStartupMode] ?: "PREFERRED_ONLY",
                    subtitleSpeed = preferences[Keys.SubtitleSpeed] ?: 1f,
                    videoFiltersJson = preferences[Keys.VideoFiltersJson] ?: "{}",
                    speedPresets = preferences[Keys.SpeedPresets] ?: "0.25,0.5,0.75,1,1.25,1.5,1.75,2,2.5,3,3.5,4",
                    preferredAudioLanguages = preferences[Keys.PreferredAudioLanguages] ?: "tr,ja,en",
                    subtitleWhitelist = preferences[Keys.SubtitleWhitelist] ?: "",
                    subtitleBlacklist = preferences[Keys.SubtitleBlacklist] ?: "",
                    audioPitchCorrection = preferences[Keys.AudioPitchCorrection] ?: true,
                    mpvGpuRenderer = preferences[Keys.MpvGpuRenderer] ?: "gpu",
                    mpvHwdecMode = preferences[Keys.MpvHwdecMode] ?: "auto-safe",
                    mpvDolbyVisionAutoFix = preferences[Keys.MpvDolbyVisionAutoFix] ?: true,
                    mpvDebandMode = preferences[Keys.MpvDebandMode] ?: "none",
                    mpvForceYuv420p = preferences[Keys.MpvForceYuv420p] ?: false,
                    mpvDemuxerCacheMb = preferences[Keys.MpvDemuxerCacheMb] ?: 64,
                    secondarySubtitleTrackId = preferences[Keys.SecondarySubtitleTrackId] ?: -1,
                    secondarySubtitleDelayMs = preferences[Keys.SecondarySubtitleDelayMs] ?: 0L,
                    subtitleItalic = preferences[Keys.SubtitleItalic] ?: false,
                    subtitleJustification = preferences[Keys.SubtitleJustification] ?: "center",
                    subtitleBackgroundColor = preferences[Keys.SubtitleBackgroundColor] ?: 0,
                    subtitleShadowOffset = preferences[Keys.SubtitleShadowOffset] ?: 1.5f,
                    subtitleBorderColor = preferences[Keys.SubtitleBorderColor] ?: 0xFF000000.toInt(),
                    subtitleBorderSize = preferences[Keys.SubtitleBorderSize] ?: 1.5f,
                    subtitleFont = preferences[Keys.SubtitleFont] ?: "Sans Serif",
                    subtitleBorderStyle = preferences[Keys.SubtitleBorderStyle] ?: "outline-and-shadow",
                    subtitleFontScale = preferences[Keys.SubtitleFontScale] ?: 1.0f,
                    subtitlePos = preferences[Keys.SubtitlePos] ?: 100,
                    subtitleOverrideAss = preferences[Keys.SubtitleOverrideAss] ?: false,
                    screenshotSubtitles = preferences[Keys.ScreenshotSubtitles] ?: true,
                    mpvConf = preferences[Keys.MpvConf] ?: "",
                    mpvInput = preferences[Keys.MpvInput] ?: "",
                    mpvUserFilesEnabled = preferences[Keys.MpvUserFilesEnabled] ?: false
                ),
                gestures = GestureAndSkipSettings(
                    aniSkipEnabled = preferences[Keys.AniSkipEnabled] ?: true,
                    aniSkipAutoSkip = preferences[Keys.AniSkipAutoSkip] ?: false,
                    aniSkipDisableOnChapters = preferences[Keys.AniSkipDisableOnChapters] ?: false,
                    animeSkipClientId = preferences[Keys.AnimeSkipClientId] ?: "",
                    gestureVolumeEnabled = preferences[Keys.GestureVolumeEnabled] ?: true,
                    gestureBrightnessEnabled = preferences[Keys.GestureBrightnessEnabled] ?: true,
                    gestureZoomEnabled = preferences[Keys.GestureZoomEnabled] ?: true,
                    gestureScrollSensitivity = preferences[Keys.GestureScrollSensitivity] ?: 1.0f,
                    doubleTapSeekSeconds = preferences[Keys.DoubleTapSeekSeconds] ?: 10,
                    holdSpeedMultiplier = preferences[Keys.HoldSpeedMultiplier] ?: 2.0f,
                    swipeVolumeBrightnessSides = preferences[Keys.SwipeVolumeBrightnessSides] ?: true,
                    horizontalSeekGestureEnabled = preferences[Keys.HorizontalSeekGestureEnabled] ?: true,
                    preciseSeeking = preferences[Keys.PreciseSeeking] ?: false,
                    showSeekBarOnGesture = preferences[Keys.ShowSeekBarOnGesture] ?: true,
                    leftDoubleTapGesture = preferences[Keys.LeftDoubleTapGesture] ?: "SEEK",
                    centerDoubleTapGesture = preferences[Keys.CenterDoubleTapGesture] ?: "PLAY_PAUSE",
                    rightDoubleTapGesture = preferences[Keys.RightDoubleTapGesture] ?: "SEEK",
                    enableSkipIntro = preferences[Keys.EnableSkipIntro] ?: true,
                    enableNetflixStyleIntroSkip = preferences[Keys.EnableNetflixStyleIntroSkip] ?: false,
                    waitingTimeIntroSkip = (preferences[Keys.WaitingTimeIntroSkip] ?: 5).coerceIn(1, 30),
                    mediaPreviousGesture = preferences[Keys.MediaPreviousGesture] ?: "SWITCH",
                    mediaPlayPauseGesture = preferences[Keys.MediaPlayPauseGesture] ?: "PLAY_PAUSE",
                    mediaNextGesture = preferences[Keys.MediaNextGesture] ?: "SWITCH",
                    defaultIntroLength = preferences[Keys.DefaultIntroLength] ?: 85
                ),
                integrations = IntegrationsAndNotificationsSettings(
                    dnsChoice = preferences[Keys.DnsChoice] ?: 0,
                    useGithubProxy = preferences[Keys.UseGithubProxy] ?: false,
                    tmdbEnabled = preferences[Keys.TmdbEnabled] ?: true,
                    tmdbUserApiKey = preferences[Keys.TmdbUserApiKey] ?: "",
                    tmdbModernHomeEnabled = preferences[Keys.TmdbModernHomeEnabled] ?: true,
                    tmdbEnrichContinueWatching = preferences[Keys.TmdbEnrichContinueWatching] ?: true,
                    tmdbLanguage = preferences[Keys.TmdbLanguage] ?: "tr",
                    tmdbUseArtwork = preferences[Keys.TmdbUseArtwork] ?: true,
                    tmdbUseBasicInfo = preferences[Keys.TmdbUseBasicInfo] ?: true,
                    tmdbUseDetails = preferences[Keys.TmdbUseDetails] ?: true,
                    tmdbUseReleaseDates = preferences[Keys.TmdbUseReleaseDates] ?: true,
                    tmdbUseCredits = preferences[Keys.TmdbUseCredits] ?: true,
                    tmdbUseProductions = preferences[Keys.TmdbUseProductions] ?: true,
                    tmdbUseNetworks = preferences[Keys.TmdbUseNetworks] ?: true,
                    tmdbUseEpisodes = preferences[Keys.TmdbUseEpisodes] ?: true,
                    tmdbUseTrailers = preferences[Keys.TmdbUseTrailers] ?: true,
                    tmdbUseMoreLikeThis = preferences[Keys.TmdbUseMoreLikeThis] ?: true,
                    tmdbUseCollections = preferences[Keys.TmdbUseCollections] ?: true,
                    mdbListApiKey = preferences[Keys.MdbListApiKey] ?: "",
                    mdbListEnabled = preferences[Keys.MdbListEnabled] ?: false,
                    mdbListShowImdb = preferences[Keys.MdbListShowImdb] ?: true,
                    mdbListShowTomatoes = preferences[Keys.MdbListShowTomatoes] ?: true,
                    mdbListShowAudience = preferences[Keys.MdbListShowAudience] ?: false,
                    mdbListShowMetacritic = preferences[Keys.MdbListShowMetacritic] ?: true,
                    mdbListShowLetterboxd = preferences[Keys.MdbListShowLetterboxd] ?: false,
                    mdbListShowTmdb = preferences[Keys.MdbListShowTmdb] ?: false,
                    mdbListShowTrakt = preferences[Keys.MdbListShowTrakt] ?: false,
                    fanartTvApiKey = preferences[Keys.FanartTvApiKey] ?: "",
                    fanartTvEnabled = preferences[Keys.FanartTvEnabled] ?: true,
                    wikiPageTheme = preferences[Keys.WikiPageTheme] ?: "APP",
                    wikiTextZoom = preferences[Keys.WikiTextZoom] ?: 100,
                    wikiDesktopLayout = preferences[Keys.WikiDesktopLayout] ?: false,
                    wikiAdBlockDisabledHosts = preferences[Keys.WikiAdBlockDisabledHosts] ?: emptySet(),
                    autoTranslateEnabled = preferences[Keys.AutoTranslateEnabled] ?: false,
                    preferredTranslator = preferences[Keys.PreferredTranslator] ?: "DEFAULT",
                    translateSourceLanguage = preferences[Keys.TranslateSourceLanguage] ?: "auto",
                    translateTargetLanguage = preferences[Keys.TranslateTargetLanguage]
                        ?.takeIf { it.isNotBlank() }
                        ?: defaultTranslateTarget(preferences[Keys.AppLanguage]),
                    airingNotificationsEnabled = preferences[Keys.AiringNotificationsEnabled] ?: false,
                    aniListNotificationsEnabled = preferences[Keys.AniListNotificationsEnabled] ?: false,
                    malNotificationsEnabled = preferences[Keys.MalNotificationsEnabled] ?: false,
                    simklNotificationsEnabled = preferences[Keys.SimklNotificationsEnabled] ?: false,
                    kitsuNotificationsEnabled = preferences[Keys.KitsuNotificationsEnabled] ?: false,
                    shikimoriNotificationsEnabled = preferences[Keys.ShikimoriNotificationsEnabled] ?: false,
                    tmdbNotificationsEnabled = preferences[Keys.TmdbNotificationsEnabled] ?: false,
                    bangumiNotificationsEnabled = preferences[Keys.BangumiNotificationsEnabled] ?: false,
                    notificationInterval = preferences[Keys.NotificationInterval] ?: 180
                ),
                downloads = DownloadAndMangaSettings(
                    mangaReadingMode = preferences[Keys.MangaReadingMode] ?: "RightToLeft",
                    mangaColorFilter = preferences[Keys.MangaColorFilter] ?: "Normal",
                    mangaFitMode = preferences[Keys.MangaFitMode] ?: "FitScreen",
                    mangaBrightness = normalizeMangaBrightness(preferences[Keys.MangaBrightness] ?: 1.0f),
                    mangaSearchTitleLanguage = preferences[Keys.MangaSearchTitleLanguage] ?: "ENGLISH",
                    mangaSearchFollowTitleLanguage =
                        preferences[Keys.MangaSearchFollowTitleLanguage] ?: true,
                    customImageDownloadUri = preferences[Keys.CustomImageDownloadUri] ?: "",
                    videoDownloadUri = preferences[Keys.VideoDownloadUri] ?: "",
                    downloaderPreference = preferences[Keys.DownloaderPreference] ?: "INTERNAL",
                    downloadOnlyOverWifi = preferences[Keys.DownloadOnlyOverWifi] ?: true,
                    downloadSpeedLimit = preferences[Keys.DownloadSpeedLimit] ?: 0,
                    saveChaptersAsCBZ = preferences[Keys.SaveChaptersAsCBZ] ?: true,
                    splitTallImages = preferences[Keys.SplitTallImages] ?: true,
                    numberOfDownloads = preferences[Keys.NumberOfDownloads] ?: 1,
                    removeAfterMarkedAsRead = preferences[Keys.RemoveAfterMarkedAsRead] ?: false,
                    removeAfterReadSlots = preferences[Keys.RemoveAfterReadSlots] ?: -1,
                    removeBookmarkedChapters = preferences[Keys.RemoveBookmarkedChapters] ?: false,
                    downloadFillermarkedItems = preferences[Keys.DownloadFillermarkedItems] ?: false,
                    removeExcludeCategories = preferences[Keys.RemoveExcludeCategories] ?: emptySet(),
                    removeExcludeAnimeCategories = preferences[Keys.RemoveExcludeAnimeCategories] ?: emptySet(),
                    downloadNewEpisodes = preferences[Keys.DownloadNewEpisodes] ?: false,
                    downloadNewChapters = preferences[Keys.DownloadNewChapters] ?: false,
                    downloadNewEpisodeCategories = preferences[Keys.DownloadNewEpisodeCategories] ?: emptySet(),
                    downloadNewChapterCategories = preferences[Keys.DownloadNewChapterCategories] ?: emptySet(),
                    downloadNewEpisodeCategoriesExclude = preferences[Keys.DownloadNewEpisodeCategoriesExclude] ?: emptySet(),
                    downloadNewChapterCategoriesExclude = preferences[Keys.DownloadNewChapterCategoriesExclude] ?: emptySet(),
                    autoDownloadWhileWatching = preferences[Keys.AutoDownloadWhileWatching] ?: 0,
                    autoDownloadWhileReading = preferences[Keys.AutoDownloadWhileReading] ?: 0,
                    useExternalDownloader = preferences[Keys.UseExternalDownloader] ?: false,
                    externalDownloaderSelection = preferences[Keys.ExternalDownloaderSelection] ?: "",
                    downloadNewUnreadChaptersOnly = preferences[Keys.DownloadNewUnreadChaptersOnly] ?: false,
                    downloadNewUnseenEpisodesOnly = preferences[Keys.DownloadNewUnseenEpisodesOnly] ?: false,
                    subtitleDownloadLanguages = preferences[Keys.SubtitleDownloadLanguages] ?: "tr"
                ),
                keyboard = PlayerKeyboardSettings(
                    keyboardEnabled = preferences[Keys.KeyboardEnabled] ?: true,
                    autoDetectExternalKeyboard = preferences[Keys.KeyboardAutoDetect] ?: true,
                    keyboardOsdEnabled = preferences[Keys.KeyboardOsdEnabled] ?: true,
                    keyboardOsdDurationScale = (preferences[Keys.KeyboardOsdDurationScale] ?: 1f).coerceIn(0.35f, 2.5f),
                    keyboardOpensFullOsd = preferences[Keys.KeyboardOpensFullOsd] ?: false,
                    keyboardHelpEnabled = preferences[Keys.KeyboardHelpEnabled] ?: true,
                    disabledShortcuts = preferences[Keys.KeyboardDisabledShortcuts] ?: "",
                    customBindings = preferences[Keys.KeyboardCustomBindings] ?: "",
                    gamepadEnabled = preferences[Keys.KeyboardGamepadEnabled] ?: true,
                    keyboardSeekStepSeconds = (preferences[Keys.KeyboardSeekStepSeconds] ?: 5).coerceIn(1, 120),
                    keyboardPreciseSeekSeconds = (preferences[Keys.KeyboardPreciseSeekSeconds] ?: 1).coerceIn(1, 30),
                    keyboardLargeSeekSeconds = (preferences[Keys.KeyboardLargeSeekSeconds] ?: 60).coerceIn(5, 600),
                    keyboardHoldToAccelerate = preferences[Keys.KeyboardHoldToAccelerate] ?: true
                )
            )
            // Çeviri intent'leri (suspend olmayan bağlamlar) ayarlara senkron erişebilsin.
            com.kitsugi.animelist.utils.KitsugiTranslatePrefs.update(
                parsedSettings.translateSourceLanguage,
                parsedSettings.translateTargetLanguage
            )
            // Compose dışı istemciler (arama/senkron/kartlar) içerik tercihlerine senkron erişsin.
            com.kitsugi.animelist.data.settings.KitsugiContentPrefs.update(parsedSettings)
            emit(parsedSettings)
        }
    }

    /**
     * Kullanıcı hiç hedef dil seçmediyse çeviri hedefi uygulamanın aktif dilidir —
     * böylece çeviri tek bir dile (Türkçe) kilitlenmez, tüm dilleri destekler.
     */
    private fun defaultTranslateTarget(appLanguage: String? = null): String {
        val tag = appLanguage?.takeIf { it.isNotBlank() && it != "system" }
            ?: com.kitsugi.animelist.LocaleCache.localeTag.takeIf {
                it != com.kitsugi.animelist.LocaleCache.UNSET && it.isNotBlank()
            }
        return tag?.substringBefore('-')?.ifBlank { "tr" } ?: "tr"
    }

    suspend fun setCustomImageDownloadUri(uri: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.CustomImageDownloadUri] = uri
        }
    }

    suspend fun setVideoDownloadUri(uri: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.VideoDownloadUri] = uri
        }
    }

    suspend fun setDownloaderPreference(preference: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.DownloaderPreference] = preference
        }
    }

    suspend fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.AutoUpdateCheckEnabled] = enabled
        }
    }

    suspend fun setSearchHistoryEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.SearchHistoryEnabled] = enabled
        }
    }

    suspend fun setSelectedThemeId(themeId: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.SelectedThemeId] = themeId
        }
    }

    suspend fun setShowAdultContent(show: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.ShowAdultContent] = show
        }
    }

    suspend fun setBlurAdultMedia(blur: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.BlurAdultMedia] = blur
        }
    }

    suspend fun setSelectedListLayoutId(layoutId: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.SelectedListLayoutId] = layoutId
        }
    }

    suspend fun setProfileInfo(
        profileName: String,
        listTitle: String,
        anilistUsername: String
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.ProfileName] = profileName
            preferences[Keys.ListTitle] = listTitle
            preferences[Keys.AniListUsername] = anilistUsername
        }
    }

    suspend fun setAniListProfileInfo(
        username: String,
        profileImageUri: String,
        bannerImageUri: String
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.AniListUsername] = username
            preferences[Keys.AniListProfileImageUri] = profileImageUri
            preferences[Keys.AniListBannerImageUri] = bannerImageUri
        }
    }

    suspend fun setMalProfileInfo(
        username: String,
        profileImageUri: String,
        bannerImageUri: String
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.MalUsername] = username
            preferences[Keys.MalProfileImageUri] = profileImageUri
            preferences[Keys.MalBannerImageUri] = bannerImageUri
        }
    }

    suspend fun setSimklProfileInfo(
        username: String,
        profileImageUri: String,
        bannerImageUri: String
    ) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.SimklUsername] = username
            preferences[Keys.SimklProfileImageUri] = profileImageUri
            preferences[Keys.SimklBannerImageUri] = bannerImageUri
        }
    }

    suspend fun clearAniListProfileInfo() {
        context.settingsDataStore.edit { preferences ->
            preferences.remove(Keys.AniListUsername)
            preferences.remove(Keys.AniListProfileImageUri)
            preferences.remove(Keys.AniListBannerImageUri)
        }
    }

    suspend fun clearMalProfileInfo() {
        context.settingsDataStore.edit { preferences ->
            preferences.remove(Keys.MalUsername)
            preferences.remove(Keys.MalProfileImageUri)
            preferences.remove(Keys.MalBannerImageUri)
        }
    }

    suspend fun clearSimklProfileInfo() {
        context.settingsDataStore.edit { preferences ->
            preferences.remove(Keys.SimklUsername)
            preferences.remove(Keys.SimklProfileImageUri)
            preferences.remove(Keys.SimklBannerImageUri)
        }
    }

    suspend fun setProfileImageUri(uri: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.ProfileImageUri] = uri
        }
    }

    suspend fun setBannerImageUri(uri: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.BannerImageUri] = uri
        }
    }

    suspend fun clearProfileImageUri() {
        context.settingsDataStore.edit { preferences ->
            preferences.remove(Keys.ProfileImageUri)
        }
    }


    suspend fun clearBannerImageUri() {
        context.settingsDataStore.edit { preferences ->
            preferences.remove(Keys.BannerImageUri)
        }
    }

    // AniHyou'dan uyarlama: Başlık dili tercihi (Romaji / İngilizce / Japonca)
    suspend fun setTitleLanguage(titleLanguage: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.TitleLanguage] = titleLanguage
        }
    }

    // AniHyou'dan uyarlama: Puanlama formatı (POINT_10 / POINT_100 / POINT_5 / POINT_3)
    suspend fun setScoreFormat(scoreFormat: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.ScoreFormat] = scoreFormat
        }
    }

    // AniHyou'dan uyarlama: Puanlama adımları ("1" / "0.5")
    suspend fun setScoreStep(step: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.ScoreStep] = step
        }
    }

    // AniHyou'dan uyarlama: Ekip & karakter adı dili (ROMAJI / ENGLISH / NATIVE)
    suspend fun setStaffNameLanguage(language: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.StaffNameLanguage] = language
        }
    }

    // AniHyou'dan uyarlama: Ayrılmış liste tarzı
    suspend fun setSeparatedListStyle(separated: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.SeparatedListStyle] = separated
        }
    }

    // AniHyou'dan uyarlama: Düşük öncelik rozetini göster
    suspend fun setShowLowPriority(show: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.ShowLowPriority] = show
        }
    }

    // AniHyou'dan uyarlama: Öncelik renk tohumları JSON'ı
    suspend fun setPriorityColorsJson(json: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.PriorityColorsJson] = json
        }
    }

    // AniHyou'dan uyarlama: Fuzzy arama
    suspend fun setFuzzySearchEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.FuzzySearchEnabled] = enabled
        }
    }

    // AniHyou'dan uyarlama: Roman/manga ayrımı
    suspend fun setSeparateNovelsManga(separate: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.SeparateNovelsManga] = separate
        }
    }

    // Liste ekleme otomasyonu: duruma göre otomatik başlangıç/bitiş tarihi
    suspend fun setAutoDateOnStatusChange(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.AutoDateOnStatusChange] = enabled
        }
    }

    // Liste ekleme otomasyonu: "Tamamlandı"da ilerlemeyi toplam bölüme tamamla
    suspend fun setAutoProgressOnComplete(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.AutoProgressOnComplete] = enabled
        }
    }

    // Hızlı eklemede kullanılacak varsayılan durum (PLANNED / WATCHING / COMPLETED)
    suspend fun setDefaultAddStatus(status: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.DefaultAddStatus] = status
        }
    }

    // MoeList'ten uyarlama: Puanları gizle
    suspend fun setHideScores(hide: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.HideScores] = hide
        }
    }

    suspend fun setShowAnimeLogos(show: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.ShowAnimeLogos] = show
        }
    }

    suspend fun setDnsChoice(choice: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.DnsChoice] = choice
        }
    }

    suspend fun setUseGithubProxy(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.UseGithubProxy] = enabled
        }
    }

    val searchHistoryFlow: Flow<List<SearchHistoryItem>> = kotlinx.coroutines.flow.flow {
        context.settingsDataStore.data.collect { preferences ->
            val jsonStr = preferences[Keys.SearchHistory] ?: ""
            emit(deserializeHistory(jsonStr))
        }
    }

    suspend fun saveSearchHistory(history: List<SearchHistoryItem>) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.SearchHistory] = serializeHistory(history)
        }
    }

    private fun serializeHistory(history: List<SearchHistoryItem>): String {
        val array = JSONArray()
        history.forEach { item ->
            val obj = JSONObject()
                .put("query", item.query)
                .put("platform", item.platform.name)
                .put("mediaType", item.mediaType.name)
            array.put(obj)
        }
        return array.toString()
    }

    private fun deserializeHistory(jsonStr: String): List<SearchHistoryItem> {
        if (jsonStr.isBlank()) return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<SearchHistoryItem>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val query = obj.optString("query")
                val platformStr = obj.optString("platform")
                val mediaTypeStr = obj.optString("mediaType")

                val platform = runCatching { SearchPlatform.valueOf(platformStr) }.getOrDefault(SearchPlatform.All)
                val mediaType = runCatching { MediaType.valueOf(mediaTypeStr) }.getOrDefault(MediaType.Anime)

                if (query.isNotBlank()) {
                    list.add(SearchHistoryItem(query, platform, mediaType))
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun setPlayerPreference(pref: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.PlayerPreference] = pref
        }
    }

    suspend fun setAutoplayEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.IsAutoplayEnabled] = enabled
        }
    }

    suspend fun setSkipIntroDurationSec(sec: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.SkipIntroDurationSec] = sec
        }
    }

    suspend fun setDefaultSubtitleSize(size: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.DefaultSubtitleSize] = size
        }
    }

    suspend fun setDefaultSubtitleColor(color: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.DefaultSubtitleColor] = color
        }
    }

    suspend fun setSubtitleBold(bold: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.SubtitleBold] = bold
        }
    }

    suspend fun setSubtitleOutlineEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.SubtitleOutlineEnabled] = enabled
        }
    }

    suspend fun setDefaultAudioBoost(boost: Float) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.DefaultAudioBoost] = boost
        }
    }

    suspend fun setDefaultAudioDelayMs(delayMs: Long) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.DefaultAudioDelayMs] = delayMs
        }
    }

    suspend fun setBufferSettings(min: Int, max: Int, playback: Int, rebuffer: Int, back: Int) {
        val validatedMax = max.coerceAtLeast(1000)
        val validatedMin = min.coerceIn(0, validatedMax)
        val validatedPlayback = playback.coerceIn(0, validatedMax)
        val validatedRebuffer = rebuffer.coerceIn(0, validatedMax)
        val validatedBack = back.coerceAtLeast(0)

        context.settingsDataStore.edit { preferences ->
            preferences[Keys.MinBufferMs] = validatedMin
            preferences[Keys.MaxBufferMs] = validatedMax
            preferences[Keys.BufferForPlaybackMs] = validatedPlayback
            preferences[Keys.BufferForPlaybackAfterRebufferMs] = validatedRebuffer
            preferences[Keys.BackBufferDurationMs] = validatedBack
        }
    }

    suspend fun setMangaReadingMode(mode: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.MangaReadingMode] = mode
        }
    }

    suspend fun setMangaColorFilter(filter: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.MangaColorFilter] = filter
        }
    }

    suspend fun setMangaFitMode(fitMode: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.MangaFitMode] = fitMode
        }
    }

    suspend fun setMangaBrightness(brightness: Float) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.MangaBrightness] = normalizeMangaBrightness(brightness)
        }
    }

    /** Kaynak arama dili (ENGLISH / ROMAJI / NATIVE / LOCAL). */
    suspend fun setMangaSearchTitleLanguage(language: String) {
        val safe = when (language.uppercase()) {
            "ENGLISH", "EN" -> "ENGLISH"
            "NATIVE", "ORIGINAL" -> "NATIVE"
            "LOCAL", "TURKISH" -> "LOCAL"
            else -> "ROMAJI"
        }
        context.settingsDataStore.edit { it[Keys.MangaSearchTitleLanguage] = safe }
    }

    /** Arama dili genel başlık dili ayarını takip etsin mi. */
    suspend fun setMangaSearchFollowTitleLanguage(follow: Boolean) {
        context.settingsDataStore.edit { it[Keys.MangaSearchFollowTitleLanguage] = follow }
    }

    // ─── Harici API Entegrasyonları set fonksiyonları ─────────────────────────────────────

    suspend fun setTmdbUserApiKey(key: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.TmdbUserApiKey] = key.trim()
        }
        val language = context.settingsDataStore.data.first()[Keys.TmdbLanguage] ?: "tr"
        com.kitsugi.animelist.data.remote.TmdbApiClient.updateCache(key, language)
    }

    suspend fun setMdbListApiKey(key: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.MdbListApiKey] = key.trim()
        }
    }

    suspend fun setMdbListEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.MdbListEnabled] = enabled
        }
    }

    suspend fun setMdbListShowImdb(show: Boolean) {
        context.settingsDataStore.edit { it[Keys.MdbListShowImdb] = show }
    }

    suspend fun setMdbListShowTomatoes(show: Boolean) {
        context.settingsDataStore.edit { it[Keys.MdbListShowTomatoes] = show }
    }

    suspend fun setMdbListShowAudience(show: Boolean) {
        context.settingsDataStore.edit { it[Keys.MdbListShowAudience] = show }
    }

    suspend fun setMdbListShowMetacritic(show: Boolean) {
        context.settingsDataStore.edit { it[Keys.MdbListShowMetacritic] = show }
    }

    suspend fun setMdbListShowLetterboxd(show: Boolean) {
        context.settingsDataStore.edit { it[Keys.MdbListShowLetterboxd] = show }
    }

    suspend fun setMdbListShowTmdb(show: Boolean) {
        context.settingsDataStore.edit { it[Keys.MdbListShowTmdb] = show }
    }

    suspend fun setMdbListShowTrakt(show: Boolean) {
        context.settingsDataStore.edit { it[Keys.MdbListShowTrakt] = show }
    }

    suspend fun setFanartTvApiKey(key: String) {
        context.settingsDataStore.edit { it[Keys.FanartTvApiKey] = key.trim() }
    }

    suspend fun setFanartTvEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.FanartTvEnabled] = enabled }
    }

    suspend fun setAniSkipEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.AniSkipEnabled] = enabled
        }
    }

    suspend fun setAniSkipAutoSkip(autoSkip: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.AniSkipAutoSkip] = autoSkip
        }
    }

    suspend fun setAniSkipDisableOnChapters(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.AniSkipDisableOnChapters] = enabled
        }
    }

    suspend fun setTmdbEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.TmdbEnabled] = enabled }
    }

    suspend fun setTmdbModernHomeEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.TmdbModernHomeEnabled] = enabled }
    }

    suspend fun setTmdbEnrichContinueWatching(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.TmdbEnrichContinueWatching] = enabled }
    }

    suspend fun setTmdbLanguage(lang: String) {
        context.settingsDataStore.edit { it[Keys.TmdbLanguage] = lang }
        runCatching {
            val userApiKey = context.settingsDataStore.data.first()[Keys.TmdbUserApiKey] ?: ""
            com.kitsugi.animelist.data.remote.TmdbApiClient.updateCache(userApiKey, lang)
        }
    }

    suspend fun setTmdbUseArtwork(use: Boolean) {
        context.settingsDataStore.edit { it[Keys.TmdbUseArtwork] = use }
    }

    suspend fun setTmdbUseBasicInfo(use: Boolean) {
        context.settingsDataStore.edit { it[Keys.TmdbUseBasicInfo] = use }
    }

    suspend fun setTmdbUseDetails(use: Boolean) {
        context.settingsDataStore.edit { it[Keys.TmdbUseDetails] = use }
    }

    suspend fun setTmdbUseReleaseDates(use: Boolean) {
        context.settingsDataStore.edit { it[Keys.TmdbUseReleaseDates] = use }
    }

    suspend fun setTmdbUseCredits(use: Boolean) {
        context.settingsDataStore.edit { it[Keys.TmdbUseCredits] = use }
    }

    suspend fun setTmdbUseProductions(use: Boolean) {
        context.settingsDataStore.edit { it[Keys.TmdbUseProductions] = use }
    }

    suspend fun setTmdbUseNetworks(use: Boolean) {
        context.settingsDataStore.edit { it[Keys.TmdbUseNetworks] = use }
    }

    suspend fun setTmdbUseEpisodes(use: Boolean) {
        context.settingsDataStore.edit { it[Keys.TmdbUseEpisodes] = use }
    }

    suspend fun setTmdbUseTrailers(use: Boolean) {
        context.settingsDataStore.edit { it[Keys.TmdbUseTrailers] = use }
    }

    suspend fun setTmdbUseMoreLikeThis(use: Boolean) {
        context.settingsDataStore.edit { it[Keys.TmdbUseMoreLikeThis] = use }
    }

    suspend fun setTmdbUseCollections(use: Boolean) {
        context.settingsDataStore.edit { it[Keys.TmdbUseCollections] = use }
    }

    suspend fun setAnimeSkipClientId(clientId: String) {
        context.settingsDataStore.edit { it[Keys.AnimeSkipClientId] = clientId }
    }

    suspend fun setWikiReadingAppearance(theme: String, textZoom: Int) {
        val mode = theme.takeIf { it in setOf("SITE", "APP", "LIGHT", "DARK") } ?: "SITE"
        context.settingsDataStore.edit {
            it[Keys.WikiPageTheme] = mode
            it[Keys.WikiTextZoom] = textZoom.coerceIn(80, 180)
        }
    }

    /** Wiki açılır sayfasının görünüm düzeni; seçim sekmeler ve oturumlar arasında korunur. */
    suspend fun setWikiDesktopLayout(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.WikiDesktopLayout] = enabled }
    }

    /** Local per-wiki exception; never changes filtering in other application WebViews. */
    suspend fun setWikiAdBlockEnabled(host: String, enabled: Boolean) {
        val normalized = host.lowercase(java.util.Locale.ROOT)
        if (!Regex("[a-z0-9.-]{1,253}").matches(normalized)) return
        context.settingsDataStore.edit {
            val disabled = it[Keys.WikiAdBlockDisabledHosts].orEmpty()
            it[Keys.WikiAdBlockDisabledHosts] = if (enabled) disabled - normalized
                else (disabled + normalized).toList().takeLast(128).toSet()
        }
    }

    suspend fun setAutoTranslateEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AutoTranslateEnabled] = enabled }
    }

    suspend fun setPreferredTranslator(translator: String) {
        context.settingsDataStore.edit { it[Keys.PreferredTranslator] = translator }
    }

    suspend fun setTranslateSourceLanguage(lang: String) {
        context.settingsDataStore.edit { it[Keys.TranslateSourceLanguage] = lang }
    }

    suspend fun setTranslateTargetLanguage(lang: String) {
        context.settingsDataStore.edit { it[Keys.TranslateTargetLanguage] = lang }
    }

    suspend fun setSelectedHomeLayoutId(layoutId: String) {
        context.settingsDataStore.edit { it[Keys.SelectedHomeLayoutId] = layoutId }
    }

    suspend fun setFrameRateMatchingMode(mode: FrameRateMatchingMode) {
        context.settingsDataStore.edit { it[Keys.FrameRateMatchingMode] = mode.name }
    }

    suspend fun setResolutionMatchingEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.ResolutionMatchingEnabled] = enabled }
    }

    suspend fun setDecoderPriority(priority: Int) {
        context.settingsDataStore.edit { it[Keys.DecoderPriority] = priority }
    }

    suspend fun setDv7HandlingMode(mode: Dv7HandlingMode) {
        context.settingsDataStore.edit { it[Keys.Dv7HandlingMode] = mode.name }
    }

    suspend fun setStripHdr10PlusSei(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.StripHdr10PlusSei] = enabled }
    }

    suspend fun setThemeMode(mode: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.ThemeMode] = mode
        }
    }

    suspend fun setAmoledBlack(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.AmoledBlack] = enabled
        }
    }

    suspend fun setCardFramesEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.CardFramesEnabled] = enabled
        }
    }

    suspend fun setCustomAccentColor(color: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.CustomAccentColor] = color
        }
    }

    suspend fun setCustomAccentColor2(color: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.CustomAccentColor2] = color
        }
    }

    suspend fun setCustomAccentGradientAngle(angle: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.CustomAccentGradientAngle] = angle
        }
    }

    suspend fun setDefaultTab(tab: String) {
        runCatching {
            context.getSharedPreferences("kitsugi_boot_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("default_tab", tab)
                .apply()
        }
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.DefaultTab] = tab
        }
    }

    suspend fun setLastUsedTab(tab: String) {
        runCatching {
            context.getSharedPreferences("kitsugi_boot_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("last_used_tab", tab)
                .apply()
        }
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.LastUsedTab] = tab
        }
    }

    suspend fun setAppLanguage(language: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.AppLanguage] = language
        }
    }

    suspend fun setAccountBackupEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.AccountBackupEnabled] = enabled
        }
    }

    suspend fun setAccountAddonSyncEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.AccountAddonSyncEnabled] = enabled
        }
    }

    suspend fun setAccountSearchSyncEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.AccountSearchSyncEnabled] = enabled
        }
    }

    suspend fun setAccountNotificationSyncEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.AccountNotificationSyncEnabled] = enabled
        }
    }

    suspend fun setFixedNavBar(fixed: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.FixedNavBar] = fixed
        }
    }

    suspend fun setAspectMode(mode: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.AspectMode] = mode
        }
    }

    suspend fun setGainBoostDb(gain: Float) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.GainBoostDb] = gain
        }
    }

    suspend fun setSubtitleDelayMs(delayMs: Long) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.SubtitleDelayMs] = delayMs
        }
    }

    suspend fun setPreferredSubtitleLanguages(langs: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.PreferredSubtitleLanguages] = langs
        }
    }

    suspend fun setAddonSubtitleStartupMode(mode: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.AddonSubtitleStartupMode] = mode
        }
    }

    suspend fun setSubtitleDownloadLanguages(langs: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.SubtitleDownloadLanguages] = langs
        }
    }

    suspend fun setGestureVolumeEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.GestureVolumeEnabled] = enabled }
    }

    suspend fun setGestureBrightnessEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.GestureBrightnessEnabled] = enabled }
    }

    suspend fun setGestureZoomEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.GestureZoomEnabled] = enabled }
    }

    /** TASK_050 — Dikey swipe hassasiyetini kaydet (0.5…2.0) */
    suspend fun setGestureScrollSensitivity(sensitivity: Float) {
        context.settingsDataStore.edit { it[Keys.GestureScrollSensitivity] = sensitivity.coerceIn(0.25f, 3.0f) }
    }

    suspend fun setDoubleTapSeekSeconds(seconds: Int) {
        context.settingsDataStore.edit { it[Keys.DoubleTapSeekSeconds] = seconds }
    }

    suspend fun setHoldSpeedMultiplier(multiplier: Float) {
        context.settingsDataStore.edit { it[Keys.HoldSpeedMultiplier] = multiplier }
    }

    suspend fun setPlayerSpeed(speed: Float) {
        context.settingsDataStore.edit { it[Keys.PlayerSpeed] = speed }
    }

    suspend fun setPipEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.PipEnabled] = enabled }
    }

    suspend fun setAudioDelayPerRouteJson(json: String) {
        context.settingsDataStore.edit { it[Keys.AudioDelayPerRouteJson] = json }
    }

    // ─── Quality Profile (T2.4) ───────────────────────────────────────────
    suspend fun setQualityProfileJson(json: String) {
        context.settingsDataStore.edit { it[Keys.QualityProfileJson] = json }
    }

    // ─── Airing Notifications (T3.3) ─────────────────────────────────────
    suspend fun setAiringNotificationsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AiringNotificationsEnabled] = enabled }
    }

    suspend fun setAniListNotificationsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AniListNotificationsEnabled] = enabled }
    }

    suspend fun setMalNotificationsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.MalNotificationsEnabled] = enabled }
    }

    suspend fun setSimklNotificationsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SimklNotificationsEnabled] = enabled }
    }

    /** Kitsu yayın bildirimleri (Kitsu'nun genel API'sinde bildirim ucu yoktur; yayın tarihi akışı). */
    suspend fun setKitsuNotificationsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.KitsuNotificationsEnabled] = enabled }
    }

    /** Shikimori gerçek bildirimleri (messages izni gerekir). */
    suspend fun setShikimoriNotificationsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.ShikimoriNotificationsEnabled] = enabled }
    }

    /** TMDB account polling/dedupe notifications (not a TMDB native notification feed). */
    suspend fun setTmdbNotificationsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.TmdbNotificationsEnabled] = enabled }
    }

    /** Bangumi bildirimleri (DOING koleksiyonu + yayın takvimi eşleşmesi). */
    suspend fun setBangumiNotificationsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.BangumiNotificationsEnabled] = enabled }
    }

    suspend fun setNotificationInterval(minutes: Int) {
        context.settingsDataStore.edit { it[Keys.NotificationInterval] = minutes }
    }

    // ─── T1.7 – StillWatching + PostPlayMode + AutoplaySessionRules ──────────
    suspend fun setStillWatchingEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.StillWatchingEnabled] = enabled }
    }

    suspend fun setStillWatchingThresholdMinutes(minutes: Int) {
        context.settingsDataStore.edit { it[Keys.StillWatchingThresholdMinutes] = minutes }
    }

    suspend fun setPostPlayMode(mode: String) {
        context.settingsDataStore.edit { it[Keys.PostPlayMode] = mode }
    }

    suspend fun setAutoplaySessionLimit(limit: Int) {
        context.settingsDataStore.edit { it[Keys.AutoplaySessionLimit] = limit }
    }

    // ─── T1.9 – Paralel Aralık İndirme ───────────────────────────────
    suspend fun setParallelRangeEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.ParallelRangeEnabled] = enabled }
    }

    // ─── T2.2 – Önizleme Seekbar ───────────────────────────────
    suspend fun setPreviewSeekbarEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.PreviewSeekbarEnabled] = enabled }
    }

    // ─── T2.5 – Harici Oynatıcı Tercihi ────────────────────────
    suspend fun setPreferredExternalPlayerPackage(packageName: String) {
        context.settingsDataStore.edit { it[Keys.PreferredExternalPlayerPackage] = packageName }
    }

    // --- T2.6 - Player Title / Media Info Visibility ---
    suspend fun setShowPlayerTitle(show: Boolean) {
        context.settingsDataStore.edit { it[Keys.ShowPlayerTitle] = show }
    }

    suspend fun setShowPlayerResolution(show: Boolean) {
        context.settingsDataStore.edit { it[Keys.ShowPlayerResolution] = show }
    }

    suspend fun setShowMediaInfo(show: Boolean) {
        context.settingsDataStore.edit { it[Keys.ShowMediaInfo] = show }
    }

    suspend fun setTitleLimitType(limitType: String) {
        context.settingsDataStore.edit { it[Keys.TitleLimitType] = limitType }
    }

    suspend fun setSplashAnimationEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SplashAnimationEnabled] = enabled }
        runCatching {
            context.getSharedPreferences("kitsugi_splash_cache", android.content.Context.MODE_PRIVATE)
                .edit().putBoolean("splash_animation_enabled", enabled).apply()
        }
    }

    suspend fun setSplashSoundEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SplashSoundEnabled] = enabled }
        runCatching {
            context.getSharedPreferences("kitsugi_splash_cache", android.content.Context.MODE_PRIVATE)
                .edit().putBoolean("splash_sound_enabled", enabled).apply()
        }
    }

    suspend fun setLiveHelperEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.LiveHelperEnabled] = enabled }
    }

    suspend fun setEnableAssExtractor(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.EnableAssExtractor] = enabled }
    }

    suspend fun setSyncEnabledAnilist(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SyncEnabledAnilist] = enabled }
    }

    suspend fun setSyncEnabledMal(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SyncEnabledMal] = enabled }
    }

    suspend fun setSyncEnabledSimkl(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SyncEnabledSimkl] = enabled }
    }

    suspend fun setSyncEnabledKitsu(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SyncEnabledKitsu] = enabled }
    }

    suspend fun setSyncEnabledShikimori(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SyncEnabledShikimori] = enabled }
    }

    suspend fun setSyncEnabledBangumi(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SyncEnabledBangumi] = enabled }
    }

    suspend fun setSyncEnabledTmdb(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SyncEnabledTmdb] = enabled }
    }

    suspend fun saveKitsuProfileInfo(username: String, avatarUrl: String? = null) {
        context.settingsDataStore.edit {
            it[Keys.KitsuUsername] = username
            if (avatarUrl != null) it[Keys.KitsuProfileImageUri] = avatarUrl
        }
    }

    suspend fun clearKitsuProfileInfo() {
        context.settingsDataStore.edit {
            it.remove(Keys.KitsuUsername)
            it.remove(Keys.KitsuProfileImageUri)
        }
    }

    suspend fun saveShikimoriProfileInfo(username: String, avatarUrl: String? = null) {
        context.settingsDataStore.edit {
            it[Keys.ShikimoriUsername] = username
            if (avatarUrl != null) it[Keys.ShikimoriProfileImageUri] = avatarUrl
        }
    }

    suspend fun clearShikimoriProfileInfo() {
        context.settingsDataStore.edit {
            it.remove(Keys.ShikimoriUsername)
            it.remove(Keys.ShikimoriProfileImageUri)
        }
    }

    suspend fun saveBangumiProfileInfo(username: String, avatarUrl: String? = null) {
        context.settingsDataStore.edit {
            it[Keys.BangumiUsername] = username
            if (avatarUrl != null) it[Keys.BangumiProfileImageUri] = avatarUrl
        }
    }

    suspend fun clearBangumiProfileInfo() {
        context.settingsDataStore.edit {
            it.remove(Keys.BangumiUsername)
            it.remove(Keys.BangumiProfileImageUri)
        }
    }

    // ─── MPV Gelişmiş Oynatıcı Ayarları ─────────────────────────────────────

    suspend fun setMpvGpuRenderer(renderer: String) {
        context.settingsDataStore.edit { it[Keys.MpvGpuRenderer] = renderer }
    }

    suspend fun setMpvHwdecMode(mode: String) {
        context.settingsDataStore.edit { it[Keys.MpvHwdecMode] = mode }
    }

    suspend fun setMpvDolbyVisionAutoFix(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.MpvDolbyVisionAutoFix] = enabled }
    }

    suspend fun setMpvDebandMode(mode: String) {
        context.settingsDataStore.edit { it[Keys.MpvDebandMode] = mode }
    }

    suspend fun setMpvForceYuv420p(force: Boolean) {
        context.settingsDataStore.edit { it[Keys.MpvForceYuv420p] = force }
    }

    suspend fun setMpvDemuxerCacheMb(mb: Int) {
        context.settingsDataStore.edit { it[Keys.MpvDemuxerCacheMb] = mb.coerceIn(8, 512) }
    }

    // ─── İkincil Altyazı ──────────────────────────────────────────────────────

    suspend fun setSecondarySubtitleTrackId(trackId: Int) {
        context.settingsDataStore.edit { it[Keys.SecondarySubtitleTrackId] = trackId }
    }

    suspend fun setSecondarySubtitleDelayMs(delayMs: Long) {
        context.settingsDataStore.edit { it[Keys.SecondarySubtitleDelayMs] = delayMs }
    }

    // ─── Altyazı Gelişmiş Stil ───────────────────────────────────────────────────

    suspend fun setSubtitleItalic(italic: Boolean) {
        context.settingsDataStore.edit { it[Keys.SubtitleItalic] = italic }
    }

    suspend fun setSubtitleJustification(justification: String) {
        context.settingsDataStore.edit { it[Keys.SubtitleJustification] = justification }
    }

    suspend fun setSubtitleBackgroundColor(color: Int) {
        context.settingsDataStore.edit { it[Keys.SubtitleBackgroundColor] = color }
    }

    suspend fun setSubtitleShadowOffset(offset: Float) {
        context.settingsDataStore.edit { it[Keys.SubtitleShadowOffset] = offset.coerceIn(0f, 8f) }
    }

    suspend fun setSubtitleBorderColor(color: Int) {
        context.settingsDataStore.edit { it[Keys.SubtitleBorderColor] = color }
    }

    suspend fun setSubtitleBorderSize(size: Float) {
        context.settingsDataStore.edit { it[Keys.SubtitleBorderSize] = size.coerceIn(0f, 6f) }
    }

    suspend fun setSubtitleFontSize(size: Int) {
        context.settingsDataStore.edit { it[Keys.SubtitleFontSize] = size.coerceIn(1, 100) }
    }

    suspend fun setSubtitleFont(font: String) {
        context.settingsDataStore.edit { it[Keys.SubtitleFont] = font }
    }

    suspend fun setSubtitleBorderStyle(style: String) {
        context.settingsDataStore.edit { it[Keys.SubtitleBorderStyle] = style }
    }

    suspend fun setSubtitleFontScale(scale: Float) {
        context.settingsDataStore.edit { it[Keys.SubtitleFontScale] = scale.coerceIn(0.1f, 5f) }
    }

    suspend fun setSubtitlePos(pos: Int) {
        context.settingsDataStore.edit { it[Keys.SubtitlePos] = pos.coerceIn(0, 150) }
    }

    suspend fun setSubtitleOverrideAss(override: Boolean) {
        context.settingsDataStore.edit { it[Keys.SubtitleOverrideAss] = override }
    }

    // ─── Uyku Zamanlayıcısı ─────────────────────────────────────────────────────

    suspend fun setSleepTimerSeconds(seconds: Int) {
        context.settingsDataStore.edit { it[Keys.SleepTimerSeconds] = seconds.coerceAtLeast(0) }
    }

    // ─── Ses Boost Sınırı ───────────────────────────────────────────────────────

    suspend fun setVolumeBoostCap(cap: Int) {
        context.settingsDataStore.edit { it[Keys.VolumeBoostCap] = cap.coerceIn(100, 200) }
    }

    // ─── Gesture genişleme ───────────────────────────────────────────────────

    suspend fun setSwipeVolumeBrightnessSides(normalSides: Boolean) {
        context.settingsDataStore.edit { it[Keys.SwipeVolumeBrightnessSides] = normalSides }
    }

    suspend fun setHorizontalSeekGestureEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.HorizontalSeekGestureEnabled] = enabled }
    }

    suspend fun setPreciseSeeking(precise: Boolean) {
        context.settingsDataStore.edit { it[Keys.PreciseSeeking] = precise }
    }

    suspend fun setAudioChannels(channels: AudioChannels) {
        context.settingsDataStore.edit { it[Keys.AudioChannelsConfig] = channels.name }
    }

    suspend fun setPlayerStatisticsPage(page: Int) {
        context.settingsDataStore.edit { it[Keys.PlayerStatisticsPage] = page }
    }

    // ─── Yeni İndirme Ayarları (Aniyomi Uyumlu Setterlar) ──────────────────────────

    suspend fun setDownloadOnlyOverWifi(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DownloadOnlyOverWifi] = enabled }
    }

    suspend fun setDownloadSpeedLimit(limit: Int) {
        context.settingsDataStore.edit { it[Keys.DownloadSpeedLimit] = limit }
    }

    suspend fun setSaveChaptersAsCBZ(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SaveChaptersAsCBZ] = enabled }
    }

    suspend fun setSplitTallImages(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SplitTallImages] = enabled }
    }

    suspend fun setNumberOfDownloads(slots: Int) {
        context.settingsDataStore.edit { it[Keys.NumberOfDownloads] = slots }
    }

    suspend fun setRemoveAfterMarkedAsRead(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.RemoveAfterMarkedAsRead] = enabled }
    }

    suspend fun setRemoveAfterReadSlots(slots: Int) {
        context.settingsDataStore.edit { it[Keys.RemoveAfterReadSlots] = slots }
    }

    suspend fun setRemoveBookmarkedChapters(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.RemoveBookmarkedChapters] = enabled }
    }

    suspend fun setDownloadFillermarkedItems(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DownloadFillermarkedItems] = enabled }
    }

    suspend fun setRemoveExcludeCategories(categories: Set<String>) {
        context.settingsDataStore.edit { it[Keys.RemoveExcludeCategories] = categories }
    }

    suspend fun setRemoveExcludeAnimeCategories(categories: Set<String>) {
        context.settingsDataStore.edit { it[Keys.RemoveExcludeAnimeCategories] = categories }
    }

    suspend fun setDownloadNewEpisodes(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DownloadNewEpisodes] = enabled }
    }

    suspend fun setDownloadNewChapters(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DownloadNewChapters] = enabled }
    }

    suspend fun setDownloadNewEpisodeCategories(categories: Set<String>) {
        context.settingsDataStore.edit { it[Keys.DownloadNewEpisodeCategories] = categories }
    }

    suspend fun setDownloadNewChapterCategories(categories: Set<String>) {
        context.settingsDataStore.edit { it[Keys.DownloadNewChapterCategories] = categories }
    }

    suspend fun setDownloadNewEpisodeCategoriesExclude(categories: Set<String>) {
        context.settingsDataStore.edit { it[Keys.DownloadNewEpisodeCategoriesExclude] = categories }
    }

    suspend fun setDownloadNewChapterCategoriesExclude(categories: Set<String>) {
        context.settingsDataStore.edit { it[Keys.DownloadNewChapterCategoriesExclude] = categories }
    }

    suspend fun setAutoDownloadWhileWatching(episodes: Int) {
        context.settingsDataStore.edit { it[Keys.AutoDownloadWhileWatching] = episodes }
    }

    suspend fun setAutoDownloadWhileReading(chapters: Int) {
        context.settingsDataStore.edit { it[Keys.AutoDownloadWhileReading] = chapters }
    }

    suspend fun setUseExternalDownloader(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.UseExternalDownloader] = enabled }
    }

    suspend fun setExternalDownloaderSelection(selection: String) {
        context.settingsDataStore.edit { it[Keys.ExternalDownloaderSelection] = selection }
    }

    suspend fun setDownloadNewUnreadChaptersOnly(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DownloadNewUnreadChaptersOnly] = enabled }
    }

    suspend fun setDownloadNewUnseenEpisodesOnly(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DownloadNewUnseenEpisodesOnly] = enabled }
    }
    suspend fun setSubtitleSpeed(value: Float) {
        context.settingsDataStore.edit { it[Keys.SubtitleSpeed] = value }
    }

    suspend fun setVideoFiltersJson(value: String) {
        context.settingsDataStore.edit { it[Keys.VideoFiltersJson] = value }
    }

    suspend fun setSpeedPresets(value: String) {
        context.settingsDataStore.edit { it[Keys.SpeedPresets] = value }
    }

    suspend fun setPreferredAudioLanguages(value: String) {
        context.settingsDataStore.edit { it[Keys.PreferredAudioLanguages] = value }
    }

    suspend fun setSubtitleWhitelist(value: String) {
        context.settingsDataStore.edit { it[Keys.SubtitleWhitelist] = value }
    }

    suspend fun setSubtitleBlacklist(value: String) {
        context.settingsDataStore.edit { it[Keys.SubtitleBlacklist] = value }
    }

    suspend fun setAudioPitchCorrection(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.AudioPitchCorrection] = value }
    }

    suspend fun setScrobbleThreshold(value: Float) {
        context.settingsDataStore.edit { it[Keys.ScrobbleThreshold] = value.coerceIn(0.1f, 1.0f) }
    }

    // ─── Otomatik liste takibi ───────────────────────────────────────────────────

    suspend fun setAutoTrackEnabled(value: Boolean) {
        context.settingsDataStore.edit {
            it[Keys.AutoTrackEnabled] = value
            // true değeri yalnızca onay diyaloğu kabul edildikten sonra yazılır.
            if (value) it[Keys.AutoTrackExperimentalConsent] = true
        }
    }

    suspend fun setAutoTrackAddNew(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.AutoTrackAddNew] = value }
    }

    suspend fun setAutoTrackRespectDropped(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.AutoTrackRespectDropped] = value }
    }

    suspend fun setQuickRateEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.QuickRateEnabled] = value }
    }

    suspend fun setQuickRateSeconds(value: Int) {
        context.settingsDataStore.edit { it[Keys.QuickRateSeconds] = value.coerceIn(5, 60) }
    }

    suspend fun setAutoTrackSyncSources(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.AutoTrackSyncSources] = value }
    }

    suspend fun setAutoTrackMinSeconds(value: Int) {
        context.settingsDataStore.edit { it[Keys.AutoTrackMinSeconds] = value.coerceIn(15, 600) }
    }

    suspend fun setShowLoadingCircle(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.ShowLoadingCircle] = value }
    }

    suspend fun setShowCurrentChapter(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.ShowCurrentChapter] = value }
    }

    suspend fun setPlayerTimeToDisappearMs(value: Int) {
        context.settingsDataStore.edit { it[Keys.PlayerTimeToDisappearMs] = value.coerceIn(1000, 30000) }
    }

    suspend fun setPanelOpacity(value: Int) {
        context.settingsDataStore.edit { it[Keys.PanelOpacity] = value.coerceIn(40, 100) }
    }

    suspend fun setReducePlayerMotion(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.ReducePlayerMotion] = value }
    }

    suspend fun setShowSystemStatusBar(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.ShowSystemStatusBar] = value }
    }

    suspend fun setPlayerFullscreenCutout(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.PlayerFullscreenCutout] = value }
    }

    suspend fun setInvertDurationTimer(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.InvertDurationTimer] = value }
    }

    suspend fun setDisplayVolumeAsPercentage(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.DisplayVolumeAsPercentage] = value }
    }

    suspend fun setShowSeekBarOnGesture(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.ShowSeekBarOnGesture] = value }
    }

    suspend fun setRememberPlayerBrightness(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.RememberPlayerBrightness] = value }
    }

    suspend fun setPlayerBrightnessValue(value: Float) {
        context.settingsDataStore.edit { it[Keys.PlayerBrightnessValue] = value }
    }

    suspend fun setRememberPlayerVolume(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.RememberPlayerVolume] = value }
    }

    suspend fun setPlayerVolumeValue(value: Float) {
        context.settingsDataStore.edit { it[Keys.PlayerVolumeValue] = value }
    }

    suspend fun setLeftDoubleTapGesture(value: String) {
        context.settingsDataStore.edit { it[Keys.LeftDoubleTapGesture] = value }
    }

    suspend fun setCenterDoubleTapGesture(value: String) {
        context.settingsDataStore.edit { it[Keys.CenterDoubleTapGesture] = value }
    }

    suspend fun setRightDoubleTapGesture(value: String) {
        context.settingsDataStore.edit { it[Keys.RightDoubleTapGesture] = value }
    }

    suspend fun setEnableSkipIntro(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.EnableSkipIntro] = value }
    }

    suspend fun setEnableNetflixStyleIntroSkip(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.EnableNetflixStyleIntroSkip] = value }
    }

    suspend fun setWaitingTimeIntroSkip(value: Int) {
        context.settingsDataStore.edit { it[Keys.WaitingTimeIntroSkip] = value.coerceIn(1, 30) }
    }

    suspend fun setScreenshotSubtitles(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.ScreenshotSubtitles] = value }
    }

    // ── Klavye / gamepad kısayolları ─────────────────────────────────────────
    suspend fun setKeyboardEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.KeyboardEnabled] = enabled }
    }

    suspend fun setKeyboardAutoDetect(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.KeyboardAutoDetect] = enabled }
    }

    suspend fun setKeyboardOsdEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.KeyboardOsdEnabled] = enabled }
    }

    suspend fun setKeyboardOsdDurationScale(scale: Float) {
        context.settingsDataStore.edit { it[Keys.KeyboardOsdDurationScale] = scale.coerceIn(0.35f, 2.5f) }
    }

    /** Tam OSD (üst/alt bar) klavye ile açılsın mı? Varsayılan: hayır, yalnızca gösterge. */
    suspend fun setKeyboardOpensFullOsd(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.KeyboardOpensFullOsd] = enabled }
    }

    suspend fun setKeyboardHelpEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.KeyboardHelpEnabled] = enabled }
    }

    suspend fun setKeyboardDisabledShortcuts(raw: String) {
        context.settingsDataStore.edit { it[Keys.KeyboardDisabledShortcuts] = raw }
    }

    suspend fun setKeyboardCustomBindings(raw: String) {
        context.settingsDataStore.edit { it[Keys.KeyboardCustomBindings] = raw }
    }

    suspend fun setGamepadEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.KeyboardGamepadEnabled] = enabled }
    }

    suspend fun setKeyboardSeekStepSeconds(seconds: Int) {
        context.settingsDataStore.edit { it[Keys.KeyboardSeekStepSeconds] = seconds.coerceIn(1, 120) }
    }

    suspend fun setKeyboardPreciseSeekSeconds(seconds: Int) {
        context.settingsDataStore.edit { it[Keys.KeyboardPreciseSeekSeconds] = seconds.coerceIn(1, 30) }
    }

    suspend fun setKeyboardLargeSeekSeconds(seconds: Int) {
        context.settingsDataStore.edit { it[Keys.KeyboardLargeSeekSeconds] = seconds.coerceIn(5, 600) }
    }

    suspend fun setKeyboardHoldToAccelerate(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.KeyboardHoldToAccelerate] = enabled }
    }

    suspend fun setPreserveWatchingPosition(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.PreserveWatchingPosition] = value }
    }

    suspend fun setDefaultPlayerOrientation(value: String) {
        context.settingsDataStore.edit { it[Keys.DefaultPlayerOrientation] = value }
    }

    suspend fun setPipEpisodeToasts(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.PipEpisodeToasts] = value }
    }

    suspend fun setPipOnExit(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.PipOnExit] = value }
    }

    suspend fun setMpvConf(value: String) {
        context.settingsDataStore.edit { it[Keys.MpvConf] = value }
    }

    suspend fun setMpvInput(value: String) {
        context.settingsDataStore.edit { it[Keys.MpvInput] = value }
    }

    suspend fun setMpvUserFilesEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[Keys.MpvUserFilesEnabled] = value }
    }

    suspend fun setMediaPreviousGesture(value: String) {
        context.settingsDataStore.edit { it[Keys.MediaPreviousGesture] = value }
    }

    suspend fun setMediaPlayPauseGesture(value: String) {
        context.settingsDataStore.edit { it[Keys.MediaPlayPauseGesture] = value }
    }

    suspend fun setMediaNextGesture(value: String) {
        context.settingsDataStore.edit { it[Keys.MediaNextGesture] = value }
    }

    suspend fun setDefaultIntroLength(value: Int) {
        context.settingsDataStore.edit { it[Keys.DefaultIntroLength] = value.coerceIn(5, 300) }
    }
}
