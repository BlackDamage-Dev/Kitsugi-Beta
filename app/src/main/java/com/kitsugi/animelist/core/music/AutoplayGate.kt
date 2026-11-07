package com.kitsugi.animelist.core.music

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.kitsugi.animelist.data.settings.AppSettings

/**
 * 🚦 Otomatik çalma kapısı
 *
 * Okuyucu açıldığında ortam sesinin kendiliğinden başlaması, kullanıcının
 * beklemediği bir anda hoparlörden ses çıkması demek olabilir. Bu yüzden
 * otomatik başlatma iki koşula bağlanabilir:
 *
 *  - `musicAutoPlayHeadphonesOnly` → yalnızca kablolu/Bluetooth/USB
 *    kulaklık takılıyken başlat
 *  - `musicAutoPlayMuteOnSilentDevice` → cihaz sessiz/titreşim modundayken
 *    başlatma
 *
 * ⚠️ Bu iki ayar `AppSettings` içinde **tanımlıydı ama hiçbir yerde
 * okunmuyordu**: kullanıcı anahtarı açıp kapatıyor, hiçbir şey
 * değişmiyordu. Artık gerçekten uygulanıyor.
 */
object AutoplayGate {

    /**
     * Otomatik çalma şu anda başlatılabilir mi?
     *
     * @param settings okuyucunun anlık ayarları
     * @param force `true` ise kapı atlanır (kullanıcı elle "çal" dediğinde
     *        kullanılmaz; yalnızca otomatik başlatma için geçerlidir)
     */
    fun canAutoplay(context: Context, settings: AppSettings, force: Boolean = false): Boolean {
        if (force) return true
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return true

        if (settings.musicAutoPlayMuteOnSilentDevice &&
            audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL
        ) {
            return false
        }

        if (settings.musicAutoPlayHeadphonesOnly && !hasPrivateOutput(audioManager)) {
            return false
        }

        return true
    }

    /**
     * Kullanıcının sesi **yalnızca kendisinin** duyacağı bir çıkış var mı?
     *
     * Kablolu kulaklık, kulaklık+ mikrofon, Bluetooth A2DP/SCO, BLE, USB
     * kulaklık ve işitme cihazı çıkışları "özel" sayılır. Hoparlör,
     * televizyon ve dock çıkışları sayılmaz.
     */
    fun hasPrivateOutput(audioManager: AudioManager): Boolean {
        val devices = runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        }.getOrDefault(emptyArray())

        return devices.any { device -> isPrivateOutput(device.type) }
    }

    /**
     * Çıkış türü "yalnızca kullanıcının duyacağı" bir çıkış mı?
     *
     * `TYPE_BLE_*` sabitleri API 31'de geldiği için ayrı ve sürüm korumalı
     * bir yardımcıya alındı (minSdk 26).
     */
    private fun isPrivateOutput(type: Int): Boolean = when (type) {
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_HEARING_AID -> true
        else -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isBleOutput(type)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    private fun isBleOutput(type: Int): Boolean =
        type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER
}
