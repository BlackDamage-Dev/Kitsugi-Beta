package com.kitsugi.animelist.data.ambient

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * YouTube Music kaynağı — **arama** tarafı.
 *
 * Tasarım kararı (v2.4.482): bu kaynak arama anında **sadece arama** yapar ve
 * `kitsugi-yt://<videoId>` işaretçisi döndürür. Gerçek ses adresi çalma anında
 * çözülür (bkz. `MangaAmbientAudioPlayback` içindeki `ResolvingDataSource`).
 *
 * Neden böyle?
 * - Arama hızlı kalır (tek HTTP isteği); 6 sonuç için 6 ayrı çıkarım yapılmaz.
 * - YouTube'un imza/`n` şifrelemesi çözümü yalnızca gerçekten çalınacak parça
 *   için yapılır; kota ve hız sınırı yemez.
 * - Çıkarım başarısız olursa yalnızca o parça atlanır, kuyruk devam eder.
 *
 * Önemli: bu kaynakta **indirme yoktur** (`downloadUrl = null`); YouTube akış
 * adresleri kısa ömürlü olduğu için çevrimdışı kopya desteklenmez. İndirme,
 * AnimeThemes ve 9 açık lisans kaynağında çalışmaya devam eder.
 */
internal class YouTubeMusicAudioSource(
    private val client: OkHttpClient = ambientAudioHttpClient,
) {

    suspend fun search(query: String, limit: Int = DEFAULT_LIMIT): List<AmbientAudioTrack> =
        withContext(Dispatchers.IO) {
            val term = query.trim().take(MAX_QUERY_LENGTH)
            if (term.isBlank()) return@withContext emptyList()

            val apiKey = innerTubeApiKey() ?: throw IOException("YouTube arama anahtarı alınamadı")
            val payload = JSONObject().apply {
                put(
                    "context",
                    JSONObject().put(
                        "client",
                        JSONObject().apply {
                            put("clientName", "WEB")
                            put("clientVersion", CLIENT_VERSION)
                            put("hl", "en")
                            put("gl", "US")
                        }
                    )
                )
                put("query", term)
            }

            val request = Request.Builder()
                .url("$API_BASE/youtubei/v1/search?key=$apiKey&prettyPrint=false")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // Anahtar bayatlamış olabilir; bir sonraki aramada yeniden okunur.
                    invalidateApiKey()
                    throw IOException("YouTube araması başarısız: HTTP ${response.code}")
                }
                response.body?.string().orEmpty()
            }
            if (body.isBlank()) throw IOException("YouTube boş yanıt döndü")

            val renderers = mutableListOf<JSONObject>()
            collectVideoRenderers(JSONObject(body), renderers)

            // Müzik/OST izlerini öne al: manga adıyla arama, fragman/inceleme
            // videolarını da döndürebiliyor. Sıralama kaynak tarafında yapılır ki
            // panelin "en alakalı" seçimi gerçekten şarkıya gitsin.
            renderers.asSequence()
                .mapNotNull { it.toAmbientTrack() }
                .distinctBy { it.id }
                .sortedByDescending { musicScore(it) }
                .take(limit)
                .toList()
        }

    // ─────────────────────────────────────────────────────────────────────
    //  InnerTube anahtarı (youtube.com ana sayfasından, önbellekli)
    // ─────────────────────────────────────────────────────────────────────

    private fun innerTubeApiKey(): String? {
        cachedApiKey?.let { return it }
        synchronized(this) {
            cachedApiKey?.let { return it }
            val request = Request.Builder()
                .url("https://www.youtube.com/")
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "en-US,en;q=0.9")
                .get()
                .build()
            val html = runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) "" else response.body?.string().orEmpty()
                }
            }.getOrDefault("")

            val key = API_KEY_REGEX.find(html)?.groupValues?.getOrNull(1)
            if (!key.isNullOrBlank()) cachedApiKey = key
            return key?.takeIf { it.isNotBlank() }
        }
    }

    private fun invalidateApiKey() {
        cachedApiKey = null
    }

    // ─────────────────────────────────────────────────────────────────────
    //  JSON ayrıştırma
    // ─────────────────────────────────────────────────────────────────────

    /**
     * YouTube yanıt şeması sık değişiyor; sabit bir yol izlemek yerine ağacı
     * tarayıp `videoRenderer` düğümlerini topluyoruz. Şema değişse bile
     * (ör. `compactVideoRenderer` eklense) sonuç bulunmaya devam eder.
     */
    private fun collectVideoRenderers(node: Any?, target: MutableList<JSONObject>) {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("videoRenderer")?.let(target::add)
                for (key in node.keys()) {
                    collectVideoRenderers(node.opt(key), target)
                }
            }
            is JSONArray -> {
                for (index in 0 until node.length()) {
                    collectVideoRenderers(node.opt(index), target)
                }
            }
            else -> Unit
        }
    }

    private fun JSONObject.toAmbientTrack(): AmbientAudioTrack? {
        val videoId = optNullableString("videoId") ?: return null
        if (videoId.length != 11) return null

        val title = optJSONObject("title").textRuns()
            ?: optNullableString("title")
            ?: return null
        if (title.isBlank()) return null

        // Yaklaşan (henüz yayınlanmamış) yayınlar akış olarak kullanılamaz.
        if (optJSONObject("upcomingEventData") != null) return null

        val durationMs = lengthSecondsMs()
        // 20 dakikadan uzun videolar (mix/derleme) ambiyans kuyruğunu kilitler.
        if (durationMs != null && durationMs > MAX_DURATION_MS) return null

        val creator = optJSONObject("ownerText").textRuns()
            ?: optJSONObject("longBylineText").textRuns()
            ?: optJSONObject("shortBylineText").textRuns()
            ?: "YouTube"

        return AmbientAudioTrack(
            id = "youtube:$videoId",
            title = title,
            creator = creator,
            // Çalma anında gerçek adrese çözülecek işaretçi.
            streamUrl = "$STREAM_SCHEME://$videoId",
            landingUrl = "https://music.youtube.com/watch?v=$videoId",
            provider = PROVIDER,
            source = PROVIDER,
            durationMs = durationMs,
            fileType = "m4a",
            tags = listOf("youtube"),
            downloadUrl = null,
        )
    }


    /** OST/OP/ED/şarkı gibi görünen sonuçları öne alan kaba puan. */
    private fun musicScore(track: AmbientAudioTrack): Int {
        val haystack = (track.title + " " + track.creator).lowercase()
        var score = 0
        if (haystack.contains("ost")) score += 3
        if (haystack.contains("opening") || haystack.contains("ending")) score += 3
        if (OP_ED_REGEX.containsMatchIn(haystack)) score += 3
        if (haystack.contains("theme")) score += 2
        if (haystack.contains("music")) score += 2
        if (haystack.contains("lyric")) score += 1
        if (haystack.contains("- topic")) score += 2
        if (haystack.contains("cover") || haystack.contains("remix")) score += 1
        if (haystack.contains("review") || haystack.contains("reaction") ||
            haystack.contains("trailer") || haystack.contains("explained")
        ) {
            score -= 4
        }
        // Kısa parçalar (OP/ED ~90 sn) ambiyans için daha uygun.
        val duration = track.durationMs ?: 0L
        if (duration in 30_000L..7L * 60L * 1_000L) score += 2
        return score
    }

    private fun JSONObject.textRuns(): String? {
        optJSONArray("runs")?.let { runs ->
            val text = buildString {
                for (index in 0 until runs.length()) {
                    val part = runs.optJSONObject(index)?.optNullableString("text").orEmpty()
                    append(part)
                }
            }
            if (text.isNotBlank()) return text.trim()
        }
        return optNullableString("simpleText")
    }

    private fun JSONObject.lengthSecondsMs(): Long? {
        optNullableString("lengthSeconds")?.toLongOrNull()?.let { return it * 1_000L }
        val text = optJSONObject("lengthText")?.optNullableString("simpleText") ?: return null
        val parts = text.split(':').mapNotNull { it.trim().toLongOrNull() }
        if (parts.isEmpty()) return null
        var seconds = 0L
        for (part in parts) seconds = seconds * 60L + part
        return seconds * 1_000L
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private companion object {
        const val API_BASE = "https://www.youtube.com"
        const val PROVIDER = "youtube"
        const val STREAM_SCHEME = "kitsugi-yt"
        const val CLIENT_VERSION = "2.20241101.00.00"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        const val DEFAULT_LIMIT = 10
        const val MAX_QUERY_LENGTH = 100
        const val MAX_DURATION_MS = 20L * 60L * 1_000L

        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val OP_ED_REGEX = Regex("(^|[^a-z])(op|ed)[ ]?[0-9]")
        val API_KEY_REGEX = Regex("\"INNERTUBE_API_KEY\":\"([^\"]+)\"")

        @Volatile
        var cachedApiKey: String? = null
    }
}
