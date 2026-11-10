package com.kitsugi.animelist.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

/**
 * Data classes mirroring the Cloudstream repository JSON format.
 */
data class CsRepository(
    val name: String,
    val description: String?,
    val pluginLists: List<String>,
    /**
     * Bu kayıt canlı depodan DEĞİL, yedek kaynaktan (Codeberg aynası / APK içine gömülü
     * `assets/plugins/repo.json`) geldi. Güncelleme denetimi bu bayrağı görürse sonucu
     * "depo güncel" diye sunmaz; kullanıcıya ulaşılamadığını bildirir.
     */
    val fromOfflineFallback: Boolean = false
)

data class CsPlugin(
    val name: String,
    val internalName: String,
    /** Direct manifest URL or plugin download URL. For Stremio addons this IS the manifest URL. */
    val url: String,
    val description: String?,
    val version: Int,
    val language: String?,
    val tvTypes: List<String>?,
    val iconUrl: String?,
    val authors: List<String>,
    /** Plugin availability status from repo JSON: 0=Down/Disabled, 1=OK, 2=Beta/Slow, 3=Broken (filtered out before reaching UI) */
    val status: Int = 1,
    /** SHA-256 hash of the .cs3 file for integrity verification (optional, provided by repo). */
    val fileHash: String? = null,
    /** File size in bytes, for display purposes (optional, provided by repo). */
    val fileSize: Long? = null,
    val repositoryUrl: String? = null,
    /**
     * Kayıt canlı depodan değil yedek kaynaktan geldi (Codeberg aynası veya APK içine gömülü
     * `assets/plugins/plugins.json`). Gömülü liste APK derlendiği anda dondurulduğu için
     * sürüm bilgisi BAYATTIR: bu kayıtlarla "güncelleme yok" sonucuna varmak yanıltıcıdır.
     */
    val fromOfflineFallback: Boolean = false
)

/**
 * Network client that fetches Cloudstream-compatible repository manifests
 * and plugin lists. The repo JSON format is:
 *
 * ```json
 * {
 *   "name": "My Repo",
 *   "description": "...",
 *   "manifestVersion": 1,
 *   "pluginLists": ["https://raw.githubusercontent.com/.../plugins.json"]
 * }
 * ```
 *
 * Each plugin list URL returns an array of plugin objects:
 *
 * ```json
 * [
 *   {
 *     "name": "Addon Name",
 *     "internalName": "AddonProvider",
 *     "url": "https://example.com/manifest.json",
 *     "version": 1,
 *     "status": 1,
 *     "apiVersion": 1,
 *     "description": "...",
 *     "language": "tr",
 *     "tvTypes": ["Anime", "AnimeMovie"],
 *     "authors": ["author"],
 *     "iconUrl": null
 *   }
 * ]
 * ```
 *
 * Note: For KitsugiAnimeList, the "url" field should be a Stremio manifest URL.
 * Cloudstream .cs3 binary plugins are NOT supported — only Stremio HTTP addons.
 */
class CloudstreamRepoClient(private val context: android.content.Context? = null) {

    private val client: OkHttpClient
        get() = com.kitsugi.animelist.core.network.KitsugiHttpClient.client

    companion object {
        private const val TAG = "CloudstreamRepoClient"
        private const val CODEBERG_API_REPO_FALLBACK = "https://codeberg.org/api/v1/repos/BlackDamage/KitsugiPlugins/raw/builds/repo.json"
        private const val CODEBERG_API_PLUGINS_FALLBACK = "https://codeberg.org/api/v1/repos/BlackDamage/KitsugiPlugins/raw/builds/plugins.json"

        /** Kitsugi resmi eklenti havuzu (uygulamanın varsayılan deposu). */
        const val KITSUGI_PLUGINS_SLUG = "BlackDamage-Plugin/Kitsugi-Video-Plugins"

        private const val GITHUB_RAW_PREFIX = "https://raw.githubusercontent.com/"
        private const val GITHUB_API_CONTENTS = "https://api.github.com/repos/"

        /** Ayna zincirinin toplam süre bütçesi: güncelleme denetimi asılı kalmasın. */
        private const val MIRROR_BUDGET_MS = 20_000L
    }

    /**
     * URL Kitsugi eklenti havuzunu (yeni GitHub deposu veya eski Codeberg aynası) mu işaret ediyor?
     *
     * NOT: Daha önce bu kontrol bazı yerlerde yalnızca `KitsugiPlugins` (tire yok) ve
     * `codeberg.org` arıyordu; resmi depo URL'si `BlackDamage-Plugin/Kitsugi-Video-Plugins/builds/...`
     * biçiminde tire içerdiği için o yollar hiç tetiklenmiyor, istek başarısız olduğunda
     * doğrudan APK içindeki BAYAT listeye düşülüyordu. Tek bir yardımcı ile birleştirildi.
     */
    fun isKitsugiRepoUrl(url: String?): Boolean {
        val value = url ?: return false
        return value.contains("Kitsugi", ignoreCase = true) ||
            value.contains("KitsugiPlugins", ignoreCase = true) ||
            value.contains("Kitsugi-Plugins", ignoreCase = true) ||
            value.contains("Kitsugi-Video-Plugins", ignoreCase = true) ||
            value.contains("BlackDamage-Plugin", ignoreCase = true) ||
            value.contains("BlackDamage-Dev", ignoreCase = true) ||
            value.contains("BlackDamage/KitsugiPlugins", ignoreCase = true)
    }

    /**
     * `raw.githubusercontent.com/<user>/<repo>/<dal>/<yol>` → `api.github.com` muadili.
     *
     * Neden: Türkiye'de `raw.githubusercontent.com` sık sık ISS/BTK seviyesinde engelleniyor
     * (DNS zehirlenmesi, TLS reset). `api.github.com` çoğu zaman açık kalıyor ve
     * `Accept: application/vnd.github.raw` başlığı ile AYNI içeriği (CDN önbelleği olmadan)
     * veriyor. Böylece "depoya ulaşılamadı → bayat gömülü liste" zinciri kırılıyor.
     */
    internal fun githubApiContentUrl(rawUrl: String): String? {
        if (!rawUrl.startsWith(GITHUB_RAW_PREFIX, ignoreCase = true)) return null
        val rest = rawUrl.removePrefix(GITHUB_RAW_PREFIX).substringBefore("?").substringBefore("#")
        val parts = rest.split("/")
        if (parts.size < 4) return null
        val user = parts[0]
        val repo = parts[1]
        val branch = parts[2]
        val path = parts.drop(3).joinToString("/")
        if (user.isBlank() || repo.isBlank() || branch.isBlank() || path.isBlank()) return null
        return "$GITHUB_API_CONTENTS$user/$repo/contents/$path?ref=$branch"
    }

    /**
     * `cdn.jsdelivr.net/gh/<user>/<repo>@<dal>/<yol>` → `raw.githubusercontent.com` muadili.
     *
     * Kullanıcı "GitHub Vekil Sunucu (jsDelivr)" ayarını açtığında birincil istek doğrudan
     * jsDelivr'a gider. jsDelivr dal içeriğini saatlerce önbellekleyebildiği için, tazelik
     * gerektiren güncelleme denetiminde zincire ham GitHub adresi ve api.github.com muadili
     * de eklenir (bkz. [mirrorChainFor]).
     */
    internal fun githubRawFromJsdelivr(url: String): String? {
        val prefix = "https://cdn.jsdelivr.net/gh/"
        if (!url.startsWith(prefix, ignoreCase = true)) return null
        val rest = url.substring(prefix.length).substringBefore("?").substringBefore("#")
        val at = rest.indexOf('@')
        if (at <= 0) return null
        val userRepo = rest.substring(0, at)
        val branchPath = rest.substring(at + 1)
        val slash = branchPath.indexOf('/')
        if (slash <= 0) return null
        val branch = branchPath.substring(0, slash)
        val path = branchPath.substring(slash + 1)
        if (userRepo.isBlank() || branch.isBlank() || path.isBlank()) return null
        return "$GITHUB_RAW_PREFIX$userRepo/$branch/$path"
    }

    /**
     * Bir manifest/eklenti listesi URL'si için denenecek ayna zinciri.
     *
     * Sıra önemli:
     *  1. Birincil URL (doğrudan GitHub ham içeriği veya kullanıcının açtığı jsDelivr vekili)
     *  2. `api.github.com` muadili — taze içerik, CDN gecikmesi yok
     *  3. jsDelivr `gh` aynası (ISS engellerinde çalışır; dal içeriği birkaç saat önbelleklenir)
     *  4. Kitsugi havuzu için eski Codeberg aynası (en son yedek; içerik daha eski olabilir)
     */
    internal fun mirrorChainFor(url: String): List<String> {
        val chain = LinkedHashSet<String>()
        chain.add(url)

        // jsDelivr üzerinden gelen birincil URL ise: ham GitHub + api.github.com muadillerini ekle.
        githubRawFromJsdelivr(url)?.let { raw ->
            chain.add(raw)
            githubApiContentUrl(raw)?.let { chain.add(it) }
        }

        githubApiContentUrl(url)?.let { chain.add(it) }

        val jsdelivr = com.kitsugi.animelist.utils.CloudstreamUrlHelper.applyGithubProxy(url)
        if (jsdelivr != url) chain.add(jsdelivr)

        if (isKitsugiRepoUrl(url)) {
            val fileName = url.substringBefore("?").substringBefore("#").substringAfterLast("/")
            if (fileName.isNotBlank()) {
                chain.add("https://codeberg.org/BlackDamage/KitsugiPlugins/raw/branch/builds/$fileName")
            }
        }

        Log.d(TAG, "Ayna zinciri (${chain.size} aday): ${chain.joinToString(" → ")}")
        return chain.toList()
    }

    fun fetchRepoFromAssets(assetPath: String = "plugins/repo.json"): CsRepository? {
        val ctx = context ?: return null
        return runCatching {
            val jsonStr = ctx.assets.open(assetPath).bufferedReader().use { it.readText() }
            val json = JSONObject(jsonStr)
            val name = json.optString("name", KitsugiStrings.get(R.string.kitsugi_eklentileri_onerilen))
            val description = json.optString("description").takeIf { it.isNotBlank() }
            val pluginLists = mutableListOf<String>()
            val arr = json.optJSONArray("pluginLists")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val url = arr.optString(i)
                    if (url.isNotBlank()) pluginLists.add(url)
                }
            }
            CsRepository(
                name = name,
                description = description,
                pluginLists = pluginLists,
                fromOfflineFallback = true
            )
        }.getOrNull()
    }

    fun fetchPluginsFromAssets(assetPath: String = "plugins/plugins.json"): List<CsPlugin> {
        val ctx = context ?: return emptyList()
        return runCatching {
            val jsonStr = ctx.assets.open(assetPath).bufferedReader().use { it.readText() }
            // Gömülü liste APK'nın derlendiği anda dondurulur; sürüm bilgisi bayat olduğu için
            // "canlı depo verisi" sayılmaması adına işaretlenir.
            dedupePlugins(parsePluginsJson(jsonStr, false)).map { it.copy(fromOfflineFallback = true) }
        }.getOrDefault(emptyList())
    }

    /**
     * Tek bir URL'yi indirir. Başarısızsa `null` döner (istisna fırlatmaz).
     *
     * @param forceRefresh true ise OkHttp önbelleği ve CDN devre dışı bırakılır (önbellek
     *        kırıcı parametre + `no-cache` başlıkları). Kullanıcı "Yenile" dediğinde şart.
     */
    private fun executeGet(url: String, forceRefresh: Boolean): String? {
        val finalUrl = if (forceRefresh) {
            com.kitsugi.animelist.utils.CloudstreamUrlHelper.withCacheBuster(url)
        } else {
            url
        }
        val isGithubApi = finalUrl.contains("api.github.com/repos/", ignoreCase = true)
        return try {
            val requestBuilder = Request.Builder()
                .url(finalUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36 Kitsugi/2.4")
                .addHeader(
                    "Accept",
                    if (isGithubApi) "application/vnd.github.raw" else "application/json, text/plain, */*"
                )

            if (forceRefresh) {
                requestBuilder.cacheControl(okhttp3.CacheControl.FORCE_NETWORK)
                requestBuilder.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                requestBuilder.addHeader("Pragma", "no-cache")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "İstek başarısız (${response.code}): $finalUrl")
                    null
                } else {
                    response.body?.string()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "İstek hatası ($finalUrl): ${e.message}")
            null
        }
    }

    /**
     * [url] için ayna zincirini sırayla dener ve ilk BAŞARILI yanıtı döndürür.
     *
     * @return gövde + gerçekten yanıt veren URL (yedek kaynak mı, birincil mi ayrımı için).
     */
    private fun fetchWithMirrors(
        url: String,
        forceRefresh: Boolean,
        budgetMs: Long = MIRROR_BUDGET_MS
    ): Pair<String, String>? {
        val deadline = System.currentTimeMillis() + budgetMs
        for (candidate in mirrorChainFor(url)) {
            val body = executeGet(candidate, forceRefresh)
            if (!body.isNullOrBlank()) return body to candidate
            // Ağ tamamen kapalıysa tüm aynaları denemek kullanıcıyı bekletir:
            // bütçe dolduğunda kalan aynalar atlanır, çağıran taraf yedek akışa geçer.
            if (System.currentTimeMillis() > deadline) {
                Log.w(TAG, "Ayna bütçesi (${budgetMs}ms) doldu, kalan adaylar atlanıyor: $url")
                break
            }
        }
        return null
    }

    /** Yanıt bir yedek/ayna kaynaktan mı geldi (birincil URL dışında bir aday)? */
    private fun isMirrorAnswer(primaryUrl: String, answeredUrl: String): Boolean {
        val primary = primaryUrl.substringBefore("?").substringBefore("#")
        val answered = answeredUrl.substringBefore("?").substringBefore("#")
        return !answered.equals(primary, ignoreCase = true)
    }

    suspend fun fetchRepo(repoUrl: String, useGithubProxy: Boolean = false, forceRefresh: Boolean = false): CsRepository? = withContext(Dispatchers.IO) {
        val normalizedUrl = com.kitsugi.animelist.utils.CloudstreamUrlHelper.normalizeAndProxy(repoUrl, useGithubProxy)
        try {
            val answer = fetchWithMirrors(normalizedUrl, forceRefresh)
            if (answer != null) {
                val (body, answeredUrl) = answer
                val parsed = runCatching { parseRepoJson(body, answeredUrl, useGithubProxy) }.getOrNull()
                if (parsed != null) {
                    return@withContext if (isMirrorAnswer(normalizedUrl, answeredUrl)) {
                        parsed.copy(fromOfflineFallback = true)
                    } else {
                        parsed
                    }
                }
            }

            // JsonParse fail / network fail → Kitsugi havuzu için Codeberg API yedeği
            if (isKitsugiRepoUrl(repoUrl)) {
                Log.i(TAG, "Birincil kaynak başarısız, Codeberg API yedeği deneniyor: $repoUrl")
                val apiResult = executeGet(CODEBERG_API_REPO_FALLBACK, forceRefresh = false)
                if (apiResult != null) {
                    val parsed = runCatching { parseRepoJson(apiResult, CODEBERG_API_REPO_FALLBACK, useGithubProxy) }.getOrNull()
                    if (parsed != null) return@withContext parsed.copy(fromOfflineFallback = true)
                }
                Log.i(TAG, "Codeberg uç noktaları da başarısız — APK içindeki (bayat) depo listesine düşülüyor.")
                return@withContext fetchRepoFromAssets()
            }
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "fetchRepo istisnası: $normalizedUrl", e)
            if (isKitsugiRepoUrl(repoUrl)) {
                return@withContext fetchRepoFromAssets()
            }
            null
        }
    }

    private fun parseRepoJson(responseBody: String, requestUrl: String, useGithubProxy: Boolean): CsRepository {
        val trimmed = responseBody.trim()
        if (trimmed.startsWith("[")) {
            // Direct plugins.json URL (e.g. Kraptor, Hexated, or direct plugins array)
            val repoName = deriveRepoNameFromUrl(requestUrl)
            return CsRepository(
                name = repoName,
                description = "Eklenti Deposu ($repoName)",
                pluginLists = listOf(requestUrl)
            )
        }
        val json = org.json.JSONObject(responseBody)
        val name = json.optString("name", deriveRepoNameFromUrl(requestUrl))
        val description = json.optString("description").takeIf { it.isNotBlank() }
        val pluginLists = mutableListOf<String>()
        val arr = json.optJSONArray("pluginLists")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val url = arr.optString(i)
                if (url.isNotBlank()) {
                    pluginLists.add(com.kitsugi.animelist.utils.CloudstreamUrlHelper.normalizeAndProxy(url, useGithubProxy))
                }
            }
        }
        return CsRepository(name = name, description = description, pluginLists = pluginLists)
    }

    private fun deriveRepoNameFromUrl(url: String): String {
        val clean = url.substringBefore("?").substringBefore("#").removeSuffix("/")
        val parts = clean.split("/")
        val nameCandidate = parts.findLast { it.isNotBlank() && !it.endsWith(".json") && !it.equals("builds", true) && !it.equals("master", true) && !it.equals("main", true) }
        return nameCandidate?.replace("-", " ")?.replace("_", " ")?.capitalize(java.util.Locale.ROOT) ?: "Eklenti Havuzu"
    }

    suspend fun fetchPlugins(pluginListUrl: String, useGithubProxy: Boolean = false, forceRefresh: Boolean = false): List<CsPlugin> = withContext(Dispatchers.IO) {
        val normalizedUrl = com.kitsugi.animelist.utils.CloudstreamUrlHelper.normalizeAndProxy(pluginListUrl, useGithubProxy)
        try {
            val answer = fetchWithMirrors(normalizedUrl, forceRefresh)
            if (answer != null) {
                val (body, answeredUrl) = answer
                val parsed = runCatching { parsePluginsJson(body, useGithubProxy) }.getOrNull()
                if (parsed != null) {
                    // Ayna yanıtı: içerik canlı depodan değil (ör. Codeberg'in eski dalı) —
                    // kurulum için kullanılabilir ama "güncel sürüm" kaynağı sayılmaz.
                    return@withContext if (isMirrorAnswer(normalizedUrl, answeredUrl)) {
                        parsed.map { it.copy(fromOfflineFallback = true) }
                    } else {
                        parsed
                    }
                }
            }

            // Kitsugi havuzu: Codeberg API yedeği, ardından (en son çare) gömülü liste.
            // NOT: bu kontrol daha önce yalnızca "KitsugiPlugins"/"codeberg.org" arıyordu;
            // resmi depo URL'si "Kitsugi-Plugins" (tireli) olduğu için hiç tetiklenmiyordu.
            if (isKitsugiRepoUrl(pluginListUrl)) {
                Log.i(TAG, "Birincil eklenti listesi başarısız, Codeberg API yedeği deneniyor: $pluginListUrl")
                val apiResult = executeGet(CODEBERG_API_PLUGINS_FALLBACK, forceRefresh = false)
                if (apiResult != null) {
                    val parsed = runCatching { parsePluginsJson(apiResult, useGithubProxy) }.getOrNull()
                    if (!parsed.isNullOrEmpty()) {
                        Log.i(TAG, "Codeberg yedeğinden ${parsed.size} eklenti okundu (bayat olabilir).")
                        return@withContext parsed.map { it.copy(fromOfflineFallback = true) }
                    }
                }
                Log.i(TAG, "Canlı eklenti listesine ulaşılamadı — APK içindeki (bayat) listeye düşülüyor.")
                return@withContext fetchPluginsFromAssets()
            }
            return@withContext emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "fetchPlugins istisnası: $normalizedUrl", e)
            if (isKitsugiRepoUrl(pluginListUrl)) {
                return@withContext fetchPluginsFromAssets()
            }
            emptyList()
        }
    }

    private fun parsePluginsJson(responseBody: String, useGithubProxy: Boolean): List<CsPlugin> {
        val arr = org.json.JSONArray(responseBody)
        val plugins = mutableListOf<CsPlugin>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val rawUrl = obj.optString("url", "")
            val name = obj.optString("name", "")
            if (rawUrl.isBlank() || name.isBlank()) continue

            val url = com.kitsugi.animelist.utils.CloudstreamUrlHelper.normalizeAndProxy(rawUrl, useGithubProxy)

            val status = obj.optInt("status", 1)
            if (status == 3) {
                Log.d(TAG, "Skipping broken/deprecated plugin: $name (status=$status)")
                continue
            }

            val tvTypesArr = obj.optJSONArray("tvTypes")
            val tvTypes = if (tvTypesArr != null) {
                (0 until tvTypesArr.length()).mapNotNull { tvTypesArr.optString(it).takeIf { s -> s.isNotBlank() } }
            } else null

            // +18 / Adult / NSFW eklentileri genel eklenti havuzuna asla ekleme
            val isAdult = com.kitsugi.animelist.data.cloudstream.CsStreamRunner.ADULT_PLUGINS_SET.any { it.equals(name, ignoreCase = true) } ||
                tvTypes?.any { it.equals("NSFW", ignoreCase = true) || it.equals("Adult", ignoreCase = true) } == true
            if (isAdult) {
                Log.d(TAG, "Skipping adult/NSFW plugin from catalog: $name")
                continue
            }

            val authorsArr = obj.optJSONArray("authors")
            val authors = if (authorsArr != null) {
                (0 until authorsArr.length()).mapNotNull { authorsArr.optString(it).takeIf { s -> s.isNotBlank() } }
            } else emptyList()

            val fileHash = obj.optString("fileHash").takeIf { it.isNotBlank() }
            val fileSize = if (obj.has("fileSize")) obj.optLong("fileSize") else null

            plugins.add(
                CsPlugin(
                    name = name,
                    internalName = obj.optString("internalName", name),
                    url = url,
                    description = obj.optString("description").takeIf { it.isNotBlank() },
                    version = obj.optInt("version", 1),
                    language = obj.optString("language").takeIf { it.isNotBlank() },
                    tvTypes = tvTypes,
                    iconUrl = obj.optString("iconUrl").takeIf { it.isNotBlank() },
                    authors = authors,
                    status = status,
                    fileHash = fileHash,
                    fileSize = fileSize,
                    repositoryUrl = obj.optString("repositoryUrl").takeIf { it.isNotBlank() }
                )
            )
        }
        return plugins
    }

    /**
     * Fetches the repo manifest and then all plugin lists it references.
     * Returns all plugins in a flat list.
     */
    suspend fun fetchAllPlugins(repoUrl: String, useGithubProxy: Boolean = false, forceRefresh: Boolean = false): List<CsPlugin>? {
        val normalizedRepoUrl = com.kitsugi.animelist.utils.CloudstreamUrlHelper.normalizeAndProxy(repoUrl, useGithubProxy)
        
        // Doğrudan plugins.json URL'si girilmişse manifest aramadan direkt eklentileri çek
        if (normalizedRepoUrl.endsWith("plugins.json", ignoreCase = true)) {
            var directPlugins = fetchPlugins(normalizedRepoUrl, useGithubProxy, forceRefresh)
            if (directPlugins.isEmpty() && !useGithubProxy && repoUrl.contains("raw.githubusercontent.com", ignoreCase = true)) {
                directPlugins = fetchPlugins(repoUrl, useGithubProxy = true, forceRefresh = forceRefresh)
            }
            if (directPlugins.isNotEmpty()) {
                return dedupePlugins(directPlugins.map { it.copy(repositoryUrl = it.repositoryUrl ?: normalizedRepoUrl) })
            }
        }

        var repo = fetchRepo(normalizedRepoUrl, useGithubProxy, forceRefresh)
        if (repo == null && !useGithubProxy && repoUrl.contains("raw.githubusercontent.com", ignoreCase = true)) {
            repo = fetchRepo(repoUrl, useGithubProxy = true, forceRefresh = forceRefresh)
        }
        if (repo == null && isKitsugiRepoUrl(normalizedRepoUrl)) {
            repo = fetchRepoFromAssets()
        }
        if (repo == null) return null
        val all = mutableListOf<CsPlugin>()
        for (listUrl in repo.pluginLists) {
            var plugins = fetchPlugins(listUrl, useGithubProxy, forceRefresh)
            if (plugins.isEmpty() && !useGithubProxy && listUrl.contains("raw.githubusercontent.com", ignoreCase = true)) {
                plugins = fetchPlugins(listUrl, useGithubProxy = true, forceRefresh = forceRefresh)
            }
            if (plugins.isEmpty() && isKitsugiRepoUrl(listUrl)) {
                plugins = fetchPluginsFromAssets()
            }
            all.addAll(plugins.map { it.copy(repositoryUrl = it.repositoryUrl ?: normalizedRepoUrl) })
        }
        return dedupePlugins(all)
    }

    /**
     * Aynı eklentinin birden fazla depoda (veya aynı depoda farklı adlarla) listelenmesi
     * durumunda TEK bir kayıt bırakır.
     *
     * Neden gerekli: Türkçe eklenti havuzunda 130+ eklenti birden fazla depoda yer alıyor
     * ve kayıtlar farklı sürüm/URL'ler taşıyor (`Kanal7` vs `Kanal 7`, `WFilmİzle` vs
     * `WFilmizle`, `Dizikorea` vs `DiziKorea` gibi yalnızca Türkçe karakter/büyük-küçük harf
     * farkı olan adlar dahil). Dedupe olmadan aynı eklenti portalda iki kez görünüyor,
     * kullanıcı eski/kırık sürümü kuruyor ve "eklenti boş dönüyor" hatası ortaya çıkıyordu.
     *
     * Kural: kimlik anahtarı normalleştirilmiş [CsPlugin.internalName]'dir. Aynı anahtar için
     * **en yüksek sürüm** kazanır; sürümler eşitse önce gelen (repo'nun pluginLists sırası)
     * korunur — böylece birincil depo tercih edilir.
     */
    internal fun dedupePlugins(plugins: List<CsPlugin>): List<CsPlugin> {
        val best = LinkedHashMap<String, CsPlugin>()
        for (plugin in plugins) {
            val key = pluginIdentityKey(plugin.internalName.ifBlank { plugin.name })
            if (key.isEmpty()) continue
            val existing = best[key]
            if (existing == null || plugin.version > existing.version) {
                best[key] = plugin
            }
        }
        return best.values.toList()
    }

    /**
     * Eklenti adını karşılaştırma anahtarına indirger: küçük harfe çevirir, Türkçe karakterleri
     * ASCII karşılıklarına katlar (ı/İ/I → i, ş → s, ğ → g, ü → u, ö → o, ç → c) ve
     * boşluk/noktalama işaretlerini atar. Böylece `"Kanal 7"`, `"Kanal7"` ile; `"WFilmİzle"`,
     * `"WFilmizle"` ile eşleşir.
     */
    internal fun pluginIdentityKey(name: String): String {
        val sb = StringBuilder(name.length)
        for (ch in name) {
            when (ch) {
                'İ', 'I', 'ı', 'i' -> sb.append('i')
                'Ş', 'ş' -> sb.append('s')
                'Ğ', 'ğ' -> sb.append('g')
                'Ü', 'ü' -> sb.append('u')
                'Ö', 'ö' -> sb.append('o')
                'Ç', 'ç' -> sb.append('c')
                else -> sb.append(ch.lowercaseChar())
            }
        }
        return sb.toString()
            .replace("\u0307", "")
            .filter { it.isLetterOrDigit() }
    }
}
