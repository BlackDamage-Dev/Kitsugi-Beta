# Kitsugi Beta v2.4.369

[English notes below]

---

## 🇹🇷 Türkçe Sürüm Notları

### 1. Detay Sayfası İskelet & Açıklama Gösterim Hızlandırması
- **Anında Açıklama Renderı:** Detay sayfalarındaki açıklama (synopsis) metni, yapay iskelet arkasında gereksiz bekletilmeden sunucudan veya önbellekten ulaştığı anda doğrudan gösterilir.
- **Akıcı İçerik Geçişi:** Açıklama metninin yüklenme süreci optimize edilerek diğer detay bileşenlerinin render bloklaması ortadan kaldırıldı.

### 2. Wiki Okuyucusu: Sayfa Araçlarından Çeviri Dil Seçimi & Hızlandırma
- **Dinamik Çeviri Dili Seçimi (`WikiPageTools`):**
  - Wiki okuyucusundaki sayfa araçları menüsüne hedef dil seçici eklendi. Kullanıcılar sayfayı kapatmadan Türkçe, İngilizce ve diğer diller arasında anlık geçiş yapabilir.
- **Paralel DOM Çeviri Hızlandırması (`dom-translator.js` & `WikiDomTranslationBatch`):**
  - Sayfa içeriği akıllı gruplar (DOM batching) halinde paralel çevrilir.
  - Önbellek mekanizması (`TranslationCacheDao`) güçlendirilerek daha önce çevrilen paragraflar anında ekrana gelir.

### 3. Detay Sayfalarında Otomatik Devam Gösterimi (`DetailAutoPagingColumn`)
- **Kesintisiz Kaydırma Deneyimi:**
  - Karakterler, İlişkili Yapımlar, Öneriler ve Personel sekmelerinde yer alan manuel "Daha fazla göster" butonuna basma zorunluluğu kaldırıldı.
  - Sayfa sonuna yaklaşıldıkça yeni içerik blokları yumuşak bir geçişle otomatik olarak yüklenir ve listeye eklenir.

### 4. MDBList Puan Kimlikleri ve Ölçek Normalizasyonu
- **Doğrulanmış Puan Eşleme (`MdbListMediaIds` & `MdbListClient`):**
  - IMDb, TMDb, Trakt, Metacritic ve Rotten Tomatoes puanlarının kaynak kimlik çözümlemesi düzeltildi.
  - Farklı ölçeklerdeki puanlar (100, 10 ve 5'lik tabanlar) standart Kitsugi gösterimine kusursuz biçimde dönüştürülerek doğru rozetlerle detay sayfasına yansıtıldı.

### 5. Listem: Doğrudan Medya Düzenleyici Kısayolu
- **Hızlı Düzenleme Butonu (Kalem İkonu):**
  - Listem kartlarındaki kazaen basılabilen doğrudan bölüm artırma (+1) butonu yerine, doğrudan tam medya düzenleyici çekmecesini açan pratik düzenleme ikonu (`Icons.Rounded.Edit`) yerleştirildi.
  - Kullanıcılar tek dokunuşla durum, bölüm/cilt ilerlemesi, puan ve özel notları eksiksiz güncelleyebilir.

### 6. Video Oynatma & Genel Uygulama Performansı
- **ParallelRangeDataSource İyileştirmesi:**
  - Video oynatma motorundaki paralel parça indirme mekanizması optimize edildi; tamponlama (buffering) süresi kısaltıldı.
- **Bellek ve Önbellek Sınırlandırması:**
  - Uzun ömürlü görsel ve detay önbelleklerine kesin üst sınırlar getirilerek bellek kullanımı düşürüldü.
  - Senkronizasyon ve metadata çağrıları `Dispatchers.IO` üzerine taşınarak ana arayüzün takılması (jank) önlendi.

---

## 🇬🇧 English Release Notes

### 1. Detail Page Synopsis & Skeleton Acceleration
- **Instant Synopsis Rendering:** Synopsis text is now displayed immediately as soon as fetched from cache or server, without being unnecessarily hidden behind skeleton loading animations.
- **Fluid Layout Transitions:** Eliminated layout jank and render blocking for description and overview panels.

### 2. Wiki Reader: In-Page Translation Language Picker & Engine Speedup
- **Dynamic Language Selection (`WikiPageTools`):**
  - Added an intuitive target language picker directly to the Wiki page tools menu. Users can switch between Turkish, English, and other languages on the fly.
- **Parallel DOM Translation (`dom-translator.js` & `WikiDomTranslationBatch`):**
  - Document elements are translated in parallel batches, substantially accelerating full-page translation.
  - Enhanced cache hit rate with `TranslationCacheDao` for instant retrieval of previously translated sections.

### 3. Detail Auto-Paging on Scroll (`DetailAutoPagingColumn`)
- **Seamless Infinite Scroll:**
  - Replaced manual "Load More" buttons in Characters, Relations, Recommendations, and Staff tabs with automated scroll-triggered paging.
  - New batches load smoothly into view as the user scrolls toward the bottom of the list.

### 4. MDBList Rating Identity & Scale Normalization
- **Accurate Rating Crosswalk (`MdbListMediaIds` & `MdbListClient`):**
  - Fixed cross-provider rating identity resolution across IMDb, TMDb, Trakt, Metacritic, and Rotten Tomatoes.
  - Scales (100-point, 10-point, 5-star) are properly normalized and displayed on detail pages with accurate badge metrics.

### 5. MyList: Direct Media Entry Editor Shortcut
- **Quick Edit Action (Pencil Icon):**
  - Replaced the direct increment (+1) action on MyList cards with a direct entry editor shortcut (`Icons.Rounded.Edit`).
  - Users can now open the complete entry editing sheet with a single tap to adjust watch status, episode/chapter progress, score, and personal notes.

### 6. Video Playback & General Performance Enhancements
- **ParallelRangeDataSource Optimization:**
  - Refined parallel chunk fetching in the playback engine, decreasing initial buffering delays and improving stream stability.
  - Long-lived caches are now strictly bounded to prevent memory leaks during long browsing sessions.
  - Background metadata and sync calls have been strictly delegated to `Dispatchers.IO` to ensure 60fps UI smoothness.
