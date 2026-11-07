package com.kitsugi.animelist.core.music

import android.util.Log
import com.kitsugi.animelist.data.music.AmbientSound
import com.kitsugi.animelist.data.music.AtmospherePreset
import com.kitsugi.animelist.data.music.AtmospherePresets
import com.kitsugi.animelist.data.music.MoodProfile
import com.kitsugi.animelist.data.music.MusicCatalogRepository
import com.kitsugi.animelist.data.music.MusicSources
import com.kitsugi.animelist.data.settings.AppSettings
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ▶️ Otomatik başlatma orkestrasyonu
 *
 * Neden ayrı dosya: bu mantık `MangaReaderScreen` içindeki **1700 satırlık**
 * composable'ın gövdesindeydi. Compose derleyicisi tek bir composable için
 * çok büyük bir JVM metodu üretiyor; buraya eklenen her satır o metodu
 * büyütüp derleme bellek tavanına yaklaştırıyordu (CI'da
 * `Couldn't transform method node` / `OutOfMemoryError`). Ayrıca test
 * edilebilir: burada Compose yok, saf kotlin var.
 *
 * Akış:
 *  1. Cihaz içi karışım **hemen** başlar (ağ beklemez).
 *  2. `musicAutoPlayIncludeOnline` açıksa içeriğin duygu profiline uygun
 *     gerçek parça aranır; bulunursa cihaz içi karışım yumuşakça kapanır ve
 *     parça başlar.
 */
object AmbientAutoplayStarter {

    /** Çevrimiçi aramanın üst sınırı — otomatik başlatma UI'ı bekletmemeli. */
    const val ONLINE_TIMEOUT_MS = 8_000L

    /**
     * Otomatik başlatmayı uygular.
     *
     * @param presetId öneri motorunun seçtiği preset (yoksa `null`)
     * @param firstSound preset yoksa çalınacak ilk ses
     * @return hangi yolun seçildiği + panele yazılacak kısa not
     */
    suspend fun start(
        engine: AmbientAudioEngine,
        catalog: MusicCatalogRepository,
        settings: AppSettings,
        mood: MoodProfile,
        detectedGenres: List<String>,
        fallbackGenres: List<String>,
        presetId: String?,
        firstSound: AmbientSound?,
        targetVolume: Float
    ): Result {
        val volume = targetVolume.coerceIn(0.05f, 1f)

        // ── 1) Anında cihaz içi ses ─────────────────────────────────────
        val preset: AtmospherePreset? = presetId?.let { AtmospherePresets.byId(it) }
        if (preset != null) {
            engine.playPreset(preset)
            engine.setMasterVolume(volume)
        } else if (firstSound != null) {
            engine.addChannel(firstSound, volume)
            engine.setMasterVolume(volume)
        }

        if (!settings.musicAutoPlayIncludeOnline) {
            return Result(startedOnline = false, note = null)
        }

        // ── 2) Çevrimiçi eşleşme ────────────────────────────────────────
        val online = runCatching {
            withTimeoutOrNull(ONLINE_TIMEOUT_MS) {
                catalog.searchForMood(
                    mood = mood,
                    genres = detectedGenres.ifEmpty { fallbackGenres },
                    settings = settings,
                    limit = 4
                )
            }
        }.getOrNull().orEmpty()

        val pick = online.firstOrNull { it.isPlayable }
        if (pick == null) {
            Log.i(TAG, "Otomatik başlatma: çevrimiçi eşleşme bulunamadı, cihaz içi devam")
            return Result(startedOnline = false, note = null)
        }

        // Archive satırlarında adres arama anında bilinmez; çözülür.
        val resolved = runCatching { catalog.resolveUrl(pick) }.getOrNull()
        val playable = if (!resolved.isNullOrBlank() && resolved != pick.streamUrl) {
            pick.copy(streamUrl = resolved)
        } else {
            pick
        }
        if (!playable.isPlayable) {
            return Result(startedOnline = false, note = null)
        }

        engine.stopAllChannels()
        if (engine.addChannel(playable, volume) < 0) {
            // Yuva/uzak sınırı dolduysa sessiz kalma: cihaz içi sesi geri koy.
            if (preset != null) engine.playPreset(preset) else firstSound?.let {
                engine.addChannel(it, volume)
            }
            return Result(startedOnline = false, note = null)
        }
        engine.setMasterVolume(volume)

        val sourceName = MusicSources.byId(pick.sourceId)?.displayName ?: pick.sourceId.name
        val note = buildString {
            append("Çevrimiçi eşleşme: ").append(sourceName)
            pick.subtitle.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        }
        Log.i(TAG, "Otomatik başlatma: çevrimiçi parça — ${pick.title} ($sourceName)")
        return Result(startedOnline = true, note = note)
    }

    data class Result(
        val startedOnline: Boolean,
        val note: String?
    )

    private const val TAG = "AmbientAutoplay"
}
