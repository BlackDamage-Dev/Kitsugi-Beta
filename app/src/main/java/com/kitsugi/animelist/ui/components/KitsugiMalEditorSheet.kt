package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.ui.utils.localizedLabel
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.utils.kitsugiVerticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KitsugiMalMediaEntryEditorSheet(
    initialEntry: MediaEntry?,
    source: String,
    scoreFormat: String = "POINT_10",
    onDismiss: () -> Unit,
    onDeleteClick: (() -> Unit)?,
    onConfirm: (
        title: String,
        subtitle: String,
        type: MediaType,
        status: WatchStatus,
        isAdult: Boolean,
        progress: Int,
        total: Int?,
        score: Int?,
        isFavorite: Boolean,
        startDate: String?,
        endDate: String?,
        notes: String?,
        tags: String?,
        priority: Int?,
        isRepeating: Boolean,
        repeatCount: Int,
        repeatValue: Int,
        volumeProgress: Int,
        isPrivate: Boolean,
        isHiddenFromStatusLists: Boolean
    ) -> Unit
) {
    val isEditing = initialEntry != null
    val resolvedAccentColor = LocalKitsugiAccent.current

    var selectedStatus by rememberSaveable(initialEntry?.id) { mutableStateOf(initialEntry?.status ?: WatchStatus.Planned) }
    var progressText by rememberSaveable(initialEntry?.id) { mutableStateOf(initialEntry?.progress?.toString() ?: "0") }
    var totalText by rememberSaveable(initialEntry?.id) { mutableStateOf(initialEntry?.total?.toString().orEmpty()) }
    var scoreText by rememberSaveable(initialEntry?.id) {
        val dbScore = initialEntry?.score
        val initialUiScore = if (dbScore != null) {
            when (scoreFormat) {
                "POINT_100" -> (dbScore * 10).toString()
                "POINT_10_DECIMAL" -> "${dbScore}.0"
                "POINT_5" -> (dbScore / 2).toString()
                "POINT_3" -> {
                    when {
                        dbScore >= 8 -> "3"
                        dbScore >= 5 -> "2"
                        else -> "1"
                    }
                }
                else -> dbScore.toString()
            }
        } else {
            ""
        }
        mutableStateOf(initialUiScore)
    }
    var isFavorite by rememberSaveable(initialEntry?.id) { mutableStateOf(initialEntry?.isFavorite ?: false) }
    var startDateText by rememberSaveable(initialEntry?.id) { mutableStateOf(initialEntry?.startDate.orEmpty()) }
    var endDateText by rememberSaveable(initialEntry?.id) { mutableStateOf(initialEntry?.endDate.orEmpty()) }
    var notes by rememberSaveable(initialEntry?.id) { mutableStateOf(initialEntry?.notes.orEmpty()) }
    var tags by rememberSaveable(initialEntry?.id) { mutableStateOf(initialEntry?.tags.orEmpty()) }
    var priority by rememberSaveable(initialEntry?.id) { mutableStateOf(initialEntry?.priority ?: 0) }
    var isRepeating by rememberSaveable(initialEntry?.id) { mutableStateOf(initialEntry?.isRepeating ?: false) }
    var repeatCount by rememberSaveable(initialEntry?.id) { mutableStateOf(initialEntry?.repeatCount ?: 0) }
    var repeatValue by rememberSaveable(initialEntry?.id) { mutableStateOf(initialEntry?.repeatValue ?: 0) }
    var volumeProgress by rememberSaveable(initialEntry?.id) { mutableStateOf(initialEntry?.volumeProgress ?: 0) }
    val scrollState = rememberScrollState()

    val parsedProgress = progressText.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val parsedTotal = totalText.toIntOrNull()?.takeIf { it > 0 }
    val parsedScore = scoreText.toDoubleOrNull()?.let { uiScore ->
        when (scoreFormat) {
            "POINT_100" -> (uiScore / 10).toInt().coerceIn(0, 10)
            "POINT_10_DECIMAL" -> uiScore.toInt().coerceIn(0, 10)
            "POINT_5" -> (uiScore * 2).toInt().coerceIn(0, 10)
            "POINT_3" -> {
                when (uiScore.toInt()) {
                    3 -> 10
                    2 -> 5
                    else -> 1
                }
            }
            else -> uiScore.toInt().coerceIn(0, 10)
        }
    }
    val normalizedStartDate = startDateText.trim().takeIf { it.isValidDateOrBlank() }
    val normalizedEndDate = endDateText.trim().takeIf { it.isValidDateOrBlank() }
    val canSave = startDateText.trim().isValidDateOrBlank() && endDateText.trim().isValidDateOrBlank()

    KitsugiSheetOrDialog(
        onDismiss = onDismiss,
        innerColumnScrollState = scrollState
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = LocalDismissAnimated.current) {
                    Text(text = stringResource(R.string.iptal), color = KitsugiColors.TextSecondary, fontWeight = FontWeight.Medium)
                }
                Text(
                    text = initialEntry?.title ?: stringResource(R.string.ui_mal_entry_fallback),
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    textAlign = TextAlign.Center
                )
                TextButton(
                    enabled = canSave,
                    onClick = {
                        onConfirm(
                            initialEntry?.title.orEmpty(), initialEntry?.subtitle.orEmpty(),
                            initialEntry?.type ?: MediaType.Anime, selectedStatus,
                            initialEntry?.isAdult ?: false, parsedProgress, parsedTotal, parsedScore,
                            isFavorite, normalizedStartDate, normalizedEndDate,
                            notes.trim().takeIf { it.isNotBlank() }, tags.trim().takeIf { it.isNotBlank() },
                            priority, isRepeating, repeatCount, repeatValue, volumeProgress, false, false
                        )
                    }
                ) {
                    Text(
                        text = "Uygula",
                        color = if (canSave) resolvedAccentColor else KitsugiColors.TextMuted,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            HorizontalDivider(color = KitsugiColors.Border, modifier = Modifier.padding(horizontal = 16.dp))

            Column(
                modifier = Modifier.fillMaxWidth().kitsugiVerticalScroll(scrollState).padding(bottom = 32.dp)
            ) {
                val selectedType = initialEntry?.type ?: MediaType.Anime

                // Status row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        WatchStatus.Watching to Icons.PlayArrow,
                        WatchStatus.Completed to Icons.Check,
                        WatchStatus.Paused to Icons.Pause,
                        WatchStatus.Dropped to Icons.Delete,
                        WatchStatus.Planned to Icons.Bookmark
                    ).forEach { (status, icon) ->
                        val selected = selectedStatus == status
                        Box(
                            modifier = Modifier.size(48.dp).clip(CircleShape)
                                .background(if (selected) resolvedAccentColor else KitsugiColors.SurfaceSoft)
                                .tvClickable(shape = CircleShape) { selectedStatus = status },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = icon, contentDescription = status.localizedLabel(),
                                tint = if (selected) KitsugiColors.Background else KitsugiColors.TextSecondary,
                                modifier = Modifier.size(24.dp))
                        }
                    }
                }

                HorizontalDivider(color = KitsugiColors.Border, modifier = Modifier.padding(horizontal = 16.dp))

                val progressSuffix = if (parsedTotal != null) " / $parsedTotal" else ""
                SheetRow(
                    icon = if (selectedType == MediaType.Anime) Icons.PlayArrow else Icons.Book,
                    label = if (selectedType == MediaType.Anime) stringResource(R.string.tv_detail_episodes) else stringResource(R.string.bolumler_chapter),
                    value = progressText,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty()) {
                            progressText = ""
                        } else {
                            val parsed = newValue.toIntOrNull()
                            if (parsed != null) {
                                progressText = if (parsedTotal != null) {
                                    parsed.coerceAtMost(parsedTotal).toString()
                                } else {
                                    parsed.toString()
                                }
                            }
                        }
                    },
                    valuePlaceholder = "0",
                    valueSuffix = progressSuffix,
                    onDecrement = { progressText = ((progressText.toIntOrNull() ?: 0) - 1).coerceAtLeast(0).toString() },
                    onIncrement = {
                        val currentVal = progressText.toIntOrNull() ?: 0
                        val maxVal = totalText.toIntOrNull()
                        val nextVal = currentVal + 1
                        progressText = if (maxVal != null) nextVal.coerceAtMost(maxVal).toString() else nextVal.toString()
                    }
                )

                if (selectedType == MediaType.Manga) {
                    SheetRow(
                        icon = Icons.Bookmark,
                        label = "Ciltler (Volume)",
                        value = volumeProgress.toString(),
                        onValueChange = { newValue ->
                            if (newValue.isEmpty()) {
                                volumeProgress = 0
                            } else {
                                val parsed = newValue.toIntOrNull()
                                if (parsed != null) {
                                    volumeProgress = parsed
                                }
                            }
                        },
                        valuePlaceholder = "0",
                        onDecrement = { volumeProgress = (volumeProgress - 1).coerceAtLeast(0) },
                        onIncrement = { volumeProgress = volumeProgress + 1 }
                    )
                }

                val scoreSuffix = when (scoreFormat) {
                    "POINT_100" -> " / 100"
                    "POINT_10_DECIMAL" -> " / 10"
                    "POINT_5" -> " / 5 (★)"
                    "POINT_3" -> {
                        val emoji = when (scoreText) {
                            "3" -> " 😊"
                            "2" -> " 😐"
                            "1" -> " 🙁"
                            else -> ""
                        }
                        " / 3$emoji"
                    }
                    else -> {
                        val sInt = scoreText.toIntOrNull() ?: 0
                        val scoreLabel = when (sInt) {
                            1 -> " (Korkunç)"; 2 -> " (Kötü)"; 3 -> " (Çok Kötü)"; 4 -> " (Kötü)"
                            5 -> " (Ortalama)"; 6 -> " (Fena Değil)"; 7 -> " (İyi)"; 8 -> " (Çok İyi)"
                            9 -> " (Harika)"; 10 -> " (Şaheser)"; else -> ""
                        }
                        " / 10$scoreLabel"
                    }
                }

                SheetRow(
                    icon = Icons.Star,
                    label = stringResource(R.string.detail_stat_score),
                    value = scoreText,
                    onValueChange = null,
                    valuePlaceholder = if (scoreFormat == "POINT_3") "-" else "0",
                    valueSuffix = scoreSuffix,
                    onDecrement = {
                        val currentVal = scoreText.toDoubleOrNull() ?: 0.0
                        val step = if (scoreFormat == "POINT_100") 10.0 else 1.0
                        val minLimit = if (scoreFormat == "POINT_3") 1.0 else 0.0
                        val newVal = if (currentVal > minLimit) (currentVal - step).coerceAtLeast(minLimit) else null
                        scoreText = if (newVal != null) {
                            if (scoreFormat == "POINT_10_DECIMAL") "${newVal.toInt()}.0" else newVal.toInt().toString()
                        } else {
                            ""
                        }
                    },
                    onIncrement = {
                        val currentVal = scoreText.toDoubleOrNull() ?: 0.0
                        val step = if (scoreFormat == "POINT_100") 10.0 else 1.0
                        val maxLimit = when (scoreFormat) {
                            "POINT_100" -> 100.0
                            "POINT_5" -> 5.0
                            "POINT_3" -> 3.0
                            else -> 10.0
                        }
                        val newVal = (currentVal + step).coerceAtMost(maxLimit)
                        scoreText = if (scoreFormat == "POINT_10_DECIMAL") "${newVal.toInt()}.0" else newVal.toInt().toString()
                    }
                )

                HorizontalDivider(color = KitsugiColors.Border, modifier = Modifier.padding(horizontal = 16.dp))

                val context = LocalContext.current
                fun selectDate(currentVal: String, onSelected: (String) -> Unit) {
                    val calendar = java.util.Calendar.getInstance()
                    val parts = currentVal.split("-")
                    val year = parts.getOrNull(0)?.toIntOrNull() ?: calendar.get(java.util.Calendar.YEAR)
                    val month = parts.getOrNull(1)?.toIntOrNull()?.minus(1) ?: calendar.get(java.util.Calendar.MONTH)
                    val day = parts.getOrNull(2)?.toIntOrNull() ?: calendar.get(java.util.Calendar.DAY_OF_MONTH)
                    android.app.DatePickerDialog(context, { _, y, m, d ->
                        onSelected("$y-${String.format("%02d", m + 1)}-${String.format("%02d", d)}")
                    }, year, month, day).show()
                }

                SheetRow(icon = Icons.CalendarToday, label = stringResource(R.string.baslangic_tarihi),
                    value = startDateText.ifBlank { KitsugiStrings.get(R.string.tarih_secilmemis) },
                    onClick = { selectDate(startDateText) { startDateText = it } },
                    onClearClick = if (startDateText.isNotBlank()) { { startDateText = "" } } else null)
                SheetRow(icon = Icons.EventAvailable, label = stringResource(R.string.bitis_tarihi),
                    value = endDateText.ifBlank { KitsugiStrings.get(R.string.tarih_secilmemis) },
                    onClick = { selectDate(endDateText) { endDateText = it } },
                    onClearClick = if (endDateText.isNotBlank()) { { endDateText = "" } } else null)

                val priorityText = when (priority) { 1 -> "Orta"; 2 -> "Yüksek"; else -> "Düşük" }
                SheetRow(
                    icon = Icons.PriorityHigh, label = stringResource(R.string.oncelik), value = priorityText,
                    onDecrement = { priority = ((priority ?: 0) - 1).coerceAtLeast(0) },
                    onIncrement = { priority = ((priority ?: 0) + 1).coerceAtMost(2) }
                )

                SheetRow(
                    icon = Icons.Repeat,
                    label = if (selectedType == MediaType.Manga) KitsugiStrings.get(R.string.yeniden_okunuyor) else KitsugiStrings.get(R.string.watch_repeating),
                    switchChecked = isRepeating, onSwitchCheckedChange = { isRepeating = it }
                )
                SheetRow(
                    icon = Icons.RepeatOne,
                    label = if (selectedType == MediaType.Manga) KitsugiStrings.get(R.string.toplam_tekrar_okuma) else KitsugiStrings.get(R.string.toplam_tekrar_izleme),
                    value = repeatCount.toString(),
                    onDecrement = { repeatCount = (repeatCount - 1).coerceAtLeast(0) },
                    onIncrement = { repeatCount = repeatCount + 1 }
                )

                val repeatValueText = when (repeatValue) { 1 -> "Çok Düşük"; 2 -> "Düşük"; 3 -> "Orta"; 4 -> "Yüksek"; 5 -> "Çok Yüksek"; else -> "─" }
                SheetRow(
                    icon = Icons.DateRange,
                    label = if (selectedType == MediaType.Manga) KitsugiStrings.get(R.string.tekrar_okuma_degeri) else KitsugiStrings.get(R.string.tekrar_izleme_degeri),
                    value = repeatValueText,
                    onDecrement = { repeatValue = (repeatValue - 1).coerceAtLeast(0) },
                    onIncrement = { repeatValue = (repeatValue + 1).coerceAtMost(5) }
                )

                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Label, contentDescription = "Etiketler",
                        tint = KitsugiColors.TextSecondary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    DialogTextField(value = tags, onValueChange = { tags = it },
                        label = "Etiketler", placeholder = stringResource(R.string.virgulle_ayirin_orn_sevilen_aksiyon),
                        modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.Top) {
                    Icon(imageVector = Icons.Notes, contentDescription = "Notlar",
                        tint = KitsugiColors.TextSecondary, modifier = Modifier.size(24.dp).padding(top = 12.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    TextField(value = notes, onValueChange = { notes = it },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        shape = RoundedCornerShape(18.dp),
                        placeholder = { Text(text = stringResource(R.string.kisisel_dusuncelerinizi_yazin), color = KitsugiColors.TextMuted) },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = KitsugiColors.TextPrimary, unfocusedTextColor = KitsugiColors.TextPrimary,
                            focusedContainerColor = KitsugiColors.SurfaceSoft, unfocusedContainerColor = KitsugiColors.SurfaceSoft,
                            focusedIndicatorColor = resolvedAccentColor, unfocusedIndicatorColor = KitsugiColors.Border,
                            cursorColor = resolvedAccentColor
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                SheetRow(icon = Icons.Star, label = "Favori",
                    switchChecked = isFavorite, onSwitchCheckedChange = { isFavorite = it })

                if (isEditing && onDeleteClick != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = KitsugiColors.Border, modifier = Modifier.padding(horizontal = 16.dp))
                    SheetRow(icon = Icons.Delete, label = KitsugiStrings.get(R.string.action_delete),
                        onClick = onDeleteClick, modifier = Modifier.padding(vertical = 4.dp))
                }

                Spacer(modifier = Modifier.height(18.dp))
                val platformName = when {
                    source == "mal" || source == "jikan" -> "MyAnimeList"
                    source == "simkl" || source == "tmdb" -> "Simkl"
                    source == "anilist" -> "AniList"
                    else -> "Yerel Kitaplık"
                }
                Text(
                    text = KitsugiStrings.get(R.string.inline_note_your_changes_will_automatical_9cfe969, platformName),
                    color = KitsugiColors.TextMuted, style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}
