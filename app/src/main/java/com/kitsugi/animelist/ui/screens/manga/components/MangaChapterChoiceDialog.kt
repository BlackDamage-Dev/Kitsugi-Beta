package com.kitsugi.animelist.ui.screens.manga.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.manga.ChapterChoiceReason
import com.kitsugi.animelist.data.manga.ChapterChoiceRequest
import com.kitsugi.animelist.data.manga.ChapterVariantInfo
import com.kitsugi.animelist.data.manga.MangaChapter
import com.kitsugi.animelist.ui.components.KitsugiButton
import com.kitsugi.animelist.ui.components.KitsugiSheetOrDialog
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Switch
import com.kitsugi.animelist.ui.theme.gradient.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Aynı bölümün birden çok çevirmeni var" seçim penceresi.
 *
 * Neden var?
 * ----------
 * Kaynaklar aynı bölümü birden fazla çevirmen (AnimeWho, WebtoonTR,
 * Tempest Fansub…) üzerinden sunuyor. Okuyucuda "sonraki bölüm"e basıldığında
 * hangi çevirmenle devam edileceği belirsizdi; kullanıcı farkında olmadan
 * başka bir çevirmene atlıyordu. Bu pencere:
 *
 *  • her çevirmenin adını, bölüm adını, yükleme tarihini ve **kaldığı sayfayı**
 *    gösterir,
 *  • hatırlanan çevirmeni ön seçili getirir,
 *  • "Bu çevirmeni hatırla" ve "Bir daha sorma" anahtarlarıyla kalıcı tercih sunar.
 *
 * Tema uyumu
 * ----------
 * Pencerenin zemini/yazıları `KitsugiColors` üzerinden gelir; vurgu rengi
 * uygulamanın tema vurgusudur. Böylece açık, koyu ve AMOLED temalarda okunaklı
 * kalır. Tüm içerik tek bir `LazyColumn` içinde olduğu için küçük ekranlarda
 * (yatay telefon, küçük tablet) taşma/kırpılma olmaz.
 */
@Composable
fun MangaChapterChoiceDialog(
    request: ChapterChoiceRequest,
    onConfirm: (chapter: MangaChapter, remember: Boolean, autoContinue: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = LocalKitsugiAccent.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    val preselected = request.preselected
    var selectedUrl by remember(request.groupKey) { mutableStateOf(preselected?.chapter?.url) }
    var rememberScanlator by remember(request.groupKey) { mutableStateOf(true) }
    var autoContinue by remember(request.groupKey) { mutableStateOf(false) }

    val selected = request.variants.firstOrNull { it.chapter.url == selectedUrl } ?: preselected

    KitsugiSheetOrDialog(onDismiss = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.72f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "choice_header") {
                Column {
                    Text(
                        text = stringResource(R.string.manga_choice_title),
                        color = KitsugiColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.manga_choice_subtitle,
                            request.chapterName,
                            request.variants.size,
                        ),
                        color = KitsugiColors.TextSecondary,
                        fontSize = 12.sp,
                    )
                    if (request.reason != ChapterChoiceReason.Manual) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (request.isForward) {
                                stringResource(R.string.manga_choice_reason_next, request.chapterName)
                            } else {
                                stringResource(R.string.manga_choice_reason_previous, request.chapterName)
                            },
                            color = accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            items(request.variants, key = { it.chapter.url }) { variant ->
                VariantCard(
                    variant = variant,
                    selected = variant.chapter.url == selected?.chapter?.url,
                    isPreferred = isPreferred(request, variant),
                    accent = accent,
                    onSelect = { selectedUrl = variant.chapter.url },
                )
            }

            item(key = "choice_switches") {
                Column {
                    Spacer(Modifier.height(2.dp))
                    ToggleLine(
                        title = stringResource(R.string.manga_choice_remember),
                        description = stringResource(R.string.manga_choice_remember_desc),
                        checked = rememberScanlator || autoContinue,
                        enabled = !autoContinue,
                        onCheckedChange = { rememberScanlator = it },
                    )
                    ToggleLine(
                        title = stringResource(R.string.manga_choice_auto),
                        description = stringResource(R.string.manga_choice_auto_desc),
                        checked = autoContinue,
                        enabled = true,
                        onCheckedChange = { autoContinue = it },
                    )
                }
            }

            item(key = "choice_continue") {
                KitsugiButton(
                    onClick = {
                        selected?.let { chosen ->
                            onConfirm(chosen.chapter, rememberScanlator || autoContinue, autoContinue)
                        }
                    },
                    enabled = selected != null,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = KitsugiColors.OnAccent,
                    ),
                ) {
                    Text(stringResource(R.string.manga_choice_continue))
                }
            }

            item(key = "choice_cancel") {
                KitsugiButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KitsugiColors.SurfaceStrong,
                        contentColor = KitsugiColors.TextSecondary,
                    ),
                ) {
                    Text(stringResource(R.string.manga_choice_cancel))
                }
            }

            item(key = "choice_bottom_inset") {
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

private fun isPreferred(request: ChapterChoiceRequest, variant: ChapterVariantInfo): Boolean {
    val preferred = request.preferredScanlator?.trim()?.lowercase().orEmpty()
    if (preferred.isEmpty()) return false
    return variant.scanlator?.trim()?.lowercase() == preferred
}

@Composable
private fun VariantCard(
    variant: ChapterVariantInfo,
    selected: Boolean,
    isPreferred: Boolean,
    accent: Color,
    onSelect: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val borderColor = if (selected) accent.copy(alpha = 0.75f) else KitsugiColors.Border
    val backgroundColor = if (selected) accent.copy(alpha = 0.14f) else KitsugiColors.SurfaceStrong

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, borderColor, shape)
            .clickable { onSelect() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) accent else KitsugiColors.TextMuted,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = variant.scanlator ?: stringResource(R.string.manga_choice_unknown_scanlator),
                    color = KitsugiColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isPreferred) {
                    Spacer(Modifier.width(6.dp))
                    Badge(text = stringResource(R.string.manga_choice_preferred), color = accent)
                }
            }

            // Bölüm adı çevirmene göre farklı yazılmış olabilir; göster.
            if (variant.chapter.name.isNotBlank() && variant.chapter.name != variant.scanlator) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = variant.chapter.name,
                    color = KitsugiColors.TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val dateText = formatUploadDate(variant.chapter.uploadDate)
            val progressText = when {
                variant.isCompleted -> stringResource(R.string.okundu)
                variant.hasProgress -> stringResource(
                    R.string.manga_choice_page_progress,
                    variant.lastPageIndex + 1,
                    variant.totalPages,
                )
                else -> null
            }
            if (dateText != null || progressText != null) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (progressText != null) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = KitsugiColors.AccentGreen,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = progressText,
                            color = KitsugiColors.AccentGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    if (progressText != null && dateText != null) {
                        Text(
                            text = " • ",
                            color = KitsugiColors.TextMuted,
                            fontSize = 11.sp,
                        )
                    }
                    if (dateText != null) {
                        Text(
                            text = dateText,
                            color = KitsugiColors.TextMuted,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.manga_choice_select),
                color = if (selected) accent else KitsugiColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = text, color = color, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ToggleLine(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                color = if (enabled) KitsugiColors.TextPrimary else KitsugiColors.TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = description,
                color = KitsugiColors.TextMuted,
                fontSize = 10.sp,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled,
        )
    }
}

private fun formatUploadDate(raw: Long): String? {
    if (raw <= 0L) return null
    val millis = if (raw > 10_000_000_000L) raw else raw * 1000L
    return runCatching {
        SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))
    }.getOrNull()
}
