package com.kitsugi.animelist.core.notifications

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kitsugi.animelist.data.auth.ExternalAuthManager
import com.kitsugi.animelist.data.settings.SettingsDataStore
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

import com.kitsugi.animelist.data.settings.AppSettings

object NotificationScheduler {
    private const val TAG = "NotificationScheduler"
    /** Teşhis paneli bu isimle WorkManager durumunu sorgular. */
    const val WORK_NAME = "airing_notifications_work"
    private const val GATE_PREFS = "kitsugi_notif_gate"
    private const val GATE_KEY = "gate"

    /**
     * Schedules only when at least one enabled notification source has a linked account.
     * Returns true when work is scheduled and false when it is cancelled.
     */
    suspend fun reconcile(context: Context): Boolean {
        val appContext = context.applicationContext
        return try {
            val settings = SettingsDataStore(appContext).settingsFlow.first()
            val shouldSchedule = shouldScheduleNotificationWork(
                settings = settings,
                accounts = getLinkedAccounts(appContext)
            )

            if (!shouldSchedule) {
                cancel(appContext)
                false
            } else {
                enqueuePeriodicWork(appContext, settings.notificationInterval)
                true
            }
        } catch (error: Exception) {
            Log.e(TAG, "Unable to verify linked notification sources; canceling periodic work", error)
            runCatching { cancel(appContext) }
            false
        }
    }

    /**
     * Called by the worker after checking/refreshing credentials. It cancels stale
     * periodic work if all enabled sources have become unlinked, without re-enqueuing
     * the currently-running periodic request.
     */
    suspend fun cancelIfNoActiveSources(context: Context) {
        val appContext = context.applicationContext
        try {
            val settings = SettingsDataStore(appContext).settingsFlow.first()
            val shouldSchedule = shouldScheduleNotificationWork(
                settings = settings,
                accounts = getLinkedAccounts(appContext)
            )
            if (!shouldSchedule) cancel(appContext)
        } catch (error: Exception) {
            Log.e(TAG, "Unable to verify linked notification sources; canceling periodic work", error)
            runCatching { cancel(appContext) }
        }
    }

    private fun getLinkedAccounts(context: Context): NotificationAccountLinks =
        NotificationAccountLinks(
            aniList = !ExternalAuthManager.getAniListToken(context).isNullOrBlank(),
            mal = !ExternalAuthManager.getMalToken(context).isNullOrBlank(),
            simkl = !ExternalAuthManager.getSimklToken(context).isNullOrBlank(),
            kitsu = !ExternalAuthManager.getKitsuToken(context).isNullOrBlank(),
            shikimori = !ExternalAuthManager.getShikimoriToken(context).isNullOrBlank(),
            bangumi = ExternalAuthManager.getAuthState(context).isBangumiConnected,
            tmdb = com.kitsugi.animelist.data.auth.TmdbAuthStore.isConnected(context)
        )

    private fun enqueuePeriodicWork(context: Context, intervalMinutes: Int) {
        val workManager = WorkManager.getInstance(context)

        // Constraints: require internet connectivity to fetch updates from external APIs.
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // WorkManager minimum periodic interval is 15 minutes.
        val periodicInterval = intervalMinutes.coerceAtLeast(15).toLong()
        val workRequest = PeriodicWorkRequestBuilder<AiringNotificationWorker>(
            periodicInterval,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        Log.d(TAG, "Scheduling periodic airing notification work: interval = $periodicInterval mins")
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    /**
     * Yalnızca bildirim izni/cihaz ayarı **değiştiyse** planlı işi uzlaştırır.
     *
     * Her `onStart`'ta koşulsuz `reconcile` çağırmak gereksiz; burada son bilinen
     * durum saklanır ve yalnızca geçiş olduğunda iş kurulur/iptal edilir.
     * Örnek: kullanıcı sistem ayarlarından bildirimleri kapatıp geri döner.
     */
    suspend fun reconcileIfGateChanged(context: Context): Boolean {
        val appContext = context.applicationContext
        val gateName = KitsugiNotificationDispatcher.gate(appContext).name
        val prefs = appContext.getSharedPreferences(GATE_PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getString(GATE_KEY, null)
        if (previous == gateName) return false
        prefs.edit().putString(GATE_KEY, gateName).apply()
        return reconcile(appContext)
    }

    /** Cancels the scheduled work request. */
    fun cancel(context: Context) {
        Log.d(TAG, "Canceling periodic airing notification work")
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
    }
}
