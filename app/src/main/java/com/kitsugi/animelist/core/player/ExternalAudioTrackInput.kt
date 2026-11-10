package com.kitsugi.animelist.core.player

/**
 * External audio rendition advertised by a source provider. Headers are kept per URL:
 * a CDN can require different credentials/referer values from the video request.
 */
data class ExternalAudioTrackInput(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    /** Provider-supplied label, when one exists. */
    val name: String? = null,
    /** Provider-supplied BCP-47/ISO language, when one exists. */
    val lang: String? = null
) : java.io.Serializable
