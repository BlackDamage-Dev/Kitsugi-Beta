package com.kitsugi.animelist.ui.screens.offline

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
import android.net.Uri
import android.os.Environment
import android.os.StrictMode
import android.widget.Toast
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import com.kitsugi.animelist.core.player.OfflinePlaybackHelper
import com.kitsugi.animelist.core.storage.DownloadStorage
import com.kitsugi.animelist.data.local.AnimeDownloadManager
import com.kitsugi.animelist.data.model.AnimeDownload
import com.kitsugi.animelist.data.remote.GalleryCategory
import com.kitsugi.animelist.data.remote.GalleryItem
import com.kitsugi.animelist.ui.components.KitsugiImageGalleryDialog
import com.kitsugi.animelist.ui.components.KitsugiSettingsSegmentedTabs
import com.kitsugi.animelist.ui.components.KitsugiSettingsTabOption
import com.kitsugi.animelist.ui.components.KitsugiAnimatedDeleteButton
import com.kitsugi.animelist.ui.screens.fullscreen.KitsugiFullscreenPlayerActivity
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.utils.KitsugiImageDownloadHelper
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import kotlinx.coroutines.Dispatchers
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
private fun normalizeImageGroupKey(title: String): String =
    title.trim().lowercase(Locale.ROOT).replace(WHITESPACE_REGEX, " ")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    val downloads by AnimeDownloadManager.downloads.collectAsState()

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val scope = rememberCoroutineScope()
    var refreshSubsTrigger by remember { mutableIntStateOf(0) }
    var refreshImagesTrigger by remember { mutableIntStateOf(0) }

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

    // ── SİLME: görseller (tekli kart + grup) ──────────────────────────────────
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KitsugiColors.Background)
            .statusBarsPadding()
    ) {
        // Top Bar
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

            // İndirme klasörünü aç → ortak üst çubuk aksiyon butonu
            KitsugiTopActionButton(
                action = KitsugiTopAction.FOLDER,
                onClick = {
                    val rootDir = when (pagerState.currentPage) {
                        1 -> File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            "Kitsugi/Subtitles"
                        ).also { it.mkdirs() }
                        2 -> File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            "Kitsugi/Images"
                        ).also { it.mkdirs() }
                        else -> File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            "Kitsugi/Video"
                        ).also { it.mkdirs() }
                    }
                    openFolderInFileManager(context, rootDir)
                },
                size = KitsugiTopActionTokens.CompactButtonSize,
                cornerRadius = KitsugiTopActionTokens.CompactCornerRadius
            )
        }

        // Sekme bandı: Videolar | Altyazılar | Resimler — "Görünüm & Tercihler" bandıyla aynı
        // (ekrana sığmazsa bandın kendi içinde yatay kaydırılır, ekrandan taşmaz)
        KitsugiSettingsSegmentedTabs(
            tabs = listOf(
                KitsugiSettingsTabOption(
                    title = stringResource(R.string.downloads_videos_count, downloads.size),
                    icon = Icons.VideoLibrary
                ),
                KitsugiSettingsTabOption(
                    title = stringResource(R.string.inline_subtitles_1_s_b81ee5c, allSubtitles.size),
                    icon = Icons.Subtitles
                ),
                KitsugiSettingsTabOption(
                    title = stringResource(R.string.downloads_images_count, allImages.size),
                    icon = Icons.Image
                )
            ),
            selectedIndex = pagerState.currentPage,
            accentColor = accentColor,
            onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        // Content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            when (page) {
                0 -> {
                    // ── VIDEOLAR TAB ──────────────────────────────────────────────
                    if (downloads.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.DownloadForOffline,
                            contentDescription = null,
                            tint = KitsugiColors.TextMuted,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.inline_no_videos_have_been_downloaded_89e49a0),
                            color = KitsugiColors.TextMuted,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(downloads, key = { "${it.animeId}_${it.episode}" }) { download ->
                        DownloadItemRow(
                            download = download,
                            accentColor = accentColor,
                            onPlay = {
                                scope.launch {
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
                                            subtitles = params.subtitles
                                        )
                                    } else {
                                        download.localPath?.let { path ->
                                            val companionSubs = withContext(Dispatchers.IO) {
                                                val videoFile = File(path)
                                                val parentDir = videoFile.parentFile
                                                val baseName = videoFile.nameWithoutExtension
                                                if (parentDir != null && parentDir.exists()) {
                                                    parentDir.listFiles()?.filter { f ->
                                                        f.isFile && f.name.startsWith("$baseName.") && f.extension.lowercase() in COMPANION_SUB_EXTS
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
                                                title = KitsugiStrings.get(R.string.title_bolum_episode, download.animeTitle, download.episode),
                                                headers = emptyMap(),
                                                subtitles = companionSubs
                                            )
                                        }
                                    }
                                }
                            },
                            onPause = {
                                AnimeDownloadManager.pauseDownload(download.animeId, download.episode)
                            },
                            onResume = {
                                AnimeDownloadManager.resumeDownload(context, download.animeId, download.episode)
                            },
                            onDelete = {
                                AnimeDownloadManager.deleteDownload(context, download.animeId, download.episode)
                                refreshSubsTrigger++
                            },
                            onOpenFolder = {
                                val safeAnimeId = resolveSafeAnimeId(download.animeId, download.animeTitle)
                                val mediaId = "${safeAnimeId}_ep${download.episode}"
                                val targetDir = download.localPath?.let { File(it).parentFile }
                                    ?: File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Kitsugi/Video").takeIf { it.exists() }
                                    ?: File(OfflinePlaybackHelper.getDownloadsDir(context), mediaId)
                                openFolderInFileManager(context, targetDir)
                            },
                            onOpenSubFolder = { subFile ->
                                openFolderInFileManager(context, subFile.parentFile ?: subFile)
                            }
                        )
                    }
                }
            }
        }
        1 -> {
            // ── ALTYAZILAR TAB ───────────────────────────────────────────
            Column(modifier = Modifier.fillMaxSize()) {
                if (allSubtitles.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Subtitles,
                                contentDescription = null,
                                tint = KitsugiColors.TextMuted,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.inline_no_downloaded_subtitles_found_yet_160c9c9),
                                color = KitsugiColors.TextMuted,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.inline_subtitles_downloaded_from_the_play_3fcd255),
                                color = KitsugiColors.TextMuted.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(allSubtitles, key = { it.file.absolutePath }) { subItem ->
                            SubtitleCardItem(
                                item = subItem,
                                accentColor = accentColor,
                                onOpenFolder = {
                                    openFolderInFileManager(context, subItem.file.parentFile ?: subItem.file)
                                },
                                onDelete = { deleteSubtitleItem(subItem) }
                            )
                        }
                    }
                }
            }
        }
        2 -> {
            // ── RESİMLER TAB ──────────────────────────────────────────────
                    DownloadedImagesTab(
                        images = allImages,
                        accentColor = accentColor,
                        onRefresh = { refreshImagesTrigger++ },
                        onDeleteImages = deleteImageFiles
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DOWNLOADED IMAGES TAB
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun DownloadedImagesTab(
    images: List<DownloadedImageItem>,
    accentColor: Color,
    onRefresh: () -> Unit,
    onDeleteImages: (List<File>) -> Unit = {}
) {
    val context = LocalContext.current
    var galleryGroup by remember { mutableStateOf<DownloadedImageGroup?>(null) }
    var galleryInitialIndex by remember { mutableIntStateOf(0) }
    // Grup silme onayı (yanlışlıkla 15-20 resmi tek dokunuşta silmeyi önler).
    var groupPendingDelete by remember { mutableStateOf<DownloadedImageGroup?>(null) }

    // İndirilen resimler içerik adına (anime / dizi / film) göre gruplanır.
    // Gruplar en yeni indirmenin tarihine göre sıralanır (arka planda hesaplanır).
    val groups by produceState(initialValue = emptyList<DownloadedImageGroup>(), key1 = images) {
        if (images.isEmpty()) {
            value = emptyList()
        } else {
            value = withContext(Dispatchers.Default) {
                images
                    .groupBy { normalizeImageGroupKey(it.title) }
                    .map { (_, list) ->
                        DownloadedImageGroup(
                            displayName = list.maxByOrNull { it.lastModified }?.title?.trim()
                                ?.ifBlank { KitsugiStrings.get(R.string.downloads_unknown_content) } ?: KitsugiStrings.get(R.string.downloads_unknown_content),
                            items = list.sortedByDescending { it.lastModified }
                        )
                    }
                    .sortedByDescending { g -> g.items.maxOfOrNull { it.lastModified } ?: 0L }
            }
        }
    }

    // Açılan galeri: yalnızca ilgili grubun resimleri gösterilir.
    // allowDownload=false → indirme butonu gösterilmez (içerik zaten cihazda);
    // karşılığında galerideki SİL butonu aktif olur — indirilen kopya doğrudan
    // buradan kaldırılabilir (İndirmeler ekranına geri dönmeden).
    val activeGroup = galleryGroup
    var deletedPaths by remember { mutableStateOf(setOf<String>()) }
    val visibleGalleryItems = remember(activeGroup, deletedPaths) {
        activeGroup?.items?.filterNot { img ->
            ("file://" + img.file.absolutePath) in deletedPaths
        } ?: emptyList()
    }
    // Grubun tüm resimleri silindiyse galeriyi kapat.
    if (activeGroup != null && visibleGalleryItems.isEmpty() && deletedPaths.isNotEmpty()) {
        LaunchedEffect(Unit) {
            galleryGroup = null
            deletedPaths = emptySet()
        }
    }
    if (activeGroup != null && visibleGalleryItems.isNotEmpty()) {
        val galleryItems = remember(visibleGalleryItems) {
            visibleGalleryItems.map { img ->
                GalleryItem(
                    url = "file://${img.file.absolutePath}",
                    source = "Kitsugi",
                    category = GalleryCategory.OTHER,
                    description = img.title,
                    width = img.width.takeIf { it > 0 },
                    height = img.height.takeIf { it > 0 }
                )
            }
        }
        KitsugiImageGalleryDialog(
            galleryItems = galleryItems,
            initialIndex = galleryInitialIndex.coerceIn(0, galleryItems.size - 1),
            title = activeGroup.displayName,
            allowDownload = false,
            allowDelete = true,
            onItemsDeleted = { urls ->
                deletedPaths = deletedPaths + urls.toSet()
                onRefresh()
            },
            onDismiss = {
                galleryGroup = null
                deletedPaths = emptySet()
            }
        )
    }

    if (images.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.ImageNotSupported,
                    contentDescription = null,
                    tint = KitsugiColors.TextMuted,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.inline_no_downloaded_images_found_yet_02e4efb),
                    color = KitsugiColors.TextMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.inline_images_downloaded_from_the_gallery_af98e85),
                    color = KitsugiColors.TextMuted.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            groups.forEach { group ->
                // ── Grup başlığı: içerik adı + resim sayısı ──
                item(key = "group_header_${group.displayName}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 2.dp),
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
                        Text(
                            text = group.displayName,
                            color = KitsugiColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(KitsugiColors.SurfaceStrong)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = KitsugiStrings.get(R.string.downloads_image_items_count, group.items.size),
                                color = KitsugiColors.TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        // Grup bazlı silme: onay penceresi çıkar, onaylanırsa bu içeriğin
                        // indirilmiş TÜM resimleri silinir (tek yerde: deleteImageFiles).
                        KitsugiAnimatedDeleteButton(
                            onDelete = { groupPendingDelete = group },
                            size = 26.dp,
                            expandedWidth = 78.dp,
                            contentDescription = stringResource(R.string.bu_icerigin_tum_indirilen_resimlerini_sil)
                        )
                    }
                }
                // ── Grup resimleri (LazyColumn'da satır bazlı tembel yükleme: 269+ resimde tek FlowRow donmasını önler) ──
                val chunkedItems = group.items.chunked(4)
                chunkedItems.forEachIndexed { chunkIndex, chunk ->
                    item(key = "group_grid_${group.displayName}_$chunkIndex") {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            chunk.forEachIndexed { inChunkIndex, img ->
                                val globalIndex = chunkIndex * 4 + inChunkIndex
                                DownloadedImageCard(
                                    item = img,
                                    accentColor = accentColor,
                                    modifier = Modifier.width(160.dp),
                                    onClick = {
                                        galleryInitialIndex = globalIndex
                                        galleryGroup = group
                                    },
                                    onOpenFolder = {
                                        openFolderInFileManager(context, img.file.parentFile ?: img.file)
                                    },
                                    onDelete = {
                                        // Tekli silme de aynı sağlam akıştan geçer: Scoped Storage'da
                                        // `File.delete()` başarısız olduğunda MediaStore kaydı üzerinden
                                        // silinir ve kullanıcıya sonuç bildirilir.
                                        onDeleteImages(listOf(img.file))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Grup silme onayı ─────────────────────────────────────────────────────
    groupPendingDelete?.let { group ->
        AlertDialog(
            onDismissRequest = { groupPendingDelete = null },
            containerColor = KitsugiColors.SurfaceSoft,
            title = {
                Text(
                    text = stringResource(R.string.downloads_delete_group_title),
                    color = KitsugiColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.downloads_delete_group_message, group.items.size),
                    color = KitsugiColors.TextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val files = group.items.map { it.file }
                    groupPendingDelete = null
                    galleryGroup = null
                    deletedPaths = emptySet()
                    onDeleteImages(files)
                }) {
                    Text(stringResource(R.string.action_delete), color = KitsugiColors.AccentRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { groupPendingDelete = null }) {
                    Text(stringResource(R.string.vazgec), color = KitsugiColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
fun DownloadedImageCard(
    item: DownloadedImageItem,
    accentColor: Color,
    onClick: () -> Unit,
    onOpenFolder: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateStr = remember(item.lastModified) {
        if (item.lastModified > 0)
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(item.lastModified))
        else ""
    }
    val sizeStr = formatBytes(item.fileSizeBytes)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(KitsugiColors.SurfaceSoft)
            .border(1.dp, KitsugiColors.Border.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Column {
            // Image thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                AsyncImage(
                    model = item.file,
                    contentDescription = item.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
                    contentScale = ContentScale.Crop
                )
                // Gradient overlay at bottom of thumb
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, KitsugiColors.SurfaceSoft)
                            )
                        )
                )
                // Size badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(KitsugiColors.SurfaceStrong.copy(alpha = 0.82f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = sizeStr,
                        color = KitsugiColors.TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Info row
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = item.title,
                    color = KitsugiColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
                if (dateStr.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dateStr,
                        color = KitsugiColors.TextMuted,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Open folder
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentColor.copy(alpha = 0.12f))
                            .clickable(onClick = onOpenFolder),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.FolderOpen,
                            contentDescription = stringResource(R.string.klasoru_ac),
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    // Delete (Animated Uiverse)
                    KitsugiAnimatedDeleteButton(
                        onDelete = onDelete,
                        size = 30.dp
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadItemRow(
    download: AnimeDownload,
    accentColor: Color,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
    onOpenFolder: () -> Unit,
    onOpenSubFolder: (File) -> Unit
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(true) }

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
        if (download.status != AnimeDownload.Status.COMPLETED && download.localPath.isNullOrBlank()) {
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(KitsugiColors.SurfaceSoft, shape = RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Anime Poster
            AsyncImage(
                model = download.posterUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(width = 60.dp, height = 90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(KitsugiColors.SurfaceStrong),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Info & Progress
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = download.animeTitle,
                    color = KitsugiColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!download.streamName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.inline_stream_1_s_038cf7a, download.streamName),
                        color = accentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!download.streamTitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = download.streamTitle,
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
                        text = stringResource(R.string.inline_episode_1_s_2_s_32608a4, download.episode, download.quality),
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
                    }
                    AnimeDownload.Status.QUEUE -> {
                        Text(
                            text = stringResource(R.string.inline_queued_195de15),
                            color = KitsugiColors.TextMuted,
                            fontSize = 12.sp
                        )
                    }
                    AnimeDownload.Status.PAUSED -> {
                        Text(
                            text = stringResource(R.string.duraklatildi),
                            color = KitsugiColors.TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    AnimeDownload.Status.COMPLETED -> {
                        Text(
                            text = stringResource(R.string.tamamlandi),
                            color = KitsugiColors.AccentGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    AnimeDownload.Status.ERROR -> {
                        Text(
                            text = stringResource(R.string.inline_an_error_occurred_2cd4106),
                            color = KitsugiColors.AccentRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Klasör simgesi: İndirilen videonun klasörünü dosya yöneticisinde açar
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

        // Subtitles belonging to this video - fully visible with folder icons
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
                        // Altyazı Klasör Simgesi: Altyazının indiği konumu açar
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
    }
}

@Composable
fun SubtitleCardItem(
    item: DownloadedSubtitleItem,
    accentColor: Color,
    onOpenFolder: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = KitsugiColors.SurfaceSoft,
        shape = RoundedCornerShape(12.dp)
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
                Icon(
                    imageVector = Icons.Subtitles,
                    contentDescription = null,
                    tint = KitsugiColors.AccentOrange,
                    modifier = Modifier.size(22.dp)
                )
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
                    // Badge: Harici vs Anime Bölümü
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

                    // Format Badge
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

            // Action Buttons: Klasör Simgesi + Sil
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

    // 1. Episode-bound subtitles
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

    // 2. Standalone & companion subtitles in public Downloads/Kitsugi/Subtitles and Downloads/Kitsugi/Video
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

    // 3. App internal cache subtitles
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

/**
 * Loads all images downloaded by [KitsugiImageDownloadHelper] from:
 * - Downloads/Kitsugi/Images (new dedicated folder)
 * - Downloads/Kitsugi (legacy flat storage)
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
                    results.add(
                        DownloadedImageItem(
                            file = f,
                            title = title.ifBlank { f.nameWithoutExtension },
                            fileSizeBytes = f.length(),
                            lastModified = f.lastModified()
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

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
