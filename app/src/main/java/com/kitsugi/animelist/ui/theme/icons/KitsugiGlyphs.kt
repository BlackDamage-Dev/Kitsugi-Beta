package com.kitsugi.animelist.ui.theme.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Kitsugi Glyph System — uygulamanın kendi, elle çizilmiş ikon seti.
 *
 * Neden var: Material ikonları her uygulamada aynı görünür; bu yüzden Keşfet,
 * Listem, Arama, ayarlar satırları, açılır-kapanır başlıklar, oynatıcı
 * kontrolleri ve tüm butonlar birbirine benziyordu. Burada her butonun/işlevin
 * **kendi silueti** var: aynı işi yapan iki alan aynı glyph'i kullanır, farklı
 * işlevler için ayrı simgeler geliştirilir.
 *
 * Tasarım kuralları:
 *  - 24x24 grid, 2.4–21.6 güvenli alan
 *  - 1.75 birim çizgi, yuvarlak uç ve birleşim
 *  - dolu alanlar yalnızca vurgu ögesi (oynatma üçgeni, nokta, yıldız)
 *  - hiçbir glyph Material'daki muadilinin geometrisini kopyalamaz
 *
 * Üretici: tools/kitsugi_glyphs/generate_kitsugi_glyphs.py
 * Önizleme: gorseller/kitsugi_ikon_seti.png
 *
 * Kullanım:
 *   Icon(imageVector = KitsugiGlyphs.Search, contentDescription = null)
 */
object KitsugiGlyphs {

    private const val STROKE = 1.75f

    private fun build(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = "kitsugi_$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply(block).build()

    /** Monoline çizgi katmanı. */
    private fun ImageVector.Builder.stroke(d: String) {
        addPath(
            pathData = addPathNodes(d),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }

    /** Dolu vurgu katmanı. */
    private fun ImageVector.Builder.solid(d: String) {
        addPath(
            pathData = addPathNodes(d),
            fill = SolidColor(Color.Black)
        )
    }

    val Explore: ImageVector = build("explore") {
            stroke("M20.6,12 A8.6,8.6 0 1 1 3.4,12 A8.6,8.6 0 1 1 20.6,12 Z")
            stroke("M12,5.9 L14,10 L18.1,12 L14,14 L12,18.1 L10,14 L5.9,12 L10,10 Z")
    }

    val MyList: ImageVector = build("myList") {
            stroke("M6.4,5.4 H6.6 A1.4,1.4 0 0 1 8,6.8 V18 A1.4,1.4 0 0 1 6.6,19.4 H6.4 A1.4,1.4 0 0 1 5,18 V6.8 A1.4,1.4 0 0 1 6.4,5.4 Z")
            stroke("M11,7.6 H11.2 A1.4,1.4 0 0 1 12.6,9 V18 A1.4,1.4 0 0 1 11.2,19.4 H11 A1.4,1.4 0 0 1 9.6,18 V9 A1.4,1.4 0 0 1 11,7.6 Z")
            stroke("M15.6,4.6 H15.8 A1.4,1.4 0 0 1 17.2,6 V18 A1.4,1.4 0 0 1 15.8,19.4 H15.6 A1.4,1.4 0 0 1 14.2,18 V6 A1.4,1.4 0 0 1 15.6,4.6 Z")
            stroke("M4.2,20.9 H19.8")
    }

    val Search: ImageVector = build("search") {
            stroke("M16.6,10.4 A6.2,6.2 0 1 1 4.2,10.4 A6.2,6.2 0 1 1 16.6,10.4 Z")
            stroke("M15.1,15.1 L20,20")
            stroke("M7.9,9.6 A3.4,3.4 0 0 1 9.6,7.9")
    }

    val Profile: ImageVector = build("profile") {
            stroke("M15.6,8.4 A3.6,3.6 0 1 1 8.4,8.4 A3.6,3.6 0 1 1 15.6,8.4 Z")
            stroke("M4.9,19.9 C5.6,15.6 8.5,13.6 12,13.6 C15.5,13.6 18.4,15.6 19.1,19.9")
            stroke("M12,13.7 V16.3")
    }

    val Settings: ImageVector = build("settings") {
            stroke("M12,3.4 L19.4,7.7 V16.3 L12,20.6 L4.6,16.3 V7.7 Z")
            stroke("M15.5,12 A3.5,3.5 0 1 1 8.5,12 A3.5,3.5 0 1 1 15.5,12 Z")
    }

    val Account: ImageVector = build("account") {
            stroke("M6.2,5.6 H17.8 A2.8,2.8 0 0 1 20.6,8.4 V15.6 A2.8,2.8 0 0 1 17.8,18.4 H6.2 A2.8,2.8 0 0 1 3.4,15.6 V8.4 A2.8,2.8 0 0 1 6.2,5.6 Z")
            stroke("M10.7,10.2 A1.9,1.9 0 1 1 6.9,10.2 A1.9,1.9 0 1 1 10.7,10.2 Z")
            stroke("M6.0,15.6 C6.4,14.0 7.4,13.2 8.8,13.2 C10.2,13.2 11.2,14.0 11.6,15.6")
            stroke("M13.8,10.2 H17.6")
            stroke("M13.8,13.6 H17.6")
    }

    val Person: ImageVector = build("person") {
            stroke("M13.6,8.2 A3.4,3.4 0 1 1 6.8,8.2 A3.4,3.4 0 1 1 13.6,8.2 Z")
            stroke("M3.4,19.9 C4.0,15.6 6.8,13.5 10.2,13.5 C13.6,13.5 16.4,15.6 17.0,19.9")
            solid("M19.2,3.7 L20.01,5.79 L22.1,6.6 L20.01,7.41 L19.2,9.5 L18.39,7.41 L16.3,6.6 L18.39,5.79 Z")
    }

    val Palette: ImageVector = build("palette") {
            stroke("M12,3.6 C6.7,3.6 3.6,7.4 3.6,11.6 C3.6,16.4 7.3,19.8 11.6,19.8 C13.6,19.8 14.4,18.5 13.7,17.2 C13.0,15.9 13.9,14.5 15.3,14.5 H17.4 C19.4,14.5 20.4,13.1 20.4,11.6 C20.4,7.2 16.9,3.6 12,3.6 Z")
            solid("M9.25,8.6 A1.05,1.05 0 1 1 7.15,8.6 A1.05,1.05 0 1 1 9.25,8.6 Z")
            solid("M13.05,7.2 A1.05,1.05 0 1 1 10.95,7.2 A1.05,1.05 0 1 1 13.05,7.2 Z")
            solid("M16.65,9.4 A1.05,1.05 0 1 1 14.55,9.4 A1.05,1.05 0 1 1 16.65,9.4 Z")
    }

    val Player: ImageVector = build("player") {
            stroke("M20.6,12 A8.6,8.6 0 1 1 3.4,12 A8.6,8.6 0 1 1 20.6,12 Z")
            solid("M10.2,8.2 L16.6,12 L10.2,15.8 Z")
    }

    val Plugin: ImageVector = build("plugin") {
            stroke("M9.2,3.4 V7.6")
            stroke("M14.8,3.4 V7.6")
            stroke("M9.2,7.6 H14.8 A2.8,2.8 0 0 1 17.6,10.4 V11.6 A2.8,2.8 0 0 1 14.8,14.4 H9.2 A2.8,2.8 0 0 1 6.4,11.6 V10.4 A2.8,2.8 0 0 1 9.2,7.6 Z")
            stroke("M12,14.4 V18.2")
            stroke("M9.3,20.6 C9.3,19.4 10.4,18.2 12,18.2 C13.6,18.2 14.7,19.4 14.7,20.6")
    }

    val Integrations: ImageVector = build("integrations") {
            stroke("M14.5,12 A2.5,2.5 0 1 1 9.5,12 A2.5,2.5 0 1 1 14.5,12 Z")
            stroke("M12,9.5 V5.4")
            stroke("M14.5,12 H18.6")
            stroke("M12,14.5 V18.6")
            stroke("M9.5,12 H5.4")
            stroke("M13.5,3.9 A1.5,1.5 0 1 1 10.5,3.9 A1.5,1.5 0 1 1 13.5,3.9 Z")
            stroke("M21.6,12 A1.5,1.5 0 1 1 18.6,12 A1.5,1.5 0 1 1 21.6,12 Z")
            stroke("M13.5,20.1 A1.5,1.5 0 1 1 10.5,20.1 A1.5,1.5 0 1 1 13.5,20.1 Z")
            stroke("M5.4,12 A1.5,1.5 0 1 1 2.4,12 A1.5,1.5 0 1 1 5.4,12 Z")
    }

    val Storage: ImageVector = build("storage") {
            stroke("M19.4,7.2 A7.4,2.7 0 1 1 4.6,7.2 A7.4,2.7 0 1 1 19.4,7.2 Z")
            stroke("M4.6,7.2 V16.8")
            stroke("M19.4,7.2 V16.8")
            stroke("M4.6,16.8 A7.4,2.7 0 0 0 19.4,16.8")
            stroke("M4.6,12 A7.4,2.7 0 0 0 19.4,12")
    }

    val Cleaning: ImageVector = build("cleaning") {
            solid("M9.2,3.8 L10.4,8 L14.6,9.2 L10.4,10.4 L9.2,14.6 L8,10.4 L3.8,9.2 L8,8 Z")
            stroke("M13.2,13.2 L19.8,19.8")
            solid("M17.6,3 L18.24,4.76 L20,5.4 L18.24,6.04 L17.6,7.8 L16.96,6.04 L15.2,5.4 L16.96,4.76 Z")
    }

    val Tune: ImageVector = build("tune") {
            stroke("M4.2,7.6 H12.6")
            stroke("M17.4,7.6 H19.8")
            stroke("M17.3,7.6 A2.3,2.3 0 1 1 12.7,7.6 A2.3,2.3 0 1 1 17.3,7.6 Z")
            stroke("M4.2,16.4 H6.4")
            stroke("M11.2,16.4 H19.8")
            stroke("M11.1,16.4 A2.3,2.3 0 1 1 6.5,16.4 A2.3,2.3 0 1 1 11.1,16.4 Z")
    }

    val Download: ImageVector = build("download") {
            stroke("M12,4.2 V14.4")
            stroke("M8.0,10.8 L12,14.8 L16.0,10.8")
            stroke("M4.6,16.4 V18.0 A2.0,2.0 0 0 0 6.6,20.0 H17.4 A2.0,2.0 0 0 0 19.4,18.0 V16.4")
    }

    val History: ImageVector = build("history") {
            stroke("M15.71,5.02 A7.9,7.9 0 1 1 9.56,4.49")
            stroke("M18.15,4.5 L15.71,5.02 L16.65,7.34")
            stroke("M12,7.9 V12.3 L15.7,14.2")
    }

    val Clock: ImageVector = build("clock") {
            stroke("M20.4,12 A8.4,8.4 0 1 1 3.6,12 A8.4,8.4 0 1 1 20.4,12 Z")
            stroke("M12,6.9 V12.4 L16.2,14.4")
    }

    val Info: ImageVector = build("info") {
            stroke("M9.8,3.6 H14.2 A6.2,6.2 0 0 1 20.4,9.8 V14.2 A6.2,6.2 0 0 1 14.2,20.4 H9.8 A6.2,6.2 0 0 1 3.6,14.2 V9.8 A6.2,6.2 0 0 1 9.8,3.6 Z")
            solid("M13.15,8.4 A1.15,1.15 0 1 1 10.85,8.4 A1.15,1.15 0 1 1 13.15,8.4 Z")
            stroke("M12,11.5 V16.5")
    }

    val Feedback: ImageVector = build("feedback") {
            stroke("M7.8,4.6 H16.2 A3.4,3.4 0 0 1 19.6,8 V12.8 A3.4,3.4 0 0 1 16.2,16.2 H7.8 A3.4,3.4 0 0 1 4.4,12.8 V8 A3.4,3.4 0 0 1 7.8,4.6 Z")
            stroke("M8.6,16.2 V20.2 L12.8,16.2")
            solid("M9.95,10.4 A0.95,0.95 0 1 1 8.05,10.4 A0.95,0.95 0 1 1 9.95,10.4 Z")
            solid("M12.95,10.4 A0.95,0.95 0 1 1 11.05,10.4 A0.95,0.95 0 1 1 12.95,10.4 Z")
            solid("M15.95,10.4 A0.95,0.95 0 1 1 14.05,10.4 A0.95,0.95 0 1 1 15.95,10.4 Z")
    }

    val Bug: ImageVector = build("bug") {
            stroke("M16.4,13.4 A4.4,5.6 0 1 1 7.6,13.4 A4.4,5.6 0 1 1 16.4,13.4 Z")
            stroke("M8.7,9.4 A3.3,3.3 0 0 1 15.3,9.4")
            stroke("M10.4,6.6 L8.6,4.4")
            stroke("M13.6,6.6 L15.4,4.4")
            stroke("M7.6,11.8 H3.6")
            stroke("M16.4,11.8 H20.4")
            stroke("M7.9,15.2 H4.4")
            stroke("M16.1,15.2 H19.6")
            stroke("M8.9,18.4 L6.4,20.4")
            stroke("M15.1,18.4 L17.6,20.4")
    }

    val Bell: ImageVector = build("bell") {
            stroke("M6.6,17.4 H17.4 L15.9,15.2 V10.6 C15.9,7.2 14.1,5.0 12,5.0 C9.9,5.0 8.1,7.2 8.1,10.6 V15.2 Z")
            stroke("M10.2,19.9 A2.0,2.0 0 0 0 13.8,19.9")
            stroke("M12,3.4 V5.0")
    }

    val Star: ImageVector = build("star") {
            stroke("M12,4 L14.17,9.41 L19.99,9.8 L15.52,13.54 L16.94,19.2 L12,16.1 L7.06,19.2 L8.48,13.54 L4.01,9.8 L9.83,9.41 Z")
    }

    val StarFilled: ImageVector = build("starFilled") {
            solid("M12,4 L14.17,9.41 L19.99,9.8 L15.52,13.54 L16.94,19.2 L12,16.1 L7.06,19.2 L8.48,13.54 L4.01,9.8 L9.83,9.41 Z")
    }

    val Heart: ImageVector = build("heart") {
            solid("M12,19.8 C6.2,15.6 3.9,12.4 3.9,9.6 C3.9,7.1 5.8,5.4 8.0,5.4 C9.7,5.4 11.2,6.4 12,7.9 C12.8,6.4 14.3,5.4 16.0,5.4 C18.2,5.4 20.1,7.1 20.1,9.6 C20.1,12.4 17.8,15.6 12,19.8 Z")
    }

    val HeartOutline: ImageVector = build("heartOutline") {
            stroke("M12,19.8 C6.2,15.6 3.9,12.4 3.9,9.6 C3.9,7.1 5.8,5.4 8.0,5.4 C9.7,5.4 11.2,6.4 12,7.9 C12.8,6.4 14.3,5.4 16.0,5.4 C18.2,5.4 20.1,7.1 20.1,9.6 C20.1,12.4 17.8,15.6 12,19.8 Z")
    }

    val Bookmark: ImageVector = build("bookmark") {
            stroke("M6.9,3.8 H17.1 V20.4 L12,16.8 L6.9,20.4 Z")
    }

    val Check: ImageVector = build("check") {
            stroke("M4.8,12.4 L9.6,17.2 L19.2,6.8")
    }

    val CheckCircle: ImageVector = build("checkCircle") {
            stroke("M20.6,12 A8.6,8.6 0 1 1 3.4,12 A8.6,8.6 0 1 1 20.6,12 Z")
            stroke("M7.8,12.2 L10.8,15.2 L16.4,8.8")
    }

    val Film: ImageVector = build("film") {
            stroke("M6.2,4.8 H17.8 A2.8,2.8 0 0 1 20.6,7.6 V16.4 A2.8,2.8 0 0 1 17.8,19.2 H6.2 A2.8,2.8 0 0 1 3.4,16.4 V7.6 A2.8,2.8 0 0 1 6.2,4.8 Z")
            stroke("M8.0,4.8 V19.2")
            stroke("M16.0,4.8 V19.2")
            solid("M6.55,8 A0.85,0.85 0 1 1 4.85,8 A0.85,0.85 0 1 1 6.55,8 Z")
            solid("M6.55,12 A0.85,0.85 0 1 1 4.85,12 A0.85,0.85 0 1 1 6.55,12 Z")
            solid("M6.55,16 A0.85,0.85 0 1 1 4.85,16 A0.85,0.85 0 1 1 6.55,16 Z")
            solid("M19.15,8 A0.85,0.85 0 1 1 17.45,8 A0.85,0.85 0 1 1 19.15,8 Z")
            solid("M19.15,12 A0.85,0.85 0 1 1 17.45,12 A0.85,0.85 0 1 1 19.15,12 Z")
            solid("M19.15,16 A0.85,0.85 0 1 1 17.45,16 A0.85,0.85 0 1 1 19.15,16 Z")
    }

    val Tv: ImageVector = build("tv") {
            stroke("M6,6.8 H18 A2.8,2.8 0 0 1 20.8,9.6 V16.2 A2.8,2.8 0 0 1 18,19 H6 A2.8,2.8 0 0 1 3.2,16.2 V9.6 A2.8,2.8 0 0 1 6,6.8 Z")
            stroke("M8.4,3.4 L12,6.8 L15.6,3.4")
            solid("M12.9,21.2 A0.9,0.9 0 1 1 11.1,21.2 A0.9,0.9 0 1 1 12.9,21.2 Z")
    }

    val Comment: ImageVector = build("comment") {
            stroke("M4.4,6.2 A2.8,2.8 0 0 1 7.2,3.4 H16.8 A2.8,2.8 0 0 1 19.6,6.2 V13.6 A2.8,2.8 0 0 1 16.8,16.4 H11.4 L7.2,20.0 V16.4 A2.8,2.8 0 0 1 4.4,13.6 Z")
            stroke("M8.4,9.9 H15.6")
    }

    val Tag: ImageVector = build("tag") {
            stroke("M11.0,3.6 H18.4 A1.9,1.9 0 0 1 20.3,5.5 V12.9 L12.2,21.0 A1.8,1.8 0 0 1 9.6,21.0 L3.1,14.5 A1.8,1.8 0 0 1 3.1,11.9 Z")
            solid("M17.75,7.4 A1.25,1.25 0 1 1 15.25,7.4 A1.25,1.25 0 1 1 17.75,7.4 Z")
    }

    val Share: ImageVector = build("share") {
            stroke("M19.9,6.2 A2.5,2.5 0 1 1 14.9,6.2 A2.5,2.5 0 1 1 19.9,6.2 Z")
            stroke("M9.1,12 A2.5,2.5 0 1 1 4.1,12 A2.5,2.5 0 1 1 9.1,12 Z")
            stroke("M19.9,17.8 A2.5,2.5 0 1 1 14.9,17.8 A2.5,2.5 0 1 1 19.9,17.8 Z")
            stroke("M8.8,10.9 L15.2,7.3")
            stroke("M8.8,13.1 L15.2,16.7")
    }

    val Copy: ImageVector = build("copy") {
            stroke("M8.6,3.6 H17.4 A2.6,2.6 0 0 1 20,6.2 V15 A2.6,2.6 0 0 1 17.4,17.6 H8.6 A2.6,2.6 0 0 1 6,15 V6.2 A2.6,2.6 0 0 1 8.6,3.6 Z")
            stroke("M4.0,9.2 V18.2 A2.4,2.4 0 0 0 6.4,20.6 H15.4")
    }

    val Link: ImageVector = build("link") {
            stroke("M7.4,9 H16.6 A3,3 0 0 1 19.6,12 V12 A3,3 0 0 1 16.6,15 H7.4 A3,3 0 0 1 4.4,12 V12 A3,3 0 0 1 7.4,9 Z")
            stroke("M12,9.0 V15.0")
    }

    val LinkOff: ImageVector = build("linkOff") {
            stroke("M7.4,9 H16.6 A3,3 0 0 1 19.6,12 V12 A3,3 0 0 1 16.6,15 H7.4 A3,3 0 0 1 4.4,12 V12 A3,3 0 0 1 7.4,9 Z")
            stroke("M12,9.0 V15.0")
            stroke("M4.4,19.6 L19.6,4.4")
    }

    val External: ImageVector = build("external") {
            stroke("M18.6,13.4 V18.2 A2.4,2.4 0 0 1 16.2,20.6 H5.8 A2.4,2.4 0 0 1 3.4,18.2 V7.8 A2.4,2.4 0 0 1 5.8,5.4 H10.8")
            stroke("M13.8,3.4 H20.6 V10.2")
            stroke("M20.2,3.8 L11.8,12.2")
    }

    val Translate: ImageVector = build("translate") {
            stroke("M4.4,17.8 L8.9,6.4 L13.4,17.8")
            stroke("M6.2,13.8 H11.6")
            stroke("M14.8,12.0 H20.4")
            stroke("M17.8,9 L20.8,12 L17.8,15")
    }

    val Subtitles: ImageVector = build("subtitles") {
            stroke("M6.2,5.6 H17.8 A2.8,2.8 0 0 1 20.6,8.4 V15.6 A2.8,2.8 0 0 1 17.8,18.4 H6.2 A2.8,2.8 0 0 1 3.4,15.6 V8.4 A2.8,2.8 0 0 1 6.2,5.6 Z")
            stroke("M6.6,10.6 H11.0")
            stroke("M13.0,10.6 H17.4")
            stroke("M6.6,14.2 H14.2")
    }

    val Delete: ImageVector = build("delete") {
            stroke("M4.6,7.0 H19.4")
            stroke("M9.4,7.0 V5.0 A1.6,1.6 0 0 1 11.0,3.4 H13.0 A1.6,1.6 0 0 1 14.6,5.0 V7.0")
            stroke("M6.6,7.0 L7.6,19.0 A2.0,2.0 0 0 0 9.6,20.8 H14.4 A2.0,2.0 0 0 0 16.4,19.0 L17.4,7.0")
            stroke("M10.4,11.0 V16.8")
            stroke("M13.6,11.0 V16.8")
    }

    val Edit: ImageVector = build("edit") {
            stroke("M4.4,19.6 L5.2,15.6 L15.4,5.4 A2.3,2.3 0 0 1 18.6,8.6 L8.4,18.8 Z")
            stroke("M13.8,7.0 L17.0,10.2")
    }

    val Filter: ImageVector = build("filter") {
            stroke("M4.2,5.6 H19.8 L13.8,12.9 V18.6 L10.2,20.6 V12.9 Z")
    }

    val Chart: ImageVector = build("chart") {
            stroke("M4.2,20.0 H20.2")
            stroke("M7.4,20.0 V13.4")
            stroke("M12.0,20.0 V8.2")
            stroke("M16.6,20.0 V11.0")
            solid("M20.65,5.6 A1.25,1.25 0 1 1 18.15,5.6 A1.25,1.25 0 1 1 20.65,5.6 Z")
    }

    val Calendar: ImageVector = build("calendar") {
            stroke("M6.6,5.4 H17.4 A3,3 0 0 1 20.4,8.4 V17.4 A3,3 0 0 1 17.4,20.4 H6.6 A3,3 0 0 1 3.6,17.4 V8.4 A3,3 0 0 1 6.6,5.4 Z")
            stroke("M8.0,3.2 V7.4")
            stroke("M16.0,3.2 V7.4")
            stroke("M3.6,10.2 H20.4")
            solid("M9.2,14.6 A1,1 0 1 1 7.2,14.6 A1,1 0 1 1 9.2,14.6 Z")
            solid("M13,14.6 A1,1 0 1 1 11,14.6 A1,1 0 1 1 13,14.6 Z")
            solid("M16.8,14.6 A1,1 0 1 1 14.8,14.6 A1,1 0 1 1 16.8,14.6 Z")
    }

    val Book: ImageVector = build("book") {
            stroke("M12,6.8 C9.8,5.0 6.8,4.6 4.2,5.6 V18.4 C7.0,17.4 9.8,17.9 12,19.7 C14.2,17.9 17.0,17.4 19.8,18.4 V5.6 C17.2,4.6 14.2,5.0 12,6.8 Z")
            stroke("M12,6.8 V19.7")
    }

    val Image: ImageVector = build("image") {
            stroke("M6.4,4.8 H17.6 A3,3 0 0 1 20.6,7.8 V16.2 A3,3 0 0 1 17.6,19.2 H6.4 A3,3 0 0 1 3.4,16.2 V7.8 A3,3 0 0 1 6.4,4.8 Z")
            stroke("M3.6,15.8 L8.6,10.8 L12.6,14.8 L15.4,12.0 L20.4,16.8")
            solid("M9.7,9 A1.3,1.3 0 1 1 7.1,9 A1.3,1.3 0 1 1 9.7,9 Z")
    }

    val Folder: ImageVector = build("folder") {
            stroke("M3.6,7.4 A2.6,2.6 0 0 1 6.2,4.8 H9.6 L11.8,7.4 H17.8 A2.6,2.6 0 0 1 20.4,10.0 V17.2 A2.6,2.6 0 0 1 17.8,19.8 H6.2 A2.6,2.6 0 0 1 3.6,17.2 Z")
    }

    val Shield: ImageVector = build("shield") {
            stroke("M12,3.4 L19.6,6.2 V11.8 C19.6,16.4 16.4,19.6 12,20.6 C7.6,19.6 4.4,16.4 4.4,11.8 V6.2 Z")
            stroke("M14.2,11.2 A2.2,2.2 0 1 1 9.8,11.2 A2.2,2.2 0 1 1 14.2,11.2 Z")
            stroke("M12,13.4 V16.2")
    }

    val Globe: ImageVector = build("globe") {
            stroke("M20.6,12 A8.6,8.6 0 1 1 3.4,12 A8.6,8.6 0 1 1 20.6,12 Z")
            stroke("M12,3.4 C8.4,6.2 8.4,17.8 12,20.6 C15.6,17.8 15.6,6.2 12,3.4 Z")
            stroke("M3.8,9.2 H20.2")
            stroke("M3.8,14.8 H20.2")
    }

    val Menu: ImageVector = build("menu") {
            stroke("M4.2,7.0 H19.8")
            stroke("M4.2,12.0 H15.2")
            stroke("M4.2,17.0 H18.0")
    }

    val More: ImageVector = build("more") {
            solid("M13.5,5.2 A1.5,1.5 0 1 1 10.5,5.2 A1.5,1.5 0 1 1 13.5,5.2 Z")
            solid("M13.5,12 A1.5,1.5 0 1 1 10.5,12 A1.5,1.5 0 1 1 13.5,12 Z")
            solid("M13.5,18.8 A1.5,1.5 0 1 1 10.5,18.8 A1.5,1.5 0 1 1 13.5,18.8 Z")
    }

    val Close: ImageVector = build("close") {
            stroke("M6.0,6.0 L18.0,18.0")
            stroke("M18.0,6.0 L6.0,18.0")
    }

    val Back: ImageVector = build("back") {
            stroke("M19.6,12 H4.4")
            stroke("M10.8,5.2 L4.0,12 L10.8,18.8")
    }

    val Forward: ImageVector = build("forward") {
            stroke("M4.4,12 H19.6")
            stroke("M13.2,5.2 L20.0,12 L13.2,18.8")
    }

    val ChevronDown: ImageVector = build("chevronDown") {
            stroke("M6.4,9.4 L12,15.0 L17.6,9.4")
    }

    val ChevronUp: ImageVector = build("chevronUp") {
            stroke("M6.4,14.6 L12,9.0 L17.6,14.6")
    }

    val ChevronRight: ImageVector = build("chevronRight") {
            stroke("M9.4,6.2 L15.0,12 L9.4,17.8")
    }

    val Plus: ImageVector = build("plus") {
            stroke("M12,4.6 V19.4")
            stroke("M4.6,12 H19.4")
    }

    val Eye: ImageVector = build("eye") {
            stroke("M2.6,12 C5.0,7.4 8.4,5.2 12,5.2 C15.6,5.2 19.0,7.4 21.4,12 C19.0,16.6 15.6,18.8 12,18.8 C8.4,18.8 5.0,16.6 2.6,12 Z")
            stroke("M15,12 A3,3 0 1 1 9,12 A3,3 0 1 1 15,12 Z")
    }

    val EyeOff: ImageVector = build("eyeOff") {
            stroke("M2.6,12 C5.0,7.4 8.4,5.2 12,5.2 C15.6,5.2 19.0,7.4 21.4,12 C19.0,16.6 15.6,18.8 12,18.8 C8.4,18.8 5.0,16.6 2.6,12 Z")
            stroke("M15,12 A3,3 0 1 1 9,12 A3,3 0 1 1 15,12 Z")
            stroke("M4.4,20.2 L19.6,4.0")
    }

    val SearchOff: ImageVector = build("searchOff") {
            stroke("M16.6,10.4 A6.2,6.2 0 1 1 4.2,10.4 A6.2,6.2 0 1 1 16.6,10.4 Z")
            stroke("M15.1,15.1 L20,20")
            stroke("M4.8,19.2 L19.2,4.8")
    }

    val Sort: ImageVector = build("sort") {
            stroke("M4.8,7.0 H19.2")
            stroke("M7.2,12.0 H16.8")
            stroke("M9.6,17.0 H14.4")
    }

    val Queue: ImageVector = build("queue") {
            stroke("M4.2,6.4 H19.8")
            stroke("M4.2,10.8 H19.8")
            stroke("M4.2,15.2 H11.4")
            solid("M15.8,14.6 L20.6,17.4 L15.8,20.2 Z")
    }

    val Sparkle: ImageVector = build("sparkle") {
            solid("M10.6,4 L12.01,9.19 L17.2,10.6 L12.01,12.01 L10.6,17.2 L9.19,12.01 L4,10.6 L9.19,9.19 Z")
            solid("M18.4,3.4 L19.04,5.56 L21.2,6.2 L19.04,6.84 L18.4,9 L17.76,6.84 L15.6,6.2 L17.76,5.56 Z")
            solid("M18.2,15.2 L18.69,16.91 L20.4,17.4 L18.69,17.89 L18.2,19.6 L17.71,17.89 L16,17.4 L17.71,16.91 Z")
    }

    val Refresh: ImageVector = build("refresh") {
            stroke("M17.35,6.05 A8,8 0 1 1 4.31,9.79")
            stroke("M5.33,12.08 L4.31,9.79 L2.24,11.19")
    }

    val Sync: ImageVector = build("sync") {
            stroke("M19.06,8.24 A8,8 0 0 1 4.94,15.76")
            stroke("M7.25,16.69 L4.94,15.76 L4.42,18.2")
            stroke("M4.94,15.76 A8,8 0 0 1 19.06,8.24")
            stroke("M16.75,7.31 L19.06,8.24 L19.58,5.8")
    }

    val CloudSync: ImageVector = build("cloudSync") {
            stroke("M7.6,18.6 A3.9,3.9 0 0 1 6.9,10.9 A5.0,5.0 0 0 1 16.5,9.7 A3.9,3.9 0 0 1 17.0,18.6 Z")
            stroke("M12,15.6 V9.8")
            stroke("M9.6,12.2 L12,9.6 L14.4,12.2")
    }

    val Play: ImageVector = build("play") {
            solid("M7.6,4.6 L19.4,12 L7.6,19.4 Z")
    }

    val Pause: ImageVector = build("pause") {
            solid("M7.8,4.6 H8.6 A1.2,1.2 0 0 1 9.8,5.8 V18.2 A1.2,1.2 0 0 1 8.6,19.4 H7.8 A1.2,1.2 0 0 1 6.6,18.2 V5.8 A1.2,1.2 0 0 1 7.8,4.6 Z")
            solid("M15.4,4.6 H16.2 A1.2,1.2 0 0 1 17.4,5.8 V18.2 A1.2,1.2 0 0 1 16.2,19.4 H15.4 A1.2,1.2 0 0 1 14.2,18.2 V5.8 A1.2,1.2 0 0 1 15.4,4.6 Z")
    }

    val PlayCircle: ImageVector = build("playCircle") {
            stroke("M20.6,12 A8.6,8.6 0 1 1 3.4,12 A8.6,8.6 0 1 1 20.6,12 Z")
            solid("M10.2,8.2 L16.6,12 L10.2,15.8 Z")
    }

    val SkipNext: ImageVector = build("skipNext") {
            solid("M6.2,4.8 L14.6,12 L6.2,19.2 Z")
            solid("M17.1,4.8 H17.7 A0.9,0.9 0 0 1 18.6,5.7 V18.3 A0.9,0.9 0 0 1 17.7,19.2 H17.1 A0.9,0.9 0 0 1 16.2,18.3 V5.7 A0.9,0.9 0 0 1 17.1,4.8 Z")
    }

    val Volume: ImageVector = build("volume") {
            stroke("M4.2,9.4 H7.6 L12.2,5.4 V18.6 L7.6,14.6 H4.2 Z")
            stroke("M15.4,8.8 A4.8,4.8 0 0 1 15.4,15.2")
            stroke("M17.8,6.4 A7.8,7.8 0 0 1 17.8,17.6")
    }

    val Fullscreen: ImageVector = build("fullscreen") {
            stroke("M9.4,4.2 H4.2 V9.4")
            stroke("M14.6,4.2 H19.8 V9.4")
            stroke("M19.8,14.6 V19.8 H14.6")
            stroke("M9.4,19.8 H4.2 V14.6")
    }

    val Cast: ImageVector = build("cast") {
            stroke("M20.4,16.2 V6.6 A2.2,2.2 0 0 0 18.2,4.4 H5.8 A2.2,2.2 0 0 0 3.6,6.6 V7.8")
            stroke("M3.6,11.2 A8.6,8.6 0 0 1 12.2,19.8")
            stroke("M3.6,15.2 A4.6,4.6 0 0 1 8.2,19.8")
            solid("M4.85,19.5 A0.95,0.95 0 1 1 2.95,19.5 A0.95,0.95 0 1 1 4.85,19.5 Z")
    }

    val Speed: ImageVector = build("speed") {
            stroke("M3.8,17.4 A8.6,8.6 0 1 1 20.2,17.4")
            stroke("M12,17.4 L16.4,10.4")
            solid("M13.25,17.4 A1.25,1.25 0 1 1 10.75,17.4 A1.25,1.25 0 1 1 13.25,17.4 Z")
    }

    val Pip: ImageVector = build("pip") {
            stroke("M5.6,4.6 H18.4 A2.8,2.8 0 0 1 21.2,7.4 V16.6 A2.8,2.8 0 0 1 18.4,19.4 H5.6 A2.8,2.8 0 0 1 2.8,16.6 V7.4 A2.8,2.8 0 0 1 5.6,4.6 Z")
            stroke("M13.4,11.2 H17.2 A1.8,1.8 0 0 1 19,13 V15.6 A1.8,1.8 0 0 1 17.2,17.4 H13.4 A1.8,1.8 0 0 1 11.6,15.6 V13 A1.8,1.8 0 0 1 13.4,11.2 Z")
    }

    val Warning: ImageVector = build("warning") {
            stroke("M13.0,3.7 A1.2,1.2 0 0 0 11.0,3.7 L2.9,17.8 A1.3,1.3 0 0 0 4.0,19.8 H20.0 A1.3,1.3 0 0 0 21.1,17.8 Z")
            stroke("M12,9.2 V13.6")
            solid("M13.05,16.5 A1.05,1.05 0 1 1 10.95,16.5 A1.05,1.05 0 1 1 13.05,16.5 Z")
    }

    val Error: ImageVector = build("error") {
            stroke("M20.8,12 A8.8,8.8 0 1 1 3.2,12 A8.8,8.8 0 1 1 20.8,12 Z")
            stroke("M12,7.4 V13.0")
            solid("M13.05,16.2 A1.05,1.05 0 1 1 10.95,16.2 A1.05,1.05 0 1 1 13.05,16.2 Z")
    }

    val Grid: ImageVector = build("grid") {
            stroke("M5.7,3.8 H8.7 A1.9,1.9 0 0 1 10.6,5.7 V8.7 A1.9,1.9 0 0 1 8.7,10.6 H5.7 A1.9,1.9 0 0 1 3.8,8.7 V5.7 A1.9,1.9 0 0 1 5.7,3.8 Z")
            stroke("M15.3,3.8 H18.3 A1.9,1.9 0 0 1 20.2,5.7 V8.7 A1.9,1.9 0 0 1 18.3,10.6 H15.3 A1.9,1.9 0 0 1 13.4,8.7 V5.7 A1.9,1.9 0 0 1 15.3,3.8 Z")
            stroke("M5.7,13.4 H8.7 A1.9,1.9 0 0 1 10.6,15.3 V18.3 A1.9,1.9 0 0 1 8.7,20.2 H5.7 A1.9,1.9 0 0 1 3.8,18.3 V15.3 A1.9,1.9 0 0 1 5.7,13.4 Z")
            solid("M15.3,13.4 H18.3 A1.9,1.9 0 0 1 20.2,15.3 V18.3 A1.9,1.9 0 0 1 18.3,20.2 H15.3 A1.9,1.9 0 0 1 13.4,18.3 V15.3 A1.9,1.9 0 0 1 15.3,13.4 Z")
    }

    val Rows: ImageVector = build("rows") {
            solid("M6.05,6.8 A1.15,1.15 0 1 1 3.75,6.8 A1.15,1.15 0 1 1 6.05,6.8 Z")
            solid("M6.05,12 A1.15,1.15 0 1 1 3.75,12 A1.15,1.15 0 1 1 6.05,12 Z")
            solid("M6.05,17.2 A1.15,1.15 0 1 1 3.75,17.2 A1.15,1.15 0 1 1 6.05,17.2 Z")
            stroke("M8.6,6.8 H19.8")
            stroke("M8.6,12.0 H19.8")
            stroke("M8.6,17.2 H15.6")
    }

    val Repeat: ImageVector = build("repeat") {
            stroke("M8.2,7.4 H15.8 A4.6,4.6 0 0 1 20.4,12 V12 A4.6,4.6 0 0 1 15.8,16.6 H8.2 A4.6,4.6 0 0 1 3.6,12 V12 A4.6,4.6 0 0 1 8.2,7.4 Z")
            stroke("M17.48,10.81 L19.4,9.2 L17.48,7.59")
            stroke("M6.52,13.19 L4.6,14.8 L6.52,16.41")
    }

    val Key: ImageVector = build("key") {
            stroke("M11.7,12 A3.7,3.7 0 1 1 4.3,12 A3.7,3.7 0 1 1 11.7,12 Z")
            stroke("M11.7,12.0 H20.2")
            stroke("M16.6,12.0 V15.2")
            stroke("M19.4,12.0 V14.4")
    }

    val Code: ImageVector = build("code") {
            stroke("M9.2,7.2 L3.8,12 L9.2,16.8")
            stroke("M14.8,7.2 L20.2,12 L14.8,16.8")
            stroke("M13.2,5.4 L10.8,18.6")
    }

    val Send: ImageVector = build("send") {
            stroke("M20.6,3.8 L3.6,10.9 L10.4,13.6 L13.1,20.4 Z")
            stroke("M10.4,13.6 L20.6,3.8")
    }

    val People: ImageVector = build("people") {
            stroke("M12.4,8.6 A3,3 0 1 1 6.4,8.6 A3,3 0 1 1 12.4,8.6 Z")
            stroke("M3.4,19.4 C4.0,15.8 6.4,14.0 9.4,14.0 C12.4,14.0 14.8,15.8 15.4,19.4")
            stroke("M19.8,9.8 A2.4,2.4 0 1 1 15,9.8 A2.4,2.4 0 1 1 19.8,9.8 Z")
            stroke("M16.3,14.2 C18.9,14.7 20.4,16.4 20.8,19.4")
    }

    val UpCircle: ImageVector = build("upCircle") {
            stroke("M20.8,12 A8.8,8.8 0 1 1 3.2,12 A8.8,8.8 0 1 1 20.8,12 Z")
            stroke("M12,16.3 V7.9")
            stroke("M8.5,11.4 L12,7.9 L15.5,11.4")
    }

    val ChevronLeft: ImageVector = build("chevronLeft") {
            stroke("M14.6,6.2 L9.0,12 L14.6,17.8")
    }

    val FastForward: ImageVector = build("fastForward") {
            solid("M3.2,5.6 L10.0,12 L3.2,18.4 Z")
            solid("M11.6,5.6 L18.4,12 L11.6,18.4 Z")
            solid("M19.9,5.6 H20.3 A0.7,0.7 0 0 1 21,6.3 V17.7 A0.7,0.7 0 0 1 20.3,18.4 H19.9 A0.7,0.7 0 0 1 19.2,17.7 V6.3 A0.7,0.7 0 0 1 19.9,5.6 Z")
    }

    val FastRewind: ImageVector = build("fastRewind") {
            solid("M20.8,5.6 L14.0,12 L20.8,18.4 Z")
            solid("M12.4,5.6 L5.6,12 L12.4,18.4 Z")
            solid("M3.7,5.6 H4.1 A0.7,0.7 0 0 1 4.8,6.3 V17.7 A0.7,0.7 0 0 1 4.1,18.4 H3.7 A0.7,0.7 0 0 1 3,17.7 V6.3 A0.7,0.7 0 0 1 3.7,5.6 Z")
    }

    val CloudDownload: ImageVector = build("cloudDownload") {
            stroke("M7.6,18.6 A3.9,3.9 0 0 1 6.9,10.9 A5.0,5.0 0 0 1 16.5,9.7 A3.9,3.9 0 0 1 17.0,18.6 Z")
            stroke("M12,9.8 V15.6")
            stroke("M9.6,13.2 L12,15.8 L14.4,13.2")
    }

    val Bolt: ImageVector = build("bolt") {
            stroke("M13.8,2.9 L5.6,13.2 H10.8 L9.6,21.1 L18.2,10.6 H12.8 Z")
    }

    val Hourglass: ImageVector = build("hourglass") {
            stroke("M6.4,3.6 H17.6")
            stroke("M6.4,20.4 H17.6")
            stroke("M8.2,3.6 V7.2 C8.2,9.6 12,11.2 12,12 C12,12.8 8.2,14.4 8.2,16.8 V20.4")
            stroke("M15.8,3.6 V7.2 C15.8,9.6 12,11.2 12,12 C12,12.8 15.8,14.4 15.8,16.8 V20.4")
    }

    val Swap: ImageVector = build("swap") {
            stroke("M4.4,9.0 H18.2")
            stroke("M16.28,10.61 L18.2,9 L16.28,7.39")
            stroke("M19.6,15.0 H5.8")
            stroke("M7.72,13.39 L5.8,15 L7.72,16.61")
    }

    val RemoveCircle: ImageVector = build("removeCircle") {
            stroke("M20.8,12 A8.8,8.8 0 1 1 3.2,12 A8.8,8.8 0 1 1 20.8,12 Z")
            stroke("M8.2,12.0 H15.8")
    }

    val Reply: ImageVector = build("reply") {
            stroke("M9.6,5.8 L3.8,11.2 L9.6,16.6")
            stroke("M3.8,11.2 H13.4 C16.6,11.2 19.0,13.6 19.0,16.6 C19.0,18.4 18.2,19.8 17.0,20.6")
    }

    val ThumbUp: ImageVector = build("thumbUp") {
            stroke("M7.0,10.4 H4.4 A1.2,1.2 0 0 0 3.2,11.6 V19.2 A1.2,1.2 0 0 0 4.4,20.4 H7.0")
            stroke("M7.0,10.4 L11.0,3.8 C11.5,3.0 12.8,3.4 12.8,4.4 V9.0 H18.2 C19.7,9.0 20.7,10.3 20.3,11.7 L18.8,18.6 C18.6,19.6 17.7,20.4 16.7,20.4 H7.0 Z")
    }

    val VolumeOff: ImageVector = build("volumeOff") {
            stroke("M4,9 H8 L13,5 V19 L8,15 H4 Z M17,9 L21,15 M21,9 L17,15")
    }

    val VolumeLow: ImageVector = build("volumeLow") {
            stroke("M4,9 H8 L13,5 V19 L8,15 H4 Z M17,9 Q20,12 17,15")
    }

    val RepeatOne: ImageVector = build("repeatOne") {
            stroke("M5,7 H19 L16,4 M19,7 L16,10 M19,17 H5 L8,20 M5,17 L8,14 M11,10 L13,9 V15")
    }

    val Unlock: ImageVector = build("unlock") {
            stroke("M8,10 V7 A4,4 0 0 1 8,7.1 M8,7 A4,4 0 0 1 16,7 M6,10 H18 V20 H6 Z M12,14 V16")
    }

    val BellOff: ImageVector = build("bellOff") {
            stroke("M5,17 H19 L17,14 V10 M7,13 V10 A5,5 0 0 1 15,6 M10,20 H14 M3,3 L21,21")
    }

    val FullscreenExit: ImageVector = build("fullscreenExit") {
            stroke("M3,8 H8 V3 M16,3 V8 H21 M21,16 H16 V21 M8,21 V16 H3")
    }

    val SubtitlesOff: ImageVector = build("subtitlesOff") {
            stroke("M4,7 V19 H20 V10 M8,5 H20 V7 M7,11 H9 M7,15 H13 M17,15 H18 M3,3 L21,21")
    }

    val BellChecked: ImageVector = build("bellChecked") {
            stroke("M4,16 H15 L14,13 V8 Q14,3 9,3 Q4,3 4,8 V13 L2,16 H4 M7,20 H10 M15,17 L18,20 L23,13")
    }

    val Seek: ImageVector = build("seek") {
            stroke("M3,12 H21 M7,8 V16 M17,8 V16 M12,4 V20")
    }

    val Random: ImageVector = build("random") {
            stroke("M6,3 L20,6 L18,21 L3,17 Z M8,7 h.01 M15,8 h.01 M10,12 h.01 M7,16 h.01 M15,17 h.01")
    }

    val Network: ImageVector = build("network") {
            stroke("M4,8 Q12,1 20,8 M7,12 Q12,7 17,12 M10,16 Q12,14 14,16 M12,20 h.01")
    }

    val Memory: ImageVector = build("memory") {
            stroke("M7,7 H17 V17 H7 Z M10,10 H14 V14 H10 Z M9,3 V7 M15,3 V7 M9,17 V21 M15,17 V21 M3,9 H7 M3,15 H7 M17,9 H21 M17,15 H21")
    }

    val Notes: ImageVector = build("notes") {
            stroke("M6,3 H16 L20,7 V21 H6 Z M16,3 V7 H20 M9,10 H17 M9,14 H17 M9,18 H14")
    }

    val Touch: ImageVector = build("touch") {
            stroke("M10,12 V5 A2,2 0 0 1 14,5 V11 L18,12 Q21,13 20,16 L18,21 H10 L5,15 Q4,12 7,13 L10,16 M4,5 Q4,2 7,2 M17,2 Q20,2 20,5")
    }

    val QueueChecked: ImageVector = build("queueChecked") {
            stroke("M4,5 H19 M4,10 H15 M4,15 H10 M13,17 L16,20 L21,13")
    }

    val Quality: ImageVector = build("quality") {
            stroke("M4,5 H20 V19 H4 Z M7,9 V15 M11,9 V15 M7,12 H11 M15,9 V15 M15,9 Q21,12 15,15")
    }

    val NumberedList: ImageVector = build("numberedList") {
            stroke("M4,4 V8 M3,12 Q6,10 5,13 L3,16 H6 M10,6 H21 M10,13 H21 M10,20 H21 M3,20 H6")
    }

    val Bold: ImageVector = build("bold") {
            stroke("M7,4 H13 Q21,4 17,11 Q23,19 14,20 H7 Z M7,11 H14")
    }

    val Italic: ImageVector = build("italic") {
            stroke("M11,4 H19 M5,20 H13 M15,4 L9,20")
    }

    val BrokenImage: ImageVector = build("brokenImage") {
            stroke("M4,4 H20 V20 H4 Z M4,15 L9,10 L12,15 L15,12 L20,17 M14,4 L11,8 L14,10")
    }

    val Brightness: ImageVector = build("brightness") {
            stroke("M12,7 A5,5 0 1 1 12,17 A5,5 0 1 1 12,7 M12,2 V4 M12,20 V22 M2,12 H4 M20,12 H22 M5,5 L6,6 M18,18 L19,19 M5,19 L6,18 M18,6 L19,5 M12,7 V17")
    }

    val Aspect: ImageVector = build("aspect") {
            stroke("M3,6 H21 V18 H3 Z M6,9 H10 M6,9 V13 M18,15 H14 M18,15 V11")
    }

    val Zoom: ImageVector = build("zoom") {
            stroke("M10,3 A7,7 0 1 1 10,17 A7,7 0 1 1 10,3 M15,15 L21,21 M7,10 H13 M10,7 V13")
    }

    val Text: ImageVector = build("text") {
            stroke("M4,5 H20 M12,5 V20 M8,20 H16")
    }

    val Swipe: ImageVector = build("swipe") {
            stroke("M3,8 H21 M3,8 L6,5 M21,8 L18,5 M9,19 V14 Q9,12 11,12 Q13,12 13,14 V16 L17,16 V21 H9")
    }

    val Stop: ImageVector = build("stop") {
            stroke("M6,6 H18 V18 H6 Z")
    }

    val StopCircle: ImageVector = build("stopCircle") {
            stroke("M12,3 A9,9 0 1 1 12,21 A9,9 0 1 1 12,3 M8,8 H16 V16 H8 Z")
    }

    val Button: ImageVector = build("button") {
            stroke("M5,7 H19 Q22,7 22,10 V14 Q22,17 19,17 H5 Q2,17 2,14 V10 Q2,7 5,7 M7,12 H12 M17,10 V14")
    }

    val Rotation: ImageVector = build("rotation") {
            stroke("M8,3 H16 V21 H8 Z M3,10 Q1,4 6,2 M2,10 L5,9 M21,14 Q23,20 18,22 M22,14 L19,15")
    }

    val Save: ImageVector = build("save") {
            stroke("M4,3 H17 L21,7 V21 H4 Z M8,3 V9 H16 V3 M8,21 V14 H17 V21")
    }

    val Feed: ImageVector = build("feed") {
            stroke("M4,4 Q20,4 20,20 M4,10 Q14,10 14,20 M5,17 A2,2 0 1 1 5,21 A2,2 0 1 1 5,17")
    }

    val Replay: ImageVector = build("replay") {
            stroke("M5,8 A8,8 0 1 1 4,16 M5,3 V8 H10 M11,9 L15,12 L11,15 Z")
    }

    val Minus: ImageVector = build("minus") {
            stroke("M5,12 H19")
    }

    val Phone: ImageVector = build("phone") {
            stroke("M7,2 H17 Q19,2 19,4 V20 Q19,22 17,22 H7 Q5,22 5,20 V4 Q5,2 7,2 M10,5 H14 M11,19 H13")
    }

    val PauseCircle: ImageVector = build("pauseCircle") {
            stroke("M12,3 A9,9 0 1 1 12,21 A9,9 0 1 1 12,3 M9,8 V16 M15,8 V16")
    }

    val Music: ImageVector = build("music") {
            stroke("M9,17 V5 L20,3 V15 M9,8 L20,6 M9,17 A3,2 0 1 1 3,17 A3,2 0 1 1 9,17 M20,15 A3,2 0 1 1 14,15 A3,2 0 1 1 20,15")
    }

    val MoreTime: ImageVector = build("moreTime") {
            stroke("M12,4 A8,8 0 1 1 4,12 M12,8 V13 L15,15 M4,2 V8 M1,5 H7")
    }

    val Location: ImageVector = build("location") {
            stroke("M12,22 Q3,13 5,7 A7,7 0 0 1 19,7 Q21,13 12,22 Z M12,6 A3,3 0 1 1 12,12 A3,3 0 1 1 12,6")
    }

    val Home: ImageVector = build("home") {
            stroke("M3,11 L12,3 L21,11 M5,10 V21 H10 V15 H14 V21 H19 V10")
    }

    val Forward10: ImageVector = build("forward10") {
            stroke("M17,6 A8,8 0 1 0 20,14 M17,2 V6 H13 M7,10 V15 M12,10 H16 V15 H12 Z")
    }

    val TextColor: ImageVector = build("textColor") {
            stroke("M6,17 L12,3 L18,17 M8,12 H16 M4,21 H20")
    }

    val ClearFormat: ImageVector = build("clearFormat") {
            stroke("M5,5 H19 M12,5 V16 M4,20 L20,4 M16,18 L20,22 M20,18 L16,22")
    }

    val AlignLeft: ImageVector = build("alignLeft") {
            stroke("M3,5 H21 M3,10 H14 M3,15 H21 M3,20 H14")
    }

    val AlignCenter: ImageVector = build("alignCenter") {
            stroke("M3,5 H21 M7,10 H17 M3,15 H21 M7,20 H17")
    }

    val AlignRight: ImageVector = build("alignRight") {
            stroke("M3,5 H21 M10,10 H21 M3,15 H21 M10,20 H21")
    }

    val AlignJustify: ImageVector = build("alignJustify") {
            stroke("M3,5 H21 M3,10 H21 M3,15 H21 M3,20 H21")
    }

    val FilterOff: ImageVector = build("filterOff") {
            stroke("M7,4 H21 L15,12 V20 L10,17 V12 L3,4 M3,3 L21,21")
    }

    val PluginOff: ImageVector = build("pluginOff") {
            stroke("M4,8 H9 V4 Q12,1 15,4 V8 H20 V19 H5 V13 M3,3 L21,21")
    }

    val DoneAll: ImageVector = build("doneAll") {
            stroke("M2,12 L7,17 L17,5 M10,16 L13,19 L23,7")
    }

    val Server: ImageVector = build("server") {
            stroke("M4,3 H20 V10 H4 Z M4,14 H20 V21 H4 Z M7,6 H8 M7,17 H8 M12,6 H17 M12,17 H17")
    }

    val Moon: ImageVector = build("moon") {
            stroke("M16,3 A9,9 0 1 0 21,16 A10,10 0 0 1 16,3 Z")
    }

    val CloudOff: ImageVector = build("cloudOff") {
            stroke("M7,18 H18 Q23,16 20,12 M17,8 Q14,2 9,6 M6,9 Q0,12 4,17 M3,3 L21,21")
    }

    val ClearAll: ImageVector = build("clearAll") {
            stroke("M4,6 H20 M7,12 H20 M10,18 H20 M3,16 L7,20 M7,16 L3,20")
    }

    val ArrowUp: ImageVector = build("arrowUp") {
            stroke("M12,21 V3 M6,9 L12,3 L18,9")
    }

    val ArrowDown: ImageVector = build("arrowDown") {
            stroke("M12,3 V21 M6,15 L12,21 L18,15")
    }

    val AddCircle: ImageVector = build("addCircle") {
            stroke("M12,3 A9,9 0 1 1 12,21 A9,9 0 1 1 12,3 M7,12 H17 M12,7 V17")
    }

    val Web: ImageVector = build("web") {
            stroke("M3,4 H21 V20 H3 Z M3,9 H21 M7,6 H7.1 M11,6 H11.1 M7,13 H17 M7,16 H13")
    }

    val Columns: ImageVector = build("columns") {
            stroke("M3,4 H21 V20 H3 Z M9,4 V20 M15,4 V20")
    }

    val Carousel: ImageVector = build("carousel") {
            stroke("M6,4 H18 V20 H6 Z M2,7 V17 M22,7 V17")
    }

    val VideoSettings: ImageVector = build("videoSettings") {
            stroke("M3,5 H16 V19 H3 Z M16,10 L21,7 V17 L16,14 M7,9 H12 M10,7 V11 M6,15 H12 M8,13 V17")
    }

    val Upload: ImageVector = build("upload") {
            stroke("M12,17 V3 M7,8 L12,3 L17,8 M4,16 V21 H20 V16")
    }

    val ThumbDown: ImageVector = build("thumbDown") {
            stroke("M7,4 H18 L21,14 H14 V20 Q13,23 11,20 L7,14 H3 V4 Z M7,4 V14")
    }

    val Tablet: ImageVector = build("tablet") {
            stroke("M4,3 H20 V21 H4 Z M11,18 H13 M7,6 H17")
    }

    val SkipPrevious: ImageVector = build("skipPrevious") {
            stroke("M5,5 V19 M19,5 L8,12 L19,19 Z")
    }

    val Signal: ImageVector = build("signal") {
            stroke("M4,20 V16 H7 V20 Z M10,20 V10 H13 V20 Z M16,20 V4 H19 V20 Z")
    }

    val Hdmi: ImageVector = build("hdmi") {
            stroke("M5,7 H19 V14 L16,18 H8 L5,14 Z M8,7 V3 M12,7 V3 M16,7 V3 M12,18 V22")
    }

    val Ethernet: ImageVector = build("ethernet") {
            stroke("M4,5 H20 V17 H16 V21 H8 V17 H4 Z M8,9 V12 M12,9 V12 M16,9 V12")
    }

    val SelectAll: ImageVector = build("selectAll") {
            stroke("M3,7 V3 H7 M17,3 H21 V7 M21,17 V21 H17 M7,21 H3 V17 M7,7 H17 V17 H7 Z")
    }

    val SdCard: ImageVector = build("sdCard") {
            stroke("M7,3 H20 V21 H4 V6 Z M9,6 V10 M13,6 V10 M17,6 V10")
    }

    val Voice: ImageVector = build("voice") {
            stroke("M10,4 Q10,1 13,3 V13 Q10,16 10,13 Z M6,10 V13 Q6,20 17,17 Q19,15 19,11 M12,19 V22 M8,22 H16")
    }

    val QueueNext: ImageVector = build("queueNext") {
            stroke("M3,5 H16 M3,10 H14 M3,15 H10 M14,14 L20,18 L14,22 Z")
    }

    val Help: ImageVector = build("help") {
            stroke("M12,3 A9,9 0 1 1 12,21 A9,9 0 1 1 12,3 M9,8 Q12,4 15,8 Q17,11 12,13 V15 M12,18 H12.01")
    }

    val PersonAdd: ImageVector = build("personAdd") {
            stroke("M9,4 A3,3 0 1 1 9,10 A3,3 0 1 1 9,4 M2,21 Q2,13 9,13 Q14,13 15,18 M18,8 V16 M14,12 H22")
    }

    val Numbers: ImageVector = build("numbers") {
            stroke("M9,3 L7,21 M17,3 L15,21 M4,8 H21 M3,16 H20")
    }

    val Login: ImageVector = build("login") {
            stroke("M14,3 H21 V21 H14 M2,12 H16 M11,7 L16,12 L11,17")
    }

    val Flower: ImageVector = build("flower") {
            stroke("M12,8 Q5,0 5,8 Q-1,12 7,15 Q7,23 12,17 Q19,24 18,16 Q25,12 18,8 Q18,0 12,8 Z M12,9 A3,3 0 1 1 12,15 A3,3 0 1 1 12,9")
    }

    val Keyboard: ImageVector = build("keyboard") {
            stroke("M2,6 H22 V19 H2 Z M5,10 H6 M9,10 H10 M13,10 H14 M18,10 H19 M5,14 H6 M9,14 H10 M13,14 H14 M18,14 H19 M7,17 H17")
    }

    val Strike: ImageVector = build("strike") {
            stroke("M17,6 Q7,0 6,7 Q6,10 12,12 Q19,14 17,18 Q13,24 6,19 M3,12 H21")
    }

    val TextSize: ImageVector = build("textSize") {
            stroke("M3,5 H15 M9,5 V20 M16,12 H22 M19,12 V20")
    }

    val Quote: ImageVector = build("quote") {
            stroke("M4,6 H10 V12 Q10,18 4,19 V16 Q7,16 7,12 H4 Z M14,6 H20 V12 Q20,18 14,19 V16 Q17,16 17,12 H14 Z")
    }

    val Paint: ImageVector = build("paint") {
            stroke("M4,3 H18 V9 H4 Z M18,6 H21 V13 H12 V21 H9 V12 H18")
    }

    val Fill: ImageVector = build("fill") {
            stroke("M10,3 L21,14 L12,22 L2,12 Z M2,12 H19 M6,2 L11,7")
    }

    val Forest: ImageVector = build("forest") {
            stroke("M8,3 L2,13 H5 L1,19 H15 L11,13 H14 Z M8,19 V22 M17,4 L22,13 H19 L23,19 H17")
    }

    val FolderCopy: ImageVector = build("folderCopy") {
            stroke("M7,5 H12 L15,8 H22 V21 H7 Z M3,17 V2 H10 L12,5")
    }

    val Focus: ImageVector = build("focus") {
            stroke("M3,8 V3 H8 M16,3 H21 V8 M21,16 V21 H16 M8,21 H3 V16 M12,8 A4,4 0 1 1 12,16 A4,4 0 1 1 12,8")
    }

    val Equalizer: ImageVector = build("equalizer") {
            stroke("M5,3 V21 M12,3 V21 M19,3 V21 M2,8 H8 M9,16 H15 M16,10 H22")
    }

    val Trophy: ImageVector = build("trophy") {
            stroke("M7,3 H17 V10 Q17,16 12,16 Q7,16 7,10 Z M7,5 H3 V9 Q3,13 8,13 M17,5 H21 V9 Q21,13 16,13 M12,16 V21 M8,21 H16")
    }

    val Downloading: ImageVector = build("downloading") {
            stroke("M12,3 V15 M7,10 L12,15 L17,10 M3,17 V21 H9 M15,21 H21 V17")
    }

    val DownloadDone: ImageVector = build("downloadDone") {
            stroke("M5,7 L10,12 L19,3 M3,17 V21 H21 V17")
    }

    val DoubleArrow: ImageVector = build("doubleArrow") {
            stroke("M4,4 L12,12 L4,20 M12,4 L20,12 L12,20")
    }

    val Crop: ImageVector = build("crop") {
            stroke("M6,2 V18 H22 M2,6 H18 V22 M9,6 H18 V15")
    }

    val CameraControl: ImageVector = build("cameraControl") {
            stroke("M12,2 L9,5 H15 Z M12,22 L9,19 H15 Z M2,12 L5,9 V15 Z M22,12 L19,9 V15 Z M12,8 A4,4 0 1 1 12,16 A4,4 0 1 1 12,8")
    }

    val Contrast: ImageVector = build("contrast") {
            stroke("M12,3 A9,9 0 1 1 12,21 A9,9 0 1 1 12,3 M12,3 V21 M12,7 H18 M12,11 H20 M12,15 H19 M12,18 H17")
    }

    val Cake: ImageVector = build("cake") {
            stroke("M4,10 H20 V21 H4 Z M4,14 Q8,18 12,14 Q16,18 20,14 M8,10 V6 M12,10 V6 M16,10 V6 M8,3 V4 M12,2 V4 M16,3 V4")
    }

    val Border: ImageVector = build("border") {
            stroke("M3,3 H21 V21 H3 Z M7,7 H17 V17 H7 Z")
    }

    val Archive: ImageVector = build("archive") {
            stroke("M3,3 H21 V8 H3 Z M5,8 V21 H19 V8 M9,12 H15")
    }

    val AddLink: ImageVector = build("addLink") {
            stroke("M10,7 L13,4 Q18,1 21,6 Q23,10 18,13 M14,17 L11,20 Q7,23 3,19 Q0,15 5,11 M8,16 L16,8 M4,3 V9 M1,6 H7")
    }

    val Snow: ImageVector = build("snow") {
            stroke("M12,2 V22 M3,7 L21,17 M3,17 L21,7 M8,4 L12,7 L16,4 M8,20 L12,17 L16,20")
    }

    val Blur: ImageVector = build("blur") {
            stroke("M4,5 H4.1 M10,5 H10.1 M16,5 H16.1 M7,10 H7.1 M13,10 H13.1 M19,10 H19.1 M4,15 H4.1 M10,15 H10.1 M16,15 H16.1 M7,20 H7.1 M13,20 H13.1 M19,20 H19.1")
    }

    /** Ada göre glyph (dinamik kullanım: ayar araması, rozetler). */
    private val byName: Map<String, ImageVector> by lazy {
        mapOf(
            "explore" to Explore,
            "myList" to MyList,
            "mylist" to MyList,
            "search" to Search,
            "profile" to Profile,
            "settings" to Settings,
            "account" to Account,
            "person" to Person,
            "palette" to Palette,
            "player" to Player,
            "plugin" to Plugin,
            "integrations" to Integrations,
            "storage" to Storage,
            "cleaning" to Cleaning,
            "tune" to Tune,
            "download" to Download,
            "history" to History,
            "clock" to Clock,
            "info" to Info,
            "feedback" to Feedback,
            "bug" to Bug,
            "bell" to Bell,
            "star" to Star,
            "starFilled" to StarFilled,
            "starfilled" to StarFilled,
            "heart" to Heart,
            "heartOutline" to HeartOutline,
            "heartoutline" to HeartOutline,
            "bookmark" to Bookmark,
            "check" to Check,
            "checkCircle" to CheckCircle,
            "checkcircle" to CheckCircle,
            "film" to Film,
            "tv" to Tv,
            "comment" to Comment,
            "tag" to Tag,
            "share" to Share,
            "copy" to Copy,
            "link" to Link,
            "linkOff" to LinkOff,
            "linkoff" to LinkOff,
            "external" to External,
            "translate" to Translate,
            "subtitles" to Subtitles,
            "delete" to Delete,
            "edit" to Edit,
            "filter" to Filter,
            "chart" to Chart,
            "calendar" to Calendar,
            "book" to Book,
            "image" to Image,
            "folder" to Folder,
            "shield" to Shield,
            "globe" to Globe,
            "menu" to Menu,
            "more" to More,
            "close" to Close,
            "back" to Back,
            "forward" to Forward,
            "chevronDown" to ChevronDown,
            "chevrondown" to ChevronDown,
            "chevronUp" to ChevronUp,
            "chevronup" to ChevronUp,
            "chevronRight" to ChevronRight,
            "chevronright" to ChevronRight,
            "plus" to Plus,
            "eye" to Eye,
            "eyeOff" to EyeOff,
            "eyeoff" to EyeOff,
            "searchOff" to SearchOff,
            "searchoff" to SearchOff,
            "sort" to Sort,
            "queue" to Queue,
            "sparkle" to Sparkle,
            "refresh" to Refresh,
            "sync" to Sync,
            "cloudSync" to CloudSync,
            "cloudsync" to CloudSync,
            "play" to Play,
            "pause" to Pause,
            "playCircle" to PlayCircle,
            "playcircle" to PlayCircle,
            "skipNext" to SkipNext,
            "skipnext" to SkipNext,
            "volume" to Volume,
            "fullscreen" to Fullscreen,
            "cast" to Cast,
            "speed" to Speed,
            "pip" to Pip,
            "warning" to Warning,
            "error" to Error,
            "grid" to Grid,
            "rows" to Rows,
            "repeat" to Repeat,
            "key" to Key,
            "code" to Code,
            "send" to Send,
            "people" to People,
            "upCircle" to UpCircle,
            "upcircle" to UpCircle,
            "chevronLeft" to ChevronLeft,
            "chevronleft" to ChevronLeft,
            "fastForward" to FastForward,
            "fastforward" to FastForward,
            "fastRewind" to FastRewind,
            "fastrewind" to FastRewind,
            "cloudDownload" to CloudDownload,
            "clouddownload" to CloudDownload,
            "bolt" to Bolt,
            "hourglass" to Hourglass,
            "swap" to Swap,
            "removeCircle" to RemoveCircle,
            "removecircle" to RemoveCircle,
            "reply" to Reply,
            "thumbUp" to ThumbUp,
            "thumbup" to ThumbUp,
            "volumeOff" to VolumeOff,
            "volumeoff" to VolumeOff,
            "volumeLow" to VolumeLow,
            "volumelow" to VolumeLow,
            "repeatOne" to RepeatOne,
            "repeatone" to RepeatOne,
            "unlock" to Unlock,
            "bellOff" to BellOff,
            "belloff" to BellOff,
            "fullscreenExit" to FullscreenExit,
            "fullscreenexit" to FullscreenExit,
            "subtitlesOff" to SubtitlesOff,
            "subtitlesoff" to SubtitlesOff,
            "bellChecked" to BellChecked,
            "bellchecked" to BellChecked,
            "seek" to Seek,
            "random" to Random,
            "network" to Network,
            "memory" to Memory,
            "notes" to Notes,
            "touch" to Touch,
            "queueChecked" to QueueChecked,
            "queuechecked" to QueueChecked,
            "quality" to Quality,
            "numberedList" to NumberedList,
            "numberedlist" to NumberedList,
            "bold" to Bold,
            "italic" to Italic,
            "brokenImage" to BrokenImage,
            "brokenimage" to BrokenImage,
            "brightness" to Brightness,
            "aspect" to Aspect,
            "zoom" to Zoom,
            "text" to Text,
            "swipe" to Swipe,
            "stop" to Stop,
            "stopCircle" to StopCircle,
            "stopcircle" to StopCircle,
            "button" to Button,
            "rotation" to Rotation,
            "save" to Save,
            "feed" to Feed,
            "replay" to Replay,
            "minus" to Minus,
            "phone" to Phone,
            "pauseCircle" to PauseCircle,
            "pausecircle" to PauseCircle,
            "music" to Music,
            "moreTime" to MoreTime,
            "moretime" to MoreTime,
            "location" to Location,
            "home" to Home,
            "forward10" to Forward10,
            "textColor" to TextColor,
            "textcolor" to TextColor,
            "clearFormat" to ClearFormat,
            "clearformat" to ClearFormat,
            "alignLeft" to AlignLeft,
            "alignleft" to AlignLeft,
            "alignCenter" to AlignCenter,
            "aligncenter" to AlignCenter,
            "alignRight" to AlignRight,
            "alignright" to AlignRight,
            "alignJustify" to AlignJustify,
            "alignjustify" to AlignJustify,
            "filterOff" to FilterOff,
            "filteroff" to FilterOff,
            "pluginOff" to PluginOff,
            "pluginoff" to PluginOff,
            "doneAll" to DoneAll,
            "doneall" to DoneAll,
            "server" to Server,
            "moon" to Moon,
            "cloudOff" to CloudOff,
            "cloudoff" to CloudOff,
            "clearAll" to ClearAll,
            "clearall" to ClearAll,
            "arrowUp" to ArrowUp,
            "arrowup" to ArrowUp,
            "arrowDown" to ArrowDown,
            "arrowdown" to ArrowDown,
            "addCircle" to AddCircle,
            "addcircle" to AddCircle,
            "web" to Web,
            "columns" to Columns,
            "carousel" to Carousel,
            "videoSettings" to VideoSettings,
            "videosettings" to VideoSettings,
            "upload" to Upload,
            "thumbDown" to ThumbDown,
            "thumbdown" to ThumbDown,
            "tablet" to Tablet,
            "skipPrevious" to SkipPrevious,
            "skipprevious" to SkipPrevious,
            "signal" to Signal,
            "hdmi" to Hdmi,
            "ethernet" to Ethernet,
            "selectAll" to SelectAll,
            "selectall" to SelectAll,
            "sdCard" to SdCard,
            "sdcard" to SdCard,
            "voice" to Voice,
            "queueNext" to QueueNext,
            "queuenext" to QueueNext,
            "help" to Help,
            "personAdd" to PersonAdd,
            "personadd" to PersonAdd,
            "numbers" to Numbers,
            "login" to Login,
            "flower" to Flower,
            "keyboard" to Keyboard,
            "strike" to Strike,
            "textSize" to TextSize,
            "textsize" to TextSize,
            "quote" to Quote,
            "paint" to Paint,
            "fill" to Fill,
            "forest" to Forest,
            "folderCopy" to FolderCopy,
            "foldercopy" to FolderCopy,
            "focus" to Focus,
            "equalizer" to Equalizer,
            "trophy" to Trophy,
            "downloading" to Downloading,
            "downloadDone" to DownloadDone,
            "downloaddone" to DownloadDone,
            "doubleArrow" to DoubleArrow,
            "doublearrow" to DoubleArrow,
            "crop" to Crop,
            "cameraControl" to CameraControl,
            "cameracontrol" to CameraControl,
            "contrast" to Contrast,
            "cake" to Cake,
            "border" to Border,
            "archive" to Archive,
            "addLink" to AddLink,
            "addlink" to AddLink,
            "snow" to Snow,
            "blur" to Blur
        )
    }

    fun byNameOrNull(name: String?): ImageVector? =
        if (name.isNullOrBlank()) null else byName[name.trim().lowercase()]
}
