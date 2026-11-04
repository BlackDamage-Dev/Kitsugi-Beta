package com.kitsugi.animelist.core.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.ts.TsExtractor
import com.kitsugi.animelist.core.player.dvmkv.DolbyVisionCompatibility
import com.kitsugi.animelist.data.cloudstream.CsVideoInterceptorFactory
import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.data.settings.Dv7HandlingMode
import com.kitsugi.animelist.data.trailer.YouTubeRequestPolicy
import com.kitsugi.animelist.data.trailer.YoutubeChunkedDataSourceFactory
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

/**
 * T1.13 – PlayerMediaSourceFactory
 *
 * Centralises all DataSource / MediaSource construction logic that was previously
 * scattered across [Media3PlayerEngine.prepare].  Every call site describes
 * *what* it wants (headers, subs, caching, parallel-range) and this factory
 * decides *how* to build it – keeping the engine lean and testable.
 */
@OptIn(UnstableApi::class)
class PlayerMediaSourceFactory(
    private val context: Context,
    private val settings: AppSettings
) {

    private val TAG = "PlayerMediaSourceFactory"
    private var customExtractorsFactory: ExtractorsFactory? = null
    private var customSubtitleParserFactory: SubtitleParser.Factory? = null

    fun configureSubtitleParsing(
        extractorsFactory: ExtractorsFactory?,
        subtitleParserFactory: SubtitleParser.Factory?
    ) {
        customExtractorsFactory = extractorsFactory
        customSubtitleParserFactory = subtitleParserFactory
    }

    // ── Extractors ────────────────────────────────────────────────────────────

    /**
     * Builds the [ExtractorsFactory] appropriate for the current DV / HDR mode.
     */
    fun buildExtractorsFactory(
        isConvertToDv81: Boolean,
        stripDvRpu: Boolean,
    ): ExtractorsFactory {
        val base = DefaultExtractorsFactory()
            .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS)
            .setTsExtractorTimestampSearchBytes(1500 * TsExtractor.TS_PACKET_SIZE)

        return if (isConvertToDv81 || stripDvRpu || settings.stripHdr10PlusSei) {
            DolbyVisionExtractorsFactory(
                delegate = base,
                config = DolbyVisionConversionConfig(
                    active = isConvertToDv81,
                    forcedMode = -1,
                    preserveMapping = false,
                    dv5Enabled = false,
                    manualDv81 = isConvertToDv81
                ),
                stripDvRpu = stripDvRpu,
                stripHdr10PlusSei = settings.stripHdr10PlusSei
            )
        } else {
            base
        }
    }

    // ── MediaItem ─────────────────────────────────────────────────────────────

    fun isHls(videoUrl: String, streamTypeHint: String? = null): Boolean =
        isHlsUrl(videoUrl, streamTypeHint)

    fun isDash(videoUrl: String, streamTypeHint: String? = null): Boolean =
        isDashUrl(videoUrl, streamTypeHint)

    /**
     * Constructs a [MediaItem] for [videoUrl] with optional subtitle configurations.
     */
    fun buildMediaItem(
        videoUrl: String,
        subtitles: List<SubtitleInput> = emptyList(),
        streamTypeHint: String? = null,
        mediaType: StreamMediaType? = null,
        csStreamType: String? = null
    ): MediaItem {
        val videoUri = Uri.parse(videoUrl)
        val format = StreamFormatPolicy.resolve(videoUrl, mediaType)
        val adaptiveType = com.kitsugi.animelist.data.cloudstream.CsStreamFormat.choose(
            csStreamType, isHlsUrl(videoUrl, streamTypeHint), isDashUrl(videoUrl, streamTypeHint)
        )
        val isM3u8  = adaptiveType == "M3U8" || format == StreamMediaType.HLS || isHlsUrl(videoUrl, streamTypeHint)
        val isDash  = adaptiveType == "DASH" || format == StreamMediaType.DASH || isDashUrl(videoUrl, streamTypeHint)
        val isSs    = videoUrl.contains(".ism",   ignoreCase = true)

        val builder = MediaItem.Builder().setUri(videoUri)
        when {
            isM3u8 -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            isDash  -> builder.setMimeType(MimeTypes.APPLICATION_MPD)
            isSs    -> builder.setMimeType(MimeTypes.APPLICATION_SS)
        }

        if (subtitles.isNotEmpty()) {
            val subConfigs = subtitles.map { sub ->
                val mime = guessSubtitleMimeType(sub.url)
                val resolvedUrl = if (sub.url.startsWith("/")) "file://${sub.url}" else sub.url
                // Kimlik (id) ZORUNLU: Media3PlayerEngine, harici altyazıyı dahili
                // altyazıdan ayırırken `format.id == sub.url` karşılaştırması yapıyor.
                // Bu alan olmadan "harici Türkçe altyazı önceliği" ve kullanıcı seçimi
                // yanlış eşleşiyordu.
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(resolvedUrl))
                    .setId(sub.url)
                    .setMimeType(mime)
                    .setLanguage(sub.lang)
                    .setLabel(sub.name)
                    .build()
            }
            builder.setSubtitleConfigurations(subConfigs)
        }
        return builder.build()
    }

    // ── DataSource factory ────────────────────────────────────────────────────

    /**
     * Builds the OkHttp-backed [DataSource.Factory], optionally wrapping it
     * in [ParallelRangeDataSource.Factory] when parallel range is enabled.
     *
     * @param addonName  CloudStream addon name (used to look up the API provider)
     * @param headers    Request headers forwarded to OkHttp interceptors
     * @param videoUrl   Raw video URL (used to build the [ExtractorLink])
     * @param streamTitle Human-readable stream title
     * @param isCS       Whether this is a CloudStream source
     * @param qualityValue Video quality hint (e.g. 1080)
     */
    fun buildDataSourceFactory(
        addonName: String?,
        headers: Map<String, String>,
        videoUrl: String,
        streamTitle: String?,
        isCS: Boolean,
        qualityValue: Int?,
        mediaType: StreamMediaType? = null,
        csStreamType: String? = null,
        csExtractorData: String? = null
    ): androidx.media3.datasource.DataSource.Factory {
        val provider = if (isCS && !addonName.isNullOrBlank()) {
            runCatching {
                com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(addonName)
            }.getOrNull()
        } else null

        @Suppress("DEPRECATION")
        val csLink = if (provider != null) {
            runCatching {
                val isM3u8Link = isHlsUrl(videoUrl)
                com.lagradost.cloudstream3.utils.ExtractorLink(
                    source    = addonName!!,
                    name      = streamTitle ?: addonName,
                    url       = videoUrl,
                    referer   = headers.entries.firstOrNull { it.key.equals("referer", ignoreCase = true) }?.value ?: "",
                    quality   = qualityValue ?: com.lagradost.cloudstream3.utils.Qualities.Unknown.value,
                    headers   = headers,
                    extractorData = csExtractorData,
                    type      = when {
                        csStreamType == "DASH" -> com.lagradost.cloudstream3.utils.ExtractorLinkType.DASH
                        csStreamType == "M3U8" || isM3u8Link -> com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8
                        else -> com.lagradost.cloudstream3.utils.ExtractorLinkType.VIDEO
                    },
                    audioTracks = emptyList()
                )
            }.getOrNull()
        } else null

        val videoInterceptor = runCatching {
            csLink?.let { provider?.getVideoInterceptor(it) }
        }.getOrNull() ?: CsVideoInterceptorFactory.videoInterceptor(
            provider = provider,
            name = streamTitle ?: addonName.orEmpty(),
            url = videoUrl,
            headers = headers,
            quality = qualityValue,
            mediaType = mediaType
        )

        val effectiveHeaders = headers.toMutableMap()
        // Range başlığı oynatıcıya gitmez (Media3 kendi Range ve chunk isteklerini yönetir)
        effectiveHeaders.keys.filter { it.equals("range", ignoreCase = true) }.toList().forEach { effectiveHeaders.remove(it) }
        val videoHost = runCatching { Uri.parse(videoUrl).host }.getOrNull()
        if (!effectiveHeaders.keys.any { it.equals("referer", ignoreCase = true) }) {
            // Önce provider ana URL'i (filmmakinesi.to, hdfilmcehennemi.nl) dene; yoksa video host'una düş.
            val fallbackRef = provider?.mainUrl?.let { it.trimEnd('/') + "/" }
                ?: if (!videoHost.isNullOrBlank()) "https://$videoHost/" else "https://google.com"
            effectiveHeaders["Referer"] = fallbackRef
        }
        if (!effectiveHeaders.keys.any { it.equals("origin", ignoreCase = true) }) {
            // Origin, Referer'ın origin'inden türetilir (CDN'ler embed origin'i doğrular).
            // Referer yoksa video host fallback.
            val refererVal = effectiveHeaders.entries.firstOrNull { it.key.equals("referer", ignoreCase = true) }?.value
            val originFromReferer = refererVal?.let { originOfUrl(it) }
            val originFromVideoHost = if (!videoHost.isNullOrBlank()) "https://$videoHost" else null
            val origin = originFromReferer ?: originFromVideoHost ?: provider?.mainUrl?.let { originOfUrl(it) }
            if (!origin.isNullOrBlank()) {
                effectiveHeaders["Origin"] = origin
            }
        }
        if (!effectiveHeaders.keys.any { it.equals("user-agent", ignoreCase = true) }) {
            effectiveHeaders["User-Agent"] = com.lagradost.cloudstream3.network.CloudflareKiller.UNIFIED_USER_AGENT
        }

        val okHttpClient = CsVideoInterceptorFactory.buildClient(
            interceptor = videoInterceptor,
            headers     = effectiveHeaders,
            ignoreSSL   = true
        )
        val base = DefaultDataSource.Factory(context, OkHttpDataSource.Factory(okHttpClient))

        val isHlsOrDash = csStreamType == "M3U8" || csStreamType == "DASH" ||
            isHlsUrl(videoUrl) || videoUrl.contains(".mpd", ignoreCase = true)
        val isLocalP2p = videoUrl.contains("127.0.0.1") || videoUrl.contains("localhost")

        return if (settings.parallelRangeEnabled && !isHlsOrDash && !isLocalP2p) {
            Log.d(TAG, "ParallelRangeDataSource enabled (${ParallelRangeDataSource.DEFAULT_PARALLEL_CONNECTIONS} connections)")
            ParallelRangeDataSource.Factory(
                upstreamFactory    = base,
                parallelConnections = ParallelRangeDataSource.DEFAULT_PARALLEL_CONNECTIONS,
                chunkSizeBytes     = ParallelRangeDataSource.DEFAULT_CHUNK_SIZE_BYTES
            )
        } else {
            base
        }
    }

    // ── Complete MediaSource ──────────────────────────────────────────────────

    /**
     * Creates the final [MediaSource] ready to hand to [ExoPlayer.setMediaSource].
     *
     * For YouTube-style streams with a separate [audioUrl], builds a
     * [MergingMediaSource].  Otherwise builds a standard progressive /
     * adaptive source backed by the OkHttp data-source factory.
     */
    fun create(
        videoUrl: String,
        audioUrl: String?,
        headers: Map<String, String>,
        subtitles: List<SubtitleInput>,
        isCS: Boolean,
        addonName: String?,
        streamTitle: String?,
        qualityValue: Int?,
        extractorsFactory: ExtractorsFactory,
        /** Engine tarafında (altyazı kimlikleri / yerel MIME tespiti ile) hazırlanmış MediaItem. */
        mediaItemOverride: MediaItem? = null,
        externalAudioTracks: List<ExternalAudioTrackInput> = emptyList(),
        streamTypeHint: String? = null,
        mediaType: StreamMediaType? = null,
        csStreamType: String? = null,
        csExtractorData: String? = null
    ): MediaSource {
        val mediaItem = mediaItemOverride ?: buildMediaItem(videoUrl, subtitles, streamTypeHint, mediaType, csStreamType)

        val activeExtractorsFactory = if (extractorsFactory is DolbyVisionExtractorsFactory) {
            customSubtitleParserFactory?.let { extractorsFactory.withSubtitleParser(it) } ?: extractorsFactory
        } else {
            customExtractorsFactory ?: extractorsFactory
        }
        val isYouTubeSource = YouTubeRequestPolicy.shouldUseYouTubeDataSource(
            videoUrl = videoUrl,
            audioUrl = audioUrl,
            isCloudstream = isCS
        )
        val perUrlHeaders = collectPerUrlHeaders(subtitles, externalAudioTracks)
        val rawDataSourceFactory = if (isYouTubeSource) {
            val isHls = isHlsUrl(videoUrl, streamTypeHint)
            val chunkRanges = YouTubeRequestPolicy.shouldUseChunkedGoogleVideoRanges(
                videoUrl = videoUrl,
                audioUrl = audioUrl,
                isCloudstream = isCS,
                isHls = isHls
            )
            Log.d(TAG, "Using YouTube DataSource: hls=$isHls, chunkedRanges=$chunkRanges")
            YoutubeChunkedDataSourceFactory(
                chunkGoogleVideoStreams = chunkRanges,
                requestHeaders = YouTubeRequestPolicy.youtubePlaybackHeaders(headers)
            )
        } else {
            buildDataSourceFactory(
                addonName = addonName,
                headers = headers,
                videoUrl = videoUrl,
                streamTitle = streamTitle,
                isCS = isCS,
                qualityValue = qualityValue,
                mediaType = mediaType,
                csStreamType = csStreamType,
                csExtractorData = csExtractorData
            )
        }
        val dsFactory = withPerUrlHeaders(rawDataSourceFactory, perUrlHeaders)

        // For HLS, the YouTube DataSource deliberately sends signed playlists/segments
        // unchanged with the required YouTube headers; query-range chunking is reserved for
        // progressive googlevideo video/audio streams.
        val audioUrls = buildList {
            audioUrl?.takeIf { it.isNotBlank() }?.let { add(it) }
            externalAudioTracks.asSequence()
                .map { it.url.trim() }
                .filter { it.isNotBlank() }
                .forEach { add(it) }
        }.distinct()
        if (audioUrls.isNotEmpty()) {
            Log.d(TAG, "Building MergingMediaSource with ${audioUrls.size} external audio rendition(s)")
            val factory = DefaultMediaSourceFactory(dsFactory, activeExtractorsFactory)
            customSubtitleParserFactory?.let {
                factory.setSubtitleParserFactory(it)
            }
            val mediaSources = ArrayList<MediaSource>(audioUrls.size + 1)
            mediaSources += factory.createMediaSource(mediaItem)
            audioUrls.forEach { url ->
                mediaSources += factory.createMediaSource(MediaItem.fromUri(Uri.parse(url)))
            }
            return MergingMediaSource(*mediaSources.toTypedArray())
        }

        // Standard progressive / adaptive
        Log.d(TAG, "Building standard MediaSource: $videoUrl")
        val factory = DefaultMediaSourceFactory(dsFactory, activeExtractorsFactory)
        customSubtitleParserFactory?.let {
            factory.setSubtitleParserFactory(it)
        }
        return factory.createMediaSource(mediaItem)
    }

    private fun collectPerUrlHeaders(
        subtitles: List<SubtitleInput>,
        audioTracks: List<ExternalAudioTrackInput>
    ): Map<String, Map<String, String>> {
        val result = linkedMapOf<String, MutableMap<String, String>>()
        fun add(url: String, headers: Map<String, String>) {
            if (headers.isEmpty()) return
            val key = url.trim().toHttpUrlOrNull()?.toString() ?: return
            val merged = result.getOrPut(key) { linkedMapOf() }
            headers.forEach { (name, value) ->
                if (name.isBlank() || value.isBlank()) return@forEach
                merged.keys.firstOrNull { it.equals(name, ignoreCase = true) }?.let(merged::remove)
                merged[name] = value
            }
        }
        subtitles.forEach { add(it.url, it.headers) }
        audioTracks.forEach { add(it.url, it.headers) }
        return result
    }

    /** Add per-file headers without changing the main video request headers. */
    private fun withPerUrlHeaders(
        upstream: androidx.media3.datasource.DataSource.Factory,
        headersByUrl: Map<String, Map<String, String>>
    ): androidx.media3.datasource.DataSource.Factory {
        if (headersByUrl.isEmpty()) return upstream
        return ResolvingDataSource.Factory(upstream, object : ResolvingDataSource.Resolver {
            override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
                val key = dataSpec.uri.toString().toHttpUrlOrNull()?.toString() ?: return dataSpec
                val specific = headersByUrl[key] ?: return dataSpec
                val merged = dataSpec.httpRequestHeaders.toMutableMap()
                specific.forEach { (name, value) ->
                    merged.keys.firstOrNull { it.equals(name, ignoreCase = true) }?.let(merged::remove)
                    merged[name] = value
                }
                return dataSpec.buildUpon().setHttpRequestHeaders(merged).build()
            }

            override fun resolveReportedUri(uri: Uri): Uri = uri
        })
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun guessSubtitleMimeType(url: String): String = when {
        url.startsWith("/") || url.startsWith("file://") -> guessLocalSubtitleMime(url.removePrefix("file://"))
        url.contains(".vtt", ignoreCase = true)          -> MimeTypes.TEXT_VTT
        url.contains(".srt", ignoreCase = true)          -> MimeTypes.APPLICATION_SUBRIP
        url.contains(".ass", ignoreCase = true)
            || url.contains(".ssa", ignoreCase = true)   -> MimeTypes.TEXT_SSA
        else                                              -> MimeTypes.TEXT_VTT
    }

    private fun guessLocalSubtitleMime(path: String): String {
        return try {
            java.io.File(path).bufferedReader().use { reader ->
                val head = (1..10).mapNotNull { reader.readLine()?.trim() }.joinToString("\n")
                when {
                    head.contains("[Script Info]") || head.contains("[V4+ Styles]") -> MimeTypes.TEXT_SSA
                    head.contains("WEBVTT")                                          -> MimeTypes.TEXT_VTT
                    head.contains("-->")                                             -> MimeTypes.APPLICATION_SUBRIP
                    else                                                             -> MimeTypes.APPLICATION_SUBRIP
                }
            }
        } catch (e: Exception) {
            when {
                path.endsWith(".vtt", ignoreCase = true)                     -> MimeTypes.TEXT_VTT
                path.endsWith(".ass", ignoreCase = true)
                    || path.endsWith(".ssa", ignoreCase = true)              -> MimeTypes.TEXT_SSA
                else                                                          -> MimeTypes.APPLICATION_SUBRIP
            }
        }
    }

    companion object {
        private const val TAG = "PlayerMediaSourceFactory"

        /**
         * Belirtilen URL'nin bir HLS (.m3u8, master playlist veya HLS text manifest)
         * akışı olup olmadığını tespit eder. Dosya yolunda '.mp4' geçse bile (örn: FilmMakinesi / CloseLoad
         * 'hls/...mp4/master.txt' yapısı veya Vidmixi /list/ manifestleri) kesin HLS belirteçleri
         * varsa true döner. Bu sayede ExoPlayer akışı progressive MP4 olarak indirip Range parçalaması yapmaz,
         * HlsMediaSource ile video + ses parçalarını kusursuz oynatır.
         */
        fun isHlsUrl(videoUrl: String, streamTypeHint: String? = null): Boolean {
            when (streamTypeHint?.uppercase(Locale.ROOT)) {
                "M3U8", "HLS" -> return true
                "DASH" -> return false
            }
            if (videoUrl.isBlank()) return false
            val clean = videoUrl.lowercase(Locale.ROOT)
            val path = try {
                java.net.URI(clean).path.orEmpty()
            } catch (_: Exception) {
                clean.substringBefore('?')
            }

            // Some provider binaries (notably Anizm v2 and TrAnimeIzle v27) recognize `.mpd`
            // but emit the generic VIDEO hint rather than DASH. A weak `/player/` marker below
            // must not make that manifest take the HLS path.
            if (path.contains(".mpd")) return false

            // 1) KESİN HLS belirteçleri (manifest adı / m3u8 yolu). Bunlar her şeyden
            //    öncedir: FilmMakinesi / CloseLoad 'hls/...mp4/master.txt' yapısı.
            // YouTube's HLS endpoint uses a signed URL such as
            // manifest.googlevideo.com/api/manifest/hls_playlist/... with no .m3u8
            // suffix. Without explicit HLS MIME, Media3 treats it as progressive media,
            // leaving the inline player at 00:00 / 00:00.
            if ((clean.contains("manifest.googlevideo.com") &&
                    (clean.contains("hls_playlist") || clean.contains("hls_variant"))) ||
                clean.contains(".m3u8") || clean.contains("m3u8") ||
                clean.contains("master.txt") || clean.contains("playlist.txt") ||
                clean.contains("video.txt") || clean.contains("tracks.txt") ||
                clean.contains("vidmixi.com/list") || clean.contains("vidmixi.com/m3u")) {
                return true
            }

            // Sistenn'in `cf/source` uçları bazen `.m3u8` uzantısı olmadan `/v4/`
            // adresi verir; plugin bunu HLS olarak yayınlar. Bu bilgi link tipine
            // taşınmadığı için host + yol sözleşmesini burada koru.
            val isSistennHost = clean.contains("sistenn.uns.bio") ||
                clean.contains("anizm.rpmvid.com") ||
                clean.contains("anizm.strp2p.com")
            if (isSistennHost && (path.contains("/v4/") || path.contains("/hlsmod/")) &&
                !PROGRESSIVE_PATH_REGEX.containsMatchIn(path)
            ) {
                return true
            }

            // 2) v2.4.258 + v2.4.304: Yol bir DOĞRUDAN progressive dosyaya bitiyorsa
            //    (.mp4/.mkv/.webm/.avi/.m4v) HLS DEĞİLDİR. v2.4.304'e kadar '/hls/' gibi
            //    ZAYIF yol belirteçleri bu kontrolden ÖNCE koşuyordu; closeload / rapidrame
            //    CDN'lerinin '/hls/.../1080p.mp4' biçimindeki düz dosyaları yine HLS MIME'ı
            //    ile (application/x-mpegURL) ExoPlayer'a veriliyor ve çözülemiyordu.
            //    Sıralama: kesin manifest → progressive uzantı → zayıf yol/host belirteçleri.
            if (PROGRESSIVE_PATH_REGEX.containsMatchIn(path)) return false

            // 3) Zayıf HLS yol belirteçleri (uzantısız manifest verebilirler)
            if (clean.contains("/hls/") || clean.contains("/hls") ||
                clean.contains("/m3u8/") || clean.contains("/playlist/") ||
                clean.contains("/hls-vod/")) {
                return true
            }

            // 4) Bilinen HLS üreten host / CDN'ler (uzantısız manifest verebilirler)
            if (clean.contains("vidmixi") || clean.contains("dosyaload") ||
                clean.contains("playmix.uno") || clean.contains("playmix") ||
                clean.contains("closeload") || clean.contains("rapidrame") ||
                clean.contains("rplayer") || clean.contains("hdfilmcehennemi") ||
                clean.contains("filmmakinesi") || clean.contains("filmakinesi") ||
                clean.contains("index-v") ||
                clean.contains("index-a") || clean.contains("imgsapi") ||
                clean.contains("molystream") || clean.contains("macellan") ||
                clean.contains("vmeas") || clean.contains("vidpapi") ||
                clean.contains("hdplayersystem") || clean.contains("imagestoo") ||
                clean.contains("pichive") || clean.contains("dizilla")) {
                return true
            }

            // 5) Olası akış yolları
            return clean.contains("/stream/") ||
                   clean.contains("/player/") ||
                   clean.contains("/embed/")
        }

        fun isDashUrl(videoUrl: String, streamTypeHint: String? = null): Boolean {
            when (streamTypeHint?.uppercase(Locale.ROOT)) {
                "DASH" -> return true
                "M3U8", "HLS" -> return false
            }
            return videoUrl.contains(".mpd", ignoreCase = true)
        }

        /** Yolun sonu doğrudan bir progressive video dosyası uzantısıysa eşleşir. */
        private val PROGRESSIVE_PATH_REGEX = Regex("""\.(mp4|mkv|webm|avi|m4v)$""")

        /**
         * URL'nin origin'ini (`scheme://host[:port]`) döndürür. Çözümlenemezse null.
         * CsStreamRunner.originOfUrl ile aynı mantık — oynatıcı katında da CDN Origin
         * doğrulaması için embed Referer'dan türetme gerekiyor.
         */
        internal fun originOfUrl(url: String?): String? {
            if (url.isNullOrBlank()) return null
            return try {
                val uri = java.net.URI(url.trim())
                val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
                val scheme = uri.scheme?.takeIf { it.isNotBlank() } ?: "https"
                val port = if (uri.port > 0 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""
                "$scheme://$host$port"
            } catch (_: Exception) {
                null
            }
        }
    }
}
