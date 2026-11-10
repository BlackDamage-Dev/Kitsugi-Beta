# Kitsugi Beta v2.4.400

---

## 🇹🇷 Türkçe Sürüm Notları — Kitsugi Beta v2.4.400

### 🎬 Oynatıcı: Kalite ve Kaynak Sheet'lerinin Tam Ayrımı
- **Ayrı Kalite Sheet'i (`QualitySheet`)**: Oynatıcıdaki kalite butonuna kısa dokunulduğunda yalnızca "Otomatik (en iyi kalite)" ve açıkça bildirilen/ölçülen çözünürlükler (1080p, 720p, 480p...) listelenir. Seçilen çözünürlükteki en iyi kaynak oynatılır.
- **Doğrulanabilir Kalite Kuralı**: Kaynaklar çözünürlük bildirmiyorsa uydurma seçenekler üretilmez; kullanıcıya net bir açıklama ve tek tıkla "Kaynak listesi"ne geçiş sağlayan kart gösterilir.
- **Ayrı Kaynak Sheet'i (`SourceSheet`)**: Kalite butonuna **uzun dokunulduğunda** veya Kalite sheet'inin sağ üstündeki "Kaynak" aksiyonuna basıldığında açılır. Sunucu ve eklenti akışları (eklenti adı + akış adı + bildirilen çözünürlük rozeti) listelenir; seçim anında kaynak değiştirilir.
- **Akıllı Hata & Yedek Kaynak Fallback'leri**: Bölüm geçişlerinde veya oynatma hatalarında alternatif kaynak aranırken ya da çözümleme başarısız olduğunda artık doğru hedef olan **Kaynak listesi** (`SourceTracks`) açılır.

### 🔍 Eklenti Arama & Keşfet Ekranlarında Ana Arama Paritesi
- **KitsugiCosmicSearchBar Standartlaşması**: Eklenti Portalı (`PluginPickerScreen`), Eklenti Seçim Sheet'i (`PluginPickerBottomSheet`) ve Eklenti Keşfet (`AddonExploreDialog`) sayfaları ana arama sayfasındaki 56 dp yüksekliğe, 16 dp köşelere ve odak efektlerine sahip Cosmic arama çubuğuna geçirildi.
- **88 dp Boşluk Yaratan Yüzen Overlay Çubuk Kaldırıldı**: Eklenti Keşfet sayfasında içeriği gereksiz yere aşağı öteleyen yarı saydam yüzen çubuk kaldırıldı; başlık ve arama çubuğu akıcı `Column` düzenine alındı.
- **Tek Sütun Kompakt Sonuç Satırları (`AddonResultRow`)**: Eklenti içi arama ve toplu arama sonuçlarındaki 3'lü poster ızgarası ve yatay şeritler yerine ana aramayla birebir aynı ölçülerde kompakt sonuç satırları kullanıldı (56×80 poster, 16 dp neon kenarlık, kalite/yıl/tür/kaynak rozetleri). Dikey, yatay ve tablet ekranlarında kusursuz okunabilirlik sağlandı.

### ⚡ Kararlılık ve Doğrulama
- HLS/DASH manifest ölçümleri ve kaynak çözünürlük gruplamaları için yeni birim testleri (`QualitySheetOptionsTest`) eklendi ve tüm birim test paketi başarıyla doğrulandı.

---

## 🇬🇧 English Release Notes — Kitsugi Beta v2.4.400

### 🎬 Player: Complete Separation of Quality and Source Sheets
- **Dedicated Quality Sheet (`QualitySheet`)**: Short tap on the player's quality button now strictly presents "Auto (best quality)" and explicitly declared/measured resolutions (1080p, 720p, 480p...). Selecting a quality plays the best source matching that resolution.
- **Verifiable Resolution Policy**: If sources don't declare a resolution, artificial qualities are avoided; an informative empty state explains why and provides a direct shortcut to the Source list.
- **Dedicated Source Sheet (`SourceSheet`)**: Accessed via **long press** on the quality button or via the "Source" header button in the Quality sheet. Displays server and plugin streams (addon name + stream title + reported resolution badge); instant playback switch on selection.
- **Smart Fallback Routing**: When searching for alternative streams on episode switch or when stream resolution fails, the player automatically routes to the **Source list** (`SourceTracks`).

### 🔍 Addon Search & Explore Full Parity with Main Search
- **Standardized KitsugiCosmicSearchBar**: Plugin Picker screen, Plugin Picker bottom sheet, and Addon Explore dialog now adopt the identical cosmic search bar (56 dp height, 16 dp rounded corners, focus glow, unified clear and search actions).
- **Removed Floating 88 dp Overlay**: The transparent floating search bar in Addon Explore that pushed content down by 88 dp has been replaced with a natural, flowing header and search bar.
- **Single-Column Compact Rows (`AddonResultRow`)**: Replaced 3-column poster grids and horizontal strips with single-column horizontal rows matching main search results (56×80 poster, 16 dp neon glow border, quality/year/type/source badges) for consistent readability in portrait, landscape, and tablet modes.

### ⚡ Reliability & Verification
- Comprehensive JVM unit test suite (`QualitySheetOptionsTest`) added and verified for quality resolution grouping, sorting, and safe year reflection.
