package com.kitsugi.animelist.ui.components.addons
import com.kitsugi.animelist.ui.components.KitsugiButton

import android.content.Context
import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Switch
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.data.local.ManagedAddonEntity
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.components.KitsugiDropdownMenu
import com.kitsugi.animelist.ui.components.KitsugiDropdownItem
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

@Composable
internal fun StremioTab(
    addons: List<ManagedAddonEntity>,
    initialDebridToken: String,
    accentColor: Color,
    onAddAddon: (String) -> Unit,
    onToggleAddon: (ManagedAddonEntity, Boolean) -> Unit,
    onDeleteAddon: (ManagedAddonEntity) -> Unit,
    /**
     * NOT: RealDebrid token GİRİŞ ALANI bu sayfadan kaldırıldı (kullanıcı isteği).
     * Kayıtlı token hâlâ [initialDebridToken] üzerinden okunur ve Torrentio önayarı
     * oluşturulurken adrese eklenir; TV arayüzü token girmeye devam eder. Parametre,
     * çağıran ekranların (ayarlar / manga / akış) imzasını bozmamak için tutulur.
     */
    @Suppress("UNUSED_PARAMETER") onSaveDebridToken: (String) -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    var newManifestUrl by rememberSaveable { mutableStateOf("") }
    // Token yalnızca okunur (giriş alanı kaldırıldı); Torrentio adresinde kullanılır.
    val debridToken = initialDebridToken
    var showP2pConsentDialog by remember { mutableStateOf(false) }

    if (showP2pConsentDialog) {
        com.kitsugi.animelist.ui.components.p2p.P2pConsentDialog(
            onDismiss = { showP2pConsentDialog = false },
            onConsentApproved = { showP2pConsentDialog = false }
        )
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── Bilgilendirme şeridi ───────────────────────────────────────────
        item {
            AddonInfoBanner(
                accentColor = accentColor,
                text = stringResource(R.string.torrent_akisi_ve_stremio_uyumlu_akis_eklentilerini_bu_sekmeden_y)
            )
        }

        // ── P2P Torrent Akışı (kendi bölüm başlığı ve kartı vardır) ─────────
        item {
            com.kitsugi.animelist.ui.components.p2p.P2pSettingsSection(
                onShowConsentDialog = { showP2pConsentDialog = true }
            )
        }

        // ── Oynatma ayarları ───────────────────────────────────────────────
        item {
            AddonSectionCard(
                title = stringResource(R.string.inline_playback_settings_ca13228),
                subtitle = stringResource(R.string.otomatik_oynatma_ve_oynatici_motoru),
                icon = Icons.PlayCircle,
                accentColor = accentColor,
                iconTint = accentColor
            ) {
                val context = LocalContext.current
                val prefs = remember { context.getSharedPreferences("MyWebViewPrefs", Context.MODE_PRIVATE) }
                var autoplayEnabled by remember { mutableStateOf(prefs.getBoolean("autoplay_enabled", false)) }
                var defaultPlayer by remember { mutableStateOf(prefs.getString("default_player_engine", "exoplayer") ?: "exoplayer") }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Otomatik Oynat (Auto-Play)", color = KitsugiColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.inline_automatically_start_the_first_cach_6bd3939), color = KitsugiColors.TextSecondary, fontSize = 12.sp)
                    }
                    Switch(
                        checked = autoplayEnabled,
                        onCheckedChange = { autoplayEnabled = it; prefs.edit().putBoolean("autoplay_enabled", it).apply() }
                    )
                }

                HorizontalDivider(color = KitsugiColors.Border.copy(alpha = 0.5f))

                Text(stringResource(R.string.inline_default_player_engine_9ae31a7), color = KitsugiColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                var dropdownExpanded by remember { mutableStateOf(false) }
                val playerOptions = listOf("exoplayer" to stringResource(R.string.player_engine_internal_exo), "mpv" to stringResource(R.string.harici_oynatici_mpv), "ask" to "Her Zaman Sor")
                val currentPlayerName = playerOptions.find { it.first == defaultPlayer }?.second ?: stringResource(R.string.player_engine_internal_exo)
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        // Kart zemini zaten SurfaceSoft; bu satır bir ton koyu/açık yüzeyle
                        // ayrışır ve kenarlıkla belirginleşir.
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(KitsugiColors.Surface)
                            .border(1.dp, KitsugiColors.Border.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                            .tvClickable(shape = RoundedCornerShape(16.dp)) { dropdownExpanded = true }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = currentPlayerName, color = KitsugiColors.TextPrimary, fontSize = 14.sp)
                        Icon(Icons.ArrowDropDown, contentDescription = stringResource(R.string.ui_tv_select), tint = KitsugiColors.TextSecondary)
                    }
                    KitsugiDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        dialogTitle = stringResource(R.string.inline_default_player_engine_9ae31a7)
                    ) {
                        playerOptions.forEach { option ->
                            KitsugiDropdownItem(
                                text = option.second,
                                selected = option.first == defaultPlayer,
                                onClick = { defaultPlayer = option.first; prefs.edit().putString("default_player_engine", option.first).apply(); dropdownExpanded = false }
                            )
                        }
                    }
                }
            }
        }

        // ── Hazır akış eklentileri ─────────────────────────────────────────
        item {
            AddonSectionCard(
                title = stringResource(R.string.hazir_akis_eklentileri),
                subtitle = stringResource(R.string.tek_tikla_kurabileceginiz_guvenilir_kaynaklar),
                icon = Icons.AutoAwesome,
                accentColor = accentColor,
                iconTint = accentColor
            ) {
                val torrentioUrl = if (debridToken.isNotBlank()) {
                    "https://torrentio.strem.fun/realdebrid=$debridToken/providers=yts,eztv,rarbg,1337x,thepiratebay,kickasstorrents,torrentproject,torrent9,yts,horriblesubs/manifest.json"
                } else {
                    "https://torrentio.strem.fun/manifest.json"
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    // Token bu sayfada girilmez (giriş alanı kaldırıldı); kayıtlı bir debrid
                    // hesabı varsa Torrentio adresine otomatik eklenir.
                    PresetAddonRow("Torrentio (Tavsiye Edilen)", "Ana torrent kaynağı. Kayıtlı debrid hesabınız varsa otomatik bağlanır.", torrentioUrl, addons.any { it.manifestUrl.contains("torrentio") }, accentColor, Icons.Download, onAddAddon)
                    PresetAddonRow("Anime Kitsu Kataloğu", "Kitsu üzerinden anime listelerini ve verileri yükler.", "https://anime-kitsu.strem.fun/manifest.json", addons.any { it.manifestUrl.contains("kitsu") }, accentColor, Icons.AutoAwesome, onAddAddon)
                    PresetAddonRow("Official Cinemeta", "Film ve dizi meta verilerini çeken resmi katalog eklentisi.", "https://v3-cinemeta.strem.io/manifest.json", addons.any { it.manifestUrl.contains("cinemeta") }, accentColor, Icons.Movie, onAddAddon)
                    PresetAddonRow("YouTube Trailers", "Fragmanları ve YouTube videolarını oynatan resmi eklenti.", "https://youtube.strem.io/manifest.json", addons.any { it.manifestUrl.contains("youtube.strem.io") }, accentColor, Icons.PlayCircle, onAddAddon)
                }
            }
        }

        // ── Özel eklenti ekle ──────────────────────────────────────────────
        item {
            AddonSectionCard(
                title = stringResource(R.string.inline_add_custom_streaming_extension_c10d9ca),
                subtitle = stringResource(R.string.manifest_adresini_yapistirin),
                icon = Icons.AddLink,
                accentColor = accentColor,
                iconTint = accentColor
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = newManifestUrl,
                        onValueChange = { newManifestUrl = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        label = { Text("Manifest URL (JSON)") },
                        placeholder = { Text("https://...") },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = KitsugiColors.TextPrimary, unfocusedTextColor = KitsugiColors.TextPrimary,
                            focusedContainerColor = KitsugiColors.SurfaceSoft, unfocusedContainerColor = KitsugiColors.SurfaceSoft,
                            focusedIndicatorColor = accentColor, unfocusedIndicatorColor = KitsugiColors.Border, cursorColor = accentColor
                        )
                    )
                    KitsugiButton(
                        onClick = { if (newManifestUrl.isNotBlank()) { onAddAddon(newManifestUrl.trim()); newManifestUrl = "" } },
                        shape = RoundedCornerShape(16.dp)
                    ) { Icon(Icons.Add, contentDescription = stringResource(R.string.ekle)) }
                }
            }
        }

        // ── Yüklü eklentiler ───────────────────────────────────────────────
        item {
            AddonListHeader(
                title = stringResource(R.string.yuklu_akis_eklentileri),
                count = addons.size,
                accentColor = accentColor
            )
        }

        if (addons.isEmpty()) {
            item {
                AddonEmptyCard(
                    icon = Icons.ExtensionOff,
                    text = stringResource(R.string.henuz_akis_eklentisi_yuklenmemis_yukaridaki_hazir_eklentilerden)
                )
            }
        } else {
            items(addons) { addon ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(KitsugiColors.SurfaceSoft).border(1.dp, KitsugiColors.Border.copy(alpha = 0.55f), RoundedCornerShape(16.dp)).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(accentColor.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Extension, contentDescription = null, tint = accentColor)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(addon.name, color = KitsugiColors.TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(addon.description ?: stringResource(R.string.ui_no_description), color = KitsugiColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = addon.isEnabled,
                        onCheckedChange = { onToggleAddon(addon, it) }
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { onDeleteAddon(addon) }) {
                        Icon(Icons.Delete, contentDescription = stringResource(R.string.action_delete), tint = KitsugiColors.AccentRed)
                    }
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
//  Ortak bölüm bileşenleri (Video Sağlayıcıları / Manga sekmeleriyle aynı dil)
// ══════════════════════════════════════════════════════════════════════════

/**
 * Sekme başındaki kısa bilgilendirme şeridi — kullanıcı bu sekmenin ne yaptığını
 * ilk bakışta görsün diye tek satırlık açıklama + ikon gösterir.
 */
@Composable
internal fun AddonInfoBanner(
    text: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(accentColor.copy(alpha = 0.08f))
            .border(1.dp, accentColor.copy(alpha = 0.20f), RoundedCornerShape(13.dp))
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Info,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            color = KitsugiColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Başlık + alt başlık + ikon içeren, gövdesi [content] olan tek tip bölüm kartı.
 * Sekme içindeki tüm ayar grupları bu bileşeni kullanır; böylece kartlar, boşluklar
 * ve tipografi her bölümde aynı görünür.
 */
@Composable
internal fun AddonSectionCard(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    subtitle: String? = null,
    iconTint: Color = accentColor,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(KitsugiColors.SurfaceSoft)
                .border(1.dp, KitsugiColors.Border.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

/** Kart içi küçük açıklama metni. */
@Composable
internal fun AddonHintText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = KitsugiColors.TextMuted,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier
    )
}

/** Liste bölümlerinin başlığı — sağ tarafta adet rozeti gösterir. */
@Composable
internal fun AddonListHeader(
    title: String,
    count: Int,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = KitsugiColors.TextPrimary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(accentColor.copy(alpha = 0.15f))
                .padding(horizontal = 10.dp, vertical = 3.dp)
        ) {
            Text(
                text = count.toString(),
                color = accentColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Boş liste durumu — bölüm kartıyla aynı görsel dilde. */
@Composable
internal fun AddonEmptyCard(
    text: String,
    icon: ImageVector = Icons.Extension,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(KitsugiColors.SurfaceSoft)
            .border(1.dp, KitsugiColors.Border.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = KitsugiColors.TextMuted,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            color = KitsugiColors.TextMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PresetAddonRow(
    title: String,
    subtitle: String,
    manifestUrl: String,
    isInstalled: Boolean,
    accentColor: Color,
    icon: ImageVector,
    onAddAddon: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(KitsugiColors.Surface)
            .border(1.dp, KitsugiColors.Border.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .tvClickable(enabled = !isInstalled, shape = RoundedCornerShape(14.dp)) { onAddAddon(manifestUrl) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(accentColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = KitsugiColors.TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, color = KitsugiColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(8.dp))
        if (isInstalled) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.CheckCircle, contentDescription = null, tint = KitsugiColors.AccentGreen, modifier = Modifier.size(14.dp))
                Text(
                    text = "Kuruldu",
                    color = KitsugiColors.TextMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        } else {
            Text(
                text = "Kur",
                color = accentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}
