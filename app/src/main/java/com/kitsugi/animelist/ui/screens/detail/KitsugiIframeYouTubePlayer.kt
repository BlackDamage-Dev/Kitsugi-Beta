package com.kitsugi.animelist.ui.screens.detail

import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.YouTubePlayerCallback
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils.loadOrCueVideo
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/**
 * Küçük oynatıcının **son çare** YouTube yolu.
 *
 * NEDEN VAR?
 * YouTube fragmanları uygulamada kendi çıkardığımız imzalı akışlarla (Media3/MPV)
 * oynatılıyor. İmza çıkarımı YouTube tarafında değişen istemci / `n` parametresi
 * kuralları yüzünden zaman zaman boş dönüyor ve o an kullanıcı "bu video çalışmıyor"
 * görüyordu. Bu bileşen, YouTube'un **kendi web oynatıcısını** (IFrame Player API) bir
 * WebView içinde çalıştırır: imza çıkarımı, kodek seçimi, HLS/adaptive ayrımı gibi tüm
 * kırılgan katmanlar devre dışı kalır ve video YouTube'un kendi altyapısıyla oynar.
 *
 * Ödünleşim: kontroller YouTube'un kendi arayüzüdür ve reklam gösterebilir. Bu yüzden
 * yalnızca **yedek** yol olarak, uygulamanın kendi hattı iki motorda da başarısız
 * olduğunda kullanılır.
 */
@Composable
internal fun KitsugiIframeYouTubePlayer(
    videoId: String,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    onUnplayable: () -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    var isReady by remember(videoId) { mutableStateOf(false) }
    var hasError by remember(videoId) { mutableStateOf(false) }
    var playerView by remember { mutableStateOf<YouTubePlayerView?>(null) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (!hasError) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    YouTubePlayerView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        enableAutomaticInitialization = false

                        val listener = object : AbstractYouTubePlayerListener() {
                            override fun onReady(youTubePlayer: YouTubePlayer) {
                                isReady = true
                                // loadOrCueVideo: WebView henüz ekrana bağlı değilken
                                // (kart açılırken / kaydırma sırasında) `loadVideo` sessizce
                                // yutuluyor ve oynatıcı 00:00'da kalıyordu. Bu yardımcı
                                // oynatıcı hazır değilse videoyu sıraya alır.
                                runCatching {
                                    if (autoPlay) {
                                        youTubePlayer.loadOrCueVideo(
                                            lifecycleOwner.lifecycle,
                                            videoId,
                                            0f
                                        )
                                    } else {
                                        youTubePlayer.cueVideo(videoId, 0f)
                                    }
                                }.onFailure {
                                    Log.w(TAG, "loadOrCueVideo hatası: ${it.message}")
                                }
                            }

                            override fun onStateChange(
                                youTubePlayer: YouTubePlayer,
                                state: PlayerConstants.PlayerState
                            ) {
                                if (state == PlayerConstants.PlayerState.PLAYING) isReady = true
                            }

                            override fun onError(
                                youTubePlayer: YouTubePlayer,
                                error: PlayerConstants.PlayerError
                            ) {
                                Log.w(TAG, "IFrame hatası: $error (video=$videoId)")
                                when (error) {
                                    // Gömülü oynatma yapımcı tarafından kapatılmışsa hiçbir
                                    // uygulama içi yol çalışmaz; kullanıcıya tarayıcı
                                    // seçeneği sunulmalı.
                                    PlayerConstants.PlayerError.VIDEO_NOT_FOUND,
                                    PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER,
                                    PlayerConstants.PlayerError.INVALID_PARAMETER_IN_REQUEST -> {
                                        hasError = true
                                        onUnplayable()
                                    }

                                    else -> Unit
                                }
                            }
                        }

                        val options = IFramePlayerOptions.Builder()
                            .controls(1)
                            .autoplay(if (autoPlay) 1 else 0)
                            .fullscreen(0)
                            .rel(0)
                            .ivLoadPolicy(3)
                            .build()
                        initialize(listener, options)

                        // Ekran kapanınca / uygulama arka plana geçince WebView oynatıcısı
                        // durdurulur; geri dönünce kullanıcı kendi başlatır.
                        lifecycleOwner.lifecycle.addObserver(this)
                        playerView = this
                    }
                },
                update = { view ->
                    // Fabrika yalnızca bir kez çalışır; video değişirse aynı oynatıcıya
                    // yeni videoyu yükle.
                    if (view.tag != videoId) {
                        view.tag = videoId
                        if (isReady) {
                            // `YouTubePlayerCallback` Kotlin'de `interface` olarak tanımlı
                            // (`fun interface` değil); lambda SAM dönüşümü derlenmez, bu
                            // yüzden açık nesne ifadesi kullanılıyor.
                            view.getYouTubePlayerWhenReady(
                                object : YouTubePlayerCallback {
                                    override fun onYouTubePlayer(youTubePlayer: YouTubePlayer) {
                                        runCatching {
                                            youTubePlayer.loadOrCueVideo(
                                                lifecycleOwner.lifecycle,
                                                videoId,
                                                0f
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            )
        }

        if (!isReady && !hasError) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                KitsugiPlasmaLoader(size = 38.dp)
                Text(
                    text = stringResource(R.string.loading),
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        if (hasError) {
            // Yapımcı gömülü oynatmayı kapatmış olabilir: o zaman ne yerel akış ne de
            // IFrame oynar. Tek dürüst çıkış tarayıcıdır; boş ekran bırakmıyoruz.
            // Hata metni yukarıda `inline_video_unsupported_body` ile gösterilir; burada
            // yalnızca eylem düğmesi eklenir.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                com.kitsugi.animelist.ui.components.KitsugiButton(
                    onClick = {
                        runCatching {
                            uriHandler.openUri("https://www.youtube.com/watch?v=$videoId")
                        }
                    }
                ) {
                    Text(
                        text = stringResource(R.string.inline_open_in_browser_eed20ab),
                        color = androidx.compose.ui.graphics.Color.Black
                    )
                }
            }
        }
    }

    // WebView'i mutlaka bırak: her satır açılışında yeni bir WebView oluşturuluyor ve
    // bırakılmayan WebView'lar süreç belleğinde birikiyordu.
    DisposableEffect(playerView, lifecycleOwner) {
        val view = playerView
        onDispose {
            if (view != null) {
                runCatching { lifecycleOwner.lifecycle.removeObserver(view) }
                runCatching { view.release() }
            }
        }
    }
}

private const val TAG = "IframeYouTubePlayer"
