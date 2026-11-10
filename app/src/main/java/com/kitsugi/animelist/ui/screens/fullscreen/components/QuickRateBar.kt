package com.kitsugi.animelist.ui.screens.fullscreen.components

import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.screens.fullscreen.runtime.PlayerAutoTrackController
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.utils.tvClickable

/**
 * Bitişte çıkan **küçük hızlı puanlama çubuğu**.
 *
 * Tam ekran bir pencere/dialog AÇMAZ: oynatıcının altında ince bir bildirim bandı olarak
 * belirir, `quickRateSeconds` kadar bekler ve kendiliğinden kaybolur (isteğe bağlı olarak
 * kullanıcı kapatabilir). Kullanıcı yıldıza bastığı an puan listedeki kayda yazılır
 * (0 = puanlanmadı, aynı yıldıza tekrar basmak puanı temizler).
 *
 * - Oynatıcı **hangi kaynaktan** açılmış olursa olsun aynı çubuk görünür; kaynak rozeti
 *   (`source`) yazının yanında gösterilir.
 * - TV (D-pad) için [tvClickable] kullanılır: odaklanınca büyür ve vurgu rengiyle çerçevelenir.
 * - PiP modunda hiç çizilmez (çağıran taraf `visible=false` verir).
 */
@Composable
fun QuickRateBar(
    prompt: PlayerAutoTrackController.QuickRatePrompt?,
    onRate: (Int) -> Unit,
    onDismiss: () -> Unit,
    visible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val accent = LocalKitsugiAccent.current
    var lastRated by remember(prompt?.title, prompt?.episode) { mutableStateOf<Int?>(null) }
    val shown = visible && prompt != null

    // Puan yazıldığında kullanıcıya 1,2 sn "kaydedildi" geri bildirimi verilir.
    var savedFeedback by remember(prompt?.title, prompt?.episode) { mutableStateOf(false) }
    LaunchedEffect(lastRated) {
        if (lastRated != null) {
            savedFeedback = true
            kotlinx.coroutines.delay(1_200L)
            savedFeedback = false
        }
    }

    AnimatedVisibility(
        visible = shown,
        enter = slideInVertically(initialOffsetY = { it / 2 }, animationSpec = tween(220)) + fadeIn(tween(180)),
        exit = slideOutVertically(targetOffsetY = { it / 2 }, animationSpec = tween(180)) + fadeOut(tween(140)),
        modifier = modifier
    ) {
        val data = prompt ?: return@AnimatedVisibility
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black.copy(alpha = 0.82f))
                .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Check,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = data.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Not: `stringResource` @Composable'dir; `buildString` gibi satır içi
                    // lambda'ların içinde çağrılamaz. Alt metin bu yüzden önce hesaplanır.
                    val ratedNow = lastRated
                    val hint = stringResource(R.string.quick_rate_hint)
                    val episodeLabel = if (!data.isMovie && data.episode > 0) {
                        stringResource(R.string.quick_rate_episode, data.episode)
                    } else {
                        null
                    }
                    val subtitle = when {
                        savedFeedback && ratedNow != null && ratedNow > 0 ->
                            stringResource(R.string.quick_rate_saved, ratedNow)
                        episodeLabel != null -> "$episodeLabel · $hint"
                        else -> hint
                    }
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Kaynak rozeti: yazının hangi platforma (AniList/MAL/Kitsu/Simkl/TMDB…)
                // işlendiğini tek bakışta gösterir; yeniyse eklenti rozetine düşer.
                data.source?.takeIf { it.isNotBlank() }?.let { source ->
                    com.kitsugi.animelist.ui.components.KitsugiSourceBadge(
                        source = source,
                        size = 16.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = "${data.remainingSeconds}s",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .tvClickable(shape = CircleShape) { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Close,
                        contentDescription = stringResource(R.string.quick_rate_dismiss),
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 10 yıldız: 1..10 puan. Aynı yıldıza ikinci dokunuş puanı temizler (0).
            val current = lastRated ?: data.currentScore ?: 0
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 1..10) {
                    val filled = i <= current
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .tvClickable(shape = CircleShape) {
                                val next = if (i == current) 0 else i
                                lastRated = next
                                onRate(next)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (filled) Icons.Star else Icons.StarBorder,
                            contentDescription = stringResource(R.string.quick_rate_score, i),
                            tint = if (filled) Color(0xFFFBBF24) else Color.White.copy(alpha = 0.38f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (data.currentScore != null && data.currentScore > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.quick_rate_existing, data.currentScore),
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 10.sp
                )
            }

            Spacer(Modifier.height(6.dp))

            // Kalan süre göstergesi: çubuk kapanmadan önce görsel geri sayım.
            LinearProgressIndicator(
                progress = {
                    val total = data.totalSeconds.coerceAtLeast(1)
                    (data.remainingSeconds.coerceIn(0, total)).toFloat() / total.toFloat()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp)),
                color = accent.copy(alpha = 0.8f),
                trackColor = Color.White.copy(alpha = 0.08f)
            )
        }
    }
}
