package com.kitsugi.animelist.data.trailer

/** Innertube client contract; pure Kotlin so all configured clients can be checked together. */
internal data class YouTubeClient(
    val key: String,
    val id: String,
    val version: String,
    val userAgent: String,
    val context: Map<String, Any>,
    val priority: Int,
    /** Embedded clients inject thirdParty.embedUrl into the request context. */
    val embedUrl: String? = null,
    /** Whether to send x-goog-visitor-id with this client's request. */
    val needsVisitorData: Boolean = true
)

/**
 * Maintained Innertube client contracts, in reliability order.
 * Retired android_vr is deliberately absent: upstream removed it after broad HTTP 403s.
 */
internal object YouTubePlayerClients {
    val ALL = listOf(
        // TV supplies progressive + HLS and is the maintained first choice.
        YouTubeClient(
            key = "tv_samsung",
            id = "7",
            version = "5.20260707",
            userAgent = "Mozilla/5.0 (SMART-TV; Linux; Tizen 2.4.0) " +
                "AppleWebKit/538.1 (KHTML, like Gecko) Version/2.4.0 TV Safari/538.1",
            context = mapOf(
                "clientName" to "TVHTML5",
                "clientVersion" to "5.20260707",
                "deviceMake" to "Samsung",
                "deviceModel" to "UKS9800",
                "osName" to "Tizen",
                "osVersion" to "2.4.0",
                "hl" to "en",
                "gl" to "US"
            ),
            priority = 0
        ),
        // Direct adaptive streams without a JavaScript player / n-challenge solver.
        YouTubeClient(
            key = "visionos",
            id = "101",
            version = "1.02",
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) " +
                "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
            context = mapOf(
                "clientName" to "VISIONOS",
                "clientVersion" to "1.02",
                "deviceMake" to "Apple",
                "deviceModel" to "RealityDevice17,1",
                "osName" to "visionOS",
                "osVersion" to "26.5.23O471",
                "hl" to "en",
                "gl" to "US"
            ),
            priority = 1
        ),
        // IOS provides a different playability path for videos rejected by TV clients.
        YouTubeClient(
            key = "ios",
            id = "5",
            version = "21.26.4",
            userAgent = "com.google.ios.youtube/21.26.4 " +
                "(iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
            context = mapOf(
                "clientName" to "IOS",
                "clientVersion" to "21.26.4",
                "deviceMake" to "Apple",
                "deviceModel" to "iPhone16,2",
                "osName" to "iPhone",
                "osVersion" to "18.3.2.22D82",
                "platform" to "MOBILE",
                "hl" to "en",
                "gl" to "US"
            ),
            priority = 2
        ),
        // Embedded WEB is a final age/compatibility path, never the preferred source.
        YouTubeClient(
            key = "web_embedded",
            id = "56",
            version = "2.20260708.00.00",
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.5 Safari/605.1.15,gzip(gfe)",
            context = mapOf(
                "clientName" to "WEB_EMBEDDED_PLAYER",
                "clientVersion" to "2.20260708.00.00",
                "hl" to "en",
                "gl" to "US"
            ),
            priority = 3,
            embedUrl = "https://www.reddit.com/",
            needsVisitorData = true
        )
    )
}
