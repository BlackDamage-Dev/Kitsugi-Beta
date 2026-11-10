package com.kitsugi.animelist.data.remote

import android.util.Log
import androidx.room.withTransaction
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.local.MediaEntryEntity
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.utils.PreferenceHelpers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections
import java.util.Locale

/**
 * Simkl ve Shikimori kütüphane satırlarındaki eksik İngilizce / özgün başlık varyantlarını tamamlar.
 *
 * ## Kök neden
 * - **Simkl** (`GET /sync/all-items/anime`) anime başlığını yalnızca Romaji olarak döndürür
 *   (ör. `title = "Kaijuu 8-gou"`, `titleEnglish = null`, `titleJapanese = null`).
 * - **Shikimori** (`GET /api/v2/user_rates`) gömülü `anime`/`manga` nesnesinde yalnızca `name`
 *   (Romaji) ve `russian` taşır; `english`/`japanese` dizileri REST liste yanıtında yer almaz.
 *
 * Kullanıcının AniList/MAL/Kitsu hesabı da bağlıysa `MyListLibraryGrouping` kardeş satırlardan bu
 * adları anında bellekte tamamlar; ancak kullanıcı **yalnızca** Simkl veya Shikimori bağlamışsa ya da
 * ilgili yapım yalnızca o kaynakta ekliyse veritabanında İngilizce ad hiç bulunmaz. Bu sınıf:
 *  1. Önce yerel veritabanındaki aynı `(tür, malId)` kardeşlerinden eksik adları ağsız kopyalar,
 *  2. Hâlâ `titleEnglish` alanı boş kalan `1..99_999_999` gerçek MAL kimlikli Anime/Manga satırlarını
 *     AniList GraphQL `media(idMal_in: [...])` ucuyla 50'lik paketler hâlinde toplu çözer ve
 *     dış senkron tetiklemeden doğrudan `MediaEntryDao.updateAll` ile yazar.
 */
internal object MalAxisLibraryTitleHydrator {

    private const val TAG = "MalAxisTitleHydrator"
    private const val REAL_MAL_MIN = 1
    private const val REAL_MAL_MAX = 99_999_999
    private const val BATCH_SIZE = 50
    private const val MAX_BATCHES_PER_PASS = 10
    private const val MAX_NO_ENGLISH_MEMORY = 4_000

    private val mutex = Mutex()

    /** AniList'te sorgulanıp ayrı bir İngilizce başlığı olmadığı doğrulanmış `"ANIME:12345"` anahtarları. */
    private val checkedWithoutEnglish: MutableSet<String> =
        Collections.synchronizedSet(LinkedHashSet())

    internal data class ResolvedMalTitles(
        val romaji: String?,
        val english: String?,
        val native: String?
    )

    private val PLACEHOLDER_REGEX =
        Regex("^(Bangumi|Shikimori|Kitsu Anime|Kitsu Manga)\\s*#\\d+$", RegexOption.IGNORE_CASE)

    private fun isPlaceholder(value: String?): Boolean {
        if (value.isNullOrBlank()) return true
        val trimmed = value.trim()
        if (trimmed.equals("Başlıksız", ignoreCase = true) || trimmed.equals("Untitled", ignoreCase = true)) {
            return true
        }
        return PLACEHOLDER_REGEX.matches(trimmed)
    }

    private fun isLatinOrNumeric(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        if (PreferenceHelpers.isLatinText(value)) return true
        return value.none { it.isLetter() } && value.any { it.isDigit() }
    }

    suspend fun hydrate(database: KitsugiDatabase): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                hydrateLocked(database)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Başlık tamamlama atlandı: ${error.message}")
                0
            }
        }
    }

    private suspend fun hydrateLocked(database: KitsugiDatabase): Int {
        val dao = database.mediaEntryDao()
        val allEntities = dao.getAll()
        if (allEntities.isEmpty()) return 0

        val targetRows = allEntities.filter { isEligibleTarget(it) }
        if (targetRows.isEmpty()) return 0

        // 1. Adım: Yerel kardeş satırlardan ağa çıkmadan başlık varyantlarını topla.
        val localTitlesByKey = HashMap<String, ResolvedMalTitles>()
        for (entity in allEntities) {
            if (entity.source.equals("tmdb", ignoreCase = true)) continue
            val malId = entity.malId?.takeIf { it in REAL_MAL_MIN..REAL_MAL_MAX } ?: continue
            val mediaType = mediaTypeOf(entity) ?: continue
            val key = "${mediaType.name}:$malId"

            val romaji = entity.title.takeIf { !isPlaceholder(it) && isLatinOrNumeric(it) }
            val english = entity.titleEnglish?.takeIf { !isPlaceholder(it) && isLatinOrNumeric(it) }
            val native = entity.titleJapanese?.takeIf { !isPlaceholder(it) }
            if (romaji == null && english == null && native == null) continue

            val prev = localTitlesByKey[key]
            localTitlesByKey[key] = ResolvedMalTitles(
                romaji = prev?.romaji ?: romaji,
                english = prev?.english ?: english,
                native = prev?.native ?: native
            )
        }

        // 2. Adım: Yerel kardeşten İngilizce adı çözülemeyen MAL kimliklerini AniList'ten 50'li paketlerle çek.
        val remoteNeededByType = EnumMapLike()
        for (entity in targetRows) {
            val malId = entity.malId ?: continue
            val mediaType = mediaTypeOf(entity) ?: continue
            val key = "${mediaType.name}:$malId"
            val hasEnglishAlready = isLatinOrNumeric(entity.titleEnglish)
            val hasLocalEnglish = localTitlesByKey[key]?.english != null
            if (!hasEnglishAlready && !hasLocalEnglish && key !in checkedWithoutEnglish) {
                remoteNeededByType.add(mediaType, malId)
            }
        }

        val remoteTitlesByKey = HashMap<String, ResolvedMalTitles>()
        for (mediaType in listOf(MediaType.Anime, MediaType.Manga)) {
            val ids = remoteNeededByType.idsFor(mediaType)
            if (ids.isEmpty()) continue
            val batches = ids.chunked(BATCH_SIZE).take(MAX_BATCHES_PER_PASS)
            for (batch in batches) {
                val fetched = fetchAniListTitlesBatch(batch, mediaType) ?: continue
                for (malId in batch) {
                    val key = "${mediaType.name}:$malId"
                    val resolved = fetched[malId]
                    if (resolved != null) {
                        remoteTitlesByKey[key] = resolved
                        if (resolved.english == null) {
                            rememberNoEnglish(key)
                        }
                    } else {
                        rememberNoEnglish(key)
                    }
                }
            }
        }

        val updates = ArrayList<MediaEntryEntity>()
        for (entity in targetRows) {
            val malId = entity.malId ?: continue
            val mediaType = mediaTypeOf(entity) ?: continue
            val key = "${mediaType.name}:$malId"
            val local = localTitlesByKey[key]
            val remote = remoteTitlesByKey[key]
            val merged = mergeResolved(entity, local, remote)
            if (merged != entity) {
                updates += merged
            }
        }

        if (updates.isEmpty()) return 0
        database.withTransaction {
            dao.updateAll(updates)
        }
        return updates.size
    }

    internal fun mergeResolved(
        entity: MediaEntryEntity,
        local: ResolvedMalTitles?,
        remote: ResolvedMalTitles?
    ): MediaEntryEntity {
        val candidateRomaji = local?.romaji ?: remote?.romaji
        val candidateEnglish = local?.english ?: remote?.english
        val candidateNative = local?.native ?: remote?.native

        val nextTitle = if (isPlaceholder(entity.title) || !isLatinOrNumeric(entity.title)) {
            candidateRomaji ?: candidateEnglish ?: entity.title
        } else {
            entity.title
        }
        val nextEnglish = if (!isLatinOrNumeric(entity.titleEnglish)) {
            candidateEnglish ?: entity.titleEnglish
        } else {
            entity.titleEnglish
        }
        val nextJapanese = if (entity.titleJapanese.isNullOrBlank()) {
            candidateNative ?: entity.titleJapanese
        } else {
            entity.titleJapanese
        }

        return if (
            nextTitle == entity.title &&
            nextEnglish == entity.titleEnglish &&
            nextJapanese == entity.titleJapanese
        ) {
            entity
        } else {
            entity.copy(
                title = nextTitle,
                titleEnglish = nextEnglish,
                titleJapanese = nextJapanese
            )
        }
    }

    private fun isEligibleTarget(entity: MediaEntryEntity): Boolean {
        val src = entity.source.trim().lowercase(Locale.ROOT)
        if (src != "simkl" && src != "shikimori" && src != "shiki" && src != "kitsu") return false
        val malId = entity.malId ?: return false
        if (malId !in REAL_MAL_MIN..REAL_MAL_MAX) return false
        if (mediaTypeOf(entity) == null) return false
        val needsEnglish = !isLatinOrNumeric(entity.titleEnglish)
        val needsJapanese = entity.titleJapanese.isNullOrBlank()
        val needsPrimaryLatin = isPlaceholder(entity.title) || !isLatinOrNumeric(entity.title)
        return needsEnglish || needsJapanese || needsPrimaryLatin
    }

    private fun mediaTypeOf(entity: MediaEntryEntity): MediaType? =
        when (entity.type.trim().lowercase(Locale.ROOT)) {
            "anime" -> MediaType.Anime
            "manga", "manhwa", "manhua", "novel", "oneshot" -> MediaType.Manga
            else -> null
        }

    /**
     * `null` dönüşü ağ/yanıt hatası demektir (kimlikler `checkedWithoutEnglish`'e yazılmaz).
     */
    private suspend fun fetchAniListTitlesBatch(
        malIds: List<Int>,
        mediaType: MediaType
    ): Map<Int, ResolvedMalTitles>? {
        if (malIds.isEmpty()) return emptyMap()
        val query = """
            query (${'$'}idsMal: [Int], ${'$'}type: MediaType) {
                Page(page: 1, perPage: 50) {
                    media(idMal_in: ${'$'}idsMal, type: ${'$'}type) {
                        idMal
                        title { romaji english native }
                    }
                }
            }
        """.trimIndent()
        val variables = JSONObject()
            .put("idsMal", JSONArray(malIds))
            .put("type", MalJikanMediaSupport.aniListMediaType(mediaType))

        return runCatching {
            val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return null
            val mediaArray = JSONObject(response)
                .optJSONObject("data")
                ?.optJSONObject("Page")
                ?.optJSONArray("media")
                ?: return null
            val result = HashMap<Int, ResolvedMalTitles>(mediaArray.length())
            for (i in 0 until mediaArray.length()) {
                val node = mediaArray.optJSONObject(i) ?: continue
                val idMal = node.optInt("idMal", 0)
                if (idMal <= 0) continue
                val titleObj = node.optJSONObject("title")
                val romaji = titleObj?.takeUnless { it.isNull("romaji") }
                    ?.optString("romaji")
                    ?.trim()
                    ?.takeIf { isLatinOrNumeric(it) }
                val english = titleObj?.takeUnless { it.isNull("english") }
                    ?.optString("english")
                    ?.trim()
                    ?.takeIf { isLatinOrNumeric(it) }
                val native = titleObj?.takeUnless { it.isNull("native") }
                    ?.optString("native")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                result[idMal] = ResolvedMalTitles(
                    romaji = romaji,
                    english = english,
                    native = native
                )
            }
            result
        }.getOrNull()
    }

    private fun rememberNoEnglish(key: String) {
        synchronized(checkedWithoutEnglish) {
            if (checkedWithoutEnglish.size >= MAX_NO_ENGLISH_MEMORY) {
                checkedWithoutEnglish.clear()
            }
            checkedWithoutEnglish += key
        }
    }

    private class EnumMapLike {
        private val animeIds = LinkedHashSet<Int>()
        private val mangaIds = LinkedHashSet<Int>()

        fun add(type: MediaType, malId: Int) {
            if (type == MediaType.Manga) mangaIds += malId else animeIds += malId
        }

        fun idsFor(type: MediaType): List<Int> =
            if (type == MediaType.Manga) mangaIds.toList() else animeIds.toList()
    }
}
