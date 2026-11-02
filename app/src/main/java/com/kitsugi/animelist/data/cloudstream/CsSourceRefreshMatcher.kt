package com.kitsugi.animelist.data.cloudstream

import com.kitsugi.animelist.data.repository.StreamSource
import java.net.URI
import java.util.Locale

/** Match a newly extracted link to the selected source, not just any link from the provider. */
internal object CsSourceRefreshMatcher {
    fun match(previous: StreamSource, previousList: List<StreamSource>, refreshed: List<StreamSource>): StreamSource? {
        fun sameLabel(candidate: StreamSource): Boolean =
            candidate.isCS && candidate.addonName.equals(previous.addonName, true) &&
                candidate.name == previous.name && candidate.title == previous.title &&
                candidate.qualityValue == previous.qualityValue

        val candidates = refreshed.filter(::sameLabel)
        val exact = candidates.filter { it.url == previous.url && !it.url.isNullOrBlank() }
        if (exact.size == 1) return exact.single()

        val key = stableUrlKey(previous.url)
        if (key != null) {
            val sameEndpoint = candidates.filter { stableUrlKey(it.url) == key }
            if (sameEndpoint.size == 1) return sameEndpoint.single()
        }

        // CDN paths sometimes change entirely on refresh. A label can be used only when
        // it was unique both before and after: Anizm often has several identical SendVid
        // labels, and arbitrarily picking one could play the wrong upload/fansub.
        val unique = candidates.singleOrNull()?.takeIf { previousList.count(::sameLabel) == 1 } ?: return null
        val previousDriveId = driveId(previous.url)
        val refreshedDriveId = driveId(unique.url)
        return unique.takeIf { previousDriveId == null || refreshedDriveId == null || previousDriveId == refreshedDriveId }
    }

    private fun driveId(url: String?): String? = try {
        if (url == null || !CsMediaResponsePolicy.isDriveDownloadUrl(url)) null else
            URI(url).rawQuery.orEmpty().split('&').firstOrNull { it.substringBefore('=').equals("id", true) }
    } catch (_: Exception) { null }

    private fun stableUrlKey(url: String?): String? {
        return try {
        val uri = URI(url ?: return null)
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        val path = uri.rawPath.orEmpty()
        val port = uri.port
        // Several Google Drive files share /uc or /download, so the file id is part
        // of their identity. Auth/confirmation parameters may legitimately change.
        val driveId = if (host.endsWith(".google.com") || host == "google.com") {
            uri.rawQuery.orEmpty().split('&').firstOrNull { it.substringBefore('=').equals("id", true) }
        } else null
        "$scheme://$host:$port$path${driveId?.let { "?$it" }.orEmpty()}"
        } catch (_: Exception) {
            null
        }
    }
}
