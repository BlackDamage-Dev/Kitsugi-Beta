# PLAN / TASK — Detay Açıklamasının Çeviri Yüzünden Dakikalarca İskalette Kalması

**Tarih:** 2026-10-25  
**Dal:** `arena/6721c9bc-kitsugi-beta`  
**Durum:** Kod düzeltmesi tamamlandı

## 1. Kullanıcı Şikâyeti

Kaynak fark etmeksizin medya detaylarındaki **Açıklama** metni eskiden hemen görünürken artık dakikalarca yükleme iskeletinde (shimmer/skeleton) kalıyor. Diğer detay kartları ve çeviri/kopyalama simgeleri bu sırada ekranda görünebiliyor.

## 2. Kök Neden

Sorun kaynak API'lerinin yavaşlığından ziyade arama/keşfet detay ekranındaki yükleme durumunun yanlış birleştirilmesiydi:

- `ApiResultDetailPage`, `translatedSynopsis == null && detailState?.synopsis != null` ifadesini `isTranslating` olarak gönderiyordu.
- Bu ifade gerçek çeviri işinin çalışıp çalışmadığını değil, yalnızca çevrilmiş metnin henüz bulunmadığını gösteriyordu.
- `ApiDetailOverviewTab`, bu değeri detay yüklemesiyle birlikte `ApiSynopsisCard.isLoading` içine katıyordu.
- `ApiSynopsisCard`, metin dolu olsa bile `isLoading == true` olduğunda metin yerine iskelet çiziyordu.

Sonuç olarak ham açıklama hazır olduğu hâlde otomatik çeviri bitene kadar gizleniyordu. Otomatik çeviri kapalıyken de sahte `isTranslating` durumu iskeletin takılı kalmasına yol açabiliyordu. Ekranda çeviri ve kopyalama simgelerinin görünmesi, açıklamanın aslında çoktan gelmiş olduğunun doğrudan kanıtıydı; çünkü bu simgeler yalnızca metin boş değilken çiziliyor.

## 3. Uygulanan Çözüm

1. **Çeviri artık açıklamanın görünürlüğünü kontrol etmiyor.**
   - Hatalı `isTranslating` parametresi ve hesaplaması kaldırıldı.
   - Açıklama kartına yalnızca gerçek detay yükleme durumu iletiliyor.

2. **Metin, yükleme iskeletinden daima öncelikli.**
   - `shouldShowApiSynopsisSkeleton(synopsis, isDetailLoading)` kuralı eklendi.
   - İskelet yalnızca `synopsis.isNullOrBlank() && isDetailLoading` olduğunda gösteriliyor.
   - Ham veya çevrilmiş metin mevcutsa, arka planda başka bir iş sürse bile metin ekranda kalıyor.

3. **Mevcut çeviri davranışı korunuyor.**
   - Ham açıklama hemen görünür.
   - Otomatik çeviri arka planda çalışır ve sonuç geldiğinde görünen metni günceller.
   - Gerçek `translationLoading` durumu çeviri simgesinin animasyonu için kullanılmaya devam eder.
   - Çeviri kapalı, başarısız veya yavaş olduğunda ham açıklama kaybolmaz.

4. **Regresyon testleri eklendi.**
   - Metin yokken ve detay yüklenirken iskelet gösterilir.
   - Metin varken yükleme durumu iskeleti devreye sokmaz.
   - Detay tamamlanmış ve açıklama yoksa sonsuz iskelet yerine boş durum gösterilir.

## 4. Değiştirilen / Eklenen Dosyalar

- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailPage.kt`
  - Yanlış `isTranslating` hesaplaması kaldırıldı.
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiDetailTabContents.kt`
  - Gereksiz `isTranslating` parametresi kaldırıldı; kart yalnızca detay yükleme durumunu alıyor.
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailComponents.kt`
  - Metin öncelikli `shouldShowApiSynopsisSkeleton` politikası eklendi ve karta uygulandı.
- `app/src/test/java/com/kitsugi/animelist/ui/screens/detail/ApiSynopsisLoadingPolicyTest.kt`
  - Üç regresyon testi eklendi.

## 5. Kontrol Edilen Diğer Akışlar

- `MediaEntryDetailViewModel`: kütüphane detayında ham metni çeviriden önce `SynopsisState.Success(rawText)` ile yayınlıyor.
- `KitsugiApiSearchDialog`: kaynak önizlemesinde ham açıklamayı çeviriden önce gösteriyor.
- `KitsugiHeroPreviewDialog`: vitrin önizlemesinde ham açıklamayı çeviriden önce gösteriyor.
- `ApiResultDetailViewModel`: görünen metin için `translatedSynopsis ?: detailState?.synopsis` yedeği bulunuyor; çeviri sonucu yoksa ham metin korunuyor.

## 6. Doğrulama

- `git diff --check`: **Başarılı**.
- Statik akış kontrolleri: **Başarılı**.
  - Hatalı `isTranslating` çağrısının kaldırıldığı doğrulandı.
  - Açıklama kartında metnin iskelete üstün geldiği doğrulandı.
  - Kütüphane ve iki önizleme akışının ham metni çeviriden önce yayınladığı doğrulandı.
- Üç JUnit regresyon testi kaynak koda eklendi.
- Bu çalışma ortamında Java/Android SDK bulunmadığı için Gradle testleri ve cihaz üzerinde görsel doğrulama çalıştırılamadı.

## 7. Beklenen Sonuç

| Durum | Beklenen davranış |
|---|---|
| Kaynak açıklamayı döndürdü, çeviri kapalı | Açıklama hemen görünür; iskelet kalmaz. |
| Kaynak açıklamayı döndürdü, otomatik çeviri sürüyor | Ham açıklama hemen görünür; çeviri gelince metin güncellenir. |
| Çeviri başarısız veya çok yavaş | Ham açıklama ekranda kalır. |
| Açıklama henüz hiç yok ve detay yükleniyor | Yükleme iskeleti gösterilir. |
| Detay tamamlandı ancak kaynakta açıklama yok | İskelet yerine “açıklama yok” durumu gösterilir. |
