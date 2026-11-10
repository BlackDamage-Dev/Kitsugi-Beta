# Liste Yenileme Mimarisi — Kök Neden Analizi ve Düzeltme Raporu
**Tarih:** 2026-10-23 · **Sürüm:** v2.4.341 · **Kapsam:** Listem (Tümü + 6 platform sekmesi), arka plan otomatik yenileme, ayarlardaki içe aktarma, bildirim işçisi, çapraz senkron doğrulama turu

---

## 1. Şikâyetin çözümlenmesi

> "Artık elle de Listem sayfasını yenilesek, kendi kendine otomatik yenilense de veriler baştan sona tamamı yenilenmemeli. Verilerin her kaynağa özgü şekilde, uygun şekilde yenilenmesi lazım. Sürekli tak diye baştan tüm veriyi çekip durması doğru mu? Uygulamamın desteklediği tüm kaynakların ve bu kaynakları kullanan açık kaynak uygulamaların bu yenilemeleri nasıl ele aldığına bakman lazım."

Üç ayrı iddia var, üçü de kodda doğrulandı:

| # | İddia | Kodda karşılığı |
|---|---|---|
| A | Her yenilemede **listenin tamamı** ağdan çekiliyor | `importAniListAnimeList` → `AniListImportManager.fetchAllLists()` → tüm sayfalar (50'lik dilimler) |
| B | Her yenilemede **veritabanı baştan yazılıyor** | `MediaEntryRepository.smartImport()` eşleşen **her** satırı `updates` listesine koyuyordu (içerik aynı olsa bile) |
| C | Yenileme **kaynak bazlı değil**, global | `KitsugiApplication` içinde tek bir `kitsugi_auto_sync.last_list_sync_ms` damgası; yalnızca AniList/MAL/Simkl |

---

## 2. Kök nedenler (öncesi → sonrası)

### 2.1. `smartImport`: "tek transaction" yeterli değildi

Eski kod:

```kotlin
for (entry in importedEntries) {
    ...
    updates[match.id] = preserveResolvedTitles(match, imported).copy(...).toEntity()
}
dao.smartImportTransaction(inserts, updates.values.toList(), deletions)
```

Eşleşme bulunduğu anda satır **koşulsuz** `updates`'e ekleniyordu. Yani:

1. Uzak liste hiç değişmemiş olsa bile N satır `updateAll` ile yeniden yazılıyor,
2. Room `media_entries` tablosu için invalidation yayıyor,
3. `MediaEntryRepository.entriesFlow` yeni bir `List<MediaEntry>` üretiyor,
4. `MyListScreen` içindeki `produceState` **birleşik kütüphaneyi** (kimlik köprüsü + union-find gruplama + rozet haritaları) 1000+ satırda **baştan** hesaplıyor,
5. Liste yeniden çiziliyor → "tak diye baştan yükleniyor" hissi.

**Düzeltme:** satırlar `updatedAt` dışındaki tüm alanlarda karşılaştırılıyor (`samePayloadAs`). Fark yoksa yazım yok, transaction yok, emisyon yok.

```kotlin
val candidate = preserveResolvedTitles(match, imported).copy(...).toEntity()
if (candidate.samePayloadAs(match)) unchanged++ else updates[match.id] = candidate
...
if (inserts.isNotEmpty() || updates.isNotEmpty() || deletions.isNotEmpty()) { /* transaction */ }
```

Ayrıca **bekleyen yerel düzenlemeler korunuyor**: çevrimdışı yapılmış ve kuyrukta (`pending_syncs`) bekleyen bir satır, liste yenilenirken uzak veriyle ezilmiyor (outbox / pending-safe reconciliation).

### 2.2. Arka plan yenilemesi "sil-yeniden-yaz" yapıyordu

`KitsugiApplication` (eski):

```kotlin
val entries = AniListImportManager.fetchAllLists(aniListToken)
repo.replaceSourcePreservingAdultFlags("anilist", entries)   // deleteBySource + insertAll
```

`replaceSourcePreservingAdultFlags` kaynağın **tüm satırlarını silip** yeni id'lerle geri yazıyordu. Sonuçları:

- Room iki kez invalidation (sil + ekle) → liste bir-iki kare boşalıp doluyor,
- satır id'leri değişiyor → bekleyen senkron işleri, kaydırma konumu ve seçim referansları anlamsızlaşıyor,
- okuma sırasında kütüphane yarım görünüyor.

**Düzeltme:** `mergeSourcePreservingAdultFlags()` — fark-tabanlı birleştirme, silme yok, +18 işaretleri korunuyor. `AiringNotificationWorker` da bu yola taşındı.

### 2.3. Tazelik "global"di

Tek bir `last_list_sync_ms` vardı:

- AniList'i elle yenilemek MAL/Simkl'in de "taze" görünmesine yol açmıyordu ama **tek global damga** üç kaynağı birlikte yönetiyordu,
- Kitsu, Shikimori ve Bangumi **hiç** otomatik yenilenmiyordu (bağlı olsalar bile),
- her yenilemede liste tamamen çekiliyordu.

**Düzeltme:** `ListSyncStateStore` — kaynak başına `lastSuccessAt`, `lastFullSyncAt`, `lastAttemptAt`, `failureCount`, `remoteRevision`, `lastError`.

### 2.4. "Tümü"yü yenile = 6 paralel tam çekim

`MyListScreen` içindeki "Tümü" sekmesi yenilemesi altı `onSyncX()` çağrısını **aynı anda** başlatıyordu: altı bağımsız sayfalama döngüsü, altı tam liste, altı yazım dalgası → ağ doygunluğu + art arda yeniden çizim.

**Düzeltme:** `AuthViewModel.syncAllLists()` — kaynaklar **sırayla**, aralarında 700 ms (arka planda 500 ms) bekleme ile; her kaynak kendi TTL'ine göre `atla / delta / tam` kararını veriyor.

---

## 3. Açık kaynak uygulamalar bunu nasıl yapıyor?

### Mihon / Tachiyomi (kitaplık güncellemesi)
- Kaynak başına **sıralı** güncelleme (aynı kaynağa paralel istek yok — oran sınırına saygı).
- `lastUpdated` / `lastUpdatedTimestamp` ve **güncelleme aralığı** tercihi; aralık dolmadan ağa çıkılmaz.
- "Smart update" kuralları: okunmamış bölümü olanları, başlamamışları, tamamlanmışları ve yayın penceresi dışındakileri **atla** — sunucuyu gereksiz dövmemek için.
- Not: Tachiyomi v0.13.1'de 12 saatten kısa global aralıklar kaldırıldı; varsayılan "yalnızca devam eden + tamamen yakalanmış" başlıklar güncelleniyor.
- Kaynaklar: [Mihon — Library Updates and Scheduled Jobs](https://deepwiki.com/mihonapp/mihon/10.1-library-updates-and-scheduled-jobs), [Mihon — Library FAQ](https://mihon.app/docs/faq/library), [Tachiyomi v0.13.1 sürüm notları](https://newreleases.io/project/github/tachiyomiorg/tachiyomi/release/v0.13.1)

### Kotatsu
- Kaynak başına TTL ve kaynaklar arasında **gecikmeli (staggered)** güncelleme; tüm kaynaklar aynı anda dövülmez.

### AniList istemcileri (AniHyou / MoeList / Shouze)
- Liste yerelde (Room) tutulur; ağ yalnızca tazelik gerektiğinde devreye girer.
- **Outbox deseni:** çevrimdışı düzenlemeler kuyruğa girer, bağlantı gelince gönderilir; sunucudan çekim sırasında **gönderilmemiş düzenlemeler sunucu verisiyle ezilmez** (pending-safe reconciliation).
- Kaynak: [Shouze — offline-first AniList tracker](https://github.com/ThatOn3Gu7/Shouze)

### AniList API'sinin sunduğu imkân
- `Page.mediaList(userId, type, updatedAt_greater: Int)` → **değişenleri** çekme.
- `MediaListCollection` → tam liste (sayfa sayfa değil, tek yapıda).
- Kaynak: [AniList Docs — Media List](https://docs.anilist.co/guide/graphql/queries/media-list), [Pagination](https://docs.anilist.co/guide/graphql/pagination)

### Kaynak yetenek matrisi (uygulamada kullanılan)

| Kaynak | Delta / değişiklik tespiti | Uygulamadaki yenileme stratejisi |
|---|---|---|
| **AniList** | `updatedAt_greater` süzgeci | Delta (uç reddederse sessizce tam listeye düşer) + günde bir tam uzlaştırma |
| **Simkl** | `GET /sync/activities` (ucuz revizyon imzası) | İmza aynıysa liste hiç indirilmez; değiştiyse tam liste |
| **MyAnimeList** | Liste uçlarında "değişenler" parametresi yok | TTL (arka planda 6 saat) + fark-tabanlı yazım |
| **Kitsu** | `filter[since]=<ISO-8601>` (yalnızca o tarihten sonra **değişen** kayıtlar) | Delta + günde bir tam uzlaştırma (delta silmeleri göremez) |
| **Shikimori** | Yok — `/api/v2/user_rates` süzgeçleri: `user_id`, `target_id`, `target_type`, `status`, `page`; `updated_at` sıralaması/süzgeci upstream'de yok ("someday") | TTL (arka planda 6 saat) + fark-tabanlı yazım |
| **Bangumi** | Yok — `/users/:user/collections` sayfalı, değişiklik parametresi yok | TTL (arka planda 6 saat) + fark-tabanlı yazım |

---

## 4. Uygulanan mimari

### Yeni dosyalar

| Dosya | Görev |
|---|---|
| `data/sync/ListSyncStateStore.kt` | Kaynak başına yenileme defteri (SharedPreferences + JSON; Room şeması büyümedi) |
| `data/sync/ListRefreshPolicy.kt` | **Saf** karar fonksiyonu: `SKIP_UP_TO_DATE` / `DELTA` / `FULL` |
| `data/sync/SourceListSynchronizer.kt` | Tek giriş noktası: single-flight kilit, revizyon kontrolü, delta→tam düşüş, fark-tabanlı yazım, defter kaydı |
| `data/sync/ImportReconciler.kt` | **Saf** silme kuralları: sunucu kökeni, yarım yanıt koruması, +18 koruması, diriliş (tombstone) engeli |
| `data/sync/ServerSnapshotStore.kt` | "Sunucuda en son ne vardı?" kimlik defteri (SharedPreferences `StringSet`); silme yalnızca burada görülmüş satırlara uygulanır |

### Değişen dosyalar

| Dosya | Değişiklik |
|---|---|
| `data/local/MediaEntryRepository.kt` | `smartImport` → `ImportStats` döner, fark-tabanlı, bekleyen düzenlemeleri korur; **silme artık üç kapıdan geçer** (`keepAdultRows`, `guardAgainstPartialPayload`, `isFullPull`); boş yanıtta kütüphane silinmez; `mergeSourcePreservingAdultFlags` eklendi, sil-yeniden-yaz kaldırıldı |
| `data/auth/AniListImportManager.kt` | `fetchListsSince(...)` (delta) + `mediaList(updatedAt_greater:)` |
| `data/auth/SimklImportManager.kt` | `fetchActivitiesRevision(...)` (ucuz değişiklik imzası) |
| `data/auth/KitsuApiClient.kt` | `fetchLibraryEntries(sinceIso:)` + `fetchLibraryEntriesSince(...)`: `filter[since]` deltası |
| `data/auth/KitsuImportManager.kt` | `fetchAllListsSince(...)` (ISO-8601 biçimleme) — Kitsu artık gerçek delta kullanıyor |
| `ui/app/AuthViewModel.kt` | 6 içe aktarma fonksiyonu ortak çekirdeğe bağlandı; `syncAllLists()` (staggered); `ListRefreshMode` parametresi; hesap değişince defter sıfırlama |
| `KitsugiApplication.kt` | Global 24 saatlik blok → kaynak başına politika (6 kaynak, sıralı) |
| `ui/screens/mylist/MyListScreen.kt` + `MyListComponents.kt` | "Tümü" yenilemesi tek akış; pull-to-refresh göstergesi gerçek senkron durumuna bağlı |
| `AppRootTabPages.kt` / `AppRootSettingsExtras.kt` / `AppRoot.kt` | `onSyncAll`, sekme→kaynak senkron durumu haritası; ayarlardaki "içe aktar" ve giriş sonrası aktarım **tam** senkron |
| `core/notifications/AiringNotificationWorker.kt` | Simkl arka plan senkronu fark-tabanlı birleştirmeye geçti |

### Politika eşikleri (`ListRefreshPolicy`)

| Sabit | Değer | Anlamı |
|---|---|---|
| `USER_FRESH_WINDOW_MS` | 45 sn | Elle yenilemede bundan yeni veri varsa ağa çıkılmaz |
| `BACKGROUND_FRESH_WINDOW_MS` | 60 dk | Delta destekleyen kaynaklarda arka plan tazelik penceresi |
| `BACKGROUND_FULL_ONLY_WINDOW_MS` | 6 saat | **Tam çekim gerektiren** kaynaklarda arka plan penceresi |
| `DELTA_MAX_FULL_AGE_MS` | 24 saat | Son tam senkron bundan eskiyse delta yerine tam çekim |
| `DELTA_MAX_AGE_MS` | 6 saat | Son başarılı senkron çok eskiyse delta yerine tam çekim |
| `MAX_CONSECUTIVE_FAILURES` | 3 | Arka planda art arda hata sonrası geri çekilme |
| `FAILURE_BACKOFF_MS` | 15 dk | Geri çekilme süresi |

### Davranış matrisi

| Kullanıcı eylemi | AniList | Simkl | Kitsu | MAL / Shikimori / Bangumi |
|---|---|---|---|---|
| Sekme yenile (45 sn içinde tekrar) | ağ yok → "az önce yenilendi" | ağ yok | ağ yok | ağ yok |
| Sekme yenile | delta (`updatedAt_greater`) | activities imzası; aynıysa ağ yok | delta (`filter[since]`) | tam liste (fark yazımı) |
| "Tümü"yü yenile | sırayla, her kaynak kendi kararıyla | ← | ← |
| Uygulama açılışı | 1 saatten yeniyse atla, sonra delta | imza kontrolü | 1 saatten yeniyse atla, sonra delta | 6 saatten yeniyse atla |
| Ayarlar → "listeyi içe aktar" / yeni giriş | **tam** senkron | **tam** | **tam** |

---

## 5. Kazanç (tahmini)

1000+ kayıtlı, 6 kaynağı bağlı bir kütüphane için:

- **Ağ:** AniList'te bir bölüm işaretlemesi sonrası ~N/50 sayfa yerine 1 sayfalık delta; Simkl'de değişiklik yoksa 1 küçük `activities` isteği.
- **Veritabanı:** no-op yenilemede **0** yazım (eskiden ~N satır `updateAll`).
- **UI:** no-op yenilemede Room emisyonu yok → birleşik kütüphane hesabı ve grid yeniden çizimi yok. Değişiklik varsa yalnızca etkilenen satırlar yazıldığı için tek emisyon.
- **Arka plan:** altı kaynak aynı anda değil, sırayla; tam çekim gerektiren kaynaklar günde en fazla birkaç kez.

---

## 5.5. "Listeye bir şey ekledim / çıkardım — ne olacak?" (senaryo matrisi)

Yenileme iki yönlü bir uzlaştırmadır ve **ekleme** ile **silme** simetrik değildir:

- Ekleme/değişiklik **görülebilir**: kayıt yanıtta vardır (`updatedAt`/`since` süzgeciyle ya da tam listede).
- Silme **görülemez**: delta yanıtı yalnızca hâlâ var olan satırları içerir. "Yanıtta yok" tek başına
  "sunucudan silinmiş" demek değildir (yerelde eklenmiş, gönderilememiş, filtrelenmiş ya da yarım
  gelmiş olabilir).

Bu yüzden silme üç bağımsız kapıdan geçer (`ImportReconciler` + `ServerSnapshotStore`):

| Kapı | Kural | Ne engeller |
|---|---|---|
| 1. **Sunucu kökeni** | Satır, geçmiş bir **tam** çekimde sunucuda görülmüş olmalı (`snapshot_<kaynak>` defteri) | Yerelde eklenip sunucuya henüz gitmemiş / gönderimi "atlandı" kayıtların silinmesi |
| 2. **Bekleyen iş** | Satırın bekleyen `UPDATE`'i varsa ne ezilir ne silinir | Çevrimdışı yapılan düzenlemenin kaybı |
| 3. **Tam yanıt** | Çekilen satır sayısı yerelin %30'undan azsa silme yapılmaz (+ MAL `nsfw=false` iken +18 satırlar aday değil) | Yarım/kesik yanıt, kasıtlı süzgeç kaynaklı toplu silme |

Delta turunda **hiç silme yapılmaz** (`allowDelete && !usedDelta`); silmeler günde bir yapılan tam
uzlaştırmada yakalanır.

### Senaryolar

| # | Senaryo | Ne olur | Ne kadar sürer |
|---|---|---|---|
| 1 | Telefondan **yeni kayıt** ekledim (çevrimiçi) | Anında yazılır, uzaklara gönderilir; sonraki yenilemede sunucu da döndürür → `aynı` sayılır, silinmez | anında |
| 2 | Telefondan ekledim ama **çevrimdışıyım** | `UPDATE` kuyruğa girer; yenileme bu satırı **ezmez ve silmez** (kapı 2); bağlantı gelince gönderilir | bağlantı gelince |
| 3 | Gönderim **"atlandı"** (kimlik çözülemedi) | Sunucu listesinde görünmediği için kapı 1 devreye girer → silinmez; kimlik çözülünce gönderilir | — |
| 4 | Web'de / başka uygulamada **yeni kayıt** ekledim | AniList: `updatedAt_greater`, Kitsu: `filter[since]`, Simkl: activities imzası → **1 küçük istek**; MAL/Shikimori/Bangumi: tam liste (yalnızca yeni satır yazılır) | ≤1 saat (delta) / ≤6 saat |
| 5 | **İlerleme/puan** değiştirdim ve çevrimdışıyım, sonra yenileme oldu | Yerel değer korunur (kapı 2), `+n bekleyen düzenleme korundu` mesajı çıkar | — |
| 6 | Telefondan **sildim** (çevrimiçi) | Satır + kardeş kaynak kopyaları yerelden gider, tüm kaynaklara DELETE gider | anında |
| 7 | Telefondan sildim ama **çevrimdışıyım** | Satır gider, `DELETE` kuyruğa girer. Liste yenilenirse sunucu kaydı hâlâ döner → **diriliş koruması** (tombstone) geri eklemez. Tombstone'lar **çapraz sağlayıcı** anahtarlarla üretilir: katalog satırı (`source=jikan`) silinse bile AniList/MAL kopyası geri gelmez | — |
| 8 | **Web'de sildim** | Delta turunda görülmez (yok sayılır); **günde bir tam uzlaştırmada** silinir (kapı 1+3) | ≤24 saat |
| 9 | Sunucu **boş/hatalı** yanıt verdi (401, kota, 5xx) | Boş yanıt **hiçbir koşulda** kütüphaneyi silmez; tur "⚠️ sunucu boş liste döndürdü, yerel liste korundu" diye bildirilir ve deftere başarı yazılmaz (6 saatlik pencere bunu önbelleklemez) | — |
| 10 | MAL'da `nsfw=false`, +18 kayıtlarım var | Bu satırlar yanıtta yok ama **silme adayı değil** | — |
| 11 | Kaynağın bağlantısını kestim | O kaynağın defteri **ve** sunucu anlık görüntüsü sıfırlanır; diğer kaynaklar etkilenmez | — |
| 12 | Aynı yapım 3 kaynakta; birinden sildim | Kardeş satırlar da yerelden silinir ve diğer kaynaklara da DELETE gider (hayalet satır kalmaz) | anında |
| 13 | "Baştan yenile" dedim | Tam çekim + silme; yalnızca **kapı 3** atlanır (niyet net). Kapı 1 ve 2 **yine geçerli** | — |

### Bilinen ve kabul edilen gecikmeler

- **Web'den silme** en geç 24 saatte yansır (delta silmeyi göremez). Bu bilinçli bir tercih:
  her yenilemede "eşleşmeyen = sil" demek, yarım yanıtta kütüphane kaybı riskidir.
- **Kitsu/AniList delta turunda** silme yok; `filter[since]` yalnızca değişenleri döndürür.
- Uygulama yeni kurulduysa **ilk tam çekime kadar** hiçbir satır silinmez (defter boş).

---

## 5.6. Ek tur: yarış durumları ve "akla gelmeyen" kenar durumlar

Senaryo matrisi çıktıktan sonra kalan riskler tek tek kapatıldı. Hepsi aynı sınıftandı:
**iki şey aynı anda olurken** veri kaybı ya da kalıcı bayatlık.

| # | Risk | Neden oluyordu | Kapatıldığı yer |
|---|---|---|---|
| 1 | **Yenileme sırasında yapılan düzenleme kayboluyordu** | Import, satırları turun başında okuyup saniyeler sonra yazıyor; arada girilen ilerleme/puan bayat kopyayla eziliyordu | `smartImport`: `refreshStartedAtSec` — tur başladıktan sonra değişen satır atlanır (`deferred`) |
| 2 | **Çift kayıt** | Aynı kaynak için iki import eşzamanlı: sekme yenileme, çapraz eşitleme doğrulaması, bildirim işçisi, açılış senkronu. İkisi de "yerelde yok" sanıp ekliyordu | `MediaEntryRepository.importMutex` — karşılaştırma+yazma bloğu serileşti |
| 3 | **Silinen kayıt import sürerken geri geliyordu** | Tombstone'lar turun başında okunuyordu; tur sırasında yapılan silme görülmüyordu | Yazma anında tombstone'lar tazelenir, o kayıtlar eklenmez |
| 4 | **Saat kayması → liste sonsuza kadar "taze"** | Cihaz saati geri alınırsa `now - lastSuccessAt` negatif olur, tazelik penceresi hiç dolmaz | `ListRefreshPolicy.CLOCK_SKEW_TOLERANCE_MS` (5 dk) — damga gelecekteyse tam çekim |
| 5 | **Kütüphane boşken "değişiklik yok" denmesi** | Simkl `activities` imzası aynı kaldığında liste hiç indirilmiyordu; yedek geri yükleme/temizlemeden sonra yerel boş kalıyordu | `countForSource(source) == 0` ise tam çekim zorunlu; imza kontrolü atlanır |
| 6 | **Temizleme / yedek geri yükleme defteri kirletiyordu** | Tazelik defteri ve "sunucuda ne vardı" anlık görüntüsü eski kütüphaneye aitti | `deleteAll()` / `replaceAll()` → `SourceListSynchronizer.resetState(ctx, null)` |
| 7 | **"Sildim, sonra tekrar ekledim" → uzaktan siliniyordu** | Yeniden eklenen kayıt **yeni yerel id** alır; kuyrukta kalan eski `DELETE` işi id eşleşmesine takılmaz | `PendingSyncDrainer.enqueue`: `UPDATE` yazılırken aynı yapımı hedefleyen bekleyen `DELETE` işleri düşürülür (`sameProduction`: tür + ortak kimlik, yoksa normalize başlık + yıl) |
| 8 | **Her yenilemede tüm kütüphane okunuyordu** | `getAll()` + Kotlin süzgeci (1000+ satır) her turda kopyalanıyordu | Yeni DAO sorguları `getBySource` / `countBySource` (şema değişikliği yok, migration gerekmez) |

### Bilinen sınırlar (bilinçli tercih, hata değil)

1. **Web'de silme en geç 24 saatte yansır.** Delta yanıtı silinenleri göremez; her turda
   "eşleşmeyen = sil" demek yarım yanıtta kütüphane kaybı riskidir. Silme yalnızca günlük
   tam uzlaştırmada ve üç kapıdan geçtikten sonra yapılır.
2. **Tek kaynakta eklenen kayıt diğerlerine otomatik yazılmaz.** Uygulamada bunun için
   ayrı bir **çapraz eşitleme** akışı var; liste yenilemesi kullanıcının diğer hesaplarına
   kendiliğinden yazmaz (istenmeyen toplu yazımı önlemek için).
3. **Bekleyen yerel düzenleme uzak silmeden üstündür.** Çevrimdışıyken ilerleme girip aynı
   kaydı web'den silersen, kuyruk boşaldığında kayıt uzakta yeniden oluşur (outbox semantiği).
4. **Çok cihaz: son yazan kazanır.** Alan bazlı birleştirme yok; cihaz A'nın bekleyen
   düzenlemesi, cihaz B'nin aynı kayıttaki yeni değerini ezebilir.
5. **Uygulama yeni kurulduysa ilk tam çekime kadar hiçbir satır silinmez** (defter boş).

---

## 6. Doğrulama

### Birim testleri (saf JVM)
```
app/src/test/java/com/kitsugi/animelist/data/sync/ListRefreshPolicyTest.kt
app/src/test/java/com/kitsugi/animelist/data/sync/ImportReconcilerTest.kt
app/src/test/java/com/kitsugi/animelist/data/local/MediaEntryPayloadDiffTest.kt
```
- Tazelik penceresi, delta/tam kararı, tam uzlaştırma periyodu, hata geri çekilmesi, `force` davranışı, delta başlangıç damgası, defter JSON gidiş-dönüşü.
- `updatedAt` dışındaki alanlar aynıysa satır "değişmedi" sayılır; bölüm/puan/durum/başlık değişimi yakalanır.

Çalıştırma: `./gradlew :app:testFossDebugUnitTest --tests "*ListRefreshPolicyTest" --tests "*MediaEntryPayloadDiffTest"`

### Elle doğrulama (logcat)
```
adb logcat -s KitsugiAutoSync SourceListSynchronizer
```
- Açılış: `anilist: +0 yeni, ~0 güncellendi, 812 aynı (delta=true, ... ms)`
- Simkl değişiklik yoksa: `simkl atlandı: unchanged`
- İkinci kez yenile: `anilist atlandı: fresh`
- Ağ trafiği (Charles/HttpToolkit): yenileme sonrası AniList'te yalnızca `updatedAt_greater` sorguları görünür.

### Regresyon kontrol listesi
1. Listem'de sekme değiştir → liste boşalmıyor, kaydırma konumu korunuyor.
2. Detay sayfasına gir-çık → birleşik kütüphane yeniden hesaplanmıyor.
3. Çevrimdışı bölüm işaretle → bağlantı gelince kuyruk gönderiliyor; bu sırada liste yenilemesi yerel değeri **ezmiyor**.
4. Hesap değiştir (çıkış → farklı hesapla giriş) → liste tam çekiliyor (defter sıfırlandı).
5. Bangumi yenilemesi sonrası başlıklar hâlâ Latin/İngilizce (CJK'ya dönmüyor).

---

## 7. Bilinçli olarak yapılmayanlar

- **Otomatik silme:** Sunucudan kaldırılan kayıtlar, liste uçları eksik/yarım dönebildiği için varsayılan olarak yerelden **silinmez** (`allowDelete = false`). Bu, ağ hatasında kütüphanenin silinmesini engeller; silme yalnızca kullanıcı eylemleriyle olur. (İleride "tam turda sil" seçeneği politika sabitiyle açılabilir.)
- **Delta silmeleri göremez:** Bu yüzden tam uzlaştırma 24 saatte bir korunuyor.
- **Room şeması değişmedi:** Senkron defteri SharedPreferences'ta; migration riski yok.

## 8. Geri alma

Tek geri dönüş noktası politika eşikleridir: `ListRefreshPolicy` sabitlerini büyütmek (ör. `BACKGROUND_FULL_ONLY_WINDOW_MS` → 24 saat) davranışı eski "günde bir" ritmine yaklaştırır. `ListRefreshMode.USER_FORCE` her durumda tam listeyi çeker; acil durumda "Ayarlar → hesap → içe aktar" bu modu kullanır. Defteri sıfırlamak için `SourceListSynchronizer.resetState(context, source)` yeterlidir.
