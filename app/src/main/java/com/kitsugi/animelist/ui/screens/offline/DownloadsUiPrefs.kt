package com.kitsugi.animelist.ui.screens.offline

import android.content.Context

/**
 * İndirilenler ekranının **görünüm** tercihleri.
 *
 * Neden SharedPreferences? Bu ekranın kendi ViewModel'i yok; tercihler yalnızca
 * "İndirilen resimler" sekmesinin sunumunu etkiliyor (kutucuk boyutu, sıralama,
 * gruplama). Bu yüzden ağır bir veri katmanı yerine küçük ve kalıcı bir depo
 * kullanılıyor — ekran kapanıp açıldığında ya da cihaz döndürüldüğünde seçim korunur.
 */
internal object DownloadsUiPrefs {

    private const val FILE = "kitsugi_downloads_ui"
    private const val KEY_TILE_SIZE = "images_tile_size"
    private const val KEY_SORT = "images_sort_order"
    private const val KEY_LAYOUT = "images_layout"
    private const val KEY_VIDEO_SORT = "videos_sort_order"
    private const val KEY_VIDEO_GROUPED = "videos_grouped"
    private const val KEY_AUDIO_SORT = "audios_sort_order"
    private const val KEY_AUDIO_GROUPED = "audios_grouped"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun tileSize(context: Context): DownloadsTileSize =
        enumOrDefault(prefs(context).getString(KEY_TILE_SIZE, null), DownloadsTileSize.AUTO)

    fun setTileSize(context: Context, value: DownloadsTileSize) {
        prefs(context).edit().putString(KEY_TILE_SIZE, value.name).apply()
    }

    fun imageSort(context: Context): DownloadsSortOrder =
        enumOrDefault(prefs(context).getString(KEY_SORT, null), DownloadsSortOrder.NEWEST)

    fun setImageSort(context: Context, value: DownloadsSortOrder) {
        prefs(context).edit().putString(KEY_SORT, value.name).apply()
    }

    fun imageLayout(context: Context): DownloadsImageLayout =
        enumOrDefault(prefs(context).getString(KEY_LAYOUT, null), DownloadsImageLayout.GROUPED)

    fun setImageLayout(context: Context, value: DownloadsImageLayout) {
        prefs(context).edit().putString(KEY_LAYOUT, value.name).apply()
    }

    fun videoSort(context: Context): DownloadsVideoSort =
        enumOrDefault(prefs(context).getString(KEY_VIDEO_SORT, null), DownloadsVideoSort.QUEUE_ORDER)

    fun setVideoSort(context: Context, value: DownloadsVideoSort) {
        prefs(context).edit().putString(KEY_VIDEO_SORT, value.name).apply()
    }

    fun videosGrouped(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VIDEO_GROUPED, true)

    fun setVideosGrouped(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_VIDEO_GROUPED, value).apply()
    }

    fun audioSort(context: Context): DownloadsSortOrder =
        enumOrDefault(prefs(context).getString(KEY_AUDIO_SORT, null), DownloadsSortOrder.NEWEST)

    fun setAudioSort(context: Context, value: DownloadsSortOrder) {
        prefs(context).edit().putString(KEY_AUDIO_SORT, value.name).apply()
    }

    fun audiosGrouped(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUDIO_GROUPED, true)

    fun setAudiosGrouped(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUDIO_GROUPED, value).apply()
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, fallback: T): T {
        if (name.isNullOrBlank()) return fallback
        return enumValues<T>().firstOrNull { it.name == name } ?: fallback
    }
}

/**
 * "İndirilen resimler" ızgarasında hedeflenen kutucuk genişliği.
 *
 * Ekran genişliği bu değere bölünerek **sütun sayısı otomatik** hesaplanır:
 * dar/dikey ekranda az, geniş/yatay ekranda çok sütun çıkar. Böylece hem ekran
 * döndürüldüğünde hem de katlanabilir/tablet gibi farklı boyutlarda ızgara
 * kendiliğinden uyum sağlar (sabit sütun sayısı yok).
 */
internal enum class DownloadsTileSize(val targetCellWidthDp: Int) {
    /** Tamamen otomatik: hedef genişlik sabit, sütun sayısı ekrana göre değişir. */
    AUTO(186),

    /** Daha çok resim görünsün (küçük kutucuk). */
    COMPACT(112),

    /** Daha büyük önizlemeler (az sütun). */
    LARGE(268)
}

internal enum class DownloadsSortOrder {
    NEWEST,
    OLDEST,
    NAME,
    SIZE
}

/**
 * Videolar sekmesinin sıralaması.
 *
 * [QUEUE_ORDER] varsayılandır ve **kuyruk sırasını korur**: devam eden işler üstte,
 * kuyrukta bekleyenler ise kullanıcı tarafından belirlenen öncelik sırasıyla listelenir
 * (bu yüzden `AnimeDownloadManager.moveQueuedDownload` ile yukarı/aşağı taşıma yalnızca
 * bu modda anlamlıdır).
 */
internal enum class DownloadsVideoSort {
    QUEUE_ORDER,
    TITLE,
    EPISODE,
    SIZE,
    RECENT
}

internal enum class DownloadsImageLayout {
    /** İçerik adına göre başlıklı gruplar (varsayılan). */
    GROUPED,

    /** Tüm indirilenler tek, kesintisiz kronolojik listede. */
    FLAT
}
