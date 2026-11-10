package com.kitsugi.animelist.ui.screens.fullscreen.controls

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kitsugi.animelist.core.player.input.ExternalInputDetector
import com.kitsugi.animelist.core.player.input.PlayerKeyBinding
import com.kitsugi.animelist.core.player.input.PlayerKeyRouter
import com.kitsugi.animelist.core.player.input.PlayerOsdDurations
import com.kitsugi.animelist.core.player.input.PlayerShortcutAction
import com.kitsugi.animelist.ui.screens.fullscreen.components.KeyboardIndicatorMessage
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Klavye / gamepad kısayollarının **tek uygulama noktası**.
 *
 * Sorumlulukları:
 *  1. Tuş olayını [PlayerKeyRouter] ile eyleme çevirmek (basılı tutma tekrarları dahil).
 *  2. Eylemi oynatıcıya uygulamak — eylem başına tek bir "yap" fonksiyonu.
 *  3. Kullanıcıya **yalnızca o eylemin** küçük göstergesini göstermek.
 *
 * Bu sınıfın bilinçli olarak YAPMADIĞI şey: `showControls()` çağırmak.
 * Tam OSD'yi açmak, kullanıcının şikâyet ettiği "arayüz gelip gidiyor" davranışının
 * kaynağıydı. Ekranı açık tutmak isteyen tek eylem [PlayerShortcutAction.TOGGLE_UI]
 * ve [PlayerShortcutAction.OPEN_SETTINGS]'tir; bunlar kullanıcının bilinçli isteğidir.
 *
 * Gösterge zamanlaması ekran tarafında `LaunchedEffect(indicator)` ile yönetilir;
 * mesajdaki [KeyboardIndicatorMessage.seq] her tetiklemede arttığı için aynı eylemin
 * arka arkaya basılması sayacı sıfırlar.
 */
@Stable
class PlayerKeyboardController(
    private val router: PlayerKeyRouter,
    /**
     * Geri çağrılar her tuş basımında **taze** okunur.
     *
     * Neden sağlayıcı (provider)?
     * Oynatıcı ekranı saniyede bir kez yeniden çiziliyor (konum güncellemesi) ve
     * bölüm/altyazı/parça listeleri sık sık değişiyor. Değerleri kurucuya kopyalamak,
     * `currentEpisode` gibi alanların bayatlamasına ve "sonraki bölüm" kısayolunun
     * eski bölümü açmasına yol açıyordu. Sağlayıcı sayesinde gösterge durumu korunur
     * ama her eylem en güncel oynatıcı durumuyla çalışır.
     */
    private val callbacksProvider: () -> PlayerKeyboardCallbacks,
) {
    private val callbacks: PlayerKeyboardCallbacks get() = callbacksProvider()

    /** Ekranda gösterilecek kısa ömürlü gösterge; `null` = gizli. */
    var indicator by mutableStateOf<KeyboardIndicatorMessage?>(null)
        private set

    /** Kısayol yardım ekranı görünür mü? (mpv `?` / VLC yardımı) */
    var helpVisible by mutableStateOf(false)
        private set

    /** Çıkış onayı bekleniyor mu? (yanlışlıkla `q` ile oturum kaybını önler) */
    var exitConfirmVisible by mutableStateOf(false)
        private set

    private var sequence = 0L

    /** Basılı tutulan eylemlerin toplam kaydırma miktarı (Just Player `keySeekStart`). */
    private var seekAccumulatorMs = 0L

    /** Sessize almadan önceki sistem ses seviyesi (mpv `cycle mute` davranışı). */
    private var preMuteVolume: Int? = null

    /**
     * Son eylemi tetikleyen tuş bileşimi.
     *
     * Göstergeye "hangi tuşa bastım" rozetini çizmek için tutulur; böylece kullanıcı
     * kısayolları deneyerek öğrenebilir (her tuş basımı aynı zamanda bir öğretim anı).
     */
    private var lastKeyLabel: String? = null

    /**
     * Fiziksel klavye / kol algılaması.
     *
     * `autoDetectExternalKeyboard` açıkken, yalnızca harf/sembol veya gamepad düğmesi
     * görülene kadar **D-pad ve medya tuşları** kısayol motoruna verilmez. Böylece
     * dokunmatik kullanan bir telefonda/tablette TV kumandası ok tuşları eskisi gibi
     * davranır; kullanıcı gerçek bir klavye taktığında (ilk harf basışında) kısayollar
     * anında devreye girer.
     */
    var externalInputDetected by mutableStateOf(false)
        private set

    private fun shouldHandle(info: PlayerKeyRouter.KeyInfo): Boolean {
        if (!callbacks.autoDetectExternalKeyboard) return true
        val inputClass = ExternalInputDetector.classify(
            keyCode = info.keyCode,
            source = info.source,
            deviceId = info.deviceId,
        )
        // Gamepad kapalıysa kol düğmeleri hiç sahiplenilmez.
        if (inputClass == ExternalInputDetector.Class.GAMEPAD) {
            if (!callbacks.gamepadEnabled) return false
            externalInputDetected = true
            return true
        }
        // Gerçek fiziksel klavye görüldüyse artık kısayollar kalıcı olarak etkin —
        // kullanıcı aynı oturumda klavyeye basmaya devam ettiği sürece menü
        // gezinmesiyle karışmaz (tek bir "kilit açma" olayı).
        if (inputClass == ExternalInputDetector.Class.KEYBOARD) {
            externalInputDetected = true
            return true
        }
        return externalInputDetected
    }

    fun dismissHelp() { helpVisible = false }

    /**
     * Çıkış onayını dışarıdan (ör. Esc'in son kademesi, geri tuşu) istemek için.
     *
     * Kısayol yolu da aynı bayrağı kullanır; böylece "tek tuşla oturum kaybı"
     * koruması **tek** yerde uygulanır ve iki kod yolu birbirinden ayrışmaz.
     */
    fun requestExitConfirmation() {
        helpVisible = false
        router.reset()
        exitConfirmVisible = true
    }

    fun dismissExitConfirm() { exitConfirmVisible = false }

    fun clearIndicator() { indicator = null }

    /** Göstergenin kendi kendine kapanması için gereken süre (ms). */
    fun indicatorDwellMs(): Long {
        val msg = indicator ?: return PlayerOsdDurations.NORMAL_MS
        val scale = callbacks.indicatorDurationScale.coerceIn(0.35f, 2.5f)
        return (dwellMs(msg) * scale).toLong()
    }

    /**
     * Doğrudan Android KeyEvent'i kabul eden köprü.
     */
    fun onKeyEvent(event: android.view.KeyEvent): Boolean {
        val info = PlayerKeyRouter.KeyInfo(
            keyCode = event.keyCode,
            shift = event.isShiftPressed,
            ctrl = event.isCtrlPressed,
            alt = event.isAltPressed,
            repeatCount = event.repeatCount,
            source = event.source,
            deviceId = event.deviceId
        )
        return onKey(info, isKeyDown = event.action == android.view.KeyEvent.ACTION_DOWN)
    }

    /**
     * Bir tuş olayını işler.
     *
     * @param info ham tuş bilgisi (Compose `KeyEvent` veya `android.view.KeyEvent`'ten)
     * @param isKeyDown `KeyEventType.KeyDown` eşdeğeri
     * @return `true` → olay tüketildi, üst katmana iletilmemeli.
     */
    fun onKey(info: PlayerKeyRouter.KeyInfo, isKeyDown: Boolean): Boolean {
        if (!callbacks.keyboardEnabled) return false
        if (!shouldHandle(info)) return false

        if (!isKeyDown) {
            val up = router.onKeyUp(info)
            if (up.action != null && PlayerKeyRouter.isRepeatable(up.action)) {
                // Basılı tutma bitti: birikimi sıfırla, gösterge kendi süresinde kaybolsun.
                seekAccumulatorMs = 0L
            }
            return up.consumed
        }

        // Yardım ekranı açıkken herhangi bir tuş onu kapatır (yeni bir kısayol çalıştırmaz).
        if (helpVisible) {
            helpVisible = false
            router.reset()
            return true
        }

        val outcome = router.onKeyDown(info)
        if (!outcome.consumed) return false
        // "Basılı tutarak hızlandır" kapalıysa tekrarlar yutulur: tek basış = tek adım.
        if (outcome.isRepeat && !callbacks.holdToAccelerate) return true
        val action = outcome.action ?: return true

        if (router.needsConfirmation(action)) {
            // Çıkış: tekrar basışta yutuyoruz, onay isteniyor.
            exitConfirmVisible = true
            router.reset()
            return true
        }

        lastKeyLabel = runCatching {
            PlayerKeyBinding(info.keyCode, info.shift, info.ctrl, info.alt).displayLabel()
        }.getOrNull()
        perform(action, outcome.isRepeat, outcome.repeatIndex)
        return true
    }

    /**
     * Basılı tutmada ivme. Kapalıysa her zaman 1.
     * Just Player'ın art arda basışta sabit adım atması yerine mpv'nin "basılı tut,
     * giderek hızlansın" hissini verir.
     */
    private fun repeatFactor(isRepeat: Boolean, repeatIndex: Int): Int =
        if (!isRepeat || !callbacks.holdToAccelerate) 1 else accelerationFactor(repeatIndex)

    private fun perform(action: PlayerShortcutAction, isRepeat: Boolean, repeatIndex: Int) {
        if (callbacks.opensFullOsd) callbacks.onRefreshFullOsd()
        when (action) {
            PlayerShortcutAction.PLAY_PAUSE -> {
                callbacks.onPlayPause()
                show(action, if (callbacks.isPlaying()) "Oynatılıyor" else "Duraklatıldı", big = true)
            }

            PlayerShortcutAction.SEEK_BACKWARD,
            PlayerShortcutAction.SEEK_FORWARD -> {
                val step = callbacks.seekStepMs * repeatFactor(isRepeat, repeatIndex)
                val delta = if (action == PlayerShortcutAction.SEEK_FORWARD) step else -step
                seekAccumulatorMs += delta
                callbacks.onSeekBy(delta)
                showSeek(action)
            }

            PlayerShortcutAction.SEEK_BACKWARD_LARGE,
            PlayerShortcutAction.SEEK_FORWARD_LARGE -> {
                val step = callbacks.largeSeekStepMs
                val delta = if (action == PlayerShortcutAction.SEEK_FORWARD_LARGE) step else -step
                seekAccumulatorMs += delta
                callbacks.onSeekBy(delta)
                showSeek(action)
            }

            PlayerShortcutAction.SEEK_BACKWARD_PRECISE,
            PlayerShortcutAction.SEEK_FORWARD_PRECISE -> {
                val step = callbacks.preciseSeekStepMs
                val delta = if (action == PlayerShortcutAction.SEEK_FORWARD_PRECISE) step else -step
                seekAccumulatorMs += delta
                callbacks.onSeekBy(delta)
                showSeek(action)
            }

            PlayerShortcutAction.SEEK_TO_START -> {
                callbacks.onSeekTo(0L)
                seekAccumulatorMs = 0L
                show(action, "Başa dönüldü")
            }

            PlayerShortcutAction.VOLUME_UP,
            PlayerShortcutAction.VOLUME_DOWN -> {
                val base = VOLUME_KEY_STEP * repeatFactor(isRepeat, repeatIndex)
                val step = if (action == PlayerShortcutAction.VOLUME_UP) base else -base
                val target = callbacks.volume() + step
                callbacks.onVolumeChange(target)
                showVolume()
            }

            PlayerShortcutAction.VOLUME_BOOST_UP,
            PlayerShortcutAction.VOLUME_BOOST_DOWN -> {
                if (!callbacks.volumeBoostSupported()) {
                    show(action, "Ses güçlendirme kapalı", "Ayarlardan açabilirsin")
                    return
                }
                val base = VOLUME_KEY_STEP * repeatFactor(isRepeat, repeatIndex)
                val step = if (action == PlayerShortcutAction.VOLUME_BOOST_UP) base else -base
                callbacks.onVolumeBoostChange(callbacks.mpvVolume() + step)
                showVolume()
            }

            PlayerShortcutAction.TOGGLE_MUTE -> {
                val max = callbacks.maxVolume().coerceAtLeast(1)
                val current = callbacks.volume().coerceIn(0, max)
                if (current > 0) {
                    // Sessize alırken seviyeyi hatırla: mpv `cycle mute` açarken
                    // önceki seviyeye döner, "en yükseğe" fırlatmaz.
                    preMuteVolume = current
                    callbacks.onVolumeChange(0)
                    show(action, "Sessiz", detail = "Önceki ses %${volumePercentOf(current, max)}", big = true)
                } else {
                    val restore = preMuteVolume?.coerceIn(1, max) ?: (max / 2).coerceAtLeast(1)
                    callbacks.onVolumeChange(restore)
                    show(action, "Ses açık", detail = "Ses %${volumePercentOf(restore, max)}", big = true)
                }
            }

            PlayerShortcutAction.SPEED_UP,
            PlayerShortcutAction.SPEED_DOWN -> {
                val factor = if (action == PlayerShortcutAction.SPEED_UP) SPEED_MULTIPLIER else 1f / SPEED_MULTIPLIER
                val speed = normalizeSpeed(callbacks.speed() * factor)
                callbacks.onSpeedChange(speed)
                showSpeed(speed)
            }

            PlayerShortcutAction.SPEED_HALVE -> {
                val speed = normalizeSpeed(callbacks.speed() * 0.5f)
                callbacks.onSpeedChange(speed)
                showSpeed(speed)
            }

            PlayerShortcutAction.SPEED_DOUBLE -> {
                val speed = normalizeSpeed(callbacks.speed() * 2f)
                callbacks.onSpeedChange(speed)
                showSpeed(speed)
            }

            PlayerShortcutAction.SPEED_RESET -> {
                callbacks.onSpeedChange(1f)
                showSpeed(1f)
            }

            PlayerShortcutAction.CYCLE_SUBTITLE -> {
                val result = callbacks.onCycleSubtitle()
                show(action, result ?: "Altyazı parçası yok")
            }

            PlayerShortcutAction.TOGGLE_SUBTITLE_VISIBILITY -> {
                val visible = callbacks.onToggleSubtitleVisibility()
                show(action, if (visible) "Altyazı açık" else "Altyazı kapalı")
            }

            PlayerShortcutAction.SUBTITLE_DELAY_DOWN,
            PlayerShortcutAction.SUBTITLE_DELAY_UP -> {
                if (!callbacks.subtitleDelaySupported()) {
                    show(action, "Altyazı gecikmesi desteklenmiyor", "MPV moturu gerekir")
                    return
                }
                val step = if (action == PlayerShortcutAction.SUBTITLE_DELAY_UP) SUBTITLE_DELAY_STEP_MS
                else -SUBTITLE_DELAY_STEP_MS
                val value = callbacks.onSubtitleDelayChange(step)
                val sign = if (value > 0) "+" else ""
                show(action, "Altyazı gecikmesi", "$sign${value} ms")
            }

            PlayerShortcutAction.CYCLE_AUDIO -> {
                val result = callbacks.onCycleAudio()
                show(action, result ?: "Ses parçası yok")
            }

            PlayerShortcutAction.NEXT_EPISODE -> {
                if (!callbacks.onNextEpisode()) show(action, "Sonraki bölüm yok")
                else show(action, "Sonraki bölüm")
            }

            PlayerShortcutAction.PREVIOUS_EPISODE -> {
                if (!callbacks.onPreviousEpisode()) show(action, "Önceki bölüm yok")
                else show(action, "Önceki bölüm")
            }

            PlayerShortcutAction.NEXT_CHAPTER,
            PlayerShortcutAction.PREVIOUS_CHAPTER -> {
                val label = callbacks.onChapterJump(action == PlayerShortcutAction.NEXT_CHAPTER)
                show(action, label ?: "Bölüm işareti yok")
            }

            PlayerShortcutAction.CYCLE_ASPECT -> show(action, callbacks.onCycleAspect())

            PlayerShortcutAction.TOGGLE_ORIENTATION -> show(action, callbacks.onCycleOrientation())

            PlayerShortcutAction.TOGGLE_STATS -> {
                val label = callbacks.onToggleStatistics()
                show(action, label ?: "İstatistikler MPV motorunda kullanılabilir")
            }

            PlayerShortcutAction.SCREENSHOT -> {
                callbacks.onScreenshot()
                show(action, "Ekran görüntüsü alındı")
            }

            PlayerShortcutAction.OPEN_SETTINGS -> {
                callbacks.onOpenSettings()
                // Ayarlar bilinçli olarak tam UI açar; gösterge gerekmez.
                clearIndicator()
            }

            PlayerShortcutAction.TOGGLE_HELP -> {
                helpVisible = true
                clearIndicator()
            }

            PlayerShortcutAction.TOGGLE_UI -> {
                callbacks.onToggleUi()
                clearIndicator()
            }

            PlayerShortcutAction.LOCK_TOGGLE -> {
                val locked = callbacks.onToggleLock()
                show(action, if (locked) "Kontroller kilitli" else "Kontroller açık", big = true)
            }

            PlayerShortcutAction.EXIT -> Unit // yukarıda onay istendi
        }
    }

    // ── Gösterge üreticileri ─────────────────────────────────────────────────

    private fun nextMessage(
        title: String,
        detail: String? = null,
        big: Boolean = false,
        action: PlayerShortcutAction? = null,
        progress: Float? = null,
    ) {
        if (!callbacks.showIndicators) return
        sequence += 1
        indicator = KeyboardIndicatorMessage(
            title = title,
            detail = detail,
            big = big,
            action = action,
            progress = progress,
            keyLabel = lastKeyLabel,
            seq = sequence,
        )
    }

    private fun show(action: PlayerShortcutAction, title: String, detail: String? = null, big: Boolean = false) {
        nextMessage(title = title, detail = detail, action = action, big = big)
    }

    private fun showSeek(action: PlayerShortcutAction) {
        val seconds = (abs(seekAccumulatorMs) / 1000.0).roundToInt()
        val sign = if (seekAccumulatorMs >= 0) "+" else "−"
        val direction = if (seekAccumulatorMs >= 0) "ileri" else "geri"
        val position = callbacks.positionMs()
        val duration = callbacks.durationMs()
        nextMessage(
            title = "${sign}${seconds} sn",
            detail = "$direction • ${formatClock(position)} / ${formatClock(duration)}",
            action = action,
            // İlerleme çubuğu: videonun neresinde olduğumuzu tek bakışta gösterir.
            progress = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else null,
        )
    }

    private fun volumePercentOf(volume: Int, max: Int): Int =
        if (max <= 0) 0 else ((volume.toFloat() / max) * 100).roundToInt().coerceIn(0, 100)

    private fun volumePercent(): Int {
        val max = callbacks.maxVolume().coerceAtLeast(1)
        val current = callbacks.volume()
        val boostPct = (callbacks.mpvVolume() - 100).coerceAtLeast(0)
        return if (current >= max && boostPct > 0) {
            ((current.toFloat() / max) * 100 + boostPct).roundToInt()
        } else {
            ((current.toFloat() / max) * 100).roundToInt()
        }
    }

    private fun showVolume() {
        val boostPct = (callbacks.mpvVolume() - 100).coerceAtLeast(0)
        val percent = volumePercent()
        nextMessage(
            title = "Ses %$percent",
            detail = if (boostPct > 0) "Güçlendirme +%$boostPct" else null,
            action = PlayerShortcutAction.VOLUME_UP,
            progress = (percent / 100f).coerceIn(0f, 1f),
        )
    }

    private fun showSpeed(speed: Float) {
        val text = if (abs(speed - speed.roundToInt()) < 0.001f) "${speed.roundToInt()}×" else "%.2f×".format(speed)
        nextMessage(
            title = "Hız $text",
            detail = if (abs(speed - 1f) < 0.001f) "Normal hız" else null,
            action = PlayerShortcutAction.SPEED_UP,
            // 0,25×–4× aralığında normalize edilmiş çubuk.
            progress = ((speed.coerceIn(0.25f, 4f) - 0.25f) / 3.75f),
        )
    }

    companion object {
        /** Ses tuşu adımı: donanım tuşlarıyla aynı hissi vermek için 1 kademe. */
        const val VOLUME_KEY_STEP = 1

        /** Basılı tutmada hızlanma: her tekrar için adım çarpanı (1→2→3… en fazla 6). */
        fun accelerationFactor(repeatIndex: Int): Int = (1 + repeatIndex / 3).coerceIn(1, 6)

        /** mpv `[`/`]` davranışı: ±%10 çarpımsal. */
        const val SPEED_MULTIPLIER = 1.1f

        /** mpv `z`/`Z`: ±100 ms. */
        const val SUBTITLE_DELAY_STEP_MS = 100L

        private val SPEED_STEPS = floatArrayOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 3.5f, 4f)

        /** Hızı 0,1–4× aralığına sıkıştırır; adımlara yakınsa tam adıma yuvarlar. */
        fun normalizeSpeed(raw: Float): Float {
            val clamped = raw.coerceIn(0.1f, 4f)
            val nearest = SPEED_STEPS.minByOrNull { abs(it - clamped) }
            return if (nearest != null && abs(nearest - clamped) < 0.04f) nearest else clamped
        }

        fun formatClock(ms: Long): String {
            val totalSec = (ms.coerceAtLeast(0L) / 1000).toInt()
            val h = totalSec / 3600
            val m = (totalSec % 3600) / 60
            val s = totalSec % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
        }

        /** Göstergenin ekranda kalma süresi. */
        fun dwellMs(message: KeyboardIndicatorMessage?): Long {
            val action = message?.action ?: return PlayerOsdDurations.NORMAL_MS
            return when (action) {
                PlayerShortcutAction.PLAY_PAUSE,
                PlayerShortcutAction.TOGGLE_MUTE,
                PlayerShortcutAction.LOCK_TOGGLE -> PlayerOsdDurations.PULSE_MS
                PlayerShortcutAction.SCREENSHOT -> PlayerOsdDurations.LONG_MS
                else -> PlayerOsdDurations.NORMAL_MS
            }
        }
    }
}

/**
 * Denetleyicinin oynatıcıya erişmek için kullandığı köprüler.
 *
 * Neden lambda'lar?
 * Oynatıcı durumu (altyazı listesi, görüntü oranı, yönlendirme) ekranın `remember`
 * alanlarında yaşıyor; ViewModel'e taşımak büyük bir refactor olurdu. Lambda'lar
 * sayesinde bu sınıf ne Compose'a ne de ViewModel'e bağlı; birim testine uygun ve
 * ekran her karede güncel değerleri okuyor (Compose `mutableStateOf` delegeleri
 * canlı okunur).
 */
data class PlayerKeyboardCallbacks(
    val keyboardEnabled: Boolean = true,
    val showIndicators: Boolean = true,
    /** Ayar: gösterge süresi çarpanı (0,35× … 2,5×). */
    val indicatorDurationScale: Float = 1f,
    /** Ayar: harici klavye/kol otomatik algılansın mı? */
    val autoDetectExternalKeyboard: Boolean = true,
    /** Ayar: gamepad düğmeleri kısayol tablosuna dahil mi? */
    val gamepadEnabled: Boolean = true,

    /**
     * Kullanıcı "Klavye tam arayüzü açsın" seçeneğini açtıysa `true`.
     * Eski davranışı geri getirmek isteyenler için: her kısayolda tam OSD tazelenir.
     */
    val opensFullOsd: Boolean = false,
    /** Tam OSD'yi tazele (yalnızca [opensFullOsd] açıkken çağrılır). */
    val onRefreshFullOsd: () -> Unit = {},
    /** Basılı tutunca hızlanma (Just Player davranışı). Kapalıysa tekrar yutulur. */
    val holdToAccelerate: Boolean = true,

    // Arama
    val seekStepMs: Long = 5_000L,
    val largeSeekStepMs: Long = 60_000L,
    val preciseSeekStepMs: Long = 1_000L,
    val positionMs: () -> Long = { 0L },
    val durationMs: () -> Long = { 0L },
    val onSeekBy: (Long) -> Unit = {},
    val onSeekTo: (Long) -> Unit = {},

    // Oynatma
    val isPlaying: () -> Boolean = { false },
    val onPlayPause: () -> Unit = {},

    // Ses
    val maxVolume: () -> Int = { 1 },
    val volume: () -> Int = { 0 },
    val mpvVolume: () -> Int = { 100 },
    val onVolumeChange: (Int) -> Unit = {},
    val onVolumeBoostChange: (Int) -> Unit = {},
    val volumeBoostSupported: () -> Boolean = { false },

    // Hız
    val speed: () -> Float = { 1f },
    val onSpeedChange: (Float) -> Unit = {},

    // Parçalar
    val onCycleSubtitle: () -> String? = { null },
    val onToggleSubtitleVisibility: () -> Boolean = { false },
    val subtitleDelaySupported: () -> Boolean = { false },
    /** @return güncel gecikme (ms) */
    val onSubtitleDelayChange: (Long) -> Long = { 0L },
    val onCycleAudio: () -> String? = { null },

    // Bölümler
    val onNextEpisode: () -> Boolean = { false },
    val onPreviousEpisode: () -> Boolean = { false },
    /** @return atlanan işaretin adı; `null` = işaret yok */
    val onChapterJump: (Boolean) -> String? = { null },

    // Görünüm
    val onCycleAspect: () -> String = { "" },
    val onCycleOrientation: () -> String = { "" },
    /** @return istatistik sayfası adı; `null` = motor desteklemiyor */
    val onToggleStatistics: () -> String? = { null },
    val onScreenshot: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onToggleUi: () -> Unit = {},
    /** @return kilitli mi */
    val onToggleLock: () -> Boolean = { false },
)

/**
 * Kısayol eylemine karşılık gelen ikon.
 *
 * Tek bir haritada tutulur ki yardım ekranı ve gösterge aynı görsel dili kullansın.
 * `material-icons-extended` zaten bağımlılıkta olduğu için ek varlık gerekmiyor.
 */
fun keyboardActionIcon(action: PlayerShortcutAction?): androidx.compose.ui.graphics.vector.ImageVector {
    val icons = com.kitsugi.animelist.ui.icons.KitsugiIcons
    return when (action) {
        PlayerShortcutAction.PLAY_PAUSE -> icons.PlayArrow
        PlayerShortcutAction.SEEK_BACKWARD, PlayerShortcutAction.SEEK_BACKWARD_LARGE ->
            icons.FastRewind
        PlayerShortcutAction.SEEK_FORWARD, PlayerShortcutAction.SEEK_FORWARD_LARGE ->
            icons.FastForward
        PlayerShortcutAction.SEEK_BACKWARD_PRECISE -> icons.ChevronLeft
        PlayerShortcutAction.SEEK_FORWARD_PRECISE -> icons.ChevronRight
        PlayerShortcutAction.SEEK_TO_START -> icons.RestartAlt
        PlayerShortcutAction.VOLUME_UP, PlayerShortcutAction.VOLUME_BOOST_UP,
        PlayerShortcutAction.VOLUME_DOWN, PlayerShortcutAction.VOLUME_BOOST_DOWN -> icons.VolumeUp
        PlayerShortcutAction.TOGGLE_MUTE -> icons.VolumeOff
        PlayerShortcutAction.SPEED_UP, PlayerShortcutAction.SPEED_DOWN, PlayerShortcutAction.SPEED_RESET,
        PlayerShortcutAction.SPEED_HALVE, PlayerShortcutAction.SPEED_DOUBLE -> icons.Speed
        PlayerShortcutAction.CYCLE_SUBTITLE -> icons.Subtitles
        PlayerShortcutAction.TOGGLE_SUBTITLE_VISIBILITY -> icons.VisibilityOff
        PlayerShortcutAction.SUBTITLE_DELAY_DOWN, PlayerShortcutAction.SUBTITLE_DELAY_UP -> icons.Timer
        PlayerShortcutAction.CYCLE_AUDIO -> icons.Audiotrack
        PlayerShortcutAction.NEXT_EPISODE -> icons.SkipNext
        PlayerShortcutAction.PREVIOUS_EPISODE -> icons.SkipPrevious
        PlayerShortcutAction.NEXT_CHAPTER, PlayerShortcutAction.PREVIOUS_CHAPTER -> icons.Bookmarks
        PlayerShortcutAction.CYCLE_ASPECT -> icons.AspectRatio
        PlayerShortcutAction.TOGGLE_ORIENTATION -> icons.ScreenRotation
        PlayerShortcutAction.TOGGLE_STATS -> icons.BarChart
        PlayerShortcutAction.SCREENSHOT -> icons.ControlCamera
        PlayerShortcutAction.OPEN_SETTINGS -> icons.Settings
        PlayerShortcutAction.TOGGLE_HELP -> icons.Keyboard
        PlayerShortcutAction.TOGGLE_UI -> icons.Visibility
        PlayerShortcutAction.LOCK_TOGGLE -> icons.Lock
        PlayerShortcutAction.EXIT -> icons.Close
        null -> icons.Keyboard
    }
}
