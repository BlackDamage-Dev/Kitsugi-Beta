# Plan Task — Türkçe Eşleşme, Eklenti Portalı Araması ve Listem Performansı

**Tarih:** 2026-10-27  
**Dal:** `feat/golge-bahcesi-integration`  
**Durum:** Uygulandı ve doğrulandı; Gradle testleri başarıyla çalıştırıldı.

## Kullanıcı bildirimi

- Türkçe başlıklar üzerinden eklenti ve diğer kaynaklarda bulunan içeriklerin yanlış eşleşmesi; özellikle detay sayfasında alakasız fragman gösterilmesi.
- Eklenti Portalı arama kutusunun eklenti kayıtlarını gerçekten araması.
- Listem arama alanında yazarken ve metni silerken yaşanan takılma.

## Kök nedenler

1. Fragman yedek akışı, Kitsu'nun tek sonuç döndüren metin aramasını başlık/yıl doğrulaması yapmadan kabul edebiliyordu. Cloudstream `searchAllAddons` sonuçları da doğrulanmadan ilk üç sonucun `load()` trailer'ı kullanılabiliyordu. Arama sonucu ve yüklenen sayfa başlığı uyuşmadığında, başka bir yapımın fragmanı detay sayfasına eklenebiliyordu.
2. Cloudstream başlık normalizasyonunda `İ` küçük harfe çevrilince ortaya çıkan U+0307 bir kelime ayırıcıya dönüşüyor, örneğin `İstanbul` başlığını hatalı biçimde bölüyordu.
3. Eklenti portalı yalnızca provider adı, adresi ve dili üzerinde doğrudan küçük harfli arama yapıyordu; kaynak eklenti kimliği/sınıfı kapsam dışındaydı ve Türkçe aksan/İ varyantları normalize edilmiyordu.
4. Listem filtreleme ve sıralama her metin değişiminde Compose ana iş parçacığında çalışıyordu. Büyük listelerde ve fuzzy arama açıkken bu, giriş alanında takılmaya yol açabiliyordu.

## Uygulama planı / tamamlananlar

- [x] Kitsu fragman sonucu için başlık benzerliği eşiği ve bilinen yıl tutarlılığı doğrulaması ekle.
- [x] Eklenti fragmanında arama sonucunu mevcut `CsTitleMatcher` ile doğrula; yüklenen `LoadResponse` başlığını da fragman kabulünden önce kontrol et.
- [x] Türkçe büyük noktalı `İ` normalizasyonundaki birleşik noktayı temizle.
- [x] Eklenti portalı aramasını eklenti adı, URL, dil, kaynak eklenti yolu ve sınıf adıyla eşleştir; Türkçe harf/aksan varyantlarını normalize et ve kaynak metinleri önbelleğe al.
- [x] Listem filtreleme/sıralama işini `Dispatchers.Default` üzerine taşı; arama girişini kısa süre grupla, önceki sonuçları koru ve iptal edilen filtre işlerini periyodik olarak sonlandır.
- [x] Türkçe başlık, portal normalizasyonu ve yanlış anime eşleşmesi için regresyon testleri ekle.

## Kabul kriterleri

- Alakasız Kitsu/eklenti arama sonuçları, başlık doğrulamasını geçmeden fragman sağlayamaz.
- `İstanbul` ile `Istanbul` ve Türkçe aksanlı/ASCII arama varyantları eşleşebilir.
- Portal araması eklenti adının yanında sağlayıcı URL'si, dili ve kaynak eklenti bilgisinde de çalışır.
- Listem araması/sıralaması kullanıcı girişini ana iş parçacığında bloke etmez; eski arama işi iptal edilir.

## Değişen dosyalar

- `app/src/main/java/com/kitsugi/animelist/data/cloudstream/CsTitleMatcher.kt`
- `app/src/main/java/com/kitsugi/animelist/data/trailer/DetailTrailerFallback.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListFilterHelpers.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/mylist/MyListScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/search/PluginPickerScreen.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/search/PluginPortalSearch.kt`
- `app/src/test/java/com/kitsugi/animelist/data/cloudstream/CsTitleMatcherTest.kt`
- `app/src/test/java/com/kitsugi/animelist/ui/screens/mylist/MyListFilterHelpersTest.kt`
- `app/src/test/java/com/kitsugi/animelist/ui/screens/search/PluginPortalSearchTest.kt`

## Doğrulama

- `git diff --check`: başarılı.
- `./gradlew.bat :app:testFossDebugUnitTest`: başarılı (Android Studio JBR OpenJDK 21 ile çalıştırıldı, 72/72 görev tamamlandı).
