package com.kitsugi.animelist.core.player.input

import android.view.KeyEvent

/**
 * Kitsugi oynatıcısının varsayılan klavye haritası + kullanıcı geçersiz kılmaları.
 *
 * Referans alınan açık kaynak oynatıcılar: **mpv** (`etc/input.conf`), **VLC**,
 * **Just Player** (`PlayerActivity.onKeyDown`), **mpv-android** (`MPVView.kt`).
 *
 * | Tuş                 | Kitsugi davranışı         | Kaynak / gerekçe                          |
 * |---------------------|---------------------------|-------------------------------------------|
 * | Boşluk / Enter / OK | Oynat / duraklat          | mpv `SPACE cycle pause`, Just Player      |
 * | ← / →               | ±5 sn                     | mpv varsayılanı                           |
 * | ↑ / ↓               | ±60 sn                    | mpv varsayılanı                           |
 * | Shift + ← / →       | ±1 sn hassas              | mpv `shift+LEFT seek -1 exact`            |
 * | Ctrl + ← / →        | ±1 sn hassas              | aynı davranışın ikinci kısayolu           |
 * | , / .               | ±1 sn hassas              | mpv kare adımının Kitsugi karşılığı       |
 * | Home                | Başa dön                  | mpv `HOME seek 0`                         |
 * | PgUp / PgDn         | Önceki / sonraki bölüm    | mpv `playlist-prev` / `playlist-next`     |
 * | Shift + PgUp / PgDn | Bölüm işareti             | mpv chapter gezinmesi                     |
 * | `[` / `]`           | Hız ∓ / ± %10             | mpv `add speed -0.1` / `0.1`              |
 * | `-` / `=`           | Hızı yarıla / ikiye katla | mpv `{` / `}` (Android'de tuş kodu yok)   |
 * | `\`                 | Hızı 1× yap               | "speed reset" — mpv `Backspace` eşleniği  |
 * | m                   | Sessize al / aç           | mpv `cycle mute`                          |
 * | 9 / 0               | Ses güçlendirme ∓ / ±     | mpv `add volume -1` / `1` (app içi)       |
 * | j                   | Sonraki altyazı parçası   | mpv `cycle sub`                           |
 * | v                   | Altyazı görünürlüğü       | mpv `cycle sub-visibility`                |
 * | z / Shift + z       | Altyazı gecikmesi ∓ 100ms | mpv `add sub-delay ∓0.1`                  |
 * | y                   | Ses parçası döngüsü       | mpv `cycle audio`                         |
 * | a                   | Görüntü oranı döngüsü     | mpv `cycle aspect`                        |
 * | f                   | Ekran yönü değiştir       | mobil karşılığı (mpv'de tam ekran)        |
 * | i                   | İstatistikler             | mpv `stats` script                        |
 * | s                   | Ekran görüntüsü           | mpv `screenshot`                          |
 * | c                   | Oynatıcı ayarları         | Just Player `settings` düğmesi            |
 * | o / Tab             | Arayüzü göster / gizle    | mpv `show-progress` OSD karşılığı         |
 * | l                   | Kontrolleri kilitle       | Aniyomi kilit düğmesi                     |
 * | ? / h               | Kısayol yardımı           | YouTube / VLC "?" yardım ekranı           |
 * | Esc / q / Medya Durdur | Oynatıcıdan çık (onaylı) | mpv `ESC quit` — Kitsugi'de onay ister   |
 * | Gamepad A / B       | Oynat-duraklat / çık      | Just Player, mpv-android                  |
 * | Gamepad L1 / R1     | Bölüm işareti             | mpv chapter gezinmesi                     |
 * | Gamepad L2 / R2     | ±5 sn sar                 | Just Player tetik davranışı                |
 *
 * Android'de **bulunmayan** mpv tuşları ve Kitsugi'nin seçtiği karşılıklar
 * ────────────────────────────────────────────────────────────────────────
 *  - `{` / `}` → `KEYCODE_LEFT_BRACE`/`RIGHT_BRACE` Android SDK'da **yok**; `-` / `=`.
 *  - `#` → `KEYCODE_NUMBER_SIGN` **yok** (yalnızca `POUND` telefon tuş takımı); `y`.
 *  - `Backspace` → `KEYCODE_BACKSPACE` **yok**, doğrusu `KEYCODE_DEL`; `\` tercih edildi
 *    çünkü fiziksel klavyede `Backspace`'e basmak Android'de metin silme/bottom-sheet
 *    davranışını tetikleyebiliyor.
 *  - Kare kare adım (`frame-step`) → iki motorun da API'si yok; 1 sn'lik hassas arama.
 *
 * Neden `q` doğrudan kapatmıyor?
 * Harici klavyede tek bir harf basımıyla oturumu kapatmak mpv'nin masaüstü
 * alışkanlığıdır; mobil uygulamada istemeden izlemeyi bitirir. Kitsugi'de `Esc`
 * arayüzü kapatır, kısayol katmanında çıkış her zaman onay ister
 * (bkz. [requiresConfirmation]).
 *
 * Öncelik kuralları
 * ─────────────────
 *  - Kullanıcı kendi eşlemesini yaptıysa (DataStore), o eşleme varsayılanı **ezer**.
 *  - Bir tuş iki eyleme bağlıysa kullanıcı eşlemesi kazanır.
 *  - Oynatıcı arayüzü görünürken (OSD açık) D-pad/Enter gezinme için ayrılır;
 *    bunu [PlayerKeyRouter] değil, oynatıcı ekranı (`onPreviewKeyEvent`) yönetir.
 */
object PlayerShortcutMap {

    /** Varsayılan eşlemeler. Aynı tuşun birden çok eylemi olabilir; son giren kazanır. */
    val defaults: Map<PlayerKeyBinding, PlayerShortcutAction> = buildMap {
        fun bind(action: PlayerShortcutAction, keyCode: Int, shift: Boolean = false, ctrl: Boolean = false, alt: Boolean = false) {
            put(PlayerKeyBinding(keyCode, shift = shift, ctrl = ctrl, alt = alt), action)
        }

        // — Oynatma —
        bind(PlayerShortcutAction.PLAY_PAUSE, KeyEvent.KEYCODE_SPACE)
        bind(PlayerShortcutAction.PLAY_PAUSE, KeyEvent.KEYCODE_ENTER)
        bind(PlayerShortcutAction.PLAY_PAUSE, KeyEvent.KEYCODE_NUMPAD_ENTER)
        bind(PlayerShortcutAction.PLAY_PAUSE, KeyEvent.KEYCODE_DPAD_CENTER)
        bind(PlayerShortcutAction.PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        bind(PlayerShortcutAction.PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK)
        bind(PlayerShortcutAction.PLAY_PAUSE, KeyEvent.KEYCODE_BUTTON_A)
        bind(PlayerShortcutAction.TOGGLE_UI, KeyEvent.KEYCODE_O)

        // — Arama (mpv varsayılanları) —
        bind(PlayerShortcutAction.SEEK_BACKWARD, KeyEvent.KEYCODE_DPAD_LEFT)
        bind(PlayerShortcutAction.SEEK_FORWARD, KeyEvent.KEYCODE_DPAD_RIGHT)
        bind(PlayerShortcutAction.SEEK_BACKWARD_LARGE, KeyEvent.KEYCODE_DPAD_DOWN)
        bind(PlayerShortcutAction.SEEK_FORWARD_LARGE, KeyEvent.KEYCODE_DPAD_UP)
        bind(PlayerShortcutAction.SEEK_BACKWARD_PRECISE, KeyEvent.KEYCODE_DPAD_LEFT, shift = true)
        bind(PlayerShortcutAction.SEEK_FORWARD_PRECISE, KeyEvent.KEYCODE_DPAD_RIGHT, shift = true)
        // Ctrl + ok: mpv'nin Shift+ok "exact seek" hissi — kare hassasiyetine yakın 1 sn.
        bind(PlayerShortcutAction.SEEK_BACKWARD_PRECISE, KeyEvent.KEYCODE_DPAD_LEFT, ctrl = true)
        bind(PlayerShortcutAction.SEEK_FORWARD_PRECISE, KeyEvent.KEYCODE_DPAD_RIGHT, ctrl = true)
        bind(PlayerShortcutAction.SEEK_BACKWARD, KeyEvent.KEYCODE_MEDIA_REWIND)
        bind(PlayerShortcutAction.SEEK_FORWARD, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
        bind(PlayerShortcutAction.SEEK_BACKWARD_PRECISE, KeyEvent.KEYCODE_COMMA)   // mpv kare adımı karşılığı
        bind(PlayerShortcutAction.SEEK_FORWARD_PRECISE, KeyEvent.KEYCODE_PERIOD)  // mpv kare adımı karşılığı
        bind(PlayerShortcutAction.SEEK_TO_START, KeyEvent.KEYCODE_MOVE_HOME)

        // — Ses —
        // NOT: Donanım ses tuşları (KEYCODE_VOLUME_UP/DOWN) bilinçli olarak BAĞLANMADI.
        // Bunları yakalamak sistem ses kontrolünü devre dışı bırakırdı (mpv de aynı
        // tuşları sistem bırakır). Uygulama içi ses için 9 / 0 (volume boost) ve
        // m (sessiz) kullanılır.
        bind(PlayerShortcutAction.TOGGLE_MUTE, KeyEvent.KEYCODE_M)
        bind(PlayerShortcutAction.VOLUME_BOOST_DOWN, KeyEvent.KEYCODE_9)
        bind(PlayerShortcutAction.VOLUME_BOOST_UP, KeyEvent.KEYCODE_0)

        // — Hız (mpv) —
        bind(PlayerShortcutAction.SPEED_DOWN, KeyEvent.KEYCODE_LEFT_BRACKET)
        bind(PlayerShortcutAction.SPEED_UP, KeyEvent.KEYCODE_RIGHT_BRACKET)
        // mpv `{` / `}` (hızı yarıla / ikiye katla) Android'de ayrı tuş kodu YOKTUR.
        // Shift+, ve Shift+. kombinasyonları ile '-' / '=' tuşlarına bağlandı:
        // klavye düzeninden bağımsız, her tuş takımında fiziksel olarak bulunurlar.
        bind(PlayerShortcutAction.SPEED_HALVE, KeyEvent.KEYCODE_MINUS)
        bind(PlayerShortcutAction.SPEED_DOUBLE, KeyEvent.KEYCODE_EQUALS)
        bind(PlayerShortcutAction.SPEED_RESET, KeyEvent.KEYCODE_BACKSLASH)

        // — Parçalar —
        bind(PlayerShortcutAction.CYCLE_SUBTITLE, KeyEvent.KEYCODE_J)
        bind(PlayerShortcutAction.TOGGLE_SUBTITLE_VISIBILITY, KeyEvent.KEYCODE_V)
        bind(PlayerShortcutAction.SUBTITLE_DELAY_DOWN, KeyEvent.KEYCODE_Z)
        bind(PlayerShortcutAction.SUBTITLE_DELAY_UP, KeyEvent.KEYCODE_Z, shift = true)
        // mpv `#` Android'de tuş kodu olarak yok (POUND/STAR telefon tuş takımı).
        // `y` (mpv `Shift+a` karşılığı) tek başına audio parça döngüsü.
        bind(PlayerShortcutAction.CYCLE_AUDIO, KeyEvent.KEYCODE_Y)

        // — Bölümler (mpv playlist/chapter) —
        bind(PlayerShortcutAction.NEXT_EPISODE, KeyEvent.KEYCODE_PAGE_DOWN)
        bind(PlayerShortcutAction.PREVIOUS_EPISODE, KeyEvent.KEYCODE_PAGE_UP)
        bind(PlayerShortcutAction.NEXT_EPISODE, KeyEvent.KEYCODE_N)
        bind(PlayerShortcutAction.PREVIOUS_EPISODE, KeyEvent.KEYCODE_N, shift = true)
        bind(PlayerShortcutAction.NEXT_EPISODE, KeyEvent.KEYCODE_MEDIA_NEXT)
        bind(PlayerShortcutAction.PREVIOUS_EPISODE, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        bind(PlayerShortcutAction.NEXT_CHAPTER, KeyEvent.KEYCODE_PAGE_DOWN, shift = true)
        bind(PlayerShortcutAction.PREVIOUS_CHAPTER, KeyEvent.KEYCODE_PAGE_UP, shift = true)
        // Gamepad: omuz tuşları bölüm işaretleri arasında atlar (mpv PgUp/PgDn),
        // tetikler (L2/R2) Just Player gibi ileri/geri sarar.
        bind(PlayerShortcutAction.NEXT_CHAPTER, KeyEvent.KEYCODE_BUTTON_R1)
        bind(PlayerShortcutAction.PREVIOUS_CHAPTER, KeyEvent.KEYCODE_BUTTON_L1)
        bind(PlayerShortcutAction.SEEK_FORWARD, KeyEvent.KEYCODE_BUTTON_R2)
        bind(PlayerShortcutAction.SEEK_BACKWARD, KeyEvent.KEYCODE_BUTTON_L2)

        // — Görünüm & araçlar —
        bind(PlayerShortcutAction.CYCLE_ASPECT, KeyEvent.KEYCODE_A)
        bind(PlayerShortcutAction.TOGGLE_ORIENTATION, KeyEvent.KEYCODE_F)
        bind(PlayerShortcutAction.TOGGLE_STATS, KeyEvent.KEYCODE_I)
        bind(PlayerShortcutAction.SCREENSHOT, KeyEvent.KEYCODE_S)
        bind(PlayerShortcutAction.OPEN_SETTINGS, KeyEvent.KEYCODE_C)
        bind(PlayerShortcutAction.TOGGLE_HELP, KeyEvent.KEYCODE_SLASH)
        bind(PlayerShortcutAction.TOGGLE_HELP, KeyEvent.KEYCODE_H)
        bind(PlayerShortcutAction.LOCK_TOGGLE, KeyEvent.KEYCODE_L)
        bind(PlayerShortcutAction.TOGGLE_UI, KeyEvent.KEYCODE_TAB)

        // — Sistem —
        bind(PlayerShortcutAction.EXIT, KeyEvent.KEYCODE_ESCAPE)
        bind(PlayerShortcutAction.EXIT, KeyEvent.KEYCODE_Q)
        bind(PlayerShortcutAction.EXIT, KeyEvent.KEYCODE_BUTTON_B)
        bind(PlayerShortcutAction.EXIT, KeyEvent.KEYCODE_MEDIA_STOP)
    }

    /**
     * Çıkış gibi yıkıcı eylemler: tek basışta uygulanmaz, onay ister.
     * mpv'nin aksine Kitsugi mobilde yanlış tuşla oturum kaybettirmez.
     */
    fun requiresConfirmation(action: PlayerShortcutAction): Boolean =
        action == PlayerShortcutAction.EXIT

    /**
     * Etkin eşlemeyi üretir.
     *
     * @param overrides kullanıcı eşlemeleri (`PlayerKeyBinding` → eylem). Boş bırakılan
     *        eylemler varsayılanda kalır; bir tuş kullanıcı tarafından başka eyleme
     *        bağlandıysa **varsayılan bağlamayı kaldırır** (çifte tetikleme olmaz).
     * @param disabled eylemler: kullanıcı bu kısayolu kapattıysa tuş hiçbir şey yapmaz.
     */
    fun resolve(
        overrides: Map<PlayerKeyBinding, PlayerShortcutAction> = emptyMap(),
        disabled: Set<PlayerShortcutAction> = emptySet()
    ): Map<PlayerKeyBinding, PlayerShortcutAction> {
        if (overrides.isEmpty() && disabled.isEmpty()) return defaults

        val overriddenKeys = overrides.keys
        val result = LinkedHashMap<PlayerKeyBinding, PlayerShortcutAction>()
        defaults.forEach { (binding, action) ->
            if (binding in overriddenKeys) return@forEach
            if (action in disabled) return@forEach
            result[binding] = action
        }
        overrides.forEach { (binding, action) ->
            if (action in disabled) return@forEach
            // Aynı eylem birden çok tuşa bağlı olabileceği için kullanıcı eşlemesi
            // varsayılanı yalnızca bu tuş için ezer; diğer bağlamalar korunur.
            result[binding] = action
        }
        return result
    }

    /** Bir eylem için etkin (varsa kullanıcı, yoksa varsayılan) ilk tuş. Yardım ekranı için. */
    fun primaryBindingFor(
        action: PlayerShortcutAction,
        effective: Map<PlayerKeyBinding, PlayerShortcutAction> = defaults
    ): PlayerKeyBinding? = effective.entries.firstOrNull { it.value == action }?.key

    /** Bir eyleme bağlı tüm tuşlar (yardım ekranında "← / Ctrl + ←" gibi göstermek için). */
    fun bindingsFor(
        action: PlayerShortcutAction,
        effective: Map<PlayerKeyBinding, PlayerShortcutAction> = defaults
    ): List<PlayerKeyBinding> = effective.filterValues { it == action }.keys.toList()
}
