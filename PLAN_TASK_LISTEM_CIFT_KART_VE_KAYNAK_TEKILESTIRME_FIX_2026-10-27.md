# PLAN & TASK — Listem (`Tümü` + Tekil Sağlayıcı Sekmeleri + TV + Profil) Çift Kart / Çoklu Kopya Kök Neden Analizi ve Kalıcı Çözümü (2026-10-27)

## 1. Kapsam ve Kullanıcı Gözlemi

Kullanıcı geri bildirimi ve ekran görüntüleri üzerinden doğrulanan belirtiler:

1. **Tekil Kaynak Sekmesinde (`Listem → Simkl`) Birebir Çift Kart:**
   - `Demon Slayer: Kimetsu no Yaiba Infinity Castle` (`1/1`, `Tamamlandı`, `★ 8/10`, favori yıldızlı `★`, Türkçe TMDB vizyon posteri) `Simkl` sekmesinde yan yana **iki kez** görünüyordu.
2. **Birleşik Kütüphanede (`Listem → 🌐 Tümü`) Kaynaklararası Bölünme:**
   - Saat **21:19**'da `Attack on Titan` tek bir `AniList` (`A.`) kartında 5 rozetle (`Simkl`, `MAL`, `Bangumi`, `Shikimori`, `Kitsu`) birleşikken, `Kaiju No. 8 Season 2` **iki ayrı kart** (`Kitsu` kartı + 4 rozetli `AniList` kartı) olarak görünüyordu.
   - Saat **22:48**'de TMDB üzerinden film eklenip `Attack on Titan` favoriye alındıktan sonra, saat **23:39**'da `Attack on Titan` da **iki ayrı karta** bölündü (favori yıldızlı `AniList` + 4 rozetli kart ve favorisiz `Kitsu` kartı).

---

## 2. Tespit Edilen Kök Nedenler (Adli Kod İncelemesi)

### A. Tekil Sağlayıcı Sekmelerindeki (`Simkl`, `AniList`, `MAL`, `Kitsu`, `Shikimori`, `Bangumi`) Çift Kart Kök Nedenleri

1. **Kaynak Yönlendirme (`result.source` → `primarySource`) ve Tekrar Ekleme Kaçağı (`AppViewModel.insertApiSelection` & `AppRootDetailPages.onToggleFavoriteClick`):**
   - Kullanıcı Keşfet/Arama/Detay ekranından `result.source = "tmdb"` (veya `"jikan"`) olan bir yapımı listeye eklediğinde `insertApiSelection` kaydı `primarySource = "simkl"` (veya `"anilist"`) altına yazıyordu.
   - Ancak mevcut kayıt kontrolü `currentEntries.firstOrNull { it.matchesInSource(result) }` ile **yalnızca `result.source` (`"tmdb"`)** üzerinde yapılıyordu! `"simkl" != "tmdb"` olduğu için kontrol her seferinde `null` dönüyor ve her ekleme/favorileme tıklaması `Simkl` altında yeni bir satır açıyordu.
   - Ayrıca `result.source == "tmdb"` iken `finalMalId` yedek kolu `result.malId` (yani TMDB ID!) değerini `malId` sütununa sızdırabiliyordu.
   - `ApiResultDetailPage` içindeki `onToggleFavoriteClick` ise hem `firstMatchingInSource(selection.result)` yüzünden mevcut `Simkl`/`AniList` satırını bulamıyor, hem de `source = result.source` ve `score = result.score` (API topluluk puanı!) ile yetim kopya kayıt açıyordu.

2. **Anime Filmlerinde `MediaType.Movie` ↔ `MediaType.Anime` Çift Kayıt Çatallaşması (`SimklImportManager`, `MediaIdentity`, `MediaEntryRepository`):**
   - TMDB üzerinden eklenen `Demon Slayer: Kimetsu no Yaiba Infinity Castle` yerel tabloya `source = "simkl", type = MediaType.Movie` olarak kaydedilirken, Simkl `/sync/all-items/anime` uç noktası aynı yapımı `type = MediaType.Anime` olarak döndürüyordu.
   - `SimklImportManager.fetchAllLists` `movies + shows + anime` listelerini birleştirirken uç noktalar arası tekilleştirme yapmıyordu.
   - `MediaIdentity.sameMedia` farklı `MediaType` değerlerini (`Movie != Anime`) koşulsuz reddettiği için `smartImport` mevcut `Movie` satırını güncelleyemiyor ve yanına ikinci bir `Anime` satırı ekliyordu.

3. **Veritabanı Yazma Katmanında İdempotensi ve Yarış Koruması Eksikliği (`MediaEntryRepository` & `MediaEntryDao`):**
   - `MediaEntryRepository.insert` ve `insertAll` aynı `source` altında aynı yapım zaten var mı diye kontrol etmeden doğrudan `id = 0` ile yeni satır ekliyordu.
   - Açılış senkronu (`KitsugiApplication`), arka plan bildirim işçisi (`AiringNotificationWorker`) ve manuel içe aktarım (`AuthViewModel`) farklı `MediaEntryRepository` örnekleri üzerinden eşzamanlı `getAll() + insertAll()` çalıştırabiliyordu (`Mutex` yoktu ve `replaceSourcePreservingAdultFlags` atomik `@Transaction` değildi).
   - AniList dışındaki sağlayıcıların liste içe aktarma uçları favori bayrağını taşımadığı (`isFavorite = false`) halde `smartImport` ve `replaceSourcePreservingAdultFlags` yerel `isFavorite = true` yıldızını eziyordu.

4. **`MyListViewModel.repairMisclassifiedSources` ve `MyListScreen` Arama Sızıntısı:**
   - `repairMisclassifiedSources`, MAL token'ı yokken gerçek `source == "mal"` kayıtlarını bile mevcut `anilist` kayıtlarıyla çakışma kontrolü yapmadan `source = "anilist"` olarak yeniden adlandırıyordu.
   - `MyListScreen.kt` içinde tekil sağlayıcı sekmelerinde arama kutusuna metin yazıldığında (`searchQuery.isNotBlank() && selectedTabIndex != MY_LIST_BANGUMI_TAB_INDEX -> entriesAfterAdultFilter`), sekme filtresi devre dışı kalıp **6 sağlayıcının ham (gruplanmamış) tüm satırları** o sekmeye dökülüyordu.

---

### B. `Listem → 🌐 Tümü` Sekmesindeki Kaynaklararası Bölünme Kök Nedenleri

1. **Tek Yönlü / Eksik Çapraz Kimlik Önbelleği (`CrossProviderIdentity.kt`, `MyListIdentityCrosswalk.kt`, `ExternalAuthManager.kt`, `KitsuApiClient.kt`, `AuthViewModel.kt`):**
   - `CrossProviderIdentityResolver.resolve`, çözülen kimlik paketini yalnızca çağıran satırın `cacheKey`'ine ve `Anime:mal:<id>` anahtarına yazıyordu; `Anime:kitsu:<id>`, `Anime:bangumi:<id>`, `Anime:shikimori:<id>`, `Anime:simkl:<id>`, `Anime:anilist:<id>` anahtarlarına yazmıyordu.
   - `MyListIdentityCrosswalk.build` ise `mal` eksenine sahip satırları `needsCrosswalk = false` diyerek atlıyor, `kitsu` satırı için `Anime:kitsu:7442` aradığında önbellekte yalnızca `Anime:mal:16498` bulunduğu için eli boş dönüyordu!
   - `ExternalAuthManager.saveKitsuMediaIdForMal` `kitsuUserId == null` olduğunda hata fırlatıyor ve `MyListIdentityCrosswalk` `MyWebViewPrefs` içindeki `kitsu_media_*_<a|m>_<malId> → kitsuMediaId` eşlemesini hiç okumuyordu.
   - `KitsuApiClient` `anilist/anime` ve `anilist/manga` mapping'lerini ve `titles.en_us` başlığını ayrıştırmıyordu (`Kaiju No. 8 2nd Season` ↔ `Kaiju No. 8 Season 2`).
   - `AuthViewModel.syncPlatforms` Faz 3 birleştirmesinde yalnızca `BangumiLocalMappingCache` kaydediliyor, öğrenilen Kitsu/AniList/Shikimori/Simkl kimlik demeti diske yazılmıyordu.

2. **21:19 → 23:39 `Attack on Titan` Bölünmesinin Tam Mekanizması (`Anime:tmdb` Çatışması + Pass-2 Union-Find Hatası):**
   - Kullanıcı `Attack on Titan` (`AniList`) kartını favorilediğinde arka planda çalışan `CrossProviderIdentityResolver` ARM üzerinden bir `tmdbId` çözüp `aniListRow.tmdbId` alanına yazdı (`persistNewlyResolvedIds`).
   - Anime sezonlarının TMDB'de bağımsız kimliği olmadığı için `Simkl` satırındaki `tmdbId` ile `AniList` satırındaki `tmdbId` farklı düştüğünde `MediaIdentity.conflictingIdentityKeys` bunu `Anime:tmdb` çakışması sayıp doğrulanmış `Anime:mal:16498` eşleşmesine rağmen birleşmeyi veto edebiliyordu.
   - Üstelik `MyListLibraryGrouping.kt` Pass-2 birleştirme döngüsünde `if (!compatible(survivor, other)) { survivor = other; continue }` doğrusal ataması yüzünden, aynı aday anahtarını paylaşan grupların ortasına uyumsuz bir grup girdiğinde baştaki ve sondaki uyumlu grupların birleşmesi tamamen atlanıyordu.
   - Birleşik kart oluşturulurken `isAdult` tüm grup üyelerinden `any` ile taşındığı halde `isFavorite` yalnızca temsilci satırdan okunuyordu.

3. **Diğer Listem Yüzeyleri (`TvLibraryScreen.kt` ve `KitsugiUserMediaListScreen.kt`):**
   - Android TV kütüphane ekranı (`TvLibraryScreen`) `entriesFlow`'u `MyListIdentityCrosswalk` + `groupMyListEntries` olmadan ham haliyle listeliyordu (6 sağlayıcı bağlıyken TV'de her anime 6 kez görünüyordu).
   - Kullanıcı profil listesi (`KitsugiUserMediaListScreen`) olası yinelenen öğeleri arayüzde tekilleştirmiyordu.

---

## 3. Uygulanan Kalıcı Çözümler (Dosya Bazlı)

1. **`app/src/main/java/com/kitsugi/animelist/model/MediaIdentity.kt`**
   - Sağlayıcı takma adları (`al`, `shiki`, `bgm`, `bgm.tv`, `themoviedb`) `canonicalSource` içine eklendi.
   - Sezon/part sıra sayısı varyantlarını (`2nd Season` ↔ `Season 2`, `Part II` ↔ `Part 2` vb.) farklı sezonları birbirine karıştırmadan üreten `normalizedTitleVariants` eklendi.
   - Yalnızca `Anime:tmdb` uyuşmazlığının doğrulanmış birincil anime kimliklerini (`mal`/`anilist`/`kitsu`/`shikimori`/`bangumi`/`simkl`) bölmesini önleyen `authoritativeConflictingIdentityKeys` eklendi.
   - Kütüphane birleştirme için ±1 yıl katalog farkı toleranslı `libraryTitleMatch`, `librarySameMedia` ve tek bölümlük anime filmleri (`Movie` ↔ `Anime`) köprüleyen `sameAnimeMovieAcrossType` eklendi.

2. **`ExternalAuthManager.kt`, `CrossProviderIdentity.kt` & `MyListIdentityCrosswalk.kt`**
   - `saveKitsuMediaIdForMal` / `getKitsuMediaIdForMal` `kitsuUserId == null` durumunda çökmeden hem global (`kitsu_media_global_<a|m>_<malId>`) hem kullanıcı kapsamlı anahtara yazacak/okuyacak şekilde güçlendirildi; `kitsuMediaMappingSnapshot` ve `findMalIdForKitsuMedia` eklendi.
   - `CrossProviderIdentityCache.writeBundle` ve `snapshotAll` eklendi: çözülen kimlik demeti artık `mal`, `anilist`, `kitsu`, `shikimori`, `bangumi`, `simkl` (ve yalnızca `Movie` türünde `tmdb`; sezon kirliliğini önlemek için `Anime`/`TvShow` türünde `tmdb` hariç!) anahtarlarının tamamına yazılıyor.
   - `MyListIdentityCrosswalk.build` artık hem `BangumiLocalMappingCache`, hem ters çevrilmiş `ExternalAuthManager.kitsuMediaMappingSnapshot` (`reverseKitsuMapping`), hem de bellek içi ters indekslenmiş `CrossProviderIdentityCache.snapshotAll` (`indexCrossIdentitySnapshot`) kaynaklarını birlikte okuyor.

3. **`KitsuApiClient.kt`, `KitsuImportManager.kt`, `SimklImportManager.kt`, `AniListImportManager.kt`, `ShikimoriImportManager.kt` & `AuthViewModel.kt`**
   - `KitsuApiClient` içinde `anilist/anime` ve `anilist/manga` mapping'leri (`realAniListId`) ile `titles.en_us` / `en_cn` İngilizce başlık yedekleri ayrıştırıldı.
   - `KitsuImportManager`, `AniListImportManager`, `SimklImportManager` ve `AuthViewModel.syncPlatforms` (Faz 3) öğrendikleri çapraz kimlikleri anında `CrossProviderIdentityCache.writeBundle` ve `saveKitsuMediaIdForMal` / `BangumiLocalMappingCache` içine kalıcı yazıyor.
   - `SimklImportManager.deduplicateImportedEntries` `movies + shows + anime` birleşiminde aynı anime filminin hem `Movie` hem `Anime` olarak gelmesini tek kayıtta birleştiriyor; Kitsu/AniList/Shikimori sayfalama tekrarları elendi.

4. **`MediaEntryDao.kt` & `MediaEntryRepository.kt`**
   - `MediaEntryDao.replaceSourceTransaction` atomik `@Transaction` varsayılan metodu eklendi.
   - Süreç-geneli `writeMutex` ile eşzamanlı yazma yarışları kapatıldı.
   - `insert` ve `insertAll` aynı kaynak altında mevcut yapımı tespit edip yeni kopya açmak yerine mevcut satırı birleştirerek güncelleyecek hale getirildi.
   - `smartImport` ve `replaceSourcePreservingAdultFlags` yerel `isFavorite` yıldızını, posteri, çözülmüş başlıkları ve `Movie` ↔ `Anime` film eşleşmelerini koruyor; fazladan kopyaları temizliyor.
   - Mevcut veritabanındaki geçmişten kalma çift satırları veri kaybetmeden birleştirip temizleyen `deduplicateSameSourceDuplicates()` eklendi ve hem `MyListViewModel.init` hem `AuthViewModel` senkron/içe aktarım sonlarına bağlandı.
   - `resolvedIdsFor` ve `findSiblingRows` Kitsu/Bangumi yerel eşlemelerini ve anime filmi çapraz tür kardeşlerini de kapsayacak şekilde genişletildi (bir kartta favori/puan/durum değiştiğinde Kitsu dahil tüm kardeş satırlara yansır).

5. **`KitsugiModels.kt`, `AppViewModel.kt` & `AppRootDetailPages.kt`**
   - `matchesTargetSource` ve `firstMatchingInTargetOrResultSource` eklendi; `AppViewModel.resolvePrimarySourceForApiResult` ile ekleme ve favorileme aynı hedef kaynak kararını (`isMalConnected` kontrolü dahil) paylaşıyor.
   - TMDB/Jikan sonuçlarından `Simkl`/`AniList` altına yapılan eklemelerde hem ön kontrolde, hem ARM çözümlemesi sonrasında, hem de `resolvedSimklId` çözümlemesi sonrasında hedef kaynak kontrol edilerek çift satır oluşumu tamamen engellendi; TMDB ID'lerinin `malId` alanına sızması kapatıldı.
   - Detay ekranı kalp/favori butonu (`onToggleFavoriteClick`) hedef kaynaktaki mevcut satırı bulup toggle ediyor; listede yoksa API topluluk puanını kullanıcı puanı gibi yazmadan (`score = null`) doğru hedef kaynağa ekliyor.

6. **`MyListLibraryGrouping.kt`, `MyListScreen.kt`, `MyListViewModel.kt`, `KitsugiUserMediaListScreen.kt` & `TvLibraryScreen.kt`**
   - `groupMyListEntries` Pass-1 ve Pass-2 kontrolleri `librarySameMedia` + `authoritativeConflictingIdentityKeys` ile güncellendi; Pass-2 kök birleştirme döngüsü tüm benzersiz kök çiftlerini `(i, j)` ikili sınayacak şekilde düzeltildi; `isFavorite` bayrağı tüm grup üyelerinden birleşik karta taşındı (`group.members.any { it.entry.isFavorite }`).
   - Tekil sekmeler ve profil listeleri için `deduplicateSingleSourceEntries` eklendi ve `MyListScreen` içinde tüm sağlayıcı sekmelerine (`1..6`) arka planda (`Dispatchers.Default`) bağlandı.
   - `MyListScreen` içinde tekil sağlayıcı sekmesinde arama yapılırken tüm sağlayıcıların ham listesinin (`entriesAfterAdultFilter`) sekmeye sızması düzeltildi.
   - `MyListViewModel.repairMisclassifiedSources` artık gerçek `"mal"` satırlarına dokunmuyor; yalnızca eski `"jikan"` satırlarını taşırken `"anilist"` altında aynı yapım varsa kopya yaratmak yerine yetim satırı siliyor.
   - `TvLibraryScreen` (`MyListIdentityCrosswalk` + `groupMyListEntries`) ve `KitsugiUserMediaListScreen` (`deduplicateSingleSourceEntries`) tekilleştirildi.

---

## 4. Doğrulama ve Regresyon Testleri

- **Birim Testleri (`app/src/test/`):**
  - `MyListLibraryGroupingTest.kt`: `Attack on Titan` (6 kaynak + farklı Anime TMDB ID + favori taşınması), `Kaiju No. 8 Season 2` (`2nd Season` ↔ `Season 2`), farklı sezonların birleşmemesi (`Season 1` ≠ `Season 2`), Pass-2 ortadaki uyumsuz gruba rağmen birleşme ve `Simkl` `Demon Slayer: Infinity Castle` (`Movie` + `Anime`) tekilleştirme testleri eklendi.
  - `MyListIdentityCrosswalkTest.kt`: `reverseKitsuMapping`, Kitsu → MAL köprüsü ve `indexCrossIdentitySnapshot` (`Anime:tmdb` sızıntı engeli dahil) testleri eklendi.
  - `MediaIdentityDisambiguationTest.kt`: Sezon sıra sayısı eşanlamlıları, `Anime:tmdb` çakışma vetosu muafiyeti ve `sameAnimeMovieAcrossType` testleri eklendi.
  - `MediaEntryMatchesTest.kt`: `matchesTargetSource` ve `firstMatchingInTargetOrResultSource` (TMDB → Simkl ve `Movie` ↔ `Anime` film) testleri eklendi.
  - `KitsuMediaIdentityTest.kt`: `anilist/anime` mapping ve `en_us` başlık ayrıştırma testi eklendi.
- **Depo Kuralları ve Kaynak Denetimleri:**
  - `+0 XML ID` kuralı korundu (`strings.xml` dosyalarına tek bir yeni ID bile eklenmedi; `check_localization_resource_parity.py` → `17696 strings and 4 arrays` tam eşleşme).
  - Bangumi kasası (`bangumi_vault_keep.xml`, `diag/bangumi_vault/*`) ve sözlük üreteçlerine dokunulmadı.
  - Tüm 25 değiştirilen Kotlin dosyasında parantez/blok/dizge dengesi ve görünürlük/import uyumu statik olarak doğrulandı.
