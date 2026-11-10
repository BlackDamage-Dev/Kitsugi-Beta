# PLAN / TASK — Keşfet Kategoriler Çubuğu ve "Tümünü Gör" Birebir Paritesi (2026-10-24)

**Tarih:** 2026-10-24  
**Branch:** `arena/8c304547-kitsugi-beta`

## Amaç

Keşfet (Explore) ekranının üst kısmındaki **Kategoriler** çubuğunun, tüm kaynaklar nezdinde (**AniList, MyAnimeList, Kitsu, Shikimori, Bangumi, TMDB, Simkl** ve **Tümü** görünümü):
1. Hemen altındaki Keşfet raflarıyla **ne eksik ne fazla** birebir aynı öğeleri içermesini sağlamak,
2. Çubuktaki herhangi bir kategoriye tıklandığında, altındaki ilgili rafın **"Tümünü Gör"** butonuna tıklandığında açılan sayfayla **tıpatıp aynı sayfayı** (`FullScreenMediaGridPage`) ve **aynı açılış mantığını/sözleşmesini** kullanmasını sağlamak.

---

## Kök Neden Analizi

1. **Sabit (hardcoded) kategori çubukları ile dinamik raflar arasındaki kopukluk:**
   - Raflar `sourceSections(platform, filteredSourcePayload)` (ve Simkl'de ek olarak kişisel izleme/planlama listeleri) üzerinden dinamik olarak oluşturulurken, `ExploreCategories.kt` içindeki eski `DefaultCategoriesSection` ve `TmdbCategoriesSection` her kaynak için sabit çip listeleri basıyordu.
   - Bu nedenle kaynağın üretmediği veya boş gelen raflar (ör. Shikimori/Bangumi'de olmayan *Yeni Eklenenler*, AniList'te olmayan *Manhwa & Manhua*, `separateNovelsManga` kapalıyken gizlenen *Noveller*, Simkl'de kullanılmayan TMDB çipleri) çubukta fazladan görünüyordu.
   - Tersi yönde, gerçekten aşağıda rafı bulunan kategoriler (**Manhwa & Manhua**, **Noveller & Light Novel**, **Bangumi Dizileri**, **Bangumi Filmleri**, **Simkl kişisel listeleri**) kategori çubuğunda hiç yer almıyordu; ayrıca raf karşılığı olmayan harici kısayollar (*Yayın Takvimi*, *Manga Okuyucu*) çubuğa karışmıştı.

2. **Farklı başlıklar, farklı `ExploreCategoryType` eşlemeleri ve farklı veri listeleri:**
   - Eski kategori çipleri eski sabit başlık kaynaklarını (`R.string.explore_popular_anime` vb.) ve ham `filtered*` listelerini doğrudan geçirirken, altındaki raflar `localizedExploreSectionTitle(section.title)` başlığını ve `distinctBy { it.exploreIdentity() }` uygulanmış `section.results` listesini geçiriyordu.
   - Özellikle **Simkl** seçiliyken `TmdbCategoriesSection` TMDB'nin kategori türlerini ve başlıklarını gönderdiği için çipten açılan *"Tümünü Gör"* sayfası ile raftan açılan *"Tümünü Gör"* sayfası hem başlık hem içerik hem de sayfalama uç noktası bakımından ayrışıyordu.
   - Simkl'in *İzlemeye Devam* raflarında *"Tümünü Gör"* butonu `FullScreenMediaGridPage` yerine `onNavigateToWatchHistory` çağırıyordu.

3. **`FullScreenMediaGridPage` ve `AppRoot` tutarlılık eksikleri:**
   - `AppRoot.kt` üzerinden `FullScreenMediaGridPage` çağrılırken `showAdultContent` ve `blurAdultMedia` ayarları aktarılmıyordu; `FullScreenMediaGridPage.baseExplorePage` içinde de MAL/AniList sayfalama çağrılarının bir kısmına `showAdultContent` geçirilmiyordu.
   - `FullScreenMediaGridPage` içindeki `remember` / `LaunchedEffect` anahtarlarında `title` yer almadığından aynı `(platform, categoryType)` çiftini paylaşabilen raflar (ör. Simkl genel rafları ile kişisel listeler) arasında durum sızıntısı riski bulunuyordu.

---

## Yapılan Değişiklikler

### 1) Tek Ortak Raf & Kategori Modeli (`AllSourcesExplore.kt`, `ExploreScreen.kt`)
- `ExploreSourceSection` veri sınıfına isteğe bağlı `customKey: String? = null` eklendi (`key = customKey ?: "${platform.name}_${category.name}"`).
- Simkl kişisel rafları (`İzlemeye Devam: Diziler/Filmler`, `Planladıklarım: Diziler/Filmler`) `simklPersonalSections(p)` ile `ExploreSourceSection` modeline taşındı ve `ExploreScreen.kt` içinde `showAdultContent` süzgecinden geçirildi.
- `singleSourceExploreSections(platform, p, baseSections)` ile seçili kaynağın tüm rafları tek merkezden üretildi (vitrin/hero aday seçimi için kullanılan `selectedSourceSections` ayrı tutularak korundu).
- Ortak açılış sözleşmeleri tanımlandı:
  - Tek kaynak görünümü için: `openExploreSectionSeeAll(section, resolvedTitle, onSeeAllSection)`
  - *Tümü* görünümü için: `openAllSourcesSectionSeeAll(platformLabel, sectionTitle, section, onSeeAllSection)`
- `classifyExploreSectionGroup` ve `buildExploreCategoryGroups` ile yalnızca `results.isNotEmpty()` olan raflar hiçbir öğe eklenmeden/çıkarılmadan kaynağa uygun mantıksal gruplara (**Anime**, **Manga**, **Dizi & Film**, **Listem**) ve kendi raf sıralarına göre ayrıldı.

### 2) Birleşik `ExploreCategoriesSection` ve Raf Bağlantıları (`ExploreCategories.kt`, `DefaultExploreContent.kt`, `AllSourcesExploreContent.kt`, `TmdbExploreContent.kt`)
- `DefaultCategoriesSection` ve `TmdbCategoriesSection` kaldırılarak yerine doğrudan `singleSourceDisplaySections` listesini tüketen **`ExploreCategoriesSection`** getirildi (TMDB *Modern Ana Sayfa* kapalıyken de kompakt kategori görünümü korunacak şekilde).
- Hem `ExploreCategoriesSection` çipleri hem de `sourceExploreSections` içindeki `KitsugiHorizontalMediaSection` rafları `localizedExploreSectionTitle(section.title)` ve `openExploreSectionSeeAll(...)` üzerinden birebir aynı çağrıyı yapacak şekilde birleştirildi.
- `AllSourcesExploreContent.kt` içinde `"Yayındaki Mangalar"` ve Simkl kişisel raf başlıkları `localizedExploreSectionTitle` içine alındı; *Tümü* görünümündeki kaynak başlığı çipleri (`SourceHeader`) ile altındaki raflar aynı `openAllSourcesSectionSeeAll(...)` sözleşmesine bağlandı.
- `TmdbExploreContent.kt` içindeki eski `simklUserExploreSections` yardımcısında da *İzlemeye Devam* raflarının `onSeeAllSection` açması sağlandı.
- Repo kuralı gereği **`+0` yeni XML `<string>` kaynağı** eklendi; mevcut TR/EN kaynakları kullanıldı.

### 3) "Tümünü Gör" Tam Ekran Sayfa Tutarlılığı (`AppRoot.kt`, `FullScreenMediaGridPage.kt`)
- `AppRoot.kt` içinde `FullScreenMediaGridPage` çağrısına `showAdultContent = appSettings.showAdultContent` ve `blurAdultMedia = appSettings.blurAdultMedia` eklendi.
- `FullScreenMediaGridPage.kt` içinde MAL ve AniList `baseExplorePage` sayfalama çağrılarının tamamına `showAdultContent` geçirildi; `seedResults`, `loadedResults`, `currentPage`, `simklPageInitialized`, `simklTrendingResults`, `hasMorePages` ve başlangıç `LaunchedEffect` anahtarlarına `title` eklendi.

### 4) Birim Testleri (`AllSourcesExploreTest.kt`)
- `categoriesBarMatchesPopulatedShelvesOneToOneAcrossAllSources`: 7 kaynağın tamamında (ve Simkl kişisel listeli/listesiz durumlarında) çubuktaki çiplerin altındaki dolu raflarla birebir eşleştiğini (*ne eksik ne fazla*) ve grup içi raf sırasını koruduğunu doğrular.
- `categoryGroupClassificationMatchesPlatformCatalogTypes`: TMDB, Simkl ve Bangumi dahil grup sınıflandırmalarını (`TV_MOVIES`, `ANIME`, `MANGA`, `PERSONAL_LISTS`) doğrular.
- `categoryChipAndShelfSeeAllInvokeIdenticalNavigationContract`: Kategori çipi tıklaması ile raf *"Tümünü Gör"* tıklamasının (hem tek kaynak hem *Tümü* modunda) birebir aynı `(title, category, results, platform)` parametrelerini ürettiğini doğrular.

---

## Değişen Dosyalar

- `PLAN_TASK_KESFET_KATEGORILER_VE_TUMUNU_GOR_PARITESI_2026-10-24.md`
- `app/src/main/java/com/kitsugi/animelist/AppRoot.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/AllSourcesExplore.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/AllSourcesExploreContent.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/DefaultExploreContent.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/ExploreCategories.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/ExploreScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/TmdbExploreContent.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/FullScreenMediaGridPage.kt`
- `app/src/test/java/com/kitsugi/animelist/ui/screens/explore/AllSourcesExploreTest.kt`

---

## Doğrulama Özeti

- `kotlinc 2.0.21` (JRE 21) ile `HeroSelectionTest`, `AllSourcesExploreTest`, `HomeHeroPolicyTest`, `HeroImageSelectionTest`, `HomeHeroGestureModifiersTest` derlenip çalıştırıldı: **74/74 test geçti (0 hata)**.
- Değişen tüm `.kt` dosyaları JetBrains `KtPsiFactory` AST ayrıştırıcısı ile kontrol edildi (**0 sözdizimi hatası**).
- `python3 scripts/check_localization_resource_parity.py` çalıştırıldı: **17.696 string ve 4 dizi senkron (`+0` yeni XML ID)**.
