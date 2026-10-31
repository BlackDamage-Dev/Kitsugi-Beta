package com.kitsugi.animelist.ui.components

import com.kitsugi.animelist.ui.theme.gradient.CircularProgressIndicator
import com.kitsugi.animelist.data.platform.PlatformErrorCode
import com.kitsugi.animelist.data.platform.PlatformFailureRegistry
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.data.remote.TmdbAccountClient
import com.kitsugi.animelist.ui.theme.KitsugiColors
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

/**
 * Uygulama içi OAuth WebView dialog'u — MAL, AniList, Simkl için.
 *
 * Kök neden düzeltmesi (2026-10-14): Uygulama içi ekranda giriş yapılamıyor,
 * Cloudflare/MAL WAF doğrulaması takılıyordu; yalnızca harici tarayıcı çalışıyordu.
 * Sebep: WebView'e SABİT sahte User-Agent ("Chrome/124, Android 13, Pixel 7") veriliyordu.
 * Cloudflare ve MAL WAF, isteğin gerçek TLS/JS parmak izini beyan edilen UA ile
 * karşılaştırır; uyumsuzluk bot sinyalidir ve doğrulama döngüsü üretir. Artık
 * SİSTEM WebView'inin gerçek varsayılan UA'sı kullanılır (cihazın gerçek WebView
 * sürümünü ve modelini içerir, parmak iziyle tutarlıdır).
 *
 * Davranış:
 *  - Uygulama içinde WebView açılır; User-Agent varsayılan (gerçek) bırakılır.
 *  - CookieManager ile üçüncü parti çerezlere izin verilir.
 *  - onReceivedHttpAuthRequest override edilerek Basic Auth popup'ı engellenir
 *    (kullanıcıya HTML login formu gösterilir).
 *  - malapp://auth redirect'i TEK bir korumalı işleyiciyle yakalanıp (çift takas
 *    koruması) ExternalAuthManager.handleAuthIntent() ile token takas edilir.
 *    Yakalama kanalları: shouldOverrideUrlLoading + onPageStarted +
 *    doUpdateVisitedHistory (JS window.location dönüşleri için).
 *  - Başarılı olunca dialog kapanır ve snackbar ile bilgi verilir.
 */
@Composable
fun KitsugiOAuthWebViewDialog(
    serviceName: String,
    onDismiss: () -> Unit,
    onSuccess: (String) -> Unit = {},
    onError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val accentColor = LocalKitsugiAccent.current

    var authUrl by remember { mutableStateOf<String?>(null) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var platformErrorCode by remember { mutableStateOf<PlatformErrorCode?>(null) }

    val serviceHost = when (serviceName.lowercase()) {
        "mal" -> "myanimelist.net"
        "anilist" -> "anilist.co"
        "tmdb" -> "themoviedb.org"
        else -> "simkl.com"
    }

    /**
     * Uygulama içi WebView'ı bırakıp varsayılan (3. parti) tarayıcıda giriş yapar.
     *
     * Yetki URL'si [ExternalAuthManager.getAuthUrlAndPrepare] ile TAZE PKCE çiftiyle
     * yeniden üretilir; tarayıcı bu URL'e gittiği için yeni üretilen challenge'ın
     * eşleşen verifier'ı hafızaya yazılmış olur (aksi halde token takası reddedilir).
     * `malapp://auth` geri dönüş şemasi AndroidManifest'te kayıtlı olduğu için
     * kullanıcı tarayıcıda işini bitirince otomatik olarak uygulamaya döner ve
     * [ExternalAuthManager.handleAuthIntent] token'ı takas eder.
     */
    val openInExternalBrowser: () -> Unit = {
        if (serviceName.equals("tmdb", ignoreCase = true)) {
            // Reuse the exact request token shown in WebView. Creating a second token here
            // can race the first request and leave the browser/pending-store mismatched.
            val preparedUrl = authUrl
            if (preparedUrl.isNullOrBlank()) {
                errorMessage = KitsugiStrings.get(R.string.tmdb_external_auth_preparing)
            } else {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(preparedUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }.onSuccess {
                    CookieManager.getInstance().flush()
                    onDismiss()
                }.onFailure { error ->
                    errorMessage = error.message ?: KitsugiStrings.get(R.string.tmdb_external_browser_error)
                    platformErrorCode = PlatformFailureRegistry.reportAndResolve("tmdb", error)
                    onError(errorMessage.orEmpty())
                }
            }
        } else {
            try {
                val extUrl = ExternalAuthManager.getAuthUrlAndPrepare(context, serviceName)
                CookieManager.getInstance().flush()
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(extUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
                android.widget.Toast.makeText(
                    context,
                    KitsugiStrings.get(R.string.oauth_webview_tarayici_toast),
                    android.widget.Toast.LENGTH_LONG
                ).show()
                // Dialog kapanır: kullanıcı tarayıcıda; dönüşte deep link ile bağlanır.
                onDismiss()
            } catch (e: Exception) {
                errorMessage = e.message ?: KitsugiStrings.get(R.string.auth_url_olusturulamadi)
                platformErrorCode = PlatformFailureRegistry.reportAndResolve(serviceName, e)
            }
        }
    }

    LaunchedEffect(serviceName) {
        try {
            authUrl = if (serviceName.equals("tmdb", ignoreCase = true)) {
                TmdbAccountClient.createAuthorizationUrl(context)
            } else {
                ExternalAuthManager.getAuthUrlAndPrepare(context, serviceName)
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: KitsugiStrings.get(R.string.auth_url_olusturulamadi)
            platformErrorCode = PlatformFailureRegistry.reportAndResolve(serviceName, e)
            onError(errorMessage ?: KitsugiStrings.get(R.string.hata))
        }
    }

    val title = when (serviceName.lowercase()) {
        "mal", "myanimelist" -> stringResource(R.string.myanimelist_girisi)
        "anilist" -> "AniList Girişi"
        "simkl" -> "Simkl Girişi"
        "tmdb" -> "TMDB Girişi"
        else -> "Hesap Girişi"
    }

    Dialog(
        onDismissRequest = {
            CookieManager.getInstance().flush()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(24.dp)),
            color = KitsugiColors.Surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = KitsugiColors.TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when (serviceName.lowercase()) {
                                "mal" -> stringResource(R.string.mal_hesabinizla_giris_yapin_ve_izin_verin)
                                "anilist" -> "AniList hesabınızla giriş yapın"
                                "tmdb" -> "TMDB hesabınızla giriş yapın ve Kitsugi'ye izin verin"
                                else -> "Hesabınızla giriş yapın"
                            },
                            color = KitsugiColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { webViewInstance?.reload() }) {
                        Icon(Icons.Refresh, contentDescription = stringResource(R.string.yenile), tint = KitsugiColors.TextSecondary)
                    }
                    IconButton(onClick = {
                        CookieManager.getInstance().flush()
                        onDismiss()
                    }) {
                        Icon(Icons.Close, contentDescription = stringResource(R.string.kapat), tint = KitsugiColors.TextPrimary)
                    }
                }

                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = accentColor,
                        trackColor = KitsugiColors.SurfaceStrong.copy(alpha = 0.1f)
                    )
                } else {
                    HorizontalDivider(color = KitsugiColors.Border)
                }

                // Error banner
                if (errorMessage != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = KitsugiColors.AccentRed.copy(alpha = 0.15f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = errorMessage ?: "",
                                color = KitsugiColors.AccentRed,
                                style = MaterialTheme.typography.bodySmall
                            )
                            platformErrorCode?.let { code ->
                                Spacer(modifier = Modifier.height(8.dp))
                                PlatformErrorCodeChip(code = code)
                            }
                        }
                    }
                }

                // WebView Container
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (authUrl != null) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    webViewClient = object : WebViewClient() {
                                    // malapp://auth dönüşü yalnızca BİR KEZ işlenir.
                                    // Aynı yönlendirme birden fazla kanala düşerse
                                    // (shouldOverrideUrlLoading + onPageStarted +
                                    // doUpdateVisitedHistory) kod iki kez takas
                                    // edilip "kod zaten kullanılmış" hatası üretilmesin.
                                    var authRedirectHandled = false

                                    /** OAuth geri dönüş şemasını yakalar; tüketildiyse true döner. */
                                    fun handleAuthRedirect(rawUrl: String?): Boolean {
                                        if (rawUrl == null || !rawUrl.startsWith("malapp://auth")) return false
                                        if (authRedirectHandled) return true
                                        authRedirectHandled = true
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(rawUrl))
                                        ExternalAuthManager.handleAuthIntent(
                                            ctx,
                                            intent,
                                            onSuccess = { svc ->
                                                android.widget.Toast.makeText(
                                                    ctx,
                                                    "${svc.uppercase()} bağlantısı başarılı",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                                onSuccess(svc)
                                                onDismiss()
                                            },
                                            onError = { err ->
                                                errorMessage = err
                                                onError(err)
                                            }
                                        )
                                        return true
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                        if (handleAuthRedirect(url)) {
                                            // WebView özel şemayı yüklemeye çalışıp
                                            // ERR_UNKNOWN_URL_SCHEME hata sayfası üretmesin.
                                            view?.stopLoading()
                                            return
                                        }
                                        isLoading = true
                                        progress = 0
                                    }

                                    /**
                                     * JS `window.location` ile yapılan OAuth geri dönüşleri
                                     * bazı WebView sürümlerinde shouldOverrideUrlLoading'e
                                     * düşmez; bu kanca en güvenilir yakalama noktasıdır.
                                     */
                                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                        if (handleAuthRedirect(url)) {
                                            view?.stopLoading()
                                            return
                                        }
                                        super.doUpdateVisitedHistory(view, url, isReload)
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                        return handleAuthRedirect(url)
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        return handleAuthRedirect(request?.url?.toString())
                                    }

                                    // FIX: MAL'in bazen döndürdüğü HTTP 401 Basic Auth challenge'ını engelle
                                    // Eski tarayıcıda "Oturum açın" popup'ı çıkıyordu ve kullanıcı doğru
                                    // girse bile giriş başarısız oluyordu. WebView'da bu callback'i override edip
                                    // iptal ederek HTML login formunun gösterilmesini sağlıyoruz.
                                    override fun onReceivedHttpAuthRequest(
                                        view: WebView?, handler: android.webkit.HttpAuthHandler?, host: String?, realm: String?
                                    ) {
                                        // Basic Auth popup'ını gösterme, iptal et ve kullanıcıya bilgi ver
                                        handler?.cancel()
                                        errorMessage = KitsugiStrings.get(R.string.mal_temel_kimlik_dogrulama_istedi_beklenmeyen_lutfen_sayfayi_yen, host)
                                    }

                                    override fun onReceivedError(
                                        view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?
                                    ) {
                                        if (request?.isForMainFrame == true) {
                                            val failingUrl = request.url?.toString().orEmpty()
                                            // malapp:// gibi özel şemalı geri dönüşler WebView'te
                                            // yükleme hatası üretir; bunlar gerçek sayfa hatası
                                            // değildir ve yukarıda işlenmiş yönlendirmelerdir.
                                            if (failingUrl.startsWith("malapp://") || failingUrl.startsWith("intent:")) return
                                            errorMessage = KitsugiStrings.get(R.string.sayfa_yuklenemedi_x, error?.description)
                                        platformErrorCode = PlatformFailureRegistry.report(
                                            platformLabel = serviceName,
                                            rawMessage = error?.description?.toString(),
                                        ) ?: PlatformErrorCode.COM_1020
                                        }
                                    }

                                    override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                        isLoading = false
                                        // Cookie sync for Cloudflare bypass
                                        val finalUrl = pageUrl ?: authUrl ?: ""
                                        try {
                                            CookieManager.getInstance().flush()
                                            // Sync to OkHttp if possible
                                            val nh = try {
                                                uy.kohesive.injekt.Injekt.get(eu.kanade.tachiyomi.network.NetworkHelper::class.java)
                                            } catch (_: Exception) {
                                                eu.kanade.tachiyomi.network.NetworkHelper(ctx)
                                            }
                                            nh.cookieJar.syncFromWebView(finalUrl)
                                        } catch (_: Exception) {}
                                    }
                                }
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            progress = newProgress
                                            if (newProgress >= 100) {
                                                isLoading = false
                                            }
                                        }
                                    }
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        databaseEnabled = true
                                        useWideViewPort = true
                                        loadWithOverviewMode = true
                                        setSupportZoom(true)
                                        builtInZoomControls = true
                                        displayZoomControls = false
                                        // KÖK NEDEN DÜZELTMESİ (2026-10-14): userAgentString SABİTLENMEZ.
                                        // Eskiden sabit "Chrome/124 (Android 13, Pixel 7)" sahte UA'sı gönderiliyordu;
                                        // Cloudflare/MAL WAF gerçek TLS+JS parmak iziyle UA'yı karşılaştırıyor,
                                        // uyumsuzluğu bot sanıp "Gerçek kişi misiniz?" doğrulama döngüsüne sokuyordu.
                                        // Varsayılan UA cihazın GERÇEK WebView sürümünü/modelini içerdiği için
                                        // parmak iziyle tutarlıdır ve harici tarayıcıdakine yakın geçiş sağlar.
                                    }
                                    CookieManager.getInstance().setAcceptCookie(true)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                    loadUrl(authUrl!!)
                                    webViewInstance = this
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (errorMessage == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = accentColor)
                        }
                    }
                }

                // Footer
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
                ) {
                    Text(
                        text = KitsugiStrings.get(R.string.guvenli_baglanti_x, serviceHost),
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = KitsugiStrings.get(R.string.oauth_webview_tarayiciya_devam_ettir),
                        color = KitsugiColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Her zaman görünür çıkış yolu: uygulama içi WebView sıkışırsa
                        // (Cloudflare doğrulaması, boş ekran, döngüsel yönlendirme)
                        // kullanıcı tek dokunuşla varsayılan tarayıcıda giriş yapar.
                        OutlinedButton(
                            onClick = openInExternalBrowser,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                Icons.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = accentColor
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = KitsugiStrings.get(R.string.inline_open_in_browser_eed20ab),
                                color = accentColor,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = {
                                CookieManager.getInstance().flush()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                        ) {
                            Text(stringResource(R.string.kapat), color = KitsugiColors.Background, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
