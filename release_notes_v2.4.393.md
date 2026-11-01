# Kitsugi Beta v2.4.393

[TR]
### 🚀 Yenilikler ve Öne Çıkan Geliştirmeler

Bu sürümde; **3B Cam/Gradyan İkon Seti & Dinamik Vurgu Ton Uyumu (3dicons CC0)** ve **Bangumi Kapsamlı Düzeltme & API Entegrasyonu (R1–R28)** yer alıyor!

---

#### 🎨 1. 3B Cam & Gradyan İkon Seti (3dicons CC0)
- **50 Adet 3B Cam İkon:** Tüm sistem konu ve başlık ikonları (≥20 dp) 192×192 yüksek çözünürlüklü, saydam 3B cam/gradyan nesnelere dönüştürüldü.
- **Otomatik Vurgu Ton Kaydırması (Hue Rotation):** İkonlar sadece etrafındaki hâleyle değil, kendileri de seçtiğiniz tema vurgu rengine otomatik olarak döner (`LocalKitsugiIconHueShift`). Menekşe, mint, turkuaz, pembe, turuncu vb. hangi rengi seçerseniz seçin cam/3B parlaklığı bozulmadan ikon tonu o renge uyarlanır.
- **İki Renkli Gradyan Desteği:** Renk seçicide iki renkli gradyan seçildiğinde ikon tonu otomatik olarak iki rengin ortasından hesaplanır.
- **Renksiz / Düşük Doygunluk Koruması:** Gri, beyaz veya siyah gibi doygunluğu %12'nin altında olan vurgularda ikon paleti rastgele renge dönüştürülmez; doğal canlı mor-pembe kimliğinde kalır.
- **Karanlık, AMOLED ve Açık Tema Desteği:** Koyu ve AMOLED temalarda ikonlar doğal cam parlaklığında durur; açık temada arkalarına plaka konulmaz, zarif bir radyal yumuşak gölge eklenir.
- **Gezinme Efektleri:** Alt bar ve navigasyon rayında seçili sekmede yumuşak vurgu rengi arka plan çipi + radyal vurgu hâlesi (`accentGlow`) ve %100 opaklık; seçilmeyen sekmelerde %80–86 opaklık uygulanır.
- **Ayarlar Sayfaları İyileştirmesi:** Ayarlar listelerindeki eski 42 dp daire zemin plakaları kaldırıldı, ikonlar doğrudan satırda saydam ve ferah bir şekilde hizalanır (`KitsugiRowIcon`).
- **Tema Açma/Kapama Seçeneği:** `KitsugiAnimeListTheme(iconsFollowAccent = true)` varsayılan olarak açıktır.

---

#### ⛩️ 2. Bangumi Kapsamlı Düzeltme & API Entegrasyonu (R1–R28)
- **Arama & Profil Navigasyon Düzeltmesi (R28):** Arama ekranında Karakter ve Personel sekmesindeki Bangumi sonuçlarına tıklandığında AniList kimlik uzayında aranması ve yanlış/boş profil açılması hatası düzeltildi; kaynak parametresi uçtan uca taşınarak doğru istemci çağrılır.
- **Canlı API Doğrulamaları:** POST `/v0/search/persons` ve `/v0/search/characters` uç noktaları; seslendirmen (seiyu), mangaka, illüstratör ve yapım stüdyosu (şirket) filtreleriyle tam uyumlu hale getirildi.
- **Keşfet, Sıralama ve Detay Sayfaları:** Bangumi kaynaklı anime/manga içeriklerinin sıralama, filtreleme, detay sekmeleri ve kütüphane entegrasyonu optimize edildi.

---

[EN]
### 🚀 What's New & Highlights

This release brings the new **3D Glass/Gradient Icon Pack with Dynamic Accent Hue Shift (3dicons CC0)** and the **Bangumi Integration & API Overhaul (R1–R28)**!

---

#### 🎨 1. 3D Glass & Gradient Icon Set (3dicons CC0)
- **50 High-Definition 3D Glass Icons:** System topic and screen icons (≥20 dp) are upgraded to 192×192 transparent 3D glass/gradient artwork (`3dicons`, CC0).
- **Dynamic Accent Hue Rotation:** Icons automatically adapt their hue to match your selected theme accent color (`LocalKitsugiIconHueShift`). Whether using mint, violet, turquoise, pink, or orange, the icons seamlessly blend with your palette while preserving lighting and 3D depth.
- **Two-Color Gradient Support:** When a two-color gradient accent is selected, the icon hue is automatically interpolated from the midpoint of the two colors.
- **Colorless / Low-Saturation Protection:** If a neutral or monochrome accent is picked (saturation < 12%), hue rotation is bypassed, preserving the natural vibrant magenta-violet palette.
- **Dark, AMOLED & Light Theme Tuning:** Icons glow naturally in Dark and AMOLED themes; in Light theme, subtle radial drop shadows ensure clear contrast without clumsy background plates.
- **Tab & Navigation Polish:** Selected navigation items feature a soft accent background pill + radial glow (`accentGlow`) at 100% alpha; unselected items sit gently at 80–86% alpha.
- **Refined Settings Rows:** Clunky 42 dp circular background plates have been removed from settings items in favor of clean, transparent `KitsugiRowIcon` presentation.
- **Theme Option:** Enabled by default via `KitsugiAnimeListTheme(iconsFollowAccent = true)`.

---

#### ⛩️ 2. Bangumi Overhaul & Navigation Fixes (R1–R28)
- **Character & Staff Navigation Fix (R28):** Fixed an issue where clicking Bangumi character or staff search results queried AniList ID space, leading to wrong or empty profiles. The source context is now passed end-to-end.
- **Verified API Endpoints:** Validated live endpoints for character, staff, mangaka, illustrator, and studio queries against the Bangumi v0 API contract.
- **Explore & Rankings Polish:** Enhanced Discover carousels, See All grids, and rankings for Bangumi entries.
