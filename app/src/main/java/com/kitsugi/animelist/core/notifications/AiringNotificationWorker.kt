package com.kitsugi.animelist.core.notifications

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kitsugi.animelist.R
import com.kitsugi.animelist.data.auth.BangumiApiClient
import com.kitsugi.animelist.data.auth.BangumiAuthStore
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.data.auth.KitsuApiClient
import com.kitsugi.animelist.data.auth.ShikimoriApiClient
import com.kitsugi.animelist.data.auth.SimklImportManager
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.local.MediaEntryRepository
import com.kitsugi.animelist.data.local.toDomain
import com.kitsugi.animelist.data.remote.BangumiIdNamespace
import com.kitsugi.animelist.data.remote.KitsugiAniListNotificationClient
import com.kitsugi.animelist.data.remote.KitsugiAiringCalendarClient
import com.kitsugi.animelist.data.remote.KitsugiBangumiDetailClient
import com.kitsugi.animelist.data.remote.SimklCalendarClient
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import com.kitsugi.animelist.utils.KitsugiStrings
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Arka plan bildirim üretecisi.
 *
 * **Cihaza gerçekten düşen** bildirimlerin tek kaynağı burasıdır; uygulama içindeki
 * Bildirimler sayfası ise kaynak API'lerinden canlı okur. İkisinin ayrışmaması için:
 *   • Her kaynak ayrı ayrı sayılır ve sonucu [NotificationDeliveryTracker]'a yazılır
 *     (teşhis panelinde "cihaz bildirimleri" bölümü bunu gösterir).
 *   • Hat kapalıysa (izin yok / bildirimler kapatılmış) ağ isteği bile atılmaz.
 *   • İlk çalıştırmada geçmiş kayıtlar bildirim olarak DÜŞMEZ (yalnızca "görüldü"
 *     işaretlenir); aksi hâlde bir kaynağı açan kullanıcıya onlarca bildirim birden
 *     geliyordu.
 *   • Bir çalıştırmada kaynak başına en fazla [MAX_PER_SOURCE_PER_RUN] bildirim düşer.
 *
 * Kaynak gerçekleri (değişmedi, ama artık daha görünür):
 *   AniList   → `Page.notifications` (gerçek bildirim ucu)
 *   MAL       → takvim + izleme listesi eşleşmesi (API'de bildirim ucu yok)
 *   Simkl     → takvim v2 CDN + izleme listesi (bildirim ucu yok)
 *   Kitsu     → `nextRelease` alanı (bildirim ucu yok)
 *   Shikimori → `/api/users/:id/messages` (gerçek bildirim ucu, `messages` izni gerekir)
 *   Bangumi   → MAL takvimi + çapraz kimlik (bgm subject → MAL id) eşleşmesi
 */
class AiringNotificationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "AiringWorker"
        private const val PREFS_NAME = "Kitsugi_notified_prefs"
        private const val KEY_NOTIFIED_ANILIST = "notified_anilist_ids"
        private const val KEY_NOTIFIED_MAL = "notified_mal_keys"
        private const val KEY_NOTIFIED_SIMKL = "notified_simkl_keys"
        private const val KEY_NOTIFIED_KITSU = "notified_kitsu_keys"
        private const val KEY_NOTIFIED_SHIKIMORI = "notified_shikimori_ids"
        private const val KEY_NOTIFIED_BANGUMI = "notified_bangumi_keys"

        /** Sel hâlinde bildirim yağmasın: kaynak başına çalıştırma limiti. */
        private const val MAX_PER_SOURCE_PER_RUN = 6

        /** Bangumi → MAL çapraz kimlik çözümü ağ isteği gerektirir; üst sınır. */
        private const val BANGUMI_MAX_RESOLVE = 15

        /** Son 24 saat penceresi (MAL/Simkl/Kitsu/Bangumi yayın eşleşmesi). */
        private const val AIRING_WINDOW_MS = 24 * 60 * 60 * 1000L

        /** Simkl tam liste senkronu: her çalıştırmada DB'yi yeniden yazmak gereksiz. */
        private const val KEY_SIMKL_FULL_SYNC_AT = "simkl_full_sync_at"
        private const val SIMKL_FULL_SYNC_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "AiringNotificationWorker background check started")

        val settings = SettingsDataStore(context).settingsFlow.first()

        // Kaydedilmiş bir anahtar, hesap koptuktan sonra da duruyor olabilir:
        // bağlantı yoksa o kaynak için hiç istek atılmaz.
        val aniListTokenForGate = ExternalAuthManager.getAniListToken(context)
        val malTokenForGate = ExternalAuthManager.getMalToken(context)
        val simklTokenForGate = ExternalAuthManager.getSimklToken(context)
        val kitsuTokenForGate = ExternalAuthManager.getKitsuToken(context)
        val shikimoriTokenForGate = ExternalAuthManager.getShikimoriToken(context)
        val bangumiConnected = ExternalAuthManager.getAuthState(context).isBangumiConnected
        val hasLinkedAccount = listOf(
            aniListTokenForGate,
            malTokenForGate,
            simklTokenForGate,
            kitsuTokenForGate,
            shikimoriTokenForGate
        ).any { !it.isNullOrBlank() } || bangumiConnected

        val isAiringEnabled = settings.airingNotificationsEnabled && hasLinkedAccount
        val isAniListEnabled = settings.aniListNotificationsEnabled && !aniListTokenForGate.isNullOrBlank()
        val isMalEnabled = settings.malNotificationsEnabled && !malTokenForGate.isNullOrBlank()
        val isSimklEnabled = settings.simklNotificationsEnabled && !simklTokenForGate.isNullOrBlank()
        val isKitsuEnabled = settings.kitsuNotificationsEnabled && !kitsuTokenForGate.isNullOrBlank()
        val isShikimoriEnabled = settings.shikimoriNotificationsEnabled && !shikimoriTokenForGate.isNullOrBlank()

        if (!isAiringEnabled && !isAniListEnabled && !isMalEnabled && !isSimklEnabled &&
            !isKitsuEnabled && !isShikimoriEnabled
        ) {
            Log.d(TAG, "No linked notification sources are enabled. Canceling stale periodic work.")
            NotificationScheduler.cancelIfNoActiveSources(context)
            return Result.success()
        }

        // ── Cihaz hattı ön kontrolü ──────────────────────────────────────────
        // İzin yoksa her `notify()` çağrısı SecurityException üretiyordu; artık hiç
        // ağ isteği atılmadan, sebebi kaydedilerek çıkılır.
        val gate = KitsugiNotificationDispatcher.gate(context)
        if (gate != KitsugiNotificationDispatcher.Gate.OK) {
            val reason = if (gate == KitsugiNotificationDispatcher.Gate.PERMISSION) {
                NotificationDeliveryTracker.BLOCKED_PERMISSION
            } else {
                NotificationDeliveryTracker.BLOCKED_DISABLED
            }
            Log.w(TAG, "Device notifications blocked ($reason); skipping this run")
            NotificationDeliveryTracker.recordBlocked(context, reason)
            NotificationScheduler.cancelIfNoActiveSources(context)
            return Result.success()
        }
        KitsugiNotificationDispatcher.ensureChannels(context)

        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Seri hâle getirilmiş izleme listesi (yerel DB)
        val db = KitsugiDatabase.getDatabase(context)
        val watchingEntries = try {
            db.mediaEntryDao().getAll()
                .filter { entity ->
                    entity.status == WatchStatus.Watching.name ||
                        entity.status == WatchStatus.Repeating.name
                }
                .map { it.toDomain() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load database watching entries: ${e.message}")
            emptyList()
        }

        // ── Çapraz kaynak tekrar engeli ──────────────────────────────────────
        // Aynı bölüm birden fazla kaynakta izleniyor olabilir (ör. hem MAL hem Kitsu
        // listesinde). Her kaynak kendi defterini tuttuğu için aynı bölüm iki ayrı
        // bildirim olarak düşüyordu. Bu küme YALNIZCA tek çalıştırma içinde geçerlidir.
        val postedEpisodeKeys = mutableSetOf<String>()
        fun episodeKey(title: String, episode: Int): String =
            title.lowercase().replace(Regex("[^a-z0-9]+"), "").take(60) + "#" + episode

        // ── 1. AniList (gerçek bildirim ucu) ─────────────────────────────────
        if (isAniListEnabled) {
            val aniListToken = ExternalAuthManager.getAniListToken(context)
            if (!aniListToken.isNullOrBlank()) {
                var posted = 0
                var seen = 0
                try {
                    val client = KitsugiAniListNotificationClient()
                    val page = client.fetchNotifications(aniListToken, page = 1, perPage = 25)
                    val notifiedIds = NotifiedKeys(sharedPrefs, KEY_NOTIFIED_ANILIST)
                    val firstRun = notifiedIds.isFirstRun

                    for (notif in page.notifications) {
                        val notifIdStr = notif.id.toString()
                        if (notifiedIds.contains(notifIdStr)) continue
                        notifiedIds.add(notifIdStr)
                        seen++
                        // İlk çalıştırma: geçmiş kayıtlar sessizce "görüldü" işaretlenir.
                        if (firstRun) continue
                        if (posted >= MAX_PER_SOURCE_PER_RUN) continue

                        val title = notif.mediaTitle ?: notif.userName ?: notif.threadTitle ?: "AniList"
                        val isMedia = notif.type in setOf(
                            "AIRING",
                            "RELATED_MEDIA_ADDITION",
                            "MEDIA_DATA_CHANGE",
                            "MEDIA_MERGE"
                        )
                        val ok = postItem(
                            id = notif.id,
                            title = title,
                            body = formatAniListBody(notif),
                            source = "AniList",
                            imageUrl = notif.mediaCoverUrl,
                            avatarUrl = notif.userAvatarUrl,
                            isMedia = isMedia,
                            mediaId = notif.mediaId,
                            whenMs = notif.createdAt?.let { value ->
                                if (value > 10_000_000_000L) value else value * 1000L
                            } ?: System.currentTimeMillis()
                        )
                        if (ok) posted++
                    }
                    notifiedIds.save(200)
                    NotificationDeliveryTracker.recordRun(
                        context = context,
                        source = "AniList",
                        posted = posted,
                        seen = seen
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "AniList notifications fetch failed: ${e.message}", e)
                    NotificationDeliveryTracker.recordRun(
                        context, "AniList", posted, seen, error = e.message
                    )
                }
            }
        }

        // ── 2. MAL takvimi + yerel izleme listesi ────────────────────────────
        if (isMalEnabled || isAiringEnabled) {
            var posted = 0
            var seen = 0
            try {
                val calendarClient = KitsugiAiringCalendarClient()
                val nowMs = System.currentTimeMillis()
                val allEntries = calendarClient.fetchAiringWindow(
                    fromEpochSec = (nowMs - AIRING_WINDOW_MS) / 1000L,
                    toEpochSec = (nowMs + 60 * 60 * 1000L) / 1000L
                )

                val matched = allEntries.filter { entry ->
                    watchingEntries.any { me ->
                        (entry.malId != null && me.malId == entry.malId) ||
                            (me.source == "anilist" && me.malId == 100_000_000 + entry.aniListId)
                    }
                }

                val notifiedMalKeys = NotifiedKeys(sharedPrefs, KEY_NOTIFIED_MAL)
                val firstRun = notifiedMalKeys.isFirstRun

                for (entry in matched) {
                    val triggerMs = entry.airingAt * 1000L
                    if (triggerMs > nowMs || triggerMs <= nowMs - AIRING_WINDOW_MS) continue
                    val malKey = "${entry.malId}_${entry.episode}"
                    if (notifiedMalKeys.contains(malKey)) continue
                    notifiedMalKeys.add(malKey)
                    seen++
                    if (firstRun) continue
                    if (posted >= MAX_PER_SOURCE_PER_RUN) continue

                    if (!postedEpisodeKeys.add(episodeKey(entry.title, entry.episode))) continue
                    val malId = entry.malId ?: 0
                    val ok = postItem(
                        id = (malId * 1000 + entry.episode) and Int.MAX_VALUE,
                        title = entry.title,
                        body = KitsugiStrings.get(R.string.bolum_x_artik_yayinda, entry.episode),
                        source = "MyAnimeList",
                        imageUrl = entry.coverUrl,
                        isMedia = true,
                        mediaId = malId,
                        whenMs = triggerMs
                    )
                    if (ok) posted++
                }
                notifiedMalKeys.save(200)
                NotificationDeliveryTracker.recordRun(context, "MyAnimeList", posted, seen)
            } catch (e: Exception) {
                Log.e(TAG, "MAL Airing Calendar fetch failed: ${e.message}", e)
                NotificationDeliveryTracker.recordRun(context, "MyAnimeList", posted, seen, error = e.message)
            }
        }

        // ── 3. Simkl (takvim v2 + izleme listesi) ────────────────────────────
        if (isSimklEnabled) {
            val simklToken = ExternalAuthManager.getSimklToken(context)
            if (!simklToken.isNullOrBlank()) {
                var posted = 0
                var seen = 0
                try {
                    // Tam liste senkronu 6 saatte bir: takvim bildirimi için listeyi her
                    // 30 dakikada bir yeniden yazmak boşa DB yükü (pil + yazma) demekti.
                    val lastFullSync = sharedPrefs.getLong(KEY_SIMKL_FULL_SYNC_AT, 0L)
                    if (System.currentTimeMillis() - lastFullSync > SIMKL_FULL_SYNC_INTERVAL_MS) {
                        runCatching {
                            val importedEntries = SimklImportManager.fetchAllLists(simklToken)
                            val repository = MediaEntryRepository(db.mediaEntryDao())
                            // Sil-yeniden-yaz sırasında +18 işaretlerini koru (bkz. repository)
                            repository.replaceSourcePreservingAdultFlags("simkl", importedEntries)
                            sharedPrefs.edit()
                                .putLong(KEY_SIMKL_FULL_SYNC_AT, System.currentTimeMillis())
                                .apply()
                            Log.d(TAG, "Simkl background sync successful: ${importedEntries.size} entries")
                        }.onFailure {
                            Log.w(TAG, "Simkl list sync skipped: ${it.message}")
                        }
                    }

                    val calendarClient = SimklCalendarClient()
                    val watchingIds = runCatching { calendarClient.fetchWatchingIds(simklToken) }.getOrNull()
                    val calendar = calendarClient.fetchAll()
                    val now = System.currentTimeMillis()
                    val notifiedSimklKeys = NotifiedKeys(sharedPrefs, KEY_NOTIFIED_SIMKL)
                    val firstRun = notifiedSimklKeys.isFirstRun

                    var matchedCount = 0
                    for (entry in calendar) {
                        val isMatched = watchingIds != null && !watchingIds.isEmpty &&
                            watchingIds.matches(entry)
                        if (!isMatched) continue

                        val airingAt = entry.airingAtMs
                        if (airingAt > now || airingAt <= now - AIRING_WINDOW_MS) continue
                        matchedCount++

                        // Her bölüm kendi anahtarına sahip (eskiden bölüm ayırt
                        // edilmiyordu, dizi başına tek bildirim gidiyordu).
                        val key = "simkl_${entry.simklId}_${entry.episode ?: 0}_$airingAt"
                        if (notifiedSimklKeys.contains(key)) continue
                        notifiedSimklKeys.add(key)
                        seen++
                        if (firstRun) continue
                        if (posted >= MAX_PER_SOURCE_PER_RUN) continue

                        if (!postedEpisodeKeys.add(episodeKey(entry.title, entry.episode ?: 0))) continue
                        val label = entry.episodeLabel
                        val ok = postItem(
                            id = key.hashCode() and 0x7fffffff,
                            title = entry.title,
                            body = if (label != null) {
                                KitsugiStrings.get(R.string.notification_episode_released, label)
                            } else {
                                KitsugiStrings.get(R.string.notification_new_movie_release)
                            },
                            source = "Simkl",
                            imageUrl = entry.posterUrl,
                            isMedia = true,
                            mediaId = entry.simklId,
                            whenMs = airingAt
                        )
                        if (ok) posted++
                    }
                    Log.d(TAG, "Simkl airing matched=$matchedCount")
                    notifiedSimklKeys.save(400)
                    NotificationDeliveryTracker.recordRun(context, "TMDB & Simkl", posted, seen)
                } catch (e: Exception) {
                    Log.e(TAG, "Simkl background sync failed: ${e.message}", e)
                    NotificationDeliveryTracker.recordRun(context, "TMDB & Simkl", posted, seen, error = e.message)
                }
            }
        }

        // ── 4. Kitsu (nextRelease alanı) ─────────────────────────────────────
        if (isKitsuEnabled || isAiringEnabled) {
            val kitsuToken = ExternalAuthManager.getKitsuToken(context)
            val kitsuUserId = ExternalAuthManager.getKitsuUserId(context)
            if (!kitsuToken.isNullOrBlank() && !kitsuUserId.isNullOrBlank()) {
                var posted = 0
                var seen = 0
                try {
                    val entries = KitsuApiClient.fetchAllLibraryEntries(kitsuToken, kitsuUserId)
                    val active = entries.filter {
                        it.status.equals("current", true) || it.status.equals("watching", true)
                    }
                    val notifiedKitsuKeys = NotifiedKeys(sharedPrefs, KEY_NOTIFIED_KITSU)
                    val firstRun = notifiedKitsuKeys.isFirstRun
                    val now = System.currentTimeMillis()

                    for (entry in active) {
                        val releaseMs = parseIsoToEpochMs(entry.nextRelease) ?: continue
                        if (releaseMs > now || releaseMs <= now - AIRING_WINDOW_MS) continue

                        val key = "kitsu_${entry.id}_$releaseMs"
                        if (notifiedKitsuKeys.contains(key)) continue
                        notifiedKitsuKeys.add(key)
                        seen++
                        if (firstRun) continue
                        if (posted >= MAX_PER_SOURCE_PER_RUN) continue

                        if (!postedEpisodeKeys.add(episodeKey(entry.title, entry.progress + 1))) continue
                        val ok = postItem(
                            id = key.hashCode() and 0x7fffffff,
                            title = entry.title,
                            body = KitsugiStrings.get(
                                R.string.bolum_entry_progress_1_yayinlandi,
                                entry.progress + 1
                            ),
                            source = "Kitsu",
                            imageUrl = entry.imageUrl,
                            isMedia = true,
                            mediaId = entry.animeId ?: entry.mangaId,
                            whenMs = releaseMs
                        )
                        if (ok) posted++
                    }
                    notifiedKitsuKeys.save(400)
                    NotificationDeliveryTracker.recordRun(context, "Kitsu", posted, seen)
                } catch (e: Exception) {
                    Log.e(TAG, "Kitsu polling failed: ${e.message}", e)
                    NotificationDeliveryTracker.recordRun(context, "Kitsu", posted, seen, error = e.message)
                }
            }
        }

        // ── 5. Shikimori (gerçek bildirim ucu: /api/users/:id/messages) ──────
        if (isShikimoriEnabled) {
            val shikiToken = runCatching { ExternalAuthManager.getOrRefreshShikimoriToken(context) }
                .getOrNull() ?: ExternalAuthManager.getShikimoriToken(context)
            val shikiUserId = ExternalAuthManager.getShikimoriUserId(context)
            if (!shikiToken.isNullOrBlank() && shikiUserId != null) {
                var posted = 0
                var seen = 0
                try {
                    val notifiedShikiIds = NotifiedKeys(sharedPrefs, KEY_NOTIFIED_SHIKIMORI)
                    val firstRun = notifiedShikiIds.isFirstRun
                    val messages = ShikimoriApiClient.fetchMessages(
                        token = shikiToken,
                        userId = shikiUserId,
                        type = "notifications",
                        limit = 30
                    ) + ShikimoriApiClient.fetchMessages(
                        token = shikiToken,
                        userId = shikiUserId,
                        type = "news",
                        limit = 15
                    )

                    for (message in messages) {
                        // Okunmuş bildirimler için tekrar bildirim gönderilmez.
                        if (message.read) continue
                        val key = "${message.type}_${message.id}"
                        if (notifiedShikiIds.contains(key)) continue
                        notifiedShikiIds.add(key)
                        seen++
                        if (firstRun) continue
                        if (posted >= MAX_PER_SOURCE_PER_RUN) continue

                        val bodyText = message.body
                            .replace(Regex("\\[/?[a-zA-Z0-9=*]+[^\\]]*\\]"), " ")
                            .replace(Regex("\\s+"), " ")
                            .trim()
                            .ifBlank { KitsugiStrings.get(R.string.notif_sys_generic) }

                        val ok = postItem(
                            id = key.hashCode() and 0x7fffffff,
                            title = message.targetTitle ?: "Shikimori",
                            body = bodyText.take(200),
                            source = "Shikimori",
                            imageUrl = message.targetImageUrl,
                            avatarUrl = message.fromAvatarUrl,
                            isMedia = message.targetImageUrl != null,
                            mediaId = message.linkedId?.toInt(),
                            whenMs = parseIsoToEpochMs(message.createdAt) ?: System.currentTimeMillis()
                        )
                        if (ok) posted++
                    }
                    notifiedShikiIds.save(400)
                    NotificationDeliveryTracker.recordRun(context, "Shikimori", posted, seen)
                } catch (e: ShikimoriApiClient.ShikimoriScopeException) {
                    Log.w(TAG, "Shikimori 'messages' izni yok; bildirim atlanıyor: ${e.message}")
                    NotificationDeliveryTracker.recordRun(
                        context, "Shikimori", posted, seen, error = "messages izni yok"
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Shikimori polling failed: ${e.message}", e)
                    NotificationDeliveryTracker.recordRun(context, "Shikimori", posted, seen, error = e.message)
                }
            }
        }

        // ── 6. Bangumi (MAL takvimi + çapraz kimlik eşleşmesi) ───────────────
        // Bangumi API'sinde bildirim ucu yoktur. Uygulama içi sayfa da aynı yolu
        // kullanır: bgm subject → MAL id çözümü + yayın takvimi.
        if (isAiringEnabled && bangumiConnected) {
            var posted = 0
            var seen = 0
            try {
                // Önce Bangumi'nin KENDİ "在看" (DOING) koleksiyonu okunur — uygulama
                // içindeki Bildirimler sayfası da bunu kullanıyor. Yalnızca yerel DB'ye
                // bakmak, hiç yerelleştirilmemiş (import edilmemiş) kayıtları kaçırıyordu.
                val serverStableIds = runCatching {
                    val token = BangumiAuthStore.getValidToken(context)
                    val username = if (!token.isNullOrBlank()) {
                        BangumiAuthStore.resolveUsername(context)
                    } else null
                    if (!token.isNullOrBlank() && !username.isNullOrBlank()) {
                        BangumiApiClient.getUserCollections(
                            token = token,
                            username = username,
                            collectionType = BangumiApiClient.CollectionType.DOING,
                            limit = 50
                        ).data.mapNotNull { BangumiIdNamespace.stableIdFromRaw(it.subjectId) }
                    } else {
                        emptyList()
                    }
                }.onFailure { Log.w(TAG, "Bangumi koleksiyonu okunamadı: ${it.message}") }
                    .getOrDefault(emptyList())

                val localStableIds = watchingEntries
                    .filter {
                        it.source.equals(BangumiIdNamespace.SOURCE, ignoreCase = true) &&
                            BangumiIdNamespace.isStableId(it.malId)
                    }
                    .mapNotNull { it.malId }

                val bangumiStableIds = (serverStableIds + localStableIds).distinct()
                if (bangumiStableIds.isNotEmpty()) {
                    val malToStable = coroutineScope {
                        bangumiStableIds.take(BANGUMI_MAX_RESOLVE).map { stableId ->
                            async {
                                val cross = runCatching {
                                    KitsugiBangumiDetailClient.resolveCrossIds(stableId, MediaType.Anime)
                                }.getOrNull()
                                cross?.malId?.takeIf { it in 1..99_999_999 }?.let { it to stableId }
                            }
                        }.mapNotNull { it.await() }
                    }.toMap()

                    if (malToStable.isNotEmpty()) {
                        val nowMs = System.currentTimeMillis()
                        val airings = KitsugiAiringCalendarClient()
                            .fetchAiringWindow(
                                fromEpochSec = (nowMs - AIRING_WINDOW_MS) / 1000L,
                                toEpochSec = (nowMs + 60 * 60 * 1000L) / 1000L
                            )
                        val notifiedBangumiKeys = NotifiedKeys(sharedPrefs, KEY_NOTIFIED_BANGUMI)
                        val firstRun = notifiedBangumiKeys.isFirstRun

                        for (entry in airings) {
                            val malId = entry.malId ?: continue
                            val stableId = malToStable[malId] ?: continue
                            val triggerMs = entry.airingAt * 1000L
                            if (triggerMs > nowMs || triggerMs <= nowMs - AIRING_WINDOW_MS) continue

                            val key = "bangumi_${malId}_${entry.episode}"
                            if (notifiedBangumiKeys.contains(key)) continue
                            notifiedBangumiKeys.add(key)
                            seen++
                            if (firstRun) continue
                            if (posted >= MAX_PER_SOURCE_PER_RUN) continue

                            if (!postedEpisodeKeys.add(episodeKey(entry.title, entry.episode))) continue
                            val ok = postItem(
                                id = key.hashCode() and 0x7fffffff,
                                title = entry.title,
                                body = KitsugiStrings.get(R.string.bolum_x_artik_yayinda, entry.episode),
                                source = "Bangumi",
                                imageUrl = entry.coverUrl,
                                isMedia = true,
                                mediaId = stableId,
                                whenMs = triggerMs
                            )
                            if (ok) posted++
                        }
                        notifiedBangumiKeys.save(200)
                    }
                }
                // İzlenen Bangumi kaydı yoksa da satır yazılır: teşhis paneli
                // "henüz çalışmadı" ile "çalıştı, eşleşme yok" durumunu ayırt etsin.
                NotificationDeliveryTracker.recordRun(context, "Bangumi", posted, seen)
            } catch (e: Exception) {
                Log.e(TAG, "Bangumi airing matching failed: ${e.message}", e)
                NotificationDeliveryTracker.recordRun(context, "Bangumi", posted, seen, error = e.message)
            }
        }

        NotificationScheduler.cancelIfNoActiveSources(context)
        return Result.success()
    }

    // ─── Yardımcılar ────────────────────────────────────────────────────────

    /**
     * "Bu kayıt için bildirim gönderildi mi?" defteri.
     *
     * **Neden zaman damgalı?** Eski sürüm `StringSet` tutuyordu; `StringSet` okunduğunda
     * sıra bilgisi kaybolduğu için budama (`takeLast`) RASTGELE kayıt siliyordu. Silinen
     * kayıt 24 saatlik pencerede hâlâ göründüğü için aynı bölüm bir sonraki çalıştırmada
     * **ikinci kez** bildiriliyordu. Artık her kayıt `anahtar|epochMs` biçiminde tutulur
     * ve budama **en eskiyi** atar.
     *
     * Eski (damgasız) kayıtlar `0` damgasıyla okunur; geçiş sırasında tekrar bildirim
     * olmaması için korunurlar.
     */
    private class NotifiedKeys(
        private val prefs: SharedPreferences,
        private val storeKey: String
    ) {
        private val timestamps: MutableMap<String, Long> = run {
            val raw = prefs.getStringSet(storeKey, emptySet()).orEmpty()
            val map = LinkedHashMap<String, Long>(raw.size)
            raw.forEach { entry ->
                val sep = entry.lastIndexOf('|')
                if (sep > 0) {
                    val ts = entry.substring(sep + 1).toLongOrNull()
                    if (ts != null) {
                        map[entry.substring(0, sep)] = ts
                        return@forEach
                    }
                }
                map[entry] = 0L
            }
            map
        }

        /** Defter hiç yazılmamışsa ilk çalıştırma sayılır. */
        val isFirstRun: Boolean = !prefs.contains(storeKey)

        fun contains(key: String): Boolean = timestamps.containsKey(key)

        fun add(key: String, atMs: Long = System.currentTimeMillis()) {
            timestamps[key] = atMs
        }

        fun save(limit: Int) {
            val kept = timestamps.entries
                .sortedByDescending { it.value }
                .take(limit)
            prefs.edit()
                .putStringSet(storeKey, kept.map { "${it.key}|${it.value}" }.toSet())
                .apply()
        }
    }

    private fun postItem(
        id: Int,
        title: String,
        body: String,
        source: String,
        imageUrl: String? = null,
        avatarUrl: String? = null,
        isMedia: Boolean = true,
        mediaId: Int? = null,
        whenMs: Long = System.currentTimeMillis()
    ): Boolean {
        val bitmap = KitsugiNotificationDispatcher.downloadBitmap(avatarUrl ?: imageUrl)
        return KitsugiNotificationDispatcher.post(
            context = context,
            item = KitsugiNotificationDispatcher.Item(
                id = id,
                source = source,
                title = title,
                body = body,
                imageUrl = imageUrl,
                avatarUrl = avatarUrl,
                isMedia = isMedia,
                mediaId = mediaId,
                whenMs = whenMs
            ),
            bitmap = bitmap
        )
    }

    private fun formatAniListBody(notif: KitsugiAniListNotificationClient.KitsugiNotification): String {
        return when (notif.type) {
            "AIRING" -> KitsugiStrings.get(
                R.string.bolum_x_artik_yayinda,
                notif.episode ?: 1
            )
            "FOLLOWING" -> KitsugiStrings.get(R.string.notif_sys_following, userName(notif))
            "ACTIVITY_MESSAGE" -> KitsugiStrings.get(R.string.notif_sys_message, userName(notif))
            "ACTIVITY_REPLY" -> KitsugiStrings.get(R.string.notif_sys_reply, userName(notif))
            "ACTIVITY_REPLY_SUBSCRIBED" ->
                KitsugiStrings.get(R.string.notif_sys_reply_subscribed, userName(notif))
            "ACTIVITY_MENTION" -> KitsugiStrings.get(R.string.notif_sys_mention, userName(notif))
            "ACTIVITY_LIKE" -> KitsugiStrings.get(R.string.notif_sys_like, userName(notif))
            "ACTIVITY_REPLY_LIKE" -> KitsugiStrings.get(R.string.notif_sys_reply_like, userName(notif))
            "THREAD_COMMENT_MENTION" -> KitsugiStrings.get(R.string.notif_sys_forum_mention, userName(notif))
            "THREAD_COMMENT_REPLY" -> KitsugiStrings.get(R.string.notif_sys_forum_reply, userName(notif))
            "THREAD_SUBSCRIBED", "THREAD_COMMENT_SUBSCRIBED" ->
                KitsugiStrings.get(R.string.notif_sys_forum_subscribed, userName(notif))
            "THREAD_COMMENT_LIKE" -> KitsugiStrings.get(R.string.notif_sys_forum_like, userName(notif))
            "THREAD_LIKE" -> KitsugiStrings.get(R.string.notif_sys_thread_like, userName(notif))
            "RELATED_MEDIA_ADDITION" -> KitsugiStrings.get(
                R.string.notif_sys_related_media,
                notif.mediaTitle ?: "AniList"
            )
            "MEDIA_DATA_CHANGE" -> KitsugiStrings.get(
                R.string.notif_sys_media_updated,
                notif.mediaTitle ?: "AniList",
                notif.reason ?: "site"
            )
            "MEDIA_MERGE" -> KitsugiStrings.get(
                R.string.notif_sys_media_merged,
                notif.mediaTitle ?: "AniList"
            )
            "MEDIA_DELETION" -> KitsugiStrings.get(
                R.string.notif_sys_media_deleted,
                notif.deletedMediaTitle ?: "AniList"
            )
            else -> notif.context ?: KitsugiStrings.get(R.string.notif_sys_generic)
        }
    }

    private fun userName(notif: KitsugiAniListNotificationClient.KitsugiNotification): String =
        notif.userName ?: KitsugiStrings.get(R.string.notif_sys_some_user)

    /** "2026-07-20T04:00:00Z" / "+09:00 ofsetli" ISO damgalarını epoch ms'e çevirir. */
    private fun parseIsoToEpochMs(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val text = raw.trim()
        return try {
            if (text.endsWith("Z", true)) Instant.parse(text).toEpochMilli()
            else OffsetDateTime.parse(text).toInstant().toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }
}
