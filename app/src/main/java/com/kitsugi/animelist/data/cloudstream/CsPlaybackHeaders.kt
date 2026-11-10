package com.kitsugi.animelist.data.cloudstream

import java.util.Locale

/** Preserve request context across the provider -> embed extractor boundary. */
internal object CsPlaybackHeaders {
    fun merge(defaults: Map<String, String>, overrides: Map<String, String>): MutableMap<String, String> {
        val result = linkedMapOf<String, String>()
        for ((key, value) in defaults.entries + overrides.entries) {
            if (key.isBlank() || value.isBlank()) continue
            result.keys.firstOrNull { it.equals(key, ignoreCase = true) }?.let { result.remove(it) }
            result[key] = value
        }
        return result
    }

    fun withReferer(headers: Map<String, String>, referer: String): MutableMap<String, String> {
        val result = merge(emptyMap(), headers)
        if (referer.isNotBlank() && result.keys.none { it.equals("referer", ignoreCase = true) }) {
            result["Referer"] = referer
        }
        return result
    }

    fun value(headers: Map<String, String>, name: String): String? =
        headers.entries.lastOrNull { it.key.equals(name, ignoreCase = true) && it.value.isNotBlank() }?.value

    fun identity(headers: Map<String, String>?): Map<String, String> =
        headers.orEmpty().entries.associate { it.key.lowercase(Locale.ROOT) to it.value }

    // An auth error or server error on a GET is not evidence that the file is gone.
    fun isDefinitelyGone(status: Int): Boolean = status == 404 || status == 410
}
