package com.kitsugi.animelist.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.auth.SourceSyncStatus
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.local.MediaEntryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * "Arka planda ne oluyor?" kartı (v2.4.341).
 *
 * Tüm liste kaynaklarının (AniList · MyAnimeList · Kitsu · Shikimori · Simkl · Bangumi)
 * eşitleme durumunu **kaynak kaynak, halk diliyle** gösterir ve bekleyen değişiklikleri
 * tek düğmeyle göndermeyi dener.
 *
 * Liste ekranı aynı bilgiyi kendi şeridinde gösterir; bu kart **bildirim** ve benzeri
 * ekranlarda kullanılır. Hiçbir sorun yoksa hiç çizilmez (yer kaplamaz).
 */
@Composable
fun SourceSyncStatusCard(
    modifier: Modifier = Modifier,
    refreshIntervalMs: Long = 10_000L
) {
    val context = LocalContext.current
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var pending by remember { mutableStateOf(0) }
    var isError by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }

    LaunchedEffect(tick) {
        // "Şimdi dene" tıklandıysa önce kuyruğu zorla boşalt, sonra durumu oku.
        if (tick > 0) {
            withContext(Dispatchers.IO) { retryPending(context) }
        }
        while (true) {
            val result = withContext(Dispatchers.IO) { buildStatusLines(context) }
            lines = result.lines
            pending = result.pending
            isError = result.isError
            delay(refreshIntervalMs)
        }
    }

    if (lines.isEmpty()) return

    // Bilgi satırları (kapalı / bağlı değil) kırmızı hata gibi görünmesin: v2.4.341.
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.sync_status_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor
                )
                lines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            if (pending > 0) {
                TextButton(
                    onClick = {
                        // Kuyruğu zorla boşalt ve durumu hemen tazele.
                        tick += 1
                    }
                ) {
                    Text(
                        text = stringResource(R.string.sync_retry_now),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

/** Kartta gösterilecek hazır durum: satırlar, toplam bekleyen, gerçek sorun var mı. */
private data class CardState(
    val lines: List<String> = emptyList(),
    val pending: Int = 0,
    val isError: Boolean = false
)

/** Tüm kaynaklar için insan dilinde durum satırları ve toplam bekleyen sayısı. */
private suspend fun buildStatusLines(context: Context): CardState {
    val db = runCatching { KitsugiDatabase.getDatabase(context) }.getOrNull() ?: return CardState()
    val repository = MediaEntryRepository(
        dao = db.mediaEntryDao(),
        pendingSyncDao = db.pendingSyncDao(),
        context = context
    )
    val perSource = runCatching { repository.pendingSyncBySource() }.getOrDefault(emptyMap())
    val snapshots = SourceSyncStatus.snapshotAll(context)
    val lines = mutableListOf<String>()
    var total = 0
    var anyError = false
    SourceSyncStatus.Source.entries.forEach { source ->
        val count = perSource[source] ?: 0
        total += count
        val snapshot = snapshots[source] ?: SourceSyncStatus.Snapshot()
        val text = SourceSyncStatus.describe(
            context = context,
            source = source,
            snapshot = snapshot,
            pending = count
        )
        if (text.isNotBlank()) {
            lines += text
            if (SourceSyncStatus.isError(snapshot.reason)) anyError = true
        }
    }
    return CardState(lines = lines.take(5), pending = total, isError = anyError)
}

private suspend fun retryPending(context: Context) {
    val db = runCatching { KitsugiDatabase.getDatabase(context) }.getOrNull() ?: return
    val repository = MediaEntryRepository(
        dao = db.mediaEntryDao(),
        pendingSyncDao = db.pendingSyncDao(),
        context = context
    )
    runCatching { repository.retryPendingSyncNow() }
}
