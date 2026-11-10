package com.kitsugi.animelist.ui.screens.settings

import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.LinearProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Text

import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.utils.kitsugiVerticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.ui.components.*
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalIsTv
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
// 1. Hesap Bağlantıları Ana Hub Sayfası
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun AccountConnectionsHubContent(
    profile: ProfileSettings,
    onNavigate: (SettingsRoute) -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val platformsScrollState = rememberScrollState()
    val syncScrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Üst sekme bandı — "Görünüm & Tercihler" bandıyla aynı davranış:
        // sığmazsa kendi içinde kaydırılır, ekrandan taşmaz.
        KitsugiSettingsSegmentedTabs(
            tabs = listOf(
                KitsugiSettingsTabOption(
                    title = stringResource(R.string.settings_section_platforms),
                    icon = Icons.Link
                ),
                KitsugiSettingsTabOption(
                    title = stringResource(R.string.settings_section_sync),
                    icon = Icons.CloudSync
                )
            ),
            selectedIndex = pagerState.currentPage,
            accentColor = accentColor,
            onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            if (page == 0) {
                PlatformConnectionsPage(
                    profile = profile,
                    onNavigate = onNavigate,
                    scrollState = platformsScrollState
                )
            } else {
                CrossSyncConnectionsPage(
                    profile = profile,
                    onNavigate = onNavigate,
                    scrollState = syncScrollState
                )
            }
        }
    }
}

/** Sekme 1 — Platformlar: bağlı hesaplar ve her platformun kendi ayar sayfası. */
@Composable
private fun PlatformConnectionsPage(
    profile: ProfileSettings,
    onNavigate: (SettingsRoute) -> Unit,
    scrollState: ScrollState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .kitsugiVerticalScroll(scrollState)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.inline_connect_your_anime_and_manga_740b3b7),
            color = KitsugiColors.TextSecondary,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        SectionHeader(stringResource(R.string.settings_section_platforms))
        SettingsNavCard {
            // AniList
            AccountHubItem(
                title = "AniList",
                subtitle = if (profile.isAniListConnected) stringResource(R.string.bagli_x, profile.anilistUsername) else stringResource(R.string.inline_not_connected_2c7d104),
                platformId = "anilist",
                isConnected = profile.isAniListConnected,
                onClick = { onNavigate(SettingsRoute.AniListSettings) }
            )
            KitsugiSettingsDivider()

            // MyAnimeList
            AccountHubItem(
                title = "MyAnimeList",
                subtitle = if (profile.isMalConnected) stringResource(R.string.bagli_x, profile.malUsername) else stringResource(R.string.inline_not_connected_2c7d104),
                platformId = "mal",
                isConnected = profile.isMalConnected,
                onClick = { onNavigate(SettingsRoute.MalSettings) }
            )
            KitsugiSettingsDivider()

            // Simkl
            AccountHubItem(
                title = "Simkl",
                subtitle = when {
                    profile.isSimklSessionExpired -> stringResource(R.string.oturum_suresi_doldu_tekrar_giris_yapin)
                    profile.isSimklConnected -> stringResource(R.string.bagli_x, profile.simklUsername)
                    else -> stringResource(R.string.inline_not_connected_2c7d104)
                },
                platformId = "simkl",
                isConnected = profile.isSimklConnected,
                onClick = { onNavigate(SettingsRoute.SimklSettings) }
            )
            KitsugiSettingsDivider()

            // Kitsu
            AccountHubItem(
                title = "Kitsu",
                subtitle = when {
                    profile.isKitsuSessionExpired -> stringResource(R.string.oturum_suresi_doldu_tekrar_giris_yapin)
                    profile.isKitsuConnected -> stringResource(R.string.bagli_x, profile.kitsuUsername)
                    else -> stringResource(R.string.inline_not_connected_2c7d104)
                },
                platformId = "kitsu",
                isConnected = profile.isKitsuConnected,
                onClick = { onNavigate(SettingsRoute.KitsuSettings) }
            )
            KitsugiSettingsDivider()

            // Shikimori
            AccountHubItem(
                title = "Shikimori",
                subtitle = when {
                    profile.isShikimoriSessionExpired -> stringResource(R.string.oturum_suresi_doldu_tekrar_giris_yapin)
                    profile.isShikimoriConnected -> stringResource(R.string.bagli_x, profile.shikimoriUsername)
                    else -> stringResource(R.string.inline_not_connected_2c7d104)
                },
                platformId = "shikimori",
                isConnected = profile.isShikimoriConnected,
                onClick = { onNavigate(SettingsRoute.ShikimoriSettings) }
            )
            KitsugiSettingsDivider()

            // Bangumi (bgm.tv)
            AccountHubItem(
                title = "Bangumi",
                subtitle = when {
                    profile.isBangumiSessionExpired -> stringResource(R.string.oturum_suresi_doldu_tekrar_giris_yapin)
                    profile.isBangumiConnected -> stringResource(R.string.bagli_x, profile.bangumiUsername)
                    else -> stringResource(R.string.inline_not_connected_2c7d104)
                },
                platformId = "bangumi",
                isConnected = profile.isBangumiConnected,
                onClick = { onNavigate(SettingsRoute.BangumiSettings) }
            )
            KitsugiSettingsDivider()

            // TMDB user account: separate from the public metadata catalog.
            AccountHubItem(
                title = "TMDB Watchlist",
                subtitle = if (profile.isTmdbConnected) stringResource(R.string.bagli_x, profile.tmdbUsername) else stringResource(R.string.inline_not_connected_2c7d104),
                platformId = "tmdb",
                isConnected = profile.isTmdbConnected,
                onClick = { onNavigate(SettingsRoute.TmdbSettings) }
            )
        }
    }
}

/** Sekme 2 — Senkronizasyon: hesaplar arası çapraz eşitleme durumu. */
@Composable
private fun CrossSyncConnectionsPage(
    profile: ProfileSettings,
    onNavigate: (SettingsRoute) -> Unit,
    scrollState: ScrollState
) {
    val connectedCount = (if (profile.isAniListConnected) 1 else 0) +
            (if (profile.isMalConnected) 1 else 0) +
            (if (profile.isSimklConnected) 1 else 0) +
            (if (profile.isKitsuConnected) 1 else 0) +
            (if (profile.isShikimoriConnected) 1 else 0) +
            (if (profile.isBangumiConnected) 1 else 0) +
            (if (profile.isTmdbConnected) 1 else 0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .kitsugiVerticalScroll(scrollState)
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(stringResource(R.string.settings_section_sync))
        SettingsNavCard {
            AccountHubItem(
                title = stringResource(R.string.settings_cross_sync),
                subtitle = if (connectedCount >= 2) stringResource(R.string.deneysel_x_hesap_arasinda_karsilikli_senkronizasyon, connectedCount) else stringResource(R.string.deneysel_en_az_2_hesap_baglandiginda_etkinlesir),
                icon = Icons.CloudSync,
                iconColor = KitsugiColors.AccentOrange,
                isConnected = connectedCount >= 2,
                onClick = { onNavigate(SettingsRoute.CrossSyncSettings) }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. AniList Alt Sayfası
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun AniListSettingsContent(profile: ProfileSettings) {
    val isTv = LocalIsTv.current
    var showTvQrDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .kitsugiVerticalScroll(scrollState)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ConnectionStatusCard(
            platformName = "AniList",
            platformId = "anilist",
            username = profile.anilistUsername,
            isConnected = profile.isAniListConnected
        )

        SectionHeader(stringResource(R.string.settings_section_actions))
        SettingsNavCard {
            if (profile.isAniListConnected) {
                KitsugiSettingsItem(
                    title = stringResource(R.string.kutuphaneyi_ice_aktar),
                    description = if (profile.isAniListImportRunning) "Senkronize ediliyor..." else stringResource(R.string.listenizi_anilist_ten_ice_aktarin),
                    icon = Icons.Sync,
                    iconColor = KitsugiColors.AccentGreen,
                    onClick = { if (!profile.isAniListImportRunning) profile.onAniListImportClick() }
                )
                KitsugiSettingsDivider()
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.otomatik_esitleme),
                    description = stringResource(R.string.kitsugi_deki_degisiklikleri_anilist_e_aninda_yansit),
                    icon = Icons.CloudSync,
                    iconColor = KitsugiColors.AccentBlue,
                    checked = profile.syncEnabledAnilist,
                    onCheckedChange = profile.onSyncEnabledAnilistChanged
                )
                KitsugiSettingsDivider()
                KitsugiSettingsItem(
                    title = stringResource(R.string.anilist_baglantisini_kes),
                    description = stringResource(R.string.hesabinizi_uygulamadan_kaldirir),
                    icon = Icons.LinkOff,
                    iconColor = KitsugiColors.AccentRed,
                    onClick = profile.onAniListAuthClick
                )
            } else {
                KitsugiSettingsItem(
                    title = stringResource(R.string.anilist_hesabini_bagla),
                    description = if (isTv) stringResource(R.string.tv_de_qr_kod_ile_giris_yap) else stringResource(R.string.oauth_ile_guvenli_giris_yapin_ve_listenizi_esitleyin),
                    icon = Icons.Link,
                    iconColor = KitsugiColors.AccentBlue,
                    onClick = {
                        if (isTv) showTvQrDialog = true
                        else profile.onAniListAuthClick()
                    }
                )
            }
        }
    }

    if (showTvQrDialog) {
        KitsugiTvQrLoginDialog(onDismiss = { showTvQrDialog = false })
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3. MyAnimeList Alt Sayfası
// ─────────────────────────────────────────────────────────────────────────────

/**
 * MAL tek oturum kartı — iki kimliğin durumunu tek satırda gösterir.
 *
 * Kullanıcı şikâyeti "normal girişten sonra bildirim gelmiyor" idi: OAuth jetonu bağlıydı
 * ama web oturumu ya yoktu ya da başka hesaba aitti. Kart hangi parçanın eksik/yanlış
 * olduğunu açıkça yazar ve tek dokunuşla ikisini birden bağlayan akışı açar.
 */
@Composable
private fun MalUnifiedSessionCard(
    status: com.kitsugi.animelist.data.auth.MalSessionCoordinator.Status,
    onFixClick: () -> Unit
) {
    val state = status.state
    val isLinked = state == com.kitsugi.animelist.data.auth.MalSessionCoordinator.State.LINKED
    val color = if (isLinked) KitsugiColors.AccentGreen else KitsugiColors.AccentOrange
    val icon = if (isLinked) Icons.CheckCircle else Icons.WarningAmber

    val apiLine = if (status.hasApiToken) {
        stringResource(R.string.mal_unified_status_api_connected, status.oauthUsername ?: "—")
    } else {
        stringResource(R.string.mal_unified_status_api_missing)
    }
    val webLine = if (status.hasWebCookie) {
        stringResource(R.string.mal_unified_status_web_connected, status.webUsername ?: "—")
    } else {
        stringResource(R.string.mal_unified_status_web_missing_line)
    }
    val summary = when (state) {
        com.kitsugi.animelist.data.auth.MalSessionCoordinator.State.LINKED ->
            stringResource(R.string.mal_unified_status_linked_summary)
        com.kitsugi.animelist.data.auth.MalSessionCoordinator.State.MISMATCHED ->
            stringResource(R.string.mal_unified_status_mismatch_summary)
        com.kitsugi.animelist.data.auth.MalSessionCoordinator.State.API_ONLY ->
            stringResource(R.string.mal_unified_status_web_missing)
        com.kitsugi.animelist.data.auth.MalSessionCoordinator.State.WEB_ONLY ->
            stringResource(R.string.mal_unified_status_api_missing_summary)
        else -> stringResource(R.string.mal_unified_status_unverified_summary)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                androidx.compose.material3.Text(
                    text = stringResource(R.string.mal_unified_status_title),
                    color = KitsugiColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.material3.Text(
                text = apiLine,
                color = KitsugiColors.TextSecondary,
                fontSize = 12.sp
            )
            androidx.compose.material3.Text(
                text = webLine,
                color = KitsugiColors.TextSecondary,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            androidx.compose.material3.Text(
                text = summary,
                color = KitsugiColors.TextMuted,
                fontSize = 12.sp
            )
            if (!isLinked) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onFixClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    androidx.compose.material3.Text(
                        text = stringResource(R.string.mal_unified_fix_action),
                        color = LocalKitsugiAccent.current,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
internal fun MalSettingsContent(profile: ProfileSettings) {
    val isTv = LocalIsTv.current
    var showTvQrDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    // Sayfa açıldığında tek oturum durumu sorgulanır (giriş penceresi açılmaz): böylece
    // "uygulama API'si + web oturumu aynı hesapta mı" bilgisi her zaman görünür olur.
    LaunchedEffect(Unit) {
        if (profile.isMalConnected) profile.onMalUnifiedProbe()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .kitsugiVerticalScroll(scrollState)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ConnectionStatusCard(
            platformName = "MyAnimeList",
            platformId = "mal",
            username = profile.malUsername,
            isConnected = profile.isMalConnected
        )

        // Tek oturum özeti: uygulama API'si (liste/senkron) ve web oturumu (bildirimler)
        // AYNI hesapta mı? Farklıysa tek dokunuşla onarım adımı sunulur.
        val malStatus = profile.malUnifiedStatus
        if (profile.isMalConnected && malStatus != null) {
            MalUnifiedSessionCard(
                status = malStatus,
                onFixClick = profile.onMalUnifiedFixClick
            )
        }

        SectionHeader(stringResource(R.string.settings_section_actions))
        SettingsNavCard {
            if (profile.isMalConnected) {
                KitsugiSettingsItem(
                    title = stringResource(R.string.kutuphaneyi_ice_aktar),
                    description = if (profile.isMalImportRunning) "Senkronize ediliyor..." else stringResource(R.string.listenizi_myanimelist_ten_ice_aktarin),
                    icon = Icons.Sync,
                    iconColor = KitsugiColors.AccentGreen,
                    onClick = { if (!profile.isMalImportRunning) profile.onMalImportClick() }
                )
                KitsugiSettingsDivider()
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.otomatik_esitleme),
                    description = stringResource(R.string.kitsugi_deki_degisiklikleri_myanimelist_e_aninda_yansit),
                    icon = Icons.CloudSync,
                    iconColor = KitsugiColors.AccentBlue,
                    checked = profile.syncEnabledMal,
                    onCheckedChange = profile.onSyncEnabledMalChanged
                )
                KitsugiSettingsDivider()
                KitsugiSettingsItem(
                    title = stringResource(R.string.profile_mal_reconnect_title),
                    description = stringResource(R.string.profile_mal_reconnect_description),
                    icon = Icons.Refresh,
                    iconColor = KitsugiColors.AccentBlue,
                    onClick = profile.onMalReconnectClick
                )
                KitsugiSettingsDivider()
                KitsugiSettingsItem(
                    title = stringResource(R.string.myanimelist_baglantisini_kes),
                    description = stringResource(R.string.hesabinizi_uygulamadan_kaldirir),
                    icon = Icons.LinkOff,
                    iconColor = KitsugiColors.AccentRed,
                    onClick = profile.onMalAuthClick
                )
            } else {
                KitsugiSettingsItem(
                    title = stringResource(R.string.myanimelist_hesabini_bagla),
                    description = if (isTv) stringResource(R.string.tv_de_qr_kod_ile_giris_yap) else stringResource(R.string.oauth_ile_giris_yapip_listenizi_esitleyin),
                    icon = Icons.Link,
                    iconColor = KitsugiColors.AccentBlue,
                    onClick = {
                        if (isTv) showTvQrDialog = true
                        else profile.onMalAuthClick()
                    }
                )
            }
        }
    }

    if (showTvQrDialog) {
        KitsugiTvQrLoginDialog(onDismiss = { showTvQrDialog = false })
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. Simkl Alt Sayfası
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun SimklSettingsContent(profile: ProfileSettings) {
    val isTv = LocalIsTv.current
    var showTvQrDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .kitsugiVerticalScroll(scrollState)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ConnectionStatusCard(
            platformName = "Simkl",
            platformId = "simkl",
            username = profile.simklUsername,
            isConnected = profile.isSimklConnected
        )

        SectionHeader(stringResource(R.string.settings_section_actions))
        SettingsNavCard {
            if (profile.isSimklConnected) {
                KitsugiSettingsItem(
                    title = stringResource(R.string.kutuphaneyi_ice_aktar),
                    description = if (profile.isSimklImportRunning) "Senkronize ediliyor..." else stringResource(R.string.listenizi_simkl_dan_ice_aktarin),
                    icon = Icons.Sync,
                    iconColor = KitsugiColors.AccentGreen,
                    onClick = { if (!profile.isSimklImportRunning) profile.onSimklImportClick() }
                )
                KitsugiSettingsDivider()
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.otomatik_esitleme),
                    description = stringResource(R.string.kitsugi_deki_degisiklikleri_simkl_e_aninda_yansit),
                    icon = Icons.CloudSync,
                    iconColor = KitsugiColors.AccentBlue,
                    checked = profile.syncEnabledSimkl,
                    onCheckedChange = profile.onSyncEnabledSimklChanged
                )
                KitsugiSettingsDivider()
                KitsugiSettingsItem(
                    title = stringResource(R.string.simkl_baglantisini_kes),
                    description = stringResource(R.string.hesabinizi_uygulamadan_kaldirir),
                    icon = Icons.LinkOff,
                    iconColor = KitsugiColors.AccentRed,
                    onClick = profile.onSimklAuthClick
                )
            } else if (profile.isSimklSessionExpired) {
                KitsugiSettingsItem(
                    title = stringResource(R.string.simkl_baglantisini_yenile_suresi_doldu),
                    description = stringResource(R.string.oturum_suresi_doldu_tekrar_giris_yapin),
                    icon = Icons.Warning,
                    iconColor = KitsugiColors.AccentRed,
                    onClick = {
                        if (isTv) showTvQrDialog = true
                        else profile.onSimklAuthClick()
                    }
                )
            } else {
                KitsugiSettingsItem(
                    title = stringResource(R.string.simkl_hesabini_bagla),
                    description = if (isTv) stringResource(R.string.tv_de_qr_kod_ile_giris_yap) else stringResource(R.string.giris_yap_film_dizi_ve_animelerinizi_esitleyin),
                    icon = Icons.Link,
                    iconColor = KitsugiColors.AccentBlue,
                    onClick = {
                        if (isTv) showTvQrDialog = true
                        else profile.onSimklAuthClick()
                    }
                )
            }
        }
    }

    if (showTvQrDialog) {
        KitsugiTvQrLoginDialog(onDismiss = { showTvQrDialog = false })
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. Kitsu Alt Sayfası
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun KitsuSettingsContent(profile: ProfileSettings) {
    var showKitsuLoginDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val kitsuColor = Color(0xFFFD755C)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .kitsugiVerticalScroll(scrollState)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ConnectionStatusCard(
            platformName = "Kitsu",
            platformId = "kitsu",
            username = profile.kitsuUsername,
            isConnected = profile.isKitsuConnected
        )

        SectionHeader(stringResource(R.string.settings_section_actions))
        SettingsNavCard {
            if (profile.isKitsuConnected) {
                KitsugiSettingsItem(
                    title = stringResource(R.string.kutuphaneyi_ice_aktar),
                    description = if (profile.isKitsuImportRunning) "Senkronize ediliyor..." else stringResource(R.string.listenizi_kitsu_dan_ice_aktarin),
                    icon = Icons.Sync,
                    iconColor = KitsugiColors.AccentGreen,
                    onClick = { if (!profile.isKitsuImportRunning) profile.onKitsuImportClick() }
                )
                KitsugiSettingsDivider()
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.otomatik_esitleme),
                    description = stringResource(R.string.kitsugi_deki_degisiklikleri_kitsu_ya_aninda_yansit),
                    icon = Icons.CloudSync,
                    iconColor = kitsuColor,
                    checked = profile.syncEnabledKitsu,
                    onCheckedChange = profile.onSyncEnabledKitsuChanged
                )
                KitsugiSettingsDivider()
                KitsugiSettingsItem(
                    title = stringResource(R.string.kitsu_baglantisini_kes),
                    description = stringResource(R.string.hesabinizi_uygulamadan_kaldirir),
                    icon = Icons.LinkOff,
                    iconColor = KitsugiColors.AccentRed,
                    onClick = profile.onKitsuAuthClick
                )
            } else {
                KitsugiSettingsItem(
                    title = stringResource(R.string.kitsu_hesabini_bagla),
                    description = stringResource(R.string.kullanici_adi_ve_sifrenizle_giris_yapip_kutuphanenizi_senkronize),
                    icon = Icons.Link,
                    iconColor = kitsuColor,
                    onClick = { showKitsuLoginDialog = true }
                )
            }
        }

        SectionHeader(stringResource(R.string.profile_platform_about, "Kitsu"))
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KitsugiPlatformLogo(platformId = "kitsu", size = 20.dp)
                    Text(
                        text = stringResource(R.string.profile_platform_integration, "Kitsu"),
                        color = KitsugiColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Text(
                    text = stringResource(R.string.inline_with_the_kitsu_edge_api_3e5049c),
                    color = KitsugiColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }

    if (showKitsuLoginDialog) {
        KitsugiKitsuLoginDialog(
            onDismiss = { showKitsuLoginDialog = false },
            onLogin = profile.onLoginKitsu
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 6. Shikimori Alt Sayfası
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun ShikimoriSettingsContent(profile: ProfileSettings) {
    var showShikimoriLoginDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val shikimoriColor = Color(0xFF8E44AD)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .kitsugiVerticalScroll(scrollState)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ConnectionStatusCard(
            platformName = "Shikimori",
            platformId = "shikimori",
            username = profile.shikimoriUsername,
            isConnected = profile.isShikimoriConnected
        )

        SectionHeader(stringResource(R.string.settings_section_actions))
        SettingsNavCard {
            if (profile.isShikimoriConnected) {
                KitsugiSettingsItem(
                    title = stringResource(R.string.kutuphaneyi_ice_aktar),
                    description = if (profile.isShikimoriImportRunning) "Senkronize ediliyor..." else stringResource(R.string.listenizi_shikimori_den_ice_aktarin),
                    icon = Icons.Sync,
                    iconColor = KitsugiColors.AccentGreen,
                    onClick = { if (!profile.isShikimoriImportRunning) profile.onShikimoriImportClick() }
                )
                KitsugiSettingsDivider()
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.otomatik_esitleme),
                    description = stringResource(R.string.kitsugi_deki_degisiklikleri_shikimori_ye_aninda_yansit),
                    icon = Icons.CloudSync,
                    iconColor = shikimoriColor,
                    checked = profile.syncEnabledShikimori,
                    onCheckedChange = profile.onSyncEnabledShikimoriChanged
                )
                KitsugiSettingsDivider()
                KitsugiSettingsItem(
                    title = stringResource(R.string.shikimori_baglantisini_kes),
                    description = stringResource(R.string.hesabinizi_uygulamadan_kaldirir),
                    icon = Icons.LinkOff,
                    iconColor = KitsugiColors.AccentRed,
                    onClick = profile.onShikimoriAuthClick
                )
            } else {
                KitsugiSettingsItem(
                    title = stringResource(R.string.shikimori_hesabini_bagla),
                    description = stringResource(R.string.client_kimlik_bilgileri_veya_yetki_kodu_ile_giris_yapin),
                    icon = Icons.Link,
                    iconColor = shikimoriColor,
                    onClick = { showShikimoriLoginDialog = true }
                )
            }
        }

        SectionHeader(stringResource(R.string.profile_platform_about, "Shikimori"))
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KitsugiPlatformLogo(platformId = "shikimori", size = 20.dp)
                    Text(
                        text = stringResource(R.string.profile_platform_integration, "Shikimori"),
                        color = KitsugiColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Text(
                    text = stringResource(R.string.inline_the_shikimori_v2_v1_rest_8e3b074),
                    color = KitsugiColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }

    if (showShikimoriLoginDialog) {
        KitsugiShikimoriLoginDialog(
            onDismiss = { showShikimoriLoginDialog = false },
            onLogin = profile.onLoginShikimori
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 6b. Bangumi (bgm.tv) Alt Sayfası
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Bangumi hesap bağlantısı alt sayfası.
 *
 * Shikimori sayfasıyla aynı yapı; farkı, Bangumi'de paylaşılan varsayılan uygulama kaydı
 * olmamasıdır. Kullanıcı ya https://bgm.tv/dev/app üzerinden kendi App ID / App Secret
 * değerini alır (OAuth yolu) ya da `next.bgm.tv/demo/access-token/create` sayfasından
 * **kişisel erişim anahtarı** üretir (uygulama kaydı gerekmez).
 *
 * Kişisel anahtar yolu, koleksiyon yazma uçlarındaki kapsam (`write:collection`)
 * denetiminden etkilenmez: token satırında `scope` boş kalır ve sunucu bunu "legacy"
 * sayar. 2026-06-25'ten (PR #1011) beri OAuth token'ının `scope` alanı JSON **dizisi**
 * olarak yazıldığında Go API'si bunu çözemeyip yazmayı 403 ile reddedebildiği için bu
 * yol kullanıcıya açıkça sunulur (bkz. `BANGUMI_YAZMA_TESHISI_RAPORU.md`).
 */
@Composable
internal fun BangumiSettingsContent(profile: ProfileSettings) {
    var showBangumiLoginDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    // Bangumi marka rengi: rgb(240,145,153)
    val bangumiColor = Color(0xFFF09199)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .kitsugiVerticalScroll(scrollState)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ConnectionStatusCard(
            platformName = "Bangumi",
            platformId = "bangumi",
            username = profile.bangumiUsername,
            isConnected = profile.isBangumiConnected
        )

        SectionHeader(stringResource(R.string.settings_section_actions))
        SettingsNavCard {
            if (profile.isBangumiConnected) {
                KitsugiSettingsItem(
                    title = stringResource(R.string.kutuphaneyi_ice_aktar),
                    description = if (profile.isBangumiImportRunning) "Senkronize ediliyor..." else stringResource(R.string.bangumi_koleksiyon_listenizi_ice_aktarin),
                    icon = Icons.Sync,
                    iconColor = KitsugiColors.AccentGreen,
                    onClick = { if (!profile.isBangumiImportRunning) profile.onBangumiImportClick() }
                )
                KitsugiSettingsDivider()
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.otomatik_esitleme),
                    description = stringResource(R.string.kitsugi_deki_degisiklikleri_bangumi_ye_aninda_yansit),
                    icon = Icons.CloudSync,
                    iconColor = bangumiColor,
                    checked = profile.syncEnabledBangumi,
                    onCheckedChange = profile.onSyncEnabledBangumiChanged
                )
                KitsugiSettingsDivider()
                KitsugiSettingsItem(
                    title = stringResource(R.string.bangumi_baglantisini_kes),
                    description = stringResource(R.string.hesabinizi_uygulamadan_kaldirir_app_id_secret_saklanir),
                    icon = Icons.LinkOff,
                    iconColor = KitsugiColors.AccentRed,
                    onClick = profile.onBangumiAuthClick
                )
            } else {
                KitsugiSettingsItem(
                    title = stringResource(R.string.bangumi_hesabini_bagla),
                    description = stringResource(R.string.bgm_tv_app_id_app_secret_ve_yetki_kodu_ile_giris_yapin),
                    icon = Icons.Link,
                    iconColor = bangumiColor,
                    onClick = { showBangumiLoginDialog = true }
                )
            }
        }

        SectionHeader(stringResource(R.string.profile_platform_about, "Bangumi"))
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KitsugiPlatformLogo(platformId = "bangumi", size = 20.dp)
                    Text(
                        text = stringResource(R.string.profile_platform_integration, "Bangumi"),
                        color = KitsugiColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Text(
                    text = stringResource(R.string.bangumi_bgm_tv_v0_api_ile_kayit_arama_sezon_sira_kesfi_bolum_lis) +
                        stringResource(R.string.koleksiyon_durumu_0_10_puan_ve_bolum_bazli) +
                        stringResource(R.string.bangumi_custom_app_registration),
                    color = KitsugiColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }

    if (showBangumiLoginDialog) {
        KitsugiBangumiLoginDialog(
            onDismiss = { showBangumiLoginDialog = false },
            onLogin = profile.onLoginBangumi
        )
    }
}
// ─────────────────────────────────────────────────────────────────────────────
// 7. TMDB kullanıcı watchlist'i (katalog oturumundan ayrı)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun TmdbSettingsContent(profile: ProfileSettings) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .kitsugiVerticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ConnectionStatusCard(
            platformName = "TMDB",
            platformId = "tmdb",
            username = profile.tmdbUsername,
            isConnected = profile.isTmdbConnected
        )

        SectionHeader(stringResource(R.string.settings_section_actions))
        SettingsNavCard {
            if (profile.isTmdbConnected) {
                KitsugiSettingsItem(
                    title = "TMDB hesabını tam eşitle",
                    description = if (profile.isTmdbImportRunning) {
                        "Watchlist, favoriler ve puanlar getiriliyor..."
                    } else {
                        "TMDB hesap snapshot'ını al; ilerlemeni ve notlarını koru"
                    },
                    icon = Icons.Rounded.Sync,
                    iconColor = KitsugiColors.AccentGreen,
                    onClick = { if (!profile.isTmdbImportRunning) profile.onTmdbImportClick() }
                )
                KitsugiSettingsDivider()
                KitsugiSettingsSwitchItem(
                    title = stringResource(R.string.otomatik_esitleme),
                    description = "Kitsugi değişikliklerini TMDB Watchlist, favori ve puanına yaz",
                    icon = Icons.Rounded.CloudSync,
                    iconColor = KitsugiColors.AccentBlue,
                    checked = profile.syncEnabledTmdb,
                    onCheckedChange = profile.onSyncEnabledTmdbChanged,
                    platformId = "tmdb"
                )
                KitsugiSettingsDivider()
                KitsugiSettingsItem(
                    title = "TMDB bağlantısını kes",
                    description = "TMDB oturumunu uygulamadan kaldır; liste kayıtlarını silme",
                    icon = Icons.Rounded.LinkOff,
                    iconColor = KitsugiColors.AccentRed,
                    onClick = profile.onTmdbAuthClick
                )
            } else {
                KitsugiSettingsItem(
                    title = "TMDB hesabını bağla",
                    description = "TMDB'de Kitsugi'ye izin ver ve Watchlist'i içe aktar",
                    icon = Icons.Rounded.Link,
                    iconColor = KitsugiColors.AccentBlue,
                    onClick = profile.onTmdbAuthClick
                )
            }
        }
        Text(
            text = "TMDB kullanıcı oturumu Watchlist, favori ve puan okuma/yazma içindir; katalog API anahtarı hesap girişi değildir. Tam snapshot ilerlemeni ve notlarını korur. TMDB'de Kitsugi'nin Watching/Completed durumları yoktur: Planlandı Watchlist'e, favori ve puan kendi alanlarına eşlenir. v3 özel liste yazımı film içindir; TV/karışık özel listeler v4 kullanıcı tokenı gerektirir.",
            color = KitsugiColors.TextSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 8. Çapraz Eşitleme (Cross-Sync) Alt Sayfası
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun CrossSyncSettingsContent(profile: ProfileSettings) {
    var showCrossSyncDialog by remember { mutableStateOf(false) }
    var showErrorCodeReference by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val connectedCount = (if (profile.isAniListConnected) 1 else 0) +
            (if (profile.isMalConnected) 1 else 0) +
            (if (profile.isSimklConnected) 1 else 0) +
            (if (profile.isKitsuConnected) 1 else 0) +
            (if (profile.isShikimoriConnected) 1 else 0) +
            (if (profile.isBangumiConnected) 1 else 0) +
            (if (profile.isTmdbConnected) 1 else 0)

    val connectedNames = mutableListOf<String>().apply {
        if (profile.isAniListConnected) add("AniList")
        if (profile.isMalConnected) add("MyAnimeList")
        if (profile.isSimklConnected) add("Simkl")
        if (profile.isKitsuConnected) add("Kitsu")
        if (profile.isShikimoriConnected) add("Shikimori")
        if (profile.isBangumiConnected) add("Bangumi")
        if (profile.isTmdbConnected) add("TMDB")
    }.joinToString(", ")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .kitsugiVerticalScroll(scrollState)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Bilgilendirme Kartı
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(KitsugiColors.AccentOrange.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.CloudSync,
                            contentDescription = null,
                            tint = KitsugiColors.AccentOrange,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.inline_cross_platform_sync_a7a05f2),
                            color = KitsugiColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = stringResource(R.string.bagli_hesaplar_x_6, connectedCount),
                            color = KitsugiColors.TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
                Text(
                    text = if (connectedCount >= 2)
                        stringResource(R.string.bagli_olan_x_hesaplarinizdaki_kutuphaneler_birbirleriyle_iki_yon, connectedNames)
                    else
                        stringResource(R.string.cross_sync_needs_two_accounts),
                    color = KitsugiColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
                CrossSyncDisclaimerText(full = true)
            }
        }

        if (connectedCount >= 2) {
            SectionHeader(stringResource(R.string.cross_sync_quick_action))
            SettingsNavCard {
                val syncDesc = when {
                    profile.crossSyncState.isRunning -> profile.crossSyncState.currentStep +
                        if (profile.crossSyncState.totalItems > 0) " (${(profile.crossSyncState.progressPercent * 100).toInt()}%)" else ""
                    profile.crossSyncState.startedAt != null -> stringResource(R.string.cross_sync_report_details)
                    else -> stringResource(R.string.cross_sync_sync_all_services)
                }

                KitsugiSettingsItem(
                    title = when {
                        profile.crossSyncState.isRunning -> stringResource(R.string.esitleme_ayrintilarini_ac)
                        profile.crossSyncState.startedAt != null -> stringResource(R.string.cross_sync_last_report)
                        else -> stringResource(R.string.cross_sync_sync_all)
                    },
                    description = syncDesc,
                    icon = Icons.Cached,
                    iconColor = KitsugiColors.AccentOrange,
                    onClick = {
                        showCrossSyncDialog = true
                        if (!profile.crossSyncState.isRunning && profile.crossSyncState.startedAt == null) {
                            profile.onCrossSyncClick()
                        }
                    }
                )

                if (profile.crossSyncState.isRunning) {
                    Spacer(modifier = Modifier.height(4.dp))
                    if (profile.crossSyncState.totalItems <= 0) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = KitsugiColors.AccentOrange,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { profile.crossSyncState.progressPercent },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = KitsugiColors.AccentOrange,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }

            CrossSyncDisclaimerText(
                full = false,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            SectionHeader(stringResource(R.string.cross_sync_auto_preferences))
            SettingsNavCard {
                if (profile.isAniListConnected) {
                    KitsugiSettingsSwitchItem(
                        title = stringResource(R.string.account_auto_sync_title, "AniList"),
                        description = stringResource(R.string.degisiklikleri_anilist_e_aninda_aktar),
                        icon = Icons.CloudSync,
                        iconColor = KitsugiColors.AccentBlue,
                        checked = profile.syncEnabledAnilist,
                        onCheckedChange = profile.onSyncEnabledAnilistChanged,
                        platformId = "anilist",
                    )
                    KitsugiSettingsDivider()
                }
                if (profile.isMalConnected) {
                    KitsugiSettingsSwitchItem(
                        title = stringResource(R.string.account_auto_sync_title, "MyAnimeList"),
                        description = stringResource(R.string.degisiklikleri_myanimelist_e_aninda_aktar),
                        icon = Icons.CloudSync,
                        iconColor = KitsugiColors.AccentBlue,
                        checked = profile.syncEnabledMal,
                        onCheckedChange = profile.onSyncEnabledMalChanged,
                        platformId = "mal",
                    )
                    KitsugiSettingsDivider()
                }
                if (profile.isSimklConnected) {
                    KitsugiSettingsSwitchItem(
                        title = stringResource(R.string.account_auto_sync_title, "Simkl"),
                        description = stringResource(R.string.degisiklikleri_simkl_e_aninda_aktar),
                        icon = Icons.CloudSync,
                        iconColor = KitsugiColors.AccentBlue,
                        checked = profile.syncEnabledSimkl,
                        onCheckedChange = profile.onSyncEnabledSimklChanged,
                        platformId = "simkl",
                    )
                    KitsugiSettingsDivider()
                }
                if (profile.isKitsuConnected) {
                    KitsugiSettingsSwitchItem(
                        title = stringResource(R.string.account_auto_sync_title, "Kitsu"),
                        description = stringResource(R.string.degisiklikleri_kitsu_ya_aninda_aktar),
                        icon = Icons.CloudSync,
                        iconColor = Color(0xFFFD755C),
                        checked = profile.syncEnabledKitsu,
                        onCheckedChange = profile.onSyncEnabledKitsuChanged,
                        platformId = "kitsu",
                    )
                    KitsugiSettingsDivider()
                }
                if (profile.isShikimoriConnected) {
                    KitsugiSettingsSwitchItem(
                        title = stringResource(R.string.account_auto_sync_title, "Shikimori"),
                        description = stringResource(R.string.degisiklikleri_shikimori_ye_aninda_aktar),
                        icon = Icons.CloudSync,
                        iconColor = Color(0xFF8E44AD),
                        checked = profile.syncEnabledShikimori,
                        onCheckedChange = profile.onSyncEnabledShikimoriChanged,
                        platformId = "shikimori",
                    )
                }
                if (profile.isBangumiConnected) {
                    KitsugiSettingsDivider()
                    KitsugiSettingsSwitchItem(
                        title = stringResource(R.string.account_auto_sync_title, "Bangumi"),
                        description = stringResource(R.string.degisiklikleri_bangumi_ye_aninda_aktar),
                        icon = Icons.CloudSync,
                        iconColor = Color(0xFFF09199),
                        checked = profile.syncEnabledBangumi,
                        onCheckedChange = profile.onSyncEnabledBangumiChanged,
                        platformId = "bangumi",
                    )
                }
            }
        }

        // Hata kodu rehberi — bağlı hesap sayısından bağımsız olarak her zaman erişilebilir.
        SectionHeader(stringResource(R.string.pf_err_ref_title))
        SettingsNavCard {
            KitsugiSettingsItem(
                title = stringResource(R.string.pf_err_ref_open),
                description = stringResource(R.string.pf_err_ref_open_desc),
                icon = Icons.BugReport,
                iconColor = KitsugiColors.AccentBlue,
                onClick = { showErrorCodeReference = true }
            )
        }
    }

    if (showCrossSyncDialog) {
        KitsugiCrossSyncDialog(
            state = profile.crossSyncState,
            onDismiss = { showCrossSyncDialog = false },
            onCancel = profile.onCrossSyncCancel,
            onStart = profile.onCrossSyncClick
        )
    }

    if (showErrorCodeReference) {
        PlatformErrorCodeReferenceDialog(
            onDismiss = { showErrorCodeReference = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Yardımcı Ortak Bileşenler
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AccountHubItem(
    title: String,
    subtitle: String,
    platformId: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = LocalKitsugiAccent.current,
    isConnected: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (platformId != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(KitsugiColors.SurfaceStrong),
                contentAlignment = Alignment.Center
            ) {
                KitsugiPlatformLogo(platformId = platformId, size = 24.dp)
            }
        } else if (icon != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = KitsugiColors.TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isConnected) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(KitsugiColors.AccentGreen)
                    )
                }
                Text(
                    text = subtitle,
                    color = if (isConnected) KitsugiColors.AccentGreen else KitsugiColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Icon(
            imageVector = Icons.ChevronRight,
            contentDescription = null,
            tint = KitsugiColors.TextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ConnectionStatusCard(
    platformName: String,
    platformId: String,
    username: String,
    isConnected: Boolean
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = KitsugiColors.Surface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(KitsugiColors.SurfaceStrong),
                contentAlignment = Alignment.Center
            ) {
                KitsugiPlatformLogo(platformId = platformId, size = 32.dp)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = platformName,
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(3.dp))
                if (isConnected && username.isNotBlank()) {
                    Text(
                        text = username,
                        color = KitsugiColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        text = if (isConnected) stringResource(R.string.ui_tv_account_connected) else stringResource(R.string.ui_tv_account_not_connected),
                        color = if (isConnected) KitsugiColors.AccentGreen else KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isConnected) KitsugiColors.AccentGreen.copy(alpha = 0.15f)
                        else KitsugiColors.SurfaceStrong
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = if (isConnected) stringResource(R.string.notif_diag_connected) else stringResource(R.string.inline_not_connected_2c7d104),
                    color = if (isConnected) KitsugiColors.AccentGreen else KitsugiColors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
