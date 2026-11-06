package com.kitsugi.animelist.data.music

/**
 * ⏱️ Uyku Zamanlayıcısı Modeli
 *
 * Chiarella, MixNotice ve Metiq'in ortak özelliği. Kullanıcı bir süre
 * seçer, süre bitince **tüm kanallar yumuşakça kısılır** (aniden kesilmez
 * — uykuya dalan kişiyi uyandırmamak için).
 *
 * İki mod var:
 *  - [Preset]      hazır süreler (5, 15, 30, 45, 60, 90 dk)
 *  - [Custom]      5 dk – 4 saat arası serbest
 *
 * "Sona kadar" modu: süre bitince durdurmak yerine ses seviyesini
 * kademeli olarak sıfıra indirir, sonra durdurur ([fadeSeconds]).
 */
data class SleepTimerState(
    /** Toplam süre (ms). 0 = kapalı. */
    val totalMs: Long = 0L,
    /** Kalan süre (ms). */
    val remainingMs: Long = 0L,
    /** Kapanış fade süresi (sn). */
    val fadeSeconds: Int = DEFAULT_FADE_SECONDS,
    /** Çalışıyor mu? */
    val running: Boolean = false,
    /** Duraklatıldı mı? */
    val paused: Boolean = false
) {
    /** Kalan sürenin okunabilir hâli: "23:41" veya "1:05:12". */
    val display: String
        get() {
            if (totalMs <= 0L) return ""
            val total = (remainingMs / 1000L).coerceAtLeast(0L)
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
        }

    /** İlerleme oranı 0..1 (dolgu göstergesi için). */
    val progress: Float
        get() = if (totalMs <= 0L) 0f
        else ((totalMs - remainingMs).toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)

    companion object {
        const val DEFAULT_FADE_SECONDS = 12
        val PRESETS_MINUTES = listOf(5, 10, 15, 30, 45, 60, 90, 120)
        const val MIN_CUSTOM_MINUTES = 5
        const val MAX_CUSTOM_MINUTES = 240
    }
}

/**
 * 💾 Karışım Profili — kaydedilmiş kanal düzeni
 *
 * Kullanıcı 6 kanalı istediği gibi ayarlayıp "kaydet" der. Profil yalnızca
 * **ses kimliği + seviye + mute** saklar; sesin kendisi değil. Böylece
 * uzak akışlar da prosedürel sesler de aynı profilde yaşar.
 *
 * `soundJson` yerine düz alanlar: veritabanı/DataStore'a JSON olarak
 * yazılabilir, ayrıca insan tarafından okunabilir.
 */
data class MixProfile(
    val id: String,
    val name: String,
    val createdAt: Long,
    val channels: List<MixChannel>,
    /** Bu profil bir manga türüyle eşleşiyorsa (otomatik öneri için). */
    val genreTag: String = ""
) {
    val channelCount: Int get() = channels.size

    /** Kısa özet: "3 ses · yağmur + piyano" */
    val summary: String
        get() = if (channels.isEmpty()) "boş"
        else "${channels.size} ses · ${channels.take(2).joinToString(" + ") { it.title }}"
}

/**
 * Profildeki tek kanal.
 *
 * [streamUrl] ve [sourceKey] **uzak sesler** için gereklidir: Jamendo veya
 * Audius'tan gelen bir parça yerleşik katalogda yoktur, bu yüzden profilde
 * akış adresi saklanmazsa geri yüklenemez. Prosedürel seslerde ikisi de
 * `null`/boş kalır.
 */
data class MixChannel(
    val soundId: String,
    val title: String,
    val volume: Float,
    val muted: Boolean = false,
    val streamUrl: String? = null,
    val sourceKey: String = ""
)

/**
 * 📤 Karışım dışa/içe aktarma biçimi
 *
 * MixNotice ve Rainfold'daki `MixCodec` karşılığı. Kullanıcı bir metin
 * olarak paylaşabilir; başka cihazda içe aktarabilir.
 */
data class MixCodecPayload(
    val version: Int = 1,
    val name: String,
    val channels: List<MixChannel>
)
