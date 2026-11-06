# PLAN TASK — Manga Bölüm Listesi: Çevirmen Seçimi (2026-11-06)

## Sorun
Aynı bölüm numarasının farklı çevirmen/eklentilerden (AnimeWho, WebtoonTR, Tempest Fansub vb.)
gelen sürümleri bölüm listesinde ayrı satırlar olarak görünüyordu (ör. "Bölüm 1" x3).

## Hedef
- Aynı bölüm numarasına ait çeviriler tek satırda gruplanır.
- Satır, çeviri sayısını ve çevirmen adlarını gösterir.
- Satıra dokununca çevirmen seçim penceresi (alt sayfa) açılır; seçilen çeviri okuyucuda açılır.
- Tek çevirmenli bölümler doğrudan açılır (eski davranış).

## Kapsam / Dosyalar
- `app/src/main/java/com/kitsugi/animelist/ui/screens/manga/MangaDetailViewModel.kt`
  - `ChapterGroup` modeli, `UiState.groups`
  - `groupChapterRows()`: numaraya göre gruplama (kaynak numarası yoksa addan `ChapterRecognition` ile çıkarma;
    numarası çözülemeyenler birleşmez)
- `app/src/main/java/com/kitsugi/animelist/ui/screens/manga/MangaDetailScreen.kt`
  - `ChapterGroupListItem` (grup satırı)
  - `ChapterTranslatorPickerSheet` (çevirmen seçim penceresi, `KitsugiSheetOrDialog` ile)

## Korunan Davranış
- "Devam Et" / "Baştan Oku" hedefi bölüm URL'sine göre çalışmaya devam eder.
- Okundu / devam ediliyor işaretleri her çeviri için ayrı tutulur.

## Doğrulama Listesi
- [ ] Aynı numaralı çok çevirmenli bölüm tek satır olarak görünüyor
- [ ] Satıra dokunulunca çevirmen penceresi açılıyor, seçim okuyucuyu doğru çeviriyle açıyor
- [ ] Tek çevirmenli bölüm doğrudan açılıyor
- [ ] Numarası olmayan bölümler (ör. Oneshot) birleşmiyor
- [ ] Devam Et / Baştan Oku doğru bölümü açıyor
- [ ] Okundu tiki ve sayfa ilerlemesi doğru gösteriliyor
- [ ] Android TV'de pencere D-pad ile gezilebiliyor

## Durum
- Kod yazıldı (commit 545f8c3). Derleme ve cihaz testi bekleniyor.
