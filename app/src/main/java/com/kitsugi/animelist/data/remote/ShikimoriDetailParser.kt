package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.model.MediaType
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime
import java.util.Locale

/**
 * Shikimori detay yanıtındaki, ana [KitsugiMediaDetail] eşlemesinden bağımsız okunabilen
 * alanları tek yerde toplar. REST yanıtındaki `[null]` başlıkları ve Rusça liste-durumu
 * adları gibi sağlayıcıya özgü ayrıntılar burada normalize edilir.
 */
internal object ShikimoriDetailParser {

    fun parse(data: JSONObject, mediaType: MediaType): ShikimoriDetailMetadata {
        val isManga = mediaType == MediaType.Manga
        val englishTitles = data.optJSONArray("english").stringValues()
        val japaneseTitles = data.optJSONArray("japanese").stringValues()
        val rawKind = data.cleanString("kind")
        val episodesAired = if (isManga) null else data.nonNegativeInt("episodes_aired")
        val season = if (isManga) null else parseSeason(data.cleanString("aired_on"))
        val stats = parseStats(data)

        val alternativeNames = buildList {
            addAll(data.optJSONArray("synonyms").stringValues())
            // API birden fazla İngilizce/Japonca ad döndürebilir. İlk adlar kendi
            // satırlarında gösterilir; kalanlar "Diğer Adlar" içinde kaybolmaz.
            addAll(englishTitles.drop(1))
            addAll(japaneseTitles.drop(1))
            data.cleanString("license_name_ru")?.let(::add)
        }.distinctBy { it.lowercase(Locale.ROOT) }

        return ShikimoriDetailMetadata(
            englishTitle = englishTitles.firstOrNull(),
            japaneseTitle = japaneseTitles.firstOrNull(),
            russianTitle = data.cleanString("russian"),
            synonyms = alternativeNames,
            status = displayStatus(data.cleanString("status"), isManga),
            rawStatus = data.cleanString("status"),
            season = season?.display,
            rawSeason = season?.raw,
            format = displayFormat(rawKind),
            rawFormat = rawKind,
            volumes = if (isManga) data.positiveInt("volumes") else null,
            episodesAired = episodesAired,
            nextAiringEpisode = if (isManga) {
                null
            } else {
                nextAiringEpisode(data.cleanString("next_episode_at"), episodesAired)
            },
            publishers = if (isManga) parseNamedObjects(data.optJSONArray("publishers")) else emptyList(),
            licensors = data.optJSONArray("licensors").stringValues(),
            fansubbers = if (isManga) emptyList() else data.optJSONArray("fansubbers").stringValues(),
            fandubbers = if (isManga) emptyList() else data.optJSONArray("fandubbers").stringValues(),
            isFavourite = data.optBoolean("favoured", false),
            scoredBy = stats.scoreDistribution.sumOf { it.amount }.takeIf { it > 0 },
            members = listOfNotNull(
                stats.watching, stats.completed, stats.planned, stats.dropped, stats.paused
            ).sum().takeIf { it > 0 },
            nativeStats = stats
        )
    }

    /** Shikimori'nin Rusça veya İngilizce döndürebildiği liste durumlarını okur. */
    private fun parseStats(data: JSONObject): KitsugiStats {
        val scoreStats = data.optJSONArray("rates_scores_stats")
        val scores = mutableListOf<KitsugiScoreStat>()
        if (scoreStats != null) {
            for (index in 0 until scoreStats.length()) {
                val item = scoreStats.optJSONObject(index) ?: continue
                val score = item.optString("name").trim().toIntOrNull() ?: continue
                val amount = item.optInt("value", 0)
                if (score in 1..10 && amount >= 0) {
                    scores += KitsugiScoreStat(score = score, amount = amount)
                }
            }
        }

        var watching = 0
        var completed = 0
        var planned = 0
        var dropped = 0
        var paused = 0
        var hasWatching = false
        var hasCompleted = false
        var hasPlanned = false
        var hasDropped = false
        var hasPaused = false

        val statusStats = data.optJSONArray("rates_statuses_stats")
        if (statusStats != null) {
            for (index in 0 until statusStats.length()) {
                val item = statusStats.optJSONObject(index) ?: continue
                val amount = item.optInt("value", 0).coerceAtLeast(0)
                when (statusKey(item.optString("name"))) {
                    StatusKey.WATCHING -> {
                        watching += amount
                        hasWatching = true
                    }
                    StatusKey.COMPLETED -> {
                        completed += amount
                        hasCompleted = true
                    }
                    StatusKey.PLANNED -> {
                        planned += amount
                        hasPlanned = true
                    }
                    StatusKey.DROPPED -> {
                        dropped += amount
                        hasDropped = true
                    }
                    StatusKey.PAUSED -> {
                        paused += amount
                        hasPaused = true
                    }
                    StatusKey.UNKNOWN -> Unit
                }
            }
        }

        val scoreDistribution = if (scores.any { it.amount > 0 }) {
            scores
                .groupBy { it.score }
                .map { (score, rows) -> KitsugiScoreStat(score, rows.sumOf { it.amount }) }
                .sortedBy { it.score }
        } else {
            emptyList()
        }

        return KitsugiStats(
            watching = watching.takeIf { hasWatching && it > 0 },
            completed = completed.takeIf { hasCompleted && it > 0 },
            planned = planned.takeIf { hasPlanned && it > 0 },
            dropped = dropped.takeIf { hasDropped && it > 0 },
            paused = paused.takeIf { hasPaused && it > 0 },
            scoreDistribution = scoreDistribution
        )
    }

    private fun statusKey(raw: String): StatusKey = when (raw.trim().lowercase(Locale.ROOT)) {
        "watching", "reading", "смотрю", "читаю",
        "rewatching", "rereading", "пересматриваю", "перечитываю" -> StatusKey.WATCHING
        "completed", "просмотрено", "прочитано" -> StatusKey.COMPLETED
        "planned", "plan_to_watch", "plan_to_read", "запланировано" -> StatusKey.PLANNED
        "dropped", "брошено" -> StatusKey.DROPPED
        "on_hold", "on hold", "paused", "отложено" -> StatusKey.PAUSED
        else -> StatusKey.UNKNOWN
    }

    private fun displayStatus(raw: String?, isManga: Boolean): String? = when (raw?.lowercase(Locale.ROOT)) {
        "released" -> if (isManga) "Finished" else "Finished Airing"
        "ongoing" -> if (isManga) "Publishing" else "Currently Airing"
        "anons" -> if (isManga) "Not yet published" else "Not yet aired"
        "paused" -> "On Hiatus"
        "discontinued" -> "Discontinued"
        null -> null
        else -> raw
    }

    private fun displayFormat(raw: String?): String? = when (raw?.lowercase(Locale.ROOT)) {
        "tv" -> "TV"
        "movie" -> "Movie"
        "ova" -> "OVA"
        "ona" -> "ONA"
        "special" -> "Special"
        "tv_special" -> "TV Special"
        "music" -> "Music"
        "pv" -> "PV"
        "cm" -> "CM"
        "manga" -> "Manga"
        "manhwa" -> "Manhwa"
        "manhua" -> "Manhua"
        "one_shot" -> "One-shot"
        "doujin" -> "Doujinshi"
        "novel" -> "Novel"
        "light_novel" -> "Light Novel"
        null -> null
        else -> raw.replace('_', ' ').replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
        }
    }

    private fun parseSeason(rawDate: String?): Season? {
        val date = rawDate ?: return null
        val year = date.take(4).toIntOrNull() ?: return null
        val month = date.split('-').getOrNull(1)?.toIntOrNull() ?: return null
        val raw = when (month) {
            in 1..3 -> "WINTER"
            in 4..6 -> "SPRING"
            in 7..9 -> "SUMMER"
            in 10..12 -> "FALL"
            else -> return null
        }
        val name = raw.lowercase(Locale.ROOT).replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
        }
        return Season(raw = raw, display = "$name $year")
    }

    private data class Season(val raw: String, val display: String)

    private fun nextAiringEpisode(rawDate: String?, episodesAired: Int?): String? {
        val value = rawDate ?: return null
        val epoch = runCatching {
            OffsetDateTime.parse(value).toInstant().epochSecond
        }.getOrNull() ?: return null
        // Yayınlanan sayı bilinmiyorsa 1. bölüm uydurma; ortak formatta -1 "bilinmiyor"dur.
        val nextEpisode = episodesAired?.plus(1) ?: -1
        return "$nextEpisode|$epoch"
    }

    private fun parseNamedObjects(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.cleanString("name")?.let(::add)
            }
        }.distinctBy { it.lowercase(Locale.ROOT) }
    }

    private fun JSONArray?.stringValues(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val value = opt(index) as? String ?: continue
                value.cleanString()?.let(::add)
            }
        }.distinctBy { it.lowercase(Locale.ROOT) }
    }

    private fun JSONObject.cleanString(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).cleanString() else null

    private fun String.cleanString(): String? =
        trim().takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

    private fun JSONObject.positiveInt(key: String): Int? =
        nonNegativeInt(key)?.takeIf { it > 0 }

    private fun JSONObject.nonNegativeInt(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key).takeIf { it >= 0 } else null

    private enum class StatusKey { WATCHING, COMPLETED, PLANNED, DROPPED, PAUSED, UNKNOWN }

    // Shikimori Types::ExternalLink::WATCH_ONLINE_KINDS + tanınan görünen ad eşleri.
    private val streamingSites = setOf(
        "crunchyroll", "netflix", "hidive", "hulu", "ivi", "wink", "okko",
        "kinopoisk hd", "youtube", "amazon", "amazon prime", "amazon prime video",
        "disney+", "disney plus"
    )

    /** Aynı bağlantının hem platform hem harici bağlantı olarak tekrarlanmasını önler. */
    fun isStreamingSite(site: String): Boolean = site.trim().lowercase(Locale.ROOT) in streamingSites

    /** Özellikle aynı ada sahip birden fazla Vikipedi bağlantısını ayırt eder. */
    fun externalLinkLanguage(kind: String, url: String): String? {
        if (!kind.equals("wikipedia", ignoreCase = true)) return null
        val host = runCatching { java.net.URI(url).host?.lowercase(Locale.ROOT) }.getOrNull()
            ?: return null
        val language = host.substringBefore(".wikipedia.org")
            .takeIf { it != host && it.isNotBlank() }
            ?: return null
        return language.uppercase(Locale.ROOT)
    }
}

internal data class ShikimoriDetailMetadata(
    val englishTitle: String?,
    val japaneseTitle: String?,
    val russianTitle: String?,
    val synonyms: List<String>,
    val status: String?,
    val rawStatus: String?,
    val season: String?,
    val rawSeason: String?,
    val format: String?,
    val rawFormat: String?,
    val volumes: Int?,
    val episodesAired: Int?,
    val nextAiringEpisode: String?,
    val publishers: List<String>,
    val licensors: List<String>,
    val fansubbers: List<String>,
    val fandubbers: List<String>,
    val isFavourite: Boolean,
    val scoredBy: Int?,
    val members: Int?,
    val nativeStats: KitsugiStats
)
