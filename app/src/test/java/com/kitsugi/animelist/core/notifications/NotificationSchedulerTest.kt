package com.kitsugi.animelist.core.notifications

import com.kitsugi.animelist.data.settings.AppSettings
import com.kitsugi.animelist.data.settings.IntegrationsAndNotificationsSettings
import com.kitsugi.animelist.data.settings.ProfileAndListSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSchedulerTest {
    @Test
    fun globalAiringNotificationsRequireAtLeastOneLinkedAccount() {
        assertFalse(
            shouldScheduleNotificationWork(
                settings = AppSettings(
                    integrations = IntegrationsAndNotificationsSettings(airingNotificationsEnabled = true)
                ),
                accounts = NotificationAccountLinks(
                    aniList = false,
                    mal = false,
                    simkl = false,
                    kitsu = false,
                    shikimori = false,
                    bangumi = false
                )
            )
        )
    }

    @Test
    fun globalAiringNotificationsCanUseAnyLinkedSupportedAccount() {
        assertTrue(
            shouldScheduleNotificationWork(
                settings = AppSettings(
                    integrations = IntegrationsAndNotificationsSettings(airingNotificationsEnabled = true)
                ),
                accounts = NotificationAccountLinks(
                    aniList = false,
                    mal = false,
                    simkl = false,
                    kitsu = false,
                    shikimori = false,
                    bangumi = true
                )
            )
        )
    }

    @Test
    fun sourceNotificationRequiresItsOwnLinkedAccount() {
        assertFalse(
            shouldScheduleNotificationWork(
                settings = AppSettings(
                    integrations = IntegrationsAndNotificationsSettings(simklNotificationsEnabled = true)
                ),
                accounts = NotificationAccountLinks(
                    aniList = false,
                    mal = false,
                    simkl = false,
                    kitsu = true,
                    shikimori = false,
                    bangumi = false
                )
            )
        )
    }

    @Test
    fun tmdbNotificationRequiresConnectionAndSchedulesWhenLinked() {
        val settings = AppSettings(
            integrations = IntegrationsAndNotificationsSettings(tmdbNotificationsEnabled = true)
        )
        assertFalse(
            shouldScheduleNotificationWork(
                settings = settings,
                accounts = NotificationAccountLinks(
                    aniList = false, mal = false, simkl = false,
                    kitsu = false, shikimori = false, bangumi = false, tmdb = false
                )
            )
        )
        assertTrue(
            shouldScheduleNotificationWork(
                settings = settings,
                accounts = NotificationAccountLinks(
                    aniList = false, mal = false, simkl = false,
                    kitsu = false, shikimori = false, bangumi = false, tmdb = true
                )
            )
        )
    }

    @Test
    fun globalAiringNotificationsDoNotUseTmdbOnlyAccount() {
        assertFalse(
            shouldScheduleNotificationWork(
                settings = AppSettings(
                    integrations = IntegrationsAndNotificationsSettings(airingNotificationsEnabled = true)
                ),
                accounts = NotificationAccountLinks(
                    aniList = false, mal = false, simkl = false,
                    kitsu = false, shikimori = false, bangumi = false, tmdb = true
                )
            )
        )
    }

    @Test
    fun tmdbAutomaticSyncSchedulesBackgroundRefreshWithoutNotifications() {
        assertTrue(
            shouldScheduleNotificationWork(
                settings = AppSettings(
                    profile = ProfileAndListSettings(syncEnabledTmdb = true)
                ),
                accounts = NotificationAccountLinks(
                    aniList = false, mal = false, simkl = false,
                    kitsu = false, shikimori = false, bangumi = false, tmdb = true
                )
            )
        )
    }

    @Test
    fun linkedSourceNotificationSchedulesWork() {
        assertTrue(
            shouldScheduleNotificationWork(
                settings = AppSettings(
                    integrations = IntegrationsAndNotificationsSettings(kitsuNotificationsEnabled = true)
                ),
                accounts = NotificationAccountLinks(
                    aniList = false,
                    mal = false,
                    simkl = false,
                    kitsu = true,
                    shikimori = false,
                    bangumi = false
                )
            )
        )
    }
}
