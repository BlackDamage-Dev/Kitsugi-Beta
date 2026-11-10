package com.kitsugi.animelist.core.player

import java.util.Locale

/**
 * Bir akışın Dolby Vision olup olmadığını ve (biliniyorsa) profilini taşır.
 *
 * @property profile 5, 7 veya 8; başlıkta profil yazmıyorsa null.
 * @property matched Eşleşmeyi üreten metin parçası (teşhis/log için).
 */
data class DolbyVisionHint(
    val profile: Int?,
    val matched: String
) {
    /** P5'in kendi başına görüntülenebilir HDR10 taban katmanı yoktur. */
    val isProfile5: Boolean get() = profile == 5

    /** Profil bilinmiyorsa (tipik "DV" etiketi) temkinli davranılır. */
    val isProfileUnknown: Boolean get() = profile == null
}

/**
 * Kaynak başlığı / eklenti adı / dosya adı gibi metinlerden Dolby Vision
 * ipuçlarını çıkarır.
 *
 * Neden gerekli: Kitsugi'nin varsayılan motoru MPV ve varsayılan video çıkışı
 * `vo=gpu`. mpv'nin eski `gpu` çıkışı Dolby Vision RPU'sunu **hiç** uygulamaz;
 * P5 (IPTPQc2) içerik bu yüzden yeşil/mor görünür. `vo=gpu-next` (libplacebo)
 * ise RPU reshaping yapar ve renkler doğru çıkar — ancak FFmpeg'in RPU'yu
 * kareye ekleyebilmesi için donanım çözücüsünün devre dışı olması gerekir
 * (MediaCodec yolu RPU'yu taşımaz). Bu ipucu, o kararı akış başına vermek için
 * kullanılır.
 *
 * Torrentio/Stremio başlıkları tipik biçimleri:
 *  - `... 2160p DSNP WEB-DL DDP5.1 Atmos DV HDR10`   → DV (profil yok)
 *  - `4K DV | HDR10+`                                  → DV
 *  - `DoVi P8`, `DV5`, `dvh1.05`, `Dolby Vision`      → profil bilgisi ile
 */
object DolbyVisionStreamHints {

    /** Codec dizesi: dvhe.05 / dvh1.08 / dvav.09 / dva1.07 */
    private val DV_CODEC = Regex(
        """\b(dvhe|dvh1|dvav|dva1)\.(\d{1,2})\b""",
        RegexOption.IGNORE_CASE
    )

    /** "Dolby Vision", "DoVi" veya kelime olarak "DV" (DVD'yi yakalamaz). */
    private val DV_WORD = Regex(
        """(?<![a-z0-9])(dovi|dolby[\s._-]*vision|dv)(?![a-z0-9])""",
        RegexOption.IGNORE_CASE
    )

    /** "DV5" / "DV 5" / "Profile 5" / "dv5.1" gibi profil etiketleri. */
    private val DV_PROFILE = Regex(
        """\b(?:dv|profile)[\s._-]?(\d{1,2})(?:\.\d)?\b""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Verilen metinlerden ilk Dolby Vision ipucunu döndürür.
     *
     * Sıra önemlidir: codec dizesi en güvenilir sinyaldir, ardından profil
     * etiketi, ardından çıplak "DV"/"DoVi"/"Dolby Vision".
     */
    fun detect(vararg candidates: String?): DolbyVisionHint? {
        for (raw in candidates) {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) continue

            DV_CODEC.find(text)?.let { match ->
                val prefix = match.groupValues[1].lowercase(Locale.ROOT)
                val digits = match.groupValues[2]
                // dvhe.05 → 5, dvhe.08 → 8; "dvh1" da aynı şemayı kullanır.
                val profile = digits.toIntOrNull()?.let(::normalizeProfile)
                return DolbyVisionHint(profile = profile, matched = "${prefix}.$digits")
            }

            if (!DV_WORD.containsMatchIn(text) && !DV_PROFILE.containsMatchIn(text)) continue

            val profile = DV_PROFILE.find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?.let(::normalizeProfile)

            val matched = DV_WORD.find(text)?.value ?: DV_PROFILE.find(text)?.value ?: "dv"
            return DolbyVisionHint(profile = profile, matched = matched)
        }
        return null
    }

    /**
     * Profil numarası bazen iki haneli yazılır (08, 07, 05). Ayrıca 8.4 gibi
     * alt profiller de 8 kabul edilir. 1..13 dışındaki değerler "bilinmiyor".
     */
    private fun normalizeProfile(value: Int): Int? = when (value) {
        5 -> 5
        7 -> 7
        8, 9, 10 -> 8 // 8.x / 9 / 10 aynı RPU yeniden şekillendirme yolunu kullanır
        else -> null
    }
}
