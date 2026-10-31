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
        knownAniListMediaId: Int? = null,
        /**
         * Çapraz kimlik katmanının çözdüğü **gerçek MAL kimliği**.
         *
         * Kitsu / Shikimori / Bangumi kaynaklı satırlarda `MediaIdentity.realMalId(entry)` NULL
         * döner (kaynak MAL değil), dolayısıyla kimlik zinciri `MAL idMal → ARM` adımlarını
         * atlayıp `null` ile çıkıyordu: bu satırlar AniList'e **hiç** yazılmıyordu. Çözülmüş
         * MAL kimliği buradan verilir; AniList yanıtındaki `idMal` alanı bunu yine de
         * teyit etmek zorundadır.
         */
        knownMalId: Int? = null,
        /**
         * Yerel satırda DEĞİŞEN alanlar. Yeni kayıt oluşturulurken ([existingEntryId] yok)
         * her zaman tam yazım yapılır; mevcut kayıtta yalnızca değişen alanlar gönderilir.
         *
         * Önemli: AniList `scoreRaw: null` (açıkça null) gönderimini **reddeder**
         * ("The score raw must be an integer") — alan hiç gönderilmemelidir. Ayrıca
         * gönderilmeyen her alan AniList'te olduğu gibi kalır.
         */
        fields: Set<SyncField> = SyncField.ALL,
        /**
         * Tam yazım mı? Süzme sonrası kümeden ANLAŞILAMAZ: `fieldsFor(provider)` her hedefe
         * kendi alt kümesini verir. Bu bayrak `true` iken boş puan/not/tarih GÖNDERİLMEZ
         * (uzaktaki değer korunur); `false` iken yalnız diff'te görünen alanlar yazılır.
         */
        fullWrite: Boolean = fields == SyncField.ALL
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
                    CrossSyncIdentityGuard.aliasesOf(entry), context,
                    knownMalId = knownMalId
                )
            }
            verifiedKnownId ?: resolveAniListMediaId(
                token = token,
                entry = entry,
                context = context,
                knownMalId = knownMalId
            )
        } else {
            val verifiedMediaId = resolveAniListMediaIdForEntry(
                token = token,
                entryId = existingEntryId,
                entry = entry,
                context = context,
                knownAniListMediaId = knownAniListMediaId,
                knownMalId = knownMalId
            ) ?: error(context.getString(R.string.account_sync_anilist_entry_identity_mismatch, entry.title))
            // Existing list-entry IDs are only mutation handles. Verify the attached media before
            // updating status, progress, score, or other fields.
            verifiedMediaId
        }

        // Null yalnızca "AniList'te hangi medya olduğu güvenle çözülemedi" anlamına gelir
        // (atlanacak kayıt). Mutasyonun başarısız olması aşağıda istisna ile bildirilir.
        if (existingEntryId == null && mediaId == null) return null

        // Yeni kayıt: hedefte korunacak veri yok → tam yazım. Mevcut kayıt: yalnız değişen alanlar.
        val writeAll = existingEntryId == null || fullWrite

        /**
         * Gönderilecek HAM puan (`scoreRaw`, 0..100) — `null` ise alan sorguya HİÇ girmez.
         *
         * GraphQL'de değişken hem dokümanda tanımlı hem de gönderilmiş olmalıdır; bu yüzden
         * "puan temizleme" (0) kararı, mutasyon metni kurulmadan ÖNCE verilir. `scoreRaw: null`
         * AniList tarafından reddedildiği için (`"The score raw must be an integer"`) hiçbir
         * durumda `null` gönderilmez.
         */
        val scoreRaw: Int? = when {
            !(writeAll || SyncField.SCORE in fields) -> null
            entry.score != null && entry.score > 0 -> SyncScores.tenPoint(entry.score)?.times(10)
            // Kullanıcı puanı SİLDİ (diff bunu söylüyor) → 0 gönder, hedefte temizlensin.
            !writeAll -> 0
            else -> null
        }
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
        if (writeAll || SyncField.STATUS in fields) variables.put("status", entry.status.toAniListStatus())
        if (writeAll || SyncField.PROGRESS in fields) variables.put("progress", entry.progress)
        if (writeAll || SyncField.VOLUME_PROGRESS in fields) variables.put("progressVolumes", entry.volumeProgress)
        if (writeAll || SyncField.PRIVATE in fields) variables.put("private", entry.isPrivate)
        // Not: boş notu yalnızca kullanıcı SİLDİYSE gönder (diff); tam yazımda boş not
        // göndermek AniList'teki notu silerdi.
        if (writeAll && !entry.notes.isNullOrBlank()) {
            variables.put("notes", entry.notes)
        } else if (!writeAll && SyncField.NOTES in fields) {
            variables.put("notes", entry.notes ?: "")
        }
        if (writeAll || SyncField.REWATCH_COUNT in fields) variables.put("repeat", entry.repeatCount)
        if (writeAll || SyncField.HIDDEN_FROM_STATUS_LISTS in fields) {
            variables.put("hiddenFromStatusLists", entry.isHiddenFromStatusLists)
        }

        // Değişken yalnız dokümanda tanımlıysa gönderilir (yukarıdaki `scoreRaw` kararı).
        scoreRaw?.let { variables.put("scoreRaw", it) }

        // FuzzyDate alanları: gönderilmezse AniList'teki tarih korunur. Temizlemek için
        // {year: null, month: null, day: null} gerekir.
        //
        // DİKKAT (E63): "ayrıştırılamadı" ile "boşaltıldı" AYNI ŞEY DEĞİLDİR. Eski kod
        // `toAniListFuzzyDate()` null döndüğünde (ör. yalnız yıl içeren "2016", ya da içe
        // aktarmadan sızmış "2016-05") temizleme gönderiyordu → kullanıcı tarihe dokunmadığı
        // hâlde AniList'teki tarih SİLİNİYORDU. Artık temizleme yalnızca alan GERÇEKTEN
        // boşaltıldığında gönderilir; parsiyel tarih hiç gönderilmez (uzak değer korunur).
        if (SyncField.START_DATE in fields || writeAll) {
            val startedAt = entry.startDate.toAniListFuzzyDate()
            when (SyncDates.action(entry.startDate, startedAt, writeAll)) {
                SyncDates.Action.WRITE -> variables.put("startedAt", startedAt)
                SyncDates.Action.CLEAR -> variables.put("startedAt", clearedFuzzyDate())
                SyncDates.Action.SKIP -> Unit
            }
        }
        if (SyncField.END_DATE in fields || writeAll) {
            val completedAt = entry.endDate.toAniListFuzzyDate()
            when (SyncDates.action(entry.endDate, completedAt, writeAll)) {
                SyncDates.Action.WRITE -> variables.put("completedAt", completedAt)
                SyncDates.Action.CLEAR -> variables.put("completedAt", clearedFuzzyDate())
                SyncDates.Action.SKIP -> Unit
            }
        }

        // Gelişmiş puanlar da yalnız puan değiştiğinde (ya da ilk yazımda) gönderilir.
        if (advancedScores != null && (writeAll || SyncField.SCORE in fields)) {
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
        knownAniListMediaId: Int? = null,
        knownMalId: Int? = null
    ): Boolean? {
        val aniListMediaId: Int = knownAniListMediaId?.takeIf { it > 0 }
            ?: resolveAniListMediaId(token = token, entry = entry, context = context, knownMalId = knownMalId)
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
                ?: error("AniList favori doğrulaması medya döndürmedi (mediaId=$aniListMediaId)")
        }.getOrThrow()
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
        knownAniListMediaId: Int? = null,
        knownMalId: Int? = null
    ): Boolean {
        val aniListMediaId: Int? = knownAniListMediaId?.takeIf { it > 0 }
            ?: resolveAniListMediaId(token = token, entry = entry, context = context, knownMalId = knownMalId)

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
        }.getOrThrow()
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
        knownAniListMediaId: Int? = null,
        knownMalId: Int? = null
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
            ?: resolveAniListMediaId(token, entry, context, knownMalId = knownMalId)
            ?: return@runSyncCatching null
        mediaId.takeIf { it == expectedMediaId }
        // HTTP/GraphQL/ağ hatası kimlik uyuşmazlığı değildir; çağırana gerçek
        // istisna olarak taşınır. Yalnız geçerli yanıttaki null/farklı ID null olur.
    }.getOrThrow()

    fun resolveAniListMediaId(
        token: String,
        entry: MediaEntry,
        context: Context,
        knownMalId: Int? = null
    ): Int? {
        val mediaTypeGql = when (entry.type) {
            MediaType.Anime, MediaType.Movie, MediaType.TvShow -> "ANIME"
            MediaType.Manga -> "MANGA"
        }
        // Çapraz katmanın çözdüğü gerçek MAL kimliği, kaynak-özel çıkarımdan önce gelir:
        // Kitsu/Shikimori/Bangumi satırlarında `MediaIdentity.realMalId` kaynak MAL olmadığı
        // için null döner ve zincir burada kırılıyordu.
        val realMalId = CrossProviderIds.realMalIdOf(knownMalId) ?: MediaIdentity.realMalId(entry)
        val nativeAniListId = MediaIdentity.aniListId(entry)
        val verifiedTitleAliases = if (nativeAniListId != null) {
            CrossSyncIdentityGuard.aliasesOf(entry)
        } else {
            if (realMalId == null) return null
            if (CrossProviderIds.realMalIdOf(knownMalId) != null) {
                // Kimlik çapraz katmandan geldi ve orada MAL kataloğuna karşı doğrulandı;
                // aşağıdaki `idMal` sorgusu ve [verifyAniListMediaIdentity] yine teyit eder.
                CrossSyncIdentityGuard.aliasesOf(entry)
            } else {
                val malVerdict = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                    CrossSyncIdentityGuard.verifyMalId(
                        malId = realMalId,
                        mediaType = entry.type,
                        localTitles = CrossSyncIdentityGuard.aliasesOf(entry),
                        localYear = entry.year
                    )
                }
                when (malVerdict) {
                    is CrossSyncIdentityGuard.Verdict.Verified -> malVerdict.matchedLocalTitles
                    // Çelişki yok, sadece kanıt eksik (yıl yok / katalog okunamadı): yazım
                    // hedefin KENDİ `idMal` teyidine bırakılır. Eskiden burada `return null`
                    // vardı ve yıl alanı boş olan her satır AniList'e hiç gitmiyordu.
                    else -> if (CrossSyncIdentityGuard.blocksCrossWrite(malVerdict)) return null
                    else CrossSyncIdentityGuard.aliasesOf(entry)
                }
            }
        }
        val rawKitsuId = MediaIdentity.kitsuId(entry)

        // A stored AniList media ID is source-native but still checked against type/title/year before add/favorite writes.
        if (nativeAniListId != null) {
            return nativeAniListId.takeIf {
                verifyAniListMediaIdentity(
                    token, it, mediaTypeGql, entry, verifiedTitleAliases, context,
                    knownMalId = realMalId
                )
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
            }.getOrThrow()
            if (resolved != null) {
                return resolved.takeIf {
                    verifyAniListMediaIdentity(
                    token, it, mediaTypeGql, entry, verifiedTitleAliases, context,
                    knownMalId = realMalId
                )
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
            verifyAniListMediaIdentity(
                token, armId, mediaTypeGql, entry, verifiedTitleAliases, context,
                knownMalId = realMalId
            )
        ) {
            return armId
        }

        // A search rank is not an identity mapping. Never mutate the first fuzzy result.
        return null
    }

    /**
     * Kimlik doğrulamasının sonucu (v2.4.341).
     *
     * Eskiden HER düzenlemede, kimliği zaten doğrulanmış bir kayıt için bile yeniden
     * `Media(id) { ... }` sorgusu atılıyordu. Bir kullanıcı "+1 bölüm"e üst üste bastığında
     * bu tek başına kotanın üçte birini yiyordu. Aynı (mediaId, tür, MAL kimliği, yıl,
     * başlık kümesi) için sonuç 6 saat (olumsuzsa 10 dk) önbellekte tutulur.
     */
    private data class VerificationCacheEntry(val verified: Boolean, val expiresAt: Long)

    private const val VERIFICATION_TTL_MS = 6L * 60L * 60L * 1_000L
    private const val VERIFICATION_NEGATIVE_TTL_MS = 10L * 60L * 1_000L
    private const val FAVORITE_PREFS = "kitsugi_anilist_favorite_sync"

    private val verificationCache =
        object : LinkedHashMap<String, VerificationCacheEntry>(32, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<String, VerificationCacheEntry>
            ): Boolean = size > 128
        }

    private fun verificationCacheKey(
        mediaId: Int,
        expectedType: String,
        entry: MediaEntry,
        expectedTitles: Collection<String>
    ): String = buildString {
        append(mediaId).append('|')
        append(expectedType).append('|')
        append(MediaIdentity.realMalId(entry)).append('|')
        append(MediaIdentity.aniListId(entry)).append('|')
        append(entry.year).append('|')
        append(expectedTitles.map { it.trim().lowercase() }.sorted().joinToString("\u0001"))
    }

    private fun verifyAniListMediaIdentity(
        token: String,
        mediaId: Int,
        expectedType: String,
        entry: MediaEntry,
        expectedTitles: Collection<String>,
<<<<<<< HEAD
        context: Context,
        knownMalId: Int? = null
=======
        context: Context
    ): Boolean {
        val cacheKey = verificationCacheKey(mediaId, expectedType, entry, expectedTitles)
        synchronized(verificationCache) {
            val cached = verificationCache[cacheKey]
            if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
                return cached.verified
            }
        }

        val verified = runSyncCatching {
            verifyAniListMediaIdentityUncached(
                token = token,
                mediaId = mediaId,
                expectedType = expectedType,
                entry = entry,
                expectedTitles = expectedTitles,
                context = context
            )
        }.getOrDefault(false)

        val ttl = if (verified) VERIFICATION_TTL_MS else VERIFICATION_NEGATIVE_TTL_MS
        synchronized(verificationCache) {
            verificationCache[cacheKey] = VerificationCacheEntry(
                verified = verified,
                expiresAt = System.currentTimeMillis() + ttl
            )
        }
        return verified
    }

    private fun verifyAniListMediaIdentityUncached(
        token: String,
        mediaId: Int,
        expectedType: String,
        entry: MediaEntry,
        expectedTitles: Collection<String>,
        context: Context
>>>>>>> arena/df3b9268-kitsugi-beta
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
        val expectedMalId = CrossProviderIds.realMalIdOf(knownMalId) ?: MediaIdentity.realMalId(entry)
        val remoteMalId = media.optInt("idMal", 0).takeIf { it in 1..99_999_999 }
        // Çelişki varsa hiçbir gerekçe yazıma izin vermez.
        if (expectedMalId != null && remoteMalId != expectedMalId) return@runSyncCatching false
        val expectedAniListId = MediaIdentity.aniListId(entry)
        if (expectedAniListId != null && expectedAniListId != mediaId) return@runSyncCatching false
        // ── OTORİTER ÇAPRAZ REFERANS ────────────────────────────────────────────────
        // AniList'in KENDİ `Media.idMal` alanı, MAL ↔ AniList eşlemesinin resmî kaynağıdır.
        // İstenen MAL kimliği ile birebir eşleşiyorsa (ve tür doğruysa) bu, "yanlış yapıma
        // yazma" riski TAŞIMAZ; başlık/yl ölçütlerini ayrıca aramak yalnızca yanlış
        // negatif üretiyordu (İngilizce başlık farkı, yayın yılı farkı, yıl alanı boş…).
        if (expectedMalId != null && remoteMalId == expectedMalId) return@runSyncCatching true
        val title = media.optJSONObject("title")
        val remoteTitles = listOf("romaji", "english", "native")
            .mapNotNull { title?.optString(it)?.takeIf(String::isNotBlank) }
        val remoteYear = media.optJSONObject("startDate")?.optInt("year", 0)?.takeIf { it > 0 }
        CrossSyncIdentityGuard.titlesAreIdentityEquivalent(expectedTitles, remoteTitles) &&
            (CrossSyncIdentityGuard.yearsCompatible(entry.year, remoteYear) || entry.year == null || remoteYear == null)
    }.getOrThrow()

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
        val mangaEnabled: Boolean,
        /** Özel liste adları (v2.4.341: aynı Viewer sorgusunda gelir, ayrı istek gerekmez). */
        val animeCustomLists: List<String> = emptyList(),
        val mangaCustomLists: List<String> = emptyList()
    )

    private data class TimedValue<T>(val value: T, val expiresAt: Long)

    private const val VIEWER_OPTIONS_TTL_MS = 30L * 60L * 1_000L
    private const val MEDIA_LIST_METADATA_TTL_MS = 5L * 60L * 1_000L

    private val viewerOptionsCache =
        java.util.concurrent.ConcurrentHashMap<String, TimedValue<ViewerOptions>>()
    private val mediaListMetadataCache =
        java.util.concurrent.ConcurrentHashMap<String, TimedValue<MediaListMetadata>>()

    /**
     * Görüntüleyici listesi seçenekleri (puanlama biçimi, gelişmiş puanlama kategorileri,
     * özel liste adları).
     *
     * v2.4.341: Düzenleyici her açıldığında bu bilgiler için **iki ayrı** GraphQL isteği
     * (biri `getViewerOptions`, biri `getViewerCustomLists`) atılıyordu; ikisi de aynı
     * `Viewer.mediaListOptions` nesnesini okuyordu. Artık tek sorgu + 30 dk önbellek.
     */
    fun getViewerOptions(token: String): ViewerOptions? {
        val key = token.hashCode().toString()
        viewerOptionsCache[key]?.let { cached ->
            if (cached.expiresAt > System.currentTimeMillis()) return cached.value
        }
        val fresh = getViewerOptionsUncached(token) ?: return null
        viewerOptionsCache[key] = TimedValue(
            value = fresh,
            expiresAt = System.currentTimeMillis() + VIEWER_OPTIONS_TTL_MS
        )
        return fresh
    }

    private fun getViewerOptionsUncached(token: String): ViewerOptions? {
        val query = """
            query {
                Viewer {
                    mediaListOptions {
                        scoreFormat
                        animeList {
                            advancedScoring
                            advancedScoringEnabled
                            customLists
                        }
                        mangaList {
                            advancedScoring
                            advancedScoringEnabled
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
                mangaEnabled = mangaEnabled,
                animeCustomLists = animeList?.optJSONArray("customLists").toStringList(),
                mangaCustomLists = mangaList?.optJSONArray("customLists").toStringList()
            )
        } catch (e: java.lang.Exception) {
            null
        }
    }

    private fun org.json.JSONArray?.toStringList(): List<String> {
        val array = this ?: return emptyList()
        val result = ArrayList<String>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optString(i)
            if (item.isNotBlank()) result.add(item)
        }
        return result
    }

    data class MediaListMetadata(
        val customLists: Map<String, Boolean>?,
        val advancedScores: Map<String, Double>?
    )

    /**
     * Bir liste kaydının özel listeleri + gelişmiş puanları (düzenleyici açılışı).
     * v2.4.341: 5 dk önbellek — düzenleyiciyi açıp kapatmak kotayı tüketmez.
     */
    fun getMediaListMetadata(
        token: String,
        entryId: Int,
        userId: Int
    ): MediaListMetadata? {
        val key = "${token.hashCode()}|$entryId|$userId"
        mediaListMetadataCache[key]?.let { cached ->
            if (cached.expiresAt > System.currentTimeMillis()) return cached.value
        }
        val fresh = getMediaListMetadataUncached(token, entryId, userId) ?: return null
        mediaListMetadataCache[key] = TimedValue(
            value = fresh,
            expiresAt = System.currentTimeMillis() + MEDIA_LIST_METADATA_TTL_MS
        )
        return fresh
    }

    private fun getMediaListMetadataUncached(
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

    /**
     * Özel liste adları. v2.4.341: ayrı istek atılmaz — [getViewerOptions] önbelleğinden gelir.
     */
    fun getViewerCustomLists(
        token: String,
        isManga: Boolean
    ): List<String>? {
        val options = getViewerOptions(token) ?: return null
        return if (isManga) options.mangaCustomLists else options.animeCustomLists
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
            val updated = JSONObject(response).optJSONObject("data")?.optJSONObject("UpdateUser") != null
            if (updated) invalidateViewerCaches()
            updated
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
            // Özel liste verisi değişti → görüntüleyici önbellekleri geçersiz.
            invalidateViewerCaches()
            true
        } catch (e: java.lang.Exception) {
            false
        }
    }

    /** Görüntüleyici/liste meta önbelleklerini temizler (yazma veya yeniden giriş sonrası). */
    fun invalidateViewerCaches() {
        viewerOptionsCache.clear()
        mediaListMetadataCache.clear()
        cachedAniListUserId = null
        cachedAniListToken = null
    }

    /**
     * Tek AniList GraphQL isteği.
     *
     * v2.4.341 — KÖK NEDEN DÜZELTMESİ: Bu yol eskiden **hiçbir hız sınırına uymuyordu**.
     * "Listem" ekranındaki ekleme/düzenleme/silme yazmaları (SaveMediaListEntry,
     * DeleteMediaListEntry, ToggleFavourite, kimlik doğrulama sorguları) tam olarak
     * buradan çıkıyordu; liste yenileme ve arka plan senkronuyla birleşince AniList'in
     * fiilî 30 istek/dk kotası dakikalar içinde tükeniyordu. Ayrıca 429 alındığında
     * `PlatformRateLimiter.notifyRateLimited` **çağıran thread'i 60–120 sn uyutuyordu**
     * (kilit altında) ve bu sırada diğer istekler kotayı tüketmeye devam ediyordu.
     *
     * Artık her istek [AniListGate] üzerinden gider: kayan pencere, patlama sınırı,
     * 429 soğuması ve öncelik (kullanıcı işlemi > arka plan) tek merkezde uygulanır.
     * Kota nedeniyle sıra gelmezse **thread kilitlenmez**; çağırana bekleme süresiyle
     * [AniListGate.QuotaExhaustedException] fırlar, [com.kitsugi.animelist.data.local.MediaEntryRepository]
     * işi bekleyen-senkron kuyruğuna alır ve soğuma bitince kendiliğinden yeniden dener.
     */
    private fun postAniList(
        token: String?,
        query: String,
        variables: JSONObject,
        context: Context? = null
    ): String {
        val payload = JSONObject()
            .put("query", query)
            .put("variables", variables)
            .toString()

        val requestBuilder = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", "KitsugiAnimeList/1.0")

        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        val request = requestBuilder.build()

        var attempt = 0
        while (true) {
            attempt++

            val outcome: AniListHttpOutcome = try {
                // Yazma işlemi: kota sırası 3 sn'den uzun sürerse thread'i bekletmek yerine
                // işi kuyruğa bırakırız (çağıran taraf QuotaExhaustedException'ı yakalar).
                AniListGate.withPermitBlocking(
                    priority = AniListGate.Priority.INTERACTIVE,
                    maxWaitMs = 3_000L
                ) {
                    com.kitsugi.animelist.core.network.KitsugiHttpClient.aniListClient
                        .newCall(request)
                        .execute()
                        .use { response ->
                            val bodyText = runCatching { response.body?.string().orEmpty() }
                                .getOrDefault("")
                            // Pencere/patlama/soğuma muhasebesi tek merkezde.
                            AniListGate.onResponse(response.code, response.headers, bodyText)
                            AniListHttpOutcome(
                                code = response.code,
                                body = bodyText,
                                isSuccessful = response.isSuccessful
                            )
                        }
                }
                }
            } catch (quota: AniListGate.QuotaExhaustedException) {
                // Kota doldu: kuyruk/UX katmanı bu işareti görüp işi erteler.
                throw IllegalStateException(quota.message, quota)
            } catch (error: Exception) {
                val reason = error.message
                    ?: context?.getString(R.string.account_sync_unknown_error)
                    ?: "Bilinmeyen hata"
                throw IllegalStateException(
                    context?.getString(R.string.account_sync_connection_error, "AniList", reason)
                        ?: "AniList bağlantı hatası: $reason",
                    error
                )
            }

            com.kitsugi.animelist.data.remote.KitsugiApiBase
                .extractAniListServiceDownMessage(outcome.body)
                ?.let { downMsg ->
                    val down = com.kitsugi.animelist.data.remote.KitsugiApiBase
                        .recordAniListServiceDown(downMsg, outcome.code)
                    throw IllegalStateException(down.message, down)
                }

            if (outcome.code == 429) {
                // AniList cezası tipik olarak 60 sn: bu thread'i uyutmak yerine bekleme
                // süresini çağırana bildiriyoruz. Kayıt bekleyen-senkron kuyruğuna girer ve
                // ceza bitince PendingSyncDrainer tarafından kendiliğinden gönderilir.
                val waitMs = AniListGate.cooldownRemainingMs()
                throw IllegalStateException(
                    context?.getString(
                        R.string.account_sync_anilist_rate_limit,
                        attempt,
                        (waitMs / 1_000L).coerceAtLeast(1L)
                    ) ?: AniListGate.quotaMessage(waitMs)
                )
            }

            if (!outcome.isSuccessful) {
                throw IllegalStateException(
                    context?.getString(R.string.account_sync_api_error, "AniList", outcome.code, outcome.body.take(300))
                        ?: "AniList API hatası: ${outcome.code} ${outcome.body.take(300)}"
                )
            }

            val errors = JSONObject(outcome.body).optJSONArray("errors")
            if (errors != null && errors.length() > 0) {
                throw IllegalStateException(errors.toString())
            }

            // Yazma sonrası okuma önbelleği bayatlamış olabilir.
            if (query.contains("mutation", ignoreCase = true)) {
                AniListGate.invalidateReadCache()
            }

            return outcome.body
        }
    }

    /** [postAniList] için ham HTTP sonucu (gövde bir kez okunur). */
    private data class AniListHttpOutcome(
        val code: Int,
        val body: String,
        val isSuccessful: Boolean
    )

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

    /** Tarihi temizlemek için: tüm bileşenler null (AniList "boş tarih" olarak yorumlar). */
    private fun clearedFuzzyDate(): JSONObject = JSONObject()
        .put("year", JSONObject.NULL)
        .put("month", JSONObject.NULL)
        .put("day", JSONObject.NULL)

    /**
     * Favori eşitlemesi gerekiyor mu?
     *
     * Eskiden her düzenlemede `Media.isFavourite` sorgusu + gerekirse `ToggleFavourite`
     * mutasyonu çalışıyordu: kullanıcı yalnızca bölüm sayısını değiştirse bile 2–3 ek AniList
     * isteği gidiyordu. Son eşitlenen favori değeri yerelde tutulur; değer değişmediyse
     * sorgu atlanır. Favoriye EKLEME her zaman yayılır (uzak taraf değişmiş olabilir).
     */
    fun shouldSyncFavorite(context: Context, entry: MediaEntry): Boolean {
        if (entry.isFavorite) return true
        if (entry.id <= 0) return true
        val prefs = context.getSharedPreferences(FAVORITE_PREFS, Context.MODE_PRIVATE)
        val key = "fav_${entry.id}_${entry.source}"
        if (!prefs.contains(key)) return true
        return prefs.getBoolean(key, false) != entry.isFavorite
    }

    /** Favori durumu uzak tarafla eşitlendi olarak işaretlenir. */
    fun markFavoriteSynced(context: Context, entry: MediaEntry) {
        if (entry.id <= 0) return
        runCatching {
            context.getSharedPreferences(FAVORITE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("fav_${entry.id}_${entry.source}", entry.isFavorite)
                .apply()
        }
    }

    /**
     * Liste uzaktan yenilendiğinde çağrılır: favori bilgisi AniList'ten geldiği için yerel
     * "son eşitlenen" işaretleri geçersizdir. Temizlenmezlerse sitenin dışında değiştirilen
     * bir favori, sonraki düzenlemede fark edilmeden atlanabilirdi.
     */
    fun resetFavoriteSyncMarkers(context: Context) {
        runCatching {
            context.getSharedPreferences(FAVORITE_PREFS, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply()
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
