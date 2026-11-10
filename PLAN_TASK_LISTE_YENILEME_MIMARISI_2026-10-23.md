# PLAN & TASK — Listem Yenileme Mimarisi (Kaynak Bazlı TTL + Delta + Güvenli Silme)

**Tarih:** 2026-10-23
**Branch:** `arena/5f455258-kitsugi-beta`
**Hedef sürüm:** v2.4.341
**Commit'ler:** `07b8bab` · `308a972` · `24a17c7` · `db21dcd` · `d35ec91` · `505a477`

**İstek (kullanıcı):**
> "Artık elle de, otomatik de Listem yenilenirken veriler baştan sona çekilmemeli; her kaynağa özgü
> şekilde yenilenmeli. Tüm kaynakların ve açık kaynak uygulamaların bunu nasıl yaptığına bak."
>
> (devam) "Listeye bir şey eklediğimde/çıkardığımda ne olacak? Aklıma gelmeyen bir sürü senaryo var."
>
> (devam) "Başka varsayımsal, aklıma gelmeyen önemli kısıtlar kalmadığına emin misin?"

---

## 1. Mevcut durum (tespit)

| # | Bulgu | Konum |
|---|-------|-------|
| M1 | Her yenilemede listenin **tamamı** ağdan çekiliyor (sayfa sayfa) | `*ImportManager.fetchAllLists()` |
| M2 | `smartImport` eşleşen **her** satırı `updates`'e koyuyor → no-op yenileme bile tüm tabloyu yazıyor, Room invalidation → birleşik liste baştan hesaplanıyor | `MediaEntryRepository.smartImport` |
| M3 | Arka plan yenilemesi `deleteBySource + insertAll` (sil-yeniden-yaz) yapıyor → satır id'leri değişiyor, kaydırma/kuyruk referansları bozuluyor | `replaceSourcePreservingAdultFlags` |
| M4 | Tazelik **global**: tek `last_list_sync_ms` damgası; yalnızca AniList/MAL/Simkl | `KitsugiApplication` |
| M5 | Kitsu / Shikimori / Bangumi bağlı olsa bile **hiç** otomatik yenilenmiyor | `KitsugiApplication` |
| M6 | "Tümü"yü yenile → 6 içe aktarma **paralel** başlıyor (6× ağ + ardışık tam yazım) | `MyListScreen.onSyncAll` |
| M7 | **Silme hiç yapılmıyor**: `allowDelete = false` her yerde → web'den silinen kayıt yerelde hayalet kalıyor | `AuthViewModel.import*List` |
| M8 | `allowDelete = true` yapılsa çevrimdışı eklenen kayıtlar silinirdi (sunucu listesinde yok) | aynı |
| M9 | Çevrimdışı silinen kayıt, liste yenilenince sunucudan geri geliyor ("sildiğim geri geldi") | tombstone yok |
| M10 | MAL `nsfw=false` iken +18 kayıtlar yanıtta yok → "eşleşmedi = sil" bunları silerdi | `MalImportManager` |
| M11 | Boş/hatalı yanıt (401, kota, kesik sayfa) `allowDelete` ile birlikte **tüm kütüphaneyi** silebilirdi | `smartImport` boş dal |
| M12 | Yenileme sürerken girilen ilerleme/puan bayat uzak kopyayla eziliyor (tur başında okunan satırlar) | `smartImport` |
| M13 | Aynı kaynak için iki import paralel çalışabiliyor (sekme + çapraz eşitleme + bildirim işçisi) → **çift kayıt** | çoklu giriş noktası |
| M14 | Cihaz saati geri alınırsa `now - lastSuccessAt` negatif → liste **sonsuza kadar "taze"** | `ListRefreshPolicy` |
| M15 | Kütüphane boşken Simkl `activities` imzası aynıysa liste hiç inmiyor → boş liste kalıyor | `SimklImportManager` |
| M16 | "Sildim, sonra tekrar ekledim" → yeniden eklenen kayıt yeni yerel id alır, kuyruktaki eski `DELETE` onu uzaktan siler | `PendingSyncDrainer` |
| M17 | Her yenilemede `getAll()` + Kotlin süzgeci (1000+ satır kopyası) | `smartImport`, `mergeSourcePreservingAdultFlags` |

**Kök sorun:** yenileme tek bir akış değil, **altı ayrı yolun kopyasıydı** (sekme, "Tümü", ayarlar,
açılış, bildirim işçisi, çapraz eşitleme). Her yolun kendi tazelik kuralı, kendi silme davranışı ve
kendi hata eğilimi vardı.

---

## 2. Çözüm mimarisi

Tek giriş noktası: **`data/sync/SourceListSynchronizer.refresh()`** — tüm yollar buradan geçer.

```
[Elle yenile / Tümü / Açılış / Ayarlar / Bildirim]
        │
        ▼
ListRefreshPolicy.plan(state, mode, supportsDelta)
   ├── SKIP_UP_TO_DATE  → ağa çıkma ("zaten taze" / "backoff")
   ├── DELTA            → fetchDelta(since)   (AniList, Kitsu) / revisionProbe (Simkl)
   └── FULL             → fetchFull()         (günlük uzlaştırma)
        │
        ▼
MediaEntryRepository.smartImport()  [importMutex ile seri]
   ├── fark-tabanlı yazım (değişmeyen satır YAZILMAZ)
   ├── bekleyen yerel düzenleme → deferred (ezilmez)
   ├── tur başladıktan sonra değişen satır → deferred
   ├── diriliş koruması (bekleyen DELETE tombstone)
   └── silme: 3 kapı (sunucu kökeni + tam yanıt + bekleyen iş yok) ⇒ yalnız TAM turda
        │
        ▼
ListSyncStateStore.recordSuccess/recordFailure  (+ ServerSnapshotStore.write)
```

### 2.1 Kaynak yetenek matrisi (uygulanan)

| Kaynak | Delta / değişiklik tespiti | Strateji | Arka plan penceresi |
|---|---|---|---|
| AniList | `Page.mediaList(updatedAt_greater:)` | delta (ret → tam) | 60 dk |
| Simkl | `GET /sync/activities` revizyon imzası | imza aynıysa liste inmez | 60 dk |
| Kitsu | `library-entries?filter[since]=<ISO-8601>` | delta (ret → tam) | 60 dk |
| MAL | — (uç yok) | tam liste + fark yazımı | 6 saat |
| Shikimori | — (`/api/v2/user_rates`'ta `updated_at` süzgeci yok) | tam liste + fark yazımı | 6 saat |
| Bangumi | — (uç yok) | tam liste + fark yazımı | 6 saat |

### 2.2 Politika eşikleri

| Sabit | Değer | Anlam |
|---|---|---|
| `USER_FRESH_WINDOW_MS` | 45 sn | elle yenilemede "zaten taze" |
| `BACKGROUND_FRESH_WINDOW_MS` | 60 dk | delta destekleyen kaynak |
| `BACKGROUND_FULL_ONLY_WINDOW_MS` | 6 saat | yalnız tam çekim yapan kaynak |
| `DELTA_MAX_FULL_AGE_MS` | 24 saat | delta için son tam senkron tazeliği |
| `DELTA_MAX_AGE_MS` | 6 saat | delta başlangıcı çok eskiyse tam çek |
| `CLOCK_SKEW_TOLERANCE_MS` | 5 dk | damga gelecekteyse tam çek |
| `MAX_CONSECUTIVE_FAILURES` / `FAILURE_BACKOFF_MS` | 3 / 15 dk | arka plan geri çekilmesi |
| `MIN_DELETION_COVERAGE` | %30 | yarım yanıt koruması |
| `SYNC_ALL_STAGGER_MS` | 700 ms | "Tümü" sıralı çalışır |

---

## 3. Yapılan işler (task listesi)

### 3.1 Yeni dosyalar
- [x] `data/sync/ListRefreshPolicy.kt` — saf karar fonksiyonu (SKIP/DELTA/FULL) + saat kayması koruması
- [x] `data/sync/ListSyncStateStore.kt` — kaynak başına defter (SharedPreferences, Room şeması büyümedi)
- [x] `data/sync/SourceListSynchronizer.kt` — tek giriş noktası, single-flight, delta→tam düşüş, boş yanıt reddi
- [x] `data/sync/ImportReconciler.kt` — saf silme kuralları (sunucu kökeni, %30 kapısı, diriliş)
- [x] `data/sync/ServerSnapshotStore.kt` — "sunucuda en son ne vardı" kimlik defteri
- [x] `app/src/test/.../ListRefreshPolicyTest.kt` — 18 birim testi (saat kayması dahil)
- [x] `app/src/test/.../ImportReconcilerTest.kt` — 13 birim testi
- [x] `app/src/test/.../MediaEntryPayloadDiffTest.kt` — fark karşılaştırma testleri

### 3.2 Değişen dosyalar
- [x] `data/local/MediaEntryRepository.kt` — `ImportStats`, fark-tabanlı `smartImport` (+ `importMutex`,
      `refreshStartedAtSec`, `isFullPull`, `keepAdultRows`, `guardAgainstPartialPayload`), boş yanıt
      artık asla silmez, `resetRefreshLedgers()` (temizleme/yedek sonrası), `identityKeysFor` (çapraz tombstone)
- [x] `data/local/MediaEntryDao.kt` — `getBySource` / `countBySource` (şema değişikliği YOK)
- [x] `data/local/PendingSyncDrainer.kt` — `UPDATE` yazılırken aynı yapımı hedefleyen `DELETE` düşürülür
- [x] `data/auth/AniListImportManager.kt` — `fetchListsSince` (delta)
- [x] `data/auth/KitsuApiClient.kt` — `fetchLibraryEntries(sinceIso:)` + `filter[since]` URL kodlaması
- [x] `data/auth/KitsuImportManager.kt` — `fetchAllListsSince` (ISO-8601 sabit biçim)
- [x] `data/auth/SimklImportManager.kt` — `fetchActivitiesRevision` (ucuz imza)
- [x] `ui/app/AuthViewModel.kt` — 6 `refresh*List` ortak çekirdeğe bağlandı, `syncAllLists` (sıralı),
      `announceListRefresh` (dürüst mesaj + `deferred` bilgisi), hesap değişince defter sıfırlama
- [x] `KitsugiApplication.kt` — global 24 saat bloğu → 6 kaynak için kaynak bazlı politika
- [x] `core/notifications/AiringNotificationWorker.kt` — Simkl fark-tabanlı birleştirme
- [x] `AppRoot.kt` / `AppRootSettingsExtras.kt` / `AppRootTabPages.kt` — post-auth ve "içe aktar" = USER_FORCE
- [x] `ui/screens/mylist/MyListScreen.kt` + `MyListComponents.kt` — tek akış, gerçek senkron göstergesi
- [x] `app/build.gradle.kts` — sürüm 2.4.340 → 2.4.341
- [x] `COZUM_RAPORU_LISTE_YENILEME_MIMARISI_2026-10-23.md`, `release_notes_v2.4.341.md`

---

## 4. Davranış matrisi (kullanıcı senaryoları)

| Senaryo | Sonuç | Gecikme |
|---|---|---|
| Sekme yenile (45 sn içinde tekrar) | ağa çıkmaz | anında |
| Delta kaynakta yenile | yalnız değişenler | 1 küçük istek |
| Web'de **ekleme** | delta ile gelir | ≤1 saat |
| Web'de **silme** | günlük tam turda, 3 kapıdan geçerek | **≤24 saat** |
| Telefondan ekle/sil (online) | anında yazılır + push | anında |
| Telefondan ekle/sil (offline) | kuyruk; ezilmez, diriltilmez | bağlantı gelince |
| Yenileme sırasında düzenleme | korunur (`deferred`) | — |
| Boş/hatalı yanıt | hiçbir şey silinmez, defter bozulmaz | — |
| Saat değişimi | tam çekim (kilitlenmez) | — |
| "Baştan yenile" | tam çekim; yalnız %30 kapısı atlanır | — |

---

## 5. Bilinçli sınırlar (kabul edilen, hata değil)

1. **Web'den silme ≤24 saat.** Delta silmeyi göremez; her turda "eşleşmeyen=sil" yarım yanıtta veri kaybı riskidir.
2. **Bir kaynakta eklenen kayıt diğerlerine otomatik yazılmaz** — bunun için ayrı çapraz eşitleme akışı var.
3. **Bekleyen yerel düzenleme uzak silmeden üstündür** (outbox semantiği).
4. **Çok cihaz: son yazan kazanır** (alan bazlı birleştirme yok).
5. **Yeni kurulumda ilk tam çekime kadar hiçbir satır silinmez** (sunucu defteri boş).

---

## 6. Doğrulama

```bash
./gradlew :app:testFossDebugUnitTest \
  --tests "*ListRefreshPolicyTest" --tests "*ImportReconcilerTest" --tests "*MediaEntryPayloadDiffTest"
./gradlew :app:assembleFossDebug
adb logcat -s KitsugiAutoSync KitsugiListRefresh SourceListSynchronizer MediaEntryRepository
```

- Statik: `app/src` altındaki **1285** `.kt` dosyası `kopyt` ile sözdizimi hatasız ayrışıyor.
- Beklenen log: delta kaynaklarda `usedDelta=true`, ikinci yenilemede `atlandı: fresh`,
  `boş liste yanıtı yok sayıldı` **görülmemeli**, liste bir kare bile boşalmamalı.
- **Yapılmadı (sandbox'ta JDK/Android SDK yok):** derleme, JUnit koşumu, cihaz testi.

---

## 7. Geri alma

- Tek dosya geri: `git revert 505a477` (boş yanıt), `git revert d35ec91` (yarış durumları) …
- Tamamı: 2.4.340'a dön (davranış eski: her yenileme tam çekim + sil-yeniden-yaz).
- Kilitlenme/acil durum: `SourceListSynchronizer.resetState(ctx)` tüm kaynak defterlerini sıfırlar;
  bir sonraki yenileme tam çekim yapar.

---

## 8. Sonraki tur (opsiyonel)

- [ ] `PendingSync` kuyruğunda aynı satırın ardışık işlemlerini birleştirme (coalescing)
- [ ] Kardeş satır yansıtmasını (`mirrorUserFieldsToSiblings`) toplu tek transaction'a alma
- [ ] `entriesFlow` sonrası birleşik liste hesabını (kimlik köprüsü + gruplanma) artımlı hale getirme
- [ ] Delta turunda da silme tespiti: AniList için ayrı `DeletedMediaListEntry` akışı / tam tur sıklığını 24→12 saat
- [ ] Çok cihaz çakışması için alan bazlı son-yazan birleştirmesi
