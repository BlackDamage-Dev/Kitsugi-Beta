package com.kitsugi.animelist.ui.screens.detail

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.kitsugi.animelist.R
import com.kitsugi.animelist.core.download.DetailVideoDownloadNotifier
import com.kitsugi.animelist.core.download.DetailVideoDownloader
import com.kitsugi.animelist.utils.KitsugiStrings
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Tek bir satırın indirme durumu. */
internal sealed interface VideoDownloadState {
    data object Idle : VideoDownloadState
    data class Running(val progress: Float?) : VideoDownloadState
    data class Done(val fileName: String) : VideoDownloadState
    data class Failed(val message: String) : VideoDownloadState
}

/**
 * Videolar sekmesindeki satır başına indirme durumunu yönetir.
 *
 * Durum satır anahtarına (`DetailVideoItem.key`) göre tutulur; böylece fragman
 * indirilirken aynı anda bir OP ya da ED de indirilebilir ve her satır kendi
 * yüzdesini gösterir. İndirme sürerken satıra tekrar basmak işi İPTAL eder.
 */
@Composable
internal fun rememberVideoDownloadController(mediaTitle: String = ""): VideoDownloadController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val states = remember { mutableStateMapOf<String, VideoDownloadState>() }
    val jobs = remember { mutableMapOf<String, Job>() }
    // Ekrandan çıkılırken süren indirmeler iptal edilir; aksi hâlde arka planda kalan
    // coroutine'ler ölü Composable durumuna yazmaya çalışıyordu.
    DisposableEffect(Unit) {
        onDispose {
            jobs.values.filter { it.isActive }.forEach { it.cancel() }
            jobs.clear()
        }
    }

    return remember(context, mediaTitle) {
        VideoDownloadController(
            stateProvider = { key -> states[key] ?: VideoDownloadState.Idle },
            toggle = { key, title, url ->
                val running = jobs[key]
                if (running != null && running.isActive) {
                    // Devam eden indirme: satıra tekrar dokunmak iptal eder.
                    running.cancel()
                    jobs.remove(key)
                    DetailVideoDownloadNotifier.cancel(context, notificationId(key))
                    states[key] = VideoDownloadState.Idle
                } else if (url.isNullOrBlank()) {
                    Toast.makeText(
                        context,
                        KitsugiStrings.get(R.string.detail_video_download_error_no_stream),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    val notifId = notificationId(key)
                    states[key] = VideoDownloadState.Running(null)
                    DetailVideoDownloadNotifier.progress(context, notifId, title, null)
                    jobs[key] = scope.launch {
                        val result = DetailVideoDownloader.download(
                            context = context,
                            videoUrl = url,
                            title = title,
                            mediaTitle = mediaTitle,
                            onProgress = { fraction, _ ->
                                val current = states[key]
                                states[key] = VideoDownloadState.Running(
                                    fraction ?: (current as? VideoDownloadState.Running)?.progress
                                )
                                DetailVideoDownloadNotifier.progress(context, notifId, title, fraction)
                            },
                            onStatus = { }
                        )
                        if (result.success && result.fileLabel != null) {
                            states[key] = VideoDownloadState.Done(result.fileLabel)
                            DetailVideoDownloadNotifier.completed(context, notifId, title, result.fileLabel)
                            Toast.makeText(
                                context,
                                context.getString(R.string.detail_video_download_done_toast, result.fileLabel),
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            val message = result.errorMessage
                                ?: KitsugiStrings.get(R.string.detail_video_download_error_generic)
                            states[key] = VideoDownloadState.Failed(message)
                            DetailVideoDownloadNotifier.failed(context, notifId, title, message)
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                        jobs.remove(key)
                    }
                }
            },
            cancelAll = {
                jobs.forEach { (key, job) ->
                    if (job.isActive) {
                        job.cancel()
                        DetailVideoDownloadNotifier.cancel(context, notificationId(key))
                    }
                    states[key] = VideoDownloadState.Idle
                }
                jobs.clear()
            }
        )
    }
}

/**
 * Satır durumunu okuyan ince denetleyici. `@Composable` olmayan bir sınıf tutuyoruz ki
 * satır composable'ı yalnızca kendi anahtarını okusun (tüm liste her ilerleme
 * güncellemesinde yeniden bestelenmesin).
 */
internal class VideoDownloadController(
    private val stateProvider: (String) -> VideoDownloadState,
    private val toggle: (key: String, title: String, url: String?) -> Unit,
    val cancelAll: () -> Unit
) {
    fun stateOf(key: String): VideoDownloadState = stateProvider(key)

    /** İndirmeyi başlatır; hâlihazırda indiriliyorsa iptal eder. */
    fun onDownloadClick(key: String, title: String, url: String?) = toggle(key, title, url)
}

/** Bildirim kimliği: satır anahtarından türetilir, aynı indirme aynı bildirimi günceller. */
private fun notificationId(key: String): Int =
    DetailVideoDownloader.NOTIFICATION_ID_BASE + (key.hashCode() and 0x0FFFFFFF) % 900
