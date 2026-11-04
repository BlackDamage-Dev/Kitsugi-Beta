package com.kitsugi.animelist.data.remote

import java.util.Locale

/**
 * Detay sayfası "Videolar" sekmesindeki ortak video kategorileri.
 */
enum class MediaVideoCategory {
    /** Fragmanlar, tanıtım videoları (PV), teaser ve karakter tanıtımları. */
    TRAILER,

    /** Açılış müzikleri / jenerikleri (OP). */
    OPENING,

    /** Kapanış müzikleri / jenerikleri (ED). */
    ENDING,

    /** Klipler, kamera arkası, özel bakış ve diğer resmi videolar. */
    EXTRA
}

/**
 * Tüm kaynaklar arasında ortaklaştırılmış tekil video öğesi.
 *
 * [GalleryItem] ile aynı felsefeyi izler: hangi detay kaynağından (AniList, MAL, TMDB,
 * Shikimori, Kitsu, Simkl, Bangumi...) açılırsa açılsın video sağlayan tüm kaynakların
 * verileri toplanır, kanonik video anahtarına göre tekilleştirilir ve videoyu sağlayan
 * kaynak(lar) [source] / [sources] alanlarında korunur.
 */
data class MediaVideoItem(
    val key: String,
    val title: String,
    val videoUrl: String?,
    val category: MediaVideoCategory,
    val source: String,
    val sources: List<String> = listOf(canonicalVideoSource(source)).filter { it.isNotBlank() },
    val alternateUrls: List<String> = emptyList()
)

private val RAW_YOUTUBE_ID_REGEX = Regex("^[A-Za-z0-9_-]{8,64}$")
private val YOUTUBE_WATCH_V_REGEX = Regex("[?&]v=([A-Za-z0-9_-]{8,64})(?:[&#]|$)")
private val YOUTUBE_PATH_ID_REGEX = Regex(
    "(?:youtu\\.be/|youtube(?:-nocookie)?\\.com/(?:embed|shorts|live|v)/)([A-Za-z0-9_-]{8,64})(?:[?&#/]|$)",
    RegexOption.IGNORE_CASE
)
private val QUOTED_SONG_TITLE_REGEX = Regex("\"([^\"]{2,})\"")

private fun normalizeForTitleMatching(s: String): String {
    return s.trim()
        .replace('İ', 'i')
        .replace('I', 'i')
        .replace('ı', 'i')
        .lowercase(Locale.ROOT)
        .replace("\u0307", "")
}

private val GENERIC_TRAILER_TITLES: Set<String> = setOf(
    "fragmani izle",
    "fragmanı izle",
    "watch trailer",
    "fragman",
    "trailer",
    "teaser",
    "pv",
    "tanitim (pv)",
    "tanıtım (pv)",
    "promo (pv)",
    "pv / tanitim",
    "pv / tanıtım",
    "on izleme",
    "ön izleme",
    "preview",
    "video",
    "official trailer"
).map { normalizeForTitleMatching(it) }.toSet()

/**
 * Kaynak kimliğini standartlaştırır; bilinen platformlar kanonik küçük harf kimliğe,
 * bilinmeyen kaynaklar (ör. CS3 eklentisi) kırpılmış özgün adına döner.
 */
fun canonicalVideoSource(raw: String?): String {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return ""
    return when (trimmed.lowercase(Locale.ROOT)) {
        "mal", "jikan", "myanimelist", "jikan (mal)", "mal (jikan)" -> "mal"
        "anilist", "al" -> "anilist"
        "tmdb", "themoviedb", "tmdb_network", "tmdb-network", "tmdbnetwork" -> "tmdb"
        "shikimori", "shiki" -> "shikimori"
        "kitsu" -> "kitsu"
        "simkl" -> "simkl"
        "bangumi", "bgm" -> "bangumi"
        "animethemes", "animethemes.moe" -> "animethemes"
        "fandom" -> "fandom"
        else -> trimmed
    }
}

/**
 * Saf Kotlin YouTube video ID çıkarıcı (Android `Uri` bağımlılığı olmadan JVM testlerinde de çalışır).
 */
fun extractCanonicalYouTubeId(url: String?): String? {
    val clean = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (RAW_YOUTUBE_ID_REGEX.matches(clean)) return clean
    YOUTUBE_WATCH_V_REGEX.find(clean)?.groupValues?.getOrNull(1)?.let { return it }
    YOUTUBE_PATH_ID_REGEX.find(clean)?.groupValues?.getOrNull(1)?.let { return it }
    return null
}

/**
 * Aynı videonun farklı kaynaklardan gelmesi durumunda tekilleştirme anahtarı üretir.
 */
internal fun videoDedupKey(item: MediaVideoItem): String {
    val ytId = extractCanonicalYouTubeId(item.videoUrl)
    if (ytId != null) return "yt:$ytId"

    val directUrl = item.videoUrl?.trim()?.takeIf { it.isNotEmpty() }
    if (directUrl != null) {
        val normalized = directUrl
            .substringBefore('#')
            .trimEnd('/')
            .lowercase(Locale.ROOT)
        return "url:$normalized"
    }

    val songKey = extractSongDedupToken(item.title)
    return "text:${item.category.name}:$songKey"
}

internal fun extractSongDedupToken(label: String): String {
    val quoted = QUOTED_SONG_TITLE_REGEX.find(label)?.groupValues?.getOrNull(1)
    val base = (quoted ?: label)
        .lowercase(Locale.ROOT)
        .replace(Regex("\\((?:op|ed)\\s*\\d*[^)]*\\)"), " ")
        .replace(Regex("^\\d+\\s*:\\s*"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), "")
        .trim()
    return base.ifEmpty { label.trim().lowercase(Locale.ROOT) }
}

private fun isGenericVideoTitle(title: String): Boolean {
    val norm = normalizeForTitleMatching(title)
    return norm.isEmpty() || norm in GENERIC_TRAILER_TITLES
}

private fun shouldReplaceVideoTitle(
    existingTitle: String,
    existingSource: String,
    incomingTitle: String,
    incomingSource: String,
    preferredSource: String? = null
): Boolean {
    val existingClean = existingTitle.trim()
    val incomingClean = incomingTitle.trim()
    if (incomingClean.isEmpty()) return false
    if (existingClean.isEmpty()) return true

    val existingGeneric = isGenericVideoTitle(existingClean)
    val incomingGeneric = isGenericVideoTitle(incomingClean)
    if (existingGeneric && !incomingGeneric) return true
    if (!existingGeneric && incomingGeneric) return false

    val canonicalIncoming = canonicalVideoSource(incomingSource)
    val canonicalExisting = canonicalVideoSource(existingSource)
    val canonicalPref = canonicalVideoSource(preferredSource)

    if (canonicalPref.isNotEmpty()) {
        if (canonicalIncoming == canonicalPref && canonicalExisting != canonicalPref) {
            return true
        }
        if (canonicalExisting == canonicalPref && canonicalIncoming != canonicalPref) {
            return false
        }
    }

    // İkisi de açıklayıcıysa tırnaklı şarkı adı veya daha zengin başlığı koru
    val existingHasQuotes = existingClean.contains('"')
    val incomingHasQuotes = incomingClean.contains('"')
    if (!existingHasQuotes && incomingHasQuotes) return true
    if (existingHasQuotes && !incomingHasQuotes) return false

    return incomingClean.length > existingClean.length && existingClean.length < 25
}

private fun categoryPriority(category: MediaVideoCategory): Int = when (category) {
    MediaVideoCategory.OPENING -> 0
    MediaVideoCategory.ENDING -> 1
    MediaVideoCategory.TRAILER -> 2
    MediaVideoCategory.EXTRA -> 3
}

private fun pickBetterCategory(a: MediaVideoCategory, b: MediaVideoCategory): MediaVideoCategory =
    if (categoryPriority(b) < categoryPriority(a)) b else a

/**
 * Çoklu kaynaktan gelen video adaylarını birleştirir, aynı URL/YouTube ID'ye sahip
 * kayıtları tekilleştirirken tüm katkıda bulunan kaynakları [MediaVideoItem.sources]
 * altında korur ve kullanıcıya en temiz sırada döndürür.
 */
fun mergeAndOrderMediaVideos(
    candidates: List<MediaVideoItem>,
    preferredSource: String? = null
): List<MediaVideoItem> {
    if (candidates.isEmpty()) return emptyList()
    val canonicalPreferred = canonicalVideoSource(preferredSource)

    // Oynatılabilir OP/ED şarkıları varken aynı şarkının salt-metin (videoUrl == null) yedeğini ele.
    val playableOpeningTokens = HashSet<String>()
    val playableEndingTokens = HashSet<String>()
    var hasAnyPlayableOpening = false
    var hasAnyPlayableEnding = false

    for (c in candidates) {
        if (!c.videoUrl.isNullOrBlank()) {
            when (c.category) {
                MediaVideoCategory.OPENING -> {
                    hasAnyPlayableOpening = true
                    playableOpeningTokens += extractSongDedupToken(c.title)
                }
                MediaVideoCategory.ENDING -> {
                    hasAnyPlayableEnding = true
                    playableEndingTokens += extractSongDedupToken(c.title)
                }
                else -> Unit
            }
        }
    }

    val filtered = candidates.filter { item ->
        if (!item.videoUrl.isNullOrBlank()) return@filter true
        if (item.title.isBlank()) return@filter false
        val token = extractSongDedupToken(item.title)
        when (item.category) {
            MediaVideoCategory.OPENING -> !hasAnyPlayableOpening || token !in playableOpeningTokens
            MediaVideoCategory.ENDING -> !hasAnyPlayableEnding || token !in playableEndingTokens
            else -> false
        }
    }

    data class Accumulator(
        var title: String,
        var titleSource: String,
        var videoUrl: String?,
        var category: MediaVideoCategory,
        val sources: LinkedHashSet<String>,
        val alternateUrls: LinkedHashSet<String> = LinkedHashSet()
    )

    val mergedByKey = LinkedHashMap<String, Accumulator>()

    for (raw in filtered) {
        val normSources = (raw.sources + raw.source)
            .map { canonicalVideoSource(it) }
            .filter { it.isNotEmpty() }
        if (normSources.isEmpty() && raw.title.isBlank()) continue

        val normalizedItem = raw.copy(
            source = normSources.firstOrNull().orEmpty(),
            sources = normSources.distinct()
        )
        val dedupKey = videoDedupKey(normalizedItem)
        val existing = mergedByKey[dedupKey]
        if (existing == null) {
            val set = LinkedHashSet<String>()
            normSources.forEach { set += it }
            val altSet = LinkedHashSet<String>()
            normalizedItem.alternateUrls.forEach { altSet += it }
            mergedByKey[dedupKey] = Accumulator(
                title = normalizedItem.title.trim(),
                titleSource = normalizedItem.source,
                videoUrl = normalizedItem.videoUrl?.trim()?.takeIf { it.isNotEmpty() },
                category = normalizedItem.category,
                sources = set,
                alternateUrls = altSet
            )
        } else {
            if (shouldReplaceVideoTitle(
                    existingTitle = existing.title,
                    existingSource = existing.titleSource,
                    incomingTitle = normalizedItem.title,
                    incomingSource = normalizedItem.source,
                    preferredSource = canonicalPreferred
                )
            ) {
                existing.title = normalizedItem.title.trim()
                existing.titleSource = normalizedItem.source
            }
            if (existing.videoUrl.isNullOrBlank() && !normalizedItem.videoUrl.isNullOrBlank()) {
                existing.videoUrl = normalizedItem.videoUrl.trim()
            }
            existing.category = pickBetterCategory(existing.category, normalizedItem.category)
            normSources.forEach { existing.sources += it }
            normalizedItem.alternateUrls.forEach { existing.alternateUrls += it }
        }
    }

    val assembled = mergedByKey.entries.mapIndexed { idx, (dedupKey, acc) ->
        val orderedSources = acc.sources.sortedWith(
            compareBy<String> { src ->
                when {
                    canonicalPreferred.isNotEmpty() && src == canonicalPreferred -> 0
                    acc.category in setOf(MediaVideoCategory.OPENING, MediaVideoCategory.ENDING) && src == "animethemes" -> 1
                    else -> 2
                }
            }
        )
        val primary = orderedSources.firstOrNull().orEmpty()
        MediaVideoItem(
            key = "${acc.category.name.lowercase(Locale.ROOT)}_${idx}_$dedupKey",
            title = acc.title,
            videoUrl = acc.videoUrl,
            category = acc.category,
            source = primary,
            sources = orderedSources,
            alternateUrls = acc.alternateUrls.toList()
        )
    }

    return assembled.sortedWith(
        compareBy<MediaVideoItem>(
            { categoryPriority(it.category) },
            { if (it.videoUrl.isNullOrBlank()) 1 else 0 },
            { item ->
                when (item.category) {
                    MediaVideoCategory.OPENING, MediaVideoCategory.ENDING -> {
                        when {
                            "animethemes" in item.sources -> 0
                            canonicalPreferred.isNotEmpty() && canonicalPreferred in item.sources -> 1
                            else -> 2
                        }
                    }
                    else -> {
                        when {
                            canonicalPreferred.isNotEmpty() && canonicalPreferred in item.sources -> 0
                            else -> 1
                        }
                    }
                }
            }
        )
    )
}
