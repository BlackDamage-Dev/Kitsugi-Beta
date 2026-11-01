package com.kitsugi.animelist.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kitsugi.animelist.MainActivity
import com.kitsugi.animelist.R
import com.kitsugi.animelist.utils.KitsugiStrings

/**
 * Cihaz bildirimlerinin tek çıkış kapısı.
 *
 * Neden ayrı bir sınıf?
 *   • **Kaynak başına kanal:** Kullanıcı Ayarlar > Bildirimler'de AniList'i kapatıp
 *     MAL'ı açık tutabilsin. Önceden tek bir "Kitsugi Bildirimleri" kanalı vardı.
 *   • **Önem (importance) HIGH:** Kanal DEFAULT önemdeyken `PRIORITY_HIGH` yok sayılıyor,
 *     bu yüzden bildirimler *hiçbir zaman* ekranın üstünde belirmiyordu (yalnızca
 *     gölgelikte sessizce duruyordu).
 *   • **İzin ön kontrolü:** Android 13+ izni yoksa `notify()` her seferinde
 *     `SecurityException` fırlatıyor ve worker bunu yutuyordu; artık çalıştırma başında
 *     tek seferde tespit edilip kaydediliyor.
 *   • **Gruplama + gürültü kontrolü:** Aynı kaynaktan art arda gelen bildirimler
 *     gruplanıyor; ilk bildirim sesli, kalanı sessiz düşüyor.
 *   • **Dokununca doğru ekran:** `kitsugianimelist://v1/notifications?source=…` deep link'i
 *     Bildirimler sayfasını ilgili kaynak sekmesinde açar. (Eski `NAV_TARGET` extra'sı
 *     uygulamada hiçbir yerde okunmuyordu; bildirime dokunmak rastgele bir ekrana
 *     götürüyordu.)
 */
object KitsugiNotificationDispatcher {

    private const val TAG = "NotifDispatcher"

    /** Eski tek kanal; yeni kaynak kanalları oluşturulunca silinir. */
    private const val LEGACY_CHANNEL_ID = "Kitsugi_airing_channel"

    const val CHANNEL_SYSTEM = "kitsugi_system"

    private val channelNames = mapOf(
        "anilist" to R.string.notification_channel_anilist,
        "mal" to R.string.notification_channel_mal,
        "simkl" to R.string.notification_channel_simkl,
        "kitsu" to R.string.notification_channel_kitsu,
        "shikimori" to R.string.notification_channel_shikimori,
        "bangumi" to R.string.notification_channel_bangumi
    )

    private val channelDescriptions = mapOf(
        "anilist" to R.string.notification_channel_anilist_desc,
        "mal" to R.string.notification_channel_mal_desc,
        "simkl" to R.string.notification_channel_simkl_desc,
        "kitsu" to R.string.notification_channel_kitsu_desc,
        "shikimori" to R.string.notification_channel_shikimori_desc,
        "bangumi" to R.string.notification_channel_bangumi_desc
    )

    /**
     * Hattın açık olup olmadığı.
     * [OK] dışındaki her değer, bildirim gönderilmeden önce çalıştırmanın durması demektir.
     */
    enum class Gate { OK, PERMISSION, DISABLED }

    /** Bir bildirimin cihaza düşmesi için gereken alanlar. */
    data class Item(
        val id: Int,
        val source: String,
        val title: String,
        val body: String,
        val imageUrl: String? = null,
        val avatarUrl: String? = null,
        val isMedia: Boolean = true,
        val mediaId: Int? = null,
        val whenMs: Long = System.currentTimeMillis()
    )

    // ─── Kanal ──────────────────────────────────────────────────────────────

    fun sourceKey(source: String): String = NotificationDeliveryTracker.sourceKey(source)

    /**
     * Bilinmeyen kaynaklar (ör. test bildirimi) için [CHANNEL_SYSTEM]'e düşer; aksi hâlde
     * Android 8+ hiç oluşturulmamış bir kanala gönderilen bildirimi sessizce yutar.
     */
    fun channelIdFor(source: String): String {
        val key = sourceKey(source)
        return if (channelNames.containsKey(key)) "kitsugi_src_$key" else CHANNEL_SYSTEM
    }

    /**
     * Kaynak kanallarını oluşturur. Android'de kanal ayarları oluşturulduktan sonra
     * değiştirilemez; bu yüzden önem değeri ilk kurulumda HIGH olarak verilir.
     */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        channelNames.forEach { (key, nameRes) ->
            val channel = NotificationChannel(
                "kitsugi_src_$key",
                KitsugiStrings.get(nameRes),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = channelDescriptions[key]?.let { KitsugiStrings.get(it) } ?: ""
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
            manager.createNotificationChannel(channel)
        }

        // Kanal yoksa "test bildirimi" ve sistem uyarıları için yedek kanal.
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SYSTEM,
                KitsugiStrings.get(R.string.notification_channel_system),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = KitsugiStrings.get(R.string.notification_channel_system_desc)
            }
        )

        // Eski tek kanal boş kalmasın: kullanıcı ayarlarda iki ayrı "Kitsugi Bildirimleri"
        // görüp hangisini kapatacağını bilemesin.
        runCatching { manager.deleteNotificationChannel(LEGACY_CHANNEL_ID) }
    }

    // ─── Ön kontrol ─────────────────────────────────────────────────────────

    fun gate(context: Context): Gate {
        val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!enabled) return Gate.DISABLED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return Gate.PERMISSION
        }
        return Gate.OK
    }

    /** Kullanıcıyı doğrudan uygulamanın bildirim ayarlarına götürür. */
    fun openSystemSettings(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "Unable to open notification settings: ${it.message}") }
    }

    // ─── Gönderim ───────────────────────────────────────────────────────────

    /**
     * Bildirimi gönderir.
     *
     * @return gerçekten gönderildiyse `true`.
     */
    fun post(
        context: Context,
        item: Item,
        bitmap: Bitmap? = null
    ): Boolean {
        if (gate(context) != Gate.OK) return false
        ensureChannels(context)

        val key = sourceKey(item.source)
        val channelId = channelIdFor(item.source)
        val groupKey = "kitsugi_group_$key"

        val deepLink = Uri.parse(
            "kitsugianimelist://v1/notifications?source=" + Uri.encode(key) +
                (item.mediaId?.let { "&mediaId=$it" } ?: "")
        )
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = deepLink
            // Eski sürümlerle uyumluluk: bu extra'lar artık okunmuyor ama zararsız.
            putExtra("NAV_TARGET", "NOTIFICATIONS")
            putExtra("NAV_SOURCE", item.source)
            item.mediaId?.let { putExtra("NAV_MEDIA_ID", it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            item.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayTitle = "[${item.source}] ${item.title}"
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setColor(ContextCompat.getColor(context, R.color.kitsugi_accent))
            .setContentTitle(displayTitle)
            .setContentText(item.body)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(displayTitle)
                    .bigText(item.body)
            )
            .setSubText(item.source)
            .setWhen(item.whenMs)
            .setShowWhen(true)
            .setCategory(
                if (item.isMedia) NotificationCompat.CATEGORY_EVENT
                else NotificationCompat.CATEGORY_SOCIAL
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            // Aynı kaynaktan gelen bildirimler gölgelikte tek başlık altında toplanır
            // (Android 7+ aynı grubu otomatik özetler); her kayıt yine tek tek açılabilir.
            .setGroup(groupKey)
            .setContentIntent(pendingIntent)

        if (bitmap != null) {
            builder.setLargeIcon(bitmap)
                .setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .setBigContentTitle(displayTitle)
                        .setSummaryText(item.body)
                )
        }

        return try {
            // Aynı `id` farklı kaynaklarda çakışabilir (AniList id'leri küçük, diğer
            // kaynaklar hash kullanır). Etiket olarak kaynak anahtarı verilince
            // bildirimler birbirini EZMEZ.
            NotificationManagerCompat.from(context).notify(key, item.id, builder.build())
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot show notification (permission): ${e.message}")
            NotificationDeliveryTracker.recordBlocked(context, NotificationDeliveryTracker.BLOCKED_PERMISSION)
            false
        }
    }

    /** Kullanıcı "Test bildirimi" düğmesine bastığında hattı uçtan uca kanıtlar. */
    fun postTest(context: Context): Boolean {
        ensureChannels(context)
        val item = Item(
            id = 424242,
            source = "Kitsugi",
            title = KitsugiStrings.get(R.string.notif_test_title),
            body = KitsugiStrings.get(R.string.notif_test_body),
            isMedia = false
        )
        val ok = post(context, item)
        if (ok) {
            NotificationDeliveryTracker.clearBlocked(context)
        } else {
            val reason = when (gate(context)) {
                Gate.DISABLED -> NotificationDeliveryTracker.BLOCKED_DISABLED
                Gate.PERMISSION -> NotificationDeliveryTracker.BLOCKED_PERMISSION
                Gate.OK -> NotificationDeliveryTracker.BLOCKED_PERMISSION
            }
            NotificationDeliveryTracker.recordBlocked(context, reason)
        }
        return ok
    }

    /** Gönderilecek görseli indirir (worker'ın ağ işi; kotayı korumak için 7 sn). */
    fun downloadBitmap(urlString: String?): Bitmap? {
        if (urlString.isNullOrBlank()) return null
        return try {
            val url = java.net.URL(urlString)
            val connection = url.openConnection()
            connection.connectTimeout = 7_000
            connection.readTimeout = 7_000
            connection.inputStream.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notification image download failed for $urlString: ${e.message}")
            null
        }
    }
}
