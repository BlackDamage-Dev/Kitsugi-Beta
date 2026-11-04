package com.kitsugi.animelist.data.remote

/** Small third-party indexes are valid; only known Keiyoushi decoys get a fallback. */
internal fun isMangaRepoDecoy(body: String, repoUrl: String): Boolean {
    val markers = listOf("Outdated App", "Update to Mihon", "Update to Tachiyomi")
    if (markers.any { body.contains(it, ignoreCase = true) }) return true
    val official = repoUrl.startsWith("https://raw.githubusercontent.com/keiyoushi/extensions/", true) ||
        repoUrl.startsWith("https://raw.github.com/keiyoushi/extensions/", true)
    return official && body.length < 5_000
}
