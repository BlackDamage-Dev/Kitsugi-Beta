# Kod karşılaştırması — referanslar ve Kitsugi uygulaması

Bu belge gerçek kaynak dosyaları okunarak hazırlanmıştır. Lisans uyumu için ShowCase’in GPL-3.0 kodu kopyalanmamıştır.

| Konu | İncelenen gerçek kod | Kitsugi’de uygulanacak fark |
|---|---|---|
| Watchlist yazma | ShowCase `AddToWatchlist.kt` | Activity/Toast yerine suspend sonuç modeli; kimlik/tür gate’i; mevcut fan-out manager ve retry. |
| Favori yazma | ShowCase `AddToFavourites.kt`; tmdb-kotlin `TmdbAccountApi.kt` | Film/TV `media_type` zorunlu; uzak ve yerel favori aynı alanla eşlenir. |
| Puan | ShowCase `AddRating.kt` | `rated` false/object ayrımı; null score delete; 0.5–10 sınırı. |
| Hesap durumu | ShowCase `GetAccountState.kt` | Yanıtın üç alanı da defensive parse; tek içerik için cache’lenebilir sonuç. |
| Liste pagination | ShowCase `GetAllListData.kt` | Başarısız sayfada işlem durur; eksik snapshot asla silme kanıtı olmaz; üst sayfa güvenlik sınırı. |
| v4 auth | ShowCase `TMDbAuthV4.kt`; resmî v4 user authentication | v3 session ile v4 user token karıştırılmaz; `/auth/access` onay adresi kullanılır. |
| Typed account/list API | tmdb-kotlin `TmdbAccountApi.kt`, `Tmdb4ListApi.kt` (Apache-2.0) | Kitsugi’nin mevcut OkHttp/org.json bağımlılıkları korunur; aynı endpoint/request ayrımı. |
| Servis/request ayrımı | haroldadmin/MovieDB `AccountService.kt`, `Requests.kt` | UI’dan bağımsız client; ortak request yardımcıları; test edilebilir dönüşüm. |
| Çok kaynaklı sync | tv-maniac tracking mimarisi | Yeni paralel motor yerine `ExternalListSyncManager`, `CrossProviderIdentity`, `PendingSyncDrainer` genişletilir. |
| Yerel kaynak modeli | Önceki Kitsugi geçici importu | `tmdb_account` gerçek hesap kaynağı; eski `tmdb` katalog satırları için one-shot migration korunur. |

## Neden mevcut mimari genişletiliyor?

Kitsugi’de ekleme/düzenleme/silme çağrıları zaten merkezi fan-out, kimlik guard’ı, kaynak başına outcome ve çevrimdışı kuyruk kullanıyor. Ayrı bir “TMDBSync” düğmesi yalnız bu yolların dışında kalırsa kullanıcı değişiklikleri yine kaybolur. Bu nedenle TMDB client ayrı, fakat senkron orkestrasyonu mevcut merkezde olacak.

## Test edilmesi gereken referans davranışlar

1. Aynı TMDB ID farklı medya türünde birleşmez.
2. Watchlist/favorite/rating union snapshotında tek TMDB hesap satırı oluşur.
3. Rated `false` ile `{value: 8}` aynı parserdan güvenle geçer.
4. 2. sayfa hatası tüm snapshotı başarısız yapar; 1. sayfadaki eksikler silinmez.
5. Legacy `tmdb` migrasyonu `tmdb_account` satırını Simkl’ye dönüştürmez.
6. `Planned` dışındaki durum rating/favorite alanlarını silmez.
7. Manga ve doğrulanmamış TMDB kimliği uzak yazıma gönderilmez.
8. İlk bildirim snapshotı mevcut bütün hesabı “yeni” diye bildirmez.

## Uygulama sonrası karşılaştırma

| Referans davranış | Kitsugi’deki son karşılık |
|---|---|
| ShowCase account-state `rated=false/object` | `fetchAccountState` iki biçimi de okur; editör canlı değeri gösterir. |
| ShowCase favorite/watchlist/rating yazma gövdeleri | Ayrı resmî çağrılar; tek bir sahte “status” modeline birleştirilmez. |
| tmdb-kotlin v4 mixed list mutation | v3 session’a ait film-only UI açıkça sınırlanır; v4 yetkisi taklit edilmez. |
| Resmî `item_status` | Liste metadata’sı + sıralı üyelik probe’u; add/remove sonrası yerel checkbox yalnız başarıda değişir. |
| TMDB errors `status_code` | IOException metninde `TMDB-<code>` marker’ı; `PlatformErrorCatalog` sabit KTS koduna çevirir. |
| Request-token onay akışı | 15 dakikalık TTL, callback eşleşmesi, atomic consume; WebView ve harici tarayıcı aynı URL/tokenı paylaşır. |
| Açık kaynak uygulamaların tek native puanı | Kitsugi’nin ortak Int puanı korunurken ayrı `Double?` native alan eklenir; sibling mirror yazma sırası da buna göre düzeltilir. |
