package com.kitsugi.animelist.ui.screens.detail.wiki

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.R
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.collect

@Composable
internal fun WikiFindBar(session: WikiOriginalSession) {
    // Native WebView search highlights text without rewriting the translated DOM.
    LaunchedEffect(session, session.findOpen, session.findQuery, session.generation) {
        if (!session.findOpen) return@LaunchedEffect
        session.findMatches = 0
        session.findActiveMatch = 0
        if (session.findQuery.isBlank()) {
            session.view?.clearMatches()
        } else {
            delay(300)
            // Conflate text updates rather than continuously postponing search during
            // a fast cached translation. At most one refresh per 600ms.
            snapshotFlow { session.documentTextRevision to (session.progress == 100) }
                .conflate().collect {
                    session.view?.findAllAsync(session.findQuery)
                    delay(600)
                }
        }
    }
    if (!session.findOpen) return
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val accent = LocalKitsugiAccent.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    // 2026-10-23: arama çubuğu uygulama temasına bağlandı — vurgu renkli odak
    // çerçevesi, yuvarlatılmış alan ve sayfa yüzeyinden ayrılan tek parça zemin.
    Column(
        Modifier
            .fillMaxWidth()
            .background(KitsugiColors.Surface)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        OutlinedTextField(
            value = session.findQuery,
            onValueChange = { session.findQuery = it.take(256) },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = KitsugiColors.TextPrimary,
                unfocusedTextColor = KitsugiColors.TextPrimary,
                cursorColor = accent,
                focusedBorderColor = accent.copy(alpha = 0.7f),
                unfocusedBorderColor = KitsugiColors.Border,
                focusedLabelColor = accent,
                unfocusedLabelColor = KitsugiColors.TextSecondary
            ),
            label = { Text(stringResource(R.string.wiki_find_in_page)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                session.view?.findAllAsync(session.findQuery)
                keyboard?.hide()
            })
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (session.findQuery.isBlank()) "" else stringResource(R.string.wiki_find_matches,
                session.findActiveMatch, session.findMatches), modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall, color = KitsugiColors.TextSecondary)
            IconButton(onClick = { session.view?.findNext(false) }, enabled = session.findMatches > 0) {
                Icon(Icons.KeyboardArrowUp, stringResource(R.string.wiki_find_previous), tint = if (session.findMatches > 0) KitsugiColors.TextSecondary else KitsugiColors.TextMuted)
            }
            IconButton(onClick = { session.view?.findNext(true) }, enabled = session.findMatches > 0) {
                Icon(Icons.KeyboardArrowDown, stringResource(R.string.wiki_find_next), tint = if (session.findMatches > 0) KitsugiColors.TextSecondary else KitsugiColors.TextMuted)
            }
            IconButton(onClick = { session.closeFind(); keyboard?.hide() }) {
                Icon(Icons.Close, stringResource(R.string.wiki_find_close), tint = KitsugiColors.TextPrimary)
            }
        }
    }
}
