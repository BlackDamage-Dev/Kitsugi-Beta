package com.kitsugi.animelist.ui.screens.fullscreen.controls.components.sheets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.core.player.SubtitleInput
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

data class SubtitleTrackInfo(
    val id: Int,
    val label: String,
    val language: String? = null,
    /** Motorun codec/MIME bilgisinden çözülen gerçek altyazı biçimi (SRT, ASS, PGS vb.). */
    val formatTag: String? = null,
)

@Composable
fun SubtitleTracksSheet(
    tracks: List<SubtitleTrackInfo>,
    selectedIndices: List<Int>,
    onSelect: (Int) -> Unit,
    selectedSecondaryIndex: Int = -1,
    supportsSecondary: Boolean = false,
    onSelectSecondary: (Int) -> Unit = {},
    onAddSubtitleFile: () -> Unit = {},
    onOpenSubtitleSettings: () -> Unit,
    onOpenSubtitleDelay: () -> Unit,
    onDismissRequest: () -> Unit = {},
    /** Oynatıcıdaki mevcut SubtitleInput listesi — indirme butonları için eşleştirmede kullanılır. */
    subtitleInputs: List<SubtitleInput> = emptyList(),
    /** Kullanıcı bir altyazıyı indirmek istediğinde çağrılır. */
    onDownloadSubtitle: (SubtitleInput) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val allTracks = androidx.compose.runtime.remember(tracks) {
        listOf(SubtitleTrackInfo(id = -99, label = KitsugiStrings.get(R.string.altyaziyi_kapat))) + tracks
    }
    val allSelected = if (selectedSecondaryIndex != -99) {
        selectedIndices + selectedSecondaryIndex
    } else {
        selectedIndices
    }

    GenericTracksSheet(
        tracks = allTracks,
        onDismissRequest = onDismissRequest,
        header = {
            TrackSheetTitle(
                title = stringResource(R.string.altyazilar),
                actions = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
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
                    }
                },
            )
        },
        track = { track ->
            val matchedInput = subtitleInputs.firstOrNull { input ->
                val trackLang = track.language?.trim().orEmpty()
                val trackLabel = track.label.trim()
                val inputLang = input.lang.trim()
                val inputName = input.name.trim()
                (trackLang.isNotBlank() && inputLang.isNotBlank() &&
                    com.kitsugi.animelist.core.player.PlayerSubtitleUtils.matchesLanguageCode(trackLang, inputLang)) ||
                    (trackLabel.isNotBlank() && inputName.isNotBlank() && trackLabel.contains(inputName, ignoreCase = true)) ||
                    (trackLabel.isNotBlank() && inputName.isNotBlank() && inputName.contains(trackLabel, ignoreCase = true))
            }
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
                formatTag = track.formatTag,
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
    formatTag: String? = null,
    modifier: Modifier = Modifier,
    /** Eğer null değilse bu altyazı indirilebilir demektir — indirme ikonu gösterilir. */
    downloadableInput: SubtitleInput? = null,
    onDownload: (SubtitleInput) -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Checkbox(checked = selected != -1, onCheckedChange = { onClick() })
        Text(
            text = title,
            fontStyle = if (selected > -1) FontStyle.Italic else FontStyle.Normal,
            fontWeight = if (selected > -1) FontWeight.ExtraBold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (!formatTag.isNullOrBlank()) {
            TrackFormatTag(
                value = formatTag,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        if (selected != -1) {
            Text(
                text = "#${selected + 1}",
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.bodyMedium,
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
    if (track.id == -1) return track.label
    val rawLanguage = track.language?.trim().orEmpty()
    val friendlyLanguage = rawLanguage.takeIf { it.isNotBlank() }
        ?.let { com.kitsugi.animelist.core.player.PlayerSubtitleUtils.getFriendlyLanguageName(it) }
    val showLanguage = !friendlyLanguage.isNullOrBlank() &&
        !track.label.contains(rawLanguage, ignoreCase = true) &&
        !track.label.contains(friendlyLanguage, ignoreCase = true)
    return buildString {
        append(track.id)
        append(". ")
        if (track.label.isNotBlank()) append(track.label)
        if (showLanguage) {
            if (track.label.isNotBlank()) append(' ')
            append('[')
            append(friendlyLanguage)
            append(']')
        }
    }.trim()
}
