# Kitsugi Beta v2.4.359

## 🇹🇷 Türkçe

### 📱 Detay Sayfalarında Animasyonlu Mini Üst Bar Geri Getirildi
- **Kompakt Üst Bar Aktivasyonu:** Sayfa aşağı kaydırıldığında sekme bandının üzerinde **geri, yapım başlığı, galeri, paylaş ve favori** aksiyonlarını içeren kompakt mini bar yeniden görünür hale getirildi.
- **Kök Neden Düzeltmesi:** Medya detay düzeninde dikey `LazyColumn` vitrin (0), sekmeler (1) ve pager içeriği (2) olmak üzere 3 öğeden oluşuyordu. Eski kodun olmayan 4. öğeyi (`showAtIndex = 3`) beklemesi nedeniyle tetiklenemeyen bar, standart `showAtIndex = 2, hideAtIndex = 0` eşiklerine bağlandı.
- **Akıcı Histerezis Animasyonu:** Mevcut `KitsugiStickyHeaderReveal` (dikey açılma, kayma ve solma) animasyonu korundu; vitrine geri dönüldüğünde yumuşakça kapanır ve sekme sınırında titreme yapmaz.
- **Tüm Detay Sayfaları Kapsamı:** Arama, Keşfet ve Listem'den açılan tüm medya detayları (`DetailPageScaffold`), karakter detayları (`CharacterDetailPage`) ve ekip detayları (`StaffDetailPage`) ortak görünürlük politikasına bağlandı; stüdyo detaylarındaki mevcut animasyonlu bar korundu.
- **Regresyon Testleri:** Eşik, histerezis, geri yükleme ve sınır durumları için beş adet birim testi (`DetailStickyHeaderVisibilityTest`) eklendi ve başarıyla doğrulandı.

---

## 🇬🇧 English

### 📱 Animated Mini Toolbar Restored on Detail Pages
- **Compact Floating Toolbar Activation:** Scrolling down reveals the compact toolbar above the tab strip with **back, media title, gallery, share, and favourite** actions.
- **Root Cause Fix:** The vertical detail `LazyColumn` has exactly 3 rows: hero (0), sticky tabs (1), and pager content (2). The previous code was expecting a non-existent fourth row (`showAtIndex = 3`). Visibility thresholds are now aligned to `showAtIndex = 2, hideAtIndex = 0`.
- **Smooth Hysteresis Motion:** Preserved `KitsugiStickyHeaderReveal` (vertical expansion, sliding, and fade) motion tokens. The toolbar collapses smoothly when returning to the hero without flickering at the tab strip boundary.
- **Universal Detail Page Scope:** Media details from Search, Explore, and My List (`DetailPageScaffold`), character details (`CharacterDetailPage`), and staff details (`StaffDetailPage`) now use the shared visibility policy; existing studio page toolbar behavior is retained.
- **Regression Coverage:** Added five unit tests in `DetailStickyHeaderVisibilityTest` covering thresholds, boundary hysteresis, and restored scroll positions.
