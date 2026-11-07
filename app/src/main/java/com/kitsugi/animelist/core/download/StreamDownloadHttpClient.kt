package com.kitsugi.animelist.core.download

import com.kitsugi.animelist.core.network.KitsugiHttpClient
import com.kitsugi.animelist.core.player.PlayerMediaSourceFactory
import com.kitsugi.animelist.data.cloudstream.CsVideoInterceptorFactory
import com.kitsugi.animelist.data.model.ExternalAudioTrack
import com.kitsugi.animelist.data.trailer.YouTubeRequestPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * İndirme motoru için ortak HTTP istemcisi ve akış aktarım katmanı.
 *
 * - CloudStream eklentilerinin `getVideoInterceptor`, paylaşılan WebView/Cloudflare `cookieJar`,
 *   SSL bypass ve IPv4-öncelikli DNS altyapısını (`CsVideoInterceptorFactory`) devralır.
 * - CDN'lere özgü `Origin` / `Referer` / `User-Agent` kurallarını uygular.
 * - `googlevideo.com` akışlarını daraltmaya (throttling) takılmadan 10 MB `range=` parçalarıyla indirir.
 */
class StreamDownloadHttpClient(
    private val addonName: String? = null,
    private val streamTitle: String? = null,
    private val isCloudStream: Boolean = false,
    private val qualityValue: Int? = null,
    private val baseHeaders: Map<String, String> = emptyMap(),
    customClient: OkHttpClient? = null
) {
    data class StreamPeek(
        val finalUrl: String,
        val statusCode: Int,
        val contentType: String?,
        val contentLength: Long,
        val headBytes: ByteArray,
        val detectedFormat: StreamFormatSniffer.DetectedFormat
    )

    data class RangeWindow(
        val startOffset: Long,
        val bytes: ByteArray,
        val totalResourceLength: Long
    )

    private val client: OkHttpClient = customClient ?: buildDefaultClient()

    private fun buildDefaultClient(): OkHttpClient {
        val cleanAddon = addonName?.removePrefix("⚡ ")?.trim()
        val provider = if (!cleanAddon.isNullOrBlank()) {
            runCatching {
                com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(cleanAddon)
            }.getOrNull()
        } else null

        val cleanedHeaders = stripInternalMetaHeaders(baseHeaders)
        val interceptor = runCatching {
            if (provider != null) {
                @Suppress("DEPRECATION")
                val dummyLink = com.lagradost.cloudstream3.utils.ExtractorLink(
                    source = cleanAddon ?: "",
                    name = streamTitle ?: cleanAddon ?: "",
                    url = provider.mainUrl,
                    referer = cleanedHeaders.entries.firstOrNull { it.key.equals("referer", true) }?.value ?: "",
                    quality = qualityValue ?: 1080,
                    headers = cleanedHeaders,
                    extractorData = null,
                    type = com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8,
                    audioTracks = emptyList()
                )
                provider.getVideoInterceptor(dummyLink)
            } else null
        }.getOrNull()

        return runCatching {
            CsVideoInterceptorFactory.buildClient(
                interceptor = interceptor,
                headers = emptyMap(), // Her istekte URL'ye göre normalize edilmiş başlıklar eklenir
                ignoreSSL = true
            ).newBuilder()
                .connectTimeout(25, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }.getOrElse {
            KitsugiHttpClient.client.newBuilder()
                .connectTimeout(25, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
        }
    }

    /**
     * Akışın ilk baytlarını ve yanıt üstbilgilerini okur; böylece uzantısı `.txt`, `.png`
     * veya uzantısız olan HLS/DASH manifestleri ile gerçek medya dosyaları kesin olarak ayrılır.
     */
    suspend fun peekStream(
        url: String,
        extraHeaders: Map<String, String> = emptyMap(),
        maxPeekBytes: Int = 8192
    ): StreamPeek {
        currentCoroutineContext().ensureActive()
        val headers = normalizeHeadersForUrl(url, mergeHeaders(baseHeaders, extraHeaders))
        val request = buildGetRequest(url, headers, byteRange = null)
        client.newCall(request).execute().use { resp ->
            val finalUrl = resp.request.url.toString()
            val ct = resp.header("Content-Type")
            val cl = resp.header("Content-Length")?.toLongOrNull() ?: -1L
            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code} (${url.take(100)})")
            }
            val body = resp.body ?: throw IOException("Boş HTTP yanıt gövdesi")
            val source = body.source()
            source.request(maxPeekBytes.toLong())
            val available = minOf(source.buffer.size.toInt(), maxPeekBytes)
            val head = if (available > 0) {
                source.buffer.snapshot(available).toByteArray()
            } else {
                ByteArray(0)
            }
            val detected = StreamFormatSniffer.sniff(
                bytes = head,
                length = head.size,
                contentType = ct,
                url = finalUrl
            )
            return StreamPeek(
                finalUrl = finalUrl,
                statusCode = resp.code,
                contentType = ct,
                contentLength = cl,
                headBytes = head,
                detectedFormat = detected
            )
        }
    }

    suspend fun fetchText(
        url: String,
        extraHeaders: Map<String, String> = emptyMap(),
        maxRetries: Int = 3
    ): Pair<String, String> {
        return executeWithRetry(maxRetries) {
            currentCoroutineContext().ensureActive()
            val headers = normalizeHeadersForUrl(url, mergeHeaders(baseHeaders, extraHeaders))
            val req = buildGetRequest(url, headers, byteRange = null)
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw HttpStatusException(resp.code, "Manifest alınamadı: HTTP ${resp.code}")
                }
                val finalUrl = resp.request.url.toString()
                val text = resp.body?.string() ?: throw IOException("Boş manifest gövdesi")
                finalUrl to text
            }
        }
    }

    suspend fun fetchBytes(
        url: String,
        extraHeaders: Map<String, String> = emptyMap(),
        byteRange: HlsManifestResolver.ByteRange? = null,
        maxRetries: Int = 3
    ): ByteArray {
        return executeWithRetry(maxRetries) {
            currentCoroutineContext().ensureActive()
            val headers = normalizeHeadersForUrl(url, mergeHeaders(baseHeaders, extraHeaders))
            val req = buildGetRequest(url, headers, byteRange = byteRange)
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw HttpStatusException(resp.code, "Segment alınamadı: HTTP ${resp.code}")
                }
                resp.body?.bytes() ?: throw IOException("Boş segment gövdesi")
            }
        }
    }

    /**
     * Uzak konteyner (MKV / MP4 / WebM / TS / AVI / FLV) içindeki çoklu ses izlerini tüm dosyayı
     * indirmeden tespit edebilmek için sınırlı bir HTTP Range penceresi okur.
     */
    fun fetchRangeWindowBlocking(
        url: String,
        extraHeaders: Map<String, String> = emptyMap(),
        startOffset: Long = 0L,
        maxBytes: Int = 1_572_864
    ): RangeWindow? {
        return runCatching {
            val clampedStart = startOffset.coerceAtLeast(0L)
            val windowLen = maxBytes.coerceIn(16_384, 4 * 1024 * 1024)
            val headers = normalizeHeadersForUrl(url, mergeHeaders(baseHeaders, extraHeaders))
            val reqBuilder = Request.Builder()
                .url(url)
                .get()
                .header("Range", "bytes=$clampedStart-${clampedStart + windowLen - 1}")
            headers.forEach { (k, v) -> reqBuilder.header(k, v) }
            client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                // Sunucu Range başlığını yok sayıp 200 döndürdüyse yalnızca 0. bayttan okumaya izin ver
                if (clampedStart > 0L && resp.code != 206) return null

                val contentRange = resp.header("Content-Range").orEmpty()
                val totalFromRange = contentRange.substringAfter('/', "")
                    .trim()
                    .takeIf { it.isNotEmpty() && it != "*" }
                    ?.toLongOrNull()
                val contentLen = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                val totalLen = when {
                    totalFromRange != null && totalFromRange > 0L -> totalFromRange
                    resp.code == 200 && contentLen > 0L -> contentLen
                    else -> -1L
                }

                val body = resp.body ?: return null
                val source = body.source()
                source.request(windowLen.toLong())
                val available = minOf(source.buffer.size.toInt(), windowLen)
                if (available <= 0) return null
                val out = source.buffer.snapshot(available).toByteArray()
                RangeWindow(
                    startOffset = clampedStart,
                    bytes = out,
                    totalResourceLength = totalLen
                )
            }
        }.getOrNull()
    }

    /**
     * Doğrudan (progressive) medya veya ses dosyasını diske indirir.
     * - `googlevideo.com` adreslerinde daraltmayı önlemek için 10 MB `range=` sorgu dilimleri kullanır.
     * - Ağ kopmalarında kaldığı bayttan (`Range: bytes=N-`) devam etmeyi dener.
     */
    suspend fun downloadProgressiveToFile(
        url: String,
        destFile: File,
        extraHeaders: Map<String, String> = emptyMap(),
        onProgress: suspend (Float) -> Unit
    ) {
        destFile.parentFile?.mkdirs()
        val host = runCatching { URI(url).host.orEmpty() }.getOrDefault("")
        if (YouTubeRequestPolicy.isGoogleVideoHost(host) && !PlayerMediaSourceFactory.isHlsUrl(url)) {
            downloadGoogleVideoChunked(url, destFile, extraHeaders, onProgress)
            return
        }

        var downloaded = if (destFile.exists()) destFile.length() else 0L
        var totalBytes = -1L
        var attempt = 0

        while (true) {
            currentCoroutineContext().ensureActive()
            try {
                val headers = normalizeHeadersForUrl(url, mergeHeaders(baseHeaders, extraHeaders))
                val reqBuilder = Request.Builder().url(url).get()
                headers.forEach { (k, v) -> reqBuilder.header(k, v) }
                if (downloaded > 0L) {
                    reqBuilder.header("Range", "bytes=$downloaded-")
                }
                client.newCall(reqBuilder.build()).execute().use { resp ->
                    if (resp.code == 416 && downloaded > 0L) {
                        // Dosya zaten tamamlanmış
                        onProgress(1f)
                        return
                    }
                    if (!resp.isSuccessful) {
                        throw HttpStatusException(resp.code, "Dosya indirilemedi: HTTP ${resp.code}")
                    }
                    val appendMode = resp.code == 206 && downloaded > 0L
                    if (!appendMode) {
                        downloaded = 0L
                    }
                    val contentLen = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                    if (contentLen > 0L) {
                        totalBytes = if (appendMode) downloaded + contentLen else contentLen
                    }
                    val body = resp.body ?: throw IOException("Boş yanıt gövdesi")
                    body.byteStream().use { input ->
                        FileOutputStream(destFile, appendMode).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var firstChunkChecked = appendMode
                            var lastReportedPct = -1
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                currentCoroutineContext().ensureActive()
                                if (!firstChunkChecked && read > 0) {
                                    firstChunkChecked = true
                                    val fmt = StreamFormatSniffer.sniff(
                                        bytes = buffer,
                                        length = read,
                                        contentType = resp.header("Content-Type"),
                                        url = url
                                    )
                                    if (fmt == StreamFormatSniffer.DetectedFormat.HTML_PAGE) {
                                        throw IOException("Sunucu medya yerine HTML hata/koruma sayfası döndürdü")
                                    }
                                }
                                output.write(buffer, 0, read)
                                downloaded += read
                                if (totalBytes > 0L) {
                                    val pct = ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                    if (pct != lastReportedPct) {
                                        lastReportedPct = pct
                                        onProgress(pct / 100f)
                                    }
                                }
                            }
                            output.flush()
                        }
                    }
                }
                if (destFile.length() <= 0L) {
                    throw IOException("İndirilen dosya boş (0 bayt)")
                }
                onProgress(1f)
                return
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                attempt++
                if (attempt >= 4 || (e is HttpStatusException && e.code in listOf(401, 403, 404, 410))) {
                    throw e
                }
                delay(600L * attempt)
                downloaded = if (destFile.exists()) destFile.length() else 0L
            }
        }
    }

    private suspend fun downloadGoogleVideoChunked(
        url: String,
        destFile: File,
        extraHeaders: Map<String, String>,
        onProgress: suspend (Float) -> Unit
    ) {
        val chunkSize = 10L * 1024L * 1024L // 10 MB
        val ytHeaders = YouTubeRequestPolicy.youtubePlaybackHeaders(
            stripInternalMetaHeaders(mergeHeaders(baseHeaders, extraHeaders))
        )
        var offset = 0L
        var totalLength = extractQueryClen(url)
        FileOutputStream(destFile, false).use { output ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val end = if (totalLength > 0L) {
                    minOf(offset + chunkSize - 1L, totalLength - 1L)
                } else {
                    offset + chunkSize - 1L
                }
                val chunkUrl = replaceOrAddRangeParam(url, "$offset-$end")
                val bytes = executeWithRetry(3) {
                    val reqBuilder = Request.Builder().url(chunkUrl).get()
                    ytHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }
                    client.newCall(reqBuilder.build()).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            throw HttpStatusException(resp.code, "YouTube parça hatası: HTTP ${resp.code}")
                        }
                        resp.body?.bytes() ?: ByteArray(0)
                    }
                }
                if (bytes.isEmpty()) break
                output.write(bytes)
                offset += bytes.size
                if (totalLength > 0L) {
                    onProgress((offset.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f))
                    if (offset >= totalLength) break
                }
                if (bytes.size < (end - (offset - bytes.size) + 1L)) {
                    break
                }
            }
            output.flush()
        }
        if (destFile.length() <= 0L) {
            throw IOException("YouTube akışı boş döndü")
        }
        onProgress(1f)
    }

    private fun buildGetRequest(
        url: String,
        headers: Map<String, String>,
        byteRange: HlsManifestResolver.ByteRange?
    ): Request {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (k, v) ->
            if (k.isNotBlank() && v.isNotBlank()) {
                runCatching { builder.header(k, v) }
            }
        }
        if (byteRange != null) {
            builder.header("Range", byteRange.toHttpRangeHeader())
        }
        return builder.build()
    }

    private suspend fun <T> executeWithRetry(maxRetries: Int, block: suspend () -> T): T {
        var lastError: Exception? = null
        for (attempt in 1..maxRetries.coerceAtLeast(1)) {
            currentCoroutineContext().ensureActive()
            try {
                return block()
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                lastError = e
                // 404/410 gibi kalıcı hatalarda gereksiz bekleme yapma
                if (e is HttpStatusException && e.code in listOf(404, 410)) {
                    throw e
                }
                if (attempt < maxRetries) {
                    delay(400L * attempt)
                }
            }
        }
        throw lastError ?: IOException("Ağ isteği başarısız oldu")
    }

    class HttpStatusException(val code: Int, message: String) : IOException(message)

    companion object {
        const val META_MASTER_URL_HEADER = "X-Kitsugi-Master-Url"

        /** Yabancı `Origin` başlığı gönderildiğinde 403 döndüren CDN aileleri. */
        private val NO_AUTO_ORIGIN_HOSTS = listOf(
            "ok.ru",
            "odnoklassniki.ru",
            "mycdn.me",
            "okcdn.ru",
            "sibnet.ru",
            "vkuser.net",
            "vkuseraudio.net",
            "vkuservideo.net",
            "vkvideo.ru",
            "googlevideo.com",
            "youtube.com"
        )

        fun stripInternalMetaHeaders(headers: Map<String, String>): Map<String, String> =
            headers.filterKeys { key ->
                !key.startsWith("X-Kitsugi-", ignoreCase = true) &&
                    !key.equals(ExternalAudioTrack.META_HEADER_LABEL, ignoreCase = true) &&
                    !key.equals(ExternalAudioTrack.META_HEADER_LANG, ignoreCase = true) &&
                    !key.equals("X-Audio-Label", ignoreCase = true) &&
                    !key.equals("X-Audio-Lang", ignoreCase = true)
            }

        fun mergeHeaders(
            base: Map<String, String>,
            overrideHeaders: Map<String, String>
        ): Map<String, String> {
            if (overrideHeaders.isEmpty()) return base
            val result = LinkedHashMap<String, String>()
            base.forEach { (k, v) -> if (k.isNotBlank() && v.isNotBlank()) result[k] = v }
            overrideHeaders.forEach { (k, v) ->
                if (k.isNotBlank() && v.isNotBlank()) {
                    val existingKey = result.keys.firstOrNull { it.equals(k, ignoreCase = true) }
                    if (existingKey != null) result.remove(existingKey)
                    result[k] = v
                }
            }
            return result
        }

        fun normalizeHeadersForUrl(
            url: String,
            rawHeaders: Map<String, String>
        ): Map<String, String> {
            val cleaned = LinkedHashMap<String, String>()
            stripInternalMetaHeaders(rawHeaders).forEach { (k, v) ->
                if (k.isNotBlank() && v.isNotBlank()) cleaned[k] = v
            }

            val host = runCatching { URI(url).host?.lowercase(Locale.ROOT).orEmpty() }.getOrDefault("")
            if (YouTubeRequestPolicy.isGoogleVideoHost(host)) {
                return YouTubeRequestPolicy.youtubePlaybackHeaders(cleaned)
            }

            val hasUa = cleaned.keys.any { it.equals("User-Agent", ignoreCase = true) }
            if (!hasUa) {
                cleaned["User-Agent"] = runCatching {
                    com.lagradost.cloudstream3.network.CloudflareKiller.UNIFIED_USER_AGENT
                }.getOrDefault(
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                )
            }

            val refererEntry = cleaned.entries.firstOrNull { it.key.equals("Referer", ignoreCase = true) }
            if (refererEntry == null && host.isNotBlank()) {
                cleaned["Referer"] = "https://$host/"
            }

            val hasOrigin = cleaned.keys.any { it.equals("Origin", ignoreCase = true) }
            val forbidAutoOrigin = NO_AUTO_ORIGIN_HOSTS.any { host == it || host.endsWith(".$it") }
            if (!hasOrigin && !forbidAutoOrigin) {
                val effectiveReferer = cleaned.entries
                    .firstOrNull { it.key.equals("Referer", ignoreCase = true) }?.value
                val derivedOrigin = PlayerMediaSourceFactory.originOfUrl(effectiveReferer)
                    ?: PlayerMediaSourceFactory.originOfUrl(url)
                if (!derivedOrigin.isNullOrBlank()) {
                    cleaned["Origin"] = derivedOrigin
                }
            }

            if (!cleaned.keys.any { it.equals("Accept", ignoreCase = true) }) {
                cleaned["Accept"] = "*/*"
            }
            return cleaned
        }

        private fun extractQueryClen(url: String): Long {
            val m = Regex("""[?&]clen=(\d+)""").find(url) ?: return -1L
            return m.groupValues[1].toLongOrNull() ?: -1L
        }

        private fun replaceOrAddRangeParam(url: String, rangeVal: String): String {
            val cleaned = url.replace(Regex("""([?&])range=[^&]*&?""")) { match ->
                val prefix = match.groupValues[1]
                if (match.value.endsWith("&")) prefix else ""
            }.trimEnd('?', '&')
            val sep = if ('?' in cleaned) '&' else '?'
            return "${cleaned}${sep}range=$rangeVal"
        }
    }
}
