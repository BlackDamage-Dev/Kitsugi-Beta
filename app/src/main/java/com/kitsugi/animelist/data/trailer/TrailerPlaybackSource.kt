package com.kitsugi.animelist.data.trailer

/**
 * Bir videonun oynatılabilir akış adresi.
 *
 * [videoUrl] birincil akıştır. YouTube imzalı adresleri kısa ömürlüdür ve HLS manifesti
 * imzalandıktan sonra bile 403 dönebiliyor; bu yüzden aynı çıkarımdan elde edilmiş
 * yedekler [alternateVideoUrls] içinde sırayla saklanır. Oynatıcı birincil akışı
 * açamazsa motoru değiştirmeden önce bu listedekileri dener — "video oynamıyor"
 * şikâyetinin en sık nedeni buydu.
 *
 * [requestHeaders] contains source-specific values (most importantly the YouTube client
 * user-agent which issued the signed URLs). [alternatives] keeps genuinely different
 * playback sources so a CDN/HTTP failure does not pointlessly retry the same broken URL
 * in another player engine.
 */
data class TrailerPlaybackSource(
    val videoUrl: String,
    val audioUrl: String? = null,
    val requestHeaders: Map<String, String> = emptyMap(),
    val alternatives: List<TrailerPlaybackSource> = emptyList(),
    val externalAudioTracks: List<com.kitsugi.animelist.core.player.ExternalAudioTrackInput> = emptyList(),
    val streamTypeHint: String? = null,
    /** Birincil akış açılamazsa denenecek yedek video adresleri (kalite sırasıyla). */
    val alternateVideoUrls: List<String> = emptyList(),
    /** [alternateVideoUrls] ile aynı indeksteki ses adresleri; `null` = akış sesli. */
    val alternateAudioUrls: List<String?> = emptyList()
) {
    /** Belirtilen indeks için (video, ses) çifti; 0 birincil akıştır. */
    fun sourceAt(index: Int): Pair<String, String?>? {
        if (index == 0) return videoUrl to audioUrl
        val video = alternateVideoUrls.getOrNull(index - 1) ?: return null
        val audio = alternateAudioUrls.getOrNull(index - 1)
        return video to audio
    }

    /** Denenebilecek toplam akış sayısı (birincil + yedekler). */
    val totalSources: Int
        get() = 1 + alternateVideoUrls.size
}

/**
 * Flattens the primary source and its alternatives into independent, cycle-free entries.
 * The returned entries intentionally have empty [TrailerPlaybackSource.alternatives], so
 * they can safely be used as Compose keys and persisted in small in-memory caches.
 */
fun TrailerPlaybackSource.playbackCandidates(): List<TrailerPlaybackSource> {
    val flattened = buildList {
        add(this@playbackCandidates)
        addAll(alternatives)
        alternateVideoUrls.forEachIndexed { i, v ->
            add(TrailerPlaybackSource(videoUrl = v, audioUrl = alternateAudioUrls.getOrNull(i), requestHeaders = requestHeaders))
        }
    }
    return flattened
        .map { it.copy(alternatives = emptyList(), alternateVideoUrls = emptyList(), alternateAudioUrls = emptyList()) }
        .distinctBy { it.videoUrl to it.audioUrl }
        .filter { it.videoUrl.isNotBlank() }
}

/** Earliest signed expiry across every primary/alternative video and audio URL. */
internal fun TrailerPlaybackSource.earliestYouTubeExpiryEpochSeconds(): Long? =
    playbackCandidates()
        .flatMap { candidate -> listOfNotNull(candidate.videoUrl, candidate.audioUrl) }
        .mapNotNull(YouTubeRequestPolicy::expiryEpochSeconds)
        .minOrNull()

/** Nests an ordered candidate list once, without recursively duplicating alternatives. */
internal fun List<TrailerPlaybackSource>.asPrimaryWithAlternatives(): TrailerPlaybackSource? {
    val candidates = flatMap { it.playbackCandidates() }
    val primary = candidates.firstOrNull() ?: return null
    return primary.copy(alternatives = candidates.drop(1))
}
