# Plan Task — Manga Bölüm Sayfası Düzenlemesi

**Tarih:** 2026-11-07  
**Durum:** Uygulandı ve FOSS Release derlemesiyle doğrulandı.  
**Kapsam:** Android uygulamasındaki telefon manga detay/bölüm listesi ekranı.

## Amaç

Manga bölüm listesini Kitsugi'nin mevcut karanlık tema, vurgu rengi ve kart diliyle tutarlı hâle getirmek; bölüm keşfini tek arama alanı, durum filtreleri ve alttan açılan sıralama paneliyle kolaylaştırmak.

## İnceleme ve tasarım kararı

- Manga ekranının mevcut `MangaDetailScreen` / `MangaDetailViewModel` akışı incelendi; bölüm okuma ilerlemesi, indirme, yer imi, çevirmen gruplama ve kaynağın bölüm sırası korunacak şekilde değişiklik uygulandı.
- Projedeki `KitsugiColors`, tema uyumlu `tvClickable` ve mevcut `ModalBottomSheet` örüntüleri referans alındı.
- Kullanıcı eskiden yeniye, kaynak sırasına, son güncellenene veya ada göre (A–Z / Z–A) sıralayabiliyor.

## Uygulanan işler

- Bölüm listesi için tek bir arama alanı: bölüm adı, numarası ve çevirmen bilgisi içinde arama, metni temizleme.
- Durum filtreleri: Tümü, Okunmadı, Devam ediliyor ve Okundu.
- Sıralama açılır menüsü yerine Kitsugi temalı alt panel (`ModalBottomSheet`): eskiden yeniye, kaynak sırası, son güncellenen ve isim A–Z / Z–A + bölüm görünüm modu.
- Toplam/tamamlanan bölüm sayısı, okuma ilerleme çubuğu ve devam edilen bölüm sayısı özeti (`ChapterProgressSummary`).
- Bölüm satırlarının tema uyumlu kartlarla sunulması; okundu/devam ediyor göstergeleri.
- Eşleşme yoksa arama veya filtre için açıklayıcı boş durum kartı.

## Değişen dosyalar

- `app/src/main/java/com/kitsugi/animelist/data/manga/ChapterSorting.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/manga/MangaDetailScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/manga/components/MangaChapterTools.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/tv/manga/TvMangaDetailScreen.kt`

## Kabul ölçütleri

- Aynı sayfada tek bölüm arama alanı görünür.
- Arama bölüm adı ve çevirmen bilgisine göre sonuçları daraltır; filtreler sıralama ile birlikte çalışır.
- Sıralama seçimi alttan açılan panelde yapılır ve seçilen değer listeye uygulanır.
- Bölüm okuma/devam etme tıklamaları mevcut akışa devam eder.
- Yeni görünüm Kitsugi renklerini ve yüzeylerini kullanır.
