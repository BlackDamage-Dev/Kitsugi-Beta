package com.kitsugi.animelist.data.cloudstream

import android.util.Log
import com.kitsugi.animelist.core.memory.BoundedCache
import com.kitsugi.animelist.data.remote.JikanGateway
import com.kitsugi.animelist.data.remote.JikanResult
import com.kitsugi.animelist.data.remote.KitsugiApiBase
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Eklenti (CloudStream) aramasında kullanılacak **kanonik başlık evreni**.
 *
 * NEDEN GEREKLİ: Akış ekranına gelen başlık kümesi detay sayfasının hangi kaynaktan
 * açıldığına göre değişiyordu. Kullanıcı raporu (2026-11-02): aynı anime **AniList**
 * kaydından açıldığında Anizm / TrAnimeIzle / AnimeciX akış verebiliyor, **MAL**
 * kaydından açıldığında veremiyor. Sebep: MAL detayı daha dar bir başlık kümesi
 * gönderiyordu (`titleRomaji` / `titleNative` boş kalıyordu, TMDB `tr-TR` başlığı
 * yalnızca TMDB kimliği çözülünce ekleniyordu).
 *
 * ÇÖZÜM: Hangi kayıttan gelinirse gelinsin, sahip olduğumuz kimliklerden (MAL ve/veya
 * AniList) **iki kaynağın başlık birleşimi** çıkarılır:
 *   - AniList GraphQL: `title { romaji english native }` + `synonyms`
 *     (yalnızca MAL kimliği varsa `Media(idMal: ...)` ile sorgulanır — ek ARM turu yok)
 *   - Jikan v4: `titles[].title` (Default / English / Japanese / Synonym / Turkish …)
 *
 * Böylece MAL kaydı AniList kaydıyla **aynı ya da daha geniş** başlık kümesiyle arar;
 * Türk eklentilerinin site başlığı (Romaji + Türkçe) her iki yolda da kapsanır.
 *
 * Maliyet: Sonuçlar oturum-önbelleğidir ([BoundedCache]); 164 eklenti tek isteği
 * paylaşır. Eşzamanlı çağrılar [fetchMutex] ile tek isteğe indirilir. İki sağlayıcı da
 * yavaşsa 4 sn tavanı aşan sonuç önbelleğe YAZILMAZ (geçici yavaşlık kalıcı boşluk olmasın).
 */
internal object CanonicalTitleAliases {
    private const val TAG = "CanonicalTitleAliases"

    /** Gerçek MAL kimlik aralığı; offset'li stable kimlikler (100M+/300M+/500M+) dışarıda. */
    private const val MAL_ID_LIMIT = 100_000_000

    /** Aşırı uzun sorgu listesi eklenti aramasını yavaşlatmasın. */
    private const val MAX_ALIASES = 12

    /** Zenginleştirme bir "bonus"tur: sağlayıcı yavaşsa eklenti araması bekletilmez. */
    private const val ALIAS_TIMEOUT_MS = 4_000L

    private val aliasCache = BoundedCache<String, List<String>>("cs.titleAliases", 400)
    private val fetchMutex = Mutex()

    /**
     * @param malId      gerçek MAL kimliği (varsa)
     * @param aniListId  gerçek AniList kimliği (varsa)
     * @param mediaType  Manga'da ağa çıkılmaz
     * @return normalize edilmiş, tekrarsız başlık listesi; hiç kimlik yoksa boş
     */
    suspend fun aliasesFor(malId: Int?, aniListId: Int?, mediaType: MediaType?): List<String> {
        if (mediaType == MediaType.Manga) return emptyList()
        val safeMal = malId?.takeIf { it in 1 until MAL_ID_LIMIT }
        val safeAniList = aniListId?.takeIf { it > 0 }
        if (safeMal == null && safeAniList == null) return emptyList()

        val key = "m=$safeMal|a=$safeAniList"
        aliasCache[key]?.let { return it }
        return fetchMutex.withLock {
            aliasCache[key] ?: run {
                val titles = withTimeoutOrNull(ALIAS_TIMEOUT_MS) {
                    try {
                        fetchTitles(safeMal, safeAniList)
                    } catch (cancel: kotlinx.coroutines.CancellationException) {
                        throw cancel
                    } catch (e: Exception) {
                        Log.w(TAG, "Başlık diğer adları alınamadı (mal=$safeMal aniList=$safeAniList): ${e.message}")
                        null
                    }
                }
                if (titles != null) aliasCache[key] = titles
                titles ?: emptyList()
            }
        }
    }

    private suspend fun fetchTitles(malId: Int?, aniListId: Int?): List<String> {
        val collected = LinkedHashSet<String>()
        collected += aniListTitles(malId, aniListId)
        collected += jikanTitles(malId)
        return collected
            .map { it.trim() }
            .filter { it.length >= 2 }
            .distinctBy { CsTitleMatcher.normalizeTitleForMatch(it) }
            .take(MAX_ALIASES)
    }

    // ── AniList ──────────────────────────────────────────────────────────────

    private suspend fun aniListTitles(malId: Int?, aniListId: Int?): List<String> {
        val filterExpr: String
        val paramDecl: String
        val variables: JSONObject
        when {
            aniListId != null -> {
                paramDecl = "\$id: Int"
                filterExpr = "id: \$id"
                variables = JSONObject().put("id", aniListId)
            }
            malId != null -> {
                // MAL kimliğiyle doğrudan AniList kaydı — ek ARM çözümü gerekmez.
                paramDecl = "\$idMal: Int"
                filterExpr = "idMal: \$idMal"
                variables = JSONObject().put("idMal", malId)
            }
            else -> return emptyList()
        }
        val query = """
            query (${'$'}type: MediaType, $paramDecl) {
                Media($filterExpr, type: ${'$'}type) {
                    title { romaji english native }
                    synonyms
                }
            }
        """.trimIndent()
        variables.put("type", "ANIME")

        val body = KitsugiApiBase.executeAniListQuery(query, variables) ?: return emptyList()
        return runCatching { parseAniListTitles(body) }.getOrElse {
            Log.d(TAG, "AniList başlık yanıtı ayrıştırılamadı: ${it.message}")
            emptyList()
        }
    }

    /** Saf fonksiyon — birim testleriyle doğrulanır. */
    internal fun parseAniListTitles(body: String): List<String> {
        val media = runCatching { JSONObject(body) }
            .getOrNull()
            ?.optJSONObject("data")
            ?.optJSONObject("Media")
            ?: return emptyList()
        val out = LinkedHashSet<String>()
        val titleObj = media.optJSONObject("title")
        listOf("romaji", "english", "native").forEach { key ->
            titleObj?.optString(key)?.takeIf { it.isNotBlank() }?.let { out += it }
        }
        media.optJSONArray("synonyms")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optString(i).takeIf { it.isNotBlank() }?.let { out += it }
            }
        }
        return out.toList()
    }

    // ── MAL / Jikan ──────────────────────────────────────────────────────────

    private suspend fun jikanTitles(malId: Int?): List<String> {
        if (malId == null) return emptyList()
        val result = JikanGateway.fetch(
            "https://api.jikan.moe/v4/anime/$malId",
            JikanGateway.Priority.BACKGROUND,
            maxRetries = 1
        )
        if (result !is JikanResult.Ok) {
            Log.d(TAG, "MAL $malId başlıkları alınamadı")
            return emptyList()
        }
        return runCatching { parseJikanTitles(result.body) }.getOrDefault(emptyList())
    }

    /**
     * Jikan `data` nesnesinden başlık diğer adlarını çıkarır. `titles` dizisi varsa tüm
     * türler (Default/English/Japanese/Synonym/Turkish…) kullanılır; yoksa eski alanlar
     * (`title`, `title_english`, `title_japanese`) okunur.
     *
     * Saf fonksiyon — birim testleriyle doğrulanır.
     */
    internal fun parseJikanTitles(body: String): List<String> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val data = root.optJSONObject("data") ?: return emptyList()
        val collected = LinkedHashSet<String>()
        data.optJSONArray("titles")?.let { titles ->
            for (i in 0 until titles.length()) {
                titles.optJSONObject(i)?.optString("title")?.takeIf { it.isNotBlank() }?.let { collected += it }
            }
        }
        listOf("title", "title_english", "title_japanese").forEach { key ->
            data.optString(key).takeIf { it.isNotBlank() }?.let { collected += it }
        }
        return collected.toList()
    }

    /** Test / ayar değişikliği sonrası önbelleği boşaltır. */
    fun clear() {
        aliasCache.clear()
    }
}
