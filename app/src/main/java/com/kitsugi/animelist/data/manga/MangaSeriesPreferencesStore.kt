package com.kitsugi.animelist.data.manga

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.security.MessageDigest

data class MangaSeriesPreferences(
    val hasCustomSettings: Boolean = false,
    val readingMode: ReadingMode? = null,
    val colorFilter: ColorFilterType? = null,
    val fitMode: MangaFitMode? = null,
    val options: MangaReaderOptions? = null,
)

/**
 * Thread-safe, non-destructive per-series reader preferences store.
 * Allows overriding global reading mode, color filters, fit modes, and options
 * on a per-manga basis.
 */
class MangaSeriesPreferencesStore(
    private val preferences: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("manga_series_reader_prefs", Context.MODE_PRIVATE)
    )

    @Synchronized
    fun get(mangaUrl: String): MangaSeriesPreferences? {
        val json = preferences.getString(key(mangaUrl), null) ?: return null
        return runCatching {
            val obj = JSONObject(json)
            if (!obj.optBoolean("hasCustomSettings", false)) return null

            val readingMode = obj.optString("readingMode").takeIf { it.isNotBlank() }?.let { mode ->
                ReadingMode.entries.firstOrNull { it.name == mode }
            }
            val colorFilter = obj.optString("colorFilter").takeIf { it.isNotBlank() }?.let { filter ->
                ColorFilterType.entries.firstOrNull { it.name == filter }
            }
            val fitMode = obj.optString("fitMode").takeIf { it.isNotBlank() }?.let { fit ->
                MangaFitMode.entries.firstOrNull { it.name == fit }
            }

            val optionsObj = obj.optJSONObject("options")
            val options = optionsObj?.let { opt ->
                MangaReaderOptions.fromJson(opt.toString()).sanitized()
            }

            MangaSeriesPreferences(
                hasCustomSettings = true,
                readingMode = readingMode,
                colorFilter = colorFilter,
                fitMode = fitMode,
                options = options,
            )
        }.getOrNull()
    }

    @Synchronized
    fun save(mangaUrl: String, prefs: MangaSeriesPreferences) {
        val obj = JSONObject().apply {
            put("hasCustomSettings", prefs.hasCustomSettings)
            prefs.readingMode?.let { put("readingMode", it.name) }
            prefs.colorFilter?.let { put("colorFilter", it.name) }
            prefs.fitMode?.let { put("fitMode", it.name) }
            prefs.options?.let { opt ->
                put("options", JSONObject(opt.toJson()))
            }
        }
        preferences.edit().putString(key(mangaUrl), obj.toString()).apply()
    }

    @Synchronized
    fun clear(mangaUrl: String) {
        preferences.edit().remove(key(mangaUrl)).apply()
    }

    private fun key(mangaUrl: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(mangaUrl.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
