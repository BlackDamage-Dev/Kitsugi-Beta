package com.kitsugi.animelist.data.cloudstream

import java.net.URI

/** Case-insensitive HTTP header handling at the plugin/extractor/player boundary. */
internal object CsStreamHeaders {
    fun value(headers: Map<String, String>, name: String): String? =
        headers.entries.lastOrNull { it.key.equals(name, ignoreCase = true) && it.value.isNotBlank() }?.value

    private fun put(headers: MutableMap<String, String>, name: String, value: String) {
        headers.keys.filter { it.equals(name, ignoreCase = true) }.toList().forEach(headers::remove)
        headers[name] = value
    }

    /** Child extractor headers win; differently cased spellings must not create duplicates. */
    fun merge(parent: Map<String, String>, child: Map<String, String>): MutableMap<String, String> {
        val result = linkedMapOf<String, String>()
        for ((name, value) in parent) {
            if (name.isNotBlank() && value.isNotBlank()) put(result, name, value)
        }
        for ((name, value) in child) {
            if (name.isNotBlank() && value.isNotBlank()) put(result, name, value)
        }
        return result
    }

    /**
     * Extractor's explicit Referer wins over the parent page, even if its headers only
     * contain some other field. An explicit Referer in the extractor map wins over both.
     */
    fun extracted(
        parent: Map<String, String>,
        child: Map<String, String>,
        extractorReferer: String?,
        fallbackReferer: String?,
        mediaUrl: String,
        userAgent: String,
        addOrigin: Boolean = true
    ): MutableMap<String, String> {
        val result = merge(parent, child)
        if (value(child, "Referer") == null && !extractorReferer.isNullOrBlank()) {
            put(result, "Referer", extractorReferer)
        }
        return complete(result, fallbackReferer, mediaUrl, userAgent, addOrigin)
    }

    fun complete(
        headers: Map<String, String>,
        fallbackReferer: String?,
        mediaUrl: String,
        userAgent: String,
        addOrigin: Boolean = true
    ): MutableMap<String, String> {
        val result = merge(emptyMap(), headers)
        if (value(result, "Referer") == null) {
            val referer = fallbackReferer?.takeIf { it.isNotBlank() }
                ?: origin(mediaUrl)?.let { "$it/" }
            if (referer != null) put(result, "Referer", referer)
        }
        if (addOrigin && value(result, "Origin") == null) {
            val origin = origin(value(result, "Referer")) ?: origin(mediaUrl)
            if (origin != null) put(result, "Origin", origin)
        }
        if (value(result, "User-Agent") == null && userAgent.isNotBlank()) {
            put(result, "User-Agent", userAgent)
        }
        return result
    }

    private fun origin(url: String?): String? {
        return try {
            val uri = URI(url?.trim().orEmpty())
            val scheme = uri.scheme?.lowercase(java.util.Locale.ROOT) ?: return null
            if (scheme != "http" && scheme != "https") return null
            val host = uri.host ?: return null
            val port = uri.port.takeIf {
                it > 0 && !(scheme == "http" && it == 80) && !(scheme == "https" && it == 443)
            }
            "$scheme://$host${port?.let { ":$it" }.orEmpty()}"
        } catch (_: Exception) {
            null
        }
    }
}
