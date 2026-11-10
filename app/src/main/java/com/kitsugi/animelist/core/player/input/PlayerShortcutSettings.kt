package com.kitsugi.animelist.core.player.input

/**
 * Kullanıcı ayarlarının `AppSettings` içindeki düz metin alanlarını, oynatıcının
 * kullanacağı tabloya çeviren küçük yardımcı.
 *
 * Neden ayrı?
 * Aynı çözümleme üç yerde gerekiyor (oynatıcı ekranı, kısayol yardım ekranı, ayar
 * arayüzünün ön izlemesi). Tek yerde tutmak, bozuk bir ayar metninin oynatıcıyı
 * kilitlemesini de önlüyor: ayrıştırılamayan satırlar **atlanır**, kalanlar çalışır.
 */
object PlayerShortcutSettings {

    /** Kullanıcının kapattığı eylemler: `"EXIT,SCREENSHOT"` → Set. */
    fun parseDisabled(raw: String?): Set<PlayerShortcutAction> {
        if (raw.isNullOrBlank()) return emptySet()
        return raw.split(',', ';', '|')
            .mapNotNull { token ->
                val name = token.trim().uppercase()
                if (name.isEmpty()) null
                else PlayerShortcutAction.entries.firstOrNull { it.name == name }
            }
            .toSet()
    }

    fun encodeDisabled(actions: Set<PlayerShortcutAction>): String =
        actions.sortedBy { it.ordinal }.joinToString(",") { it.name }

    /**
     * Kullanıcı eşlemelerini ayrıştırır.
     *
     * Biçim: `"keycode66=PLAY_PAUSE,shift+keycode22=SEEK_FORWARD_PRECISE"`
     *
     * - `=` ile ayrılmış iki parça: sol tuş bileşimi, sağ eylem adı.
     * - Bilinmeyen eylem adları, çözülemeyen tuş kodları ve yinelenen tuşlar atlanır.
     * - Aynı tuşa iki eylem yazıldıysa **sonuncusu** kazanır (kullanıcı düzeltmesi).
     */
    fun parseOverrides(raw: String?): Map<PlayerKeyBinding, PlayerShortcutAction> {
        if (raw.isNullOrBlank()) return emptyMap()
        val result = LinkedHashMap<PlayerKeyBinding, PlayerShortcutAction>()
        raw.split(',', ';', '\n').forEach { entry ->
            val trimmed = entry.trim()
            if (trimmed.isEmpty()) return@forEach
            val splitAt = trimmed.indexOf('=')
            if (splitAt <= 0) return@forEach
            val binding = PlayerKeyBindingCodec.decode(trimmed.substring(0, splitAt))
            val action = PlayerShortcutAction.entries
                .firstOrNull { it.name.equals(trimmed.substring(splitAt + 1).trim(), ignoreCase = true) }
            if (binding != null && action != null) result[binding] = action
        }
        return result
    }

    fun encodeOverrides(overrides: Map<PlayerKeyBinding, PlayerShortcutAction>): String =
        overrides.entries.joinToString(",") { (binding, action) ->
            "${PlayerKeyBindingCodec.encode(binding)}=${action.name}"
        }

    /** Ayar metinlerinden nihai tuş tablosu. */
    fun buildTable(disabledRaw: String?, overridesRaw: String?): Map<PlayerKeyBinding, PlayerShortcutAction> =
        PlayerShortcutMap.resolve(
            overrides = parseOverrides(overridesRaw),
            disabled = parseDisabled(disabledRaw)
        )

    /**
     * Hazır ön ayarlar — kullanıcı sıfırdan eşleme yapmak zorunda kalmasın.
     *
     * - [DEFAULT] mpv/Just Player karışımı (yukarıdaki varsayılan tablo).
     * - [VLC] VLC masaüstü alışkanlıkları: Enter = tam ekran değil, `f` = tam ekran,
     *   `m` = sessiz, ↑/↓ = ses (arama yerine). Bu yüzden okları yeniden bağlarız.
     * - [MINIMAL] yalnızca oynatma + arama + ses: harici klavyeyi yalnızca "uzaktan
     *   kumanda" gibi kullananlar için; hız/parça/araç kısayolları kapalı.
     */
    enum class Preset(val label: String) {
        DEFAULT("Varsayılan (mpv uyumlu)"),
        VLC("VLC tarzı"),
        MINIMAL("Sade (yalnızca temel kontroller)")
    }

    fun applyPreset(preset: Preset): Pair<String, String> = when (preset) {
        Preset.DEFAULT -> "" to ""

        Preset.VLC -> "" to encodeOverrides(
            mapOf(
                PlayerKeyBinding(android.view.KeyEvent.KEYCODE_DPAD_UP) to PlayerShortcutAction.VOLUME_UP,
                PlayerKeyBinding(android.view.KeyEvent.KEYCODE_DPAD_DOWN) to PlayerShortcutAction.VOLUME_DOWN,
                PlayerKeyBinding(android.view.KeyEvent.KEYCODE_DPAD_LEFT) to PlayerShortcutAction.SEEK_BACKWARD,
                PlayerKeyBinding(android.view.KeyEvent.KEYCODE_DPAD_RIGHT) to PlayerShortcutAction.SEEK_FORWARD,
                PlayerKeyBinding(android.view.KeyEvent.KEYCODE_SPACE) to PlayerShortcutAction.PLAY_PAUSE,
            )
        )

        Preset.MINIMAL -> encodeDisabled(
            setOf(
                PlayerShortcutAction.SPEED_HALVE,
                PlayerShortcutAction.SPEED_DOUBLE,
                PlayerShortcutAction.SUBTITLE_DELAY_UP,
                PlayerShortcutAction.SUBTITLE_DELAY_DOWN,
                PlayerShortcutAction.TOGGLE_STATS,
                PlayerShortcutAction.CYCLE_ASPECT,
                PlayerShortcutAction.TOGGLE_ORIENTATION,
                PlayerShortcutAction.SCREENSHOT,
                PlayerShortcutAction.OPEN_SETTINGS,
            )
        ) to ""
    }
}
