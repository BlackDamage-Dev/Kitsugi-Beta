package com.kitsugi.animelist.ui.screens.offline

import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import androidx.compose.foundation.verticalScroll
import com.kitsugi.animelist.ui.components.KitsugiChevronIcon
import com.kitsugi.animelist.ui.components.KitsugiTopAction
import com.kitsugi.animelist.ui.components.KitsugiTopActionButton
import com.kitsugi.animelist.ui.components.KitsugiTopActionTokens
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.LinearProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Text

import android.app.Activity
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.os.StrictMode
import android.widget.Toast
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.kitsugi.animelist.core.download.DownloadExportNaming
import com.kitsugi.animelist.core.player.OfflinePlaybackHelper
import com.kitsugi.animelist.core.player.audio.player.KitsugiAudioPlayerManager
import com.kitsugi.animelist.core.storage.DownloadStorage
import com.kitsugi.animelist.data.local.AnimeDownloadManager
import com.kitsugi.animelist.data.model.AnimeDownload
import com.kitsugi.animelist.ui.components.KitsugiSettingsSegmentedTabs
import com.kitsugi.animelist.ui.components.KitsugiSettingsTabOption
import com.kitsugi.animelist.ui.components.KitsugiAnimatedDeleteButton
import com.kitsugi.animelist.ui.screens.audio.KitsugiAudioPlayerHost
import com.kitsugi.animelist.ui.screens.fullscreen.KitsugiFullscreenPlayerActivity
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.utils.KitsugiImageDownloadHelper
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

private val WHITESPACE_REGEX = Regex("\\s+")
private val NON_ALPHANUM_REGEX = Regex("[^a-z0-9]")
private val EP_SUBTITLE_REGEX = Regex("""^(.*?)\s*-\s*Bölüm\s*(\d+)(?:\.([a-zA-Z0-9_ -]+))?$""", RegexOption.IGNORE_CASE)
private val DOT_SUBTITLE_REGEX = Regex("""^(.*?)\.([a-zA-Z0-9_ -]+)$""")
private val TRAILING_TIMESTAMP_REGEX = Regex("_\\d{13}$")
private val MULTI_UNDERSCORE_REGEX = Regex("_+")
private val COMPANION_SUB_EXTS = setOf("srt", "ass", "ssa", "vtt")

private fun resolveSafeAnimeId(animeId: String, animeTitle: String): String =
    animeId.ifBlank {
        animeTitle.lowercase(Locale.ROOT)
            .replace(NON_ALPHANUM_REGEX, "_")
            .trim('_')
            .take(30)
            .ifBlank { "media" }
    }

/**
 * Model representing any downloaded subtitle on disk (standalone or episode-bound).
 */
@Immutable
data class DownloadedSubtitleItem(
    val title: String,
    val language: String,
    val format: String,
    val fileSizeBytes: Long,
    val lastModified: Long,
    val file: File,
    val animeTitle: String? = null,
    val episode: Int? = null,
    val isStandalone: Boolean = false
)

/**
 * Model representing a downloaded gallery image on disk.
 * Images are saved by [KitsugiImageDownloadHelper] into Downloads/Kitsugi/Images.
 */
@Immutable
data class DownloadedImageItem(
    val file: File,
    val title: String,          // Parsed from filename (e.g. "Kitsugi_Naruto_...")
    val fileSizeBytes: Long,
    val lastModified: Long,
    val width: Int = 0,
    val height: Int = 0
)

/**
 * İndirilen resimleri içerik adına (anime / dizi / film) göre gruplar.
 * Başlık olarak en güncel kaydın adı, bölüm olarak o içeriğin resimleri gösterilir.
 */
@Immutable
data class DownloadedImageGroup(
    val displayName: String,
    val items: List<DownloadedImageItem>
)

/** Gruplama anahtarı: büyük/küçük harf ve boşluk farklarını dengeler. */
internal fun normalizeImageGroupKey(title: String): String =
    title.trim().lowercase(Locale.ROOT).replace(WHITESPACE_REGEX, " ")

/** Videolar sekmesindeki hızlı durum filtresi. */
private enum class DownloadsVideoFilter { ALL, ACTIVE, PAUSED, COMPLETED, ERROR }

private fun AnimeDownload.matchesFilter(filter: DownloadsVideoFilter): Boolean = when (filter) {
    DownloadsVideoFilter.ALL -> true
    DownloadsVideoFilter.ACTIVE ->
        status == AnimeDownload.Status.DOWNLOADING || status == AnimeDownload.Status.QUEUE
    DownloadsVideoFilter.PAUSED -> status == AnimeDownload.Status.PAUSED
    DownloadsVideoFilter.COMPLETED -> status == AnimeDownload.Status.COMPLETED
    DownloadsVideoFilter.ERROR -> status == AnimeDownload.Status.ERROR
}

/** Sıralama: devam edenler her zaman üstte, sonra en yeni eklenenler. */
private fun AnimeDownload.statusRank(): Int = when (status) {
    AnimeDownload.Status.DOWNLOADING, AnimeDownload.Status.QUEUE -> 0
    AnimeDownload.Status.PAUSED, AnimeDownload.Status.ERROR -> 1
    AnimeDownload.Status.COMPLETED -> 2
}

/** Bir indirme kaydının benzersiz anahtarı (seçim, liste anahtarı). */
internal fun videoKey(download: AnimeDownload): String = "${download.animeId}_${download.episode}"

/** Videolar sekmesindeki grup anahtarı: aynı içeriğin bölümleri tek başlıkta toplanır. */
private fun videoGroupKey(download: AnimeDownload): String =
    if (download.animeId.isNotBlank()) download.animeId
    else normalizeImageGroupKey(download.animeTitle)

/**
 * Videolar sekmesinin sıralama seçenekleri.
 *
 * [DownloadsVideoSort.QUEUE_ORDER] **kuyruk önceliğini** korur: devam eden iş en üstte,
 * ardından kuyrukta bekleyenler kullanıcının belirlediği sırayla gelir (listenin alttaki
 * dizilişi indirme servisinin seçim sırasıdır — bkz. `AnimeDownloadManager.getNextDownload`).
 */
private fun sortDownloadsForDisplay(
    list: List<AnimeDownload>,
    sort: DownloadsVideoSort
): List<AnimeDownload> = when (sort) {
    DownloadsVideoSort.QUEUE_ORDER -> list.withIndex()
        .sortedWith(
            compareBy<IndexedValue<AnimeDownload>>(
                { it.value.statusRank() },
                { it.index }
            )
        )
        .map { it.value }

    DownloadsVideoSort.TITLE -> list.sortedWith(
        compareBy<AnimeDownload> { it.animeTitle.lowercase(Locale.ROOT) }
            .thenBy { it.episode }
    )

    DownloadsVideoSort.EPISODE -> list.sortedWith(
        compareBy<AnimeDownload> { it.episode }
            .thenBy { it.animeTitle.lowercase(Locale.ROOT) }
    )

    DownloadsVideoSort.SIZE -> list.sortedByDescending { it.downloadedBytes }
    DownloadsVideoSort.RECENT -> list.sortedByDescending { it.createdAt }
}

/** Videolar sekmesinde ya grup başlığı ya da tek bir indirme satırı. */
internal sealed interface VideoListEntry {
    val key: String

    data class Header(
        override val key: String,
        val title: String,
        val items: List<AnimeDownload>
    ) : VideoListEntry

    data class Item(val download: AnimeDownload) : VideoListEntry {
        override val key: String get() = videoKey(download)
    }
}

/**
 * Videolar sekmesinin görünen girdilerini üretir.
 * Gruplu modda başlık + (daraltılmamışsa) satırlar; grup sırası sıralanmış listenin
 * ilk görünüm sırasını izler (LinkedHashMap), böylece kuyruk önceliği korunur.
 */
private fun buildVideoEntries(
    sortedList: List<AnimeDownload>,
    grouped: Boolean,
    collapsed: Set<String>
): List<VideoListEntry> {
    if (!grouped) return sortedList.map { VideoListEntry.Item(it) }

    val groups = LinkedHashMap<String, MutableList<AnimeDownload>>()
    sortedList.forEach { download ->
        groups.getOrPut(videoGroupKey(download)) { mutableListOf() }.add(download)
    }

    val entries = ArrayList<VideoListEntry>(sortedList.size + groups.size)
    groups.forEach { (key, items) ->
        entries.add(
            VideoListEntry.Header(
                key = key,
                title = items.firstOrNull()?.animeTitle.orEmpty(),
                items = items
            )
        )
        if (key !in collapsed) {
            items.forEach { entries.add(VideoListEntry.Item(it)) }
        }
    }
    return entries
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    initialTab: Int = 0
) {
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    val downloads by AnimeDownloadManager.downloads.collectAsState()
    val audioManager = remember(context) { KitsugiAudioPlayerManager.getInstance(context) }
    val audioPlayerState by audioManager.state.collectAsState()

    val pagerState = rememberPagerState(initialPage = initialTab.coerceIn(0, 3), pageCount = { 4 })
    val scope = rememberCoroutineScope()

    LaunchedEffect(initialTab) {
        val safePage = initialTab.coerceIn(0, 3)
        if (pagerState.currentPage != safePage) {
            pagerState.scrollToPage(safePage)
        }
    }

    // Keep all four scroll positions across tab changes and configuration changes.
    // The shared overview observes only the active page, never an off-screen page.
    val videoListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val audioListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val subtitleListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val imageGridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    var refreshAudiosTrigger by remember { mutableIntStateOf(0) }
    var refreshSubsTrigger by remember { mutableIntStateOf(0) }
    var refreshImagesTrigger by remember { mutableIntStateOf(0) }

    // ── Arama & filtre durumu (ekran döndürülünce korunur) ───────────────────
    var menuOpen by remember { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var videoFilter by rememberSaveable { mutableStateOf(DownloadsVideoFilter.ALL) }
    var subtitleSort by rememberSaveable { mutableStateOf(DownloadsSortOrder.NEWEST) }

    // Silme onayları: video tekli silme, "tamamlananları sil" ve seçilenleri silme.
    var pendingVideoDelete by remember { mutableStateOf<AnimeDownload?>(null) }
    var pendingDeleteCompleted by remember { mutableStateOf<List<AnimeDownload>>(emptyList()) }
    var deleteSelectedVideosPending by remember { mutableStateOf(false) }
    var deleteSelectedSubtitlesPending by remember { mutableStateOf(false) }

    // ── Videolar sekmesi: sıralama, gruplama ve çoklu seçim ──────────────────
    var videoSort by remember { mutableStateOf(DownloadsUiPrefs.videoSort(context)) }
    var videoGrouped by remember { mutableStateOf(DownloadsUiPrefs.videosGrouped(context)) }
    var collapsedVideoGroups by remember { mutableStateOf(setOf<String>()) }
    var videoSelectionMode by remember { mutableStateOf(false) }
    var selectedVideoKeys by remember { mutableStateOf(setOf<String>()) }

    // ── Sesler sekmesi: sıralama, gruplama, format filtresi ve çoklu seçim ───
    var audioSort by remember { mutableStateOf(DownloadsUiPrefs.audioSort(context)) }
    var audioGrouped by remember { mutableStateOf(DownloadsUiPrefs.audiosGrouped(context)) }
    var selectedAudioFormat by rememberSaveable { mutableStateOf<String?>(null) }
    var audioSelectionMode by remember { mutableStateOf(false) }

    // ── Altyazılar sekmesi: çoklu seçim ──────────────────────────────────────
    var subtitleSelectionMode by remember { mutableStateOf(false) }
    var selectedSubtitlePaths by remember { mutableStateOf(setOf<String>()) }

    // Geri tuşu: önce seçim modunu, sonra aramayı kapatır; ekrandan çıkmaz.
    BackHandler(enabled = searchOpen || videoSelectionMode || audioSelectionMode || subtitleSelectionMode) {
        when {
            videoSelectionMode -> {
                videoSelectionMode = false
                selectedVideoKeys = emptySet()
            }
            audioSelectionMode -> {
                audioSelectionMode = false
            }
            subtitleSelectionMode -> {
                subtitleSelectionMode = false
                selectedSubtitlePaths = emptySet()
            }
            else -> {
                searchQuery = ""
                searchOpen = false
            }
        }
    }

    // Galeriden yeni resim indirip bu ekrana dönüldüğünde listeler bayat kalmasın:
    // ekran her öne geldiğinde (ON_RESUME) ses, altyazı ve resim taramasını tazele.
    // İlk resume (ekran ilk açılış) atlanır — ilk yükleme zaten LaunchedEffect ile yapılıyor.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeSeenOnce by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (resumeSeenOnce) {
                    refreshAudiosTrigger++
                    refreshSubsTrigger++
                    refreshImagesTrigger++
                } else {
                    resumeSeenOnce = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // PERF: İndirme ilerlerken (downloadedBytes/progress değişirken) altyazı disk taramasını
    // her karede ana iş parçacığında tekrar çalıştırma! Yalnızca tamamlanan indirmelerin
    // listesi değiştiğinde veya manuel yenilemede Dispatchers.IO üzerinde tara.
    val completedDownloadsKey by remember {
        derivedStateOf {
            downloads.asSequence()
                .filter { it.status == AnimeDownload.Status.COMPLETED }
                .map { "${it.animeId}_${it.episode}_${it.localPath}" }
                .toList()
        }
    }

    var allAudios by remember { mutableStateOf(emptyList<DownloadedAudioItem>()) }
    var isLoadingAudios by remember { mutableStateOf(true) }
    val activeAudioExports = rememberActiveAudioExports(context) {
        refreshAudiosTrigger++
    }

    LaunchedEffect(completedDownloadsKey, refreshAudiosTrigger) {
        val currentDownloadsSnapshot = AnimeDownloadManager.downloads.value
        allAudios = scanDownloadedAudioItems(context, currentDownloadsSnapshot)
        isLoadingAudios = false
    }

    // FIX: Hızlı silme durumunda state güncelleme sorunu - produceState yerine mutableStateOf
    // kullanıyoruz. Silme sonrası anında listeden kaldırarak UI'ın hemen güncellenmesini sağlıyoruz.
    var allSubtitles by remember { mutableStateOf(emptyList<DownloadedSubtitleItem>()) }

    LaunchedEffect(completedDownloadsKey, refreshSubsTrigger) {
        val currentDownloadsSnapshot = AnimeDownloadManager.downloads.value
        allSubtitles = withContext(Dispatchers.IO) {
            loadAllDownloadedSubtitles(context, currentDownloadsSnapshot)
        }
    }

    var allImages by remember { mutableStateOf(emptyList<DownloadedImageItem>()) }

    LaunchedEffect(refreshImagesTrigger) {
        allImages = withContext(Dispatchers.IO) {
            loadAllDownloadedImages(context)
        }
    }

    val overviewScrollState = rememberDownloadsOverviewScrollState(
        activePage = pagerState.currentPage,
        videoListState = videoListState,
        subtitleListState = subtitleListState,
        imageGridState = imageGridState,
        audioListState = audioListState,
        // A new search/filter/sort is a new browsing context: show storage info
        // again instead of leaving the user with a hidden header over new data.
        contentRevision = listOf(
            downloads.size, allAudios.size, activeAudioExports.size, allSubtitles.size, allImages.size,
            searchOpen, searchQuery, videoFilter, subtitleSort, videoSort, videoGrouped,
            audioSort, audioGrouped, selectedAudioFormat
        )
    )

    // ── SİLME: sistem onayı köprüsü ───────────────────────────────────────────
    // Android 11+ Scoped Storage'da uygulamanın sahibi olmadığı MediaStore kayıtları
    // (ör. uygulama yeniden kurulduğunda eski UID ile oluşturulmuş görseller/altyazılar)
    // yalnızca MediaStore.createDeleteRequest onayıyla silinebilir. Onay sonrası liste
    // tazelenir; iptal edilirse kullanıcı bilgilendirilir.
    val systemConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            refreshSubsTrigger++
            refreshImagesTrigger++
        } else {
            Toast.makeText(
                context,
                KitsugiStrings.get(R.string.downloads_delete_cancelled),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val requestSystemDelete: (List<Uri>) -> Unit = { uris ->
        val sender = runCatching {
            KitsugiImageDownloadHelper.systemDeleteRequestSender(context, uris)
        }.getOrNull()
        val launched = sender != null && runCatching {
            systemConsentLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }.isSuccess
        if (!launched) {
            Toast.makeText(
                context,
                KitsugiStrings.get(R.string.downloads_delete_permission_needed),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ── SİLME: altyazı (tekli) ────────────────────────────────────────────────
    // Eski akış `File.delete()` çağırıyordu; MediaStore ile yazılan .srt/.ass dosyaları
    // Scoped Storage'da silinmediği için kart tazelenince geri geliyordu. Artık silme
    // DownloadStorage üzerinden (dosya + MediaStore kaydı + tüm kopyalar) yapılıyor.
    val deleteSubtitleItem: (DownloadedSubtitleItem) -> Unit = { item ->
        val removedPath = item.file.absolutePath
        allSubtitles = allSubtitles.filter { it.file.absolutePath != removedPath }
        scope.launch {
            val result = DownloadStorage.deleteSubtitle(context, item.file)
            when {
                result.deleted -> Toast.makeText(
                    context,
                    KitsugiStrings.get(R.string.altyazi_silindi_x, item.title),
                    Toast.LENGTH_SHORT
                ).show()

                result.consentUris.isNotEmpty() -> {
                    allSubtitles = (allSubtitles + item)
                        .distinctBy { it.file.absolutePath }
                        .sortedByDescending { it.lastModified }
                    Toast.makeText(
                        context,
                        KitsugiStrings.get(R.string.downloads_delete_confirm_prompt),
                        Toast.LENGTH_LONG
                    ).show()
                    requestSystemDelete(result.consentUris)
                }

                else -> {
                    allSubtitles = (allSubtitles + item)
                        .distinctBy { it.file.absolutePath }
                        .sortedByDescending { it.lastModified }
                    Toast.makeText(
                        context,
                        result.message
                            ?: KitsugiStrings.get(R.string.downloads_subtitle_delete_failed, item.title),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            refreshSubsTrigger++
        }
    }

    // ── SİLME: görseller (tekli kart + grup + çoklu seçim) ───────────────────
    // Kart üzerindeki silme önceden `file.delete()` idi → Scoped Storage'da sessizce
    // başarısız oluyordu ve hiçbir geri bildirim gösterilmiyordu. Artık her dosya
    // KitsugiImageDownloadHelper.deleteImageFile ile (index + MediaStore + kopyalar)
    // silinir, sonuç kullanıcıya bildirilir ve liste her durumda tazelenir.
    val deleteImageFiles: (List<File>) -> Unit = { files ->
        if (files.isNotEmpty()) {
            val paths = files.map { it.absolutePath }.toSet()
            // İyimser güncelleme: kartlar anında kaybolsun (silme başarısızsa liste tazelenir).
            allImages = allImages.filterNot { it.file.absolutePath in paths }
            scope.launch {
                var deleted = 0
                var consentUris = emptyList<Uri>()
                var failure: String? = null
                withContext(Dispatchers.IO) {
                    files.forEach { file ->
                        when (val outcome = KitsugiImageDownloadHelper.deleteImageFile(context, file)) {
                            is KitsugiImageDownloadHelper.GalleryDeleteOutcome.Success -> deleted++
                            is KitsugiImageDownloadHelper.GalleryDeleteOutcome.RequiresSystemConsent ->
                                consentUris = (consentUris + outcome.uris).distinct()
                            is KitsugiImageDownloadHelper.GalleryDeleteOutcome.Failed ->
                                failure = outcome.message
                            KitsugiImageDownloadHelper.GalleryDeleteOutcome.NothingToDelete -> Unit
                        }
                    }
                }
                when {
                    consentUris.isNotEmpty() -> {
                        Toast.makeText(
                            context,
                            KitsugiStrings.get(R.string.downloads_delete_confirm_prompt),
                            Toast.LENGTH_LONG
                        ).show()
                        requestSystemDelete(consentUris)
                    }
                    deleted > 0 -> Toast.makeText(
                        context,
                        KitsugiStrings.get(R.string.downloads_images_deleted, deleted),
                        Toast.LENGTH_SHORT
                    ).show()
                    else -> Toast.makeText(
                        context,
                        failure ?: KitsugiStrings.get(R.string.downloads_images_delete_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
                refreshImagesTrigger++
            }
        }
    }

    // ── Toplu duraklat / sürdür ──────────────────────────────────────────────
    val pauseAllActive: () -> Unit = {
        downloads
            .filter { it.status == AnimeDownload.Status.DOWNLOADING || it.status == AnimeDownload.Status.QUEUE }
            .forEach { AnimeDownloadManager.pauseDownload(it.animeId, it.episode) }
    }
    val resumeAllPaused: () -> Unit = {
        downloads
            .filter {
                it.status == AnimeDownload.Status.PAUSED ||
                    it.status == AnimeDownload.Status.ERROR ||
                    it.status == AnimeDownload.Status.QUEUE
            }
            .forEach { AnimeDownloadManager.resumeDownload(context, it.animeId, it.episode) }
    }

    // ── Silme akışları (onaylı) ─────────────────────────────────────────────
    val deleteVideoNow: (AnimeDownload) -> Unit = { download ->
        if (download.isClip) {
            scope.launch {
                val result = AnimeDownloadManager.deleteClipRecord(context, download)
                when {
                    result.deleted -> Toast.makeText(
                        context,
                        KitsugiStrings.get(
                            R.string.kitsugi_clip_deleted_toast,
                            download.clipTitle ?: download.animeTitle
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                    result.consentUris.isNotEmpty() -> {
                        Toast.makeText(
                            context,
                            KitsugiStrings.get(R.string.downloads_delete_confirm_prompt),
                            Toast.LENGTH_LONG
                        ).show()
                        requestSystemDelete(result.consentUris)
                    }
                    else -> Toast.makeText(
                        context,
                        result.message ?: KitsugiStrings.get(
                            R.string.kitsugi_clip_delete_failed,
                            download.clipTitle ?: download.animeTitle
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }
                refreshSubsTrigger++
            }
        } else {
            AnimeDownloadManager.deleteDownload(context, download.animeId, download.episode)
            refreshSubsTrigger++
        }
    }
    val deleteCompletedNow: (List<AnimeDownload>) -> Unit = { list ->
        list.forEach { download ->
            if (download.isClip) {
                scope.launch {
                    AnimeDownloadManager.deleteClipRecord(context, download)
                }
            } else {
                AnimeDownloadManager.deleteDownload(context, download.animeId, download.episode)
            }
        }
        refreshAudiosTrigger++
        refreshSubsTrigger++
        refreshImagesTrigger++
    }

    val allRefreshed: () -> Unit = {
        refreshAudiosTrigger++
        refreshSubsTrigger++
        refreshImagesTrigger++
    }

    // ── Altyazı: seçilenleri topluca sil ─────────────────────────────────────
    val deleteSubtitleBatch: (List<DownloadedSubtitleItem>) -> Unit = { items ->
        if (items.isNotEmpty()) {
            val paths = items.map { it.file.absolutePath }.toSet()
            // İyimser güncelleme: kartlar anında kaybolsun.
            allSubtitles = allSubtitles.filterNot { it.file.absolutePath in paths }
            scope.launch {
                var deleted = 0
                var consentUris = emptyList<Uri>()
                var failure: String? = null
                withContext(Dispatchers.IO) {
                    items.forEach { item ->
                        val result = runCatching {
                            DownloadStorage.deleteSubtitle(context, item.file)
                        }.getOrNull()
                        when {
                            result == null -> failure = KitsugiStrings.get(R.string.downloads_subtitle_delete_failed, item.title)
                            result.deleted -> deleted++
                            result.consentUris.isNotEmpty() ->
                                consentUris = (consentUris + result.consentUris).distinct()
                            else -> failure = result.message
                        }
                    }
                }
                when {
                    consentUris.isNotEmpty() -> {
                        Toast.makeText(
                            context,
                            KitsugiStrings.get(R.string.downloads_delete_confirm_prompt),
                            Toast.LENGTH_LONG
                        ).show()
                        requestSystemDelete(consentUris)
                    }
                    deleted > 0 -> Toast.makeText(
                        context,
                        KitsugiStrings.get(R.string.downloads_subtitles_deleted, deleted),
                        Toast.LENGTH_SHORT
                    ).show()
                    else -> Toast.makeText(
                        context,
                        failure ?: KitsugiStrings.get(R.string.downloads_subtitle_delete_failed, ""),
                        Toast.LENGTH_LONG
                    ).show()
                }
                refreshSubsTrigger++
            }
        }
    }

    // ── Oynatma: indirilen bölümü çevrimdışı aç ──────────────────────────────
    val playDownloaded: (AnimeDownload) -> Unit = { download ->
        scope.launch {
            if (download.isClip) {
                val file = download.localPath?.let { File(it) }
                if (file != null && file.isFile && file.length() > 0L) {
                    KitsugiFullscreenPlayerActivity.startWithStreamUrls(
                        context = context,
                        videoUrl = "file://${file.absolutePath}",
                        title = download.clipTitle ?: download.animeTitle,
                        headers = emptyMap(),
                        subtitles = emptyList(),
                        treatAsTrailer = true
                    )
                } else {
                    Toast.makeText(
                        context,
                        KitsugiStrings.get(R.string.kitsugi_clip_error_empty),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@launch
            }
            val safeAnimeId = resolveSafeAnimeId(download.animeId, download.animeTitle)
            val mediaId = "${safeAnimeId}_ep${download.episode}"
            val params = withContext(Dispatchers.IO) {
                val localMedia = OfflinePlaybackHelper.getLocalMedia(context, mediaId)
                localMedia?.let { OfflinePlaybackHelper.buildPlayerParams(it) }
            }
            if (params != null) {
                KitsugiFullscreenPlayerActivity.startWithStreamUrls(
                    context = context,
                    videoUrl = params.videoUri,
                    title = params.title,
                    headers = params.headers,
                    subtitles = params.subtitles,
                    malId = download.malId,
                    aniListId = download.aniListId,
                    tmdbId = download.tmdbId,
                    season = download.season,
                    episode = download.episode,
                    animeTitle = download.animeTitle,
                    posterUrl = download.posterUrl
                )
            } else {
                download.localPath?.let { path ->
                    val companionSubs = withContext(Dispatchers.IO) {
                        val videoFile = File(path)
                        val parentDir = videoFile.parentFile
                        val baseName = videoFile.nameWithoutExtension
                        if (parentDir != null && parentDir.exists()) {
                            parentDir.listFiles()?.filter { f ->
                                f.isFile && f.name.startsWith("$baseName.") &&
                                    f.extension.lowercase() in COMPANION_SUB_EXTS
                            }?.map { subFile ->
                                val lang = subFile.nameWithoutExtension.removePrefix(baseName).trim('.', '_')
                                com.kitsugi.animelist.core.player.SubtitleInput(
                                    url = "file://${subFile.absolutePath}",
                                    name = lang.ifBlank { KitsugiStrings.get(R.string.bangumi_tag_subtitles) },
                                    lang = lang.ifBlank { "tr" }
                                )
                            } ?: emptyList()
                        } else emptyList()
                    }
                    KitsugiFullscreenPlayerActivity.startWithStreamUrls(
                        context = context,
                        videoUrl = "file://$path",
                        title = KitsugiStrings.get(
                            R.string.title_bolum_episode,
                            download.animeTitle,
                            download.episode
                        ),
                        headers = emptyMap(),
                        subtitles = companionSubs,
                        malId = download.malId,
                        aniListId = download.aniListId,
                        tmdbId = download.tmdbId,
                        season = download.season,
                        episode = download.episode,
                        animeTitle = download.animeTitle,
                        posterUrl = download.posterUrl
                    )
                }
            }
        }
    }

    // ── Klasör: indirilen bölümün klasörünü dosya yöneticisinde aç ──────────
    val openDownloadFolder: (AnimeDownload) -> Unit = { download ->
        if (download.isClip) {
            val target = download.localPath?.let { File(it).parentFile }
                ?: File(
                    OfflinePlaybackHelper.getDownloadsDir(context),
                    com.kitsugi.animelist.core.download.DownloadExportNaming.CLIP_FOLDER
                ).also { it.mkdirs() }
            openFolderInFileManager(context, target)
        } else {
            val safeAnimeId = resolveSafeAnimeId(download.animeId, download.animeTitle)
            val mediaId = "${safeAnimeId}_ep${download.episode}"
            val targetDir = download.localPath?.let { File(it).parentFile }
                ?: File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "Kitsugi/Video"
                ).takeIf { it.exists() }
                ?: File(OfflinePlaybackHelper.getDownloadsDir(context), mediaId)
            openFolderInFileManager(context, targetDir)
        }
    }

    // ── Filtrelenmiş listeler ────────────────────────────────────────────────
    val queryText = searchQuery.trim().lowercase(Locale.ROOT)

    val filteredDownloads = remember(downloads, videoFilter, queryText) {
        downloads
            .filter { it.matchesFilter(videoFilter) }
            .filter { download ->
                queryText.isBlank() ||
                    download.animeTitle.lowercase(Locale.ROOT).contains(queryText) ||
                    download.streamName?.lowercase(Locale.ROOT)?.contains(queryText) == true ||
                    download.streamTitle?.lowercase(Locale.ROOT)?.contains(queryText) == true ||
                    download.source?.lowercase(Locale.ROOT)?.contains(queryText) == true
            }
    }

    // Sıralama + gruplama → LazyColumn'a verilecek düz girdi listesi.
    val sortedDownloads = remember(filteredDownloads, videoSort) {
        sortDownloadsForDisplay(filteredDownloads, videoSort)
    }
    val videoEntries = remember(sortedDownloads, videoGrouped, collapsedVideoGroups) {
        buildVideoEntries(sortedDownloads, videoGrouped, collapsedVideoGroups)
    }

    val filteredSubtitles = remember(allSubtitles, subtitleSort, queryText) {
        allSubtitles
            .filter { item ->
                queryText.isBlank() ||
                    item.title.lowercase(Locale.ROOT).contains(queryText) ||
                    item.animeTitle?.lowercase(Locale.ROOT)?.contains(queryText) == true ||
                    item.language.lowercase(Locale.ROOT).contains(queryText)
            }
            .let { list ->
                when (subtitleSort) {
                    DownloadsSortOrder.NEWEST -> list.sortedByDescending { it.lastModified }
                    DownloadsSortOrder.OLDEST -> list.sortedBy { it.lastModified }
                    DownloadsSortOrder.NAME -> list.sortedBy { it.title.lowercase(Locale.ROOT) }
                    DownloadsSortOrder.SIZE -> list.sortedByDescending { it.fileSizeBytes }
                }
            }
    }

    val audioBytes = remember(allAudios) { allAudios.sumOf { it.sizeBytes } }
    val availableAudioFormats = remember(allAudios) {
        allAudios.map { it.formatUpper }.distinct().sorted()
    }
    val subtitleBytes = remember(allSubtitles) { allSubtitles.sumOf { it.fileSizeBytes } }
    val imageBytes = remember(allImages) { allImages.sumOf { it.fileSizeBytes } }

    val commonMenu: @Composable () -> Unit = {
        FlowRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            DownloadsFilterChip(label = stringResource(R.string.downloads_search_open), selected = searchOpen, accentColor = accentColor, leadingIcon = Icons.Search, onClick = { searchOpen = !searchOpen; if (!searchOpen) searchQuery = ""; menuOpen = false })
            DownloadsFilterChip(label = stringResource(R.string.page_open_folder), selected = false, accentColor = accentColor, leadingIcon = Icons.Folder, onClick = {
                    val rootDir = when (pagerState.currentPage) {
                        1 -> DownloadExportNaming.publicAudioDir().also { runCatching { it.mkdirs() } }
                        2 -> File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            "Kitsugi/Subtitles"
                        ).also { it.mkdirs() }
                        3 -> File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            "Kitsugi/Images"
                        ).also { it.mkdirs() }
                        else -> File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            "Kitsugi/Video"
                        ).also { it.mkdirs() }
                    }
                    openFolderInFileManager(context, rootDir)
                menuOpen = false
            })
            if (downloads.any { it.status == AnimeDownload.Status.DOWNLOADING || it.status == AnimeDownload.Status.QUEUE }) {
                DownloadsFilterChip(label = stringResource(R.string.downloads_pause_all), selected = false, accentColor = accentColor, leadingIcon = Icons.PauseCircle, onClick = pauseAllActive)
            }
            if (downloads.any { it.status == AnimeDownload.Status.PAUSED || it.status == AnimeDownload.Status.ERROR }) {
                DownloadsFilterChip(label = stringResource(R.string.downloads_resume_all), selected = false, accentColor = accentColor, leadingIcon = Icons.PlayCircle, onClick = resumeAllPaused)
            }
        }
    }
    if (menuOpen && pagerState.currentPage != 3) {
        val sheetScroll = rememberScrollState()
        KitsugiSheetOrDialog(onDismiss = { menuOpen = false }, innerColumnScrollState = sheetScroll) {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(sheetScroll).padding(vertical = 12.dp)) {
                Text(stringResource(R.string.page_controls), color = KitsugiColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                commonMenu()
                when (pagerState.currentPage) {
                    0 -> {
                                DownloadsVideoFilterRow(
                                    downloads = downloads,
                                    selected = videoFilter,
                                    accentColor = accentColor,
                                    onSelect = { videoFilter = it },
                                    onRefresh = allRefreshed,
                                    onDeleteCompleted = { list -> menuOpen = false; pendingDeleteCompleted = list }
                                )
                                DownloadsVideoControlsRow(
                                    sort = videoSort,
                                    onSortChange = { option ->
                                        videoSort = option
                                        DownloadsUiPrefs.setVideoSort(context, option)
                                    },
                                    grouped = videoGrouped,
                                    onToggleGroup = {
                                        val next = !videoGrouped
                                        videoGrouped = next
                                        DownloadsUiPrefs.setVideosGrouped(context, next)
                                        if (next) collapsedVideoGroups = emptySet()
                                    },
                                    onStartSelection = {
                                        menuOpen = false
                                        videoSelectionMode = true
                                        selectedVideoKeys = emptySet()
                                    },
                                    accentColor = accentColor
                                )
                    }
                    1 -> {
                        DownloadsAudioControlsRow(
                            sortOrder = audioSort,
                            onSortChange = { order ->
                                audioSort = order
                                DownloadsUiPrefs.setAudioSort(context, order)
                            },
                            groupedByMedia = audioGrouped,
                            onGroupedChange = { next ->
                                audioGrouped = next
                                DownloadsUiPrefs.setAudiosGrouped(context, next)
                            },
                            availableFormats = availableAudioFormats,
                            selectedFormat = selectedAudioFormat,
                            onSelectFormat = { selectedAudioFormat = it },
                            isSelectionMode = audioSelectionMode,
                            onToggleSelectionMode = {
                                menuOpen = false
                                audioSelectionMode = !audioSelectionMode
                            },
                            hasItems = allAudios.isNotEmpty(),
                            onPlayAll = {
                                menuOpen = false
                                if (allAudios.isNotEmpty()) {
                                    audioManager.playQueue(
                                        items = allAudios.map { it.toAudioPlayerItem() },
                                        startIndex = 0,
                                        openFullSheet = false
                                    )
                                }
                            },
                            onRefresh = { refreshAudiosTrigger++ }
                        )
                    }
                    else -> {
                                FlowRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    DownloadsSortOrder.entries.forEach { option ->
                                        DownloadsFilterChip(
                                            label = subtitleSortLabel(option),
                                            selected = subtitleSort == option,
                                            accentColor = accentColor,
                                            leadingIcon = Icons.Sort,
                                            onClick = { subtitleSort = option }
                                        )
                                    }
                                    DownloadsFilterChip(
                                        label = stringResource(R.string.downloads_select_subtitles),
                                        selected = false,
                                        accentColor = accentColor,
                                        leadingIcon = Icons.CheckCircle,
                                        onClick = {
                                            menuOpen = false
                                            subtitleSelectionMode = true
                                            selectedSubtitlePaths = emptySet()
                                        }
                                    )
                                    DownloadsFilterChip(
                                        label = stringResource(R.string.downloads_refresh),
                                        selected = false,
                                        accentColor = accentColor,
                                        leadingIcon = Icons.Refresh,
                                        onClick = { refreshSubsTrigger++ }
                                    )
                                }
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(KitsugiColors.Background)) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KitsugiColors.Background)
            .statusBarsPadding()
    ) {
        // ── Üst bar ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.ArrowBack,
                    contentDescription = stringResource(R.string.geri),
                    tint = KitsugiColors.TextPrimary
                )
            }
            Text(
                text = stringResource(R.string.settings_downloads),
                color = KitsugiColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )

            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                Icon(imageVector = Icons.Menu, contentDescription = stringResource(R.string.page_controls), tint = accentColor)
            }
        }

        // ── Arama alanı ──────────────────────────────────────────────────────
        if (searchOpen) {
            DownloadsSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onClose = {
                    searchQuery = ""
                    searchOpen = false
                },
                accentColor = accentColor
            )
        }

        // ── Cihazdaki indirmeler özeti (boyut + canlı ilerleme) ──────────────
        DownloadsOverviewReveal(
            visible = overviewScrollState.isVisible,
            modifier = Modifier.fillMaxWidth().testTag("downloads_overview_reveal")
        ) {
            DownloadsOverviewCard(
                downloads = downloads,
                audioCount = allAudios.size,
                audioBytes = audioBytes,
                activeAudioExportCount = activeAudioExports.size,
                subtitleCount = allSubtitles.size,
                subtitleBytes = subtitleBytes,
                imageCount = allImages.size,
                imageBytes = imageBytes,
                accentColor = accentColor,
                onPauseAll = pauseAllActive,
                onResumeAll = resumeAllPaused,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Sekme bandı: Videolar | Sesler | Altyazılar | Resimler — "Görünüm & Tercihler" bandıyla aynı
        // (geniş ekranlarda eşit bölünür, dar ekranlarda sıkışmadan yatay kaydırılabilir)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(KitsugiColors.Surface)
                .padding(3.dp)
        ) {
            val tabLabels = listOf(
                stringResource(R.string.downloads_videos_count, downloads.size),
                stringResource(R.string.downloads_audios_count, allAudios.size + activeAudioExports.size),
                stringResource(R.string.inline_subtitles_1_s_b81ee5c, allSubtitles.size),
                stringResource(R.string.downloads_images_count, allImages.size)
            )
            val useEqualWeights = maxWidth >= 380.dp
            Row(
                modifier = if (useEqualWeights) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                },
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                tabLabels.forEachIndexed { index, title ->
                    val tabModifier = if (useEqualWeights) {
                        Modifier.weight(1f)
                    } else {
                        Modifier.padding(horizontal = 4.dp)
                    }
                    Box(
                        modifier = tabModifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (pagerState.currentPage == index) accentColor.copy(alpha = 0.18f)
                                else Color.Transparent
                            )
                            .clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                            .padding(horizontal = 6.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            color = if (pagerState.currentPage == index) accentColor else KitsugiColors.TextSecondary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // ── İçerik ───────────────────────────────────────────────────────────
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .nestedScroll(overviewScrollState.nestedScrollConnection)
        ) { page ->
            when (page) {
                0 -> {
                    // ── VİDEOLAR ─────────────────────────────────────────────
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (downloads.isEmpty()) {
                            DownloadsEmptyState(
                                icon = Icons.DownloadForOffline,
                                title = stringResource(R.string.inline_no_videos_have_been_downloaded_89e49a0),
                                subtitle = null
                            )
                        } else {
                            if (videoSelectionMode) {
                                DownloadsSelectionBar(
                                    count = selectedVideoKeys.size,
                                    accentColor = accentColor,
                                    onClose = {
                                        videoSelectionMode = false
                                        selectedVideoKeys = emptySet()
                                    },
                                    onSelectAll = {
                                        val visibleKeys = sortedDownloads.map { videoKey(it) }
                                        selectedVideoKeys = if (
                                            visibleKeys.isNotEmpty() && selectedVideoKeys.containsAll(visibleKeys)
                                        ) {
                                            emptySet()
                                        } else {
                                            visibleKeys.toCollection(LinkedHashSet())
                                        }
                                    },
                                    onDelete = {
                                        if (selectedVideoKeys.isNotEmpty()) deleteSelectedVideosPending = true
                                    }
                                )
                            }

                            if (videoEntries.isEmpty()) {
                                DownloadsEmptyState(
                                    icon = Icons.SearchOff,
                                    title = stringResource(R.string.downloads_no_results),
                                    subtitle = stringResource(R.string.downloads_no_results_hint)
                                )
                            } else {
                                LazyColumn(
                                    state = videoListState,
                                    modifier = Modifier.fillMaxSize().testTag("downloads_video_list"),
                                    contentPadding = PaddingValues(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 4.dp,
                                        bottom = 24.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(
                                        count = videoEntries.size,
                                        key = { index -> videoEntries[index].key },
                                        contentType = { index ->
                                            if (videoEntries[index] is VideoListEntry.Header) "header" else "row"
                                        }
                                    ) { index ->
                                        when (val entry = videoEntries[index]) {
                                            is VideoListEntry.Header -> {
                                                val groupSelected = remember(entry.items, selectedVideoKeys) {
                                                    entry.items.isNotEmpty() && entry.items.all {
                                                        videoKey(it) in selectedVideoKeys
                                                    }
                                                }
                                                VideoGroupHeaderRow(
                                                    title = entry.title,
                                                    items = entry.items,
                                                    collapsed = entry.key in collapsedVideoGroups,
                                                    selectionMode = videoSelectionMode,
                                                    allSelected = groupSelected,
                                                    accentColor = accentColor,
                                                    onClick = {
                                                        if (videoSelectionMode) {
                                                            val keys = entry.items.map { videoKey(it) }
                                                            selectedVideoKeys = if (groupSelected) {
                                                                selectedVideoKeys - keys.toSet()
                                                            } else {
                                                                selectedVideoKeys + keys
                                                            }
                                                        } else {
                                                            collapsedVideoGroups =
                                                                if (entry.key in collapsedVideoGroups) {
                                                                    collapsedVideoGroups - entry.key
                                                                } else {
                                                                    collapsedVideoGroups + entry.key
                                                                }
                                                        }
                                                    }
                                                )
                                            }

                                            is VideoListEntry.Item -> {
                                                val download = entry.download
                                                val key = videoKey(download)
                                                // Kuyruk taşıma yalnızca kuyruk sırası görünümünde
                                                // ve gerçekten başka bekleyen iş varsa anlamlı.
                                                val canReorder = videoSort == DownloadsVideoSort.QUEUE_ORDER &&
                                                    download.status == AnimeDownload.Status.QUEUE
                                                DownloadItemRow(
                                                    download = download,
                                                    accentColor = accentColor,
                                                    selectionMode = videoSelectionMode,
                                                    selected = key in selectedVideoKeys,
                                                    onLongClick = {
                                                        if (!videoSelectionMode) {
                                                            videoSelectionMode = true
                                                            selectedVideoKeys = setOf(key)
                                                        } else {
                                                            selectedVideoKeys = if (key in selectedVideoKeys) {
                                                                selectedVideoKeys - key
                                                            } else {
                                                                selectedVideoKeys + key
                                                            }
                                                        }
                                                    },
                                                    onMoveUp = if (canReorder) {
                                                        {
                                                            AnimeDownloadManager.moveQueuedDownload(
                                                                download.animeId,
                                                                download.episode,
                                                                -1
                                                            )
                                                            Unit
                                                        }
                                                    } else null,
                                                    onMoveDown = if (canReorder) {
                                                        {
                                                            AnimeDownloadManager.moveQueuedDownload(
                                                                download.animeId,
                                                                download.episode,
                                                                1
                                                            )
                                                            Unit
                                                        }
                                                    } else null,
                                                    onPlay = { playDownloaded(download) },
                                                    onPause = {
                                                        AnimeDownloadManager.pauseDownload(
                                                            download.animeId,
                                                            download.episode
                                                        )
                                                    },
                                                    onResume = {
                                                        AnimeDownloadManager.resumeDownload(
                                                            context,
                                                            download.animeId,
                                                            download.episode
                                                        )
                                                    },
                                                    onDelete = { pendingVideoDelete = download },
                                                    onOpenFolder = { openDownloadFolder(download) },
                                                    onOpenSubFolder = { subFile ->
                                                        openFolderInFileManager(
                                                            context,
                                                            subFile.parentFile ?: subFile
                                                        )
                                                    },
                                                    companionAudios = if (download.isClip) {
                                                        emptyList()
                                                    } else {
                                                        findCompanionAudiosForEpisode(
                                                            allAudios,
                                                            download.animeTitle,
                                                            download.episode
                                                        )
                                                    },
                                                    onPlayCompanionAudio = { targetAudio, episodeAudios ->
                                                        val idx = episodeAudios.indexOfFirst { it.id == targetAudio.id }
                                                            .coerceAtLeast(0)
                                                        audioManager.playQueue(
                                                            items = episodeAudios.map { it.toAudioPlayerItem() },
                                                            startIndex = idx,
                                                            openFullSheet = false
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                        }
                    }
                }

                1 -> {
                    // ── SESLER (indirilen ses parçaları + canlı dışa aktarımlar + dahili oynatıcı) ──
                    Column(modifier = Modifier.fillMaxSize()) {
                        DownloadsAudioControlsRow(
                            sortOrder = audioSort,
                            onSortChange = { order ->
                                audioSort = order
                                DownloadsUiPrefs.setAudioSort(context, order)
                            },
                            groupedByMedia = audioGrouped,
                            onGroupedChange = { next ->
                                audioGrouped = next
                                DownloadsUiPrefs.setAudiosGrouped(context, next)
                            },
                            availableFormats = availableAudioFormats,
                            selectedFormat = selectedAudioFormat,
                            onSelectFormat = { selectedAudioFormat = it },
                            isSelectionMode = audioSelectionMode,
                            onToggleSelectionMode = { audioSelectionMode = !audioSelectionMode },
                            hasItems = allAudios.isNotEmpty(),
                            onPlayAll = {
                                if (allAudios.isNotEmpty()) {
                                    audioManager.playQueue(
                                        items = allAudios.map { it.toAudioPlayerItem() },
                                        startIndex = 0,
                                        openFullSheet = false
                                    )
                                }
                            },
                            onRefresh = { refreshAudiosTrigger++ }
                        )
                        DownloadedAudiosTabContent(
                            audioItems = allAudios,
                            activeExports = activeAudioExports,
                            isLoading = isLoadingAudios,
                            listState = audioListState,
                            sortOrder = audioSort,
                            groupedByMedia = audioGrouped,
                            selectedFormat = selectedAudioFormat,
                            isSelectionMode = audioSelectionMode,
                            onExitSelectionMode = { audioSelectionMode = false },
                            onRefreshRequested = { refreshAudiosTrigger++ },
                            bottomExtraPadding = if (audioPlayerState.isActive) 76.dp else 0.dp
                        )
                    }
                }

                2 -> {
                    // ── ALTYAZILAR ───────────────────────────────────────────
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (allSubtitles.isEmpty()) {
                            DownloadsEmptyState(
                                icon = Icons.Subtitles,
                                title = stringResource(R.string.inline_no_downloaded_subtitles_found_yet_160c9c9),
                                subtitle = stringResource(R.string.inline_subtitles_downloaded_from_the_play_3fcd255)
                            )
                        } else {
                            if (subtitleSelectionMode) {
                                DownloadsSelectionBar(
                                    count = selectedSubtitlePaths.size,
                                    accentColor = accentColor,
                                    onClose = {
                                        subtitleSelectionMode = false
                                        selectedSubtitlePaths = emptySet()
                                    },
                                    onSelectAll = {
                                        val visiblePaths = filteredSubtitles.map { it.file.absolutePath }
                                        selectedSubtitlePaths = if (
                                            visiblePaths.isNotEmpty() &&
                                            selectedSubtitlePaths.containsAll(visiblePaths)
                                        ) {
                                            emptySet()
                                        } else {
                                            visiblePaths.toCollection(LinkedHashSet())
                                        }
                                    },
                                    onDelete = {
                                        if (selectedSubtitlePaths.isNotEmpty()) {
                                            deleteSelectedSubtitlesPending = true
                                        }
                                    }
                                )
                            }

                            if (filteredSubtitles.isEmpty()) {
                                DownloadsEmptyState(
                                    icon = Icons.SearchOff,
                                    title = stringResource(R.string.downloads_no_results),
                                    subtitle = stringResource(R.string.downloads_no_results_hint)
                                )
                            } else {
                                LazyColumn(
                                    state = subtitleListState,
                                    modifier = Modifier.fillMaxSize().testTag("downloads_subtitle_list"),
                                    contentPadding = PaddingValues(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 4.dp,
                                        bottom = 24.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(filteredSubtitles, key = { it.file.absolutePath }) { subItem ->
                                        val path = subItem.file.absolutePath
                                        SubtitleCardItem(
                                            item = subItem,
                                            accentColor = accentColor,
                                            selectionMode = subtitleSelectionMode,
                                            selected = path in selectedSubtitlePaths,
                                            onLongClick = {
                                                if (!subtitleSelectionMode) {
                                                    subtitleSelectionMode = true
                                                    selectedSubtitlePaths = setOf(path)
                                                } else {
                                                    selectedSubtitlePaths =
                                                        if (path in selectedSubtitlePaths) {
                                                            selectedSubtitlePaths - path
                                                        } else {
                                                            selectedSubtitlePaths + path
                                                        }
                                                }
                                            },
                                            onOpenFolder = {
                                                openFolderInFileManager(
                                                    context,
                                                    subItem.file.parentFile ?: subItem.file
                                                )
                                            },
                                            onDelete = { deleteSubtitleItem(subItem) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    // ── RESİMLER (ekran boyutuna göre otomatik ızgara) ───────
                    DownloadedImagesTab(
                        images = allImages,
                        accentColor = accentColor,
                        onRefresh = { refreshImagesTrigger++ },
                        onDeleteImages = deleteImageFiles,
                        query = searchQuery,
                        gridState = imageGridState,
                        menuOpen = menuOpen && pagerState.currentPage == 3,
                        onDismissMenu = { menuOpen = false },
                        commonMenu = commonMenu
                    )
                }
            }
        }
    }

    // ── Dahili evrensel ses oynatıcısı (mini çubuk + tam ekran panel) ────────
    KitsugiAudioPlayerHost(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
    )
    } // Box

    // ── Onay: tekli video indirmesini sil ────────────────────────────────────
    pendingVideoDelete?.let { download ->
        AlertDialog(
            onDismissRequest = { pendingVideoDelete = null },
            containerColor = KitsugiColors.SurfaceSoft,
            title = {
                Text(
                    text = stringResource(R.string.downloads_delete_video_title),
                    color = KitsugiColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = if (download.isClip) {
                        download.clipTitle ?: download.animeTitle
                    } else {
                        stringResource(
                            R.string.downloads_delete_video_message,
                            download.animeTitle,
                            download.episode
                        )
                    },
                    color = KitsugiColors.TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = download
                    pendingVideoDelete = null
                    deleteVideoNow(target)
                }) {
                    Text(stringResource(R.string.action_delete), color = KitsugiColors.AccentRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingVideoDelete = null }) {
                    Text(stringResource(R.string.vazgec), color = KitsugiColors.TextSecondary)
                }
            }
        )
    }

    // ── Onay: tamamlanan indirmeleri topluca sil ─────────────────────────────
    if (pendingDeleteCompleted.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingDeleteCompleted = emptyList() },
            containerColor = KitsugiColors.SurfaceSoft,
            title = {
                Text(
                    text = stringResource(R.string.downloads_delete_completed_title),
                    color = KitsugiColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.downloads_delete_completed_message,
                        pendingDeleteCompleted.size
                    ),
                    color = KitsugiColors.TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val targets = pendingDeleteCompleted
                    pendingDeleteCompleted = emptyList()
                    deleteCompletedNow(targets)
                }) {
                    Text(stringResource(R.string.action_delete), color = KitsugiColors.AccentRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteCompleted = emptyList() }) {
                    Text(stringResource(R.string.vazgec), color = KitsugiColors.TextSecondary)
                }
            }
        )
    }

    // ── Onay: seçilen video indirmelerini sil ────────────────────────────────
    if (deleteSelectedVideosPending) {
        val selected = downloads.filter { videoKey(it) in selectedVideoKeys }
        AlertDialog(
            onDismissRequest = { deleteSelectedVideosPending = false },
            containerColor = KitsugiColors.SurfaceSoft,
            title = {
                Text(
                    text = stringResource(R.string.downloads_delete_selected_videos_title),
                    color = KitsugiColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.downloads_delete_selected_videos_message,
                        selected.size
                    ),
                    color = KitsugiColors.TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteSelectedVideosPending = false
                    videoSelectionMode = false
                    selectedVideoKeys = emptySet()
                    deleteCompletedNow(selected)
                }) {
                    Text(stringResource(R.string.action_delete), color = KitsugiColors.AccentRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteSelectedVideosPending = false }) {
                    Text(stringResource(R.string.vazgec), color = KitsugiColors.TextSecondary)
                }
            }
        )
    }

    // ── Onay: seçilen altyazıları sil ────────────────────────────────────────
    if (deleteSelectedSubtitlesPending) {
        val selected = filteredSubtitles.filter { it.file.absolutePath in selectedSubtitlePaths }
        AlertDialog(
            onDismissRequest = { deleteSelectedSubtitlesPending = false },
            containerColor = KitsugiColors.SurfaceSoft,
            title = {
                Text(
                    text = stringResource(R.string.downloads_delete_selected_subtitles_title),
                    color = KitsugiColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.downloads_delete_selected_subtitles_message,
                        selected.size
                    ),
                    color = KitsugiColors.TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteSelectedSubtitlesPending = false
                    subtitleSelectionMode = false
                    selectedSubtitlePaths = emptySet()
                    deleteSubtitleBatch(selected)
                }) {
                    Text(stringResource(R.string.action_delete), color = KitsugiColors.AccentRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteSelectedSubtitlesPending = false }) {
                    Text(stringResource(R.string.vazgec), color = KitsugiColors.TextSecondary)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ORTAK KÜÇÜK BİLEŞENLER
// ─────────────────────────────────────────────────────────────────────────────

/** İndirilenler ekranındaki filtre/sıralama düğmeleri (tek tip görünüm). */
@Composable
internal fun DownloadsFilterChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) accentColor.copy(alpha = 0.18f) else KitsugiColors.SurfaceSoft)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) accentColor.copy(alpha = 0.7f) else KitsugiColors.Border.copy(alpha = 0.5f),
                shape = RoundedCornerShape(999.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = if (selected) accentColor else KitsugiColors.TextMuted,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = label,
            color = if (selected) accentColor else KitsugiColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

/** Boş durum / sonuç yok ekranı. */
@Composable
internal fun DownloadsEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String?
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = KitsugiColors.TextMuted,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                color = KitsugiColors.TextMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = KitsugiColors.TextMuted.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
internal fun DownloadsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    accentColor: Color
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(KitsugiColors.SurfaceSoft)
            .border(1.dp, KitsugiColors.Border.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Search,
            contentDescription = null,
            tint = KitsugiColors.TextMuted,
            modifier = Modifier.size(17.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.downloads_search_hint),
                    color = KitsugiColors.TextMuted,
                    fontSize = 13.sp
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = KitsugiColors.TextPrimary, fontSize = 13.sp),
                cursorBrush = SolidColor(accentColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .focusRequester(focusRequester)
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
            Icon(
                imageVector = Icons.Close,
                contentDescription = stringResource(R.string.downloads_search_close),
                tint = KitsugiColors.TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun subtitleSortLabel(sort: DownloadsSortOrder): String = stringResource(
    when (sort) {
        DownloadsSortOrder.NEWEST -> R.string.downloads_sort_newest
        DownloadsSortOrder.OLDEST -> R.string.downloads_sort_oldest
        DownloadsSortOrder.NAME -> R.string.downloads_sort_name
        DownloadsSortOrder.SIZE -> R.string.downloads_sort_size
    }
)

/**
 * Videolar sekmesinin durum filtresi.
 * Yalnızca gerçekten kayıt bulunan filtreler gösterilir ("Tümü" her zaman görünür).
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DownloadsVideoFilterRow(
    downloads: List<AnimeDownload>,
    selected: DownloadsVideoFilter,
    accentColor: Color,
    onSelect: (DownloadsVideoFilter) -> Unit,
    onRefresh: () -> Unit,
    onDeleteCompleted: (List<AnimeDownload>) -> Unit
) {
    val counts = remember(downloads) {
        DownloadsVideoFilter.entries.associateWith { filter ->
            downloads.count { it.matchesFilter(filter) }
        }
    }
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DownloadsVideoFilter.entries.forEach { filter ->
            val count = counts[filter] ?: 0
            if (filter == DownloadsVideoFilter.ALL || count > 0) {
                DownloadsFilterChip(
                    label = "${videoFilterLabel(filter)} ($count)",
                    selected = selected == filter,
                    accentColor = accentColor,
                    onClick = { onSelect(filter) }
                )
            }
        }
        // Manuel tazeleme: dosya yöneticisinden/dışarıdan değişen disk durumunu yeniden tara.
        DownloadsFilterChip(
            label = stringResource(R.string.downloads_refresh),
            selected = false,
            accentColor = accentColor,
            leadingIcon = Icons.Refresh,
            onClick = onRefresh
        )
        // Depolama yönetimi: tamamlanmış bölümleri tek seferde sil (onaylı).
        val completed = remember(downloads) {
            downloads.filter { it.status == AnimeDownload.Status.COMPLETED }
        }
        if (completed.isNotEmpty()) {
            DownloadsFilterChip(
                label = stringResource(R.string.downloads_delete_completed, completed.size),
                selected = false,
                accentColor = accentColor,
                leadingIcon = Icons.DeleteSweep,
                onClick = { onDeleteCompleted(completed) }
            )
        }
    }
}

@Composable
private fun videoFilterLabel(filter: DownloadsVideoFilter): String = stringResource(
    when (filter) {
        DownloadsVideoFilter.ALL -> R.string.downloads_filter_all
        DownloadsVideoFilter.ACTIVE -> R.string.downloads_filter_downloading
        DownloadsVideoFilter.PAUSED -> R.string.downloads_filter_paused
        DownloadsVideoFilter.COMPLETED -> R.string.downloads_filter_completed
        DownloadsVideoFilter.ERROR -> R.string.downloads_filter_error
    }
)

@Composable
private fun videoSortLabel(sort: DownloadsVideoSort): String = stringResource(
    when (sort) {
        DownloadsVideoSort.QUEUE_ORDER -> R.string.downloads_video_sort_queue
        DownloadsVideoSort.TITLE -> R.string.downloads_sort_name
        DownloadsVideoSort.EPISODE -> R.string.downloads_video_sort_episode
        DownloadsVideoSort.SIZE -> R.string.downloads_sort_size
        DownloadsVideoSort.RECENT -> R.string.downloads_sort_newest
    }
)

/**
 * Videolar sekmesinin ikinci araç çubuğu: sıralama, gruplama ve çoklu seçim.
 * (Durum filtresi ayrı satırda; böylece dar ekranlarda iki satır da taşmadan kaydırılır.)
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DownloadsVideoControlsRow(
    sort: DownloadsVideoSort,
    onSortChange: (DownloadsVideoSort) -> Unit,
    grouped: Boolean,
    onToggleGroup: () -> Unit,
    onStartSelection: () -> Unit,
    accentColor: Color
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        var sortMenuOpen by remember { mutableStateOf(false) }
        Box {
            DownloadsFilterChip(
                label = videoSortLabel(sort),
                selected = sort != DownloadsVideoSort.QUEUE_ORDER,
                accentColor = accentColor,
                leadingIcon = Icons.Sort,
                onClick = { sortMenuOpen = true }
            )
            DropdownMenu(
                expanded = sortMenuOpen,
                onDismissRequest = { sortMenuOpen = false }
            ) {
                DownloadsVideoSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = videoSortLabel(option),
                                fontSize = 13.sp,
                                fontWeight = if (option == sort) FontWeight.Bold else FontWeight.Normal,
                                color = if (option == sort) accentColor else KitsugiColors.TextPrimary
                            )
                        },
                        onClick = {
                            onSortChange(option)
                            sortMenuOpen = false
                        }
                    )
                }
            }
        }
        DownloadsFilterChip(
            label = if (grouped) {
                stringResource(R.string.downloads_group_on)
            } else {
                stringResource(R.string.downloads_group_off)
            },
            selected = !grouped,
            accentColor = accentColor,
            leadingIcon = Icons.Layers,
            onClick = onToggleGroup
        )
        DownloadsFilterChip(
            label = stringResource(R.string.downloads_select_videos),
            selected = false,
            accentColor = accentColor,
            leadingIcon = Icons.CheckCircle,
            onClick = onStartSelection
        )
    }
}

/**
 * Çoklu seçim araç çubuğu (videolar ve altyazılar için ortak).
 * Kapat, seçim sayısı, tümünü seç/temizle ve seçilenleri sil.
 */
@Composable
internal fun DownloadsSelectionBar(
    count: Int,
    accentColor: Color,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = Icons.Close,
                contentDescription = stringResource(R.string.downloads_clear_selection),
                tint = KitsugiColors.TextPrimary,
                modifier = Modifier.size(19.dp)
            )
        }
        Text(
            text = stringResource(R.string.downloads_selected_count, count),
            color = KitsugiColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onSelectAll, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = Icons.SelectAll,
                contentDescription = stringResource(R.string.downloads_select_all),
                tint = accentColor,
                modifier = Modifier.size(19.dp)
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(34.dp),
            enabled = count > 0
        ) {
            Icon(
                imageVector = Icons.Delete,
                contentDescription = stringResource(R.string.action_delete),
                tint = if (count == 0) KitsugiColors.TextMuted else KitsugiColors.AccentRed,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

/**
 * Videolar sekmesindeki içerik (anime) başlığı.
 * Normal modda dokununca grubu daraltır/açar; seçim modunda grubun tamamını seçer.
 */
@Composable
private fun VideoGroupHeaderRow(
    title: String,
    items: List<AnimeDownload>,
    collapsed: Boolean,
    selectionMode: Boolean,
    allSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val completedCount = remember(items) {
        items.count { it.status == AnimeDownload.Status.COMPLETED }
    }
    val groupBytes = remember(items) {
        items.sumOf { it.downloadedBytes.coerceAtLeast(0L) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accentColor.copy(alpha = 0.15f))
                .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.VideoLibrary,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(14.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(
                    R.string.downloads_video_group_badge,
                    items.size,
                    completedCount,
                    formatBytes(groupBytes)
                ),
                color = KitsugiColors.TextMuted,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (allSelected) accentColor else KitsugiColors.SurfaceStrong.copy(alpha = 0.7f)
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (allSelected) accentColor else KitsugiColors.TextMuted,
                        shape = RoundedCornerShape(999.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (allSelected) {
                    Icon(
                        imageVector = Icons.Check,
                        contentDescription = null,
                        tint = KitsugiColors.OnAccent,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        } else {
            Icon(
                imageVector = if (collapsed) {
                    Icons.KeyboardArrowDown
                } else {
                    Icons.KeyboardArrowUp
                },
                contentDescription = null,
                tint = KitsugiColors.TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ÖZET KARTI (boyutlar + canlı ilerleme + hız)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Cihazdaki tüm indirmelerin özeti:
 * tür bazlı adet/boyut, toplam boyut, devam eden indirmelerin birleşik ilerlemesi,
 * anlık toplam hız ve tahmini kalan süre. Hiç indirme yoksa kart gizlenir.
 */
@Composable
private fun DownloadsOverviewCard(
    downloads: List<AnimeDownload>,
    audioCount: Int,
    audioBytes: Long,
    activeAudioExportCount: Int = 0,
    subtitleCount: Int,
    subtitleBytes: Long,
    imageCount: Int,
    imageBytes: Long,
    accentColor: Color,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val videoBytes = remember(downloads) { downloads.sumOf { it.downloadedBytes.coerceAtLeast(0L) } }
    val activeDownloads = remember(downloads) {
        downloads.filter {
            it.status == AnimeDownload.Status.DOWNLOADING || it.status == AnimeDownload.Status.QUEUE
        }
    }
    val pausedCount = remember(downloads) {
        downloads.count { it.status == AnimeDownload.Status.PAUSED || it.status == AnimeDownload.Status.ERROR }
    }

    val totalBytes = videoBytes + audioBytes + subtitleBytes + imageBytes
    if (totalBytes <= 0L && downloads.isEmpty() && audioCount == 0 && activeAudioExportCount == 0 && subtitleCount == 0 && imageCount == 0) return

    val activeBytes = activeDownloads.sumOf { it.downloadedBytes.coerceAtLeast(0L) }
    val activeTotalBytes = activeDownloads.sumOf { it.totalBytes.coerceAtLeast(0L) }
    val overallProgress = if (activeTotalBytes > 0L) {
        (activeBytes.toFloat() / activeTotalBytes).coerceIn(0f, 1f)
    } else 0f
    val speed = rememberTransferSpeed(active = activeDownloads.isNotEmpty(), bytes = activeBytes)
    val speedHistory = rememberSpeedHistory(
        active = activeDownloads.isNotEmpty(),
        speed = speed
    )
    // Boş alanı her ilerleme karesinde değil; içerik silinip eklendiğinde ve
    // indirme sürerken 5 sn'de bir arka planda tazele. Böylece karttaki "boş"
    // değeri indirme bitmeden veya resim/altyazı silinince bayatlamaz.
    val completedCount = remember(downloads) { downloads.count { it.status == AnimeDownload.Status.COMPLETED } }
    val hasActiveDownloads = activeDownloads.isNotEmpty() || activeAudioExportCount > 0
    var freeSpaceBytes by remember { mutableLongStateOf(0L) }
    LaunchedEffect(downloads.size, completedCount, audioCount, subtitleCount, imageCount, hasActiveDownloads) {
        suspend fun readFreeSpace(): Long = withContext(Dispatchers.IO) {
            runCatching {
                android.os.StatFs(Environment.getExternalStorageDirectory().absolutePath).availableBytes
            }.getOrDefault(0L)
        }
        freeSpaceBytes = readFreeSpace()
        while (hasActiveDownloads) {
            delay(5_000L)
            freeSpaceBytes = readFreeSpace()
        }
    }
    val remainingMs = if (speed > 0L && activeTotalBytes > activeBytes) {
        ((activeTotalBytes - activeBytes) * 1000L) / speed
    } else 0L

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KitsugiColors.Surface)
            .border(1.dp, KitsugiColors.Border.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Storage,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.downloads_overview_title),
                    color = KitsugiColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.downloads_overview_total, formatBytes(totalBytes)),
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Tür bazlı boyut dağılımı (video / ses / altyazı / resim)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OverviewStatPill(Icons.VideoLibrary, downloads.size, videoBytes, KitsugiColors.AccentBlue)
            if (audioCount > 0 || activeAudioExportCount > 0) {
                OverviewStatPill(Icons.AudioLines, audioCount + activeAudioExportCount, audioBytes, KitsugiColors.AccentTeal)
            }
            if (subtitleCount > 0) {
                OverviewStatPill(Icons.Subtitles, subtitleCount, subtitleBytes, KitsugiColors.AccentOrange)
            }
            if (imageCount > 0) {
                OverviewStatPill(Icons.Image, imageCount, imageBytes, KitsugiColors.AccentPurple)
            }
            if (freeSpaceBytes > 0L) {
                OverviewFreeSpacePill(freeSpaceBytes)
            }
        }

        if (activeDownloads.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { overallProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = accentColor,
                trackColor = KitsugiColors.SurfaceStrong
            )
            Spacer(modifier = Modifier.height(5.dp))
            // Metinler composable dışı lambda içinde çözülmesin diye burada hazırlanır.
            val activeLabel = stringResource(R.string.downloads_active_downloads, activeDownloads.size) +
                " • " + "${(overallProgress * 100f).toInt()}%"
            val speedLabel = if (speed > 0L) {
                val perSecond = stringResource(R.string.downloads_per_second, formatBytes(speed))
                if (remainingMs > 0L) {
                    perSecond + " • " +
                        stringResource(R.string.downloads_eta_remaining, formatDuration(remainingMs))
                } else {
                    perSecond
                }
            } else {
                ""
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = activeLabel,
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (speedLabel.isNotEmpty()) {
                    Text(
                        text = speedLabel,
                        color = KitsugiColors.TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
            // Hız geçmişi (son ~40 sn): dalgalanmayı gösteren mini grafik
            if (speedHistory.size >= 2) {
                Spacer(modifier = Modifier.height(6.dp))
                SpeedSparkline(samples = speedHistory, accentColor = accentColor)
            }
        }


    }
}

/** Cihazda kalan boş alan rozeti (depolama planlaması için). */
@Composable
private fun OverviewFreeSpacePill(freeBytes: Long) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(KitsugiColors.SurfaceSoft)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = Icons.Storage,
            contentDescription = null,
            tint = KitsugiColors.AccentGreen,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = stringResource(R.string.downloads_free_space, formatBytes(freeBytes)),
            color = KitsugiColors.TextMuted,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun OverviewStatPill(
    icon: ImageVector,
    count: Int,
    bytes: Long,
    tint: Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(KitsugiColors.SurfaceSoft)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = "$count",
            color = KitsugiColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = formatBytes(bytes),
            color = KitsugiColors.TextMuted,
            fontSize = 10.sp
        )
    }
}

/**
 * Aktarılan bayt miktarından anlık hız (bayt/sn) tahmini.
 *
 * İndirme yöneticisi hız bilgisi tutmuyor; bu yardımcı iki örnek arasındaki farkı
 * ölçüp üstel hareketli ortalama ile yumuşatır (tek karelik sıçramalar hızı bozmaz).
 */
@Composable
internal fun rememberTransferSpeed(active: Boolean, bytes: Long): Long {
    var speed by remember { mutableStateOf(0L) }
    var lastTime by remember { mutableStateOf(0L) }
    var lastBytes by remember { mutableStateOf(0L) }

    LaunchedEffect(active, bytes) {
        val now = System.currentTimeMillis()
        if (!active) {
            speed = 0L
            lastTime = 0L
            lastBytes = bytes
            return@LaunchedEffect
        }
        val elapsed = now - lastTime
        if (lastTime > 0L && elapsed in 120..8_000L) {
            val delta = bytes - lastBytes
            val instant = if (delta > 0L) (delta * 1000L) / elapsed else 0L
            speed = if (speed <= 0L) instant else (speed * 3L + instant) / 4L
        }
        lastTime = now
        lastBytes = bytes
    }

    return if (active) speed else 0L
}

/**
 * Son N saniyenin hız örneklerini tutan hafif geçmiş arabelleği (mini grafik için).
 * İndirme durunca temizlenir; her saniye tek örnek eklenir (recomposition seli yok).
 */
@Composable
internal fun rememberSpeedHistory(
    active: Boolean,
    speed: Long,
    maxSamples: Int = 40
): List<Long> {
    val samples = remember { mutableStateListOf<Long>() }
    val latestSpeed by rememberUpdatedState(speed)
    LaunchedEffect(active) {
        if (!active) {
            samples.clear()
            return@LaunchedEffect
        }
        while (true) {
            samples.add(latestSpeed)
            if (samples.size > maxSamples) samples.removeAt(0)
            delay(1000L)
        }
    }
    return samples
}

/** Anlık hız geçmişini çizen mini çizgi grafik (sparkline). */
@Composable
private fun SpeedSparkline(
    samples: List<Long>,
    accentColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(KitsugiColors.SurfaceSoft)
            .padding(horizontal = 6.dp, vertical = 5.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (samples.size < 2) return@Canvas
            val maxValue = (samples.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
            val stepX = size.width / (samples.size - 1).toFloat()
            val path = Path()
            samples.forEachIndexed { index, value ->
                val x = stepX * index
                val y = size.height - (value / maxValue) * size.height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = accentColor,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

/** "1 sa 05 dk" / "12 dk 30 sn" / "45 sn" biçiminde kısa süre metni. */
@Composable
internal fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0L) return "—"
    val totalSeconds = durationMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    val hourUnit = stringResource(R.string.downloads_duration_hour)
    val minuteUnit = stringResource(R.string.downloads_duration_minute)
    val secondUnit = stringResource(R.string.downloads_duration_second)
    return when {
        hours > 0L -> "$hours $hourUnit ${minutes.toString().padStart(2, '0')} $minuteUnit"
        minutes > 0L -> "$minutes $minuteUnit ${seconds.toString().padStart(2, '0')} $secondUnit"
        else -> "$seconds $secondUnit"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// VİDEO SATIRI
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Tek bir indirme satırı.
 *
 * @param selectionMode Çoklu seçim modu aktif mi? (uzun basış ile girilir)
 * @param selected Bu satır seçili mi?
 * @param onLongClick Uzun basış: seçim modunu açar / seçimi değiştirir.
 * @param onMoveUp Kuyrukta bir üst sıraya taşı (`null` ise düğme gizlenir).
 * @param onMoveDown Kuyrukta bir alt sıraya taşı (`null` ise düğme gizlenir).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun DownloadItemRow(
    download: AnimeDownload,
    accentColor: Color,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onOpenFolder: () -> Unit,
    onOpenSubFolder: (File) -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    companionAudios: List<DownloadedAudioItem> = emptyList(),
    onPlayCompanionAudio: ((DownloadedAudioItem, List<DownloadedAudioItem>) -> Unit)? = null
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(true) }
    var isAudiosExpanded by remember { mutableStateOf(true) }

    val mediaId = remember(download.animeId, download.animeTitle, download.episode) {
        "${resolveSafeAnimeId(download.animeId, download.animeTitle)}_ep${download.episode}"
    }

    // PERF: İndirme sürerken (DOWNLOADING) her progress/byte artışında ana iş parçacığında
    // disk taraması (OfflinePlaybackHelper.getLocalMedia + listFiles) yapılmasını önle!
    // Altyazı ve metadata yalnızca indirme tamamlandığında (veya localPath oluştuğunda) IO'da okunur.
    val localSubs by produceState(
        initialValue = emptyList<OfflinePlaybackHelper.LocalSubtitle>(),
        key1 = mediaId,
        key2 = download.status,
        key3 = download.localPath
    ) {
        if (download.isClip ||
            (download.status != AnimeDownload.Status.COMPLETED && download.localPath.isNullOrBlank())
        ) {
            value = emptyList()
        } else {
            value = withContext(Dispatchers.IO) {
                val localMedia = OfflinePlaybackHelper.getLocalMedia(context, mediaId)
                if (!localMedia?.subtitles.isNullOrEmpty()) {
                    localMedia?.subtitles.orEmpty()
                } else {
                    download.localPath?.let { path ->
                        val vFile = File(path)
                        val pDir = vFile.parentFile
                        val bName = vFile.nameWithoutExtension
                        if (pDir != null && pDir.exists()) {
                            pDir.listFiles()?.filter { f ->
                                f.isFile && f.name.startsWith("$bName.") && f.extension.lowercase() in COMPANION_SUB_EXTS
                            }?.map { subFile ->
                                val lang = subFile.nameWithoutExtension.removePrefix(bName).trim('.', '_')
                                OfflinePlaybackHelper.LocalSubtitle(
                                    language = lang.ifBlank { "tr" },
                                    label = subFile.nameWithoutExtension,
                                    uri = "file://${subFile.absolutePath}"
                                )
                            } ?: emptyList()
                        } else emptyList()
                    } ?: emptyList()
                }
            }
        }
    }

    val isDownloading = download.status == AnimeDownload.Status.DOWNLOADING
    val speed = rememberTransferSpeed(active = isDownloading, bytes = download.downloadedBytes)
    val etaMs = if (isDownloading && speed > 0L && download.totalBytes > download.downloadedBytes) {
        ((download.totalBytes - download.downloadedBytes) * 1000L) / speed
    } else 0L

    val addedDate = remember(download.createdAt) {
        if (download.createdAt > 0L) {
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(download.createdAt))
        } else ""
    }

    val rowShape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(KitsugiColors.SurfaceSoft, shape = rowShape)
            .border(
                width = if (selected) 1.5.dp else 0.dp,
                color = if (selected) accentColor else Color.Transparent,
                shape = rowShape
            )
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = { if (selectionMode) onLongClick() },
                        onLongClick = onLongClick
                    )
                } else {
                    Modifier
                }
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Anime posteri (+ seçim işareti)
            Box(
                modifier = Modifier.size(width = 60.dp, height = 90.dp)
            ) {
                AsyncImage(
                    model = download.posterUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(KitsugiColors.SurfaceStrong),
                    contentScale = ContentScale.Crop
                )
                if (selectionMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(26.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (selected) accentColor else KitsugiColors.SurfaceStrong.copy(alpha = 0.78f)
                            )
                            .border(
                                width = 1.5.dp,
                                color = if (selected) accentColor else KitsugiColors.TextMuted,
                                shape = RoundedCornerShape(999.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Check,
                                contentDescription = null,
                                tint = KitsugiColors.OnAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Bilgi & ilerleme
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (download.isClip) {
                        download.clipTitle ?: download.animeTitle
                    } else download.animeTitle,
                    color = KitsugiColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (download.isClip) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = download.animeTitle,
                        color = KitsugiColors.TextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Eski indirmelerde eklenti metni HTML/CSS artığı içerebiliyor; gösterirken temizle.
                val cleanStreamName = remember(download.streamName) {
                    com.kitsugi.animelist.utils.StreamLabelSanitizer.sanitize(download.streamName)
                }
                if (cleanStreamName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.inline_stream_1_s_038cf7a, cleanStreamName),
                        color = accentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val cleanStreamTitle = remember(download.streamTitle) {
                    com.kitsugi.animelist.utils.StreamLabelSanitizer.sanitize(download.streamTitle)
                }
                if (cleanStreamTitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = cleanStreamTitle,
                        color = KitsugiColors.TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (download.isClip) {
                            stringResource(R.string.kitsugi_clip_label)
                        } else {
                            stringResource(R.string.inline_episode_1_s_2_s_32608a4, download.episode, download.quality)
                        },
                        color = KitsugiColors.TextSecondary,
                        fontSize = 13.sp
                    )
                    if (!download.source.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(KitsugiColors.AccentPurple.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = download.source,
                                color = KitsugiColors.AccentPurple,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                when (download.status) {
                    AnimeDownload.Status.DOWNLOADING -> {
                        LinearProgressIndicator(
                            progress = { download.progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = accentColor,
                            trackColor = KitsugiColors.SurfaceStrong
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val sizeText = if (download.totalBytes > 0) {
                            "${formatBytes(download.downloadedBytes)} / ${formatBytes(download.totalBytes)}"
                        } else {
                            formatBytes(download.downloadedBytes)
                        }
                        Text(
                            text = stringResource(R.string.inline_downloading_1_s_2_s_40c4645, sizeText, download.progress),
                            color = accentColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (speed > 0L) {
                            val detailParts = mutableListOf(
                                stringResource(R.string.downloads_per_second, formatBytes(speed))
                            )
                            if (etaMs > 0L) {
                                detailParts.add(
                                    stringResource(R.string.downloads_eta_remaining, formatDuration(etaMs))
                                )
                            }
                            if (download.downloadedSegments > 0) {
                                detailParts.add(
                                    stringResource(
                                        R.string.downloads_segments_label,
                                        download.downloadedSegments
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = detailParts.joinToString(" • "),
                                color = KitsugiColors.TextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }

                    AnimeDownload.Status.QUEUE -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.inline_queued_195de15),
                                color = KitsugiColors.TextMuted,
                                fontSize = 12.sp
                            )
                            // Kuyruk önceliği: sıradaki işi yukarı/aşağı taşı
                            if (onMoveUp != null || onMoveDown != null) {
                                QueueOrderButtons(
                                    onMoveUp = onMoveUp,
                                    onMoveDown = onMoveDown,
                                    accentColor = accentColor
                                )
                            }
                        }
                    }

                    AnimeDownload.Status.PAUSED -> {
                        Text(
                            text = buildString {
                                append(KitsugiStrings.get(R.string.duraklatildi))
                                if (download.downloadedBytes > 0L) {
                                    append(" • ")
                                    append(formatBytes(download.downloadedBytes))
                                }
                            },
                            color = KitsugiColors.TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    AnimeDownload.Status.COMPLETED -> {
                        Text(
                            text = buildString {
                                append(KitsugiStrings.get(R.string.tamamlandi))
                                if (download.downloadedBytes > 0L) {
                                    append(" • ")
                                    append(formatBytes(download.downloadedBytes))
                                }
                                if (addedDate.isNotEmpty()) {
                                    append(" • ")
                                    append(addedDate)
                                }
                            },
                            color = KitsugiColors.AccentGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    AnimeDownload.Status.ERROR -> {
                        Text(
                            text = download.errorMessage?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.inline_an_error_occurred_2cd4106),
                            color = KitsugiColors.AccentRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Eylemler
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Klasör simgesi: indirilen videonun klasörünü dosya yöneticisinde açar
                IconButton(
                    onClick = onOpenFolder,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.FolderOpen,
                        contentDescription = stringResource(R.string.klasoru_ac),
                        tint = KitsugiColors.AccentBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }

                when (download.status) {
                    AnimeDownload.Status.DOWNLOADING -> {
                        IconButton(onClick = onPause, modifier = Modifier.size(36.dp)) {
Icon(Icons.Pause, contentDescription = stringResource(R.string.downloads_pause_action), tint = KitsugiColors.TextPrimary, modifier = Modifier.size(20.dp))
                        }
                    }

                    AnimeDownload.Status.PAUSED, AnimeDownload.Status.ERROR, AnimeDownload.Status.QUEUE -> {
                        IconButton(onClick = onResume, modifier = Modifier.size(36.dp)) {
Icon(Icons.PlayArrow, contentDescription = stringResource(R.string.tv_start), tint = accentColor, modifier = Modifier.size(20.dp))
                        }
                    }

                    AnimeDownload.Status.COMPLETED -> {
                        IconButton(onClick = onPlay, modifier = Modifier.size(36.dp)) {
Icon(Icons.PlayArrow, contentDescription = stringResource(R.string.oynat), tint = KitsugiColors.AccentGreen, modifier = Modifier.size(22.dp))
                        }
                    }
                }

                KitsugiAnimatedDeleteButton(
                    onDelete = onDelete,
                    size = 36.dp
                )
            }
        }

        // Bu videoya ait altyazılar - klasör simgeleriyle birlikte
        if (localSubs.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = KitsugiColors.Border.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Subtitles,
                        contentDescription = null,
                        tint = KitsugiColors.TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = stringResource(R.string.inline_downloaded_subtitles_1_s_2e9132a, localSubs.size),
                        color = KitsugiColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                KitsugiChevronIcon(
                    expanded = isExpanded,
                    contentDescription = null,
                    tint = KitsugiColors.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(6.dp))
                localSubs.forEach { sub ->
                    val subFile = remember(sub.uri) { File(sub.uri.removePrefix("file://")) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.CheckCircle,
                            contentDescription = null,
                            tint = KitsugiColors.AccentGreen,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = sub.label,
                            color = KitsugiColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = sub.uri.substringAfterLast(".").uppercase(),
                            color = KitsugiColors.TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(KitsugiColors.SurfaceStrong, shape = RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        // Altyazı klasör simgesi: altyazının indiği konumu açar
                        IconButton(
                            onClick = { onOpenSubFolder(subFile) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.FolderOpen,
                                contentDescription = stringResource(R.string.altyazi_klasorunu_ac),
                                tint = KitsugiColors.AccentOrange,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        if (companionAudios.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = KitsugiColors.Border.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isAudiosExpanded = !isAudiosExpanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AudioLines,
                        contentDescription = null,
                        tint = KitsugiColors.AccentTeal,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = stringResource(R.string.downloads_companion_audios_count, companionAudios.size),
                        color = KitsugiColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                KitsugiChevronIcon(
                    expanded = isAudiosExpanded,
                    contentDescription = null,
                    tint = KitsugiColors.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (isAudiosExpanded) {
                Spacer(modifier = Modifier.height(6.dp))
                companionAudios.forEach { audio ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .clickable { onPlayCompanionAudio?.invoke(audio, companionAudios) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onPlayCompanionAudio?.invoke(audio, companionAudios) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.PlayArrow,
                                contentDescription = stringResource(R.string.audio_player_play),
                                tint = KitsugiColors.AccentTeal,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = audio.trackLabel ?: audio.displayTitle,
                            color = KitsugiColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = audio.formatUpper,
                            color = KitsugiColors.AccentTeal,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(KitsugiColors.SurfaceStrong, shape = RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { onOpenSubFolder(audio.folderForExplorer()) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.FolderOpen,
                                contentDescription = stringResource(R.string.klasoru_ac),
                                tint = KitsugiColors.AccentTeal,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ALTYAZI KARTI
// ─────────────────────────────────────────────────────────────────────────────

/** Kuyruktaki bir işi yukarı/aşağı taşıyan küçük düğme çifti. */
@Composable
private fun QueueOrderButtons(
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    accentColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        QueueOrderButton(
            icon = Icons.KeyboardArrowUp,
            contentDescription = stringResource(R.string.downloads_move_up),
            enabled = onMoveUp != null,
            accentColor = accentColor,
            onClick = { onMoveUp?.invoke() }
        )
        QueueOrderButton(
            icon = Icons.KeyboardArrowDown,
            contentDescription = stringResource(R.string.downloads_move_down),
            enabled = onMoveDown != null,
            accentColor = accentColor,
            onClick = { onMoveDown?.invoke() }
        )
    }
}

@Composable
private fun QueueOrderButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(KitsugiColors.SurfaceStrong.copy(alpha = if (enabled) 0.9f else 0.35f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) accentColor else KitsugiColors.TextMuted.copy(alpha = 0.5f),
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * Altyazı kartı.
 *
 * @param selectionMode Çoklu seçim modu aktif mi?
 * @param selected Kart seçili mi?
 * @param onLongClick Uzun basış: seçim modunu açar / seçimi değiştirir.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SubtitleCardItem(
    item: DownloadedSubtitleItem,
    accentColor: Color,
    onOpenFolder: () -> Unit,
    onDelete: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = { if (selectionMode) onLongClick() },
                        onLongClick = onLongClick
                    )
                } else {
                    Modifier
                }
            ),
        color = KitsugiColors.SurfaceSoft,
        shape = RoundedCornerShape(12.dp),
        border = if (selected) {
            androidx.compose.foundation.BorderStroke(1.5.dp, accentColor)
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(KitsugiColors.AccentOrange.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
if (selectionMode) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) accentColor else KitsugiColors.SurfaceStrong)
                            .border(
                                width = 1.5.dp,
                                color = if (selected) accentColor else KitsugiColors.TextMuted,
                                shape = RoundedCornerShape(999.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Check,
                                contentDescription = null,
                                tint = KitsugiColors.OnAccent,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                } else {
                    Icon(
                        imageVector = Icons.Subtitles,
                        contentDescription = null,
                        tint = KitsugiColors.AccentOrange,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = KitsugiColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Rozet: harici altyazı ya da anime bölümü
                    val badgeText = if (item.animeTitle != null) {
                        "${item.animeTitle} • Ep ${item.episode ?: 1}"
                    } else {
                        stringResource(R.string.downloads_external_subtitle)
                    }
                    Text(
                        text = badgeText,
                        color = accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )

                    // Biçim rozeti
                    Text(
                        text = item.format,
                        color = KitsugiColors.TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(KitsugiColors.SurfaceStrong, RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )

                    if (item.fileSizeBytes > 0) {
                        Text(
                            text = formatBytes(item.fileSizeBytes),
                            color = KitsugiColors.TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                val dateStr = remember(item.lastModified) {
                    if (item.lastModified > 0) {
                        SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(item.lastModified))
                    } else ""
                }
                if (dateStr.isNotEmpty()) {
                    Text(
                        text = dateStr,
                        color = KitsugiColors.TextMuted.copy(alpha = 0.8f),
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Eylemler: klasörü aç + sil
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onOpenFolder,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.FolderOpen,
                        contentDescription = stringResource(R.string.klasoru_ac),
                        tint = KitsugiColors.AccentBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }

                KitsugiAnimatedDeleteButton(
                    onDelete = onDelete,
                    size = 34.dp,
                    contentDescription = stringResource(R.string.altyaziyi_sil)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DİSK TARAMA / YARDIMCILAR
// ─────────────────────────────────────────────────────────────────────────────

private fun parseSubtitleFileInfo(nameWithoutExt: String): Triple<String?, Int?, String> {
    // Örnek: "Solo Leveling - Bölüm 3.tr" veya "Solo Leveling - Bölüm 3.tr_Türkçe"
    val epMatch = EP_SUBTITLE_REGEX.find(nameWithoutExt)
    if (epMatch != null) {
        val anime = epMatch.groupValues[1].trim()
        val ep = epMatch.groupValues[2].toIntOrNull()
        val lang = epMatch.groupValues.getOrNull(3)?.substringBefore("_")?.trim() ?: "auto"
        return Triple(anime, ep, lang)
    }

    // Örnek: "Spirited Away.tr"
    val dotMatch = DOT_SUBTITLE_REGEX.find(nameWithoutExt)
    if (dotMatch != null) {
        val anime = dotMatch.groupValues[1].trim()
        val lang = dotMatch.groupValues[2].substringBefore("_").trim()
        return Triple(anime, null, lang)
    }

    return Triple(null, null, "auto")
}

/**
 * Loads all downloaded subtitles across:
 * 1. Offline media episode downloads (<cacheDir>/Kitsugi_downloads/<mediaId>/subs/)
 * 2. Standalone subtitles downloaded via player (Downloads/Kitsugi/Subtitles/)
 * 3. App cache subtitles (<cacheDir>/subtitles/)
 */
fun loadAllDownloadedSubtitles(
    context: Context,
    downloads: List<AnimeDownload>
): List<DownloadedSubtitleItem> {
    val results = mutableListOf<DownloadedSubtitleItem>()
    val seenPaths = HashSet<String>()
    val seenNames = HashSet<String>()
    val subExtensions = setOf("srt", "ass", "ssa", "vtt", "ttml")
    val downloadsByMediaId = downloads.associateBy { "${it.animeId}_ep${it.episode}" }

    // 1. Bölüme bağlı altyazılar
    val downloadedMediaList = OfflinePlaybackHelper.listDownloadedMedia(context)
    downloadedMediaList.forEach { media ->
        val matchingDownload = downloadsByMediaId[media.mediaId]
        media.subtitles.forEach { sub ->
            val subFile = File(sub.uri.removePrefix("file://"))
            if (subFile.exists() && subFile.isFile && seenPaths.add(subFile.absolutePath)) {
                seenNames.add(subFile.name)
                results.add(
                    DownloadedSubtitleItem(
                        title = sub.label,
                        language = sub.language,
                        format = subFile.extension.uppercase(),
                        fileSizeBytes = subFile.length(),
                        lastModified = subFile.lastModified(),
                        file = subFile,
                        animeTitle = matchingDownload?.animeTitle ?: media.title,
                        episode = matchingDownload?.episode,
                        isStandalone = false
                    )
                )
            }
        }
    }

    // 2. Downloads/Kitsugi/Subtitles ve Downloads/Kitsugi/Video içindeki bağımsız altyazılar
    val publicDirsToScan = listOf(
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Kitsugi/Subtitles"),
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Kitsugi/Video")
    )
    for (publicDir in publicDirsToScan) {
        try {
            if (publicDir.exists()) {
                publicDir.listFiles()?.forEach { f ->
                    if (f.isFile && f.extension.lowercase() in subExtensions && seenPaths.add(f.absolutePath)) {
                        seenNames.add(f.name)
                        val (parsedAnime, parsedEp, parsedLang) = parseSubtitleFileInfo(f.nameWithoutExtension)
                        results.add(
                            DownloadedSubtitleItem(
                                title = f.nameWithoutExtension,
                                language = if (parsedLang.isNotBlank() && parsedLang != "auto") parsedLang else "auto",
                                format = f.extension.uppercase(),
                                fileSizeBytes = f.length(),
                                lastModified = f.lastModified(),
                                file = f,
                                animeTitle = parsedAnime,
                                episode = parsedEp,
                                isStandalone = true
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
    }

    // 3. Uygulama önbelleğindeki altyazılar
    try {
        val cacheSubDir = File(context.cacheDir, "subtitles")
        if (cacheSubDir.exists()) {
            cacheSubDir.listFiles()?.forEach { f ->
                if (f.isFile && f.extension.lowercase() in subExtensions && seenNames.add(f.name)) {
                    seenPaths.add(f.absolutePath)
                    val (parsedAnime, parsedEp, parsedLang) = parseSubtitleFileInfo(f.nameWithoutExtension)
                    results.add(
                        DownloadedSubtitleItem(
                            title = f.nameWithoutExtension,
                            language = if (parsedLang.isNotBlank() && parsedLang != "auto") parsedLang else "auto",
                            format = f.extension.uppercase(),
                            fileSizeBytes = f.length(),
                            lastModified = f.lastModified(),
                            file = f,
                            animeTitle = parsedAnime,
                            episode = parsedEp,
                            isStandalone = true
                        )
                    )
                }
            }
        }
    } catch (_: Exception) {}

    return results.sortedByDescending { it.lastModified }
}

/** Resim dosyasının piksel boyutlarını yalnızca başlığı okuyarak çözer (bellek dostu). */
private fun readImageDimensions(file: File): Pair<Int, Int> {
    return try {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        val w = options.outWidth.coerceAtLeast(0)
        val h = options.outHeight.coerceAtLeast(0)
        w to h
    } catch (_: Throwable) {
        0 to 0
    }
}

/**
 * Loads all images downloaded by [KitsugiImageDownloadHelper] from:
 * - Downloads/Kitsugi/Images (new dedicated folder)
 * - Downloads/Kitsugi (legacy flat storage)
 *
 * Piksel boyutları da çözülür: kutucuk rozetinde "1920×1080" gösterilir ve galeri
 * en-boy oranını önceden bilir (yüklenme sırasında zıplama olmaz).
 */
fun loadAllDownloadedImages(context: Context): List<DownloadedImageItem> {
    // İndirilen görseller ORİJİNAL formatıyla kaydedilir (KitsugiImageDownloadHelper
    // GIF/PNG/AVIF/HEIC dönüşümü yapmaz) → tarayıcı da aynı listeyi bilmeli, aksi
    // halde örneğin indirilmiş bir HEIC/AVIF ne İndirmeler'de görünür ne de silinebilir.
    val imageExtensions = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "avif", "heic", "heif", "bmp", "tiff"
    )
    val results = mutableListOf<DownloadedImageItem>()
    val seenPaths = HashSet<String>()
    val dirsToScan = listOfNotNull(
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Kitsugi/Images"),
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Kitsugi"),   // legacy
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Kitsugi/Images"),
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Kitsugi"),
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { File(it, "Kitsugi/Images") },
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.let { File(it, "Kitsugi/Images") }
    )
    for (dir in dirsToScan) {
        try {
            if (!dir.exists()) continue
            dir.listFiles()?.forEach { f ->
                if (f.isFile &&
                    f.name.startsWith("Kitsugi_") &&
                    f.extension.lowercase() in imageExtensions &&
                    seenPaths.add(f.absolutePath)
                ) {
                    // Title: strip "Kitsugi_" prefix and trailing timestamp+extension
                    // (birden fazla alt çizgiyi tek boşluğa indir — Unicode içerik adları düzgün görünsün)
                    val rawName = f.nameWithoutExtension.removePrefix("Kitsugi_")
                    val title = rawName
                        .replace(TRAILING_TIMESTAMP_REGEX, "")
                        .replace(MULTI_UNDERSCORE_REGEX, " ")
                        .trim()
                    val (width, height) = readImageDimensions(f)
                    results.add(
                        DownloadedImageItem(
                            file = f,
                            title = title.ifBlank { f.nameWithoutExtension },
                            fileSizeBytes = f.length(),
                            lastModified = f.lastModified(),
                            width = width,
                            height = height
                        )
                    )
                }
            }
        } catch (_: Exception) {}
    }
    return results.sortedByDescending { it.lastModified }
}

/**
 * Robust helper function that opens the folder containing a downloaded file or directory
 * in the user's external file manager app.
 */
fun openFolderInFileManager(context: Context, targetFileOrDir: File) {
    val folder = if (targetFileOrDir.isDirectory) targetFileOrDir else (targetFileOrDir.parentFile ?: targetFileOrDir)
    if (!folder.exists()) {
        folder.mkdirs()
    }

    // Copy absolute path to clipboard for quick paste in any file explorer
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(KitsugiStrings.get(R.string.klasor_konumu), folder.absolutePath)
        clipboard?.setPrimaryClip(clip)
    }

    val pm = context.packageManager
    val extraIntents = mutableListOf<Intent>()

    val oldPolicy = StrictMode.getVmPolicy()
    StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())

    try {
        val fileUri = Uri.fromFile(folder)

        // 1. Direct explicit package intents for popular file managers (ZArchiver, MiXplorer, Solid, etc.)
        val knownFileManagerPkgs = listOf(
            "ru.zdevs.zarchiver",
            "ru.zdevs.zarchiver.pro",
            "com.mixplorer",
            "com.mixplorer.silver",
            "pl.solidexplorer2",
            "com.sec.android.app.myfiles",
            "com.mi.android.globalFileexplorer",
            "com.google.android.apps.nbu.files",
            "com.estrongs.android.pop",
            "com.alphainventor.filemanager"
        )

        for (pkg in knownFileManagerPkgs) {
            val isInstalled = runCatching {
                pm.getPackageInfo(pkg, 0)
                true
            }.getOrDefault(false)

            if (isInstalled) {
                val directIntent = Intent(Intent.ACTION_VIEW).apply {
                    setPackage(pkg)
                    setDataAndType(fileUri, "resource/folder")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                extraIntents.add(directIntent)
            }
        }

        // 2. Intent with resource/folder (widely supported by Android file explorers)
        val genericFolderIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "resource/folder")
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // 3. Intent with vnd.android.cursor.dir/*
        val cursorDirIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "vnd.android.cursor.dir/*")
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        extraIntents.add(cursorDirIntent)

        // 4. FileProvider intent
        val contentUri = runCatching {
            FileProvider.getUriForFile(context, "com.kitsugi.animelist.fileprovider", folder)
        }.getOrNull()
        if (contentUri != null) {
            val fpIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            extraIntents.add(fpIntent)
        }

        // 5. Android SAF DocumentsContract intent (for system "Dosyalar" / DocumentsUI)
        val extStorage = Environment.getExternalStorageDirectory()
        if (folder.absolutePath.startsWith(extStorage.absolutePath)) {
            val relPath = folder.absolutePath.removePrefix(extStorage.absolutePath).trimStart('/', '\\')
            val encoded = Uri.encode(relPath)
            val docUri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3A$encoded")
            val safIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(docUri, "vnd.android.document/directory")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            extraIntents.add(safIntent)
        }

        // 6. DownloadManager intent if inside Downloads folder
        val publicDownloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (folder.absolutePath.startsWith(publicDownloadsDir.absolutePath)) {
            val dlIntent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            extraIntents.add(dlIntent)
        }

        val chooser = Intent.createChooser(genericFolderIntent, KitsugiStrings.get(R.string.downloads_open_folder_chooser)).apply {
            if (extraIntents.isNotEmpty()) {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, extraIntents.toTypedArray())
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        Toast.makeText(context, KitsugiStrings.get(R.string.klasor_aciliyor_x, folder.name), Toast.LENGTH_SHORT).show()
        return
    } catch (_: Exception) {}
    finally {
        StrictMode.setVmPolicy(oldPolicy)
    }

    Toast.makeText(context, KitsugiStrings.get(R.string.klasor_konumu_x, folder.absolutePath), Toast.LENGTH_LONG).show()
}

internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
