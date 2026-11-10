# Kitsugi Beta v2.4.365

## [TR] Detay ve Resimler Sekmesi Performans İyileştirmeleri

Bu sürümde detay sayfalarındaki Resimler (`DetailGalleryCard` & `KitsugiImageGalleryDialog`) sekmesi ile genel detay sayfalarında (`ApiResultDetailPage`, `MediaEntryDetailPage`, `CharacterDetailPage`, `StaffDetailPage`) yaşanan takılma, donma ve sıçramaların tamamı 7 ana kök neden çözülerek optimize edilmiştir:

1. **CDN Küçük Resim & Decode Boyut Politikası (`GalleryThumbnailImagePolicy`):**
   - Küçük önizleme kartlarına orijinal 4K ve tam boy şeffaf Fanart/TMDB görselleri yerine uygun boyutlu CDN varyantları (/w185, /w342, /w500) verildi; 16px kuantize decode boyutu + `Precision.INEXACT` ile CPU/RAM yükü ve GC duraksamaları ortadan kaldırıldı.
2. **Galeri & Resimler Yeniden Mimarisi:**
   - Tek geçişli `precomputeGalleryModel` ile kaynak/kategori grupları ve `globalIndex` tabloları önbelleğe alındı.
   - `withFrameNanos` tabanlı kademeli şerit montajı ile aynı anda onlarca şeridin compose edilerek arayüzü kilitlemesi önlendi.
   - Hücre bazlı katmanlar kaldırılarak draw-phase `graphicsLayer` ve ortak degrade fırçası uygulandı.
   - `KitsugiImageGalleryDialog` içindeki sürekli çalışan gereksiz animasyon kaldırıldı, sayfa geçişleri `graphicsLayer` çizim fazına taşındı.
3. **Sıfır Boşta Animasyon & Sıfır JNI Shader Tahsisatı:**
   - `KitsugiGalleryIconButton` yalnızca yükleme sırasında animasyon oluşturacak şekilde hafifletildi.
   - `KitsugiPlasmaLoader` radyal gradyan fırçalarını remember ederek `withTransform` matrisiyle taşıdı (kare başı 0 JNI shader tahsisatı).
   - `KitsugiNsfwImage` tekil paylaşımlı `SharedNsfwMainHandler` ve `rememberUpdatedState` kullanımına geçirildi.
4. **Sekme Bazlı StateFlow İzolasyonu & Çeviri Sınırlandırması:**
   - Sekmelere özel StateFlow akışları ilgili sekme slotlarına izole edildi; arka plandaki veri yanıtlarının tüm detay sayfasını yeniden compose etmesi önlendi.
   - `TranslationManager` kısmi yayınları (streaming onPartial) 250ms ile sınırlandırıldı ve önbellek isabetindeki çift yayın kaldırıldı.
5. **Yerleşim (Layout) Fazında Pager Yüksekliği & Sabit Vitrin:**
   - Yatay sekme geçişlerinde `currentPageOffsetFraction` ve `pageHeights` doğrudan `Modifier.layout` içine taşındı; kaydırma sırasında sıfır recomposition sağlandı.
   - Vitrin yön seçimi standartlaştırıldı; `lockedHeroUrl` ve %3 tolerans bandı ile geç gelen galeri yanıtlarının sayfa üstünde sıçrama yapması engellendi.
6. **Büyük Listelerde Sayfalama & Optimizasyon:**
   - 40 öğeyi aşan karakter, ekip, öneri ve ilişki listeleri için `DetailChunkSelectorRow` aralık seçici ve kararlı anahtarlar (`key`) eklendi.
   - `InfoRow` satır başı seçim yöneticisi yükü hafifletildi; bölüm puanları indeksi tek seferlik önbelleğe alındı.

---

## [EN] Detail & Gallery Tab Performance Improvements

This release addresses stutters, freezes, and frame drops across the Images (`DetailGalleryCard` & `KitsugiImageGalleryDialog`) tab and all detail pages (`ApiResultDetailPage`, `MediaEntryDetailPage`, `CharacterDetailPage`, `StaffDetailPage`):

1. **CDN Thumbnail & Decode Sizing Policy (`GalleryThumbnailImagePolicy`):**
   - High-resolution (4K / full transparent) previews are now downscaled using appropriate CDN thumbnail variants (/w185, /w342, /w500) with 16px quantized decode sizes and `Precision.INEXACT`, eliminating GC pauses and CPU-bound software decoding bottlenecks.
2. **Gallery & Dialog Architecture:**
   - Single-pass `precomputeGalleryModel` caches category groupings and O(1) global index lookups.
   - Staggered row mounting via `withFrameNanos` prevents composing 15–25 LazyRows in a single frame.
   - Eliminated cell-level alpha layers in favor of draw-phase `graphicsLayer` alpha; removed continuous idling animations from `KitsugiImageGalleryDialog`.
3. **Zero-Idle Animations & Shader Optimizations:**
   - `KitsugiGalleryIconButton` only instantiates transitions while actively loading.
   - `KitsugiPlasmaLoader` reuses remembered radial gradient shaders via `withTransform` matrices (zero per-frame JNI allocations).
   - `KitsugiNsfwImage` now uses a singleton `SharedNsfwMainHandler` and `rememberUpdatedState`.
4. **Tab-Scoped StateFlow Isolation & Translation Throttling:**
   - Tab-specific StateFlow subscriptions are isolated into dedicated tab slot composables, preventing background network updates from recomposing the root detail page.
   - `TranslationManager` throttles streaming `onPartial` emissions to 250ms intervals and removes redundant emissions on Room cache hits.
5. **Layout-Phase Pager Height & Locked Hero Artwork:**
   - Moved `currentPageOffsetFraction` and `pageHeights` reads to `Modifier.layout { ... }`, achieving zero recompositions during horizontal tab swiping.
   - Locked hero artwork via `lockedHeroUrl` and a 3% aspect ratio deadband to prevent layout jumps when background gallery metadata arrives late.
6. **Large List Chunking & Ep Rating Indexing:**
   - Introduced `DetailChunkSelectorRow` (40 items per chunk) and stable keys for large character, staff, recommendation, and relation lists.
   - Lightweight `InfoRow` without per-row `SelectionContainer` overhead; O(1) memoized episode ratings index.
