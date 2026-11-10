package com.kitsugi.animelist.data.ambient

/**
 * 🎛️ Ortam sesi oynatma ve indirme kuralları — saf (Android'siz) mantık.
 *
 * Neden ayrı dosya: kuyruk kurma, tekrar modu ve indirme reddi kararları
 * JVM birim testleriyle doğrulanabilsin diye Media3/Context'ten bağımsız
 * tutuldu. Değerler Media3 `Player.REPEAT_MODE_*` sabitleriyle birebir aynıdır.
 */
internal object AmbientQueueRules {

    const val REPEAT_OFF = 0
    const val REPEAT_ONE = 1
    const val REPEAT_ALL = 2

    /** Sıralı devamda bir seferde kuyruğa alınacak en fazla parça. */
    const val MAX_CONTINUATION_QUEUE = 25

    /** Kuyruk bu kadar parça kalınca yeni aday eklenir (sıra bitmeden). */
    const val REFILL_THRESHOLD = 2

    /** Kuyruğa tek seferde eklenecek en fazla yeni aday. */
    const val REFILL_BATCH = 5

    /** Uyku zamanlayıcısı seçenekleri (dakika). 0 = kapalı. */
    val SLEEP_TIMER_OPTIONS: List<Int> = listOf(0, 15, 30, 60)

    /** Uyku zamanlayıcısı döngüsü: kapalı → 15 → 30 → 60 → kapalı. */
    fun nextSleepTimerMinutes(current: Int): Int {
        val index = SLEEP_TIMER_OPTIONS.indexOf(current).let { if (it < 0) 0 else it }
        return SLEEP_TIMER_OPTIONS[(index + 1).mod(SLEEP_TIMER_OPTIONS.size)]
    }

    /** Oynatma hızı seçenekleri. Döngü sırası kullanıcı dostu tutuldu (1x başta). */
    val PLAYBACK_SPEEDS: List<Float> = listOf(1f, 1.25f, 1.5f, 2f, 0.75f)

    fun nextPlaybackSpeed(current: Float): Float {
        val index = PLAYBACK_SPEEDS.indexOfFirst { kotlin.math.abs(it - current) < 0.001f }.let { if (it < 0) 0 else it }
        return PLAYBACK_SPEEDS[(index + 1).mod(PLAYBACK_SPEEDS.size)]
    }

    /** Tekrar modu döngüsü: kapalı → tek parça → liste → kapalı. */
    fun nextRepeatMode(current: Int): Int = when (current) {
        REPEAT_OFF -> REPEAT_ONE
        REPEAT_ONE -> REPEAT_ALL
        else -> REPEAT_OFF
    }

    /**
     * Kullanıcı [pickedId] parçasını seçtiğinde sıralı devam kuyruğunu kurar.
     *
     * Seçilen parça başta olur; ardından listedeki sonraki parçalar, en sonda
     * ise seçilenden önceki parçalar gelir. Engellenmiş ve çalınamayan
     * parçalar atlanır. Seçilen parça çalınabilir değilse boş döner.
     */
    fun buildQueueFrom(
        visible: List<AmbientAudioTrack>,
        pickedId: String,
        blockedIds: Set<String>,
        limit: Int = MAX_CONTINUATION_QUEUE,
    ): List<AmbientAudioTrack> {
        val playable = visible
            .filter { it.isStreamable && it.id !in blockedIds }
            .distinctBy { it.deduplicationKey() }
        val pickedIndex = playable.indexOfFirst { it.id == pickedId }
        if (pickedIndex < 0) return emptyList()
        return (playable.drop(pickedIndex) + playable.take(pickedIndex)).take(limit)
    }

    /** Kuyrukta olmayan, engellenmemiş ve çalınabilir yeni adaylar (sıra korunur). */
    fun continuationCandidates(
        pool: List<AmbientAudioTrack>,
        queuedIds: Set<String>,
        blockedIds: Set<String>,
        limit: Int = REFILL_BATCH,
    ): List<AmbientAudioTrack> = pool
        .filter { it.isStreamable && it.id !in queuedIds && it.id !in blockedIds }
        .distinctBy { it.deduplicationKey() }
        .take(limit)

    /**
     * Sıralı devam açıkken kuyruk bitmeden yeni aday istenmeli mi?
     *
     * @param remaining kuyrukta çalınmamış kalan parça sayısı
     */
    fun shouldRequestMore(
        remaining: Int,
        repeatMode: Int,
        continuous: Boolean,
        onlineEnabled: Boolean,
    ): Boolean = continuous &&
        onlineEnabled &&
        repeatMode != REPEAT_ONE &&
        remaining <= REFILL_THRESHOLD

    /**
     * Parça indirilemiyorsa kullanıcıya gösterilecek kısa neden.
     * `null` dönerse parça indirilebilir demektir.
     */
    fun downloadRefusalReason(track: AmbientAudioTrack): String? {
        val provider = track.provider.lowercase()
        return when {
            provider == "radio_browser" -> "Canlı radyo yayını indirilemez."
            track.isDownloadable -> null
            provider == "jamendo" ->
                "Jamendo yalnızca akış sunar; indirme bu kaynakta kapalı."
            provider == "audius" ->
                "Bu parçanın sanatçısı indirmeyi kapatmış."
            provider == "youtube" ->
                "Bu YouTube parçası için indirilebilir ses adresi bulunamadı."
            else -> "Bu kaynak bu parça için indirme adresi sunmuyor."
        }
    }
}
