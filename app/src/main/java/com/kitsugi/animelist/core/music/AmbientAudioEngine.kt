package com.kitsugi.animelist.core.music

import android.content.Context
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.kitsugi.animelist.data.music.AmbientOrigin
import com.kitsugi.animelist.data.music.AmbientSound
import com.kitsugi.animelist.data.music.AtmospherePreset
import com.kitsugi.animelist.data.music.AtmospherePresets
import com.kitsugi.animelist.data.music.ChannelPlayState
import com.kitsugi.animelist.data.music.ChannelState
import com.kitsugi.animelist.data.music.MAX_AMBIENT_CHANNELS
import com.kitsugi.animelist.data.music.MixChannel
import com.kitsugi.animelist.data.music.MixCodecPayload
import com.kitsugi.animelist.data.music.MixProfile
import com.kitsugi.animelist.data.music.MusicSourceId
import com.kitsugi.animelist.data.music.ProceduralSoundCatalog
import com.kitsugi.animelist.data.music.SleepTimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 🎚️ Kitsugi Birleşik Ortam Sesi Motoru
 *
 * **Tek arayüz, her kaynak.** Prosedürel sesler (`AudioTrack`) ve uzak
 * akışlar (`ExoPlayer`) aynı kanal havuzunda yaşar. Paneldeki her buton
 * (ses seviyesi, mute, kaldır, duraklat) kanalın kaynağı ne olursa olsun
 * aynı şekilde çalışır.
 *
 * ## Neden yeniden yazıldı
 * Eski tasarımda iki sabit kanal vardı (`MUSIC` + `AMBIENT`). Bu yüzden:
 *  - Uzak bir Jamendo parçası ile prosedürel yağmuru aynı anda çalmak imkânsızdı
 *  - Ses seviyesi/mute gibi kontroller yalnızca 2 kanala uygulanabiliyordu
 *  - "Ses başına mute" gibi özellikler altyapı yokluğundan eklenemiyordu
 *
 * Yeni tasarım: **6 yuvalı kanal havuzu** (`MAX_AMBIENT_CHANNELS`).
 * Her yuva bağımsız olarak prosedürel veya uzak ses çalabilir.
 *
 * ## Kanal yaşam döngüsü
 * ```
 * addChannel(sound)  → boş yuva bulunur, yüklenir
 * playChannel(i)     → fade-in, PLAYING
 * setChannelVolume(i)→ fade ile yeni seviyeye geçer
 * toggleMute(i)      → 0'a fade, hafızada ses seviyesi korunur
 * stopChannel(i)     → fade-out, yuva boşalır
 * ```
 *
 * ## Mimari not
 * Prosedürel sesler için her kanal bir `ProceduralSoundPlayer` (AudioTrack,
 * gerçek zamanlı PCM). Uzak sesler için her kanal bir `ExoPlayer`. İkisi de
 * `ChannelVoice` arayüzüyle soyutlanır → motor hangi tür olduğunu bilmez.
 */
@UnstableApi
class AmbientAudioEngine(
    private val context: Context
) {

    companion object {
        private const val TAG = "AmbientAudioEngine"
        private const val FADE_MS = 900L
        private const val FADE_STEP_MS = 40L

        /**
         * Süreç genelinde **tek** motor.
         *
         * Neden singleton: okuyucudan çıkınca ses devam etsin, arka plandan
         * bildirimle kontrol edilebilsin diye tek örnek şart. `remember`
         * ile ekran başına motor yaratılırsa ekran kapanınca ses kesilir.
         */
        @Volatile
        private var instance: AmbientAudioEngine? = null

        fun get(context: Context): AmbientAudioEngine =
            instance ?: synchronized(this) {
                instance ?: AmbientAudioEngine(context.applicationContext).also { instance = it }
            }

        /** Test veya tam kapanış için. */
        fun reset() {
            synchronized(this) {
                instance?.release()
                instance = null
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Ses indirmeleri — çevrimdışı çalma için. */
    val downloadManager: AmbientDownloadManager = AmbientDownloadManager.get(context)

    // ─────────────────────────────────────────────────────────────────────
    //  Kanal havuzu
    // ─────────────────────────────────────────────────────────────────────

    /** Yuva → aktif ses sesi. `null` = yuva boş. */
    private val voices = arrayOfNulls<ChannelVoice>(MAX_AMBIENT_CHANNELS)

    private val _channels = MutableStateFlow<List<ChannelState>>(emptyList())
    /** Aktif kanalların anlık durumu — UI bunu dinler. */
    val channels: StateFlow<List<ChannelState>> = _channels.asStateFlow()

    // ─────────────────────────────────────────────────────────────────────
    //  Global durum
    // ─────────────────────────────────────────────────────────────────────

    private val _masterVol = MutableStateFlow(0.8f)
    val masterVolume: StateFlow<Float> = _masterVol.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _activeTitle = MutableStateFlow<String?>(null)
    /** Mini player'da gösterilen başlık. */
    val activeTitle: StateFlow<String?> = _activeTitle.asStateFlow()

    private val _activePresetId = MutableStateFlow<String?>(null)
    val activePresetId: StateFlow<String?> = _activePresetId.asStateFlow()

    /**
     * Ekran kapanınca devam etsin mi (foreground service kararı için).
     *
     * Varsayılan **açık**: kullanıcı okuyucudan çıkınca sesin kesilmesi
     * beklenmez. Ayarlardan kapatılabilir.
     */
    var continueInBackground: Boolean = true

    private val presetJobs = mutableListOf<Job>()

    // ─────────────────────────────────────────────────────────────────────
    //  Uyku zamanlayıcısı
    // ─────────────────────────────────────────────────────────────────────

    private val _sleepTimer = MutableStateFlow(SleepTimerState())
    /** Uyku zamanlayıcısı — kalan süre, fade, duraklatma. */
    val sleepTimer: StateFlow<SleepTimerState> = _sleepTimer.asStateFlow()

    private var sleepJob: Job? = null

    /**
     * Sayaç döngüsünün okuduğu **tek doğruluk kaynağı**.
     *
     * `SleepTimerState.remainingMs` UI için; buradaki değer ise uzatma
     * (`extendSleepTimer`) ve duraklatma ile birlikte yaşayan gerçek
     * sayaç. İkisini ayrı tutmak, `extendSleepTimer`'ın hiç işe
     * yaramaması hatasını önler.
     */
    @Volatile
    private var sleepRemainingMs: Long = 0L

    // ─────────────────────────────────────────────────────────────────────
    //  Favoriler / son çalınanlar
    // ─────────────────────────────────────────────────────────────────────

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _recentIds = MutableStateFlow<List<String>>(emptyList())
    /** En son çalınan sesler (en yeni başta, en çok 20). */
    val recentIds: StateFlow<List<String>> = _recentIds.asStateFlow()

    /** Kaydedilmiş karışım profilleri. */
    private val _profiles = MutableStateFlow<List<MixProfile>>(emptyList())
    val profiles: StateFlow<List<MixProfile>> = _profiles.asStateFlow()

    // ─────────────────────────────────────────────────────────────────────
    //  Kanal ekleme / çalma
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Bir sesi havuza ekler ve çalmaya başlar.
     *
     * Aynı ses zaten çalıyorsa ikinci kopya açılmaz; mevcut kanal
     * kaldırılır (toggle davranışı).
     *
     * @param sound Eklenecek ses (prosedürel veya uzak)
     * @param volume Başlangıç ses seviyesi
     * @return Oluşturulan kanalın yuvası, başarısızsa `-1`
     */
    fun addChannel(sound: AmbientSound, volume: Float = 0.6f): Int {
        // Zaten çalıyorsa → kaldır (toggle)
        val existing = _channels.value.firstOrNull { it.sound.id == sound.id }
        if (existing != null) {
            stopChannel(existing.index)
            return existing.index
        }

        // Boş yuva bul
        val slot = voices.indexOfFirst { it == null }
        if (slot < 0) {
            Log.w(TAG, "Kanal sınırı dolu ($MAX_AMBIENT_CHANNELS)")
            return -1
        }

        // ⚠️ StreamVoice.start() akış hazır olana kadar bekler (en çok 4 sn).
        // Ana thread'de çağrılırsa arayüz donar. Uzak sesler için işi
        // arka plana atıp hemen yuvayı rezerve ediyoruz.
        // Uzak sesler HER ZAMAN arka planda hazırlanır — `streamUrl` null
        // olsa bile. (Internet Archive'da null olur ve adres çalma anında
        // çözülür; eskiden bu durumda ana thread'de ağ beklenebiliyordu.)
        if (sound.origin != AmbientOrigin.PROCEDURAL) {
            return addChannelAsync(sound, slot, volume)
        }

        val voice = createVoice(sound) ?: return -1
        voices[slot] = voice

        val state = ChannelState(
            index = slot,
            sound = sound,
            volume = volume.coerceIn(0f, 1f),
            state = ChannelPlayState.BUFFERING
        )
        _channels.update { it + state }
        syncActiveFlags()

        // Yükle ve çal
        voice.setVolume(0f)
        val started = voice.start()
        if (!started) {
            voices[slot]?.release()
            voices[slot] = null
            _channels.update { list -> list.filterNot { it.index == slot } }
            syncActiveFlags()
            return -1
        }

        fadeVoice(slot, 0f, state.effectiveVolume(_masterVol.value))
        updateChannel(slot) { it.copy(state = ChannelPlayState.PLAYING) }
        rememberRecent(sound.id)
        if (_activePresetId.value == null) {
            _activeTitle.value = state.displayLabel
        }
        _isPlaying.value = true

        // Arka planda çalmaya devam edebilmek için ön plan servisi
        ensureForegroundService()

        return slot
    }

    /**
     * Uzak sesi **bloklamadan** ekler.
     *
     * Yuva hemen `BUFFERING` durumuyla rezerve edilir — kullanıcı anında
     * geri bildirim görür ("yükleniyor…"). Arka planda akış hazırlanır;
     * başarılıysa `PLAYING`, değilse yuva boşaltılıp hata gösterilir.
     */
    private fun addChannelAsync(sound: AmbientSound, slot: Int, volume: Float): Int {
        val safeVolume = volume.coerceIn(0f, 1f)

        val placeholder = ChannelState(
            index = slot,
            sound = sound,
            volume = safeVolume,
            state = ChannelPlayState.BUFFERING
        )
        // Rezervasyon: aynı anda ikinci bir dokunuş aynı yuvayı seçmesin
        voices[slot] = BufferingVoice
        _channels.update { it + placeholder }
        syncActiveFlags()

        scope.launch {
            // Adres yoksa (Internet Archive) çalma anında çöz
            val resolved = withContext(Dispatchers.IO) { topUpStreamUrl(sound) }
            val voice = createVoice(resolved)
            if (voice == null) {
                voices[slot] = null
                _channels.update { list ->
                    list.map {
                        if (it.index == slot) it.copy(
                            state = ChannelPlayState.ERROR,
                            errorMessage = "Ses yüklenemedi — kaynak yanıt vermedi"
                        ) else it
                    }
                }
                syncActiveFlags()
                return@launch
            }

            voice.setVolume(0f)
            val started = runCatching { voice.start() }.getOrElse { false }

            if (!started) {
                voice.release()
                voices[slot] = null
                _channels.update { list ->
                    list.map {
                        if (it.index == slot) it.copy(
                            state = ChannelPlayState.ERROR,
                            errorMessage = "Akış açılamadı — internet veya kaynak sorunu"
                        ) else it
                    }
                }
                syncActiveFlags()
                return@launch
            }

            voices[slot] = voice
            fadeVoice(slot, 0f, safeVolume * _masterVol.value)
            updateChannel(slot) { it.copy(state = ChannelPlayState.PLAYING, errorMessage = null) }
            rememberRecent(sound.id)
            if (_activePresetId.value == null) {
                _activeTitle.value = placeholder.displayLabel
            }
            _isPlaying.value = true
            ensureForegroundService()
        }

        return slot
    }

    /**
     * Bir başka sese geçerken öncekini durdurur (tekli çalma).
     * Preset'ler ve "tek ses" butonları bunu kullanır.
     */
    fun playExclusive(sound: AmbientSound, volume: Float = 0.7f) {
        stopAllChannels()
        addChannel(sound, volume)
    }

    /** Bir yuvayı durdurur ve serbest bırakır. */
    fun stopChannel(index: Int) {
        val voice = voices.getOrNull(index) ?: return
        voices[index] = null
        voice.fadeOutAndRelease(FADE_MS)
        _channels.update { list -> list.filterNot { it.index == index } }
        syncActiveFlags()
        if (_channels.value.isEmpty()) {
            _activeTitle.value = null
            _activePresetId.value = null
        } else {
            _activeTitle.value = _channels.value.firstOrNull()?.displayLabel
        }
    }

    /** Tüm kanalları durdurur. */
    fun stopAllChannels() {
        voices.forEachIndexed { i, v ->
            if (v != null) {
                v.fadeOutAndRelease(FADE_MS)
                voices[i] = null
            }
        }
        _channels.value = emptyList()
        _activeTitle.value = null
        _activePresetId.value = null
        _isPlaying.value = false
        presetJobs.forEach { it.cancel() }
        presetJobs.clear()
        runCatching { AmbientPlaybackService.stop(context) }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Ses seviyesi / mute
    // ─────────────────────────────────────────────────────────────────────

    /** Kanalın ses seviyesini ayarlar (yumuşak geçiş). */
    fun setChannelVolume(index: Int, volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        updateChannel(index) { it.copy(volume = v) }
        val state = _channels.value.firstOrNull { it.index == index } ?: return
        fadeVoice(index, -1f, state.effectiveVolume(_masterVol.value))
    }

    /** Kanalı sessize alır/açar. Ses seviyesi hafızada korunur. */
    fun toggleMute(index: Int) {
        updateChannel(index) { it.copy(muted = !it.muted) }
        val state = _channels.value.firstOrNull { it.index == index } ?: return
        fadeVoice(index, -1f, state.effectiveVolume(_masterVol.value))
    }

    /** Ana ses seviyesi — tüm kanalları etkiler. */
    fun setMasterVolume(value: Float) {
        val v = value.coerceIn(0f, 1f)
        if (v == _masterVol.value) return
        _masterVol.value = v
        _channels.value.forEach { state ->
            fadeVoice(state.index, -1f, state.effectiveVolume(v))
        }
    }

    /** Tek kanalı sessize alır/açar (adlandırılmış yardımcı). */
    fun setChannelMuted(index: Int, muted: Boolean) {
        val current = _channels.value.firstOrNull { it.index == index } ?: return
        if (current.muted == muted) return
        updateChannel(index) { it.copy(muted = muted) }
        val state = _channels.value.firstOrNull { it.index == index } ?: return
        fadeVoice(index, -1f, state.effectiveVolume(_masterVol.value))
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Toplu kontrol
    // ─────────────────────────────────────────────────────────────────────

    fun pause() {
        voices.forEach { it?.pause() }
        _isPlaying.value = false
        _channels.update { list -> list.map { it.copy(state = ChannelPlayState.PAUSED) } }
    }

    fun resume() {
        voices.forEach { it?.resume() }
        _isPlaying.value = true
        _channels.update { list -> list.map { it.copy(state = ChannelPlayState.PLAYING) } }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pause() else resume()
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Preset
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Bir atmosfer presetini yükler. Katmanlar gecikmeli olarak havuza eklenir.
     */
    fun playPreset(preset: AtmospherePreset) {
        stopAllChannels()
        _activePresetId.value = preset.id
        _activeTitle.value = "${preset.emoji} ${preset.name}"
        _isPlaying.value = true

        val resolved = AtmospherePresets.resolveLayers(preset)
        if (resolved.isEmpty()) {
            Log.w(TAG, "Preset katmanları çözülemedi: ${preset.id}")
            return
        }

        resolved.forEach { (sound, layer) ->
            val job = scope.launch {
                if (layer.delayMs > 0) delay(layer.delayMs)
                if (_activePresetId.value != preset.id) return@launch
                addChannel(sound, layer.volume)
            }
            presetJobs += job
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Uyku zamanlayıcısı (Chiarella / MixNotice / Metiq ortak özelliği)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Zamanlayıcıyı başlatır.
     *
     * Süre dolunca **aniden kesmez** — son [fadeSeconds] saniye içinde
     * ses seviyesini kademeli olarak sıfıra indirir, sonra tüm kanalları
     * durdurur. Uykuya dalmak üzere olan kullanıcıyı uyandırmamak için.
     *
     * @param minutes Süre (dakika). 0 → zamanlayıcıyı iptal eder.
     */
    fun startSleepTimer(minutes: Int, fadeSeconds: Int = SleepTimerState.DEFAULT_FADE_SECONDS) {
        cancelSleepTimer()
        if (minutes <= 0) return

        val totalMs = minutes * 60_000L
        val fade = fadeSeconds.coerceIn(0, 60)
        sleepRemainingMs = totalMs
        _sleepTimer.value = SleepTimerState(
            totalMs = totalMs,
            remainingMs = totalMs,
            fadeSeconds = fade,
            running = true
        )

        sleepJob = scope.launch {
            val tick = 1_000L
            while (sleepRemainingMs > 0) {
                delay(tick)
                // Kullanıcı duraklattıysa bekle
                while (_sleepTimer.value.paused) delay(250L)
                sleepRemainingMs -= tick
                _sleepTimer.update { it.copy(remainingMs = sleepRemainingMs.coerceAtLeast(0L)) }
            }
            // Süre doldu → kısarak sustur (aniden kesme)
            if (fade > 0) fadeAllOut(fade * 1000L) else stopAllChannels()
            _sleepTimer.value = SleepTimerState()
        }
    }

    fun pauseSleepTimer() {
        val cur = _sleepTimer.value
        if (!cur.running || cur.paused) return
        _sleepTimer.update { it.copy(paused = true) }
    }

    fun resumeSleepTimer() {
        val cur = _sleepTimer.value
        if (!cur.running || !cur.paused) return
        _sleepTimer.update { it.copy(paused = false) }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        sleepRemainingMs = 0L
        _sleepTimer.value = SleepTimerState()
    }

    /** Kalan süreyi değiştirir (kullanıcı "+10 dk" derse). */
    fun extendSleepTimer(extraMinutes: Int) {
        val cur = _sleepTimer.value
        if (!cur.running) return
        val delta = extraMinutes * 60_000L
        // Gerçek sayacı da güncelle — yoksa döngü eski değeri okumaya devam eder
        sleepRemainingMs = (sleepRemainingMs + delta).coerceAtLeast(1_000L)
        _sleepTimer.update {
            it.copy(
                totalMs = (it.totalMs + delta).coerceAtLeast(60_000L),
                remainingMs = sleepRemainingMs
            )
        }
    }

    /**
     * Tüm kanalları [durationMs] boyunca kısarak susturur.
     * Zamanlayıcı dolduğunda kullanılır.
     */
    private fun fadeAllOut(durationMs: Long) {
        val states = _channels.value
        if (states.isEmpty()) return
        val steps = (durationMs / FADE_STEP_MS).coerceAtLeast(1).toInt()
        scope.launch {
            val starts = states.associate { it.index to (voices.getOrNull(it.index)?.currentVolume() ?: 0f) }
            repeat(steps) { step ->
                val factor = 1f - (step + 1).toFloat() / steps.toFloat()
                starts.forEach { (index, start) ->
                    voices.getOrNull(index)?.setVolume((start * factor).coerceIn(0f, 1f))
                }
                delay(FADE_STEP_MS)
            }
            stopAllChannels()
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Favoriler / son çalınanlar
    // ─────────────────────────────────────────────────────────────────────

    fun toggleFavorite(soundId: String) {
        _favorites.update { set ->
            if (soundId in set) set - soundId else set + soundId
        }
    }

    fun isFavorite(soundId: String): Boolean = soundId in _favorites.value

    fun setFavorites(ids: Set<String>) {
        _favorites.value = ids
    }

    private fun rememberRecent(soundId: String) {
        _recentIds.update { list ->
            (listOf(soundId) + list.filterNot { it == soundId }).take(20)
        }
    }

    /** Son çalınanları [AmbientSound] olarak döner (katalogdan çözülür). */
    fun recentSounds(): List<AmbientSound> =
        _recentIds.value.mapNotNull { id -> ProceduralSoundCatalog.byId(id) }

    fun clearRecent() {
        _recentIds.value = emptyList()
    }

    /** Depodan geri yüklemek için (hidrasyon). */
    fun setRecentIds(ids: List<String>) {
        _recentIds.value = ids.take(20)
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Karışım profilleri
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Şu anki kanal düzenini profil olarak kaydeder.
     *
     * **Uzak sesler de kaydedilir:** profilde `soundId` yanında `streamUrl`
     * ve `sourceId` saklanır. Aksi hâlde Jamendo/Audius gibi akışlar
     * kaydedilir ama geri yüklenemezdi — panel ise kaynağı ayırt etmez.
     */
    fun saveCurrentAsProfile(name: String, genreTag: String = ""): MixProfile? {
        val states = _channels.value
        if (states.isEmpty()) return null

        val profile = MixProfile(
            id = "mix_${System.currentTimeMillis()}",
            name = name.ifBlank { "Karışım ${_profiles.value.size + 1}" },
            createdAt = System.currentTimeMillis(),
            channels = states.map {
                MixChannel(
                    soundId = it.sound.id,
                    title = it.sound.title,
                    volume = it.volume,
                    muted = it.muted,
                    streamUrl = it.sound.streamUrl,
                    sourceKey = it.sound.sourceId.storageKey
                )
            },
            genreTag = genreTag
        )
        _profiles.update { list -> list + profile }
        return profile
    }

    fun deleteProfile(id: String) {
        _profiles.update { list -> list.filterNot { it.id == id } }
    }

    fun renameProfile(id: String, newName: String) {
        _profiles.update { list ->
            list.map { if (it.id == id) it.copy(name = newName) else it }
        }
    }

    fun setProfiles(list: List<MixProfile>) {
        _profiles.value = list
    }

    /**
     * Profili yükler: tüm kanalları durdurup profildeki düzeni kurar.
     * Katalogda bulunamayan sesler sessizce atlanır.
     */
    fun applyProfile(profile: MixProfile) {
        stopAllChannels()
        var restored = 0
        profile.channels.forEach { mc ->
            val sound = resolveProfileSound(mc) ?: return@forEach
            val idx = addChannel(sound, mc.volume)
            if (idx >= 0) {
                restored++
                if (mc.muted) setChannelMuted(idx, true)
            }
        }
        _activeTitle.value = profile.name
        _isPlaying.value = restored > 0 && _channels.value.isNotEmpty()
        if (restored == 0) {
            Log.w(TAG, "Profil geri yüklenemedi (sesler bulunamadı): ${profile.name}")
        }
    }

    /**
     * Profildeki kanalı bir [AmbientSound]'a çözer.
     *
     * Sıra: 1) yerleşik katalog, 2) kaydedilmiş akış adresi.
     * Akış adresi yoksa ve katalogda da yoksa atlanır.
     */
    private fun resolveProfileSound(mc: MixChannel): AmbientSound? {
        ProceduralSoundCatalog.byId(mc.soundId)?.let { return it }
        val url = mc.streamUrl ?: return null
        if (url.isBlank()) return null

        val sourceId = MusicSourceId.fromStorageKey(mc.sourceKey) ?: MusicSourceId.BUILTIN
        return AmbientSound(
            id = mc.soundId,
            title = mc.title.ifBlank { "Kayıtlı ses" },
            subtitle = "Profilden geri yüklendi",
            origin = AmbientOrigin.REMOTE,
            streamUrl = url,
            sourceId = sourceId,
            varietyKey = "profile_restore"
        )
    }

    /** Profili dışa aktarma metnine çevirir (paylaşılabilir). */
    fun exportProfile(profile: MixProfile): String {
        val payload = MixCodecPayload(
            name = profile.name,
            channels = profile.channels
        )
        return org.json.JSONObject().apply {
            put("version", payload.version)
            put("name", payload.name)
            put("channels", org.json.JSONArray().apply {
                payload.channels.forEach { ch ->
                    put(org.json.JSONObject().apply {
                        put("id", ch.soundId)
                        put("title", ch.title)
                        put("volume", ch.volume.toDouble())
                        put("muted", ch.muted)
                        ch.streamUrl?.let { put("streamUrl", it) }
                        if (ch.sourceKey.isNotBlank()) put("sourceKey", ch.sourceKey)
                    })
                }
            })
        }.toString()
    }

    /** Dışa aktarılmış metni profle çevirir. Geçersizse `null`. */
    fun importProfile(json: String): MixProfile? = runCatching {
        val root = org.json.JSONObject(json)
        val name = root.optString("name", "İçe aktarılan")
        val arr = root.optJSONArray("channels") ?: return@runCatching null
        val channels = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            MixChannel(
                soundId = o.optString("id"),
                title = o.optString("title"),
                volume = o.optDouble("volume", 0.6).toFloat(),
                muted = o.optBoolean("muted", false),
                streamUrl = o.optString("streamUrl").takeIf { it.isNotBlank() },
                sourceKey = o.optString("sourceKey", "")
            )
        }
        if (channels.isEmpty()) null
        else MixProfile(
            id = "mix_${System.currentTimeMillis()}",
            name = name,
            createdAt = System.currentTimeMillis(),
            channels = channels
        )
    }.getOrNull()

    // ─────────────────────────────────────────────────────────────────────
    //  Serbest bırakma
    // ─────────────────────────────────────────────────────────────────────

    fun release() {
        cancelSleepTimer()
        voices.forEachIndexed { i, v ->
            v?.release()
            voices[i] = null
        }
        _channels.value = emptyList()
        _isPlaying.value = false
        _activeTitle.value = null
        _activePresetId.value = null
        scope.cancel()
    }

    // ═════════════════════════════════════════════════════════════════════
    //  DAHİLİ
    // ═════════════════════════════════════════════════════════════════════

    /** Ses çalmaya başlayınca ön plan servisini devreye alır. */
    private fun ensureForegroundService() {
        if (!continueInBackground) return
        runCatching { AmbientPlaybackService.start(context) }
            .onFailure { Log.w(TAG, "Ön plan servisi başlatılamadı", it) }
    }

    private fun updateChannel(index: Int, transform: (ChannelState) -> ChannelState) {
        _channels.update { list ->
            list.map { if (it.index == index) transform(it) else it }
        }
    }

    private fun syncActiveFlags() {
        _isPlaying.value = _channels.value.any { it.state == ChannelPlayState.PLAYING }
    }

    /**
     * Ses tanımına göre uygun arka ucu seçer.
     *
     * Prosedürel → [ProceduralVoice] (AudioTrack)
     * Uzak        → [StreamVoice] (ExoPlayer)
     */
    /**
     * Toplaması gereken akış adresini çözer.
     *
     * Internet Archive arama API'si yalnızca öğe kimliği döndürür;
     * oynatılabilir dosya adı `/metadata/<id>` çağrısından gelir. Bu
     * yüzden `streamUrl` boşsa [MusicCatalogRepository.resolveUrl] ile
     * çözülür.
     *
     * **Ağ çağrısı yapar — her zaman `Dispatchers.IO` üzerinde çağır.**
     *
     * @return Adresi doldurulmuş ses; çözülemezse orijinali
     */
    private suspend fun topUpStreamUrl(sound: AmbientSound): AmbientSound {
        if (!sound.streamUrl.isNullOrBlank()) return sound
        if (sound.origin == AmbientOrigin.PROCEDURAL) return sound
        if (downloadManager.localFile(sound.id) != null) return sound

        val resolved = runCatching {
            com.kitsugi.animelist.data.music.MusicCatalogRepository.get().resolveUrl(sound)
        }.getOrNull()

        return if (resolved.isNullOrBlank()) {
            Log.w(TAG, "Akış adresi çözülemedi: ${sound.id}")
            sound
        } else {
            sound.copy(streamUrl = resolved)
        }
    }

    private fun createVoice(sound: AmbientSound): ChannelVoice? = when {
        sound.origin == AmbientOrigin.PROCEDURAL -> ProceduralVoice(context, sound)
        else -> {
            // ÇEVRİMDIŞI ÖNCELİĞİ: indirilmiş dosya varsa ağa hiç çıkma.
            // Bu kontrol olmadan çevrimdışı çalma çalışmazdı — indirilen
            // ses yine de internetten çalmaya çalışırdı.
            val downloaded = downloadManager.localFile(sound.id)
            val url = downloaded?.toURI()?.toString()
                ?: sound.streamUrl?.takeIf { it.isNotBlank() }

            // `url == null` artık ölümcül DEĞİL: Internet Archive gibi
            // kaynaklarda adres yalnızca `/metadata/<id>` çağrısıyla
            // çözülür. [topUpStreamUrl] çözümlemeyi arka planda yapar.
            StreamVoice(context, sound, url)
        }
    }

    /** Bir kanalın ses seviyesini kademeli değiştirir. */
    private fun fadeVoice(index: Int, from: Float, to: Float) {
        val voice = voices.getOrNull(index) ?: return
        scope.launch {
            val start = if (from < 0f) voice.currentVolume() else from
            val steps = (FADE_MS / FADE_STEP_MS).coerceAtLeast(1).toInt()
            val delta = (to - start) / steps
            var current = start
            repeat(steps) {
                current += delta
                voice.setVolume(current.coerceIn(0f, 1f))
                delay(FADE_STEP_MS)
            }
            voice.setVolume(to.coerceIn(0f, 1f))
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════
//  KANAL SESİ SOYUTLAMASI
// ═════════════════════════════════════════════════════════════════════════

/**
 * Bir kanalın ses arka ucu. Motor bu arayüz üzerinden konuşur — altta
 * `AudioTrack` mi `ExoPlayer` mı olduğunu bilmez.
 */
internal interface ChannelVoice {
    /** Çalmaya başlar. Başarılıysa `true`. */
    fun start(): Boolean
    fun pause()
    fun resume()
    /** Anlık ses seviyesi (fade başlangıcı için). */
    fun currentVolume(): Float
    fun setVolume(value: Float)
    /** Yumuşak kapanış + kaynak serbest bırakma. */
    fun fadeOutAndRelease(durationMs: Long)
    /** Anında serbest bırakma. */
    fun release()
}

/**
 * Yuva rezervasyonu için yer tutucu.
 *
 * Kanal hazırlanırken `voices[slot]` buraya işaret eder; gerçek ses
 * gelince üzerine yazılır. Böylece aynı yuvaya iki kanal atanmaz.
 * Hiçbir metodu gerçek iş yapmaz.
 */
private object BufferingVoice : ChannelVoice {
    override fun start(): Boolean = false
    override fun pause() = Unit
    override fun resume() = Unit
    override fun currentVolume(): Float = 0f
    override fun setVolume(value: Float) = Unit
    override fun fadeOutAndRelease(durationMs: Long) = Unit
    override fun release() = Unit
}

/**
 * Prosedürel ses kanalı — `AudioTrack` üzerinden gerçek zamanlı PCM.
 */
internal class ProceduralVoice(
    private val context: Context,
    private val sound: AmbientSound
) : ChannelVoice {

    private val player = ProceduralSoundPlayer(context, sound.id)
    @Volatile
    private var volume = 0f

    override fun start(): Boolean {
        return runCatching {
            player.start()
        }.getOrElse {
            Log.w("ProceduralVoice", "Başlatılamadı: ${sound.id}", it)
            false
        }
    }

    override fun pause() = player.pause()
    override fun resume() { player.resume() }
    override fun currentVolume(): Float = volume

    override fun setVolume(value: Float) {
        volume = value
        player.setVolume(value)
    }

    override fun fadeOutAndRelease(durationMs: Long) {
        player.fadeOutAndStop(durationMs)
    }

    override fun release() = player.stop()
}

/**
 * Uzak akış kanalı — Media3 `ExoPlayer`.
 *
 * Ses tanımında `streamUrl` varsa doğrudan kullanılır. Internet Archive
 * gibi dosya adının çözülmesi gereken kaynaklarda çağıran taraf önce
 * `MusicCatalogClient.resolveArchiveTrackUrl()` ile adresi almalıdır.
 */
@UnstableApi
internal class StreamVoice(
    context: Context,
    private val sound: AmbientSound,
    url: String?
) : ChannelVoice {

    /**
     * Oynatılacak adres.
     *
     * `null` olabilir: Internet Archive arama API'si yalnızca öğe kimliği
     * döndürür, dosya adı `/metadata/<id>` ile çözülür. Adres
     * [AmbientAudioEngine.topUpStreamUrl] tarafından `prepare()` öncesi
     * doldurulur.
     */
    @Volatile
    var sourceUrl: String? = url
        internal set

    private val player: ExoPlayer

    @Volatile
    private var volume = 0f

    @Volatile
    private var playing = false

    init {
        val dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(context)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            // true → kulaklık çıkarılınca otomatik duraklat
            .setAudioAttributes(audioAttributes, true)
            .build()

        player.repeatMode = Player.REPEAT_MODE_ONE
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.w("StreamVoice", "Akış hatası: ${sound.id} — ${error.errorCodeName}", error)
            }
        })
    }

    /**
     * Akışı hazırlar ve bekler.
     *
     * **Neden bekliyoruz:** `prepare()` asenkron. Eskiden hemen `true`
     * dönüyordu, dolayısıyla HTTP 404 / ağ hatası olsa bile kanal
     * "çalıyor" görünüyor ve kullanıcı sessiz bir kanal görüyordu.
     * Artık en fazla 4 saniye hazır olmayı bekliyoruz; olmazsa kanal
     * oluşturulmuyor ve hata kullanıcıya gösteriliyor.
     */
    override fun start(): Boolean {
        val target = sourceUrl
        if (target.isNullOrBlank()) {
            Log.w("StreamVoice", "Adres çözülemedi: ${sound.id}")
            return false
        }
        val ok = runCatching {
            player.setMediaItem(MediaItem.fromUri(target))
            player.volume = 0f
            player.prepare()
            true
        }.getOrElse {
            Log.w("StreamVoice", "Hazırlanamadı: $target", it)
            false
        }
        if (!ok) return false

        // Hazır olana kadar bekle (en çok 4 sn)
        val deadline = System.currentTimeMillis() + 4_000L
        while (System.currentTimeMillis() < deadline) {
            val state = player.playbackState
            if (state == Player.STATE_READY) {
                player.playWhenReady = true
                player.play()
                playing = true
                return true
            }
            if (state == Player.STATE_ENDED || player.playerError != null) {
                Log.w("StreamVoice", "Akış hatası: $target — ${player.playerError?.errorCodeName}")
                return false
            }
            // Yerel dosyalar neredeyse anında hazır olur; 20 ms yeterli
            runCatching { Thread.sleep(20L) }
        }

        Log.w("StreamVoice", "Zaman aşımı: akış 4 sn içinde hazır olmadı — $target")
        return false
    }

    override fun pause() {
        player.pause()
        playing = false
    }

    override fun resume() {
        player.play()
        playing = true
    }

    override fun currentVolume(): Float = volume

    override fun setVolume(value: Float) {
        volume = value
        runCatching { player.volume = value }
    }

    /**
     * Yumuşak kapanış + serbest bırakma.
     *
     * **Neden fade şart:** anında `stop()` çağrısı hoparlörde "pat" sesi
     * üretir. Uyku zamanlayıcısı gece yarısı kanalları kapatırken bu
     * duyulursa kullanıcıyı uyandırır — özelliğin amacına aykırı.
     *
     * Fade adımları ana thread'i bloklamamak için `Thread` üzerinde;
     * süre genelde 900 ms, en kötü 12 sn (uyku fade'i) olur.
     */
    override fun fadeOutAndRelease(durationMs: Long) {
        if (durationMs <= 0L) {
            release()
            return
        }
        val startVolume = volume
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val steps = (durationMs / 40L).coerceAtLeast(1L)
        var i = 0L
        val runnable = object : Runnable {
            override fun run() {
                if (i >= steps) {
                    release()
                    return
                }
                i++
                val factor = 1f - i.toFloat() / steps.toFloat()
                runCatching { player.volume = (startVolume * factor).coerceIn(0f, 1f) }
                mainHandler.postDelayed(this, 40L)
            }
        }
        mainHandler.post(runnable)
    }

    override fun release() {
        val doRelease = {
            runCatching {
                player.stop()
                player.release()
            }
            playing = false
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            doRelease()
        } else {
            android.os.Handler(android.os.Looper.getMainLooper()).post { doRelease() }
        }
    }
}
