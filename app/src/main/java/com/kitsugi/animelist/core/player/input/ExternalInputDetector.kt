package com.kitsugi.animelist.core.player.input

import android.view.InputDevice
import android.view.KeyEvent

/**
 * Harici giriş aygıtı (fiziksel klavye / gamepad) algılaması.
 *
 * Neden gerekli?
 * ─────────────
 * Kitsugi hem telefon/tablette (dokunmatik + TV kumandası olası) hem de TV kutularında
 * (yalnızca kumanda) çalışıyor. Kısayollar her yerde etkin olsaydı:
 *
 *  - **TV kumandasında** ok tuşlarına basmak arama yapardı; kullanıcı ise menüde
 *    gezinmek isterdi. (Bu, kullanıcıların en çok şikâyet ettiği tuş çakışması.)
 *  - **Tablet + Bluetooth klavye** durumunda ise kısayollar aktif olmalı.
 *
 * Bu yüzden "tuş bir eyleme bağlı mı?" sorusundan ÖNCE "bu tuş hangi aygıttan geldi?"
 * sorusu soruluyor. Android bunu iki şekilde söyler:
 *
 *  1. `KeyEvent.getSource()` → `InputDevice.SOURCE_KEYBOARD`, `SOURCE_DPAD`,
 *     `SOURCE_GAMEPAD`, `SOURCE_JOYSTICK` … (bit maskesi).
 *  2. `InputDevice.getDevice(deviceId).keyboardType` → `KEYBOARD_NOKEYS` olan bir
 *     kumanda gerçek klavye değildir.
 *
 * Ek olarak harf/rakam/sembol tuş kodları yalnızca **gerçek bir klavyeden** gelebilir;
 * kumandalarda bu tuşlar yoktur. Bu yüzden tek başına alfanümerik bir tuş görmek
 * fiziksel klavye kanıtı sayılır (mpv-android'in `MPVView` yaklaşımı da böyle).
 */
object ExternalInputDetector {

    /** Tuş olayı bir gamepad/joystick'ten mi geldi? */
    fun isFromGamepad(source: Int, keyCode: Int): Boolean {
        val sourceGamepad = source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
        val sourceJoystick = source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        if (sourceGamepad || sourceJoystick) return true
        // Bazı kollar `source` bilgisini doğru raporlamaz; tuş kodundan anlarız.
        return PlayerKeyBinding(keyCode).isGamepadKey
    }

    /**
     * Tuş olayı **gerçek** bir fiziksel klavyeden mi geldi?
     *
     * @param keyCode ham tuş kodu
     * @param source `KeyEvent.source`
     * @param deviceId `KeyEvent.deviceId` (cihaz bilgisi için)
     */
    fun isFromPhysicalKeyboard(keyCode: Int, source: Int, deviceId: Int): Boolean {
        // 1) Alfa-nümerik tuş: kumandada bulunmaz, kesin klavye kanıtı.
        if (PlayerKeyBinding(keyCode).isAlphaNumericKey) return true

        // 2) `source` bilgisi klavye diyorsa ve D-pad/kumanda *değilse* klavye say.
        val isKeyboardSource = source and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD
        val isDpadSource = source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD
        val isTouchSource = source and InputDevice.SOURCE_TOUCHSCREEN == InputDevice.SOURCE_TOUCHSCREEN
        if (isKeyboardSource && !isDpadSource && !isTouchSource) return true

        // 3) Cihazın kendi raporuna bak: tuş takımı olmayan cihaz (TV kumandası) klavye değildir.
        val device = runCatching { InputDevice.getDevice(deviceId) }.getOrNull() ?: return false
        if (device.isVirtual) {
            // Sanal klavyeler (erişilebilirlik, test otomasyonu) oynatıcıda gerçek
            // kısayol kullanımına işaret etmez; D-pad davranışını bozmayalım.
            return false
        }
        val hasRealKeypad = device.keyboardType != InputDevice.KEYBOARD_TYPE_NONE
        return hasRealKeypad && !isDpadSource
    }

    /**
     * Bir tuş olayını tek çağrıda sınıflandırır.
     * @return `KEYBOARD`, `GAMEPAD` veya `OTHER` (kumanda/dokunmatik/oynatıcı dışı)
     */
    fun classify(keyCode: Int, source: Int, deviceId: Int): Class {
        if (isFromGamepad(source, keyCode)) return Class.GAMEPAD
        if (isFromPhysicalKeyboard(keyCode, source, deviceId)) return Class.KEYBOARD
        return Class.OTHER
    }

    /** Android `KeyEvent` kısayolu. */
    fun classify(event: KeyEvent): Class =
        classify(event.keyCode, event.source, event.deviceId)

    enum class Class { KEYBOARD, GAMEPAD, OTHER }
}
