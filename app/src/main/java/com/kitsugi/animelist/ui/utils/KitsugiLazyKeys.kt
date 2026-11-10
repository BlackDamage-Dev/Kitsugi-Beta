package com.kitsugi.animelist.ui.utils

import com.kitsugi.animelist.data.remote.JikanSearchResult

/**
 * LazyColumn / LazyRow / LazyVerticalGrid öğeleri için TEK merkezden üretilen kimlik anahtarı.
 *
 * NEDEN: Kaynakların id uzayları medya türüne göre AYRIDIR — aynı sayısal kimlik hem anime
 * hem manga (MAL/Jikan, Kitsu, Shikimori, Simkl) veya hem film hem dizi (TMDB) olabilir.
 * Örneğin MAL anime #21 ile MAL manga #21 iki FARKLI yapımdır.
 *
 * Liste anahtarı yalnızca `source + malId` ile kurulursa "Tümü" (karma anime+manga) veya
 * karma arama listelerinde iki farklı öğe **aynı anahtarı** alır. Compose aynı anahtarlı
 * öğeleri tek bir slot sanıp üst üste çizer (kutucuklar üst üste biner), kaydırma konumu ve
 * öğe geri dönüşümü bozulur; ayrıca "Key was already used" çökmesi üretir.
 *
 * Bu yüzden anahtar şu üç kimlik alanını birlikte taşımak ZORUNDADIR:
 *   `source` (hangi API) + `type` (anime/manga/film/dizi) + sayısal kimlik (`malId`/`tmdbId`).
 * `cs3Url` yalnızca eklenti (CS3) sonuçlarında dolduğu için hash'i son ek olarak korunur.
 *
 * Kullanım: `key = { _, item -> item.kitsugiLazyKey("grid") }` veya `key = { it.kitsugiLazyKey("tv") }`
 * (prefix o listenin okunabilirlik etiketidir; farklı listeler aynı öğeyi paylaşabilir).
 */
/**
 * Verilen taban anahtarları Compose'un `key` sözleşmesine uygun hâle getirir:
 * **liste içinde her anahtar benzersiz olmalıdır.**
 *
 * NEDEN: LazyColumn/LazyRow/LazyVerticalGrid aynı anahtarı iki kez görürse
 * `IllegalArgumentException: Key "..." was already used` fırlatır ve uygulama çöker
 * (bkz. Kitsugi_Crash_Report 2026-11-10 — `Key "1622_20"`). Aynı bölümün birden fazla
 * kalite/dil/kaynak varyantı indirildiğinde `animeId_episode` gibi doğal anahtarlar
 * kaçınılmaz olarak tekrarlanır.
 *
 * Tekrarlanan anahtara kararlı bir sıra numarası eklenir (`taban`, `taban#2`, `taban#3`).
 * Aynı girdi listesi her zaman aynı sonucu verdiği için kaydırma konumu ve öğe durumu korunur.
 *
 * Kullanım:
 * ```
 * val keys = remember(downloads) { uniqueLazyKeys(downloads.map { "${it.animeId}_${it.episode}" }) }
 * itemsIndexed(downloads, key = { index, _ -> keys[index] }) { index, item -> ... }
 * ```
 */
fun uniqueLazyKeys(baseKeys: List<String>): List<String> {
    val seen = HashMap<String, Int>(baseKeys.size)
    return baseKeys.map { base ->
        val count = (seen[base] ?: 0) + 1
        seen[base] = count
        if (count == 1) base else "$base#$count"
    }
}

/**
 * Tek bir öğe için [uniqueLazyKeys] ile aynı biçimde anahtar üretir; `index` kaçınılmaz
 * olarak benzersizliği garanti eder (kaydırma sırasında kimlik değişebilir, bu yüzden
 * yalnızca liste sık değişmiyorsa tercih edilmelidir).
 */
fun uniqueLazyKey(base: String, index: Int): String = "$base#idx$index"

fun JikanSearchResult.kitsugiLazyKey(prefix: String, index: Int = -1): String {
    val idxPart = if (index >= 0) "${index}_" else ""
    val idPart = if (malId == 0 && (tmdbId == null || tmdbId == 0) && cs3Url.isNullOrEmpty()) {
        "${title.hashCode()}_${subtitle.hashCode()}"
    } else {
        "${malId}_${tmdbId ?: 0}_${cs3Url.orEmpty().hashCode()}"
    }
    return "${prefix}_${idxPart}${source.lowercase()}_${type.name.lowercase()}_$idPart"
}
