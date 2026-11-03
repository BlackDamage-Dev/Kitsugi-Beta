package com.kitsugi.animelist.ui.screens.fullscreen.controls.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.kitsugi.animelist.ui.components.KitsugiEmojiLabel
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.Checkbox
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.core.player.SubtitleInput
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

data class SubtitleTrackInfo(
    val id: Int,
    val label: String,
    val language: String? = null,
)

@Composable
fun SubtitleTracksSheet(
    tracks: List<SubtitleTrackInfo>,
    selectedIndices: List<Int>,
    onSelect: (Int) -> Unit,
    selectedSecondaryIndex: Int = -1,
    supportsSecondary: Boolean = false,
    onSelectSecondary: (Int) -> Unit = {},
    onAddSubtitleFile: () -> Unit,
    onOpenSubtitleSettings: () -> Unit,
    onOpenSubtitleDelay: () -> Unit,
    onDismissRequest: () -> Unit,
    /** Oynatıcıdaki mevcut SubtitleInput listesi — indirme butonları için eşleştirmede kullanılır. */
    subtitleInputs: List<SubtitleInput> = emptyList(),
    /** Kullanıcı bir altyazıyı indirmek istediğinde çağrılır. */
    onDownloadSubtitle: (SubtitleInput) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val allTracks = androidx.compose.runtime.remember(tracks) {
        listOf(SubtitleTrackInfo(id = -99, label = KitsugiStrings.get(R.string.altyaziyi_kapat))) + tracks
    }
    val allSelected = androidx.compose.runtime.remember(selectedIndices) {
        if (selectedIndices.isEmpty()) listOf(-99) else selectedIndices
    }

    GenericTracksSheet(
        tracks = allTracks,
        onDismissRequest = onDismissRequest,
        header = {
            TrackSheetTitle(
                title = stringResource(R.string.altyazilar),
                actions = {
                    TextButton(onClick = onOpenSubtitleSettings) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(imageVector = Icons.Palette, contentDescription = null)
                            Text("Stil")
                        }
                    }
                    TextButton(onClick = onOpenSubtitleDelay) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(imageVector = Icons.MoreTime, contentDescription = null)
                            Text("Gecikme")
                        }
                    }
                },
            )
            AddTrackRow(title = KitsugiStrings.get(R.string.harici_altyazi_ekle), onClick = onAddSubtitleFile)
        },
        track = { track ->
            // "Altyazıyı Kapat" (-99) veya negatif track için indirme yok
            val matchedInput = if (track.id >= 0) {
                val cleanLabel = track.label.replace(Regex("""^\d+\.\s*"""), "").trim()
                // 1. İsim birebir eşleşmesi (yerel dosya, http/https, file:// tüm formatlar desteklenir)
                subtitleInputs.firstOrNull { sub ->
                    sub.url.isNotBlank() && (
                        sub.name.trim().equals(cleanLabel, ignoreCase = true) ||
                        sub.name.trim().equals(track.label.trim(), ignoreCase = true)
                    )
                } ?:
                // 2. İsim içerme eşleşmesi (örn: "Türkçe (OpenSubtitles v3)" vs "Türkçe")
                subtitleInputs.firstOrNull { sub ->
                    sub.url.isNotBlank() && (
                        cleanLabel.contains(sub.name.trim(), ignoreCase = true) ||
                        sub.name.trim().contains(cleanLabel, ignoreCase = true)
                    )
                } ?:
                // 3. İndeks bazlı doğrudan eşleşme
                subtitleInputs.getOrNull(track.id)?.takeIf { it.url.isNotBlank() } ?:
                // 4. Dil kodu eşleşmesi
                (if (!track.language.isNullOrBlank()) {
                    subtitleInputs.firstOrNull { sub ->
                        sub.url.isNotBlank() && sub.lang.equals(track.language, ignoreCase = true)
                    }
                } else null) ?:
                // 5. Türkçe anahtar kelime eşleşmesi
                (if (com.kitsugi.animelist.core.player.PlayerSubtitleUtils.isTurkish(track.language, track.label)) {
                    subtitleInputs.firstOrNull { sub ->
                        sub.url.isNotBlank() && com.kitsugi.animelist.core.player.PlayerSubtitleUtils.isTurkish(sub.lang, sub.name)
                    }
                } else null)
            } else null

            if (supportsSecondary) {
                TextButton(onClick = { onSelectSecondary(track.id) },
                    enabled = track.id == -99 || track.id !in selectedIndices) {
                    KitsugiEmojiLabel(
                        label = if (track.id == selectedSecondaryIndex) "İkinci altyazı: seçili"
                            else if (track.id == -99) "İkinci altyazıyı kapat" else "İkinci altyazı olarak seç",
                        icon = if (track.id == selectedSecondaryIndex) Icons.Check else null,
                        color = LocalContentColor.current,
                        style = MaterialTheme.typography.labelLarge,
                        iconSize = 12.dp
                    )
                }
            }
            SubtitleTrackRow(
                title = if (track.id == -99) track.label else buildTrackTitle(track),
                selected = if (allSelected.contains(track.id)) 0 else -1,
                onClick = { onSelect(track.id) },
                downloadableInput = matchedInput,
                onDownload = { onDownloadSubtitle(it) },
            )
        },
        footer = {
            Column(
                modifier = modifier.padding(16.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Icon(Icons.Info, null)
                Text(
                    stringResource(R.string.inline_only_ass_ssa_format_is_8635db9),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        modifier = modifier,
    )
}

@Composable
fun SubtitleTrackRow(
    title: String,
    selected: Int, // -1=seçilmedi, 0=1. seçim, 1=2. seçim
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Eğer null değilse bu altyazı indirilebilir demektir — indirme ikonu gösterilir. */
    downloadableInput: SubtitleInput? = null,
    onDownload: (SubtitleInput) -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = selected > -1,
            onCheckedChange = { onClick() },
        )
        Text(
            text = title,
            fontStyle = if (selected > -1) FontStyle.Italic else FontStyle.Normal,
            fontWeight = if (selected > -1) FontWeight.ExtraBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected != -1) {
            Text(
                text = "#${selected + 1}",
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        if (downloadableInput != null) {
            IconButton(
                onClick = { onDownload(downloadableInput) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Download,
                    contentDescription = stringResource(R.string.altyaziyi_indir),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private fun buildTrackTitle(track: SubtitleTrackInfo): String {
    return when {
        track.id == -1 -> track.label
        track.language.isNullOrBlank() && track.label.isNotBlank() -> "${track.id}. ${track.label}"
        !track.language.isNullOrBlank() && track.label.isNotBlank() -> "${track.id}. ${track.label} [${track.language}]"
        !track.language.isNullOrBlank() -> "${track.id}. [${track.language}]"
        else -> "${track.id}. ${track.label}"
    }
}
