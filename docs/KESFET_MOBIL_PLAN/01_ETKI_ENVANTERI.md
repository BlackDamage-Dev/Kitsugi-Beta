# 01 — Etki envanteri ve korunacak davranışlar

**Temel:** 051c4b6c · statik analiz · Mobil dışı ekranlar kapsam dışı. Bu dosyadaki "değişebilir" kesin değişiklik değil; önce görev sırasında yeniden doğrula.

## Çağrı ve veri zinciri
`AppRootTabPages.kt:257` → `ExploreScreen` → `ExploreViewModel` / `sourceSections` → `KitsugiHorizontalMediaSection` / `KitsugiExploreMediaCard` → `AppRoot.openApiDetail` veya `openFullScreenSection` → `FullScreenMediaGridState` → `FullScreenMediaGridPage` → `ExploreSourceFilterQuery` / kaynak istemcileri. Kaynak/medya kimliği her sınırda korunmalı. `AppViewModel.exploreScrollIndex/Offset` bugün TEK konum saklar; dört sekme bu sözleşmeyi zorlar.

## Dosya listesi: doğrudan mobil ve olası etki
| Dosya (app/src/main/java/com/kitsugi/animelist/ altında) | Bugün / olası işlem | Risk |
|---|---|---|
| `ui/screens/explore/ExploreScreen.kt` | Hero, sticky kaynak, içerik, cache/hata, yenileme, FAB, rastgele, bildirim, scroll ve callback; yeni mobil kabuğun ana yeri. | Kaybolan eylem, yanlış kaynak, nested gesture, tek scroll, alt bar. |
| `ui/screens/explore/ExplorePlatformToggle.kt` | 7+Tümü seçici; sekmeden bağımsız kalmalı. | Kaynak tercihi veya başlık/etiket karışması. |
| `ui/screens/explore/ExploreModels.kt`, `ExploreCategoryType.kt` | Mevcut model ve enum; mobil için **eklemeli** sunum modeli önerilir; eski enumları yeniden adlandırma. | Shared kullanıcılar ve Gson eski cache. |
| `ui/screens/explore/AllSourcesExplore.kt` | `sourceSections`, `forSource`, `exploreIdentity`, `ExploreSourceState`. Ortak sınır: değiştirmeden yeni mobil mapper girdisi olarak kullanmayı tercih et. | Legacy alan anlamları, diğer tüketicilerin davranışı, source fallback. |
| `ui/screens/explore/AllSourcesExploreContent.kt`, `DefaultExploreContent.kt`, `TmdbExploreContent.kt`, `BangumiRealExploreSections.kt`, `NativeKindExploreSections.kt`, `ExploreCategories.kt`, `ExploreCategoryGrid.kt` | Var olan raflar/kısayollar. Mobil yeni renderer'a geçişte özellik kaybı denetlenir; kullanılmıyor diye ortak kod hemen silinmez. | Okuyucu kısayolu, sıralama, farklı eski/modern seçenekleri ve ortak çağıranlar. |
| `ui/screens/explore/HeroSelection.kt`, `ui/components/HomeHeroPolicy.kt`, `HomeHeroArtworkLoader.kt`, `KitsugiHeroSection.kt` | Mevcut hero kitapları bilerek dışlar; artwork seçimi/kararlılık/iptal. İlk sürüm manga hero gizlenebilir; kitap için mevcut video guard'ını gevşetme. | Yanlış kimlik/görsel, autoplay-pager çakışması, shared bileşen yan etkisi. |
| `ui/components/KitsugiHorizontalMediaSection.kt`, `KitsugiExploreMediaCard.kt`, `KitsugiSourceBadge.kt`, `KitsugiPlatformLogo...` | Kartlar, liste durumu, başlık, puan, görüntü, tıklama ve badge. Mümkünse yeni mobil sarmalayıcıyla yeniden kullan. | Bunlar başka sayfalarda da kullanılır; global restyling yapma. |
| `ui/screens/explore/ExploreMapping.kt` | Kütüphane eşlemesi `matchesInSource` ile. | Aynı başlık/ID başka kaynak/medya eşleşmesi, ekleme yerine düzenleme. |
| `ui/screens/explore/ExploreViewModel.kt` | 7 loader, farklı cache/iş/iptal ve prefetch; paylaşılan mevcut davranışı koru. | Request generation, hızlı geçiş, fallback, retry ve trafik. |
| `AppRootTabPages.kt`, `AppRoot.kt`, `ui/app/AppViewModel.kt`, `ui/app/FullScreenMediaGridState.kt`, `ui/app/AppNavigationState.kt` | Callback, sayfa durumu, konum ve geri yığını. Yalnız gerekiyorsa geriye uyumlu telefon katmanı ekle. | Aynı shared app durumunun başka akışlara etkisi; `savedStateKey`, önceki sayfa konumu. |
| `ui/screens/fullscreen/FullScreenMediaGridPage.kt`, `ExploreSourceFilterQuery.kt` | Kaynağa/kategoriye göre endpoint, filtre, sıralama, sayfalama. | İlk sayfa ve devamın farklı sıralama/endpoint olması; Simkl kişisel liste başlıktan algılanıyor. |
| `data/remote/KitsugiModels.kt`, `TmdbDiscoverClient.kt`, `TmdbApiClient.kt`, `SimklApiClient.kt`, `JikanApiClient.kt`, `AniListSearchClient.kt`, `KitsuExploreClient.kt`, `KitsugiShikimoriClient.kt`, `KitsugiBangumiClient.kt`, `KitsugiAiringCalendarClient.kt` | Veri ve kaynak API'leri; ilk fazda davranış değiştirmeme hedefi. | `type`, namespace, sıralama semantiği ve arka plan sorguları. Değişiklik zorunluysa ek risk ve ortak etki analizi. |
| `data/local/ExploreCacheDao.kt`, `ExploreCacheEntity.kt`, `KitsugiDatabase.kt`; `data/settings/SettingsDataStore.kt`, `AppSettings.kt` | Kaynak/dil/title-resolver sürümü ile cache; son kaynak ve ayarlar. | Migration/veri kaybı; yeni modele cache göçü zorunlu değil. |
| `ui/theme/KitsugiColorScheme.kt`, `KitsugiColors.kt`, `KitsugiDynamicTheme.kt`, `gradient/`; `res/values/strings.xml`, `res/values-en/strings.xml` | Mevcut tema/gradient ve yerelleştirme tüketilir; ek metinler TR/EN. | Sert kodlu mint, düşük kontrast, dil değişimi. |

## Özellikle korunacak özellikler
- Kaynak seçimi, Tümü ve her kaynak bağımsız hata/yeniden deneme; çevrimdışı cache ve yenileme; mevcut dil/yetişkin filtreleri.
- Arama/kaynak seçimi, bildirim eylemi, rastgele seçim, yayın takvimi, manga okuyucu kısayolu, sıralama/kategori erişimleri, kullanıcı rafları (girişliyse), listeye ekle/düzenle, detay geri dönüşü.
- Uzun basma, `showAnimeLogos`, `titleLanguage`, `scoreFormat`, `hideScores`, `showAdultContent`, `blurAdultMedia`, `separateNovelsManga`, `tmdbModernHomeEnabled`; tema ve gradyan; pull-to-refresh, yukarı çık FAB, bottom bar görünürlüğü ve scroll geri yükleme.
- Bu özelliklerden birinin yeni tasarımda yeri belirsizse kullanıcıya sorulacak; basitçe kaldırılmayacak. Modern/klasik ayarının yeni ekranla ne yapacağı ayrı açık karardır (`12`).

## Test dosyaları ve yeni test alanları
Mevcut `app/src/test/.../ui/screens/explore/{AllSourcesExploreTest,ExploreMappingSourceScopeTest,HeroSelectionTest}.kt`, `ui/components/{HomeHeroPolicyTest,HeroVitrinLayoutTest,HeroImageSelectionTest,HomeHeroGestureModifiersTest}.kt`, `data/remote/SimklExploreParsingTest.kt`, `SettingsDataStoreTest.kt`; `app/src/androidTest/.../ui/components/KitsugiHeroSectionTest.kt`. Yeni mapper/source/tab durum testleri ve mobil UI testleri eklenecek. Mevcut testlerin varlığı çalıştıkları anlamına gelmez.
