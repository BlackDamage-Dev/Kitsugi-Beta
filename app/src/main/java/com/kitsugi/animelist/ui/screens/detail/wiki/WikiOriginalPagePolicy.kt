package com.kitsugi.animelist.ui.screens.detail.wiki

import java.net.URI
import java.util.Locale

/** Main-frame navigation policy. Resource filtering is separate in WikiAdBlockRules,
 * which protects the wiki content/CDN origins required by themes and media. */
internal object WikiOriginalPagePolicy {
    enum class Destination { WIKI, EXTERNAL, BLOCKED }

    fun destination(url: String): Destination {
        val uri = runCatching { URI(url) }.getOrNull() ?: return Destination.BLOCKED
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.rawUserInfo != null ||
            (uri.port != -1 && uri.port != 443)
        ) return Destination.BLOCKED
        val host = uri.host?.lowercase(Locale.ROOT) ?: return Destination.BLOCKED
        if (host.isBlank()) return Destination.BLOCKED
        return if (listOf("fandom.com", "wikia.com", "wikipedia.org").any {
                host == it || host.endsWith(".$it")
            }) Destination.WIKI else Destination.EXTERNAL
    }

    /** Keep the installed engine's version; never impersonate an old, hard-coded Chrome. */
    fun desktopUserAgent(systemUserAgent: String): String = systemUserAgent
        .replace(Regex("\\([^)]*Android[^)]*\\)"), "(X11; Linux x86_64)")
        .replace("; wv", "")
        .replace(Regex("\\s+Version/\\S+"), "")
        .replace(" Mobile", "")
}
