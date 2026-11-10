package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.utils.KitsugiDateUtils
import com.kitsugi.animelist.utils.sortedByLanguagePreference
import com.kitsugi.animelist.utils.toTurkishStaffRole

data class JikanSearchResult(
    val malId: Int,
    val title: String,
    val subtitle: String,
    val type: MediaType,
    val total: Int?,
    val score: Int?,
    val isAdult: Boolean,
    val imageUrl: String?,
    val year: Int?,
    val source: String,
    // AniList kaynağından gelen sonuçlarda gerçek MAL ID'si (idMal != null ise)
    val realMalId: Int? = null,
    val titleEnglish: String? = null,
    val titleJapanese: String? = null,
    /** Latin özgün başlık (özellikle Bangumi / AniList kaynaklarında English'ten ayrıdır). */
    val titleRomaji: String? = null,
    val tmdbId: Int? = null,
    val backdropUrl: String? = null,
    val rank: Int? = null,
    val members: Int? = null,
    val favorites: Int? = null,
    val rawScoreDouble: Double? = null,
    // AniList kaynaklı sonuçlar için: "episode|airingAtEpoch" formatında
    val nextAiringEpisode: String? = null,
    val cs3Url: String? = null,
    val cs3ApiName: String? = null,
    val genres: List<String> = emptyList(),
    val genreIds: List<Int> = emptyList(),
    /** Bangumi subject namespace type (2=anime, 1=book, 6=real); null for other sources. */
    val bangumiSubjectType: Int? = null
)

data class KitsugiTheme(
    val label: String,       // Görüntülenecek metin: "We Are!" by Hiroshi Kitadani (OP1)
    val videoUrl: String?,   // animethemes.moe direkt .webm linki (yoksa null → YouTube araması)
    val source: String = "",
    val alternateVideoUrls: List<String> = emptyList()
)

enum class StudioRole {
    STUDIO, PRODUCER, MAGAZINE, PUBLISHER, NETWORK, LICENSOR,
    /** Manga/roman yazarı (Jikan `authors` / MAL v2 `authors[].node`) — stüdyo değil, kişidir. */
    AUTHOR
}

data class KitsugiStudio(
    val id: Int,
    val name: String,
    val isMain: Boolean = true,
    /** Provider whose ID namespace [id] belongs to; blank means legacy/unknown. */
    val source: String = "",
    val role: StudioRole = StudioRole.STUDIO,
    /** Provider logo/photo, when the media detail API includes it. */
    val imageUrl: String? = null
)

data class KitsugiRanking(
    val rank: Int,
    val type: String, // RATED, POPULAR
    val context: String = "",
    val allTime: Boolean = false,
    val year: Int? = null,
    val season: String? = null,
    val id: Int = 0,
    val format: String = ""
)

data class KitsugiStudioDetail(
    val id: Int,
    val name: String,
    val isMain: Boolean = true,
    val imageUrl: String? = null,
    val favorites: Int? = null,
    val established: String? = null,
    val about: String? = null,
    val mediaWorks: List<KitsugiStaffMediaWork> = emptyList(),
    val isFavourite: Boolean = false,
    val aniListId: Int? = null,
    /** Yapım listesinde bu sayfadan sonra da içerik olup olmadığı (sonsuz kaydırma). */
    val hasMoreWorks: Boolean = false
)

/** Stüdyo yapım listesinin bir sayfası ([hasMore]: sonraki sayfa var mı). */
data class KitsugiStudioWorksPage(
    val works: List<KitsugiStaffMediaWork>,
    val hasMore: Boolean
)

data class KitsugiMediaDetail(
    val synopsis: String?,
    val genres: List<String> = emptyList(),
    val status: String? = null,
    val season: String? = null,
    val sourceMaterial: String? = null,
    val studios: List<KitsugiStudio> = emptyList(),
    val producers: List<KitsugiStudio> = emptyList(),
    val rating: String? = null,
    val broadcast: String? = null,
    val episodeDuration: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val titleEnglish: String? = null,
    val titleJapanese: String? = null,
    val titleRomaji: String? = null,
    val titleNative: String? = null,
    /** Türkçe başlık (TMDB alternative_titles'dan TR kodlu başlık). Türk streaming siteleri için arama kritik. */
    val titleTurkish: String? = null,
    val synonyms: List<String> = emptyList(),
    val openings: List<KitsugiTheme> = emptyList(),
    val endings: List<KitsugiTheme> = emptyList(),
    val trailerUrl: String? = null,
    val trailerSource: String = "",
    val title: String? = null,
    val imageUrl: String? = null,
    val bannerImage: String? = null,
    val type: MediaType? = null,
    val score: Int? = null,
    val year: Int? = null,
    val total: Int? = null,
    val isAdult: Boolean = false,
    val realMalId: Int? = null,
    val tags: List<KitsugiTag> = emptyList(),
    val externalLinks: List<KitsugiExternalLink> = emptyList(),
    val streamingLinks: List<KitsugiExternalLink> = emptyList(),
    val streamingEpisodes: List<KitsugiStreamingEpisode> = emptyList(),
    val tmdbId: Int? = null,   // SeriesGraph API için — AniList externalLinks'ten veya TVDB'den çıkarılır
    val tmdbSeason: Int? = null,
    val pictures: List<String> = emptyList(),  // Jikan /pictures endpoint'inden gelen ek resimler
    val totalSeasons: Int? = null,
    val nextAiringEpisode: String? = null,
    val meanScore: Int? = null,
    val averageScore: Int? = null,
    val popularity: Int? = null,
    val favorites: Int? = null,
    val rank: Int? = null,
    val popularityRank: Int? = null,
    val scoredBy: Int? = null,
    val members: Int? = null,
    val isFavourite: Boolean = false,
    val themes: List<String> = emptyList(),
    val demographics: List<String> = emptyList(),
    val serializations: List<KitsugiStudio> = emptyList(),
    val networks: List<KitsugiStudio> = emptyList(),
    val authors: List<KitsugiStaff> = emptyList(),
    val rankings: List<KitsugiRanking> = emptyList(),
    val format: String? = null,
    val countryOfOrigin: String? = null,
    val originalLanguage: String? = null,
    val tagline: String? = null,
    val budget: Long? = null,
    val revenue: Long? = null,
    val volumes: Int? = null,
    val rawFormat: String? = null,
    val rawStatus: String? = null,
    val rawSourceMaterial: String? = null,
    val rawSeason: String? = null,
    val seasonYear: Int? = null,
    /** Shikimori gibi sağlayıcıların ayrıntı yanıtında taşıdığı ek alanlar. */
    val titleRussian: String? = null,
    val episodesAired: Int? = null,
    // Nullable kalır: Gson, eski kalıcı önbellek JSON'larında olmayan non-null List
    // alanlarına emptyList() yerine null koyar; eski MAL/AniList kayıtları da güvenli okunur.
    val publishers: List<String>? = null,
    val licensors: List<String>? = null,
    val fansubbers: List<String>? = null,
    val fandubbers: List<String>? = null,
    /** Kaynağın kendi topluluk istatistikleri; çapraz-servis yedeğinden önce kullanılır. */
    val nativeStats: KitsugiStats? = null
)

data class KitsugiStreamingEpisode(
    val title: String,
    val thumbnail: String?,
    val url: String?,
    val site: String?,
    val seasonNumber: Int? = null,   // Bölüm puanı eşleştirmesi için
    val episodeNumber: Int? = null   // Bölüm puanı eşleştirmesi için
)

data class KitsugiTag(
    val name: String,
    val rank: Int? = null,       // % relevance, AniList'ten gelir
    val isSpoiler: Boolean,
    val id: Int? = null,
    val source: String = "anilist",
    val description: String? = null,
    val category: String? = null
)

data class KitsugiExternalLink(
    val site: String,
    val url: String,
    val language: String? = null  // örn. "JP", "EN"
)

data class KitsugiVoiceActor(
    val id: Int,
    val name: String,
    val language: String,
    val imageUrl: String?,
    val source: String = "jikan",
    val romanizedName: String? = null,
    val nativeName: String? = null,
    val englishName: String? = null
)

data class KitsugiCharacter(
    val id: Int,
    val name: String,
    val role: String,
    val imageUrl: String?,
    val voiceActors: List<KitsugiVoiceActor> = emptyList(),
    val source: String = "jikan",
    // Canlı çekim (animasyon olmayan) dizi/film karakterleri: detay sayfası
    // AniList/MAL/Kitsu araması yerine doğrudan TMDB/Simkl verisiyle açılır.
    val isRealMediaRole: Boolean = false,
    // TMDB jeneriğinde "(voice)" / "(seslendirme)" olarak işaretlenmiş seslendirme rolü mü?
    val isVoiceRole: Boolean = false,
    val romanizedName: String? = null,
    val nativeName: String? = null,
    val englishName: String? = null
)

data class KitsugiCharacterMediaAppearance(
    val mediaId: Int,
    val title: String,
    val imageUrl: String?,
    val mediaType: String,
    val characterRole: String,
    val source: String = "jikan",
    val titleEnglish: String? = null,
    val titleJapanese: String? = null,
    val titleRomaji: String? = null
)

data class KitsugiCharacterDetail(
    val id: Int,
    val name: String,
    val nativeName: String?,
    val alternativeNames: List<String>,
    val imageUrl: String?,
    val gender: String?,
    val age: String?,
    val birthday: String?,
    val bloodType: String?,
    val biography: String?,
    val voiceActors: List<KitsugiVoiceActor> = emptyList(),
    val mediaAppearances: List<KitsugiCharacterMediaAppearance> = emptyList(),
    val isFavourite: Boolean = false,
    val aniListId: Int? = null,
    val source: String = "jikan",
    val romanizedName: String? = null,
    val englishName: String? = null
)

data class KitsugiStaff(
    val id: Int,
    val name: String,
    val role: String,
    val imageUrl: String?,
    val source: String = "jikan",
    val romanizedName: String? = null,
    val nativeName: String? = null,
    val englishName: String? = null
)

data class KitsugiStaffCharacterRole(
    val characterId: Int,
    val characterName: String,
    val characterImageUrl: String?,
    val characterSource: String = "jikan",
    val mediaId: Int,
    val mediaTitle: String,
    val mediaImageUrl: String?,
    val mediaType: String,
    val characterRole: String,
    val mediaSource: String = "jikan",
    val characterRomanizedName: String? = null,
    val characterNativeName: String? = null,
    val characterEnglishName: String? = null,
    /** Rolün geçtiği eser için ayrı dil alanları (Bangumi p1 / v0 yanıtları). */
    val mediaTitleEnglish: String? = null,
    val mediaTitleJapanese: String? = null,
    val mediaTitleRomaji: String? = null
)

data class KitsugiStaffMediaWork(
    val mediaId: Int,
    val mediaTitle: String,
    val mediaImageUrl: String?,
    val mediaType: String,
    val staffRole: String,
    val source: String = "jikan",
    val titleEnglish: String? = null,
    val titleJapanese: String? = null,
    val titleRomaji: String? = null
)

data class KitsugiStaffDetail(
    val id: Int,
    val name: String,
    val nativeName: String?,
    val alternativeNames: List<String>,
    val imageUrl: String?,
    val biography: String?,
    val occupation: String?,
    val birthday: String?,
    val age: String?,
    val gender: String?,
    val homeTown: String?,
    val characterRoles: List<KitsugiStaffCharacterRole> = emptyList(),
    val mediaWorks: List<KitsugiStaffMediaWork> = emptyList(),
    val isFavourite: Boolean = false,
    val aniListId: Int? = null,
    val romanizedName: String? = null,
    val englishName: String? = null
)

data class KitsugiRelation(
    val malId: Int,
    val title: String,
    val relationType: String,
    val imageUrl: String?,
    val mediaType: MediaType,
    val source: String,
    val titleEnglish: String? = null,
    val titleJapanese: String? = null,
    val titleRomaji: String? = null,
    val isAdult: Boolean = false,
    /** Exact Bangumi subject namespace when this relation came from Bangumi. */
    val bangumiSubjectType: Int? = null
)

data class KitsugiScoreStat(
    val score: Int,
    val amount: Int
)



data class KitsugiStats(
    val watching: Int?,
    val completed: Int?,
    val planned: Int?,
    val dropped: Int?,
    val paused: Int? = null,
    val scoreDistribution: List<KitsugiScoreStat> = emptyList(),
    val rankings: List<KitsugiRanking> = emptyList()
)

data class KitsugiReview(
    val id: Int? = null,
    val userId: Int? = null,
    val username: String,
    val avatarUrl: String?,
    val score: Int?,
    val summary: String,
    val fullText: String = "",
    val dateText: String? = null,
    val helpfulCount: Int? = null,
    val ratingAmount: Int? = null,
    val userRating: String? = null,
    /**
     * İncellemenin YAZILDIĞI orijinal dil (ISO 639-1). Kaynak sağlayabiliyorsa doldurulur
     * (TMDB `iso_639_1`). Dilden bağımsız tüm incelemeler gösterildiği için çeviri
     * aracına kaynak dil olarak iletilir; null ise çeviri tarafı otomatik algılar.
     */
    val languageCode: String? = null,
    val source: String = "anilist"
)

/**
 * İncelemenin liste/seçim kimliği (LazyRow anahtarı, açık detay sayfasının geri bulunması,
 * çeviri önbelleği anahtarı).
 *
 * TMDB (metin/hex yorum kimliği), MAL/Jikan ve Bangumi incelemeleri sayısal kimlik
 * taşımaz → [KitsugiReview.id] `null` kalır. Eskiden anahtar `"${source}_${id}"` olduğu için
 * bu kaynaklardaki HER inceleme aynı `"tmdb_null"` / `"jikan_null"` / `"bangumi_null"`
 * anahtarını alıyor ve ikinci kart ölçülürken uygulama çöküyordu (v2.4.369 raporu).
 * Kimlik yoksa yazar + tarih + metin özeti parmak izi kullanılır.
 *
 * NOT: API aynı incelemeyi iki kez döndürebileceği için bu değer tek başına benzersizlik
 * GARANTİSİ vermez; Lazy listelerde `uniqueItems` / `uniqueLazyKeys` ile birlikte kullanın.
 */
fun KitsugiReview.stableKey(): String {
    val src = source.trim().lowercase(java.util.Locale.ROOT).ifEmpty { "unknown" }
    id?.let { return "rev:$src:$it" }
    val fingerprint = buildString {
        append(username.trim().lowercase(java.util.Locale.ROOT))
        append('\u0001').append(dateText.orEmpty().trim())
        append('\u0001').append(summary.trim().take(REVIEW_FINGERPRINT_CHARS))
        append('\u0001').append(fullText.trim().take(REVIEW_FINGERPRINT_CHARS))
    }
    return "rev:$src:h${Integer.toHexString(fingerprint.hashCode())}"
}

private const val REVIEW_FINGERPRINT_CHARS = 160

data class KitsugiForumTopic(
    val id: Int,
    val title: String,
    val commentCount: Int,
    val viewCount: Int,
    val username: String,
    val avatarUrl: String?,
    val dateText: String? = null,
    val likeCount: Int = 0,
    val isLiked: Boolean = false,
    val userId: Int? = null,
    val source: String = "anilist",
    val body: String = ""
)

data class KitsugiForumReply(
    val id: Int,
    val comment: String,
    val dateText: String?,
    val username: String,
    val avatarUrl: String?,
    val likeCount: Int,
    val isLiked: Boolean,
    val userId: Int? = null,
    val createdAt: Int? = null,
    val childComments: List<KitsugiForumReply> = emptyList()
)

data class KitsugiActivity(
    val id: Int,
    val text: String,
    val dateText: String?,
    val username: String,
    val avatarUrl: String?,
    val mediaTitle: String? = null,
    val mediaTitleRomaji: String? = null,
    val mediaTitleEnglish: String? = null,
    val mediaTitleNative: String? = null,
    val mediaCoverUrl: String? = null,
    val likeCount: Int,
    val isLiked: Boolean,
    val replies: List<KitsugiActivityReply> = emptyList(),
    val mediaId: Int? = null,
    val mediaType: String? = null,
    val isAdult: Boolean = false,
    val userId: Int? = null,
    val source: String = "anilist"
)

data class KitsugiActivityReply(
    val id: Int,
    val text: String,
    val dateText: String?,
    val username: String,
    val avatarUrl: String?,
    val likeCount: Int,
    val isLiked: Boolean,
    val userId: Int? = null
)

/** Canonical identity for a list provider; aliases are unified, providers are not. */
fun canonicalMediaSourceId(source: String): String = when (source.trim().lowercase()) {
    "anilist", "al" -> "anilist"
    "mal", "myanimelist", "jikan", "jikan (mal)", "mal (jikan)" -> "mal"
    "simkl" -> "simkl"
    "tmdb", "themoviedb" -> "tmdb"
    "tmdb_account" -> "tmdb_account"
    "kitsu" -> "kitsu"
    "shikimori", "shiki" -> "shikimori"
    "bangumi", "bgm", "bgm.tv" -> "bangumi"
    else -> source.trim().lowercase()
}

/**
 * Membership matching for a provider-scoped screen. It intentionally considers only that
 * provider's ID and title identity; a shared MAL/TMDB ID must not make a Simkl row appear
 * in Bangumi's list (or vice versa).
 */
fun MediaEntry.matchesInSource(result: JikanSearchResult): Boolean {
    if (canonicalMediaSourceId(source) != canonicalMediaSourceId(result.source)) return false
    if (matchesProviderIdentity(result)) return true
    // Same-title remakes are different titles. Title fallback is only an identity hint when
    // neither side carries a conflicting ID in the provider namespace being queried.
    if (hasConflictingProviderId(result)) return false
    return matchesTitleVariant(result)
}

fun MediaEntry.matchesInSource(mediaId: Int, mediaSource: String): Boolean {
    val entrySource = canonicalMediaSourceId(source)
    val querySource = canonicalMediaSourceId(mediaSource)
    if (entrySource == querySource) {
        return matchesProviderId(mediaId, mediaSource)
    }
    if (entrySource == "simkl" && querySource == "tmdb") {
        return tmdbId != null && tmdbId == mediaId
    }
    return false
}

fun List<MediaEntry>.firstMatchingInSource(result: JikanSearchResult): MediaEntry? =
    firstOrNull { it.matchesInSource(result) }

fun List<MediaEntry>.firstMatchingInSource(mediaId: Int, mediaSource: String): MediaEntry? =
    firstOrNull { it.matchesInSource(mediaId, mediaSource) }

/**
 * Ekleme/favorileme hedef kaynağında (`targetSource`) zaten kayıt var mı kontrolü.
 *
 * Örneğin kullanıcı TMDB keşfet/detay ekranından (`result.source = "tmdb"`) bir film veya
 * anime filmi eklediğinde kayıt `primarySource = "simkl"` altında saklanır. Yalnızca
 * `matchesInSource(result)` çağrıldığında `"simkl" != "tmdb"` yüzünden her tıklama yeni bir
 * Simkl satırı açıyordu (Simkl sekmesinde birebir aynı kartın yan yana çoğalması).
 *
 * Bu yardımcı yalnızca hedef sağlayıcı (`targetSource`) ve sonuç sağlayıcısı (`result.source`)
 * kapsamındaki kayıtları denetler; alakasız üçüncü bir sağlayıcının kaydını ödünç almaz.
 */
fun MediaEntry.matchesTargetSource(result: JikanSearchResult, targetSource: String): Boolean {
    val entryCanon = canonicalMediaSourceId(source)
    val targetCanon = canonicalMediaSourceId(targetSource)
    if (entryCanon != targetCanon) return false
    if (canonicalMediaSourceId(result.source) == targetCanon && matchesInSource(result)) return true

    val resultCanon = canonicalMediaSourceId(result.source)
    val resultTmdb = result.tmdbId?.takeIf { it > 0 }
        ?: if (resultCanon == "tmdb") result.malId.takeIf { it > 0 } else null
    if (tmdbId != null && tmdbId > 0 && resultTmdb != null && tmdbId == resultTmdb) {
        if (type == result.type || isCompatibleAnimeMovieType(type, result.type, total, progress)) {
            return true
        }
    }

    val resultMal = when {
        result.realMalId != null && result.realMalId in 1..99_999_999 -> result.realMalId
        resultCanon == "mal" && result.malId in 1..99_999_999 -> result.malId
        resultCanon == "anilist" && result.malId in 1..99_999_999 -> result.malId
        else -> null
    }
    val entryRealMal = com.kitsugi.animelist.model.MediaIdentity.realMalId(this)
    if (entryRealMal != null && resultMal != null && entryRealMal == resultMal) {
        if (type == result.type || isCompatibleAnimeMovieType(type, result.type, total, progress)) {
            return true
        }
    }

    if (targetCanon == "simkl" && resultCanon == "simkl" && simklId != null && simklId == result.malId) {
        return true
    }

    return matchesTitleVariantAllowingAnimeMovie(result)
}

fun List<MediaEntry>.firstMatchingInTargetOrResultSource(
    result: JikanSearchResult,
    targetSource: String
): MediaEntry? =
    firstOrNull { it.matchesTargetSource(result, targetSource) }
        ?: firstOrNull { it.matchesInSource(result) }

private fun isCompatibleAnimeMovieType(
    entryType: MediaType,
    resultType: MediaType,
    entryTotal: Int?,
    entryProgress: Int
): Boolean {
    val pair = (entryType == MediaType.Anime && resultType == MediaType.Movie) ||
        (entryType == MediaType.Movie && resultType == MediaType.Anime)
    if (!pair) return false
    return entryType != MediaType.Anime || ((entryTotal == null || entryTotal <= 1) && entryProgress <= 1)
}

private fun MediaEntry.matchesTitleVariantAllowingAnimeMovie(result: JikanSearchResult): Boolean {
    if (type != result.type && !isCompatibleAnimeMovieType(type, result.type, total, progress)) {
        return false
    }
    val entryTitles = listOfNotNull(title, titleEnglish, titleJapanese)
        .flatMap { com.kitsugi.animelist.model.MediaIdentity.normalizedTitleVariants(it) }
        .filter { it.length >= 2 }
        .toSet()
    val resultTitles = listOfNotNull(result.title, result.titleEnglish, result.titleJapanese, result.titleRomaji)
        .flatMap { com.kitsugi.animelist.model.MediaIdentity.normalizedTitleVariants(it) }
        .filter { it.length >= 2 }
        .toSet()
    if (entryTitles.none { it in resultTitles }) return false
    val entryYear = year
    val resultYear = result.year
    return entryYear == null || resultYear == null || kotlin.math.abs(entryYear - resultYear) <= 1
}

fun MediaEntry.matches(result: JikanSearchResult): Boolean {
    // Aynı sağlayıcıdaki doğrudan ID ve başlık eşleşmeleri.
    if (canonicalMediaSourceId(source) == canonicalMediaSourceId(result.source) && matchesInSource(result)) {
        return true
    }

    // Birleşik kütüphane / cross-sync için kaynaklar arası kimlik eşleşmeleri.
    val entryTmdb = tmdbId
    val resultTmdb = result.tmdbId ?: if (canonicalMediaSourceId(result.source) == "tmdb") result.malId else null
    if (entryTmdb != null && resultTmdb != null && entryTmdb == resultTmdb) return true

    val resultIsMal = canonicalMediaSourceId(result.source) == "mal"
    val entryIsMal = canonicalMediaSourceId(source) == "mal"
    val resultMalId = if (resultIsMal) {
        result.malId
    } else {
        result.realMalId ?: if (canonicalMediaSourceId(result.source) == "anilist" && result.malId < 100_000_000) result.malId else null
    }
    val entryMalId = if (entryIsMal) malId else malId?.takeIf { it < 100_000_000 }
    if (resultMalId != null && entryMalId != null && resultMalId == entryMalId) return true

    // Loose matching remains available only to the combined-library lookup. Provider-scoped
    // callers use matchesInSource() and never reach these cross-provider fallbacks.
    return matchesTitleVariant(result)
}

private fun MediaEntry.matchesProviderIdentity(result: JikanSearchResult): Boolean =
    matchesProviderId(result.malId, result.source) ||
        (result.tmdbId?.let { matchesProviderId(it, "tmdb") } == true) ||
        (canonicalMediaSourceId(source) == "simkl" && (
            (simklId != null && simklId == result.malId) ||
            (result.realMalId != null && malId != null && malId == result.realMalId)
        ))

private fun MediaEntry.matchesProviderId(mediaId: Int, mediaSource: String): Boolean {
    if (canonicalMediaSourceId(source) != canonicalMediaSourceId(mediaSource)) return false
    if (mediaId <= 0) return false
    val entrySource = canonicalMediaSourceId(source)
    return when (entrySource) {
        "anilist" -> {
            val rawEntryId = malId?.let { if (it >= 100_000_000) it - 100_000_000 else it }
            val rawResultId = if (mediaId >= 100_000_000) mediaId - 100_000_000 else mediaId
            rawEntryId != null && rawEntryId == rawResultId
        }
        "simkl" -> simklId == mediaId || malId == mediaId
        "tmdb", "tmdb_account" -> tmdbId == mediaId || malId == mediaId
        else -> malId == mediaId
    }
}

private fun MediaEntry.hasConflictingProviderId(result: JikanSearchResult): Boolean {
    if (result.malId <= 0 && result.tmdbId == null) return false
    val resultSource = canonicalMediaSourceId(result.source)
    val entrySource = canonicalMediaSourceId(source)
    return when (resultSource) {
        "tmdb", "tmdb_account" -> {
            val resultId = result.tmdbId ?: result.malId.takeIf { it > 0 }
            val entryId = tmdbId ?: if (entrySource == "tmdb" || entrySource == "tmdb_account") malId?.takeIf { it > 0 } else null
            resultId != null && entryId != null && resultId != entryId
        }
        "anilist" -> {
            val resultId = result.malId.let { if (it >= 100_000_000) it - 100_000_000 else it }
            val entryId = malId?.let { if (it >= 100_000_000) it - 100_000_000 else it }
            resultId > 0 && entryId != null && entryId > 0 && resultId != entryId
        }
        "simkl" -> {
            val resultId = result.malId.takeIf { it > 0 }
            val entryId = simklId ?: if (entrySource == "simkl") malId?.takeIf { it > 0 } else null
            resultId != null && entryId != null && resultId != entryId
        }
        else -> result.malId > 0 && malId != null && malId != result.malId
    }
}

private fun MediaEntry.matchesTitleVariant(result: JikanSearchResult): Boolean {
    // Yalnızca `result.title`'a bakmak, ekranda seçili başlık diline göre değişen
    // GÖRÜNEN başlığı kimlik sanmak demekti. Kayıt ve sonuçtaki tüm başlık varyantlarını
    // normalize edip karşılaştır; sağlayıcı kapsamı matchesInSource() tarafından korunur.
    if (type != result.type) return false
    val entryTitles = listOfNotNull(title, titleEnglish, titleJapanese)
        .flatMap { com.kitsugi.animelist.model.MediaIdentity.normalizedTitleVariants(it) }
        .filter { it.length >= 2 }
        .toSet()
    val resultTitles = listOfNotNull(result.title, result.titleEnglish, result.titleJapanese, result.titleRomaji)
        .flatMap { com.kitsugi.animelist.model.MediaIdentity.normalizedTitleVariants(it) }
        .filter { it.length >= 2 }
        .toSet()
    if (entryTitles.none { it in resultTitles }) return false
    val entryYear = year
    val resultYear = result.year
    return entryYear == null || resultYear == null || kotlin.math.abs(entryYear - resultYear) <= 1
}

fun MediaEntry.matches(mediaId: Int, mediaSource: String): Boolean {
    if (canonicalMediaSourceId(this.source) == canonicalMediaSourceId(mediaSource)) {
        if (canonicalMediaSourceId(this.source) == "anilist") {
            val rawEntryId = if (this.malId != null && this.malId >= 100_000_000) this.malId - 100_000_000 else this.malId
            val rawMediaId = if (mediaId >= 100_000_000) mediaId - 100_000_000 else mediaId
            if (rawEntryId != null && rawEntryId == rawMediaId) {
                return true
            }
        } else {
            if (this.malId == mediaId) {
                return true
            }
            if (canonicalMediaSourceId(this.source) == "simkl" && this.simklId == mediaId) {
                return true
            }
        }
    }

    // TMDB ID eşleşmesi
    if (mediaSource.equals("tmdb", ignoreCase = true) && this.tmdbId == mediaId) {
        return true
    }

    val resultIsMal = mediaSource.equals("jikan", ignoreCase = true) || mediaSource.equals("mal", ignoreCase = true)
    val entryIsMal = this.source.equals("jikan", ignoreCase = true) || this.source.equals("mal", ignoreCase = true)
    val rMal = mediaId
    val eMal = if (entryIsMal) this.malId else (if (this.malId != null && this.malId < 100_000_000) this.malId else null)
    if (resultIsMal && eMal != null && rMal == eMal) {
        return true
    }

    return false
}

fun List<MediaEntry>.firstMatching(
    result: JikanSearchResult,
    isAniListConnected: Boolean = false,
    isMalConnected: Boolean = false,
    isSimklConnected: Boolean = false
): MediaEntry? {
    val src = result.source.lowercase()
    val exactMatch = this.firstOrNull { entry ->
        val entrySrc = entry.source.lowercase()
        (entrySrc == src || (entrySrc == "mal" && src == "jikan") || (entrySrc == "jikan" && src == "mal")) && entry.matches(result)
    }
    if (exactMatch != null) return exactMatch

    val isResultSourceConnected = when {
        src == "anilist" -> isAniListConnected
        src == "mal" || src == "jikan" -> isMalConnected
        src == "simkl" || src == "tmdb" -> isSimklConnected
        else -> false
    }
    if (isResultSourceConnected) {
        return null
    }

    return this.firstOrNull { entry ->
        entry.matches(result)
    }
}

fun List<MediaEntry>.firstMatching(
    mediaId: Int,
    mediaSource: String,
    isAniListConnected: Boolean = false,
    isMalConnected: Boolean = false,
    isSimklConnected: Boolean = false
): MediaEntry? {
    val src = mediaSource.lowercase()
    val exactMatch = this.firstOrNull { entry ->
        val entrySrc = entry.source.lowercase()
        (entrySrc == src || (entrySrc == "mal" && src == "jikan") || (entrySrc == "jikan" && src == "mal")) && entry.matches(mediaId, mediaSource)
    }
    if (exactMatch != null) return exactMatch

    val isResultSourceConnected = when {
        src == "anilist" -> isAniListConnected
        src == "mal" || src == "jikan" -> isMalConnected
        src == "simkl" || src == "tmdb" -> isSimklConnected
        else -> false
    }
    if (isResultSourceConnected) {
        return null
    }

    return this.firstOrNull { entry ->
        entry.matches(mediaId, mediaSource)
    }
}

fun KitsugiCharacterDetail.mergeWith(other: KitsugiCharacterDetail): KitsugiCharacterDetail {
    val mergedAlternativeNames = (this.alternativeNames + other.alternativeNames)
        .map { it.trim() }
        .filter { it.isNotBlank() && it != "null" }
        .distinct()

    val mergedVoiceActors = (this.voiceActors + other.voiceActors)
        .groupBy { it.name.lowercase().trim() }
        .map { (_, group) ->
            group.firstOrNull { !it.imageUrl.isNullOrBlank() } ?: group.first()
        }
        .sortedByLanguagePreference()

    val mergedMediaAppearances = (this.mediaAppearances + other.mediaAppearances)
        .groupBy { it.title.lowercase().trim() }
        .map { (_, group) ->
            group.firstOrNull { !it.imageUrl.isNullOrBlank() } ?: group.first()
        }

    val mergedBirthdayRaw = this.birthday.takeIf { !it.isNullOrBlank() && it != "null" && !it.startsWith("{") }
        ?: other.birthday.takeIf { !it.isNullOrBlank() && it != "null" && !it.startsWith("{") }
        ?: this.birthday ?: other.birthday
    val mergedAgeRaw = this.age.takeIf { !it.isNullOrBlank() && it != "null" } ?: other.age
    val (mergedBirthday, mergedAge) = KitsugiDateUtils.formatBirthdayAndCalculateAge(
        mergedBirthdayRaw,
        mergedAgeRaw
    )

    return this.copy(
        nativeName = this.nativeName.takeIf { !it.isNullOrBlank() && it != "null" } ?: other.nativeName,
        romanizedName = this.romanizedName ?: other.romanizedName,
        alternativeNames = mergedAlternativeNames,
        imageUrl = this.imageUrl.takeIf { !it.isNullOrBlank() && it != "null" } ?: other.imageUrl,
        gender = this.gender.takeIf { !it.isNullOrBlank() && it != "null" } ?: other.gender,
        age = mergedAge,
        birthday = mergedBirthday,
        bloodType = this.bloodType.takeIf { !it.isNullOrBlank() && it != "null" } ?: other.bloodType,
        biography = this.biography.takeIf { !it.isNullOrBlank() && it != "null" } ?: other.biography,
        voiceActors = mergedVoiceActors,
        mediaAppearances = mergedMediaAppearances,
        isFavourite = this.isFavourite || other.isFavourite,
        aniListId = this.aniListId ?: other.aniListId
    )
}

fun KitsugiStaffDetail.mergeWith(other: KitsugiStaffDetail): KitsugiStaffDetail {
    val mergedAlternativeNames = (this.alternativeNames + other.alternativeNames)
        .map { it.trim() }
        .filter { it.isNotBlank() && it != "null" }
        .distinct()

    val mergedCharacterRoles = (this.characterRoles + other.characterRoles)
        .groupBy { (it.characterName.lowercase().trim() + "_" + it.mediaTitle.lowercase().trim()) }
        .map { (_, group) ->
            group.firstOrNull { !it.characterImageUrl.isNullOrBlank() } ?: group.first()
        }

    val mergedMediaWorks = (this.mediaWorks + other.mediaWorks)
        .groupBy { (it.mediaTitle.lowercase().trim() + "_" + it.staffRole.lowercase().trim()) }
        .map { (_, group) ->
            group.firstOrNull { !it.mediaImageUrl.isNullOrBlank() } ?: group.first()
        }

    val mergedBirthdayRaw = this.birthday.takeIf { !it.isNullOrBlank() && it != "null" && !it.startsWith("{") }
        ?: other.birthday.takeIf { !it.isNullOrBlank() && it != "null" && !it.startsWith("{") }
        ?: this.birthday ?: other.birthday
    val mergedAgeRaw = this.age.takeIf { !it.isNullOrBlank() && it != "null" } ?: other.age
    val (mergedBirthday, mergedAge) = KitsugiDateUtils.formatBirthdayAndCalculateAge(
        mergedBirthdayRaw,
        mergedAgeRaw
    )

    val mergedOccupation = (this.occupation.takeIf { !it.isNullOrBlank() && it != "null" } ?: other.occupation)
        ?.toTurkishStaffRole()

    return this.copy(
        nativeName = this.nativeName.takeIf { !it.isNullOrBlank() && it != "null" } ?: other.nativeName,
        romanizedName = this.romanizedName ?: other.romanizedName,
        alternativeNames = mergedAlternativeNames,
        imageUrl = this.imageUrl.takeIf { !it.isNullOrBlank() && it != "null" } ?: other.imageUrl,
        biography = this.biography.takeIf { !it.isNullOrBlank() && it != "null" } ?: other.biography,
        occupation = mergedOccupation,
        birthday = mergedBirthday,
        age = mergedAge,
        gender = this.gender.takeIf { !it.isNullOrBlank() && it != "null" } ?: other.gender,
        homeTown = this.homeTown.takeIf { !it.isNullOrBlank() && it != "null" } ?: other.homeTown,
        characterRoles = mergedCharacterRoles,
        mediaWorks = mergedMediaWorks,
        isFavourite = this.isFavourite || other.isFavourite,
        aniListId = this.aniListId ?: other.aniListId
    )
}


