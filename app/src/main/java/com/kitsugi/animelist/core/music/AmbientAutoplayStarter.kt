package com.kitsugi.animelist.core.music

import android.util.Log
import com.kitsugi.animelist.data.music.AmbientSound
import com.kitsugi.animelist.data.music.AtmospherePreset
import com.kitsugi.animelist.data.music.AtmospherePresets
import com.kitsugi.animelist.data.music.MoodProfile
import com.kitsugi.animelist.data.music.MusicCatalogRepository
import com.kitsugi.animelist.data.music.ChannelPlayState
import com.kitsugi.animelist.data.music.MusicSources
import com.kitsugi.animelist.data.settings.AppSettings
import kotlinx.coroutines.delay
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

    /** Bir çevrimiçi adayın gerçekten çalmaya başlaması için beklenecek süre. */
    const val ONLINE_READY_TIMEOUT_MS = 12_000L

    /** Açılamazsa denenecek en fazla çevrimiçi aday sayısı. */
    const val MAX_ONLINE_CANDIDATES = 3

    /** Kanal durumu yoklama aralığı. */
    private const val READY_POLL_MS = 150L

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

        // ── 2) Çevrimiçi eşleşme: ÖNCE hazırla, HAZIR olunca değiştir ─────
        // ⚠️ KÖK NEDEN (kullanıcı şikâyeti: "yerel çalıyor, sonra duruyor,
        // içeriğe uygun parça bir türlü başlamıyor"): eski akış önce
        // `stopAllChannels()` ile yereli öldürüyor, sonra çevrimiçi kanalı
        // ekliyordu. Kanal hazırlığı ASENKRON olduğu için `addChannel`
        // başarıyla dönse bile akış açılamazsa (403, yanlış Archive dosyası,
        // kota, zaman aşımı) kanal ERROR'da kalıyor ve ortada çalan hiçbir
        // şey olmuyordu.
        //
        // Yeni akış: çevrimiçi aday, yerel çalmaya devam ederken SESSİZ (0f)
        // eklenir; durumu PLAYING olursa yereller bırakılır ve sesi açılır.
        // Açılamazsa aday kaldırılır, sıradaki aday denenir; hiçbiri
        // açılmazsa yerel KESİNTİSİZ devam eder ve neden nota yazılır.
        val online = runCatching {
            withTimeoutOrNull(ONLINE_TIMEOUT_MS) {
                catalog.searchForMood(
                    mood = mood,
                    genres = detectedGenres.ifEmpty { fallbackGenres },
                    settings = settings,
                    limit = 6
                )
            }
        }.getOrNull().orEmpty()

        val candidates = online.filter { it.isPlayable }.take(MAX_ONLINE_CANDIDATES)
        if (candidates.isEmpty()) {
            Log.i(TAG, "Otomatik başlatma: çevrimiçi eşleşme bulunamadı, cihaz içi devam")
            return Result(
                startedOnline = false,
                note = "Çevrimiçi parça bulunamadı — cihaz içi ses devam ediyor"
            )
        }

        // Geçişte bırakılacak yerel kanallar (çevrimiçi aday hariç hepsi).
        val localIndices = engine.channels.value.map { it.index }

        for (candidate in candidates) {
            // Archive satırlarında adres arama anında bilinmez; çözülür.
            val resolved = runCatching { catalog.resolveUrl(candidate) }.getOrNull()
            val playable = if (!resolved.isNullOrBlank() && resolved != candidate.streamUrl) {
                candidate.copy(streamUrl = resolved)
            } else {
                candidate
            }
            if (!playable.isPlayable) continue

            // Sessiz ekle: hazırlık bitene kadar kullanıcı yereli duymaya devam eder.
            val slot = engine.addChannel(playable, 0f)
            if (slot < 0) continue

            if (awaitChannelPlaying(engine, slot, ONLINE_READY_TIMEOUT_MS)) {
                // ✅ Çevrimiçi gerçekten çalıyor — şimdi yerelleri bırak.
                localIndices.forEach { index ->
                    if (index != slot) engine.stopChannel(index)
                }
                engine.setChannelVolume(slot, volume)
                engine.setMasterVolume(volume)

                val sourceName = MusicSources.byId(playable.sourceId)?.displayName
                    ?: playable.sourceId.name
                val note = buildString {
                    append("Çevrimiçi eşleşme: ").append(sourceName)
                    playable.subtitle.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                }
                Log.i(TAG, "Otomatik başlatma: çevrimiçi parça — ${playable.title} ($sourceName)")
                return Result(startedOnline = true, note = note)
            }

            // Açılamadı: adayı temizle, yerel hâlâ çalıyor — sıradakini dene.
            Log.i(TAG, "Otomatik başlatma: aday açılamadı (${playable.title}), cihaz içi devam")
            engine.stopChannel(slot)
        }

        return Result(
            startedOnline = false,
            note = "Çevrimiçi parça açılamadı — cihaz içi ses devam ediyor"
        )
    }

    /**
     * Bir kanalın gerçekten çalmaya başlamasını bekler.
     *
     * `addChannel` yalnızca yuvayı ayırır; hazırlık arka planda sürer.
     * Kanal `PLAYING` olana kadar (ya da `ERROR`/zaman aşımı) bekler.
     */
    private suspend fun awaitChannelPlaying(
        engine: AmbientAudioEngine,
        slot: Int,
        timeoutMs: Long
    ): Boolean {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            val channel = engine.channels.value.firstOrNull { it.index == slot }
                ?: return false
            when (channel.state) {
                ChannelPlayState.PLAYING -> return true
                ChannelPlayState.ERROR -> return false
                else -> delay(READY_POLL_MS)
            }
        }
        return false
    }

    data class Result(
        val startedOnline: Boolean,
        val note: String?
    )

    private const val TAG = "AmbientAutoplay"
}
