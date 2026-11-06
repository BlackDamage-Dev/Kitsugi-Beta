package com.kitsugi.animelist.data.music

import android.util.Log
import com.kitsugi.animelist.data.music.remote.MusicCatalogClient
import com.kitsugi.animelist.data.settings.AppSettings
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 🌐 Birleşik Ses Kataloğu Deposu
 *
 * **Tek giriş noktası.** Panel yalnızca bu sınıfla konuşur; hangi kaynağın
 * aktif olduğunu, anahtarı olup olmadığını, kotasını buradan öğrenir.
 *
 * Tasarım ilkesi: **her kaynak kendi koşulunda çalışır.**
 *  - Anahtarsız kaynaklar (Openverse, Archive) her zaman denenebilir
 *  - Anahtarlı kaynaklar (Jamendo, Freesound, Audius) yalnızca
 *    `enabled && key.isNotBlank()` ise denenir
 *  - Biri hata verirse diğerleri ETKİLENMEZ (her kaynak `runCatching` içinde)
 *
 * Sonuç modeli kaynak türünden bağımsızdır: hepsi [AmbientSound] döner,
 * dolayısıyla panelde aynı butonlarla çalınır.
 */
class MusicCatalogRepository(
    private val client: MusicCatalogClient = MusicCatalogClient()
) {

    companion object {
        private const val TAG = "MusicCatalogRepository"

        /**
         * Süreç genelinde tek örnek.
         *
         * **Neden:** kullanıcının açtığı/kapattığı kaynaklar `remember` ile
         * ekran başına yaşarsa, başka bir mangaya geçince sıfırlanır —
         * Freesound'u kapatan kullanıcı yeni mangada açık bulur. Ayrıca
         * son arama sonuçları da kaybolur.
         */
        @Volatile
        private var instance: MusicCatalogRepository? = null

        fun get(): MusicCatalogRepository =
            instance ?: synchronized(this) {
                instance ?: MusicCatalogRepository().also { instance = it }
            }

        /** Günlük istek sayacı — kota takibi için. */
        private const val QUOTA_OPENVERSE = 200
        private const val QUOTA_FREESOUND = 2_000
        private const val QUOTA_JAMENDO_MONTHLY = 35_000
    }

    // ── Arama sonuçları ─────────────────────────────────────────────────
    private val _results = MutableStateFlow<List<AmbientSound>>(emptyList())
    val results: StateFlow<List<AmbientSound>> = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    // ── Kaynak durumları ────────────────────────────────────────────────
    private val _sources = MutableStateFlow<List<MusicSourceState>>(
        MusicSources.all.map { def ->
            MusicSourceState(
                def = def,
                enabled = def.id in setOf(MusicSourceId.BUILTIN, MusicSourceId.OPENVERSE, MusicSourceId.ARCHIVE),
                keyConfigured = def.keyless
            )
        }
    )
    val sources: StateFlow<List<MusicSourceState>> = _sources.asStateFlow()

    // ── Kotadan kalan (yaklaşık) ────────────────────────────────────────
    private val _quotaUsed = MutableStateFlow<Map<MusicSourceId, Int>>(emptyMap())
    val quotaUsed: StateFlow<Map<MusicSourceId, Int>> = _quotaUsed.asStateFlow()

    /** Ayarlardan gelen anlık yapılandırma. UI her değişimde bunu çağırır. */
    fun syncSettings(settings: AppSettings) {
        _sources.update { current ->
            current.map { state ->
                // Kullanıcı bu kaynağı panelden değiştirdiyse ayar onu ezmesin
                if (state.def.id in overridden) return@map state
                when (state.def.id) {
                    MusicSourceId.BUILTIN -> state.copy(enabled = true, keyConfigured = true)
                    MusicSourceId.JAMENDO -> state.copy(
                        enabled = settings.musicJamendoEnabled,
                        keyConfigured = settings.musicJamendoClientId.isNotBlank()
                    )
                    MusicSourceId.FREESOUND -> state.copy(
                        enabled = settings.musicFreesoundEnabled,
                        keyConfigured = settings.musicFreesoundApiKey.isNotBlank()
                    )
                    MusicSourceId.OPENVERSE -> state.copy(
                        enabled = settings.musicOpenverseEnabled,
                        keyConfigured = settings.musicOpenverseToken.isNotBlank()
                    )
                    MusicSourceId.ARCHIVE -> state.copy(
                        enabled = settings.musicArchiveEnabled,
                        keyConfigured = true
                    )
                    MusicSourceId.AUDIUS -> state.copy(
                        enabled = settings.musicAudiusEnabled,
                        keyConfigured = settings.musicAudiusApiKey.isNotBlank()
                    )
                }
            }
        }
    }

    /**
     * Kaynağı aç/kapa.
     *
     * Anahtar gerektiren bir kaynağı açmaya çalışırken anahtar yoksa
     * yine de `enabled = true` yapılır — panelde 🟡 "anahtar girilmedi"
     * olarak görünür ve kullanıcı Ayarlar'a yönlendirilir. Sessizce
     * reddetmek daha kötü olurdu: kullanıcı düğmenin çalışmadığını sanır.
     */
    fun toggleSource(sourceId: MusicSourceId) {
        if (sourceId == MusicSourceId.BUILTIN) return
        overridden += sourceId
        _sources.update { list ->
            list.map { if (it.def.id == sourceId) it.copy(enabled = !it.enabled) else it }
        }
    }

    /**
     * Kullanıcının **anlık** kaynak tercihini döndürür.
     *
     * `null` = bu kaynağın tercihi henüz değiştirilmedi, ayardaki değer
     * geçerli. UI bunu kalıcılaştırmak için kullanır (DataStore'a yazar).
     */
    fun enabledOverride(sourceId: MusicSourceId): Boolean? =
        _sources.value.firstOrNull { it.def.id == sourceId }?.enabled

    /**
     * Ayarlardan gelen değeri uygular **ama** kullanıcının oturum içinde
     * yaptığı değişikliği ezmez.
     *
     * Eskiden her `syncSettings` çağrısı kaynak durumlarını ayardan
     * yeniden yazıyordu; kullanıcı panelde kapattığı kaynağı ayar
     * akışı yenilendiğinde tekrar açık buluyordu.
     */
    private val overridden = mutableSetOf<MusicSourceId>()

    fun markOverridden(sourceId: MusicSourceId) {
        overridden += sourceId
    }

    /** Kullanılabilir (etkin + gerekiyorsa anahtarı var) kaynaklar. */
    fun usableSources(): List<MusicSourceState> = _sources.value.filter { state ->
        val def = state.def
        when {
            !state.enabled -> false
            def.keyless -> true
            else -> state.keyConfigured
        }
    }

    /**
     * Tüm etkin kaynaklarda paralel arama yapar.
     *
     * Bir kaynak hata verse bile diğerlerinin sonuçları gelir.
     */
    suspend fun search(query: String, settings: AppSettings) {
        syncSettings(settings)

        val q = query.trim()
        _query.value = q
        _isSearching.value = true
        _searchError.value = null

        val usable = usableSources()
        if (usable.isEmpty()) {
            _results.value = emptyList()
            _isSearching.value = false
            _searchError.value = "Etkin kaynak yok. Ayarlar → Müzik bölümünden bir kaynak aç."
            return
        }

        // Arama metni boşsa duyguya göre genel bir terim kullan
        val effective = q.ifBlank { "ambient" }

        val collected = coroutineScope {
            usable.mapNotNull { state ->
                when (state.def.id) {
                    MusicSourceId.BUILTIN -> null
                    MusicSourceId.JAMENDO -> async {
                        runCatching {
                            client.searchJamendo(settings.musicJamendoClientId, tagsFor(effective))
                        }.getOrElse { e ->
                            Log.w(TAG, "Jamendo araması başarısız", e)
                            emptyList()
                        }
                    }
                    MusicSourceId.FREESOUND -> async {
                        runCatching {
                            client.searchFreesound(settings.musicFreesoundApiKey, effective)
                        }.getOrElse { e ->
                            Log.w(TAG, "Freesound araması başarısız", e)
                            emptyList()
                        }
                    }
                    MusicSourceId.OPENVERSE -> async {
                        runCatching {
                            client.searchOpenverse(settings.musicOpenverseToken, effective)
                        }.getOrElse { e ->
                            Log.w(TAG, "Openverse araması başarısız", e)
                            emptyList()
                        }
                    }
                    MusicSourceId.ARCHIVE -> async {
                        runCatching { client.searchArchive(effective) }.getOrElse { e ->
                            Log.w(TAG, "Archive araması başarısız", e)
                            emptyList()
                        }
                    }
                    MusicSourceId.AUDIUS -> async {
                        runCatching {
                            client.searchAudius(settings.musicAudiusApiKey, effective)
                        }.getOrElse { e ->
                            Log.w(TAG, "Audius araması başarısız", e)
                            emptyList()
                        }
                    }
                }
            }.map { it.await() }
        }

        val flattened = collected.flatten()
            .filter { it.isPlayable }
            .distinctBy { it.id }

        _results.value = flattened
        markAllValid(usable.map { it.def.id })
        _isSearching.value = false

        if (flattened.isEmpty() && _searchError.value == null) {
            _searchError.value = "Sonuç bulunamadı — farklı bir kelime dene"
        }
    }

    /** Arama kutusunu günceller (istek yapmaz). */
    fun setQuery(value: String) {
        _query.value = value
    }

    /** Aramayı temizler. */
    fun clearResults() {
        _results.value = emptyList()
        _query.value = ""
        _searchError.value = null
    }

    /**
     * Bir anahtarı doğrular ve sonucu kalıcı olarak kaynağa işler.
     * UI bunu "Doğrula" butonundan çağırır.
     */
    suspend fun validateSource(sourceId: MusicSourceId, key: String): Boolean {
        val ok = runCatching { client.validateKey(sourceId, key) }.getOrDefault(false)
        _sources.update { list ->
            list.map {
                if (it.def.id == sourceId) it.copy(
                    keyValid = ok,
                    keyConfigured = key.isNotBlank(),
                    lastCheckedAt = System.currentTimeMillis()
                ) else it
            }
        }
        return ok
    }

    /** Internet Archive öğesinin gerçek dosya adresini çözer. */
    suspend fun resolveUrl(sound: AmbientSound): String? {
        if (sound.sourceId != MusicSourceId.ARCHIVE) return sound.streamUrl
        val identifier = sound.id.removePrefix("archive_")
        return runCatching { client.resolveArchiveTrackUrl(identifier) }.getOrNull()
            ?: sound.streamUrl
    }

    // ── Dahili ──────────────────────────────────────────────────────────

    private fun markAllValid(ids: List<MusicSourceId>) {
        _sources.update { list ->
            list.map {
                if (it.def.id in ids) it.copy(keyValid = true, lastCheckedAt = System.currentTimeMillis())
                else it
            }
        }
        _quotaUsed.update { current ->
            current.toMutableMap().apply {
                ids.forEach { id -> this[id] = (this[id] ?: 0) + 1 }
            }
        }
    }

    /** Kullanıcı sorgusunu Jamendo etiketlerine çevirir. */
    private fun tagsFor(query: String): List<String> {
        val normalized = query.lowercase()
        val map = mapOf(
            "yağmur" to listOf("rain", "ambient"),
            "rain" to listOf("rain", "ambient"),
            "orman" to listOf("nature", "ambient"),
            "forest" to listOf("nature", "ambient"),
            "piyano" to listOf("piano", "instrumental"),
            "piano" to listOf("piano", "instrumental"),
            "sakin" to listOf("chill", "relaxing"),
            "kalma" to listOf("chill", "relaxing"),
            "chill" to listOf("chill", "relaxing"),
            "korku" to listOf("dark", "horror"),
            "horror" to listOf("dark", "horror")
        )
        return map[normalized] ?: listOf(normalized, "ambient")
    }

    /** Kota özeti (UI göstergesi için). */
    fun quotaLine(state: MusicSourceState): String {
        val used = _quotaUsed.value[state.def.id] ?: 0
        return when (state.def.id) {
            MusicSourceId.OPENVERSE -> "$used/$QUOTA_OPENVERSE bu oturum"
            MusicSourceId.FREESOUND -> "$used/$QUOTA_FREESOUND bugün"
            MusicSourceId.JAMENDO -> "$used/$QUOTA_JAMENDO_MONTHLY aylık"
            MusicSourceId.ARCHIVE -> "sınırsız"
            MusicSourceId.AUDIUS -> "—"
            MusicSourceId.BUILTIN -> "sınırsız"
        }
    }
}
