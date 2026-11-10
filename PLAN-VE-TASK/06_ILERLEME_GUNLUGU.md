# 06 · İLERLEME GÜNLÜĞÜ (en yeni üstte)

## 2026-10-23 · Geçiş 16 (`arena/86b99c20-kitsugi-beta` — E96–E105 + Tam CI/JUnit/R8 DEX Kanıtı)

### Özet
- Kullanıcının `Listem` ekran görüntüsünde (`kaiju no 8`, `AL` kartı: `Tamamlandı · 12/12 bölüm · ★ 9/10`, yalnızca `Simkl`, `Kitsu`, `MAL` rozetleri var; **Shikimori** ve **Bangumi** eksik) ve Kitsu yeniden giriş gözleminde ortaya çıkan **tüm uçtan uca çapraz eşitleme, kümeleme, kimlik köprüsü, yerel yansıtma, alan adı yedekliliği ve oturum yenileme** kök nedenleri tespit edilip eksiksiz kapatıldı (`E96`–`E105`).
- Önceki tüm turlarda eksik olan **gerçek JDK 17 + Android SDK 36 Gradle derleme, tüm JUnit birim test paketleri, FOSS Debug APK, R8 tam küçültmeli Release APK ve `verify_apk_dex_bytecode.py` DEX doğrulaması** GitHub Actions üzerinde çalıştırılıp **üç iş akışının tamamında (`🏗️ Android Derleme Doğrulama`, `🎌 Bangumi MASTER Doğrulama`, `🧩 Ortak Taksonomi Doğrulama`) %100 yeşil** kanıtlandı:
  - **Debug APK DEX kanıtı:** `22` DEX dosyası, `409.633` metot gövdesi, `11.261.566` talimat doğrulandı, `max invoke register words = 176/255`, **`VerifyError = 0`**.
  - **R8 Release APK DEX kanıtı:** `5` DEX dosyası, `254.610` metot gövdesi, `7.266.213` talimat doğrulandı, `max invoke register words = 176/255`, **`VerifyError = 0`**.

### Kapatılan Hatalar (`E96`–`E105`)
- **E96 (`AuthViewModel.kt`, `CrossSyncIdentityGuard.kt`, `CrossSyncClusterEngine.kt`)**: `syncPlatforms` içindeki `identityVerdict?.allowsAdditions ?: true` kilidi kaldırılıp tekil eşitlemeyle aynı `CrossSyncIdentityGuard.allowsBulkAddition(verdict)` (`!blocksCrossWrite`) sözleşmesine bağlandı; `extractCrosswalkOrCandidateMalId` ile crosswalk üzerinden gelen gerçek MAL kimliği çıkarıldı.
- **E97 (`CrossSyncClusterEngine.kt`, `CrossSyncCandidateIndex.kt`, `CrossSyncIdentityGuard.kt`)**:
  1. `CrossSyncCandidateIndex` içindeki birbirini görmeyen iki ayrı harita (`groupsByTitleAlias` vs `groupsByJapaneseTitle`) `groupsByAnyTitle` altında birleştirildi (`Bangumi` `title = "怪獣8号"` ↔ `AniList` `titleJapanese = "怪獣８号"` doğrudan bulunur).
  2. Küme birleşmelerinde eski grubun indeks kovalarında hayalet olarak kalmasını önleyen `index.replaceGroup(secondary, primary)` eklendi.
  3. `"gou"` yapısal belirteci, sıra ekli rakam ayrıştırıcısı (`digitRunRegex`: `"2nd Season"` ≠ 1. sezon), `clusterYearsCompatible`, CJK↔Latin çapraz alfabe havuz kontrolü ve `assignClusterRowIds` (`1..N`) eklendi. **6 sağlayıcının tüm `6! = 720` permütasyonunda tek küme garantisi gerçek JUnit koşusunda kanıtlandı.**
- **E98 (`ExternalListSyncManager.kt`)**: Canlı ve toplu eşitlemede `OUTCOME_SYNCED` dönen her hedef platform için yerel `media_entries` tablosuna anında yansıtma satırı (`buildLocalProviderMirrorEntry` + `upsertSyncedLocalMirrors`) yazılması sağlandı; artık manuel içe aktarma beklemeden `Listem` kart rozetlerinde ve platform sekmelerinde Shikimori ve Bangumi anında görünür.
- **E99 (`AuthViewModel.kt`, `ShikimoriApiClient.kt`, `ShikimoriImportManager.kt`)**: Birleşik kayda (`mergedEntry`) kümedeki tüm kardeş satırlardan İngilizce/Japonca başlıklar ve gerçek `malId` taşındı; Shikimori içe aktarmasında `japanese` başlığı korundu.
- **E100 (`BangumiIdNamespace.kt`, `BangumiSyncManager.kt`)**: Tam genişlikli rakamlar için NFKC arama normalizasyonu (`"怪獣８号"` → `"怪獣8号"`), `infobox` takma adları + `expectedYear` (`2024`) ile eşadlı yapım ayrımı, `MediaIdentity.realMalId` koruması ve CJK başlık yokken AniList/Jikan resmî `native` Japonca başlık köprüsü eklendi.
- **E101 (`MediaIdentity.kt`)**: `MediaIdentity.titleMatch` içinde `title`, `titleEnglish` ve `titleJapanese` alanlarının tamamı çapraz alan olarak karşılaştırıldı (`Bangumi` `title = "怪獣8号", titleJapanese = null` ↔ `AniList` `title = "Kaijuu 8-gou", titleJapanese = "怪獣８号"`).
- **E102 (`ShikimoriApiClient.kt`, `ShikimoriPosterResolver.kt`)**: Tüm REST ve GraphQL kapak istekleri `shikimori.io` → `shikimori.one` → `shikimori.tv` yedekli ayna geçişine (`executeShikimori`: bağlantı hatası, HTML engel sayfası `peekBody`, HTTP `502..504`/`520..529`/`451`) bağlandı ve 2 yerel soketli canlı JUnit testiyle doğrulandı.
- **E103 (`AuthViewModel.kt`, `ExternalListSyncManager.kt`, `AppRoot.kt`)**: Bayat Kitsu statik token okumaları `getOrRefreshKitsuToken` ile değiştirildi, süresi dolmuş oturumlar toplu eşitlemede görünür raporlandı ve yeniden girişte ilgili sağlayıcının bekleyen kuyruk işlerine taze deneme bütçesi + anında drain verildi.
- **E104 (`MyListIdentityCrosswalk.kt`, `BangumiSubjectTitleResolver.kt`, `KitsugiBangumiDetailClient.kt`)**: `MyListIdentityCrosswalk.build` artık `AniList`/`MAL` satırları dahil tüm satırlarda `CrossProviderIdentityCache` ve `bgm_detail_xref_v1` önbelleklerini okur; Bangumi başlık/detay çözümleyicileri buldukları AniList `idMal` değerini otomatik olarak `BangumiLocalMappingCache` içine yazar.
- **E105 (`diag/bangumi_master/`, `TaxonomyLabelsTest.kt`, `BangumiMasterDictionaryTest.kt`, `KitsugiTranslationsTest.kt`, `KitsugiBangumiDetailClientTest.kt`, `.github/workflows/*.yml`)**: `v2.4.340`/`v2.4.342` taksonomi değişikliklerinden kalan 5 eski test/harness beklentisi ve CI Android SDK 36 kurulum adımları düzeltilerek 3 GitHub Actions iş akışının tamamı birlikte %100 yeşile çıkarıldı.

---
Her turda tek satır/blok eklenir. En yeni en üstte. Amaç: "ne zaman ne değişti, hangi kanıtla"
sorusunun tek yerden cevaplanması.

---

## 2026-10-23 · Geçiş 15 — oturum dayanıklılığı: token yenileme, Bangumi Legacy yedek yolu, oturum-doldu görünürlüğü (E85–E95)

**Durum:** `arena/9c5d6aae-kitsugi-beta` · HEAD: bu commit · temel: v2.4.348 (0b02e79) main (76eff11) ile birleştirildi (63cba7e6)
**Doğrulama:** tree-sitter Kotlin sözdizimi taraması 14 değişen dosyada temiz (BangumiApiClient.kt = `07_ARACLAR.md`'de belgeli bilinen yanlış pozitif; temel dosya da aynı hatayı veriyordu). **Derleme ve birim test BU ORTAMDA ÇALIŞTIRILMADI** (JDK/Gradle/Android SDK yok) — kullanıcı: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`

### Kapatılan hatalar (E85–E95, ayrıntı: 03_HATA_KAYDI)

| # | Özet | Düzeltme |
|---|---|---|
| E85 | Kitsu 30 günlük access token hiç yenilenmiyordu | `KitsuApiClient.refreshToken` + `getOrRefreshKitsuToken` (proaktif, mutex'li) + `forceRefreshKitsuToken` (401 sonrası) + `executeWithKitsuAuthRetry` (bir kez tekrar) |
| E86 | Bangumi'de yeni yapıma ilerleme yazımı `400 ...add subject to your collection first` ile düşüyordu | `isSubjectNotCollected` ayrımı → önce koleksiyon oluştur, ilerlemeyi yeniden yaz |
| E87 | Bangumi `/v0` normal OAuth token'ına `403 insufficient token scope` veriyordu (`parseTokenScope` JSON dizesini çözemez — `bangumi/server` `internal/auth/mysql_repository.go` + `TestParseTokenScope_NonObject`) | Resmî **Legacy PHP** yedek yolu: `POST /collection/{id}/update`, `POST /subject/{id}/update/watched_eps` (alan-bazlı koruma korunur; HTTP 200 gövdeli hatalar `parseLegacyWriteError` ile ayrıştırılır) |
| E88 | Bangumi `getValidToken` geçici yenileme başarısızında `null` döndürüp "bağlı değil" yalanı üretiyordu | Bayat-token fallback + `forceRefresh` kalıcı (401/403/invalid_grant) vs geçici ayrımı + başarılı `saveAuth`'ta cooldown reset |
| E89 | MAL refresh gövdesi elle URL-encode ediliyordu (`client_secret` bozulabilirdi); geçici ağ hatası "oturum doldu" sanılıyordu | `FormBody` + `getOrRefreshMalToken` bayat-token fallback + `forceRefreshMalToken` |
| E90 | Shikimori/MAL/Kitsu refresh'leri mutex'sizdi → tek kullanımlık refresh token paralel tüketiliyordu | Sağlayıcı başına `Mutex` (`kitsuRefreshMutex` / `shikimoriRefreshMutex` / `malRefreshMutex`) |
| E91 | Oturumu düşen hesap UI'da görünmüyordu | `markSessionExpired`/`isSessionExpired` + `${provider}_session_expired` bayrakları; ayarlar kartlarında "Oturum süresi doldu" |
| E92 | Shikimori 5 yazım ucu 401'i sessizce `false/null`a çeviriyordu | Uçlar `ShikimoriTokenException(401)` fırlatır; `syncEntryToShikimori` refresh + bir kez tekrar |
| E93 | Shikimori silmede bayat önbellek `rateId` 404 → hayalet kayıt kalıyordu | `resolveFreshRateId` ile taze kimlik ve ikinci DELETE |
| E94 | MAL içe aktarma 401'de yenileme yapmıyordu | `fetchWithOptionalAuthRetry` |
| E95 | Oturumu düşen hesap "Bağlı" görünmeye devam ediyordu | `ProfileSettings.is*SessionExpired` + `AccountHubItem` başlıkları |

### Saklanan sözleşmeler (bozan yok)

- Alan-bazlı yazım korundu: Legacy yolu da `A4` ("gönderilmeyen alan sunucuda sıfırlanır") gereği mevcut koleksiyondan doldurulur.
- `PendingSyncPolicy` ve kuyruk kararları değişmedi; 401 metinleri mevcut `isAuthError` sözlüğüyle uyumlu ("401"/"Unauthorized").
- Silme-yolu `E81` (`notinteresting`) ve tekrar-sözleşmeleri (`E72`/`E76`/`E77`) dokunulmadı.

### Yeni testler

- `BangumiLegacyWriteContractTest` (7 test): `CollectionType → Legacy status` eşlemesi, form alanlarının mevcut koleksiyondan doldurulması (puan/not silinmesin), 380-karakter sınırı, HTTP 200 gövdeli `invalid_token` ayrıştırması, `sessionExpiredKey` kanonikleştirme.

### Kanıt

- Resmî doküman: `bangumi/api` `open-api/api.yml` (`/collection/{subject_id}/{action}`, `/subject/{subject_id}/update/watched_eps`, `CollectionStatusType = wish|collect|do|on_hold|dropped`), `docs-raw/How-to-Auth.md`.
- Sunucu kaynağı: `bangumi/server` `web/routes.go` (`mw.NeedScope(ScopeWriteCollection)`), `internal/auth/mysql_repository.go` (`parseTokenScope` + `TestParseTokenScope_NonObject`), `ctrl/update_subject_collection.go` (`ErrSubjectNotCollected` yolu), `web/handler/user/update_episode_collection.go` (`400 "you need to add subject to your collection first"`).
- Referans istemciler: Mihon `BangumiApi`/`KitsuApi` (refresh_token grant + Content-Type sözleşmesi), czy0729/Bangumi `fetch.ts` (HTTP 200 gövdeli hata ayrıştırması), Kazumi `BangumiSyncService` (PAT doğrulama deseni).
- Sözdizimi taraması: 14 üretim + 1 test dosyası temiz (bilinen 1 yanlış pozitif hariç). **Derleme/test çalıştırılmadı.**
 — gerçek Gradle derleme ve birim test doğrulaması (E83, E84)

**Durum:** Derleme ve birim test ortamı başarıyla koşturuldu.
**Testler:** `SimklSyncContractTest` **11**; `data/auth` **210**; `PendingSync*` (data/local) **35**; TOPLAM **256 test firesiz geçti** (`BUILD SUCCESSFUL in 3m 33s` ve `3m 26s`).

### Kapatılan hatalar

| # | Hata | Kök neden | Düzeltme |
|---|---|---|---|
| **E83** | `CrossProviderIdentity.kt:667`'de `Unresolved reference 'anilist'` derleme hatası | `ConnectedSources` veri sınıfında alan adı `val aniList: Boolean = true` (büyük `L`) tanımlıyken küçük `l` ile `connected.anilist` yazılmış | `connected.anilist` → `connected.aniList` |
| **E84** | `RewatchTargetVocabularyTest.kt:200`'de `tek kaynakli grupta saglayici kendine yazmaz` assertion hatası (`[STATUS]` beklenmeyen yazım) | Test döngüsü her sağlayıcıyı kendi erişilebilir durum uzayı yerine tüm sağlayıcıların genel birleşik kümesi üzerinden sınıyordu; AniList'e MAL'in `Watching + is_rewatching=true` durumu verildiğinde politikanın AniList durumunu `REPEATING`e güncellemek istemesi doğruydu | `statesFor(provider)` eklenerek her sağlayıcının yalnızca kendi meşru ve erişilebilir durum uzayında tekil idempotans sınaması yapılması sağlandı |

### Kanıt
- `./gradlew.bat testFossDebugUnitTest --tests "com.kitsugi.animelist.data.auth.*"`: 210 test BAŞARILI (0 hata).
- `./gradlew.bat testFossDebugUnitTest --tests "com.kitsugi.animelist.data.remote.SimklSyncContractTest" --tests "com.kitsugi.animelist.data.local.PendingSync*"`: 46 test BAŞARILI (0 hata).
- Rapor: `KANIT_RAPORU_ALAN_BAZLI_ESITLEME_2026-10-22.md` **§22**.

---

## 2026-10-22 · Geçiş 13 — kullanıcı sorgusuyla tetiklenen çapraz denetim (E81, E82)

**Durum:** `arena/71836ee9-kitsugi-beta` · HEAD **`c08094d`** · sürüm 2.4.340-beta
**Testler:** `SimklSyncContractTest` **11** (mevcut 8 + yeni 3); `data/auth` **210** (sayı değişmedi, E82 çağrı biçimi düzeltildi)

### Kapatılan hatalar

| # | Hata | Kök neden | Düzeltme |
|---|---|---|---|
| **E81** | Simkl'de silinen kayıt eski liste durumunda kalabiliyordu | Normal yazım `watchStatusToSimkl` ile `notinteresting` gönderirken silme akışı geçersiz `"dropped"` dizgesini sert-kodluyordu; istemci de bunu `to` alanına normalize etmeden koyuyordu | Silme akışı `watchStatusToSimkl(WatchStatus.Dropped, entry.type)` kullanır; `SimklSyncContract.normalizeListStatus` istemci sınırında ikinci emniyettir; yanlış KDoc düzeltildi |
| **E82** | Geçiş 12'deki dört `newestOrNull` testi derlenmezdi | Testler, `e()` yardımcısında olmayan `id = ...` parametresiyle yazılmıştı | Senaryolar `e(...).copy(id = ...)` biçimine çevrildi |

### Süreç notu

- E81 testleri eklenirken mevcut `SimklSyncContractTest.kt` çalışma ağacında geçici olarak ezildi; commit öncesi `git diff` ile yakalandı, `HEAD` sürümü geri getirildi ve mevcut 8 test korunarak 3 test eklendi. Ezilmiş içerik commit/push edilmedi; kalıcı kayıp yoktur.

### Kanıt

- Kod: `data/auth/SimklSyncManager.kt`, `data/remote/SimklApiClient.kt`, `data/remote/SimklSyncContract.kt`.
- Testler: `data/remote/SimklSyncContractTest.kt`, `data/auth/CrossSyncMergePolicyTest.kt`.
- Rapor: depodaki `KANIT_RAPORU_ALAN_BAZLI_ESITLEME_2026-10-22.md` **§21**.
- Sözdizimi taraması: 5 dosyada yalnız `SimklApiClient.kt` satır 167 bilinen yanlış pozitifi. **Derleme ve birim test ÇALIŞTIRILMADI** — kullanıcı: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`

---

## 2026-10-22 · Geçiş 12 — geri-okuma koşumu (T05) + E76–E80

**Durum:** `arena/71836ee9-kitsugi-beta` · HEAD **`feddfbc`** · sürüm 2.4.340-beta
**Testler:** `data/auth` **210** (24 dosya; +34 bu turda)

### Kapatılan hatalar

| # | Hata | Kök neden | Düzeltme |
|---|---|---|---|
| **E76** | Bangumi/Simkl'de her turda aynı STATUS isteği (sonsuz yazım) | `narrow()` `!rewatchingAsStatus` koşuluyla REWATCH_COUNT'a STATUS ekliyordu; tekrarı ifade edemeyen hedeflerde sayaç hep 0 kaldığından istek hiç sönmüyordu (A5) | Enjeksiyon yalnız `AS_FLAG` (MAL/Kitsu); UNSUPPORTED'ta küme boş |
| **E77** | MAL yazıcısı E72'yi bozuyordu: tekrar yazımında `watching → completed` | Politika doğru karar veriyordu (`statusFor`) ama yazıcının gövdesi `toMalStatus(Repeating)="completed"` ile koşulsuzdu | Tekrar yazımında mevcut MAL durumu tek GET ile okunur; saf `malStatusForWrite` allowlist'i korur, bilinmeyende `completed` |
| **E78** | "En yeni" sırasının kalan kopyaları (T20): 3 elden yazma + 2 tiebreak'siz `maxByOrNull` | Tanım dağınık | `CrossSyncMergePolicy.NEWEST_FIRST` + `newestOrNull()` tek kaynak; AVM/Backup/smartImport bağlandı |
| **E79** | Simkl'de bırakılanlar içe aktarımda "planlandı" oluyordu | `mapSimklStatus` `notinteresting` kovasını tanımıyordu | `notinteresting → Dropped` |
| **E80** | Simkl notu yazılıyor ama okunmuyordu; `notes=null` gelen satır yerel notu siliyordu | İçe aktarma `notes` doldurmuyordu; resmî okuma alanı `?memos=yes → memo.text` kullanılmıyordu | Liste isteği `?memos=yes`; `memo.text` (≤140) `notes`a alınır |

### T05 + puan sınırları

- `ProviderReadBackIdempotenceTest`: 9 senaryo × 6 sağlayıcı; yazım sonrası `dirtyFieldsFor(readBack, merge)` boş olmalı.
- `ProviderScoreBoundaryTest` (T07/T08 kod düzeyi): AniList 0..100, Kitsu 2..20 (0/1 yok), Bangumi 1..10 (boş → gövdeye konmaz).
- `MalRewatchStatusWriteTest` (6), `SimklImportParsingTest` (6), `newestOrNull` (4).

### Kanıt

- Kod: `data/auth/SyncField.kt`, `data/auth/MalSyncManager.kt`, `data/auth/CrossSyncMergePolicy.kt`, `data/auth/SimklImportManager.kt`, `ui/app/AuthViewModel.kt`, `data/local/MediaEntryBackup.kt`, `data/local/MediaEntryRepository.kt`.
- Rapor: depodaki `KANIT_RAPORU_ALAN_BAZLI_ESITLEME_2026-10-22.md` **§20**.
- Resmî kanıt: api.simkl.org/guides/sync (`memos=yes`, `memo.text` ≤140).
- Sözdizimi taraması: 12 dosya / 0 hata. Model: `fuzz_merge` 0 ihlal; `fuzz2` Geçiş 11 tabanıyla aynı (`added_row_lowers=66`, §19.5 sözleşmesi). **Derleme ve birim test ÇALIŞTIRILMADI** — kullanıcı: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`

---

## 2026-10-22 · Geçiş 11 — kuyruk testleri (T02) + kuyrukta sessiz alan kaybı

**Durum:** `arena/71836ee9-kitsugi-beta` · HEAD **`a05ef60`** · sürüm 2.4.340-beta
(Devir notu: bu dal `main` `26bb044` üstünden açılmıştı; `0762faf` onun torunu olduğu için dal fast-forward ile devir noktasına getirildi, başka dala yazılmadı.)
**Testler:** `data/auth` **176** (değişmedi) + `data/local` **+35 yeni** (`PendingSyncPolicyTest` 11, `PendingSyncDrainerTest` 24; dizin toplamı 53)

### Kapatılan hatalar

| # | Hata | Kök neden | Düzeltme |
|---|---|---|---|
| **E73** | Çevrimdışıyken kuyruğa giren alan (ör. not) sessizce kayboluyordu | Doğrudan yazım başarılı olunca `removeFromQueueIfPresent` aynı satırın kuyruk işini **alan kümesine bakmadan** siliyordu: `{NOTES}` işi, yalnız `{PROGRESS}` gönderen başarılı düzenlemeyle düşüyordu (A6 ihlali) | Alan bazlı düşme: `PendingSyncDrainer.removeSyncedFields` + saf `PendingSyncPolicy.remainingFields`; yalnız gönderilen alanlar düşer, kalan alanlar `_syncFields` güncellenerek kuyrukta kalır. Çağrılmayan `deleteStale` de kaldırıldı |
| **E74** | Yetki hatası istisna olarak geldiğinde deneme sayacı yanıyordu | Kontrol yalnız `SyncResult.errors` metinlerine bakıyordu (3 kopya); çağrı fırlatırsa metin boş kalıp sayaç artıyor, kullanıcıya da yanlış "çevrimdışı" mesajı gidiyordu | Tek yer: `PendingSyncPolicy.isAuthError` + `decision(..., thrownMessage)` istisna mesajını da kapsar |
| **E75** | Birleşik kayda taban seçimi aday sırasına bağlıydı | `AuthViewModel` `newest = maxByOrNull { updatedAt }` — E70'in yasakladığı eşitlik bozucusuz seçim | `CrossSyncMergePolicy` ile aynı sıra: `updatedAt` → ilerleme → kaynak adı → satır `id` |

### T02 (kuyruk testleri)

- Kararlar saf `PendingSyncPolicy`ye taşındı (MAX_RETRIES=5, uygunluk, karar tablosu, alan birleştirme/düşme, satır eşleştirme, yetki metni); `drain`e `Context`'siz çalıştırıcı dikişi eklendi (kamu imzası korundu).
- Sahte DAO ile enqueue birleştirme, MAX_RETRIES, yetki sayacı, freshBudget, DISABLED davranışı test ediliyor.

### Kanıt

- Kod: `data/local/PendingSyncPolicy.kt` (yeni), `data/local/PendingSyncDrainer.kt`, `data/local/PendingSyncDao.kt`, `data/local/MediaEntryRepository.kt`, `ui/app/AuthViewModel.kt`.
- Rapor: depodaki `KANIT_RAPORU_ALAN_BAZLI_ESITLEME_2026-10-22.md` **§19**.
- Sözdizimi taraması: 7 dosya / 0 hata. Model koşturması: `fuzz_merge` 0 ihlal; `fuzz2` `added_row_lowers=66` analizi §19.5 (kod hatası değil — kırpma sözleşmesi; yazım katmanı geriye yazmaz). **Derleme ve birim test ÇALIŞTIRILMADI** (JDK/Gradle/Android SDK yok) — kullanıcı: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`

---

## 2026-10-22 · Geçiş 10 — kardeş satır aynası + hedef sözlüğünde tekrar

**Durum:** `arena/38590839-kitsugi-beta` · HEAD **`0762faf`** · sürüm 2.4.340-beta
**Testler:** `data/auth` **176** (20 dosya; +31 bu turda)

### Kapatılan hatalar

| # | Hata | Kök neden | Düzeltme |
|---|---|---|---|
| **E57** | Kardeş satır aynası birleştirme kurallarını deliyordu | `mirrorUserFieldsToSiblings` **koşulsuz** kopyalıyordu: ilerleme/cilt/tekrar gerileyebiliyor, yalnız puan düzenlendiğinde `status` da değişiyor, `updatedAt` eziliyordu | Ayna saf `SiblingMirrorPolicy`'ye taşındı: yalnız **değişen alanlar**, monotonluk (`max`), `Dropped`/`Paused` yapışkanlığı, puan silmenin yayılmaması, tarih birleştirme, `updatedAt` korunur, değişmeyen satır yazılmaz |
| **E72** | Yayında olan yapım MAL'de "tamamlandı"ya çevriliyordu **ve** her turda aynı istek atılıyordu | `statusFor`, birleşik `Repeating`i her `AS_FLAG` hedefte `repeatingStatus`a zorluyordu (MAL → `completed`); oysa MAL `is_rewatching` durumdan bağımsızdır ve içe aktarma `watching`+bayrak üretir | Yeni `rewatchFlagNeedsRepeatingStatus`: MAL'de durum **korunur** (yalnız bayrak yazılır), Kitsu'da `reconsuming` yalnız `current` ile gönderildiği için durum `current`a çekilir. `repeatingStatus` yalnız `Planned` satırda kullanılır |

### Neden bu ikisi kritikti

- E57/1, "ilerleme asla gerilemez" kuralını **veri katmanında** deliyordu: 12 ilerlemeli AniList satırı, 10 ilerlemeli Kitsu satırı düzenlendiğinde 11'e iniyor; birleşim 11 olup AniList'e **12 → 11 geri yazım** üretiliyordu.
- E72, kabul ölçütlerinden ikisini birden bozuyordu: **doğru değer** (durum bozuluyor) ve **idempotans** (her turda aynı PATCH).

### Kanıt

- Kod: `data/auth/SiblingMirrorPolicy.kt` (yeni), `data/auth/SyncField.kt` (`statusFor`, `rewatchFlagNeedsRepeatingStatus`), `data/local/MediaEntryRepository.kt` (iki çağrı yeri).
- Testler: `SiblingMirrorPolicyTest` (**19**), `RewatchTargetVocabularyTest` (**12** — tek kaynaklı grupta 6 sağlayıcı × 10 durum tablosu dahil).
- Rapor: `05_KANIT_RAPORU_2026-10-22.md` **§18**.
- Sözdizimi taraması: 4 dosya / 0 hata. **Derleme ve birim test ÇALIŞTIRILMADI** (JDK/Gradle/Android SDK yok).

---

## 2026-10-22 · Geçiş 9 — birleştirme kuralının kendi içinde denetimi

- **E66/E67/E67b/E68** kapatıldı (tekrar sırasında ilerleme, bayat `REPEATING` kilidi, bayrak/durum çelişkisi, sıraya bağlı kırpma) — commit `4bab31b`.
- **E69** kapandı: MAL'de alan-daraltılmış PATCH yarım kayıt oluşturuyordu → yazımdan önce varlık doğrulaması (`GET ?fields=my_list_status`) — commit `08e339e`.
- **E70/E71** kapandı: birleşim aday sırasına bağlıydı; `id` eşitlik bozucusu yoktu ve öncelikli toplam ilerlemeyi kırpıyordu — commit `11871f8`.
- Model koşturması (Python, 5.000 senaryo) ile bulundu; **Kotlin testi değildir.**

## 2026-10-22 · Geçiş 8 — dış kaynak doğrulaması

- MALSync (MAL/AniList/Kitsu/Simkl/Shikimori), SuperMarcus MAL şeması, aniyomiorg, Harbor, anime-db-plugins, AL-Additions okundu; **E62–E65** kapatıldı (MAL tarih temizleme 400 dayanıklılığı, `WRITE/CLEAR/SKIP` tarih kararı, ilerleme alanı çift adı, `is_favorited` seçicisi).
