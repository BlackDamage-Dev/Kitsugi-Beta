# Kitsugi Beta v2.4.387

[TR] **Eklenti Portalı Gerçek İçerik Araması, Listem Arama Kökten Hızlandırması, Detay Sayfası 5sn Donma Çözümü ve Resimler Uyarlanabilir Kılavuzu**
[EN] **Cross-Plugin Content Search, My List Real-Time Search Optimization, Detail Page 5s Freeze Fix & Adaptive Images Grid**

---

### [TR] Yenilikler ve İyileştirmeler

#### 1. Eklenti Portalı Gerçek Çapraz İçerik Araması (PluginPickerScreen)
- **Gerçek Eklenti İçi Medya Taraması:** Eklenti portalındaki arama kutusu artık yalnızca eklenti isimlerini filtrelemekle kalmaz; tüm etkin eklentiler üzerinde Dispatchers.IO + Semaphore(6) eşzamanlılık sınırı ve eklenti başına 18 sn zaman aşımı ile gerçek CsStreamRunner.searchWithCfRetry içerik aramasını çalıştırır.
- **Kademeli Akış & Canlı İlerleme Çubuğu:** Yanıt veren eklentilerin sonuçları anında ekrana eklenir (progressive streaming); üst kısımda tamamlanan eklenti sayısını gösteren canlı ilerleme çubuğu yer alır.
- **Dinamik Eklenti Çipleri & 3 Sütunlu Medya Kartları:** Arama sonuçları bulunan eklentilere göre filtreleme çipleri (Hepsi, Eklenti Adı (N)) sunulur; tıklanan kartlar doğrudan KitsugiAddonDetailDialog üzerinden detayları açar.

#### 2. Listem Arama ve Silme Kökten Hızlandırması (829+ Öğe Donma Çözümü)
- **80ms Debounce & 0.0ms Anında Temizleme:** KitsugiCosmicSearchBar ve KitsugiSearchField bileşenlerinde yazarken 80 ms gecikme, temizleme butonuna basıldığında veya arama kutusu tamamen silindiğinde ise 0.0 ms anında sıfırlama uygulandı.
- **Sıfır Sıralama Maliyetli Arama Hattı:** aseSortedWithoutQuery ve aseSectionsWithoutQuery önbelleği oluşturuldu. Arama yapılırken zaten sıralı olan baz liste taranarak her harfte pplySort çalıştırılması engellendi; arama temizlendiğinde 829+ öğelik tam liste 0.0 ms'de tek referans takasıyla geri yüklenir.
- **Üst Sekme Çubuğu İzolasyonu:** MyListTabBar bileşenine onPickRandomClick sağlandı ve dinamik liste parametresi kaldırılarak arama sırasındaki tam sayfa recomposition'ları önlendi.
- **Çift Ölçüm Darboğazı Kaldırıldı:** Poster satırlarındaki Modifier.height(IntrinsicSize.Min) kaldırıldı; 3'lü kart satırları 
emember ile sabitlendi.

#### 3. Önceden Ziyaret Edilmiş Detay Sayfası 5 sn Donma Çözümü
- **Tür Duyarlı Önbellekleme:** DetailCache yapılandırmasına 	ype.name eklenerek anime/film/dizi çakışmaları giderildi.
- **Senkron Vitrin Görseli & Arka Plan Çözümleme:** _detailLoading.value = false Aşama 1 biter bitmez tetiklenir; TMDB/Fanart ağ tavanı beklemesi arka plana alındı.
- **O(1) Kaynak İndeksleme & Önden Isıtma:** 58.000+ XML dizesini içeren ppStringIdMap eklenerek Resources.getIdentifier darboğazı ortadan kaldırıldı; TSV sözlükleri uygulama açılışında (prewarmTranslationCaches) arka planda ısıtılır.
- **GPU Donanım Bitmap Hızlandırması:** Vitrin görsellerinde llowHardware(true) açılarak bellek ve GPU performansı maksimize edildi.

#### 4. Dikey Modda Üst Aksiyon Barı Histerezis & Görünüm Düzeltmesi
- **Kaybolmayan Üst Bar:** Dikey modda kaydırma sınırları showAtIndex = 1, hideAtIndex = 0 olarak düzeltildi.
- **88.dp Titreme Koruması:** Hero görselinin görünür piksel yüksekliğini ölçen 88.dp histerezis eşiği eklenerek sınır bölgelerindeki salınım ve titreşimler giderildi.

#### 5. Detay Sayfası İskeleti ve Sekme Optimizasyonları (0..10)
- **Resimler Sekmesi Uyarlanabilir Kılavuz (Grid):** Yatay LazyRow yerine küçük telefonlarda 2, tabletlerde 4–6 sütunlu GridCells.Adaptive(150.dp) kılavuz düzenine geçildi; 12.dp aralıklar ve doğal en-boy oranı (spectRatio) korundu.
- **İskelet & Pager Gecikmesi:** HorizontalPager gövdesi 1 vsync ertelendi, uzak sekmelerde scrollToPage kullanıldı.
- **Karakterler, Öneriler & İlişkiler:** 20–24'lü ilk pencere ve +24/+36 genişletme butonu uygulandı; kararlı key ve role bazlı pre-localization getirildi.
- **Grafikler & Yorumlar:** Dilim ve istatistik hesapları 
ememberStatusSlices ile memoize edildi; contentType atandı.

---

### [EN] Release Notes

#### 1. Cross-Plugin Real Content Search (PluginPickerScreen)
- **Real In-Plugin Search Pipeline:** Cross-searches all active plugins using Dispatchers.IO + Semaphore(6) with an 18-second per-plugin timeout via CsStreamRunner.searchWithCfRetry.
- **Progressive Streaming & Live Progress Bar:** Real-time incremental result delivery with active plugin counter and progress bar.
- **Dynamic Filter Chips & Media Cards:** Filter chips per plugin with results, opening KitsugiAddonDetailDialog on card click.

#### 2. My List Real-Time Search & Backspace Optimization
- **80ms Debounce & 0.0ms Instant Reset:** Debounced typing with instant zero-latency clear swap on empty query.
- **Zero-Sort Search Pipeline:** Introduces aseSortedWithoutQuery. Keystrokes filter already-sorted entries without re-running pplySort. Backspacing to empty restores 829+ items in 0.0ms via reference swap.
- **Tab Bar Recomposition Isolation:** MyListTabBar uses onPickRandomClick, isolating tab bar rendering from typing keystrokes.
- **Eliminated Double-Measure:** Removed Modifier.height(IntrinsicSize.Min) on grid cards.

#### 3. Previously Visited Detail Page 5s Freeze Fix
- **Type-Aware Detail Cache:** Added 	ype.name to cache keys, preventing movie/series conflicts.
- **Immediate Loading Dismissal:** _detailLoading.value = false fires immediately upon Phase 1 completion; TMDB/Fanart lookups run asynchronously in the background.
- **O(1) Resource Mapping & Cache Prewarming:** ppStringIdMap bypasses Resources.getIdentifier bottle-necks across 58,000+ strings. Dictionary caches prewarm on app start.
- **Hardware Bitmap Acceleration:** Restored GPU hardware bitmaps for hero artwork images.

#### 4. Portrait Top Action Bar Visibility & Hysteresis
- **Stable Floating Bar:** Corrected visibility indices to showAtIndex = 1, hideAtIndex = 0.
- **88.dp Hysteresis:** Pixel-height hero tracking prevents oscillation and flicker at scroll thresholds.

#### 5. Detail Tab Performance & Adaptive Images Grid
- **Adaptive Images Grid:** Migrated from single-row LazyRow to adaptive 2–6 column grid layout with 12dp spacing and natural aspect ratio scaling.
- **Deferred Pager Frame:** 1-vsync deferral for pager body and fast tab jumps via scrollToPage.
- **Windowed Tabs:** Characters, Recommendations, and Relations use windowed pagination with expand buttons and stable item keys.
