package com.kitsugi.animelist.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * EMOJİ → HD İKON EŞLEMESİ
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Uygulamada tür/ülke/kaynak etiketleri veri katmanından **emoji ile** geliyor
 * (ör. `"🤖 Mecha"`, `"🔥 Popüler Türler"`). Bu emojiler tıklanır çiplerin
 * görsel ikonu olarak kullanıldığı için, çizim sırasında HD vektör karşılığına
 * çevrilir. Eşlemesi olmayan emojiler (ör. ülke bayrakları) metin olarak kalır.
 *
 * Tek kaynak: burada tanımlı. Eşleme değişirse tüm ekranlar birlikte değişir.
 */
object KitsugiEmojiIcons {

    private val mapping: Map<String, ImageVector> = mapOf(
        // ── Türler ──────────────────────────────────────────────────────────
        "🤖" to KitsugiIcons.Bot,
        "🚀" to KitsugiIcons.Rocket,
        "⚽" to KitsugiIcons.Dumbbell,
        "🧠" to KitsugiIcons.Brain,
        "✨" to KitsugiIcons.Sparkles,
        "🎵" to KitsugiIcons.AudioLines,
        "🏫" to KitsugiIcons.School,
        "🏯" to KitsugiIcons.Castle,
        "👻" to KitsugiIcons.Ghost,
        "🔥" to KitsugiIcons.Flame,
        "🗡️" to KitsugiIcons.Swords,
        "⚔️" to KitsugiIcons.Swords,
        "🎭" to KitsugiIcons.Theater,
        "👾" to KitsugiIcons.Gamepad2,
        "🎮" to KitsugiIcons.Gamepad,
        "🌸" to KitsugiIcons.Flower,
        "🌙" to KitsugiIcons.Moon,
        "🌐" to KitsugiIcons.Globe,
        "📖" to KitsugiIcons.BookOpen,
        "🧑" to KitsugiIcons.User,
        "👶" to KitsugiIcons.Baby,
        "❤️" to KitsugiIcons.HeartSolid,
        "⭐" to KitsugiIcons.StarSolid,
        "🏆" to KitsugiIcons.Trophy,
        "🎖️" to KitsugiIcons.Medal,
        "🎬" to KitsugiIcons.Clapperboard,
        "📽️" to KitsugiIcons.Film,
        "🍿" to KitsugiIcons.Popcorn,
        "📺" to KitsugiIcons.Tv,
        "📦" to KitsugiIcons.Package,
        "📱" to KitsugiIcons.Smartphone,
        "📁" to KitsugiIcons.Folder,
        "🔍" to KitsugiIcons.Search,
        // ── Sıralama / filtre etiket glifleri ───────────────────────────────
        "📅" to KitsugiIcons.CalendarDays,
        "🗓️" to KitsugiIcons.CalendarRange,
        "👥" to KitsugiIcons.Users,
        "🔤" to KitsugiIcons.ArrowDownAZ,
        "🎯" to KitsugiIcons.Target,
        "🦊" to KitsugiIcons.Dog,
        "💖" to KitsugiIcons.Heart,
        "💕" to KitsugiIcons.Heart,
        "👤" to KitsugiIcons.UserRound,
        "🎙️" to KitsugiIcons.Mic,
        "💰" to KitsugiIcons.Banknote,
        "💿" to KitsugiIcons.Disc,
        "🎉" to KitsugiIcons.PartyPopper,
        "🅰️" to KitsugiIcons.Type,
        "Ⓜ️" to KitsugiIcons.Circle,
        "🌟" to KitsugiIcons.StarSolid,
        "🎨" to KitsugiIcons.Palette,
        "🖼️" to KitsugiIcons.Image,
        "📋" to KitsugiIcons.ClipboardList,
        "🟩" to KitsugiIcons.Square,
        "🎫" to KitsugiIcons.Ticket,
        "🎥" to KitsugiIcons.Video,
        "📰" to KitsugiIcons.Newspaper,
        "🔖" to KitsugiIcons.Bookmark,
        "🔢" to KitsugiIcons.Hash,
        "🆕" to KitsugiIcons.BadgePlus,
        "⏱️" to KitsugiIcons.Timer,
        "🧒" to KitsugiIcons.Baby,
        "📜" to KitsugiIcons.Scroll,
        "📂" to KitsugiIcons.FolderOpen,
        "❄️" to KitsugiIcons.Snowflake,
        "🌱" to KitsugiIcons.Sprout,
        "☀️" to KitsugiIcons.Sun,
        "🍂" to KitsugiIcons.Leaf,
        "🫰" to KitsugiIcons.HandHeart,
        "🏢" to KitsugiIcons.Building,
        "🎲" to KitsugiIcons.Dices,
        "📊" to KitsugiIcons.ChartColumn,
        "🏷️" to KitsugiIcons.Tag,
        "💬" to KitsugiIcons.MessageCircle,
        "🎌" to KitsugiIcons.Flag,
        "📡" to KitsugiIcons.Satellite,
        "🖼" to KitsugiIcons.Image,
        "✕" to KitsugiIcons.X,
        "🔒" to KitsugiIcons.Lock,
        "🕐" to KitsugiIcons.Clock,
        "🏠" to KitsugiIcons.Home,
        "📵" to KitsugiIcons.CloudOff,
        "🏁" to KitsugiIcons.Flag,
        // ── Durum / rozet glifleri ──────────────────────────────────────────
        "✅" to KitsugiIcons.CircleCheck,
        "☑️" to KitsugiIcons.CircleCheck,
        "✓" to KitsugiIcons.Check,
        "✔" to KitsugiIcons.Check,
        "✗" to KitsugiIcons.X,
        "❌" to KitsugiIcons.CircleX,
        "⚠️" to KitsugiIcons.AlertTriangle,
        "ℹ️" to KitsugiIcons.Info,
        "🔐" to KitsugiIcons.Lock,
        "🔎" to KitsugiIcons.Search,
        "🧩" to KitsugiIcons.Puzzle,
        "🔑" to KitsugiIcons.Key,
        "🔁" to KitsugiIcons.RefreshCw,
        "⏸️" to KitsugiIcons.Pause,
        "🧹" to KitsugiIcons.Brush,
        "💾" to KitsugiIcons.Save,
        "🔊" to KitsugiIcons.Volume2,
        "🔗" to KitsugiIcons.Link,
        "📭" to KitsugiIcons.MailOpen,
        "📚" to KitsugiIcons.BookOpen,
        "★" to KitsugiIcons.StarSolid,
        "☆" to KitsugiIcons.StarStroke,
        "❤" to KitsugiIcons.HeartSolid,
        "😊" to KitsugiIcons.Smile,
        "🙁" to KitsugiIcons.Frown,
        "😐" to KitsugiIcons.Meh,
        "▶" to KitsugiIcons.Play,
        "▾" to KitsugiIcons.ChevronDown,
        "⬆️" to KitsugiIcons.ArrowUp,
        "⬇️" to KitsugiIcons.ArrowDown,
        "☁️" to KitsugiIcons.Cloud,
        "⚡" to KitsugiIcons.Zap,
        "🚫" to KitsugiIcons.Ban,
        "⌛" to KitsugiIcons.Hourglass,
        "⏳" to KitsugiIcons.Hourglass,
        "🕵️" to KitsugiIcons.VenetianMask,
        "🧭" to KitsugiIcons.Compass,
        "✈️" to KitsugiIcons.Plane,
        "🏋️" to KitsugiIcons.Dumbbell,
        "💗" to KitsugiIcons.HeartPulse,
        "⛪" to KitsugiIcons.Church,
        "🏛️" to KitsugiIcons.Landmark,
        "😄" to KitsugiIcons.Smile,
        "😂" to KitsugiIcons.Laugh,
        "😢" to KitsugiIcons.Frown,
        "😠" to KitsugiIcons.Angry,
        "🔞" to KitsugiIcons.CircleAlert,
        "🟢" to KitsugiIcons.CircleDot,
        "🧑‍🍳" to KitsugiIcons.ChefHat,
        "👑" to KitsugiIcons.Crown,
        "🪄" to KitsugiIcons.WandSparkles,
        "🌩️" to KitsugiIcons.CloudLightning,
        "▶️" to KitsugiIcons.CirclePlay
    )

    /**
     * Etiketin başındaki emojiyi (varsa) HD ikona çevirir.
     * `"🤖 Mecha"` → Bot ikonu; eşleme yoksa `null` (metin olduğu gibi çizilir).
     */
    fun forLabel(label: String): ImageVector? = mapping.entries
        .firstOrNull { label.startsWith(it.key) }
        ?.value

    /** Yalnızca emoji içeren etiketler için doğrudan eşleme. */
    fun forEmoji(emoji: String): ImageVector? = mapping[emoji]

    /** Etiketten baştaki emoji(ler)i (ve boşlukları) kaldırır. */
    fun stripLeadingEmoji(label: String): String {
        var out = label
        while (true) {
            val hit = mapping.keys.firstOrNull { out.startsWith(it) } ?: break
            out = out.removePrefix(hit).trimStart()
        }
        return out
    }
}
