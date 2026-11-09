package com.kitsugi.animelist.ui.screens.offline

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.kitsugi.animelist.core.player.audio.player.AudioFormatSupport
import com.kitsugi.animelist.ui.screens.audio.KitsugiAudioPlayerActivity
import com.kitsugi.animelist.ui.screens.fullscreen.KitsugiFullscreenPlayerActivity
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R

/**
 * T1.14 – DownloadedPlayerActivity
 * Dışarıdan (dosya yöneticisi vb.) gelen video oynatma intentlerini
 * yakalayıp KitsugiFullscreenPlayerActivity'ye yönlendirir.
 */
class DownloadedPlayerActivity : ComponentActivity() {
    companion object {
        const val TAG = "DownloadedPlayerActivity"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) {
            finish()
            return
        }
        val data = intent.data ?: intent.clipData?.getItemAt(0)?.uri
        if (data != null) {
            if (isUriSafe(data)) {
                Log.d(TAG, "Playing URI offline: $data")
                val filename = data.lastPathSegment ?: "Yerel Dosya"
                val resolvedMime = intent.type
                    ?: runCatching { contentResolver.getType(data) }.getOrNull()
                val pathHint = data.toString()
                val inAudioFolder = pathHint.contains("Kitsugi/Audio", ignoreCase = true) ||
                    pathHint.contains("Kitsugi%2FAudio", ignoreCase = true)
                val shouldOpenInAudioPlayer = AudioFormatSupport.isAudioMimeType(resolvedMime) ||
                    AudioFormatSupport.isAudioFileName(pathHint, allowWebmAsAudio = inAudioFolder)
                if (shouldOpenInAudioPlayer) {
                    KitsugiAudioPlayerActivity.startWithUri(
                        context = this,
                        uri = data,
                        titleHint = null
                    )
                } else {
                    val fileParams = if (data.scheme.equals("file", ignoreCase = true)) {
                        data.path?.let { filePath ->
                            com.kitsugi.animelist.core.player.OfflinePlaybackHelper.resolveFilePlaybackParams(
                                videoFile = java.io.File(filePath),
                                fallbackTitle = filename
                            )
                        }
                    } else {
                        null
                    }
                    KitsugiFullscreenPlayerActivity.startWithStreamUrls(
                        context = this,
                        videoUrl = fileParams?.videoUri ?: data.toString(),
                        audioUrl = fileParams?.audioUri,
                        title = fileParams?.title ?: filename,
                        headers = emptyMap(),
                        subtitles = fileParams?.subtitles ?: emptyList()
                    )
                }
            } else {
                Toast.makeText(this, KitsugiStrings.get(R.string.gecersiz_veya_guvenli_olmayan_dosya_adresi), Toast.LENGTH_LONG).show()
            }
        }
        finish()
    }

    private fun isUriSafe(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "file" && scheme != "content" && scheme != "http" && scheme != "https") {
            Log.w(TAG, "Geçersiz şema reddedildi: $scheme")
            return false
        }
        if (scheme == "file") {
            val path = uri.path ?: return false
            val canonicalPath = try {
                java.io.File(path).canonicalPath
            } catch (e: Exception) {
                path
            }
            val privatePaths = listOf(
                "/data/data/$packageName",
                "/data/user/0/$packageName",
                "/data/user_de/0/$packageName",
                filesDir.parentFile?.canonicalPath ?: "",
                cacheDir.parentFile?.canonicalPath ?: ""
            ).filter { it.isNotEmpty() }
            if (privatePaths.any { canonicalPath.startsWith(it) }) {
                Log.w(TAG, "Uygulamanın özel dizininden dosya oynatma girişimi engellendi: $canonicalPath")
                return false
            }
        }
        if (scheme == "content") {
            val authority = uri.authority
            if (authority != null && authority.contains(packageName, ignoreCase = true)) {
                Log.w(TAG, "Uygulamanın kendi provider'ından dosya oynatma girişimi engellendi: $authority")
                return false
            }
        }
        return true
    }
}
