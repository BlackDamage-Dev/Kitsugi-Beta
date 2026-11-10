package com.kitsugi.animelist.data.ambient

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * AnimeThemes.moe ses kaynağı — **anime açılış/kapanış şarkılarının kendisi**.
 *
 * Neden bu kaynak?
 * - Doğrudan CDN'den gelen, çözücü/PO-token gerektirmeyen **.ogg ses dosyaları**
 *   (`https://animethemes.moe/audio/Bakemonogatari-OP1.ogg`).
 * - AnimeThemes'in kendi `Audio` kaynağı (API v1, herkese açık, anahtarsız).
 * - Kırılma yüzeyi yok: YouTube player'ı dönse, imza şifrelemesi değişse bile
 *   bu dosyalar etkilenmez. İndirilebilir → çevrimdışı çalışır.
 *
 * Arama akışı:
 * 1. `GET /search?q=<okunan içeriğin adı>` → en iyi eşleşen anime.
 * 2. `GET /anime?filter[slug]=<slug>&include=…videos.audio` → OP/ED listesi.
 * 3. Her video kaydının `audio.link` alanı; yoksa `video.basename` üzerinden
 *    türetilen `…/audio/<basename>.ogg` adresi kullanılır.
 */
internal class AnimeThemesAudioSource(
    private val client: OkHttpClient = ambientAudioHttpClient,
) {

    suspend fun search(query: String, limit: Int = DEFAULT_LIMIT): List<AmbientAudioTrack> =
        withContext(Dispatchers.IO) {
            val term = query.trim().take(MAX_QUERY_LENGTH)
            if (term.isBlank()) return@withContext emptyList()

            val animeSlug = findAnimeSlug(term) ?: return@withContext emptyList()
            fetchThemes(animeSlug, limit)
        }

    // ─────────────────────────────────────────────────────────────────────
    //  1) İsim → anime slug
    // ─────────────────────────────────────────────────────────────────────

    private fun findAnimeSlug(term: String): String? {
        val url = "$API_BASE/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", term)
            .addQueryParameter("page[limit]", SEARCH_LIMIT.toString())
            .addQueryParameter("fields[anime]", "name,slug,year,media_format")
            .build()

        val body = get(url.toString()) ?: return null
        val anime = JSONObject(body)
            .optJSONObject("search")
            ?.optJSONArray("anime")
            ?: return null

        var bestSlug: String? = null
        var bestScore = 0
        for (index in 0 until anime.length()) {
            val item = anime.optJSONObject(index) ?: continue
            val slug = item.optNullableString("slug") ?: continue
            val name = item.optNullableString("name").orEmpty()
            val score = matchScore(term, name)
            if (score > bestScore) {
                bestScore = score
                bestSlug = slug
            }
        }
        // Hiçbir sonuç mantıklı eşleşmiyorsa ilk sonucu yine de kullanmayalım:
        // yanlış anime'nin OP/ED'sini çalmak, sessiz kalmaktan daha kötü.
        return bestSlug?.takeIf { bestScore > 0 }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  2) Slug → OP/ED ses kayıtları
    // ─────────────────────────────────────────────────────────────────────

    private fun fetchThemes(slug: String, limit: Int): List<AmbientAudioTrack> {
        val url = "$API_BASE/anime".toHttpUrl().newBuilder()
            .addQueryParameter("filter[slug]", slug)
            .addQueryParameter(
                "include",
                "animethemes.song.artists,animethemes.animethemeentries.videos.audio"
            )
            .addQueryParameter("page[limit]", "1")
            .build()

        val body = get(url.toString()) ?: return emptyList()
        val animeArr = JSONObject(body).optJSONArray("anime") ?: return emptyList()
        val anime = animeArr.optJSONObject(0) ?: return emptyList()
        val animeName = anime.optNullableString("name").orEmpty()
        val themes = anime.optJSONArray("animethemes") ?: return emptyList()

        val seen = HashSet<String>()
        val tracks = mutableListOf<AmbientAudioTrack>()

        for (i in 0 until themes.length()) {
            if (tracks.size >= limit) break
            val theme = themes.optJSONObject(i) ?: continue
            val label = themeLabel(theme)
            val song = theme.optJSONObject("song")
            val songTitle = song?.optNullableString("title")?.takeIf { it.isNotBlank() }
                ?: continue
            val artists = song.optJSONArray("artists").artistNames()

            val entries = theme.optJSONArray("animethemeentries") ?: continue
            for (e in 0 until entries.length()) {
                val entry = entries.optJSONObject(e) ?: continue
                val videos = entry.optJSONArray("videos") ?: continue
                for (v in 0 until videos.length()) {
                    val video = videos.optJSONObject(v) ?: continue
                    val audioUrl = audioUrlOf(video) ?: continue
                    if (!seen.add(audioUrl)) continue

                    val basename = video.optNullableString("basename")
                        ?: audioUrl.substringAfterLast('/').substringBeforeLast('.')
                    tracks += AmbientAudioTrack(
                        id = "animethemes:$basename",
                        title = if (label.isBlank()) songTitle else "$songTitle ($label)",
                        creator = artists.ifBlank { animeName },
                        streamUrl = audioUrl,
                        landingUrl = "https://animethemes.moe/anime/$slug",
                        provider = PROVIDER,
                        source = PROVIDER,
                        durationMs = null,
                        fileType = "ogg",
                        tags = buildList {
                            if (label.isNotBlank()) add(label)
                            add("anime")
                            add(animeName)
                        }.filter { it.isNotBlank() }.distinct(),
                        // Ses dosyası doğrudan CDN'de → çevrimdışı indirme çalışır.
                        downloadUrl = audioUrl,
                    )
                    break // Aynı tema için tek kayıt yeterli
                }
            }
        }
        return tracks
    }

    /** Video kaydının ses adresi: `audio.link` varsa o, yoksa basename'den türetilir. */
    private fun audioUrlOf(video: JSONObject): String? {
        video.optJSONObject("audio")
            ?.optNullableString("link")
            ?.takeIf { it.startsWith("http") }
            ?.let { return it }

        val basename = video.optNullableString("basename") ?: return null
        if (basename.isBlank()) return null
        return "$AUDIO_BASE/$basename.ogg"
    }

    private fun themeLabel(theme: JSONObject): String {
        val type = theme.optNullableString("type").orEmpty().uppercase()
        val sequence = theme.optInt("sequence", 0)
        if (type.isBlank()) return ""
        return if (sequence > 1) "$type$sequence" else type
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Yardımcılar
    // ─────────────────────────────────────────────────────────────────────

    private fun get(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("AnimeThemes isteği başarısız: HTTP ${response.code}")
            }
            return response.body?.string()?.takeIf { it.isNotBlank() }
        }
    }

    /**
     * 0..100 arası kaba başlık benzerliği.
     *
     * Amaç: "Sousou no Frieren" / "Frieren: Beyond Journey's End" gibi farklı
     * yazımların ilk sonucu yine de yakalaması, ama alakasız bir animeyi
     * (ör. yalnızca "no" kelimesini paylaşan) seçmemesi.
     */
    private fun matchScore(query: String, candidate: String): Int {
        val q = normalize(query)
        val c = normalize(candidate)
        if (q.isBlank() || c.isBlank()) return 0
        if (q == c) return 100
        if (q.contains(c) || c.contains(q)) return 85

        val qWords = q.split(' ').filter { it.length > 2 }.toSet()
        val cWords = c.split(' ').filter { it.length > 2 }.toSet()
        if (qWords.isEmpty() || cWords.isEmpty()) return 0
        val shared = qWords.count { it in cWords }
        if (shared == 0) return 0
        return (shared * 60) / maxOf(qWords.size, cWords.size) + 10
    }

    private fun normalize(value: String): String = value
        .lowercase()
        .replace(Regex("[^a-z0-9ğüşıöç ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun JSONArray?.artistNames(): String {
        if (this == null) return ""
        val names = mutableListOf<String>()
        for (i in 0 until length()) {
            val artist = optJSONObject(i) ?: continue
            val name = artist.optNullableString("name")?.takeIf { it.isNotBlank() } ?: continue
            if (name !in names) names += name
        }
        return names.joinToString(", ")
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val value = optString(key)
        return value.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val API_BASE = "https://api.animethemes.moe"
        const val AUDIO_BASE = "https://animethemes.moe/audio"
        const val PROVIDER = "animethemes"
        const val USER_AGENT = "KitsugiBeta/2.x (Android ambient audio; AnimeThemes API client)"
        const val SEARCH_LIMIT = 5
        const val DEFAULT_LIMIT = 12
        const val MAX_QUERY_LENGTH = 80
    }
}
