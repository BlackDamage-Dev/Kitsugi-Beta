package com.kitsugi.animelist.core.player

import kotlinx.serialization.Serializable

/**
 * T2.4 — Kalite Profili Modeli.
 *
 * Kullanıcının tercih ettiği video çözünürlüğünü ve maksimum bant genişliğini
 * saklar. Stream ekranı kaynakları bu profile göre sıralar; oynatıcıdaki adaptif
 * varyantların seçimi ayrıca player track-selection politikasına bağlıdır.
 *
 * ### Kalite Öncelik Mantığı
 * - `AUTO`   → Ağ hızına göre ExoPlayer/MPV otomatik seçer (varsayılan)
 * - `P1080`  → 1080p ve üzeri tercih edilir
 * - `P720`   → 720p tercih edilir
 * - `P480`   → Düşük bant genişliği / hücresel ağ
 * - `DATA_SAVER` → Mümkün olan en düşük kalite (480p altı)
 */
@Serializable
enum class QualityPreference(val label: String, val minQualityValue: Int, val maxQualityValue: Int) {
    AUTO        ("Otomatik",      -1,    Int.MAX_VALUE),
    P1080       ("1080p+",       1080,   Int.MAX_VALUE),
    P720        ("720p",          720,   1079),
    P480        ("480p",          480,   719),
    DATA_SAVER  ("Veri Tasarrufu",  0,   479);

    /**
     * Bu tercihin oynatıcıya uygulanacak **çözünürlük tavanı** (piksel yüksekliği).
     *
     * `null` → tavan yok (AUTO = adaptif, P1080 = 1080 ve üzeri serbest).
     * Oynatıcı motorları (Media3 `maxVideoSize`, MPV `hls-bitrate`) bu değeri kullanır;
     * aksi hâlde ayarlardaki kalite profili yalnızca bir etiket olarak kalırdı.
     */
    val maxVideoHeight: Int?
        get() = when (this) {
            AUTO, P1080 -> null
            P720 -> 720
            P480 -> 480
            DATA_SAVER -> 360
        }

    companion object {
        fun fromString(value: String?): QualityPreference =
            entries.firstOrNull { it.name == value } ?: AUTO
    }
}

/**
 * Tam kalite profili — tercih + maksimum bant genişliği limiti.
 *
 * @param preference Tercih edilen kalite bandı
 * @param maxBitrateKbps Maksimum bit hızı kbps cinsinden. -1 = sınırsız
 */
@Serializable
data class QualityProfile(
    val preference: QualityPreference = QualityPreference.AUTO,
    val maxBitrateKbps: Int = -1
) {
    /** Kullanıcı bir kalite seçmediyse oynatıcının uygulayacağı çözünürlük tavanı. */
    val effectiveMaxVideoHeight: Int? get() = preference.maxVideoHeight

    /** Sıfır/negatif → sınırsız. */
    val effectiveMaxBitrateKbps: Int? get() = maxBitrateKbps.takeIf { it > 0 }

    companion object {
        val DEFAULT = QualityProfile()
        private const val SEPARATOR = "|"

        /** AppSettings string alanına serialize eder: "P1080|5000" */
        fun serialize(profile: QualityProfile): String =
            "${profile.preference.name}$SEPARATOR${profile.maxBitrateKbps}"

        /** AppSettings string alanından deserialize eder */
        fun deserialize(raw: String?): QualityProfile {
            if (raw.isNullOrBlank()) return DEFAULT
            val parts = raw.split(SEPARATOR)
            return QualityProfile(
                preference    = QualityPreference.fromString(parts.getOrNull(0)),
                maxBitrateKbps = parts.getOrNull(1)?.toIntOrNull() ?: -1
            )
        }
    }
}

/**
 * Kaynak listesini kalite profiline göre sıralar.
 * Profille eşleşen kalite grubundaki kaynaklar başa gelir.
 *
 * @param sources Ham kaynak listesi (CS3 / Stremio vb.)
 * @param profile Kullanıcının aktif kalite profili
 * @return Profile göre sıralanmış kaynak listesi
 */
object QualityDataHelper {

    fun sortByProfile(
        sources: List<com.kitsugi.animelist.data.repository.StreamSource>,
        profile: QualityProfile
    ): List<com.kitsugi.animelist.data.repository.StreamSource> {
        if (profile.preference == QualityPreference.AUTO) return sources

        val pref = profile.preference
        return sources.sortedWith(
            compareByDescending<com.kitsugi.animelist.data.repository.StreamSource> { source ->
                val q = source.qualityValue ?: 0
                when {
                    // Tercih edilen bant içinde
                    q in pref.minQualityValue..pref.maxQualityValue -> 2
                    // Bir üst bant (kabul edilebilir fallback)
                    q > pref.maxQualityValue -> 1
                    // Alt bant (son tercih)
                    else -> 0
                }
            }.thenByDescending { it.qualityValue ?: 0 }
        )
    }

    /**
     * Bit hızı kısıtı uygulanmış kaynak listesi.
     * maxBitrateKbps = -1 ise filtre uygulanmaz.
     */
    fun filterByBitrate(
        sources: List<com.kitsugi.animelist.data.repository.StreamSource>,
        maxBitrateKbps: Int
    ): List<com.kitsugi.animelist.data.repository.StreamSource> {
        if (maxBitrateKbps <= 0) return sources
        return sources.filter { source ->
            // qualityValue is a vertical pixel count (e.g. 720), NOT a bitrate.
            // Unknown bitrate must remain eligible; filtering it using height * 8
            // silently removed valid 720p/1080p sources at common limits.
            val sourceBitrate = source.bitrateKbps
            sourceBitrate == null || sourceBitrate <= maxBitrateKbps
        }
    }
}
