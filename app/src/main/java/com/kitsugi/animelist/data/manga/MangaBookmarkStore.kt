package com.kitsugi.animelist.data.manga

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class MangaBookmark(
    val chapterUrl: String,
    val mangaUrl: String,
    val chapterName: String,
    val pageIndex: Int,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Small atomic per-series bookmark store; no destructive Room migration is required. */
class MangaBookmarkStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("manga_reader_bookmarks", Context.MODE_PRIVATE)

    @Synchronized
    fun list(mangaUrl: String): List<MangaBookmark> {
        val json = preferences.getString(key(mangaUrl), null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.getJSONObject(index)
                    add(MangaBookmark(
                        chapterUrl = value.getString("chapterUrl"),
                        mangaUrl = value.getString("mangaUrl"),
                        chapterName = value.optString("chapterName"),
                        pageIndex = value.getInt("pageIndex").coerceAtLeast(0),
                        updatedAt = value.optLong("updatedAt"),
                    ))
                }
            }.sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun toggle(bookmark: MangaBookmark): Boolean {
        val current = list(bookmark.mangaUrl).toMutableList()
        val removed = current.removeAll {
            it.chapterUrl == bookmark.chapterUrl && it.pageIndex == bookmark.pageIndex
        }
        if (!removed) current += bookmark
        write(bookmark.mangaUrl, current)
        return !removed
    }

    private fun write(mangaUrl: String, bookmarks: List<MangaBookmark>) {
        val array = JSONArray()
        bookmarks.sortedByDescending { it.updatedAt }.forEach { bookmark ->
            array.put(JSONObject().apply {
                put("chapterUrl", bookmark.chapterUrl)
                put("mangaUrl", bookmark.mangaUrl)
                put("chapterName", bookmark.chapterName)
                put("pageIndex", bookmark.pageIndex)
                put("updatedAt", bookmark.updatedAt)
            })
        }
        preferences.edit().putString(key(mangaUrl), array.toString()).apply()
    }

    private fun key(mangaUrl: String): String = MessageDigest.getInstance("SHA-256")
        .digest(mangaUrl.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
