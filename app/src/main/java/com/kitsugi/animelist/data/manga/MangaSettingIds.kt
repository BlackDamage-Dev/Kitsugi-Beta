package com.kitsugi.animelist.data.manga

/**
 * Manga okuyucu ayarlarının **tek doğruluk kaynağı** olan metin kimlikleri.
 *
 * Neden var?
 * ----------
 * Okuma modu / renk filtresi / sığdırma modu değerleri Room'da değil,
 * DataStore'da düz `String` olarak tutuluyor. Bu değerler daha önce üç ayrı
 * yerde elle yazılıyordu:
 *
 *  1. `KitsugiPreferencesSettingsDialog` (ayarları YAZAN yer) → "GrayScale",
 *     "Inverted", "Warm", "Cool", "WebtoonZoom" yazıyordu.
 *  2. `MangaReaderViewModel` (ayarları OKUYAN yer) → "Grayscale", "Invert",
 *     "Webtoon" bekliyordu.
 *  3. TV okuyucusu → kendi listesini kullanıyordu.
 *
 * Sonuç: kullanıcı Ayarlar'dan "Gri Ton", "Ters Çevir", "Sıcak", "Soğuk" ya da
 * "Webtoon Zoom" seçtiğinde değer sessizce `Normal` / `RightToLeft`'e düşüyordu
 * (hiçbir hata gösterilmeden ayar kayboluyordu).
 *
 * Bu dosya hem yazma hem okuma tarafını aynı sabitlere bağlar; eski sürümlerden
 * kalan değerler de [readingMode], [colorFilter], [fitMode] çözücüleri
 * tarafından geriye dönük uyumlu şekilde kabul edilir.
 */
object MangaSettingIds {

    // ─── Okuma modu ──────────────────────────────────────────────────────────
    const val READING_RTL = "RightToLeft"
    const val READING_LTR = "LeftToRight"
    const val READING_VERTICAL = "Vertical"
    const val READING_WEBTOON = "Webtoon"

    /** Tüm okuma modu kimlikleri (ayar ekranındaki seçenek sırası). */
    val readingModeIds: List<String> = listOf(
        READING_RTL, READING_LTR, READING_VERTICAL, READING_WEBTOON
    )

    fun readingModeId(mode: ReadingMode): String = when (mode) {
        ReadingMode.RightToLeft -> READING_RTL
        ReadingMode.LeftToRight -> READING_LTR
        ReadingMode.Vertical -> READING_VERTICAL
        ReadingMode.Webtoon -> READING_WEBTOON
    }

    fun readingMode(id: String?): ReadingMode = when (id?.trim()?.lowercase()) {
        "ltr", "lefttoright", "left_to_right", "soldan_saga", "soldansaga" ->
            ReadingMode.LeftToRight
        "vertical", "verticalpager", "dikey", "dikey_pager" ->
            ReadingMode.Vertical
        // "WebtoonZoom" = eski ayar kimliği (Tachiyomi'deki "long strip")
        "webtoon", "webtoonzoom", "longstrip", "long_strip", "webtoon_zoom" ->
            ReadingMode.Webtoon
        else -> ReadingMode.RightToLeft
    }

    // ─── Renk filtresi ───────────────────────────────────────────────────────
    const val FILTER_NORMAL = "Normal"
    const val FILTER_WARM = "Warm"
    const val FILTER_COOL = "Cool"
    const val FILTER_GRAYSCALE = "Grayscale"
    const val FILTER_SEPIA = "Sepia"
    const val FILTER_INVERT = "Invert"

    fun colorFilterId(filter: ColorFilterType): String = when (filter) {
        ColorFilterType.Normal -> FILTER_NORMAL
        ColorFilterType.Warm -> FILTER_WARM
        ColorFilterType.Cool -> FILTER_COOL
        ColorFilterType.Grayscale -> FILTER_GRAYSCALE
        ColorFilterType.Sepia -> FILTER_SEPIA
        ColorFilterType.Invert -> FILTER_INVERT
    }

    fun colorFilter(id: String?): ColorFilterType = when (id?.trim()?.lowercase()) {
        "warm", "sicak", "sıcak" -> ColorFilterType.Warm
        "cool", "soguk", "soğuk" -> ColorFilterType.Cool
        "grayscale", "greyscale", "gri", "griton", "gray" -> ColorFilterType.Grayscale
        "sepia", "sepya" -> ColorFilterType.Sepia
        "invert", "inverted", "ters", "terscevir", "negatif" -> ColorFilterType.Invert
        else -> ColorFilterType.Normal
    }

    // ─── Sığdırma modu ───────────────────────────────────────────────────────
    const val FIT_SCREEN = "FitScreen"
    const val FIT_WIDTH = "FitWidth"
    const val FIT_HEIGHT = "FitHeight"
    const val FIT_CROP = "Crop"
    const val FIT_ORIGINAL = "Original"

    fun fitModeId(mode: MangaFitMode): String = when (mode) {
        MangaFitMode.FitScreen -> FIT_SCREEN
        MangaFitMode.FitWidth -> FIT_WIDTH
        MangaFitMode.FitHeight -> FIT_HEIGHT
        MangaFitMode.Crop -> FIT_CROP
        MangaFitMode.Original -> FIT_ORIGINAL
    }

    fun fitMode(id: String?): MangaFitMode = when (id?.trim()?.lowercase()) {
        "fitwidth", "genislik", "genişlik", "widthfit" -> MangaFitMode.FitWidth
        "fitheight", "yukseklik", "yükseklik", "heightfit" -> MangaFitMode.FitHeight
        "crop", "kirp", "kırp", "doldur" -> MangaFitMode.Crop
        "original", "orijinal", "none" -> MangaFitMode.Original
        else -> MangaFitMode.FitScreen
    }

    // ─── Döngü yardımcıları (alt bardaki hızlı butonlar) ─────────────────────
    fun nextReadingMode(mode: ReadingMode): ReadingMode = when (mode) {
        ReadingMode.RightToLeft -> ReadingMode.LeftToRight
        ReadingMode.LeftToRight -> ReadingMode.Vertical
        ReadingMode.Vertical -> ReadingMode.Webtoon
        ReadingMode.Webtoon -> ReadingMode.RightToLeft
    }

    fun nextFitMode(mode: MangaFitMode): MangaFitMode = when (mode) {
        MangaFitMode.FitScreen -> MangaFitMode.FitWidth
        MangaFitMode.FitWidth -> MangaFitMode.FitHeight
        MangaFitMode.FitHeight -> MangaFitMode.Crop
        MangaFitMode.Crop -> MangaFitMode.Original
        MangaFitMode.Original -> MangaFitMode.FitScreen
    }
}

/** Kısa okunur adlar (log / rapor amaçlı, UI metni değil). */
fun ReadingMode.readerLabel(): String = when (this) {
    ReadingMode.RightToLeft -> "RTL"
    ReadingMode.LeftToRight -> "LTR"
    ReadingMode.Vertical -> "Vertical"
    ReadingMode.Webtoon -> "Webtoon"
}
