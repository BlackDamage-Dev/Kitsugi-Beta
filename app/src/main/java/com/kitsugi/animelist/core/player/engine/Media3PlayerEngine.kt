package com.kitsugi.animelist.core.player.engine



import android.content.Context

import android.net.Uri

import android.util.Log

import android.util.TypedValue

import android.view.LayoutInflater

import android.view.View

import androidx.annotation.OptIn

import androidx.media3.common.C

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.flow.first

import kotlinx.coroutines.withContext

import androidx.media3.common.MediaItem

import androidx.media3.common.MimeTypes

import androidx.media3.common.PlaybackException

import androidx.media3.common.Player

import androidx.media3.common.Tracks

import androidx.media3.common.TrackSelectionOverride

import androidx.media3.common.util.UnstableApi

import androidx.media3.datasource.DefaultDataSource

import androidx.media3.datasource.okhttp.OkHttpDataSource

import androidx.media3.exoplayer.ExoPlayer

import androidx.media3.exoplayer.DefaultRenderersFactory

import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

import androidx.media3.exoplayer.source.MergingMediaSource

import androidx.media3.extractor.DefaultExtractorsFactory

import androidx.media3.extractor.ExtractorsFactory

import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory

import androidx.media3.extractor.ts.TsExtractor

import androidx.media3.ui.PlayerView

import androidx.media3.ui.CaptionStyleCompat

import com.kitsugi.animelist.R

import com.kitsugi.animelist.core.player.AudioDelayProcessor

import com.kitsugi.animelist.core.player.BitrateAwareLoadControl

import com.kitsugi.animelist.core.player.ParallelRangeDataSource

import com.kitsugi.animelist.core.player.DolbyVisionBaseLayerPolicy

import com.kitsugi.animelist.core.player.DolbyVisionCodecFallback

import com.kitsugi.animelist.core.player.DolbyVisionConversionConfig

import com.kitsugi.animelist.core.player.DolbyVisionConversionStats

import com.kitsugi.animelist.core.player.DolbyVisionExtractorsFactory

import com.kitsugi.animelist.core.player.DoviBridge

import com.kitsugi.animelist.core.player.SubtitleInput

import com.kitsugi.animelist.core.player.dvmkv.DolbyVisionCompatibility

import com.kitsugi.animelist.core.player.PlayerMediaSourceFactory

import com.kitsugi.animelist.core.player.PlayerLoadingDiagnostics

import com.kitsugi.animelist.core.player.PlayerPlaybackAnalytics

import com.kitsugi.animelist.data.cloudstream.CsVideoInterceptorFactory

import com.kitsugi.animelist.data.cloudstream.diag.CsTrace

import com.kitsugi.animelist.data.settings.AppSettings

import com.kitsugi.animelist.data.settings.Dv7HandlingMode

import com.kitsugi.animelist.data.trailer.YoutubeChunkedDataSourceFactory

import com.kitsugi.animelist.ui.screens.fullscreen.components.SubtitleStyleSettings

import com.kitsugi.animelist.ui.screens.fullscreen.components.TrackOption

import com.kitsugi.animelist.ui.screens.fullscreen.components.StreamInfoData

import java.util.Locale



import com.kitsugi.animelist.core.player.buildWithAssSupportCompat

import com.kitsugi.animelist.core.player.getAssHandlerCompat

import com.kitsugi.animelist.core.player.clearAssHandlerCompat



@OptIn(androidx.media3.common.util.UnstableApi::class)

class Media3PlayerEngine(

    private val context: Context,

    private val settings: AppSettings

) : PlayerEngine {



    private companion object {

        /**

         * Otomatik altyazı seçimi için izin verilen en fazla deneme sayısı.

         * Harici altyazıların akışa geç bağlanması durumunda politikanın birkaç

         * `onTracksChanged` turunda yeniden denemesine imkân verir; sonsuz döngüyü engeller.

         */

        const val MAX_AUTO_SUBTITLE_ATTEMPTS = 8



        /**

         * Altyazının ekranın altından ne kadar yukarıda durduğu (görünüm yüksekliğinin oranı).

         * Sabit bir varsayılan: görüntü modu değişse de altyazı hep aynı yerde durur.

         */

        const val SUBTITLE_BOTTOM_PADDING_FRACTION = 0.08f



        /** Parça seçiminin uygulanıp uygulanmadığını kontrol etmeden önce beklenecek süre. */

        const val TRACK_SELECTION_VERIFY_DELAY_MS = 450L



        /** Zaman çizgisi bildirim aralığı (4 Hz). */

        const val POSITION_TICK_INTERVAL_MS = 250L

    }



    private val TAG = "Media3PlayerEngine"

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    @Volatile private var released = false



    /**

     * İlk video karesi gerçekten çizildi mi? [Player.Listener.onRenderedFirstFrame] ile

     * işaretlenir; her yeni medyada sıfırlanır. `currentTracks` video parçasını seçili

     * gösterse bile çözücü kare üretemiyorsa bu bayrak `false` kalır ve küçük oynatıcı

     * sessiz "sesi var, görüntüsü yok" durumunu yakalayıp yedek motora geçebilir.

     */

    @Volatile private var renderedFirstVideoFrame = false



    /**

     * Zaman çizgisi darbesi. Media3 konum değişimlerini `Player.Listener` üzerinden

     * bildirmediği için detay sayfasındaki küçük oynatıcı sayacı 00:00'da donmuş

     * görünüyordu. Saniyede dört bildirim sayaç ve ilerleme çubuğu için fazlasıyla

     * yeterlidir; 60 Hz'lik bir akış ana iş parçacığını ve Compose'u boğardı.

     */

    private val positionTickRunnable = object : Runnable {

        override fun run() {

            if (released) return

            val player = exoPlayer ?: return

            if (!player.isPlaying) return

            val positionMs = player.currentPosition.coerceAtLeast(0L)

            val durationMs = player.duration.coerceAtLeast(0L)

            listeners.forEach { it.onPositionChanged(positionMs, durationMs) }

            mainHandler.postDelayed(this, POSITION_TICK_INTERVAL_MS)

        }

    }



    private fun startPositionTicker() {

        mainHandler.removeCallbacks(positionTickRunnable)

        mainHandler.postDelayed(positionTickRunnable, POSITION_TICK_INTERVAL_MS)

    }



    private fun stopPositionTicker() {

        mainHandler.removeCallbacks(positionTickRunnable)

    }

    private fun runOnMainThread(block: () -> Unit) {

        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {

            block()

        } else {

            mainHandler.post(block)

        }

    }

    private var exoPlayer: ExoPlayer? = null

    private var trackSelector: androidx.media3.exoplayer.trackselection.DefaultTrackSelector? = null



    /**

     * Kullanıcının seçtiği çözünürlük tavanı (piksel yüksekliği). `null` → sınırsız.

     * DASH gibi tek URL'li adaptif akışlarda "Kalite" seçimi bu değerle uygulanır.

     */

    private var maxVideoHeight: Int? = null



    /**

     * Kullanıcının ayarlardaki **Kalite Profili**'nden gelen çözünürlük/bit hızı tavanı.

     * Elle kalite seçilmediyse uygulanır; ayar değişirse `prepare()` sırasında tazelenir.

     */

    private var profileMaxHeight: Int? = null

    private var profileMaxBitrateKbps: Int? = null

    private var playerView: PlayerView? = null

    private var assSubtitleView: View? = null

    private val listeners = mutableListOf<PlayerEngine.Listener>()

    private val gainAudioProcessor = com.kitsugi.animelist.core.player.GainAudioProcessor().apply {

        setGainDb(settings.defaultAudioBoost * 6f)

    }

    /**

     * Gerçek ses gecikmesi (lip-sync). Media3'te yerleşik bir API olmadığı için PCM hattında

     * sessizlik ekleyip/örnek atlayarak uygulanır. Paneldeki "Ses gecikmesi" artık no-op değil.

     */

    private val audioDelayProcessor = AudioDelayProcessor().apply {

        setDelayMs(settings.defaultAudioDelayMs)

    }

    private var currentAspectMode: com.kitsugi.animelist.core.player.PlayerAspectMode = com.kitsugi.animelist.core.player.PlayerAspectMode.ORIGINAL



    /** Kullanıcının seçtiği anlık hız; yeni kaynak hazırlığında sıfırlanmasın. */

    private var currentPlaybackSpeed: Float = settings.playerSpeed.coerceIn(0.25f, 4.0f)



    // ── T1.13: Diagnostics & Analytics ───────────────────────────────────────

    private val mediaSourceFactory = PlayerMediaSourceFactory(context, settings)

    val diagnostics = PlayerLoadingDiagnostics()

    val analytics = PlayerPlaybackAnalytics()

    

    private var currentResizeMode: Int = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT

    private var currentSubtitleStyle = SubtitleStyleSettings()



    override val engineType: PlayerEngineType = PlayerEngineType.MEDIA3

    

    override var currentState: PlayerEngine.State = PlayerEngine.State.IDLE

        private set

        

    override val bufferedPosition: Long get() = exoPlayer?.bufferedPosition ?: 0L



    override val currentPosition: Long

        get() = exoPlayer?.currentPosition ?: 0L

        

    override val duration: Long

        get() = exoPlayer?.duration?.coerceAtLeast(0L) ?: 0L

        

    override val isPlaying: Boolean

        get() = exoPlayer?.isPlaying ?: false



    /**

     * Seçili video parçası var mı?

     *

     * Ses ve görüntü ayrı akışlardan geldiğinde (imzalı YouTube adaptif akışları) ya da

     * cihazın çözemediği bir video kodeği geldiğinde (ör. AnimeThemes'in VP9 `.webm`

     * dosyaları) Media3 hata ÜRETMEZ: parça seçici video parçasını atlar, ses çalmaya

     * devam eder, süre ilerler. Kullanıcı bunu "sesi var ama görüntüsü yok" olarak görür.

     * Küçük oynatıcı bekçisi bu bayrakla durumu yakalayıp diğer motora geçer.

     */

    override val hasVideoTrack: Boolean

        get() = exoPlayer?.currentTracks

            ?.groups

            ?.any { group ->

                group.type == androidx.media3.common.C.TRACK_TYPE_VIDEO && group.isSelected

            } == true



    /**

     * Seçilen video parçası için en az bir kare çizildi mi?

     *

     * Yalnızca [hasVideoTrack]'e güvenmek, çözücüsü olmayan (ör. VP9) bir dosyada

     * "parça seçili ama ekran siyah" durumunu kaçırıyordu. Media3 ilk kareyi

     * `onRenderedFirstFrame` ile bildirir; bu bayrak o ana kadar `false` kalır.

     */

    override val hasRenderedVideoFrame: Boolean

        get() = renderedFirstVideoFrame

        

    override val currentSpeed: Float

        get() = exoPlayer?.playbackParameters?.speed ?: settings.playerSpeed

        

    override val currentVolume: Float

        get() = exoPlayer?.volume ?: 1.0f



    override var subtitleDelayMs: Long = 0L

        private set

        

    override var audioDelayMs: Long = settings.defaultAudioDelayMs

        private set

        

    override val isSubtitleDisabled: Boolean

        get() = exoPlayer?.trackSelectionParameters?.disabledTrackTypes?.contains(C.TRACK_TYPE_TEXT) ?: true



    // Media3'te altyazı zaman kaydırma / hız / renk filtresi yerleşik değildir.

    // UI bu bayraklarla MPV'ye geçiş yapar; böylece paneldeki kontroller no-op kalmaz.

    override val supportsSubtitleDelay: Boolean get() = false

    override val supportsNegativeSubtitleDelay: Boolean get() = false

    override val supportsSecondarySubtitleDelay: Boolean get() = false

    override val supportsSubtitleSpeed: Boolean get() = false

    override val supportsVideoFilters: Boolean get() = false

    override val supportsNegativeAudioDelay: Boolean get() = false



    private var currentAddonName: String? = null

    private var isCsSource: Boolean = false

    private var streamName: String? = null

    private var streamTitle: String? = null

    private var videoUrl: String? = null



    private var videoWidth: Int? = null

    private var videoHeight: Int? = null

    private var videoFrameRate: Float? = null

    private var videoBitrate: Int? = null

    private var videoCodec: String? = null

    private var audioCodec: String? = null

    private var audioChannels: String? = null

    private var audioSampleRate: Int? = null

    private var audioLanguage: String? = null



    /** Hazırlık sırasında motorun aldığı SubtitleInput listesi (dahili/harici bayrağını taşır). */

    private var preparedSubtitles: List<SubtitleInput> = emptyList()

    /**

     * İlk otomatik parça seçimi tamamlandı mı?

     * true = tamamlandı (kullanıcı manuel değişiklik yapabilir, tekrar tetiklenmez)

     * false = henüz yapılmadı (bir sonraki onTracksChanged'de çalışacak)

     */

    private var initialSelectionDone: Boolean = false

    private var initialAudioSelectionDone: Boolean = false

    private var userSelectedAudioTrack: Boolean = false



    /**

     * Kullanıcı altyazı parçasını ELLE seçti mi? true ise otomatik politika bir daha devreye

     * girmez (kullanıcının seçimi ezilmez).

     */

    private var userSelectedTextTrack: Boolean = false



    /**

     * Otomatik Türkçe/tercih edilen altyazı seçimi sonuçlandı mı?

     *

     * Neden tek atımlık `initialSelectionDone` yetmiyordu: ExoPlayer ilk `onTracksChanged`

     * olayını çoğu akışta **harici (sideloaded) altyazılar henüz bağlanmadan** verir. Politika

     * o anda bir kez çalışıp `initialSelectionDone=true` yaptığı için, videonun kendi Türkçe

     * parçası listeye bir tık geç düşünce seçilmiyor ve kullanıcı altyazısız kalıyordu.

     * Artık seçim, hedef dil gerçekten seçilene (veya gerçekten hiç yok sayılana) kadar

     * sınırlı sayıda yeniden denenir.

     */

    private var autoSubtitleSelectionSettled: Boolean = false

    private var autoSubtitleAttemptCount: Int = 0



    override val activeStreamInfo: StreamInfoData

        get() = StreamInfoData(

            addonName = currentAddonName ?: "Dahili",

            streamName = streamName,

            streamDescription = streamTitle,

            filename = videoUrl?.substringAfterLast('/'),

            playerEngine = "ExoPlayer (Dahili)",

            videoWidth = videoWidth,

            videoHeight = videoHeight,

            videoFrameRate = videoFrameRate,

            videoBitrate = videoBitrate,

            videoCodec = videoCodec,

            audioCodec = audioCodec,

            audioChannels = audioChannels,

            audioSampleRate = audioSampleRate,

            audioLanguage = audioLanguage

        )



    private val playerListener = object : Player.Listener {

        override fun onRenderedFirstFrame() {

            renderedFirstVideoFrame = true

        }



        override fun onIsPlayingChanged(isPlaying: Boolean) {

            // CRITICAL: This is the correct callback for play/pause transitions in ExoPlayer/Media3.

            // onPlaybackStateChanged does NOT fire when the user pauses — only this callback does.

            // Without this, playerEngine.pause() silently succeeds but isPlayingState in the

            // Compose UI never updates, making the pause/stop button appear broken.

            Log.d(TAG, "onIsPlayingChanged: isPlaying=$isPlaying state=$currentState")

            analytics.onPlayingChanged(isPlaying)

            if (isPlaying) startPositionTicker() else stopPositionTicker()

            listeners.forEach { it.onPlayingChanged(isPlaying) }

            listeners.forEach { it.onStateChanged(currentState) }

        }



        override fun onPlaybackStateChanged(playbackState: Int) {

            val mappedState = when (playbackState) {

                Player.STATE_IDLE -> PlayerEngine.State.IDLE

                Player.STATE_BUFFERING -> PlayerEngine.State.BUFFERING

                Player.STATE_READY -> PlayerEngine.State.READY

                Player.STATE_ENDED -> PlayerEngine.State.ENDED

                else -> PlayerEngine.State.IDLE

            }

            currentState = mappedState

            diagnostics.onStateChanged(mappedState.name)

            when (playbackState) {

                Player.STATE_BUFFERING -> diagnostics.onBufferingStarted("rebuffer")

                Player.STATE_READY     -> diagnostics.onBufferingEnded()

                Player.STATE_ENDED     -> {

                    diagnostics.onPlaybackEnded()

                    analytics.endSession()

                }

                else -> { /* no-op */ }

            }

            listeners.forEach { it.onStateChanged(mappedState) }

        }



        override fun onPlayerError(error: PlaybackException) {

            val detailed = buildDetailedPlaybackMessage(error)

            Log.e(TAG, "ExoPlayer error code=${error.errorCode} name=${runCatching { error.errorCodeName }.getOrNull()} msg=$detailed url=${videoUrl?.take(110)}", error)

            try {

                CsTrace.error("Player", "playback", "ExoPlayer code=${error.errorCode} msg=$detailed url=${videoUrl?.take(100)} host=${runCatching { java.net.URI(videoUrl ?: "").host }.getOrNull()}", error)

            } catch (_: Exception) {}

            diagnostics.onPlaybackError(error.errorCode, detailed)

            diagnostics.dump()

            listeners.forEach {

                it.onPlaybackError(error.errorCode, detailed, error)

            }

        }



        private fun buildDetailedPlaybackMessage(error: PlaybackException): String {

            val base = error.localizedMessage?.takeIf { it.isNotBlank() } ?: "Oynatma hatası"

            val codeName = runCatching { error.errorCodeName }.getOrNull() ?: "CODE_${error.errorCode}"

            val httpCode = Regex("""Response code:\s*(\d{3})""").find(base)?.groupValues?.get(1)

                ?: Regex("""Response code:\s*(\d{3})""").find(error.cause?.message ?: "")?.groupValues?.get(1)

            val causeChain = generateSequence(error.cause as Throwable?) { it.cause }

                .joinToString(" → ") { "${it.javaClass.simpleName}: ${it.message?.take(120)}" }

                .takeIf { it.isNotBlank() }

            val hint = when {

                httpCode == "403" || base.contains("403") -> " (403 Erişim reddedildi — kaynak engelli/VPN gerekebilir, Origin/Referer kontrol et)"

                httpCode == "404" -> " (404 Kaynak kaldırılmış)"

                httpCode?.startsWith("5") == true -> " (Sunucu hatası $httpCode)"

                base.contains("5004") -> " (CDN 5004 — Origin/Referer hatası, app güncelle)"

                base.contains("UnrecognizedInputFormat") || base.contains("None of the available extractors") -> " (Format tanınamadı — HLS/MP4 ayrımı hatası olabilir)"

                error.errorCode in 4000..4005 -> " (Kodek çözücü hatası — MPV denenebilir)"

                else -> ""

            }

            return buildString {

                append(base)

                append(" [kod $codeName")

                if (httpCode != null) append("/HTTP $httpCode")

                append("$hint]")

                if (causeChain != null) append("\nNeden: $causeChain")

                videoUrl?.let { append("\nURL: ${it.take(110)}") }

                // Host ipucu: closeload / rapidrame gibi bilinen CDN’lerde teşhis kolaylaşsın

                runCatching { java.net.URI(videoUrl ?: "").host } .getOrNull()?.let { h -> append("\nHost: $h") }

            }

        }



        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {

            videoWidth = videoSize.width.takeIf { it > 0 }

            videoHeight = videoSize.height.takeIf { it > 0 }

            applyAspectMode()

            listeners.forEach {

                it.onVideoSizeChanged(

                    videoSize.width,

                    videoSize.height,

                    videoSize.unappliedRotationDegrees,

                    videoSize.pixelWidthHeightRatio

                )

            }

        }



        override fun onTracksChanged(tracks: Tracks) {

            // Subtitle track detection

            val textOptions = mutableListOf<TrackOption>()

            val textGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }

            textGroups.forEachIndexed { groupIndex, group ->

                for (trackIndex in 0 until group.length) {

                    val format = group.getTrackFormat(trackIndex)

                    val rawLang = format.language

                    val mappedLang = when (rawLang?.lowercase(Locale.ROOT)) {

                        "tur", "tr" -> "Türkçe"

                        "eng", "en" -> "İngilizce"

                        "jpn", "ja" -> "Japonca"

                        "ger", "de" -> "Almanca"

                        "fre", "fra", "fr" -> "Fransızca"

                        "spa", "es" -> "İspanyolca"

                        "ita", "it" -> "İtalyanca"

                        "rus", "ru" -> "Rusça"

                        "chi", "zho", "zh" -> "Çince"

                        "kor", "ko" -> "Korece"

                        else -> rawLang?.uppercase(Locale.ROOT)

                    }

                    val trackLabel = trackLabelWithSupport(

                        format.label ?: mappedLang ?: "Altyazı #${textOptions.size + 1}",

                        group.isTrackSupported(trackIndex)

                    )

                    textOptions.add(

                        TrackOption(

                            group = group,

                            groupIndex = groupIndex,

                            trackIndex = trackIndex,

                            label = trackLabel,

                            isSelected = group.isTrackSelected(trackIndex)

                        )

                    )

                }

            }



            // Audio track detection

            val audioOptions = mutableListOf<TrackOption>()

            val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }

            audioGroups.forEachIndexed { groupIndex, group ->

                for (trackIndex in 0 until group.length) {

                    val format = group.getTrackFormat(trackIndex)

                    val rawLang = format.language

                    val mappedLang = when (rawLang?.lowercase(Locale.ROOT)) {

                        "tur", "tr" -> "Türkçe"

                        "eng", "en" -> "İngilizce"

                        "jpn", "ja" -> "Japonca"

                        "ger", "de" -> "Almanca"

                        "fre", "fra", "fr" -> "Fransızca"

                        "spa", "es" -> "İspanyolca"

                        "ita", "it" -> "İtalyanca"

                        "rus", "ru" -> "Rusça"

                        "chi", "zho", "zh" -> "Çince"

                        "kor", "ko" -> "Korece"

                        else -> rawLang?.uppercase(Locale.ROOT)

                    }

                    val trackLabel = trackLabelWithSupport(

                        format.label ?: mappedLang ?: "Ses #${audioOptions.size + 1}",

                        group.isTrackSupported(trackIndex)

                    )

                    audioOptions.add(

                        TrackOption(

                            group = group,

                            groupIndex = groupIndex,

                            trackIndex = trackIndex,

                            label = trackLabel,

                            isSelected = group.isTrackSelected(trackIndex),

                            audioLanguage = format.language,

                            audioName = format.label,

                            audioCodec = format.sampleMimeType,

                            audioBitrate = format.bitrate.takeIf { it > 0 },

                            audioMediaUrl = videoUrl

                        )

                    )

                }

            }



            // ── Ses parçası seçimi (Türkçe önce, sonra tercih listesi, boşsa ilk ses) ──────────────

            // HLS veya çok dilli akışlarda audio parçaları DEFAULT=NO ile gelebilir (örn. Vidmixi/Dosyaload).

            // Hiçbir parça seçili değilse player sessiz oynar! Bu durumda her zaman geçerli bir ses seçilmelidir.

            // Kullanıcı sesi elle seçtiyse otomatik politika TAMAMEN devre dışı kalır.

            // Aksi hâlde seçim sonrası parça geçişi sırasında oluşan kısa "hiçbir ses seçili

            // değil" anı, otomatik seçiciyi tetikleyip kullanıcının seçimini geri alabiliyordu.

            if (audioOptions.isNotEmpty() && !userSelectedAudioTrack) {

                val currentlySelectedAudio = audioOptions.firstOrNull { it.isSelected }

                val isSelectedAudioTurkish = currentlySelectedAudio != null && run {

                    val format = currentlySelectedAudio.group.getTrackFormat(currentlySelectedAudio.trackIndex)

                    com.kitsugi.animelist.core.player.PlayerSubtitleUtils.isTurkish(format.language, format.label)

                }



                // Hiçbir ses seçili değilse VEYA (kullanıcı henüz elle seçmediyse ve Türkçe ses bulunabilir durumdaysa):

                if (currentlySelectedAudio == null || (!userSelectedAudioTrack && !initialAudioSelectionDone)) {

                    val preferredLangs = settings.preferredAudioLanguages

                        .split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }



                    val preferredAudio = run {

                        var found: TrackOption? = null

                        for (lang in preferredLangs) {

                            found = audioOptions.find { opt ->

                                val format = opt.group.getTrackFormat(opt.trackIndex)

                                com.kitsugi.animelist.core.player.PlayerSubtitleUtils.matchesTrackLanguage(format.language, format.label, lang)

                            }

                            if (found != null) break

                        }

                        found ?: audioOptions.firstOrNull()

                    }



                    preferredAudio?.let {

                        if (currentlySelectedAudio != it) {

                            Log.i(TAG, "Auto-selecting audio track (current=${currentlySelectedAudio?.label}): ${it.label}")

                            selectTrack(it)

                        }

                    }

                    initialAudioSelectionDone = true

                }

            }



            // ── Otomatik altyazı seçimi ────────────────────────────────────────────────

            // Kullanıcının ELLE yaptığı seçim asla ezilmez; onun dışında hedef dil gerçekten

            // seçilene kadar (sınırlı denemeyle) yeniden denenir. Tek atımlık eski davranış,

            // harici altyazılar ilk onTracksChanged'e yetişmediğinde videonun kendi Türkçe

            // parçasının hiç seçilmemesine yol açıyordu.

            if (!userSelectedTextTrack && !autoSubtitleSelectionSettled) {

                val preferredLangs = settings.preferredSubtitleLanguages

                    .split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }



                // ── Altyazı: Kesin Öncelik Hiyerarşisi ──────────────────────────────────

                // 1. Dahili / site kaynaklı Türkçe altyazı (isExternal = false)

                // 2. Harici (OpenSubtitles vb.) Türkçe altyazı (isExternal = true)

                // 3. Kullanıcı diğer tercih dilleri

                // 4. Eşleşme yoksa: Devre dışı bırak (İtalyanca vb. yabancı diller ASLA seçilmez!)

                if (!isSubtitleDisabled && textOptions.isNotEmpty()) {

                    // Harici altyazı tespiti: ÖNCE track kimliği (setId ile verilen sub.url),

                    // sonra birebir (büyük/küçük harf duyarsız) isim eşleşmesi.

                    // Eskiden "contains" tabanlı eşleşme yüzünden dahili "Türkçe" parçası,

                    // "Türkçe (addon)" adlı harici altyazıyla karıştırılıp harici sayılıyordu.

                    val isExternalTrack: (TrackOption) -> Boolean = { opt ->

                        val format = opt.group.getTrackFormat(opt.trackIndex)

                        val label = format.label ?: ""

                        val id = format.id ?: ""

                        val externals = preparedSubtitles.filter { it.isExternal }

                        externals.any { sub ->

                            (id.isNotBlank() && (id == sub.url || id == "file://${sub.url}")) ||

                                (label.isNotBlank() && sub.name.isNotBlank() && label.equals(sub.name, ignoreCase = true))

                        }

                    }



                    val currentlySelected = textOptions.firstOrNull { it.isSelected }

                    // Politikanın kabul ettiği bir parça seçili mi? (dahili/harici Türkçe ya da

                    // kullanıcının tercih listesindeki bir dil)

                    val currentSatisfiesPolicy = currentlySelected != null && run {

                        val format = currentlySelected.group.getTrackFormat(currentlySelected.trackIndex)

                        com.kitsugi.animelist.core.player.PlayerSubtitleUtils.isTurkish(format.language, format.label) ||

                            com.kitsugi.animelist.core.player.SubtitleSelectionPolicy

                                .effectiveLanguages(preferredLangs)

                                .any { lang ->

                                    com.kitsugi.animelist.core.player.PlayerSubtitleUtils

                                        .matchesTrackLanguage(format.language, format.label, lang)

                                }

                    }



                    if (currentSatisfiesPolicy && settings.subtitleWhitelist.isBlank() && settings.subtitleBlacklist.isBlank()) {

                        // Hedefe ulaşıldı — artık otomatik seçim devreye girmez.

                        autoSubtitleSelectionSettled = true

                    } else {

                        // Tek ortak politika (SubtitleSelectionPolicy) — MPV ile birebir aynı sonuç.

                        val candidates = textOptions.map { opt ->

                            val format = opt.group.getTrackFormat(opt.trackIndex)

                            com.kitsugi.animelist.core.player.SubtitleSelectionPolicy.Candidate(

                                item = opt,

                                lang = format.language,

                                label = format.label,

                                isExternal = isExternalTrack(opt),

                            )

                        }

                        val bestSub = com.kitsugi.animelist.core.player.SubtitleSelectionPolicy.pick(candidates, preferredLangs,

                            settings.subtitleWhitelist.split(","), settings.subtitleBlacklist.split(","))

                        if (bestSub != null) {

                            // Sınırlı yeniden deneme: override'ın bir sonraki onTracksChanged'e

                            // yansıması gerekir; yansımazsa sonsuz döngü yerine pes edilir.

                            if (currentlySelected != bestSub && autoSubtitleAttemptCount < MAX_AUTO_SUBTITLE_ATTEMPTS) {

                                autoSubtitleAttemptCount++

                                Log.i(

                                    TAG,

                                    "Auto-selecting best subtitle track (attempt $autoSubtitleAttemptCount): ${bestSub.label}"

                                )

                                selectTrack(bestSub)

                            } else if (currentlySelected == bestSub || autoSubtitleAttemptCount >= MAX_AUTO_SUBTITLE_ATTEMPTS) {

                                autoSubtitleSelectionSettled = true

                            }

                        } else {

                            // Türkçe veya tercih edilen dil yoksa yabancı dili (İtalyanca vb.) kapat

                            Log.i(TAG, "No Turkish/preferred subtitle found. Disabling non-preferred track: ${currentlySelected?.label}")

                            disableSubtitles()

                            autoSubtitleSelectionSettled = true

                        }

                    }

                }

            }



            // Detailed format diagnostics

            videoWidth = null

            videoHeight = null

            videoFrameRate = null

            videoBitrate = null

            videoCodec = null

            audioCodec = null

            audioChannels = null

            audioSampleRate = null

            audioLanguage = null



            tracks.groups.forEach { group ->

                for (i in 0 until group.length) {

                    if (group.isTrackSelected(i)) {

                        val format = group.getTrackFormat(i)

                        if (group.type == C.TRACK_TYPE_VIDEO) {

                            videoWidth = format.width.takeIf { it > 0 }

                            videoHeight = format.height.takeIf { it > 0 }

                            videoFrameRate = format.frameRate.takeIf { it > 0f }

                            videoBitrate = format.bitrate.takeIf { it > 0 }

                            videoCodec = format.codecs ?: format.sampleMimeType

                        } else if (group.type == C.TRACK_TYPE_AUDIO) {

                            audioCodec = format.codecs ?: format.sampleMimeType

                            audioChannels = format.channelCount.takeIf { it > 0 }?.toString()

                            audioSampleRate = format.sampleRate.takeIf { it > 0 }

                            audioLanguage = format.language

                        }

                    }

                }

            }



            // Extract native chapters from track metadata (ID3 ChapterFrame / VorbisComment)

            val extractedChapters = mutableListOf<com.kitsugi.animelist.core.player.PlayerChapter>()

            val vorbisPairs = mutableListOf<Pair<String, String>>()

            tracks.groups.forEach { group ->

                for (i in 0 until group.length) {

                    val metadata = group.getTrackFormat(i).metadata ?: continue

                    for (entryIdx in 0 until metadata.length()) {

                        when (val entry = metadata.get(entryIdx)) {

                            is androidx.media3.extractor.metadata.id3.ChapterFrame -> {

                                var title: String? = null

                                for (subIdx in 0 until entry.subFrameCount) {

                                    val sub = entry.getSubFrame(subIdx)

                                    if (sub is androidx.media3.extractor.metadata.id3.TextInformationFrame &&

                                        sub.id.equals("TIT2", ignoreCase = true)

                                    ) {

                                        title = sub.values.firstOrNull()?.takeIf { it.isNotBlank() }

                                        if (title != null) break

                                    }

                                }

                                val startMs = entry.startTimeMs.toLong()

                                if (startMs >= 0L) {

                                    extractedChapters.add(

                                        com.kitsugi.animelist.core.player.PlayerChapter(

                                            title = title ?: entry.chapterId.takeIf { it.isNotBlank() } ?: "Chapter ${extractedChapters.size + 1}",

                                            positionMs = startMs

                                        )

                                    )

                                }

                            }

                            is androidx.media3.extractor.metadata.vorbis.VorbisComment -> {

                                vorbisPairs.add(entry.key to entry.value)

                            }

                        }

                    }

                }

            }

            if (vorbisPairs.isNotEmpty()) {

                extractedChapters.addAll(

                    com.kitsugi.animelist.core.player.PlayerChapterPolicy.parseVorbisComments(vorbisPairs)

                )

            }

            if (extractedChapters.isNotEmpty()) {

                val normalizedChapters = com.kitsugi.animelist.core.player.PlayerChapterPolicy.normalize(

                    extractedChapters,

                    duration

                )

                listeners.forEach { it.onChaptersChanged(normalizedChapters) }

            }



            listeners.forEach { it.onTracksChanged(audioOptions, textOptions) }

        }

    }



    // ── Dolby Vision runtime state ──────────────────────────────────────────

    /** Policy result computed once per playback session in [prepare]. Null = not yet resolved. */

    @Volatile private var dvPolicy: DolbyVisionBaseLayerPolicy.Result? = null

    /** Effective DV7 handling mode for the current playback. Derived from settings + policy. */

    @Volatile private var effectiveDv7Mode: Dv7HandlingMode = Dv7HandlingMode.AUTO

    /**

     * Gizli DV çözücü keşfi yalnızca dönüştürme aktifken devreye girer.

     * Bazı Amlogic/MTK cihazlar `video/dolby-vision` çözücüsünü yalnızca

     * profil 7 olarak bildirir; libdovi 8.1'e çevirdiğinde standart seçici

     * çözücüyü reddeder ve HDR10'a düşer. Bkz. [DolbyVisionCodecFallback].

     */

    private val dv81CodecFallbackActive = java.util.concurrent.atomic.AtomicBoolean(false)



    private fun initializePlayer() {

        runOnMainThread {

            if (exoPlayer != null) return@runOnMainThread

            val loadControl = BitrateAwareLoadControl(

                minBufferMs = settings.minBufferMs,

                maxBufferMs = settings.maxBufferMs,

                bufferForPlaybackMs = settings.bufferForPlaybackMs,

                bufferForPlaybackAfterRebufferMs = settings.bufferForPlaybackAfterRebufferMs,

                prioritizeTimeOverSizeThresholds = true,

                backBufferDurationMs = settings.backBufferDurationMs,

                retainBackBufferFromKeyframe = false

            )

            val renderersFactory = object : DefaultRenderersFactory(context) {

                override fun buildAudioSink(

                    context: Context,

                    enableFloatOutput: Boolean,

                    enableAudioTrackPlaybackParams: Boolean

                ): androidx.media3.exoplayer.audio.AudioSink? {

                    val caps = androidx.media3.exoplayer.audio.AudioCapabilities.getCapabilities(context)

                    return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)

                        .setAudioCapabilities(caps)

                        .setAudioProcessors(arrayOf(gainAudioProcessor, audioDelayProcessor))

                        .setEnableFloatOutput(enableFloatOutput)

                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)

                        .build()

                }

            }.apply {

                val mode = if (settings.decoderPriority == 0) {

                    androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON

                } else {

                    settings.decoderPriority

                }

                setExtensionRendererMode(mode)

                setEnableDecoderFallback(true)

                // Profil-bağımsız DV çözücü yedeği: yalnızca dönüştürme aktifken ve

                // standart seçici `video/dolby-vision` için boş döndüğünde devreye girer.

                setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->

                    val standard = androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT

                        .getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)

                    if (standard.isNotEmpty() || !dv81CodecFallbackActive.get() ||

                        !mimeType.equals(MimeTypes.VIDEO_DOLBY_VISION, ignoreCase = true)

                    ) {

                        standard

                    } else {

                        DolbyVisionCodecFallback.findDvDecodersIgnoringProfile()

                    }

                }

            }



            val preferredLangs = settings.preferredSubtitleLanguages.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

            val expandedTextLangs = mutableListOf<String>()

            preferredLangs.forEach { lang ->

                if (lang == "tr" || lang == "tur") {

                    expandedTextLangs.add("tr")

                    expandedTextLangs.add("tur")

                    expandedTextLangs.add("turkish")

                } else {

                    expandedTextLangs.add(lang)

                }

            }

            val preferredAudioLangs = settings.preferredAudioLanguages.split(",").map { it.trim() }.filter { it.isNotEmpty() }

            val localSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context).apply {

                parameters = buildUponParameters()

                    .setPreferredAudioLanguages(*preferredAudioLangs.toTypedArray())

                    .setPreferredTextLanguages(*expandedTextLangs.toTypedArray())

                    .setSelectUndeterminedTextLanguage(false)

                    .build()

            }

            this.trackSelector = localSelector



            val baseBuilder = ExoPlayer.Builder(context)

                .setTrackSelector(localSelector)

                .setLoadControl(loadControl)



            exoPlayer = if (settings.enableAssExtractor) {

                val dsFactoryForAss = mediaSourceFactory.buildDataSourceFactory(

                    addonName = null,

                    headers = emptyMap(),

                    videoUrl = "",

                    streamTitle = null,

                    isCS = false,

                    qualityValue = null

                )

                baseBuilder.buildWithAssSupportCompat(

                    context = context,

                    renderType = io.github.peerless2012.ass.media.type.AssRenderType.CUES,

                    playerMediaSourceFactory = mediaSourceFactory,

                    dataSourceFactory = dsFactoryForAss,

                    extractorsFactory = DefaultExtractorsFactory()

                        .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS)

                        .setTsExtractorTimestampSearchBytes(1500 * TsExtractor.TS_PACKET_SIZE),

                    renderersFactory = renderersFactory

                )

            } else {

                baseBuilder

                    .setRenderersFactory(renderersFactory)

                    .build()

            }.apply {

                playWhenReady = true

                addListener(playerListener)

                // Set proper AudioAttributes so Android audio focus is handled correctly.

                // Without this, MIUI/Xiaomi may silently duck or mute media audio.

                setAudioAttributes(

                    androidx.media3.common.AudioAttributes.Builder()

                        .setUsage(androidx.media3.common.C.USAGE_MEDIA)

                        .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)

                        .build(),

                    /* handleAudioFocus = */ true

                )

            }

            playerView?.player = exoPlayer

            // Kalite tavanı yeni oynatıcıya da uygulanır (motor yeniden kurulursa kaybolmaz).

            applyProfileJson(settings.qualityProfileJson)

            applyTrackLimits(localSelector)

        }

    }



    init {

        initializePlayer()

    }



    override fun addListener(listener: PlayerEngine.Listener) {

        listeners.add(listener)

    }



    override fun removeListener(listener: PlayerEngine.Listener) {

        listeners.remove(listener)

    }



    override suspend fun prepare(

        videoUrl: String,

        audioUrl: String?,

        externalAudioTracks: List<com.kitsugi.animelist.core.player.ExternalAudioTrackInput>,

        streamTypeHint: String?,

        headers: Map<String, String>,

        subtitles: List<SubtitleInput>,

        startPositionMs: Long,

        addonName: String?,

        isCS: Boolean,

        streamTitle: String?,

        qualityValue: Int?,

        mediaType: com.kitsugi.animelist.core.player.StreamMediaType?,

        csStreamType: String?,

        csExtractorData: String?

    ) {

        withContext(Dispatchers.Main) {

            initializePlayer()

            // Ayar ekranında kalite profili değiştirilmiş olabilir: her hazırlıkta tazele.

            // (Uygulama, aşağıdaki dil parametreleri yazıldıktan SONRA yapılır; aksi hâlde

            //  sonraki `trackSelectionParameters` yazımı tavanı ezebilir.)

            refreshProfileLimits()

            val player = exoPlayer

            if (player != null) {

                // A fixed rendition belongs to the old media, never to the next source/episode.

                player.trackSelectionParameters = player.trackSelectionParameters

                    .buildUpon()

                    .clearOverridesOfType(C.TRACK_TYPE_VIDEO)

                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)

                    .build()

            }

            trackSelector?.let { selector ->

                val preferredLangs = settings.preferredSubtitleLanguages.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

                val expandedTextLangs = mutableListOf<String>()

                preferredLangs.forEach { lang ->

                    if (lang == "tr" || lang == "tur") {

                        expandedTextLangs.add("tr")

                        expandedTextLangs.add("tur")

                        expandedTextLangs.add("turkish")

                    } else {

                        expandedTextLangs.add(lang)

                    }

                }

                selector.parameters = selector.buildUponParameters()

                    .setPreferredTextLanguages(*expandedTextLangs.toTypedArray())

                    .setSelectUndeterminedTextLanguage(false)

                    .build()

                // En son: kalite profili / elle seçilen çözünürlük tavanı.

                applyTrackLimits(selector)

            }

        }

        this.videoUrl = videoUrl

        this.currentAddonName = addonName

        this.isCsSource = isCS

        this.streamTitle = streamTitle

        // Yeni ortam için otomatik parça seçimini sıfırla

        this.preparedSubtitles = subtitles

        this.initialSelectionDone = false

        this.initialAudioSelectionDone = false

        this.userSelectedAudioTrack = false

        this.userSelectedTextTrack = false

        this.autoSubtitleSelectionSettled = false

        this.autoSubtitleAttemptCount = 0

        runOnMainThread {

            listeners.forEach { it.onChaptersChanged(emptyList()) }

        }



        // ── T1.13: Start diagnostics & analytics session ──────────────────────

        val sessionId = java.util.UUID.randomUUID().toString().take(8)

        diagnostics.startSession(sessionId, videoUrl)

        analytics.startSession(sessionId, videoUrl)



        // ── Ağır Dolby Vision hesaplamalarını IO thread'de yap ───────────────

        // ExoPlayer'a dokunmadan önce IO'da DV policy resolve et — ana thread bloke olmaz

        var resolvedPolicy: DolbyVisionBaseLayerPolicy.Result? = null

        var finalMode: Dv7HandlingMode = Dv7HandlingMode.OFF

        var isConvertToDv81: Boolean = false

        var stripDvRpuEnabled: Boolean = false

        var effectiveExtractorsFactory: ExtractorsFactory = DefaultExtractorsFactory()

        var mediaItem: MediaItem = MediaItem.fromUri(Uri.parse(videoUrl))

        var mediaSourceForMainThread: androidx.media3.exoplayer.source.MediaSource? = null





        withContext(Dispatchers.IO) {

            val player = exoPlayer ?: return@withContext



            // ── Dolby Vision Policy Resolution ──────────────────────────────────

            DoviBridge.resetRuntimeCounters()

            DolbyVisionConversionStats.reset()



            val rPolicy: DolbyVisionBaseLayerPolicy.Result?

            val rMode: Dv7HandlingMode

            when (settings.dv7HandlingMode) {

                Dv7HandlingMode.AUTO -> {

                    val result = DolbyVisionBaseLayerPolicy.resolve(

                        context = context,

                        bridgeReady = DoviBridge.isLibraryLoaded

                    )

                    rPolicy = result

                    rMode = when (result.decision) {

                        DolbyVisionBaseLayerPolicy.Decision.NATIVE_DV7      -> Dv7HandlingMode.OFF

                        DolbyVisionBaseLayerPolicy.Decision.CONVERT_TO_DV81 -> Dv7HandlingMode.DV81_LIBDOVI

                        else                                                 -> Dv7HandlingMode.HDR10_BASE_LAYER

                    }

                    Log.i(TAG, "DV7_AUTO: decision=${result.decision} effectiveMode=$rMode " +

                            "displayDv=${result.displayDv} displayHdr10=${result.displayHdr10} " +

                            "bridgeReady=${result.bridgeReady} url=$videoUrl")

                }

                else -> {

                    rPolicy = null

                    rMode = settings.dv7HandlingMode

                }

            }

            dvPolicy = rPolicy

            effectiveDv7Mode = rMode

            resolvedPolicy = rPolicy



            // ── DV5 sinyal düzeltmesi (NuvioTV paritesi) ─────────────────────────

            // Cihazın DV çözücüsü P5'i DEĞİL yalnızca P8.1'i çözebiliyorsa

            // (dvhe.st var, dvhe.stn yok), profil 5 akışının codec dizesini 8.1'e

            // çevirmek renkleri düzeltir: çözücü akışı kabul eder ve IPTPQc2→RGB

            // dönüşümünü donanımda kendisi yapar. RPU'ya dokunulmaz.

            val dv5SignalOnlyFix = rPolicy?.let { p ->

                p.displayDv && p.codecSupportsDvheSt && !p.codecSupportsDvheStn

            } ?: false

            if (dv5SignalOnlyFix) {

                Log.i(TAG, "DV5_SIGNAL_FIX: device decodes P8.1 but not P5 -> signal rewrite enabled")

            }



            // Inform the vendored Matroska extractor whether HDR10 base-layer mode is active.

            val isHdr10BaseLayerMode = rMode == Dv7HandlingMode.HDR10_BASE_LAYER ||

                    rMode == Dv7HandlingMode.STRIP_DV

            DolbyVisionCompatibility.setHdr10BaseLayerModeActive(isHdr10BaseLayerMode)



            // ── DV7 Probe (only runs when conversion is requested) ───────────────

            val dv7ToDv81Active = rMode == Dv7HandlingMode.DV81_LIBDOVI

            val dv81Probe = if (dv7ToDv81Active) {

                DoviBridge.probeRealtimeConversionSupport(videoUrl)

            } else {

                null

            }



            val fm = if (settings.dv7HandlingMode == Dv7HandlingMode.AUTO &&

                rMode == Dv7HandlingMode.DV81_LIBDOVI &&

                dv81Probe?.supported != true

            ) {

                Log.i(TAG, "DV7_AUTO_FALLBACK: dv81-probe-failed reason=${dv81Probe?.reason} → HDR10_BASE_LAYER")

                Dv7HandlingMode.HDR10_BASE_LAYER

            } else {

                rMode

            }

            effectiveDv7Mode = fm

            finalMode = fm



            isConvertToDv81 = fm == Dv7HandlingMode.DV81_LIBDOVI && dv81Probe?.supported == true

            stripDvRpuEnabled = fm == Dv7HandlingMode.HDR10_BASE_LAYER || fm == Dv7HandlingMode.STRIP_DV

            // Gizli DV çözücü keşfi yalnızca gerçek dönüştürme oturumunda açılır.

            dv81CodecFallbackActive.set(isConvertToDv81)



            Log.i(TAG, "DV7_FINAL: mode=$fm convert=$isConvertToDv81 stripRpu=$stripDvRpuEnabled " +

                    "stripHdr10Plus=${settings.stripHdr10PlusSei} bridgeLoaded=${DoviBridge.isLibraryLoaded}")



            // ── Extractors Factory ───────────────────────────────────────────────

            val baseExtractorsFactory = DefaultExtractorsFactory()

                .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS)

                .setTsExtractorTimestampSearchBytes(1500 * TsExtractor.TS_PACKET_SIZE)



            effectiveExtractorsFactory =

                if (isConvertToDv81 || stripDvRpuEnabled || settings.stripHdr10PlusSei) {

                    DolbyVisionExtractorsFactory(

                        delegate = baseExtractorsFactory,

                        config = DolbyVisionConversionConfig(

                            active = isConvertToDv81,

                            forcedMode = -1,

                            preserveMapping = false,

                            dv5Enabled = false,

                            manualDv81 = fm == Dv7HandlingMode.DV81_LIBDOVI,

                            dv5ToDv81SignalOnly = dv5SignalOnlyFix

                        ),

                        stripDvRpu = stripDvRpuEnabled,

                        stripHdr10PlusSei = settings.stripHdr10PlusSei

                    )

                } else {

                    baseExtractorsFactory

                }



            // ── MediaItem ────────────────────────────────────────────────────────

            val videoUri = Uri.parse(videoUrl)

            val format = com.kitsugi.animelist.core.player.StreamFormatPolicy.resolve(videoUrl, mediaType)

            val adaptiveType = com.kitsugi.animelist.data.cloudstream.CsStreamFormat.choose(
                csStreamType, PlayerMediaSourceFactory.isHlsUrl(videoUrl, streamTypeHint), PlayerMediaSourceFactory.isDashUrl(videoUrl, streamTypeHint)
            )

            val isM3u8 = adaptiveType == "M3U8" || format == com.kitsugi.animelist.core.player.StreamMediaType.HLS || PlayerMediaSourceFactory.isHlsUrl(videoUrl, streamTypeHint)

            val isDash = adaptiveType == "DASH" || format == com.kitsugi.animelist.core.player.StreamMediaType.DASH || PlayerMediaSourceFactory.isDashUrl(videoUrl, streamTypeHint)

            val isSs = videoUrl.contains(".ism", ignoreCase = true)



            val mediaItemBuilder = MediaItem.Builder().setUri(videoUri)

            if (isM3u8) mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)

            else if (isDash) mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)

            else if (isSs) mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_SS)



            if (subtitles.isNotEmpty()) {

                val subConfigs = subtitles.map { sub ->

                    val mime = when {

                        sub.url.startsWith("/") || sub.url.startsWith("file://") -> {

                            val path = sub.url.removePrefix("file://")

                            guessLocalMimeType(path)

                        }

                        sub.url.contains(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT

                        sub.url.contains(".srt", ignoreCase = true) -> MimeTypes.APPLICATION_SUBRIP

                        sub.url.contains(".ass", ignoreCase = true) || sub.url.contains(".ssa", ignoreCase = true) -> MimeTypes.TEXT_SSA

                        else -> MimeTypes.TEXT_VTT

                    }

                    // NOT: "file://$sub.url" yanlış interpolasyondu (sub.toString() + ".url").

                    // Yerel önbellek yolu için doğru şema "file://${sub.url}" olmalı.

                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(if (sub.url.startsWith("/")) "file://${sub.url}" else sub.url))

                        // Harici altyazıyı track kimliğiyle eşleştirebilmek için benzersiz id:

                        // isim benzerliğine (ör. "Türkçe" dahili / "Türkçe (addon)" harici) güvenmiyoruz.

                        .setId(sub.url)

                        .setMimeType(mime)

                        .setLanguage(sub.lang)

                        .setLabel(sub.name)

                        .build()

                }

                mediaItemBuilder.setSubtitleConfigurations(subConfigs)

            }

            mediaItem = mediaItemBuilder.build()



            // ── T1.13: Delegate media source creation to PlayerMediaSourceFactory ──

            // All DataSource / ExtractorFactory / MergingMediaSource logic lives there.

            // ÖNEMLİ: MediaSource yalnızca BİR kez üretilir ve ana thread'de aynen kullanılır.

            // Eskiden burada üretilen kaynak (MergingMediaSource değilse) atılıp main thread'de

            // ikinci kez üretiliyordu. Bu; CS interceptor'ının, OkHttp istemcisinin ve altyazı

            // konfigürasyonunun iki kez kurulmasına, ayrıca hazırlanan MediaSource'un çöp

            // toplanmasına yol açıyordu.

            mediaSourceForMainThread = mediaSourceFactory.create(

                videoUrl          = videoUrl,

                audioUrl          = audioUrl,

                externalAudioTracks = externalAudioTracks,

                streamTypeHint    = streamTypeHint,

                headers           = headers,

                subtitles         = subtitles,

                isCS              = isCS,

                addonName         = addonName,

                streamTitle       = streamTitle,

                qualityValue      = qualityValue,

                extractorsFactory = effectiveExtractorsFactory,

                mediaItemOverride = mediaItem,

                mediaType         = mediaType,

                csStreamType      = csStreamType,

                csExtractorData   = csExtractorData

            )

        }



        // ── ExoPlayer çağrıları MUTLAKA Main thread'de yapılmalı ────────────────

        withContext(Dispatchers.Main) {

            val player = exoPlayer ?: return@withContext

            player.stop()

            player.clearMediaItems()



            // IO bloğunda hazırlanan kaynak aynen kullanılır; yeniden üretilmez.

            val sourceToSet = mediaSourceForMainThread

                ?: mediaSourceFactory.create(

                    videoUrl          = videoUrl,

                    audioUrl          = audioUrl,

                    externalAudioTracks = externalAudioTracks,

                    streamTypeHint    = streamTypeHint,

                    headers           = headers,

                    subtitles         = subtitles,

                    isCS              = isCS,

                    addonName         = addonName,

                    streamTitle       = streamTitle,

                    qualityValue      = qualityValue,

                    extractorsFactory = effectiveExtractorsFactory,

                    mediaItemOverride = mediaItem,

                    csStreamType      = csStreamType,

                    csExtractorData   = csExtractorData

                )

            player.setMediaSource(sourceToSet)



            // Yeni medya: önceki dosyanın "ilk kare çizildi" bilgisi taşınmamalı.

            renderedFirstVideoFrame = false

            player.playbackParameters = androidx.media3.common.PlaybackParameters(currentPlaybackSpeed)

            player.playWhenReady = true

            player.prepare()

            if (startPositionMs > 0L) {

                player.seekTo(startPositionMs)

            }

            diagnostics.onBufferingStarted("initial-load")

        }

    }



    override fun play() {

        runOnMainThread {

            exoPlayer?.play()

        }

    }



    override fun pause() {

        runOnMainThread {

            exoPlayer?.pause()

        }

    }



    /**

     * Medya tuşları köprüsü (kulaklık düğmesi, Bluetooth oynat/duraklat, TV kumandası).

     *

     * ExoPlayer varsayılanda pencere seviyesindeki medya tuşlarını dinlemez.

     * Klavye/kumanda yöneticisi [PlayerKeyboardController] bu olayları motora iletir.

     * Yalnızca motor seviyesinde güvenle yapılabilecek evrensel medya eylemleri

     * uygulanır: oynat/duraklat, durdur ve ±10 sn. Diğer tuşlar sahiplenilmez.

     */

    override fun handleKey(event: android.view.KeyEvent): Boolean {

        if (android.view.KeyEvent.isModifierKey(event.keyCode)) return false

        if (event.action != android.view.KeyEvent.ACTION_DOWN) return false

        val player = exoPlayer ?: return false

        return when (event.keyCode) {

            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,

            android.view.KeyEvent.KEYCODE_HEADSETHOOK,

            android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {

                if (player.isPlaying) player.pause() else player.play()

                true

            }

            android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> { player.pause(); true }

            android.view.KeyEvent.KEYCODE_MEDIA_STOP -> { player.pause(); true }

            android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {

                // `player.duration` canlı akışta C.TIME_UNSET (-1) döner; o durumda üst

                // sınır uygulanmaz, aksi hâlde tüm atlamalar 0'a kırpılırdı.

                val target = player.currentPosition + 10_000L

                val total = player.duration

                player.seekTo(if (total > 0L) target.coerceAtMost(total) else target)

                true

            }

            android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> {

                player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))

                true

            }

            else -> false

        }

    }



    override fun seekTo(positionMs: Long) {

        runOnMainThread {

            exoPlayer?.seekTo(positionMs)

        }

    }



    override fun setPlaybackSpeed(speed: Float) {

        val safeSpeed = speed.coerceIn(0.25f, 4.0f)

        currentPlaybackSpeed = safeSpeed

        runOnMainThread {

            exoPlayer?.playbackParameters = androidx.media3.common.PlaybackParameters(

                safeSpeed, if (settings.audioPitchCorrection) 1f else safeSpeed

            )

        }

    }



    override fun setVolume(volume: Float) {

        runOnMainThread {

            val player = exoPlayer ?: return@runOnMainThread

            if (volume > 1.0f) {

                player.volume = 1.0f

                val boost = volume - 1.0f

                gainAudioProcessor.setGainDb(boost * 6f)

            } else {

                player.volume = volume

                gainAudioProcessor.setGainDb(0f)

            }

        }

    }



    override fun setSubtitleDelay(delayMs: Long) {

        subtitleDelayMs = delayMs

        // Media3 standard Player interface does not support generic dynamic subtitle offset shift natively.

        Log.w(TAG, "Subtitle delay of $delayMs ms requested but not supported natively in Media3 wrapper.")

    }



    override fun setAudioDelay(delayMs: Long) {

        // Negatif değer (sesi öne alma) Media3 PCM hattında desteklenmez; 0'a sabitlenir.

        // UI bu durumda MPV motoruna geçiş önerir (supportsNegativeAudioDelay = false).

        val applied = delayMs.coerceAtLeast(0L)

        audioDelayMs = applied

        audioDelayProcessor.setDelayMs(applied)

        Log.d(TAG, "Audio delay applied: requested=$delayMs ms, effective=$applied ms")

    }



    override fun setSubtitleStyle(style: SubtitleStyleSettings) {

        currentSubtitleStyle = style

        runOnMainThread { applySubtitleStylesToView() }

    }



    override fun setMaxVideoHeight(height: Int?) {

        val sanitized = height?.takeIf { it in 144..4320 }

        if (sanitized == maxVideoHeight) return

        maxVideoHeight = sanitized

        runOnMainThread { applyTrackLimits(trackSelector) }

    }



    /**

     * Etkin çözünürlük/bit hızı tavanını track selector'a yazar.

     *

     * Öncelik: kullanıcının elle seçtiği kalite ([maxVideoHeight]) → kalite profili

     * ([profileMaxHeight]) → sınırsız. Böylece ayarlardaki "720p tercih et" / "Veri

     * tasarrufu" gerçekten uygulanır (eskiden yalnızca kaydediliyordu).

     */

    private fun applyTrackLimits(selector: androidx.media3.exoplayer.trackselection.DefaultTrackSelector?) {

        val target = selector ?: trackSelector ?: return

        val height = maxVideoHeight ?: profileMaxHeight

        val bitrate = (profileMaxBitrateKbps?.times(1000))?.takeIf { it > 0 }

        runCatching {

            target.parameters = target.buildUponParameters()

                .setMaxVideoSize(Int.MAX_VALUE, height ?: Int.MAX_VALUE)

                .setMaxVideoBitrate(bitrate ?: Int.MAX_VALUE)

                .build()

        }.onFailure { Log.w(TAG, "applyTrackLimits failed: ${it.message}") }

    }



    /** Verilen JSON'dan kalite profilini uygular (AUTO → tavan yok). */

    private fun applyProfileJson(json: String?) {

        val profile = com.kitsugi.animelist.core.player.QualityProfile.deserialize(json)

        profileMaxHeight = profile.effectiveMaxVideoHeight

        profileMaxBitrateKbps = profile.effectiveMaxBitrateKbps

    }



    /**

     * Ayarlardaki kalite profilini **taze** okur.

     *

     * Motor `settings` anlık görüntüsüyle kurulduğu için, kullanıcı oynatıcı açıkken

     * ayarlardan "720p / Veri tasarrufu" seçerse eski değer kalırdı. Bu yüzden her

     * `prepare()` çağrısında DataStore'dan kısa bir tavanla okunur; okunamazsa motorun

     * anlık görüntüsü kullanılır.

     */

    private suspend fun refreshProfileLimits() {

        val fresh = runCatching {

            kotlinx.coroutines.withTimeoutOrNull(400L) {

                withContext(Dispatchers.IO) {

                    val store = com.kitsugi.animelist.data.settings.SettingsDataStore(context)

                    store.settingsFlow.first()

                }

            }?.qualityProfileJson

        }.getOrNull()

        applyProfileJson(fresh ?: settings.qualityProfileJson)

    }



    override fun setResizeMode(resizeMode: Int) {

        currentResizeMode = resizeMode

        runOnMainThread { applyAspectMode() }

    }



    override fun setAspectMode(mode: com.kitsugi.animelist.core.player.PlayerAspectMode) {

        currentAspectMode = mode

        currentResizeMode = com.kitsugi.animelist.core.player.PlayerAspectScaleUtils.getMedia3ResizeMode(mode)

        runOnMainThread { applyAspectMode() }

    }



    private fun applyAspectMode() {

        runOnMainThread {

            val pv = playerView ?: return@runOnMainThread

            val contentFrame = pv.findViewById<androidx.media3.ui.AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame) ?: return@runOnMainThread

            val overrideRatio = com.kitsugi.animelist.core.player.PlayerAspectScaleUtils.getMedia3AspectRatioOverride(currentAspectMode)

            if (overrideRatio > 0f) {

                contentFrame.setAspectRatio(overrideRatio)

            } else {

                val w = videoWidth ?: 0

                val h = videoHeight ?: 0

                if (w > 0 && h > 0) {

                    contentFrame.setAspectRatio(w.toFloat() / h)

                }

            }

            pv.resizeMode = currentResizeMode

            // Görüntü modu değiştiğinde altyazının içerik çerçevesine geri dönmediğinden

            // emin ol (Media3 bazı sürümlerde alt yazı görünümünü yeniden ebeveynler) ve

            // konumunu pencereye göre tazele. Kullanıcı raporu (2026-10-21): "Doldur"

            // seçilince harici altyazı video ile birlikte büyüyüp ekran altına taşıyordu.

            detachSubtitleViewFromContentFrame(pv)

            applySubtitleStylesToView()

        }

    }



    /**

     * Cihazın çözemediği (codec/donanım desteği olmayan) parçaları etikette işaretler.

     *

     * Kullanıcı raporu (2026-10-12): "3 ses seçeneği hiçbir şey değiştirmiyor, hep aynı

     * İngilizce ses oynuyor." Desteklenmeyen bir parça seçildiğinde ExoPlayer seçimi

     * uygulamaz ve sessizce eski parça çalmaya devam eder; sebep arayüzde görünmüyordu.

     */

    private fun trackLabelWithSupport(baseLabel: String, supported: Boolean): String =

        if (supported) baseLabel else "$baseLabel (cihaz desteklemiyor)"



    override val videoQualityTracks: List<TrackOption>

        get() = exoPlayer?.currentTracks?.groups?.flatMapIndexed { groupIndex, group ->

            if (group.type != C.TRACK_TYPE_VIDEO) emptyList() else {

                (0 until group.length).mapNotNull { index ->

                    val format = group.getTrackFormat(index)

                    if (!group.isTrackSupported(index) || format.height <= 0) null else {

                        val bitrate = format.bitrate.takeIf { it > 0 }?.let { " · ${it / 1000} kbps" }.orEmpty()

                        TrackOption(group, groupIndex, index, "${format.height}p$bitrate", group.isTrackSelected(index))

                    }

                }

            }

        }.orEmpty().sortedByDescending { it.group.getTrackFormat(it.trackIndex).height }



    override val isVideoQualityAuto: Boolean

        get() = exoPlayer?.trackSelectionParameters?.overrides?.values

            ?.none { it.mediaTrackGroup.type == C.TRACK_TYPE_VIDEO } ?: true



    override fun selectVideoQuality(track: TrackOption?) {

        runOnMainThread {

            val player = exoPlayer ?: return@runOnMainThread

            // Ignore stale rows after an episode/source/engine switch.

            if (track != null && (track.group.type != C.TRACK_TYPE_VIDEO ||

                    track.trackIndex !in 0 until track.group.length ||

                    player.currentTracks.groups.none { it.mediaTrackGroup == track.group.mediaTrackGroup } ||

                    !track.group.isTrackSupported(track.trackIndex))) return@runOnMainThread

            val parameters = player.trackSelectionParameters.buildUpon()

                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)

                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)

            if (track != null) parameters.setOverrideForType(

                TrackSelectionOverride(track.group.mediaTrackGroup, track.trackIndex)

            )

            player.trackSelectionParameters = parameters.build()

        }

    }



    override fun selectTrack(trackOption: TrackOption) {

        runOnMainThread {

            val player = exoPlayer ?: return@runOnMainThread

            if (!trackOption.group.isTrackSupported(trackOption.trackIndex)) {

                Log.w(

                    TAG,

                    "Seçilen parça cihaz tarafından desteklenmiyor: '${trackOption.label}' — " +

                        "ses/altyazı değişmeyebilir (donanım/codec kısıtı)."

                )

                CsTrace.warn(

                    "Player",

                    "track",

                    "Desteklenmeyen parça seçildi: ${trackOption.label} (cihaz çözemiyor — seçim uygulanmayabilir)"

                )

            }

            val isSubtitle = trackOption.group.type == C.TRACK_TYPE_TEXT

            val type = if (isSubtitle) C.TRACK_TYPE_TEXT else C.TRACK_TYPE_AUDIO

            if (type == C.TRACK_TYPE_AUDIO) {

                userSelectedAudioTrack = true

            } else {

                // Kullanıcı altyazıyı elle seçti → otomatik politika bir daha karışmasın.

                userSelectedTextTrack = true

                autoSubtitleSelectionSettled = true

            }

            applyTrackOverride(player, type, trackOption)



            // ── Uygulandı mı doğrulaması ─────────────────────────────────────────

            // Kullanıcı raporu (2026-10-12): "3 ses seçeneği hiçbir şey değiştirmiyor."

            // ExoPlayer, çok gruplu/HLS akışlarında override'ı ilk turda uygulamayabiliyor;

            // bu yüzden kısa bir gecikmeyle gerçekten seçili mi diye bakılır ve değilse

            // zorlamalı yol (disable→enable + override) denenir.

            val expectedGroup = trackOption.group.mediaTrackGroup

            val expectedIndex = trackOption.trackIndex

            val label = trackOption.label

            mainHandler.postDelayed({

                val p = exoPlayer ?: return@postDelayed

                if (isTrackOverrideApplied(p.currentTracks, expectedGroup, expectedIndex)) {

                    Log.i(TAG, "Parça seçimi uygulandı: $label (type=$type)")

                    return@postDelayed

                }

                Log.w(TAG, "Parça seçimi ilk turda uygulanmadı ($label) — zorlamalı yeniden uygulanıyor.")

                runCatching {

                    p.trackSelectionParameters = p.trackSelectionParameters

                        .buildUpon()

                        .setTrackTypeDisabled(type, true)

                        .build()

                    p.trackSelectionParameters = p.trackSelectionParameters

                        .buildUpon()

                        .setTrackTypeDisabled(type, false)

                        .setOverrideForType(TrackSelectionOverride(expectedGroup, expectedIndex))

                        .build()

                }.onFailure { Log.e(TAG, "Zorlamalı parça seçimi başarısız: ${it.message}") }

            }, TRACK_SELECTION_VERIFY_DELAY_MS)

        }

    }



    /** Override'ı uygular (varsa o tür için eski override'lar yerine geçer). */

    private fun applyTrackOverride(player: ExoPlayer, type: Int, trackOption: TrackOption) {

        player.trackSelectionParameters = player.trackSelectionParameters

            .buildUpon()

            .setTrackTypeDisabled(type, false)

            .setOverrideForType(

                TrackSelectionOverride(

                    trackOption.group.mediaTrackGroup,

                    trackOption.trackIndex

                )

            )

            .build()

    }



    /** Verilen grup/indeks gerçekten seçili mi? */

    private fun isTrackOverrideApplied(

        tracks: Tracks?,

        group: androidx.media3.common.TrackGroup,

        trackIndex: Int

    ): Boolean {

        if (tracks == null) return false

        for (tracksGroup in tracks.groups) {

            if (tracksGroup.mediaTrackGroup != group) continue

            return trackIndex in 0 until tracksGroup.length && tracksGroup.isTrackSelected(trackIndex)

        }

        return false

    }



    override fun disableSubtitles() {

        runOnMainThread {

            val player = exoPlayer ?: return@runOnMainThread

            player.trackSelectionParameters = player.trackSelectionParameters

                .buildUpon()

                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)

                .build()

        }

    }



    /**

     * Capture a frame from either supported Media3 surface. The fullscreen player uses a

     * SurfaceView so Android can preserve HDR/Dolby Vision output; PixelCopy is asynchronous

     * and avoids blocking the UI thread. TextureView remains supported for embedded players.

     */

    override suspend fun takeScreenshot(cachePath: String, showSubtitles: Boolean): java.io.InputStream? {

        var output: android.graphics.Bitmap? = null

        try {

            // Surface access / subtitle drawing happen on the UI thread; PixelCopy itself is asynchronous.

            output = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {

                captureScreenshotFrame(showSubtitles)

            }

            val frame = output ?: return null

            return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {

                java.io.ByteArrayOutputStream().use { bytes ->

                    if (frame.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bytes)) {

                        java.io.ByteArrayInputStream(bytes.toByteArray())

                    } else null

                }

            }

        } finally {

            // Also releases a frame captured immediately before coroutine cancellation.

            output?.recycle()

        }

    }



    private suspend fun captureScreenshotFrame(showSubtitles: Boolean): android.graphics.Bitmap? {

        val pv = playerView ?: return null

        val surface = pv.videoSurfaceView ?: return null

        val frame = when (surface) {

            is android.view.TextureView -> runCatching { surface.bitmap }.getOrNull()

            is android.view.SurfaceView -> captureSurfaceViewFrame(surface)

            else -> null

        } ?: return null

        val output = frame.copy(android.graphics.Bitmap.Config.ARGB_8888, true) ?: frame

        if (output !== frame) frame.recycle()

        if (showSubtitles) {

            runCatching { drawSubtitleOverlays(pv, surface, output) }

                .onFailure { Log.w(TAG, "Subtitle overlay could not be drawn: ${it.message}") }

        }

        return output

    }



    private suspend fun captureSurfaceViewFrame(

        surfaceView: android.view.SurfaceView

    ): android.graphics.Bitmap? = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->

        val width = surfaceView.width

        val height = surfaceView.height

        if (width <= 0 || height <= 0 || !surfaceView.holder.surface.isValid) {

            continuation.resumeWith(Result.success(null))

            return@suspendCancellableCoroutine

        }

        val bitmap = runCatching {

            android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)

        }.getOrNull()

        if (bitmap == null) {

            continuation.resumeWith(Result.success(null))

            return@suspendCancellableCoroutine

        }

        continuation.invokeOnCancellation { bitmap.recycle() }

        runCatching {

            android.view.PixelCopy.request(surfaceView, bitmap, { result ->

                if (!continuation.isActive) {

                    if (!bitmap.isRecycled) bitmap.recycle()

                } else if (result == android.view.PixelCopy.SUCCESS) {

                    continuation.resumeWith(Result.success(bitmap))

                } else {

                    bitmap.recycle()

                    continuation.resumeWith(Result.success(null))

                }

            }, mainHandler)

        }.onFailure {

            bitmap.recycle()

            if (continuation.isActive) continuation.resumeWith(Result.success(null))

            Log.w(TAG, "PixelCopy failed to start: ${it.message}")

        }

    }



    private fun drawSubtitleOverlays(

        pv: PlayerView,

        videoSurface: View,

        target: android.graphics.Bitmap

    ) {

        if (videoSurface.width <= 0 || videoSurface.height <= 0) return

        val canvas = android.graphics.Canvas(target)

        val surfaceLocation = IntArray(2)

        videoSurface.getLocationInWindow(surfaceLocation)

        val scaleX = target.width.toFloat() / videoSurface.width.toFloat()

        val scaleY = target.height.toFloat() / videoSurface.height.toFloat()



        listOfNotNull(pv.subtitleView, assSubtitleView).forEach { overlay ->

            if (overlay.visibility != View.VISIBLE || overlay.width <= 0 || overlay.height <= 0) {

                return@forEach

            }

            val location = IntArray(2)

            overlay.getLocationInWindow(location)

            canvas.save()

            canvas.scale(scaleX, scaleY)

            canvas.translate(

                (location[0] - surfaceLocation[0]).toFloat(),

                (location[1] - surfaceLocation[1]).toFloat()

            )

            overlay.draw(canvas)

            canvas.restore()

        }

    }



    override fun createVideoView(context: Context): View {

        if (playerView == null) {

            val root = LayoutInflater.from(context).inflate(R.layout.fullscreen_player_view, null)

            val pv = root.findViewById<PlayerView>(R.id.trailer_player_view)

            pv.player = exoPlayer

            playerView = pv

            detachSubtitleViewFromContentFrame(pv)

            applySubtitleStylesToView()

            applyAspectMode()



            if (settings.enableAssExtractor) {

                val assHandler = exoPlayer?.getAssHandlerCompat()

                if (assHandler != null) {

                    val assView = io.github.peerless2012.ass.media.widget.AssSubtitleView(context, assHandler)

                    assSubtitleView = assView

                    pv.addView(

                        assView,

                        android.widget.FrameLayout.LayoutParams(

                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,

                            android.view.ViewGroup.LayoutParams.MATCH_PARENT

                        )

                    )

                }

            }

        }

        return playerView!!

    }



    /**

     * Altyazı görünümünü, videoyu taşıyan `exo_content_frame` (AspectRatioFrameLayout)

     * içinden çıkarıp PlayerView kök katmanına taşır.

     *

     * NEDEN (2026-10-12 kullanıcı raporu): Media3, `exo_subtitles` görünümünü içerik

     * çerçevesinin İÇİNE koyar. Kullanıcı görüntü modunu değiştirdiğinde (Doldur / Yakınlaştır

     * / 16:9 / 4:3) içerik çerçevesinin en-boy oranı ve ölçüsü değişiyor; dolayısıyla altyazı

     * alanı da yeniden ölçekleniyor ve altyazı ekranda kayıyor, geriliyor/kısaltılıyordu.

     * Altyazıyı kök katmana taşıdığımızda konum ve genişlik yalnızca ekran ölçüsüne bağlı

     * kalır: altyazı videodan bağımsız olarak alt tarafta sabit durur.

     */

    private fun detachSubtitleViewFromContentFrame(pv: PlayerView) {

        runOnMainThread {

            try {

                val subtitleView = pv.subtitleView ?: return@runOnMainThread

                val parent = subtitleView.parent as? android.view.ViewGroup ?: return@runOnMainThread

                if (parent === pv) return@runOnMainThread // zaten kök katmanda

                parent.removeView(subtitleView)

                pv.addView(

                    subtitleView,

                    android.widget.FrameLayout.LayoutParams(

                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,

                        android.view.ViewGroup.LayoutParams.MATCH_PARENT

                    )

                )

                subtitleView.setBottomPaddingFraction(SUBTITLE_BOTTOM_PADDING_FRACTION)

                Log.i(TAG, "Altyazı görünümü içerik çerçevesinden ayrıldı — görüntü modu artık altyazıyı etkilemez.")

            } catch (t: Throwable) {

                // Görsel/işlevsel bir zorunluluk değil; başarısız olursa eski davranış sürer.

                Log.w(TAG, "Altyazı görünümü ayrılamadı (eski davranış geçerli): ${t.message}")

            }

        }

    }



    private fun applySubtitleStylesToView() {

        runOnMainThread {

            val pv = playerView ?: return@runOnMainThread

            pv.subtitleView?.let { subtitleView ->

                subtitleView.setApplyEmbeddedStyles(false)

                subtitleView.setApplyEmbeddedFontSizes(false)

                

                val edgeType = if (currentSubtitleStyle.outlineEnabled) {

                    when (currentSubtitleStyle.borderStyle) {

                        "opaque-box" -> CaptionStyleCompat.EDGE_TYPE_OUTLINE

                        "background-box" -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW

                        else -> CaptionStyleCompat.EDGE_TYPE_OUTLINE

                    }

                } else {

                    CaptionStyleCompat.EDGE_TYPE_NONE

                }

                // Font family + Bold + Italic kombinasyonu (Aniyomi: sub-font)

                val baseFamily = when (currentSubtitleStyle.font.lowercase().trim()) {

                    "serif" -> android.graphics.Typeface.SERIF

                    "monospace", "mono" -> android.graphics.Typeface.MONOSPACE

                    else -> try { android.graphics.Typeface.create(currentSubtitleStyle.font, android.graphics.Typeface.NORMAL) } catch (_: Exception) { android.graphics.Typeface.DEFAULT }

                }

                val styleFlags = when {

                    currentSubtitleStyle.bold && currentSubtitleStyle.italic -> android.graphics.Typeface.BOLD_ITALIC

                    currentSubtitleStyle.bold -> android.graphics.Typeface.BOLD

                    currentSubtitleStyle.italic -> android.graphics.Typeface.ITALIC

                    else -> android.graphics.Typeface.NORMAL

                }

                val typeface = try { android.graphics.Typeface.create(baseFamily, styleFlags) } catch (_: Exception) { baseFamily }

                // Arka plan ve kenar renkleri artik style'dan geliyor (Aniyomi paritesi)

                val windowColor = currentSubtitleStyle.backgroundColor.takeIf { it != 0 } ?: android.graphics.Color.TRANSPARENT

                val captionStyle = CaptionStyleCompat(

                    currentSubtitleStyle.textColor,

                    windowColor,

                    android.graphics.Color.TRANSPARENT,

                    edgeType,

                    currentSubtitleStyle.borderColor.takeIf { it != 0 } ?: android.graphics.Color.BLACK,

                    typeface

                )

                subtitleView.setStyle(captionStyle)

                // fontSize (1..100, default 45) + fontScale (0.2..5) + size fallback

                val baseFontSize = currentSubtitleStyle.fontSize.takeIf { it in 1..200 } ?: 45

                val effectiveScale = currentSubtitleStyle.fontScale.takeIf { it in 0.1f..5f } ?: 1f

                // fontSize 55 ~ 16sp esdeger; 55 = 16sp taban alinir

                val baseSp = (baseFontSize / 55f * 16f).coerceIn(6f, 48f)

                subtitleView.setFixedTextSize(

                    TypedValue.COMPLEX_UNIT_SP,

                    (baseSp * effectiveScale).coerceIn(6f, 64f)

                )

                // sub-pos (0..150): 100 = varsayilan alt konum, 0 = en ust

                // pos degerini bottomPaddingFraction'a cevir: pos 100 -> 0.08, pos 50 -> 0.45, pos 0 -> ~0.85

                val posFraction = if (currentSubtitleStyle.pos in 0..150) {

                    ((150 - currentSubtitleStyle.pos) / 150f * 0.78f + 0.08f).coerceIn(0.02f, 0.92f)

                } else SUBTITLE_BOTTOM_PADDING_FRACTION

                subtitleView.setBottomPaddingFraction(posFraction)

                // shadowOffset >0 ise edgeType zaten OUTLINE, ek gölge gerekmez (Media3 sinirli)

                // verticalOffset: kullanici dikey ince ayari (-200..200)

                val density = context.resources.displayMetrics.density

                val requested = -currentSubtitleStyle.verticalOffset.toFloat() * density

                val subHeight = subtitleView.height.takeIf { it > 0 }

                    ?: (pv.height.takeIf { it > 0 } ?: 0)

                val marginPx = 4f * density

                val translation = if (subHeight > 0) {

                    requested.coerceIn(-(subHeight - marginPx), subHeight - marginPx)

                } else {

                    requested

                }

                subtitleView.translationY = translation

                assSubtitleView?.translationY = translation

            }

        }

    }



    private fun guessLocalMimeType(urlPath: String): String {

        val file = java.io.File(urlPath)

        if (file.exists() && file.isFile) {

            try {

                file.bufferedReader().use { reader ->

                    val lines = mutableListOf<String>()

                    for (i in 0 until 10) {

                        val line = reader.readLine() ?: break

                        lines.add(line.trim())

                    }

                    val content = lines.joinToString("\n")

                    if (content.contains("[Script Info]") || content.contains("[V4+ Styles]")) {

                        return MimeTypes.TEXT_SSA

                    }

                    if (content.contains("WEBVTT")) {

                        return MimeTypes.TEXT_VTT

                    }

                    if (content.contains("-->")) {

                        return MimeTypes.APPLICATION_SUBRIP

                    }

                }

            } catch (e: Exception) {

                Log.e(TAG, "Error guessing mime type from file content: $urlPath", e)

            }

        }

        // Fallback to extension or default

        return when {

            urlPath.endsWith(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT

            urlPath.endsWith(".srt", ignoreCase = true) -> MimeTypes.APPLICATION_SUBRIP

            urlPath.endsWith(".ass", ignoreCase = true) || urlPath.endsWith(".ssa", ignoreCase = true) -> MimeTypes.TEXT_SSA

            else -> MimeTypes.TEXT_VTT

        }

    }



    override fun release() {

        released = true

        renderedFirstVideoFrame = false

        stopPositionTicker()

        runOnMainThread {

            exoPlayer?.let { player ->

                // ASS handler kaydı statik map'te tutuluyor; motor kapanırken bırakılmazsa

                // bayat handler kaydı kalır.

                runCatching { player.clearAssHandlerCompat() }

                player.removeListener(playerListener)

                player.stop()

                player.clearMediaItems()

                player.release()

            }

            exoPlayer = null

            playerView?.player = null

            playerView = null

            listeners.clear()

        }

    }

}

