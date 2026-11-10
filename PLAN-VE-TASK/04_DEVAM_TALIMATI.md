# 04 · DEVAM TALİMATI (Geçiş 16 Sonrası — `c8e77e5`)

## Güncel Durum (`2026-10-23` · Dal: `arena/86b99c20-kitsugi-beta` · Son Commit: `c8e77e5`)

1. **`Kaiju No. 8` (`MAL 52588`) — Shikimori + Bangumi + 6-Platform Çapraz Eşitleme (`E96`–`E104`):** **TAMAMLANDI VE CI'DA KANITLANDI.**
   - **E96:** `syncPlatforms` içindeki `identityVerdict?.allowsAdditions ?: true` kilidi `CrossSyncIdentityGuard.allowsBulkAddition(verdict)` (`!blocksCrossWrite`) ile düzeltildi; `extractCrosswalkOrCandidateMalId` ile crosswalk MAL kimliği çıkarıldı.
   - **E97:** `CrossSyncCandidateIndex` içinde ayrık `groupsByTitleAlias` / `groupsByJapaneseTitle` haritaları `groupsByAnyTitle` altında birleştirildi (`Bangumi` `"怪獣8号"` ↔ `AniList` `"怪獣８号"` doğrudan bulunur), `index.replaceGroup(secondary, primary)` ile birleşen grupların hayalet kalıntıları temizlendi, `"gou"` belirteci, `digitRunRegex` (`"2nd Season"` ayrımı), `clusterYearsCompatible` ve `assignClusterRowIds` (`1..N`) eklendi (**`6! = 720` permütasyonda tek küme kanıtı**).
   - **E98:** `ExternalListSyncManager` içinde `OUTCOME_SYNCED` dönen her platform için yerel `media_entries` yansıtma satırı (`buildLocalProviderMirrorEntry` + `upsertSyncedLocalMirrors`) anında yazılır; manuel içe aktarma beklemeden `Listem` kartında tüm platform rozetleri görünür.
   - **E99 & E101:** Birleşik kayda kardeş satırlardaki İngilizce/Japonca başlıklar ve gerçek `malId` taşındı; Shikimori `japanese` alanı içe aktarmada korundu; `MediaIdentity.titleMatch` çapraz alan CJK↔Latin eşleştirmesine geçirildi.
   - **E100 & E104:** Bangumi NFKC tam genişlikli rakam normalizasyonu (`"怪獣８号"` → `"怪獣8号"`), `infobox` + `expectedYear` (`2024`) seçimi, AniList/Jikan resmî `native` Japonca başlık köprüsü, `BangumiLocalMappingCache` geri yazımı ve `MyListIdentityCrosswalk` içinde tüm satırlarda `CrossProviderIdentityCache` + `bgm_detail_xref_v1` okuması tamamlandı.
   - **E102 & E103:** `shikimori.io` / `shikimori.one` / `shikimori.tv` otomatik failover (`executeShikimori`), Kitsu `getOrRefreshKitsuToken`, süresi dolmuş oturum görünürlüğü ve yeniden girişte kuyruk canlandırması tamamlandı.

2. **Gerçek JDK 17 + Android SDK 36 Derleme, JUnit ve R8 DEX Doğrulaması (`E105`):** **%100 YEŞİL.**
   - `🏗️ Android Derleme Doğrulama` (`android-build.yml`): Tüm JUnit testleri + FOSS Debug APK (`22` DEX, `409.633` metot, `11.261.566` talimat, `VerifyError = 0`) + FOSS R8 Release APK (`5` DEX, `254.610` metot, `7.266.213` talimat, `VerifyError = 0`).
   - `🎌 Bangumi MASTER Doğrulama` (`bangumi-master.yml`): Python doğrulayıcı + `Main.kt` + `BangumiMasterDictionaryTest` **%100 yeşil**.
   - `🧩 Ortak Taksonomi Doğrulama` (`taxonomy-master.yml`): Python doğrulayıcı + `TaxonomyLabelsTest` **%100 yeşil**.

## Fiziksel Cihazda Son Kontrol (`T18` / `T31` / `T32`)
1. `arena/86b99c20-kitsugi-beta` dalından üretilen `Kitsugi-Beta-v2.4.353-foss.apk` paketini cihaza kurun.
2. `Ayarlar → Hesaplar ve Senkronizasyon → Toplu Eşitle` çalıştırın.
3. `Listem` ekranında `kaiju no 8` aramasında **AniList + MAL + Simkl + Kitsu + Shikimori + Bangumi** rozetlerinin tek kart altında eksiksiz birleştiğini doğrulayın.
