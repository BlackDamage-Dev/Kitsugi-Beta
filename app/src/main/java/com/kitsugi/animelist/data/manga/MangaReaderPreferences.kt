package com.kitsugi.animelist.data.manga

/** Options shared by every reader layout. Values are persisted as one atomic JSON object. */
data class MangaReaderOptions(
    val cropBorders: Boolean = false,
    val showPageNumbers: Boolean = true,
    val doublePage: Boolean = false,
    val landscapeDoublePage: Boolean = false,
    val sidePaddingDp: Int = 0,
    val pageGapDp: Int = 4,
    val rotateWidePages: Boolean = false,
    val pageOpacity: Float = 1f,
    val bookEffect: Boolean = false,
    val highColorMode: Boolean = false,
    val tapNavigation: MangaTapNavigation = MangaTapNavigation.Standard,
    val volumeKeys: Boolean = true,
    val reverseVolumeKeys: Boolean = false,
    val zoomStart: MangaZoomStart = MangaZoomStart.Center,
    val webtoonZoom: Boolean = true,
    val autoWebtoon: Boolean = true,
    val skipReadChapters: Boolean = false,
) {
    fun sanitized(): MangaReaderOptions = copy(
        sidePaddingDp = sidePaddingDp.coerceIn(0, 32),
        pageGapDp = pageGapDp.coerceIn(0, 32),
        pageOpacity = if (pageOpacity.isFinite()) pageOpacity.coerceIn(0.1f, 1f) else 1f,
    )
}

enum class MangaTapNavigation { Standard, Inverted, Disabled }
enum class MangaZoomStart { Automatic, Center, Left, Right }

data class MangaReaderPreferences(
    val readingMode: ReadingMode = ReadingMode.RightToLeft,
    val colorFilter: ColorFilterType = ColorFilterType.Normal,
    val fitMode: MangaFitMode = MangaFitMode.FitScreen,
    val brightness: Float = 1f,
    val options: MangaReaderOptions = MangaReaderOptions(),
) {
    fun sanitized(): MangaReaderPreferences = copy(
        brightness = if (brightness.isFinite()) brightness.coerceIn(0.1f, 1f) else 1f,
        options = options.sanitized(),
    )
}

/** A pager item contains one or two real pages; progress always uses their real indices. */
data class MangaDisplayPage(
    val pages: List<MangaPage>,
) {
    val firstPageIndex: Int get() = pages.first().index
    val key: String get() = pages.joinToString(separator = ":") { "${it.index}@${it.url}" }
}

fun shouldUseDoublePage(
    readingMode: ReadingMode,
    isLandscape: Boolean,
    options: MangaReaderOptions,
): Boolean = (readingMode == ReadingMode.LeftToRight || readingMode == ReadingMode.RightToLeft) &&
    (options.doublePage || (isLandscape && options.landscapeDoublePage))

fun buildMangaDisplayPages(
    pages: List<MangaPage>,
    doublePage: Boolean,
): List<MangaDisplayPage> = if (doublePage) {
    pages.chunked(2) { group -> MangaDisplayPage(group.toList()) }
} else {
    pages.map { MangaDisplayPage(listOf(it)) }
}

fun orderMangaSpreadPages(pages: List<MangaPage>, readingMode: ReadingMode): List<MangaPage> =
    if (readingMode == ReadingMode.RightToLeft) pages.asReversed() else pages.toList()

fun mangaDisplayPageIndexFor(displayPages: List<MangaDisplayPage>, realPageIndex: Int): Int {
    if (displayPages.isEmpty()) return 0
    return displayPages.indexOfFirst { group ->
        group.pages.any { it.index == realPageIndex }
    }.takeIf { it >= 0 } ?: realPageIndex.coerceIn(0, displayPages.lastIndex)
}

fun nextMangaColorFilter(current: ColorFilterType): ColorFilterType = when (current) {
    ColorFilterType.Normal -> ColorFilterType.Grayscale
    ColorFilterType.Grayscale -> ColorFilterType.Sepia
    ColorFilterType.Sepia -> ColorFilterType.Invert
    ColorFilterType.Invert -> ColorFilterType.Normal
}

fun nextMangaReadingMode(current: ReadingMode): ReadingMode = when (current) {
    ReadingMode.RightToLeft -> ReadingMode.LeftToRight
    ReadingMode.LeftToRight -> ReadingMode.Vertical
    ReadingMode.Vertical -> ReadingMode.Webtoon
    ReadingMode.Webtoon -> ReadingMode.RightToLeft
}
