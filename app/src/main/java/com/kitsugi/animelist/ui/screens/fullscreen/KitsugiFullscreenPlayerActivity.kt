package com.kitsugi.animelist.ui.screens.fullscreen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import com.kitsugi.animelist.core.player.ExternalPlayerLauncher
import com.kitsugi.animelist.core.player.PlayerMediaSessionHelper
import com.kitsugi.animelist.core.player.PlayerPipHelper
import com.kitsugi.animelist.core.player.SubtitleInput
import com.kitsugi.animelist.data.repository.StreamSource
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.ui.screens.fullscreen.components.MetaCastMember
import com.kitsugi.animelist.ui.theme.KitsugiAnimeListTheme
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

class KitsugiFullscreenPlayerActivity : ComponentActivity() {

    private val viewModel: KitsugiPlayerViewModel by viewModels()
    private var isPipEnabled = true
    private var isPipOnExitEnabled = true
    private var showSystemStatusBar = false
    private var playerFullscreenCutout = true
    private var preferredExternalPlayerPkg: String = ""

    private val externalPlayerResultLauncher = registerForActivityResult(
        com.kitsugi.animelist.core.player.ExternalPlayerResultContract()
    ) { result ->
        handedOffToExternalPlayer = false
        if (result != null) {
            viewModel.onExternalPlayerResult(
                positionMs = result.positionMs,
                durationMs = result.durationMs,
                endedByUser = result.endedByUser
            )
        }
    }

    // ── T2.3: MediaSession + PiP BroadcastReceiver ────────────────────────────
    private var mediaSessionHelper: PlayerMediaSessionHelper? = null

    /**
     * Oynatıcı ekranının (Compose) köprüsü. PiP penceresindeki / bildirimdeki tuşlar ve
     * Activity yaşam döngüsü olayları buraya iletilir. Ekran, DisposableEffect ile kaydolur
     * ve çıkarken [setPipPlayerCallback] ile `null` yapar.
     */
    private var pipPlayerCallback: PipPlayerCallback? = null

    /** PiP penceresinde miyiz? (onPictureInPictureModeChanged ile güncellenir) */
    private var isInPipNow = false

    /** Whether playback was active before the Activity was temporarily backgrounded. */
    private var resumePlaybackAfterStop = false

    /**
     * Harici (3. parti) oynatıcıya devredildi mi? true ise Activity'nin görünmez olması
     * "mini pencere kapatıldı" anlamına gelmez; bu yüzden onStop'te kapanış tetiklenmez.
     */
    private var handedOffToExternalPlayer = false

    /**
     * v2.4.319: onCreate yarıda kalırsa (beklenmeyen bir çalışma zamanı hatası) süreç
     * öldürülmesin diye kurulum try/catch ile sarılır. Bu bayraklar, kısmi kurulum
     * durumunda onDestroy'un kaydedilmemiş bir receiver'ı kaldırmaya çalışmasını engeller.
     */
    private var pipReceiverRegistered = false

    /** Son bilinen oynatma durumu — PiP'e geçerken RemoteAction ikonlarını doğru kurmak için. */
    private var lastKnownIsPlaying = false
    private var lastKnownHasNext = false
    private var lastKnownHasPrevious = false
    private var pipEngine: com.kitsugi.animelist.core.player.engine.PlayerEngine? = null

    interface PipPlayerCallback {
        fun onPipPlay()
        fun onPipPause()
        fun onPipSkipNext()
        fun onPipSkipPrevious() {}
        fun onPipSkipForward() {}
    }

    fun setPipPlayerCallback(callback: PipPlayerCallback?) {
        pipPlayerCallback = callback
        if (callback == null) pipEngine = null
    }

    private val pipBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                PlayerPipHelper.ACTION_PLAY -> invokePipAction { it.onPipPlay() }
                PlayerPipHelper.ACTION_PAUSE -> invokePipAction { it.onPipPause() }
                PlayerPipHelper.ACTION_SKIP_NEXT -> invokePipAction { it.onPipSkipNext() }
                PlayerPipHelper.ACTION_SKIP_PREVIOUS -> invokePipAction { it.onPipSkipPrevious() }
                PlayerPipHelper.ACTION_SKIP_FORWARD -> invokePipAction { it.onPipSkipForward() }
            }
        }
    }

    /**
     * PiP/bildirim tuşlarını oynatıcıya iletir.
     *
     * Compose köprüsü (`pipPlayerCallback`) yalnızca oynatıcı yüzeyi ağaçtayken kayıtlıdır;
     * kaynak çözümleme/hata durumlarında `null` olabiliyordu ve bu yüzden PiP penceresindeki
     * Oynat/Duraklat/Sonraki tuşları hiçbir şey yapmıyordu. Köprü yoksa doğrudan
     * ViewModel'e düşüyoruz — ViewModel Activity ömrüne bağlı olduğu için her zaman canlıdır.
     */
    private fun invokePipAction(action: (PipPlayerCallback) -> Unit) {
        val callback = pipPlayerCallback
        if (callback != null) {
            runCatching { action(callback) }
            return
        }
        runCatching {
            val fallback = object : PipPlayerCallback {
                override fun onPipPlay() = viewModel.play()
                override fun onPipPause() = viewModel.pause()
                // "Sonraki bölüm" akışı ekran tarafındaki çözümleyiciye bağlıdır; köprü
                // yokken güvenli bir karşılığı olmadığı için bilinçli olarak no-op.
                override fun onPipSkipNext() = viewModel.changeEpisode(false)
                override fun onPipSkipPrevious() = viewModel.changeEpisode(true)
                override fun onPipSkipForward() = viewModel.seekBy(10000)
            }
            action(fallback)
        }
    }

    /** Oynatmayı her koşulda durdurur (köprü olmasa bile ses arka planda devam etmesin). */
    private fun stopPlaybackEverywhere() {
        invokePipAction { it.onPipPause() }
    }

    /**
     * Sistem çubuklarını (durum + alt gezinme çubuğu) gizler ve yalnızca kaydırmayla geçici
     * olarak görünmelerine izin verir.
     *
     * Neden tekrar tekrar çağrılıyor: `onCreate` içinde tek sefer gizlemek yetmiyor. MIUI/HyperOS
     * başta olmak üzere bazı kabuklarda PiP'e geçiş/çıkış, bölünmüş ekran ve odak değişimi
     * sonrası alt gezinme çubuğu "geçici" olmaktan çıkıp kalıcı hâle geliyor ve video oynarken
     * ekranın altında beliriyordu. Bu yüzden her odaklanma ve her onResume'da yeniden uygulanır.
     */
    private fun applyImmersiveMode() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val attrs = window.attributes
                attrs.layoutInDisplayCutoutMode = if (playerFullscreenCutout) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
                }
                window.attributes = attrs
            }
            WindowInsetsControllerCompat(window, window.decorView).apply {
                if (showSystemStatusBar) {
                    show(WindowInsetsCompat.Type.statusBars())
                    hide(WindowInsetsCompat.Type.navigationBars())
                } else {
                    hide(WindowInsetsCompat.Type.systemBars())
                }
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    internal fun launchExternalForResult(
        videoUrl: String,
        title: String,
        positionMs: Long,
        headers: Map<String, String>?,
        subtitles: List<SubtitleInput>?,
        preferredPackage: String?
    ): Boolean {
        val pkg = preferredPackage?.takeIf { it.isNotBlank() } ?: preferredExternalPlayerPkg.takeIf { it.isNotBlank() }
        return try {
            handedOffToExternalPlayer = true
            externalPlayerResultLauncher.launch(
                ExternalPlayerLauncher.createInput(
                    url              = videoUrl,
                    title            = title,
                    headers          = headers,
                    resumePositionMs = positionMs,
                    subtitles        = subtitles,
                    preferredPackage = pkg
                )
            )
            true
        } catch (_: Exception) {
            handedOffToExternalPlayer = false
            false
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveMode()
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveMode()
        if (resumePlaybackAfterStop && !handedOffToExternalPlayer && !isInPipNow) {
            resumePlaybackAfterStop = false
            invokePipAction { it.onPipPlay() }
        }
    }

    companion object {
        const val EXTRA_VIDEO_ID  = "extra_video_id"
        const val EXTRA_VIDEO_URL = "extra_video_url"
        const val EXTRA_AUDIO_URL = "extra_audio_url"
        const val EXTRA_TITLE     = "extra_title"
        const val EXTRA_HEADERS   = "extra_headers"

        const val EXTRA_SUBTITLES_JSON = "extra_subtitles_json"
        const val EXTRA_STREAM_LIST_JSON = "extra_stream_list_json"
        const val EXTRA_CURRENT_INDEX = "extra_current_index"
        const val EXTRA_MAL_ID = "extra_mal_id"
        const val EXTRA_ANILIST_ID = "extra_anilist_id"
        const val EXTRA_TMDB_ID = "extra_tmdb_id"
        const val EXTRA_EPISODE = "extra_episode"
        const val EXTRA_SEASON = "extra_season"
        const val EXTRA_ANIME_TITLE = "extra_anime_title"
        const val EXTRA_POSTER_URL = "extra_poster_url"
        const val EXTRA_TITLE_ENGLISH = "extra_title_english"
        const val EXTRA_TITLE_ROMAJI = "extra_title_romaji"
        const val EXTRA_TITLE_NATIVE = "extra_title_native"
        const val EXTRA_SYNONYMS = "extra_synonyms"
        const val EXTRA_START_YEAR = "extra_start_year"
        const val EXTRA_DESCRIPTION = "extra_description"
        const val EXTRA_CAST_JSON   = "extra_cast_json"
        const val EXTRA_IS_MOVIE    = "extra_is_movie"
        const val EXTRA_CS3_URL     = "extra_cs3_url"
        const val EXTRA_CS3_API_NAME = "extra_cs3_api_name"
        const val EXTRA_RESUME_POSITION = "extra_resume_position"
        const val EXTRA_HISTORY_ID = "extra_history_id"
        const val EXTRA_AUTO_RESUME = "extra_auto_resume"
        const val EXTRA_MEDIA_TYPE = "extra_media_type"
        const val EXTRA_IMDB_ID = "extra_imdb_id"

        @Volatile
        var tempStreamSources: List<StreamSource>? = null

        @Volatile
        var tempCast: List<MetaCastMember>? = null

        @Volatile
        var tempSubtitles: List<SubtitleInput>? = null

        fun startWithYouTubeId(context: Context, videoId: String, title: String = "") {
            context.startActivity(
                Intent(context, KitsugiFullscreenPlayerActivity::class.java).apply {
                    putExtra(EXTRA_VIDEO_ID, videoId)
                    putExtra(EXTRA_TITLE, title)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }

        /**
         * Oynatıcıyı başlatır.
         *
         * @return true → Activity başlatıldı. false → başlatılamadı (sebep loglandı ve
         *         kullanıcıya toast ile söylendi). Çağıran ekran bu durumda kendi
         *         hata diyaloğunu gösterebilir; eskiden hata sessizce yutuluyordu.
         */
        fun startWithStreamUrls(
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
            startYear: Int? = null,
            description: String? = null,
            cast: List<MetaCastMember> = emptyList(),
            isMovie: Boolean = false,
            cs3Url: String? = null,
            cs3ApiName: String? = null,
            resumePositionMs: Long = 0L,
            bangumiId: Int? = null,
            imdbId: String? = null,
            historyAnimeId: String? = null,
            autoResume: Boolean = false,
            mediaType: com.kitsugi.animelist.model.MediaType? = null,
            knownImdbId: String? = null
        ): Boolean {
            tempSubtitles = subtitles
            tempStreamSources = allSources
            tempCast = cast

            try {
                context.startActivity(
                    Intent(context, KitsugiFullscreenPlayerActivity::class.java).apply {
                        putExtra(EXTRA_VIDEO_URL, videoUrl)
                        putExtra(EXTRA_AUDIO_URL, audioUrl)
                        putExtra(EXTRA_TITLE, title)
                        cs3Url?.let { putExtra(EXTRA_CS3_URL, it) }
                        cs3ApiName?.let { putExtra(EXTRA_CS3_API_NAME, it) }
                        if (!headers.isNullOrEmpty()) {
                            val bundle = android.os.Bundle()
                            headers.forEach { (k, v) -> bundle.putString(k, v) }
                            putExtra(EXTRA_HEADERS, bundle)
                        }
                        putExtra(EXTRA_CURRENT_INDEX, currentSourceIndex)
                        malId?.let { putExtra(EXTRA_MAL_ID, it) }
                        aniListId?.let { putExtra(EXTRA_ANILIST_ID, it) }
                        tmdbId?.let { putExtra(EXTRA_TMDB_ID, it) }
                        bangumiId?.let { putExtra("subtitle_bangumi_id", it) }
                        imdbId?.let { putExtra("subtitle_imdb_id", it) }
                        putExtra(EXTRA_SEASON, season)
                        putExtra(EXTRA_EPISODE, episode)
                        putExtra(EXTRA_ANIME_TITLE, animeTitle)
                        putExtra(EXTRA_POSTER_URL, posterUrl)
                        putExtra(EXTRA_TITLE_ENGLISH, titleEnglish)
                        putExtra(EXTRA_TITLE_ROMAJI, titleRomaji)
                        putExtra(EXTRA_TITLE_NATIVE, titleNative)
                        if (synonyms.isNotEmpty()) putStringArrayListExtra(EXTRA_SYNONYMS, ArrayList(synonyms))
                        startYear?.let { putExtra(EXTRA_START_YEAR, it) }
                        description?.let { putExtra(EXTRA_DESCRIPTION, it) }
                        putExtra(EXTRA_IS_MOVIE, isMovie)
                        putExtra(EXTRA_RESUME_POSITION, resumePositionMs)
                        historyAnimeId?.let { putExtra(EXTRA_HISTORY_ID, it) }
                        putExtra(EXTRA_AUTO_RESUME, autoResume)
                        mediaType?.let { putExtra(EXTRA_MEDIA_TYPE, it.name) }
                        knownImdbId?.let { putExtra(EXTRA_IMDB_ID, it) }

                        // Süreç ölümü (process death) sonrası Activity yeniden oluşturulursa
                        // static `tempSubtitles` / `tempStreamSources` kaybolur. Bu yedek
                        // JSON alanları olmadan altyazılar ve kaynak listesi sessizce
                        // kayboluyordu.
                        //
                        // BINDER SINIRI (v2.4.319): Önceki eşikler (250 KB + 450 KB karakter)
                        // tek başına ~1,4 MB'lık bir Intent üretebiliyordu; Binder işlem başına
                        // ~1 MB sınırı olduğu için `startActivity` "TransactionTooLargeException"
                        // ile düşüyor ve video hiç açılmıyordu. 125 kaynaklı listelerde bu eşiğe
                        // çok yaklaşılıyordu. Artık toplam yedek veri 96 KB ile sınırlı ve
                        // normal (süreç yaşıyor) yol statik referansla çalışır.
                        runCatching {
                            val gson = com.google.gson.Gson()
                            val subtitlesJson = gson.toJson(subtitles)
                            if (subtitlesJson.length in 1..48_000) {
                                putExtra(EXTRA_SUBTITLES_JSON, subtitlesJson)
                            }
                            val sourcesJson = gson.toJson(allSources)
                            if (sourcesJson.length in 1..96_000) {
                                putExtra(EXTRA_STREAM_LIST_JSON, sourcesJson)
                            }
                        }.onFailure {
                            android.util.Log.w("KitsugiPlayer", "Ekstra oynatma verisi yazılamadı: ${it.message}")
                        }
                    }
                )
                return true
            } catch (e: android.os.TransactionTooLargeException) {
                // Çok büyük kaynak listesi / altyazı: yedek JSON'ları at ve sadece temel
                // veriyle yeniden dene. Oynatma çalışır, "kaynak değiştir" listesi boş kalır.
                android.util.Log.e("KitsugiPlayer", "Intent çok büyük (TransactionTooLargeException); yedek veri olmadan yeniden deneniyor", e)
                return runCatching {
                    context.startActivity(
                        Intent(context, KitsugiFullscreenPlayerActivity::class.java).apply {
                            tempStreamSources = emptyList()
                            tempSubtitles = emptyList()
                            tempCast = emptyList()
                            putExtra(EXTRA_VIDEO_URL, videoUrl)
                            putExtra(EXTRA_AUDIO_URL, audioUrl)
                            putExtra(EXTRA_TITLE, title)
                            putExtra(EXTRA_CURRENT_INDEX, -1)
                            putExtra(EXTRA_SEASON, season)
                            putExtra(EXTRA_EPISODE, episode)
                            putExtra(EXTRA_ANIME_TITLE, animeTitle)
                            putExtra(EXTRA_IS_MOVIE, isMovie)
                            putExtra(EXTRA_RESUME_POSITION, resumePositionMs)
                            posterUrl?.let { putExtra(EXTRA_POSTER_URL, it) }
                        }
                    )
                    true
                }.getOrElse {
                    android.util.Log.e("KitsugiPlayer", "Yedek başlatma da başarısız: ${it.message}", it)
                    android.widget.Toast.makeText(context, KitsugiStrings.get(R.string.oynatici_baslatilamadi_x, it.message ?: it.javaClass.simpleName), android.widget.Toast.LENGTH_LONG).show()
                    false
                }
            } catch (t: Throwable) {
                android.util.Log.e("KitsugiPlayer", "Oynatıcı başlatılırken hata oluştu: ${t.javaClass.simpleName}: ${t.message}", t)
                android.widget.Toast.makeText(context, KitsugiStrings.get(R.string.oynatici_baslatilamadi_x, t.message ?: t.javaClass.simpleName), android.widget.Toast.LENGTH_LONG).show()
                return false
            }
        }

        /**
         * Harici oynatıcıya devreder.
         *
         * @return true → gerçekten bir oynatıcı açıldı (Activity'den çıkmak güvenli).
         *         false → hiçbir şey açılamadı. v2.4.319: Önceden dönüş değeri yoktu ve
         *         çağıran taraf her durumda `onBack()` yapıyordu; harici oynatıcı kurulu
         *         değilse oynatıcı ekranı kapanıyor, kullanıcı "video açılmadı, geri
         *         attı" görüyordu. Artık başarısızlıkta ekran açık kalır ve sebep söylenir.
         */
        fun launchExternalPlayer(
            context: Context,
            videoUrl: String,
            title: String,
            positionMs: Long,
            headers: Map<String, String>? = null,
            subtitles: List<SubtitleInput>? = null,
            preferredPackage: String? = null
        ): Boolean {
            val playerActivity = context as? KitsugiFullscreenPlayerActivity
            val effectivePkg = preferredPackage?.takeIf { it.isNotBlank() }
                ?: playerActivity?.preferredExternalPlayerPkg?.takeIf { it.isNotBlank() }

            if (playerActivity != null && playerActivity.launchExternalForResult(
                    videoUrl         = videoUrl,
                    title            = title,
                    positionMs       = positionMs,
                    headers          = headers,
                    subtitles        = subtitles,
                    preferredPackage = effectivePkg
                )
            ) {
                playerActivity.handedOffToExternalPlayer = true
                return true
            }

            val launched = ExternalPlayerLauncher.launch(
                context          = context,
                url              = videoUrl,
                title            = title,
                headers          = headers,
                resumePositionMs = positionMs,
                subtitles        = subtitles,
                preferredPackage = effectivePkg
            )
            if (!launched) {
                val fallbackWorked = try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                    true
                } catch (_: Exception) {
                    Toast.makeText(context, KitsugiStrings.get(R.string.harici_oynatici_baslatilamadi), Toast.LENGTH_SHORT).show()
                    false
                }
                if (!fallbackWorked) {
                    playerActivity?.handedOffToExternalPlayer = false
                    return false
                }
            }
            // Harici oynatıcıya devredildi: bu Activity artık "mini pencere kapatıldı" diye
            // sonlandırılmamalı (kullanıcı geri döndüğünde yerinde bulsun).
            playerActivity?.handedOffToExternalPlayer = true
            return true
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── v2.4.319: Oynatıcı kurulumu asla süreci düşürmemeli ─────────────────
        // Şikâyet: "İzle'ye basınca video açılmıyor, uygulama ana sayfaya atıyor;
        // çökmüş gibi oluyor." Kurulum sırasında fırlayan (Intent çözümleme, servis
        // başlatma, tema/Compose, viewModel init) herhangi bir istisna yakalanmadan
        // ana thread'e kaçtığında Android süreci öldürüyordu; kullanıcı bunu
        // "ana sayfaya atma" olarak görüyordu. Artık hata kullanıcıya söylenir ve
        // Activity düzgünce kapanır (stream ekranına geri döner).
        val bootResult = runCatching { bootstrapPlayer(savedInstanceState) }
        if (bootResult.isFailure) {
            val failure = bootResult.exceptionOrNull()
            android.util.Log.e("KitsugiPlayer", "Oynatıcı kurulumu başarısız: ${failure?.javaClass?.simpleName}: ${failure?.message}", failure)
            runCatching {
                Toast.makeText(
                    this,
                    KitsugiStrings.get(
                        R.string.stream_play_failed_body,
                        failure?.message ?: (failure?.javaClass?.simpleName ?: "?")
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
            finish()
        }
    }

    /**
     * Oynatıcının gerçek kurulumu. [onCreate] bunu try/catch ile çağırır; burada
     * fırlayan her hata kullanıcıya gösterilir, süreç öldürülmez.
     */
    private fun bootstrapPlayer(savedInstanceState: Bundle?) {
        // Intent verisi yoksa oynatıcı boş açılmasın: sebebi söyle ve kapan.
        val hasVideoId = !intent.getStringExtra(EXTRA_VIDEO_ID).isNullOrBlank()
        val hasVideoUrl = !intent.getStringExtra(EXTRA_VIDEO_URL).isNullOrBlank()
        if (!hasVideoId && !hasVideoUrl) {
            android.util.Log.e("KitsugiPlayer", "Oynatıcı veri olmadan açıldı (videoId/videoUrl yok)")
            Toast.makeText(
                this,
                KitsugiStrings.get(R.string.stream_play_failed_body, "videoUrl/videoId yok"),
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

        // T1.8: KeepAliveService start — FGS kısıtları istisna atabildiği için korunur.
        runCatching { com.kitsugi.animelist.core.player.KeepAliveService.start(this) }
            .onFailure { android.util.Log.w("KitsugiPlayer", "KeepAliveService başlatılamadı: ${it.message}") }

        // T2.3: PiP BroadcastReceiver kaydı
        val filter = IntentFilter().apply {
            addAction(PlayerPipHelper.ACTION_PLAY)
            addAction(PlayerPipHelper.ACTION_PAUSE)
            addAction(PlayerPipHelper.ACTION_SKIP_NEXT)
            addAction(PlayerPipHelper.ACTION_SKIP_PREVIOUS)
            addAction(PlayerPipHelper.ACTION_SKIP_FORWARD)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipBroadcastReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(pipBroadcastReceiver, filter)
        }
        pipReceiverRegistered = true
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyImmersiveMode()

        val videoId  = intent.getStringExtra(EXTRA_VIDEO_ID)
        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
        val audioUrl = intent.getStringExtra(EXTRA_AUDIO_URL)
        val title    = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val headersBundle = intent.getBundleExtra(EXTRA_HEADERS)
        val requestHeaders: Map<String, String> = if (headersBundle != null) {
            buildMap { headersBundle.keySet().forEach { k -> headersBundle.getString(k)?.let { v -> put(k, v) } } }
        } else emptyMap()

        val initialSubtitles: List<SubtitleInput> = tempSubtitles ?: run {
            val subtitlesJson = intent.getStringExtra(EXTRA_SUBTITLES_JSON)
            if (!subtitlesJson.isNullOrEmpty()) {
                try {
                    val type = object : com.google.gson.reflect.TypeToken<List<SubtitleInput>>() {}.type
                    com.google.gson.Gson().fromJson(subtitlesJson, type)
                } catch (e: Exception) {
                    emptyList()
                }
            } else emptyList()
        }
        tempSubtitles = null

        val rawStreamSources: List<StreamSource> = tempStreamSources ?: run {
            val streamsJson = intent.getStringExtra(EXTRA_STREAM_LIST_JSON)
            if (!streamsJson.isNullOrEmpty()) {
                try {
                    val type = object : com.google.gson.reflect.TypeToken<List<StreamSource>>() {}.type
                    com.google.gson.Gson().fromJson(streamsJson, type)
                } catch (e: Exception) {
                    emptyList()
                }
            } else emptyList()
        }
        tempStreamSources = null
        val streamSources = rawStreamSources.map {
            it.copy(
                subtitles = it.subtitles ?: emptyList(),
                addonName = it.addonName ?: "Bilinmeyen Eklenti",
                name = it.name ?: "",
                title = it.title ?: ""
            )
        }

        val currentIndex = intent.getIntExtra(EXTRA_CURRENT_INDEX, -1)
        val malId = intent.getIntExtra(EXTRA_MAL_ID, -1).takeIf { it != -1 }
        val aniListId = intent.getIntExtra(EXTRA_ANILIST_ID, -1).takeIf { it != -1 }
        val tmdbId = intent.getIntExtra(EXTRA_TMDB_ID, -1).takeIf { it != -1 }
        val season = intent.getIntExtra(EXTRA_SEASON, 1)
        val episode = intent.getIntExtra(EXTRA_EPISODE, 1)
        val animeTitle = intent.getStringExtra(EXTRA_ANIME_TITLE) ?: ""
        val posterUrl = intent.getStringExtra(EXTRA_POSTER_URL)
        val titleEnglish = intent.getStringExtra(EXTRA_TITLE_ENGLISH)
        val titleRomaji = intent.getStringExtra(EXTRA_TITLE_ROMAJI)
        val titleNative = intent.getStringExtra(EXTRA_TITLE_NATIVE)
        val synonyms = intent.getStringArrayListExtra(EXTRA_SYNONYMS)?.filter { it.isNotBlank() }.orEmpty()
        val startYear = intent.getIntExtra(EXTRA_START_YEAR, -1).takeIf { it != -1 }
        val description = intent.getStringExtra(EXTRA_DESCRIPTION)
        val isMovie = intent.getBooleanExtra(EXTRA_IS_MOVIE, false)
        val cs3Url = intent.getStringExtra(EXTRA_CS3_URL)
        val cs3ApiName = intent.getStringExtra(EXTRA_CS3_API_NAME)
        val resumePosition = intent.getLongExtra(EXTRA_RESUME_POSITION, 0L)
        val historyAnimeId = intent.getStringExtra(EXTRA_HISTORY_ID)
        val autoResume = intent.getBooleanExtra(EXTRA_AUTO_RESUME, false)
        val mediaType = intent.getStringExtra(EXTRA_MEDIA_TYPE)?.let { runCatching { com.kitsugi.animelist.model.MediaType.valueOf(it) }.getOrNull() }
        val knownImdbId = intent.getStringExtra(EXTRA_IMDB_ID)

        val castList: List<MetaCastMember> = tempCast ?: run {
            val castJson = intent.getStringExtra(EXTRA_CAST_JSON)
            if (!castJson.isNullOrEmpty()) {
                try {
                    val type = object : com.google.gson.reflect.TypeToken<List<MetaCastMember>>() {}.type
                    com.google.gson.Gson().fromJson(castJson, type)
                } catch (_: Exception) { emptyList() }
            } else emptyList()
        }
        tempCast = null

        lifecycleScope.launch {
            SettingsDataStore(applicationContext).settingsFlow.collectLatest { settings ->
                isPipEnabled = settings.pipEnabled
                isPipOnExitEnabled = settings.pipOnExit
                val barChanged = showSystemStatusBar != settings.showSystemStatusBar ||
                    playerFullscreenCutout != settings.playerFullscreenCutout
                showSystemStatusBar = settings.showSystemStatusBar
                playerFullscreenCutout = settings.playerFullscreenCutout
                preferredExternalPlayerPkg = settings.preferredExternalPlayerPackage
                if (barChanged) applyImmersiveMode()
            }
        }

        lifecycleScope.launch {
            viewModel.subtitleEvents.collectLatest { event ->
                if (event is KitsugiPlayerViewModel.SubtitleEvent.LoadFailed) {
                    Toast.makeText(
                        this@KitsugiFullscreenPlayerActivity,
                        "Eklentilerden altyaz\u0131 bulunamad\u0131. L\u00fctfen dahili altyaz\u0131lar\u0131 veya dosya se\u00e7iciyi kontrol edin.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        // T2.3: MediaSession başlat
        mediaSessionHelper = PlayerMediaSessionHelper(
            context = this,
            title = animeTitle.ifBlank { title },
            onPlay = { invokePipAction { it.onPipPlay() } },
            onPause = { invokePipAction { it.onPipPause() } },
            onSkipNext = { invokePipAction { it.onPipSkipNext() } },
            onSkipPrevious = { invokePipAction { it.onPipSkipPrevious() } },
            onSeek = { viewModel.seekTo(it) }
        ).also { helper ->
            helper.setMetadata(
                title = animeTitle.ifBlank { title },
                subtitle = if (episode > 0) KitsugiStrings.get(R.string.inline_episode_1_s_dc662be, episode) else "",
                durationMs = 0L
            )
            helper.updatePlaybackState(isPlaying = true, positionMs = 0L, hasNext = false)
        }

        // ── Cihaz geri tuşu zinciri (jest + 3 tuşlu gezinme + donanım tuşu) ─────────
        // Jest tabanlı gezinmede sistem KEYCODE_BACK üretmez; doğrudan OnBackPressedDispatcher
        // çalışır. Compose'taki `onPreviewKeyEvent(Key.Back)` işleyicisi ise yalnızca odağı
        // Compose aldığında (ve TV/donanım tuşlarında) devreye girer. Bu callback her iki
        // yolu da kapsar ve deterministik zinciri garanti eder:
        //   dialog → panel → sheet → kontrolleri gizle → oynatıcıdan çık.
        // Böylece geri tuşu hiçbir durumda "algılanmamış" olmaz.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isInPipNow || isInPictureInPictureMode) {
                        // PiP'te kapatma kararını sistem verir; zinciri by-pass et.
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                        return
                    }
                    when {
                        viewModel.dialogShown.value != KitsugiDialogs.None ->
                            viewModel.showDialog(KitsugiDialogs.None)
                        viewModel.panelShown.value != KitsugiPanels.None ->
                            viewModel.showPanel(KitsugiPanels.None)
                        viewModel.sheetShown.value != KitsugiSheets.None ->
                            viewModel.showSheet(KitsugiSheets.None)
                        viewModel.controlsShown.value ->
                            viewModel.hideControls()
                        else -> {
                            // Zincir tükendi: Activity'nin kendi kapanışına bırak.
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                            isEnabled = true
                        }
                    }
                }
            }
        )

        setContent {
            // Oynatıcı, ana uygulamayla AYNI tema ayarlarını kullanmalı (seçili tema rengi, AMOLED, tema modu).
            // Aksi halde KitsugiAnimeListTheme varsayılan (mint) vurgu rengiyle açılıyordu.
            val playerSettingsStore = remember { SettingsDataStore(applicationContext) }
            val playerSettings by playerSettingsStore.settingsFlow.collectAsState(
                initial = com.kitsugi.animelist.data.settings.AppSettings()
            )
            val playerDarkTheme = when (playerSettings.themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            KitsugiAnimeListTheme(
                darkTheme = playerDarkTheme,
                amoledBlack = playerSettings.amoledBlack,
                selectedThemeId = playerSettings.selectedThemeId,
                customAccentColor = playerSettings.customAccentColor,
                customAccentColor2 = playerSettings.customAccentColor2,
                customAccentGradientAngle = playerSettings.customAccentGradientAngle,
            ) {
              PlayerAccentTheme {
                KitsugiFullscreenPlayerScreen(
                    videoId          = videoId,
                    videoUrl         = videoUrl,
                    audioUrl         = audioUrl,
                    title            = title,
                    requestHeaders   = requestHeaders,
                    initialSubtitles = initialSubtitles,
                    streamSources    = streamSources,
                    initialIndex     = currentIndex,
                    malId            = malId,
                    aniListId        = aniListId,
                    tmdbId           = tmdbId,
                    bangumiId        = intent.getIntExtra("subtitle_bangumi_id", -1).takeIf { it > 0 },
                    imdbId           = intent.getStringExtra("subtitle_imdb_id"),
                    season           = season,
                    episode          = episode,
                    animeTitle       = animeTitle,
                    posterUrl        = posterUrl,
                    titleEnglish     = titleEnglish,
                    titleRomaji      = titleRomaji,
                    titleNative      = titleNative,
                    synonyms         = synonyms,
                    startYear        = startYear,
                    description      = description,
                    castList         = castList,
                    isMovie          = isMovie,
                    cs3Url           = cs3Url,
                    cs3ApiName       = cs3ApiName,
                    resumePosition   = resumePosition,
                    historyAnimeId   = historyAnimeId,
                    autoResume       = autoResume,
                    mediaType        = mediaType,
                    knownImdbId      = knownImdbId,
                    onBack           = { finish() }
                )
              }
            }
        }
    }

    /**
     * Hızlı ardışık bölüm açma/kapama senaryolarında Android bazen henüz RESUME
     * olmamış bir Activity'ye PAUSE transaction gönderir. onNewIntent override'ı
     * intent'i günceller ve bu geçiş hatalarını engeller.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isPipEnabled && isPipOnExitEnabled && lastKnownIsPlaying && !handedOffToExternalPlayer) {
            PlayerPipHelper.enterPipSafe(
                this,
                pipEngine,
                isPlaying = lastKnownIsPlaying,
                hasNext = lastKnownHasNext,
                hasPrevious = lastKnownHasPrevious
            )
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        val wasInPip = isInPipNow
        isInPipNow = isInPictureInPictureMode
        PlayerPipHelper.onPipModeChanged(isInPictureInPictureMode) { /* Screen observes via ViewModel */ }

        // Mini (PiP) penceresi çarpıya sürüklenip kapatıldığında veya "tam ekrana dön"
        // sonrası sistem Activity'yi bitirmeye karar verdiğinde: oynatma derhal durdurulur.
        // Eski davranışta Activity arka planda yaşamaya devam ediyor, ses ise çalmayı
        // sürdürüyordu (kullanıcının "arka plandan sesi gelmeye devam ediyor" şikâyeti).
        if (wasInPip && !isInPictureInPictureMode && isFinishing) {
            stopPlaybackEverywhere()
        }
    }

    /**
     * Pause while hidden, but retain the player and its playback position. When the app
     * returns, resume only if it had been playing before the background transition. This
     * avoids keeping audio alive in the background and avoids tearing down HDR/Dolby decoder
     * state (which can leave a blank or corrupted frame after returning to the Activity).
     */
    override fun onStop() {
        super.onStop()
        if (!isInPipNow && !isInPictureInPictureMode && !isChangingConfigurations) {
            resumePlaybackAfterStop = lastKnownIsPlaying && !handedOffToExternalPlayer
            stopPlaybackEverywhere()
            runCatching {
                com.kitsugi.animelist.core.player.KeepAliveService.stop(this)
            }
        }
    }

    /**
     * T2.3: MediaSession metadata/state güncellemesi — ViewModel veya Screen tarafından çağrılır.
     */
    fun updateMediaSession(
        title: String,
        episode: Int,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        hasNext: Boolean = false,
        hasPrevious: Boolean = false,
        playbackSpeed: Float = 1f,
        engine: com.kitsugi.animelist.core.player.engine.PlayerEngine? = null
    ) {
        pipEngine = engine
        lastKnownHasPrevious = hasPrevious
        lastKnownIsPlaying = isPlaying
        lastKnownHasNext = hasNext
        val helper = mediaSessionHelper ?: return
        helper.setMetadata(title = title, subtitle = KitsugiStrings.get(R.string.inline_episode_1_s_dc662be, episode), durationMs = durationMs)
        helper.updatePlaybackState(isPlaying = isPlaying, positionMs = positionMs, hasNext = hasNext, hasPrevious = hasPrevious, playbackSpeed = playbackSpeed)
        if (isPipEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PlayerPipHelper.updatePipActions(this, engine, isPlaying, hasNext, hasPrevious)
        }
    }

    override fun onDestroy() {
        // Activity tamamen kapanıyorsa (ekran döndürme hariç) ses kesin olarak durmalı.
        // Kaynakların serbest bırakılması Compose tarafındaki DisposableEffect'te yapılır;
        // burada yalnızca durdurma yapıyoruz ki kayıt (progress) işlemi bozulmasın.
        if (!isChangingConfigurations) {
            // Köprü çoktan sökülmüş olsa bile ses kesin olarak durdurulur.
            runCatching { stopPlaybackEverywhere() }
        }
        pipPlayerCallback = null
        super.onDestroy()
        tempStreamSources = null
        tempCast = null
        tempSubtitles = null
        // T2.3: MediaSession temizliği
        mediaSessionHelper?.release()
        mediaSessionHelper = null
        if (pipReceiverRegistered) {
            runCatching { unregisterReceiver(pipBroadcastReceiver) }
            pipReceiverRegistered = false
        }
        // T1.8: KeepAliveService stop
        runCatching { com.kitsugi.animelist.core.player.KeepAliveService.stop(this) }
    }
}
