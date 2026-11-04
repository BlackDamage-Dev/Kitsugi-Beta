package com.kitsugi.animelist.core.player.input

/**
 * Kitsugi oynatıcısının klavye / gamepad kısayol modeli.
 *
 * Neden ayrı bir model?
 * ─────────────────────
 * Oynatıcıda tuş basımları iki farklı katmandan geçiyordu:
 *
 *  1. `KitsugiFullscreenPlayerScreen` içindeki `onPreviewKeyEvent` — yalnızca D-pad
 *     yönleri, Enter ve Back'i biliyordu. Diğer her tuş `playerEngine.handleKey(...)`
 *     ile MPV'ye ham olarak gönderiliyordu. MPV'nin varsayılan bağlamaları masaüstü
 *     varsayımlarıdır: `q` oynatıcıyı kapatır, `s` ekran görüntüsü alır, `f`
 *     tam ekranı değiştirir… Yani harici klavye takan bir kullanıcı farkında
 *     olmadan oynatıcıdan çıkabiliyordu.
 *  2. Media3 (ExoPlayer) motorunda `handleKey` hiçbir şey yapmıyordu — uygulama
 *     içi (harici olmayan) oynatıcıda klavye tamamen ölüydü.
 *
 * Bu model, tuş → eylem eşlemesini motordan ve UI'dan bağımsız hale getirir.
 * Açık kaynak oynatıcılardan alınan davranışlar referans alındı:
 *
 *  - **mpv** (`etc/input.conf`): boşluk = oynat/duraklat, ←/→ = ±5 sn, ↑/↓ = ±60 sn,
 *    Shift+ok = 1 sn hassas arama, `[`/`]` = hız ±%10, `{`/`}` = hızı yarıla/ikiye
 *    katla, Backspace = hızı sıfırla, `v` = altyazı görünürlüğü, `j`/`#` = altyazı/ses
 *    parçası döngüsü, `z`/`Z` = altyazı gecikmesi, PgUp/PgDn = bölüm, `s` = ekran
 *    görüntüsü, `f` = tam ekran, `m` = sessize al.
 *  - **Just Player** (Media3, `moneytoo/Player`): oynatıcı arayüzü görünürken
 *    ok tuşlarını arayüz gezinmesine bırakır; arayüz gizliyken ←/→ ile ±10 sn
 *    atlar, tuş basılı tutulduğunda art arda atlar ve birikimli süreyi gösterir.
 *  - **mpv-android** (`mpv-android/mpv-android`): tuş tekrarını (repeat) kendi
 *    başına yönetir; basılı tutulan tuşta olayı yutar ama her tekrarı işlemez,
 *    ham `keydown`/`keyup` yerine kendi tuş eşleme tablosunu kullanır.
 *  - **Aniyomi**: jest eşikleri ve OSD zamanlaması için referans.
 *
 * Tasarım ilkesi: **tek bir tuş = tek bir eylem**, ve eylem her zaman motorun
 * yeteneklerine göre Kitsugi tarafında uygulanır (MPV'ye ham tuş gönderilmez).
 * Böylece Media3 ve MPV motorları aynı kısayolları paylaşır.
 */
enum class PlayerShortcutAction {
    PLAY_PAUSE,
    SEEK_BACKWARD,
    SEEK_FORWARD,
    SEEK_BACKWARD_LARGE,
    SEEK_FORWARD_LARGE,
    SEEK_BACKWARD_PRECISE,
    SEEK_FORWARD_PRECISE,
    SEEK_TO_START,
    VOLUME_UP,
    VOLUME_DOWN,
    VOLUME_BOOST_UP,
    VOLUME_BOOST_DOWN,
    TOGGLE_MUTE,
    SPEED_UP,
    SPEED_DOWN,
    SPEED_RESET,
    SPEED_HALVE,
    SPEED_DOUBLE,
    CYCLE_SUBTITLE,
    TOGGLE_SUBTITLE_VISIBILITY,
    SUBTITLE_DELAY_DOWN,
    SUBTITLE_DELAY_UP,
    CYCLE_AUDIO,
    NEXT_EPISODE,
    PREVIOUS_EPISODE,
    NEXT_CHAPTER,
    PREVIOUS_CHAPTER,
    CYCLE_ASPECT,
    TOGGLE_ORIENTATION,
    TOGGLE_STATS,
    SCREENSHOT,
    OPEN_SETTINGS,
    TOGGLE_HELP,
    TOGGLE_UI,
    LOCK_TOGGLE,
    EXIT;

    /** Kısayolun kullanıcıya gösterilecek kısa adı (Türkçe). */
    val label: String
        get() = when (this) {
            PLAY_PAUSE -> "Oynat / Duraklat"
            SEEK_BACKWARD -> "Geri sar"
            SEEK_FORWARD -> "İleri sar"
            SEEK_BACKWARD_LARGE -> "Uzun geri sar"
            SEEK_FORWARD_LARGE -> "Uzun ileri sar"
            SEEK_BACKWARD_PRECISE -> "Hassas geri sar"
            SEEK_FORWARD_PRECISE -> "Hassas ileri sar"
            SEEK_TO_START -> "Başa dön"
            VOLUME_UP -> "Sesi artır"
            VOLUME_DOWN -> "Sesi azalt"
            VOLUME_BOOST_UP -> "Ses güçlendirmeyi artır"
            VOLUME_BOOST_DOWN -> "Ses güçlendirmeyi azalt"
            TOGGLE_MUTE -> "Sessize al / aç"
            SPEED_UP -> "Hızı artır"
            SPEED_DOWN -> "Hızı azalt"
            SPEED_RESET -> "Hızı normale döndür"
            SPEED_HALVE -> "Hızı yarıla"
            SPEED_DOUBLE -> "Hızı ikiye katla"
            CYCLE_SUBTITLE -> "Sonraki altyazı"
            TOGGLE_SUBTITLE_VISIBILITY -> "Altyazıyı göster / gizle"
            SUBTITLE_DELAY_DOWN -> "Altyazı gecikmesini azalt"
            SUBTITLE_DELAY_UP -> "Altyazı gecikmesini artır"
            CYCLE_AUDIO -> "Ses parçasını değiştir"
            NEXT_EPISODE -> "Sonraki bölüm"
            PREVIOUS_EPISODE -> "Önceki bölüm"
            NEXT_CHAPTER -> "Sonraki bölüm işareti"
            PREVIOUS_CHAPTER -> "Önceki bölüm işareti"
            CYCLE_ASPECT -> "Görüntü oranını değiştir"
            TOGGLE_ORIENTATION -> "Ekran yönünü değiştir"
            TOGGLE_STATS -> "İstatistikleri göster / gizle"
            SCREENSHOT -> "Ekran görüntüsü al"
            OPEN_SETTINGS -> "Oynatıcı ayarları"
            TOGGLE_HELP -> "Klavye kısayolları yardımı"
            TOGGLE_UI -> "Arayüzü göster / gizle"
            LOCK_TOGGLE -> "Kontrolleri kilitle"
            EXIT -> "Oynatıcıdan çık"
        }

    /** Yardım ekranındaki gruplama. */
    val group: PlayerShortcutGroup
        get() = when (this) {
            PLAY_PAUSE, TOGGLE_UI -> PlayerShortcutGroup.PLAYBACK
            SEEK_BACKWARD, SEEK_FORWARD, SEEK_BACKWARD_LARGE, SEEK_FORWARD_LARGE,
            SEEK_BACKWARD_PRECISE, SEEK_FORWARD_PRECISE, SEEK_TO_START -> PlayerShortcutGroup.SEEK
            VOLUME_UP, VOLUME_DOWN, VOLUME_BOOST_UP, VOLUME_BOOST_DOWN, TOGGLE_MUTE ->
                PlayerShortcutGroup.VOLUME
            SPEED_UP, SPEED_DOWN, SPEED_RESET, SPEED_HALVE, SPEED_DOUBLE -> PlayerShortcutGroup.SPEED
            CYCLE_SUBTITLE, TOGGLE_SUBTITLE_VISIBILITY, SUBTITLE_DELAY_DOWN, SUBTITLE_DELAY_UP,
            CYCLE_AUDIO -> PlayerShortcutGroup.TRACKS
            NEXT_EPISODE, PREVIOUS_EPISODE, NEXT_CHAPTER, PREVIOUS_CHAPTER ->
                PlayerShortcutGroup.EPISODES
            CYCLE_ASPECT, TOGGLE_ORIENTATION, TOGGLE_STATS, SCREENSHOT, OPEN_SETTINGS,
            TOGGLE_HELP, LOCK_TOGGLE -> PlayerShortcutGroup.VIEW
            EXIT -> PlayerShortcutGroup.SYSTEM
        }
}

/** Yardım ekranı grupları (mpv `input.conf` başlıklarına benzer). */
enum class PlayerShortcutGroup(val title: String) {
    PLAYBACK("Oynatma"),
    SEEK("Arama"),
    VOLUME("Ses"),
    SPEED("Hız"),
    TRACKS("Parçalar"),
    EPISODES("Bölümler"),
    VIEW("Görünüm & Araçlar"),
    SYSTEM("Sistem")
}

/**
 * Tek bir tuş bileşimi: hold edilen değiştiriciler + tuş.
 *
 * [key] Compose `Key` nesnesi yerine `android.view.KeyEvent.keyCode` tutar; böylece
 * hem Compose ön-işleme (onPreviewKeyEvent) hem de Activity `dispatchKeyEvent`
 * katmanı aynı tabloyu kullanabilir. Gamepad tuşları da buraya sığar
 * (`KEYCODE_BUTTON_A`, `KEYCODE_BUTTON_L1`, medya tuşları…).
 *
 * [shift]/[ctrl]/[alt] yalnızca gerçekten ayırt edici olduklarında `true` yapılır;
 * eşleştirme sırasında "basılı olması gerekmiyor" (false) anlamına gelir.
 */
data class PlayerKeyBinding(
    val keyCode: Int,
    val shift: Boolean = false,
    val ctrl: Boolean = false,
    val alt: Boolean = false
) {
    /** Harf/rakam/sembol tuşu mu? (fiziksel klavye algılaması için) */
    val isAlphaNumericKey: Boolean
        get() = (keyCode in android.view.KeyEvent.KEYCODE_A..android.view.KeyEvent.KEYCODE_Z) ||
            (keyCode in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9) ||
            keyCode in setOf(
                android.view.KeyEvent.KEYCODE_SPACE,
                android.view.KeyEvent.KEYCODE_COMMA,
                android.view.KeyEvent.KEYCODE_PERIOD,
                android.view.KeyEvent.KEYCODE_LEFT_BRACKET,
                android.view.KeyEvent.KEYCODE_RIGHT_BRACKET,
                android.view.KeyEvent.KEYCODE_BACKSLASH,
                android.view.KeyEvent.KEYCODE_SLASH,
                android.view.KeyEvent.KEYCODE_MINUS,
                android.view.KeyEvent.KEYCODE_EQUALS,
                android.view.KeyEvent.KEYCODE_STAR,
                android.view.KeyEvent.KEYCODE_PLUS,
            )

    /**
     * Gamepad (kol) düğmesi mi?
     *
     * `KEYCODE_BUTTON_A` (96) … `KEYCODE_BUTTON_MODE` (110) **kesintisiz** bir aralıktır
     * (A,B,C,X,Y,Z,L1,R1,L2,R2,THUMBL,THUMBR,START,SELECT,MODE) — SDK kaynağında
     * doğrulandı. TV kumandasının D-pad'i (19–23) bu aralığa girmez.
     */
    val isGamepadKey: Boolean
        get() = keyCode in android.view.KeyEvent.KEYCODE_BUTTON_A..android.view.KeyEvent.KEYCODE_BUTTON_MODE

    /** Kısayolun yardım ekranındaki gösterimi, ör. "Shift + →", "Ctrl + L". */
    fun displayLabel(): String {
        val base = keyCodeLabel(keyCode)
        val parts = buildList {
            if (ctrl) add("Ctrl")
            if (alt) add("Alt")
            if (shift) add("Shift")
            add(base)
        }
        return parts.joinToString(" + ")
    }
}

/**
 * Android `KeyEvent.keyCode` → insan okunur etiket.
 *
 * Yalnızca Kitsugi'nin varsayılan olarak bağladığı tuşlar ve yaygın klavye
 * düğmeleri eşlenmiştir; bilinmeyen tuşlar için keyCode sayısal olarak gösterilir
 * (kullanıcı yine de kendi eşlemesini yapabilsin diye).
 */
fun keyCodeLabel(keyCode: Int): String = when (keyCode) {
    android.view.KeyEvent.KEYCODE_DPAD_LEFT -> "←"
    android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> "→"
    android.view.KeyEvent.KEYCODE_DPAD_UP -> "↑"
    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> "↓"
    android.view.KeyEvent.KEYCODE_DPAD_CENTER -> "OK"
    android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> "Enter"
    android.view.KeyEvent.KEYCODE_SPACE -> "Boşluk"
    android.view.KeyEvent.KEYCODE_DEL -> "Backspace"
    android.view.KeyEvent.KEYCODE_FORWARD_DEL -> "Delete"
    android.view.KeyEvent.KEYCODE_COMMA -> ","
    android.view.KeyEvent.KEYCODE_PERIOD -> "."
    android.view.KeyEvent.KEYCODE_LEFT_BRACKET -> "["
    android.view.KeyEvent.KEYCODE_RIGHT_BRACKET -> "]"
    android.view.KeyEvent.KEYCODE_BACKSLASH -> "\\"
    android.view.KeyEvent.KEYCODE_SLASH -> "/"
    android.view.KeyEvent.KEYCODE_STAR -> "*"
    android.view.KeyEvent.KEYCODE_PLUS -> "+"
    // Hız yarıla / ikiye katla: Android'de `{` `}` tuş kodu yok; `-` ve `=` kullanılıyor.
    // Yardım ekranında sembolün yanına anlamı yazılır ki kullanıcı tahmin etmesin.
    android.view.KeyEvent.KEYCODE_EQUALS -> "= (hız ×2)"
    android.view.KeyEvent.KEYCODE_MINUS -> "- (hız ÷2)"
    android.view.KeyEvent.KEYCODE_APOSTROPHE -> "'"
    android.view.KeyEvent.KEYCODE_SEMICOLON -> ";"
    android.view.KeyEvent.KEYCODE_GRAVE -> "`"
    android.view.KeyEvent.KEYCODE_TAB -> "Tab"
    android.view.KeyEvent.KEYCODE_ESCAPE -> "Esc"
    android.view.KeyEvent.KEYCODE_MENU -> "Menü"
    android.view.KeyEvent.KEYCODE_PAGE_UP -> "PgUp"
    android.view.KeyEvent.KEYCODE_PAGE_DOWN -> "PgDn"
    android.view.KeyEvent.KEYCODE_MOVE_HOME -> "Home"
    android.view.KeyEvent.KEYCODE_MOVE_END -> "End"
    android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "Medya Oynat/Duraklat"
    android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> "Medya Oynat"
    android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> "Medya Duraklat"
    android.view.KeyEvent.KEYCODE_MEDIA_STOP -> "Medya Durdur"
    android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> "Medya Sonraki"
    android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "Medya Önceki"
    android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "Medya İleri"
    android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> "Medya Geri"
    android.view.KeyEvent.KEYCODE_VOLUME_UP -> "Ses +"
    android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> "Ses −"
    android.view.KeyEvent.KEYCODE_BUTTON_A -> "Gamepad A"
    android.view.KeyEvent.KEYCODE_BUTTON_B -> "Gamepad B"
    android.view.KeyEvent.KEYCODE_BUTTON_X -> "Gamepad X"
    android.view.KeyEvent.KEYCODE_BUTTON_Y -> "Gamepad Y"
    android.view.KeyEvent.KEYCODE_BUTTON_L1 -> "Gamepad L1"
    android.view.KeyEvent.KEYCODE_BUTTON_R1 -> "Gamepad R1"
    android.view.KeyEvent.KEYCODE_BUTTON_L2 -> "Gamepad L2"
    android.view.KeyEvent.KEYCODE_BUTTON_R2 -> "Gamepad R2"
    android.view.KeyEvent.KEYCODE_BUTTON_START -> "Gamepad Start"
    android.view.KeyEvent.KEYCODE_BUTTON_SELECT -> "Gamepad Select"
    android.view.KeyEvent.KEYCODE_BUTTON_THUMBL -> "Gamepad Sol Çubuk"
    android.view.KeyEvent.KEYCODE_BUTTON_THUMBR -> "Gamepad Sağ Çubuk"
    android.view.KeyEvent.KEYCODE_STB_INPUT -> "Kaynak"
    android.view.KeyEvent.KEYCODE_HEADSETHOOK -> "Kulaklık düğmesi"
    else -> {
        if (keyCode in android.view.KeyEvent.KEYCODE_F1..android.view.KeyEvent.KEYCODE_F12) {
            "F${keyCode - android.view.KeyEvent.KEYCODE_F1 + 1}"
        } else if (keyCode in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9) {
            "${keyCode - android.view.KeyEvent.KEYCODE_0}"
        } else if (keyCode in android.view.KeyEvent.KEYCODE_A..android.view.KeyEvent.KEYCODE_Z) {
            // KEYCODE_A..KEYCODE_Z harflerle sıralıdır; 'q' vb. okunur görünsün.
            ('A' + (keyCode - android.view.KeyEvent.KEYCODE_A)).toString()
        } else {
            "Tuş #$keyCode"
        }
    }
}

/**
 * Kullanıcı ayarlarında saklanan tuş bileşimi.
 *
 * Neden `String` olarak saklanıyor?
 * DataStore içinde [android.view.KeyEvent.KeyEvent] nesnesi tutulamaz; `keyCode`
 * motor/cihaz sürümleri arasında da sabit kalır. Biçim: `"shift+ctrl+keycode"`,
 * ör. `"keycode66"`, `"shift+keycode22"`. Bilinmeyen/bozuk değerler sessizce
 * varsayılana düşer — ayar dosyası elle düzenlense bile oynatıcı açılmaya devam eder.
 */
object PlayerKeyBindingCodec {
    fun encode(binding: PlayerKeyBinding): String = buildString {
        if (binding.shift) append("shift+")
        if (binding.ctrl) append("ctrl+")
        if (binding.alt) append("alt+")
        append("keycode").append(binding.keyCode)
    }

    fun decode(raw: String?): PlayerKeyBinding? {
        if (raw.isNullOrBlank()) return null
        var shift = false
        var ctrl = false
        var alt = false
        val keyCode = raw.split('+')
            .map { it.trim().lowercase() }
            .firstNotNullOfOrNull { part ->
                when (part) {
                    "shift" -> { shift = true; null }
                    "ctrl" -> { ctrl = true; null }
                    "alt" -> { alt = true; null }
                    else -> part.removePrefix("keycode").toIntOrNull()
                }
            } ?: return null
        if (keyCode <= 0) return null
        return PlayerKeyBinding(keyCode = keyCode, shift = shift, ctrl = ctrl, alt = alt)
    }
}
