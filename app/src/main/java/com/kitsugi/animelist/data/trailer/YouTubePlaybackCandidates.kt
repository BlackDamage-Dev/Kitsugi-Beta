package com.kitsugi.animelist.data.trailer

/** A direct progressive/adaptive stream returned by one YouTube player client. */
internal data class YouTubeStreamCandidate(
    val client: String,
    val priority: Int,
    val url: String,
    val score: Double,
    val hasN: Boolean,
    val itag: String,
    val height: Int,
    val fps: Int,
    val ext: String
)

/** A reachable HLS manifest and the client which signed it. */
internal data class YouTubeManifestCandidate(
    val client: String,
    val priority: Int,
    val manifestUrl: String,
    val width: Int,
    val height: Int,
    val bandwidth: Long
)

/**
 * Pure source-ordering policy for YouTube playback.
 *
 * Reliability comes before nominal quality: a working 720p/1080p stream is preferable to
 * a 4K URL carrying an unsolved `n` challenge. Audio and video are always paired from the
 * same player client; mixing signatures from different clients produces intermittent 403s.
 */
internal object YouTubePlaybackCandidates {
    private const val MAX_INLINE_HEIGHT = 1080
    private const val MAX_RETURNED_SOURCES = 4

    private val videoComparator =
        compareBy<YouTubeStreamCandidate>(
            { if (it.hasN) 1 else 0 },
            { if (it.height > MAX_INLINE_HEIGHT) 1 else 0 },
            { it.priority },
            { containerPreference(it.ext) },
            { -it.height.coerceAtMost(MAX_INLINE_HEIGHT) },
            { -it.fps.coerceAtMost(60) },
            { -it.score }
        )

    private val audioComparator =
        compareBy<YouTubeStreamCandidate>(
            { if (it.hasN) 1 else 0 },
            { containerPreference(it.ext) },
            { it.priority },
            { -it.score }
        )

    private val manifestComparator =
        compareBy<YouTubeManifestCandidate>(
            { it.priority },
            { -it.height.coerceAtMost(MAX_INLINE_HEIGHT) },
            { -it.bandwidth },
            { -it.width }
        )

    fun sortedVideos(items: List<YouTubeStreamCandidate>): List<YouTubeStreamCandidate> =
        items.sortedWith(videoComparator)

    fun sortedProgressive(items: List<YouTubeStreamCandidate>): List<YouTubeStreamCandidate> =
        items.sortedWith(videoComparator)

    fun sortedManifests(items: List<YouTubeManifestCandidate>): List<YouTubeManifestCandidate> =
        items.sortedWith(manifestComparator)

    /** Returns best same-client video/audio pairs, best pair first. */
    fun adaptivePairs(
        videos: List<YouTubeStreamCandidate>,
        audios: List<YouTubeStreamCandidate>
    ): List<Pair<YouTubeStreamCandidate, YouTubeStreamCandidate>> {
        val clients = videos.mapTo(linkedSetOf()) { it.client }
            .intersect(audios.mapTo(linkedSetOf()) { it.client })
        return clients.mapNotNull { client ->
            val video = videos.filter { it.client == client }.let(::sortedVideos).firstOrNull()
            val audio = audios.filter { it.client == client }.sortedWith(audioComparator).firstOrNull()
            if (video != null && audio != null) video to audio else null
        }.sortedWith { left, right -> videoComparator.compare(left.first, right.first) }
    }

    fun buildOrderedSources(
        manifests: List<YouTubeManifestCandidate>,
        adaptiveVideos: List<YouTubeStreamCandidate>,
        adaptiveAudios: List<YouTubeStreamCandidate>,
        progressive: List<YouTubeStreamCandidate>,
        headersForClient: (String) -> Map<String, String>
    ): List<TrailerPlaybackSource> {
        val sources = buildList {
            sortedManifests(manifests).forEach { manifest ->
                add(
                    TrailerPlaybackSource(
                        videoUrl = manifest.manifestUrl,
                        requestHeaders = headersForClient(manifest.client)
                    )
                )
            }
            adaptivePairs(adaptiveVideos, adaptiveAudios).forEach { (video, audio) ->
                add(
                    TrailerPlaybackSource(
                        videoUrl = video.url,
                        audioUrl = audio.url,
                        requestHeaders = headersForClient(video.client)
                    )
                )
            }
            sortedProgressive(progressive).forEach { video ->
                add(
                    TrailerPlaybackSource(
                        videoUrl = video.url,
                        requestHeaders = headersForClient(video.client)
                    )
                )
            }
        }
        return sources
            .distinctBy { it.videoUrl to it.audioUrl }
            .take(MAX_RETURNED_SOURCES)
    }

    private fun containerPreference(ext: String): Int = when (ext.lowercase()) {
        "mp4", "m4a" -> 0
        "webm" -> 1
        else -> 2
    }
}
