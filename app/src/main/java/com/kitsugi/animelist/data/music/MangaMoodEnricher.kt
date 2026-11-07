package com.kitsugi.animelist.data.music

import android.util.Log
import com.kitsugi.animelist.data.remote.JikanSearchClient
import com.kitsugi.animelist.data.remote.KitsugiTaxonomySources
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 🧬 Başlıktan tür/etiket zenginleştirme — **yedi kaynak**
 *
 * ## Sorun
 * Okuyucuya gelen `MangaDetails.genre` çoğu zaman **boş** oluyor:
 * eklentilerin bir kısmı (özellikle Türkçe kaynaklar) tür alanını hiç
 * doldurmuyor, bir kısmı yalnızca "Manhwa", "Webtoon" gibi biçim
 * etiketleri veriyor. Bu durumda [ContentMoodResolver] hiçbir sinyal
 * bulamıyor ve panel "Tür/açıklama tanınmadı — dengeli başlangıç" diyordu.
 *
 * ## Çözüm
 * Yerel tür listesi boş ya da çözümlenemiyorsa, başlık üzerinden
 * uygulamanın **mevcut** çok kaynaklı taksonomi toplayıcısı
 * ([KitsugiTaxonomySources]) çağrılır. O toplayıcı yedi kaynağı paralel
 * tarar:
 *
 * ```
 * 1. MAL / Jikan      → genres, themes, demographics
 * 2. AniList          → genres, tags
 * 3. Shikimori        → genres (kind: genre/theme/demographic)
 * 4. Kitsu            → categories
 * 5. Bangumi          → tags + infobox
 * 6. TMDB             → genres, keywords
 * 7. Simkl            → genres
 * ```
 *
 * ## Kurallar
 *  - **Ağ işi asla ana thread'de değil** ([Dispatchers.IO]).
 *  - **Tek seferlik + önbellekli:** aynı başlık için okuyucu her
 *    açıldığında yedi kaynağı yeniden taramayız (12 saat TTL).
 *  - **Sessiz başarısızlık:** hiçbir kaynak yanıt vermezse boş liste
 *    döner ve çağıran taraf nötr profilde kalır — çökme yok.
 *  - **Kimlik güvenliği:** MAL kimliği yalnızca arama sonucundan gelir,
 *    uydurulmaz. Yanlış yapımın türleri başka bir mangaya yapışmaz.
 */
object MangaMoodEnricher {

    private const val TAG = "MangaMoodEnricher"

    /** Toplam bütçe — taksonomi toplayıcısının kendi tavanı (8 sn) + arama. */
    private const val TOTAL_TIMEOUT_MS = 14_000L

    /** Önbellek taze kalma süresi. */
    private const val CACHE_TTL_MS = 12 * 60 * 60 * 1000L

    private const val CACHE_LIMIT = 64

    /**
     * Duygu çözümlemesine verilecek en fazla terim.
     *
     * `ContentMoodResolver` ilk 6 türü ağırlıklı kullanır; geri kalanı
     * etiket katmanına düşer. 24 terim, hem kapsamı hem de maliyeti
     * dengeler.
     */
    private const val MAX_TERMS = 24

    /** Zenginleştirme sonucu. */
    data class Result(
        /** Birleştirilmiş tür + etiket listesi (tekrarsız, sıralı). */
        val genres: List<String> = emptyList(),
        /** Veri döndüren kaynak adları (tanılama + UI notu için). */
        val sources: List<String> = emptyList(),
        /** Önbellekten mi geldi? */
        val fromCache: Boolean = false
    ) {
        val isEmpty: Boolean get() = genres.isEmpty()
    }

    private data class Entry(val result: Result, val fetchedAt: Long)

    private val cache = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean =
            size > CACHE_LIMIT
    }

    private val jikanSearch = JikanSearchClient()

    /**
     * Başlık için tür/etiket zenginleştirmesi yapar.
     *
     * @param title Okuyucudaki başlık ("Chainsaw Man").
     * @param alternativeTitles Eklentinin bildirdiği alternatif adlar
     *        (İngilizce/romaji adlar arama isabetini ciddi biçimde artırır).
     */
    suspend fun enrich(
        title: String,
        alternativeTitles: List<String> = emptyList()
    ): Result = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim()
        if (cleanTitle.length < 2) return@withContext Result()

        val key = cleanTitle.lowercase()
        synchronized(cache) {
            val hit = cache[key]
            if (hit != null && System.currentTimeMillis() - hit.fetchedAt < CACHE_TTL_MS) {
                return@withContext hit.result.copy(fromCache = true)
            }
        }

        val queries = buildList {
            add(cleanTitle)
            alternativeTitles.forEach { alt ->
                val trimmed = alt.trim()
                if (trimmed.length >= 2 && trimmed.lowercase() != cleanTitle.lowercase()) add(trimmed)
            }
        }.distinct().take(3)

        val result = withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
            runCatching { collect(queries) }
                .onFailure { Log.w(TAG, "Zenginleştirme başarısız: $cleanTitle", it) }
                .getOrElse { Result() }
        } ?: Result()

        if (!result.isEmpty) {
            synchronized(cache) {
                cache[key] = Entry(result, System.currentTimeMillis())
            }
        }
        result
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Dahili
    // ─────────────────────────────────────────────────────────────────────

    private suspend fun collect(queries: List<String>): Result {
        // 1) MAL kimliği + doğrudan tür listesi (en güvenilir sinyal).
        var malId: Int? = null
        var localGenres: List<String> = emptyList()

        for (query in queries) {
            val hit = runCatching {
                jikanSearch.search(query = query, mediaType = MediaType.Manga)
            }.getOrDefault(emptyList()).firstOrNull { it.type == MediaType.Manga }
                ?: continue

            malId = (hit.realMalId ?: hit.malId).takeIf { it > 0 }
            localGenres = hit.genres
            // Kimlik bulunduysa taksonomi toplayıcısı yedi kaynağı zaten
            // paralel tarayacak; kalan başlıkları sırayla denemek sadece
            // gecikme ekler.
            if (malId != null) break
        }

        // 2) Yedi kaynaklı taksonomi (MAL/Jikan, AniList, Shikimori, Kitsu,
        //    Bangumi, TMDB, Simkl) — başlık üzerinden de çalışır.
        val slice = runCatching {
            KitsugiTaxonomySources.collect(
                malId = malId,
                mediaType = MediaType.Manga,
                titles = queries,
                includeBangumi = true
            )
        }.getOrDefault(KitsugiTaxonomySources.Slice())

        val sources = mutableListOf<String>()
        if (localGenres.isNotEmpty()) sources += "MAL"
        if (slice.genres.isNotEmpty()) sources += "tür"
        if (slice.tags.isNotEmpty()) sources += "etiket(${slice.tags.size})"
        if (slice.themes.isNotEmpty() || slice.demographics.isNotEmpty()) sources += "tema"

        val merged = buildList {
            addAll(localGenres)
            addAll(slice.genres)
            addAll(slice.tags.map { it.name })
            addAll(slice.themes)
            addAll(slice.demographics)
        }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_TERMS)

        if (merged.isEmpty()) {
            Log.d(TAG, "Zenginleştirme boş: ${queries.firstOrNull()}")
            return Result()
        }

        Log.d(
            TAG,
            "Zenginleştirildi: ${queries.firstOrNull()} → ${merged.size} terim " +
                "(malId=$malId, kaynaklar=$sources)"
        )
        return Result(genres = merged, sources = sources)
    }
}
