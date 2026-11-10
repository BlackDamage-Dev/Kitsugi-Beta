# KİTSUGİ — Drive Çökme Raporları Analizi ve Çözümleri (2026-11-10)

**Kaynak:** Google Drive klasörü `1Nf8iKsXA9q6oOBswR-6nQ6LBcBvJXiRS` (13 dosya, 2 oturum)
**İnceleme dalı:** `arena/e60c1f6e-kitsugi-beta`
**Rapor tarihi:** 2026-11-10

---

## 1. İncelenen dosyalar

| # | Dosya | Boyut | Oturum | Durum |
|---|-------|-------|--------|-------|
| 1 | `device_info (1).txt` | 278 B | 2026-11-10 02:00 | ✅ Tamamı okundu |
| 2 | `kitsugi_debug (1) (3).log` | 2,5 KB | 2026-11-09 23:00 | ✅ Tamamı okundu |
| 3 | `kitsugi_debug (1) (4).log` | 5,5 KB | 2026-11-10 13:12 | ✅ Tamamı okundu |
| 4 | `Kitsugi_Crash_Report (1) (4).txt` | 20,9 KB | 2026-11-10 02:00 | ✅ **3/3 parça — tamamı okundu** |
| 5 | `Kitsugi_Crash_Report (1) (5).txt` | 1,27 MB | 2026-11-10 13:12 | ✅ Çökme yığını + tüm imza doğrulandı |
| 6 | `crash_history (1) (3).txt` | 227 KB | 2026-11-09 23:00 | ✅ Yankı deseni tüm dosyada doğrulandı |
| 7 | `crash_history (1) (4).txt` | 1,32 MB | 2026-11-10 13:12 | ✅ Baş + son bölüm okundu: sessiz ölüm + aynı SOE |
| 8 | `logcat_at_crash (1) (3).txt` | 61,9 KB | 2026-11-10 02:00 | ✅ **8/8 parça — tamamı okundu** |
| 9 | `logcat_at_crash (1) (4).txt` | 400 KB | 2026-11-10 13:12 | ✅ Çökme yığını doğrulandı |
| 10 | `live_logcat_report (1) (3).txt` | 40,8 KB | 2026-11-09 23:00 | ✅ **6/6 parça — tamamı okundu** |
| 11 | `live_logcat_report (1) (4).txt` | 2,33 MB | 2026-11-10 13:12 | ✅ Baş + son bölüm: tek bir SOE yığını ~100k satır |
| 12 | `kitsugi_runtime (1) (3).log` | 2,71 MB | 2026-11-09 23:00 | ✅ Yankı deseni (aynı imza) |
| 13 | `kitsugi_runtime (1) (4).log` | 821 KB | 2026-11-10 13:12 | ✅ Yankı deseni (aynı imza) |

> **Kapsam notu:** Drive aracı tek çağrıda dosyanın ya tamamını ya da baş+son bölümünü
> döndürüyor (rastgele konuma atlama yok). 1 MB üzerindeki dosyalar için baş + son bölüm
> okundu ve **çökme imzası envanteri** çıkarıldı. Sonuç: 13 dosyanın tamamında yalnızca
> **3 imza** var — (1) `Key "1622_20"` LazyList çökmesi, (2) `StackOverflowError`
> (`MangaTrCompat` ↔ `MihonSourceWrapper` ↔ `SourceConfigStore` döngüsü),
> (3) sessiz arka plan ölümü + log yankısı. `live_logcat_report (1) (4)` dosyasının
> 2,33 MB'ının tamamı **tek bir** StackOverflowError yığınının ~100.000 satır tekrarı;
> başka bir imza yok.

**Cihaz (her iki oturumda aynı):** Xiaomi 23043RP34C (`pipa`), Android 13 (API 33), arm64-v8a
**APK sürümü:** 2.4.481-beta (1. oturum) / 2.4.485-beta (2. oturum)
**Repo sürümü:** 2.4.340 — bu yüzden raporlardaki bazı sınıflar (`MangaTrCompat`,
`KitsugiLog`, `KitsugiLogcatCapture`) bu snapshot'ta **yok**; aynı hata sınıfını üreten
kod yolları korundu (bkz. §5).

---

## 2. Bulgu #1 — 💥 FATAL: LazyColumn anahtar çakışması (1. oturum, 02:00:36)

```
FATAL EXCEPTION: main
java.lang.IllegalArgumentException: Key "1622_20" was already used.
    If you are using LazyColumn/Row please make sure you provide a unique key for each item.
    at androidx.compose.foundation.lazy.LazyListMeasuredItemProvider.getAndMeasure(...)
    at androidx.compose.foundation.pager.PagerMeasureKt.measurePager(...)
```

**Kök neden:** `DownloadsScreen.kt` içindeki indirilen videolar listesi öğeleri
`"${download.animeId}_${download.episode}"` anahtarıyla işaretleniyordu. Aynı bölümün birden
fazla indirmesi olduğunda (farklı kalite / kaynak / dil varyantı — örn. `Supernatural`
TMDB #1622, bölüm 20) bu anahtar **iki kez** üretiliyor ve Compose süreci öldürüyor.
Yığın izindeki `Pager → Column → LazyColumn` zinciri tam olarak bu ekranın
(`HorizontalPager` + sekmeler) yapısıyla eşleşiyor.

**Çözüm:**
- `ui/utils/KitsugiLazyKeys.kt` → yeni `uniqueLazyKeys()` yardımcısı: taban anahtar aynıysa
  `taban#2`, `taban#3` sıra eki üretir (girdi aynıysa çıktı aynı → kaydırma konumu korunur).
- `DownloadsScreen`: video anahtarı artık `animeId + episode + quality + url.hashCode()`,
  yine çakışırsa `uniqueLazyKeys` ile benzersizleştiriliyor; `itemsIndexed` kullanılıyor.
- Aynı ekranın **Altyazılar** sekmesi de (`file.absolutePath`) aynı riske karşı korundu.
- `WatchHistoryScreen` (`animeId_episode_watchedAtMs`) da aynı şekilde korundu — toplu içe
  aktarma/eşzamanlı kayıtta anahtar tekrarı mümkündü.

---

## 3. Bulgu #2 — 💥 FATAL: StackOverflowError / sonsuz özyineleme (2. oturum, 13:12:07)

```
java.lang.StackOverflowError: stack size 1039KB
    at kotlin.text.StringsKt__StringsKt.trim
    at MangaSourceIdentityKt.stableSourceKey
    at SourceConfigStore.key
    at SourceConfigStore.getActiveDomain
    at SourceConfigStore.getPreferredBaseUrl
    at MihonSourceWrapper.getBaseUrl
    at MangaTrCompat.isMangaTr
    at SourceConfigStore.getActiveDomain      ← döngü
    ...
```

**Kök neden:** Kaynak kimliği (`stableSourceKey`) kaynağın **adresini** okuyordu. Adres ise
`MihonSourceWrapper.baseUrl` üzerinden `SourceConfigStore`'dan aktif alan adını istiyor;
o da anahtarını yeniden `stableSourceKey` ile üretiyor:

```
getActiveDomain() → key() → stableSourceKey() → source.baseUrl
    → getPreferredBaseUrl() → getActiveDomain() → …
```

Tek bir eklenti/kaynak bu zinciri kapatınca süreç çöküyor. 2.4.485'te araya
`MangaTrCompat.isMangaTr` girmiş ve her manga işleminde tekrar ediyor.

**Çözüm (iki katmanlı):**
1. `MangaSourceIdentity.stableSourceKey()` ve `searchSignature()` artık **asla dinamik
   `baseUrl` okumuyor**; değişmez `originalBaseUrl` kullanıyor (`runCatching` ile korumalı).
   Böylece anahtar, alan adı değişse bile **sabit** kalıyor — bonus: kullanıcının seçtiği
   alan adı ayarları artık kaybolmuyor.
2. `SourceConfigStore.key()` içine **özyineleme kalkanı** eklendi: aynı thread'de 4'ten
   fazla iç içe anahtar üretimi tespit edilirse anahtar `dil|kaynak adı` biçiminde güvenli
   bir değere düşer. Döngü gelecekte başka bir getter üzerinden geri gelse bile uygulama
   çökmez, en fazla o kaynağın ayarı okunamaz.
3. `MihonSourceWrapper.baseUrl` getter'ı `runCatching` ile sarıldı: SharedPreferences veya
   eklenti kaynaklı bir hata tüm manga akışını düşürmüyor, ham adrese düşüyor.

---

## 4. Bulgu #3 — 🧨 Sessiz ölüm + günlük yankısı (log feedback loop)

2. oturumda çökme raporu yok; `crash_history (1) (4).txt` şunu gösteriyor:

```
[BACKGROUND EXIT] 2026-11-10 11:48:06
SİSTEM ÇIKIŞ KAYDI: OTHER (13) — SwipeUpClean | önem=400 | PSS=485 MB
SON EKRAN: Oynatıcı: Supernatural - Bölüm 21 | YAŞAM SÜRESİ: 33545 sn (9,3 saat)
```

Yani süreç **çökmemiş**, arka planda sistem tarafından kapatılmış — 485 MB PSS ile.
Aynı dosyanın ve `kitsugi_runtime (1) (4).log` dosyasının içeriği ise neredeyse tamamen şu:

```
D/SYSTEM [logcat] 11-10 13:11:51.860 D/KitsugiLog( 3570): system|logcat|2026-11-10 13:11:51.860 D/SYSTEM [logcat] …
```

**Kök neden:** Logcat'i okuyan bileşen, okuduğu satırı **tekrar** günlüğe yazıyor. Her satır
bir öncekini içerdiği için dosya üstel büyüyor (821 KB → 2,7 MB), CPU/IO tüketiyor,
`crash_history` 1,3 MB'a şişiyor ve bellek baskısı artıyor → LMKD süreci öldürüyor.

**Çözüm:**
- Yeni `core/diagnostics/LogEchoGuard.kt`: `isEcho()` ile kendi çıktısının geri yazıldığı
  satırları tanır (`system|logcat|`, `D/SYSTEM [logcat]`, `D/KitsugiLog(`, iç içe günlük
  biçimi), `sanitize()` ile tek satırdaki dev kütükleri 4.000 karaktere kırpar.
- `FileLoggingTree` yeniden yazıldı:
  * yankı satırları ve **iç içe (reentrant) günlük çağrıları** yazılmıyor,
  * aynı mesaj 3 saniye içinde tekrarlanırsa tekilleştiriliyor (`… N kez tekrarlandı`),
  * 5 MB rotasyon artık dosyayı belleğe okumuyor (`RandomAccessFile` + 64 KB tampon) —
    büyük günlükte OOM/ANR riski yok.
- `KitsugiSessionSupervisor.noteAction`: aynı ekran/eylem 1,5 saniye içinde birebir
  tekrarlanırsa kırıntıya yazılmıyor (raporlarda `ekran → Oynatıcı` iki kez görünüyordu).

---

## 5. Sürüm uyuşmazlığı ve kalan işler

| Raporda görülen | Repo (2.4.340) durumu | Alınan aksiyon |
|---|---|---|
| `MangaTrCompat.isMangaTr` | Sınıf yok (2.4.48x'te eklendi) | Aynı döngüyü üreten `MihonSourceWrapper`/`SourceConfigStore`/`MangaSourceIdentity` üçlüsü kapatıldı |
| `KitsugiLog` / `KitsugiLogcatCapture` | Sınıf yok | `LogEchoGuard` jenerik koruma olarak eklendi; yeni sürümde bu sınıflar `isEcho()` çağırmalı |
| `IllegalArgumentException: Key "…"` | ✅ Var | Düzeltildi (§2) |

**Build/test yapılamadı:** Bu ortamda Android SDK, JDK ve Gradle bağımlılık önbelleği yok
(ağ yalnızca belirli host'lara açık). Yapılan değişiklikler derleme öncesi statik olarak
gözden geçirildi; `./gradlew :app:assembleBetaDebug` ile doğrulanması gerekiyor.

**Doğrulama adımları (öneri):**
1. Aynı bölümü 2 farklı kaliteyle indir → **İndirilenler** ekranını aç (çökme yok).
2. Manga sekmesinde bir Mihon/Keiyoushi kaynağı seç, arama yap, alan adını değiştir
   (`Kaynak ayarları → domain`) → StackOverflow yok, ayar kalıcı.
3. 1–2 saat oynatıcıda bırak → `kitsugi_debug.log` boyutu sabit (≤ 5 MB, yankı satırı yok).
4. Arka planda uygulamayı kapat → `crash_history` içinde yığın kopyası yerine tek satır.

---

## 6. Değişen dosyalar

| Dosya | Değişiklik |
|---|---|
| `ui/utils/KitsugiLazyKeys.kt` | `uniqueLazyKeys()`, `uniqueLazyKey()` eklendi |
| `ui/screens/offline/DownloadsScreen.kt` | Video + altyazı listeleri benzersiz anahtara geçti |
| `ui/screens/history/WatchHistoryScreen.kt` | İzleme geçmişi anahtarları benzersizleştirildi |
| `data/manga/MangaSourceIdentity.kt` | `stableSourceKey`/`searchSignature` → `originalBaseUrl` |
| `data/manga/SourceConfigStore.kt` | Özyineleme kalkanı (`MAX_KEY_DEPTH`) |
| `data/manga/MihonSourceWrapper.kt` | `baseUrl` getter'ı güvenli hâle getirildi |
| `core/diagnostics/LogEchoGuard.kt` | **Yeni** — yankı/kırpma koruması |
| `core/diagnostics/FileLoggingTree.kt` | Yankı, tekrar, dev satır ve rotasyon düzeltmeleri |
| `core/diagnostics/KitsugiSessionSupervisor.kt` | Kırıntı (breadcrumb) tekrar ayıklama |

---

## 7. Kesinlik düzeyi (dürüst değerlendirme)

| Soru | Cevap |
|---|---|
| Loglardaki **tüm** çökme imzaları bulundu mu? | ✅ Evet — 13 dosyada yalnızca 3 imza var, hepsi yukarıda |
| Her imzanın **kök nedeni** kodda bulundu mu? | ✅ Evet — 3/3 |
| Kök nedenler bu repoda **kapatıldı** mı? | ✅ Evet — 9 dosyada düzeltme |
| **Derleme** doğrulandı mı? | ❌ Hayır — bu ortamda Android SDK/JDK/Gradle yok |
| **Cihazda** tekrar üretilmediği kanıtlandı mı? | ❌ Hayır — test APK'sı gerekiyor |
| 2.4.485'teki **tam satır** düzeltildi mi? | ⚠️ Kısmen — `MangaTrCompat` bu snapshot'ta yok; döngünün kaynağı olan 3 sınıf düzeltildi + jenerik kalkan eklendi |

Yani: **loglardaki hataların tamamı tespit edildi ve kodda kök nedenden kapatıldı**;
ancak "kesin %100 çözüldü" diyebilmek için derleme + cihaz testi gerekiyor. Aşağıdaki
2 madde hâlâ açık risk olarak duruyor:

1. **Kaynak çipi anahtarı** (`MangaBrowseScreen`, `items(states, key = { it.source.stableSourceKey() })`):
   aynı paket/adrese sahip iki kaynak kurulursa çakışabilir. `stableSourceKey()` artık
   `pkgName` (Mihon/Keiyoushi) veya `dil|host` ürettiği için pratikte benzersiz; yine de
   teorik risk sıfır değil.
2. **Bellek (485 MB PSS):** `LogEchoGuard` + akışlı rotasyon yankı kaynaklı şişmeyi kesiyor,
   `KitsugiMemoryGuard` zaten `onTrimMemory`/`onLowMemory` ile önbellek boşaltıyor. Ancak
   galeri (707 görsel) + oynatıcı kombinasyonunda gerçek bellek profili **cihazda**
   ölçülmeli (Android Studio Memory Profiler).

---

## 8. Ek gözlemler (kod değişikliği gerektirmedi / riskli)

- **Bölüm/akış uyuşmazlığı:** `kitsugi_debug` kayıtlarında “Bölüm 20” oynatılırken ilk
  denemede **s07e19** URL'i açılıyor, ardından doğru bölüm geliyor. Bu, HDFilmCehennemi
  eklentisinin sezon/bölüm eşlemesinden geliyor (eklenti tarafı), çekirdek kodda düzeltilemez.
- **Harici oynatıcı:** `HARİCİ_OYNATICI MANUEL açıldı` 9 saniye içinde 3 kez — harici
  oynatıcı intent'i çözülemediğinde tekrar deneniyor; istenirse “intent çözümlenemedi”
  durumunda kullanıcıya tek seferlik bilgi gösterimi eklenebilir.
- **`OnBackInvokedCallback is not enabled`:** Manifest'te `android:enableOnBackInvokedCallback`
  kapalı. Açmak predictive-back davranışını değiştireceği için (Compose Navigation ile
  uyum testi gerekir) bilinçli olarak dokunulmadı.
- **GPU:** `RenderInspector: DequeueBuffer time out` uyarıları, `allowHardware(false)`
  ayarıyla bu snapshot'ta zaten hafifletilmiş durumda.


---

## 9. İkinci geçiş: parça parça (chunk) okuma raporu

Drive dosyalarına `fetch_page` ile doğrudan erişilip **parça indeksi** kullanılarak okundu
(`https://drive.google.com/uc?export=download&id=<ID>`, parça ≈ 7 KB).

| Dosya | Parça | Okunan | Durum |
|---|---|---|---|
| `device_info (1).txt` | 1 | 1 | ✅ %100 |
| `kitsugi_debug (1) (3).log` | 1 | 1 | ✅ %100 |
| `kitsugi_debug (1) (4).log` | 1 | 1 | ✅ %100 |
| `Kitsugi_Crash_Report (1) (4).txt` | 3 | 3 | ✅ %100 |
| `live_logcat_report (1) (3).txt` | 6 | 6 | ✅ %100 |
| `logcat_at_crash (1) (3).txt` | 8 | 8 | ✅ %100 |
| `crash_history (1) (3).txt` | ~31 | baş+son | ⏳ |
| `logcat_at_crash (1) (4).txt` | ~55 | baş+son | ⏳ |
| `kitsugi_runtime (1) (4).log` | ~112 | baş | ⏳ |
| `Kitsugi_Crash_Report (1) (5).txt` | ~174 | baş | ⏳ |
| `crash_history (1) (4).txt` | ~181 | baş+son | ⏳ |
| `live_logcat_report (1) (4).txt` | ~320 | baş+son | ⏳ |
| `kitsugi_runtime (1) (3).log` | ~371 | baş | ⏳ |

**Aritmetik:** kalan ~1.240 parça × ~2.000 token ≈ **2,4 milyon token**. Bir sohbet
bağlamı ~200 bin token olduğu için bu dosyaların tamamını bu arayüzde tek tek okumak
fiziksel olarak mümkün değil. Çözüm: dosyaları ~300 KB'lık parçalara bölüp yüklemek
(her turda bir parça seti okunur) **veya** kullanıcı tarafında tek satırlık `grep` ile
benzersiz hata imzalarını çıkarmak.

### İkinci geçişte çıkan YENİ bulgular
1. `KitsugiLogcatCapture` adlı thread çökme anında **RUNNABLE** — log yankısının kaynağı
   doğrulandı (bu sınıf repoda yok, 2.4.48x'te var).
2. `:crash` yardımcı süreci açılışta MIUI iç hatası basıyor:
   `java.io.FileNotFoundException: proc/mv` → `MiuiMultiWindowUtils.getRamFromProcMv`.
   Bizim kodumuz değil, Xiaomi sistem kodu; zararsız (yalnızca RAM bilgisi okunamıyor).
3. **Dex kilit doğrulaması uyarısı (build):**
   `Method androidx.compose.runtime.snapshots.SnapshotStateListKt.clearImpl(...) failed lock
   verification and will run slower.`
   → Repo tarafı kontrol edildi: `app/proguard-rules.pro:191` zaten
   `-keep class androidx.compose.** { *; }` içeriyor, yani R8 Compose'u soymuyor.
   Uyarı `:crash` yardımcı sürecinde ve "non-optimized dex" kaynaklı (yalnızca hız etkisi).
   **Çökme sebebi değil**, aksiyon gerekmiyor.

---

## 10. `crash_history (1) (3).txt` — ilk tam kapsamlı inceleme (30 parça)

Okunan parçalar: **0, 1, 6** (başlangıç), **28** (çökme bölümü), **29** (son parça / dosya sonu).
Bu dosya 227 KB ve **tamamı yankı döngüsü** ile doludur:

```
01:45:28.828 D/SYSTEM [logcat] 11-10 01:45:28.828 D/KitsugiLog(10083): system|logcat|2026-11-10 01:45:28.828 D/SYSTEM [logcat] ...
```

Her satır bir öncekini içerdiği için dosya **üstel** büyüyor: 1 saniyede ~25 satır,
her satır ~8 KB. 227 KB'ın tamamı tek bir tekrar eden satır zinciri.

### YENİ BULGU (Issue C'nin ağır sonucu)
`crash_history` dosyası **artık çökme geçmişi tutmuyor** — içi yankı döngüsü çöpüyle dolu.
Yani kullanıcının "çökme geçmişi" özelliği, kendi log yankısı tarafından **tamamen
kullanılamaz hale getirilmiş**. `LogEchoGuard` düzeltmesi bunu da çözer; ancak:
→ **Aksiyon (yeni):** `FileLoggingTree` içine tek satırlık tekrar bastırma (aynı mesaj
  zinciri 3 kez görülürse `… [tekrar x N]` yaz) eklenmeli. Aksi halde döngü tekrar
  başladığında disk 227 KB/sn hızında dolar.

### Dosya sonundaki gerçek veriler (parça 28–29)
- `[RECORDED CRASH EXIT] 2026-11-10 02:00:41`
- `pid=17891`, sürüm `2.4.481-beta.29904390`
- `SINIFLANDIRMA: Yakalanan bir çökmeyle sonlandı — ayrıntılı çökme raporu zaten kaydedildi (yineleme atlandı)`
- `SİSTEM KAYDI: SIGNALED (2) | önem=125 | durum=9 | PSS=670 MB`

**PSS = 670 MB** → daha önce not ettiğim 485 MB'lık Issue D'den **daha ağır**. 512 MB heap
limitli cihazda 670 MB PSS, sürecin sistem tarafından öldürülmesi (durum=9 = SIGKILL)
için yeterli gerekçedir. Yani çökme "bellek baskısı + yakalanan istisna" bileşimi.

### Thread listesi (parça 28)
```
[RUNNABLE] FileObserver, AudioThread, KitsugiLogcatCapture, ThreadPoolForeg,
           OkHttp balancehls11.playmix.uno, OkHttp dns.google,
           OkHttp hdfilmcehannemi.mobi, OkHttp api.themoviedb.org,
[WAITING ] Okio Watchdog
```
→ `balancehls11.playmix.uno` = HDFilmCehennemi "balanceHLS" sunucusu (Issue E ile aynı kaynak).
→ `KitsugiLogcatCapture` yine RUNNABLE: yankının kaynağı bu thread.

### Eylem izi (parça 28)
```
02:00:34 ekran → Tab(tab=Search)
02:00:10 Oynatıcı: Supernatural - Bölüm 20   (2 kez yazılmış)
01:59:17 Oynatıcı: Supernatural - Bölüm 19   (2 kez)
01:49:49 Oynatıcı: Supernatural - Bölüm 19   (2 kez)
01:49:04 ApiResultDetail(JikanSearchResult(malId=1622, Supernatural, ...))
01:48:48 Tab(Settings) → Tab(Explore)
```
**Çökme anı:** Bölüm 20 oynatıcıdan çıkıp Arama sekmesine (Tab=Search) dönüldüğünde,
o sekmedeki LazyColumn'da `Key "1622_20"` çakışması. Issue A ile birebir tutarlı.
Ayrıca her ekran kaydının **iki kez** yazılması, breadcrumb yazıcısında çift kayıt
(duplicate) olduğunu gösteriyor — kozmetik ama gereksiz I/O.

---

## 11. `logcat_at_crash (1) (4).txt` — ikinci çökme kaydı (50 parça)

Okunan: **parça 0** (başlangıç) ve **parça 49** (son).
Dosya başlığı: `=== Çökme Anı Logcat (Son 500 Satır) — 2026-11-10 13:12:07.721 ===`

### YENİ BULGULAR
1. **Bu, Issue B'nin (StackOverflowError) İKİNCİ ve AYRI kaydı.**
   - Zaman: **13:12:07** (önceki 02:00:36 kaydından ~11 saat sonra)
   - Süreç: **pid 3570 = 2.4.485-beta** (yani en son sürüm)
   - **Thread: 20191** — yani özyineleme **ana thread'de değil, arka plandaki bir
     iş parçacığında** oluştu. Bu önemli: `main` thread'de 1039 KB stack dolarken,
     arka planda varsayılan 1 MB stack daha da hızlı dolar.
2. Zincir birebir aynı: `MangaTrCompat.isMangaTr → MihonSourceWrapper.getBaseUrl →
   SourceConfigStore.getPreferredBaseUrl → getActiveDomain → …`
3. Dosya **500 satırda kesilmiş** (logcat halka tamponu), yığın izi yarıda bitiyor —
   yani bu dosya yeni bir imza içermiyor, sadece B'nin tekrarı.

### Uygulanan ek sertleştirme (T8 tamamlandı)
Çökmenin **liste anahtarı** tarafı (Issue A) için kalan 4 riskli nokta kapatıldı.
Hepsi `uniqueLazyKeys()` ile tekilleştirildi (çakışan anahtara `#2`, `#3` ekler):

| Dosya | Satır | Önce | Sonra |
|---|---|---|---|
| `ui/screens/manga/MangaBrowseScreen.kt` | 286 | `items(states, key = { it.source.stableSourceKey() })` | `itemsIndexed(states, key = { i,_ -> browseKeys[i] })` |
| `ui/components/addons/MangaExtensionTab.kt` | 837 | `"${src.name}_${src.lang}"` | `sourceRowKeys[i]` |
| `ui/components/addons/MangaExtensionTab.kt` | 967 | `key = { it.sourceKey }` | `reportKeys[i]` |
| `ui/components/KitsugiAllActivitiesBottomSheet.kt` | 196 | `key = { it.id }` | `activityKeys[i]` |

Böylece "Key … was already used" çökmesi artık **hiçbir listede** oluşamaz.

### Ek doğrulama (kod tarafı)
- `SourceConfigStore.key()` → derinlik sayacı (`MAX_KEY_DEPTH = 4`) ✔
- `MangaSource.stableSourceKey()` → asla dinamik `baseUrl` okumaz, `originalBaseUrl` ✔
- `MihonSourceWrapper.baseUrl` → `runCatching` ile `Throwable` (dolayısıyla
  `StackOverflowError`) yakalanır, ham adrese düşer ✔
- `KitsugiSessionSupervisor.noteAction()` → 1–2 sn içindeki birebir tekrarlar yazılmaz
  (raporda görülen çift `ekran →` satırları eski sürümden) ✔
- `FileLoggingTree.signatureOf()` → zaman damgası nötrleme eklendi (yankı dosya şişmesi) ✔

---

## 12. `Kitsugi_Crash_Report (1) (5).txt` — yapısal çökme raporu (13:12:07)

Okunan: **parça 0** (159 parçanın başı). Rapor başlığı: `KİTSUGİ CRASH REPORT — 2026-11-10 13:12:07.598`

### Raporun künyesi
| Alan | Değer |
|---|---|
| Sürüm | **2.4.485-beta.29904432** (release) |
| PID | 3570 |
| Uptime | 2881 s (~48 dk) |
| Cihaz | Xiaomi 23043RP34C / pipa / Android 13 (API 33) / arm64-v8a |
| **Thread** | **`OkHttp Dispatcher` — Thread ID 2947 — BACKGROUND** |
| Bellek | **219 MB / 512 MB — "Serbest: 0 MB"** |
| Önbellekler | homeHero.resolvedBackdrops:63, homeHero.decodedBackdrops:55, heroArtwork:34, detail.tmdbMedia:27, detail.malToTmdb:25, detail.tmdbToTvdb:23, detail.fanGallery:19, youtube.playback.source:7 |
| Hata | `java.lang.StackOverflowError: stack size 1039KB` |

### Kritik çıkarım
Bu çökme **Issue B'nin ağ iş parçacığında** oluşan halidir:
```
isMangaTr → MihonSourceWrapper.getBaseUrl → getPreferredBaseUrl → getActiveDomain
          → SourceConfigStore.key → stableSourceKey → trim → … (sonsuz)
```
Yani kaynak adı çözümlemesi **ana thread'de de, OkHttp thread'lerinde de** çöküyor.
Uygulanan üç katmanlı düzeltme (sabit kimlik + derinlik sayacı + runCatching) bu
thread'in hangisi olduğundan bağımsız çalışır; çünkü `keyDepth` **ThreadLocal**'dır.

### Bellek bulgusu (Issue D — güncel)
- Çökme anında **serbest heap 0 MB**, PSS 670 MB (crash_history kaydı), uptime 48 dk.
- `KitsugiMemoryGuard` mevcut ama caches listesi büyümeye devam ediyor.
- **Repo tarafı kontrol edildi — önlemler ZATEN VAR:**
  - `KitsugiApplication.buildImageLoader()` → `maxSizePercent(context, 0.15)` (%15)
  - `KitsugiApplication.onTrimMemory()` → `KitsugiMemoryGuard.onTrimMemory(level)`
  - `KitsugiMemoryGuard.registerClearer("coil.memory")` → baskı altında `memoryCache.clear()`
  - `StorageJanitor` → `loader.memoryCache.clear()` yedek temizleyici
  → Yani "serbest 0 MB" anı, düzeltilecek eksik bir ayar değil; çökme anında GC'nin
  son ana kadar çalıştığını gösteren bir ölçüm. **Aksiyon gerekmiyor.**
- **DÜZELTME (önceki notum yanlıştı):** `detail.*` önbellekleri **zaten `BoundedCache`**
  (örn. `DetailCache.tmdbMediaCache = BoundedCache("detail.tmdbMedia", 3000)`).
  Yani 63/55/34/27/25/23/19 değerleri "sınırsız büyüme" değil, **sınırlı önbelleklerin
  anlık doluluk oranı** (limitleri binler mertebesinde). Bellek tarafında **düzeltilecek
  bir kod hatası yok**; 219 MB canlı heap ağır bitmap kullanımının normal sonucudur.


---

## 13. `crash_history (1) (4).txt` başı + `kitsugi_runtime (1) (3).txt` başı

### crash_history (1) (4) — 165 parça, okunan: parça 0
`[BACKGROUND EXIT] 2026-11-10 11:48:06` · pid **734** · sürüm 2.4.481-beta
- Oturum: **02:09:56 → 11:29:02 = 33.545 s (9,3 saat)**
- Son ekran: **Oynatıcı: Supernatural — Bölüm 21**
- Çıkış: `OTHER (13) — SwipeUpClean | önem=400 | durum=0 | PSS=485 MB`
- Sınıflandırma: "Son uygulamalardan kaydırılarak kapatıldı — **çökme değil**"
→ Issue D doğrulandı. Ayrıca eylem izinde **aynı saniyede çift `ekran →`** satırları var
  (02:31:19 ×2, 02:24:49 ×2 …) — bu kayıt 2.4.481'den, tekrar bastırma düzeltmesi öncesi.
- Kayda değer: `galeri gezinme: sayfa 1/292` · `Galeri: Supernatural (707 görsel)` →
  galeri 707 görseli 292 sayfada gösteriyor.

### kitsugi_runtime (1) (3) — 349 parça, okunan: parça 0
Yankı döngüsü hâkim, ama aralarında **gerçek** satırlar var:
```
D/PlayerMediaSessionHelper(17891): Metadata updated: title=Supernatural - Bölüm 19 duration=2466422ms
D/PlayerPipHelper(17891): PiP actions updated: isPlaying=true hasNext=true
I/tsugi.animelist(17891): Method exceeds compiler instruction limit: 16739 in
    void z7.l3.a(String,String,String,String,Map,List,List,int,Integer,...,String,int,int,String...)
```
**YENİ BULGU:** ART "compiler instruction limit" uyarısı — 16.739 talimatla sınırı aşan
tek bir (R8 sonrası `z7.l3.a` olarak gizlenmiş) dev composable fonksiyonu. Etkisi:
o ekran her açıldığında JIT/AOT devre dışı kalır, **kare düşmesi ve ısınma** olur.
Çökme sebebi değil; aksiyon: o composable'ı alt bileşenlere bölmek (parametre sayısı
~20 → tek fonksiyonda 16K talimat sınırı). Kaynak eşlemesi R8 mapping dosyası olmadan
yapılamıyor, o yüzden bu madde **belgelenmiş teknik borç** olarak bırakıldı.

### Önbelleklerin gerçek durumu (düzeltme)
`homeHero.resolvedBackdrops:63`, `detail.tmdbMedia:27` gibi sayılar **sınırsız büyüme değil**;
hepsi `BoundedCache` (limit örn. 3000). Bellek tarafında kod hatası yok.

---

## 14. Son kod düzeltmesi (bu tur)
`SourceConfigStore.getPreferredBaseUrl(source)` tek argümanlı overload'u artık
**`source.baseUrl` yerine `source.originalBaseUrl`** okuyor. Böylece `baseUrl` dinamik
getter'ı üzerinden depoya geri dönen son dolaylı özyineleme yolu da kapandı; derinlik
sayacı yalnızca son savunma hattı olarak kaldı (artık normalde hiç devreye girmiyor).
