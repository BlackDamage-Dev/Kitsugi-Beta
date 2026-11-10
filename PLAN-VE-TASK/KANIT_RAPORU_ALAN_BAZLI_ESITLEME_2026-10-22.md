# Kanıta Dayalı Alan Bazlı Çapraz Eşitleme Raporu

**Tarih:** 22 Ekim 2026
**Dal:** `arena/38590839-kitsugi-beta`
**Commit:** `a331b99` — *"alan bazlı çapraz eşitleme: yalnızca değişen alanlar hedeflere yazılır"*
**Kapsam:** AniList, MyAnimeList, Kitsu, Shikimori, Bangumi, Simkl (TMDB kimlik kaynağıdır, yazma ucu yoktur)

---

## 0. Kısa özet

Önceki turlarda altı kaynağa **eşzamanlı fan-out** kurulmuştu (`ExternalListSyncManager`, 6 × `async` + 30 sn timeout). Bu turda kapatılan asıl açık farklıydı ve altı kaynağın **hepsini** ilgilendiriyordu:

> Uygulama, her eşitlemede satırın **tamamını** hedefe yazıyordu. Yerel satırda puan/not/tarih boşken bu, hedefteki değerleri **siliyor ya da eziyordu**. Aynı nedenle, yalnızca ilerleme değiştiğinde bile Kitsu'daki not, MAL'daki puan, Bangumi'deki etiket yeniden yazılıyordu.

Bu turda eşitleme **alan farkı (diff)** üzerine taşındı: `SyncFieldDiff` yalnızca gerçekten değişen alanları üretir, `ProviderCapabilitiesCatalog` her hedefin hangi alanı desteklediğini tanımlar, `ExternalListSyncManager` her sağlayıcıya yalnızca kendi payına düşen alanları verir. Hiçbir alan değişmediyse istek **atılmaz** ve sonuç arayüzde `değişiklik yok` olarak görünür (sessiz atlama yok).

Bunun yanında, kod okumasında çıkan **beş somut veri kaybı hatası** kapatıldı (bölüm 2).

---

## 1. Yöntem: neye dayanarak?

### 1.1 Resmî API sözleşmeleri

| Sağlayıcı | İncelenen sözleşme | Kritik sonuç |
|---|---|---|
| AniList | `SaveMediaListEntry` mutation ([docs.anilist.co/reference/mutation](https://docs.anilist.co/reference/mutation)), `MediaListStatus` | Alanlar: `score`/`scoreRaw`, `progress`, `progressVolumes`, `repeat`, `priority`, `private`, `notes` (≤6000), `startedAt`/`completedAt` = `FuzzyDateInput`. `id` = liste kaydı, `mediaId` = medya. |
| MAL | API v2 `PATCH /{anime\|manga}/{id}/my_list_status` ([myanimelist.net/apiconfig/references/api/v2](https://myanimelist.net/apiconfig/references/api/v2)) | `status`, `score` 0–10 **"0 = puanı sil"**, `num_watched_episodes`/`num_chapters_read`, `num_volumes_read`, `is_rewatching`/`is_rereading`, `num_times_rewatched`/`num_times_reread`, `rewatch_value` 0–5, `priority` 0–2, `tags`, `comments`, `start_date`/`finish_date` (`YYYY-MM-DD`). **Favori ucu yok.** |
| Kitsu | JSON:API `library-entries` ([kitsu.app/api/edge](https://kitsu.app/api/edge/library-entries)) | `ratingTwenty` (2–20), `status` (current/planned/completed/on_hold/dropped), `progress`, `reconsuming`, `reconsumeCount`, `startedAt`/`finishedAt` (ISO), `notes`, `private`. |
| Shikimori | API v2 `user_rates` ([shikimori.one/api/doc/2.0](https://shikimori.one/api/doc/2.0)) | `status` (planned/watching/rewatching/completed/on_hold/dropped), `score` 0–10, `episodes`/`chapters`, `volumes`, `rewatches`, `text`. Yazma scope'u `user_rates`. |
| Bangumi | API v0 `POST/PATCH /v0/users/-/collections/{subject_id}` ([bangumi.github.io/api](https://bangumi.github.io/api/)) | `type` 1–5, `rate` 0–10, `ep_status`, `vol_status`, `comment` (≤380), `private`, `tags` (≤10, her etiket ≥2 karakter, boşluksuz). |
| Simkl | `POST /sync/add-to-list`, `/sync/history`, `/sync/ratings` ([api.simkl.org](https://api.simkl.org/)) | İzinli `to` değerleri: **`watching`, `plantowatch`, `completed`, `notinteresting`, `hold`** — `dropped` **geçersiz**. Tarih, cilt, tekrar sayısı yok. Manga yok. |

### 1.2 Açık kaynak uygulamaların kodu (davranış referansı)

Klone edilip okundu (`/tmp/aniyomi`, `/tmp/malsync` — kalıcı değil, yeniden klonlanabilir):

- **MAL-Sync / MALSync** — `src/_provider/SingleAbstract.ts` (`getStateDiff`, `isValueDirty`), `_provider/AniList/single.ts`, `_provider/MyAnimeList_api/single.ts`, `_provider/Kitsu/single.ts` + `helper.ts`, `_provider/Simkl/single.ts` + `helper.ts`, `_provider/Shikimori/helper.ts`.
  - **Kilit bulgu:** MAL-Sync her yazmada yalnızca *kirli* alanı gönderir (`isValueDirty`); gönderilmeyen alan hedefte değişmez. Bizim eski davranışımız bunun tam tersiydi.
  - `SingleAbstract.fixDates()`: başlangıç boş + durum "izleniyor" + ilerleme > 0 → bugün; bitiş boş + durum "tamamlandı" → bugün.
- **Aniyomi** — `app/src/main/java/eu/kanade/tachiyomi/data/track/{anilist,myanimelist,kitsu,shikimori,bangumi,simkl}/`.
  - `Tracker.kt` yetenek bayrakları: `supportsReadingDates`, `supportsPrivateTracking`.
  - `myanimelist/MyAnimeList.kt`: "Rewatching" **ayrı durum değildir** → `status = watching` + `is_rewatching = true`.

---

## 2. Bulunan somut hatalar (kod kanıtlı)

Aşağıdaki satır numaraları **düzeltme öncesi** `HEAD = 1d33889` durumuna aittir.

### H1 — MAL'da puan/öncelik her eşitlemede sıfırlanıyordu
`MalSyncManager.kt` (eski hâli, `updateMalEntry` gövdesi):

```kotlin
append("&score=${SyncScores.tenPoint(entry.score) ?: 0}")   // entry.score == null → "score=0"
append("&priority=${entry.priority ?: 0}")                  // entry.priority == null → "priority=0"
```

MAL sözleşmesi: *"Set this value to 0 to remove the user's rating."*
Yani **Kitsu'dan yapılan bir ilerleme güncellemesi, MAL'daki puanı siliyordu** — kullanıcının şikâyet ettiği "verilerim kayboluyor" davranışının doğrudan kaynağı.

### H2 — Kitsu'da puan her güncellemede siliniyordu
`KitsuApiClient.updateLibraryEntryDetailed` (eski):

```kotlin
if (ratingTwenty != null && ratingTwenty > 0) put("ratingTwenty", ratingTwenty)
else put("ratingTwenty", JSONObject.NULL)     // ← puan YOKSA da null yazılıyor
```

`null`, "puanı kaldır" demektir. Yerel satırda puan yoksa (ör. AniList'ten eklenmiş, puanı olmayan kayıt) Kitsu'daki puan **her eşitlemede** siliniyordu.

### H3 — Kitsu'ya tekrar (rewatch) bilgisi hiç yazılmıyordu
`KitsuEntryExtras` yalnızca `startedAt, finishedAt, notes, isPrivate` taşıyordu. `reconsuming` / `reconsumeCount` **hiç gönderilmiyordu**; yani "Repeating" durumu Kitsu'da düz "current" olarak kalıyor, tekrar sayısı kayboluyordu. MAL-Sync `_provider/Kitsu/single.ts` bu iki alanı açıkça yazar.

### H4 — Shikimori PATCH yolu not/tekrar/cildi düşürüyordu
- `ShikimoriApiClient.createUserRate` (satır 829): `text`, `rewatches`, `volumes` **var**.
- `ShikimoriApiClient.updateUserRate` (satır ~878, eski): yalnızca `status`, `score`, `episodes/chapters` — **`text`, `rewatches`, `volumes` yok**.

Sonuç: kayıt bir kez oluştuktan sonra not/tekrar/cilt güncellemeleri Shikimori'ye hiç ulaşmıyordu.

### H5 — Simkl'e bırakılan kayıtlar geçersiz değerle gönderiliyordu
`SimklSyncManager.watchStatusToSimkl` (eski):

```kotlin
WatchStatus.Dropped -> "dropped"     // Simkl'de GEÇERSİZ
```

Simkl `to` alanı `notinteresting` bekler. Aynı hata `SimklSyncRepository.kt:316` içindeki ikinci kopyada da vardı. Yani **Simkl'e hiçbir zaman "bırakıldı" olarak yazılamıyordu**; istek reddediliyor, kayıt hiçbir listede görünmüyordu.

### H6 — AniList tarih temizleme sessizce çalışıyordu
`AniListSyncManager` eski davranışı: boş tarih → `{year:0, month:0, day:0}` gönderilmez, fakat `scoreRaw: null` **açıkça** gönderilirse AniList 400 döner (`"The score raw must be an integer."` — seanime #985, 17.10.2026). Ayrıca her eşitlemede `status/progress/notes/repeat` yeniden yazıldığı için AniList web'inde yapılan düzenlemeler eziliyordu.

> Not: `scoreRaw: null` gönderimi bu depoda zaten yoktu; risk **alan atlama** mantığına geçerken null göndermeye kaymaktı. Yeni kod bu yüzden `scoreRaw` için "asla null gönderme" kuralını yorumla sabitliyor.

---

## 3. Yeni mimari: alan farkı → yetenek süzgeci → eşzamanlı fan-out

```
MediaEntryRepository.update()
        │
        ├── previous = dao.getById(entry.id)          ← DB'deki ÖNCEKİ hâl
        ├── changed  = SyncFieldDiff.between(previous, current)   ← yalnız değişenler
        │
        └── ExternalListSyncManager.syncEntry(fields = changed)
                 │
                 ├── Run.fieldsFor("anilist")   → narrowTo("anilist")   ─┐
                 ├── Run.fieldsFor("mal")       → narrowTo("mal")        │  6 × async
                 ├── Run.fieldsFor("kitsu")     → narrowTo("kitsu")      ├─ aynı anda
                 ├── Run.fieldsFor("shikimori") → narrowTo("shikimori")  │  30 sn timeout
                 ├── Run.fieldsFor("bangumi")   → narrowTo("bangumi")    │
                 └── Run.fieldsFor("simkl")     → narrowTo("simkl")     ─┘
                          │
                          └── boş küme ⇒ hedefe İSTEK ATILMAZ, sonuç: "değişiklik yok"
```

### 3.1 `SyncField.kt` (yeni dosya)
- `enum class SyncField` — 13 alan: `STATUS, SCORE, PROGRESS, VOLUME_PROGRESS, START_DATE, END_DATE, NOTES, TAGS, FAVORITE, PRIVATE, REWATCH_COUNT, REPEAT_VALUE, PRIORITY` (satır 22).
- `object SyncFieldDiff.between(previous, current)` (satır 43):
  - `previous == null` (yeni kayıt) → `SyncField.ALL` — ilk yazım tam olmalı, aksi hâlde hedefte kayıt oluşmaz.
  - Alan karşılaştırmaları `null`/`""` ayrımını korur: **boşaltma da bir değişikliktir**.
  - `isRepeating` bayrağı değişirse `REWATCH_COUNT` **ve** `STATUS` kirli sayılır (MAL/Kitsu bayrağı durum yazımıyla taşır).
- `ProviderCapabilities` + `ProviderCapabilitiesCatalog` (satır 157): 6 sağlayıcının desteklediği alan kümesi, puan ölçeği (`HUNDRED`/`TWENTY`/`TEN`), durum sözlüğü ve `rewatchingAsStatus` bayrağı.
- `narrow()`: `rewatchingAsStatus = false` olan hedeflerde (MAL, Kitsu) `REWATCH_COUNT` gelirse `STATUS` de eklenir — böylece tekrar sayısı değişince `is_rewatching` / `reconsuming` bayrağı da yazılır.
- `Set<SyncField>.narrowTo(provider)`: bilinmeyen sağlayıcıda tam yazım korunur (ileriye dönük güvenlik).

### 3.2 `ExternalListSyncManager.kt`
- `Run.fields: Set<SyncField>` + `Run.fieldsFor(provider)` (satır 95–98).
- `syncEntry(..., fields: Set<SyncField> = SyncField.ALL)`; varsayılan yalnızca *yeni kayıt* ve *kuyruk yeniden denemesi* için doğrudur.
- Her sağlayıcı bloğu **kapı kontrollerinden sonra** `if (fields.isEmpty()) return SourceResult(X, OUTCOME_UP_TO_DATE)` yapar (satır 342, 458, 483, 520, 572, 607). Yani:
  - kapalı / bağlı değil / tür desteklenmiyor / kimlik çözülemedi → **eskisi gibi görünür gerekçe**,
  - değişiklik yok → **yeni görünür gerekçe**: `değişiklik yok`.
- Yeni sonuç sabiti `OUTCOME_UP_TO_DATE = "değişiklik yok"` (satır 51); `SyncResult.anySynced`/`needsRetry` bunu hata saymaz → gereksiz yeniden deneme kuyruğu oluşmaz.
- AniList favori sorgusu artık yalnızca `FAVORITE` alanı kirliyse yapılır (2 gereksiz istek tasarrufu).

### 3.3 Sağlayıcı yazıcıları

| Dosya | Değişiklik |
|---|---|
| `MalSyncManager.kt:38` | `fields` parametresi. Gövde yalnız kirli alanları ekler. `score` yalnız `SCORE` kirliyse (`tenPoint ?: 0`). Tarih: tam tarih → aynen; **boşaltıldı** → `0000-00-00`; parsiyel (`2016`, `2016-05`) → **gönderilmez**. `is_rewatching`/`is_rereading` durumla birlikte. Boş gövde → **istek atılmaz**. |
| `AniListSyncManager.kt:22` | `fields`. Var olan kayıtta yalnız kirli alanlar `variables`'a girer. `scoreRaw` **asla `null` gönderilmez**; temizleme `scoreRaw = 0`. Tarih temizleme yalnız diff `START_DATE`/`END_DATE` içeriyorsa `{year:null,month:null,day:null}` ile. Yeni kayıtta tam yazım. |
| `KitsuSyncManager.kt:132` | `fields`. `KitsuEntryExtras`'a `reconsuming` + `reconsumeCount` eklendi. Güncelleme yolu artık **attribute PATCH** (`patchLibraryEntry`) kullanır; gönderilmeyen attribute Kitsu'da korunur. İlerleme uzunluk koruması (`422 cannot exceed length`) korunur. |
| `KitsuApiClient.kt:727` | Yeni `patchLibraryEntry(token, entryId, attributes)` + `patchAttributes`. `updateLibraryEntryDetailed` korunur ama eşitleme yolunda artık kullanılmaz. |
| `ShikimoriSyncManager.kt:72` | `fields`. Rate kimliği biliniyorsa `patchUserRate`; `text`/`rewatches`/`volumes` artık PATCH'te de gider. Puan yalnız `SCORE` kirliyse. `patch` başarısızsa (kayıt yok/eski kimlik) resmî `createUserRate` upsert'üne düşer. |
| `ShikimoriApiClient.kt:883` | Yeni `patchUserRate`; `createUserRate`'in `score` parametresi `Int?` yapıldı (`null` = puana dokunma). |
| `BangumiSyncManager.kt:86` | `fields`. Durum değişmediyse `patchCollection` (PATCH) — kayıt yoksa 404 → `upsertCollection`'a düşer. Not/etiket sanitizasyonu (`sanitizeCommentForWrite`, `sanitizeTagsForWrite`) korunur. |
| `SimklSyncManager.kt:52` | `Dropped → "notinteresting"` (film dalı dâhil). `"notinteresting"` okuma eşlemesi eklendi. `fields`: puan yalnız `SCORE` kirliyse (`removeRating` artık kazara çağrılmıyor), `/sync/history` yalnız `PROGRESS`, `/sync/add-to-list` yalnız `STATUS`. |
| `SimklSyncRepository.kt:316` | Aynı `Dropped → notinteresting` düzeltmesi (ikinci kopya). |
| `MediaEntryRepository.kt:230` | `update()` önce DB'deki önceki satırı okur, `SyncFieldDiff.between` ile farkı çıkarır, `syncEntryIfPossible(..., fields = changedFields)` çağırır. Yeni kayıt (`insert`) yolu varsayılan `ALL` ile çalışır. |
| `PlayerScrobbleController.kt` | Scrobble artık yalnız `{PROGRESS, STATUS}` yazar. Oynatıcı puan/not/tarihe dokunmaz. |

---

## 4. Eşleme tabloları (kanıta dayalı)

### 4.1 Durum

| Kitsugi | AniList | MAL (anime / manga) | Kitsu | Shikimori | Bangumi | Simkl |
|---|---|---|---|---|---|---|
| Watching | `CURRENT` | `watching` / `reading` | `current` | `watching` | `type=3` (在看) | `watching` |
| Completed | `COMPLETED` | `completed` | `completed` | `completed` | `type=2` (看过) | `completed` |
| Planned | `PLANNING` | `plan_to_watch` / `plan_to_read` | `planned` | `planned` | `type=1` (想看) | `plantowatch` |
| Dropped | `DROPPED` | `dropped` | `dropped` | `dropped` | `type=5` (抛弃) | **`notinteresting`** |
| Paused | `PAUSED` | `on_hold` | `on_hold` | `on_hold` | `type=4` (搁置) | `hold` |
| Repeating | `REPEATING` | `watching` + `is_rewatching=true` | `current` + `reconsuming=true` | `rewatching` | (yok) | (yok) |

### 4.2 Puan

| Hedef | Alan | Aralık | "Puan yok" davranışı |
|---|---|---|---|
| AniList | `scoreRaw` (Int) | 0–100 | `0` (alan `null` **gönderilmez**) |
| MAL | `score` | 0–10 | `0` = puanı sil |
| Kitsu | `ratingTwenty` | 2–20 | `null` = puanı sil (yalnız kullanıcı silerse) |
| Shikimori | `score` | 0–10 | `0` = puanı sil |
| Bangumi | `rate` | 0–10 | `0` |
| Simkl | `user_rating` | 1–10 | `removeRating` |

### 4.3 Tarih biçimleri

| Hedef | Biçim | Boşaltma |
|---|---|---|
| AniList | `FuzzyDateInput {year, month, day}` — üçü birlikte | `{null, null, null}` (yalnız diff'te varsa) |
| MAL | `YYYY-MM-DD` | `0000-00-00` |
| Kitsu | `YYYY-MM-DDT00:00:00.000Z` | `null` (yalnız diff'te varsa) |
| Shikimori / Bangumi / Simkl | **desteklenmez** | — |

---

## 5. Asenkronluk ve dayanıklılık

| Gereksinim | Uygulama | Kanıt |
|---|---|---|
| Altı kaynak eşzamanlı | `coroutineScope { listOf(async{…}×6).awaitAll() }` | `ExternalListSyncManager.kt:153–161` |
| Bir kaynak donarsa diğerleri durmaz | her blok `withTimeoutOrNull(30_000)` | `:57`, `:314` |
| Kaynak yoksa görünür gerekçe | `outcomes` haritası: `eşitlendi`, `kimlik çözülemedi`, `tür desteklenmiyor`, `kapalı`, `bağlı değil`, `hata`, `zaman aşımı`, `çağıran yazdı`, **`değişiklik yok`** | `aggregate()` `:898` |
| Yeniden başlatma / çevrimdışı | `SyncResult.needsRetry` yalnız gerçek hatada `true`; `PendingSyncDrainer` kuyruğu boşaltır; `değişiklik yok` kuyruğa **girmez** | `SyncResult:1027` |
| Kaynak ayrıcalıklı değil | `run.allowFanOut` + `CrossSyncPolicy.allowsWrite` her hedef için ayrı; kaynak satırın kendisi "çağıran yazdı" ile işaretlenir | `prepare()` |

---

## 6. Doğrulama

### 6.1 Otomatik (bu ortamda yapılabilen)

```
python3 /tmp/kcheck.py $(git status --porcelain | awk '{print $2}' | grep '\.kt$')
→ files: 14 with errors: 0
```

Tam kaynak taraması (1280 dosya) 6 **önceden var olan** tree-sitter yanlış-pozitifi verir
(`AppRootTabPages.kt`, `LinkedAccountVault.kt`, `BangumiApiClient.kt`, `SimklApiClient.kt`,
`TmdbDiscoverClient.kt`, `KitsugiAccountContent.kt`) — bu değişikliklerle ilgisi yoktur.

### 6.2 Kullanıcının çalıştırması gerekenler (bu ortamda JDK/Android SDK yok)

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest --tests '*SyncFieldDiffTest'
./gradlew :app:testDebugUnitTest --tests '*CrossSync*'
```

Yeni test: `app/src/test/java/com/kitsugi/animelist/data/auth/SyncFieldDiffTest.kt`
(16 test) — sözleşmeyi sabitler:
- `previous yoksa tam yazım`, `hiçbir alan değişmediyse boş küme`
- `sadece puan değişti` → **yalnız** `SCORE`
- `not boşaltıldı` → `NOTES` (hedefte silinmeli)
- `tekrar bayrağı durumu da kirletir` → `STATUS + REWATCH_COUNT`
- `desteklenmeyen alan hedefe yazılmaz` (Simkl'de tarih/cilt yok; Kitsu'da cilt yok)
- `mal tekrar sayısı durumu tetikler`, `anilist tekrar sayısı durumu tetiklemez`
- `altı sağlayıcı tanımlı`

### 6.3 Elle senaryolar (cihazda)

1. **Kitsu'dan ilerleme:** Kitsu listesinde bir animeyi 3 → 4 yap. MAL/AniList/Shikimori/Bangumi/Simkl'de ilerleme 4 olmalı; **MAL puanı ve Kitsu dışındaki notlar değişmemeli**.
2. **MAL'da puan silme:** MAL web'inde puanı 8 yap, uygulamada yerel puanı **boş** bırakıp yalnız ilerlemeyi değiştir → MAL'da puan **8 kalmalı** (eski kodda 0'a düşüyordu).
3. **Kitsu'da puan koruma:** Kitsu'da puanı 8 yap, uygulamada yerel puanı boş bırak, ilerleme değiştir → Kitsu puanı **8 kalmalı**.
4. **Tekrar:** Bir animeyi "Tekrar izleniyor" yap → Kitsu'da `reconsuming=true`, `reconsumeCount=1`; MAL'da `is_rewatching=1`; Shikimori'de `rewatches=1`.
5. **Bırakıldı → Simkl:** Simkl'de kayıt "Not Interested" altında görünmeli (eskiden hiç görünmüyordu).
6. **Hiç değişiklik yok:** Bir satırı aç, değiştirmeden kaydet → sonuç raporunda her hedef için `değişiklik yok`; hiçbirinde `eşitlendi` yazmamalı, ağ isteği olmamalı.
7. **Donan kaynak:** Uçak modunda 3 kaynağı aç, 3'ünü kapat → yanıt 30 sn içinde dönmeli, açık olanlar gerekçeleriyle listelenmeli.

---

## 7. Dürüst sınırlar (gizlenmiyor)

| Konu | Durum |
|---|---|
| **TMDB** | Yazma ucu yok (liste/oturum API'si entegre değil); yalnız kimlik kaynağı. Eşitleme raporunda "hedef" olarak sayılmaz. |
| **Simkl manga** | Simkl manga desteklemez → `tür desteklenmiyor` gerekçesiyle atlanır. |
| **Simkl tarih/tekrar** | API'de yok; sessizce gönderilmez, raporda hedef "eşitlendi" görünür (desteklenen alt küme için). |
| **Bangumi tarih** | Koleksiyon API'sinde yok. |
| **Shikimori tarih** | `user_rates`'te yok. |
| **MAL favori** | MAL'de favori ucu yok → yalnız AniList→MAL yönünde "rapor" mümkün, yazma yok. |
| **Derleme** | Bu ortamda JDK/Android SDK olmadığı için **derleme ve birim test çalıştırılmadı**; doğrulama tree-sitter sözdizimi + kod okuma düzeyinde. `./gradlew :app:compileDebugKotlin` sonucu bekleniyor. |
| **`scoreRaw: null`** | AniList açık `null`'ı reddediyor (seanime #985). Kod bu nedenle puanı temizlerken `0` gönderiyor; alan hiç gönderilmiyor da değil, çünkü kullanıcı puanı sildiyse hedefte de silinmeli. |

---

## 8. Dosya değişiklikleri

```
 yeni  app/src/main/java/com/kitsugi/animelist/data/auth/SyncField.kt
       app/src/main/java/com/kitsugi/animelist/data/auth/ExternalListSyncManager.kt
       app/src/main/java/com/kitsugi/animelist/data/auth/AniListSyncManager.kt
       app/src/main/java/com/kitsugi/animelist/data/auth/MalSyncManager.kt
       app/src/main/java/com/kitsugi/animelist/data/auth/KitsuApiClient.kt
       app/src/main/java/com/kitsugi/animelist/data/auth/KitsuSyncManager.kt
       app/src/main/java/com/kitsugi/animelist/data/auth/ShikimoriApiClient.kt
       app/src/main/java/com/kitsugi/animelist/data/auth/ShikimoriSyncManager.kt
       app/src/main/java/com/kitsugi/animelist/data/auth/BangumiSyncManager.kt
       app/src/main/java/com/kitsugi/animelist/data/auth/SimklSyncManager.kt
       app/src/main/java/com/kitsugi/animelist/data/repository/SimklSyncRepository.kt
       app/src/main/java/com/kitsugi/animelist/data/local/MediaEntryRepository.kt
       app/src/main/java/com/kitsugi/animelist/ui/screens/fullscreen/runtime/PlayerScrobbleController.kt
 yeni  app/src/test/java/com/kitsugi/animelist/data/auth/SyncFieldDiffTest.kt
```

---

## 9. Kaynak listesi

**Resmî dokümanlar**
1. AniList GraphQL Reference — `SaveMediaListEntry`: https://docs.anilist.co/reference/mutation
2. AniList mutations rehberi: https://docs.anilist.co/guide/graphql/mutations
3. AniList media list sorgusu: https://docs.anilist.co/guide/graphql/queries/media-list
4. MAL API v2 referansı: https://myanimelist.net/apiconfig/references/api/v2
5. Kitsu API (JSON:API): https://kitsu.app/api/edge/library-entries
6. Shikimori API v2: https://shikimori.one/api/doc/2.0
7. Bangumi API v0: https://bangumi.github.io/api/
8. Simkl API: https://api.simkl.org/ (senkronizasyon: `/sync/add-to-list`, `/sync/history`, `/sync/ratings`)

**Açık kaynak kod (klone edilip okundu)**
9. MALSync/MALSync — `src/_provider/SingleAbstract.ts`, `AniList/single.ts`, `MyAnimeList_api/single.ts`, `Kitsu/{single,helper}.ts`, `Simkl/{single,helper}.ts`, `Shikimori/helper.ts`, `pages-sync/syncPage.ts`: https://github.com/MALSync/MALSync
10. aniyomiorg/aniyomi — `data/track/{anilist,myanimelist,kitsu,shikimori,bangumi,simkl}`: https://github.com/aniyomiorg/aniyomi

**Hata raporu**
11. seanime #985 — AniList `scoreRaw: null` → 400 *"The score raw must be an integer"* (17.10.2026).

---

## 10. Ek: ikinci geçişte kapatılan hatalar (commit `1e92d59`)

Alan bazlı yazıma geçtikten sonra kodu tekrar taradım; altı yeni hata daha çıktı:

| # | Hata | Kanıt | Düzeltme |
|---|---|---|---|
| E1 | **Kuyruktan yeniden deneme tam yazım yapıyordu** — çevrimdışıyken kuyruğa giren "ilerleme" işi, bağlantı gelince satırın tamamını yazıp hedeflerdeki puan/notu ezebilirdi. | `PendingSyncDrainer.drain` → `syncEntry(context, entry)` (varsayılan `ALL`) | İşin alan kümesi JSON'a `_syncFields` olarak gömülür; drain yalnız o alanları yazar. Eski kayıtlar (`_syncFields` yok) tam yazım olarak okunur — geriye dönük uyumlu. |
| E2 | **AniList'te tanımsız GraphQL değişkeni** — puan temizlenirken `scoreRaw: 0` gönderiliyordu ama `$scoreRaw` dokümanda tanımlı değildi. | `AniListSyncManager` mutasyon metni `scoreRaw != null` iken kuruluyordu | Puan kararı (`null` / `0` / değer) **doküman kurulmadan önce** verilir; değişken yalnız tanımlıysa gönderilir. `scoreRaw: null` hiçbir durumda gönderilmez. |
| E3 | **MAL "eşitlendi" yalanı** — yazılacak geçerli alan yokken (ör. yalnız parsiyel tarih değişti) istek atılmıyor ama sonuç "eşitlendi" görünüyordu. | `updateMalEntry` `Unit` dönüyordu | `Boolean` döner; `false` → `değişiklik yok`. |
| E4 | **Simkl notu hiç yazılmıyordu** ama yetenek matrisi `NOTES` destekliyor diyordu. | `SimklBatchEntry` alanlarında not yok; `private_memo` gönderilmiyor | `SimklBatchEntry.privateMemo` + `private_memo` gövdesi; not değiştiyse `add-to-list` tetiklenir (not silinirse boş string). |
| E5 | **Bangumi boş gövde** — yalnız not değişip not boşaltıldığında `PATCH {}` 400 üretiyor, ardından gereksiz tam upsert çalışıyordu. Ayrıca not düzenlemesi tüm bölümleri yeniden işaretliyordu. | `hasAnyArg` yok; `setProgress` bloğu koşulsuz | Yazacak argüman yoksa istek atılmaz (`yazılacak değişiklik yok`); bölüm işaretleme yalnız `PROGRESS`/`STATUS` kirliyken. |
| E6 | **MAL gövdesi `&` ile başlıyordu** — tek alan gönderildiğinde gövde `&score=8` oluyordu (eski kodda hep `status` önce geldiği için görünmüyordu). | `buildString` ilk parçayı da `&` ile ekliyor | Gönderim öncesi `trimStart('&')`. |

Ayrıca Simkl'de "hiç istek atılmadı" durumu artık `eşitlendi` değil `değişiklik yok` olarak raporlanıyor (kapasite süzgeci tüm alanları eleyebilir).

---

## 11. Üçüncü geçiş: satır satır denetim (commit `f1198c6`, `ae0f897`, `b0b530a`)

"En ufak kod atlanmasın" talebiyle tüm yazma yolları tek tek okundu. Bulunan **14 ek hata**:

| # | Hata | Neden kritik | Düzeltme |
|---|---|---|---|
| E7 | **Tam yazım / kısmi yazım ayrımı kayboluyordu.** `Run.fieldsFor(provider)` her hedefe *süzülmüş* alt küme verdiği için yazıcı içindeki `fields == ALL` testi asla doğru olmuyordu. | "Tam yazımda boş puan gönderme" koruması hiç devreye girmiyordu → tam yazımda puan/not yine silinebiliyordu. | `Run.isFullWrite` + yazıcılara açık `fullWrite` parametresi. |
| E8 | MAL'da tam yazımda `tags=` ve `comments=` boş gönderiliyordu. | MAL'daki etiket/not silinir. | Boş değer yalnız diff'te gönderiliyor. |
| E9 | AniList'te tam yazımda `notes: ""` ve favori toggle. | Not silinir; yerelde favori yoksa AniList favorisi kaldırılır. | Not yalnız dolu/diff'te; favori tam yazımda **yalnız ekleme** yönünde. |
| E10 | Simkl'de tam yazımda `removeRating`. | Simkl puanı silinir. | `removeRating` yalnız diff'te. |
| E11 | Bangumi: `writeAll` "durum değişti" ile "tam yazım"ı karıştırıyordu; ayrıca yalnız ilerleme değişince koleksiyon yazımı atlanınca **bölüm işaretleme de atlanıyordu**. | Puan/not gereksiz eziliyor; anime ilerlemesi Bangumi'ye hiç gitmiyor. | `fullWrite` / `statusChanged` ayrıldı; koleksiyon atlanırken ilerleme yine yazılıyor. |
| E12 | **Oynatıcı scrobble'ı yerel DB'yi hiç güncellemiyordu.** | Liste eski bölümü gösteriyor; sonraki bir tam yazım eski ilerlemeyi tüm kaynaklara geri yazabiliyordu (ilerleme geri düşer). | Scrobble sonrası yerel satır yazılır (`max(progress)`, Completed/Repeating korunur) + tarih otomasyonu. |
| E13 | MAL'de `Repeating → "watching"`. | Tamamlanmış kayıt MAL'da "İzleniyor"a düşüyor, tamamlanma bozuluyor. | `completed` + `is_rewatching` (MAL'ın kendi modeli). |
| E14 | AniList 429'da `Retry-After` kadar **bloklayan** bekleme (120 sn'ye kadar). | `withTimeoutOrNull(30s)` bloklayan çağrıyı kesemez → tek kayıt tüm yayılımı dakikalarca kilitler. | Bekleme 5 sn ile sınırlandı; geri çekilme sayacı gerçek değerle işaretlenir. |
| E15 | Kuyrukta aynı satır için yeni iş eskisini **alan kümesi birleştirmeden** siliyordu. | Çevrimdışıyken önce ilerleme, sonra not değiştirilirse ilerleme güncellemesi kaybolur. | Alan kümeleri birleştirilir. |
| E16 | Kuyruk boşaltmada başarı ölçütü "mesaj var mı"ydı. | Yazacak şey olmayan işler 5 kez denenip sonsuza kadar kuyrukta kalıyordu. | Ölçüt `!needsRetry` (yalnız gerçek hata). |
| E17 | Başarı yolunda kaynak bazlı sonuç özeti gösterilmiyordu. | "Simkl'e neden yazmadı?" cevabı hiç görünmüyordu. | `eşitlendi` dışında sonuç varsa özet bildirime eklenir. |
| E18 | `hiddenFromStatusLists` (listede gizle) hiç eşitlenmiyordu. | Düzenleme ekranındaki anahtar hiçbir yere gitmiyordu. | `SyncField.HIDDEN_FROM_STATUS_LISTS` + AniList yazımı. |
| E19 | Kitsu tam yazımda puan/not/tarih `null` gönderiyordu. | Kitsu'daki değerler silinir. | Boş değer yalnız diff'te; tam yazımda yalnız dolu değerler. |
| E20 | Değişiklik yokken bile altı kaynağa istek atılıyordu. | Gereksiz ağ yükü ve gürültülü bildirim. | Diff boşsa ağa çıkılmaz. |

### Doğrulama durumu

- Tree-sitter: 1284 Kotlin dosyası tarandı; **6 bilinen yanlış pozitif** (bu değişikliklerle ilgisiz).
- `SyncFieldDiffTest` artık **22 test** (alan farkı, yetenek matrisi, tekrar bayrağı, tarih/puan/öncelik/favori/gizlilik kapsamı).
- **Derleme ve çalışma zamanı testi bu ortamda YAPILAMADI** (JDK/Android SDK yok). Bu rapordaki hiçbir madde "derlendi" garantisi taşımaz; `./gradlew :app:compileDebugKotlin` çalıştırılmalıdır.

---

## 12. Dördüncü geçiş: sözlük ve içe aktarma denetimi (commit `04bdfe7`)

Bu geçişte odak **"tekrar izleme" bilgisinin altı kaynakta tutarlı taşınması** ve **içe aktarma
sırasında UYDURULMUŞ alan adları** oldu. Hepsi resmî sözleşmeye karşı doğrulandı
(MAL API v2 `list_status`, AniList `MediaList`, Kitsu JSON:API `library-entries`).

| # | Hata | Neden kritik | Düzeltme |
|---|---|---|---|
| E21 | **MAL içe aktarma var olmayan bir alan istiyordu:** `list_status{…,is_repeating,…}`. MAL API v2'de böyle bir alan YOK; anime için `is_rewatching`, manga için `is_rereading` vardır. | Tekrar durumu hiçbir zaman içe aktarılmıyordu; ayrıca bilinmeyen alan isteyen liste çağrısı 400 dönebilir (o durumda **tüm** MAL içe aktarması düşer). | İki ayrı URL anime/manga için doğru alan adlarıyla kurulur; eski yanıtlar için `is_repeating` yine okunur (geriye dönük uyum). |
| E22 | **`rewatching`/`rereading` durum sözcükleri `WatchStatus.Repeating`e çevrilmeli mi?** İlk plan "evet"ti; kod okunduğunda **hayır** çıktı. | MAL'a geri yazımda `Repeating → completed` (E13) kodlanmıştır. Devam eden bir yapım `Repeating` olarak içe aktarılsaydı MAL'da "tamamlandı"ya dönerdi. MAL'ın kendi modeli: durum `watching`/`reading` kalır, tekrar `is_rewatching` bayrağındadır. | Durum `Watching` kalır; `isRepeating` bayrağı hem `is_rewatching`/`is_rereading` alanlarından hem de ham `rewatching`/`rereading` metninden doldurulur. |
| E23 | **AniList içe aktarması `REPEATING` durumunu tanımıyordu** (`else -> Planned`). | AniList'te "tekrar izleniyor" kaydı kütüphaneye **"Planlandı"** olarak giriyordu. | `"REPEATING" -> WatchStatus.Repeating`. |
| E24 | **`isRepeating = repeatCount > 0` yanlış semantik.** UI'da bu alan "Yeniden İzleniyor" anahtarıdır (şu an), "geçmişte tekrar yapıldı" demek değildir. | Geçmişte 2 kez tekrar izlenmiş bir yapım, sıradaki eşitlemede MAL'a `is_rewatching=1`, Kitsu'ya `reconsuming=true` yazıyordu → kullanıcının bitmiş yapımları "şu an tekrar izleniyor" görünüyor ve Kitsu/MAL durumu bozuluyordu. | AniList: bayrak yalnız `status == REPEATING` iken; Kitsu: yalnız `reconsuming`; Shikimori: yalnız `status == rewatching`. Sayaç (`repeatCount`) ayrı alan olarak korunur. |
| E25 | **Kitsu içe aktarması tekrar bilgisini tamamen atıyordu.** `KitsuLibraryEntry` içinde `reconsuming`/`reconsumeCount` hiç okunmuyordu. | Kitsu'da "şu an tekrar izleniyor" bilgisi `status=current` + `reconsuming` ile tutulur; bu okunmadığı için içe aktarma sonrası ilk eşitleme bayrağı **kapatıyordu**. | Alanlar `KitsuLibraryEntry`e eklendi, `isRepeating`/`repeatCount` olarak eşlendi (Kitsu yanıtı `fields[]` ile kısıtlanmadığı için alanlar zaten geliyordu). |
| E26 | **Kitsu'ya çelişkili kayıt yazılıyordu:** durum `completed` iken `reconsuming=true`. | Kitsu bu birleşimi "tekrar izleme sürüyor" olarak yorumlayabilir; bayrak kalıcı açık kalır. | `reconsuming` yalnız `status == "current"` iken gönderilir. |
| E27 | **MAL etiketleri nesne olarak gelebiliyor:** `tags: [{"name":"x"}]`. Kod `optString` ile ham JSON metnini etiket yapıyordu. | Etiketler bozuk metin olarak yazılır, sonraki yazımda MAL'a geri gönderilir. | İki biçim de okunur (`String` / `{name}`); boş liste `null`a düşer. |
| E28 | **AniList `priority` hiç sorgulanmıyordu.** | Öncelik alanı yazılabildiği hâlde içe aktarmada kayboluyordu. | Sorguya `priority` eklendi, `entry.priority`ye eşlendi (0 = yok). |
| E29 | **Kimlik önbelleği "tamam" işaretini bağlantı durumuna bağlamıştı:** MAL bağlı değilken `malId == null` "tamam" sayılıyordu. | MAL kimliği, diğer beş kaynağın çözüm anahtarıdır. Kullanıcı MAL'ı sonradan bağladığında satır önbellekten "tamam" geldiği için **MAL'a hiçbir zaman yazılmıyordu** — sessiz ve kalıcı kayıp. | Anime/manga için `malId != null` bağlantıdan bağımsız zorunlu; çözülemezse kayıt `complete=false` yazılır ve 6 saatlik soğuma sonrası yeniden denenir. |

### Ek doğrulama notları (bu geçişte yeniden okunanlar)

- `ExternalListSyncManager.syncEntry`: altı kaynak tek `coroutineScope` içinde `async`; her kaynak
  `disabled / not_connected / skipped_type / skipped_identity / timeout / up_to_date / failed / synced`
  ayrımını **görünür** sonuç olarak döndürüyor; `Run.fieldsFor(provider)` yetenek süzgeci, `Run.isFullWrite`
  ise süzme sonrası kümeye bakmadan taşınıyor (E7'nin kalıcı çözümü).
- Shikimori yazımı: `rewatching` gerçek bir durumdur (`rewatchingAsStatus = true`), bu yüzden tekrar
  bayrağı ayrıca gönderilmez; sayaç `rewatches` ile taşınır.
- Bangumi: anime ilerlemesi ayrı uçtan (`setProgress`), kitap ilerlemesi `ep_status`/`vol_status`
  ile; koleksiyon yazımı atlansa bile ilerleme yazılır (E11 korunuyor).
- Simkl: `progressDirty` durum değişimini de kapsar (geçmiş yazımı sonrası durum en sonda yeniden
  uygulanır); `notinteresting` ↔ `dropped` eşlemesi iki yönlü kapalı devre.

### Test kapsamı

- Yeni `WatchStatusMappingTest` (5 test): Kitsu `Repeating → current`, Kitsu durum gidiş-dönüşü,
  Shikimori `rewatching` birinci sınıf durum, AniList `REPEATING` tablosu, MAL'ın `rewatching` diye
  bir durumu **olmadığının** sabitlenmesi.
- `MalImportManagerUrlTest` artık `is_rewatching` / `is_rereading` alanlarının varlığını ve
  `is_repeating`in **yokluğunu** doğruluyor (E21 regresyon kilidi).
- Toplam: `SyncFieldDiffTest` 22 + `WatchStatusMappingTest` 5 + `MalImportManagerUrlTest` 2.

### Doğrulama durumu (dürüst)

- Tree-sitter taraması: **1388 Kotlin dosyası**, 7 bilinen yanlış pozitif
  (`AppRootTabPages.kt`, `LinkedAccountVault.kt`, `BangumiApiClient.kt`, `SimklApiClient.kt`,
  `TmdbDiscoverClient.kt`, `KitsugiAccountContent.kt`, `scripts/tests/fixtures/RoomAnnotations.kt`).
- **Derleme/çalıştırma YAPILMADI** (ortamda JDK/Android SDK yok). Kullanıcı tarafından
  `./gradlew :app:compileDebugKotlin` ve
  `./gradlew :app:testDebugUnitTest --tests '*WatchStatusMappingTest' --tests '*MalImportManagerUrlTest' --tests '*SyncFieldDiffTest'`
  çalıştırılmalıdır.

---

## 13. Beşinci geçiş: dayanıklılık + "kaybolan değişiklik" avı (commit `bcc61dd`)

Odak: **yazım sırasında oluşan sessiz kayıplar** ve **tek bir yavaş kaynağın turu kilitlemesi**.
MAL-Sync kaynak kodu (`src/_provider/MyAnimeList_api/{single,helper}.ts`) bu geçişte yeniden
okundu; `_setStatus(Rewatching)` davranışı **birebir doğrulandı** (aşağıda E-not).

| # | Hata | Neden kritik | Düzeltme |
|---|---|---|---|
| E30 | **Eşitleme yazımlarında toplam çağrı tavanı yoktu.** `KitsugiHttpClient.client` içinde `callTimeout` yok; `connectTimeout`/`readTimeout` yalnız tek adımı sınırlar. | Yavaş damlayan (trickle) bir yanıt + 2 otomatik yeniden deneme, tek bir kaydın yazımını dakikalarca sürdürebiliyordu. `withTimeoutOrNull` **bloklayan** çağrıyı kesemez. | Yeni `KitsugiHttpClient.syncClient` (25 sn `callTimeout`, bağlantı havuzu/dispatcher paylaşılır) MAL/Kitsu/Shikimori/Simkl yazımlarında kullanılıyor. Bangumi zaten `bangumiClient` (14 sn) kullanıyordu; AniList `aniListClient` (15 sn). İndirme/oynatma yolları **etkilenmez**. |
| E31 | **Hesap bağlantısı kesilince çapraz kimlik önbelleği temizlenmiyordu.** | Önbellek "tamam/eksik" işaretini bağlantı durumuna göre taşır; bayat işaret yüzünden yeniden bağlanan hesapta kimlikler eksik kalabiliyordu. | `ExternalAuthManager.disconnectAccount` → `CrossProviderIdentityCache.clear`. |
| E32 | **"Bağlı değil" sonucu başarı sayılıyordu.** `SyncResult.needsRetry` yalnız `errors`e bakıyordu. | Token'ı süresi dolmuşken yapılan düzenleme kuyruğa **yazılmıyor**, üstelik kuyrukta bekleyen iş "başarılı" sayılıp **siliniyordu** → değişiklik o sağlayıcıya bir daha hiç gitmiyordu. | `hasConnectionGap` + `connectionGapSources` eklendi; `needsRetry` bunu da kapsıyor. `MediaEntryRepository` (update + delete) `success` ölçütünü `!needsRetry` yaptı; böylece iş kuyruğa yazılıyor. |
| E33 | **Yukarıdaki düzeltmenin yan etkisi: kullanıcının hiç kullanmadığı servisler için gürültü.** | Bangumi hesabı olmayan bir kullanıcıya her düzenlemede "hesap bağlı değil" uyarısı çıkardı. | `connectionGapSources` yalnız **kullanıldığı kanıtlı** kaynakları içerir: satırın kendi kaynağı, ya da satırda o sağlayıcıya ait kayıt kimliği (AniList `aniListEntryId`, Simkl `simklId`). Diğerleri sessizce "bağlı değil" kalır. |
| E34 | **Kitsu boş PATCH'i "güncellendi" diye raporluyordu.** | Değişen alanlar süzüldükten sonra yazılacak attribute kalmadığında istek atılmıyor ama kullanıcıya başarı mesajı gidiyordu; üst katman da sonucu `eşitlendi` sayıyordu. | Attribute kümesi boşsa `SyncResult(messages = empty)` döner; `ExternalListSyncManager` bunu `değişiklik yok` olarak raporlar. |
| E35 | **Yeniden bağlanma, kuyruktaki işlere taze bütçe vermiyordu.** | 5 denemeyi doldurmuş işler (ör. uzun süre token'sız kalmış) hesap yeniden bağlandığında da denenmiyordu. | `AuthEvent.Success` akışındaki `drainPendingQueue()` → `freshBudget = true`. |
| E36 | **Zaman aşımının hata olarak işaretlendiği doğrulandı.** | (Şüphe: `timed()` hatasız dönüyor olabilirdi.) Kod okundu: `aggregate` `OUTCOME_TIMEOUT` için `errors`e mesaj ekliyor → `needsRetry = true`. Ek değişiklik **yapılmadı** (mükerrer mesaj olurdu). | — |

### Doğrulanan upstream davranış (değişiklik değil, teyit)

- `MALSync src/_provider/MyAnimeList_api/single.ts` → `_setStatus(Rewatching)`: durum **`completed`**
  yapılır ve `is_rewatching = true` yazılır; `_getStatus()` de `completed + is_rewatching` çiftini
  `Rewatching` olarak geri okur. Bizim `Repeating → completed` + `is_rewatching` eşlememiz **aynı**.
  (Bu satır, önceki geçişte "şüpheli" işaretlenmişti; kaynak kodla kapatıldı.)
- `MALSync .../helper.ts` durum sözlüğü `watching|completed|on_hold|dropped|plan_to_watch` —
  "rewatching" bir **durum değildir** (E22/E13 ile tutarlı).

### Test kapsamı

- Yeni `SyncResultRetrySemanticsTest` (**7 test**): kimlik atlaması tekrar denemez, geçici hata
  dener, "bağlı değil" (kanıtlı) dener, kullanılmayan servis denemez, kapalı servis denemez,
  "değişiklik yok" denemez, zaman aşımı dener.
- Toplam test dosyası: `SyncFieldDiffTest` 22, `WatchStatusMappingTest` 5,
  `MalImportManagerUrlTest` 2, `SyncResultRetrySemanticsTest` 7, `EntryAutoRulesTest` (mevcut).

### Doğrulama durumu (dürüst)

- Tree-sitter: **1390 Kotlin dosyası**, 7 bilinen yanlış pozitif (aynı liste).
- **Derleme/çalıştırma YAPILMADI** (ortamda JDK/Android SDK yok). `syncClient` gibi yeni semboller
  ve yeni test dosyaları yalnız **statik** olarak doğrulandı; derleyici hata verirse düzeltmek için
  çıktı yeterli.

---

## 14. Altıncı geçiş: birleştirme kuralının tek noktaya taşınması + yazma yolu denetimi

Odak: **çapraz eşitleme motorunun (`AuthViewModel` L1179-1750) karar mantığı.** Bu blok,
altı kaynağın altı ayrı `if` gövdesinde kendi kopyası olan bir mantığı çalıştırıyordu; her
kopya kendi hatasını taşıyordu. Karar mantığı **saf, yan etkisiz ve test edilebilir** tek bir
nesneye taşındı: `data/auth/CrossSyncMergePolicy.kt` (+ `CrossSyncMergePolicyTest`, 26 test).

Bu geçişte ayrıca yazma yolu (interceptor + sağlayıcı yazıcıları) satır satır yeniden okundu;
iki yeni hata bulundu (E45, E46), biri bu geçişte kendi eklediğim kuralın ürettiği bir
**sonsuz yazım döngüsüydü** (E47) ve düzeltildi.

### 14.1 Bulunan hatalar

| # | Hata | Neden kritik | Düzeltme |
|---|---|---|---|
| E37 | `needsUpdate` kapısı `!singleSourceGroup` ile korunuyordu: **tek kaynaklı grupta o kaynağa hiç yazılmıyordu.** | Kullanıcının elinde yalnız MAL kaydı varken (12/12 izlenmiş ama durum "izliyorum") otomatik düzeltme asla uygulanmıyordu. Aynı şekilde toplamı aşan ilerleme hiç kırpılmıyordu. | Kapı kaldırıldı. Tek kaynaklı grupta da `dirtyFields` çalışır; birleşim tek satırın kendisinden türediği için normalde boş küme döner, yalnız **gerçek** düzeltme (toplam yakalandı → `Completed`, ilerleme toplamı aştı → kırp) yazılır. |
| E38 | `bestStartDate`/`bestEndDate` = `firstNotNullOfOrNull` | Sonuç aday **sırasına** bağlıydı; aynı veri her turda farklı tarih üretebiliyordu. | Kural: başlangıç = **en erken**, bitiş = **en geç** tarih (ISO sözlük sırası = kronolojik). Boş/boşluk tarihler yok sayılır. Sıra bağımsızlığı test edildi (`merge(list) == merge(list.reversed())`). |
| E39 | `bestStatus` sabit öncelik zinciri `Repeating > Watching > Paused > Dropped` | Kullanıcının **bilerek bıraktığı** (Dropped) yapım, eski bir "izliyorum" satırı yüzünden geri açılıyordu. Ters yönde de `Planned` satırı ilerlemesi olan kaydı ezebiliyordu. | Yeni kural: (1) en güncel **anlamlı** satır (`Planned` hariç) `Dropped`/`Paused` diyorsa o kazanır — 12/12 izleyip bırakan kullanıcı `Completed`'a çevrilmez; (2) en güncel anlamlı satır `Completed` diyorsa `Completed`; (3) aksi hâlde ilerleme toplamı yakaladıysa `Completed` (kaynaklar arası en yaygın tutarsızlık); (4) yoksa en güncel anlamlı satırın durumu. Eşit zaman damgasında sıra deterministik (ilerleme ↓, kaynak adı ↑). |
| E40 | `progress = maxProgress` **kırpılmadan** yazılıyordu | Bir kaynak 12 bölümlük yapı için 25 bildirirse MAL `PATCH` 400 döner ve **aynı istekteki durum/puan/tarih de** yazılmaz. Kitsu 422 verir. | `Merged.progress` = toplam biliniyorsa `min(rawMaxProgress, total)`; `rawMaxProgress` raporlama için korunur. Toplam bilinmiyorsa kırpılmaz. |
| E41 | `score` = "en güncel **sıfırdan büyük** puan" | Belgelenmiş davranış korundu, ancak eşit zaman damgasında sıra kaynak adına bağlıydı. | `updatedAt` ↓, sonra puan ↓, sonra kaynak adı ↑ ile **deterministik**. Puan **silme**nin taşınamaması bilinçli sınırlama olarak §14.4'te yazıldı. |
| E42 | `bestRepeat`/`isFav` hesaplanıp `mergedEntry`'ye yazılıyor **ama altı `needsUpdate` ifadesinin hiçbiri bu alanlara bakmıyordu** | İki alan hiçbir zaman hedefe gitmiyordu (ölü kod). | `dirtyFields` artık `REWATCH_COUNT` ve (tek yönlü) `FAVORITE` üretir. AniList favorisi ayrı bir mutasyondur (`ToggleFavourite`) ve **tersine dönebilir**: yazmadan önce uzak durum okunur, yalnız farklıysa toggle edilir (`getAniListMediaFavoriteStatus` → `toggleAniListFavourite`). |
| E43 | Simkl toplu yazımı `mergedEntry.status/progress` kullanıyordu ama kırpma yoktu | Aynı 400 sınıfı hata (Simkl `shows`). | `mergedEntry.progress = merge.progress` olduğu için toplu yol da kırpılmış değeri gönderir. |
| E44 | Not/etiket/öncelik `firstNotNullOfOrNull` ile **birleştiriliyordu** | Farklı sağlayıcıların serbest metinleri birbirini ezerdi; öncelik ölçekleri uyumsuz (MAL 0..2, AniList 0..255, Bangumi yok). | Bu üç alan **bilinçli olarak birleştirilmiyor**: en güncel satırın değeri kullanılır ve `dirtyFields` bu alanları **hiç üretmez** → bir kaynağın notu diğerine yazılmaz. Gerekçe kodda yorum olarak duruyor. |
| E48 | E42 düzeltmesinin ardından **yalnız favori değiştiğinde** AniList'e içi boş bir `SaveMediaListEntry` (sadece `id`) gönderiliyordu | Gereksiz istek + yanıltıcı "güncellendi" mesajı; AniList'te liste kaydı değişmediği hâlde sayaç artıyordu. | `listFields = dirty - FAVORITE`; küme boşsa liste mutasyonu hiç çağrılmaz, yalnız favori adımı çalışır. |
| E45 | **MAL kimliği araması (`Jikan`, adım 6) `connected.mal` şartına bağlıydı** | MAL kimliği MAL'a yazmak için değil, **Shikimori'nin `target_id`si** ve Kitsu `mappings` köprüsü için de gerekir. Kullanıcı MAL'ı bağlamamış ama Shikimori/Kitsu/Bangumi bağlamışsa bu adım atlanıyor → o kaynaklar "kimlik çözülemedi" diye sessizce atlanıyordu. | Kapı `connected.mal || connected.shikimori || connected.kitsu || connected.bangumi || connected.anilist` oldu. Arama Jikan'da **anonim** yapılır, ek yetki gerekmez; eşik aynı (normalize başlık eşitliği + yıl + `singleOrNull`). |
| E46 | `RetryInterceptor` **POST/PATCH isteklerini de** 5xx ve `IOException` durumunda yeniden deniyordu | "İstek işlendi mi?" bilinmediği için tekrar gönderim **çift kayıt** üretir (Kitsu `library-entries`, Simkl history). `retryOnConnectionFailure(true)` de OkHttp seviyesinde aynı riski taşır. | `Request.isRetrySafe()` eklendi: 5xx ve `IOException` tekrarı yalnız `GET/HEAD/OPTIONS/TRACE` için. **429 istisna**: sunucu isteği reddettiğini açıkça söyler, yan etki oluşmamıştır → tüm metotlarda tekrar denenir. |
| E47 | **E42'nin yan etkisi: sonsuz yazım döngüsü.** `isRepeating` yalnız "bayrak" farkıyla değiştiğinde AniList/Shikimori'ye durum yazılıyordu; oysa bu iki hedefte tekrar **ayrı bir durumdur** ve yazılan durum geri okunduğunda `isRepeating` hiç oluşmaz. | Her eşitleme turunda aynı PATCH sonsuza kadar tekrarlanırdı (kota + gürültü). | Yeni `CrossSyncMergePolicy.dirtyFieldsFor(current, merged, provider)`: sağlayıcı yetenekleriyle süzer ve `rewatchingAsStatus = true` olan hedeflerde durum **yalnız durumun kendisi değiştiyse** yazılır. Bayrak tabanlı hedeflerde (MAL `is_rewatching`, Kitsu `reconsuming`) bayrak farkı durum yazımıyla taşınmaya devam eder. Test: `bayrak farki AniList'e sonsuz durum yazimi uretmez`. |

### 14.2 Yazıcı sözleşmeleri — yeniden doğrulananlar (değişiklik yok)

Bunlar "şüpheli" listesindeydi; kod okundu, **doğru** bulundu, değiştirilmedi:

- **AniList puanı silme**: `scoreRaw = 0` gönderiliyor (yalnız diff'te `SCORE` varsa ve puan
  boşsa); `scoreRaw: null` **hiç gönderilmiyor** (seanime #985 → 400 "must be an integer").
  Tam yazımda boş puan gönderilmez, uzaktaki puan silinmez.
- **Kitsu** `reconsuming` yalnız `status == "current"` iken gönderiliyor (`completed` +
  `reconsuming=true` çelişkisi engellendi); boş PATCH atılmıyor; **kayıt oluşturmadan önce
  `findLibraryEntryId` ile aranıyor** ve 422 "already exists" durumunda ID bulunup PATCH'e
  düşülüyor → tekrar gönderim çift kayıt üretmez.
- **İçe aktarma tarafı**: altı içe aktarıcı da `isRepeating`'i **"şu an tekrar izleniyor"**
  olarak yazıyor (AniList `status == REPEATING`, MAL `is_rewatching || is_rereading`,
  Kitsu `reconsuming`, Shikimori `status == rewatching`); `rewatches`/`repeat`/`reconsumeCount`
  **sayıdan bayrak türetilmiyor**. Bu, §12/E27'deki kararın kodla teyidi.
- **MAL** `Repeating → completed` + `is_rewatching` (MALSync `_setStatus(Rewatching)` ile aynı);
  `Dropped → dropped`, `Paused → on_hold`, manga için `reading`/`plan_to_read`.

### 14.3 Akış (yeni)

1. `unifiedItems` → grup başına `candidates` (altı kaynağın satırları).
2. `CrossSyncMergePolicy.merge(candidates)` → **tek** birleşik değer kümesi (`status`,
   `progress` kırpılmış, `rawMaxProgress`, `volumeProgress`, `score`, tarihler, `repeatCount`,
   `isRepeating`, `isFavorite`, `total`).
3. Her kaynak için `dirtyFieldsFor(current, merged, provider)` → **yalnız o kaynağa yazılacak,
   yalnız gerçekten geride kalan, yalnız desteklenen** alanlar. Boş küme = istek yok,
   "güncellendi" mesajı yok.
4. Yazıcılar alan kümesini onurlandırır (gönderilmeyen alan hedefte değişmez).

### 14.4 Bilinçli sınırlamalar (düzeltilmedi — gerekçeli)

- **Puan silme taşınamaz.** Altı API'nin hiçbiri "puanı kaldırdım" ile "hiç puan vermedim"
  arasında ayrım sunmaz (hepsi `null`/`0` döner). Bir kaynakta puanı silmek, puanlı eski
  kaydın değerini geri getirir. Rastgele temizlemek kullanıcının diğer servislerdeki
  puanlarını sessizce silerdi. Test: `BILINEN SINIRLAMA puan silme ...`.
- **`retryOnConnectionFailure(true)` (OkHttp içi tekrar)** kapatılmadı: kapatmak GET'ler için
  DNS/rota yedekliliğini de kaybettirir. Kalan risk: gövde gönderildikten sonra bağlantı
  koparsa OkHttp POST'u bir kez daha gönderebilir. Etki alanı daraltıldı — yazma yollarının
  tamamı upsert'tir (AniList `SaveMediaListEntry`, MAL `PATCH`, Shikimori `user_rates`,
  Bangumi `POST /collections/{subject_id}`, Simkl `add-to-list`) ve Kitsu oluşturma yolu
  "önce ara, sonra oluştur" + 422 geri düşüşü ile korunur. Kalan tek etki Simkl'de mükerrer
  "play" sayısıdır (izleme geçmişi), kayıt bütünlüğü değil.
- **`isRepeating` birleşimi "herhangi biri" (`any`)**: bir istemcide açık kalan bayrak
  diğerlerine yayılır. Ters yön (bayrağı kapatma) taşınmaz; hiçbir API "bayrak kapalı"yı
  güvenilir biçimde bildirmez.
- **Not/etiket/öncelik birleştirilmez** (E44). Kayıt başına tek doğru değer yoktur.
- **TMDB** liste yazımı yoktur (salt okunur metadata kaynağı); `simkl` manga desteklemez.

### 14.5 Test kapsamı

- Yeni `CrossSyncMergePolicyTest` — **26 test**: ilerleme gerilemezliği, toplam kırpma,
  Simkl `total` istisnası, `Dropped`/`Paused` korunması, `Planned` ezmemesi, tamamlanma
  çıkarımı, tarih determinizmi ve sıra bağımsızlığı, puan seçimi + belgelenmiş sınırlama,
  tekrar/favori, kirli alan kümesi (boş küme, geri yazmama, not/etiket/öncelik üretmeme),
  sağlayıcı süzgeci, sonsuz döngü koruması.
- Önceki dosyalar: `SyncFieldDiffTest` 22, `WatchStatusMappingTest` 5,
  `MalImportManagerUrlTest` 2, `SyncResultRetrySemanticsTest` 7, `EntryAutoRulesTest`.

### 14.6 Doğrulama durumu (dürüst)

- Tree-sitter: **1390 Kotlin dosyası**, 7 bilinen yanlış pozitif (aynı liste; hepsi
  `*/` içeren KDoc ya da Room anotasyon fixture'ı).
- **`./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` ÇALIŞTIRILMADI** — bu ortamda
  JDK/Gradle/Android SDK yok. Yeni semboller (`CrossSyncMergePolicy`, `dirtyFieldsFor`,
  `isRetrySafe`) ve 26 test yalnız **statik** doğrulandı. Derleyici/test çıktısı gelirse
  düzeltmek için hazırım; bu raporda "geçti" denmemesi bilinçlidir.

---

## 15. Yedinci geçiş: dış kaynak doğrulaması (MAL-Sync, resmî API dokümanları, bağımsız istemciler)

Bu geçişte kod yalnız kendi içinde değil, **referans uygulamaların ve resmî dokümanların
kaynak koduyla** karşılaştırıldı. Okunan kaynaklar:

- `MALSync/MALSync` → `src/_provider/MyAnimeList_api/single.ts`, `.../Kitsu/single.ts`,
  `.../Shikimori/single.ts` + `.../Shikimori/helper.ts`, `.../AniList/single.ts`,
  `.../Simkl/helper.ts`, `src/_provider/singleAbstract.ts`
- Shikimori resmî API v2 dokümanı: `user_rates/create` (POST gövdesi ve **yalnız 201** dönüşü)
- Bağımsız istemci/eklenti araştırmaları: `anime-db/anime-db-plugins` (#42 Shikimori
  find-or-create, #181 MAL `is_rewatching` tuzağı), `Harborofficial/Harbor` PR #22
  (rewatch push'unda ilerlemenin sıfırlanması), AniList forum #15234 + r/anilist
  ("rewatching" durumunun anlamı, bitiş tarihinin korunması)

### 15.1 Bulunan yeni hatalar

| # | Hata | Kanıt | Düzeltme |
|---|---|---|---|
| E49 | **Shikimori'de `POST /user_rates` "create-only"dir, upsert DEĞİL.** Kodumuz "create upsert'tir" varsayıp, rate kimliği önbellekte yokken doğrudan POST atıyordu. | Resmî doküman: create yalnız `201` döner; bağımsız istemci notu: "POST — create-only; обновление — PATCH/PUT /:id … find-or-create". | `ShikimoriApiClient.fetchUserRateId()` eklendi (`GET /v2/user_rates?user_id&target_id&target_type`). Artık: kimlik önbellekte → PATCH; yoksa **önce arayıp** varsa PATCH, yoksa POST. POST yine başarısız olursa (yarış) bir kez daha ID aranıp PATCH'e düşülür. Kayıt bulununca **her zaman PATCH** (POST'un "zaten var" davranışına bağımlılık kaldırıldı). |
| E50 | **MAL, ilerleme yazıldığında `is_rewatching` bayrağını kendisi `false`'a çeker.** Yalnız `num_watched_episodes` gönderdiğimizde kullanıcının "şu an tekrar izliyorum" durumu sessizce siliniyordu. | `anime-db-plugins` #181: "Push без is_rewatching=false: флаг пересмотра переживает push … следующий pull снова возвращает Watching"; MALSync tüm `my_list_status` nesnesini gönderdiği için tuzağa düşmüyor. | `PROGRESS` yazılırken, tekrar sürüyorsa `is_rewatching=true` (`is_rereading=true`) aynı gövdede gönderilir. `false` göndermek gereksiz (sunucunun varsayılanı zaten `false`). |
| E51 | **Tekrar sırasında ilerleme `max` ile alınıyordu.** 12/12 bitmiş yapımı yeniden izlemeye başlayan kullanıcıda MAL 12, AniList 2 bildirir → AniList'e 12 yazılır, tekrar anında "bitmiş" görünür. | `Harbor` PR #22: "a plain CURRENT/'watching' push would reset the entry's progress, because both trackers set progress to whatever is sent"; r/anilist: "You set it to rewatching … you can add each episode again **without losing your previous watch info**". | Tekrar sürerken (`status == REPEATING` ya da `isRepeating` + `Watching/Completed`) **en güncel tekrar satırının** ilerlemesi esastır; `max` yalnız raporlamada (`rawMaxProgress`) kalır. Ayrıca "ilerleme gerilemez" kuralı gereği diğer kaynaklara geri yazılmaz. |
| E52 | **İlerleme toplamı yakaladığında durum `Completed`'a yükseltiliyordu; bu, tekrar sırasında AniList'in `REPEATING` durumunu ve tekrar ilerlemesini eziyordu.** | Aynı kaynaklar; AniList'te REPEATING ayrı bir durumdur ve ilerleme yeniden sayılır. | `resolveStatus`: herhangi bir kaynak "şu an tekrar izleniyor" diyorsa sonuç `Repeating`'dir; toplam-tabanlı `Completed` çıkarımı **tekrar sürerken uygulanmaz**. |
| E53 | **Durum, hedefin sözlüğüne çevrilmeden karşılaştırılıyordu.** `Repeating`, MAL'de `completed`, Kitsu'da `current`, Bangumi/Simkl'de hiçbir şey demektir. Karşılaştırma ham tutulduğu için ya gereksiz yazım ya da (Simkl'de) "tamamlandı"yı "izliyorum"a çevirme riski vardı. | MALSync `_setStatus` her sağlayıcıda kendi sözlüğüne çevirir; Simkl `translateList` içinde `dropped` yok, `notinteresting` var. | `ProviderCapabilities.rewatchModel` (`AS_STATUS` / `AS_FLAG` / `UNSUPPORTED`) + `repeatingStatus` eklendi. `dirtyFieldsFor` durumu **hedefin sözlüğünde** karşılaştırır: MAL `completed`, Kitsu `current`; Bangumi/Simkl'de tekrar yüzünden durum **değiştirilmez**. |
| E54 | **Bayrak farkı, bayrak taşımayan hedeflerde de `STATUS` üretiyordu → her turda aynı istek (sonsuz yazım döngüsü).** | Kendi kodumuzun analizi (E47'nin devamı): Simkl/Bangumi `Repeating`i ifade edemez, yazılan durum geri okununca fark hiç kapanmaz. | `STATUS`, bayrak farkından **yalnız `AS_FLAG` hedeflerde** eklenir (MAL, Kitsu). Regresyon testi: `tekrar ifade edemeyen hedefin durumu degistirilmez`, `MAL_da baslayan tekrar AniList'e REPEATING olarak yazilir ve bir kez yazilir`. |

### 15.2 Referansla doğrulananlar (değişiklik yok)

- **Puan ölçekleri**: AniList `_setScore(score) = score * 10` (POINT_100) → bizim
  `SyncScores.aniListRaw = 0..10 * 10` **aynı**. Kitsu `ratingTwenty = ratingHundred / 5`
  → bizim `tenPoint * 2` **aynı**. Kitsu'da MALSync puanı 5'in altında `1` gönderiyor
  (API alt sınırı 2 iken); bizim `kitsuTwenty` **2'ye sabitliyor** — referanstan daha doğru.
- **Kitsu tekrar**: MALSync `reconsuming && status === 'current'` → Rewatching; bizim
  `isRepeating` tanımı ve `reconsuming = reconsuming && status == "current"` kapımız aynı.
- **MAL yazımı**: `PATCH my_list_status` bir **upsert**'tir (find-or-create gerekmez — bizim
  kod da öyle); `start_date`/`finish_date` temizlemek için `0000-00-00`; `Repeating →
  completed + is_rewatching` (MALSync `_setStatus`).
- **Shikimori tarihleri**: MALSync `datesSupport = false`; bizim yetenek matrisinde
  Shikimori'de `START_DATE`/`END_DATE` **yok** — tutarlı. Durum sözlüğü (planned, watching,
  rewatching, completed, on_hold, dropped) doğrulandı.
- **Simkl**: `notinteresting` = Dropped, tarih/tekrar alanı yok — kodda aynı.
- **Bangumi**: `type` 1..5 (wish/collect/do/on_hold/dropped) eşlemesi doğru; `rate` 0..10;
  `Repeating → DOING`.
- **Tekrar bitince bitiş tarihi değişmez** (r/anilist): birleştirmede `endDate = en geç`
  kuralı eski bitiş tarihini korur — istenen davranış.

### 15.3 Test kapsamı

`CrossSyncMergePolicyTest` → **29 test** (yeni: tekrar sürerken ilerleme, MAL→AniList
REPEATING yayılımı ve döngüsüzlüğü, tekrar ifade edemeyen hedef, MAL/Kitsu bayrak taşıma).

### 15.4 Kalan riskler (dürüst)

- **Derleme/test çalıştırılmadı** (JDK/Gradle/Android SDK yok). Tüm doğrulama statik.
- Kitsu `filter[userId]` / `filter[animeId]` biçimi: MALSync `filter[user_id]` kullanıyor;
  Kitsu (jsonapi-resources + humps) iki biçimi de kabul ediyor, ancak **canlı doğrulanmadı**.
- `retryOnConnectionFailure(true)` (OkHttp içi tekrar) kapatılmadı; kalan etki Simkl'de
  mükerrer "play" sayısıdır (kayıt bütünlüğü değil).
- Shikimori'de **puanı 0 yapmak** puanı siler; biz puanı yalnız diff'te gönderiyoruz, ancak
  "puanı kaldır" (null) ile "hiç puan vermedim" ayrımı Shikimori'de de yapılamıyor (§14.4).

---

## §16 — Geçiş 8: Dış Kaynak Doğrulaması (MALSync / MAL şeması / topluluk notları)

Bu geçişte amaç "kod kendi içinde tutarlı mı" değil, **kod üçüncü taraf gerçek
uygulamalarla aynı şeyi mi yapıyor** sorusuydu. Aşağıdaki kaynaklar bu geçişte
okunmuştur (dosya + satır düzeyinde karşılaştırma yapıldı):

| Kaynak | Okunan yer | Çıkarılan kural |
|---|---|---|
| MALSync `src/_provider/MyAnimeList_api/single.ts` | `_update`, `_sync`, `_getStatus`, `_setStatus`, `_setAbsoluteScore` | Yazma **PUT** + tüm `my_list_status` nesnesi; tarih `?? ''`; `_getStatus` tekrar bayrağını **yalnız `completed` ile** `Rewatching`'e çevirir; `num_episodes_watched` → `num_watched_episodes` eşitlemesi ("// Handle api bug") |
| MALSync `src/_provider/AniList/single.ts` | `_update`, `_sync`, `_delete` | Puan **`score(format: POINT_100)`** ile okunur (profil formatı ne olursa olsun); `mediaId` ile yazar; `DeleteMediaListEntry(id: <liste kaydı id>)` |
| MALSync `src/_provider/Kitsu/single.ts` | `_getStatus`, `_setStatus`, `_getScore`, `_setScore`, `_getVolume` | `reconsuming && status === 'current'` = tekrar; `ratingHundred = ratingTwenty*5`; puan 0 → `ratingTwenty = null`; cilt = `volumesOwned` |
| SuperMarcus `myanimelist-api-specification` | `MyListStatusObject` | `is_favorited` **şemada YOK**; ilerleme alanı `num_episodes_watched` |
| `Daiya404/AL-Additions` (Troubleshooting) | — | "MAL bir değeri sürekli reddediyorsa o alan bastırılır, aksi hâlde **yazma döngüsü** oluşur" |
| `bigspawn/anilist-mal-sync` | README | "nil source date never overwrites a set target date"; bitiş tarihi yalnız `Completed` iken; MAL'de favori **yazma ucu yok** |

### 16.1 Bu geçişte doğrulanan (kod zaten doğruydu)

| # | İddia | Doğrulama |
|---|---|---|
| V1 | MAL'a tarih temizleme `0000-00-00` ile gider, `''` 400 döner | `MalSyncManager` §E49 yolu; MALSync `?? ''` gönderdiği için **her iki biçim de denenebilir** (aşağıdaki E62) |
| V2 | Kitsu puanı 2..20 (`tenPoint*2`), 0 → `null` | `SyncScores.kitsuTwenty` + `KitsuSyncManager` L198-199; MALSync `ratingHundred`/`ratingTwenty*5` ile birebir |
| V3 | Shikimori'ye tarih **hiç** gönderilmez | `ProviderCapabilitiesCatalog.shikimori.supports` içinde START_DATE/END_DATE yok; yazıcı yalnız status/score/progress/rewatches/notes |
| V4 | MAL tekrar bayrağı `is_rewatching`/`is_rereading` (manga ayrı ad) | `MalSyncManager` STATUS bloğu; `is_repeating` hiç gönderilmiyor; import iki adı da okuyor |
| V5 | AniList puanı profil formatından bağımsız okunur | `AniListImportManager` L110-111: `score(format: POINT_10_DECIMAL)` **ve** `scoreRaw: score(format: POINT_100)` |
| V6 | Kitsu'da tekrar yalnız `current` ile yazılır | `KitsuSyncManager` L188-191 `reconsuming && status == "current"` |
| V7 | Kitsu cilt alanı `volumesOwned` **değildir** | `ProviderCapabilitiesCatalog.kitsu` VOLUME_PROGRESS içermiyor → çapraz eşitleme cilt yazmıyor |
| V8 | AniList/Kitsu'da `tags` diye bir alan yok (MALSync etiketleri `notes`'a gömüyor) | `ProviderCapabilitiesCatalog` bu iki sağlayıcıda TAGS içermiyor → notlar etiketle ezilmiyor. Bu **bilinçli** bir sapma: MALSync etiket/not ayrımını kaybeder. |
| V9 | "Bağlı değil" bir değişiklik kaybıdır, "kapalı" değildir | `SyncResult.needsRetry` + `connectionGapSources`; `SyncResultRetrySemanticsTest` (7 test) ile sabitlendi |

### 16.2 Bu geçişte bulunan **yeni** hatalar

| # | Hata | Etki | Düzeltme |
|---|---|---|---|
| **E62** | MAL'da tarih temizleme sentinel'i (`0000-00-00`) 400 ile reddedilirse **istekteki tüm alanlar** (durum/ilerleme/puan) kayboluyordu | Sessiz eşitleme kaybı + kuyrukta 5 kez tekrar | `patchWithDateClearFallback` + `stripDateClears`: 400'de yalnız tarih temizleme parametreleri çıkarılıp **bir kez** yeniden denenir; başka alan kaybolmaz |
| **E63** | AniList ve Kitsu yazıcıları "tarih **ayrıştırılamadı**" ile "tarih **boşaltıldı**"yı ayırt etmiyordu. İçe aktarmadan sızmış `"2016"` / `"2016-05"` gibi parsiyel tarihte hedefteki tarih **siliniyordu** | Kullanıcı tarihe dokunmadığı hâlde AniList/Kitsu'daki başlangıç-bitiş tarihi kaybolur | `SyncDates.action(raw, parsed, fullWrite)` → `WRITE / CLEAR / SKIP`; iki yazıcı da bu tek kararı kullanıyor. `SyncDatesActionTest` (4 test) parsiyel tarihin **asla** temizleme üretmediğini sabitliyor |
| **E64** | MAL `my_list_status` içindeki ilerleme alanının adı **uca göre** değişiyor (`num_episodes_watched` ↔ `num_watched_episodes`); import tek ad okuyordu | Yanıt biçimi değişirse ilerleme **0** okunur ve 0 olarak beş sağlayıcıya yazılır (sessiz veri kaybı) | `MalImportManager` iki adı da okur (`maxOf`); yazma adı `num_watched_episodes` olarak korunur |
| **E65** | MAL liste isteğinde `is_favorited` alanı isteniyordu; bu alan MAL v2 `MyListStatusObject` şemasında **yok** | Sunucu bilinmeyen alanı reddederse `400 invalid field` → **tüm MAL içe aktarma** çalışmaz | `fetchPageWithFieldFallback`: 400'de `is_favorited` seçiciden çıkarılıp sayfa tekrar denenir (sonraki sayfalarda da atlanır). `MalImportManagerUrlTest.favoritedFieldCanBeDroppedWhenServerRejectsIt` testi eklendi |

### 16.3 Bilinçli olarak yapılmayanlar (gerekçeli)

1. **Alan bazlı "reddedilen değer bastırma"** (AL-Additions deseni): MAL bir alanı sürekli
   reddediyorsa o alanın bir süre gönderilmemesi. Bizde döngü riski yok çünkü (a) alan farkı
   **yerel DB satırına** göre hesaplanıyor — yazım başarısız olsa da satır değişmiyor,
   dolayısıyla aynı turda ikinci kez denenmiyor; (b) kuyruk en fazla 5 kez deneyip bekletiyor;
   (c) çapraz eşitleme kullanıcı tetiklemeli. Yine de uzun vadede eklenmesi gereken bir
   koruma olarak not edildi.
2. **Shikimori favori**: Shikimori'de `POST /api/v2/favorites` var; ancak hedef `linked_id`
   Shikimori'nin kendi anime id'sidir ve mevcut yazıcı yalnız user-rate bağlamında MAL id
   taşıyor. Yanlış id ile favori eklemek geri alınamaz olduğu için **bu geçişte eklenmedi**;
   ayrı bir kimlik çözümü turu gerektirir. (AniList favori uçtan uca çalışıyor, MAL/Kitsu/
   Bangumi/Simkl'de yazma ucu yok.)
3. **MALSync'in "etiketleri `notes`'a göm" davranışı** kopyalanmadı (V8).
4. **`finish_date` yalnız `Completed` iken yazılır** (bigspawn kuralı) uygulanmadı: bizde
   tarih yalnız **kullanıcı değiştirdiğinde** (diff) gönderildiği için "yarım kalmış bitiş
   tarihi" senaryosu oluşmuyor; ayrıca `Completed` dışı bir durumda bitiş tarihi yazımını
   engellemek, kullanıcının bilerek girdiği değeri düşürürdü.

### 16.4 Test kapsamı (bu geçişte eklenen)

- `SyncDatesActionTest` — 4 test (WRITE / CLEAR / SKIP; parsiyel tarih ASLA CLEAR değil)
- `MalDateClearBodyTest` — 4 test (yalnız sentinel süzülür; gerçek tarihler korunur; gövde boşalırsa istek atılmaz)
- `MalImportManagerUrlTest` — +1 test (`is_favorited` alanı düşürülünce seçici bozulmuyor)

### 16.5 Dürüst durum

Bu depo **derlenmedi ve test edilmedi** (ortamda JDK/Gradle/Android SDK yok). Doğrulama
`python3 /tmp/kcheck.py` (tree-sitter sözdizimi) ile yapıldı: 1392 `.kt` dosyası, 7 bilinen
yanlış pozitif dışında hata yok. Çalıştırılması gereken komut:

```
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```

---

## §17 — Geçiş 9: Birleştirme kuralının kendi içinde denetimi

Bu geçişte odak, tek tek sağlayıcılar değil **altı kaynağı tek karara indiren kural**
(`CrossSyncMergePolicy`) oldu. Kural yanlışsa bütün sağlayıcı yazıcıları doğru olsa bile
sonuç yanlış olur. Üç mantık hatası bulundu.

| # | Hata | Senaryo | Etki | Düzeltme |
|---|---|---|---|---|
| **E66** | Tekrar sürerken ilerleme **hiç** geri yazılmıyordu | MAL'da 12/12 bitmiş yapım tekrar izlenmeye başlandı (2. bölüm) → AniList `REPEATING` oldu ama ilerlemesi 12'de kaldı | AniList'te tekrar "12/12" görünür; tekrar ilerlemesi hiç yansımaz | `dirtyFieldsFor`: `AS_STATUS` hedeflerde (AniList `REPEATING`, Shikimori `rewatching`) ve birleşik durum tekrar ise, ilerleme farkı **geri yönlü de** yazılır. Bayrak taşıyan hedeflerde (MAL `is_rewatching`, Kitsu `reconsuming`) geri yazım YOK — MAL'de ilerlemeyi düşürmek bayrağı sıfırlar |
| **E67** | "Herhangi bir kayıt tekrar diyor" kuralı bayat satırla kilitleniyordu | Kullanıcı tekrarı MAL'da bitirdi (bayrak kapandı, daha yeni); AniList'te bayat `REPEATING` kaldı | Birleşim sonsuza kadar `Repeating`; her turda MAL'a `is_rewatching=true` **geri yazılır**, tekrar asla bitmez | Tekrar bilgisi ancak kendisinden **daha yeni** bir kayıt aksini söylemediği sürece geçerli (eşitlikte tekrar kazanır: tekrarı başlatan tur kendi kendini iptal etmesin) |
| **E67b** | `merged.isRepeating` "herhangi biri" iken `merged.status` "en yeni kazanır" idi → iki alan çelişebiliyordu | Yukarıdaki senaryo | Bayrak `true`, durum `Completed` → bayrak taşıyan hedefe gereksiz durum yazımı ve `is_rewatching` yeniden açılması | `isRepeating = (status == Repeating)`; tekrar satırlarının ilerlemesi de yalnız birleşik durum tekrar iken dikkate alınır |
| **E68** | Kırpma toplamı **aday sırasına** bağlıydı (`firstOrNull`) | AniList 12 bölüm, MAL 13 bölüm bildiriyor | Grup sırası değişince kırpma değeri değişir; turdan tura ilerleme bir aşağı bir yukarı yazılır (deterministik değil) | Sabit öncelik: MAL → AniList → Kitsu → Shikimori → Bangumi; aynı kaynakta birden çok değer varsa en büyüğü |

### 17.0 Aynı geçişte bulunan ek hata

| # | Hata | Senaryo | Etki | Düzeltme |
|---|---|---|---|---|
| **E69** | MAL'da kısmi alan yazımı **kayıt oluşturuyordu** | Satır MAL kapalıyken/bağlı değilken eklendi; sonra MAL bağlandı ve kullanıcı yalnız puanı değiştirdi → `fields = {SCORE}` | `PATCH my_list_status` upsert olduğu için MAL'de `plan_to_watch` + `0/12` ile **yarım kayıt** doğar; sonraki turlarda durum/ilerleme "değişmediği" için asla düzeltilmez | Durum alanı içermeyen (kayıt oluşturabilecek) yazımlardan önce `GET ?fields=my_list_status` ile kaydın varlığı doğrulanır; **yoksa** bir kez tam yazıma geçilir. Ek istek yalnız bu nadir yolda (puan/not/tarih düzenlemesi) atılır; durum içeren her yazımda ek istek YOK. Doğrulama başarısız olursa (ağ) davranış eskisi gibi kalır |

### 17.0b Model koşturmasıyla bulunan iki hata (E70, E71)

Birleştirme kuralı, Kotlin'deki mantığın **birebir Python modeli** olarak yazıldı ve
rastgele 4000 aday kümesiyle koşturuldu (determinizm, sabit nokta/idempotans, monotonluk,
toplam–ilerleme tutarlılığı). İlk koşuda 4000 kümenin **126'sında** sonuç aday sırasına
göre değişiyordu:

| # | Hata | Kök neden | Etki | Düzeltme |
|---|---|---|---|---|
| **E70** | Birleşik ilerleme, adayların **sırasına** bağlıydı | İki kaynak aynı saniyede güncellendiğinde `maxByOrNull { updatedAt }` **ilk rastlanan** satırı seçer; `sortedWith` kararlı olduğu için `resolveStatus`/`resolveScore` de eşit anahtarda sıraya bağlı kalıyordu | Aynı veriden iki farklı birleşim → ilerleme 6 ↔ 13 arasında zıplayıp **her iki yöne de yazım** üretebilir | Sıralamaya son anahtar olarak satır kimliği (`id`) eklendi; tekrar satırı seçimi `newestRewatch()` yardımcısına alındı (en yeni → en yüksek ilerleme → kaynak → id) |
| **E71** | Öncelikli kaynağın toplamı eldeki ilerlemeyi taşımıyorsa ilerleme **kırpılarak geri yazılıyordu** | `totalOf` yalnız öncelik sırasına bakıyordu | Ör. AniList 12 bölüm, Bangumi 13 bölüm ve ilerleme 14 → birleşim 12'ye kırpılıyor; yeni bir kaynak eklendiğinde ilerleme 13 → 12 düşebiliyor | Öncelikli toplam, en yüksek ilerlemeyi taşımıyorsa adaylardaki **en büyük** toplam kullanılır (deterministik; maksimum sıradan bağımsız) |

Düzeltmeden sonra aynı koşturma: **sıra bağımlılığı 0, durum/ilerleme idempotans ihlali 0,
monotonluk ihlali 0**. Kalan 66 "toplam düştü" vakası, hiç toplam yokken bir kaynağın ilk kez
toplam bildirmesi (14 → 12 kırpma) ve **beklenen** davranış: kırpma olmasa MAL/Kitsu yazımı
400/422 ile tümden düşer; daha ileride olan sağlayıcıya geri yazım zaten yapılmaz.

> Not: Bu koşturma Kotlin kodunu çalıştırmaz; **kuralın algoritmasını** doğrular (model
> tabanlı test). Kotlin tarafındaki ifade ile model arasındaki fark riski raporda açıkça
> kabul edilmiştir.

Aynı hata sınıfı diğer sağlayıcılarda **yok**, çünkü onlarda kaydın varlığı yazımdan önce
kanıtlanıyor: AniList `aniListEntryId == null → writeAll`, Kitsu `findLibraryEntryId`
(ardından 422 → bul → PATCH), Shikimori find-or-create, Bangumi `PATCH → 404 → upsert`
(upsert her zaman `type` taşır), Simkl `add-to-list` her zaman `to` (durum) taşır.

### 17.1 Bu geçişte doğrulanan (değişiklik gerekmedi)

- **Simkl notları**: `private_memo` yolu **zaten** var (`SimklSyncManager` L520-544 +
  `SimklApiClient` L1057); not değişimi add-to-list yazımını tetikliyor, boşaltma `""` ile
  gidiyor. (Önceki geçiş notum "Simkl not desteklemiyor" **yanlıştı**, düzeltildi.)
- **Sağlayıcı hız sınırlayıcı**: `PlatformRateLimiter` platform başına mutex tutuyor; farklı
  platformlar birbirini **beklemiyor** → altı kaynak gerçekten eşzamanlı (kabul ölçütü).
  Aynı platform içinde seri olması doğru (429 üretmemek için). Yan not: `notifyRateLimited`
  hem `lastCallTime`i ileri atıyor hem `delay` yapıyor (çift bekleme); davranışı bozmadığı
  için değiştirilmedi.
- **`SyncFieldDiff`** 14 alanın tamamını karşılaştırıyor; boşaltma (`null` → `""`) fark
  sayılıyor, `isRepeating` değişimi `STATUS`ı da işaretliyor (bayrak hedefte durumla taşınır).
- **Simkl/Kitsu/Shikimori/Bangumi/MAL/AniList** yazıcılarının alan kapıları
  (`fullWrite` / `SyncField.X in fields`) tek tek okundu; boş değer yalnız kullanıcı
  alanı boşalttığında gönderiliyor.

### 17.2 Test kapsamı

`CrossSyncMergePolicyTest` 29 → **36 test**. Yeni testler:
- `tekrar sirasinda AniList ilerlemesi geri sayilir` (E66)
- `bayrakli hedefte tekrar sirasinda ilerleme geri yazilmaz` (E66'nın sınırı)
- `biten tekrar bayat REPEATING satirini ezmez` (E67 + E67b)
- `esit zaman damgasinda tekrar kazanir` (E67 eşitlik kuralı)
- `toplam kaynagi aday sirasindan bagimsizdir` (E68)
- `esit zaman damgasinda tekrar satiri da deterministik secilir` (E70)
- `toplam eldeki ilerlemeyi tasimiyorsa buyuk toplam kullanilir` (E71)

### 17.3 Kalan açık riskler (dürüst liste, önem sırasıyla)

1. **Kademeli yazım yok**: bir kaynakta kimlik çözümü + yazım + favori sorgusu arka arkaya
   gidiyor; `PlatformRateLimiter` beklemesiyle toplam süre 30 sn'lik kaynak bütçesini
   aşarsa sonuç `OUTCOME_TIMEOUT` olur. OkHttp `callTimeout` (25 sn) bloke çağrıyı kesiyor
   ama **çok adımlı** akışta ikinci/üçüncü istek hiç başlamayabilir. Bu bir "yanlış veri"
   hatası değil, "eksik yazım" hatasıdır ve kuyruk yeniden dener.
2. **`updatedAt` kaynak saatine dayanır**: farklı sağlayıcıların saat kayması "en yeni
   kazanır" kuralını teorik olarak etkileyebilir (kabul edilen risk; alternatifi yerel
   işlem zamanı tutmak ve bunu her sağlayıcı için ayrı saklamak olurdu).
3. **Shikimori favori** ve **Bangumi favori** yazımı yok (gerekçe §16.3).
4. **`MutableList` alanı yok**: `tags` düz metin olarak tutuluyor; sıra değişikliği fark
   üretir (yalnız MAL ve Bangumi'ye yazıldığı için etkisi sınırlı).
5. **E69'un doğrulama sorgusu** birim testine uygun değil (ağ): kod yolu okundu ve
   mantığı yukarıda yazılı; entegrasyon testi gerekir.
6. Bu geçişte de **derleme/test çalıştırılmadı** (ortamda JDK/Gradle yok).

---

## §18 — Geçiş 10: kardeş satır aynası + hedef sözlüğünde tekrar (E57, E72)

Bu geçişte iki ayrı hata sınıfı kapatıldı. İkisi de "alan değeri yanlış" değil,
**veri katmanının kendi kuralını delmesi** türündendir.

### 18.1 E57 — kardeş satır aynası birleştirme kurallarını deliyordu

`MediaEntryRepository.mirrorUserFieldsToSiblings` kullanıcı alanlarını (durum, puan,
ilerleme, cilt, tarihler, not, etiket, favori, gizlilik, öncelik, tekrar, `updatedAt`)
**koşulsuz** kopyalıyordu. Üç ayrı sonucu vardı:

| # | Delinen kural | Senaryo | Sonuç |
|---|---|---|---|
| 1 | "İlerleme asla gerilemez" | AniList satırı 12, Kitsu satırı 10; kullanıcı Kitsu'da 11'e çekti | Ayna AniList satırını da 11 yapıyor → birleşim 11 → AniList'e **12 → 11 geri yazım** (E71'in veri katmanındaki eşdeğeri) |
| 2 | "Yalnız değişen alan yazılır" | Kullanıcı yalnız puanı düzenledi | Ayna `status`'u da kopyalıyor → `Dropped`/`Paused` yapışkan kararı ve tamamlanmış kayıt başka duruma çekiliyor |
| 3 | "En yeni kullanıcı işlemi kazanır" | Ayna `updatedAt`'i de eziyor | Hiçbir alanı değişmeyen kardeş satır "az önce düzenlendi" gibi damgalanıyor → birleştirme yanlış satırı en yeni sanıyor |

**Düzeltme.** Ayna saf ve test edilebilir bir kurala taşındı: `data/auth/SiblingMirrorPolicy.kt`.
- Ayna yalnız **gerçekten değişen alanları** taşır (`changed: Set<SyncField>`); dokunulmayan
  alanlar kardeş satırda kalır.
- **Monotonluk:** ilerleme, cilt ilerlemesi, tekrar sayısı ve tekrar değeri `max` ile taşınır —
  asla düşürülmez.
- **Yapışkanlık:** durum açıkça değiştirilmediyse `Dropped`/`Paused` ezilmez; ilerlemesi olan
  satır `Planned`a çekilmez. Durum **açıkça** değiştirildiyse (diff'te `STATUS` varsa) kullanıcı
  kararı aynen taşınır.
- **Puan silme yayılmaz:** `null` puan kardeş satıra yazılmaz (bir kaynağın gerçek puanı yerelde
  silinirse sonraki birleştirme o kaynağa başka bir puan yazar — E41 ile aynı gerekçe).
- **Tarihler:** tam yazımda birleştirme kuralıyla aynı (başlangıç = en erken, bitiş = en geç);
  açık değişiklikte aynen (boşaltma dahil).
- **`updatedAt` kopyalanmaz.**
- Değişmeyen satır Room'a hiç yazılmaz (gereksiz akış/`entriesFlow` yenilemesi yok).

### 18.2 E72 — MAL'de yayında olan yapım "tamamlandı"ya çevriliyordu (ve sonsuz yazım)

`ProviderCapabilities.statusFor` birleşik `Repeating` durumunu her `AS_FLAG` hedefte
`repeatingStatus`a zorluyordu (MAL → `Completed`). Ama MAL `is_rewatching` bayrağı durumdan
**bağımsızdır** ve içe aktarma (E22) `watching` + bayrak üretir. Sonuç:

1. Yayında olan bir yapım tekrar izlenirken MAL'de `watching` → bizim yazdığımız `completed`
   ile **veri bozuluyordu** (MAL'ın kendi modeline aykırı).
2. `dirtyFields` içe aktarılan `watching` ile hedef `completed`ı hiç eşitleyemediği için
   **her turda** aynı PATCH atılıyordu (E47/E54/E67 sınıfı sonsuz yazım).

**Düzeltme.** `ProviderCapabilities.rewatchFlagNeedsRepeatingStatus` eklendi.
- MAL (ve bayrağı durumdan bağımsız taşıyan hedefler): durum **korunur**, yalnız bayrak yazılır.
  `repeatingStatus` yalnız durumu olmayan (`Planned`) satırda kullanılır.
- Kitsu: `reconsuming` yalnız `status == current` iken gönderildiği için (E26) durum
  `repeatingStatus`a (`Watching` = `current`) çekilir — aksi hâlde bayrak sunucuda oluşmaz.

### 18.3 Bu geçişte eklenen testler

| Dosya | Test | Ne sabitliyor |
|---|---|---|
| `SiblingMirrorPolicyTest` | **19** | ilerleme/cilt/tekrar gerilemez; puan düzenlemesi durumu değiştirmez; açık durum değişikliği yapışkanlığı ezer; `Planned`a geri çekilme yok; boş puan taşınmaz; tarih birleştirme; `updatedAt` korunur; bayat tekrar bayrağı temizlenir; boş küme/aynı satır dejenere durumları |
| `RewatchTargetVocabularyTest` | **12** | MAL/Kitsu/AniList tekrar hâlinde `dirtyFieldsFor` **boş**; MAL `watching` korunur; Kitsu `current`a çekilir; Bangumi/Simkl tekrar yüzünden durum yazmaz; tekrar bitince bayrak kapanır; **tek kaynaklı grupta sağlayıcı kendine yazmaz** (6 sağlayıcı × 10 durum tablosu) |

`data/auth` test sayısı: **145 → 176** (20 dosya).

### 18.4 Dürüst durum

- Bu geçişte de **derleme/test çalıştırılmadı**: ortamda JDK/Gradle/Android SDK yok.
  Doğrulama = satır satır kod okuması + sözdizimi taraması (`/tmp/kcheck.py`, 4 dosya, 0 hata)
  + yeni testlerin mantık yürütmesi.
- Kullanıcının çalıştırması gereken komut değişmedi:
  `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
- Açık kalan: §17.3 listesi (kademeli yazım bütçesi, saat kayması, Shikimori/Bangumi favori,
  `tags` düz metin, E69 entegrasyon testi).

## §19 — Geçiş 11: kuyruk testleri (T02) + kuyrukta sessiz alan kaybı (E73, E74, E75)

**Devralma notu.** Bu geçiş yeni dalda (`arena/71836ee9-kitsugi-beta`) yapıldı. Dal,
`main` (`26bb044`) üzerinden açılmıştı; §18'in commit'i `0762faf` onun torunu olduğu
için dal **fast-forward** ile devir noktasına getirildi (başka dala geçilmedi, başka
dala yazılmadı). Doğrulanan başlangıç durumu: HEAD `0762faf`, `appVersionName = "2.4.340"`,
`data/auth` içinde 20 dosya / 176 `@Test`, E57 (`SiblingMirrorPolicy`) ve E72
(`rewatchFlagNeedsRepeatingStatus`, `SyncField.kt:163`) kodda mevcut.
`03_HATA_KAYDI`'nda `AÇIK` satır yoktur; tek `DOĞRULANMADI` satır E60'tır (Kitsu
puan temizleme/kırpma çalışma zamanı — T09 kapsamında açık kalıyor).

Bu geçişin konusu listedeki ilk iş (**T02**: `PendingSyncDrainer` kuyruk testleri) idi.
Kuyruğu test edilebilir hâle getirirken zincirin tamamı (Drainer ↔ DAO ↔
`MediaEntryRepository` ↔ `SyncResult.needsRetry`) satır satır okundu ve üç hata bulundu.

### 19.1 E73 — başarılı kısmi yazım, kuyruktaki gönderilmemiş alanları siliyordu

Kuyruk işleri alan kümesiyle saklanır (`_syncFields`): çevrimdışıyken yalnız not
değiştirilirse iş `{NOTES}` taşır. Doğrudan (çevrimiçi) yazım başarılı olduğunda
`MediaEntryRepository.syncEntryIfPossible` aynı satırın kuyruk işini
`removeFromQueueIfPresent(entry, "UPDATE")` ile **alan kümesine hiç bakmadan**
siliyordu (eski `MediaEntryRepository.kt:441` + eski `L568` gövdesi).

| Adım | Olay | Eski sonuç |
|---|---|---|
| 1 | Çevrimdışı: kullanıcı notu değiştirdi → diff `{NOTES}` → yazım başarısız → kuyrukta `{NOTES}` işi | not yalnız yerel DB'de + kuyrukta |
| 2 | Çevrimiçi: kullanıcı ilerlemeyi değiştirdi → diff `{PROGRESS}` (`update()` L254/L272) → yazım başarılı | ilerleme altı kaynağa gitti |
| 3 | Başarı yolu kuyruktaki işi (alanlarına bakmadan) sildi | **not hiçbir hedefe gitmeden kayboldu**; yerel DB'de durduğu için kullanıcı fark etmez (A6 ihlali) |

**Düzeltme.** Alan düşme kararı saf kural olarak `PendingSyncPolicy.remainingFields`e,
uygulaması kuyruk JSON'unun sahibine (`PendingSyncDrainer.removeSyncedFields`) taşındı:
başarılı doğrudan yazım, kuyruktaki işten **yalnızca o turda gönderilen alanları**
düşer; geri kalan alanlar `_syncFields` güncellenerek kuyrukta kalır (anlık görüntüdeki
değerleriyle — aradaki her değişiklik ya doğrudan gönderilip düşülmüş ya da yeni
`enqueue` ile görüntüyü tazelemiştir). `DELETE` yolu `SyncField.ALL` geçirir ve işi
bütünüyle düşürür (eski davranış, silme için doğru). Aynı denetimde, hiç çağrılmayan
ama bütçesi dolan işleri **toplu silen** `PendingSyncDao.deleteStale` kaldırıldı:
tavan yalnız otomatik denemeyi durdurur, kayıt silinmez sözleşmesine aykırı bir
metodun DAO'da hazır durması başlı başına riskti.

### 19.2 E74 — yetki hatası istisna olarak geldiğinde deneme sayacını yakıyordu

Sözleşme: *"yetki hatası sayaç artırmaz."* Oysa hem drainer (eski `L125`) hem
repository (eski `L466`, `L514`) yetki kontrolünü yalnız `SyncResult.errors`
metinlerinde yapıyordu. Yazım çağrısının kendisi fırlatırsa (`prepare()` içinde
token/ayar okuma, DataStore I/O vb.) `syncResult == null` olur, hata metni boş
kalır ve (a) kuyruk sayacı yetki hatasında bile artar, (b) kullanıcıya "oturum
süresi doldu" yerine "çevrimdışı kaydedildi" gösterilirdi.

**Düzeltme.** Kontrol tek yere toplandı: `PendingSyncPolicy.isAuthError(...)`
(üç kopya silindi; alt dizgeler `401` / `token geçersiz` / `Unauthorized` aynen
korundu) ve fırlatılan istisnanın mesajı da karara girdi
(`PendingSyncPolicy.decision(..., thrownMessage)`).

### 19.3 E75 — birleşik kayda taban seçimi aday sırasına bağlıydı (AuthViewModel)

`AuthViewModel.kt` toplu eşitleme yolunda `val newest = candidates.maxByOrNull
{ it.updatedAt }` (eski L1222) — E70'in yasakladığı eşitlik-bozucusuz seçim.
`newest`, birleşik kaydın tabanıdır (başlıklar + `bestNotes/bestTags/bestPriority`);
eşit zaman damgasında (içe aktarılmış satırlar aynı saniyeyi taşır) seçim aday
sırasını miras alıyordu. Sıra `CrossSyncMergePolicy` ile aynı yapıldı:
`updatedAt` (azalan) → ilerleme (azalan) → kaynak adı → satır `id`.

Aynı desenin iki kopyası içe aktarma eşleştirme yolunda duruyor
(`MediaEntryRepository.kt:131`, `MediaEntryBackup.kt:192` —
`matches.maxByOrNull { it.updatedAt }`). Bunlar yazım döngüsünde değil, içe aktarım
satır eşleştirmesindedir; çağrı zinciri bu geçişte okunmadığı için **bilinçli olarak
dokunulmadı** ve T20 (dedektör testi) ile birlikte ele alınmak üzere açık bırakıldı.

### 19.4 T02 — kuyruk artık test edilebilir ve test ediliyor

`PendingSyncDrainer.drain`, `Context` + altı yazıcıyı doğrudan çağırdığı için JVM'de
koşulamıyordu. Kamu imzası korunarak çekirdek, çalıştırıcıyı parametre alan iç
(`internal`) bir `drain(dao, freshBudget, execute)` dikişine ayrıldı; kararlar saf
`PendingSyncPolicy`ye (`MAX_RETRIES`, uygunluk, `RetryDecision`, alan
birleştirme/düşme, satır eşleştirme, yetki metni) taşındı. Yeni testler
`app/src/test/java/com/kitsugi/animelist/data/local/` altında (dizinde önceden
3 dosya / 18 test vardı; bu geçişle 5 dosya / 53 test):

| Dosya | Test | Ne sabitliyor |
|---|---|---|
| `PendingSyncPolicyTest` | **11** | bütçe = 5 ve uygunluk sınırı; karar tablosu (düşür / artır / artırma); E74 istisna yolu; alan birleşimi; E73 alan düşme; satır eşleştirme matrisi (`id<=0`, kaynak, tür) |
| `PendingSyncDrainerTest` | **24** | sahte DAO ile: enqueue alan birleştirme + en yeni anlık görüntü, farklı satır/`id=0`/eski-biçim işler; `removeSyncedFields` (E73 regresyonu, kısmi düşme, sayaç koruma, işlem/satır ayrımı); drain (başarıda düşme + çözülen alan/satırın çalıştırıcıya geçişi, geçici hatada artırma, **DISABLED = gönderildi**, bağlantı boşluğunda koruma, yetki sonucunda VE istisnasında artırma yok, ağ istisnasında artırma, `MAX_RETRIES` dolunca çalıştırmadan koruma, `freshBudget` ile canlanma, bozuk JSON, tanınmayan işlem, boş kuyruk, iptalin bütçe yakmadan yayılması ve kilidin bırakılması) |

`data/auth` sayısı değişmedi (**176**); bu geçiş **+35 test** getirdi.

### 19.5 Model koşturması bulgusu: `fuzz2.py` "added_row_lowers" (kod hatası DEĞİL)

Drive'daki araçlar yeniden koşturuldu (**model koşturması** — Kotlin'i değil kuralı
doğrular): `fuzz_merge.py` → `{'status': 0, 'progress': 0, 'total': 0}`;
`fuzz2.py` → `{'monotone_progress': 0, 'added_row_lowers': 66, 'invariants': 0}`.
66 ihlalin örüntüsü tek: toplamı bilinmeyen adaylara (ör. ilerleme 14, `total = null`)
toplamı 12 olan yeni bir satır eklenince birleşik ilerleme 14 → 12 oluyor.
Kotlin karşılığı denetlendi: bu, `CrossSyncMergePolicy.merge` kırpmasıdır
(toplam biliniyorsa ilerleme toplama kırpılır; MAL toplamı aşanı 400 ile reddeder —
kırpılmazsa isteğin TAMAMI düşerdi) ve E71 dalıyla tutarlıdır (daha büyük toplam
adaylarda varsa o kullanılır; yoksa tek bilinen toplam geçerlidir). **Yazım geriye
gitmez:** `dirtyFields` ilerlemeyi yalnız `current.progress < merged.progress`
iken yazar; 14'teki hedefe 12 yazılmaz, gerideki hedefe 12 yazılır (12 üstü zaten
toplama aykırı olduğundan geçersiz veridir). Sonuç: model invaryantı, sözleşmeden
daha katı yazılmış; kod değiştirilmedi. `fuzz2.py` invaryantının "toplam altında
kalan ilerleme düşmez" biçiminde daraltılması Drive araçlarının işidir (araçlar
depoda değil, Drive'dadır — bu geçişte düzenlenmedi).

### 19.6 Dürüst durum

- **Derleme ve birim test ÇALIŞTIRILMADI**: ortamda JDK/Gradle/Android SDK yok
  (`java: command not found` ile doğrulandı). Doğrulama düzeyi: **kod okuması**
  (zincirin tamamı satır satır) + **sözdizimi taraması** (`kcheck.py`: değişen/
  eklenen 7 dosya, 0 hata) + **model koşturması** (yukarıda). Yeni testlerin
  JVM'de koşulabilirliği bağımlılıklardan denetlendi (`org.json:json` test
  bağımlılığı gerçek impl.; `runBlocking` coroutines-core'da; `SyncResult`
  mevcut testte zaten JVM'de kuruluyor) — ama bu, "test geçti" demek DEĞİLDİR.
- Kullanıcının çalıştırması gereken komut değişmedi:
  `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
- Bu geçişte değişen dosyalar: `data/local/PendingSyncPolicy.kt` (yeni),
  `data/local/PendingSyncDrainer.kt`, `data/local/PendingSyncDao.kt`,
  `data/local/MediaEntryRepository.kt`, `ui/app/AuthViewModel.kt` +
  2 yeni test dosyası.
- Sıradaki işler (02_TASK_TAKIP sırasıyla): **T13** (`AuthViewModel`
  orkestrasyon testi — E75'in denetimi sırasında okunan AVM yolu hâlâ testsiz),
  **T05** (sağlayıcı başına geri-okuma koşumu), **T03/T04** (kısmi yeniden
  bağlanmada bayat token, kaynak bazlı kimlik önbelleği temizliği). Ayrıca
  §19.3'te bırakılan iki `maxByOrNull` kopyası T20 ile birlikte bakılmalı.

---

## §20 — Geçiş 12: geri-okuma koşumu (T05) + E76/E77/E78/E79/E80, puan sınırları (T07/T08), Simkl not okuma (T06/T18 kod düzeyi)

Bu geçiş T05 için planlanmıştı; koşum yazılırken ve çevre kod okunurken **beş yeni
hata** bulundu. İkisi (E76, E77) E72'nin bizzat düzeltmek için yazıldığı hata
sınıfının komşu katmanlarda süren örnekleridir: politika doğru karar veriyor ama
daha alt katman (daraltma/yazıcı) kararı bozuyordu.

### 20.1 E76 — Bangumi/Simkl'de sonsuz STATUS yazımı (`SyncField.narrow`)

**Kanıt (kod okuması, eski hâl):** `ProviderCapabilities.narrow()`:

```kotlin
if (!rewatchingAsStatus && SyncField.REWATCH_COUNT in fields && SyncField.STATUS in supports) {
    allowed += SyncField.STATUS
}
```

Koşul "ayrı tekrar durumu olmayan her hedef" diyordu; bu kümeye tekrarı **hiç**
ifade edemeyen Bangumi/Simkl (`RewatchModel.UNSUPPORTED`, `repeatCount` alanı API'de
yok, satırda hep 0) de giriyordu. Birleşik sayaç 0'dan büyük olduğu sürece her toplu
turda `dirtyFieldsFor`, Bangumi/Simkl için `{STATUS}` üretiyor; yazıcı aynı durumu
yeniden yazıyor; satırdaki sayaç bir sonraki turda yine 0 — **sonsuz yazım** (A5
ihlali, gereksiz API kotası + kuyruk gürültüsü). Mevcut testlerdeki Bangumi/Simkl
senaryoları `repeatCount = 0` kullandığı için yakalanmamıştı.

**Düzeltme:** koşul `rewatchModel == RewatchModel.AS_FLAG` oldu (MAL `is_rewatching`,
Kitsu `reconsuming` — sayaç değişikliği bayrağı taşıyan durum yazımıyla gitmeli).
UNSUPPORTED hedeflerde `REWATCH_COUNT` zaten `supports` kesişiminde düştüğü için
sonuç boş küme: tekrar sayacı o hedeflerde **bilinçli kısıttır**, istek üretilmez.

### 20.2 E77 — MAL yazıcısı E72'yi bozuyordu (`MalSyncManager`)

**Kanıt (kod okuması, eski hâl):** E72 düzeltilirken politika katmanı
(`ProviderCapabilities.statusFor` + `dirtyFieldsFor`) "MAL'de mevcut durum korunur,
yalnız `is_rewatching` bayrağı yazılır" diye düzeltildi. Ama **yazıcının** STATUS
gövdesi hâlâ koşulsuz eşlemedeydi:

```kotlin
append("status=${URLEncoder.encode(entry.status.toMalStatus(entry.type), "UTF-8")}")
```

`toMalStatus(Repeating) == "completed"` olduğundan, tekrar sürerken bayrak taşınmak
için STATUS kümeye girdiğinde yayındaki bir yapımın MAL durumu `watching →
completed`a dönüyordu. Politika doğru, yazıcı kararı uygulayamıyor — E72 ile aynı
hastalık, bir katman altı.

**Düzeltme:** tekrar yazımında (`entry.status == Repeating && STATUS ∈ fields`)
MAL'deki mevcut durum tek bir GET ile okunur (`malCurrentListStatus`,
`?fields=my_list_status`, `syncClient` kalıbı) ve saf karar fonksiyonu
`malStatusForWrite(status, type, existing)` uygulanır: tekrar için mevcut durum
`{watching, reading, completed, on_hold, dropped}` allowlist'indeyse korunur;
`plan_to_*`/bilinmeyen/yoksa eski varsayılan `completed`. Allowlist dışı dizge
API'ye asla geçirilmez. Okuma ağ/transient hatasında `null` döner → `completed`
varsayılanı; yazım ek GET yüzünden bloklanmaz, hata sessizce yazıma dönüşmez
(karar yalnızca gövde içeriğidir, başarı/yeniden-deneme semantiği değişmedi).
Karar fonksiyonu `internal` ve saf — 6 birim testiyle kilitlendi
(`MalRewatchStatusWriteTest`).

### 20.3 E78 — "en yeni" seçiminin kalan kopyaları tek kaynağa toplandı (T20 kalanı)

E75, `AuthViewModel`deki `maxByOrNull { updatedAt }`yi sıralamalı biçime çevirmişti
ama sıra hâlâ **üç ayrı yerde elle yazılıyordu** (`resolveStatus`, `newestRewatch`,
AVM) ve iki yerde daha tiebreak'siz kopya duruyordu (`MediaEntryBackup` eşleşme
seçimi, `MediaEntryRepository.smartImport` entity eşleşmesi) — §19.3'te bilinçli
bırakılanlar. Bu geçişte:

- `CrossSyncMergePolicy.NEWEST_FIRST` + `newestOrNull()` tek kaynak yapıldı
  (`updatedAt↓ → progress↓ → source↑ → id↑`; son anahtar E70).
- `resolveStatus`/`newestRewatch` bu sırayı kullanır; davranış birebir aynı
  (anahtarlar değişmedi), yalnız tanım tekelleşti.
- `AuthViewModel` ve `MediaEntryBackup` `newestOrNull()`a bağlandı;
  `smartImport` entity üzerinde aynı anahtarlarla sıralar (entity tipi farklı
  olduğu için politika nesnesinin fonksiyonu doğrudan kullanılamaz; anahtarlar
  yoruma çapraz referanslıdır).
- 4 yeni birim testi (eşit damgada ilerleme/kaynak/id kırılımı, girdi sırasından
  bağımsızlık, boş liste).
- Bilinçli istisna: `MangaDetailViewModel`deki bölüm-ilerleme seçimi kütüphane
  satırı seçimi değildir (kod okumasıyla doğrulandı), kapsam dışı bırakıldı.

### 20.4 E79 — Simkl "bırakıldı" içe aktarmada "planlandı" oluyordu

`SimklImportManager.mapSimklStatus` yalnız `"dropped"` dizgesini tanıyordu; Simkl'in
gerçek kovası `notinteresting` (yazıcı ve `SimklSyncManager` okuma eşlemesi bunu
kullanır) `else` dalından **Planned** oluyordu. Bırakılan her kayıt, içe aktarmada
"planlandı"ya dönüyor; üstelik toplu tur bu yanlış durumu diğer kaynaklara "en yeni
işlem" gibi yayabilirdi. Eşlemeye `notinteresting → Dropped` eklendi (iki dizge de
kabul edilir); fonksiyon `internal` yapılıp testle kilitlendi.

### 20.5 E80 — Simkl notu yazılıyor ama hiç okunmuyordu (+smartImport not silmesi)

**Kanıt:** Simkl yazıcısı `private_memo` yazar (T18 kod düzeyi bu turda da
doğrulandı: diff yazımında `entry.notes ?: ""`, tam yazımda yalnız dolu not;
`SimklApiClient` `private_memo` alanını koyar). `SimklImportManager` ise içe
aktarırken `notes` alanını hiç doldurmuyordu — üstelik `notes = null` gelen Simkl
satırı, `smartImport`'ta yerel notun üzerine yazılarak **kullanıcının notunu
siliyordu** (A4 ihlali, içe aktarma yönünde).

**Resmî sözleşme (api.simkl.org/guides/sync, bu turda dokümandan doğrulandı):**
`GET /sync/all-items?memos=yes`, kaleme tekil `memo` nesnesi ekler: `text`
(≤140 karakter) + `is_private`; boş memo `{}`. Yazma tarafı `private_memo` düz
dizgesidir — asimetri Simkl'in kendisinindir.

**Düzeltme:** liste isteği `?memos=yes` taşır; `notes = simklMemoText(item)`
(`memo.text`, boş/`{}` → `null`). Not uzunluğu 140'ı aşarsa Simkl tarafı kırpar —
bu, hedefin kendi sınırıdır (E41 sınıfı bilinçli kısıt adayı); yerel tam metin
başka hedeflerde korunur çünkü not merge'inde taşınmaz (sözleşme: not/etiket/
öncelik birleştirilmez). Çalışma-zamanı doğrulaması (gerçek hesapta memo'nun
dönmesi) hâlâ yok — T06'nın kalan yarısıdır.

### 20.6 T05 — Geri-okuma koşumu (`ProviderReadBackIdempotenceTest`, 9 test × matris)

Yeni koşum: adaylardan birleşim → `dirtyFieldsFor` → yazımın kabul edilip **hedefin
kendi sözlüğünde geri okunduğu** satır (`readBackAfterWrite`: durum `statusFor` ile,
bayrak AS_FLAG'de birleşik bayrağa, diğer alanlar yalnız dirty ise) → aynı birleşime
karşı ikinci `dirtyFieldsFor` **BOŞ** olmalı. Senaryolar: geride satır, tamamlanma,
aktif tekrar, bitmiş tekrar sayacı (E76 regresyonunu iki yönden kilitler: koşum +
Bangumi/Simkl'de ilk dirty'nin de boş olduğu), yapışkan Dropped/Paused, puan+tarih
doldurma, manga cilt (Simkl tür kapısı üst katmanda, matriste yok), yalnız-planlandı.
9 test metodunun her biri 6 sağlayıcıyı döner. Koşum, E72 ve E76 öncesi kodda
denendiğinde zihinsel çalıştırmayla **kırmızı** verir (Bayrak/STATUS döngüsü ikinci
turda yeniden dirty üretir) — düzeltme sonrası boş küme. Koşumun sınırı: kanonik
`MediaEntry` düzeyi; HTTP gövde dönüşümleri (puan ölçekleri) ayrı dosyada (§20.7),
MAL gövdesi §20.2 testindedir; sunucunun kendi kırpmaları modellenmez (onlar
sessiz döngü üretmez, hata/yeniden-deneme yoluna girer).

### 20.7 T07/T08 kod düzeyi — puan sınırları (`ProviderScoreBoundaryTest`, 9 test)

- **AniList** `scoreRaw`: `SyncScores.aniListRaw` 0..100 kelepçesi (null/negatif→0,
  taşma→100).
- **Kitsu** `ratingTwenty`: `kitsuTwenty`, 2..20 dışında asla değer üretmez
  (−20..220 taraması); boş puan 0/1'e kelepçelenmez, `null` olur (yazıcı tarafında
  temizleme — KDoc'ta sınırı yazıldı).
- **Bangumi** `rate`: `toBangumiRate`, boş/0/negatif → `null` (gövdeye konmaz, E41),
  100'lük sızıntılar 1..10'a (ör. 75→8, 95→10, 250→10); −50..300 taraması.
  Yuvarlama bağı (x.5) değerleri, `kotlin.math.round`'un bağ-kırma semantiğine
  test bağlanmasın diye bilinçli seçilmedi.
- T07'nin çalışma-zamanı yarısı (`rate=0` sunucu davranışı) bu dosyayla KAPANMAZ;
  gerçek API çağrısı gerekir.

### 20.8 Doğrulama (model koşturması + sözdizimi)

- `kcheck.py`: değişen/yeni **12 dosya, 0 hata**.
- `fuzz_merge.py` (5000): `{status: 0, progress: 0, total: 0}`.
- `fuzz2.py` (5000): `{monotone_progress: 0, added_row_lowers: 66, invariants: 0}`
  — Geçiş 11 tabanıyla birebir aynı; 66, §19.5'teki sözleşme davranışıdır
  (toplam kırpması), E78 anahtarları değiştirmediği için modelde regresyon yok.
- Test sayıları: `data/auth` **24 dosya / 210 test** (bu geçiş: 4 yeni dosya 30 test
  + `CrossSyncMergePolicyTest`e 4 = **+34**). Testler **dosyadadır, koşulmamıştır**.

### 20.9 Dürüst durum

- **Derleme ve birim test ÇALIŞTIRILMADI** (ortamda JDK/Gradle/Android SDK yok).
  Doğrulama: kod okuması (bu bölümdeki her iddia satır/dosyayla) + resmî Simkl
  dokümanı + kcheck (12/0) + model koşturması (yukarıda). "%100 çalışıyor" iddiası
  ancak kullanıcının Gradle koşusu ve §20.5/§20.7'deki çalışma-zamanı
  doğrulamalarıyla tamamlanır. Komut:
  `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
- Bu geçişte değişen üretim dosyaları: `data/auth/SyncField.kt` (E76),
  `data/auth/MalSyncManager.kt` (E77), `data/auth/CrossSyncMergePolicy.kt` +
  `ui/app/AuthViewModel.kt` + `data/local/MediaEntryBackup.kt` +
  `data/local/MediaEntryRepository.kt` (E78), `data/auth/SimklImportManager.kt`
  (E79/E80). Yeni test dosyaları: `ProviderReadBackIdempotenceTest`,
  `MalRewatchStatusWriteTest`, `SimklImportParsingTest`,
  `ProviderScoreBoundaryTest` + `CrossSyncMergePolicyTest` ekleri.
- Sıradaki işler: **T13** (`AuthViewModel` orkestrasyonu — hâlâ en büyük testsiz
  yüzey), **T03/T04** (bayat token/kimlik önbelleği), **T10/T11** (favori),
  **T22** (ayna entegrasyonu); çalışma-zamanı doğrulamaları **T06/T07/T09** gerçek
  hesap/API gerektirir. E60 tek DOĞRULANMADI çalışma-zamanı maddesi olarak durur.

---

## §21 — Geçiş 13: kullanıcı sorgusuyla tetiklenen çapraz denetim (E81, E82)

Geçiş 12 kapatıldıktan sonra kullanıcı, “bir eksik/gedik veya yanlış eşleşme
kalmadığına emin misin?” diye açıkça sordu. Buna sözlü güvence verilmedi; onun
yerine yasak desenler ve Geçiş 12'de eklenen testlerin çağırdığı semboller tekrar
tarandı. Bu denetim iki gerçek hata daha buldu: biri üretim kodunda, biri bu
oturumda yazılan test kodunda.

### 21.1 E81 — Simkl silme akışı geçersiz `dropped` durumu gönderiyordu

**Kanıt (kod okuması, düzeltme öncesi):**

- Normal Simkl yazım yolu doğruydu: `SimklSyncManager.watchStatusToSimkl`
  `WatchStatus.Dropped` değerini `notinteresting`e çevirir.
- Silme akışı ise ikinci bir yoldu ve `deleteEntryFromSimkl` içinde
  `SimklBatchEntry(status = "dropped", ...)` sert-kodluyordu.
- `SimklApiClient.addToListBatchDetailed`, bu dizgeyi normalize etmeden
  `JSONObject().put("to", entry.status)` ile `/sync/add-to-list` gövdesine koyuyordu.
- Simkl sözleşmesindeki izinli `to` değerleri
  `watching | plantowatch | completed | notinteresting | hold`dur; `dropped`
  geçerli değildir. Dosyanın kendi `watchStatusToSimkl` açıklaması da Simkl'in bunu
  reddettiğini söyler.
- Ayrıca `SimklApiClient.addToList` üzerindeki eski KDoc, `dropped`ı geçerli bir
  durum gibi listeliyordu. Bu metin-kod çelişkisi E81'in muhtemel kaynağıydı;
  sözleşmeye uygun olan kod esas alınarak yorum düzeltildi.

**Etki:** Yerel silmede ilerleme ve puan temizleme adımları çalışsa bile son
“satırı bırakıldı kovasına taşı” isteği geçersiz enum yüzünden kabul edilmiyordu.
Böylece kayıt Simkl'de eski liste durumunda kalabiliyordu; makbuzun `not_found`
benzeri sayılan dalı da bu sessiz eksikliği ayırt etmiyordu. Bu, silmenin altı
kaynağa doğru yayılması (A1/A10) ve sessiz atlama yasağı açısından üretim hatasıdır.

**Düzeltme (iki katman):**

1. Asıl sözleşme çağırana iade edildi: silme akışı artık
   `watchStatusToSimkl(WatchStatus.Dropped, entry.type)` kullanır.
2. İstemci sınırına son emniyet eklendi:
   `SimklSyncContract.normalizeListStatus`, bilinen tek geçersiz eşlemeyi
   (`dropped`, büyük/küçük harf ve çevre boşluğu duyarsız) `notinteresting`e
   çevirir. Bu doğrulayıcı değildir; bilinmeyen değerler sessizce başka kovaya
   çevrilmez.
3. Yanlış KDoc, `notinteresting`i geçerli kova olarak gösterecek şekilde düzeltildi.

**Testler:** `SimklSyncContractTest`e 3 test eklendi; dosya 8 testten 11 teste
çıktı. Testler çeviriyi, beş yasal kovanın değişmeden geçişini ve bilinmeyen
değerlerin yeniden yazılmadığını kilitler. Gerçek Simkl hesabında silme isteğinin
sunucuda kabulü bu bölümle **çalışma-zamanı doğrulanmış sayılmaz**; hesabı olan
kullanıcı koşusu/T06 kapsamı ayrıca gerekir.

### 21.2 E82 — Geçiş 12 testlerinde var olmayan yardımcı parametresi

Geçiş 12'de eklenen dört `newestOrNull` testi,
`CrossSyncMergePolicyTest.e("mal", id = 1, ...)` biçiminde yazılmıştı. Oysa
yardımcının `id` parametresi yoktur; `MediaEntry(id = source.hashCode(), ...)`
kurar. Bu hata kullanıcının ilk Gradle koşusunda test derlemesini kırardı.

Bu, üretim davranışında bir hata değildir; bu oturumun test yazım hatasıdır ve
saklanmadan kaydedilmiştir. Testler `e(...).copy(id = ...)` biçimine çevrildi;
böylece ilerleme → kaynak → `id` kırılımı gerçekten ayrı kimliklerle sınanır.
Geçiş 12'deki diğer yeni testlerin çağırdığı ilgili kurucu/fonksiyon imzaları da
(`MediaEntry`, `statusFor`, `ProviderCapabilitiesCatalog.of`, `Merged`,
`malStatusForWrite`, Simkl import yardımcıları ve puan dönüşümleri) bu denetimde
tek tek kaynakla karşılaştırıldı.

### 21.3 Aynı denetimde hata sayılmayan iki şüpheli nokta

- `MalImportManager.kt` içinde `is_favorited` ilk seçicide görünür; ancak kod
  400/`invalid field` durumunda alanı çıkarıp bir kez yeniden dener ve sonraki
  sayfalarda da atlamayı hatırlar. Bu, daha önce belgelenmiş MAL içe aktarma
  alan-düşürme istisnasıdır; yeni bir üretim hatası olarak işaretlenmedi.
- `ShikimoriSyncManager.kt` içindeki `fullWrite || SyncField.STATUS in fields`
  yalnız `status` niteliğini kapılar; Bangumi E11'deki “STATUS varsa her şeyi yaz”
  tırmanması değildir. Diğer alanlar kendi `SyncField` koşullarıyla ayrıca
  kapılandığı için bu nokta temiz bulundu.

### 21.4 Süreç bulgusu: geçici test dosyası ezmesi yakalanıp geri alındı

E81 testleri yazılırken `SimklSyncContractTest.kt` için tam dosya yazımı kullanıldı
ve mevcut dosya çalışma ağacında geçici olarak ezildi. Bu, commit öncesi `git diff`
denetiminde fark edildi; dosya `HEAD` sürümünden geri getirildi, mevcut 8 test
aynen korunarak yeni 3 test sonuna eklendi. Ezilmiş içerik commit/push edilmemiştir;
kalıcı test kaybı yoktur. Buradaki ders E82 ile aynıdır: dosya varlığı ve yardımcı
imzaları varsayılmaz; önce okunur, sonra yazılır.

### 21.5 Doğrulama ve dürüst durum

- `kcheck.py`: bu geçişte dokunulan 5 dosya tarandı. Tek rapor,
  `SimklApiClient.kt` satır 167'deki önceden bilinen yanlış pozitif olmuştur;
  diğer 4 dosyada sözdizimi hatası yoktur.
- Birleşim algoritması bu geçişte değişmediği için fuzz sonuçları §20.8'deki
  tabanla aynı kapsamdadır; yeni bir “fuzz geçti” iddiası üretilmemiştir.
- **Derleme ve birim test bu ortamda yine ÇALIŞTIRILMADI.** E82'nin kendisi,
  yalnız kod okuması ve kcheck'in bir Gradle koşusu yerine geçemeyeceğini gösterir.
  Kullanıcının çalıştırması gereken komut değişmemiştir:
  `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`
- Bu geçişte değişen dosyalar: `data/auth/SimklSyncManager.kt`,
  `data/remote/SimklApiClient.kt`, `data/remote/SimklSyncContract.kt`,
  `data/auth/CrossSyncMergePolicyTest.kt` ve
  `data/remote/SimklSyncContractTest.kt`.
- “Hiç eksik kalmadı” iddiası bu bölümle de kurulmaz. Açık kalan başlıca kanıt
  boşlukları: Gradle derleme/test koşusu; T13 `AuthViewModel` orkestrasyonu;
  T03/T04 token/kimlik önbelleği; T22 ayna entegrasyonu; T06/T07/T09 çalışma-zamanı
  doğrulamaları ve E60. Bir sonraki saldırı yüzeyi özellikle kimlik eşleşmesi ve
  orkestrasyon yoludur.

