package com.kitsugi.animelist.data.local

import com.kitsugi.animelist.data.model.WatchHistoryEntry
import com.kitsugi.animelist.data.repository.StreamSource
import com.kitsugi.animelist.data.repository.extractTorrentSchemeFileIdx
import java.net.URI
import java.util.Locale

/** Bridge between a history record and fresh streams returned by the installed addons. */
object WatchHistorySource {
    fun stremioAddonId(manifestUrl: String): String = "stremio:${WatchHistoryRules.sha256(manifestUrl.trim())}"
    fun cloudStreamAddonId(pluginId: String): String = "cs:$pluginId"
    fun cloudStreamPluginId(addonId: String?): String? = addonId
        ?.takeIf { it.startsWith("cs:") }
        ?.removePrefix("cs:")
        ?.takeIf { it.isNotBlank() }

    /** Hash endpoint and content query separately, omitting common expiring credentials. */
    fun urlKey(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            val uri = URI(url)
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            if (scheme !in listOf("http", "https")) return null
            val host = uri.host?.lowercase(Locale.ROOT)?.removeSurrounding("[", "]")?.trimEnd('.') ?: return null
            if (host == "localhost" || host.endsWith(".localhost") || host.startsWith("127.") ||
                host == "::1" || host == "0:0:0:0:0:0:0:1" || host == "0.0.0.0" || host == "::") return null
            val stableQuery = uri.rawQuery.orEmpty().split('&').filter { parameter ->
                val key = java.net.URLDecoder.decode(parameter.substringBefore('='), "UTF-8").lowercase(Locale.ROOT)
                key.isNotBlank() && key !in VOLATILE_QUERY_KEYS &&
                    !key.startsWith("x-amz-") && !key.startsWith("x-goog-")
            }.sorted().joinToString("&")
            val endpoint = WatchHistoryRules.sha256("$scheme://$host:${uri.port}${uri.rawPath.orEmpty()}")
            "$endpoint:${WatchHistoryRules.sha256(stableQuery)}"
        }.getOrNull()
    }

    private val VOLATILE_QUERY_KEYS = setOf(
        "token", "access_token", "auth", "authorization", "signature", "sig", "expires", "expiry", "exp",
        "api_key", "apikey", "api-key", "jwt", "session", "sessionid", "session_id", "policy", "hdnts", "hdnea"
    )

    fun withSource(entry: WatchHistoryEntry, source: StreamSource, resolvedUrl: String): WatchHistoryEntry {
        val sameCsApi = source.isCS && !source.addonId.isNullOrBlank() && source.addonId == entry.addonId &&
            entry.cs3ApiName.equals(source.cs3ApiName ?: source.addonName, ignoreCase = true)
        return entry.copy(
            source = source.addonName,
            addonId = source.addonId,
            quality = source.quality,
            streamName = source.name,
            streamTitle = source.title,
            streamInfoHash = source.p2pHash ?: source.infoHash,
            streamFileIndex = source.fileIndex ?: source.url.extractTorrentSchemeFileIdx(),
            streamUrlKey = urlKey(source.url),
            streamAudioKind = source.providerAudioKind,
            streamLanguages = source.providerLanguages.takeIf { it.isNotEmpty() },
            streamUrl = resolvedUrl,
            streamHeaders = source.requestHeaders,
            cs3Url = if (source.isCS) source.cs3PageUrl ?: entry.cs3Url.takeIf { sameCsApi } else null,
            cs3ApiName = if (source.isCS) source.cs3ApiName ?: source.addonName else null
        )
    }

    /** A saved file hash or URL fingerprint is stronger than a display-name match. */
    fun hasExactFingerprint(entry: WatchHistoryEntry, source: StreamSource): Boolean {
        val hash = entry.streamInfoHash?.takeIf { it.isNotBlank() }
        if (hash != null) {
            return (source.p2pHash ?: source.infoHash).equals(hash, ignoreCase = true) &&
                (entry.streamFileIndex == null ||
                    (source.fileIndex ?: source.url.extractTorrentSchemeFileIdx()) == entry.streamFileIndex)
        }
        val savedKey = entry.streamUrlKey ?: urlKey(entry.streamUrl)
        return savedKey != null && savedKey == urlKey(source.url)
    }

    /**
     * Only auto-play the recorded addon and a recognisable server/torrent. If it is gone,
     * return null so the picker can explain the problem and let the user choose instead
     * of silently starting a different provider or an unrelated release.
     */
    fun findSavedStream(entry: WatchHistoryEntry, streams: List<StreamSource>): StreamSource? {
        val addonId = entry.addonId?.takeIf { it.isNotBlank() }
        val addonName = entry.source?.trim()?.takeIf { it.isNotEmpty() }
        val candidates = if (addonId != null) {
            streams.filter { it.addonId == addonId }
        } else if (addonName != null) {
            streams.filter { it.addonName.equals(addonName, ignoreCase = true) }
        } else return null
        if (candidates.isEmpty()) return null

        // Legacy entries have only a display name. Do not guess if two installed
        // providers share that name and their actual identities differ.
        if (addonId == null && candidates.mapNotNull { it.addonId }.distinct().size > 1) return null

        val hash = entry.streamInfoHash?.takeIf { it.isNotBlank() }
        val matches = if (hash != null) {
            candidates.filter { hasExactFingerprint(entry, it) }
        } else {
            val savedUrlKey = entry.streamUrlKey ?: urlKey(entry.streamUrl)
            val exactUrl = savedUrlKey?.let { key -> candidates.filter { urlKey(it.url) == key } }.orEmpty()
            if (exactUrl.isNotEmpty()) exactUrl else {
                // On the same endpoint, a different non-auth query can identify a
                // different video. Do not override that conflict with a generic title.
                val fallbackCandidates = candidates.filterNot { candidate ->
                    val candidateKey = urlKey(candidate.url)
                    savedUrlKey?.contains(':') == true && candidateKey != null &&
                        savedUrlKey.substringBefore(':') == candidateKey.substringBefore(':') && savedUrlKey != candidateKey
                }
                val byTitle = entry.streamTitle?.takeIf { it.isNotBlank() }?.let { title ->
                    fallbackCandidates.filter {
                        it.title.equals(title, ignoreCase = true) &&
                            (it.isCS || entry.streamName.isNullOrBlank() || it.name.equals(entry.streamName, ignoreCase = true))
                    }
                }.orEmpty()
                if (byTitle.isNotEmpty()) byTitle else {
                    // CloudStream names contain the extractor/server name. A Stremio
                    // display name can be shared by hundreds of unrelated torrents.
                    val byServer = entry.streamName?.takeIf { it.isNotBlank() }?.let { name ->
                        fallbackCandidates.filter { it.isCS && it.name.equals(name, ignoreCase = true) }
                    }.orEmpty()
                    when {
                        byServer.isNotEmpty() -> byServer
                        entry.streamName.isNullOrBlank() && entry.streamTitle.isNullOrBlank() && fallbackCandidates.size == 1 -> fallbackCandidates
                        else -> emptyList()
                    }
                }
            }
        }
        fun score(candidate: StreamSource): Int =
            (if (candidate.name.equals(entry.streamName, ignoreCase = true)) 8 else 0) +
                (if (candidate.title.equals(entry.streamTitle, ignoreCase = true)) 8 else 0) +
                (if (!entry.quality.isNullOrBlank() && candidate.quality.equals(entry.quality, ignoreCase = true)) 4 else 0) +
                (if (!entry.streamAudioKind.isNullOrBlank() && candidate.providerAudioKind.equals(entry.streamAudioKind, ignoreCase = true)) 4 else 0) +
                (if (!entry.streamLanguages.isNullOrEmpty() && candidate.providerLanguages.map { it.lowercase(Locale.ROOT) }.toSet() ==
                    entry.streamLanguages.map { it.lowercase(Locale.ROOT) }.toSet()) 4 else 0)

        val bestScore = matches.maxOfOrNull(::score) ?: return null
        val best = matches.filter { score(it) == bestScore }
        // If equally plausible results refer to different files/URLs, don't guess.
        val identities = best.map { candidate ->
            val torrent = candidate.p2pHash ?: candidate.infoHash
            if (!torrent.isNullOrBlank()) {
                "torrent:${torrent.lowercase(Locale.ROOT)}:${candidate.fileIndex ?: candidate.url.extractTorrentSchemeFileIdx()}" to null
            } else {
                candidate.url.orEmpty().substringBefore('#') to candidate.requestHeaders
            }
        }.distinct()
        return best.firstOrNull().takeIf { identities.size <= 1 }
    }
}
