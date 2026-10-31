package com.kitsugi.animelist.ui.tv.qrlogin

import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import com.kitsugi.animelist.R
import androidx.compose.ui.res.stringResource
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import com.kitsugi.animelist.ui.theme.gradient.Text
import android.content.Intent
import android.net.Uri
import com.kitsugi.animelist.ui.components.KitsugiWebViewDialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.ui.app.AuthViewModel
import com.kitsugi.animelist.ui.theme.KitsugiTvTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder
import com.kitsugi.animelist.utils.KitsugiStrings

// ── QR kod üretme yardımcısı (ZXing) ─────────────────────────────────────────
private fun generateQrBitmap(url: String, sizePx: Int = 400): Bitmap? {
    return runCatching {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val writer = QRCodeWriter()
        val matrix = writer.encode(url, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bitmap
    }.getOrNull()
}

// ── Servis tanımı ─────────────────────────────────────────────────────────────
private enum class TvAuthService(
    val displayName: String,
    val colorHex: Long
) {
    AniList("AniList", 0xFF02A9FF),
    MAL("MyAnimeList", 0xFF2E51A2),
    Simkl("Simkl", 0xFFE8BC0E)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvQrLoginScreen(
    authViewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Auth durumunu yenile
    LaunchedEffect(Unit) { authViewModel.refreshAuthState() }

    var selectedService by remember { mutableStateOf<TvAuthService?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var currentQrUrl by remember { mutableStateOf("") }

    var showWebViewUrl by remember { mutableStateOf<String?>(null) }
    var activeAuthService by remember { mutableStateOf<String?>(null) }

    // Seçilen servis değiştiğinde QR'ı oluştur ve Simkl ise polling başlat
    LaunchedEffect(selectedService) {
        val service = selectedService ?: run { qrBitmap = null; return@LaunchedEffect }

        var url = ""
        var simklUserCode: String? = null

        when (service) {
            TvAuthService.AniList -> {
                url = runCatching { ExternalAuthManager.getAuthUrlAndPrepare(context, "anilist") }.getOrDefault("")
            }
            TvAuthService.MAL -> {
                url = runCatching { ExternalAuthManager.getAuthUrlAndPrepare(context, "mal") }.getOrDefault("")
            }
            TvAuthService.Simkl -> {
                // Simkl için PIN flow kullanıyoruz — önce PIN al, URL'yi QR'a bas
                runCatching {
                    val (code, verificationUrl) = ExternalAuthManager.getSimklPinSuspend(context)
                    simklUserCode = code
                    url = verificationUrl
                }.onFailure {
                    url = "https://simkl.com/pin"
                }
            }
        }

        currentQrUrl = url
        qrBitmap = generateQrBitmap(url, sizePx = 320)

        // Eğer Simkl seçildiyse ve user code alındıysa, polling yap
        if (service == TvAuthService.Simkl && !simklUserCode.isNullOrEmpty()) {
            val code = simklUserCode!!
            while (selectedService == TvAuthService.Simkl) {
                delay(5000L)
                val success = runCatching {
                    ExternalAuthManager.pollSimklTokenSuspend(context, code)
                }.getOrDefault(false)

                if (success) {
                    android.widget.Toast.makeText(context, KitsugiStrings.get(R.string.simkl_baglantisi_basarili), android.widget.Toast.LENGTH_SHORT).show()
                    authViewModel.refreshAuthState()
                    // Profil bilgisini çekip settings'e yaz
                    runCatching {
                        val token = ExternalAuthManager.getSimklToken(context)
                        if (!token.isNullOrBlank()) {
                            val settingsDataStore = com.kitsugi.animelist.data.settings.SettingsDataStore(context)
                            val profile = com.kitsugi.animelist.data.auth.SimklImportManager.fetchUserProfile(token)
                            settingsDataStore.setSimklProfileInfo(
                                username = profile.name,
                                profileImageUri = profile.avatarUrl.orEmpty(),
                                bannerImageUri = ""
                            )
                        }
                    }
                    selectedService = null
                    break
                }
            }
        }
    }

    if (showWebViewUrl != null) {
        val title = when (activeAuthService) {
            "anilist" -> stringResource(R.string.anilist_girisi)
            "mal" -> "MyAnimeList Girişi"
            else -> "Web Girişi"
        }
        KitsugiWebViewDialog(
            title = title,
            url = showWebViewUrl!!,
            onDismiss = {
                showWebViewUrl = null
                activeAuthService = null
                authViewModel.refreshAuthState()
            },
            onUrlRedirect = { redirectUrl ->
                if (redirectUrl.startsWith("malapp://auth")) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(redirectUrl))
                    ExternalAuthManager.handleAuthIntent(context, intent,
                        onSuccess = { serviceName ->
                            android.widget.Toast.makeText(context, KitsugiStrings.get(R.string.x_baglantisi_basarili, if (serviceName == "anilist") "AniList" else "MyAnimeList"), android.widget.Toast.LENGTH_SHORT).show()
                            authViewModel.refreshAuthState()
                        },
                        onError = { error ->
                            android.widget.Toast.makeText(context, KitsugiStrings.get(R.string.hata_x, error), android.widget.Toast.LENGTH_LONG).show()
                        }
                    )
                    showWebViewUrl = null
                    activeAuthService = null
                    true
                } else {
                    false
                }
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = KitsugiTvTokens.Spacing.screenHorizontal,
                vertical = KitsugiTvTokens.Spacing.screenVertical
            ),
        horizontalArrangement = Arrangement.spacedBy(KitsugiTvTokens.Spacing.screenHorizontal)
    ) {
        // ── Sol Panel: Servis Seçimi ───────────────────────────────────────────
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.inline_link_account_13c91f4),
                style = MaterialTheme.typography.headlineMedium,
                color = androidx.compose.ui.graphics.Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = stringResource(R.string.inline_connect_directly_through_your_tv_714ffdd),
                style = MaterialTheme.typography.bodyMedium,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Servis butonları
            TvAuthService.entries.forEach { service ->
                val isConnected = when (service) {
                    TvAuthService.AniList -> authViewModel.isAniListConnected
                    TvAuthService.MAL     -> authViewModel.isMalConnected
                    TvAuthService.Simkl   -> authViewModel.isSimklConnected
                }
                val isSelected = selectedService == service
                val accentColor = androidx.compose.ui.graphics.Color(service.colorHex)

                TvServiceButton(
                    label = service.displayName,
                    isConnected = isConnected,
                    isSelected = isSelected,
                    accentColor = accentColor,
                    onSelect = { selectedService = if (isSelected) null else service },
                    onDisconnect = {
                        val svcName = when (service) {
                            TvAuthService.AniList -> "anilist"
                            TvAuthService.MAL     -> "mal"
                            TvAuthService.Simkl   -> "simkl"
                        }
                        authViewModel.disconnectExternalAccount(svcName)
                        if (selectedService == service) selectedService = null
                    }
                )

                Spacer(modifier = Modifier.height(KitsugiTvTokens.Spacing.itemGap))
            }
        }

        // ── Sağ Panel: Giriş Seçenekleri / QR Görüntüsü ────────────────────────
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(
                visible = qrBitmap != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                qrBitmap?.let { bmp ->
                    val service = selectedService
                    val accentColor = androidx.compose.ui.graphics.Color(
                        service?.colorHex ?: 0xFFFFFFFF
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.inline_sign_in_with_1_s_6d8f7e7, (service?.displayName).toString()),
                            style = MaterialTheme.typography.titleMedium,
                            color = accentColor,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = KitsugiTvTokens.Spacing.contentPadding)
                        )

                        if (service == TvAuthService.Simkl) {
                            Box(
                                modifier = Modifier
                                    .clip(KitsugiTvTokens.Shapes.posterCard as RoundedCornerShape)
                                    .background(androidx.compose.ui.graphics.Color.White)
                                    .padding(12.dp)
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "QR Kodu",
                                    modifier = Modifier.size(220.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(KitsugiTvTokens.Spacing.contentPadding))

                            Text(
                                text = stringResource(R.string.inline_scan_with_your_phone_s_a9830f6),
                                style = MaterialTheme.typography.bodySmall,
                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Button(
                                onClick = {
                                    val svcName = when (service) {
                                        TvAuthService.AniList -> "anilist"
                                        TvAuthService.MAL -> "mal"
                                        else -> ""
                                    }
                                    if (svcName.isNotEmpty()) {
                                        runCatching {
                                            val url = ExternalAuthManager.getAuthUrlAndPrepare(context, svcName)
                                            activeAuthService = svcName
                                            showWebViewUrl = url
                                        }.onFailure { e ->
                                            android.widget.Toast.makeText(context, KitsugiStrings.get(R.string.hata_x, e.message), android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.colors(
                                    containerColor = accentColor,
                                    contentColor = androidx.compose.ui.graphics.Color.White
                                ),
                                modifier = Modifier.padding(bottom = 16.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.inline_sign_in_with_tv_browser_8345c87),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = stringResource(R.string.inline_sign_in_using_the_secure_262b8ee),
                                style = MaterialTheme.typography.bodySmall,
                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .clip(KitsugiTvTokens.Shapes.posterCard as RoundedCornerShape)
                                    .background(androidx.compose.ui.graphics.Color.White)
                                    .padding(8.dp)
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "QR Kodu",
                                    modifier = Modifier.size(120.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (selectedService == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Smartphone,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f),
                        modifier = androidx.compose.ui.Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(KitsugiTvTokens.Spacing.contentPadding))
                    Text(
                        text = stringResource(R.string.inline_select_a_service_on_the_778639b),
                        style = MaterialTheme.typography.bodyLarge,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ── Servis Butonu ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TvServiceButton(
    label: String,
    isConnected: Boolean,
    isSelected: Boolean,
    accentColor: androidx.compose.ui.graphics.Color,
    onSelect: () -> Unit,
    onDisconnect: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val borderColor = if (isSelected) accentColor else androidx.compose.ui.graphics.Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) accentColor.copy(alpha = 0.15f)
                else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
                KitsugiTvTokens.Shapes.posterCard as RoundedCornerShape
            )
            .border(KitsugiTvTokens.Cards.focusedBorderWidth, borderColor, KitsugiTvTokens.Shapes.posterCard as RoundedCornerShape)
            .tvClickable(shape = KitsugiTvTokens.Shapes.posterCard as RoundedCornerShape) {
                if (isConnected) onDisconnect() else onSelect()
            }
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused && !isConnected) {
                    onSelect()
                }
            }
            .padding(horizontal = KitsugiTvTokens.Spacing.contentPadding, vertical = KitsugiTvTokens.Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(KitsugiTvTokens.Shapes.chip as RoundedCornerShape)
                    .background(if (isConnected) androidx.compose.ui.graphics.Color(0xFF4CAF50) else accentColor)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = if (isConnected) stringResource(R.string.ui_tv_connected_tap_exit) else stringResource(R.string.ui_tv_tap_connect_select),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isConnected) androidx.compose.ui.graphics.Color(0xFF4CAF50)
                            else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f)
                )
            }
        }

        Spacer(modifier = Modifier.width(KitsugiTvTokens.Spacing.itemGap))

        // Visual only indicator (non-focusable to prevent D-pad trapping)
        Box(
            modifier = Modifier
                .clip(KitsugiTvTokens.Shapes.posterCard as RoundedCornerShape)
                .background(
                    if (isConnected) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.1f)
                    else accentColor
                )
                .border(
                    1.dp,
                    if (isConnected) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.2f)
                    else androidx.compose.ui.graphics.Color.Transparent,
                    KitsugiTvTokens.Shapes.posterCard as RoundedCornerShape
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = if (isConnected) stringResource(R.string.ui_tv_logout) else stringResource(R.string.ui_tv_show_qr),
                style = MaterialTheme.typography.labelSmall,
                color = androidx.compose.ui.graphics.Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
