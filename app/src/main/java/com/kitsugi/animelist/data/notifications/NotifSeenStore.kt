package com.kitsugi.animelist.data.notifications

import android.content.Context
import org.json.JSONObject

/**
 * "Bu bildirimi gördüm" defteri — sunucuda okundu durumu OLMAYAN kaynaklar için.
 *
 * Neden gerekli?
 *   • MyAnimeList mobil API'si, Simkl, Kitsu ve Bangumi bildirimlerini biz üretiyoruz;
 *     sunucuda "okundu" diye bir alan yok. Bu yüzden bu kaynaklarda "yeni" rozeti
 *     hiçbir zaman düşmüyor ve **"Okunmamış" sekmesi boşuna duruyor**du.
 *   • Shikimori'de sunucu `read` alanı veriyor ama uygulamada okundu işaretleme
 *     yapılmadığı için sayaç hiç azalmıyordu; kullanıcı bildirimi açtığında yerel
 *     olarak "görüldü" işaretlenir.
 *
 * Tasarım
 *   • Kaynak başına `id → epochMs` haritası, `filesDir` yerine SharedPreferences
 *     (`kitsugi_notif_seen`): okuma/yazma senkron ve ucuz.
 *   • Kaynak başına [MAX_PER_SOURCE] kayıt; en eski kayıt atılır.
 *   • Bildirim arşivi (`NotificationArchiveStore`) eski kayıtları koruduğu için defter
 *     budandıkça eski kayıtlar "yeni" görünebilir; bu yüzden yalnızca **son 72 saat**
 *     içindeki kayıtlar "yeni" sayılır (bkz. [isRecentForBadge]).
 */
object NotifSeenStore {

    private const val PREFS = "kitsugi_notif_seen"
    private const val MAX_PER_SOURCE = 600

    /** Rozet için "yeni" sayılan azami yaş. */
    const val RECENT_WINDOW_MS = 72 * 60 * 60 * 1000L

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun read(context: Context, source: String): MutableMap<String, Long> {
        val raw = prefs(context).getString(source, null) ?: return mutableMapOf()
        val map = LinkedHashMap<String, Long>()
        runCatching {
            val json = JSONObject(raw)
            json.keys().forEach { key -> map[key] = json.optLong(key, 0L) }
        }
        return map
    }

    private fun write(context: Context, source: String, map: Map<String, Long>) {
        val kept = map.entries.sortedByDescending { it.value }.take(MAX_PER_SOURCE)
        val json = JSONObject()
        kept.forEach { (key, ts) -> json.put(key, ts) }
        prefs(context).edit().putString(source, json.toString()).apply()
    }

    fun isSeen(context: Context, source: String, id: String): Boolean =
        read(context, source).containsKey(id)

    fun markSeen(context: Context, source: String, id: String, atMs: Long = System.currentTimeMillis()) {
        val map = read(context, source)
        map[id] = atMs
        write(context, source, map)
    }

    fun markAllSeen(context: Context, source: String, ids: Collection<String>) {
        if (ids.isEmpty()) return
        val now = System.currentTimeMillis()
        val map = read(context, source)
        ids.forEach { map[it] = now }
        write(context, source, map)
    }

    fun clear(context: Context, source: String) {
        prefs(context).edit().remove(source).apply()
    }

    /**
     * Sayaç/rozet için "yeni" sayılır mı?
     *
     * Görülmemiş **ve** son [RECENT_WINDOW_MS] içinde oluşmuş kayıtlar yenidir.
     * Böylece arşivden gelen aylık eski kayıtlar rozeti şişirmez.
     */
    fun isRecentForBadge(tsMs: Long?, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (tsMs == null) return false
        return nowMs - tsMs <= RECENT_WINDOW_MS && tsMs <= nowMs + 60 * 60 * 1000L
    }

    /**
     * Elde olan kayıtları deftere göre işaretler.
     *
     * @param hadServerState true ise kaydın `isUnread` değeri sunucudan gelmiştir
     *   (Shikimori). Bu durumda yalnızca sunucunun okunmadı dediği kayıtlar, yerelde
     *   görülmemişse yeni kalır. false ise kayıt "yeni" olup olmadığı burada belirlenir.
     */
    fun <T> applyTo(
        context: Context,
        source: String,
        items: List<T>,
        idOf: (T) -> String,
        tsOf: (T) -> Long?,
        unreadOf: (T) -> Boolean,
        hadServerState: Boolean,
        copy: (T, Boolean) -> T
    ): List<T> {
        if (items.isEmpty()) return items
        val seen = read(context, source)
        return items.map { item ->
            val localUnread = !seen.containsKey(idOf(item)) && isRecentForBadge(tsOf(item))
            val unread = if (hadServerState) unreadOf(item) && localUnread else localUnread
            copy(item, unread)
        }
    }
}
