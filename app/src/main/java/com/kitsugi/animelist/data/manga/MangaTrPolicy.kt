package com.kitsugi.animelist.data.manga

/** Restrict the special login flow to the Manga-TR extension, not arbitrary sites. */
internal fun isMangaTrLoginRequired(packageName: String, message: String?): Boolean =
    packageName.endsWith(".mangatr", ignoreCase = true) &&
        message?.contains("üye girişi", ignoreCase = true) == true

/** A guessed mirror may belong to someone else; Manga-TR owns its domain selection. */
internal fun allowsAutomaticMangaMirror(packageName: String): Boolean =
    !packageName.endsWith(".mangatr", ignoreCase = true)

/** Manga-TR's extension handles its own challenge and image transformation. */
fun isMangaTrHost(host: String): Boolean =
    host.equals("manga-tr.com", true) || host.endsWith(".manga-tr.com", true) ||
        host.equals("mngtr.site", true) || host.endsWith(".mngtr.site", true)

/** Keep the complete URL including fragment; transformed images must not reuse old cache entries. */
fun mangaImageCacheKey(packageName: String, imageUrl: String): String =
    if (packageName.endsWith(".mangatr", ignoreCase = true)) "mangatr-descrambled-v1:$imageUrl" else imageUrl

/**
 * Numeric fragments are the extension's strip permutation. Reject only a
 * provably invalid permutation; other anchors belong to the extension unchanged.
 */
internal fun isInvalidMangaTrImageOrder(fragment: String?): Boolean {
    if (fragment.isNullOrEmpty() || fragment.any { !it.isDigit() && it != ',' }) return false
    val pieces = fragment.split(',')
    val codes = pieces.map { it.toIntOrNull() ?: return true }
    return codes.any { it / 100 !in 0..3 || it % 100 !in codes.indices } ||
        codes.map { it % 100 }.distinct().size != codes.size
}

/** Repository metadata must describe the APK itself, not merely its filename. */
fun matchesMangaApkMetadata(expectedPackage: String, expectedVersion: Long?, actualPackage: String?, actualVersion: Long?): Boolean =
    actualPackage == expectedPackage && actualVersion != null &&
        (expectedVersion == null || expectedVersion <= 0 || actualVersion == expectedVersion)

private val RAW_GITHUB_APK_REGEX =
    Regex("""^https?://raw\.githubusercontent\.com/([^/]+)/([^/]+)/([^/]+)/(.+\.apk)(\?.*)?$""", RegexOption.IGNORE_CASE)

/**
 * Builds ordered download candidates for an extension APK URL.
 *
 * When a `raw.githubusercontent.com/<owner>/<repo>/builds/apk/<file>.apk` catalog entry lags
 * behind `<owner>/<repo>/main/apk/<file>.apk` (or vice-versa, before `sync_enabled_plugins`
 * finishes mirroring `main` -> `builds`), include same-repository (`<owner>/<repo>`) sibling
 * branch paths so `downloadExtension` can verify each candidate against `matchesMangaApkMetadata`
 * and pick the exact matching APK without ever crossing repository boundaries.
 */
internal fun buildMangaApkDownloadCandidates(
    urlString: String,
    applyMirror: (String) -> String = { it },
): List<String> {
    val trimmed = urlString.trim()
    if (trimmed.isEmpty()) return emptyList()

    val directCandidates = linkedSetOf<String>()
    directCandidates += trimmed

    val match = RAW_GITHUB_APK_REGEX.matchEntire(trimmed)
    if (match != null) {
        val owner = match.groupValues[1]
        val repo = match.groupValues[2]
        val ref = match.groupValues[3]
        val restPath = match.groupValues[4]
        val query = match.groupValues[5]
        val fileName = restPath.substringAfterLast('/')

        if (ref.equals("builds", ignoreCase = true) ||
            ref.equals("main", ignoreCase = true) ||
            ref.equals("master", ignoreCase = true)
        ) {
            directCandidates += "https://raw.githubusercontent.com/$owner/$repo/main/apk/$fileName$query"
            directCandidates += "https://raw.githubusercontent.com/$owner/$repo/main/builds/apk/$fileName$query"
            directCandidates += "https://raw.githubusercontent.com/$owner/$repo/builds/apk/$fileName$query"
            directCandidates += "https://raw.githubusercontent.com/$owner/$repo/builds/builds/apk/$fileName$query"
        }
    }

    val allCandidates = linkedSetOf<String>()
    allCandidates.addAll(directCandidates)
    directCandidates.forEach { candidate ->
        val mirrored = applyMirror(candidate).trim()
        if (mirrored.isNotEmpty()) allCandidates += mirrored
    }
    return allCandidates.toList()
}
