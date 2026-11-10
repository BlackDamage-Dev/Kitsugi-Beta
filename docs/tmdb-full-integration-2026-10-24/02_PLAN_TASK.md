# PLAN / TASK — TMDB tam entegrasyon

Branch: `arena/0d16774b-kitsugi-beta`  
Tarih: 2026-10-24  
Durum: kod ve seçili doğrulamalar tamamlandı; tam Android derlemesi ile gerçek cihaz/hesap doğrulaması açık.

## Kabul kriterleri

- [x] TMDB hesabındaki film/TV verisi ayrı **TMDB** Listem sekmesinde görünür.
- [x] Katalog satırı ile hesap üyeliği ayrılır; aynı içerik “Tümü”nde doğrulanmış kimlikle tek birleşik kartta kalır.
- [x] TMDB Watchlist/favori/puan okunur ve kullanıcı yetkisiyle yazılır; editör açılışında canlı `account_states` okunur.
- [x] Yerel ekleme/düzenleme/favori/puan/silme, otomatik senkron ayarına ve doğrudan hesap işlemi ayrımına uygun biçimde TMDB’ye ve diğer bağlı sağlayıcılara güvenli kimlikle yayılır.
- [x] TMDB tam snapshot içe aktarımı tekrarlanabilir; kullanıcı ilerlemesini/notunu ezmez ve başarısız sayfa silme kanıtı sayılmaz.
- [x] Ayarlar, profil, bildirim gate’leri, senkron raporları ve hesap yedeği TMDB’yi tanır.
- [x] Native Watchlist, Favorite ve 0,5 adımlı rating birbirinden bağımsızdır; `WatchStatus` bunların yerine geçirilmez.
- [x] Film özel listeleri `item_status` ile okunur; liste oluşturma ve film ekleme/çıkarma resmî v3 uçlarıyla yapılır.
- [x] v3/v4 özel liste sınırları açık; desteklenmeyen TV/karışık liste yazımı başarı gibi gösterilmez.
- [x] Request token yaklaşık 15 dakikayla sınırlı, callback’e bağlı ve tek kullanımlıktır; WebView → tarayıcı geçişi aynı tokenı kullanır.
- [x] TMDB `status_code` değerleri sabit Kitsugi hata kodlarına eşlenir ve iki dilli açıklamaları vardır.
- [x] Seçili saf Kotlin testleri, izole gerçek-kaynak derlemeleri ve SQL/Room şema kontrolleri çalışır.
- [ ] Tam Gradle `testDebugUnitTest` / `assembleDebug` ve Room cihaz migrasyon testi çalışır. **Ortamda Android SDK/Gradle dağıtımı yok.**
- [ ] Gerçek TMDB hesabıyla onay, session, Watchlist/favorite/rating ve özel liste yazma cihazda doğrulanır.
- [x] Araştırma, karşılaştırma, kod/değişiklik arşivi ve devir notu için Drive teslim paketi hazırlanır.

## Tamamlanan iş paketleri

1. **Kimlik ve Listem**
   - `tmdb_account` ile eski katalog `tmdb` kaynağı ayrıldı.
   - Sağlayıcı-sekmeli eşleşmede hesap satırı Simkl/katalog üyeliğini ödünç almaz.
   - “Tümü” görünümü doğrulanmış kimliklerle gruplar; çelişkili ID’lerde birleştirme/veri silme yapılmaz.
   - TMDB sekmesinden API araması ortak ekleme/fan-out zincirine bağlandı; yalnız isimle sahte hesap üyeliği üretilemez.

2. **Native hesap modeli**
   - `tmdbWatchlist: Boolean?` ve `tmdbRating: Double?` eklendi.
   - Room 30 ve 29→30 migrasyonu; nullable kolonlar ve `30.json` üretildi.
   - Yedek ve bekleyen sync JSON’u iki native alanı da taşır.
   - Kardeş satır senkronunda ortak Int puan değişirse eski native rating geçersiz sayılır; değişmediyse 0,5 değer korunur.

3. **TMDB’ye özel editör**
   - Canlı account-state okuma; kullanıcı yanıttan önce düzenleme yaparsa uzak yanıt girdiyi ezmez.
   - Native Watchlist/favorite switch’leri, 0,5 rating stepper’ı ve film özel liste dialogu.
   - Status/progress/tarih/not gibi TMDB’de karşılığı olmayan alanların yalnız Kitsugi’de kaldığı UI’da açıklanır.
   - Yeni yüzey metinleri TR/EN string kaynaklarında.

4. **Auth ve hata yönetimi**
   - v3 request token → kullanıcı onayı → session → account akışı.
   - Token zaman damgası, expiry, callback eşleşmesi ve atomik tek kullanım.
   - `status_code` auth/session/write/validation/maintenance/rate-limit ayrımları; `KTS-TMD-7005…7008`.

5. **Senkron ve uygulama yüzeyleri**
   - İki yönlü snapshot ve mevcut `ExternalListSyncManager` fan-out’u.
   - Diğer altı sağlayıcıyla çapraz senkron; hedef bazlı kimlik doğrulama önbelleği.
   - Profil, bildirim arşivi, scheduler, ayarlar, bağlantı dialogları, TV editör callback’i ve yedekleme.

6. **Hesap değişimi ve arka-plan dayanıklılığı**
   - Snapshot/write/login/logout ortak operation mutex’ine bağlandı; eski hesap snapshot’ı import’a alınmaz.
   - Pending işler hedef TMDB account ID’si taşır; yanlış hesapta HTTP yazılmaz, yazma ortasında değişimde kalan alanlar durur.
   - Native yazmadan önce canlı account-state preflight’ı idempotent çağrıları eler; rating temizleme resmî DELETE yolunu korur.
   - Editör native alanların dirty-state’ini ayrı tutar; canlı okuma kullanıcı girdisini veya Kitsugi-only alan niyetini ezmez.
   - Bildirim baseline’ı account-scoped ve teslimden sonra kalıcıdır; teslim hatası güvenli retry üretir, arşiv yazımı await edilir.

## Üretime çıkmadan kalan doğrulamalar

- Android SDK bulunan ortamda tam Gradle build ve tüm birim testleri.
- API 29→30 Room `MigrationTestHelper` / gerçek cihaz yükseltmesi.
- Geçici/test TMDB hesabında gerçek onay ve yazma; özellikle yarım puan, Watchlist’ten bağımsız favorite ve `item_status`.
- TR/EN telefon ve TV UI, erişilebilirlik, büyük liste/çok sayfalı hesap ve rate-limit davranışı.
- v4 mixed/private list özelliği istenirse ayrı v4 kullanıcı access-token akışı; v3 session ile taklit edilmemeli.
