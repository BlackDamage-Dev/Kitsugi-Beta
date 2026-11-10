package com.kitsugi.animelist.core.player

/** Match AniSub's public API fields, not release names (which often contain 1080p/FPS). */
object AnisubMatching {
    // Exact titles alone cannot distinguish remakes or season records. AniSub's
    // AniList media ID must agree; otherwise omit the release rather than guess.
    @Suppress("UNUSED_PARAMETER")
    fun matchesMedia(expectedId: Int?, resultId: Int?, query: String, titles: List<String?>): Boolean =
        expectedId != null && expectedId > 0 && resultId == expectedId

    /** null = no reliable episode information, true/false = explicit matching metadata. */
    fun episodeMatches(contentType: String?, description: String?, episode: Int?): Boolean? {
        if (episode == null) return null
        val type = contentType.orEmpty()
        val explicit = Regex("(?i)b[oö]l[üu]m\\s*[:#-]?\\s*(\\d+)").find(type)
        if (explicit != null) return explicit.groupValues[1].toIntOrNull() == episode
        val detail = description.orEmpty().trim()
        if (detail.isEmpty()) return null
        val range = Regex("^\\s*(\\d+)\\s*[-–]\\s*(\\d+)\\s*$").matchEntire(detail)
        if (range != null) {
            val start = range.groupValues[1].toIntOrNull() ?: return null
            val end = range.groupValues[2].toIntOrNull() ?: return null
            return episode in start..end
        }
        if (detail.matches(Regex("\\d+"))) return detail.toIntOrNull() == episode
        return null
    }

    /** Only explicit episode markers: codec, resolution, title and checksum digits
     * must not be accepted as episode numbers. Unknown filenames are not matches. */
    fun matchesFileEpisode(fileName: String, episode: Int): Boolean = fileEpisode(fileName) == episode

    /** Multiple matching files may be different seasons, languages or cuts. Do not guess. */
    fun uniqueEpisodeFile(fileNames: List<String>, episode: Int): String? =
        fileNames.filter { matchesFileEpisode(it, episode) }.singleOrNull()

    fun fileEpisode(fileName: String): Int? {
        val name = fileName.substringBeforeLast('.').lowercase()
        val seasonEpisode = Regex("""(?i)\bs(?:eason)?\s*(\d+)\s*e(?:pisode)?\s*(\d+)\b""").find(name)
        if (seasonEpisode != null) return seasonEpisode.groupValues[2].toIntOrNull()
        val xEpisode = Regex("""(?i)\b(\d+)\s*x\s*(\d+)\b""").find(name)
        if (xEpisode != null) return xEpisode.groupValues[2].toIntOrNull()
        val prefix = Regex("""(?i)\b(?:ep(?:isode)?|b[oö]l[üu]m)\s*[:#-]?\s*(\d+)\b""").find(name)
        if (prefix != null) return prefix.groupValues[1].toIntOrNull()
        val dash = Regex("""\s-\s*(\d{1,4})(?:v\d+)?(?=\s|\.|\[|\(|$)""").find(name)
        if (dash != null) return dash.groupValues[1].toIntOrNull()
        // Common anime pattern: "[Group] Title 12 [1080p BD][AV1]".
        val beforeTag = Regex("""\s(\d{1,4})(?:v\d+)?(?=\s*(?:\[|\(|$))""").findAll(name).lastOrNull()
        if (beforeTag != null) return beforeTag.groupValues[1].toIntOrNull()
        val bracket = Regex("""\[(\d{1,4})]""").findAll(name).lastOrNull()
        return bracket?.groupValues?.get(1)?.toIntOrNull()
    }

    fun isPackage(contentType: String?) = contentType.orEmpty().contains("paket", ignoreCase = true) ||
        contentType.orEmpty().contains("batch", ignoreCase = true)
}
