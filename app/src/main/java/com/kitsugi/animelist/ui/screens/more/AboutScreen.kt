package com.kitsugi.animelist.ui.screens.more
import com.kitsugi.animelist.ui.components.KitsugiButton

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import com.kitsugi.animelist.ui.theme.gradient.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import com.kitsugi.animelist.ui.utils.kitsugiVerticalScroll
import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons
import androidx.compose.material3.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsugi.animelist.ui.theme.LocalKitsugiAccent
import com.kitsugi.animelist.ui.theme.LocalKitsugiColors
import com.kitsugi.animelist.ui.utils.tvClickable
import com.kitsugi.animelist.ui.screens.settings.DeveloperLogsDialog
import com.kitsugi.animelist.ui.components.CleanReleaseNotesText
import com.kitsugi.animelist.core.update.AppRelease
import com.kitsugi.animelist.core.update.KitsugiUpdateRepository

import com.kitsugi.animelist.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import androidx.compose.ui.res.stringResource
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

@Composable
fun AboutScreen(
    autoUpdateCheckEnabled: Boolean,
    onAutoUpdateCheckEnabledChanged: (Boolean) -> Unit,
    onCheckForUpdatesClick: () -> Unit,
    onCheckForPluginUpdatesClick: (() -> Unit)? = null,
    onBackClick: () -> Unit
) {
    val KitsugiColors = LocalKitsugiColors.current
    val accentColor = LocalKitsugiAccent.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val version = BuildConfig.VERSION_NAME
    val developer = "Kitsugi Team"
    val projectRepo = "https://github.com/BlackDamage-Dev/Kitsugi-Beta"
    val pluginsRepo = "https://github.com/BlackDamage-Plugin/Kitsugi-Video-Plugins"
    val mangaPluginsRepo = "https://github.com/BlackDamage-Plugin/Kitsugi-Manga-Plugins"

    var showDeveloperLogs by remember { mutableStateOf(false) }

    // ── Sürüm notları (GitHub'daki en son sürüm) ────────────────────────────────
    // Uygulama güncellemeleri GitHub Releases üzerinden dağıtıldığı için kullanıcı,
    // yeni sürümü kurmadan önce ne değiştiğini burada okuyabilir. İçerik
    // KitsugiUpdateRepository üzerinden (6 saatlik disk önbelleğiyle) çekilir.
    val updateRepository = remember { KitsugiUpdateRepository() }
    var releaseNotesState by remember { mutableStateOf<ReleaseNotesUiState>(ReleaseNotesUiState.Loading) }
    var releaseNotesRefreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(releaseNotesRefreshKey) {
        releaseNotesState = ReleaseNotesUiState.Loading
        val result = updateRepository.latestRelease(context, force = releaseNotesRefreshKey > 0)
        releaseNotesState = result.fold(
            onSuccess = { release ->
                if (release == null) ReleaseNotesUiState.Unavailable
                else ReleaseNotesUiState.Ready(release)
            },
            onFailure = { error ->
                ReleaseNotesUiState.Failed(error.localizedMessage ?: error.message.orEmpty())
            }
        )
    }

    val libraries = listOf(
        // ── Medya Motoru ──────────────────────────────────────────────
        LibraryInfo("CloudStream Engine", "Eklenti tabanlı medya sağlayıcısı (video akışı, torrent, debrid)", "https://github.com/recloudstream/cloudstream"),
        LibraryInfo("Kotatsu Reader", "Gelişmiş manga okuyucu motoru ve kaynak yöneticisi", "https://github.com/KotatsuApp/Kotatsu"),
        LibraryInfo("MPV-Android", "libmpv tabanlı gelişmiş video oynatıcı motoru (GPU render, hwdec, Lua)", "https://github.com/mpv-android/mpv-android"),
        LibraryInfo("Media3 & ExoPlayer", "Google Media3 tabanlı güçlü video oynatıcı (arabellek & adaptif akış)", "https://github.com/google/ExoPlayer"),
        // ── Platform API'leri ────────────────────────────────────────
        LibraryInfo("AniList API (Apollo GraphQL)", "AniList anime/manga veritabanı – GraphQL istemcisi", "https://github.com/apollographql/apollo-kotlin"),
        LibraryInfo("MyAnimeList / Jikan API", "MyAnimeList için açık REST API köprüsü", "https://jikan.moe"),
        LibraryInfo("Kitsu API", "Kitsu anime & manga kataloğu REST API", "https://kitsu.app/api/edge"),
        LibraryInfo("Shikimori API", "Rus dili anime veritabanı ve sosyal platform", "https://shikimori.io/api/doc"),
        LibraryInfo("Simkl API", "Film, dizi ve anime takip platformu API'si", "https://simkl.com/apps/developer"),
        LibraryInfo("TMDB API", "The Movie Database – film ve dizi meta verisi", "https://developer.themoviedb.org"),
        LibraryInfo("MDBList API", "IMDb, Rotten Tomatoes, Metacritic puan toplayıcısı", "https://mdblist.com"),
        LibraryInfo("Fanart.tv API", "Yüksek kaliteli anime/film sanat eserleri", "https://fanart.tv"),
        LibraryInfo("AniSkip / AnimeSkip API", "Anime giriş/bitiş atlaması için topluluk veri tabanı", "https://aniskip.com"),
        // ── Ağ Katmanı ───────────────────────────────────────────────
        LibraryInfo("Retrofit 2", "Android için tip güvenli HTTP REST istemcisi", "https://github.com/square/retrofit"),
        LibraryInfo("OkHttp 4", "Verimli HTTP/2 ağ iletişim katmanı", "https://github.com/square/okhttp"),
        LibraryInfo("Gson", "Google JSON serileştirme/ayrıştırma kütüphanesi", "https://github.com/google/gson"),
        // ── UI & Görüntü ─────────────────────────────────────────────
        LibraryInfo("Jetpack Compose", "Android için modern bildirimsel UI kütüphanesi", "https://developer.android.com/compose"),
        LibraryInfo("Coil 3", "Kotlin öncelikli eş zamansız görüntü yükleme kütüphanesi", "https://github.com/coil-kt/coil"),
        // ── Veri & Depolama ──────────────────────────────────────────
        LibraryInfo("Room Database", "Çevrimdışı öncelikli SQLite ORM veri tabanı mimarisi", "https://developer.android.com/training/data-storage/room"),
        LibraryInfo("DataStore", "Jetpack DataStore – tip güvenli ayar depolama", "https://developer.android.com/topic/libraries/architecture/datastore"),
        LibraryInfo("Supabase client", "Bulut veri eşitleme, gerçek zamanlı abonelik ve kimlik doğrulama", "https://supabase.com"),
        // ── Bağımlılık Enjeksiyonu & Eş Zamanlılık ──────────────────
        LibraryInfo("Hilt (Dagger)", "Android için derleme zamanı bağımlılık enjeksiyonu", "https://dagger.dev/hilt"),
        LibraryInfo("Kotlin Coroutines & Flow", "Reaktif veri akışı ve eş zamansız programlama", "https://github.com/Kotlin/kotlinx.coroutines"),
        LibraryInfo("WorkManager", "Garantili arka plan iş zamanlayıcısı (güncelleme, bildirim)", "https://developer.android.com/topic/libraries/architecture/workmanager"),
    )

    fun openUrl(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        }
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.ArrowBack,
                        contentDescription = stringResource(R.string.geri),
                        tint = KitsugiColors.textPrimary
                    )
                }

                Text(
                    text = KitsugiStrings.get(R.string.inline_about_the_app_3a16868),
                    color = KitsugiColors.textPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        },
        containerColor = KitsugiColors.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(24.dp))

                // App Brand
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Info,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Kitsugi Anime & Manga",
                    color = KitsugiColors.textPrimary,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )

                Text(
                    text = KitsugiStrings.get(R.string.inline_version_1_s_2e56b1d, version),
                    color = KitsugiColors.textMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Info Card
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = KitsugiColors.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.hakkinda),
                            color = KitsugiColors.textPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = KitsugiStrings.get(R.string.inline_kitsugi_is_an_all_in_dd47c3b),
                            color = KitsugiColors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Divider(color = KitsugiColors.surfaceStrong, thickness = 1.dp)

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .tvClickable { openUrl(projectRepo) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Public,
                                contentDescription = null,
                                tint = KitsugiColors.textPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "GitHub Repository",
                                    color = KitsugiColors.textPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "github.com/BlackDamage-Dev/Kitsugi-Beta",
                                    color = KitsugiColors.textMuted,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .tvClickable { openUrl(pluginsRepo) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Code,
                                contentDescription = null,
                                tint = KitsugiColors.textPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Kitsugi Plugins (Eklentiler)",
                                    color = KitsugiColors.textPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "github.com/BlackDamage-Plugin/Kitsugi-Video-Plugins",
                                    color = KitsugiColors.textMuted,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .tvClickable { openUrl(mangaPluginsRepo) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                                contentDescription = null,
                                tint = KitsugiColors.textPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Kitsugi Manga Plugins (Manga Havuzu)",
                                    color = KitsugiColors.textPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "github.com/BlackDamage-Plugin/Kitsugi-Manga-Plugins",
                                    color = KitsugiColors.textMuted,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Updates Card
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = KitsugiColors.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = KitsugiStrings.get(R.string.inline_app_updates_72b007e),
                            color = KitsugiColors.textPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = KitsugiStrings.get(R.string.inline_check_for_new_versions_automatical_1803304),
                            color = KitsugiColors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1.1f)
                                    .background(
                                        color = KitsugiColors.surfaceSoft,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Otomatik Kontrol",
                                        color = KitsugiColors.textPrimary,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (autoUpdateCheckEnabled) KitsugiStrings.get(R.string.option_light) else KitsugiStrings.get(R.string.player_afr_off),
                                        color = KitsugiColors.textMuted,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Switch(
                                    checked = autoUpdateCheckEnabled,
                                    onCheckedChange = onAutoUpdateCheckEnabledChanged,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = KitsugiColors.background,
                                        checkedTrackColor = accentColor
                                    )
                                )
                            }

                            KitsugiButton(
                                onClick = onCheckForUpdatesClick,
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                                modifier = Modifier.weight(0.9f)
                            ) {
                                Text(
                                    text = KitsugiStrings.get(R.string.inline_check_now_c288ab8),
                                    color = KitsugiColors.background,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        if (onCheckForPluginUpdatesClick != null) {
                            Divider(color = KitsugiColors.surfaceStrong, thickness = 1.dp)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1.1f)) {
                                    Text(
                                        text = KitsugiStrings.get(R.string.inline_extension_updates_15cbd1c),
                                        color = KitsugiColors.textPrimary,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = KitsugiStrings.get(R.string.inline_check_the_versions_of_video_4536a47),
                                        color = KitsugiColors.textMuted,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                KitsugiButton(
                                    onClick = onCheckForPluginUpdatesClick,
                                    colors = ButtonDefaults.buttonColors(containerColor = accentColor.copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                    modifier = Modifier.weight(0.9f)
                                ) {
                                    Icon(Icons.Code, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Eklentileri Denetle",
                                        color = accentColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Release Notes Card — uygulama GitHub üzerinden güncellendiği için
                // son sürümün değişiklik notları burada okunabilir olmalı.
                ReleaseNotesCard(
                    state = releaseNotesState,
                    installedVersion = version,
                    hasUpdate = (releaseNotesState as? ReleaseNotesUiState.Ready)
                        ?.let { updateRepository.isNewerThanInstalled(it.release) } == true,
                    accentColor = accentColor,
                    onRefresh = { releaseNotesRefreshKey++ },
                    onOpenReleases = { openUrl("$projectRepo/releases") }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Debug and Logs Card
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = KitsugiColors.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = KitsugiStrings.get(R.string.inline_developer_error_logs_fb50437),
                            color = KitsugiColors.textPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        KitsugiButton(
                            onClick = { showDeveloperLogs = true },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(KitsugiStrings.get(R.string.inline_view_developer_logs_bebf6dc), color = KitsugiColors.background, fontWeight = FontWeight.Bold)
                        }

                        val errorColor = Color(0xFFEF5350)
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(context, com.kitsugi.animelist.ui.screens.crash.KitsugiCrashActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = errorColor),
                            border = androidx.compose.foundation.BorderStroke(1.dp, errorColor.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Warning, contentDescription = null, tint = errorColor, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(KitsugiStrings.get(R.string.inline_crash_error_diagnostic_report_53375ae), color = errorColor, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    try {
                                        val filesToShare = ArrayList<Uri>()

                                        // 1. Cihaz Bilgisi
                                        val infoFile = File(context.filesDir, "device_info.txt")
                                        val infoText = buildString {
                                            append("Uygulama Sürümü: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
                                            append("Cihaz Markası: ${android.os.Build.BRAND}\n")
                                            append("Cihaz Modeli: ${android.os.Build.MODEL}\n")
                                            append("Android Sürümü: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n")
                                            append("Üretici: ${android.os.Build.MANUFACTURER}\n")
                                            append("Zaman Dilimi: ${java.util.TimeZone.getDefault().id}\n")
                                            append("Yerel Saat: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n")
                                        }
                                        infoFile.writeText(infoText)
                                        val infoUri = androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            "com.kitsugi.animelist.fileprovider",
                                            infoFile
                                        )
                                        filesToShare.add(infoUri)

                                        // 2. Çökme Raporu + sessiz ölüm (native/ANR/OOM) kanıtları
                                        //    ((Java istisnası olmayan çökmeler burada yer alır)
                                        val crashRelatedFiles = listOf(
                                            "crash_log.txt",            // son Java çökmesi
                                            "crash_history.txt",        // tüm çökme geçmişi
                                            "unclean_exit.txt",         // sessiz kapanma analizi (native/ANR/lmkd)
                                            "native_crash.txt",         // native SIGSEGV/SIGABRT geri izi
                                            "logcat_at_crash.txt",      // çökme anı logcat
                                            "post_mortem_logcat.txt",   // ölüm sonrası logcat kanıtları
                                            "crash_in_crash.txt",       // çökme ekranının kendisi çöktüyse
                                            "breadcrumbs.txt",          // kullanıcı eylem izi
                                            "app_logs.txt"              // (debug) sürekli log dosyası
                                        )
                                        for (fileName in crashRelatedFiles) {
                                            val f = File(context.filesDir, fileName)
                                            if (!f.exists() || f.length() <= 0) continue
                                            try {
                                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                                    context,
                                                    "com.kitsugi.animelist.fileprovider",
                                                    f
                                                )
                                                filesToShare.add(uri)
                                            } catch (_: Throwable) {}
                                        }

                                        // 3. Arka Plan Günlükleri
                                        val logFiles = listOf("app_logs.txt", "app_logs.txt.1", "app_logs.txt.2")
                                        for (fileName in logFiles) {
                                            val logFile = File(context.filesDir, fileName)
                                            if (logFile.exists() && logFile.length() > 0) {
                                                val logUri = androidx.core.content.FileProvider.getUriForFile(
                                                    context,
                                                    "com.kitsugi.animelist.fileprovider",
                                                    logFile
                                                )
                                                filesToShare.add(logUri)
                                            }
                                        }

                                        // 4. Canlı Logcat Dökümü
                                        val currentLogcatFile = File(context.filesDir, "current_logcat.txt")
                                        withContext(Dispatchers.IO) {
                                            try {
                                                val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "*:D"))
                                                val reader = BufferedReader(InputStreamReader(process.inputStream))
                                                val writer = currentLogcatFile.bufferedWriter()
                                                var line: String?
                                                while (reader.readLine().also { line = it } != null) {
                                                    writer.write(line)
                                                    writer.newLine()
                                                }
                                                writer.close()
                                                reader.close()
                                            } catch (e: Exception) {
                                                currentLogcatFile.writeText(KitsugiStrings.get(R.string.anlik_log_alinamadi_x, e.localizedMessage))
                                            }
                                        }
                                        if (currentLogcatFile.exists() && currentLogcatFile.length() > 0) {
                                            val currentUri = androidx.core.content.FileProvider.getUriForFile(
                                                context,
                                                "com.kitsugi.animelist.fileprovider",
                                                currentLogcatFile
                                            )
                                            filesToShare.add(currentUri)
                                        }

                                        if (filesToShare.isNotEmpty()) {
                                            val intent = Intent().apply {
                                                action = Intent.ACTION_SEND_MULTIPLE
                                                type = "text/plain"
                                                putParcelableArrayListExtra(Intent.EXTRA_STREAM, filesToShare)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Hata Raporunu Paylaş"))
                                        } else {
                                            Toast.makeText(context, KitsugiStrings.get(R.string.paylasilacak_log_dosyasi_bulunamadi), Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, KitsugiStrings.get(R.string.paylasim_hatasi_x, e.localizedMessage), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            border = androidx.compose.foundation.BorderStroke(1.dp, accentColor),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(KitsugiStrings.get(R.string.inline_send_error_report_to_developer_bc60edd), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Libraries Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Code,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = KitsugiStrings.get(R.string.inline_open_source_libraries_b703dea),
                        color = KitsugiColors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Libraries List
            items(libraries.size) { index ->
                val lib = libraries[index]
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = KitsugiColors.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .tvClickable { openUrl(lib.url) }
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = lib.name,
                            color = KitsugiColors.textPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = lib.desc,
                            color = KitsugiColors.textMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Made with",
                        color = KitsugiColors.textMuted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    Icon(
                        imageVector = Icons.HeartSolid,
                        contentDescription = null,
                        tint = Color(0xFFE5484D),
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "by $developer",
                        color = KitsugiColors.textMuted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    if (showDeveloperLogs) {
        DeveloperLogsDialog(
            onDismiss = { showDeveloperLogs = false }
        )
    }
}

data class LibraryInfo(
    val name: String,
    val desc: String,
    val url: String
)

/** "Sürüm Notları" kartının durumları. */
private sealed interface ReleaseNotesUiState {
    /** İstek sürüyor (ilk açılış ya da "Yenile"). */
    object Loading : ReleaseNotesUiState
    /** Uç noktalar yanıt vermedi ya da yayınlanmış bir sürüm yok. */
    object Unavailable : ReleaseNotesUiState
    /** Ağ hatası: kullanıcıya sebep gösterilir, yeniden deneme sunulur. */
    data class Failed(val message: String) : ReleaseNotesUiState
    /** Son sürümün notları hazır. */
    data class Ready(val release: AppRelease) : ReleaseNotesUiState
}

/**
 * Uygulama Hakkında → **Sürüm Notları** kartı.
 *
 * Uygulama GitHub Releases üzerinden güncellendiği için kullanıcı yeni sürümü kurmadan
 * önce ne değiştiğini görebilmelidir: kart, GitHub'daki **en son** sürümün başlığını,
 * yayın tarihini ve markdown notlarını ([CleanReleaseNotesText] ile sadeleştirilmiş)
 * gösterir. Kurulu sürüm en güncel olsa bile notlar gösterilir; yalnızca "Güncelleme var"
 * rozetinin görünürlüğü değişir.
 */
@Composable
private fun ReleaseNotesCard(
    state: ReleaseNotesUiState,
    installedVersion: String,
    hasUpdate: Boolean,
    accentColor: Color,
    onRefresh: () -> Unit,
    onOpenReleases: () -> Unit
) {
    val KitsugiColors = LocalKitsugiColors.current
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = KitsugiColors.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.NewReleases,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.about_release_notes_title),
                        color = KitsugiColors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.about_release_notes_subtitle),
                        color = KitsugiColors.textMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    enabled = state !is ReleaseNotesUiState.Loading
                ) {
                    Icon(
                        imageVector = Icons.Refresh,
                        contentDescription = stringResource(R.string.about_release_notes_refresh),
                        tint = if (state is ReleaseNotesUiState.Loading) KitsugiColors.textMuted else accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            when (state) {
                is ReleaseNotesUiState.Loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        color = accentColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.about_release_notes_loading),
                        color = KitsugiColors.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                is ReleaseNotesUiState.Unavailable -> Text(
                    text = stringResource(R.string.about_release_notes_unavailable),
                    color = KitsugiColors.textMuted,
                    style = MaterialTheme.typography.bodySmall
                )

                is ReleaseNotesUiState.Failed -> Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.about_release_notes_error),
                        color = KitsugiColors.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = onRefresh) {
                        Text(
                            text = stringResource(R.string.about_release_notes_retry),
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                is ReleaseNotesUiState.Ready -> {
                    val release = state.release
                    // Rozetler: en son sürüm · (güncelleme var | kurulu sürüm güncel) · tarih
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ReleaseBadge(
                            text = "v${release.versionName}",
                            accentColor = accentColor,
                            containerColor = accentColor.copy(alpha = 0.16f)
                        )
                        Text(
                            text = if (hasUpdate) {
                                stringResource(R.string.about_release_notes_update_available)
                            } else {
                                stringResource(R.string.about_release_notes_up_to_date)
                            },
                            color = if (hasUpdate) accentColor else KitsugiColors.textMuted,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        text = stringResource(R.string.about_release_notes_installed, installedVersion),
                        color = KitsugiColors.textMuted,
                        style = MaterialTheme.typography.labelSmall
                    )

                    if (release.releaseTitle.isNotBlank() &&
                        release.releaseTitle != "v${release.versionName}" &&
                        release.releaseTitle != release.versionName
                    ) {
                        Text(
                            text = release.releaseTitle,
                            color = KitsugiColors.textPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (release.publishedAt.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.about_release_notes_published, release.publishedAt),
                            color = KitsugiColors.textMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    // Notlar uzun olabilir: kart büyüyüp sayfayı kaplamasın diye sabit
                    // yükseklikte, kendi içinde kaydırılabilir bir kutuda gösterilir.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(color = KitsugiColors.surfaceSoft, shape = RoundedCornerShape(16.dp))
                            .border(
                                width = 1.dp,
                                color = KitsugiColors.surfaceStrong,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(14.dp)
                            .kitsugiVerticalScroll(rememberScrollState())
                    ) {
                        CleanReleaseNotesText(notes = release.releaseNotes)
                    }

                    TextButton(onClick = onOpenReleases) {
                        Icon(
                            imageVector = Icons.OpenInNew,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.about_release_notes_open_github),
                            color = accentColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/** Sürüm notları kartındaki küçük bilgi rozeti (sürüm, tarih, güncelleme durumu). */
@Composable
private fun ReleaseBadge(
    text: String,
    accentColor: Color,
    containerColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color = containerColor, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = accentColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
