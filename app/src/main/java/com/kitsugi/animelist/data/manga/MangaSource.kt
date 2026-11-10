package com.kitsugi.animelist.data.manga

/**
 * TR Manga eklentilerinin kullandığı scraping motorunu tanımlar.
 *
 * APK içindeki classes.dex'ten string analizi ile tespit edilir.
 *
 * MADARA      → WordPress + WP-Manga Plugin (31 site)
 * THEMESIA    → WP Manga Themesia (16 site)
 * SVELTE      → SvelteKit __data.json API (6 site: uzaymanga, afroditscans, vb.)
 * INERTIA     → Laravel + Inertia.js (1 site: mangadenizi)
 * CUSTOM_HTML → Kendi HTML/JSON yapısı (15 site: hattorimanga, siyahmelek, vb.)
 * UNKNOWN     → Tespit edilemeyen (MEX veya yeni formatlar)
 */
enum class ExtensionEngine {
    MADARA,
    THEMESIA,
    SVELTE,
    INERTIA,
    KEI_SOURCE,
    CUSTOM_HTML,
    UNKNOWN
}

/**
 * Tüm manga kaynak eklentilerinin uymak zorunda olduğu temel kontrat.
 *
 * Her eklenti bu interface'i implemente ederek kendi sitesindeki
 * manga listesini, bölümleri ve sayfa URL'lerini Kitsugi'ya sunar.
 *
 * Mihon/Aniyomi'nin HttpSource yapısından esinlenerek, Kitsugi'nun
 * mevcut hafif eklenti mimarisine uygun şekilde tasarlanmıştır.
 */
interface MangaSource {
    /** Kaynak eklentinin görünen adı. Örn: "Manga-TR", "SadScans" */
    val name: String

    /** Eklentinin kapsadığı site adresi. Örn: "https://manga-tr.com" */
    val baseUrl: String

    /** Eklentinin orijinal (sabit kodlanmış) base adresi. */
    val originalBaseUrl: String get() = baseUrl

    /** Kaynak dilin ISO 639-1 kodu. Örn: "tr", "en", "ko" */
    val lang: String
    
    /** Eklentinin paket adı veya id'si. */
    val pkgName: String get() = ""

    /** Eklentinin scraping motor tipi (APK DEX analizinden tespit edilir). */
    val engineType: ExtensionEngine get() = ExtensionEngine.UNKNOWN

    /**
     * Bu kaynağın yerel arşiv dosyası (ZIP/CBZ/RAR) üzerinden manga sunuyor olup olmadığını
     * işaret eder. true ise MangaPageLoaderV2/WebReader archive yolunu kullanır;
     * false (varsayılan) ise normal HTTP/HTTPS scraping akışı devam eder.
     */
    val isArchive: Boolean get() = false

    /** Generic site session page used as a fallback when an extension has no dedicated auth API. */
    val webSessionUrl: String?
        get() = baseUrl.takeIf { it.startsWith("http://") || it.startsWith("https://") }

    /** Dedicated source-owned login/cookie or credential-preference capabilities, if any. */
    suspend fun authentication(): MangaSourceAuthentication? = null

    /** Null means the extension cannot report its current authorization state. */
    suspend fun isAuthenticated(): Boolean? = null

    suspend fun authenticatedUsername(): String? = null

    /** Saves only fields declared by [MangaSourceAuthentication.credentialFields]. */
    suspend fun saveCredentials(values: Map<String, String>) {
        throw UnsupportedOperationException("$name hesap tercihlerini desteklemiyor")
    }

    /** Uygulama içine gömülü (APK silinemeyen) dahili kaynak mı? */
    val isBuiltIn: Boolean get() = false

    /** Tekil sayfa indirme zaman aşımı (ms). WebView/Turnstile kullanan kaynaklar artırabilir. */
    val imageFetchTimeoutMs: Long get() = 30_000L

    /**
     * Sitedeki en güncel / popüler mangaların listesini döndürür.
     * Sayfalama için [page] parametresi kullanılır (1'den başlar).
     */
    suspend fun fetchPopularManga(page: Int): MangaSourceResult

    /**
     * Arama terimi veya filtreye göre manga listesi döndürür.
     */
    suspend fun fetchSearchManga(page: Int, query: String): MangaSourceResult

    /**
     * Kaynak "Son Güncellemeler" rafını gerçekten destekliyor mu?
     *
     * Mihon/Keiyoushi eklentileri bu bilgiyi `supportsLatest` bayrağıyla verir.
     * Kitsugi, eklenti portalındaki manga kaynağı için açılan **Keşfet** sayfasında
     * bu rafı yalnızca bayrak `true` ise gösterir — böylece uydurma/yanlış
     * sıralanmış bir liste kullanıcıya "son güncelleme" diye sunulmaz.
     *
     * Varsayılan `false`: raf gizlenir.
     */
    val supportsLatest: Boolean get() = false

    /**
     * Sitede son güncellenen / yeni bölüm eklenen mangaların listesini döndürür.
     *
     * Yalnızca [supportsLatest] `true` iken çağrılır. Varsayılan uygulama popüler
     * listeye düşer; gerçek desteği olan kaynaklar (Mihon/Kotatsu adaptörleri)
     * bunu kendi "latest" uçlarıyla ezer.
     */
    suspend fun fetchLatestManga(page: Int): MangaSourceResult = fetchPopularManga(page)

    /** Select-filter indices are interpreted by the extension, never as server values. */
    fun selectFilters(): List<MangaSelectFilter> = emptyList()
    suspend fun fetchFilteredManga(page: Int, selections: Map<Int, Int>): MangaSourceResult = fetchPopularManga(page)

    /**
     * Verilen [mangaUrl] için detaylı manga bilgilerini çeker.
     * Açıklama, yazar, durum ve kapak resmi bu çağrıyla doldurulur.
     */
    suspend fun fetchMangaDetails(mangaUrl: String): MangaDetails

    /**
     * Verilen manga URL'si için tüm bölüm listesini döndürür.
     * Sonuç en yeniden en eskiye sıralanmış olmalıdır.
     */
    suspend fun fetchChapterList(mangaUrl: String): List<MangaChapter>

    /**
     * Verilen [chapter] için bu bölümdeki tüm sayfa bilgilerini döndürür.
     * Döndürülen listenin her elemanı 0'dan indexlenir.
     */
    suspend fun fetchPageList(chapter: MangaChapter): List<MangaPage>

    /**
     * Bir sayfanın resim URL'si henüz bilinmiyorsa (boş ise),
     * ek bir HTTP isteği yaparak gerçek resim URL'sini çözer.
     * Çoğu kaynak için override gerekmeyebilir.
     */
    suspend fun fetchImageUrl(page: MangaPage): String = page.imageUrl ?: page.url

    /**
     * Bir sayfanın resmini indirir ve giriş akışını (InputStream) döndürür.
     * Bu sayede eklenti kendi özel client/headers yapılandırmasını kullanarak istek yapabilir.
     */
    suspend fun getImage(page: MangaPage): java.io.InputStream {
        val imageUrl = page.imageUrl ?: throw IllegalArgumentException("Image URL is null")
        val networkHelper = uy.kohesive.injekt.Injekt.get(eu.kanade.tachiyomi.network.NetworkHelper::class.java)
        val request = okhttp3.Request.Builder()
            .url(imageUrl)
            .header("Referer", baseUrl)
            .header("User-Agent", networkHelper.defaultUserAgentProvider())
            .build()
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val response = networkHelper.client.newCall(request).execute()
            if (!response.isSuccessful) {
                response.close()
                throw java.io.IOException("HTTP error ${response.code}")
            }
            val body = response.body ?: throw java.io.IOException("Response body is null")
            object : java.io.FilterInputStream(body.byteStream()) {
                override fun close() {
                    super.close()
                    response.close()
                }
            }
        }
    }
}

data class MangaSourceCredentialField(
    val key: String,
    val title: String,
    val value: String,
    val isSecret: Boolean,
)

data class MangaSourceAuthentication(
    /** Kotatsu's provider-owned login page. Never guessed by the UI. */
    val loginUrl: String? = null,
    /** Real ConfigurableSource credential fields exposed by a Mihon extension. */
    val credentialFields: List<MangaSourceCredentialField> = emptyList(),
)

data class MangaSelectFilter(val index: Int, val name: String, val values: List<String>, val selected: Int)

/**
 * Bir sorgu veya listeleme işleminin sonucunu taşır.
 */
data class MangaSourceResult(
    val mangas: List<MangaDetails>,
    val hasNextPage: Boolean,
    /** Kaynak hatası (varsa). Boş liste + hata ayırt edilebilsin diye. */
    val error: String? = null
)

/**
 * F9: Captcha veya WebView doğrulaması gerektiren kaynaklarda fırlatılır.
 * MangaReaderViewModel / MangaPageLoaderV2 bu exception'ı yakalayıp
 * kullanıcıya anlamlı bir hata mesajı gösterebilir.
 */
class CaptchaRequiredException(
    val sourceName: String,
    override val message: String = "Captcha gerekli"
) : Exception(message)

/**
 * Dil bagimsiz cok dilli / global katalog kaynaklari (MangaDex, Comick, NamiComi vb.)
 */
val MangaSource.isGlobalCatalog: Boolean
    get() = lang.equals("all", ignoreCase = true) || lang.equals("multi", ignoreCase = true)

