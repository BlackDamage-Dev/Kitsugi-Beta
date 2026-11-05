package com.kitsugi.animelist.data.manga

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * MangaExtensionPrefs
 *
 * Manga eklentilerinin **eklenti başına** tercihlerini saklar. Video (Cloudstream)
 * eklentilerinde her eklenti için ayrı ayrı verilebilen "otomatik güncelleme"
 * kontrolünün manga tarafındaki karşılığıdır.
 *
 * Saklanan veriler:
 *  - `auto_update_enabled`      → global ana anahtar (varsayılan: açık)
 *  - `auto_update_disabled_pkgs`→ otomatik güncellemesi kapatılmış paket adları
 *
 * Neden SharedPreferences? [MangaExtensionAutoUpdater] uygulama açılışında,
 * UI'dan bağımsız olarak (hatta süreç arka planda uyanmışken) çalışır; bu yüzden
 * ayarların DataStore akışına değil, senkron okunabilen hafif bir depoya yazılması
 * gerekir. `manga_auto_updater` prefs dosyasından ayrı tutulur ki güncelleme
 * istatistikleri ile kullanıcı tercihleri birbirine karışmasın.
 */
object MangaExtensionPrefs {

    private const val TAG = "MangaExtensionPrefs"
    private const val PREFS_NAME = "manga_extension_prefs"
    private const val KEY_AUTO_UPDATE_ENABLED = "auto_update_enabled"
    private const val KEY_AUTO_UPDATE_DISABLED_PKGS = "auto_update_disabled_pkgs"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ─── Global ana anahtar ──────────────────────────────────────────────────

    /** Tüm manga eklentileri için otomatik güncelleme açık mı? (varsayılan: açık) */
    fun isAutoUpdateEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_UPDATE_ENABLED, true)

    /** Global ana anahtarı değiştirir. Kapatıldığında hiçbir eklenti otomatik güncellenmez. */
    fun setAutoUpdateEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_UPDATE_ENABLED, enabled).apply()
        Log.d(TAG, "Global otomatik güncelleme: $enabled")
    }

    // ─── Eklenti başına anahtar ──────────────────────────────────────────────

    /**
     * Tek bir eklenti (paket adı) için otomatik güncelleme açık mı?
     * Global anahtar kapalıysa her zaman `false` döner.
     */
    fun isAutoUpdateEnabled(context: Context, pkg: String): Boolean {
        if (!isAutoUpdateEnabled(context)) return false
        if (pkg.isBlank()) return true
        return !disabledPackages(context).contains(normalize(pkg))
    }

    /**
     * Tek bir eklenti için otomatik güncellemeyi açar/kapatır.
     *
     * Not: global anahtar kapalıyken eklenti anahtarını açmak, kullanıcının
     * niyetini kaybetmemek için yine de saklanır (global anahtar açıldığında
     * tercih hatırlanır).
     */
    fun setAutoUpdateEnabled(context: Context, pkg: String, enabled: Boolean) {
        if (pkg.isBlank()) return
        val key = normalize(pkg)
        val current = disabledPackages(context).toMutableSet()
        if (enabled) current.remove(key) else current.add(key)
        prefs(context).edit().putStringSet(KEY_AUTO_UPDATE_DISABLED_PKGS, current).apply()
        Log.d(TAG, "Eklenti otomatik güncelleme ($pkg): $enabled")
    }

    /**
     * Eklenti anahtarının **kullanıcı tercihi** olarak durumu (global ana anahtardan
     * bağımsız). Ayarlar ekranındaki "Oto" çipi bunu gösterir; böylece global anahtar
     * kapalıyken bile kullanıcı eklenti bazlı tercihini görebilir/değiştirebilir.
     */
    fun isExtensionSwitchOn(context: Context, pkg: String): Boolean {
        if (pkg.isBlank()) return true
        return !disabledPackages(context).contains(normalize(pkg))
    }

    /** Otomatik güncellemesi kapatılmış tüm paket adları. */
    fun disabledPackages(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_AUTO_UPDATE_DISABLED_PKGS, emptySet())?.toSet() ?: emptySet()

    /** Kullanıcı tüm eklenti anahtarlarını varsayılana (açık) döndürür. */
    fun resetPerExtensionSwitches(context: Context) {
        prefs(context).edit().remove(KEY_AUTO_UPDATE_DISABLED_PKGS).apply()
    }

    private fun normalize(pkg: String): String = pkg.trim().replace(".", "_").lowercase()
}
