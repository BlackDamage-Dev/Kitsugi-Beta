package com.kitsugi.animelist.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import com.kitsugi.animelist.ui.utils.kitsugiVerticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.auth.TmdbAuthStore
import com.kitsugi.animelist.data.platform.PlatformFailureRegistry
import com.kitsugi.animelist.data.remote.TmdbAccountClient
import com.kitsugi.animelist.data.remote.TmdbListSummary
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.ui.theme.KitsugiColors
import kotlinx.coroutines.launch

private data class TmdbListRow(
    val summary: TmdbListSummary,
    val containsMovie: Boolean
)

/**
 * Native v3 custom-list editor. v3 item mutations accept movies only; this dialog is never
 * shown as if it could write TV or mixed/private v4 lists with a v3 session.
 */
@Composable
fun KitsugiTmdbListsDialog(
    entry: MediaEntry,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionId = remember { TmdbAuthStore.sessionId(context) }
    val accountId = remember { TmdbAuthStore.accountId(context) }
    val tmdbId = entry.tmdbId?.takeIf { it > 0 }
    var rows by remember(entry.id, entry.tmdbId) { mutableStateOf<List<TmdbListRow>>(emptyList()) }
    var loading by remember(entry.id, entry.tmdbId) { mutableStateOf(true) }
    var busyListIds by remember(entry.id, entry.tmdbId) { mutableStateOf<Set<Int>>(emptySet()) }
    var creating by remember(entry.id, entry.tmdbId) { mutableStateOf(false) }
    var newListName by remember(entry.id, entry.tmdbId) { mutableStateOf("") }
    var error by remember(entry.id, entry.tmdbId) { mutableStateOf<String?>(null) }

    fun ensureActiveAccount() {
        val activeSession = sessionId
        val activeAccount = accountId
        if (activeSession.isNullOrBlank() || activeAccount == null ||
            !TmdbAuthStore.matchesSession(context, activeSession, activeAccount)
        ) {
            throw java.io.IOException(context.getString(R.string.account_sync_tmdb_account_changed))
        }
    }

    LaunchedEffect(entry.id, entry.tmdbId, sessionId, accountId) {
        loading = true
        error = null
        if (entry.type != MediaType.Movie || tmdbId == null || sessionId.isNullOrBlank() || accountId == null) {
            loading = false
            error = context.getString(R.string.tmdb_lists_invalid)
            return@LaunchedEffect
        }
        runCatching {
            ensureActiveAccount()
            val lists = TmdbAccountClient.fetchLists(sessionId, accountId)
            // Official item_status calls are deliberately sequential to avoid bursting the
            // account rate limit when a user owns many lists.
            lists.map { summary ->
                ensureActiveAccount()
                TmdbListRow(
                    summary = summary,
                    containsMovie = TmdbAccountClient.isMovieInList(sessionId, summary.id, tmdbId)
                )
            }.also { ensureActiveAccount() }
        }.onSuccess {
            rows = it
        }.onFailure {
            if (it is kotlinx.coroutines.CancellationException) throw it
            PlatformFailureRegistry.reportAndResolve("tmdb", it)
            error = it.message ?: context.getString(R.string.tmdb_lists_read_error)
        }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tmdb_editor_custom_lists)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .kitsugiVerticalScroll(rememberScrollState())
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.tmdb_lists_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = KitsugiColors.TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                when {
                    loading -> {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Text(
                            text = stringResource(R.string.tmdb_lists_loading),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    rows.isEmpty() && error == null -> Text(
                        text = stringResource(R.string.tmdb_lists_empty),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    else -> rows.forEach { row ->
                        val busy = row.summary.id in busyListIds
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = row.containsMovie,
                                enabled = !busy && !creating,
                                onCheckedChange = { checked ->
                                    val activeSession = sessionId ?: return@Checkbox
                                    val movieId = tmdbId ?: return@Checkbox
                                    busyListIds = busyListIds + row.summary.id
                                    error = null
                                    scope.launch {
                                        runCatching {
                                            ensureActiveAccount()
                                            if (checked) {
                                                TmdbAccountClient.addMovieToList(
                                                    activeSession,
                                                    row.summary.id,
                                                    movieId
                                                )
                                            } else {
                                                TmdbAccountClient.removeMovieFromList(
                                                    activeSession,
                                                    row.summary.id,
                                                    movieId
                                                )
                                            }
                                            ensureActiveAccount()
                                        }.onSuccess {
                                            rows = rows.map { current ->
                                                if (current.summary.id == row.summary.id) {
                                                    current.copy(
                                                        containsMovie = checked,
                                                        summary = current.summary.copy(
                                                            itemCount = (current.summary.itemCount +
                                                                if (checked) 1 else -1).coerceAtLeast(0)
                                                        )
                                                    )
                                                } else current
                                            }
                                        }.onFailure {
                                            if (it is kotlinx.coroutines.CancellationException) throw it
                                            PlatformFailureRegistry.reportAndResolve("tmdb", it)
                                            error = it.message ?: context.getString(R.string.tmdb_lists_membership_error)
                                        }
                                        busyListIds = busyListIds - row.summary.id
                                    }
                                }
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = row.summary.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(R.string.tmdb_lists_item_count, row.summary.itemCount) +
                                        row.summary.description.takeIf { it.isNotBlank() }
                                            ?.let { " • $it" }.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KitsugiColors.TextSecondary,
                                    maxLines = 2
                                )
                            }
                            if (busy) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text(
                    text = stringResource(R.string.tmdb_lists_create_section),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = newListName,
                    onValueChange = { newListName = it },
                    enabled = !creating,
                    singleLine = true,
                    label = { Text(stringResource(R.string.tmdb_lists_name)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    enabled = newListName.isNotBlank() && !creating && !loading &&
                        !sessionId.isNullOrBlank() && accountId != null,
                    onClick = {
                        val activeSession = sessionId ?: return@OutlinedButton
                        creating = true
                        error = null
                        scope.launch {
                            runCatching {
                                ensureActiveAccount()
                                TmdbAccountClient.createList(activeSession, newListName).also {
                                    ensureActiveAccount()
                                }
                            }.onSuccess { listId ->
                                // A newly created v3 list is empty by definition. Do not turn
                                // a later refresh failure into a misleading "create failed".
                                rows = rows + TmdbListRow(
                                    TmdbListSummary(listId, newListName.trim(), "", 0),
                                    containsMovie = false
                                )
                                newListName = ""
                            }.onFailure {
                                if (it is kotlinx.coroutines.CancellationException) throw it
                                PlatformFailureRegistry.reportAndResolve("tmdb", it)
                                error = it.message ?: context.getString(R.string.tmdb_lists_create_error)
                            }
                            creating = false
                        }
                    }
                ) {
                    if (creating) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.tmdb_lists_create_button))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}
