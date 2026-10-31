package com.kitsugi.animelist.ui.components.addons

import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.local.CsPluginEntity
import com.kitsugi.animelist.data.remote.CsPlugin
import com.kitsugi.animelist.ui.theme.KitsugiColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CsPluginRow(
    plugin: CsPlugin,
    installState: PluginInstallState,
    isInstalled: Boolean,
    installedVersion: Int?,
    installedPlugin: CsPluginEntity?,
    accentColor: Color,
    onInstallPlugin: (CsPlugin, ((Boolean) -> Unit)?) -> Unit,
    onStartInstall: () -> Unit,
    onInstallResult: (Boolean) -> Unit
) {
    val isStremio = plugin.url.trim().endsWith("/manifest.json", ignoreCase = true)

    // Güncelleme kararı (sürüm numarası VEYA dosya içerik parmak izi) TEK merkezden gelir;
    // böylece bu satır, kurulu eklenti sekmesindeki "güncelle" göstergesinden ayrışamaz.
    val rowContext = LocalContext.current
    val rowHasUpdate = installedPlugin != null &&
        com.kitsugi.animelist.data.cloudstream.CsPluginUpdateChecker
            .hasUpdate(rowContext, installedPlugin, plugin)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KitsugiColors.Surface)
            .border(1.dp, KitsugiColors.Border.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accentColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            var isError by remember { mutableStateOf(false) }
            if (!plugin.iconUrl.isNullOrBlank() && !isError) {
                AsyncImage(
                    model = plugin.iconUrl.replace("%size%", "128").replace("%exact_size%", "128"),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onError = { isError = true }
                )
            } else {
                Icon(Icons.Extension, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = plugin.name,
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )

                when (plugin.status) {
                    0 -> {
                        Text(
                            text = "🔴 Down",
                            color = KitsugiColors.AccentRed,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(KitsugiColors.AccentRed.copy(alpha = 0.12f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    2 -> {
                        Text(
                            text = "🟡 Beta",
                            color = KitsugiColors.AccentOrange,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(KitsugiColors.AccentOrange.copy(alpha = 0.12f))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                val verText = if (installedVersion != null) {
                    when {
                        rowHasUpdate && installedVersion < plugin.version -> "v$installedVersion → v${plugin.version}"
                        // Sürüm aynı ama dosya değişmiş (yeniden derlenmiş eklenti)
                        rowHasUpdate -> "v${plugin.version} ⟳"
                        else -> "v${plugin.version}"
                    }
                } else {
                    "v${plugin.version}"
                }

                Text(
                    text = verText,
                    color = accentColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.12f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )

                if (plugin.fileSize != null) {
                    Text(
                        text = formatFileSize(plugin.fileSize),
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(KitsugiColors.SurfaceSoft)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            if (!plugin.description.isNullOrBlank()) {
                Text(
                    plugin.description,
                    color = KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            if (plugin.status == 2) {
                Text(
                    text = stringResource(R.string.inline_this_extension_is_in_beta_1b5de5b),
                    color = KitsugiColors.AccentOrange,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (!plugin.language.isNullOrBlank()) {
                Text(
                    "🌐 ${plugin.language?.uppercase()}",
                    color = KitsugiColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Spacer(Modifier.width(8.dp))

        when (installState) {
            PluginInstallState.LOADING -> {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = accentColor, strokeWidth = 2.5.dp)
            }
            PluginInstallState.SUCCESS -> {
                Text(
                    text = stringResource(R.string.basarili),
                    color = KitsugiColors.AccentGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(KitsugiColors.AccentGreen.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            PluginInstallState.FAILURE -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.hata_37fcfb3),
                        color = KitsugiColors.AccentRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(KitsugiColors.AccentRed.copy(alpha = 0.13f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Text(
                        text = "Tekrar",
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .tvClickable(shape = RoundedCornerShape(8.dp)) {
                                onStartInstall()
                                onInstallPlugin(plugin) { success ->
                                    onInstallResult(success)
                                }
                            }
                            .background(accentColor.copy(alpha = 0.13f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            PluginInstallState.IDLE -> {
                // `installedVersion` null olabilir (entity yok); o durumda `rowHasUpdate` zaten false.
                val hasUpdate = !isStremio && isInstalled && rowHasUpdate
                if (hasUpdate) {
                    Text(
                        text = stringResource(R.string.inline_update_27ef47d),
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .tvClickable(shape = RoundedCornerShape(8.dp)) {
                                onStartInstall()
                                onInstallPlugin(plugin) { success ->
                                    onInstallResult(success)
                                }
                            }
                            .background(accentColor.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                } else if (isInstalled && !isStremio) {
                    val localContext = LocalContext.current
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "✓ Kuruldu",
                            color = KitsugiColors.AccentGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(KitsugiColors.AccentGreen.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        val isEnabled = installedPlugin?.enabled == true
                        val loadedPluginIds by com.kitsugi.animelist.data.cloudstream.CsPluginLoader.loadedPluginIds.collectAsState()
                        val pluginHasSettings = remember(loadedPluginIds, plugin.internalName, isInstalled, isEnabled) {
                            isInstalled && isEnabled && com.kitsugi.animelist.data.cloudstream.CsPluginLoader.hasSettings(plugin.internalName, localContext)
                        }
                        val scope = androidx.compose.runtime.rememberCoroutineScope()
                        var isSettingsOpening by remember { mutableStateOf(false) }
                        if (pluginHasSettings) {
                            Icon(
                                imageVector = Icons.Settings,
                                contentDescription = stringResource(R.string.eklenti_ayarlari),
                                tint = accentColor,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .tvClickable(shape = RoundedCornerShape(6.dp)) {
                                        if (isSettingsOpening) return@tvClickable
                                        isSettingsOpening = true
                                        scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                            try {
                                                var loadedPlugin = com.kitsugi.animelist.data.cloudstream.CsPluginLoader
                                                    .getPluginInstance(plugin.internalName, localContext)
                                                if (loadedPlugin == null && isEnabled) {
                                                    com.kitsugi.animelist.data.cloudstream.CsPluginLoader.loadExtension(localContext, plugin.internalName)
                                                    loadedPlugin = com.kitsugi.animelist.data.cloudstream.CsPluginLoader
                                                        .getPluginInstance(plugin.internalName, localContext)
                                                }
                                                val baseCtx = com.kitsugi.animelist.KitsugiApplication.activeActivity as? androidx.appcompat.app.AppCompatActivity
                                                if (loadedPlugin != null && baseCtx != null) {
                                                    val themedCtx = com.kitsugi.animelist.data.cloudstream.CsPluginContextWrapper(
                                                        baseCtx,
                                                        com.kitsugi.animelist.R.style.Theme_KitsugiAnimeList
                                                    )
                                                    loadedPlugin.openSettings?.invoke(themedCtx)
                                                } else {
                                                    android.widget.Toast.makeText(localContext, KitsugiStrings.get(R.string.eklenti_veya_ekran_hazir_degil), android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Throwable) {
                                                android.util.Log.e("KitsugiAddons", "Ayarlar açılamadı: ${e.message}", e)
                                            } finally {
                                                kotlinx.coroutines.delay(1000)
                                                isSettingsOpening = false
                                            }
                                        }
                                    }
                                    .padding(2.dp)
                            )
                        }
                        Icon(
                            imageVector = Icons.Refresh,
                            contentDescription = stringResource(R.string.yeniden_kur),
                            tint = KitsugiColors.TextMuted,
                            modifier = Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .tvClickable(shape = RoundedCornerShape(6.dp)) {
                                    onStartInstall()
                                    onInstallPlugin(plugin) { success ->
                                        onInstallResult(success)
                                    }
                                }
                                .padding(2.dp)
                        )
                    }
                } else if (isInstalled) {
                    Text(
                        text = "✓ Kuruldu",
                        color = KitsugiColors.AccentGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(KitsugiColors.AccentGreen.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                } else {
                    if (plugin.status == 0) {
                        Text(
                            text = "Kur",
                            color = KitsugiColors.TextMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(KitsugiColors.SurfaceSoft)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    } else {
                        Text(
                            text = "Kur",
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .tvClickable(shape = RoundedCornerShape(8.dp)) {
                                    onStartInstall()
                                    onInstallPlugin(plugin) { success ->
                                        onInstallResult(success)
                                    }
                                }
                                .background(accentColor.copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
