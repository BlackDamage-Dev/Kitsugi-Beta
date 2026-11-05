package com.kitsugi.animelist.ui.screens.fullscreen.runtime

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.core.player.AutoTrackPolicy
import com.kitsugi.animelist.data.local.HistoryRepository
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.repository.PlaybackListWriter
import com.kitsugi.animelist.data.settings.KitsugiContentPrefs
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * OYNATICI → LİSTE OTOMATİK TAKİBİ (runtime denetleyicisi).
 *
 * `PlayerRuntimeOrchestrator` içinde `PlayerScrobbleController`'ın yanında yaşar; farkı
 * **hangi kaynaktan** açılırsa açılsın (AniList/MAL/Kitsu/Shikimori/Bangumi/Simkl/TMDB ve
 * Cloudstream eklentileri) aynı davranışı üretmesi ve yazmayı [PlaybackListWriter]
 * üzerinden **altı kaynağa yaymasıdır**.
 *
 * ## Akış
 * 1. `bind()` — oynatıcı bölüm hazır olduğunda kimliği ve bölüm meta verisini verir.
 *    Yerel satır bir kez çözülür; DB her saniye taranmaz.
 * 2. `onTick()` — motorun konum/süre bildirimi. Karar [AutoTrackPolicy.evaluate] ile üretilir,
 *    yalnızca **gerçek bir değişiklik** varsa yazılır (dakikada en fazla bir yazma).
 * 3. Bölüm bittiğinde: ilerleme yazılır → tamamlandıysa `Completed` → kısa süreli
 *    "hızlı puanla" çubuğu ([quickRate]) yayınlanır.
 * 4. `rate()` — çubuktan gelen puan ([PlaybackListWriter.rateEntry]).
 *
 * ## Önemli güvenlik kuralları
 * - İlerleme asla azalmaz; tamamlanmış kayıt yeniden izlenince 0'a düşmez.
 * - "Bırakıldı" kayıt (ayar açıksa) otomatik takiple ezilmez.
 * - Fragman / jenerik (`isTrailer`) ve 60 sn'den kısa videolar takip edilmez.
 * - Çevrimdışıyken yerel yazım başarılıdır; `MediaEntryRepository` başarısız uzak yazımı
 *   bekleyen-senkron kuyruğuna koyar → bağlantı gelince kendiliğinden gönderilir.
 */
class PlayerAutoTrackController(
    private val scope: CoroutineScope,
    private val context: Context,
    /** Test/önizleme için karar dinleyicisi (UI çubuğu bunu kullanmaz). */
    private val onDecision: (AutoTrackPolicy.Decision) -> Unit = {}
) {
    private val TAG = "AutoTrack"

    /** Hızlı puanlama çubuğunun UI modeli. */
    data class QuickRatePrompt(
        val title: String,
        val episode: Int,
        val isMovie: Boolean,
        val currentScore: Int?,
        val remainingSeconds: Int,
        /** Çubuğun toplam görünme süresi (geri sayım oranı için). */
        val totalSeconds: Int,
        val statusLabel: String,
        val source: String?
    )

    private val _quickRate = MutableStateFlow<QuickRatePrompt?>(null)
    val quickRate: StateFlow<QuickRatePrompt?> = _quickRate.asStateFlow()

    /** Son karar (tanı ve "neden yazılmadı" göstergeleri için). */
    private val _lastDecision = MutableStateFlow<AutoTrackPolicy.Decision?>(null)
    val lastDecision: StateFlow<AutoTrackPolicy.Decision?> = _lastDecision.asStateFlow()

    // ── Oturum durumu ───────────────────────────────────────────────────────────
    private var identity: PlaybackListWriter.PlaybackIdentity? = null
    private var entry: MediaEntry? = null
    /** null entry is valid only after a successful list lookup; distinguish it from pending lookup. */
    private var entryResolved: Boolean = false
    private var entryResolutionInFlight: Boolean = false
    private var kind: AutoTrackPolicy.Kind = AutoTrackPolicy.Kind.EPISODE
    private var episode: Int = 1
    private var totalEpisodes: Int? = null
    private var hasNextEpisode: Boolean = true
    private var isTrailer: Boolean = false
    private var outroStartMs: Long? = null
    private var trackedProgress: Int = 0
    private var trackedStatus: AutoTrackPolicy.Status = AutoTrackPolicy.Status.PLANNED
    private var completionHandled: Boolean = false
    private var startHandled: Boolean = false
    private var lastWriteAtMs: Long = 0L
    private var sessionKey: String? = null
    /** Kullanıcı ayarındaki "en kısa takip süresi" (fragman/klip eşiği). */
    private var minDurationMs: Long = 60_000L

    private val writeMutex = Mutex()
    private var quickRateHideJob: Job? = null
    private var quickRateTickJob: Job? = null

    /** Aynı bölüm için yeniden bağlanmayı (motor değişimi/kaynak değişimi) ayıklar. */
    private fun keyOf(identity: PlaybackListWriter.PlaybackIdentity, ep: Int): String =
        "${identity.malId}|${identity.aniListId}|${identity.tmdbId}|${identity.title.lowercase()}|s${identity.season}|e$ep"

    /**
     * Oynatıcı bölüm hazır olduğunda çağrılır.
     *
     * @param force aynı bölüm için yeniden çözümlemeyi zorla (ör. kaynak değişti)
     */
    fun bind(
        identity: PlaybackListWriter.PlaybackIdentity,
        episode: Int,
        totalEpisodes: Int?,
        hasNextEpisode: Boolean,
        kind: AutoTrackPolicy.Kind,
        isTrailer: Boolean = false,
        outroStartSec: Long? = null,
        /** Ayardan gelen "en kısa takip süresi" (saniye). */
        minDurationSec: Int? = null,
        force: Boolean = false
    ) {
        if (minDurationSec != null) this.minDurationMs = (minDurationSec.coerceIn(15, 600) * 1000L)
        val key = keyOf(identity, episode)
        if (!force && key == sessionKey) {
            // Aynı bölüm: yalnızca dinamik alanları tazele (AniSkip geç yüklenebilir).
            if (outroStartSec != null && outroStartSec > 0L) outroStartMs = outroStartSec * 1000L
            return
        }
        sessionKey = key
        this.identity = identity
        this.episode = episode.coerceAtLeast(0)
        this.totalEpisodes = totalEpisodes?.takeIf { it > 0 }
        this.hasNextEpisode = hasNextEpisode
        this.kind = kind
        this.isTrailer = isTrailer
        this.outroStartMs = outroStartSec?.takeIf { it > 0L }?.times(1000L)
        this.completionHandled = false
        this.startHandled = false
        this.entry = null
        this.entryResolved = false
        this.entryResolutionInFlight = false
        this.trackedProgress = 0
        this.trackedStatus = AutoTrackPolicy.Status.PLANNED
        this.lastWriteAtMs = 0L
        hideQuickRate()

        if (isTrailer) {
            Log.d(TAG, "Fragman/jenerik: otomatik takip devre dışı ($key)")
            return
        }
        if (!settings().enabled) {
            // Deneysel özellik VARSAYILAN OLARAK KAPALI: kullanıcı elle açmadıkça hiçbir
            // liste/geçmiş yazımı yapılmaz, hızlı puanlama çubuğu da çıkmaz.
            Log.d(TAG, "Otomatik takip kapalı (ayarlardan açılmalı): $key")
            return
        }
        resolveEntryIfNeeded()
    }

    /** Ayar akışından gelen "en kısa takip süresi" güncellemesi (oturum yeniden bağlanmadan). */
    fun updateMinDurationSeconds(seconds: Int) {
        val value = seconds.coerceIn(15, 600) * 1000L
        if (value != minDurationMs) minDurationMs = value
    }

    /**
     * Liste sorgusu salt okunur. Disabled durumda bind() bu fonksiyona ulaşmaz; kullanıcı
     * aynı oturumda etkinleştirirse onTick() lazy olarak başlatır. Hata halinde sonraki tik
     * yeniden dener, "bulunamadı" ile "henüz çözümlenmedi" birbirine karışmaz.
     */
    private fun resolveEntryIfNeeded() {
        if (entryResolved || entryResolutionInFlight) return
        val identityAtStart = identity ?: return
        val keyAtStart = sessionKey
        entryResolutionInFlight = true
        scope.launch(Dispatchers.IO) {
            val result = runCatching { PlaybackListWriter.resolveEntry(context, identityAtStart) }
            if (sessionKey != keyAtStart) return@launch
            entryResolutionInFlight = false
            result.onSuccess { resolved ->
                entry = resolved
                trackedProgress = resolved?.progress ?: 0
                trackedStatus = resolved?.status?.toTrackStatus() ?: AutoTrackPolicy.Status.PLANNED
                entryResolved = true
                Log.d(
                    TAG,
                    "bind ep=$episode kind=$kind entry=${resolved?.title ?: "<listede yok>"} " +
                        "progress=$trackedProgress status=$trackedStatus"
                )
            }.onFailure { error ->
                Log.w(TAG, "Liste kaydı çözümlenemedi; sonraki tikte yeniden denenecek", error)
            }
        }
    }

    /** AniSkip/outro bilgisi bölüm ortasında gelebilir. */
    fun updateOutroStart(outroStartSec: Long?) {
        val value = outroStartSec?.takeIf { it > 0L }?.times(1000L) ?: return
        outroStartMs = value
    }

    /**
     * Motorun konum bildirimi (saniyede birkaç kez gelebilir).
     * Yazma yalnızca karar gerçekten değiştiğinde yapılır.
     */
    fun onTick(positionMs: Long, durationMs: Long, isPlaying: Boolean = true, endReached: Boolean = false) {
        val currentIdentity = identity ?: return
        if (isTrailer) return
        // Ayarlar her tikte yeniden okunur: kullanıcı özelliği oynatma sırasında kapatırsa
        // bir sonraki tikte takip anında durur.
        val settings = settings()
        if (!settings.enabled) {
            hideQuickRate()
            return
        }
        if (!settings.quickRateEnabled) hideQuickRate()
        // Kapalıyken bind() bilinçli olarak listeyi çözümlemez. Etkinleştirme sonrasında
        // ilk tikte önce mevcut kaydı çözümleyip (salt okunur) ancak sonraki tikte karar ver;
        // böylece oynatım sürerken açıldığında mevcut kayıt yanlışlıkla yinelenmez.
        if (!entryResolved) {
            resolveEntryIfNeeded()
            return
        }
        // Duraklatılmışken sadece bitiş bildirimi işlenir: kullanıcı bir süre sonra devam
        // ederse ilerleme yine yazılır, ama gereksiz yazma üretilmez.
        if (!isPlaying && !endReached) return

        val snapshot = AutoTrackPolicy.Snapshot(
            positionMs = positionMs,
            durationMs = durationMs,
            episode = episode,
            totalEpisodes = totalEpisodes,
            kind = kind,
            hasNextEpisode = hasNextEpisode,
            endReached = endReached,
            outroStartMs = outroStartMs,
            seasonNumber = currentIdentity.season
        )
        val current = if (entry == null) null else AutoTrackPolicy.TrackedState(
            status = trackedStatus,
            progress = trackedProgress,
            total = entry?.total ?: totalEpisodes,
            score = entry?.score
        )
        val decision = AutoTrackPolicy.evaluate(snapshot, settings, current)
        _lastDecision.value = decision
        onDecision(decision)
        if (!decision.hasMutation) return

        // Yazma sıklığı sınırı: aynı bölüm için 15 saniyede en fazla bir yazma.
        // (Tamamlanma kararı bu sınırı atlar — bölüm biterken yazma kaybolmamalı.)
        val now = System.currentTimeMillis()
        val isCompletion = decision.action == AutoTrackPolicy.Action.COMPLETE ||
            decision.action == AutoTrackPolicy.Action.ADVANCE_EPISODE
        if (!isCompletion && now - lastWriteAtMs < WRITE_THROTTLE_MS) return
        if (isCompletion && completionHandled) return
        if (decision.action == AutoTrackPolicy.Action.START_WATCHING && startHandled) return
        lastWriteAtMs = now
        if (decision.action == AutoTrackPolicy.Action.START_WATCHING) startHandled = true
        // Bölüm ilerlemesi + karar tek bir IO işinde, kilit altında yazılır. Sıra önemli:
        // ilerleme yazılamazsa `completionHandled` ilerletilmemeli, aksi hâlde bölüm bir
        // daha asla listede ilerlemezdi.
        if (isCompletion) completionHandled = true
        applyDecision(decision, positionMs, durationMs)
    }

    /**
     * Oynatıcı ekranından çıkarken: son konum tek bir kez değerlendirilir.
     *
     * Tavan (throttle) burada bilinçli olarak atlanır — çıkış anı son şanstır. Yalnızca
     * eşiği geçmiş bölümler ilerletilir (`endReached=false`), yani yarım bırakılan bölüm
     * "izlendi" olmaz.
     */
    fun onLeaving(positionMs: Long, durationMs: Long) {
        if (identity == null || isTrailer) return
        if (!settings().enabled) {
            hideQuickRate()
            return
        }
        if (positionMs <= 0L || durationMs <= 0L) return
        lastWriteAtMs = 0L
        onTick(positionMs = positionMs, durationMs = durationMs, isPlaying = true)
    }

    /** Motor `ENDED` bildirdiğinde: konum/süre ne olursa olsun bölüm tamamlanmıştır. */
    fun onEnded(positionMs: Long, durationMs: Long) {
        onTick(positionMs = positionMs, durationMs = durationMs, isPlaying = false, endReached = true)
    }

    /**
     * BÖLÜM İLERLEMESİ — en önemli adım.
     *
     * Kullanıcı isteği: *"bölüm bitince otomatik olarak izlenen bölüm sayısını bir artır"*.
     * Bölüm numarası ilerlemeye yazılır (`progress = max(mevcut, bölüm)`), yani kullanıcı
     * 5. bölümü izlediğinde 1–4 zaten geçmişte kalmışsa liste geride kalmaz.
     *
     * **Neden durum yazımından AYRI?** Durum/puan yazımı bulut API'sine gider ve
     * `MediaEntryRepository.update` başarısız uzak yazımı bekleyen-senkron kuyruğuna koyar.
     * İlerleme ise listede satır **yoksa bile** (otomatik ekleme kapalıyken) yerel geçmiş
     * tablosuna yazılmalıdır; "devam et" satırı ve geçmiş ekranı buradan okur.
     *
     * @return ilerleme en az bir yere yazıldıysa `true`
     */
    private suspend fun applyEpisodeProgress(positionMs: Long, durationMs: Long): Boolean {
        if (!settings().enabled) return false
        if (kind == AutoTrackPolicy.Kind.UNKNOWN) return false
        var wrote = false
        // 1) Yerel geçmiş: satır yoksa oluşturulur, varsa "izlendi" işaretlenir.
        if (!settings().enabled) return false
        runCatching { markHistoryWatched(positionMs, durationMs) }
            .onSuccess { wrote = true }
            .onFailure { Log.w(TAG, "Geçmiş ilerlemesi yazılamadı: ${it.message}") }
        // 2) Liste satırı: varsa ilerleme/durum kararı normal akışta yazılır. Yoksa ve
        //    otomatik ekleme kapalıysa yalnızca geçmiş yazımıyla yetinilir.
        val current = entry ?: return wrote
        val targetProgress = AutoTrackPolicy.evaluate(
            snapshot = AutoTrackPolicy.Snapshot(
                positionMs = positionMs,
                durationMs = durationMs,
                episode = episode,
                totalEpisodes = totalEpisodes,
                kind = kind,
                hasNextEpisode = hasNextEpisode,
                endReached = true,
                outroStartMs = outroStartMs
            ),
            settings = settings(),
            current = AutoTrackPolicy.TrackedState(
                status = trackedStatus,
                progress = trackedProgress,
                total = current.total ?: totalEpisodes,
                score = current.score
            )
        ).progress
        if (targetProgress <= trackedProgress) return wrote
        val latestSettings = settings()
        if (!latestSettings.enabled) return wrote
        val result = PlaybackListWriter.updateEntry(
            context = context,
            entry = current,
            progress = targetProgress,
            syncExternal = latestSettings.syncSources
        )
        if (result is PlaybackListWriter.Result.Updated) {
            entry = result.entry
            trackedProgress = result.entry.progress
            wrote = true
            Log.d(TAG, "Bölüm ilerlemesi yazıldı: $targetProgress (${result.entry.title})")
        }
        return wrote
    }

    private fun applyDecision(
        decision: AutoTrackPolicy.Decision,
        positionMs: Long,
        durationMs: Long
    ) {
        val currentIdentity = identity ?: return
        scope.launch(Dispatchers.IO) {
            writeMutex.withLock {
                // Ayar coroutine beklerken kapanmış olabilir. Yazım sınırında tekrar denetle.
                val currentSettings = settings()
                if (!currentSettings.enabled) {
                    hideQuickRate()
                    return@withLock
                }
                // 1) Bölüm ilerlemesi: geçmiş tablosu + liste satırı (durum yazımından bağımsız).
                if (decision.action == AutoTrackPolicy.Action.ADVANCE_EPISODE ||
                    decision.action == AutoTrackPolicy.Action.COMPLETE
                ) {
                    if (!applyEpisodeProgress(positionMs, durationMs)) {
                        // Yazma başarısız: tekrar denenebilsin diye tamamlanma kilidi açılır.
                        completionHandled = false
                    }
                }
                val existing = entry
                if (existing == null) {
                    if (!currentSettings.addNewEntries) {
                        Log.d(TAG, "Listede yok ve otomatik ekleme kapalı: ${currentIdentity.title}")
                        return@withLock
                    }
                    val created = PlaybackListWriter.createEntry(
                        context = context,
                        identity = currentIdentity,
                        progress = decision.progress,
                        status = decision.status.toWatchStatus(),
                        total = totalEpisodes ?: if (kind == AutoTrackPolicy.Kind.MOVIE) 1 else null
                    ) ?: return@withLock
                    entry = created
                    trackedProgress = created.progress
                    // Yeni satır tamamlanmış açılmış olabilir (tek bölümlük içerik): durum
                    // kararın değil DB'nin gerçeğine göre saklanır, aksi hâlde sonraki
                    // tikler yanlış "Watching" varsayımıyla çalışırdı.
                    trackedStatus = created.status.toTrackStatus()
                    Log.d(TAG, "Otomatik eklendi: ${created.title} (${created.status}) ep=$episode")
                    // Yerel geçmişte bu medya için DAHA İLERİDE izlenmiş bölüm varsa
                    // (kullanıcı 5. bölümü izlerken 1-4'ü geçmişte duruyorsa) liste geride
                    // kalmasın: sıçrama yalnızca bölümlü içerikte ve ileri yönde yapılır.
                    backfillFromWatchHistory()
                } else {
                    val result = PlaybackListWriter.updateEntry(
                        context = context,
                        entry = existing,
                        status = decision.status.toWatchStatus(),
                        progress = decision.progress,
                        syncExternal = currentSettings.syncSources
                    )
                    if (result is PlaybackListWriter.Result.Updated) {
                        entry = result.entry
                        trackedProgress = result.entry.progress
                        trackedStatus = result.entry.status.toTrackStatus()
                        if (result.changed) {
                            Log.d(
                                TAG,
                                "Liste güncellendi: ${result.entry.title} status=${decision.status} " +
                                    "progress=${decision.progress}/${result.entry.total ?: "?"} action=${decision.action}"
                            )
                        }
                    }
                }

                // Bölüm ilerlemesi `applyEpisodeProgress` içinde yazıldı (tek yazma yolu).
                if (decision.showQuickRate && currentSettings.quickRateEnabled) {
                    // StateFlow yazımı iş parçacığından bağımsızdır; Main'e geçmek
                    // IO kilidi tutulurken gereksiz gecikme yaratıyordu.
                    showQuickRate(currentSettings)
                }
            }
        }
    }

    /**
     * Yerel `watch_history` tablosundaki "izlendi" bayraklarından liste ilerlemesini tamamlar.
     *
     * Otomatik takip yalnızca BURADAN itibaren izlenen bölümleri görür; kullanıcının bu
     * sürümden önce izlediği bölümler listede eksik kalırsa "5. bölümü izledim ama liste 2
     * gösteriyor" durumu doğar. Geçmişte işaretlenmiş en yüksek bölüm daha ileriyse ve
     * bölümlü içerikse ilerleme oraya çekilir. Tamamlanma kararı verilmez (bölüm henüz
     * bitmedi); yalnızca sayı hizalanır.
     */
    private suspend fun backfillFromWatchHistory() {
        if (!settings().enabled) return
        if (kind != AutoTrackPolicy.Kind.EPISODE) return
        val currentEntry = entry ?: return
        val currentIdentity = identity ?: return
        val mediaId = mediaIdForHistory(currentIdentity)
        val maxWatched = runCatching {
            HistoryRepository(KitsugiDatabase.getDatabase(context).historyDao())
                .maxWatchedEpisode(mediaId)
        }.getOrNull() ?: return
        if (maxWatched <= currentEntry.progress) return
        val target = currentEntry.total?.let { minOf(maxWatched, it) } ?: maxWatched
        val status = if (currentEntry.status == WatchStatus.Planned) WatchStatus.Watching else currentEntry.status
        val latestSettings = settings()
        if (!latestSettings.enabled) return
        val result = PlaybackListWriter.updateEntry(
            context = context,
            entry = currentEntry,
            status = status,
            progress = target,
            syncExternal = latestSettings.syncSources
        )
        if (result is PlaybackListWriter.Result.Updated) {
            entry = result.entry
            trackedProgress = result.entry.progress
            Log.d(TAG, "Geçmişten ilerleme tamamlandı: $target (${result.entry.title})")
        }
    }

    /** Yerel geçmişte de "izlendi" işareti: "devam et" satırı aynı bölümü tekrar önermez. */
    private suspend fun markHistoryWatched(positionMs: Long, durationMs: Long) {
        if (!settings().enabled) return
        val currentIdentity = identity ?: return
        val mediaId = mediaIdForHistory(currentIdentity)
        HistoryRepository(KitsugiDatabase.getDatabase(context).historyDao())
            .markEpisodeWatched(
                mediaId = mediaId,
                episode = episode,
                durationMs = durationMs.coerceAtLeast(positionMs),
                // Kaynak adı geçmiş ekranında görünür; oynatıcının bildirdiği eklenti adı
                // varsa yazılır (yoksa mevcut kayıt korunur).
                addonName = currentIdentity.sourceHint
            )
    }

    /**
     * Hızlı puanlama çubuğu. Kısa süre görünür, kendiliğinden kapanır; oynatıcı
     * kontrolleri açıkken de kapanmaz (kullanıcı puanı girebilsin).
     */
    private fun showQuickRate(settings: AutoTrackPolicy.Settings) {
        val current = entry
        val currentIdentity = identity ?: return
        val title = current?.title?.takeIf { it.isNotBlank() }
            ?: currentIdentity.title.ifBlank { "—" }
        val seconds = settings.quickRateSeconds.coerceIn(5, 60)
        val prompt = QuickRatePrompt(
            title = title,
            episode = episode,
            isMovie = kind == AutoTrackPolicy.Kind.MOVIE,
            currentScore = current?.score,
            remainingSeconds = seconds,
            totalSeconds = seconds,
            statusLabel = decisionStatusLabel(settings),
            source = current?.source
        )
        _quickRate.value = prompt
        quickRateTickJob?.cancel()
        quickRateTickJob = scope.launch {
            var remaining = seconds
            while (remaining > 0) {
                delay(1_000L)
                remaining -= 1
                val latest = _quickRate.value ?: return@launch
                if (latest.remainingSeconds != remaining) {
                    _quickRate.value = latest.copy(remainingSeconds = remaining)
                }
            }
            _quickRate.value = null
        }
    }

    private fun decisionStatusLabel(settings: AutoTrackPolicy.Settings): String =
        if (settings.syncSources) {
            com.kitsugi.animelist.utils.KitsugiStrings.getOrFallback(
                com.kitsugi.animelist.R.string.auto_track_synced_all,
                "Tüm bağlı kaynaklara yazıldı"
            )
        } else {
            com.kitsugi.animelist.utils.KitsugiStrings.getOrFallback(
                com.kitsugi.animelist.R.string.auto_track_synced_local,
                "Yerel listeye yazıldı"
            )
        }

    fun hideQuickRate() {
        quickRateTickJob?.cancel()
        quickRateTickJob = null
        _quickRate.value = null
    }

    /**
     * Çubuktan puan verildi.
     *
     * Puan `0` verilirse puan **temizlenir** (MediaEntry.score = null). "Planlandı" bir
     * kayda puan verilmesi, kullanıcının onu izlediği anlamına gelir → durum ilerletilir
     * ([AutoTrackPolicy.statusAfterScore]).
     */
    fun rate(score: Int) {
        val currentEntry = entry
        val currentIdentity = identity
        hideQuickRate()
        // Özellik/alt özellik kapalıyken çubuk hiç çıkmaz; yine de yazımı engelle.
        val currentSettings = settings()
        if (!currentSettings.enabled || !currentSettings.quickRateEnabled) return
        if (currentEntry == null) {
            // Kayıt henüz oluşmadıysa (nadiren) puanı kaybetmek yerine listeye ekleyip puanla.
            if (currentIdentity == null || !currentSettings.addNewEntries) return
            scope.launch(Dispatchers.IO) {
                writeMutex.withLock {
                    val latestSettings = settings()
                    if (!latestSettings.enabled || !latestSettings.quickRateEnabled || !latestSettings.addNewEntries) return@withLock
                    // Film/tek parça ise puan vermek "izlendi" demektir → doğrudan tamamlanmış
                    // açılır; bölümlü içerikte ilerleme oynatıcının bildirdiği yerde kalır.
                    val createStatus = if (kind == AutoTrackPolicy.Kind.MOVIE) WatchStatus.Completed else WatchStatus.Watching
                    val createTotal = totalEpisodes ?: if (kind == AutoTrackPolicy.Kind.MOVIE) 1 else null
                    val created = PlaybackListWriter.createEntry(
                        context = context,
                        identity = currentIdentity,
                        progress = if (kind == AutoTrackPolicy.Kind.MOVIE) (createTotal ?: 1) else trackedProgress,
                        status = createStatus,
                        total = createTotal
                    ) ?: return@withLock
                    entry = created
                    trackedStatus = if (createStatus == WatchStatus.Completed) AutoTrackPolicy.Status.COMPLETED else AutoTrackPolicy.Status.WATCHING
                    trackedProgress = created.progress
                    PlaybackListWriter.rateEntry(context, created, score, latestSettings.syncSources)
                }
            }
            return
        }
        val nextStatus = AutoTrackPolicy.statusAfterScore(
            current = AutoTrackPolicy.TrackedState(
                status = trackedStatus,
                progress = currentEntry.progress,
                total = currentEntry.total,
                score = currentEntry.score
            ),
            score = score,
            kind = kind
        )
        val normalized = AutoTrackPolicy.normalizeScore(score)
        val nextProgress = AutoTrackPolicy.progressAfterScore(
            current = AutoTrackPolicy.TrackedState(
                status = trackedStatus,
                progress = currentEntry.progress,
                total = currentEntry.total,
                score = currentEntry.score
            ),
            nextStatus = nextStatus,
            kind = kind
        )
        scope.launch(Dispatchers.IO) {
            writeMutex.withLock {
                val latestSettings = settings()
                if (!latestSettings.enabled || !latestSettings.quickRateEnabled) return@withLock
                val result = PlaybackListWriter.updateEntry(
                    context = context,
                    entry = entry ?: currentEntry,
                    status = nextStatus.toWatchStatus(),
                    progress = nextProgress,
                    score = normalized,
                    syncExternal = latestSettings.syncSources
                )
                if (result is PlaybackListWriter.Result.Updated) {
                    entry = result.entry
                    trackedStatus = nextStatus
                    trackedProgress = result.entry.progress
                    Log.d(TAG, "Hızlı puan yazıldı: ${normalized ?: "temizlendi"} (${result.entry.title})")
                }
            }
        }
    }

    /** Oynatıcıdan çıkarken; çubuk ve oturum temizlenir. */
    fun clear() {
        hideQuickRate()
        sessionKey = null
        identity = null
        entry = null
        completionHandled = false
        startHandled = false
    }

    // ─── Yardımcılar ─────────────────────────────────────────────────────────────

    private fun settings(): AutoTrackPolicy.Settings = AutoTrackPolicy.Settings(
        enabled = KitsugiContentPrefs.autoTrackEnabled,
        addNewEntries = KitsugiContentPrefs.autoTrackAddNew,
        respectDropped = KitsugiContentPrefs.autoTrackRespectDropped,
        quickRateEnabled = KitsugiContentPrefs.quickRateEnabled,
        quickRateSeconds = KitsugiContentPrefs.quickRateSeconds,
        syncSources = KitsugiContentPrefs.autoTrackSyncSources,
        minTrackableDurationMs = minDurationMs
    )

    /**
     * `watch_history` tablosunun anahtarı.
     *
     * **Oynatıcının kendi yazdığı anahtarla BİREBİR aynı olmak zorundadır**
     * (`KitsugiPlayerViewModel.getResolveMediaId()` = `malId ?: aniListId ?: title.hashCode()`).
     * Burada liste satırının `id`'sini kullanmak, aynı bölüm için ikinci bir geçmiş satırı
     * açardı: "devam et" yanlış yeri gösterir, geçmişten geri doldurma (`backfill`) hiçbir şey
     * bulamazdı.
     */
    private fun mediaIdForHistory(identity: PlaybackListWriter.PlaybackIdentity): Int =
        identity.malId ?: identity.aniListId ?: identity.bangumiId ?: identity.title.hashCode()

    private fun WatchStatus.toTrackStatus(): AutoTrackPolicy.Status = when (this) {
        WatchStatus.Watching -> AutoTrackPolicy.Status.WATCHING
        WatchStatus.Completed -> AutoTrackPolicy.Status.COMPLETED
        WatchStatus.Planned -> AutoTrackPolicy.Status.PLANNED
        WatchStatus.Dropped -> AutoTrackPolicy.Status.DROPPED
        WatchStatus.Paused -> AutoTrackPolicy.Status.PAUSED
        WatchStatus.Repeating -> AutoTrackPolicy.Status.REPEATING
    }

    private fun AutoTrackPolicy.Status.toWatchStatus(): WatchStatus = when (this) {
        AutoTrackPolicy.Status.WATCHING -> WatchStatus.Watching
        AutoTrackPolicy.Status.COMPLETED -> WatchStatus.Completed
        AutoTrackPolicy.Status.PLANNED -> WatchStatus.Planned
        AutoTrackPolicy.Status.DROPPED -> WatchStatus.Dropped
        AutoTrackPolicy.Status.PAUSED -> WatchStatus.Paused
        AutoTrackPolicy.Status.REPEATING -> WatchStatus.Repeating
    }

    companion object {
        /** Aynı bölüm için art arda yazma aralığı (uzak API'leri dövmemek için). */
        private const val WRITE_THROTTLE_MS = 15_000L

        /** Oynatıcı türünden içerik şekli. */
        fun kindOf(isMovie: Boolean, mediaType: MediaType?): AutoTrackPolicy.Kind = when {
            isMovie -> AutoTrackPolicy.Kind.MOVIE
            mediaType == MediaType.Movie && isMovie -> AutoTrackPolicy.Kind.MOVIE
            else -> AutoTrackPolicy.Kind.EPISODE
        }
    }
}
