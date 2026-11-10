package com.kitsugi.animelist.core.player.audio

import java.net.URI
import java.util.Locale

/** URL resolution and forwarding policy shared by playlist, key and byte-range requests. */
internal object AudioExportAddress {
    fun resolve(base: String, relative: String): String {
        // Java URI.resolve("") AND URI.resolve("?newQuery") drop the last path
        // component. DASH mediaRange and HLS query-only segment URIs refer to the
        // same base FILE, not its parent directory.
        val uri = relative.trim().let { reference ->
            when {
                reference.isEmpty() -> URI(base)
                reference.startsWith('?') -> URI(base.substringBefore('#').substringBefore('?') + reference)
                else -> URI(base).resolve(reference)
            }
        }
        require(uri.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") && !uri.host.isNullOrBlank() &&
            uri.rawUserInfo == null && uri.fragment == null) { "Medya adresi geçersiz" }
        // A remote playlist may not redirect into a device's loopback service.
        if (URI(base).host?.lowercase(Locale.ROOT) !in setOf("localhost", "127.0.0.1", "::1")) {
            require(uri.host?.lowercase(Locale.ROOT) !in setOf("localhost", "127.0.0.1", "::1")) {
                "Uzak medya kaynağı yerel adrese yönlendirdi"
            }
        }
        return uri.toString()
    }

    fun sameOrigin(a: String, b: String): Boolean = runCatching {
        fun port(uri: URI) = if (uri.port >= 0) uri.port else if (uri.scheme.equals("https", true)) 443 else 80
        val first = URI(a); val second = URI(b)
        first.host != null && first.scheme.equals(second.scheme, true) &&
            first.host.equals(second.host, true) && port(first) == port(second)
    }.getOrDefault(false)

    fun headersFor(root: String, target: String, headers: Map<String, String>): Map<String, String> =
        headers.filterKeys { key ->
            !key.equals("Range", true) && !key.equals("Host", true) &&
                !key.equals("Accept-Encoding", true) && !key.equals("Content-Length", true) &&
                !key.equals("Transfer-Encoding", true) &&
                (sameOrigin(root, target) || key.equals("User-Agent", true) ||
                    key.equals("Accept", true) || key.equals("Accept-Language", true) ||
                    key.equals("Referer", true) || key.equals("Origin", true))
        }
}
