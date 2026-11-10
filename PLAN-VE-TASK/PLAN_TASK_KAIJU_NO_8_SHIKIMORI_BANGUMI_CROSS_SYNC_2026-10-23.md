# Kitsugi — Kaiju No. 8 (`MAL 52588`) Shikimori & Bangumi Çapraz Eşitleme, 6-Platform Kümeleme ve Tam CI/DEX Düzeltmesi (`E96`–`E106`)

**Tarih:** 2026-10-23  
**Dal:** `arena/86b99c20-kitsugi-beta`  
**Temel commit:** `c3d639c` (`v2.4.351` / `main: 76eff11d0793914e0dc6bb4a1e18ce3b51e075b3`)  
**Son commit:** `c8e77e5` (`v2.4.353`)  
**Durum:** Tüm kod düzeltmeleri, 720-permütasyon JUnit testleri, Bangumi MASTER doğrulaması, Ortak Taksonomi doğrulaması, FOSS Debug APK + FOSS R8 Release APK derlemesi ve DEX bayt kodu doğrulaması (`VerifyError = 0`) **%100 tamamlandı ve kanıtlandı.**

---

## 1. Kullanıcı şikâyeti ve kabul ölçütü

`Listem` ekranında `kaiju no 8` araması yapıldığında birincil `AL` kartı (`Tamamlandı · 12/12 bölüm · ★ 9/10`) üzerinde yalnızca **Simkl (`S`)**, **Kitsu** ve **MAL** rozetleri görünürken **Shikimori** ve **Bangumi** rozetleri eksik kalıyordu; üstelik `Kaiju No. 8` her iki platformda da mevcut olmasına rağmen birleşmiyor ve eşitlenmiyordu. Kitsu'da ise süresi dolmuş oturumdan çıkış-giriş yapılınca kayıt otomatik olarak birleşmişti.

Kabul ölçütleri:
1. `Kaiju No. 8` (`MAL 52588`) dahil tüm yapımlar 6 sağlayıcının (**AniList, MAL, Kitsu, Simkl, Shikimori, Bangumi**) **herhangi bir sıralamasında (`6! = 720` permütasyon)** tek bir birleşik kümede toplanmalı; asla ikiye bölünmemeli veya yanlışlıkla `identityReviewRequired` ile kilitlenmemelidir.
2. Kümede halihazırda 2 veya daha fazla kaynak varken (`AL + MAL + Simkl + Kitsu`) yapılan toplu veya tekil eşitlemede **Shikimori** ve **Bangumi** eklemeleri bloke edilmemelidir.
3. Uzak API'ye başarıyla yazılan her platform için yerel Room `media_entries` tablosuna anında yansıtma satırı yazılmalı; ek bir manuel liste içe aktarması gerekmeden `Listem` kartında tüm platform rozetleri görünmelidir.
4. Tüm değişiklikler gerçek JDK 17 + Android SDK 36 + Gradle JUnit + FOSS Debug/Release APK + R8 DEX bayt kodu doğrulamasından **sıfır hata** ile geçmelidir.

---

## 2. Araştırmada doğrulanan kök nedenler (`E96`–`E106`)

### 2.1 `E96` — Toplu eşitlemede `identityVerdict?.allowsAdditions ?: true` ekleme kilidi (`AuthViewModel.kt`, `CrossSyncIdentityGuard.kt`, `CrossSyncClusterEngine.kt`)
- `AuthViewModel.syncPlatforms` içinde yeni platformlara ekleme izni `val allowsAdditions = identityVerdict?.allowsAdditions ?: true` ile kontrol ediliyordu.
- Kümede 2'den fazla kaynak oluştuğu an (`AniList + MAL + Simkl + Kitsu` → `multiSource = true`), Jikan/MAL doğrulaması ağ gecikmesi veya hız sınırına takıldığında `CrossSyncIdentityGuard.verifyMalId` `Unverifiable` döndürüyor; `Unverifiable.allowsAdditions == false` olduğu için **Shikimori ve Bangumi dahil tüm yeni platform eklemeleri %100 bloke ediliyordu**.
- Ek olarak `candidateMalId`, `entries.firstNotNullOfOrNull { MediaIdentity.realMalId(it) }` ile okunduğundan `MyListIdentityCrosswalk` üzerinden gelen gerçek `52588` kimliği gözden kaçabiliyordu.

### 2.2 `E105` — `CrossSyncCandidateIndex` içinde birbirini hiç sorgulamayan iki ayrı başlık haritası (`CrossSyncCandidateIndex.kt`)
- `CrossSyncCandidateIndex`, genel başlıkları `groupsByTitleAlias`, Japonca alanı ise ayrı `groupsByJapaneseTitle` haritasında tutuyordu.
- `Bangumi` satırı `title = "怪獣8号", titleJapanese = null` ile geldiğinden `"怪獣8号"` anahtarı **yalnızca `groupsByTitleAlias`** içine yazılıyor; `AniList` satırı ise `title = "Kaijuu 8-gou", titleJapanese = "怪獣８号"` ile geldiğinden `"怪獣8号"` anahtarını **yalnızca `groupsByJapaneseTitle`** içinde arıyordu!
- Sonuç olarak `possibleMatches()` **Bangumi kümesini `MediaIdentity.sameMedia()` fonksiyonuna aday olarak bile döndürmüyordu**.

### 2.3 `E97` & `E106` — Küme birleşmesinde hayalet grup artıkları, `"gou"` belirteci, sıra ekli rakam kaçışı ve `id == 0` çarpışması (`CrossSyncClusterEngine.kt`, `CrossSyncCandidateIndex.kt`, `CrossSyncIdentityGuard.kt`, `MediaIdentity.kt`)
- İki aday grup `canCoalesceAllMatches` ile birleştiğinde yutulan ikincil grup (`secondary`) indeks kovalarından silinmiyordu (`E106`). Sonraki sağlayıcı satırı geldiğinde hem `primary` hem hayalet `secondary` dönerek sahte çakışma üretiyordu.
- `MediaIdentity` yapısal belirteçlerinde Japonca sayaç eki `"gou"` yoktu (`"Kaijuu 8-gou"` ↔ `"Kaiju No. 8"`).
- `digitRunRegex` sıra ekli `"2nd Season"` içindeki `2` rakamını yakalamadığı için 1. sezon ile 2. sezon karışabiliyordu.
- Bellek içi `id == 0` satırları `MyListIdentityCrosswalk` içinde aynı `0` anahtarında çarpışıyordu.

### 2.4 `E98` — Canlı ve toplu eşitleme sonrası yerel `media_entries` yansıtma satırı eksikliği (`ExternalListSyncManager.kt`)
- `syncEntryNow` / `syncEntryNowDetailed` hedef platforma başarıyla yazsa bile yerel Room `media_entries` tablosuna o sağlayıcının satırını yazmıyordu; kullanıcı ayrıca tam liste içe aktarma yapmadıkça `Listem` kartında **Shikimori** ve **Bangumi** rozetleri görünmüyordu.

### 2.5 `E99` & `E101` — Kardeş başlık / gerçek `malId` yayılım eksikliği ve tek alanlı CJK karşılaştırması (`AuthViewModel.kt`, `ShikimoriApiClient.kt`, `ShikimoriImportManager.kt`, `MediaIdentity.kt`)
- `mergedEntry` oluşturulurken kümedeki kardeş satırlarda bulunan İngilizce (`"Kaiju No. 8"`), Japonca (`"怪獣８号"`) başlıklar ve gerçek `malId` (`52588`) birleşik kayda aktarılmıyor; `ShikimoriImportManager` ise API'nin döndürdüğü `japanese` başlığını kaydetmiyordu.
- `MediaIdentity.titleMatch` yalnızca `a.titleJapanese` ile `b.titleJapanese` alanlarını karşılaştırıyordu (`Bangumi` `title = "怪獣8号", titleJapanese = null` ↔ `AniList` `titleJapanese = "怪獣８号"` eşleşmiyordu).

### 2.6 `E100` & `E104` — Bangumi NFKC rakam farkı, eşadlı yapım yıl/infobox ayrımı, resmî Japonca başlık köprüsü ve çift yönlü crosswalk (`BangumiIdNamespace.kt`, `BangumiSyncManager.kt`, `MyListIdentityCrosswalk.kt`, `BangumiSubjectTitleResolver.kt`, `KitsugiBangumiDetailClient.kt`)
- `"怪獣８号"` içindeki tam genişlikli `８` (`U+FF18`), NFKC uygulanmadan Bangumi aramasına gönderildiğinde yarı genişlikli `"怪獣8号"` kaydıyla eşleşmiyordu.
- `selectUniqueSubjectMatch` `infobox` takma adlarını ve `expectedYear` (`2024`) yıl bilgisini kullanmıyordu; yerel satırda CJK başlık yoksa AniList/Jikan resmî `native` Japonca başlığı çekilmiyordu.
- `MyListIdentityCrosswalk.build`, `AniList`/`MAL` satırlarında `CrossProviderIdentityCache` ve `bgm_detail_xref_v1` önbelleklerini atlıyordu; Bangumi çözümleyicileri de buldukları AniList `idMal` değerini `BangumiLocalMappingCache`'e geri yazmıyordu.

### 2.7 `E102` & `E103` — Shikimori çoklu alan adı yedekliliği (`shikimori.io` / `.one` / `.tv`) ve oturum yenileme (`ShikimoriApiClient.kt`, `ShikimoriPosterResolver.kt`, `AuthViewModel.kt`, `ExternalListSyncManager.kt`, `AppRoot.kt`)
- Tüm Shikimori REST ve GraphQL kapak istekleri tek sabit alan adına gidiyordu; ISP HTML engeli (`peekBody(256)`), bağlantı hatası veya `502..504`/`520..529`/`451` durumunda `shikimori.io` → `shikimori.one` → `shikimori.tv` otomatik failover eklendi.
- Kitsu statik token okumaları `getOrRefreshKitsuToken` ile değiştirildi; süresi dolan oturumların toplu eşitlemede görünür raporlanması ve yeniden girişte bekleyen kuyruk işlerinin taze hakla boşaltılması sağlandı.

---

## 3. Tamamlanan görevler

- [x] **E96:** `CrossSyncIdentityGuard.allowsBulkAddition` (`!blocksCrossWrite`) ve `extractCrosswalkOrCandidateMalId` ile çok kaynaklı kümede yeni platform ekleme kilidini kaldır.
- [x] **E97 & E105 & E106:** `CrossSyncCandidateIndex` başlık kovalarını `groupsByAnyTitle` altında birleştir, `replaceGroup(secondary, primary)` ile birleşen grupları indeksten tahliye et, `"gou"` + `digitRunRegex` + `clusterYearsCompatible` + `assignClusterRowIds` ile **720 permütasyonun tamamında tek küme garantisi** sağla.
- [x] **E98:** `ExternalListSyncManager.buildLocalProviderMirrorEntry` + `upsertSyncedLocalMirrors` ile `OUTCOME_SYNCED` dönen her platform için yerel `media_entries` yansıtma satırını anında yaz.
- [x] **E99 & E101:** Kardeş satırlardaki İngilizce/Japonca başlıkları ve gerçek `malId`'yi `mergedEntry`'ye taşı; `ShikimoriImportManager` içinde `japanese` başlığını sakla; `MediaIdentity.titleMatch` içinde çapraz alan CJK↔Latin karşılaştırması yap.
- [x] **E100 & E104:** Bangumi NFKC arama normalizasyonu, `infobox` + `expectedYear` seçimi, AniList/Jikan resmî `native` Japonca başlık köprüsü, `BangumiLocalMappingCache` geri yazımı ve çift yönlü `MyListIdentityCrosswalk` okumasını tamamla.
- [x] **E102 & E103:** `ShikimoriApiClient` + `ShikimoriPosterResolver` üzerinde 3 alan adlı otomatik failover (`executeShikimori`) ve Kitsu/oturum/kuyruk canlandırma akışını tamamla.
- [x] **CI / JUnit / APK / R8 DEX:** Üç GitHub Actions iş akışının tamamını (`android-build.yml`, `bangumi-master.yml`, `taxonomy-master.yml`) gerçek derleme ve DEX doğrulamasıyla %100 yeşile çıkar.

---

## 4. Değişen ve eklenen dosyalar (`c3d639c..c8e77e5` — toplam 36 kod/CI/takip dosyası + bu belge)

### Üretim Kodu (`app/src/main/...`)
1. `app/src/main/java/com/kitsugi/animelist/AppRoot.kt`
2. `app/src/main/java/com/kitsugi/animelist/data/auth/BangumiSyncManager.kt`
3. `app/src/main/java/com/kitsugi/animelist/data/auth/CrossSyncCandidateIndex.kt`
4. `app/src/main/java/com/kitsugi/animelist/data/auth/CrossSyncClusterEngine.kt` *(yeni)*
5. `app/src/main/java/com/kitsugi/animelist/data/auth/CrossSyncIdentityGuard.kt`
6. `app/src/main/java/com/kitsugi/animelist/data/auth/ExternalListSyncManager.kt`
7. `app/src/main/java/com/kitsugi/animelist/data/auth/ShikimoriApiClient.kt`
8. `app/src/main/java/com/kitsugi/animelist/data/auth/ShikimoriImportManager.kt`
9. `app/src/main/java/com/kitsugi/animelist/data/local/MyListIdentityCrosswalk.kt`
10. `app/src/main/java/com/kitsugi/animelist/data/remote/BangumiIdNamespace.kt`
11. `app/src/main/java/com/kitsugi/animelist/data/remote/BangumiSubjectTitleResolver.kt`
12. `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiBangumiDetailClient.kt`
13. `app/src/main/java/com/kitsugi/animelist/data/remote/ShikimoriPosterResolver.kt`
14. `app/src/main/java/com/kitsugi/animelist/model/MediaIdentity.kt`
15. `app/src/main/java/com/kitsugi/animelist/ui/app/AuthViewModel.kt`
16. `app/src/main/res/raw/taxonomy_catalog.json`
17. `app/src/main/resources/bangumi/orgs.tsv`
18. `diag/bangumi_master/harness/Main.kt`
19. `diag/bangumi_master/master.json`
20. `diag/bangumi_master/orgs.tsv`
21. `version.properties`

### Birim Testleri ve CI İş Akışları (`app/src/test/...`, `.github/workflows/...`)
22. `app/src/test/java/com/kitsugi/animelist/data/auth/CrossSyncClusterEngineTest.kt` *(yeni — 720 permütasyon testi dahil)*
23. `app/src/test/java/com/kitsugi/animelist/data/auth/ExternalListSyncMirrorTest.kt` *(yeni)*
24. `app/src/test/java/com/kitsugi/animelist/data/auth/ShikimoriOAuthTest.kt`
25. `app/src/test/java/com/kitsugi/animelist/data/remote/BangumiIdNamespaceTest.kt`
26. `app/src/test/java/com/kitsugi/animelist/data/remote/BangumiSubjectTitleResolverTest.kt`
27. `app/src/test/java/com/kitsugi/animelist/data/remote/KitsugiBangumiDetailClientTest.kt`
28. `app/src/test/java/com/kitsugi/animelist/model/MediaIdentityCrossScriptTest.kt` *(yeni)*
29. `app/src/test/java/com/kitsugi/animelist/utils/BangumiMasterDictionaryTest.kt`
30. `app/src/test/java/com/kitsugi/animelist/utils/KitsugiTranslationsTest.kt`
31. `app/src/test/java/com/kitsugi/animelist/utils/TaxonomyLabelsTest.kt`
32. `.github/workflows/android-build.yml`
33. `.github/workflows/bangumi-master.yml`
34. `.github/workflows/taxonomy-master.yml`

### Plan / Task / Kanıt Belgeleri (`PLAN-VE-TASK/` + Kök Belge)
35. `PLAN_TASK_KAIJU_NO_8_SHIKIMORI_BANGUMI_CROSS_SYNC_2026-10-23.md` *(bu belge)*
36. `PLAN-VE-TASK/00_BASLA_BURADAN.md`, `01_PLAN.md`, `02_TASK_TAKIP.csv/.xlsx`, `03_HATA_KAYDI.csv/.xlsx`, `04_DEVAM_TALIMATI.md`, `05_KANIT_RAPORU_2026-10-23_E83-E105.md`, `06_ILERLEME_GUNLUGU.md`, `07_ARACLAR.md`, `ANTIGRAVITY-DEVIR.md`, `KANIT_RAPORU_ALAN_BAZLI_ESITLEME_2026-10-22.md`

---

## 5. Gerçek CI Derleme, JUnit ve R8 DEX Doğrulama Sonuçları (`c8e77e5`)

- **`🏗️ Android Derleme Doğrulama` (`android-build.yml`, Run `39990965003` — `23m 50s`, `completed / success`):**
  - Tüm JUnit birim test paketleri (`CrossSyncClusterEngineTest` 720 permütasyon, `MediaIdentityCrossScriptTest`, `ExternalListSyncMirrorTest`, `BangumiIdNamespaceTest`, `BangumiSubjectTitleResolverTest`, `ShikimoriOAuthTest`, `ShikimoriPosterResolverTest`, `AccountDatabaseSafetyTest`, vb.) **0 hata ile geçti**.
  - **`app/build/outputs/apk/foss/debug/Kitsugi-Beta-v2.4.353-foss.apk`:** `22` DEX dosyası, `409.633` metot gövdesi, `11.261.566` talimat doğrulandı, `max invoke register words = 176/255`, **`VerifyError count = 0`**.
  - **`app/build/outputs/apk/foss/release/Kitsugi-Beta-v2.4.353-foss.apk`:** `5` DEX dosyası, `254.610` metot gövdesi, `7.266.213` talimat doğrulandı, `max invoke register words = 176/255`, **`VerifyError count = 0`**.
- **`🧩 Ortak Taksonomi Doğrulama` (`taxonomy-master.yml`, Run `39990965044` — `6m 24s`, `completed / success`):** `verify_taxonomy_master.py` + `TaxonomyLabelsTest` **%100 yeşil**.
- **`🎌 Bangumi MASTER Doğrulama` (`bangumi-master.yml`, Run `39986692376` — `4m 35s`, `completed / success`):** Python doğrulayıcı + `Main.kt` + `BangumiMasterDictionaryTest` **%100 yeşil**.
