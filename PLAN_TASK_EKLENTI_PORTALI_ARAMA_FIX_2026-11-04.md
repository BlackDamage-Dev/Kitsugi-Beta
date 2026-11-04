# PLAN / TASK — Eklenti Portalı Araması: Portal İçi Video + Manga Arama (v2.4.341)

**Tarih:** 2026-11-04
**Dal:** `arena/b6285d2c-kitsugi-beta`
**Commit:** `3e53aec` (taban: `a6dda9d` / v2.4.340)
**Kapsam:** 4 dosya değişti + 1 yeni (sürüm notu) · +759 / −12 satır

---

## 1. Kullanıcı Şikâyeti

1. "Eklentilerde arama mantığı, eklentilerde arama yaparak çalışmalı."
2. "Arama butonuna basınca beni niye Kaynaklar arama sayfasına atıyor?"
3. Ekran görüntüsü: Eklenti Portalı'nda "Eklentilerde ara... (8 eklenti)" kutusu; altta portal listesi yerine kaynak aramasının "Son Aramalar" geçmişi (the mentalist, supernatural...) görünüyor — portal araması portalda kalmıyor.

## 2. Kök Neden Analizi

| # | Kök neden | Etki |
|---|-----------|------|
| 1 | `PluginPickerScreen` araması yalnızca eklenti **adı/adresi/dili** süzüyordu; içerik araması yoktu | Kullanıcı "Naruto" yazsa bile hiçbir eklentide arama çalışmıyordu |
| 2 | Klavye eylemi `ImeAction.Done` idi → yalnızca klavyeyi kapatıyordu | "Arama butonu" hiçbir arama tetiklemiyordu |
| 3 | "Tüm Eklentiler" satırı `searchViewModel.setSelectedPlugin(null) + onBackClick()` çağırıyordu | Kullanıcı Kaynaklar arama sayfasına (SearchScreen) fırlatılıyordu |
| 4 | Eklenti satırı da `setSelectedPlugin(api.name)` ile global arama durumunu tetikliyordu | Portal, kaynak aramasının CS3 akışına bağlanıyor; arka planda kaynak araması çalışıyordu |
| 5 | Portalda Manga ailesi hiç yoktu | Manga eklentilerinde/kaynaklarında arama yapılamıyordu |

## 3. Yapılan İşler

### 3.1 Portal içi içerik araması (Video)
- [x] Arama kutusuna açık **arama butonu** (büyüteç) eklendi; klavye eylemi `ImeAction.Search` oldu. İkisi de `performPortalSearch()` çalıştırır.
- [x] Video araması: seçili kategori kapsamındaki aktif eklentilerde paralel arama (`CsStreamRunner.searchWithCfRetry`, `supervisorScope + async`), yeni aramada `CsPluginStatusTracker.clear()` + `clearUnsupportedMethodsCache()` (AddonSearchDialog ile aynı sözleşme).
- [x] Sonuçlar portal içinde, eklentiye göre gruplu `LazyColumn + LazyRow` kartlarıyla gösterilir; karta dokunmak `KitsugiAddonDetailDialog` açar. `SearchViewModel` hiç tetiklenmez.
- [x] Kapsam düzeltmesi: içerik araması `categoryFilteredApis` kullanır (yalnız kategori); ad süzgeci yalnızca göz atma listesindedir. Sorgu değişirse bayat sonuçlar `LaunchedEffect(queryText)` ile düşürülür; arama sırasında sorgu değiştiyse sonuç basılmaz.

### 3.2 Video / Manga modları
- [x] Portal üstüne `KitsugiSettingsSegmentedTabs` ile **Video / Manga** sekmeleri eklendi; başlık sayacı moda göre "X eklenti / X kaynak" gösterir.
- [x] Kategori çipleri (TvType grupları) yalnızca Video modunda gösterilir.
- [x] Manga kaynakları `MangaSourceRepository.getAvailableSources()` ile yüklenir (uzantılar geç dolabildiği için 5×600 ms yeniden deneme).
- [x] Manga araması: kaynak seçili değilse `MangaSourceRepository.searchAllSources(query)`, seçiliyse yalnızca o kaynakta `fetchSearchManga(1, q)` + `postProcessSearchResults(relaxScoring = true)`.
- [x] Manga kaynağına dokunmak arama kapsamını seçer (tekrar dokunmak bırakır); sonuç kartına dokunmak `navState.mangaDetailNavState` ile manga detayını açar.

### 3.3 Navigasyon temizliği
- [x] "Tüm Eklentiler" satırı: `setSelectedPlugin + onBackClick` kaldırıldı; sorgu varsa tüm aktif eklentilerde portal içi arama yapar.
- [x] Eklenti satırı: yalnızca `DetailScreen.AddonExplore` açar; global arama durumu tetiklenmez.
- [x] Geri tuşu: önce arama sonuçlarını, sonra sorgu/kapsam seçimlerini temizler.
- [x] +18 sözleşmesi: `LocalShowAdultContent` kapalıyken video (`isAddonAdultResponse`) ve manga (`isAdultContent`) sonuçları elenir.

## 4. Değişen Dosyalar

| Dosya | Değişiklik |
|-------|------------|
| `app/src/main/java/com/kitsugi/animelist/ui/screens/search/PluginPickerScreen.kt` | Portal içi Video+Manga araması, sekmeler, navigasyon temizliği (+740 satır) |
| `app/src/main/res/values/strings.xml` | `manga_kaynaklarinda_ara_x_kaynak`, `tum_manga_kaynaklari`, `aktif_tum_manga_kaynaklarinda_ara` |
| `app/src/main/res/values-en/strings.xml` | Aynı 3 anahtarın İngilizcesi |
| `app/build.gradle.kts` | Sürüm 2.4.340 → **2.4.341** |
| `release_notes_v2.4.341.md` | Yeni sürüm notları (TR/EN) |

## 5. Doğrulama

- [ ] CI Android build (bu ortamda JDK/Android SDK yok; statik kontroller yapıldı: parantez dengesi, tüm `R.string` referansları mevcut, kullanılan bileşenler — `ResultItemCard`, `KitsugiAddonDetailDialog`, `KitsugiSettingsSegmentedTabs` — modül içinde tanımlı).
- [ ] Cihazda: Video sekmesinde arama → sonuçlar portalda gruplu; Kaynaklar sayfasına atma yok.
- [ ] Cihazda: Manga sekmesinde arama → kaynak gruplu sonuçlar; kaynak seçimi kapsamı sınırlar; sonuç manga detayını açar.
- [ ] Cihazda: +18 kapalıyken yetişkin sonuçlar listelenmiyor.
