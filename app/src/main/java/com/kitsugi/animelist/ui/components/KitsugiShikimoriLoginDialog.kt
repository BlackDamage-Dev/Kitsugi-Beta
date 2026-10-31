package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.ui.theme.gradient.Icon
import com.kitsugi.animelist.ui.theme.gradient.Text
import com.kitsugi.animelist.ui.components.KitsugiTonalButton
import com.kitsugi.animelist.ui.components.KitsugiButton

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import com.kitsugi.animelist.ui.theme.gradient.background
import com.kitsugi.animelist.ui.theme.gradient.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.data.auth.ShikimoriApiClient
import com.kitsugi.animelist.ui.theme.KitsugiColors
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.platform.ListPlatform
import com.kitsugi.animelist.utils.KitsugiStrings

@Composable
fun KitsugiShikimoriLoginDialog(
    onDismiss: () -> Unit,
    onLogin: (clientId: String, clientSecret: String, authCode: String, onComplete: (Boolean, String?) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val shikimoriBrandColor = Color(0xFF8E44AD)

    var clientId by remember {
        mutableStateOf(ExternalAuthManager.getShikimoriClientId(context))
    }
    var clientSecret by remember {
        mutableStateOf(ExternalAuthManager.getShikimoriClientSecret(context))
    }
    var authCode by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    // Otomatik deep-link ile giriş tamamlandığında diyaloğu otomatik kapat;
    // deep-link sırasında oluşan hataları da kullanıcıya göster.
    LaunchedEffect(Unit) {
        ExternalAuthManager.authEvents.collect { event ->
            when (event) {
                is ExternalAuthManager.AuthEvent.Success ->
                    if (event.serviceName.equals("shikimori", ignoreCase = true)) onDismiss()
                is ExternalAuthManager.AuthEvent.Error -> {
                    // Yalnızca Shikimori akışıyla ilgili hataları burada göster.
                    if (isLoading || event.message.contains("shikimori", ignoreCase = true)) {
                        isLoading = false
                        errorMessage = event.message
                    }
                }
                else -> Unit
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, shikimoriBrandColor.copy(alpha = 0.35f), RoundedCornerShape(24.dp)),
            color = KitsugiColors.Surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(shikimoriBrandColor.copy(alpha = 0.15f))
                        .border(1.dp, shikimoriBrandColor.copy(alpha = 0.45f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    KitsugiPlatformLogo(platformId = "shikimori", size = 32.dp, fallbackTint = shikimoriBrandColor)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.inline_connect_to_shikimori_dee71dc),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = KitsugiColors.TextPrimary
                    )
                )

                Text(
                    text = stringResource(R.string.inline_sync_your_shikimori_watchlists_dir_989517b),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = KitsugiColors.TextSecondary,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                // Scrollable content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (errorMessage != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp)),
                            color = KitsugiColors.AccentRed.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, KitsugiColors.AccentRed.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.ErrorOutline,
                                    contentDescription = null,
                                    tint = KitsugiColors.AccentRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = errorMessage ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = KitsugiColors.AccentRed
                                    )
                                    // Kaynağa özgü hata kodu + neden/çözüm (dil dosyasından).
                                    PlatformErrorCodeHint(message = errorMessage, platform = ListPlatform.SHIKIMORI)
                                }
                            }
                        }
                    }

                    // 1. Adım: Tarayıcıda Oturum Aç ve Yetki Ver
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = KitsugiColors.SurfaceElevated.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, shikimoriBrandColor.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.VpnKey,
                                    contentDescription = null,
                                    tint = shikimoriBrandColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.inline_step_1_authorize_with_your_027ff6e),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = shikimoriBrandColor
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.asagidaki_1_tik_otomatik_giris_butonuna_dokunun_tarayicida_shiki),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = KitsugiColors.TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // 1-Tık Otomatik Giriş (Deep Link)
                            KitsugiButton(
                                onClick = {
                                    val targetId = clientId.trim().ifBlank { ShikimoriApiClient.DEFAULT_CLIENT_ID }
                                    errorMessage = null
                                    // Token adımı authorize adımındaki redirect_uri ile eşleşmek zorunda.
                                    ExternalAuthManager.saveShikimoriPendingRedirectUri(context, ShikimoriApiClient.DEEP_LINK_REDIRECT_URI)
                                    val authUrl = ShikimoriApiClient.buildAuthorizeUrl(
                                        targetId,
                                        ShikimoriApiClient.DEEP_LINK_REDIRECT_URI,
                                        ShikimoriApiClient.scopesFor(targetId)
                                    )
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.s1_tik_otomatik_giris_onerilen),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Alternatif: Kod Gösterimi (oob)
                            KitsugiTonalButton(
                                onClick = {
                                    val targetId = clientId.trim().ifBlank { ShikimoriApiClient.DEFAULT_CLIENT_ID }
                                    errorMessage = null
                                    ExternalAuthManager.saveShikimoriPendingRedirectUri(context, ShikimoriApiClient.DEFAULT_REDIRECT_URI)
                                    val authUrl = ShikimoriApiClient.buildAuthorizeUrl(
                                        targetId,
                                        ShikimoriApiClient.DEFAULT_REDIRECT_URI,
                                        ShikimoriApiClient.scopesFor(targetId)
                                    )
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = shikimoriBrandColor.copy(alpha = 0.15f),
                                    contentColor = shikimoriBrandColor
                                )
                            ) {
                                Icon(Icons.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.inline_open_in_browser_get_code_be59fc3),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Redirect URI uyuşmazlığında ne yapılacağını açıkla
                            Text(
                                text = stringResource(R.string.tarayicida_the_requested_redirect_uri_is_malformed_or_doesn_t_ma) +
                                    stringResource(R.string.shikimori_deki_oauth_uygulamanizin_redirect_uri_listesinde_x_bir, ShikimoriApiClient.DEEP_LINK_REDIRECT_URI) +
                                    "Kendi uygulamanızda bu URI'yi ekleyin; farklı bir callback kayıtlıysa aşağıdaki alternatif düğmesini ya da manuel kod akışını kullanın.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = KitsugiColors.TextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(ShikimoriApiClient.APPLICATIONS_URL))
                                            )
                                        }
                                    }
                                ) {
                                    Text(stringResource(R.string.inline_my_shikimori_oauth_apps_05c1e76), fontSize = 11.sp)
                                }
                                TextButton(
                                    onClick = {
                                        val targetId = clientId.trim().ifBlank { ShikimoriApiClient.DEFAULT_CLIENT_ID }
                                        errorMessage = null
                                        ExternalAuthManager.saveShikimoriPendingRedirectUri(context, ShikimoriApiClient.FALLBACK_DEEP_LINK_REDIRECT_URI)
                                        val authUrl = ShikimoriApiClient.buildAuthorizeUrl(
                                            targetId,
                                            ShikimoriApiClient.FALLBACK_DEEP_LINK_REDIRECT_URI,
                                            ShikimoriApiClient.scopesFor(targetId)
                                        )
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))) }
                                    }
                                ) {
                                    Text(stringResource(R.string.inline_alternative_one_tap_kitsugi_e45c65e), fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // 2. Adım: Verilen Kodu Yapıştırın
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = KitsugiColors.SurfaceElevated.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, KitsugiColors.Border)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.ContentPaste,
                                    contentDescription = null,
                                    tint = KitsugiColors.TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.inline_step_2_paste_the_authorization_776dc2e),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = KitsugiColors.TextPrimary
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.inline_after_granting_access_paste_the_70384e6),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = KitsugiColors.TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = authCode,
                                onValueChange = {
                                    authCode = ShikimoriApiClient.sanitizeAuthCode(it)
                                    errorMessage = null
                                },
                                label = { Text("Yetkilendirme Kodu (Code)") },
                                placeholder = { Text(stringResource(R.string.tarayicidan_aldiginiz_kod)) },
                                trailingIcon = {
                                    KitsugiTonalButton(
                                        onClick = {
                                            val text = clipboard.getText()?.text.orEmpty().trim()
                                            if (text.isNotBlank()) {
                                                authCode = ShikimoriApiClient.sanitizeAuthCode(text)
                                                errorMessage = null
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.padding(end = 6.dp)
                                    ) {
                                        Icon(Icons.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.yapistir), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                },
                                singleLine = true,
                                enabled = !isLoading,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Gelişmiş Ayarlar (İsteğe Bağlı)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(KitsugiColors.SurfaceElevated.copy(alpha = 0.3f))
                            .border(1.dp, KitsugiColors.Border.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAdvanced = !showAdvanced }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Tune,
                                    contentDescription = null,
                                    tint = KitsugiColors.TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.inline_custom_api_keys_optional_87a5a8a),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.Medium,
                                        color = KitsugiColors.TextSecondary
                                    )
                                )
                            }
                            KitsugiChevronIcon(
                                expanded = showAdvanced,
                                contentDescription = null,
                                tint = KitsugiColors.TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = showAdvanced,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier.padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.inline_a_ready_to_use_api_7a9b6f9),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = KitsugiColors.TextSecondary,
                                        fontSize = 11.sp
                                    )
                                )

                                OutlinedTextField(
                                    value = clientId,
                                    onValueChange = { clientId = it.trim() },
                                    label = { Text(stringResource(R.string.inline_custom_client_id_89f0f76)) },
                                    placeholder = { Text(ShikimoriApiClient.DEFAULT_CLIENT_ID) },
                                    singleLine = true,
                                    enabled = !isLoading,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = clientSecret,
                                    onValueChange = { clientSecret = it.trim() },
                                    label = { Text(stringResource(R.string.inline_custom_client_secret_1f286e9)) },
                                    placeholder = { Text(ShikimoriApiClient.DEFAULT_CLIENT_SECRET) },
                                    singleLine = true,
                                    enabled = !isLoading,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                TextButton(
                                    onClick = {
                                        ExternalAuthManager.resetShikimoriCredentials(context)
                                        clientId = ShikimoriApiClient.DEFAULT_CLIENT_ID
                                        clientSecret = ShikimoriApiClient.DEFAULT_CLIENT_SECRET
                                    },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Icon(Icons.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.inline_restore_default_keys_ada2a26), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = KitsugiColors.TextSecondary
                        )
                    ) {
                        Text(stringResource(R.string.iptal))
                    }

                    KitsugiButton(
                        onClick = {
                            val cleanCode = ShikimoriApiClient.sanitizeAuthCode(authCode)
                            if (cleanCode.isBlank()) {
                                errorMessage = KitsugiStrings.get(R.string.lutfen_tarayicidan_aldiginiz_yetkilendirme_kodunu_girin)
                            } else {
                                isLoading = true
                                errorMessage = null
                                val targetId = clientId.trim().ifBlank { ShikimoriApiClient.DEFAULT_CLIENT_ID }
                                val targetSecret = clientSecret.trim().ifBlank { ShikimoriApiClient.DEFAULT_CLIENT_SECRET }

                                onLogin(targetId, targetSecret, cleanCode) { success, error ->
                                    isLoading = false
                                    if (success) {
                                        onDismiss()
                                    } else {
                                        errorMessage = error ?: KitsugiStrings.get(R.string.shikimori_baglantisi_basarisiz_oldu)
                                    }
                                }
                            }
                        },
                        enabled = !isLoading && authCode.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) {
                            KitsugiPlasmaLoader(size = 18.dp)
                        } else {
                            Text(stringResource(R.string.baglan), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
