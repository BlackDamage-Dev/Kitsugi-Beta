package com.kitsugi.animelist.ui.screens.detail

/** UI pager index reserved for the media-video library tab. It does not fetch detail-tab data. */
internal const val DETAIL_VIDEO_PAGE_INDEX = 1

/**
 * Maps the visible pager index to the legacy lazy-data index used by detail ViewModels.
 * The Videos page has no remote tab-state request; all other pages map 1:1 to ViewModel indices.
 */
internal fun detailDataTabIndexForPage(pageIndex: Int): Int? =
    if (pageIndex == DETAIL_VIDEO_PAGE_INDEX) null else pageIndex

sealed class DetailTabState<out T> {
    data object Loading : DetailTabState<Nothing>()

    /**
     * Sekme yüklenemedi. [platformErrorCode], sekmeyi besleyen kaynağın `KTS-…` hata kodudur
     * (bilinmiyorsa null); sekme bu kodu neden/çözüm açıklamasıyla gösterir.
     */
    data class Error(
        val platformErrorCode: com.kitsugi.animelist.data.platform.PlatformErrorCode? = null,
    ) : DetailTabState<Nothing>()

    data class Success<out T>(val data: T) : DetailTabState<T>()
}
