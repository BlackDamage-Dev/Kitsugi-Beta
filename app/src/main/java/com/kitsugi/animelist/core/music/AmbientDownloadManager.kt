package com.kitsugi.animelist.core.music

import android.content.Context
import android.util.Log
import com.kitsugi.animelist.core.network.KitsugiHttpClient
import com.kitsugi.animelist.data.music.AmbientSound
import com.kitsugi.animelist.data.music.MusicCatalogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * ⬇️ Ses İndirme Yöneticisi
 *
 * **Neden gerekli:** uzak kaynaklar (Jamendo, Freesound, Openverse, Archive,
 * Audius) internet ister. İnternetsiz okumak, metroya girmek veya veri
 * tasarrufu yapmak isteyen kullanıcı için indirme şart.
 *
 * ## Tasarım kararları
 *
 * **1. Tek yer:** tüm sesler `filesDir/ambient/` altında düz dosya olarak
 * durur. Anime indirmelerinin aksine alt klasör/`DocumentFile` yok —
 * sesler küçük (tipik 0,5–8 MB), düz yapı yeterli ve hızlı.
 *
 * **2. Dosya adı = ses kimliğinin hash'i.** Ses kimliği URL veya rastgele
 * karakter içerebilir (`audius_0x1234...`); doğrudan dosya adı yapmak
 * sorun çıkarır (uzunluk, `/`, `:`). SHA-1'in ilk 16 karakteri hem kısa
 * hem çakışmasız.
 *
 * **3. Uzantı içerik tipinden.** Sunucu `Content-Type` vermezse `.mp3`
 * varsayılır — çoğu kaynak mp3 veriyor.
 *
 * **4. Akış adresi çözümü.** Archive gibi kaynaklarda klasör URL'i gelir;
 * indirmeden önce `resolveArchiveTrackUrl` ile gerçek dosya bulunur.
 *
 * **5. Devam edilebilir indirme YOK.** Sesler küçük olduğu için parça
 * parça indirme yerine tek istek yeterli. Yarım kalan dosya silinir —
 * bozuk ses çalmaktan iyidir.
 */
class AmbientDownloadManager(
    private val context: Context
) {

    companion object {
        private const val TAG = "AmbientDownload"
        private const val DIR_NAME = "ambient"

        /** Güvenli üst sınır: 40 MB. Bundan büyük "ses" muhtemelen yanlış. */
        private const val MAX_BYTES = 40L * 1024L * 1024L

        @Volatile
        private var instance: AmbientDownloadManager? = null

        fun get(context: Context): AmbientDownloadManager =
            instance ?: synchronized(this) {
                instance ?: AmbientDownloadManager(context.applicationContext).also { instance = it }
            }
    }

    /** İndirme durumu. */
    data class DownloadState(
        val soundId: String,
        val progress: Float = 0f,
        val bytesDone: Long = 0L,
        val bytesTotal: Long = 0L,
        val downloading: Boolean = false,
        val error: String? = null
    )

    /** Ses kimliği → indirilmiş dosya. */
    private val _downloaded = MutableStateFlow<Map<String, File>>(emptyMap())
    val downloaded: StateFlow<Map<String, File>> = _downloaded.asStateFlow()

    /** Ses kimliği → o anki indirme durumu. */
    private val _active = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val active: StateFlow<Map<String, DownloadState>> = _active.asStateFlow()

    /** Toplam disk kullanımı (bayt). */
    val totalBytes: Long
        get() = runCatching {
            ambientDir().listFiles()?.sumOf { it.length() } ?: 0L
        }.getOrDefault(0L)

    init {
        _downloaded.value = scanInstalled()
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Sorgular
    // ─────────────────────────────────────────────────────────────────────

    fun isDownloaded(soundId: String): Boolean = soundId in _downloaded.value

    /** İndirilmiş dosyanın yolu (yoksa null). */
    fun localFile(soundId: String): File? = _downloaded.value[soundId]

    /**
     * Çalma için en iyi dosya yolu: indirildiyse yerel, değilse uzak akış.
     * Bu, çevrimdışı çalmanın tek karar noktasıdır.
     */
    fun playableUri(sound: AmbientSound): String? {
        localFile(sound.id)?.let { return it.toURI().toString() }
        return sound.streamUrl
    }

    // ─────────────────────────────────────────────────────────────────────
    //  İndirme
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Bir sesi indirir.
     *
     * Aynı ses zaten indiriliyorsa ikinci istek açılmaz.
     *
     * @return Başarılıysa dosya, değilse `null`
     */
    suspend fun download(
        sound: AmbientSound,
        repository: MusicCatalogRepository? = null
    ): File? = withContext(Dispatchers.IO) {

        if (isDownloaded(sound.id)) return@withContext localFile(sound.id)
        if (_active.value[sound.id]?.downloading == true) return@withContext null

        // Akış adresini çöz (Archive klasör URL'i olabilir)
        var url = sound.streamUrl
        if (repository != null) {
            url = runCatching { repository.resolveUrl(sound) }.getOrNull() ?: url
        }
        if (url.isNullOrBlank()) {
            setError(sound.id, "İndirilebilir adres yok")
            return@withContext null
        }

        setActive(sound.id, DownloadState(soundId = sound.id, downloading = true))

        val dir = ambientDir()
        val target = File(dir, "${safeName(sound.id)}.$DEFAULT_EXT")
        val temp = File(dir, "${safeName(sound.id)}.part")

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "audio/*,*/*;q=0.8")
                .build()

            val response = KitsugiHttpClient.client.newCall(request).execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    setError(sound.id, "Sunucu hatası (${resp.code})")
                    return@withContext null
                }

                val body = resp.body ?: run {
                    setError(sound.id, "Boş yanıt")
                    return@withContext null
                }

                val total = body.contentLength()
                if (total > MAX_BYTES) {
                    setError(sound.id, "Dosya çok büyük (${total / 1024 / 1024} MB)")
                    return@withContext null
                }

                // Uzantıyı içerik tipinden belirle
                val ext = extensionFor(resp.header("Content-Type"), url)
                val finalTarget = File(dir, "${safeName(sound.id)}.$ext")

                var written = 0L
                val buffer = ByteArray(64 * 1024)

                temp.outputStream().use { out ->
                    body.byteStream().use { input ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                            written += read

                            if (written > MAX_BYTES) {
                                throw IllegalStateException("Dosya sınırı aşıldı")
                            }
                            setActive(
                                sound.id,
                                DownloadState(
                                    soundId = sound.id,
                                    downloading = true,
                                    bytesDone = written,
                                    bytesTotal = if (total > 0) total else written,
                                    progress = if (total > 0) {
                                        (written.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                    } else 0f
                                )
                            )
                        }
                    }
                }

                // Doğrulama: gerçekten ses verisi mi?
                if (written < 1024L) {
                    temp.delete()
                    setError(sound.id, "Dosya çok küçük — geçersiz")
                    return@withContext null
                }

                // Nihai konuma taşı
                if (finalTarget.exists()) finalTarget.delete()
                if (!temp.renameTo(finalTarget)) {
                    temp.copyTo(finalTarget, overwrite = true)
                    temp.delete()
                }

                _downloaded.update { it + (sound.id to finalTarget) }
                _active.update { it - sound.id }
                writeManifest(sound.id to finalTarget.nameWithoutExtension)

                Log.i(TAG, "İndirildi: ${sound.title} → ${finalTarget.name} (${written / 1024} KB)")
                finalTarget
            }
        } catch (t: Throwable) {
            Log.w(TAG, "İndirme başarısız: ${sound.id}", t)
            runCatching { temp.delete() }
            setError(sound.id, t.message ?: "İndirme hatası")
            null
        }
    }

    /** İndirilen sesi siler. */
    fun delete(soundId: String): Boolean {
        val file = localFile(soundId) ?: return false
        val ok = runCatching { file.delete() }.getOrDefault(false)
        if (ok) {
            _downloaded.update { it - soundId }
            _active.update { it - soundId }
            writeManifest()
        }
        return ok
    }

    /** Tüm ses indirmelerini siler. */
    fun clearAll(): Int {
        var count = 0
        _downloaded.value.values.forEach { file ->
            if (runCatching { file.delete() }.getOrDefault(false)) count++
        }
        _downloaded.value = emptyMap()
        _active.value = emptyMap()
        runCatching { manifestFile().delete() }
        return count
    }

    /** Hata durumunu temizler. */
    fun clearError(soundId: String) {
        _active.update { it - soundId }
    }

    /** Diskteki dosyaları tarar — uygulama yeniden açıldığında. */
    fun rescan() {
        _downloaded.value = scanInstalled()
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Dahili
    // ─────────────────────────────────────────────────────────────────────

    private fun ambientDir(): File {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Diskteki dosyaları tarar.
     *
     * **Neden manifest:** dosya adı ses kimliğinin SHA-1'i. Hash tek yönlü
     * olduğu için "hangi ses bu" sorusu dosya adından yanıtlanamaz.
     * `manifest.json` hash → ses kimliği eşlemesini tutar. Bu dosya
     * olmadan uygulama yeniden açıldığında indirmeler kayıp görünürdü —
     * diskte dosya var ama hangi sese ait olduğu bilinmiyor.
     */
    private fun scanInstalled(): Map<String, File> {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.isDirectory) return emptyMap()

        val onDisk = dir.listFiles()
            ?.filter {
                it.isFile && it.extension != "part" &&
                    it.name != "manifest.json" && it.length() > 1024L
            }
            ?.associateBy { it.nameWithoutExtension }
            .orEmpty()

        if (onDisk.isEmpty()) {
            runCatching { manifestFile().delete() }
            return emptyMap()
        }

        val mapping = readManifest()
        val result = mutableMapOf<String, File>()

        // Manifestteki eşlemelerden yalnızca dosyası hâlâ var olanlar
        mapping.forEach { (soundId, hash) ->
            val file = onDisk[hash] ?: return@forEach
            result[soundId] = file
        }
        return result
    }

    /** hash → ses kimliği ters eşlemesi (manifestte saklanır). */
    private fun readManifest(): Map<String, String> {
        val file = manifestFile()
        if (!file.isFile) return emptyMap()
        return runCatching {
            val root = org.json.JSONObject(file.readText())
            val out = mutableMapOf<String, String>()
            root.keys().forEach { soundId ->
                out[soundId] = root.optString(soundId)
            }
            out
        }.getOrElse {
            Log.w(TAG, "Manifest okunamadı — yeniden oluşturulacak", it)
            emptyMap()
        }
    }

    /**
     * Manifesti **tek doğruluk kaynağı** olarak yeniden yazar.
     *
     * Önce diskteki mevcut eşlemeler okunur, sonra `_downloaded` içeriği
     * (gerçek durum) üzerine yazılır. Böylece silinen kayıtlar düşer,
     * yeni eklenenler kalır. `extra` yalnızca `_downloaded` henüz
     * güncellenmeden önce çağrılırsa gerekir — normalde kullanılmaz.
     */
    private fun writeManifest(extra: Pair<String, String>? = null) {
        runCatching {
            val map = mutableMapOf<String, String>()
            if (extra != null) map[extra.first] = extra.second
            _downloaded.value.forEach { (soundId, file) ->
                map[soundId] = file.nameWithoutExtension
            }
            val json = org.json.JSONObject()
            map.forEach { (k, v) -> json.put(k, v) }
            manifestFile().writeText(json.toString())
        }.onFailure {
            Log.w(TAG, "Manifest yazılamadı", it)
        }
    }

    private fun manifestFile(): File = File(ambientDir(), "manifest.json")

    private fun setActive(soundId: String, state: DownloadState) {
        _active.update { it + (soundId to state) }
    }

    private fun setError(soundId: String, message: String) {
        _active.update {
            it + (soundId to DownloadState(soundId = soundId, downloading = false, error = message))
        }
    }

    /** Ses kimliğini güvenli dosya adına çevirir. */
    private fun safeName(soundId: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-1")
            .digest(soundId.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun extensionFor(contentType: String?, url: String): String {
        val ct = contentType?.lowercase().orEmpty()
        return when {
            ct.contains("mpeg") || ct.contains("mp3") -> "mp3"
            ct.contains("ogg") || ct.contains("opus") -> "ogg"
            ct.contains("wav") -> "wav"
            ct.contains("m4a") || ct.contains("mp4") || ct.contains("aac") -> "m4a"
            ct.contains("flac") -> "flac"
            ct.contains("webm") -> "webm"
            else -> {
                // İçerik tipi yoksa adresten çıkar
                val fromUrl = url.substringBefore('?').substringAfterLast('.', "")
                if (fromUrl.length in 2..4) fromUrl.lowercase() else DEFAULT_EXT
            }
        }
    }

    private val DEFAULT_EXT = "mp3"
    private val USER_AGENT = "Mozilla/5.0 (Android) KitsugiMedia/1.0"
}
