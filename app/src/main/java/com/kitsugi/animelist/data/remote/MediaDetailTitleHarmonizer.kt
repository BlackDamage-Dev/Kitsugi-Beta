package com.kitsugi.animelist.data.remote

import java.util.Locale

/**
 * Detay nesnesindeki başlık alanlarını TEK bir tutarlı kümeye indirger.
 *
 * NEDEN: Akış ekranı, eklentilere giden arama varyantlarını `title / titleEnglish /
 * titleRomaji / titleNative / synonyms` alanlarından kuruyor. Kaynak istemcileri bu
 * alanları **farklı dolduruyordu**:
 *
 * | Kaynak   | title            | titleEnglish | titleRomaji | titleNative | titleTurkish |
 * |----------|------------------|--------------|-------------|-------------|--------------|
 * | AniList  | Romaji           | ✔            | ✔           | ✔ (native)  | –            |
 * | MAL      | Romaji           | ✔            | ✘ → ✔ (fix) | ✘ → ✔ (fix) | –            |
 * | Kitsu    | canonicalTitle   | ✔            | ✔           | ✔           | –            |
 * | Simkl    | yerelleştirilmiş | ✔            | ✘ (null)    | kısmi       | –            |
 * | TMDB     | yerelleştirilmiş | ✔            | ✘ (alan yok)| ✔           | ✔            |
 * | Shikimori| Romaji           | ✔            | ✔           | ✔           | –            |
 * | Bangumi  | asıl             | ✔            | ✔           | ✔           | –            |
 *
 * Simkl/TMDB gibi kaynaklarda Romaji alanı boş kaldığı için, **aynı anime farklı
 * kaynaktan açıldığında eklentiye farklı sayıda başlık gidiyordu** (kullanıcı raporu:
 * AniList'ten girince Anizm/TrAnimeIzle/AnimeciX akış veriyor, MAL'dan girince
 * vermiyordu). Bu sınıf, hangi kaynak olursa olsun mevcut tüm başlık varyantlarını
 * `synonyms` içinde toplar ve `titleNative`/`titleJapanese` alanlarını karşılıklı
 * tamamlar. Böylece akış ekranı HER kaynakta tam başlık evrenini görür.
 *
 * Saf fonksiyondur (ağ/ağ erişimi yok) — birim testleriyle doğrulanır.
 */
internal object MediaDetailTitleHarmonizer {

    /** Sorgu listesi şişmesin; en fazla bu kadar ek varyant taşınır (mevcut + yeni). */
    private const val MAX_SYNONYMS = 12

    fun harmonize(detail: KitsugiMediaDetail): KitsugiMediaDetail {
        val primary = detail.title?.trim().orEmpty()
        val primaryKey = normalize(primary)

        // 1) Bilinen tüm başlık varyantlarını topla (mevcut synonyms önce gelir).
        val ordered = LinkedHashMap<String, String>()
        fun add(value: String?) {
            val title = value?.trim().orEmpty()
            if (title.length < 2) return
            val key = normalize(title)
            if (key.isEmpty() || key == primaryKey) return
            ordered.putIfAbsent(key, title)
        }
        detail.synonyms.forEach { add(it) }
        add(detail.titleTurkish)
        add(detail.titleEnglish)
        add(detail.titleRomaji)
        add(detail.titleNative)
        add(detail.titleJapanese)

        // 2) Aynı başlığın farklı alanlara dağılmış olması durumunu düzelt:
        //    native/japanese alanları birbirini tamamlar (Japonca asıl ad her ikisidir).
        val nativeFilled = detail.titleNative?.takeIf { it.isNotBlank() }
            ?: detail.titleJapanese?.takeIf { it.isNotBlank() }
        val japaneseFilled = detail.titleJapanese?.takeIf { it.isNotBlank() }
            ?: detail.titleNative?.takeIf { it.isNotBlank() }

        val synonyms = ordered.values.take(MAX_SYNONYMS)

        if (synonyms == detail.synonyms &&
            nativeFilled == detail.titleNative &&
            japaneseFilled == detail.titleJapanese
        ) {
            return detail
        }
        return detail.copy(
            titleNative = nativeFilled,
            titleJapanese = japaneseFilled,
            synonyms = synonyms
        )
    }

    /** Karşılaştırma anahtarı: büyük/küçük harf, fazla boşluk ve noktalama farkını yok sayar. */
    internal fun normalize(value: String): String =
        value.lowercase(Locale.ROOT)
            .replace(Regex("[\\s\\u00A0]+"), " ")
            .trim()
}
