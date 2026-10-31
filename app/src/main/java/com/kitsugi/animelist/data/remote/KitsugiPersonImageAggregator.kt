package com.kitsugi.animelist.data.remote

import com.kitsugi.animelist.data.auth.AniListGate

import com.kitsugi.animelist.data.auth.BangumiApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Karakter / kişi (seslendirmen, oyuncu, personel) görsellerini **tüm kaynaklardan birlikte**
 * toplayan ortak toplayıcı.
 *
 * Tarihsel olarak galeri yalnızca sayfanın açıldığı kaynağın görselini + (kimlik MAL
 * uzayındaysa) Jikan `/pictures` eklerini gösteriyordu. Artık kimlik bilinen/çözümlenebilen
 * her kaynak (MAL/Jikan, AniList, Shikimori, Kitsu, Bangumi, TMDB) katkı verir; her görsel
 * kendi kaynak etiketiyle döner (galeri diyaloğundaki kaynak rozeti bu etiketi gösterir).
 *
 * Güvenilirlik kuralları:
 *  - İsme-dayalı aramalar **sıkı eşleşme** gerektirir (yanlış kişi/karakter görseli göstermek
 *    yerine hiç göstermemek tercih edilir) — bkz. [nameMatches].
 *  - Her kaynak çağrısı bağımsız hata/timeout toleranslıdır; toplam süre [budgetMs] ile
 *    sınırlıdır ve dolan kaynakların görselleri kısmi olarak döner.
 *  - Kurgusal karakterler TMDB'de bulunmadığından TMDB yalnızca [PersonKind.PERSON] için
 *    sorgulanır (kişi profil fotoğrafları); Kitsu yalnızca karakterler için sorgulanır.
 */
object KitsugiPersonImageAggregator {

    enum class PersonKind { CHARACTER, PERSON }

    /** Galeri diyaloğunun anladığı görünür kaynak etiketi. */
    data class SourceImage(val url: String, val source: String)

    private const val SRC_MAL = "MyAnimeList"
    private const val SRC_ANILIST = "AniList"
    private const val SRC_SHIKIMORI = "Shikimori"
    private const val SRC_KITSU = "Kitsu"
    private const val SRC_BANGUMI = "Bangumi"
    private const val SRC_TMDB = "TMDB"

    private const val MAX_TMDB_PROFILES = 12
    private const val SEARCH_NAME_ATTEMPTS = 5
    private const val JIKAN_SEARCH_LIMIT = 8

    /**
     * Tüm kaynaklardan görselleri toplar.
     *
     * @param kind       karakter mi gerçek kişi mi
     * @param source     detayın açıldığı kaynak (jikan/mal, anilist, shikimori, kitsu, bangumi, tmdb...)
     * @param id         açılan kaynaktaki kimlik (kaynak uzayında)
     * @param malId      biliniyorsa gerçek MAL kimliği (Jikan + Shikimori sorgularını açar)
     * @param aniListId  biliniyorsa AniList kimliği (doğrudan görsel sorgusu)
     * @param names      aday adlar: birincil ad + native/romanize/ingilizce/alternatif adlar
     * @param tmdbEnabled kullanıcı ayarı; kapalıysa TMDB atlanır
     * @param budgetMs   toplam süre bütçesi (kısmi sonuç döner)
     * @param allowSingleTokenNames karakterin kimliği (MAL/AniList) biliniyorsa ya da adın
     *   birebir eşleşmesi zorunlu tutuluyorsa tek kelimeli Latin adlar da aday kabul edilir
     *   (bkz. [isUnambiguousName] 2. imzası). Varsayılan `false` = eski, katı davranış.
     */
    suspend fun aggregate(
        kind: PersonKind,
        source: String,
        id: Int,
        malId: Int?,
        aniListId: Int?,
        names: List<String>,
        tmdbEnabled: Boolean,
        budgetMs: Long = 9_000L,
        allowSingleTokenNames: Boolean = false
    ): List<SourceImage> {
        val canonical = MalJikanMediaSupport.canonicalSource(source)
        val isBangumiSource = canonical == "bangumi" || canonical == "bgm"
        // İsimle (kimliksiz) arama yalnızca AYIRT EDİCİ adlarla yapılır: "Suzu", "Ayane" gibi
        // tek kelimelik adlar binlerce farklı yapımın karakterinde geçer; bu adlarla yapılan
        // aramalar alakasız karakterin görselini getiriyordu.
        // İstisna: çağıran, adayın ya kimlikle (MAL/AniList) ya da birebir ad eşleşmesiyle
        // doğrulandığını garanti ediyorsa tek kelimeli adlar da kabul edilir — aksi hâlde
        // "Nobita"/"Doraemon" gibi adlar hiç aranamıyor ve görsel hiç gelmiyordu.
        val nameCandidates = names.filter { it.isNotBlank() }.map { it.trim() }.distinct()
            .filter { isUnambiguousName(it, allowSingleTokenLatin = allowSingleTokenNames) }
            .take(SEARCH_NAME_ATTEMPTS)
        val knownMalId = when {
            MalJikanMediaSupport.isMalSource(source) ->
                MalJikanMediaSupport.resolveMalId(source, id, malId)
            canonical == "shikimori" -> malId?.takeIf { it in 1..99_999_999 }
            else -> malId?.takeIf { it in 1..99_999_999 }
        }
        val results = mutableListOf<SourceImage>()
        coroutineScope {
            val completedImages = Channel<List<SourceImage>>(Channel.UNLIMITED)
            val scope = this
            val tasks = mutableListOf<Job>()
            fun enqueue(fetch: suspend () -> List<SourceImage>) {
                tasks += scope.launch { completedImages.send(safeImages(fetch)) }
            }

            if (knownMalId != null || nameCandidates.isNotEmpty()) {
                enqueue {
                    malLinkedImages(
                        kind = kind,
                        knownMalId = knownMalId,
                        names = nameCandidates
                    )
                }
            }
            if ((aniListId != null && aniListId > 0) || nameCandidates.isNotEmpty()) {
                enqueue { aniListImage(kind, aniListId, nameCandidates) }
            }
            when (kind) {
                PersonKind.CHARACTER -> enqueue {
                    kitsuCharacterImage(knownId = id.takeIf { canonical == "kitsu" && it > 0 }, names = nameCandidates)
                }
                PersonKind.PERSON -> Unit // Kitsu personel görseli sağlamaz
            }
            enqueue { bangumiImage(kind, knownId = id.takeIf { isBangumiSource && it > 0 }, names = nameCandidates) }
            if (kind == PersonKind.PERSON && tmdbEnabled) {
                enqueue { tmdbPersonImages(knownId = id.takeIf { canonical == "tmdb" && it > 0 }, names = nameCandidates) }
            }

            // Kaynakları başlatıldığı sırayla değil tamamlandığı sırayla topla. Böylece yavaş
            // Jikan araması, hazır AniList/Bangumi/TMDB görsellerinin süre bütçesi dolduğu için
            // atlanmasına neden olmaz.
            val deadline = System.currentTimeMillis() + budgetMs
            var collected = 0
            while (collected < tasks.size) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0L) break
                val images = withTimeoutOrNull(remaining) { completedImages.receive() } ?: break
                results += images
                collected++
            }
            tasks.forEach { it.cancel() }
        }
        return results.distinctBy { it.url }
    }

    /**
     * Liste kartları (ör. TMDB/Simkl Karakterler sekmesi) için **ilk geçerli portreyi** bulur bulmaz
     * diğer paralel istekleri anında iptal ederek döner.
     *
     * [CharacterDetailPage]'in karakter adına tıklayınca her zaman portre bulabilmesinin sırrı,
     * Kitsu + Bangumi + Jikan + AniList uçlarını paralel koşturmasıdır; bu metot aynı gücü tek
     * görsel hedefiyle minimum gecikmeyle liste görünümüne taşır.
     */
    suspend fun firstPortrait(
        names: List<String>,
        allowSingleTokenNames: Boolean = true,
        allowAnimeCatalogs: Boolean = true,
        budgetMs: Long = 4_500L
    ): String? {
        val nameCandidates = names.asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .filter { isUnambiguousName(it, allowSingleTokenLatin = allowSingleTokenNames && allowAnimeCatalogs) }
            .take(3)
            .toList()
        if (nameCandidates.isEmpty()) return null

        for (candidate in nameCandidates) {
            DetailCache.getCachedCharacterPortrait(candidate)?.let { return it }
        }

        var winner: String? = null
        coroutineScope {
            val completed = Channel<String?>(Channel.UNLIMITED)
            val scope = this
            val tasks = mutableListOf<Job>()
            fun enqueue(fetch: suspend () -> String?) {
                tasks += scope.launch {
                    val found = try {
                        fetch()?.takeIf { it.isNotBlank() }
                    } catch (c: CancellationException) {
                        throw c
                    } catch (_: Exception) {
                        null
                    }
                    completed.send(found)
                }
            }

            if (allowAnimeCatalogs) {
                enqueue { kitsuCharacterImage(knownId = null, names = nameCandidates).firstOrNull()?.url }
                enqueue { jikanDirectCharacterPortrait(nameCandidates) }
                enqueue { aniListImage(PersonKind.CHARACTER, null, nameCandidates).firstOrNull()?.url }
            }
            enqueue { bangumiImage(PersonKind.CHARACTER, knownId = null, names = nameCandidates).firstOrNull()?.url }

            val deadline = System.currentTimeMillis() + budgetMs
            var finished = 0
            while (finished < tasks.size && winner == null) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0L) break
                val url = withTimeoutOrNull(remaining) { completed.receive() }
                finished++
                if (!url.isNullOrBlank()) {
                    winner = url
                    break
                }
            }
            tasks.forEach { it.cancel() }
        }

        if (!winner.isNullOrBlank()) {
            nameCandidates.firstOrNull()?.let { DetailCache.propagateCharacterPortrait(it, winner) }
        }
        return winner
    }

    /**
     * Jikan `/v4/characters?q=...` yanıtındaki ilk doğrulanmış eşleşmenin doğrudan portresini döner
     * (`/pictures` için ikinci bir istek atmaz; `fetchCharacterDetail("tmdb")` ile birebir aynı mantık).
     */
    private suspend fun jikanDirectCharacterPortrait(names: List<String>): String? {
        for (query in names) {
            val results = runCatching { JikanApiClient().searchMalCharacters(query, page = 1) }.getOrNull().orEmpty()
            val match = results.firstOrNull { item ->
                !item.imageUrl.isNullOrBlank() && nameMatches(query, item.title)
            }
            if (match != null && !match.imageUrl.isNullOrBlank()) {
                return match.imageUrl
            }
        }
        return null
    }

    private suspend fun safeImages(fetch: suspend () -> List<SourceImage>): List<SourceImage> =
        try {
            fetch()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }

    /** MAL/Jikan görselleri ve Shikimori profil görseli; kimlik yoksa katı ad eşleşmesiyle çözülür. */
    private suspend fun malLinkedImages(
        kind: PersonKind,
        knownMalId: Int?,
        names: List<String>
    ): List<SourceImage> {
        val resolvedMalId = knownMalId?.takeIf { it in 1..99_999_999 }
            ?: resolveMalIdByName(kind, names)
            ?: return emptyList()

        return coroutineScope {
            val jikan = async { jikanPictures(kind, resolvedMalId) }
            val shikimori = async { shikimoriImage(kind, resolvedMalId) }
            jikan.await() + shikimori.await()
        }
    }

    /**
     * AniList/Bangumi/Kitsu/TMDB entity IDs eivät ole MAL-ID:itä. Adları Jikan'da aratıp
     * yalnızca tam ad/alternatif ad eşleşmesi tek bir MAL sonucuna gidiyorsa ID'yi kullan.
     */
    private suspend fun resolveMalIdByName(kind: PersonKind, names: List<String>): Int? = withContext(Dispatchers.IO) {
        val endpoint = if (kind == PersonKind.CHARACTER) "characters" else "people"
        for (query in names.filter { it.isNotBlank() }.distinct().take(SEARCH_NAME_ATTEMPTS)) {
            val url = "https://api.jikan.moe/v4/$endpoint?q=${URLEncoder.encode(query, "UTF-8")}&limit=$JIKAN_SEARCH_LIMIT"
            val body = when (val response = JikanGateway.fetchBlocking(url, JikanGateway.Priority.UI)) {
                is JikanResult.Ok -> response.body
                else -> continue
            }
            val data = runCatching { JSONObject(body).optJSONArray("data") }.getOrNull() ?: continue
            val exactMatches = (0 until data.length()).mapNotNull { index ->
                val entry = data.optJSONObject(index) ?: return@mapNotNull null
                val malId = entry.optInt("mal_id", 0).takeIf { it in 1..99_999_999 }
                    ?: return@mapNotNull null
                val candidates = jikanEntityNames(kind, entry)
                malId.takeIf { candidates.any { candidate -> nameMatches(query, candidate) } }
            }.distinct()
            if (exactMatches.size == 1) return@withContext exactMatches.single()
        }
        null
    }

    private fun jikanEntityNames(kind: PersonKind, entry: JSONObject): List<String> = buildList {
        fun addField(key: String) {
            entry.optNullableString(key)?.takeIf { it.isNotBlank() }?.let(::add)
        }
        fun addArray(key: String) {
            val array = entry.optJSONArray(key) ?: return
            for (index in 0 until array.length()) {
                array.optString(index).trim().takeIf { it.isNotBlank() && it != "null" }?.let(::add)
            }
        }

        addField("name")
        when (kind) {
            PersonKind.CHARACTER -> {
                addField("name_kanji")
                addArray("nicknames")
            }
            PersonKind.PERSON -> {
                addField("given_name")
                addField("family_name")
                val given = entry.optNullableString("given_name")
                val family = entry.optNullableString("family_name")
                if (!given.isNullOrBlank() && !family.isNullOrBlank()) {
                    add("$given $family")
                    add("$family $given")
                }
                addArray("alternate_names")
            }
        }
    }.distinct()

    // ── MAL / Jikan — /pictures ekleri ──────────────────────────────────────────

    private suspend fun jikanPictures(kind: PersonKind, malId: Int): List<SourceImage> =
        withContext(Dispatchers.IO) {
            val endpoint = if (kind == PersonKind.CHARACTER) "characters" else "people"
            runCatching {
                val text = (JikanGateway.fetchBlocking(
                    "https://api.jikan.moe/v4/$endpoint/$malId/pictures",
                    JikanGateway.Priority.UI
                ) as? JikanResult.Ok)?.body ?: return@runCatching emptyList()
                val dataArr = JSONObject(text).optJSONArray("data") ?: return@runCatching emptyList()
                val urls = mutableListOf<SourceImage>()
                for (i in 0 until dataArr.length()) {
                    val obj = dataArr.getJSONObject(i)
                    val webp = obj.optJSONObject("webp")
                    val jpg = obj.optJSONObject("jpg")
                    val picUrl = webp?.optString("large_image_url")?.takeIf { it.isNotBlank() }
                        ?: webp?.optString("image_url")?.takeIf { it.isNotBlank() }
                        ?: jpg?.optString("large_image_url")?.takeIf { it.isNotBlank() }
                        ?: jpg?.optString("image_url")?.takeIf { it.isNotBlank() }
                    if (!picUrl.isNullOrBlank()) urls += SourceImage(picUrl, SRC_MAL)
                }
                urls
            }.getOrElse { emptyList() }
        }

    // ── AniList — doğrudan kimlik veya isim araması ────────────────────────────

    private suspend fun aniListImage(
        kind: PersonKind,
        aniListId: Int?,
        names: List<String>
    ): List<SourceImage> = withContext(Dispatchers.IO) {
        val node = if (kind == PersonKind.CHARACTER) "Character" else "Staff"
        val directImage = if (aniListId != null && aniListId > 0) {
            runCatching {
                val query = "query (\$id: Int) { $node(id: \$id) { image { large } } }"
                // Kişi görseli zenginleştirmesi: kullanıcı kotasına dokunmaz, kıtlıkta atlanır.
                val response = KitsugiApiBase.executeAniListQuery(
                    query = query,
                    variables = JSONObject().put("id", aniListId),
                    priority = AniListGate.Priority.BACKGROUND
                )
                response?.let {
                    JSONObject(it).optJSONObject("data")?.optJSONObject(node)
                        ?.optJSONObject("image")?.optNullableString("large")
                }
            }.getOrNull()
        } else null
        if (!directImage.isNullOrBlank()) return@withContext listOf(SourceImage(directImage, SRC_ANILIST))
        if (names.isEmpty()) return@withContext emptyList()

        val pageField = if (kind == PersonKind.CHARACTER) "characters" else "staff"
        // Karakterlerde yapım bilgisi (+18 kontrolü için) de istenir.
        val mediaField = if (kind == PersonKind.CHARACTER) "media(perPage: 10) { nodes { isAdult } }" else ""
        for (name in names) {
            val query = """
                query (${'$'}search: String) {
                    Page(page: 1, perPage: 5) {
                        $pageField(search: ${'$'}search) {
                            id name { full userPreferred native } image { large }
                            $mediaField
                        }
                    }
                }
            """.trimIndent()
            val response = runCatching {
                KitsugiApiBase.executeAniListQuery(
                    query = query,
                    variables = JSONObject().put("search", name),
                    priority = AniListGate.Priority.BACKGROUND
                )
            }.getOrNull() ?: continue
            val arr = runCatching {
                JSONObject(response).optJSONObject("data")?.optJSONObject("Page")?.optJSONArray(pageField)
            }.getOrNull() ?: continue
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val nameObj = obj.optJSONObject("name")
                val candidateNames = listOfNotNull(
                    nameObj?.optNullableString("userPreferred"),
                    nameObj?.optNullableString("full"),
                    nameObj?.optNullableString("native")
                )
                val matched = names.any { requested -> candidateNames.any { candidate -> nameMatches(requested, candidate) } }
                if (matched) {
                    // Kimliksiz isim eşleşmesi: +18 bir yapımda geçen karakter, sayfanın karakteri
                    // olmayabilir (aynı isimli farklı karakter) → atlanır.
                    if (kind == PersonKind.CHARACTER && hasAdultAniListMedia(obj)) continue
                    val image = obj.optJSONObject("image")?.optNullableString("large")
                    if (!image.isNullOrBlank()) return@withContext listOf(SourceImage(image, SRC_ANILIST))
                }
            }
        }
        emptyList()
    }

    // ── Shikimori — kimlik MAL uzayıyla aynı ────────────────────────────────────

    private suspend fun shikimoriImage(kind: PersonKind, malId: Int): List<SourceImage> =
        withContext(Dispatchers.IO) {
            runCatching {
                val segment = if (kind == PersonKind.CHARACTER) "characters" else "people"
                val response = KitsugiApiBase.executeGetRequestResilient(
                    URL("https://shikimori.io/api/$segment/$malId")
                ) ?: return@runCatching emptyList()
                val relative = JSONObject(response).optJSONObject("image")?.optString("original")
                ShikimoriPosterResolver.absoluteUrl(relative)
                    ?.let { listOf(SourceImage(it, SRC_SHIKIMORI)) }
                    ?: emptyList()
            }.getOrElse { emptyList() }
        }

    // ── Kitsu — yalnızca karakterler ────────────────────────────────────────────

    private fun kitsuGet(urlStr: String): JSONObject? {
        val request = okhttp3.Request.Builder()
            .url(urlStr)
            .header("Accept", "application/vnd.api+json")
            .header("User-Agent", "Kitsugi/1.0 (Android)")
            .build()
        return runCatching {
            com.kitsugi.animelist.core.network.KitsugiHttpClient.client.newCall(request).execute()
                .use { response ->
                    if (!response.isSuccessful) null
                    else response.body?.string()?.takeIf { it.isNotBlank() }?.let { JSONObject(it) }
                }
        }.getOrNull()
    }

    private fun kitsuImageOf(attrs: JSONObject?): String? {
        val imageObj = attrs?.optJSONObject("image") ?: return null
        return imageObj.optString("original").takeIf { it.isNotBlank() }
            ?: imageObj.optString("large").takeIf { it.isNotBlank() }
            ?: imageObj.optString("medium").takeIf { it.isNotBlank() }
    }

    private suspend fun kitsuCharacterImage(
        knownId: Int?,
        names: List<String>
    ): List<SourceImage> = withContext(Dispatchers.IO) {
        runCatching {
            if (knownId != null && knownId > 0) {
                val root = kitsuGet(KitsuApiHost.url("/characters/$knownId"))
                val attrs = root?.optJSONObject("data")?.optJSONObject("attributes")
                kitsuImageOf(attrs)?.let { return@runCatching listOf(SourceImage(it, SRC_KITSU)) }
            }
            for (name in names) {
                val encoded = URLEncoder.encode(name, "UTF-8")
                val root = kitsuGet(KitsuApiHost.url("/characters?filter[name]=$encoded&page[limit]=5"))
                    ?: continue
                val data = root.optJSONArray("data") ?: continue
                for (i in 0 until data.length()) {
                    val attrs = data.optJSONObject(i)?.optJSONObject("attributes") ?: continue
                    val candidateNames = listOfNotNull(
                        attrs.optNullableString("name"),
                        attrs.optNullableString("canonicalName")
                    )
                    val matched = names.any { q -> candidateNames.any { nameMatches(q, it) } }
                    if (matched) {
                        kitsuImageOf(attrs)?.let { return@runCatching listOf(SourceImage(it, SRC_KITSU)) }
                    }
                }
            }
            emptyList()
        }.getOrElse { emptyList() }
    }

    // ── Bangumi — kimlik veya arama (karakter + kişi) ───────────────────────────

    private fun bangumiImageOf(images: JSONObject?): String? {
        if (images == null) return null
        for (key in listOf("large", "common", "medium", "small", "grid")) {
            if (images.isNull(key)) continue
            val url = BangumiApiClient.absoluteImageUrl(images.optString(key).trim().ifBlank { null })
            if (!url.isNullOrBlank()) return url
        }
        return null
    }

    private suspend fun bangumiImage(
        kind: PersonKind,
        knownId: Int?,
        names: List<String>
    ): List<SourceImage> = withContext(Dispatchers.IO) {
        runCatching {
            if (knownId != null && knownId > 0) {
                val raw = BangumiIdNamespace.rawIdFromStable(knownId) ?: knownId
                val root = if (kind == PersonKind.CHARACTER) {
                    BangumiApiClient.getCharacter(raw)
                } else {
                    BangumiApiClient.getPerson(raw)
                }
                bangumiImageOf(root?.optJSONObject("images"))
                    ?.let { return@runCatching listOf(SourceImage(it, SRC_BANGUMI)) }
            }
            for (name in names) {
                val page = if (kind == PersonKind.CHARACTER) {
                    runCatching { BangumiApiClient.searchCharacters(name, limit = 5) }.getOrNull()
                } else {
                    runCatching { BangumiApiClient.searchPersons(name, limit = 5) }.getOrNull()
                } ?: continue
                for (entity in page.data) {
                    val candidates = listOf(entity.name, entity.nameCn).filter { it.isNotBlank() }
                    val matched = names.any { q -> candidates.any { nameMatches(q, it) } }
                    if (matched) {
                        val img = entity.images?.large ?: entity.images?.common
                            ?: entity.images?.medium ?: entity.images?.small ?: entity.images?.grid
                        BangumiApiClient.absoluteImageUrl(img)
                            ?.let { return@runCatching listOf(SourceImage(it, SRC_BANGUMI)) }
                        // Arama yanıtı görsel taşımıyorsa eşleşmeye ait detaydan alınır.
                        val root = if (kind == PersonKind.CHARACTER) {
                            runCatching { BangumiApiClient.getCharacter(entity.id) }.getOrNull()
                        } else {
                            runCatching { BangumiApiClient.getPerson(entity.id) }.getOrNull()
                        }
                        bangumiImageOf(root?.optJSONObject("images"))
                            ?.let { return@runCatching listOf(SourceImage(it, SRC_BANGUMI)) }
                    }
                }
            }
            emptyList()
        }.getOrElse { emptyList() }
    }

    // ── TMDB — yalnızca gerçek kişiler (profil fotoğrafları) ────────────────────

    private suspend fun tmdbPersonImages(
        knownId: Int?,
        names: List<String>
    ): List<SourceImage> = withContext(Dispatchers.IO) {
        runCatching {
            var personId = knownId?.takeIf { it > 0 }
            if (personId == null) {
                // Kimliği isimle çöz: ilk sıkı eşleşme kabul edilir (yanlış kişi riski düşük tutulur).
                personId = resolveTmdbPersonId(names) ?: return@runCatching emptyList()
            }
            val apiKey = TmdbApiClient.getActiveApiKey()
            val url = URL("https://api.themoviedb.org/3/person/$personId/images?api_key=$apiKey")
            val response = KitsugiApiBase.executeGetRequest(url) ?: return@runCatching emptyList()
            val profiles = JSONObject(response).optJSONArray("profiles") ?: return@runCatching emptyList()
            val urls = mutableListOf<SourceImage>()
            for (i in 0 until profiles.length()) {
                val path = profiles.optJSONObject(i)?.optNullableString("file_path") ?: continue
                if (path.isBlank()) continue
                urls += SourceImage("https://image.tmdb.org/t/p/w500$path", SRC_TMDB)
                if (urls.size >= MAX_TMDB_PROFILES) break
            }
            urls
        }.getOrElse { emptyList() }
    }

    private suspend fun resolveTmdbPersonId(names: List<String>): Int? {
        if (names.isEmpty()) return null
        val client = TmdbApiClient()
        for (name in names) {
            val results = runCatching { client.searchPerson(name) }.getOrNull().orEmpty()
            for (result in results) {
                // TMDB'nin yetişkin (+18) işaretli kişileri isimle eşleştirmede asla kullanılmaz.
                if (result.isAdult) continue
                if (names.any { nameMatches(it, result.title) }) {
                    return result.malId.takeIf { it > 0 }
                }
            }
        }
        return null
    }

    // ── Sıkı isim eşleştirme ────────────────────────────────────────────────────

    /** Diakritik ve noktalama duyarsız Unicode normalizasyonu; Japonca/Çince adları da korur. */
    internal fun normalizeName(value: String): String = java.text.Normalizer
        .normalize(java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC), java.text.Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** AniList karakter düğümünün yapımlarından herhangi biri +18 ise true (kimliksiz eşleşmede elenir). */
    internal fun hasAdultAniListMedia(node: JSONObject): Boolean {
        val nodes = node.optJSONObject("media")?.optJSONArray("nodes") ?: return false
        for (i in 0 until nodes.length()) {
            if (nodes.optJSONObject(i)?.optBoolean("isAdult", false) == true) return true
        }
        return false
    }

    /**
     * Kimliksiz (isimle) aramada kullanılabilecek kadar ayırt edici mi?
     *  - En az iki kelime (ad + soyad / "Takeshi Gouda") → kabul.
     *  - Tek kelime ise yalnızca Japonca/Çince (CJK) ve en az 3 karakterli ad kabul edilir
     *    (ör. "森田鈴"). Latin tek kelimeli adlar ("Suzu", "Ayane") belirsizdir → reddedilir.
     */
    internal fun isUnambiguousName(value: String): Boolean = isUnambiguousName(value, allowSingleTokenLatin = false)

    /**
     * [isUnambiguousName] örüntüsünün çağıran tarafça DOĞRULANABİLEN aramalar için gevşetilmiş
     * hâli.
     *
     * Kurgusal karakter adlarının büyük bölümü TEK kelimedir ve Latincedir: "Nobita",
     * "Doraemon", "Goku", "Eren", "Luffy", "Spike"... Eski kural bu adları tamamen reddediyordu;
     * sonuç: TMDB/Simkl kaynaklı anime detay sayfalarında (karakter kimliği TMDB kişi kimliği
     * olduğu için) karakter hiçbir kaynaktan aranamıyor, hem kartta hem karakter detay sayfasında
     * görsel hiç gelmiyordu.
     *
     * Bu çağrı yalnızca sonuç BAŞKA BİR KİMLİKLE doğrulanabildiğinde kullanılır:
     *  - karakterin bağlı olduğu yapım (MAL/AniList kimliği) ile çapraz doğrulama, ya da
     *  - arama sonucunda adın BİREBİR eşleşmesi zorunluluğu ([nameMatches]).
     *
     * @param allowSingleTokenLatin tek kelimeli Latin adlara izin verilsin mi.
     */
    internal fun isUnambiguousName(value: String, allowSingleTokenLatin: Boolean): Boolean {
        val normalized = normalizeName(value)
        val tokens = normalized.split(" ").filter { it.isNotBlank() }
        if (tokens.size >= 2) return true
        val single = tokens.firstOrNull() ?: return false
        if (single.any { it.code >= 0x2E80 }) return single.length >= 3
        // Tek kelimeli Latin ad: ancak çağıran doğrulama yapıyorsa kabul edilir.
        return allowSingleTokenLatin && single.length >= MIN_SINGLE_TOKEN_LATIN_NAME
    }

    /** Tek kelimeli Latin adın isimle aramaya sokulabilmesi için gereken en kısa uzunluk. */
    private const val MIN_SINGLE_TOKEN_LATIN_NAME = 4

    /**
     * Sıkı eşleşme: tam normalize eşitlik veya aynı jeton kümesi (ör. "Kagenou Cid" ≡
     * "Cid Kagenou" — Japonca ad sırası farkı). Kısmi/önek eşleşmeler kabul EDİLMEZ
     * (ör. "Yuki" ∉ "Yuki Takeya") — yanlış kişi/karakter görseli riskini dışlar.
     */
    internal fun nameMatches(query: String, candidate: String?): Boolean {
        if (candidate.isNullOrBlank()) return false
        val q = normalizeName(query)
        val c = normalizeName(candidate)
        if (q.isEmpty() || c.isEmpty()) return false
        if (q == c) return true
        val qTokens = q.split(" ").filter { it.isNotBlank() }.toSet()
        val cTokens = c.split(" ").filter { it.isNotBlank() }.toSet()
        return qTokens.size >= 2 && qTokens == cTokens
    }
}
