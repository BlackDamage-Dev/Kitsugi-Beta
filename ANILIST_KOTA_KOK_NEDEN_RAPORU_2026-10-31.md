# AniList Kota Sorunu — Kök Neden Raporu (2026-10-31)

> **Şikâyet (özet):** Listem sayfasında sayfa yenilenirken, anime/manga eklenip çıkarılırken,
> düzenlenirken ve arka plan eşitlemesi çalışırken sürekli **"kota doldu"** hatası alınıyor.
> Bazen düzenleme yerele kaydediliyor ama AniList'e hiç gitmiyor; kullanıcı aynı düğmeye
> tekrar tekrar basıyor.

Bu rapor, sorunun **uygulama kaynaklı** olduğunu (AniList'in keyfî bir kesintisi olmadığını)
kod üzerinden kanıtlar, AniList'in 2026'daki gerçek limitlerini belgeler ve uygulanan
kök düzeltmeleri listeler.

---

## 1. Sonuç özeti (TL;DR)

AniList'in **fiilî limiti 30 istek/dakika** (dokümante edilen 90 değil; API yıllardır
"degraded" durumda). Kitsugi ise bu kotayı **dört ayrı, birbirinden habersiz kapıdan**
harcıyordu:

| # | Yol | Eski davranış | Kota etkisi |
|---|-----|---------------|-------------|
| 1 | `KitsugiApiBase.executeAniListQuery` (detay, arama, ilişkiler, karakterler, takvim) | 350 ms aralık + 3 eşzamanlı istek | **≈171 istek/dk** — limitin 5,7 katı |
| 2 | `AniListSyncManager.postAniList` (**listeye ekleme / düzenleme / silme / favori**) | **hiçbir hız sınırı yok** | kullanıcı işlemlerinin tamamı kotasız |
| 3 | `AniListImportManager.postGraphQl` (**Listem → yenile**) | **hiçbir hız sınırı yok** | tek yenilemede 1 + sayfa sayısı istek, ardı ardına |
| 4 | Profil ekranlarındaki doğrudan `aniListClient` çağrıları | **hiçbir hız sınırı yok** | profil sekmeleri kotasız |

`PlatformRateLimiter` içindeki 2,1 sn aralık ise **yalnızca** arka plan senkronunda
uygulanıyordu; 1, 3 ve 4 numaralı yollar onu tamamen görmezden geliyordu. Yani uygulamanın
"kota koruması" vardı ama trafiğin çoğu o korumanın dışındaydı.

30 istek/dk bütçesini bir dakikada tüketmek için gereken şey çok az:
**Listem'i bir kez yenilemek** (500 kayıtlık liste = ~1 Viewer + 10 anime sayfası + 5 manga
sayfası + 1 profil ≈ 17 istek) + **iki-üç düzenleme** (kayıt başına 1–4 istek) + açık bir
detay sayfası → 429.

429 geldiğinde AniList **60 saniyelik ceza penceresi** uygular ve bu süre boyunca gelen
istekleri de reddeder. Uygulama bu pencereyi doğru yönetmediği için kullanıcı 1 dakika
boyunca her işlemde aynı hatayı görüyordu. Ayrıca `postAniList` 429 aldığında
`PlatformRateLimiter.notifyRateLimited` çağırıp **çağıran thread'i 60–120 sn uyutuyordu**
(kilit altında) — bu sırada diğer yollar kotayı tüketmeye devam ediyordu.

---

## 2. AniList gerçekleri (2026-10-31 itibarıyla)

Resmî doküman: <https://docs.anilist.co/guide/rate-limiting>

- API **degraded durumda**: limit **30 istek/dakika** (normal koşulda 90). Doküman bunu
  açıkça yazıyor: *"The API is currently in a degraded state and is limited to 30 requests
  per minute."*
- Limitin üstüne **burst limiter** var: kısa sürede çok istek atmak da 429 üretir.
- 429 yanıtı: **1 dakika timeout**; gövdede `{"errors":[{"message":"Too Many Requests.","status":429}]}`,
  başlıklarda `Retry-After`, `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`.
- `X-RateLimit-Remaining: 0` gelen **başarılı** yanıt da "bu pencerede kota bitti" demektir;
  sonraki istek büyük olasılıkla 429 alır.
- Ağır sorgular yalnızca yavaş değil, **HTTP 500** de üretebiliyor (çok sayıda ilişki düğümü
  içeren liste sorguları). Bu yüzden liste sorgusu hafif tutulmalı.
- AniList, ciddi kararsızlık dönemlerinde API'yi **tamamen kapatabiliyor** (403 +
  "temporarily disabled due to severe stability issues"). 2026-10-21'de PHP güvenlik açığı
  nedeniyle site+API kapatılmıştı.

Bağımsız doğrulamalar (canlı ölçümler):

- `autobrr/upbrr` #255 — canlı `X-Ratelimit-Limit: 30` ölçümü ve `Retry-After`'ın
  yok sayılmasının sessiz veri kaybına yol açması:
  <https://github.com/autobrr/upbrr/issues/255>
- `JoaoPucci/ani-gui` #286 — 30/dk için **60 sn'lik kayan pencerede 25 istek**, isteğin
  **yanıt geldiğinde** sayılması, 429'da bir dakikalık soğuma ve arka plan için kota rezervi:
  <https://github.com/JoaoPucci/ani-gui/pull/286>
- `dixit92/manga-list` #5 — 30/dk için 2,1 sn aralık, `Retry-After` (65 sn'ye kadar),
  Cloudflare'ın jenerik istemci imzalarını 403 ile reddetmesi:
  <https://github.com/dixit92/manga-list/pull/5>
- `LOST-4EVER/AniSequel` #2 — ağır liste sorgusunun HTTP 500 üretmesi; hata sınıflandırması:
  <https://github.com/LOST-4EVER/AniSequel/pull/2>

Depodaki önceki araştırma notu da aynı sonuca varmıştı:
`ARASTIRMA_KAYNAK_SENKRON/01_ANILIST.md`.

---

## 3. Kök nedenler (kod kanıtlı)

### 3.1 "Listem" düzenlemeleri hız sınırının tamamen dışındaydı

`AniListSyncManager.postAniList()` doğrudan `OkHttp` çağırıyordu; `PlatformRateLimiter`
veya `KitsugiApiBase` zamanlayıcısından geçmiyordu. Bu fonksiyon şunları yapar:

- `SaveMediaListEntry` (ekleme/düzenleme),
- `DeleteMediaListEntry`,
- `ToggleFavourite`,
- kimlik doğrulama sorguları (`Media(id)`, `Media(idMal)`, `MediaList(id)`),
- `Viewer`/`mediaListOptions` sorguları.

Yani **kullanıcının "düzenlerken kota doluyor" dediği yol tam olarak buradaydı** ve hiçbir
bütçeye tabi değildi.

### 3.2 Tek bir düzenleme 4–7 AniList isteği üretiyordu

`ExternalListSyncManager.syncEntry()` her düzenlemede:

1. `verifyAniListMediaIdentity()` → `Media(id){...}` (her seferinde, önbellek yok)
2. `SaveMediaListEntry`
3. `getAniListMediaFavoriteStatus()` → `Media(id){ isFavourite }` (kullanıcı favoriyi
   değiştirmemiş olsa bile)
4. Gerekirse `ToggleFavourite`
5. `resolveAniListMediaIdForEntry()` → `MediaList(id)` + `fetchAniListUserId()`

`+1 bölüm`e üç kez basmak = 12–20 istek. 30/dk bütçesinde bu, tek bir kullanıcı
etkileşimiyle kotanın yarısını yemek demek.

### 3.3 Liste yenileme kotasız bir toplu çekimdi

`AniListImportManager.fetchAllLists()` her yenilemede şunları **art arda, beklemesiz**
gönderiyordu:

- `fetchUserProfile` (her yenilemede!),
- `fetchViewer`,
- anime listesi sayfaları (`perPage: 50`),
- manga listesi sayfaları.

500 anime + 250 manga = 1 + 1 + 10 + 5 = **17 istek, saniyeler içinde**. Kota penceresi
60 sn olduğu için bu tek başına bütçenin yarısından fazlası.

### 3.4 İki bağımsız sayaç, tek gerçek limit

`KitsugiApiBase` 350 ms aralık + 3 eşzamanlılık (≈171 istek/dk) uygularken,
`PlatformRateLimiter` AniList için 2,1 sn (≈28 istek/dk) uyguluyordu. İkisi aynı kotayı
paylaştığını bilmiyordu; ayrıca `X-RateLimit-Remaining` yalnızca "degraded" bayrağını
çeviriyor, istekleri gerçekten yavaşlatmıyordu.

### 3.5 429 yönetimi thread'i kilitliyordu

`postAniList` 429'da:

```kotlin
kotlinx.coroutines.runBlocking { PlatformRateLimiter.notifyRateLimited("anilist", retrySec) }
```

→ `mutex.withLock { delay(60_000) }`. Bu, çağıran IO thread'ini 60–120 sn uyutur; üstelik
kilit altında olduğu için aynı platformun diğer çağrılarını da bekletir. Uyandığında istek
aynı kotayla tekrar dener → döngü.

### 3.6 Hata mesajı yanlış sınıflandırılıyordu

`MediaEntryRepository`, eşitleme hatası `401` içermiyorsa her durumda
**"📵 Çevrimdışı kaydedildi"** diyordu. Kota (429) ile çevrimdışı olmak aynı mesaja
düşüyordu; kullanıcı internetini kontrol ediyor, sonuç değişmiyordu.

### 3.7 Yeniden deneme yoktu

Kota nedeniyle başarısız olan iş bekleme kuyruğuna giriyordu ama kuyruk yalnızca uygulama
açılışında / ağ geri geldiğinde / bir sonraki başarılı eşitlemede boşaltılıyordu. 60 sn'lik
ceza penceresi bu tetiklemelerin hiçbirine denk gelmiyordu → kullanıcı elle tekrar deniyor,
her denemede ceza yenileniyordu.

### 3.8 Gereksiz istekler

- Düzenleyici her açılışta **4 ayrı** `Viewer`/`MediaList` isteği atıyordu
  (`fetchAniListUserId`, `getViewerOptions`, `getViewerCustomLists`, `getMediaListMetadata`);
  ilk ikisi aynı `Viewer.mediaListOptions` nesnesini okuyordu.
- `getViewerOptions` / `getViewerCustomLists` önbelleksizdi.
- Her düzenlemede favori durumu yeniden sorgulanıyordu.

---

## 4. Uygulanan düzeltmeler (v2.4.341)

### 4.1 Tek merkez: `AniListGate` (`data/auth/AniListGate.kt`)

AniList'e giden **tüm** trafik artık tek kapıdan geçiyor:

- **Kayan pencere:** son 60 sn'de tamamlanan istek ≤ `limit − 4` (limit başlıklardan
  öğrenilir, varsayılan 30). İstek **yanıt geldiğinde** sayılır → bizim penceremiz
  AniList'inkinden önce kapanmaz.
- **Asgari aralık:** 400 ms (429 sonrası 1,5 sn).
- **Patlama sınırı:** 2 sn'de en fazla 5 istek.
- **Öncelik:** `INTERACTIVE` (kullanıcı bekliyor), `BACKGROUND` (bildirim/zenginleştirme),
  `BULK` (liste yenileme). Arka plan, kullanıcıya ayrılmış 6 isteklik payı yiyemez; toplu
  çekim de 3 isteklik pay bırakır.
- **Soğuma:** 429 / `X-RateLimit-Remaining: 0` / gövdede "Too Many Requests" → `Retry-After`
  veya `X-RateLimit-Reset` kadar (yoksa belgelenen 60 sn) istek gönderilmez.
- **Fail-fast:** sıra beklemesi sınırı aşarsa `QuotaExhaustedException` fırlar; **hiçbir
  thread 60 sn uyutulmaz**.
- **Tekilleştirme + 1,5 sn okuma önbelleği:** aynı sorgu uçuştayken/hemen ardından ikinci
  HTTP isteği çıkmaz. Mutasyonlar asla önbelleklenmez, yazma sonrası okuma önbelleği
  temizlenir.

### 4.2 Tüm AniList yolları kapıya bağlandı

| Dosya | Değişiklik |
|-------|-----------|
| `AniListSyncManager.postAniList` | **Yazmalar** artık `AniListGate.withPermitBlocking` içinde; 429'da thread uyutmak yok, iş kuyruğa bırakılıyor |
| `KitsugiApiBase.executeAniListQuery` | 350 ms sayaç kaldırıldı; gate kullanılıyor, `priority` parametresi eklendi |
| `AniListImportManager.postGraphQl` | `BULK` önceliğiyle kapıdan geçiyor |
| `KitsugiProfileViewModel`, `KitsugiUserProfileViewModel` | Doğrudan istemci çağrıları kapıya bağlandı |
| Bildirim istemcisi | `BACKGROUND` önceliği |
| Kişi görseli / Fandom zenginleştirme | `BACKGROUND` önceliği + kota azalınca tamamen atlanır |
| `PlatformRateLimiter` | AniList kaydı **kaldırıldı** (tek kaynak artık gate); `notifyRateLimited` artık kilit tutarken uyumuyor |

### 4.3 İstek sayısı azaltıldı

- **Kimlik doğrulama önbelleği (6 saat / olumsuzda 10 dk):** aynı medya için tekrar tekrar
  `Media(id){...}` atılmıyor.
- **Favori eşitlemesi gereksizse atlanıyor:** son eşitlenen favori değeri yerelde tutuluyor;
  yalnızca bölüm/puan değiştiren düzenlemelerde favori sorgusu **hiç** çıkmıyor.
- **Düzenleyici açılışı:** 4 istek → 1 istek (ilk açılış), sonraki açılışlar 0–1 istek
  (30 dk Viewer önbelleği + 5 dk liste meta önbelleği).
- **Yenileme:** profil 15 dk, Viewer 24 saat önbellekte → 17 istek yerine 15 istek ve
  sayfalar arası tempo.
- **Yazma birleştirme (`ExternalSyncCoalescer`):** aynı kayda 1,2 sn içinde gelen
  düzenlemeler **tek** uzak yazmaya indirgeniyor; tüm uzak yazmalar tek sırada çalışıyor
  (farklı kayıtlar aynı anda kota patlatmıyor). Yerel yazma her zaman anında.

### 4.4 Kota artık "kalıcı hata" değil

- `MediaEntryRepository`: kota hatası ayrı mesajla gösteriliyor —
  **"⏳ AniList istek sınırı doldu — N sn sonra kendiliğinden yeniden denenecek"**.
- `PendingSyncDrainer.scheduleResume`: ceza bitince kuyruk **otomatik** boşaltılıyor;
  kullanıcının düğmeye tekrar basması gerekmiyor.
- Kuyruk boşaltılırken kota hâlâ aktifse iş **deneme bütçesini yakmıyor** (retry sayacı
  artmıyor) ve kalan işler ceza bitimine erteleniyor.
- Kota hatası artık "kota" olarak sınıflandırılıyor (`AniListGate.isQuotaMessage`), böylece
  "çevrimdışı" mesajı yanlışlıkla gösterilmiyor.

### 4.5 Ek düzeltmeler (aynı turda bulunanlar)

- **`PendingSyncDrainer.scheduleResume` erken dönüş hatası:** kuyruk boşaltma işi kendi
  içinden `scheduleResume` çağırdığında "iş zaten aktif" kontrolü erken dönüyordu → kota
  nedeniyle yarıda kalan kuyruk bir daha **kendiliğinden** denenmiyordu. Artık planlanan
  zamana göre karşılaştırma yapılıyor ve gerekirse yeniden planlanıyor.
- **`AniListGate.budget()` anlamı netleşti:** pencere tamamen doluysa da `EXHAUSTED`
  dönüyor (önceden yalnızca soğuma için `EXHAUSTED`, doluluk için `CONSERVE` dönüyordu).
- **`X-RateLimit-Remaining: 0` hatası:** "pencere sıfırlanana kadar bekle" süresi mutlak
  zaman damgası yerine **şimdi + süre** olarak yazılıyor; aksi halde geçmiş bir zaman
  damgası yazılıyor ve bekleme hiç uygulanmıyordu.
- **Sahte "kota" tespiti:** başarılı (2xx) bir yanıt gövdesinde geçen "too many requests"
  ifadesi artık kota sayılmıyor (inceleme metni gibi kullanıcı içerikleri yanlış
  sınıflandırılabiliyordu).
- **Askıya alınabilir yolda bloklayan semafor:** `withPermit` artık
  `kotlinx.coroutines.sync.Semaphore` kullanıyor; ana iş parçacığından çağrılsa bile
  bloklamıyor, askıya alıyor.
- **Toplu çekim payı:** liste yenileme (BULK), 26 isteğin tamamını tek başına tüketmiyor;
  3 isteklik pay kullanıcıya kalıyor.
- **Yenileme başlamadan hızlı ret:** ceza penceresi 5 sn'den uzunsa liste yenileme hiç
  başlamıyor; kullanıcıya "N sn sonra yeniden çekilebilir" deniyor (60 sn dönen spinner yok).
- **Tanılama ekranı:** `NotificationDiagnostics` AniList isteği arka plan önceliğiyle kapıya
  bağlandı; tüm tanılama çağrıları IO'ya alındı (ana iş parçacığında ağ beklemesi bitti).
- **Platform hata açıklamaları** (`pf_err_anl_2001/2002`) yeni davranışa göre güncellendi.

### 4.6 Testler

`app/src/test/java/com/kitsugi/animelist/data/auth/AniListGateTest.kt`:

- kayan pencere dolunca isteğin ertelenmesi,
- `Retry-After` → soğuma süresi,
- `X-RateLimit-Remaining: 0` + `X-RateLimit-Reset` → pencere sonuna kadar bekleme,
- arka planın kullanıcıya ayrılmış payı tüketememesi,
- kota mesajı işaretinin tanınması,
- okuma önbelleğinin TTL sonunda geçersizleşmesi.

---

## 5. Doğrulama (elle test)

1. AniList hesabı bağlı, ~500 kayıtlık liste ile Listem → **Yenile**.
   - Beklenen: liste gelir; süre uzayabilir (tempo) ama **429/kota hatası görünmez**.
   - Logcat: `KitsugiApiBase: AniList 429 — ...` satırı **olmamalı**.
2. Yenileme sürerken bir kaydı düzenle (durum/puan).
   - Beklenen: yerele anında yazılır, uzak yazma birkaç saniye içinde tamamlanır
     (BULK trafiği kullanıcı payını yemez).
3. Aynı kayda `+1` düğmesine 5 kez hızlıca bas.
   - Beklenen: tek `SaveMediaListEntry` isteği (Logcat'te tek `postAniList`), kota düşmez.
4. Bilerek kota tüket (hızlı yenilemeler) → hata mesajı **"⏳ … sn sonra kendiliğinden
   yeniden denenecek"** olmalı; ceza bitince kayıt kendiliğinden AniList'e gider.
5. Uçak modunda düzenle → mesaj **"📵 Çevrimdışı kaydedildi"** kalmalı (kota ile karışmamalı).

---

## 6. Kalan riskler / öneriler

1. **Detay sayfası istek sayısı:** bir yapım açıldığında ilişkiler, karakterler, ekip,
   stüdyo, sosyal sekmeler ayrı ayrı sorgulanıyor. Gate bunları tempolar ama 30/dk sert
   sınırdır; ileride bu 5–6 sorgu **tek GraphQL isteğinde** birleştirilmeli (AniList
   `Media` nesnesi hepsini tek seferde verebiliyor).
2. **Liste sorgusunun ağırlığı:** `perPage: 50` ve her kayıt için `description` çekiliyor.
   Çok büyük hesaplarda bu HTTP 500 üretebiliyor (AniSequel #2). Öneri: listede
   `description` çekmeyip detay sayfasında tembel yüklemek.
3. **AniList dışı platformlar:** MAL/Jikan, Kitsu, Shikimori, Simkl ve Bangumi'nin kendi
   limitleri var; bu turda yalnızca AniList tek kapıya alındı. Aynı desen (tek kayan pencere
   + öncelik + soğuma) diğerlerine de uygulanırsa "kota" mesajları tümden biter.
4. **Kota telemetrisi:** `AniListGate.remainingRequests()` / `budget()` hazır; Ayarlar →
   Tanılama ekranında "AniList kota: 12/26, yenilenme 34 sn" göstergesi faydalı olur.
5. **AniList sunucu kesintisi:** kodda zaten var olan `AniListServiceDownException` +
   45 sn devre kesici korunuyor; API tamamen kapandığında (403 "temporarily disabled")
   arayüz yedek kaynaklara düşüyor.
6. **Başlık araması:** "Tümü" aramasında AniList'e giden eşzamanlı sorgular hâlâ çok
   olabilir; arama tarafında sonuç önbelleği (sorgu başına 60 sn) kotayı belirgin azaltır.

---

## 7. Değişen dosyalar

```
+ data/auth/AniListGate.kt                       (yeni — tek kota kapısı)
+ data/local/ExternalSyncCoalescer.kt             (yeni — yazma birleştirme/sıralama)
+ test/.../data/auth/AniListGateTest.kt           (yeni — kota muhasebesi testleri)
~ data/auth/AniListSyncManager.kt                 (kapı + kimlik/favori/viewer önbellekleri)
~ data/auth/AniListImportManager.kt               (kapı + Viewer/profil önbelleği)
~ data/auth/ExternalListSyncManager.kt            (favori atlaması, gate'e devir)
~ data/auth/PlatformRateLimiter.kt                (AniList kaydı kaldırıldı, kilit düzeltmesi)
~ data/local/MediaEntryRepository.kt              (yazma birleştirme, kota mesajı, otomatik devam)
~ data/local/PendingSyncDrainer.kt                (kota farkındalığı + kendiliğinden devam)
~ data/remote/KitsugiApiBase.kt                   (350 ms sayaç kaldırıldı, gate + öncelik)
~ data/remote/KitsugiAniListNotificationClient.kt (BACKGROUND önceliği)
~ data/remote/KitsugiPersonImageAggregator.kt     (BACKGROUND önceliği)
~ data/fandom/FandomAniListSource.kt              (BACKGROUND önceliği)
~ ui/app/AuthViewModel.kt                         (favori işaretlerinin sıfırlanması)
~ ui/app/KitsugiProfileViewModel.kt               (gate)
~ ui/screens/profile/KitsugiUserProfileViewModel.kt (gate)
~ data/auth/AniListSyncStatus.kt                  (YENİ — kalıcı "neden eşitlenmiyor" durumu)
~ data/notifications/NotificationDiagnostics.kt   (kapı + IO)
~ KitsugiApplication.kt                          (otomatik yenileme: sil-yaz yerine birleştir + açılışta kuyruk)
~ core/notifications/AiringNotificationWorker.kt  (Simkl: sil-yaz yerine birleştir)
~ ui/screens/mylist/MyListViewModel.kt            (syncHealth + "Şimdi dene")
~ ui/screens/mylist/MyListScreen.kt               (durum şeridi)
~ res/values/strings.xml, res/values-en/strings.xml  (kota mesajları + hata açıklamaları)
```

---

## 8. İkinci tur — "günlerdir hiçbir şey AniList'e gitmiyor"

Kullanıcı geri bildirimi: *"Listem'de son birkaç gündür eklediğim hiçbir yeni içerik veya
düzenleme AniList ile eşitlenmemiş."* Bu, 429 sorunundan **ayrı** ve daha ağır bir kök
neden zinciri ortaya çıkardı. Dördü de "sessiz" çalışıyordu:

### 8.1 24 saatlik otomatik yenileme, yerel kayıtları SİLİYORDU

`KitsugiApplication` açılışta (24 saat geçtiyse) listeleri
`MediaEntryRepository.replaceSourcePreservingAdultFlags(source, entries)` ile yazıyordu.
Bu fonksiyon **kaynağın tüm satırlarını siler** (`dao.deleteBySource`) ve uzaktan geleni
yeniden ekler; ayrıca o kaynağın **bekleyen kuyruk işlerini de temizler**
(`clearPendingJobsFor(source)`).

Sonuç: AniList'e henüz gönderilememiş **yeni** bir kayıt, uzak listede olmadığı için
bir sonraki otomatik yenilemede **tamamen siliniyordu**; düzenlemeler ise uzak (eski)
değerlerle eziliyordu. Kullanıcının "ekliyorum, eşitlenmiyor" gözleminin ana nedeni budur.

**Düzeltme:** otomatik yenileme artık `smartImport(source, entries, allowDelete = false)`
kullanıyor (birleştirir, silmez) ve **önce bekleyen kuyruğu gönderiyor**
(`drainPendingQueue(freshBudget = true)`), sonra listeyi çekiyor.

### 8.2 Bekleyen değişikliği olan satır, uzak kopyayla EZİLİYORDU

`smartImport`, eşleşen satırı uzak değerlerle güncelliyordu; satırın henüz gönderilmemiş
bir değişikliği varsa bu, kullanıcının düzenlemesini geri alıyordu.

**Düzeltme:** kuyrukta işi olan satırlar (`pendingSyncDao` içindeki `entryJson.id`)
içe aktarımda **korunuyor**; uzak veri onları ezmiyor.

### 8.3 Deneme sayacı "ölüm cezası" gibiydi

`PendingSyncDrainer.drain()` yalnızca `retryCount < 5` işleri deniyordu. Kota (429)
döneminde her deneme sayacı artırdığı için işler 5'te takılıyor ve **bir daha asla**
denenmiyordu. Kuyruk da yalnızca (a) ağ değişiminde, (b) bir yazma **başarılı** olduğunda
boşaltılıyordu; ikisi de günlerce gerçekleşmeyebilir.

**Düzeltme:**
- Kota hatası artık **deneme sayacını artırmaz** (v2.4.341).
- Uygulama açılışında kuyruk `freshBudget = true` ile zorla boşaltılır → 5'i aşmış işler
  dahil herkes bir şans daha alır.
- Listem ekranındaki **"Şimdi dene"** düğmesi aynı işi elle tetikler.

### 8.4 Kimlik doğrulaması "katalog ulaşılamadı" diye HER ŞEYİ kilitliyordu

`ExternalListSyncManager.crossWriteBlocked()` MAL kataloğunu doğrular. Katalog
ulaşılamadığında (`Unverifiable`) veya istek istisna attığında sonuç `false` dönüyor ve
yazma **sessizce atlanıyordu**. MAL API 429/403/timeout verdiğinde bu, AniList dahil
hiçbir kaynağa hiçbir şey yazılmaması demek — ve tek bir mesaj bile çıkmıyordu.

**Düzeltme:** yalnızca guard'ın açıkça **reddettiği** (başlık/tür/yıl uyuşmazlığı)
eşleşmeler yazmayı durdurur. Katalog erişilemediğinde, satırın kimliği kendi kaynağından
geliyorsa (native MAL satırı) yazma devam eder. Reddedilen/kimliksiz durumlar artık
"kimlik doğrulanamadı" gerekçesiyle **kullanıcıya gösterilir**.

### 8.5 Doğru uyarılar: tek bir "neden" ve kalıcı durum

Yeni `AniListSyncStatus` (yeni dosya) son başarılı yazmanın zamanını ve son hatanın
**sınıflandırılmış** nedenini kalıcı olarak tutar:

| Neden | Listem şeridi (örnek) |
|-------|------------------------|
| Kota | ⏳ AniList istek sınırı doldu — 12 değişiklik 43 sn sonra kendiliğinden gönderilecek |
| Oturum | 🔑 AniList oturumu geçersiz — 12 değişiklik gönderilemiyor, yeniden giriş yapın |
| Bağlı değil | 🔌 AniList bağlı değil — 12 değişiklik yalnızca bu cihazda duruyor |
| Kapalı | ⚙️ AniList eşitlemesi ayarlardan kapalı |
| İnternet | 📵 İnternet yok — bağlantı gelince gönderilecek |
| API kapalı | 🛠 AniList API geçici olarak kapalı |
| Kimlik | 🔎 N değişikliğin AniList karşılığı doğrulanamadı |

- Şerit yalnızca gerçekten bekleyen bir şey varsa görünür; üzerinde **"Şimdi dene"**
  düğmesi vardır.
- Tek tek yapılan işlemlerde çıkan mesajlar da aynı sınıflandırmayı kullanır:
  "📵 Çevrimdışı kaydedildi" tek başına artık gösterilmez; yerine kota/oturum/internet/API
  ayrımı yapılır. Aynı gerekçe 2 dakikada bir defadan sık gösterilmez.
- **Başarılı görünen ama AniList'in atlandığı** turlar (bağlı değil / kimlik çözülemedi /
  kapalı) artık sessiz değil: durum kaydedilir ve şeritte görünür.

---

# 9. Üçüncü tur — TÜM kaynaklar, TÜM ekranlar, halk dili (v2.4.341)

Kullanıcı talebi: *"Bir kaynağa ait durum/uyarı/bilgi nerede görünüyorsa — sadece
AniList değil, MyAnimeList, Kitsu, Shikimori, Bangumi, Simkl, TMDB, eklenti
kaynakları da — doğru mantık ve doğru cümleyle, sade günlük dille yazılsın."*

## 9.1 İncelenen yerler (envanter)

| Alan | Durum (öncesi) | v2.4.341 |
|------|----------------|----------|
| Listem / kitaplık | Yalnız AniList için şerit; diğer beş kaynağın durumu **hiç yok** | `SourceSyncStatus` + kaynak kaynak şerit, bekleyen sayısı, "Şimdi dene" |
| Tek işlem bildirimi (ekle/düzenle/sil) | Her hata "📵 Çevrimdışı kaydedildi" | Kota / oturum / bağlı değil / kapalı / internet / servis / kimlik ayrımı |
| Bildirimler ekranı | Yalnız hata kaydı listesi | Üstte kaynak durum kartı (aynı cümleler) |
| Liste içe aktarma (6 kaynak) | Ham istisna metni (`HTTP 401`, `JSONException`) | Kaynak adı + sınıflandırılmış halk dili cümlesi |
| Arama / Keşfet / detay / sezon takvimi / çapraz senkron | Hata kodu rozeti + "ne oldu / ne yapmalı" bloğu vardı ama **kaynak eşitleme** durumu yok | Ayrıca eşitleme hataları `PlatformFailureRegistry`'ye yazılıyor → rozet/panel aynı dili konuşuyor |
| Profil ekranları (kendi/profil) | Kota uyarısı "bekleyin" diyordu, süre yok | Süre + kaynak adı |
| Manga/eklenti kaynak sağlığı (KTS-SRC / KTS-CSP kodları) | Kod + açıklama vardı | **Değişmedi** — bu aile ayrı; bkz. 9.4 |

## 9.2 Tek doğruluk kaynağı: `SourceSyncStatus`

`data/auth/SourceSyncStatus.kt` (eski `AniListSyncStatus` yerine) altı kaynak için
kalıcı durum tutar: `ANILIST, MAL, KITSU, SHIKIMORI, SIMKL, BANGUMI`.
Gerekçeler: `NONE, OK, QUOTA, AUTH, NOT_CONNECTED, DISABLED, OFFLINE, SERVICE_DOWN,
IDENTITY, UNKNOWN`.

Aynı cümleler üç yerde kullanılır: Listem şeridi, bildirim ekranı kartı, işlem
bildirimleri. Metinler (`describe`, `describeAction`) şöyle:

```
⏳ AniList istek sınırı doldu — 12 değişiklik 43 sn sonra kendiliğinden gönderilecek
🔑 MyAnimeList oturumu geçersiz — 3 değişiklik gönderilemiyor. Ayarlar > Platformlar üzerinden yeniden giriş yapın.
🔌 Kitsu hesabı bağlı değil — 5 değişiklik yalnızca bu cihazda duruyor
⚙️ Shikimori eşitlemesi ayarlardan kapalı — 2 değişiklik yalnızca bu cihazda duruyor
📵 İnternet yok — Simkl için 1 değişiklik bağlantı gelince gönderilecek
🛠 Bangumi servisi geçici olarak yanıt vermiyor — 4 değişiklik daha sonra gönderilecek
🔎 AniList: 2 değişikliğin yapım karşılığı doğrulanamadı (yanlış kayda yazmamak için beklemede)
```

**Bilgi ≠ hata:** `DISABLED` ve `NOT_CONNECTED` kullanıcının bilinçli seçimi / henüz
kurulmamış bir bağlantı. `SourceSyncStatus.isError(reason)` bunları ayırır; şerit ve
kart **nötr ton** kullanır, gerçek arızalarda (kota/oturum/internet/servis/kimlik)
kırmızı ton kullanılır.

## 9.3 Sessiz kalan yerler kapatıldı

- `KitsugiApplication` açılış boşaltması ve AniList/MAL/Simkl otomatik senkronu artık
  kaynak başına `reportSuccess` / `reportFailure` yazar → "hiçbir şey eşitlenmiyor ama
  neden yok" durumu kalmadı.
- `ExternalListSyncManager.sourceFailure(...)` her yazma hatasını
  `PlatformFailureRegistry`'ye de kaydeder → hata kodu rozetinden "ne oldu / ne yapmalı"
  görülebilir.
- Liste içe aktarma hataları `importFailureMessage(...)` ile kaynak adı + sınıflandırma
  üretir; içe aktarma başarısı da kaynağın eski hata durumunu temizler.
- Profil ekranlarındaki kota uyarısı artık kaynak adı + kalan saniye içeriyor.

## 9.4 Kasıtlı olarak ayrı tutulan kod aileleri

- `data/platform/PlatformErrorCode.kt` → **KTS-ANL/MAL/KIT/SHK/BGM/SIM/TMDB-…** uç nokta
  hataları (65 kod; `pf_err_<prefix>_<NNNN>_<title|why|fix>` anahtarları, TR+EN tam).
- `data/diagnostics/PlatformErrorCatalog.kt` → **KTS-SRC-90xx** (manga kaynağı) ve
  **KTS-CSP-91xx** (CloudStream eklentisi) kodları; `platform_error_src_*` /
  `platform_error_csp_*` anahtarları.

İkisi **kopya değil**, farklı arıza aileleri. Birleştirilmemeli; kullanıcıya gösterilen
numara şeması (KTS-…) korunmalı.

## 9.5 Doğrulama

- `R.string.*` taraması: Kotlin'de referans verilen **tüm** anahtarlar `res/values/*.xml`
  içinde var (0 eksik).
- XML'ler parse ediliyor; yeni anahtarlar TR+EN çift.
- `./gradlew :app:testDebugUnitTest` (kullanıcı tarafında çalıştırılacak).

---

# 10. Dördüncü tur — günlükler: eksiksiz, kalıcı, çökmeden (v2.4.342)

Kullanıcı talebi: *"Bu kaynaklarla ilgili oluşabilecek tüm kayıtlar, hatalar, sorunlar,
uyarılar — arka planda olanları o anda elle kopyalayamadığımız için günlük her şeyi
alabilmeli; çökme, donma yapmadan."*

## 10.1 Eski durumun kök nedeni

| Sorun | Sonuç |
|-------|-------|
| "Günlükler" ekranı `logcat -d` okuyordu | Android 10+ üzerinde yalnızca kendi süreç; **halka tampon dolunca eski satırlar silinir** → "az önce ne oldu" kanıtı kayıp |
| `FileLoggingTree` yalnızca oynatıcı/birkaç tanı noktasından çağrılıyordu | Eşitleme, ağ, kuyruk, worker hataları **dosyaya hiç girmiyordu** |
| Ekran yalnızca son 1500 satırı tutuyordu | Uzun süreli arka plan sorunları (gece boyunca süren 429'lar) görünmüyordu |
| Ekmek kırıntısı dosyası her kullanıcı eyleminde baştan yazılıyordu | Gereksiz disk yükü (donma riski) |
| Donmalar için hiçbir kayıt yoktu | ANR/`LMKD` kill sonrası yalnızca "temiz kapanmadı" notu kalıyordu; **hangi kodun kilitlediği** kayıp |

## 10.2 Yeni mimari

1. **`KitsugiLog`** (`core/diagnostics/KitsugiLog.kt`) — tek yazma yolu.
   - Bellek halkası: son 3000 satır (anında görüntüleme).
   - Kalıcı dosya: `files/diagnostics/kitsugi_runtime.log`, 4 MB'da döner, `.1` yedeği
     (toplam ≈ 8 MB).
   - **Kilitlemez:** tek daemon iş parçacığı + 4096 kapasiteli kuyruk; kuyruk dolarsa satır
     atılır ve `dropped` sayacı artar. Ana iş parçacığı hiç beklemez.
   - **Tekrar sıkıştırma:** aynı satır 1,5 sn içinde tekrarlanırsa `[… N kez tekrarlandı]`.
   - Satır biçimi: `2026-10-31 14:03:22.193  W/SYNC   [anilist] mesaj | ayrıntı`.
   - Kanallar: `APP · SYNC · NET · SOURCE · WORK · PLAYER · CRASH · FREEZE · USER · SYSTEM`.

2. **Besleyiciler (kim, neyi yazıyor)**

   | Kaynak | Kanal | Nereden |
   |--------|-------|---------|
   | AniList/MAL/Kitsu/Shikimori/Simkl/Bangumi eşitleme sonucu | SYNC | `SourceSyncStatus.reportSuccess/reportFailure` |
   | AniList kota kapısı (429, `X-RateLimit-Remaining=0`) | SYNC | `AniListGate.onResponse` |
   | Kuyruk boşaltma turları ve kalan işler | WORK | `PendingSyncDrainer.drain` |
   | Her ağ denemesi (host/yol/kod/süre/kota başlıkları) | NET | `KitsugiNetworkLogInterceptor` (paylaşılan `KitsugiHttpClient` + TMDB) |
   | Hata kodu üretimi (KTS-…) | NET | `PlatformFailureRegistry.record` |
   | Manga/eklenti tanıları | SOURCE | `MangaLogger` + oynatıcı → `FileLoggingTree` |
   | Çökmeler (Java/Kotlin) | CRASH | `KitsugiCrashHandler` (+ `flushBlocking(2 sn)`) |
   | Sessiz ölüm / beklenmedik kapanma | SYSTEM/CRASH | `KitsugiSessionSupervisor` |
   | **Donmalar** | FREEZE | `UiFreezeWatchdog` (yeni) |
   | Uygulamanın kendi `Log.*` satırları (kütüphaneler, eklenti çalışma zamanı) | SYSTEM | `LogcatCapture` (yeni, kendi PID'i, saniyede ≤300 satır) |
   | Ekran geçişleri | USER | `KitsugiSessionSupervisor.noteScreen` |

3. **`UiFreezeWatchdog`** — ana iş parçacığını saniyede bir boş `post` ile yoklar; yanıt
   2,5 sn'yi geçerse o anki **yığın izi** FREEZE satırı olarak yazılır (≥8 sn → ERROR).
   Donma başına en fazla 5 sn'de bir kayıt. Kendi hataları yutulur, uygulama etkilenmez.

4. **`LogcatCapture`** — `logcat -v time --pid=<pid>` akışı, daemon iş parçacığı.
   Kendi yazdığımız `KitsugiLog` satırları etiketle ayıklanır (çift kayıt yok). Cihazda
   `logcat` çağrılamıyorsa tek satır not düşüp susar.

5. **Görüntüleyici (`DeveloperLogsDialog`)**
   - Kaynak: **Uygulama günlüğü** (varsayılan, kalıcı) / Sistem (logcat).
   - Konu çipleri: Tüm konular · Eşitleme · Ağ · Kaynak · Arka plan · Donma · Çökme ·
     Sistem · Diğer. Seviye çipleri ve arama eskisi gibi çalışır (`I/SYNC` biçimi mevcut
     `\s[VDIWEF]/` ayrıştırıcısıyla uyumlu).
   - Üstte boyut/satır/atlanan tekrar bilgisi; kopyala · kaydet · paylaş · temizle.
   - Diskten yalnızca son ~512 KB okunur → 8 MB'lik günlük bile anında açılır.

6. **Çökme raporu** artık `kitsugi_runtime.log` dosyasını da ek olarak paylaşır.

## 10.3 "Donma yapmama" önlemleri (özet)

- Tüm yazma işi UI dışında; kuyruk doluysa **at, say, devam et**.
- Görüntüleyici: IO dispatcher + `RandomAccessFile` ile son 512 KB, ekranda en fazla 3000 satır
  ve `LazyColumn` (zaten satır başına `SelectionContainer` — bilinen Compose seçim çökmesi
  için korunmuş mevcut çözüm).
- `logcat` alt süreci asla `waitFor` ile beklenmez; okuma kesilirse akış biter.
- Kırıntı dosyası yazımı 1,5 sn'de bir ile sınırlandı; çökme anında `flushNow` zorlar.

## 10.4 Doğrulama

- `KitsugiLog.*` çağrılarının tamamı tanımlı üyelerle eşleşiyor (otomatik tarama).
- Parantez/blok denetimi (`/tmp/balcheck.py`) tüm değişen dosyalarda temiz.
- `./gradlew :app:testDebugUnitTest` (kullanıcı tarafında).
- Elle: Ayarlar ▸ Sistem ▸ Geliştirici günlükleri → "Uygulama günlüğü" → Listem'de bir kayıt
  düzenleyin; `SYNC` ve `NET` satırları görünmeli. Uygulamayı zorla durdurun; yeniden açınca
  `SYSTEM` kanalında "Önceki oturum temiz kapanmadı" satırı olmalı.
