package com.kitsugi.animelist.ui.screens.fullscreen.controls.components.panels
import com.kitsugi.animelist.ui.components.KitsugiButton

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.utils.kitsugiVerticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.screens.fullscreen.controls.components.OutlinedNumericChooser
import com.kitsugi.animelist.ui.theme.KitsugiColors
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

enum class DelayType { Audio, Subtitle }

val CARDS_MAX_WIDTH = 420.dp

@Composable
fun panelCardsColors() = CardDefaults.cardColors(
    containerColor = com.kitsugi.animelist.ui.screens.fullscreen.playerSurfaceColor(0.96f),
    contentColor = Color.White,
)

@Suppress("LambdaParameterInRestartableEffect")
@Composable
fun DelayCard(
    delay: Int,
    onDelayChange: (Int) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    title: @Composable () -> Unit,
    delayType: DelayType,
    modifier: Modifier = Modifier,
    extraSettings: @Composable ColumnScope.() -> Unit = {},
) {
    Card(
        modifier = modifier
            .widthIn(max = CARDS_MAX_WIDTH)
            .animateContentSize(),
        colors = panelCardsColors(),
        shape = RoundedCornerShape(16.dp),
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(
                modifier = Modifier
                    .kitsugiVerticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                title()

                OutlinedNumericChooser(
                    label = { Text("Gecikme", color = Color.White) },
                    value = delay,
                    onChange = onDelayChange,
                    step = 50,
                    min = Int.MIN_VALUE,
                    max = Int.MAX_VALUE,
                    suffix = { Text("ms", color = Color.White) },
                )

                Column(modifier = Modifier.animateContentSize()) { extraSettings() }

                // True = heard→spotted (audio forward), false = spotted→heard (audio backward)
                var isDirectionPositive by remember { mutableStateOf<Boolean?>(null) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    var finalDelay by remember { mutableIntStateOf(delay) }

                    // `!!` kullanılmıyor: yön ve başlangıç zamanı yerel kopyalarda tutuluyor.
                    // Effect, isDirectionPositive her değiştiğinde yeniden başlar; bu yüzden
                    // yerel `direction` coroutine boyunca sabittir ve null olamaz.
                    LaunchedEffect(isDirectionPositive) {
                        val direction = isDirectionPositive
                        if (direction == null) {
                            onDelayChange(finalDelay)
                            return@LaunchedEffect
                        }
                        finalDelay = delay
                        val startedAt = System.currentTimeMillis()
                        val startingDelay = finalDelay
                        while (true) {
                            val elapsed = System.currentTimeMillis() - startedAt
                            finalDelay = startingDelay + (if (direction) elapsed else -elapsed).toInt()
                            delay(20)
                        }
                    }

                    val (labelA, labelB) = if (delayType == DelayType.Audio) {
                        "Duyulan ses" to "Algılanan ses"
                    } else {
                        "Duyulan ses" to "Görülen metin"
                    }

                    KitsugiButton(
                        onClick = {
                            isDirectionPositive = if (isDirectionPositive == null) delayType == DelayType.Audio else null
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KitsugiColors.Accent,
                            contentColor = com.kitsugi.animelist.ui.theme.onAccentColor(KitsugiColors.Accent),
                            disabledContainerColor = KitsugiColors.Accent.copy(alpha = 0.5f),
                            disabledContentColor = com.kitsugi.animelist.ui.theme.onAccentColor(KitsugiColors.Accent).copy(alpha = 0.5f)
                        ),
                    ) {
                        Text(labelA, fontWeight = FontWeight.Bold, color = com.kitsugi.animelist.ui.theme.onAccentColor(KitsugiColors.Accent))
                    }

                    KitsugiButton(
                        onClick = {
                            isDirectionPositive = if (isDirectionPositive == null) delayType != DelayType.Audio else null
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KitsugiColors.Accent,
                            contentColor = com.kitsugi.animelist.ui.theme.onAccentColor(KitsugiColors.Accent),
                            disabledContainerColor = KitsugiColors.Accent.copy(alpha = 0.5f),
                            disabledContentColor = com.kitsugi.animelist.ui.theme.onAccentColor(KitsugiColors.Accent).copy(alpha = 0.5f)
                        ),
                    ) {
                        Text(labelB, fontWeight = FontWeight.Bold, color = com.kitsugi.animelist.ui.theme.onAccentColor(KitsugiColors.Accent))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KitsugiButton(
                        onClick = onApply,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KitsugiColors.Accent,
                            contentColor = com.kitsugi.animelist.ui.theme.onAccentColor(KitsugiColors.Accent)
                        ),
                    ) {
                        Text(stringResource(R.string.inline_set_as_default_03a9077), fontWeight = FontWeight.Bold, color = com.kitsugi.animelist.ui.theme.onAccentColor(KitsugiColors.Accent))
                    }
                    FilledIconButton(
                        onClick = onReset,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = KitsugiColors.Accent,
                            contentColor = com.kitsugi.animelist.ui.theme.onAccentColor(KitsugiColors.Accent)
                        )
                    ) {
                        Icon(Icons.Refresh, null, tint = com.kitsugi.animelist.ui.theme.onAccentColor(KitsugiColors.Accent))
                    }
                }
            }
        }
    }
}
