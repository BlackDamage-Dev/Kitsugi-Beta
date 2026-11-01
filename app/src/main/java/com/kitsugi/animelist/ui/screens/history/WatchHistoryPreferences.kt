package com.kitsugi.animelist.ui.screens.history

import android.content.Context
import android.content.SharedPreferences

/**
 * İzleme geçmişi ekranının kullanıcı tercihleri (sıralama, filtre, gruplama).
 *
 * Neden ayrı bir depo? Bu ekran oynatıcıdan (tam ekran) ve uygulama ayarlarından
 * açılabiliyor; kullanıcı "kaldığı yerden devam" için bir kez "Devam edenler"
 * filtresini seçtiğinde bu tercihin sonraki açılışlarda da korunması beklenir.
 * `SettingsDataStore` (DataStore) şemasını büyütmek yerine, ekranla sınırlı küçük
 * bir SharedPreferences dosyası kullanılıyor — ayar göçü riski yok.
 *
 * Diskte duran bir enum adı yeni sürümde bulunmayabilir; bu durumda çökmek yerine
 * varsayılana dönülür.
 */
class WatchHistoryPreferences(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var sortMode: WatchHistorySortMode
        get() {
            val raw = prefs.getString(KEY_SORT, null) ?: return WatchHistorySortMode.RECENT
            return WatchHistorySortMode.values().firstOrNull { it.name == raw } ?: WatchHistorySortMode.RECENT
        }
        set(value) = prefs.edit().putString(KEY_SORT, value.name).apply()

    var progressFilter: WatchHistoryProgressFilter
        get() {
            val raw = prefs.getString(KEY_PROGRESS_FILTER, null) ?: return WatchHistoryProgressFilter.ALL
            return WatchHistoryProgressFilter.values().firstOrNull { it.name == raw }
                ?: WatchHistoryProgressFilter.ALL
        }
        set(value) = prefs.edit().putString(KEY_PROGRESS_FILTER, value.name).apply()

    var mediaFilter: WatchHistoryMediaFilter
        get() {
            val raw = prefs.getString(KEY_MEDIA_FILTER, null) ?: return WatchHistoryMediaFilter.ALL
            return WatchHistoryMediaFilter.values().firstOrNull { it.name == raw }
                ?: WatchHistoryMediaFilter.ALL
        }
        set(value) = prefs.edit().putString(KEY_MEDIA_FILTER, value.name).apply()

    var groupBySeries: Boolean
        get() = prefs.getBoolean(KEY_GROUP, false)
        set(value) = prefs.edit().putBoolean(KEY_GROUP, value).apply()

    companion object {
        private const val PREFS_NAME = "watch_history_prefs"
        private const val KEY_SORT = "sort_mode"
        private const val KEY_PROGRESS_FILTER = "progress_filter"
        private const val KEY_MEDIA_FILTER = "media_filter"
        private const val KEY_GROUP = "group_by_series"
    }
}
