package com.kitsugi.animelist.data.manga

import java.util.Locale

/**
 * Arama sorgusunun **dil bağımsız** genişletilmesi.
 *
 * Sorun
 * -----
 * Anime/Light novel/Manhwa/Manhwa kaynakları aynı eseri kendi dillerindeki adla
 * listeliyor:
 *
 * ```
 * AniList (romaji)  : Youkoso Jitsuryoku Shijou Shugi no Kyoushitsu e
 * Anime title (en)  : Classroom of the Elite
 * Manga-TR (tr)     : Sınıfın Seçkinleri
 * ```
 *
 * Kullanıcı uygulamada hangi başlığı görüyorsa o başlıkla arıyordu; kaynak
 * farklı dilde kayıtlıysa **0 sonuç** dönüyordu. Bu yardımcı, elimizdeki tüm
 * başlık varyantlarını (romaji / İngilizce / Japonca / yerel) tekilleştirip
 * arama sırasına koyar. Böylece "hangi kaynaktan olursa olsun" eser bulunur.
 *
 * Sıra
 * ----
 * 1. Kullanıcının yazdığı/gördüğü başlık,
 * 2. uygulama diline en yakın başlık (varsa),
 * 3. diğer başlıklar,
 * 4. başlıktan türetilen kısa biçimler ("Ana Başlık: Alt Başlık" → "Ana Başlık").
 */
object MangaSearchAlternates {

    /** Bir kaynak için denenecek en fazla farklı başlık sayısı. */
    const val MAX_TITLE_ATTEMPTS = 4

    /**
     * @param primary kullanıcının seçtiği/gördüğü başlık
     * @param alternates diğer dillerdeki başlıklar (null/boş olabilir)
     * @param appLanguage uygulama dili kodu ("tr", "en", "system", …)
     */
    fun build(
        primary: String?,
        alternates: List<String?> = emptyList(),
        appLanguage: String? = null,
    ): List<String> {
        val preferred = appLanguage
            ?.takeIf { it.isNotBlank() && it != "system" }
            ?.substringBefore('-')
            ?.lowercase(Locale.ROOT)

        val cleaned = LinkedHashMap<String, String>() // anahtar → görünen sorgu

        fun push(value: String?) {
            val text = value?.trim().orEmpty()
            if (text.length < 2) return
            val key = CanonicalMangaResolver.resolve(text).compact
                .ifBlank { text.lowercase(Locale.ROOT) }
            if (!cleaned.containsKey(key)) cleaned[key] = text
        }

        push(primary)
        // Önce uygulama diline "benzeyen" başlık: Latin alfabesi dışı yazı sistemi
        // (Japonca/Korece/Çince) yalnızca o dil seçiliyse öne alınır.
        val others = alternates.filterNotNull().map { it.trim() }.filter { it.length >= 2 }
        val (nativeScript, latin) = others.partition { it.any { ch -> ch.code > 0x2E80 } }
        if (preferred == null || preferred in setOf("ja", "ko", "zh")) {
            nativeScript.forEach(::push)
        }
        latin.forEach(::push)
        if (preferred != null && preferred !in setOf("ja", "ko", "zh")) {
            nativeScript.forEach(::push)
        }

        // Kısa biçimler: uzun alt başlıklı isimlerde kaynaklar genelde kısa adı kullanır.
        cleaned.values.toList().forEach { value ->
            val short = value
                .substringBefore('：')
                .substringBefore(':')
                .substringBefore(" - ")
                .trim()
            if (short.length in 3 until value.length) push(short)
        }

        return cleaned.values.take(MAX_TITLE_ATTEMPTS)
    }
}
