package com.kitsugi.animelist.ui.screens.stream
import com.kitsugi.animelist.ui.components.KitsugiButton

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsugi.animelist.ui.components.KitsugiEmptyState
import com.kitsugi.animelist.ui.components.KitsugiErrorState
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors

import com.kitsugi.animelist.ui.components.KitsugiPlasmaLoader
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

/** Shows a pulsing spinner while the IMDb ID is being resolved. */
@Composable
fun ResolvingIdState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        KitsugiPlasmaLoader(size = 46.dp)
        Text(stringResource(R.string.inline_resolving_imdb_id_bb96c5b), color = KitsugiColors.TextSecondary)
    }
}

/** Shows a generic error icon + message with an optional retry button. */
@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)?) {
    KitsugiErrorState(
        message = message,
        modifier = Modifier.padding(top = 40.dp),
        onRetryClick = onRetry
    )
}

/** Prompt shown when no compatible addons are installed. */
@Composable
fun NoAddonsState(onOpenSettings: (() -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        KitsugiEmptyState(
            title = stringResource(R.string.uyumlu_eklenti_bulunamadi),
            subtitle = stringResource(R.string.ayarlar_eklentiler_bolumunden_torrentio_dizilla_veya_baska_bir_e),
            icon = Icons.Extension,
            modifier = Modifier.padding(top = 30.dp)
        )
        if (onOpenSettings != null) {
            Spacer(modifier = Modifier.height(16.dp))
            KitsugiButton(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.inline_manage_extensions_settings_3530be1), fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Prompt shown when all addons finished loading but returned no streams. */
@Composable
fun EmptyStreamsState(onOpenSettings: (() -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        KitsugiEmptyState(
            title = stringResource(R.string.inline_no_streams_found_d834d54),
            subtitle = stringResource(R.string.tum_eklentiler_denendi_farkli_bir_bolum_veya_daha_fazla_eklenti),
            icon = Icons.SearchOff,
            modifier = Modifier.padding(top = 30.dp)
        )
        if (onOpenSettings != null) {
            Spacer(modifier = Modifier.height(16.dp))
            KitsugiButton(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Extension, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.daha_fazla_eklenti_ekle), fontWeight = FontWeight.Bold)
            }
        }
    }
}
