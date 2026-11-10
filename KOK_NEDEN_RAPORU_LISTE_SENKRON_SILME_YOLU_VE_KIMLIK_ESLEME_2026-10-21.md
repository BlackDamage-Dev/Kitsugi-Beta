# Kök Neden Raporu — Silme ve Eşitlemede Kimlik Çözümü, Kimlik Eşlemesi ve Sonuç Sınıflandırması (2026-10-21)

## Şikâyet (kullanıcının tarifi, ekran görüntüsünden)

"Calling You" (2016 TV, AniList satırı) ayrıntı sayfasından silme:

> **AniList kütüphanesinden silindi • Atlanan: MAL: doğrulanmış medya kimliği yok • Shikimori
> medya kimliği bulunamadı • Bangumi eşleşmesi bulunamadı: Omokage Tayuta • Çevrimdışı kaydedildi,
> bağlantı gelince gönderilecek**

Sonuç özeti:

> **AniList: eşitlendi, MyAnimeList: kimlik çözülemedi, Simkl: hata, Kitsu: hata, Shikimori:
> kimlik çözülemedi, Bangumi: kimlik çözülemedi**

Kullanıcının isteği (özetle): ekleme/güncelleme/silme işlemlerinde **tarihler, puanlar, bölüm
sayısı vb. değerlerin diğer beş kaynaklara düzgün yayılması** — yamayla değil kökünden.

İlgili önceki rapor: `KOK_NEDEN_RAPORU_LISTE_SENKRON_SECILEN_DEGERLER_2026-10-20.md` (v2.4.312,
9 kök neden). Bu rapor, onun kalan **silme yolu / kimlik eşlemesi / çıktı sınıflandırma**
katmanını kapatır.

---

## TL;DR — 8 kök neden

Ortak tema: **eşitleme yolu çapraz kimlik katmanıyla (`CrossProviderIdentityResolver`) çalışırken
silme yolu hâlâ "kendi satırına bak, ağa çıkma" kuralındaydı; ayrıca yöneticilerin "kimlik bulunamadı"
sonuçları hata ile atlama arasında gidip geliyordu** — hata sayılan her şey bekleyen-senkron
kuyruğuna ve "Çevrimdışı kaydedildi" bildirimine düşüyor, kullanıcı hatayı sonsuza kadar
görüyordu. Üstelik iki yöneticide kimlik yorumlama kuralı (`<100M` aralığı, `>=300M` eşiği)
**kaynak uzayını** (AniList 100M, Simkl 150M, Kitsu 300M, Shikimori 400M, Bangumi 500M
offset'leri) dikkate almıyordu.

| # | Kök neden | Belirti (ekran görüntüsünde) | Düzeltme |
|---|-----------|------------------------------|----------|
| 1 | Silme kimlik çözümünde **ağa çıkılmıyordu** (`allowNetwork = false`) | Daha önce hiç yayılmamış satır yalnızca kendi kaynağından silinir; diğerlerinde hayalet kayıt + "kimlik çözülemedi" | `allowNetwork = true` + `connected` kaynaklar (güncellemeyle aynı doğrulanmış çözüm zinciri) |
| 2 | "Kimlik bulunamadı" sonuçları **`errors`** listesine yazılıyordu | `needsRetry` = `errors` dolu → kuyruk → her açılışta "Çevrimdışı kaydedildi" + "Simkl: hata, Kitsu: hata" | `unresolvedMedia`/`unresolvedSubject` sözleşmesi: gerekçe `skipped`'e, `errors` yalnızca taşıma/HTTP hatası |
| 3 | Silme yolu yöneticileri kimlik taramasını **kısıtlı** yapıyordu (Kitsu: namespace+MAL önbelleği; Shikimori: yalnız rate önbelleği; Simkl: arama yok) | "Shikimori medya kimliği bulunamadı", "Kitsu: hata", içe aktarılmış kayıtların silinememesi | `knownKitsuMediaId`/`knownMalId` geçişi + yazma yoluyla **aynı** çözüm zinciri; Shikimori'de `fetchAllUserRates` geri araması; bulunamayan kütüphane kaydı = idempotent başarı |
| 4 | Canlı hat `rawKitsuId` için **geniş `>= 300M`** kuralı kullanıyordu (toplu hatta 2026-10-07'de düzeltilmişti) | Shikimori (400M) stableId'leri `id - 300M` ile **sahte Kitsu kimliğine** dönüyor → Simkl her yazma/silmde `not_found` → "Simkl: hata" | `rawKitsuIdOf()`: yalnız `300_000_001..399_999_999` → `-300M`, ya da `source == "kitsu"` ham (<100M) kimlik |
| 5 | `realMalId` = saf `0 < id < 100M` aralığı (canlı Simkl/Shikimori) | İki yönlü: (a) eski Kitsu/Bangumi ham kimlikleri ve TMDB uzay değerleri **MAL sanılıp yanlış yapıma yazabiliyordu**; (b) çapraz eşitlemenin çözdüğü gerçek MAL, `source = "shikimori"` vb. satırın geçici kopyasına yazılıp decode tarafından **ham kimlik sanılıyor ve düşürülüyordu** | `knownMalId` parametresi (kimlik katmanının doğrulanmış sonucu önce gelir) + decode fallback (Simkl sync/sil, Shikimori sync/sil, AuthViewModel toplu Shikimori). Toplu hatta ham satır kuralı **bilinçli** korundu (Shikimori importu MAL uzayındaki `target_id`'yi ham yazar) |
| 6 | AniList silmede `account_sync_anilist_media_id_unresolved` her zaman **FAILED** | Kimlik eksiği bile "hata" → kuyruk | Atlama (skip); `identity_mismatch` yeniden denenebilir kalır |
| 7 | MAL PATCH parsriyel tarih (`"2016"`, `"2016-05"`) ile 400 dönüyordu | Tüm güncelleme tek eksik tarihte "hata" sayılıp kuyruğa düşüyordu | `sendableDate()`: yalnız `yyyy-MM-dd` gönderilir; uymayan alan göndermez (uzak değer korunur) |
| 8 | Bangumi silmede **tüm** hatalar "atlandı" sayılıyordu | Gerçek yazım hataları (ağ/HTTP) kuyruğa hiç girmiyordu | `unresolvedSubject` ayrımı: kimlik eksiği = atlandı, yazım hatası = hata |
| 9 | Çevrimdışı kimlik denemesi **olumsuz önbellek** yazıyordu | Ağ adımı düşse bile `resolveUncached` normal dönüyor, eksik kayıt yazılıp **6 saatlik soğuma** başlıyordu → bağlantı gelince kuyruktaki yeniden deneme bile kimliksiz kalıyor, yayılım kalıcı kayboluyordu | `resolve()`: çevrimdışıysa ağ denemesi yok, önbellek yazımı yok (bağlantıda ilk denemede çözülür) |
| 10 | Çevrimdışı eklenen **Bangumi satırında** hiçbir yazar çalışmıyor ama hata da üretilmiyor | `allowFanOut` kapalıyken altı kaynak da "atlanır" → `errors` boş → kuyruk boş → Bangumi satırı bir daha **hiç** yayılmaz | Çevrimdışı + fanout kapalıyken `errors`'a `account_sync_offline_identity_pending` → "Çevrimdışı kaydedildi" + kuyruk → `isOnlineFlow` bağlantıyla otomatik drain |
| 11 | Kuyrukta **5 denemeyi aşan işler bir daha hiç denenmiyordu** | Uzun çevrimdışı kalmış/5 kez başarısız olmuş ekleme-güncelleme-silme işi DB'de sonsuza kadar donuyor, işlem **kalıcı kayboluyordu** (entity sözü "bağlantı gelince yeniden denenir" idi ama kod öyle yapmıyordu) | `resetRetries()` + bağlantı geldiğinde `drainPendingQueue(freshBudget = true)`: oturum başına taze 5 deneme — iş asla donmaz, silinmez |
| 12 | `deleteAll`/`replaceAll` (liste temizleme / yedek geri yükleme) **kuyruğu temizlemiyordu** | Bayat UPDATE işi, silinen/üzerine yazılan kayıtları uzaklara **geri diriltir** ya da taze yedeği eski kopyayla ezerek geri sürerdi | `clearAll()`: iki akışta da kuyruk temizlenir |
| 13 | Simkl `removeRating` **taşıma/5xx hatalarını `false`'a gizliyordu** ve çağrıda yalnız log'lanıyordu | Çevrimdışıyken "puanı sil" işlemi sessizce kayboluyordu (kuyruğa hiç girmiyordu) | `removeRating`: taşıma/5xx → fırlat → `errors` → kuyruk; yalnız 4xx (zaten puan yok) sessiz |
| 14 | Yerelde **sil/yeniden-yaz** yapan kalıplar bekleyen kuyruk işlerini temizlemiyordu | Silinen kardeş satırın/inline'dan düşen satırın bayat UPDATE'i drain edilince **uzak kayıt dirilir** ya da taze içe aktarım eski kopyayla geri sürülürdü | `clearPendingJobsFor(entryIds, source)`: `deleteById`(+kardeşler), `deleteBySource`, `smartImport` (düşenler + kaynak boşaltma), `replaceSourcePreservingAdultFlags` hepsinde temizlik |

---

## Ayrıntılar

### 1) Silme yolu kimlik çözümünde ağa çıkmıyordu
`ExternalListSyncManager.deleteEntry`, `CrossProviderIdentityResolver.resolve(allowNetwork = false)`
ile çağrılıyordu (yorum: "silmede yanlış yapıma dokunmamak için"). Eşitleme yolu ise
`allowNetwork = true` + doğrulanmış eşleşmelerle çalışıyordu. Sonuç: kullanıcı bir kaydı ilk kez
sildiğinde (daha önce hiç yayılmamış içe aktarım satırı gibi) çapraz kimlik hiç çözülemiyor,
**yalnızca satırın kendi kaynağı** silinebiliyor, diğer kaynaklarda hayalet kayıt kalıyordu.
Güvenlik eşitlemeyle aynı: çözümleyici yalnızca doğrulanmış eşleşmeler döndürür; çözülemeyen
alan `null` kalır ve o kaynak atlanır. Artık token/connectedSource hazırlığı kimlik aramasından
**önce** yapılıyor ve `connected` bilgisi aramaya veriliyor (bağlı olmayan kaynak için ağ araması
yapılmaz).

### 2) Kimlik eksiği "hata" sayılıyordu → sonsuz "Çevrimdışı kaydedildi"
`MediaEntryRepository` başarıyı `errors.isEmpty()` ile ölçer, hata görürse kaydı bekleyen-senkron
kuyruğuna yazar ve çevrimdışı bildirimi gösterir. Kuyrukta yeniden denemenin faydası olmayan kimlik
eksiği, eski kodda `errors`a girdiği için **her açılışta aynı hata yeniden üretiliyordu** (şikâyetin
tam kalbi). Artık:

- Yönetici düzeyi: `SyncResult.unresolvedMedia` (Simkl/Kitsu), `unresolvedSubject` (Bangumi) —
  `errors` bu durumda gerekçe metinlerini taşır ama sınıf **atlandı**'dır.
- Orchestrator düzeyi: gerekçe `skipped` listesine, `errors` yalnızca taşıma/HTTP'ye; özet mesajda
  "Atlanan: …" satırı, sonuç haritasında `OUTCOME_SKIPPED_IDENTITY` ("kimlik çözülemedi").
- `skipped asla kuyruğa girmez`; `errors` kuyruğa girer (gerçek hatalar yeniden denensin diye).

Simkl ekleme makbuz ayrıştırımı: `addToListBatchDetailed` yanıtında `transportFailed` (HTTP/ağ) =
hata, `not_found` (katalogda yok) = atlandı. Simkl silme: id arama dönüşü `false` (bulunamadı) =
atlandı; `removeIntoList` Boolean'i bu ayrımı yapamadığı için kimlik arama sonucu ölçüt alındı.

**Ekrandaki sonuç artık:** `Simkl: hata → kimlik çözülemedi/atlandı` (veya kimlik varsa
`eşitlendi`), `Kitsu: hata → kimlik çözülemedi` (veya `eşitlendi`), kuyruk ve "Çevrimdışı
kaydedildi" yalnız gerçek ağ/HTTP hatalarında.

### 3) Silme yolu kimlik taramasını sınırlı yapıyordu
- **Kitsu silme:** yalnız satırın namespace'i + MAL→Kitsu önbelleği. Çapraz kimlik katmanının
  çözdüğü Kitsu medya kimliği yok sayılıyordu. Artık `knownKitsuMediaId = identity.kitsuId` ile
  eklemeyle **aynı** zincir: bilinen kimlik → MAL önbelleği → Kitsu stableId → Kitsu mappings →
  ARM → sıkı başlık araması. Kimlik var ama kütüphane kaydı yoksa = zaten silinmiş (başarı).
- **Shikimori silme:** yalnız rate önbelleği (yalnızca bu uygulama üzerinden oluşturulan kayıtlar).
  Artık: önbellekte yoksa `fetchAllUserRates` → `targetId + targetType` ile eşleşen rate → sil.
  Uzak liste **okunamazsa** bu "yok" değildir → gerçek hata (yeniden dener). Hiç kayıt yoksa =
  idempotent başarı. Hedef hiç çözülemezse atlandı.
- **Simkl silme:** `simklId` yoksa eskiden `account_sync_media_id_not_found` bir **hataydı**;
  artık `unresolvedMedia` (atlandı) ve öncesinde `knownMalId` + kimlik arama üzerinden eşleşme
  denenir.

### 4) Canlı hat Shikimori 400M'yi Kitsu 300M sanıyordu
Eski kural: `entry.source == "kitsu" || malId >= 300_000_000` → `id - 300_000_000`. Shikimori
stableId aralığı (400_000_001..400_099_999) bu eşiğin üstünde olduğu için `id - 300M` ile
**300M+ bir sayı** (gerçek Kitsu id'leri <100K) Simkl'e `ids.kitsu` olarak gidiyor → her yazma ve
silmede `not_found` → "Simkl: hata". Toplu hatta bu hata 2026-10-07'de düzeltilmiş, canlı hatta
kalmıştı. `rawKitsuIdOf()` artık tek sözleşmedir: 300M penceresi → `-300M`; `source == "kitsu"` ve
ham (<100M) → olduğu gibi; aksi halde `null`.

### 5) `realMalId` yorumlaması — iki yönlü hata
Eski kural `0 < id < 100M` idi ve **kaynağa** bakmıyordu:

- (a) Eski Kitsu/Bangumi ham kimlikleri ve film/dizi satırlarındaki TMDB uzay değerleri aynı
  aralığa düşüp MAL sayılıyordu → `ids.mal` olarak yanlış yapıma yazma riski ve `not_found`.
- (b) Tersi: `withResolvedIds` ile çapraz eşitlemenin çözdüğü **gerçek MAL** geçici kopyaya
  (`source = "shikimori"` vb.) yazılıyordu; saf `CrossProviderIds.decode` bunu kaynağın kendi ham
  kimliği sanıp `malId`'den **düşürüyordu** → doğru yazım atlanıyordu.

Çözüm, codebase'in `knownXxxId` desenine uydu: Simkl sync/sil ve Shikimori sync/sil fonksiyonlarına
`knownMalId: Int? = null` parametresi eklendi, çağrılar `identity.malId` geçiriyor (AuthViewModel
toplu Shikimori akışı da `realMalId`'yi geçiriyor). Öncelik: `realMalIdOf(knownMalId)` → decode
fallback. **Toplu Simkl hattı** bilinçli olarak eski aralık kuralında bırakıldı: o hat ham kütüphane
satırları üzerinde çalışır (kopya değil) ve Shikimori importu MAL uzayındaki `target_id`'yi ham
yazar (`<100M`) — decode bunu shikimori saydığı için aralık kuralı orada **doğru** davranır;
dosya içi yorumla belgelendi.

### 6)–(8) Sınıflandırma/alan yamaları
- AniList silmede medya kimliği çözülemedi → atlandı; `identity_mismatch` (olabilir yeniden deneme)
  FAILED kalır.
- MAL tarihleri: `fullIsoDate` (`yyyy-MM-dd`) ile `sendableDate()`; parsriyel tarih gönderilmez,
  PATCH 400'e düşmez. (AniList `scoreRaw` ondalık×10 matematiği, `SyncScores` ve içe aktarma
  ölçekleri denetlendi — **doğru**, "düzeltilmedi".)
- Bangumi silmede `unresolvedSubject` = atlandı, yazım hatası = FAILED (eşitleme yoluyla aynı).

---

## Uçtan uca doğrulama — "Bangumi'den Mushoku Tensei ekledim/güncelledim" senaryosu

Kullanıcı sorusu: bu değişikliklerle Bangumi'den eklenen/güncellenen bir kayıt **tüm** sağlayıcılarla
eksiksiz ve hatasız eşitlenecek mi? Kod üzerinden uçtan uca izlenmiş akış (çevrimiçi, altı kaynak
bağlı ve açık):

1. **Kapı:** `MediaEntryRepository.insert/update(syncExternal=true)` → `syncEntryIfPossible`
   (yalnızca hiçbir kimliği/başlığı olmayan satırları atlar; Bangumi satırı geçer) →
   `ExternalListSyncManager.syncEntry`. Ekle/güncelle/sil üç çağrı noktası da bu hatta
   (AppRoot, AppViewModel, MyListViewModel).
2. **Kimlik (Bangumi satırı):** `decode` → `bangumiSubjectId` (500M stableId) →
   **ARM kavşağı** (`bangumiId` girdisi → MAL+AniList+Kitsu+TMDB tek adımda) → olmazsa
   **Jikan sıkı başlık** (İngilizce → Japonca → görünen ad + yıl eşleşmesi, tek net sonuç) →
   doğrulanınca `allowFanOut` açılır. Ardından Kitsu resmi `mappings` (MAL→Kitsu) ve Simkl
   canlı arama (MAL kimliğiyle). Mushoku Tensei bu zincirin her adımında mevcut bir yapım.
3. **Alan yayılımı (kanıt: manager fonksiyonlarının gönderdiği parametreler):**

| Alan | Bangumi (kaynak) | AniList | MAL | Kitsu | Shikimori | Simkl |
|------|:---:|:---:|:---:|:---:|:---:|:---:|
| Durum | ✓ `upsertCollection` | ✓ | ✓ PATCH | ✓ | ✓ | ✓ `to` |
| Bölüm ilerleme | ✓ `setProgress` | ✓ `progress` | ✓ `progress` | ✓ + uzunluk koruması | ✓ `episodes` | ✓ 1..N geçmiş |
| Puan | ✓ `rate` | ✓ `scoreRaw` (×10) | ✓ `score` | ✓ /20 | ✓ /10 | ✓ `ratings` |
| Tarih (başlangıç/bitiş) | — (Bangumi'de alan yok) | ✓ `FuzzyDates` | ✓ `yyyy-MM-dd` | ✓ `started/finished` | — (API'de alan yok¹) | — (alan yok) |
| Not | ✓ `comment` | ✓ | ✓ `comments` | ✓ `notes` | ✓ `text` | — (alan yok) |
| Etiket | ✓ `tags` | — | ✓ | — | — | — |

¹ Shikimori resmi dokümanı `POST /api/v2/user_rates` parametreleri: `user_id, target_id,
target_type, status, score, chapters, episodes, volumes, rewatches, text` — tarih alanı
**yok** (platformun liste kaydında start/finish tarihi desteklenmiyor). Simkl ve Bangumi de
kullanıcı ayarlı tarih tutmaz. Yani "tarihler" destekleyen üç platforma (AniList/MAL/Kitsu)
eksiksiz gider; diğerleri teknik olarak alamaz.

4. **Sonuç sınıfı:** kimlik eksiği → "kimlik çözülemedi/atlandı" (kuyruk yok, çevrimdışı
   bildirimi yok); ağ/HTTP/429 hatası → "hata" → kuyruk → bağlantı gelince `isOnlineFlow`
   ile otomatik gönderim. Altı kaynak bağlıysa ve kimlik çözüldüyse hata HATA üretilmez,
   özet yalnız "eşitlendi" + gerekirse "atlanan" satırı taşır.

Kalan dürüst istisnalar: (a) kaynak bağlı değil/kapalı → "hesaba bağlı değil/kapalı" (hata
değil); (b) başlık/yıl eşleşmesi gerçekten bulunamayan nadir yapım → atlandı (yanlış yapıma
yazmamak için); (c) ağ/429/5xx → geçici hata + otomatik yeniden deneme (kuyruk düzgün çalışır).

---

## Koruma mekanizması envanteri (işlem × kaynak)

Kullanıcı isteği: ekleme/güncelleme/silme + tüm alanlar (durum, bölüm, puan, tarih, not,
etiket) **her kaynakta** korumalı olsun, hiçbir işlem yok olmasın. Mevcut + bu sürümde eklenen:

| Koruma | Ne korur | Kapsam |
|---|---|---|
| **Bekleyen-senkron kuyruğu**: hata → kayıt → açılış/bağlantıda otomatik drain; **yeni: taze bütçe** (k11) | Ağ/5xx/429/401 kaynaklı her ekle/güncelle/sil | 6 kaynak, tüm alanlar |
| **Yeniden deneme asla donmaz** (k11) + **bayat iş üretmez** (k12) | İşin kalıcı kaybı / silinenin dirilmesi / yedeğin ezilmesi | kuyruk altyapısı |
| **Alan süzgeçleri**: Kitsu 422→uzunluk koruması+tekrar yazım; MAL parsriyel tarih (`sendableDate`); AniList `{0,0,0}` boş tarih gönderilmez; Bangumi not≤380/etiket kuralı süzgeci; Shikimori yalnız dolu alanlar (boş uzak değeri ezmek); puan ölçek dönüşümleri (10/20/100) | Alan bazlı 400/422 → tüm PATCH'in düşmesi; yanlış değer yazımı | ilgili her kaynak |
| **Kimlik yok = atlandı, ağ hatası = hata** (k2) | Kuyruk şişmesi ve tersi: işlemin sessiz kaybı | 6 kaynak |
| **Taşıma hatası asla `false`'a çevrilmez** (k13: Simkl puan silme) | "Puanı sil" işleminin kaybolması | Simkl |
| **Idempotent silme** ("zaten silinmiş" = başarı) + silmede ağla kimlik çözümü | Tekrarlı silmede hata; hayalet kayıt | Kitsu/Shikimori/Simkl/Bangumi/AniList |
| **Sıralı Simkl yazımı**: geçmiş → puan → **durum en son** | Durumun geri ezilmesi | Simkl |
| **429/kimlik yenileme**: `PlatformRateLimiter`, Bangumi 401→token yenileme, Simkl outbox backoff, MAL/Shikimori 401→yeniden bağlanma sinyali | Anlık API aşırı yükü/kimlik düşüşü | ilgili kaynaklar |
| **Kardeş satır aynası** (yerel) + **çapraz kimlik kalıcılığı** (bir kez çözülen kalır) | Yerel tutarsızlık + tekrar tekrar ağ taraması | yerel/6 kaynak |
| **Çevrimdışı**: kimlik önbelleği zehirlenmez (k9), Bangumi satırı kuyruğa girer (k10) | Çevrimdışı eklenenin kaybı | 6 kaynak |

**Araştırılıp yapılmayanlar:** MAL'nin `num_watched_episodes` için toplam bölüm doğrulaması
yaptığına dair kayıt bulunamadı (kaynak: resmi olmayan API spesifikasyonları — tam sayı serbest);
Kitsu'daki gibi kanıtlanmış tek platform sınırı (422 `cannot exceed length`) zaten korumalı.
Kanıtlanmamış sınır için spekülatif clamp eklenmedi.

---

## Değişen dosyalar

| Dosya | Değişiklik |
|-------|------------|
| `data/auth/ExternalListSyncManager.kt` | Silme kimliği `allowNetwork=true` + `connected`; Simkl/Kitsu/Shikimori/AniList silme 3'lü sınıflandırma (sync/del Simkl, sync Kitsu/Bangumi zaten öyleydi); `knownKitsuMediaId`/`knownMalId` geçişi; Shikimori hedef kapısı + `account_sync_shikimori_id_not_found` gerekçesi; Bangumi silme `unresolvedSubject` ayrımı; sınıf KDoc'u; **çözülen `simklId`/`tmdbId` tutamaçları yerel satıra geri yazılır (yalnız null ise; video satırları early-return'ten kurtarıldı)** |
| `data/auth/SimklSyncManager.kt` | `realMalIdOf(entry, knownMalId)`, `rawKitsuIdOf()`; sync adım 3 → `addToListBatchDetailed` makbuz ayrıştırımı; kimlik yoksa/bulunamadıysa `unresolvedMedia`; silme id-miss → `unresolvedMedia`; batch kuralı belge yorumu |
| `data/auth/KitsuSyncManager.kt` | `deleteEntryFromKitsu(…, knownKitsuMediaId)` — eklemeyle aynı çözüm zinciri; null → `unresolvedMedia`; kütüphanede olmayan → idempotent başarı |
| `data/auth/ShikimoriSyncManager.kt` | `shikimoriTargetIdOf(entry, knownMalId)`; sync/sil `knownMalId`; silme: rate önbelleği yoksa `fetchAllUserRates` ile hedefe göre bulma; okuma hatası ≠ bulunamadı; bulunamayan kayıt = idempotent başarı; hedefsiz → `unresolvedMedia` |
| `data/auth/MalSyncManager.kt` | `sendableDate()` — yalnız tam `yyyy-MM-dd` tarih PATCH'e girer |
| `data/auth/CrossProviderIdentity.kt` | `resolve()`: çevrimdışı ağ denemesi/olumsuz önbellek yazımı yok (6 saatlik soğuma zehirlenmesi kalktı) |
| `ui/app/AuthViewModel.kt` | Toplu Shikimori çağrılarına `knownMalId = realMalId` |
| `res/values/strings.xml` | `account_sync_offline_identity_pending` |
| `data/local/PendingSyncDao.kt` | `resetRetries()` (taze yeniden deneme bütçesi), `clearAll()` |
| `data/local/PendingSyncDrainer.kt` | `drain(freshBudget)` — bağlantı dönüşünde sayaç sıfırla |
| `data/local/MediaEntryRepository.kt` | `drainPendingQueue(freshBudget)`; `deleteAll`/`replaceAll` kuyruğu temizler |
| `AppRoot.kt` | `isOnlineFlow` → `drainPendingQueue(freshBudget = true)` |
| `data/remote/SimklApiClient.kt` | `removeRating`: taşıma/5xx fırlatır (kuyruk), 4xx sessiz |
| `data/auth/SimklSyncManager.kt` | `removeRating` başarısızlığı → `errors` (kuyruğa girer) |
| `data/local/MediaEntryRepository.kt` | `clearPendingJobsFor`: deleteById(+kardeş), deleteBySource, smartImport düşenleri, replaceSource — bayat iş bırakmaz |

Dokunulmayan (bilerek): `CrossSyncIdentityGuard` (decode/cacheKey sözleşmesi v2.4.312'de oturdu),
`CrossSyncReportFormatter`, `MediaEntryRepository`'nin başarı/hata sözleşmesi (k11–k12, k14 hariç).

---

## Ekosistem araştırması — altı kaynağın uygulama-geneli neden kırılgan (2026-10-21)

`ARASTIRMA_KAYNAK_SENKRON/` klasöründe8 dosyalık, forum/Reddit/GitHub-issue/resmi-doküman
kaynaklı kapsamlı araştırma. Ana bulgular: AniList'in **fiili limiti 30/dk** (doküman 90) +
2026-09-10 ve 2026-10-21 403-disable olayları; MAL 2018 süresiz-kapatma ve 2024 `/v2`
bloklaması; `kitsu.app`-`kitsu.io` domain karmaşası; Shikimori domain rotasyonu (Rusya
Ocak-2026 engelleri); Simkl **20 sn yazma kilidi** + **watchlist-silme endpoint'inin yokluğu**
(var-olmayan yola 200-null); Bangumi Cloudflare-503 (bangumi/api#50); MALSync wiki itirafı
("başka sync aracı veriyi bozdu → sil yeniden ekle"). Sentez ve 14 nedenle eşleme:
`08_SISTEMIK_NEDENLER_VE_KITSUGI.md`.

Bu araştırmadan çıkan **kod eylemi**: `BangumiApiClient` içinde `api.bgm.tv` için 450 ms
minimum istek aralığı (5 çağrı noktasına `awaitBangumiThrottle()`) — Cloudflare-503 sınıfına
karşı. tree-sitter: değişiklik öncesi/sonrası hata kümaları birebir aynı (2 önceden-var
string-template hatası, yeni hata yok).

## Açık liste — bilinçli olarak kalanlar (gizlenmeyen sınırlar)

1. **Derleme/CI testi bu ortamda koşulamadı** (JVM/Gradle yok): tree-sitter sözdizimi +
   satır-satır diff denetimi + API sözleşmeleri kaynak üzerinden doğrulandı; sürümü açmadan
   önce `assembleDebug` + `testDebugUnitTest` CI'dan geçmeli. **Tek açık halka budur.**
2. **Toplu "Tümünü eşitle" hattı kuyruğa YAZMAZ** — kendi rapor/hata sayacıyla çalışır
   (tasarım: kullanıcı yeniden çalıştırır); tekil ekle/düzenle/sil akışları kuyruğa yazar.
   Sınıflandırma (atlandı/hata) her iki hatta da aynı sözleşmeyi kullanır.
3. **"Listeyi temizle" (deleteAll) yalnız yereli siler** — uzaklara toplu silme göndermez
   (ürün kararı); kuyruk da temizlenir (k12), bayat iş geri yazamaz.
4. **İçe aktarım ile bekleyen kullanıcı düzenlemesinin çakışması**: kuyruk işi korunur (kullanıcı
   niyeti kazanır); drain'de uzak değer kullanıcınınkine döner, sonraki içe aktarım yereli de
   ona eşitle — yakınsama kullanıcı niyetine doğrudur. Çapraz çözüm mekanizması eklenmedi.
5. **MAL'de toplam-bölüm doğrulaması kanıtı yok** (araştırıldı): spekülatif clamp eklenmedi;
   kanıtlanmış tek sınır Kitsu 422'si korumalı.
6. `CrossSyncEntryFactory`'nin üretim çağıranı yok (yalnız testleri var) — silinmedi.
7. ~~Simkl: watchlist'ten tam API-silme yok~~ **→ GİDERİLDİ (bu tur, en-iyi-mevcut)**:
   silme yoluna `ratings/remove` **+ korumalı `dropped` taşıması** eklendi; hayalet-koruma:
   id bu silmede yeni çözüldüyse taşıma yok. Tam-silme ucu API'de hâlâ yok (imkânsız),
   kalan satır-durumu kapatılır → "sildim geri geldi" en-son-çare kapatıldı.
   (`ARASTIRMA_KAYNAK_SENKRON/05_SIMKL.md` §3 + bu tur kodu)
8. ~~Shikimori domain-fallback uygulanmadı~~ **→ GİDERİLDİ (bu tur)**: `executeShikimori` erişim-hatasında .io→.one→.tv aday geçişi + çalışan-domain önbelleği; `KitsugiIdResolver` aday döngüsü; `.tv` bütçesi. Eski madde:: `.io` bugünkü kullanım; `.tv` adayı yalnız
   zayıf kaynaklardan (mirror sitesi) doğrulandı — tek-gerçek ilan edilmeden aday-listesi
   eklenmeyecek. `KitsugiIdResolver` `.io`'ya sabit. TR'de `.io` çalışıyor; risk
   ağırlıklı Rusya/engelli-ülkeler. (`04_SHIKIMORI.md`)
9. ~~Kitsu host doğrulaması~~ **→ GİTERİLDİ (bu tur)**: `kitsu.app↔kitsu.io` erişim-dönüşümlü fallback (yalnız erişim-hatalarında; kanonik-host tercihi belirsiz → ikisi de denenir). Eski madde:: uygulama `kitsu.app`, resmi dokümanlar `kitsu.io`
   (EverythingBox #330); ikisi de erişilebilirken fallback eklenmedi → kanonik-host
   teyidi bekliyor. (`03_KITSU.md` §1)
10. **Bangumi "silme" semantiği belirsiz**: v0 DELETE koleksiyonu kaydı DROPPED'a düşürüyor
    olabilir (tam silme web-özel) — "silindi" mesajı bu gerçeği gizliyor olabilir.
    (`06_BANGUMI.md` §4)
11. **Bangumi write-bütçesi — KARAR: kod eklenmedi (gerekçeli)**: topluluk ölçümü ~3/dk
    resmi değil; temkinli sayı bile iddiayı karşılamazken (3/dk ≈ 20sn aralık) yavaşlatma
    kullanıcıyı üzen şey olurdu. Mevcut 450ms + 429/503→kuyruk düzeni korunuyor; doğru sayı
    Bangumi'den doğrulanınca A11 kapanır. (09.md §F1)
12. **Shikimori 403-token varyantı** (Oca-2026 kulüp raporu): cookie'li istemcilerde süresi-dolmuş
    token 401 yerine 403 — Kitsugi Bearer-only (düşük risk), üretimde izlenecek.
13. **AniList finale-otomatik-tamamlama** kaynak-çelişkisi (2018 changelog vs 2026 geliştiriciler):
    otomatik-tamamlama eklenmedi, yerel-otorite korundu.
    Ayrıntı: `ARASTIRMA_KAYNAK_SENKRON/09_KENDI_VERITABANINA_YAZMA_HATALARI.md` + `10_YAZMA_SEMANTIGI_MATRISI.md` (tur-2).
14. ~~Simkl ortak hız sınırlayıcısı yoktu~~ **→ GİDERİLDİ (2026-10-25):** Eski `awaitSimklRateLimit` her `SimklApiClient` nesnesine özeldi (GET 120 ms); paralel ekranlar ve doğrudan import/profil çağrıları ayrı sayaçlarla aynı Simkl kotasını aşabiliyordu. Ortak `SimklRateLimitInterceptor` artık tüm `api.simkl.com` çağrılarına 350 ms GET aralığı, 1.25 sn seri yazım aralığı ve Retry-After tabanlı paylaşılan cooldown uygular; Simkl HTTP 400 `rate_limit` için bir kontrollü yeniden deneme yapar. Geçici 429 retry ile düzelen çağrı, sonuçta kaynak hatası olarak gösterilmez. Simkl hesap kotası/banı canlı hesapla doğrulanmadı.
15. **Simkl V1→V2 — HAZIRLIK TAMAM**: 8 adımlı göç planı `13_SIMKL_V2_GOC_PLANI.md`
    (kayıt=KULLANICI hesabıyla, PKCE şablonu=MAL, premium_only 200+error ayrıştırıcı,
    test listesi). Kod = client_id kaydı sonrası; hedef 2026 sonu (V1 ölümü ~Nisan 2027).
    V1 günlük kota ortak havuzu → V2'de kullanıcı-başına iner.
16. ~~Bölüm-clamp eklenmedi~~ **→ GİDERİLDİ (bu tur, kısmi-kasıtlı)**: `total` **kesin
    biliniyorsa** `min(progress,total)` — Simkl `historyBatchReceipt` (tekil+toplu hattı
    kapsar), Shikimori, Bangumi. Bilinmeyorsa dokunulmaz (veri kaybı yok); asla-geri-alma
    round-3'tten var; Kitsu 422-on-squeeze + MAL pre-clamp zaten korumalı; AniList
    toleranslı → dokunulmadı. Ayrıntı: `11_…` + `12_…` (tur-3).

17. **Çapraz eşitlemekte yanlış eşleşme koruması 5/6 hedefte YOKTU** → **GİTERİLDİ (bu
    tur)**: `CrossSyncIdentityGuard` doktrini ("her çapraz yazma yolu doğrulamalı") yalnız
    AniList yolunda çalışıyordu; `ExternalListSyncManager`'e `crossWriteBlocked` önkapısı
    eklendi (sync 6 hedef + delete 6 koşul/gate): doğrulanmayan çapraz yazım/silme
    `OUTCOME_SKIPPED_IDENTITY` ile ATLANIR (mesaj: `cross_sync_reason_identity_unverified`),
    yerel kaynak-kendisine yazım serbest, doğrulama önbellekli/tek-sefer. Ayrıca FAZ1-3
    akış haritası + üstüne-yazma semantiği denetimi: `ARASTIRMA_KAYNAK_SENKRON/14_CAPRAZ_ESITLEME_DENETIMI.md`.
    Kalanlar (açık): fabrika üretim-ölü (6/16), yeniden-adlandırılan başlık alias'ı,
    FAZ2 alan-kazanan satır okuması, CI.

---

## Doğrulama durumu

- **tree-sitter-kotlin** ile 6 değişik `.kt` dosyasının tamamı hatasız ayrıştırıldı (ERROR/MISSING
  yok) — önceki rapordaki yöntem.
- Sandbox'ta `java`/`gradle`/`kotlinc`/`ANDROID_HOME` yok; `scripts/run_sync_*_checks.sh`
  (`$KOTLINC`/`$JSON_JAR` ister) ve Gradle testleri **burada çalıştırılamaz** → CI'da derleme +
  `app/src/test` koşulması gerekiyor.
- API sözleşmeleri kaynak üzerinden doğrulandı: `SimklBatchEntry`/`SimklBatchResponse` alanları,
  `addToListBatchDetailed(token, entries)`, `titleMatchingAllowed`, `fetchAllUserRates`,
  `resolveKitsuMediaId` imzası/dönüşü, string kaynakları (`account_sync_media_id_not_found`
  `%1$s`, `account_sync_shikimori_id_not_found` `%1$s`, `account_sync_anilist_media_id_unresolved`
  argümansız) — çağrı biçimleriyle uyumlu.
- Değişen sembollerin test çağrıları tarandı: yalnız `scripts/tests/fixtures/ExternalListSyncManager.kt`
  (yerel `SyncResult` tanımı, etkilenmez).

## Bilinçli olarak yapılmayanlar

- `PendingSyncDrainer` başarı koşulu gevşetilmedi — kuyruk şişmesinin kaynağı yanlış
  sınıflandırmaydı, kaynaka düzeltildi.
- MAL ilerleme > toplam kırpması yapılmadı (kanıtlanamamış spekülasyon) — istenirse ayrı iş.
- `withResolvedIds` tümüyle yeniden yazılmadı — AniList 100M stableId'lerini null'lar
  `resolveVerifiedMalIdFromAniList`'i kırardı; bunun yerine yöneticilere `known*` kimlikleri geçildi.
- Puan ölçeği/tarih biçimi/"başlık bozulması" iddiaları denetlendi ve **sahte** bulundu
  ("Omokage Tayuta" = "Calling You"nın özgün adı).

## İzleyen işler

1. CI'da `assembleDebug` + `testDebugUnitTest` (özellikle `CrossProviderIdentityTest`,
   `CrossSyncIdentityGuardTest`, rapor biçim testleri).
2. Üretimde izlenecek: silme özetinde `atlandı` gerekçeleri artmalı, "Çevrimdışı kaydedildi" ve
   bekleyen-senkron kuyruğu yalnız gerçek ağ/HTTP hatalarında büyümelidir.
3. (İsteğe bağlı) `rawKitsuIdOf`/`shikimoriTargetIdOf` için saf yardımcı seviyesinde birim test —
   yardımcılar şu an `private`; yalnız test için `internal` yapılmaları tartışılabilir.
4. Ekosistem araştırması kalanları: Simkl tam-çıkış kararı (açık madde 7), Shikimori/Kitsu
   domain teyidi (8-9), Bangumi silme-semantiği teyidi (10) — `ARASTIRMA_KAYNAK_SENKRON/`.
