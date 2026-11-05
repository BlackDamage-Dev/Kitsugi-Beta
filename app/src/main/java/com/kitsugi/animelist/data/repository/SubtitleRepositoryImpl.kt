package com.kitsugi.animelist.data.repository

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.core.player.OpenSubtitlesHasher
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.local.ManagedAddonEntity
import com.kitsugi.animelist.data.remote.AddonStreamClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

data class Subtitle(
    val id: String,
    val url: String,
    val lang: String,
    val addonName: String,
    val addonLogo: String? = null,
    val format: String? = null // "ass", "srt", "vtt" vb.
)

/**
 * SubtitleRepository — Stremio /v11/ altyazı eklentilerinden altyazı çeker.
 * NuvioTV referans mimarisine uygun olarak:
 *  1. Aktif eklentileri çeker (addon.subtitleTypes != null veya altyazı eklentisi adını taşıyanlar).
 *  2. Tüm eklentileri PARALEL sorgular (async/awaitAll).
 *  3. OpenSubtitles için video hash'ini arka planda paralel hesaplar.
 *  4. Sonuçları URL'e göre tekilleştirir (deduplicate).
 */
class SubtitleRepositoryImpl(private val context: Context) {

    private val addonDao = KitsugiDatabase.getDatabase(context).managedAddonDao()
    private val addonClient = AddonStreamClient()

    companion object {
        private const val TAG = "SubtitleRepository"
        private const val PER_ADDON_TIMEOUT_MS = 6000L

        // Circuit breaker: manifestUrl -> Hata sayısı ve son hata zamanı
        private val circuitBreakers = ConcurrentHashMap<String, CircuitBreakerState>()

        private data class CircuitBreakerState(
            var failureCount: Int = 0,
            var lastFailureTimeMs: Long = 0L
        )
    }

    /**
     * Verilen içerik için tüm aktif altyazı eklentilerini paralel sorgular.
     */
    suspend fun getSubtitles(
        type: String,
        id: String,
        videoUrl: String? = null,
        videoHeaders: Map<String, String>? = null,
        filename: String? = null,
        aniListId: Int? = null,
        animeTitle: String? = null,
        episode: Int? = null,
        season: Int? = null,
        precomputedHash: OpenSubtitlesHasher.HashResult? = null,
        hashAlreadyComputed: Boolean = false
    ): List<Subtitle> = coroutineScope {

        val normalizedType = if (type.equals("tv", ignoreCase = true)) "series" else type.lowercase()

        // 1. Altyazı destekleyen enabled addon'ları al + filtrele
        val allAddons = addonDao.getEnabledAddons()
        val subtitleAddons = allAddons.filter { addon ->
            // subtitleTypes kontrolü:
            if (addon.subtitleTypes != null) {
                if (StremioSubtitleRouting.requestType(addon.subtitleTypes, normalizedType) == null) {
                    Log.d(TAG, "Addon '${addon.name}' atlandı: type=$normalizedType desteklenmiyor (desteklenenler: ${addon.subtitleTypes})")
                    return@filter false
                }
            } else {
                val looksLikeSubtitleAddon = looksLikeSubtitleAddon(addon)
                if (!looksLikeSubtitleAddon && addon.streamTypes != null) {
                    Log.d(TAG, "Addon '${addon.name}' atlandı: subtitleTypes=null ve stream-only addon gibi görünüyor")
                    return@filter false
                }
                Log.d(TAG, "Addon '${addon.name}' dahil edildi: subtitleTypes=null ama altyazı addon'u olabilir")
            }
            true
        }

        if (subtitleAddons.isEmpty()) {
            Log.w(TAG, "Hiç aktif altyazı addon'u bulunamadı — type=$normalizedType, id=$id")
            return@coroutineScope emptyList()
        }

        Log.d(TAG, "Altyazı fetch başlatılıyor: ${subtitleAddons.size} addon, type=$normalizedType, id=$id, filename=$filename")

        // 2. OpenSubtitles hash hesapla (arka planda, addon fetch'iyle eş zamanlı)
        val hashJob = async(Dispatchers.IO) {
            if (hashAlreadyComputed) return@async precomputedHash
            if (videoUrl.isNullOrBlank()) return@async null
            try {
                val result = OpenSubtitlesHasher.compute(videoUrl, videoHeaders)
                if (result != null) {
                    Log.d(TAG, "OpenSubtitles hash: hash=${result.hash}, size=${result.size}")
                }
                result
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (e: Exception) {
                Log.w(TAG, "Hash hesaplanamadı: ${e.message}")
                null
            }
        }

        // 3. Her addon için paralel fetch
        val deferredResults = subtitleAddons.map { addon ->
            async(Dispatchers.IO) {
                val manifestUrl = addon.manifestUrl
                val cbState = circuitBreakers.getOrPut(manifestUrl) { CircuitBreakerState() }
                val now = System.currentTimeMillis()
                if (cbState.failureCount >= 3 && now - cbState.lastFailureTimeMs < 5 * 60 * 1000) {
                    Log.w(TAG, "Circuit breaker tripped for addon '${addon.name}' (${manifestUrl}). Skipping query.")
                    return@async emptyList()
                }

                val hashResult = try { hashJob.await() } catch (_: Exception) { null }

                val extraParams = buildMap<String, String> {
                    if (hashResult != null) {
                        put("videoHash", hashResult.hash)
                        put("videoSize", hashResult.size.toString())
                    }
                    if (!filename.isNullOrBlank()) put("filename", filename)
                }

                var querySuccess = false
                var errorType: String? = null

                val subtitles = withTimeoutOrNull(PER_ADDON_TIMEOUT_MS) {
                    try {
                        val items = addonClient.fetchSubtitles(
                            context = context,
                            manifestUrl = addon.manifestUrl,
                            type = StremioSubtitleRouting.requestType(addon.subtitleTypes, normalizedType) ?: normalizedType,
                            id = id,
                            extraParams = extraParams.ifEmpty { null },
                            aniListId = aniListId,
                            animeTitle = animeTitle,
                            episode = episode,
                            season = season
                        )
                        querySuccess = true
                        Log.d(TAG, "Addon '${addon.name}': ${items.size} altyazı döndü")
                        items.mapNotNull { item ->
                            val url = item.url ?: return@mapNotNull null
                            val lang = item.lang ?: "unknown"
                            Subtitle(
                                id = item.id ?: url,
                                url = url,
                                lang = lang,
                                addonName = addon.name,
                                addonLogo = addon.icon,
                                format = normalizeDeclaredFormat(item.format) ?: guessFormat(url)
                            )
                        }
                    } catch (cancel: kotlinx.coroutines.CancellationException) {
                        throw cancel
                    } catch (e: Exception) {
                        errorType = "EXCEPTION: ${e.javaClass.simpleName} - ${e.message}"
                        Log.e(TAG, "Addon '${addon.name}' fetch hatası: $errorType")
                        emptyList()
                    }
                } ?: run {
                    errorType = "TIMEOUT (${PER_ADDON_TIMEOUT_MS}ms)"
                    Log.w(TAG, "Addon '${addon.name}' ${PER_ADDON_TIMEOUT_MS}ms timeout")
                    emptyList()
                }

                val targetCbState = circuitBreakers.getOrPut(manifestUrl) { CircuitBreakerState() }
                if (querySuccess) {
                    targetCbState.failureCount = 0
                } else {
                    targetCbState.failureCount += 1
                    targetCbState.lastFailureTimeMs = System.currentTimeMillis()
                    Log.w(TAG, "Addon '${addon.name}' failure registered ($errorType). Failure count: ${targetCbState.failureCount}/3")
                }

                subtitles
            }
        }

        val allSubtitles = deferredResults.awaitAll().flatten()
        Log.d(TAG, "Toplam ${allSubtitles.size} altyazı bulundu (${subtitleAddons.size} addon sorgulandı)")

        // 4. URL'e göre dedup
        val seen = LinkedHashSet<String>()
        val deduped = mutableListOf<Subtitle>()
        for (sub in allSubtitles) {
            if (seen.add(sub.url)) deduped += sub
        }

        deduped
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
     * Addon'un idPrefixes JSON array'ini parse eder.
     * Null → prefix filtresi yok (her ID ile eşleşir).
     */
    private fun parseIdPrefixes(addon: ManagedAddonEntity): List<String>? {
        val json = addon.idPrefixes ?: return null
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }.filter { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "idPrefixes parse hatası (addon='${addon.name}'): ${e.message}")
            null
        }
    }

    /** Addon'un beyan ettiği format adını iç temsile ("ass"/"srt"/"vtt") çevirir. */
    private fun normalizeDeclaredFormat(raw: String?): String? {
        val normalized = raw?.trim()?.lowercase()?.trimStart('.').orEmpty()
        return when (normalized) {
            "ass", "ssa" -> "ass"
            "srt", "subrip", "application/x-subrip" -> "srt"
            "vtt", "webvtt" -> "vtt"
            "ttml", "dfxp" -> "ttml"
            else -> null
        }
    }

    /**
     * URL uzantısından format tahmin eder.
     */
    private fun guessFormat(url: String): String? {
        val path = url.substringBefore("?").substringBefore('#').lowercase(java.util.Locale.ROOT)
        return when {
            path.endsWith(".ass") -> "ass"
            path.endsWith(".ssa") -> "ssa"
            path.endsWith(".srt") -> "srt"
            path.endsWith(".vtt") -> "vtt"
            path.endsWith(".ttml") -> "ttml"
            path.endsWith(".dfxp") -> "dfxp"
            path.endsWith(".sub") -> "sub"
            path.endsWith(".smi") || path.endsWith(".sami") -> "smi"
            else -> null // Bilinmeyeni SRT diye etiketleme; indirilen içerik imzasıyla tanınır.
        }
    }
}
