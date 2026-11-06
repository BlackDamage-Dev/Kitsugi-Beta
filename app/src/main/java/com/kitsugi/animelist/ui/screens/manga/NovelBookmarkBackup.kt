package com.kitsugi.animelist.ui.screens.manga

/** A bounded, document-bound backup; mismatch is rejected rather than misplacing notes. */
internal fun encodeNovelBookmarkBackup(kind: String, bookKey: String, bookmarks: List<NovelBookmark>): String {
    require(kind in setOf("text", "epub") && bookKey.isNotEmpty() && '\n' !in bookKey && '\r' !in bookKey)
    return "KITSUGI-BOOKMARKS-1\n$kind\n$bookKey\n${encodeNovelBookmarks(bookmarks)}"
}

internal fun decodeNovelBookmarkBackup(data: String, kind: String, bookKey: String): List<NovelBookmark> {
    require(data.length <= 800_200) { "Backup too large" }
    val parts = data.split('\n', limit = 4)
    require(parts.size == 4 && parts[0] == "KITSUGI-BOOKMARKS-1" && parts[1] == kind && parts[2] == bookKey) { "Backup does not match document" }
    val records = decodeNovelBookmarks(parts[3])
    require(encodeNovelBookmarks(records) == parts[3]) { "Invalid bookmark backup" }
    return records
}

/** Preserve existing notes when locations conflict; never evict existing bookmarks. */
internal fun mergeNovelBookmarks(existing: List<NovelBookmark>, incoming: List<NovelBookmark>): List<NovelBookmark> {
    var merged = existing
    for (bookmark in incoming) merged = addNovelBookmark(merged, bookmark)
    return merged
}
