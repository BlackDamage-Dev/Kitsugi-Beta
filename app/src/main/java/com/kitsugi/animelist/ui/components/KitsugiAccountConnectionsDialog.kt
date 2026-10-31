package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text

import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalIsTv
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitsugiAccountConnectionsDialog(
    isAniListConnected: Boolean,
    anilistUsername: String,
    isAniListImportRunning: Boolean,
    onAniListImportClick: () -> Unit,
    onAniListAuthClick: () -> Unit,
    isMalConnected: Boolean,
    malUsername: String,
    isMalImportRunning: Boolean,
    onMalImportClick: () -> Unit,
    onMalAuthClick: () -> Unit,
    isSimklConnected: Boolean = false,
    simklUsername: String = "",
    isSimklImportRunning: Boolean = false,
    isSimklSessionExpired: Boolean = false,
    onSimklImportClick: () -> Unit = {},
    onSimklAuthClick: () -> Unit = {},
    isKitsuConnected: Boolean = false,
    kitsuUsername: String = "",
    isKitsuImportRunning: Boolean = false,
    onKitsuImportClick: () -> Unit = {},
    onKitsuAuthClick: () -> Unit = {},
    syncEnabledKitsu: Boolean = false,
    onSyncEnabledKitsuChanged: (Boolean) -> Unit = {},
    onLoginKitsu: (username: String, password: String, onComplete: (Boolean, String?) -> Unit) -> Unit = { _, _, _ -> },
    isShikimoriConnected: Boolean = false,
    shikimoriUsername: String = "",
    isShikimoriImportRunning: Boolean = false,
    onShikimoriImportClick: () -> Unit = {},
    onShikimoriAuthClick: () -> Unit = {},
    syncEnabledShikimori: Boolean = false,
    onSyncEnabledShikimoriChanged: (Boolean) -> Unit = {},
    onLoginShikimori: (clientId: String, clientSecret: String, authCode: String, onComplete: (Boolean, String?) -> Unit) -> Unit = { _, _, _, _ -> },
    isCrossSyncRunning: Boolean = false,
    crossSyncState: com.kitsugi.animelist.model.CrossSyncProgressState = com.kitsugi.animelist.model.CrossSyncProgressState(),
    onCrossSyncClick: () -> Unit = {},
    onCrossSyncCancel: () -> Unit = {},
    syncEnabledAnilist: Boolean = false,
    onSyncEnabledAnilistChanged: (Boolean) -> Unit = {},
    syncEnabledMal: Boolean = false,
    onSyncEnabledMalChanged: (Boolean) -> Unit = {},
    syncEnabledSimkl: Boolean = false,
    onSyncEnabledSimklChanged: (Boolean) -> Unit = {},
    embeddedMode: Boolean = false,
    onDismiss: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    val scrollState = rememberScrollState()

    KitsugiSheetOrDialog(
        onDismiss = onDismiss,
        fullScreen = true,
        embeddedMode = embeddedMode,
        innerColumnScrollState = scrollState
    ) {
        if (!embeddedMode) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.hesap_baglantilari),
                        color = KitsugiColors.TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Close, contentDescription = stringResource(R.string.kapat), tint = KitsugiColors.TextSecondary)
                    }
                }
            }
        }

        // Body
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            AccountConnectionsTab(
                isAniListConnected = isAniListConnected,
                anilistUsername = anilistUsername,
                isAniListImportRunning = isAniListImportRunning,
                onAniListImportClick = onAniListImportClick,
                onAniListAuthClick = onAniListAuthClick,
                isMalConnected = isMalConnected,
                malUsername = malUsername,
                isMalImportRunning = isMalImportRunning,
                onMalImportClick = onMalImportClick,
                onMalAuthClick = onMalAuthClick,
                isSimklConnected = isSimklConnected,
                simklUsername = simklUsername,
                isSimklImportRunning = isSimklImportRunning,
                isSimklSessionExpired = isSimklSessionExpired,
                onSimklImportClick = onSimklImportClick,
                onSimklAuthClick = onSimklAuthClick,
                isKitsuConnected = isKitsuConnected,
                kitsuUsername = kitsuUsername,
                isKitsuImportRunning = isKitsuImportRunning,
                onKitsuImportClick = onKitsuImportClick,
                onKitsuAuthClick = onKitsuAuthClick,
                syncEnabledKitsu = syncEnabledKitsu,
                onSyncEnabledKitsuChanged = onSyncEnabledKitsuChanged,
                onLoginKitsu = onLoginKitsu,
                isShikimoriConnected = isShikimoriConnected,
                shikimoriUsername = shikimoriUsername,
                isShikimoriImportRunning = isShikimoriImportRunning,
                onShikimoriImportClick = onShikimoriImportClick,
                onShikimoriAuthClick = onShikimoriAuthClick,
                syncEnabledShikimori = syncEnabledShikimori,
                onSyncEnabledShikimoriChanged = onSyncEnabledShikimoriChanged,
                onLoginShikimori = onLoginShikimori,
                isCrossSyncRunning = isCrossSyncRunning,
                crossSyncState = crossSyncState,
                onCrossSyncClick = onCrossSyncClick,
                onCrossSyncCancel = onCrossSyncCancel,
                syncEnabledAnilist = syncEnabledAnilist,
                onSyncEnabledAnilistChanged = onSyncEnabledAnilistChanged,
                syncEnabledMal = syncEnabledMal,
                onSyncEnabledMalChanged = onSyncEnabledMalChanged,
                syncEnabledSimkl = syncEnabledSimkl,
                onSyncEnabledSimklChanged = onSyncEnabledSimklChanged,
                accentColor = accentColor,
                scrollState = scrollState
            )
        }

        if (!embeddedMode) {
            // Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.settings_ok), color = accentColor, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun AccountConnectionsTab(
    isAniListConnected: Boolean,
    anilistUsername: String,
    isAniListImportRunning: Boolean,
    onAniListImportClick: () -> Unit,
    onAniListAuthClick: () -> Unit,
    isMalConnected: Boolean,
    malUsername: String,
    isMalImportRunning: Boolean,
    onMalImportClick: () -> Unit,
    onMalAuthClick: () -> Unit,
    isSimklConnected: Boolean,
    simklUsername: String,
    isSimklImportRunning: Boolean,
    isSimklSessionExpired: Boolean,
    onSimklImportClick: () -> Unit,
    onSimklAuthClick: () -> Unit,
    isKitsuConnected: Boolean,
    kitsuUsername: String,
    isKitsuImportRunning: Boolean,
    onKitsuImportClick: () -> Unit,
    onKitsuAuthClick: () -> Unit,
    syncEnabledKitsu: Boolean,
    onSyncEnabledKitsuChanged: (Boolean) -> Unit,
    onLoginKitsu: (username: String, password: String, onComplete: (Boolean, String?) -> Unit) -> Unit,
    isShikimoriConnected: Boolean,
    shikimoriUsername: String,
    isShikimoriImportRunning: Boolean,
    onShikimoriImportClick: () -> Unit,
    onShikimoriAuthClick: () -> Unit,
    syncEnabledShikimori: Boolean,
    onSyncEnabledShikimoriChanged: (Boolean) -> Unit,
    onLoginShikimori: (clientId: String, clientSecret: String, authCode: String, onComplete: (Boolean, String?) -> Unit) -> Unit,
    isCrossSyncRunning: Boolean,
    crossSyncState: com.kitsugi.animelist.model.CrossSyncProgressState,
    onCrossSyncClick: () -> Unit,
    onCrossSyncCancel: () -> Unit,
    syncEnabledAnilist: Boolean,
    onSyncEnabledAnilistChanged: (Boolean) -> Unit,
    syncEnabledMal: Boolean,
    onSyncEnabledMalChanged: (Boolean) -> Unit,
    syncEnabledSimkl: Boolean,
    onSyncEnabledSimklChanged: (Boolean) -> Unit,
    accentColor: Color,
    scrollState: androidx.compose.foundation.ScrollState
) {
    val isTv = LocalIsTv.current
    var showTvQrDialog by remember { mutableStateOf(false) }
    var showKitsuLoginDialog by remember { mutableStateOf(false) }
    var showShikimoriLoginDialog by remember { mutableStateOf(false) }
    var showCrossSyncDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // AniList
        KitsugiSettingsSection(title = stringResource(R.string.anilist_hesabi)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                if (isAniListConnected) {
                    KitsugiSettingsItem(
                        title = stringResource(R.string.anilist_hesabi_anilistusername, anilistUsername),
                        description = if (isAniListImportRunning) "Senkronize ediliyor..." else stringResource(R.string.listenizi_anilist_ten_ice_aktarin),
                        icon = Icons.Sync,
                        iconColor = KitsugiColors.AccentGreen,
                        onClick = { if (!isAniListImportRunning) onAniListImportClick() }
                    )
                    KitsugiSettingsDivider()
                    KitsugiSettingsSwitchItem(
                        title = stringResource(R.string.otomatik_esitleme),
                        description = stringResource(R.string.kitsugi_deki_degisiklikleri_anilist_e_otomat),
                        icon = Icons.CloudSync,
                        iconColor = KitsugiColors.AccentBlue,
                        checked = syncEnabledAnilist,
                        onCheckedChange = onSyncEnabledAnilistChanged
                    )
                    KitsugiSettingsDivider()
                    KitsugiSettingsItem(
                        title = stringResource(R.string.anilist_baglantisini_kes),
                        description = stringResource(R.string.hesabinizi_uygulamadan_kaldirir),
                        icon = Icons.LinkOff,
                        iconColor = KitsugiColors.AccentRed,
                        onClick = onAniListAuthClick
                    )
                } else {
                    KitsugiSettingsItem(
                        title = stringResource(R.string.anilist_hesabini_bagla),
                        description = if (isTv) stringResource(R.string.tv_de_qr_kod_ile_giris_yap) else stringResource(R.string.giris_yap_ve_listenizi_esitle),
                        icon = Icons.Link,
                        iconColor = KitsugiColors.AccentBlue,
                        onClick = {
                            if (isTv) showTvQrDialog = true
                            else onAniListAuthClick()
                        }
                    )
                }
            }
        }

        // MyAnimeList
        KitsugiSettingsSection(title = stringResource(R.string.myanimelist_hesabi)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                if (isMalConnected) {
                    KitsugiSettingsItem(
                        title = stringResource(R.string.myanimelist_hesabi_malusername, malUsername),
                        description = if (isMalImportRunning) "Senkronize ediliyor..." else stringResource(R.string.listenizi_myanimelist_ten_ice_aktarin),
                        icon = Icons.Sync,
                        iconColor = KitsugiColors.AccentGreen,
                        onClick = { if (!isMalImportRunning) onMalImportClick() }
                    )
                    KitsugiSettingsDivider()
                    KitsugiSettingsSwitchItem(
                        title = stringResource(R.string.otomatik_esitleme),
                        description = stringResource(R.string.kitsugi_deki_degisiklikleri_myanimelist_e_ot),
                        icon = Icons.CloudSync,
                        iconColor = KitsugiColors.AccentBlue,
                        checked = syncEnabledMal,
                        onCheckedChange = onSyncEnabledMalChanged
                    )
                    KitsugiSettingsDivider()
                    KitsugiSettingsItem(
                        title = stringResource(R.string.myanimelist_baglantisini_kes),
                        description = stringResource(R.string.hesabinizi_uygulamadan_kaldirir),
                        icon = Icons.LinkOff,
                        iconColor = KitsugiColors.AccentRed,
                        onClick = onMalAuthClick
                    )
                } else {
                    KitsugiSettingsItem(
                        title = stringResource(R.string.myanimelist_hesabini_bagla),
                        description = if (isTv) stringResource(R.string.tv_de_qr_kod_ile_giris_yap) else stringResource(R.string.giris_yap_ve_listenizi_esitle),
                        icon = Icons.Link,
                        iconColor = KitsugiColors.AccentBlue,
                        onClick = {
                            if (isTv) showTvQrDialog = true
                            else onMalAuthClick()
                        }
                    )
                }
            }
        }

        // Simkl
        KitsugiSettingsSection(title = stringResource(R.string.simkl_hesabi)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                if (isSimklConnected) {
                    KitsugiSettingsItem(
                        title = stringResource(R.string.simkl_hesabi_simklusername, simklUsername),
                        description = if (isSimklImportRunning) "Senkronize ediliyor..." else stringResource(R.string.listenizi_simkl_dan_ice_aktarin),
                        icon = Icons.Sync,
                        iconColor = KitsugiColors.AccentGreen,
                        onClick = { if (!isSimklImportRunning) onSimklImportClick() }
                    )
                    KitsugiSettingsDivider()
                    KitsugiSettingsSwitchItem(
                        title = stringResource(R.string.otomatik_esitleme),
                        description = stringResource(R.string.kitsugi_deki_degisiklikleri_simkl_e_otomatik),
                        icon = Icons.CloudSync,
                        iconColor = KitsugiColors.AccentBlue,
                        checked = syncEnabledSimkl,
                        onCheckedChange = onSyncEnabledSimklChanged
                    )
                    KitsugiSettingsDivider()
                    KitsugiSettingsItem(
                        title = stringResource(R.string.simkl_baglantisini_kes),
                        description = stringResource(R.string.hesabinizi_uygulamadan_kaldirir),
                        icon = Icons.LinkOff,
                        iconColor = KitsugiColors.AccentRed,
                        onClick = onSimklAuthClick
                    )
                } else if (isSimklSessionExpired) {
                    KitsugiSettingsItem(
                        title = stringResource(R.string.simkl_baglantisini_yenile_suresi_doldu),
                        description = stringResource(R.string.oturum_suresi_doldu_tekrar_giris_yapin),
                        icon = Icons.Warning,
                        iconColor = KitsugiColors.AccentRed,
                        onClick = {
                            if (isTv) showTvQrDialog = true
                            else onSimklAuthClick()
                        }
                    )
                } else {
                    KitsugiSettingsItem(
                        title = stringResource(R.string.simkl_hesabini_bagla),
                        description = if (isTv) stringResource(R.string.tv_de_qr_kod_ile_giris_yap) else stringResource(R.string.giris_yap_film_dizi_ve_animelerini_esitle),
                        icon = Icons.Link,
                        iconColor = KitsugiColors.AccentBlue,
                        onClick = {
                            if (isTv) showTvQrDialog = true
                            else onSimklAuthClick()
                        }
                    )
                }
            }
        }

        // Kitsu
        val kitsuColor = Color(0xFFFD755C)
        KitsugiSettingsSection(title = stringResource(R.string.kitsu_hesabi)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                if (isKitsuConnected) {
                    KitsugiSettingsItem(
                        title = stringResource(R.string.kitsu_hesabi_kitsuusername, kitsuUsername),
                        description = if (isKitsuImportRunning) "Senkronize ediliyor..." else stringResource(R.string.listenizi_kitsu_dan_ice_aktarin),
                        icon = Icons.Sync,
                        iconColor = KitsugiColors.AccentGreen,
                        onClick = { if (!isKitsuImportRunning) onKitsuImportClick() }
                    )
                    KitsugiSettingsDivider()
                    KitsugiSettingsSwitchItem(
                        title = stringResource(R.string.otomatik_esitleme),
                        description = stringResource(R.string.kitsugi_deki_degisiklikleri_kitsu_ya_otomati),
                        icon = Icons.CloudSync,
                        iconColor = kitsuColor,
                        checked = syncEnabledKitsu,
                        onCheckedChange = onSyncEnabledKitsuChanged
                    )
                    KitsugiSettingsDivider()
                    KitsugiSettingsItem(
                        title = stringResource(R.string.kitsu_baglantisini_kes),
                        description = stringResource(R.string.hesabinizi_uygulamadan_kaldirir),
                        icon = Icons.LinkOff,
                        iconColor = KitsugiColors.AccentRed,
                        onClick = onKitsuAuthClick
                    )
                } else {
                    KitsugiSettingsItem(
                        title = stringResource(R.string.kitsu_hesabini_bagla),
                        description = stringResource(R.string.kullanici_adi_ve_sifrenizle_giris_yapip_list),
                        icon = Icons.Link,
                        iconColor = kitsuColor,
                        onClick = { showKitsuLoginDialog = true }
                    )
                }
            }
        }

        // Shikimori
        val shikimoriColor = Color(0xFF8E44AD)
        KitsugiSettingsSection(title = stringResource(R.string.shikimori_hesabi)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                if (isShikimoriConnected) {
                    KitsugiSettingsItem(
                        title = stringResource(R.string.shikimori_hesabi_shikimoriusername, shikimoriUsername),
                        description = if (isShikimoriImportRunning) "Senkronize ediliyor..." else stringResource(R.string.listenizi_shikimori_den_ice_aktarin),
                        icon = Icons.Sync,
                        iconColor = KitsugiColors.AccentGreen,
                        onClick = { if (!isShikimoriImportRunning) onShikimoriImportClick() }
                    )
                    KitsugiSettingsDivider()
                    KitsugiSettingsSwitchItem(
                        title = stringResource(R.string.otomatik_esitleme),
                        description = stringResource(R.string.kitsugi_deki_degisiklikleri_shikimori_ye_oto),
                        icon = Icons.CloudSync,
                        iconColor = shikimoriColor,
                        checked = syncEnabledShikimori,
                        onCheckedChange = onSyncEnabledShikimoriChanged
                    )
                    KitsugiSettingsDivider()
                    KitsugiSettingsItem(
                        title = stringResource(R.string.shikimori_baglantisini_kes),
                        description = stringResource(R.string.hesabinizi_uygulamadan_kaldirir),
                        icon = Icons.LinkOff,
                        iconColor = KitsugiColors.AccentRed,
                        onClick = onShikimoriAuthClick
                    )
                } else {
                    KitsugiSettingsItem(
                        title = stringResource(R.string.shikimori_hesabini_bagla),
                        description = stringResource(R.string.oauth2_yetkilendirme_ile_shikimori_listenizi),
                        icon = Icons.Link,
                        iconColor = shikimoriColor,
                        onClick = { showShikimoriLoginDialog = true }
                    )
                }
            }
        }

        // Çok Yönlü Eşitleme (En az 2 hesap bağlıysa göster)
        val context = androidx.compose.ui.platform.LocalContext.current
        val isBangumiConnected = com.kitsugi.animelist.data.auth.BangumiAuthStore.isConnected(context)
        val isTmdbAccountConnected = com.kitsugi.animelist.data.auth.TmdbAuthStore.isConnected(context)
        val connectedCount = (if (isAniListConnected) 1 else 0) +
                (if (isMalConnected) 1 else 0) +
                (if (isSimklConnected) 1 else 0) +
                (if (isKitsuConnected) 1 else 0) +
                (if (isShikimoriConnected) 1 else 0) +
                (if (isBangumiConnected) 1 else 0) +
                (if (isTmdbAccountConnected) 1 else 0)

        if (connectedCount >= 2) {
            val connectedNames = mutableListOf<String>().apply {
                if (isAniListConnected) add("AniList")
                if (isMalConnected) add("MyAnimeList")
                if (isSimklConnected) add("Simkl")
                if (isKitsuConnected) add("Kitsu")
                if (isShikimoriConnected) add("Shikimori")
                if (isBangumiConnected) add("Bangumi")
                if (isTmdbAccountConnected) add("TMDB")
            }.joinToString(", ")

            KitsugiSettingsSection(title = stringResource(R.string.cok_yonlu_esitleme)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    val syncDesc = when {
                        crossSyncState.isRunning -> crossSyncState.currentStep +
                            if (crossSyncState.totalItems > 0) " (%${(crossSyncState.progressPercent * 100).toInt()})" else ""
                        crossSyncState.startedAt != null -> "Son eşitleme raporunu, sorunları ve platform ayrıntılarını görüntüle"
                        else -> "Deneysel · $connectedNames verilerini karşılıklı senkronize edin (Asla silme yapılmaz)"
                    }

                    KitsugiSettingsItem(
                        title = when {
                            crossSyncState.isRunning -> stringResource(R.string.esitleme_ayrintilarini_ac_x_6, connectedCount)
                            crossSyncState.startedAt != null -> "Son Eşitleme Raporu ($connectedCount/7)"
                            else -> "Tüm Hesapları Birbiriyle Eşitle ($connectedCount/7)"
                        },
                        description = syncDesc,
                        icon = Icons.Cached,
                        iconColor = KitsugiColors.AccentOrange,
                        onClick = {
                            showCrossSyncDialog = true
                            if (!crossSyncState.isRunning && crossSyncState.startedAt == null) {
                                onCrossSyncClick()
                            }
                        }
                    )

                    CrossSyncDisclaimerText(
                        full = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )

                    if (crossSyncState.isRunning) {
                        Spacer(modifier = Modifier.height(4.dp))
                        if (crossSyncState.totalItems <= 0) {
                            com.kitsugi.animelist.ui.theme.gradient.LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
                                color = KitsugiColors.AccentOrange,
                                trackColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                            )
                        } else {
                            com.kitsugi.animelist.ui.theme.gradient.LinearProgressIndicator(
                                progress = { crossSyncState.progressPercent },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
                                color = KitsugiColors.AccentOrange,
                                trackColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showTvQrDialog) {
        KitsugiTvQrLoginDialog(
            onDismiss = { showTvQrDialog = false }
        )
    }

    if (showKitsuLoginDialog) {
        KitsugiKitsuLoginDialog(
            onDismiss = { showKitsuLoginDialog = false },
            onLogin = onLoginKitsu
        )
    }

    if (showShikimoriLoginDialog) {
        KitsugiShikimoriLoginDialog(
            onDismiss = { showShikimoriLoginDialog = false },
            onLogin = onLoginShikimori
        )
    }

    if (showCrossSyncDialog) {
        KitsugiCrossSyncDialog(
            state = crossSyncState,
            onDismiss = { showCrossSyncDialog = false },
            onCancel = onCrossSyncCancel,
            onStart = onCrossSyncClick
        )
    }
}
