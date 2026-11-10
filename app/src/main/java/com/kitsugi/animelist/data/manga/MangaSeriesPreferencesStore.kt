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
            val defaults = MangaReaderOptions()
            val options = optionsObj?.let { opt ->
                MangaReaderOptions(
                    cropBorders = opt.optBoolean("cropBorders", defaults.cropBorders),
                    showPageNumbers = opt.optBoolean("showPageNumbers", defaults.showPageNumbers),
                    doublePage = opt.optBoolean("doublePage", defaults.doublePage),
                    landscapeDoublePage = opt.optBoolean("landscapeDoublePage", defaults.landscapeDoublePage),
                    sidePaddingDp = opt.optInt("sidePaddingDp", defaults.sidePaddingDp),
                    pageGapDp = opt.optInt("pageGapDp", defaults.pageGapDp),
                    rotateWidePages = opt.optBoolean("rotateWidePages", defaults.rotateWidePages),
                    pageOpacity = opt.optDouble("pageOpacity", defaults.pageOpacity.toDouble()).toFloat(),
                    bookEffect = opt.optBoolean("bookEffect", defaults.bookEffect),
                    highColorMode = opt.optBoolean("highColorMode", defaults.highColorMode),
                    tapNavigation = opt.optString("tapNavigation").let { name ->
                        MangaTapNavigation.entries.firstOrNull { it.name == name } ?: defaults.tapNavigation
                    },
                    volumeKeys = opt.optBoolean("volumeKeys", defaults.volumeKeys),
                    reverseVolumeKeys = opt.optBoolean("reverseVolumeKeys", defaults.reverseVolumeKeys),
                    zoomStart = opt.optString("zoomStart").let { name ->
                        MangaZoomStart.entries.firstOrNull { it.name == name } ?: defaults.zoomStart
                    },
                    webtoonZoom = opt.optBoolean("webtoonZoom", defaults.webtoonZoom),
                    autoWebtoon = opt.optBoolean("autoWebtoon", defaults.autoWebtoon),
                    skipReadChapters = opt.optBoolean("skipReadChapters", defaults.skipReadChapters),
                    keepScreenOn = opt.optBoolean("keepScreenOn", defaults.keepScreenOn),
                    fullscreen = opt.optBoolean("fullscreen", defaults.fullscreen),
                    pageTransitions = opt.optBoolean("pageTransitions", defaults.pageTransitions),
                    longTapNavigation = opt.optBoolean("longTapNavigation", defaults.longTapNavigation),
                    preloadAhead = opt.optInt("preloadAhead", defaults.preloadAhead),
                    incognito = opt.optBoolean("incognito", defaults.incognito),
                    readerTheme = opt.optString("readerTheme").let { name ->
                        MangaReaderTheme.entries.firstOrNull { it.name == name } ?: defaults.readerTheme
                    },
                    autoScroll = opt.optBoolean("autoScroll", defaults.autoScroll),
                    autoScrollSpeed = opt.optInt("autoScrollSpeed", defaults.autoScrollSpeed),
                    showProgressPercent = opt.optBoolean("showProgressPercent", defaults.showProgressPercent),
                    novelFontSizeSp = opt.optInt("novelFontSizeSp", defaults.novelFontSizeSp),
                    novelLineHeight = opt.optDouble("novelLineHeight", defaults.novelLineHeight.toDouble()).toFloat(),
                    novelMarginDp = opt.optInt("novelMarginDp", defaults.novelMarginDp),
                    novelFontFamily = opt.optString("novelFontFamily").let { name ->
                        MangaNovelFont.entries.firstOrNull { it.name == name } ?: defaults.novelFontFamily
                    },
                    novelTextAlign = opt.optString("novelTextAlign").let { name ->
                        MangaNovelTextAlign.entries.firstOrNull { it.name == name } ?: defaults.novelTextAlign
                    },
                    novelSpeechRate = opt.optDouble("novelSpeechRate", defaults.novelSpeechRate.toDouble()).toFloat(),
                    novelSpeechHighlight = opt.optBoolean("novelSpeechHighlight", defaults.novelSpeechHighlight),
                    bionicReading = opt.optBoolean("bionicReading", defaults.bionicReading),
                    novelShowBatteryAndClock = opt.optBoolean("novelShowBatteryAndClock", defaults.novelShowBatteryAndClock),
                    cropWebtoonBorders = opt.optBoolean("cropWebtoonBorders", defaults.cropWebtoonBorders),
                ).sanitized()
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
                val o = JSONObject().apply {
                    put("cropBorders", opt.cropBorders)
                    put("showPageNumbers", opt.showPageNumbers)
                    put("doublePage", opt.doublePage)
                    put("landscapeDoublePage", opt.landscapeDoublePage)
                    put("sidePaddingDp", opt.sidePaddingDp)
                    put("pageGapDp", opt.pageGapDp)
                    put("rotateWidePages", opt.rotateWidePages)
                    put("pageOpacity", opt.pageOpacity.toDouble())
                    put("bookEffect", opt.bookEffect)
                    put("highColorMode", opt.highColorMode)
                    put("tapNavigation", opt.tapNavigation.name)
                    put("volumeKeys", opt.volumeKeys)
                    put("reverseVolumeKeys", opt.reverseVolumeKeys)
                    put("zoomStart", opt.zoomStart.name)
                    put("webtoonZoom", opt.webtoonZoom)
                    put("autoWebtoon", opt.autoWebtoon)
                    put("skipReadChapters", opt.skipReadChapters)
                    put("keepScreenOn", opt.keepScreenOn)
                    put("fullscreen", opt.fullscreen)
                    put("pageTransitions", opt.pageTransitions)
                    put("longTapNavigation", opt.longTapNavigation)
                    put("preloadAhead", opt.preloadAhead)
                    put("incognito", opt.incognito)
                    put("readerTheme", opt.readerTheme.name)
                    put("autoScroll", opt.autoScroll)
                    put("autoScrollSpeed", opt.autoScrollSpeed)
                    put("showProgressPercent", opt.showProgressPercent)
                    put("novelFontSizeSp", opt.novelFontSizeSp)
                    put("novelLineHeight", opt.novelLineHeight.toDouble())
                    put("novelMarginDp", opt.novelMarginDp)
                    put("novelFontFamily", opt.novelFontFamily.name)
                    put("novelTextAlign", opt.novelTextAlign.name)
                    put("novelSpeechRate", opt.novelSpeechRate.toDouble())
                    put("novelSpeechHighlight", opt.novelSpeechHighlight)
                    put("bionicReading", opt.bionicReading)
                    put("novelShowBatteryAndClock", opt.novelShowBatteryAndClock)
                    put("cropWebtoonBorders", opt.cropWebtoonBorders)
                }
                put("options", o)
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
