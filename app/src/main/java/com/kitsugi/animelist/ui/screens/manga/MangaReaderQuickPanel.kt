package com.kitsugi.animelist.ui.screens.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.data.manga.MangaNovelSpeechController
import com.kitsugi.animelist.data.manga.MangaReaderOptions
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

/** Frequently used actions in one predictable place; the reading surface remains uncluttered. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MangaReaderQuickPanel(
    options: MangaReaderOptions,
    hasText: Boolean,
    canShare: Boolean,
    isBookmarked: Boolean,
    speechState: MangaNovelSpeechController.State,
    onDismiss: () -> Unit,
    onToggleBookmark: () -> Unit,
    onBookmarks: () -> Unit,
    onShare: () -> Unit,
    onSearch: () -> Unit,
    onToggleSpeech: () -> Unit,
    onStopSpeech: () -> Unit,
    onPreviousSpeech: () -> Unit = {},
    onNextSpeech: () -> Unit = {},
    onSaveGallery: (() -> Unit)? = null,
    onSetWallpaper: (() -> Unit)? = null,
    onOptionsChange: (MangaReaderOptions) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF17171F)) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text("Hızlı Araçlar", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Sayfa, metin ve oturum kontrolleri", color = Color.White.copy(0.55f), fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                QuickTool(if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, "Yer imi", onToggleBookmark)
                QuickTool(Icons.AutoMirrored.Filled.MenuBook, "Yer imleri", onBookmarks)
                if (canShare) QuickTool(Icons.Filled.Share, "Paylaş", onShare)
                if (canShare && !hasText) {
                    onSaveGallery?.let { QuickTool(Icons.Filled.Download, stringResource(com.kitsugi.animelist.R.string.reader_save_gallery), it) }
                    onSetWallpaper?.let { QuickTool(Icons.Filled.Wallpaper, stringResource(com.kitsugi.animelist.R.string.reader_save_wallpaper), it) }
                }
                if (hasText) QuickTool(Icons.Filled.Search, "Bölümde ara", onSearch)
                if (hasText) QuickTool(
                    if (speechState == MangaNovelSpeechController.State.Speaking) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (speechState == MangaNovelSpeechController.State.Speaking) "Duraklat" else "Sesli oku",
                    onToggleSpeech,
                )
                if (speechState != MangaNovelSpeechController.State.Idle && speechState != MangaNovelSpeechController.State.Unavailable) {
                    QuickTool(Icons.Filled.SkipPrevious, "Önceki cümle", onPreviousSpeech)
                    QuickTool(Icons.Filled.SkipNext, "Sonraki cümle", onNextSpeech)
                    QuickTool(Icons.Filled.Stop, "Sesi durdur", onStopSpeech)
                }
            }
            QuickSwitch("Otomatik kaydır", "Okuma hızını alttaki ayarla değiştir", options.autoScroll) {
                onOptionsChange(options.copy(autoScroll = it))
            }
            if (options.autoScroll) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Hız: ${options.autoScrollSpeed} px/sn", color = Color.White.copy(0.7f), modifier = Modifier.weight(1f))
                    IconButton(onClick = { onOptionsChange(options.copy(autoScrollSpeed = (options.autoScrollSpeed - 4).coerceAtLeast(8))) }) { Text("−", color = Color.White, fontSize = 20.sp) }
                    IconButton(onClick = { onOptionsChange(options.copy(autoScrollSpeed = (options.autoScrollSpeed + 4).coerceAtMost(240))) }) { Text("+", color = Color.White, fontSize = 20.sp) }
                }
            }
            QuickSwitch("Ekranı açık tut", null, options.keepScreenOn) { onOptionsChange(options.copy(keepScreenOn = it)) }
            QuickSwitch("Gizli okuma", "İlerlemeyi bu oturumda kaydetme", options.incognito) { onOptionsChange(options.copy(incognito = it)) }
            Text("Tüm görünüm ve gezinme seçenekleri Ayarlar bölümündedir.", color = Color.White.copy(0.45f), fontSize = 11.sp, modifier = Modifier.padding(vertical = 20.dp))
        }
    }
}

@Composable
private fun QuickTool(icon: ImageVector, label: String, onClick: () -> Unit) {
    FilterChip(selected = false, onClick = onClick, label = { Text(label) }, leadingIcon = { Icon(icon, null, modifier = Modifier.padding(end = 4.dp)) })
}

@Composable
private fun QuickSwitch(title: String, description: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp)
            description?.let { Text(it, color = Color.White.copy(0.5f), fontSize = 11.sp) }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
