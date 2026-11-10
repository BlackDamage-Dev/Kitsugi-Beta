package com.kitsugi.animelist.ui.screens.offline

import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.manga.ChapterRecognition
import com.kitsugi.animelist.data.manga.download.*
import com.kitsugi.animelist.ui.screens.manga.components.rememberMangaDownloadPermissionGate
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Aynı serinin **tek bir kaynaktan** indirilmiş bölümleri.
 *
 * ## Neden kaynak + manga URL'si anahtar?
 * Aynı manga birden fazla kaynakta (Manga-TR, TRManga, Mihon eklentileri…) bulunabilir.
 * Kullanıcı hangi bölümü hangi kaynaktan indirdiğini kaybetmemeli; bu yüzden kaynak
 * rozeti grup başlığında **sabit** kalır, çevirmen (scanlator) ise bölüm bazında değişir.
 *
 * ## Neden çevirmen grup anahtarına dahil DEĞİL?
 * Aynı kaynak aynı bölümü çevirmen farkıyla birden fazla kez yayınlayabilir. Bölümler
 * ayrı ayrı listelendiği ve her satır kendi çevirmen rozetini taşıdığı için, aynı bölüm
 * numarası iki farklı çevirmenden inse bile satırlar karışmaz.
 */
private data class MangaOfflineGroup(
    val sourceKey: String,
    val sourceName: String,
    val sourceLang: String,
    val mangaUrl: String,
    val mangaTitle: String,
    val records: List<MangaDownload>
) {
    /** Tamamı indirilmiş (çevrimdışı hazır) bölüm sayısı. */
    val readyChapters: Int get() = records.count { it.status == MangaDownloadStatus.Complete }

    val sizeBytes: Long get() = records.sumOf { it.sizeBytes.coerceAtLeast(0L) }

    /** Bu seride indirilmiş bölümlerin çevirmenleri — tekrarsız, indirme sırasıyla. */
    val scanlators: List<String> get() = records
        .map { it.scanlator?.trim().orEmpty() }
        .filter { it.isNotEmpty() }
        .distinct()

    /** Bir bölümün bile çevirmen bilgisi yoksa rozet "Bilinmeyen" olarak çizilir. */
    val hasScanlatorInfo: Boolean get() = scanlators.isNotEmpty()

    /**
     * Kapak: serinin **ilk sayfası**. En küçük bölüm numarasından başlanır; ilk bölüm
     * hâlâ iniyorsa sıradaki tamamlanmış bölümün ilk sayfası kapak olur.
     */
    val coverUri: String? get() = records
        .sortedBy { it.sortKey }
        .firstNotNullOfOrNull { it.firstPageUri }
}

/** Bölüm sıralaması: başlıktan çözülen numara; çözülemezse en sona. */
private val MangaDownload.sortKey: Float
    get() = ChapterRecognition
        .parseChapterNumber(mangaTitle, chapterName, chapterNumber.toDouble())
        .toFloat()
        .takeIf { it.isFinite() && it >= 0f } ?: Float.MAX_VALUE

/** Bir bölümün kapak/küçük resim kaynağı: sıralı ilk **tamamlanmış** sayfa. */
private val MangaDownload.firstPageUri: String?
    get() = pages.sortedBy { it.index }.firstOrNull { it.complete }?.uri

/**
 * Kaynak rozetleri için sabit palet.
 *
 * Renk kaynak adının karmasından türetilir → aynı kaynak her zaman aynı rengi alır ve
 * kullanıcı "şu rozet Manga-TR" diye rengi tanır. Tema vurgusu çevirmen rozetinde
 * kullanılır, böylece iki rozet türü karışmaz.
 */
private val MangaSourcePalette = listOf(
    Color(0xFF60A5FA), // mavi
    Color(0xFFFB7185), // gül
    Color(0xFF34D399), // yeşil
    Color(0xFFFB923C), // turuncu
    Color(0xFF9D7CFF), // mor
    Color(0xFF2DD4BF), // turkuaz
    Color(0xFFFBBF24), // sarı
    Color(0xFF818CF8)  // indigo
)

@Composable
private fun mangaSourceColor(sourceName: String): Color {
    val palette = remember { MangaSourcePalette }
    val key = sourceName.trim().lowercase(Locale.ROOT)
    // `absoluteValue` Int.MIN_VALUE'da negatif kalır; mod alma iki adımda yapılır.
    val slot = ((key.hashCode() % palette.size) + palette.size) % palette.size
    return palette[slot]
}

@Composable
fun MangaDownloadsTab(
    downloads: List<MangaDownload>,
    onOpen: (MangaDownload) -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    val context = LocalContext.current
    val manager = remember { MangaDownloadManager.get(context) }
    val scope = rememberCoroutineScope()
    val permissionGate = rememberMangaDownloadPermissionGate()
    val accent = LocalKitsugiAccent.current
    var deleting by remember { mutableStateOf<MangaDownload?>(null) }
    var busy by remember { mutableStateOf(setOf<String>()) }

    val groups = remember(downloads) {
        downloads.groupBy { listOf(it.sourceKey, it.sourceName, it.sourceLang, it.mangaUrl) }
            .map { (key, records) ->
                MangaOfflineGroup(
                    sourceKey = key[0],
                    sourceName = key[1],
                    sourceLang = key[2],
                    mangaUrl = key[3],
                    mangaTitle = records.first().mangaTitle,
                    records = records
                )
            }
            .sortedBy { it.mangaTitle.lowercase(Locale.ROOT) }
    }
    // Bölüm sırası tek yerde hesaplanır: LazyColumn içinde `remember` çağrılamaz.
    // Aynı bölüm numarası farklı çevirmenlerden indirildiyse satırlar çevirmene göre de
    // sıralanır: aynı numaralı iki bölüm alt alta durur, rozetlerden ayırt edilir.
    val sections = remember(groups) {
        groups.map { group ->
            group to group.records.sortedWith(
                compareBy({ it.sortKey }, { it.scanlator?.trim()?.lowercase(Locale.ROOT).orEmpty() })
            )
        }
    }

    fun perform(record: MangaDownload, action: suspend () -> Unit) {
        if (record.id in busy) return
        busy = busy + record.id
        scope.launch {
            try { action() }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                Toast.makeText(context, e.message ?: context.getString(R.string.manga_download_failed), Toast.LENGTH_LONG).show()
            } finally { busy = busy - record.id }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "manga_download_summary") {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp)) {
                Text(stringResource(R.string.manga_download_storage_summary, downloads.size,
                    String.format(Locale.ROOT, "%.1f", downloads.sumOf { it.sizeBytes } / (1024.0 * 1024))),
                    fontWeight = FontWeight.Bold, color = accent)
                Text(stringResource(R.string.manga_download_folder_hint), fontSize = 12.sp, color = KitsugiColors.TextMuted)
            }
        }
        if (downloads.isEmpty()) item(key = "manga_empty") {
            Column(Modifier.fillMaxWidth().padding(vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null, modifier = Modifier.size(56.dp), tint = accent)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.manga_download_empty), color = KitsugiColors.TextSecondary)
            }
        }
        sections.forEach { (group, chapters) ->
            item(key = "manga_group_${group.sourceKey}_${group.sourceName}_${group.sourceLang}_${group.mangaUrl}") {
                MangaOfflineGroupHeader(group = group, accent = accent)
            }
            items(chapters, key = { it.id }) { record ->
                MangaOfflineChapterCard(
                    record = record,
                    accent = accent,
                    busy = record.id in busy,
                    onRead = { onOpen(record) },
                    onOpenFolder = { openMangaDownloadFolder(context, record.relativePath) },
                    onToggle = {
                        val active = record.status in setOf(MangaDownloadStatus.Downloading, MangaDownloadStatus.Queued)
                        if (active) perform(record) { manager.pause(record.id) }
                        else permissionGate { perform(record) { manager.resume(record.id) } }
                    },
                    onDelete = { deleting = record }
                )
            }
        }
    }

    deleting?.let { record ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.manga_download_delete)) },
            text = { Text(stringResource(R.string.manga_download_delete_confirm, record.mangaTitle, record.chapterName)) },
            confirmButton = { TextButton(onClick = {
                deleting = null
                perform(record) { manager.delete(record.id) }
            }) { Text(stringResource(R.string.manga_download_delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(android.R.string.cancel)) } }
        )
    }
}

/**
 * Seri başlığı ("bar"): kapak görseli + seri adı + **kaynak / dil / çevirmen** rozetleri.
 *
 * Kapak, serinin ilk indirilen sayfasından gelir ([MangaOfflineGroup.coverUri]); böylece
 * listede hangi serinin neye ait olduğu tek bakışta anlaşılır.
 */
@Composable
private fun MangaOfflineGroupHeader(group: MangaOfflineGroup, accent: Color) {
    val sourceColor = mangaSourceColor(group.sourceName)
    val unknownStr = stringResource(R.string.manga_scanlator_unknown)
    val sourceLabel = stringResource(R.string.manga_download_source_badge)
    val scanlatorLabel = stringResource(R.string.manga_download_scanlator_badge)
    val sizeMb = String.format(Locale.ROOT, "%.1f", group.sizeBytes / (1024.0 * 1024))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MangaOfflineCover(
            uri = group.coverUri,
            accent = accent,
            modifier = Modifier.width(52.dp).height(70.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = group.mangaTitle,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = KitsugiColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MangaOfflineTag(
                    text = group.sourceName,
                    tint = sourceColor,
                    icon = Icons.Rounded.Puzzle,
                    maxWidth = 96.dp,
                    a11yLabel = sourceLabel
                )
                if (group.sourceLang.isNotBlank()) {
                    MangaOfflineTag(
                        text = group.sourceLang.uppercase(Locale.ROOT),
                        tint = KitsugiColors.TextSecondary,
                        icon = Icons.Rounded.Languages,
                        maxWidth = 44.dp
                    )
                }
                if (group.hasScanlatorInfo) {
                    // Dar ekranlarda rozet satırı taşmasın: en fazla bir çevirmen adı,
                    // kalanı "+N". Bölüm satırları zaten çevirmeni tek tek gösterir.
                    val firstScanlator = group.scanlators.first()
                    MangaOfflineTag(
                        text = firstScanlator,
                        tint = accent,
                        icon = Icons.Rounded.Mic,
                        maxWidth = 88.dp,
                        a11yLabel = scanlatorLabel
                    )
                    val overflow = group.scanlators.size - 1
                    if (overflow > 0) {
                        MangaOfflineTag(text = "+$overflow", tint = accent, maxWidth = 32.dp)
                    }
                } else {
                    MangaOfflineTag(
                        text = unknownStr,
                        tint = KitsugiColors.TextMuted,
                        icon = Icons.Rounded.Mic,
                        maxWidth = 72.dp,
                        a11yLabel = scanlatorLabel
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            val baseSummary = stringResource(R.string.manga_download_storage_summary, group.records.size, sizeMb)
            val readySummary = if (group.readyChapters < group.records.size) {
                stringResource(R.string.manga_download_group_ready_suffix, group.readyChapters, group.records.size)
            } else null
            val summary = if (readySummary != null) "$baseSummary · $readySummary" else baseSummary
            Text(
                text = summary,
                fontSize = 11.5.sp,
                color = KitsugiColors.TextSecondary
            )
        }
    }
}

/**
 * Bölüm kartı: küçük kapak (bölümün ilk sayfası), bölüm adı, **kaynak + çevirmen**
 * rozetleri (aynı bölüm farklı çevirmenden inse bile ayırt edilir) ve eylemler.
 */
@Composable
private fun MangaOfflineChapterCard(
    record: MangaDownload,
    accent: Color,
    busy: Boolean,
    onRead: () -> Unit,
    onOpenFolder: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val sourceColor = mangaSourceColor(record.sourceName)
    val shape = RoundedCornerShape(16.dp)
    val active = record.status in setOf(MangaDownloadStatus.Downloading, MangaDownloadStatus.Queued)
    val unknownScanlator = stringResource(R.string.manga_scanlator_unknown)
    val sourceLabel = stringResource(R.string.manga_download_source_badge)
    val scanlatorLabel = stringResource(R.string.manga_download_scanlator_badge)

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.outlinedCardColors(containerColor = KitsugiColors.Surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (record.status == MangaDownloadStatus.Complete) accent.copy(alpha = 0.22f) else KitsugiColors.Border.copy(alpha = 0.4f)
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                MangaOfflineCover(
                    uri = record.firstPageUri,
                    accent = accent,
                    modifier = Modifier.width(44.dp).height(60.dp),
                    corner = 8.dp
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = record.chapterName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5.sp,
                        color = KitsugiColors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MangaOfflineTag(
                            text = record.sourceName,
                            tint = sourceColor,
                            icon = Icons.Rounded.Puzzle,
                            maxWidth = 88.dp,
                            a11yLabel = sourceLabel
                        )
                        val scanlator = record.scanlator?.trim().orEmpty()
                        MangaOfflineTag(
                            text = scanlator.ifEmpty { unknownScanlator },
                            tint = if (scanlator.isEmpty()) KitsugiColors.TextMuted else accent,
                            icon = Icons.Rounded.Mic,
                            maxWidth = 96.dp,
                            a11yLabel = scanlatorLabel
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    val statusLabel = record.status.label()
                    Text(
                        text = "$statusLabel · ${record.completedPages}/${record.totalPages}",
                        fontSize = 11.5.sp,
                        color = if (record.status == MangaDownloadStatus.Complete) accent else KitsugiColors.TextSecondary,
                        fontWeight = if (record.status == MangaDownloadStatus.Complete) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }

            if (record.totalPages > 0) {
                LinearProgressIndicator(
                    progress = { (record.completedPages.toFloat() / record.totalPages).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = accent,
                    trackColor = KitsugiColors.SurfaceStrong.copy(alpha = 0.55f)
                )
            }

            record.error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (record.status == MangaDownloadStatus.Complete) {
                    TextButton(onClick = onRead, enabled = !busy) {
                        Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.manga_download_read_offline))
                    }
                }
                IconButton(onClick = onOpenFolder) {
                    Icon(Icons.Rounded.FolderOpen, stringResource(R.string.manga_download_open_folder), tint = accent)
                }
                IconButton(enabled = !busy, onClick = onToggle) {
                    Icon(if (active) Icons.Rounded.Pause else Icons.Rounded.Refresh,
                        stringResource(if (active) R.string.manga_download_pause else R.string.manga_download_resume), tint = accent)
                }
                IconButton(onClick = onDelete, enabled = !busy) {
                    Icon(Icons.Rounded.DeleteOutline, stringResource(R.string.manga_download_delete), tint = KitsugiColors.TextSecondary)
                }
            }
        }
    }
}

/**
 * Minik bilgi rozeti (kaynak · dil · çevirmen).
 *
 * 18 dp yüksekliğinde, 6 dp köşeli; başlık satırını şişirmeden üç bilgiyi yan yana taşır.
 */
@Composable
private fun MangaOfflineTag(
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    maxWidth: Dp = 120.dp,
    /** Ekran okuyucu etiketi (ör. "Kaynak: Manga-TR"); null ise yalnız metin okunur. */
    a11yLabel: String? = null
) {
    val shape = RoundedCornerShape(6.dp)
    // Ekran okuyucu rozeti tek parça okur: "Kaynak: Manga-TR" / "Çevirmen: Şımarık Fansub".
    val a11yModifier = a11yLabel?.let { label ->
        Modifier.clearAndSetSemantics { contentDescription = "$label: $text" }
    } ?: Modifier
    Row(
        modifier = modifier
            .then(a11yModifier)
            .clip(shape)
            .background(tint.copy(alpha = 0.14f))
            .border(0.75.dp, tint.copy(alpha = 0.32f), shape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(10.dp))
            Spacer(Modifier.width(3.dp))
        }
        Text(
            text = text,
            color = tint,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = maxWidth)
        )
    }
}

/** Kapak/küçük resim: dosya erişilemezse yer tutucu ikon gösterir (boş kutu kalmaz). */
@Composable
private fun MangaOfflineCover(
    uri: String?,
    accent: Color,
    modifier: Modifier = Modifier,
    corner: Dp = 10.dp
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .clip(shape)
            .background(KitsugiColors.SurfaceSoft)
            .border(1.dp, KitsugiColors.Border.copy(alpha = 0.45f), shape),
        contentAlignment = Alignment.Center
    ) {
        if (!uri.isNullOrBlank()) {
            AsyncImage(
                model = uri,
                contentDescription = stringResource(R.string.manga_download_cover),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.AutoMirrored.Rounded.MenuBook,
                contentDescription = null,
                tint = accent.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun MangaDownloadStatus.label() = stringResource(when (this) {
    MangaDownloadStatus.Queued -> R.string.manga_download_status_queued
    MangaDownloadStatus.Downloading -> R.string.manga_download_status_downloading
    MangaDownloadStatus.Paused -> R.string.manga_download_status_paused
    MangaDownloadStatus.Error -> R.string.manga_download_status_error
    MangaDownloadStatus.Complete -> R.string.manga_download_status_complete
})

/** Use a documents URI, never relax StrictMode or expose file:// paths to other apps. */
fun openMangaDownloadFolder(context: Context, relativePath: String = "Pictures/Kitsugi/Manga/") {
    val uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:${relativePath.trimEnd('/')}")
    val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { context.startActivity(view) }
    catch (_: Exception) {
        try {
            context.startActivity(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { Toast.makeText(context, relativePath, Toast.LENGTH_LONG).show() }
    }
}
