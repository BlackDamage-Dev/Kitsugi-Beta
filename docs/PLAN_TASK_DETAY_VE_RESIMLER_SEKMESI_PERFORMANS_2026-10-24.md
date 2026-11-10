# PLAN / TASK — Detay Sayfaları ve Resimler Sekmesi Performans Optimizasyonu

**Tarih:** 2026-10-24
**Dal:** `arena/b6311f1e-kitsugi-beta`
**Taban Commit:** `3d3242ffb9a99359a7afc4db9bc126a0e511cc48` (`main`)
**Kapsam:** 23 kod dosyası (22 değişti, 1 yeni) + 1 Plan/Task belgesi · `+0 XML ID`

---

## 1. Kullanıcı Şikâyeti

1. Detay sayfalarındaki **`Resimler` (`DetailGalleryCard`)** sekmesinde aşağı-yukarı ve yatay kaydırma sırasında belirgin kasma, takılma ve donmalar yaşanması.
2. Tüm detay sayfalarında (`ApiResultDetailPage`, `MediaEntryDetailPage`, `CharacterDetailPage`, `StaffDetailPage`) **belli belirsiz zamanlarda ve farklı şekillerde** ani takılma, kare düşmesi ve kaydırma ortasında sıçrama görülmesi.

---

## 2. Kök Neden Analizi

| # | Kök Neden | Etki |
|---|-----------|------|
| 1 | **`Resimler` sekmesinde 4K yazılımsal decode (`allowHardware(false)`)**: `105×148dp` / `196×112dp` küçük kartlara doğrudan TMDB `/t/p/original` (4K) ve şeffaf Fanart.tv HD PNG URL'leri veriliyordu | Şeffaf PNG'ler `RGB_565` yerine zorunlu olarak `ARGB_8888` (~8–33 MB/görsel) çözülüyor; CPU decode kuyruğunu tıkayıp Coil bellek önbelleğini sürekli boşaltıyor ve GC duraksamaları üretiyordu |
| 2 | **`Constraints.Infinity` altında 15–25 `LazyRow`'un tek karede şişmesi + O(N²) indeks araması**: `DetailGalleryCard` içinde gruplamalar `remember` edilmemişti, her hücrede `items.indexOf(item)` çalışıyor, `LazyRow` `key` kullanılmıyor, hücre başı `.alpha(0.5f)` katmanı ve `Brush.verticalGradient` üretiliyordu | `Resimler` sekmesine geçişte ve galeri listesi her güncellendiğinde yüzlerce kart tek karede yeniden kurulup ana iş parçacığını kilitliyordu |
| 3 | **Boştayken çalışan `rememberInfiniteTransition` ve kare başı JNI shader tahsisi**: `KitsugiGalleryIconButton` boşta bile 60–120 FPS çalışıyor, `KitsugiImageGalleryDialog` kökünde sonsuz `downloadPulse` tüm diyaloğu 60–120 FPS yeniden compose ediyor, `KitsugiPlasmaLoader` ise `Canvas` içinde her karede 4–5 adet `Brush.radialGradient` üretiyordu | Kullanıcı hiçbir şeye dokunmazken veya kaydırırken sürekli CPU/GPU ve GC baskısı oluşuyordu |
| 4 | **Kök detay sayfalarında sekme akışı (`StateFlow`) fırtınası**: `ApiResultDetailPage` ve `MediaEntryDetailPage` kökünde 12+ sekme akışı + tam `AppSettings`, `CharacterDetailPage` kökünde 10 ayrı `wikiViewModel` akışı dinleniyordu | Sayfa açıldıktan 2–6 sn sonra arka planda IMDb bölüm puanları, MDBList, Fandom Wiki veya sekme verileri geldikçe tüm detay sayfası (Hero + Sol Panel + Sekme Çubuğu + Aktif Sekme) kaydırma ortasında yeniden compose oluyordu |
| 5 | **Akışlı (streaming) çeviride sınırsız `onPartial` yayını**: `TranslationManager.fetchFromGoogle` her parçada (özellikle parça önbelleği sıcakken birkaç ms içinde 10–15 kez) tüm metni birleştirip regex ile `unmask` ederek yayınlıyordu | Otomatik özet/biyografi/wiki çevirisi akarken `KitsugiMarkdownText` ayrıştırması ve `pageHeights` ölçümü art arda tetiklenip kaydırmayı takıyordu |
| 6 | **Yatay sekme kaydırmada composition fazında offset okuması & kaydırma ortasında vitrin (Hero) zıplaması**: `pagerState.currentPageOffsetFraction` composition fazında okunuyordu; ayrıca `KitsugiDetailHeroSection` dikey telefonlarda önce posterle başlayıp `galleryItems` gelince 16:9 görsele geçerek ~175 dp sıçrama yapıyordu | Sekmeler arası geçişte her karede recomposition oluyor; kütüphane detayında 3–4 sn sonra sayfa aniden zıplıyordu |
| 7 | **Dev sekme listelerinin (`Constraints.Infinity`) tek seferde compose edilmesi, `InfoRow` seçim yöneticisi şişmesi ve `Episodes` O(N·S) hesabı**: 100–600 öğeli karakter/ekip/görünüm/ilişki listeleri tek `Column` içinde açılıyor; her `InfoRow` ayrı bir `SelectionContainer` (~60 gesture node/sekme) kuruyor; `resolveRating` her bölüm satırı için `seasonMaxEpisodes` haritasını baştan hesaplıyordu | Uzun soluklu anime ve seslendirmen sayfalarında sekmeye tıklanınca çok saniyeli donmalar yaşanıyordu |

---

## 3. Yapılan İşler

### 3.1 Küçük Resim CDN & Decode Boyut Politikası
- [x] **Yeni `GalleryThumbnailImagePolicy.kt`**: Orijinal `GalleryItem.url` alanlarına dokunmadan (tam ekran yakınlaştırma ve indirme 4K/kayıpsız kalır), küçük kutularda TMDB (`/t/p/w185`, `w342`, `w500`), AniList (`/cover/medium/`), Kitsu (`small`/`medium`), Bangumi (`/r/400/`) ve Shikimori (`/preview/`) küçük resim URL dönüşümü.
- [x] `rememberThumbnailImageRequest`: Hedef dp boyutuna göre `16px` kuantize `size(widthPx, heightPx)` + `Precision.INEXACT` + ayrık `memoryCacheKey("thumb_...")` ile dev PNG/JPEG dosyalarının küçük kutularda tam boy çözülmesinin engellenmesi.
- [x] `KitsugiNsfwImage.kt`: Görsel başına `Handler` tahsisi kaldırılıp tekil `SharedNsfwMainHandler` ve `rememberUpdatedState` ile kararlı `AsyncImage` geri çağrıları sağlandı.

### 3.2 `Resimler` Sekmesi (`DetailGalleryCard`) ve Tam Ekran Galeri (`KitsugiImageGalleryDialog`)
- [x] `EntryDetailTabContents.kt`: Tek geçişli O(N) `precomputeGalleryModel(items)` ve O(1) `globalIndex` tablosu `remember(items)` içine alındı.
- [x] Çok kategorili galerilerde 15–25 `LazyRow`'un aynı karede şişmesini önleyen `withFrameNanos` tabanlı **kademeli şerit montajı** ve sabit yükseklikli yer tutucular eklendi.
- [x] Hücre başı `.alpha(0.5f)` katmanları kaldırılıp satır düzeyinde draw-phase `graphicsLayer { alpha = ... }` uygulandı; ortak `GalleryThumbScrimBrush`, kararlı `LazyRow` `key`/`contentType` ve `rememberThumbnailImageRequest` bağlandı.
- [x] `KitsugiImageGalleryDialog.kt`: Kök kapsamda 60–120 FPS çalışan `downloadPulse` (`rememberInfiniteTransition`) kaldırıldı; `GalleryImagePage` sayfa geçiş matematiği `Modifier.graphicsLayer { ... }` çizim fazına taşındı; alt/sol küçük resim şeritleri ve ilişkili görseller O(1) indeks + `GalleryThumbnailImagePolicy` ile sınırlandırıldı.

### 3.3 Boşta Sıfır Animasyon & Sıfır Kare Başı JNI Shader
- [x] `KitsugiGalleryIconButton.kt`: `rememberInfiniteTransition` yalnızca `isLoading == true` iken monte edilen yaprak `PulsingLoadingGalleryIcon` bileşenine izole edildi (`graphicsLayer` alfa).
- [x] `KitsugiPlasmaLoader.kt`: `Canvas` içinde her karede yeniden oluşturulan `Brush.radialGradient` çağrıları boyut başına `remember` edilen fırçalara dönüştürülüp `withTransform` matrisiyle taşındı/ölçeklendi.

### 3.4 Kök `StateFlow` İzolasyonu & Çeviri Akışı Birleştirme
- [x] `ApiResultDetailPage.kt` & `MediaEntryDetailPage.kt`: Sekmelere özel akışlar (`translatedSynopsis`, `showSynopsisOriginal`, `mdbListRatings`, `mdbListLoading`, `charactersState`, `staffState`, `recommendationsState`, `relationsState`, `statsState`, `reviewsState`, `episodesState`, `episodeRatings`) kök kapsamdan çıkarılıp `Api*TabSlot` ve `Entry*TabSlot` alt bileşenlerine izole edildi.
- [x] `DetailSharedComponents.kt`: 100+ alanlı `AppSettings` akışı yerine yalnızca detay sayfasının kullandığı alanları dinleyen `rememberDetailSettingsSlice` (`distinctUntilChanged`) ve kendi akışını yalnızca açıkken toplayan `DetailIntegrationsSettingsDialog` eklendi.
- [x] `CharacterDetailPage.kt`: Kök kapsamdaki 10 ayrı `wikiViewModel` `StateFlow` aboneliği ve yinelenen `FandomWikiReaderSheet` kaldırılıp izole `CharacterWikiTabSlot(viewModel = wikiViewModel, ...)` yapısına geçirildi.
- [x] `TranslationManager.kt`: Akışlı çeviride `onPartial` yayınları en az `250ms` aralıkla (`PARTIAL_EMIT_MIN_INTERVAL_MS`) sınırlandırıldı; tam Room önbellek isabetindeki çift yayın kaldırıldı.

### 3.5 Yerleşim Fazında Pager Yüksekliği & Vitrin (Hero) Sabitlemesi
- [x] `DetailPageScaffold.kt`, `CharacterDetailPage.kt`, `StaffDetailPage.kt`: `pagerState.currentPageOffsetFraction` ve `pageHeights` okumaları composition fazından çıkarılıp doğrudan `Modifier.layout { ... }` içine taşındı.
- [x] `KitsugiDetailHeroSection.kt` & `KitsugiDetailHero.kt`: Yön tercihi `resolveHeroArtworkPreference` ile eşitlendi; yüklenen vitrin görselini gecikmeli `galleryItems` güncellemelerine karşı koruyan `lockedHeroUrl`, `%3` en-boy oranı ölü bandı ve tek katmanlı `BoxWithConstraints` uygulandı.

### 3.6 Büyük Sekme Listelerinde 40'lık Sayfalama, `InfoRow` Hafifletmesi ve `Episodes` O(1) İndeksi
- [x] `DetailSharedComponents.kt`: `+0 XML ID` kuralına tam uyumlu `DetailChunkSelectorRow` (`1 - 40`, `41 - 80`, ...) eklendi; `InfoRow` içindeki satır başı `SelectionContainer` sarmalayıcısı kaldırıldı (`KitsugiMiniCopyButton` korundu).
- [x] `CharactersTab.kt`, `RecommendationsTab.kt`, `RelationsTab.kt`, `CharacterDetailComponents.kt`, `StaffDetailComponents.kt`: 40'lık sayfalama (`DetailChunkSelectorRow`), kararlı `key(...)` ve `GalleryThumbnailImagePolicy` küçük resim sınırlandırması eklendi.
- [x] `KitsugiDetailEpisodesTab.kt`: Her satırda yeniden kurulan `seasonMaxEpisodes` yerine tek geçişli `remember(episodeRatings) { EpisodeRatingIndex.build(...) }`, `SeasonAccordion` için önbellekli sezon gruplaması ve `100×60dp` bölüm küçük resmi boyutlandırması eklendi.
- [x] `KitsugiDetailComponents.kt` & `ReviewsTab.kt`: `KitsugiTagsCard` etiket tekilleştirme/filtreleme işlemleri `remember` içine alındı; `ReviewsTab` yatay listelerine kararlı `key` eklendi.

---

## 4. Değişen Dosyalar

### Yeni (2)
| Dosya | Amaç |
|-------|------|
| `app/src/main/java/com/kitsugi/animelist/ui/components/GalleryThumbnailImagePolicy.kt` | CDN küçük resim URL dönüştürücü ve kuantize `ImageRequest` politikası |
| `PLAN_TASK_DETAY_VE_RESIMLER_SEKMESI_PERFORMANS_2026-10-24.md` | Kök neden analizi, görev listesi ve doğrulama raporu |

### Değişen (22)
| Dosya | Özet Değişiklik |
|-------|-----------------|
| `app/src/main/java/com/kitsugi/animelist/data/local/TranslationManager.kt` | `onPartial` 250ms hız sınırı + tam önbellek çift yayın temizliği |
| `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiGalleryIconButton.kt` | Yalnızca `isLoading == true` iken monte edilen `PulsingLoadingGalleryIcon` |
| `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiImageGalleryDialog.kt` | Sonsuz `downloadPulse` kaldırıldı, `graphicsLayer` sayfa geçişi, küçük resim politikası |
| `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiNsfwImage.kt` | `SharedNsfwMainHandler` + `rememberUpdatedState` |
| `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiPlasmaLoader.kt` | `remember` edilmiş radyal fırçalar + `withTransform` (0 JNI tahsisi/kare) |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/EntryDetailTabContents.kt` | `DetailGalleryCard` O(N) ön-hesaplama, kademeli şerit montajı, küçük resim politikası |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailPage.kt` | Sekme bazlı `StateFlow` slot izolasyonu + daraltılmış `DetailSettingsSlice` |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/MediaEntryDetailPage.kt` | Sekme bazlı `StateFlow` slot izolasyonu + daraltılmış `DetailSettingsSlice` |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/CharacterDetailPage.kt` | `CharacterWikiTabSlot(viewModel = ...)` izolasyonu + `Modifier.layout` pager boyu |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/StaffDetailPage.kt` | `Modifier.layout` fazında pager boyu enterpolasyonu |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/DetailPageScaffold.kt` | `Modifier.layout` fazında pager boyu enterpolasyonu |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/DetailSharedComponents.kt` | `InfoRow` hafifletmesi, `DetailChunkSelectorRow`, `rememberDetailSettingsSlice` |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailHero.kt` | `lockedHeroUrl`, oran ölü bandı, tek `BoxWithConstraints` |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailHeroSection.kt` | `resolveHeroArtworkPreference` uyumu, `lockedHeroUrl`, tek `BoxWithConstraints` |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/CharactersTab.kt` | 40'lık sayfalama, kararlı `key`, küçük resim boyutlandırması |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/RecommendationsTab.kt` | `remember` filtreleme, 40'lık sayfalama, küçük resim boyutlandırması |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/RelationsTab.kt` | `remember` filtreleme, 40'lık sayfalama, küçük resim boyutlandırması |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/CharacterDetailComponents.kt` | Görünümler & seslendirmenler 40'lık sayfalama + küçük resim boyutlandırması |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/StaffDetailComponents.kt` | Karakter rolleri & yapımlar 40'lık sayfalama + küçük resim boyutlandırması |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailEpisodesTab.kt` | `EpisodeRatingIndex` O(1) arama, sezon gruplama `remember`, `100×60dp` küçük resim |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/KitsugiDetailComponents.kt` | `KitsugiTagsCard` etiket birleştirme/filtreleme `remember` önbelleği |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ReviewsTab.kt` | Forum konuları, aktiviteler ve incelemeler `LazyRow` kararlı `key` tanımları |

---

## 5. Mimari Kısıt & Doğrulama Kontrolleri

- [x] **`+0 XML ID` Kuralı:** `app/src/main/res/` altında sıfır değişiklik (`scripts/check_localization_resource_parity.py` → `17696 strings and 4 arrays` tam senkron).
- [x] **Dalvik 255-Yazmaç Tavanı:** Kök detay sayfalarındaki 10–14 yerel `State` delegesi sekme slotlarına taşınarak kök çerçeve boyutu küçültüldü; 30 parametreyi aşan fonksiyon yok.
- [x] **MIUI / HyperOS GPU Güvenliği (Xiaomi Pad 6 `pipa`):** Ham `Modifier.blur()` eklenmedi.
- [x] **Bangumi Kasası:** `diag/bangumi_vault/*` ve `gen_bangumi_master.py` dokunulmadan korundu.
