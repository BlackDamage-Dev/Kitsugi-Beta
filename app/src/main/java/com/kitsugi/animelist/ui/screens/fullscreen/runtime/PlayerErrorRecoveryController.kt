package com.kitsugi.animelist.ui.screens.fullscreen.runtime

import android.content.Context
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.kitsugi.animelist.core.player.PlayerManagerListener
import com.kitsugi.animelist.core.player.engine.PlayerEngineType
import com.kitsugi.animelist.core.player.engine.PlayerFallbackCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * T1.6: PlayerErrorRecoveryController
 *
 * Oynatma hatalarını yakalar ve fallback / retry mantığını yönetir.
 * Hata → engine fallback (MEDIA3→MPV) → source fallback → retry (max 3x) → kullanıcıya bildir.
 *
 * Engine switching ([PlayerFallbackCoordinator]) artık bu sınıfın içinde yönetilmektedir;
 * UI katmanındaki ad-hoc fallback koordinatörüne gerek kalmamaktadır.
 */
class PlayerErrorRecoveryController(
    private val scope: CoroutineScope,
    private val onRetry: (attempt: Int) -> Unit,
    private val onFallback: () -> Unit,
    private val onFatal: (errorCode: Int, errorMsg: String) -> Unit,
    /** Called when a fallback engine switch is required (e.g. MEDIA3 → MPV). */
    private val onSwitchEngine: ((to: PlayerEngineType) -> Unit)? = null,
    /** Lambda that provides the currently active engine type. */
    private val getCurrentEngine: (() -> PlayerEngineType)? = null,
    /** Whether the user has MPV enabled in settings. */
    private val isMpvEnabled: (() -> Boolean)? = null,
    /** Application context for showing toasts. */
    private val context: Context? = null
) {
    private val TAG = "ErrorRecovery"

    companion object {
        private const val MAX_RETRY = 3
        private const val RETRY_DELAY_MS = 1500L

        /**
         * Aynı hata çok kısa sürede birden fazla kez raporlanabilir (watchdog + ExoPlayer
         * listener + MPV event'i). Bu pencere içindeki birebir aynı hata yok sayılır; aksi
         * hâlde tek bir bozuk kaynak iki-üç motor geçişini tek seferde tetikliyordu.
         */
        private const val DUPLICATE_ERROR_WINDOW_MS = 1_200L
    }

    private var retryCount = 0
    private var lastErrorSignature: String? = null
    private var lastErrorAtMs = 0L
    private val _isRecovering = MutableStateFlow(false)
    val isRecovering: StateFlow<Boolean> = _isRecovering.asStateFlow()

    /** Internal engine-level fallback coordinator */
    private val engineFallback = PlayerFallbackCoordinator(
        maxAttempts = 3,
        listener = object : PlayerManagerListener {
            override fun onPlayerSwitched(from: PlayerEngineType, to: PlayerEngineType) {
                Log.d(TAG, "Engine fallback: $from → $to")
                showToastOnMain("⚠️ Oynatma hatası — Oynatıcı motoru değiştiriliyor...")
                onSwitchEngine?.invoke(to)
            }
            override fun onFatalError(errorCode: Int, errorMsg: String) {
                Log.e(TAG, "Engine fallback exhausted: $errorMsg")
                // All engines tried — now fall through to source-level fallback
                triggerSourceFallbackOrFatal(errorCode, errorMsg)
            }
        }
    )

    /**
     * Hata bildirimi alındığında çağrılır.
     * Engine switching önce denenir, sonra kaynak değiştirme, sonra retry, sonra fatal.
     */
    fun onPlaybackError(errorCode: Int, errorMsg: String) {
        val now = android.os.SystemClock.elapsedRealtime()
        val signature = "$errorCode|$errorMsg"
        if (signature == lastErrorSignature && now - lastErrorAtMs < DUPLICATE_ERROR_WINDOW_MS) {
            Log.d(TAG, "Duplicate playback error ignored: code=$errorCode ($errorMsg)")
            return
        }
        lastErrorSignature = signature
        lastErrorAtMs = now
        Log.w(TAG, "Playback error: code=$errorCode, msg=$errorMsg, retry=$retryCount, hasFallback=$hasFallback")

        // ── Kodek / çözücü hataları ──────────────────────────────────────────
        // Media3 4000–4005: decoder init/query başarısız, format desteklenmiyor, çözünürlük
        // cihaz sınırını aşıyor. 5001/5002: ses kanalı başlatılamadı/yazılamadı
        // ("ses yok" şikâyetinin tipik kodu).
        //
        // ESKİ DAVRANIŞ (HATA): Bu durumlar MPV motorunu atlayıp doğrudan kaynak değiştirmeye
        // gidiyordu. MPV (libmpv + ffmpeg) bu formatların neredeyse tamamını oynatabildiği için
        // kullanıcı "bu kaynak çalışmıyor" görüyordu. YENİ DAVRANIŞ: kodek hatalarında önce
        // MPV motoruna geçilir; MPV de başarısız olursa kaynak değişir.
        val isCodecFailure = errorCode in 4000..4005 || errorCode == 5001 || errorCode == 5002

        // 1. Kesin ölü kaynak (401/403/404/5xx, tanınmayan format): motor denemek anlamsız.
        //    Not: "UnrecognizedInputFormat" bir kodek değil KAP (container) hatasıdır; MPV
        //    yine de oynatabilir — bu yüzden yalnızca HTTP/erişim hatalarında motoru atlarız.
        val isHttpLevelFailure = errorCode == 403 ||
            errorMsg.contains("403") ||
            errorMsg.contains("404") ||
            errorMsg.contains("401") ||
            errorMsg.contains("Response code: 4") ||
            errorMsg.contains("Response code: 5")

        if (isHttpLevelFailure) {
            Log.e(TAG, "Erişim/HTTP hatası ($errorCode). Kaynak değiştirme deneniyor.")
            retryCount = 0
            _isRecovering.value = false
            triggerSourceFallbackOrFatal(errorCode, errorMsg)
            return
        }

        // 2. Motor seviyesinde kurtarma (yalnızca dahili motorlar): MEDIA3 ↔ MPV
        val currentEngine = getCurrentEngine?.invoke() ?: PlayerEngineType.MEDIA3

        // Kodek hatasında aynı motorda tekrar denemek anlamsızdır: doğrudan ikinci dahili motor MPV.
        // MPV uygulamayla birlikte paketlidir; kullanıcı tercihinden bağımsız olarak kullanılabilir.
        val mpvUsable = isMpvEnabled?.invoke() ?: true
        if (isCodecFailure && currentEngine == PlayerEngineType.MEDIA3 && mpvUsable) {
            Log.w(TAG, "Kodek/çözücü hatası ($errorCode) — MPV motoruna geçiş zorlanıyor.")
            if (onSwitchEngine != null) {
                retryCount = 0
                _isRecovering.value = false
                // Zorunlu geçişte her iki tarafı da kaydet: başarısız Media3'e ve henüz
                // denenecek MPV'ye. MPV sonrasında gelecek farklı bir hata, kaynak zaten
                // açılamayan Media3'e gereksiz geri dönüş yapmamalı.
                engineFallback.markTried(PlayerEngineType.MEDIA3)
                engineFallback.markTried(PlayerEngineType.MPV)
                onSwitchEngine(PlayerEngineType.MPV)
                return
            }
        }

        // MPV de kodek hatası verdiyse MEDIA3'e geri dönmek anlamsızdır (aynı çözücü
        // yetersizliği). Doğrudan kaynak değiştirmeye geç.
        if (isCodecFailure && currentEngine == PlayerEngineType.MPV) {
            Log.w(TAG, "Kodek hatası MPV'de de sürdü ($errorCode) — kaynak değiştiriliyor.")
            retryCount = 0
            _isRecovering.value = false
            triggerSourceFallbackOrFatal(errorCode, errorMsg)
            return
        }

        // Motor kurtarma yalnızca dahili motorlar arasında (MEDIA3 ↔ MPV) yapılır.
        val nextEngine = engineFallback.getFallbackEngine(
            currentEngine = currentEngine,
            errorCode = errorCode,
            mpvEnabled = mpvUsable
        )

        if (nextEngine != null) {
            if (onSwitchEngine != null) {
                // Engine switch is being triggered — wait for the new engine to pick up
                Log.d(TAG, "Switching to engine $nextEngine — deferring source fallback")
                retryCount = 0
                _isRecovering.value = false
                return
            }
            // Motor değiştirme kanalı yoksa (ör. eski çağrı yolu) boş yere beklemeyelim.
            Log.w(TAG, "Fallback engine $nextEngine istendi ancak onSwitchEngine bağlı değil")
        }
        // nextEngine == null means engineFallback.onFatalError was called → handled by listener
    }

    private fun triggerSourceFallbackOrFatal(errorCode: Int, errorMsg: String) {
        if (hasFallback) {
            onFallback()
            return
        }
        if (retryCount < MAX_RETRY) {
            retryCount++
            _isRecovering.value = true
            Log.w(TAG, "Aynı kaynak yeniden deneniyor ($retryCount/$MAX_RETRY)")
            showToastOnMain("Oynatma yeniden deneniyor ($retryCount/$MAX_RETRY)…")
            scope.launch {
                delay(RETRY_DELAY_MS)
                // Bu arada kaynak değişmiş olabilir; o durumda retry yapma.
                if (hasFallback) {
                    onFallback()
                } else {
                    onRetry(retryCount)
                }
            }
            return
        }
        retryCount = 0
        _isRecovering.value = false
        onFatal(errorCode, "Yayın kaynağı okunamadı ($errorCode). Lütfen farklı bir kaynak seçin.")
    }

    /** Oynatma başarılı olduğunda retry sayacını ve engine fallback durumunu sıfırla */
    fun onPlaybackReady() {
        if (retryCount > 0) {
            Log.d(TAG, "Recovery successful after $retryCount retries")
            retryCount = 0
        }
        _isRecovering.value = false
    }

    /** Fallback kaynak mevcut mu (VM kaynak listesi tarafından güncellenir). */
    var hasFallback: Boolean = false

    /** Hata kurtarma veya motor geçişi tetiklendi mi? */
    val hasRecoveryAttempted: Boolean
        get() = retryCount > 0 || engineFallback.attemptCount > 0 || _isRecovering.value

    /** Controller durumunu ve engine fallback sayacını sıfırla (yeni medya açıldığında) */
    fun reset() {
        retryCount = 0
        lastErrorSignature = null
        lastErrorAtMs = 0L
        _isRecovering.value = false
        engineFallback.reset()
    }

    private fun showToastOnMain(msg: String) {
        val ctx = context ?: return
        val run = Runnable { Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
        if (Looper.myLooper() == Looper.getMainLooper()) run.run()
        else android.os.Handler(Looper.getMainLooper()).post(run)
    }
}
