package com.kitsugi.animelist.data.manga

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import org.json.JSONObject

/**
 * # Manga Okuyucu — TEK DOĞRULUK KAYNAĞI
 *
 * Bu dosya, okuyucunun tüm görsel ve davranışsal ayarlarını tanımlar. Başka hiçbir
 * ekran kendi renk matrisini, kendi okuma modu eşlemesini veya kendi sığdırma
 * listesini yazmamalıdır. (Eskiden `MangaPageItem` içinde gömülü matris +
 * `KitsugiPreferencesSettingsDialog` içinde farklı kimlikler vardı; iki liste
 * birbirinden kopmuştu. Artık hepsi burada.)
 *
 * Kalıcılık: `SettingsDataStore` → `manga_reader_options_json` (tek JSON anahtarı).
 * Mevcut eski anahtarlar (`manga_reading_mode`, `manga_color_filter`,
 * `manga_fit_mode`, `manga_brightness`) **okunmaya devam eder**; geçiş
 * [MangaReaderOptions.fromJson] içindeki `fallback*` parametreleriyle yapılır.
 */

// ─── Okuma modu ──────────────────────────────────────────────────────────────

/** Okuma yönü / sunum modeli. */
enum class ReadingMode {
    LeftToRight,   // Soldan Sağa (Manhua / Batı çizgi romanı)
    RightToLeft,   // Sağdan Sola (Japon manga)
    Vertical,      // Dikey sayfalama (tek sayfa, dikey geçiş)
    Webtoon;       // Kesintisiz dikey kaydırma (manhwa / webtoon)

    /** Eski/yeni kayıtlı kimlikleri tek yerden çözer. */
    fun toKey(): String = name

    companion object {
        fun fromKey(key: String?): ReadingMode = when (key?.trim()) {
            "LeftToRight", "Left", "LTR", "SoldanSaga" -> LeftToRight
            "Vertical", "Dikey" -> Vertical
            "Webtoon", "WebtoonZoom", "LongStrip", "WebtoonGaps" -> Webtoon
            "RightToLeft", "Right", "RTL", "SagdanSola" -> RightToLeft
            else -> RightToLeft
        }

        /**
         * Okuma modu döngüsü — **tek kaynak**.
         *
         * Alt bar düğmesi, TV kumandası ve klavye aynı sırayı kullanır; her çağrı
         * yerinde ayrı liste tanımlanmaz (önceki kodda iki ayrı liste vardı).
         */
        val cycleOrder: List<ReadingMode> =
            listOf(RightToLeft, LeftToRight, Vertical, Webtoon)
    }
}

// ─── Renk filtresi ───────────────────────────────────────────────────────────

/**
 * Renk matrisi tabanlı filtreler + kalibrasyon (kontrast / doygunluk / mavi ışık).
 *
 * Kimlikler (`key`) kullanıcıya kaydedilen değerlerdir; eski kayıtlarla uyum için
 * [fromKey] birden fazla yazımı kabul eder.
 */
enum class ColorFilterType(val key: String) {
    Normal("Normal"),
    Grayscale("Grayscale"),
    Sepia("Sepia"),
    Invert("Invert"),
    Warm("Warm"),
    Cool("Cool"),
    HighContrast("HighContrast"),
    Soft("Soft");

    companion object {
        fun fromKey(key: String?): ColorFilterType = when (key?.trim()) {
            "Normal", "None" -> Normal
            "Grayscale", "GrayScale", "Greyscale", "Monochrome" -> Grayscale
            "Sepia", "Book", "BookEffect" -> Sepia
            "Invert", "Inverted", "Negative" -> Invert
            "Warm", "Warmth" -> Warm
            "Cool", "Bluelight" -> Cool
            "HighContrast", "Contrast" -> HighContrast
            "Soft", "Soften" -> Soft
            else -> Normal
        }

        /** Okuyucudaki renk filtresi döngü butonunun sırası. */
        val cycleOrder: List<ColorFilterType> = listOf(
            Normal, Grayscale, Sepia, Warm, Cool, HighContrast, Soft, Invert
        )
    }
}

// ─── Sığdırma ────────────────────────────────────────────────────────────────

enum class MangaFitMode(val key: String) {
    FitScreen("FitScreen"),
    FitWidth("FitWidth"),
    FitHeight("FitHeight"),
    Original("Original"),
    Stretch("Stretch"),
    Crop("Crop"),

    /**
     * Mihon `ScaleType.SMART_FIT`: sayfa ekrandan büyükse sığdır, küçükse
     * **büyütme** yapmadan ölçek %100'de bırak (bulanıklık oluşmaz).
     */
    SmartFit("SmartFit");

    companion object {
        fun fromKey(key: String?): MangaFitMode = when (key?.trim()) {
            "FitWidth", "Width" -> FitWidth
            "FitHeight", "Height" -> FitHeight
            "Original", "OriginalSize", "None" -> Original
            "Stretch", "Fill", "FillBounds" -> Stretch
            "Crop" -> Crop
            "SmartFit", "Smart", "SMART_FIT" -> SmartFit
            else -> FitScreen
        }

        val cycleOrder: List<MangaFitMode> =
            listOf(FitScreen, FitWidth, FitHeight, Original, Stretch, Crop, SmartFit)
    }
}

// ─── Arka plan rengi ─────────────────────────────────────────────────────────

/** Sayfanın arkasındaki zemin. `Smart` sayfa tonundan otomatik seçilir. */
enum class MangaPageBackground(val key: String) {
    Black("Black"),
    DarkGray("DarkGray"),
    White("White"),
    Sepia("Sepia"),
    Smart("Smart");

    companion object {
        fun fromKey(key: String?): MangaPageBackground = when (key?.trim()) {
            "DarkGray", "Gray", "Grey" -> DarkGray
            "White", "Light" -> White
            "Sepia" -> Sepia
            "Smart", "Automatic", "Auto" -> Smart
            else -> Black
        }

        val cycleOrder: List<MangaPageBackground> =
            listOf(Black, DarkGray, White, Sepia, Smart)
    }
}

/** [MangaPageBackground] için somut renk. `Smart` için [smartBackground] kullanın. */
fun MangaPageBackground.color(): Color = when (this) {
    MangaPageBackground.Black -> Color(0xFF000000)
    MangaPageBackground.DarkGray -> Color(0xFF1B1B1F)
    MangaPageBackground.White -> Color(0xFFFFFFFF)
    MangaPageBackground.Sepia -> Color(0xFFF3E5C8)
    MangaPageBackground.Smart -> Color(0xFF000000)
}

/**
 * TachiyomiSY "otomatik akıllı arka plan": örneklenen sayfa tonu açıksa
 * koyu, koyuysa açık zemin seçilir (kontrast korunur, göz yormaz).
 */
fun smartBackground(pageLuminance: Float): Color =
    if (pageLuminance > 0.62f) Color(0xFFE8E4DC) else Color(0xFF101014)

/**
 * Kotatsu "book background" paritesi: sayfa filtresi (kitap efekti) açıkken
 * zemin de aynı yönde hafifçe tonlanır — aksi halde sayfa ile zemin arasında
 * renk sıçraması görünür. Filtre kapalıysa zemin rengi **hiç değişmez**
 * (ekran görüntüsü/davranış birebir korunur).
 */
/**
 * Sayfa zeminini **tek yerden** çözer: `Smart` ise sayfa tonundan, değilse sabit
 * renkten; sonra kitap efekti açıksa tonlanır. Önceden bu iki adım her çağrı
 * yerinde ayrı ayrı yazılıyordu (kopya mantık).
 */
fun resolvePageBackground(
    background: MangaPageBackground,
    pageLuminance: Float,
    bookEffect: Boolean
): Color = bookTintedBackground(
    base = if (background == MangaPageBackground.Smart) smartBackground(pageLuminance)
           else background.color(),
    bookEffect = bookEffect
)

fun bookTintedBackground(base: Color, bookEffect: Boolean): Color =
    if (!bookEffect) base else Color(
        red = (base.red * 0.98f).coerceIn(0f, 1f),
        green = (base.green * 0.99f).coerceIn(0f, 1f),
        blue = (base.blue * 0.92f).coerceIn(0f, 1f),
        alpha = base.alpha
    )

// ─── Dokunma bölgeleri ───────────────────────────────────────────────────────

enum class MangaTapZone(val key: String) {
    /** Sol %33 geri / sağ %33 ileri (RTL'de otomatik ters) — varsayılan. */
    Default("Default"),

    /** Dokunma bölgeleri okuma yönüne göre ters çevrilir. */
    Inverted("Inverted"),

    /** Sadece orta dokunma (bar aç/kapat); sayfa çevirme yok. */
    Disabled("Disabled");

    companion object {
        fun fromKey(key: String?): MangaTapZone = when (key?.trim()) {
            "Inverted", "Invert", "Reverse" -> Inverted
            "Disabled", "Off", "None" -> Disabled
            else -> Default
        }
    }
}

// ─── Hareket / animasyon ─────────────────────────────────────────────────────

/**
 * Sayfa geçiş animasyonu (Kotatsu `ReaderAnimation`: NONE / DEFAULT / ADVANCED).
 * `Default` = standart hız, `Fast` = kısa süre, `None` = animasyonsuz.
 */
enum class MangaPageAnimation(val key: String) {
    None("None"),
    Default("Default"),
    Fast("Fast");

    companion object {
        fun fromKey(key: String?): MangaPageAnimation = when (key?.trim()) {
            "None", "NONE", "Off" -> None
            "Fast", "ADVANCED", "Quick" -> Fast
            else -> Default
        }
        val cycleOrder: List<MangaPageAnimation> = listOf(Default, Fast, None)
    }
}

/** Yan menü / bar otomatik gizleme hassasiyeti (Mihon "sensitivity for hiding menu"). */
enum class MangaScrollHideSensitivity(val key: String) {
    Off("Off"),
    Low("Low"),
    High("High");

    companion object {
        fun fromKey(key: String?): MangaScrollHideSensitivity = when (key?.trim()?.lowercase()) {
            null, "off", "none", "false" -> Off
            "high", "highest" -> High
            else -> Low
        }
        val cycleOrder: List<MangaScrollHideSensitivity> =
            listOf(Low, High, Off)
    }
}

/** Kenar kırpma profili — Kotatsu'da ayrı ayrı standard/webtoon ayarı vardır. */
enum class MangaCropProfile(val key: String) {
    Off("Off"),
    White("White"),
    Black("Black"),
    Auto("Auto");

    companion object {
        fun fromKey(key: String?): MangaCropProfile = when (key?.trim()) {
            "White", "On", "true" -> White
            "Black" -> Black
            "Auto", "Both" -> Auto
            else -> Off
        }
        val cycleOrder: List<MangaCropProfile> = listOf(Off, White, Auto, Black)
    }
}

// ─── Bar düzeni ─────────────────────────────────────────────────────────────

/**
 * Alt/üst bar yerleşimi.
 *
 * Hızlı okurken bar **ekranın büyük kısmını kaplamamalı**. Üç kademe:
 *
 * - [Full] — mevcut düzen (slider satırı + etiketli ikon satırı). **Varsayılan**,
 *   hiçbir şey değişmez.
 * - [Compact] — tek satır: etiketsiz ikonlar + üstte ince ilerleme çizgisi.
 *   Yükseklik yaklaşık yarıya iner.
 * - [Minimal] — yalnızca küçük bir kapsül: "13/52" + ince ilerleme. Ikon yok;
 *   orta dokunma/`M` ile yine de açılabilir.
 *
 * Hiçbiri dokunma bölgelerini veya klavye kısayollarını değiştirmez.
 */
enum class MangaBarStyle(val key: String) {
    Full("Full"),
    Compact("Compact"),
    Minimal("Minimal");

    companion object {
        fun fromKey(key: String?): MangaBarStyle = when (key?.trim()?.lowercase()) {
            "compact", "slim", "tek" -> Compact
            "minimal", "thin", "kapsul" -> Minimal
            else -> Full
        }
        val cycleOrder: List<MangaBarStyle> = listOf(Full, Compact, Minimal)
    }
}

// ─── Uzun dokunma / zoom / flaş ───────────────────────────────────────────────

/** Mihon "Long tap action": menü veya doğrudan eylem. */
enum class MangaLongTapAction(val key: String) {
    Menu("Menu"),
    SavePage("SavePage"),
    SharePage("SharePage"),
    CopyLink("CopyLink");

    companion object {
        fun fromKey(key: String?): MangaLongTapAction? = when (key?.trim()) {
            "Menu" -> Menu
            "SavePage", "Save" -> SavePage
            "SharePage", "Share" -> SharePage
            "CopyLink", "Link" -> CopyLink
            else -> null
        }
        val cycleOrder: List<MangaLongTapAction> = listOf(Menu, SavePage, SharePage, CopyLink)
    }
}

/** Mihon `ZoomStartType` — yakınlaştırılmış sayfanın başlangıç hizası. */
enum class MangaZoomStart(val key: String) {
    Automatic("Automatic"),
    Left("Left"),
    Right("Right"),
    Center("Center");

    companion object {
        fun fromKey(key: String?): MangaZoomStart? = when (key?.trim()) {
            "Automatic", "Auto" -> Automatic
            "Left" -> Left
            "Right" -> Right
            "Center" -> Center
            else -> null
        }
        val cycleOrder: List<MangaZoomStart> = listOf(Automatic, Center, Left, Right)
    }
}

/** Mihon `FlashColor`. */
enum class MangaFlashColor(val key: String) {
    Black("Black"),
    White("White"),
    WhiteBlack("WhiteBlack");

    companion object {
        fun fromKey(key: String?): MangaFlashColor? = when (key?.trim()) {
            "Black" -> Black
            "White" -> White
            "WhiteBlack", "WhiteAndBlack" -> WhiteBlack
            else -> null
        }
        val cycleOrder: List<MangaFlashColor> = listOf(White, Black, WhiteBlack)
    }
}

// ─── Görsel kalibrasyon ──────────────────────────────────────────────────────

/**
 * Telefon, tablet ve TV'nin **ortak** kullandığı görüntü işleme.
 *
 * Tüm parametreler tek bir 4x5 renk matrisine katlanır; GPU'da bedava uygulanır
 * (piksel kopyası yok → bellek dostu, Kotatsu Next'in GPU yaklaşımıyla aynı ilke).
 */
object MangaImageFilters {

    /** Nötr sonuç üretilip üretilmeyeceğini söyler (gereksiz `ColorFilter` önlenir). */
    fun isIdentity(
        filter: ColorFilterType,
        contrast: Float = 1f,
        saturation: Float = 1f,
        blueLight: Float = 0f,
        bookEffect: Boolean = false
    ): Boolean = !bookEffect &&
        filter == ColorFilterType.Normal &&
        kotlin.math.abs(contrast - 1f) < 0.01f &&
        kotlin.math.abs(saturation - 1f) < 0.01f &&
        blueLight <= 0.01f

    fun build(
        filter: ColorFilterType,
        contrast: Float = 1f,
        saturation: Float = 1f,
        blueLight: Float = 0f,
        /** Kotatsu "book background": mavi kanalı hafifçe kısar (kağıt hissi). */
        bookEffect: Boolean = false
    ): ColorFilter? {
        if (isIdentity(filter, contrast, saturation, blueLight, bookEffect)) return null
        val matrix = pipeline(contrast, saturation, blueLight, bookEffect) * filterMatrix(filter)
        return ColorFilter.colorMatrix(ColorMatrix(matrix))
    }

    /** Kotatsu `ReaderColorFilter.addBookEffect()` uyarlaması (mavi × 0.92). */
    fun bookEffectMatrix(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 0.92f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )

    /** Filtrenin kendi matrisi. */
    fun filterMatrix(filter: ColorFilterType): FloatArray = when (filter) {
        ColorFilterType.Normal -> identity()
        ColorFilterType.Grayscale -> floatArrayOf(
            0.2126f, 0.7152f, 0.0722f, 0f, 0f,
            0.2126f, 0.7152f, 0.0722f, 0f, 0f,
            0.2126f, 0.7152f, 0.0722f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        ColorFilterType.Sepia -> floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        ColorFilterType.Invert -> floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )
        ColorFilterType.Warm -> floatArrayOf(
            1.07f, 0f, 0f, 0f, 6f,
            0f, 1.01f, 0f, 0f, 2f,
            0f, 0f, 0.93f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        ColorFilterType.Cool -> floatArrayOf(
            0.94f, 0f, 0f, 0f, 0f,
            0f, 1.0f, 0f, 0f, 2f,
            0f, 0f, 1.08f, 0f, 8f,
            0f, 0f, 0f, 1f, 0f
        )
        ColorFilterType.HighContrast -> floatArrayOf(
            1.28f, 0f, 0f, 0f, -32f,
            0f, 1.28f, 0f, 0f, -32f,
            0f, 0f, 1.28f, 0f, -32f,
            0f, 0f, 0f, 1f, 0f
        )
        ColorFilterType.Soft -> floatArrayOf(
            0.88f, 0.04f, 0.04f, 0f, 26f,
            0.04f, 0.88f, 0.04f, 0f, 26f,
            0.04f, 0.04f, 0.88f, 0f, 26f,
            0f, 0f, 0f, 1f, 0f
        )
    }

    /**
     * Kontrast + doygunluk + mavi ışık (+ isteğe bağlı kitap efekti) tek
     * matriste birleşir.
     */
    fun pipeline(
        contrast: Float,
        saturation: Float,
        blueLight: Float,
        bookEffect: Boolean = false
    ): FloatArray {
        val c = contrast.coerceIn(0.5f, 2.0f)
        val s = saturation.coerceIn(0f, 2.0f)
        val blue = blueLight.coerceIn(0f, 1f)

        // Doygunluk (luma katsayıları 0.2126 / 0.7152 / 0.0722)
        val sat = floatArrayOf(
            (0.2126f + 0.7874f * s), (0.7152f - 0.7152f * s), (0.0722f - 0.0722f * s), 0f, 0f,
            (0.2126f - 0.2126f * s), (0.7152f + 0.2848f * s), (0.0722f - 0.0722f * s), 0f, 0f,
            (0.2126f - 0.2126f * s), (0.7152f - 0.7152f * s), (0.0722f + 0.9278f * s), 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )

        // Kontrast: ölçek + 128 merkezli ofset
        val offset = 128f * (1f - c)
        val con = floatArrayOf(
            c, 0f, 0f, 0f, offset,
            0f, c, 0f, 0f, offset,
            0f, 0f, c, 0f, offset,
            0f, 0f, 0f, 1f, 0f
        )

        // Mavi ışık: mavi kanalı kıs, sıcaklığı hafifçe artır
        val warmShift = 18f * blue
        val bl = floatArrayOf(
            1f + 0.04f * blue, 0f, 0f, 0f, warmShift,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f - 0.32f * blue, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )

        val combined = bl * con * sat
        // Kitap efekti (Kotatsu addBookEffect): mavi kanal hafifçe kısılır.
        return if (bookEffect) bookEffectMatrix() * combined else combined
    }

    private fun identity() = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )
}

/** Android `ColorMatrix` ile aynı düzeni kullanan 4x5 matris çarpımı (a * b). */
operator fun FloatArray.times(other: FloatArray): FloatArray {
    require(size == 20 && other.size == 20) { "4x5 renk matrisi bekleniyordu" }
    val out = FloatArray(20)
    for (row in 0 until 4) {
        for (col in 0 until 5) {
            var sum = 0f
            for (k in 0 until 4) {
                sum += this[row * 5 + k] * other[k * 5 + col]
            }
            // Ofset kolonunda (col == 4) sabit terim de eklenir
            if (col == 4) sum += this[row * 5 + 4]
            out[row * 5 + col] = sum
        }
    }
    return out
}

enum class MangaTapNavigation { Standard, Inverted, Disabled }

enum class MangaReaderTheme(val backgroundArgb: Long, val textArgb: Long) {
    Black(0xFF000000, 0xFFFFFFFF),
    Dark(0xFF17171D, 0xFFF5F5F7),
    White(0xFFFFFFFF, 0xFF202124),
    Sepia(0xFFF4ECD8, 0xFF4A3B2A);

    companion object {
        fun fromKey(key: String?): MangaReaderTheme = entries.firstOrNull { it.name.equals(key, true) } ?: Black
    }
}

enum class MangaNovelFont {
    Sans, Serif, Monospace;

    companion object {
        fun fromKey(key: String?): MangaNovelFont = entries.firstOrNull { it.name.equals(key, true) } ?: Sans
    }
}

enum class MangaNovelTextAlign {
    Start, Justify, Center;

    companion object {
        fun fromKey(key: String?): MangaNovelTextAlign = entries.firstOrNull { it.name.equals(key, true) } ?: Start
    }
}

// ─── Okuyucu seçenekleri ─────────────────────────────────────────────────────

/**
 * Okuyucunun tüm ayarları. Tek JSON anahtarında saklanır.
 *
 * Not: Alanlar bilinçli olarak "düz" tutuldu; yeni alan eklemek geriye dönük
 * uyumludur çünkü [fromJson] eksik alanlarda varsayılana düşer.
 */
data class MangaReaderOptions(
    /** Okuma yönü. */
    val readingMode: ReadingMode = ReadingMode.RightToLeft,
    /** Renk filtresi. */
    val colorFilter: ColorFilterType = ColorFilterType.Normal,
    /** Sayfa sığdırma. */
    val fitMode: MangaFitMode = MangaFitMode.FitScreen,
    /** Yapay parlaklık (0.1 – 1.0). */
    val brightness: Float = 1.0f,
    /** Kontrast (0.5 – 2.0). */
    val contrast: Float = 1.0f,
    /** Doygunluk / vibrance (0 – 2). */
    val saturation: Float = 1.0f,
    /** Mavi ışık filtresi (0 – 1). */
    val blueLight: Float = 0f,
    /** Sayfa zemini. */
    val background: MangaPageBackground = MangaPageBackground.Black,
    /**
     * Kenar kırpma profili (kapalı / beyaz / siyah / otomatik).
     *
     * **Tek doğruluk kaynağı.** Eski `cropBorders` boolean'ı kaldırıldı; JSON'da
     * hâlâ okunur (`cropBorders=true` → `White`) ki kayıtlı ayar kaybolmasın.
     */
    val cropProfile: MangaCropProfile = MangaCropProfile.Off,
    /** Kenar kırpma toleransı (0 = kesin tek renk, 40 = gevşek). */
    val cropTolerance: Int = 18,
    /** Webtoon modunda sol/sağ boşluk (dp). */
    val sidePadding: Int = 0,
    /** Webtoon modunda sayfalar arası boşluk (dp). */
    val pageGap: Int = 4,
    /** Sayfa numarasını göster. */
    val showPageNumber: Boolean = true,
    /** Ekranı açık tut. */
    val keepScreenOn: Boolean = true,
    /** Sistem çubuklarını gizle (immersive). */
    val immersive: Boolean = true,
    /** Dokunma bölgeleri. */
    val tapZone: MangaTapZone = MangaTapZone.Default,
    /** Hacim tuşlarıyla sayfa çevirme. */
    val volumeKeys: Boolean = false,
    /** Hacim tuşlarını ters çevir. */
    val invertVolumeKeys: Boolean = false,
    /**
     * Uzun şerit (webtoon) modunda yakınlaştırma hareketleri.
     * Mihon'daki "Disable double tap zoom for long strip" ayarının uyarlaması:
     * kapalıyken webtoon modunda pinch/çift dokunma zoom devre dışı kalır,
     * kaydırma hiçbir zaman engellenmez.
     */
    val webtoonZoom: Boolean = true,
    /** Pager modunda çift sayfa (yayılım). */
    val dualPage: Boolean = false,
    /**
     * Çift sayfa düzeninde **ilk sayfayı tek göster** (Mihon `dualPageSplit`
     * değil, "first page single" davranışı).
     *
     * Kapak her zaman tek sayfadır; açıkken 0. sayfa tek başına, sonrası
     * (1,2), (3,4)… şeklinde eşleşir. Kapalıyken (0,1), (2,3)… eşleşir.
     */
    val firstPageSingle: Boolean = false,
    /** Çift sayfa sırasını ters çevir (sağ-sol yer değiştirir). */
    val dualPageInvert: Boolean = false,
    /** Bölüm açılırken webtoon ise otomatik webtoon moduna geç. */
    val autoWebtoon: Boolean = true,
    /** Bölüm/sayfa sonunda otomatik sonraki bölüme geç (sn). 0 = kapalı. */
    val autoNextChapterSeconds: Int = 0,
    /** Uzun şerit sonundaki bölüm geçiş kartı. */
    val chapterTransitionCard: Boolean = true,
    /** Otomatik kaydırma hızı (dp/sn). 0 = kapalı. */
    val autoScrollSpeed: Int = 0,
    /** Sayfa değişiminde kısa beyaz flaş (e-ink). */
    val einkFlash: Boolean = false,
    /** Uzun dokunma eylem menüsü (kaydet / paylaş / bağlantı). */
    val longTapActions: Boolean = true,
    /** Uzun dokunmada açılacak menü yerine doğrudan eylem (Mihon ile aynı seçenekler). */
    val longTapAction: MangaLongTapAction = MangaLongTapAction.Menu,
    /**
     * Yakınlaştırılmış sayfa ekrandan küçükken nereye yerleşsin
     * (Mihon `ZoomStartType`). Sayfa **zoom yapılmamışsa** etkisi yoktur.
     */
    val zoomStart: MangaZoomStart = MangaZoomStart.Center,
    /** E-ink flaş rengi (Mihon `FlashColor`). */
    val flashColor: MangaFlashColor = MangaFlashColor.White,
    /** Flaşın ekranda kalma süresi (ms). */
    val flashDurationMs: Int = 90,
    /** Kaç sayfada bir flaş uygulanır (1 = her sayfa). */
    val flashInterval: Int = 1,
    /** Bölüm sonunda okunmuş bölümleri atla (Mihon `skipRead`). */
    val skipReadChapters: Boolean = false,
    /** Geniş sayfa çevirmede yönü ters çevir (Mihon `pageRotateInvert`). */
    val rotateWidePagesInvert: Boolean = false,

    // ─── Kotatsu / Mihon paritesi (2026-11-07 · 2. tur) ────────────────────

    /** Sayfa opaklığı (0.2 – 1.0). Kotatsu "opacity" / gece okuma için. */
    val pageOpacity: Float = 1.0f,
    /** Kritik okuma tercihine göre (ARGB_8888) çöz — "32-bit color". */
    val highResColors: Boolean = false,
    /** Pager modlarında sayfa geçiş animasyonu. */
    val pageAnimation: MangaPageAnimation = MangaPageAnimation.Default,
    /** Uzun şeritte kaydırırken barları otomatik gizle. */
    val autoHideMenuOnScroll: MangaScrollHideSensitivity = MangaScrollHideSensitivity.Low,
    /** Üstte okuma bilgi çubuğu (saat + bölüm/sayfa ilerlemesi). */
    val infoBar: Boolean = false,
    /** Okuyucuda yakınlaştırma (+/−) düğmeleri. */
    val zoomButtons: Boolean = false,
    /** Alt bardaki hızlı düğmeler (Kotatsu `ReaderControl`): slider, bölüm listesi, döndürme… */
    /** Alt bardaki sayfa slider satırı. */
    val showSlider: Boolean = true,
    /** Alt barda önceki/sonraki bölüm düğmeleri. */
    val showChapterButtons: Boolean = true,
    /** Alt barda sayfa ızgarası düğmesi (varsayılan kapalı → düzen aynı kalır). */
    val showPagesSheet: Boolean = false,
    /** Alt barda otomatik kaydırma düğmesi. */
    val showScrollTimer: Boolean = true,
    /** Alt barda yer imi düğmesi (varsayılan kapalı → düzen aynı kalır). */
    val showBookmarkButton: Boolean = false,
    /**
     * Geniş (çift sayfa yayılımı) sayfaları 90° çevir (Mihon "Rotate wide pages").
     * Sayfa sayısını **değiştirmez**, bu yüzden ilerleme/geri yükleme bozulmaz.
     */
    val rotateWidePages: Boolean = false,
    /** "Geniş sayfa" eşiği: genişlik / yükseklik. Varsayılan 1.2 (Mihon ile aynı). */
    val widePageThreshold: Float = 1.2f,
    /** Yatay modda otomatik çift sayfa (Kotatsu "two pages on landscape"). */
    val dualPageOnLandscape: Boolean = false,
    /**
     * Bu manga için **okuma biçimini** (mod, sığdırma, çift sayfa, kırpma, geniş
     * sayfa) hatırla — Mihon "per-series override" paritesi. Kalibrasyon/parlaklık
     * gibi ekran ayarları global kalır (ikinci bir ayar sistemi oluşmaz).
     */
    val perMangaMode: Boolean = true,
    /** Kitap efekti arka plan tonu (Kotatsu "book background"). */
    val bookEffect: Boolean = false,
    /** Alt/üst bar yerleşimi (varsayılan: mevcut iki satırlı düzen). */
    val barStyle: MangaBarStyle = MangaBarStyle.Full,
    val readerTheme: MangaReaderTheme = MangaReaderTheme.Black,
    val novelShowBatteryAndClock: Boolean = true,
    val novelMarginDp: Int = 20,
    val bionicReading: Boolean = false,
    val novelFontSizeSp: Int = 18,
    val novelLineHeight: Float = 1.5f,
    val novelFontFamily: MangaNovelFont = MangaNovelFont.Sans,
    val novelTextAlign: MangaNovelTextAlign = MangaNovelTextAlign.Start,
    val novelSpeechRate: Float = 1f,
    val novelSpeechHighlight: Boolean = true,
    val cropWebtoonBorders: Boolean = false,
    val showProgressPercent: Boolean = true,
    val reverseVolumeKeys: Boolean = false,
    val pageTransitions: Boolean = true,
    val longTapNavigation: Boolean = false,
    val preloadAhead: Int = 3,
    val incognito: Boolean = false,
    val autoScroll: Boolean = false,
) {

    val doublePage: Boolean get() = dualPage
    val landscapeDoublePage: Boolean get() = dualPageOnLandscape

    fun sanitized(): MangaReaderOptions = copy(
        sidePadding = sidePadding.coerceIn(0, 32),
        pageGap = pageGap.coerceIn(0, 32),
        pageOpacity = if (pageOpacity.isFinite()) pageOpacity.coerceIn(0.1f, 1f) else 1f,
        preloadAhead = preloadAhead.coerceIn(0, 8),
        autoScrollSpeed = autoScrollSpeed.coerceIn(8, 600),
        novelFontSizeSp = novelFontSizeSp.coerceIn(12, 36),
        novelLineHeight = if (novelLineHeight.isFinite()) novelLineHeight.coerceIn(1f, 2.5f) else 1.5f,
        novelMarginDp = novelMarginDp.coerceIn(0, 48),
        novelSpeechRate = if (novelSpeechRate.isFinite()) novelSpeechRate.coerceIn(0.5f, 2f) else 1f,
    )

    /** Bölüm sonu otomatik geçiş açık mı? (tek kaynak: süre > 0) */
    val autoNextEnabled: Boolean get() = autoNextChapterSeconds > 0

    /** Okuma modu pager mi? (kısayollar için) */
    val isPaged: Boolean get() = readingMode != ReadingMode.Webtoon

    /**
     * Çift sayfa gerçekten etkin mi?
     * `dualPage` elle açılabilir veya yatay ekranda `dualPageOnLandscape` ile
     * otomatik devreye girer. Ekran yönü çağrı yerinde verilir (tek kaynak burada).
     */
    fun dualPageActive(isLandscape: Boolean = false): Boolean =
        readingMode != ReadingMode.Webtoon && (dualPage || (dualPageOnLandscape && isLandscape))


    /** Kenar kırpma açık mı? (tek kaynak: [cropProfile]) */
    val cropEnabled: Boolean get() = cropProfile != MangaCropProfile.Off
    val cropBorders: Boolean get() = cropEnabled
    val sidePaddingDp: Int get() = sidePadding
    val pageGapDp: Int get() = pageGap


    /**
     * Görüntü kalibrasyonu (kontrast/doygunluk/mavi ışık/kitap efekti/filtre)
     * varsayılandan farklı mı? Tek yerden karar verilir — UI'da ayrı ayrı
     * "eşik" karşılaştırması yazılmaz.
     */
    val hasCalibration: Boolean
        get() = !MangaImageFilters.isIdentity(
            filter = colorFilter,
            contrast = contrast,
            saturation = saturation,
            blueLight = blueLight,
            bookEffect = bookEffect
        )

    /** Kalibrasyonu varsayılana döndürür (tek "sıfırla" yolu). */
    fun resetCalibration(): MangaReaderOptions = copy(
        colorFilter = ColorFilterType.Normal,
        contrast = 1f,
        saturation = 1f,
        blueLight = 0f,
        bookEffect = false,
        brightness = 1f
    )

    // ── Serileştirme ──────────────────────────────────────────────────────

    fun toJson(): String = JSONObject().apply {
        put("readingMode", readingMode.toKey())
        put("colorFilter", colorFilter.key)
        put("fitMode", fitMode.key)
        put("brightness", brightness.toDouble())
        put("contrast", contrast.toDouble())
        put("saturation", saturation.toDouble())
        put("blueLight", blueLight.toDouble())
        put("background", background.key)
        put("cropProfile", cropProfile.key)
        put("cropTolerance", cropTolerance)
        put("sidePadding", sidePadding)
        put("pageGap", pageGap)
        put("showPageNumber", showPageNumber)
        put("keepScreenOn", keepScreenOn)
        put("immersive", immersive)
        put("tapZone", tapZone.key)
        put("volumeKeys", volumeKeys)
        put("invertVolumeKeys", invertVolumeKeys)
        put("webtoonZoom", webtoonZoom)
        put("dualPage", dualPage)
        put("firstPageSingle", firstPageSingle)
        put("dualPageInvert", dualPageInvert)
        put("autoWebtoon", autoWebtoon)
        put("autoNextChapterSeconds", autoNextChapterSeconds)
        put("chapterTransitionCard", chapterTransitionCard)
        put("autoScrollSpeed", autoScrollSpeed)
        put("einkFlash", einkFlash)
        put("longTapActions", longTapActions)
        put("longTapAction", longTapAction.key)
        put("zoomStart", zoomStart.key)
        put("flashColor", flashColor.key)
        put("flashDurationMs", flashDurationMs)
        put("flashInterval", flashInterval)
        put("skipReadChapters", skipReadChapters)
        put("rotateWidePagesInvert", rotateWidePagesInvert)
        put("pageOpacity", pageOpacity.toDouble())
        put("highResColors", highResColors)
        put("pageAnimation", pageAnimation.key)
        put("autoHideMenuOnScroll", autoHideMenuOnScroll.key)
        put("barStyle", barStyle.key)
        put("infoBar", infoBar)
        put("zoomButtons", zoomButtons)
        put("showSlider", showSlider)
        put("showChapterButtons", showChapterButtons)
        put("showPagesSheet", showPagesSheet)
        put("showScrollTimer", showScrollTimer)
        put("showBookmarkButton", showBookmarkButton)
        put("rotateWidePages", rotateWidePages)
        put("widePageThreshold", widePageThreshold.toDouble())
        put("dualPageOnLandscape", dualPageOnLandscape)
        put("perMangaMode", perMangaMode)
        put("bookEffect", bookEffect)
        put("readerTheme", readerTheme.name)
        put("novelShowBatteryAndClock", novelShowBatteryAndClock)
        put("novelMarginDp", novelMarginDp)
        put("bionicReading", bionicReading)
        put("novelFontSizeSp", novelFontSizeSp)
        put("novelLineHeight", novelLineHeight.toDouble())
        put("novelFontFamily", novelFontFamily.name)
        put("novelTextAlign", novelTextAlign.name)
        put("novelSpeechRate", novelSpeechRate.toDouble())
        put("novelSpeechHighlight", novelSpeechHighlight)
        put("cropWebtoonBorders", cropWebtoonBorders)
        put("showProgressPercent", showProgressPercent)
    }.toString()

    companion object {
        val DEFAULT = MangaReaderOptions()

        /**
         * JSON'u çözer. Eksik alanlar [fallback] değerlerinden gelir — bu sayede
         * eski sürümden gelen kullanıcıların `manga_reading_mode` gibi tekil
         * anahtarları kaybolmaz.
         */
        fun fromJson(
            raw: String?,
            fallback: MangaReaderOptions = DEFAULT
        ): MangaReaderOptions {
            if (raw.isNullOrBlank()) return fallback
            val json = runCatching { JSONObject(raw) }.getOrNull() ?: return fallback
            fun str(key: String): String? = if (json.has(key)) json.optString(key) else null
            fun bool(key: String, default: Boolean): Boolean =
                if (json.has(key)) json.optBoolean(key, default) else default
            fun int(key: String, default: Int): Int =
                if (json.has(key)) json.optInt(key, default) else default
            fun float(key: String, default: Float): Float =
                if (json.has(key)) json.optDouble(key, default.toDouble()).toFloat() else default

            return fallback.copy(
                readingMode = str("readingMode")?.let { ReadingMode.fromKey(it) } ?: fallback.readingMode,
                colorFilter = str("colorFilter")?.let { ColorFilterType.fromKey(it) } ?: fallback.colorFilter,
                fitMode = str("fitMode")?.let { MangaFitMode.fromKey(it) } ?: fallback.fitMode,
                brightness = float("brightness", fallback.brightness).coerceIn(0.1f, 1f),
                contrast = float("contrast", fallback.contrast).coerceIn(0.5f, 2f),
                saturation = float("saturation", fallback.saturation).coerceIn(0f, 2f),
                blueLight = float("blueLight", fallback.blueLight).coerceIn(0f, 1f),
                background = str("background")?.let { MangaPageBackground.fromKey(it) } ?: fallback.background,
                cropProfile = when {
                    str("cropProfile") != null -> MangaCropProfile.fromKey(str("cropProfile"))
                    // Eski sürüm: `cropBorders=true` → beyaz kenar profili
                    json.has("cropBorders") && json.optBoolean("cropBorders", false) ->
                        MangaCropProfile.White
                    // Eski sürüm: `cropBorders=false` → kapalı
                    json.has("cropBorders") -> MangaCropProfile.Off
                    else -> fallback.cropProfile
                },
                cropTolerance = int("cropTolerance", fallback.cropTolerance).coerceIn(0, 60),
                sidePadding = int("sidePadding", fallback.sidePadding).coerceIn(0, 80),
                pageGap = int("pageGap", fallback.pageGap).coerceIn(0, 40),
                showPageNumber = bool("showPageNumber", fallback.showPageNumber),
                keepScreenOn = bool("keepScreenOn", fallback.keepScreenOn),
                immersive = bool("immersive", fallback.immersive),
                tapZone = str("tapZone")?.let { MangaTapZone.fromKey(it) } ?: fallback.tapZone,
                volumeKeys = bool("volumeKeys", fallback.volumeKeys),
                invertVolumeKeys = bool("invertVolumeKeys", fallback.invertVolumeKeys),
                webtoonZoom = bool("webtoonZoom", fallback.webtoonZoom),
                dualPage = bool("dualPage", fallback.dualPage),
                firstPageSingle = bool("firstPageSingle", fallback.firstPageSingle),
                dualPageInvert = bool("dualPageInvert", fallback.dualPageInvert),
                autoWebtoon = bool("autoWebtoon", fallback.autoWebtoon),
                autoNextChapterSeconds = int("autoNextChapterSeconds", fallback.autoNextChapterSeconds).coerceIn(0, 60),
                chapterTransitionCard = bool("chapterTransitionCard", fallback.chapterTransitionCard),
                autoScrollSpeed = int("autoScrollSpeed", fallback.autoScrollSpeed).coerceIn(0, 600),
                einkFlash = bool("einkFlash", fallback.einkFlash),
                longTapActions = bool("longTapActions", fallback.longTapActions),
                longTapAction = MangaLongTapAction.fromKey(str("longTapAction"))
                    ?: fallback.longTapAction,
                zoomStart = MangaZoomStart.fromKey(str("zoomStart")) ?: fallback.zoomStart,
                flashColor = MangaFlashColor.fromKey(str("flashColor")) ?: fallback.flashColor,
                flashDurationMs = int("flashDurationMs", fallback.flashDurationMs).coerceIn(30, 1000),
                flashInterval = int("flashInterval", fallback.flashInterval).coerceIn(1, 10),
                skipReadChapters = bool("skipReadChapters", fallback.skipReadChapters),
                rotateWidePagesInvert = bool("rotateWidePagesInvert", fallback.rotateWidePagesInvert),
                pageOpacity = float("pageOpacity", fallback.pageOpacity).coerceIn(0.2f, 1f),
                highResColors = bool("highResColors", fallback.highResColors),
                pageAnimation = str("pageAnimation")?.let { MangaPageAnimation.fromKey(it) } ?: fallback.pageAnimation,
                autoHideMenuOnScroll = str("autoHideMenuOnScroll")?.let { MangaScrollHideSensitivity.fromKey(it) }
                    ?: fallback.autoHideMenuOnScroll,
                barStyle = str("barStyle")?.let { MangaBarStyle.fromKey(it) } ?: fallback.barStyle,
                infoBar = bool("infoBar", fallback.infoBar),
                zoomButtons = bool("zoomButtons", fallback.zoomButtons),
                showSlider = bool("showSlider", fallback.showSlider),
                showChapterButtons = bool("showChapterButtons", fallback.showChapterButtons),
                showPagesSheet = bool("showPagesSheet", fallback.showPagesSheet),
                showScrollTimer = bool("showScrollTimer", fallback.showScrollTimer),
                showBookmarkButton = bool("showBookmarkButton", fallback.showBookmarkButton),
                rotateWidePages = bool("rotateWidePages", fallback.rotateWidePages),
                widePageThreshold = float("widePageThreshold", fallback.widePageThreshold).coerceIn(1f, 2f),
                dualPageOnLandscape = bool("dualPageOnLandscape", fallback.dualPageOnLandscape),
                perMangaMode = bool("perMangaMode", fallback.perMangaMode),
                bookEffect = bool("bookEffect", fallback.bookEffect),
                readerTheme = str("readerTheme")?.let { MangaReaderTheme.fromKey(it) } ?: fallback.readerTheme,
                novelShowBatteryAndClock = bool("novelShowBatteryAndClock", fallback.novelShowBatteryAndClock),
                novelMarginDp = int("novelMarginDp", fallback.novelMarginDp).coerceIn(0, 48),
                bionicReading = bool("bionicReading", fallback.bionicReading),
                novelFontSizeSp = int("novelFontSizeSp", fallback.novelFontSizeSp).coerceIn(12, 36),
                novelLineHeight = float("novelLineHeight", fallback.novelLineHeight).coerceIn(1f, 2.5f),
                novelFontFamily = str("novelFontFamily")?.let { MangaNovelFont.fromKey(it) } ?: fallback.novelFontFamily,
                novelTextAlign = str("novelTextAlign")?.let { MangaNovelTextAlign.fromKey(it) } ?: fallback.novelTextAlign,
                novelSpeechRate = float("novelSpeechRate", fallback.novelSpeechRate).coerceIn(0.5f, 2f),
                novelSpeechHighlight = bool("novelSpeechHighlight", fallback.novelSpeechHighlight),
                cropWebtoonBorders = bool("cropWebtoonBorders", fallback.cropWebtoonBorders),
                showProgressPercent = bool("showProgressPercent", fallback.showProgressPercent),
            )
        }
    }
}

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
    else -> ColorFilterType.Normal
}

fun nextMangaReadingMode(current: ReadingMode): ReadingMode = when (current) {
    ReadingMode.RightToLeft -> ReadingMode.LeftToRight
    ReadingMode.LeftToRight -> ReadingMode.Vertical
    ReadingMode.Vertical -> ReadingMode.Webtoon
    ReadingMode.Webtoon -> ReadingMode.RightToLeft
}

data class MangaReaderPreferencesModel(
    val readingMode: ReadingMode = ReadingMode.RightToLeft,
    val colorFilter: ColorFilterType = ColorFilterType.Normal,
    val fitMode: MangaFitMode = MangaFitMode.FitScreen,
    val brightness: Float = 1f,
    val options: MangaReaderOptions = MangaReaderOptions(),
) {
    fun sanitized(): MangaReaderPreferencesModel = copy(
        brightness = if (brightness.isFinite()) brightness.coerceIn(0.1f, 1f) else 1f,
        options = options.sanitized(),
    )
}
