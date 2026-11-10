package com.kitsugi.animelist.data.manga

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Okuyucuya özel (okuma modu/sığdırma/filtre dışındaki) tercihler.
 *
 * Mihon / Aniyomi / Kotatsu okuyucularındaki temel deneyim ayarlarının
 * sadeleştirilmiş karşılığı:
 *
 *  • [immersiveByDefault] — okuyucu açılırken sistem çubukları gizlensin mi
 *    (Mihon: "Fullscreen")
 *  • [keepScreenOn]       — okurken ekran kapanmasın (Mihon: "Keep screen on")
 *  • [tapZonesEnabled]    — sol/sağ dokunuşla sayfa çevirme (Mihon: "Tap zones")
 *  • [invertTapZones]     — dokunma bölgelerini ters çevir (Mihon: "Invert tap zones")
 *  • [volumeKeysEnabled]  — ses tuşlarıyla sayfa geçişi (Mihon: "Volume keys")
 *  • [showPageNumber]     — sayfa numarası rozeti
 *  • [mirrorPages]        — sayfayı yatay aynala (bazı kaynaklar sayfaları
 *    telif taramasını atlatmak için aynalanmış servis ediyor; ayrıca ters
 *    taranmış bölümleri düzeltmek için)
 *  • [showChapterTransition] — bölüm sonu geçiş kartı
 */
data class MangaReaderPrefs(
    val immersiveByDefault: Boolean = true,
    val keepScreenOn: Boolean = true,
    val tapZonesEnabled: Boolean = true,
    val invertTapZones: Boolean = false,
    val volumeKeysEnabled: Boolean = false,
    val showPageNumber: Boolean = true,
    val mirrorPages: Boolean = false,
    val showChapterTransition: Boolean = true,
)

/**
 * [MangaReaderPrefs] için basit, süreç içinde tek örnekli (singleton) depo.
 *
 * Neden SharedPreferences?
 * ------------------------
 * Okuyucu ilk kareyi çizerken bu değerler **senkron** okunmak zorunda
 * (tam ekran / ekranı açık tut gibi davranışlar ilk karede uygulanmalı).
 * DataStore asenkron olduğu için okuyucu bir kare "yanlış" modda açılıyordu.
 * Yazma hacmi çok küçük (birkaç boolean), bu yüzden SharedPreferences yeterli.
 */
class MangaReaderPreferences private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<MangaReaderPrefs> = _state.asStateFlow()

    /** Anlık değer (Compose dışından, örn. ViewModel'dan okumak için). */
    val current: MangaReaderPrefs get() = _state.value

    /** Tercihleri günceller ve diske yazar. Aynı değer verilirse hiçbir şey yapmaz. */
    fun update(transform: (MangaReaderPrefs) -> MangaReaderPrefs) {
        val next = transform(_state.value)
        if (next == _state.value) return
        _state.value = next
        prefs.edit()
            .putBoolean(KEY_IMMERSIVE, next.immersiveByDefault)
            .putBoolean(KEY_KEEP_SCREEN_ON, next.keepScreenOn)
            .putBoolean(KEY_TAP_ZONES, next.tapZonesEnabled)
            .putBoolean(KEY_INVERT_TAP_ZONES, next.invertTapZones)
            .putBoolean(KEY_VOLUME_KEYS, next.volumeKeysEnabled)
            .putBoolean(KEY_PAGE_NUMBER, next.showPageNumber)
            .putBoolean(KEY_MIRROR, next.mirrorPages)
            .putBoolean(KEY_CHAPTER_TRANSITION, next.showChapterTransition)
            .apply()
    }

    private fun read(): MangaReaderPrefs {
        val defaults = MangaReaderPrefs()
        return MangaReaderPrefs(
            immersiveByDefault = prefs.getBoolean(KEY_IMMERSIVE, defaults.immersiveByDefault),
            keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, defaults.keepScreenOn),
            tapZonesEnabled = prefs.getBoolean(KEY_TAP_ZONES, defaults.tapZonesEnabled),
            invertTapZones = prefs.getBoolean(KEY_INVERT_TAP_ZONES, defaults.invertTapZones),
            volumeKeysEnabled = prefs.getBoolean(KEY_VOLUME_KEYS, defaults.volumeKeysEnabled),
            showPageNumber = prefs.getBoolean(KEY_PAGE_NUMBER, defaults.showPageNumber),
            mirrorPages = prefs.getBoolean(KEY_MIRROR, defaults.mirrorPages),
            showChapterTransition = prefs.getBoolean(KEY_CHAPTER_TRANSITION, defaults.showChapterTransition),
        )
    }

    companion object {
        private const val FILE_NAME = "kitsugi_manga_reader_prefs"

        private const val KEY_IMMERSIVE = "reader_immersive"
        private const val KEY_KEEP_SCREEN_ON = "reader_keep_screen_on"
        private const val KEY_TAP_ZONES = "reader_tap_zones"
        private const val KEY_INVERT_TAP_ZONES = "reader_invert_tap_zones"
        private const val KEY_VOLUME_KEYS = "reader_volume_keys"
        private const val KEY_PAGE_NUMBER = "reader_page_number"
        private const val KEY_MIRROR = "reader_mirror_pages"
        private const val KEY_CHAPTER_TRANSITION = "reader_chapter_transition"

        @Volatile
        private var instance: MangaReaderPreferences? = null

        fun get(context: Context): MangaReaderPreferences =
            instance ?: synchronized(this) {
                instance ?: MangaReaderPreferences(context).also { instance = it }
            }

        fun readingMode(value: String): ReadingMode = when (value) {
            "WebtoonZoom", "Webtoon" -> ReadingMode.Webtoon // migrate the old global-settings ID
            else -> ReadingMode.entries.firstOrNull { it.name == value } ?: ReadingMode.RightToLeft
        }

        fun nextReadingMode(mode: ReadingMode): ReadingMode {
            val modes = listOf(ReadingMode.RightToLeft, ReadingMode.LeftToRight, ReadingMode.Vertical, ReadingMode.Webtoon)
            return modes[(modes.indexOf(mode) + 1) % modes.size]
        }

        fun colorFilter(value: String): ColorFilterType = when (value) {
            "GrayScale", "Grayscale" -> ColorFilterType.Grayscale
            "Inverted", "Invert" -> ColorFilterType.Invert
            "Warm", "Sepia" -> ColorFilterType.Sepia
            else -> ColorFilterType.Normal
        }

        fun fitMode(value: String): MangaFitMode =
            MangaFitMode.entries.firstOrNull { it.name == value } ?: MangaFitMode.FitScreen

        fun finite(value: Float, default: Float, min: Float, max: Float): Float =
            if (value.isFinite()) value.coerceIn(min, max) else default
    }
}

/** Only persistent preferences; transient zoom/auto-scroll never survive a chapter or app restart. */
data class MangaWebtoonPreferences(
    val sidePaddingDp: Int = 0,
    val pageGapDp: Int = 0,
    val scrollPercent: Int = 90,
    val tapToScroll: Boolean = true,
    val volumeKeys: Boolean = false,
    val autoScrollDpPerSecond: Int = 32,
    val background: String = "Black",
    val showPageNumber: Boolean = true,
    val keepScreenOn: Boolean = true,
) {
    fun sanitized() = copy(
        sidePaddingDp = sidePaddingDp.coerceIn(0, 64),
        pageGapDp = pageGapDp.coerceIn(0, 32),
        scrollPercent = scrollPercent.coerceIn(25, 100),
        autoScrollDpPerSecond = autoScrollDpPerSecond.coerceIn(8, 160),
        background = background.takeIf { it in setOf("Black", "DarkGray", "White") } ?: "Black",
    )
}
