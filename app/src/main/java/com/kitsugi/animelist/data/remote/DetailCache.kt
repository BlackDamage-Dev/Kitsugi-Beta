package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.core.memory.BoundedCache

import java.util.concurrent.ConcurrentHashMap

object DetailCache {

    // ─── Episode Ratings (tmdbId → CacheEntry) ───────────────────────────────
    data class RatingCacheEntry(
        val ratings: Map<Pair<Int, Int>, Double>,
        val expiresAtMs: Long
    )
    val episodeRatingsCache = BoundedCache<Int, RatingCacheEntry>("detail.episodeRatings", 150)

    // ─── MAL ID → TMDB ID mapping ────────────────────────────────────────────
    // Negative keys = AniList IDs (stored as -aniListId)
    val malToTmdbCache: MutableMap<Int, Int?> = BoundedCache<Int, Int?>("detail.malToTmdb", 3000)

    // ─── TMDB ID → TVDB ID mapping ───────────────────────────────────────────
    val tmdbToTvdbCache: MutableMap<Int, Int?> = BoundedCache<Int, Int?>("detail.tmdbToTvdb", 3000)

    // ─── TMDB ID → media kind ("movie" | "tv" | null = kontrol edildi, film değil) ──
    // ARM ("media") / animeapi ("themoviedb_type") çapraz eşlemelerinden doldurulur.
    // Film kayıtlarının TVDB/fanart-TV zincirine girmesini engelleyen doğruluk kaynağı:
    // TVDB filmleri dizinin 0. sezonu olarak tutar; film için TVDB kullanılırsa dizi
    // logosu/afişi sızar (Chainsaw Man Reze-hen vakası).
    val tmdbMediaCache: MutableMap<Int, String?> = BoundedCache<Int, String?>("detail.tmdbMedia", 3000)

    // ─── TMDB ID → Logo URL mapping ──────────────────────────────────────────
    val logoCache: MutableMap<Int, String?> = BoundedCache<Int, String?>("detail.logo", 1500)
    /** Dil + tür + sezon anahtarlı logo seçimi. Eski tek-logo önbelleği dil/film ayrımı yapamaz. */
    val logoSelectionCache: MutableMap<String, String?> = BoundedCache<String, String?>("detail.logoSelection", 400)

    // ─── TMDB (tmdbId, season) → episode DTOs ────────────────────────────────
    data class TmdbEpisodeDtoCached(
        val episodeNumber: Int,
        val name: String?,
        val overview: String?,
        val stillPath: String?,
        val airDate: String?
    )
    val tmdbEpisodesCache = BoundedCache<Pair<Int, Int>, List<TmdbEpisodeDtoCached>>("detail.tmdbEpisodes", 80)
    private val mediaDetails = BoundedCache<String, KitsugiMediaDetail>("detail.media", 160)
    private val mediaCharacters = BoundedCache<String, List<KitsugiCharacter>>("detail.characters", 80)
    private val mediaStaff = BoundedCache<String, List<KitsugiStaff>>("detail.staff", 80)
    private val mediaRelations = BoundedCache<String, List<KitsugiRelation>>("detail.relations", 80)
    private val mediaStats = BoundedCache<String, KitsugiStats>("detail.stats", 80)
    private val mediaReviews = BoundedCache<String, List<KitsugiReview>>("detail.reviews", 40)
    private val mediaEpisodes = BoundedCache<String, List<KitsugiStreamingEpisode>>("detail.episodes", 60)
    private val mediaRecommendations = BoundedCache<String, List<KitsugiRelation>>("detail.recommendations", 60)

    private val characterDetails = BoundedCache<String, KitsugiCharacterDetail>("detail.characterDetail", 60)
    private val staffDetails = BoundedCache<String, KitsugiStaffDetail>("detail.staffDetail", 60)
    private val studioDetails = BoundedCache<String, KitsugiStudioDetail>("detail.studioDetail", 30)

    private val translations = BoundedCache<String, String>("detail.translations", 300)

    /** Oturum boyunca çözülen kurgusal karakter portreleri (normalize karakter adı → URL). */
    val resolvedCharacterPortraits = BoundedCache<String, String>("detail.characterPortraits", 1500)

    /** Karakter portresi güncellendiğinde detay sekmelerinin anında haberdar olması için sayaç. */
    private val _characterPortraitVersion = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val characterPortraitVersion: kotlinx.coroutines.flow.StateFlow<Long> = _characterPortraitVersion

    private fun normalizePortraitKey(name: String): String =
        name.lowercase()
            .replace(Regex("\\s*\\((?:voice|uncredited|uncredited voice|seslendirme|ses|child)\\)", RegexOption.IGNORE_CASE), "")
            .replace("ou", "o").replace("oo", "o").replace("oh", "o").replace("uu", "u")
            .replace(Regex("[^\\p{L}\\p{N}]"), "")

    /**
     * TMDB görsel URL'lerindeki boyut segmentini (`w185`, `w300`, `w500`, `original`)
     * normalize ederek aynı kişi fotoğrafının farklı çözünürlüklerdeki kopyalarını eşleştirir.
     */
    fun normalizeImageIdentity(url: String?): String {
        val clean = url?.trim()?.substringBefore('?')?.trimEnd('/').orEmpty()
        if (clean.isEmpty()) return ""
        return clean.replace(Regex("^https?://image\\.tmdb\\.org/t/p/[^/]+/"), "https://image.tmdb.org/t/p/")
    }

    fun isSameImageResource(a: String?, b: String?): Boolean {
        val na = normalizeImageIdentity(a)
        val nb = normalizeImageIdentity(b)
        return na.isNotEmpty() && na == nb
    }

    fun getCachedCharacterPortrait(name: String?): String? {
        if (name.isNullOrBlank()) return null
        val fullKey = normalizePortraitKey(name)
        if (fullKey.length >= 2) {
            resolvedCharacterPortraits[fullKey]?.let { return it }
        }
        val primaryKey = normalizePortraitKey(name.substringBefore("/"))
        if (primaryKey.length >= 2 && primaryKey != fullKey) {
            resolvedCharacterPortraits[primaryKey]?.let { return it }
        }
        return null
    }

    /**
     * Karakter detay sayfasında veya çapraz kaynakta doğrulanan portreyi kaydeder ve
     * önbellekteki TMDB/Simkl karakter listelerinde görseli boş kalan kartlara anında işler.
     */
    fun propagateCharacterPortrait(name: String?, imageUrl: String?) {
        val rawName = name?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val url = imageUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val primaryName = rawName.substringBefore("/").substringBefore(" (").trim()
        val keys = listOf(rawName, primaryName)
            .map(::normalizePortraitKey)
            .filter { it.length >= 2 }
            .distinct()
        if (keys.isEmpty()) return

        for (cacheKey in mediaCharacters.keys.toList()) {
            if (!cacheKey.startsWith("tmdb") && !cacheKey.startsWith("simkl")) continue
            val existing = mediaCharacters[cacheKey] ?: continue
            val matchesVoiceActorPhoto = existing.any { char ->
                val charFullKey = normalizePortraitKey(char.name)
                val charPrimaryKey = normalizePortraitKey(char.name.substringBefore("/"))
                (charFullKey in keys || charPrimaryKey in keys) &&
                    char.voiceActors.any { va -> isSameImageResource(url, va.imageUrl) }
            }
            if (matchesVoiceActorPhoto) {
                keys.forEach { k ->
                    if (isSameImageResource(resolvedCharacterPortraits[k], url)) {
                        resolvedCharacterPortraits.remove(k)
                    }
                }
                return
            }
        }

        var updatedAny = false
        for (targetKey in keys) {
            if (resolvedCharacterPortraits[targetKey] != url) {
                resolvedCharacterPortraits[targetKey] = url
                updatedAny = true
            }
        }
        for (cacheKey in mediaCharacters.keys.toList()) {
            if (!cacheKey.startsWith("tmdb") && !cacheKey.startsWith("simkl")) continue
            val existing = mediaCharacters[cacheKey] ?: continue
            var changed = false
            val patched = existing.map { char ->
                val charFullKey = normalizePortraitKey(char.name)
                val charPrimaryKey = normalizePortraitKey(char.name.substringBefore("/"))
                val isActorDup = !char.imageUrl.isNullOrBlank() &&
                    char.voiceActors.any { va -> isSameImageResource(char.imageUrl, va.imageUrl) }
                val urlIsActorPhoto = char.voiceActors.any { va -> isSameImageResource(url, va.imageUrl) }
                if (!urlIsActorPhoto &&
                    (charFullKey in keys || charPrimaryKey in keys) &&
                    (char.imageUrl.isNullOrBlank() || (!char.isRealMediaRole && isActorDup))
                ) {
                    changed = true
                    char.copy(imageUrl = url, isRealMediaRole = false)
                } else {
                    char
                }
            }
            if (changed) {
                mediaCharacters[cacheKey] = patched
                updatedAny = true
            }
        }
        if (updatedAny) {
            _characterPortraitVersion.value = _characterPortraitVersion.value + 1L
        }
    }

    fun makeKey(source: String, id: Int, mediaType: String? = null): String {
        return if (!mediaType.isNullOrBlank()) {
            "${source.lowercase()}_${mediaType.lowercase()}_$id"
        } else {
            "${source.lowercase()}_$id"
        }
    }

    // Media Details
    fun getMediaDetail(source: String, id: Int, mediaType: String? = null): KitsugiMediaDetail? {
        val composite = mediaDetails[makeKey(source, id, mediaType)]
        return composite ?: mediaDetails[makeKey(source, id)]
    }

    fun putMediaDetail(source: String, id: Int, detail: KitsugiMediaDetail, mediaType: String? = null) {
        mediaDetails[makeKey(source, id, mediaType)] = detail
        mediaDetails[makeKey(source, id)] = detail
    }

    fun removeMediaDetail(source: String, id: Int, mediaType: String? = null) {
        mediaDetails.remove(makeKey(source, id, mediaType))
        mediaDetails.remove(makeKey(source, id))
    }

    // Characters Tab
    fun getMediaCharacters(source: String, id: Int, mediaType: String? = null): List<KitsugiCharacter>? {
        return mediaCharacters[makeKey(source, id, mediaType)] ?: mediaCharacters[makeKey(source, id)]
    }

    fun putMediaCharacters(source: String, id: Int, list: List<KitsugiCharacter>, mediaType: String? = null) {
        mediaCharacters[makeKey(source, id, mediaType)] = list
        mediaCharacters[makeKey(source, id)] = list
    }

    // Staff Tab
    fun getMediaStaff(source: String, id: Int, mediaType: String? = null): List<KitsugiStaff>? {
        return mediaStaff[makeKey(source, id, mediaType)] ?: mediaStaff[makeKey(source, id)]
    }

    fun putMediaStaff(source: String, id: Int, list: List<KitsugiStaff>, mediaType: String? = null) {
        mediaStaff[makeKey(source, id, mediaType)] = list
        mediaStaff[makeKey(source, id)] = list
    }

    // Relations Tab
    fun getMediaRelations(source: String, id: Int, mediaType: String? = null): List<KitsugiRelation>? {
        return mediaRelations[makeKey(source, id, mediaType)] ?: mediaRelations[makeKey(source, id)]
    }

    fun putMediaRelations(source: String, id: Int, list: List<KitsugiRelation>, mediaType: String? = null) {
        mediaRelations[makeKey(source, id, mediaType)] = list
        mediaRelations[makeKey(source, id)] = list
    }

    // Stats Tab
    fun getMediaStats(source: String, id: Int, mediaType: String? = null): KitsugiStats? {
        return mediaStats[makeKey(source, id, mediaType)] ?: mediaStats[makeKey(source, id)]
    }

    fun putMediaStats(source: String, id: Int, stats: KitsugiStats, mediaType: String? = null) {
        mediaStats[makeKey(source, id, mediaType)] = stats
        mediaStats[makeKey(source, id)] = stats
    }

    fun hasMediaStats(source: String, id: Int, mediaType: String? = null): Boolean {
        return mediaStats.containsKey(makeKey(source, id, mediaType)) || mediaStats.containsKey(makeKey(source, id))
    }

    // Reviews Tab
    fun getMediaReviews(source: String, id: Int, mediaType: String? = null): List<KitsugiReview>? {
        // Review results are source + format specific. Unlike general metadata, an
        // untyped fallback can leak a show's/anime's reviews onto a different detail page.
        val key = if (mediaType.isNullOrBlank()) makeKey(source, id) else makeKey(source, id, mediaType)
        return mediaReviews[key]
    }

    fun putMediaReviews(source: String, id: Int, list: List<KitsugiReview>, mediaType: String? = null) {
        mediaReviews[makeKey(source, id, mediaType)] = list
        mediaReviews[makeKey(source, id)] = list
    }

    fun removeMediaReviews(source: String, id: Int, mediaType: String? = null) {
        mediaReviews.remove(makeKey(source, id, mediaType))
        mediaReviews.remove(makeKey(source, id))
    }

    // Episodes Tab
    fun getMediaEpisodes(source: String, id: Int, mediaType: String? = null): List<KitsugiStreamingEpisode>? {
        return mediaEpisodes[makeKey(source, id, mediaType)] ?: mediaEpisodes[makeKey(source, id)]
    }

    fun putMediaEpisodes(source: String, id: Int, list: List<KitsugiStreamingEpisode>, mediaType: String? = null) {
        mediaEpisodes[makeKey(source, id, mediaType)] = list
        mediaEpisodes[makeKey(source, id)] = list
    }

    fun removeMediaEpisodes(source: String, id: Int, mediaType: String? = null) {
        mediaEpisodes.remove(makeKey(source, id, mediaType))
        mediaEpisodes.remove(makeKey(source, id))
    }

    // Recommendations Tab
    fun getMediaRecommendations(source: String, id: Int, mediaType: String? = null): List<KitsugiRelation>? {
        return mediaRecommendations[makeKey(source, id, mediaType)] ?: mediaRecommendations[makeKey(source, id)]
    }

    fun putMediaRecommendations(source: String, id: Int, list: List<KitsugiRelation>, mediaType: String? = null) {
        mediaRecommendations[makeKey(source, id, mediaType)] = list
        mediaRecommendations[makeKey(source, id)] = list
    }

    fun removeMediaRecommendations(source: String, id: Int, mediaType: String? = null) {
        mediaRecommendations.remove(makeKey(source, id, mediaType))
        mediaRecommendations.remove(makeKey(source, id))
    }

    fun removeMediaCharacters(source: String, id: Int, mediaType: String? = null) {
        mediaCharacters.remove(makeKey(source, id, mediaType))
        mediaCharacters.remove(makeKey(source, id))
    }

    fun removeMediaStaff(source: String, id: Int, mediaType: String? = null) {
        mediaStaff.remove(makeKey(source, id, mediaType))
        mediaStaff.remove(makeKey(source, id))
    }

    fun removeMediaRelations(source: String, id: Int, mediaType: String? = null) {
        mediaRelations.remove(makeKey(source, id, mediaType))
        mediaRelations.remove(makeKey(source, id))
    }


    // Character Detail
    fun getCharacterDetail(source: String, id: Int, identity: String? = null): KitsugiCharacterDetail? {
        return characterDetails[makeIdentityKey(source, id, identity)]
    }

    fun putCharacterDetail(source: String, id: Int, detail: KitsugiCharacterDetail, identity: String? = null) {
        characterDetails[makeIdentityKey(source, id, identity)] = detail
    }

    fun removeCharacterDetail(source: String, id: Int, identity: String? = null) {
        characterDetails.remove(makeIdentityKey(source, id, identity))
    }

    /** Add a per-entity discriminator when one provider ID represents several rows (e.g. TMDB roles). */
    private fun makeIdentityKey(source: String, id: Int, identity: String?): String {
        val base = makeKey(source, id)
        val variant = identity?.trim()?.lowercase(java.util.Locale.ROOT)?.takeIf { it.isNotBlank() }
            ?: return base
        return "${base}_identity_${variant}"
    }

    // Staff Detail
    fun getStaffDetail(source: String, id: Int): KitsugiStaffDetail? {
        return staffDetails[makeKey(source, id)]
    }

    fun putStaffDetail(source: String, id: Int, detail: KitsugiStaffDetail) {
        staffDetails[makeKey(source, id)] = detail
    }

    fun removeStaffDetail(source: String, id: Int) {
        staffDetails.remove(makeKey(source, id))
    }

    // Studio Detail
    fun getStudioDetail(source: String, id: Int, identity: String? = null): KitsugiStudioDetail? {
        return studioDetails[makeIdentityKey(source, id, identity)] ?: studioDetails[makeKey(source, id)]
    }

    fun putStudioDetail(source: String, id: Int, detail: KitsugiStudioDetail, identity: String? = null) {
        studioDetails[makeIdentityKey(source, id, identity)] = detail
        if (id > 0) {
            studioDetails[makeKey(source, id)] = detail
        }
    }

    private val fanartGalleryCache = BoundedCache<String, List<GalleryItem>>("detail.fanartGallery", 20)
    private val tmdbGalleryCache = BoundedCache<String, List<GalleryItem>>("detail.tmdbGallery", 20)

    private fun translationKey(type: String, source: String, id: Int, identity: String? = null): String {
        val targetLang = com.kitsugi.animelist.utils.KitsugiTranslatePrefs.resolvedTarget().lowercase()
        return "${targetLang}_${type.lowercase()}_${makeIdentityKey(source, id, identity)}"
    }

    /**
     * Çevirinin ait olduğu **kaynak metnin** kimliği.
     *
     * Neden gerekli: anahtar yalnızca (dil, tür, kaynak, id) içeriyordu. Bir kaydın özeti /
     * biyografisi sonradan değiştiğinde (ör. TMDB zenginleştirmesi farklı bir özet getirdiğinde)
     * eski metnin çevirisi yeni metne "yapışıyor" ve sayfada alakasız bir açıklama görünüyordu.
     * Karşılaştırma normalize edilmiş metinle yapılır: boşluk/noktalama farkı çeviriyi
     * geçersiz kılmaz, anlamlı metin değişimi kılar.
     */
    private val translationSourceTexts = BoundedCache<String, String>("detail.translationTexts", 300)

    private fun translationTextKey(type: String, source: String, id: Int, identity: String?): String =
        "src_${type.lowercase()}_${makeIdentityKey(source, id, identity)}"

    // Translations (Synopsis / Biography)
    fun getTranslation(
        type: String,
        source: String,
        id: Int,
        identity: String? = null,
        sourceText: String? = null
    ): String? {
        val key = translationKey(type, source, id, identity)
        val translation = translations[key] ?: return null
        // Çağıran kaynak metni veriyorsa çeviri o metne ait olmalı; değilse eski/yapışmış kayıttır.
        if (sourceText != null) {
            val stored = translationSourceTexts[translationTextKey(type, source, id, identity)]
            if (stored != null &&
                stored != com.kitsugi.animelist.utils.normalizeTranslationText(sourceText)
            ) {
                return null
            }
        }
        return translation
    }

    fun putTranslation(
        type: String,
        source: String,
        id: Int,
        translation: String,
        identity: String? = null,
        sourceText: String? = null
    ) {
        val key = translationKey(type, source, id, identity)
        translations[key] = translation
        if (sourceText != null) {
            translationSourceTexts[translationTextKey(type, source, id, identity)] =
                com.kitsugi.animelist.utils.normalizeTranslationText(sourceText)
        } else {
            translationSourceTexts.remove(translationTextKey(type, source, id, identity))
        }
    }

    fun removeTranslation(type: String, source: String, id: Int, identity: String? = null) {
        val key = translationKey(type, source, id, identity)
        translations.remove(key)
        translationSourceTexts.remove(translationTextKey(type, source, id, identity))
    }

    // Fanart.tv Gallery Cache
    fun getFanartGallery(isMovie: Boolean, id: Int, language: String = ""): List<GalleryItem>? {
        val key = fanartGalleryKey(isMovie, id, language)
        return fanartGalleryCache[key]
    }

    fun putFanartGallery(isMovie: Boolean, id: Int, list: List<GalleryItem>, language: String = "") {
        val key = fanartGalleryKey(isMovie, id, language)
        fanartGalleryCache[key] = list
    }

    private fun fanartGalleryKey(isMovie: Boolean, id: Int, language: String): String =
        "${if (isMovie) "movie" else "tv"}_${id}_${language.trim().lowercase()}"

    fun removeFanartGallery(isMovie: Boolean, id: Int) {
        val prefix = "${if (isMovie) "movie" else "tv"}_${id}_"
        fanartGalleryCache.keys.filter { it.startsWith(prefix) }.forEach { key -> fanartGalleryCache.remove(key) }
    }

    fun clearFanartCache() {
        fanartGalleryCache.clear()
    }

    // TMDB Gallery Cache
    fun getTmdbGallery(
        isMovie: Boolean,
        id: Int,
        language: String = "",
        seasonNumber: Int? = null
    ): List<GalleryItem>? = tmdbGalleryCache[tmdbGalleryKey(isMovie, id, language, seasonNumber)]

    fun putTmdbGallery(
        isMovie: Boolean,
        id: Int,
        list: List<GalleryItem>,
        language: String = "",
        seasonNumber: Int? = null
    ) {
        tmdbGalleryCache[tmdbGalleryKey(isMovie, id, language, seasonNumber)] = list
    }

    private fun tmdbGalleryKey(isMovie: Boolean, id: Int, language: String, seasonNumber: Int?): String {
        val normalizedLanguage = language.trim().lowercase().replace('_', '-').substringBefore('-')
        val season = seasonNumber?.takeIf { it > 0 } ?: 0
        return "${if (isMovie) "movie" else "tv"}_${id}_${normalizedLanguage}_$season"
    }

    fun removeTmdbGallery(isMovie: Boolean, id: Int) {
        val prefix = "${if (isMovie) "movie" else "tv"}_${id}_"
        tmdbGalleryCache.keys.filter { it.startsWith(prefix) }.forEach { key -> tmdbGalleryCache.remove(key) }
    }

    fun clearTmdbGalleryCache() {
        tmdbGalleryCache.clear()
    }

    // Shikimori Gallery Cache
    private val shikimoriGalleryCache = BoundedCache<Int, List<GalleryItem>>("detail.shikimoriGallery", 20)

    fun getShikimoriGallery(id: Int): List<GalleryItem>? = shikimoriGalleryCache[id]
    fun putShikimoriGallery(id: Int, list: List<GalleryItem>) { shikimoriGalleryCache[id] = list }
    fun removeShikimoriGallery(id: Int) { shikimoriGalleryCache.remove(id) }
    fun clearShikimoriGalleryCache() { shikimoriGalleryCache.clear() }

    // Unified Multi-Source Videos Cache
    private val mediaVideos = BoundedCache<String, List<MediaVideoItem>>("detail.mediaVideos", 60)

    private fun mediaVideosKey(source: String, id: Int, seasonNumber: Int? = null): String =
        "${makeKey(source, id)}_s${seasonNumber?.takeIf { it > 0 } ?: 0}"

    fun getMediaVideos(source: String, id: Int, seasonNumber: Int? = null): List<MediaVideoItem>? =
        mediaVideos[mediaVideosKey(source, id, seasonNumber)]

    fun putMediaVideos(source: String, id: Int, list: List<MediaVideoItem>, seasonNumber: Int? = null) {
        if (list.isNotEmpty()) {
            mediaVideos[mediaVideosKey(source, id, seasonNumber)] = list
        }
    }

    fun removeMediaVideos(source: String, id: Int) {
        val prefix = "${makeKey(source, id)}_"
        mediaVideos.keys.filter { it.startsWith(prefix) }.forEach { mediaVideos.remove(it) }
    }

    // Clear all (called on app-level reset / force refresh from settings)
    fun clear() {
        mediaDetails.clear()
        mediaCharacters.clear()
        mediaStaff.clear()
        mediaRelations.clear()
        mediaStats.clear()
        mediaReviews.clear()
        mediaEpisodes.clear()
        mediaRecommendations.clear()
        mediaVideos.clear()
        characterDetails.clear()
        staffDetails.clear()
        studioDetails.clear()
        translations.clear()
        translationSourceTexts.clear()
        fanartGalleryCache.clear()
        tmdbGalleryCache.clear()
        shikimoriGalleryCache.clear()
        episodeRatingsCache.clear()
        malToTmdbCache.clear()
        tmdbToTvdbCache.clear()
        tmdbMediaCache.clear()
        logoCache.clear()
        logoSelectionCache.clear()
        tmdbEpisodesCache.clear()
    }
}
