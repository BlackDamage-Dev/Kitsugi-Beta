package com.kitsugi.animelist.data.fandom

import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Fandom MediaWiki API ve topluluk araması çağrıları. */
internal object FandomApi {

    private const val COMMUNITY_SEARCH = "https://services.fandom.com/unified-search/community-search"
    private const val MAX_TITLES_PER_REQUEST = 50

    /**
     * "İlgili maddeler" kartı için özet uzunluğu. Kart 2 satır gösterir; canlı ölçümde
     * 20 aday × 1200 karakter özet gereksiz büyük bir yanıt üretiyordu.
     */
    private const val RELATED_EXCERPT_CHARS = 320

    /** `continue`/`excontinue` turları için güvenlik sınırı (her tur 20'ye kadar sayfa getirir). */
    private const val MAX_PREVIEW_ROUNDS = 12

    suspend fun siteInfo(wikiId: String): FandomSiteInfo? {
        val url = FandomUrls.apiQuery(
            wikiId,
            listOf(
                "action" to "query",
                "meta" to "siteinfo",
                "siprop" to "general|statistics",
                "format" to "json",
                "formatversion" to "2"
            )
        ) ?: return null
        val body = FandomHttp.get(url)
        val info = FandomApiParsers.parseSiteInfo(wikiId, body)
        if (info == null && body != null) FandomHttp.markFailure()
        return info
    }

    /**
     * Başlıkları birebir sorgular (wiki'nin kendi yönlendirmeleriyle); bilgi kutusu JSON'u
     * ve kısa özet de aynı istekte gelir. 50'den fazla başlık parçalara bölünür.
     */
    suspend fun lookupTitles(
        wikiId: String,
        titles: List<String>,
        withTurkishLink: Boolean = false
    ): Map<String, FandomTitleLookup> {
        val clean = titles.map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= 200 && !it.contains('|') }
            .distinct()
        if (clean.isEmpty()) return emptyMap()
        val result = LinkedHashMap<String, FandomTitleLookup>()
        for (chunk in clean.chunked(MAX_TITLES_PER_REQUEST)) {
            val params = titleLookupParams(wikiId, chunk.joinToString("|"), withTurkishLink)
            val url = FandomUrls.apiQuery(wikiId, params) ?: continue
            var body = FandomHttp.get(url)
            if (body != null && !FandomApiParsers.hasValidPageQuery(body)) {
                // Older wikis can reject pageimages/TextExtracts with HTTP 200 + JSON error.
                // Retry the essential pageprops/redirect query before treating pages as absent.
                val leanUrl = FandomUrls.apiQuery(
                    wikiId, titleLookupParams(wikiId, chunk.joinToString("|"), withTurkishLink, minimal = true)
                )
                body = leanUrl?.let { FandomHttp.get(it) }
                if (body == null || !FandomApiParsers.hasValidPageQuery(body)) FandomHttp.markFailure()
            }
            result.putAll(FandomApiParsers.parseTitleLookups(chunk, body))
        }
        return result
    }

    /**
     * Verilen başlıkların Wikidata varlık kimlikleri (`pageprops.wikibase_item`).
     *
     * Kimlik denetimi: başlık benzerliği tek başına yeterli değildir — aynı adı taşıyan başka
     * bir yapımın maddesi "ilgili" sayılıp gösterilebilir. Yalnızca Vikipedi maddeleri için
     * çağrılır; Fandom isteklerinin parametreleri DEĞİŞMEZ (Fandom'da bu alan yoktur).
     */
    suspend fun pageWikidataItems(wikiId: String, titles: List<String>): Map<String, String> {
        val clean = titles.map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= 200 && !it.contains('|') }
            .distinct()
        if (clean.isEmpty()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        for (chunk in clean.chunked(MAX_TITLES_PER_REQUEST)) {
            val url = FandomUrls.apiQuery(
                wikiId,
                listOf(
                    "action" to "query",
                    "titles" to chunk.joinToString("|"),
                    "redirects" to "1",
                    "prop" to "pageprops",
                    "ppprop" to "wikibase_item",
                    "format" to "json",
                    "formatversion" to "2"
                )
            ) ?: continue
            FandomApiParsers.parseTitleLookups(chunk, FandomHttp.get(url)).forEach { (requested, lookup) ->
                lookup.wikidataItem?.let { qid -> result[requested] = qid }
            }
        }
        return result
    }

    /** TextExtracts is available on Wikipedia, not on Fandom's MediaWiki fork. Sending
     * `prop=extracts` to Fandom can invalidate the *whole* query, even for existing pages. */
    internal fun titleLookupParams(
        wikiId: String,
        titles: String,
        withTurkishLink: Boolean,
        minimal: Boolean = false
    ): List<Pair<String, String>> = buildList {
        add("action" to "query")
        add("titles" to titles)
        add("redirects" to "1")
        add("prop" to buildList {
            add("pageprops")
            if (withTurkishLink) add("langlinks")
            if (!minimal) add("pageimages")
            if (!minimal && FandomUrls.isWikipedia(wikiId)) add("extracts")
        }.joinToString("|"))
        add("ppprop" to "infoboxes|fandomdescription|disambiguation")
        if (!minimal) {
            add("piprop" to "thumbnail")
            add("pithumbsize" to "480")
            add("pilimit" to MAX_TITLES_PER_REQUEST.toString())
            if (FandomUrls.isWikipedia(wikiId)) {
                add("pilicense" to "any")
            }
        }
        if (!minimal && FandomUrls.isWikipedia(wikiId)) {
            add("exintro" to "1")
            add("explaintext" to "1")
            add("exchars" to "1200")
            add("exlimit" to "20")
        }
        if (withTurkishLink) {
            add("lllang" to "tr")
            add("llprop" to "url")
        }
        add("format" to "json")
        add("formatversion" to "2")
    }

    /**
     * Önizleme modülleri. `description` yalnızca Vikipedi'de istenir: yanıt, maddenin
     * ne olduğunu söyleyen kısa tanımı verir ("Kuzey Amerika'da bir ülke") ve "İlgili
     * maddeler" süzgeci bunu özet metinden daha güvenilir okur. Fandom'da bu modül
     * bulunmadığından istek reddedilmesin diye eklenmez.
     */
    private fun previewProps(wikiId: String): String =
        if (FandomUrls.isWikipedia(wikiId)) "pageprops|pageimages|extracts|description"
        else "pageprops|pageimages"

    private fun excerptParams(wikiId: String, chars: Int = 1200): List<Pair<String, String>> =
        if (FandomUrls.isWikipedia(wikiId)) listOf(
            "exintro" to "1", "explaintext" to "1", "exchars" to chars.toString()
        ) else emptyList()
    /** Lead image plus the page's full MediaWiki file list and attribution metadata. */
    suspend fun pageImages(wikiId: String, title: String, maxImages: Int = 300): List<FandomImage> {
        val fileTitles = LinkedHashSet<String>()
        var leadImage: FandomImage? = null
        var imageContinue: String? = null
        var generalContinue: String? = null
        var pagesFetched = 0
        val limit = maxImages.coerceIn(1, 500)
        do {
            val params = mutableListOf(
                "action" to "query",
                "titles" to title,
                "prop" to "images|pageimages",
                "imlimit" to "50",
                "piprop" to "thumbnail",
                "pithumbsize" to "1200",
                "format" to "json",
                "formatversion" to "2"
            )
            if (FandomUrls.isWikipedia(wikiId)) {
                params += "pilicense" to "any"
            }
            imageContinue?.let { params += "imcontinue" to it }
            generalContinue?.let { params += "continue" to it }
            val url = FandomUrls.apiQuery(wikiId, params) ?: break
            val batch = FandomApiParsers.parsePageImageBatch(FandomHttp.get(url), title)
            if (leadImage == null) leadImage = batch.leadImage
            batch.fileTitles.forEach { fileTitles += it }
            imageContinue = batch.continueToken
            generalContinue = batch.generalContinueToken
            pagesFetched += batch.fileTitles.size
        } while (!imageContinue.isNullOrBlank() && pagesFetched < limit)

        val details = mutableListOf<FandomImage>()
        for (chunk in fileTitles.take(limit).chunked(MAX_TITLES_PER_REQUEST)) {
            val url = FandomUrls.apiQuery(
                wikiId,
                listOf(
                    "action" to "query",
                    "titles" to chunk.joinToString("|"),
                    "prop" to "imageinfo",
                    "iiprop" to "url|extmetadata",
                    "iiurlwidth" to "1200",
                    "format" to "json",
                    "formatversion" to "2"
                )
            ) ?: continue
            details += FandomApiParsers.parseImageInfo(FandomHttp.get(url))
        }
        val leadIdentity = leadImage?.url?.let(::imageIdentity)
        val detailedLead = leadIdentity?.let { identity -> details.firstOrNull { imageIdentity(it.url) == identity } }
        val preferredLead = detailedLead?.let { image ->
            image.copy(caption = image.caption ?: leadImage?.caption)
        } ?: leadImage
        return (listOfNotNull(preferredLead) + details.filterNot { imageIdentity(it.url) == leadIdentity })
            .distinctBy { imageIdentity(it.url) }
    }

    private fun imageIdentity(url: String): String =
        url.substringBefore("/revision/latest").substringBefore('?').trimEnd('/')

    suspend fun sections(wikiId: String, title: String): List<FandomSectionRef>? {
        val url = FandomUrls.apiQuery(
            wikiId,
            listOf(
                "action" to "parse",
                "page" to title,
                "prop" to "sections",
                "redirects" to "1",
                "format" to "json",
                "formatversion" to "2"
            )
        ) ?: return null
        val body = FandomHttp.get(url)
        val sections = FandomApiParsers.parseSections(body)
        if (body != null && sections == null) FandomHttp.markFailure()
        return sections
    }

    suspend fun sectionHtml(wikiId: String, title: String, index: Int): String? {
        val url = FandomUrls.apiQuery(
            wikiId,
            listOf(
                "action" to "parse",
                "page" to title,
                "section" to index.toString(),
                "prop" to "text",
                "redirects" to "1",
                "disableeditsection" to "1",
                "disablelimitreport" to "1",
                "format" to "json",
                "formatversion" to "2"
            )
        ) ?: return null
        return FandomApiParsers.parseParsedText(FandomHttp.get(url))
    }

    /**
     * Kategori adaylarının varlığı ve sayfa sayısı — 50 başlıklı gruplar halinde toplu
     * (`prop=categoryinfo`). Anahtarlar yönlendirmeler çözüldükten sonraki başlıklardır.
     */
    suspend fun categoryInfo(wikiId: String, categoryTitles: List<String>): Map<String, Int> {
        val clean = categoryTitles.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (clean.isEmpty()) return emptyMap()
        val result = LinkedHashMap<String, Int>()
        for (chunk in clean.chunked(MAX_TITLES_PER_REQUEST)) {
            val url = FandomUrls.apiQuery(
                wikiId,
                listOf(
                    "action" to "query",
                    "titles" to chunk.joinToString("|"),
                    "prop" to "categoryinfo",
                    "redirects" to "1",
                    "format" to "json",
                    "formatversion" to "2"
                )
            ) ?: continue
            result.putAll(FandomApiParsers.parseCategoryInfo(FandomHttp.get(url)))
        }
        return result
    }

    /** Bir kategorinin ana ad alanındaki sayfaları (çip listesi) — yalnızca ilk sayfa. */
    suspend fun categoryMembers(wikiId: String, categoryTitle: String, limit: Int = 14): List<String> =
        categoryMembersPage(wikiId, categoryTitle, limit).items

    /**
     * Bir kategorinin ana ad alanındaki sayfaları, `cmcontinue` ile sayfalanmış.
     * "Devamını göster" akışı: ilk istek [continueToken] olmadan yapılır, yanıttaki
     * jeton sonraki isteğe taşınır; jeton `null` ise kategori tükenmiştir.
     */
    suspend fun categoryMembersPage(
        wikiId: String,
        categoryTitle: String,
        limit: Int = 14,
        continueToken: String? = null
    ): FandomCategoryMembersPage {
        val params = mutableListOf(
            "action" to "query",
            "list" to "categorymembers",
            "cmtitle" to categoryTitle,
            "cmnamespace" to "0",
            "cmlimit" to limit.coerceIn(1, 50).toString(),
            "format" to "json",
            "formatversion" to "2"
        )
        if (!continueToken.isNullOrBlank()) params += "cmcontinue" to continueToken
        val url = FandomUrls.apiQuery(wikiId, params) ?: return FandomCategoryMembersPage()
        val body = FandomHttp.get(url)
        return FandomCategoryMembersPage(
            items = FandomApiParsers.parseCategoryMembers(body),
            nextToken = FandomApiParsers.parseContinueToken(body)
        )
    }

    /** Wiki içi arama: başlık + kırpılmış özet (arama kartı sonuç listesi). */
    suspend fun searchHits(wikiId: String, query: String, limit: Int = 8): List<FandomSearchHit> {
        if (query.isBlank()) return emptyList()
        val url = FandomUrls.apiQuery(
            wikiId,
            listOf(
                "action" to "query",
                "list" to "search",
                "srsearch" to query,
                "srnamespace" to "0",
                "srlimit" to limit.coerceIn(1, 10).toString(),
                "format" to "json",
                "formatversion" to "2"
            )
        ) ?: return emptyList()
        return FandomApiParsers.parseSearchHits(FandomHttp.get(url))
    }

    suspend fun searchPages(wikiId: String, query: String, limit: Int = 5): List<String> {
        if (query.isBlank()) return emptyList()
        val url = FandomUrls.apiQuery(
            wikiId,
            listOf(
                "action" to "query",
                "list" to "search",
                "srsearch" to query,
                "srnamespace" to "0",
                "srlimit" to limit.coerceIn(1, 10).toString(),
                "format" to "json",
                "formatversion" to "2"
            )
        ) ?: return emptyList()
        return FandomApiParsers.parseSearchTitles(FandomHttp.get(url))
    }

    suspend fun communitySearch(query: String, lang: String = "en", limit: Int = 8): List<FandomCommunityHit> {
        if (query.isBlank()) return emptyList()
        val url = COMMUNITY_SEARCH.toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("lang", lang)
            .addQueryParameter("limit", limit.coerceIn(1, 20).toString())
            .build()
            .toString()
        return FandomApiParsers.parseCommunitySearch(FandomHttp.get(url))
    }

    /** Wiki'de birebir dış bağlantı (ör. `anilist.co/anime/11757`) var mı? */
    suspend fun hasExternalLink(wikiId: String, urlWithoutProtocol: String): Boolean {
        val url = FandomUrls.apiQuery(
            wikiId,
            listOf(
                "action" to "query",
                "list" to "exturlusage",
                "euquery" to urlWithoutProtocol,
                "eunamespace" to "0",
                "eulimit" to "1",
                "format" to "json",
                "formatversion" to "2"
            )
        ) ?: return false
        return FandomApiParsers.parseExtUrlUsageFound(FandomHttp.get(url))
    }

    /**
     * Belirli başlıklar için önizleme (kapak + açıklama): "İlgili wiki sayfaları",
     * "İlgili maddeler" ve metin içi bağlantı kartları kullanır. Tek istekte
     * `prop=pageimages|pageprops` ile thumbnail + fandomdescription, Vikipedi'de
     * ayrıca özet (`extracts`) ve kısa tanım (`description`) alınır.
     *
     * Vikipedi'de `prop=extracts` tek istekte en çok 20 sayfa döndürür ve fazlası
     * `continue`/`excontinue` ile bildirilir; bu yüzden devam anahtarı SONUNA KADAR
     * tüketilir (eskiden yok sayılıyordu ve şerit sessizce yarıda kesiliyordu).
     *
     * Dönen liste ÇAĞIRANIN verdiği sırayı korur: MediaWiki sayfaları pageid'e göre
     * sıraladığı için sıralama geri kurulmazsa önem sırası kaybolur.
     */
    suspend fun pagePreviews(wikiId: String, titles: List<String>, thumbWidth: Int = 320): List<FandomRelatedPage> {
        val clean = titles.map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= 200 && !it.contains('|') }
            .distinct()
        if (clean.isEmpty()) return emptyList()
        val byTitle = LinkedHashMap<String, FandomRelatedPage>()
        for (chunk in clean.chunked(MAX_TITLES_PER_REQUEST)) {
            val chunkPages = ArrayList<FandomRelatedPage>()
            val chunkResolved = HashMap<String, String>()
            var continueParams: Map<String, String> = emptyMap()
            var round = 0
            do {
                val params = mutableListOf(
                    "action" to "query",
                    "titles" to chunk.joinToString("|"),
                    "redirects" to "1",
                    "prop" to previewProps(wikiId),
                    "ppprop" to "infoboxes|fandomdescription|disambiguation",
                    "piprop" to "thumbnail",
                    "pithumbsize" to thumbWidth.toString(),
                    "pilimit" to chunk.size.toString()
                )
                params += excerptParams(wikiId, RELATED_EXCERPT_CHARS)
                if (FandomUrls.isWikipedia(wikiId)) {
                    params += "pilicense" to "any"
                    // `prop=extracts` tek istekte en çok 20 sayfa verir; sınırı açıkça
                    // belirtmezsek API varsayılanı (1) yanıtı tek sayfaya indirebilir.
                    params += "exlimit" to chunk.size.coerceAtMost(20).toString()
                }
                params += listOf("format" to "json", "formatversion" to "2")
                continueParams.forEach { (key, value) -> params += key to value }
                val url = FandomUrls.apiQuery(wikiId, params) ?: break
                val body = FandomHttp.get(url)
                val batch = FandomApiParsers.parseRelatedPageBatch(body, wikiId)
                if (batch.pages.isEmpty() && !FandomApiParsers.hasValidPageQuery(body)) {
                    // A wiki may reject an optional preview module. Ask MediaWiki core for
                    // actual page IDs; never turn missing/red links into clickable cards.
                    chunkPages += barePagePreviews(wikiId, chunk)
                    break
                }
                chunkPages += batch.pages
                chunkResolved.putAll(batch.resolvedTitles)
                continueParams = batch.continueParams
                round += 1
            } while (continueParams.isNotEmpty() && round < MAX_PREVIEW_ROUNDS)
            val resolvedChunk = chunk.map { title -> FandomApiParsers.resolveTitle(title, chunkResolved) }
            // Chunk içi sıralama: çağıranın aday sırası (çözümleme sonrası başlıkla eşleşir).
            val order = HashMap<String, Int>()
            resolvedChunk.forEachIndexed { index, title -> order.putIfAbsent(normalizeTitle(title), index) }
            chunkPages.sortedBy { page -> order[normalizeTitle(page.title)] ?: Int.MAX_VALUE }
                .forEach { page -> byTitle.putIfAbsent(normalizeTitle(page.title), page) }
        }
        return byTitle.values.toList()
    }

    private fun normalizeTitle(title: String): String =
        FandomNameVariants.normalizeForCompare(title)

    /** Core query with no optional modules; used only when a wiki rejects preview props. */
    private suspend fun barePagePreviews(wikiId: String, titles: List<String>): List<FandomRelatedPage> {
        val url = FandomUrls.apiQuery(wikiId, listOf(
            "action" to "query", "titles" to titles.joinToString("|"), "redirects" to "1",
            "format" to "json", "formatversion" to "2"
        )) ?: return emptyList()
        val lookups = FandomApiParsers.parseTitleLookups(titles, FandomHttp.get(url))
        // Çağıranın sırası korunur: MediaWiki yanıtı pageid sırasına göre gelir.
        return titles.mapNotNull { requested ->
            lookups[requested]?.takeIf { it.exists && !it.isDisambiguation }?.resolvedTitle
                ?.let { FandomRelatedPage(it) }
        }.distinctBy { it.title }
    }

    /**
     * Reader metadata. A recently validated related-page preview avoids a second
     * pageimages query on tap; TOC is still fetched for the canonical title.
     * Without a preview, a failed optional-module query falls back to a core-only
     * page-ID lookup. A truly missing page is never manufactured from a link title.
     * On Wikipedia, leader extracts are loaded for the description.
     */
    suspend fun pageMeta(
        wikiId: String,
        title: String,
        thumbWidth: Int = 600,
        verifiedPreview: FandomRelatedPage? = null
    ): FandomFullPage? {
        val clean = title.trim()
        if (clean.isEmpty() || clean.length > 200 || clean.contains('|')) return null
        val preview = verifiedPreview?.takeIf { it.title.equals(clean, ignoreCase = true) }
        val hit = preview ?: run {
            val params = buildList {
                add("action" to "query")
                add("titles" to clean)
                add("redirects" to "1")
                add("prop" to previewProps(wikiId))
                add("ppprop" to "infoboxes|fandomdescription|disambiguation")
                add("piprop" to "thumbnail")
                add("pithumbsize" to thumbWidth.toString())
                if (FandomUrls.isWikipedia(wikiId)) {
                    add("pilicense" to "any")
                }
                addAll(excerptParams(wikiId))
                add("format" to "json")
                add("formatversion" to "2")
            }
            val url = FandomUrls.apiQuery(wikiId, params) ?: return null
            val body = FandomHttp.get(url)
            FandomApiParsers.parseRelatedPages(body, wikiId).firstOrNull()
                ?: if (!FandomApiParsers.hasValidPageQuery(body)) barePagePreviews(wikiId, listOf(clean)).firstOrNull()
                    else null
        } ?: return null
        val toc = try {
            sections(wikiId, hit.title)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null // Metadata is still valid; the reader exposes a TOC retry action.
        }
        return FandomFullPage(
            title = clean,
            resolvedTitle = hit.title,
            description = hit.snippet,
            thumbnailUrl = hit.thumbnailUrl,
            sections = toc.orEmpty(),
            sectionsFailed = toc == null
        )
    }
}
