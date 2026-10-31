package com.kitsugi.animelist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.platform.ListPlatform
import com.kitsugi.animelist.data.platform.PlatformErrorCatalog
import com.kitsugi.animelist.data.platform.PlatformErrorCode
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

/**
 * Liste kaynağı hata kodları rehberi.
 *
 * Kullanıcı bir eşitleme hatasında `KTS-XXX-####` kodu gördüğünde, o kodun
 * **hangi kaynağa ait olduğunu, neden oluştuğunu ve ne yapması gerektiğini**
 * bu ekrandan okuyabilir. Metinler dil dosyasından gelir (TR/EN).
 *
 * Kapsam: AniList, MyAnimeList (+Jikan), Kitsu, Shikimori, Bangumi, TMDB, Simkl
 * ve tümü için geçerli ortak (ağ/HTTP/oturum) kodları.
 */
@Composable
fun PlatformErrorCodeReferenceDialog(
    onDismiss: () -> Unit,
    initialFilter: ListPlatform? = null,
    initialQuery: String = "",
) {
    var query by remember { mutableStateOf(initialQuery) }
    var platformFilter by remember { mutableStateOf(initialFilter) }

    // Başlıklar cihaz diline göre bir kez çözülür; arama bu metinler üzerinden yapılır.
    val titles = PlatformErrorCode.entries.associateWith { stringResource(it.titleRes) }
    val platformLabels = ListPlatform.entries.associateWith { stringResource(it.labelRes) }

    val normalizedQuery = query.trim().lowercase()
    val filtered = remember(query, platformFilter, titles) {
        PlatformErrorCode.entries.filter { code ->
            val matchesPlatform = platformFilter == null || code.platform == platformFilter
            if (!matchesPlatform) return@filter false
            if (normalizedQuery.isEmpty()) return@filter true
            val title = titles[code].orEmpty().lowercase()
            code.code.lowercase().contains(normalizedQuery) ||
                title.contains(normalizedQuery) ||
                code.platform.codePrefix.lowercase().contains(normalizedQuery)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KitsugiColors.Surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.pf_err_ref_title),
                    color = KitsugiColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Text(
                    text = stringResource(R.string.pf_err_ref_subtitle),
                    color = KitsugiColors.TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            Icons.Search,
                            contentDescription = null,
                            tint = KitsugiColors.TextMuted,
                            modifier = Modifier.height(18.dp)
                        )
                    },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.pf_err_ref_search_hint),
                            color = KitsugiColors.TextMuted,
                            fontSize = 12.sp
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlatformErrorFilterChip(
                        label = stringResource(R.string.tumu),
                        selected = platformFilter == null,
                        onClick = { platformFilter = null }
                    )
                    ListPlatform.entries.forEach { platform ->
                        PlatformErrorFilterChip(
                            label = platformLabels[platform].orEmpty(),
                            selected = platformFilter == platform,
                            onClick = {
                                platformFilter = if (platformFilter == platform) null else platform
                            }
                        )
                    }
                }

                if (filtered.isEmpty()) {
                    Text(
                        text = stringResource(R.string.pf_err_ref_empty),
                        color = KitsugiColors.TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.name }) { code ->
                            PlatformErrorCodeReferenceItem(
                                code = code,
                                platformLabel = platformLabels[code.platform].orEmpty()
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.pf_err_close), color = KitsugiColors.AccentBlue, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun PlatformErrorFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) KitsugiColors.AccentBlue.copy(alpha = 0.2f) else KitsugiColors.SurfaceSoft)
            .border(
                width = 1.dp,
                color = if (selected) KitsugiColors.AccentBlue else KitsugiColors.Border.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = if (selected) KitsugiColors.AccentBlue else KitsugiColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PlatformErrorCodeReferenceItem(code: PlatformErrorCode, platformLabel: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(KitsugiColors.SurfaceSoft)
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(KitsugiColors.AccentBlue.copy(alpha = 0.16f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = code.code,
                    color = KitsugiColors.AccentBlue,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(text = platformLabel, color = KitsugiColors.TextMuted, fontSize = 10.sp)
            Text(
                text = stringResource(code.severity.labelRes),
                color = KitsugiColors.TextMuted,
                fontSize = 10.sp
            )
        }
        Text(
            text = stringResource(code.titleRes),
            color = KitsugiColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
        ErrorCodeLabeledText(
            label = stringResource(R.string.pf_err_why_label),
            value = stringResource(code.whyRes)
        )
        ErrorCodeLabeledText(
            label = stringResource(R.string.pf_err_fix_label),
            value = stringResource(code.fixRes)
        )
        Text(
            text = stringResource(
                if (code.retryable) R.string.pf_err_retryable_yes else R.string.pf_err_retryable_no
            ),
            color = KitsugiColors.TextMuted,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun ErrorCodeLabeledText(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            color = KitsugiColors.TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            color = KitsugiColors.TextSecondary,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
    }
}

/**
 * Giriş ve eşitleme hata kutularının altında gösterilen kompakt kod ipucu.
 *
 * `"KTS-SHK-5003 · Shikimori yönlendirme adresi hatası"` satırının ardından
 * hatanın **neden** çıktığını ve **ne yapılması** gerektiğini dil dosyasından okur.
 * Böylece kullanıcı "hata aldım" demek yerine "şu kaynakta şu nedenle hata aldım" der.
 */
@Composable
fun PlatformErrorCodeHint(
    message: String?,
    platform: ListPlatform = ListPlatform.GENERIC,
    tint: Color = KitsugiColors.AccentRed,
) {
    if (message.isNullOrBlank()) return
    val code = remember(message, platform) {
        PlatformErrorCatalog.classify(platform = platform, rawMessage = message)
    }
    Column(
        modifier = Modifier.padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "${code.code} · ${stringResource(code.titleRes)}",
            color = tint,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(code.whyRes),
            color = KitsugiColors.TextMuted,
            fontSize = 10.sp,
            lineHeight = 14.sp
        )
        Text(
            text = stringResource(code.fixRes),
            color = KitsugiColors.TextSecondary,
            fontSize = 10.sp,
            lineHeight = 14.sp
        )
    }
}
