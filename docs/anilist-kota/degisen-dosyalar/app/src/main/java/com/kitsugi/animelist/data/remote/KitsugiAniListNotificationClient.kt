package com.kitsugi.animelist.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * AniList bildirim (notification) API'sini yöneten istemci.
 *
 * TASK-108: Bildirim listeleme ve okunmamış sayımı için GraphQL query'leri.
 *
 * AniList API endpoint: https://graphql.anilist.co
 * Kimlik doğrulama: Bearer token (yalnızca oturum açık kullanıcılar için bildirim alınabilir)
 */
class KitsugiAniListNotificationClient {

    // ─────────────────────────────────────────────────────────────────────────
    // Önbellek
    //
    // AniList kotası 30 istek/dk. Bildirim ekranı açıldığında eskiden 2 istek
    // gidiyordu (sayfa + okunmamış sayacı), her sekme değişiminde de yeni bir
    // istek üretiliyordu. Artık:
    //  - sayfa + okunmamış sayacı TEK GraphQL belgesinde istenir,
    //  - 60 sn içindeki tekrarlar önbellekten karşılanır,
    //  - "Tümü" yanıtı önbellekteyse diğer sekmeler istemci tarafında filtrelenir.
    // ─────────────────────────────────────────────────────────────────────────

    private data class CachedPage(val page: KitsugiNotificationPage, val storedAtMs: Long)

    private companion object {
        const val PAGE_TTL_MS = 60_000L
        const val UNREAD_TTL_MS = 60_000L

        /** Sayfa önbelleği: sınıf örneği her yüklemede yeniden üretildiği için ORTAK. */
        val pageCache = java.util.concurrent.ConcurrentHashMap<String, CachedPage>()

        /** Son bilinen okunmamış sayısı — fetchNotifications() aynı yanıtta getirir. */
        @Volatile var lastUnreadCount: Int? = null
        @Volatile var lastUnreadAtMs: Long = 0L
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Veri modelleri
    // ─────────────────────────────────────────────────────────────────────────

    enum class NotificationGroup(val apiTypes: List<String>) {
        ALL(emptyList()),
        AIRING(listOf("AIRING")),
        ACTIVITY(listOf(
            "ACTIVITY_MESSAGE", "ACTIVITY_REPLY", "ACTIVITY_REPLY_SUBSCRIBED",
            "ACTIVITY_MENTION", "ACTIVITY_LIKE", "ACTIVITY_REPLY_LIKE"
        )),
        FORUM(listOf(
            "THREAD_COMMENT_MENTION", "THREAD_COMMENT_REPLY", "THREAD_COMMENT_SUBSCRIBED",
            "THREAD_COMMENT_LIKE", "THREAD_LIKE"
        )),
        FOLLOWS(listOf("FOLLOWING")),
        MEDIA(listOf("RELATED_MEDIA_ADDITION", "MEDIA_DATA_CHANGE", "MEDIA_MERGE", "MEDIA_DELETION"))
    }

    data class KitsugiNotification(
        val id: Int,
        val type: String,
        val context: String?,            // Açıklama metni (ör. "beğendi yorumunuzu")
        val createdAt: Long?,
        val dateText: String?,
        // Kullanıcı bağlantılı bildirimler
        val userId: Int?,
        val userName: String?,
        val userAvatarUrl: String?,
        // Medya bağlantılı bildirimler
        val mediaId: Int?,
        val mediaTitle: String?,
        val mediaCoverUrl: String?,
        val episode: Int?,               // Yayın bildirimleri için bölüm numarası
        val airingContexts: List<String>?, // AiringNotification.contexts listesi
        // Forum / aktivite bağlantılı bildirimler
        val activityId: Int?,
        val commentId: Int?,
        val threadId: Int?,
        val threadTitle: String?,
        // Medya silme
        val deletedMediaTitle: String?,
        val reason: String?,
        val mediaType: String? = null
    )

    data class KitsugiNotificationPage(
        val notifications: List<KitsugiNotification>,
        val hasNextPage: Boolean,
        val currentPage: Int
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Okunmamış bildirim sayısı
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Kullanıcının okunmamış bildirim sayısını döndürür.
     * Token olmadan null döner.
     */
    suspend fun fetchUnreadCount(accessToken: String): Int? {
        // 1) fetchNotifications() aynı belgede sayacı da getiriyor: ek istek YOK.
        val cached = lastUnreadCount
        if (cached != null && System.currentTimeMillis() - lastUnreadAtMs <= UNREAD_TTL_MS) {
            return cached
        }
        return withContext(Dispatchers.IO) {
            val query = """
                query UnreadNotificationCount {
                    Viewer {
                        unreadNotificationCount
                    }
                }
            """.trimIndent()
            runCatching {
                val response = KitsugiApiBase.executeAniListQuery(
                    query = query,
                    variables = JSONObject(),
                    accessToken = accessToken,
                    priority = AniListQuotaGate.Priority.CRITICAL
                ) ?: return@runCatching null
                JSONObject(response)
                    .optJSONObject("data")
                    ?.optJSONObject("Viewer")
                    ?.optInt("unreadNotificationCount")
            }.getOrNull()?.also { count ->
                lastUnreadCount = count
                lastUnreadAtMs = System.currentTimeMillis()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bildirim listesi
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sayfalandırılmış AniList bildirimlerini çeker.
     *
     * @param accessToken Kullanıcının AniList Bearer token'ı
     * @param page Sayfa numarası (1'den başlar)
     * @param perPage Sayfa başına bildirim sayısı (maks. 25 önerilir)
     * @param group Bildirim grubu filtresi; [NotificationGroup.ALL] tüm bildirimleri getirir
     * @param resetCount true ise sunucu okunmamış sayacını sıfırlar
     */
    suspend fun fetchNotifications(
        accessToken: String,
        page: Int = 1,
        perPage: Int = 25,
        group: NotificationGroup = NotificationGroup.ALL,
        resetCount: Boolean = false
    ): KitsugiNotificationPage {
        // 0) Aynı sayfa son 60 sn içinde çekildiyse ağa hiç çıkma.
        val cacheKey = "anilist:notif:$page:$perPage:${group.name}:$resetCount"
        pageCache[cacheKey]?.let { cached ->
            if (System.currentTimeMillis() - cached.storedAtMs <= PAGE_TTL_MS) return cached.page
        }
        // 0b) "Tümü" önbellekte ise sekmeleri sunucuda değil, burada filtrele:
        //     6 sekme gezinmek = 6 istek yerine 0 istek.
        if (group != NotificationGroup.ALL) {
            val allKey = "anilist:notif:$page:$perPage:${NotificationGroup.ALL.name}:false"
            val all = pageCache[allKey]
            if (all != null && System.currentTimeMillis() - all.storedAtMs <= PAGE_TTL_MS) {
                val filtered = all.page.notifications.filter { it.type in group.apiTypes }
                return all.page.copy(notifications = filtered)
            }
        }

        return withContext(Dispatchers.IO) {
            val typeInParam = if (group.apiTypes.isEmpty()) "" else ", \$typeIn: [NotificationType]"
            val typeInArg   = if (group.apiTypes.isEmpty()) "" else ", type_in: \$typeIn"

            // TASK-108: Tüm bildirim alt tiplerini kapsayan kapsamlı query.
            // NOT: `Viewer { unreadNotificationCount }` aynı belgede KARDEŞ kök alandır
            // (Page kısıtı yalnızca Page içindir). Böylece sayfa + sayaç = 1 istek.
            val query = """
                query Notifications(${'$'}page: Int, ${'$'}perPage: Int$typeInParam, ${'$'}resetCount: Boolean) {
                    Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                        notifications(resetNotificationCount: ${'$'}resetCount$typeInArg) {
                            ... on AiringNotification {
                                id contexts animeId episode
                                media { type title { userPreferred } coverImage { medium large } }
                                type createdAt
                            }
                            ... on FollowingNotification {
                                id context userId
                                user { name avatar { medium } }
                                type createdAt
                            }
                            ... on ActivityMessageNotification {
                                id context activityId userId
                                user { name avatar { medium } }
                                type createdAt
                            }
                            ... on ActivityMentionNotification {
                                id context activityId userId
                                user { name avatar { medium } }
                                type createdAt
                            }
                            ... on ActivityReplyNotification {
                                id context activityId userId
                                user { name avatar { medium } }
                                type createdAt
                            }
                            ... on ActivityReplySubscribedNotification {
                                id context activityId userId
                                user { name avatar { medium } }
                                type createdAt
                            }
                            ... on ActivityLikeNotification {
                                id context activityId userId
                                user { name avatar { medium } }
                                type createdAt
                            }
                            ... on ActivityReplyLikeNotification {
                                id context activityId userId
                                user { name avatar { medium } }
                                type createdAt
                            }
                            ... on ThreadCommentMentionNotification {
                                id context commentId
                                thread { id title }
                                userId
                                user { name avatar { medium } }
                                type createdAt
                            }
                            ... on ThreadCommentReplyNotification {
                                id context commentId
                                thread { id title }
                                userId
                                user { name avatar { medium } }
                                type createdAt
                            }
                            ... on ThreadCommentSubscribedNotification {
                                id context commentId
                                thread { id title }
                                userId
                                user { name avatar { medium } }
                                type createdAt
                            }
                            ... on ThreadCommentLikeNotification {
                                id context commentId
                                thread { id title }
                                userId
                                user { name avatar { medium } }
                                type createdAt
                            }
                            ... on ThreadLikeNotification {
                                id context threadId userId
                                user { name avatar { medium } }
                                type createdAt
                            }
                            ... on RelatedMediaAdditionNotification {
                                id context mediaId
                                media { type title { userPreferred } coverImage { medium large } }
                                type createdAt
                            }
                            ... on MediaDataChangeNotification {
                                id context mediaId reason
                                media { type title { userPreferred } coverImage { medium large } }
                                type createdAt
                            }
                            ... on MediaMergeNotification {
                                id context reason mediaId
                                media { type title { userPreferred } coverImage { medium large } }
                                type createdAt
                            }
                            ... on MediaDeletionNotification {
                                id context reason deletedMediaTitle
                                type createdAt
                            }
                        }
                        pageInfo { currentPage hasNextPage }
                    }
                    Viewer { unreadNotificationCount }
                }
            """.trimIndent()

            val variables = JSONObject()
                .put("page", page)
                .put("perPage", perPage)
                .put("resetCount", resetCount)
            if (group.apiTypes.isNotEmpty()) {
                val arr = org.json.JSONArray()
                group.apiTypes.forEach { arr.put(it) }
                variables.put("typeIn", arr)
            }

            runCatching {
                val response = KitsugiApiBase.executeAniListQuery(
                    query = query,
                    variables = variables,
                    accessToken = accessToken,
                    throwOnServiceDown = true
                )
                    ?: run {
                        // Eskiden burada tek bir "ağ/sunucu hatası" yazıyordu; oysa
                        // gerçek sebep çoğu zaman kota idi. Kullanıcı kaç saniye
                        // bekleyeceğini görsün.
                        val waitMs = AniListQuotaGate.cooldownRemainingMs()
                            .takeIf { it > 0L }
                            ?: AniListQuotaGate.waitEstimateMs(AniListQuotaGate.Priority.CRITICAL)
                        val detail = if (waitMs > 0L) {
                            "AniList kotası dolu; yaklaşık ${(waitMs / 1000).coerceAtLeast(1)} sn sonra yeniden deneyin."
                        } else {
                            "AniList bildirim isteği yanıtsız kaldı (ağ/sunucu hatası)."
                        }
                        throw AniListNotificationException(detail, null, waitMs)
                    }

                // GraphQL hataları daha önce sessizce yutuluyordu; bu yüzden kullanıcı
                // "bildirim yok" sanıyordu. Artık hata açıkça fırlatılır.
                val root = JSONObject(response)
                val errors = root.optJSONArray("errors")
                if (errors != null && errors.length() > 0) {
                    val message = errors.optJSONObject(0)?.optString("message").orEmpty()
                    throw AniListNotificationException(
                        message.ifBlank { "AniList GraphQL hatası (HTTP/yanıt doğrulanamadı)" }
                    )
                }

                val pageObj = root
                    .optJSONObject("data")
                    ?.optJSONObject("Page")
                    ?: throw AniListNotificationException("AniList yanıtı beklenen sayfa yapısında değil.")

                val notificationsArr = pageObj.optJSONArray("notifications")
                val pageInfo = pageObj.optJSONObject("pageInfo")
                val hasNext = pageInfo?.optBoolean("hasNextPage", false) ?: false
                val currentP = pageInfo?.optInt("currentPage", page) ?: page

                // Aynı yanıtta gelen okunmamış sayacı (varsa) önbelleğe yaz.
                val unreadFromDoc = root.optJSONObject("data")
                    ?.optJSONObject("Viewer")
                    ?.optInt("unreadNotificationCount", -1)
                    ?.takeIf { it >= 0 }
                if (resetCount) {
                    // Sunucu sayacı sıfırladı; rozet hemen düşsün.
                    lastUnreadCount = 0
                    lastUnreadAtMs = System.currentTimeMillis()
                } else if (unreadFromDoc != null) {
                    lastUnreadCount = unreadFromDoc
                    lastUnreadAtMs = System.currentTimeMillis()
                }

                val list = mutableListOf<KitsugiNotification>()
                if (notificationsArr != null) {
                    for (i in 0 until notificationsArr.length()) {
                        val item = notificationsArr.optJSONObject(i) ?: continue
                        list.add(parseNotification(item))
                    }
                }
                val resultPage = KitsugiNotificationPage(list, hasNext, currentP)
                pageCache[cacheKey] = CachedPage(resultPage, System.currentTimeMillis())
                resultPage
            }.getOrElse { cause ->
                // Sessiz boş liste YOK: çağıran katman gerçek nedeni gösterebilsin.
                if (cause is AniListNotificationException) throw cause
                throw AniListNotificationException(
                    cause.message ?: cause.javaClass.simpleName,
                    cause
                )
            }
        }
    }

    /** AniList bildirim akışındaki hataları taşır (ağ, token, GraphQL, kota). */
    class AniListNotificationException(
        message: String,
        cause: Throwable? = null,
        /** Kota nedeniyleyse: kaç ms sonra yeniden denenebilir (0 = bilinmiyor). */
        val retryInMs: Long = 0L
    ) : Exception(message, cause)

    // ─────────────────────────────────────────────────────────────────────────
    // Private — JSON parser
    // ─────────────────────────────────────────────────────────────────────────

    private fun parseNotification(item: JSONObject): KitsugiNotification {
        val type = item.optString("type", "UNKNOWN")
        val createdAtSec = item.optLong("createdAt").takeIf { it > 0 }
        val dateText = createdAtSec?.let {
            try {
                java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(it * 1000L))
            } catch (_: Exception) { null }
        }

        // Kullanıcı alanları
        val userObj = item.optJSONObject("user")
        val userName = userObj?.optNullableString("name")
        val userAvatarUrl = userObj?.optJSONObject("avatar")?.optNullableString("medium")

        // Medya alanları
        val mediaObj = item.optJSONObject("media")
        val mediaTitle = mediaObj?.optJSONObject("title")?.optNullableString("userPreferred")
        val mediaCoverUrl = mediaObj?.optJSONObject("coverImage")?.let {
            it.optNullableString("large") ?: it.optNullableString("medium")
        }

        // Thread alanları
        val threadObj = item.optJSONObject("thread")
        val threadTitle = threadObj?.optNullableString("title")

        // Contexts (AiringNotification) — JSONArray → List<String>
        val contextsArr = item.optJSONArray("contexts")
        val airingContexts = if (contextsArr != null) {
            (0 until contextsArr.length()).mapNotNull { contextsArr.optString(it).takeIf { s -> s.isNotBlank() } }
        } else null

        return KitsugiNotification(
            id                = item.optInt("id"),
            type              = type,
            context           = item.optNullableString("context"),
            createdAt         = createdAtSec,
            dateText          = dateText,
            userId            = item.optInt("userId").takeIf { it > 0 },
            userName          = userName,
            userAvatarUrl     = userAvatarUrl,
            mediaId           = (item.optInt("mediaId").takeIf { it > 0 }) ?: (item.optInt("animeId").takeIf { it > 0 }),
            mediaTitle        = mediaTitle,
            mediaCoverUrl     = mediaCoverUrl,
            episode           = item.optInt("episode").takeIf { it > 0 },
            airingContexts    = airingContexts,
            activityId        = item.optInt("activityId").takeIf { it > 0 },
            commentId         = item.optInt("commentId").takeIf { it > 0 },
            threadId          = (item.optInt("threadId").takeIf { it > 0 }) ?: threadObj?.optInt("id")?.takeIf { it > 0 },
            threadTitle       = threadTitle,
            deletedMediaTitle = item.optNullableString("deletedMediaTitle"),
            reason            = item.optNullableString("reason"),
            mediaType         = mediaObj?.optNullableString("type")
        )
    }
}
