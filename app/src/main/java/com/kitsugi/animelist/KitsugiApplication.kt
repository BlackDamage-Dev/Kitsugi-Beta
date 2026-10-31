package com.kitsugi.animelist

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.allowHardware
import coil3.request.allowRgb565
import coil3.request.crossfade
import coil3.size.Precision
import coil3.gif.AnimatedImageDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okio.Path.Companion.toOkioPath
import com.kitsugi.animelist.data.remote.KitsugiEpisodeRatingsRepository
import com.kitsugi.animelist.data.repository.AddonStreamRepository
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.cloudstream.CsPluginLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class KitsugiApplication : Application(), SingletonImageLoader.Factory {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        val APP_LAUNCH_TIME = System.currentTimeMillis()

        @Volatile
        var activeActivity: android.app.Activity? = null

        @Volatile
        private var instance: KitsugiApplication? = null

        fun getInstance(): KitsugiApplication? = instance

        private var dynamicContextWrapper: KitsugiDynamicContextWrapper? = null

        fun getDynamicContext(context: Context): Context {
            var wrapper = dynamicContextWrapper
            if (wrapper == null) {
                wrapper = KitsugiDynamicContextWrapper(context.applicationContext)
                dynamicContextWrapper = wrapper
            }
            return wrapper
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        // ── SÜREÇTEKİ EN ERKEN NOKTA ───────────────────────────────────────────────
        // Çökme yakalayıcı burada kurulur; çünkü ContentProvider'lar (Hilt, WorkManager,
        // tachiyomi/cloudstream init) Application.onCreate'ten ÖNCE çalışır ve orada oluşan
        // çökmeler daha önce hiçbir rapora girmiyordu.
        // Açılış aşaması izi ilk satırda yazılır: süreç buradan sonra ölürse rapor
        // "attachBaseContext" aşamasında olduğunu söyler.
        try { com.kitsugi.animelist.core.diagnostics.StartupStage.mark(base, "attachBaseContext") } catch (_: Throwable) {}
        try { com.kitsugi.animelist.core.diagnostics.KitsugiCrashHandler.install(base) } catch (_: Throwable) {}
        try { com.kitsugi.animelist.core.diagnostics.KitsugiSessionSupervisor.install(base) } catch (_: Throwable) {}
        // Dil dosyası metinlerinin Composable olmayan bağlamlardan (ViewModel,
        // servis, Worker, veri katmanı) çözülebilmesi için bağlamı burada sakla.
        try { com.kitsugi.animelist.utils.KitsugiStrings.install(base) } catch (_: Throwable) {}
    }

    override fun onCreate() {
        // ── 0. :crash Süreci Koruması ────────────────────────────────────────────────
        // Eğer bu süreç çökme ekranı (:crash) için başlatılmışsa, arka plan işçilerini,
        // WorkManager'ı ve Room veritabanı senkronizasyonunu çalıştırma!
        val isCrashProcess = runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                Application.getProcessName().endsWith(":crash")
            } else {
                val pid = android.os.Process.myPid()
                val am = getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                am?.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName?.endsWith(":crash") == true
            }
        }.getOrDefault(false)

        if (isCrashProcess) {
            instance = this
            super.onCreate()
            Thread.setDefaultUncaughtExceptionHandler { crashThread, crashThrowable ->
                android.util.Log.e("KitsugiCrashProcess", "Crash in :crash process!", crashThrowable)
                // Çökme ekranının KENDİSİ çöktü: kanıtı yaz, bayrağı kaldır ve süreci kapat.
                // (Eski kod süreci canlı bırakıyordu → kullanıcı boş/siyah ekranda kilitleniyordu.)
                try {
                    java.io.File(filesDir, "crash_in_crash.txt").writeText(
                        "Çökme ekranı süreci çöktü (${crashThread.name}):\n" +
                            android.util.Log.getStackTraceString(crashThrowable)
                    )
                } catch (_: Throwable) {}
                try {
                    getSharedPreferences("kitsugi_crash_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("has_unread_crash", true)
                        .putLong("last_crash_time", System.currentTimeMillis())
                        .putString("last_crash_title", "Çökme ekranı hatası: ${crashThrowable.javaClass.simpleName}")
                        .commit()
                } catch (_: Throwable) {}
                Thread({
                    try { Thread.sleep(400) } catch (_: InterruptedException) {}
                    try { android.os.Process.killProcess(android.os.Process.myPid()) } catch (_: Throwable) {}
                }, "KitsugiCrashProcessWatchdog").start()
            }
            return
        }

        instance = this
        super.onCreate()

        // ── Açılış aşaması izi ────────────────────────────────────────────────────────
        // Aşağıdaki her adım diske "buradayım" yazar. Uygulama ana ekranı açamadan
        // ölürse (native/SIGSEGV, class-load, sistem kill) Java handler devreye giremez;
        // o zaman bu dosya tek kanıttır. MainActivity ilk kareyi çizince silinir.
        com.kitsugi.animelist.core.diagnostics.StartupStage.mark(this, "Application.onCreate")

        // Only the main process may register vault listeners and persistent WorkManager jobs.
        com.kitsugi.animelist.core.diagnostics.StartupStage.mark(this, "LinkedAccountVault.startAutoBackup")
        runCatching { com.kitsugi.animelist.data.account.LinkedAccountVault.startAutoBackup(this) }
        com.kitsugi.animelist.core.diagnostics.StartupStage.mark(this, "KitsugiAccountRepository.startSettingsObserver")
        runCatching { com.kitsugi.animelist.data.account.KitsugiAccountRepository.startSettingsObserver(this) }

        // ── Bellek koruması ───────────────────────────────────────────────────────────
        // Gezindikçe şişen bellek önbellekleri, heap tavanına yaklaşınca sistem uyarısını
        // beklemeden 15 sn'de bir kontrol edilip küçültülür (bkz. KitsugiMemoryGuard).
        com.kitsugi.animelist.core.diagnostics.StartupStage.mark(this, "startMemoryWatchdog")
        runCatching { startMemoryWatchdog() }

        // ── Alt sistem init'leri: HER BİRİ İZOLE ──────────────────────────────────────
        // KURAL: Bu çağrılardan biri patlarsa uygulama AÇILMAMAZLIK ETMEZ; hata loglanır,
        // ilgili alt sistem devre dışı kalır, kullanıcı ana ekranı görür. Eskiden burada
        // try/catch yoktu: örneğin bozuk bir indirme kaydı/DB açılışı `Application.onCreate`
        // içinde istisna atınca süreç ana ekran çizilmeden ölüyor ve kullanıcı yalnızca
        // (varsa) ESKİ çökme raporunu görebiliyordu.
        com.kitsugi.animelist.core.diagnostics.StartupStage.mark(this, "AnimeDownloadManager.init")
        runCatching { com.kitsugi.animelist.data.local.AnimeDownloadManager.init(this) }
            .onFailure { android.util.Log.e("KitsugiApplication", "AnimeDownloadManager.init hatası: ${it.message}", it) }

        com.kitsugi.animelist.core.diagnostics.StartupStage.mark(this, "WatchHistoryManager.init")
        runCatching { com.kitsugi.animelist.data.local.WatchHistoryManager.init(this) }
            .onFailure { android.util.Log.e("KitsugiApplication", "WatchHistoryManager.init hatası: ${it.message}", it) }

        com.kitsugi.animelist.core.diagnostics.StartupStage.mark(this, "FileLoggingTree.init")
        runCatching { com.kitsugi.animelist.core.diagnostics.FileLoggingTree.init(this) }
            .onFailure { android.util.Log.e("KitsugiApplication", "FileLoggingTree.init hatası: ${it.message}", it) }

        // CS eklenti canlı izleyicisi (kalıcı trace + paylaşılabilir rapor)
        com.kitsugi.animelist.core.diagnostics.StartupStage.mark(this, "CsTrace.init")
        runCatching { com.kitsugi.animelist.data.cloudstream.diag.CsTrace.init(this) }

        // KitsugiCrashLogger'a başlatma zamanını bildir
        com.kitsugi.animelist.core.diagnostics.KitsugiCrashLogger.KitsugiApplication_LaunchTime = APP_LAUNCH_TIME

        // ── Çökme yakalayıcı ─────────────────────────────────────────────────────────
        // attachBaseContext() içinde ZATEN kuruldu (provider/erken init çökmelerini de
        // yakalayabilmek için). Burada yalnızca garantiye alıyoruz.
        com.kitsugi.animelist.core.diagnostics.StartupStage.mark(this, "KitsugiCrashHandler.install(re)")
        runCatching { com.kitsugi.animelist.core.diagnostics.KitsugiCrashHandler.install(this) }

        // Ana Looper'ın içine ikinci bir Looper.loop() KESİNLİKLE kurma.
        // Önceki "Cockroach" yaklaşımı ActivityThread'in gerçek mesaj döngüsünü kalıcı
        // olarak iç içe bir döngünün arkasında bırakıyordu. Ayrıca UI callback'lerinden
        // kaçan hataları yutup yarım kalmış Compose/Window durumuyla uygulamayı canlı
        // tutuyordu; kullanıcı bunu çökme yerine bütün uygulamada donma/jank olarak
        // görüyordu. Eklenti işleri zaten izole dispatcher/thread'lerde çalışıyor ve
        // normal uncaught handler gerçek hatayı güvenli biçimde raporluyor.
        //
        // Aynı nedenle uygulama içinden `logcat -f *:D` süreci de başlatmıyoruz. Özellikle
        // debug APK'da yüzlerce ayrıntılı kayıt sürekli diske yazılıyor, görsel decode ve
        // Room trafiğiyle I/O yarışına girerek kaydırma karelerini düşürüyordu. Gerektiğinde
        // logcat Android Studio/adb üzerinden, yalnız ilgili tag'lerle toplanmalıdır.

        // Initialize ratings repository room cache context
        com.kitsugi.animelist.core.diagnostics.StartupStage.mark(this, "KitsugiEpisodeRatingsRepository.init")
        runCatching { KitsugiEpisodeRatingsRepository.init(this) }
            .onFailure { android.util.Log.e("KitsugiApplication", "RatingsRepository.init hatası: ${it.message}", it) }

        // Bangumi Latin başlık önbelleğini arka planda belleğe al (disk okuması IO kapsamında).
        // "Listem", Bangumi profili ve listeler ilk çizimde kullanıcının seçtiği başlık diline
        // uyabilsin diye önbellek hazır tutulur; hiçbir ağ isteği yapılmaz.
        com.kitsugi.animelist.core.diagnostics.StartupStage.mark(this, "BangumiTitleCache.warmUp")
        runCatching { com.kitsugi.animelist.data.remote.BangumiTitleCache.warmUp() }

        // Initialize P2P Torrent Engine & Settings
        com.kitsugi.animelist.core.diagnostics.StartupStage.mark(this, "P2pSettingsRepository.initialize")
        runCatching { com.kitsugi.animelist.core.p2p.P2pSettingsRepository.initialize(this) }
            .onFailure { android.util.Log.e("KitsugiApplication", "P2pSettingsRepository hatası: ${it.message}", it) }
        com.kitsugi.animelist.core.diagnostics.StartupStage.mark(this, "P2pStreamingEngine.initialize")
        runCatching { com.kitsugi.animelist.core.p2p.P2pStreamingEngine.initialize(this) }
            .onFailure { android.util.Log.e("KitsugiApplication", "P2pStreamingEngine hatası: ${it.message}", it) }

        // T4-11: Initialize DNS Over HTTPS resolver without blocking main thread.
        // DnsManager starts with default=0 immediately; the real persisted value is applied
        // asynchronously once the DataStore flow emits its first value.
        com.kitsugi.animelist.core.diagnostics.StartupStage.mark(this, "DnsManager.init")
        runCatching { com.kitsugi.animelist.core.network.DnsManager.init(this, 0) }
            .onFailure { android.util.Log.e("KitsugiApplication", "DnsManager.init hatası: ${it.message}", it) }

        // Buraya geldiysek tüm kritik init'ler denendi; UI hâlâ çizilmedi.
        com.kitsugi.animelist.core.diagnostics.StartupStage.mark(this, "Application.onCreate tamamlandı (UI bekleniyor)")
        applicationScope.launch {
            try {
                val dataStore = com.kitsugi.animelist.data.settings.SettingsDataStore(this@KitsugiApplication)
                val settings = dataStore.settingsFlow.first()

                // CsStreamRunner adult content filter init
                com.kitsugi.animelist.data.cloudstream.CsStreamRunner.setShowAdultContent(settings.showAdultContent)

                // Uzak domain listesini uygulama açılışında önceden çek —
                // domain_fixes.json GitHub'dan okunur, eklentiler arama başlamadan doğru domaine sahip olur
                com.kitsugi.animelist.data.cloudstream.CsStreamRunner.triggerRemoteDomainsFetch()

                // DNS init
                if (settings.dnsChoice != 0) {
                    com.kitsugi.animelist.core.network.DnsManager.init(this@KitsugiApplication, settings.dnsChoice)
                }

                // TMDB önbelleği — runBlocking kullanımını ortadan kaldırır
                com.kitsugi.animelist.data.remote.TmdbApiClient.updateCache(
                    apiKey  = settings.tmdbUserApiKey,
                    language = settings.tmdbLanguage
                )
            } catch (e: Exception) {
                android.util.Log.w("KitsugiApplication", "Init async failed: ${e.message}")
            }
        }

        // T3.3: WorkManager tabanlı bildirim planlaması
        applicationScope.launch {
            try {
                com.kitsugi.animelist.core.notifications.NotificationScheduler.reconcile(this@KitsugiApplication)
            } catch (e: Exception) {
                android.util.Log.e("KitsugiApplication", "Failed to schedule notifications: ${e.message}")
            }
        }

        // Schedule offline mapping database sync periodically (every 14 days)
        applicationScope.launch {
            try {
                val db = KitsugiDatabase.getDatabase(this@KitsugiApplication)
                val count = db.mediaMetaCacheDao().getCount()
                
                // One-time sync on first run if database is empty
                if (count == 0) {
                    val oneTimeRequest = androidx.work.OneTimeWorkRequestBuilder<com.kitsugi.animelist.data.local.MappingSyncWorker>()
                        .build()
                    androidx.work.WorkManager.getInstance(this@KitsugiApplication).enqueueUniqueWork(
                        "mapping_sync_one_time",
                        androidx.work.ExistingWorkPolicy.KEEP,
                        oneTimeRequest
                    )
                }

                // Periodic sync
                val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.kitsugi.animelist.data.local.MappingSyncWorker>(
                    14, java.util.concurrent.TimeUnit.DAYS
                )
                    .setConstraints(
                        androidx.work.Constraints.Builder()
                            .setRequiredNetworkType(androidx.work.NetworkType.UNMETERED) // Wi-Fi only
                            .setRequiresBatteryNotLow(true)
                            .build()
                    )
                    .build()
                androidx.work.WorkManager.getInstance(this@KitsugiApplication).enqueueUniquePeriodicWork(
                    "mapping_sync_work",
                    androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )
            } catch (e: Exception) {
                android.util.Log.e("KitsugiApplication", "Failed to schedule mapping sync: ${e.message}")
            }
        }

        // ── Depolama bakımı (StorageJanitor) ───────────────────────────────────────
        // Uygulamada daha önce HİÇBİR merkezî önbellek temizliği yoktu: P2P disk önbelleği
        // (varsayılan 2 GB), Coil görselleri (512 MB), manga sayfaları, video önizlemeleri,
        // altyazı zip'leri ve Room'daki önbellek tabloları zamanla birikip kurulumu ~4 GB'a
        // çıkarıyor, artan disk G/Ç'si de kaydırma/gezinme kasmasına dönüşüyordu. Üstelik
        // budama kodu MappingSyncWorker içinde yalnızca "Wi-Fi + batarya iyi" koşuluyla
        // planlandığı için mobil veride hiç çalışmıyordu.
        //
        // Burada: açılışta IO dispatcher'da (6 saatte bir, ilk açılışta hemen) hafif bakım;
        // ayrıca ağ koşulu OLMAYAN günlük WorkManager işi planlanır.
        applicationScope.launch {
            runCatching { com.kitsugi.animelist.core.storage.StorageJanitor.runMaintenance(this@KitsugiApplication) }
                .onFailure { android.util.Log.w("KitsugiStorage", "Açılış bakımı hatası: ${it.message}") }
        }
        runCatching { com.kitsugi.animelist.core.storage.StorageMaintenanceWorker.schedule(this) }
            .onFailure { android.util.Log.w("KitsugiStorage", "Bakım işi planlanamadı: ${it.message}") }

        // ── Otomatik Arka Plan Liste Yenileme ──────────────────────────────────────
        // v2.4.341 — Açılışta bekleyen eşitleme kuyruğu **zorla** boşaltılır. Deneme sayacı
        // 5'i aşmış ("artık denenmeyecek") işler de tazelenir: kullanıcı geri bildirimi
        // "günlerdir hiçbir değişiklik AniList'e gitmiyor" — eski kod bu işleri sessizce
        // ölüme terk ediyor, kuyruk yalnızca ağ değişiminde yeniden deneniyordu.
        applicationScope.launch {
            runCatching {
                val db = com.kitsugi.animelist.data.local.KitsugiDatabase.getDatabase(this@KitsugiApplication)
                val repo = com.kitsugi.animelist.data.local.MediaEntryRepository(
                    dao = db.mediaEntryDao(),
                    pendingSyncDao = db.pendingSyncDao(),
                    context = this@KitsugiApplication
                )
                val sent = repo.drainPendingQueue(freshBudget = true)
                if (sent > 0) {
                    com.kitsugi.animelist.data.auth.AniListSyncStatus.reportSuccess(this@KitsugiApplication)
                    android.util.Log.d("KitsugiAutoSync", "Bekleyen $sent değişiklik gönderildi.")
                }
            }
        }

        // AniHyou / MoeList gibi: uygulama açıldığında bağlı platformların listelerini
        // sessizce arka planda yeniler. Son yenilemeden 24 saat geçmemişse atlar.
        applicationScope.launch {
            try {
                val prefs = getSharedPreferences("kitsugi_auto_sync", Context.MODE_PRIVATE)
                val lastSync = prefs.getLong("last_list_sync_ms", 0L)
                val now = System.currentTimeMillis()
                val twentyFourHours = 24 * 60 * 60 * 1000L

                if (now - lastSync < twentyFourHours) {
                    android.util.Log.d("KitsugiAutoSync", "Son yenilemeden 24 saat geçmedi, atlanıyor.")
                } else {
                    val db = com.kitsugi.animelist.data.local.KitsugiDatabase.getDatabase(this@KitsugiApplication)
                    val dao = db.mediaEntryDao()
                    val repo = com.kitsugi.animelist.data.local.MediaEntryRepository(dao = dao, context = this@KitsugiApplication)

                    var syncCount = 0

                    // AniList
                    val aniListToken = com.kitsugi.animelist.data.auth.ExternalAuthManager.getAniListToken(this@KitsugiApplication)
                    if (!aniListToken.isNullOrBlank()) {
                        try {
                            android.util.Log.d("KitsugiAutoSync", "AniList listesi yenileniyor...")
                            // v2.4.341 — ÖNEMLİ: önce bekleyen yerel değişiklikler gönderilir.
                            // Aksi halde uzak liste, henüz push edilmemiş yerel düzenlemeleri
                            // ezip "hiçbir şey eşitlenmiyor" durumuna yol açıyordu.
                            runCatching { repo.drainPendingQueue(freshBudget = true) }
                            val entries = com.kitsugi.animelist.data.auth.AniListImportManager.fetchAllLists(aniListToken)
                            // v2.4.341 — ESKİ DAVRANIŞ (veri kaybı): replaceSourcePreservingAdultFlags
                            // kaynağın TÜM satırlarını silip yeniden yazıyordu. Uzak listede
                            // bulunmayan (henüz push edilmemiş) yeni kayıtlar ve bekleyen kuyruk
                            // işleri her 24 saatte bir yok oluyordu. Artık birleştirme yapılır:
                            // eşleşen satırlar güncellenir, yerelde kalanlar SİLİNMEZ, bekleyen
                            // değişikliği olan satırlar uzak veriyle ezilmez.
                            repo.smartImport("anilist", entries, allowDelete = false)
                            com.kitsugi.animelist.data.auth.AniListSyncStatus.reportSuccess(this@KitsugiApplication)
                            syncCount += entries.size
                            android.util.Log.d("KitsugiAutoSync", "AniList: ${entries.size} kayıt yenilendi.")
                        } catch (e: Exception) {
                            android.util.Log.w("KitsugiAutoSync", "AniList yenileme hatası: ${e.message}")
                            com.kitsugi.animelist.data.auth.AniListSyncStatus.reportFailure(
                                this@KitsugiApplication,
                                com.kitsugi.animelist.data.auth.AniListSyncStatus.classify(
                                    e.message,
                                    com.kitsugi.animelist.data.auth.AniListGate.isCoolingDown()
                                ),
                                e.message
                            )
                        }
                    }

                    // MyAnimeList
                    val malToken = com.kitsugi.animelist.data.auth.ExternalAuthManager.getOrRefreshMalToken(this@KitsugiApplication)
                    if (!malToken.isNullOrBlank()) {
                        try {
                            android.util.Log.d("KitsugiAutoSync", "MAL listesi yenileniyor...")
                            val dataStore = com.kitsugi.animelist.data.settings.SettingsDataStore(this@KitsugiApplication)
                            val showAdult = dataStore.settingsFlow.first().showAdultContent
                            val entries = com.kitsugi.animelist.data.auth.MalImportManager.fetchAllLists(malToken, showAdult)
                            repo.smartImport("mal", entries, allowDelete = false) // v2.4.341: sil-yaz yerine birleştir (yerel kayıtlar korunur)
                            syncCount += entries.size
                            android.util.Log.d("KitsugiAutoSync", "MAL: ${entries.size} kayıt yenilendi.")
                        } catch (e: Exception) {
                            android.util.Log.w("KitsugiAutoSync", "MAL yenileme hatası: ${e.message}")
                        }
                    }

                    // Simkl
                    val simklToken = com.kitsugi.animelist.data.auth.ExternalAuthManager.getSimklToken(this@KitsugiApplication)
                    if (!simklToken.isNullOrBlank()) {
                        try {
                            android.util.Log.d("KitsugiAutoSync", "Simkl listesi yenileniyor...")
                            val entries = com.kitsugi.animelist.data.auth.SimklImportManager.fetchAllLists(simklToken)
                            // Simkl liste API'si `adult` taşımaz → eski +18 işaretleri
                            // korunmalı ve hemen ardından kimlik üzerinden tamamlanmalı.
                            repo.smartImport("simkl", entries, allowDelete = false) // v2.4.341: sil-yaz yerine birleştir (yerel kayıtlar korunur)
                            syncCount += entries.size
                            android.util.Log.d("KitsugiAutoSync", "Simkl: ${entries.size} kayıt yenilendi.")
                        } catch (e: Exception) {
                            android.util.Log.w("KitsugiAutoSync", "Simkl yenileme hatası: ${e.message}")
                        }
                    }

                    if (syncCount > 0) {
                        prefs.edit().putLong("last_list_sync_ms", now).apply()
                        android.util.Log.i("KitsugiAutoSync", "Otomatik liste yenileme tamamlandı: toplam $syncCount kayıt.")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("KitsugiAutoSync", "Otomatik sync hatası: ${e.message}")
            }
        }
        // ─────────────────────────────────────────────────────────────────────────────

        // Initialize Cloudstream runtime singleton context and client
        com.kitsugi.animelist.data.cloudstream.CsRuntimeInit.init(this)

        // Do not pre-create a batch of hidden WebViews during application startup.
        // CloudflareKiller resolves a challenge lazily for the exact host that needs it.
        // The previous startup warmup could create up to 14 Chromium renderers in sequence,
        // adding substantial native memory and RenderThread pressure without user interaction.

        try {
            uy.kohesive.injekt.Injekt.addSingleton(eu.kanade.tachiyomi.network.NetworkHelper::class.java, eu.kanade.tachiyomi.network.NetworkHelper(this))
            uy.kohesive.injekt.Injekt.addSingleton(kotlinx.serialization.json.Json::class.java, kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            })
            android.util.Log.d("KitsugiApplication", "NetworkHelper and Json registered in Injekt.")
        } catch (e: Exception) {
            android.util.Log.w("KitsugiApplication", "Injekt registration skipped: ${e.message}")
        }

        // Track active activity to provide correct Window Token context for plugin dialogs (e.g. Captchas)
        // Handler to delay-null activeActivity (gives plugin dialogs time to close)
        val activityNullHandler = android.os.Handler(android.os.Looper.getMainLooper())
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {
                disableForceDarkOnActivity(activity)
            }
            override fun onActivityStarted(activity: android.app.Activity) {
                disableForceDarkOnActivity(activity)
                // Cancel any pending null — new activity is becoming visible
                activityNullHandler.removeCallbacksAndMessages(null)
                com.lagradost.api.setContext(java.lang.ref.WeakReference(getDynamicContext(activity)))
            }
            override fun onActivityResumed(activity: android.app.Activity) {
                disableForceDarkOnActivity(activity)
                activityNullHandler.removeCallbacksAndMessages(null)
                activeActivity = activity
                com.lagradost.api.setContext(java.lang.ref.WeakReference(getDynamicContext(activity)))
                // Sessiz ölüm dedektörü: ön plan/arka plan durumunu kaydet
                try {
                    com.kitsugi.animelist.core.diagnostics.KitsugiSessionSupervisor.noteForeground(true)
                } catch (_: Throwable) {}
            }
            override fun onActivityPaused(activity: android.app.Activity) {
                // DO NOT null activeActivity here! A paused activity still has a valid window
                // token. Nulling here causes BotKontrol (and any plugin dialog) to crash with
                // BadTokenException: "Unable to add window -- token null is not valid".
                // We keep the reference alive so plugin dialogs (captcha, etc.) can still show.
            }
            override fun onActivityStopped(activity: android.app.Activity) {
                try {
                    com.kitsugi.animelist.core.diagnostics.KitsugiSessionSupervisor.noteForeground(false)
                } catch (_: Throwable) {}
                // Delay null by 2s — gives async plugin captcha dialogs time to finish
                if (activeActivity === activity) {
                    activityNullHandler.postDelayed({
                        if (activeActivity === activity) {
                            activeActivity = null
                        }
                    }, 2000L)
                }
            }
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {
                activityNullHandler.removeCallbacksAndMessages(null)
                if (activeActivity === activity) {
                    activeActivity = null
                }
                // ÇÖKME ÖNLEMİ: Activity yıkılırken GPU RenderNode zinciri henüz
                // RenderThread tarafından çiziliyorsa, framework'ün Surface'i
                // destroy etmesiyle RenderThread'de serbest bırakılmış katmanlara
                // erişip libhwui.so'da SIGSEGV (fault_addr=0x20) üretme yarışı oluşur
                // (MIUI/HyperOS'ta kronik). Window'nun DecorView'una bağlı HardwareRenderer
                // destroy öncesi senkron olarak durdurulursa bekleyen GPU işleri biter ve
                // katmanlar güvenli sırayla serbest kalır.
                safeFreeHardwareRenderer(activity)
            }
        })

        // Seed default Stremio addons if database is empty
        applicationScope.launch {
            AddonStreamRepository(this@KitsugiApplication).seedPresetsIfEmpty()
        }

        // Pre-load all enabled Cloudstream plugins sequentially on startup immediately on IO thread.
        applicationScope.launch(Dispatchers.IO) {
            try {
                val db = KitsugiDatabase.getDatabase(this@KitsugiApplication)
                val enabledPlugins = db.csPluginDao().getEnabledPlugins()
                android.util.Log.d("KitsugiApplication", "Pre-loading ${enabledPlugins.size} enabled CS plugin(s) sequentially...")
                for (plugin in enabledPlugins) {
                    try {
                        CsPluginLoader.loadExtension(this@KitsugiApplication, plugin.id)
                        android.util.Log.d("KitsugiApplication", "Pre-loaded plugin: ${plugin.id}")
                    } catch (e: Exception) {
                        android.util.Log.w("KitsugiApplication", "Pre-load failed for ${plugin.id}: ${e.message}")
                    }
                    kotlinx.coroutines.delay(50L)
                }
                android.util.Log.d("KitsugiApplication", "Plugin pre-loading complete.")
            } catch (e: Exception) {
                android.util.Log.e("KitsugiApplication", "Plugin pre-loading error: ${e.message}", e)
            }
        }

        // Pre-load manga extensions on startup immediately on IO thread
        applicationScope.launch(Dispatchers.IO) {
            try {
                com.kitsugi.animelist.data.manga.MangaExtensionLoader.loadAllExtensions(this@KitsugiApplication)
                android.util.Log.d("KitsugiApplication", "Manga eklentileri tarama tamamlandı.")

                // Kotatsu-Redo: tüm 1300+ built-in kaynağı yükle (dil filtresi: TR varsayılan)
                try {
                    com.kitsugi.animelist.data.manga.KotatsuExtensionAdapter.initialize(this@KitsugiApplication)
                    android.util.Log.i("KitsugiApplication",
                        "Kotatsu init: ${com.kitsugi.animelist.data.manga.KotatsuExtensionAdapter.getSourceCount()} kaynak yüklendi.")
                } catch (e: Exception) {
                    android.util.Log.e("KitsugiApplication", "Kotatsu init hatası: ${e.message}", e)
                }

                // Dinamik manga domain kataloğunu internetten çek ve yerel ayarları güncelle
                try {
                    com.kitsugi.animelist.data.manga.MangaCatalogManager.syncCatalog(this@KitsugiApplication)
                } catch (e: Exception) {
                    android.util.Log.e("KitsugiApplication", "Manga katalog güncelleme hatası: ${e.message}", e)
                }
            } catch (e: Exception) {
                android.util.Log.e("KitsugiApplication", "Manga eklenti tarama hatası: ${e.message}", e)
            }

            // Eklentiler yüklendikten 5 saniye sonra Keiyoushi repo'sundan otomatik
            // güncelleme kontrolü yap (günde en fazla 1 kez çalışır).
            kotlinx.coroutines.delay(5_000L)
            try {
                val result = com.kitsugi.animelist.data.manga.MangaExtensionAutoUpdater.runIfNeeded(
                    context = this@KitsugiApplication,
                    forceCheck = false
                )
                when (result) {
                    is com.kitsugi.animelist.data.manga.MangaExtensionAutoUpdater.UpdateResult.Success ->
                        android.util.Log.i("KitsugiApplication",
                            "Manga oto-güncelleme: ${result.updated} güncellendi, ${result.checked} kontrol edildi.")
                    is com.kitsugi.animelist.data.manga.MangaExtensionAutoUpdater.UpdateResult.Skipped ->
                        android.util.Log.d("KitsugiApplication", "Manga extension update check skipped; next check in ${result.minutesUntilNextCheck} minutes.")
                    is com.kitsugi.animelist.data.manga.MangaExtensionAutoUpdater.UpdateResult.Failed ->
                        android.util.Log.w("KitsugiApplication", "Manga extension update check failed: ${result.reasonCode}")
                }
            } catch (e: Exception) {
                android.util.Log.e("KitsugiApplication", "Manga oto-güncelleme hatası: ${e.message}", e)
            }

            // Kotatsu-Redo kaynaklarını arka planda sessizce güncelle (12 saatte bir)
            kotlinx.coroutines.delay(4_000L)
            try {
                val kotatsuCount = com.kitsugi.animelist.data.manga.MangaCatalogManager.syncKotatsuSources(
                    context = this@KitsugiApplication,
                    forceCheck = false
                )
                android.util.Log.i("KitsugiApplication", "Kotatsu-Redo kaynakları güncellendi: $kotatsuCount kaynak yüklendi.")
            } catch (e: Exception) {
                android.util.Log.e("KitsugiApplication", "Kotatsu sync hatası: ${e.message}", e)
            }
        }

        // Load locale synchronously so it's available before Activity.attachBaseContext.
        val tag = getSharedPreferences("app_locale", Context.MODE_PRIVATE)
            .getString("locale_tag", null)
        LocaleCache.localeTag = tag ?: ""
    }

    private fun startMemoryWatchdog() {
        applicationScope.launch(Dispatchers.Default) {
            while (true) {
                kotlinx.coroutines.delay(15_000L)
                try {
                    com.kitsugi.animelist.core.memory.KitsugiMemoryGuard.watchdogTick()
                } catch (_: Throwable) {}
            }
        }
    }

    /**
     * Android bellek azaldığında bu geri çağrıyla uyarır. Eskiden dinlenmiyordu; uyarıya cevap
     * vermeyen süreç LMKD tarafından hiçbir rapor bırakmadan öldürülüyordu.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        try { com.kitsugi.animelist.core.memory.KitsugiMemoryGuard.onTrimMemory(level) } catch (_: Throwable) {}
        // UI görünmez olduğunda bekleyen izleme ilerlemesi diske indirilir: süreç bu noktadan
        // sonra öldürülürse son konum kaybolmaz (toplulaştırılmış yazma penceresi boşaltılır).
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            try { com.kitsugi.animelist.data.local.WatchHistoryManager.flush() } catch (_: Throwable) {}
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        @Suppress("DEPRECATION")
        super.onLowMemory()
        try { com.kitsugi.animelist.core.memory.KitsugiMemoryGuard.onLowMemory() } catch (_: Throwable) {}
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return buildImageLoader(context).also { loader ->
            // Coil görsel önbelleği de bellek baskısında küçültülür / boşaltılır.
            com.kitsugi.animelist.core.memory.KitsugiMemoryGuard.registerClearer("coil.memory") { fraction ->
                val cache = loader.memoryCache ?: return@registerClearer
                if (fraction <= 0f) cache.clear()
                else cache.trimToSize((cache.size * fraction).toLong())
            }
        }
    }

    private fun buildImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                }
                add(OkHttpNetworkFetcherFactory(callFactory = { com.kitsugi.animelist.core.network.KitsugiHttpClient.client }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.15)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("nuvio_images").toOkioPath())
                    .maxSizeBytes(512L * 1024 * 1024)
                    .build()
            }
            // PERF & STABILITY:
            // 1) crossfade(false): LazyList/Grid içinde kaydırma anında her yeni kart görseli
            //    crossfade animasyonu başlatmasın.
            // 2) allowHardware(false): API 28+ ImageDecoder allowHardware(true) iken tüm görselleri
            //    AHardwareBuffer (/dev/kgsl-3d0 + dmabuf) olarak ayırır, allowRgb565(true) ayarını
            //    yok sayar ve galeri/profil geçişlerinde GPU buffer havuzunu tüketerek
            //    RenderInspector DequeueBuffer timeout + libhwui.so RenderThread SIGSEGV'e yol açar.
            //    allowHardware(false) ile RGB_565 gerçekten devreye girer (%50 RAM tasarrufu).
            .crossfade(false)
            .allowHardware(false)
            .allowRgb565(true)           // posterlerde %50 bellek tasarrufu
            .precision(Precision.INEXACT)
            .build()
    }
}

/**
 * MIUI / HyperOS ve Android 10+ (API 29+) cihazlarda sistemin "Force Dark" (TransformCanvas)
 * katmanını DecorView ve tüm alt View ağacında (AndroidComposeView dahil) devre dışı bırakır.
 *
 * NEDEN: MIUI ForceDark açıkken libhwui.so içindeki RenderNodeDrawable::drawContent (+0x23bb88)
 * çizimi TransformCanvas (+0x2779c4) üzerinden geçirir ve Compose'un iç içe RenderNode + Shader
 * ağacında SkShader null pointer dereference (+0x3ff650, fault_addr=0x20) ile RenderThread'i çökertir.
 */
private fun disableForceDarkOnActivity(activity: android.app.Activity) {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return
    runCatching {
        val decorView = activity.window?.peekDecorView() ?: activity.window?.decorView ?: return@runCatching
        disableForceDarkTree(decorView)
        decorView.post {
            runCatching {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    disableForceDarkTree(decorView)
                }
            }
        }
    }
}

private fun disableForceDarkTree(view: android.view.View) {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return
    runCatching {
        view.isForceDarkAllowed = false
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                view.getChildAt(i)?.let { disableForceDarkTree(it) }
            }
        }
    }
}

class KitsugiDynamicContextWrapper(appContext: Context) : android.content.ContextWrapper(appContext) {
    override fun getPackageName(): String {
        return "com.lagradost.cloudstream3"
    }

    override fun getSystemService(name: String): Any? {
        // Delegate to live Activity's service where possible so dialogs get valid window tokens.
        // NO proxy wrapping — Android internals cast WindowManager to WindowManagerImpl (concrete
        // class) inside Window.setWindowManager(), an interface proxy causes ClassCastException.
        val activity = KitsugiApplication.activeActivity
        if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
            try {
                val service = activity.getSystemService(name)
                if (service != null) return service
            } catch (_: Exception) {}
        }
        return super.getSystemService(name)
    }

    override fun getTheme(): android.content.res.Resources.Theme {
        return KitsugiApplication.activeActivity?.theme ?: super.getTheme()
    }

    override fun getResources(): android.content.res.Resources {
        return KitsugiApplication.activeActivity?.resources ?: super.getResources()
    }

    override fun getAssets(): android.content.res.AssetManager {
        return KitsugiApplication.activeActivity?.assets ?: super.getAssets()
    }
}

object LocaleCache {
    const val UNSET = "__UNSET__"

    @Volatile
    var localeTag: String = UNSET

    /**
     * Uygulama dilini değiştirir.
     *
     * GEÇMİŞTEKİ İKİ HATA
     * ──────────────────
     * 1) **"İngilizce seçili ama arayüz Türkçe"** — Android 13 (API 33) ve üzerinde
     *    uygulama dili artık çerçevenin (framework / LocaleManager) sorumluluğunda.
     *    `Locale.setDefault()` + `resources.updateConfiguration()` ile elle dil
     *    zorlamak, çerçevenin kendi per-app dil kararıyla çakışıyor ve Activity
     *    yeniden kurulduğunda kaynaklar eski dile dönüyordu. Bu yüzden API 33+ 'da
     *    işi tamamen `AppCompatDelegate.setApplicationLocales()`'e devrediyoruz.
     *
     * 2) **Dil değiştirirken çökme (SIGSEGV / RenderThread / libhwui.so)** —
     *    `recreate()` doğrudan Compose tıklama geri çağrısının İÇİNDEN çağrılıyordu.
     *    Ana iş parçacığı olay dağıtımı sürerken tüm görünüm hiyerarşisi
     *    (RenderNode'lar + GPU katmanları) yıkıma gidiyor; o sırada çizim yapan
     *    RenderThread serbest bırakılmış bir katmana erişince libhwui içinde
     *    null pointer (fault_addr=0x20) ile patlıyor. recreate()'i looper
     *    kuyruğunun sonuna bırakmak bu yarışı ortadan kaldırır.
     */
    fun updateLocale(context: Context, tag: String) {
        val prefsTag = if (tag == "system") "" else tag

        // 1) Tercihi kalıcı yaz. commit() bilinçli: Activity yeniden kurulmadan
        //    (veya süreç ölüp geri gelmeden) önce değerin diske gitmiş olması
        //    garanti edilsin. apply() zamanında yetişmeyebiliyor.
        runCatching {
            context.getSharedPreferences("app_locale", Context.MODE_PRIVATE)
                .edit()
                .putString("locale_tag", prefsTag)
                .commit()
        }

        localeTag = prefsTag

        // 2) Android 13+ : dili çerçeve yönetsin.
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            runCatching {
                val localeList = if (tag == "system") {
                    androidx.core.os.LocaleListCompat.getEmptyLocaleList()
                } else {
                    androidx.core.os.LocaleListCompat.forLanguageTags(tag)
                }
                androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(localeList)
            }
            // Güvenlik ağı: bazı cihazlarda çerçeve Activity'yi yeniden kurmazsa
            // (örn. manifest configChanges kalıntısı / OEM davranışı) dili biz uygularız.
            scheduleLocaleSafetyRecreate(context, prefsTag)
            return
        }

        // 3) API < 33 : eski yol.
        val locale = if (tag == "system") {
            java.util.Locale.getDefault()
        } else {
            java.util.Locale.forLanguageTag(tag).takeIf { !it.language.isNullOrBlank() }
                ?: java.util.Locale.getDefault()
        }
        java.util.Locale.setDefault(locale)
        runCatching {
            val config = android.content.res.Configuration(context.resources.configuration)
            config.setLocale(locale)
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
        }

        // recreate()'i mevcut olayın İÇİNDEN değil, looper kuyruğunun sonundan tetikle.
        findActivity(context)?.let { activity ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                runCatching {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        activity.recreate()
                    }
                }
            }
        }
    }

    /**
     * Dil değişiminden kısa bir süre sonra Activity'nin gerçekten yeni dili
     * gösterip göstermediğini doğrular; göstermiyorsa bir kez recreate eder.
     * Çerçeve Activity'yi zaten yeniden kurduysa eski örnek destroyed durumuna
     * düşer ve hiçbir şey yapılmaz (çift recreate olmaz).
     */
    private fun scheduleLocaleSafetyRecreate(context: Context, expectedTag: String) {
        val activity = findActivity(context) ?: return
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            runCatching {
                if (activity.isFinishing || activity.isDestroyed) return@runCatching
                val applied = activity.resources.configuration.locales?.get(0)
                    ?: return@runCatching
                val expected = if (expectedTag.isEmpty()) {
                    androidx.core.os.LocaleListCompat.getDefault().get(0)
                } else {
                    java.util.Locale.forLanguageTag(expectedTag)
                }
                if (expected != null && applied.language != expected.language) {
                    activity.recreate()
                }
            }
        }, 700)
    }

    private fun findActivity(context: Context): android.app.Activity? {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) {
                return ctx
            }
            ctx = ctx.baseContext
        }
        return null
    }
}

/**
 * Activity yıkımı sırasında bekleyen GPU komutlarını boşaltıp RenderNode katmanlarını
 * güvenli biçimde serbest bıraktırır. Reflektif çağrıldığı için herhangi bir platform
 * sürümünde çalışmazsa yutulur; hiçbir normal akışı etkilemez, sadece çökme ihtimalini
 * azaltır.
 */
private fun safeFreeHardwareRenderer(activity: android.app.Activity) {
    if (activity.isFinishing.not() && activity.isDestroyed.not()) return
    // Sadece gerçekten ölmekte olan activity'de çalışsın; tab'li veya yeniden yaratılan
    // activity'lerde dekor view henüz kullanımda olabilir.
    runCatching {
        val decorView = activity.window?.peekDecorView() ?: return@runCatching
        // ThreadedRenderer.destroy() beklenmedik biçimde erken çağrılırsa framework'ün kendi
        // destroy'unu kırabilir. Bunun yerine GPU boru hattını senkron olarak boşaltmak için
        // View#onDescendantInvalidated sonrası "surface release" benzeri bir etki beklemek
        // yerine, yalnızca DecorView'ın bitmap/drawable cache'ini ve HardwareLayer'larını
        // temizlemek daha güvenli bir seçimdir.
        decorView.destroyDrawingCache()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            try {
                // API 28+: ThreadedRenderer pause/drain (reflection)
                val viewClass = android.view.View::class.java
                val attachInfoField = viewClass.getDeclaredField("mAttachInfo").apply { isAccessible = true }
                val attachInfo = attachInfoField.get(decorView) ?: return@runCatching
                val attachInfoClass = attachInfo.javaClass
                val trField = runCatching {
                    attachInfoClass.getDeclaredField("mThreadedRenderer").apply { isAccessible = true }
                }.getOrNull() ?: runCatching {
                    attachInfoClass.getDeclaredField("mHardwareRenderer").apply { isAccessible = true }
                }.getOrNull() ?: return@runCatching
                val renderer = trField.get(attachInfo) ?: return@runCatching
                // HardwareRenderer: pause/syncFrame/destroy güvenli yöntemlerini dene.
                val rendererClass = renderer.javaClass
                for (name in arrayOf("pause", "syncFrame", "drainFence", "notifyFramePending")) {
                    runCatching {
                        val m = rendererClass.getDeclaredMethod(name).apply { isAccessible = true }
                        m.invoke(renderer)
                    }
                }
            } catch (_: Throwable) {}
        }
    }
}

