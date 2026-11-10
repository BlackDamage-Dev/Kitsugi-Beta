package com.kitsugi.animelist.ui.components
import com.kitsugi.animelist.ui.components.KitsugiButton

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import com.kitsugi.animelist.ui.utils.tvClickable
import androidx.compose.foundation.focusable
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
import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import com.kitsugi.animelist.ui.utils.requestFocusAfterFrames
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.Intent
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.ui.app.AuthViewModel
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import java.net.URLEncoder
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

/**
 * TV-optimized QR Login Dialog.
 *
 * Displayed automatically on Android TV when user taps a login button.
 * Shows QR codes for AniList, MAL, and Simkl — user scans with phone.
 *
 * On mobile this dialog is never shown (browser intent is used instead).
 */

private fun generateQrBitmapFromUrl(url: String, sizePx: Int = 400): Bitmap? {
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
                bitmap.setPixel(x, y, if (matrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
        bitmap
    }.getOrNull()
}

private enum class QrAuthService(
    val displayName: String,
    val accentColor: Color
) {
    AniList("AniList", Color(0xFF02A9FF)),
    MAL("MyAnimeList", Color(0xFF2E51A2)),
    Simkl("Simkl", Color(0xFFE8BC0E))
}

@Composable
fun KitsugiTvQrLoginDialog(
    authViewModel: AuthViewModel = viewModel(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current
    val firstServiceFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        authViewModel.refreshAuthState()
        try { firstServiceFocusRequester.requestFocusAfterFrames(frames = 3) } catch (_: Exception) {}
    }

    var selectedService by remember { mutableStateOf<QrAuthService?>(null) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    var showWebViewUrl by remember { mutableStateOf<String?>(null) }
    var activeAuthService by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedService) {
        val service = selectedService ?: run {
            qrBitmap = null
            return@LaunchedEffect
        }
        isLoading = true
        qrBitmap = null

        var url = ""
        var simklUserCode: String? = null

        when (service) {
            QrAuthService.AniList -> {
                url = runCatching { ExternalAuthManager.getAuthUrlAndPrepare(context, "anilist") }.getOrDefault("")
            }
            QrAuthService.MAL -> {
                url = runCatching { ExternalAuthManager.getAuthUrlAndPrepare(context, "mal") }.getOrDefault("")
            }
            QrAuthService.Simkl -> {
                runCatching {
                    val (code, verificationUrl) = ExternalAuthManager.getSimklPinSuspend(context)
                    simklUserCode = code
                    url = verificationUrl
                }.onFailure {
                    url = "https://simkl.com/pin"
                }
            }
        }

        qrBitmap = generateQrBitmapFromUrl(url, sizePx = 360)
        isLoading = false

        // Eğer Simkl seçildiyse ve user code alındıysa, polling yap
        if (service == QrAuthService.Simkl && !simklUserCode.isNullOrEmpty()) {
            val code = simklUserCode!!
            while (selectedService == QrAuthService.Simkl) {
                kotlinx.coroutines.delay(5000L)
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
            onOpenInBrowser = {
                // Uygulama içi WebView yerine varsayılan (3. parti) tarayıcıda giriş.
                // URL taze PKCE çiftiyle yeniden üretilir; `malapp://auth` geri dönüş
                // şeması kayıtlı olduğu için tarayıcı işini bitirince uygulamaya döner.
                val svc = activeAuthService
                if (!svc.isNullOrBlank()) {
                    runCatching {
                        val externalUrl = ExternalAuthManager.getAuthUrlAndPrepare(context, svc)
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(externalUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                        android.widget.Toast.makeText(
                            context,
                            KitsugiStrings.get(R.string.oauth_webview_tarayici_toast),
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }.onFailure { e ->
                        android.widget.Toast.makeText(
                            context,
                            KitsugiStrings.get(R.string.hata_x, e.message),
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            },
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .background(KitsugiColors.Surface)
                .padding(28.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // ── Sol Panel: Servis Seçimi ──────────────────────────────────────
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.inline_link_account_13c91f4),
                            color = KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.inline_directly_via_tv_or_with_76b7ec6),
                            color = KitsugiColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Close,
                            contentDescription = stringResource(R.string.kapat),
                            tint = KitsugiColors.TextSecondary
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Servis butonları
                QrAuthService.entries.forEachIndexed { index, service ->
                    val isConnected = when (service) {
                        QrAuthService.AniList -> authViewModel.isAniListConnected
                        QrAuthService.MAL -> authViewModel.isMalConnected
                        QrAuthService.Simkl -> authViewModel.isSimklConnected
                    }
                    val isSelected = selectedService == service

                    QrServiceRow(
                        label = service.displayName,
                        isConnected = isConnected,
                        isSelected = isSelected,
                        accentColor = service.accentColor,
                        onSelect = {
                            selectedService = if (isSelected && !isConnected) null else service
                        },
                        onDisconnect = {
                            val svcName = when (service) {
                                QrAuthService.AniList -> "anilist"
                                QrAuthService.MAL -> "mal"
                                QrAuthService.Simkl -> "simkl"
                            }
                            authViewModel.disconnectExternalAccount(svcName)
                            if (selectedService == service) selectedService = null
                        },
                        modifier = if (index == 0) Modifier.focusRequester(firstServiceFocusRequester) else Modifier
                    )

                    Spacer(Modifier.height(10.dp))
                }
            }

            // ── Sağ Panel: Giriş Seçenekleri / QR Görseli ─────────────────────
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when {
                    isLoading -> {
                        KitsugiPlasmaLoader(size = 48.dp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.inline_preparing_103a0f6),
                            color = KitsugiColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    qrBitmap != null -> {
                        val service = selectedService
                        val serviceColor = service?.accentColor ?: accentColor

                        AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(R.string.inline_sign_in_with_1_s_f9d2e40, service?.displayName.orEmpty()),
                                    color = serviceColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(Modifier.height(16.dp))

                                if (service == QrAuthService.Simkl) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White)
                                            .padding(12.dp)
                                    ) {
                                        Image(
                                            bitmap = qrBitmap!!.asImageBitmap(),
                                            contentDescription = "QR Kod",
                                            modifier = Modifier.size(220.dp)
                                        )
                                    }
                                    Spacer(Modifier.height(14.dp))
                                    Text(
                                        text = stringResource(R.string.inline_scan_with_a_camera_or_30960ab),
                                        color = KitsugiColors.TextSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center
                                    )
                                } else {
                                    KitsugiButton(
                                        onClick = {
                                            val svcName = when (service) {
                                                QrAuthService.AniList -> "anilist"
                                                QrAuthService.MAL -> "mal"
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
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.inline_sign_in_with_tv_browser_8345c87),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    Text(
                                        text = stringResource(R.string.tv_uzerinde_acilacak_guvenli_pencere_ile_giris_yapin_alternatif),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = KitsugiColors.TextSecondary,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White)
                                            .padding(8.dp)
                                    ) {
                                        Image(
                                            bitmap = qrBitmap!!.asImageBitmap(),
                                            contentDescription = "QR Kod",
                                            modifier = Modifier.size(120.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Smartphone,
                                contentDescription = null,
                                tint = LocalKitsugiAccent.current,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.inline_select_a_service_on_the_12d68ed),
                                color = KitsugiColors.TextMuted,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QrServiceRow(
    label: String,
    isConnected: Boolean,
    isSelected: Boolean,
    accentColor: Color,
    onSelect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val borderColor = when {
        isSelected -> accentColor
        isFocused -> Color.White.copy(alpha = 0.4f)
        else -> KitsugiColors.Border
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isSelected -> accentColor.copy(alpha = 0.12f)
                    isFocused -> KitsugiColors.SurfaceStrong
                    else -> KitsugiColors.SurfaceSoft
                }
            )
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .tvClickable(shape = RoundedCornerShape(12.dp)) { if (isConnected) onDisconnect() else onSelect() }
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused && !isConnected) onSelect()
            }
            .focusable()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (isConnected) Color(0xFF4CAF50) else accentColor)
            )
            Column {
                Text(
                    text = label,
                    color = KitsugiColors.TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                )
                Text(
                    text = if (isConnected) stringResource(R.string.ui2_tv_connected_short) else stringResource(R.string.ui_tv_connect_qr),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isConnected) Color(0xFF4CAF50) else KitsugiColors.TextSecondary
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isConnected) KitsugiColors.SurfaceStrong else accentColor)
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = if (isConnected) stringResource(R.string.ui_tv_exit) else stringResource(R.string.ui_tv_select),
                style = MaterialTheme.typography.labelSmall,
                color = if (isConnected) Color.White else com.kitsugi.animelist.ui.theme.onAccentColor(accentColor),
                fontWeight = FontWeight.Bold
            )
        }
    }
}
