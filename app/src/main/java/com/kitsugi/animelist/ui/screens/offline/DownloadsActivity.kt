package com.kitsugi.animelist.ui.screens.offline

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import com.kitsugi.animelist.DeviceFormFactor
import com.kitsugi.animelist.DeviceProfile
import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.ui.theme.KitsugiAnimeListTheme

/**
 * Standalone activity for the Downloads screen.
 * Launched from KitsugiStreamActivity/Screen or audio export notifications so the user
 * can view progress and play downloaded media directly.
 */
class DownloadsActivity : ComponentActivity() {

    private val requestedTabState = mutableIntStateOf(TAB_VIDEOS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        requestedTabState.intValue = intent?.getIntExtra(EXTRA_INITIAL_TAB, TAB_VIDEOS) ?: TAB_VIDEOS

        val isTv = DeviceProfile.detect(this) == DeviceFormFactor.TV
        setContent {
            val settingsStore = remember { SettingsDataStore(applicationContext) }
            val appSettings by settingsStore.settingsFlow.collectAsState(initial = AppSettings())
            val darkTheme = when (appSettings.themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }
            val initialTab by requestedTabState
            KitsugiAnimeListTheme(
                darkTheme = darkTheme,
                amoledBlack = appSettings.amoledBlack,
                selectedThemeId = appSettings.selectedThemeId,
                customAccentColor = appSettings.customAccentColor,
                customAccentColor2 = appSettings.customAccentColor2,
                customAccentGradientAngle = appSettings.customAccentGradientAngle,
                isTv = isTv
            ) {
                DownloadsScreen(
                    onBack = { finish() },
                    initialTab = initialTab
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra(EXTRA_INITIAL_TAB)) {
            requestedTabState.intValue = intent.getIntExtra(EXTRA_INITIAL_TAB, TAB_VIDEOS)
        }
    }

    companion object {
        const val EXTRA_INITIAL_TAB = "extra_initial_tab"
        const val TAB_VIDEOS = 0
        const val TAB_AUDIOS = 1
        const val TAB_SUBTITLES = 2
        const val TAB_IMAGES = 3
    }
}
