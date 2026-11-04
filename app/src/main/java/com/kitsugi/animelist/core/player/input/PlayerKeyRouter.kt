package com.kitsugi.animelist.core.player.input

import android.os.SystemClock

/**
 * Tuş olaylarını Kitsugi eylemlerine çeviren, basılı tutma (auto-repeat) mantığını
 * yöneten saf yönlendirici.
 *
 * Neden ayrı bir sınıf?
 * ─────────────────────
 * Aynı mantık üç yerde gerekiyordu: Compose `onPreviewKeyEvent`, Activity
 * `dispatchKeyEvent` (PiP / odak dışı durumlar) ve gelecekte eklenecek uzaktan
 * kumanda katmanı. Mantık Composable içine gömülünce ne test edilebiliyor ne de
 * tekrar kullanılabiliyordu (bkz. eski `KitsugiFullscreenPlayerScreen` içindeki
 * iç içe `when` blokları).
 *
 * Basılı tutma davranışı (mpv-android + Just Player karışımı)
 * ───────────────────────────────────────────────────────────
 *  - İlk basış her zaman tek bir eylem üretir.
 *  - Yalnızca "tekrarlanabilir" eylemler (arama, ses, hız, altyazı gecikmesi)
 *    otomatik tekrardan etkilenir. Oynat/duraklat'ı basılı tutmak oynatıcıyı
 *    titretmez — mpv-android'in "olayı yut ama yok say" davranışı.
 *  - Tekrarlar [MIN_REPEAT_INTERVAL_MS]'den hızlı gelirse yutulur; bazı TV
 *    kutuları ve klavye sürücüleri çok agresif tekrar üretiyor.
 *  - Tekrar sayısı art arda birikir; arayüz tek bir göstergede toplam süreyi
 *    gösterir (Just Player'ın `keySeekStart` birikimi).
 */
class PlayerKeyRouter(
    private val bindingsProvider: () -> Map<PlayerKeyBinding, PlayerShortcutAction>,
    private val clockMs: () -> Long = { SystemClock.elapsedRealtime() },
) {

    /** Yönlendiricinin ham tuş bilgisi — Compose ve View katmanlarının ortak dili. */
    data class KeyInfo(
        val keyCode: Int,
        val shift: Boolean = false,
        val ctrl: Boolean = false,
        val alt: Boolean = false,
        val repeatCount: Int = 0,
        /** `KeyEvent.source` — giriş aygıtı türünü ayırt etmek için (klavye/kumanda/kol). */
        val source: Int = 0,
        /** `KeyEvent.deviceId` — cihazın gerçek klavye olup olmadığını sorgulamak için. */
        val deviceId: Int = -1,
    )

    /**
     * @param consumed olay sahiplenildi mi? `false` ise üst katman olayı normal
     *        akışa bırakır (ör. odak gezinmesi).
     * @param action tetiklenen eylem; tekrarda da aynı eylem döner.
     * @param repeatIndex basılı tutmada kaçıncı tekrar (0 = ilk basış).
     * @param isRepeat ilk basış mı, basılı tutma tekrarı mı?
     */
    data class Outcome(
        val consumed: Boolean,
        val action: PlayerShortcutAction? = null,
        val repeatIndex: Int = 0,
        val isRepeat: Boolean = false,
    ) {
        companion object {
            val NotConsumed = Outcome(consumed = false)
            fun consumedOnly() = Outcome(consumed = true)
        }
    }

    private data class Hold(
        val action: PlayerShortcutAction,
        var lastEventMs: Long,
        var repeats: Int = 0,
        var lastRepeatAcceptedMs: Long = 0L,
    )

    /** Şu anda basılı tutulan eylemler (aynı anda birden çok tuş olabilir). */
    private val holds = LinkedHashMap<PlayerShortcutAction, Hold>()

    val isAnyKeyHeld: Boolean get() = holds.isNotEmpty()

    fun isHeld(action: PlayerShortcutAction): Boolean = holds.containsKey(action)

    /** Ekrandan çıkarken / panel açılırken basılı durumları temizlemek için. */
    fun reset() = holds.clear()

    fun onKeyDown(info: KeyInfo): Outcome {
        val action = resolve(info) ?: return Outcome.NotConsumed
        if (action == PlayerShortcutAction.EXIT) {
            // Çıkış tekrarında ikinci kez onay istemeyelim; tekrarı yutuyoruz.
            if (info.repeatCount > 0) return Outcome.consumedOnly()
        }

        if (info.repeatCount == 0) {
            holds[action] = Hold(
                action = action,
                lastEventMs = clockMs(),
                repeats = 0,
                lastRepeatAcceptedMs = 0L,
            )
            return Outcome(consumed = true, action = action, repeatIndex = 0, isRepeat = false)
        }

        val hold = holds.getOrPut(action) {
            Hold(action = action, lastEventMs = clockMs(), repeats = 0, lastRepeatAcceptedMs = 0L)
        }
        val now = clockMs()
        hold.lastEventMs = now

        if (!isRepeatable(action)) {
            // Basılı tutma bu eylem için anlamsız (oynat/duraklat titremesin).
            return Outcome.consumedOnly()
        }
        if (hold.lastRepeatAcceptedMs != 0L && now - hold.lastRepeatAcceptedMs < MIN_REPEAT_INTERVAL_MS) {
            return Outcome.consumedOnly()
        }
        hold.lastRepeatAcceptedMs = now
        hold.repeats += 1
        return Outcome(consumed = true, action = action, repeatIndex = hold.repeats, isRepeat = true)
    }

    /**
     * Tuş bırakıldığında basılı durumu temizler.
     *
     * @return bırakılan tuş bir eyleme bağlıysa `consumed = true`; [action] bırakılan
     *         eylemdir (ör. uzun basılan ses tuşu için son değeri sabitlemek gerekirse).
     */
    fun onKeyUp(info: KeyInfo): Outcome {
        val action = resolve(info) ?: return Outcome.NotConsumed
        val removed = holds.remove(action)
        return Outcome(
            consumed = true,
            action = action,
            repeatIndex = removed?.repeats ?: 0,
            isRepeat = false,
        )
    }

    /** Bir olayın hangi eyleme karşılık geldiğini bulur (basılı durumu değiştirmeden). */
    fun resolve(info: KeyInfo): PlayerShortcutAction? {
        val table = bindingsProvider()
        // Tam eşleşme (değiştiriciler dahil) her zaman önce gelir.
        table[PlayerKeyBinding(info.keyCode, info.shift, info.ctrl, info.alt)]?.let { return it }
        // Değiştirici basılı olması eşleşmeyi ENGELLEMEZ: kullanıcı Shift'e basılı
        // tutarken ok tuşuna basarsa ve Shift kombinasyonu tanımlı değilse, temel
        // bağlamaya düşeriz (mpv davranışı: tanımsız kombinasyon temel tuşa düşer).
        if (info.shift || info.ctrl || info.alt) {
            table[PlayerKeyBinding(info.keyCode)]?.let { return it }
        }
        return null
    }

    /** Çıkış gibi yıkıcı eylemlerde tek basış yetmez; arayüz onay ister. */
    fun needsConfirmation(action: PlayerShortcutAction): Boolean =
        PlayerShortcutMap.requiresConfirmation(action)

    companion object {
        /** Bazı klavye/TV sürücüleri 10–20 ms'de bir tekrar gönderir; süzüyoruz. */
        const val MIN_REPEAT_INTERVAL_MS = 45L

        private val REPEATABLE = setOf(
            PlayerShortcutAction.SEEK_BACKWARD,
            PlayerShortcutAction.SEEK_FORWARD,
            PlayerShortcutAction.SEEK_BACKWARD_LARGE,
            PlayerShortcutAction.SEEK_FORWARD_LARGE,
            PlayerShortcutAction.SEEK_BACKWARD_PRECISE,
            PlayerShortcutAction.SEEK_FORWARD_PRECISE,
            PlayerShortcutAction.VOLUME_UP,
            PlayerShortcutAction.VOLUME_DOWN,
            PlayerShortcutAction.VOLUME_BOOST_UP,
            PlayerShortcutAction.VOLUME_BOOST_DOWN,
            PlayerShortcutAction.SPEED_UP,
            PlayerShortcutAction.SPEED_DOWN,
            PlayerShortcutAction.SUBTITLE_DELAY_DOWN,
            PlayerShortcutAction.SUBTITLE_DELAY_UP,
        )

        fun isRepeatable(action: PlayerShortcutAction): Boolean = action in REPEATABLE
    }
}
