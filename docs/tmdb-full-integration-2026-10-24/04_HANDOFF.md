# HANDOFF — Gemini ile entegrasyon ve APK doğrulaması

Güncelleme: 2026-10-24  
Branch: `arena/0d16774b-kitsugi-beta`  
Baz commit: `3d3242ffb9a99359a7afc4db9bc126a0e511cc48`

## Paketi uygulama

Bu teslim ZIP'i tam repo değil, **baz commit üzerine açılacak değişen dosyaların tamamıdır**. Yeni (untracked) kaynakların tam içeriği ve tracked dosyaların güncel tam içeriği bulunur; ayrı patch uygulamak gerekmez.

1. Aynı deponun temiz bir çalışma kopyasını aç.
2. `3d3242ffb9a99359a7afc4db9bc126a0e511cc48` commit'ine dayalı yeni bir çalışma dalı oluştur.
3. ZIP'i repo kökünde aç; `app/...` ve `docs/...` yollarının üzerine yazılmasına izin ver.
4. `FILE_MANIFEST.tsv` ile dosya listesini, `SHA256SUMS.txt` ile aktarım bütünlüğünü kontrol et.
5. Android SDK yolunu ve mevcut `tmdb_api_key` değerini kendi `local.properties` dosyanda yapılandır. **Bu dosya ve hiçbir kullanıcı tokenı/session'ı teslim paketinde yoktur.**
6. Önce `./gradlew testDebugUnitTest`, ardından `./gradlew assembleDebug` çalıştır. Derleme hatası olursa eski davranışa dönmek için native TMDB alanlarını kaldırma; hatayı ilgili kaynak/callback zincirinde düzelt.
7. Oda şemasını cihazda/API emülatörde 29→30 migrasyon testiyle doğrula.

Plan ve kabul listesi: `docs/tmdb-full-integration-2026-10-24/02_PLAN_TASK.md`  
Güncel doğrulama: `docs/tmdb-full-integration-2026-10-24/06_VALIDATION.md`

## Tamamlanan ana kapsam

- Ayrı `tmdb_account` hesap kaynağı ve Listem TMDB sekmesi; eski katalog `tmdb` kaynağından ayrım.
- Watchlist, favorite ve 0,5 adımlı rating için altı sayfalı koleksiyonun birleşimi; film/TV namespace'leri ayrı.
- Resmî v3 request-token → onay → session → account akışı; TTL, callback eşleşmesi ve tek kullanım.
- Alan maskeli yazma, çevrimdışı kuyrukta `_tmdbAccountId`, yanlış hesapta yazmama ve yazma ortasında kalan alanları durdurma.
- Yazma öncesinde canlı `account_states` karşılaştırması; aynı değere gereksiz POST/DELETE yok, null rating resmî DELETE rating yolunda.
- Editörde Watchlist/favorite/rating için ayrı dirty-state; canlı okuma kullanıcının dokunduğu alanı ezmez ve Kitsugi-only editler native niyet üretmez.
- Snapshot/write/login/logout için ortak operation mutex; eski hesap snapshot'ı yerel import öncesinde reddedilir.
- Hesap-bazlı bildirim baseline'ı; ilk snapshot sessiz, teslim başarısızsa baseline ilerlemez, başarılı teslimden sonra stable ID'lerle tekrar üretilmez.
- Await edilen bildirim arşivi yazımı ve yazı tamamlandıktan sonra bulut senkron tetikleme.
- Film özel listeleri için resmî v3 `item_status`, create, add/remove; hesap değişimi guard'ları.
- AniList/MAL/Simkl/Kitsu/Shikimori/Bangumi fan-out, hedef-bazlı kimlik doğrulama ve provider-scope korumaları.
- Room 30, 29→30 migrasyon, native alanlı yedekleme, profil/ayarlar/bildirim/TV ve TR/EN hata metinleri.

## Son çalıştırılan kontroller

- **61/61** kimlik, gruplama, migrasyon, TMDB contract ve bildirim politikası testi PASS.
- **7/7** notification scheduler ve **3/3** cross-write gate testi PASS; toplam **71 seçili birim testi**.
- **8/8 fonksiyonel grup** PASS: gerçek auth/client/mutex/coordinator/sync/poller/backup/drainer kaynakları, sahte HTTP ve Android test double'ları.
- **12/12** TMDB hata sınıflandırması PASS; ilgili kaynaklar bu kontrolden sonra değişmedi.
- Room 29→30 SQL/kolon ve identity-hash kontrolleri PASS; 30 hash `7ac1f9538df18d2e5dd257582a0f27df`.
- TR ve EN XML ayrı ayrı 17.597 string, mükerrer isim yok; `git diff --check` temiz.

Bunlar tam Android/APK derlemesi iddiası değildir. Ayrıntı ve sınırlar `06_VALIDATION.md` içindedir.

## Gemini'nin cihazda tamamlaması gereken kabul testleri

1. WebView ve harici tarayıcıda onay/session; yanlış, expired ve replayed callback.
2. Watchlist/favorite/rated film+TV birleşimi; çok sayfalı hesap ve başarısız sayfada deletion guard.
3. Bağımsız Watchlist/favorite, 7,5 rating, rating DELETE ve aynı değere no-op preflight.
4. Film `item_status`, liste oluşturma, add/remove; TV/karışık v4 yazımının başarı gibi gösterilmemesi.
5. Yazma öncesinde ve ortasında hesap değiştirme; eski snapshot import olmamalı, yeni hesaba eski kuyruk işi gitmemeli.
6. Otomatik senkron kapalıyken doğrudan TMDB hesap satırı işlemi ile kardeş-provider fan-out davranışının ayrımı.
7. Bildirim teslim/arşiv hatasında retry, process death sonrası stable-ID davranışı ve hesaplar arasında sessiz ilk baseline.
8. Room 29→30 gerçek veri migrasyonu, yedek round-trip, telefon/TV ve TR/EN UI.

## Bilinçli ürün sınırları

- v3 özel liste item mutation bu entegrasyonda **film-only**dir. TV/karışık/private v4 liste yazımı için ayrı v4 user access-token iş paketi gerekir; v3 session ile taklit edilmez.
- TMDB'nin resmî bildirim feed'i yoktur; bildirimler tam snapshot farkından cihazda üretilir.
- TMDB'de Watching/Completed, bölüm ilerlemesi, tarih ve not alanları yoktur. Bunlar Kitsugi'de korunur ve uydurma uzak veriye dönüştürülmez.
- Katalog API anahtarı kullanıcı yazma yetkisi değildir.

Drive klasörü: https://drive.google.com/drive/folders/1Ib2NeRLT1XkgTBLmMOGMovCNB4bmzJ4p
