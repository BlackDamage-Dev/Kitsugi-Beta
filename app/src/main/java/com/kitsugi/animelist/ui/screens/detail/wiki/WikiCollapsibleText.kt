package com.kitsugi.animelist.ui.screens.detail.wiki

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.icons.KitsugiIcon as Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.components.animateDetailContentSize
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.utils.tvClickable

/**
 * Wiki özeti metinleri için "Daha fazla / Daha az" davranışı — detay sayfasındaki Açıklama
 * kartıyla birebir aynı: uzun metin 110 dp'de kesilir, altında yüzey renginde bir geçiş
 * gösterilir ve alttaki düğmeyle açılıp kapanır.
 *
 * Açık/kapalı durumu `remember` ile tutulur; orijinal/çeviri geçişinde metin değişse de
 * kullanıcının seçimi korunur.
 *
 * @param text kısaltma kararının verileceği, şu an gösterilen metin.
 * @param content metni çizen composable (SelectionContainer + Text vb.).
 */
@Composable
internal fun WikiCollapsibleText(
    text: String,
    accentColor: Color,
    content: @Composable () -> Unit
) {
    val isLongText = text.length > 200 || text.count { it == '\n' } >= 4
    var isExpanded by remember { mutableStateOf(false) }
    val surfaceColor = KitsugiColors.Surface

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .animateDetailContentSize()
                .then(
                    if (isLongText && !isExpanded) Modifier.heightIn(max = 110.dp).clipToBounds()
                    else Modifier
                )
        ) {
            content()
            if (isLongText && !isExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(colors = listOf(Color.Transparent, surfaceColor)))
                )
            }
        }

        if (isLongText) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .tvClickable(shape = RoundedCornerShape(8.dp)) { isExpanded = !isExpanded }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.VisibilityOff else Icons.Visibility,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(
                        if (isExpanded) R.string.action_see_less else R.string.action_see_more
                    ),
                    color = accentColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
