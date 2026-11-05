package com.kitsugi.animelist.core.player

/**
 * OYNATMA KAYNAKLI OTOMATİK LİSTE TAKİBİ — SAF POLİTİKA MOTORU.
 *
 * ## Ne yapar?
 * Kullanıcı **hangi kaynaktan** (AniList, MAL, Kitsu, Shikimori, Bangumi, Simkl, TMDB /
 * Cloudstream eklentisi / Stremio) **hangi içeriği** (anime bölümü, film, dizi, TV show,
 * tek bölümlük OVA/ONA) izlerse izlesin; oynatıcının bildirdiği konum/süre çiftinden
 * "liste ne olmalı?" kararını üretir:
 *
 * - izlemeye başlandı → durum `Watching` (Planlandı ise),
 * - bölüm bitti → **bölüm numarası** ilerlemeye yazılır (`progress = max(mevcut, bölüm)`),
 * - son bölüm bittiyse / film veya tek bölümlük içerik bittiyse → `Completed`,
 * - ilerleme **asla geri alınmaz** (bayat bir tick, tamamlanmış kaydı bozamaz).
 *
 * ## Neden "saf"?
 * Sınıf Android, Compose veya Room'a dokunmaz; `Context`, `suspend`, `Flow` yoktur. Böylece
 * aynı kurallar üç yerde birebir aynı sonucu verir:
 * 1. oynatıcı (`PlayerAutoTrackController` — her saniye gelen tick),
 * 2. harici oynatıcıdan dönüş (`onExternalPlayerResult`),
 * 3. birim testleri (`AutoTrackPolicyTest`).
 *
 * ## Eşik ve "fragman/bitiş" muhasebesi
 * - `completeThreshold` (varsayılan %85) aşıldığında bölüm izlenmiş sayılır.
 * - AniSkip `outro` aralığının başlangıcını **geçmek** de tamamlanma sayılır: kullanıcı
 *   jeneriği atladığında oynatıcı %100'e ulaşmaz, ama bölüm gerçekten bitmiştir
 *   ("fragman / bitiş kısmını hesaba kat" isteği).
 * - `endReached` (motor `ENDED` durumu) her zaman tamamlanmadır.
 * - `isTrailer` (YouTube video kimliği ile açılan tanıtım/jenerik oynatmaları) ve
 *   `minTrackableDurationMs`'den kısa klipler **asla** takip edilmez.
 *
 * ## Deneysel ve VARSAYILAN KAPALI
 * Bu yetenek kullanıcı hesabındaki gerçek listeleri değiştirir. Bu yüzden
 * `Settings.enabled` varsayılanı `false`'tur ve açma işi yalnızca **Ayarlar → Deneysel**
 * sayfasındaki onay penceresinden yapılır (`ExperimentalSettingsScreen`). Kapalıyken
 * `evaluate` her zaman `Action.NONE` döner; yazma katmanı da ayrıca kapıyı kontrol eder.
 *
 * ## Kullanıcı iradesine saygı
 * - `Dropped` bir kayıt otomatik olarak `Watching`'e çevrilmez (`respectDropped`).
 * - `Completed` kayıt yeniden izlense bile ilerlemesi düşürülmez; yalnızca hızlı puanlama
 *   çubuğu yeniden sunulur.
 */
object AutoTrackPolicy {

    /** İçeriğin şekli — bölüm mü, tek parça (film) mi? */
    enum class Kind {
        /** Bölümlü içerik: anime, dizi, TV show. */
        EPISODE,

        /** Tek parça: film, anime filmi, tek bölümlük OVA, belgesel. */
        MOVIE,

        /** Bilinmiyor (kaynak türü çözülemedi) — yalnızca "İzleniyor" işaretlenir. */
        UNKNOWN
    }

    /** Politikanın gördüğü durum adları ([com.kitsugi.animelist.model.WatchStatus] eşleniği). */
    enum class Status {
        WATCHING, COMPLETED, PLANNED, DROPPED, PAUSED, REPEATING
    }

    /** Karar türü. */
    enum class Action {
        /** Hiçbir şey yazılmaz (gereksiz ağ/DB yazımı yok). */
        NONE,

        /** Durum "İzleniyor"a çekilir (ilerleme değişmez). */
        START_WATCHING,

        /** Bölüm bitti; ilerlemeye bölüm numarası yazılır. */
        ADVANCE_EPISODE,

        /** Son bölüm/film bitti; durum "Tamamlandı". */
        COMPLETE
    }

    /** Kullanıcı ayarları (Compose dışı katman bunu `AppSettings`'ten doldurur). */
    data class Settings(
        /** Deneysel özellik — varsayılan KAPALI (bkz. [AutoTrackPolicy] başlığı). */
        val enabled: Boolean = false,
        /** Listede olmayan içerik otomatik eklensin mi? */
        val addNewEntries: Boolean = true,
        val respectDropped: Boolean = true,
        /** Henüz yayınlanmamış bölümleri "izlendi" sayma (bölüm numarası toplamı aşamaz). */
        val countUnaired: Boolean = false,
        /** Oynatma bu orana ulaşınca "İzleniyor" işaretlenir. */
        val startThreshold: Float = 0.05f,
        /** Bu orana ulaşınca bölüm izlenmiş sayılır. */
        val completeThreshold: Float = 0.85f,
        /** Bitince hızlı puanlama çubuğu gösterilsin mi? */
        val quickRateEnabled: Boolean = true,
        /** Çubuk bu saniye sonra kendiliğinden kapanır. */
        val quickRateSeconds: Int = 20,
        /** Kararlar bağlı tüm platformlara yayılsın mı? */
        val syncSources: Boolean = true,
        /** Bundan kısa videolar (klip, önizleme) takip edilmez. */
        val minTrackableDurationMs: Long = 60_000L
    )

    /** Listede var olan kaydın politikaya görünen özeti. */
    data class TrackedState(
        val status: Status,
        val progress: Int = 0,
        val total: Int? = null,
        val score: Int? = null
    )

    /**
     * Oynatıcıdan gelen anlık görüntü.
     *
     * @param positionMs anlık konum
     * @param durationMs toplam süre (0/negatif = canlı yayın veya süre bilinmiyor)
     * @param episode oynatılan bölüm numarası (film/tek parça için 1)
     * @param totalEpisodes serinin bilinen toplam bölümü (yoksa null)
     * @param kind içerik şekli
     * @param hasNextEpisode bu bölümden sonra bir bölüm var mı?
     * @param endReached motor `ENDED` bildirdi mi?
     * @param outroStartMs AniSkip outro aralığının başlangıcı (ms) — varsa jeneriği geçmek tamamlanmadır
     * @param seasonNumber sezon (bilgi amaçlı; Anime satırlarında sezon tek satırdır)
     * @param isTrailer tanıtım/jenerik oynatması — asla takip edilmez
     */
    data class Snapshot(
        val positionMs: Long,
        val durationMs: Long,
        val episode: Int = 1,
        val totalEpisodes: Int? = null,
        val kind: Kind = Kind.EPISODE,
        val hasNextEpisode: Boolean = true,
        val endReached: Boolean = false,
        val outroStartMs: Long? = null,
        val seasonNumber: Int = 1,
        val isTrailer: Boolean = false
    )

    /** Karar. [showQuickRate] yalnızca sunum içindir; yazma kararı [action] ile verilir. */
    data class Decision(
        val action: Action,
        val status: Status,
        val progress: Int,
        val fraction: Float,
        val showQuickRate: Boolean = false
    ) {
        /** Yazılacak bir şey var mı? */
        val hasMutation: Boolean get() = action != Action.NONE
    }

    /**
     * İzlenmiş sayılan oran (0..1).
     *
     * Jenerik sonrası (`outroStartMs` ve sonrası) tam kabul edilir; `endReached` doğrudan 1.
     * Süre bilinmiyorsa (0) canlı yayın varsayılır: bitiş bildirilmediyse 0.
     */
    fun effectiveFraction(snapshot: Snapshot, settings: Settings = Settings()): Float {
        if (snapshot.endReached) return 1f
        val duration = snapshot.durationMs
        if (duration <= 0L) return 0f
        val position = snapshot.positionMs.coerceIn(0L, duration)
        val outro = snapshot.outroStartMs
            ?.takeIf { it in 1L until duration }
            ?.coerceAtLeast(settings.minTrackableDurationMs.coerceAtMost(duration / 2))
        val effective = if (outro != null && position >= outro) duration else position
        return (effective.toDouble() / duration.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Konum/süre çiftinden "liste ne olmalı?" kararını üretir.
     *
     * @param current listedeki mevcut kayıt (yoksa `null` — otomatik ekleme kararı verilir)
     */
    fun evaluate(
        snapshot: Snapshot,
        settings: Settings = Settings(),
        current: TrackedState? = null
    ): Decision {
        val fraction = effectiveFraction(snapshot, settings)
        val baseStatus = current?.status ?: Status.PLANNED
        val baseProgress = current?.progress ?: 0
        val total = (snapshot.totalEpisodes?.takeIf { it > 0 } ?: current?.total?.takeIf { it > 0 })
        val quiet = Decision(Action.NONE, baseStatus, baseProgress, fraction)

        if (!settings.enabled) return quiet
        // Tanıtım/jenerik ve çok kısa klipler takip edilmez; bitiş bildirimi olsa bile
        // (bir fragmanın sonuna gelmek "bölüm izlendi" demek değildir).
        if (snapshot.isTrailer) return quiet
        if (snapshot.durationMs <= 0L && !snapshot.endReached) return quiet
        if (snapshot.durationMs in 1 until settings.minTrackableDurationMs) return quiet
        if (fraction.isNaN() || fraction.isInfinite()) return quiet
        // Kullanıcı bilinçli olarak bıraktı: otomatik takip durumu ezmez.
        if (baseStatus == Status.DROPPED && settings.respectDropped) return quiet
        // Tekrar izlenen (Repeating) kayıt ilerlemeyi düşürmemeli; tamamlanınca
        // ilerleme toplamda kalır, durum yine Completed'e döner.
        val reachedEnd = snapshot.endReached || fraction >= settings.completeThreshold.coerceIn(0.1f, 1f)

        if (reachedEnd) {
            // Zaten tamamlanmış bir kaydın ilerlemesi ASLA değiştirilmez; yalnızca hızlı
            // puanlama çubuğu yeniden sunulur (kullanıcı tekrar izledi ve puan vermek isteyebilir).
            if (baseStatus == Status.COMPLETED) {
                return Decision(Action.NONE, Status.COMPLETED, baseProgress, 1f, settings.quickRateEnabled)
            }
            val isFinalEpisode = when (snapshot.kind) {
                Kind.MOVIE -> true
                // Tür çözülemediyse yalnızca "sonraki bölüm yok" kanıtı yeterlidir.
                Kind.UNKNOWN -> !snapshot.hasNextEpisode
                Kind.EPISODE -> !snapshot.hasNextEpisode ||
                    (total != null && snapshot.episode >= total)
            }
            val targetProgress = computeProgress(
                episode = snapshot.episode,
                baseProgress = baseProgress,
                total = total,
                countUnaired = settings.countUnaired,
                completing = isFinalEpisode
            )
            return if (isFinalEpisode) {
                Decision(
                    action = Action.COMPLETE,
                    status = Status.COMPLETED,
                    progress = total ?: targetProgress,
                    fraction = 1f,
                    showQuickRate = settings.quickRateEnabled
                )
            } else {
                Decision(
                    action = Action.ADVANCE_EPISODE,
                    status = Status.WATCHING,
                    progress = targetProgress,
                    fraction = 1f,
                    showQuickRate = settings.quickRateEnabled
                )
            }
        }

        // İzlemeye başlandı: yalnızca durum düzeltilir, ilerleme DEĞİŞMEZ.
        if (fraction >= settings.startThreshold.coerceIn(0f, 0.9f)) {
            val shouldStart = when (baseStatus) {
                Status.PLANNED, Status.PAUSED -> true
                // Tekrar izleme: durum zaten doğru, ilerleme tamamlanınca güncellenir.
                Status.REPEATING, Status.WATCHING, Status.COMPLETED, Status.DROPPED -> false
            }
            if (shouldStart) {
                return Decision(
                    action = Action.START_WATCHING,
                    status = Status.WATCHING,
                    progress = baseProgress,
                    fraction = fraction
                )
            }
        }
        return quiet
    }

    /**
     * İlerleme hesabı: **asla azaltmaz**, toplam biliniyorsa toplamı aşmaz.
     *
     * @param completing son bölüm izleniyorsa `true` → ilerleme toplam bölüme çekilir
     */
    private fun computeProgress(
        episode: Int,
        baseProgress: Int,
        total: Int?,
        countUnaired: Boolean,
        completing: Boolean
    ): Int {
        val episodeIndex = episode.coerceAtLeast(0)
        val candidate = when {
            completing && total != null -> total
            total != null && !countUnaired -> episodeIndex.coerceIn(0, total)
            else -> episodeIndex
        }
        val merged = maxOf(baseProgress, candidate)
        return if (total != null) merged.coerceIn(0, total) else merged
    }

    /**
     * Hızlı puanlama çubuğunun "ön yüz" metinleri için yardımcı: kalan süre (ms).
     * Süre bilinmiyorsa 0 döner.
     */
    fun remainingMs(snapshot: Snapshot): Long {
        if (snapshot.durationMs <= 0L) return 0L
        return (snapshot.durationMs - snapshot.positionMs).coerceIn(0L, snapshot.durationMs)
    }

    /**
     * Puanın geçerli aralıkta olup olmadığı. `MediaEntry.score` 0..10 tamsayıdır;
     * 0 = "puanlanmadı" (kayıt silinmez, sadece puan temizlenir).
     */
    fun normalizeScore(score: Int?): Int? =
        score?.takeIf { it in 1..10 }

    /**
     * Puan vermenin duruma etkisi: izlenmiş bir içerik "puanladım" diye durum değiştirmez,
     * ancak **Planlandı** bir kayıt puanlanınca kullanıcı onu izlemiş sayılmalıdır →
     * durum `Completed`e çekilir (yalnızca film/tek parça ya da ilerleme toplamdaysa
     * `Watching`). Bu, "kısayoldan puan ver" isteğinin doğal sonucudur.
     */
    fun statusAfterScore(current: TrackedState, score: Int, kind: Kind): Status {
        if (score <= 0) return current.status
        return when (current.status) {
            Status.PLANNED -> if (kind == Kind.MOVIE) Status.COMPLETED else Status.WATCHING
            Status.DROPPED -> Status.DROPPED
            else -> current.status
        }
    }

    /**
     * Puan verildiğinde **ilerlemenin** ne olacağı.
     *
     * `Planlandı` bir **film** kaydına puan vermek onu tamamlar; ilerleme de `total`e (yoksa
     * 1'e) çekilmelidir, aksi hâlde liste "Tamamlandı · 0/1" gösteriyordu. Bölümlü içerikte
     * ilerlemeye dokunulmaz: kullanıcı yalnızca puan vermiştir, kaç bölüm izlediğini
     * otomatik takip bilir.
     */
    fun progressAfterScore(current: TrackedState, nextStatus: Status, kind: Kind): Int {
        val base = current.progress.coerceAtLeast(0)
        if (nextStatus != Status.COMPLETED) return base
        val target = current.total?.takeIf { it > 0 } ?: if (kind == Kind.MOVIE) 1 else null
        return if (target != null) maxOf(base, target) else base
    }
}
