package com.kitsugi.animelist.data.music

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * 💾 Müzik Durumu Kalıcı Depolama
 *
 * Favoriler, son çalınanlar ve karışım profilleri süreç ölünce kaybolmasın.
 *
 * Ayrı bir DataStore kullanılıyor (`kitsugi_music_state`): ayar deposu
 * `AppSettings`'e bağlı ve JSON blob'ları oraya karıştırmak o devasa
 * veri sınıfını kirletirdi. Müzik durumu bağımsız yaşar.
 *
 * Saklanan şeyler:
 *  - `favorites`   → JSON dizi (ses kimlikleri)
 *  - `recent`      → JSON dizi (en yeni başta, 20 sınırı)
 *  - `profiles`    → JSON dizi (MixProfile nesneleri)
 */
class MusicStateStore(private val context: Context) {

    companion object {
        private const val TAG = "MusicStateStore"
        private val Context.musicStateStore by preferencesDataStore(name = "kitsugi_music_state")

        private val KEY_FAVORITES = stringPreferencesKey("favorites")
        private val KEY_RECENT = stringPreferencesKey("recent")
        private val KEY_PROFILES = stringPreferencesKey("profiles")
    }

    /** Favori ses kimlikleri. */
    val favorites: Flow<Set<String>> = context.musicStateStore.data.map { prefs ->
        decodeStringArray(prefs[KEY_FAVORITES]).toSet()
    }

    /** Son çalınanlar (en yeni başta). */
    val recent: Flow<List<String>> = context.musicStateStore.data.map { prefs ->
        decodeStringArray(prefs[KEY_RECENT])
    }

    /** Kayıtlı karışım profilleri. */
    val profiles: Flow<List<MixProfile>> = context.musicStateStore.data.map { prefs ->
        decodeProfiles(prefs[KEY_PROFILES])
    }

    // ── Yazma ───────────────────────────────────────────────────────────

    suspend fun saveFavorites(ids: Set<String>) {
        context.musicStateStore.edit { prefs ->
            prefs[KEY_FAVORITES] = encodeStringArray(ids.toList())
        }
    }

    suspend fun saveRecent(ids: List<String>) {
        context.musicStateStore.edit { prefs ->
            prefs[KEY_RECENT] = encodeStringArray(ids.take(20))
        }
    }

    suspend fun saveProfiles(profiles: List<MixProfile>) {
        context.musicStateStore.edit { prefs ->
            prefs[KEY_PROFILES] = encodeProfiles(profiles)
        }
    }

    // ── Kodlama ─────────────────────────────────────────────────────────

    private fun encodeStringArray(values: List<String>): String =
        JSONArray().apply { values.forEach { put(it) } }.toString()

    private fun decodeStringArray(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
        }.getOrElse {
            Log.w(TAG, "Favori listesi okunamadı", it)
            emptyList()
        }
    }

    private fun encodeProfiles(profiles: List<MixProfile>): String {
        val arr = JSONArray()
        profiles.forEach { profile ->
            arr.put(JSONObject().apply {
                put("id", profile.id)
                put("name", profile.name)
                put("createdAt", profile.createdAt)
                put("genreTag", profile.genreTag)
                put("channels", JSONArray().apply {
                    profile.channels.forEach { ch ->
                        put(JSONObject().apply {
                            put("id", ch.soundId)
                            put("title", ch.title)
                            put("volume", ch.volume.toDouble())
                            put("muted", ch.muted)
                            ch.streamUrl?.let { put("streamUrl", it) }
                            if (ch.sourceKey.isNotBlank()) put("sourceKey", ch.sourceKey)
                        })
                    }
                })
            })
        }
        return arr.toString()
    }

    private fun decodeProfiles(raw: String?): List<MixProfile> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val chArr = o.optJSONArray("channels") ?: JSONArray()
                val channels = (0 until chArr.length()).mapNotNull { j ->
                    val c = chArr.optJSONObject(j) ?: return@mapNotNull null
                    MixChannel(
                        soundId = c.optString("id"),
                        title = c.optString("title"),
                        volume = c.optDouble("volume", 0.6).toFloat(),
                        muted = c.optBoolean("muted", false),
                        streamUrl = c.optString("streamUrl").takeIf { it.isNotBlank() },
                        sourceKey = c.optString("sourceKey", "")
                    )
                }
                MixProfile(
                    id = o.optString("id"),
                    name = o.optString("name", "Karışım"),
                    createdAt = o.optLong("createdAt", 0L),
                    channels = channels,
                    genreTag = o.optString("genreTag", "")
                )
            }
        }.getOrElse {
            Log.w(TAG, "Profiller okunamadı", it)
            emptyList()
        }
    }
}
