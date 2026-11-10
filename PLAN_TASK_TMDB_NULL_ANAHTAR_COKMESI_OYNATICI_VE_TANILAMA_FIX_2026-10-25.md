# Plan / Task — `Key "tmdb_null" was already used` Çökmesi, mpv Altyazı Donması ve Tanılama Düzeltmeleri

**Tarih:** 25 Ekim 2026  
**Dal:** `arena/093295a6-kitsugi-beta`  
**Taban:** `v2.4.371` (`36a80e6b`) — gerçek kaynak hattı; dala `4244c24f` ile birleştirildi (GitHub `main` v2.4.340'ta eski kaldığı için).  
**Çöken derleme:** `v2.4.369-beta.29882115` · Redmi 2405CRPFDG · Android 15 (API 35)  
**Kapsam:** Drive klasöründeki 12 raporun satır satır denetimi; bulunan tüm hata, uyarı ve sorunların kök nedenleriyle düzeltilmesi.  
**Ayrıntılı rapor:** `KOK_NEDEN_RAPORU_TMDB_NULL_ANAHTAR_COKMESI_VE_RAPOR_DENETIMI_2026-10-25.md`

## Amaç

Detay sayfasındaki çökmeyi (`IllegalArgumentException: Key "tmdb_null" was already used`) kalıcı olarak gidermek; raporlarda görülen oynatıcı donmalarını, altyazı gelince akışın baştan hazırlanmasını ve yanlış "SESSİZ ÇÖKME" alarmlarını düzeltmek; aynı türden gizli çökmelerin başka listelerde tekrarlanmasını engellemek.

## Plan

1. **Rapor denetimi:** 12 dosyanın (4.375 satır) tamamını oku; E/W satırlarını kümeleyip (415 küme) tek tek sınıflandır; ölüm nedenlerini zaman çizelgesine diz.
2. **Kaynak hattını doğrula:** Çöken APK'nın hangi koddan derlendiğini bul; düzeltmeleri o hattın üstüne kur.
3. **Kritik çökme:** Yığın izinden çöken listeyi bul, kimliği boş gelen kaynakları belirle, anahtarı her koşulda benzersiz yap.
4. **Proaktif koruma:** Aynı riski taşıyan Lazy liste/ızgara/Pager anahtarlarını tek bir yardımcıyla güvenceye al.
5. **Oynatıcı:** Ana iş parçacığını kilitleyen mpv `sub-add` çağrılarını ve altyazı gelince akışın yeniden hazırlanmasını düzelt.
6. **Tanılama:** Yanlış "SESSİZ ÇÖKME" kayıtlarını ayıkla, sistem kapanma nedenini rapora ekle, belirsiz günlükleri netleştir.
7. **Doğrulama:** Saf Kotlin birim testleri yaz; GitHub Actions'ta derleme + test + DEX doğrulaması çalıştır.
8. **Teslim:** Kök neden raporu, bu plan/task dosyası ve değişen dosyaların ZIP'i.

## Task listesi

### Tamamlananlar

- [x] **T01 — Rapor denetimi.** 12 rapor Drive'dan indirildi; 4.375 satır baştan sona okundu, 415 E/W kümesi sınıflandırıldı.
- [x] **T02 — Kaynak hattı.** `homeHero.decodedBackdrops` / `homeHero.resolvedBackdrops` önbellek adları yalnızca etiket hattında bulunuyor → APK etiketlerden derlenmiş. `v2.4.371` (`36a80e6b`) dala çakışmasız birleştirildi (`4244c24f`).
- [x] **T03 — FATAL kök neden.** `ReviewsTab.kt` yorum `LazyRow` anahtarı `"${rev.source}_${rev.id}"` idi (v2.4.365, `7f83063b`). TMDB (`TmdbCreditsClient`), Jikan (`KitsugiMediaSocialClient`) ve Bangumi (`KitsugiBangumiDetailClient`) yorumlarında `id = null` olduğu için 2+ yorumda aynı `tmdb_null` anahtarı oluşuyor ve Compose çöküyordu (15:07:32 Yüzüklerin Efendisi, 16:20:46 Castlevania; ikisi de TMDB). Hata v2.4.371'de de duruyordu. `KitsugiReview.stableKey()` eklendi: kimlik yoksa yazar + tarih + metin parmak izi.
- [x] **T04 — Proaktif anahtar koruması.** `uniqueLazyKeys` / `uniqueLazyKeysIndexed` (saf Kotlin) ve `uniqueItems` / `uniqueItemsIndexed` (LazyList + LazyGrid): ilk anahtar aynen kalır, tekrarına `#2`, `#3`… eklenir; API veriyi yinelese bile çökme olmaz. `ReviewsTab.kt` dışında 44 dosyadaki listeler korumaya alındı (bildirimler, indirmeler, akış eklentileri, arama, sıralama, stüdyo eserleri, CS3 ızgaraları, vitrin Pager'ı, wiki şeritleri, manga, profil, TV ekranları…).
- [x] **T05 — mpv `sub-add` donması.** `sub-add <uzak URL>` ana iş parçacığında senkron indirme yapıyordu. Raporlarda 4 olay / 6 kayıt var: uyarı 10:08:37, 15:00:36, 16:03:36, 16:08:15; HANG 15:00:39, 16:08:17. Hepsinin yığın izi `MPV.command` ← `addAndSelectExternalSubtitle`. Düzeltme `MpvSubtitlePrefetcher`: uzak altyazılar IO'da `cacheDir/mpv_subtitles` içine indiriliyor (en çok 3 paralel, 20 sn, 20 MB; 24 saat / 300 dosya önbellek sınırı) ve mpv'ye yerel dosya veriliyor. Zorunlu parça seçimi yükleme bittikten sonra başlıyor. `sub-add`'i arka plan iş parçacığına taşımak reddedildi, çünkü `destroy()` ile yarışırsa native use-after-free riski doğuruyor.
- [x] **T06 — Çift `[OYNAT] Başladı`.** Altyazı URL'leri `prepareKey` içinde olduğu için eklenti altyazıları gelince oynatıcı baştan hazırlanıyordu. Düzeltme `PlayerEngine.addExternalSubtitles`: varsayılanı `false`. mpv yeni altyazıları akışı kesmeden `auto` bayrağıyla ekliyor; kullanıcı elle parça seçmediyse parça seçim kuralları yeniden uygulanıyor. `PreparedStreamIdentity` akışın kimliğini (URL, başlık özeti, ses URL'si, motor) izliyor. Media3'te ve altyazı listesi küçüldüğünde eski tam hazırlık yolu korunuyor.
- [x] **T07 — Yanlış "SESSİZ ÇÖKME".** 6 kaydın 4'ü yanlış alarmdı: 10:06 ve 14:57 güncellemeydi; 15:07:40 ve 16:20:54 zaten kaydedilmiş çökmelerin tekrarıydı. Kalan 2'si (08:40, 16:02:59) gerçek arka plan kapatması. Düzeltmeler:
  - `PreviousExitClassifier` sınıflandırıcısı;
  - `crash_pending.flag` 4. satıra pid (ilk 3 satır geriye uyumlu), `last_crash_pid` ve `KitsugiCrashLogger.wasCrashRecordedForPid`;
  - yeni kayıt etiketleri `[UPDATE EXIT]` / `[RECORDED CRASH EXIT]`;
  - "SEBEP BULUNAMADI" yerine `ApplicationExitInfo` nedeni, ölüm anındaki önem düzeyi ve ANR izi rapora ekleniyor.
- [x] **T08 — AniList 404.** 15:07:26'daki hata artık `WARN` seviyesinde ve `describeAniListRequest` ile kök alanı + değişkenleri yazıyor.
- [x] **T09 — `PlayerLoadingDiagnostics`.** Aynı anda gelen çift "buffering started" yok sayılıyor. Etiket oturum durumundan türetiliyor. Hatayla biten tamponlama da özete giriyor; 429 oturumu artık "0 event" göstermiyor.
- [x] **T10 — SLF4J.** `No SLF4J providers were found` uyarısı için `org.slf4j:slf4j-nop:2.0.16` eklendi.
- [x] **T11 — Testler.** 5 yeni birim testi: `LazyKeyDedupTest`, `KitsugiReviewStableKeyTest`, `PreviousExitClassifierTest`, `MpvSubtitlePrefetcherTest`, `PlayerLoadingDiagnosticsBufferingTest`. İlk ikisi CI test filtresine eklendi; diğer üçü mevcut `core.diagnostics.*`, `core.player.*`, `core.player.engine.*` kalıplarıyla zaten çalışıyor.
- [x] **T12 — CI doğrulaması.** Üç iş akışı yeşil (ayrıntılar aşağıda).
- [x] **T13 — Kök neden raporu.** Rapor yazıldı. Bu commit'te özet tablosundaki iki sayım düzeltildi (mpv olayları, "SESSİZ ÇÖKME" kayıtları) ve CI sonucu eklendi.

### Açık kalanlar

- [ ] **T14 — Fiziksel cihazda doğrulama (kullanıcı).**
  - TMDB yorumu olan bir detay sayfası açılıp yorumlar yüklendiğinde çökme olmamalı (ör. Yüzüklerin Efendisi, Castlevania).
  - Harici altyazılı oynatmada `APP_SCOUT_WARNING/HANG` görülmemeli ve `[OYNAT] Başladı` tek kez yazılmalı.
- [ ] **T15 — TrAnimeIzle eklentisini güncelle (kullanıcı).** `com.kraptor.BotKontrol` ana iş parçacığını kilitliyordu (15:08:23–15:08:42, 5 × 2,5 sn). Kitsugi-Plugins deposunda v27 ile düzeltildi; uygulama kodunda değişiklik gerekmiyor.
- [ ] **T16 — AniList 404 kaynağı.** 404'ü üreten çağıranı yeni raporlardaki `AniList 404 (kayıt bulunamadı): …` satırıyla kesinleştir.
- [ ] **T17 — `GitHub_Upload.bat`.** `main`'e push, domain botunun günlük commit'i yüzünden reddediliyor olabilir. Push'tan önce `git pull --rebase origin main` veya `%ERRORLEVEL%` kontrolü önerilir.

## Bilinçli olarak değiştirilmeyenler

| Uyarı | Neden |
|---|---|
| `OnBackInvokedCallback is not enabled` | `enableOnBackInvokedCallback="true"` açılırsa TV'de `KEYCODE_BACK` işleyen `TvDialog`, `TvSharedTrailerOverlay` ve `KitsugiSplashActivity.onBackPressed` bozulur. |
| Oynatıcı `onStop` 101–256 ms | mpv `destroy()` senkron çalışıyor; uyarı MIUI'nin 100 ms eşikli bilgilendirmesi. |
| Soğuk açılışta `Skipped 34 frames` | Düzeltmek riskli bir yeniden düzenleme gerektirir; işlevsel hata değil. |
| `clearImpl failed lock verification` | ART'ın AOT derlemesinden önce verdiği performans uyarısı. |
| HTTP 429 (`s429.rapidrame.com`) | Sunucu tarafında; uygulama yedek kaynağa geçti. T06 tekrar istekleri azaltıyor. |
| MIUI/sistem/codec günlükleri | Uygulama hatası değil. |

## Commitler

| Commit | Açıklama |
|---|---|
| `4244c24f` | merge: v2.4.371 kaynak hattı (`36a80e6b`) dala alındı |
| `1da2118e` | fix(crash): Lazy liste anahtarları her koşulda benzersiz (T03, T04, testler) |
| `f812dbd5` | fix(player): mpv harici altyazıları ana iş parçacığını kilitlemiyor; altyazı gelince akış yeniden hazırlanmıyor (T05, T06, T09) |
| `3ab2cbf1` | fix(diagnostics): yanlış "sessiz çökme", ApplicationExitInfo, AniList 404, SLF4J, CI filtresi (T07, T08, T10, T11) |
| `3aec2798` | docs: kök neden raporu (T13) |
| *(bu commit)* | docs: bu plan/task dosyası + rapordaki sayım düzeltmeleri |

## Değişen dosyalar (66 + bu dosya)

Yollar depo köküne göredir. `…/` = `app/src/main/java/com/kitsugi/animelist/`, `test/…/` = `app/src/test/java/com/kitsugi/animelist/`.

### Yeni (10 + bu dosya)

- `PLAN_TASK_TMDB_NULL_ANAHTAR_COKMESI_OYNATICI_VE_TANILAMA_FIX_2026-10-25.md` (bu dosya)
- `KOK_NEDEN_RAPORU_TMDB_NULL_ANAHTAR_COKMESI_VE_RAPOR_DENETIMI_2026-10-25.md`
- `…/ui/utils/LazyKeyDedup.kt`
- `…/ui/utils/UniqueLazyItems.kt`
- `…/core/player/engine/MpvSubtitlePrefetcher.kt`
- `…/core/diagnostics/PreviousExitClassifier.kt`
- `test/…/ui/utils/LazyKeyDedupTest.kt`
- `test/…/data/remote/KitsugiReviewStableKeyTest.kt`
- `test/…/core/diagnostics/PreviousExitClassifierTest.kt`
- `test/…/core/player/engine/MpvSubtitlePrefetcherTest.kt`
- `test/…/core/player/PlayerLoadingDiagnosticsBufferingTest.kt`

### Değişen — çökme kökü, oynatıcı, tanılama, derleme (12)

- `…/ui/screens/detail/ReviewsTab.kt` — çöken yorum şeridi
- `…/data/remote/KitsugiModels.kt` — `KitsugiReview.stableKey()`
- `…/core/player/engine/PlayerEngine.kt` — `addExternalSubtitles` arayüzü
- `…/core/player/engine/MpvPlayerEngine.kt` — altyazı ön-indirme, akışı kesmeden ekleme
- `…/core/player/engine/KitsugiMpvSurfaceView.kt` — yerel altyazı ekleme / parça seçimi
- `…/ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt` — `PreparedStreamIdentity`
- `…/core/player/PlayerLoadingDiagnostics.kt`
- `…/core/diagnostics/KitsugiSessionSupervisor.kt`
- `…/core/diagnostics/KitsugiCrashLogger.kt`
- `…/data/remote/KitsugiApiBase.kt` — AniList 404 günlüğü
- `app/build.gradle.kts` — `slf4j-nop`
- `.github/workflows/android-build.yml` — test filtresi

### Değişen — Lazy anahtar koruması (44)

- `…/ui/components/KitsugiActivityDetailBottomSheet.kt`
- `…/ui/components/KitsugiAllActivitiesBottomSheet.kt`
- `…/ui/components/KitsugiAllReviewsBottomSheet.kt`
- `…/ui/components/KitsugiAllTopicsBottomSheet.kt`
- `…/ui/components/KitsugiCrossSyncDialog.kt`
- `…/ui/components/KitsugiHeroSection.kt`
- `…/ui/components/KitsugiImageGalleryDialog.kt`
- `…/ui/components/KitsugiMediaGridDialog.kt`
- `…/ui/components/KitsugiRankingBottomSheet.kt`
- `…/ui/components/KitsugiTopicDetailBottomSheet.kt`
- `…/ui/components/addons/CloudstreamExtensionTab.kt`
- `…/ui/components/addons/MangaExtensionTab.kt`
- `…/ui/screens/detail/StudioDetailPage.kt`
- `…/ui/screens/detail/wiki/FandomWikiReader.kt`
- `…/ui/screens/detail/wiki/WikiPagePreviewCard.kt`
- `…/ui/screens/fullscreen/AddonFullScreenGridPage.kt`
- `…/ui/screens/history/WatchHistoryScreen.kt`
- `…/ui/screens/manga/MangaBrowseScreen.kt`
- `…/ui/screens/manga/MangaDetailScreen.kt`
- `…/ui/screens/manga/MangaSourceHealthScreen.kt`
- `…/ui/screens/manga/components/MangaPagerReader.kt`
- `…/ui/screens/manga/components/MangaWebtoonReader.kt`
- `…/ui/screens/notifications/KitsugiNotificationsScreen.kt`
- `…/ui/screens/offline/DownloadsScreen.kt`
- `…/ui/screens/profile/BangumiProfileContent.kt`
- `…/ui/screens/profile/ProfileActivityComponents.kt`
- `…/ui/screens/search/GenresTagsSheet.kt`
- `…/ui/screens/search/OrgFilterSheet.kt`
- `…/ui/screens/search/PluginPickerScreen.kt`
- `…/ui/screens/search/SearchScreen.kt`
- `…/ui/screens/search/components/AddonExploreDialog.kt`
- `…/ui/screens/search/components/AddonExploreInline.kt`
- `…/ui/screens/search/components/AddonSearchDialog.kt`
- `…/ui/screens/search/components/PluginPickerBottomSheet.kt`
- `…/ui/screens/settings/CsPluginDiagnosticScreen.kt`
- `…/ui/screens/settings/PlatformErrorCatalogScreen.kt`
- `…/ui/screens/stream/StreamScreenContent.kt`
- `…/ui/tv/addons/TvAddonsScreen.kt`
- `…/ui/tv/detail/TvActivityDetailDialog.kt`
- `…/ui/tv/detail/TvTopicDetailDialog.kt`
- `…/ui/tv/manga/TvMangaBrowseScreen.kt`
- `…/ui/tv/manga/TvMangaDetailScreen.kt`
- `…/ui/tv/manga/TvMangaExtensionScreen.kt`
- `…/ui/tv/manga/TvMangaSourceHealthScreen.kt`

## Doğrulama durumu

- **GitHub Actions — `3ab2cbf1`** (tüm kod değişiklikleri bu commit'e kadar):
  - `🏗️ Android Derleme Doğrulama` (run `40214266114`): `:app:testFossDebugUnitTest` (seçili testler, 5 yeni test dahil) + `:app:assembleFossDebug` + `:app:assembleFossRelease` (R8) → **`BUILD SUCCESSFUL in 26m 59s`**. Testler derlemeyle aynı Gradle çağrısında çalıştığı için bu sonuç, seçili testlerin tamamının geçtiğini gösteriyor.
  - DEX doğrulaması: debug 22 dex / 413.885 metot gövdesi / `VerifyError = 0`; release 5 dex / 257.371 metot gövdesi / `VerifyError = 0`.
  - APK: `Kitsugi-Beta-v2.4.372-foss.apk` (artifact `kitsugi-foss-apks`, 1 Kasım 2026'ya kadar indirilebilir).
  - `🎌 Bangumi MASTER Doğrulama` (`40214266117`) ve `🧩 Ortak Taksonomi Doğrulama` (`40214266105`): başarılı.
- Sandbox'ta JDK/Gradle yok; derleme ve testler yalnızca CI'da çalıştırıldı.
- Fiziksel cihazda test yapılmadı (T14).

## Güvenlik ve teslim notu

- ZIP'e token, anahtar, imza dosyası veya APK eklenmedi. ZIP yalnızca değişen kaynak, test, derleme, iş akışı ve belge dosyalarının tam içeriklerini ve bu planı taşır. Yollar depo köküne göredir; içerik proje köküne kopyalanınca dosyalar yerine oturur.
- `domain_fixes.json` farkı `main`'deki günlük domain botundan geliyor. Bu işin parçası olmadığı için ZIP'e eklenmedi.
- Dalı yerel hatta almak için: `git fetch origin arena/093295a6-kitsugi-beta && git merge origin/arena/093295a6-kitsugi-beta`
- Bu belge, değişen dosyalarla birlikte ZIP olarak verilmek üzere hazırlandı.
