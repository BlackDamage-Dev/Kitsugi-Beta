package com.kitsugi.animelist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.data.platform.ListPlatform
import com.kitsugi.animelist.data.platform.PlatformErrorCode
import com.kitsugi.animelist.data.platform.PlatformErrorSeverity
import com.kitsugi.animelist.ui.theme.KitsugiColors

/**
 * Liste kaynağı hata kodunu (`KTS-SMK-8004 · Simkl hız sınırı`) tek satırlık kompakt rozet olarak gösterir.
 *
 * Yükleme ekranı gibi dar alanlarda kullanılır; tam neden/çözüm metni için
 * [PlatformErrorExplanationBlock] kullanılır.
 *
 * [platform] verilirse rozet, kod genel olsa bile (`KTS-COM-…`) hatanın hangi
 * kaynaktan geldiğini `Kitsu · …` biçiminde gösterir.
 */
@Composable
fun PlatformErrorCodeChip(
    code: PlatformErrorCode,
    modifier: Modifier = Modifier,
    platform: ListPlatform? = null,
) {
    val tint = code.severity.chipTint()
    val title = stringResource(code.titleRes)
    val platformLabel = if (platform != null) stringResource(platform.labelRes) else null
    val friendlyTitle = if (platformLabel != null) "$platformLabel · $title" else title
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .semantics { contentDescription = "${code.code}: $friendlyTitle" }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = code.code,
            color = tint,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        Text(
            text = friendlyTitle,
            color = KitsugiColors.TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun PlatformErrorSeverity.chipTint(): Color = when (this) {
    PlatformErrorSeverity.INFO -> Color(0xFF64B5F6)
    PlatformErrorSeverity.WARNING -> Color(0xFFFFB74D)
    PlatformErrorSeverity.ERROR, PlatformErrorSeverity.CRITICAL -> KitsugiColors.AccentRed
}
