package com.kitsugi.animelist.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.ui.theme.KitsugiColors
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

/**
 * Çapraz eşitleme yüzeylerinde gösterilen yasal uyarılar.
 *
 * Metinler tek kaynak olarak burada tutulur; ekranlar bu sabitleri kullanır.
 * Not: Bu metinler hukuki danışmanlık yerine geçmez; yayın öncesi bir hukukçu tarafından gözden geçirilmelidir.
 */
/**
 * Uyarı metnini turuncu bir ikonla birlikte gösterir.
 *
 * @param full true ise ayrıntılı metin, false ise kısa metin kullanılır.
 */
@Composable
fun CrossSyncDisclaimerText(
    modifier: Modifier = Modifier,
    full: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.WarningAmber,
            contentDescription = null,
            tint = KitsugiColors.AccentOrange,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(
                text = stringResource(if (full) R.string.cross_sync_disclaimer_full else R.string.cross_sync_disclaimer_short),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                color = KitsugiColors.TextMuted
            )
        }
    }
}
