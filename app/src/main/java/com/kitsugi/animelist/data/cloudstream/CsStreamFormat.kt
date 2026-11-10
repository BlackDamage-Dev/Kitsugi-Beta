package com.kitsugi.animelist.data.cloudstream

/** Only explicit adaptive types prove that an extensionless CS URL is a manifest. */
internal object CsStreamFormat {
    fun isAdaptive(type: String?): Boolean = type == "M3U8" || type == "DASH"

    fun choose(type: String?, urlLooksHls: Boolean, urlLooksDash: Boolean): String? = when {
        type == "M3U8" -> "M3U8"
        type == "DASH" -> "DASH"
        urlLooksHls -> "M3U8"
        urlLooksDash -> "DASH"
        else -> null
    }
}
