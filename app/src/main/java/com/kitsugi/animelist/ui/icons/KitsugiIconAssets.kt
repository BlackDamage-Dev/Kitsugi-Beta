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
        R.drawable.ic_kitsugi_card,
        listOf("AccountCircle", "AccountBox", "ManageAccounts", "Badge", "PermIdentity")
    ),
    Connections(R.drawable.ic_kitsugi_link, listOf("Link", "LinkOff", "Share", "LinkRounded")),

    // ── Görünüm / oynatma ────────────────────────────────────────────────
    Palette(
        R.drawable.ic_kitsugi_colour_palette,
        listOf("Palette", "ColorLens", "Brush", "Gradient", "FormatPaint", "Style", "InvertColors", "Opacity")
    ),
    Player(
        R.drawable.ic_kitsugi_video_camera,
        listOf("PlayCircle", "PlayCircleFilled", "Movie", "Theaters", "Videocam", "VideoSettings", "SlowMotionVideo")
    ),
    Play(R.drawable.ic_kitsugi_play, listOf("PlayArrow", "PlayCircleOutline")),
    Pause(R.drawable.ic_kitsugi_pause, listOf("Pause", "PauseCircle", "StopCircle", "PlayPause")),
    Next(R.drawable.ic_kitsugi_next, listOf("SkipNext", "FastForward", "Forward10", "Forward30", "Forward5")),
    Rewind(R.drawable.ic_kitsugi_back, listOf("FastRewind", "Replay10", "Replay30", "Replay5", "Redo")),

    // ── Kaynaklar / entegrasyonlar ───────────────────────────────────────
    Extensions(R.drawable.ic_kitsugi_puzzle, listOf("Extension", "Widgets", "ExtensionOff")),
    Integrations(
        R.drawable.ic_kitsugi_sphere,
        listOf("Hub", "DeviceHub", "Api", "Public", "Language", "TravelExplore", "Cloud")
    ),
    Network(
        R.drawable.ic_kitsugi_wifi,
        listOf("NetworkCheck", "Wifi", "WifiOff", "SettingsEthernet", "Dns", "Lan", "CastConnected")
    ),
    Sync(
        R.drawable.ic_kitsugi_wifi,
        listOf("Sync", "SyncAlt", "SyncProblem", "CloudSync", "Cached", "Autorenew", "Refresh", "Repeat", "RepeatOne", "RestartAlt")
    ),

    // ── Veri / depolama ──────────────────────────────────────────────────
    Database(R.drawable.ic_kitsugi_locker, listOf("Storage", "Backup", "Save", "CloudUpload", "Archive")),
    Storage(
        R.drawable.ic_kitsugi_trash_can,
        listOf("CleaningServices", "DeleteSweep", "FolderDelete", "Delete", "DeleteOutline", "DeleteForever", "RemoveCircle")
    ),

    // ── İndirmeler / geçmiş ──────────────────────────────────────────────
    Tune(R.drawable.ic_kitsugi_setting, listOf("Tune", "SettingsApplications", "Equalizer", "GraphicEq")),
    Download(
        R.drawable.ic_kitsugi_travel,
        listOf("Download", "DownloadForOffline", "FileDownload", "SaveAlt", "DownloadDone", "CloudDownload", "CloudDownloadOutline")
    ),
    History(R.drawable.ic_kitsugi_clock, listOf("History", "Restore", "Update", "WatchLater", "Replay")),
    Timer(R.drawable.ic_kitsugi_clock, listOf("Timer", "HourglassEmpty", "HourglassFull", "AccessTime", "Schedule")),

    // ── Destek / tanılama ────────────────────────────────────────────────
    Info(R.drawable.ic_kitsugi_bulb, listOf("Info", "InfoOutline")),
    Feedback(R.drawable.ic_kitsugi_megaphone, listOf("Feedback", "RateReview", "Campaign", "Announcement")),
    Warning(
        R.drawable.ic_kitsugi_sheild,
        listOf("BugReport", "Warning", "WarningAmber", "ErrorOutline", "Report", "Error", "PriorityHigh", "ReportProblem")
    ),
    Help(R.drawable.ic_kitsugi_chat, listOf("Help", "HelpOutline", "QuestionMark")),
    Memo(
        R.drawable.ic_kitsugi_pencil,
        listOf("Notes", "Description", "Article", "EditNote", "Edit", "Book", "MenuBook", "Title", "TextFields", "Translate", "DriveFileRenameOutline")
    ),

    // ── Oynatıcı ayarları sekmeleri ──────────────────────────────────────
    Gestures(
        R.drawable.ic_kitsugi_magic_trick,
        listOf("Swipe", "SwipeLeft", "SwipeRight", "SwipeUp", "SwipeDown", "Gesture", "TouchApp", "PanTool")
    ),
    Decoder(R.drawable.ic_kitsugi_tool, listOf("Memory", "DeveloperBoard", "Hd", "HighQuality", "Handyman", "HomeRepairService")),
    Subtitles(
        R.drawable.ic_kitsugi_chat_text,
        listOf("Subtitles", "SubtitlesOff", "ClosedCaption", "SubtitlesOutlined", "ChatBubble", "ChatBubbleOutline", "Comment", "Forum")
    ),
    Audio(
        R.drawable.ic_kitsugi_headphone,
        listOf("VolumeUp", "VolumeDown", "VolumeMute", "VolumeOff", "Audiotrack", "MusicNote", "Hearing")
    ),
    Buttons(R.drawable.ic_kitsugi_toggle, listOf("SmartButton", "SportsEsports", "Gamepad", "Vibration")),
    Code(R.drawable.ic_kitsugi_computer, listOf("Code", "CodeOff", "Terminal", "DataObject")),
    Advanced(R.drawable.ic_kitsugi_lab, listOf("DeveloperMode", "SettingsSuggest", "Construction")),

    // ── Yardımcı ─────────────────────────────────────────────────────────
    Speed(R.drawable.ic_kitsugi_rocket, listOf("Speed", "Bolt", "FlashOn", "RocketLaunch")),
    Sun(R.drawable.ic_kitsugi_sun, listOf("BrightnessMedium", "Brightness7", "BrightnessLow", "WbSunny", "LightMode")),
    Moon(R.drawable.ic_kitsugi_moon, listOf("DarkMode", "NightsStay", "Bedtime", "Nightlight")),
    Tv(R.drawable.ic_kitsugi_computer, listOf("Tv", "ConnectedTv", "LiveTv", "SmartDisplay")),
    Key(R.drawable.ic_kitsugi_key, listOf("Key", "KeyOff", "VpnKey", "Password")),
    Star(R.drawable.ic_kitsugi_star, listOf("Star", "StarBorder", "StarHalf", "Grade", "Favorite")),
    Picture(R.drawable.ic_kitsugi_picture, listOf("Image", "Photo", "InsertPhoto", "Wallpaper", "ImageSearch")),
    Heart(R.drawable.ic_kitsugi_heart, listOf("FavoriteBorder", "HeartBroken")),
    Clipboard(R.drawable.ic_kitsugi_copy, listOf("ContentCopy", "ContentPaste", "Assignment", "FileCopy")),
    Chart(R.drawable.ic_kitsugi_chart, listOf("BarChart", "DonutLarge", "Insights", "ShowChart", "TrendingUp", "Analytics")),
    CheckCircle(R.drawable.ic_kitsugi_tick, listOf("CheckCircle", "CheckCircleOutline", "DoneAll", "TaskAlt")),
    Lock(R.drawable.ic_kitsugi_lock, listOf("Lock", "LockOpen", "Security", "HealthAndSafety", "VerifiedUser", "PrivacyTip")),
    Folder(
        R.drawable.ic_kitsugi_folder,
        listOf("Folder", "FolderOpen", "PhotoLibrary", "VideoLibrary", "AutoStories", "Books", "SdStorage", "FolderSpecial")
    ),
    Bell(R.drawable.ic_kitsugi_bell, listOf("Notifications", "NotificationsNone", "NotificationsActive", "NotificationsOff")),
    Calendar(
        R.drawable.ic_kitsugi_calender,
        listOf("CalendarToday", "DateRange", "Event", "EventAvailable", "CalendarMonth", "Today")
    ),
    Sparkles(R.drawable.ic_kitsugi_flash, listOf("AutoAwesome", "NewReleases", "Stars", "AutoFixHigh")),
    ThumbUp(R.drawable.ic_kitsugi_thumb_up, listOf("ThumbUp", "ThumbUpAlt", "Recommend")),
    Mobile(R.drawable.ic_kitsugi_mobile, listOf("PhoneAndroid", "Tablet", "Devices", "Smartphone", "PhoneIphone")),
    Pin(R.drawable.ic_kitsugi_pin, listOf("LocationOn", "Place", "PinDrop", "MyLocation")),

    // ── Gezinme çubuğu ───────────────────────────────────────────────────
    NavExplore(R.drawable.ic_kitsugi_explorer, listOf("Explore")),
    NavList(R.drawable.ic_kitsugi_bookmark, listOf("ListAlt", "Bookmark", "BookmarkBorder", "Bookmarks", "CollectionsBookmark", "FormatListBulleted")),
    NavSearch(R.drawable.ic_kitsugi_zoom, listOf("Search", "ManageSearch", "SearchOff", "ZoomIn", "ZoomOut")),
    NavProfile(R.drawable.ic_kitsugi_boy, listOf("Person", "Person2", "PersonOutline", "Group", "Groups", "People")),
    NavSettings(R.drawable.ic_kitsugi_setting, listOf("Settings", "Build"));

    companion object {
        /** Material ikon adı -> 3B ikon. Ad "Rounded.PlayCircle" ise son parça kullanılır. */
        private val byVectorName: Map<String, KitsugiArt> by lazy {
            val map = HashMap<String, KitsugiArt>()
            values().forEach { art ->
                art.vectorNames.forEach { name -> map[name] = art }
            }
            map
        }

        /**
         * Verilen Material [icon] için 3B karşılığını döner; eşleşme yoksa `null`.
         * `null` dönerse çağıran taraf tek renkli glife düşer (geri oku, kapat,
         * onay işareti gibi kontrol mikro-ikonları bilerek burada bırakılır).
         */
        fun fromVector(icon: ImageVector): KitsugiArt? {
            val rawName = icon.name
            if (rawName.isEmpty()) return null
            byVectorName[rawName]?.let { return it }
            return byVectorName[rawName.substringAfterLast('.')]
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
