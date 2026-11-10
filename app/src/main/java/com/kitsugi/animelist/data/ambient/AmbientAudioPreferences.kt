package com.kitsugi.animelist.data.ambient

import android.content.Context
import android.content.SharedPreferences

/**
 * 🎚️ Ortam sesi motorunun kalıcı tercihleri.
 *
 * Aynı dosya (`ambient_audio_settings`) hem okuyucu paneli hem de
 * **Ayarlar → Harici Entegrasyonlar → Müzik** bölümü tarafından
 * kullanılır. Böylece panelden açılan bir kaynak ayarlar ekranında da
 * anında işaretli görünür (tek doğruluk kaynağı).
 *
 * Kaydedilen değerler:
 *  - `enabled_sources`        → açık ses kaynaklarının anahtarları (virgülle)
 *  - `online_playback_enabled`→ çevrimiçi akış oynatımı (v2.4.471'den beri aynı anahtar)
 *  - `auto_play_online`       → okuyucu açılınca internetten otomatik çalma
 *  - `blocked_track_ids`      → "bir daha önerme" listesi
 *  - `queue_shuffle`          → kuyruk karıştırma
 */
class AmbientAudioPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** Açık kaynaklar. Hiç kayıt yoksa tümü açıktır. */
    fun enabledSources(): Set<AmbientAudioSource> {
        val raw = prefs.getString(KEY_ENABLED_SOURCES, null) ?: return AmbientAudioSource.entries.toSet()
        val parsed = raw.split(',')
            .mapNotNull { key -> AmbientAudioSource.entries.firstOrNull { it.key == key.trim() } }
            .toSet()
        // Kayıt boş/bozuksa tümüne dön: arama sessizce ölmesin.
        return parsed.ifEmpty { AmbientAudioSource.entries.toSet() }
    }

    fun setEnabledSources(sources: Set<AmbientAudioSource>) {
        prefs.edit()
            .putString(KEY_ENABLED_SOURCES, sources.sortedBy { it.key }.joinToString(",") { it.key })
            .apply()
    }

    /** Çevrimiçi akış oynatımı açık mı? Kapalıysa yalnızca indirilmiş dosyalar çalar. */
    var onlinePlaybackEnabled: Boolean
        get() = prefs.getBoolean(KEY_ONLINE_PLAYBACK, true)
        set(value) {
            prefs.edit().putBoolean(KEY_ONLINE_PLAYBACK, value).apply()
        }

    /** Okuyucu açılınca manga adıyla internetten otomatik çalma. */
    var autoPlayOnline: Boolean
        get() = prefs.getBoolean(KEY_AUTO_PLAY_ONLINE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_PLAY_ONLINE, value).apply()
        }

    /**
     * "Bir daha önerme" listesi.
     *
     * Kullanıcının çalmak istemediği parçaların kimlikleri; hem paneldeki
     * çöp/X düğmesiyle hem de otomatik sıralamada kullanılır. Kalıcıdır.
     */
    fun blockedTrackIds(): Set<String> =
        prefs.getStringSet(KEY_BLOCKED_TRACKS, emptySet()).orEmpty().toSet()

    fun blockTrack(id: String) {
        if (id.isBlank()) return
        val next = blockedTrackIds() + id
        prefs.edit().putStringSet(KEY_BLOCKED_TRACKS, next).apply()
    }

    fun unblockTrack(id: String) {
        if (id.isBlank()) return
        val next = blockedTrackIds() - id
        prefs.edit().putStringSet(KEY_BLOCKED_TRACKS, next).apply()
    }

    fun clearBlockedTracks() {
        prefs.edit().remove(KEY_BLOCKED_TRACKS).apply()
    }

    /** Kuyruk karıştırılsın mı? (ExoPlayer shuffle) */
    var shuffleEnabled: Boolean
        get() = prefs.getBoolean(KEY_SHUFFLE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SHUFFLE, value).apply()
        }

    companion object {
        const val FILE_NAME = "ambient_audio_settings"
        private const val KEY_ENABLED_SOURCES = "enabled_sources"
        private const val KEY_ONLINE_PLAYBACK = "online_playback_enabled"
        private const val KEY_AUTO_PLAY_ONLINE = "auto_play_online"
        private const val KEY_BLOCKED_TRACKS = "blocked_track_ids"
        private const val KEY_SHUFFLE = "queue_shuffle"
    }
}
