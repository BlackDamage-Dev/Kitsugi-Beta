package com.kitsugi.animelist.ui.icons

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent

/**
 * Kitsugi 3B ikon seti (ayarlar satırları, sekmeler ve gezinme çubuğu).
 *
 * Neden var?
 * ----------
 * Ekranlarda tek renkli Material vektör ikonları ("◯", "✓", "•" gibi görünen ince
 * çizgiler) hem küçük boyutlarda okunmuyor hem de "bu satır ne yapıyor?" sorusunu
 * yanıtlamıyordu. Bu set, her satırın **işlevini doğrudan anlatan** cam/gradyan
 * görünümlü, 3B render edilmiş, saydam arka planlı (arka plansız) görseller kullanır.
 *
 * Kaynak / lisans
 * ---------------
 * Görseller **3Dicons** setinden (Vijay Verma) alınmıştır:
 * https://3dicons.co — **CC0 1.0** (atıf gerekmez, ticari kullanım serbest).
 * Render'lar https://github.com/Vyshnav2255/notion-icons-3d aynasından (Apache-2.0)
 * indirilir. Ayrıntı: docs/IKON_SETI.md
 *
 * Teknik notlar
 * -------------
 * - Varlıklar `res/drawable-nodpi/ic_kitsugi_*.png` (192×192, saydam) olarak durur;
 *   hepsi aynı optik kutuya normalize edilmiştir (tools/iconpack/normalize.js).
 * - Bir varlık birden fazla mantıksal anahtar tarafından paylaşılabilir
 *   (ör. `wifi` hem Ağ hem Eşitleme; `clock` hem Geçmiş hem Zamanlayıcı).
 * - **Kontrol glifleri:** 3dicons paketinde kapat / geri / chevron / filtre / sırala /
 *   görünüm modu / tam ekran / kırpma / metin biçimi işaretleri yoktu. Bunlar aynı
 *   cam-gradyan diliyle `tools/iconpack/control_glyphs.js` tarafından üretildi
 *   (`ic_kitsugi_glyph_*.png`, 128×128). Böylece set **tam**: uygulamada tek renkli
 *   Material vektörüne düşen tek bir ikon çağrısı bile kalmadı (denetim: 1533/1533).
 * - [fromVector] Material ikon adından otomatik eşleme yapar; böylece 150+ çağrı
 *   yerini tek tek değiştirmek gerekmez. Yanlış eşleşme riski olan yerlerde
 *   `art = KitsugiArt.X` ile açıkça belirtilir.
 */
/**
 * 3dicons `front/gradient` setinin **doğal ortalama tonu** (ölçüldü: 303,7°).
 * Vurgu rengine göre ton kaydırması bu sabite göre hesaplanır.
 */
const val KITSUGI_ICON_NATIVE_HUE = 303.7f

/**
 * İkon tonunun vurgu rengine göre kaydırılacağı açı (derece, -180..180).
 * `0` = ikonlar doğal paletinde (mor-pembe) kalır.
 *
 * Tema kökü (`KitsugiAnimeListTheme`) bu değeri kullanıcının vurgu renginden
 * hesaplar; böylece renk seçicide mint / pembe / mor / mavi / yeşil / turuncu /
 * özel renk / iki renkli gradyan seçildiğinde ikonlar **otomatik** olarak o
 * renk ailesine kayar. Görsel: docs/ikon-tema-uyumu.png
 */
val LocalKitsugiIconHueShift = compositionLocalOf { 0f }

/** Verilen renk(ler)in tonu ile ikon doğal tonu arasındaki en kısa fark. */
fun iconHueShiftFor(accent: Color, accent2: Color? = null): Float {
    val target = if (accent2 == null) accent else Color(
        red = (accent.red + accent2.red) / 2f,
        green = (accent.green + accent2.green) / 2f,
        blue = (accent.blue + accent2.blue) / 2f,
        alpha = 1f
    )
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(target.toArgb(), hsv)
    // Neredeyse gri/beyaz bir vurgu seçildiyse tonu yok say (palet doğal kalsın).
    if (hsv[1] < 0.12f) return 0f
    var delta = hsv[0] - KITSUGI_ICON_NATIVE_HUE
    delta = ((delta + 540f) % 360f) - 180f
    return delta
}

/**
 * Ton kaydırma matrisi (standart hue-rotation). Doygunluk ve parlaklık korunur;
 * yalnızca renk ailesi döner — böylece cam/3B görünüm bozulmaz.
 */
private fun hueRotationMatrix(degrees: Float): ColorMatrix {
    val rad = degrees * PI.toFloat() / 180f
    val c = cos(rad)
    val s = sin(rad)
    return ColorMatrix(
        floatArrayOf(
            0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f, 0f, 0f,
            0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f, 0f, 0f,
            0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )
}

enum class KitsugiArt(
    @DrawableRes val resId: Int,
    /** Otomatik eşlemede kullanılan Material ikon adları (ör. "PlayCircle"). */
    internal val vectorNames: List<String> = emptyList()
) {
    // ── Hesap ────────────────────────────────────────────────────────────
    Account(
        R.drawable.ic_kitsugi_card
    ),
    Connections(R.drawable.ic_kitsugi_link),

    // ── Görünüm / oynatma ────────────────────────────────────────────────
    Palette(
        R.drawable.ic_kitsugi_colour_palette
    ),
    Player(
        R.drawable.ic_kitsugi_video_camera
    ),
    Play(R.drawable.ic_kitsugi_play),
    Pause(R.drawable.ic_kitsugi_pause),
    Next(R.drawable.ic_kitsugi_next),
    Rewind(R.drawable.ic_kitsugi_back),

    // ── Kaynaklar / entegrasyonlar ───────────────────────────────────────
    Extensions(R.drawable.ic_kitsugi_puzzle),
    Integrations(
        R.drawable.ic_kitsugi_sphere
    ),
    Network(
        R.drawable.ic_kitsugi_wifi
    ),
    Sync(
        R.drawable.ic_kitsugi_obj_sync
    ),

    // ── Veri / depolama ──────────────────────────────────────────────────
    Database(R.drawable.ic_kitsugi_locker),
    Storage(
        R.drawable.ic_kitsugi_trash_can
    ),

    // ── İndirmeler / geçmiş ──────────────────────────────────────────────
    Tune(R.drawable.ic_kitsugi_obj_sliders),
    Download(
        R.drawable.ic_kitsugi_obj_download
    ),
    History(R.drawable.ic_kitsugi_clock),
    Timer(R.drawable.ic_kitsugi_obj_timer),

    // ── Destek / tanılama ────────────────────────────────────────────────
    Info(R.drawable.ic_kitsugi_bulb),
    Feedback(R.drawable.ic_kitsugi_megaphone),
    Warning(
        R.drawable.ic_kitsugi_sheild
    ),
    Help(R.drawable.ic_kitsugi_chat),
    Memo(
        R.drawable.ic_kitsugi_pencil
    ),

    // ── Oynatıcı ayarları sekmeleri ──────────────────────────────────────
    Gestures(
        R.drawable.ic_kitsugi_magic_trick
    ),
    Decoder(R.drawable.ic_kitsugi_tool),
    Subtitles(
        R.drawable.ic_kitsugi_chat_text
    ),
    Audio(
        R.drawable.ic_kitsugi_headphone
    ),
    Buttons(R.drawable.ic_kitsugi_toggle),
    Code(R.drawable.ic_kitsugi_obj_code),
    Advanced(R.drawable.ic_kitsugi_lab),

    // ── Yardımcı ─────────────────────────────────────────────────────────
    Speed(R.drawable.ic_kitsugi_rocket),
    Sun(R.drawable.ic_kitsugi_sun),
    Moon(R.drawable.ic_kitsugi_moon),
    Tv(R.drawable.ic_kitsugi_obj_tv),
    Key(R.drawable.ic_kitsugi_key),
    Star(R.drawable.ic_kitsugi_star),
    Picture(R.drawable.ic_kitsugi_picture),
    Heart(R.drawable.ic_kitsugi_heart),
    Clipboard(R.drawable.ic_kitsugi_copy),
    Chart(R.drawable.ic_kitsugi_chart),
    CheckCircle(R.drawable.ic_kitsugi_tick),
    Lock(R.drawable.ic_kitsugi_lock),
    Folder(
        R.drawable.ic_kitsugi_folder
    ),
    Bell(R.drawable.ic_kitsugi_bell),
    Calendar(
        R.drawable.ic_kitsugi_calender
    ),
    Sparkles(R.drawable.ic_kitsugi_flash),
    ThumbUp(R.drawable.ic_kitsugi_thumb_up),
    Mobile(R.drawable.ic_kitsugi_mobile),
    Pin(R.drawable.ic_kitsugi_pin),

    // ── Gezinme çubuğu ───────────────────────────────────────────────────
    NavExplore(R.drawable.ic_kitsugi_explorer),
    NavList(R.drawable.ic_kitsugi_bookmark),
    NavSearch(R.drawable.ic_kitsugi_zoom),
    NavProfile(R.drawable.ic_kitsugi_boy),
    Upload(R.drawable.ic_kitsugi_obj_upload),
    Tag(R.drawable.ic_kitsugi_obj_tag),
    Backup(R.drawable.ic_kitsugi_obj_backup),
    Notebook(R.drawable.ic_kitsugi_notebook),
    Document(R.drawable.ic_kitsugi_file_text),
    File(R.drawable.ic_kitsugi_file),
    NewFolder(R.drawable.ic_kitsugi_new_folder),
    FavFolder(R.drawable.ic_kitsugi_fav_folder),
    Mail(R.drawable.ic_kitsugi_mail),
    Chat(R.drawable.ic_kitsugi_chat_bubble),
    Target(R.drawable.ic_kitsugi_target),
    Flag(R.drawable.ic_kitsugi_flag),
    Trophy(R.drawable.ic_kitsugi_trophy),
    Fire(R.drawable.ic_kitsugi_fire),
    Gift(R.drawable.ic_kitsugi_gift),
    Calculator(R.drawable.ic_kitsugi_calculator),
    Scissor(R.drawable.ic_kitsugi_scissor),
    PaintKit(R.drawable.ic_kitsugi_paint_kit),
    Brush(R.drawable.ic_kitsugi_paint_brush),
    Eyedropper(R.drawable.ic_kitsugi_eyedropper),
    Mic(R.drawable.ic_kitsugi_mic),
    Music(R.drawable.ic_kitsugi_music),
    Hash(R.drawable.ic_kitsugi_hash),
    Text(R.drawable.ic_kitsugi_text),
    At(R.drawable.ic_kitsugi_at),
    Cube(R.drawable.ic_kitsugi_cube),
    Wallet(R.drawable.ic_kitsugi_wallet),
    Battery(R.drawable.ic_kitsugi_battery),
    HeartBell(R.drawable.ic_kitsugi_notify_heart),
    Location(R.drawable.ic_kitsugi_location),
    Phone(R.drawable.ic_kitsugi_phone_ringing),
    Gym(R.drawable.ic_kitsugi_gym),
    Medal(R.drawable.ic_kitsugi_medal),
    Umbrella(R.drawable.ic_kitsugi_umbrella),
    Circle(R.drawable.ic_kitsugi_circle),
    Bag(R.drawable.ic_kitsugi_bag),
    Chess(R.drawable.ic_kitsugi_chess),
    Money(R.drawable.ic_kitsugi_money),
    Cup(R.drawable.ic_kitsugi_cup),
    Glass(R.drawable.ic_kitsugi_glass),
    Travel(R.drawable.ic_kitsugi_travel),
    NavSettings(R.drawable.ic_kitsugi_setting),


    // ── Kontrol glifleri (3dicons'ta yok; setin diliyle üretildi) ────────
    // Kaynak şekiller: tools/iconpack/control_glyphs.js — aynı cam/gradyan dili,
    // aynı ışık ve gölge kuralları. Böylece kapat / geri / filtre / sırala gibi
    // mikro kontroller de 3B setin parçası olur (tek renkli glif kalmaz).
    CtlClose(R.drawable.ic_kitsugi_glyph_close),
    CtlMinus(R.drawable.ic_kitsugi_glyph_minus),
    CtlChevronUp(R.drawable.ic_kitsugi_glyph_chevron_up),
    CtlChevronDown(R.drawable.ic_kitsugi_glyph_chevron_down),
    CtlChevronLeft(R.drawable.ic_kitsugi_glyph_chevron_left),
    CtlChevronRight(R.drawable.ic_kitsugi_glyph_chevron_right),
    CtlArrowUp(R.drawable.ic_kitsugi_glyph_arrow_up),
    CtlArrowDown(R.drawable.ic_kitsugi_glyph_arrow_down),
    CtlArrowLeft(R.drawable.ic_kitsugi_glyph_arrow_left),
    CtlArrowRight(R.drawable.ic_kitsugi_glyph_arrow_right),
    CtlEye(R.drawable.ic_kitsugi_glyph_eye),
    CtlEyeOff(R.drawable.ic_kitsugi_glyph_eye_off),
    CtlFunnel(R.drawable.ic_kitsugi_glyph_funnel),
    CtlSort(R.drawable.ic_kitsugi_glyph_sort),
    CtlGrid(R.drawable.ic_kitsugi_glyph_grid),
    CtlList(R.drawable.ic_kitsugi_glyph_list),
    CtlStream(R.drawable.ic_kitsugi_glyph_stream),
    CtlCarousel(R.drawable.ic_kitsugi_glyph_carousel),
    CtlMore(R.drawable.ic_kitsugi_glyph_more),
    CtlExternal(R.drawable.ic_kitsugi_glyph_external),
    CtlFullscreen(R.drawable.ic_kitsugi_glyph_fullscreen),
    CtlCrop(R.drawable.ic_kitsugi_glyph_crop),
    CtlRotate(R.drawable.ic_kitsugi_glyph_rotate),
    CtlAspect(R.drawable.ic_kitsugi_glyph_aspect),
    CtlBlur(R.drawable.ic_kitsugi_glyph_blur),
    CtlAlignLeft(R.drawable.ic_kitsugi_glyph_align_left),
    CtlAlignCenter(R.drawable.ic_kitsugi_glyph_align_center),
    CtlAlignJustify(R.drawable.ic_kitsugi_glyph_align_justify),
    CtlBorder(R.drawable.ic_kitsugi_glyph_border),
    CtlSelectAll(R.drawable.ic_kitsugi_glyph_select_all),
    CtlDrag(R.drawable.ic_kitsugi_glyph_drag),
    CtlDensity(R.drawable.ic_kitsugi_glyph_density),
    CtlKeyboard(R.drawable.ic_kitsugi_glyph_keyboard),
    CtlInput(R.drawable.ic_kitsugi_glyph_input),
    CtlDoubleArrow(R.drawable.ic_kitsugi_glyph_double_arrow),
    CtlHome(R.drawable.ic_kitsugi_glyph_home),
    CtlLeaf(R.drawable.ic_kitsugi_glyph_leaf),
    CtlSnow(R.drawable.ic_kitsugi_glyph_snow),
    CtlCake(R.drawable.ic_kitsugi_glyph_cake),
    CtlCamera(R.drawable.ic_kitsugi_glyph_camera),
    CtlTextBold(R.drawable.ic_kitsugi_glyph_text_bold),
    CtlTextItalic(R.drawable.ic_kitsugi_glyph_text_italic),
    CtlTextTitle(R.drawable.ic_kitsugi_glyph_text_title),
    CtlTextHash(R.drawable.ic_kitsugi_glyph_text_hash),
    CtlTextStrike(R.drawable.ic_kitsugi_glyph_text_strike),
    CtlTextQuote(R.drawable.ic_kitsugi_glyph_text_quote),
    Plus(R.drawable.ic_kitsugi_plus),
    ThumbDown(R.drawable.ic_kitsugi_thumb_down);

    companion object {
        /**
         * **Material ikon adı → 3B varlık** eşlemesi. Tek doğru kaynak.
         *
         * Neden tek tablo?
         * ----------------
         * Önceki turlarda eşleme üç ayrı yerde (enum listeleri, EXTRA_ALIASES,
         * CONTROL_ALIASES) duruyordu; aynı ikon farklı işlevlerde tekrar ediyordu.
         * Artık **her ad tek satırda, kendi işlevine en yakın görselle** eşlenir.
         * Denetim: `node tools/iconpack/coverage_report.js` (268 ad, 0 boşta).
         *
         * Sıra: tam ad ("Rounded.PlayCircle") → kısa ad ("PlayCircle").
         */
        private val NAME_MAP: Map<String, KitsugiArt> by lazy {
            mapOf(
                // kapat / temizle
                "Close" to KitsugiArt.CtlClose, "Cancel" to KitsugiArt.CtlClose, "Clear" to KitsugiArt.CtlClose,
                "ClearAll" to KitsugiArt.CtlClose, "FormatClear" to KitsugiArt.CtlClose,
                // ekle
                "Add" to KitsugiArt.Plus, "AddCircle" to KitsugiArt.Plus, "AddCircleOutline" to KitsugiArt.Plus,
                // azalt
                "Remove" to KitsugiArt.CtlMinus,
                // onay / tamam
                "Check" to KitsugiArt.CheckCircle, "CheckCircle" to KitsugiArt.CheckCircle,
                "CheckCircleOutline" to KitsugiArt.CheckCircle, "Done" to KitsugiArt.CheckCircle,
                "DoneAll" to KitsugiArt.CheckCircle, "TaskAlt" to KitsugiArt.CheckCircle,
                // yukarı / daralt
                "KeyboardArrowUp" to KitsugiArt.CtlChevronUp, "ExpandLess" to KitsugiArt.CtlChevronUp,
                "ArrowDropUp" to KitsugiArt.CtlChevronUp, "UnfoldLess" to KitsugiArt.CtlChevronUp,
                // aşağı / genişlet
                "KeyboardArrowDown" to KitsugiArt.CtlChevronDown, "ExpandMore" to KitsugiArt.CtlChevronDown,
                "ArrowDropDown" to KitsugiArt.CtlChevronDown, "UnfoldMore" to KitsugiArt.CtlChevronDown,
                // sağ ok
                "ChevronRight" to KitsugiArt.CtlChevronRight, "NavigateNext" to KitsugiArt.CtlChevronRight,
                // sol ok
                "ChevronLeft" to KitsugiArt.CtlChevronLeft, "NavigateBefore" to KitsugiArt.CtlChevronLeft,
                // geri
                "ArrowBack" to KitsugiArt.CtlArrowLeft, "ArrowBackIos" to KitsugiArt.CtlArrowLeft,
                "West" to KitsugiArt.CtlArrowLeft, "KeyboardBackspace" to KitsugiArt.CtlArrowLeft,
                // ileri
                "ArrowForward" to KitsugiArt.CtlArrowRight, "East" to KitsugiArt.CtlArrowRight,
                // yukarı ok
                "ArrowUpward" to KitsugiArt.CtlArrowUp, "ArrowCircleUp" to KitsugiArt.CtlArrowUp,
                "North" to KitsugiArt.CtlArrowUp,
                // aşağı ok
                "ArrowDownward" to KitsugiArt.CtlArrowDown, "South" to KitsugiArt.CtlArrowDown,
                // dışa aç
                "OpenInNew" to KitsugiArt.CtlExternal, "OpenInBrowser" to KitsugiArt.CtlExternal,
                "Launch" to KitsugiArt.CtlExternal, "ExitToApp" to KitsugiArt.CtlExternal,
                // göster
                "Visibility" to KitsugiArt.CtlEye, "RemoveRedEye" to KitsugiArt.CtlEye,
                // gizle
                "VisibilityOff" to KitsugiArt.CtlEyeOff,
                // filtre
                "FilterList" to KitsugiArt.CtlFunnel, "FilterAlt" to KitsugiArt.CtlFunnel,
                "FilterAltOff" to KitsugiArt.CtlFunnel,
                // sırala
                "Sort" to KitsugiArt.CtlSort, "SortByAlpha" to KitsugiArt.CtlSort, "SwapVert" to KitsugiArt.CtlSort,
                // ızgara görünümü
                "GridView" to KitsugiArt.CtlGrid, "ViewQuilt" to KitsugiArt.CtlGrid, "ViewWeek" to KitsugiArt.CtlGrid,
                "ViewModule" to KitsugiArt.CtlGrid,
                // liste görünümü / çalma listesi
                "ViewList" to KitsugiArt.CtlList, "List" to KitsugiArt.CtlList,
                "FormatListBulleted" to KitsugiArt.CtlList, "FormatListNumbered" to KitsugiArt.CtlList,
                "ListAlt" to KitsugiArt.CtlList, "PlaylistAdd" to KitsugiArt.CtlList,
                "PlaylistAddCheck" to KitsugiArt.CtlList, "PlaylistPlay" to KitsugiArt.CtlList,
                "QueuePlayNext" to KitsugiArt.CtlList, "LibraryAdd" to KitsugiArt.CtlList, "Toc" to KitsugiArt.CtlList,
                // akış görünümü
                "ViewStream" to KitsugiArt.CtlStream,
                // karusel görünümü
                "ViewCarousel" to KitsugiArt.CtlCarousel,
                // diğer (⋮)
                "MoreVert" to KitsugiArt.CtlMore, "MoreHoriz" to KitsugiArt.CtlMore,
                // tam ekran
                "Fullscreen" to KitsugiArt.CtlFullscreen, "FitScreen" to KitsugiArt.CtlFullscreen,
                "SettingsOverscan" to KitsugiArt.CtlFullscreen,
                // kırp / odakla
                "Crop" to KitsugiArt.CtlCrop, "CropFree" to KitsugiArt.CtlCrop,
                "CenterFocusWeak" to KitsugiArt.CtlCrop, "FilterCenterFocus" to KitsugiArt.CtlCrop,
                // döndür
                "ScreenRotation" to KitsugiArt.CtlRotate, "RotateLeft" to KitsugiArt.CtlRotate,
                "RotateRight" to KitsugiArt.CtlRotate,
                // en-boy oranı
                "AspectRatio" to KitsugiArt.CtlAspect,
                // bulanıklık
                "BlurOn" to KitsugiArt.CtlBlur,
                // sola hizala
                "FormatAlignLeft" to KitsugiArt.CtlAlignLeft,
                // ortala
                "FormatAlignCenter" to KitsugiArt.CtlAlignCenter,
                // iki yana yasla
                "FormatAlignJustify" to KitsugiArt.CtlAlignJustify,
                // kenarlık
                "BorderOuter" to KitsugiArt.CtlBorder, "BorderColor" to KitsugiArt.CtlBorder,
                // tümünü seç
                "SelectAll" to KitsugiArt.CtlSelectAll,
                // sürükle / sırala
                "DragHandle" to KitsugiArt.CtlDrag,
                // yoğunluk
                "DensityMedium" to KitsugiArt.CtlDensity,
                // klavye
                "Keyboard" to KitsugiArt.CtlKeyboard, "KeyboardAlt" to KitsugiArt.CtlKeyboard,
                // girişler
                "SettingsInputComponent" to KitsugiArt.CtlInput, "SettingsInputHdmi" to KitsugiArt.CtlInput,
                // aralık / genişlet
                "DoubleArrow" to KitsugiArt.CtlDoubleArrow,
                // ana sayfa
                "Home" to KitsugiArt.CtlHome,
                // bitki / doğa
                "LocalFlorist" to KitsugiArt.CtlLeaf, "Forest" to KitsugiArt.CtlLeaf,
                // kar / soğuk
                "AcUnit" to KitsugiArt.CtlSnow,
                // pasta / doğum Günü
                "Cake" to KitsugiArt.CtlCake,
                // kalın
                "FormatBold" to KitsugiArt.CtlTextBold,
                // italik
                "FormatItalic" to KitsugiArt.CtlTextItalic,
                // başlık / boyut
                "Title" to KitsugiArt.CtlTextTitle, "FormatSize" to KitsugiArt.CtlTextTitle,
                "FormatColorText" to KitsugiArt.CtlTextTitle, "TextFields" to KitsugiArt.CtlTextTitle,
                // numara / #
                "Numbers" to KitsugiArt.CtlTextHash,
                // üstü çizili
                "FormatStrikethrough" to KitsugiArt.CtlTextStrike,
                // alıntı
                "FormatQuote" to KitsugiArt.CtlTextQuote,
                // hesap
                "AccountCircle" to KitsugiArt.Account, "ManageAccounts" to KitsugiArt.Account,
                "Badge" to KitsugiArt.Account,
                // bağlantı / paylaş
                "Link" to KitsugiArt.Connections, "LinkOff" to KitsugiArt.Connections,
                "Share" to KitsugiArt.Connections,
                // renk & tema
                "Palette" to KitsugiArt.Palette, "ColorLens" to KitsugiArt.Palette,
                "FormatPaint" to KitsugiArt.Palette, "Gradient" to KitsugiArt.Palette,
                "InvertColors" to KitsugiArt.Palette, "Opacity" to KitsugiArt.Palette, "Style" to KitsugiArt.Palette,
                "FormatColorFill" to KitsugiArt.Palette, "FormatColorReset" to KitsugiArt.Palette,
                // oynatıcı / video
                "Movie" to KitsugiArt.Player, "PlayCircle" to KitsugiArt.Player,
                "PlayCircleFilled" to KitsugiArt.Player, "VideoSettings" to KitsugiArt.Player,
                "SlowMotionVideo" to KitsugiArt.Player, "Videocam" to KitsugiArt.Player,
                "VideoLibrary" to KitsugiArt.Player, "ControlCamera" to KitsugiArt.Player,
                // oynat
                "PlayArrow" to KitsugiArt.Play, "PlayCircleOutline" to KitsugiArt.Play,
                // duraklat / durdur
                "Pause" to KitsugiArt.Pause, "PauseCircle" to KitsugiArt.Pause, "StopCircle" to KitsugiArt.Pause,
                "Stop" to KitsugiArt.Pause,
                // sonraki
                "SkipNext" to KitsugiArt.Next, "FastForward" to KitsugiArt.Next, "Forward10" to KitsugiArt.Next,
                "Forward" to KitsugiArt.Next,
                // geri sar
                "SkipPrevious" to KitsugiArt.Rewind, "FastRewind" to KitsugiArt.Rewind, "Replay" to KitsugiArt.Rewind,
                "Restore" to KitsugiArt.Rewind,
                // eklenti
                "Extension" to KitsugiArt.Extensions, "ExtensionOff" to KitsugiArt.Extensions,
                "Widgets" to KitsugiArt.Extensions, "Apps" to KitsugiArt.Extensions,
                // entegrasyon / dil
                "Hub" to KitsugiArt.Integrations, "Language" to KitsugiArt.Integrations,
                "Public" to KitsugiArt.Integrations, "TravelExplore" to KitsugiArt.Integrations,
                "Cloud" to KitsugiArt.Integrations, "Translate" to KitsugiArt.Integrations,
                // ağ / DNS
                "Wifi" to KitsugiArt.Network, "WifiOff" to KitsugiArt.Network, "NetworkCheck" to KitsugiArt.Network,
                "SettingsEthernet" to KitsugiArt.Network, "Dns" to KitsugiArt.Network, "Lan" to KitsugiArt.Network,
                "Web" to KitsugiArt.Network, "RssFeed" to KitsugiArt.Network, "AddLink" to KitsugiArt.Network,
                "SignalCellularAlt" to KitsugiArt.Network, "CloudOff" to KitsugiArt.Network,
                // eşitleme / yenile
                "Sync" to KitsugiArt.Sync, "SyncAlt" to KitsugiArt.Sync, "Refresh" to KitsugiArt.Sync,
                "Cached" to KitsugiArt.Sync, "Autorenew" to KitsugiArt.Sync, "Repeat" to KitsugiArt.Sync,
                "RepeatOne" to KitsugiArt.Sync, "RestartAlt" to KitsugiArt.Sync, "SwapHoriz" to KitsugiArt.Sync,
                "CompareArrows" to KitsugiArt.Sync, "CloudSync" to KitsugiArt.Sync,
                // veritabanı / depolama
                "Storage" to KitsugiArt.Database, "SdCard" to KitsugiArt.Database, "Archive" to KitsugiArt.Database,
                "SdStorage" to KitsugiArt.Database,
                // yedek / bulut
                "CloudUpload" to KitsugiArt.Backup, "CloudDone" to KitsugiArt.Backup, "Backup" to KitsugiArt.Backup,
                // sil / çöp
                "Delete" to KitsugiArt.Storage, "DeleteOutline" to KitsugiArt.Storage,
                "DeleteForever" to KitsugiArt.Storage, "DeleteSweep" to KitsugiArt.Storage,
                "CleaningServices" to KitsugiArt.Storage, "RemoveCircle" to KitsugiArt.Storage,
                // ince ayar / kaydırıcılar
                "Tune" to KitsugiArt.Tune, "Equalizer" to KitsugiArt.Tune, "GraphicEq" to KitsugiArt.Tune,
                "SettingsApplications" to KitsugiArt.Tune,
                // indir
                "Download" to KitsugiArt.Download, "DownloadForOffline" to KitsugiArt.Download,
                "CloudDownload" to KitsugiArt.Download, "Downloading" to KitsugiArt.Download,
                "FileDownload" to KitsugiArt.Download, "SystemUpdate" to KitsugiArt.Download,
                "SystemUpdateAlt" to KitsugiArt.Download, "SaveAlt" to KitsugiArt.Download,
                "Save" to KitsugiArt.Download,
                // yükle
                "Upload" to KitsugiArt.Upload,
                // geçmiş
                "History" to KitsugiArt.History, "Update" to KitsugiArt.History, "WatchLater" to KitsugiArt.History,
                // zamanlayıcı / alarm
                "Timer" to KitsugiArt.Timer, "Schedule" to KitsugiArt.Timer, "AccessTime" to KitsugiArt.Timer,
                "HourglassEmpty" to KitsugiArt.Timer, "HourglassFull" to KitsugiArt.Timer,
                "HourglassBottom" to KitsugiArt.Timer, "MoreTime" to KitsugiArt.Timer, "AvTimer" to KitsugiArt.Timer,
                "Upcoming" to KitsugiArt.Timer, "Alarm" to KitsugiArt.Timer, "AlarmOn" to KitsugiArt.Timer,
                // bilgi
                "Info" to KitsugiArt.Info, "InfoOutline" to KitsugiArt.Info,
                // geri bildirim
                "Feedback" to KitsugiArt.Feedback, "RateReview" to KitsugiArt.Feedback,
                "Campaign" to KitsugiArt.Feedback, "Announcement" to KitsugiArt.Feedback,
                // uyarı / hata
                "BugReport" to KitsugiArt.Warning, "Warning" to KitsugiArt.Warning,
                "WarningAmber" to KitsugiArt.Warning, "ErrorOutline" to KitsugiArt.Warning,
                "Error" to KitsugiArt.Warning, "Report" to KitsugiArt.Warning, "PriorityHigh" to KitsugiArt.Warning,
                "ReportProblem" to KitsugiArt.Warning,
                // yardım
                "Help" to KitsugiArt.Help, "HelpOutline" to KitsugiArt.Help, "QuestionMark" to KitsugiArt.Help,
                // not / düzenle
                "Edit" to KitsugiArt.Memo, "EditNote" to KitsugiArt.Memo, "Notes" to KitsugiArt.Memo,
                "Create" to KitsugiArt.Memo,
                // kitap / defter
                "MenuBook" to KitsugiArt.Notebook, "Book" to KitsugiArt.Notebook, "AutoStories" to KitsugiArt.Notebook,
                // belge / metin dosyası
                "Description" to KitsugiArt.Document, "Article" to KitsugiArt.Document,
                // kopyala / yapıştır
                "ContentCopy" to KitsugiArt.Clipboard, "ContentPaste" to KitsugiArt.Clipboard,
                "FileCopy" to KitsugiArt.Clipboard, "Assignment" to KitsugiArt.Clipboard,
                // hareketler
                "TouchApp" to KitsugiArt.Gestures, "Swipe" to KitsugiArt.Gestures, "SwipeLeft" to KitsugiArt.Gestures,
                "SwipeRight" to KitsugiArt.Gestures, "SwipeUp" to KitsugiArt.Gestures,
                "SwipeDown" to KitsugiArt.Gestures, "Gesture" to KitsugiArt.Gestures, "PanTool" to KitsugiArt.Gestures,
                // kod çözücü / donanım
                "Memory" to KitsugiArt.Decoder, "DeveloperBoard" to KitsugiArt.Decoder, "Hd" to KitsugiArt.Decoder,
                "HighQuality" to KitsugiArt.Decoder, "Handyman" to KitsugiArt.Decoder,
                "HomeRepairService" to KitsugiArt.Decoder,
                // altyazı
                "Subtitles" to KitsugiArt.Subtitles, "SubtitlesOff" to KitsugiArt.Subtitles,
                "ClosedCaption" to KitsugiArt.Subtitles,
                // ses
                "VolumeUp" to KitsugiArt.Audio, "VolumeDown" to KitsugiArt.Audio, "VolumeMute" to KitsugiArt.Audio,
                "VolumeOff" to KitsugiArt.Audio, "Hearing" to KitsugiArt.Audio,
                // mikrofon / ses kaydı
                "Mic" to KitsugiArt.Mic, "MicNone" to KitsugiArt.Mic, "MicOff" to KitsugiArt.Mic,
                "KeyboardVoice" to KitsugiArt.Mic, "RecordVoiceOver" to KitsugiArt.Mic,
                // müzik
                "MusicNote" to KitsugiArt.Music, "Audiotrack" to KitsugiArt.Music,
                // özel buton / oyun
                "SmartButton" to KitsugiArt.Buttons, "SportsEsports" to KitsugiArt.Buttons,
                "Gamepad" to KitsugiArt.Buttons, "Vibration" to KitsugiArt.Buttons,
                // kod / geliştirici
                "Code" to KitsugiArt.Code, "CodeOff" to KitsugiArt.Code, "Terminal" to KitsugiArt.Code,
                "DataObject" to KitsugiArt.Code,
                // gelişmiş
                "DeveloperMode" to KitsugiArt.Advanced, "Construction" to KitsugiArt.Advanced,
                // hız / performans
                "Speed" to KitsugiArt.Speed, "Bolt" to KitsugiArt.Speed, "FlashOn" to KitsugiArt.Speed,
                "RocketLaunch" to KitsugiArt.Speed,
                // aydınlık tema
                "BrightnessMedium" to KitsugiArt.Sun, "Brightness7" to KitsugiArt.Sun,
                "BrightnessLow" to KitsugiArt.Sun, "BrightnessHigh" to KitsugiArt.Sun, "Brightness6" to KitsugiArt.Sun,
                "WbSunny" to KitsugiArt.Sun, "LightMode" to KitsugiArt.Sun, "Contrast" to KitsugiArt.Sun,
                // karanlık tema
                "DarkMode" to KitsugiArt.Moon, "NightsStay" to KitsugiArt.Moon, "Bedtime" to KitsugiArt.Moon,
                "Nightlight" to KitsugiArt.Moon, "ModeNight" to KitsugiArt.Moon,
                // TV / yayın
                "Tv" to KitsugiArt.Tv, "ConnectedTv" to KitsugiArt.Tv, "LiveTv" to KitsugiArt.Tv,
                "SmartDisplay" to KitsugiArt.Tv,
                // şifre / anahtar
                "Key" to KitsugiArt.Key, "VpnKey" to KitsugiArt.Key, "Login" to KitsugiArt.Key,
                "Logout" to KitsugiArt.Key, "Password" to KitsugiArt.Key,
                // puan / yıldız
                "Star" to KitsugiArt.Star, "StarBorder" to KitsugiArt.Star, "StarHalf" to KitsugiArt.Star,
                "Grade" to KitsugiArt.Star,
                // başarı / ödül
                "EmojiEvents" to KitsugiArt.Trophy, "WorkspacePremium" to KitsugiArt.Trophy,
                "MilitaryTech" to KitsugiArt.Trophy, "Stars" to KitsugiArt.Trophy,
                // görsel / fotoğraf
                "Image" to KitsugiArt.Picture, "Photo" to KitsugiArt.Picture, "InsertPhoto" to KitsugiArt.Picture,
                "Wallpaper" to KitsugiArt.Picture, "ImageSearch" to KitsugiArt.Picture,
                "BrokenImage" to KitsugiArt.Picture, "ImageNotSupported" to KitsugiArt.Picture,
                "Slideshow" to KitsugiArt.Picture, "Preview" to KitsugiArt.Picture,
                "PictureInPictureAlt" to KitsugiArt.Picture, "PhotoLibrary" to KitsugiArt.Picture,
                // favori / kalp
                "Favorite" to KitsugiArt.Heart, "FavoriteBorder" to KitsugiArt.Heart,
                // istatistik
                "BarChart" to KitsugiArt.Chart, "DonutLarge" to KitsugiArt.Chart, "Insights" to KitsugiArt.Chart,
                "ShowChart" to KitsugiArt.Chart, "TrendingUp" to KitsugiArt.Chart, "Analytics" to KitsugiArt.Chart,
                // güvenlik / kilit
                "Lock" to KitsugiArt.Lock, "LockOpen" to KitsugiArt.Lock, "Security" to KitsugiArt.Lock,
                "HealthAndSafety" to KitsugiArt.Lock, "VerifiedUser" to KitsugiArt.Lock,
                "PrivacyTip" to KitsugiArt.Lock,
                // klasör
                "Folder" to KitsugiArt.Folder, "FolderOpen" to KitsugiArt.Folder, "FolderSpecial" to KitsugiArt.Folder,
                "FolderCopy" to KitsugiArt.Folder, "FolderFilled" to KitsugiArt.Folder,
                // yeni klasör
                "CreateNewFolder" to KitsugiArt.NewFolder,
                // bildirim
                "Notifications" to KitsugiArt.Bell, "NotificationsNone" to KitsugiArt.Bell,
                "NotificationsActive" to KitsugiArt.Bell, "NotificationsOff" to KitsugiArt.Bell,
                // takvim
                "CalendarToday" to KitsugiArt.Calendar, "DateRange" to KitsugiArt.Calendar,
                "Event" to KitsugiArt.Calendar, "EventAvailable" to KitsugiArt.Calendar,
                "CalendarMonth" to KitsugiArt.Calendar, "Today" to KitsugiArt.Calendar,
                // yenilik
                "AutoAwesome" to KitsugiArt.Sparkles, "NewReleases" to KitsugiArt.Sparkles,
                "AutoFixHigh" to KitsugiArt.Sparkles,
                // beğen
                "ThumbUp" to KitsugiArt.ThumbUp,
                // beğenme
                "ThumbDown" to KitsugiArt.ThumbDown,
                // mobil cihaz
                "PhoneAndroid" to KitsugiArt.Mobile, "Tablet" to KitsugiArt.Mobile, "Devices" to KitsugiArt.Mobile,
                "Smartphone" to KitsugiArt.Mobile, "PhoneIphone" to KitsugiArt.Mobile,
                // konum
                "LocationOn" to KitsugiArt.Pin, "Place" to KitsugiArt.Pin, "PinDrop" to KitsugiArt.Pin,
                "MyLocation" to KitsugiArt.Pin,
                // keşfet (sekme)
                "Explore" to KitsugiArt.NavExplore, "SearchOff" to KitsugiArt.NavExplore,
                // listem (sekme)
                "Bookmark" to KitsugiArt.NavList, "BookmarkBorder" to KitsugiArt.NavList,
                "Bookmarks" to KitsugiArt.NavList, "BookmarkAdded" to KitsugiArt.NavList,
                // arama
                "Search" to KitsugiArt.NavSearch, "ManageSearch" to KitsugiArt.NavSearch,
                "ZoomIn" to KitsugiArt.NavSearch, "ZoomOut" to KitsugiArt.NavSearch,
                // profil (sekme)
                "Person" to KitsugiArt.NavProfile, "People" to KitsugiArt.NavProfile,
                "Groups" to KitsugiArt.NavProfile, "Group" to KitsugiArt.NavProfile,
                "PersonOutline" to KitsugiArt.NavProfile, "PersonAdd" to KitsugiArt.NavProfile,
                "Person2" to KitsugiArt.NavProfile,
                // ayarlar (sekme)
                "Settings" to KitsugiArt.NavSettings, "Build" to KitsugiArt.NavSettings,
                // etiket
                "Label" to KitsugiArt.Tag, "Sell" to KitsugiArt.Tag, "Tag" to KitsugiArt.Tag,
                // e-posta
                "Mail" to KitsugiArt.Mail, "Email" to KitsugiArt.Mail, "MailOutline" to KitsugiArt.Mail,
                // sohbet / yorum
                "ChatBubble" to KitsugiArt.Chat, "ChatBubbleOutline" to KitsugiArt.Chat, "Comment" to KitsugiArt.Chat,
                "Forum" to KitsugiArt.Chat,
                // hedef
                "TrackChanges" to KitsugiArt.Target,
                // işaretle
                "OutlinedFlag" to KitsugiArt.Flag,
                // trend / popüler
                "Whatshot" to KitsugiArt.Fire, "LocalFireDepartment" to KitsugiArt.Fire,
                // hediye / davet
                "CardGiftcard" to KitsugiArt.Gift, "Redeem" to KitsugiArt.Gift,
                // hesaplama
                "Calculate" to KitsugiArt.Calculator,
                // kes
                "ContentCut" to KitsugiArt.Scissor,
                // çizim
                "Brush" to KitsugiArt.Brush, "Draw" to KitsugiArt.Brush,
                // renk seçici
                "Colorize" to KitsugiArt.Eyedropper,
                // etiket / #
                "AlternateEmail" to KitsugiArt.Hash,
                // metin denetimi
                "Spellcheck" to KitsugiArt.Text,
                // katman / 3B
                "ViewInAr" to KitsugiArt.Cube, "Layers" to KitsugiArt.Cube,
                // cüzdan / ödeme
                "AccountBalanceWallet" to KitsugiArt.Wallet,
                // pil / güç
                "BatteryFull" to KitsugiArt.Battery,
                // favori bildirimi
                "NotificationAdd" to KitsugiArt.HeartBell,
                // arama / destek
                "PhoneInTalk" to KitsugiArt.Phone, "Phone" to KitsugiArt.Phone, "Call" to KitsugiArt.Phone,
                // durum
                "Circle" to KitsugiArt.Circle,
                // ── KitsugiIcons (Lucide & ek uyumluluk adları) ──────────────────
                "ALargeSmall" to KitsugiArt.CtlTextTitle, "AlertTriangle" to KitsugiArt.Warning, "Angry" to KitsugiArt.Warning,
                "ArrowDown" to KitsugiArt.CtlArrowDown, "ArrowDownAZ" to KitsugiArt.CtlSort, "ArrowDownWideNarrow" to KitsugiArt.CtlSort,
                "ArrowLeft" to KitsugiArt.CtlArrowLeft, "ArrowLeftRight" to KitsugiArt.Sync, "ArrowRight" to KitsugiArt.CtlArrowRight,
                "ArrowUp" to KitsugiArt.CtlArrowUp, "ArrowUpDown" to KitsugiArt.CtlSort, "ArrowUpNarrowWide" to KitsugiArt.CtlSort,
                "ArrowUpZA" to KitsugiArt.CtlSort, "AudioLines" to KitsugiArt.Audio, "Award" to KitsugiArt.Trophy,
                "Baby" to KitsugiArt.NavProfile, "BadgePlus" to KitsugiArt.Plus, "Ban" to KitsugiArt.CtlClose,
                "Banknote" to KitsugiArt.Money, "Baseline" to KitsugiArt.CtlTextTitle, "Bell" to KitsugiArt.Bell,
                "BellRing" to KitsugiArt.Bell, "BellSolid" to KitsugiArt.Bell, "Blend" to KitsugiArt.Palette,
                "BookOpen" to KitsugiArt.Notebook, "BookmarkSolid" to KitsugiArt.NavList, "BookmarkStroke" to KitsugiArt.NavList,
                "Bot" to KitsugiArt.Sparkles, "Brain" to KitsugiArt.Sparkles, "Bug" to KitsugiArt.Warning,
                "Building" to KitsugiArt.CtlHome, "Cable" to KitsugiArt.CtlInput, "Calendar" to KitsugiArt.Calendar,
                "CalendarArrowDown" to KitsugiArt.Calendar, "CalendarArrowUp" to KitsugiArt.Calendar, "CalendarCheck" to KitsugiArt.Calendar,
                "CalendarDays" to KitsugiArt.Calendar, "CalendarRange" to KitsugiArt.Calendar, "Captions" to KitsugiArt.Subtitles,
                "Castle" to KitsugiArt.CtlHome, "ChartColumn" to KitsugiArt.Chart, "ChartPie" to KitsugiArt.Chart,
                "CheckCheck" to KitsugiArt.CheckCircle, "ChefHat" to KitsugiArt.Cup, "ChevronDown" to KitsugiArt.CtlChevronDown,
                "ChevronUp" to KitsugiArt.CtlChevronUp, "Church" to KitsugiArt.CtlHome, "CircleAlert" to KitsugiArt.Warning,
                "CircleArrowUp" to KitsugiArt.CtlArrowUp, "CircleCheck" to KitsugiArt.CheckCircle, "CircleDot" to KitsugiArt.Circle,
                "CircleMinus" to KitsugiArt.Storage, "CirclePause" to KitsugiArt.Pause, "CirclePlay" to KitsugiArt.Player,
                "CirclePlus" to KitsugiArt.Plus, "CircleQuestionMark" to KitsugiArt.Help, "CircleStop" to KitsugiArt.Pause,
                "CircleUserRound" to KitsugiArt.Account, "CircleX" to KitsugiArt.CtlClose, "Clapperboard" to KitsugiArt.Player,
                "Clipboard" to KitsugiArt.Clipboard, "ClipboardList" to KitsugiArt.Clipboard, "Clock" to KitsugiArt.Timer,
                "ClockArrowUp" to KitsugiArt.History, "CloudLightning" to KitsugiArt.Network, "Coffee" to KitsugiArt.Cup,
                "Columns3" to KitsugiArt.CtlGrid, "Compass" to KitsugiArt.NavExplore, "Copy" to KitsugiArt.Clipboard,
                "Cpu" to KitsugiArt.Decoder, "Crown" to KitsugiArt.Trophy, "Database" to KitsugiArt.Database,
                "Dices" to KitsugiArt.Chess, "Disc" to KitsugiArt.Music, "Dog" to KitsugiArt.Heart,
                "DownloadDone" to KitsugiArt.CheckCircle, "Drama" to KitsugiArt.Player, "Droplets" to KitsugiArt.CtlBlur,
                "Dumbbell" to KitsugiArt.Gym, "EllipsisVertical" to KitsugiArt.CtlMore, "Eraser" to KitsugiArt.CtlClose,
                "ExternalLink" to KitsugiArt.CtlExternal, "Eye" to KitsugiArt.CtlEye, "EyeOff" to KitsugiArt.CtlEyeOff,
                "FaceAngry" to KitsugiArt.Warning, "FaceGrinning" to KitsugiArt.ThumbUp, "FaceNeutral" to KitsugiArt.Chat,
                "FaceSlightlyFrowning" to KitsugiArt.ThumbDown, "FaceSlightlySmiling" to KitsugiArt.ThumbUp, "Film" to KitsugiArt.Player,
                "Flag" to KitsugiArt.Flag, "Flame" to KitsugiArt.Fire, "FlaskConical" to KitsugiArt.Advanced,
                "Flower" to KitsugiArt.CtlLeaf, "Focus" to KitsugiArt.CtlCrop, "FormatAlignRight" to KitsugiArt.CtlAlignLeft,
                "Frown" to KitsugiArt.ThumbDown, "Funnel" to KitsugiArt.CtlFunnel, "GalleryHorizontal" to KitsugiArt.CtlCarousel,
                "GalleryVertical" to KitsugiArt.CtlStream, "Gamepad2" to KitsugiArt.Buttons, "Gauge" to KitsugiArt.Speed,
                "Ghost" to KitsugiArt.Buttons, "Globe" to KitsugiArt.Integrations, "GripVertical" to KitsugiArt.CtlDrag,
                "Hand" to KitsugiArt.Gestures, "HandHeart" to KitsugiArt.Heart, "HardDrive" to KitsugiArt.Database,
                "Hash" to KitsugiArt.Hash, "HdrOff" to KitsugiArt.Sun, "HdrOn" to KitsugiArt.Sun,
                "Heading1" to KitsugiArt.CtlTextTitle, "Heart" to KitsugiArt.Heart, "HeartPulse" to KitsugiArt.HeartBell,
                "HeartSolid" to KitsugiArt.Heart, "Hourglass" to KitsugiArt.Timer, "House" to KitsugiArt.CtlHome,
                "ImageOff" to KitsugiArt.Picture, "Images" to KitsugiArt.Picture, "Italic" to KitsugiArt.CtlTextItalic,
                "KeyRound" to KitsugiArt.Key, "KeyboardArrowRight" to KitsugiArt.CtlChevronRight, "Landmark" to KitsugiArt.CtlHome,
                "Languages" to KitsugiArt.Integrations, "Laugh" to KitsugiArt.ThumbUp, "LayoutGrid" to KitsugiArt.CtlGrid,
                "Leaf" to KitsugiArt.CtlLeaf, "Library" to KitsugiArt.Notebook, "Link2" to KitsugiArt.Connections,
                "ListChecks" to KitsugiArt.CtlList, "ListOrdered" to KitsugiArt.CtlList, "ListPlus" to KitsugiArt.CtlList,
                "ListVideo" to KitsugiArt.CtlList, "LogIn" to KitsugiArt.Key, "MailOpen" to KitsugiArt.Mail,
                "MapPin" to KitsugiArt.Pin, "Maximize" to KitsugiArt.CtlFullscreen, "Medal" to KitsugiArt.Medal,
                "Meh" to KitsugiArt.Chat, "Menu" to KitsugiArt.CtlDensity, "MessageCircle" to KitsugiArt.Chat,
                "MessageSquare" to KitsugiArt.Chat, "Minus" to KitsugiArt.CtlMinus, "MonitorPlay" to KitsugiArt.Player,
                "Moon" to KitsugiArt.Moon, "MoonStar" to KitsugiArt.Moon, "Network" to KitsugiArt.Network,
                "Newspaper" to KitsugiArt.Document, "NotebookPen" to KitsugiArt.Memo, "NotebookText" to KitsugiArt.Memo,
                "Package" to KitsugiArt.Extensions, "PaintBucket" to KitsugiArt.Palette, "PaintRoller" to KitsugiArt.Palette,
                "PartyPopper" to KitsugiArt.Gift, "Pencil" to KitsugiArt.Memo, "PictureInPicture" to KitsugiArt.Picture,
                "Plane" to KitsugiArt.Travel, "Play" to KitsugiArt.Play, "Plus" to KitsugiArt.Plus,
                "Pointer" to KitsugiArt.Gestures, "Popcorn" to KitsugiArt.Player, "Presentation" to KitsugiArt.Picture,
                "Puzzle" to KitsugiArt.Extensions, "Quote" to KitsugiArt.CtlTextQuote, "Ratio" to KitsugiArt.CtlAspect,
                "RefreshCw" to KitsugiArt.Sync, "RemoveFormatting" to KitsugiArt.CtlClose, "Repeat1" to KitsugiArt.Sync,
                "Reply" to KitsugiArt.Rewind, "Rewind" to KitsugiArt.Rewind, "Rocket" to KitsugiArt.Speed,
                "RotateCcw" to KitsugiArt.Rewind, "RotateCw" to KitsugiArt.CtlRotate, "Rows3" to KitsugiArt.CtlStream,
                "Rss" to KitsugiArt.Network, "Satellite" to KitsugiArt.Network, "SatelliteDish" to KitsugiArt.Network,
                "Scan" to KitsugiArt.CtlCrop, "School" to KitsugiArt.Notebook, "Scroll" to KitsugiArt.Document,
                "SearchX" to KitsugiArt.NavExplore, "Send" to KitsugiArt.Mail, "Server" to KitsugiArt.Network,
                "Settings2" to KitsugiArt.Tune, "Shapes" to KitsugiArt.Extensions, "Share2" to KitsugiArt.Connections,
                "Shield" to KitsugiArt.Lock, "ShieldCheck" to KitsugiArt.Lock, "Signal" to KitsugiArt.Network,
                "SignalHigh" to KitsugiArt.Warning, "SkipBack" to KitsugiArt.Rewind, "SkipForward" to KitsugiArt.Next,
                "Skull" to KitsugiArt.Warning, "SlidersHorizontal" to KitsugiArt.Tune, "Smile" to KitsugiArt.ThumbUp,
                "Snowflake" to KitsugiArt.CtlSnow, "Sparkles" to KitsugiArt.Sparkles, "Sprout" to KitsugiArt.CtlLeaf,
                "Square" to KitsugiArt.CtlBorder, "SquareDashed" to KitsugiArt.CtlSelectAll, "SquarePlus" to KitsugiArt.Plus,
                "StarOff" to KitsugiArt.Star, "StarSolid" to KitsugiArt.Star, "StarStroke" to KitsugiArt.Star,
                "Strikethrough" to KitsugiArt.CtlTextStrike, "Sun" to KitsugiArt.Sun, "SunDim" to KitsugiArt.Sun,
                "SunMedium" to KitsugiArt.Sun, "Sword" to KitsugiArt.Target, "Swords" to KitsugiArt.Target,
                "Tab" to KitsugiArt.Clipboard, "Target" to KitsugiArt.Target, "TextAlignCenter" to KitsugiArt.CtlAlignCenter,
                "TextAlignEnd" to KitsugiArt.CtlAlignLeft, "TextAlignJustify" to KitsugiArt.CtlAlignJustify, "TextAlignStart" to KitsugiArt.CtlAlignLeft,
                "Theater" to KitsugiArt.Player, "ThumbsDown" to KitsugiArt.ThumbDown, "ThumbsUp" to KitsugiArt.ThumbUp,
                "Ticket" to KitsugiArt.Gift, "Trash" to KitsugiArt.Storage, "Trees" to KitsugiArt.CtlLeaf,
                "TrendingDown" to KitsugiArt.Chart, "TriangleAlert" to KitsugiArt.Warning, "Trophy" to KitsugiArt.Trophy,
                "Type" to KitsugiArt.CtlTextTitle, "Unlink" to KitsugiArt.Connections, "User" to KitsugiArt.NavProfile,
                "UserPlus" to KitsugiArt.NavProfile, "UserRound" to KitsugiArt.NavProfile, "Users" to KitsugiArt.NavProfile,
                "VenetianMask" to KitsugiArt.Buttons, "Verified" to KitsugiArt.CheckCircle, "Video" to KitsugiArt.Player,
                "Volume1" to KitsugiArt.Audio, "Volume2" to KitsugiArt.Audio, "VolumeX" to KitsugiArt.Audio,
                "WandSparkles" to KitsugiArt.Gestures, "X" to KitsugiArt.CtlClose, "Zap" to KitsugiArt.Speed,
            )
        }

        /**
         * Verilen Material [icon] için 3B karşılığını döner; eşleşme yoksa `null`.
         * Uygulamada kullanılan 268 ikon adının tamamı eşlidir (boşta ad: 0).
         */
        fun fromVector(icon: ImageVector): KitsugiArt? {
            val rawName = icon.name
            if (rawName.isEmpty()) return null
            val bare = rawName.substringAfterLast('.')
            return NAME_MAP[rawName] ?: NAME_MAP[bare]
        }
    }
}

/**
 * Tek bir 3B ikonu çizer (saydam arka plan, kutu doldurma) — **tema uyumlu**.
 *
 * Uyum kuralları
 * --------------
 * - **Koyu tema / AMOLED:** görsel olduğu gibi çizilir. Cam parlaklıkları koyu zeminde
 *   zaten okunur; ek katman yok (plaka yok, çerçeve yok).
 * - **Açık tema:** beyaz cam parlamaları beyaz zeminde kaybolduğu için ikonun arkasına
 *   *plaka değil*, çok yumuşak bir gölge (radyal, %22 → saydam) çizilir. İkon beyaz
 *   kartta da "havada duran 3B nesne" gibi kalır.
 * - **Seçili durum** (`accentGlow = true`): kullanıcının seçtiği vurgu renginden çok
 *   hafif bir hâle. Böylece kullanıcı hangi vurguyu (mint, mor, turuncu, özel renk,
 *   hatta iki renkli gradyan) seçerse seçsin ikon o renkle çevrelenmiş görünür —
 *   ikon paleti sabit kalsa da ekranın rengiyle konuşur.
 * - **Renk seçimi / gradyan (otomatik):** [LocalKitsugiIconHueShift] tema kökünde
 *   kullanıcının vurgu renginden hesaplanır ve ikon yalnızca **ton** olarak o renge
 *   kaydırılır (doygunluk/parlaklık korunur). Vurgu iki renkli gradyansa iki rengin
 *   ortası kullanılır. Gri/beyaz bir vurgu seçilirse (doygunluk < %12) kaydırma
 *   yapılmaz, palet doğal kalır.
 * - [alpha] seçili/seçili olmayan geçişlerinde yumuşak solma için kullanılır.
 */
@Composable
fun KitsugiArtIcon(
    art: KitsugiArt,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: Dp = 30.dp,
    alpha: Float = 1f,
    accentGlow: Boolean = false
) {
    val accent = LocalKitsugiAccent.current
    val hueShift = LocalKitsugiIconHueShift.current
    val lightSurface = MaterialTheme.colorScheme.background.luminance() > 0.5f
    // Hâle / gölge fırçaları, vurgu rengi değişmedikçe yeniden üretilmez.
    val glowBrush = remember(accent) {
        Brush.radialGradient(
            0f to accent.copy(alpha = 0.40f),
            0.55f to accent.copy(alpha = 0.12f),
            1f to Color.Transparent
        )
    }
    val shadeBrush = remember {
        Brush.radialGradient(
            0f to Color.Black.copy(alpha = 0.24f),
            0.6f to Color.Black.copy(alpha = 0.08f),
            1f to Color.Transparent
        )
    }
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        if (accentGlow || lightSurface) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        drawCircle(
                            brush = if (accentGlow) glowBrush else shadeBrush,
                            radius = this.size.minDimension * if (accentGlow) 0.66f else 0.56f,
                            center = center
                        )
                    }
            )
        }
        // Vurgu rengine göre otomatik ton kaydırması (0° → palet doğal kalır).
        val hueFilter = remember(hueShift) {
            if (abs(hueShift) < 1f) null else ColorFilter.colorMatrix(hueRotationMatrix(hueShift))
        }
        Image(
            painter = painterResource(art.resId),
            contentDescription = contentDescription,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Fit,
            alpha = alpha,
            colorFilter = hueFilter
        )
    }
}

/**
 * Material3 [Icon] ile **birebir aynı imza** taşıyan, ama 3B seti bilen sarmalayıcı.
 *
 * Neden var?
 * ----------
 * Projedeki ikonların bir kısmı doğrudan `androidx.compose.material3.Icon` ile
 * çiziliyordu (liste kartları, araç çubukları, FAB'lar, arama alanları...). Bu
 * sarmalayıcı sayesinde çağrı yerlerini tek tek değiştirmek gerekmez:
 *
 * ```kotlin
 * import com.kitsugi.animelist.ui.icons.KitsugiIcon as Icon
   // tek satır
 * ```
 *
 * Eşleşen bir 3B varlık yoksa (kapat, geri, onay, chevron, filtre gibi kontrol
 * glifleri) mevcut mono Material ikonu aynen çizilir — davranış değişmez.
 *
 * Boyut: çağıranın `Modifier.size(..)` değeri korunur; boyut verilmemişse 24 dp
 * (Material Icon ile aynı varsayılan).
 */
@Composable
fun KitsugiIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = androidx.compose.material3.LocalContentColor.current
) {
    val art = remember(imageVector) { KitsugiArt.fromVector(imageVector) }
    if (art != null) {
        KitsugiArtIcon(
            art = art,
            modifier = modifier,
            contentDescription = contentDescription,
            size = 24.dp
        )
    } else {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint
        )
    }
}

/**
 * Ayar satırı ikonu: 3B görsel varsa onu, yoksa satır rengiyle boyanmış tek renkli
 * glifi (arka plan dairesi OLMADAN) çizer. Slot her iki durumda da aynı genişlikte
 * olduğu için başlık hizası kaymaz.
 */
@Composable
fun KitsugiRowIcon(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    art: KitsugiArt? = null,
    artSize: Dp = 30.dp,
    glyphSize: Dp = 24.dp
) {
    val resolved = art ?: KitsugiArt.fromVector(icon)
    Box(
        modifier = modifier.size(32.dp),
        contentAlignment = Alignment.Center
    ) {
        if (resolved != null) {
            KitsugiArtIcon(art = resolved, size = artSize)
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(glyphSize)
            )
        }
    }
}
