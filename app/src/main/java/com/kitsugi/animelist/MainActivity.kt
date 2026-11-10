package com.kitsugi.animelist

import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.kitsugi.animelist.core.deeplink.DeepLinkHandler
import com.kitsugi.animelist.core.recommendations.TvChannelSyncService
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.data.account.KitsugiAccountClient
import com.kitsugi.animelist.data.account.AccountConfirmationLink
import com.kitsugi.animelist.data.account.AccountErrorFormatter
import com.kitsugi.animelist.data.account.KitsugiAccountRepository
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.kitsugi.animelist.ui.theme.KitsugiAnimeListTheme
import com.kitsugi.animelist.ui.tv.TvRootScreen
import com.kitsugi.animelist.ui.tv.design.KitsugiTvTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var tvChannelSyncService: TvChannelSyncService

    private val formFactor by lazy { DeviceProfile.detect(this) }

    override fun attachBaseContext(newBase: Context) {
        // Dil uygulaması başarısız olursa uygulama AÇILMAMASIN diye her şey
        // koruma altında: hata durumunda sistem diliyle normal şekilde devam.
        val localized = runCatching {
            val tag = LocaleCache.localeTag.takeIf { it != LocaleCache.UNSET }
            if (tag.isNullOrEmpty()) return@runCatching null

            val locale = Locale.forLanguageTag(tag)
            // Bozuk/boş etiket → geçersiz Locale; sistemi zorlamayalım.
            if (locale.language.isNullOrBlank()) return@runCatching null

            Locale.setDefault(locale)
            val config = Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            newBase.createConfigurationContext(config)
        }.getOrNull()

        super.attachBaseContext(localized ?: newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_KitsugiAnimeList)
        super.onCreate(savedInstanceState)
        // Açılış aşaması izi: süreç ilk Compose karesi çizilmeden ölürse rapor bunu gösterir.
        runCatching {
            com.kitsugi.animelist.core.diagnostics.StartupStage.mark(this, "MainActivity.onCreate")
        }
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            runCatching { window.decorView.isForceDarkAllowed = false }
        }

        setContent {
            val settingsDataStore = remember { com.kitsugi.animelist.data.settings.SettingsDataStore(applicationContext) }
            val appSettings by settingsDataStore.settingsFlow.collectAsState(initial = com.kitsugi.animelist.data.settings.AppSettings())

            val darkTheme = when (appSettings.themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            KitsugiAnimeListTheme(
                darkTheme = darkTheme,
                amoledBlack = appSettings.amoledBlack,
                selectedThemeId = appSettings.selectedThemeId,
                customAccentColor = appSettings.customAccentColor,
                customAccentColor2 = appSettings.customAccentColor2,
                customAccentGradientAngle = appSettings.customAccentGradientAngle,
                isTv = formFactor == DeviceFormFactor.TV
            ) {
                CompositionLocalProvider(
                    com.kitsugi.animelist.ui.theme.LocalCardFramesEnabled provides appSettings.cardFramesEnabled
                ) {
                    KitsugiPermissionRequester()

                    // İlk kare çizildi → uygulama gerçekten açıldı. "Açılmadan çöküyor"
                    // teşhis izini temizle; bundan sonraki çökmeler normal rapora düşer.
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        com.kitsugi.animelist.core.diagnostics.StartupStage.clear(applicationContext)
                    }

                    // Çökme kontrolü ANA THREAD'DE DOSYA OKUMASIN (eski hâli arayüzü kilitliyordu).
                    var showCrashRecovery by androidx.compose.runtime.remember {
                        androidx.compose.runtime.mutableStateOf(false)
                    }
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        val hasUnreported = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            runCatching {
                                com.kitsugi.animelist.core.download.DownloadExportNaming
                                    .ensureStandardDownloadDirs(applicationContext)
                            }
                            com.kitsugi.animelist.core.diagnostics.KitsugiCrashLogger.hasUnreadCrash(applicationContext)
                        }
                        if (hasUnreported) showCrashRecovery = true
                    }
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        // Sessiz çökme (native/ANR/OOM) analizi arka planda tamamlanır; kısa bir
                        // gecikmeyle tekrar bakıp kaçırılan raporu da kullanıcıya göster.
                        kotlinx.coroutines.delay(2_500L)
                        val late = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            com.kitsugi.animelist.core.diagnostics.KitsugiCrashLogger.hasUnreadCrash(applicationContext)
                        }
                        if (late) showCrashRecovery = true
                    }

                    if (showCrashRecovery) {
                        com.kitsugi.animelist.ui.components.KitsugiCrashRecoveryDialog(
                            onDismiss = {
                                showCrashRecovery = false
                                // ── HER KAPANIŞ "OKUNDU" SAYILIR ──────────────────────────
                                // Eski davranış: yalnızca diyalogdaki belirli butonlar
                                // markCrashAsRead çağırıyordu. Kullanıcı geri tuşuyla ya da
                                // dışına dokunarak kapattığında bayrak temizlenmiyor, bu
                                // yüzden "Beklenmedik Kapanma" ekranı HER AÇILIŞTA tekrar
                                // geliyor ve hep aynı ESKİ rapor gösteriliyordu. Kullanıcı
                                // bunu "uygulama açılmıyor / çöküyor" olarak yaşıyordu.
                                // Rapor dosyaları SİLİNMEZ; Geliştirici Günlükleri ve
                                // Hakkında > çökme raporu kartından hâlâ erişilebilir.
                                runCatching {
                                    com.kitsugi.animelist.core.diagnostics.KitsugiCrashLogger
                                        .markCrashAsRead(applicationContext)
                                }
                            }
                        )
                    }

                    when (formFactor) {
                        DeviceFormFactor.TV -> {
                            KitsugiTvTheme {
                                TvRootScreen()
                            }
                        }
                        DeviceFormFactor.TABLET,
                        DeviceFormFactor.PHONE -> AppRoot()
                    }
                }
            }
        }

        handleAuthIntent(intent)
        // B1.1: Non-auth deep links (Channels / typed links) park as pending.
        // TvRootScreen drains them once navState and session are ready.
        handleDeepLinkIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // Bildirim izni/ayarı uygulama kapalıyken değişmiş olabilir (kullanıcı sistem
        // ayarlarından kapatıp geri döner). Yalnızca DURUM DEĞİŞTİYSE planlı işi
        // uzlaştır; her açılışta WorkManager isteğini yeniden kurmak gereksiz.
        lifecycleScope.launch {
            runCatching {
                com.kitsugi.animelist.core.notifications.NotificationScheduler
                    .reconcileIfGateChanged(this@MainActivity)
            }
        }
        if (formFactor == DeviceFormFactor.TV) {
            // B1.11: Servisi baslatir (ilk cagirida periyodik job planlanir).
            // Sonraki cagirilar no-op'tur (job zaten planlanmis).
            tvChannelSyncService.start()
            tvChannelSyncService.onForegroundChanged(foreground = true)
        }
    }

    override fun onStop() {
        super.onStop()
        if (formFactor == DeviceFormFactor.TV) {
            com.kitsugi.animelist.core.player.TvTrailerPlayerPoolHolder.get(this).yield()
            // B1.11: Arka plana geciste launcher icin son reconcile yapilir
            tvChannelSyncService.onForegroundChanged(foreground = false)
        }
    }

    override fun onDestroy() {
        // Kullanıcı uygulamayı düzgün kapattıysa "temiz kapanış" işaretle —
        // böylece sessiz ölüm dedektörü yanlış alarm vermez.
        if (isFinishing) {
            try {
                com.kitsugi.animelist.core.diagnostics.KitsugiSessionSupervisor.markCleanExit()
            } catch (_: Throwable) {}
        }
        super.onDestroy()
    }

    // Installed only while the optional reader volume-key setting is active.
    var readerVolumeKeyHandler: ((KeyEvent) -> Boolean)? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return try {
            if (readerVolumeKeyHandler?.invoke(event) == true) true
            else super.dispatchKeyEvent(event)
        } catch (e: IllegalStateException) {
            if (e.message?.contains("FocusRequester is not initialized", ignoreCase = true) == true) {
                android.util.Log.w("MainActivity", "Bypassed uninitialized FocusRequester crash during key event dispatch", e)
                true
            } else {
                throw e
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthIntent(intent)
        // B1.1: Warm-start channel/deep link
        handleDeepLinkIntent(intent)
    }

    // B1.1: Routes versioned deep links (Kitsugianimelist://v1/*) to DeepLinkHandler.
    // Auth links are handled by handleAuthIntent; this returns true for them (ignored).
    // NavState not available at Activity level; parked as pending; TvRootScreen drains.
    private fun handleDeepLinkIntent(intent: Intent?) {
        DeepLinkHandler.handle(intent)
    }

    private fun handleAuthIntent(intent: Intent?) {
        val callbackUri = intent?.data
        if (callbackUri != null &&
            callbackUri.scheme == KitsugiAccountClient.AUTH_CALLBACK_SCHEME &&
            callbackUri.host == KitsugiAccountClient.AUTH_CALLBACK_HOST
        ) {
            val response = AccountConfirmationLink.parse(callbackUri.toString())
            // Consume once; Activity recreation must not re-exchange an expired one-time code.
            intent?.data = null
            when (response) {
                is AccountConfirmationLink.Result.Code -> lifecycleScope.launch {
                    KitsugiAccountRepository.confirmEmailCode(response.value)
                        .onSuccess {
                            Toast.makeText(this@MainActivity, getString(R.string.account_confirmation_success), Toast.LENGTH_LONG).show()
                        }
                        .onFailure { error ->
                            // Never log the callback URI, code, request headers or raw exception.
                            Toast.makeText(this@MainActivity, AccountErrorFormatter.userMessage(error), Toast.LENGTH_LONG).show()
                        }
                }
                AccountConfirmationLink.Result.ManualSignIn -> Toast.makeText(
                    this, getString(R.string.account_confirmation_manual_login), Toast.LENGTH_LONG
                ).show()
                else -> Toast.makeText(this, getString(R.string.account_confirmation_invalid), Toast.LENGTH_LONG).show()
            }
            return
        }

        ExternalAuthManager.handleAuthIntent(
            context = this,
            intent = intent,
            onSuccess = { serviceName ->
                runOnUiThread {
                    Toast.makeText(
                        this,
                        when (serviceName) {
                            "anilist"   -> "AniList bağlantısı başarılı."
                            "simkl"     -> "Simkl bağlantısı başarılı."
                            "shikimori" -> "Shikimori bağlantısı başarılı."
                            "tmdb"      -> "TMDB bağlantısı başarılı."
                            else        -> "MyAnimeList bağlantısı başarılı."
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onError = { message ->
                runOnUiThread {
                    Toast.makeText(
                        this,
                        message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }
}

@androidx.compose.runtime.Composable
private fun KitsugiPermissionRequester() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionsToRequest = androidx.compose.runtime.remember {
        buildList {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                // Android 13+
                add(android.Manifest.permission.POST_NOTIFICATIONS)
                add(android.Manifest.permission.READ_MEDIA_IMAGES)
                add(android.Manifest.permission.READ_MEDIA_VIDEO)
                add(android.Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                // Android 12 and below
                add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.P) {
                    add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (permissionsToRequest.isNotEmpty()) {
            launcher.launch(permissionsToRequest)
        }
    }
}