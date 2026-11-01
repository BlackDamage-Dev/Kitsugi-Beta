# Çözüm Raporu — Arama "Tümü" Sayfası: Filtre Sonrası Kaydırmada Yenileme + `KTS-COM-1016`

**Tarih:** 2026-11-02  
**Dal:** `arena/0fb04b34-kitsugi-beta`  
**Kapsam:** Arama sayfası, filtre sayfası, 7 kaynak (AniList / MAL / TMDB / Shikimori / Kitsu / Simkl / Bangumi) ortaklığı

---

## 1. Bildirilen belirti

> "Etiketler / türler / kurumları seçtikten sonra Tümü sayfasını aşağı kaydırdığım anda sayfa yenileniyormuş gibi oluyor ve `KTS-COM-1016 • API şema hatası` alıyorum. Filtre sayfası ile kaynaklar arasında artık sorun istemiyorum."

Ekran görüntüsünde: üstte `Tümü • 2 filtre / Düzenle` çipi, hemen altında hata rozeti, ardından **filtreyle ilgisiz popüler** `MyAnimeList` ve `Film ve Dizi (TMDB)` kartları vardı. Arama çubuğu boştu (filtre ile keşif araması).

---

## 2. Kök neden (tek cümle)

**"Tümü" sayfası sayfalanmış bir liste değil, 7 kaynağın ilk sayfasından oluşan bir raf görünümü olduğu hâlde, sayfalama (`loadMore`) bu ekranda açık bırakılmıştı.** Filtreli durumda `hasNextPage` bayrağı doğru kapanmadığı için aşağı kaydırmak `loadMore()` tetikliyor; `loadMore()` raf aramasını tekrar çalıştırıp `multiResults`'ı "yükleniyor" durumuna yazıyor (tüm raflar kaybolup shimmer'a dönüyor = *sayfa yenileniyor*), yedi kaynağa yeniden istek atıyor ve geçersiz filtre parametreleri HTTP 400 → `KTS-COM-1016` üretiyordu.

### 2.1 Zincirin halkaları

| # | Sorun | Nerede |
|---|-------|--------|
| 1 | `search()` her aramanın başında `hasNextPage = true` yazıyor, raf dalı ise `hasNext = false` döndürüyor. Aradaki pencerede (ve `setEngine` / `setScope` / `resetFilters` / `applyDetailFilterRequest` gibi `hasNextPage = true` yazan girişlerde) bayrak açık kalıyor. | `SearchViewModel.search()`, `setEngine`, `setScope`, `applyDetailFilterRequest` |
| 2 | `shouldLoadMore` yalnızca `hasNextPage` + "listenin sonuna 3 öte" koşuluna bakıyor. Raf görünümü ~10 öğeden oluştuğu için kullanıcı ilk kaydırmada hemen "sona" geliyor. | `SearchScreen.kt` |
| 3 | `snapshotFlow` **her kaydırma karesinde** `autoLoadBudget` / `lastAutoLoadLoadedCount` Compose state'ini sıfırlıyor; bu state `LaunchedEffect`'in anahtarı olduğu için efekt her karede yeniden başlayıp `loadMore()` çağırıyor. "Sonsuz sayfalama emniyeti" aslında çalışmıyordu. | `SearchScreen.kt` |
| 4 | `loadMore()` işi `searchJob` ile iptal edilmiyordu. Yeni arama başlarken arkada kalan sayfalama işi `executeSearchForPage` içinden `multiResults = plan.toLoadingState()` yazıp taze aramanın raflarını siliyordu. | `SearchViewModel.search()` / `executeSearchForPage` |
| 5 | `loadMore()` hata/iptal yolunda `isLoadingMore`'ı veya `hasNextPage`'i kapatmıyordu → spinner takılması ve aynı sayfanın tekrar istenmesi. | `SearchViewModel.loadMore()` |

### 2.2 `KTS-COM-1016` nereden geliyordu

`PlatformErrorCatalog` her **HTTP 400** yanıtını `COM_1016` ("API şema hatası") olarak sınıflandırır. Filtreler kaynaklara doğrulanmadan gönderiliyordu:

* AniList `genre_in` / `tag_in` alanlarına demografi (`Shounen`), yerelleşmiş veya tanınmayan etiketler; `seasonYear` tek başına;
* Kitsu `filter[categories]` alanına `"Action"` gibi görünen adlar (slug beklenir) ve `/manga` ucuna `season`/`seasonYear` (o uçta bu alan yok);
* Bangumi `filter.tag` alanına İngilizce/ASCII sözlük anahtarı (`isekai`) — API özgün Çince wiki etiketi bekler;
* Jikan/MAL `producers` parametresine **AniList stüdyo ID'si** (farklı kimlik ad alanı);
* TMDB `with_keywords` parametresine serbest arama metni (sayısal keyword ID beklenir);
* Shikimori / Simkl / Bangumi'ye eşlenemeyen türler gönderilmesine rağmen kaynak yine de sorgulanıyordu.

Ek olarak `sourceErrorCodes`, `PlatformFailureRegistry.failuresSince(...)` ile **uygulamanın başka ekranlarındaki** (senkronizasyon/keşfet) hataları da topluyor ve sonuçlar doluyken bile kırmızı rozet basıyordu.

### 2.3 Filtre ↔ kaynak uyumsuzluğu (ekran görüntüsündeki alakasız kartlar)

* Global `genres` / `tags` yalnızca **AniList rafına** uygulanıyordu; MAL/TMDB/Shikimori/Kitsu/Simkl/Bangumi rafları aynı filtre varken **filtresiz popüler** sonuç döndürüyordu.
* `planAllSourcesForState` yalnızca `detailSearchFilterRequest`'i dikkate alıyordu; filtre sayfasından gelen global facet'ler ve kaynağa özel filtre demetleri plana hiç girmiyordu.
* `activeFilterCount` (ALL) kaynağa özel 7 demetin hiçbirini saymıyordu → rozet yalan söylüyor, `hasFiltersApplied` false olduğu için boş sorgulu filtreli arama `clearResults()` ile siliniyordu.
* Filtre sheet'indeki **her çip tıklaması** `search()` çağırıyordu → her tıklamada 7 kaynağa yeni istek + öncekinin iptali; "Uygula" bir kez daha arama yapıyordu.
* "Tümü" için seçilen sıralama yalnızca AniList demetini güncelliyordu; özet çipi "En Yüksek Puanlı" derken diğer raflar kendi eski sıralamasında kalıyordu.
* `GenresTagsSheet` uygulaması `updateFilters(SearchFilters(...))` ile yıl/sezon/puan alanlarını null'a çekiyordu.
* `setScope` seçilen kapsamı motorun `availableScopes()` ile doğrulamıyordu; `setEngine` önceki motorun filtrelerini state'te bırakıyordu.

---

## 3. Yapılan düzeltmeler

### 3.1 Kaydırma / yenileme bug'ı — kökten kapatma

| Dosya | Değişiklik |
|-------|-----------|
| `SearchUiState.kt` | `isShelfMode` tek kaynaklı doğruluk kaynağı (`selectedEngine == ALL \|\| currentTab == All`). |
| `SearchViewModel.search()` | Raf modunda arama **başında** `hasNextPage = false`; bitişte de `false`. `loadMoreJob` iptali, `isLoadingMore = false`, `lastRequestedPage = 0`. Boş sorgulu "Tümü" keşif araması artık `clearResults()` ile öldürülmüyor. |
| `SearchViewModel.loadMore()` | Raf modunda **hemen return** + bayrakları kapatır; `lastRequestedPage` ile aynı sayfanın iki kez istenmesi engellenir; iş `loadMoreJob` ile izlenir; hata/iptal `finally` bloğunda spinner'ı temizler ve `hasNextPage`'i kapatır; filtre sheet'i açıkken çalışmaz. Yeni iş önce kaydedilip eski iş sonra iptal edilir ve `finally` yalnızca hâlâ güncel işin bayrağını söndürür (eski iptalin yeni spinner'ı silmesi engellendi). |
| `SearchViewModel.executeSearchForPage()` | Rafların `toLoadingState()` yazımı yalnızca `page == 1 && güncel nesil` için; bayat sayfalama işi taze rafları silemez. |
| `SearchScreen.kt` | `shouldLoadMore` artık `!uiState.isShelfMode` şartlı. `snapshotFlow` yalnızca "tepeye gelindi" geçişini (`distinctUntilChanged`) üretir; her karede state yazan bütçe sıfırlama kaldırıldı. `LaunchedEffect` anahtarları gerçek durum değişimleri (`shouldLoadMore`, `page`, `hasNextPage`, motor, sekme). |
| `SearchScreen.kt` | `LazyColumn`'daki bütün `item`'lara sabit `key` verildi. Hata rozeti / atlanan kaynak bilgisi eklenip çıktığında pozisyon kayması (scroll sıçraması) olmuyor. |
| `SearchViewModel.clearResults/clearQuery/setQuery/openSourceSearch` | Sayfalama işi ve bayrakları da temizlenir. |

### 3.2 Filtre ↔ 7 kaynak ortaklığı

Yeni **tek karar noktası**: `SearchFacetBridge.kt` + ortak taksonomi `SearchTaxonomy.kt`.

* Global tür/etiket/yıl/sezon/kurum facet'leri **her kaynağın kendi API'sine** çevrilir: AniList `genre_in`/`tag_in`/`genre_not_in`/`tag_not_in`, MAL-Jikan genre ID'leri, TMDB genre ID'leri, Shikimori genre ID'leri, Kitsu category **slug**'ları, Simkl genre slug'ı, Bangumi **özgün wiki etiketi**.
* Bir kaynak seçili facet'i temsil **edemiyorsa raf kapatılır** — filtresiz popüler sonuç sızmaz, geçersiz parametre gönderilmez. Atlanan kaynaklar hem arama sayfasında hem filtre sheet'inde yerel adlarıyla gösterilir; hiçbiri kalmadıysa anlaşılır bir mesaj verilir.
* **Hariç tutulan** terimler de dahil edilenlerle aynı sözlükten doğrulanır. Eskiden yalnızca AniList için doğrulanan hariç tutma, MAL/TMDB/Shikimori'de tanınmayınca sessizce yok sayılıp dışlanmak istenen içerik rafta görünüyordu; artık o raf kapanır. Hariç tutmayı API'sinde ifade edemeyen Kitsu/Simkl/Bangumi zaten kapatılır.
* Kaynağa özel filtre demetleri de plana girer; global + kaynağa özel + detay facet'i katmanları birlikte (union) uygulanır. Yalnızca kaynağa özel filtre varsa diğer raflar filtresiz sonuç üretmemek için kapanır; **global/detail facet de varsa** bu facet her rafa ayrı ayrı çevrildiği için destekleyen diğer raflar açık kalır (6 rafın gereksiz yere kaybolması engellendi).
* `GenresTagsSheet` artık `SearchTaxonomy` ortak listesini kullanıyor; UI ile API doğrulaması aynı kaynaktan besleniyor.
* `CanonicalFilterBridge.supportsDetailFilter` ve `applyDetailFilterRequest` doğrulanmış değerleri kullanır: bilinmeyen AniList tag'i, geçersiz Kitsu kategorisi, İngilizce Bangumi etiketi, AniList studio ID'si artık API'ye gitmez.
* `BangumiTagDictionary.nativeTagFor()` eklendi: `isekai` gibi ASCII sözlük anahtarı yerine `异世界` gibi özgün etiket döner.
* `AniListSearchClient` / `JikanApiClient`: `$excludedTags: [String]` + `tag_not_in` desteği; `seasonYear` yalnız `season` ile gönderilir.
* Kitsu `/manga` ucuna `season`/`seasonYear` gönderilmez; single-engine Kitsu/Bangumi/TMDB yollarında da aynı parametre doğrulaması uygulandı.
* TMDB `with_keywords` yalnızca sayısal keyword ID alır; metin sorgusu + filtre birleşemediğinde kaynak kapatılır ya da `search` ucu + yıl filtresi kullanılır.
* `activeFilterCount` (ALL) bütün katmanları sayar; detay facet'i 7 kez sayılmaz (`DetailSearchFilterRequest.activeFacetCount`).
* `resetFilters()` / motor değişimi **bütün** filtre katmanlarını temizler (`withoutSearchFilters()`); `setScope` kapsamı motorun desteklediği listede doğrular; `applyAllEngineSort()` "Tümü" sıralamasını bütün raflara yayar.
* Filtre sheet'i açıkken çipler arama tetiklemez (`setFilterEditing` / `requestSearch` / `applyFilterEdits`): kapanırken **tek** arama. Sheet açılıp hiçbir şey değiştirilmeden kapatılırsa gereksiz 7 istek atılmaz.
* `GenresTagsSheet` uygulaması ve eski **TMDB tür seçim diyaloğu** için `updateGenresTagsFilters()` kullanılıyor: yıl/sezon/puan/kaynak filtreleri artık silinmiyor. Geçmiş öğesine basmak da filtre düzenleme oturumu açıkken tek commit kuralına uyuyor (`requestSearch`).
* Hata rozetleri artık yalnızca **bu aramada sorgulanan** kaynaklara, yalnızca gerçek hata koduna (`COM_1020` gürültüsü hariç) ve yalnızca o kaynak hiç sonuç döndürmediyse gösterilir.

### 3.3 Ek proaktif kontroller

* `loadMore` hata/iptal yollarında state temizliği, aynı sayfa deduplikasyonu, `filterEditing` kapısı.
* `AllSourcePlan.intersect()` / `activePlatformIds()` / `isEmpty` yardımcıları.
* Tek kaynak ANILIST / KITSU / BANGUMI / TMDB / SHIKIMORI yollarında da geçersiz alan gönderimi engellendi.
* Yeni TR/EN dizeler: `search_skipped_sources_notice`, `search_no_source_supports_filters`.
* Saf mantık için JUnit testleri: `app/src/test/java/com/kitsugi/animelist/ui/screens/search/SearchFacetBridgeTest.kt` (13 test: AniList tür/etiket ayrımı, bilinmeyen etiket reddi, Kitsu slug eşlemesi, desteklenmeyen facet'te raf kapatma, sezon kısıtı, kimlik ad alanı, metin+facet, Bangumi özgün etiket, **bilinmeyen hariç tutma teriminin sızmaması**, **global + kaynağa özel facet birlikteliği**, **yalnız kaynağa özel facet'te diğer rafların kapanması**, facet sayacı).

---

## 4. Doğrulama durumu

> **Bu sandbox'ta JDK / Android SDK / Gradle yok** (`java`, `kotlinc`, `gradle` bulunamadı). Bu yüzden `./gradlew assembleDebug` veya `testDebugUnitTest` çalıştırılamadı. Değişiklikler statik olarak gözden geçirildi: çağrı imzaları, import'lar, sembol referansları, parantez/süslü parantez dengesi ve **tree-sitter Kotlin ayrıştırıcısıyla tüm değişen `.kt` dosyalarının sözdizimi** (15/15 dosya `ERROR` içermiyor). Tip denetimi, derleme ve cihaz testi kullanıcı ortamında yapılmalıdır.

Önerilen doğrulama adımları:

1. `./gradlew :app:testDebugUnitTest --tests "*SearchFacetBridgeTest*"`
2. `./gradlew :app:assembleDebug`
3. Cihazda: Arama → **Tümü** → filtre sheet'inden 2 tür/etiket/kurum seç → Uygula → **aşağı kaydır**. Beklenen: raflar sabit kalır, shimmer/yenileme olmaz, `KTS-COM-1016` çıkmaz, yalnızca filtreyi uygulayabilen kaynak rafları + "atlanan kaynaklar" bilgisi görünür.
4. Aynı akışı boş sorgu, metin sorgusu, tek kaynak (ör. MAL/Kitsu), "Tümünü Gör" sayfası ve filtre sıfırlama ile tekrarla.
5. Logcat'te `SearchViewModel` "Bayat arama nesli" satırlarının kaydırma sırasında **artmadığını** kontrol et.

---

## 5. Bilinen tasarım kararları

* "Metin sorgusu + facet" durumunda TMDB ve Simkl bilinçli olarak atlanır: bu iki API metin araması ile discover filtrelerini tek istekte birleştiremediği için filtresiz sonuç göstermek yerine raf kapatılır. Kullanıcı bunu ekrandaki "atlanan kaynaklar" satırında görür.
* Filtre uygulayamayan hiçbir kaynak kalmazsa boş bir "sonuç bulunamadı" yerine açıklayıcı mesaj gösterilir.
* Motor değiştirildiğinde o motora ait olmayan filtreler temizlenir (filtreler kaynak bazlıdır); bu, "2 filtre" rozeti varken başka kaynağa geçip filtresiz sonuç görme tutarsızlığını önler.
* Yalnızca **kaynağa özel** filtre varsa diğer raflar kapanır (filtresiz popüler sonuç sızmasın). Global/detay facet'i de varsa, o facet her rafa ayrı ayrı çevrildiği için destekleyen diğer raflar açık kalır; kaynağa özel demet yine de yalnızca kendi rafına uygulanır.
