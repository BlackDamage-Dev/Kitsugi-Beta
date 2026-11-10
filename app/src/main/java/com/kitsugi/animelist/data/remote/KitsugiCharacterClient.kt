package com.kitsugi.animelist.data.remote

import android.util.Log
import com.kitsugi.animelist.KitsugiApplication
import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.URL
import com.kitsugi.animelist.utils.*

class KitsugiCharacterClient {

    companion object {
        private const val TAG = "KitsugiCharacterClient"

        /**
         * Bangumi karakter/seslendirmen adları için AniList köprüsü süre tavanı.
         * Süre dolarsa ya da istek başarısız olursa liste orijinal (özgün) adlarla döner.
         */
        private const val BANGUMI_NAME_BRIDGE_TIMEOUT_MS = 8_000L
        private const val BANGUMI_INFOBOX_NAME_BRIDGE_TIMEOUT_MS = 7_500L

        /** Kitsu listesi VA'sızken MAL/AniList'ten VA eklemek için bekleme sınırı (yavaşsa VA'sız gösterilir). */
        private const val KITSU_VA_MERGE_TIMEOUT_MS = 5_000L

        /** Shikimori listesinde VA eksiği varsa MAL/AniList yedeği için bekleme sınırı. */
        private const val SHIKIMORI_VA_MERGE_TIMEOUT_MS = 5_000L

        /** Karakter görselleri için yapım düzeyinde denenecek en fazla başlık adayı. */
        private const val MAX_ANILIST_TITLE_LOOKUPS = 4

        /** Görseli boş kalan karakterler için yapılacak en fazla AniList ad araması. */
        private const val MAX_ANILIST_CHAR_IMAGE_LOOKUPS = 8

        /** Karakter adı aramaları için toplam süre bütçesi — sekme bunu beklemez. */
        private const val ANILIST_CHAR_IMAGE_FALLBACK_BUDGET_MS = 9_000L

        /**
         * TMDB kaynaklı animasyon/anime içeriklerinde AniList görsel bulamadığında denenen
         * MAL/Jikan, Shikimori ve Kitsu yedeklerinin her biri için ayrı süre bütçesi.
         */
        private const val CROSS_SOURCE_CHAR_IMAGE_BUDGET_MS = 5_000L

        /** İsim eşleştirmede "aynı kişi mi" ayrımını bozan sıfatlar. */
        private val ANI_CHAR_NAME_MODIFIERS = setOf(
            "former", "self", "child", "young", "older", "future", "past",
            "baby", "shadow", "clone", "alter", "dark", "fake"
        )
    }

    /**
     * [realMalId] — AniList kaynaklı entrylerde ARM veya detailState'ten gelen gerçek MAL ID.
     * Bu değer varsa Jikan fetch'i için [externalId] yerine kullanılır.
     */
    suspend fun fetchCharacters(
        source: String,
        externalId: Int?,
        mediaType: MediaType,
        realMalId: Int? = null,
        tmdbId: Int? = null,
        title: String? = null,
        /**
         * `false` → Bangumi listesi **ham** döner (kanji adlar); adlar daha sonra
         * [enrichBangumiCharacters] ile arka planda doldurulur. Detay sekmeleri ilk
         * boyamayı bu modda yapar, böylece sekme ağ zenginleştirmesini beklemez.
         */
        enrichNames: Boolean = true
    ): List<KitsugiCharacter> {
        return withContext(Dispatchers.IO) {
            if (externalId == null || externalId <= 0) {
                Log.w(TAG, "fetchCharacters: externalId geçersiz ($externalId), source=$source")
                return@withContext emptyList()
            }

            val srcLower = MalJikanMediaSupport.canonicalSource(source)
            Log.d(TAG, "fetchCharacters başladı: source=$source, externalId=$externalId, realMalId=$realMalId, mediaType=$mediaType, tmdbId=$tmdbId, title=$title")

            when (srcLower) {
                "bangumi", "bgm" -> {
                    // 1) Bangumi'nin KENDİ karakter + seslendirmen listesi (p1): eşleme gerektirmez.
                    val native = runCatching { KitsugiBangumiDetailClient.fetchCharacters(externalId, mediaType) }
                        .getOrNull().orEmpty()
                        .ifEmpty {
                            runCatching { KitsugiBangumiCreditsClient.fetchSubjectCharacters(externalId) }.getOrNull().orEmpty()
                        }
                    if (native.isNotEmpty()) {
                        if (!enrichNames) return@withContext native
                        // Bangumi liste uçları infobox döndürmez → adlar kanji kalır. AniList
                        // köprüsüyle (özgün ada birebir eşleme) romaji/İngilizce adlar doldurulur;
                        // başlık dili ROMAJI/ENGLISH olduğunda arayüz bunları gösterir.
                        return@withContext enrichBangumiCharacters(native, mediaType, realMalId, externalId)
                    }

                    // 2) Yedek: AniList araması → MAL kimliği çözülüp MAL/AniList karakterleri.
                    //    Bangumi stableId'si (500M+) ASLA MAL ID olarak kullanılmaz.
                    val cross = runCatching {
                        KitsugiBangumiDetailClient.resolveCrossIds(externalId, mediaType)
                    }.getOrNull()
                    val malId = cross?.malId ?: KitsugiBangumiDetailClient.sanitizeMalId(realMalId)
                    if (malId != null && malId > 0 && mediaType != MediaType.Movie && mediaType != MediaType.TvShow) {
                        fetchJikanOrAniListCharacters(malId, mediaType, tmdbId ?: cross?.tmdbId, title)
                    } else {
                        val resolvedTmdb = tmdbId ?: cross?.tmdbId
                        if (resolvedTmdb != null && resolvedTmdb > 0 && mediaType != MediaType.Manga) {
                            val (tmdbChars, _) = TmdbApiClient().fetchCredits(resolvedTmdb, mediaType == MediaType.Movie)
                            tmdbChars.map { it.copy(isRealMediaRole = true) }
                        } else emptyList()
                    }
                }
                "shikimori" -> {
                    // 1) Shikimori kendi karakter + seiyuu listesini verir: tek istek.
                    val shikiChars = KitsugiShikimoriClient.fetchCharacters(mediaType, externalId)

                    // 2) Seslendirmen eksiği yoksa MAL/AniList birleştirmesi HİÇ yapılmaz.
                    //    Eskiden her seferinde ARM + Jikan/AniList zinciri bekleniyordu ve
                    //    sekme bu yüzden onlarca saniye skeleton'da kalıyordu.
                    val needsVaMerge = shikiChars.isNotEmpty() && shikiChars.any { it.voiceActors.isEmpty() }
                    if (!needsVaMerge || mediaType == MediaType.Manga) {
                        return@withContext shikiChars
                    }

                    // 3) NOT: externalId burada Shikimori ID'sidir — MAL ID'si olarak KULLANILMAZ.
                    //    Gerçek MAL ID'si detay önbelleğinden (myanimelist_id) ya da ARM/Shikimori
                    //    API zincirinden çözülür.
                    val malId = realMalId?.takeIf { it > 0 }
                        ?: DetailCache.getMediaDetail("shikimori", externalId)?.realMalId
                        ?: KitsugiIdResolver.resolveMalIdFromShikimori(externalId)
                    if (malId == null || malId <= 0) {
                        return@withContext shikiChars
                    }

                    // 4) Yedek liste AYRI kapsamda başlatılır ve süreyle sınırlanır; yavaşsa
                    //    Shikimori listesi VA'sız hâliyle hemen gösterilir.
                    val refDeferred = vaMergeScope.async {
                        runCatching {
                            fetchCharacters("jikan", malId, mediaType, malId, tmdbId, title)
                        }.getOrNull()?.takeIf { it.isNotEmpty() }
                            ?: runCatching {
                                fetchCharacters("anilist", malId, mediaType, malId, tmdbId, title)
                            }.getOrNull()
                    }
                    val refChars = withTimeoutOrNull(SHIKIMORI_VA_MERGE_TIMEOUT_MS) { refDeferred.await() }
                        ?: run {
                            refDeferred.cancel()
                            emptyList<KitsugiCharacter>()
                        }
                    if (refChars.isEmpty()) {
                        shikiChars
                    } else {
                        mergeVoiceActorsIntoCharacters(shikiChars, refChars)
                    }
                }
                "simkl" -> {
                    val simklCross = KitsugiSimklDetailClient.resolveSimklCrossIds(
                        simklId = externalId,
                        mediaType = mediaType,
                        hintTmdbId = tmdbId,
                        hintMalId = realMalId
                    )
                    val simklDetail = DetailCache.getMediaDetail("simkl", externalId)
                    val malId = realMalId?.takeIf { it > 0 && it != externalId }
                        ?: simklCross.malId
                        ?: simklDetail?.realMalId
                    if (malId != null && malId > 0) {
                        val malList = fetchCharacters("jikan", malId, MediaType.Anime, malId, simklCross.tmdbId ?: tmdbId, title ?: simklCross.title)
                        if (malList.isNotEmpty()) return@withContext malList
                        val aniList = fetchCharacters("anilist", malId, MediaType.Anime, malId, simklCross.tmdbId ?: tmdbId, title ?: simklCross.title)
                        if (aniList.isNotEmpty()) return@withContext aniList
                    } else if (simklCross.aniListId != null && simklCross.aniListId > 0) {
                        val aniList = fetchCharacters("anilist", 100_000_000 + simklCross.aniListId, MediaType.Anime, null, simklCross.tmdbId ?: tmdbId, title ?: simklCross.title)
                        if (aniList.isNotEmpty()) return@withContext aniList
                    }
                    if (simklCross.kitsuId != null && simklCross.kitsuId > 0) {
                        val kitsuList = runCatching { KitsuClient.fetchKitsuCharacters(simklCross.kitsuId) }.getOrNull().orEmpty()
                        if (kitsuList.isNotEmpty()) return@withContext kitsuList
                    }
                    val resolvedTmdb = tmdbId ?: simklCross.tmdbId ?: run {
                        val malIdForResolve = malId ?: simklDetail?.realMalId
                        KitsugiIdResolver.resolveIds(malId = malIdForResolve, aniListId = simklCross.aniListId, tmdbId = tmdbId).tmdbId
                    }
                    if (resolvedTmdb != null && resolvedTmdb > 0) {
                        val isMovie = simklCross.isMovie || mediaType == MediaType.Movie
                        val (tmdbChars, _) = TmdbApiClient().fetchCredits(resolvedTmdb, isMovie)
                        val titleCandidates = buildTitleCandidates(title ?: simklCross.title, simklDetail)
                        if (tmdbChars.isNotEmpty()) {
                            return@withContext enrichTmdbCastCharacters(
                                tmdbChars = tmdbChars,
                                effectiveTmdbId = resolvedTmdb,
                                mediaType = mediaType,
                                mediaDetail = simklDetail,
                                titleCandidates = titleCandidates,
                                realMalId = malId,
                                hintAniListId = simklCross.aniListId,
                                hintKitsuId = simklCross.kitsuId,
                                hintImdbId = simklCross.imdbId
                            )
                        }
                    }
                    emptyList()
                }

                "tmdb" -> {
                    val effectiveTmdbId = tmdbId ?: externalId
                    if (effectiveTmdbId > 0) {
                        val isMovie = mediaType == MediaType.Movie
                        val (tmdbChars, _) = TmdbApiClient().fetchCredits(effectiveTmdbId, isMovie)
                        val mediaDetail = DetailCache.getMediaDetail("tmdb", effectiveTmdbId, mediaType.name)
                            ?: DetailCache.getMediaDetail("tmdb", effectiveTmdbId)
                        val titleCandidates = buildTitleCandidates(title, mediaDetail)
                        if (tmdbChars.isNotEmpty()) {
                            enrichTmdbCastCharacters(
                                tmdbChars = tmdbChars,
                                effectiveTmdbId = effectiveTmdbId,
                                mediaType = mediaType,
                                mediaDetail = mediaDetail,
                                titleCandidates = titleCandidates,
                                realMalId = realMalId ?: mediaDetail?.realMalId
                            )
                        } else {
                            tmdbChars
                        }
                    } else emptyList()
                }
                "jikan", "mal" -> {
                    val jikanId = MalJikanMediaSupport.resolveMalId(source, externalId, realMalId)
                        ?: return@withContext emptyList()
                    val jikanList = fetchJikanCharacterList(jikanId, mediaType)

                    if (jikanList.isNotEmpty()) {
                        jikanList
                    } else {
                        // MAL and Jikan are the same identity namespace. If Jikan is
                        // temporarily empty/unavailable, query AniList by this exact MAL ID
                        // before paying for a Shikimori ID-resolution fallback.
                        val aniListList = runCatching {
                            fetchCharacters("anilist", jikanId, mediaType, null, tmdbId, title)
                        }.getOrNull().orEmpty()
                        if (aniListList.isNotEmpty()) {
                            aniListList
                        } else {
                            Log.w(TAG, "Jikan/AniList karakter listesi boş; Shikimori fallback deneniyor")
                            // Shikimori endpoint'i KENDİ ID'sini bekler — MAL ID'si ARM ile
                            // Shikimori ID'sine çevrilmeden çağrılırsa YANLIŞ yapımın verisi gelir.
                            val shikiId = KitsugiIdResolver.resolveShikimoriIdFromMal(jikanId)
                            if (shikiId != null && shikiId > 0) {
                                KitsugiShikimoriClient.fetchCharacters(mediaType, shikiId)
                            } else {
                                emptyList()
                            }
                        }
                    }
                }

                "anilist" -> {
                    // externalId >= 100_000_000 ise AniList internal ID encode edilmiş demek
                    val aniListId = when {
                        externalId >= 100_000_000 -> externalId - 100_000_000
                        else -> null  // idMal kullan
                    }
                    Log.d(TAG, "AniList fetch: externalId=$externalId, aniListId=$aniListId")
                    val idParam = if (aniListId != null) "\$id: Int" else "\$idMal: Int"
                    val idFilter = if (aniListId != null) "id: \$id" else "idMal: \$idMal"
                    val query = """
                        query (${'$'}type: MediaType, $idParam) {
                            Media($idFilter, type: ${'$'}type) {
                                characters(page: 1, perPage: 24, sort: [RELEVANCE, ROLE, FAVOURITES_DESC]) {
                                    edges {
                                        role
                                        node {
                                            id
                                            name { userPreferred full first middle last native alternative }
                                            image { medium }
                                        }
                                        voiceActors(sort: [RELEVANCE, LANGUAGE]) {
                                            id
                                            name { userPreferred full first middle last native alternative }
                                            image { medium }
                                            languageV2
                                        }
                                    }
                                }
                            }
                        }
                    """.trimIndent()
                    val variables = JSONObject().put("type", MalJikanMediaSupport.aniListMediaType(mediaType))
                    if (aniListId != null) variables.put("id", aniListId) else variables.put("idMal", externalId)

                    runCatching {
                        val response = KitsugiApiBase.executeAniListQuery(query, variables)
                        if (response == null) {
                            Log.w(TAG, "AniList yanıt null: idParam=$idParam, value=${if (aniListId != null) aniListId else externalId}")
                            return@runCatching emptyList<KitsugiCharacter>()
                        }
                        // GraphQL hata kontrolu
                        if (response.contains("\"errors\"")) {
                            Log.e(TAG, "AniList GraphQL hata yanıtı: ${response.take(300)}")
                        }
                        val root = JSONObject(response)
                        val edges = root.optJSONObject("data")
                            ?.optJSONObject("Media")
                            ?.optJSONObject("characters")
                            ?.optJSONArray("edges")
                        if (edges == null) {
                            Log.w(TAG, "AniList edges null. data.Media null mu: ${root.optJSONObject("data")?.optJSONObject("Media") == null}")
                            return@runCatching emptyList<KitsugiCharacter>()
                        }
                        Log.d(TAG, "AniList karakter sayısı: ${edges.length()}")
                        val list = mutableListOf<KitsugiCharacter>()
                        for (i in 0 until edges.length()) {
                            val edge = edges.optJSONObject(i) ?: continue
                            val role = (edge.optNullableString("role") ?: "Bilinmeyen").toTurkishCharacterRole()
                            val node = edge.optJSONObject("node") ?: continue
                            val id = node.optInt("id")
                            val nameObj = node.optJSONObject("name")
                            val personName = nameObj.aniListPersonName()
                            val name = personName.preferred
                            val imageUrl = node.optJSONObject("image")?.optNullableString("medium")

                            val vaList = mutableListOf<KitsugiVoiceActor>()
                            val vaArray = edge.optJSONArray("voiceActors")
                            if (vaArray != null) {
                                for (j in 0 until vaArray.length()) {
                                    val vaItem = vaArray.optJSONObject(j) ?: continue
                                    val vaId = vaItem.optInt("id")
                                    val vaPersonName = vaItem.optJSONObject("name").aniListPersonName()
                                    val vaName = vaPersonName.preferred
                                    val vaLang = (vaItem.optNullableString("languageV2")
                                        ?: vaItem.optNullableString("language")
                                        ?: "Japanese").toTurkishLanguage()
                                    val vaImageUrl = vaItem.optJSONObject("image")?.optNullableString("medium")
                                    vaList.add(KitsugiVoiceActor(vaId, vaName, vaLang, vaImageUrl, source = "anilist", romanizedName = vaPersonName.romanized, nativeName = vaPersonName.native))
                                }
                            }
                            list.add(KitsugiCharacter(id, name, role, imageUrl, vaList, source = "anilist", romanizedName = personName.romanized, nativeName = personName.native))
                        }
                        list
                    }.getOrElse { err ->
                        Log.e(TAG, "AniList fetch exception: ${err.javaClass.simpleName}: ${err.message}", err)
                        emptyList()
                    }
                }


                "kitsu" -> {
                    // externalId = kitsuStableId = kitsuNumericId + 300_000_000
                    val kitsuOffset = 300_000_000
                    val kitsuNumericId = if (externalId >= kitsuOffset) externalId - kitsuOffset else externalId
                    Log.d(TAG, "Kitsu karakter fetch: stableId=$externalId, kitsuNumericId=$kitsuNumericId")
                    if (kitsuNumericId <= 0) return@withContext emptyList()

                    val kitsuList = runCatching {
                        KitsuClient.fetchKitsuCharacters(kitsuNumericId)
                    }.getOrElse { err ->
                        Log.e(TAG, "Kitsu karakter fetch hatası: ${err.message}", err)
                        emptyList()
                    }

                    // Kitsu listesi seslendirmenleriyle birlikte hazırsa HEMEN dön. Eski akış, VA
                    // eşlemesi için ARM + Jikan + AniList'i sırayla bekliyordu; Jikan yavaşken
                    // Kitsu karakter sekmesi dakikalarca skeleton'da kalıyordu.
                    if (kitsuList.isNotEmpty() && kitsuList.any { it.voiceActors.isNotEmpty() }) {
                        Log.d(TAG, "Kitsu karakter listesi başarılı: ${kitsuList.size} karakter")
                        return@withContext kitsuList
                    }

                    // Kitsu'da hiç VA yoksa ya da liste boşsa MAL/AniList yedeğine bak.
                    val effectiveMalId = realMalId?.takeIf { it > 0 }
                        ?: DetailCache.getMediaDetail("kitsu", externalId)?.realMalId
                        ?: runCatching {
                            KitsugiIdResolver.resolveIds(malId = null, aniListId = null, kitsuId = kitsuNumericId).malId
                        }.getOrNull()?.takeIf { it > 0 }

                    if (effectiveMalId == null || effectiveMalId <= 0) {
                        return@withContext kitsuList
                    }

                    if (kitsuList.isEmpty()) {
                        // Kitsu boş: MAL/AniList yedeği tek başına sonuçtur (süre sınırı VM tarafında).
                        Log.w(TAG, "Kitsu boş döndü, MAL/AniList yedeği deneniyor (malId=$effectiveMalId)")
                        val fallback = fetchJikanOrAniListCharacters(effectiveMalId, mediaType, tmdbId, title)
                        fallback.map { it.copy(source = "kitsu", voiceActors = it.voiceActors.map { va -> va.copy(source = "kitsu") }) }
                    } else {
                        // Kitsu listesi var ama VA'sız: yedek yalnızca VA eklemek için; kısa süre bekle,
                        // yavaşsa Kitsu listesini VA'sız olarak göster.
                        // Ayrı kapsamda başlatılır: withContext/withTimeoutOrNull bloklayan ağ çağrısını
                        // beklemeden dönemez. Süre dolunca UI Kitsu listesini VA'sız gösterir.
                        val refDeferred = vaMergeScope.async {
                            fetchJikanOrAniListCharacters(effectiveMalId, mediaType, tmdbId, title)
                        }
                        val refChars = withTimeoutOrNull(KITSU_VA_MERGE_TIMEOUT_MS) { refDeferred.await() }
                            ?: run {
                                refDeferred.cancel()
                                emptyList<KitsugiCharacter>()
                            }
                        if (refChars.isEmpty()) {
                            kitsuList
                        } else {
                            val kitsuRefChars = refChars.map { rc ->
                                rc.copy(voiceActors = rc.voiceActors.map { va -> va.copy(source = "kitsu") })
                            }
                            mergeVoiceActorsIntoCharacters(kitsuList, kitsuRefChars)
                        }
                    }
                }

                else -> {
                    // Bilinmeyen source — Jikan ile dene (MAL ID varsa)
                    val jikanId = realMalId?.takeIf { it > 0 } ?: externalId.takeIf { it > 0 && it < 100_000_000 }
                    if (jikanId != null) {
                        Log.w(TAG, "Bilinmeyen source '$source', Jikan fallback ile deneniyor: id=$jikanId")
                        fetchCharacters("jikan", jikanId, mediaType, null)
                    } else {
                        Log.e(TAG, "Bilinmeyen source '$source' ve geçerli MAL ID yok. Boş liste döndürülüyor.")
                        emptyList()
                    }
                }
            }
        }
    }

    /**
     * Bangumi karakter/seslendirmen listesindeki CJK adlara AniList köprüsüyle romaji ve
     * İngilizce karşılık ekler. Eşleme özgün (native) ada göredir; eşleşmeyen adlar olduğu
     * gibi kalır. Tek önbellekli AniList sorgusu kullanılır; süre tavanı/başarısızlıkta
     * liste orijinal haliyle döner (sekme kanji adlarla açılır, beklemez).
     */
    /**
     * Bangumi karakter listesini ad zenginleştirmesiyle döndürür (detay sekmesinin arka plan
     * geçişi). İlk boyama ham liste ile yapılır; bu çağrı tamamlanınca sekme Latin adlara
     * kendiliğinden geçer.
     */
    suspend fun enrichBangumiCharacters(
        characters: List<KitsugiCharacter>,
        mediaType: MediaType,
        realMalId: Int? = null,
        externalId: Int? = null
    ): List<KitsugiCharacter> {
        if (characters.isEmpty()) return characters
        val malIdForNames = KitsugiBangumiDetailClient.sanitizeMalId(realMalId)
            ?: externalId?.let { id ->
                runCatching { KitsugiBangumiDetailClient.resolveCrossIds(id, mediaType) }.getOrNull()?.malId
            }
        return enrichBangumiCharacterNames(characters, mediaType, malIdForNames)
    }

    /**
     * Bangumi karakter/seslendirmen listesindeki CJK adlara AniList köprüsüyle romaji ve
     * İngilizce karşılık ekler. Eşleme özgün (native) ada göredir; eşleşmeyen adlar olduğu
     * gibi kalır. Tek önbellekli AniList sorgusu kullanılır; süre tavanı/başarısızlıkta
     * liste orijinal haliyle döner (sekme kanji adlarla açılır, beklemez).
     */
    private suspend fun enrichBangumiCharacterNames(
        characters: List<KitsugiCharacter>,
        mediaType: MediaType,
        malId: Int?
    ): List<KitsugiCharacter> {
        if (characters.isEmpty()) return characters
        val needsEnrich = characters.any { char ->
            PreferenceHelpers.hasCjkCharacters(char.name) ||
                char.voiceActors.any { PreferenceHelpers.hasCjkCharacters(it.name) }
        }
        if (!needsEnrich) return characters

        // AniList is useful when the Bangumi record has a cross-ID, but its first two pages
        // don't cover every character/staff member. Keep it as the fast first pass only.
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
        val anilistEnriched = if (names == null) characters else characters.map { char ->
            val variant = if (PreferenceHelpers.hasCjkCharacters(char.name)) {
                KitsugiAniListPersonBridge.findByNative(names.characters, char.nativeName, char.name)
            } else null
            val voiceActors = char.voiceActors.map { va ->
                val vaVariant = if (PreferenceHelpers.hasCjkCharacters(va.name)) {
                    KitsugiAniListPersonBridge.findByNative(names.voiceActors, va.nativeName, va.name)
                } else null
                if (vaVariant == null) va else va.copy(
                    romanizedName = va.romanizedName ?: vaVariant.romaji,
                    englishName = va.englishName ?: vaVariant.english
                )
            }
            if (variant == null && voiceActors == char.voiceActors) return@map char
            char.copy(
                romanizedName = char.romanizedName ?: variant?.romaji,
                englishName = char.englishName ?: variant?.english,
                voiceActors = voiceActors
            )
        }

        // p1 person/character detail includes localized infobox aliases and also works when
        // AniList has no matching record or its page-one result set omitted the name.
        val enriched = try {
            withTimeoutOrNull(BANGUMI_INFOBOX_NAME_BRIDGE_TIMEOUT_MS) {
                KitsugiBangumiDetailClient.enrichCharacterNamesFromBangumi(anilistEnriched)
            } ?: anilistEnriched
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            anilistEnriched
        }
        // Süre dolduğunda AniList turu tek başına kalmış olabilir: `name` alanı hâlâ kanji
        // ise romaji/İngilizce karşılığı oraya da yaz (liste/çip/arama yolları `name` kullanır).
        return enriched.map { it.withLatinDisplayName() }
    }

    /** `name` CJK kaldıysa romaji (yoksa İngilizce) karşılığını ana ada yazar. */
    private fun KitsugiCharacter.withLatinDisplayName(): KitsugiCharacter {
        if (!PreferenceHelpers.hasCjkCharacters(name)) return this
        val latin = listOfNotNull(romanizedName, englishName).firstOrNull {
            it.isNotBlank() && PreferenceHelpers.isLatinText(it)
        } ?: return this
        return copy(name = latin, nativeName = nativeName ?: name)
    }

    /**
     * Kitsu VA birleştirme işleri için bağımsız kapsam (ana çağrının kapsamı DEĞİL). Böylece
     * süre dolduğunda ana çağrı bloklanmaz; arka plan işi kendi OkHttp callTimeout'u ile biter.
     */
    private val vaMergeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * MAL ID üzerinden Jikan, yoksa AniList'ten karakter listesi (yedek kaynak).
     */
    private suspend fun fetchJikanOrAniListCharacters(
        malId: Int,
        mediaType: MediaType,
        tmdbId: Int?,
        title: String?
    ): List<KitsugiCharacter> {
        val jikan = runCatching {
            fetchCharacters("jikan", malId, mediaType, malId, tmdbId, title)
        }.getOrNull()
        if (!jikan.isNullOrEmpty()) return jikan
        return runCatching {
            fetchCharacters("anilist", malId, mediaType, malId, tmdbId, title)
        }.getOrNull() ?: emptyList()
    }

    suspend fun fetchCharacterDetail(
        source: String,
        characterId: Int,
        name: String? = null,
        /**
         * Arayüzden gelen (kartta/tab'da gösterilmiş) görsel. Kaynak veride görsel yoksa
         * karakter detay sayfası boş kalmasın diye son çare olarak kullanılır.
         */
        fallbackImageUrl: String? = null,
        isRealMediaRole: Boolean = false
    ): KitsugiCharacterDetail? {
        return withContext(Dispatchers.IO) {
            if (characterId <= 0) return@withContext null
            when (MalJikanMediaSupport.canonicalSource(source)) {
                "bangumi", "bgm" -> {
                    val raw = KitsugiBangumiCreditsClient.fetchCharacterDetail(characterId, name, fallbackImageUrl)
                        ?: KitsugiBangumiDetailClient.fetchCharacterDetail(characterId)
                    // "Yapımlar" listesindeki CJK başlıklar romaji/İngilizce'ye çözülsün;
                    // süre bütçesi içeride korunur, başarısızlıkta ham veri döner.
                    raw?.let { KitsugiBangumiDetailClient.enrichCharacterDetailNames(it, cacheOnly = true) }
                }
                "shikimori" -> {
                    KitsugiShikimoriClient.fetchCharacterDetail(characterId)
                }
                "jikan", "mal" -> {
                    var detail: KitsugiCharacterDetail? = null
                    var networkError: Throwable? = null

                    // 1. MAL / Jikan API
                    val url = URL("https://api.jikan.moe/v4/characters/$characterId/full")
                    val jikanRes = runCatching {
                        KitsugiApiBase.runWithRateLimit {
                            val response = KitsugiApiBase.executeGetRequestOrThrow(url)
                            val root = JSONObject(response)
                            val data = root.optJSONObject("data") ?: return@runWithRateLimit null

                            val charName = (data.optNullableString("name") ?: "Bilinmeyen").toFriendlyName()
                            val nativeName = data.optNullableString("name_kanji")

                            val nicknamesArray = data.optJSONArray("nicknames")
                            val alternativeNames = mutableListOf<String>()
                            if (nicknamesArray != null) {
                                for (i in 0 until nicknamesArray.length()) {
                                    val nickname = nicknamesArray.optString(i)
                                    if (nickname.isNotBlank() && nickname != "null") {
                                        alternativeNames.add(nickname)
                                    }
                                }
                            }

                            val imageUrl = data.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url")
                            val biography = data.optNullableString("about")?.cleanApiText()?.takeIf { it.isNotBlank() }

                            // Parse voice actors
                            val voiceActors = mutableListOf<KitsugiVoiceActor>()
                            val voicesArray = data.optJSONArray("voices")
                            if (voicesArray != null) {
                                for (i in 0 until voicesArray.length()) {
                                    val item = voicesArray.optJSONObject(i) ?: continue
                                    val personObj = item.optJSONObject("person") ?: continue
                                    val vaId = personObj.optInt("mal_id")
                                    val vaName = (personObj.optNullableString("name") ?: "Bilinmeyen").toFriendlyName()
                                    val language = (item.optNullableString("language") ?: "Bilinmeyen").toTurkishLanguage()
                                    val vaImg = personObj.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url")
                                    voiceActors.add(KitsugiVoiceActor(vaId, vaName, language, vaImg))
                                }
                            }

                            // Parse media appearances
                            val mediaAppearances = mutableListOf<KitsugiCharacterMediaAppearance>()
                            val animeArray = data.optJSONArray("anime")
                            if (animeArray != null) {
                                for (i in 0 until animeArray.length()) {
                                    val item = animeArray.optJSONObject(i) ?: continue
                                    val role = (item.optNullableString("role") ?: "Bilinmeyen").toTurkishCharacterRole()
                                    val animeObj = item.optJSONObject("anime") ?: continue
                                    val mediaId = animeObj.optInt("mal_id")
                                    val title = animeObj.optNullableString("title") ?: "Bilinmeyen"
                                    val mediaImg = animeObj.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url")
                                    mediaAppearances.add(KitsugiCharacterMediaAppearance(
                                        mediaId = mediaId,
                                        title = title,
                                        imageUrl = mediaImg,
                                        mediaType = "anime".toTurkishMediaTypeString(),
                                        characterRole = role,
                                        source = "jikan"
                                    ))
                                }
                            }
                            val mangaArray = data.optJSONArray("manga")
                            if (mangaArray != null) {
                                for (i in 0 until mangaArray.length()) {
                                    val item = mangaArray.optJSONObject(i) ?: continue
                                    val role = (item.optNullableString("role") ?: "Bilinmeyen").toTurkishCharacterRole()
                                    val mangaObj = item.optJSONObject("manga") ?: continue
                                    val mediaId = mangaObj.optInt("mal_id")
                                    val title = mangaObj.optNullableString("title") ?: "Bilinmeyen"
                                    val mediaImg = mangaObj.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url")
                                    mediaAppearances.add(KitsugiCharacterMediaAppearance(
                                        mediaId = mediaId,
                                        title = title,
                                        imageUrl = mediaImg,
                                        mediaType = "manga".toTurkishMediaTypeString(),
                                        characterRole = role,
                                        source = "jikan"
                                    ))
                                }
                            }

                            KitsugiCharacterDetail(
                                id = characterId,
                                name = charName,
                                nativeName = nativeName,
                                alternativeNames = alternativeNames,
                                imageUrl = imageUrl,
                                gender = null,
                                age = null,
                                birthday = null,
                                bloodType = null,
                                biography = biography,
                                voiceActors = voiceActors.sortedByLanguagePreference(),
                                mediaAppearances = mediaAppearances
                            )
                        }
                    }
                    jikanRes.onSuccess {
                        detail = it
                    }.onFailure { err ->
                        if (err !is ResourceNotFoundException) {
                            networkError = err
                        }
                        Log.e(TAG, "Jikan fetch failed: ${err.message}", err)
                    }

                    // 2. Shikimori Fallback / Merge (yalnızca Jikan başarısızsa veya biyografi/görsel eksikse)
                    if (detail == null || detail?.biography.isNullOrBlank() || detail?.imageUrl.isNullOrBlank()) {
                        runCatching {
                            KitsugiShikimoriClient.fetchCharacterDetail(characterId)
                        }.onSuccess { shikiDetail ->
                            if (shikiDetail != null) {
                                detail = if (detail == null) shikiDetail else detail!!.mergeWith(shikiDetail)
                            }
                        }.onFailure { err ->
                            Log.e(TAG, "Shikimori character detail fallback failed: ${err.message}", err)
                        }
                    }

                    // 3. AniList Fallback / Merge
                    val targetName = detail?.name ?: name
                    if (!targetName.isNullOrBlank()) {
                        runCatching {
                            fetchAniListCharacterByName(targetName)
                        }.onSuccess { aniListDetail ->
                            if (aniListDetail != null) {
                                detail = if (detail == null) aniListDetail else detail!!.mergeWith(aniListDetail)
                            }
                        }.onFailure { err ->
                            Log.e(TAG, "AniList character detail fallback failed: ${err.message}", err)
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
                            Character(id: ${'$'}id) {
                                id
                                isFavourite
                                name {
                                    userPreferred
                                    full first middle last
                                    native
                                    alternative
                                    alternativeSpoiler
                                }
                                image {
                                    large
                                }
                                description
                                gender
                                dateOfBirth {
                                    year
                                    month
                                    day
                                }
                                age
                                bloodType
                                media(page: 1, perPage: 25) {
                                    edges {
                                        characterRole
                                        node {
                                            id
                                            idMal
                                            title { userPreferred english romaji native }
                                            coverImage { large }
                                            type
                                        }
                                        voiceActors {
                                            id
                                            name { userPreferred full first middle last native alternative }
                                            image { medium }
                                            language
                                            languageV2
                                        }
                                    }
                                }
                            }
                        }
                    """.trimIndent()
                    val variables = JSONObject().put("id", characterId)
                    runCatching {
                        val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return@runCatching null
                        val root = JSONObject(response)
                        val data = root.optJSONObject("data")?.optJSONObject("Character") ?: return@runCatching null

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
                        val altSpoilerArray = nameObj?.optJSONArray("alternativeSpoiler")
                        if (altSpoilerArray != null) {
                            for (i in 0 until altSpoilerArray.length()) {
                                val alt = altSpoilerArray.optString(i)
                                if (alt.isNotBlank() && alt != "null") alternativeNames.add(alt)
                            }
                        }

                        val imageUrl = data.optJSONObject("image")?.optNullableString("large")
                        val biography = data.optNullableString("description")?.cleanApiText()?.takeIf { it.isNotBlank() }
                        val gender = data.optNullableString("gender")?.toTurkishGender()
                        val age = data.optNullableString("age")
                        val bloodType = data.optNullableString("bloodType")

                        val dobObj = data.optJSONObject("dateOfBirth")
                        val birthday = if (dobObj != null) {
                            val d = dobObj.optInt("day", 0)
                            val m = dobObj.optInt("month", 0)
                            val y = dobObj.optInt("year", 0)
                            if (d > 0 && m > 0) {
                                if (y > 0) "$d/$m/$y" else "$d/$m"
                            } else null
                        } else null

                        // Parse media appearances & collect unique voice actors
                        val mediaAppearances = mutableListOf<KitsugiCharacterMediaAppearance>()
                        val vaMap = mutableMapOf<Int, KitsugiVoiceActor>()

                        val mediaEdges = data.optJSONObject("media")?.optJSONArray("edges")
                        if (mediaEdges != null) {
                            for (i in 0 until mediaEdges.length()) {
                                val edge = mediaEdges.optJSONObject(i) ?: continue
                                val characterRole = (edge.optNullableString("characterRole") ?: "Bilinmeyen").toTurkishCharacterRole()
                                val node = edge.optJSONObject("node") ?: continue

                                val rawId = node.optInt("id")
                                val idMal = node.optInt("idMal")
                                val mediaId = if (idMal > 0) idMal else (100_000_000 + rawId)
                                val titleObj = node.optJSONObject("title")
                                val titleRomaji = titleObj?.optNullableString("romaji")
                                val titleEnglish = titleObj?.optNullableString("english")
                                val titleNative = titleObj?.optNullableString("native")
                                val title = titleObj?.optNullableString("userPreferred")
                                    ?: titleRomaji ?: titleEnglish ?: titleNative ?: "Bilinmeyen"
                                val mediaImg = node.optJSONObject("coverImage")?.optNullableString("large")
                                val mediaType = node.optNullableString("type").orEmpty().toTurkishMediaTypeString()

                                mediaAppearances.add(KitsugiCharacterMediaAppearance(
                                    mediaId = mediaId,
                                    title = title,
                                    imageUrl = mediaImg,
                                    mediaType = mediaType,
                                    characterRole = characterRole,
                                    source = "anilist",
                                    titleEnglish = titleEnglish,
                                    titleJapanese = titleNative,
                                    titleRomaji = titleRomaji
                                ))

                                val vaArray = edge.optJSONArray("voiceActors")
                                if (vaArray != null) {
                                    for (j in 0 until vaArray.length()) {
                                        val vaItem = vaArray.optJSONObject(j) ?: continue
                                        val vaId = vaItem.optInt("id")
                                        val vaPersonName = vaItem.optJSONObject("name").aniListPersonName()
                                        val vaName = vaPersonName.preferred
                                        val vaLang = (vaItem.optNullableString("languageV2")
                                            ?: vaItem.optNullableString("language")
                                            ?: "Japanese").toTurkishLanguage()
                                        val vaImageUrl = vaItem.optJSONObject("image")?.optNullableString("medium")
                                        vaMap[vaId] = KitsugiVoiceActor(vaId, vaName, vaLang, vaImageUrl, source = "anilist", romanizedName = vaPersonName.romanized, nativeName = vaPersonName.native)
                                    }
                                }
                            }
                        }

                        val isFavourite = data.optBoolean("isFavourite", false)
                        val resolvedBiography = biography ?: try {
                            val lookupName = primaryCharacterName(name)
                            if (KitsugiPersonImageAggregator.isUnambiguousName(lookupName)) {
                                val jikanSearch = JikanApiClient().searchMalCharacters(lookupName, page = 1)
                                val firstMalId = jikanSearch
                                    .firstOrNull { KitsugiPersonImageAggregator.nameMatches(lookupName, it.title) }
                                    ?.malId
                                if (firstMalId != null && firstMalId > 0) {
                                    val jikanUrl = "https://api.jikan.moe/v4/characters/$firstMalId"
                                    val jikanResp = (JikanGateway.fetch(jikanUrl, JikanGateway.Priority.UI) as? JikanResult.Ok)?.body
                                    jikanResp?.let { JSONObject(it).optJSONObject("data")?.optNullableString("about")?.cleanApiText()?.takeIf { b -> b.isNotBlank() } }
                                } else null
                            } else null
                        } catch (c: kotlinx.coroutines.CancellationException) {
                            throw c
                        } catch (_: Exception) {
                            null
                        }

                        KitsugiCharacterDetail(
                            id = characterId,
                            name = name,
                            nativeName = nativeName,
                            alternativeNames = alternativeNames,
                            imageUrl = imageUrl,
                            gender = gender,
                            age = age,
                            birthday = birthday,
                            bloodType = bloodType,
                            biography = resolvedBiography,
                            voiceActors = vaMap.values.toList(),
                            mediaAppearances = mediaAppearances,
                            isFavourite = isFavourite,
                            aniListId = characterId,
                            romanizedName = personName.romanized
                        )
                    }.getOrNull() ?: run {
                        // AniList API kesintisinde (HTTP 403/503/429) sayfa hataya düşmesin;
                        // karakter adıyla Jikan / Bangumi yedeklerini dene.
                        val fallbackLookup = name?.trim()?.takeIf { it.isNotBlank() }
                        if (fallbackLookup != null) {
                            fetchFictionalCharacterDetailByName(fallbackLookup, fallbackImageUrl)
                        } else null
                    }
                }
                "tmdb" -> {
                    if (isRealMediaRole) {
                        val personDetail = TmdbApiClient().fetchPersonCharacterDetail(characterId)
                        return@withContext if (personDetail != null && personDetail.imageUrl.isNullOrBlank() && !fallbackImageUrl.isNullOrBlank()) {
                            personDetail.copy(imageUrl = fallbackImageUrl, source = "tmdb")
                        } else personDetail
                    }

                    val cleanCharName = name?.let(::cleanCharacterDisplayName)?.trim()
                    val personDetail = TmdbApiClient().fetchPersonCharacterDetail(characterId)
                    val actorPhotoUrl = personDetail?.imageUrl
                    val safeFallbackImage = fallbackImageUrl?.takeIf {
                        it.isNotBlank() && !DetailCache.isSameImageResource(it, actorPhotoUrl)
                    } ?: cleanCharName?.let { DetailCache.getCachedCharacterPortrait(it) }?.takeIf {
                        it.isNotBlank() && !DetailCache.isSameImageResource(it, actorPhotoUrl)
                    }

                    val matchingAppearances = if (personDetail != null && !cleanCharName.isNullOrBlank()) {
                        filterCharacterAppearances(personDetail.mediaAppearances, cleanCharName)
                    } else {
                        personDetail?.mediaAppearances.orEmpty()
                    }
                    val voiceActorList = if (personDetail != null) {
                        listOf(
                            KitsugiVoiceActor(
                                id = characterId,
                                name = personDetail.name,
                                language = "",
                                imageUrl = personDetail.imageUrl,
                                source = "tmdb"
                            )
                        )
                    } else emptyList()

                    // Kurgusal/anime karakteri ise seslendirmen biyografisi yerine AniList, Jikan,
                    // Bangumi, Fandom veya Vikipedi üzerinden gerçek kurgusal karakter profilini yükle.
                    if (!cleanCharName.isNullOrBlank()) {
                        val resolvedDetail = fetchFictionalCharacterDetailByName(
                            cleanCharName = cleanCharName,
                            fallbackImageUrl = safeFallbackImage,
                            mediaAppearancesHint = matchingAppearances
                        )
                        if (resolvedDetail != null) {
                            val mergedVas = if (resolvedDetail.voiceActors.isNotEmpty()) {
                                resolvedDetail.voiceActors
                            } else {
                                voiceActorList
                            }
                            val mergedAppearances = if (resolvedDetail.mediaAppearances.isNotEmpty()) {
                                resolvedDetail.mediaAppearances
                            } else {
                                matchingAppearances
                            }
                            return@withContext resolvedDetail.copy(
                                id = if (resolvedDetail.id > 0) resolvedDetail.id else characterId,
                                voiceActors = mergedVas,
                                mediaAppearances = mergedAppearances
                            )
                        }
                    }

                    if (!cleanCharName.isNullOrBlank()) {
                        val primaryName = primaryCharacterName(cleanCharName)
                        val roleAliases = extractSlashRoleAliases(cleanCharName, primaryName)
                        KitsugiCharacterDetail(
                            id = characterId,
                            name = primaryName,
                            nativeName = null,
                            alternativeNames = roleAliases,
                            imageUrl = safeFallbackImage,
                            gender = null,
                            age = null,
                            birthday = null,
                            bloodType = null,
                            biography = "Bu kurgusal karakter için ek biyografi bilgisi bulunmuyor.",
                            voiceActors = voiceActorList,
                            mediaAppearances = matchingAppearances,
                            source = "tmdb"
                        ).withRoleAlias(cleanCharName)
                    } else if (personDetail != null) {
                        if (personDetail.imageUrl.isNullOrBlank() && !fallbackImageUrl.isNullOrBlank()) {
                            personDetail.copy(imageUrl = fallbackImageUrl, source = "tmdb")
                        } else personDetail
                    } else null
                }
                "kitsu" -> {
                    val kitsuDetail = runCatching {
                        val request = okhttp3.Request.Builder()
                            .url(KitsuApiHost.url("/characters/$characterId"))
                            .header("Accept", "application/vnd.api+json")
                            .header("User-Agent", "Kitsugi/1.0 (Android)")
                            .build()
                        val responseBody = com.kitsugi.animelist.core.network.KitsugiHttpClient.client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) throw java.io.IOException("HTTP error ${response.code}")
                            response.body?.string() ?: ""
                        }
                        val root = JSONObject(responseBody)
                        val data = root.optJSONObject("data") ?: return@runCatching null
                        val attrs = data.optJSONObject("attributes") ?: return@runCatching null
                        val name = attrs.optString("name", attrs.optString("canonicalName", "Bilinmeyen"))
                        val description = attrs.optNullableString("description")?.cleanApiText()
                        val imageObj = attrs.optJSONObject("image")
                        val imageUrl = imageObj?.optString("original")
                            ?: imageObj?.optString("large")
                            ?: imageObj?.optString("medium")
                        val malId = attrs.optInt("malId", 0)

                        KitsuCharacterParsed(name, description, imageUrl, malId)
                    }.getOrNull()

                    // Kitsu karakter detayında fallbackImageUrl/name desteği eklendi:
                    // Kitsu API 404/429 veya malId eşleşmesi yoksa sayfa boş kalmasın diye
                    // AniList → Jikan → kart görseli yedekleri sırayla denenir.
                    if (kitsuDetail != null) {
                        val resolvedKitsuImage = kitsuDetail.imageUrl?.takeIf { it.isNotBlank() } ?: fallbackImageUrl?.takeIf { it.isNotBlank() }
                        if (kitsuDetail.malId > 0) {
                            val jikanDetail = runCatching {
                                fetchCharacterDetail("jikan", kitsuDetail.malId)
                            }.getOrNull()
                            if (jikanDetail != null) {
                                val mergedImage = jikanDetail.imageUrl?.takeIf { it.isNotBlank() } ?: resolvedKitsuImage
                                jikanDetail.copy(imageUrl = mergedImage, source = "kitsu")
                            } else {
                                KitsugiCharacterDetail(
                                    id = characterId,
                                    name = kitsuDetail.name,
                                    nativeName = null,
                                    alternativeNames = emptyList(),
                                    imageUrl = resolvedKitsuImage,
                                    gender = null,
                                    age = null,
                                    birthday = null,
                                    bloodType = null,
                                    biography = kitsuDetail.description,
                                    voiceActors = emptyList(),
                                    mediaAppearances = emptyList(),
                                    source = "kitsu"
                                )
                            }
                        } else {
                            // MAL eşleşmesi yoksa AniList ismiyle zenginleştir
                            val targetName = name?.takeIf { it.isNotBlank() } ?: kitsuDetail.name
                            val aniEnriched = if (!targetName.isNullOrBlank()) {
                                runCatching { fetchAniListCharacterByName(targetName) }.getOrNull()
                            } else null
                            if (aniEnriched != null) {
                                val finalImage = aniEnriched.imageUrl?.takeIf { it.isNotBlank() } ?: resolvedKitsuImage
                                aniEnriched.copy(imageUrl = finalImage, source = "kitsu")
                            } else {
                                KitsugiCharacterDetail(
                                    id = characterId,
                                    name = kitsuDetail.name,
                                    nativeName = null,
                                    alternativeNames = emptyList(),
                                    imageUrl = resolvedKitsuImage,
                                    gender = null,
                                    age = null,
                                    birthday = null,
                                    bloodType = null,
                                    biography = kitsuDetail.description,
                                    voiceActors = emptyList(),
                                    mediaAppearances = emptyList(),
                                    source = "kitsu"
                                )
                            }
                        }
                    } else {
                        // Kitsu API tamamen başarısız: isim üzerinden AniList/Jikan yedekleri
                        val targetName = name?.takeIf { it.isNotBlank() }
                        val aniFallback = if (!targetName.isNullOrBlank()) {
                            runCatching { fetchAniListCharacterByName(targetName) }.getOrNull()
                        } else null
                        if (aniFallback != null) {
                            val finalImage = aniFallback.imageUrl?.takeIf { it.isNotBlank() } ?: fallbackImageUrl?.takeIf { it.isNotBlank() }
                            aniFallback.copy(imageUrl = finalImage, source = "kitsu")
                        } else {
                            val jikanFallback = if (!targetName.isNullOrBlank()) {
                                runCatching {
                                    val searchResults = JikanApiClient().searchMalCharacters(targetName, page = 1)
                                    val firstMalId = searchResults.firstOrNull()?.malId
                                    if (firstMalId != null && firstMalId > 0) {
                                        fetchCharacterDetail("jikan", firstMalId, targetName, fallbackImageUrl)
                                    } else null
                                }.getOrNull()
                            } else null
                            if (jikanFallback != null) {
                                val finalImage = jikanFallback.imageUrl?.takeIf { it.isNotBlank() } ?: fallbackImageUrl?.takeIf { it.isNotBlank() }
                                if (finalImage != null) jikanFallback.copy(imageUrl = finalImage, source = "kitsu") else jikanFallback
                            } else if (!targetName.isNullOrBlank() && !fallbackImageUrl.isNullOrBlank()) {
                                KitsugiCharacterDetail(
                                    id = characterId,
                                    name = targetName,
                                    nativeName = null,
                                    alternativeNames = emptyList(),
                                    imageUrl = fallbackImageUrl,
                                    gender = null,
                                    age = null,
                                    birthday = null,
                                    bloodType = null,
                                    biography = "Kitsu karakter detayları şu anda Kitsu API üzerinden alınamadı. Kartta görünen görsel yedek olarak gösteriliyor.",
                                    voiceActors = emptyList(),
                                    mediaAppearances = emptyList(),
                                    source = "kitsu"
                                )
                            } else {
                                null
                            }
                        }
                    }
                }
                else -> null
            }
        }
    }

    /**
     * TMDB rol adını karakterin alternatif adlarına ekler.
     *
     * Kurgusal karakter başka bir kaynaktan (AniList/Jikan) çözüldüğünde ad farklı
     * yazılabilir: TMDB "Nobita" yazar, AniList "Nobita Nobi".
     * [CharacterDetailIdentity.matchesExpectedDetail] yalnızca BİREBİR ad eşleşmesi kabul
     * ettiği için doğru sonuç "kimlik uyuşmuyor" diye çöpe atılıyor, sayfa hata ekranına
     * düşüyor ve karakter görseli hiç görünmüyordu. Rol adı alternatif ad olarak eklendiğinde
     * kimlik doğrulanır; sayfa doğru karakterle ve görseliyle açılır.
     */
    private fun KitsugiCharacterDetail.withRoleAlias(roleName: String?): KitsugiCharacterDetail {
        val raw = roleName?.trim().orEmpty()
        if (raw.isBlank()) return this
        val segments = raw.split("/")
            .map { cleanCharacterDisplayName(it).trim() }
            .filter { it.isNotBlank() }
        if (segments.isEmpty()) return this
        val existing = (listOf(name, englishName, romanizedName, nativeName) + alternativeNames)
            .mapNotNull { it?.trim()?.lowercase() }
            .toMutableSet()
        val toAdd = segments.filter { seg -> existing.add(seg.lowercase()) }
        return if (toAdd.isEmpty()) this else copy(alternativeNames = alternativeNames + toAdd)
    }

    /**
     * TMDB karakter adı çoğunlukla "Gerçek Ad / Kurgusal Ad" biçimindedir
     * (ör. "Peter Parker / Spider-Man"). Arama ve doğrulama için yalnızca ilk parça
     * kullanılır; aksi halde "Spider-Man" gibi ortak kelimeler yanlış karakterle eşleşir.
     */
    private fun primaryCharacterName(name: String): String =
        name.substringBefore("/").substringBefore(" (").trim().ifBlank { name.trim() }

    private fun extractSlashRoleAliases(rawName: String, primaryName: String): List<String> =
        rawName.split("/")
            .map { cleanCharacterDisplayName(it).trim() }
            .filter { it.isNotBlank() && !it.equals(primaryName, ignoreCase = true) }
            .distinct()

    private fun filterCharacterAppearances(
        appearances: List<KitsugiCharacterMediaAppearance>,
        cleanCharName: String
    ): List<KitsugiCharacterMediaAppearance> {
        if (appearances.isEmpty()) return emptyList()
        val roleSegmentsNorm = cleanCharName.split("/")
            .map { normalizeCharacterMatchNorm(it) }
            .filter { it.length >= 2 }
        if (roleSegmentsNorm.isEmpty()) return appearances
        val matched = appearances.filter { app ->
            val appRoleNorm = normalizeCharacterMatchNorm(app.characterRole)
            appRoleNorm.length >= 2 && roleSegmentsNorm.any { seg ->
                appRoleNorm.contains(seg) || seg.contains(appRoleNorm)
            }
        }
        return matched.ifEmpty { appearances }
    }

    /**
     * AniList karakter düğümü: adı sorguyla eşleşiyor mu (birebir / jeton kümesi) ve
     * karakterin yer aldığı yapımlardan hiçbiri +18 değil mi?
     */
    private fun isVerifiedAniListCharacterNode(node: JSONObject, query: String): Boolean {
        val nameObj = node.optJSONObject("name") ?: return false
        val candidates = mutableListOf<String>()
        nameObj.optNullableString("userPreferred")?.let { candidates.add(it) }
        nameObj.optNullableString("full")?.let { candidates.add(it) }
        nameObj.optNullableString("native")?.let { candidates.add(it) }
        val alts = nameObj.optJSONArray("alternative")
        if (alts != null) {
            for (i in 0 until alts.length()) {
                val alt = alts.optString(i)
                if (alt.isNotBlank() && alt != "null") candidates.add(alt)
            }
        }
        if (candidates.none { KitsugiPersonImageAggregator.nameMatches(query, it) }) return false
        val edges = node.optJSONObject("media")?.optJSONArray("edges") ?: return true
        for (i in 0 until edges.length()) {
            val media = edges.optJSONObject(i)?.optJSONObject("node") ?: continue
            if (media.optBoolean("isAdult", false)) return false
        }
        return true
    }

    /**
     * AniList karakter araması (kimliksiz, yalnızca adla).
     *
     * @param allowSingleTokenName tek kelimeli Latin adlara ("Nobita", "Doraemon", "Goku")
     *   izin verilsin mi. Kurgusal karakter adlarının büyük bölümü tek kelimedir; katı kural
     *   bunları tümüyle reddettiği için TMDB/Simkl karakter detay sayfası hiç açılamıyordu.
     *   Güvenlik [isVerifiedAniListCharacterNode] ile sağlanır: sonucun adı sorguyla BİREBİR
     *   eşleşmek zorundadır (alternatif adlar dâhil) ve karakter +18 bir yapımda geçmemelidir.
     */
    private suspend fun fetchAniListCharacterByName(
        name: String,
        allowSingleTokenName: Boolean = false
    ): KitsugiCharacterDetail? {
        val query = """
            query (${'$'}search: String) {
                Page(page: 1, perPage: 8) {
                    characters(search: ${'$'}search) {
                        id
                        isFavourite
                        name {
                            userPreferred
                            full first middle last
                            native
                            alternative
                            alternativeSpoiler
                        }
                        image {
                            large
                        }
                        description
                        gender
                        dateOfBirth {
                            year
                            month
                            day
                        }
                        age
                        bloodType
                        media(page: 1, perPage: 25) {
                            edges {
                                characterRole
                                node {
                                    id
                                    idMal
                                    isAdult
                                    title { userPreferred english romaji native }
                                    coverImage { large }
                                    type
                                }
                                voiceActors {
                                    id
                                    name { userPreferred full first middle last native alternative }
                                    image { medium }
                                    language
                                    languageV2
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        // Kimliksiz isim araması normalde yalnızca ayırt edici adlarla yapılır (ör. "Suzu" değil).
        // Kurgusal karakterlerde ad çoğu zaman TEK kelimedir ("Nobita", "Doraemon"): bu adlar
        // reddedildiğinde TMDB/Simkl karakter detay sayfası hiçbir kaynaktan çözülemiyordu.
        // [allowSingleTokenName] yalnızca sonucun adı birebir doğrulanabildiği için açılır.
        if (!KitsugiPersonImageAggregator.isUnambiguousName(
                value = name,
                allowSingleTokenLatin = allowSingleTokenName
            )
        ) return null
        val variables = JSONObject().put("search", name)
        return runCatching {
            val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return@runCatching null
            val root = JSONObject(response)
            val charactersArr = root.optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("characters") ?: return@runCatching null
            if (charactersArr.length() == 0) return@runCatching null
            // İlk sonuç körlemesine alınmaz: yalnızca adı sorguyla eşleşen ve +18 yapımda
            // geçmeyen karakter kabul edilir (aynı isimli başka karakterin verisi karışmaz).
            val data = (0 until charactersArr.length())
                .mapNotNull { charactersArr.optJSONObject(it) }
                .firstOrNull { node -> isVerifiedAniListCharacterNode(node, name) }
                ?: return@runCatching null

            val nameObj = data.optJSONObject("name")
            val personName = nameObj.aniListPersonName()
            val charName = personName.preferred
            val nativeName = nameObj?.optNullableString("native")

            val alternativeNames = mutableListOf<String>()
            val altArray = nameObj?.optJSONArray("alternative")
            if (altArray != null) {
                for (i in 0 until altArray.length()) {
                    val alt = altArray.optString(i)
                    if (alt.isNotBlank() && alt != "null") alternativeNames.add(alt)
                }
            }
            val altSpoilerArray = nameObj?.optJSONArray("alternativeSpoiler")
            if (altSpoilerArray != null) {
                for (i in 0 until altSpoilerArray.length()) {
                    val alt = altSpoilerArray.optString(i)
                    if (alt.isNotBlank() && alt != "null") alternativeNames.add(alt)
                }
            }

            val imageUrl = data.optJSONObject("image")?.optNullableString("large")
            val biography = data.optNullableString("description")?.cleanApiText()

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
            val bloodType = data.optNullableString("bloodType")

            val mediaAppearances = mutableListOf<KitsugiCharacterMediaAppearance>()
            val voiceActors = mutableListOf<KitsugiVoiceActor>()

            val mediaObj = data.optJSONObject("media")
            val edges = mediaObj?.optJSONArray("edges")
            if (edges != null) {
                for (i in 0 until edges.length()) {
                    val edge = edges.optJSONObject(i) ?: continue
                    val node = edge.optJSONObject("node") ?: continue
                    val role = (edge.optNullableString("characterRole") ?: "SUPPORTING").toTurkishCharacterRole()
                    val mediaId = node.optInt("id")
                    val idMal = node.optionalPositiveInt("idMal")
                    val title = node.optJSONObject("title")?.optNullableString("userPreferred") ?: "Bilinmeyen"
                    val titleEnglish = node.optJSONObject("title")?.optNullableString("english")
                    val titleNative = node.optJSONObject("title")?.optNullableString("native")
                    val titleRomaji = node.optJSONObject("title")?.optNullableString("romaji")
                    val imgUrl = node.optJSONObject("coverImage")?.optNullableString("large")
                    val type = node.optNullableString("type").orEmpty().lowercase()

                    val stableId = idMal ?: (100_000_000 + mediaId)

                    mediaAppearances.add(
                        KitsugiCharacterMediaAppearance(
                            mediaId = stableId,
                            title = title,
                            imageUrl = imgUrl,
                            mediaType = type.toTurkishMediaTypeString(),
                            characterRole = role,
                            source = if (idMal != null) "jikan" else "anilist",
                            titleEnglish = titleEnglish,
                            titleJapanese = titleNative,
                            titleRomaji = titleRomaji
                        )
                    )

                    val vaArr = edge.optJSONArray("voiceActors")
                    if (vaArr != null) {
                        for (j in 0 until vaArr.length()) {
                            val va = vaArr.optJSONObject(j) ?: continue
                            val vaId = va.optInt("id")
                            val vaPersonName = va.optJSONObject("name").aniListPersonName()
                            val vaName = vaPersonName.preferred
                            val vaImg = va.optJSONObject("image")?.optNullableString("medium")
                            val vaLang = (va.optNullableString("languageV2")
                                ?: va.optNullableString("language")
                                ?: "Japanese").toTurkishLanguage()
                            voiceActors.add(
                                KitsugiVoiceActor(
                                    id = vaId,
                                    name = vaName,
                                    romanizedName = vaPersonName.romanized,
                                    nativeName = vaPersonName.native,
                                    language = vaLang,
                                    imageUrl = vaImg,
                                    source = "anilist"
                                )
                            )
                        }
                    }
                }
            }

            val isFavourite = data.optBoolean("isFavourite", false)
            KitsugiCharacterDetail(
                id = data.optInt("id"),
                name = charName,
                nativeName = nativeName,
                alternativeNames = alternativeNames,
                imageUrl = imageUrl,
                biography = biography,
                gender = gender,
                birthday = birthday,
                age = age,
                bloodType = bloodType,
                mediaAppearances = mediaAppearances,
                voiceActors = voiceActors,
                isFavourite = isFavourite,
                aniListId = data.optInt("id"),
                romanizedName = personName.romanized
            )
        }.getOrNull()
    }

    /**
     * MAL/Jikan karakter listesini çeker. Hem standart Jikan akışında hem de TMDB/Simkl
     * için görsel zincirinde (AniList'in bulamadığı yeni veya adı uyuşmayan yapımlarda)
     * kullanılır.
     */
    private suspend fun fetchJikanCharacterList(malId: Int, mediaType: MediaType): List<KitsugiCharacter> {
        val endpoint = MalJikanMediaSupport.jikanEndpoint(mediaType)
        val url = URL("https://api.jikan.moe/v4/$endpoint/$malId/characters")
        Log.d(TAG, "Jikan isteği: $url")
        return runCatching {
            KitsugiApiBase.runWithRateLimit {
                val response = KitsugiApiBase.executeGetRequestResilient(url)
                if (response == null) {
                    Log.w(TAG, "Jikan yanıt null: $url")
                    return@runWithRateLimit emptyList()
                }
                if (!response.trimStart().startsWith('{')) {
                    Log.e(TAG, "Jikan HTML/CF yanıtı (ilk 200 char): ${response.take(200)}")
                    return@runWithRateLimit emptyList()
                }
                val root = JSONObject(response)
                val data = root.optJSONArray("data")
                if (data == null) {
                    Log.w(TAG, "Jikan 'data' alanı yok. Root keys: ${root.keys().asSequence().toList()}")
                    return@runWithRateLimit emptyList()
                }
                Log.d(TAG, "Jikan karakter sayısı: ${data.length()}")
                val list = mutableListOf<KitsugiCharacter>()
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val charObj = item.optJSONObject("character") ?: continue
                    val id = charObj.optInt("mal_id")
                    val name = (charObj.optNullableString("name") ?: "Bilinmeyen").toFriendlyName()
                    val role = (item.optNullableString("role") ?: "Bilinmeyen").toTurkishCharacterRole()
                    val imageUrl = charObj.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url")

                    val vaList = mutableListOf<KitsugiVoiceActor>()
                    val vaArray = item.optJSONArray("voice_actors")
                    if (vaArray != null) {
                        for (j in 0 until vaArray.length()) {
                            val vaItem = vaArray.optJSONObject(j) ?: continue
                            val vaPerson = vaItem.optJSONObject("person") ?: continue
                            val vaId = vaPerson.optInt("mal_id")
                            val vaName = (vaPerson.optNullableString("name") ?: "Bilinmeyen").toFriendlyName()
                            val vaLang = (vaItem.optNullableString("language") ?: "Bilinmeyen").toTurkishLanguage()
                            val vaImageUrl = vaPerson.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url")
                            vaList.add(KitsugiVoiceActor(vaId, vaName, vaLang, vaImageUrl, source = "jikan"))
                        }
                    }
                    list.add(KitsugiCharacter(id, name, role, imageUrl, vaList, source = "jikan"))
                }
                list
            }
        }.getOrElse { err ->
            Log.e(TAG, "Jikan fetch exception: ${err.javaClass.simpleName}: ${err.message}", err)
            emptyList()
        }
    }

    internal data class TmdbCharacterEnrichmentDeps(
        val resolveTmdbAnimeIds: suspend (Int, MediaType, String?) -> TmdbAnimeIdCandidates,
        val resolveSimklCrossIds: suspend (Int, MediaType, Int?, Int?) -> SimklCrossIds?,
        val fetchAniListMedia: suspend (JSONObject) -> Pair<List<AniCharInfo>, Int?>,
        val searchAniListCharacter: suspend (String, Int?) -> AniCharInfo?,
        val fetchShikimoriFast: suspend (Int, MediaType) -> List<KitsugiCharacter>,
        val fetchJikanList: suspend (Int, MediaType) -> List<KitsugiCharacter>,
        val searchJikanMalIds: suspend (List<String>, MediaType, Int?) -> List<Int>,
        val fetchKitsuList: suspend (Int) -> List<KitsugiCharacter>,
        val fetchBangumiList: suspend (Int?, List<String>, Int?) -> List<KitsugiCharacter>,
        val fetchFandomPortraits: suspend (com.kitsugi.animelist.data.fandom.FandomSourceIds, Boolean, List<String>, List<String>) -> Map<String, String>,
        val searchMultiSourcePortrait: suspend (String, Boolean) -> String?
    )

    private fun defaultEnrichmentDeps(): TmdbCharacterEnrichmentDeps = TmdbCharacterEnrichmentDeps(
        resolveTmdbAnimeIds = { tmdbId, type, imdb -> KitsugiIdResolver.resolveAllTmdbAnimeIds(tmdbId, type, imdb) },
        resolveSimklCrossIds = { tmdbId, type, _, hintMal ->
            runCatching {
                val simklId = SimklApiClient().lookupSimklId(malId = hintMal, tmdbId = tmdbId, mediaType = type)
                if (simklId != null && simklId > 0) {
                    KitsugiSimklDetailClient.resolveSimklCrossIds(simklId, type, hintTmdbId = tmdbId, hintMalId = hintMal)
                } else null
            }.getOrNull()
        },
        fetchAniListMedia = { vars -> fetchAniListMediaCharacters(vars) },
        searchAniListCharacter = { name, malId -> searchAniListCharacterByName(name, malId) },
        fetchShikimoriFast = { malId, type -> fetchShikimoriRolePortraitsFast(malId, type) },
        fetchJikanList = { malId, type -> fetchJikanCharacterList(malId, type) },
        searchJikanMalIds = { titles, type, year -> searchJikanAnimeMalIdsByTitle(titles, type, year) },
        fetchKitsuList = { kitsuId -> KitsuClient.fetchKitsuCharacters(kitsuId) },
        fetchBangumiList = { bgmId, titles, year -> fetchBangumiSubjectPortraits(bgmId, titles, year) },
        fetchFandomPortraits = { ids, isMovie, mediaTitles, missingCharNames ->
            fetchFandomBatchPortraits(ids, isMovie, mediaTitles, missingCharNames)
        },
        searchMultiSourcePortrait = { charName, allowAnimeCatalogs ->
            KitsugiPersonImageAggregator.firstPortrait(
                names = listOf(charName, primaryCharacterName(charName)).distinct(),
                allowSingleTokenNames = true,
                allowAnimeCatalogs = allowAnimeCatalogs,
                budgetMs = 4_500L
            )
        }
    )

    internal fun cleanCharacterDisplayName(raw: String): String =
        raw.replace(
            Regex("\\s*\\((?:voice|uncredited|uncredited voice|seslendirme|ses|child)\\)", RegexOption.IGNORE_CASE),
            ""
        ).trim()

    internal fun isActorDuplicateImage(char: KitsugiCharacter): Boolean =
        !char.imageUrl.isNullOrBlank() &&
            char.voiceActors.any { va -> DetailCache.isSameImageResource(char.imageUrl, va.imageUrl) }

    internal fun hasMajorityVoiceCast(characters: List<KitsugiCharacter>): Boolean {
        val sample = characters.take(10)
        if (sample.isEmpty()) return false
        val voiceCount = sample.count { it.isVoiceRole }
        return (voiceCount >= 2 && voiceCount * 2 >= sample.size) ||
            (sample.size == 1 && voiceCount == 1)
    }

    private fun isEastAsianOriginOrAnime(
        detail: KitsugiMediaDetail?,
        mediaType: MediaType,
        hasAnimeCatalogIds: Boolean
    ): Boolean {
        if (hasAnimeCatalogIds) return true
        val d = detail ?: return true
        val origin = d.countryOfOrigin?.lowercase().orEmpty()
        val language = d.originalLanguage?.lowercase().orEmpty()
        if (origin.isNotEmpty() || language.isNotEmpty()) {
            val isEastAsian = origin.contains("jp") || origin.contains("japon") || origin.contains("japan") ||
                origin.contains("kr") || origin.contains("kore") || origin.contains("cn") || origin.contains("çin") ||
                origin.contains("tw") || origin.contains("hk") ||
                language.startsWith("ja") || language.startsWith("ko") || language.startsWith("zh")
            if (isEastAsian) return true
            if (!d.titleJapanese.isNullOrBlank() || PreferenceHelpers.hasCjkCharacters(d.titleNative.orEmpty())) {
                return true
            }
            return false
        }
        if (mediaType == MediaType.Anime || d.type == MediaType.Anime) return true
        return !d.titleJapanese.isNullOrBlank() ||
            PreferenceHelpers.hasCjkCharacters(d.titleNative.orEmpty())
    }

    /**
     * Kurgusal karakter detayı için çok-kaynaklı ad çözümleyici
     * (AniList → Jikan → Bangumi → Fandom / Vikipedi).
     */
    private suspend fun fetchFictionalCharacterDetailByName(
        cleanCharName: String,
        fallbackImageUrl: String?,
        mediaAppearancesHint: List<KitsugiCharacterMediaAppearance> = emptyList()
    ): KitsugiCharacterDetail? {
        val lookupName = primaryCharacterName(cleanCharName)
        val aniDetail = fetchAniListCharacterByName(
            name = lookupName,
            allowSingleTokenName = true
        )
        if (aniDetail != null && !aniDetail.biography.isNullOrBlank() && !aniDetail.imageUrl.isNullOrBlank()) {
            DetailCache.propagateCharacterPortrait(cleanCharName, aniDetail.imageUrl)
            return aniDetail.withRoleAlias(cleanCharName)
        }

        val jikanDetail = runCatching {
            if (!KitsugiPersonImageAggregator.isUnambiguousName(lookupName, allowSingleTokenLatin = true)) {
                return@runCatching null
            }
            val jikanSearch = JikanApiClient().searchMalCharacters(lookupName, page = 1)
            val firstMalId = jikanSearch
                .firstOrNull { KitsugiPersonImageAggregator.nameMatches(lookupName, it.title) }
                ?.malId
            if (firstMalId != null && firstMalId > 0) {
                fetchCharacterDetail("jikan", firstMalId, cleanCharName, fallbackImageUrl)
            } else null
        }.getOrNull()

        if (aniDetail != null || jikanDetail != null) {
            val combined = when {
                aniDetail != null && jikanDetail != null -> aniDetail.mergeWith(jikanDetail)
                else -> aniDetail ?: jikanDetail!!
            }
            val withImage = if (combined.imageUrl.isNullOrBlank() && !fallbackImageUrl.isNullOrBlank()) {
                combined.copy(imageUrl = fallbackImageUrl)
            } else combined
            DetailCache.propagateCharacterPortrait(cleanCharName, withImage.imageUrl)
            return withImage.withRoleAlias(cleanCharName)
        }

        val bangumiDetail = runCatching {
            val page = com.kitsugi.animelist.data.auth.BangumiApiClient.searchCharacters(lookupName, limit = 8)
            val matched = page.data.firstOrNull { entity ->
                KitsugiPersonImageAggregator.nameMatches(lookupName, entity.name) ||
                    (entity.nameCn.isNotBlank() && KitsugiPersonImageAggregator.nameMatches(lookupName, entity.nameCn))
            }
            if (matched != null && matched.id > 0) {
                KitsugiBangumiCreditsClient.fetchCharacterDetail(matched.id, cleanCharName, fallbackImageUrl)
            } else null
        }.getOrNull()
        if (bangumiDetail != null) {
            DetailCache.propagateCharacterPortrait(cleanCharName, bangumiDetail.imageUrl)
            return bangumiDetail.withRoleAlias(cleanCharName)
        }

        val wikiDetail = runCatching {
            fetchWikiFictionalCharacterDetailByName(
                cleanCharName = cleanCharName,
                fallbackImageUrl = fallbackImageUrl,
                mediaAppearancesHint = mediaAppearancesHint
            )
        }.getOrNull()
        if (wikiDetail != null) {
            return wikiDetail
        }

        return null
    }

    private suspend fun fetchWikiFictionalCharacterDetailByName(
        cleanCharName: String,
        fallbackImageUrl: String?,
        mediaAppearancesHint: List<KitsugiCharacterMediaAppearance> = emptyList()
    ): KitsugiCharacterDetail? {
        val lookupName = primaryCharacterName(cleanCharName)
        if (lookupName.length < 2) return null
        val roleAliases = extractSlashRoleAliases(cleanCharName, lookupName)

        val mediaTitles = mediaAppearancesHint.flatMap {
            listOfNotNull(it.titleEnglish, it.titleRomaji, it.title)
        }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        val slugCandidates = com.kitsugi.animelist.data.fandom.FandomNameVariants
            .slugCandidates(mediaTitles)
            .take(2)

        return coroutineScope {
            val enDeferred = async {
                runCatching {
                    com.kitsugi.animelist.data.fandom.FandomApi.lookupTitles(
                        wikiId = "wikipedia.en",
                        titles = listOf(lookupName),
                        withTurkishLink = true
                    )[lookupName]?.takeIf { it.exists && !it.isDisambiguation }
                }.getOrNull()
            }
            val trDeferred = async {
                runCatching {
                    com.kitsugi.animelist.data.fandom.FandomApi.lookupTitles(
                        wikiId = "wikipedia.tr",
                        titles = listOf(lookupName),
                        withTurkishLink = false
                    )[lookupName]?.takeIf { it.exists && !it.isDisambiguation }
                }.getOrNull()
            }
            val fandomDeferred = async {
                for (slug in slugCandidates) {
                    val lookup = runCatching {
                        com.kitsugi.animelist.data.fandom.FandomApi.lookupTitles(
                            wikiId = slug,
                            titles = listOf(lookupName),
                            withTurkishLink = false
                        )[lookupName]?.takeIf { it.exists && !it.isDisambiguation }
                    }.getOrNull()
                    if (lookup != null) {
                        val infobox = com.kitsugi.animelist.data.fandom.FandomInfoboxParser.parse(lookup.infoboxJson, slug)
                        var intro = lookup.description?.takeIf { it.isNotBlank() }
                        var resolvedInfobox = infobox
                        if (intro.isNullOrBlank() || resolvedInfobox == null) {
                            val html = runCatching {
                                com.kitsugi.animelist.data.fandom.FandomApi.sectionHtml(
                                    slug,
                                    lookup.resolvedTitle ?: lookupName,
                                    0
                                )
                            }.getOrNull()
                            if (!html.isNullOrBlank()) {
                                val lead = com.kitsugi.animelist.data.fandom.FandomLeadParser.parse(html, slug)
                                if (intro.isNullOrBlank()) intro = lead.intro
                                if (resolvedInfobox == null) resolvedInfobox = lead.infobox
                            }
                        }
                        return@async Triple(slug, lookup.copy(description = intro ?: lookup.description), resolvedInfobox)
                    }
                }
                null
            }

            val enLookup = enDeferred.await()
            var trLookup = trDeferred.await()
            val fandomHit = fandomDeferred.await()

            if (trLookup == null && enLookup?.turkishLink != null) {
                val trTitle = enLookup.turkishLink.second
                if (!trTitle.equals(lookupName, ignoreCase = true)) {
                    trLookup = runCatching {
                        com.kitsugi.animelist.data.fandom.FandomApi.lookupTitles(
                            wikiId = "wikipedia.tr",
                            titles = listOf(trTitle),
                            withTurkishLink = false
                        )[trTitle]?.takeIf { it.exists && !it.isDisambiguation }
                    }.getOrNull()
                }
            }

            val fandomLookup = fandomHit?.second
            val fandomInfobox = fandomHit?.third

            if (enLookup == null && trLookup == null && fandomLookup == null) {
                return@coroutineScope null
            }

            val bestImage = fandomInfobox?.images?.firstOrNull { it.url.isNotBlank() }?.url
                ?: fandomLookup?.thumbnailUrl?.takeIf { it.isNotBlank() }
                ?: enLookup?.thumbnailUrl?.takeIf { it.isNotBlank() }
                ?: trLookup?.thumbnailUrl?.takeIf { it.isNotBlank() }
                ?: fallbackImageUrl?.takeIf { it.isNotBlank() }

            val bestBio = trLookup?.description?.cleanApiText()?.takeIf { it.isNotBlank() }
                ?: fandomLookup?.description?.cleanApiText()?.takeIf { it.isNotBlank() }
                ?: enLookup?.description?.cleanApiText()?.takeIf { it.isNotBlank() }

            if (bestImage.isNullOrBlank() && bestBio.isNullOrBlank()) {
                return@coroutineScope null
            }

            val fields = fandomInfobox?.allFields().orEmpty()
            val gender = fields.firstOrNull {
                it.label.equals("Gender", ignoreCase = true) ||
                    it.label.equals("Sex", ignoreCase = true) ||
                    it.label.equals("Cinsiyet", ignoreCase = true)
            }?.value?.toTurkishGender()
            val age = fields.firstOrNull {
                it.label.equals("Age", ignoreCase = true) ||
                    it.label.equals("Yaş", ignoreCase = true)
            }?.value
            val birthday = fields.firstOrNull {
                it.label.equals("Born", ignoreCase = true) ||
                    it.label.equals("Birthday", ignoreCase = true) ||
                    it.label.equals("Date of birth", ignoreCase = true)
            }?.value

            if (!bestImage.isNullOrBlank()) {
                DetailCache.propagateCharacterPortrait(cleanCharName, bestImage)
                DetailCache.propagateCharacterPortrait(lookupName, bestImage)
            }

            KitsugiCharacterDetail(
                id = 0,
                name = lookupName,
                nativeName = null,
                alternativeNames = roleAliases,
                imageUrl = bestImage,
                gender = gender,
                age = age,
                birthday = birthday,
                bloodType = null,
                biography = bestBio ?: "Bu kurgusal karakter için ek biyografi bilgisi bulunmuyor.",
                voiceActors = emptyList(),
                mediaAppearances = mediaAppearancesHint,
                source = "tmdb"
            ).withRoleAlias(cleanCharName)
        }
    }

    internal suspend fun enrichTmdbCastCharacters(
        tmdbChars: List<KitsugiCharacter>,
        effectiveTmdbId: Int,
        mediaType: MediaType,
        mediaDetail: KitsugiMediaDetail?,
        titleCandidates: List<String>,
        realMalId: Int? = null,
        hintAniListId: Int? = null,
        hintKitsuId: Int? = null,
        hintImdbId: String? = null,
        deps: TmdbCharacterEnrichmentDeps = defaultEnrichmentDeps()
    ): List<KitsugiCharacter> {
        if (tmdbChars.isEmpty()) return tmdbChars

        val armCandidates = runCatching {
            withTimeoutOrNull(CROSS_SOURCE_CHAR_IMAGE_BUDGET_MS) {
                deps.resolveTmdbAnimeIds(effectiveTmdbId, mediaType, hintImdbId)
            }
        }.getOrNull() ?: TmdbAnimeIdCandidates()

        val malIds = LinkedHashSet<Int>()
        realMalId?.takeIf { it in 1..99_999_999 }?.let { malIds += it }
        mediaDetail?.realMalId?.takeIf { it in 1..99_999_999 }?.let { malIds += it }
        armCandidates.malIds.filter { it in 1..99_999_999 }.forEach { malIds += it }

        val aniListIds = LinkedHashSet<Int>()
        hintAniListId?.takeIf { it > 0 }?.let { aniListIds += it }
        armCandidates.aniListIds.filter { it > 0 }.forEach { aniListIds += it }

        val kitsuIds = LinkedHashSet<Int>()
        hintKitsuId?.takeIf { it > 0 }?.let { kitsuIds += it }
        armCandidates.kitsuIds.filter { it > 0 }.forEach { kitsuIds += it }

        var resolvedBangumiId = armCandidates.bangumiId
        var resolvedImdbId = hintImdbId ?: armCandidates.imdbId

        val initialAnimatedSignal = looksAnimatedOrAnime(mediaDetail, mediaType) ||
            hasMajorityVoiceCast(tmdbChars) ||
            malIds.isNotEmpty() || aniListIds.isNotEmpty() || kitsuIds.isNotEmpty() || resolvedBangumiId != null

        if ((initialAnimatedSignal || mediaDetail == null) && malIds.isEmpty() && aniListIds.isEmpty()) {
            val simklCross = runCatching {
                withTimeoutOrNull(3_000L) {
                    deps.resolveSimklCrossIds(effectiveTmdbId, mediaType, armCandidates.simklId, malIds.firstOrNull())
                }
            }.getOrNull()
            if (simklCross != null) {
                simklCross.malId?.takeIf { it in 1..99_999_999 }?.let { malIds += it }
                simklCross.aniListId?.takeIf { it > 0 }?.let { aniListIds += it }
                simklCross.kitsuId?.takeIf { it > 0 }?.let { kitsuIds += it }
                if (resolvedImdbId.isNullOrBlank()) resolvedImdbId = simklCross.imdbId
            }
        }

        val hasCatalogAnimeIds = malIds.isNotEmpty() || aniListIds.isNotEmpty() || kitsuIds.isNotEmpty()
        val isAnimatedWork = looksAnimatedOrAnime(mediaDetail, mediaType) ||
            hasMajorityVoiceCast(tmdbChars) ||
            hasCatalogAnimeIds ||
            resolvedBangumiId != null

        if (!isAnimatedWork) {
            val hasHybridVoiceRoles = tmdbChars.any { it.isVoiceRole }
            if (!hasHybridVoiceRoles) {
                return tmdbChars.map { it.copy(name = cleanCharacterDisplayName(it.name), isRealMediaRole = true) }
            }
            val voiceIndices = tmdbChars.indices.filter { idx -> tmdbChars[idx].isVoiceRole }
            val voiceSlice = voiceIndices.map { idx ->
                val ch = tmdbChars[idx]
                ch.copy(name = cleanCharacterDisplayName(ch.name), imageUrl = null, isRealMediaRole = false)
            }
            val enrichedVoice = enrichFictionalCharacterSlice(
                initial = voiceSlice,
                effectiveTmdbId = effectiveTmdbId,
                mediaType = mediaType,
                mediaDetail = mediaDetail,
                titleCandidates = titleCandidates,
                malIds = malIds,
                aniListIds = aniListIds,
                kitsuIds = kitsuIds,
                bangumiId = resolvedBangumiId,
                imdbId = resolvedImdbId,
                wikidataId = armCandidates.wikidataId,
                allowAnimeCatalogs = false,
                deps = deps
            )
            val byVoicePos = voiceIndices.withIndex().associate { (pos, origIdx) -> origIdx to enrichedVoice[pos] }
            return tmdbChars.mapIndexed { idx, original ->
                byVoicePos[idx] ?: original.copy(name = cleanCharacterDisplayName(original.name), isRealMediaRole = true)
            }
        }

        val prepared = tmdbChars.map { ch ->
            val cleanedName = cleanCharacterDisplayName(ch.name)
            val cachedPortrait = DetailCache.getCachedCharacterPortrait(cleanedName)
            val cleanedImg = when {
                !cachedPortrait.isNullOrBlank() -> cachedPortrait
                isActorDuplicateImage(ch) -> null
                else -> ch.imageUrl
            }
            ch.copy(name = cleanedName, imageUrl = cleanedImg, isRealMediaRole = false)
        }

        val allowAnimeCatalogs = isEastAsianOriginOrAnime(mediaDetail, mediaType, hasCatalogAnimeIds)
        return enrichFictionalCharacterSlice(
            initial = prepared,
            effectiveTmdbId = effectiveTmdbId,
            mediaType = mediaType,
            mediaDetail = mediaDetail,
            titleCandidates = titleCandidates,
            malIds = malIds,
            aniListIds = aniListIds,
            kitsuIds = kitsuIds,
            bangumiId = resolvedBangumiId,
            imdbId = resolvedImdbId,
            wikidataId = armCandidates.wikidataId,
            allowAnimeCatalogs = allowAnimeCatalogs,
            deps = deps
        )
    }

    private suspend fun enrichFictionalCharacterSlice(
        initial: List<KitsugiCharacter>,
        effectiveTmdbId: Int,
        mediaType: MediaType,
        mediaDetail: KitsugiMediaDetail?,
        titleCandidates: List<String>,
        malIds: MutableSet<Int>,
        aniListIds: MutableSet<Int>,
        kitsuIds: MutableSet<Int>,
        bangumiId: Int?,
        imdbId: String?,
        wikidataId: String?,
        allowAnimeCatalogs: Boolean,
        deps: TmdbCharacterEnrichmentDeps
    ): List<KitsugiCharacter> {
        var current = initial
        if (current.none { it.imageUrl.isNullOrBlank() }) return current

        if (allowAnimeCatalogs) {
            val aniEnriched = enrichCharactersWithAnimeImages(
                characters = current,
                titleCandidates = titleCandidates,
                realMalId = malIds.firstOrNull(),
                aniListIds = aniListIds.toList(),
                deps = deps
            )
            current = aniEnriched.characters
            aniEnriched.resolvedMalId?.takeIf { it in 1..99_999_999 }?.let { malIds += it }
        }

        if (allowAnimeCatalogs && malIds.isEmpty() && current.any { it.imageUrl.isNullOrBlank() }) {
            val searchedMalIds = runCatching {
                withTimeoutOrNull(4_500L) {
                    deps.searchJikanMalIds(titleCandidates, mediaType, mediaDetail?.year)
                }
            }.getOrNull().orEmpty()
            malIds.addAll(searchedMalIds)
        }

        if (current.any { it.imageUrl.isNullOrBlank() }) {
            current = enrichFromCrossSourceCharacterListsParallel(
                characters = current,
                mediaType = mediaType,
                malIds = malIds.take(3),
                kitsuIds = kitsuIds.take(2),
                bangumiId = bangumiId,
                titleCandidates = titleCandidates,
                expectedYear = mediaDetail?.year,
                allowAnimeCatalogs = allowAnimeCatalogs,
                deps = deps
            )
        }

        if (current.any { it.imageUrl.isNullOrBlank() }) {
            val missingNames = current.filter { it.imageUrl.isNullOrBlank() && it.name.length >= 2 }
                .map { it.name }
                .distinct()
            if (missingNames.isNotEmpty()) {
                val sourceIds = com.kitsugi.animelist.data.fandom.FandomSourceIds(
                    aniListId = aniListIds.firstOrNull(),
                    malId = malIds.firstOrNull(),
                    kitsuId = kitsuIds.firstOrNull(),
                    bangumiId = bangumiId,
                    tmdbId = effectiveTmdbId.takeIf { it > 0 },
                    wikidataId = wikidataId,
                    imdbId = imdbId
                )
                val fandomMap = runCatching {
                    withTimeoutOrNull(8_000L) {
                        deps.fetchFandomPortraits(
                            sourceIds,
                            mediaType == MediaType.Movie,
                            titleCandidates,
                            missingNames
                        )
                    }
                }.getOrNull().orEmpty()
                if (fandomMap.isNotEmpty()) {
                    current = current.map { ch ->
                        if (!ch.imageUrl.isNullOrBlank()) ch
                        else {
                            val hit = fandomMap[ch.name] ?: fandomMap[primaryCharacterName(ch.name)]
                            if (!hit.isNullOrBlank()) ch.copy(imageUrl = hit) else ch
                        }
                    }
                }
            }
        }

        if (current.any { it.imageUrl.isNullOrBlank() }) {
            current = enrichMissingImagesFromMultiSourceSearch(
                characters = current,
                realMalId = malIds.firstOrNull(),
                allowAnimeCatalogs = allowAnimeCatalogs,
                deps = deps
            )
        }

        current.forEach { ch ->
            if (!ch.imageUrl.isNullOrBlank() && !isActorDuplicateImage(ch)) {
                DetailCache.resolvedCharacterPortraits[normalizeCharacterMatchNorm(ch.name)] = ch.imageUrl
                val primaryKey = normalizeCharacterMatchNorm(primaryCharacterName(ch.name))
                if (primaryKey.length >= 2) {
                    DetailCache.resolvedCharacterPortraits.putIfAbsent(primaryKey, ch.imageUrl)
                }
            }
        }
        return current
    }

    private suspend fun enrichFromCrossSourceCharacterListsParallel(
        characters: List<KitsugiCharacter>,
        mediaType: MediaType,
        malIds: List<Int>,
        kitsuIds: List<Int>,
        bangumiId: Int?,
        titleCandidates: List<String>,
        expectedYear: Int?,
        allowAnimeCatalogs: Boolean,
        deps: TmdbCharacterEnrichmentDeps = defaultEnrichmentDeps()
    ): List<KitsugiCharacter> {
        if (characters.none { it.imageUrl.isNullOrBlank() }) return characters

        val referenceBatches = coroutineScope {
            val shikiJob = async {
                if (!allowAnimeCatalogs || malIds.isEmpty()) emptyList()
                else withTimeoutOrNull(CROSS_SOURCE_CHAR_IMAGE_BUDGET_MS) {
                    malIds.flatMap { id -> runCatching { deps.fetchShikimoriFast(id, mediaType) }.getOrNull().orEmpty() }
                }.orEmpty()
            }
            val jikanJob = async {
                if (!allowAnimeCatalogs || malIds.isEmpty()) emptyList()
                else withTimeoutOrNull(7_000L) {
                    malIds.flatMap { id -> runCatching { deps.fetchJikanList(id, mediaType) }.getOrNull().orEmpty() }
                }.orEmpty()
            }
            val kitsuJob = async {
                if (!allowAnimeCatalogs) emptyList()
                else withTimeoutOrNull(CROSS_SOURCE_CHAR_IMAGE_BUDGET_MS) {
                    val effectiveKitsuIds = if (kitsuIds.isNotEmpty()) {
                        kitsuIds
                    } else if (malIds.isNotEmpty()) {
                        listOfNotNull(
                            runCatching {
                                KitsugiIdResolver.resolveIds(malId = malIds.first(), aniListId = null, mediaType = mediaType).kitsuId
                            }.getOrNull()
                        )
                    } else {
                        listOfNotNull(searchKitsuAnimeIdByTitle(titleCandidates))
                    }
                    effectiveKitsuIds.flatMap { id -> runCatching { deps.fetchKitsuList(id) }.getOrNull().orEmpty() }
                }.orEmpty()
            }
            val bangumiJob = async {
                if (!allowAnimeCatalogs && bangumiId == null) emptyList()
                else withTimeoutOrNull(6_000L) {
                    runCatching { deps.fetchBangumiList(bangumiId, titleCandidates, expectedYear) }.getOrNull().orEmpty()
                }.orEmpty()
            }
            listOf(jikanJob.await(), shikiJob.await(), kitsuJob.await(), bangumiJob.await())
        }

        var current = characters
        for (batch in referenceBatches) {
            if (batch.isNotEmpty() && current.any { it.imageUrl.isNullOrBlank() }) {
                current = mergeImagesFromReferenceCharacters(current, batch)
            }
        }
        return current
    }

    private suspend fun fetchShikimoriRolePortraitsFast(
        malId: Int,
        mediaType: MediaType
    ): List<KitsugiCharacter> {
        if (malId <= 0) return emptyList()
        val endpoint = if (mediaType == MediaType.Manga) "mangas" else "animes"
        val url = URL("https://shikimori.io/api/$endpoint/$malId/roles")
        return runCatching {
            val response = KitsugiApiBase.runWithRateLimit {
                KitsugiApiBase.executeGetRequestResilient(url)
            } ?: return@runCatching emptyList()
            val array = org.json.JSONArray(response)
            val out = ArrayList<KitsugiCharacter>(array.length())
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val charObj = item.optJSONObject("character") ?: continue
                val charId = charObj.optInt("id", 0)
                if (charId <= 0) continue
                val rawName = charObj.optString("name", "").trim()
                if (rawName.isEmpty()) continue
                val jpName = charObj.optString("japanese", "").trim().takeIf { it.isNotEmpty() }
                val imgObj = charObj.optJSONObject("image")
                val relImg = imgObj?.optString("original")?.takeIf { it.isNotBlank() && !it.contains("missing") }
                    ?: imgObj?.optString("preview")?.takeIf { it.isNotBlank() && !it.contains("missing") }
                val imageUrl = ShikimoriPosterResolver.absoluteUrl(relImg)
                out += KitsugiCharacter(
                    id = charId,
                    name = rawName,
                    role = "Supporting",
                    imageUrl = imageUrl,
                    source = "shikimori",
                    nativeName = jpName
                )
            }
            if (out.any { it.imageUrl.isNullOrBlank() }) {
                KitsugiShikimoriClient.fetchCharacters(mediaType, malId)
            } else {
                out
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun searchJikanAnimeMalIdsByTitle(
        titleCandidates: List<String>,
        mediaType: MediaType,
        expectedYear: Int?
    ): List<Int> {
        val cleanQueries = titleCandidates
            .map { it.replace(Regex("\\s*\\(.*?\\)"), "").trim() }
            .filter { it.length >= 2 }
            .distinct()
            .take(3)
        if (cleanQueries.isEmpty()) return emptyList()

        fun normTitle(s: String?): String =
            s?.lowercase()?.replace(Regex("[^\\p{L}\\p{N}]"), "").orEmpty()

        val targetNorms = cleanQueries.map(::normTitle).filter { it.isNotEmpty() }.toSet()
        for (query in cleanQueries) {
            val results = runCatching {
                JikanSearchClient().searchMALOnly(query = query, mediaType = MediaType.Anime)
            }.getOrNull().orEmpty()
            val matched = results.filter { item ->
                val itemNorms = listOfNotNull(item.title, item.titleEnglish, item.titleJapanese)
                    .map(::normTitle)
                    .filter { it.isNotEmpty() }
                val titleMatches = itemNorms.any { it in targetNorms }
                val yearMatches = expectedYear == null || item.year == null || kotlin.math.abs(expectedYear - item.year) <= 1
                titleMatches && yearMatches
            }.mapNotNull { (it.realMalId ?: it.malId).takeIf { id -> id in 1..99_999_999 } }
                .distinct()
            if (matched.isNotEmpty()) return matched.take(3)
        }
        return emptyList()
    }

    private suspend fun searchKitsuAnimeIdByTitle(titleCandidates: List<String>): Int? {
        for (query in titleCandidates.map { it.trim() }.filter { it.length >= 2 }.distinct().take(2)) {
            val detail = runCatching { KitsuClient.fetchAnimeDetailByTitle(query) }.getOrNull() ?: continue
            val linkId = detail.externalLinks
                .firstOrNull { it.site.equals("Kitsu", ignoreCase = true) || it.url.contains("kitsu.io/anime/") }
                ?.url?.substringAfter("kitsu.io/anime/")?.substringBefore("/")?.trim()?.toIntOrNull()
            val rawId = linkId ?: detail.realMalId?.takeIf { it > 0 }?.let { id -> if (id >= 300_000_000) id - 300_000_000 else id }
            if (rawId != null && rawId > 0) return rawId
        }
        return null
    }

    private suspend fun fetchBangumiSubjectPortraits(
        knownBangumiId: Int?,
        titleCandidates: List<String>,
        expectedYear: Int?
    ): List<KitsugiCharacter> {
        var subjectId = knownBangumiId?.takeIf { it > 0 }
        if (subjectId == null) {
            val ctx = KitsugiApplication.getInstance()?.applicationContext
            if (ctx != null) {
                for (candidate in titleCandidates.map { it.trim() }.filter { it.length >= 2 }.distinct().take(3)) {
                    val resolved = runCatching {
                        BangumiIdNamespace.resolveBangumiId(
                            context = ctx,
                            storedId = null,
                            title = candidate,
                            expectedYear = expectedYear,
                            isAnime = true,
                            subjectType = com.kitsugi.animelist.data.auth.BangumiApiClient.SubjectType.ANIME
                        )
                    }.getOrNull()?.takeIf { it > 0 }
                    if (resolved != null) {
                        subjectId = resolved
                        break
                    }
                }
            }
            if (subjectId == null) {
                for (candidate in titleCandidates.map { it.trim() }.filter { it.length >= 2 }.distinct().take(2)) {
                    val page = runCatching {
                        com.kitsugi.animelist.data.auth.BangumiApiClient.searchAnime(candidate, limit = 5)
                    }.getOrNull() ?: continue
                    val normTarget = candidate.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")
                    val hit = page.data.firstOrNull { sub ->
                        val n1 = sub.name.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")
                        val n2 = sub.nameCn.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")
                        val yearOk = expectedYear == null || sub.date?.take(4)?.toIntOrNull()?.let { y ->
                            kotlin.math.abs(y - expectedYear) <= 1
                        } ?: true
                        yearOk && normTarget.isNotEmpty() && (n1 == normTarget || n2 == normTarget)
                    }
                    if (hit != null && hit.id > 0) {
                        subjectId = hit.id
                        break
                    }
                }
            }
        }
        return if (subjectId != null && subjectId > 0) {
            KitsugiBangumiCreditsClient.fetchSubjectCharacters(subjectId)
        } else emptyList()
    }

    private suspend fun fetchFandomBatchPortraits(
        sourceIds: com.kitsugi.animelist.data.fandom.FandomSourceIds,
        isMovie: Boolean,
        mediaTitles: List<String>,
        missingCharNames: List<String>
    ): Map<String, String> {
        val cleanLookupMap = LinkedHashMap<String, String>()
        val secondaryLookupMap = LinkedHashMap<String, String>()
        for (orig in missingCharNames.take(35)) {
            val segments = orig.split("/")
                .map { cleanCharacterDisplayName(it).trim() }
                .filter { it.length >= 2 }
            val primary = segments.firstOrNull() ?: primaryCharacterName(orig)
            if (primary.isNotBlank()) {
                cleanLookupMap.putIfAbsent(primary, orig)
            }
            if (!orig.contains("/")) {
                cleanLookupMap.putIfAbsent(orig, orig)
            }
            segments.drop(1).take(2).forEach { sec ->
                if (sec.isNotBlank()) secondaryLookupMap.putIfAbsent(sec, orig)
            }
        }
        if (cleanLookupMap.isEmpty()) return emptyMap()

        val candidateWikis = LinkedHashSet<String>()
        val ctx = KitsugiApplication.getInstance()?.applicationContext
        if (ctx != null) {
            val query = com.kitsugi.animelist.data.fandom.FandomMediaQuery(
                ids = sourceIds,
                isManga = false,
                isMovie = isMovie,
                fallbackTitles = mediaTitles,
                isTvShow = !isMovie
            )
            val wikiRef = withTimeoutOrNull(3_000L) {
                com.kitsugi.animelist.data.fandom.FandomRepository.resolveMediaWiki(
                    context = ctx,
                    query = query,
                    allowNameFallback = true
                )
            }
            if (wikiRef != null && !com.kitsugi.animelist.data.fandom.FandomUrls.isWikipedia(wikiRef.wikiId)) {
                candidateWikis += wikiRef.wikiId
            }
            com.kitsugi.animelist.data.fandom.FandomNameVariants.slugCandidates(mediaTitles)
                .take(2)
                .forEach { candidateWikis += it }
            if (wikiRef != null && com.kitsugi.animelist.data.fandom.FandomUrls.isWikipedia(wikiRef.wikiId)) {
                candidateWikis += wikiRef.wikiId
            }
        } else {
            com.kitsugi.animelist.data.fandom.FandomNameVariants.slugCandidates(mediaTitles)
                .take(2)
                .forEach { candidateWikis += it }
        }
        candidateWikis += "wikipedia.en"
        candidateWikis += "wikipedia.tr"

        val resolved = LinkedHashMap<String, String>()
        val targetCount = missingCharNames.take(35).distinct().size
        for (wikiId in candidateWikis) {
            val remainingPrimary = cleanLookupMap.filterValues { it !in resolved }
            if (remainingPrimary.isEmpty()) break
            val batchTitles = (remainingPrimary.keys + secondaryLookupMap.filterValues { it !in resolved }.keys)
                .distinct()
                .take(50)
            if (batchTitles.isEmpty()) continue
            val lookups = runCatching {
                com.kitsugi.animelist.data.fandom.FandomApi.lookupTitles(
                    wikiId = wikiId,
                    titles = batchTitles
                )
            }.getOrNull().orEmpty()
            for ((lookupKey, lookup) in lookups) {
                if (!lookup.exists || lookup.isDisambiguation) continue
                val origName = cleanLookupMap[lookupKey] ?: secondaryLookupMap[lookupKey] ?: continue
                if (origName in resolved) continue
                val img = com.kitsugi.animelist.data.fandom.FandomInfoboxParser.parse(lookup.infoboxJson, wikiId)
                    ?.images
                    ?.firstOrNull { it.url.isNotBlank() }
                    ?.url
                    ?: lookup.thumbnailUrl?.takeIf { it.isNotBlank() }
                if (!img.isNullOrBlank()) {
                    resolved[origName] = img
                }
            }
            if (resolved.size >= targetCount) break
        }
        return resolved
    }

    private fun normalizeCharacterMatchNorm(s: String): String =
        cleanCharacterDisplayName(s)
            .lowercase()
            .replace("ou", "o").replace("oo", "o").replace("oh", "o").replace("uu", "u")
            .replace(Regex("[^\\p{L}\\p{N}]"), "")

    internal fun mergeImagesFromReferenceCharacters(
        characters: List<KitsugiCharacter>,
        reference: List<KitsugiCharacter>
    ): List<KitsugiCharacter> {
        val withImages = reference.filter { !it.imageUrl.isNullOrBlank() }
        if (withImages.isEmpty()) return characters

        fun getTokens(s: String): List<String> =
            cleanCharacterDisplayName(s)
                .lowercase()
                .replace("'", "")
                .replace("’", "")
                .split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.length >= 2 }

        fun candidateNamesOf(ref: KitsugiCharacter): List<String> =
            listOfNotNull(ref.name, ref.englishName, ref.romanizedName, ref.nativeName)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()

        return characters.map { char ->
            if (!char.imageUrl.isNullOrBlank() && !isActorDuplicateImage(char)) return@map char
            val lookupVariants = listOf(char.name, primaryCharacterName(char.name))
                .map { cleanCharacterDisplayName(it) }
                .filter { it.isNotBlank() }
                .distinct()
            val primaryTarget = lookupVariants.firstOrNull() ?: return@map char
            val targetNorm = normalizeCharacterMatchNorm(primaryTarget)
            if (targetNorm.length < 2) return@map char
            val targetTokens = getTokens(primaryTarget)
            val targetMods = targetTokens.filter { it in ANI_CHAR_NAME_MODIFIERS }.toSet()

            val match = withImages.firstOrNull { ref ->
                candidateNamesOf(ref).any { cand ->
                    val candMods = getTokens(cand).filter { it in ANI_CHAR_NAME_MODIFIERS }.toSet()
                    candMods == targetMods && lookupVariants.any { v ->
                        normalizeCharacterMatchNorm(cand) == normalizeCharacterMatchNorm(v)
                    }
                }
            } ?: withImages.firstOrNull { ref ->
                candidateNamesOf(ref).any { cand ->
                    val refTokens = getTokens(cand)
                    val candMods = refTokens.filter { it in ANI_CHAR_NAME_MODIFIERS }.toSet()
                    candMods == targetMods &&
                        refTokens.isNotEmpty() && targetTokens.isNotEmpty() &&
                        refTokens.toSet() == targetTokens.toSet()
                }
            } ?: withImages.firstOrNull { ref ->
                candidateNamesOf(ref).any { cand ->
                    val refTokens = getTokens(cand)
                    val candMods = refTokens.filter { it in ANI_CHAR_NAME_MODIFIERS }.toSet()
                    if (candMods != targetMods || refTokens.isEmpty() || targetTokens.isEmpty()) {
                        return@any false
                    }
                    val firstShared = (refTokens.first() in targetTokens) || (targetTokens.first() in refTokens)
                    if (!firstShared) return@any false
                    val intersection = refTokens.toSet().intersect(targetTokens.toSet())
                    val union = refTokens.toSet().union(targetTokens.toSet())
                    union.isNotEmpty() && intersection.size.toDouble() / union.size.toDouble() >= 0.5
                }
            }

            if (match != null) {
                char.copy(
                    imageUrl = match.imageUrl,
                    voiceActors = if (char.voiceActors.isEmpty()) match.voiceActors else char.voiceActors
                )
            } else {
                char
            }
        }
    }

    private data class AniListEnrichResult(
        val characters: List<KitsugiCharacter>,
        val resolvedMalId: Int?
    )

    private suspend fun enrichCharactersWithAnimeImages(
        characters: List<KitsugiCharacter>,
        titleCandidates: List<String>,
        realMalId: Int?,
        aniListIds: List<Int> = emptyList(),
        deps: TmdbCharacterEnrichmentDeps = defaultEnrichmentDeps()
    ): AniListEnrichResult {
        if (characters.isEmpty()) return AniListEnrichResult(characters, realMalId?.takeIf { it > 0 })
        val cleanTitles = titleCandidates
            .map { it.replace(Regex("\\s*\\(.*?\\)"), "").trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_ANILIST_TITLE_LOOKUPS)
        if (cleanTitles.isEmpty() && (realMalId == null || realMalId <= 0) && aniListIds.isEmpty()) {
            return AniListEnrichResult(characters, null)
        }

        return runCatching {
            val lookups = buildList {
                aniListIds.filter { it > 0 }.forEach { add(JSONObject().put("id", it)) }
                if (realMalId != null && realMalId > 0) add(JSONObject().put("idMal", realMalId))
                cleanTitles.forEach { add(JSONObject().put("search", it)) }
            }

            var firstNonEmpty: List<AniCharInfo> = emptyList()
            var mediaMalId: Int? = realMalId?.takeIf { it > 0 }
            val mergedAniChars = ArrayList<AniCharInfo>()
            for (variables in lookups) {
                val (list, idMal) = runCatching { deps.fetchAniListMedia(variables) }
                    .getOrNull() ?: Pair<List<AniCharInfo>, Int?>(emptyList(), null)
                if (list.isEmpty()) continue
                if (firstNonEmpty.isEmpty()) firstNonEmpty = list
                if (characters.any { char -> matchAniListCharacter(char.name, list) != null }) {
                    mergedAniChars += list
                    if (mediaMalId == null && idMal != null && idMal > 0) mediaMalId = idMal
                    if (variables.has("search")) break
                }
                if (mediaMalId == null && idMal != null && idMal > 0) mediaMalId = idMal
            }
            val aniChars = if (mergedAniChars.isNotEmpty()) mergedAniChars else firstNonEmpty

            val fromAnime = characters.map { char ->
                val cleanName = cleanCharacterDisplayName(char.name)
                val isActorImage = isActorDuplicateImage(char)
                if (!char.imageUrl.isNullOrBlank() && !isActorImage) {
                    return@map char.copy(name = cleanName)
                }

                val matched = matchAniListCharacter(cleanName, aniChars)
                    ?: matchAniListCharacter(primaryCharacterName(cleanName), aniChars)
                if (matched != null) {
                    char.copy(
                        id = if (matched.id > 0) matched.id else char.id,
                        name = cleanName,
                        imageUrl = matched.imageUrl,
                        source = if (matched.id > 0) "anilist" else char.source
                    )
                } else {
                    char.copy(name = cleanName)
                }
            }
            AniListEnrichResult(fromAnime, mediaMalId)
        }.getOrElse { e ->
            Log.e(TAG, "AniList character image enrichment error: ${e.message}", e)
            AniListEnrichResult(characters, realMalId?.takeIf { it > 0 })
        }
    }

    private fun looksAnimatedOrAnime(detail: KitsugiMediaDetail?, mediaType: MediaType): Boolean {
        if (mediaType == MediaType.Anime) return true
        val d = detail ?: return false
        if (d.type == MediaType.Anime) return true
        val genres = d.genres
        if (genres.any { genre ->
                genre.contains("anim", ignoreCase = true) || genre.contains("anime", ignoreCase = true)
            }
        ) return true
        if (d.tags.any { tag ->
                tag.name.contains("anime", ignoreCase = true) || tag.name.contains("animasyon", ignoreCase = true)
            }
        ) return true
        val origin = d.countryOfOrigin?.lowercase().orEmpty()
        val language = d.originalLanguage?.lowercase().orEmpty()
        val eastAsian = origin.contains("jp") || origin.contains("japon") || origin.contains("japan") ||
            origin.contains("kr") || origin.contains("kore") || origin.contains("cn") || origin.contains("çin") ||
            language.startsWith("ja") || language.startsWith("ko") || language.startsWith("zh")
        return eastAsian && d.format?.contains("anima", ignoreCase = true) == true
    }

    internal fun buildTitleCandidates(title: String?, detail: KitsugiMediaDetail?): List<String> {
        val candidates = mutableListOf<String?>()
        detail?.let { d ->
            candidates.add(d.titleEnglish)
            candidates.add(d.titleRomaji)
            candidates.add(d.titleJapanese)
            candidates.add(d.titleNative)
            candidates.add(d.title)
        }
        candidates.add(title)
        detail?.synonyms?.forEach { candidates.add(it) }
        return candidates
            .mapNotNull { it?.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private suspend fun fetchAniListMediaCharacters(variables: JSONObject): Pair<List<AniCharInfo>, Int?> {
        val query = """
            query (${'$'}id: Int, ${'$'}idMal: Int, ${'$'}search: String) {
                Media(id: ${'$'}id, idMal: ${'$'}idMal, search: ${'$'}search, type: ANIME) {
                    id
                    idMal
                    characters(perPage: 50, sort: [ROLE, RELEVANCE]) {
                        edges {
                            role
                            node {
                                id
                                name {
                                    full
                                    native
                                    alternative
                                }
                                image {
                                    large
                                    medium
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return Pair(emptyList(), null)
        val media = runCatching {
            JSONObject(response).optJSONObject("data")?.optJSONObject("Media")
        }.getOrNull()
        val mediaMalId = media?.optInt("idMal", 0)?.takeIf { it > 0 }
        val edges = runCatching {
            media?.optJSONObject("characters")?.optJSONArray("edges")
        }.getOrNull() ?: return Pair(emptyList(), mediaMalId)

        val result = mutableListOf<AniCharInfo>()
        for (i in 0 until edges.length()) {
            val edge = edges.optJSONObject(i) ?: continue
            val node = edge.optJSONObject("node") ?: continue
            val nameObj = node.optJSONObject("name") ?: continue
            val full = nameObj.optString("full", "")
            val native = nameObj.optNullableString("native")
            val altArray = nameObj.optJSONArray("alternative")
            val alternatives = mutableListOf<String>()
            if (altArray != null) {
                for (j in 0 until altArray.length()) {
                    val alt = altArray.optString(j)
                    if (alt.isNotBlank()) alternatives.add(alt)
                }
            }
            val imageObj = node.optJSONObject("image")
            val img = imageObj?.optNullableString("large")
                ?: imageObj?.optNullableString("medium")
            val id = node.optInt("id", 0)
            if (full.isNotBlank() && !img.isNullOrBlank()) {
                result.add(AniCharInfo(id, full, native, alternatives, img))
            }
        }
        return Pair(result, mediaMalId)
    }

    internal fun matchAniListCharacter(
        rawName: String,
        aniChars: List<AniCharInfo>
    ): AniCharInfo? {
        if (aniChars.isEmpty()) return null

        fun norm(s: String) = normalizeCharacterMatchNorm(s)

        fun getTokens(s: String): List<String> = cleanCharacterDisplayName(s)
            .lowercase()
            .replace("'", "")
            .replace("’", "")
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotBlank() }

        val cleanName = cleanCharacterDisplayName(rawName)
        val targetNorm = norm(cleanName)
        if (targetNorm.length < 2) return null

        val targetTokens = getTokens(cleanName)
        val targetMods = targetTokens.filter { it in ANI_CHAR_NAME_MODIFIERS }.toSet()

        aniChars.firstOrNull { ani ->
            val aniMods = getTokens(ani.full).filter { it in ANI_CHAR_NAME_MODIFIERS }.toSet()
            if (targetMods != aniMods) return@firstOrNull false
            norm(ani.full) == targetNorm ||
                (ani.native != null && norm(ani.native) == targetNorm) ||
                ani.alternatives.any { norm(it) == targetNorm }
        }?.let { return it }

        aniChars.firstOrNull { ani ->
            val aniTokens = getTokens(ani.full)
            val aniMods = aniTokens.filter { it in ANI_CHAR_NAME_MODIFIERS }.toSet()
            if (targetMods != aniMods) return@firstOrNull false
            aniTokens.isNotEmpty() && aniTokens.toSet() == targetTokens.toSet()
        }?.let { return it }

        aniChars.firstOrNull { ani ->
            val aniTokens = getTokens(ani.full)
            val aniMods = aniTokens.filter { it in ANI_CHAR_NAME_MODIFIERS }.toSet()
            if (targetMods != aniMods) return@firstOrNull false
            if (aniTokens.isEmpty() || targetTokens.isEmpty()) return@firstOrNull false
            val sharesGivenName = (aniTokens.first() in targetTokens) || (targetTokens.first() in aniTokens)
            if (!sharesGivenName) return@firstOrNull false
            val intersection = aniTokens.toSet().intersect(targetTokens.toSet())
            val union = aniTokens.toSet().union(targetTokens.toSet())
            if (union.isEmpty()) return@firstOrNull false
            intersection.size.toDouble() / union.size.toDouble() >= 0.5
        }?.let { return it }

        return aniChars.firstOrNull { ani ->
            val aniTokens = getTokens(ani.full)
            val aniMods = aniTokens.filter { it in ANI_CHAR_NAME_MODIFIERS }.toSet()
            if (targetMods != aniMods) return@firstOrNull false
            ani.alternatives.any { alt ->
                norm(alt) == targetNorm || getTokens(alt).toSet() == targetTokens.toSet()
            }
        }
    }

    private suspend fun enrichMissingImagesFromMultiSourceSearch(
        characters: List<KitsugiCharacter>,
        realMalId: Int?,
        allowAnimeCatalogs: Boolean,
        deps: TmdbCharacterEnrichmentDeps = defaultEnrichmentDeps()
    ): List<KitsugiCharacter> {
        val targetIndices = characters.indices
            .filter { idx ->
                val ch = characters[idx]
                ch.imageUrl.isNullOrBlank() && ch.name.trim().length >= 3
            }
            .take(16)
        if (targetIndices.isEmpty()) return characters

        val resolvedByIndex = java.util.concurrent.ConcurrentHashMap<Int, KitsugiCharacter>()
        val concurrencyGate = kotlinx.coroutines.sync.Semaphore(4)
        runCatching {
            withTimeoutOrNull(ANILIST_CHAR_IMAGE_FALLBACK_BUDGET_MS) {
                coroutineScope {
                    val jobs = targetIndices.map { idx ->
                        async {
                            concurrencyGate.acquire()
                            try {
                                val char = characters[idx]
                                val aniInfo = if (allowAnimeCatalogs) {
                                    runCatching { deps.searchAniListCharacter(char.name, realMalId) }.getOrNull()
                                } else null
                                if (aniInfo != null && !aniInfo.imageUrl.isNullOrBlank()) {
                                    resolvedByIndex[idx] = char.copy(
                                        id = if (aniInfo.id > 0) aniInfo.id else char.id,
                                        imageUrl = aniInfo.imageUrl,
                                        source = if (aniInfo.id > 0) "anilist" else char.source
                                    )
                                    return@async
                                }
                                val fallbackUrl = runCatching {
                                    deps.searchMultiSourcePortrait(char.name, allowAnimeCatalogs)
                                }.getOrNull()
                                if (!fallbackUrl.isNullOrBlank()) {
                                    resolvedByIndex[idx] = char.copy(imageUrl = fallbackUrl)
                                }
                            } finally {
                                concurrencyGate.release()
                            }
                        }
                    }
                    jobs.forEach { runCatching { it.await() } }
                }
            }
        }
        if (resolvedByIndex.isEmpty()) return characters
        return characters.mapIndexed { idx, ch -> resolvedByIndex[idx] ?: ch }
    }

    internal suspend fun searchAniListCharacterByName(name: String, realMalId: Int?): AniCharInfo? {
        val query = """
            query (${'$'}search: String) {
                Page(perPage: 6) {
                    characters(search: ${'$'}search, sort: [FAVOURITES_DESC, RELEVANCE]) {
                        id
                        name {
                            full
                            native
                            alternative
                        }
                        image {
                            large
                            medium
                        }
                        media(perPage: 25, sort: [POPULARITY_DESC]) {
                            nodes {
                                id
                                idMal
                                type
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        val variables = JSONObject().put("search", name.trim())
        val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return null
        return parseBestAniListCharacterSearchHit(response, name, realMalId)
    }

    internal fun parseBestAniListCharacterSearchHit(
        response: String,
        name: String,
        realMalId: Int?
    ): AniCharInfo? {
        val nodes = runCatching {
            JSONObject(response).optJSONObject("data")
                ?.optJSONObject("Page")
                ?.optJSONArray("characters")
        }.getOrNull() ?: return null

        var fallbackExactMatch: AniCharInfo? = null
        for (i in 0 until nodes.length()) {
            val node = nodes.optJSONObject(i) ?: continue
            val nameObj = node.optJSONObject("name") ?: continue
            val full = nameObj.optString("full", "")
            if (full.isBlank()) continue
            val altArray = nameObj.optJSONArray("alternative")
            val alternatives = mutableListOf<String>()
            if (altArray != null) {
                for (j in 0 until altArray.length()) {
                    val alt = altArray.optString(j)
                    if (alt.isNotBlank()) alternatives.add(alt)
                }
            }
            val imageObj = node.optJSONObject("image")
            val img = imageObj?.optNullableString("large")
                ?: imageObj?.optNullableString("medium")
            if (img.isNullOrBlank()) continue

            val info = AniCharInfo(node.optInt("id", 0), full, nameObj.optNullableString("native"), alternatives, img)

            if (matchAniListCharacter(name, listOf(info)) == null) continue

            val mediaNodes = node.optJSONObject("media")?.optJSONArray("nodes")
            if (realMalId != null && realMalId > 0 && mediaNodes != null) {
                var matchesTarget = false
                for (m in 0 until mediaNodes.length()) {
                    val media = mediaNodes.optJSONObject(m) ?: continue
                    if (media.optInt("idMal", 0) == realMalId) {
                        matchesTarget = true
                        break
                    }
                }
                if (matchesTarget) return info
                if (fallbackExactMatch == null) fallbackExactMatch = info
                continue
            }
            return info
        }
        return fallbackExactMatch
    }

    private fun mergeVoiceActorsIntoCharacters(
        characters: List<KitsugiCharacter>,
        referenceCharacters: List<KitsugiCharacter>
    ): List<KitsugiCharacter> {
        if (referenceCharacters.isEmpty()) return characters

        fun norm(s: String): String = s.lowercase()
            .replace(Regex("\\s*\\((?:voice|uncredited|uncredited voice|child)\\)", RegexOption.IGNORE_CASE), "")
            .replace("ou", "o")
            .replace("oo", "o")
            .replace("oh", "o")
            .replace("uu", "u")
            .replace(Regex("[^a-z0-9]"), "")

        fun getTokens(s: String): Set<String> = s.lowercase()
            .replace(Regex("\\s*\\((?:voice|uncredited|uncredited voice|child)\\)", RegexOption.IGNORE_CASE), "")
            .split(Regex("[\\s,\\-_.]+"))
            .filter { it.length >= 3 }
            .map { it.replace("ou", "o").replace("oo", "o").replace("oh", "o").replace("uu", "u") }
            .toSet()

        return characters.map { char ->
            if (char.voiceActors.isNotEmpty()) return@map char

            val targetNorm = norm(char.name)
            val targetTokens = getTokens(char.name)

            // 1. Match by ID (if positive and equal)
            var matchedRef = referenceCharacters.firstOrNull { ref ->
                char.id > 0 && ref.id > 0 && char.id == ref.id && ref.voiceActors.isNotEmpty()
            }

            // 2. Match by normalized name (exact)
            if (matchedRef == null && targetNorm.length >= 2) {
                matchedRef = referenceCharacters.firstOrNull { ref ->
                    norm(ref.name) == targetNorm && ref.voiceActors.isNotEmpty()
                }
            }

            // 3. Match by reversed name tokens (e.g. "Yoshino Himekawa" vs "Himekawa Yoshino")
            if (matchedRef == null && targetTokens.size >= 2) {
                matchedRef = referenceCharacters.firstOrNull { ref ->
                    val refTokens = getTokens(ref.name)
                    refTokens == targetTokens && ref.voiceActors.isNotEmpty()
                }
            }

            // 4. Match by token subset / containment (e.g. "Yoshino" vs "Yoshino Himekawa")
            if (matchedRef == null && targetTokens.isNotEmpty()) {
                matchedRef = referenceCharacters.firstOrNull { ref ->
                    val refTokens = getTokens(ref.name)
                    (targetTokens.all { it in refTokens } || refTokens.all { it in targetTokens }) && ref.voiceActors.isNotEmpty()
                }
            }

            // 5. Match by substring if name is long enough
            if (matchedRef == null && targetNorm.length >= 4) {
                matchedRef = referenceCharacters.firstOrNull { ref ->
                    val refNorm = norm(ref.name)
                    refNorm.length >= 4 && (refNorm.contains(targetNorm) || targetNorm.contains(refNorm)) && ref.voiceActors.isNotEmpty()
                }
            }

            if (matchedRef != null) {
                char.copy(voiceActors = matchedRef.voiceActors)
            } else {
                char
            }
        }
    }

    internal data class AniCharInfo(
        val id: Int,
        val full: String,
        val native: String?,
        val alternatives: List<String>,
        val imageUrl: String
    )
}

/** Geçici parse sonucu — fetchCharacterDetail içinde Kitsu API yanıtını taşır. */
private data class KitsuCharacterParsed(
    val name: String,
    val description: String?,
    val imageUrl: String?,
    val malId: Int
)

