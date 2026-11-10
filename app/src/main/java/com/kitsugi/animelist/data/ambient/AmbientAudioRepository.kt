package com.kitsugi.animelist.data.ambient

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Coordinates the no-key catalogs, a small persistent cache and a recent-play history.
 * Cache entries are intentionally local to this install and expire after 12 hours; the app never
 * fans requests out across proxy hosts or otherwise tries to evade a provider's rate limits.
 */
class AmbientAudioRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val client = ambientAudioHttpClient
    private val openverse = OpenverseAudioSource(client)
    private val internetArchive = InternetArchiveAudioSource(client)
    private val audius = AudiusAudioSource(client)
    private val wikimediaCommons = WikimediaCommonsAudioSource(client)
    private val ccMixter = CcMixterAudioSource(client)
    private val freeToUse = FreeToUseAudioSource(client)
    private val radioBrowser = RadioBrowserAudioSource(client)
    private val jamendo = JamendoAudioSource(client)
    private val animeThemes = AnimeThemesAudioSource(client)
    private val youTubeMusic = YouTubeMusicAudioSource(client)

    suspend fun search(
        query: String,
        sources: Set<AmbientAudioSource>,
        plan: AmbientAudioPlan,
        /** Jamendo kişisel `client_id`; boşsa Jamendo atlanır (diğer kaynaklar çalışır). */
        jamendoClientId: String = "",
    ): AmbientAudioSearchResult = searchInternal(
        query = query,
        sources = sources.toList().sortedBy { it.key },
        plan = plan,
        cacheScope = "manual",
        includeLiveRadio = true,
        allowGenericFallback = true,
        showEmptyWarning = true,
        jamendoClientId = jamendoClientId,
    )

    /** Search the reader's manga title first, then add style/context discoveries to the queue. */
    suspend fun searchForManga(
        sources: Set<AmbientAudioSource>,
        plan: AmbientAudioPlan,
        /** Jamendo kişisel `client_id`; boşsa Jamendo atlanır. */
        jamendoClientId: String = "",
    ): AmbientAudioSearchResult {
        val enabledSources = sources.toList().sortedBy { it.key }
        if (enabledSources.isEmpty()) {
            return AmbientAudioSearchResult(emptyList(), listOf("Arama için en az bir kaynak aç."))
        }

        val titleResult = if (plan.titleQuery.isNotBlank()) {
            // Live radio cannot be meaningfully searched by manga title and has no track metadata.
            searchInternal(
                query = plan.titleQuery,
                sources = enabledSources.filterNot { it == AmbientAudioSource.RADIO_BROWSER },
                plan = plan,
                cacheScope = "manga-title",
                includeLiveRadio = false,
                allowGenericFallback = false,
                showEmptyWarning = false,
                jamendoClientId = jamendoClientId,
            )
        } else {
            AmbientAudioSearchResult(emptyList())
        }
        val titleMatches = titleResult.tracks.filter { AmbientAudioPlanner.isTitleMatch(it, plan) }
        val history = recentlyPlayedTrackIds()
        val freshTitleMatchCount = titleMatches.count { track ->
            track.historyKeys().none(history::contains)
        }

        val needsMoodFallback = AmbientAudioPlanner.shouldSearchMoodContext(freshTitleMatchCount)
        // v2.4.483 — kullanıcı isteği: "en alakalıdan en alakasıza kadar TÜM kaynaklar
        // arasında sıralı çalma". Bu yüzden başlık eşleşmesi yeterli olsa bile bağlam
        // araması tüm kaynaklarda yapılır; sıralamayı yine `orderForAutoplay` belirler
        // (başlık eşleşmesi +1000 puan aldığı için her zaman en başta kalır).
        val contextSources = enabledSources
        val contextResult = if (contextSources.isNotEmpty()) {
            searchInternal(
                query = plan.query,
                sources = contextSources,
                plan = plan,
                cacheScope = "manga-context",
                includeLiveRadio = true,
                allowGenericFallback = needsMoodFallback,
                showEmptyWarning = false,
                jamendoClientId = jamendoClientId,
            )
        } else {
            AmbientAudioSearchResult(emptyList())
        }
        val ordered = AmbientAudioPlanner
            .orderForAutoplay(titleMatches, contextResult.tracks, plan, history)
            .take(MAX_QUEUE_TRACKS)
        val warnings = (titleResult.warnings + contextResult.warnings).distinct().toMutableList()
        if (ordered.isEmpty() && warnings.isEmpty()) {
            warnings += "Bu manga için eşleşme bulunamadı; farklı kaynakları veya tür/anahtar kelimeleri deneyebilirsin."
        }
        return AmbientAudioSearchResult(
            tracks = ordered,
            warnings = warnings,
            fromCache = titleResult.fromCache && (contextSources.isEmpty() || contextResult.fromCache),
            titleMatchCount = ordered.count { AmbientAudioPlanner.isTitleMatch(it, plan) },
            contextMatchCount = ordered.count { !AmbientAudioPlanner.isTitleMatch(it, plan) },
        )
    }

    private suspend fun searchInternal(
        query: String,
        sources: List<AmbientAudioSource>,
        plan: AmbientAudioPlan,
        cacheScope: String,
        includeLiveRadio: Boolean,
        allowGenericFallback: Boolean,
        showEmptyWarning: Boolean,
        jamendoClientId: String = "",
    ): AmbientAudioSearchResult {
        val normalizedQuery = query.trim().replace(Regex("\\s+"), " ").take(MAX_QUERY_LENGTH)
        if (normalizedQuery.isBlank()) {
            return AmbientAudioSearchResult(emptyList(), listOf("Önce bir arama ifadesi yaz."))
        }
        if (sources.isEmpty()) return AmbientAudioSearchResult(emptyList())

        val key = cacheKey(normalizedQuery, sources, cacheScope)
        readCache(key)?.let { cached ->
            return AmbientAudioSearchResult(
                tracks = AmbientAudioPlanner.filterAndRank(cached, plan, recentlyPlayedTrackIds()),
                fromCache = true,
            )
        }

        val warnings = mutableListOf<String>()
        val fetched = searchSources(normalizedQuery, sources, plan, warnings, includeLiveRadio, jamendoClientId)
        var ranked = AmbientAudioPlanner.filterAndRank(fetched, plan, recentlyPlayedTrackIds())

        // One restrained mood-search fallback is useful when no enabled source has a playable hit.
        // It uses a single enabled Openverse dataset, rather than fanning out again.
        if (allowGenericFallback && ranked.isEmpty()) {
            val fallbackSource = sources.firstOrNull { it.openverseSource != null }
            val fallbackQuery = GENERIC_FALLBACK_QUERY
            if (fallbackSource != null && !normalizedQuery.equals(fallbackQuery, ignoreCase = true)) {
                val fallback = try {
                    openverse.search(fallbackQuery, fallbackSource)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    warnings += sourceWarning(fallbackSource, error)
                    emptyList()
                }
                if (fallback.isNotEmpty()) {
                    warnings += "Bağlama özel sonuç çıkmadı; genel ambiyans/müzik araması denendi."
                    ranked = AmbientAudioPlanner.filterAndRank(fallback, plan, recentlyPlayedTrackIds())
                    if (ranked.isNotEmpty()) {
                        writeCache(cacheKey(fallbackQuery, listOf(fallbackSource), "fallback"), fallback)
                    }
                }
            }
        }

        if (fetched.isNotEmpty()) writeCache(key, fetched)
        if (showEmptyWarning && ranked.isEmpty() && warnings.isEmpty()) {
            warnings += "Bu arama için oynatılabilir bir ses akışı bulunamadı. Farklı bir tür/anahtar kelime deneyebilirsin."
        }
        return AmbientAudioSearchResult(tracks = ranked, warnings = warnings.distinct())
    }

    fun recentlyPlayedTrackIds(): Set<String> =
        preferences.getString(KEY_RECENT_TRACKS, "")
            .orEmpty()
            .lineSequence()
            .filter(String::isNotBlank)
            .toSet()

    fun recordPlayed(track: AmbientAudioTrack) {
        val keys = track.historyKeys()
        val current = preferences.getString(KEY_RECENT_TRACKS, "")
            .orEmpty()
            .lineSequence()
            .filter(String::isNotBlank)
            .filterNot { it in keys }
            .toMutableList()
        current.addAll(0, keys.toList())
        preferences.edit().putString(KEY_RECENT_TRACKS, current.take(MAX_RECENT_TRACK_KEYS).joinToString("\n")).apply()
    }

    private suspend fun searchSources(
        query: String,
        sources: List<AmbientAudioSource>,
        plan: AmbientAudioPlan,
        warnings: MutableList<String>,
        includeLiveRadio: Boolean,
        jamendoClientId: String,
    ): List<AmbientAudioTrack> = coroutineScope {
        sources.map { source ->
            async {
                try {
                    when {
                        source == AmbientAudioSource.RADIO_BROWSER && !includeLiveRadio -> emptyList()
                        source.openverseSource != null -> openverse.search(query, source)
                        source == AmbientAudioSource.INTERNET_ARCHIVE -> internetArchive.search(query, plan)
                        source == AmbientAudioSource.AUDIUS -> audius.search(query)
                        source == AmbientAudioSource.WIKIMEDIA_COMMONS -> wikimediaCommons.search(query, plan)
                        source == AmbientAudioSource.CCMIXTER -> ccMixter.search(query)
                        source == AmbientAudioSource.FREE_TO_USE -> freeToUse.search(query)
                        source == AmbientAudioSource.RADIO_BROWSER -> radioBrowser.search(query, plan)
                        // Jamendo BYOK: anahtar yoksa sessizce atlanır (panelde sabit uyarı var).
                        source == AmbientAudioSource.JAMENDO && jamendoClientId.isBlank() -> emptyList()
                        source == AmbientAudioSource.JAMENDO -> jamendo.search(jamendoClientId, query)
                        // Anime OP/ED şarkılarının kendisi (doğrudan .ogg, çözücü yok).
                        source == AmbientAudioSource.ANIMETHEMES -> animeThemes.search(query)
                        // YouTube Music: arama; akış çalma anında çözülür.
                        source == AmbientAudioSource.YOUTUBE -> youTubeMusic.search(query)
                        else -> emptyList()
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    synchronized(warnings) { warnings += sourceWarning(source, error) }
                    emptyList()
                }
            }
        }.awaitAll().flatten()
    }

    private fun sourceWarning(source: AmbientAudioSource, error: Throwable): String {
        val description = error.message.orEmpty()
        return when {
            description.contains("rate limited", ignoreCase = true) && source.openverseSource != null ->
                "Openverse geçici istek sınırına ulaştı; biraz sonra tekrar dene."
            description.contains("HTTP 429") ->
                "${source.label.substringBefore(" ·")} isteği geçici olarak sınırlandı; biraz sonra tekrar dene."
            source == AmbientAudioSource.JAMENDO ->
                "Jamendo için kişisel Client ID gerekir (Harici Entegrasyonlar → Müzik)."
            source == AmbientAudioSource.INTERNET_ARCHIVE ->
                "Internet Archive şu an yanıt vermedi; diğer kaynaklar kullanılabilir."
            source == AmbientAudioSource.ANIMETHEMES ->
                "AnimeThemes'te bu başlık için OP/ED kaydı bulunamadı; diğer kaynaklar kullanılabilir."
            source == AmbientAudioSource.YOUTUBE ->
                "YouTube Music şu an yanıt vermedi; diğer kaynaklar kullanılabilir."
            else -> "${source.label.substringBefore(" ·")} şu an yanıt vermedi; diğer kaynaklar kullanılabilir."
        }
    }

    private fun cacheKey(query: String, sources: List<AmbientAudioSource>, scope: String): String {
        val raw = scope + "|" + query.lowercase() + "|" + sources.map { it.key }.sorted().joinToString(",")
        val hash = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
            .take(12)
            .joinToString("") { "%02x".format(it) }
        return "$CACHE_PREFIX$hash"
    }

    private fun readCache(key: String): List<AmbientAudioTrack>? {
        val raw = preferences.getString(key, null) ?: return null
        return runCatching {
            val root = JSONObject(raw)
            val savedAt = root.optLong("savedAt", 0L)
            if (savedAt <= 0L || System.currentTimeMillis() - savedAt > CACHE_TTL_MS) {
                preferences.edit().remove(key).apply()
                return null
            }
            val items = root.optJSONArray("tracks") ?: return null
            buildList {
                for (index in 0 until items.length()) {
                    items.optJSONObject(index)?.toTrack()?.let(::add)
                }
            }
        }.getOrNull()
    }

    private fun writeCache(key: String, tracks: List<AmbientAudioTrack>) {
        runCatching {
            val root = JSONObject()
                .put("savedAt", System.currentTimeMillis())
                .put("tracks", JSONArray().apply { tracks.forEach { put(it.toJson()) } })
            preferences.edit().putString(key, root.toString()).apply()
            pruneCache()
        }
    }

    private fun pruneCache() {
        val cacheEntries = preferences.all
            .filterKeys { it.startsWith(CACHE_PREFIX) }
            .mapNotNull { (key, value) ->
                val savedAt = (value as? String)?.let { raw ->
                    runCatching { JSONObject(raw).optLong("savedAt") }.getOrNull()
                } ?: 0L
                key to savedAt
            }
            .sortedByDescending { it.second }
        if (cacheEntries.size > MAX_CACHE_ENTRIES) {
            preferences.edit().apply {
                cacheEntries.drop(MAX_CACHE_ENTRIES).forEach { (key, _) -> remove(key) }
            }.apply()
        }
    }

    private fun AmbientAudioTrack.toJson(): JSONObject = JSONObject()
        .put("id", id.cappedAmbientAudioValue())
        .put("title", title.cappedAmbientAudioValue())
        .put("creator", creator.cappedAmbientAudioValue())
        .put("streamUrl", streamUrl.cappedAmbientAudioValue())
        .put("landingUrl", landingUrl.cappedAmbientAudioValue())
        .put("provider", provider.cappedAmbientAudioValue())
        .put("source", source.cappedAmbientAudioValue())
        .put("durationMs", durationMs ?: 0L)
        .put("fileType", fileType.cappedAmbientAudioValue())
        .put("fileSizeBytes", fileSizeBytes ?: 0L)
        .put("downloadUrl", downloadUrl?.cappedAmbientAudioValue() ?: "")
        .put("tags", JSONArray(tags.map { it.cappedAmbientAudioValue() }))

    private fun JSONObject.toTrack(): AmbientAudioTrack? {
        fun value(key: String, defaultValue: String = ""): String =
            optString(key, defaultValue).cappedAmbientAudioValue()

        val id = value("id")
        val title = value("title")
        val streamUrl = value("streamUrl")
        if (id.isBlank() || title.isBlank() || streamUrl.isBlank()) return null
        val tagsArray = optJSONArray("tags") ?: JSONArray()
        val tags = (0 until minOf(tagsArray.length(), MAX_CACHED_TAGS)).mapNotNull { index ->
            tagsArray.optString(index).cappedAmbientAudioValue().takeIf(String::isNotBlank)
        }
        fun longValue(key: String): Long? = when (val raw = opt(key)) {
            is Number -> raw.toLong()
            is String -> raw.cappedAmbientAudioValue().toLongOrNull()
            else -> null
        }
        return AmbientAudioTrack(
            id = id,
            title = title,
            creator = value("creator", "Unknown creator"),
            streamUrl = streamUrl,
            landingUrl = value("landingUrl"),
            provider = value("provider"),
            source = value("source"),
            durationMs = longValue("durationMs")?.takeIf { it > 0L },
            fileType = value("fileType"),
            tags = tags,
            fileSizeBytes = longValue("fileSizeBytes")?.takeIf { it > 0L },
            downloadUrl = value("downloadUrl").takeIf(String::isNotBlank),
        )
    }

    private companion object {
        const val PREFS_NAME = "ambient_audio_catalog_cache"
        const val KEY_RECENT_TRACKS = "recent_track_ids"
        const val CACHE_PREFIX = "search_"
        const val MAX_QUERY_LENGTH = 120
        /** Kuyrukta tutulacak en fazla parça (alakalıdan alakasıza sıralı). */
        const val MAX_QUEUE_TRACKS = 40
        const val MAX_RECENT_TRACK_KEYS = 80
        const val MAX_CACHE_ENTRIES = 36
        const val MAX_CACHED_TAGS = 48
        const val CACHE_TTL_MS = 12L * 60L * 60L * 1_000L
        const val GENERIC_FALLBACK_QUERY = "instrumental ambient background music"
    }
}

/** One anonymous Openverse request every three seconds is slower than the burst allowance. */
internal object OpenverseRequestLimiter {
    private val mutex = Mutex()
    private var nextRequestAtMs = 0L

    suspend fun awaitSlot() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val waitForMs = (nextRequestAtMs - now).coerceAtLeast(0L)
            if (waitForMs > 0L) delay(waitForMs)
            nextRequestAtMs = System.currentTimeMillis() + MIN_REQUEST_INTERVAL_MS
        }
    }

    private const val MIN_REQUEST_INTERVAL_MS = 3_100L
}
