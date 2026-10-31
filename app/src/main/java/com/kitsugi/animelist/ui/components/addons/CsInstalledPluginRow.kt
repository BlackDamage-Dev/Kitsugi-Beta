package com.kitsugi.animelist.ui.components.addons
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.components.KitsugiButton

import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Switch
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kitsugi.animelist.data.local.CsPluginEntity
import com.kitsugi.animelist.data.remote.CsPlugin
import com.kitsugi.animelist.ui.theme.KitsugiColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

@Composable
internal fun CsInstalledPluginRow(
    plugin: CsPluginEntity,
    accentColor: Color,
    reinstallState: PluginInstallState,
    latestPlugin: CsPlugin? = null,
    onInstallPlugin: (CsPlugin, ((Boolean) -> Unit)?) -> Unit,
    onToggleCsPlugin: (CsPluginEntity, Boolean) -> Unit,
    onUninstallCsPlugin: (CsPluginEntity) -> Unit,
    onVerifyPlugin: (pluginId: String, pluginName: String) -> Unit,
    onStartReinstall: () -> Unit,
    onReinstallResult: (Boolean) -> Unit,
    onExplorePlugin: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSettingsOpening by remember { mutableStateOf(false) }

    // Güncelleme kararı TEK merkezden verilir: sürüm numarası VEYA dosya içerik parmak izi
    // (SHA-256) farkı. Depoda yeniden derlenip sürümü artırılmayan eklentiler de burada
    // "güncellenebilir" görünür. Hesaplama anahtarlarla önbelleklenir; içerik parmak izi
    // yalnızca sürümler eşit olduğunda okunur.
    val updateReason = remember(plugin.id, plugin.version, latestPlugin?.version, latestPlugin?.fileHash) {
        com.kitsugi.animelist.data.cloudstream.CsPluginUpdateChecker.reason(context, plugin, latestPlugin)
    }
    val hasUpdate = updateReason != com.kitsugi.animelist.data.cloudstream.CsPluginUpdateChecker.Reason.NONE
    val contentOnlyUpdate = updateReason == com.kitsugi.animelist.data.cloudstream.CsPluginUpdateChecker.Reason.CONTENT

    // Eklenti etkinse arka planda sessizce yükle (henüz yüklenmemişse),
    // böylece hasSettings reaktif olarak doğru sonuç verebilir.
    LaunchedEffect(plugin.id, plugin.enabled) {
        if (plugin.enabled) {
            val alreadyLoaded = com.kitsugi.animelist.data.cloudstream.CsPluginLoader
                .getPluginInstance(plugin.id, context) != null
            if (!alreadyLoaded) {
                com.kitsugi.animelist.data.cloudstream.CsPluginLoader.loadExtension(context, plugin.id)
            }
        }
    }

    // loadedPluginIds flow'u değişince hasSettings reaktif olarak yeniden hesaplanır.
    val loadedPluginIds by com.kitsugi.animelist.data.cloudstream.CsPluginLoader.loadedPluginIds.collectAsState()
    val hasSettings = remember(loadedPluginIds, plugin.id, plugin.enabled) {
        plugin.enabled && com.kitsugi.animelist.data.cloudstream.CsPluginLoader.hasSettings(plugin.id, context)
    }

    val isCorrupt by produceState(initialValue = false, key1 = plugin.id, key2 = plugin.installedAt) {
        value = withContext(Dispatchers.IO) {
            val file = java.io.File(context.filesDir, "cs_extensions/${plugin.id}.cs3")
            if (!file.exists()) {
                true
            } else {
                try {
                    java.util.zip.ZipFile(file).use { false }
                } catch (_: Exception) {
                    true
                }
            }
        }
    }

    if (isCorrupt) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(KitsugiColors.SurfaceSoft)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(KitsugiColors.AccentRed.copy(alpha = 0.15f)),
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
                    Icon(
                        imageVector = Icons.AlertTriangle,
                        contentDescription = null,
                        tint = KitsugiColors.AccentOrange,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    plugin.name,
                    color = KitsugiColors.AccentRed,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(R.string.inline_verification_error_corrupted_file_0e3c632),
                    color = KitsugiColors.AccentRed.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            when (reinstallState) {
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
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
                PluginInstallState.FAILURE -> {
                    Text(
                        text = "Tekrar Dene",
                        color = KitsugiColors.AccentRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .tvClickable(shape = RoundedCornerShape(8.dp)) {
                                onStartReinstall()
                                val csPlugin = CsPlugin(
                                    name = plugin.name,
                                    internalName = plugin.id,
                                    url = plugin.downloadUrl,
                                    description = "",
                                    version = plugin.version,
                                    language = null,
                                    tvTypes = try {
                                        com.google.gson.Gson().fromJson(plugin.tvTypes, Array<String>::class.java).toList()
                                    } catch (_: Exception) { null },
                                    iconUrl = plugin.iconUrl,
                                    authors = emptyList(),
                                    repositoryUrl = plugin.repositoryUrl
                                )
                                onInstallPlugin(csPlugin) { success ->
                                    onReinstallResult(success)
                                }
                            }
                            .background(KitsugiColors.AccentRed.copy(alpha = 0.13f))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
                PluginInstallState.IDLE -> {
                    Text(
                        text = stringResource(R.string.yeniden_kur),
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .tvClickable(shape = RoundedCornerShape(8.dp)) {
                                onStartReinstall()
                                val csPlugin = CsPlugin(
                                    name = plugin.name,
                                    internalName = plugin.id,
                                    url = plugin.downloadUrl,
                                    description = "",
                                    version = plugin.version,
                                    language = null,
                                    tvTypes = try {
                                        com.google.gson.Gson().fromJson(plugin.tvTypes, Array<String>::class.java).toList()
                                    } catch (_: Exception) { null },
                                    iconUrl = plugin.iconUrl,
                                    authors = emptyList(),
                                    repositoryUrl = plugin.repositoryUrl
                                )
                                onInstallPlugin(csPlugin) { success ->
                                    onReinstallResult(success)
                                }
                            }
                            .background(accentColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = { onUninstallCsPlugin(plugin) }) {
                Icon(Icons.Delete, contentDescription = stringResource(R.string.kaldir), tint = KitsugiColors.AccentRed)
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    if (!plugin.enabled)
                        KitsugiColors.SurfaceSoft.copy(alpha = 0.55f)
                    else
                        KitsugiColors.SurfaceSoft
                )
                .then(
                    if (!plugin.enabled)
                        Modifier.border(1.dp, KitsugiColors.AccentRed.copy(alpha = 0.30f), RoundedCornerShape(18.dp))
                    else
                        Modifier.border(1.dp, KitsugiColors.Border.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                )
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
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
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plugin.name,
                        color = if (!plugin.enabled) KitsugiColors.TextSecondary else KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // "Devre Dışı" badge — otomatik pasifleştirilmiş pluginler için
                    if (!plugin.enabled) {
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(KitsugiColors.AccentRed.copy(alpha = 0.15f))
                                    .border(1.dp, KitsugiColors.AccentRed.copy(alpha = 0.40f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    stringResource(R.string.inline_disabled_2299e09),
                                    color = KitsugiColors.AccentRed,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "v${plugin.version} • ${plugin.id}",
                            color = KitsugiColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (hasUpdate) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(accentColor.copy(alpha = 0.15f))
                                    .border(1.dp, accentColor.copy(alpha = 0.38f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    // İçerik değişikliğinde sürüm numarası aynı kalır; kullanıcıya
                                    // "aynı sürüm" göstermek yanıltıcı olurdu.
                                    if (contentOnlyUpdate) {
                                        "⟳ Yeniden derlendi (v${latestPlugin?.version ?: plugin.version})"
                                    } else {
                                        "⬆️ v${latestPlugin?.version ?: plugin.version} Mevcut"
                                    },
                                    color = accentColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    if (!plugin.tvTypes.isNullOrBlank() && plugin.tvTypes != "[]") {
                        val types = try {
                            com.google.gson.Gson().fromJson(plugin.tvTypes, Array<String>::class.java).take(3).joinToString(", ")
                        } catch (_: Exception) { plugin.tvTypes }
                        Text(
                            text = types,
                            color = accentColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = plugin.enabled,
                    onCheckedChange = { onToggleCsPlugin(plugin, it) }
                )
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { onUninstallCsPlugin(plugin) }) {
                    Icon(Icons.Delete, contentDescription = stringResource(R.string.kaldir), tint = KitsugiColors.AccentRed)
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = KitsugiColors.Border.copy(alpha = 0.5f))
            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (latestPlugin != null && hasUpdate) {
                    KitsugiButton(
                        onClick = {
                            onStartReinstall()
                            onInstallPlugin(latestPlugin) { success ->
                                onReinstallResult(success)
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        if (reinstallState == PluginInstallState.LOADING) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.ArrowCircleUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (contentOnlyUpdate) {
                                // Sürüm aynı, dosya değişti → "güncelle" yerine "yeniden kur" ifadesi doğru.
                                "⟳ Yeniden kur (v${latestPlugin.version})"
                            } else {
                                stringResource(R.string.inline_update_v_1_s_2e5501a, latestPlugin.version)
                            },
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }

                TextButton(
                    onClick = {
                        val csPlugin = CsPlugin(
                            name = plugin.name,
                            internalName = plugin.id,
                            url = plugin.downloadUrl,
                            description = "",
                            version = plugin.version,
                            language = null,
                            tvTypes = try {
                                com.google.gson.Gson().fromJson(plugin.tvTypes, Array<String>::class.java).toList()
                            } catch (_: Exception) { null },
                            iconUrl = plugin.iconUrl,
                            authors = emptyList(),
                            repositoryUrl = plugin.repositoryUrl
                        )
                        onInstallPlugin(csPlugin, null)
                    },
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Refresh, contentDescription = null, tint = KitsugiColors.TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.yeniden_kur), color = KitsugiColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.width(4.dp))

                if (hasSettings) {
                    TextButton(
                        onClick = {
                            if (isSettingsOpening) return@TextButton
                            isSettingsOpening = true
                            scope.launch(Dispatchers.Main) {
                                try {
                                    // Eklenti yüklenmemişse önce yükle (sessizce)
                                    var loadedPlugin = com.kitsugi.animelist.data.cloudstream.CsPluginLoader.getPluginInstance(plugin.id, context)
                                    if (loadedPlugin == null && plugin.enabled) {
                                        com.kitsugi.animelist.data.cloudstream.CsPluginLoader.loadExtension(context, plugin.id)
                                        loadedPlugin = com.kitsugi.animelist.data.cloudstream.CsPluginLoader.getPluginInstance(plugin.id, context)
                                    }
                                    val baseCtx = com.kitsugi.animelist.KitsugiApplication.activeActivity as? androidx.appcompat.app.AppCompatActivity
                                    if (loadedPlugin != null && baseCtx != null) {
                                        val themedCtx = com.kitsugi.animelist.data.cloudstream.CsPluginContextWrapper(
                                            baseCtx,
                                            com.kitsugi.animelist.R.style.Theme_KitsugiAnimeList
                                        )
                                        loadedPlugin.openSettings?.invoke(themedCtx)
                                            ?: android.widget.Toast.makeText(context, KitsugiStrings.get(R.string.eklentinin_ayar_sayfasi_yok), android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        android.widget.Toast.makeText(context, KitsugiStrings.get(R.string.eklenti_yuklenemedi_veya_ekran_hazir_degil), android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Throwable) {
                                    android.util.Log.e("KitsugiAddons", "Ayarlar açılamadı: ${e.message}", e)
                                    android.widget.Toast.makeText(context, KitsugiStrings.get(R.string.hata_x, e.message), android.widget.Toast.LENGTH_LONG).show()
                                } finally {
                                    kotlinx.coroutines.delay(1000)
                                    isSettingsOpening = false
                                }
                            }
                        },
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Settings, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.ayarlar), color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (plugin.enabled && onExplorePlugin != null) {
                    TextButton(
                        onClick = { onExplorePlugin(plugin.name) },
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Explore, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.kesfet), color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(4.dp))
                }

                TextButton(
                    onClick = { onVerifyPlugin(plugin.id, plugin.name) },
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.OpenInBrowser, contentDescription = null, tint = KitsugiColors.TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.inline_verify_56ac91e), color = KitsugiColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
