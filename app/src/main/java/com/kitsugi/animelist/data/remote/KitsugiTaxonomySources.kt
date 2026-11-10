package com.kitsugi.animelist.data.remote

import android.util.Log
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.utils.KitsugiSpoilerRules
import com.kitsugi.animelist.utils.toLatinStudioName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.URL

/**
 * **Detay sayfası tür / etiket / kurum toplayıcısı.**
 *
 * Açılan sayfanın kaynağı ne olursa olsun (AniList, MAL/Jikan, Bangumi, Shikimori, Kitsu,
 * Simkl, TMDB), aynı yapımın **diğer kaynaklardaki** tür, etiket, stüdyo, yapımcı, kanal ve
 * dergi verisi burada toplanır; [KitsugiTaxonomyMerge] ile tek listede birleştirilir.
 *
 * Tasarım kuralları:
 *  • **Ek katmandır, davranış bozmaz.** Her istek `runCatching` + zaman tavanı içindedir;
 *    geciken kaynak atlanır, sayfa asıl veriyle açılır.
 *  • **Paralel.** Kaynaklar `async` ile aynı anda başlar; toplam süre en yavaş kaynak değil
 *    [TOTAL_TIMEOUT_MS] tavanıdır.
 *  • **Önbellekli.** Jikan tarafı [JikanGateway] önbelleğini, AniList tarafı
 *    `KitsugiAniListDetailClient` bellekteki etiket önbelleğini kullanır; ikinci açılışta
 *    ağa çıkılmaz.
 *  • **Kimlik güvenliği.** MAL/Jikan yalnızca 1..99.999.999 aralığındaki gerçek MAL
 *    kimlikleriyle çağrılır. Bangumi'nin 500M+ "stable id"leri ve Shikimori/Kitsu kendi
 *    kimlik uzayları buraya **girmez** (yanlış yapımın türlerinin yapışmasını engeller).
 *  • **Kiril/Çince sızıntısı yok.** Kurum adları Latin ada çevrilir; tür/etiketler
 *    gösterim katmanında çözülür.
 *
 * Toplanan kaynaklar — **altısı da paralel** çalışır, hepsi birleşime katılır:
 *  • **MAL/Jikan** — `genres`, `explicit_genres`, `themes`, `demographics`,
 *    `studios`, `producers`, `licensors`
 *  • **AniList** — `genres`, `tags` (spoiler/rank dahil), `studios` (isMain dahil)
 *  • **Shikimori** — `genres` (`kind`: genre/theme/demographic) + `studios`
 *  • **Kitsu** — `categories` + `animeProductions.producer` (rol: studio/producer/licensor)
 *  • **Bangumi** — başlık eşleşmesiyle bulunan `subject`: `tags` + infobox kurumları
 *    (动画制作 · 製作 · 製作委員会 · 制作協力 · レーベル · 配給 · 播放电视台 · 掲載誌)
 *  • **TMDB** — `genres`, `keywords` (etiket), `production_companies` (stüdyo+yapımcı),
 *    TV'de `networks` (kanal)
 *  • **Simkl** — `genres` + `network` (yayın kanalı). Stüdyo/yapımcı alanı yayınlamaz.
 *
 * Toplam **yedi kaynak** paralel çalışır ve hepsi tek havuzda birleşir.
 */
internal object KitsugiTaxonomySources {

    private const val TAG = "KitsugiTaxonomySources"

    /** Ağ katmanı için makul toplam tavan — detay sayfasının 20 sn'lik zenginleştirme bütçesinin içinde. */
    private const val TOTAL_TIMEOUT_MS = 8_000L

    /** Tek kaynak için üst sınır. */
    private const val SOURCE_TIMEOUT_MS = 5_500L

    private val REAL_MAL_ID = 1..99_999_999

    /**
     * Tek bir kaynaktan toplanan taksonomi parçası. Boş alanlar `emptyList()`'tir; birleştirme
     * sırasında yalnızca dolu alanlar kullanılır.
     */
    data class Slice(
        val genres: List<String> = emptyList(),
        val tags: List<KitsugiTag> = emptyList(),
        val studios: List<KitsugiStudio> = emptyList(),
        val producers: List<KitsugiStudio> = emptyList(),
        val networks: List<KitsugiStudio> = emptyList(),
        val serializations: List<KitsugiStudio> = emptyList(),
        val demographics: List<String> = emptyList(),
        val themes: List<String> = emptyList()
    ) {
        val isEmpty: Boolean
            get() = genres.isEmpty() && tags.isEmpty() && studios.isEmpty() && producers.isEmpty() &&
                networks.isEmpty() && serializations.isEmpty() &&
                demographics.isEmpty() && themes.isEmpty()

        /** Parçayı birleştirme motorunun beklediği [KitsugiMediaDetail] biçimine çevirir. */
        fun toDetail(): KitsugiMediaDetail = KitsugiMediaDetail(
            synopsis = null,
            genres = genres,
            tags = tags,
            studios = studios,
            producers = producers,
            networks = networks,
            serializations = serializations,
            demographics = demographics,
            themes = themes
        )
    }

    /** İki parçayı birleştirir (aynı kaynak içinde birden çok bölüm geldiyse). */
    fun Slice.plus(other: Slice): Slice = Slice(
        genres = (genres + other.genres).distinct(),
        tags = tags + other.tags,
        studios = studios + other.studios,
        producers = producers + other.producers,
        networks = networks + other.networks,
        serializations = serializations + other.serializations,
        demographics = (demographics + other.demographics).distinct(),
        themes = (themes + other.themes).distinct()
    )

    /**
     * Tüm ikincil kaynaklardan taksonomi parçası toplar.
     *
     * @param malId gerçek MAL kimliği (1..99.999.999) — değilse MAL/Jikan atlanır.
     * @param aniListId AniList kimliği (100M+ "stable" biçimi de kabul edilir).
     * @param mediaType tür; Manga için MAL ucu `manga` olur.
     */
    suspend fun collect(
        malId: Int? = null,
        aniListId: Int? = null,
        mediaType: MediaType = MediaType.Anime,
        tmdbId: Int? = null,
        titles: List<String?> = emptyList(),
        year: Int? = null,
        /** Bangumi sayfası açılırken kendi verisi zaten elde: gereksiz arama yapılmaz. */
        includeBangumi: Boolean = true
    ): Slice {
        val safeMalId = malId?.takeIf { it in REAL_MAL_ID }
        val safeAniListId = aniListId?.takeIf { it > 0 }
        val safeTmdbId = tmdbId?.takeIf { it > 0 }
        val cleanTitles = titles.mapNotNull { it?.trim()?.takeIf { t -> t.length >= 2 } }
            .distinct()
        if (safeMalId == null && safeAniListId == null &&
            (safeTmdbId == null || mediaType == MediaType.Manga) && cleanTitles.isEmpty()
        ) {
            return Slice()
        }

        val slice = withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
            coroutineScope {
                val jikan = async(Dispatchers.IO) {
                    withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                        runCatching { fetchJikan(safeMalId, mediaType) }.getOrNull()
                    }
                }
                val aniList = async(Dispatchers.IO) {
                    withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                        runCatching {
                            KitsugiAniListDetailClient.fetchTagsAndAiringInfo(
                                aniListId = safeAniListId,
                                malId = safeMalId,
                                mediaType = mediaType
                            )?.let { info ->
                                Slice(
                                    genres = info.genres,
                                    tags = info.tags,
                                    studios = info.studios
                                )
                            }
                        }.getOrNull()
                    }
                }
                // Shikimori ve Kitsu kendi kimlik uzaylarını kullanır; MAL kimliğinden
                // deterministik çözüm yapılır (Shikimori yanıtı doğrulanır, Kitsu `mappings`).
                val shikimori = async(Dispatchers.IO) {
                    withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                        runCatching { fetchShikimori(safeMalId, mediaType) }.getOrNull()
                    }
                }
                val kitsu = async(Dispatchers.IO) {
                    withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                        runCatching { fetchKitsu(safeMalId, mediaType) }.getOrNull()
                    }
                }
                // Bangumi ve TMDB başlık/kimlik üzerinden çözülür; ikisi de kurum
                // (stüdyo/yapımcı/kanal/dergi) katkısı yapar.
                val bangumi = async(Dispatchers.IO) {
                    if (!includeBangumi) return@async null
                    withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                        runCatching { fetchBangumi(cleanTitles, year, mediaType) }.getOrNull()
                    }
                }
                val tmdb = async(Dispatchers.IO) {
                    withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                        runCatching { fetchTmdb(safeTmdbId, mediaType) }.getOrNull()
                    }
                }
                // Simkl: tür + `network` (kanal). Stüdyo/yapımcı alanı yok.
                val simkl = async(Dispatchers.IO) {
                    withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                        runCatching { fetchSimkl(cleanTitles, year, mediaType) }.getOrNull()
                    }
                }
                val parts = linkedMapOf(
                    "jikan" to jikan.await(),
                    "anilist" to aniList.await(),
                    "shikimori" to shikimori.await(),
                    "kitsu" to kitsu.await(),
                    "bangumi" to bangumi.await(),
                    "tmdb" to tmdb.await(),
                    "simkl" to simkl.await()
                )
                if (parts.values.all { it == null }) return@coroutineScope null
                // Kaynak bazlı sayaç: cihaz günlüğünde hangi kaynağın ne kadar veri
                // verdiği görülebilsin (teşhis ve regresyon takibi).
                Log.d(
                    TAG,
                    "Kaynak bazlı: " + parts.entries.joinToString(" | ") { (name, part) ->
                        if (part == null) "$name=yok"
                        else "$name=${part.genres.size}t/${part.tags.size}e/" +
                            "${part.studios.size + part.producers.size}k"
                    }
                )
                parts.values.filterNotNull()
                    .fold(Slice()) { acc, part -> if (acc.isEmpty) part else acc.plus(part) }
            }
        }
        if (slice == null || slice.isEmpty) return Slice()
        Log.d(TAG, "Taksonomi toplandı: tür=${slice.genres.size} etiket=${slice.tags.size} " +
            "stüdyo=${slice.studios.size} yapımcı=${slice.producers.size}")
        return slice
    }

    // ── Shikimori (Rusça) ──────────────────────────────────────────────────────

    /**
     * Shikimori detayından tür ve stüdyo bilgisini okur.
     *
     * **Kimlik güvenliği:** Shikimori kimliği MAL kimliği değildir. İstek MAL kimliğiyle
     * yapılır (Shikimori veritabanı MAL'dan türediği için çoğu kayıtta aynıdır) ve yanıttaki
     * `myanimelist_id` farklıysa **veri kullanılmaz** — yanlış yapımın türleri yapışmasın.
     *
     * `kind` alanı korunur: `genre` → tür, `theme` → tema etiketi, `demographic` → hedef kitle.
     * Rusça adlar gösterim katmanında çözülür (`RussianTaxonomyDictionary`).
     */
    private suspend fun fetchShikimori(malId: Int?, mediaType: MediaType): Slice? {
        if (malId == null) return null
        val endpoint = if (mediaType == MediaType.Manga) "mangas" else "animes"
        val body = KitsugiApiBase.executeGetRequestResilient(
            URL("https://shikimori.io/api/$endpoint/$malId")
        ) ?: return null
        val data = runCatching { JSONObject(body) }.getOrNull() ?: return null

        val realMalId = data.optInt("myanimelist_id", 0)
        if (realMalId > 0 && realMalId != malId) return null

        val genres = mutableListOf<String>()
        val themes = mutableListOf<String>()
        val demographics = mutableListOf<String>()
        val tags = mutableListOf<KitsugiTag>()

        data.optJSONArray("genres")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").trim()
                if (name.isEmpty()) continue
                when (obj.optString("kind", "").lowercase()) {
                    "theme" -> {
                        themes.add(name)
                        genres.add(name)
                    }
                    "demographic" -> {
                        demographics.add(name)
                        genres.add(name)
                    }
                    else -> genres.add(name)
                }
                tags.add(
                    KitsugiTag(
                        id = obj.optInt("id", 0).takeIf { it > 0 },
                        name = name,
                        isSpoiler = KitsugiSpoilerRules.isIntrinsicSpoiler(name),
                        source = "shikimori"
                    )
                )
            }
        }

        val studios = mutableListOf<KitsugiStudio>()
        data.optJSONArray("studios")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").trim()
                if (name.isEmpty()) continue
                studios.add(
                    KitsugiStudio(
                        id = obj.optInt("id", 0).takeIf { it > 0 } ?: 0,
                        name = name.toLatinStudioName(),
                        isMain = true,
                        source = "shikimori",
                        role = StudioRole.STUDIO
                    )
                )
            }
        }

        // Lisans verenler (anime) ve yayınevleri (manga) — Shikimori'nin kurum alanları.
        val licensors = KitsugiShikimoriClient.parseShikimoriOrgArray(
            data.optJSONArray("licensors"),
            StudioRole.LICENSOR
        )
        val publishers = KitsugiShikimoriClient.parseShikimoriOrgArray(
            data.optJSONArray("publishers"),
            StudioRole.PUBLISHER
        )
        val producerOrgs = (licensors + publishers)
            .filter { org -> studios.none { it.name.equals(org.name, ignoreCase = true) } }

        if (genres.isEmpty() && studios.isEmpty() && producerOrgs.isEmpty()) return null
        return Slice(
            genres = genres.distinct(),
            tags = KitsugiSpoilerRules.normalizeAndMergeTags(tags),
            studios = studios,
            producers = producerOrgs,
            themes = themes.distinct(),
            demographics = demographics.distinct()
        )
    }

    // ── Kitsu ──────────────────────────────────────────────────────────────────

    /**
     * Kitsu detayından kategori ve **yapım şirketlerini** okur.
     *
     * Kitsu, MAL kimliğini `mappings` tablosuyla deterministik olarak çözer
     * ([com.kitsugi.animelist.data.auth.KitsuApiClient.lookupKitsuIdByExternalMapping]).
     * Kurumlar `animeProductions.producer` ilişkisinden gelir; `role` alanı
     * `studio` / `producer` / `licensor` olarak korunur. Bu katman olmadan Kitsu kaynaklı
     * sayfalarda **hiç stüdyo görünmüyordu**.
     */
    private suspend fun fetchKitsu(malId: Int?, mediaType: MediaType): Slice? {
        if (malId == null) return null
        val isManga = mediaType == MediaType.Manga
        val site = if (isManga) "myanimelist/manga" else "myanimelist/anime"
        val kitsuId = runCatching {
            com.kitsugi.animelist.data.auth.KitsuApiClient
                .lookupKitsuIdByExternalMapping(site, malId)
        }.getOrNull() ?: return null
        if (kitsuId <= 0) return null

        val endpoint = if (isManga) "manga" else "anime"
        val base = "${KitsuApiHost.base}/$endpoint/$kitsuId"
        // Manga tarafında yapım ilişkisi garanti değil: kategori ile başla, anime ise
        // yapım şirketlerini de iste; uç reddederse (400) ilişkisiz isteğe düşülür.
        val withProductions = if (isManga) {
            "$base?include=categories,mangaProductions.producer"
        } else {
            "$base?include=categories,animeProductions.producer"
        }
        val body = KitsugiApiBase.executeGetRequestResilient(URL(withProductions))
            ?: KitsugiApiBase.executeGetRequestResilient(URL("$base?include=categories"))
            ?: return null
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val included = root.optJSONArray("included") ?: return null

        val genres = mutableListOf<String>()
        val tags = mutableListOf<KitsugiTag>()
        val studios = mutableListOf<KitsugiStudio>()
        val producers = mutableListOf<KitsugiStudio>()

        // 1) Kategoriler (tür + etiket)
        val producerNames = HashMap<String, Pair<String, String>>()   // id -> (name, role)
        val producerRefs = mutableListOf<Pair<String, String>>()      // (producerId, role)

        for (i in 0 until included.length()) {
            val inc = included.optJSONObject(i) ?: continue
            val type = inc.optString("type", "")
            val attrs = inc.optJSONObject("attributes")
            when (type) {
                "categories" -> {
                    val title = attrs?.optString("title", "")?.trim().orEmpty()
                    if (title.isEmpty()) continue
                    genres.add(title)
                    tags.add(
                        KitsugiTag(
                            id = inc.optString("id", "").toIntOrNull(),
                            name = title,
                            isSpoiler = KitsugiSpoilerRules.isIntrinsicSpoiler(title),
                            source = "kitsu"
                        )
                    )
                }
                "animeProductions", "mangaProductions" -> {
                    val role = attrs?.optString("role", "").orEmpty().trim()
                    val producerId = inc.optJSONObject("relationships")
                        ?.optJSONObject("producer")
                        ?.optJSONObject("data")
                        ?.optString("id", "")
                        .orEmpty()
                    if (producerId.isNotEmpty()) producerRefs.add(producerId to role)
                }
                "producers" -> {
                    val name = attrs?.optString("name", "")?.trim().orEmpty()
                    if (name.isEmpty()) continue
                    producerNames[inc.optString("id", "")] = name to ""
                }
            }
        }

        // 2) Yapım şirketleri (rol bilgisiyle)
        for ((producerId, role) in producerRefs) {
            val name = producerNames[producerId]?.first ?: continue
            val studioRole = when (role.lowercase()) {
                "studio" -> StudioRole.STUDIO
                "licensor" -> StudioRole.LICENSOR
                "publisher" -> StudioRole.PUBLISHER
                "magazine" -> StudioRole.MAGAZINE
                "network" -> StudioRole.NETWORK
                else -> StudioRole.PRODUCER
            }
            val org = KitsugiStudio(
                id = producerId.toIntOrNull() ?: 0,
                name = name.toLatinStudioName(),
                isMain = studioRole == StudioRole.STUDIO,
                source = "kitsu",
                role = studioRole
            )
            if (studioRole == StudioRole.STUDIO) studios.add(org) else producers.add(org)
        }

        if (genres.isEmpty() && studios.isEmpty() && producers.isEmpty()) return null
        return Slice(
            genres = genres.distinct(),
            tags = KitsugiSpoilerRules.normalizeAndMergeTags(tags),
            studios = studios,
            producers = producers
        )
    }

    // ── MAL / Jikan ────────────────────────────────────────────────────────────

    /**
     * MAL/Jikan detayından **yalnızca taksonomi** alanlarını okur.
     *
     * Ağır detay istemcisi ([KitsugiMalDetailClient]) karakter/ekip/video/şarkı uçlarına da
     * gider; burada tek ve önbellekli `anime|manga/{id}` yanıtı yeterlidir.
     */
    private suspend fun fetchJikan(malId: Int?, mediaType: MediaType): Slice? {
        if (malId == null) return null
        val path = if (mediaType == MediaType.Manga) "manga" else "anime"
        val url = "https://api.jikan.moe/v4/$path/$malId"
        val body = when (val result = JikanGateway.fetch(url)) {
            is JikanResult.Ok -> result.body
            else -> null
        } ?: return null

        val data = JSONObject(body).optJSONObject("data") ?: return null
        val genres = mutableListOf<String>()
        val themes = mutableListOf<String>()
        val demographics = mutableListOf<String>()
        val tags = mutableListOf<KitsugiTag>()

        fun readNamedArray(name: String, collectTo: MutableList<String>, tagSource: String? = null) {
            val arr = data.optJSONArray(name) ?: return
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val value = obj.optString("name", "").trim()
                if (value.isEmpty()) continue
                collectTo.add(value)
                if (tagSource != null) {
                    tags.add(
                        KitsugiTag(
                            id = obj.optInt("mal_id", 0).takeIf { it > 0 },
                            name = value,
                            isSpoiler = KitsugiSpoilerRules.isIntrinsicSpoiler(value),
                            source = tagSource
                        )
                    )
                }
            }
        }

        readNamedArray("genres", genres, tagSource = null)
        readNamedArray("explicit_genres", genres, tagSource = null)
        readNamedArray("themes", themes, tagSource = "mal")
        readNamedArray("demographics", demographics, tagSource = "mal")
        genres.addAll(themes)
        genres.addAll(demographics)

        fun readOrgs(name: String, role: StudioRole): List<KitsugiStudio> {
            val arr = data.optJSONArray(name) ?: return emptyList()
            val out = ArrayList<KitsugiStudio>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val value = obj.optString("name", "").trim()
                if (value.isEmpty()) continue
                out.add(
                    KitsugiStudio(
                        id = obj.optInt("mal_id", 0).takeIf { it > 0 } ?: 0,
                        name = value.toLatinStudioName(),
                        isMain = role == StudioRole.STUDIO,
                        source = "mal",
                        role = role
                    )
                )
            }
            return out
        }

        val studios = readOrgs("studios", StudioRole.STUDIO)
        val producers = readOrgs("producers", StudioRole.PRODUCER)
            .filter { candidate -> studios.none { it.name.equals(candidate.name, ignoreCase = true) } }
        val licensors = readOrgs("licensors", StudioRole.LICENSOR)
        // Manga tarafı: MAL/Jikan `serializations` (dergi), `publishers` (yayınevi) ve
        // `authors` (yazar) döndürür. Bunlar daha önce çapraz toplayıcıda okunmuyordu;
        // MAL kaynaklı manga sayfalarında dergi/yayınevi listesi boş kalıyordu.
        val serializations = readOrgs("serializations", StudioRole.MAGAZINE)
        val publishers = readOrgs("publishers", StudioRole.PUBLISHER)

        return Slice(
            genres = genres.distinct(),
            tags = KitsugiSpoilerRules.normalizeAndMergeTags(tags),
            studios = studios,
            // Yayınevleri "Yayıncılar"da, dergiler "Dergiler"de görünür (rol ayrımı korunur).
            producers = producers + licensors + publishers,
            serializations = serializations,
            demographics = demographics,
            themes = themes
        )
    }

    // ── Bangumi (başlık eşleşmesi) ─────────────────────────────────────────────

    /**
     * Bangumi'yi **başlık eşleşmesiyle** bulur ve infobox kurumlarını okur.
     *
     * Bangumi, kurum verisi en zengin kaynaktır (动画制作 · 製作 · 製作委員会 · 制作協力 ·
     * レーベル · 配給 · 播放电视台 · 掲載誌). Ancak MAL kimliğiyle doğrudan açılmaz;
     * bu yüzden başlık aranır ve **normalize edilmiş tam başlık eşleşmesi** (varsa yıl
     * toleransı ±1) doğrulanmadan veri kullanılmaz — yanlış yapımın etiketleri yapışmasın.
     */
    private suspend fun fetchBangumi(
        titles: List<String>,
        year: Int?,
        mediaType: MediaType
    ): Slice? {
        if (titles.isEmpty()) return null
        val typeId = if (mediaType == MediaType.Manga) 1 else 2
        val hits = LinkedHashMap<Int, com.kitsugi.animelist.data.auth.BangumiApiClient.BangumiSubject>()
        for (title in titles.take(3)) {
            val query = runCatching { KitsugiBangumiDetailClient.sanitizeQuery(title) }.getOrNull()
            if (query.isNullOrBlank() || query.length < 2) continue
            val page = runCatching {
                com.kitsugi.animelist.data.auth.BangumiApiClient.searchSubjects(
                    keyword = query,
                    types = listOf(typeId),
                    limit = 8
                )
            }.getOrNull() ?: continue
            page.data.forEach { hits[it.id] = it }
            if (hits.isNotEmpty()) break
        }
        if (hits.isEmpty()) return null

        val wanted = titles
            .map { KitsugiBangumiDetailClient.normalizeTitle(it) }
            .filter { it.length >= 2 }
            .toHashSet()
        if (wanted.isEmpty()) return null

        val subject = hits.values.firstOrNull { candidate ->
            val names = listOf(candidate.name, candidate.nameCn)
                .map { KitsugiBangumiDetailClient.normalizeTitle(it) }
                .filter { it.length >= 2 }
            val titleOk = names.any { it in wanted }
            val yearOk = if (year == null || candidate.date == null) {
                true
            } else {
                val found = Regex("\\d{4}").find(candidate.date)?.value?.toIntOrNull()
                found == null || Math.abs(found - year) <= 1
            }
            titleOk && yearOk
        } ?: return null

        val full = runCatching { KitsugiBangumiDetailClient.loadSubject(subject.id) }.getOrNull()
            ?: subject
        val detail = runCatching {
            KitsugiBangumiDetailClient.buildNativeDetail(full, mediaType)
        }.getOrNull() ?: return null

        if (detail.tags.isEmpty() && detail.studios.isEmpty() &&
            detail.producers.isEmpty() && detail.networks.isEmpty()
        ) {
            return null
        }
        return Slice(
            genres = detail.genres,
            tags = detail.tags,
            studios = detail.studios,
            producers = detail.producers,
            networks = detail.networks,
            serializations = detail.serializations,
            themes = detail.themes,
            demographics = detail.demographics
        )
    }

    // ── Simkl ──────────────────────────────────────────────────────────────────

    /**
     * Simkl'den tür ve **yayın kanalını** (`network`) okur.
     *
     * Simkl API'si stüdyo/yapımcı alanı yayınlamaz, bu yüzden kurum katkısı yalnızca
     * kanaldır. Kimlik, başlık aramasıyla bulunur; **normalize başlık eşleşmesi**
     * (ve varsa yıl toleransı ±1) doğrulanmadan veri kullanılmaz.
     */
    private suspend fun fetchSimkl(
        titles: List<String>,
        year: Int?,
        mediaType: MediaType
    ): Slice? {
        if (titles.isEmpty() || mediaType == MediaType.Manga) return null
        val type = when (mediaType) {
            MediaType.Movie -> "movie"
            MediaType.TvShow -> "tv"
            else -> "anime"
        }
        val wanted = titles
            .map { KitsugiSpoilerRules.normalizeLabel(it) }
            .filter { it.length >= 3 }
            .toHashSet()
        if (wanted.isEmpty()) return null

        val hits = runCatching {
            SimklApiClient().search(titles.first(), type = type, limit = 6)
        }.getOrNull()
        if (hits.isNullOrEmpty()) return null

        val hit = hits.firstOrNull { candidate ->
            val norm = KitsugiSpoilerRules.normalizeLabel(candidate.title)
            val titleOk = norm.isNotEmpty() &&
                (norm in wanted || wanted.any { w -> norm.contains(w) || w.contains(norm) })
            val candidateYear = candidate.year
            val targetYear = year
            val yearOk = if (targetYear == null || candidateYear == null) {
                true
            } else {
                Math.abs(candidateYear - targetYear) <= 1
            }
            titleOk && yearOk
        } ?: return null

        // Simkl arama sonucunda `malId` alanı Simkl kimliğini taşır (MAL kimliği `realMalId`).
        val simklId = hit.malId
        if (simklId <= 0) return null
        val obj = KitsugiSimklDetailClient.fetchSimklRawJson(simklId, mediaType) ?: return null

        val genres = mutableListOf<String>()
        obj.optJSONArray("genres")?.let { arr ->
            for (i in 0 until arr.length()) {
                val name = arr.optString(i, "").trim()
                if (name.isNotEmpty()) genres.add(name)
            }
        }

        val tags = genres
            .filter {
                !com.kitsugi.animelist.ui.screens.search.CanonicalFilterBridge
                    .isOfficialAniListGenre(it)
            }
            .map {
                KitsugiTag(
                    name = it,
                    isSpoiler = KitsugiSpoilerRules.isIntrinsicSpoiler(it),
                    source = "simkl"
                )
            }

        val networks = mutableListOf<KitsugiStudio>()
        fun addNetwork(rawName: String?) {
            val name = rawName?.trim().orEmpty()
            if (name.isEmpty() || name == "null") return
            if (networks.any { it.name.equals(name, ignoreCase = true) }) return
            networks.add(
                KitsugiStudio(
                    id = 0,
                    name = name,
                    isMain = false,
                    source = "simkl",
                    role = StudioRole.NETWORK
                )
            )
        }
        addNetwork(obj.optString("network", ""))
        obj.optJSONArray("networks")?.let { arr ->
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i)
                addNetwork(if (item != null) item.optString("name", "") else arr.optString(i, ""))
            }
        }

        if (genres.isEmpty() && networks.isEmpty()) return null
        return Slice(
            genres = genres.distinct(),
            tags = KitsugiSpoilerRules.normalizeAndMergeTags(tags),
            networks = networks
        )
    }

    // ── TMDB ───────────────────────────────────────────────────────────────────

    /**
     * TMDB detayından tür, anahtar kelime (etiket), yapım şirketleri (stüdyo + yapımcı)
     * ve TV kanallarını okur.
     *
     * `production_companies` ilk kaydı **stüdyo**, kalanı **yapımcı** rolündedir; `tv`
     * tarafında `networks` kanal olarak eklenir. Manga için TMDB çağrılmaz.
     */
    private suspend fun fetchTmdb(tmdbId: Int?, mediaType: MediaType): Slice? {
        if (tmdbId == null || tmdbId <= 0) return null
        if (mediaType == MediaType.Manga) return null
        val apiKey = runCatching { TmdbApiClient.getActiveApiKey() }.getOrNull().orEmpty()
        if (apiKey.isBlank()) return null
        val isMovie = mediaType == MediaType.Movie
        val path = if (isMovie) "movie" else "tv"
        val url = "https://api.themoviedb.org/3/$path/$tmdbId" +
            "?api_key=$apiKey&language=tr-TR&append_to_response=keywords"
        val body = KitsugiApiBase.executeGetRequestResilient(URL(url)) ?: return null
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null

        val genres = mutableListOf<String>()
        root.optJSONArray("genres")?.let { arr ->
            for (i in 0 until arr.length()) {
                val name = arr.optJSONObject(i)?.optString("name", "").orEmpty().trim()
                if (name.isNotEmpty()) genres.add(name)
            }
        }

        val tags = mutableListOf<KitsugiTag>()
        val keywordArray = root.optJSONObject("keywords")?.let { kw ->
            kw.optJSONArray("keywords") ?: kw.optJSONArray("results")
        }
        keywordArray?.let { arr ->
            for (i in 0 until arr.length()) {
                val name = arr.optJSONObject(i)?.optString("name", "").orEmpty().trim()
                if (name.isEmpty()) continue
                tags.add(
                    KitsugiTag(
                        id = arr.optJSONObject(i)?.let { obj -> obj.optInt("id", 0).takeIf { it > 0 } },
                        name = name,
                        isSpoiler = KitsugiSpoilerRules.isIntrinsicSpoiler(name),
                        source = "tmdb"
                    )
                )
            }
        }

        val studios = mutableListOf<KitsugiStudio>()
        val producers = mutableListOf<KitsugiStudio>()
        root.optJSONArray("production_companies")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").trim()
                if (name.isEmpty()) continue
                val logo = obj.optString("logo_path", "").trim()
                studios.add(
                    KitsugiStudio(
                        id = obj.optInt("id", 0).takeIf { it > 0 } ?: 0,
                        name = name,
                        isMain = i == 0,
                        source = "tmdb",
                        role = if (i == 0) StudioRole.STUDIO else StudioRole.PRODUCER,
                        imageUrl = if (logo.isNotEmpty()) "https://image.tmdb.org/t/p/w200$logo" else null
                    )
                )
            }
        }
        // İlk şirket stüdyodur; kalanı yapımcı listesine taşınır (aynı kayıt iki rolde kalır).
        if (studios.size > 1) {
            producers.addAll(studios.drop(1).map { it.copy(role = StudioRole.PRODUCER) })
            studios.subList(1, studios.size).clear()
        }

        val networks = mutableListOf<KitsugiStudio>()
        root.optJSONArray("networks")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").trim()
                if (name.isEmpty()) continue
                networks.add(
                    KitsugiStudio(
                        id = obj.optInt("id", 0).takeIf { it > 0 } ?: 0,
                        name = name,
                        isMain = false,
                        source = "tmdb",
                        role = StudioRole.NETWORK
                    )
                )
            }
        }

        if (genres.isEmpty() && tags.isEmpty() && studios.isEmpty() &&
            producers.isEmpty() && networks.isEmpty()
        ) {
            return null
        }
        return Slice(
            genres = genres.distinct(),
            tags = KitsugiSpoilerRules.normalizeAndMergeTags(tags),
            studios = studios,
            producers = producers,
            networks = networks
        )
    }

}
