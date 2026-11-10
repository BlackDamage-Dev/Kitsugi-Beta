package com.kitsugi.animelist.data.manga

import android.content.Context

/**
 * Manga bazında **çevirmen (scanlator) tercihi** deposu.
 *
 * Kaynaklar aynı bölümü birden çok çevirmen üzerinden sunabildiği için
 * ([MangaChapterGrouping]) kullanıcının hangi çevirmenle devam etmek istediği
 * hatırlanır:
 *
 *  • [preferredScanlator] — bu manga için son seçilen çevirmen (bölüm geçişinde
 *    ön seçim olarak kullanılır),
 *  • [groupChoice] — belirli bir bölüm için yapılan açık seçim (detay ekranında
 *    "seçili" olarak işaretlenir),
 *  • [autoContinue] — açıkken çok çevirmenli bölümlerde soru sorulmadan
 *    hatırlanan çevirmenle devam edilir.
 *
 * DataStore yerine SharedPreferences: bu değerler okuyucu ilk karesini
 * çizerken senkron okunuyor (bkz. [MangaReaderPreferences] ile aynı gerekçe).
 */
class MangaScanlatorPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** Bu manga için son kullanılan çevirmen (yoksa null). */
    fun preferredScanlator(mangaUrl: String): String? =
        prefs.getString(keyPreferred(mangaUrl), null)?.takeIf { it.isNotBlank() }

    fun setPreferredScanlator(mangaUrl: String, scanlator: String?) {
        val value = scanlator?.trim()?.takeIf { it.isNotEmpty() }
        prefs.edit().apply {
            if (value == null) remove(keyPreferred(mangaUrl)) else putString(keyPreferred(mangaUrl), value)
        }.apply()
    }

    fun clearPreferredScanlator(mangaUrl: String) {
        prefs.edit().remove(keyPreferred(mangaUrl)).apply()
    }

    /** Belirli bir bölüm grubu için yapılan açık çevirmen seçimi. */
    fun groupChoice(mangaUrl: String, groupKey: String): String? =
        prefs.getString(keyGroup(mangaUrl, groupKey), null)?.takeIf { it.isNotBlank() }

    fun setGroupChoice(mangaUrl: String, groupKey: String, scanlator: String?) {
        val value = scanlator?.trim()?.takeIf { it.isNotEmpty() }
        prefs.edit().apply {
            if (value == null) {
                remove(keyGroup(mangaUrl, groupKey))
            } else {
                putString(keyGroup(mangaUrl, groupKey), value)
            }
        }.apply()
    }

    /** Çok çevirmenli bölümlerde soru sormadan hatırlanan çevirmenle devam et. */
    fun autoContinue(mangaUrl: String): Boolean =
        prefs.getBoolean(keyAuto(mangaUrl), false)

    fun setAutoContinue(mangaUrl: String, enabled: Boolean) {
        prefs.edit().putBoolean(keyAuto(mangaUrl), enabled).apply()
    }

    private fun keyPreferred(mangaUrl: String) = "pref::$mangaUrl"
    private fun keyAuto(mangaUrl: String) = "auto::$mangaUrl"
    private fun keyGroup(mangaUrl: String, groupKey: String) = "group::${mangaUrl}::$groupKey"

    companion object {
        private const val FILE_NAME = "kitsugi_manga_scanlator_prefs"

        @Volatile
        private var instance: MangaScanlatorPreferences? = null

        fun get(context: Context): MangaScanlatorPreferences =
            instance ?: synchronized(this) {
                instance ?: MangaScanlatorPreferences(context).also { instance = it }
            }
    }
}
