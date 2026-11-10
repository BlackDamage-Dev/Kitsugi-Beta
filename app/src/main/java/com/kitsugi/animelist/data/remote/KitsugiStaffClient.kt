package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.KitsugiApplication
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.URL
import com.kitsugi.animelist.utils.*
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

class KitsugiStaffClient {

    private companion object {
        /**
         * Bangumi ekip adları için AniList köprüsü süre tavanı. Süre dolarsa ya da istek
         * başarısız olursa liste orijinal (özgün) adlarla döner.
         */
        const val BANGUMI_NAME_BRIDGE_TIMEOUT_MS = 8_000L
        const val BANGUMI_INFOBOX_NAME_BRIDGE_TIMEOUT_MS = 7_500L
    }

    suspend fun fetchStaff(
        source: String,
        externalId: Int?,
        mediaType: MediaType,
        tmdbId: Int? = null,
        realMalId: Int? = null
    ): List<KitsugiStaff> {
        return withContext(Dispatchers.IO) {
            if (externalId == null || externalId <= 0) {
                return@withContext emptyList()
            }

            when (MalJikanMediaSupport.canonicalSource(source)) {
                "bangumi", "bgm" -> {
                    // 1) Bangumi'nin kendi ekip listesi (p1 /staffs/persons veya v0 /subjects/{id}/persons).
                    val native = runCatching { KitsugiBangumiDetailClient.fetchStaff(externalId, mediaType) }
                        .getOrNull().orEmpty()
                        .ifEmpty {
                            runCatching { KitsugiBangumiCreditsClient.fetchSubjectStaff(externalId) }.getOrNull().orEmpty()
                        }
                    if (native.isNotEmpty()) {
                        // Bangumi liste uçları infobox döndürmez → ekip adları kanji kalır.
                        // AniList köprüsüyle (özgün ada birebir eşleme) romaji/İngilizce
                        // adlar doldurulur; başlık dili ROMAJI/ENGLISH olduğunda gösterilir.
                        val malIdForNames = KitsugiBangumiDetailClient.sanitizeMalId(realMalId)
                            ?: runCatching { KitsugiBangumiDetailClient.resolveCrossIds(externalId, mediaType) }
                                .getOrNull()?.malId
                        return@withContext enrichBangumiStaffNames(native, mediaType, malIdForNames)
                    }

                    // 2) Yedek: Çözülen MAL kimliğiyle MAL/Jikan ekibi (stableId MAL ID değildir).
                    val cross = runCatching {
                        KitsugiBangumiDetailClient.resolveCrossIds(externalId, mediaType)
                    }.getOrNull()
                    val malId = cross?.malId ?: KitsugiBangumiDetailClient.sanitizeMalId(realMalId)
                    if (malId != null && malId > 0 && (mediaType == MediaType.Anime || mediaType == MediaType.Manga)) {
                        val jikanList = fetchStaff("jikan", malId, mediaType, null, null)
                        if (jikanList.isNotEmpty()) return@withContext jikanList
                    }
                    val resolvedTmdb = tmdbId ?: cross?.tmdbId
                    if (resolvedTmdb != null && resolvedTmdb > 0 && mediaType != MediaType.Manga) {
                        val (_, tmdbStaff) = TmdbApiClient().fetchCredits(resolvedTmdb, mediaType == MediaType.Movie)
                        if (tmdbStaff.isNotEmpty()) return@withContext tmdbStaff
                    }
                    emptyList()
                }
                "shikimori" -> {
                    // 1) Shikimori'nin kendi `/roles` ucu personel kayıtlarını (yönetmen,
                    //    senarist …) ve seiyuu kayıtlarını içerir.
                    val shikiStaff = KitsugiShikimoriClient.fetchStaff(mediaType, externalId)
                    if (shikiStaff.isNotEmpty()) return@withContext shikiStaff

                    // 2) Shikimori'de personel kaydı YOKSA (küçük/az bakımlı yapımlarda sık
                    //    görülür) Ekip sekmesi boş kalmasın: gerçek MAL ID'si çözülüp
                    //    MAL/Jikan personel listesi gösterilir. Not: Shikimori ID'si MAL ID
                    //    DEĞİLDİR; bu yüzden ARM/Shikimori API eşlemesi kullanılır.
                    val malId = DetailCache.getMediaDetail("shikimori", externalId)?.realMalId?.takeIf { it > 0 }
                        ?: KitsugiIdResolver.resolveMalIdFromShikimori(externalId)
                    if (malId != null && malId > 0) {
                        val jikanList = fetchStaff(
                            source = "jikan",
                            externalId = malId,
                            mediaType = if (mediaType == MediaType.Manga) MediaType.Manga else MediaType.Anime
                        )
                        if (jikanList.isNotEmpty()) return@withContext jikanList
                    }
                    shikiStaff
                }
                "simkl" -> {
                    val simklCross = KitsugiSimklDetailClient.resolveSimklCrossIds(
                        simklId = externalId,
                        mediaType = mediaType,
                        hintTmdbId = tmdbId,
                        hintMalId = realMalId
                    )
                    val malId = realMalId?.takeIf { it > 0 && it != externalId }
                        ?: simklCross.malId
                        ?: DetailCache.getMediaDetail("simkl", externalId)?.realMalId
                    val resolvedTmdb = tmdbId?.takeIf { it > 0 }
                        ?: simklCross.tmdbId
                        ?: DetailCache.getMediaDetail("simkl", externalId)?.tmdbId
                    val aniListId = simklCross.aniListId ?: run {
                        if (malId != null || resolvedTmdb != null) {
                            runCatching {
                                KitsugiIdResolver.resolveIds(
                                    malId = malId,
                                    aniListId = null,
                                    tmdbId = resolvedTmdb,
                                    mediaType = mediaType
                                ).aniListId
                            }.getOrNull()
                        } else null
                    }
                    val animeMediaType = if (mediaType == MediaType.Manga) MediaType.Manga else MediaType.Anime

                    coroutineScope {
                        val aniDeferred = async {
                            val targetAniId = when {
                                aniListId != null && aniListId > 0 -> 100_000_000 + aniListId
                                malId != null && malId > 0 -> malId
                                else -> null
                            }
                            if (targetAniId != null) {
                                runCatching { fetchStaff("anilist", targetAniId, animeMediaType, null, malId) }
                                    .getOrNull().orEmpty()
                            } else emptyList()
                        }
                        val malDeferred = async {
                            if (malId != null && malId > 0) {
                                runCatching { fetchStaff("jikan", malId, animeMediaType, null, malId) }
                                    .getOrNull().orEmpty()
                            } else emptyList()
                        }
                        val tmdbDeferred = async {
                            if (resolvedTmdb != null && resolvedTmdb > 0 && mediaType != MediaType.Manga) {
                                val rawTmdbStaff = runCatching {
                                    TmdbApiClient().fetchCredits(resolvedTmdb, simklCross.isMovie).second
                                }.getOrNull().orEmpty()
                                if (rawTmdbStaff.isNotEmpty() && malId != null && malId > 0) {
                                    enrichBangumiStaffNames(rawTmdbStaff, animeMediaType, malId)
                                } else {
                                    rawTmdbStaff
                                }
                            } else emptyList()
                        }

                        val aniListStaff = aniDeferred.await()
                        val malStaff = malDeferred.await()
                        val tmdbStaff = tmdbDeferred.await()

                        mergeAndEnrichStaffLists(
                            primary = if (mediaType == MediaType.Anime) aniListStaff else tmdbStaff,
                            secondary = if (mediaType == MediaType.Anime) malStaff else aniListStaff,
                            tertiary = if (mediaType == MediaType.Anime) tmdbStaff else malStaff
                        )
                    }
                }

                "tmdb" -> {
                    val effectiveTmdbId = tmdbId ?: externalId
                    if (effectiveTmdbId > 0) {
                        val isMovie = mediaType == MediaType.Movie
                        val detail = DetailCache.getMediaDetail("tmdb", effectiveTmdbId)
                        val resolvedIds = if (mediaType == MediaType.Anime || realMalId != null || detail?.realMalId != null) {
                            runCatching {
                                KitsugiIdResolver.resolveIds(
                                    malId = realMalId ?: detail?.realMalId,
                                    aniListId = null,
                                    tmdbId = effectiveTmdbId,
                                    mediaType = mediaType
                                )
                            }.getOrNull()
                        } else null
                        val malId = realMalId ?: detail?.realMalId ?: resolvedIds?.malId
                        val aniListId = resolvedIds?.aniListId

                        coroutineScope {
                            val tmdbDeferred = async {
                                val (_, rawTmdb) = TmdbApiClient().fetchCredits(effectiveTmdbId, isMovie)
                                if (rawTmdb.isNotEmpty() && malId != null && malId > 0) {
                                    enrichBangumiStaffNames(rawTmdb, MediaType.Anime, malId)
                                } else rawTmdb
                            }
                            val aniDeferred = async {
                                val targetAniId = when {
                                    aniListId != null && aniListId > 0 -> 100_000_000 + aniListId
                                    malId != null && malId > 0 -> malId
                                    else -> null
                                }
                                if (targetAniId != null) {
                                    runCatching { fetchStaff("anilist", targetAniId, MediaType.Anime, null, malId) }
                                        .getOrNull().orEmpty()
                                } else emptyList()
                            }
                            val malDeferred = async {
                                if (malId != null && malId > 0) {
                                    runCatching { fetchStaff("jikan", malId, MediaType.Anime, null, malId) }
                                        .getOrNull().orEmpty()
                                } else emptyList()
                            }
                            val tmdbStaff = tmdbDeferred.await()
                            val aniStaff = aniDeferred.await()
                            val malStaff = malDeferred.await()
                            if (aniStaff.isEmpty() && malStaff.isEmpty()) {
                                tmdbStaff
                            } else {
                                mergeAndEnrichStaffLists(
                                    primary = if (mediaType == MediaType.Anime) aniStaff else tmdbStaff,
                                    secondary = if (mediaType == MediaType.Anime) malStaff else aniStaff,
                                    tertiary = if (mediaType == MediaType.Anime) tmdbStaff else malStaff
                                )
                            }
                        }
                    } else emptyList()
                }
                "jikan", "mal" -> {
                    val jikanId = MalJikanMediaSupport.resolveMalId(source, externalId, realMalId)
                        ?: return@withContext emptyList()
                    val endpoint = MalJikanMediaSupport.jikanEndpoint(mediaType)
                    val url = URL("https://api.jikan.moe/v4/$endpoint/$jikanId/staff")
                    val jikanList = runCatching {
                        KitsugiApiBase.runWithRateLimit {
                            // 429/5xx dayanıklı — rate-limit'te ekip listesi eksik dönmüş olmasın
                            val response = KitsugiApiBase.executeGetRequestResilient(url) ?: return@runWithRateLimit emptyList()
                            val root = JSONObject(response)
                            val data = root.optJSONArray("data") ?: return@runWithRateLimit emptyList()
                            val list = mutableListOf<KitsugiStaff>()
                            for (i in 0 until data.length()) {
                                val item = data.optJSONObject(i) ?: continue
                                val personObj = item.optJSONObject("person") ?: continue
                                val id = personObj.optInt("mal_id")
                                val name = (personObj.optNullableString("name") ?: "Bilinmeyen").toFriendlyName()
                                val positions = item.optJSONArray("positions")
                                val role = if (positions != null && positions.length() > 0) {
                                    val posList = mutableListOf<String>()
                                    for (j in 0 until positions.length()) {
                                        val pos = positions.optString(j)
                                        if (pos.isNotBlank() && pos != "null") posList.add(pos.toTurkishStaffRole())
                                    }
                                    posList.joinToString(", ")
                                } else "Ekip Üyesi"
                                val imageUrl = personObj.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url")
                                list.add(KitsugiStaff(id, name, role, imageUrl, source = "jikan"))
                            }
                            list
                        }
                    }.getOrElse { emptyList() }

                    if (jikanList.isNotEmpty()) {
                        jikanList
                    } else {
                        // Jikan boşsa aynı doğrulanmış MAL ID'siyle AniList'i dene; bu
                        // kimlik köprüsü ARM/fuzzy aramadan daha hızlı ve daha güvenlidir.
                        val aniListList = runCatching {
                            fetchStaff("anilist", jikanId, mediaType)
                        }.getOrNull().orEmpty()
                        if (aniListList.isNotEmpty()) {
                            aniListList
                        } else {
                            android.util.Log.w("KitsugiStaffClient", "Jikan/AniList ekip listesi boş; Shikimori fallback deneniyor")
                            // Shikimori kendi ID'sini bekler — MAL ID'si ARM ile çevrilir.
                            val shikiId = KitsugiIdResolver.resolveShikimoriIdFromMal(jikanId)
                            if (shikiId != null && shikiId > 0) {
                                KitsugiShikimoriClient.fetchStaff(mediaType, shikiId)
                            } else {
                                emptyList()
                            }
                        }
                    }
                }

                "kitsu" -> {
                    // externalId = kitsuStableId = kitsuNumericId + 300_000_000
                    val kitsuOffset = 300_000_000
                    val kitsuNumericId = if (externalId >= kitsuOffset) externalId - kitsuOffset else externalId
                    android.util.Log.d("KitsugiStaffClient", "Kitsu staff fetch: stableId=$externalId, kitsuNumericId=$kitsuNumericId")
                    if (kitsuNumericId <= 0) return@withContext emptyList()

                    // Kitsu has no native staff endpoint — resolve to AniList/Jikan via ARM
                    val resolved = runCatching {
                        KitsugiIdResolver.resolveIds(malId = realMalId, aniListId = null, kitsuId = kitsuNumericId)
                    }.getOrNull()

                    val aniListId = resolved?.aniListId
                    if (aniListId != null && aniListId > 0) {
                        // Encode aniListId with the 100M offset so fetchStaff("anilist") handles it correctly
                        val encoded = 100_000_000 + aniListId
                        val aniList = fetchStaff("anilist", encoded, mediaType, tmdbId, realMalId ?: resolved?.malId)
                        if (aniList.isNotEmpty()) return@withContext aniList.map { it.copy(source = "kitsu") }
                    }

                    val jikanId = realMalId?.takeIf { it > 0 } ?: resolved?.malId?.takeIf { it > 0 }
                    if (jikanId != null) {
                        val jikanList = fetchStaff("jikan", jikanId, mediaType, tmdbId, null)
                        if (jikanList.isNotEmpty()) return@withContext jikanList.map { it.copy(source = "kitsu") }
                    }

                    android.util.Log.w("KitsugiStaffClient", "Kitsu staff: AniList ve Jikan fallback boş döndü (kitsuId=$kitsuNumericId)")
                    emptyList()
                }

                "anilist" -> {
                    val aniListId = if (externalId >= 100_000_000) externalId - 100_000_000 else null
                    val idParam = if (aniListId != null) "\$id: Int" else "\$idMal: Int"
                    val idFilter = if (aniListId != null) "id: \$id" else "idMal: \$idMal"
                    val query = """
                        query (${'$'}type: MediaType, $idParam) {
                            Media($idFilter, type: ${'$'}type) {
                                staff(page: 1, perPage: 24) {
                                    edges {
                                        role
                                        node {
                                            id
                                            name { userPreferred full first middle last native alternative }
                                            image { medium }
                                        }
                                    }
                                }
                            }
                        }
                    """.trimIndent()
                    val variables = JSONObject().put("type", MalJikanMediaSupport.aniListMediaType(mediaType))
                    if (aniListId != null) variables.put("id", aniListId) else variables.put("idMal", externalId)

                    runCatching {
                        val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return@runCatching emptyList()
                        val root = JSONObject(response)
                        val edges = root.optJSONObject("data")?.optJSONObject("Media")?.optJSONObject("staff")?.optJSONArray("edges") ?: return@runCatching emptyList()
                        val list = mutableListOf<KitsugiStaff>()
                        for (i in 0 until edges.length()) {
                            val edge = edges.optJSONObject(i) ?: continue
                            val role = (edge.optNullableString("role") ?: "Ekip Üyesi").toTurkishStaffRole()
                            val node = edge.optJSONObject("node") ?: continue
                            val id = node.optInt("id")
                            val nameObj = node.optJSONObject("name")
                            val personName = nameObj.aniListPersonName()
                            val name = personName.preferred
                            val imageUrl = node.optJSONObject("image")?.optNullableString("medium")
                            list.add(KitsugiStaff(id, name, role, imageUrl, source = "anilist", romanizedName = personName.romanized, nativeName = personName.native))
                        }
                        list
                    }.getOrElse { emptyList() }
                }

                else -> emptyList()
            }
        }
    }

    /**
     * Bangumi ekip listesindeki CJK adlara AniList köprüsüyle (özgün ada birebir eşleme)
     * romaji/İngilizce karşılık ekler. Eşleşme bulunamazsa liste olduğu gibi döner.
     * Tek önbellekli AniList sorgusu kullanılır; süre tavanı/başarısızlıkta orijinal
     * liste döner (sekme beklemez).
     */
    private suspend fun enrichBangumiStaffNames(
        staff: List<KitsugiStaff>,
        mediaType: MediaType,
        malId: Int?
    ): List<KitsugiStaff> {
        if (staff.isEmpty() || staff.none { PreferenceHelpers.hasCjkCharacters(it.name) }) return staff

        // AniList only has a partial staff list for some titles; use its match as a first pass.
        val names = if (malId != null && malId > 0) {
            try {
                withTimeoutOrNull(BANGUMI_NAME_BRIDGE_TIMEOUT_MS) {
                    KitsugiAniListPersonBridge.fetchMediaNames(malId, mediaType)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        } else null
        val anilistEnriched = if (names == null) staff else staff.map { person ->
            if (!PreferenceHelpers.hasCjkCharacters(person.name)) return@map person
            val variant = KitsugiAniListPersonBridge.findByNative(names.staff, person.nativeName, person.name)
                ?: return@map person
            person.copy(
                romanizedName = person.romanizedName ?: variant.romaji,
                englishName = person.englishName ?: variant.english
            )
        }

        // Unlike AniList's media relationship, Bangumi's own person page exposes each infobox.
        return try {
            withTimeoutOrNull(BANGUMI_INFOBOX_NAME_BRIDGE_TIMEOUT_MS) {
                KitsugiBangumiDetailClient.enrichStaffNamesFromBangumi(anilistEnriched)
            } ?: anilistEnriched
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            anilistEnriched
        }
    }

    suspend fun fetchStaffDetail(
        source: String,
        staffId: Int,
        name: String? = null
    ): KitsugiStaffDetail? {
        return withContext(Dispatchers.IO) {
            val canonicalSource = MalJikanMediaSupport.canonicalSource(source)
            var personId = staffId
            if (personId <= 0) {
                // Kimliksiz kişi kayıtları (örn. MAL v2 authors node'u) ada göre Jikan
                // kişiler arasında çözümlenir; bulunamazsa null — verisiz/boş kişi
                // sayfası yerine hiç açılmasın (bkz. PLAN 2026-10-12).
                val targetName = name?.trim()?.takeIf { it.isNotBlank() } ?: return@withContext null
                personId = when (canonicalSource) {
                    "jikan", "mal" -> resolveJikanPersonIdByName(targetName)
                    else -> null
                } ?: return@withContext null
            }
            when (canonicalSource) {
                "bangumi", "bgm" -> {
                    val raw = KitsugiBangumiCreditsClient.fetchPersonDetail(personId, name)
                        ?: KitsugiBangumiDetailClient.fetchStaffDetail(personId)
                    // Karakter/yapım adları CJK kalmasın diye infobox + önbellekle zenginleştir.
                    // Ağ isteği sınırı ve zaman aşımı içeride korunur; başarısız olursa ham veri döner.
                    raw?.let { KitsugiBangumiDetailClient.enrichStaffDetailNames(it, cacheOnly = true) }
                }
                "shikimori" -> {
                    KitsugiShikimoriClient.fetchStaffDetail(personId)
                }
                "jikan", "mal" -> {
                    var detail: KitsugiStaffDetail? = null
                    var networkError: Throwable? = null

                    // 1. MAL / Jikan API
                    val url = URL("https://api.jikan.moe/v4/people/$personId/full")
                    val jikanRes = runCatching {
                        KitsugiApiBase.runWithRateLimit {
                            val response = KitsugiApiBase.executeGetRequestOrThrow(url)
                            val root = JSONObject(response)
                            val data = root.optJSONObject("data") ?: return@runWithRateLimit null

                            val staffName = (data.optNullableString("name") ?: "Bilinmeyen").toFriendlyName()
                            val nativeName = data.optNullableString("given_name") ?: data.optNullableString("family_name")

                            val alternativeNames = mutableListOf<String>()
                            val altArray = data.optJSONArray("alternate_names")
                            if (altArray != null) {
                                for (i in 0 until altArray.length()) {
                                    val alt = altArray.optString(i)
                                    if (alt.isNotBlank() && alt != "null") alternativeNames.add(alt)
                                }
                            }

                            val imageUrl = data.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url")
                            val biography = data.optNullableString("about")?.cleanApiText()?.takeIf { it.isNotBlank() }

                            val rawBirthday = data.optNullableString("birthday")
                            val (birthday, age) = com.kitsugi.animelist.utils.KitsugiDateUtils.formatBirthdayAndCalculateAge(rawBirthday, null)

                            val characterRoles = mutableListOf<KitsugiStaffCharacterRole>()
                            val voicesArray = data.optJSONArray("voices")
                            if (voicesArray != null) {
                                for (i in 0 until voicesArray.length()) {
                                    val item = voicesArray.optJSONObject(i) ?: continue
                                    val role = (item.optNullableString("role") ?: "Bilinmeyen").toTurkishCharacterRole()

                                    val animeObj = item.optJSONObject("anime")
                                    val animeId = animeObj?.optInt("mal_id") ?: 0
                                    val animeTitle = animeObj?.optNullableString("title") ?: "Bilinmeyen"
                                    val animeImg = animeObj?.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url")

                                    val charObj = item.optJSONObject("character") ?: continue
                                    val charId = charObj.optInt("mal_id")
                                    val charName = (charObj.optNullableString("name") ?: "Bilinmeyen").toFriendlyName()
                                    val charImg = charObj.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url")

                                    if (charId <= 0 && charName.isBlank()) continue

                                    characterRoles.add(KitsugiStaffCharacterRole(
                                        characterId = charId,
                                        characterName = charName.ifBlank { "Bilinmeyen" },
                                        characterImageUrl = charImg,
                                        characterSource = "jikan",
                                        mediaId = animeId,
                                        mediaTitle = animeTitle,
                                        mediaImageUrl = animeImg,
                                        mediaType = "anime".toTurkishMediaTypeString(),
                                        characterRole = role,
                                        mediaSource = "jikan"
                                    ))
                                }
                            }

                            val mediaWorks = mutableListOf<KitsugiStaffMediaWork>()
                            val animeWorks = data.optJSONArray("anime")
                            if (animeWorks != null) {
                                for (i in 0 until animeWorks.length()) {
                                    val item = animeWorks.optJSONObject(i) ?: continue
                                    val position = (item.optNullableString("position") ?: "Ekip Üyesi").toTurkishStaffRole()
                                    val animeObj = item.optJSONObject("anime")
                                    val animeId = animeObj?.optInt("mal_id") ?: 0
                                    val animeTitle = animeObj?.optNullableString("title") ?: "Bilinmeyen"
                                    val animeImg = animeObj?.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url")
                                    mediaWorks.add(KitsugiStaffMediaWork(
                                        mediaId = animeId,
                                        mediaTitle = animeTitle,
                                        mediaImageUrl = animeImg,
                                        mediaType = "anime".toTurkishMediaTypeString(),
                                        staffRole = position,
                                        source = "jikan"
                                    ))
                                }
                            }
                            // Manga yapımları: yazar/çizer kişilerin eser listesi (bkz. PLAN 2026-10-12)
                            val mangaWorks = data.optJSONArray("manga")
                            if (mangaWorks != null) {
                                for (i in 0 until mangaWorks.length()) {
                                    val item = mangaWorks.optJSONObject(i) ?: continue
                                    val position = (item.optNullableString("position") ?: "Ekip Üyesi").toTurkishStaffRole()
                                    val mangaObj = item.optJSONObject("manga")
                                    val mangaId = mangaObj?.optInt("mal_id") ?: 0
                                    val mangaTitle = mangaObj?.optNullableString("title") ?: "Bilinmeyen"
                                    val mangaImg = mangaObj?.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url")
                                    mediaWorks.add(KitsugiStaffMediaWork(
                                        mediaId = mangaId,
                                        mediaTitle = mangaTitle,
                                        mediaImageUrl = mangaImg,
                                        mediaType = "manga".toTurkishMediaTypeString(),
                                        staffRole = position,
                                        source = "jikan"
                                    ))
                                }
                            }

                            KitsugiStaffDetail(
                                id = personId,
                                name = staffName,
                                nativeName = nativeName,
                                alternativeNames = alternativeNames,
                                imageUrl = imageUrl,
                                biography = biography,
                                occupation = null,
                                birthday = birthday,
                                age = age,
                                gender = null,
                                homeTown = null,
                                characterRoles = characterRoles,
                                mediaWorks = mediaWorks
                            )
                        }
                    }
                    jikanRes.onSuccess {
                        detail = it
                    }.onFailure { err ->
                        if (err !is ResourceNotFoundException) {
                            networkError = err
                        }
                        android.util.Log.e("KitsugiStaffClient", "Jikan fetch failed: ${err.message}", err)
                    }

                    // 2. Shikimori Fallback / Merge (yalnızca Jikan başarısızsa veya biyografi/görsel eksikse)
                    if (detail == null || detail?.biography.isNullOrBlank() || detail?.imageUrl.isNullOrBlank()) {
                        runCatching {
                            KitsugiShikimoriClient.fetchStaffDetail(personId)
                        }.onSuccess { shikiDetail ->
                            if (shikiDetail != null) {
                                detail = if (detail == null) shikiDetail else detail!!.mergeWith(shikiDetail)
                            }
                        }.onFailure { err ->
                            android.util.Log.e("KitsugiStaffClient", "Shikimori staff detail fallback failed: ${err.message}", err)
                        }
                    }

                    // 3. AniList Fallback / Merge
                    val targetName = detail?.name ?: name
                    if (!targetName.isNullOrBlank()) {
                        runCatching {
                            fetchAniListStaffByName(targetName)
                        }.onSuccess { aniListDetail ->
                            if (aniListDetail != null) {
                                detail = if (detail == null) aniListDetail else detail!!.mergeWith(aniListDetail)
                            }
                        }.onFailure { err ->
                            android.util.Log.e("KitsugiStaffClient", "AniList staff detail fallback failed: ${err.message}", err)
                        }
                    }

                    if (detail == null && networkError != null) {
                        throw networkError!!
                    }
                    detail
                }

                "anilist" -> {
                    val query = """
                        query (${'$'}id: Int) {
                            Staff(id: ${'$'}id) {
                                id
                                isFavourite
                                name {
                                    userPreferred
                                    full first middle last
                                    native
                                    alternative
                                }
                                image { large }
                                description
                                primaryOccupations
                                gender
                                dateOfBirth { year month day }
                                age
                                homeTown
                                characterMedia(page: 1, perPage: 24) {
                                    edges {
                                        characterRole
                                        node {
                                            id
                                            idMal
                                            title { userPreferred english romaji native }
                                            coverImage { large }
                                            type
                                        }
                                        characters {
                                            id
                                            name { userPreferred full first middle last native alternative }
                                            image { medium }
                                        }
                                    }
                                }
                                staffMedia(page: 1, perPage: 24) {
                                    edges {
                                        staffRole
                                        node {
                                            id
                                            idMal
                                            title { userPreferred english romaji native }
                                            coverImage { large }
                                            type
                                        }
                                    }
                                }
                            }
                        }
                    """.trimIndent()
                    val variables = JSONObject().put("id", staffId)
                    runCatching {
                        val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return@runCatching null
                        val root = JSONObject(response)
                        val data = root.optJSONObject("data")?.optJSONObject("Staff") ?: return@runCatching null

                        val nameObj = data.optJSONObject("name")
                        val personName = nameObj.aniListPersonName()
                        val name = personName.preferred
                        val nativeName = nameObj?.optNullableString("native")

                        val alternativeNames = mutableListOf<String>()
                        val altArray = nameObj?.optJSONArray("alternative")
                        if (altArray != null) {
                            for (i in 0 until altArray.length()) {
                                val alt = altArray.optString(i)
                                if (alt.isNotBlank() && alt != "null") alternativeNames.add(alt)
                            }
                        }

                        val imageUrl = data.optJSONObject("image")?.optNullableString("large")
                        val biography = data.optNullableString("description")?.cleanApiText()?.takeIf { it.isNotBlank() }

                        val gender = data.optNullableString("gender")?.toTurkishGender()
                        val age = data.optNullableString("age")
                        val homeTown = data.optNullableString("homeTown")

                        val occupArray = data.optJSONArray("primaryOccupations")
                        val occupation = if (occupArray != null && occupArray.length() > 0) {
                            val list = mutableListOf<String>()
                            for (i in 0 until occupArray.length()) {
                                val occ = occupArray.optString(i)
                                if (occ.isNotBlank() && occ != "null") list.add(occ.toTurkishStaffRole())
                            }
                            list.joinToString(", ")
                        } else null

                        val dobObj = data.optJSONObject("dateOfBirth")
                        val birthday = if (dobObj != null) {
                            val d = dobObj.optInt("day", 0)
                            val m = dobObj.optInt("month", 0)
                            val y = dobObj.optInt("year", 0)
                            if (d > 0 && m > 0) {
                                if (y > 0) "$d/$m/$y" else "$d/$m"
                            } else null
                        } else null

                        val characterRoles = mutableListOf<KitsugiStaffCharacterRole>()
                        val charMediaEdges = data.optJSONObject("characterMedia")?.optJSONArray("edges")
                        if (charMediaEdges != null) {
                            for (i in 0 until charMediaEdges.length()) {
                                val edge = charMediaEdges.optJSONObject(i) ?: continue
                                val charRole = (edge.optNullableString("characterRole") ?: "Bilinmeyen").toTurkishCharacterRole()
                                val mediaNode = edge.optJSONObject("node") ?: continue
                                val mediaId = mediaNode.optInt("idMal").takeIf { it > 0 } ?: (100_000_000 + mediaNode.optInt("id"))
                                val mediaTitleObj = mediaNode.optJSONObject("title")
                                val mediaTitle = mediaTitleObj?.optNullableString("userPreferred") ?: "Bilinmeyen"
                                val mediaTitleEnglish = mediaTitleObj?.optNullableString("english")
                                val mediaTitleNative = mediaTitleObj?.optNullableString("native")
                                val mediaTitleRomaji = mediaTitleObj?.optNullableString("romaji")
                                val mediaImg = mediaNode.optJSONObject("coverImage")?.optNullableString("large")
                                val mediaType = mediaNode.optNullableString("type").orEmpty().toTurkishMediaTypeString()

                                val chars = edge.optJSONArray("characters")
                                if (chars != null && chars.length() > 0) {
                                    val charObj = chars.optJSONObject(0) ?: continue
                                    val charId = charObj.optInt("id")
                                    val charPersonName = charObj.optJSONObject("name").aniListPersonName()
                                    val charName = charPersonName.preferred
                                    val charImg = charObj.optJSONObject("image")?.optNullableString("medium")

                                    characterRoles.add(KitsugiStaffCharacterRole(
                                        characterId = charId,
                                        characterName = charName,
                                        characterRomanizedName = charPersonName.romanized,
                                        characterNativeName = charPersonName.native,
                                        characterImageUrl = charImg,
                                        characterSource = "anilist",
                                        mediaId = mediaId,
                                        mediaTitle = mediaTitle,
                                        mediaImageUrl = mediaImg,
                                        mediaType = mediaType,
                                        characterRole = charRole,
                                        mediaSource = "anilist"
                                    ))
                                }
                            }
                        }

                        val mediaWorks = mutableListOf<KitsugiStaffMediaWork>()
                        val staffMediaEdges = data.optJSONObject("staffMedia")?.optJSONArray("edges")
                        if (staffMediaEdges != null) {
                            for (i in 0 until staffMediaEdges.length()) {
                                val edge = staffMediaEdges.optJSONObject(i) ?: continue
                                val staffRole = (edge.optNullableString("staffRole") ?: "Ekip Üyesi").toTurkishStaffRole()
                                val mediaNode = edge.optJSONObject("node") ?: continue
                                val mediaId = mediaNode.optInt("idMal").takeIf { it > 0 } ?: (100_000_000 + mediaNode.optInt("id"))
                                val staffTitleObj = mediaNode.optJSONObject("title")
                                val mediaTitle = staffTitleObj?.optNullableString("userPreferred") ?: "Bilinmeyen"
                                val mediaTitleEnglish = staffTitleObj?.optNullableString("english")
                                val mediaTitleNative = staffTitleObj?.optNullableString("native")
                                val mediaTitleRomaji = staffTitleObj?.optNullableString("romaji")
                                val mediaImg = mediaNode.optJSONObject("coverImage")?.optNullableString("large")
                                val mediaType = mediaNode.optNullableString("type").orEmpty().toTurkishMediaTypeString()

                                mediaWorks.add(KitsugiStaffMediaWork(
                                    mediaId = mediaId,
                                    mediaTitle = mediaTitle,
                                    mediaImageUrl = mediaImg,
                                    mediaType = mediaType,
                                    staffRole = staffRole,
                                    source = "anilist",
                                    titleEnglish = mediaTitleEnglish,
                                    titleJapanese = mediaTitleNative,
                                    titleRomaji = mediaTitleRomaji
                                ))
                            }
                        }

                        val isFavourite = data.optBoolean("isFavourite", false)
                        val resolvedBiography = biography ?: try {
                            if (KitsugiPersonImageAggregator.isUnambiguousName(name)) {
                                val jikanSearch = JikanApiClient().searchMalPeople(name, page = 1)
                                val firstMalId = jikanSearch
                                    .firstOrNull { KitsugiPersonImageAggregator.nameMatches(name, it.title) }
                                    ?.malId
                                if (firstMalId != null && firstMalId > 0) {
                                    val jikanUrl = "https://api.jikan.moe/v4/people/$firstMalId"
                                    val jikanResp = (JikanGateway.fetch(jikanUrl, JikanGateway.Priority.UI) as? JikanResult.Ok)?.body
                                    jikanResp?.let { JSONObject(it).optJSONObject("data")?.optNullableString("about")?.cleanApiText()?.takeIf { b -> b.isNotBlank() } }
                                } else null
                            } else null
                        } catch (c: kotlinx.coroutines.CancellationException) {
                            throw c
                        } catch (_: Exception) {
                            null
                        }

                        KitsugiStaffDetail(
                            id = staffId,
                            name = name,
                            nativeName = nativeName,
                            alternativeNames = alternativeNames,
                            imageUrl = imageUrl,
                            biography = resolvedBiography,
                            occupation = occupation,
                            birthday = birthday,
                            age = age,
                            gender = gender,
                            homeTown = homeTown,
                            characterRoles = characterRoles,
                            mediaWorks = mediaWorks,
                            isFavourite = isFavourite,
                            aniListId = staffId,
                            romanizedName = personName.romanized
                        )
                    }.getOrNull()
                }
                "tmdb" -> {
                    val tmdbRes = TmdbApiClient().fetchPersonStaffDetail(staffId)
                    if (tmdbRes != null && (tmdbRes.biography.isNullOrBlank() || KitsugiApplication.getInstance()?.let { com.kitsugi.animelist.data.auth.ExternalAuthManager.getAniListToken(it) } != null)) {
                        val aniListDetail = fetchAniListStaffByName(tmdbRes.name)
                        if (aniListDetail != null) {
                            tmdbRes.copy(
                                biography = tmdbRes.biography?.takeIf { it.isNotBlank() } ?: aniListDetail.biography,
                                isFavourite = aniListDetail.isFavourite,
                                aniListId = aniListDetail.id
                            )
                        } else {
                            tmdbRes
                        }
                    } else {
                        tmdbRes
                    }
                }
                "kitsu" -> {
                    var targetName = name
                    if (targetName.isNullOrBlank() && staffId > 0) {
                        targetName = runCatching {
                            val req = okhttp3.Request.Builder()
                                .url(KitsuApiHost.url("/people/$staffId"))
                                .header("Accept", "application/vnd.api+json")
                                .header("User-Agent", "KitsugiApp/2.4")
                                .build()
                            com.kitsugi.animelist.core.network.KitsugiHttpClient.client.newCall(req).execute().use { resp ->
                                if (resp.isSuccessful) {
                                    val root = org.json.JSONObject(resp.body?.string().orEmpty())
                                    root.optJSONObject("data")?.optJSONObject("attributes")?.optString("name")
                                } else null
                            }
                        }.getOrNull()
                    }
                    if (!targetName.isNullOrBlank()) {
                        val aniStaff = fetchAniListStaffByName(targetName)
                        if (aniStaff != null) return@withContext aniStaff
                    }
                    if (staffId > 0) {
                        runCatching {
                            fetchStaffDetail("jikan", staffId, targetName)
                        }.getOrNull()
                    } else null
                }
                else -> null
            }
        }
    }

    /**
     * Kimliksiz kişi kayıtlarını (örn. MAL v2 `authors[].node` — id eksikse) ada göre
     * Jikan kişiler arasında çözümler. Ad eşleşmesi zorunludur; eşleşmezse null döner.
     */
    private suspend fun resolveJikanPersonIdByName(name: String): Int? = runCatching {
        JikanApiClient().searchMalPeople(name, page = 1)
            .firstOrNull { KitsugiPersonImageAggregator.nameMatches(name, it.title) }
            ?.malId
            ?.takeIf { it > 0 }
    }.getOrNull()

    private suspend fun fetchAniListStaffByName(name: String): KitsugiStaffDetail? {
        val query = """
            query (${'$'}search: String) {
                Page(page: 1, perPage: 1) {
                    staff(search: ${'$'}search) {
                        id
                        isFavourite
                        name {
                            userPreferred
                            full first middle last
                            native
                            alternative
                        }
                        image { large }
                        description
                        primaryOccupations
                        gender
                        dateOfBirth { year month day }
                        age
                        homeTown
                        characterMedia(page: 1, perPage: 24) {
                            edges {
                                characterRole
                                node {
                                    id
                                    idMal
                                    title { userPreferred english romaji native }
                                    coverImage { large }
                                    type
                                }
                                characters {
                                    id
                                    name { userPreferred full first middle last native alternative }
                                    image { medium }
                                }
                            }
                        }
                        staffMedia(page: 1, perPage: 24) {
                            edges {
                                staffRole
                                node {
                                    id
                                    idMal
                                    title { userPreferred english romaji native }
                                    coverImage { large }
                                    type
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        val variables = JSONObject().put("search", name)
        return runCatching {
            val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return@runCatching null
            val root = JSONObject(response)
            val staffArr = root.optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("staff") ?: return@runCatching null
            if (staffArr.length() == 0) return@runCatching null
            val data = staffArr.getJSONObject(0)

            val nameObj = data.optJSONObject("name")
            val personName = nameObj.aniListPersonName()
            val staffName = personName.preferred
            val nativeName = nameObj?.optNullableString("native")

            val alternativeNames = mutableListOf<String>()
            val altArray = nameObj?.optJSONArray("alternative")
            if (altArray != null) {
                for (i in 0 until altArray.length()) {
                    val alt = altArray.optString(i)
                    if (alt.isNotBlank() && alt != "null") alternativeNames.add(alt)
                }
            }

            val imageUrl = data.optJSONObject("image")?.optNullableString("large")
            val biography = data.optNullableString("description")?.cleanApiText()

            val primaryOccupations = mutableListOf<String>()
            data.optJSONArray("primaryOccupations")?.let { arr ->
                for (i in 0 until arr.length()) primaryOccupations.add(arr.getString(i))
            }
            val occupation = primaryOccupations.joinToString(", ").takeIf { it.isNotBlank() }

            val gender = data.optNullableString("gender")?.toTurkishGender()
            val birthday = buildString {
                val dob = data.optJSONObject("dateOfBirth")
                if (dob != null) {
                    val d = dob.optionalPositiveInt("day")
                    val m = dob.optionalPositiveInt("month")
                    val y = dob.optionalPositiveInt("year")
                    if (d != null && m != null) {
                        append("$d.$m")
                        if (y != null) append(".$y")
                    } else if (y != null) {
                        append(y)
                    }
                }
            }.takeIf { it.isNotBlank() }

            val age = data.optNullableString("age")
            val homeTown = data.optNullableString("homeTown")

            val characterRoles = mutableListOf<KitsugiStaffCharacterRole>()
            val charMedia = data.optJSONObject("characterMedia")
            val charEdges = charMedia?.optJSONArray("edges")
            if (charEdges != null) {
                for (i in 0 until charEdges.length()) {
                    val edge = charEdges.optJSONObject(i) ?: continue
                    val node = edge.optJSONObject("node") ?: continue
                    val charRole = (edge.optNullableString("characterRole") ?: "SUPPORTING").toTurkishCharacterRole()
                    val mediaId = node.optInt("id")
                    val idMal = node.optionalPositiveInt("idMal")
                    val mediaTitle = node.optJSONObject("title")?.optNullableString("userPreferred") ?: "Bilinmeyen"
                    val mediaImg = node.optJSONObject("coverImage")?.optNullableString("large")
                    val type = node.optNullableString("type").orEmpty().lowercase()

                    val stableMediaId = idMal ?: (100_000_000 + mediaId)

                    val charArr = edge.optJSONArray("characters")
                    if (charArr != null && charArr.length() > 0) {
                        val charObj = charArr.getJSONObject(0)
                        val charId = charObj.optInt("id")
                        val charPersonName = charObj.optJSONObject("name").aniListPersonName()
                        val charName = charPersonName.preferred
                        val charImg = charObj.optJSONObject("image")?.optNullableString("medium")

                        characterRoles.add(
                            KitsugiStaffCharacterRole(
                                characterId = charId,
                                characterName = charName,
                                characterRomanizedName = charPersonName.romanized,
                                characterNativeName = charPersonName.native,
                                characterImageUrl = charImg,
                                characterSource = "anilist",
                                mediaId = stableMediaId,
                                mediaTitle = mediaTitle,
                                mediaImageUrl = mediaImg,
                                mediaType = type.toTurkishMediaTypeString(),
                                characterRole = charRole
                            )
                        )
                    }
                }
            }

            val mediaWorks = mutableListOf<KitsugiStaffMediaWork>()
            val staffMedia = data.optJSONObject("staffMedia")
            val staffEdges = staffMedia?.optJSONArray("edges")
            if (staffEdges != null) {
                for (i in 0 until staffEdges.length()) {
                    val edge = staffEdges.optJSONObject(i) ?: continue
                    val node = edge.optJSONObject("node") ?: continue
                    val staffRole = (edge.optNullableString("staffRole") ?: "Staff").toTurkishStaffRole()
                    val mediaId = node.optInt("id")
                    val idMal = node.optionalPositiveInt("idMal")
                    val staffTitleObj = node.optJSONObject("title")
                    val mediaTitle = staffTitleObj?.optNullableString("userPreferred") ?: "Bilinmeyen"
                    val mediaTitleEnglish = staffTitleObj?.optNullableString("english")
                    val mediaTitleNative = staffTitleObj?.optNullableString("native")
                    val mediaTitleRomaji = staffTitleObj?.optNullableString("romaji")
                    val mediaImg = node.optJSONObject("coverImage")?.optNullableString("large")
                    val type = node.optNullableString("type").orEmpty().lowercase()

                    val stableMediaId = idMal ?: (100_000_000 + mediaId)

                    mediaWorks.add(
                        KitsugiStaffMediaWork(
                            mediaId = stableMediaId,
                            mediaTitle = mediaTitle,
                            mediaImageUrl = mediaImg,
                            mediaType = type.toTurkishMediaTypeString(),
                            staffRole = staffRole,
                            source = if (idMal != null) "jikan" else "anilist",
                            titleEnglish = mediaTitleEnglish,
                            titleJapanese = mediaTitleNative,
                            titleRomaji = mediaTitleRomaji
                        )
                    )
                }
            }

            val isFavourite = data.optBoolean("isFavourite", false)
            KitsugiStaffDetail(
                id = data.optInt("id"),
                name = staffName,
                nativeName = nativeName,
                alternativeNames = alternativeNames,
                imageUrl = imageUrl,
                biography = biography,
                occupation = occupation,
                birthday = birthday,
                age = age,
                gender = gender,
                homeTown = homeTown,
                characterRoles = characterRoles,
                mediaWorks = mediaWorks,
                isFavourite = isFavourite,
                aniListId = data.optInt("id"),
                romanizedName = personName.romanized
            )
        }.getOrNull()
    }

    private fun mergeAndEnrichStaffLists(
        primary: List<KitsugiStaff>,
        secondary: List<KitsugiStaff>,
        tertiary: List<KitsugiStaff>
    ): List<KitsugiStaff> {
        val all = primary + secondary + tertiary
        if (all.isEmpty()) return emptyList()

        fun normLatin(text: String?): String? {
            val s = text?.trim()?.takeIf { it.isNotBlank() && !PreferenceHelpers.hasCjkCharacters(it) } ?: return null
            val tokens = s.lowercase()
                .replace(Regex("[^a-z0-9\\s]"), " ")
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .sorted()
            return tokens.takeIf { it.isNotEmpty() }?.joinToString(" ")
        }

        fun normNative(text: String?): String? {
            val s = text?.trim()?.replace(Regex("\\s+"), "") ?: return null
            return s.takeIf { it.isNotBlank() && PreferenceHelpers.hasCjkCharacters(it) }
        }

        val merged = mutableListOf<KitsugiStaff>()
        val latinIndex = mutableMapOf<String, Int>()
        val nativeIndex = mutableMapOf<String, Int>()

        for (item in all) {
            val lKey = normLatin(item.romanizedName) ?: normLatin(item.englishName) ?: normLatin(item.name)
            val nKey = normNative(item.nativeName) ?: normNative(item.name)

            val existingIdx = (lKey?.let { latinIndex[it] }) ?: (nKey?.let { nativeIndex[it] })
            if (existingIdx == null) {
                val idx = merged.size
                merged.add(item)
                if (lKey != null) latinIndex[lKey] = idx
                if (nKey != null) nativeIndex[nKey] = idx
            } else {
                val cur = merged[existingIdx]
                val bestRomanized = cur.romanizedName?.takeIf { it.isNotBlank() && !PreferenceHelpers.hasCjkCharacters(it) }
                    ?: item.romanizedName?.takeIf { it.isNotBlank() && !PreferenceHelpers.hasCjkCharacters(it) }
                    ?: cur.name.takeIf { !PreferenceHelpers.hasCjkCharacters(it) }
                    ?: item.name.takeIf { !PreferenceHelpers.hasCjkCharacters(it) }
                val bestNative = cur.nativeName?.takeIf { it.isNotBlank() }
                    ?: item.nativeName?.takeIf { it.isNotBlank() }
                    ?: cur.name.takeIf { PreferenceHelpers.hasCjkCharacters(it) }
                    ?: item.name.takeIf { PreferenceHelpers.hasCjkCharacters(it) }
                val bestEnglish = cur.englishName?.takeIf { it.isNotBlank() }
                    ?: item.englishName?.takeIf { it.isNotBlank() }
                val bestDisplay = bestRomanized ?: bestEnglish ?: cur.name.ifBlank { item.name }
                val bestImage = cur.imageUrl?.takeIf { it.isNotBlank() }
                    ?: item.imageUrl?.takeIf { it.isNotBlank() }
                val combinedRoles = (cur.role.split(",") + item.role.split(","))
                    .map { it.trim() }
                    .filter { it.isNotBlank() && !it.equals("Ekip Üyesi", ignoreCase = true) }
                    .distinctBy { it.lowercase() }
                    .ifEmpty { listOf(cur.role.ifBlank { KitsugiStrings.get(R.string.ekip_uyesi) }) }
                    .take(3)
                    .joinToString(", ")

                val updated = cur.copy(
                    name = bestDisplay,
                    role = combinedRoles,
                    imageUrl = bestImage,
                    romanizedName = bestRomanized,
                    nativeName = bestNative,
                    englishName = bestEnglish
                )
                merged[existingIdx] = updated
                if (lKey != null) latinIndex[lKey] = existingIdx
                if (nKey != null) nativeIndex[nKey] = existingIdx
            }
        }

        fun rolePriority(role: String): Int {
            val r = role.lowercase()
            return when {
                r.contains("yönetmen") && !r.contains("yardımcı") && !r.contains("bölüm") -> 0
                r.contains("orijinal yaratıcı") || r.contains("yaratıcı") || r.contains("eser sahibi") -> 1
                r.contains("seri kompozisyonu") || r.contains("senaryo") || r.contains("yazar") -> 2
                r.contains("karakter tasarımı") -> 3
                r.contains("müzik") || r.contains("besteci") -> 4
                r.contains("baş animasyon") || r.contains("ses yönetmeni") || r.contains("sanat yönetmeni") -> 5
                r.contains("yapımcı") -> 6
                else -> 7
            }
        }

        return merged.sortedWith(
            compareBy<KitsugiStaff> { PreferenceHelpers.hasCjkCharacters(it.name) && it.romanizedName.isNullOrBlank() }
                .thenBy { rolePriority(it.role) }
                .thenByDescending { !it.imageUrl.isNullOrBlank() }
        )
    }
}
