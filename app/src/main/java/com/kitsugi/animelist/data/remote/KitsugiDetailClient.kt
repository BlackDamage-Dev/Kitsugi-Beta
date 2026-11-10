package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.kitsugi.animelist.utils.*

class KitsugiDetailClient {

    /**
     * Birincil detay zincirinin (kaynak + fallback'ler) toplam zaman tavanı.
     * Yavaş/askıda kalan istekler sayfanın önünü bloklamaz; eskimeş önbellek devreye girer.
     */
    private val PRIMARY_FETCH_TIMEOUT_MS = 45_000L

    /**
     * Kitsu özet temizliğinin (şüpheli özet → doğrulanmış kaynak) kendi zaman tavanı.
     * Birincil zincirin tavanından AYRI tutulur: temizleme yarıda kesilirse bozuk özet
     * yine de gösterilmez (özet boşaltılır), sayfa asla beklemez.
     */
    private val KITSU_SANITIZE_TIMEOUT_MS = 15_000L

    private suspend fun getTurkishMetadataFromTmdb(
        source: String,
        externalId: Int,
        mediaType: MediaType,
        providedTmdbId: Int? = null,
        providedRealMalId: Int? = null
    ): KitsugiMediaDetail? {
        if (mediaType == MediaType.Manga) return null

        val tmdbId = providedTmdbId ?: run {
            // Source'a göre doğru ID tipini belirle
            val malIdForResolve: Int? = when (MalJikanMediaSupport.canonicalSource(source)) {
                "simkl" -> providedRealMalId
                "jikan", "mal" -> externalId
                "kitsu" -> providedRealMalId
                // Bangumi stableId'si (500M+) MAL ID DEĞİLDİR; yalnızca çözülmüş gerçek MAL ID kullanılır.
                "bangumi" -> KitsugiBangumiDetailClient.sanitizeMalId(providedRealMalId)
                "anilist" -> {
                    // stableId < 100_000_000 → AniList arama sonucu MAL ID ile döndü
                    // Bu durumda MAL ID olarak çözümle, AniList ID olarak değil
                    if (externalId < 100_000_000) externalId else providedRealMalId
                }
                else -> null
            }
            val aniListIdForResolve: Int? = if (source.lowercase() == "anilist" && externalId >= 100_000_000) {
                // 100_000_000+ offset'li stableId → gerçek AniList ID'yi çıkar
                externalId - 100_000_000
            } else null
            val kitsuIdForResolve: Int? = if (source.lowercase() == "kitsu" && mediaType != MediaType.Manga) {
                // Yalnızca gerçek Kitsu stableId aralığındaki değer çözülür; altındaki
                // değerler MAL ID'dir ve kitsuId olarak asla yorumlanmamalıdır.
                KitsuIdNamespace.rawIdFromStable(externalId)
            } else null
            
            KitsugiIdResolver.resolveIds(
                malId = malIdForResolve,
                aniListId = aniListIdForResolve,
                tmdbId = providedTmdbId,
                mediaType = mediaType,
                kitsuId = kitsuIdForResolve
            ).tmdbId
        }

        if (tmdbId != null && tmdbId > 0) {
            val isMovie = mediaType == MediaType.Movie
            val firstTry = TmdbApiClient().fetchMediaDetail(tmdbId, isMovie)
            if (firstTry != null) return firstTry

            // Anime veya TMDB kaynaklı içeriklerde film/dizi ayrımı yanlış yapılmış olabilir.
            // İlk deneme null dönerse, diğer formatta tekrar çekmeyi dene (TV -> Movie veya Movie -> TV).
            if (mediaType != MediaType.Manga) {
                return TmdbApiClient().fetchMediaDetail(tmdbId, !isMovie)
            }
        }
        return null
    }

    /**
     * TMDB'den dönen detayın beklenen başlıkla uyuşup uyuşmadığını denetler.
     * Beklenen başlık boşsa doğrulama yapılamaz ve true döner (eski davranış korunur).
     */
    private fun tmdbTitleMatches(expectedTitle: String?, detail: KitsugiMediaDetail): Boolean {
        val expected = expectedTitle?.trim()?.lowercase().orEmpty()
        if (expected.isBlank()) return true
        val fetched = detail.title?.trim()?.lowercase().orEmpty()
        val fetchedEn = detail.titleEnglish?.trim()?.lowercase().orEmpty()
        val matchesTitle = { candidate: String ->
            candidate.isNotBlank() && (candidate.contains(expected) || expected.contains(candidate))
        }
        return matchesTitle(fetched) || matchesTitle(fetchedEn)
    }

    /**
     * Simkl kayıtları için TMDB tabanlı ayrıntı çözümü.
     *
     * Simkl API'si yavaş ve gecikmeli veri döndürdüğü için Simkl kütüphanesindeki
     * kayıtların ayrıntı sayfası önce TMDB üzerinden açılır:
     *  1. Bilinen/çözülen TMDB ID ile doğrudan TMDB,
     *  2. Olmazsa ters medya türüyle (film ↔ dizi) TMDB,
     *  3. TMDB ID hiç yoksa başlıkla TMDB araması.
     * Hiçbiri tutmazsa null döner; çağıran taraf MAL (Jikan) → Simkl zincirini dener.
     */
    private suspend fun fetchSimklDetailViaTmdb(
        tmdbId: Int?,
        mediaType: MediaType,
        title: String?
    ): KitsugiMediaDetail? {
        if (mediaType == MediaType.Manga) return null

        if (tmdbId != null && tmdbId > 0) {
            val isMovie = mediaType == MediaType.Movie
            TmdbApiClient().fetchMediaDetail(tmdbId, isMovie)?.let { return it }
            if (mediaType != MediaType.Manga) {
                // Ters tür yalnızca başlık tutarsa (aynı TMDB ID'si farklı yapımı gösterebilir)
                TmdbApiClient().fetchMediaDetail(tmdbId, !isMovie)
                    ?.takeIf { tmdbTitleMatches(title, it) }
                    ?.let { return it }
            }
        }

        // TMDB ID çözülemedi → başlıkla ara (dizi/film başlıkları TMDB'de en güvenilir sonucu verir)
        val searchTitle = title?.trim().orEmpty()
        if (searchTitle.isBlank()) return null

        val results = runCatching { TmdbApiClient().search(searchTitle) }.getOrNull().orEmpty()
        if (results.isEmpty()) return null

        // Beklenen tür önce denenir; anime kayıtları TMDB'de dizi ya da film olarak görünür.
        val preferredTypes = when (mediaType) {
            MediaType.Movie -> listOf(MediaType.Movie, MediaType.TvShow)
            MediaType.TvShow -> listOf(MediaType.TvShow, MediaType.Movie)
            else -> listOf(MediaType.TvShow, MediaType.Movie, MediaType.Anime)
        }

        for (type in preferredTypes) {
            val candidate = results.firstOrNull { it.type == type && (it.tmdbId ?: 0) > 0 } ?: continue
            TmdbApiClient().fetchMediaDetail(candidate.tmdbId!!, type == MediaType.Movie)?.let { return it }
        }
        return null
    }

    suspend fun fetchSynopsis(
        source: String,
        externalId: Int?,
        mediaType: MediaType
    ): String? {
        return withContext(Dispatchers.IO) {
            if (externalId == null || externalId <= 0) {
                return@withContext null
            }

            // 1. Önce kaynağın kendi özeti denenir (kaynak otoritesi).
            val canonical = MalJikanMediaSupport.canonicalSource(source)
            val sourceSynopsis = when (canonical) {
                "jikan", "mal" -> KitsugiMalDetailClient.fetchSynopsis(
                    malId = externalId,
                    mediaType = mediaType
                )

                "anilist" -> KitsugiAniListDetailClient.fetchSynopsis(
                    stableId = externalId,
                    mediaType = mediaType
                )

                "simkl" -> KitsugiSimklDetailClient.fetchSimklDetailDirect(externalId, mediaType)?.synopsis

                "tmdb" -> TmdbApiClient().fetchMediaDetail(externalId, mediaType == MediaType.Movie)?.synopsis

                "bangumi" -> KitsugiBangumiDetailClient.fetchDetail(externalId, mediaType)?.synopsis

                "kitsu" -> {
                    // Kitsu özeti ancak sağlıklıysa döner. Bozuk/alakasız özetler
                    // (örn. "ROAR" 2004 kaydındaki 1997 Fox TV dizisi özeti) asla
                    // gösterilmez — bu durumda null döner, UI "Açıklama bulunamadı" der.
                    val kitsuDetail = KitsuExploreClient.fetchDetailByStableId(externalId, mediaType)
                    val synopsis = kitsuDetail?.synopsis?.takeIf { it.isNotBlank() }
                    if (synopsis != null &&
                        KitsuSynopsisValidator.suspiciousReason(synopsis, kitsuDetail?.year, mediaType) != null
                    ) {
                        android.util.Log.w(
                            "KitsugiDetailClient",
                            "Kitsu fetchSynopsis: bozuk özet gösterilmedi ('${kitsuDetail?.title}', ${kitsuDetail?.year})"
                        )
                        null
                    } else {
                        synopsis
                    }
                }

                "shikimori" -> {
                    // Shikimori detayı özet taşır (Türkçeye çevrilir). Özet yoksa gerçek
                    // MAL ID'si çözülüp MAL/Jikan özeti denenir — Shikimori ID'si MAL
                    // ID'si yerine kullanılmaz.
                    val shiki = KitsugiShikimoriClient.fetchDetail(externalId, mediaType)
                    val shikiSynopsis = shiki?.synopsis?.takeIf { it.isNotBlank() }
                    if (shikiSynopsis != null) {
                        shikiSynopsis
                    } else {
                        val malId = shiki?.realMalId?.takeIf { it > 0 }
                            ?: KitsugiIdResolver.resolveMalIdFromShikimori(externalId)
                        if (malId != null && malId > 0) {
                            KitsugiMalDetailClient.fetchSynopsis(malId = malId, mediaType = mediaType)
                        } else null
                    }
                }

                else -> null
            }?.takeIf { it.isNotBlank() }

            if (sourceSynopsis != null) {
                return@withContext sourceSynopsis
            }

            // 2. Kaynakta özet yoksa ve manga değilse TMDB Türkçe özeti denenir.
            if (mediaType != MediaType.Manga) {
                val cachedCrossDetail = when {
                    source.equals("simkl", ignoreCase = true) -> DetailCache.getMediaDetail("simkl", externalId)
                    source.equals("bangumi", ignoreCase = true) -> DetailCache.getMediaDetail("bangumi", externalId)
                    else -> null
                }
                val trMeta = getTurkishMetadataFromTmdb(
                    source = source,
                    externalId = externalId,
                    mediaType = mediaType,
                    providedTmdbId = cachedCrossDetail?.tmdbId,
                    providedRealMalId = cachedCrossDetail?.realMalId
                )
                if (trMeta != null && !trMeta.synopsis.isNullOrBlank()) {
                    return@withContext trMeta.synopsis
                }
            }
            null
        }
    }

    /**
     * Birincil detayı çeker: kaynak istemcisi + fallback zinciri + Room önbelleği.
     *
     * TMDB zenginleştirmesi BURADA yapılmaz — o adım [enrichDetail] ile ayrı ve
     * (ViewModel tarafında) zaman tavanlı çalışır. Böylece detay sayfası asıl veriyle
     * HEMEN açılır; Türkçe/TMDB metaları arka planda gelince eklenir.
     */
    suspend fun fetchPrimaryDetail(
        source: String,
        externalId: Int?,
        mediaType: MediaType,
        tmdbId: Int? = null,
        realMalId: Int? = null,
        title: String? = null
    ): KitsugiMediaDetail? {
        // 2026-11-02: Kaynak başına boş bırakılan başlık alanları (ör. Simkl/TMDB'de
        // titleRomaji) akış ekranında daha AZ arama varyantı demekti. Tek çıkış
        // noktasında harmanlanır (bkz. MediaDetailTitleHarmonizer).
        val harmonizePrimary: (KitsugiMediaDetail?) -> KitsugiMediaDetail? = { it?.let(MediaDetailTitleHarmonizer::harmonize) }
        return harmonizePrimary(withContext(Dispatchers.IO) {
            // Kitsu kayıtlarında kimlik alanı boş olabilir (eski içe aktarmalar, kimlik onarımı
            // ya da hiç çözülememiş eşleme). Bu durumda detayı KAYIT BAŞLIĞINDAN çözmeye
            // çalışırız; kimlik yok diye sayfayı düşürmeyiz. Diğer kaynaklarda kimlik yoksa
            // güvenli bir çıkış yolu yoktur → eskisi gibi null.
            val hasUsableId = externalId != null && externalId > 0
            val isKitsuSource = source.lowercase() == "kitsu"
            if (!hasUsableId && !(isKitsuSource && !title.isNullOrBlank())) {
                return@withContext null
            }
            // Kimlik yoksa 0: aşağıda hiçbir yerde ham sayı Kitsu/MAL kimliği gibi
            // yorumlanmaz, yalnızca başlık çözümlemesi devreye girer.
            val extId: Int = externalId?.takeIf { it > 0 } ?: 0

            val context = com.kitsugi.animelist.KitsugiApplication.getInstance()?.applicationContext

            // Kitsu kayıtlarında kimlik alanı her zaman offset'li stableId taşımak zorunda.
            // Eski sürümlerde (veya MAL eşleşmesi bilinen kayıtlarda) bu alana gerçek MAL ID
            // yazıldığı için 35658 gibi bir MAL ID'si "Kitsu ID" diye yorumlanıp ALAKASIZ bir
            // yapımın ayrıntısı açılıyordu. Kimliği önce Kitsu uzayına kanonikleştiriyoruz.
            val kitsuCanonicalId = if (isKitsuSource) {
                KitsuIdNamespace.stableIdOrNull(extId)
                    ?: KitsuIdNamespace.resolveCanonicalStableId(
                        context = context,
                        storedId = null, // çözümleme realMalId/başlık üzerinden yapılsın
                        realMalId = realMalId ?: KitsuIdNamespace.realMalIdOf(extId),
                        isAnime = KitsuIdNamespace.isAnimeType(mediaType),
                        title = title
                    )
            } else {
                extId
            }

            val mediaTypeStr = mediaType.name.lowercase()
            val keyId = if (isKitsuSource) kitsuCanonicalId ?: extId else extId
            // Kimliği çözülememiş Kitsu kayıtlarında önbellek KULLANMIYORUZ: alan adı
            // (source + id) tek başına kimliği garanti etmiyor ve yanlış anahtar, yanlış veriyi
            // besleyebiliyor (gerçek hayatta görülen "alakasız veri" hatasının ikinci bacağı).
            val cacheKey = if (isKitsuSource && kitsuCanonicalId == null) {
                null
            } else {
                detailCacheKey(source, mediaTypeStr, keyId)
            }
            val legacyKey = if (cacheKey == null) {
                null
            } else if (source.lowercase() == "bangumi") {
                // Eski sürümler Bangumi kayıtlarını Kitsu başlık aramasıyla (alakasız/eksik veri)
                // önbelleğe yazıyordu; eski anahtar bilerek OKUNMAZ.
                null
            } else if (source.lowercase() == "kitsu") {
                // ks1: bozuk Kitsu özetleri temizlenmeye başlandı — eski (sürüm suffix'i
                // olmayan) anahtarlar bozuk özet taşıyabilir, bu yüzden bilerek OKUNMAZ.
                null
            } else if (source.lowercase() == "tmdb") {
                val typeStr = if (mediaType == MediaType.Movie) "movie" else "tv"
                "tmdb_${typeStr}_$extId"
            } else {
                "${source.lowercase()}_$keyId"
            }
            val db = context?.let { com.kitsugi.animelist.data.local.KitsugiDatabase.getDatabase(it) }
            val gson = com.google.gson.Gson()

            // 1. Fresh Cache Check (Room - 24 hours threshold)
            if (db != null && !cacheKey.isNullOrBlank()) {
                try {
                    val cached = db.persistentDetailCacheDao().getDetail(cacheKey)
                        // TMDB/Simkl için eski (sürümsüz) anahtarlar okunmaz: o kayıtlar
                        // düzeltme öncesi CJK başlıkları taşıyor olabilir ve 24 saat boyunca
                        // ekrana Japonca başlık düşmesine yol açardı.
                        ?: legacyKey?.takeIf { !MediaTitleResolver.isLatinPreferredSource(source) }
                            ?.let { db.persistentDetailCacheDao().getDetail(it) }
                    if (cached != null) {
                        val isFresh = (System.currentTimeMillis() - cached.cachedAtMs) < 24 * 60 * 60 * 1000L
                        if (isFresh) {
                            val detail = gson.fromJson(cached.detailJson, KitsugiMediaDetail::class.java)
                            if (detail != null) {
                                // Eski nsfw!="white" hatasından dolayı yanlışlıkla isAdult=true kalan içerikleri düzelt
                                val isActuallyAdult = detail.isAdult && (
                                    detail.rating?.contains("rx", ignoreCase = true) == true ||
                                    detail.rating?.contains("hentai", ignoreCase = true) == true ||
                                    detail.genres.any { it.contains("hentai", ignoreCase = true) }
                                )
                                val cleanDetail = if (detail.isAdult && !isActuallyAdult) {
                                    detail.copy(isAdult = false)
                                } else {
                                    detail
                                }

                                if (!title.isNullOrBlank() && !detail.title.isNullOrBlank() &&
                                    (source.lowercase() == "tmdb" || isKitsuSource)
                                ) {
                                    // TMDB ve Kitsu'da önbellek anahtarı yanlış kimlikten etkilenebildiği
                                    // için başlık doğrulaması yapıyoruz; uyuşmazsa önbelleği tamamen siliyor,
                                    // "geçersiz kılıp bir daha denemek"le uğraşmıyoruz.
                                    val orig = title.trim().lowercase()
                                    val dTitle = detail.title?.trim()?.lowercase().orEmpty()
                                    val dEnTitle = detail.titleEnglish?.trim()?.lowercase().orEmpty()
                                    val matches = dTitle.contains(orig) || orig.contains(dTitle) || dEnTitle.contains(orig) || orig.contains(dEnTitle)
                                    if (!matches) {
                                        android.util.Log.w("KitsugiDetailClient", "Cached $source detail title mismatch: expected '$title', got '${detail.title}'. Invalidating cache.")
                                        db.persistentDetailCacheDao().deleteDetail(cacheKey)
                                        legacyKey?.let { db.persistentDetailCacheDao().deleteDetail(it) }
                                        if (source.lowercase() == "tmdb") db.persistentDetailCacheDao().deleteDetail("tmdb_$extId")
                                    } else {
                                        android.util.Log.d("KitsugiDetailClient", "Serving fresh detail from Room cache for $cacheKey")
                                        return@withContext cleanDetail
                                    }
                                } else {
                                    android.util.Log.d("KitsugiDetailClient", "Serving fresh detail from Room cache for $cacheKey")
                                    return@withContext cleanDetail
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("KitsugiDetailClient", "Error reading detail cache: ${e.message}")
                }
            }

            // 2. Primary source fetch — toplam zaman tavanı: yavaş/hanging zincirler
            // (Jikan → ARM → TMDB → Kitsu ...) sayfanın önünü bloklamaz.
            val detail = withTimeoutOrNull(PRIMARY_FETCH_TIMEOUT_MS) { when (MalJikanMediaSupport.canonicalSource(source)) {
                "jikan", "mal" -> KitsugiMalDetailClient.fetchDetail(extId, mediaType)
                "shikimori" -> {
                    // NOT: externalId Shikimori ID'sidir — MAL ID'si olarak KULLANILMAZ!
                    // Shikimori başarısızsa gerçek MAL ID'si ARM ile çözülüp dene.
                    KitsugiShikimoriClient.fetchDetail(extId, mediaType)
                        ?: run {
                            val malId = KitsugiIdResolver.resolveMalIdFromShikimori(extId)
                            if (malId != null && malId > 0) KitsugiMalDetailClient.fetchDetail(malId, mediaType) else null
                        }
                }
                "anilist" -> {
                    val primaryAniList = KitsugiAniListDetailClient.fetchDetail(extId, mediaType)
                    if (primaryAniList != null) {
                        primaryAniList
                    } else {
                        val knownMalId = realMalId?.takeIf { it in 1..99_999_999 }
                            ?: extId.takeIf { it in 1..99_999_999 }
                        val rawAniListId = if (extId >= 100_000_000) extId - 100_000_000 else null
                        var fallbackDetail: KitsugiMediaDetail? = knownMalId?.let {
                            KitsugiMalDetailClient.fetchDetail(it, mediaType)
                        }
                        if (fallbackDetail == null && mediaType != MediaType.Manga && rawAniListId != null) {
                            val cross = runCatching {
                                KitsugiIdResolver.resolveIds(
                                    malId = knownMalId,
                                    aniListId = rawAniListId,
                                    mediaType = mediaType
                                )
                            }.getOrNull()
                            if (cross?.malId != null && cross.malId > 0) {
                                fallbackDetail = KitsugiMalDetailClient.fetchDetail(cross.malId, mediaType)
                            }
                            if (fallbackDetail == null && cross?.kitsuId != null && cross.kitsuId > 0) {
                                fallbackDetail = KitsuIdNamespace.stableIdFromRaw(cross.kitsuId)?.let { stableKitsuId ->
                                    KitsuExploreClient.fetchDetailByStableId(stableKitsuId, mediaType)
                                }
                            }
                            if (fallbackDetail == null && cross?.tmdbId != null && cross.tmdbId > 0) {
                                fallbackDetail = TmdbApiClient().fetchMediaDetail(
                                    cross.tmdbId,
                                    mediaType == MediaType.Movie
                                )
                            }
                        }
                        fallbackDetail
                    }
                }
                // Bangumi: yerel (v0 + p1) veri; MAL/AniList/TMDB ile birleştirme enrichDetail'de.
                "bangumi" -> KitsugiBangumiDetailClient.fetchDetail(extId, mediaType)
                // Kitsu keşfet fallback öğeleri: stableId = kitsuId + 300_000_000
                "kitsu" -> {
                    // Kanonik (300M aralığındaki) Kitsu stableId'si olmadan asla ham ID ile
                    // çekmeyiz — olmayan bir kimlikle çekmek alakasız bir yapımın verisini getirir.
                    val canonicalKitsuId = kitsuCanonicalId
                    android.util.Log.d(
                        "KitsugiDetailClient",
                        if (canonicalKitsuId != null) "Fetching Kitsu detail for stableId=$canonicalKitsuId"
                        else "Kitsu identity unresolved for stored id=$extId ('$title'); resolving by title"
                    )
                    val rawKitsuDetail = canonicalKitsuId?.let { KitsuExploreClient.fetchDetailByStableId(it, mediaType) }

                    // ── Yedek zinciri ────────────────────────────────────────────────────
                    // Kitsu kaydı veri döndürmediğinde (Kitsu'da silinmiş/18+ gizlenmiş kayıt,
                    // 404, eşleşmemiş kimlik) sayfa "yüklenemedi" ekranına düşmemelidir.
                    // Sıra: (a) kaydın/belleğin bildiği gerçek MAL ID → Jikan,
                    //       (b) sıkı başlık eşleşmeli MAL araması → Jikan,
                    //       (c) film/dizi ise başlıktan TMDB.
                    // Hiçbir adım başlığı doğrulanmamış bir yapımı kabul etmez.
                    var resolved: KitsugiMediaDetail? = rawKitsuDetail
                    val malIdFallback = realMalId?.takeIf { it in 1..99_999_999 }
                        ?: KitsuIdNamespace.realMalIdOf(extId)
                    if (resolved == null && malIdFallback != null) {
                        resolved = KitsugiMalDetailClient.fetchDetail(malIdFallback, mediaType)
                    }
                    if (resolved == null) {
                        resolved = fetchMalDetailByTitle(title, mediaType)
                    }
                    if (resolved == null) {
                        resolved = fetchTmdbDetailByTitle(title, mediaType)
                    }
                    // Bozuk/alakasız Kitsu özeti (örn. "ROAR" 2004 kaydı 1997 Fox TV dizisinin
                    // özetini taşıyor): şüpheli özeti doğrulanmış bir kaynakla değiştirir;
                    // doğrulanamazsa özet boşaltılır — asla alakasız özet gösterilmez.
                    resolved = sanitizeKitsuSynopsis(resolved, mediaType, malIdFallback, title, keyId)
                    val kitsuDetail = resolved
                    // AnimeThemes entegrasyonu: Kitsu ID'si ile tema müziklerini çek
                    if (kitsuDetail != null && rawKitsuDetail != null && mediaType != MediaType.Manga) {
                        val kitsuNumericId = KitsuIdNamespace.rawIdFromStable(canonicalKitsuId ?: extId)
                        if (kitsuNumericId != null && kitsuNumericId > 0) {
                            try {
                                val themes = KitsugiAnimeThemesClient.fetchAnimeThemes(kitsuNumericId, "Kitsu")
                                if (themes.first.isNotEmpty() || themes.second.isNotEmpty()) {
                                    android.util.Log.d("KitsugiDetailClient", "AnimeThemes Kitsu: ${themes.first.size} OP, ${themes.second.size} ED")
                                    kitsuDetail.copy(openings = themes.first, endings = themes.second)
                                } else kitsuDetail
                            } catch (e: Exception) {
                                android.util.Log.w("KitsugiDetailClient", "AnimeThemes Kitsu fetch failed: ${e.message}")
                                kitsuDetail
                            }
                        } else kitsuDetail
                    } else kitsuDetail
                }
                // TMDB discovery öğeleri: malId aslında tmdbId, doğrudan TMDB'den çek
                "tmdb" -> {
                    val effectiveTmdbId = tmdbId ?: extId
                    if (effectiveTmdbId > 0) {
                        val isMovie = mediaType == MediaType.Movie
                        val firstTry = TmdbApiClient().fetchMediaDetail(effectiveTmdbId, isMovie)
                        if (firstTry != null && tmdbTitleMatches(title, firstTry)) {
                            firstTry
                        } else {
                            // TMDB'de film ve dizi aynı sayısal ID'yi paylaşabilir (ör. Uncharted filmi
                            // ile 2026 dizisi). Ters tür YALNIZCA başlık gerçekten tutuyorsa kabul edilir;
                            // aksi halde filmin sayfasında alakasız dizinin verisi görünürdü.
                            android.util.Log.w("KitsugiDetailClient", "TMDB detail title mismatch or missing for tmdbId=$effectiveTmdbId (expected '$title'). Trying alternative type only with matching title...")
                            val secondTry = TmdbApiClient().fetchMediaDetail(effectiveTmdbId, !isMovie)
                                ?.takeIf { tmdbTitleMatches(title, it) }
                            secondTry ?: firstTry
                        }
                    } else null
                }
                "simkl" -> {
                    // Simkl API'sinden çapraz ID'ler (mal, tmdb, anilist, kitsu) çözümlenir;
                    // böylece TMDB detayı dönerken bile realMalId ve type korunur ve
                    // Ekip, İlişkiler, Grafikler, Yorumlar sekmeleri eksiksiz çalışır.
                    val simklCross = KitsugiSimklDetailClient.resolveSimklCrossIds(
                        simklId = extId,
                        mediaType = mediaType,
                        hintTmdbId = tmdbId,
                        hintMalId = realMalId
                    )
                    val malIdForResolve = realMalId?.takeIf { it > 0 && it != extId }
                        ?: simklCross.malId
                        ?: DetailCache.getMediaDetail("simkl", extId)?.realMalId
                    val resolvedTmdb = tmdbId?.takeIf { it > 0 }
                        ?: simklCross.tmdbId
                        ?: run {
                            KitsugiIdResolver.resolveIds(
                                malId = malIdForResolve,
                                aniListId = simklCross.aniListId,
                                tmdbId = null,
                                mediaType = mediaType
                            ).tmdbId
                        }

                    val tmdbDetail = fetchSimklDetailViaTmdb(
                        tmdbId = resolvedTmdb,
                        mediaType = mediaType,
                        title = title ?: simklCross.title
                    )
                    val resolvedFinalMalId = malIdForResolve ?: tmdbDetail?.tmdbId?.let { foundTmdb ->
                        KitsugiIdResolver.resolveIds(
                            malId = null,
                            aniListId = simklCross.aniListId,
                            tmdbId = foundTmdb,
                            mediaType = mediaType
                        ).malId
                    }
                    if (tmdbDetail != null) {
                        tmdbDetail.copy(
                            realMalId = resolvedFinalMalId ?: tmdbDetail.realMalId,
                            tmdbId = tmdbDetail.tmdbId ?: resolvedTmdb,
                            type = mediaType
                        )
                    } else if (mediaType == MediaType.Anime && resolvedFinalMalId != null && resolvedFinalMalId > 0) {
                        (KitsugiMalDetailClient.fetchDetail(resolvedFinalMalId, mediaType)
                            ?: KitsugiSimklDetailClient.fetchSimklDetailDirect(extId, mediaType))?.let { det ->
                            det.copy(
                                realMalId = resolvedFinalMalId ?: det.realMalId,
                                tmdbId = resolvedTmdb ?: det.tmdbId,
                                type = mediaType
                            )
                        }
                    } else {
                        KitsugiSimklDetailClient.fetchSimklDetailDirect(extId, mediaType)?.let { simklDet ->
                            simklDet.copy(
                                realMalId = resolvedFinalMalId ?: simklDet.realMalId,
                                tmdbId = resolvedTmdb ?: simklDet.tmdbId,
                                type = mediaType
                            )
                        }
                    }
                }
                else -> null
            } }

            var finalDetail = detail
            // Yedek zincirde Kitsu'dan gelen detayların bozuk özet taşıyıp taşımadığını
            // sonradan kontrol edebilmek için işaret.
            var detailFromKitsuFallback = false

            // 3. Fallback Client Chains (Live Backups) — aynı tavanla sınırlı
            if (finalDetail == null && !isKitsuSource) {
                withTimeoutOrNull(PRIMARY_FETCH_TIMEOUT_MS) {
                    if (mediaType == MediaType.Movie || mediaType == MediaType.TvShow) {
                        // TMDB Fallback: TVmaze for TV Shows
                        if (mediaType == MediaType.TvShow && !title.isNullOrBlank()) {
                            android.util.Log.d("KitsugiDetailClient", "TMDB returned null. Trying TVmaze fallback for TV show: $title")
                            finalDetail = TvMazeClient.fetchShowDetailByTitle(title)
                        }
                        // TMDB Fallback: Search fallback by title
                        if (finalDetail == null && !title.isNullOrBlank()) {
                            android.util.Log.d("KitsugiDetailClient", "Trying direct TMDB search fallback for: $title")
                            val searchResults = TmdbApiClient().search(title)
                            val matchedResult = searchResults.firstOrNull { it.type == mediaType }
                            if (matchedResult != null && matchedResult.tmdbId != null && matchedResult.tmdbId > 0) {
                                finalDetail = TmdbApiClient().fetchMediaDetail(matchedResult.tmdbId, mediaType == MediaType.Movie)
                            }
                        }
                    } else if (mediaType == MediaType.Anime) {
                        // Anime Fallback: Kitsu
                        android.util.Log.d("KitsugiDetailClient", "AniList/MAL detail returned null. Trying Kitsu fallback.")
                        val kitsuId = if (db != null) {
                            val resolvedEntity = if (source.lowercase() == "anilist") {
                                val rawAniId = if (extId >= 100_000_000) extId - 100_000_000 else extId
                                db.mediaMetaCacheDao().getByAniListId(rawAniId)
                                    ?: db.mediaMetaCacheDao().getByAniListId(extId)
                            } else {
                                db.mediaMetaCacheDao().getByMalId(extId)
                            }
                            resolvedEntity?.kitsuId
                        } else null

                        if (!kitsuId.isNullOrBlank()) {
                            android.util.Log.d("KitsugiDetailClient", "Fetching Kitsu detail via resolved kitsuId: $kitsuId")
                            finalDetail = KitsuClient.fetchAnimeDetail(kitsuId)
                            detailFromKitsuFallback = finalDetail != null
                        }
                        if (finalDetail == null && !title.isNullOrBlank()) {
                            android.util.Log.d("KitsugiDetailClient", "Fetching Kitsu detail via title search: $title")
                            finalDetail = KitsuClient.fetchAnimeDetailByTitle(title)
                            detailFromKitsuFallback = finalDetail != null
                        }
                        if (finalDetail == null && !title.isNullOrBlank()) {
                            runCatching {
                                val simklResults = SimklApiClient().search(title, type = "anime", limit = 1)
                                val matched = simklResults.firstOrNull()
                                if (matched != null && matched.malId > 0) {
                                    finalDetail = KitsugiSimklDetailClient.fetchSimklDetailDirect(matched.malId, mediaType)
                                }
                            }
                        }
                        if (finalDetail == null && !title.isNullOrBlank()) {
                            runCatching {
                                val jikanResults = JikanApiClient().search(title, MediaType.Anime)
                                val matched = jikanResults.firstOrNull()
                                if (matched != null && matched.malId > 0) {
                                    finalDetail = KitsugiMalDetailClient.fetchDetail(matched.malId, mediaType)
                                }
                            }
                        }
                    } else if (mediaType == MediaType.Manga) {
                        // Manga / Manhwa Fallback: Jikan (MAL) başlık eşleşmesi → Kitsu manga yedeği
                        android.util.Log.d("KitsugiDetailClient", "AniList/MAL manga detail returned null. Trying MAL/Kitsu manga fallback.")
                        val knownMalId = realMalId?.takeIf { it in 1..99_999_999 }
                        if (knownMalId != null) {
                            finalDetail = KitsugiMalDetailClient.fetchDetail(knownMalId, MediaType.Manga)
                        }
                        if (finalDetail == null && !title.isNullOrBlank()) {
                            finalDetail = fetchMalDetailByTitle(title, MediaType.Manga)
                        }
                        if (finalDetail == null && !title.isNullOrBlank()) {
                            finalDetail = fetchKitsuDetailByTitle(title, MediaType.Manga)
                            detailFromKitsuFallback = finalDetail != null
                        }
                    }
                }
            }

            // 3b. Yedek zincir Kitsu'dan geldiyse ve özet şüpheliyse temizle.
            // (AniList/MAL birincil veri vermediyse Kitsu yedeği devreye girer; o kayıt
            // da bozuk/alakasız özet taşıyabilir — örn. başlık aramasıyla "ROAR".)
            if (detailFromKitsuFallback) {
                val current = finalDetail
                if (current != null &&
                    KitsuSynopsisValidator.suspiciousReason(current.synopsis, current.year, mediaType) != null
                ) {
                    finalDetail = withTimeoutOrNull(KITSU_SANITIZE_TIMEOUT_MS) {
                        sanitizeKitsuSynopsis(current, mediaType, realMalId, title, keyId, source)
                    } ?: current.copy(synopsis = null)
                }
            }

            // 4. Stale Cache Fallback (If all network attempts returned null, check cache again even if expired)
            if (finalDetail == null && db != null && !cacheKey.isNullOrBlank()) {
                try {
                    val cached = db.persistentDetailCacheDao().getDetail(cacheKey)
                    if (cached != null) {
                        finalDetail = gson.fromJson(cached.detailJson, KitsugiMediaDetail::class.java)
                        if (finalDetail != null) {
                            android.util.Log.d("KitsugiDetailClient", "Serving stale detail from Room cache for $cacheKey")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("KitsugiDetailClient", "Error reading stale cache: ${e.message}")
                }
            }

            // 5. Cache update on success
            val currentFinal = finalDetail?.let {
                if (it.type == null) it.copy(type = mediaType) else it
            }
            finalDetail = currentFinal
            if (currentFinal != null && !cacheKey.isNullOrBlank()) {
                saveToRoomCache(source, mediaType, keyId, currentFinal)
            }

            finalDetail
        })
    }


    /**
     * Kitsu kaydının özeti bozuksa düzeltir.
     *
     * Kitsu'de vandalize edilmiş/hatalı birleştirilmiş kayıtlar (örn. "ROAR" 2004 — özet
     * 1997 Fox TV dizisini anlatıyor) bambaşka bir yapımın özetini taşıyabilir. Bu kayıtlar
     * olduğu gibi gösterilmez:
     *  1. Özet sağlıklıysa → dokunulmaz.
     *  2. Şüpheliyse → MAL/Jikan'dan temiz özet aranır: önce kaydın gerçek MAL ID'si,
     *     sonra sıkı başlık eşleşmesi. Aday özet de şüpheli ise ya da başlık bu kayıtla
     *     akraba değilse (kimlik karışıklığı) reddedilir.
     *  3. Doğrulanmış temiz özet bulunursa → yer değiştirir; bulunamazsa özet boşaltılır
     *     (UI "Açıklama bulunamadı" gösterir — alakasız bir özeti göstermekten iyidir).
     */
    private suspend fun sanitizeKitsuSynopsis(
        detail: KitsugiMediaDetail?,
        mediaType: MediaType,
        realMalId: Int?,
        title: String?,
        cacheId: Int,
        cacheSource: String = "kitsu"
    ): KitsugiMediaDetail? {
        if (detail == null) return null
        val reason = KitsuSynopsisValidator.suspiciousReason(detail.synopsis, detail.year, mediaType)
        if (reason == null) return detail
        android.util.Log.w(
            "KitsugiDetailClient",
            "Kitsu bozuk özet tespit edildi ('${detail.title}', ${detail.year}): $reason — düzeltme deneniyor"
        )
        // Bozuk özetin eski (otomatik) çevirisi de önbellekte kalmış olabilir — temizle.
        DetailCache.removeTranslation("synopsis", cacheSource, cacheId)

        // Adaylar: (a) gerçek MAL ID ile Jikan, (b) sıkı başlık eşleşmesi ile Jikan.
        val candidates = mutableListOf<KitsugiMediaDetail>()
        val malId = realMalId?.takeIf { it in 1..99_999_999 }
        if (malId != null) {
            runCatching { KitsugiMalDetailClient.fetchDetail(malId, mediaType) }
                .getOrNull()?.let { candidates.add(it) }
        }
        if (candidates.isEmpty() && !title.isNullOrBlank()) {
            runCatching { fetchMalDetailByTitle(title, mediaType) }
                .getOrNull()?.let { candidates.add(it) }
        }

        val detailTitles = listOfNotNull(detail.title, detail.titleEnglish, detail.titleRomaji, detail.titleJapanese)
        for (candidate in candidates) {
            val candSynopsis = candidate.synopsis?.takeIf { it.isNotBlank() } ?: continue
            // Aday özet de şüpheli mi? (MAL'deki kayıt da aynı yabancı diziyi anlatıyor olabilir.)
            if (KitsuSynopsisValidator.suspiciousReason(candSynopsis, candidate.year, mediaType) != null) continue
            // Kimlik karışıklığı olmasın: aday başlık bu kayıtla akraba olmalı.
            val candTitles = listOfNotNull(candidate.title, candidate.titleEnglish, candidate.titleRomaji, candidate.titleJapanese)
            val related = detailTitles.isNotEmpty() && candTitles.isNotEmpty() &&
                com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.titlesLookRelated(detailTitles, candTitles, strict = true)
            if (!related) continue
            android.util.Log.i(
                "KitsugiDetailClient",
                "Kitsu özeti doğrulanmış kaynakla değiştirildi: '${detail.title}' ← '${candidate.title}' (MAL)"
            )
            return detail.copy(synopsis = candSynopsis)
        }

        android.util.Log.w(
            "KitsugiDetailClient",
            "Kitsu bozuk özeti doğrulanamadı; özet boşaltıldı ('${detail.title}')"
        )
        return detail.copy(synopsis = null)
    }

    /**
     * MAL (Jikan) başlık araması: yalnızca başlığı **doğrulanan** ilk sonucu kabul eder.
     *
     * Kitsu gibi birincil kaynağı veri döndürmeyen kayıtlar (Kitsu'da silinmiş ya da
     * gizlenmiş 18+ yapımlar) için güvenli yedektir; başlığı tutmayan hiçbir sonucu
     * kabul etmediği için kullanıcıya asla alakasız bir yapım göstermez.
     */
    private suspend fun fetchMalDetailByTitle(title: String?, mediaType: MediaType): KitsugiMediaDetail? {
        val query = title?.trim().orEmpty()
        if (query.isBlank()) return null
        val candidates = runCatching {
            JikanApiClient().searchMALOnly(query, mediaType, showAdultContent = true)
        }.getOrNull().orEmpty()
        if (candidates.isEmpty()) return null
        val match = candidates.firstOrNull { candidate ->
            com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.titlesLookRelated(
                listOfNotNull(candidate.title, candidate.titleEnglish, candidate.titleJapanese),
                listOf(query),
                strict = true
            )
        } ?: return null
        android.util.Log.d(
            "KitsugiDetailClient",
            "MAL başlık yedeği: '$query' → MAL ${match.malId} ('${match.title}')"
        )
        return KitsugiMalDetailClient.fetchDetail(match.malId, mediaType)
    }

    /**
     * Kitsu başlık araması (özellikle MAL'de kaydı olmayan Manhwa/Webtoon ve AniList kesintileri için).
     * Yalnızca sıkı başlık doğrulamasından geçen ilk yapım kabul edilir.
     */
    private suspend fun fetchKitsuDetailByTitle(title: String?, mediaType: MediaType): KitsugiMediaDetail? {
        val query = title?.trim().orEmpty()
        if (query.isBlank()) return null
        val candidates = runCatching {
            KitsuExploreClient.searchAnime(query, mediaType, limit = 8)
        }.getOrNull().orEmpty()
        if (candidates.isEmpty()) return null
        val match = candidates.firstOrNull { candidate ->
            com.kitsugi.animelist.data.auth.CrossSyncIdentityGuard.titlesLookRelated(
                listOfNotNull(candidate.title, candidate.titleEnglish, candidate.titleJapanese),
                listOf(query),
                strict = true
            )
        } ?: return null
        android.util.Log.d(
            "KitsugiDetailClient",
            "Kitsu başlık yedeği: '$query' → Kitsu ${match.malId} ('${match.title}')"
        )
        return KitsuExploreClient.fetchDetailByStableId(match.malId, mediaType)
    }

    /**
     * TMDB başlık araması (yalnızca film/dizi) — medya türü eşleşen ilk sonuç kullanılır.
     * Kaynak verisi tükenmiş film/dizi kayıtlarında sayfanın boş kalmamasını sağlar.
     */
    private suspend fun fetchTmdbDetailByTitle(title: String?, mediaType: MediaType): KitsugiMediaDetail? {
        if (mediaType != MediaType.Movie && mediaType != MediaType.TvShow) return null
        val query = title?.trim().orEmpty()
        if (query.isBlank()) return null
        val candidate = runCatching { TmdbApiClient().search(query) }.getOrNull().orEmpty()
            .firstOrNull { it.type == mediaType && (it.tmdbId ?: 0) > 0 } ?: return null
        val tmdbId = candidate.tmdbId ?: return null
        return runCatching {
            TmdbApiClient().fetchMediaDetail(tmdbId, mediaType == MediaType.Movie)
        }.getOrNull()
    }

    /**
     * TMDB zenginleştirmesi (TR meta, görseller, puanlar) + next-airing çözümü.
     *
     * Birincil detaydan SONRA çağrılır; ApiResultDetailViewModel bu adımı zaman
     * tavanıyla (~20 sn) çalıştırır. Başarısızsa/timeout'ta [detail] aynen döner —
     * sayfa asla asıl veriye kavuşmamış durumuna düşmez.
     */
    suspend fun enrichDetail(
        source: String,
        externalId: Int?,
        mediaType: MediaType,
        detail: KitsugiMediaDetail,
        tmdbId: Int? = null,
        realMalId: Int? = null,
        title: String? = null
    ): KitsugiMediaDetail? = withContext(Dispatchers.IO) {
        val isBangumi = source.equals("bangumi", ignoreCase = true)
        // Bangumi: çapraz kimlik çözümü (AniList → MAL → ARM) + MAL/AniList/TMDB birleştirmesi.
        // Bu adım ViewModel tarafında zaman tavanlıdır; başarısız olursa yerel detay aynen kalır.
        val baseDetail = if (isBangumi && externalId != null && externalId > 0) {
            runCatching { KitsugiBangumiDetailClient.enrich(externalId, mediaType, detail) }.getOrDefault(detail)
        } else {
            detail
        }
        // Bangumi zenginleştirmesi gerçekten çalıştıysa altı kaynağın tamamı orada toplandı;
        // aşağıdaki toplama tekrarlanmaz (20 sn bütçesi ikiye bölünmesin).
        val bangumiEnriched = isBangumi && baseDetail !== detail
        if (externalId == null || externalId <= 0) {
            return@withContext baseDetail.copy(
                tags = KitsugiSpoilerRules.applySpoilerFlags(baseDetail.tags)
            )
        }

        val currentDetail = baseDetail.copy(
            tags = KitsugiSpoilerRules.applySpoilerFlags(baseDetail.tags)
        )

        // Bangumi stableId'si (500M+) gerçek MAL ID değildir; çağıranın verdiği değer süzülür.
        val safeRealMalId = if (isBangumi) KitsugiBangumiDetailClient.sanitizeMalId(realMalId) else realMalId

        // TMDB zenginleştirmesi (yalnızca Manga dışı türler için)
        val simklCrossForEnrich = if (mediaType != MediaType.Manga && source.equals("simkl", ignoreCase = true) && externalId > 0) {
            runCatching {
                KitsugiSimklDetailClient.resolveSimklCrossIds(
                    simklId = externalId,
                    mediaType = mediaType,
                    hintTmdbId = tmdbId ?: currentDetail.tmdbId,
                    hintMalId = safeRealMalId ?: currentDetail.realMalId
                )
            }.getOrNull()
        } else null

        val effectiveRealMalId = safeRealMalId
            ?: currentDetail.realMalId
            ?: simklCrossForEnrich?.malId
            ?: if (source.lowercase() == "anilist" && externalId < 100_000_000) externalId else null

        var resolvedTmdbId = if (mediaType != MediaType.Manga) {
            tmdbId ?: currentDetail.tmdbId ?: simklCrossForEnrich?.tmdbId
        } else null

        // Fallback scenario 2: Primary detail is not null, but tmdbId is missing -> Try direct TMDB search fallback by title!
        if ((resolvedTmdbId == null || resolvedTmdbId <= 0) && (mediaType == MediaType.Movie || mediaType == MediaType.TvShow)) {
            val searchTitle = title ?: currentDetail.title ?: currentDetail.titleEnglish
            if (!searchTitle.isNullOrBlank()) {
                android.util.Log.d("KitsugiDetailClient", "Primary resolution has no tmdbId. Triggering TMDB search for title: $searchTitle")
                val searchResults = TmdbApiClient().search(searchTitle)
                val matchedResult = searchResults.firstOrNull { it.type == mediaType }
                if (matchedResult != null && matchedResult.tmdbId != null && matchedResult.tmdbId > 0) {
                    resolvedTmdbId = matchedResult.tmdbId
                    android.util.Log.d("KitsugiDetailClient", "Resolved tmdbId = $resolvedTmdbId via search for title: $searchTitle")
                }
            }
        }

        val trMeta = if (mediaType != MediaType.Manga) {
            getTurkishMetadataFromTmdb(source, externalId, mediaType, resolvedTmdbId, effectiveRealMalId)
        } else null
        var mergedDetail = if (trMeta != null) {
            // TMDB özeti artık yalnızca kaynağın özeti **yoksa** kullanılır; dilli çeviri
            // zaten otomatik çeviri katmanında yapılır.
            val sourceSynopsis = currentDetail.synopsis?.takeIf { it.isNotBlank() }
            val updatedSynopsis = sourceSynopsis
                ?: trMeta.synopsis?.takeIf { it.isNotBlank() }
            // Kaynak Otoritesi Kuralı (Source Authority Preservation):
            // Birincil kaynağın başlıkları, türleri, stüdyoları ve kapak görseli her zaman önceliklidir!
            val updatedTitle = if (!currentDetail.title.isNullOrBlank()) currentDetail.title else trMeta.title
            val updatedTitleEnglish = if (!currentDetail.titleEnglish.isNullOrBlank()) currentDetail.titleEnglish else trMeta.titleEnglish
            // Türler de BİRLEŞTİRİLİR (yerine koyma yok): kaynak türleri + TMDB türleri.
            val updatedGenres = KitsugiTaxonomyMerge.mergeGenres(currentDetail.genres, trMeta.genres)
            val updatedTags = KitsugiSpoilerRules.mergeTagsPreservingSpoilers(currentDetail.tags, trMeta.tags)
            val combinedPictures = (currentDetail.pictures.orEmpty() + trMeta.pictures.orEmpty()).distinct()
            // Kurumlar da BİRLEŞTİRİLİR: MAL stüdyoları + AniList stüdyoları + TMDB yapım
            // şirketleri/kanalları tek listede. Aynı kurum Latin adına göre tekilleşir;
            // kimliği olan (tıklanabilir) kayıt tercih edilir.
            val mergedStudios = KitsugiTaxonomyMerge.mergeOrgs(currentDetail.studios, trMeta.studios)
            // Rol duyarlı birleştirme: TMDB şirketi aynı zamanda MAL stüdyosu olsa da
            // yapımcı kaydı korunur (kurum hiçbir rolde eksik kalmaz).
            val mergedProducers = KitsugiTaxonomyMerge.mergeOrgs(
                currentDetail.producers,
                trMeta.producers
            )
            val mergedNetworks = KitsugiTaxonomyMerge.mergeOrgs(currentDetail.networks, trMeta.networks)
            val mergedRating = if (!currentDetail.rating.isNullOrBlank()) currentDetail.rating else trMeta.rating
            val updatedImageUrl = if (!currentDetail.imageUrl.isNullOrBlank()) currentDetail.imageUrl else trMeta.imageUrl
            currentDetail.copy(
                synopsis = updatedSynopsis,
                title = updatedTitle,
                titleEnglish = updatedTitleEnglish,
                genres = updatedGenres,
                tags = updatedTags,
                tmdbId = resolvedTmdbId ?: currentDetail.tmdbId,
                realMalId = effectiveRealMalId ?: currentDetail.realMalId ?: trMeta.realMalId,
                type = currentDetail.type ?: mediaType,
                imageUrl = updatedImageUrl,
                pictures = combinedPictures,
                studios = mergedStudios,
                producers = mergedProducers,
                networks = mergedNetworks,
                rating = mergedRating,
                totalSeasons = if (source.lowercase() == "tmdb" || source.lowercase() == "simkl") {
                    trMeta.totalSeasons ?: currentDetail.totalSeasons
                } else {
                    currentDetail.totalSeasons ?: 1
                },
                meanScore = currentDetail.meanScore ?: trMeta.meanScore,
                averageScore = currentDetail.averageScore ?: trMeta.averageScore,
                popularity = currentDetail.popularity ?: trMeta.popularity,
                favorites = currentDetail.favorites ?: trMeta.favorites,
                rank = currentDetail.rank ?: trMeta.rank,
                popularityRank = currentDetail.popularityRank ?: trMeta.popularityRank,
                scoredBy = currentDetail.scoredBy ?: trMeta.scoredBy,
                members = currentDetail.members ?: trMeta.members,
                nextAiringEpisode = currentDetail.nextAiringEpisode ?: trMeta.nextAiringEpisode
            )
        } else {
            currentDetail.copy(
                tmdbId = resolvedTmdbId ?: currentDetail.tmdbId,
                realMalId = effectiveRealMalId ?: currentDetail.realMalId,
                type = currentDetail.type ?: mediaType
            )
        }

        // Tüm kaynaklarda (MAL/Jikan, Shikimori, Kitsu, Bangumi, Simkl, TMDB vb.) AniList'teki
        // spoiler etiketlerini (`isMediaSpoiler`, `isGeneralSpoiler`, `rank`) ve varsa
        // `nextAiringEpisode` bilgisini çözüp birleştir.
        val canonicalSrc = MalJikanMediaSupport.canonicalSource(source)

        // ── Çapraz kaynak kimlikleri (tür/etiket/kurum birleşimi için de gerekli) ──────
        val malIdForResolve = when (canonicalSrc) {
            "simkl" -> safeRealMalId ?: mergedDetail.realMalId ?: simklCrossForEnrich?.malId
            "jikan", "mal" -> externalId.takeIf { it in 1..99_999_999 } ?: mergedDetail.realMalId
            "anilist" -> if (externalId < 100_000_000) externalId else safeRealMalId ?: mergedDetail.realMalId
            "kitsu" -> safeRealMalId ?: mergedDetail.realMalId
            "bangumi" -> mergedDetail.realMalId
            // Shikimori kimlik uzayı MAL DEĞİLDİR — önce gerçek MAL kimliğine çözümlenir.
            "shikimori" -> mergedDetail.realMalId
                ?: runCatching { KitsugiIdResolver.resolveMalIdFromShikimori(externalId) }.getOrNull()
            "tmdb" -> safeRealMalId ?: mergedDetail.realMalId
            else -> safeRealMalId ?: mergedDetail.realMalId
        }
        val kitsuRawId = if (canonicalSrc == "kitsu") KitsuIdNamespace.rawIdFromStable(externalId) else null
        val resolvedIds = runCatching {
            KitsugiIdResolver.resolveIds(
                malId = malIdForResolve,
                aniListId = simklCrossForEnrich?.aniListId,
                tmdbId = mergedDetail.tmdbId,
                mediaType = mediaType,
                kitsuId = kitsuRawId
            )
        }.getOrNull()
        val resolvedAniListId = simklCrossForEnrich?.aniListId ?: resolvedIds?.aniListId
        val resolvedMalId = malIdForResolve ?: resolvedIds?.malId

        val hasAniListRankedTags = canonicalSrc == "anilist" || mergedDetail.tags.any { it.source == "anilist" }
        val needsAniListEnrich = !hasAniListRankedTags || (mediaType != MediaType.Manga && mergedDetail.nextAiringEpisode == null)
        val alInfo = runCatching {
            val fallbackSearchTitle = if (resolvedAniListId == null && resolvedMalId == null &&
                (mediaType == MediaType.Anime || mediaType == MediaType.Manga)
            ) {
                mergedDetail.titleEnglish ?: mergedDetail.titleRomaji ?: mergedDetail.title ?: title
            } else null
            if (needsAniListEnrich || resolvedAniListId != null || resolvedMalId != null) {
                KitsugiAniListDetailClient.fetchTagsAndAiringInfo(
                    aniListId = resolvedAniListId,
                    malId = resolvedMalId,
                    searchTitle = if (needsAniListEnrich) fallbackSearchTitle else null,
                    mediaType = mediaType
                )
            } else {
                null
            }
        }.getOrNull()

        if (alInfo != null) {
            val enrichedTags = if (alInfo.tags.isNotEmpty()) {
                KitsugiSpoilerRules.mergeTagsPreservingSpoilers(mergedDetail.tags, alInfo.tags, limit = 600)
            } else {
                KitsugiSpoilerRules.applySpoilerFlags(mergedDetail.tags)
            }
            mergedDetail = mergedDetail.copy(
                tags = enrichedTags,
                realMalId = mergedDetail.realMalId ?: alInfo.malId ?: resolvedMalId,
                nextAiringEpisode = mergedDetail.nextAiringEpisode ?: alInfo.nextAiringEpisode
            )
        }

        // ══════════════════════════════════════════════════════════════════════════
        // TÜM KAYNAKLARIN TÜR / ETİKET / KURUM BİRLEŞİMİ
        // ══════════════════════════════════════════════════════════════════════════
        // Eskiden birincil kaynakta dolu olan alan ikincil kaynağın verisini TAMAMEN
        // eziyordu (`if (dir.genres.isNotEmpty()) dir.genres else other.genres`). Artık
        // birleşim alınıyor: Bangumi'nin Çince etiketleri + MAL türleri + AniList etiketleri
        // + TMDB anahtar kelimeleri + AniList stüdyoları tek listede toplanıyor.
        // Zaman tavanlıdır; geciken kaynak atlanır ve sayfa asıl veriyle açılır.
        // Bangumi sayfasında bu toplama zaten `KitsugiBangumiDetailClient.enrich` içinde
        // yapıldı (6 kaynak). İkinci kez çağırmak 20 sn'lik bütçeyi ikiye böler.
        mergedDetail = if (bangumiEnriched) mergedDetail else runCatching {
            val taxonomy = KitsugiTaxonomySources.collect(
                malId = resolvedMalId ?: mergedDetail.realMalId,
                aniListId = resolvedAniListId,
                mediaType = mediaType,
                tmdbId = mergedDetail.tmdbId,
                titles = listOf(
                    mergedDetail.title,
                    mergedDetail.titleEnglish,
                    mergedDetail.titleRomaji,
                    mergedDetail.titleNative,
                    title
                ) + mergedDetail.synonyms,
                year = mergedDetail.year
            )
            val withAniList = if (alInfo != null && (alInfo.genres.isNotEmpty() || alInfo.studios.isNotEmpty())) {
                KitsugiTaxonomyMerge.mergeDetail(
                    primary = mergedDetail,
                    secondary = KitsugiMediaDetail(
                        synopsis = null,
                        genres = alInfo.genres,
                        studios = alInfo.studios
                    )
                )
            } else {
                mergedDetail
            }
            if (taxonomy.isEmpty) {
                withAniList
            } else {
                KitsugiTaxonomyMerge.mergeDetail(
                    primary = withAniList,
                    secondary = taxonomy.toDetail()
                )
            }
        }.getOrDefault(mergedDetail)

        // Kurum adları gösterim için normalleştirilir (Rusça/Çince ad → Latin ad) ve
        // yapımcı listesinde zaten stüdyo olan kurumlar tekrar etmez.
        mergedDetail = runCatching {
            val studios = KitsugiTaxonomyMerge.normalizeOrgNames(mergedDetail.studios)
            // Rol duyarlı tekilleştirme: stüdyo listesinde görünen bir şirket
            // yapımcı listesinden SİLİNMEZ (bkz. KitsugiTaxonomyMerge.orgDedupKey).
            val producers = KitsugiTaxonomyMerge.normalizeOrgNames(mergedDetail.producers)
            mergedDetail.copy(
                studios = studios,
                producers = producers,
                networks = KitsugiTaxonomyMerge.normalizeOrgNames(mergedDetail.networks),
                serializations = KitsugiTaxonomyMerge.normalizeOrgNames(mergedDetail.serializations)
            )
        }.getOrDefault(mergedDetail)

        // Cihaz doğrulaması: birleşik sonuçtaki gerçek sayılar (kaynak başına dağılım
        // KitsugiTaxonomySources tarafından ayrıca basılır).
        android.util.Log.d(
            "KitsugiDetailClient",
            "Birleşik taksonomi: tür=${mergedDetail.genres.size} etiket=${mergedDetail.tags.size} " +
                "stüdyo=${mergedDetail.studios.size} yapımcı=${mergedDetail.producers.size} " +
                "kanal=${mergedDetail.networks.size} dergi=${mergedDetail.serializations.size}"
        )

        val isKitsuSource = source.lowercase() == "kitsu"
        val keyId = if (isKitsuSource) {
            KitsuIdNamespace.stableIdOrNull(externalId) ?: externalId
        } else {
            externalId
        }
        // Aynı harmonizasyon: hangi kaynaktan gelirse gelsin akış ekranı tam başlık kümesini görsün.
        val harmonized = MediaDetailTitleHarmonizer.harmonize(mergedDetail)
        saveToRoomCache(source, mediaType, keyId, harmonized)
        harmonized
    }

    /**
     * Eski davranışı koruyan tam akış (TV / entry detay sayfası): birincil detay +
     * TMDB zenginleştirmesi tek çağrıda. Kademeli akış için
     * [fetchPrimaryDetail] + [enrichDetail] ayrımı kullanılır.
     */
    suspend fun fetchDetail(
        source: String,
        externalId: Int?,
        mediaType: MediaType,
        tmdbId: Int? = null,
        realMalId: Int? = null,
        title: String? = null
    ): KitsugiMediaDetail? {
        val primary = fetchPrimaryDetail(source, externalId, mediaType, tmdbId, realMalId, title) ?: return null
        return runCatching {
            enrichDetail(source, externalId, mediaType, primary, tmdbId, realMalId, title)
        }.getOrNull() ?: primary
    }

    /**
     * Detay önbelleği anahtarı.
     *
     * TMDB/Simkl kayıtlarında anahtara [MediaTitleResolver.VERSION] eklenir: böylece
     * başlık çözümleme mantığı düzeltildiğinde eski (Japonça başlık taşıyan)
     * kayıtlar 24 saatlik tazelik penceresi boyunca servis edilmez.
     */
    private fun detailCacheKey(source: String, mediaTypeStr: String, keyId: Int): String {
        val base = "${source.lowercase()}_${mediaTypeStr}_$keyId"
        val versioned = when {
            // v2: Bangumi'nin English / romaji / özgün adları ayrı taşınır. Önceki
            // sürümde Çince ad ana başlığa yazıldığı için eski satırlar okunmaz.
            source.equals("bangumi", ignoreCase = true) -> "${base}_bgm2"
            // ks1: bozuk Kitsu özetleri temizlenmeye başlandı (KitsuSynopsisValidator).
            // Eski satırlar bozuk özet taşıyabileceği için yeni anahtar kullanılır.
            source.equals("kitsu", ignoreCase = true) -> "${base}_ks1"
            // s2: Simkl detaylarına çapraz realMalId ve type eklendi.
            source.equals("simkl", ignoreCase = true) -> "${base}_vl${MediaTitleResolver.VERSION}_s2"
            // shiki_info1: Shikimori'nin atlanan yerel detay/istatistik alanları eklendi.
            source.equals("shikimori", ignoreCase = true) -> "${base}_shiki_info1"
            MediaTitleResolver.isLatinPreferredSource(source) -> "${base}_vl${MediaTitleResolver.VERSION}"
            else -> base
        }
        return "${versioned}_sp1"
    }

    /** Birincil detay + zenginleşmiş detay ortak Room önbellek yazarı. */
    private suspend fun saveToRoomCache(
        source: String,
        mediaType: MediaType,
        externalId: Int,
        detail: KitsugiMediaDetail
    ) {
        val context = com.kitsugi.animelist.KitsugiApplication.getInstance()?.applicationContext ?: return
        val db = com.kitsugi.animelist.data.local.KitsugiDatabase.getDatabase(context) ?: return
        try {
            val mediaTypeStr = mediaType.name.lowercase()
            val cacheKey = detailCacheKey(source, mediaTypeStr, externalId)
            val entity = com.kitsugi.animelist.data.local.PersistentDetailCacheEntity(
                cacheKey = cacheKey,
                detailJson = com.google.gson.Gson().toJson(detail),
                cachedAtMs = System.currentTimeMillis()
            )
            db.persistentDetailCacheDao().insertDetail(entity)
        } catch (e: Exception) {
            android.util.Log.e("KitsugiDetailClient", "Error writing detail cache: ${e.message}")
        }
    }
}
