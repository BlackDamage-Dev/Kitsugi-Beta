# Kök Neden Raporu — `Key "tmdb_null" was already used` çökmesi ve 12 raporun tam denetimi (2026-10-25)

**Çöken sürüm:** v2.4.369-beta.29882115 (release, kaynak etiketi `v2.4.369` = `b4c3132b`)
**Düzeltmenin tabanı:** v2.4.371 (`36a80e6b`, en güncel etiket) + bugünkü domain güncellemesi
**Cihaz:** Redmi 2405CRPFDG (dizi), Xiaomi, Android 15 (API 35), arm64-v8a, heap 512 MB
**Dal:** `arena/093295a6-kitsugi-beta`

---

## 1. Özet

| # | Bulgu | Önem | Durum |
|---|-------|------|-------|
| 1 | **FATAL** `IllegalArgumentException: Key "tmdb_null" was already used` (15:07:32, 16:20:46) | Kritik | ✅ Düzeltildi |
| 2 | Ana iş parçacığı mpv `sub-add` ile 2,5–5 sn kilitleniyor (4 olay / 6 kayıt: 4 uyarı + 2 HANG) | Yüksek (ANR) | ✅ Düzeltildi |
| 3 | Altyazı gelince oynatıcının tamamen yeniden hazırlanması (her oynatmada çift `[OYNAT] Başladı`) | Orta | ✅ Düzeltildi (mpv) |
| 4 | Aynı riski taşıyan ~45 Lazy liste/ızgara/Pager anahtarı | Yüksek (gizli çökme) | ✅ Korumaya alındı |
| 5 | 6 "SESSİZ ÇÖKME" kaydının 4'ü yanlış alarm (2 güncelleme + 2 yinelenen kayıt); kalan 2'si (08:40, 16:02:59) gerçek arka plan kapatması | Orta | ✅ Düzeltildi |
| 6 | "SEBEP BULUNAMADI" — sistem kapatmalarının nedeni görünmüyor | Orta | ✅ `ApplicationExitInfo` eklendi |
| 7 | AniList HTTP 404 "permanent failure" (ERROR) — hangi sorgu olduğu belirsiz | Düşük | ✅ WARN + sorgu/kimlik günlüğü |
| 8 | `PlayerLoadingDiag` çift/yanlış "Buffering started" + 429 oturumunda "0 event" | Düşük | ✅ Düzeltildi |
| 9 | `SLF4J(W): No SLF4J providers were found` | Düşük | ✅ `slf4j-nop` |
| 10 | `com.kraptor.BotKontrol` (TrAnimeIzle eklentisi) ana iş parçacığında `CountDownLatch.await` (5 × 2,5 sn) | Yüksek | ✅ Eklenti deposunda v27 ile düzeltilmiş → **eklentiyi güncelle** |
| 11 | HTTP 429 (s429.rapidrame.com, HDFilmCehennemi CDN) | Sunucu | ℹ️ Otomatik kaynak geçişi çalıştı; #3 tekrar istekleri azaltır |
| 12 | `WindowOnBackDispatcher: OnBackInvokedCallback is not enabled` | Bilgi | ⚠️ Bilinçli olarak değiştirilmedi (bkz. §6) |
| 13 | MIUI/sistem gürültüsü (FileUtils, Codec2Client, RenderInspector, libc, ziparchive…) | Bilgi | ℹ️ Uygulama hatası değil |

---

## 2. İncelenen raporlar (tamamı, satır satır)

| Dosya | Satır | İçerik |
|-------|------:|--------|
| `crash_log (14).txt` | 186 | 15:07:32 ön plan çökmesi (pid 11948) |
| `crash_log (15).txt` | 190 | 16:20:46 ön plan çökmesi (pid 30470) |
| `crash_history (11).txt` | 1078 | 08:40 → 15:07:40 geçmişi |
| `crash_history (12).txt` | 1817 | 08:40 → 16:20:54 geçmişi (11'in üst kümesi) |
| `logcat_at_crash (10/11).txt` | 222 / 251 | Çökme anı logcat |
| `live_logcat_report (12/13).txt` | 224 / 254 | Anlık logcat (+ `:crash` süreci açılışı) |
| `kitsugi_debug (11/12).log` | 40 / 83 | Oynatıcı günlüğü (`[OYNAT]`, `PlayerLoadingDiag`) |
| `device_info (13/14).txt` | 9 / 9 | Cihaz bilgisi |

Toplam **4 375 satır**; benzersiz E/W satırları kümelenip tek tek sınıflandırıldı (415 küme).

---

## 3. Zaman çizelgesi

| Zaman | Süreç / sürüm | Olay |
|-------|---------------|------|
| 10-24 23:19–23:32 | 25290 / 2.4.365 | Galeri (360 görsel) → arka plan; sistem kapattı → 08:40'ta `BACKGROUND EXIT` (zararsız) |
| 10-25 08:40–10:06 | 8179 / 2.4.365 | 10:06'da **2.4.367'ye güncelleme** → `BACKGROUND EXIT` (yanlış alarm) |
| 10:08:37 | 11641 / 2.4.367 | `APP_SCOUT_WARNING` 2,5 sn — **mpv `sub-add`** (Eyewitness, HDFC) |
| 14:56:36 → 14:57:01 | 11641 → 11948 | Ayarlar › Hakkında → **2.4.369'a güncelleme** → `UNCLEAN EXIT` "gerçek çökme" (yanlış alarm) |
| 15:00:36 → 15:00:39 | 11948 / 2.4.369 | `APP_SCOUT_WARNING` → **`APP_SCOUT_HANG` 5 sn** — mpv `sub-add` (Capernaum, HDFC; ikinci prepare'den hemen sonra) |
| 15:02:37 / 15:05:28 | 11948 | LOTR — HDFC + Torrentio, her biri **çift `[OYNAT] Başladı`** |
| 15:07:26 | 11948 | `KitsugiApiBase: AniList query permanent failure with code: 404` |
| **15:07:32** | 11948 | **FATAL `Key "tmdb_null"`** — LOTR (TMDB film) detayına oynatıcıdan dönüş |
| 15:07:40 | 11948 | Aynı çökme ikinci kez `UNCLEAN EXIT` olarak yazıldı (yineleme) |
| 15:08:23–15:08:42 | 21114 | 5 × `APP_SCOUT_WARNING` 2,5 sn — `com.kraptor.BotKontrolKt.getIconHashes` → `CountDownLatch.await` (eklenti) |
| 16:02:59 | 21114 → 30470 | `BACKGROUND EXIT` (arka planda sistem kapattı) |
| 16:03:29 / 16:03:30 | 30470 | Capernaum (HDFC) çift prepare; 16:03:36 `APP_SCOUT_WARNING` — mpv `sub-add` |
| 16:05:19 → 16:05:28 | 30470 | Media3 oturumu → **HTTP 429** (`s429.rapidrame.com`, aynı belirteçli adrese 3. istek) → yedek kaynağa geçti, oynadı |
| 16:08:15 → 16:08:17 | 30470 | `APP_SCOUT_WARNING` → **`APP_SCOUT_HANG`** — mpv `sub-add` (Castlevania) |
| **16:20:46** | 30470 | **FATAL `Key "tmdb_null"`** — Castlevania (TMDB dizi) detayı açıldıktan 2 sn sonra |
| 16:20:54 | 30470 | Aynı çökme ikinci kez `UNCLEAN EXIT` (yineleme) |

---

## 4. Kök neden #1 — `Key "tmdb_null" was already used`

### Kanıt zinciri
1. **Yığın izi:** `LazyListMeasure` (LazyRow/LazyColumn) → `FillNode` → `ColumnMeasurePolicy` → `PaddingNode` → `SimpleGraphicsLayerModifier` → `FillNode`.
   Yani `Column(Modifier.fillMaxWidth().clip(…).background(…).border(…).padding(vertical=…)) { LazyRow(Modifier.fillMaxWidth()) }`.
2. Bu zincir `ReviewsTab.kt` yorumlar bölümüyle **birebir** aynı: `.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(…).border(…).padding(vertical = 12.dp)`.
3. O LazyRow'un anahtarı: `key = { rev -> "${rev.source}_${rev.id}" }` (v2.4.365, `7f83063b` "performans optimizasyonları" ile eklendi).
4. `KitsugiReview.id: Int? = null`. Kimliği **null** üreten kaynaklar:
   - `TmdbCreditsClient` → `id = null, source = "tmdb"` (TMDB yorum kimliği metin/hex)
   - `KitsugiMediaSocialClient` (Jikan/MAL) → `id = null, source = "jikan"`
   - `KitsugiBangumiDetailClient` → `id = null, source = "bangumi"`
5. 2+ yorumu olan her TMDB/MAL/Bangumi sayfasında tüm kartlar `"tmdb_null"` / `"jikan_null"` / `"bangumi_null"` anahtarını alıyor; LazyRow ikinci kartı ölçerken Compose çöküyor.
6. Her iki çökme de **TMDB** içeriğinde (LOTR film, Castlevania dizi) ve yorumlar yüklendiği an gerçekleşti.

> Not: GitHub `main` v2.4.340'ta kaldığı için bu kod orada yoktu; hata v2.4.365–v2.4.371 hattında (etiketlerde) mevcuttu ve v2.4.371'de **hâlâ duruyordu**.

### Düzeltme
- `KitsugiReview.stableKey()` — kimlik yoksa yazar + tarih + metin parmak izi (`rev:tmdb:h…`).
- `uniqueLazyKeys` (saf Kotlin) + `uniqueItems` / `uniqueItemsIndexed` (LazyList **ve** LazyGrid): temel anahtar ilk görüldüğünde **aynen** kalır (kaydırma/animasyon kararlı), tekrarında `#2`, `#3`… eklenir → API veriyi yinelese bile **çökme imkânsız**.
- Açık yorum detayının geri bulunması ve "Tüm incelemeler" çeviri önbelleği aynı kimliğe geçti (eskiden aynı kullanıcının iki yorumu çeviriyi paylaşıyordu).

### Aynı riski taşıyan diğer listeler (proaktif)
Konu/etkinlik/yanıt/yorum sayfaları (telefon + TV) · bildirimler (grup ön eki + grup içi tekilleştirme) · indirmeler · akış eklenti listesi (aynı adlı iki "Torrentio") · arama sonuçları · sıralama · **stüdyo eserleri** (anahtarda tür yoktu: TMDB film #122 ile dizi #122 çakışıyordu) · CS3 ızgara/keşif/arama (eklentiler aynı URL'yi tekrar döndürür; `indexOf` tabanlı O(n²) anahtar da kaldırıldı) · vitrin `HorizontalPager` · wiki şeritleri (tek noktadan: `WikiPreviewStripRow`) · manga bölüm/sayfa/kaynak listeleri ve okuyucu Pager'ı (bazı kaynaklar her sayfaya aynı `index` veriyor) · eklenti/depo listeleri · profil etkinlikleri · hata kataloğu (iki katalog aynı LazyColumn'da ön eksiz anahtar kullanıyordu) · tür/etiket satırları.

Dokunulmayanlar yalnızca zaten benzersiz olanlar: indeks önekli anahtarlar, Room birincil anahtarları, statik/enum seçenekler, `distinct()` sonrası listeler.

---

## 5. Diğer bulgular ve düzeltmeler

### 5.1 mpv `sub-add` ana iş parçacığını kilitliyor (ANR riski)
- **İz:** `MPV.command` ← `KitsugiMpvSurfaceView.addAndSelectExternalSubtitle` ← `MpvPlayerEngine.event` (`MPV_EVENT_FILE_LOADED`).
- **Neden:** `sub-add <uzak URL>` senkron; altyazılar ana iş parçacığında ağdan sırayla iniyordu.
- **Düzeltme:** `MpvSubtitlePrefetcher` — uzak altyazılar IO'da (≤3 paralel, 20 sn, 20 MB) önbelleğe indirilir, mpv'ye **yerel dosya** verilir. Komut arka plana taşınmadı: aynı mpv tutamacı arka planda kullanılırken `destroy()` native use-after-free riski doğururdu. Zorunlu parça seçimi zamanlayıcısı yükleme bittikten sonra başlar.

### 5.2 Altyazı gelince akışın yeniden hazırlanması (çift `[OYNAT] Başladı`)
- **Neden:** `LaunchedEffect(playerEngine, prepareKey)` anahtarı altyazı URL'lerini içeriyor; eklenti altyazıları 1–9 sn sonra gelince **tüm oynatıcı** yeniden hazırlanıyordu → aynı CDN adresine ikinci istek (429 riski), yeniden tamponlama, ikinci `sub-add` fırtınası (15:00:39 HANG bunun hemen ardından).
- **Düzeltme:** `PlayerEngine.addExternalSubtitles` — mpv yeni altyazıları akışı kesmeden (`auto` bayrağı) ekler; kullanıcı elle parça seçmediyse Türkçe öncelikli politika yeniden uygulanır. Media3 ve liste daralması durumunda eski tam hazırlık yolu korunur.

### 5.3 Yanlış "SESSİZ ÇÖKME" alarmları ve "SEBEP BULUNAMADI"
- 10:06 ve 14:57 → **güncelleme** (logcat'teki `ClassLoaderContext classpath element mismatch` / `Unable to resolve path …/base.apk` kanıt). Sürüm adı + paket güncelleme zamanı karşılaştırılır → `[UPDATE EXIT]`, uyarı yok.
- 15:07:40 ve 16:20:54 → aynı çökmenin **yinelemesi**. Çökme yakalayıcısı artık pid'i kaydeder (`crash_pending.flag` 4. satır — ilk 3 satır geriye uyumlu — ve `last_crash_pid`) → `[RECORDED CRASH EXIT]`.
- "SEBEP BULUNAMADI": uygulamalar sistem günlüklerini okuyamaz. Android 11+ **`ApplicationExitInfo`** ile kesin neden (ANR / LMK / kullanıcı kapattı / native / aşırı kaynak…), sürecin ölüm anındaki **önemi** (gerçekten ön planda mıydı — kalp atışı 15 sn gecikmeli yazıldığı için bayrak yanılabiliyordu) ve ANR'de sistemin **ANR izi** rapora girer. Zararsız nedenlerde erken kaldırılan uyarı bayrağı geri alınır.
- Saf Kotlin `PreviousExitClassifier` + `PreviousExitClassifierTest` (raporlardaki her senaryo).

### 5.4 AniList HTTP 404
AniList GraphQL kimlik bulunamayınca 404 döner; bu "kayıt yok" yanıtıdır. Artık `WARN` seviyesinde ve **kök alan + değişkenlerle** yazılıyor (ör. `AniList 404 (kayıt bulunamadı): Media(...) vars={"id":122}`). Sosyal sekme TMDB film/dizileri için AniList'e zaten gitmiyor (`supportsAnimeSocial`); 404'ü üreten çağıran bir sonraki raporda bu satırla kesinleşecek.

### 5.5 `PlayerLoadingDiag`
Media3 `prepare()` sırasında aynı ms'de "rebuffer" + "initial-load" yazılıyordu; 429 ile biten oturum "0 event(s), total 0 ms" gösteriyordu. Çift başlangıç yok sayılır, etiket oturum durumundan türetilir, hata anında süren tamponlama özete eklenir. `PlayerLoadingDiagnosticsBufferingTest`.

### 5.6 SLF4J
Ktor 3 (Supabase) `slf4j-api` getiriyor, sağlayıcı yoktu. `org.slf4j:slf4j-nop:2.0.16` eklendi (davranış aynı; `META-INF/versions/**` paketlemede zaten hariç).

### 5.7 TrAnimeIzle eklentisi — `BotKontrol` (uygulama dışı)
`com.kraptor.BotKontrolKt.getIconHashes` ana iş parçacığında `CountDownLatch.await` (5 × 2,5 sn). **Kitsugi-Plugins deposunda bugün 15:44–15:49 UTC (v27) düzeltilmiş** ve `TrAnimeIzle.cs3` 15:56 UTC'de yayımlanmış; rapordaki iz 12:08 UTC'de, düzeltmeden önce. → **Uygulamada Eklentiler › TrAnimeIzle › Güncelle.**

---

## 6. Bilinçli olarak değiştirilmeyenler

| Uyarı | Neden değiştirilmedi |
|-------|----------------------|
| `WindowOnBackDispatcher: OnBackInvokedCallback is not enabled…` | `android:enableOnBackInvokedCallback="true"` açılırsa Android 13+'ta `KEYCODE_BACK` görünüm ağacına iletilmez; `TvDialog`, `TvSharedTrailerOverlay` geri tuşunu bu olayla yakalıyor, `KitsugiSplashActivity` `onBackPressed` override ediyor → TV'de geri tuşu bozulur. Uyarı yalnızca bilgilendirme. |
| `PerfMonitor: Slow Operation: …FullscreenPlayerActivity onStop took 101–256ms` | `onStop` motoru kapatıyor; mpv `destroy()` doğası gereği senkron. MIUI'nin 100 ms eşikli uyarısı; hata değil. |
| `Choreographer: Skipped 34 frames` (soğuk açılış) | Cloudstream çalışma zamanı + 313 ayıklayıcı kaydı; domain ön-yüklemesi zaten `apply()` (asenkron). Riskli yeniden düzenleme gerektirir. |
| `SnapshotStateListKt.clearImpl failed lock verification` | ART'ın AOT derlemesi öncesi R8 çıktısı için verdiği performans uyarısı (`:crash` sürecinde, kurulum sonrası). |
| `FileUtils: err write to mi_exception_log`, `Codec2Client`, `RenderInspector`, `libc: Access denied finding property`, `ziparchive: QXPerformance.dm`, `FramePredict`, `MiuiMultiWindowUtils`, `IVideoService`, `LB: fail to open node`, `HWUI: Unknown dataspace`, `VideoCodecInfo`, `ColorUtils`, `jdwp agent`, `avc: granted` | MIUI / sistem / codec günlükleri; uygulama hatası değil. |

---

## 7. Doğrulama
- Yerel derleme ortamı yok; doğrulama **GitHub Actions `🏗️ Android Derleme Doğrulama`** ile yapılır: `:app:assembleFossDebug :app:assembleFossRelease` (R8) + DEX bayt kodu doğrulaması + seçili birim testleri.
- Yeni testler: `LazyKeyDedupTest`, `KitsugiReviewStableKeyTest`, `PreviousExitClassifierTest`, `MpvSubtitlePrefetcherTest`, `PlayerLoadingDiagnosticsBufferingTest`. İlk ikisi CI test filtresine eklendi; diğer üçü mevcut `core.diagnostics.*`, `core.player.*` ve `core.player.engine.*` kalıplarıyla zaten çalışıyor.
- **Sonuç (`3ab2cbf1`, run `40214266114`):** `BUILD SUCCESSFUL in 26m 59s` — testler ve iki APK aynı Gradle çağrısında; seçili testlerin tamamı geçti. DEX: debug 22 dex / 413.885 metot gövdesi / `VerifyError = 0`; release (R8) 5 dex / 257.371 metot gövdesi / `VerifyError = 0`. `🎌 Bangumi MASTER` (`40214266117`) ve `🧩 Ortak Taksonomi` (`40214266105`) de başarılı. Fiziksel cihaz testi yapılmadı.

---

## 8. Önemli: GitHub `main` v2.4.340'ta kalmış
`main` = 2026-10-24 domain-bot commit'i (`3d3242ff`, v2.4.340) + bugünkü bot commit'i. Gerçek geliştirme hattın (v2.4.341 → v2.4.371) yalnızca **etiketlerde** ve bazı arena dallarında. `GitHub_Upload.bat` içindeki `git push -u origin main` büyük olasılıkla domain botunun günlük commit'i yüzünden **non-fast-forward reddi** alıyor ve betik hatayı yutup derlemeye/sürüme devam ediyor.
**Öneri:** betikte push'tan önce `git pull --rebase origin main` (veya push sonucunu `%ERRORLEVEL%` ile kontrol edip durma).

Bu dal v2.4.371'in üstüne kurulduğu için yerel çalışma hattına doğrudan birleştirilebilir:
```
git fetch origin arena/093295a6-kitsugi-beta
git merge origin/arena/093295a6-kitsugi-beta
```
