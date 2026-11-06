package com.kitsugi.animelist.ui.screens.manga.components

/** Maps visual spreads to original page indices. No pairing crosses chapter boundaries. */
internal fun readerSpreadCount(pageCount: Int, doublePage: Boolean, coverAlone: Boolean): Int {
    if (pageCount <= 0) return 0
    if (!doublePage) return pageCount
    return if (coverAlone) 1 + pageCount / 2 else pageCount / 2 + pageCount % 2
}

internal fun readerSpreadFirst(spread: Int, doublePage: Boolean, coverAlone: Boolean): Int = when {
    !doublePage -> spread
    coverAlone && spread == 0 -> 0
    coverAlone -> 1 + (spread - 1) * 2
    else -> spread * 2
}

internal fun readerSpreadForPage(page: Int, doublePage: Boolean, coverAlone: Boolean): Int = when {
    !doublePage -> page
    coverAlone && page == 0 -> 0
    coverAlone -> 1 + (page - 1) / 2
    else -> page / 2
}

internal fun readerSpreadLast(spread: Int, count: Int, doublePage: Boolean, coverAlone: Boolean): Int {
    val first = readerSpreadFirst(spread, doublePage, coverAlone)
    val paired = doublePage && !(coverAlone && spread == 0)
    return (first + if (paired) 1 else 0).coerceAtMost(count - 1)
}
