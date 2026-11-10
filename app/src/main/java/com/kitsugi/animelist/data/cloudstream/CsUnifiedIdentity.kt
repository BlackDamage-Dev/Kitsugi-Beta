package com.kitsugi.animelist.data.cloudstream

import com.kitsugi.animelist.model.MediaType
import java.util.Locale

/**
 * Ortak küme kimliği — bir eserin, UYGULAMAYA HANGİ KAYNAKTAN gelirse gelsin
 * (AniList, MAL, TMDB, Kitsu, Bangumi, Simkl veya bir eklentinin kendi araması)
 * aynı anahtarlara indirgenmiş hâli.
 *
 * Sorun (teşhis raporu: KOK_NEDEN_RAPORU_EKLENTI_KAYNAK_BAGIMLILIGI_VE_ORTAK_KUME):
 * CloudStream eklentileri başlıkla arama yaptığı için, detay sayfasında seçilen
 * katalog kaynağı değiştiğinde eklentiye giden başlık/ID/sezon modeli de değişiyor
 * ve arama→eşleme zinciri kopuyordu. Stremio/Torrentio tarafı ise tek ve kesin bir
 * kimlikle (IMDb/Kitsu ID) çalıştığı için kaynaktan etkilenmiyor.
 *
 * Bu nesne aynı determinizmi uygulama tarafında üretir: eserin bilinen TÜM kimlik
 * uzayları (IMDb, TMDB, MAL, AniList, Kitsu, Bangumi) ve başlık+yıl takma adı,
 * tek bir "takma ad anahtarları" kümesi olarak çıkarılır. [CsProviderUrlMemory]
 * bir eklentinin içerik URL'sini bu anahtarların HEPSİNE birden kaydeder; böylece
 * kullanıcı esere başka bir katalog kaynağından girse bile, çözülen kimliklerden
 * en az biri ortaksa eklenti URL'si doğrudan bulunur ve kırılgan başlık
 * arama/eşleme halkaları hiç çalışmaz.
 */
internal object CsUnifiedIdentity {

    private val IMDB_ID_REGEX = Regex("tt[0-9]{5,12}")

    /**
     * Öncelik sırasına göre tekil kanonik anahtar (loglama/teşhis için).
     * Hiçbir kimlik yoksa ve başlık+yıl da yoksa null.
     */
    fun canonicalKey(
        imdbId: String? = null,
        tmdbId: Int? = null,
        malId: Int? = null,
        aniListId: Int? = null,
        kitsuId: Int? = null,
        bangumiId: Int? = null,
        title: String? = null,
        year: Int? = null,
        mediaType: MediaType? = null,
        isMovie: Boolean = false
    ): String? = aliasKeys(
        imdbId = imdbId,
        tmdbId = tmdbId,
        malId = malId,
        aniListId = aniListId,
        kitsuId = kitsuId,
        bangumiId = bangumiId,
        title = title,
        year = year,
        mediaType = mediaType,
        isMovie = isMovie
    ).firstOrNull()

    /**
     * Eserin bilinen tüm kimlik uzaylarındaki takma ad anahtarları, en güçlüden
     * en zayıfa sıralı. Hafıza kaydı bu anahtarların tamamına yazılır; sorgu da
     * tamamında aranır — kaynağa göre elde hangi kimlik varsa onunla eşleşilir.
     *
     * Başlık takma adı en zayıf halkadır ve yalnızca yıl biliniyorsa üretilir:
     * yılsız başlık anahtarı, aynı adlı farklı eserlerin URL'lerini birbirine
     * karıştırabilir (hafıza isabeti yanlış sayfayı açsa bile alt yollar devreye
     * girer, ancak gereksiz yükü önlemek için bu anahtar muhafazakâr tutulur).
     */
    fun aliasKeys(
        imdbId: String? = null,
        tmdbId: Int? = null,
        malId: Int? = null,
        aniListId: Int? = null,
        kitsuId: Int? = null,
        bangumiId: Int? = null,
        title: String? = null,
        year: Int? = null,
        mediaType: MediaType? = null,
        isMovie: Boolean = false
    ): List<String> {
        val keys = LinkedHashSet<String>()

        imdbId?.trim()?.lowercase(Locale.ROOT)
            ?.takeIf { IMDB_ID_REGEX.matches(it) }
            ?.let { keys.add("imdb:$it") }

        tmdbId?.takeIf { it > 0 }?.let { id ->
            // TMDB'de film ve dizi aynı sayısal uzayı paylaşır; tür ayrımı şarttır.
            val kind = if (mediaType == MediaType.Movie || (mediaType == null && isMovie)) "movie" else "tv"
            keys.add("tmdb:$kind:$id")
        }

        malId?.takeIf { it > 0 }?.let { keys.add("mal:$it") }
        aniListId?.takeIf { it > 0 }?.let { keys.add("anilist:$it") }
        kitsuId?.takeIf { it > 0 }?.let { keys.add("kitsu:$it") }
        bangumiId?.takeIf { it > 0 }?.let { keys.add("bangumi:$it") }

        if (year != null && year in 1900..2100) {
            val normalizedTitle = title
                ?.let { CsTitleMatcher.normalizeTitleForMatch(it) }
                ?.takeIf { it.length >= 3 }
            if (normalizedTitle != null) {
                keys.add("title:$normalizedTitle:$year")
            }
        }

        return keys.toList()
    }
}
