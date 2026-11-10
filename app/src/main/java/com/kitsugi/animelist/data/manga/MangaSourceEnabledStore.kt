package com.kitsugi.animelist.data.manga

import android.content.Context

/**
 * Kullanıcının "bu kaynak aktif mi?" tercihi.
 *
 * GEÇMİŞTEKİ HATA: aktiflik bilgisi sağlık durumuna (`SourceHealthStatus.Disabled`)
 * yazılıyordu. Bu yüzden:
 *   - bir kere `Disabled` yazıldı mı kaynak arama listesinden tamamen kayboluyor,
 *   - tekrar açacak bir anahtar telefon arayüzünde hiç yoktu,
 *   - eklenti güncelleme / yeniden yükleme stale `Disabled` değerini temizlemiyordu,
 *   - "sağlık raporu" ile "kullanıcı tercihi" aynı alanda çakışıyordu.
 *
 * Artık aktiflik ayrı bir SharedPreferences dosyasında tutulur. Varsayılan her zaman
 * `true`'dur: yeni kurulan eklenti ASLA devre dışı gelmez. Sağlık durumu sadece
 * teşhis verisidir; kullanıcı bir kaynağı kapatınca `Disabled` yazılmaz.
 */
class MangaSourceEnabledStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(source: MangaSource): Boolean = isEnabled(source.stableSourceKey())

    fun isEnabled(sourceKey: String): Boolean = prefs.getBoolean(sourceKey, true)

    /** Daha önce bu kaynak için açıkça bir tercih kaydedilmiş mi? (migrasyon için) */
    fun hasExplicitPreference(source: MangaSource): Boolean =
        prefs.contains(source.stableSourceKey())

    fun setEnabled(source: MangaSource, enabled: Boolean) {
        prefs.edit().putBoolean(source.stableSourceKey(), enabled).apply()
    }

    /**
     * Eski sürümlerde `Disabled` sağlık durumu "kapalı" anlamına geliyordu.
     * Açık bir kullanıcı tercihi yoksa kaynağı aktif kabul et ve eski bayrağı temizle.
     */
    fun ensureExplicitDefault(source: MangaSource): Boolean {
        val key = source.stableSourceKey()
        if (prefs.contains(key)) return false
        prefs.edit().putBoolean(key, true).apply()
        return true
    }

    private companion object {
        const val PREFS_NAME = "manga_source_enabled"
    }
}
