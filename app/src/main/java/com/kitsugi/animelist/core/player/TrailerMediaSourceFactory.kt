package com.kitsugi.animelist.core.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import com.kitsugi.animelist.data.trailer.TrailerPlaybackSource
import com.kitsugi.animelist.data.trailer.YouTubeRequestPolicy
import com.kitsugi.animelist.data.trailer.YoutubeChunkedDataSourceFactory

/** Shared MediaSource construction for mobile/TV trailer and theme previews. */
@OptIn(UnstableApi::class)
object TrailerMediaSourceFactory {
    fun create(context: Context, source: TrailerPlaybackSource): MediaSource {
        val headers = YouTubeRequestPolicy.headersForPlaybackSource(source)
        val isHls = PlayerMediaSourceFactory.isHlsUrl(source.videoUrl)
        val isYouTube = YouTubeRequestPolicy.shouldUseYouTubeDataSource(
            videoUrl = source.videoUrl,
            audioUrl = source.audioUrl,
            isCloudstream = false
        )
        val dataSourceFactory = if (isYouTube) {
            YoutubeChunkedDataSourceFactory(
                chunkGoogleVideoStreams = YouTubeRequestPolicy.shouldUseChunkedGoogleVideoRanges(
                    videoUrl = source.videoUrl,
                    audioUrl = source.audioUrl,
                    isCloudstream = false,
                    isHls = isHls
                ),
                requestHeaders = headers
            )
        } else {
            val userAgent = headers.entries
                .firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
                ?.value
                ?: YouTubeRequestPolicy.STREAM_USER_AGENT
            val httpFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setDefaultRequestProperties(
                    headers.filterKeys { !it.equals("User-Agent", ignoreCase = true) }
                )
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(15_000)
                .setAllowCrossProtocolRedirects(true)
            DefaultDataSource.Factory(context, httpFactory)
        }

        val factory = DefaultMediaSourceFactory(dataSourceFactory)
        val videoItem = MediaItem.Builder()
            .setUri(Uri.parse(source.videoUrl))
            .apply { if (isHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        val videoSource = factory.createMediaSource(videoItem)
        val audioUrl = source.audioUrl
        return if (!audioUrl.isNullOrBlank() && audioUrl != source.videoUrl) {
            MergingMediaSource(
                videoSource,
                factory.createMediaSource(MediaItem.fromUri(Uri.parse(audioUrl)))
            )
        } else {
            videoSource
        }
    }
}
