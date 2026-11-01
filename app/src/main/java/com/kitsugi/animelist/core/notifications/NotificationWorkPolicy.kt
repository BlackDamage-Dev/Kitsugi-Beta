package com.kitsugi.animelist.core.notifications

import com.kitsugi.animelist.data.settings.AppSettings

internal data class NotificationAccountLinks(
    val aniList: Boolean,
    val mal: Boolean,
    val simkl: Boolean,
    val kitsu: Boolean,
    val shikimori: Boolean,
    val bangumi: Boolean,
    val tmdb: Boolean = false
) {
    /** TMDB account polling is not an airing-feed source and has its own gate below. */
    val any: Boolean
        get() = aniList || mal || simkl || kitsu || shikimori || bangumi
}

internal fun shouldScheduleNotificationWork(
    settings: AppSettings,
    accounts: NotificationAccountLinks
): Boolean =
    (settings.airingNotificationsEnabled && accounts.any) ||
        (settings.bangumiNotificationsEnabled && accounts.bangumi) ||
        (settings.aniListNotificationsEnabled && accounts.aniList) ||
        (settings.malNotificationsEnabled && accounts.mal) ||
        (settings.simklNotificationsEnabled && accounts.simkl) ||
        (settings.kitsuNotificationsEnabled && accounts.kitsu) ||
        (settings.shikimoriNotificationsEnabled && accounts.shikimori) ||
        (settings.tmdbNotificationsEnabled && accounts.tmdb) ||
        // Automatic account sync needs the same periodic worker even when user-visible
        // notifications are disabled. The worker still suppresses notification UI in that case.
        (settings.syncEnabledTmdb && accounts.tmdb)
