package com.kitsugi.animelist.ui.screens.stream

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import com.google.gson.Gson
import com.kitsugi.animelist.core.player.ExternalPlayerResultContract
import com.kitsugi.animelist.ui.screens.fullscreen.components.MetaCastMember
import com.kitsugi.animelist.ui.theme.KitsugiAnimeListTheme
import android.util.Log
import com.kitsugi.animelist.DeviceProfile
import com.kitsugi.animelist.DeviceFormFactor
import com.kitsugi.animelist.ui.screens.offline.DownloadsActivity
import com.kitsugi.animelist.data.remote.inferStreamMediaType
import com.kitsugi.animelist.model.MediaType

class KitsugiStreamActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_MAL_ID         = "extra_mal_id"
        private const val EXTRA_ANILIST_ID     = "extra_anilist_id"
        private const val EXTRA_TMDB_ID        = "extra_tmdb_id"
        private const val EXTRA_EPISODE        = "extra_episode"
        private const val EXTRA_SEASON         = "extra_season"
        private const val EXTRA_IS_MOVIE       = "extra_is_movie"
        private const val EXTRA_MEDIA_TYPE     = "extra_media_type"
        private const val EXTRA_BANGUMI_ID     = "extra_bangumi_id"
        private const val EXTRA_TITLE          = "extra_title"
        private const val EXTRA_POSTER_URL     = "extra_poster_url"
        private const val EXTRA_TITLE_ENGLISH  = "extra_title_english"
        private const val EXTRA_TITLE_ROMAJI   = "extra_title_romaji"
        private const val EXTRA_TITLE_NATIVE   = "extra_title_native"
        private const val EXTRA_SYNONYMS       = "extra_synonyms"
        private const val EXTRA_START_YEAR     = "extra_start_year"
        private const val EXTRA_DESCRIPTION    = "extra_description"
        private const val EXTRA_CAST_JSON      = "extra_cast_json"

        private const val EXTRA_IS_AUTOPLAY    = "extra_is_autoplay"
        private const val EXTRA_IS_DOWNLOAD_MODE = "extra_is_download_mode"
        private const val EXTRA_CS3_URL        = "extra_cs3_url"
        private const val EXTRA_CS3_API_NAME   = "extra_cs3_api_name"
        private const val EXTRA_IMDB_ID        = "extra_imdb_id"
        private const val EXTRA_RESUME_HISTORY = "extra_resume_history"
        private const val EXTRA_CS3_PLUGIN_ID  = "extra_cs3_plugin_id"
        private const val STATE_STREAM_KEY     = "state_stream_key"
        private const val STATE_HISTORY_ID     = "state_history_id"

        const val PREFS_NAME  = "KitsugiStreamPrefs"
        const val KEY_POS_PFX = "pos_"

        @Volatile
        var tempCast: List<MetaCastMember>? = null

        fun start(
            context: Context,
            malId: Int?,
            aniListId: Int?,
            tmdbId: Int? = null,
            episode: Int,
            season: Int = 1,
            isMovie: Boolean = false,
            mediaType: MediaType? = null,
            bangumiId: Int? = null,
            title: String,
            posterUrl: String? = null,
            titleEnglish: String? = null,
            titleRomaji: String? = null,
            titleNative: String? = null,
            synonyms: List<String> = emptyList(),
            startYear: Int? = null,
            description: String? = null,
            cast: List<MetaCastMember> = emptyList(),
            isAutoplay: Boolean = false,
            isDownloadMode: Boolean = false,
            cs3Url: String? = null,
            cs3ApiName: String? = null,
            /** Detay sayfasının bildiği IMDb kimliği (tt...). Yoksa stream tarafı kendi çözer. */
            imdbId: String? = null,
            resumeEntry: com.kitsugi.animelist.data.model.WatchHistoryEntry? = null,
            cs3PluginId: String? = null
        ) {
            val effectiveMediaType = inferStreamMediaType(
                mediaType = mediaType,
                isMovie = isMovie,
                malId = malId,
                aniListId = aniListId,
                tmdbId = tmdbId
            )
            val effectiveIsMovie = effectiveMediaType?.let { it == MediaType.Movie } ?: isMovie
            tempCast = cast
            context.startActivity(
                Intent(context, KitsugiStreamActivity::class.java).apply {
                    malId?.let          { putExtra(EXTRA_MAL_ID, it) }
                    aniListId?.let      { putExtra(EXTRA_ANILIST_ID, it) }
                    tmdbId?.let         { putExtra(EXTRA_TMDB_ID, it) }
                    putExtra(EXTRA_EPISODE, episode)
                    putExtra(EXTRA_SEASON, season)
                    putExtra(EXTRA_IS_MOVIE, effectiveIsMovie)
                    effectiveMediaType?.let { putExtra(EXTRA_MEDIA_TYPE, it.name) }
                    bangumiId?.let { putExtra(EXTRA_BANGUMI_ID, it) }
                    putExtra(EXTRA_TITLE, title)
                    posterUrl?.let      { putExtra(EXTRA_POSTER_URL, it) }
                    titleEnglish?.let   { putExtra(EXTRA_TITLE_ENGLISH, it) }
                    titleRomaji?.let    { putExtra(EXTRA_TITLE_ROMAJI, it) }
                    titleNative?.let    { putExtra(EXTRA_TITLE_NATIVE, it) }
                    if (synonyms.isNotEmpty()) putStringArrayListExtra(EXTRA_SYNONYMS, ArrayList(synonyms))
                    startYear?.let      { putExtra(EXTRA_START_YEAR, it) }
                    description?.let    { putExtra(EXTRA_DESCRIPTION, it) }
                    putExtra(EXTRA_IS_AUTOPLAY, isAutoplay)
                    putExtra(EXTRA_IS_DOWNLOAD_MODE, isDownloadMode)
                    cs3Url?.let         { putExtra(EXTRA_CS3_URL, it) }
                    cs3ApiName?.let     { putExtra(EXTRA_CS3_API_NAME, it) }
                    cs3PluginId?.let    { putExtra(EXTRA_CS3_PLUGIN_ID, it) }
                    imdbId?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_IMDB_ID, it) }
                    resumeEntry?.let { entry ->
                        // Only the identity/fingerprint travels in the Intent. Expiring URLs,
                        // credentials in headers and large metadata are not needed to re-fetch.
                        val snapshot = entry.copy(
                            streamUrlKey = entry.streamUrlKey
                                ?: com.kitsugi.animelist.data.local.WatchHistorySource.urlKey(entry.streamUrl),
                            streamUrl = null, streamHeaders = null, posterUrl = null,
                            synonyms = null, titleEnglish = null, titleRomaji = null, titleNative = null
                        )
                        putExtra(EXTRA_RESUME_HISTORY, Gson().toJson(snapshot))
                    }
                }
            )
        }
    }

    private val externalPlayerLauncher = registerForActivityResult(
        ExternalPlayerResultContract()
    ) { result ->
        // Servisi durdur
        com.kitsugi.animelist.core.player.KeepAliveService.stop(this)

        if (result == null) { Log.d("KitsugiStream", "External player returned no result"); return@registerForActivityResult }
        Log.d("KitsugiStream", "External player result → pos=${result.positionMs}ms dur=${result.durationMs}ms")
        if (result.positionMs >= 0L) {
            currentStreamKey?.let { key ->
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putLong(KEY_POS_PFX + key, result.positionMs)
                    .also { editor -> result.durationMs?.let { editor.putLong("${KEY_POS_PFX}dur_$key", it) } }
                    .apply()
            }

            currentHistoryId?.let { historyId ->
                com.kitsugi.animelist.data.local.WatchHistoryManager.updateProgress(
                    animeId = historyId,
                    season = currentSeason,
                    episode = currentEpisode,
                    positionMs = result.positionMs,
                    durationMs = result.durationMs ?: 0L
                )
                com.kitsugi.animelist.data.local.WatchHistoryManager.flush()
            }
        }

        // T1.8: Otomatik sonraki bölümü başlatma
        val autoPlay = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean("autoplay", true)
        if (!currentIsMovie && com.kitsugi.animelist.core.player.ExternalAutoNextPolicy.shouldAutoNext(
                positionMs = result.positionMs,
                durationMs = result.durationMs,
                endedByUser = result.endedByUser,
                autoPlayEnabled = autoPlay
            )
        ) {
            val nextEp = currentEpisode + 1
            val previousEntry = currentHistoryId?.let {
                com.kitsugi.animelist.data.local.WatchHistoryManager.find(it, currentSeason, currentEpisode)
            }
            Log.d("KitsugiStream", "AutoPlay Next Episode: S${currentSeason}E${nextEp}")
            // KitsugiStreamActivity'yi bir sonraki bölüm için başlat ve eskisini kapat
            start(
                context = this,
                malId = currentMalId,
                aniListId = currentAniList,
                tmdbId = currentTmdbId,
                episode = nextEp,
                season = currentSeason,
                isMovie = currentIsMovie,
                mediaType = currentMediaType,
                bangumiId = currentBangumiId,
                title = currentTitle,
                posterUrl = currentPosterUrl,
                titleEnglish = currentTitleEnglish,
                titleRomaji = currentTitleRomaji,
                titleNative = currentTitleNative,
                synonyms = currentSynonyms,
                startYear = currentStartYear,
                description = currentDescription,
                imdbId = currentImdbId,
                isAutoplay = true,
                cs3Url = if (previousEntry != null) previousEntry.cs3Url else currentCs3Url,
                cs3ApiName = if (previousEntry != null) previousEntry.cs3ApiName else currentCs3ApiName,
                cs3PluginId = com.kitsugi.animelist.data.local.WatchHistorySource.cloudStreamPluginId(previousEntry?.addonId)
            )
            finish()
        }
    }

    var currentStreamKey: String? = null
    private var currentHistoryId: String? = null

    // Fields for auto-next transition
    private var currentMalId: Int? = null
    private var currentAniList: Int? = null
    private var currentTmdbId: Int? = null
    private var currentEpisode: Int = 1
    private var currentSeason: Int = 1
    private var currentIsMovie: Boolean = false
    private var currentMediaType: MediaType? = null
    private var currentBangumiId: Int? = null
    private var currentImdbId: String? = null
    private var currentTitle: String = ""
    private var currentPosterUrl: String? = null
    private var currentTitleEnglish: String? = null
    private var currentTitleRomaji: String? = null
    private var currentTitleNative: String? = null
    private var currentSynonyms: List<String> = emptyList()
    private var currentStartYear: Int? = null
    private var currentDescription: String? = null
    private var isAutoplayMode: Boolean = false
    private var isDownloadMode: Boolean = false
    private var currentCs3Url: String? = null
    private var currentCs3ApiName: String? = null
    private var currentCs3PluginId: String? = null
    private var resumeEntry: com.kitsugi.animelist.data.model.WatchHistoryEntry? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        currentMalId        = if (intent.hasExtra(EXTRA_MAL_ID)) intent.getIntExtra(EXTRA_MAL_ID, 0).takeIf { it > 0 } else null
        currentAniList    = if (intent.hasExtra(EXTRA_ANILIST_ID)) intent.getIntExtra(EXTRA_ANILIST_ID, 0).takeIf { it > 0 } else null
        currentTmdbId       = if (intent.hasExtra(EXTRA_TMDB_ID)) intent.getIntExtra(EXTRA_TMDB_ID, 0).takeIf { it > 0 } else null
        currentBangumiId    = if (intent.hasExtra(EXTRA_BANGUMI_ID)) intent.getIntExtra(EXTRA_BANGUMI_ID, 0).takeIf { it > 0 } else null
        currentEpisode      = intent.getIntExtra(EXTRA_EPISODE, 1)
        currentSeason       = intent.getIntExtra(EXTRA_SEASON, 1)
        currentIsMovie      = intent.getBooleanExtra(EXTRA_IS_MOVIE, false)
        currentMediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE)
            ?.let { runCatching { MediaType.valueOf(it) }.getOrNull() }
            ?: inferStreamMediaType(
                isMovie = currentIsMovie,
                malId = currentMalId,
                aniListId = currentAniList,
                tmdbId = currentTmdbId
            )
        currentIsMovie = currentMediaType?.let { it == MediaType.Movie } ?: currentIsMovie
        currentTitle        = intent.getStringExtra(EXTRA_TITLE) ?: ""
        currentPosterUrl    = intent.getStringExtra(EXTRA_POSTER_URL)
        currentTitleEnglish = intent.getStringExtra(EXTRA_TITLE_ENGLISH)
        currentTitleRomaji  = intent.getStringExtra(EXTRA_TITLE_ROMAJI)
        currentTitleNative  = intent.getStringExtra(EXTRA_TITLE_NATIVE)
        currentSynonyms     = intent.getStringArrayListExtra(EXTRA_SYNONYMS)?.filter { it.isNotBlank() }.orEmpty()
        currentStartYear    = if (intent.hasExtra(EXTRA_START_YEAR)) intent.getIntExtra(EXTRA_START_YEAR, 0).takeIf { it > 0 } else null
        currentDescription  = intent.getStringExtra(EXTRA_DESCRIPTION)
        isAutoplayMode      = intent.getBooleanExtra(EXTRA_IS_AUTOPLAY, false)
        isDownloadMode      = intent.getBooleanExtra(EXTRA_IS_DOWNLOAD_MODE, false)
        currentCs3Url       = intent.getStringExtra(EXTRA_CS3_URL)
        currentCs3ApiName   = intent.getStringExtra(EXTRA_CS3_API_NAME)
        currentCs3PluginId  = intent.getStringExtra(EXTRA_CS3_PLUGIN_ID)
        currentImdbId       = intent.getStringExtra(EXTRA_IMDB_ID)?.takeIf { it.isNotBlank() }
        currentStreamKey    = savedInstanceState?.getString(STATE_STREAM_KEY)
        currentHistoryId    = savedInstanceState?.getString(STATE_HISTORY_ID)
        resumeEntry = intent.getStringExtra(EXTRA_RESUME_HISTORY)?.let { json ->
            runCatching { Gson().fromJson(json, com.kitsugi.animelist.data.model.WatchHistoryEntry::class.java) }.getOrNull()
        }

        val castList: List<MetaCastMember> = tempCast ?: run {
            val castJson = intent.getStringExtra(EXTRA_CAST_JSON)
            if (!castJson.isNullOrEmpty()) {
                try { val type = object : com.google.gson.reflect.TypeToken<List<MetaCastMember>>() {}.type; Gson().fromJson(castJson, type) }
                catch (_: Exception) { emptyList() }
            } else emptyList()
        }
        tempCast = null

        val isTv = DeviceProfile.detect(this) == DeviceFormFactor.TV
        setContent {
            val settingsStore = remember { com.kitsugi.animelist.data.settings.SettingsDataStore(applicationContext) }
            val appSettings by settingsStore.settingsFlow.collectAsState(
                initial = com.kitsugi.animelist.data.settings.AppSettings()
            )
            val darkTheme = when (appSettings.themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            KitsugiAnimeListTheme(
                darkTheme = darkTheme,
                amoledBlack = appSettings.amoledBlack,
                selectedThemeId = appSettings.selectedThemeId,
                customAccentColor = appSettings.customAccentColor,
                customAccentColor2 = appSettings.customAccentColor2,
                customAccentGradientAngle = appSettings.customAccentGradientAngle,
                isTv = isTv
            ) {
                KitsugiStreamScreen(
                    malId = currentMalId, aniListId = currentAniList, tmdbId = currentTmdbId,
                    episode = currentEpisode, season = currentSeason, isMovie = currentIsMovie,
                    mediaType = currentMediaType, bangumiId = currentBangumiId,
                    title = currentTitle, posterUrl = currentPosterUrl, titleEnglish = currentTitleEnglish,
                    titleRomaji = currentTitleRomaji, titleNative = currentTitleNative, synonyms = currentSynonyms, startYear = currentStartYear,
                    description = currentDescription, castList = castList,
                    isAutoplay = isAutoplayMode,
                    isDownloadMode = isDownloadMode,
                    cs3Url = currentCs3Url,
                    cs3ApiName = currentCs3ApiName,
                    cs3PluginId = currentCs3PluginId,
                    knownImdbId = currentImdbId,
                    resumeEntry = resumeEntry,
                    onBack = { finish() },
                    onLaunchExternalPlayer = { input, streamKey, historyId ->
                        currentStreamKey = streamKey
                        currentHistoryId = historyId
                        com.kitsugi.animelist.core.player.KeepAliveService.start(this)
                        externalPlayerLauncher.launch(input)
                    },
                    onOpenHistory = {
                        startActivity(
                            android.content.Intent(this, WatchHistoryActivity::class.java)
                        )
                    },
                    onOpenDownloads = {
                        startActivity(
                            android.content.Intent(this, DownloadsActivity::class.java)
                        )
                    }
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_STREAM_KEY, currentStreamKey)
        outState.putString(STATE_HISTORY_ID, currentHistoryId)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        com.kitsugi.animelist.data.local.WatchHistoryManager.flush()
        super.onDestroy()
        tempCast = null
    }
}
