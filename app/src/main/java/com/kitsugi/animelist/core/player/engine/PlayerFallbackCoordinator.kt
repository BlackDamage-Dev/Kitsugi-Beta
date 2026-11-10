package com.kitsugi.animelist.core.player.engine

import android.util.Log
import com.kitsugi.animelist.core.player.PlaybackState
import com.kitsugi.animelist.core.player.PlayerManagerListener

/**
 * TASK_032 — PlayerFallbackCoordinator (Geliştirilmiş)
 *
 * NuvioTV PlayerRuntimeControllerEngineFailover ve PlayerManagerImpl
 * referans alınarak genişletildi.
 *
 * Özellikler:
 * - Deneme limiti ([maxAttempts]) aşıldığında fallback durdurulur
 * - Fallback zinciri: MEDIA3 → MPV → EXTERNAL → null (bitti)
 * - [PlayerManagerListener] üzerinden geçiş ve fatal hata bildirimi
 * - [reset] ile yeni bölüm/kaynak geçişinde sayaç sıfırlanır
 */
class PlayerFallbackCoordinator(
    private val maxAttempts: Int = 3,
    private val listener: PlayerManagerListener? = null
) {
    private val TAG = "PlayerFallbackCoord"
    private var attempts = 0

    /** Bu kaynak için zaten denenmiş dahili motorlar (aynı motora geri dönüşü engeller). */
    private val triedEngines = mutableSetOf<PlayerEngineType>()

    /**
     * Mevcut motora göre bir sonraki fallback motorunu döndürür.
     *
     * @param currentEngine Şu an oynatmaya çalışan motor tipi
     * @param errorCode     [PlayerEngine.Listener.onPlaybackError] hata kodu
     * @param mpvEnabled    MPV dahili motorunun kullanılabilir olup olmadığı (paketle gelir, varsayılan true)
     *
     * @return Denedecek sonraki motor; fallback bitti ise null
     */
    fun getFallbackEngine(
        currentEngine: PlayerEngineType,
        errorCode: Int,
        mpvEnabled: Boolean = true
    ): PlayerEngineType? {
        if (attempts >= maxAttempts) {
            Log.w(TAG, "Fallback limit aşıldı ($attempts/$maxAttempts) — fatal error bildiriliyor")
            listener?.onFatalError(
                errorCode = errorCode,
                errorMsg  = "Tüm dahili motorlar ($maxAttempts deneme) başarısız oldu. Hata kodu: $errorCode"
            )
            return null
        }

        attempts++
        // The active engine has already failed for this source. Recording only the
        // destination allowed MEDIA3 → MPV → MEDIA3 loops (and could return to the very
        // decoder that had just failed). Each source gets at most one attempt per engine.
        triedEngines += currentEngine
        val next = nextInternalEngine(currentEngine, mpvEnabled, triedEngines)

        Log.d(TAG, "Fallback #$attempts: $currentEngine → $next (hata kodu: $errorCode, mpv=$mpvEnabled)")

        if (next != null) {
            triedEngines += next
            listener?.onPlayerSwitched(from = currentEngine, to = next)
        } else {
            listener?.onFatalError(
                errorCode = errorCode,
                errorMsg  = "Fallback zinciri tükendi (son motor: $currentEngine). Hata kodu: $errorCode"
            )
        }

        return next
    }

    /**
     * Yeni bölüm veya kaynak değişiminde fallback sayacını sıfırlar.
     * KitsugiPlayerViewModel.resetAutoSwitch() ile birlikte çağrılmalıdır.
     */
    fun reset() {
        if (attempts > 0) Log.d(TAG, "Fallback sayacı sıfırlandı ($attempts deneme vardı)")
        attempts = 0
        triedEngines.clear()
    }

    /** Kaç fallback denemesi yapıldığını döndürür */
    val attemptCount: Int get() = attempts

    /** Bir motorun bu oturumda zaten denendiğini bildirir (ör. kodek hatasında zorunlu MPV geçişi). */
    fun wasTried(engine: PlayerEngineType): Boolean = engine in triedEngines

    /** Dışarıdan yapılan zorunlu motor geçişini kaydeder; aynı motora geri dönüşü engeller. */
    fun markTried(engine: PlayerEngineType) {
        triedEngines += engine
    }

    // ── Statik yardımcı (geriye dönük uyumluluk) ──────────────────────────────

    companion object {
        /**
         * Statik kullanım için kolaylaştırıcı — listener veya limit yok.
         * Mevcut inline kodlar için geriye dönük uyumlu.
         */
        fun nextEngine(
            currentEngine: PlayerEngineType,
            mpvEnabled: Boolean = true
        ): PlayerEngineType? = nextInternalEngine(currentEngine, mpvEnabled, emptySet())

        /**
         * Dahili motorlar arasında bir sonraki motoru seçer.
         * MEDIA3 → MPV (MPV paketle geldiği için her zaman kullanılabilir kabul edilir,
         * [mpvEnabled] false verilirse yalnızca MPV'yi atlar), MPV → MEDIA3.
         * Zaten denenmiş motorlara dönülmez.
         */
        internal fun nextInternalEngine(
            currentEngine: PlayerEngineType,
            mpvEnabled: Boolean,
            tried: Set<PlayerEngineType>
        ): PlayerEngineType? {
            val candidate = when (currentEngine) {
                PlayerEngineType.MEDIA3   -> if (mpvEnabled) PlayerEngineType.MPV else null
                PlayerEngineType.MPV      -> PlayerEngineType.MEDIA3
                PlayerEngineType.EXTERNAL -> null
            }
            return candidate?.takeUnless { it in tried }
        }
    }
}
