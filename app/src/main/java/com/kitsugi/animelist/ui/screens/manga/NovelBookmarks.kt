package com.kitsugi.animelist.ui.screens.manga

import java.util.Base64

/** Stable chapter/segment and pixel-offset bookmarks, stored per document. */
internal data class NovelBookmark(val index: Int, val offset: Int, val note: String = "")

private const val MAX_BOOKMARKS = 200
internal const val MAX_BOOKMARK_NOTE = 500
private const val MAX_BOOKMARK_DATA = 800_000
private const val V2_PREFIX = "v2|"

/** Reads both the original index:offset format and the versioned notes format. */
internal fun decodeNovelBookmarks(serialized: String?): List<NovelBookmark> {
    if (serialized.isNullOrEmpty() || serialized.length > MAX_BOOKMARK_DATA) return emptyList()
    val version2 = serialized.startsWith(V2_PREFIX)
    val data = if (version2) serialized.removePrefix(V2_PREFIX) else serialized
    val decoder = Base64.getUrlDecoder()
    return data.split(',').asSequence().mapNotNull { record ->
        val parts = record.split(':')
        if (parts.size != if (version2) 3 else 2) return@mapNotNull null
        val index = parts[0].toIntOrNull() ?: return@mapNotNull null
        val offset = parts[1].toIntOrNull() ?: return@mapNotNull null
        if (index < 0 || offset < 0) return@mapNotNull null
        val note = if (!version2) "" else {
            if (parts[2].length > MAX_BOOKMARK_NOTE * 4) return@mapNotNull null
            try {
                val bytes = decoder.decode(parts[2])
                if (bytes.size > MAX_BOOKMARK_NOTE * 4) return@mapNotNull null
                val utf8 = Charsets.UTF_8.newDecoder()
                utf8.decode(java.nio.ByteBuffer.wrap(bytes)).toString().takeIf { it.length <= MAX_BOOKMARK_NOTE }
                    ?: return@mapNotNull null
            } catch (_: IllegalArgumentException) {
                return@mapNotNull null
            } catch (_: java.nio.charset.CharacterCodingException) {
                return@mapNotNull null
            }
        }
        NovelBookmark(index, offset, note)
    }.distinctBy { it.index to it.offset }.take(MAX_BOOKMARKS).toList()
}

internal fun encodeNovelBookmarks(bookmarks: List<NovelBookmark>): String {
    val encoder = Base64.getUrlEncoder().withoutPadding()
    return V2_PREFIX + bookmarks.asSequence()
        .filter { it.index >= 0 && it.offset >= 0 && it.note.length <= MAX_BOOKMARK_NOTE }
        .distinctBy { it.index to it.offset }.take(MAX_BOOKMARKS)
        .joinToString(",") { "${it.index}:${it.offset}:${encoder.encodeToString(it.note.toByteArray(Charsets.UTF_8))}" }
}

/** Do not silently evict old bookmarks at the storage limit or duplicate a position. */
internal fun addNovelBookmark(bookmarks: List<NovelBookmark>, bookmark: NovelBookmark): List<NovelBookmark> =
    if (bookmark.index < 0 || bookmark.offset < 0 || bookmark.note.length > MAX_BOOKMARK_NOTE ||
        bookmarks.any { it.index == bookmark.index && it.offset == bookmark.offset } || bookmarks.size >= MAX_BOOKMARKS) bookmarks
    else bookmarks + bookmark

internal fun updateNovelBookmarkNote(bookmarks: List<NovelBookmark>, target: NovelBookmark, note: String): List<NovelBookmark> =
    if (note.length > MAX_BOOKMARK_NOTE) bookmarks else bookmarks.map {
        if (it.index == target.index && it.offset == target.offset) it.copy(note = note) else it
    }
