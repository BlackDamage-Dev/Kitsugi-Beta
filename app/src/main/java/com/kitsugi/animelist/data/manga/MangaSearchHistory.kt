package com.kitsugi.animelist.data.manga

import android.content.Context

/**
 * Manga arama geçmişi.
 *
 * Neden?
 * -----
 * Kullanıcı aynı mangayı birkaç gün arayla tekrar arıyor ("one piece", "solo
 * leveling"…). Her seferinde klavyeden yazmak yerine son aramalar tek dokunuşla
 * tekrar çalıştırılabilsin diye en son aramalar cihazda saklanır.
 *
 * Notlar
 * ------
 *  • Aynı sorgu (büyük/küçük harf duyarsız) tekrar eklenmez, en başa taşınır.
 *  • Liste [MAX_ITEMS] ile sınırlı; en eski otomatik düşer.
 *  • Tek bir `SharedPreferences` anahtarında `\u001F` ayırıcısıyla tutulur —
 *    JSON bağımlılığı yok, okuma maliyeti yok.
 */
class MangaSearchHistory(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** En yeni arama başta olacak şekilde son aramalar. */
    fun recent(): List<String> = read()

    /** Sorguyu geçmişe ekler (varsa en başa taşır). 2 karakterden kısa sorgu yok sayılır. */
    fun add(query: String): List<String> {
        val value = query.trim()
        if (value.length < 2) return read()

        val updated = ArrayList<String>(MAX_ITEMS)
        updated += value
        read().forEach { existing ->
            if (updated.size < MAX_ITEMS && !existing.equals(value, ignoreCase = true)) {
                updated += existing
            }
        }
        write(updated)
        return updated
    }

    fun remove(query: String): List<String> {
        val updated = read().filterNot { it.equals(query, ignoreCase = true) }
        write(updated)
        return updated
    }

    fun clear(): List<String> {
        write(emptyList())
        return emptyList()
    }

    private fun read(): List<String> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split(SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .take(MAX_ITEMS)
    }

    private fun write(items: List<String>) {
        prefs.edit()
            .putString(KEY_ITEMS, items.joinToString(SEPARATOR))
            .apply()
    }

    companion object {
        private const val FILE_NAME = "kitsugi_manga_search_history"
        private const val KEY_ITEMS = "items_v1"
        private const val SEPARATOR = "\u001F"
        private const val MAX_ITEMS = 12

        @Volatile
        private var instance: MangaSearchHistory? = null

        fun get(context: Context): MangaSearchHistory =
            instance ?: synchronized(this) {
                instance ?: MangaSearchHistory(context).also { instance = it }
            }
    }
}
