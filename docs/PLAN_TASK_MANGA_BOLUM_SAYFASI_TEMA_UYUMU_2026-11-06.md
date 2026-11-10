# PLAN TASK — Manga Bölüm Sayfası Tema Uyumu (2026-11-06)

## Amaç
Manga bölüm listesi sayfasının (telefon + TV) görünümünü uygulamanın geri kalan
sayfalarıyla tam uyumlu, "uygulamaya ait" bir tasarıma kavuşturmak. Hiçbir işlevsel
özellik değiştirilmeyecek.

## Değişen dosyalar
1. `app/src/main/java/com/kitsugi/animelist/ui/screens/manga/MangaDetailScreen.kt`
2. `app/src/main/java/com/kitsugi/animelist/ui/tv/manga/TvMangaDetailScreen.kt`

## Yapılan görsel değişiklikler
- "Bölümler (N)" başlığı: düz satır → `SurfaceSoft` zeminli, 14.dp köşeli kart.
- Yenile butonu: sönük gri ikon → `SurfaceStrong` zeminli yuvarlak buton, accent rengi ikon.
- Bölüm satırları: kart görünümü (`SurfaceSoft`, 14.dp köşe); devam eden bölüm
  `accent.copy(0.14f)` ile vurgulu.
- Geri butonu: yarı saydam siyah daire → `SurfaceStrong` daire + `TextPrimary` ikon (TV dahil).

## Korunan işlevler
Bölüm sayısı, yenileme, okundu tikleri, "Sayfa x/y" bilgisi, tıklama işlevleri,
yükleniyor/hata durumları (ortak Kitsugi bileşenleri), Fandom Wiki kartı — hepsi aynı.

## Doğrulama
- Süslü/parantez dengesi: 0 fark (her iki dosya).
- Tüm import'lar ve `background(color, shape)` overload'ı kaynakta mevcut.
- Sandbox'ta Java olmadığı için Gradle derlemesi çalıştırılamadı.
