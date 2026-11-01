package com.kitsugi.animelist.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * KITSUGI HD İKON SETİ (tek kaynak)
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Uygulamadaki tüm buton / tıklanır alan / açılır-kapanır kontrol ikonları bu
 * nesneden gelir. Material ikonlarının kalın dolgulu görünümü yerine **şeffaf
 * arka planlı, ince çizgili, yüksek çözünürlüklü vektör** ikonlar kullanılır.
 * Renk hiçbir ikonda gömülü değildir: her ikon bulunduğu yerin `tint` değeriyle
 * boyanır (tema vurgusu ve gradyan modu otomatik uygulanır).
 *
 * Kaynak: Lucide (ISC lisansı) — 24dp ızgara, 2dp çizgi kalınlığı, yuvarlak uçlar.
 * İkonlar tembel (lazy) üretilir: yol verisi yalnızca ilk kullanımda ayrıştırılır.
 *
 * Göç: `import androidx.compose.material.icons.Icons` yerine
 * `import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons` kullanılır;
 * Material ikon adları (Icons.Rounded.X / Icons.Default.X / Icons.AutoMirrored... )
 * aşağıdaki uyumluluk adlarıyla birebir çalışmaya devam eder.
 */
object KitsugiIcons {

    /** Çizgi kalınlığı — tüm sette ortak (24dp ızgarada 2dp). */
    private const val STROKE = 2f

    private fun build(name: String, pathData: List<String>, solid: Boolean = false): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        )
        pathData.forEach { raw ->
            // "FILL:" önekli yollar (nokta/dolu daire) dolgu, diğerleri kontur olarak çizilir.
            val filled = solid || raw.startsWith("FILL:")
            val d = if (raw.startsWith("FILL:")) raw.removePrefix("FILL:") else raw
            // ⚠️ KRİTİK: `PathParser` kendi dâhilî düğüm listesini yeniden kullanır ve her
            // `parsePathString` çağrısının başında o listeyi TEMİZLER; `toNodes()` de aynı
            // listenin referansını döndürür. Tek bir parser paylaşıldığında `addPath` ile
            // eklenen tüm yollar aynı listeye bakıyordu ve döngü bitince liste yalnızca
            // SON yolun düğümlerini taşıyordu. Sonuç: çok yollu ikonların (saat, indirme,
            // grafik, bilgi, sohbet, yıldız…) sadece son parçası çiziliyor, ekranlarda
            // ikonlar "tek çubuk / tek çentik" gibi görünüyordu. Her yol için taze parser
            // ve kopyalanmış liste kullanılır.
            val pathNodes = PathParser().parsePathString(d).toNodes().toList()
            builder.addPath(
                pathData = pathNodes,
                fill = if (filled) SolidColor(Color.White) else null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            )
        }
        return builder.build()
    }

    /** Lucide `a-large-small`. */
    val ALargeSmall: ImageVector by lazy { build("ALargeSmall", listOf("m15 16 2.536-7.328a1.02 1.02 1 0 1 1.928 0L22 16", "M15.697 14h5.606", "m2 16 4.039-9.69a.5.5 0 0 1 .923 0L11 16", "M3.304 13h6.392")) }
    /** Lucide `archive`. */
    val Archive: ImageVector by lazy { build("Archive", listOf("M3.0 3.0h18.0a1.0 1.0 0 0 1 1.0 1.0v3.0a1.0 1.0 0 0 1 -1.0 1.0h-18.0a1.0 1.0 0 0 1 -1.0 -1.0v-3.0a1.0 1.0 0 0 1 1.0 -1.0Z", "M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8", "M10 12h4")) }
    /** Lucide `arrow-down`. */
    val ArrowDown: ImageVector by lazy { build("ArrowDown", listOf("M12 5v14", "m19 12-7 7-7-7")) }
    /** Lucide `arrow-down-a-z`. */
    val ArrowDownAZ: ImageVector by lazy { build("ArrowDownAZ", listOf("m3 16 4 4 4-4", "M7 20V4", "M20 8h-5", "M15 10V6.5a2.5 2.5 0 0 1 5 0V10", "M15 14h5l-5 6h5")) }
    /** Lucide `arrow-down-wide-narrow`. */
    val ArrowDownWideNarrow: ImageVector by lazy { build("ArrowDownWideNarrow", listOf("m3 16 4 4 4-4", "M7 20V4", "M11 4h10", "M11 8h7", "M11 12h4")) }
    /** Lucide `arrow-left`. */
    val ArrowLeft: ImageVector by lazy { build("ArrowLeft", listOf("m12 19-7-7 7-7", "M19 12H5")) }
    /** Lucide `arrow-left-right`. */
    val ArrowLeftRight: ImageVector by lazy { build("ArrowLeftRight", listOf("M8 3 4 7l4 4", "M4 7h16", "m16 21 4-4-4-4", "M20 17H4")) }
    /** Lucide `arrow-right`. */
    val ArrowRight: ImageVector by lazy { build("ArrowRight", listOf("M5 12h14", "m12 5 7 7-7 7")) }
    /** Lucide `arrow-up`. */
    val ArrowUp: ImageVector by lazy { build("ArrowUp", listOf("m5 12 7-7 7 7", "M12 19V5")) }
    /** Lucide `arrow-up-down`. */
    val ArrowUpDown: ImageVector by lazy { build("ArrowUpDown", listOf("m21 16-4 4-4-4", "M17 20V4", "m3 8 4-4 4 4", "M7 4v16")) }
    /** Lucide `arrow-up-narrow-wide`. */
    val ArrowUpNarrowWide: ImageVector by lazy { build("ArrowUpNarrowWide", listOf("m3 8 4-4 4 4", "M7 4v16", "M11 12h4", "M11 16h7", "M11 20h10")) }
    /** Lucide `arrow-up-z-a`. */
    val ArrowUpZA: ImageVector by lazy { build("ArrowUpZA", listOf("m3 8 4-4 4 4", "M7 4v16", "M15 4h5l-5 6h5", "M15 20v-3.5a2.5 2.5 0 0 1 5 0V20", "M20 18h-5")) }
    /** Lucide `audio-lines`. */
    val AudioLines: ImageVector by lazy { build("AudioLines", listOf("M2 10v3", "M6 6v11", "M10 3v18", "M14 8v7", "M18 5v13", "M22 10v3")) }
    /** Lucide `award`. */
    val Award: ImageVector by lazy { build("Award", listOf("m15.477 12.89 1.515 8.526a.5.5 0 0 1-.81.47l-3.58-2.687a1 1 0 0 0-1.197 0l-3.586 2.686a.5.5 0 0 1-.81-.469l1.514-8.526", "M6.0 8.0a6.0 6.0 0 1 0 12.0 0a6.0 6.0 0 1 0 -12.0 0Z")) }
    /** Lucide `baby`. */
    val Baby: ImageVector by lazy { build("Baby", listOf("M10 16c.5.3 1.2.5 2 .5s1.5-.2 2-.5", "M15 12h.01", "M19.38 6.813A9 9 0 0 1 20.8 10.2a2 2 0 0 1 0 3.6 9 9 0 0 1-17.6 0 2 2 0 0 1 0-3.6A9 9 0 0 1 12 3c2 0 3.5 1.1 3.5 2.5s-.9 2.5-2 2.5c-.8 0-1.5-.4-1.5-1", "M9 12h.01")) }
    /** Lucide `badge-plus`. */
    val BadgePlus: ImageVector by lazy { build("BadgePlus", listOf("M3.85 8.62a4 4 0 0 1 4.78-4.77 4 4 0 0 1 6.74 0 4 4 0 0 1 4.78 4.78 4 4 0 0 1 0 6.74 4 4 0 0 1-4.77 4.78 4 4 0 0 1-6.75 0 4 4 0 0 1-4.78-4.77 4 4 0 0 1 0-6.76Z", "M12 8L12 16", "M8 12L16 12")) }
    /** Lucide `ban`. */
    val Ban: ImageVector by lazy { build("Ban", listOf("M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0Z", "M4.929 4.929 19.07 19.071")) }
    /** Lucide `banknote`. */
    val Banknote: ImageVector by lazy { build("Banknote", listOf("M4.0 6.0h16.0a2.0 2.0 0 0 1 2.0 2.0v8.0a2.0 2.0 0 0 1 -2.0 2.0h-16.0a2.0 2.0 0 0 1 -2.0 -2.0v-8.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M10.0 12.0a2.0 2.0 0 1 0 4.0 0a2.0 2.0 0 1 0 -4.0 0Z", "M6 12h.01M18 12h.01")) }
    /** Lucide `baseline`. */
    val Baseline: ImageVector by lazy { build("Baseline", listOf("M4 20h16", "m6 16 6-12 6 12", "M8 12h8")) }
    /** Lucide `bell`. */
    val Bell: ImageVector by lazy { build("Bell", listOf("M10.268 21a2 2 0 0 0 3.464 0", "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326")) }
    /** Lucide `bell` — dolu (seçili/aktif) varyant. */
    val BellSolid: ImageVector by lazy { build("BellSolid", listOf("M10.268 21a2 2 0 0 0 3.464 0", "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326"), solid = true) }
    /** Lucide `bell-ring`. */
    val BellRing: ImageVector by lazy { build("BellRing", listOf("M10.268 21a2 2 0 0 0 3.464 0", "M22 8c0-2.3-.8-4.3-2-6", "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326", "M4 2C2.8 3.7 2 5.7 2 8")) }
    /** Lucide `blend`. */
    val Blend: ImageVector by lazy { build("Blend", listOf("M8.0 9.0a7.0 7.0 0 1 0 14.0 0a7.0 7.0 0 1 0 -14.0 0Z", "M2.0 15.0a7.0 7.0 0 1 0 14.0 0a7.0 7.0 0 1 0 -14.0 0Z")) }
    /** Lucide `book`. */
    val Book: ImageVector by lazy { build("Book", listOf("M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H19a1 1 0 0 1 1 1v18a1 1 0 0 1-1 1H6.5a1 1 0 0 1 0-5H20")) }
    /** Lucide `book-open`. */
    val BookOpen: ImageVector by lazy { build("BookOpen", listOf("M12 5v16", "M20.001 19A2 2 0 0022 17V5a2 2 0 00-1.999-2L16 3.002A5 5 0 0012 5a5 5 0 00-4-2H4a2 2 0 00-2 2v12a2 2 0 001.999 2H8a5 5 0 014 2 5 5 0 014-2z")) }
    /** Lucide `bookmark`. */
    val BookmarkStroke: ImageVector by lazy { build("BookmarkStroke", listOf("M17 3a2 2 0 0 1 2 2v15a1 1 0 0 1-1.496.868l-4.512-2.578a2 2 0 0 0-1.984 0l-4.512 2.578A1 1 0 0 1 5 20V5a2 2 0 0 1 2-2z")) }
    /** Lucide `bookmark` — dolu (seçili/aktif) varyant. */
    val BookmarkSolid: ImageVector by lazy { build("BookmarkSolid", listOf("M17 3a2 2 0 0 1 2 2v15a1 1 0 0 1-1.496.868l-4.512-2.578a2 2 0 0 0-1.984 0l-4.512 2.578A1 1 0 0 1 5 20V5a2 2 0 0 1 2-2z"), solid = true) }
    /** Lucide `bot`. */
    val Bot: ImageVector by lazy { build("Bot", listOf("M12 8V4H8", "M6.0 8.0h12.0a2.0 2.0 0 0 1 2.0 2.0v8.0a2.0 2.0 0 0 1 -2.0 2.0h-12.0a2.0 2.0 0 0 1 -2.0 -2.0v-8.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M2 14h2", "M20 14h2", "M15 13v2", "M9 13v2")) }
    /** Lucide `brain`. */
    val Brain: ImageVector by lazy { build("Brain", listOf("M12 18V5", "M15 13a4.17 4.17 0 01-3-4 4.17 4.17 0 01-3 4", "M17.707 6.294A3 3 0 1012 5a3 3 0 10-5.707 1.294", "M17.996 5.125a4 4 0 012.526 5.77", "M18 18a4 4 0 002-7.465", "M19.967 17.482a4 4 0 11-7.967.517 4 4 0 11-7.967-.517", "M6 18a4 4 0 01-2-7.465", "M6.004 5.125a4 4 0 00-2.526 5.77")) }
    /** Lucide `brush`. */
    val Brush: ImageVector by lazy { build("Brush", listOf("m11 10 3 3", "M6.5 21A3.5 3.5 0 1 0 3 17.5a2.62 2.62 0 0 1-.708 1.792A1 1 0 0 0 3 21z", "M9.969 17.031 21.378 5.624a1 1 0 0 0-3.002-3.002L6.967 14.031")) }
    /** Lucide `bug`. */
    val Bug: ImageVector by lazy { build("Bug", listOf("M12 20v-9", "M14 7a4 4 0 0 1 4 4v3a6 6 0 0 1-12 0v-3a4 4 0 0 1 4-4z", "M14.12 3.88 16 2", "M21 21a4 4 0 0 0-3.81-4", "M21 5a4 4 0 0 1-3.55 3.97", "M22 13h-4", "M3 21a4 4 0 0 1 3.81-4", "M3 5a4 4 0 0 0 3.55 3.97", "M6 13H2", "m8 2 1.88 1.88", "M9 7.13V6a3 3 0 1 1 6 0v1.13")) }
    /** Lucide `building`. */
    val Building: ImageVector by lazy { build("Building", listOf("M12 10h.01", "M12 14h.01", "M12 6h.01", "M16 10h.01", "M16 14h.01", "M16 6h.01", "M8 10h.01", "M8 14h.01", "M8 6h.01", "M9 22v-3a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v3", "M6.0 2.0h12.0a2.0 2.0 0 0 1 2.0 2.0v16.0a2.0 2.0 0 0 1 -2.0 2.0h-12.0a2.0 2.0 0 0 1 -2.0 -2.0v-16.0a2.0 2.0 0 0 1 2.0 -2.0Z")) }
    /** Lucide `cable`. */
    val Cable: ImageVector by lazy { build("Cable", listOf("M17 19a1 1 0 0 1-1-1v-2a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2a1 1 0 0 1-1 1z", "M17 21v-2", "M19 14V6.5a1 1 0 0 0-7 0v11a1 1 0 0 1-7 0V10", "M21 21v-2", "M3 5V3", "M4 10a2 2 0 0 1-2-2V6a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2a2 2 0 0 1-2 2z", "M7 5V3")) }
    /** Lucide `cake`. */
    val Cake: ImageVector by lazy { build("Cake", listOf("M20 21v-8a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8", "M4 16s.5-1 2-1 2.5 2 4 2 2.5-2 4-2 2.5 2 4 2 2-1 2-1", "M2 21h20", "M7 8v3", "M12 8v3", "M17 8v3", "M7 4h.01", "M12 4h.01", "M17 4h.01")) }
    /** Lucide `calendar`. */
    val Calendar: ImageVector by lazy { build("Calendar", listOf("M8 2v3", "M16 2v3", "M5.0 3.0h14.0a2.0 2.0 0 0 1 2.0 2.0v14.0a2.0 2.0 0 0 1 -2.0 2.0h-14.0a2.0 2.0 0 0 1 -2.0 -2.0v-14.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M3 9h18")) }
    /** Lucide `calendar-arrow-down`. */
    val CalendarArrowDown: ImageVector by lazy { build("CalendarArrowDown", listOf("m14 17 4 4 4-4", "M16 2v3", "M18 13v8", "M21 10.354V5a2 2 0 00-2-2H5a2 2 0 00-2 2v14a2 2 0 002 2h7.343", "M3 9h18", "M8 2v3")) }
    /** Lucide `calendar-arrow-up`. */
    val CalendarArrowUp: ImageVector by lazy { build("CalendarArrowUp", listOf("m14 17 4-4 4 4", "M16 2v3", "M18 21v-8", "M21 10.343V5a2 2 0 00-2-2H5a2 2 0 00-2 2v14a2 2 0 002 2h9", "M3 9h18", "M8 2v3")) }
    /** Lucide `calendar-check`. */
    val CalendarCheck: ImageVector by lazy { build("CalendarCheck", listOf("M8 2v3", "M16 2v3", "M5.0 3.0h14.0a2.0 2.0 0 0 1 2.0 2.0v14.0a2.0 2.0 0 0 1 -2.0 2.0h-14.0a2.0 2.0 0 0 1 -2.0 -2.0v-14.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M3 9h18", "m9 15 2 2 4-4")) }
    /** Lucide `calendar-days`. */
    val CalendarDays: ImageVector by lazy { build("CalendarDays", listOf("M8 2v3", "M16 2v3", "M5.0 3.0h14.0a2.0 2.0 0 0 1 2.0 2.0v14.0a2.0 2.0 0 0 1 -2.0 2.0h-14.0a2.0 2.0 0 0 1 -2.0 -2.0v-14.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M3 9h18", "M8 13h.01", "M12 13h.01", "M16 13h.01", "M8 17h.01", "M12 17h.01", "M16 17h.01")) }
    /** Lucide `calendar-range`. */
    val CalendarRange: ImageVector by lazy { build("CalendarRange", listOf("M5.0 3.0h14.0a2.0 2.0 0 0 1 2.0 2.0v14.0a2.0 2.0 0 0 1 -2.0 2.0h-14.0a2.0 2.0 0 0 1 -2.0 -2.0v-14.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M16 2v3", "M3 9h18", "M8 2v3", "M17 13h-6", "M13 17H7", "M7 13h.01", "M17 17h.01")) }
    /** Lucide `captions`. */
    val Captions: ImageVector by lazy { build("Captions", listOf("M5.0 5.0h14.0a2.0 2.0 0 0 1 2.0 2.0v10.0a2.0 2.0 0 0 1 -2.0 2.0h-14.0a2.0 2.0 0 0 1 -2.0 -2.0v-10.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M7 15h4M15 15h2M7 11h2M13 11h4")) }
    /** Lucide `castle`. */
    val Castle: ImageVector by lazy { build("Castle", listOf("M10 5V3", "M14 5V3", "M15 21v-3a3 3 0 0 0-6 0v3", "M18 3v8", "M18 5H6", "M22 11H2", "M22 9v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9", "M6 3v8")) }
    /** Lucide `chart-column`. */
    val ChartColumn: ImageVector by lazy { build("ChartColumn", listOf("M3 3v16a2 2 0 0 0 2 2h16", "M18 17V9", "M13 17V5", "M8 17v-3")) }
    /** Lucide `chart-pie`. */
    val ChartPie: ImageVector by lazy { build("ChartPie", listOf("M21 12c.552 0 1.005-.449.95-.998a10 10 0 0 0-8.953-8.951c-.55-.055-.998.398-.998.95v8a1 1 0 0 0 1 1z", "M21.21 15.89A10 10 0 1 1 8 2.83")) }
    /** Lucide `check`. */
    val Check: ImageVector by lazy { build("Check", listOf("M20 6 9 17l-5-5")) }
    /** Lucide `check-check`. */
    val CheckCheck: ImageVector by lazy { build("CheckCheck", listOf("M18 6 7 17l-5-5", "m22 10-7.5 7.5L13 16")) }
    /** Lucide `chef-hat`. */
    val ChefHat: ImageVector by lazy { build("ChefHat", listOf("M17 21a1 1 0 0 0 1-1v-5.35c0-.457.316-.844.727-1.041a4 4 0 0 0-2.134-7.589 5 5 0 0 0-9.186 0 4 4 0 0 0-2.134 7.588c.411.198.727.585.727 1.041V20a1 1 0 0 0 1 1Z", "M6 17h12")) }
    /** Lucide `chevron-down`. */
    val ChevronDown: ImageVector by lazy { build("ChevronDown", listOf("m6 9 6 6 6-6")) }
    /** Lucide `chevron-left`. */
    val ChevronLeft: ImageVector by lazy { build("ChevronLeft", listOf("m15 18-6-6 6-6")) }
    /** Lucide `chevron-right`. */
    val ChevronRight: ImageVector by lazy { build("ChevronRight", listOf("m9 18 6-6-6-6")) }
    /** Lucide `chevron-up`. */
    val ChevronUp: ImageVector by lazy { build("ChevronUp", listOf("m18 15-6-6-6 6")) }
    /** Lucide `church`. */
    val Church: ImageVector by lazy { build("Church", listOf("M10 9h4", "M12 7v5", "M14 21v-3a2 2 0 0 0-4 0v3", "m18 9 3.52 2.147a1 1 0 0 1 .48.854V19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-6.999a1 1 0 0 1 .48-.854L6 9", "M6 21V7a1 1 0 0 1 .376-.782l5-3.999a1 1 0 0 1 1.249.001l5 4A1 1 0 0 1 18 7v14")) }
    /** Lucide `circle`. */
    val Circle: ImageVector by lazy { build("Circle", listOf("M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0Z")) }
    /** Lucide `circle-alert`. */
    val CircleAlert: ImageVector by lazy { build("CircleAlert", listOf("M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0Z", "M12 8L12 12", "M12 16L12.01 16")) }
    /** Lucide `circle-arrow-up`. */
    val CircleArrowUp: ImageVector by lazy { build("CircleArrowUp", listOf("M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0Z", "m16 12-4-4-4 4", "M12 16V8")) }
    /** Lucide `circle-check`. */
    val CircleCheck: ImageVector by lazy { build("CircleCheck", listOf("M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0Z", "m16 9-5.5 5.5L8 12")) }
    /** Lucide `circle-dot`. */
    val CircleDot: ImageVector by lazy { build("CircleDot", listOf("M11.0 12.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0Z", "M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0Z")) }
    /** Lucide `circle-minus`. */
    val CircleMinus: ImageVector by lazy { build("CircleMinus", listOf("M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0Z", "M8 12h8")) }
    /** Lucide `circle-pause`. */
    val CirclePause: ImageVector by lazy { build("CirclePause", listOf("M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0Z", "M10 15L10 9", "M14 15L14 9")) }
    /** Lucide `circle-play`. */
    val CirclePlay: ImageVector by lazy { build("CirclePlay", listOf("M9 9.003a1 1 0 0 1 1.517-.859l4.997 2.997a1 1 0 0 1 0 1.718l-4.997 2.997A1 1 0 0 1 9 14.996z", "M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0Z")) }
    /** Lucide `circle-plus`. */
    val CirclePlus: ImageVector by lazy { build("CirclePlus", listOf("M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0Z", "M8 12h8", "M12 8v8")) }
    /** Lucide `circle-question-mark`. */
    val CircleQuestionMark: ImageVector by lazy { build("CircleQuestionMark", listOf("M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0Z", "M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3", "M12 17h.01")) }
    /** Lucide `circle-stop`. */
    val CircleStop: ImageVector by lazy { build("CircleStop", listOf("M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0Z", "M10.0 9.0h4.0a1.0 1.0 0 0 1 1.0 1.0v4.0a1.0 1.0 0 0 1 -1.0 1.0h-4.0a1.0 1.0 0 0 1 -1.0 -1.0v-4.0a1.0 1.0 0 0 1 1.0 -1.0Z")) }
    /** Lucide `circle-user-round`. */
    val CircleUserRound: ImageVector by lazy { build("CircleUserRound", listOf("M17.925 20.056a6 6 0 0 0-11.851.001", "M8.0 11.0a4.0 4.0 0 1 0 8.0 0a4.0 4.0 0 1 0 -8.0 0Z", "M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0Z")) }
    /** Lucide `circle-x`. */
    val CircleX: ImageVector by lazy { build("CircleX", listOf("M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0Z", "m15 9-6 6", "m9 9 6 6")) }
    /** Lucide `clapperboard`. */
    val Clapperboard: ImageVector by lazy { build("Clapperboard", listOf("m12.296 3.464 3.02 3.956", "M20.2 6 3 11l-.9-2.4c-.3-1.1.3-2.2 1.3-2.5l13.5-4c1.1-.3 2.2.3 2.5 1.3z", "M3 11h18v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", "m6.18 5.276 3.1 3.899")) }
    /** Lucide `clipboard`. */
    val Clipboard: ImageVector by lazy { build("Clipboard", listOf("M9.0 2.0h6.0a1.0 1.0 0 0 1 1.0 1.0v2.0a1.0 1.0 0 0 1 -1.0 1.0h-6.0a1.0 1.0 0 0 1 -1.0 -1.0v-2.0a1.0 1.0 0 0 1 1.0 -1.0Z", "M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2")) }
    /** Lucide `clipboard-list`. */
    val ClipboardList: ImageVector by lazy { build("ClipboardList", listOf("M9.0 2.0h6.0a1.0 1.0 0 0 1 1.0 1.0v2.0a1.0 1.0 0 0 1 -1.0 1.0h-6.0a1.0 1.0 0 0 1 -1.0 -1.0v-2.0a1.0 1.0 0 0 1 1.0 -1.0Z", "M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2", "M12 11h4", "M12 16h4", "M8 11h.01", "M8 16h.01")) }
    /** Lucide `clock`. */
    val Clock: ImageVector by lazy { build("Clock", listOf("M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0Z", "M12 6v6l4 2")) }
    /** Lucide `clock-arrow-up`. */
    val ClockArrowUp: ImageVector by lazy { build("ClockArrowUp", listOf("M12 6v6l1.56.78", "M13.227 21.925a10 10 0 1 1 8.767-9.588", "m14 18 4-4 4 4", "M18 22v-8")) }
    /** Lucide `cloud`. */
    val Cloud: ImageVector by lazy { build("Cloud", listOf("M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z")) }
    /** Lucide `cloud-download`. */
    val CloudDownload: ImageVector by lazy { build("CloudDownload", listOf("M12 13v8l-4-4", "m12 21 4-4", "M4.393 15.269A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.436 8.284")) }
    /** Lucide `cloud-lightning`. */
    val CloudLightning: ImageVector by lazy { build("CloudLightning", listOf("M6 16.326A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 .5 8.973", "m13 12-3 5h4l-3 5")) }
    /** Lucide `cloud-off`. */
    val CloudOff: ImageVector by lazy { build("CloudOff", listOf("M10.94 5.274A7 7 0 0 1 15.71 10h1.79a4.5 4.5 0 0 1 4.222 6.057", "M18.796 18.81A4.5 4.5 0 0 1 17.5 19H9A7 7 0 0 1 5.79 5.78", "m2 2 20 20")) }
    /** Lucide `code`. */
    val Code: ImageVector by lazy { build("Code", listOf("m16 18 6-6-6-6", "m8 6-6 6 6 6")) }
    /** Lucide `coffee`. */
    val Coffee: ImageVector by lazy { build("Coffee", listOf("M10 2v2", "M14 2v2", "M16 8a1 1 0 0 1 1 1v8a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4V9a1 1 0 0 1 1-1h14a4 4 0 1 1 0 8h-1", "M6 2v2")) }
    /** Lucide `columns-3`. */
    val Columns3: ImageVector by lazy { build("Columns3", listOf("M5.0 3.0h14.0a2.0 2.0 0 0 1 2.0 2.0v14.0a2.0 2.0 0 0 1 -2.0 2.0h-14.0a2.0 2.0 0 0 1 -2.0 -2.0v-14.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M9 3v18", "M15 3v18")) }
    /** Lucide `compass`. */
    val Compass: ImageVector by lazy { build("Compass", listOf("M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0Z", "m16.24 7.76-1.804 5.411a2 2 0 0 1-1.265 1.265L7.76 16.24l1.804-5.411a2 2 0 0 1 1.265-1.265z")) }
    /** Lucide `contrast`. */
    val Contrast: ImageVector by lazy { build("Contrast", listOf("M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0Z", "M12 18a6 6 0 0 0 0-12v12z")) }
    /** Lucide `copy`. */
    val Copy: ImageVector by lazy { build("Copy", listOf("M10.0 8.0h10.0a2.0 2.0 0 0 1 2.0 2.0v10.0a2.0 2.0 0 0 1 -2.0 2.0h-10.0a2.0 2.0 0 0 1 -2.0 -2.0v-10.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2")) }
    /** Lucide `cpu`. */
    val Cpu: ImageVector by lazy { build("Cpu", listOf("M12 20v2", "M12 2v2", "M17 20v2", "M17 2v2", "M2 12h2", "M2 17h2", "M2 7h2", "M20 12h2", "M20 17h2", "M20 7h2", "M7 20v2", "M7 2v2", "M6.0 4.0h12.0a2.0 2.0 0 0 1 2.0 2.0v12.0a2.0 2.0 0 0 1 -2.0 2.0h-12.0a2.0 2.0 0 0 1 -2.0 -2.0v-12.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M9.0 8.0h6.0a1.0 1.0 0 0 1 1.0 1.0v6.0a1.0 1.0 0 0 1 -1.0 1.0h-6.0a1.0 1.0 0 0 1 -1.0 -1.0v-6.0a1.0 1.0 0 0 1 1.0 -1.0Z")) }
    /** Lucide `crop`. */
    val Crop: ImageVector by lazy { build("Crop", listOf("M6 2v14a2 2 0 0 0 2 2h14", "M18 22V8a2 2 0 0 0-2-2H2")) }
    /** Lucide `crown`. */
    val Crown: ImageVector by lazy { build("Crown", listOf("M11.562 3.266a.5.5 0 0 1 .876 0L15.39 8.87a1 1 0 0 0 1.516.294L21.183 5.5a.5.5 0 0 1 .798.519l-2.834 10.246a1 1 0 0 1-.956.734H5.81a1 1 0 0 1-.957-.734L2.02 6.02a.5.5 0 0 1 .798-.519l4.276 3.664a1 1 0 0 0 1.516-.294z", "M5 21h14")) }
    /** Lucide `database`. */
    val Database: ImageVector by lazy { build("Database", listOf("M3.0 5.0a9.0 3.0 0 1 0 18.0 0a9.0 3.0 0 1 0 -18.0 0Z", "M3 5V19A9 3 0 0 0 21 19V5", "M3 12A9 3 0 0 0 21 12")) }
    /** Lucide `dices`. */
    val Dices: ImageVector by lazy { build("Dices", listOf("M4.0 10.0h8.0a2.0 2.0 0 0 1 2.0 2.0v8.0a2.0 2.0 0 0 1 -2.0 2.0h-8.0a2.0 2.0 0 0 1 -2.0 -2.0v-8.0a2.0 2.0 0 0 1 2.0 -2.0Z", "m17.92 14 3.5-3.5a2.24 2.24 0 0 0 0-3l-5-4.92a2.24 2.24 0 0 0-3 0L10 6", "M6 18h.01", "M10 14h.01", "M15 6h.01", "M18 9h.01")) }
    /** Lucide `disc`. */
    val Disc: ImageVector by lazy { build("Disc", listOf("M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0Z", "M10.0 12.0a2.0 2.0 0 1 0 4.0 0a2.0 2.0 0 1 0 -4.0 0Z")) }
    /** Lucide `dog`. */
    val Dog: ImageVector by lazy { build("Dog", listOf("M11.25 16.25h1.5L12 17z", "M16 14v.5", "M4.42 11.247A13.152 13.152 0 0 0 4 14.556C4 18.728 7.582 21 12 21s8-2.272 8-6.444a11.702 11.702 0 0 0-.493-3.309", "M8 14v.5", "M8.5 8.5c-.384 1.05-1.083 2.028-2.344 2.5-1.931.722-3.576-.297-3.656-1-.113-.994 1.177-6.53 4-7 1.923-.321 3.651.845 3.651 2.235A7.497 7.497 0 0 1 14 5.277c0-1.39 1.844-2.598 3.767-2.277 2.823.47 4.113 6.006 4 7-.08.703-1.725 1.722-3.656 1-1.261-.472-1.855-1.45-2.239-2.5")) }
    /** Lucide `download`. */
    val Download: ImageVector by lazy { build("Download", listOf("M12 15V3", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "m7 10 5 5 5-5")) }
    /** Lucide `drama`. */
    val Drama: ImageVector by lazy { build("Drama", listOf("M10 11h.01", "M14 6h.01", "M18 6h.01", "M6.5 13.1h.01", "M22 5c0 9-4 12-6 12s-6-3-6-12c0-2 2-3 6-3s6 1 6 3", "M17.4 9.9c-.8.8-2 .8-2.8 0", "M10.1 7.1C9 7.2 7.7 7.7 6 8.6c-3.5 2-4.7 3.9-3.7 5.6 4.5 7.8 9.5 8.4 11.2 7.4.9-.5 1.9-2.1 1.9-4.7", "M9.1 16.5c.3-1.1 1.4-1.7 2.4-1.4")) }
    /** Lucide `droplets`. */
    val Droplets: ImageVector by lazy { build("Droplets", listOf("M7 16.3c2.2 0 4-1.83 4-4.05 0-1.16-.57-2.26-1.71-3.19S7.29 6.75 7 5.3c-.29 1.45-1.14 2.84-2.29 3.76S3 11.1 3 12.25c0 2.22 1.8 4.05 4 4.05z", "M12.56 6.6A10.97 10.97 0 0 0 14 3.02c.5 2.5 2 4.9 4 6.5s3 3.5 3 5.5a6.98 6.98 0 0 1-11.91 4.97")) }
    /** Lucide `dumbbell`. */
    val Dumbbell: ImageVector by lazy { build("Dumbbell", listOf("M17.596 12.768a2 2 0 1 0 2.829-2.829l-1.768-1.767a2 2 0 0 0 2.828-2.829l-2.828-2.828a2 2 0 0 0-2.829 2.828l-1.767-1.768a2 2 0 1 0-2.829 2.829z", "m2.5 21.5 1.4-1.4", "m20.1 3.9 1.4-1.4", "M5.343 21.485a2 2 0 1 0 2.829-2.828l1.767 1.768a2 2 0 1 0 2.829-2.829l-6.364-6.364a2 2 0 1 0-2.829 2.829l1.768 1.767a2 2 0 0 0-2.828 2.829z", "m9.6 14.4 4.8-4.8")) }
    /** Lucide `ellipsis-vertical`. */
    val EllipsisVertical: ImageVector by lazy { build("EllipsisVertical", listOf("M11.0 12.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0Z", "M11.0 5.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0Z", "M11.0 19.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0Z")) }
    /** Lucide `eraser`. */
    val Eraser: ImageVector by lazy { build("Eraser", listOf("M21 21H8a2 2 0 0 1-1.42-.587l-3.994-3.999a2 2 0 0 1 0-2.828l10-10a2 2 0 0 1 2.829 0l5.999 6a2 2 0 0 1 0 2.828L12.834 21", "m5.082 11.09 8.828 8.828")) }
    /** Lucide `external-link`. */
    val ExternalLink: ImageVector by lazy { build("ExternalLink", listOf("M15 3h6v6", "M10 14 21 3", "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6")) }
    /** Lucide `eye`. */
    val Eye: ImageVector by lazy { build("Eye", listOf("M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0", "M9.0 12.0a3.0 3.0 0 1 0 6.0 0a3.0 3.0 0 1 0 -6.0 0Z")) }
    /** Lucide `eye-off`. */
    val EyeOff: ImageVector by lazy { build("EyeOff", listOf("M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49", "M14.084 14.158a3 3 0 0 1-4.242-4.242", "M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143", "m2 2 20 20")) }
    /** Lucide `face-angry`. */
    val FaceAngry: ImageVector by lazy { build("FaceAngry", listOf("M15 12v-1.584", "M17 10a5 5 0 00-3 1", "M7 10a5 5 0 013 1", "M9 12v-1.584", "M9 17a5 5 0 016.001 0", "M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0Z")) }
    /** Lucide `face-grinning`. */
    val FaceGrinning: ImageVector by lazy { build("FaceGrinning", listOf("M15 10V9", "M7.084 14.302a5.12 5.12 0 009.833 0 .24.24 0 00-.235-.302H7.32a.24.24 0 00-.235.302", "M9 10V9", "M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0Z")) }
    /** Lucide `face-neutral`. */
    val FaceNeutral: ImageVector by lazy { build("FaceNeutral", listOf("M15 10V9", "M8 16h8", "M9 10V9", "M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0Z")) }
    /** Lucide `face-slightly-frowning`. */
    val FaceSlightlyFrowning: ImageVector by lazy { build("FaceSlightlyFrowning", listOf("M15 10V9", "M9 10V9", "M9 16a5 5 0 016 0", "M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0Z")) }
    /** Lucide `face-slightly-smiling`. */
    val FaceSlightlySmiling: ImageVector by lazy { build("FaceSlightlySmiling", listOf("M15 10V9", "M16.472 15a6 6 0 01-8.943 0", "M9 10V9", "M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0Z")) }
    /** Lucide `fast-forward`. */
    val FastForward: ImageVector by lazy { build("FastForward", listOf("M12 6a2 2 0 0 1 3.414-1.414l6 6a2 2 0 0 1 0 2.828l-6 6A2 2 0 0 1 12 18z", "M2 6a2 2 0 0 1 3.414-1.414l6 6a2 2 0 0 1 0 2.828l-6 6A2 2 0 0 1 2 18z")) }
    /** Lucide `film`. */
    val Film: ImageVector by lazy { build("Film", listOf("M5.0 3.0h14.0a2.0 2.0 0 0 1 2.0 2.0v14.0a2.0 2.0 0 0 1 -2.0 2.0h-14.0a2.0 2.0 0 0 1 -2.0 -2.0v-14.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M7 3v18", "M3 7.5h4", "M3 12h18", "M3 16.5h4", "M17 3v18", "M17 7.5h4", "M17 16.5h4")) }
    /** Lucide `flag`. */
    val Flag: ImageVector by lazy { build("Flag", listOf("M4 22V4a1 1 0 0 1 .4-.8A6 6 0 0 1 8 2c3 0 5 2 7.333 2q2 0 3.067-.8A1 1 0 0 1 20 4v10a1 1 0 0 1-.4.8A6 6 0 0 1 16 16c-3 0-5-2-8-2a6 6 0 0 0-4 1.528")) }
    /** Lucide `flame`. */
    val Flame: ImageVector by lazy { build("Flame", listOf("M12 3q1 4 4 6.5t3 5.5a1 1 0 0 1-14 0 5 5 0 0 1 1-3 1 1 0 0 0 5 0c0-2-1.5-3-1.5-5q0-2 2.5-4")) }
    /** Lucide `flask-conical`. */
    val FlaskConical: ImageVector by lazy { build("FlaskConical", listOf("M14 2v6a2 2 0 0 0 .245.96l5.51 10.08A2 2 0 0 1 18 22H6a2 2 0 0 1-1.755-2.96l5.51-10.08A2 2 0 0 0 10 8V2", "M6.453 15h11.094", "M8.5 2h7")) }
    /** Lucide `flower`. */
    val Flower: ImageVector by lazy { build("Flower", listOf("M9.0 12.0a3.0 3.0 0 1 0 6.0 0a3.0 3.0 0 1 0 -6.0 0Z", "M12 16.5A4.5 4.5 0 1 1 7.5 12 4.5 4.5 0 1 1 12 7.5a4.5 4.5 0 1 1 4.5 4.5 4.5 4.5 0 1 1-4.5 4.5", "M12 7.5V9", "M7.5 12H9", "M16.5 12H15", "M12 16.5V15", "m8 8 1.88 1.88", "M14.12 9.88 16 8", "m8 16 1.88-1.88", "M14.12 14.12 16 16")) }
    /** Lucide `focus`. */
    val Focus: ImageVector by lazy { build("Focus", listOf("M9.0 12.0a3.0 3.0 0 1 0 6.0 0a3.0 3.0 0 1 0 -6.0 0Z", "M3 7V5a2 2 0 0 1 2-2h2", "M17 3h2a2 2 0 0 1 2 2v2", "M21 17v2a2 2 0 0 1-2 2h-2", "M7 21H5a2 2 0 0 1-2-2v-2")) }
    /** Lucide `folder`. */
    val Folder: ImageVector by lazy { build("Folder", listOf("M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z")) }
    /** Lucide `folder-open`. */
    val FolderOpen: ImageVector by lazy { build("FolderOpen", listOf("m6 14 1.5-2.9A2 2 0 0 1 9.24 10H20a2 2 0 0 1 1.94 2.5l-1.54 6a2 2 0 0 1-1.95 1.5H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h3.9a2 2 0 0 1 1.69.9l.81 1.2a2 2 0 0 0 1.67.9H18a2 2 0 0 1 2 2v2")) }
    /** Lucide `forward`. */
    val Forward: ImageVector by lazy { build("Forward", listOf("m15 17 5-5-5-5", "M4 18v-2a4 4 0 0 1 4-4h12")) }
    /** Lucide `funnel`. */
    val Funnel: ImageVector by lazy { build("Funnel", listOf("M10 20a1 1 0 0 0 .553.895l2 1A1 1 0 0 0 14 21v-7a2 2 0 0 1 .517-1.341L21.74 4.67A1 1 0 0 0 21 3H3a1 1 0 0 0-.742 1.67l7.225 7.989A2 2 0 0 1 10 14z")) }
    /** Lucide `gallery-horizontal`. */
    val GalleryHorizontal: ImageVector by lazy { build("GalleryHorizontal", listOf("M2 3v18", "M8.0 3.0h8.0a2.0 2.0 0 0 1 2.0 2.0v14.0a2.0 2.0 0 0 1 -2.0 2.0h-8.0a2.0 2.0 0 0 1 -2.0 -2.0v-14.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M22 3v18")) }
    /** Lucide `gallery-vertical`. */
    val GalleryVertical: ImageVector by lazy { build("GalleryVertical", listOf("M3 2h18", "M5.0 6.0h14.0a2.0 2.0 0 0 1 2.0 2.0v8.0a2.0 2.0 0 0 1 -2.0 2.0h-14.0a2.0 2.0 0 0 1 -2.0 -2.0v-8.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M3 22h18")) }
    /** Lucide `gamepad`. */
    val Gamepad: ImageVector by lazy { build("Gamepad", listOf("M6 12L10 12", "M8 10L8 14", "M15 13L15.01 13", "M18 11L18.01 11", "M4.0 6.0h16.0a2.0 2.0 0 0 1 2.0 2.0v8.0a2.0 2.0 0 0 1 -2.0 2.0h-16.0a2.0 2.0 0 0 1 -2.0 -2.0v-8.0a2.0 2.0 0 0 1 2.0 -2.0Z")) }
    /** Lucide `gamepad-2`. */
    val Gamepad2: ImageVector by lazy { build("Gamepad2", listOf("M6 11L10 11", "M8 9L8 13", "M15 12L15.01 12", "M18 10L18.01 10", "M17.32 5H6.68a4 4 0 0 0-3.978 3.59c-.006.052-.01.101-.017.152C2.604 9.416 2 14.456 2 16a3 3 0 0 0 3 3c1 0 1.5-.5 2-1l1.414-1.414A2 2 0 0 1 9.828 16h4.344a2 2 0 0 1 1.414.586L17 18c.5.5 1 1 2 1a3 3 0 0 0 3-3c0-1.545-.604-6.584-.685-7.258-.007-.05-.011-.1-.017-.151A4 4 0 0 0 17.32 5z")) }
    /** Lucide `gauge`. */
    val Gauge: ImageVector by lazy { build("Gauge", listOf("m12 14 4-4", "M3.34 19a10 10 0 1 1 17.32 0")) }
    /** Lucide `ghost`. */
    val Ghost: ImageVector by lazy { build("Ghost", listOf("M15 10v1", "M7.528 20.472a1.6 1.6 0 012.277 0l1.057 1.056a1.6 1.6 0 002.276 0l1.057-1.056a1.6 1.6 0 012.277 0l1.114 1.114a1.4 1.4 0 002.414-1V10a8 8 0 00-16 0v10.586a1.4 1.4 0 002.414 1z", "M9 10v1")) }
    /** Lucide `globe`. */
    val Globe: ImageVector by lazy { build("Globe", listOf("M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0Z", "M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20", "M2 12h20")) }
    /** Lucide `grip-vertical`. */
    val GripVertical: ImageVector by lazy { build("GripVertical", listOf("M8.0 12.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0Z", "M8.0 5.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0Z", "M8.0 19.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0Z", "M14.0 12.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0Z", "M14.0 5.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0Z", "M14.0 19.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0Z")) }
    /** Lucide `hand`. */
    val Hand: ImageVector by lazy { build("Hand", listOf("M18 11V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2", "M14 10V4a2 2 0 0 0-2-2a2 2 0 0 0-2 2v2", "M10 10.5V6a2 2 0 0 0-2-2a2 2 0 0 0-2 2v8", "M18 8a2 2 0 1 1 4 0v6a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15")) }
    /** Lucide `hand-heart`. */
    val HandHeart: ImageVector by lazy { build("HandHeart", listOf("M11 14h2a2 2 0 0 0 0-4h-3c-.6 0-1.1.2-1.4.6L3 16", "m14.45 13.39 5.05-4.694C20.196 8 21 6.85 21 5.75a2.75 2.75 0 0 0-4.797-1.837.276.276 0 0 1-.406 0A2.75 2.75 0 0 0 11 5.75c0 1.2.802 2.248 1.5 2.946L16 11.95", "m2 15 6 6", "m7 20 1.6-1.4c.3-.4.8-.6 1.4-.6h4c1.1 0 2.1-.4 2.8-1.2l4.6-4.4a1 1 0 0 0-2.75-2.91")) }
    /** Lucide `hard-drive`. */
    val HardDrive: ImageVector by lazy { build("HardDrive", listOf("M10 16h.01", "M2.212 11.577a2 2 0 0 0-.212.896V18a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-5.527a2 2 0 0 0-.212-.896L18.55 5.11A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z", "M21.946 12.013H2.054", "M6 16h.01")) }
    /** Lucide `hash`. */
    val Hash: ImageVector by lazy { build("Hash", listOf("M4 9L20 9", "M4 15L20 15", "M10 3L8 21", "M16 3L14 21")) }
    /** Lucide `heading-1`. */
    val Heading1: ImageVector by lazy { build("Heading1", listOf("M4 12h8", "M4 18V6", "M12 18V6", "m17 12 3-2v8")) }
    /** Lucide `heart`. */
    val Heart: ImageVector by lazy { build("Heart", listOf("M2 9.5a5.5 5.5 0 0 1 9.591-3.676.56.56 0 0 0 .818 0A5.49 5.49 0 0 1 22 9.5c0 2.29-1.5 4-3 5.5l-5.492 5.313a2 2 0 0 1-3 .019L5 15c-1.5-1.5-3-3.2-3-5.5")) }
    /** Lucide `heart` — dolu (seçili/aktif) varyant. */
    val HeartSolid: ImageVector by lazy { build("HeartSolid", listOf("M2 9.5a5.5 5.5 0 0 1 9.591-3.676.56.56 0 0 0 .818 0A5.49 5.49 0 0 1 22 9.5c0 2.29-1.5 4-3 5.5l-5.492 5.313a2 2 0 0 1-3 .019L5 15c-1.5-1.5-3-3.2-3-5.5"), solid = true) }
    /** Lucide `heart-pulse`. */
    val HeartPulse: ImageVector by lazy { build("HeartPulse", listOf("M2 9.5a5.5 5.5 0 0 1 9.591-3.676.56.56 0 0 0 .818 0A5.49 5.49 0 0 1 22 9.5c0 2.29-1.5 4-3 5.5l-5.492 5.313a2 2 0 0 1-3 .019L5 15c-1.5-1.5-3-3.2-3-5.5", "M3.22 13H9.5l.5-1 2 4.5 2-7 1.5 3.5h5.27")) }
    /** Lucide `hourglass`. */
    val Hourglass: ImageVector by lazy { build("Hourglass", listOf("M5 22h14", "M5 2h14", "M17 22v-4.172a2 2 0 0 0-.586-1.414L12 12l-4.414 4.414A2 2 0 0 0 7 17.828V22", "M7 2v4.172a2 2 0 0 0 .586 1.414L12 12l4.414-4.414A2 2 0 0 0 17 6.172V2")) }
    /** Lucide `house`. */
    val House: ImageVector by lazy { build("House", listOf("M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8", "M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z")) }
    /** Lucide `image`. */
    val Image: ImageVector by lazy { build("Image", listOf("M5.0 3.0h14.0a2.0 2.0 0 0 1 2.0 2.0v14.0a2.0 2.0 0 0 1 -2.0 2.0h-14.0a2.0 2.0 0 0 1 -2.0 -2.0v-14.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M7.0 9.0a2.0 2.0 0 1 0 4.0 0a2.0 2.0 0 1 0 -4.0 0Z", "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21")) }
    /** Lucide `image-off`. */
    val ImageOff: ImageVector by lazy { build("ImageOff", listOf("M2 2L22 22", "M10.41 10.41a2 2 0 1 1-2.83-2.83", "M13.5 13.5L6 21", "M18 12L21 15", "M3.59 3.59A1.99 1.99 0 0 0 3 5v14a2 2 0 0 0 2 2h14c.55 0 1.052-.22 1.41-.59", "M21 15V5a2 2 0 0 0-2-2H9")) }
    /** Lucide `images`. */
    val Images: ImageVector by lazy { build("Images", listOf("m22 11-1.296-1.296a2.4 2.4 0 0 0-3.408 0L11 16", "M4 8a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2", "FILL:M12.0 7.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0Z", "M10.0 2.0h10.0a2.0 2.0 0 0 1 2.0 2.0v10.0a2.0 2.0 0 0 1 -2.0 2.0h-10.0a2.0 2.0 0 0 1 -2.0 -2.0v-10.0a2.0 2.0 0 0 1 2.0 -2.0Z")) }
    /** Lucide `info`. */
    val Info: ImageVector by lazy { build("Info", listOf("M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0Z", "M12 16v-4", "M12 8h.01")) }
    /** Lucide `italic`. */
    val Italic: ImageVector by lazy { build("Italic", listOf("M19 4L10 4", "M14 20L5 20", "M15 4L9 20")) }
    /** Lucide `key-round`. */
    val KeyRound: ImageVector by lazy { build("KeyRound", listOf("M2.586 17.414A2 2 0 0 0 2 18.828V21a1 1 0 0 0 1 1h3a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h1a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h.172a2 2 0 0 0 1.414-.586l.814-.814a6.5 6.5 0 1 0-4-4z", "FILL:M16.0 7.5a0.5 0.5 0 1 0 1.0 0a0.5 0.5 0 1 0 -1.0 0Z")) }
    /** Lucide `keyboard`. */
    val Keyboard: ImageVector by lazy { build("Keyboard", listOf("M10 8h.01", "M12 12h.01", "M14 8h.01", "M16 12h.01", "M18 8h.01", "M6 8h.01", "M7 16h10", "M8 12h.01", "M4.0 4.0h16.0a2.0 2.0 0 0 1 2.0 2.0v12.0a2.0 2.0 0 0 1 -2.0 2.0h-16.0a2.0 2.0 0 0 1 -2.0 -2.0v-12.0a2.0 2.0 0 0 1 2.0 -2.0Z")) }
    /** Lucide `landmark`. */
    val Landmark: ImageVector by lazy { build("Landmark", listOf("M10 18v-7", "M11.119 2.205a2 2 0 0 1 1.762 0l7.84 3.846A.5.5 0 0 1 20.5 7h-17a.5.5 0 0 1-.22-.949z", "M14 18v-7", "M18 18v-7", "M3 22h18", "M6 18v-7")) }
    /** Lucide `languages`. */
    val Languages: ImageVector by lazy { build("Languages", listOf("m5 8 6 6", "m4 14 6-6 2-3", "M2 5h12", "M7 2h1", "m22 22-5-10-5 10", "M14 18h6")) }
    /** Lucide `layout-grid`. */
    val LayoutGrid: ImageVector by lazy { build("LayoutGrid", listOf("M4.0 3.0h5.0a1.0 1.0 0 0 1 1.0 1.0v5.0a1.0 1.0 0 0 1 -1.0 1.0h-5.0a1.0 1.0 0 0 1 -1.0 -1.0v-5.0a1.0 1.0 0 0 1 1.0 -1.0Z", "M15.0 3.0h5.0a1.0 1.0 0 0 1 1.0 1.0v5.0a1.0 1.0 0 0 1 -1.0 1.0h-5.0a1.0 1.0 0 0 1 -1.0 -1.0v-5.0a1.0 1.0 0 0 1 1.0 -1.0Z", "M15.0 14.0h5.0a1.0 1.0 0 0 1 1.0 1.0v5.0a1.0 1.0 0 0 1 -1.0 1.0h-5.0a1.0 1.0 0 0 1 -1.0 -1.0v-5.0a1.0 1.0 0 0 1 1.0 -1.0Z", "M4.0 14.0h5.0a1.0 1.0 0 0 1 1.0 1.0v5.0a1.0 1.0 0 0 1 -1.0 1.0h-5.0a1.0 1.0 0 0 1 -1.0 -1.0v-5.0a1.0 1.0 0 0 1 1.0 -1.0Z")) }
    /** Lucide `leaf`. */
    val Leaf: ImageVector by lazy { build("Leaf", listOf("M11 20a10 10 0 0010-10 25.9 25.9 0 00-1.04-7.281 1 1 0 00-1.755-.325C15.833 5.5 13 5.5 9.8 6.1A7 7 0 0011 20", "M2 21a5 5 0 012.911-4.544C7.613 15.212 8.351 15.24 11 13")) }
    /** Lucide `library`. */
    val Library: ImageVector by lazy { build("Library", listOf("m16 6 4 14", "M12 6v14", "M8 8v12", "M4 4v16")) }
    /** Lucide `link`. */
    val Link: ImageVector by lazy { build("Link", listOf("M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71", "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71")) }
    /** Lucide `link-2`. */
    val Link2: ImageVector by lazy { build("Link2", listOf("M9 17H7A5 5 0 0 1 7 7h2", "M15 7h2a5 5 0 1 1 0 10h-2", "M8 12L16 12")) }
    /** Lucide `list`. */
    val List: ImageVector by lazy { build("List", listOf("M3 5h.01", "M3 12h.01", "M3 19h.01", "M8 5h13", "M8 12h13", "M8 19h13")) }
    /** Lucide `list-checks`. */
    val ListChecks: ImageVector by lazy { build("ListChecks", listOf("M13 5h8", "M13 12h8", "M13 19h8", "m3 17 2 2 4-4", "m3 7 2 2 4-4")) }
    /** Lucide `list-ordered`. */
    val ListOrdered: ImageVector by lazy { build("ListOrdered", listOf("M11 5h10", "M11 12h10", "M11 19h10", "M4 4h1v5", "M4 9h2", "M6.5 20H3.4c0-1 2.6-1.925 2.6-3.5a1.5 1.5 0 0 0-2.6-1.02")) }
    /** Lucide `list-plus`. */
    val ListPlus: ImageVector by lazy { build("ListPlus", listOf("M16 5H3", "M11 12H3", "M16 19H3", "M18 9v6", "M21 12h-6")) }
    /** Lucide `list-video`. */
    val ListVideo: ImageVector by lazy { build("ListVideo", listOf("M21 5H3", "M10 12H3", "M10 19H3", "M15 12.003a1 1 0 0 1 1.517-.859l4.997 2.997a1 1 0 0 1 0 1.718l-4.997 2.997a1 1 0 0 1-1.517-.86z")) }
    /** Lucide `lock`. */
    val Lock: ImageVector by lazy { build("Lock", listOf("M5.0 11.0h14.0a2.0 2.0 0 0 1 2.0 2.0v7.0a2.0 2.0 0 0 1 -2.0 2.0h-14.0a2.0 2.0 0 0 1 -2.0 -2.0v-7.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M7 11V7a5 5 0 0 1 10 0v4")) }
    /** Lucide `lock-open`. */
    val LockOpen: ImageVector by lazy { build("LockOpen", listOf("M5.0 11.0h14.0a2.0 2.0 0 0 1 2.0 2.0v7.0a2.0 2.0 0 0 1 -2.0 2.0h-14.0a2.0 2.0 0 0 1 -2.0 -2.0v-7.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M7 11V7a5 5 0 0 1 9.9-1")) }
    /** Lucide `log-in`. */
    val LogIn: ImageVector by lazy { build("LogIn", listOf("m10 17 5-5-5-5", "M15 12H3", "M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4")) }
    /** Lucide `mail-open`. */
    val MailOpen: ImageVector by lazy { build("MailOpen", listOf("M21.2 8.4c.5.38.8.97.8 1.6v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V10a2 2 0 0 1 .8-1.6l8-6a2 2 0 0 1 2.4 0l8 6Z", "m22 10-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 10")) }
    /** Lucide `map-pin`. */
    val MapPin: ImageVector by lazy { build("MapPin", listOf("M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0", "M9.0 10.0a3.0 3.0 0 1 0 6.0 0a3.0 3.0 0 1 0 -6.0 0Z")) }
    /** Lucide `maximize`. */
    val Maximize: ImageVector by lazy { build("Maximize", listOf("M8 3H5a2 2 0 0 0-2 2v3", "M21 8V5a2 2 0 0 0-2-2h-3", "M3 16v3a2 2 0 0 0 2 2h3", "M16 21h3a2 2 0 0 0 2-2v-3")) }
    /** Lucide `medal`. */
    val Medal: ImageVector by lazy { build("Medal", listOf("M7.21 15 2.66 7.14a2 2 0 0 1 .13-2.2L4.4 2.8A2 2 0 0 1 6 2h12a2 2 0 0 1 1.6.8l1.6 2.14a2 2 0 0 1 .14 2.2L16.79 15", "M11 12 5.12 2.2", "m13 12 5.88-9.8", "M8 7h8", "M7.0 17.0a5.0 5.0 0 1 0 10.0 0a5.0 5.0 0 1 0 -10.0 0Z", "M12 18v-2h-.5")) }
    /** Lucide `menu`. */
    val Menu: ImageVector by lazy { build("Menu", listOf("M4 5h16", "M4 12h16", "M4 19h16")) }
    /** Lucide `message-circle`. */
    val MessageCircle: ImageVector by lazy { build("MessageCircle", listOf("M2.992 16.342a2 2 0 0 1 .094 1.167l-1.065 3.29a1 1 0 0 0 1.236 1.168l3.413-.998a2 2 0 0 1 1.099.092 10 10 0 1 0-4.777-4.719")) }
    /** Lucide `message-square`. */
    val MessageSquare: ImageVector by lazy { build("MessageSquare", listOf("M22 17a2 2 0 0 1-2 2H6.828a2 2 0 0 0-1.414.586l-2.202 2.202A.71.71 0 0 1 2 21.286V5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2z")) }
    /** Lucide `mic`. */
    val Mic: ImageVector by lazy { build("Mic", listOf("M12 19v3", "M19 10v2a7 7 0 0 1-14 0v-2", "M12.0 2.0h0.0a3.0 3.0 0 0 1 3.0 3.0v7.0a3.0 3.0 0 0 1 -3.0 3.0h-0.0a3.0 3.0 0 0 1 -3.0 -3.0v-7.0a3.0 3.0 0 0 1 3.0 -3.0Z")) }
    /** Lucide `minus`. */
    val Minus: ImageVector by lazy { build("Minus", listOf("M5 12h14")) }
    /** Lucide `monitor-play`. */
    val MonitorPlay: ImageVector by lazy { build("MonitorPlay", listOf("M15.033 9.44a.647.647 0 0 1 0 1.12l-4.065 2.352a.645.645 0 0 1-.968-.56V7.648a.645.645 0 0 1 .967-.56z", "M12 17v4", "M8 21h8", "M4.0 3.0h16.0a2.0 2.0 0 0 1 2.0 2.0v10.0a2.0 2.0 0 0 1 -2.0 2.0h-16.0a2.0 2.0 0 0 1 -2.0 -2.0v-10.0a2.0 2.0 0 0 1 2.0 -2.0Z")) }
    /** Lucide `moon`. */
    val Moon: ImageVector by lazy { build("Moon", listOf("M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0 8.268 8.268c.344-.215.825-.004.803.401")) }
    /** Lucide `moon-star`. */
    val MoonStar: ImageVector by lazy { build("MoonStar", listOf("M18 5h4", "M20 3v4", "M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0 8.268 8.268c.344-.215.825-.004.803.401")) }
    /** Lucide `network`. */
    val Network: ImageVector by lazy { build("Network", listOf("M17.0 16.0h4.0a1.0 1.0 0 0 1 1.0 1.0v4.0a1.0 1.0 0 0 1 -1.0 1.0h-4.0a1.0 1.0 0 0 1 -1.0 -1.0v-4.0a1.0 1.0 0 0 1 1.0 -1.0Z", "M3.0 16.0h4.0a1.0 1.0 0 0 1 1.0 1.0v4.0a1.0 1.0 0 0 1 -1.0 1.0h-4.0a1.0 1.0 0 0 1 -1.0 -1.0v-4.0a1.0 1.0 0 0 1 1.0 -1.0Z", "M10.0 2.0h4.0a1.0 1.0 0 0 1 1.0 1.0v4.0a1.0 1.0 0 0 1 -1.0 1.0h-4.0a1.0 1.0 0 0 1 -1.0 -1.0v-4.0a1.0 1.0 0 0 1 1.0 -1.0Z", "M5 16v-3a1 1 0 0 1 1-1h12a1 1 0 0 1 1 1v3", "M12 12V8")) }
    /** Lucide `newspaper`. */
    val Newspaper: ImageVector by lazy { build("Newspaper", listOf("M15 18h-5", "M18 14h-8", "M4 22h16a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2H8a2 2 0 0 0-2 2v16a2 2 0 0 1-4 0v-9a2 2 0 0 1 2-2h2", "M11.0 6.0h6.0a1.0 1.0 0 0 1 1.0 1.0v2.0a1.0 1.0 0 0 1 -1.0 1.0h-6.0a1.0 1.0 0 0 1 -1.0 -1.0v-2.0a1.0 1.0 0 0 1 1.0 -1.0Z")) }
    /** Lucide `notebook-pen`. */
    val NotebookPen: ImageVector by lazy { build("NotebookPen", listOf("M13.4 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-7.4", "M2 6h4", "M2 10h4", "M2 14h4", "M2 18h4", "M21.378 5.626a1 1 0 1 0-3.004-3.004l-5.01 5.012a2 2 0 0 0-.506.854l-.837 2.87a.5.5 0 0 0 .62.62l2.87-.837a2 2 0 0 0 .854-.506z")) }
    /** Lucide `notebook-text`. */
    val NotebookText: ImageVector by lazy { build("NotebookText", listOf("M2 6h4", "M2 10h4", "M2 14h4", "M2 18h4", "M6.0 2.0h12.0a2.0 2.0 0 0 1 2.0 2.0v16.0a2.0 2.0 0 0 1 -2.0 2.0h-12.0a2.0 2.0 0 0 1 -2.0 -2.0v-16.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M9.5 8h5", "M9.5 12H16", "M9.5 16H14")) }
    /** Lucide `package`. */
    val Package: ImageVector by lazy { build("Package", listOf("M11 21.73a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73z", "M12 22V12", "M3.29 7L12 12L20.71 7", "m7.5 4.27 9 5.15")) }
    /** Lucide `paint-bucket`. */
    val PaintBucket: ImageVector by lazy { build("PaintBucket", listOf("M11 7 6 2", "M18.992 12H2.041", "M21.145 18.38A3.34 3.34 0 0 1 20 16.5a3.3 3.3 0 0 1-1.145 1.88c-.575.46-.855 1.02-.855 1.595A2 2 0 0 0 20 22a2 2 0 0 0 2-2.025c0-.58-.285-1.13-.855-1.595", "m8.5 4.5 2.148-2.148a1.205 1.205 0 0 1 1.704 0l7.296 7.296a1.205 1.205 0 0 1 0 1.704l-7.592 7.592a3.615 3.615 0 0 1-5.112 0l-3.888-3.888a3.615 3.615 0 0 1 0-5.112L5.67 7.33")) }
    /** Lucide `paint-roller`. */
    val PaintRoller: ImageVector by lazy { build("PaintRoller", listOf("M4.0 2.0h12.0a2.0 2.0 0 0 1 2.0 2.0v2.0a2.0 2.0 0 0 1 -2.0 2.0h-12.0a2.0 2.0 0 0 1 -2.0 -2.0v-2.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M10 16v-2a2 2 0 0 1 2-2h8a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2", "M9.0 16.0h2.0a1.0 1.0 0 0 1 1.0 1.0v4.0a1.0 1.0 0 0 1 -1.0 1.0h-2.0a1.0 1.0 0 0 1 -1.0 -1.0v-4.0a1.0 1.0 0 0 1 1.0 -1.0Z")) }
    /** Lucide `palette`. */
    val Palette: ImageVector by lazy { build("Palette", listOf("M12 22a1 1 0 0 1 0-20 10 9 0 0 1 10 9 5 5 0 0 1-5 5h-2.25a1.75 1.75 0 0 0-1.4 2.8l.3.4a1.75 1.75 0 0 1-1.4 2.8z", "FILL:M13.0 6.5a0.5 0.5 0 1 0 1.0 0a0.5 0.5 0 1 0 -1.0 0Z", "FILL:M17.0 10.5a0.5 0.5 0 1 0 1.0 0a0.5 0.5 0 1 0 -1.0 0Z", "FILL:M6.0 12.5a0.5 0.5 0 1 0 1.0 0a0.5 0.5 0 1 0 -1.0 0Z", "FILL:M8.0 7.5a0.5 0.5 0 1 0 1.0 0a0.5 0.5 0 1 0 -1.0 0Z")) }
    /** Lucide `party-popper`. */
    val PartyPopper: ImageVector by lazy { build("PartyPopper", listOf("M5.8 11.3 2 22l10.7-3.79", "M4 3h.01", "M22 8h.01", "M15 2h.01", "M22 20h.01", "m22 2-2.24.75a2.9 2.9 0 0 0-1.96 3.12c.1.86-.57 1.63-1.45 1.63h-.38c-.86 0-1.6.6-1.76 1.44L14 10", "m22 13-.82-.33c-.86-.34-1.82.2-1.98 1.11c-.11.7-.72 1.22-1.43 1.22H17", "m11 2 .33.82c.34.86-.2 1.82-1.11 1.98C9.52 4.9 9 5.52 9 6.23V7", "M11 13c1.93 1.93 2.83 4.17 2 5-.83.83-3.07-.07-5-2-1.93-1.93-2.83-4.17-2-5 .83-.83 3.07.07 5 2Z")) }
    /** Lucide `pause`. */
    val Pause: ImageVector by lazy { build("Pause", listOf("M15.0 3.0h3.0a1.0 1.0 0 0 1 1.0 1.0v16.0a1.0 1.0 0 0 1 -1.0 1.0h-3.0a1.0 1.0 0 0 1 -1.0 -1.0v-16.0a1.0 1.0 0 0 1 1.0 -1.0Z", "M6.0 3.0h3.0a1.0 1.0 0 0 1 1.0 1.0v16.0a1.0 1.0 0 0 1 -1.0 1.0h-3.0a1.0 1.0 0 0 1 -1.0 -1.0v-16.0a1.0 1.0 0 0 1 1.0 -1.0Z")) }
    /** Lucide `pencil`. */
    val Pencil: ImageVector by lazy { build("Pencil", listOf("M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z", "m15 5 4 4")) }
    /** Lucide `picture-in-picture`. */
    val PictureInPicture: ImageVector by lazy { build("PictureInPicture", listOf("M2 10h6V4", "m2 4 6 6", "M21 10V7a2 2 0 0 0-2-2h-7", "M3 14v2a2 2 0 0 0 2 2h3", "M13.0 14.0h8.0a1.0 1.0 0 0 1 1.0 1.0v5.0a1.0 1.0 0 0 1 -1.0 1.0h-8.0a1.0 1.0 0 0 1 -1.0 -1.0v-5.0a1.0 1.0 0 0 1 1.0 -1.0Z")) }
    /** Lucide `plane`. */
    val Plane: ImageVector by lazy { build("Plane", listOf("M17.8 19.2 16 11l3.5-3.5C21 6 21.5 4 21 3c-1-.5-3 0-4.5 1.5L13 8 4.8 6.2c-.5-.1-.9.1-1.1.5l-.3.5c-.2.5-.1 1 .3 1.3L9 12l-2 3H4l-1 1 3 2 2 3 1-1v-3l3-2 3.5 5.3c.3.4.8.5 1.3.3l.5-.2c.4-.3.6-.7.5-1.2z")) }
    /** Lucide `play`. */
    val Play: ImageVector by lazy { build("Play", listOf("M5 5a2 2 0 0 1 3.008-1.728l11.997 6.998a2 2 0 0 1 .003 3.458l-12 7A2 2 0 0 1 5 19z")) }
    /** Lucide `plus`. */
    val Plus: ImageVector by lazy { build("Plus", listOf("M5 12h14", "M12 5v14")) }
    /** Lucide `pointer`. */
    val Pointer: ImageVector by lazy { build("Pointer", listOf("M22 14a8 8 0 0 1-8 8", "M18 11v-1a2 2 0 0 0-2-2a2 2 0 0 0-2 2", "M14 10V9a2 2 0 0 0-2-2a2 2 0 0 0-2 2v1", "M10 9.5V4a2 2 0 0 0-2-2a2 2 0 0 0-2 2v10", "M18 11a2 2 0 1 1 4 0v3a8 8 0 0 1-8 8h-2c-2.8 0-4.5-.86-5.99-2.34l-3.6-3.6a2 2 0 0 1 2.83-2.82L7 15")) }
    /** Lucide `popcorn`. */
    val Popcorn: ImageVector by lazy { build("Popcorn", listOf("M18 8a2 2 0 0 0 0-4 2 2 0 0 0-4 0 2 2 0 0 0-4 0 2 2 0 0 0-4 0 2 2 0 0 0 0 4", "M10 22 9 8", "m14 22 1-14", "M20 8c.5 0 .9.4.8 1l-2.6 12c-.1.5-.7 1-1.2 1H7c-.6 0-1.1-.4-1.2-1L3.2 9c-.1-.6.3-1 .8-1Z")) }
    /** Lucide `presentation`. */
    val Presentation: ImageVector by lazy { build("Presentation", listOf("M2 3h20", "M21 3v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V3", "m7 21 5-5 5 5")) }
    /** Lucide `puzzle`. */
    val Puzzle: ImageVector by lazy { build("Puzzle", listOf("M15.39 4.39a1 1 0 0 0 1.68-.474 2.5 2.5 0 1 1 3.014 3.015 1 1 0 0 0-.474 1.68l1.683 1.682a2.414 2.414 0 0 1 0 3.414L19.61 15.39a1 1 0 0 1-1.68-.474 2.5 2.5 0 1 0-3.014 3.015 1 1 0 0 1 .474 1.68l-1.683 1.682a2.414 2.414 0 0 1-3.414 0L8.61 19.61a1 1 0 0 0-1.68.474 2.5 2.5 0 1 1-3.014-3.015 1 1 0 0 0 .474-1.68l-1.683-1.682a2.414 2.414 0 0 1 0-3.414L4.39 8.61a1 1 0 0 1 1.68.474 2.5 2.5 0 1 0 3.014-3.015 1 1 0 0 1-.474-1.68l1.683-1.682a2.414 2.414 0 0 1 3.414 0z")) }
    /** Lucide `quote`. */
    val Quote: ImageVector by lazy { build("Quote", listOf("M16 3a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2 1 1 0 0 1 1 1v1a2 2 0 0 1-2 2 1 1 0 0 0-1 1v2a1 1 0 0 0 1 1 6 6 0 0 0 6-6V5a2 2 0 0 0-2-2z", "M5 3a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2 1 1 0 0 1 1 1v1a2 2 0 0 1-2 2 1 1 0 0 0-1 1v2a1 1 0 0 0 1 1 6 6 0 0 0 6-6V5a2 2 0 0 0-2-2z")) }
    /** Lucide `ratio`. */
    val Ratio: ImageVector by lazy { build("Ratio", listOf("M8.0 2.0h8.0a2.0 2.0 0 0 1 2.0 2.0v16.0a2.0 2.0 0 0 1 -2.0 2.0h-8.0a2.0 2.0 0 0 1 -2.0 -2.0v-16.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M4.0 6.0h16.0a2.0 2.0 0 0 1 2.0 2.0v8.0a2.0 2.0 0 0 1 -2.0 2.0h-16.0a2.0 2.0 0 0 1 -2.0 -2.0v-8.0a2.0 2.0 0 0 1 2.0 -2.0Z")) }
    /** Lucide `refresh-cw`. */
    val RefreshCw: ImageVector by lazy { build("RefreshCw", listOf("M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8", "M21 3v5h-5", "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16", "M8 16H3v5")) }
    /** Lucide `remove-formatting`. */
    val RemoveFormatting: ImageVector by lazy { build("RemoveFormatting", listOf("M4 7V4h16v3", "M5 20h6", "M13 4 8 20", "m15 15 5 5", "m20 15-5 5")) }
    /** Lucide `repeat`. */
    val Repeat: ImageVector by lazy { build("Repeat", listOf("m17 2 4 4-4 4", "M3 11v-1a4 4 0 0 1 4-4h14", "m7 22-4-4 4-4", "M21 13v1a4 4 0 0 1-4 4H3")) }
    /** Lucide `repeat-1`. */
    val Repeat1: ImageVector by lazy { build("Repeat1", listOf("m17 2 4 4-4 4", "M3 11v-1a4 4 0 0 1 4-4h14", "m7 22-4-4 4-4", "M21 13v1a4 4 0 0 1-4 4H3", "M11 10h1v4")) }
    /** Lucide `reply`. */
    val Reply: ImageVector by lazy { build("Reply", listOf("M20 18v-2a4 4 0 0 0-4-4H4", "m9 17-5-5 5-5")) }
    /** Lucide `rewind`. */
    val Rewind: ImageVector by lazy { build("Rewind", listOf("M12 6a2 2 0 0 0-3.414-1.414l-6 6a2 2 0 0 0 0 2.828l6 6A2 2 0 0 0 12 18z", "M22 6a2 2 0 0 0-3.414-1.414l-6 6a2 2 0 0 0 0 2.828l6 6A2 2 0 0 0 22 18z")) }
    /** Lucide `rocket`. */
    val Rocket: ImageVector by lazy { build("Rocket", listOf("M12 15v5s3.03-.55 4-2c1.08-1.62 0-5 0-5", "M4.5 16.5c-1.5 1.26-2 5-2 5s3.74-.5 5-2c.71-.84.7-2.13-.09-2.91a2.18 2.18 0 0 0-2.91-.09", "M9 12a22 22 0 0 1 2-3.95A12.88 12.88 0 0 1 22 2c0 2.72-.78 7.5-6 11a22.4 22.4 0 0 1-4 2z", "M9 12H4s.55-3.03 2-4c1.62-1.08 5 .05 5 .05")) }
    /** Lucide `rotate-ccw`. */
    val RotateCcw: ImageVector by lazy { build("RotateCcw", listOf("M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8", "M3 3v5h5")) }
    /** Lucide `rotate-cw`. */
    val RotateCw: ImageVector by lazy { build("RotateCw", listOf("M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8", "M21 3v5h-5")) }
    /** Lucide `rows-3`. */
    val Rows3: ImageVector by lazy { build("Rows3", listOf("M5.0 3.0h14.0a2.0 2.0 0 0 1 2.0 2.0v14.0a2.0 2.0 0 0 1 -2.0 2.0h-14.0a2.0 2.0 0 0 1 -2.0 -2.0v-14.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M21 9H3", "M21 15H3")) }
    /** Lucide `rss`. */
    val Rss: ImageVector by lazy { build("Rss", listOf("M4 11a9 9 0 0 1 9 9", "M4 4a16 16 0 0 1 16 16", "M4.0 19.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0Z")) }
    /** Lucide `satellite`. */
    val Satellite: ImageVector by lazy { build("Satellite", listOf("m13.5 6.5-3.148-3.148a1.205 1.205 0 0 0-1.704 0L6.352 5.648a1.205 1.205 0 0 0 0 1.704L9.5 10.5", "M16.5 7.5 19 5", "m17.5 10.5 3.148 3.148a1.205 1.205 0 0 1 0 1.704l-2.296 2.296a1.205 1.205 0 0 1-1.704 0L13.5 14.5", "M9 21a6 6 0 0 0-6-6", "M9.352 10.648a1.205 1.205 0 0 0 0 1.704l2.296 2.296a1.205 1.205 0 0 0 1.704 0l4.296-4.296a1.205 1.205 0 0 0 0-1.704l-2.296-2.296a1.205 1.205 0 0 0-1.704 0z")) }
    /** Lucide `satellite-dish`. */
    val SatelliteDish: ImageVector by lazy { build("SatelliteDish", listOf("M18 12a6 6 0 00-6-6", "M2.824 10.459a8 8 0 0010.717 10.717c.558-.276.623-1.012.183-1.452l-9.448-9.448c-.44-.44-1.176-.375-1.452.183", "M22 12A10 10 0 0012 2", "m9 15 4-4")) }
    /** Lucide `save`. */
    val Save: ImageVector by lazy { build("Save", listOf("M15.2 3a2 2 0 0 1 1.4.6l3.8 3.8a2 2 0 0 1 .6 1.4V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M17 21v-7a1 1 0 0 0-1-1H8a1 1 0 0 0-1 1v7", "M7 3v4a1 1 0 0 0 1 1h7")) }
    /** Lucide `scan`. */
    val Scan: ImageVector by lazy { build("Scan", listOf("M3 7V5a2 2 0 0 1 2-2h2", "M17 3h2a2 2 0 0 1 2 2v2", "M21 17v2a2 2 0 0 1-2 2h-2", "M7 21H5a2 2 0 0 1-2-2v-2")) }
    /** Lucide `school`. */
    val School: ImageVector by lazy { build("School", listOf("M14 21v-3a2 2 0 0 0-4 0v3", "M18 4.933V21", "m4 6 7.106-3.79a2 2 0 0 1 1.788 0L20 6", "m6 11-3.52 2.147a1 1 0 0 0-.48.854V19a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-5a1 1 0 0 0-.48-.853L18 11", "M6 4.933V21", "M10.0 9.0a2.0 2.0 0 1 0 4.0 0a2.0 2.0 0 1 0 -4.0 0Z")) }
    /** Lucide `scroll`. */
    val Scroll: ImageVector by lazy { build("Scroll", listOf("M19 17V5a2 2 0 0 0-2-2H4", "M8 21h12a2 2 0 0 0 2-2v-1a1 1 0 0 0-1-1H11a1 1 0 0 0-1 1v1a2 2 0 1 1-4 0V5a2 2 0 1 0-4 0v2a1 1 0 0 0 1 1h3")) }
    /** Lucide `search`. */
    val Search: ImageVector by lazy { build("Search", listOf("m21 21-4.34-4.34", "M3.0 11.0a8.0 8.0 0 1 0 16.0 0a8.0 8.0 0 1 0 -16.0 0Z")) }
    /** Lucide `search-x`. */
    val SearchX: ImageVector by lazy { build("SearchX", listOf("m13.5 8.5-5 5", "m8.5 8.5 5 5", "M3.0 11.0a8.0 8.0 0 1 0 16.0 0a8.0 8.0 0 1 0 -16.0 0Z", "m21 21-4.3-4.3")) }
    /** Lucide `send`. */
    val Send: ImageVector by lazy { build("Send", listOf("M14.536 21.686a.5.5 0 0 0 .937-.024l6.5-19a.496.496 0 0 0-.635-.635l-19 6.5a.5.5 0 0 0-.024.937l7.93 3.18a2 2 0 0 1 1.112 1.11z", "m21.854 2.147-10.94 10.939")) }
    /** Lucide `server`. */
    val Server: ImageVector by lazy { build("Server", listOf("M4.0 2.0h16.0a2.0 2.0 0 0 1 2.0 2.0v4.0a2.0 2.0 0 0 1 -2.0 2.0h-16.0a2.0 2.0 0 0 1 -2.0 -2.0v-4.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M4.0 14.0h16.0a2.0 2.0 0 0 1 2.0 2.0v4.0a2.0 2.0 0 0 1 -2.0 2.0h-16.0a2.0 2.0 0 0 1 -2.0 -2.0v-4.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M6 6L6.01 6", "M6 18L6.01 18")) }
    /** Lucide `settings`. */
    val Settings: ImageVector by lazy { build("Settings", listOf("M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915", "M9.0 12.0a3.0 3.0 0 1 0 6.0 0a3.0 3.0 0 1 0 -6.0 0Z")) }
    /** Lucide `settings-2`. */
    val Settings2: ImageVector by lazy { build("Settings2", listOf("M14 17H5", "M19 7h-9", "M14.0 17.0a3.0 3.0 0 1 0 6.0 0a3.0 3.0 0 1 0 -6.0 0Z", "M4.0 7.0a3.0 3.0 0 1 0 6.0 0a3.0 3.0 0 1 0 -6.0 0Z")) }
    /** Lucide `shapes`. */
    val Shapes: ImageVector by lazy { build("Shapes", listOf("M8.3 10a.7.7 0 0 1-.626-1.079L11.4 3a.7.7 0 0 1 1.198-.043L16.3 8.9a.7.7 0 0 1-.572 1.1Z", "M4.0 14.0h5.0a1.0 1.0 0 0 1 1.0 1.0v5.0a1.0 1.0 0 0 1 -1.0 1.0h-5.0a1.0 1.0 0 0 1 -1.0 -1.0v-5.0a1.0 1.0 0 0 1 1.0 -1.0Z", "M14.0 17.5a3.5 3.5 0 1 0 7.0 0a3.5 3.5 0 1 0 -7.0 0Z")) }
    /** Lucide `share-2`. */
    val Share2: ImageVector by lazy { build("Share2", listOf("M15.0 5.0a3.0 3.0 0 1 0 6.0 0a3.0 3.0 0 1 0 -6.0 0Z", "M3.0 12.0a3.0 3.0 0 1 0 6.0 0a3.0 3.0 0 1 0 -6.0 0Z", "M15.0 19.0a3.0 3.0 0 1 0 6.0 0a3.0 3.0 0 1 0 -6.0 0Z", "M8.59 13.51L15.42 17.49", "M15.41 6.51L8.59 10.49")) }
    /** Lucide `shield`. */
    val Shield: ImageVector by lazy { build("Shield", listOf("M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z")) }
    /** Lucide `shield-check`. */
    val ShieldCheck: ImageVector by lazy { build("ShieldCheck", listOf("M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z", "m9 12 2 2 4-4")) }
    /** Lucide `signal`. */
    val Signal: ImageVector by lazy { build("Signal", listOf("M2 20h.01", "M7 20v-4", "M12 20v-8", "M17 20V8", "M22 4v16")) }
    /** Lucide `signal-high`. */
    val SignalHigh: ImageVector by lazy { build("SignalHigh", listOf("M2 20h.01", "M7 20v-4", "M12 20v-8", "M17 20V8")) }
    /** Lucide `skip-back`. */
    val SkipBack: ImageVector by lazy { build("SkipBack", listOf("M17.971 4.285A2 2 0 0 1 21 6v12a2 2 0 0 1-3.029 1.715l-9.997-5.998a2 2 0 0 1-.003-3.432z", "M3 20V4")) }
    /** Lucide `skip-forward`. */
    val SkipForward: ImageVector by lazy { build("SkipForward", listOf("M21 4v16", "M6.029 4.285A2 2 0 0 0 3 6v12a2 2 0 0 0 3.029 1.715l9.997-5.998a2 2 0 0 0 .003-3.432z")) }
    /** Lucide `skull`. */
    val Skull: ImageVector by lazy { build("Skull", listOf("m12.5 17-.5-1-.5 1h1z", "M15 22a1 1 0 0 0 1-1v-1a2 2 0 0 0 1.56-3.25 8 8 0 1 0-11.12 0A2 2 0 0 0 8 20v1a1 1 0 0 0 1 1z", "M14.0 12.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0Z", "M8.0 12.0a1.0 1.0 0 1 0 2.0 0a1.0 1.0 0 1 0 -2.0 0Z")) }
    /** Lucide `sliders-horizontal`. */
    val SlidersHorizontal: ImageVector by lazy { build("SlidersHorizontal", listOf("M10 5H3", "M12 19H3", "M14 3v4", "M16 17v4", "M21 12h-9", "M21 19h-5", "M21 5h-7", "M8 10v4", "M8 12H3")) }
    /** Lucide `smartphone`. */
    val Smartphone: ImageVector by lazy { build("Smartphone", listOf("M7.0 2.0h10.0a2.0 2.0 0 0 1 2.0 2.0v16.0a2.0 2.0 0 0 1 -2.0 2.0h-10.0a2.0 2.0 0 0 1 -2.0 -2.0v-16.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M12 18h.01")) }
    /** Lucide `snowflake`. */
    val Snowflake: ImageVector by lazy { build("Snowflake", listOf("m10 20-1.25-2.5L6 18", "M10 4 8.75 6.5 6 6", "m14 20 1.25-2.5L18 18", "m14 4 1.25 2.5L18 6", "m17 21-3-6h-4", "m17 3-3 6 1.5 3", "M2 12h6.5L10 9", "m20 10-1.5 2 1.5 2", "M22 12h-6.5L14 15", "m4 10 1.5 2L4 14", "m7 21 3-6-1.5-3", "m7 3 3 6h4")) }
    /** Lucide `sparkles`. */
    val Sparkles: ImageVector by lazy { build("Sparkles", listOf("M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1 0 1.966l-5.558 1.051a2 2 0 0 0-1.594 1.594l-1.051 5.558a1 1 0 0 1-1.966 0l-1.051-5.558a2 2 0 0 0-1.594-1.594l-5.558-1.051a1 1 0 0 1 0-1.966l5.558-1.051a2 2 0 0 0 1.594-1.594z", "M20 2v4", "M22 4h-4", "M2.0 20.0a2.0 2.0 0 1 0 4.0 0a2.0 2.0 0 1 0 -4.0 0Z")) }
    /** Lucide `sprout`. */
    val Sprout: ImageVector by lazy { build("Sprout", listOf("M14 9.536V7a4 4 0 0 1 4-4h1.5a.5.5 0 0 1 .5.5V5a4 4 0 0 1-4 4 4 4 0 0 0-4 4c0 2 1 3 1 5a5 5 0 0 1-1 3", "M4 9a5 5 0 0 1 8 4 5 5 0 0 1-8-4", "M5 21h14")) }
    /** Lucide `square`. */
    val Square: ImageVector by lazy { build("Square", listOf("M5.0 3.0h14.0a2.0 2.0 0 0 1 2.0 2.0v14.0a2.0 2.0 0 0 1 -2.0 2.0h-14.0a2.0 2.0 0 0 1 -2.0 -2.0v-14.0a2.0 2.0 0 0 1 2.0 -2.0Z")) }
    /** Lucide `square-dashed`. */
    val SquareDashed: ImageVector by lazy { build("SquareDashed", listOf("M5 3a2 2 0 0 0-2 2", "M19 3a2 2 0 0 1 2 2", "M21 19a2 2 0 0 1-2 2", "M5 21a2 2 0 0 1-2-2", "M9 3h1", "M9 21h1", "M14 3h1", "M14 21h1", "M3 9v1", "M21 9v1", "M3 14v1", "M21 14v1")) }
    /** Lucide `square-plus`. */
    val SquarePlus: ImageVector by lazy { build("SquarePlus", listOf("M5.0 3.0h14.0a2.0 2.0 0 0 1 2.0 2.0v14.0a2.0 2.0 0 0 1 -2.0 2.0h-14.0a2.0 2.0 0 0 1 -2.0 -2.0v-14.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M8 12h8", "M12 8v8")) }
    /** Lucide `star`. */
    val StarStroke: ImageVector by lazy { build("StarStroke", listOf("M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.123 2.123 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.736 3.638a2.123 2.123 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.122 2.122 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.122 2.122 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.122 2.122 0 0 0 1.597-1.16z")) }
    /** Lucide `star` — dolu (seçili/aktif) varyant. */
    val StarSolid: ImageVector by lazy { build("StarSolid", listOf("M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.123 2.123 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.736 3.638a2.123 2.123 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.122 2.122 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.122 2.122 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.122 2.122 0 0 0 1.597-1.16z"), solid = true) }
    /** Lucide `star-off`. */
    val StarOff: ImageVector by lazy { build("StarOff", listOf("m10.344 4.688 1.181-2.393a.53.53 0 0 1 .95 0l2.31 4.679a2.12 2.12 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.237 3.152", "m17.945 17.945.43 2.505a.53.53 0 0 1-.771.56l-4.618-2.428a2.12 2.12 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.12 2.12 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a8 8 0 0 0 .4-.099", "m2 2 20 20")) }
    /** Lucide `strikethrough`. */
    val Strikethrough: ImageVector by lazy { build("Strikethrough", listOf("M16 4H9a3 3 0 0 0-2.83 4", "M14 12a4 4 0 0 1 0 8H6", "M4 12L20 12")) }
    /** Lucide `sun`. */
    val Sun: ImageVector by lazy { build("Sun", listOf("M8.0 12.0a4.0 4.0 0 1 0 8.0 0a4.0 4.0 0 1 0 -8.0 0Z", "M12 2v2", "M12 20v2", "m4.93 4.93 1.41 1.41", "m17.66 17.66 1.41 1.41", "M2 12h2", "M20 12h2", "m6.34 17.66-1.41 1.41", "m19.07 4.93-1.41 1.41")) }
    /** Lucide `sun-dim`. */
    val SunDim: ImageVector by lazy { build("SunDim", listOf("M8.0 12.0a4.0 4.0 0 1 0 8.0 0a4.0 4.0 0 1 0 -8.0 0Z", "M12 4h.01", "M20 12h.01", "M12 20h.01", "M4 12h.01", "M17.657 6.343h.01", "M17.657 17.657h.01", "M6.343 17.657h.01", "M6.343 6.343h.01")) }
    /** Lucide `sun-medium`. */
    val SunMedium: ImageVector by lazy { build("SunMedium", listOf("M8.0 12.0a4.0 4.0 0 1 0 8.0 0a4.0 4.0 0 1 0 -8.0 0Z", "M12 3v1", "M12 20v1", "M3 12h1", "M20 12h1", "m18.364 5.636-.707.707", "m6.343 17.657-.707.707", "m5.636 5.636.707.707", "m17.657 17.657.707.707")) }
    /** Lucide `sword`. */
    val Sword: ImageVector by lazy { build("Sword", listOf("m11 19-6-6", "m5 21-2-2", "m8 16-4 4", "M9.5 17.5 20.414 6.586A2 2 0 0021 5.172V3h-2.172a2 2 0 00-1.414.586L6.5 14.5")) }
    /** Lucide `swords`. */
    val Swords: ImageVector by lazy { build("Swords", listOf("m13 19 6-6", "M14.5 17.5 3.586 6.586A2 2 0 013 5.172V3h2.172a2 2 0 011.414.586L17.5 14.5", "m14.828 6.172 2.586-2.586A2 2 0 0118.828 3H21v2.172a2 2 0 01-.586 1.414l-2.586 2.586", "m16 16 4 4", "m19 21 2-2", "m5 14 4 4", "m5 21-2-2", "M7.5 16.5 4 20")) }
    /** Lucide `tablet`. */
    val Tablet: ImageVector by lazy { build("Tablet", listOf("M6.0 2.0h12.0a2.0 2.0 0 0 1 2.0 2.0v16.0a2.0 2.0 0 0 1 -2.0 2.0h-12.0a2.0 2.0 0 0 1 -2.0 -2.0v-16.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M12 18L12.01 18")) }
    /** Lucide `tag`. */
    val Tag: ImageVector by lazy { build("Tag", listOf("M12.586 2.586A2 2 0 0 0 11.172 2H4a2 2 0 0 0-2 2v7.172a2 2 0 0 0 .586 1.414l8.704 8.704a2.426 2.426 0 0 0 3.42 0l6.58-6.58a2.426 2.426 0 0 0 0-3.42z", "FILL:M7.0 7.5a0.5 0.5 0 1 0 1.0 0a0.5 0.5 0 1 0 -1.0 0Z")) }
    /** Lucide `target`. */
    val Target: ImageVector by lazy { build("Target", listOf("M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0Z", "M6.0 12.0a6.0 6.0 0 1 0 12.0 0a6.0 6.0 0 1 0 -12.0 0Z", "M10.0 12.0a2.0 2.0 0 1 0 4.0 0a2.0 2.0 0 1 0 -4.0 0Z")) }
    /** Lucide `text-align-center`. */
    val TextAlignCenter: ImageVector by lazy { build("TextAlignCenter", listOf("M21 5H3", "M17 12H7", "M19 19H5")) }
    /** Lucide `text-align-end`. */
    val TextAlignEnd: ImageVector by lazy { build("TextAlignEnd", listOf("M21 5H3", "M21 12H9", "M21 19H7")) }
    /** Lucide `text-align-justify`. */
    val TextAlignJustify: ImageVector by lazy { build("TextAlignJustify", listOf("M3 5h18", "M3 12h18", "M3 19h18")) }
    /** Lucide `text-align-start`. */
    val TextAlignStart: ImageVector by lazy { build("TextAlignStart", listOf("M21 5H3", "M15 12H3", "M17 19H3")) }
    /** Lucide `theater`. */
    val Theater: ImageVector by lazy { build("Theater", listOf("M2 10s3-3 3-8", "M22 10s-3-3-3-8", "M10 2c0 4.4-3.6 8-8 8", "M14 2c0 4.4 3.6 8 8 8", "M2 10s2 2 2 5", "M22 10s-2 2-2 5", "M8 15h8", "M2 22v-1a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v1", "M14 22v-1a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v1")) }
    /** Lucide `thumbs-down`. */
    val ThumbsDown: ImageVector by lazy { build("ThumbsDown", listOf("M9 18.12 10 14H4.17a2 2 0 0 1-1.92-2.56l2.33-8A2 2 0 0 1 6.5 2H20a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-2.76a2 2 0 0 0-1.79 1.11L12 22a3.13 3.13 0 0 1-3-3.88Z", "M17 14V2")) }
    /** Lucide `thumbs-up`. */
    val ThumbsUp: ImageVector by lazy { build("ThumbsUp", listOf("M15 5.88 14 10h5.83a2 2 0 0 1 1.92 2.56l-2.33 8A2 2 0 0 1 17.5 22H4a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2h2.76a2 2 0 0 0 1.79-1.11L12 2a3.13 3.13 0 0 1 3 3.88Z", "M7 10v12")) }
    /** Lucide `ticket`. */
    val Ticket: ImageVector by lazy { build("Ticket", listOf("M2 9a3 3 0 0 1 0 6v2a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-2a3 3 0 0 1 0-6V7a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2Z", "M13 5v2", "M13 17v2", "M13 11v2")) }
    /** Lucide `timer`. */
    val Timer: ImageVector by lazy { build("Timer", listOf("M10 2L14 2", "M12 14L15 11", "M4.0 14.0a8.0 8.0 0 1 0 16.0 0a8.0 8.0 0 1 0 -16.0 0Z")) }
    /** Lucide `trash`. */
    val Trash: ImageVector by lazy { build("Trash", listOf("M10 11v6", "M14 11v6", "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6", "M3 6h18", "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2")) }
    /** Lucide `trees`. */
    val Trees: ImageVector by lazy { build("Trees", listOf("M10 10v.2A3 3 0 0 1 8.9 16H5a3 3 0 0 1-1-5.8V10a3 3 0 0 1 6 0Z", "M7 16v6", "M13 19v3", "M12 19h8.3a1 1 0 0 0 .7-1.7L18 14h.3a1 1 0 0 0 .7-1.7L16 9h.2a1 1 0 0 0 .8-1.7L13 3l-1.4 1.5")) }
    /** Lucide `trending-down`. */
    val TrendingDown: ImageVector by lazy { build("TrendingDown", listOf("M16 17h6v-6", "m22 17-8.5-8.5-5 5L2 7")) }
    /** Lucide `trending-up`. */
    val TrendingUp: ImageVector by lazy { build("TrendingUp", listOf("M16 7h6v6", "m22 7-8.5 8.5-5-5L2 17")) }
    /** Lucide `triangle-alert`. */
    val TriangleAlert: ImageVector by lazy { build("TriangleAlert", listOf("m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3", "M12 9v4", "M12 17h.01")) }
    /** Lucide `trophy`. */
    val Trophy: ImageVector by lazy { build("Trophy", listOf("M10 14.66V17a1 1 0 0 1-1 1 2 2 0 0 0-2 2v2", "M14 14.66V17a1 1 0 0 0 1 1 2 2 0 0 1 2 2v2", "M17.916 10H19.5A2.5 2.5 0 0 0 22 7.5V5a1 1 0 0 0-1-1h-3", "M4 22h16", "M6 9a6 6 0 0 0 12 0V3a1 1 0 0 0-1-1H7a1 1 0 0 0-1 1z", "M6.084 10H4.5A2.5 2.5 0 0 1 2 7.5V5a1 1 0 0 1 1-1h3")) }
    /** Lucide `tv`. */
    val Tv: ImageVector by lazy { build("Tv", listOf("m17 2-5 5-5-5", "M4.0 7.0h16.0a2.0 2.0 0 0 1 2.0 2.0v11.0a2.0 2.0 0 0 1 -2.0 2.0h-16.0a2.0 2.0 0 0 1 -2.0 -2.0v-11.0a2.0 2.0 0 0 1 2.0 -2.0Z")) }
    /** Lucide `type`. */
    val Type: ImageVector by lazy { build("Type", listOf("M12 4v16", "M4 7V5a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1v2", "M9 20h6")) }
    /** Lucide `unlink`. */
    val Unlink: ImageVector by lazy { build("Unlink", listOf("m18.84 12.25 1.72-1.71h-.02a5.004 5.004 0 0 0-.12-7.07 5.006 5.006 0 0 0-6.95 0l-1.72 1.71", "m5.17 11.75-1.71 1.71a5.004 5.004 0 0 0 .12 7.07 5.006 5.006 0 0 0 6.95 0l1.71-1.71", "M8 2L8 5", "M2 8L5 8", "M16 19L16 22", "M19 16L22 16")) }
    /** Lucide `upload`. */
    val Upload: ImageVector by lazy { build("Upload", listOf("M12 3v12", "m17 8-5-5-5 5", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4")) }
    /** Lucide `user`. */
    val User: ImageVector by lazy { build("User", listOf("M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2", "M8.0 7.0a4.0 4.0 0 1 0 8.0 0a4.0 4.0 0 1 0 -8.0 0Z")) }
    /** Lucide `user-plus`. */
    val UserPlus: ImageVector by lazy { build("UserPlus", listOf("M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2", "M5.0 7.0a4.0 4.0 0 1 0 8.0 0a4.0 4.0 0 1 0 -8.0 0Z", "M19 8L19 14", "M22 11L16 11")) }
    /** Lucide `user-round`. */
    val UserRound: ImageVector by lazy { build("UserRound", listOf("M7.0 8.0a5.0 5.0 0 1 0 10.0 0a5.0 5.0 0 1 0 -10.0 0Z", "M20 21a8 8 0 0 0-16 0")) }
    /** Lucide `users`. */
    val Users: ImageVector by lazy { build("Users", listOf("M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2", "M16 3.128a4 4 0 0 1 0 7.744", "M22 21v-2a4 4 0 0 0-3-3.87", "M5.0 7.0a4.0 4.0 0 1 0 8.0 0a4.0 4.0 0 1 0 -8.0 0Z")) }
    /** Lucide `venetian-mask`. */
    val VenetianMask: ImageVector by lazy { build("VenetianMask", listOf("M18 11c-1.5 0-2.5.5-3 2", "M4 6a2 2 0 0 0-2 2v4a5 5 0 0 0 5 5 8 8 0 0 1 5 2 8 8 0 0 1 5-2 5 5 0 0 0 5-5V8a2 2 0 0 0-2-2h-3a8 8 0 0 0-5 2 8 8 0 0 0-5-2z", "M6 11c1.5 0 2.5.5 3 2")) }
    /** Lucide `video`. */
    val Video: ImageVector by lazy { build("Video", listOf("m16 13 5.223 3.482a.5.5 0 0 0 .777-.416V7.87a.5.5 0 0 0-.752-.432L16 10.5", "M4.0 6.0h10.0a2.0 2.0 0 0 1 2.0 2.0v8.0a2.0 2.0 0 0 1 -2.0 2.0h-10.0a2.0 2.0 0 0 1 -2.0 -2.0v-8.0a2.0 2.0 0 0 1 2.0 -2.0Z")) }
    /** Lucide `volume-1`. */
    val Volume1: ImageVector by lazy { build("Volume1", listOf("M11 4.702a.705.705 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.705.705 0 0 0 11 19.298z", "M16 9a5 5 0 0 1 0 6")) }
    /** Lucide `volume-2`. */
    val Volume2: ImageVector by lazy { build("Volume2", listOf("M11 4.702a.705.705 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.705.705 0 0 0 11 19.298z", "M16 9a5 5 0 0 1 0 6", "M19.364 18.364a9 9 0 0 0 0-12.728")) }
    /** Lucide `volume-x`. */
    val VolumeX: ImageVector by lazy { build("VolumeX", listOf("M11 4.702a.7.7 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.7.7 0 0 0 11 19.298z", "m16.5 14.5 5-5", "m16.5 9.5 5 5")) }
    /** Lucide `wand-sparkles`. */
    val WandSparkles: ImageVector by lazy { build("WandSparkles", listOf("m21.64 3.64-1.28-1.28a1.21 1.21 0 0 0-1.72 0L2.36 18.64a1.21 1.21 0 0 0 0 1.72l1.28 1.28a1.2 1.2 0 0 0 1.72 0L21.64 5.36a1.2 1.2 0 0 0 0-1.72", "m14 7 3 3", "M5 6v4", "M19 14v4", "M10 2v2", "M7 8H3", "M21 16h-4", "M11 3H9")) }
    /** Lucide `wifi`. */
    val Wifi: ImageVector by lazy { build("Wifi", listOf("M12 20h.01", "M2 8.82a15 15 0 0 1 20 0", "M5 12.859a10 10 0 0 1 14 0", "M8.5 16.429a5 5 0 0 1 7 0")) }
    /** Lucide `x`. */
    val X: ImageVector by lazy { build("X", listOf("M18 6 6 18", "m6 6 12 12")) }
    /** Lucide `zap`. */
    val Zap: ImageVector by lazy { build("Zap", listOf("M15.914 4a1.5 1.5 0 00-2.474-1.561l-9 9A1.5 1.5 0 005.5 14h4.002a.5.5 0 01.471.666L8.086 20a1.5 1.5 0 002.475 1.56l9-9A1.5 1.5 0 0018.5 10h-3.997a.5.5 0 01-.472-.667z")) }
    /** Lucide `zoom-in`. */
    val ZoomIn: ImageVector by lazy { build("ZoomIn", listOf("M3.0 11.0a8.0 8.0 0 1 0 16.0 0a8.0 8.0 0 1 0 -16.0 0Z", "M21 21L16.65 16.65", "M11 8L11 14", "M8 11L14 11")) }

    val AlertTriangle: ImageVector get() = TriangleAlert
    val Smile: ImageVector get() = FaceSlightlySmiling
    val Laugh: ImageVector get() = FaceGrinning
    val Frown: ImageVector get() = FaceSlightlyFrowning
    val Meh: ImageVector get() = FaceNeutral
    val Angry: ImageVector get() = FaceAngry
    // ── Uyumluluk adları: eski Material ikon adları → aynı HD ikon ────────────
    val AcUnit: ImageVector get() = Snowflake
    val AccessTime: ImageVector get() = Clock
    val AccountCircle: ImageVector get() = CircleUserRound
    val Add: ImageVector get() = Plus
    val AddCircle: ImageVector get() = CirclePlus
    val AddCircleOutline: ImageVector get() = CirclePlus
    val AddLink: ImageVector get() = Link2
    val Apps: ImageVector get() = LayoutGrid
    val ArrowBack: ImageVector get() = ArrowLeft
    val ArrowCircleUp: ImageVector get() = CircleArrowUp
    val ArrowDownward: ImageVector get() = ArrowDown
    val ArrowDropDown: ImageVector get() = ChevronDown
    val ArrowForward: ImageVector get() = ArrowRight
    val ArrowUpward: ImageVector get() = ArrowUp
    val AspectRatio: ImageVector get() = Ratio
    val Audiotrack: ImageVector get() = AudioLines
    val AutoAwesome: ImageVector get() = Sparkles
    val AutoStories: ImageVector get() = BookOpen
    val AvTimer: ImageVector get() = Timer
    val BarChart: ImageVector get() = ChartColumn
    val BlurOn: ImageVector get() = Droplets
    val Bolt: ImageVector get() = Zap
    val Bookmark: ImageVector get() = BookmarkSolid
    val BookmarkBorder: ImageVector get() = BookmarkStroke
    val Bookmarks: ImageVector get() = BookmarkStroke
    val BorderColor: ImageVector get() = PaintBucket
    val BorderOuter: ImageVector get() = Square
    val Brightness6: ImageVector get() = SunMedium
    val BrightnessHigh: ImageVector get() = Sun
    val BrightnessLow: ImageVector get() = SunDim
    val BrightnessMedium: ImageVector get() = SunMedium
    val BrokenImage: ImageVector get() = ImageOff
    val BugReport: ImageVector get() = Bug
    val Cached: ImageVector get() = RefreshCw
    val CalendarMonth: ImageVector get() = CalendarDays
    val CalendarToday: ImageVector get() = Calendar
    val CenterFocusWeak: ImageVector get() = Focus
    val ChatBubble: ImageVector get() = MessageCircle
    val ChatBubbleOutline: ImageVector get() = MessageCircle
    val CheckCircle: ImageVector get() = CircleCheck
    val CleaningServices: ImageVector get() = Sparkles
    val Clear: ImageVector get() = X
    val ClearAll: ImageVector get() = Eraser
    val Close: ImageVector get() = X
    val CloudSync: ImageVector get() = RefreshCw
    val ColorLens: ImageVector get() = Palette
    val Comment: ImageVector get() = MessageSquare
    val CompareArrows: ImageVector get() = ArrowLeftRight
    val ContentCopy: ImageVector get() = Copy
    val ContentPaste: ImageVector get() = Clipboard
    val ControlCamera: ImageVector get() = Video
    val CropFree: ImageVector get() = Scan
    val DarkMode: ImageVector get() = Moon
    val DateRange: ImageVector get() = Calendar
    val Delete: ImageVector get() = Trash
    val DeleteForever: ImageVector get() = Trash
    val DeleteOutline: ImageVector get() = Trash
    val DeleteSweep: ImageVector get() = Eraser
    val DensityMedium: ImageVector get() = Menu
    val DeveloperBoard: ImageVector get() = Cpu
    val Dns: ImageVector get() = Server
    val Done: ImageVector get() = Check
    val DoneAll: ImageVector get() = CheckCheck
    val DonutLarge: ImageVector get() = ChartPie
    val DoubleArrow: ImageVector get() = ArrowLeftRight
    val DownloadDone: ImageVector get() = Download
    val DownloadForOffline: ImageVector get() = Download
    val Downloading: ImageVector get() = Download
    val DragHandle: ImageVector get() = GripVertical
    val Edit: ImageVector get() = Pencil
    val EditNote: ImageVector get() = NotebookPen
    val EmojiEvents: ImageVector get() = Trophy
    val Equalizer: ImageVector get() = AudioLines
    val Error: ImageVector get() = CircleAlert
    val ErrorOutline: ImageVector get() = CircleAlert
    val Event: ImageVector get() = Calendar
    val EventAvailable: ImageVector get() = CalendarCheck
    val ExpandLess: ImageVector get() = ChevronUp
    val ExpandMore: ImageVector get() = ChevronDown
    val Explore: ImageVector get() = Compass
    val Extension: ImageVector get() = Puzzle
    val ExtensionOff: ImageVector get() = Puzzle
    val FastRewind: ImageVector get() = Rewind
    val Favorite: ImageVector get() = HeartSolid
    val FavoriteBorder: ImageVector get() = Heart
    val Feedback: ImageVector get() = MessageSquare
    val FilterAlt: ImageVector get() = SlidersHorizontal
    val FilterAltOff: ImageVector get() = SlidersHorizontal
    val FilterCenterFocus: ImageVector get() = Focus
    val FilterList: ImageVector get() = SlidersHorizontal
    val FitScreen: ImageVector get() = Maximize
    val FolderCopy: ImageVector get() = Copy
    val Forest: ImageVector get() = Trees
    val FormatAlignCenter: ImageVector get() = TextAlignCenter
    val FormatAlignJustify: ImageVector get() = TextAlignJustify
    val FormatAlignLeft: ImageVector get() = TextAlignStart
    val FormatAlignRight: ImageVector get() = TextAlignEnd
    val FormatBold: ImageVector get() = Type
    val FormatClear: ImageVector get() = RemoveFormatting
    val FormatColorFill: ImageVector get() = PaintBucket
    val FormatColorReset: ImageVector get() = PaintBucket
    val FormatColorText: ImageVector get() = Baseline
    val FormatItalic: ImageVector get() = Italic
    val FormatListBulleted: ImageVector get() = List
    val FormatListNumbered: ImageVector get() = ListOrdered
    val FormatPaint: ImageVector get() = PaintRoller
    val FormatQuote: ImageVector get() = Quote
    val FormatSize: ImageVector get() = ALargeSmall
    val FormatStrikethrough: ImageVector get() = Strikethrough
    val Forward10: ImageVector get() = Forward
    val Fullscreen: ImageVector get() = Maximize
    val Gradient: ImageVector get() = Blend
    val GridView: ImageVector get() = LayoutGrid
    val Group: ImageVector get() = Users
    val Groups: ImageVector get() = Users
    val Hd: ImageVector get() = MonitorPlay
    val HealthAndSafety: ImageVector get() = ShieldCheck
    val Help: ImageVector get() = CircleQuestionMark
    val HighQuality: ImageVector get() = MonitorPlay
    val History: ImageVector get() = ClockArrowUp
    val Home: ImageVector get() = House
    val HourglassBottom: ImageVector get() = Hourglass
    val HourglassEmpty: ImageVector get() = Hourglass
    val Hub: ImageVector get() = Network
    val ImageNotSupported: ImageVector get() = ImageOff
    val Key: ImageVector get() = KeyRound
    val KeyboardAlt: ImageVector get() = Keyboard
    val KeyboardArrowDown: ImageVector get() = ChevronDown
    val KeyboardArrowRight: ImageVector get() = ChevronRight
    val KeyboardArrowUp: ImageVector get() = ChevronUp
    val Label: ImageVector get() = Tag
    val Language: ImageVector get() = Languages
    val Launch: ImageVector get() = ExternalLink
    val LibraryAdd: ImageVector get() = SquarePlus
    val LightMode: ImageVector get() = Sun
    val LinkOff: ImageVector get() = Unlink
    val ListAlt: ImageVector get() = ListChecks
    val Layers: ImageVector get() = Copy
    val LocalFlorist: ImageVector get() = Flower
    val LocationOn: ImageVector get() = MapPin
    val Login: ImageVector get() = LogIn
    val ManageSearch: ImageVector get() = Search
    val Memory: ImageVector get() = Cpu
    val MenuBook: ImageVector get() = BookOpen
    val ModeNight: ImageVector get() = MoonStar
    val MoreTime: ImageVector get() = Timer
    val MoreVert: ImageVector get() = EllipsisVertical
    val Movie: ImageVector get() = Film
    val MusicNote: ImageVector get() = AudioLines
    val NavigateBefore: ImageVector get() = ChevronLeft
    val NavigateNext: ImageVector get() = ChevronRight
    val NetworkCheck: ImageVector get() = Wifi
    val NewReleases: ImageVector get() = Sparkles
    val Notes: ImageVector get() = NotebookText
    val Notifications: ImageVector get() = Bell
    val NotificationsActive: ImageVector get() = BellRing
    val NotificationsNone: ImageVector get() = Bell
    val Numbers: ImageVector get() = Hash
    val OpenInBrowser: ImageVector get() = Globe
    val OpenInNew: ImageVector get() = ExternalLink
    val PauseCircle: ImageVector get() = CirclePause
    val People: ImageVector get() = Users
    val Person: ImageVector get() = User
    val PersonAdd: ImageVector get() = UserPlus
    val PhoneAndroid: ImageVector get() = Smartphone
    val Photo: ImageVector get() = Image
    val PhotoLibrary: ImageVector get() = Images
    val PictureInPictureAlt: ImageVector get() = PictureInPicture
    val PlayArrow: ImageVector get() = Play
    val PlayCircle: ImageVector get() = CirclePlay
    val PlaylistAdd: ImageVector get() = ListPlus
    val PlaylistAddCheck: ImageVector get() = ListChecks
    val PlaylistPlay: ImageVector get() = ListVideo
    val Preview: ImageVector get() = Eye
    val PriorityHigh: ImageVector get() = SignalHigh
    val Public: ImageVector get() = Globe
    val QuestionMark: ImageVector get() = CircleQuestionMark
    val QueuePlayNext: ImageVector get() = ListVideo
    val RecordVoiceOver: ImageVector get() = Mic
    val Refresh: ImageVector get() = RefreshCw
    val Remove: ImageVector get() = Minus
    val RemoveCircle: ImageVector get() = CircleMinus
    val RemoveRedEye: ImageVector get() = Eye
    val RepeatOne: ImageVector get() = Repeat1
    val Replay: ImageVector get() = RotateCcw
    val RestartAlt: ImageVector get() = RotateCcw
    val Restore: ImageVector get() = RotateCcw
    val RssFeed: ImageVector get() = Rss
    val Schedule: ImageVector get() = Clock
    val ScreenRotation: ImageVector get() = RotateCw
    val SdCard: ImageVector get() = HardDrive
    val SearchOff: ImageVector get() = SearchX
    val Security: ImageVector get() = Shield
    val SelectAll: ImageVector get() = SquareDashed
    val Sell: ImageVector get() = Tag
    val SettingsApplications: ImageVector get() = Settings
    val SettingsEthernet: ImageVector get() = Network
    val SettingsInputComponent: ImageVector get() = Cable
    val SettingsInputHdmi: ImageVector get() = Cable
    val SettingsOverscan: ImageVector get() = Maximize
    val Share: ImageVector get() = Share2
    val SignalCellularAlt: ImageVector get() = Signal
    val SkipNext: ImageVector get() = SkipForward
    val SkipPrevious: ImageVector get() = SkipBack
    val Slideshow: ImageVector get() = Presentation
    val SlowMotionVideo: ImageVector get() = Timer
    val SmartButton: ImageVector get() = Sparkles
    val Sort: ImageVector get() = ArrowUpDown
    val Speed: ImageVector get() = Gauge
    val Star: ImageVector get() = StarSolid
    val StarBorder: ImageVector get() = StarStroke
    val Stop: ImageVector get() = Square
    val StopCircle: ImageVector get() = CircleStop
    val Storage: ImageVector get() = Database
    val Subtitles: ImageVector get() = Captions
    val SwapHoriz: ImageVector get() = ArrowLeftRight
    val Tab: ImageVector get() = Copy
    val Swipe: ImageVector get() = Hand
    val SwipeRight: ImageVector get() = Hand
    val Sync: ImageVector get() = RefreshCw
    val SystemUpdate: ImageVector get() = Download
    val SystemUpdateAlt: ImageVector get() = Download
    val TextFields: ImageVector get() = Type
    val ThumbDown: ImageVector get() = ThumbsDown
    val ThumbUp: ImageVector get() = ThumbsUp
    val Title: ImageVector get() = Heading1
    val TouchApp: ImageVector get() = Pointer
    val Translate: ImageVector get() = Languages
    val TravelExplore: ImageVector get() = Compass
    val Tune: ImageVector get() = SlidersHorizontal
    val Verified: ImageVector get() = ShieldCheck
    val VideoLibrary: ImageVector get() = Library
    val VideoSettings: ImageVector get() = Settings
    val ViewCarousel: ImageVector get() = GalleryHorizontal
    val ViewList: ImageVector get() = List
    val ViewQuilt: ImageVector get() = LayoutGrid
    val ViewStream: ImageVector get() = Rows3
    val ViewWeek: ImageVector get() = Columns3
    val Visibility: ImageVector get() = Eye
    val VisibilityOff: ImageVector get() = EyeOff
    val VolumeDown: ImageVector get() = Volume1
    val VolumeMute: ImageVector get() = VolumeX
    val VolumeOff: ImageVector get() = VolumeX
    val VolumeUp: ImageVector get() = Volume2
    val VpnKey: ImageVector get() = KeyRound
    val Warning: ImageVector get() = TriangleAlert
    val WarningAmber: ImageVector get() = TriangleAlert
    val WbSunny: ImageVector get() = Sun
    val Web: ImageVector get() = Globe
    val HdrOn: ImageVector get() = SunMedium
    val HdrOff: ImageVector get() = SunDim
    val ExitToApp: ImageVector get() = LogIn
    val HelpOutline: ImageVector get() = Help

    val Rounded: KitsugiIcons get() = this
    val Default: KitsugiIcons get() = this
    val Filled: KitsugiIcons get() = this
    val Outlined: KitsugiIcons get() = this
    object AutoMirrored {
        val Rounded: KitsugiIcons get() = KitsugiIcons
        val Default: KitsugiIcons get() = KitsugiIcons
        val Filled: KitsugiIcons get() = KitsugiIcons
        val Outlined: KitsugiIcons get() = KitsugiIcons
        val MenuBook: ImageVector get() = KitsugiIcons.MenuBook
        val ArrowBack: ImageVector get() = KitsugiIcons.ArrowBack
        val ArrowForward: ImageVector get() = KitsugiIcons.ArrowForward
        val OpenInNew: ImageVector get() = KitsugiIcons.OpenInNew
        val ExitToApp: ImageVector get() = KitsugiIcons.ExitToApp
        val HelpOutline: ImageVector get() = KitsugiIcons.HelpOutline
        val Help: ImageVector get() = KitsugiIcons.Help
        val List: ImageVector get() = KitsugiIcons.List
        val ViewList: ImageVector get() = KitsugiIcons.ViewList
        val VolumeUp: ImageVector get() = KitsugiIcons.VolumeUp
        val VolumeDown: ImageVector get() = KitsugiIcons.VolumeDown
        val VolumeMute: ImageVector get() = KitsugiIcons.VolumeMute
        val VolumeOff: ImageVector get() = KitsugiIcons.VolumeOff
    }
}
