package com.kitsugi.animelist.core.player

import java.net.URI

/** A remote addon must not redirect the app to its own Stremio-only localhost proxy. */
object SubtitleUrlPolicy {
    fun isReachableForProvider(manifestUrl: String, subtitleUrl: String?): Boolean {
        val target = try { URI(subtitleUrl ?: return false) } catch (_: Exception) { return false }
        if (target.scheme?.lowercase() !in setOf("http", "https") || target.host.isNullOrBlank()) return false
        val provider = try { URI(manifestUrl) } catch (_: Exception) { return false }
        return !isLoopback(target.host) || isLoopback(provider.host)
    }

    private fun isLoopback(host: String?): Boolean {
        val value = host?.trim('[', ']')?.lowercase() ?: return false
        return value == "localhost" || value.endsWith(".localhost") || value == "::1" ||
            value.startsWith("127.") || value.startsWith("::ffff:127.")
    }
}
