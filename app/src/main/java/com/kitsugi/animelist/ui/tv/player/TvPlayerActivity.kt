package com.kitsugi.animelist.ui.tv.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kitsugi.animelist.core.player.SubtitleInput
import com.kitsugi.animelist.data.repository.StreamSource
import com.kitsugi.animelist.ui.screens.fullscreen.components.MetaCastMember
import com.kitsugi.animelist.ui.theme.KitsugiAnimeListTheme

class TvPlayerActivity : ComponentActivity() {

    private var playerKeyEventHandler: ((android.view.KeyEvent) -> Boolean)? = null

    fun setPlayerKeyEventHandler(handler: ((android.view.KeyEvent) -> Boolean)?) {
        playerKeyEventHandler = handler
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (window?.currentFocus !is android.widget.EditText) {
            val handler = playerKeyEventHandler
            if (handler != null && handler(event)) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        const val EXTRA_VIDEO_URL = "extra_video_url"
        const val EXTRA_AUDIO_URL = "extra_audio_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_HEADERS = "extra_headers"
        const val EXTRA_CURRENT_INDEX = "extra_current_index"
        const val EXTRA_MAL_ID = "extra_mal_id"
        const val EXTRA_ANILIST_ID = "extra_anilist_id"
        const val EXTRA_TMDB_ID = "extra_tmdb_id"
        const val EXTRA_BANGUMI_ID = "extra_bangumi_id"
        const val EXTRA_MEDIA_TYPE = "extra_media_type"
        const val EXTRA_IMDB_ID = "extra_imdb_id"
        const val EXTRA_HISTORY_ID = "extra_history_id"
        const val EXTRA_RESUME_POSITION = "extra_resume_position"
        const val EXTRA_CS3_URL = "extra_cs3_url"
        const val EXTRA_CS3_API_NAME = "extra_cs3_api_name"
        const val EXTRA_SEASON = "extra_season"
        const val EXTRA_EPISODE = "extra_episode"
        const val EXTRA_ANIME_TITLE = "extra_anime_title"
        const val EXTRA_POSTER_URL = "extra_poster_url"
        const val EXTRA_TITLE_ENGLISH = "extra_title_english"
        const val EXTRA_TITLE_ROMAJI = "extra_title_romaji"
        const val EXTRA_TITLE_NATIVE = "extra_title_native"
        const val EXTRA_SYNONYMS = "extra_synonyms"
        const val EXTRA_IS_MOVIE = "extra_is_movie"
        const val EXTRA_START_YEAR = "extra_start_year"
        const val EXTRA_DESCRIPTION = "extra_description"
        const val EXTRA_STREAM_SOURCES = "extra_stream_sources"
        const val EXTRA_CAST = "extra_cast"
        const val EXTRA_SUBTITLES = "extra_subtitles"

        @Volatile
        private var tempSubtitles: List<SubtitleInput>? = null

        @Volatile
        private var tempStreamSources: List<StreamSource>? = null

        fun start(
            context: Context,
            videoUrl: String,
            audioUrl: String? = null,
            title: String = "",
            headers: Map<String, String>? = null,
            subtitles: List<SubtitleInput> = emptyList(),
            allSources: List<StreamSource> = emptyList(),
            currentSourceIndex: Int = -1,
            malId: Int? = null,
            aniListId: Int? = null,
            tmdbId: Int? = null,
            season: Int = 1,
            episode: Int = 1,
            animeTitle: String = "",
            posterUrl: String? = null,
            titleEnglish: String? = null,
            titleRomaji: String? = null,
            titleNative: String? = null,
            synonyms: List<String> = emptyList(),
            isMovie: Boolean = false,
            startYear: Int? = null,
            description: String? = null,
            cast: List<MetaCastMember> = emptyList(),
            bangumiId: Int? = null,
            mediaType: com.kitsugi.animelist.model.MediaType? = null,
            knownImdbId: String? = null,
            historyAnimeId: String? = null,
            resumePositionMs: Long = 0L,
            cs3Url: String? = null,
            cs3ApiName: String? = null
        ) {
            tempSubtitles = subtitles
            tempStreamSources = allSources

            fun buildIntent(includeLargeCollections: Boolean): Intent =
                Intent(context, TvPlayerActivity::class.java).apply {
                    putExtra(EXTRA_VIDEO_URL, videoUrl)
                    putExtra(EXTRA_AUDIO_URL, audioUrl)
                    putExtra(EXTRA_TITLE, title)
                    if (!headers.isNullOrEmpty()) {
                        val bundle = android.os.Bundle()
                        headers.forEach { (k, v) -> bundle.putString(k, v) }
                        putExtra(EXTRA_HEADERS, bundle)
                    }
                    putExtra(EXTRA_CURRENT_INDEX, currentSourceIndex)
                    malId?.let { putExtra(EXTRA_MAL_ID, it) }
                    aniListId?.let { putExtra(EXTRA_ANILIST_ID, it) }
                    tmdbId?.let { putExtra(EXTRA_TMDB_ID, it) }
                    bangumiId?.let { putExtra(EXTRA_BANGUMI_ID, it) }
                    mediaType?.let { putExtra(EXTRA_MEDIA_TYPE, it.name) }
                    knownImdbId?.let { putExtra(EXTRA_IMDB_ID, it) }
                    historyAnimeId?.let { putExtra(EXTRA_HISTORY_ID, it) }
                    putExtra(EXTRA_RESUME_POSITION, resumePositionMs)
                    cs3Url?.let { putExtra(EXTRA_CS3_URL, it) }
                    cs3ApiName?.let { putExtra(EXTRA_CS3_API_NAME, it) }
                    putExtra(EXTRA_SEASON, season)
                    putExtra(EXTRA_EPISODE, episode)
                    putExtra(EXTRA_ANIME_TITLE, animeTitle)
                    putExtra(EXTRA_POSTER_URL, posterUrl)
                    putExtra(EXTRA_TITLE_ENGLISH, titleEnglish)
                    putExtra(EXTRA_TITLE_ROMAJI, titleRomaji)
                    putExtra(EXTRA_TITLE_NATIVE, titleNative)
                    if (synonyms.isNotEmpty()) putStringArrayListExtra(EXTRA_SYNONYMS, ArrayList(synonyms))
                    putExtra(EXTRA_IS_MOVIE, isMovie)
                    startYear?.let { putExtra(EXTRA_START_YEAR, it) }
                    if (includeLargeCollections) {
                        putExtra(EXTRA_DESCRIPTION, description)
                        if (subtitles.size <= 30) putExtra(EXTRA_SUBTITLES, ArrayList(subtitles))
                        if (allSources.size <= 30) putExtra(EXTRA_STREAM_SOURCES, ArrayList(allSources))
                        if (cast.size <= 30) putExtra(EXTRA_CAST, ArrayList(cast))
                    }
                    if (context !is android.app.Activity) {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }

            try {
                context.startActivity(buildIntent(includeLargeCollections = true))
            } catch (e: RuntimeException) {
                if (e.cause is android.os.TransactionTooLargeException || e is android.os.TransactionTooLargeException) {
                    context.startActivity(buildIntent(includeLargeCollections = false))
                } else {
                    throw e
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // T1.8: KeepAliveService start
        com.kitsugi.animelist.core.player.KeepAliveService.start(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: ""
        val audioUrl = intent.getStringExtra(EXTRA_AUDIO_URL)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val headersBundle = intent.getBundleExtra(EXTRA_HEADERS)
        val requestHeaders: Map<String, String> = if (headersBundle != null) {
            buildMap { headersBundle.keySet().forEach { k -> headersBundle.getString(k)?.let { v -> put(k, v) } } }
        } else emptyMap()

        @Suppress("UNCHECKED_CAST")
        val initialSubtitles = tempSubtitles
            ?: (intent.getSerializableExtra(EXTRA_SUBTITLES) as? ArrayList<SubtitleInput>)
            ?: emptyList()
        tempSubtitles = null

        @Suppress("UNCHECKED_CAST")
        val streamSources = tempStreamSources
            ?: (intent.getSerializableExtra(EXTRA_STREAM_SOURCES) as? ArrayList<StreamSource>)
            ?: emptyList()
        tempStreamSources = null

        val currentIndex = intent.getIntExtra(EXTRA_CURRENT_INDEX, -1)
        val malId = intent.getIntExtra(EXTRA_MAL_ID, -1).takeIf { it != -1 }
        val aniListId = intent.getIntExtra(EXTRA_ANILIST_ID, -1).takeIf { it != -1 }
        val tmdbId = intent.getIntExtra(EXTRA_TMDB_ID, -1).takeIf { it != -1 }
        val bangumiId = intent.getIntExtra(EXTRA_BANGUMI_ID, -1).takeIf { it > 0 }
        val mediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE)?.let {
            runCatching { com.kitsugi.animelist.model.MediaType.valueOf(it) }.getOrNull()
        }
        val knownImdbId = intent.getStringExtra(EXTRA_IMDB_ID)
        val historyAnimeId = intent.getStringExtra(EXTRA_HISTORY_ID)
        val resumePosition = intent.getLongExtra(EXTRA_RESUME_POSITION, 0L)
        val cs3Url = intent.getStringExtra(EXTRA_CS3_URL)
        val cs3ApiName = intent.getStringExtra(EXTRA_CS3_API_NAME)
        val season = intent.getIntExtra(EXTRA_SEASON, 1)
        val episode = intent.getIntExtra(EXTRA_EPISODE, 1)
        val animeTitle = intent.getStringExtra(EXTRA_ANIME_TITLE) ?: ""
        val posterUrl = intent.getStringExtra(EXTRA_POSTER_URL)
        val titleEnglish = intent.getStringExtra(EXTRA_TITLE_ENGLISH)
        val titleRomaji = intent.getStringExtra(EXTRA_TITLE_ROMAJI)
        val titleNative = intent.getStringExtra(EXTRA_TITLE_NATIVE)
        val synonyms = intent.getStringArrayListExtra(EXTRA_SYNONYMS)?.filter { it.isNotBlank() }.orEmpty()
        val isMovie = intent.getBooleanExtra(EXTRA_IS_MOVIE, false)
        val startYear = intent.getIntExtra(EXTRA_START_YEAR, -1).takeIf { it != -1 }
        val description = intent.getStringExtra(EXTRA_DESCRIPTION)

        @Suppress("UNCHECKED_CAST")
        val castList = (intent.getSerializableExtra(EXTRA_CAST) as? ArrayList<MetaCastMember>) ?: emptyList()

        setContent {
            KitsugiAnimeListTheme(
                isTv = true
            ) {
                TvPlayerScreen(
                    videoUrl = videoUrl,
                    audioUrl = audioUrl,
                    title = title,
                    requestHeaders = requestHeaders,
                    initialSubtitles = initialSubtitles,
                    streamSources = streamSources,
                    initialIndex = currentIndex,
                    malId = malId,
                    aniListId = aniListId,
                    tmdbId = tmdbId,
                    bangumiId = bangumiId,
                    mediaType = mediaType,
                    knownImdbId = knownImdbId,
                    historyAnimeId = historyAnimeId,
                    resumePosition = resumePosition,
                    cs3Url = cs3Url,
                    cs3ApiName = cs3ApiName,
                    season = season,
                    episode = episode,
                    animeTitle = animeTitle,
                    posterUrl = posterUrl,
                    titleEnglish = titleEnglish,
                    titleRomaji = titleRomaji,
                    titleNative = titleNative,
                    synonyms = synonyms,
                    isMovie = isMovie,
                    startYear = startYear,
                    description = description,
                    castList = castList,
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        com.kitsugi.animelist.data.local.WatchHistoryManager.flush()
        super.onDestroy()
        // T1.8: KeepAliveService stop
        com.kitsugi.animelist.core.player.KeepAliveService.stop(this)
    }
}
