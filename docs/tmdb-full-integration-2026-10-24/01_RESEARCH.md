# Kitsugi × TMDB tam entegrasyon — araştırma

Tarih: 2026-10-24  
Branch: `arena/0d16774b-kitsugi-beta`  
Kapsam: yalnız katalog/Watchlist içe aktarımı değil; hesap, liste, yazma, iki yönlü ve çapraz senkron ile uygulama yüzeyleri.

## 1. Resmî TMDB kaynakları

Aşağıdaki davranışlar resmî TMDB dokümanlarına göre planlandı:

| Yetenek | Resmî kaynak | Uygulama kararı |
|---|---|---|
| v3 kullanıcı doğrulama | https://developer.themoviedb.org/docs/authentication-user | Katalog API anahtarı ile kullanıcı oturumu ayrılır. Request token → kullanıcı onayı → session → account. |
| Watchlist okuma | https://developer.themoviedb.org/reference/account-watchlist-movies ve `account-watchlist-tv` | Film ve TV sayfaları ayrı okunur; pagination zorunlu. |
| Watchlist yazma | https://developer.themoviedb.org/reference/account-add-to-watchlist | `media_type`, `media_id`, `watchlist` gövdesi; hem ekleme hem kaldırma aynı uçla. |
| Favori okuma/yazma | https://developer.themoviedb.org/reference/account-get-favorites , `account-favorite-tv`, `account-add-favorite` | Yerel `isFavorite` ile eşlenir; film/TV kimlik ad alanı korunur. |
| Puan yazma/silme | https://developer.themoviedb.org/reference/movie-add-rating , `tv-series-add-rating`, `movie-delete-rating`, `tv-series-delete-rating` | Kitsugi 0–10 puanı TMDB’nin 0.5–10 aralığına güvenli dönüştürülür; boş puan silme demektir. |
| Hesap durumu | https://developer.themoviedb.org/reference/movie-account-states , `tv-series-account-states` | Bir içerikteki favorite/watchlist/rated durumu tek yerde okunabilir. |
| Hesap özel listeleri | https://developer.themoviedb.org/reference/account-lists , `list-create`, `list-add-movie`, `list-remove-movie` | v3 özel liste uçlarının film odaklı sınırı açıkça korunur; TV için v4 kullanıcı erişim tokenı gerekir. |
| v4 kullanıcı doğrulama | https://developer.themoviedb.org/v4/docs/authentication-user | v4 request token onay adresi `/auth/access?request_token=...`; v4 listeler için ayrı kullanıcı access tokenı gerekir. v3 session v4 token gibi kullanılmaz. |
| v4 liste modeli | https://developer.themoviedb.org/docs/v3-or-v4-lists ve resmî v4 list referansı | v4 karışık film/TV listeleri ve özel liste yazma için doğru yol; uygulama v3 sessionı burada sahte biçimde kullanmayacak. |

Önemli sınır: TMDB’de Kitsugi’deki gibi tek bir “Watching / Completed / Dropped / Paused” kütüphane durumu yoktur. Watchlist “izlemek istiyorum”, favori ve puan ise ayrı hesap durumlarıdır. Bu nedenle senkron eşlemesi kullanıcıya gösterilir; `Watching` rastgele `Completed` yapılmaz.

## 2. Açık kaynak kod incelemesi

Kodlar `/tmp/tmdb-research/` altında okundu. GPL kodu kopyalanmadı; yalnız mimari ve API davranışı karşılaştırıldı.

### WirelessAlien/MovieDB — ShowCase (GPL-3.0)

- `tmdb/account/AddToWatchlist.kt`: `POST /3/account/{account_id}/watchlist`; gövdede `media_type`, `media_id`, `watchlist`. Hem ekleme hem kaldırma için boolean kullanılıyor.
- `AddToFavourites.kt`: aynı istemci modeli `/favorite` için; kullanıcı access tokenı Authorization başlığında.
- `AddRating.kt`: `POST /3/{movie|tv}/{id}/rating`, gövde `value`; TMDB’nin farklı başarı status code’ları ele alınıyor.
- `GetAccountState.kt`: `/account_states` yanıtındaki `favorite`, `watchlist`, `rated` alanlarını okuyor; `rated` hem boolean hem nesne olabiliyor.
- `GetAllListData.kt`: v4 liste sonuçlarını sayfa sayfa okuyor; `total_pages` ile ilerliyor.
- `TMDbAuthV4.kt`: v4 request token ve `/auth/access` onay akışını gösteriyor. v4 kullanıcı tokenı ile v3 hesap uçlarının Bearer kullanımı da bu projede görülüyor.

Kitsugi’ye aktarılan ders: yazma uçları ayrı kullanıcı yetkisi ister; `rated` parse edilirken tip savunması gerekir; pagination ve kullanıcıya sonuç bildirimi zorunludur. Activity/Toast’a bağlı eski yapı yerine Kitsugi’nin suspend/OkHttp/sonuç modeli kullanılacak.

### ChrisKruegerDev/tmdb-kotlin (Apache-2.0)

- `TmdbAccountApi.kt`: favori ve Watchlist için typed request body; film/TV ayrımı.
- `Tmdb4ListApi.kt`: v4 create/update/add/remove/check item uçları; film ve TV birlikte destekleniyor; özel liste için geçerli kullanıcı access tokenı şart.

Kitsugi’ye aktarılan ders: request modelleri ve endpoint ayrımı net olmalı; v3 ve v4 auth tek bir token alanına sıkıştırılmamalı.

### haroldadmin/MovieDB

- `AccountService.kt` ve `Requests.kt`: hesap uçlarının servis/request modeliyle ayrılması, favori/Watchlist gövdesinin yeniden kullanılabilir olması için referans alındı.

### thomaskioko/tv-maniac (Apache-2.0)

- Çok kaynaklı tracking’de kaynak kimliği, yerel kütüphane ve arka plan senkronunun ayrılması incelendi. Kitsugi’de mevcut `ExternalListSyncManager` korunacak; ikinci bir genel sync motoru icat edilmeyecek.

## 3. Mevcut Kitsugi’de bulunan ve düzeltilen noktalar

- Eski `source = "tmdb"` satırları gerçek TMDB hesap listesi değildi; katalog köprüsüydü. Yeni hesap satırları `source = "tmdb_account"` ile ayrılır, rozet/kimlik katmanında ikisi de TMDB olarak okunur.
- Eski Watchlist aktarımı Simkl satırı üretiyordu. Bu, ayrı TMDB sekmesi ve gerçek iki yönlü hesap senkronu için yeterli değildir; yeni tam hesap snapshotı TMDB hesap kaynağına yazılır.
- `MediaIdentity`/`CrossProviderIdentity` zorunlu kalır: TMDB ID hiçbir zaman MAL ID alanına yazılmaz; Simkl ID ile TMDB ID karıştırılmaz; belirsiz eşleşmede yazma yoktur.
- Mevcut `PendingSyncEntity`/`PendingSyncDrainer` kullanılır. Kimlik çözülemezse hata kuyruğu şişirilmez; gerçek ağ/HTTP hatası yeniden denenebilir.

## 4. Senkron ve güvenlik kararları

1. **Yerel kullanıcı alanları korunur:** uzaktan liste üyeliği/favori/puan okunurken progress, not, tarih ve kullanıcı durumu sıfırlanmaz.
2. **Tam snapshot olmadan uzaktan silme yok:** pagination veya istek başarısızsa eksik sonuç “sil” olarak yorumlanmaz.
3. **Silme çapraz yazımda kimlik ister:** TMDB ID + medya türü doğrulanmadan başka sağlayıcıda silme yapılmaz.
4. **Watchlist eşlemesi:** `Planned` → Watchlist açık; diğer durumlar Watchlist’i kapatır. Favori ve puan ayrıca yazılır. TMDB’den dönen Watchlist satırı, daha önce yerel durum yoksa `Planned` olur; puan/favori varlığı tek başına izlenmişlik kanıtı sayılmaz.
5. **Favori/puan:** uzak hesap snapshotı bu alanların TMDB tarafındaki kaynağıdır; çapraz yazım yerel kullanıcı değişikliğini bağlı diğer hesaplara taşır.
6. **Özel listeler:** v3 ile yalnız resmî olarak desteklenen film işlemleri yapılır. TV/karışık liste için v4 tokenı olmadan başarı mesajı üretilmez.
7. **Bildirim:** TMDB’nin AniList benzeri kullanıcı notification feed’i yoktur. Kitsugi kendi polling/dedupe mekanizmasıyla hesap değişikliklerini ve gerektiğinde takvim verisini işler; ilk snapshot toplu bildirim üretmez.
8. **Rapor/gizlilik:** session, request token ve API anahtarı raporlara/Drive notlarına yazılmaz; hata metinleri mevcut credential redaction katmanından geçer.

## 5. Doğrulama sınırı

Bu sandbox’ta Android SDK ve Gradle dağıtımı yok. Saf Kotlin birim/regresyon testleri çalıştırılacak; tam Compose/APK derlemesi ve gerçek TMDB hesabıyla cihaz testi ayrıca yapılmalıdır. Gerçek kullanıcı tokenı hiçbir test dosyasına veya Drive belgesine konulmayacak.

## 5. İkinci araştırma turu — native durum, hata ve login ayrıntıları

- **Account-state bağımsızlığı:** `movie-account-states` ve `tv-series-account-states` yanıtındaki `favorite`, `watchlist` ve `rated` birbirinden bağımsızdır. `rated` hem `false` hem de `{ "value": 7.5 }` biçiminde olabilir. Bu nedenle status’tan Watchlist türetmek yalnız legacy fallback olabilir; native alanlar modele ayrı eklendi.
- **Yarım puan:** TMDB rating 0,5–10 aralığındadır. Ortak Kitsugi `Int` puanı 7,5 değerini kayıpsız taşıyamaz; ayrı `Double?` alan ve 0,5 UI adımı zorunludur.
- **Özel liste üyeliği:** https://developer.themoviedb.org/reference/list-check-item-status — `movie_id` ile `item_present` okunur. Her checkbox için bütün liste içeriğini indirmek yerine resmî probe kullanılır; istekler rate-limit patlamasını azaltmak için sırayla yapılır.
- **Request-token ömrü:** v4 authentication rehberi request tokenın yaklaşık 15 dakikalık ömrünü belirtir; v3 tokenı da aynı kullanıcı-onay modeliyle ele alınır. Uygulama zaman damgası, callback token eşleşmesi ve atomik tek kullanım uygular.
- **Resmî hata gövdesi:** https://developer.themoviedb.org/docs/errors — HTTP durumuna ek olarak JSON `status_code` korunur. 33 expired/invalid request token, 41 onaylanmamış token, 36 yazma izni, 18 validation, 25 rate-limit, 9/46 bakım aileleri ayrı Kitsugi kodlarına bağlanır.
- **Kod incelemesi sonucu:** ShowCase’in account-state `rated` false/object ayrımı ve yazma gövdeleri davranış referansı olarak kullanıldı; GPL-3.0 kaynak kodu kopyalanmadı. Apache-2.0 tmdb-kotlin v4 list API’si yalnız v3/v4 yetki ve medya-türü sınırını doğrulamak için kullanıldı.
