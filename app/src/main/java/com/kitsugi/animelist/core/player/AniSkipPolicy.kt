package com.kitsugi.animelist.core.player

/**
 * Pure policy helpers for AniSkip integration (SDK-independent).
 *
 * - Whether AniSkip should be disabled when the video already contains chapters
 *   (Aniyomi: "Disable AniSkip if video already contains chapters").
 * - Merging of AniSkip and AnimeSkip intervals (AnimeSkip wins per type).
 */
object AniSkipPolicy {
    /**
     * AniSkip should be skipped entirely when the user opted to disable it on
     * chaptered videos and the video actually has chapters.
     */
    fun shouldDisableOnChapters(hasChapters: Boolean, disableOnChaptersSetting: Boolean): Boolean =
        disableOnChaptersSetting && hasChapters

    /**
     * Kapanış jeneriği (credits) sayılan segment türleri.
     *
     * **Neden sabit liste?** AniSkip `op`/`ed`/`recap`/`mixed-op`/`mixed-ed`, AnimeSkip
     * `opening`/`ending`/`mixed-opening`… üretir; `mixed-op` içiçe geçmiş açılış, `mixed-ed`
     * içiçe geçmiş kapanıştır. Eski kod yalnızca `"outro"` arıyordu — hiçbir sağlayıcı bu
     * türü üretmediği için "jeneriği atlayınca bölüm bitti sayılsın" ve "bitişte sonraki
     * bölüme geç" kuralları **sessizce hiç çalışmıyordu**.
     */
    private val OUTRO_TYPES: Set<String> = setOf("ed", "mixed-ed", "ending", "mixed-ending", "outro")

    /** Açılış jeneriği sayılan segment türleri. */
    private val INTRO_TYPES: Set<String> = setOf("op", "mixed-op", "opening", "mixed-opening", "intro")

    fun isOutroType(type: String): Boolean = type.trim().lowercase() in OUTRO_TYPES

    fun isIntroType(type: String): Boolean = type.trim().lowercase() in INTRO_TYPES

    /**
     * Kapanış jeneriğinin başlangıcı (saniye) — **en erken** kapanış segmenti.
     *
     * Jeneriğe ulaşmak "bölüm bitti" sayıldığı için en erken başlangıç en güvenli seçimdir
     * (birden fazla `ed` tanımı olan bölümlerde geç olanı seçmek tamamlanmayı geciktirirdi).
     */
    fun <T> outroStartSec(
        intervals: List<T>,
        typeOf: (T) -> String,
        startOf: (T) -> Double
    ): Double? = intervals
        .filter { isOutroType(typeOf(it)) }
        .map(startOf)
        .filter { it > 0.0 }
        .minOrNull()

    /**
     * Merge AniSkip intervals with AnimeSkip intervals. AnimeSkip takes priority
     * for a given segment type; AniSkip fills the gaps.
     */
    fun <T> mergeIntervals(
        aniList: List<T>,
        animeList: List<T>,
        typeOf: (T) -> String
    ): List<T> {
        val merged = mutableListOf<T>()
        merged.addAll(animeList)
        val covered = animeList.map(typeOf).toSet()
        aniList.filterNot { typeOf(it) in covered }.forEach { merged.add(it) }
        return merged
    }
}
