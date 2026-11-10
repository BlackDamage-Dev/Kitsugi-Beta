package com.kitsugi.animelist.ui.screens.manga.components

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.kitsugi.animelist.data.ambient.AmbientAudioContext
import com.kitsugi.animelist.data.ambient.AmbientAudioDownloadManager
import com.kitsugi.animelist.data.ambient.AmbientAudioDownloadStatus
import com.kitsugi.animelist.data.ambient.AmbientAudioMode
import com.kitsugi.animelist.data.ambient.AmbientAudioPlanner
import com.kitsugi.animelist.data.ambient.AmbientAudioPreferences
import com.kitsugi.animelist.data.ambient.AmbientAudioRepository
import com.kitsugi.animelist.data.ambient.AmbientAudioSource
import com.kitsugi.animelist.data.ambient.AmbientAudioTrack
import com.kitsugi.animelist.data.ambient.AmbientQueueRules
import com.kitsugi.animelist.data.ambient.deduplicationKey
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.data.manga.MangaDetails
import com.kitsugi.animelist.ui.screens.manga.MangaAmbientAudioPlayback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 🌐 Çevrimiçi ortam sesi denetleyicisi — **eski (sekmeli) panel düzeni** için.
 *
 * v2.4.471'de okuyucunun müzik düğmesi sekmesiz, tek ekranlı yeni bir
 * sayfaya bağlanmıştı; kullanıcı bunun **eski sekmeli panelin yerini
 * almaması** gerektiğini bildirdi. Bu sınıf, yeni çok kaynaklı motoru
 * (AmbientAudioRepository + Media3 kuyruğu) eski panelin "Çevrimiçi" ve
 * "Ayarlar" sekmelerine taşır:
 *
 *  - Manga adıyla arama → en alakalı sonuç → **sırayla** otomatik çalma
 *  - 8 açık lisans kaynağı, tek tek açılıp kapatılabilir
 *  - İndirme (çevrimdışı) ve kuyruğa ekleme
 *  - Çevrimiçi akış oynatımı ve "açılışta otomatik çal" anahtarları
 *
 * Durum tutucu bilinçli olarak Compose'a bağımlı değildir; yalnızca
 * `mutableStateOf` alanları taşır ve `remember*` yardımcısıyla oluşturulur.
 */
class MangaAmbientOnlineController internal constructor(
    private val context: Context,
    val playback: MangaAmbientAudioPlayback,
    private val scope: CoroutineScope,
    private val manga: MangaDetails,
    chapter: MangaChapter?,
    jamendoClientId: String,
) {
    private val repository = AmbientAudioRepository(context)
    private val preferences = AmbientAudioPreferences(context)

    private var ambientContext: AmbientAudioContext = ambientContextOf(manga, chapter)
    private var jamendoClientId: String = jamendoClientId.trim()

    // ── Arama durumu ──────────────────────────────────────────────────────
    private var _mode by mutableStateOf(AmbientAudioMode.AUTO)
    val mode: AmbientAudioMode get() = _mode
    var plan by mutableStateOf(AmbientAudioPlanner.createPlan(ambientContext, AmbientAudioMode.AUTO))
        private set
    var query by mutableStateOf(plan.titleQuery.take(MAX_QUERY_LENGTH))
        private set
    var manualQuery by mutableStateOf(false)
        private set
    var tracks by mutableStateOf<List<AmbientAudioTrack>>(emptyList())
        private set
    var warnings by mutableStateOf<List<String>>(emptyList())
        private set
    var isSearching by mutableStateOf(false)
        private set
    var usedCache by mutableStateOf(false)
        private set
    var titleMatchCount by mutableIntStateOf(0)
        private set
    var contextMatchCount by mutableIntStateOf(0)
        private set

    // ── Kaynak / oynatma tercihleri ───────────────────────────────────────
    var enabledSources by mutableStateOf(preferences.enabledSources())
        private set
    private var _onlinePlaybackEnabled by mutableStateOf(preferences.onlinePlaybackEnabled)
    val onlinePlaybackEnabled: Boolean get() = _onlinePlaybackEnabled
    private var _autoPlayOnline by mutableStateOf(preferences.autoPlayOnline)
    val autoPlayOnline: Boolean get() = _autoPlayOnline

    /** "Bir daha önerme" listesi (kalıcı). */
    var blockedIds by mutableStateOf(preferences.blockedTrackIds())
        private set

    /** Kuyruk karıştırma. */
    var shuffleEnabled by mutableStateOf(preferences.shuffleEnabled)
        private set

    /**
     * Tekrar modu: 0 kapalı · 1 tek parça · 2 liste.
     * Oynatıcı ile aynı değerler kullanılır (bkz. [AmbientQueueRules]).
     */
    private var _repeatMode by mutableIntStateOf(preferences.repeatMode)
    val repeatMode: Int get() = _repeatMode

    /**
     * Sıralı devam: açıkken seçilen parçadan sonra da kuyruk, arama mantığıyla
     * sıradaki benzer parçalarla kendiliğinden sürer.
     */
    private var _continuousPlay by mutableStateOf(preferences.continuousPlay)
    val continuousPlay: Boolean get() = _continuousPlay

    /**
     * Kullanıcı başka bir parça seçince yerini aldığı otomatik kuyruk ve o
     * anda çalan dizin. "Sıraya dön" bu kaydı kullanır.
     */
    private var resumeSnapshot by mutableStateOf<Pair<List<AmbientAudioTrack>, Int>?>(null)
    val canResumeAutoQueue: Boolean get() = resumeSnapshot != null

    /** Aynı anda tek yenileme isteği; boş sonuçtan sonra kısa bekleme. */
    private var refilling = false
    private var lastEmptyRefillAt = 0L

    init {
        playback.setShuffle(shuffleEnabled)
        playback.setRepeatMode(_repeatMode)
        // Kuyruk bitmeden yeni aday ekleme (sıralı devam) oynatıcıdan tetiklenir.
        playback.onNearQueueEnd = { requestMoreIfNeeded() }
    }

    // ── Tekrar ve sıralı devam ────────────────────────────────────────────
    /** Tekrar düğmesi: kapalı → tek parça → liste → kapalı. */
    fun cycleRepeatMode() = setRepeatMode(AmbientQueueRules.nextRepeatMode(_repeatMode))

    fun setRepeatMode(mode: Int) {
        _repeatMode = mode.coerceIn(AmbientQueueRules.REPEAT_OFF, AmbientQueueRules.REPEAT_ALL)
        preferences.repeatMode = _repeatMode
        playback.setRepeatMode(_repeatMode)
    }

    fun setContinuousPlay(enabled: Boolean) {
        _continuousPlay = enabled
        preferences.continuousPlay = enabled
    }

    /**
     * Kullanıcı bir parça seçer.
     *
     * "Sıralı devam" açıksa: seçilen parçadan başlayıp listedeki benzer parçalarla
     * devam eden bir kuyruk kurulur ve kuyruk bitmeden yenisi eklenir. Kapalıysa
     * yalnızca o parça çalar. Yerine geçilen otomatik kuyruk "Sıraya dön" ile
     * geri alınabilir.
     */
    fun playTrack(track: AmbientAudioTrack) {
        val queue = if (_continuousPlay && onlinePlaybackEnabled) {
            AmbientQueueRules.buildQueueFrom(tracks, track.id, blockedIds)
        } else {
            emptyList()
        }
        if (queue.size > 1) playQueue(queue, startIndex = 0) else playQueue(listOf(track))
    }

    /** Yerine geçilen otomatik kuyruğa kaldığı parçadan geri döner. */
    fun resumeAutoQueue() {
        val (queue, index) = resumeSnapshot ?: return
        if (queue.isEmpty()) return
        playQueue(queue, startIndex = index.coerceIn(0, queue.lastIndex))
    }

    /** Sıralı devam: kuyruk bitmeden yeni aday ekler; önce yerel, sonra yeni arama. */
    private fun requestMoreIfNeeded() {
        val remaining = playback.queueLength - playback.queuePosition
        if (!AmbientQueueRules.shouldRequestMore(remaining, _repeatMode, _continuousPlay, onlinePlaybackEnabled)) return
        if (refilling) return
        scope.launch {
            val local = AmbientQueueRules.continuationCandidates(
                pool = tracks,
                queuedIds = playback.queuedTrackIds,
                blockedIds = blockedIds,
            )
            if (local.isNotEmpty()) {
                local.forEach { addToQueue(it) }
                return@launch
            }
            if (enabledSources.isEmpty() || query.isBlank()) return@launch
            if (System.currentTimeMillis() - lastEmptyRefillAt < REFILL_COOLDOWN_MS) return@launch
            refilling = true
            try {
                val result = repository.search(query, enabledSources, plan, jamendoClientId)
                val fresh = AmbientQueueRules.continuationCandidates(
                    pool = result.tracks,
                    queuedIds = playback.queuedTrackIds,
                    blockedIds = blockedIds,
                )
                if (fresh.isEmpty()) {
                    lastEmptyRefillAt = System.currentTimeMillis()
                    return@launch
                }
                tracks = (tracks + fresh).distinctBy { it.deduplicationKey() }
                fresh.forEach { addToQueue(it) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                lastEmptyRefillAt = System.currentTimeMillis()
            } finally {
                refilling = false
            }
        }
    }

    fun setShuffle(enabled: Boolean) {
        shuffleEnabled = enabled
        preferences.shuffleEnabled = enabled
        playback.setShuffle(enabled)
    }

    /**
     * Parçayı "bir daha önerme" listesine ekler: listeden kaldırır ve
     * çalıyorsa sıradakine geçer.
     */
    fun blockTrack(track: AmbientAudioTrack) {
        preferences.blockTrack(track.id)
        blockedIds = preferences.blockedTrackIds()
        tracks = tracks.filterNot { it.id == track.id }
        if (playback.currentTrack?.id == track.id) playback.skipToNext()
    }

    fun clearBlocked() {
        preferences.clearBlockedTracks()
        blockedIds = preferences.blockedTrackIds()
    }

    /** Plan özeti: panelin üst kısmında "içerik uyumu" olarak gösterilir. */
    val moodLabel: String get() = plan.moodLabel
    val planReason: String get() = plan.reason

    /** Manga adı (otomatik arama metni). */
    val titleQuery: String get() = plan.titleQuery

    /** Jamendo kişisel Client ID'si girilmiş mi (akış-only kaynak). */
    val jamendoConfigured: Boolean get() = jamendoClientId.isNotBlank()

    /** Ayarlardan gelen Jamendo Client ID'sini tazeler (arama sürerken güvenli). */
    fun updateJamendoClientId(value: String) {
        jamendoClientId = value.trim()
    }

    // ── Ayar değiştiricileri ──────────────────────────────────────────────
    fun setMode(value: AmbientAudioMode) {
        if (_mode == value) return
        _mode = value
        plan = AmbientAudioPlanner.createPlan(ambientContext, value)
        if (!manualQuery) query = plan.titleQuery.take(MAX_QUERY_LENGTH)
    }

    /** Bölüm değiştiğinde plan/ipucu metnini tazeler (çalma durdurulmaz). */
    fun updateChapter(chapter: MangaChapter?) {
        val updated = ambientContextOf(manga, chapter)
        if (updated == ambientContext) return
        ambientContext = updated
        plan = AmbientAudioPlanner.createPlan(ambientContext, mode)
        if (!manualQuery) query = plan.titleQuery.take(MAX_QUERY_LENGTH)
    }

    fun onQueryChanged(value: String) {
        query = value.take(MAX_QUERY_LENGTH)
        manualQuery = true
    }

    fun resetQueryToTitle() {
        manualQuery = false
        query = plan.titleQuery.take(MAX_QUERY_LENGTH)
    }

    fun toggleSource(source: AmbientAudioSource) {
        val next = if (source in enabledSources) enabledSources - source else enabledSources + source
        // Son kaynak da kapatılamaz: arama tamamen ölmesin.
        if (next.isEmpty()) return
        enabledSources = next
        preferences.setEnabledSources(next)
    }

    fun setOnlinePlaybackEnabled(enabled: Boolean) {
        if (_onlinePlaybackEnabled == enabled) return
        _onlinePlaybackEnabled = enabled
        preferences.onlinePlaybackEnabled = enabled
        if (!enabled) playback.stop()
    }

    fun setAutoPlayOnline(enabled: Boolean) {
        if (_autoPlayOnline == enabled) return
        _autoPlayOnline = enabled
        preferences.autoPlayOnline = enabled
    }

    // ── Arama ─────────────────────────────────────────────────────────────
    fun search(text: String = query, autoPlayBest: Boolean = false, titleFirst: Boolean = false) {
        val trimmed = text.trim()
        if (isSearching) return
        if (enabledSources.isEmpty()) {
            warnings = listOf("Arama için en az bir ses kaynağı açık olmalı.")
            return
        }
        if (!titleFirst && trimmed.isBlank()) return

        isSearching = true
        warnings = emptyList()
        tracks = emptyList()
        usedCache = false
        titleMatchCount = 0
        contextMatchCount = 0
        scope.launch {
            try {
                val currentPlan = AmbientAudioPlanner.createPlan(ambientContext, mode)
                plan = currentPlan
                val result = if (titleFirst) {
                    repository.searchForManga(enabledSources, currentPlan, jamendoClientId)
                } else {
                    repository.search(trimmed, enabledSources, currentPlan, jamendoClientId)
                }
                // "Bir daha önerme" listesindeki parçalar sonuçlardan çıkarılır.
                val blocked = preferences.blockedTrackIds()
                blockedIds = blocked
                tracks = result.tracks.filterNot { it.id in blocked }
                warnings = result.warnings
                usedCache = result.fromCache
                titleMatchCount = result.titleMatchCount
                contextMatchCount = result.contextMatchCount
                if (autoPlayBest && onlinePlaybackEnabled) {
                    // Kullanıcı isteği: yalnızca ilk parça değil, **tüm sıralı
                    // liste** çalsın — parça bitince sıradaki kendiliğinden gelir,
                    // liste bitince başa döner (okuma boyunca kesintisiz ambiyans).
                    // YouTube işaretçisi (kitsugi-yt://) de çalınabilir bir ses akışıdır;
                    // çalma anında gerçek adrese çözülür ve yalnızca ses olarak oynatılır.
                    val playable = tracks.filter { it.isStreamable }
                    if (playable.isNotEmpty()) playQueue(playable, startIndex = 0)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Ağ/kota hatalarında panel sessizce ölmesin; kullanıcıya tek satır bilgi.
                android.util.Log.w("MangaAmbientOnline", "Arama hatası: ${error.message}")
                warnings = listOf("Müzik araması tamamlanamadı. Bağlantını kontrol edip tekrar deneyebilirsin.")
            } finally {
                isSearching = false
            }
        }
    }

    /**
     * "Önce manga adını ara ve en iyi eşleşmeyi çal": başlık eşleşmeleri
     * önce gelir, ardından bağlam önerileri kuyruğa eklenir — böylece parça
     * bitince sıradaki kendiliğinden çalar.
     */
    fun playBestForManga() {
        resetQueryToTitle()
        search(titleFirst = true, autoPlayBest = onlinePlaybackEnabled)
    }

    /** Kuyruktaki bir sonraki çalınabilir parçaya geçer ("Başka öner"). */
    fun playNextCandidate() {
        val playable = tracks.filter { candidate ->
            onlinePlaybackEnabled ||
                AmbientAudioDownloadManager.downloads.value[candidate.id]?.status ==
                AmbientAudioDownloadStatus.COMPLETED
        }
        if (playable.isEmpty()) return
        val currentIndex = playable.indexOfFirst { it.id == playback.currentTrack?.id }
        val next = playable[(currentIndex + 1).mod(playable.size)]
        playQueue(tracks = playable, startIndex = playable.indexOf(next))
    }

    // ── Oynatma ───────────────────────────────────────────────────────────
    /** Çevrimiçi kuyruğu (ve çalan parçayı) durdurur. */
    fun stopPlayback() {
        playback.stop()
    }

    fun playQueue(
        tracks: List<AmbientAudioTrack>,
        startIndex: Int = 0,
        repeatMode: Int = _repeatMode,
    ) {
        // Yerine geçilen kuyruğu sakla: "Sıraya dön" ile geri dönülebilsin.
        val previous = playback.queueSnapshot()
        if (previous.size > 1 && previous.map { it.id } != tracks.map { it.id }) {
            resumeSnapshot = previous to playback.queueIndex
        }
        val localUris = HashMap<String, String>()
        val localTypes = HashMap<String, String>()
        val states = AmbientAudioDownloadManager.downloads.value
        tracks.forEach { track ->
            val state = states[track.id] ?: return@forEach
            if (state.status != AmbientAudioDownloadStatus.COMPLETED) return@forEach
            val uri = state.contentUri?.takeIf { it.isNotBlank() } ?: return@forEach
            localUris[track.id] = uri
            localTypes[track.id] = if (state.converted) "m4a" else track.fileType
        }
        playback.playQueue(
            tracks = tracks,
            startIndex = startIndex,
            localFileUris = localUris,
            localFileTypes = localTypes,
            allowOnline = onlinePlaybackEnabled,
            repeatMode = repeatMode,
        )
    }

    /** Parçayı şu an çalanın hemen ardına koyar. */
    fun playNext(track: AmbientAudioTrack) {
        val state = AmbientAudioDownloadManager.downloads.value[track.id]
        val localUri = state
            ?.takeIf { it.status == AmbientAudioDownloadStatus.COMPLETED }
            ?.contentUri
            ?.takeIf { it.isNotBlank() }
        playback.playNext(
            track = track,
            localFileUri = localUri,
            localFileType = localUri?.let { if (state?.converted == true) "m4a" else track.fileType },
            allowOnline = onlinePlaybackEnabled,
        )
    }

    /** Geçerli oynatma hızı. */
    val playbackSpeed: Float get() = playback.playbackSpeed

    /** Oynatma hızı döngüsü (1x → 1.25x → 1.5x → 2x → 0.75x). */
    fun cycleSpeed() = playback.setPlaybackSpeed(AmbientQueueRules.nextPlaybackSpeed(playback.playbackSpeed))

    fun removeFromQueue(index: Int) = playback.removeFromQueue(index)

    fun jumpToQueueIndex(index: Int) = playback.jumpToQueueIndex(index)

    /** Uyku zamanlayıcısı: süre dolunca çalma durur. 0 kapatır. */
    var sleepTimerMinutes by mutableIntStateOf(0)
        private set
    private var sleepJob: Job? = null

    fun cycleSleepTimer() = setSleepTimer(AmbientQueueRules.nextSleepTimerMinutes(sleepTimerMinutes))

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        sleepJob = null
        sleepTimerMinutes = minutes
        if (minutes <= 0) return
        sleepJob = scope.launch {
            // Son birkaç saniyede sesi kademeli kısar; aniden kesilmez.
            val fadeMs = SLEEP_FADE_MS.coerceAtMost(minutes * 60_000L)
            delay(minutes * 60_000L - fadeMs)
            val startVolume = playback.volume
            val steps = 20
            repeat(steps) { step ->
                playback.setVolume(startVolume * (1f - (step + 1) / steps.toFloat()))
                delay(fadeMs / steps)
            }
            playback.stop()
            playback.setVolume(startVolume)
            sleepTimerMinutes = 0
        }
    }

    fun addToQueue(track: AmbientAudioTrack) {
        val state = AmbientAudioDownloadManager.downloads.value[track.id]
        val localUri = state
            ?.takeIf { it.status == AmbientAudioDownloadStatus.COMPLETED }
            ?.contentUri
            ?.takeIf { it.isNotBlank() }
        playback.addToQueue(
            track = track,
            localFileUri = localUri,
            localFileType = localUri?.let { if (state?.converted == true) "m4a" else track.fileType },
            allowOnline = onlinePlaybackEnabled,
        )
    }

    /** İndirmeyi başlatır; reddedilirse nedeni kısa bir bildirimle gösterir. */
    fun download(track: AmbientAudioTrack) {
        AmbientAudioDownloadManager.start(context, track)?.let { reason ->
            Toast.makeText(context, reason, Toast.LENGTH_SHORT).show()
        }
    }

    fun recordPlayed(track: AmbientAudioTrack) {
        repository.recordPlayed(track)
    }

    private fun ambientContextOf(manga: MangaDetails, chapter: MangaChapter?): AmbientAudioContext =
        AmbientAudioContext(
            title = manga.title,
            chapterTitle = chapter?.name.orEmpty(),
            description = manga.description.orEmpty(),
            genres = manga.genre,
        )

    private companion object {
        const val MAX_QUERY_LENGTH = 120
        /** Boş yeni arama sonrası tekrar denemeden önce bekleme (ağ/kota koruması). */
        const val REFILL_COOLDOWN_MS = 60_000L
        /** Uyku zamanlayıcısında son kısma süresi. */
        const val SLEEP_FADE_MS = 10_000L
    }
}

/**
 * Okuyucuya bağlı denetleyici. Manga değiştiğinde yeniden kurulur; bölüm
 * değişiminde [MangaAmbientOnlineController.updateChapter] çağrılır.
 */
@Composable
fun rememberMangaAmbientOnlineController(
    manga: MangaDetails,
    chapter: MangaChapter?,
    playback: MangaAmbientAudioPlayback,
    jamendoClientId: String = "",
): MangaAmbientOnlineController {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    return remember(manga.url, playback) {
        MangaAmbientOnlineController(
            context = context,
            playback = playback,
            scope = scope,
            manga = manga,
            chapter = chapter,
            jamendoClientId = jamendoClientId,
        )
    }
}
