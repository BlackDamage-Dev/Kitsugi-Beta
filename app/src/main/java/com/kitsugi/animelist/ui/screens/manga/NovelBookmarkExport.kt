package com.kitsugi.animelist.ui.screens.manga

/** A readable UTF-8 export of locations and their notes; never includes document contents. */
internal fun buildNovelBookmarkExport(
    heading: String,
    bookmarks: List<NovelBookmark>,
    label: (NovelBookmark) -> String
): String = buildString {
    append(heading).append('\n')
    for (bookmark in bookmarks.take(200)) {
        append('\n').append(label(bookmark)).append('\n')
        if (bookmark.note.isNotBlank()) append(bookmark.note).append('\n')
    }
}
