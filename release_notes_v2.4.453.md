# Kitsugi Beta v2.4.453 — Manga Bölüm Sayfası Tema Uyumu (Design & Theme Cohesion)

### 🇹🇷 Türkçe Güncelleme Özeti

Bu sürümle birlikte manga detay ve bölüm sayfaları (mobil ve Android TV), uygulamanın genel görsel diliyle (`KitsugiColors` + accent tokenları) tam uyumlu hale getirilmiştir.

#### 🎨 Yenilikler ve Görsel İyileştirmeler:
1. **Geri Butonları:**
   - Sayfa tepesindeki yüzen geri butonu, uygulamanın standart tasarım dili olan `SurfaceStrong` (%75 opaklık) dairesel kart zeminine ve `TextPrimary` ikon rengine kavuştu.
   - TV ekranındaki geri butonu da aynı şekilde `SurfaceStrong` zemin ve `TextPrimary` rengi ile güncellendi.
2. **Bölümler Başlık Şeridi:**
   - Bölümler başlığı ve sayaç alanı, 14.dp (`TvMangaDetailScreen` için 12.dp) yuvarlak köşeli `SurfaceSoft` zeminli modern kart görünümüne taşındı.
   - Yenile butonu, `SurfaceStrong` zeminli ve accent vurgulu yuvarlak buton olarak modernize edildi.
3. **Bölüm Satırları:**
   - Hem tekil bölüm satırları (`ChapterListItem`) hem de çevirmen gruplu satırlar (`ChapterGroupListItem`), 14.dp köşeli `SurfaceSoft` kart düzenine geçirildi.
   - Okunmakta / devam eden bölümler `accent.copy(0.14f)` ile belirgin ve şık bir şekilde vurgulanıyor.
   - TV ekranındaki bölüm satırları da 12.dp köşeli `SurfaceSoft` ve `AccentBlue` vurgulu kartlara dönüştürüldü.
4. **İşlevsellik ve Kararlılık:**
   - Önceki sürümlerdeki çevrimdışı indirme, çevirmen seçim pencereleri (`MangaChapterChoiceDialog`), sayfa galerisi, filtreler ve arama işlevleri eksiksiz korunmuştur.

---

### 🇬🇧 English Release Notes

Kitsugi Beta v2.4.453 refines the visual cohesion of the manga detail and chapter pages across both mobile and Android TV, adhering completely to Kitsugi design system tokens (`KitsugiColors` + dynamic accents).

#### 🎨 Highlights:
1. **Back Buttons:**
   - Floating back button now features a `SurfaceStrong` (75% opacity) circular badge with `TextPrimary` icon tint, matching the rest of the application.
   - TV back button updated to `SurfaceStrong` and `TextPrimary` for unified styling.
2. **Chapters Header:**
   - Chapter section header now wraps in a 14.dp (12.dp on TV) rounded `SurfaceSoft` card surface.
   - Circular refresh button restyled with `SurfaceStrong` background and dynamic accent tint.
3. **Chapter Rows:**
   - Both single chapters and multi-translator group rows now render on 14.dp rounded `SurfaceSoft` cards.
   - In-progress chapters are highlighted with `accent.copy(0.14f)`.
   - Android TV chapter items updated with 12.dp `SurfaceSoft` cards and `AccentBlue` tinting.
4. **Full Feature Preservation:**
   - Offline chapter downloads, translator choice dialogs, page gallery, search, and sorting remain 100% intact.
