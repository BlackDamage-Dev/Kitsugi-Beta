package com.kitsugi.animelist.ui.screens.fullscreen.controls.components.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.kitsugi.animelist.ui.components.KitsugiDropdownMenu
import com.kitsugi.animelist.ui.components.KitsugiMenuItem
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.IconButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import com.kitsugi.animelist.ui.screens.fullscreen.controls.components.OutlinedNumericChooser
import kotlin.math.round
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

enum class SubtitleDelayType(val label: String) {
    Primary("Birincil"),
    Secondary("İkincil"),
    Both("İkisi Birden"),
}

@Composable
fun SubtitleDelayPanel(
    onDismissRequest: () -> Unit,
    currentSubtitleDelayMs: Int,
    currentSecondaryDelayMs: Int,
    currentSpeed: Float,
    onSubDelayChanged: (Int) -> Unit,
    onSecondaryDelayChanged: (Int) -> Unit,
    onSpeedChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    ConstraintLayout(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        val delayControlCard = createRef()

        var affectedSubtitle by remember { mutableStateOf(SubtitleDelayType.Primary) }
        var delay by remember { mutableIntStateOf(currentSubtitleDelayMs) }
        var secondaryDelay by remember { mutableIntStateOf(currentSecondaryDelayMs) }
        var speed by remember { mutableFloatStateOf(currentSpeed) }

        // İlk kompozisyonda callback tetiklenmesini engelle: aksi hâlde panel açılır
        // açılmaz değerler "değişmiş" sayılıp motora yeniden yazılıyordu.
        var lastAppliedPrimary by remember { mutableIntStateOf(currentSubtitleDelayMs) }
        var lastAppliedSecondary by remember { mutableIntStateOf(currentSecondaryDelayMs) }
        var lastAppliedSpeed by remember { mutableFloatStateOf(currentSpeed) }

        // Birincil/ikincil seçimi değiştiğinde kaydırıcıyı o türün GERÇEK değeriyle eşle.
        // (Eskiden "İkincil" seçilse bile kaydırıcı birincil değeri gösteriyordu.)
        LaunchedEffect(affectedSubtitle, currentSubtitleDelayMs, currentSecondaryDelayMs) {
            when (affectedSubtitle) {
                SubtitleDelayType.Primary -> delay = currentSubtitleDelayMs
                SubtitleDelayType.Secondary -> secondaryDelay = currentSecondaryDelayMs
                SubtitleDelayType.Both -> {
                    delay = currentSubtitleDelayMs
                    secondaryDelay = currentSecondaryDelayMs
                }
            }
        }

        LaunchedEffect(speed) {
            if (speed in 0.1f..10f && speed != lastAppliedSpeed) {
                lastAppliedSpeed = speed
                onSpeedChanged(speed)
            }
        }

        LaunchedEffect(delay, secondaryDelay, affectedSubtitle) {
            when (affectedSubtitle) {
                SubtitleDelayType.Primary -> {
                    if (delay != lastAppliedPrimary) {
                        lastAppliedPrimary = delay
                        onSubDelayChanged(delay)
                    }
                }
                SubtitleDelayType.Secondary -> {
                    if (secondaryDelay != lastAppliedSecondary) {
                        lastAppliedSecondary = secondaryDelay
                        onSecondaryDelayChanged(secondaryDelay)
                    }
                }
                SubtitleDelayType.Both -> {
                    // "İkisi Birden" seçiliyken ikincil değer birincile eşitlenir; eski kod
                    // ikincil callback'ini yine de ikincil (eski) değerle çağırıyordu.
                    val changed = delay != lastAppliedPrimary || delay != lastAppliedSecondary
                    if (changed) {
                        lastAppliedPrimary = delay
                        lastAppliedSecondary = delay
                        secondaryDelay = delay
                        onSubDelayChanged(delay)
                        onSecondaryDelayChanged(delay)
                    }
                }
            }
        }

        DelayCard(
            delay = if (affectedSubtitle == SubtitleDelayType.Secondary) secondaryDelay else delay,
            onDelayChange = {
                if (affectedSubtitle == SubtitleDelayType.Secondary) secondaryDelay = it else delay = it
            },
            onApply = { /* saved externally */ },
            onReset = {
                delay = 0
                secondaryDelay = 0
                speed = 1f
            },
            title = {
                SubtitleDelayTitle(
                    affectedSubtitle = affectedSubtitle,
                    onClose = onDismissRequest,
                    onTypeChange = { affectedSubtitle = it },
                )
            },
            extraSettings = {
                if (affectedSubtitle == SubtitleDelayType.Primary) {
                    OutlinedNumericChooser(
                        label = { Text(stringResource(R.string.inline_speed_425657d), color = Color.White) },
                        value = speed,
                        onChange = { speed = round(it * 1000) / 1000f },
                        max = 10f,
                        step = .05f,
                        min = .1f,
                    )
                }
            },
            delayType = DelayType.Subtitle,
            modifier = Modifier.constrainAs(delayControlCard) {
                linkTo(parent.top, parent.bottom, bias = 0.8f)
                end.linkTo(parent.end)
            },
        )
    }
}

@Composable
private fun SubtitleDelayTitle(
    affectedSubtitle: SubtitleDelayType,
    onClose: () -> Unit,
    onTypeChange: (SubtitleDelayType) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDropDown by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.inline_subtitle_delay_9b49ab2),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Close,
                    contentDescription = stringResource(R.string.kapat),
                    modifier = Modifier.size(26.dp),
                    tint = Color.White,
                )
            }
        }

        // Popup'ı dar başlık metnine değil, kart genişliğindeki ayrı seçim alanına
        // bağla. Sabit menü genişliği eski tam-ekran/taşan görünümü engeller.
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .clickable { showDropDown = true }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Etkilenen altyazı",
                        color = Color.White.copy(alpha = 0.58f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = affectedSubtitle.label,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Icon(
                    imageVector = Icons.ArrowDropDown,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                )
            }

            KitsugiDropdownMenu(
                expanded = showDropDown,
                onDismissRequest = { showDropDown = false },
                modifier = Modifier.width(280.dp),
            ) {
                SubtitleDelayType.entries.forEach { type ->
                    KitsugiMenuItem(
                        text = { Text(type.label, color = Color.White) },
                        selected = type == affectedSubtitle,
                        onClick = {
                            onTypeChange(type)
                            showDropDown = false
                        },
                    )
                }
            }
        }
    }
}
