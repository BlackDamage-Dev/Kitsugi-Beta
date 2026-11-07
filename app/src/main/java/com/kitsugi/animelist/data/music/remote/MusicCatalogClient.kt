package com.kitsugi.animelist.data.music.remote

import android.util.Log
import com.kitsugi.animelist.data.music.AmbientOrigin
import com.kitsugi.animelist.data.music.AmbientSound
import com.kitsugi.animelist.data.music.MoodProfile
import com.kitsugi.animelist.data.music.MusicSourceId
import com.kitsugi.animelist.data.music.SoundFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 🌐 Uzak Ses Kataloğu İstemcisi
 *
 * Tümü **kişisel API anahtarı** ile çalışır. Uygulama hiçbir ortak
 * anahtar taşımaz; bu sayede ticari/telif kısıtları oluşmaz ve
 * kullanıcının kotası kendi hesabından işler.
 *
 * Desteklenen kaynaklar: Jamendo, Freesound, Openverse (+ anahtarsız
 * Openverse & Internet Archive).
 *
 * Tüm istekler `runCatching` ile sarılır — ağ hataları çalma
 * deneyimini ASLA bozmaz, yerleşik prosedürel sesler her zaman çalışır.
 */
class MusicCatalogClient(
    private val okHttpClient: OkHttpClient = defaultClient(),
    /**
     * Test/teşhis kancası: verilirse ağ isteği yerine bu fonksiyon
     * kullanılır. Üretimde `null` — davranış değişmez.
     */
    private val httpFetcher: ((Request) -> String?)? = null
) {

    companion object {
        private const val TAG = "MusicCatalogClient"

        /** Anahtar/kota sınırlarını aşmamak için sayfa boyu. */
        private const val PAGE_SIZE = 20

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Jamendo
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Jamendo'da enstrümantal, sakin parça arar.
     *
     * @param clientId Kullanıcının kendi Jamendo Client ID'si
     * @param tags     Duyguya göre etiketler ("ambient", "chill", "piano" …)
     */
    suspend fun searchJamendo(
        clientId: String,
        tags: List<String> = listOf("ambient", "chill"),
        limit: Int = PAGE_SIZE
    ): List<AmbientSound> = withContext(Dispatchers.IO) {
        if (clientId.isBlank()) return@withContext emptyList()

        val url = "https://api.jamendo.com/v3.0/tracks/".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("client_id", clientId)
            ?.addQueryParameter("format", "json")
            ?.addQueryParameter("limit", limit.toString())
            ?.addQueryParameter("fuzzytags", tags.joinToString("+"))
            ?.addQueryParameter("vocalinstrumental", "instrumental")
            ?.addQueryParameter("audioformat", "mp32")
            ?.addQueryParameter("include", "musicinfo")
            ?.addQueryParameter("boost", "popularity_month")
            ?.build()
            ?: return@withContext emptyList()

        val body = execute(Request.Builder().url(url).get().build())
            ?: return@withContext emptyList()

        runCatching {
            val root = JSONObject(body)
            val results = root.optJSONArray("results") ?: JSONArray()
            buildList {
                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    val streamUrl = item.optString("audio")
                    if (streamUrl.isBlank()) continue

                    val tagsList = item.optJSONObject("musicinfo")
                        ?.optJSONObject("tags")
                        ?.let { t ->
                            val all = mutableListOf<String>()
                            for (key in listOf("genres", "instruments", "vartags")) {
                                val arr = t.optJSONArray(key) ?: continue
                                for (j in 0 until arr.length()) all += arr.optString(j)
                            }
                            all
                        } ?: emptyList()

                    add(
                        AmbientSound(
                            id = "jamendo_${item.optString("id")}",
                            title = item.optString("name", "Jamendo parçası"),
                            subtitle = item.optString("artist_name", ""),
                            family = SoundFamily.INSTRUMENTAL,
                            origin = AmbientOrigin.REMOTE,
                            mood = moodFromTags(tagsList),
                            streamUrl = streamUrl,
                            durationMs = (item.optLong("duration", 0L)) * 1000L,
                            attribution = "“${item.optString("name")}” — ${item.optString("artist_name")} (Jamendo)",
                            licenseUrl = item.optString("license_ccurl", ""),
                            pageUrl = item.optString("shareurl", ""),
                            sourceId = MusicSourceId.JAMENDO,
                            varietyKey = "jamendo_${item.optString("artist_id")}"
                        )
                    )
                }
            }
        }.getOrElse {
            Log.w(TAG, "Jamendo yanıtı ayrıştırılamadı", it)
            emptyList()
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Freesound
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Freesound'da ortam kaydı arar.
     *
     * Not: Yalnızca `previews` alanı kullanılır — bu, API şartlarına
     * uygun biçimde önizleme akışıdır (orijinal dosya indirilmez).
     */
    suspend fun searchFreesound(
        apiKey: String,
        query: String = "ambience",
        limit: Int = PAGE_SIZE
    ): List<AmbientSound> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()

        val url = "https://freesound.org/apiv2/search/text/".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("query", query)
            ?.addQueryParameter("fields", "id,name,previews,username,license,url,duration,tags")
            ?.addQueryParameter("page_size", limit.toString())
            ?.addQueryParameter("filter", "duration:[30 TO 600]")
            ?.build()
            ?: return@withContext emptyList()

        val body = execute(
            Request.Builder()
                .url(url)
                .header("Authorization", "Token $apiKey")
                .get()
                .build()
        ) ?: return@withContext emptyList()

        runCatching {
            val root = JSONObject(body)
            val results = root.optJSONArray("results") ?: JSONArray()
            buildList {
                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    val previews = item.optJSONObject("previews") ?: continue
                    // Yüksek kaliteli önizleme varsa onu kullan
                    val streamUrl = previews.optString("preview-hq-mp3")
                        .ifBlank { previews.optString("preview-lq-mp3") }
                    if (streamUrl.isBlank()) continue

                    val tagList = mutableListOf<String>()
                    item.optJSONArray("tags")?.let { arr ->
                        for (j in 0 until arr.length()) tagList += arr.optString(j)
                    }

                    add(
                        AmbientSound(
                            id = "freesound_${item.optString("id")}",
                            title = item.optString("name", "Freesound kaydı"),
                            subtitle = item.optString("username", ""),
                            family = familyFromTags(tagList),
                            origin = AmbientOrigin.REMOTE,
                            mood = moodFromTags(tagList),
                            streamUrl = streamUrl,
                            durationMs = (item.optDouble("duration", 0.0) * 1000).toLong(),
                            attribution = buildString {
                                append("“${item.optString("name")}” — ${item.optString("username")} (Freesound)")
                                val lic = item.optString("license", "")
                                if (lic.isNotBlank()) append(" · $lic")
                            },
                            // Freesound `license` alanı lisans ADI döndürür (URL değil),
                            // bu yüzden licenseUrl boş bırakılıp ada attribution'a eklenir.
                            licenseUrl = "",
                            pageUrl = item.optString("url", ""),
                            sourceId = MusicSourceId.FREESOUND,
                            varietyKey = "freesound_${item.optString("username")}"
                        )
                    )
                }
            }
        }.getOrElse {
            Log.w(TAG, "Freesound yanıtı ayrıştırılamadı", it)
            emptyList()
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Openverse (anahtarsız!)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Openverse'te CC0/CC-BY açık lisanslı ses arar.
     *
     * Anahtarsız çalışır (20 istek/dk, 200 istek/gün anonim limit).
     * Kullanıcı kendi token'ını girerse limit yükselir.
     *
     * @param accessToken Opsiyonel OAuth token (kullanıcının kendi)
     * @param query       Arama metni
     * @param cc0Only     Yalnızca CC0 (atıf gerektirmez)
     */
    suspend fun searchOpenverse(
        accessToken: String = "",
        query: String = "ambient",
        cc0Only: Boolean = true,
        limit: Int = PAGE_SIZE
    ): List<AmbientSound> = withContext(Dispatchers.IO) {
        val urlBuilder = "https://api.openverse.org/v1/audio/".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("q", query)
            ?.addQueryParameter("page_size", limit.coerceIn(1, 20).toString())
            ?: return@withContext emptyList()

        if (cc0Only) {
            urlBuilder.addQueryParameter("license", "cc0")
        } else {
            urlBuilder.addQueryParameter("license", "cc0,by")
        }

        val requestBuilder = Request.Builder().url(urlBuilder.build()).get()
        if (accessToken.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $accessToken")
        }

        val body = execute(requestBuilder.build()) ?: return@withContext emptyList()

        runCatching {
            val root = JSONObject(body)
            val results = root.optJSONArray("results") ?: JSONArray()
            buildList {
                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    val streamUrl = item.optString("url")
                    if (streamUrl.isBlank()) continue
                    if (item.optBoolean("mature", false)) continue

                    val tagList = mutableListOf<String>()
                    item.optJSONArray("tags")?.let { arr ->
                        for (j in 0 until arr.length()) {
                            // Openverse `tags` alanı sürüme göre nesne
                            // ({name, accuracy}) ya da düz metin döndürür.
                            val entry = arr.opt(j)
                            when (entry) {
                                is JSONObject -> entry.optString("name")
                                    .takeIf { it.isNotBlank() }?.let { tagList += it }
                                is String -> entry.takeIf { it.isNotBlank() }?.let { tagList += it }
                            }
                        }
                    }
                    // Tür ipucu olarak başlığı da ekle: etiketsiz kayıtlarda
                    // duygu profili nötr kalmasın.
                    item.optString("title").takeIf { it.isNotBlank() }?.let { tagList += it }

                    add(
                        AmbientSound(
                            id = "openverse_${item.optString("id")}",
                            title = item.optString("title", "Openverse kaydı"),
                            subtitle = item.optString("creator", ""),
                            family = familyFromTags(tagList),
                            origin = AmbientOrigin.REMOTE,
                            mood = moodFromTags(tagList),
                            streamUrl = streamUrl,
                            durationMs = item.optLong("duration", 0L),
                            // Openverse hazır atıf metni verir — aynen kullanılır
                            attribution = item.optString("attribution", ""),
                            licenseUrl = item.optString("license_url", ""),
                            pageUrl = item.optString("foreign_landing_url", ""),
                            sourceId = MusicSourceId.OPENVERSE,
                            varietyKey = "openverse_${item.optString("creator")}"
                        )
                    )
                }
            }
        }.getOrElse {
            Log.w(TAG, "Openverse yanıtı ayrıştırılamadı", it)
            emptyList()
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Internet Archive (anahtarsız, public domain)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Internet Archive'da public domain ortam kaydı arar.
     * Yalnızca `licenseurl` içinde `publicdomain` geçen öğeler listelenir.
     */

    /**
     * 🔎 Audius araması
     *
     * Audius anahtarsız da çalışır (`app_name` yeterli); anahtar verilirse
     * kota yükselir. Bu yüzden `apiKey` boşken de arama yapılır — kullanıcı
     * anahtar girmeden de kaynağı kullanabilsin.
     *
     * Akış adresi `stream` endpoint'inden gelir; parça kimliği ile
     * birleştirilir: `https://api.audius.co/v1/tracks/{id}/stream?app_name=`
     */
    suspend fun searchAudius(
        apiKey: String = "",
        query: String = "ambient",
        limit: Int = PAGE_SIZE
    ): List<AmbientSound> = withContext(Dispatchers.IO) {
        val url = "https://api.audius.co/v1/tracks/search".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("query", query)
            ?.addQueryParameter("app_name", "Kitsugi")
            ?.addQueryParameter("limit", limit.coerceIn(1, 30).toString())
            ?.build() ?: return@withContext emptyList()

        val requestBuilder = Request.Builder().url(url).get()
        // Audius anahtarı `x-api-key` başlığıyla gönderilir (Bearer değil).
        // Anahtarsız da çalışır; yalnızca kota yükselir.
        if (apiKey.isNotBlank()) {
            requestBuilder.header("x-api-key", apiKey)
        }

        val body = execute(requestBuilder.build()) ?: return@withContext emptyList()

        runCatching {
            val data = JSONObject(body).optJSONArray("data") ?: JSONArray()
            buildList {
                for (i in 0 until data.length()) {
                    val track = data.optJSONObject(i) ?: continue
                    val id = track.optString("id")
                    if (id.isBlank()) continue

                    // 🔇 Çalınamayan parçaları listeye hiç almayalım.
                    //
                    // Audius'ta `track_cid` boş olan ya da `is_streamable`
                    // false olan parçalar `/stream` ucundan 302 yerine
                    // `{"code":404,"error":"track audio is unavailable"}`
                    // döndürür. Kullanıcı parçaya dokunup sessiz bir hata
                    // görüyordu. Silinmiş/gizlenmiş (is_delete,
                    // is_available) ve akışa kapalı (access.stream) kayıtlar
                    // da aynı sonucu verir.
                    if (!track.optBoolean("is_streamable", true)) continue
                    if (track.optBoolean("is_delete", false)) continue
                    if (!track.optBoolean("is_available", true)) continue
                    if (track.optJSONObject("access")?.optBoolean("stream", true) == false) continue
                    if (track.optString("track_cid").isBlank()) continue

                    val title = track.optString("title").ifBlank { "Audius parçası" }
                    val artist = track.optJSONObject("user")?.optString("name").orEmpty()
                    val genre = track.optString("genre").orEmpty()
                    val moodTag = track.optString("mood").orEmpty()

                    // Audius akışı yönlendirme yapar; ExoPlayer bunu takip eder
                    val streamUrl = "https://api.audius.co/v1/tracks/$id/stream" +
                        "?app_name=Kitsugi"

                    add(
                        AmbientSound(
                            id = "audius_$id",
                            title = title,
                            subtitle = listOf(artist, genre).filter { it.isNotBlank() }
                                .joinToString(" · "),
                            family = familyFromTags(listOf(genre, moodTag)),
                            origin = AmbientOrigin.REMOTE,
                            mood = moodFromTags(listOf(genre, moodTag)),
                            streamUrl = streamUrl,
                            // ✅ Audius v1 `duration` alanı **saniye**
                            // döndürür (ör. 509 → 8:29). Milisaniyeye
                            // çevrilmesi şart; `/v1/tracks/search`
                            // yanıtıyla doğrulandı.
                            durationMs = track.optLong("duration", 0L) * 1000L,
                            attribution = "Audius · $artist",
                            licenseUrl = "https://audius.co/",
                            pageUrl = "https://audius.co/tracks/$id",
                            sourceId = MusicSourceId.AUDIUS,
                            varietyKey = "audius"
                        )
                    )
                }
            }
        }.getOrElse {
            Log.w(TAG, "Audius yanıtı çözümlenemedi", it)
            emptyList()
        }
    }

    suspend fun searchArchive(
        query: String = "rain ambience",
        limit: Int = PAGE_SIZE
    ): List<AmbientSound> = withContext(Dispatchers.IO) {
        // Kademeli daralma — her adım bir öncekinden daha gevşek:
        //   1) ses + topluluk ses arşivi + public domain (en güvenli)
        //   2) ses + topluluk ses arşivi (lisans alanı boş olan CC
        //      kayıtlarını da yakalar)
        //   3) yalnızca ses (niş sorgularda sonuç bırakmamak için)
        // Gerçek API yanıtıyla doğrulandı: 1. adım "rain ambience" için
        // 57 sonuç veriyor.
        archiveQuery(query, limit, scope = ArchiveScope.PUBLIC_DOMAIN).ifEmpty {
            archiveQuery(query, limit, scope = ArchiveScope.OPEN_AUDIO_COLLECTION).ifEmpty {
                archiveQuery(query, limit, scope = ArchiveScope.AUDIO_ONLY)
            }
        }
    }

    /** Archive sorgusunun daralma kademesi. */
    private enum class ArchiveScope {
        PUBLIC_DOMAIN,
        OPEN_AUDIO_COLLECTION,
        AUDIO_ONLY
    }

    /**
     * Archive advancedsearch sorgusu.
     *
     * Önemli ayrıntılar:
     *  - `mediatype:(audio)` → video/PDF sonuçları elenir
     *  - `sort[]=downloads+desc` → dinlenme sayısı yüksek, gerçekten
     *    çalınabilir öğeler önce gelir (varsayılan sıralama neredeyse
     *    rastgeleydi ve çoğu zaman sessiz/bozuk dosyalar geliyordu)
     *  - `collection:opensource_audio` → topluluk ses arşivi; kapalı
     *    (dark) öğelerin çıkma olasılığı düşer
     */
    private suspend fun archiveQuery(
        query: String,
        limit: Int,
        scope: ArchiveScope
    ): List<AmbientSound> {
        val fullQuery = buildString {
            append("mediatype:(audio)")
            when (scope) {
                ArchiveScope.PUBLIC_DOMAIN -> append(" AND collection:(opensource_audio)")
                    .append(" AND licenseurl:*publicdomain*")
                ArchiveScope.OPEN_AUDIO_COLLECTION -> append(" AND collection:(opensource_audio)")
                ArchiveScope.AUDIO_ONLY -> Unit
            }
            append(" AND (").append(query).append(")")
        }
        val url = "https://archive.org/advancedsearch.php".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("q", fullQuery)
            ?.addQueryParameter("fl[]", "identifier")
            ?.addQueryParameter("fl[]", "title")
            ?.addQueryParameter("fl[]", "licenseurl")
            ?.addQueryParameter("fl[]", "creator")
            ?.addQueryParameter("fl[]", "subject")
            ?.addQueryParameter("fl[]", "downloads")
            ?.addQueryParameter("rows", limit.coerceIn(1, 50).toString())
            ?.addQueryParameter("sort[]", "downloads desc")
            ?.addQueryParameter("output", "json")
            ?.build()
            ?: return emptyList()

        val body = execute(Request.Builder().url(url).get().build())
            ?: return emptyList()

        return runCatching {
            val root = JSONObject(body)
            val docs = root.optJSONObject("response")?.optJSONArray("docs") ?: JSONArray()
            buildList {
                for (i in 0 until docs.length()) {
                    val item = docs.optJSONObject(i) ?: continue
                    val identifier = item.optString("identifier")
                    if (identifier.isBlank()) continue

                    // `subject` alanı dizi ya da metin olabilir. Dizi hâlinde
                    // `optString` JSON metnini ("[\"rain\",\"nature\"]")
                    // döndürürdü; etiketleri düzgün birleştiriyoruz.
                    val subjectText = when (val rawSubject = item.opt("subject")) {
                        is JSONArray -> buildList {
                            for (j in 0 until rawSubject.length()) {
                                rawSubject.optString(j).takeIf { it.isNotBlank() }?.let { add(it) }
                            }
                        }.joinToString(" ")
                        is String -> rawSubject
                        else -> ""
                    }

                    // Akış adresi: /download/<id>/ üzerinden ilk dosya
                    // Not: kesin dosya adı metadata çağrısı gerektirir;
                    // pratikte çalma sırasında 302 yönlendirme çalışır.
                    add(
                        AmbientSound(
                            id = "archive_$identifier",
                            title = item.optString("title", identifier),
                            subtitle = item.optString("creator", "Internet Archive"),
                            family = familyFromTags(
                                listOf(identifier, item.optString("title"), subjectText)
                            ),
                            origin = AmbientOrigin.REMOTE,
                            mood = moodFromTags(listOf(item.optString("title"), subjectText)),
                            // Not: kesin dosya adı metadata çağrısı gerektirir.
                            // resolveArchiveTrackUrl() çalma anında çözer.
                            streamUrl = null,
                            attribution = "“${item.optString("title")}” — Internet Archive (public domain)",
                            licenseUrl = item.optString("licenseurl", ""),
                            pageUrl = "https://archive.org/details/$identifier",
                            sourceId = MusicSourceId.ARCHIVE,
                            varietyKey = "archive_$identifier"
                        )
                    )
                }
            }
        }.getOrElse {
            Log.w(TAG, "Internet Archive yanıtı ayrıştırılamadı", it)
            emptyList()
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Internet Archive — dosya adı çözümleme
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Internet Archive öğesinin gerçek ses dosyası adresini çözer.
     *
     * Archive search API yalnızca öğe kimliği döndürür; oynatılabilir
     * dosya adı `/metadata/<id>` çağrısından gelir. Bu yüzden çalma
     * anında bu yardımcı çağrılır.
     *
     * @return Oynatılabilir MP3/OGG adresi, bulunamazsa `null`
     */
    suspend fun resolveArchiveTrackUrl(identifier: String): String? =
        withContext(Dispatchers.IO) {
            if (identifier.isBlank()) return@withContext null
            val url = "https://archive.org/metadata/$identifier".toHttpUrlOrNull()
                ?: return@withContext null
            val body = execute(Request.Builder().url(url).get().build())
                ?: return@withContext null

            runCatching {
                val root = JSONObject(body)
                val files = root.optJSONArray("files") ?: return@runCatching null
                // Öncelik: VBR MP3 → MP3 → OGG
                var best: String? = null
                for (i in 0 until files.length()) {
                    val file = files.optJSONObject(i) ?: continue
                    val name = file.optString("name")
                    if (name.isBlank()) continue
                    val format = file.optString("format", "").lowercase()
                    if (!format.contains("mp3") && !format.contains("ogg")) continue
                    // Dosya adında boşluk varsa URL kodla
                    val encoded = java.net.URLEncoder.encode(name, "UTF-8")
                        .replace("+", "%20")
                    val candidate = "https://archive.org/download/$identifier/$encoded"
                    if (format.contains("vbr")) return@runCatching candidate
                    if (best == null) best = candidate
                }
                best
            }.getOrNull()
        }

    // ─────────────────────────────────────────────────────────────────────
    //  Anahtar doğrulama
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Verilen kaynağın anahtarını doğrular. Ağ hatası → `false`.
     * Boş anahtar → `false`.
     */
    suspend fun validateKey(sourceId: MusicSourceId, key: String): Boolean =
        withContext(Dispatchers.IO) {
            if (key.isBlank()) return@withContext false
            when (sourceId) {
                MusicSourceId.JAMENDO -> searchJamendo(key, limit = 1).isNotEmpty()
                MusicSourceId.FREESOUND -> searchFreesound(key, limit = 1).isNotEmpty()
                MusicSourceId.OPENVERSE -> {
                    // Token ile bir arama dene
                    searchOpenverse(accessToken = key, limit = 1).isNotEmpty()
                }
                MusicSourceId.AUDIUS -> validateAudius(key)
                // Anahtarsız kaynaklar her zaman geçerli
                MusicSourceId.BUILTIN, MusicSourceId.ARCHIVE -> true
            }
        }

    private suspend fun validateAudius(key: String): Boolean = withContext(Dispatchers.IO) {
        val url = "https://api.audius.co/v1/tracks/trending".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("app_name", "Kitsugi")
            ?.addQueryParameter("limit", "1")
            ?.build() ?: return@withContext false

        val body = execute(
            Request.Builder().url(url).header("Authorization", "Bearer $key").get().build()
        ) ?: return@withContext false

        runCatching { JSONObject(body).optJSONArray("data")?.length() ?: 0 > 0 }
            .getOrDefault(false)
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Dahili yardımcılar
    // ─────────────────────────────────────────────────────────────────────

    /**
     * İsteği çalıştırır ve gövdeyi döndürür. Tüm hatalar `null`'a döner —
     * çağıran taraf boş listeye çevirir, çalma deneyimi kesintiye uğramaz.
     */
    private fun execute(request: Request): String? = runCatching {
        httpFetcher?.let { return@runCatching it(request) }
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "HTTP ${response.code} — ${request.url.host}")
                return@use null
            }
            response.body?.string()
        }
    }.getOrElse {
        Log.w(TAG, "İstek başarısız: ${request.url.host}", it)
        null
    }

    /**
     * Etiketlerden ses ailesi tahmin eder.
     */
    private fun familyFromTags(tags: List<String?>): SoundFamily {
        val joined = tags.filterNotNull().joinToString(" ").lowercase()
        return when {
            joined.containsAny("rain", "yağmur", "thunder", "storm") -> SoundFamily.WEATHER
            joined.containsAny("fire", "ateş", "campfire", "bonfire") -> SoundFamily.FIRE
            joined.containsAny("water", "ocean", "wave", "stream", "river", "sea") -> SoundFamily.WATER
            joined.containsAny("wind", "rüzgar", "breeze") -> SoundFamily.WIND
            joined.containsAny("forest", "bird", "kuş", "nature", "cricket") -> SoundFamily.FOREST
            joined.containsAny("city", "urban", "traffic", "cafe", "street") -> SoundFamily.URBAN
            joined.containsAny("noise", "gürültü", "white", "pink") -> SoundFamily.NOISE
            joined.containsAny("piano", "guitar", "koto", "instrumental", "music") ->
                SoundFamily.INSTRUMENTAL
            else -> SoundFamily.OTHER
        }
    }

    /**
     * Etiketlerden duygu profili tahmin eder.
     *
     * Not: Uzak katalogdan gelen her ses için gerçek bir duygu analizi
     * yapılmıyor; etiketler kaba bir tahmin sağlıyor. Yerleşik
     * prosedürel sesler bu yüzden her zaman daha iyi sonuç verir.
     */
    private fun moodFromTags(tags: List<String>): MoodProfile {
        val joined = tags.joinToString(" ").lowercase()

        val energy = when {
            joined.containsAny("energetic", "upbeat", "fast", "dance", "rock", "epic") -> 0.85f
            joined.containsAny("calm", "slow", "sleep", "relax", "ambient", "chill") -> 0.22f
            else -> 0.45f
        }
        val valence = when {
            joined.containsAny("happy", "joy", "bright", "uplifting", "hopeful") -> 0.85f
            joined.containsAny("sad", "dark", "melancholy", "somber", "depressing") -> 0.18f
            else -> 0.52f
        }
        val tension = when {
            joined.containsAny("horror", "suspense", "scary", "creepy", "tension") -> 0.90f
            joined.containsAny("relax", "peaceful", "gentle", "serene") -> 0.10f
            else -> 0.35f
        }
        val warmth = when {
            joined.containsAny("acoustic", "warm", "wood", "violin", "piano", "guitar") -> 0.85f
            joined.containsAny("electronic", "synth", "industrial", "cold", "space") -> 0.18f
            else -> 0.50f
        }
        // Uzak kaynaklarda vokal olup olmadığı bilinmiyor → odak cezası
        val focus = when {
            joined.containsAny("vocal", "song", "singing", "lyrics") -> 0.25f
            joined.containsAny("instrumental", "ambient", "noise", "soundscape") -> 0.92f
            else -> 0.65f
        }
        return MoodProfile(energy, valence, tension, warmth, focus)
    }

    private fun String.containsAny(vararg needles: String): Boolean =
        needles.any { this.contains(it) }
}
