package com.kitsugi.animelist.data.cloudstream.extractors

import android.util.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.extractorApis
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URI
import java.util.Locale

/**
 * T-CR-1 — Close / Rapid (JWPlayer "dc_" şifreli) CDN ailesi için UYGULAMA YERLEŞİK extractor.
 *
 * NEDEN GEREKLİ (2026-10-18 kök neden analizi):
 *  1. HDFilmCehennemi ve FilmMakinesi videoları aynı iki gömülü kaynaktan geliyor:
 *     **Close** (`closeload.*`) ve **Rapid** (`rapidrame.*` / `rplayer.*`).
 *  2. Eklentiler (.cs3) cihazlarda bayat kalabiliyor: depo manifestindeki `version`
 *     yükseltilmeden yeniden derleme yapıldığında güncelleme denetimi "güncel" sanıp
 *     ATLADIĞI için kullanıcı eski çözücüyle kalıyor ("halen oynatılmıyor" döngüsü).
 *  3. FilmMakinesi eklentisi (v62 dahil) `rapidrame` iframe'ini `loadExtractor`'a bırakıyor
 *     ama hiçbir eklenti rapidrame için extractor KAYDETMİYOR → "DUAL Rapid" düğmesi
 *     uygulama tarafında sıfır link üretiyordu.
 *
 * ÇÖZÜM: CS çalışma zamanının global `extractorApis` listesine uygulama yerleşikleri
 * eklenir. `loadExtractor` önek eşleşmesini TERSTEN iterasyonda yaptığı için eklentiler
 * sonradan kayıt olduğunda kendi host'larında öncelik alır; eklentinin hiç bilmediği
 * host'larda (rapidrame.*, closeload.top, closeload.com ...) ise bu yerleşikler devreye
 * girer. Böylece oynatma zinciri eklenti sürümünden bağımsız hale gelir.
 *
 * Algoritma, eklenti deposunun son kararlı çözücüsüyle (CloseLoad v62) birebir aynıdır:
 *  - JWPlayer sayfasındaki `dc_*(...)` JS bloğunu native deşifre et
 *    (dizi birleştir → işlem sırası: atob / reverse / rot(replace) → modulo unmix),
 *  - olmazsa JSON-LD `contentUrl`,
 *  - olmazsa sayfada `master.txt` / `.m3u8` ara,
 *  - master listesindeki tüm kalite varyantlarını ve adaptif bağlantıyı koru;
 *    Origin/Referer'ı GERÇEK yayın host'una sabitle (yoksa CDN 5004 ile düşürüyor).
 */
class KitsugiCdnJwExtractor(
    override val name: String,
    override val mainUrl: String
) : ExtractorApi() {

    override val requiresReferer: Boolean = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit
    ) {
        // Referer, çağıranın verdiği host değil embed adresinin KENDİ host'udur:
        // closeload.filmmakinesi.to ölünce başka bir closeload host'u da çalışsın.
        val origin = originOf(url)
            ?: referer?.let { originOf(it) }?.takeIf { it.startsWith("http") }
            ?: mainUrl
        val pageHeaders = headersFor(origin)

        try {
            val html = app.get(url, headers = pageHeaders).text
            if (html.isBlank()) {
                val msg = "[$name] boş HTML — embed sayfası boş döndü (403/CF?) url=${url.take(90)} origin=$origin"
                Log.w(TAG, msg)
                com.kitsugi.animelist.data.cloudstream.diag.CsTrace.warn(name, "extract", msg)
                mirrorToAliasTags("extract", msg, null)
            }

            // 1) JS desifre algoritmasi - dc_ bloklu sayfada bayat fallback'e dusme
            val hasEncryptedBlock = html.contains("dc_") && html.contains("<script")
            var realUrl = decryptNative(html)
            if (realUrl.isNullOrBlank()) {
                if (hasEncryptedBlock) {
                    val msg = "[$name] sifreli blok var ama cozulemedi - bayat contentUrl/master fallback'i atlaniyor (404 sahte link uretmemek icin) url=${url.take(90)}"
                    Log.w(TAG, msg)
                    com.kitsugi.animelist.data.cloudstream.diag.CsTrace.warn(name, "extract", msg)
                    mirrorToAliasTags("extract", msg, null)
                    realUrl = M3U8_REGEX.find(html)?.value ?: MASTER_TXT_REGEX.find(html)?.value
                    if (!realUrl.isNullOrBlank()) {
                        Log.d(TAG, "[$name] sifreli cozum yok ama sayfada acik HLS bulundu -> $realUrl")
                    } else {
                        Log.w(TAG, "[$name] sifreli cozum yok ve acik HLS de yok - kaynak uretilmiyor url=${url.take(90)}")
                        com.kitsugi.animelist.data.cloudstream.diag.CsTrace.warn(name, "extract", "crypted block cozulemedi, acik HLS yok - bos donuluyor url=${url.take(90)}")
                        mirrorToAliasTags("extract", "crypted block cozulemedi, bos", null)
                    }
                } else {
                    realUrl = CONTENT_URL_REGEX.find(html)?.groupValues?.get(1)?.replace("\\/", "/")
                    if (realUrl.isNullOrBlank()) {
                        realUrl = MASTER_TXT_REGEX.find(html)?.value ?: M3U8_REGEX.find(html)?.value
                    }
                }
            }
            if (realUrl.isNullOrBlank()) {
                realUrl = MASTER_TXT_REGEX.find(html)?.value ?: M3U8_REGEX.find(html)?.value
            }

            if (!realUrl.isNullOrBlank() && realUrl.startsWith("http")) {
                val streams = resolveMasterPlaylist(realUrl, pageHeaders)
                for ((variant, streamHeaders) in streams) {
                    val streamUrl = variant.url
                    val type = if (looksLikeHls(realUrl) || looksLikeHls(streamUrl)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    val label = variant.height?.let { "${it}p" } ?: "Auto"
                    callback.invoke(
                        newExtractorLink(source = name, name = "$name $label", url = streamUrl, type = type) {
                            quality = variant.height ?: Qualities.Unknown.value
                            headers = streamHeaders
                        }
                    )
                }
            } else {
                val msg = "[$name] gerçek URL bulunamadı/deşifre edilemedi: url=${url.take(90)} origin=$origin htmlLen=${html.length}"
                Log.w(TAG, msg)
                com.kitsugi.animelist.data.cloudstream.diag.CsTrace.warn(name, "extract", msg)
                mirrorToAliasTags("extract", msg, null)
            }

            // Altyazılar (JWPlayer tracks bloğu). for-döngüsü: newSubtitleFile suspend.
            for ((label, file) in parseSubtitleTracks(html)) {
                subtitleCallback(newSubtitleFile(label, file))
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            reportExtractorError(stage = "extract", url = url, referer = referer, origin = origin, error = e)
        }
    }

    /** Keep the adaptive master and expose every independent source quality, not just 1080p. */
    private suspend fun resolveMasterPlaylist(
        playlistUrl: String,
        headers: Map<String, String>
    ): List<Pair<PlaylistVariant, Map<String, String>>> {
        val fallback = listOf(PlaylistVariant(playlistUrl, null) to headers)
        if (!looksLikeHls(playlistUrl)) return fallback
        return try {
            val response = app.get(playlistUrl, headers = headers)
            val listText = response.text
            val variants = parseVariants(listText, playlistUrl)
            if (variants.isEmpty()) return fallback
            val adaptive = listText.lineSequence().any { it.trim().startsWith("#EXT-X-STREAM-INF:") }
            // Keep HLS renditions inside the active video. Emitting child URLs as
            // sources makes autoplay choose a fixed variant and hides the other
            // resolutions (and potentially alternate audio/subtitles) from the player.
            if (adaptive || listText.lineSequence().any { it.trim().startsWith("#EXT-X-MEDIA:") }) return fallback
            val links = variants.map { variant ->
                val streamOrigin = originOf(variant.url)
                val streamHeaders = if (streamOrigin != null && streamOrigin != originOf(playlistUrl)) {
                    headersFor(streamOrigin)
                } else headers
                variant to streamHeaders
            }
            links
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            reportExtractorError(stage = "master", url = playlistUrl, referer = headers["Referer"], origin = headers["Origin"], error = e)
            fallback
        }
    }

    // ── Hata yakalayıcı — tüm extractor hataları buradan tek formatta loglanır ──────
    private fun reportExtractorError(stage: String, url: String, referer: String?, origin: String?, error: Throwable) {
        val shortUrl = url.take(100)
        val head = "[$name] HATA stage=$stage url=$shortUrl ref=${referer?.take(60)} origin=$origin | ${error.javaClass.simpleName}: ${error.message}"
        Log.e(TAG, head, error)
        com.kitsugi.animelist.data.cloudstream.diag.CsTrace.error(name, stage, head, error)
        mirrorToAliasTags(stage, head, error)
        // Ayrıca player loguna yaz — kullanıcı Ayarlar > Log'dan görebilsin
        try {
            val causeChain = generateSequence(error as Throwable) { it.cause }.joinToString(" → ") { "${it.javaClass.simpleName}: ${it.message}" }.take(300)
            Log.e("PlayerDiagnostics", "Extractor hata zinciri: $causeChain", error)
        } catch (_: Exception) {}
    }

    private fun mirrorToAliasTags(stage: String, message: String, error: Throwable?) {
        // Kullanıcı log'u HDCH / Kekik_CloseLoad / Rapidrame ile filtreliyor — o tag'lerde de görünsün
        val aliases = when {
            name.contains("Close", ignoreCase = true) -> listOf("Kekik_CloseLoad", "HDCH")
            name.contains("Rapid", ignoreCase = true) || name.contains("Rplayer", ignoreCase = true) -> listOf("Rapidrame", "HDCH")
            else -> emptyList()
        }
        for (alias in aliases) {
            if (error != null) Log.e(alias, message, error) else Log.w(alias, message)
            if (error != null) com.kitsugi.animelist.data.cloudstream.diag.CsTrace.error(alias, stage, message, error)
            else com.kitsugi.animelist.data.cloudstream.diag.CsTrace.warn(alias, stage, message)
        }
    }

    companion object {
        private const val TAG = "KitsugiCdnJwExtractor"

        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"

        private val CONTENT_URL_REGEX = Regex(""""contentUrl"\s*:\s*"([^"]+)"""")
        private val MASTER_TXT_REGEX = Regex("""https?://[^\s"']+/master\.txt[^\s"']*""")
        private val M3U8_REGEX = Regex("""https?://[^\s"']+\.m3u8[^\s"']*""")

        /** Uygulama yerleşikleri: (ad, mainUrl öneki). Önek eşleşmesi host ailesini kapsar. */
        val REGISTRATIONS: List<Pair<String, String>> = listOf(
            "KitsugiCloseLoad" to "https://closeload.",
            "KitsugiRapidrame" to "https://rapidrame.",
            "KitsugiRplayer" to "https://rplayer.",
            "KitsugiHdfcEmbed" to "https://hdfilmcehennemi.mobi",
            "KitsugiFmRapid" to "https://rapid.filmmakinesi.to",
            "KitsugiHdfcRplayerNl" to "https://www.hdfilmcehennemi.nl/rplayer/",
            "KitsugiHdfcRplayerBare" to "https://hdfilmcehennemi.nl/rplayer/"
        )

        /**
         * Yerleşik extractor'ları CS çalışma zamanına bir kez kaydeder.
         * Eklentiler sonradan kayıt olup kendi host'larında öncelik alır; burada yalnızca
         * eklentilerin hiç bilmediği / bayat kaldığı host aileleri garanti altına alınır.
         */
        fun registerAll() {
            for ((name, mainUrl) in REGISTRATIONS) {
                if (extractorApis.any { it.name == name }) continue
                runCatching { extractorApis.add(KitsugiCdnJwExtractor(name, mainUrl)) }
                    .onFailure { Log.w(TAG, "yerleşik extractor kaydedilemedi: $name — ${it.message}") }
            }
        }

        fun originOf(url: String): String? = try {
            val uri = URI(url)
            val host = uri.host ?: return null
            val scheme = uri.scheme ?: "https"
            val port = if (uri.port > 0 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""
            "$scheme://$host$port"
        } catch (e: Exception) {
            null
        }

        fun headersFor(origin: String): Map<String, String> = mapOf(
            "User-Agent" to USER_AGENT,
            "Referer" to "${origin.trimEnd('/')}/",
            "Origin" to origin
        )

        fun looksLikeHls(url: String): Boolean {
            val lower = url.lowercase(Locale.ROOT)
            return lower.contains(".m3u8") || lower.contains("master.txt") ||
                lower.contains("playlist.txt") || lower.contains("/hls/")
        }

        /** A source quality; unknown resolution is never fabricated as 1080p. */
        data class PlaylistVariant(val url: String, val height: Int?)

        fun parseVariants(listText: String, baseUrl: String): List<PlaylistVariant> {
            val result = mutableListOf<PlaylistVariant>()
            var pending: String? = null
            // Media playlist segments must never become selectable video sources.
            if (listText.contains("#EXTINF:")) return emptyList()
            for (line in listText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }) {
                if (line.startsWith("#EXT-X-STREAM-INF:")) {
                    pending = line
                    continue
                }
                if (line.startsWith("#")) continue
                val info = pending
                pending = null
                val candidate = absolutize(line, baseUrl) ?: continue
                if (!candidate.startsWith("https://") && !candidate.startsWith("http://")) continue
                if (info == null && !looksLikeHls(candidate)) continue
                val height = info?.let { Regex("RESOLUTION=\\d+x(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() }
                    ?: Regex("(\\d{3,4})p", RegexOption.IGNORE_CASE).find(candidate)?.groupValues?.get(1)?.toIntOrNull()
                result += PlaylistVariant(candidate, height?.takeIf { it > 0 })
            }
            return result.distinctBy { it.url }
        }

        fun pickVariant(listText: String, baseUrl: String): String? {
            val lines = listText.lines().map { it.trim() }.filter { it.isNotEmpty() }
            var bestUrl: String? = null
            var bestScore = -1
            var pendingScore = -1

            for (line in lines) {
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    pendingScore = variantScore(line)
                    continue
                }
                if (line.startsWith("#")) continue

                val candidate = absolutize(line, baseUrl) ?: continue
                if (!candidate.contains(".m3u8") && !candidate.contains("master.txt")) continue

                // resolution= etiketi yoksa dosya adındaki 1080p/720p ipucu kullanılır.
                val score = if (pendingScore >= 0) pendingScore else nameScore(candidate)
                if (score > bestScore) {
                    bestScore = score
                    bestUrl = candidate
                }
                pendingScore = -1
            }
            return bestUrl
        }

        fun variantScore(streamInfLine: String): Int {
            val resolution = Regex("""RESOLUTION=(\d+)x(\d+)""").find(streamInfLine)
            if (resolution != null) {
                val height = resolution.groupValues[2].toIntOrNull() ?: 0
                return height * 1000
            }
            val bandwidth = Regex("""BANDWIDTH=(\d+)""").find(streamInfLine)
            return (bandwidth?.groupValues?.get(1)?.toLongOrNull() ?: 0L).let { (it / 1000).toInt() }
        }

        fun nameScore(url: String): Int {
            val match = Regex("""(\d{3,4})p""").find(url.lowercase(Locale.ROOT)) ?: return 0
            return (match.groupValues[1].toIntOrNull() ?: 0) * 1000
        }

        /** Göreli liste satırını (index.m3u8, ../x/y.m3u8) tam adrese çevirir. */
        fun absolutize(line: String, baseUrl: String): String? {
            val value = line.replace("\\/", "/").trim().trim('"', '\'')
            if (value.isBlank()) return null
            return when {
                value.startsWith("http://") || value.startsWith("https://") -> value
                value.startsWith("//") -> "https:$value"
                else -> try {
                    URI(baseUrl).resolve(value).toString()
                } catch (e: Exception) {
                    null
                }
            }
        }

        /**
         * JWPlayer `tracks: [...]` bloğundaki altyazıları çıkarır: (etiket → dosya).
         * Yalnızca http ile başlayan ve altyazı türündeki (captions/subtitles veya türsüz)
         * kayıtlar döner.
         */
        fun parseSubtitleTracks(html: String): List<Pair<String, String>> {
            val out = mutableListOf<Pair<String, String>>()
            try {
                val tracksJson = html.substringAfter("tracks: [", "").substringBefore("]")
                if (tracksJson.isBlank()) return out
                val blockRegex = Regex("""\{[^}]*\}""")
                val fileRegex = Regex(""""file"\s*:\s*"([^"]+)"""")
                val labelRegex = Regex(""""label"\s*:\s*"([^"]+)"""")
                val kindRegex = Regex(""""kind"\s*:\s*"([^"]+)"""")
                blockRegex.findAll(tracksJson).forEach { match ->
                    val block = match.value
                    val file = fileRegex.find(block)?.groupValues?.get(1)?.replace("\\/", "/")
                    if (file.isNullOrBlank() || !file.startsWith("http")) return@forEach
                    val kind = kindRegex.find(block)?.groupValues?.get(1)?.lowercase(Locale.ROOT)
                    if (kind != null && kind !in setOf("captions", "subtitles")) return@forEach
                    val label = labelRegex.find(block)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
                        ?: "Türkçe"
                    out.add(label to file)
                }
            } catch (e: Exception) {
                Log.w(TAG, "altyazı ayrıştırma hatası: ${e.message}")
            }
            return out
        }

        // ── JWPlayer "dc_" native deşifre ────────────────────────────────────────────────

        /**
         * Embed sayfasındaki JS bloğunu deşifre eder:
         *  1. şifreli dizi parçalarını birleştir,
         *  2. fonksiyon gövgesindeki SIRAYA göre atob / reverse / rot(replace) uygula,
         *  3. modulo unmix ile aç.
         * Başarısızsa null.
         */
        fun decryptNative(html: String): String? {
            try {
                val scriptBlockMatch =
                    Regex("""<script[^>]*>(.*?dc_[a-zA-Z0-9_]+\(.*?</script>)""", RegexOption.DOT_MATCHES_ALL)
                        .find(html)
                val scriptContent = scriptBlockMatch?.groupValues?.get(1) ?: return null

                // 1. Şifreli dizi
                val arrayMatch = Regex("""\(\[((?:"[^"]+",?\s*)+)\]\)""").find(scriptContent)
                val parts = arrayMatch?.groupValues?.get(1)?.split(",")?.map {
                    it.trim().trim('"').replace("\\/", "/")
                } ?: return null

                // 2. Dinamik modulo çarpanları
                val moduloMatch = Regex("""(\d+)\s*%\s*\(i\s*\+\s*(\d+)\)""").find(scriptContent)
                val magicNum = moduloMatch?.groupValues?.get(1)?.toLongOrNull() ?: 399756995L
                val magicOffset = moduloMatch?.groupValues?.get(2)?.toIntOrNull() ?: 5

                // 3. Fonksiyon gövdesi
                val funcStartIdx = scriptContent.indexOf("function dc_")
                if (funcStartIdx == -1) return null
                val funcEndIdx = scriptContent.indexOf("function d1x()", funcStartIdx)
                    .takeIf { it != -1 } ?: scriptContent.length
                val functionBody = scriptContent.substring(funcStartIdx, funcEndIdx)

                // 4. Dinamik ROT kaydırma değeri
                var rotShift = 13
                val rotShiftMatch = Regex("""charCodeAt\(0\)\s*\+\s*(\d+)""").find(functionBody)
                if (rotShiftMatch != null) {
                    rotShift = rotShiftMatch.groupValues[1].toInt()
                } else {
                    val alt = Regex("""o\s*-\s*base\s*([+-])\s*(\d+)""").find(functionBody)
                    if (alt != null) {
                        val sign = alt.groupValues[1]
                        val num = alt.groupValues[2].toInt()
                        rotShift = if (sign == "-") (26 - num) % 26 else num
                    }
                }

                // İşlem sırasını siteden oku
                val operations = mutableListOf<Pair<Int, String>>()
                var index = functionBody.indexOf("atob(")
                while (index >= 0) {
                    operations.add(index to "atob")
                    index = functionBody.indexOf("atob(", index + 1)
                }
                index = functionBody.indexOf("reverse")
                while (index >= 0) {
                    operations.add(index to "reverse")
                    index = functionBody.indexOf("reverse", index + 1)
                }
                index = functionBody.indexOf("replace")
                while (index >= 0) {
                    operations.add(index to "rot")
                    index = functionBody.indexOf("replace", index + 1)
                }
                operations.sortBy { it.first }

                var result = parts.joinToString("")

                for ((_, op) in operations) {
                    when (op) {
                        "reverse" -> result = result.reversed()
                        "atob" -> {
                            var padded = result.replace('-', '+').replace('_', '/')
                            while (padded.length % 4 != 0) padded += "="
                            result = try {
                                String(java.util.Base64.getDecoder().decode(padded), Charsets.ISO_8859_1)
                            } catch (_: Exception) {
                                String(java.util.Base64.getUrlDecoder().decode(padded), Charsets.ISO_8859_1)
                            }
                        }
                        "rot" -> {
                            val rot = StringBuilder()
                            for (c in result) {
                                val shifted = when (c) {
                                    in 'a'..'z' -> c.code + rotShift
                                    in 'A'..'Z' -> c.code + rotShift
                                    else -> -1
                                }
                                if (shifted == -1) {
                                    rot.append(c)
                                } else {
                                    var ch = shifted
                                    val base = if (c in 'a'..'z') 'z'.code else 'Z'.code
                                    if (ch > base) ch -= 26
                                    rot.append(ch.toChar())
                                }
                            }
                            result = rot.toString()
                        }
                    }
                }

                // Son adım: modulo unmix
                val unmix = StringBuilder()
                for (i in result.indices) {
                    val charCode = result[i].code.toLong()
                    val decryptedCode = (charCode - (magicNum % (i + magicOffset)) + 256) % 256
                    unmix.append(decryptedCode.toInt().toChar())
                }
                return unmix.toString()
            } catch (e: Exception) {
                Log.e(TAG, "native çözümleme hatası: ${e.message}")
                return null
            }
        }
    }
}