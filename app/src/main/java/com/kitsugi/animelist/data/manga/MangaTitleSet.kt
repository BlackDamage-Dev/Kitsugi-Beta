package com.kitsugi.animelist.data.manga

/**
 * Bir serinin **tüm bilinen adları**.
 *
 * Neden gerekli: kaynak aramasının dili sonradan değişebilir (kaynak tarayıcısındaki
 * dil çipi, ya da "Oku" ekranı). Ekran yalnızca "hangi adla arandı" bilgisini
 * taşırsa, dil değiştiğinde yeni dildeki adı üretemez — elinde o ad yoktur.
 *
 * Bu yüzden detay ekranı tüm adları ([MangaTitleSet]) taşır; arama sorgusu her
 * seferinde [queries] ile **seçili dile göre yeniden** üretilir.
 */
data class MangaTitleSet(
    /** Yerel/çevrilmiş ad (kitap kaydının görünen başlığı). */
    val localTitle: String? = null,
    /** İngilizce ad. */
    val englishTitle: String? = null,
    /** Latin harfli orijinal ad (romaji). */
    val romajiTitle: String? = null,
    /** Orijinal (Japonca/Çince/Korece) ad. */
    val nativeTitle: String? = null,
    /** Alternatif adlar (synonyms). */
    val synonyms: List<String> = emptyList()
) {

    /** Hiç ad yok mu? (Boş küme ile arama yapılmaz.) */
    val isEmpty: Boolean
        get() = listOf(localTitle, englishTitle, romajiTitle, nativeTitle)
            .all { it.isNullOrBlank() } && synonyms.all { it.isBlank() }

    /**
     * Seçili arama diline göre öncelik sıralı sorgu listesi.
     *
     * @param titleLanguage Uygulamanın başlık dili (`ENGLISH`/`ROMAJI`/`NATIVE`).
     * @param searchLanguage Kaynak araması için seçilen dil.
     * @param followTitleLanguage true ise [titleLanguage] esas alınır.
     */
    fun queries(
        titleLanguage: String,
        searchLanguage: MangaSearchLanguage,
        followTitleLanguage: Boolean
    ): List<String> = MangaSearchQueryBuilder.buildQueries(
        titleLanguage = titleLanguage,
        searchLanguage = searchLanguage,
        followTitleLanguage = followTitleLanguage,
        localTitle = localTitle,
        englishTitle = englishTitle,
        romajiTitle = romajiTitle,
        nativeTitle = nativeTitle,
        synonyms = synonyms
    )
}
