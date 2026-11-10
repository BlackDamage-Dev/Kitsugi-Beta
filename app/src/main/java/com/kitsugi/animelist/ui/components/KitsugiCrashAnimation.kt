package com.kitsugi.animelist.ui.components

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.theme.KitsugiColors

/**
 * Çökme ve kurtarma pencerelerinde gösterilen döngüsel animasyon bileşeni.
 * Doğrudan R.raw.crash_animation (mp4/gif) kaynağından okur, sessiz ve kesintisiz
 * döngüyle oynatır. TextureView kullandığı için Compose kırpma ve kenarlıklarını
 * kusursuz destekler.
 */
@Composable
fun KitsugiCrashAnimation(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp)
) {
    val context = LocalContext.current
    var hasError by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(shape)
            .background(KitsugiColors.SurfaceSoft)
            .border(1.dp, KitsugiColors.AccentRed.copy(alpha = 0.35f), shape),
        contentAlignment = Alignment.Center
    ) {
        if (!hasError) {
            var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }

            DisposableEffect(Unit) {
                onDispose {
                    try {
                        mediaPlayerRef?.stop()
                        mediaPlayerRef?.release()
                        mediaPlayerRef = null
                    } catch (_: Throwable) {}
                }
            }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    TextureView(ctx).apply {
                        isOpaque = false
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                                try {
                                    val surface = Surface(surfaceTexture)
                                    val mp = MediaPlayer().apply {
                                        setSurface(surface)
                                        val afd = ctx.resources.openRawResourceFd(R.raw.crash_animation)
                                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                                        afd.close()
                                        isLooping = true
                                        setVolume(0f, 0f)
                                        setOnPreparedListener { player ->
                                            try {
                                                player.start()
                                            } catch (_: Throwable) {}
                                        }
                                        setOnErrorListener { _, _, _ ->
                                            hasError = true
                                            true
                                        }
                                        prepareAsync()
                                    }
                                    mediaPlayerRef = mp
                                } catch (e: Throwable) {
                                    android.util.Log.w("KitsugiCrashAnimation", "MediaPlayer başlatılamadı: ${e.message}")
                                    hasError = true
                                }
                            }

                            override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {}

                            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                                try {
                                    mediaPlayerRef?.stop()
                                    mediaPlayerRef?.release()
                                    mediaPlayerRef = null
                                } catch (_: Throwable) {}
                                return true
                            }

                            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
                        }
                    }
                }
            )
        } else {
            // Beklenmeyen oynatma hatasında çökme ekranının kendisi bozulmasın
            Icon(
                Icons.BugReport,
                contentDescription = null,
                tint = KitsugiColors.AccentRed,
                modifier = Modifier.size(38.dp)
            )
        }
    }
}
