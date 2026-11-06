package com.kitsugi.animelist.ui.components
import com.kitsugi.animelist.ui.components.KitsugiButton

import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.KitsugiColors
import eu.kanade.tachiyomi.network.NetworkHelper
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R


@Composable
fun KitsugiWebViewDialog(
    title: String,
    url: String,
    onUrlRedirect: ((String) -> Boolean)? = null,
    onPageFinished: ((String) -> Unit)? = null,
    onOpenInBrowser: (() -> Unit)? = null,
    /** Additional source domains whose cookies must reach OkHttp after cross-domain login. */
    cookieSyncUrls: List<String> = emptyList(),
    onDismiss: () -> Unit
) {
    val accentColor = LocalKitsugiAccent.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentOnPageFinished by rememberUpdatedState(onPageFinished)
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var currentUrl by remember(url) { mutableStateOf(url) }

    fun synchronizeCookies() {
        runCatching {
            CookieManager.getInstance().flush()
            val network = try {
                uy.kohesive.injekt.Injekt.get(NetworkHelper::class.java)
            } catch (_: Exception) {
                NetworkHelper(context)
            }
            (cookieSyncUrls + currentUrl).distinct().forEach(network.cookieJar::syncFromWebView)
        }
    }

    fun closeDialog() {
        synchronizeCookies()
        onDismiss()
    }

    Dialog(
        onDismissRequest = { closeDialog() },
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
                            text = url,
                            color = KitsugiColors.TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { webViewInstance?.reload() }) {
                        Icon(Icons.Refresh, contentDescription = stringResource(R.string.yenile), tint = KitsugiColors.TextSecondary)
                    }
                    IconButton(onClick = { closeDialog() }) {
                        Icon(Icons.Close, contentDescription = stringResource(R.string.kapat), tint = KitsugiColors.TextPrimary)
                    }
                }

                // Progress Bar
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

                // WebView Container
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                // Aynı redirect'in birden fazla kanaldan (onPageStarted +
                                // shouldOverrideUrlLoading + doUpdateVisitedHistory) iki kez
                                // işlenmesini önleyen koruma.
                                var lastRedirectHandledUrl: String? = null
                                fun interceptRedirect(rawUrl: String?): Boolean {
                                    if (rawUrl == null) return false
                                    if (rawUrl == lastRedirectHandledUrl) return true
                                    val consumed = onUrlRedirect?.invoke(rawUrl) == true
                                    if (consumed) lastRedirectHandledUrl = rawUrl
                                    return consumed
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                        if (url != null) currentUrl = url
                                        if (interceptRedirect(url)) {
                                            view?.stopLoading()
                                            return
                                        }
                                        isLoading = true
                                        progress = 0
                                    }

                                    /**
                                     * JS window.location ile yapılan OAuth geri dönüşleri bazı
                                     * WebView sürümlerinde shouldOverrideUrlLoading'e düşmez;
                                     * bu kanca en güvenilir yakalama noktasıdır.
                                     */
                                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                        if (interceptRedirect(url)) {
                                            view?.stopLoading()
                                            return
                                        }
                                        super.doUpdateVisitedHistory(view, url, isReload)
                                    }

                                    override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                        isLoading = false
                                        // WebView cookie'lerini OkHttp CookieJar'a aktar (Cloudflare ve hesap oturumu).
                                        val finalUrl = pageUrl ?: currentUrl
                                        currentUrl = finalUrl
                                        synchronizeCookies()
                                        runCatching { currentOnPageFinished?.invoke(finalUrl) }
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                        if (interceptRedirect(url)) {
                                            return true
                                        }
                                        return super.shouldOverrideUrlLoading(view, url)
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                        if (interceptRedirect(request?.url?.toString())) {
                                            return true
                                        }
                                        return super.shouldOverrideUrlLoading(view, request)
                                    }

                                    // FIX: HTTP Basic Auth popup'ını engelle (MAL OAuth ve bazı eklenti sitelerinde)
                                    override fun onReceivedHttpAuthRequest(
                                        view: WebView?,
                                        handler: android.webkit.HttpAuthHandler?,
                                        host: String?,
                                        realm: String?
                                    ) {
                                        handler?.cancel()
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
                                    // Sabit sahte "Chrome/124" UA'sı, Cloudflare/MAL WAF'ın gerçek
                                    // TLS+JS parmak iziyle UA karşılaştırmasında uyumsuzluk yaratıp
                                    // giriş sayfalarını doğrulama döngüsüne sokuyordu. Sistem
                                    // WebView'inin gerçek varsayılan UA'sı kullanılır.
                                }
                                // Üçüncü parti cookie'lere izin ver (bazı Türk kaynaklarda gerekli)
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                loadUrl(url)
                                webViewInstance = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }


                // Footer Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Uygulama içi WebView sıkışırsa (Cloudflare doğrulaması, boş ekran)
                    // kullanıcı tek dokunuşla varsayılan tarayıcıda devam edebilir.
                    if (onOpenInBrowser != null) {
                        OutlinedButton(
                            onClick = {
                                synchronizeCookies()
                                onOpenInBrowser()
                            },
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
                                text = stringResource(R.string.inline_open_in_browser_eed20ab),
                                color = accentColor,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                    }
                    KitsugiButton(
                        onClick = { closeDialog() },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(stringResource(R.string.tamamlandi), color = KitsugiColors.Surface, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
