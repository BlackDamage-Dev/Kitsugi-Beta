package com.kitsugi.animelist.ui.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kitsugi.animelist.data.local.MediaEntryBackup
import com.kitsugi.animelist.data.local.MediaEntryRepository
import com.kitsugi.animelist.data.remote.ApiSearchSelection
import com.kitsugi.animelist.data.remote.BangumiMediaEntryFactory
import com.kitsugi.animelist.data.remote.canonicalMediaSourceId
import com.kitsugi.animelist.data.remote.matchesInSource
import com.kitsugi.animelist.data.remote.firstMatchingInTargetOrResultSource
import com.kitsugi.animelist.data.settings.SettingsDataStore
import com.kitsugi.animelist.data.settings.KitsugiContentPrefs
import com.kitsugi.animelist.model.EntryAutoRules
import com.kitsugi.animelist.model.MediaEntry
import com.kitsugi.animelist.model.MediaType
import com.kitsugi.animelist.model.WatchStatus
import com.kitsugi.animelist.ui.components.BackupImportMode
import com.kitsugi.animelist.ui.navigation.MainTab
import com.kitsugi.animelist.ui.screens.mylist.MY_LIST_ALL_TAB_INDEX
import com.kitsugi.animelist.ui.screens.mylist.MY_LIST_ANILIST_TAB_INDEX
import com.kitsugi.animelist.ui.screens.mylist.MY_LIST_SIMKL_TAB_INDEX
import com.kitsugi.animelist.ui.screens.mylist.MY_LIST_TAB_COUNT
import com.kitsugi.animelist.ui.screens.mylist.MY_LIST_SORT_DEFAULT
import com.kitsugi.animelist.ui.screens.mylist.duplicateListMessage
import kotlinx.coroutines.launch
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.kitsugi.animelist.utils.KitsugiStrings
import com.kitsugi.animelist.R
import dagger.hilt.android.EntryPointAccessors

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppTvSyncEntryPoint {
    fun channelSyncService(): com.kitsugi.animelist.core.recommendations.TvChannelSyncService
}

/**
 * Koordinatör ViewModel:
 *  - Tab seçimi & global arama
 *  - Snackbar mesajları
 *  - MyList filtre / sıralama / scroll state
 *  - Yedekleme (backup/import) text state
 *  - Listeye kayıt ekleme
 *  - Tema / liste görünümü / genel görsel ayarlar
 *  - Tüm liste silme
 *
 *  Auth → AuthViewModel
 *  Profil bilgisi & görseller → ProfileViewModel
 *  Oynatıcı ayarları → PlayerSettingsViewModel
 */
class AppViewModel : ViewModel() {

    // ── Navigation ────────────────────────────────────────────────────────────

    var selectedTab by mutableStateOf(MainTab.Explore)
        private set

    var showGlobalSearch by mutableStateOf(false)
        private set

    internal var requestedSettingsRoute by mutableStateOf<com.kitsugi.animelist.ui.screens.settings.SettingsRoute?>(null)
        private set

    internal fun requestSettingsRoute(route: com.kitsugi.animelist.ui.screens.settings.SettingsRoute) {
        requestedSettingsRoute = route
    }

    internal fun consumeSettingsRoute(route: com.kitsugi.animelist.ui.screens.settings.SettingsRoute) {
        if (requestedSettingsRoute == route) requestedSettingsRoute = null
    }

    // ── Backup / Import ───────────────────────────────────────────────────────

    var backupText by mutableStateOf("")
        private set

    var importText by mutableStateOf("")
        private set

    var importMode by mutableStateOf(BackupImportMode.Replace)
        private set

    // ── Snackbar ──────────────────────────────────────────────────────────────

    var snackbarMessage by mutableStateOf<String?>(null)
        private set

    var snackbarLong by mutableStateOf(false)
        private set

    // ── MyList filters / sort / scroll ────────────────────────────────────────

    var myListSearchQuery by mutableStateOf("")
        private set

    var myListStatusFilterId by mutableStateOf("all")
        private set

    var myListTypeFilterId by mutableStateOf("all")
        private set

    var myListFavoriteFilterId by mutableStateOf("all")
        private set

    var myListScoreFilterId by mutableStateOf("all")
        private set

    var myListYearFilterId by mutableStateOf("all")
        private set

    var myListExtraFilterId by mutableStateOf("all")
        private set

    var myListSortId by mutableStateOf(MY_LIST_SORT_DEFAULT)
        private set

    /**
     * Liste gruplaması — sıralamadan BAĞIMSIZ eksen.
     *
     * `added` (varsayılan): Bugün / Dün / Bu Hafta / Bu Ay … bölümleri.
     * `status`: İzleniyor / Planlandı / İzlendi … bölümleri.
     * `none`  : tek düz akış.
     * Seçilen sıralama bu gruplamayı kaldırmaz; yalnızca bölümlerin içini sıralar.
     */
    var myListGroupId by mutableStateOf("added")
        private set

    // Scroll konumları bilinçli olarak Compose state'i DEĞİLDİR: bu değerler
    // kaydırma sırasında HER KARE yazılır ve yalnızca ekran yeniden kurulurken
    // "ilk değer" olarak okunur. mutableIntStateOf olduklarında her kaydırma
    // karesi tüm sekmeyi yeniden kompoze edip Keşfet/Listem sayfalarını
    // kilitliyordu (geri besleme döngüsü). Düz değişkenler yazma anında
    // hiçbir kompozisyonu geçersiz kılmaz.
    var myListScrollIndex: Int = 0
        private set

    var myListScrollOffset: Int = 0
        private set

    var myListTabIndex by mutableIntStateOf(0)
        private set

    var exploreScrollIndex: Int = 0
        private set

    var exploreScrollOffset: Int = 0
        private set

    private val tabHistory = java.util.ArrayList<MainTab>()

    var hasSetInitialTab: Boolean = false
        internal set

    fun setInitialTab(tab: MainTab) {
        if (!hasSetInitialTab) {
            hasSetInitialTab = true
            selectedTab = tab
        }
    }

    fun selectTab(tab: MainTab) {
        if (selectedTab != tab) {
            tabHistory.remove(tab)
            tabHistory.add(selectedTab)
            selectedTab = tab
        }
    }

    fun popTabHistory(): Boolean {
        if (tabHistory.isNotEmpty()) {
            val prev = tabHistory.removeAt(tabHistory.size - 1)
            selectedTab = prev
            return true
        }
        return false
    }

    fun openGlobalSearch() {
        showGlobalSearch = true
    }

    fun closeGlobalSearch() {
        showGlobalSearch = false
    }

    // ── Snackbar ──────────────────────────────────────────────────────────────

    fun showSnackbarMessage(message: String, long: Boolean = false) {
        snackbarLong = long
        snackbarMessage = message
    }

    fun clearSnackbarMessage() {
        snackbarMessage = null
        snackbarLong = false
    }

    // ── MyList state persistence ──────────────────────────────────────────────

    fun loadFilters(context: Context) {
        val prefs = context.getSharedPreferences("Kitsugi_list_filters", Context.MODE_PRIVATE)
        myListStatusFilterId = prefs.getString("status_filter", "all") ?: "all"
        myListTypeFilterId = prefs.getString("type_filter", "all") ?: "all"
        myListFavoriteFilterId = prefs.getString("favorite_filter", "all") ?: "all"
        myListScoreFilterId = prefs.getString("score_filter", "all") ?: "all"
        myListYearFilterId = prefs.getString("year_filter", "all") ?: "all"
        myListExtraFilterId = prefs.getString("extra_filter", "all") ?: "all"
        // Sıralama sözleşmesi değişti (2026-10-23): eskiden "newest" hem varsayılan
        // hem de "Temizle" hedefiydi; kayıtlı değer kullanıcının bilinçli seçimi
        // olmayabilir. Bir kez: kayıtlı "newest" gerçek varsayılana (durum bölümlü
        // gezinme) taşınır ki güncelleme sonrası herkesin Listem görünümü aniden
        // değişmesin — "Son eklenen"i isteyen tek dokunuşla yeniden seçer ve artık
        // gerçek eklenme tarihine göre, global olarak çalışır.
        myListSortId = migrateLegacyDefaultSort(prefs)
        myListGroupId = prefs.getString("group", "added") ?: "added"
        // Kayıtlı sekme indeksi DOĞRUDAN geri yüklenir (mevcut sıra: 0 = Tümü … 6 = Bangumi).
        // Eski sıra göçü (migrateLegacyMyListTabIndex) bilinçli olarak kaldırıldı: koşulsuz
        // olarak her açılışta uygulanıyor ve son seçilen sekmeyi bir sıra kaydırıyordu
        // (uygulama kapatılıp açılınca seçim hep değişiyordu; Bangumi 6 → "Tümü", "Tümü" →
        // AniList, AniList → MAL …). Kayıtlar bu hatadan beri zaten güncel sıraya göre
        // yazılıyor; geriye dönük taşımaya gerek yoktur.
        val storedTabIndex = prefs.getInt("tab_index", -1)
        val restoredTabIndex = if (storedTabIndex < 0) {
            // Yeni kurulum: hesap bağlıysa doğrudan birleşik "Tümü" kütüphanesi açılır,
            // hiç hesap yoksa bağlantı çağrısının görüneceği AniList sekmesi gösterilir.
            if (hasAnyConnectedAccount(context)) MY_LIST_ALL_TAB_INDEX else MY_LIST_ANILIST_TAB_INDEX
        } else {
            storedTabIndex.coerceIn(0, MY_LIST_TAB_COUNT - 1)
        }
        myListTabIndex = restoredTabIndex
        if (restoredTabIndex != storedTabIndex) {
            prefs.edit().putInt("tab_index", restoredTabIndex).apply()
        }
    }

    /**
     * Kayıtlı sıralama seçimini okur; eski varsayılan ("newest") yalnızca İLK
     * okumada yeni varsayılana taşınır. Taşıma bayrağı yazıldıktan sonra kayıtlı
     * değer (kullanıcının yeni sözleşmeyle bilinçli seçtiği "newest" dahil)
     * aynen geri yüklenir — koşulsuz taşıma, sekme göçündeki hatanın aynısı olurdu.
     */
    private fun migrateLegacyDefaultSort(prefs: android.content.SharedPreferences): String {
        // Kayıt yoksa yazma da yok: taze kurulum doğrudan yeni varsayılanla açılır.
        val stored = prefs.getString("sort", null) ?: return MY_LIST_SORT_DEFAULT
        val alreadyMigrated = prefs.getBoolean("sort_default_v2_done", false)
        if (!alreadyMigrated && (stored == "newest" || stored == "added")) {
            prefs.edit()
                .putString("sort", MY_LIST_SORT_DEFAULT)
                .putBoolean("sort_default_v2_done", true)
                .apply()
            return MY_LIST_SORT_DEFAULT
        }
        // Başka bir bilinçli seçim kayıtlı: dokunma. Bayrak, kullanıcı sıralamayı
        // bir sonraki değiştirişinde updateMyListSort tarafından yazılır; o ana
        // kadar kayıtlı değer "newest" olamayacağı için yanlış göç riski yoktur.
        return stored.takeIf { it.isNotBlank() } ?: MY_LIST_SORT_DEFAULT
    }

    fun updateMyListTabIndex(context: Context, index: Int) {
        val safeIndex = index.coerceIn(0, MY_LIST_TAB_COUNT - 1)
        myListTabIndex = safeIndex
        resetMyListScroll()
        if (safeIndex == MY_LIST_SIMKL_TAB_INDEX && myListTypeFilterId == "manga") {
            updateMyListTypeFilter(context, "all")
        }
        context.getSharedPreferences("Kitsugi_list_filters", Context.MODE_PRIVATE)
            .edit().putInt("tab_index", safeIndex).apply()
    }

    private fun hasAnyConnectedAccount(context: Context): Boolean {
        val auth = com.kitsugi.animelist.data.auth.ExternalAuthManager
        return auth.getAniListToken(context) != null ||
            auth.getMalToken(context) != null ||
            auth.getSimklToken(context) != null ||
            auth.getKitsuToken(context) != null ||
            auth.getShikimoriToken(context) != null
    }

    fun updateMyListScrollPosition(index: Int, offset: Int) {
        myListScrollIndex = index
        myListScrollOffset = offset
    }

    fun updateExploreScrollPosition(index: Int, offset: Int) {
        exploreScrollIndex = index
        exploreScrollOffset = offset
    }

    fun updateMyListSearchQuery(value: String) {
        myListSearchQuery = value
        resetMyListScroll()
    }

    fun updateMyListStatusFilter(context: Context, value: String) {
        myListStatusFilterId = value
        resetMyListScroll()
        context.getSharedPreferences("Kitsugi_list_filters", Context.MODE_PRIVATE)
            .edit().putString("status_filter", value).apply()
    }

    fun updateMyListTypeFilter(context: Context, value: String) {
        myListTypeFilterId = value
        resetMyListScroll()
        context.getSharedPreferences("Kitsugi_list_filters", Context.MODE_PRIVATE)
            .edit().putString("type_filter", value).apply()
    }

    fun updateMyListFavoriteFilter(context: Context, value: String) {
        myListFavoriteFilterId = value
        resetMyListScroll()
        context.getSharedPreferences("Kitsugi_list_filters", Context.MODE_PRIVATE)
            .edit().putString("favorite_filter", value).apply()
    }

    fun updateMyListScoreFilter(context: Context, value: String) {
        myListScoreFilterId = value
        resetMyListScroll()
        context.getSharedPreferences("Kitsugi_list_filters", Context.MODE_PRIVATE)
            .edit().putString("score_filter", value).apply()
    }

    fun updateMyListYearFilter(context: Context, value: String) {
        myListYearFilterId = value
        resetMyListScroll()
        context.getSharedPreferences("Kitsugi_list_filters", Context.MODE_PRIVATE)
            .edit().putString("year_filter", value).apply()
    }

    fun updateMyListExtraFilter(context: Context, value: String) {
        myListExtraFilterId = value
        resetMyListScroll()
        context.getSharedPreferences("Kitsugi_list_filters", Context.MODE_PRIVATE)
            .edit().putString("extra_filter", value).apply()
    }

    fun updateMyListSort(context: Context, value: String) {
        myListSortId = value
        resetMyListScroll()
        context.getSharedPreferences("Kitsugi_list_filters", Context.MODE_PRIVATE)
            .edit()
            .putString("sort", value)
            // Bilinçli seçim işareti: eski varsayılan göçü bu değeri bir daha taşımasın.
            .putBoolean("sort_default_v2_done", true)
            .apply()
    }

    fun updateMyListGroup(context: Context, value: String) {
        myListGroupId = value
        resetMyListScroll()
        context.getSharedPreferences("Kitsugi_list_filters", Context.MODE_PRIVATE)
            .edit().putString("group", value).apply()
    }

    private fun resetMyListScroll() {
        myListScrollIndex = 0
        myListScrollOffset = 0
    }

    // ── Backup / Import text state ────────────────────────────────────────────

    fun clearBackupText() {
        backupText = ""
    }

    fun updateImportText(value: String) {
        importText = value
    }

    fun clearImportText() {
        importText = ""
    }

    fun updateImportMode(value: BackupImportMode) {
        importMode = value
    }

    fun createBackupText(currentEntries: List<MediaEntry>) {
        backupText = MediaEntryBackup.exportToJson(currentEntries)
    }

    fun importBackupText(currentEntries: List<MediaEntry>, repository: MediaEntryRepository) {
        importBackupJsonText(
            jsonText = importText,
            currentEntries = currentEntries,
            repository = repository
        )
        clearImportText()
    }

    fun importBackupJsonText(
        jsonText: String,
        currentEntries: List<MediaEntry>,
        repository: MediaEntryRepository
    ) {
        runCatching {
            MediaEntryBackup.importFromJson(jsonText)
        }.onSuccess { importedEntries ->
            val entriesToSave = when (importMode) {
                BackupImportMode.Replace -> importedEntries
                BackupImportMode.Append -> MediaEntryBackup.mergeWithoutApiDuplicates(
                    currentEntries = currentEntries,
                    importedEntries = importedEntries
                )
            }

            viewModelScope.launch {
                when (importMode) {
                    BackupImportMode.Replace -> repository.replaceAll(entriesToSave)
                    BackupImportMode.Append -> repository.insertAll(entriesToSave)
                }

                val message = when (importMode) {
                    BackupImportMode.Replace -> KitsugiStrings.get(R.string.x_kayit_dosyadan_ice_aktarildi, entriesToSave.size)
                    BackupImportMode.Append -> "${entriesToSave.size} yeni kayıt dosyadan eklendi"
                }
                showSnackbarMessage(message)
            }
        }.onFailure { error ->
            showSnackbarMessage(error.message ?: KitsugiStrings.get(R.string.yedek_dosyasi_ice_aktarilamadi))
        }
    }

    // ── Add API selection to list ─────────────────────────────────────────────

    private enum class ApiInsertStatus { ADDED, DUPLICATE, FAILED }

    /** [insertApiSelection] sonucu: durum + kullanıcıya gösterilecek mesaj + eklenen kayıt. */
    private data class ApiInsertOutcome(
        val status: ApiInsertStatus,
        val message: String,
        val entry: MediaEntry? = null
    )

    /**
     * Tek bir API seçimini listeye ekler; bildirimi kendisi GÖSTERMEZ, ne olduğunu
     * [ApiInsertOutcome] olarak döner. Tekil ekleme ([addApiSelectionToList]) ve toplu
     * ekleme ([addApiSelectionsToList]) aynı doğrulama/çözümleme zincirini paylaşır.
     */
    private suspend fun insertApiSelection(
        selection: ApiSearchSelection,
        currentEntries: List<MediaEntry>,
        repository: MediaEntryRepository,
        isAniListConnected: Boolean,
        isMalConnected: Boolean,
        isSimklConnected: Boolean,
        isTmdbConnected: Boolean,
        isBangumiConnected: Boolean,
        isKitsuConnected: Boolean,
        isShikimoriConnected: Boolean,
        markFavorite: Boolean = false,
        preferredSource: String? = null
    ): ApiInsertOutcome {
        val result = selection.result
        val prefersTmdbAccount = preferredSource == com.kitsugi.animelist.data.auth.TMDB_ACCOUNT_SOURCE
        if (prefersTmdbAccount && !isTmdbConnected) {
            return ApiInsertOutcome(
                ApiInsertStatus.FAILED,
                KitsugiStrings.get(R.string.tmdb_account_required)
            )
        }
        if (prefersTmdbAccount && !com.kitsugi.animelist.data.auth.supportsTmdbAccountType(result.type)) {
            return ApiInsertOutcome(
                ApiInsertStatus.FAILED,
                KitsugiStrings.get(R.string.tmdb_identity_unresolved)
            )
        }

        if (!prefersTmdbAccount && canonicalMediaSourceId(result.source) == "bangumi") {
            if (!isBangumiConnected) {
                return ApiInsertOutcome(
                    ApiInsertStatus.FAILED,
                    "Bangumi listesine eklemek için önce Bangumi hesabını bağlamalısın."
                )
            }
            val existingBangumiEntry = currentEntries.firstOrNull { it.matchesInSource(result) }
            if (existingBangumiEntry != null) {
                if (markFavorite && !existingBangumiEntry.isFavorite) {
                    repository.update(existingBangumiEntry.copy(isFavorite = true))
                }
                return ApiInsertOutcome(ApiInsertStatus.DUPLICATE, duplicateListMessage(existingBangumiEntry))
            }
            val newBangumiEntry = BangumiMediaEntryFactory.fromSearchResult(result, selection.synopsis)?.let {
                if (markFavorite) it.copy(isFavorite = true) else it
            }
            if (newBangumiEntry == null) {
                return ApiInsertOutcome(
                    ApiInsertStatus.FAILED,
                    "Bangumi kaydı için geçerli bir subject kimliği ve başlık gerekli."
                )
            }
            if (repository.context == null) {
                return ApiInsertOutcome(
                    ApiInsertStatus.FAILED,
                    "Bangumi hesabına ulaşılamadı; kayıt eklenemedi."
                )
            }
            // Diğer kaynaklarla aynı hat: kayıt önce yerele yazılır, canlı eşitleme Bangumi
            // dahil yedi kaynağa birden yayar. Eski akış önce Bangumi'ye yazıp başarısızlıkta
            // kaydı hiç eklemiyordu (token/kapsam/ağ hatasında ekleme tümüyle kayboluyordu);
            // artık başarısız yazımlar bekleyen senkron kuyruğunda yeniden denenir ve sonuç
            // repository geri çağrısıyla kullanıcıya bildirilir.
            repository.insert(newBangumiEntry, syncExternal = true)
            return ApiInsertOutcome(
                ApiInsertStatus.ADDED,
                "Listeye eklendi: ${newBangumiEntry.title}",
                newBangumiEntry
            )
        }

        // En az bir kaynak bağlı olmalı — Kitsu / Shikimori / Bangumi de artık tek başına yeterli,
        // çünkü ekleme sonrası çapraz eşitleme altı kaynağın tamamına yayılıyor.
        val anySourceConnected = isAniListConnected || isMalConnected || isSimklConnected ||
            isKitsuConnected || isShikimoriConnected || isBangumiConnected || isTmdbConnected
        if (!anySourceConnected) {
            return ApiInsertOutcome(
                ApiInsertStatus.FAILED,
                KitsugiStrings.get(R.string.listeye_eklemek_icin_en_az_bir_hesap_baglamalisin_anilist_mal_ve)
            )
        }

        // Film/dizi: Simkl bağlı değilse Bangumi (REAL türü) üzerinden de izlenebilir.
        if ((result.type == MediaType.TvShow || result.type == MediaType.Movie) && !isSimklConnected && !isBangumiConnected && !isTmdbConnected) {
            return ApiInsertOutcome(
                ApiInsertStatus.FAILED,
                KitsugiStrings.get(R.string.film_ve_dizileri_eklemek_icin_once_simkl_hesabini_baglamalisin)
            )
        }
        if (result.type == MediaType.Anime && !isAniListConnected && !isMalConnected &&
            !isKitsuConnected && !isShikimoriConnected
        ) {
            return ApiInsertOutcome(
                ApiInsertStatus.FAILED,
                KitsugiStrings.get(R.string.anime_eklemek_icin_anilist_veya_myanimelist_hesabini_baglamalisi)
            )
        }

        // Kaynak belirleme: unified entry'nin hangi source olacağını seç
        val resultSource = com.kitsugi.animelist.data.remote.canonicalMediaSourceId(result.source)
        val primarySource = resolvePrimarySourceForApiResult(
            result = result,
            isAniListConnected = isAniListConnected,
            isMalConnected = isMalConnected,
            isSimklConnected = isSimklConnected,
            isBangumiConnected = isBangumiConnected,
            isKitsuConnected = isKitsuConnected,
            isShikimoriConnected = isShikimoriConnected,
            isTmdbConnected = isTmdbConnected,
            preferredSource = preferredSource
        )

        // Provider listeleri birbirinden bağımsızdır: başka bir kaynaktaki kopya bu kaynağın
        // ekleme işlemini engellememeli; ancak YAZILACAK hedef kaynakta (`primarySource`) zaten
        // kayıt varsa (örn. TMDB ekranından Simkl'a eklenmiş film) çift satır açılmamalıdır.
        val targetResult = if (primarySource == com.kitsugi.animelist.data.auth.TMDB_ACCOUNT_SOURCE) {
            val targetId = result.tmdbId ?: result.malId.takeIf { resultSource == "tmdb" && it > 0 }
            result.copy(
                source = com.kitsugi.animelist.data.auth.TMDB_ACCOUNT_SOURCE,
                malId = targetId ?: 0,
                tmdbId = targetId
            )
        } else result
        val existingEntry = currentEntries.firstMatchingInTargetOrResultSource(targetResult, primarySource)
        if (existingEntry != null) {
            if (markFavorite && !existingEntry.isFavorite) {
                repository.update(existingEntry.copy(isFavorite = true))
            }
            return ApiInsertOutcome(ApiInsertStatus.DUPLICATE, duplicateListMessage(existingEntry))
        }

        // 1. ARM üzerinden cross-platform ID'leri çöz
        val rawMalId = result.realMalId
            ?: if (result.source.equals("jikan", ignoreCase = true) || result.source.equals("mal", ignoreCase = true))
                result.malId.takeIf { it in 1 until 100_000_000 }
            else null
        val rawAniListId = if (result.source.equals("anilist", ignoreCase = true) && result.malId >= 100_000_000)
            result.malId - 100_000_000
        else null
        // Legacy TMDB results keep their TMDB id in `malId`. Never reinterpret that number
        // as MAL; use it only to resolve/fill the real TMDB field.
        val rawTmdbId = result.tmdbId ?: result.malId.takeIf {
            resultSource == "tmdb" && it > 0
        }
        val resultMalIdFallback = result.malId.takeIf {
            resultSource != "tmdb" && it in 1 until 100_000_000
        }

        val resolvedIds = runCatching {
            com.kitsugi.animelist.data.remote.KitsugiIdResolver.resolveIds(
                malId = rawMalId,
                aniListId = rawAniListId,
                tmdbId = rawTmdbId,
                mediaType = result.type
            )
        }.getOrNull()

        val finalMalId = resolvedIds?.malId ?: rawMalId ?: resultMalIdFallback
        val finalTmdbId = resolvedIds?.tmdbId ?: rawTmdbId
        if (primarySource == com.kitsugi.animelist.data.auth.TMDB_ACCOUNT_SOURCE && finalTmdbId == null) {
            return ApiInsertOutcome(
                ApiInsertStatus.FAILED,
                KitsugiStrings.get(R.string.tmdb_identity_unresolved)
            )
        }

        // Çözülmüş kimliklerle ikinci kontrol: kullanıcı detay sayfasında zenginleştirme
        // (realMalId / tmdbId) tamamlanmadan tıkladıysa ön kontrol kaçmış olabilir; bu
        // kontrol çift kaydı engeller ve yine BULUNAN kaydı isimlendirir.
        val resolvedProbe = targetResult.copy(
            realMalId = result.realMalId ?: finalMalId,
            malId = if (primarySource == com.kitsugi.animelist.data.auth.TMDB_ACCOUNT_SOURCE) {
                finalTmdbId ?: targetResult.malId
            } else targetResult.malId,
            tmdbId = targetResult.tmdbId ?: finalTmdbId
        )
        val existingAfterResolve = currentEntries.firstMatchingInTargetOrResultSource(resolvedProbe, primarySource)
        if (existingAfterResolve != null) {
            if (markFavorite && !existingAfterResolve.isFavorite) {
                repository.update(existingAfterResolve.copy(isFavorite = true))
            }
            return ApiInsertOutcome(ApiInsertStatus.DUPLICATE, duplicateListMessage(existingAfterResolve))
        }

        // 2. Simkl bağlıysa Simkl ID'sini çöz
        val resolvedSimklId: Int? = if (isSimklConnected) {
            runCatching {
                com.kitsugi.animelist.data.remote.SimklApiClient().lookupSimklId(
                    malId = finalMalId,
                    tmdbId = finalTmdbId,
                    aniListId = rawAniListId ?: resolvedIds?.aniListId,
                    title = result.titleEnglish ?: result.title,
                    year = result.year,
                    mediaType = result.type
                )
            }.getOrNull()
        } else null

        // Simkl ID çözüldükten sonra da mevcut kayıt kontrolü (farklı başlık/yıl varyantı için)
        if (resolvedSimklId != null && resolvedSimklId > 0) {
            val existingBySimkl = currentEntries.firstOrNull { entry ->
                com.kitsugi.animelist.data.remote.canonicalMediaSourceId(entry.source) ==
                    com.kitsugi.animelist.data.remote.canonicalMediaSourceId(primarySource) &&
                    entry.simklId == resolvedSimklId
            }
            if (existingBySimkl != null) {
                if (markFavorite && !existingBySimkl.isFavorite) {
                    repository.update(existingBySimkl.copy(isFavorite = true))
                }
                return ApiInsertOutcome(ApiInsertStatus.DUPLICATE, duplicateListMessage(existingBySimkl))
            }
        }

        // 3. Unified MediaEntry oluştur — tüm çözümlenen ID'leri tek bir kayıtta birleştir
        //
        // Varsayılan ekleme durumu (Ayarlar → Tercihler → Liste). Kullanıcı
        // "İzliyorum"u seçtiyse otomatik tarih kuralı da devreye girer: yapım,
        // düzenleyici paneli hiç açılmadan bugünün başlangıç tarihiyle listeye girer.
        val defaultStatus = KitsugiContentPrefs.defaultAddStatusValue()
        val auto = EntryAutoRules.resolveOnStatusChange(
            status = defaultStatus,
            startDate = null,
            endDate = null,
            progress = 0,
            total = result.total,
            // Yeni kayıt: durum "değişti" sayılır → varsayılan durum "İzledim" ise
            // ilerleme doğrudan toplam bölüme yazılır.
            statusChanged = true
        )

        val newEntry = MediaEntry(
            id = 0,
            title = result.title,
            subtitle = result.subtitle,
            type = result.type,
            status = defaultStatus,
            // Yeni eklenen kayıtlar puan sıfırdan başlamalı;
            // API metadata puanı (result.score) kullanıcının kişisel puanı değildir.
            score = null,
            progress = auto.progress,
            total = result.total,
            isFavorite = markFavorite,
            // Choosing the TMDB account tab is an explicit Watchlist-add intent. Kitsugi's
            // local Watching/Completed default must not turn that into a remote no-op.
            tmdbWatchlist = if (primarySource == com.kitsugi.animelist.data.auth.TMDB_ACCOUNT_SOURCE) true else null,
            isAdult = result.isAdult,
            source = primarySource,
            // Satır kendi kaynağının kimliğini taşımaya devam eder (Kitsu 300M, Shikimori 400M
            // aralığı); çözülen gerçek MAL ID yalnızca uzak çağrılarda kullanılır.
            malId = when (primarySource) {
                com.kitsugi.animelist.data.auth.TMDB_ACCOUNT_SOURCE -> null
                "kitsu" -> result.malId.takeIf { it in 300_000_001..399_999_999 }
                    ?: finalMalId ?: result.malId.takeIf { it > 0 }
                "shikimori" -> result.malId.takeIf { it > 0 } ?: finalMalId
                else -> finalMalId ?: resultMalIdFallback
            },
            imageUrl = result.imageUrl,
            year = result.year,
            synopsis = selection.synopsis,
            startDate = auto.startDate,
            endDate = auto.endDate,
            tmdbId = finalTmdbId,
            simklId = resolvedSimklId,
            aniListEntryId = null, // AniList sync sonrası DB'ye yazılacak
            titleEnglish = result.titleEnglish,
            titleJapanese = result.titleJapanese
        )

        repository.insert(newEntry)
        return ApiInsertOutcome(
            ApiInsertStatus.ADDED,
            KitsugiStrings.get(
                when (defaultStatus) {
                    WatchStatus.Watching  -> R.string.izliyor_olarak_eklendi
                    WatchStatus.Completed -> R.string.izlendi_olarak_eklendi
                    else                  -> R.string.listeye_eklendi_isim
                },
                newEntry.title
            ),
            newEntry
        )
    }

    /**
     * Tekil listeye ekleme: [insertApiSelection] sonucunu tek bir bildirim olarak gösterir.
     */
    fun addApiSelectionToList(
        selection: ApiSearchSelection,
        currentEntries: List<MediaEntry>,
        repository: MediaEntryRepository,
        isAniListConnected: Boolean,
        isMalConnected: Boolean,
        isSimklConnected: Boolean,
        isTmdbConnected: Boolean = false,
        isBangumiConnected: Boolean = false,
        isKitsuConnected: Boolean = false,
        isShikimoriConnected: Boolean = false,
        markFavorite: Boolean = false,
        preferredSource: String? = null
    ) {
        viewModelScope.launch {
            val outcome = insertApiSelection(
                selection = selection,
                currentEntries = currentEntries,
                repository = repository,
                isAniListConnected = isAniListConnected,
                isMalConnected = isMalConnected,
                isSimklConnected = isSimklConnected,
                isTmdbConnected = isTmdbConnected,
                isBangumiConnected = isBangumiConnected,
                isKitsuConnected = isKitsuConnected,
                isShikimoriConnected = isShikimoriConnected,
                markFavorite = markFavorite,
                preferredSource = preferredSource
            )
            showSnackbarMessage(outcome.message)
        }
    }

    /**
     * "Tümünü gör" sayfasının toplu kısayolu: görünen tüm sonuçları sırayla listeye ekler
     * ve TEK bir özet bildirim gösterir (kalem başına bildirim yerine).
     *
     * Aynı sayfada tekrar eden başlıklar da dahil olmak üzere her kalem [insertApiSelection]
     * zincirinden geçer; eklenen kayıtlar birikir, böylece grup içinde mükerrer engellenir.
     */
    fun addApiSelectionsToList(
        selections: List<ApiSearchSelection>,
        currentEntries: List<MediaEntry>,
        repository: MediaEntryRepository,
        isAniListConnected: Boolean,
        isMalConnected: Boolean,
        isSimklConnected: Boolean,
        isTmdbConnected: Boolean = false,
        isBangumiConnected: Boolean = false,
        isKitsuConnected: Boolean = false,
        isShikimoriConnected: Boolean = false
    ) {
        if (selections.isEmpty()) return
        showSnackbarMessage(KitsugiStrings.get(R.string.toplu_listeye_ekleniyor, selections.size))
        viewModelScope.launch {
            var added = 0
            var duplicated = 0
            var failed = 0
            var firstFailure: String? = null
            val accumulated = currentEntries.toMutableList()
            for (selection in selections) {
                // Bir kalemin beklenmedik hatası (ör. DB yazım hatası) tüm toplu işlemi
                // öldürmesin: kalem başarısız sayılır, özet yine de gösterilir.
                val outcome = try {
                    insertApiSelection(
                        selection = selection,
                        currentEntries = accumulated,
                        repository = repository,
                        isAniListConnected = isAniListConnected,
                        isMalConnected = isMalConnected,
                        isSimklConnected = isSimklConnected,
                        isTmdbConnected = isTmdbConnected,
                        isBangumiConnected = isBangumiConnected,
                        isKitsuConnected = isKitsuConnected,
                        isShikimoriConnected = isShikimoriConnected
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ApiInsertOutcome(
                        ApiInsertStatus.FAILED,
                        e.message ?: KitsugiStrings.get(R.string.ui_error_generic_period)
                    )
                }
                when (outcome.status) {
                    ApiInsertStatus.ADDED -> {
                        added += 1
                        outcome.entry?.let { accumulated.add(it) }
                    }
                    ApiInsertStatus.DUPLICATE -> duplicated += 1
                    ApiInsertStatus.FAILED -> {
                        failed += 1
                        if (firstFailure == null) firstFailure = outcome.message
                    }
                }
            }
            val failureMessage = firstFailure
            val message = when {
                added == 0 && failed > 0 -> failureMessage ?: KitsugiStrings.get(R.string.ui_error_generic_period)
                added == 0 -> KitsugiStrings.get(R.string.tumu_zaten_listede)
                else -> KitsugiStrings.get(R.string.toplu_listeye_ekleme_sonuc, added, duplicated, failed)
            }
            showSnackbarMessage(message)
        }
    }

    // ── Visual / general settings (delegates to SettingsDataStore) ────────────

    fun updateTheme(
        themeId: String,
        settingsDataStore: SettingsDataStore
    ) {
        viewModelScope.launch {
            settingsDataStore.setSelectedThemeId(themeId)
            showSnackbarMessage(KitsugiStrings.get(R.string.tema_guncellendi))
        }
    }

    fun updateListLayout(
        layoutId: String,
        settingsDataStore: SettingsDataStore
    ) {
        viewModelScope.launch {
            settingsDataStore.setSelectedListLayoutId(layoutId)
            showSnackbarMessage(KitsugiStrings.get(R.string.liste_gorunumu_guncellendi))
        }
    }

    fun updateTitleLanguage(
        titleLanguage: String,
        settingsDataStore: SettingsDataStore
    ) {
        viewModelScope.launch {
            settingsDataStore.setTitleLanguage(titleLanguage)
            showSnackbarMessage(KitsugiStrings.get(R.string.baslik_dili_guncellendi))
        }
    }

    fun updateScoreFormat(
        scoreFormat: String,
        settingsDataStore: SettingsDataStore
    ) {
        viewModelScope.launch {
            settingsDataStore.setScoreFormat(scoreFormat)
            showSnackbarMessage(KitsugiStrings.get(R.string.puan_formati_guncellendi))
        }
    }

    fun updateHideScores(
        hide: Boolean,
        settingsDataStore: SettingsDataStore
    ) {
        viewModelScope.launch {
            settingsDataStore.setHideScores(hide)
            showSnackbarMessage(if (hide) "Puanlar gizlendi" else KitsugiStrings.get(R.string.puanlar_gosteriliyor))
        }
    }

    fun updateShowAnimeLogos(
        show: Boolean,
        settingsDataStore: SettingsDataStore
    ) {
        viewModelScope.launch {
            settingsDataStore.setShowAnimeLogos(show)
            showSnackbarMessage(
                if (show) KitsugiStrings.get(R.string.anime_logolari_etkinlestirildi) else KitsugiStrings.get(R.string.anime_logolari_devre_disi_birakildi)
            )
        }
    }

    fun updateDnsChoice(
        choice: Int,
        settingsDataStore: SettingsDataStore
    ) {
        viewModelScope.launch {
            settingsDataStore.setDnsChoice(choice)
            com.kitsugi.animelist.core.network.DnsManager.dnsChoice = choice
            showSnackbarMessage(KitsugiStrings.get(R.string.dns_ayari_guncellendi))
        }
    }

    // ── Delete all entries ────────────────────────────────────────────────────

    fun deleteAllEntries(repository: MediaEntryRepository) {
        viewModelScope.launch {
            repository.deleteAll()
            showSnackbarMessage(KitsugiStrings.get(R.string.liste_temizlendi))

            // B1.12: Clear TV launcher channels/recommendations
            val ctx = repository.context
            if (ctx != null) {
                try {
                    val entryPoint = EntryPointAccessors.fromApplication(
                        ctx.applicationContext, AppTvSyncEntryPoint::class.java
                    )
                    entryPoint.channelSyncService().clearAll()
                } catch (e: Exception) {
                    android.util.Log.w("AppViewModel", "Failed to clear TV channel during deleteAllEntries", e)
                }
            }
        }
    }

    companion object {
        fun resolvePrimarySourceForApiResult(
            result: com.kitsugi.animelist.data.remote.JikanSearchResult,
            isAniListConnected: Boolean,
            isMalConnected: Boolean,
            isSimklConnected: Boolean,
            isBangumiConnected: Boolean,
            isKitsuConnected: Boolean,
            isShikimoriConnected: Boolean,
            isTmdbConnected: Boolean = false,
            preferredSource: String? = null
        ): String {
            val prefersTmdbAccount = preferredSource == com.kitsugi.animelist.data.auth.TMDB_ACCOUNT_SOURCE
            val resultSource = canonicalMediaSourceId(result.source)
            return when {
                prefersTmdbAccount && isTmdbConnected && com.kitsugi.animelist.data.auth.supportsTmdbAccountType(result.type) ->
                    com.kitsugi.animelist.data.auth.TMDB_ACCOUNT_SOURCE
                result.type == MediaType.TvShow || result.type == MediaType.Movie -> when {
                    prefersTmdbAccount && isTmdbConnected -> com.kitsugi.animelist.data.auth.TMDB_ACCOUNT_SOURCE
                    resultSource == "tmdb" && isTmdbConnected -> com.kitsugi.animelist.data.auth.TMDB_ACCOUNT_SOURCE
                    isSimklConnected -> "simkl"
                    isBangumiConnected -> com.kitsugi.animelist.data.remote.BangumiIdNamespace.SOURCE
                    isTmdbConnected -> com.kitsugi.animelist.data.auth.TMDB_ACCOUNT_SOURCE
                    else -> "simkl"
                }
                resultSource == "anilist" && isAniListConnected -> "anilist"
                resultSource == "mal" && isMalConnected -> "mal"
                resultSource == "kitsu" && isKitsuConnected -> "kitsu"
                resultSource == "shikimori" && isShikimoriConnected -> "shikimori"
                resultSource == "bangumi" && isBangumiConnected -> com.kitsugi.animelist.data.remote.BangumiIdNamespace.SOURCE
                isAniListConnected -> "anilist"
                isMalConnected -> "mal"
                isSimklConnected -> "simkl"
                isKitsuConnected -> "kitsu"
                isShikimoriConnected -> "shikimori"
                isTmdbConnected && com.kitsugi.animelist.data.auth.supportsTmdbAccountType(result.type) ->
                    com.kitsugi.animelist.data.auth.TMDB_ACCOUNT_SOURCE
                else -> "simkl"
            }
        }
    }
}