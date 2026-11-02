package com.kitsugi.animelist.utils

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import okhttp3.Request

object CloudstreamUrlHelper {

    private const val TAG = "CloudstreamUrlHelper"

    /** Kısa kod regex: sadece harf, rakam, tire, alt çizgi, ünlem — nokta/slash yok */
    private val SHORT_CODE_REGEX = Regex("^[a-zA-Z0-9!_-]+$")

    /**
     * Normalizes repository and plugin URLs.
     * Handles cloudstreamrepo:// and cs.repo/? URI schemes.
     * Both keyiflerolsun and feroxx repos are treated as independent, valid sources.
     */
    fun normalizeUrl(rawUrl: String): String {
        var url = rawUrl.trim()
        if (url.contains("http-protocol-redirector?r=")) {
            val queryParam = url.substringAfter("http-protocol-redirector?r=")
            url = try {
                URLDecoder.decode(queryParam, "UTF-8")
            } catch (e: Exception) {
                queryParam
            }
        }
        if (url.startsWith("cloudstreamrepo://")) {
            url = "https://" + url.removePrefix("cloudstreamrepo://")
        } else if (url.startsWith("cloudstreamrepo:")) {
            val remaining = url.removePrefix("cloudstreamrepo:")
            url = if (remaining.startsWith("http://") || remaining.startsWith("https://")) {
                remaining
            } else {
                "https://$remaining"
            }
        } else if (url.startsWith("https://cs.repo/?") || url.startsWith("https://cs.repo?")) {
            // cs.repo kısa URL şeması: https://cs.repo/?https://raw.githubusercontent.com/...
            val realUrl = url.substringAfter("?")
            url = if (realUrl.startsWith("http")) realUrl else "https://$realUrl"
        }

        // Remove trailing slashes and common wrappers
        val cleanUrl = url.trim().removeSuffix("/")

        // Check for our KitsugiPlugins repo or legacy repositories that migrated to KitsugiPlugins
        val isOurRepoOrLegacy = cleanUrl.contains("KitsugiPlugins", ignoreCase = true) ||
            cleanUrl.contains("Kitsugi-Plugins", ignoreCase = true) ||
            cleanUrl.contains("f26901964-eng/Kitsugi-Plugins", ignoreCase = true) ||
            cleanUrl.contains("KitsugiBeta-dev", ignoreCase = true) ||
            cleanUrl.contains("gameras1010-afk/Kitsugi-Plugins", ignoreCase = true)

        if (isOurRepoOrLegacy) {
            val cleanPath = cleanUrl.substringBefore("?").substringBefore("#")
            return when {
                cleanPath.endsWith(".cs3", ignoreCase = true) -> {
                    val fileName = cleanPath.substringAfterLast("/")
                    val baseName = fileName.removeSuffix(".cs3")
                    // Bilinen ad eşlemesi varsa dosya adını düzelt: eski depo kayıtları
                    // (ör. `CizgiveDizi`, `Dizikorea`) yeni depoda farklı adla derleniyor.
                    val alias = CS3_NAME_ALIASES[pluginIdentityKey(baseName)]
                    val resolvedName = if (alias != null) "$alias.cs3" else fileName
                    "https://raw.githubusercontent.com/f26901964-eng/Kitsugi-Plugins/builds/$resolvedName"
                }
                cleanPath.endsWith("plugins.json", ignoreCase = true) -> {
                    "https://raw.githubusercontent.com/f26901964-eng/Kitsugi-Plugins/builds/plugins.json"
                }
                else -> {
                    // Canonical repo manifest (verified 200 OK)
                    "https://raw.githubusercontent.com/f26901964-eng/Kitsugi-Plugins/builds/repo.json"
                }
            }
        }

        // Kraptor123 cs-kraptor only serves builds/plugins.json (no repo.json)
        if (cleanUrl.contains("Kraptor123/cs-kraptor", ignoreCase = true)) {
            val cleanPath = cleanUrl.substringBefore("?").substringBefore("#")
            if (cleanPath.endsWith(".cs3", ignoreCase = true)) {
                val fileName = cleanPath.substringAfterLast("/")
                return "https://raw.githubusercontent.com/Kraptor123/cs-kraptor/builds/$fileName"
            }
            return "https://raw.githubusercontent.com/Kraptor123/cs-kraptor/builds/plugins.json"
        }

        // Hexated cloudstream-extensions-hexated serves builds/plugins.json
        if (cleanUrl.contains("hexated/cloudstream-extensions-hexated", ignoreCase = true)) {
            val cleanPath = cleanUrl.substringBefore("?").substringBefore("#")
            if (cleanPath.endsWith(".cs3", ignoreCase = true)) {
                val fileName = cleanPath.substringAfterLast("/")
                return "https://raw.githubusercontent.com/hexated/cloudstream-extensions-hexated/builds/$fileName"
            }
            return "https://raw.githubusercontent.com/hexated/cloudstream-extensions-hexated/builds/plugins.json"
        }

        // Kekik feroxx repository — `maarrem/cs-Kekik` ve `keyiflerolsun` eski adresleri
        // (README'de duyurulan otomatik geçiş) güncel fork'a yönlendirilir.
        if (cleanUrl.contains("feroxx/Kekik-cloudstream", ignoreCase = true) ||
            cleanUrl.contains("keyiflerolsun/Kekik-cloudstream", ignoreCase = true) ||
            cleanUrl.contains("maarrem/cs-Kekik", ignoreCase = true) ||
            cleanUrl.contains("/cs-Kekik", ignoreCase = true)) {
            val cleanPath = cleanUrl.substringBefore("?").substringBefore("#")
            if (cleanPath.endsWith(".cs3", ignoreCase = true)) {
                val fileName = cleanPath.substringAfterLast("/")
                return "https://raw.githubusercontent.com/feroxx/Kekik-cloudstream/builds/$fileName"
            }
            if (cleanPath.endsWith("plugins.json", ignoreCase = true)) {
                return "https://raw.githubusercontent.com/feroxx/Kekik-cloudstream/builds/plugins.json"
            }
            return "https://raw.githubusercontent.com/feroxx/Kekik-cloudstream/refs/heads/builds/repo.json"
        }

        // Handle general GitHub raw/web URLs
        if (url.startsWith("https://github.com/", ignoreCase = true)) {
            val withoutPrefix = url.removePrefix("https://github.com/")
            if (withoutPrefix.contains("/blob/") || withoutPrefix.contains("/raw/")) {
                val converted = withoutPrefix.replaceFirst("/blob/", "/").replaceFirst("/raw/", "/")
                return "https://raw.githubusercontent.com/$converted"
            }
            if (withoutPrefix.split("/").size == 2 && !withoutPrefix.endsWith(".json")) {
                return "https://raw.githubusercontent.com/$withoutPrefix/master/repo.json"
            }
        }

        return url
    }

    /**
     * İndirme bağlantısı için alternatif ayna (mirror) ve vekil sunucu URL adayları üretir.
     * Bir bağlantı 404, DNS hatası veya ISS engeline takıldığında sırayla diğer adaylar denenir.
     */
    fun getCandidateDownloadUrls(scraperId: String, rawUrl: String): List<String> {
        val candidates = linkedSetOf<String>()
        val normalized = normalizeUrl(rawUrl)
        candidates.add(normalized)

        // 0. Dosya adı VARYANTLARI.
        // Neden gerekli: eklenti kaydındaki `internalName` ile depodaki .cs3 dosya adı
        // birebir uyuşmadığında indirme 404 alıyor ve kullanıcı "eklenti indirilemiyor /
        // boş dönüyor" hatası görüyordu. Gerçek örnekler (Kitsugi-Plugins, 2026-10):
        //   internalName `WFilmİzle`     → depodaki dosya `WFilmizle.cs3`
        //   internalName `CanliTV`      → depodaki dosya `CanliTv.cs3`
        //   internalName `DDizi`        → depodaki dosya `Ddizi.cs3`
        //   internalName `Kanal 7`      → depodaki dosya `Kanal7.cs3`
        // Aşağıda Türkçe karakter katlaması + başlık biçimi + boşluk temizliği ile üretilen
        // varyantlar sırayla denenir; ilk başarılı indirme kazanır.
        val baseDirs = normalized.substringBeforeLast('/', "")
        val originalName = normalized.substringBefore("?").substringAfterLast("/").removeSuffix(".cs3")

        // Bilinen ad eşlemeleri (eski/yeni marka adı farkları)
        val alias = CS3_NAME_ALIASES[pluginIdentityKey(originalName)]

        val names = linkedSetOf<String>()
        alias?.let { names.add(it) }
        names.add(originalName)
        names.addAll(cs3NameVariants(originalName))

        // Dosya adı zaten URL'de olduğu gibi varsa listenin başında kalması için yalnızca
        // farklı isimleri ekle (aynı URL iki kez denenmesin).
        for (name in names) {
            if (name.isBlank() || name == originalName) continue
            if (baseDirs.isNotBlank()) {
                candidates.add("$baseDirs/$name.cs3")
            }
            // Depo `prebuilt/` alt klasörü kullanıyorsa orayı da dene
            if (!baseDirs.endsWith("/prebuilt")) {
                candidates.add("https://raw.githubusercontent.com/f26901964-eng/Kitsugi-Plugins/builds/prebuilt/$name.cs3")
            }
        }

        // 1. jsDelivr proxy alternatifi
        val proxyUrl = applyGithubProxy(normalized)
        if (proxyUrl != normalized) {
            candidates.add(proxyUrl)
        }

        // 2. Kitsugi havuzu aynaları
        if (normalized.contains("Kitsugi-Plugins", ignoreCase = true) ||
            normalized.contains("KitsugiPlugins", ignoreCase = true) ||
            normalized.contains("gameras1010-afk", ignoreCase = true) ||
            normalized.contains("f26901964-eng", ignoreCase = true)) {
            // When raw GitHub is blocked, try the official current build via the API
            // before a possibly much older prebuilt/mirror (e.g. Anizm v1 vs v20).
            candidates.add("https://api.github.com/repos/f26901964-eng/Kitsugi-Plugins/contents/$scraperId.cs3?ref=builds")
            candidates.add("https://raw.githubusercontent.com/gameras1010-afk/Kitsugi-Plugins/builds/$scraperId.cs3")
            candidates.add("https://cdn.jsdelivr.net/gh/gameras1010-afk/Kitsugi-Plugins@builds/$scraperId.cs3")
            candidates.add("https://raw.githubusercontent.com/f26901964-eng/Kitsugi-Plugins/builds/prebuilt/$scraperId.cs3")
            candidates.add("https://cdn.jsdelivr.net/gh/f26901964-eng/Kitsugi-Plugins@builds/prebuilt/$scraperId.cs3")

            // api.github.com aynası: raw.githubusercontent.com ISS/BTK tarafından
            // engellendiğinde (Türkiye'de sık) ikili içeriği yine de indirir.
            // İndirme katmanı bu adresler için `Accept: application/vnd.github.raw` gönderir
            // (aksi halde GitHub base64 JSON döner ve ZIP doğrulaması başarısız olur).
            candidates.add("https://api.github.com/repos/f26901964-eng/Kitsugi-Plugins/contents/prebuilt/$scraperId.cs3?ref=builds")

            // 2b. Codeberg (eski birincil havuz) SON ÇARE yedeği.
            // Kitsugi-Plugins deposu Codeberg havuzunun yerini aldı; ancak bazı eklentiler
            // (ör. `Filmmirasım`, `FullHDFilmİzlede`, `YesilCamTv`, `__New`) yeni deponun
            // `builds` dalında hiç derlenmemiş durumda. Bu durumda eski havuz hâlâ tek
            // çalışan kaynak oluyor — aksi halde eklenti tamamen indirilemez oluyordu.
            // Varyant adlarını da Codeberg üzerinde dene.
            for (name in names) {
                if (name.isBlank()) continue
                candidates.add("https://codeberg.org/BlackDamage/KitsugiPlugins/raw/branch/builds/$name.cs3")
            }
            candidates.add("https://codeberg.org/BlackDamage/KitsugiPlugins/raw/branch/builds/prebuilt/$scraperId.cs3")
        }

        // 3. Kraptor havuzu aynaları
        if (normalized.contains("cs-kraptor", ignoreCase = true) || normalized.contains("Kraptor123", ignoreCase = true)) {
            candidates.add("https://raw.githubusercontent.com/Kraptor123/cs-kraptor/builds/$scraperId.cs3")
            candidates.add("https://cdn.jsdelivr.net/gh/Kraptor123/cs-kraptor@builds/$scraperId.cs3")
        }

        // 4. Kekik feroxx aynaları
        if (normalized.contains("Kekik-cloudstream", ignoreCase = true) || normalized.contains("feroxx", ignoreCase = true)) {
            candidates.add("https://raw.githubusercontent.com/feroxx/Kekik-cloudstream/builds/$scraperId.cs3")
            candidates.add("https://cdn.jsdelivr.net/gh/feroxx/Kekik-cloudstream@builds/$scraperId.cs3")
        }

        return candidates.toList()
    }

    /**
     * Eklenti adından indirilebilir `.cs3` dosya adı varyantları üretir.
     *
     * Örnek: `"WFilmİzle"` → `["WFilmizle", "Wfilmizle", "WFILMIZLE"]`,
     *        `"CanliTV"` → `["CanliTv", "CANLITV"]`, `"Kanal 7"` → `["Kanal7", "Kanal 7"]`.
     */
    internal fun cs3NameVariants(name: String): List<String> {
        val folded = foldToAscii(name)
        val noSpace = folded.replace(" ", "")
        val variants = linkedSetOf(folded, noSpace)
        // Kısaltma katlama: "CanliTV" → "CanliTv", "DDizi" → "Ddizi"
        variants.add(collapseAcronyms(noSpace))
        variants.add(noSpace.lowercase())
        variants.add(noSpace.uppercase())
        return variants.filter { it.isNotBlank() }
    }

    /**
     * Ardışık büyük harf gruplarını (kısaltmaları) başlık biçimine indirger:
     * `"CanliTV"` → `"CanliTv"`, `"DDizi"` → `"Ddizi"`, `"AnimeAV"` → `"AnimeAv"`.
     *
     * Neden: depodaki gerçek dosya adları bu biçimde (`CanliTv.cs3`, `Ddizi.cs3`), kayıttaki
     * `internalName` ise bitişik büyük harfle yazılmış (`CanliTV`, `DDizi`) — doğrudan
     * eşleştirme 404 veriyordu.
     */
    internal fun collapseAcronyms(name: String): String {
        val sb = StringBuilder(name.length)
        var i = 0
        while (i < name.length) {
            val c = name[i]
            if (c.isUpperCase()) {
                var j = i
                while (j < name.length && name[j].isUpperCase()) j++
                if (j - i >= 2) {
                    sb.append(name[i])
                    sb.append(name.substring(i + 1, j).lowercase())
                } else {
                    sb.append(c)
                }
                i = j
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    /** Türkçe karakterleri ASCII karşılıklarına indirger; ı/İ/I → i/I, ş/Ş→s/S, ğ/Ğ→g/G, ü/Ü→u/U, ö/Ö→o/O, ç/Ç→c/C. */
    internal fun foldToAscii(name: String): String {
        val sb = StringBuilder(name.length)
        for (ch in name) {
            when (ch) {
                'İ', 'ı', 'i' -> sb.append('i')
                'I' -> sb.append('I')
                'Ş' -> sb.append('S')
                'ş' -> sb.append('s')
                'Ğ' -> sb.append('G')
                'ğ' -> sb.append('g')
                'Ü' -> sb.append('U')
                'ü' -> sb.append('u')
                'Ö' -> sb.append('O')
                'ö' -> sb.append('o')
                'Ç' -> sb.append('C')
                'ç' -> sb.append('c')
                else -> sb.append(ch)
            }
        }
        return sb.toString().replace("\u0307", "")
    }



    /** Eklenti adını karşılaştırma anahtarına indirger (küçük harf + ASCII + noktalama yok). */
    internal fun pluginIdentityKey(name: String): String =
        foldToAscii(name).lowercase().filter { it.isLetterOrDigit() }

    /**
     * Eski/yeni marka adı farkları: kayıttaki `internalName` ile depodaki `.cs3` dosya adı
     * tamamen farklı olduğunda kullanılır. Yalnızca DOĞRULANMIŞ eşlemeler yer alır.
     */
    private val CS3_NAME_ALIASES = mapOf(
        // Kitsugi-Plugins builds: eski ad → yeni ad
        "cizgivedizi" to "CizgiVeDizi",
        "dizikorea" to "DiziKorea",
        "sinewix" to "Sinewix",
        "kanal7" to "Kanal7"
    )

    /**
     * Appends a timestamp query parameter to bypass CDN/Fastly caches for instant updates.
     */
    fun withCacheBuster(url: String): String {
        val trimmed = url.trim()
        val sep = if (trimmed.contains("?")) "&" else "?"
        return "$trimmed${sep}_ts=${System.currentTimeMillis()}"
    }

    /**
     * GitHub Vekil Sunucu — CS3'ün "GitHub proxy" özelliğinin muadili.
     *
     * ISS'ler raw.githubusercontent.com'u engellediyse, URL'yi jsDelivr CDN üzerinden
     * yönlendirir. jsDelivr birkaç günlük önbellek gecikmesine sahip olabilir.
     *
     * Dönüşüm örneği:
     *   https://raw.githubusercontent.com/maarrem/cs-Kekik/master/repo.json
     *   → https://cdn.jsdelivr.net/gh/maarrem/cs-Kekik@master/repo.json
     *
     * @param url Herhangi bir URL; raw.githubusercontent.com değilse değiştirilmez.
     */
    fun applyGithubProxy(url: String): String {
        val prefix = "https://raw.githubusercontent.com/"
        if (!url.startsWith(prefix)) return url
        // Format: /user/repo/branch/path → cdn.jsdelivr.net/gh/user/repo@branch/path
        val rest = url.removePrefix(prefix)       // "user/repo/branch/path"
        val parts = rest.split("/", limit = 3)    // ["user", "repo", "branch/path"]
        if (parts.size < 3) return url
        val (user, repo, branchAndPath) = parts
        val branchSep = branchAndPath.indexOf('/')
        return if (branchSep == -1) {
            // Sadece branch, path yok — olası değil ama güvenli işle
            "https://cdn.jsdelivr.net/gh/$user/$repo@$branchAndPath"
        } else {
            val branch = branchAndPath.substring(0, branchSep)
            val path   = branchAndPath.substring(branchSep + 1)
            "https://cdn.jsdelivr.net/gh/$user/$repo@$branch/$path"
        }
    }

    /**
     * normalizeUrl + isteğe bağlı GitHub proxy dönüşümü.
     * [useProxy] true ise ve URL raw.githubusercontent.com ise jsDelivr'a yönlendirir.
     */
    fun normalizeAndProxy(rawUrl: String, useProxy: Boolean): String {
        val normalized = normalizeUrl(rawUrl)
        return if (useProxy) applyGithubProxy(normalized) else normalized
    }

    /**
     * Verilen girdinin bir Cloudstream kısa kodu olup olmadığını kontrol eder.
     * Kısa kod: sadece harf/rakam/tire/alt çizgi/ünlem içeren ve nokta veya slash içermeyen string.
     * Örnek: "kekikdevam" → true, "https://..." → false
     */
    fun isShortCode(input: String): Boolean {
        val trimmed = input.trim()
        return trimmed.isNotBlank() &&
               !trimmed.contains('.') &&
               !trimmed.contains('/') &&
               !trimmed.startsWith("http") &&
               trimmed.matches(SHORT_CODE_REGEX)
    }

    /**
     * cutt.ly URL kısaltma servisi üzerinden kısa kodu gerçek repo URL'sine çözer.
     * Cloudstream topluluğu repo URL'lerini cutt.ly kısa kodlarıyla paylaşır.
     *
     * Örnek: "kekikdevam" → "https://raw.githubusercontent.com/Kekik.../repo.json"
     *
     * @return Gerçek URL veya null (geçersiz / bulunamayan kısa kod)
     */
    suspend fun resolveShortCode(shortCode: String): String? {
        return try {
            Log.d(TAG, "Kısa kod çözülüyor: cutt.ly/$shortCode")
            val request = Request.Builder()
                .url("https://cutt.ly/$shortCode")
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val noRedirectClient = com.kitsugi.animelist.core.network.KitsugiHttpClient.client.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build()

            noRedirectClient.newCall(request).execute().use { response ->
                val location = response.header("Location")
                if (location == null) {
                    Log.w(TAG, "cutt.ly/$shortCode → Location header yok")
                    return null
                }
                if (location.startsWith("https://cutt.ly/404") || location.removeSuffix("/") == "https://cutt.ly") {
                    Log.w(TAG, "cutt.ly/$shortCode → Geçersiz kısa kod (404)")
                    return null
                }

                Log.d(TAG, "Kısa kod çözüldü: $shortCode → $location")
                location
            }
        } catch (e: Exception) {
            Log.e(TAG, "Kısa kod çözme hatası: $shortCode", e)
            null
        }
    }
}
