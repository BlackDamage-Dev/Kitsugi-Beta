package com.kitsugi.animelist.data.sync

import android.content.Context
import org.json.JSONObject

/**
 * Bir kaynağın (AniList / MAL / Simkl / Kitsu / Shikimori / Bangumi) liste yenileme geçmişi.
 *
 * NEDEN: Eskiden uygulama "son liste yenilemesi" için TEK bir global zaman damgası
 * tutuyordu (`kitsugi_auto_sync.last_list_sync_ms`) ve her yenilemede kaynak listesinin
 * TAMAMI ağdan yeniden çekilip veritabanına yeniden yazılıyordu. Bu yüzden:
 *  - bir kaynağı elle yenilemek diğerlerinin tazeliğini de "taze" gösteriyordu,
 *  - hiçbir şey değişmemiş olsa bile tüm tablo yeniden yazılıyor, Room tüm kütüphaneyi
 *    yeniden yayınlıyor ve Listem'in birleşik hesaplaması baştan koşuyordu.
 *
 * Artık her kaynağın kendi durumu var: son başarılı yenileme, son TAM yenileme,
 * son deneme, ardışık hata sayısı ve (destekleyen kaynaklarda) uzak revizyon imzası.
 */
data class ListSyncState(
    val source: String = "",
    val lastSuccessAt: Long = 0L,
    val lastFullSyncAt: Long = 0L,
    val lastAttemptAt: Long = 0L,
    val failureCount: Int = 0,
    val remoteRevision: String? = null,
    val lastError: String? = null
) {
    val hasSyncedOnce: Boolean get() = lastSuccessAt > 0L

    fun toJson(): JSONObject = JSONObject()
        .put("lastSuccessAt", lastSuccessAt)
        .put("lastFullSyncAt", lastFullSyncAt)
        .put("lastAttemptAt", lastAttemptAt)
        .put("failureCount", failureCount)
        .put("remoteRevision", remoteRevision ?: JSONObject.NULL)
        .put("lastError", lastError ?: JSONObject.NULL)

    companion object {
        fun fromJson(source: String, json: JSONObject?): ListSyncState {
            if (json == null) return ListSyncState(source = source)
            return ListSyncState(
                source = source,
                lastSuccessAt = json.optLong("lastSuccessAt", 0L),
                lastFullSyncAt = json.optLong("lastFullSyncAt", 0L),
                lastAttemptAt = json.optLong("lastAttemptAt", 0L),
                failureCount = json.optInt("failureCount", 0),
                remoteRevision = json.optString("remoteRevision")
                    .takeIf { it.isNotBlank() && it != "null" },
                lastError = json.optString("lastError")
                    .takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }
}

/**
 * [ListSyncState] kalıcı deposu (SharedPreferences, JSON).
 *
 * Neden Room değil: bu bir şema değişikliği değil — kullanıcı verisi değil, senkron
 * defteri. Room sürümü/migration'ı büyütmeden tutmak riski sıfırda tutar.
 */
class ListSyncStateStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val lock = Any()

    fun read(source: String): ListSyncState = synchronized(lock) {
        val raw = prefs.getString(keyFor(source), null)
        val json = runCatching { raw?.let { JSONObject(it) } }.getOrNull()
        ListSyncState.fromJson(source, json)
    }

    fun readAll(): Map<String, ListSyncState> = synchronized(lock) {
        val result = mutableMapOf<String, ListSyncState>()
        for ((key, value) in prefs.all) {
            if (!key.startsWith(STATE_PREFIX)) continue
            val raw = value as? String ?: continue
            val json = runCatching { JSONObject(raw) }.getOrNull() ?: continue
            val source = key.removePrefix(STATE_PREFIX)
            result[source] = ListSyncState.fromJson(source, json)
        }
        result
    }

    fun recordAttempt(source: String, now: Long = System.currentTimeMillis()) {
        update(source) { it.copy(lastAttemptAt = now) }
    }

    /**
     * @param fullSync tam liste mi çekildi (delta değil)? `lastFullSyncAt` yalnızca tam
     *        senkronda ilerler; delta yenilemeleri "tam senkron borcu"nu ertelemez.
     * @param remoteRevision destekleyen kaynaklarda (ör. Simkl activities) uzak revizyon
     *        imzası; `null` verilirse önceki değer korunur.
     */
    fun recordSuccess(
        source: String,
        fullSync: Boolean,
        remoteRevision: String? = null,
        now: Long = System.currentTimeMillis()
    ) {
        update(source) { state ->
            state.copy(
                lastSuccessAt = now,
                lastFullSyncAt = if (fullSync) now else state.lastFullSyncAt,
                lastAttemptAt = now,
                failureCount = 0,
                remoteRevision = remoteRevision ?: state.remoteRevision,
                lastError = null
            )
        }
    }

    fun recordFailure(
        source: String,
        message: String?,
        now: Long = System.currentTimeMillis()
    ) {
        update(source) { state ->
            state.copy(
                lastAttemptAt = now,
                failureCount = state.failureCount + 1,
                lastError = message?.take(300)
            )
        }
    }

    fun clear(source: String? = null) {
        synchronized(lock) {
            val editor = prefs.edit()
            if (source == null) editor.clear() else editor.remove(keyFor(source))
            editor.apply()
        }
    }

    private fun update(source: String, transform: (ListSyncState) -> ListSyncState) {
        synchronized(lock) {
            val current = read(source)
            prefs.edit().putString(keyFor(source), transform(current).toJson().toString()).apply()
        }
    }

    private fun keyFor(source: String): String = STATE_PREFIX + source.lowercase()

    private companion object {
        const val PREFS_NAME = "kitsugi_list_sync_state"
        const val STATE_PREFIX = "state_"
    }
}
