package com.kitsugi.animelist.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.icons.KitsugiEmojiIcons

/**
 * ─────────────────────────────────────────────────────────────────────────────
 * KitsugiEmojiLabel — emoji önekli etiketleri HD vektör ikonla çizen tek birim
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * Filtre çipleri, sıralama seçenekleri ve tür etiketleri veri katmanından
 * `"🔥 Popülerlik"`, `"🤖 Mecha"` gibi **emoji önekli** metinlerle geliyor.
 * Bu bileşen etiketin başındaki emojiyi [KitsugiEmojiIcons] tablosundan HD
 * ikona çevirir; ikon yoksa (ör. ülke bayrağı) metin olduğu gibi kalır.
 *
 * Böylece her çip aynı tipografi, aynı ikon boyu ve aynı boşlukla çizilir.
 */
@Composable
fun KitsugiEmojiLabel(
    label: String,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelSmall,
    fontWeight: FontWeight? = null,
    iconSize: Dp = 12.dp,
    icon: ImageVector? = null
) {
    val resolvedIcon = icon ?: KitsugiEmojiIcons.forLabel(label)
    val text = if (resolvedIcon != null) KitsugiEmojiIcons.stripLeadingEmoji(label) else label
    val effectiveColor = if (color != Color.Unspecified) color else if (style.color != Color.Unspecified) style.color else androidx.compose.material3.LocalContentColor.current

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (resolvedIcon != null) {
            Icon(
                imageVector = resolvedIcon,
                contentDescription = null,
                tint = effectiveColor,
                modifier = Modifier.size(iconSize)
            )
        }
        Text(
            text = text,
            color = effectiveColor,
            style = style,
            fontWeight = fontWeight,
            maxLines = 1
        )
    }
}

@Composable
fun KitsugiEmojiLabel(
    label: Number,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelSmall,
    fontWeight: FontWeight? = null,
    iconSize: Dp = 12.dp,
    icon: ImageVector? = null
) = KitsugiEmojiLabel(
    label = label.toString(),
    color = color,
    modifier = modifier,
    style = style,
    fontWeight = fontWeight,
    iconSize = iconSize,
    icon = icon
)
