package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.core.memory.BoundedCache

import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

/**
 * AniList ad köprüsü: Bangumi liste uçlarındaki (Çince/Japonca) kişi adlarına romaji ve
 * İngilizce karşılık kazandırır.
 *
 * Bangumi'nin liste uçları (`/v0/subjects/{id}/characters`, `/v0/subjects/{id}/persons`,
 * `/p1/subjects/{id}/staffs/persons`) infobox döndürmez; bu yüzden karakter, seslendirmen
 * ve kadro adları listelerde özgün (kanji) kalır. AniList'te aynı yapım (`idMal`) için
 * adlar `full` (romaji) + `native` (özgün) + `alternative` olarak gelir; özgün ada birebir
 * eşleme yapılır — uydurma transliterasyon/çeviri üretilmez. Eşleşmeyen adlar olduğu gibi
 * kalır.
 *
 * Sonuçlar bellek içinde önbelleklenir; sekme kapansa bile aynı yapım için tekrar sorgu
 * yapılmaz.
 */
internal object KitsugiAniListPersonBridge {

    /** Bir kişi için AniList ad varyantları. */
    data class NameVariant(
        val full: String,
        val native: String?,
        val alternatives: List<String>
    ) {
        /** Romaji adayı: `full` Latin alfabesindeyse alınır. */
        val romaji: String?
            get() = full.takeIf { isLatinName(it) }

        /** İngilizce adayı: `full` dışındaki ilk Latin alternatif. */
        val english: String?
            get() = alternatives.firstOrNull { isLatinName(it) && !it.equals(full, ignoreCase = true) }

        /** Eşleme anahtarları: özgün ad + full + tüm alternatifler (normalleştirilmiş). */
        val matchKeys: Set<String> = (listOfNotNull(native, full) + alternatives)
            .mapNotNull { normalizeKey(it) }
            .toSet()
    }

    /** Bir Media kaydının AniList ad listeleri. */
    data class MediaNames(
        val characters: List<NameVariant>,
        val voiceActors: List<NameVariant>,
        val staff: List<NameVariant>
    )

    private val cache = BoundedCache<Int, MediaNames>("anilist.personBridge", 500)

    private fun isLatinName(text: String): Boolean =
        com.kitsugi.animelist.utils.PreferenceHelpers.isLatinText(text)

    internal fun normalizeKey(text: String): String? {
        val normalized = Normalizer.normalize(text.trim(), Normalizer.Form.NFKC)
            .lowercase()
            .replace(Regex("[\\s\\p{Punct}]+"), "")
        return normalized.takeIf { it.isNotEmpty() }
    }

    /**
     * AniList `Media(idMal:)` sorgusuyla karakter + seslendirmen + kadro adlarını çeker.
     * Önbelleklidir; ağ/ayrıştırma hatasında `null` döner.
     */
    suspend fun fetchMediaNames(malId: Int, mediaType: MediaType): MediaNames? {
        if (malId <= 0) return null
        cache[malId]?.let { return it }
        val query = """
            query (${'$'}idMal: Int, ${'$'}type: MediaType) {
                Media(idMal: ${'$'}idMal, type: ${'$'}type) {
                    # Bangumi p1 ekip/kadro uçları 60-100 öğe döndürebilir. İki sayfayı
                    # aynı GraphQL isteğinde alarak 50'den sonraki adları da eşleştir.
                    charactersPage1: characters(page: 1, perPage: 50, sort: [RELEVANCE, ROLE]) {
                        edges {
                            node { id name { full native alternative } }
                            voiceActors { id name { full native alternative } }
                        }
                    }
                    charactersPage2: characters(page: 2, perPage: 50, sort: [RELEVANCE, ROLE]) {
                        edges {
                            node { id name { full native alternative } }
                            voiceActors { id name { full native alternative } }
                        }
                    }
                    staffPage1: staff(page: 1, perPage: 50) {
                        edges {
                            node { id name { full native alternative } }
                        }
                    }
                    staffPage2: staff(page: 2, perPage: 50) {
                        edges {
                            node { id name { full native alternative } }
                        }
                    }
                }
            }
        """.trimIndent()
        val variables = JSONObject()
            .put("idMal", malId)
            .put("type", MalJikanMediaSupport.aniListMediaType(mediaType))
        val response = runCatching {
            withContext(Dispatchers.IO) {
                KitsugiApiBase.executeAniListQuery(query, variables)
            }
        }.getOrNull() ?: return null
        val media = runCatching {
            JSONObject(response).optJSONObject("data")?.optJSONObject("Media")
        }.getOrNull() ?: return null

        fun parseNameObject(name: JSONObject?): NameVariant? {
            if (name == null) return null
            val full = name.optString("full").trim()
            if (full.isEmpty()) return null
            val native = name.optString("native").trim().takeIf { it.isNotEmpty() }
            val alternatives = mutableListOf<String>()
            name.optJSONArray("alternative")?.let { alt ->
                for (j in 0 until alt.length()) {
                    alt.optString(j).trim().takeIf { it.isNotEmpty() }?.let { alternatives.add(it) }
                }
            }
            return NameVariant(full = full, native = native, alternatives = alternatives)
        }

        fun mergedEdges(vararg pageKeys: String): JSONArray {
            val merged = JSONArray()
            pageKeys.forEach { key ->
                val edges = media.optJSONObject(key)?.optJSONArray("edges") ?: return@forEach
                for (index in 0 until edges.length()) {
                    edges.optJSONObject(index)?.let { merged.put(it) }
                }
            }
            return merged
        }

        fun parseEdges(edges: JSONArray): List<NameVariant> =
            (0 until edges.length()).mapNotNull { i ->
                parseNameObject(edges.optJSONObject(i)?.optJSONObject("node")?.optJSONObject("name"))
            }

        val charactersEdges = mergedEdges("charactersPage1", "charactersPage2")
        val staffEdges = mergedEdges("staffPage1", "staffPage2")
        val characters = parseEdges(charactersEdges)
        val staff = parseEdges(staffEdges)

        val voiceActors = mutableListOf<NameVariant>()
        for (i in 0 until charactersEdges.length()) {
            val vaArray = charactersEdges.optJSONObject(i)?.optJSONArray("voiceActors") ?: continue
            for (j in 0 until vaArray.length()) {
                val variant = parseNameObject(vaArray.optJSONObject(j)?.optJSONObject("name")) ?: continue
                voiceActors.add(variant)
            }
        }

        val result = MediaNames(
            characters = characters,
            voiceActors = voiceActors.distinctBy { it.matchKeys },
            staff = staff
        )
        if (cache.size > 128) cache.clear()
        cache[malId] = result
        return result
    }

    /**
     * Özgün (native) ada göre birebir eşleşme arar; `fallbackName` (örn. liste adının
     * kendisi) de anahtar olarak denenir. Bulunamazsa `null` döner.
     */
    fun findByNative(
        list: List<NameVariant>,
        nativeName: String?,
        fallbackName: String? = null
    ): NameVariant? {
        val keys = listOfNotNull(
            nativeName?.trim()?.takeIf { it.isNotEmpty() },
            fallbackName?.trim()?.takeIf { it.isNotEmpty() }
        ).mapNotNull { normalizeKey(it) }.toSet()
        if (keys.isEmpty()) return null
        return list.firstOrNull { variant -> variant.matchKeys.any { it in keys } }
    }
}
