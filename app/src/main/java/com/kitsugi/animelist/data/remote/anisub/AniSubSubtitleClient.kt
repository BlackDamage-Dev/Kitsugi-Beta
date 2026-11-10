package com.kitsugi.animelist.data.remote.anisub

import android.content.Context
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.kitsugi.animelist.core.network.IPv4FirstDns
import com.kitsugi.animelist.data.remote.SubtitleResponseItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * AniSub.co için Kitsugi'ye özel altyazı istemcisi.
 *
 * NEDEN AYRI İSTEMCİ?
 * AniSub, Stremio protokolü sunsa da paket altyazılarda Stremio indirme
 * ucu (`/stremio/subtitle/{id}`) paketin ilk dosyasını verir; hangi bölüm
 * istenirse istensin hep 1. bölüm gelir. Ayrıca Stremio ucu yalnızca
 * IMDb (tt) kimliğini tanır. Bu istemci:
 *
 *  1. Adayları iki yoldan keşfeder:
 *     a. IMDb varsa resmî Stremio ucundan (`/subtitles/{type}/{id}.json`)
 *     b. Başlıkla resmî JSON API'den (`/api/subtitles?q=...`)
 *        ve AniList kimliğiyle kesin eşleştirir.
 *  2. Her adayı doğru bölüm dosyasına indirger:
 *     a. Önce sunucunun arşiv listesinden (`/api/subtitle/{id}/archive`)
 *        bölüme uyan tek dosyayı bulur, tek-Dosya URL'sini döner
 *        (paketin tamamını indirmeye gerek kalmaz).
 *     b. Arşiv API'sinin açamadığı paketlerde (ör. RAR) paketin tamamını
 *        indirir, cihazda açar ve bölüme uyan dosyayı `file://` olarak döner.
 *     c. Tek dosyalık kayıtlarda CDN indirme adresini doğrudan döner.
 *  3. Yanlış bölümü asla döndürmez: çok dosyalı pakette eşleşme yoksa
 *     o adaydan sonuç üretmez.
 *
 * Eski uygulama içi uygulamanın (Inertia HTML kazıma + yalnızca ZIP açma +
 * oyuncuya `file://` verip SubtitleFileCache'in bunu indiremeyişi) yerini alır.
 */
object AniSubSubtitleClient {

    private const val TAG = "AniSubClient"
    private const val BASE = "https://anisub.co"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private const val MAX_CANDIDATES = 4
    private const val MAX_PACKAGE_BYTES = 80L * 1024 * 1024
    private const val RESULT_CACHE_TTL_MS = 10 * 60 * 1000L

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .dns(IPv4FirstDns())
        .cookieJar(InMemoryCookieJar())
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Oyuncu aynı içerik için paralel 4-6 sorgu atar (tt / kitsu / başlık,
    // movie / series). Hepsi aynı AniSub sonucunu ister; tek iş yapıp paylaşırız.
    private data class CachedResult(val atMs: Long, val items: List<SubtitleResponseItem>)
    private val resultCache = ConcurrentHashMap<String, CachedResult>()
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<List<SubtitleResponseItem>>>()

    // Aynı paketin iki farklı sorguyla aynı anda indirilip açılmasını engeller.
    private val packageExtractionLock = Any()

    // ------------------------------------------------------------ public API

    suspend fun fetchSubtitles(
        context: Context,
        type: String,
        id: String,
        aniListId: Int?,
        animeTitle: String?,
        episode: Int?,
        filenameHint: String? = null,
        animeTitles: List<String> = emptyList()
    ): List<SubtitleResponseItem> {
        val request = ParsedRequest.parse(
            type = type,
            id = id,
            aniListId = aniListId,
            animeTitle = animeTitle,
            episodeParam = episode,
            filenameHint = filenameHint,
            extraTitles = animeTitles
        )
        val key = request.cacheKey()

        resultCache[key]?.let { cached ->
            if (System.currentTimeMillis() - cached.atMs < RESULT_CACHE_TTL_MS) {
                Log.d(TAG, "AniSub sonuç bellek önbelleği: ${cached.items.size} altyazı ($key)")
                return cached.items
            }
            resultCache.remove(key)
        }

        val mine = CompletableDeferred<List<SubtitleResponseItem>>()
        val existing = inFlight.putIfAbsent(key, mine)
        if (existing != null) {
            Log.d(TAG, "AniSub isteği uçuşta — aynı sonuç bekleniyor ($key)")
            return existing.await()
        }

        return try {
            val result = withContext(Dispatchers.IO) { doFetch(context.applicationContext, request) }
            if (result.isNotEmpty()) {
                resultCache[key] = CachedResult(System.currentTimeMillis(), result)
            }
            mine.complete(result)
            result
        } catch (cancel: kotlinx.coroutines.CancellationException) {
            mine.cancel(cancel)
            throw cancel
        } catch (e: Exception) {
            Log.e(TAG, "AniSub altyazı akışı başarısız: ${e.message}", e)
            mine.complete(emptyList())
            emptyList()
        } finally {
            inFlight.remove(key, mine)
        }
    }

    // ------------------------------------------------------------ akış

    private fun doFetch(context: Context, request: ParsedRequest): List<SubtitleResponseItem> {
        Log.d(
            TAG,
            "AniSub arama: imdb=${request.imdbId} aniList=${request.aniListId} " +
                "title=${request.title} season=${request.season} episode=${request.episode} movie=${request.movieIntent}"
        )

        val byId = LinkedHashMap<Long, Candidate>()

        // 1a) IMDb varsa resmî Stremio ucundan keşif
        request.imdbId?.let { imdb ->
            discoverViaStremio(imdb, request).forEach { candidate ->
                byId.merge(candidate)
            }
        }

        // 1b) Başlıkla API araması (Stremio yalnızca tt tanır; kitsu/anilist
        //     yolundan oynatılan animeler için zorunlu)
        if (request.candidateTitles.isNotEmpty()) {
            discoverViaApiSearch(request).forEach { candidate ->
                byId.merge(candidate)
            }
        }

        if (byId.isEmpty()) {
            Log.w(TAG, "AniSub: hiçbir aday bulunamadı")
            return emptyList()
        }

        // Stremio adaylarında meta eksiktir: AniList kimliğine sahipsek sezon
        // doğruluğunu sağlamak için detaylardan tamamla (Stremio ucu bölümü
        // yok sayıp 1. sezon paketini 2. sezona da önerebiliyor).
        if (request.aniListId != null) {
            // Yalnızca Stremio'dan gelen (metasız) adaylar icin detay cekilir;
            // API aramasindan gelenler AniList kimligini zaten tasir. Boylece
            // baslik aramasinin alakasiz sonuclari icin fazladan ag istegi atilmaz.
            byId.values
                .filter { it.anilistId == null && it.discoveredViaStremio }
                .forEach { candidate ->
                    fetchDetail(candidate.subtitleId)?.let { candidate.absorb(it) }
                }
        }

        var candidates = byId.values.toList()

        // 2) Kimlik filtresi: AniList tam eşleşmesi > başlık eşleşmesi > hepsi
        if (request.aniListId != null) {
            val exact = candidates.filter { it.anilistId == request.aniListId }
            if (exact.isNotEmpty()) {
                candidates = exact
            } else if (request.candidateTitles.isNotEmpty()) {
                val byTitle = candidates.filter { cand ->
                    request.candidateTitles.any { t ->
                        AniSubEpisodeMatcher.titleMatches(t, cand.romaji, cand.english)
                    }
                }
                if (byTitle.isNotEmpty()) candidates = byTitle
            }
        } else if (request.candidateTitles.isNotEmpty()) {
            val byTitle = candidates.filter { cand ->
                request.candidateTitles.any { t ->
                    AniSubEpisodeMatcher.titleMatches(t, cand.romaji, cand.english)
                }
            }
            // Başlık bilgisi eksik adaylar (Stremio) başlıktan elenemez; bilgi
            // taşıyan ve tutmayanları ele, taşımayanları tut.
            if (byTitle.isNotEmpty()) {
                candidates = candidates.filter { it in byTitle || (it.romaji == null && it.english == null) }
            }
        }

        // 3) Bölüm uyumluluğu (Paketler Dosya düzeyinde tekrar doğrulanır;
        //    tek bölümlük yanlış kayıtları daha indirmeden elemek için)
        candidates = candidates.filter { candidate ->
            AniSubEpisodeMatcher.isReleaseCompatibleWithEpisode(
                contentType = candidate.contentType,
                episodeDescription = candidate.episodeDescription,
                releaseName = candidate.releaseName,
                episode = request.effectiveEpisodeFor(candidate),
                season = request.season
            )
        }
        if (candidates.isEmpty()) {
            Log.w(TAG, "AniSub: adaylar bölüm filtresinde elendi (ep=${request.episode})")
            return emptyList()
        }

        // 4) Dil: Türkçe aday varsa yalnızca Türkçe; yoksa diğer diller de
        //    doğru dil etiketiyle sunulur (oyuncu tercihi eler).
        val turkish = candidates.filter { isTurkish(it.languageRaw) }
        if (turkish.isNotEmpty()) candidates = turkish

        // 5) Sırala ve en iyi adayları çöz
        val sorted = candidates.sortedWith(
            compareByDescending<Candidate> { isTurkish(it.languageRaw) }
                .thenByDescending { it.anilistId != null && it.anilistId == request.aniListId }
                .thenByDescending { filenameAffinity(request.filenameHint, it.releaseName) }
                .thenBy { it.isAiTranslated }
                .thenByDescending { it.thanks * 10 + it.downloads }
        ).take(MAX_CANDIDATES)

        Log.d(TAG, "AniSub: ${sorted.size} aday çözülüyor: ${sorted.map { it.subtitleId }}")

        val results = mutableListOf<SubtitleResponseItem>()
        for (candidate in sorted) {
            try {
                resolveCandidate(context, candidate, request)?.let { results += it }
            } catch (e: Exception) {
                Log.w(TAG, "AniSub aday çözülemedi id=${candidate.subtitleId}: ${e.message}")
            }
            // Ekrana hızlı ilk sonucu vermek yerine hepsini topla; SubtitleRepository
            // zaten addon başına timeout uygular ve sonuçlar bellek önbelleğe gider.
        }
        Log.d(TAG, "AniSub: ${results.size} altyazı üretildi")
        return results
    }

    // ------------------------------------------------------------ keşif: Stremio

    private fun discoverViaStremio(imdbId: String, request: ParsedRequest): List<Candidate> {
        val variants = LinkedHashSet<String>()
        val ep = request.episode
        val season = request.season
        if (ep != null && ep > 0) {
            variants += "$BASE/subtitles/series/$imdbId:${season ?: 1}:$ep.json"
        }
        if (request.movieIntent) variants += "$BASE/subtitles/movie/$imdbId.json"
        if (ep == null || ep <= 0) variants += "$BASE/subtitles/series/$imdbId.json"
        if (!request.movieIntent && ep == null) variants += "$BASE/subtitles/movie/$imdbId.json"

        val found = LinkedHashMap<Long, Candidate>()
        for (url in variants) {
            val json = httpGetJson(url) ?: continue
            val arr = json.arr("subtitles") ?: continue
            for (el in arr) {
                val obj = el.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                val downloadUrl = obj.str("url")
                val subtitleId = downloadUrl?.let { parseSubtitleIdFromStremioUrl(it) }
                    ?: obj.str("id")?.let { Regex("""(\d+)""").find(it)?.groupValues?.get(1)?.toLongOrNull() }
                    ?: continue
                if (subtitleId in found) continue
                found[subtitleId] = Candidate(
                    subtitleId = subtitleId,
                    languageRaw = obj.str("lang"),
                    formatHint = obj.str("format")?.lowercase(Locale.ROOT),
                    releaseName = obj.str("releaseInfo"),
                    discoveredViaStremio = true
                )
            }
            if (found.isNotEmpty()) break // ilk dolu varyant yeter (server bölümü yok sayıyor)
        }
        Log.d(TAG, "AniSub/Stremio keşif ($imdbId): ${found.size} aday")
        return found.values.toList()
    }

    // ------------------------------------------------------------ keşif: API araması

    private fun discoverViaApiSearch(request: ParsedRequest): List<Candidate> {
        val titleHits = LinkedHashMap<Long, Candidate>()
        val variants = request.candidateTitles
            .flatMap { AniSubEpisodeMatcher.searchQueryVariants(it) }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .take(4)
        for (variant in variants) {
            val items = searchApi(variant, request)
            if (items.isEmpty()) continue

            if (request.aniListId != null) {
                val exact = items.filter { it.anilistId == request.aniListId }
                if (exact.isNotEmpty()) return items // filtre ana akışta
            }
            items.forEach { titleHits.merge(it) }
        }
        Log.d(TAG, "AniSub/API keşif: ${titleHits.size} aday")
        return titleHits.values.toList()
    }

    /** `/api/subtitles?q=` için gereken sayfaları çeker (en çok 2 sayfa x 50). */
    private fun searchApi(query: String, request: ParsedRequest): List<Candidate> {
        val collected = LinkedHashMap<Long, Candidate>()
        var page = 1
        var lastPage = 1
        while (page <= 2 && page <= lastPage) {
            val url = "$BASE/api/subtitles?q=${URLEncoder.encode(query, "UTF-8")}&per_page=50&page=$page"
            val json = httpGetJson(url) ?: break
            val (items, lp) = parseSearchPage(json)
            items.forEach { collected.merge(it) }
            if (lp != null) lastPage = lp
            // Başka sayfaya geçme kararı: AniList eşleşmesi ve bölüm uyumu
            // zaten sağlandıysa 2. sayfaya gitmek yersiz ağ yükü olur.
            if (request.aniListId != null) {
                val exact = collected.values.filter { it.anilistId == request.aniListId }
                val usable = exact.any { candidate ->
                    AniSubEpisodeMatcher.isReleaseCompatibleWithEpisode(
                        candidate.contentType, candidate.episodeDescription,
                        candidate.releaseName, request.effectiveEpisodeFor(candidate), request.season
                    )
                }
                if (usable) break
            } else if (collected.size >= 20) {
                break
            }
            page++
        }
        return collected.values.toList()
    }

    private fun parseSearchPage(json: JsonObject): Pair<List<Candidate>, Int?> {
        // Beklenen biçim: { success, data: { data: [ ... ], meta: { last_page } } }
        // Alan düzenine katı bağımlı kalmamak için birkaç olası yerleşim denenir.
        val dataObj = json.obj("data")
        val array: JsonArray? = dataObj?.arr("data")
            ?: (json.get("data")?.takeIf { it.isJsonArray }?.asJsonArray)
            ?: json.arr("subtitles")
        val lastPage = dataObj?.obj("meta")?.int("last_page")
            ?: json.obj("meta")?.int("last_page")
            ?: dataObj?.int("last_page")
        if (array == null) return emptyList<Candidate>() to lastPage

        val items = mutableListOf<Candidate>()
        for (el in array) {
            val obj = el.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            parseCandidate(obj)?.let { items += it }
        }
        return items to lastPage
    }

    private fun fetchDetail(subtitleId: Long): Candidate? {
        val json = httpGetJson("$BASE/api/subtitles/$subtitleId") ?: return null
        // { success, data: { data: { ... } } }
        val obj = json.obj("data")?.obj("data") ?: json.obj("data") ?: json
        return parseCandidate(obj)
    }

    private fun parseCandidate(obj: JsonObject): Candidate? {
        val id = obj.long("subtitle_id") ?: obj.long("id") ?: return null
        val media = obj.obj("media_info") ?: obj.obj("mediaInfo")
        return Candidate(
            subtitleId = id,
            languageRaw = obj.str("subtitle_language") ?: obj.str("language"),
            formatHint = obj.str("format")?.lowercase(Locale.ROOT),
            contentType = obj.str("content_type"),
            episodeDescription = obj.str("episode_description"),
            releaseName = obj.str("subtitle_release_name") ?: obj.str("description"),
            anilistId = obj.int("subtitle_anilist_id") ?: media?.int("media_anilist_id"),
            romaji = media?.str("title_romaji"),
            english = media?.str("title_english"),
            mediaFormat = media?.str("format"),
            isAiTranslated = obj.bool("is_ai_translated") ?: false,
            downloads = obj.long("downloads_count") ?: obj.long("download_count") ?: 0L,
            thanks = obj.long("thanks_count") ?: 0L
        )
    }

    // ------------------------------------------------------------ aday çözme

    private fun resolveCandidate(
        context: Context,
        candidate: Candidate,
        request: ParsedRequest
    ): SubtitleResponseItem? {
        val effectiveEpisode = request.effectiveEpisodeFor(candidate)
        val subDir = File(context.cacheDir, "anisub_subs/${candidate.subtitleId}")
        val extractedDir = File(subDir, "extracted")
        val completeMarker = File(subDir, ".extract_complete")
        val language = normalizeLanguage(candidate.languageRaw)

        // 0) Daha önce cihazda açılmış paket varsa onu kullan.
        val cachedFiles = listExtractedSubtitles(extractedDir)
        if (cachedFiles.isNotEmpty()) {
            AniSubEpisodeMatcher.pickEpisodeFile(
                cachedFiles.map { it.name }, effectiveEpisode, request.title
            )?.let { pickedName ->
                val file = cachedFiles.first { it.name == pickedName }
                Log.d(TAG, "AniSub[${candidate.subtitleId}] yerel paketten: ${file.name}")
                return itemForLocalFile(candidate, file, language, effectiveEpisode)
            }
            if (completeMarker.exists()) {
                Log.d(TAG, "AniSub[${candidate.subtitleId}] yerel paket bu bölümü içermiyor")
                return null
            }
        }

        // 1) Sunucu arşiv API'si: bölüme uyan tek dosyanın adresini ver.
        //    ZIP paketlerde en hızlı ve en güvenilir yol (indirilecek veri tek Dosya).
        val archiveEntries = fetchArchiveEntries(candidate.subtitleId)
        if (!archiveEntries.isNullOrEmpty()) {
            val pickedPath = AniSubEpisodeMatcher.pickEpisodeFile(
                archiveEntries.map { it.path }, effectiveEpisode, request.title
            )
            if (pickedPath != null) {
                val entry = archiveEntries.first { it.path == pickedPath }
                val fileParam = URLEncoder.encode(entry.path, "UTF-8")
                val url = "$BASE/api/subtitle/${candidate.subtitleId}/archive/download?file=$fileParam"
                Log.d(TAG, "AniSub[${candidate.subtitleId}] arşiv tek dosyası: ${entry.path}")
                return SubtitleResponseItem(
                    id = itemId(candidate.subtitleId, effectiveEpisode, entry.name),
                    url = url,
                    lang = language,
                    format = AniSubEpisodeMatcher.subtitleExtension(entry.name)
                        ?: candidate.formatHint
                )
            }
            // Paket içeriği kesin olarak biliniyor ve bu bölüm yoksa, sunucunun
            // Stremio ucu yanlışlıkla önermiş olsa bile bu aday elenir.
            if (archiveEntries.size > 1 || effectiveEpisode != null) {
                Log.d(TAG, "AniSub[${candidate.subtitleId}] paket içeriğinde ep=$effectiveEpisode yok")
                return null
            }
        }

        // 2) İndirme bilgisi: tek Dosya ise doğrudan, paketse cihazda aç.
        val download = fetchDownloadInfo(candidate.subtitleId)
        if (download != null) {
            val urlExt = SubtitleArchiveExtractor.extensionOf(
                download.url.substringBefore('?').substringAfterLast('/')
            )
            val displayExt = download.filename?.let { SubtitleArchiveExtractor.extensionOf(it) }
            val ext = urlExt.ifBlank { displayExt.orEmpty() }

            if (AniSubEpisodeMatcher.isSubtitleFileName("x.$ext")) {
                // Tek dosyalık altyazı.
                val fileEpisode = AniSubEpisodeMatcher.extractEpisode(
                    download.url.substringAfterLast('/').substringBefore('?'), request.title
                ) ?: download.filename?.let {
                    AniSubEpisodeMatcher.extractEpisode(it, request.title)
                }
                if (effectiveEpisode != null && fileEpisode != null && fileEpisode != effectiveEpisode) {
                    Log.d(TAG, "AniSub[${candidate.subtitleId}] tek Dosya yanlış bölüm (Dosya ep=$fileEpisode)")
                    return null
                }
                Log.d(TAG, "AniSub[${candidate.subtitleId}] tek Dosya doğrudan: ${download.url}")
                return SubtitleResponseItem(
                    id = itemId(candidate.subtitleId, effectiveEpisode, download.filename ?: "subtitle.$ext"),
                    url = download.url,
                    lang = language,
                    format = ext
                )
            }

            // Paket (zip/rar/7z veya uzantısız): indir, türünü baytlardan anla, aç.
            val extracted = synchronized(packageExtractionLock) {
                // Kilit beklenirken başka sorgu paketi açmış olabilir.
                val recheck = listExtractedSubtitles(extractedDir)
                if (recheck.isNotEmpty()) {
                    recheck
                } else {
                    downloadAndExtractPackage(
                        candidate = candidate,
                        download = download,
                        subDir = subDir,
                        extractedDir = extractedDir,
                        completeMarker = completeMarker
                    )
                }
            }
            if (extracted != null) {
                if (extracted.isEmpty()) {
                    Log.w(TAG, "AniSub[${candidate.subtitleId}] paketten altyazı çıkmadı")
                    return null
                }
                val pickedName = AniSubEpisodeMatcher.pickEpisodeFile(
                    extracted.map { it.name }, effectiveEpisode, request.title
                ) ?: run {
                    Log.d(TAG, "AniSub[${candidate.subtitleId}] açık pakette ep=$effectiveEpisode yok")
                    return null
                }
                val file = extracted.first { it.name == pickedName }
                Log.d(TAG, "AniSub[${candidate.subtitleId}] cihazda açıldı: ${file.name}")
                return itemForLocalFile(candidate, file, language, effectiveEpisode)
            }
            // İndirme/açma desteklenmiyorsa (7z vb.) son çare dusulur.
        }

        // 3) Son çare: resmî Stremio tek-Dosya ucu. Sunucu, paketlerde ilk
        //    dosyayı verdiğinden yalnızca film/1. bölüm bağlamında güvenlidir.
        if (effectiveEpisode == null || effectiveEpisode == 1) {
            val format = candidate.formatHint ?: "ass"
            val url = "$BASE/stremio/subtitle/${candidate.subtitleId}?file=subtitle.$format"
            Log.d(TAG, "AniSub[${candidate.subtitleId}] son çare Stremio ucu kullanılıyor")
            return SubtitleResponseItem(
                id = itemId(candidate.subtitleId, effectiveEpisode, "stremio.$format"),
                url = url,
                lang = language,
                format = format
            )
        }
        return null
    }

    /**
     * Paketi indirir ve altyazılarını çıkarır.
     *
     * @return çıkarılan dosyalar; null = indirme başarısız veya arşiv türü
     *         cihazda desteklenmiyor (örn. 7z) — çağıran yedek yollara düşebilir.
     *         Bos liste = arşiv açıldı ama içinde altyazı yok.
     */
    private fun downloadAndExtractPackage(
        candidate: Candidate,
        download: DownloadInfo,
        subDir: File,
        extractedDir: File,
        completeMarker: File
    ): List<File>? {
        if (!subDir.exists()) subDir.mkdirs()
        if (!extractedDir.exists()) extractedDir.mkdirs()

        var packageFile = subDir.listFiles()?.firstOrNull {
            it.isFile && it.name.startsWith("package") && it.length() > 0
        }
        if (packageFile == null) {
            // Gerçek indirme adı korunur; GZIP gibi tek-dosya biçimlerinde iç
            // Dosya adı bu addan türetilir ("...ass.gz" -> "...ass").
            val sourceName = listOfNotNull(
                download.url.substringBefore('?').substringAfterLast('/'),
                download.filename
            ).firstNotNullOfOrNull { SubtitleArchiveExtractor.sanitizeFileName(it) }
            val target = File(subDir, if (sourceName != null) "package-$sourceName" else "package.bin")
            if (!httpDownloadToFile(download.url, target, MAX_PACKAGE_BYTES)) {
                Log.w(TAG, "AniSub[${candidate.subtitleId}] paket indirilemedi")
                return null
            }
            packageFile = target
        }

        return when (SubtitleArchiveExtractor.sniffKind(packageFile)) {
            SubtitleArchiveExtractor.ArchiveKind.PLAIN_TEXT -> {
                // Uzantısı arşiv görünse de içerik düz altyazı olabilir.
                val format = sniffPlainSubtitleFormat(packageFile) ?: candidate.formatHint ?: "srt"
                val targetName = SubtitleArchiveExtractor.sanitizeFileName(
                    download.filename ?: "subtitle.$format"
                )?.takeIf { AniSubEpisodeMatcher.isSubtitleFileName(it) }
                    ?: "subtitle.$format"
                val target = File(extractedDir, targetName)
                packageFile.inputStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                runCatching { completeMarker.writeText("plain") }
                listOf(target)
            }
            SubtitleArchiveExtractor.ArchiveKind.SEVEN_ZIP -> {
                Log.w(TAG, "AniSub[${candidate.subtitleId}] 7z paketi desteklenmiyor")
                null
            }
            SubtitleArchiveExtractor.ArchiveKind.UNKNOWN -> {
                Log.w(TAG, "AniSub[${candidate.subtitleId}] bilinmeyen paket biçimi")
                null
            }
            else -> {
                val extracted = SubtitleArchiveExtractor.extractSubtitles(packageFile, extractedDir)
                if (extracted.isNotEmpty()) {
                    runCatching { completeMarker.writeText("ok:${extracted.size}") }
                }
                extracted
            }
        }
    }

    private data class ArchiveEntry(val name: String, val path: String)

    /**
     * `/api/subtitle/{id}/archive` liste ucundan paketin içeriğini döner.
     * null = uç bu kaydı desteklemiyor (tek Dosya, RAR vb.) veya hata.
     */
    private fun fetchArchiveEntries(subtitleId: Long): List<ArchiveEntry>? {
        val json = httpGetJson("$BASE/api/subtitle/$subtitleId/archive") ?: return null
        val success = json.bool("success")
        if (success == false) return null
        val files = json.arr("files") ?: return null
        val entries = mutableListOf<ArchiveEntry>()
        for (el in files) {
            val obj = el.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val name = obj.str("name") ?: continue
            val path = obj.str("path") ?: name
            val category = obj.str("category")?.lowercase(Locale.ROOT)
            if (AniSubEpisodeMatcher.isSubtitleFileName(name) || category == "subtitle") {
                entries += ArchiveEntry(name, path)
            }
        }
        return entries
    }

    private data class DownloadInfo(val url: String, val filename: String?)

    private fun fetchDownloadInfo(subtitleId: Long): DownloadInfo? {
        val json = httpGetJson("$BASE/api/subtitles/$subtitleId/download") ?: return null
        if (json.bool("success") == false) return null
        val data = json.obj("data") ?: return null
        val url = data.str("download_url") ?: return null
        return DownloadInfo(url = url, filename = data.str("filename"))
    }

    // ------------------------------------------------------------ HTTP

    private fun httpGetJson(url: String): JsonObject? {
        val body = httpGetString(url) ?: return null
        return try {
            JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
        } catch (e: Exception) {
            Log.w(TAG, "JSON ayrıştırılamadı ($url): ${e.message}")
            null
        }
    }

    private fun httpGetString(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .header("Referer", "$BASE/")
            .get()
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "HTTP ${response.code} ($url)")
                    return null
                }
                response.body?.string()
            }
        } catch (e: Exception) {
            Log.w(TAG, "İstek başarısız ($url): ${e.message}")
            null
        }
    }

    private fun httpDownloadToFile(url: String, target: File, maxBytes: Long): Boolean {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "$BASE/")
            .get()
            .build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Paket indirilemedi HTTP ${response.code} ($url)")
                    return false
                }
                val body = response.body ?: return false
                if (body.contentLength() > maxBytes) {
                    Log.w(TAG, "Paket cok buyuk: ${body.contentLength()} > $maxBytes")
                    return false
                }
                val temp = File(target.parentFile, target.name + ".tmp")
                var written = 0L
                body.byteStream().use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n <= 0) break
                            written += n
                            if (written > maxBytes) {
                                temp.delete()
                                Log.w(TAG, "Paket indirme sınırı asildi")
                                return false
                            }
                            output.write(buffer, 0, n)
                        }
                    }
                }
                if (written == 0L) {
                    temp.delete()
                    return false
                }
                if (target.exists()) target.delete()
                temp.renameTo(target)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Paket indirme hatasi ($url): ${e.message}")
            false
        }
    }

    // ------------------------------------------------------------ yardımcılar

    private fun itemForLocalFile(
        candidate: Candidate,
        file: File,
        language: String,
        episode: Int?
    ): SubtitleResponseItem = SubtitleResponseItem(
        id = itemId(candidate.subtitleId, episode, file.name),
        // "file://" şeması ile dönülür; SubtitleFileCache yerel dosyaları
        // indirmeden doğrudan kabul eder (bkz. SubtitleFileCache.cacheSubtitle).
        url = "file://${file.absolutePath}",
        lang = language,
        format = AniSubEpisodeMatcher.subtitleExtension(file.name) ?: candidate.formatHint
    )

    private fun itemId(subtitleId: Long, episode: Int?, fileName: String): String =
        "anisub-$subtitleId-e${episode ?: 0}-${Integer.toHexString(fileName.hashCode())}"

    private fun listExtractedSubtitles(dir: File): List<File> {
        if (!dir.exists()) return emptyList()
        val files = mutableListOf<File>()
        dir.walkTopDown().forEach { file ->
            if (file.isFile && file.length() > 0 && AniSubEpisodeMatcher.isSubtitleFileName(file.name)) {
                files += file
            }
        }
        return files
    }

    /** Düz metin altyazının biçimini içeriğinden anlar. */
    private fun sniffPlainSubtitleFormat(file: File): String? {
        return try {
            val head = file.inputStream().use { input ->
                val bytes = ByteArray(4096)
                val read = input.read(bytes)
                if (read <= 0) "" else String(bytes, 0, read, Charsets.UTF_8)
            }
            val trimmed = head.trimStart('﻿', ' ', '\n', '\r', '\t')
            when {
                trimmed.startsWith("[Script Info]", true) || trimmed.startsWith("[Events]", true) -> "ass"
                trimmed.startsWith("WEBVTT") -> "vtt"
                trimmed.contains("-->") -> "srt"
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSubtitleIdFromStremioUrl(url: String): Long? {
        val last = url.substringBefore('?').trimEnd('/').substringAfterLast('/')
        return last.toLongOrNull()
            ?: Regex("""subtitle/(\d+)""").find(url)?.groupValues?.get(1)?.toLongOrNull()
    }

    /** Release adı ile video Dosya adı arasındaki ortak sözcük sayısı (uyum ipucu). */
    private fun filenameAffinity(filenameHint: String?, releaseName: String?): Int {
        if (filenameHint.isNullOrBlank() || releaseName.isNullOrBlank()) return 0
        fun tokens(value: String) = value.lowercase(Locale.ROOT)
            .split(Regex("""[^a-z0-9]+"""))
            .filter { it.length >= 3 }
            .toSet()
        return tokens(filenameHint).intersect(tokens(releaseName)).size
    }

    /**
     * Dil adını karşılaştırmaya uygun anahtara indirir.
     * Locale.ROOT ile küçük harfe çevrilen "İngilizce", i + birleşik nokta
     * (U+0307) içerdiğinden düz "ingilizce" ile eşleşmez; anahtar üretiminde
     * işaretler ve Türkçe harfler sadeleştirilir.
     */
    private fun languageKey(rawLanguage: String?): String =
        rawLanguage?.trim().orEmpty()
            .lowercase(Locale.ROOT)
            .replace("\u0307", "")
            .replace('ı', 'i')
            .replace('ü', 'u')
            .replace('ö', 'o')
            .replace('ş', 's')
            .replace('ç', 'c')
            .replace('ğ', 'g')

    internal fun isTurkish(rawLanguage: String?): Boolean {
        val value = languageKey(rawLanguage)
        if (value.isEmpty()) return true // AniSub varsayımsal olarak Türkçe yayındır
        return value in setOf(
            "turkce", "turkish", "turk", "tr", "tur",
            "turkish language", "turkiye turkcesi"
        ) || value.contains("turk")
    }

    /** AniSub'un Türkçe dil adlarını Stremio/oyuncunun beklediği etiketlere çevirir. */
    internal fun normalizeLanguage(rawLanguage: String?): String {
        val raw = rawLanguage?.trim().orEmpty()
        if (raw.isEmpty()) return "Turkish"
        return when (languageKey(raw)) {
            "turkce", "turkish", "turk", "tr", "tur" -> "Turkish"
            "ingilizce", "english", "en", "eng" -> "English"
            "japonca", "japanese", "ja", "jp", "jpn" -> "Japanese"
            "almanca", "german", "de", "deu" -> "German"
            "fransizca", "french", "fr", "fra" -> "French"
            "ispanyolca", "spanish", "es", "spa" -> "Spanish"
            "italyanca", "italian", "it", "ita" -> "Italian"
            "korece", "korean", "ko", "kor" -> "Korean"
            "cince", "chinese", "zh", "zho" -> "Chinese"
            "rusca", "russian", "ru", "rus" -> "Russian"
            "arapca", "arabic", "ar", "ara" -> "Arabic"
            else -> raw
        }
    }

    // ------------------------------------------------------------ iç tipler

    private data class Candidate(
        val subtitleId: Long,
        var languageRaw: String? = null,
        var formatHint: String? = null,
        var contentType: String? = null,
        var episodeDescription: String? = null,
        var releaseName: String? = null,
        var anilistId: Int? = null,
        var romaji: String? = null,
        var english: String? = null,
        var mediaFormat: String? = null,
        var isAiTranslated: Boolean = false,
        var downloads: Long = 0L,
        var thanks: Long = 0L,
        var discoveredViaStremio: Boolean = false
    ) {
        /** Eksik alanları [other] ile tamamlar; dolu alanında üzerine yazmaz. */
        fun absorb(other: Candidate) {
            if (languageRaw.isNullOrBlank()) languageRaw = other.languageRaw
            if (formatHint.isNullOrBlank()) formatHint = other.formatHint
            if (contentType.isNullOrBlank()) contentType = other.contentType
            if (episodeDescription.isNullOrBlank()) episodeDescription = other.episodeDescription
            if (releaseName.isNullOrBlank()) releaseName = other.releaseName
            if (anilistId == null) anilistId = other.anilistId
            if (romaji.isNullOrBlank()) romaji = other.romaji
            if (english.isNullOrBlank()) english = other.english
            if (mediaFormat.isNullOrBlank()) mediaFormat = other.mediaFormat
            if (!isAiTranslated) isAiTranslated = other.isAiTranslated
            if (downloads == 0L) downloads = other.downloads
            if (thanks == 0L) thanks = other.thanks
        }
    }

    private fun MutableMap<Long, Candidate>.merge(candidate: Candidate) {
        val existing = this[candidate.subtitleId]
        if (existing == null) {
            this[candidate.subtitleId] = candidate
        } else {
            existing.absorb(candidate)
        }
    }

    private data class ParsedRequest(
        val imdbId: String?,
        val season: Int?,
        val episode: Int?,
        val aniListId: Int?,
        val title: String?,
        val movieIntent: Boolean,
        val filenameHint: String?,
        val candidateTitles: List<String> = emptyList()
    ) {
        /** Film kayıtlarında bölüm hedeflemek anlamsızdır; dizi kayıtlarında kesindir. */
        fun effectiveEpisodeFor(candidate: Candidate): Int? {
            val format = candidate.mediaFormat?.lowercase(Locale.ROOT).orEmpty()
            val looksMovie = format.contains("film") || format.contains("movie")
            if (looksMovie) return null
            if (movieIntent && (episode == null || episode <= 1) && format.isEmpty()) return null
            return episode?.takeIf { it > 0 }
        }

        /**
         * Oyuncu aynı bölüm için tt / kitsu / başlık ve movie / series
         * varyantlarıyla paralel sorgular atar; hepsinde AniList kimliği ile
         * başlık ve bölüm aynı taşınır. Anahtar bu yüzden sorgu kimliğine
         * değil içeriğin kanonik kimliğine kurulur: AniList > IMDb > başlık.
         */
        fun cacheKey(): String {
            val episodePart = episode?.toString() ?: "-"
            return when {
                aniListId != null -> "al:$aniListId|i:${imdbId ?: "-"}|e:$episodePart"
                !imdbId.isNullOrBlank() -> "tt:$imdbId|s:${season ?: "-"}|e:$episodePart"
                !title.isNullOrBlank() ->
                    "t:${AniSubEpisodeMatcher.normalizeTitle(title)}|e:$episodePart"
                else -> listOf(
                    imdbId ?: "-",
                    season?.toString() ?: "-",
                    episodePart,
                    if (movieIntent) "movie" else "series"
                ).joinToString("|")
            }
        }

        companion object {
            fun parse(
                type: String,
                id: String,
                aniListId: Int?,
                animeTitle: String?,
                episodeParam: Int?,
                filenameHint: String?,
                extraTitles: List<String> = emptyList()
            ): ParsedRequest {
                val rawId = id.trim()
                val imdb = Regex("""(tt\d{5,})""").find(rawId)?.groupValues?.get(1)

                var season: Int? = null
                var episodeFromId: Int? = null
                var titleFromId: String? = null

                when {
                    rawId.startsWith("tt") -> {
                        val parts = rawId.split(':')
                        season = parts.getOrNull(1)?.toIntOrNull()
                        episodeFromId = parts.getOrNull(2)?.toIntOrNull()
                    }
                    rawId.startsWith("kitsu:") -> {
                        val parts = rawId.split(':')
                        episodeFromId = parts.getOrNull(2)?.toIntOrNull()
                    }
                    rawId.contains(':') -> {
                        // Başlık sorgusu biçimi: "Anime Adı:1:5". Başlık kendi içinde
                        // iki nokta içerebileceği için yalnızca sondaki ":S:E" ayrılır.
                        val tail = Regex(""":(\d{1,3}):(\d{1,4})$""").find(rawId)
                        if (tail != null) {
                            season = tail.groupValues[1].toIntOrNull()
                            episodeFromId = tail.groupValues[2].toIntOrNull()
                            titleFromId = rawId.substring(0, tail.range.first).trim()
                        } else if (!rawId.contains("://")) {
                            titleFromId = rawId
                        }
                    }
                    rawId.isNotBlank() && rawId.any { it.isLetter() } -> titleFromId = rawId
                }

                // "tmdb:12345" / "anilist:12345" gibi önekli kimlikler başlık değildir.
                val usableTitleFromId = titleFromId
                    ?.takeUnless { Regex("""^[a-z]{2,}:\d+$""").matches(it.trim()) }
                val allTitles = (extraTitles + listOfNotNull(animeTitle, usableTitleFromId))
                    .map { it.trim() }
                    .filter { it.isNotBlank() && it.any { ch -> ch in 'A'..'Z' || ch in 'a'..'z' || ch.isDigit() } }
                    .distinctBy { it.lowercase(Locale.ROOT) }
                val title = allTitles.firstOrNull()
                    ?: animeTitle?.trim()?.takeIf { it.isNotBlank() }
                    ?: usableTitleFromId?.takeIf { it.isNotBlank() }

                val episode = episodeFromId ?: episodeParam?.takeIf { it > 0 }
                val normalizedType = type.lowercase(Locale.ROOT)
                val movieIntent = normalizedType == "movie" && episodeFromId == null

                val titleSuffix = if (usableTitleFromId != null) " (id başlığından)" else ""
                Log.d(
                    TAG,
                    "Istek ayrıştırıldı: id=$rawId imdb=$imdb S=$season E=$episode " +
                        "(param=$episodeParam) title=$title movie=$movieIntent$titleSuffix"
                )
                return ParsedRequest(
                    imdbId = imdb,
                    season = season,
                    episode = episode,
                    aniListId = aniListId,
                    title = title,
                    movieIntent = movieIntent,
                    filenameHint = filenameHint,
                    candidateTitles = if (allTitles.isNotEmpty()) allTitles else listOfNotNull(title)
                )
            }
        }
    }

    // Gson güvenli okuyucular (alan adı/bitişi değişse bile çökmez)
    private fun JsonObject.obj(key: String): JsonObject? =
        runCatching { get(key)?.takeIf { it.isJsonObject }?.asJsonObject }.getOrNull()

    private fun JsonObject.arr(key: String): JsonArray? =
        runCatching { get(key)?.takeIf { it.isJsonArray }?.asJsonArray }.getOrNull()

    private fun JsonObject.str(key: String): String? =
        runCatching {
            val el = get(key) ?: return null
            if (!el.isJsonPrimitive) return null
            el.asString?.takeIf { it.isNotBlank() }
        }.getOrNull()

    private fun JsonObject.int(key: String): Int? =
        runCatching { get(key)?.takeIf { it.isJsonPrimitive }?.asInt }.getOrNull()

    private fun JsonObject.long(key: String): Long? =
        runCatching { get(key)?.takeIf { it.isJsonPrimitive }?.asLong }.getOrNull()

    private fun JsonObject.bool(key: String): Boolean? =
        runCatching { get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean }.getOrNull()

    /**
     * Laravel oturum ve XSRF çerezlerini istekler arasında tutan bellek içi çerez yöneticisi.
     */
    private class InMemoryCookieJar : CookieJar {
        private val cookieStore = mutableListOf<Cookie>()

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val now = System.currentTimeMillis()
            cookies.forEach { newCookie ->
                cookieStore.removeAll { it.name == newCookie.name && it.matches(url) }
                if (newCookie.expiresAt > now) {
                    cookieStore.add(newCookie)
                }
            }
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            cookieStore.removeAll { it.expiresAt <= now }
            return cookieStore.filter { it.matches(url) }
        }
    }
}
