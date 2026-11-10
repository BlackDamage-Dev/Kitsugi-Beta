package com.kitsugi.animelist.ui.screens.notifications

/**
 * Sistem bildirimine dokunulduğunda hangi kaynağın sekmesinin açılacağını taşıyan
 * tek seferlik istek.
 *
 * Akış: `AiringNotificationWorker` → `PendingIntent` (`kitsugianimelist://v1/notifications?source=…`)
 * → `MainActivity` → `DeepLinkHandler` → `AppRoot` → burada park edilir →
 * `KitsugiNotificationsScreen` açılışta okur ve ilgili sayfaya gider.
 *
 * Neden ekstra intent yerine singleton? `DetailScreen.Notifications` navigasyon modelinde
 * parametresiz bir durak; modeli kaynak parametresiyle genişletmek tüm derin bağlantı
 * tablosunu değiştirirdi. Tek seferlik tüketim (`consume`) aynı isteğin iki kez
 * uygulanmasını engeller.
 */
object NotifOpenRequest {

    @Volatile
    private var pendingSourceKey: String? = null

    /** @param sourceKey "anilist", "mal", "simkl", "kitsu", "shikimori", "bangumi". */
    fun request(sourceKey: String?) {
        pendingSourceKey = sourceKey
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() }
    }

    fun consume(): String? {
        val value = pendingSourceKey
        pendingSourceKey = null
        return value
    }
}
