package com.kitsugi.animelist.data.sync

/**
 * Liste yenilemenin kim tarafından istendiği. Karar eşikleri buna göre değişir:
 * kullanıcı "Yenile"ye bastığında daha kısa bir tazelik penceresi, arka plan
 * yenilemesinde daha uzun bir pencere uygulanır.
 */
enum class ListRefreshMode(val isUserInitiated: Boolean) {
    /** Kullanıcı sekmeyi/başlığı elle yeniledi (pull-to-refresh / yenile simgesi). */
    USER_REFRESH(true),

    /** Kullanıcı "baştan tam yenile" istedi (force): politika atlanır, tam liste çekilir. */
    USER_FORCE(true),

    /** Uygulama açılışında / arka planda sessiz yenileme. */
    BACKGROUND(false)
}

/** [ListRefreshPolicy.plan] sonucu. */
enum class ListRefreshPlan {
    /** Ağa hiç çıkılmaz: veri yeterince taze veya uzak revizyon değişmemiş. */
    SKIP_UP_TO_DATE,

    /** Yalnızca son senkrondan beri değişen kayıtlar çekilir (destekleyen kaynaklarda). */
    DELTA,

    /** Listenin tamamı çekilir (silinenleri de yakalayan uzlaştırma turu). */
    FULL
}

/**
 * Liste yenileme politikası — **saf fonksiyon**, yan etkisi yok.
 *
 * Tasarım referansları (açık kaynak uygulamalar):
 *  - **Mihon / Tachiyomi**: kitaplık güncellemesi kaynak başına sıralı çalışır, kaynak
 *    başına `lastUpdated` zamanı ve güncelleme aralığı tutulur; "her açılışta tüm
 *    kitaplığı baştan çek" yoktur, aralık dolmadan ağa çıkılmaz.
 *  - **AniHyou / MoeList / Shouze (AniList istemcileri)**: liste yerelde tutulur;
 *    sunucudan çekim `updatedAt` tabanlı fark (delta) ile yapılır ve yerelde henüz
 *    gönderilmemiş düzenlemeler (outbox) sunucu verisiyle EZİLMEZ.
 *  - **Kotatsu**: kaynak başına TTL + kaynaklar arasında gecikmeli (staggered) yenileme.
 *
 * Kurallar:
 *  1. Hiç senkron olmamış kaynak → FULL.
 *  2. Son başarılı senkron "tazelik penceresi" içinde → SKIP (ağa çıkılmaz).
 *     Delta desteklemeyen kaynaklarda arka plan penceresi daha uzundur (tam çekim pahalı).
 *  3. Destekleyen kaynakta son TAM senkron 24 saatten yeni → DELTA.
 *  4. Aksi halde → FULL (silinen kayıtları da yakalayan uzlaştırma turu).
 *  5. Arka planda 3 ardışık hatadan sonra 15 dk geri çekilme (sunucuyu dövmemek için).
 *  6. [ListRefreshMode.USER_FORCE] her zaman FULL.
 */
object ListRefreshPolicy {

    /**
     * Defterdeki damga bu kadar ilerideyse (cihaz saati değişmiş / yedekten eski-yeni karışık
     * zaman damgası) defter güvenilmez sayılır ve tam çekim yapılır.
     */
    const val CLOCK_SKEW_TOLERANCE_MS = 5 * 60 * 1000L

    /** Elle yenilemede "zaten taze" sayılan pencere. */
    const val USER_FRESH_WINDOW_MS = 45_000L

    /** Arka plan yenilemesinde "zaten taze" sayılan pencere (delta destekleyen kaynaklar). */
    const val BACKGROUND_FRESH_WINDOW_MS = 60 * 60 * 1000L

    /**
     * Delta DESTEKLEMEYEN kaynaklarda (MAL, Kitsu, Shikimori, Bangumi) arka planda
     * "zaten taze" sayılan pencere. Bu kaynaklarda her yenileme TAM liste çekmek
     * demektir; bu yüzden arka planda en fazla 6 saatte bir çalışırlar. Kullanıcı
     * elle yenilerse politika yine uygulanır (45 sn tazelik penceresinden sonra tam çekim).
     */
    const val BACKGROUND_FULL_ONLY_WINDOW_MS = 6 * 60 * 60 * 1000L

    /**
     * Delta yenilemesinin kullanılabilmesi için son TAM senkronun en fazla bu kadar
     * eski olması gerekir. Delta silmeleri yakalayamaz; tam uzlaştırma periyodik
     * olarak yapılmalıdır.
     */
    const val DELTA_MAX_FULL_AGE_MS = 24 * 60 * 60 * 1000L

    /**
     * Delta'nın başlangıç noktası en fazla bu kadar geriye gidebilir. Uzun süre
     * uygulama açılmadıysa fark çekmek yerine tam liste çekmek daha güvenli.
     */
    const val DELTA_MAX_AGE_MS = 6 * 60 * 60 * 1000L

    /** Arka planda art arda bu kadar hata sonrası geri çekilme uygulanır. */
    const val MAX_CONSECUTIVE_FAILURES = 3

    /** Hata sonrası arka plan yenilemelerinin bekleyeceği süre. */
    const val FAILURE_BACKOFF_MS = 15 * 60 * 1000L

    fun plan(
        state: ListSyncState,
        mode: ListRefreshMode,
        supportsDelta: Boolean,
        now: Long = System.currentTimeMillis()
    ): ListRefreshPlan {
        if (mode == ListRefreshMode.USER_FORCE) return ListRefreshPlan.FULL

        if (!state.hasSyncedOnce) return ListRefreshPlan.FULL

        // SAAT KAYMASI KORUMASI: cihaz saati geriye alındıysa (ya da defter gelecekteki bir
        // damga taşıyorsa) `now - lastSuccessAt` negatif çıkar ve liste SONSUZA KADAR
        // "taze" sayılırdı — kullanıcı elle yenilediğinde bile ağa çıkılmaz. Damga
        // gelecekteyse hiç senkron olunmamış gibi davranılır.
        if (state.lastSuccessAt > now + CLOCK_SKEW_TOLERANCE_MS) return ListRefreshPlan.FULL

        val sinceSuccess = (now - state.lastSuccessAt).coerceAtLeast(0L)
        val freshWindow = when {
            mode.isUserInitiated -> USER_FRESH_WINDOW_MS
            supportsDelta -> BACKGROUND_FRESH_WINDOW_MS
            else -> BACKGROUND_FULL_ONLY_WINDOW_MS
        }
        if (sinceSuccess < freshWindow) return ListRefreshPlan.SKIP_UP_TO_DATE

        if (!mode.isUserInitiated &&
            state.failureCount >= MAX_CONSECUTIVE_FAILURES &&
            now - state.lastAttemptAt < FAILURE_BACKOFF_MS
        ) {
            return ListRefreshPlan.SKIP_UP_TO_DATE
        }

        if (supportsDelta &&
            state.lastFullSyncAt > 0L &&
            now - state.lastFullSyncAt < DELTA_MAX_FULL_AGE_MS &&
            sinceSuccess < DELTA_MAX_AGE_MS
        ) {
            return ListRefreshPlan.DELTA
        }

        return ListRefreshPlan.FULL
    }

    /** Delta çekiminde başlangıç damgası (epoch saniye); güvenlik payı düşülür. */
    fun deltaSinceEpochSeconds(lastSuccessAt: Long, safetyMarginSeconds: Long = 120L): Long =
        (lastSuccessAt / 1000L - safetyMarginSeconds).coerceAtLeast(1L)
}
