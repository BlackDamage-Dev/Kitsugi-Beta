package com.kitsugi.animelist.ui.screens.detail

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.gradient.Text

internal const val DETAIL_LIST_PAGE_SIZE = 12

/**
 * Sonsuz-yükseklikte ölçülen detay pager'ı için küçük, kontrollü liste artışı.
 * İç içe bir LazyColumn eklemeden ilk sekme ölçümündeki kart ve görsel sayısını sınırlar.
 */
@Composable
internal fun DetailLoadMoreButton(
    remainingCount: Int,
    onClick: () -> Unit
) {
    if (remainingCount <= 0) return
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
    ) {
        Text(
            text = "${stringResource(R.string.inline_load_more_dc765e9)} (+$remainingCount)",
            color = LocalKitsugiAccent.current,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}
