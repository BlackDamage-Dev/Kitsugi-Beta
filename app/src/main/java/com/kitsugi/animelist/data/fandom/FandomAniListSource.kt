package com.kitsugi.animelist.data.fandom

import com.kitsugi.animelist.data.auth.AniListGate

import android.util.Log
import com.kitsugi.animelist.core.memory.BoundedCache
import com.kitsugi.animelist.data.remote.KitsugiApiBase
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/**
 * Doğrulama verisini AniList'ten kimlikle çeker: başlıklar, yaratıcılar, karakterler
 * (yerel adlarıyla) ve ilişkiler. Uygulamanın ortak, hız sınırlı AniList kapısını
 * (`KitsugiApiBase.executeAniListQuery`) kullanır.
 */
internal object FandomAniListSource {

    private const val TAG = "FandomAniList"
    private const val MAX_TITLE_QUERIES = 2

    private val QUERY = """
        query (${'$'}id: Int, ${'$'}idMal: Int, ${'$'}type: MediaType) {
          Media(id: ${'$'}id, idMal: ${'$'}idMal, type: ${'$'}type) {
            id
            idMal
            type
            title { romaji english native }
            synonyms
            relations { edges { relationType node { id idMal type } } }
            characters(sort: [ROLE, RELEVANCE, ID], perPage: 12) {
              edges { role node { id name { full native alternative } } }
            }
            staff(sort: [RELEVANCE, ID], perPage: 10) {
              edges { role node { name { full } } }
            }
          }
        }
    """.trimIndent()

    private val SEARCH_QUERY = """
        query (${'$'}search: String, ${'$'}type: MediaType) {
          Page(perPage: 10) {
            media(search: ${'$'}search, type: ${'$'}type, sort: [SEARCH_MATCH]) {
              id
              idMal
              popularity
              startDate { year }
              title { romaji english native }
              synonyms
            }
          }
        }
    """.trimIndent()

    private val cache = BoundedCache<String, FandomMediaEvidence>("fandom_anilist_evidence", 48)
    private val titleCache = BoundedCache<String, FandomTitleCandidate>("fandom_anilist_title", 48)

    /**
     * Eklenti kaynaklı manga/manhwa gibi KİMLİKSİZ kayıtlar için başlıkla AniList kimliği bulur.
     * Yalnızca normalize edilmiş başlığı birebir eşleşen sonuç kabul edilir; bulunamazsa null.
     * Kimlik bulununca Wikidata / karakter doğrulaması gibi güçlü yollar açılır.
     */
    suspend fun findByTitle(titles: List<String>, isManga: Boolean): FandomTitleCandidate? {
        val queries = titles.map { it.trim() }.filter { it.isNotEmpty() && it.length <= 200 }
            .distinctBy(FandomNameVariants::normalizeForCompare)
            .take(MAX_TITLE_QUERIES)
        for (title in queries) {
            val key = "${if (isManga) "m" else "a"}:${FandomNameVariants.normalizeForCompare(title)}"
            titleCache[key]?.let { return it }
            val variables = JSONObject()
                .put("search", title)
                .put("type", if (isManga) "MANGA" else "ANIME")
            val body = try {
                KitsugiApiBase.executeAniListQuery(
                    query = SEARCH_QUERY,
                    variables = variables,
                    // Vikipedi/Fandom zenginleştirmesi: kıtlıkta kullanıcı kotasını yemesin.
                    priority = AniListGate.Priority.BACKGROUND
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "AniList başlık araması başarısız ($title): ${e.message}")
                null
            }
            if (body == null) {
                FandomHttp.markFailure()
                continue
            }
            val match = FandomAniListParser.pickTitleMatch(FandomAniListParser.parseTitleSearch(body), queries)
            if (match != null) {
                titleCache[key] = match
                return match
            }
        }
        return null
    }

    suspend fun load(aniListId: Int?, malId: Int?, isManga: Boolean): FandomMediaEvidence? {
        val key = when {
            aniListId != null -> "al:$aniListId"
            malId != null -> "mal:${if (isManga) "m" else "a"}:$malId"
            else -> return null
        }
        cache[key]?.let { return it }
        val variables = JSONObject()
        if (aniListId != null) {
            variables.put("id", aniListId)
        } else if (malId != null) {
            variables.put("idMal", malId)
            variables.put("type", if (isManga) "MANGA" else "ANIME")
        }
        val body = try {
            KitsugiApiBase.executeAniListQuery(
                query = QUERY,
                variables = variables,
                priority = AniListGate.Priority.BACKGROUND
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "AniList doğrulama verisi alınamadı ($key): ${e.message}")
            null
        }
        // Yanıt yoksa (429 cooldown, ağ, zaman aşımı) bu geçici bir hatadır: çağıran
        // "bulunamadı" sonucunu kalıcı önbelleğe yazmasın diye işaretlenir.
        if (body == null) FandomHttp.markFailure()
        val evidence = FandomAniListParser.parse(body, isManga) ?: return null
        cache[key] = evidence
        return evidence
    }
}
