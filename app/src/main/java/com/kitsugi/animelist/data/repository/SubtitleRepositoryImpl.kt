package com.kitsugi.animelist.data.repository

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.core.player.OpenSubtitlesHasher
import com.kitsugi.animelist.core.player.model.Subtitle
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.local.ManagedAddonEntity
import com.kitsugi.animelist.data.remote.AddonStreamClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException

/** Searches enabled subtitle addons in parallel, isolating provider/query failures.
 * Stream-specific manifest ID prefixes are not used to filter subtitle resources.
 */
class SubtitleRepositoryImpl(private val context: Context) {

    companion object {
        private const val TAG = "SubtitleRepository"
        private const val PER_ADDON_TIMEOUT_MS = 45_000L
    }

    data class Query(val type: String, val id: String)

    private val database = KitsugiDatabase.getDatabase(context.applicationContext)
    private val addonDao = database.managedAddonDao()
    private val addonClient = AddonStreamClient()

    /**
     * Tüm aktif altyazı addon'larından paralel olarak altyazı listesi çeker.
     *
     * @param type        İçerik tipi: "series" veya "movie"
     * @param id          Stremio ID (örn: "tt1234567:1:5" veya "kitsu:12345:5")
     * @param videoUrl    Video URL'si (OpenSubtitles hash hesabı için; null ise hash atlanır)
     * @param videoHeaders Video HTTP header'ları (hash isteği için)
     * @param filename    Dosya adı (addon'lara ipucu olarak verilir)
     */
    suspend fun getSubtitles(
        type: String,
        id: String,
        videoUrl: String? = null,
        videoHeaders: Map<String, String>? = null,
        filename: String? = null,
        aniListId: Int? = null,
        animeTitle: String? = null,
        episode: Int? = null
    ): List<Subtitle> = getSubtitlesForQueries(
        listOf(Query(type, id)), videoUrl, videoHeaders, filename, aniListId, animeTitle, episode
    )

    /** Search all enabled providers once per playback; one provider failing must not hide others. */
    suspend fun getSubtitlesForQueries(
        queries: List<Query>,
        videoUrl: String? = null,
        videoHeaders: Map<String, String>? = null,
        filename: String? = null,
        aniListId: Int? = null,
        animeTitle: String? = null,
        episode: Int? = null
    ): List<Subtitle> = coroutineScope {
        val normalizedQueries = queries.map {
            Query(if (it.type.equals("tv", true)) "series" else it.type.lowercase(), it.id.trim())
        }.filter { it.id.isNotEmpty() }.distinct()
        if (normalizedQueries.isEmpty()) return@coroutineScope emptyList()
        val addons = addonDao.getEnabledAddons().filter { addon ->
            addon.subtitleTypes != null || addon.streamTypes == null || looksLikeSubtitleAddon(addon)
        }
        if (addons.isEmpty()) Log.w(TAG, "No enabled subtitle providers")

        // Hash once, not once per ID/provider. Failure must never prevent ID-only searches.
        val hashJob = async(Dispatchers.IO) {
            if (videoUrl.isNullOrBlank()) null else OpenSubtitlesHasher.compute(videoUrl, videoHeaders)
        }
        val hash = hashJob.await()
        val extras = buildMap<String, String> {
            if (hash != null) {
                put("videoHash", hash.hash)
                put("videoSize", hash.size.toString())
            }
            if (!filename.isNullOrBlank()) put("filename", filename)
        }
        addons.map { addon ->
            async(Dispatchers.IO) {
                val supported = addon.subtitleTypes?.split(',')?.map { it.trim().lowercase() }
                val applicable = normalizedQueries.filter {
                    supported == null || "*" in supported || it.type in supported
                }.let { if (addon.manifestUrl.contains("anisub.co", ignoreCase = true)) it.take(1) else it }
                applicable.map { query ->
                    async {
                        withTimeoutOrNull(PER_ADDON_TIMEOUT_MS) {
                            try {
                                addonClient.fetchSubtitles(
                                    context, addon.manifestUrl, query.type, query.id,
                                    extras.ifEmpty { null }, aniListId, animeTitle, episode
                                ).mapNotNull { item ->
                                    if (!com.kitsugi.animelist.core.player.SubtitleEpisodeValidator.isCompatible(
                                            query.type, query.id, item.season.subtitleIntOrNull(), item.episode.subtitleIntOrNull(), item.subtitleFileName, item.movieReleaseName
                                        )) return@mapNotNull null
                                    val url = item.url?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                                    Subtitle(
                                        item.id ?: url, url, item.lang ?: "unknown", addon.name, addon.icon,
                                        guessFormat(url),
                                        listOf(item.label, item.releaseInfo, item.subtitleFileName, item.title, item.movieReleaseName)
                                            .firstOrNull { !it.isNullOrBlank() }
                                    )
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w(TAG, "Subtitle provider '${addon.name}' failed for ${query.type}/${query.id}: ${e.message}")
                                emptyList()
                            }
                        } ?: run {
                            Log.w(TAG, "Subtitle provider '${addon.name}' timed out for ${query.type}/${query.id}")
                            emptyList()
                        }
                    }
                }.awaitAll().flatten()
            }
        }.awaitAll().flatten().distinctBy {
            // AltyaziDB signs different URLs for IMDb and TMDB but keeps a stable subtitle ID.
            it.addonName to if (it.id.startsWith("altyazidb:")) it.id else it.url
        }
    }

    /**
     * Addon'un isim veya URL'sine bakarak altyazı addon'u olup olmadığını tahmin eder.
     * subtitleTypes = null olan eski kayıtlar için fallback filtre.
     */
    private fun looksLikeSubtitleAddon(addon: ManagedAddonEntity): Boolean {
        val combined = (addon.name + " " + addon.manifestUrl).lowercase()
        return combined.contains("subtitle") ||
            combined.contains("altyaz") ||
            combined.contains("opensubtitle") ||
            combined.contains("yts-subtitle") ||
            combined.contains("turkcealtyazi") ||
            combined.contains("caption") ||
            combined.contains("sub.strem")
    }

    /**
     * URL uzantısından format tahmin eder.
     */
    private fun guessFormat(url: String): String {
        val path = url.substringBefore("?").lowercase()
        return when {
            path.endsWith(".ass") || path.endsWith(".ssa") -> "ass"
            path.endsWith(".vtt") -> "vtt"
            path.endsWith(".ttml") || path.endsWith(".dfxp") -> "ttml"
            else -> "srt"
        }
    }
}

/** Tolerate optional metadata encoded as a JSON number, string, null, or object. */
private fun com.google.gson.JsonElement?.subtitleIntOrNull(): Int? =
    if (this != null && isJsonPrimitive && asJsonPrimitive.isNumber) runCatching { asInt }.getOrNull()
    else if (this != null && isJsonPrimitive && asJsonPrimitive.isString) asString.toIntOrNull()
    else null
