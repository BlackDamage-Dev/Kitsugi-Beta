package com.kitsugi.animelist.ui.utils

/**
 * Lazy liste / ızgara / Pager anahtarlarını liste İÇİNDE benzersizleştirir.
 *
 * NEDEN (v2.4.369 çökmesi — `Key "tmdb_null" was already used`):
 * Compose, aynı LazyLayout içinde aynı anahtarı ikinci kez görünce
 * `IllegalArgumentException` fırlatır ve uygulama ana iş parçacığında çöker. Anahtar
 * `"${source}_${id}"` gibi API verisinden kurulduğunda bu garanti EDİLEMEZ:
 *  - TMDB / MAL (Jikan) / Bangumi yorumlarının sayısal kimliği yoktur → `id = null` →
 *    her yorum aynı `"tmdb_null"` anahtarını alır (raporlardaki iki ön plan çökmesi);
 *  - API sayfalamaları çakışınca aynı öğe iki kez gelebilir (Jikan, CS3 eklentileri);
 *  - farklı kimlik uzayları (film #122 ↔ dizi #122) tür bilgisi olmadan çakışır.
 *
 * ÇÖZÜM: Temel anahtar ilk görüldüğünde AYNEN kullanılır (kaydırma konumu, animasyon ve
 * öğe durumu kararlı kalır); aynı temel anahtar tekrar görülürse `#2`, `#3`… son eki
 * eklenir. Üretilen son ek başka bir temel anahtarla çakışırsa bir sonraki numaraya
 * geçilir; böylece sonuç listesi her koşulda benzersizdir. Çıktı daima [String] olduğu
 * için Bundle'a kaydedilebilir (rememberSaveable ile uyumlu).
 *
 * Saf Kotlin'dir (Compose bağımlılığı yok) → JVM birim testleriyle doğrulanır.
 */
fun <T> List<T>.uniqueLazyKeys(baseKey: (item: T) -> Any?): List<String> =
    uniqueLazyKeysIndexed { _, item -> baseKey(item) }

/** [uniqueLazyKeys]'in indeksli sürümü: temel anahtar hem indekse hem öğeye bakabilir. */
fun <T> List<T>.uniqueLazyKeysIndexed(baseKey: (index: Int, item: T) -> Any?): List<String> {
    if (isEmpty()) return emptyList()
    val occurrences = HashMap<String, Int>(size * 2)
    val used = HashSet<String>(size * 2)
    val keys = ArrayList<String>(size)
    forEachIndexed { index, item ->
        val base = baseKey(index, item)?.toString()?.takeIf { it.isNotEmpty() } ?: NULL_LAZY_KEY
        var occurrence = occurrences[base] ?: 0
        var candidate: String
        do {
            occurrence++
            candidate = if (occurrence == 1) base else "$base$DUPLICATE_KEY_SEPARATOR$occurrence"
        } while (!used.add(candidate))
        occurrences[base] = occurrence
        keys.add(candidate)
    }
    return keys
}

/** Temel anahtar `null`/boş olduğunda kullanılan yer tutucu (sonra yine benzersizleştirilir). */
internal const val NULL_LAZY_KEY = "null"

/** Yinelenen temel anahtarlara eklenen son ekin ayıracı: `base#2`, `base#3`… */
internal const val DUPLICATE_KEY_SEPARATOR = "#"
