package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import com.kitsugi.animelist.utils.*
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

class KitsugiStudioClient {

    suspend fun fetchStudioDetail(
        source: String,
        studioId: Int,
        name: String? = null
    ): KitsugiStudioDetail? {
        return withContext(Dispatchers.IO) {
            if (studioId <= 0 && name.isNullOrBlank()) return@withContext null
            val canonicalSource = StudioSourceSupport.canonicalSource(source)
            val primary = when (canonicalSource) {
                "jikan", "mal" -> {
                    val byId = fetchJikanStudioDetail(studioId)
                    // A producer ID from one namespace must never silently resolve to a different
                    // company. If the clicked chip's label disagrees, recover by exact-name search
                    // in Jikan rather than falling through to an unrelated AniList ID.
                    val detail = when {
                        byId != null && StudioSourceSupport.namesMatch(name, byId.name) -> byId
                        !name.isNullOrBlank() -> {
                            val matchedId = searchJikanProducerId(name)
                            matchedId?.let { fetchJikanStudioDetail(it) }
                                ?.takeIf { StudioSourceSupport.namesMatch(name, it.name) }
                        }
                        else -> byId
                    }
                    if (detail != null && !detail.name.isNullOrBlank() &&
                        com.kitsugi.animelist.KitsugiApplication.getInstance()?.let { com.kitsugi.animelist.data.auth.ExternalAuthManager.getAniListToken(it) } != null) {
                        val aniListDetail = fetchAniListStudioByName(detail.name)
                        if (aniListDetail != null) {
                            detail.copy(isFavourite = aniListDetail.isFavourite, aniListId = aniListDetail.id)
                        } else detail
                    } else detail
                }
                "anilist" -> fetchAniListStudioDetail(studioId)
                "bangumi" -> {
                    var detail = if (studioId > 0) fetchBangumiStudioDetail(studioId, name) else null
                    // Kimlik yoksa / yanlışsa kurumu ADIYLA çözümle (Bangumi şirket条目 type=2,
                    // eşleşmezse aynı adlı kişi kaydı — sayfa kişi şablonunu kullanır).
                    if (detail == null && !name.isNullOrBlank()) {
                        val personId = KitsugiBangumiDetailClient.resolveCompanyPersonId(name)
                        if (personId != null) detail = fetchBangumiStudioDetail(personId, name)
                    }
                    // Bangumi kaydı bulundu ama p1 uçları boş döndüyse V0 yedeği denenir.
                    if (detail == null && studioId > 0) {
                        detail = KitsugiBangumiDetailClient.fetchCompanyFallback(studioId)
                    }
                    detail
                }
                "shikimori" -> fetchShikimoriStudioDetail(studioId, name)
                "tmdb" -> {
                    val tmdbRes = fetchTmdbStudioDetail(studioId)
                    val detail = if (tmdbRes != null && !tmdbRes.name.isNullOrBlank() &&
                        !name.isNullOrBlank() && !StudioSourceSupport.namesMatch(name, tmdbRes.name)) {
                        // TMDB ID başka bir şirkete denk geldi (ör. Jikan ID'si TMDB şirket ID'si sanıldı)
                        null
                    } else {
                        tmdbRes
                    }
                    if (detail != null && !detail.name.isNullOrBlank() &&
                        com.kitsugi.animelist.KitsugiApplication.getInstance()?.let { com.kitsugi.animelist.data.auth.ExternalAuthManager.getAniListToken(it) } != null) {
                        val aniListDetail = fetchAniListStudioByName(detail.name)
                        if (aniListDetail != null) {
                            detail.copy(isFavourite = aniListDetail.isFavourite, aniListId = aniListDetail.id)
                        } else detail
                    } else detail
                }
                StudioSourceSupport.TMDB_NETWORK_SOURCE -> fetchTmdbNetworkDetail(studioId)
                else -> null
            }

            // ── Sağlayıcılar arası isim kurtarması ───────────────────────────
            // Stüdyo/şirket kimlikleri sağlayıcıya özgüdür: kimlik uzayı çözülen
            // kaynakta bulunamadığında (ör. Simkl'in TMDB şirket ID'siyle Jikan/MAL'a
            // gitmesi ya da birincil sağlayıcının boş dönmesi) tıklanan ad boşa
            // düşmez; sırayla Jikan → AniList → TMDB üzerinden adıyla aranır.
            // Ad, tüm bilinen yazımlarıyla karşılaştırılır: çip `SHUEISHA` diyorsa ve
            // dönen kurum `集英社` adıyla geliyorsa bu bir uyuşmazlık değildir.
            val expectedNames = StudioSourceSupport.orgNameVariants(name)
                .ifEmpty { listOfNotNull(name?.trim()?.takeIf { it.isNotBlank() }) }
            // Üretim komitelerinin gerçek bir kurum sayfası yoktur; başka bir şirkete
            // "kurtarma" yapmak yanlış kurumu göstermek olur.
            if (expectedNames.isNotEmpty() && !StudioSourceSupport.isCommitteeName(name)) {
                val primaryMatches = primary != null &&
                    expectedNames.any { StudioSourceSupport.namesMatch(it, primary.name) }
                if (!primaryMatches) {
                    val recovered = recoverStudioByName(expectedNames, excludeSource = canonicalSource.orEmpty())
                    if (recovered != null) return@withContext enrichStudioMetaFromJikan(recovered, canonicalSource)
                }
            }

            primary?.let { enrichStudioMetaFromJikan(it, canonicalSource) }
        }
    }

    /**
     * Stüdyo yapımlarının [page]. sayfası (2'den başlar; 1. sayfa [fetchStudioDetail] ile gelir).
     * [studioId] çözümlenmiş kimliktir (detail.id): Jikan'da chip kimliğinden farklı olabilir.
     */
    suspend fun fetchStudioWorksPage(source: String, studioId: Int, page: Int): KitsugiStudioWorksPage? =
        withContext(Dispatchers.IO) {
            if (studioId <= 0 || page < 2) return@withContext null
            when (StudioSourceSupport.canonicalSource(source)) {
                "jikan", "mal" -> fetchJikanStudioWorksPage(studioId, page)
                "anilist" -> fetchAniListStudioWorksPage(studioId, page)
                "shikimori" -> fetchShikimoriStudioWorksPage(studioId, page)
                "tmdb" -> fetchTmdbStudioWorksPage(studioId, page)
                StudioSourceSupport.TMDB_NETWORK_SOURCE -> fetchTmdbNetworkWorksPage(studioId, page)
                // Bangumi: yapım listesi sayfalanmıyor (sayfalama ucu doğrulanamadı) → devamı yok.
                else -> null
            }
        }

    /**
     * Sağlayıcılar arası ad kurtarması.
     * Birincil sağlayıcı boş döndüğünde ya da dönen kurum adı çiple uyuşmadığında
     * adıyla doğrulanmış bir sağlayıcıdan (Jikan → AniList → TMDB) kurtarır.
     */
    private suspend fun recoverStudioByName(names: List<String>, excludeSource: String): KitsugiStudioDetail? {
        val candidates = names
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length <= 80 }
            .distinct()
            .take(RECOVERY_NAME_LIMIT)
        if (candidates.isEmpty()) return null

        if (excludeSource != "jikan" && excludeSource != "mal") {
            for (candidate in candidates) {
                val jikanId = searchJikanProducerId(candidate) ?: continue
                fetchJikanStudioDetail(jikanId)
                    ?.takeIf { detail -> candidates.any { StudioSourceSupport.namesMatch(it, detail.name) } }
                    ?.let { return it }
            }
        }
        if (excludeSource != "anilist") {
            for (candidate in candidates) {
                fetchAniListStudioByName(candidate)
                    ?.takeIf { detail -> candidates.any { StudioSourceSupport.namesMatch(it, detail.name) } }
                    ?.let { return it }
            }
        }
        if (excludeSource != "tmdb") {
            for (candidate in candidates) {
                val tmdbId = searchTmdbCompanyId(candidate) ?: continue
                fetchTmdbStudioDetail(tmdbId)
                    ?.takeIf { detail -> candidates.any { StudioSourceSupport.namesMatch(it, detail.name) } }
                    ?.let { return it }
            }
        }
        return null
    }

    /** TMDB şirket adıyla kimlik arar — kimlik uzayı uyuşmazlığında kurtarma yoludur. */
    private suspend fun searchTmdbCompanyId(expectedName: String): Int? = runCatching {
        val apiKey = TmdbApiClient.getActiveApiKey()
        if (apiKey.isBlank()) return@runCatching null
        val query = URLEncoder.encode(expectedName.trim(), "UTF-8")
        val url = URL("https://api.themoviedb.org/3/search/company?api_key=$apiKey&query=$query")
        val response = KitsugiApiBase.executeGetRequest(url) ?: return@runCatching null
        val results = JSONObject(response).optJSONArray("results") ?: return@runCatching null
        var bestId: Int? = null
        var bestScore = 0
        val expectedKey = StudioSourceSupport.normalizeName(expectedName)

        for (index in 0 until results.length()) {
            val item = results.optJSONObject(index) ?: continue
            val id = item.optInt("id").takeIf { it > 0 } ?: continue
            val companyName = item.optNullableString("name") ?: continue
            if (!StudioSourceSupport.namesMatch(expectedName, companyName)) continue
            val exact = StudioSourceSupport.normalizeName(companyName) == expectedKey
            val score = if (exact) 2 else 1
            if (score > bestScore) {
                bestId = id
                bestScore = score
                if (exact) break
            }
        }
        bestId
    }.getOrNull()

    /**
     * Bangumi'de stüdyo/şirketler ayrı bir kayıt türü değil, 人物 (kişi)条目'larının
     * `type = 2` (公司) alt kümesidir. Bu yüzden kurum detayı, kişi ucu üzerinden okunur ve
     * Kitsugi'nin stüdyo modeline çevrilir: açıklamalar infobox'dan, "Yapımlar" listesi
     * `/persons/{id}/works` ucundan gelir.
     */
    private suspend fun fetchBangumiStudioDetail(
        personId: Int,
        expectedName: String? = null
    ): KitsugiStudioDetail? {
        // p1 deneyseldir ve devre kesiciye takılabilir. Kurum kökünü ve yapımları önce
        // resmî v0 person uçlarından al; p1'i yalnızca eksik yapım bölümünü tamamlamak için kullan.
        val v0Person = runCatching {
            KitsugiBangumiCreditsClient.fetchPersonDetail(personId, expectedName)
        }.getOrElse {
            if (it is CancellationException) throw it
            null
        }
        val p1Person = if (v0Person?.mediaWorks.isNullOrEmpty()) {
            runCatching { KitsugiBangumiDetailClient.fetchStaffDetail(personId) }.getOrElse {
                if (it is CancellationException) throw it
                null
            }
        } else {
            null
        }
        val person = mergeBangumiOrganizationDetails(v0Person, p1Person)
        if (person == null) {
            val fallback = runCatching { KitsugiBangumiDetailClient.fetchCompanyFallback(personId) }.getOrNull()
            if (fallback != null && (expectedName.isNullOrBlank() || StudioSourceSupport.namesMatch(expectedName, fallback.name))) {
                return fallback
            }
            return null
        }
        if (!matchesBangumiOrganization(expectedName, person)) return null

        var about = buildString {
            if (!person.homeTown.isNullOrBlank()) appendLine(person.homeTown)
            if (!person.biography.isNullOrBlank()) {
                append(person.biography)
            } else if (!person.occupation.isNullOrBlank()) {
                append(person.occupation)
            }
        }.trim().takeIf { it.isNotBlank() }
        var works = person.mediaWorks
        var imageUrl = person.imageUrl

        // ŞİRKET KAYITLARI: p1 `/persons/{id}/works` ucu kişi "görev"lerini döndürür;
        // şirket kayıtlarında bu liste sık sık BOŞ geliyor (kurumun katkısı `staff`
        // pozisyonu olarak tutulmuyor). Sayfa açılıyor ama "Yapımlar" bölümü boş kalıyordu.
        // V0 `/persons/{id}/subjects` ucu kurumun yer aldığı yapımları doğrudan verir.
        if (works.isEmpty() || about == null) {
            val fallback = runCatching {
                KitsugiBangumiDetailClient.fetchCompanyFallback(personId)
            }.getOrNull()
            if (fallback != null) {
                if (works.isEmpty()) works = fallback.mediaWorks
                if (about == null) about = fallback.about
                if (imageUrl.isNullOrBlank()) imageUrl = fallback.imageUrl
            }
        }

        return KitsugiStudioDetail(
            id = person.id,
            name = person.englishName ?: person.name,
            isMain = true,
            imageUrl = imageUrl,
            about = about,
            mediaWorks = works
        )
    }

    /**
     * AniList, Shikimori, Bangumi veya TMDB kaynaklı stüdyolarda açıklama (about),
     * kuruluş tarihi (established) veya logo (imageUrl) eksikse Jikan/MAL yapımcı
     * kaydından tamamlar.
     */
    private suspend fun enrichStudioMetaFromJikan(
        detail: KitsugiStudioDetail,
        canonicalSource: String?
    ): KitsugiStudioDetail {
        if (canonicalSource == "jikan" || canonicalSource == "mal") return detail
        val currentAbout = detail.about?.trim().orEmpty()
        val hasSubstantialAbout = currentAbout.isNotBlank() &&
            currentAbout.lines().any { line ->
                val t = line.trim()
                t.isNotBlank() &&
                    !t.startsWith("Merkez:", ignoreCase = true) &&
                    !t.startsWith("Ülke:", ignoreCase = true) &&
                    !t.startsWith("Web:", ignoreCase = true)
            }
        if (hasSubstantialAbout && !detail.imageUrl.isNullOrBlank() && !detail.established.isNullOrBlank()) {
            return detail
        }
        val studioName = detail.name.trim()
        if (studioName.isBlank()) return detail

        return runCatching {
            val query = URLEncoder.encode(studioName, "UTF-8")
            val url = URL("https://api.jikan.moe/v4/producers?q=$query&limit=25")
            val response = KitsugiApiBase.runWithRateLimit {
                KitsugiApiBase.executeGetRequest(url)
            } ?: return@runCatching detail
            val data = JSONObject(response).optJSONArray("data") ?: return@runCatching detail
            val expectedKey = StudioSourceSupport.normalizeName(studioName)
            var matchedItem: JSONObject? = null
            var bestScore = 0

            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                val names = buildList {
                    item.optNullableString("name")?.let(::add)
                    item.optJSONArray("titles")?.let { titles ->
                        for (titleIndex in 0 until titles.length()) {
                            titles.optJSONObject(titleIndex)?.optNullableString("title")?.let(::add)
                        }
                    }
                }
                if (names.none { StudioSourceSupport.namesMatch(studioName, it) }) continue
                val exact = names.any { StudioSourceSupport.normalizeName(it) == expectedKey }
                val score = if (exact) 2 else 1
                if (score > bestScore) {
                    matchedItem = item
                    bestScore = score
                    if (exact) break
                }
            }

            val item = matchedItem ?: return@runCatching detail
            val jikanAbout = item.optNullableString("about")?.cleanApiText()?.takeIf { it.isNotBlank() }
            val jikanImg = item.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url")
            val jikanEstablished = item.optNullableString("established")?.substringBefore("T")

            val mergedAbout = when {
                hasSubstantialAbout -> detail.about
                currentAbout.isNotBlank() && !jikanAbout.isNullOrBlank() -> "$currentAbout\n\n$jikanAbout"
                !jikanAbout.isNullOrBlank() -> jikanAbout
                else -> detail.about
            }

            detail.copy(
                about = mergedAbout,
                imageUrl = detail.imageUrl?.takeIf { it.isNotBlank() } ?: jikanImg,
                established = detail.established?.takeIf { it.isNotBlank() } ?: jikanEstablished
            )
        }.getOrDefault(detail)
    }

    private suspend fun searchJikanProducerId(expectedName: String): Int? = runCatching {
        val query = URLEncoder.encode(expectedName.trim(), "UTF-8")
        val url = URL("https://api.jikan.moe/v4/producers?q=$query&limit=25")
        val response = KitsugiApiBase.runWithRateLimit {
            KitsugiApiBase.executeGetRequest(url)
        } ?: return@runCatching null
        val data = JSONObject(response).optJSONArray("data") ?: return@runCatching null
        var bestId: Int? = null
        var bestScore = 0
        val expectedKey = StudioSourceSupport.normalizeName(expectedName)

        for (index in 0 until data.length()) {
            val item = data.optJSONObject(index) ?: continue
            val id = item.optInt("mal_id").takeIf { it > 0 } ?: continue
            val names = buildList {
                item.optNullableString("name")?.let(::add)
                item.optJSONArray("titles")?.let { titles ->
                    for (titleIndex in 0 until titles.length()) {
                        titles.optJSONObject(titleIndex)?.optNullableString("title")?.let(::add)
                    }
                }
            }
            if (names.none { StudioSourceSupport.namesMatch(expectedName, it) }) continue
            val exact = names.any { StudioSourceSupport.normalizeName(it) == expectedKey }
            val score = if (exact) 2 else 1
            if (score > bestScore) {
                bestId = id
                bestScore = score
                if (exact) break
            }
        }
        bestId
    }.getOrNull()

    /**
     * Shikimori stüdyo (kurum) sayfası.
     *
     * ÖNEMLİ: Shikimori'nin tek kayıtlık stüdyo ucu YOKTUR — `GET /api/studios/{id}` 404
     * döner, `/api/studios` ise tüm stüdyoların dizin listesidir. Eski kod bu olmayan ucu
     * okumaya çalıştığı için Shikimori kaynaklı yapımlarda "Stüdyo detayları yüklenemedi."
     * ekranı görünüyordu. Sayfa artık iki doğrulanmış uçtan kurulur:
     *  - `GET /api/animes?studio={id}` → stüdyonun yapımları
     *  - `GET /api/animes/{animeId}`   → anime kaydının `studios[]` satırı (kanonik ad + logo)
     *
     * Stüdyo logosu Shikimori'de `/system/studios/...` biçiminde göreli dize olarak döner;
     * anime `studios[]` alanında `image` bir NESNE değil DİZGİDİR (düz yol).
     */
    private suspend fun fetchShikimoriStudioDetail(
        studioId: Int,
        expectedName: String?
    ): KitsugiStudioDetail? = runCatching {
        var studioName = expectedName?.trim()?.takeIf { it.isNotEmpty() }
        var studioImage: String? = null
        val worksUrl = shikimoriStudioWorksUrl(studioId, 1)
        val worksResponse = KitsugiApiBase.executeGetRequestResilient(worksUrl)
        val works = worksResponse?.let { runCatching { JSONArray(it) }.getOrNull() }
        val mediaWorks = withShikimoriWorkPosters(
            parseShikimoriStudioWorks(works).distinctBy { it.mediaId }
        )

        // Kanonik ad/logo `GET /api/animes/{id}` yanıtındaki `studios[]` satırından okunur.
        // Ad zaten bilinse bile logo genellikle YALNIZCA orada bulunur; bu yüzden bu istek
        // "ad yok" koşuluna bağlanmaz. İlk yapım stüdyoyu listelemiyorsa (ortak yapımlar,
        // TV kısaltmaları vb.) sıradaki birkaç yapım denenir — tavan, sayfanın açılışını
        // geciktirmemek için 3 istekle sınırlıdır.
        if (studioName == null || studioImage == null) {
            for (candidate in mediaWorks.take(SHIKIMORI_STUDIO_LOOKUP_LIMIT)) {
                if (candidate.mediaId <= 0) continue
                val animeDetail = runCatching {
                    val url = URL("https://shikimori.io/api/animes/${candidate.mediaId}")
                    KitsugiApiBase.executeGetRequestResilient(url)?.let { JSONObject(it) }
                }.getOrNull() ?: continue
                val studios = animeDetail.optJSONArray("studios") ?: continue
                for (index in 0 until studios.length()) {
                    val entry = studios.optJSONObject(index) ?: continue
                    if (entry.optInt("id") != studioId) continue
                    if (studioName == null) {
                        studioName = entry.optNullableString("name") ?: entry.optNullableString("filtered_name")
                    }
                    if (studioImage == null) {
                        studioImage = (entry.optNullableString("image") ?: entry.optJSONObject("image")
                            ?.optNullableString("original"))?.let(::absoluteShikimoriImageUrl)
                    }
                    break
                }
                if (studioName != null && studioImage != null) break
            }
        }

        val name = studioName ?: return@runCatching null

        KitsugiStudioDetail(
            id = studioId,
            name = name,
            isMain = true,
            imageUrl = studioImage,
            about = null,
            mediaWorks = mediaWorks,
            hasMoreWorks = (works?.length() ?: 0) >= STUDIO_WORKS_PAGE_SIZE
        )
    }.getOrNull()

    private fun shikimoriStudioWorksUrl(studioId: Int, page: Int): URL =
        URL("https://shikimori.io/api/animes?studio=$studioId&limit=$STUDIO_WORKS_PAGE_SIZE&order=aired_on&page=$page")

    private fun parseShikimoriStudioWorks(works: JSONArray?): List<KitsugiStaffMediaWork> {
        if (works == null) return emptyList()
        val out = ArrayList<KitsugiStaffMediaWork>(works.length())
        for (index in 0 until works.length()) {
            val item = works.optJSONObject(index) ?: continue
            val mediaId = item.optInt("id").takeIf { it > 0 } ?: continue
            val romajiTitle = item.optNullableString("name")
            val englishTitle = item.optJSONArray("english")?.optString(0)?.takeIf { it.isNotBlank() && it != "null" }
            val russianTitle = item.optNullableString("russian")
            val title = russianTitle ?: englishTitle ?: romajiTitle ?: if (isTurkish()) KitsugiStrings.get(R.string.basliksiz) else "Untitled"
            val kind = item.optNullableString("kind").orEmpty().lowercase()
            // Not: `/api/animes` kayıtlarında `image` normalde {original, preview, …} NESNESİDİR;
            // ancak `studios[]` gibi bazı alanlarda düz DİZGE olarak da dönebilir. İki biçim de
            // kabul edilir; eksik/geçersiz kalan kapaklar `withShikimoriWorkPosters` ile
            // GraphQL'den tamamlanır.
            val posterUrl = (item.optJSONObject("image")
                ?.let { image -> image.optNullableString("original") ?: image.optNullableString("preview") }
                ?: item.optNullableString("image"))
                ?.let(::absoluteShikimoriImageUrl)
            out.add(
                KitsugiStaffMediaWork(
                    mediaId = mediaId,
                    mediaTitle = title,
                    mediaImageUrl = posterUrl,
                    mediaType = (if (kind == "movie") "movie" else "anime").toTurkishMediaTypeString(),
                    staffRole = if (isTurkish()) "Stüdyo" else "Studio",
                    source = "shikimori",
                    titleEnglish = englishTitle,
                    titleRomaji = romajiTitle
                )
            )
        }
        return out
    }

    private suspend fun fetchShikimoriStudioWorksPage(studioId: Int, page: Int): KitsugiStudioWorksPage? = runCatching {
        val body = KitsugiApiBase.executeGetRequestResilient(shikimoriStudioWorksUrl(studioId, page))
            ?: return@runCatching null
        val works = JSONArray(body)
        KitsugiStudioWorksPage(
            works = withShikimoriWorkPosters(parseShikimoriStudioWorks(works).distinctBy { it.mediaId }),
            hasMore = works.length() >= STUDIO_WORKS_PAGE_SIZE
        )
    }.getOrNull()

    /**
     * Stüdyo yapım listesindeki eksik kapakları GraphQL'den toplu olarak tamamlar.
     *
     * NEDEN GEREKLİ: Shikimori REST API'si `image` alanını eski Paperclip eki üzerinden
     * üretir. Kapakları yıllar önce `Poster` tablosuna (Shrine) taşıyan yapımlarda bu alan
     * sessizce `/assets/globals/missing_original.jpg` döner; [absoluteShikimoriImageUrl] bunu
     * "görsel yok" sayıp `null`'a çevirdiği için stüdyo sayfasındaki kartlar baş harf yer
     * tutucusuyla kalıyordu ("MAPPA → Boukyaku Battery = BO" tablosu). Gerçek kapaklar
     * yalnızca GraphQL `poster` alanında bulunur; eksik kimlikler [ShikimoriPosterResolver]
     * üzerinden 50'lik gruplar hâlinde tek istekte çözülür. Ayrıntılı kök neden:
     * [ShikimoriPosterResolver] sınıf açıklaması.
     */
    private suspend fun withShikimoriWorkPosters(
        works: List<KitsugiStaffMediaWork>
    ): List<KitsugiStaffMediaWork> {
        if (works.isEmpty()) return works
        val missingIds = works.filter { it.mediaImageUrl == null && it.mediaId > 0 }.map { it.mediaId }
        if (missingIds.isEmpty()) return works
        val posters = runCatching {
            ShikimoriPosterResolver.resolve(ShikimoriPosterResolver.Kind.ANIME, missingIds)
        }.getOrNull().orEmpty()
        if (posters.isEmpty()) return works
        return works.map { work ->
            if (work.mediaImageUrl != null) work
            else posters[work.mediaId]?.cardUrl?.let { url -> work.copy(mediaImageUrl = url) } ?: work
        }
    }

    private fun absoluteShikimoriImageUrl(value: String): String? = when {
        value.isBlank() || value.contains("/assets/globals/missing_") -> null
        value.startsWith("https://", ignoreCase = true) || value.startsWith("http://", ignoreCase = true) -> value
        value.startsWith("//") -> "https:$value"
        value.startsWith("/") -> "https://shikimori.io$value"
        else -> null
    }

    private suspend fun fetchTmdbStudioDetail(studioId: Int): KitsugiStudioDetail? {
        val apiKey = TmdbApiClient.getActiveApiKey()
        val lang = TmdbApiClient.getActiveLanguage()
        val infoUrl = URL("https://api.themoviedb.org/3/company/$studioId?api_key=$apiKey")

        return runCatching {
            val detailResponse = KitsugiApiBase.executeGetRequest(infoUrl) ?: return@runCatching null
            val detailData = JSONObject(detailResponse)

            val name = detailData.optString("name")
            val hq = detailData.optNullableString("headquarters")
            val country = detailData.optNullableString("origin_country")
            val desc = detailData.optNullableString("description")

            val about = buildString {
                if (!hq.isNullOrBlank()) append("Merkez: $hq\n")
                if (!country.isNullOrBlank()) append("Ülke: $country\n")
                if (!desc.isNullOrBlank()) append(desc)
            }.trim().cleanApiText().takeIf { it.isNotBlank() }

            val logoPath = detailData.optNullableString("logo_path")
            val imageUrl = if (!logoPath.isNullOrBlank()) "https://image.tmdb.org/t/p/w300$logoPath" else null

            val mediaWorks = mutableListOf<KitsugiStaffMediaWork>()

            val (movieWorks, movieTotalPages) = fetchTmdbDiscover(true, studioId, apiKey, lang, 1)
            val (tvWorks, tvTotalPages) = fetchTmdbDiscover(false, studioId, apiKey, lang, 1)
            mediaWorks.addAll(movieWorks)
            mediaWorks.addAll(tvWorks)
            val tmdbHasMore = movieTotalPages > 1 || tvTotalPages > 1

            KitsugiStudioDetail(
                id = studioId,
                name = name,
                isMain = true,
                imageUrl = imageUrl,
                favorites = null,
                established = null,
                about = about,
                mediaWorks = mediaWorks.distinctBy { it.mediaId },
                hasMoreWorks = tmdbHasMore
            )
        }.getOrNull()
    }

    private enum class TmdbOrganizationKind {
        COMPANY,
        NETWORK
    }

    /** TMDB network detayları /network/{id} ucundan alınır; bu ID alanı şirketlerden ayrıdır. */
    private suspend fun fetchTmdbNetworkDetail(networkId: Int): KitsugiStudioDetail? {
        if (networkId <= 0) return null
        val apiKey = TmdbApiClient.getActiveApiKey()
        val lang = TmdbApiClient.getActiveLanguage()
        val infoUrl = URL("https://api.themoviedb.org/3/network/$networkId?api_key=$apiKey")

        return runCatching {
            val detailResponse = KitsugiApiBase.executeGetRequest(infoUrl) ?: return@runCatching null
            val detailData = JSONObject(detailResponse)
            val name = detailData.optNullableString("name")?.takeIf { it.isNotBlank() }
                ?: return@runCatching null
            val hq = detailData.optNullableString("headquarters")
            val country = detailData.optNullableString("origin_country")
            val homepage = detailData.optNullableString("homepage")
            val about = buildString {
                if (!hq.isNullOrBlank()) append("Merkez: $hq\n")
                if (!country.isNullOrBlank()) append("Ülke: $country\n")
                if (!homepage.isNullOrBlank()) append("Web: $homepage")
            }.trim().takeIf { it.isNotBlank() }
            val logoPath = detailData.optNullableString("logo_path")
            val imageUrl = if (!logoPath.isNullOrBlank()) "https://image.tmdb.org/t/p/w300$logoPath" else null
            val (works, totalPages) = fetchTmdbDiscover(
                isMovie = false,
                studioId = networkId,
                apiKey = apiKey,
                lang = lang,
                page = 1,
                organizationKind = TmdbOrganizationKind.NETWORK
            )

            KitsugiStudioDetail(
                id = networkId,
                name = name,
                imageUrl = imageUrl,
                about = about,
                mediaWorks = works.distinctBy { it.mediaId },
                hasMoreWorks = totalPages > 1
            )
        }.getOrNull()
    }

    /** TMDB discover (film veya dizi) tek sayfası: yapımlar + toplam sayfa sayısı. */
    private fun fetchTmdbDiscover(
        isMovie: Boolean,
        studioId: Int,
        apiKey: String,
        lang: String,
        page: Int,
        organizationKind: TmdbOrganizationKind = TmdbOrganizationKind.COMPANY
    ): Pair<List<KitsugiStaffMediaWork>, Int> {
        val kind = if (isMovie) "movie" else "tv"
        val companyFilter = organizationKind == TmdbOrganizationKind.COMPANY
        val filterParam = if (companyFilter) "with_companies" else "with_networks"
        val urlStr = "https://api.themoviedb.org/3/discover/$kind?api_key=$apiKey&$filterParam=$studioId&language=$lang&sort_by=popularity.desc&page=$page"
        val response = KitsugiApiBase.executeGetRequest(URL(urlStr)) ?: return Pair(emptyList(), 0)
        val root = JSONObject(response)
        val results = root.optJSONArray("results") ?: return Pair(emptyList(), 0)
        val totalPages = root.optInt("total_pages", 0)
        // Türkçe başlık yoksa İngilizce'ye düşmek için en-US haritası
        val enTitles = if (TmdbTitleFallback.needsEnglishFallback(results, urlStr, { isMovie })) {
            TmdbTitleFallback.parseEnglishTitles(
                runCatching { KitsugiApiBase.executeGetRequest(URL(TmdbUrlUtils.englishVariant(urlStr))) }.getOrNull()
            )
        } else emptyMap()
        val works = ArrayList<KitsugiStaffMediaWork>(results.length())
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val id = item.optInt("id")
            val title = TmdbTitleFallback.resolve(
                item = item,
                isMovie = isMovie,
                url = urlStr,
                englishTitle = enTitles[id]
            ).display.ifBlank { if (isTurkish()) "Başlıksız" else "Untitled" }
            val posterPath = item.optNullableString("poster_path")
            val imgUrl = if (!posterPath.isNullOrBlank()) "https://image.tmdb.org/t/p/w185$posterPath" else null
            works.add(
                KitsugiStaffMediaWork(
                    mediaId = id,
                    mediaTitle = title,
                    mediaImageUrl = imgUrl,
                    mediaType = kind.toTurkishMediaTypeString(),
                    staffRole = if (companyFilter) (if (isTurkish()) "Yapım Şirketi" else "Production Company") else (if (isTurkish()) "Yayıncı Ağı" else "Network"),
                    source = "tmdb"
                )
            )
        }
        return Pair(works, totalPages)
    }

    private suspend fun fetchTmdbStudioWorksPage(studioId: Int, page: Int): KitsugiStudioWorksPage? = runCatching {
        val apiKey = TmdbApiClient.getActiveApiKey()
        val lang = TmdbApiClient.getActiveLanguage()
        val (movies, movieTotal) = fetchTmdbDiscover(true, studioId, apiKey, lang, page)
        val (tv, tvTotal) = fetchTmdbDiscover(false, studioId, apiKey, lang, page)
        KitsugiStudioWorksPage(
            works = (movies + tv).distinctBy { it.mediaId },
            hasMore = page < movieTotal || page < tvTotal
        )
    }.getOrNull()

    private suspend fun fetchTmdbNetworkWorksPage(networkId: Int, page: Int): KitsugiStudioWorksPage? = runCatching {
        val apiKey = TmdbApiClient.getActiveApiKey()
        val lang = TmdbApiClient.getActiveLanguage()
        val (works, totalPages) = fetchTmdbDiscover(
            isMovie = false,
            studioId = networkId,
            apiKey = apiKey,
            lang = lang,
            page = page,
            organizationKind = TmdbOrganizationKind.NETWORK
        )
        KitsugiStudioWorksPage(works = works.distinctBy { it.mediaId }, hasMore = page < totalPages)
    }.getOrNull()

    private fun jikanStudioWorksUrl(producerId: Int, page: Int): URL =
        // limit=50: Tenrai'nin üst sınırı (daha büyük değer 400 döndürür).
        URL("https://api.jikan.moe/v4/anime?producers=$producerId&order_by=start_date&sort=desc&limit=$STUDIO_WORKS_PAGE_SIZE&sfw=false&page=$page")

    private fun parseJikanStudioWorks(data: JSONArray?): List<KitsugiStaffMediaWork> {
        if (data == null) return emptyList()
        val out = ArrayList<KitsugiStaffMediaWork>(data.length())
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val id = item.optInt("mal_id")
            val title = item.optNullableString("title") ?: if (isTurkish()) "Bilinmeyen" else "Unknown"
            val imageUrl = item.optJSONObject("images")?.optJSONObject("jpg")?.optNullableString("image_url")
            val type = item.optNullableString("type").orEmpty().lowercase()
            out.add(
                KitsugiStaffMediaWork(
                    mediaId = id,
                    mediaTitle = title,
                    mediaImageUrl = imageUrl,
                    mediaType = if (type.contains("manga")) "manga".toTurkishMediaTypeString() else "anime".toTurkishMediaTypeString(),
                    staffRole = if (isTurkish()) "Ana Stüdyo" else "Main Studio",
                    source = "jikan"
                )
            )
        }
        return out
    }

    private suspend fun fetchJikanStudioWorksPage(producerId: Int, page: Int): KitsugiStudioWorksPage? = runCatching {
        val body = KitsugiApiBase.executeGetRequestResilient(jikanStudioWorksUrl(producerId, page))
            ?: return@runCatching null
        val root = JSONObject(body)
        KitsugiStudioWorksPage(
            works = parseJikanStudioWorks(root.optJSONArray("data")).distinctBy { it.mediaId },
            hasMore = root.optJSONObject("pagination")?.optBoolean("has_next_page", false) == true
        )
    }.getOrNull()

    private suspend fun fetchJikanStudioDetail(studioId: Int): KitsugiStudioDetail? {
        val infoUrl = URL("https://api.jikan.moe/v4/producers/$studioId")
        // limit=50: Tenrai'nin üst sınırı; daha büyük değerler 400 döndürüp yapım listesini boşaltıyordu.
        val mediaUrl = jikanStudioWorksUrl(studioId, 1)

        return runCatching {
            // Jikan rate limit: maks 3 istek/saniye — her çağrı runWithRateLimit ile korunuyor
            // Resilient: tek seferlik 429/5xx yüzünden tüm stüdyo sayfası düşmesin.
            val infoResponse = KitsugiApiBase.runWithRateLimit {
                KitsugiApiBase.executeGetRequestResilient(infoUrl)
            } ?: return@runCatching null
            val infoRoot = JSONObject(infoResponse)
            val infoData = infoRoot.optJSONObject("data") ?: return@runCatching null

            // Jikan /producers/{id}: isim data.titles[].title (type=="Default") altında,
            // üst seviye data.name alanı bu endpoint'te mevcut değil.
            val titlesArr = infoData.optJSONArray("titles")
            val name: String = if (titlesArr != null) {
                var defaultTitle: String? = null
                var anyTitle: String? = null
                for (i in 0 until titlesArr.length()) {
                    val t = titlesArr.optJSONObject(i) ?: continue
                    val type = t.optNullableString("type")
                    val title = t.optNullableString("title")
                    if (!title.isNullOrBlank()) {
                        if (anyTitle == null) anyTitle = title
                        if (type.equals("Default", ignoreCase = true) ||
                            type.equals("English", ignoreCase = true)
                        ) {
                            defaultTitle = title
                        }
                    }
                }
                defaultTitle ?: anyTitle ?: infoData.optNullableString("name") ?: if (isTurkish()) "Bilinmeyen" else "Unknown"
            } else {
                infoData.optNullableString("name") ?: if (isTurkish()) "Bilinmeyen" else "Unknown"
            }

            val favorites = infoData.optionalPositiveInt("favorites")
            val established = infoData.optNullableString("established")
            val about = infoData.optNullableString("about")?.cleanApiText()

            val mediaWorks = mutableListOf<KitsugiStaffMediaWork>()
            val mediaResponse = KitsugiApiBase.runWithRateLimit {
                KitsugiApiBase.executeGetRequestResilient(mediaUrl)
            }
            var worksHasMore = false
            if (mediaResponse != null) {
                val mediaRoot = JSONObject(mediaResponse)
                mediaWorks.addAll(parseJikanStudioWorks(mediaRoot.optJSONArray("data")))
                worksHasMore = mediaRoot.optJSONObject("pagination")?.optBoolean("has_next_page", false) == true
            }

            val imagesObj = infoData.optJSONObject("images")
            val imageUrl = imagesObj?.optJSONObject("jpg")?.optNullableString("image_url")

            KitsugiStudioDetail(
                id = studioId,
                name = name,
                isMain = true,
                imageUrl = imageUrl,
                favorites = favorites,
                established = established,
                about = about,
                mediaWorks = mediaWorks.distinctBy { it.mediaId },
                hasMoreWorks = worksHasMore
            )
        }.getOrNull()
    }

    private suspend fun fetchAniListStudioDetail(studioId: Int): KitsugiStudioDetail? {
        val query = """
            query (${'$'}id: Int) {
                Studio(id: ${'$'}id) {
                    id
                    name
                    isAnimationStudio
                    isFavourite
                    favourites
                    media(page: 1, perPage: 50, sort: [START_DATE_DESC]) {
                        pageInfo { hasNextPage }
                        nodes {
                            id
                            idMal
                            title { userPreferred english romaji native }
                            coverImage { large }
                            type
                        }
                    }
                }
            }
        """.trimIndent()

        val variables = JSONObject().put("id", studioId)

        return runCatching {
            val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return@runCatching null
            val root = JSONObject(response)
            val data = root.optJSONObject("data")?.optJSONObject("Studio") ?: return@runCatching null

            val name = data.optString("name")
            val favorites = data.optionalPositiveInt("favourites")
            val isAnimationStudio = data.optBoolean("isAnimationStudio", true)

            val mediaWorks = mutableListOf<KitsugiStaffMediaWork>()
            val mediaObj = data.optJSONObject("media")
            val nodes = mediaObj?.optJSONArray("nodes")
            if (nodes != null) {
                mediaWorks.addAll(parseAniListStudioNodes(nodes))
            }

            val worksHasMore = mediaObj?.optJSONObject("pageInfo")?.optBoolean("hasNextPage", false) == true
            val isFavourite = data.optBoolean("isFavourite", false)
            KitsugiStudioDetail(
                id = studioId,
                name = name,
                isMain = isAnimationStudio,
                imageUrl = null,
                favorites = favorites,
                established = null,
                about = null,
                mediaWorks = mediaWorks.distinctBy { it.mediaId },
                isFavourite = isFavourite,
                aniListId = studioId,
                hasMoreWorks = worksHasMore
            )
        }.getOrNull()
    }

    private suspend fun fetchAniListStudioWorksPage(studioId: Int, page: Int): KitsugiStudioWorksPage? = runCatching {
        val query = """
            query (${'$'}id: Int, ${'$'}page: Int) {
                Studio(id: ${'$'}id) {
                    media(page: ${'$'}page, perPage: 50, sort: [START_DATE_DESC]) {
                        pageInfo { hasNextPage }
                        nodes {
                            id
                            idMal
                            title { userPreferred english romaji native }
                            coverImage { large }
                            type
                        }
                    }
                }
            }
        """.trimIndent()

        val variables = JSONObject().put("id", studioId).put("page", page)
        val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return@runCatching null
        val mediaObj = JSONObject(response).optJSONObject("data")
            ?.optJSONObject("Studio")
            ?.optJSONObject("media") ?: return@runCatching null
        KitsugiStudioWorksPage(
            works = parseAniListStudioNodes(mediaObj.optJSONArray("nodes")).distinctBy { it.mediaId },
            hasMore = mediaObj.optJSONObject("pageInfo")?.optBoolean("hasNextPage", false) == true
        )
    }.getOrNull()

    private fun parseAniListStudioNodes(nodes: JSONArray?): List<KitsugiStaffMediaWork> {
        if (nodes == null) return emptyList()
        val out = ArrayList<KitsugiStaffMediaWork>(nodes.length())
        for (i in 0 until nodes.length()) {
            val node = nodes.optJSONObject(i) ?: continue
            val id = node.optInt("id")
            val idMal = node.optionalPositiveInt("idMal")
            val titleObj = node.optJSONObject("title")
            val title = titleObj?.optNullableString("userPreferred") ?: if (isTurkish()) KitsugiStrings.get(R.string.basliksiz) else "Untitled"
            val imgUrl = node.optJSONObject("coverImage")?.optNullableString("large")
            val type = node.optNullableString("type").orEmpty().lowercase()
            out.add(
                KitsugiStaffMediaWork(
                    mediaId = idMal ?: (100_000_000 + id),
                    mediaTitle = title,
                    mediaImageUrl = imgUrl,
                    mediaType = type.toTurkishMediaTypeString(),
                    staffRole = if (isTurkish()) "Ana Stüdyo" else "Main Studio",
                    source = if (idMal != null) "jikan" else "anilist",
                    titleEnglish = titleObj?.optNullableString("english"),
                    titleJapanese = titleObj?.optNullableString("native"),
                    titleRomaji = titleObj?.optNullableString("romaji")
                )
            )
        }
        return out
    }

    private suspend fun fetchAniListStudioByName(name: String): KitsugiStudioDetail? {
        val query = """
            query (${'$'}search: String) {
                Page(page: 1, perPage: 10) {
                    studios(search: ${'$'}search) {
                        id
                        name
                        isAnimationStudio
                        isFavourite
                        favourites
                        media(page: 1, perPage: 50, sort: [START_DATE_DESC]) {
                            pageInfo { hasNextPage }
                            nodes {
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

        val variables = JSONObject().put("search", name)

        return runCatching {
            val response = KitsugiApiBase.executeAniListQuery(query, variables) ?: return@runCatching null
            val root = JSONObject(response)
            val studiosArr = root.optJSONObject("data")?.optJSONObject("Page")?.optJSONArray("studios") ?: return@runCatching null
            val candidates = (0 until studiosArr.length()).mapNotNull { studiosArr.optJSONObject(it) }
            val expectedName = StudioSourceSupport.normalizeName(name)
            val studioObj = candidates.firstOrNull {
                StudioSourceSupport.normalizeName(it.optString("name")) == expectedName
            } ?: candidates.firstOrNull {
                StudioSourceSupport.namesMatch(name, it.optString("name"))
            } ?: return@runCatching null

            val studioId = studioObj.optInt("id")
            val studioName = studioObj.optString("name")
            val favorites = studioObj.optionalPositiveInt("favourites")
            val isAnimationStudio = studioObj.optBoolean("isAnimationStudio", true)

            val mediaWorks = mutableListOf<KitsugiStaffMediaWork>()
            val mediaObj = studioObj.optJSONObject("media")
            val nodes = mediaObj?.optJSONArray("nodes")
            if (nodes != null) {
                mediaWorks.addAll(parseAniListStudioNodes(nodes))
            }

            val worksHasMore = mediaObj?.optJSONObject("pageInfo")?.optBoolean("hasNextPage", false) == true
            val isFavourite = studioObj.optBoolean("isFavourite", false)
            KitsugiStudioDetail(
                id = studioId,
                name = studioName,
                isMain = isAnimationStudio,
                imageUrl = null,
                favorites = favorites,
                established = null,
                about = null,
                mediaWorks = mediaWorks.distinctBy { it.mediaId },
                isFavourite = isFavourite,
                aniListId = studioId,
                hasMoreWorks = worksHasMore
            )
        }.getOrNull()
    }
}

/** Stüdyo yapım listesinin sayfa boyutu: Jikan/Tenrai, Shikimori ve AniList için ortak üst sınır. */
private const val STUDIO_WORKS_PAGE_SIZE = 50

/**
 * Shikimori'de stüdyo logosu/kayıt adı aranırken bakılacak en fazla yapım sayısı.
 * Her aday ek bir `GET /api/animes/{id}` isteği demektir; 3, sayfa açılışını
 * belirgin biçimde geciktirmeden ortak yapımlarda da doğru satırı bulmaya yeter.
 */
private const val SHIKIMORI_STUDIO_LOOKUP_LIMIT = 3

/**
 * Sağlayıcılar arası ad kurtarmasında denenecek en fazla yazım. Her yazım en fazla bir
 * sağlayıcı araması demektir; 4, sayfa açılışını geciktirmeden yazım farklarını kapatır.
 */
private const val RECOVERY_NAME_LIMIT = 4
