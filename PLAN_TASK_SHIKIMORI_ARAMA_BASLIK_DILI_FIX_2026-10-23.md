# Plan Task — Shikimori Arama Başlık Dili Düzeltmesi

**Tarih:** 2026-10-23  
**Dal:** `arena/fecb3472-kitsugi-beta`  
**Durum:** Kod düzeltmesi tamamlandı; test/cihaz doğrulaması JDK eksikliği nedeniyle bekliyor.

## Şikayet

Arama sayfasında Shikimori rafındaki anime/manga başlıkları, Ayarlar'da seçilen başlık diliyle gösterilmiyor. Diğer kaynaklar (ör. AniList ve Kitsu) seçili dilde görünürken Shikimori sonuçları Romaji adlarla kalıyor.

## Kök neden

Shikimori REST arama uçları çoğunlukla `name` (Romaji) ve `russian` alanlarını döndürüyor; İngilizce ve Japonca başlıklar bu sonuçlarda bulunmuyor. `KitsugiShikimoriClient` arama eşleyicileri `name` değerini yanlışlıkla `titleEnglish` alanına yazdığı için ortak başlık seçici bunu gerçek İngilizce başlık sanıyordu. `ShikimoriTitleResolver` ise İngilizce/Japonca adları GraphQL'den yalnızca ilişki/öneri akışında tamamlıyordu; arama ve keşfet sonuçlarında kullanılmıyordu.

## Hedef ve kabul ölçütleri

- [x] Shikimori `name` değeri `titleEnglish` değil, `titleRomaji` alanında tutulmalı.
- [x] Anime ve manga arama/keşfet sonuçlarına GraphQL'den gerçek İngilizce ve Japonca adlar eklenmeli.
- [x] Sonuçlar ortak kullanıcı tercihine uygun gösterilmeli: İngilizce, Romaji veya Japonca.
- [x] Çözümleme Shikimori'nin kendi ID'sini kullanmalı; `realMalId`/MAL kimliği değiştirilmemeli.
- [x] Görsel ve başlık zenginleştirmeleri paralel çalışmalı; başlık isteği en fazla 6 saniye sürmeli ve başarısızlıkta Romaji yedeği korunmalı.
- [x] Başlık sorgusu, ayrıştırma, sonuç eşleme ve dil tercihi için regresyon testleri eklenmeli.

## Yapılan değişiklikler

1. `KitsugiShikimoriClient.searchAnime`, `searchManga` ve `searchMediaAdvanced` eşlemeleri Romaji değeri `titleRomaji` alanına taşındı.
2. Arama/keşfet medya sonuçları, `ShikimoriTitleResolver` ile anime/manga türü başına toplu biçimde zenginleştiriliyor. Poster çözümleme ile paralel çalışıyor.
3. İngilizce ve Japonca varyantlar ortak `JikanSearchResult` kayıtlarına Shikimori ID'si üzerinden ekleniyor.
4. GraphQL başlık isteğine 6 saniyelik çağrı zaman aşımı eklendi; istek başarısız olsa bile mevcut Romaji başlık korunuyor.
5. İngilizce, Romaji ve Japonca gösterimi ile GraphQL yanıt ayrıştırmasını doğrulayan `ShikimoriTitleResolverTest` eklendi.

## Değişen dosyalar

- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiShikimoriClient.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/ShikimoriTitleResolver.kt`
- `app/src/test/java/com/kitsugi/animelist/data/remote/ShikimoriTitleResolverTest.kt`

## Doğrulama

- `git diff --check` başarılı.
- Kaynak düzeyindeki Shikimori başlık dili kontrolleri başarılı.
- `./gradlew :app:testFossDebugUnitTest --tests com.kitsugi.animelist.data.remote.ShikimoriTitleResolverTest --no-daemon` denendi; ortamda Java/JDK bulunmadığı için test başlamadı (`JAVA_HOME is not set and no 'java' command could be found`).

## Sonraki doğrulama adımları

- [ ] JDK bulunan CI/geliştirici ortamında `ShikimoriTitleResolverTest` testini çalıştır.
- [ ] Uygulamada aynı aramayı İngilizce, Romaji ve Japonca başlık tercihleriyle doğrula.
- [ ] Shikimori GraphQL erişilemediğinde arama sonuçlarının Romaji yedeğiyle geldiğini doğrula.
