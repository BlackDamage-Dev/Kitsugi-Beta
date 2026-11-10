package com.kitsugi.animelist.data.trailer

import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.kitsugi.animelist.BuildConfig
import com.kitsugi.animelist.core.network.IPv4FirstDns
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "InAppYouTubeExtractor"
private const val EXTRACTOR_TIMEOUT_MS = 30_000L
private const val PREFERRED_SEPARATE_CLIENT = "android_vr"

private val VIDEO_ID_REGEX = Regex("^[a-zA-Z0-9_-]{11}$")
private val API_KEY_REGEX = Regex("\"INNERTUBE_API_KEY\":\"([^\"]+)\"")
private val VISITOR_DATA_REGEX = Regex("\"VISITOR_DATA\":\"([^\"]+)\"")
private val QUALITY_LABEL_REGEX = Regex("(\\d{2,4})p")

// Uses internal data class YouTubeClient from YouTubePlayerClients.kt

private data class WatchConfig(
    val apiKey: String?,
    val visitorData: String?
)

private data class StreamCandidate(
    val client: String,
    val priority: Int,
    val url: String,
    val score: Double,
    val hasN: Boolean,
    val itag: String,
    val height: Int,
    val fps: Int,
    val ext: String
)

private data class ManifestCandidate(
    val client: String,
    val manifestUrl: String,
    val width: Int,
    val height: Int,
    val bandwidth: Long
)

private val DEFAULT_HEADERS = mapOf(
    "accept-language" to "en-US,en;q=0.9",
    "user-agent" to YouTubeRequestPolicy.STREAM_USER_AGENT
)

private val CLIENTS = listOf(
    // TVHTML5_SIMPLY_EMBEDDED_PLAYER: login‐required‐immune, always returns OK for public videos.
    // Highest priority so we always get at least a working HLS manifest.
    YouTubeClient(
        key = "tv_embedded",
        id = "85",
        version = "2.0",
        userAgent = "Mozilla/5.0 (SMART-TV; LINUX; Tizen 6.0) AppleWebKit/538.1 " +
            "(KHTML, like Gecko) Version/6.0 TV Safari/538.1",
        context = mapOf(
            "clientName" to "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
            "clientVersion" to "2.0",
            "clientScreen" to "EMBED",
            "hl" to "en",
            "gl" to "US"
        ),
        priority = 0,
        embedUrl = "https://www.youtube.com",
        needsVisitorData = false
    ),
    // ANDROID_VR (Meta Quest): best source for adaptive video/audio streams.
    YouTubeClient(
        key = "android_vr",
        id = "28",
        version = "1.60.45",
        userAgent = "com.google.android.apps.youtube.vr.oculus/1.60.45 " +
            "(Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1) gzip",
        context = mapOf(
            "clientName" to "ANDROID_VR",
            "clientVersion" to "1.60.45",
            "deviceMake" to "Oculus",
            "deviceModel" to "Quest 3",
            "osName" to "Android",
            "osVersion" to "12",
            "platform" to "MOBILE",
            "androidSdkVersion" to 32,
            "hl" to "en",
            "gl" to "US"
        ),
        priority = 1
    ),
    // ANDROID: broad format support, updated to ~Q4 2026 version.
    YouTubeClient(
        key = "android",
        id = "3",
        version = "20.38.6",
        userAgent = "com.google.android.youtube/20.38.6 (Linux; U; Android 14; en_US) gzip",
        context = mapOf(
            "clientName" to "ANDROID",
            "clientVersion" to "20.38.6",
            "osName" to "Android",
            "osVersion" to "14",
            "platform" to "MOBILE",
            "androidSdkVersion" to 34,
            "hl" to "en",
            "gl" to "US"
        ),
        priority = 2
    ),
    // IOS: independent auth path, updated to ~Q4 2026 version.
    YouTubeClient(
        key = "ios",
        id = "5",
        version = "20.38.1",
        userAgent = "com.google.ios.youtube/20.38.1 (iPhone16,2; U; CPU iOS 18_0 like Mac OS X)",
        context = mapOf(
            "clientName" to "IOS",
            "clientVersion" to "20.38.1",
            "deviceModel" to "iPhone16,2",
            "osName" to "iPhone",
            "osVersion" to "18.0.0.22A3354",
            "platform" to "MOBILE",
            "hl" to "en",
            "gl" to "US"
        ),
        priority = 3
    )
)

class InAppYouTubeExtractor {
    private val gson = Gson()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .dns(IPv4FirstDns())
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    // --- Cached watch config (api key + visitor data) ---
    private data class CachedConfig(
        val apiKey: String,
        val visitorData: String?,
        val fetchedAt: Long = System.currentTimeMillis()
    )

    private val cachedConfig = AtomicReference<CachedConfig?>(null)
    private val configMutex = Mutex()

    companion object {
        /** A process-wide extractor reuses config and HTTP resources across detail-video switches. */
        val shared: InAppYouTubeExtractor by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            InAppYouTubeExtractor()
        }

        /** How long cached visitor_data stays valid before a proactive refresh. */
        private const val CONFIG_TTL_MS = 3 * 60 * 60 * 1000L // 3 hours

        /**
         * Çözümlenmiş imzalı akış adresleri YouTube tarafından ~6 saat geçerli tutulur.
         * Muhafazakâr bir pencere seçiyoruz: adres önbellekten döndüğünde hâlâ geçerli.
         */
        private const val PLAYBACK_SOURCE_CACHE_TTL_MS = 45 * 60 * 1000L // 45 minutes

        /** Birincil akış açılamazsa denenecek en fazla yedek video akışı sayısı. */
        private const val MAX_ALTERNATE_VIDEO_SOURCES = 3
    }

    /**
     * İndirme için gereken ham kaynak listesi.
     *
     * `TrailerPlaybackSource` yalnızca **oynatılabilir** adresi taşır (çoğunlukla YouTube
     * HLS master playlist'i). İndirme için segment listesini çözebilmek adına extractor'ın
     * gördüğü tüm adaylar (HLS manifest, ayrı video/ses, progressive) gerekir.
     */
    data class YouTubeDownloadCandidates(
        val videoId: String,
        val hlsManifestUrl: String?,
        val adaptiveVideoUrl: String?,
        val adaptiveAudioUrl: String?,
        val progressiveUrl: String?
    )

    /**
     * HLS manifest'ini media playlist'e indirger; master geldiyse en yüksek varyanta iner.
     * İndirme motoru bu yardımcıyı kullanır, böylece HLS çözümleme mantığı tek yerde kalır.
     */
    internal suspend fun resolveHlsMediaPlaylist(
        manifestUrl: String
    ): YouTubeHlsMediaPlaylist? = withContext(Dispatchers.IO) {
        var currentUrl = manifestUrl
        repeat(3) {
            val response = try {
                performRequest(
                    url = currentUrl,
                    method = "GET",
                    headers = YouTubeRequestPolicy.youtubePlaybackHeaders(DEFAULT_HEADERS)
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "HLS playlist okunamadı: ${e.message}")
                return@withContext null
            }
            if (!response.ok) {
                Log.w(TAG, "HLS playlist isteği başarısız (${response.status})")
                return@withContext null
            }

            val media = YouTubeHlsMediaPlaylistParser.parse(currentUrl, response.body) ?: return@withContext null
            if (media.segmentUrls.isNotEmpty()) return@withContext media

            // Segment yoksa bu bir master playlist: en yüksek varyanta in.
            val variant = YouTubeHlsManifestParser.parse(currentUrl, response.body)?.selectedVariantUrl
                ?: return@withContext null
            currentUrl = variant
        }
        null
    }

    /**
     * Bir YouTube videosu için indirilebilir akış adaylarını döner (kalite sırasıyla).
     * Hiçbir aday yoksa `null`.
     */
    suspend fun resolveDownloadCandidates(youtubeUrl: String): YouTubeDownloadCandidates? =
        withContext(Dispatchers.IO) {
            val videoId = extractVideoId(youtubeUrl) ?: return@withContext null
            repeat(2) { attempt ->
                try {
                    val candidates = withTimeout(EXTRACTOR_TIMEOUT_MS) {
                        collectCandidatesForDownload(videoId, forceRefreshConfig = attempt > 0)
                    }
                    if (candidates != null) return@withContext candidates
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (error: Exception) {
                    Log.w(TAG, "Download candidate resolution failed (attempt=${attempt + 1}): ${error.message}")
                }
            }
            null
        }

    /**
     * Returns cached watch config, fetching from watch page only if:
     *  - No cached config exists yet (first call)
     *  - Cache is older than CONFIG_TTL_MS
     *  - [forceRefresh] is true (e.g. after LOGIN_REQUIRED)
     */
    private suspend fun ensureWatchConfig(forceRefresh: Boolean = false): CachedConfig {
        // Fast path: return valid cache without locking
        if (!forceRefresh) {
            val current = cachedConfig.get()
            if (current != null && !isConfigStale(current)) {
                return current
            }
        }

        // Slow path: fetch new config under mutex (only one fetch at a time)
        return configMutex.withLock {
            // Double-check after acquiring lock
            if (!forceRefresh) {
                val current = cachedConfig.get()
                if (current != null && !isConfigStale(current)) {
                    return@withLock current
                }
            }

            Log.d(TAG, "Fetching watch page for visitor_data (forceRefresh=$forceRefresh)")
            val watchUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&hl=en"
            val watchResponse = performRequest(
                url = watchUrl,
                method = "GET",
                headers = DEFAULT_HEADERS
            )
            if (!watchResponse.ok) {
                // If we have a stale config, prefer it over failing
                val stale = cachedConfig.get()
                if (stale != null) {
                    Log.w(TAG, "Watch page failed (${watchResponse.status}), using stale config")
                    return@withLock stale
                }
                throw IllegalStateException("Failed to fetch watch page (${watchResponse.status})")
            }

            val parsed = getWatchConfig(watchResponse.body)
            val apiKey = parsed.apiKey ?: "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8" // fallback key
            val newConfig = CachedConfig(
                apiKey = apiKey,
                visitorData = parsed.visitorData
            )
            cachedConfig.set(newConfig)
            Log.d(TAG, "Watch config cached (visitor=${!parsed.visitorData.isNullOrBlank()})")
            newConfig
        }
    }

    private fun isConfigStale(config: CachedConfig): Boolean {
        return System.currentTimeMillis() - config.fetchedAt > CONFIG_TTL_MS
    }

    /** Invalidate cached config so next extraction re-fetches watch page. */
    fun invalidateConfig() {
        cachedConfig.set(null)
        Log.d(TAG, "Watch config invalidated")
    }

    /**
     * Aynı video için art arda yapılan çözümlemeleri tekilleştirir.
     *
     * NEDEN: küçük oynatıcı her açılışta tam bir YouTube çözümlemesi yapıyordu (watch
     * sayfası + 4 istemci için player API). Kullanıcı satırı kapatıp yeniden açtığında
     * bu süre baştan yaşanıyor ve bazı denemeler YouTube tarafında 429'a takılıp
     * "video çalışmadı" olarak görünüyordu. İmzalı akış adresleri ~6 saat geçerli
     * olduğundan bu süre boyunca önbellek güvenli.
     */
    private val sourceCache = com.kitsugi.animelist.core.memory.BoundedCache<String, CachedPlaybackSource>(
        name = "youtube.playback.source",
        maxEntries = 60
    )

    fun invalidatePlaybackSource(videoId: String) {
        sourceCache.remove(videoId)
    }

    fun clearPlaybackSourceCache() {
        sourceCache.clear()
    }

    internal fun headersForClient(clientKey: String): Map<String, String> {
        val client = CLIENTS.firstOrNull { it.key == clientKey } ?: return emptyMap()
        return mapOf(
            "User-Agent" to client.userAgent,
            "Origin" to "https://www.youtube.com",
            "Referer" to "https://www.youtube.com/"
        )
    }


    private data class CachedPlaybackSource(
        val source: TrailerPlaybackSource,
        val cachedAtMs: Long = System.currentTimeMillis()
    )

    suspend fun extractPlaybackSource(youtubeUrl: String): TrailerPlaybackSource? = withContext(Dispatchers.IO) {
        if (youtubeUrl.isBlank()) return@withContext null

        val videoId = extractVideoId(youtubeUrl)
        if (videoId != null) {
            val cached = sourceCache[videoId]
            if (cached != null && System.currentTimeMillis() - cached.cachedAtMs < PLAYBACK_SOURCE_CACHE_TTL_MS) {
                Log.d(TAG, "Playback source cache hit for $videoId")
                return@withContext cached.source
            }
        }

        Log.d(TAG, "Starting Kotlin extraction for ${summarizeUrl(youtubeUrl)}")
        var source: TrailerPlaybackSource? = null
        try {
            source = withTimeout(EXTRACTOR_TIMEOUT_MS) {
                extractPlaybackSourceInternal(youtubeUrl, forceRefreshConfig = false)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (error: Exception) {
            Log.w(TAG, "Kotlin extractor failed for $youtubeUrl: ${error.message}")
        }

        // Retry with fresh config if first attempt returned nothing
        if (source == null) {
            Log.d(TAG, "First attempt failed, retrying with fresh watch config...")
            try {
                source = withTimeout(EXTRACTOR_TIMEOUT_MS) {
                    extractPlaybackSourceInternal(youtubeUrl, forceRefreshConfig = true)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (error: Exception) {
                Log.w(TAG, "Kotlin extractor retry failed for $youtubeUrl: ${error.message}")
            }
        }

        if (source == null) {
            Log.w(TAG, "Kotlin extraction returned no playable source for ${summarizeUrl(youtubeUrl)}")
        } else {
            if (videoId != null) {
                sourceCache[videoId] = CachedPlaybackSource(source)
            }
            Log.d(
                TAG,
                "Kotlin extraction success for ${summarizeUrl(youtubeUrl)} " +
                    "(video=${summarizeUrl(source.videoUrl)}, audioPresent=${!source.audioUrl.isNullOrBlank()})"
            )
        }

        source
    }

    private suspend fun extractPlaybackSourceInternal(
        youtubeUrl: String,
        forceRefreshConfig: Boolean
    ): TrailerPlaybackSource? {
        val videoId = extractVideoId(youtubeUrl) ?: return null
        val collected = collectCandidates(videoId, forceRefreshConfig) ?: return null

        val bestProgressive = sortCandidates(collected.progressive).firstOrNull()
        val bestVideo = pickBestForClient(collected.adaptiveVideo, PREFERRED_SEPARATE_CLIENT)
        val bestAudio = pickBestForClient(collected.adaptiveAudio, PREFERRED_SEPARATE_CLIENT)
        val bestManifest = collected.bestManifest

        // Prefer the multiplexed HLS stream when available. Adaptive YouTube formats are
        // frequently signed per-client and can become unusable between extraction and the
        // first media request; selecting one here caused the inline player to sit at 00:00
        // even though the same video had a valid HLS manifest. HLS also avoids the fragile
        // separate video/audio merge and remains seekable in Media3.
        if (bestManifest != null) {
            Log.d(TAG, "Using HLS manifest from ${bestManifest.client} (${bestManifest.height}p)")
            val altSources = buildAlternateSources(
                progressive = collected.progressive,
                adaptiveVideo = collected.adaptiveVideo,
                adaptiveAudio = collected.adaptiveAudio,
                excludeUrl = bestManifest.manifestUrl
            )
            return TrailerPlaybackSource(
                videoUrl = bestManifest.manifestUrl,
                audioUrl = null,
                // HLS manifest'i 403/yaş kısıtı/boş playlist dönebiliyor. O anda oynatıcı
                // siyah ekranda 00:00'da kalıyordu; artık aynı çıkarımdan elde edilmiş
                // adaptive/progressive adayları sırayla denenecek.
                alternateVideoUrls = altSources.videoUrls,
                alternateAudioUrls = altSources.audioUrls
            )
        }

        // No HLS available — try adaptive video + audio (best quality, separate streams).
        kotlinx.coroutines.yield()
        val resolvedVideo = bestVideo?.url?.let { resolveReachableUrl(it) }
        val resolvedAudio = if (resolvedVideo != null) bestAudio?.url?.let { resolveReachableUrl(it) } else null

        if (resolvedVideo != null) {
            return TrailerPlaybackSource(videoUrl = resolvedVideo, audioUrl = resolvedAudio)
        }

        // No HLS/adaptive source — try progressive (combined video+audio, usually low quality)
        val resolvedProgressive = bestProgressive?.url?.let { resolveReachableUrl(it) }
        if (resolvedProgressive != null) {
            return TrailerPlaybackSource(videoUrl = resolvedProgressive, audioUrl = null)
        }

        return null
    }

    /**
     * Oynatıcının, birincil akış açılamadığında sırayla deneyeceği yedek akışlar.
     *
     * HLS manifest'i YouTube tarafında imzalandıktan sonra da geçersizleşebiliyor
     * (özellikle yaş kısıtlı / telif uyarılı kısa videolarda manifest 403 döner). Tek
     * URL ile devam etmek "video oynamıyor" şikâyetinin en sık nedeniydi.
     */
    private suspend fun buildAlternateSources(
        progressive: List<StreamCandidate>,
        adaptiveVideo: List<StreamCandidate>,
        adaptiveAudio: List<StreamCandidate>,
        excludeUrl: String
    ): AlternateSources {
        val videoUrls = mutableListOf<String>()
        val audioUrls = mutableListOf<String?>()

        val sortedVideo = adaptiveVideo
            .sortedWith(compareByDescending<StreamCandidate> { it.height }.thenByDescending { it.score })
            .take(MAX_ALTERNATE_VIDEO_SOURCES)
        // Yedekler için `resolveReachableUrl` çağrılmaz: her aday için ek bir ağ sondası
        // yapmak çıkarım bütçesini (30 sn) tüketip asıl akışı da kaybettiriyordu.
        // İmzalı adresler doğrudan kullanılır; açılmazsa sıradaki adaya geçilir.
        //
        // Ses akışı, video akışıyla AYNI istemciden seçilmelidir: imza istemciye bağlıdır,
        // farklı istemcinin sesi `403` dönebilir.
        val audioByClient = adaptiveAudio
            .sortedByDescending { it.score }
            .groupBy { it.client }
            .mapValues { (_, list) -> list.first().url }
        val fallbackAudio = adaptiveAudio.sortedByDescending { it.score }.firstOrNull()?.url
        for (candidate in sortedVideo) {
            val video = candidate.url
            if (video == excludeUrl || videoUrls.contains(video)) continue
            videoUrls += video
            audioUrls += audioByClient[candidate.client] ?: fallbackAudio
        }

        // Sesli tek dosya (progressive, genelde 360p) — son ve en uyumlu yedek.
        val progressiveUrl = progressive
            .sortedWith(compareByDescending<StreamCandidate> { it.height }.thenByDescending { it.score })
            .firstOrNull { it.url != excludeUrl && !videoUrls.contains(it.url) }
            ?.url
        if (progressiveUrl != null) {
            videoUrls += progressiveUrl
            audioUrls += null
        }

        return AlternateSources(videoUrls, audioUrls)
    }

    private class AlternateSources(
        val videoUrls: List<String>,
        val audioUrls: List<String?>
    )

    /**
     * player API sondajının ham sonucu. Oynatma yolu bunun yalnızca "en iyi" adaylarını
     * kullanır; indirme yolu HLS manifestini segmentlerine ayırabilmek için ham listeyi görür.
     */
    private class CollectedCandidates(
        val progressive: List<StreamCandidate>,
        val adaptiveVideo: List<StreamCandidate>,
        val adaptiveAudio: List<StreamCandidate>,
        val bestManifest: ManifestCandidate?
    )

    private suspend fun collectCandidates(
        videoId: String,
        forceRefreshConfig: Boolean
    ): CollectedCandidates? {
        // Use cached config instead of fetching watch page every time
        val config = ensureWatchConfig(forceRefresh = forceRefreshConfig)
        Log.d(TAG, "Using config: apiKey=${config.apiKey.take(10)}... visitor=${!config.visitorData.isNullOrBlank()}")

        val progressive = mutableListOf<StreamCandidate>()
        val adaptiveVideo = mutableListOf<StreamCandidate>()
        val adaptiveAudio = mutableListOf<StreamCandidate>()
        val manifestUrls = mutableListOf<Triple<String, Int, String>>()
        var loginRequiredCount = 0

        for (client in CLIENTS) {
            kotlinx.coroutines.yield()
            try {
                val playerResponse = fetchPlayerResponse(
                    apiKey = config.apiKey,
                    videoId = videoId,
                    client = client,
                    visitorData = config.visitorData,
                    cookieHeader = null
                )

                // Check for LOGIN_REQUIRED which means visitor_data is stale
                val playabilityStatus = playerResponse.mapValue("playabilityStatus")
                val status = playabilityStatus?.stringValue("status")
                if (status == "LOGIN_REQUIRED") {
                    loginRequiredCount++
                    Log.w(TAG, "Client ${client.key}: LOGIN_REQUIRED (visitor may be stale)")
                    continue
                }
                if (status != null && status != "OK") {
                    continue
                }

                val streamingData = playerResponse.mapValue("streamingData") ?: continue
                val hlsManifestUrl = streamingData.stringValue("hlsManifestUrl")
                if (!hlsManifestUrl.isNullOrBlank()) {
                    manifestUrls += Triple(client.key, client.priority, hlsManifestUrl)
                }

                for (format in streamingData.listMapValue("formats")) {
                    val url = format.stringValue("url") ?: continue
                    val mimeType = format.stringValue("mimeType").orEmpty()
                    if (!mimeType.contains("video/") && mimeType.isNotBlank()) continue

                    val height = (format.numberValue("height")
                        ?: parseQualityLabel(format.stringValue("qualityLabel"))?.toDouble()
                        ?: 0.0).toInt()
                    val fps = (format.numberValue("fps") ?: 0.0).toInt()
                    val bitrate = format.numberValue("bitrate")
                        ?: format.numberValue("averageBitrate")
                        ?: 0.0

                    progressive += StreamCandidate(
                        client = client.key,
                        priority = client.priority,
                        url = url,
                        score = videoScore(height, fps, bitrate),
                        hasN = hasNParam(url),
                        itag = format.stringValue("itag").orEmpty(),
                        height = height,
                        fps = fps,
                        ext = if (mimeType.contains("webm")) "webm" else "mp4"
                    )
                }

                for (format in streamingData.listMapValue("adaptiveFormats")) {
                    val url = format.stringValue("url") ?: continue
                    val mimeType = format.stringValue("mimeType").orEmpty()
                    val hasVideo = mimeType.contains("video/")
                    val hasAudio = mimeType.contains("audio/") || mimeType.startsWith("audio/")

                    if (hasVideo) {
                        val height = (format.numberValue("height")
                            ?: parseQualityLabel(format.stringValue("qualityLabel"))?.toDouble()
                            ?: 0.0).toInt()
                        val fps = (format.numberValue("fps") ?: 0.0).toInt()
                        val bitrate = format.numberValue("bitrate")
                            ?: format.numberValue("averageBitrate")
                            ?: 0.0

                        adaptiveVideo += StreamCandidate(
                            client = client.key,
                            priority = client.priority,
                            url = url,
                            score = videoScore(height, fps, bitrate),
                            hasN = hasNParam(url),
                            itag = format.stringValue("itag").orEmpty(),
                            height = height,
                            fps = fps,
                            ext = if (mimeType.contains("webm")) "webm" else "mp4"
                        )
                    } else if (hasAudio) {
                        val bitrate = format.numberValue("bitrate")
                            ?: format.numberValue("averageBitrate")
                            ?: 0.0
                        val asr = format.numberValue("audioSampleRate") ?: 0.0

                        adaptiveAudio += StreamCandidate(
                            client = client.key,
                            priority = client.priority,
                            url = url,
                            score = audioScore(bitrate, asr),
                            hasN = hasNParam(url),
                            itag = format.stringValue("itag").orEmpty(),
                            height = 0,
                            fps = 0,
                            ext = if (mimeType.contains("webm")) "webm" else "m4a"
                        )
                    }
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Client ${client.key} failed: ${error.message}")
                }
            }

        }

        // tv_embedded never returns LOGIN_REQUIRED so exclude it from this count.
        val loginSensitiveClientCount = CLIENTS.count { it.needsVisitorData }
        if (loginRequiredCount >= loginSensitiveClientCount && loginSensitiveClientCount > 0) {
            Log.w(TAG, "All $loginSensitiveClientCount visitor-dependent clients returned LOGIN_REQUIRED, invalidating config")
            invalidateConfig()
        }

        if (manifestUrls.isEmpty() && progressive.isEmpty() && adaptiveVideo.isEmpty() && adaptiveAudio.isEmpty()) {
            return null
        }

        var bestManifest: ManifestCandidate? = null
        for ((clientKey, _, manifestUrl) in manifestUrls) {
            try {
                val variant = parseHlsManifest(manifestUrl) ?: continue
                val candidate = ManifestCandidate(
                    client = clientKey,
                    manifestUrl = manifestUrl,
                    width = variant.width,
                    height = variant.height,
                    bandwidth = variant.bandwidth
                )
                if (
                    bestManifest == null ||
                    candidate.height > bestManifest.height ||
                    (candidate.height == bestManifest.height && candidate.bandwidth > bestManifest.bandwidth) ||
                    (candidate.height == bestManifest.height &&
                        candidate.bandwidth == bestManifest.bandwidth &&
                        candidate.width > bestManifest.width)
                ) {
                    bestManifest = candidate
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.w(TAG, "Manifest parse failed: ${error.message}")
                }
            }
        }

        return CollectedCandidates(
            progressive = progressive,
            adaptiveVideo = adaptiveVideo,
            adaptiveAudio = adaptiveAudio,
            bestManifest = bestManifest
        )
    }

    private suspend fun collectCandidatesForDownload(
        videoId: String,
        forceRefreshConfig: Boolean
    ): YouTubeDownloadCandidates? {
        val collected = collectCandidates(videoId, forceRefreshConfig) ?: return null
        val bestProgressive = sortCandidates(collected.progressive).firstOrNull()
        val bestVideo = pickBestForClient(collected.adaptiveVideo, PREFERRED_SEPARATE_CLIENT)
        val bestAudio = pickBestForClient(collected.adaptiveAudio, PREFERRED_SEPARATE_CLIENT)
        val bestManifest = collected.bestManifest

        if (bestManifest == null && bestProgressive == null && bestVideo == null) return null
        return YouTubeDownloadCandidates(
            videoId = videoId,
            hlsManifestUrl = bestManifest?.manifestUrl,
            adaptiveVideoUrl = bestVideo?.url,
            adaptiveAudioUrl = bestAudio?.url,
            progressiveUrl = bestProgressive?.url
        )
    }

    fun extractVideoId(input: String): String? {
        val trimmed = input.trim()
        if (VIDEO_ID_REGEX.matches(trimmed)) return trimmed

        val normalized = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }

        return runCatching {
            val uri = Uri.parse(normalized)
            val host = uri.host?.lowercase().orEmpty()
            if (host.endsWith("youtu.be")) {
                val id = uri.pathSegments.firstOrNull()
                if (!id.isNullOrBlank() && VIDEO_ID_REGEX.matches(id)) {
                    return id
                }
            }

            val queryId = uri.getQueryParameter("v")
            if (!queryId.isNullOrBlank() && VIDEO_ID_REGEX.matches(queryId)) {
                return queryId
            }

            val segments = uri.pathSegments
            if (segments.size >= 2) {
                val first = segments[0]
                val second = segments[1]
                if ((first == "embed" || first == "shorts" || first == "live") && VIDEO_ID_REGEX.matches(second)) {
                    return second
                }
            }

            null
        }.getOrNull()
    }

    private fun getWatchConfig(html: String): WatchConfig {
        val apiKey = API_KEY_REGEX.find(html)?.groupValues?.getOrNull(1)
        val visitorData = VISITOR_DATA_REGEX.find(html)?.groupValues?.getOrNull(1)
        return WatchConfig(apiKey = apiKey, visitorData = visitorData)
    }

    private suspend fun fetchPlayerResponse(
        apiKey: String,
        videoId: String,
        client: YouTubeClient,
        visitorData: String?,
        cookieHeader: String?
    ): Map<*, *> {
        val endpoint = "https://www.youtube.com/youtubei/v1/player?key=${Uri.encode(apiKey)}"

        val headers = buildMap {
            putAll(DEFAULT_HEADERS)
            put("content-type", "application/json")
            put("origin", "https://www.youtube.com")
            put("x-youtube-client-name", client.id)
            put("x-youtube-client-version", client.version)
            put("user-agent", client.userAgent)
            if (client.needsVisitorData && !visitorData.isNullOrBlank()) put("x-goog-visitor-id", visitorData)
            if (!cookieHeader.isNullOrBlank()) put("cookie", cookieHeader)
            // tv_embedded requires a referer so YouTube serves embed-accessible formats
            if (client.embedUrl != null) put("referer", client.embedUrl)
        }

        val payload = buildMap<String, Any> {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            // For embedded clients (e.g. tv_embedded), inject thirdParty.embedUrl into context
            put("context", buildMap {
                put("client", client.context)
                if (client.embedUrl != null) {
                    put("thirdParty", mapOf("embedUrl" to client.embedUrl))
                }
            })
            put("playbackContext", mapOf(
                "contentPlaybackContext" to mapOf("html5Preference" to "HTML5_PREF_WANTS")
            ))
        }

        val response = performRequest(
            url = endpoint,
            method = "POST",
            headers = headers,
            body = gson.toJson(payload)
        )
        if (!response.ok) {
            throw IllegalStateException("player API ${client.key} failed (${response.status})")
        }

        val parsed = gson.fromJson(response.body, Map::class.java)
        return parsed ?: emptyMap<String, Any>()
    }

    private suspend fun parseHlsManifest(manifestUrl: String): YouTubeHlsManifestInfo? {
        val response = performRequest(
            url = manifestUrl,
            method = "GET",
            headers = YouTubeRequestPolicy.youtubePlaybackHeaders(DEFAULT_HEADERS)
        )
        if (!response.ok) {
            throw IllegalStateException("Failed to fetch HLS manifest (${response.status})")
        }
        return YouTubeHlsManifestParser.parse(manifestUrl, response.body)
    }

    private fun parseQualityLabel(label: String?): Int? {
        if (label.isNullOrBlank()) return null
        val match = QUALITY_LABEL_REGEX.find(label) ?: return null
        return match.groupValues.getOrNull(1)?.toIntOrNull()
    }

    private fun hasNParam(url: String): Boolean {
        return runCatching {
            !Uri.parse(url).getQueryParameter("n").isNullOrBlank()
        }.getOrDefault(false)
    }

    private fun videoScore(height: Int, fps: Int, bitrate: Double): Double {
        return height * 1_000_000_000.0 + fps * 1_000_000.0 + bitrate
    }

    private fun audioScore(bitrate: Double, audioSampleRate: Double): Double {
        return bitrate * 1_000_000.0 + audioSampleRate
    }

    private fun sortCandidates(items: List<StreamCandidate>): List<StreamCandidate> {
        return items.sortedWith(
            compareByDescending<StreamCandidate> { it.score }
                .thenBy { if (it.hasN) 1 else 0 }
                .thenBy { containerPreference(it.ext) }
                .thenBy { it.priority }
        )
    }

    private fun containerPreference(ext: String): Int {
        return when (ext.lowercase()) {
            "mp4", "m4a" -> 0
            "webm" -> 1
            else -> 2
        }
    }

    private fun pickBestForClient(items: List<StreamCandidate>, clientKey: String): StreamCandidate? {
        val sameClient = items.filter { it.client == clientKey }
        if (sameClient.isNotEmpty()) {
            return sortCandidates(sameClient).firstOrNull()
        }
        return sortCandidates(items).firstOrNull()
    }

    /**
     * Probes CDN nodes for the given googlevideo URL and returns the first reachable one.
     * Returns null if no CDN node responds successfully (all return 403/timeout).
     */
    private suspend fun resolveReachableUrl(url: String): String? {
        if (!YouTubeRequestPolicy.isGoogleVideoUrl(url)) return url
        val uri = Uri.parse(url)
        val mnParam = uri.getQueryParameter("mn") ?: return url
        val servers = mnParam.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (servers.size < 2) return url

        val candidates = mutableListOf(url)
        for (server in servers) {
            val mviIndex = servers.indexOf(server)
            val altHost = uri.host?.replaceFirst(
                Regex("^rr\\d+---"),
                "rr${mviIndex + 1}---"
            )?.replaceFirst(
                Regex("sn-[a-z0-9]+-[a-z0-9]+"),
                server
            ) ?: continue
            if (altHost == uri.host) continue
            candidates += url.replace(uri.host!!, altHost)
        }

        if (candidates.size == 1) {
            // Single candidate — verify it's reachable
            return if (isUrlReachable(candidates[0])) candidates[0] else null
        }

        val result = CompletableDeferred<String>()
        val probeScope = CoroutineScope(Dispatchers.IO)
        candidates.forEach { candidate ->
            probeScope.launch {
                val reachable = isUrlReachable(candidate)
                if (reachable) result.complete(candidate)
            }
        }
        return try {
            withTimeoutOrNull(2_000L) { result.await() }
        } finally {
            probeScope.cancel()
        }
    }

    private val probeClient by lazy {
        OkHttpClient.Builder()
            .dns(IPv4FirstDns())
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private suspend fun isUrlReachable(url: String): Boolean {
        return try {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Range", "bytes=0-0")
                .headers(buildHeaders(YouTubeRequestPolicy.youtubePlaybackHeaders(DEFAULT_HEADERS)))
                .build()
            val code = executeCallCancellable(probeClient.newCall(request)) { response -> response.code }
            code == 200 || code == 206
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    private fun summarizeUrl(url: String): String {
        return runCatching {
            val parsed = URL(url)
            val host = parsed.host ?: "unknown-host"
            val path = parsed.path ?: "/"
            "$host$path"
        }.getOrDefault(url.take(80))
    }

    private suspend fun performRequest(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String? = null
    ): RequestResponse {
        val requestBuilder = Request.Builder()
            .url(url)
            .headers(buildHeaders(headers))

        when (method.uppercase()) {
            "POST" -> requestBuilder.post((body ?: "").toRequestBody())
            "PUT" -> requestBuilder.put((body ?: "").toRequestBody())
            "DELETE" -> requestBuilder.delete()
            else -> requestBuilder.get()
        }

        return executeCallCancellable(httpClient.newCall(requestBuilder.build())) { response ->
            RequestResponse(
                ok = response.isSuccessful,
                status = response.code,
                statusText = response.message,
                url = response.request.url.toString(),
                body = response.body?.string().orEmpty()
            )
        }
    }

    /**
     * Executes an OkHttp request as a cancellable suspend call. Removing a player from the
     * Compose tree now cancels its in-flight YouTube extraction instead of leaving blocking
     * requests alive until their 20-second socket timeout.
     */
    private suspend fun <T> executeCallCancellable(
        call: Call,
        readResponse: (Response) -> T
    ): T = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        if (!continuation.isActive) return@suspendCancellableCoroutine

        try {
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        runCatching { continuation.resumeWith(Result.failure(e)) }
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching { response.use(readResponse) }
                    if (continuation.isActive) {
                        runCatching { continuation.resumeWith(result) }
                    }
                }
            })
        } catch (e: Exception) {
            if (continuation.isActive) {
                runCatching { continuation.resumeWith(Result.failure(e)) }
            }
        }
    }

    private fun buildHeaders(source: Map<String, String>): Headers {
        val headers = Headers.Builder()
        source.forEach { (name, value) ->
            if (!name.equals("Accept-Encoding", ignoreCase = true)) {
                headers.add(name, value)
            }
        }
        if (source.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            headers.add("User-Agent", YouTubeRequestPolicy.STREAM_USER_AGENT)
        }
        return headers.build()
    }
}

private data class RequestResponse(
    val ok: Boolean,
    val status: Int,
    val statusText: String,
    val url: String,
    val body: String
)

private fun Map<*, *>.mapValue(key: String): Map<*, *>? {
    return this[key] as? Map<*, *>
}

private fun Map<*, *>.listMapValue(key: String): List<Map<*, *>> {
    val raw = this[key] as? List<*> ?: return emptyList()
    return raw.mapNotNull { it as? Map<*, *> }
}

private fun Map<*, *>.stringValue(key: String): String? {
    val value = this[key] ?: return null
    return value.toString()
}

private fun Map<*, *>.numberValue(key: String): Double? {
    val value = this[key] ?: return null
    return when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }
}
