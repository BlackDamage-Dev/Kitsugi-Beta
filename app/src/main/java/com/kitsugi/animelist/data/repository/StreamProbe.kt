package com.kitsugi.animelist.data.repository

import com.kitsugi.animelist.core.memory.BoundedCache
import com.kitsugi.animelist.core.player.DashManifest
import com.kitsugi.animelist.core.player.HlsMasterPlaylist
import com.kitsugi.animelist.core.player.HlsVariant
import com.kitsugi.animelist.core.player.QualityOption

import android.util.Log
import com.kitsugi.animelist.core.network.KitsugiHttpClient
import com.kitsugi.animelist.data.cloudstream.CsVideoInterceptorFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Akışın GERÇEK bilgilerini kaynağın kendisinden ölçer.
 *
 * - HLS (`.m3u8`): master playlist indirilir; `#EXT-X-STREAM-INF` satırlarından gerçek
 *   `RESOLUTION` değerleri, `#EXT-X-MEDIA` satırlarından gerçek ses/altyazı dilleri okunur.
 * - Progressive (`.mp4`, `.mkv`, `.webm`): HTTP HEAD ile `Content-Length` (gerçek dosya boyutu).
 *
 * Böylece arayüzdeki "1080p", "Altyazılı", "Dublaj" rozetleri tahmin değil ölçüm olur.
 * Ölçülemeyen bilgi için rozet basılmaz.
 *
 * Sonuçlar URL bazlı önbelleğe alınır; eşzamanlı ölçüm sayısı sınırlıdır.
 */
object StreamProbe {

    private const val TAG = "StreamProbe"
    private const val MAX_PLAYLIST_BYTES = 128 * 1024L
    private const val PROBE_TIMEOUT_MS = 8_000L

    /**
     * Eşzamanlı ölçüm sayısı.
     *
     * 3 → 2: Listede aynı anda onlarca kart var; ölçümler OkHttp bağlantı havuzunu ve
     * mobil radyoyu doldurduğunda resim yükleme/kaydırma da yavaşlıyordu.
     */
    private val gate = Semaphore(2)

    private val cache = BoundedCache<String, MeasuredStreamInfo>("streamProbe", 200)

    /**
     * Tamamlanan ölçüm sayacı.
     *
     * Listede sıralama/filtreler ölçüm sonucunu (gerçek boyut, ses/altyazı dilleri, codec)
     * kullanır. Ölçüm sonuçları asenkron geldiği için sayaç artmadıkça liste eski bilgiyle
     * donup kalıyordu. Sayaç yalnızca GERÇEKTEN yeni bir sonuç üretildiğinde artar.
     */
    private val _completions = kotlinx.coroutines.flow.MutableStateFlow(0)
    val completions: kotlinx.coroutines.flow.StateFlow<Int> = _completions

    /**
     * CloudStream kaynakları için **oynatıcıyla aynı** HTTP istemcisi.
     *
     * rapidrame / closeload gibi embed'ler Referer, Cloudflare çerezi ve eklentinin
     * `getVideoInterceptor()` başlıklarını ister. Düz istemciyle ölçüm 403 döner ve
     * kalite listesi boş kalırdı; oynatıcı ise aynı URL'yi sorunsuz açıyordu.
     */
    private val csClient: OkHttpClient? by lazy {
        runCatching { CsVideoInterceptorFactory.buildClient(headers = emptyMap()) }.getOrNull()
    }

    private fun clientFor(stream: StreamSource): OkHttpClient {
        if (!stream.isCS) return KitsugiHttpClient.client
        val provider = com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(stream.addonName)
        val interceptor = CsVideoInterceptorFactory.videoInterceptor(
            provider = provider,
            name = stream.title ?: stream.addonName,
            url = stream.url.orEmpty(),
            headers = stream.requestHeaders.orEmpty(),
            quality = stream.qualityValue,
            mediaType = stream.mediaType
        )
        return if (interceptor != null) {
            CsVideoInterceptorFactory.buildClient(interceptor = interceptor, headers = stream.requestHeaders.orEmpty())
        } else {
            csClient ?: KitsugiHttpClient.client
        }
    }

    /** Kalite sayfası için varyant listesi önbelleği (URL başına). */
    private val variantCache = BoundedCache<String, List<HlsVariant>>("streamVariants", 60)

    /** Kalite sayfası için (HLS + DASH) seçenek önbelleği. */
    private val optionCache = BoundedCache<String, List<QualityOption>>("streamQualityOptions", 60)

    /** Daha önce aynı URL, transport ipucu ve request header'larıyla ölçülmüş sonuç. */
    fun cached(stream: StreamSource): MeasuredStreamInfo? = cache[cacheKeyFor(stream)]

    /** Daha önce ölçülmüş sonuç (anında arayüze verilebilir). */
    fun cached(url: String?): MeasuredStreamInfo? = url?.let { cache[it] }

    fun clearCache() {
        cache.clear()
        variantCache.clear()
        optionCache.clear()
    }

    internal fun cacheKeyFor(stream: StreamSource): String {
        val material = buildString {
            append(stream.url?.trim().orEmpty())
            append('\n').append(stream.streamTypeHint?.uppercase(Locale.ROOT).orEmpty())
            stream.requestHeaders.orEmpty().entries
                .sortedBy { it.key.lowercase(Locale.ROOT) }
                .forEach { (name, value) ->
                    append('\n').append(name.lowercase(Locale.ROOT)).append(':').append(value)
                }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
    }

    /** Bu akış için ölçüm yapmak anlamlı mı? (torrent / embed sayfaları ölçülmez) */
    fun isProbeable(stream: StreamSource): Boolean {
        if (stream.isTorrent) return false
        val url = stream.url?.trim().orEmpty()
        if (!url.startsWith("http", ignoreCase = true)) return false
        // DASH rozet ölçümü kalite sayfasında yapılır; kart listesinde manifest indirilmez.
        return when (mediaKind(stream)) {
            MediaKind.HLS, MediaKind.PROGRESSIVE -> true
            MediaKind.DASH, MediaKind.UNSUPPORTED -> false
        }
    }

    /**
     * Ölçüm bu akışa YENİ bilgi katıyor mu?
     *
     * NEDEN GEREKLİ: Kitsugi rozetleri kanıta dayalı gösterir. Kaynak (CloudStream eklentisi /
     * Stremio) kalite, ses türü ve altyazı listesini ZATEN bildirdiyse ağ üzerinden ölçüm yapmak
     * hiçbir yeni rozet üretmez — yalnızca veri harcar ve kaydırmayı kasar. Bilinen eksik bilgi
     * varsa (ör. yalnızca kalite verilmiş, altyazı verilmemiş) ölçüm yine yapılır.
     */
    fun isProbeNeeded(stream: StreamSource): Boolean {
        if (!isProbeable(stream)) return false
        val url = stream.url?.trim().orEmpty()
        val qualityKnown = StreamInfoResolver.csQualityHeightOrNull(stream.qualityValue) != null
        val audioKnown = !stream.providerAudioKind.isNullOrBlank() ||
            stream.providerLanguages.isNotEmpty()
        val subtitlesKnown = stream.subtitles.isNotEmpty()
        // v2.4.340: boyut bilgisi de ölçüm gerekçesidir. Progressive (mp4/mkv) kaynaklarda
        // kaynak boyut bildirmediyse HEAD isteği hem canlılık hem GERÇEK boyut verir.
        // (HLS'te ölçüm boyut üretmez; bu yüzden yalnızca progressive dosyalarda zorlanır.)
        val sizeKnown = stream.videoSizeBytes != null
        val sizeObtainable = mediaKind(url) == MediaKind.PROGRESSIVE
        if (!sizeKnown && sizeObtainable && qualityKnown && audioKnown && subtitlesKnown) return true
        return !(qualityKnown && audioKnown && subtitlesKnown)
    }

    private enum class MediaKind { HLS, DASH, PROGRESSIVE, UNSUPPORTED }

    private fun mediaKind(stream: StreamSource): MediaKind {
        when (stream.streamTypeHint?.uppercase(Locale.ROOT)) {
            "M3U8", "HLS" -> return MediaKind.HLS
            "DASH" -> return MediaKind.DASH
            "TORRENT", "MAGNET" -> return MediaKind.UNSUPPORTED
        }
        return mediaKind(stream.url.orEmpty())
    }

    private fun mediaKind(url: String): MediaKind {
        val lower = url.lowercase(Locale.ROOT).substringBefore('#')
        return when {
            lower.contains(".m3u8") -> MediaKind.HLS
            lower.contains(".mpd") -> MediaKind.DASH
            lower.contains(".mp4") || lower.contains(".mkv") ||
                lower.contains(".webm") || lower.contains(".m4v") -> MediaKind.PROGRESSIVE
            else -> MediaKind.UNSUPPORTED
        }
    }

    /**
     * Akışı ölçer (önbellekte varsa onu döndürür). Ölçüm mümkün değilse `null` döner —
     * bu durumda arayüz yalnızca kaynağın bildirdiği bilgileri gösterir.
     */
    suspend fun probe(stream: StreamSource): MeasuredStreamInfo? {
        val url = stream.url?.trim().orEmpty()
        if (!isProbeable(stream)) return null
        val key = cacheKeyFor(stream)
        cache[key]?.let { return it }

        val result = gate.withPermit {
            cache[key] ?: withContext(Dispatchers.IO) {
                withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                    runCatching {
                        val client = clientFor(stream)
                        when (mediaKind(stream)) {
                            MediaKind.HLS -> probeHls(url, stream.requestHeaders, client)
                            MediaKind.PROGRESSIVE -> probeProgressive(url, stream.requestHeaders, client)
                            // DASH: rozet ölçümü kalite sayfasında (`qualityOptions`) yapılır;
                            // burada ikinci bir manifest isteği atılmaz.
                            MediaKind.DASH, MediaKind.UNSUPPORTED -> null
                        }
                    }.getOrElse { error ->
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        Log.w(TAG, "Ölçüm hatası (${error.javaClass.simpleName}): ${url.take(80)}")
                        MeasuredStreamInfo(failed = true)
                    }
                } ?: MeasuredStreamInfo(failed = true)
            }
        }

        cache[key] = result
        _completions.value += 1
        return result
    }

    /**
     * Açık akış için seçilebilir **video kaliteleri** (HLS master varyantları).
     *
     * Oynatıcının "Kalite" sayfası bunu çağırır: eklenti kalite bildirmese bile
     * (rapidrame / closeload → `Qualities.Unknown`) gerçek varyant listesi buradan gelir.
     * Ölçüm [probe] ile aynı önbelleği kullanır; sayfa açıldığında ikinci kez ağa çıkılmaz.
     */
    suspend fun variants(stream: StreamSource): List<HlsVariant> {
        val url = stream.url?.trim().orEmpty()
        if (!url.startsWith("http", ignoreCase = true)) return emptyList()

        variantCache[url]?.let { return it }

        val kind = mediaKind(url)
        val list: List<HlsVariant> = when {
            // Standart HLS: ölçüm sonucu zaten varyantları taşır (aynı önbellek).
            kind == MediaKind.HLS -> {
                // Liste sayfasındaki rozet ölçümü başlıksız/çerezsiz yapılmış ve
                // başarısız olmuş olabilir. Oynatıcı bağlamında (Referer + çerez hazır)
                // bir kez daha denenir; aksi hâlde kalite listesi kalıcı olarak boş kalırdı.
                val key = cacheKeyFor(stream)
                if (cache[key]?.failed == true) cache.remove(key)
                if (cache[url]?.failed == true) cache.remove(url)
                probe(stream)?.variants.orEmpty()
            }

            // closeload gibi kaynaklar playlist'i `master.txt` olarak sunar; uzantı
            // m3u8 olmadığı için normal ölçüm atlanır. İçerik `#EXTM3U` ise yine de
            // gerçek varyantlar çıkarılabilir.
            kind == MediaKind.UNSUPPORTED && looksLikePlaylist(url) ->
                fetchVariants(url, stream.requestHeaders, clientFor(stream))

            else -> emptyList()
        }
        // Boş sonucu önbelleğe YAZMA: geçici ağ hatası/403 kalıcı "kalite yok"a dönüşmesin.
        if (list.isNotEmpty()) variantCache[url] = list
        return list
    }

    /**
     * Açık akışın okunabilen **tüm** kalite seçenekleri.
     *
     * Desteklenen kaynaklar (tek giriş noktası):
     *  - **HLS** (`master.m3u8`, `.txt/master.txt`): gerçek varyant URL'leri → seçim URL değiştirir.
     *  - **DASH** (`.mpd`): manifest içindeki video `Representation`'lar → seçim motorun
     *    çözünürlük tavanıyla uygulanır ([QualityOption.switchUrl] boş).
     *
     * Ölçülemezse **boş liste** döner; arayüz uydurma kalite göstermez.
     */
    suspend fun qualityOptions(stream: StreamSource): List<QualityOption> {
        val url = stream.url?.trim().orEmpty()
        if (!url.startsWith("http", ignoreCase = true)) return emptyList()

        optionCache[url]?.let { return it }

        val measured: List<QualityOption> = when (mediaKind(url)) {
            MediaKind.DASH -> fetchDashOptions(url, stream)
            // Uzantısız/garip linkler (ör. `/play/abc123`) manifest olabilir:
            // içerik koklanır (`<MPD` / `#EXTM3U`). Progressive uzantılar, P2P yerel
            // sunucusu (127.0.0.1/localhost) ve segment adresleri koklanmaz.
            MediaKind.UNSUPPORTED -> if (shouldSniff(url)) sniffManifestOptions(url, stream) else emptyList()
            else -> variants(stream).map { QualityOption.fromHls(it) }
        }

        // Ölçüm hiçbir şey vermediyse ama eklenti kaliteyi ZATEN bildirdiyse
        // (CS `ExtractorLink.quality`, Stremio başlığı) o bilgi kaybolmaz:
        // tek, seçilemez ama görünür bir satır olarak gösterilir.
        val options = if (measured.isEmpty()) declaredQualityOption(stream)?.let { listOf(it) } ?: emptyList()
        else measured

        if (options.isNotEmpty()) optionCache[url] = options
        return options
    }

    /** Eklentinin/kaynağın bildirdiği kalite (ölçüm başarısız olduğunda son çare). */
    private fun declaredQualityOption(stream: StreamSource): QualityOption? {
        val height = StreamInfoResolver.csQualityHeightOrNull(stream.qualityValue)
        val label = stream.quality?.trim()?.takeIf { it.isNotEmpty() }
            ?: height?.let { "${it}p" }
        if (label.isNullOrEmpty()) return null
        return QualityOption(
            label = label,
            height = height,
            bandwidth = null,
            switchUrl = null,
            declaredBySource = true,
        )
    }

    /**
     * Manifest içeriğini koklar: DASH (`<MPD`) veya HLS master (`#EXTM3U` + `#EXT-X-STREAM-INF`).
     *
     * Progressive mp4/mkv uzantılı linklerde çağrılmaz; onlarda seçilebilir kalite zaten yoktur.
     */
    private suspend fun sniffManifestOptions(url: String, stream: StreamSource): List<QualityOption> =
        gate.withPermit {
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                    runCatching {
                        val body = fetchText(url, stream.requestHeaders, clientFor(stream))
                            ?: return@runCatching emptyList<QualityOption>()
                        when {
                            body.contains("<MPD", ignoreCase = true) -> DashManifest.parse(body)
                            body.contains("#EXTM3U", ignoreCase = true) ->
                                HlsMasterPlaylist.parse(url, body).map { QualityOption.fromHls(it) }
                            else -> emptyList()
                        }
                    }.getOrElse { error ->
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        emptyList()
                    }
                } ?: emptyList()
            }
        }

    private suspend fun fetchDashOptions(url: String, stream: StreamSource): List<QualityOption> =
        gate.withPermit {
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                    runCatching {
                        val body = fetchText(url, stream.requestHeaders, clientFor(stream))
                            ?: return@runCatching emptyList<QualityOption>()
                        DashManifest.parse(body)
                    }.getOrElse { error ->
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        Log.w(TAG, "DASH ayrıştırma hatası (${error.javaClass.simpleName}): ${url.take(80)}")
                        emptyList()
                    }
                } ?: emptyList()
            }
        }

    /**
     * Bu URL manifest olabilir mi? (içerik koklamadan önce ucuz ön filtre)
     *
     * P2P/yerel akış sunucusuna (`127.0.0.1`, `localhost`) ve bilinen medya/segment
     * uzantılarına istek atılmaz: boşa ağ trafiği ve gecikme olurdu.
     */
    private fun shouldSniff(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        if (lower.contains("127.0.0.1") || lower.contains("localhost") || lower.contains("[::1]")) return false
        val path = lower.substringBefore('?').substringBefore('#')
        if (NON_MANIFEST_SUFFIXES.any { path.endsWith(it) }) return false
        return true
    }

    /** Manifest olamayacağı kesin uzantılar (segment, ses, görsel, arşiv). */
    private val NON_MANIFEST_SUFFIXES = listOf(
        ".ts", ".m4s", ".mp4", ".mkv", ".webm", ".m4v", ".mov", ".avi",
        ".mp3", ".m4a", ".aac", ".flac", ".ogg", ".opus", ".wav",
        ".jpg", ".jpeg", ".png", ".webp", ".gif", ".vtt", ".srt", ".ass"
    )

    /** Fast-path: uzantısı m3u8 olmayan ama playlist olma ihtimali yüksek URL'ler. */
    private fun looksLikePlaylist(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return lower.contains("master") || lower.contains(".m3u8") ||
            lower.contains("/hls/") || lower.contains("/txt/")
    }

    private suspend fun fetchVariants(
        url: String,
        headers: Map<String, String>?,
        client: OkHttpClient,
    ): List<HlsVariant> =
        gate.withPermit {
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                    runCatching {
                        val body = fetchText(url, headers, client) ?: return@runCatching emptyList<HlsVariant>()
                        HlsMasterPlaylist.parse(url, body)
                    }.getOrElse { error ->
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        Log.w(TAG, "Varyant ayrıştırma hatası (${error.javaClass.simpleName}): ${url.take(80)}")
                        emptyList()
                    }
                } ?: emptyList()
            }
        }

    // ── HLS ──────────────────────────────────────────────────────────────────

    private val RESOLUTION_ATTR = Regex("""RESOLUTION=(\d{2,5})x(\d{2,5})""", RegexOption.IGNORE_CASE)
    private val BANDWIDTH_ATTR = Regex("""BANDWIDTH=(\d+)""", RegexOption.IGNORE_CASE)
    private val CODECS_ATTR = Regex("""CODECS="([^"]+)""", RegexOption.IGNORE_CASE)
    private val LANGUAGE_ATTR = Regex("LANGUAGE=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
    private val TYPE_ATTR = Regex("""TYPE=([A-Z\-]+)""", RegexOption.IGNORE_CASE)

    private suspend fun probeHls(
        url: String,
        headers: Map<String, String>?,
        client: OkHttpClient,
    ): MeasuredStreamInfo {
        val body = fetchText(url, headers, client) ?: return MeasuredStreamInfo(failed = true)
        if (!body.contains("#EXTM3U")) return MeasuredStreamInfo(failed = true)

        val variantHeights = mutableListOf<Int>()
        val audioLanguages = mutableListOf<String>()
        val subtitleLanguages = mutableListOf<String>()
        // Varyant CODECS + BANDWIDTH: HLS master playlist'i indirmişken codec'i de
        // ayrıştırıyoruz (ek istek yok). Böylece kartta "HEVC"/"AV1" gerçekten ölçülmüş olur.
        val codecs = mutableSetOf<String>()
        var maxBandwidth = 0L

        body.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true) -> {
                    RESOLUTION_ATTR.find(line)?.groupValues?.getOrNull(2)?.toIntOrNull()?.let {
                        variantHeights.add(it)
                    }
                    BANDWIDTH_ATTR.find(line)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { bw ->
                        if (bw > maxBandwidth) maxBandwidth = bw
                    }
                    CODECS_ATTR.find(line)?.groupValues?.getOrNull(1)
                        ?.split(',')
                        ?.map { it.trim().lowercase(Locale.ROOT) }
                        ?.filter { it.isNotEmpty() }
                        ?.let { codecs.addAll(it) }
                }
                line.startsWith("#EXT-X-MEDIA", ignoreCase = true) -> {
                    val type = TYPE_ATTR.find(line)?.groupValues?.getOrNull(1)?.uppercase(Locale.ROOT)
                    val lang = LANGUAGE_ATTR.find(line)?.groupValues?.getOrNull(1)
                    if (!lang.isNullOrBlank()) {
                        when (type) {
                            "AUDIO" -> audioLanguages.add(lang)
                            "SUBTITLES", "CLOSED-CAPTIONS" -> subtitleLanguages.add(lang)
                        }
                    }
                }
            }
        }

        val isMaster = body.contains("#EXT-X-STREAM-INF", ignoreCase = true)

        // Kalite seçimi için GERÇEK oynatılabilir varyantlar (URL + çözünürlük).
        // rapidrame / closeload master'ları buradan çözülür.
        val variants = if (isMaster) HlsMasterPlaylist.parse(url, body) else emptyList()

        val distinctHeights = (variantHeights + variants.mapNotNull { it.height })
            .distinct()
            .sorted()

        return MeasuredStreamInfo(
            height = distinctHeights.maxOrNull().takeIf { distinctHeights.size == 1 },
            isAdaptive = isMaster,
            variantHeights = distinctHeights,
            variants = variants,
            audioLanguages = audioLanguages.distinct(),
            subtitleLanguages = subtitleLanguages.distinct(),
            sizeBytes = null,
            failed = false,
            // HLS'te toplam dosya boyutu playlist'te YOKTUR (parça parça indirilir) →
            // uydurma boyut yazmıyoruz; yalnızca codec ve bit hızı raporlanır.
            maxBandwidthBps = maxBandwidth.takeIf { it > 0 },
            codec = codecs.firstNotNullOfOrNull { raw ->
                when {
                    raw.startsWith("hvc1") || raw.startsWith("hev1") -> "HEVC"
                    raw.startsWith("avc1") || raw.startsWith("avc3") -> "H.264"
                    raw.startsWith("av01") -> "AV1"
                    raw.startsWith("vp09") -> "VP9"
                    raw.startsWith("mp4a") || raw.startsWith("opus") || raw.startsWith("ac-3") ||
                        raw.startsWith("ec-3") || raw.startsWith("flac") -> null // ses codec'i
                    else -> null
                }
            }
        )
    }

    // ── Progressive dosyalar ─────────────────────────────────────────────────

    private suspend fun probeProgressive(
        url: String,
        headers: Map<String, String>?,
        client: OkHttpClient,
    ): MeasuredStreamInfo {
        val request = buildRequest(url, headers).head().build()
        // İptal edilebilir async çağrı: kart görüş alanından çıkınca istek GERÇEKTEN iptal edilir
        // ve eşzamanlılık kapısı (semaphore) hemen serbest kalır.
        val response = awaitResponse(client.newCall(request))
            ?: return MeasuredStreamInfo(failed = true)
        return response.use { resp ->
            val mime = resp.header("Content-Type").orEmpty().substringBefore(';').trim()
            if (!resp.isSuccessful || mime.equals("text/html", true) || mime.equals("application/xhtml+xml", true)) {
                MeasuredStreamInfo(failed = true)
            } else {
                val length = resp.header("Content-Length")?.toLongOrNull()
                MeasuredStreamInfo(
                    sizeBytes = length?.takeIf { it > 0 },
                    failed = length == null
                )
            }
        }
    }

    // ── Ortak yardımcılar ────────────────────────────────────────────────────

    private fun buildRequest(url: String, headers: Map<String, String>?): Request.Builder {
        val builder = Request.Builder().url(url)
        headers?.forEach { (key, value) ->
            if (key.isNotBlank() && value.isNotBlank()) {
                runCatching { builder.header(key, value) }
            }
        }
        return builder
    }

    private suspend fun fetchText(
        url: String,
        headers: Map<String, String>?,
        client: OkHttpClient,
    ): String? {
        val request = buildRequest(url, headers)
            .header("Range", "bytes=0-${MAX_PLAYLIST_BYTES - 1}")
            .get()
            .build()
        val response = awaitResponse(client.newCall(request)) ?: return null
        return response.use { resp ->
            if (!resp.isSuccessful) null else resp.peekBody(MAX_PLAYLIST_BYTES).string()
        }
    }

    /**
     * OkHttp çağrısını askıya alınabilir (cancellable) biçimde çalıştırır.
     *
     * NEDEN: Eskiden `execute()` (bloklayıcı) kullanılıyordu. Coroutine iptal edilse bile
     * bloklayıcı çağrı arka planda devam ediyor, `gate` semaforundaki izni 8 sn boyunca
     * TUTUYORDU. Listeyi hızlı kaydırınca onlarca terk edilmiş ölçüm izinleri tüketiyor ve
     * yeni görünen kartların rozetleri hiç gelmiyordu (kilitlenme hissi).
     */
    private suspend fun awaitResponse(call: Call): Response? = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { runCatching { call.cancel() } }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resume(null)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else runCatching { response.close() }
            }
        })
    }
}
