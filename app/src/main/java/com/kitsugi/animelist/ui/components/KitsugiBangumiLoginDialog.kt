package com.kitsugi.animelist.ui.components

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.ButtonDefaults
import com.kitsugi.animelist.ui.theme.gradient.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import com.kitsugi.animelist.ui.theme.gradient.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
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
import android.widget.Toast
import com.kitsugi.animelist.data.auth.BangumiApiClient
import com.kitsugi.animelist.data.auth.BangumiAuthManager
import com.kitsugi.animelist.data.auth.BangumiAuthStore
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.ui.theme.KitsugiColors
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.platform.ListPlatform
import com.kitsugi.animelist.utils.KitsugiStrings

/**
 * Bangumi (bgm.tv) hesap bağlama diyaloğu.
 *
 * Shikimori diyaloğuyla ([KitsugiShikimoriLoginDialog]) aynı etkileşim deseni, ancak
 * **önemli bir farkla**: Bangumi'de paylaşılan bir varsayılan uygulama kaydı YOKTUR.
 * Bangumi'nin token ucu `client_secret` istediği için her uygulamanın kendi kaydını
 * https://bgm.tv/dev/app adresinden yapması gerekir (Aniyomi, Mihon, Komikku, Yokai ve
 * Suwayomi'nin her biri ayrı App ID/Secret kullanır). Bu yüzden kimlik bilgileri alanı
 * varsayılan olarak AÇIK gelir ve boşken "1-Tık Giriş" devre dışıdır.
 *
 * Akış:
 *  1. Kullanıcı App ID + App Secret girer → [BangumiAuthStore.saveCredentials]
 *  2. Tarayıcıda `bgm.tv/oauth/authorize` açılır → kullanıcı 允许 (İzin Ver) der
 *  3. `kitsugi://bangumi-auth?code=...` deep link'i uygulamaya döner
 *     ([ExternalAuthManager.handleAuthIntent] → [com.kitsugi.animelist.data.auth.BangumiAuthManager])
 *  4. Deep link açılmazsa kullanıcı adres çubuğundaki URL'yi 2. adıma yapıştırır
 */
@Composable
fun KitsugiBangumiLoginDialog(
    onDismiss: () -> Unit,
    onLogin: (clientId: String, clientSecret: String, authCode: String, onComplete: (Boolean, String?) -> Unit) -> Unit = { _, _, _, onComplete ->
        onComplete(false, "Bangumi giriş işleyicisi bağlı değil")
    }
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    // Bangumi marka rengi — Aniyomi'nin ic_tracker_bangumi rengiyle aynı: rgb(240,145,153)
    val bangumiBrandColor = Color(0xFFF09199)

    var clientId by remember { mutableStateOf(BangumiAuthStore.getClientId(context)) }
    var clientSecret by remember { mutableStateOf(BangumiAuthStore.getClientSecret(context)) }
    var authCode by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(!BangumiAuthStore.hasCredentials(context)) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    // ── Kişisel erişim anahtarı (PAT) ─────────────────────────────────────────
    // OAuth yolunun yanında, uygulama kaydı gerektirmeyen ikinci bir bağlantı yolu.
    // OAuth token'larında Bangumi'nin `scope` alanı JSON dizisi olarak yazılabiliyor;
    // sunucu bunu "kapsamsız" sayarsa koleksiyon yazıları 403 ile reddedilir. PAT'lerde
    // `scope` hiç yazılmaz → sunucu "legacy" kabul eder → yazma çalışır. Ayrıca
    // yenileme akışı olmadığı için "refresh başarısız" durumu da oluşmaz.
    var patToken by remember { mutableStateOf("") }
    var patBusy by remember { mutableStateOf(false) }

    // ── Bağlantı/yazma izni testi ────────────────────────────────────────────
    var diagBusy by remember { mutableStateOf(false) }
    var diagReport by remember { mutableStateOf<List<String>?>(null) }

    // Otomatik deep link ile giriş tamamlandığında diyaloğu kapat; hataları göster.
    LaunchedEffect(Unit) {
        ExternalAuthManager.authEvents.collect { event ->
            when (event) {
                is ExternalAuthManager.AuthEvent.Success ->
                    if (event.serviceName.equals("bangumi", ignoreCase = true)) {
                        isLoading = false
                        onDismiss()
                    }
                is ExternalAuthManager.AuthEvent.Error -> {
                    if (isLoading || event.message.contains("bangumi", ignoreCase = true)) {
                        isLoading = false
                        errorMessage = event.message
                    }
                }
                else -> Unit
            }
        }
    }

    val credentialsReady = clientId.isNotBlank() && clientSecret.isNotBlank()

    fun persistCredentials() {
        BangumiAuthStore.saveCredentials(context, clientId, clientSecret)
    }

    fun openInBrowser(redirectUri: String) {
        errorMessage = null
        if (clientId.isBlank() || clientSecret.isBlank()) {
            errorMessage = KitsugiStrings.get(R.string.once_app_id_ve_app_secret_alanlarini_doldurun_bkz_ozel_api_anaht)
            showAdvanced = true
            return
        }
        persistCredentials()
        BangumiAuthStore.savePendingRedirectUri(context, redirectUri)
        val authUrl = BangumiApiClient.buildAuthorizeUrl(
            clientId = clientId.trim(),
            redirectUri = redirectUri,
            state = java.util.UUID.randomUUID().toString().take(8)
        )
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)))
        }.onFailure {
            errorMessage = KitsugiStrings.get(R.string.tarayici_acilamadi_x, it.message)
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
                .border(1.dp, bangumiBrandColor.copy(alpha = 0.35f), RoundedCornerShape(24.dp)),
            color = KitsugiColors.Surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Başlık
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(bangumiBrandColor.copy(alpha = 0.15f))
                        .border(1.dp, bangumiBrandColor.copy(alpha = 0.45f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "番", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = bangumiBrandColor)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = KitsugiStrings.get(R.string.inline_connect_to_bangumi_a32d363),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = KitsugiColors.TextPrimary
                    )
                )

                Text(
                    text = KitsugiStrings.get(R.string.inline_sync_your_bgm_tv_collection_10a1831),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = KitsugiColors.TextSecondary,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Hata kutusu
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
                                    PlatformErrorCodeHint(message = errorMessage, platform = ListPlatform.BANGUMI)
                                }
                            }
                        }
                    }

                    // Önerilen yol: Kişisel Erişim Anahtarı (个人令牌)
                    // Uygulama kaydı gerekmez; `next.bgm.tv/demo/access-token/create`
                    // sayfasından üretilir. OAuth token'larındaki kapsam (scope) sorunu
                    // bu yolda oluşmaz.
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = bangumiBrandColor.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, bangumiBrandColor.copy(alpha = 0.55f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.VpnKey,
                                    contentDescription = null,
                                    tint = bangumiBrandColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = KitsugiStrings.get(R.string.bangumi_pat_bolum_baslik),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = bangumiBrandColor
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = KitsugiStrings.get(R.string.bangumi_pat_bolum_aciklama),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = KitsugiColors.TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = patToken,
                                onValueChange = {
                                    patToken = it.trim()
                                    errorMessage = null
                                },
                                label = { Text(KitsugiStrings.get(R.string.bangumi_pat_anahtar_etiketi)) },
                                trailingIcon = {
                                    KitsugiTonalButton(
                                        onClick = {
                                            val text = clipboard.getText()?.text.orEmpty().trim()
                                            if (text.isNotBlank()) {
                                                patToken = BangumiAuthManager.sanitizePersonalToken(text)
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
                                enabled = !patBusy && !isLoading,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse(BangumiApiClient.ACCESS_TOKEN_CREATE_URL)
                                            )
                                        )
                                    }
                                }) {
                                    Icon(Icons.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(KitsugiStrings.get(R.string.bangumi_pat_olustur_linki), fontSize = 11.sp)
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                KitsugiButton(
                                    onClick = {
                                        val clean = BangumiAuthManager.sanitizePersonalToken(patToken)
                                        when {
                                            clean.isBlank() -> {
                                                errorMessage = KitsugiStrings.get(R.string.bangumi_pat_bos)
                                            }
                                            else -> {
                                                patBusy = true
                                                errorMessage = null
                                                diagReport = null
                                                BangumiAuthManager.connectWithPersonalToken(context, clean) { success, error ->
                                                    patBusy = false
                                                    if (success) {
                                                        // `saveManualToken` zaten
                                                        // `emitBangumiSuccess()` yayınlar; AppRoot'un
                                                        // authEvents dinleyicisi koleksiyon aktarımını
                                                        // başlatır (çift olay göndermiyoruz).
                                                        Toast.makeText(
                                                            context,
                                                            KitsugiStrings.get(R.string.bangumi_pat_baglanti_basarili),
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                        onDismiss()
                                                    } else {
                                                        errorMessage = error
                                                            ?: KitsugiStrings.get(R.string.bangumi_baglantisi_basarisiz_oldu)
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    enabled = !patBusy && !isLoading && patToken.isNotBlank(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    if (patBusy) {
                                        KitsugiPlasmaLoader(size = 16.dp)
                                    } else {
                                        Text(
                                            text = KitsugiStrings.get(R.string.bangumi_pat_baglan_butonu),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // OAuth yolu ile PAT yolu arasında ayraç
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = KitsugiColors.Border.copy(alpha = 0.7f)
                        )
                        Text(
                            text = KitsugiStrings.get(R.string.bangumi_pat_veya_oauth),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = KitsugiColors.TextSecondary,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = KitsugiColors.Border.copy(alpha = 0.7f)
                        )
                    }

                    // 0. Adım: Uygulama kaydı (App ID / App Secret)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = KitsugiColors.SurfaceElevated.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, bangumiBrandColor.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Key,
                                    contentDescription = null,
                                    tint = bangumiBrandColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = KitsugiStrings.get(R.string.inline_step_1_bangumi_app_keys_6ba8d2a),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = bangumiBrandColor
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = KitsugiStrings.get(R.string.bangumi_her_uygulamanin_kendi_kaydini_yapmasini_ister) +
                                    KitsugiStrings.get(R.string.paylasilan_bir_varsayilan_anahtar_yoktur) +
                                    "bgm.tv hesabınızla oturum açıp 开发者平台 → 我的应用 → 创建应用 adımlarını izleyin. " +
                                    "\"回调地址\" (Redirect URI) alanını boş bırakın ya da birebir " +
                                    "${BangumiApiClient.DEEP_LINK_REDIRECT_URI} yazın. " +
                                    "Oluşan App ID ve App Secret'ı aşağıdaki \"Özel API Anahtarları\" bölümüne girin.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = KitsugiColors.TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(BangumiApiClient.DEV_APP_URL))
                                        )
                                    }
                                }) {
                                    Icon(Icons.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(KitsugiStrings.get(R.string.inline_bgm_tv_dev_app_app_2a4f7d4), fontSize = 11.sp)
                                }
                                if (credentialsReady) {
                                    Text(
                                        text = KitsugiStrings.get(R.string.inline_keys_are_ready_e64daca),
                                        fontSize = 11.sp,
                                        color = KitsugiColors.AccentGreen,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // 1. Adım: Yetkilendirme
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = KitsugiColors.SurfaceElevated.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, bangumiBrandColor.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.VpnKey,
                                    contentDescription = null,
                                    tint = bangumiBrandColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = KitsugiStrings.get(R.string.inline_step_2_authorize_with_your_a9c58f1),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = bangumiBrandColor
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = KitsugiStrings.get(R.string.tarayicida_bangumi_oturumunuzla_izin_ver_dugmesine_bastiginizda) +
                                    KitsugiStrings.get(R.string.uygulama_otomatik_olarak_baglanir_yetki_kodu_60_saniye_gecerlidi),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = KitsugiColors.TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            KitsugiButton(
                                onClick = { openInBrowser(BangumiApiClient.DEEP_LINK_REDIRECT_URI) },
                                enabled = !isLoading && credentialsReady,
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

                            KitsugiTonalButton(
                                onClick = { openInBrowser(BangumiApiClient.FALLBACK_DEEP_LINK_REDIRECT_URI) },
                                enabled = !isLoading && credentialsReady,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = bangumiBrandColor.copy(alpha = 0.15f),
                                    contentColor = bangumiBrandColor
                                )
                            ) {
                                Icon(Icons.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = KitsugiStrings.get(R.string.inline_alternative_scheme_aniyomi_889ac71),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = KitsugiStrings.get(R.string.tarayici_sayfa_acilamiyor_derse_endiselenmeyin_adres_cubugundaki) +
                                    KitsugiStrings.get(R.string.kitsugi_bangumi_auth_code_kopyalayip_3_adima_yapistirmaniz_yeter),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = KitsugiColors.TextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            )
                        }
                    }

                    // 2. Adım: Kod yapıştırma
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
                                    text = KitsugiStrings.get(R.string.inline_step_3_paste_the_authorization_4edf1e8),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = KitsugiColors.TextPrimary
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = KitsugiStrings.get(R.string.deep_link_calismadiysa_izin_verdikten_sonra_ekranda_adres_cubugu) +
                                    KitsugiStrings.get(R.string.kodu_veya_tam_adresi_buraya_yapistirin),
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
                                    authCode = BangumiApiClient.sanitizeAuthCode(it)
                                    errorMessage = null
                                },
                                label = { Text("Yetkilendirme Kodu (Code)") },
                                placeholder = { Text(stringResource(R.string.tarayicidan_aldiginiz_kod)) },
                                trailingIcon = {
                                    KitsugiTonalButton(
                                        onClick = {
                                            val text = clipboard.getText()?.text.orEmpty().trim()
                                            if (text.isNotBlank()) {
                                                authCode = BangumiApiClient.sanitizeAuthCode(text)
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

                    // Bağlantı + yazma izni testi
                    // "Listeye Ekle çalışmıyor" şikâyetinin kökenini ekranda görünür kılar:
                    // token geçerli mi, koleksiyona yazma izni var mı (403 mü, 404 mü).
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = KitsugiColors.SurfaceElevated.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, KitsugiColors.Border)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Tune,
                                    contentDescription = null,
                                    tint = KitsugiColors.TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = KitsugiStrings.get(R.string.bangumi_test_baslik),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = KitsugiColors.TextPrimary
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            KitsugiTonalButton(
                                onClick = {
                                    diagBusy = true
                                    diagReport = null
                                    scope.launch {
                                        val report = runCatching {
                                            BangumiAuthManager.diagnose(context)
                                        }.getOrElse { listOf("HATA: " + (it.message ?: "?")) }
                                        diagReport = report
                                        diagBusy = false
                                    }
                                },
                                enabled = !diagBusy && !isLoading,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (diagBusy) {
                                    KitsugiPlasmaLoader(size = 16.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = KitsugiStrings.get(R.string.bangumi_test_calisiyor),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                } else {
                                    Text(
                                        text = KitsugiStrings.get(R.string.bangumi_test_butonu),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            diagReport?.let { report ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    report.forEach { line ->
                                        Text(
                                            text = line,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp,
                                                color = if (line.startsWith("HATA")) {
                                                    KitsugiColors.AccentRed
                                                } else {
                                                    KitsugiColors.TextSecondary
                                                }
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Özel API anahtarları
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
                                    text = KitsugiStrings.get(R.string.inline_custom_api_keys_app_id_c00c55e),
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
                                OutlinedTextField(
                                    value = clientId,
                                    onValueChange = {
                                        clientId = it.trim()
                                        errorMessage = null
                                    },
                                    label = { Text("App ID (client_id)") },
                                    placeholder = { Text("bgm...") },
                                    singleLine = true,
                                    enabled = !isLoading,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = clientSecret,
                                    onValueChange = {
                                        clientSecret = it.trim()
                                        errorMessage = null
                                    },
                                    label = { Text("App Secret (client_secret)") },
                                    placeholder = { Text("32 karakterlik gizli anahtar") },
                                    singleLine = true,
                                    enabled = !isLoading,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text(
                                    text = KitsugiStrings.get(R.string.anahtarlar_yalnizca_bu_cihazda_saklanir_ve_dogrudan_bgm_tv_ile_p) +
                                        KitsugiStrings.get(R.string.kitsugi_sunucusuna_gonderilmez_bir_apk_yi_dagitacaksaniz_anahtar) +
                                        "local.properties üzerinden derlemeye gömmek yerine kullanıcıdan istemek " +
                                        "daha güvenlidir (Bangumi PKCE desteklemediği için gömülen secret çıkarılabilir).",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = KitsugiColors.TextSecondary,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp
                                    )
                                )

                                TextButton(
                                    onClick = {
                                        BangumiAuthStore.clearCredentials(context)
                                        clientId = ""
                                        clientSecret = ""
                                        errorMessage = null
                                    },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text(KitsugiStrings.get(R.string.inline_clear_saved_keys_2e9198b), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Eylemler
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
                            val cleanCode = BangumiApiClient.sanitizeAuthCode(authCode)
                            when {
                                clientId.isBlank() || clientSecret.isBlank() -> {
                                    showAdvanced = true
                                    errorMessage = KitsugiStrings.get(R.string.app_id_ve_app_secret_gereklidir_bgm_tv_dev_app)
                                }
                                cleanCode.isBlank() -> {
                                    errorMessage = KitsugiStrings.get(R.string.lutfen_tarayicidan_aldiginiz_yetkilendirme_kodunu_girin)
                                }
                                else -> {
                                    isLoading = true
                                    errorMessage = null
                                    persistCredentials()
                                    onLogin(clientId.trim(), clientSecret.trim(), cleanCode) { success, error ->
                                        isLoading = false
                                        if (success) onDismiss()
                                        else errorMessage = error ?: KitsugiStrings.get(R.string.bangumi_baglantisi_basarisiz_oldu)
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
