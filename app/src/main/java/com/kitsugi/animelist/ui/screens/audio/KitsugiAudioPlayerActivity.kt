package com.kitsugi.animelist.ui.screens.audio

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.kitsugi.animelist.DeviceFormFactor
import com.kitsugi.animelist.DeviceProfile
import com.kitsugi.animelist.core.player.audio.player.KitsugiAudioPlayerManager
import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.ui.theme.KitsugiAnimeListTheme
import com.kitsugi.animelist.ui.theme.KitsugiColors

/**
 * Bildirim çubuğundan veya harici dosya yöneticilerinden (ses MIME türleri) açılan
 * tam ekran Kitsugi Ses Oynatıcısı ekranı.
 */
class KitsugiAudioPlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        consumeExternalIntentIfPresent(intent)

        val isTv = DeviceProfile.detect(this) == DeviceFormFactor.TV
        setContent {
            val settingsStore = remember { SettingsDataStore(applicationContext) }
            val appSettings by settingsStore.settingsFlow.collectAsState(initial = AppSettings())
            val darkTheme = when (appSettings.themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }
            val manager = remember { KitsugiAudioPlayerManager.getInstance(applicationContext) }
            val state by manager.state.collectAsState()
            val artwork by manager.artworkBitmap.collectAsState()

            LaunchedEffect(state.isActive) {
                // Oynatıcı tamamen kapatıldıysa ve bekleyen URI yoksa aktiviteden çık.
                if (!state.isActive && intent?.data == null && intent?.getStringExtra(EXTRA_AUDIO_URI) == null) {
                    finish()
                }
            }

            KitsugiAnimeListTheme(
                darkTheme = darkTheme,
                amoledBlack = appSettings.amoledBlack,
                selectedThemeId = appSettings.selectedThemeId,
                customAccentColor = appSettings.customAccentColor,
                customAccentColor2 = appSettings.customAccentColor2,
                customAccentGradientAngle = appSettings.customAccentGradientAngle,
                isTv = isTv
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(KitsugiColors.Background)
                        .systemBarsPadding(),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.isActive) {
                        KitsugiAudioPlayerFullContent(
                            state = state,
                            artworkBitmap = artwork,
                            manager = manager,
                            onCollapse = { finish() },
                            onClosePlayer = {
                                manager.stopAndClear()
                                finish()
                            },
                            isStandaloneScreen = true
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeExternalIntentIfPresent(intent)
    }

    private fun consumeExternalIntentIfPresent(incoming: Intent?) {
        if (incoming == null) return
        val uri = incoming.getStringExtra(EXTRA_AUDIO_URI)?.let(Uri::parse) ?: incoming.data ?: return
        val titleHint = incoming.getStringExtra(EXTRA_AUDIO_TITLE)
        incoming.removeExtra(EXTRA_AUDIO_URI)
        incoming.data = null
        KitsugiAudioPlayerManager.getInstance(applicationContext)
            .playExternalUri(uri, titleHint = titleHint, openFullSheet = false)
    }

    companion object {
        const val EXTRA_AUDIO_URI = "extra_audio_uri"
        const val EXTRA_AUDIO_TITLE = "extra_audio_title"

        fun startWithUri(context: Context, uri: Uri, titleHint: String? = null) {
            val intent = Intent(context, KitsugiAudioPlayerActivity::class.java).apply {
                data = uri
                putExtra(EXTRA_AUDIO_URI, uri.toString())
                if (!titleHint.isNullOrBlank()) putExtra(EXTRA_AUDIO_TITLE, titleHint)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
        }
    }
}
