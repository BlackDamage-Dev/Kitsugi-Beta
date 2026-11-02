package com.kitsugi.animelist.data.cloudstream

import org.jsoup.Jsoup
import java.net.URI
import java.net.URLEncoder

/** Parse only explicit Google Drive GET confirmation forms/links; never guess tokens. */
internal object CsDriveConfirmation {
    fun resolve(html: String, pageUrl: String): String? {
        if (html.isBlank()) return null
        val doc = Jsoup.parse(html, pageUrl)
        fun trusted(url: String): Boolean = CsMediaResponsePolicy.isDriveDownloadUrl(url) &&
            runCatching { URI(url).scheme.equals("https", ignoreCase = true) }.getOrDefault(false)
        for (form in doc.select("form[action]")) {
            if (form.attr("method").equals("post", true)) continue
            val action = form.absUrl("action").substringBefore('#')
            if (!trusted(action)) continue
            val fields = form.select("input[name]").filter { it.hasAttr("value") }
            if (fields.isEmpty()) continue
            val query = fields.joinToString("&") {
                URLEncoder.encode(it.attr("name"), "UTF-8") + "=" + URLEncoder.encode(it.attr("value"), "UTF-8")
            }
            return action + (if (action.contains('?')) "&" else "?") + query
        }
        for (anchor in doc.select("a[href]")) {
            val target = anchor.absUrl("href")
            if (trusted(target) && target.contains("confirm=")) return target
        }
        return null
    }
}
