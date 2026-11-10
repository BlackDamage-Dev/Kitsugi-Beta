package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.utils.NextAiringFormat
import kotlinx.coroutines.delay

/**
 * "episode|airingAtEpoch" formatındaki string'i parse eden, canlı geri sayım
 * gösteren chip bileşeni. Tüm kaynaklar için aynı mantık:
 * `"Bölüm 2 · 2026-10-15 · 6 gün sonra yayında"` (yayın tarihi + geri sayım).
 *
 * Bölüm numarası bilinmiyorsa `"Dizi · 2026-10-15 · …"`, film ise `"Film · …"`.
 *
 * @param nextAiringEpisode "episode|airingAtEpoch" formatında string. null ise gösterilmez.
 * @param accentColor Chip rengi (varsayılan: AccentOrange)
 */
@Composable
fun NextAiringChip(
    nextAiringEpisode: String?,
    modifier: Modifier = Modifier,
    accentColor: Color = KitsugiColors.AccentOrange
) {
    if (nextAiringEpisode.isNullOrBlank()) return

    val parsed = remember(nextAiringEpisode) { NextAiringFormat.parse(nextAiringEpisode) }
    if (!parsed.isMachineFormat && parsed.legacyText.isNullOrBlank()) return

    var countdownText by remember(parsed) { mutableStateOf("") }

    LaunchedEffect(parsed) {
        while (true) {
            countdownText = NextAiringFormat.chipText(parsed)
            val targetEpoch = parsed.epoch
            if (targetEpoch == null) break
            val remaining = targetEpoch - System.currentTimeMillis() / 1000L
            if (remaining <= 0L) break
            // Metin en küçük dakika çözünürlüğünde değişir; 10 sn'lik tik gereksizdi.
            val delayMs = if (remaining < 86400L) 30_000L else 60_000L
            delay(delayMs)
        }
    }

    if (countdownText.isBlank()) return

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accentColor.copy(alpha = 0.15f))
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Schedule,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(11.dp)
        )
        Text(
            text = countdownText,
            color = accentColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Geriye dönük uyumluluk için static overload.
 * Tercih edilen: [NextAiringChip(nextAiringEpisode: String?)]
 */
@Composable
fun NextAiringChip(
    episodeNumber: Int,
    airingInSeconds: Long,
    modifier: Modifier = Modifier,
    accentColor: Color = KitsugiColors.AccentOrange
) {
    NextAiringChip(
        nextAiringEpisode = "$episodeNumber|$airingInSeconds",
        modifier = modifier,
        accentColor = accentColor
    )
}
