package com.kitsugi.animelist.data.auth

import android.content.Context
import com.kitsugi.animelist.R
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaIdentity
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull

object AniListSyncManager {
    @Volatile private var cachedAniListUserId: Int? = null
    @Volatile private var cachedAniListToken: String? = null

    fun updateAniListEntry(
        token: String,
        entry: MediaEntry,
        advancedScores: List<Double>? = null,
        context: Context,
        /**
         * Çapraz kimlik katmanının ([CrossProviderIdentityResolver]) önceden çözdüğü AniList
         * medya kimliği. Verilmezse eski zincir (yerel kimlik → MAL `idMal` → ARM) çalışır.
         * Verilse bile yazmadan önce [verifyAniListMediaIdentity] ile doğrulanır.
         */
        knownAniListMediaId: Int? = null
    ): Int? {
        require(entry.type == MediaType.Anime || entry.type == MediaType.Manga) {
            context.getString(R.string.account_sync_anilist_type_unsupported)
        }
        val existingEntryId: Int? = entry.aniListEntryId?.takeIf { it > 0 }

        val mediaId: Int? = if (existingEntryId == null) {
            // Önceden çözülmüş kimlik önce denenir: Bangumi köprüsü bu kimliği AniList'in kendi
            // kataloğunda başlık+yıl puanlamasıyla bulmuş olabilir; buna rağmen yerel zincir
            // (MAL doğrulaması, Jikan başlıkları) aynı kaydı Çince görünen başlık yüzünden
            // reddedebiliyordu ve çözülmüş kimlik çöpe gidiyordu. Doğrulama yine yapılır.
            val verifiedKnownId = knownAniListMediaId?.takeIf { it > 0 }?.takeIf { candidate ->
                val mediaTypeGql = if (entry.type == MediaType.Manga) "MANGA" else "ANIME"
                verifyAniListMediaIdentity(
                    token, candidate, mediaTypeGql, entry,
                    CrossSyncIdentityGuard.aliasesOf(entry), context
                )
            }
            verifiedKnownId ?: resolveAniListMediaId(token = token, entry = entry, context = context)
        } else {
            val verifiedMediaId = resolveAniListMediaIdForEntry(
                token = token,
                entryId = existingEntryId,
                entry = entry,
                context = context,
                knownAniListMediaId = knownAniListMediaId
            ) ?: error(context.getString(R.string.account_sync_anilist_entry_identity_mismatch, entry.title))
            // Existing list-entry IDs are only mutation handles. Verify the attached media before
            // updating status, progress, score, or other fields.
            verifiedMediaId
        }

        // Null yalnızca "AniList'te hangi medya olduğu güvenle çözülemedi" anlamına gelir
        // (atlanacak kayıt). Mutasyonun başarısız olması aşağıda istisna ile bildirilir.
        if (existingEntryId == null && mediaId == null) return null

        val scoreRaw = SyncScores.tenPoint(entry.score)?.times(10)
        val scoreVariableDefinition = if (scoreRaw != null) "${'$'}scoreRaw: Int," else ""
        val scoreArgument = if (scoreRaw != null) "scoreRaw: ${'$'}scoreRaw," else ""
        val query = """
            mutation (
                ${'$'}id: Int,
                ${'$'}mediaId: Int,
                ${'$'}status: MediaListStatus,
                $scoreVariableDefinition
                ${'$'}progress: Int,
                ${'$'}progressVolumes: Int,
                ${'$'}startedAt: FuzzyDateInput,
                ${'$'}completedAt: FuzzyDateInput,
                ${'$'}private: Boolean,
                ${'$'}notes: String,
                ${'$'}repeat: Int,
                ${'$'}hiddenFromStatusLists: Boolean,
                ${'$'}advancedScores: [Float]
            ) {
                SaveMediaListEntry(
                    id: ${'$'}id,
                    mediaId: ${'$'}mediaId,
                    status: ${'$'}status,
                    $scoreArgument
                    progress: ${'$'}progress,
                    progressVolumes: ${'$'}progressVolumes,
                    startedAt: ${'$'}startedAt,
                    completedAt: ${'$'}completedAt,
                    private: ${'$'}private,
                    notes: ${'$'}notes,
                    repeat: ${'$'}repeat,
                    hiddenFromStatusLists: ${'$'}hiddenFromStatusLists,
                    advancedScores: ${'$'}advancedScores
                ) {
                    id
                    status
                    score
                    progress
                }
            }
        """.trimIndent()

        val variables = JSONObject()
        if (existingEntryId != null) {
            variables.put("id", existingEntryId)
        } else {
            variables.put("mediaId", mediaId)
        }
        variables
            .put("status", entry.status.toAniListStatus())
            .put("progress", entry.progress)
            .put("progressVolumes", entry.volumeProgress)
            .put("private", entry.isPrivate)
            .put("notes", entry.notes ?: "")
            .put("repeat", entry.repeatCount)
            .put("hiddenFromStatusLists", entry.isHiddenFromStatusLists)

        if (scoreRaw != null) {
            variables.put("scoreRaw", scoreRaw)
        }

        val startedAt = entry.startDate.toAniListFuzzyDate()
        if (startedAt != null) {
            variables.put("startedAt", startedAt)
        }

        val completedAt = entry.endDate.toAniListFuzzyDate()
        if (completedAt != null) {
            variables.put("completedAt", completedAt)
        }

        if (advancedScores != null) {
            val jsonArray = org.json.JSONArray()
            advancedScores.forEach { jsonArray.put(it) }
            variables.put("advancedScores", jsonArray)
        }

        val response = postAniList(
            token = token,
            query = query,
            variables = variables,
            context = context
        )

        return JSONObject(response)
            .optJSONObject("data")
            ?.optJSONObject("SaveMediaListEntry")
            ?.optInt("id", 0)
            ?.takeIf { it > 0 }
            ?: throw IllegalStateException(
                context.getString(R.string.account_sync_anilist_entry_id_missing, entry.title)
            )
    }

    /**
     * AniList'teki güncel favori durumunu sorgular.
     */
    fun getAniListMediaFavoriteStatus(
        token: String,
        entry: MediaEntry,
        context: Context,
        knownAniListMediaId: Int? = null
    ): Boolean? {
        val aniListMediaId: Int = knownAniListMediaId?.takeIf { it > 0 }
            ?: resolveAniListMediaId(token = token, entry = entry, context = context)
            ?: return null

        val query = """
            query (${'$'}id: Int) {
                Media(id: ${'$'}id) {
                    isFavourite
                }
            }
        """.trimIndent()

        val variables = JSONObject().put("id", aniListMediaId)

        return runSyncCatching {
            val response = postAniList(token = token, query = query, variables = variables, context = context)
            JSONObject(response)
                .optJSONObject("data")
                ?.optJSONObject("Media")
                ?.optBoolean("isFavourite", false)
        }.getOrNull()
    }

    /**
     * AniList'te favori toggle yapar.
     * ToggleFavourite mutasyonu ile anime/manga ID'sine göre toggle eder.
     * Bu fonksiyon sadece isFavorite değeri değiştiğinde çağrılmalı.
     * @return true = başarılı, false = API hatası veya mediaId bulunamadı
     */
    fun toggleAniListFavourite(
        token: String,
        entry: MediaEntry,
        context: Context,
        knownAniListMediaId: Int? = null
    ): Boolean {
        val aniListMediaId: Int? = knownAniListMediaId?.takeIf { it > 0 }
            ?: resolveAniListMediaId(token = token, entry = entry, context = context)

        if (aniListMediaId == null) return false

        val isAnime = entry.type != MediaType.Manga
        val mutationArg = if (isAnime) "\$animeId: Int" else "\$mangaId: Int"
        val mutationParam = if (isAnime) "animeId: \$animeId" else "mangaId: \$mangaId"
        val varKey = if (isAnime) "animeId" else "mangaId"

        val query = """
            mutation ($mutationArg) {
                ToggleFavourite($mutationParam) {
                    anime { nodes { id } }
                    manga { nodes { id } }
                }
            }
        """.trimIndent()

        val variables = JSONObject().put(varKey, aniListMediaId)

        return runSyncCatching {
            postAniList(token = token, query = query, variables = variables, context = context)
            true
        }.getOrDefault(false)
    }

    fun deleteAniListEntry(
        token: String,
        entry: MediaEntry,
        context: Context
    ) {
        val existingEntryId = entry.aniListEntryId?.takeIf { it > 0 }
        val entryId: Int? = if (existingEntryId != null) {
            resolveAniListMediaIdForEntry(token, existingEntryId, entry, context)
                ?: error(context.getString(R.string.account_sync_anilist_entry_identity_mismatch, entry.title))
            existingEntryId
        } else {
            val mediaId = resolveAniListMediaId(token = token, entry = entry, context = context)
                ?: error(context.getString(R.string.account_sync_anilist_media_id_unresolved))
            val userId = fetchAniListUserId(token, context)

            val findQuery = """
                query (
                    ${'$'}mediaId: Int,
                    ${'$'}userId: Int
                ) {
                    MediaList(
                        mediaId: ${'$'}mediaId,
                        userId: ${'$'}userId
                    ) {
                        id
                    }
                }
            """.trimIndent()

            val findVariables = JSONObject()
                .put("mediaId", mediaId)
                .put("userId", userId)

            val findResponse = postAniList(
                token = token,
                query = findQuery,
                variables = findVariables,
                context = context
            )

            JSONObject(findResponse)
                .optJSONObject("data")
                ?.optJSONObject("MediaList")
                ?.optInt("id", 0)
                ?.takeIf { it > 0 }
        }

        entryId ?: return

        val deleteQuery = """
            mutation (
                ${'$'}entryId: Int
            ) {
                DeleteMediaListEntry(
                    id: ${'$'}entryId
                ) {
                    deleted
                }
            }
        """.trimIndent()

        val deleteVariables = JSONObject()
            .put("entryId", entryId)

        postAniList(
            token = token,
            query = deleteQuery,
            variables = deleteVariables,
            context = context
        )
    }

    /** Resolves a list-entry handle only after proving which AniList media it belongs to. */
    private fun resolveAniListMediaIdForEntry(
        token: String,
        entryId: Int,
        entry: MediaEntry,
        context: Context,
        knownAniListMediaId: Int? = null
    ): Int? = runSyncCatching {
        if (entryId <= 0) return@runSyncCatching null
        val query = """
            query (${'$'}id: Int) {
                MediaList(id: ${'$'}id) {
                    id
                    userId
                    mediaId
                    media { id }
                }
            }
        """.trimIndent()
        val response = postAniList(
            token = token,
            query = query,
            variables = JSONObject().put("id", entryId),
            context = context
        )
        val listEntry = JSONObject(response)
            .optJSONObject("data")
            ?.optJSONObject("MediaList") ?: return@runSyncCatching null
        if (listEntry.optInt("id", 0) != entryId) return@runSyncCatching null
        if (listEntry.optInt("userId", 0) != fetchAniListUserId(token, context)) return@runSyncCatching null
        val mediaId = listEntry.optJSONObject("media")?.optInt("id", 0)?.takeIf { it > 0 }
            ?: return@runSyncCatching null
        if (listEntry.optInt("mediaId", 0) != mediaId) return@runSyncCatching null
        // Liste kaydının bağlı olduğu medya, çapraz katmanın çözdüğü kimlikle de doğrulanabilir;
        // yerel zincir başlık/yıl katılıkları yüzünden aynı medyayı bulamayabiliyordu.
        val expectedMediaId = knownAniListMediaId?.takeIf { it > 0 }
            ?: resolveAniListMediaId(token, entry, context)
            ?: return@runSyncCatching null
        mediaId.takeIf { it == expectedMediaId }
    }.getOrNull()

    fun resolveAniListMediaId(
        token: String,
        entry: MediaEntry,
        context: Context
    ): Int? {
        val mediaTypeGql = when (entry.type) {
            MediaType.Anime, MediaType.Movie, MediaType.TvShow -> "ANIME"
            MediaType.Manga -> "MANGA"
        }
        val realMalId = MediaIdentity.realMalId(entry)
        val nativeAniListId = MediaIdentity.aniListId(entry)
        val verifiedTitleAliases = if (nativeAniListId != null) {
            CrossSyncIdentityGuard.aliasesOf(entry)
        } else {
            if (realMalId == null) return null
            val malVerdict = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                CrossSyncIdentityGuard.verifyMalId(
                    malId = realMalId,
                    mediaType = entry.type,
                    localTitles = CrossSyncIdentityGuard.aliasesOf(entry),
                    localYear = entry.year
                )
            } as? CrossSyncIdentityGuard.Verdict.Verified ?: return null
            malVerdict.matchedLocalTitles
        }
        val rawKitsuId = MediaIdentity.kitsuId(entry)

        // A stored AniList media ID is source-native but still checked against type/title/year before add/favorite writes.
        if (nativeAniListId != null) {
            return nativeAniListId.takeIf {
                verifyAniListMediaIdentity(token, it, mediaTypeGql, entry, verifiedTitleAliases, context)
            }
        }

        // Resolve by real MAL ID, then verify AniList's returned type/title/year before writing.
        if (realMalId != null) {
            val query = """
                query (${'$'}idMal: Int, ${'$'}type: MediaType) {
                    Media(idMal: ${'$'}idMal, type: ${'$'}type) { id }
                }
            """.trimIndent()
            val variables = JSONObject().put("idMal", realMalId).put("type", mediaTypeGql)
            val resolved = runSyncCatching {
                val response = postAniList(token = token, query = query, variables = variables, context = context)
                JSONObject(response).optJSONObject("data")?.optJSONObject("Media")?.optInt("id", 0)?.takeIf { it > 0 }
            }.getOrNull()
            if (resolved != null) {
                return resolved.takeIf {
                    verifyAniListMediaIdentity(token, it, mediaTypeGql, entry, verifiedTitleAliases, context)
                }
            }
        }

        // ARM is another cross-reference, not identity proof. Verify its candidate in AniList as well.
        val armId = if (realMalId != null || rawKitsuId != null) {
            runSyncCatching {
                kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                    com.kitsugi.animelist.data.remote.KitsugiIdResolver.resolveIds(
                        malId = realMalId,
                        aniListId = null,
                        tmdbId = null,
                        mediaType = entry.type,
                        kitsuId = rawKitsuId
                    ).aniListId
                }
            }.getOrNull()
        } else null
        if (
            armId != null && armId > 0 &&
            verifyAniListMediaIdentity(token, armId, mediaTypeGql, entry, verifiedTitleAliases, context)
        ) {
            return armId
        }

        // A search rank is not an identity mapping. Never mutate the first fuzzy result.
        return null
    }

    private fun verifyAniListMediaIdentity(
        token: String,
        mediaId: Int,
        expectedType: String,
        entry: MediaEntry,
        expectedTitles: Collection<String>,
        context: Context
    ): Boolean = runSyncCatching {
        val query = """
            query (${'$'}id: Int) {
                Media(id: ${'$'}id) {
                    id
                    idMal
                    type
                    title { romaji english native }
                    startDate { year }
                }
            }
        """.trimIndent()
        val response = postAniList(
            token = token,
            query = query,
            variables = JSONObject().put("id", mediaId),
            context = context
        )
        val media = JSONObject(response).optJSONObject("data")?.optJSONObject("Media") ?: return@runSyncCatching false
        if (media.optInt("id", 0) != mediaId || media.optString("type") != expectedType) return@runSyncCatching false
        val expectedMalId = MediaIdentity.realMalId(entry)
        if (expectedMalId != null && media.optInt("idMal", 0) != expectedMalId) return@runSyncCatching false
        val expectedAniListId = MediaIdentity.aniListId(entry)
        if (expectedAniListId != null && expectedAniListId != mediaId) return@runSyncCatching false
        val title = media.optJSONObject("title")
        val remoteTitles = listOf("romaji", "english", "native")
            .mapNotNull { title?.optString(it)?.takeIf(String::isNotBlank) }
        val remoteYear = media.optJSONObject("startDate")?.optInt("year", 0)?.takeIf { it > 0 }
        CrossSyncIdentityGuard.titlesAreIdentityEquivalent(expectedTitles, remoteTitles) &&
            CrossSyncIdentityGuard.yearsCompatible(entry.year, remoteYear)
    }.getOrDefault(false)

    /**
     * Resolves an AniList source row's official `idMal` only after verifying AniList's own record
     * agrees with the source-native ID, media type, strict title aliases, and exact known year.
     */
    internal fun resolveVerifiedMalIdFromAniList(
        token: String?,
        entry: MediaEntry,
        aniListId: Int
    ): Int? = runSyncCatching {
        if (aniListId <= 0 || MediaIdentity.aniListId(entry) != aniListId) return@runSyncCatching null
        val expectedType = when (entry.type) {
            MediaType.Anime -> "ANIME"
            MediaType.Manga -> "MANGA"
            else -> return@runSyncCatching null
        }
        val query = """
            query (${'$'}id: Int) {
                Media(id: ${'$'}id) {
                    id
                    idMal
                    type
                    title { romaji english native }
                    startDate { year }
                }
            }
        """.trimIndent()
        val response = postAniList(token = token, query = query, variables = JSONObject().put("id", aniListId))
        val media = JSONObject(response).optJSONObject("data")?.optJSONObject("Media")
            ?: return@runSyncCatching null
        if (media.optInt("id", 0) != aniListId || media.optString("type") != expectedType) return@runSyncCatching null
        val malId = media.optInt("idMal", 0).takeIf { it in 1..99_999_999 }
            ?: return@runSyncCatching null
        val title = media.optJSONObject("title")
        val remoteTitles = listOf("romaji", "english", "native")
            .mapNotNull { title?.optString(it)?.takeIf(String::isNotBlank) }
        val remoteYear = media.optJSONObject("startDate")?.optInt("year", 0)?.takeIf { it > 0 }
        if (!CrossSyncIdentityGuard.titlesAreIdentityEquivalent(CrossSyncIdentityGuard.aliasesOf(entry), remoteTitles)) return@runSyncCatching null
        if (!CrossSyncIdentityGuard.yearsCompatible(entry.year, remoteYear)) return@runSyncCatching null
        val storedMalId = MediaIdentity.realMalId(entry)
        malId.takeIf { storedMalId == null || storedMalId == it }
    }.getOrNull()

    /**
     * AniList-sourced list rows must prove their native list handle belongs to the current viewer,
     * even when the stored identity is an AniList synthetic ID. This resolves the attached media,
     * checks account ownership, and confirms its official `idMal`, type, title aliases, and exact
     * year before returning the MAL ID.
     */
    internal fun resolveVerifiedMalIdFromAniListEntry(
        token: String?,
        entry: MediaEntry,
        expectedMalId: Int?
    ): Int? = runSyncCatching {
        val listEntryId = entry.aniListEntryId?.takeIf { it > 0 } ?: return@runSyncCatching null
        if (token.isNullOrBlank()) return@runSyncCatching null
        if (expectedMalId != null && (expectedMalId !in 1..99_999_999 || MediaIdentity.realMalId(entry) != expectedMalId)) return@runSyncCatching null
        val expectedType = when (entry.type) {
            MediaType.Anime -> "ANIME"
            MediaType.Manga -> "MANGA"
            else -> return@runSyncCatching null
        }
        val query = """
            query (${'$'}id: Int) {
                Viewer { id }
                MediaList(id: ${'$'}id) {
                    id
                    userId
                    media {
                        id
                        idMal
                        type
                        title { romaji english native }
                        startDate { year }
                    }
                }
            }
        """.trimIndent()
        val response = JSONObject(postAniList(token, query, JSONObject().put("id", listEntryId)))
        val data = response.optJSONObject("data") ?: return@runSyncCatching null
        val viewerId = data.optJSONObject("Viewer")?.optInt("id", 0)?.takeIf { it > 0 }
            ?: return@runSyncCatching null
        val listEntry = data.optJSONObject("MediaList") ?: return@runSyncCatching null
        if (listEntry.optInt("id", 0) != listEntryId || listEntry.optInt("userId", 0) != viewerId) return@runSyncCatching null
        val media = listEntry.optJSONObject("media") ?: return@runSyncCatching null
        val resolvedMalId = media.optInt("idMal", 0).takeIf { it in 1..99_999_999 }
            ?: return@runSyncCatching null
        if (media.optInt("id", 0) <= 0 || media.optString("type") != expectedType) return@runSyncCatching null
        if (expectedMalId != null && resolvedMalId != expectedMalId) return@runSyncCatching null
        val title = media.optJSONObject("title")
        val remoteTitles = listOf("romaji", "english", "native")
            .mapNotNull { title?.optString(it)?.takeIf(String::isNotBlank) }
        val remoteYear = media.optJSONObject("startDate")?.optInt("year", 0)?.takeIf { it > 0 }
        if (!CrossSyncIdentityGuard.titlesAreIdentityEquivalent(CrossSyncIdentityGuard.aliasesOf(entry), remoteTitles)) return@runSyncCatching null
        if (!CrossSyncIdentityGuard.yearsCompatible(entry.year, remoteYear)) return@runSyncCatching null
        resolvedMalId
    }.getOrNull()

    /**
     * AniList Media ID'sinden gerçek MAL ID'yi sorgular.
     */
    fun resolveMalIdFromAniList(
        token: String?,
        aniListId: Int
    ): Int? {
        val query = """
            query (${'$'}id: Int) {
                Media(id: ${'$'}id) {
                    id
                    idMal
                }
            }
        """.trimIndent()
        val variables = JSONObject().put("id", aniListId)
        return runSyncCatching {
            val response = postAniList(token = token, query = query, variables = variables)
            val media = JSONObject(response)
                .optJSONObject("data")
                ?.optJSONObject("Media") ?: return@runSyncCatching null
            if (media.optInt("id", 0) != aniListId) return@runSyncCatching null
            media.optInt("idMal", 0).takeIf { it in 1..99_999_999 }
        }.getOrNull()
    }

    fun fetchAniListUserId(
        token: String,
        context: Context
    ): Int {
        if (token == cachedAniListToken && cachedAniListUserId != null) {
            return cachedAniListUserId!!
        }

        val query = """
            query {
                Viewer {
                    id
                }
            }
        """.trimIndent()

        val response = postAniList(
            token = token,
            query = query,
            variables = JSONObject(),
            context = context
        )

        val userId = JSONObject(response)
            .optJSONObject("data")
            ?.optJSONObject("Viewer")
            ?.optInt("id", 0)
            ?.takeIf { it > 0 }
            ?: throw IllegalStateException(
                context.getString(R.string.account_sync_anilist_user_id_unavailable)
            )

        cachedAniListToken = token
        cachedAniListUserId = userId
        return userId
    }

    data class ViewerOptions(
        val scoreFormat: String,
        val animeCategories: List<String>,
        val animeEnabled: Boolean,
        val mangaCategories: List<String>,
        val mangaEnabled: Boolean
    )

    fun getViewerOptions(token: String): ViewerOptions? {
        val query = """
            query {
                Viewer {
                    mediaListOptions {
                        scoreFormat
                        animeList {
                            advancedScoring
                            advancedScoringEnabled
                        }
                        mangaList {
                            advancedScoring
                            advancedScoringEnabled
                        }
                    }
                }
            }
        """.trimIndent()

        return try {
            val response = postAniList(token = token, query = query, variables = JSONObject())
            val viewer = JSONObject(response)
                .optJSONObject("data")
                ?.optJSONObject("Viewer")
            val mediaListOptions = viewer?.optJSONObject("mediaListOptions")
            val scoreFormat = mediaListOptions?.optString("scoreFormat", "POINT_10") ?: "POINT_10"
            
            val animeList = mediaListOptions?.optJSONObject("animeList")
            val animeEnabled = animeList?.optBoolean("advancedScoringEnabled", false) ?: false
            val animeCatsArray = animeList?.optJSONArray("advancedScoring")
            val animeCategories = mutableListOf<String>()
            if (animeCatsArray != null) {
                for (i in 0 until animeCatsArray.length()) {
                    animeCategories.add(animeCatsArray.optString(i))
                }
            }

            val mangaList = mediaListOptions?.optJSONObject("mangaList")
            val mangaEnabled = mangaList?.optBoolean("advancedScoringEnabled", false) ?: false
            val mangaCatsArray = mangaList?.optJSONArray("advancedScoring")
            val mangaCategories = mutableListOf<String>()
            if (mangaCatsArray != null) {
                for (i in 0 until mangaCatsArray.length()) {
                    mangaCategories.add(mangaCatsArray.optString(i))
                }
            }

            ViewerOptions(
                scoreFormat = scoreFormat,
                animeCategories = animeCategories,
                animeEnabled = animeEnabled,
                mangaCategories = mangaCategories,
                mangaEnabled = mangaEnabled
            )
        } catch (e: java.lang.Exception) {
            null
        }
    }

    data class MediaListMetadata(
        val customLists: Map<String, Boolean>?,
        val advancedScores: Map<String, Double>?
    )

    fun getMediaListMetadata(
        token: String,
        entryId: Int,
        userId: Int
    ): MediaListMetadata? {
        val query = """
            query (${'$'}id: Int, ${'$'}userId: Int) {
                MediaList(id: ${'$'}id, userId: ${'$'}userId) {
                    customLists
                    advancedScores
                }
            }
        """.trimIndent()

        val variables = JSONObject()
            .put("id", entryId)
            .put("userId", userId)

        return try {
            val response = postAniList(token = token, query = query, variables = variables)
            val mediaList = JSONObject(response)
                .optJSONObject("data")
                ?.optJSONObject("MediaList") ?: return null

            val jsonCustomLists = mediaList.optJSONObject("customLists")
            val customListsMap = mutableMapOf<String, Boolean>()
            if (jsonCustomLists != null) {
                val keys = jsonCustomLists.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    customListsMap[key] = jsonCustomLists.optBoolean(key, false)
                }
            }

            val jsonAdvancedScores = mediaList.optJSONObject("advancedScores")
            val advancedScoresMap = mutableMapOf<String, Double>()
            if (jsonAdvancedScores != null) {
                val keys = jsonAdvancedScores.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    advancedScoresMap[key] = jsonAdvancedScores.optDouble(key, 0.0)
                }
            }

            MediaListMetadata(customLists = customListsMap, advancedScores = advancedScoresMap)
        } catch (e: java.lang.Exception) {
            null
        }
    }

    fun getViewerCustomLists(
        token: String,
        isManga: Boolean
    ): List<String>? {
        val query = """
            query {
                Viewer {
                    mediaListOptions {
                        animeList {
                            customLists
                        }
                        mangaList {
                            customLists
                        }
                    }
                }
            }
        """.trimIndent()

        return try {
            val response = postAniList(token = token, query = query, variables = JSONObject())
            val viewer = JSONObject(response)
                .optJSONObject("data")
                ?.optJSONObject("Viewer")
            val listOptions = viewer?.optJSONObject("mediaListOptions")
            val list = if (isManga) {
                listOptions?.optJSONObject("mangaList")
            } else {
                listOptions?.optJSONObject("animeList")
            }
            val customListsArray = list?.optJSONArray("customLists")
            val result = mutableListOf<String>()
            if (customListsArray != null) {
                for (i in 0 until customListsArray.length()) {
                    val item = customListsArray.optString(i)
                    if (!item.isNullOrBlank()) {
                        result.add(item)
                    }
                }
            }
            result
        } catch (e: java.lang.Exception) {
            null
        }
    }

    /**
     * AniHyou "Özel Listeler" paritesi: hesap genelindeki özel liste adlarını günceller.
     * UpdateUser mutasyonu anime+manga liste seçeneklerine aynı ad listesini yazar.
     */
    fun updateCustomLists(token: String, customLists: List<String>): Boolean {
        val query = """
            mutation (${'$'}animeLists: [String], ${'$'}mangaLists: [String]) {
                UpdateUser(
                    animeListOptions: { customLists: ${'$'}animeLists },
                    mangaListOptions: { customLists: ${'$'}mangaLists }
                ) {
                    id
                }
            }
        """.trimIndent()
        val variables = JSONObject()
            .put("animeLists", org.json.JSONArray(customLists))
            .put("mangaLists", org.json.JSONArray(customLists))
        return try {
            val response = postAniList(token = token, query = query, variables = variables)
            JSONObject(response).optJSONObject("data")?.optJSONObject("UpdateUser") != null
        } catch (e: java.lang.Exception) {
            false
        }
    }

    fun updateEntryCustomLists(
        token: String,
        mediaId: Int?,
        entryId: Int?,
        customLists: List<String>
    ): Boolean {
        val query = """
            mutation (${'$'}id: Int, ${'$'}mediaId: Int, ${'$'}customLists: [String]) {
                SaveMediaListEntry(id: ${'$'}id, mediaId: ${'$'}mediaId, customLists: ${'$'}customLists) {
                    id
                }
            }
        """.trimIndent()

        val jsonArray = org.json.JSONArray()
        customLists.forEach { jsonArray.put(it) }

        val variables = JSONObject()
        if (entryId != null && entryId > 0) {
            variables.put("id", entryId)
        } else if (mediaId != null) {
            variables.put("mediaId", mediaId)
        } else {
            return false
        }
        variables.put("customLists", jsonArray)

        return try {
            postAniList(token = token, query = query, variables = variables)
            true
        } catch (e: java.lang.Exception) {
            false
        }
    }

    private fun postAniList(
        token: String?,
        query: String,
        variables: JSONObject,
        context: Context? = null
    ): String {
        // Kotayı görünmez şekilde tüketen doğrudan POST: kapıdan geçir.
        if (!com.kitsugi.animelist.data.remote.KitsugiApiBase.aniListGateBlocking(
                com.kitsugi.animelist.data.remote.AniListQuotaGate.Priority.BACKGROUND
            )
        ) {
            throw IllegalStateException("AniList kotası dolu; eşitleme sonraki tura ertelendi.")
        }
        val payload = JSONObject()
            .put("query", query)
            .put("variables", variables)
            .toString()

        val requestBuilder = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")

        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        val request = requestBuilder.build()

        // AniList'in fiili limiti 30 istek/dk; 429 aldığımızda Retry-After kadar (yoksa 60 sn)
        // bekleyip aynı isteği en fazla 3 kez tekrarlıyoruz. Böylece toplu eşitlemede tek bir
        // hız sınırı yanıtı kaydı "hata" olarak işaretlemiyor.
        var attempt = 0
        while (true) {
            attempt++
            try {
                com.kitsugi.animelist.core.network.KitsugiHttpClient.aniListClient.newCall(request).execute().use { response ->
                    if (response.code == 429) {
                        val retrySec = response.header("Retry-After")?.toLongOrNull()?.coerceIn(1L, 120L) ?: 60L
                        kotlinx.coroutines.runBlocking {
                            PlatformRateLimiter.notifyRateLimited("anilist", retrySec)
                        }
                        if (attempt >= MAX_ANILIST_ATTEMPTS) {
                            throw IllegalStateException(
                                context?.getString(R.string.account_sync_anilist_rate_limit, attempt, retrySec)
                                    ?: "AniList API hatası: 429 hız sınırı ($attempt deneme, Retry-After=${retrySec}s)"
                            )
                        }
                        return@use
                    }
                    if (!response.isSuccessful) {
                        val errorText = response.body?.string().orEmpty().take(300)
                        com.kitsugi.animelist.data.remote.KitsugiApiBase
                            .extractAniListServiceDownMessage(errorText)
                            ?.let { downMsg ->
                                val down = com.kitsugi.animelist.data.remote.KitsugiApiBase
                                    .recordAniListServiceDown(downMsg, response.code)
                                throw IllegalStateException(down.message, down)
                            }
                        throw IllegalStateException(
                            context?.getString(R.string.account_sync_api_error, "AniList", response.code, errorText)
                                ?: "AniList API hatası: ${response.code} $errorText"
                        )
                    }

                    val responseText = response.body?.string() ?: ""
                    com.kitsugi.animelist.data.remote.KitsugiApiBase
                        .extractAniListServiceDownMessage(responseText)
                        ?.let { downMsg ->
                            val down = com.kitsugi.animelist.data.remote.KitsugiApiBase
                                .recordAniListServiceDown(downMsg, 503)
                            throw IllegalStateException(down.message, down)
                        }
                    val errors = JSONObject(responseText).optJSONArray("errors")
                    if (errors != null && errors.length() > 0) {
                        throw IllegalStateException(errors.toString())
                    }

                    return responseText
                }
            } catch (e: Exception) {
                if (e is IllegalStateException) throw e
                val reason = e.message ?: context?.getString(R.string.account_sync_unknown_error) ?: "Bilinmeyen hata"
                throw IllegalStateException(
                    context?.getString(R.string.account_sync_connection_error, "AniList", reason)
                        ?: "AniList bağlantı hatası: $reason",
                    e
                )
            }
        }
    }

    private const val MAX_ANILIST_ATTEMPTS = 3

    private fun WatchStatus.toAniListStatus(): String {
        return when (this) {
            WatchStatus.Watching -> "CURRENT"
            WatchStatus.Completed -> "COMPLETED"
            WatchStatus.Planned -> "PLANNING"
            WatchStatus.Dropped -> "DROPPED"
            WatchStatus.Paused -> "PAUSED"
            WatchStatus.Repeating -> "REPEATING"
        }
    }

    private fun String?.toAniListFuzzyDate(): JSONObject? {
        // Boş tarih = değişken hiç gönderilmez. Eskiden {0,0,0} gönderiliyordu; AniList bunu
        // "tarihi temizle" olarak uygular — yerel satırda tarih yokken yapılan her eşitleme
        // (ör. Bangumi satırından yayılım) AniList'teki başlangıç/bitiş tarihlerini sessizce
        // siliyordu. MAL/Kitsu hatlarıyla aynı kural: boş alan uzaktaki değeri ezmez.
        if (this.isNullOrBlank()) return null

        val parts = split("-")
        if (parts.size != 3) return null

        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null

        return JSONObject()
            .put("year", year)
            .put("month", month)
            .put("day", day)
    }

}
