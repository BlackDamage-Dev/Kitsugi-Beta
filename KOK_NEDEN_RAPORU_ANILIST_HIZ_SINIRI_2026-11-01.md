# Kök Neden Raporu — AniList neden uygulama genelinde sürekli bozuk? (2026-11-01)

**Sürüm:** v2.4.340 · **Branch:** `arena/f110cbe0-kitsugi-beta`
**Şikâyet:** *"AniList hesabım üzerinden uygulama genelinde hiçbir şey düzgün çalışmıyor; sürekli istek sınırı doldu uyarısı ve Listem boş."*

---

## 1. Gözlem (ekran görüntüsü)

`Listem` ekranı:
- **"Listeniz boş"** + *"Hesabınızdaki verileri çekmek için 'Senkronize Et' butonunu kullanın."*
- Üstte AniList uyarı bandı: *"AniList istek sınırı doldu — 1 değişiklik 1 sn sonra kendiliğinden gönderilecek"*
- Yani: (a) liste içe aktarımı hiç tamamlanamıyor, (b) yazma denemeleri kuyruğa düşüp orada bekliyor.

İki ayrı belirti, **tek bir kök nedenin** sonucu.

---

## 2. Kök neden: 30 istek/dk'lık TEK kota, ÜÇ ayrı yoldan habersiz tüketiliyordu

### 2.1 AniList'in gerçek limiti

> "The API is currently in a degraded state and is limited to **30 requests per minute**."
> — https://anilist.gitbook.io/anilist-apiv2-docs/docs/guide/rate-limiting

Aynı doküman:
- Her yanıtta `X-RateLimit-Limit` / `X-RateLimit-Remaining` başlıkları gelir.
- Limit aşılınca **1 dakikalık zaman aşımı** uygulanır; bu süreçte gelen her istek
  `Retry-After` + `X-RateLimit-Reset` döner ve **zaman aşımını yeniden uzatır**.
- 429, HTTP koduyla birlikte gövdeye de yazılır:
  `{"data":null,"errors":[{"message":"Too Many Requests.","status":429}]}`
- Ayrıca, üst limitin altında kalsanız bile kısa aralıklı istek yığınlarını kesen
  bir **burst limiter** vardır.

### 2.2 Uygulamada durum

| Yol | Eski hız politikası | Sonuç |
|---|---|---|
| `KitsugiApiBase.executeAniListQuery` (Keşfet / detay / bildirim / takvim / profil) | normalde **350 ms** (≈171 istek/dk), "degraded" modda 1.500 ms (≈40/dk), 3 eşzamanlı izin | Limitin 5-6 katı; sık sık 429 + burst limiter |
| `AniListImportManager.postGraphQl` (Listem içe aktarımı + açılışta otomatik senkron) | **Hiç sınırlama yok.** 1 Viewer + N sayfa (50 kayıt/sayfa) arka arkaya, tek 429'da **tüm aktarım çöküyor**, hiçbir kayıt yazılmıyor | Kalıcı **"Listeniz boş"** |
| `AniListSyncManager.postAniList` (durum/puan/bölüm yazma) | Kendi 3 denemelik döngüsü + `PlatformRateLimiter` (**ayrı sayaç**, 2.100 ms) | Bu döngü beklerken diğer iki yol kotayı yemeye devam ediyordu |

**Kritik nokta:** bu üç yol birbirinin soğumasını görmüyordu. Biri 429 alıp beklerken
diğerleri istek göndermeye devam ettiği için AniList zaman aşımını sürekli baştan
başlatıyordu — kullanıcının gördüğü "çözülmeyen" tablo tam olarak budur.

### 2.3 İkinci kök neden: GraphQL katmanındaki 429 okunmuyordu

AniList 429'u çoğu zaman **HTTP 200 + `errors[status=429]`** olarak döndürür.
Eski kod:
- `AniListImportManager`: `require(errors == null || errors.length() == 0)` → tek bir
  hız sınırı yanıtı tüm içe aktarımı istisnaya düşürüyordu.
- `AniListSyncManager`: `if (errors.length() > 0) throw` → mutasyon "kalıcı hata"
  sayılıp kuyruğa atılıyordu (oysa 1 dk sonra tekrar denense çalışacaktı).
- `KitsugiApiBase`: `errors` içeren gövdeyi **başarılı** sayıp `data: null` döndürüyordu
  → raflar/detaylar sessizce boş kalıyordu.

### 2.4 Üçüncü kök neden: düzenleme başına 5-6 istek

Tek bir "bölüm +1" dokunuşu şunları yapıyordu: `MediaList(id)` sorgusu → `Viewer`
(kimlik doğrulama) → `Media(idMal)` çözümü → `Media(id)` kimlik doğrulama → mutasyon
→ **artı** her seferinde favori durumu sorgusu. 30/dk kotasında bu, **dakikada ~6
düzenlemede** hız sınırına takılmak demekti.

### 2.5 Kuyruk cezası

`PendingSyncDrainer`, 429'dan kaynaklanan başarısızlıklarda da `retryCount` artırıyordu;
tek bir hız sınırı fırtınası 5 deneme hakkını **saniyeler içinde** bitiriyordu.

---

## 3. Uygulanan çözümler

### 3.1 `AniListGovernor` (yeni) — tek ve doğru hız yöneticisi
`app/src/main/java/com/kitsugi/animelist/data/auth/AniListGovernor.kt`

- **Tek bütçe:** üç yol da aynı atomik slot rezervasyonundan geçer.
- **Doğru aralık:** `X-RateLimit-Limit` başlığından limiti öğrenir, aralığı
  `60 sn / limit + 200 ms` olarak uygular (başlık gelmezse 30/dk varsayımı → 2,2 sn).
  Kota yetersizse hızlanmaz.
- **Proaktif bekleme:** `X-RateLimit-Remaining ≤ 1` ise 429 yemeden
  `X-RateLimit-Reset` anına kadar bekler.
- **Gerçek soğuma:** 429'da `Retry-After` / `X-RateLimit-Reset` süresince **tüm**
  yollar 0 ms'de fail-fast döner (AniList'i dövmeyi bırakır → zaman aşımı uzamaz),
  süre bitince kaldığı yerden devam eder.
- **GraphQL 429 tespiti:** `errors[status=429]` / "Too Many Requests" / "rate limit"
  → yeniden denenebilir (artık sessiz boş sonuç üretmez).
- **Devre kesiciler:** "temporarily disabled" şalteri (45 sn) + ardışık gateway/timeout (20 sn).
- Hem `suspend` (`acquire`) hem bloklayan (`acquireBlocking`) API.

### 3.2 İçe aktarım artık kırılgan değil — `AniListImportManager`
- Her istek ortak bütçeden geçer; 429/5xx/**GraphQL 429** için soğumayı bekleyip
  yeniden dener (4 deneme, 70 sn'ye kadar bekleme).
- **Kısmi sonuç:** en az bir sayfa alındıysa eldeki kayıtlar döndürülür
  (`ImportResult.complete = false`); hiç alınamadıysa net hata fırlatılır.
  → Artık "ya hep ya hiç" değil; liste kademeli dolar.
- Yeni hata tipleri: `AniListRateLimitException`, `AniListUnavailableException`,
  `AniListAuthException` (oturum düştüyse kullanıcıya net mesaj).

### 3.3 Mutasyon yolu — `AniListSyncManager`
- `postAniList` artık ortak bütçeden geçer; 429'da gerçekten bekler; GraphQL
  katmanındaki 429'u yeniden denenebilir sayar.
- **Kimlik önbelleği (yeni):** `Media(id)` doğrulaması 30 dk, `idMal → AniList id`
  eşlemesi 30 dk önbellekte → aynı kayıt üst üste düzenlenirken **ağa çıkılmaz**.
  Düzenleme başına istek sayısı 5-6'dan **1-2'ye** düştü.
- `clearSessionCaches()`: çıkış / hesap değişiminde önbellekler düşürülür
  (`AuthViewModel.disconnectExternalAccount`).

### 3.4 Diğer AniList yolları da bütçeye bağlandı
- `KitsugiApiBase.executeAniListQuery`: kendi 350 ms'lik sayacı kaldırıldı,
  yöneticiye devredildi; hata sınıflandırma (GraphQL 429 dahil) iyileştirildi.
- `PlatformRateLimiter`: `anilist` platformu yöneticiye devredildi (çift sayaç kalktı).
- `KitsugiProfileViewModel`, `KitsugiUserProfileViewModel`, `NotificationDiagnostics`
  içindeki doğrudan `graphql.anilist.co` çağrıları da artık bütçeden geçiyor.

### 3.5 Kuyruk artık hız sınırında kendini yemiyor — `PendingSyncDrainer`
- Soğuma/kesinti aktifken kuyruk boşaltma **atlanır** (5 hak saniyelerde erimez).
- 429 / "istek sınırı" hatası deneme hakkını **yakmaz** (kullanıcı hatası değil).

### 3.6 Açılışta otomatik senkron artık liste SİLMEZ — `KitsugiApplication`
- Tam aktarımda eskisi gibi `replaceSourcePreservingAdultFlags`.
- **Kısmi aktarımda `smartImport(allowDelete = false)`** → var olan satırlar korunur.
- Aktarım yarım kaldıysa 24 saatlik "son senkron" damgası **yazılmaz**;
  eksik kalan kayıtlar bir sonraki açılışta tamamlanır.

### 3.7 YAZMA KATMANI: listeye ekleme / güncelleme / çıkarma neden hiç çalışmıyordu?

Hız sınırı düzeltildi ama tek başına yetmezdi — **ikinci, bağımsız bir kök neden** daha var:
kimlik doğrulama kapıları o kadar katıydı ki çoğu kayıtta yazma hiç gerçekleşmiyordu.

**A) Mevcut kayıt güncellemesinde SONUCU KULLANILMAYAN doğrulama yazmayı bloke ediyordu.**
`updateAniListEntry`, `aniListEntryId` (AniList liste kaydı kimliği) varsa
`resolveAniListMediaIdForEntry(...)` çağırıyor ve dönen `mediaId` değerini **hiçbir yerde
kullanmıyordu** (mutasyon yalnızca `id` ile yapılıyor). Çağrının tek etkisi,
doğrulama başarısız olduğunda `error("…doğrulanamadı")` fırlatıp **yazmayı iptal etmekti**.
Doğrulama zinciri (MAL katalog başlığı + yıl katılığı) sık sık takıldığı için kullanıcı
"puan/bölüm/durum güncellenmiyor" yaşıyordu.
→ **Düzeltme:** kayıt kimliği zaten AniList'ten gelmiş ve bize ait; sahiplik ucuz bir
sorguyla doğrulanır, doğrulanamazsa yazma **engellenmez** (AniList sunucuda zaten doğrular).

**B) `resolveAniListMediaIdForEntry` kimliği gereksiz yere yeniden çözüyordu.**
`knownAniListMediaId` yoksa yerel zincirle (`Media(idMal)` + başlık/yıl katılığı) kimliği
yeniden çözmeye çalışıyor, çözemezse `null` dönüyordu → A'daki iptal.
→ **Düzeltme:** kayıt var + `userId` bizim + `mediaId` tutarlı = yeterli kanıt.

**C) `verifyAniListMediaIdentity`: başlık dili ve boş yıl yazmayı öldürüyordu.**
`idMal` ya da AniList kimliği **birebir eşleştiği hâlde** bir de başlık/yıl katılığı
uygulanıyordu. Yani:
- yerel başlık farklı dildeyse (romaji ↔ İngilizce/Türkçe),
- ya da `entry.year` boşsa (`yearsCompatible` iki tarafın da yılı bilmesini ister)

→ kayıt **reddediliyor**, AniList'e yazma hiç gerçekleşmiyordu.
→ **Düzeltme:** güçlü kimlik kanıtı (idMal/AniList id birebir eşleşmesi) varsa başlık
farkı engel değildir; yalnızca iki taraf da yılı biliyor **ve** fark ≥2 yıl ise reddedilir.
Ayrıca AniList'in `synonyms` (alternatif adlar) alanı başlık havuzuna eklendi.

**D) `CrossSyncIdentityGuard.verifyMalId` (çapraz yazım kapısı):**
`related && yearOk` şartı, yılın iki tarafta da bilinmesini ve **birebir eşit** olmasını
istiyordu. Yerel yılı boş olan veya AniList sezon yılı ile MAL başlangıç yılı 1 yıl kayan
kayıtlar `Unverifiable` dönüyordu → **tüm çapraz yazım atlanıyordu** ("Kimlik doğrulanamadı…").
→ **Düzeltme:** başlık eşleşiyorsa, yıl bilinmiyorsa ya da ±1 yıl kayıyorsa **Verified**.

**E) `deleteAniListEntry`** aynı `error(...)` desenini kullanıyordu → listeden çıkarma da
çalışmıyordu. Artık doğrulama başarısız olsa bile silme denenir (AniList sahipliği
sunucuda doğrular; bize ait olmayan kayıt zaten silinemez).

**F) Favori durumu her düzenlemede ayrı bir istekti** → 10 dk önbelleğe alındı.

**G) OAuth (bağlanma) tarafı:**
- `redirect_uri` AniList yetkilendirme URL'inde **kodlanmamış** gönderiliyordu (MAL/Simkl
  yollarında kodlanıyordu). Artık `URLEncoder.encode(...)` ile gönderiliyor.
- Token takas gövdesi **elle string birleştirmeyle** üretiliyordu; kod/secret içinde özel
  karakter olursa JSON bozuluyor ve gerçek hata görünmüyordu → artık `JSONObject`.
- 400/401 yanıtında kullanıcıya net ipucu gösteriliyor.

---

## 4. Beklenen etki

| Önce | Sonra |
|---|---|
| Listem sürekli boş (aktarım tek 429'da çöküyor) | Aktarım soğumayı bekleyip devam eder; yarım kalsa bile eldeki kayıtlar yazılır |
| Her düzenlemede "istek sınırı doldu" | Düzenleme başına 1-2 istek + doğru hız; kota aşılmaz |
| Bir kez 429 gelince tüm uygulama dakikalarca susar | Soğuma paylaşıldığı için süre uzamaz, bitince otomatik devam |
| 429 → 5 deneme hakkı saniyelerde biter | Hız sınırı hatası hak yakmaz, kuyruk soğuma bitince dener |
| Keşfet/detay rafları sessizce boş | GraphQL 429 tespit edilir, yedek zincire düşer/gerektiğinde yeniden dener |

**Not:** AniList 30 istek/dk'da olduğu için ilk tam aktarım bilinçli olarak
yavaştır (300 kayıt ≈ 8-10 istek ≈ 20 sn). Bu, "bozuk" ile "yavaş ama çalışan"
arasındaki farktır.

---

## 5. Doğrulama

1. AniList'e giriş yap → **Listem → Senkronize Et**: kayıtlar gelmeli, hata alınmamalı.
2. Aynı kayda arka arkaya 5-6 kez "+1 bölüm": hız sınırı uyarısı **çıkmamalı**
   (geliştirici günlüklerinde `AniListGovernor` satırı `limit=30/dk` göstermeli).
3. Uyarı bandı çıkarsa: üzerindeki süre geri sayıp bitince değişiklik **kendiliğinden**
   gönderilmeli (kuyruk hakkı yanmadan).
4. `Geliştirici günlükleri → Platform hataları`: `KTS-ANL-*` kayıtları 429 için
   doğru süreyle düşmeli; "boş hata" kaydı görülmemeli.

---

## 6. İlgili dosyalar

```
app/src/main/java/com/kitsugi/animelist/data/auth/AniListGovernor.kt        (yeni)
app/src/main/java/com/kitsugi/animelist/data/auth/AniListImportManager.kt
app/src/main/java/com/kitsugi/animelist/data/auth/AniListSyncManager.kt
app/src/main/java/com/kitsugi/animelist/data/auth/PlatformRateLimiter.kt
app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiApiBase.kt
app/src/main/java/com/kitsugi/animelist/data/local/PendingSyncDrainer.kt
app/src/main/java/com/kitsugi/animelist/KitsugiApplication.kt
app/src/main/java/com/kitsugi/animelist/ui/app/AuthViewModel.kt
app/src/main/java/com/kitsugi/animelist/ui/app/KitsugiProfileViewModel.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/profile/KitsugiUserProfileViewModel.kt
app/src/main/java/com/kitsugi/animelist/data/notifications/NotificationDiagnostics.kt
app/src/main/res/values/strings.xml · values-en/strings.xml  (account_sync_import_partial)
```
