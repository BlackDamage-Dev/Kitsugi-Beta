package com.kitsugi.animelist.core.update

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.BuildConfig
import com.kitsugi.animelist.core.network.KitsugiHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class KitsugiUpdateRepository(
    private val codebergOwner: String = "BlackDamage",
    private val codebergRepo: String = "Kitsugi-Beta",
    private val githubOwner: String = "f26901964-eng",
    private val githubRepo: String = "Kitsugi-Beta"
) {

    companion object {
        private const val TAG = "KitsugiUpdateRepo"
        private const val GITHUB_RELEASES_API = "https://api.github.com/repos/%s/%s/releases/latest"
        private const val CODEBERG_RELEASES_API = "https://codeberg.org/api/v1/repos/%s/%s/releases/latest"
    }

    suspend fun checkForUpdate(): Result<AppRelease?> = withContext(Dispatchers.IO) {
        runCatching {
            val json = fetchLatestReleaseJson() ?: return@runCatching null

            val tagName = json.optString("tag_name", "").trim()
            val fetchedVersionCode = parseVersionCodeFromTag(tagName, json)

            val isNewer = isNewerRelease(
                currentVersionName = BuildConfig.VERSION_NAME,
                fetchedTag = tagName,
                currentVersionCode = BuildConfig.VERSION_CODE,
                fetchedVersionCode = fetchedVersionCode
            )

            Log.d(TAG, "Current: ${BuildConfig.VERSION_NAME} (code: ${BuildConfig.VERSION_CODE}), Fetched: $tagName (code: $fetchedVersionCode) -> isNewer: $isNewer")

            if (!isNewer) {
                return@runCatching null // No newer update
            }

            buildRelease(json, requireApk = true)
        }
    }

    /**
     * "Uygulama Hakkında → Sürüm Notları" kartı için **en son sürümün** notlarını döner.
     *
     * [checkForUpdate]'ten farkı:
     *  - Yeni sürüm koşulu aranmaz: kurulu sürüm en güncel olsa da son sürümün notları gösterilir.
     *  - APK eki zorunlu değildir; yalnızca notlar gösterileceği için indirme bağlantısı boş kalabilir.
     *  - Hakkında kartı GitHub'ı tek doğruluk kaynağı olarak kullanır; gecikebilen Codeberg aynası
     *    sürüm notu diye eski bir sürümü göstermesin. Codeberg yedeği kurulum denetiminde korunur.
     *  - Önbellek uygulama sürümüne bağlıdır; yeni APK eski sürümün notlarını yeniden kullanmaz.
     */
    suspend fun latestRelease(context: Context, force: Boolean = false): Result<AppRelease?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val appVersion = BuildConfig.VERSION_NAME
                if (!force) {
                    ReleaseNotesCache.read(context, appVersion)?.let { return@runCatching it }
                }
                val json = fetchLatestGitHubReleaseJson()
                    ?: throw IOException("GitHub Releases API yanıt vermedi.")
                val release = buildRelease(json, requireApk = false) ?: return@runCatching null
                ReleaseNotesCache.write(context, release, appVersion)
                release
            }
        }

    /**
     * Kurulu sürüme göre [release] daha yeni mi? Hakkında ekranındaki "Güncelleme var" rozeti
     * bunu kullanır (güncelleme akışının karar mantığıyla aynı fonksiyon).
     */
    fun isNewerThanInstalled(release: AppRelease): Boolean = isNewerRelease(
        currentVersionName = BuildConfig.VERSION_NAME,
        fetchedTag = "v${release.versionName}",
        currentVersionCode = BuildConfig.VERSION_CODE,
        fetchedVersionCode = release.versionCode
    )

    /**
     * Güncelleme denetimi için GitHub (birincil) → Codeberg (yedek) sırasıyla yanıt getirir.
     * Kurulum akışı aynayı kullanabilir; sürüm notları ise yalnızca GitHub kaynağını kullanır.
     */
    private fun fetchLatestReleaseJson(): JSONObject? {
        val endpoints = listOf(
            String.format(GITHUB_RELEASES_API, githubOwner, githubRepo),
            String.format(CODEBERG_RELEASES_API, codebergOwner, codebergRepo)
        )
        return endpoints.firstNotNullOfOrNull(::fetchReleaseJson)
    }

    /** Sürüm notlarının kanonik kaynağı; gecikebilen Codeberg aynasına düşmez. */
    private fun fetchLatestGitHubReleaseJson(): JSONObject? = fetchReleaseJson(
        String.format(GITHUB_RELEASES_API, githubOwner, githubRepo)
    )

    private fun fetchReleaseJson(url: String): JSONObject? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "KitsugiApp/${BuildConfig.VERSION_NAME}")
                .header("Accept", "application/json")
                .header("Cache-Control", "no-cache")
                .build()

            KitsugiHttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Update check failed for $url with code: ${response.code}")
                    null
                } else {
                    val bodyString = response.body?.string()
                    if (bodyString.isNullOrBlank()) {
                        Log.w(TAG, "Empty release response from: $url")
                        null
                    } else {
                        Log.d(TAG, "Successfully fetched update release info from: $url")
                        JSONObject(bodyString)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking update from $url: ${e.message}")
            null
        }
    }

    /**
     * API yanıtını [AppRelease]'e çevirir.
     *
     * @param requireApk true → indirilebilir APK eki yoksa null döner: kurulum yapılamayacağı
     *   için sürüm "güncelleme var" sayılmaz. false → yalnızca sürüm notları gösterilecek
     *   (Hakkında ekranı), bu yüzden APK aranmaz.
     */
    private fun buildRelease(json: JSONObject, requireApk: Boolean): AppRelease? {
        val tagName = json.optString("tag_name", "").trim()
        val releaseTitle = json.optString("name", tagName).ifEmpty { "Yeni Güncelleme" }
        val releaseNotes = json.optString("body", "Detaylı değişiklik notu bulunmuyor.")
        val publishedAt = formatReleaseDate(json.optString("published_at", ""))
        val (downloadUrl, apkSize) = selectApkAsset(json)

        if (downloadUrl.isEmpty()) {
            if (requireApk) {
                Log.w(TAG, "New release found ($tagName), but no valid APK asset attached.")
                return null
            }
            Log.i(TAG, "$tagName sürümünde APK eki yok; yalnızca sürüm notları gösterilecek.")
        }

        return AppRelease(
            versionCode = parseVersionCodeFromTag(tagName, json),
            versionName = tagName.removePrefix("v"),
            releaseTitle = releaseTitle,
            releaseNotes = releaseNotes,
            downloadUrl = downloadUrl,
            apkSizeBytes = apkSize,
            publishedAt = publishedAt
        )
    }

    /** Yüklenen flavor'a (foss/gms) uyan APK ekini seçer; bulamazsa ilk APK'ya düşer. */
    private fun selectApkAsset(json: JSONObject): Pair<String, Long> {
        val assets = json.optJSONArray("assets") ?: JSONArray()
        val currentFlavor = BuildConfig.FLAVOR.lowercase()
        var downloadUrl = ""
        var apkSize = 0L
        var fallbackUrl = ""
        var fallbackSize = 0L

        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name", "").lowercase()
            val url = asset.optString("browser_download_url", "")
            val size = asset.optLong("size", 0L)

            if (name.endsWith(".apk") || asset.optString("content_type") == "application/vnd.android.package-archive") {
                if (fallbackUrl.isEmpty()) {
                    fallbackUrl = url
                    fallbackSize = size
                }
                if (currentFlavor.isNotEmpty() && name.contains(currentFlavor)) {
                    downloadUrl = url
                    apkSize = size
                    break
                }
            }
        }

        if (downloadUrl.isEmpty()) {
            downloadUrl = fallbackUrl
            apkSize = fallbackSize
        }
        return downloadUrl to apkSize
    }

    private fun formatReleaseDate(isoDate: String): String {
        if (isoDate.isBlank()) return ""
        return try {
            val parsed = java.time.OffsetDateTime.parse(isoDate)
            val trZone = java.time.ZoneId.of("Europe/Istanbul")
            val formatter = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale("tr", "TR"))
            parsed.atZoneSameInstant(trZone).format(formatter)
        } catch (e: Exception) {
            try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                inputFormat.timeZone = TimeZone.getTimeZone("UTC")
                val date = inputFormat.parse(isoDate) ?: return isoDate
                val outputFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("tr", "TR"))
                outputFormat.timeZone = TimeZone.getTimeZone("Europe/Istanbul")
                outputFormat.format(date)
            } catch (e2: Exception) {
                isoDate
            }
        }
    }

    private fun parseVersionCodeFromTag(tag: String, json: JSONObject): Int {
        val cleanTag = tag.removePrefix("v").trim()

        // If tag is pure numbers (e.g. 29762145)
        cleanTag.toIntOrNull()?.let { return it }

        // If tag is like 2.4.0-beta.29762145, extract the last numeric segment
        val lastSegment = cleanTag.substringAfterLast(".").toIntOrNull()
        if (lastSegment != null && lastSegment > 100000) {
            return lastSegment
        }

        // Fallback: check assets for versionCode in name (e.g., Kitsugi-29762145.apk)
        val assets = json.optJSONArray("assets") ?: JSONArray()
        for (i in 0 until assets.length()) {
            val assetName = assets.optJSONObject(i)?.optString("name", "") ?: continue
            val apkDigits = assetName.filter { it.isDigit() }.toIntOrNull()
            if (apkDigits != null && apkDigits > 100000) {
                return apkDigits
            }
        }

        return 0
    }

    private fun isNewerRelease(
        currentVersionName: String,
        fetchedTag: String,
        currentVersionCode: Int,
        fetchedVersionCode: Int
    ): Boolean {
        // 1. If timestamp-based versionCode is fetched and strictly greater
        if (fetchedVersionCode > 100000 && currentVersionCode > 100000 && fetchedVersionCode > currentVersionCode) {
            return true
        }

        // 2. Semantic version comparison (e.g., v2.4.40 vs 2.4.39)
        val cleanCurrent = currentVersionName.removePrefix("v").substringBefore("-").trim()
        val cleanFetched = fetchedTag.removePrefix("v").substringBefore("-").trim()

        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
        val fetchedParts = cleanFetched.split(".").mapNotNull { it.toIntOrNull() }

        for (i in 0 until maxOf(currentParts.size, fetchedParts.size)) {
            val curr = currentParts.getOrElse(i) { 0 }
            val fetch = fetchedParts.getOrElse(i) { 0 }
            if (fetch > curr) return true
            if (fetch < curr) return false
        }

        return false
    }
}

