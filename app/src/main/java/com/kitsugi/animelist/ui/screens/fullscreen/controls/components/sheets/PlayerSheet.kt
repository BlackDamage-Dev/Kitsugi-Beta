package com.kitsugi.animelist.ui.screens.fullscreen.controls.components.sheets
import com.kitsugi.animelist.ui.screens.fullscreen.playerSurfaceColor

import androidx.activity.compose.BackHandler
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

/**
 * Kitsugi'ye uyarlanmış PlayerSheet sarmalayıcı.
 * Artık sürükleyici modu bozmayan ve yerel hiyerarşide çalışan özel bir bileşendir.
 */
@Composable
fun PlayerSheet(
    onDismissRequest: () -> Unit,
    dismissEvent: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onDismissRequest)

    LaunchedEffect(dismissEvent) {
        if (dismissEvent) {
            onDismissRequest()
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val store = androidx.compose.runtime.remember(context) { com.kitsugi.animelist.data.settings.SettingsDataStore(context.applicationContext) }
    val settings by store.settingsFlow.collectAsState(initial = com.kitsugi.animelist.data.settings.AppSettings())
    val sheetAlpha = com.kitsugi.animelist.core.player.PlayerUiPolicy.panelAlpha(settings.panelOpacity)

    val sheetSurface = com.kitsugi.animelist.ui.screens.fullscreen.playerSurfaceColor()
    val sheetAccent = com.kitsugi.animelist.ui.theme.KitsugiColors.Accent
    androidx.compose.material3.MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            background = sheetSurface,
            onBackground = Color.White,
            surface = sheetSurface,
            onSurface = Color.White,
            surfaceVariant = androidx.compose.ui.graphics.lerp(sheetSurface, Color.White, 0.10f),
            onSurfaceVariant = Color.White,
            primary = sheetAccent,
            onPrimary = com.kitsugi.animelist.ui.screens.fullscreen.playerOnAccentColor()
        )
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .widthIn(max = 600.dp), // landscape/tablet genişlik sınırı
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = sheetSurface.copy(alpha = sheetAlpha),
            contentColor = Color.White,
        ) {
            Column(
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                // Sürükleme kolu taklidi (Drag Handle) + sağ üstte kapatma çarpısı.
                // Oynatıcı içindeki TÜM alttan açılır sayfalarda tek dokunuşluk çıkış garantisi:
                // çarpısı olmayan sheet'e buradan otomatik olarak eklenir.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, end = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(vertical = 6.dp)
                            .size(width = 36.dp, height = 4.dp)
                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                    )
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Close,
                            contentDescription = stringResource(R.string.kapat),
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                content()
            }
        }
    }
}
