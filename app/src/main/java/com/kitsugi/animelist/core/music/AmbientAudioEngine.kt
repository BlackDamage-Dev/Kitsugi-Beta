package com.kitsugi.animelist.core.music

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.kitsugi.animelist.data.music.AmbientOrigin
import com.kitsugi.animelist.data.music.AmbientSound
import com.kitsugi.animelist.data.music.AtmospherePreset
import com.kitsugi.animelist.data.music.AtmospherePresets
import com.kitsugi.animelist.data.music.ChannelPlayState
import com.kitsugi.animelist.data.music.ChannelState
import com.kitsugi.animelist.data.music.MAX_AMBIENT_CHANNELS
import com.kitsugi.animelist.core.network.KitsugiHttpClient
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

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
         * Aynı anda çalabilecek **çevrimiçi** kanal sayısı.
         *
         * Prosedürel sesler cihazda üretildiği için sınır yoktur (yuva
         * sayısına kadar); ağ tabanlı akışlar ise bant genişliği ve
         * çözücü kaynağı tüketir. 6 uzak akış aynı anda açıldığında zayıf
         * cihazlarda ses kesiliyor ve arayüz donuyordu.
         */
        const val MAX_REMOTE_CHANNELS = 3

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

    /**
     * Yuva **nesil sayacı**.
     *
     * Bir yuva için başlatılan asenkron hazırlık (ağ çözümlemesi + `start()`)
     * tamamlandığında, yuva bu arada kullanıcı tarafından boşaltılmış veya
     * başka bir sesle yeniden rezerve edilmiş olabilir. Eskiden bu kontrol
     * yoktu: geç biten hazırlık, artık başka bir sese ait olan yuvaya kendi
     * sesini yazıyor ve **hiçbir zaman serbest bırakılmayan** bir çalar
     * bırakıyordu (asıl semptom: "durdurduğum ses çalmaya devam ediyor,
     * sonra hiçbir ses çalmıyor").
     *
     * Her rezervasyon sayacı artırır; hazırlık bitince sayaç değişmişse
     * çalar derhal serbest bırakılır.
     */
    private val slotGeneration = IntArray(MAX_AMBIENT_CHANNELS)

    /**
     * Kanal eklenemediğinde panelin göstereceği mesaj.
     *
     * Sessiz `-1` dönüşü kullanıcıya "dokundum ama hiçbir şey olmadı"
     * hissi veriyordu. Panel bu akışı dinleyip snackbar gösterir.
     */
    private val _lastRejection = MutableStateFlow<String?>(null)
    val lastRejection: StateFlow<String?> = _lastRejection.asStateFlow()

    fun clearRejection() {
        _lastRejection.value = null
    }

    /** Yuva → süren fade işi. Aynı kanala yeni fade başlarken eskisi iptal edilir. */
    private val fadeJobs = arrayOfNulls<Job>(MAX_AMBIENT_CHANNELS)

    // ─────────────────────────────────────────────────────────────────────
    //  Ses odağı (AudioFocus)
    // ─────────────────────────────────────────────────────────────────────

    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    /**
     * Uygulama **tek** ses odağı ister; her kanal ayrı ayrı istemez.
     *
     * Eskiden her `ExoPlayer` `setAudioAttributes(..., handleAudioFocus = true)`
     * ile kuruluyordu. Altı kanal aynı anda çalarken her yeni kanal odak
     * isteyip diğerlerini `LOSS_TRANSIENT`'a düşürüyor, kanallar birbirini
     * duraklatıp duruyordu (semptom: preset açılınca sesler birbirine
     * giriyor / "zortluyor" ve sonra hiç ses gelmiyor).
     */
    @Volatile
    private var focusGranted = false

    /** Odak geçici kaybı yüzünden mi duraklattık? (Kazanınca devam edelim.) */
    @Volatile
    private var pausedByFocusLoss = false

    private var focusRequest: AudioFocusRequest? = null

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                focusGranted = false
                pausedByFocusLoss = false
                runCatching { pause() }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                focusGranted = false
                if (_isPlaying.value) {
                    pausedByFocusLoss = true
                    runCatching { pause() }
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Kısa bildirim/konuşma: duraklatmak yerine kıs.
                _channels.value.forEach { state ->
                    voices.getOrNull(state.index)?.setVolume(
                        (state.effectiveVolume(_masterVol.value) * 0.25f).coerceIn(0f, 1f)
                    )
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                focusGranted = true
                if (pausedByFocusLoss) {
                    pausedByFocusLoss = false
                    runCatching { resume() }
                } else {
                    // Kısılmış kanalları eski seviyesine döndür.
                    _channels.value.forEach { state ->
                        voices.getOrNull(state.index)
                            ?.setVolume(state.effectiveVolume(_masterVol.value))
                    }
                }
            }
        }
    }

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

    /** Ön plan servisini biz mi başlattık? (Gereksiz start/stop churn'ünü önler.) */
    @Volatile
    private var serviceStarted = false

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
     * ⚠️ **Ana thread'i bloklamaz.** Yuva anında `BUFFERING` olarak
     * rezerve edilir; adres çözümlemesi, `AudioTrack`/`ExoPlayer` kurulumu
     * ve `start()` arka planda yapılır. Eskiden prosedürel sesler ana
     * thread'de başlatılıyor, uzak sesler ise `StreamVoice.start()` içinde
     * `Thread.sleep` ile **ana thread'de 4 saniye** bekletiliyordu —
     * çevrimiçi bir parçaya dokunulduğunda uygulamanın donmasının nedeni
     * buydu.
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

        // 🌐 Uzak akış sınırı: 6 kanalın hepsi çevrimiçi parça olursa
        // cihazda 6 ExoPlayer + 6 HTTP bağlantısı aynı anda çalışır. Zayıf
        // cihazlarda ve yavaş ağda bu, sesin kesilmesine (underrun) ve
        // arayüzde donmaya yol açıyordu. En fazla MAX_REMOTE_CHANNELS
        // çevrimiçi kanal; kalanlar prosedürel (cihaz içi, sıfır ağ).
        if (sound.origin == AmbientOrigin.REMOTE) {
            val remoteCount = _channels.value.count {
                it.sound.origin == AmbientOrigin.REMOTE && it.state != ChannelPlayState.ERROR
            }
            if (remoteCount >= MAX_REMOTE_CHANNELS) {
                _lastRejection.value =
                    "Aynı anda en fazla $MAX_REMOTE_CHANNELS çevrimiçi ses çalabilir. " +
                        "Önce birini kaldır ya da cihaz içi ses ekle."
                Log.w(TAG, "Uzak kanal sınırı dolu ($MAX_REMOTE_CHANNELS)")
                return -1
            }
        }

        // Boş yuva bul
        val slot = voices.indexOfFirst { it == null }
        if (slot < 0) {
            _lastRejection.value = "En fazla $MAX_AMBIENT_CHANNELS ses aynı anda çalabilir."
            Log.w(TAG, "Kanal sınırı dolu ($MAX_AMBIENT_CHANNELS)")
            return -1
        }

        return reserveChannel(sound, slot, volume)
    }

    /**
     * Yuvayı rezerve eder ve hazırlığı arka planda başlatır.
     *
     * @return Rezerve edilen yuva (hazırlık başarısız olursa kanal
     *         `ERROR` durumunda kalır ve panelden kaldırılabilir).
     */
    private fun reserveChannel(sound: AmbientSound, slot: Int, volume: Float): Int {
        val safeVolume = volume.coerceIn(0f, 1f)
        val generation = ++slotGeneration[slot]

        voices[slot] = BufferingVoice
        _channels.update { list ->
            list.filterNot { it.index == slot } + ChannelState(
                index = slot,
                sound = sound,
                volume = safeVolume,
                state = ChannelPlayState.BUFFERING
            )
        }
        syncActiveFlags()

        scope.launch { prepareAndStart(slot, generation, sound, safeVolume) }
        return slot
    }

    /**
     * Kanalı hazırlar ve çalar. **Her adım iptal edilebilir:** yuva bu
     * arada boşaltıldıysa (kullanıcı sesi kaldırdı) hazırlık sonucu
     * çöpe atılır ve çalar serbest bırakılır.
     */
    private suspend fun prepareAndStart(
        slot: Int,
        generation: Int,
        sound: AmbientSound,
        volume: Float
    ) {
        // Adres yoksa (Internet Archive) çalma anında çöz — ağ işi IO'da.
        val resolved = withContext(Dispatchers.IO) {
            runCatching { topUpStreamUrl(sound) }.getOrDefault(sound)
        }
        if (slotGeneration[slot] != generation) return

        val voice = createVoice(resolved)
        if (voice == null) {
            failSlot(slot, generation, "Ses yüklenemedi — kaynak yanıt vermedi")
            return
        }

        voice.setVolume(0f)
        // `start()` suspend: uzak akışta hazır olmayı bekleme (bloklamadan),
        // prosedürel seste AudioTrack kurulumu IO/Default üzerinde yapılır.
        val started = runCatching { voice.start() }.getOrElse { error ->
            Log.w(TAG, "Kanal başlatılamadı: ${sound.id}", error)
            false
        }

        // Bu arada yuva başkasına verildiyse → kendi çalarımızı kapat.
        if (slotGeneration[slot] != generation) {
            runCatching { voice.release() }
            return
        }

        if (!started) {
            val hint = runCatching { voice.errorHint() }.getOrNull()
            runCatching { voice.release() }
            failSlot(
                slot,
                generation,
                hint ?: "Akış açılamadı — internet veya kaynak sorunu"
            )
            return
        }

        voices[slot] = voice
        ensureAudioFocus()

        fadeVoice(slot, 0f, volume * _masterVol.value)
        updateChannel(slot) { it.copy(state = ChannelPlayState.PLAYING, errorMessage = null) }
        rememberRecent(sound.id)
        if (_activePresetId.value == null) {
            _activeTitle.value = _channels.value.firstOrNull { it.index == slot }?.displayLabel
        }
        _isPlaying.value = true
        ensureForegroundService()
    }

    /** Hazırlık başarısız olduğunda yuvayı `ERROR` durumuna düşürür. */
    private fun failSlot(slot: Int, generation: Int, message: String) {
        if (slotGeneration[slot] != generation) return
        voices[slot] = null
        updateChannel(slot) { it.copy(state = ChannelPlayState.ERROR, errorMessage = message) }
        syncActiveFlags()
    }

    /**
     * Bir başka sese geçerken öncekini durdurur (tekli çalma).
     * Preset'ler ve "tek ses" butonları bunu kullanır.
     */
    fun playExclusive(sound: AmbientSound, volume: Float = 0.7f) {
        stopAllChannels()
        addChannel(sound, volume)
    }

    /**
     * Bir yuvayı durdurur ve serbest bırakır.
     *
     * Hata durumunda kalmış (çaları olmayan) bir kanal da bu çağrıyla
     * listeden düşer — eskiden `voice == null` erken dönüşü yüzünden
     * "yüklenemedi" kartları panelden **hiç** kaldırılamıyordu.
     */
    fun stopChannel(index: Int) {
        if (index !in 0 until MAX_AMBIENT_CHANNELS) return

        // Bekleyen hazırlık varsa iptal et (geç biten hazırlık yuvaya yazmasın).
        slotGeneration[index]++
        fadeJobs[index]?.cancel()
        fadeJobs[index] = null

        val voice = voices.getOrNull(index)
        voices[index] = null
        runCatching { voice?.fadeOutAndRelease(FADE_MS) }

        _channels.update { list -> list.filterNot { it.index == index } }
        syncActiveFlags()

        if (_channels.value.isEmpty()) {
            _activeTitle.value = null
            _activePresetId.value = null
            _isPlaying.value = false
            abandonAudioFocus()
            stopForegroundService()
        } else {
            _activeTitle.value = _channels.value.firstOrNull()?.displayLabel
        }
    }

    /**
     * `ERROR` durumundaki bir kanalı **aynı seste** yeniden dener.
     *
     * Kanal kaldırılmaz: yuva korunur, dolayısıyla karışımdaki yer ve ses
     * seviyesi değişmez. Yalnızca hazırlık yeniden başlatılır.
     */
    fun retryChannel(index: Int) {
        if (index !in 0 until MAX_AMBIENT_CHANNELS) return
        val channel = _channels.value.firstOrNull { it.index == index } ?: return
        if (channel.state != ChannelPlayState.ERROR) return

        Log.i(TAG, "Kanal yeniden deneniyor: ${channel.sound.title}")
        slotGeneration[index]++
        voices[index] = null
        _lastRejection.value = null
        reserveChannel(channel.sound, index, channel.volume)
    }

    /**
     * Tüm kanalları durdurur.
     *
     * @param fromService `true` ise çağrı [AmbientPlaybackService]'in kendi
     *        `ACTION_STOP` komutundan geliyordur; bu durumda servise tekrar
     *        `stop` gönderilmez.
     *
     * ⚠️ **Eskiden sonsuz döngü vardı:** `stopAllChannels()` servise
     * `ACTION_STOP` yolluyor, servis `onStartCommand` içinde yeniden
     * `engine.stopAllChannels()` çağırıyor, o da tekrar servise `ACTION_STOP`
     * yolluyordu. Servis her turda yeniden başlatılıp `startForeground`
     * yapılıyordu → ana thread sürekli meşgul, uygulama kilitleniyor ve
     * bir daha ses çalmıyordu. `fromService` bu zinciri keser.
     */
    fun stopAllChannels(fromService: Boolean = false) {
        stopAllVoices()
        pausedByFocusLoss = false
        abandonAudioFocus()
        if (!fromService) stopForegroundService()
    }

    /**
     * Çalan tüm sesleri susturur **ama** ön plan servisini ve ses odağını
     * kapatmaz.
     *
     * `playPreset` bunu kullanır: preset değiştirirken servisi durdurup
     * hemen yeniden başlatmak (her katman için ayrı `startForegroundService`)
     * hem gereksiz hem de Android 12+ arka plan kısıtlarında hataya açıktı.
     */
    private fun stopAllVoices() {
        voices.forEachIndexed { i, v ->
            slotGeneration[i]++
            fadeJobs[i]?.cancel()
            fadeJobs[i] = null
            if (v != null) {
                runCatching { v.fadeOutAndRelease(FADE_MS) }
                voices[i] = null
            }
        }
        _channels.value = emptyList()
        _activeTitle.value = null
        _activePresetId.value = null
        _isPlaying.value = false
        presetJobs.forEach { it.cancel() }
        presetJobs.clear()
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
        fadeJobs.forEachIndexed { i, job ->
            job?.cancel()
            fadeJobs[i] = null
        }
        voices.forEach { it?.pause() }
        _isPlaying.value = false
        _channels.update { list -> list.map { it.copy(state = ChannelPlayState.PAUSED) } }
    }

    fun resume() {
        if (_channels.value.isEmpty()) return
        ensureAudioFocus()
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
        // Ses odağı ve ön plan servisi korunur (bkz. stopAllVoices).
        stopAllVoices()
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
            slotGeneration[i]++
            fadeJobs[i]?.cancel()
            fadeJobs[i] = null
            runCatching { v?.release() }
            voices[i] = null
        }
        _channels.value = emptyList()
        _isPlaying.value = false
        _activeTitle.value = null
        _activePresetId.value = null
        abandonAudioFocus()
        scope.cancel()
    }

    // ═════════════════════════════════════════════════════════════════════
    //  DAHİLİ
    // ═════════════════════════════════════════════════════════════════════

    /** Ses çalmaya başlayınca ön plan servisini devreye alır. */
    private fun ensureForegroundService() {
        if (!continueInBackground) return
        runCatching { AmbientPlaybackService.start(context) }
            .onSuccess { serviceStarted = true }
            .onFailure { Log.w(TAG, "Ön plan servisi başlatılamadı", it) }
    }

    /**
     * Ön plan servisini durdurur.
     *
     * Yalnızca gerçekten kanal varken (yani servis ayakta olabilecekken)
     * çağrılır. Servis `ACTION_STOP` aldığında kendisi de bu metodu
     * tetikleyebileceği için [stopAllChannels] `fromService = true` ile
     * korunur — aksi hâlde servis ↔ motor arasında sonsuz döngü oluşur.
     */
    private fun stopForegroundService() {
        if (!serviceStarted) return
        serviceStarted = false
        runCatching { AmbientPlaybackService.stop(context) }
            .onFailure { Log.w(TAG, "Ön plan servisi durdurulamadı", it) }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Ses odağı yardımcıları
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Ses odağını **bir kez** ister (kanal başına değil).
     *
     * Odak alınamazsa (ör. başka bir uygulama konuşuyor) ses yine çalar;
     * Android bu durumda kısılmış çalmaya izin verir. Sessizce hiç
     * çalmamak daha kötü bir davranış olurdu.
     */
    private fun ensureAudioFocus() {
        val manager = audioManager ?: return
        if (focusGranted) return
        focusGranted = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .build()
                    .also { focusRequest = it }
                manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                manager.requestAudioFocus(
                    focusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        }.getOrDefault(false)
    }

    /** Ses odağını bırakır (başka uygulamaların sesi kısılmasın). */
    private fun abandonAudioFocus() {
        if (!focusGranted && focusRequest == null) return
        focusGranted = false
        val manager = audioManager
        runCatching {
            if (manager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    focusRequest?.let { manager.abandonAudioFocusRequest(it) }
                } else {
                    @Suppress("DEPRECATION")
                    manager.abandonAudioFocus(focusChangeListener)
                }
            }
        }
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
        sound.origin == AmbientOrigin.PROCEDURAL -> ProceduralVoice(
            context = context,
            sound = sound,
            // AudioTrack kendiliğinden ölürse kanal "çalıyor" görünmesin:
            // yuvayı boşalt, durumu ERROR'a çek (kullanıcı tekrar deneyebilsin).
            onFailed = {
                val slot = _channels.value.firstOrNull { it.sound.id == sound.id }?.index
                if (slot != null) {
                    voices[slot] = null
                    updateChannel(slot) {
                        it.copy(
                            state = ChannelPlayState.ERROR,
                            errorMessage = "Ses motoru durdu — tekrar dene"
                        )
                    }
                    syncActiveFlags()
                }
            }
        )
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

    /**
     * Bir kanalın ses seviyesini kademeli değiştirir.
     *
     * ⚠️ Aynı yuvada **tek** fade çalışır. Eskiden ana ses kaydırıcısı her
     * hareketinde yeni bir fade başlatılıyor, onlarca eşzamanlı fade aynı
     * çaların sesini ileri geri çekiyordu — duyulan şey "zortlama" ve
     * tıklama sesiydi. Önceki fade iptal edilir.
     */
    private fun fadeVoice(index: Int, from: Float, to: Float) {
        val voice = voices.getOrNull(index) ?: return
        if (index in fadeJobs.indices) {
            fadeJobs[index]?.cancel()
        }
        val job = scope.launch {
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
        if (index in fadeJobs.indices) fadeJobs[index] = job
    }
}

// ═════════════════════════════════════════════════════════════════════════
//  KANAL SESİ SOYUTLAMASI
// ═════════════════════════════════════════════════════════════════════════

internal interface ChannelVoice {
    /**
     * Çalmaya başlar. Başarılıysa `true`.
     *
     * **`suspend` olması şart:** uzak akışlarda hazır olmayı beklemek
     * gerekir. Eskiden bu bekleme `Thread.sleep` ile yapılıyordu ve
     * çağıran taraf ana thread olduğunda arayüz 4 saniye donuyordu.
     */
    suspend fun start(): Boolean
    fun pause()
    fun resume()
    /** Anlık ses seviyesi (fade başlangıcı için). */
    fun currentVolume(): Float
    fun setVolume(value: Float)
    /** Yumuşak kapanış + kaynak serbest bırakma. */
    fun fadeOutAndRelease(durationMs: Long)
    /** Anında serbest bırakma. */
    fun release()

    /**
     * Başarısız başlatmada kullanıcıya gösterilecek **somut** neden.
     *
     * Eskiden her hata "Akış açılamadı — internet veya kaynak sorunu"
     * olarak gösteriliyordu; kullanıcı parçanın mı, kaynağın mı yoksa
     * bağlantının mı bozuk olduğunu ayırt edemiyordu.
     */
    fun errorHint(): String? = null
}

/**
 * Yuva rezervasyonu için yer tutucu.
 *
 * Kanal hazırlanırken `voices[slot]` buraya işaret eder; gerçek ses
 * gelince üzerine yazılır. Böylece aynı yuvaya iki kanal atanmaz.
 * Hiçbir metodu gerçek iş yapmaz.
 */
private object BufferingVoice : ChannelVoice {
    override suspend fun start(): Boolean = false
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
    private val sound: AmbientSound,
    /** Çalar kendi kendine öldüğünde (AudioTrack hatası) motoru haberdar eder. */
    private val onFailed: (() -> Unit)? = null
) : ChannelVoice {

    private val player = ProceduralSoundPlayer(context, sound.id)
    @Volatile
    private var volume = 0f

    init {
        player.onRenderFailure = {
            Log.w("ProceduralVoice", "Sentez çaları durdu: ${sound.id}")
            onFailed?.invoke()
        }
    }

    /**
     * `AudioTrack` kurulumu ve ilk `play()` **ana thread dışında** yapılır.
     *
     * Altı kanal aynı anda açılırken AudioTrack oluşturma + ilk buffer
     * yazımı ana thread'de onlarca milisaniye sürüyor, kaydırma ve
     * dokunma tepkileri düşüyordu.
     */
    override suspend fun start(): Boolean = withContext(Dispatchers.Default) {
        runCatching {
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
 *
 * ## Neden bu sınıf yeniden yazıldı
 *  - **Ana thread bloklaması:** eski `start()` "hazır olana kadar bekle"
 *    döngüsünü `Thread.sleep(20)` ile yapıyordu. Çağıran motor ana
 *    thread olduğu için çevrimiçi bir parçaya dokunmak uygulamayı 4
 *    saniyeye kadar donduruyordu. Artık `suspendCancellableCoroutine`
 *    ile dinleyici tabanlı bekleme var.
 *  - **Ses odağı kavgası:** `handleAudioFocus = true` her kanalın ayrı
 *    odak istemesi demekti; altı kanal birbirini duraklatıyordu.
 *    Odak artık motorda tek merkezden yönetilir.
 *  - **Yönlendirme/timeout:** CC arşivleri sık sık http→https yönlendirmesi
 *    yapıyor. Varsayılan HTTP veri kaynağı yönlendirmeyi izlemediğinde
 *    akış hiç hazır olmuyordu ("müzik çalmıyor").
 */
@UnstableApi
internal class StreamVoice(
    private val context: Context,
    private val sound: AmbientSound,
    url: String?
) : ChannelVoice {

    companion object {
        private const val TAG = "StreamVoice"

        /** Hazır olma beklemesi — ağ yavaşsa kanal hata olarak işaretlenir. */
        private const val READY_TIMEOUT_MS = 10_000L

        /** Toplam deneme sayısı (ilk deneme dahil). */
        private const val MAX_ATTEMPTS = 3

        /** Denemeler arası bekleme (her denemede artar: 400ms → 800ms). */
        private const val RETRY_DELAY_MS = 400L

        /** Bazı CDN'ler boş UA'yı engelliyor. */
        private const val USER_AGENT = "Kitsugi/AmbientStream"
    }

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

    /**
     * Çalar **`start()` anında** kurulur.
     *
     * Sebep: veri kaynağı fabrikası adrese göre seçilir. Uzak akışta
     * zaman aşımı + yönlendirme izleyen HTTP kaynağı, yerel indirilmiş
     * dosyada (`file:`) ise dosya kaynağı kullanılır.
     */
    private var player: ExoPlayer? = null

    @Volatile
    private var volume = 0f

    @Volatile
    private var playing = false

    /** Sadece tanılama için: son hata adı. */
    @Volatile
    private var lastErrorName: String? = null

    /**
     * Akışı hazırlar ve **bloklamadan** bekler.
     *
     * `prepare()` asenkron. Eskiden hemen `true` dönüyordu, dolayısıyla
     * HTTP 404 / ağ hatası olsa bile kanal "çalıyor" görünüyor ve kullanıcı
     * sessiz bir kanal görüyordu. Ayrıca eski sürüm "hazır olana kadar
     * bekle" döngüsünü `Thread.sleep(20)` ile yapıyordu; çağıran motor ana
     * thread olduğu için çevrimiçi bir parçaya dokunmak uygulamayı 4
     * saniyeye kadar donduruyordu. Artık bekleme tamamen askıya alınmış
     * (suspending) durumda.
     */
    override suspend fun start(): Boolean {
        val raw = sourceUrl
        if (raw.isNullOrBlank()) {
            Log.w(TAG, "Adres çözülemedi: ${sound.id}")
            return false
        }
        // ⚠️ Uygulama `usesCleartextTraffic=false` ile derleniyor. CC
        // arşivleri (Internet Archive, Openverse) sık sık `http://` adres
        // döndürüyor; bu adresler sessizce çalınmıyordu. Aynı sunucular
        // https'i desteklediği için şemayı yükseltiyoruz.
        val target = if (raw.startsWith("http://", ignoreCase = true)) {
            "https://" + raw.substring("http://".length)
        } else {
            raw
        }

        // 🔁 Yeniden deneme: ağ hatası / zaman aşımı / 5xx geçici olabilir.
        // Eskiden tek denemeydi; yavaş bir bağlantıda ilk paket geciktiğinde
        // kanal kalıcı olarak "hata" durumunda kalıyordu.
        var lastFailure: String? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            val outcome = startOnce(target)
            if (outcome == null) {
                playing = true
                return true
            }
            lastFailure = outcome
            releasePlayer()
            if (!isRetryable(outcome) || attempt == MAX_ATTEMPTS) break
            Log.i(TAG, "Yeniden denenecek (${attempt + 1}/$MAX_ATTEMPTS): $target — $outcome")
            delay(RETRY_DELAY_MS * attempt)
        }

        Log.w(TAG, "Akış başlatılamadı: $target — ${lastFailure ?: "bilinmeyen hata"}")
        return false
    }

    /**
     * Tek bir oynatma denemesi.
     *
     * @return `null` başarı, aksi hâlde hata kodu adı
     */
    private suspend fun startOnce(target: String): String? {
        val exo = runCatching { buildPlayer(target) }.getOrElse { error ->
            Log.w(TAG, "Çalar kurulamadı: $target", error)
            null
        } ?: return "PlayerError"

        val prepared = runCatching {
            exo.setMediaItem(MediaItem.fromUri(target))
            exo.volume = 0f
            exo.playWhenReady = true
            exo.prepare()
            true
        }.getOrElse {
            Log.w(TAG, "Hazırlanamadı: $target", it)
            false
        }
        if (!prepared) {
            runCatching { exo.release() }
            return "PlayerError"
        }
        // Çaları sahaya al: `releasePlayer()` ve `release()` bunu kapatır.
        player = exo

        val ready = withTimeoutOrNull(READY_TIMEOUT_MS) {
            awaitReadyOrError(exo)
        } ?: false

        if (!ready) {
            Log.w(
                TAG,
                "Akış hazır olmadı (${READY_TIMEOUT_MS}ms): $target" +
                    (lastErrorName?.let { " — $it" } ?: "")
            )
            return lastErrorName ?: "Timeout"
        }

        runCatching { exo.play() }
        return null
    }

    /** Bu hata yeniden denemeye değer mi? */
    private fun isRetryable(error: String): Boolean = error in setOf(
        "Timeout",
        "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED",
        "ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT",
        "ERROR_CODE_IO_UNSPECIFIED",
        "ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE",
        "ERROR_CODE_UNSPECIFIED",
        "PlayerError"
    )

    private fun releasePlayer() {
        val exo = player
        player = null
        runCatching { exo?.release() }
    }

    override fun errorHint(): String? = when (lastErrorName) {
        null -> null
        "Timeout" -> "Kaynak zamanında yanıt vermedi — bağlantı yavaş olabilir."
        "ERROR_CODE_IO_BAD_HTTP_STATUS" ->
            "Kaynak dosyayı vermedi (404/403) — parça kaldırılmış olabilir."
        "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED",
        "ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT" ->
            "Ağa ulaşılamadı — internet bağlantısını kontrol et."
        "ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED",
        "ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED",
        "ERROR_CODE_PARSING_CONTAINER_MALFORMED" ->
            "Ses biçimi çözülemedi — farklı bir parça dene."
        "ERROR_CODE_DECODING_FORMAT_UNSUPPORTED" ->
            "Bu ses kodeği cihazda desteklenmiyor."
        else -> "Akış açılamadı — kaynak sorunu ($lastErrorName)."
    }

    /**
     * Adrese uygun çaları kurar.
     *
     * Ses odağı **motorda** tek merkezden yönetilir; bu yüzden
     * `handleAudioFocus = false`. Eskiden her kanal kendi odağını istiyor
     * ve kanallar birbirini duraklatıyordu.
     */
    private fun buildPlayer(target: String): ExoPlayer {
        val dataSourceFactory: DataSource.Factory = if (target.startsWith("http", ignoreCase = true)) {
            // 🌐 Uygulamanın **paylaşılan** OkHttp istemcisi kullanılır:
            //  - IPv4-öncelikli DNS (bazı sağlayıcılarda IPv6 AAAA kaydı
            //    dönüyor ama yol yok → akış hiç başlamıyordu)
            //  - bağlantı havuzu + HTTP/2 (6 kanal 6 TLS el sıkışması yapmaz)
            //  - yeniden deneme ve ağ günlüğü tek yerden
            //  - `usesCleartextTraffic=false` olduğu için http→https
            //    yönlendirmesi serbest bırakılır (Archive/Openverse CC
            //    adresleri sık sık http döner)
            //
            // `DefaultDataSource` sarmalayıcısı şart: HLS manifestosu,
            // `file:`/`content:` yönlendirmeleri ve indirilmiş dosyalar aynı
            // fabrikadan geçsin diye.
            val httpFactory = OkHttpDataSource.Factory(KitsugiHttpClient.streamClient)
                .setUserAgent(USER_AGENT)
                .setDefaultRequestProperties(
                    mapOf(
                        "Accept" to "*/*",
                        "Accept-Encoding" to "identity"
                    )
                )
            DefaultDataSource.Factory(context, httpFactory)
        } else {
            // İndirilmiş dosya (file:) veya content: URI'leri.
            DefaultDataSource.Factory(context)
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(audioAttributes, false)
            // Kulaklık çıkarılınca yalnızca bu kanal duraklar.
            .setHandleAudioBecomingNoisy(true)
            // 📶 Tampon ayarları: varsayılan `bufferForPlayback` 2,5 sn;
            // yavaş ağda ilk saniyelerde sürekli "buffering" olup ses
            // kesiliyordu. 4 sn ön tampon + 12 sn yeniden-tampon + 120 sn
            // ileri tampon, dalgalanmaları yutuyor.
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        /* minBufferMs = */ 30_000,
                        /* maxBufferMs = */ 120_000,
                        /* bufferForPlaybackMs = */ 4_000,
                        /* bufferForPlaybackAfterRebufferMs = */ 12_000
                    )
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()
            )
            // Ekran kapanınca ağ akışı devam etsin (kısmi uyandırma kilidi).
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .also { exo ->
                exo.repeatMode = Player.REPEAT_MODE_ONE
                exo.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        lastErrorName = error.errorCodeName
                        Log.w(TAG, "Akış hatası: ${sound.id} — ${error.errorCodeName}", error)
                    }
                })
            }
    }

    /**
     * `STATE_READY` gelince `true`, hata/bitiş gelince `false` döner.
     *
     * Dinleyici kaydı iptalde (timeout) kaldırılır; aksi hâlde her
     * başarısız denemede player'a bir dinleyici daha eklenirdi.
     */
    private suspend fun awaitReadyOrError(exo: ExoPlayer): Boolean =
        suspendCancellableCoroutine { cont ->
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> if (cont.isActive) cont.resume(true)
                        Player.STATE_ENDED -> if (cont.isActive) cont.resume(false)
                        else -> Unit
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    lastErrorName = error.errorCodeName
                    if (cont.isActive) cont.resume(false)
                }
            }
            exo.addListener(listener)
            cont.invokeOnCancellation { runCatching { exo.removeListener(listener) } }

            // Yarış durumu: durum zaten hazırsa/hatalıysa dinleyici hiç
            // tetiklenmez — burada hemen kontrol et.
            when {
                exo.playerError != null -> if (cont.isActive) cont.resume(false)
                exo.playbackState == Player.STATE_READY -> if (cont.isActive) cont.resume(true)
                exo.playbackState == Player.STATE_ENDED -> if (cont.isActive) cont.resume(false)
            }
        }

    override fun pause() {
        runCatching { player?.pause() }
        playing = false
    }

    override fun resume() {
        runCatching { player?.play() }
        playing = true
    }

    override fun currentVolume(): Float = volume

    override fun setVolume(value: Float) {
        volume = value
        runCatching { player?.volume = value }
    }

    /**
     * Yumuşak kapanış + serbest bırakma.
     *
     * **Neden fade şart:** anında `stop()` çağrısı hoparlörde "pat" sesi
     * üretir. Uyku zamanlayıcısı gece yarısı kanalları kapatırken bu
     * duyulursa kullanıcıyı uyandırır — özelliğin amacına aykırı.
     */
    override fun fadeOutAndRelease(durationMs: Long) {
        val exo = player
        if (exo == null || durationMs <= 0L) {
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
                runCatching { exo.volume = (startVolume * factor).coerceIn(0f, 1f) }
                mainHandler.postDelayed(this, 40L)
            }
        }
        mainHandler.post(runnable)
    }

    override fun release() {
        val exo = player
        player = null
        val doRelease = {
            if (exo != null) {
                runCatching {
                    exo.stop()
                    exo.release()
                }
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
