package com.kitsugi.animelist.data.manga

import android.content.Context
import android.net.Uri
import android.util.Log
import com.kitsugi.animelist.data.local.MangaChapterProgressEntity
import com.kitsugi.animelist.data.local.MangaSourceStateEntity
import com.kitsugi.animelist.data.local.KitsugiDatabase
import com.kitsugi.animelist.data.local.MangaMappingEntity
import com.kitsugi.animelist.data.manga.loader.MangaCache
import com.kitsugi.animelist.data.manga.model.SourceHealthStatus
import com.kitsugi.animelist.data.manga.model.SourceRuntimeStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Manga kaynaklarının ve okuma ilerlemesinin merkezi yönetim sınıfı.
 *
 * Sorumluluklar:
 * - Yüklü kaynak eklentileri (MangaSource) üzerinden manga/bölüm/sayfa verisini çekmek
 * - Yüklü, mevcut kaynakları listelemek
 * - Okuma ilerlemesini veritabanına kaydetmek/okumak
 * - MangaCache'i yönetmek (temizleme, boyut sorgusu)
 */
class MangaSourceRepository(private val context: Context) {

    private val TAG = "MangaSourceRepository"

    private val db    = KitsugiDatabase.getDatabase(context)
    val dao           = db.mangaChapterProgressDao()
    val cache         = MangaCache(context)
    private val sourceStateStore = MangaSourceStateStore(context)
    private val sourceConfigStore = SourceConfigStore(context)
    private val mirrorResolver = SourceMirrorResolver(context, sourceConfigStore)
    private val sourceEnabledStore = MangaSourceEnabledStore(context)
    private val sourceHealthService = SourceHealthService(context, sourceStateStore, mirrorResolver)
    private val searchCoordinator = MangaSearchCoordinator(sourceStateStore, sourceEnabledStore)
    /** Toplu silme ile gizlenen yerleşik kaynakların geri getirilebilmesi için kalıcı anahtar seti. */
    private val bulkDeletePrefs = context.getSharedPreferences("manga_bulk_delete", Context.MODE_PRIVATE)

    private val _availableSourcesFlow = MutableStateFlow<List<MangaSource>>(emptyList())
    val availableSourcesFlow: Flow<List<MangaSource>> = _availableSourcesFlow.asStateFlow()

    private fun refreshAvailableSources() {
        _availableSourcesFlow.value = searchCoordinator.getAvailableSources(MangaExtensionLoader.getLoadedSources())
    }

    private companion object {
        const val KEY_HIDDEN_BUILT_IN_KEYS = "hidden_builtin_keys"
    }

    /** Startup'taki tek eklenti yükleme işi. VM bunu join() ile bekler. */
    private val extensionsLoaded: Job =
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            MangaExtensionLoader.loadAllExtensions(context)
            KitsugiMangaDefaultProfile.ensureApplied(context)
            refreshAvailableSources()
        }

    init {
        KotatsuExtensionAdapter.loadSavedPrefs(context)
        // Loader her yeni eklenti yüklediğinde available sources listesini yenile
        MangaExtensionLoader.addLoadListener { refreshAvailableSources() }
    }

    // ─── Kaynak Yönetimi ─────────────────────────────────────────────────────

    /**
     * O anda belleğe yüklenmiş tüm manga kaynaklarını döndürür.
     * Kaynaklar Kotatsu benzeri şekilde önceliklendirilir:
     * - TR kaynaklar önce
     * - güvenilir / sağlıklı kaynaklar öne alınır
     * - devre dışı bırakılan kaynaklar gizlenir
     */
    fun getAvailableSources(): List<MangaSource> =
        searchCoordinator.getAvailableSources(MangaExtensionLoader.getLoadedSources()).also {
            if (_availableSourcesFlow.value !== it) _availableSourcesFlow.value = it
        }

    /**
     * Ayarlar ekranı için TÜM kurulu kaynaklar — kapalı olanlar DAHİL.
     * Kapalı kaynağı listeden gizlemek, kullanıcının onu tekrar açmasını imkânsız
     * hale getiriyordu (ekran görüntüsündeki "Disabled + kapalı switch" sorunu).
     */
    fun getInstalledSources(): List<MangaSource> =
        MangaExtensionLoader.getLoadedSources()
            .distinctBy { it.stableSourceKey() }
            .sortedWith(
                compareByDescending<MangaSource> { if (isSourceEnabled(it)) 1 else 0 }
                    .thenByDescending { searchCoordinator.getSourcePriority(it) }
                    .thenBy { it.name.lowercase() }
            )

    fun isSourceEnabled(source: MangaSource): Boolean = sourceEnabledStore.isEnabled(source)

    fun setSourceEnabled(source: MangaSource, enabled: Boolean) {
        sourceEnabledStore.setEnabled(source, enabled)
        // Diğer ViewModel/ekran örnekleri de aynı tercihi görsün.
        MangaExtensionLoader.notifySourceChanged()
        // Sağlık alanına ASLA Disabled yazmıyoruz. Yeniden açarken eski soğuma/hata
        // bayraklarını temizle ki kaynak hemen aramaya katılabilsin.
        if (enabled) {
            sourceStateStore.clearLegacyDisabledStatus(source)
            if (sourceStateStore.isCoolingDown(source)) {
                sourceStateStore.setHealthStatus(source, com.kitsugi.animelist.data.manga.model.SourceHealthStatus.Unknown)
            }
        }
    }

    /**
     * Eski sürümlerden kalan `Disabled` sağlık kayıtlarını bir kez temizler ve her
     * kurulu kaynak için açık bir "aktif" tercihi oluşturur (varsayılan = açık).
     */
    fun migrateLegacyDisabledStates() {
        MangaExtensionLoader.getLoadedSources().forEach { source ->
            if (sourceEnabledStore.ensureExplicitDefault(source)) {
                sourceStateStore.clearLegacyDisabledStatus(source)
            }
        }
    }

    /**
     * Eklentiler IO thread'de yüklenene kadar bekler; VM startup yarışını önler.
     * Yeniden `loadAllExtensions()` çağırmıyoruz — aynı APK'nın iki kez
     * classloader'a yüklenmesini engeller.
     */
    suspend fun awaitExtensionsLoaded() = extensionsLoaded.join()

    fun getSourceByName(name: String): MangaSource? =
        MangaExtensionLoader.getLoadedSources().firstOrNull { it.name == name }

    /**
     * Global arama için tercih edilen kaynak listesi.
     * Önce Türkçe kaynaklar, ardından az sayıda güvenilir fallback kaynak gelir.
     */
    fun getSearchCandidateSources(includeTrustedFallbacks: Boolean = true): List<MangaSource> =
        searchCoordinator.getSearchCandidateSources(
            MangaExtensionLoader.getLoadedSources(),
            includeTrustedFallbacks = includeTrustedFallbacks,
        )

    fun getSourcePriority(source: MangaSource): Int =
        searchCoordinator.getSourcePriority(source)

    fun getConfiguredBaseUrl(source: MangaSource): String =
        sourceConfigStore.getPreferredBaseUrl(source, source.baseUrl) ?: source.baseUrl

    fun getConfiguredDomain(source: MangaSource): String? =
        sourceConfigStore.getActiveDomain(source)

    fun clearConfiguredDomain(source: MangaSource) {
        sourceConfigStore.setActiveDomain(source, null)
    }

    fun setConfiguredDomain(source: MangaSource, domain: String?): Boolean =
        sourceConfigStore.setActiveDomainValidated(source, domain)

    fun validateSourceDomain(domain: String?): String? =
        sourceConfigStore.validateDomain(domain)

    fun getSourceUserAgentOverride(source: MangaSource): String? =
        sourceConfigStore.getUserAgentOverride(source)

    fun setSourceUserAgentOverride(source: MangaSource, value: String?): Boolean =
        sourceConfigStore.setUserAgentOverrideValidated(source, value)

    fun isValidSourceUserAgent(value: String?): Boolean =
        sourceConfigStore.isValidUserAgent(value)

    fun getSourceSlowdownEnabled(source: MangaSource): Boolean =
        sourceConfigStore.getSlowdownEnabled(source)

    fun setSourceSlowdownEnabled(source: MangaSource, enabled: Boolean) {
        sourceConfigStore.setSlowdownEnabled(source, enabled)
    }

    fun resetSourceDiagnostics(source: MangaSource) {
        sourceConfigStore.clearAllForSource(source)
        sourceStateStore.resetSource(source)
    }

    fun clearAllSourceDiagnostics() {
        sourceStateStore.clearAll()
    }

    fun clearAllSourceConfigs() {
        sourceConfigStore.clearAll()
    }

    fun getSourceRuntimeStats(source: MangaSource): SourceRuntimeStats =
        sourceStateStore.getRuntimeStats(source)

    fun getSourceFailureStreak(source: MangaSource): Int =
        sourceStateStore.getFailureStreak(source)

    fun getSourceCooldownUntil(source: MangaSource): Long =
        sourceStateStore.getCooldownUntil(source)

    fun getSourceLastErrorCode(source: MangaSource): String? =
        sourceStateStore.getLastErrorCode(source)

    fun getSourceLastReason(source: MangaSource): String? =
        sourceStateStore.getLastReason(source)

    fun observeSourceStateReport(): Flow<List<MangaSourceStateEntity>> =
        db.mangaSourceStateDao().observeAll()

    suspend fun tryRefreshSourceMirror(source: MangaSource, cause: Throwable? = null): Boolean =
        withContext(Dispatchers.IO) {
            mirrorResolver.tryResolveAndActivateMirror(source, cause)
        }

    suspend fun quickCheckSourceHealth(
        source: MangaSource,
        sampleQuery: String? = null,
    ): SourceHealthStatus = sourceHealthService.quickCheck(source, sampleQuery)

    fun postProcessSearchResults(
        source: MangaSource,
        query: String,
        mangas: List<MangaDetails>,
        relaxScoring: Boolean = false
    ): List<MangaDetails> = searchCoordinator.postProcess(source, query, mangas, relaxScoring)

    /**
     * Verilen [extensionId] eklentisini diskten yükler ve kullanıma hazır hale getirir.
     * Başarısız olursa null döner.
     */
    fun loadSource(extensionId: String): MangaSource? =
        MangaExtensionLoader.loadExtension(context, extensionId)

    /**
     * Verilen [extensionId] eklentisini bellekten kaldırır.
     */
    fun unloadSource(extensionId: String) =
        MangaExtensionLoader.unloadExtension(context, extensionId)

    /**
     * Seçilen bir Uri dosyasından (.mex / .apk) manga eklentisini kurar.
     * Eklentiyi filesDir/manga_extensions/ klasörüne kopyalar ve yükler.
     */
    fun installExtensionFromUri(uri: Uri): Result<MangaSource> {
        return try {
            val contentResolver = context.contentResolver
            var fileName = "extension_${System.currentTimeMillis()}"
            var extension = "mex"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    val rawName = cursor.getString(nameIndex)
                    if (!rawName.isNullOrBlank()) {
                        fileName = rawName.substringBeforeLast(".")
                        val rawExt = rawName.substringAfterLast(".", "")
                        if (rawExt.equals("apk", ignoreCase = true)) {
                            extension = "apk"
                        }
                    }
                }
            }

            val dir = File(context.filesDir, "manga_extensions").also { if (!it.exists()) it.mkdirs() }
            val tempFile = File(dir, "$fileName.$extension")
            if (tempFile.exists()) tempFile.delete()

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            // NOT: setReadOnly() KALDIRILDI — Android 10+ PathClassLoader read-only dosyayı yükleyemiyor

            // Dosya adını paket adına normalize et. Aksi halde elle kurulan bir APK
            // (`tachiyomi-tr.mangatr-v1.6.24.apk`) repo kurulumuyla aynı kimliği
            // paylaşmaz; getInstalledVersionCode()/güncelleme kontrolü onu bulamaz ve
            // aynı eklenti ikinci kez "kurulmuş" gibi görünür.
            val extensionId = if (extension == "apk") {
                MangaExtensionLoader.readPackageName(context, tempFile)
                    ?.takeIf { it.isNotBlank() }
                    ?.replace(".", "_")
                    ?: fileName
            } else fileName

            val targetFile = File(dir, "$extensionId.$extension")
            if (targetFile.absolutePath != tempFile.absolutePath) {
                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
            }

            // Kullanıcı dosyayı elle seçti → imzaya açık onay vermiş sayılır.
            // (Özel Kitsugi-Manga-Plugins APK'ları debug anahtarıyla imzalıdır ve
            // Keiyoushi resmî parmak izleriyle eşleşmez.)
            val loaded = MangaExtensionLoader.loadExtension(
                context,
                extensionId,
                trustSignature = extension == "apk",
            )
            if (loaded != null) {
                sourceEnabledStore.ensureExplicitDefault(loaded)
                sourceStateStore.clearLegacyDisabledStatus(loaded)
                Result.success(loaded)
            } else {
                Result.failure(Exception("Uyumsuz eklenti formatı veya kaynak sınıfı yüklenemedi."))
            }
        } catch (e: SecurityException) {
            Result.failure(Exception("Eklenti imzası doğrulanamadı: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Manga kaynağını kaldırır (diskten siler ve bellekten siler).
     * Dahili kaynaklar (`isBuiltIn == true`) diskten silinemez; yalnızca devre dışı bırakılır.
     */
    fun deleteExtension(source: MangaSource): Boolean {
        if (source.isBuiltIn) {
            sourceStateStore.setHealthStatus(source, SourceHealthStatus.Disabled, reason = "user_disabled")
            return false
        }
        resetSourceDiagnostics(source)
        val extId = MangaExtensionLoader.getExtensionIdForSource(source) ?: return false
        return MangaExtensionLoader.deleteExtension(context, extId)
    }

    /** "Toplu Sil" sonucu: silinen eklenti, silinemeyen eklenti ve gizlenen yerleşik kaynak sayısı. */
    data class MangaBulkDeleteResult(
        val deletedExtensions: Int,
        val failedExtensions: Int,
        val hiddenBuiltIns: Int,
    ) {
        val totalRemoved: Int get() = deletedExtensions + hiddenBuiltIns
    }

    /** Geri getirilebilecek (toplu silme ile gizlenmiş) yerleşik kaynak sayısı. */
    fun getBulkDeleteHiddenBuiltInCount(): Int =
        bulkDeletePrefs.getStringSet(KEY_HIDDEN_BUILT_IN_KEYS, emptySet())?.size ?: 0

    /**
     * Manga sekmesindeki "Toplu Sil" aksiyonunun gerçek işi.
     *
     * İki kaynak türü vardır:
     *  1) İndirilmiş .apk/.mex eklentileri → dosyaları diskten silinir, bellekten çıkarılır,
     *     tanılama/ayar kayıtları temizlenir. Görünür liste yerine DİSK esas alınır; böylece
     *     devre dışı bırakılmış ya da yüklenememiş eklentiler de gerçekten silinir.
     *  2) Uygulama içine gömülü Kotatsu kaynakları → silinecek dosyaları yoktur. Eski kod
     *     `deleteExtension` ile bunları silmeye çalışıyor, her biri `false` döndüğü için
     *     buton basılmasına rağmen hiçbir şey olmuyordu. Bunlar kalıcı olarak "Disabled"
     *     yapılarak listeden/aramadan çıkarılır ve [restoreBulkDeletedBuiltIns] ile geri getirilir.
     */
    fun deleteAllExtensions(): MangaBulkDeleteResult {
        // 1) Diskteki tüm eklentileri sil (görünür liste değil disk kaynak).
        val extensionIds = MangaExtensionLoader.getInstalledExtensionFiles(context)
            .map { it.nameWithoutExtension }
            .distinct()
        val loadedByExtension = MangaExtensionLoader.getLoadedExtensions()
        extensionIds.forEach { id ->
            loadedByExtension[id]?.forEach { resetSourceDiagnostics(it) }
        }
        var deleted = 0
        var failed = 0
        extensionIds.forEach { id ->
            if (MangaExtensionLoader.deleteExtension(context, id)) deleted++ else failed++
        }

        // 2) Yerleşik kaynakları geri getirilebilir biçimde gizle.
        val builtIns = MangaExtensionLoader.getBuiltInSources()
        val hiddenKeys = LinkedHashSet(
            bulkDeletePrefs.getStringSet(KEY_HIDDEN_BUILT_IN_KEYS, emptySet()).orEmpty()
        )
        val notYetDisabled = builtIns.filter {
            sourceStateStore.getHealthStatus(it) != SourceHealthStatus.Disabled
        }
        sourceStateStore.setHealthStatusForAll(notYetDisabled, SourceHealthStatus.Disabled)
        // Yalnızca BU işlemle gizlenenleri kaydet: kullanıcının daha önce kendisi
        // devre dışı bıraktığı kaynaklar "Geri getir" ile istemeden açılmasın.
        notYetDisabled.forEach { hiddenKeys.add(it.stableSourceKey()) }
        bulkDeletePrefs.edit().putStringSet(KEY_HIDDEN_BUILT_IN_KEYS, hiddenKeys).apply()

        return MangaBulkDeleteResult(
            deletedExtensions = deleted,
            failedExtensions = failed,
            hiddenBuiltIns = notYetDisabled.size,
        )
    }

    /** "Toplu Sil" ile gizlenen yerleşik kaynakları tekrar listeye/aramaya dahil eder. */
    fun restoreBulkDeletedBuiltIns(): Int {
        val hiddenKeys = bulkDeletePrefs.getStringSet(KEY_HIDDEN_BUILT_IN_KEYS, emptySet()).orEmpty()
        if (hiddenKeys.isEmpty()) return 0
        val toRestore = KotatsuExtensionAdapter.getLoadedSources()
            .filter { it.stableSourceKey() in hiddenKeys }
        sourceStateStore.setHealthStatusForAll(toRestore, SourceHealthStatus.Unknown)
        bulkDeletePrefs.edit().remove(KEY_HIDDEN_BUILT_IN_KEYS).apply()
        return toRestore.size
    }


    // ─── Manga Arama ve Listeleme ─────────────────────────────────────────────

    /**
     * Yüklü kaynaklarda paralel arama yapar ama artık tüm source'ları körlemesine kullanmaz.
     * Önce TR odaklı adaylar seçilir, sonra sonuçlar kanonik başlık + kalite skoru ile filtrelenir.
     */
    suspend fun searchAllSources(query: String, page: Int = 1): List<Pair<MangaSource, MangaDetails>> =
        withContext(Dispatchers.IO) {
            val sources = getSearchCandidateSources(includeTrustedFallbacks = true)
            if (sources.isEmpty()) {
                Log.w(TAG, "Arama için uygun manga kaynağı yok")
                return@withContext emptyList()
            }

            kotlinx.coroutines.supervisorScope {
                sources.map { source ->
                    async {
                        val startedAt = System.currentTimeMillis()
                        val isFastEngine = source.engineType in listOf(
                            ExtensionEngine.MADARA,
                            ExtensionEngine.THEMESIA,
                            ExtensionEngine.SVELTE,
                            ExtensionEngine.INERTIA,
                            ExtensionEngine.KEI_SOURCE
                        )
                        val timeoutMs = 25_000L
                        try {
                            kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
                                val result = source.fetchSearchManga(page, query)
                                val elapsed = System.currentTimeMillis() - startedAt
                                recordSearchSuccess(source, elapsedMs = elapsed)
                                // MangaLogger: kaynak düzeyi log (MihonSourceWrapper zaten içerde loguyor;
                                // burada sadece repository timeout durumunu yakalarken de log bırakıyoruz)
                                postProcessSearchResults(source, query, result.mangas)
                                    .map { manga -> source to manga }
                            } ?: run {
                                // Timeout — MangaSourceRepository düzeyi log
                                val elapsed = System.currentTimeMillis() - startedAt
                                MangaLogger.logSearch(context, source.name, query, success = false,
                                    elapsedMs = elapsed,
                                    error = Exception("Kaynak zaman aşımı (${timeoutMs}ms)"))
                                recordSearchFailure(source, Exception("Timeout"), elapsedMs = elapsed)
                                emptyList()
                            }
                        } catch (e: Exception) {
                            recordSearchFailure(source, e, elapsedMs = System.currentTimeMillis() - startedAt)
                            Log.e(TAG, "[${source.name}] arama hatası: ${e.message}")
                            emptyList()
                        }
                    }
                }.awaitAll().flatten()
            }
        }

    /**
     * Tek bir kaynaktan popüler mangaların listesini çeker.
     */
    suspend fun fetchPopular(source: MangaSource, page: Int = 1): MangaSourceResult =
        withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            try {
                val result = source.fetchPopularManga(page)
                val elapsed = System.currentTimeMillis() - startedAt
                recordPopularSuccess(source, elapsedMs = elapsed)
                result.copy(mangas = result.mangas.map { it.copy(source = source.name) })
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val elapsed = System.currentTimeMillis() - startedAt
                recordPopularFailure(source, e, elapsedMs = elapsed)
                Log.e(TAG, "[${source.name}] popüler liste hatası: ${e.message}")
                // Hatayı YUTMA: eskiden boş liste dönüyordu ve kullanıcı
                // "sonuç bulunamadı" sanıp kaynağı bozuk sanıyordu.
                MangaSourceResult(
                    mangas = emptyList(),
                    hasNextPage = false,
                    error = e.localizedMessage ?: e.message ?: "Popüler liste alınamadı."
                )
            }
        }

    fun getSourceHealthStatus(source: MangaSource): SourceHealthStatus =
        sourceStateStore.getHealthStatus(source)

    fun setSourceHealthStatus(source: MangaSource, status: SourceHealthStatus) {
        sourceStateStore.setHealthStatus(source, status)
    }

    fun recordSearchSuccess(source: MangaSource, elapsedMs: Long = 0L) {
        sourceStateStore.recordOperationSuccess(source, operation = "search", elapsedMs = elapsedMs)
    }

    fun recordPopularSuccess(source: MangaSource, elapsedMs: Long = 0L) {
        sourceStateStore.recordOperationSuccess(source, operation = "popular", elapsedMs = elapsedMs)
    }

    fun recordSearchFailure(source: MangaSource, error: Throwable?, elapsedMs: Long? = null) {
        val message = error?.message.orEmpty()
        val status = SourceFailureClassifier.classifyStatus(error)
        sourceStateStore.recordOperationFailure(
            source = source,
            operation = "search",
            reason = message.take(240),
            statusOverride = status,
            elapsedMs = elapsedMs,
        )
    }

    fun recordPopularFailure(source: MangaSource, error: Throwable?, elapsedMs: Long? = null) {
        val message = error?.message.orEmpty()
        val status = SourceFailureClassifier.classifyStatus(error)
        sourceStateStore.recordOperationFailure(
            source = source,
            operation = "popular",
            reason = message.take(240),
            statusOverride = status,
            elapsedMs = elapsedMs,
        )
    }

    // ─── Okuma İlerlemesi ────────────────────────────────────────────────────

    suspend fun saveProgress(
        chapterUrl: String,
        mangaUrl: String,
        chapterName: String,
        pageIndex: Int,
        totalPages: Int
    ) = withContext(Dispatchers.IO) {
        val isCompleted = totalPages > 0 && pageIndex >= totalPages - 1
        dao.upsert(
            MangaChapterProgressEntity(
                chapterUrl    = chapterUrl,
                mangaUrl      = mangaUrl,
                chapterName   = chapterName,
                lastPageIndex = pageIndex,
                totalPages    = totalPages,
                isCompleted   = isCompleted,
                updatedAt     = System.currentTimeMillis()
            )
        )
        Log.v(TAG, "İlerleme kaydedildi: $chapterName sayfa=${pageIndex + 1}/$totalPages tamamlandı=$isCompleted")
    }

    suspend fun getProgress(chapterUrl: String): MangaChapterProgressEntity? =
        withContext(Dispatchers.IO) { dao.getProgress(chapterUrl) }

    suspend fun getAllProgressForManga(mangaUrl: String): List<MangaChapterProgressEntity> =
        withContext(Dispatchers.IO) { dao.getAllForManga(mangaUrl) }

    suspend fun getCompletedChapters(mangaUrl: String): List<MangaChapterProgressEntity> =
        withContext(Dispatchers.IO) { dao.getCompletedChapters(mangaUrl) }

    // ─── Önbellek Yönetimi ────────────────────────────────────────────────────

    fun cacheSizeMb(): Float = cache.currentSizeMb()

    fun clearCache() = cache.clearAll()

    // ─── Manga Eşleştirme / Mapping ──────────────────────────────────────────

    fun observeMangaMapping(mediaId: Int): Flow<MangaMappingEntity?> =
        db.mangaMappingDao().observeMapping(mediaId)

    suspend fun getMangaMapping(mediaId: Int): MangaMappingEntity? =
        withContext(Dispatchers.IO) { db.mangaMappingDao().getMapping(mediaId) }

    suspend fun saveMangaMapping(mediaId: Int, source: String, url: String, title: String, thumbnailUrl: String?) =
        withContext(Dispatchers.IO) {
            db.mangaMappingDao().insert(
                MangaMappingEntity(
                    mediaId = mediaId,
                    mangaSource = source,
                    mangaUrl = url,
                    mangaTitle = title,
                    mangaThumbnail = thumbnailUrl
                )
            )
            Log.d(TAG, "Manga eşleşmesi kaydedildi: mediaId=$mediaId -> $source: $title")
        }

    suspend fun deleteMangaMapping(mediaId: Int) =
        withContext(Dispatchers.IO) {
            db.mangaMappingDao().deleteMapping(mediaId)
            Log.d(TAG, "Manga eşleşmesi silindi: mediaId=$mediaId")
        }
}
