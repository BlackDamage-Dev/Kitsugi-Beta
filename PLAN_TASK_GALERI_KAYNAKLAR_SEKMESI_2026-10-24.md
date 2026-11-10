# Plan Task — Galeri "Diğer Resimler" Sekmesi → "Kaynaklar"

**Tarih:** 2026-10-24
**Durum:** Tamamlandı

## İstenenler

- Galeri diyaloğunun (yatay düzen) sağ panelindeki "Diğer Resimler" sekmesinin adı,
  içeriğine (kaynaklara) göre değiştirilsin: **"Kaynaklar"**.
- Bu sekmede hiçbir görsel önizleme gösterilmesin; yalnızca kaynak listesi yer alsın.

## Uygulama

- `gallery_tab_other_images` dizesi TR'de "Kaynaklar", EN'de "Sources" olarak güncellendi
  (`values/strings.xml`, `values-en/strings.xml`).
- `GalleryLandscapeLayout` ikinci sekme içeriği (aynı kategoriden küçük görsel ızgarası)
  tamamen kaldırıldı; yerine yalnızca kaynak listesi konuldu:
  - İlk satır: 📁 **Tümü** + toplam görsel sayısı.
  - Sonrası: her kaynak için bir satır — platform logosu (`KitsugiPlatformLogo`),
    kaynak adı ve o kaynağın görsel adedi (ekran görüntüsündeki listeyle aynı düzen).
  - Yeni özel bileşen: `GallerySourceRow` (logo + ad + adet, seçili satır vurgulu).
- Kaynak satırına dokunmak galeriyi o kaynağa filtreler:
  - `KitsugiImageGalleryDialog` içine `selectedSourceFilter` durumu eklendi
    (null = Tümü).
  - `filteredItems` artık kategori filtresiyle birlikte kaynak filtresini de uygular.
  - Sayfa taşmasını önleyen sıfırlama `LaunchedEffect`'i iki filtreyi de izler.
  - "Tümü" satırı filtreyi temizler.
- Dikey (portrait) düzen değişmedi; sekme yalnızca yatay sağ panelde bulunur.

## Değişen dosyalar

- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiImageGalleryDialog.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`

## Doğrulama

- `git diff --check`: temiz.
- Parantez/imza dengesi elle doğrulandı (gradient `Text`/`Icon`/`border`,
  `KitsugiPlatformLogo` imzalarıyla birebir uyumlu).
- `:app:compileDebugKotlin`: çalıştırılamadı; çalışma ortamında `java`/`JAVA_HOME` yok.
