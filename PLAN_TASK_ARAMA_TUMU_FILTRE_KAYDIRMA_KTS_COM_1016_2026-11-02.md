# PLAN/TASK — Arama "Tümü": filtre sonrası kaydırmada yenileme + `KTS-COM-1016` + filtre ↔ kaynak ortaklığı

**Tarih:** 2026-11-02  
**Dal:** `arena/0fb04b34-kitsugi-beta`  
**Öncelik:** Kritik (arama ekranı yenileniyor / yanlış sonuç / API 400)  
**Kullanıcı bildirimi:** "Etiketler, türler veya kurumları seçtikten sonra Tümü sayfasını aşağı kaydırdığım anda buga giriyor, sayfa yenileniyormuş gibi oluyor ve `KTS-COM-1016 • Kitsu • API şema hatası` alıyorum. Filtre sayfası ile kaynaklar arasında artık sorun istemiyorum; aklıma gelmeyen sorunları da kontrol et ve adam akıllı gider."

---

## 1. Hedef / Kabul kriterleri

1. Filtre seçiliyken "Tümü" aşağı kaydırılınca **yenileme, shimmer, scroll sıçraması, tekrarlanan API isteği, sahte hata** olmayacak.
2. `KTS-COM-1016 • Kitsu • API şema hatası` bu akışta üretilmeyecek.
3. Arama sayfası + filtre sayfası + 7 kaynak (AniList / MAL / TMDB / Shikimori / Kitsu / Simkl / Bangumi) arasında **filtre-state çelişkisi** kalmayacak; filtresiz popüler sonuç sızmayacak.
4. Kullanıcının saymadığı ek olası sorunlar proaktif taranıp düzeltilecek.
5. Yüzeysel yama değil, **kök neden** giderilecek.

---

## 2. Teşhis (kök neden zinciri)

| # | Kök neden | Yer |
|---|-----------|-----|
| 1 | "Tümü" sayfalanmış liste değil, 7 kaynağın ilk sayfasından oluşan **raf görünümü**; buna rağmen `hasNextPage = true` bırakılıyordu. | `SearchViewModel.search()`, `setEngine`, `setScope`, `resetFilters`, `applyDetailFilterRequest` |
| 2 | `shouldLoadMore` yalnızca `hasNextPage` + "sona 3 öğe" koşuluna bakıyor; ~10 item'lık rafta ilk kaydırmada tetikleniyordu. | `SearchScreen.kt` |
| 3 | `snapshotFlow` her kaydırma karesinde `autoLoadBudget` / `lastAutoLoadLoadedCount` Compose state'ini sıfırlıyor; bu state `LaunchedEffect` anahtarı olduğu için efekt her karede yeniden başlayıp `loadMore()` çağırıyordu. | `SearchScreen.kt` |
| 4 | `loadMore()` işi `searchJob` ile iptal edilmiyordu; arkada kalan iş `multiResults = plan.toLoadingState()` yazıp taze rafları shimmer'a düşürüyordu. | `SearchViewModel` |
| 5 | `loadMore()` hata/iptal yolunda `isLoadingMore` / `hasNextPage` kapatmıyordu. | `SearchViewModel` |
| 6 | Global tür/etiket/yıl facet'leri yalnızca AniList rafına uygulanıyor; diğer 6 raf filtresiz popüler sonuç döndürüyordu. | `executeSearchForPage` |
| 7 | Filtre değerleri doğrulanmadan API'ye gidiyordu: AniList `genre_in` demografi/bilinmeyen tag, Kitsu `filter[categories]` görünen ad + `/manga` sezon alanı, Bangumi `filter.tag` ASCII anahtar, Jikan `producers` AniList studio ID, TMDB `with_keywords` serbest metin → HTTP 400 → `COM_1016`. | tüm istemciler |
| 8 | `sourceErrorCodes` arka plandaki senkronizasyon hatalarını da topluyor, sonuçlar doluyken bile kırmızı rozet basıyordu. | `PlatformFailureRegistry.failuresSince()` |
| 9 | Her filtre çipi 7 istek + "Uygula" bir kez daha; `GenresTagsSheet` yıl/sezon/puan'ı siliyor; `setEngine` eski motor filtrelerini bırakıyor; `setScope` kapsamı doğrulamıyor; "Tümü" sort sadece AniList'e yazılıyor; `activeFilterCount` kaynağa özel demetleri saymıyor. | UI + ViewModel |

---

## 3. Plan / Yapılanlar

### A. Kaydırma → yenileme bug'ının kökten kapatılması
- [x] `SearchUiState.isShelfMode` tek doğruluk kaynağı (`selectedEngine == ALL || currentTab == All`).
- [x] `search()` raf modunda **başta ve sonda** `hasNextPage = false`; `loadMoreJob` iptali, `lastRequestedPage = 0`, `isLoadingMore = false`.
- [x] `loadMore()` raf modunda hemen return + bayrak kapatma; aynı sayfa deduplikasyonu; hata/iptal `finally` temizliği; eski işin `finally`'inin yeni spinner'ı söndürmemesi için iş kayıt sırası düzeltildi.
- [x] `executeSearchForPage` raf yükleme durumunu yalnızca `page == 1 && güncel nesil` yazıyor.
- [x] `SearchScreen.shouldLoadMore` → `!uiState.isShelfMode`; `snapshotFlow` → `distinctUntilChanged` ile yalnızca "tepeye gelindi" sinyali; `LaunchedEffect` anahtarları gerçek durum değişimleri; tüm `LazyColumn item`'larına sabit `key`.
- [x] `clearResults` / `clearQuery` / `setQuery` / `openSourceSearch` sayfalama işini ve bayraklarını temizliyor.

### B. Filtre ↔ 7 kaynak ortaklığı
- [x] `SearchTaxonomy.kt`: UI ile API doğrulamasının **ortak** tür/etiket taksonomisi.
- [x] `SearchFacetBridge.kt`: tek karar noktası — kapsam planı ∧ facet planı; desteklemeyen kaynak atlanıyor, atlananlar ekranda gösteriliyor, hiçbiri kalmazsa açıklayıcı mesaj.
- [x] Global + kaynağa özel + detay facet katmanları birlikte çevriliyor; **yalnız kaynağa özel** filtre varsa diğer raflar kapanıyor, global/detail facet varsa destekleyen raflar açık kalıyor.
- [x] Hariç tutulan terimler de aynı sözlükle doğrulanıyor; dışlama API'si olmayan Kitsu/Simkl/Bangumi kapatılıyor.
- [x] Kitsu slug eşlemesi, Bangumi özgün wiki etiketi (`nativeTagFor`), AniList `tag_not_in` (`$excludedTags`), `seasonYear` guard'ı, Jikan/MAL producer ID ad alanı, TMDB sayısal keyword ID, Kitsu `/manga` sezon alanı, tek-kaynak yollarında da aynı doğrulama.
- [x] `activeFilterCount` tüm katmanları sayıyor; detay facet'i 7 kez sayılmıyor (`activeFacetCount`).
- [x] `withoutSearchFilters()` ile `resetFilters` / motor değişimi tüm katmanları temizliyor; `setScope` `availableScopes()` ile doğruluyor; `applyAllEngineSort()` sıralamayı tüm raflara yayıyor.
- [x] `setFilterEditing` / `requestSearch` / `applyFilterEdits`: sheet açıkken çip başına istek yok, kapanırken **tek** arama. `updateGenresTagsFilters()` yıl/sezon/puan'ı koruyor (eski TMDB tür diyaloğu da buna geçti).
- [x] Hata rozetleri yalnızca bu aramada sorgulanan + gerçekten sonuç döndürmeyen kaynaklara, `COM_1020` gürültüsü hariç.

### C. Ek proaktif kontroller
- [x] `AllSourcePlan.intersect()` / `activePlatformIds()` / `isEmpty`.
- [x] TR/EN dizeler: `search_skipped_sources_notice`, `search_no_source_supports_filters`.
- [x] 13 JUnit testi: `SearchFacetBridgeTest.kt`.
- [x] `COZUM_RAPORU_ARAMA_TUMU_FILTRE_KAYDIRMA_KTS_COM_1016_2026-11-02.md` kök-neden raporu.

---

## 4. Doğrulama durumu

> Bu ortamda **JDK / Android SDK / Gradle yok**; `assembleDebug` ve unit testler çalıştırılamadı. Statik kontroller yapıldı: 15/15 değişen `.kt` dosyası tree-sitter ile sözdizimi açısından temiz, çağrı imzaları/import'lar/semboller elle kontrol edildi, TR/EN `strings.xml` XML olarak geçerli, `git diff --check` temiz.

Kullanıcı ortamında:

```bash
./gradlew :app:testDebugUnitTest --tests "*SearchFacetBridgeTest*"
./gradlew :app:assembleDebug
```

Cihaz senaryosu: Arama → **Tümü** → 2 tür/etiket/kurum seç → Uygula → **aşağı kaydır**. Beklenen: raflar sabit, shimmer/yenileme yok, `KTS-COM-1016` yok, yalnızca filtreyi uygulayabilen raflar + "atlanan kaynaklar" bilgisi. Aynı akış boş sorgu / metin sorgusu / tek kaynak / "Tümünü Gör" / sıfırlama ile tekrarlanmalı; Logcat'te "Bayat arama nesli" satırları kaydırma sırasında artmamalı.

---

## 5. Bilinen tasarım kararları

- Metin sorgusu + facet: TMDB/Simkl iki ucu birleştiremediği için bilinçli atlanır (filtresiz sonuç yerine raf kapanır).
- Hiçbir kaynak filtreyi uygulayamıyorsa boş "sonuç bulunamadı" yerine açıklayıcı mesaj.
- Motor değişince o motora ait olmayan filtreler temizlenir.
- Yalnız kaynağa özel filtre varsa diğer raflar kapanır; global/detail facet de varsa destekleyen raflar açık kalır.

## 6. İlgili rapor

- `COZUM_RAPORU_ARAMA_TUMU_FILTRE_KAYDIRMA_KTS_COM_1016_2026-11-02.md`
