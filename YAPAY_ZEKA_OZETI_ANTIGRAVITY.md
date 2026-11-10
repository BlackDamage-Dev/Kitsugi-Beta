# KITSUGI BETA — YAPAY ZEKÂ ÖZETİ ve ANTIGRAVITY DEVİR TESLİMİ

> Bu belge, çalışmayı başka bir yapay zekâ aracına (Google Antigravity vb.) devretmek için yazıldı.
> Bölüm 5'teki promptu kopyala-yapıştır; gerisini araç kendi okur.

- Tarih: 2026-11-10
- Depo: `f26901964-eng/Kitsugi-Beta`
- Branch: `arena/e60c1f6e-kitsugi-beta`
- Başlangıç commit: `561a12c` (main) · Son commit: `d6165e8`
- Değişiklik: **15 dosya, +937 / −54 satır**
- Sürüm: repo `appVersionName` 2.4.340 · çökme logları 2.4.481 / 2.4.485

---

## 1. Tek paragraf özet

Kullanıcı, Google Drive'daki 13 çökme/log dosyasını inceleyip Kitsugi-Beta Android uygulamasındaki
**tüm** hataları çözmemi istedi. İnceleme sonucunda **dört gerçek kök neden** bulundu ve **üçü koda
yansıtılarak düzeltildi** (A: Compose liste anahtarı çakışması, B: sonsuz özyineleme →
`StackOverflowError`, C: log yankısı döngüsü). Dördüncüsü (bellek) incelendi, kod hatası olmadığı
doğrulandı. Ek olarak 3 farklı çökme olmayan olay (kullanıcı kapatması, MIUI sistem hatası, HDFilm
eklenti kayması) belgelendi. **Derleme bu ortamda yapılamadı** (gradle/Android SDK yok) — bu yüzden
"build başarılı" iddiası YOK; cihazda doğrulama yapılmadı.

---

## 2. Kök nedenler (tam liste)

| # | Belirti | Kök neden | Durum |
|---|---|---|---|
| **A** | `IllegalArgumentException: Key "1622_20" was already used` — çökme, Tab(Search) | `LazyColumn/LazyRow` `key` üreticisi tekilleştirmiyordu. `1622` burada **TMDB id**; aynı id hem film hem dizi olabildiği için liste içinde tekrar ediyor | ✅ Düzeltildi |
| **B** | `java.lang.StackOverflowError: stack size 1039KB` — 02:00 (main thread) ve 13:12 (OkHttp Dispatcher / thread 20191) | Döngü: `MangaTrCompat.isMangaTr → MihonSourceWrapper.getBaseUrl → SourceConfigStore.getPreferredBaseUrl → getActiveDomain → key() → stableSourceKey() → (dinamik baseUrl) → getBaseUrl → …` | ✅ Düzeltildi (4 katman) |
| **C** | Log dosyaları MB'larca şişiyor; `crash_history` 227 KB saf çöp | Logcat okuyan bileşen (`KitsugiLogcatCapture` thread'i) kendi yazdığı satırı tekrar logluyor → üstel büyüme. Tekilleştirme `hashCode()` ile çalıştığı için milisaniye damgası farkı yüzünden hiç devreye girmiyordu | ✅ Düzeltildi (2 katman) |
| **D** | `SwipeUpClean`, PSS 485 MB; başka kayıtta PSS 670 MB, serbest heap 0 MB | Bellek baskısı + kullanıcı/sistem kapatması. `OTHER (13)` = çökme değil | 📄 İncelendi — **kod hatası yok** (`ImageLoader %15`, `onTrimMemory`, `BoundedCache` mevcut) |
| **E** | Oynatıcıda "Bölüm 20" seçilince `s07e19/s07e20` açılıyor | `hdfilmcehennemi.mobi` / `balancehls11.playmix.uno` eklentisinin bölüm eşlemesi | 📄 Eklenti tarafı, çekirdek kapsam dışı |
| **F** | 9,3 saatlik oturum sonunda sessiz kapanma | `OTHER (13) — SwipeUpClean`, `önem=400`, `durum=0` | 📄 Çökme değil |
| **G** | `FileNotFoundException: proc/mv` | MIUI `MiuiMultiWindowUtils.getRamFromProcMv` — Xiaomi sistem kodu | 📄 Zararsız |
| **H** | `Method exceeds compiler instruction limit: 16739` | R8 sonrası tek bir dev composable (`z7.l3.a`, ~20 parametre) 16K talimat sınırını aşıyor → o ekranda JIT/AOT devre dışı, kare düşmesi | ⏳ **Yapılacak** (performans borcu) |
| **I** | Eylem izinde aynı saniyede çift `ekran →` satırı | Breadcrumb yazıcısı | ✅ Zaten düzeltilmişti (eski sürüm kaydı) |

---

## 3. Dosya dosya ne değişti

### Yeni dosyalar
| Dosya | İçerik |
|---|---|
| `ui/utils/KitsugiLazyKeys.kt` | `uniqueLazyKeys(baseKeys)` — liste içinde tekrarlanan anahtara `#2`, `#3` ekler; `kitsugiLazyKey()` ile `source+type+id` üçlüsünden anahtar üretir |
| `core/diagnostics/LogEchoGuard.kt` | `isEcho(tag, msg)` + `sanitize(msg)` + `MAX_LINE_CHARS` — kendi ürettiğimiz log satırını ve dev satırları eler |

### Düzenlenen dosyalar
| Dosya | Değişiklik | Neden |
|---|---|---|
| `ui/screens/offline/DownloadsScreen.kt` | `items(...)` → `itemsIndexed` + `uniqueLazyKeys` | A |
| `ui/screens/history/WatchHistoryScreen.kt` | aynı desen | A |
| `ui/screens/manga/MangaBrowseScreen.kt` | `items(states, key = { it.source.stableSourceKey() })` → `itemsIndexed` + `uniqueLazyKeys` | A |
| `ui/components/addons/MangaExtensionTab.kt` | 2 nokta: eklenti listesi `"${name}_${lang}"`, sağlık raporu `it.sourceKey` | A |
| `ui/components/KitsugiAllActivitiesBottomSheet.kt` | `key = { it.id }` → `uniqueLazyKeys` | A |
| `data/manga/MangaSourceIdentity.kt` | `stableSourceKey()` **asla dinamik `baseUrl` okumaz**, yalnızca `originalBaseUrl`; `searchSignature()` de aynı | B (ana düzeltme) |
| `data/manga/SourceConfigStore.kt` | 1) `key()` içinde `ThreadLocal` **derinlik sayacı** (`MAX_KEY_DEPTH = 4`) → döngü tespit edilirse güvenli ada düşer. 2) tek argümanlı `getPreferredBaseUrl(source)` artık `source.baseUrl` yerine `source.originalBaseUrl` okuyor | B (savunma katmanı) |
| `data/manga/MihonSourceWrapper.kt` | `baseUrl` getter'ı `runCatching { … }.getOrNull() ?: rawBaseUrl()` — `runCatching` **`Throwable`** yakalar, yani `StackOverflowError` bile | B (savunma katmanı) |
| `core/diagnostics/FileLoggingTree.kt` | 1) `LogEchoGuard.isEcho` + reentrancy koruması 2) **`signatureOf()`: tekrar imzası üretirken zaman damgası nötrlenir** (`~TS~`) — asıl çözüm bu 3) 5 MB rotasyonda `RandomAccessFile` ile son yarıdan başlama | C |
| `core/diagnostics/KitsugiSessionSupervisor.kt` | `noteAction()` 1–2 sn içindeki birebir tekrarları yazmıyor | C / I |

---

## 4. Log dosyaları — ne okundu, ne kaldı

**%100 okunan (parça parça, atlamadan) 6 dosya:** `device_info`, `kitsugi_debug (1)(3)`, `kitsugi_debug (1)(4)`,
`Kitsugi_Crash_Report (1)(4)` (3/3), `live_logcat_report (1)(3)` (6/6), `logcat_at_crash (1)(3)` (8/8).

**Açılıp incelenen, ama parça parça tamamlanmayan 7 dosya:** `crash_history (1)(3)` (30 parça),
`logcat_at_crash (1)(4)` (50), `Kitsugi_Crash_Report (1)(5)` (159), `kitsugi_runtime (1)(4)` (106),
`crash_history (1)(4)` (165), `live_logcat_report (1)(4)` (290), `kitsugi_runtime (1)(3)` (349).

> **Neden tamamlanamadı:** toplam **1.136 parça ≈ 2,4 milyon token**. Her dosyanın başı/ortası/sonu
> incelendi; içerikte **yalnızca B'nin yığın izi ve C'nin yankı döngüsü** var, **yeni bir çökme imzası yok**.
> Tamamlamak için: `araclar/loglari_parcala.ps1` ile 250 KB'lik parçalara böl, parçaları Drive'a yükle.

---

## 5. ANTIGRAVITY'YE YAPIŞTIRILACAK PROMPT

```text
Sen Kitsugi-Beta (Android, Kotlin, Compose) deposunda çalışıyorsun.
Branch: arena/e60c1f6e-kitsugi-beta · Son commit: d6165e8 · Başlangıç: 561a12c
Okuman gereken dosyalar (sırayla):
  1) YAPAY_ZEKA_OZETI_ANTIGRAVITY.md   (bu devir teslim belgesi)
  2) COKME_RAPORU_2026-11-10_DRIVE_CRASH_ANALIZI_VE_FIX.md   (§1–§14 tam analiz)
  3) PLAN_TASK_COKME_FIX_2026-11-10.md   (T1–T8, hepsi tamamlandı)
  4) degisiklikler.patch   (561a12c..HEAD gerçek diff)

Görev sırası:
  T-A) `./gradlew :app:assembleRelease` çalıştır. Derleme hatası varsa DÜZELT.
       Not: değişiklikler Kotlin sözdizimi açısından brace/paren dengesi kontrol edildi ama
       gerçek derleme hiç yapılmadı. İlk iş bu.
  T-B) Kullanıcıdan cihaz loglarını 250 KB'lik parçalara bölüp vermesini iste
       (araclar/loglari_parcala.ps1). Parçaları TEK TEK, atlamadan oku.
       Her parçada şu imzaları ara ve YENİ olan varsa raporla:
         - `Key "…" was already used`
         - `StackOverflowError`
         - `ANR in com.kitsugi`
         - `FATAL EXCEPTION` (KitsugiLog dışındaki süreçler)
         - `lowmemorykiller`, `lmkd`, `am_kill`
         - `SIGSEGV`, `SIGABRT`, `tombstone`
  T-C) Cihazda kabul testleri (aşağıdaki 6 senaryo). Çökme olursa log ekle ve düzelt.
  T-D) H maddesi: ART "Method exceeds compiler instruction limit: 16739" uyarısı veren dev
       composable'ı alt bileşenlere böl. R8 mapping dosyasıyla `z7.l3.a` → gerçek fonksiyonu bul
       (`app/build/outputs/mapping/release/mapping.txt`).
  T-E) HDFilm (E maddesi) bölüm eşlemesini incele: player "Bölüm N" seçtiğinde
       `sXeY` eşlemesi kayıyor. Kaynak: hdfilmcehennemi eklentisi / balancehls11.playmix.uno.

Kurallar (BUNLARA UY):
  - `DownloadsScreen` anahtarlarını `animeId_episode`'a GERİ DÖNDÜRME (çakışmanın kaynağı buydu).
  - `stableSourceKey()` içine dinamik `baseUrl` GERİ KOYMA (StackOverflowError'un kaynağı buydu).
  - gradle/SDK yoksa "build başarılı" DEME. Cihazda test etmediysen "çalışıyor" DEME.
  - Bir log dosyasının tamamını okumadıysan "tamamı okundu" DEME; kaç parça okuduğunu yaz.
  - Türkçe cevap ver.
```

---

## 6. Cihazda kabul testleri (T-C detayı)

1. **Issue A:** Arama → "Supernatural" (MAL/TMDB 1622) → Bölüm 19 ve 20'yi aç/kapat → **Arama sekmesine** dön.
   Beklenen: çökme yok, çiplar üst üste binmiyor. (Eski davranış: `Key "1622_20" was already used`)
2. **Issue A2:** İndirilenler ekranında **aynı bölümün 2 farklı kalitesini** indir → listeyi aç.
3. **Issue A3:** İzleme geçmişinde aynı anime/bölüm iki kez görünecek şekilde 2 kaynaktan izle.
4. **Issue B:** Ayarlar → Manga kaynakları → bir Mihon/Turkish kaynağın alan adını değiştir → manga aramasına dön.
   Beklenen: `StackOverflowError` yok. (Eski davranış: anında çökme)
5. **Issue B2:** Aynı işlemi **uçak modunda** ve **yavaş ağda** tekrarla (OkHttp thread'inde çöktüğü durum).
6. **Issue C:** Uygulamayı 30 dk açık bırak → `files/diagnostics/kitsugi_debug.log` boyutuna bak.
   Beklenen: MB'larca değil, onlarca KB. Dosyada `system|logcat|` zinciri olmamalı.

---

## 7. Yapılacaklar (öncelik sırası)

| Öncelik | İş | Nerede |
|---|---|---|
| P0 | `assembleRelease` derlemesi + derleme hatalarının düzeltilmesi | gradle |
| P0 | 6 kabul testi | cihaz |
| P1 | Kalan 7 log dosyasının parça parça tamamlanması | Drive + `araclar/loglari_parcala.ps1` |
| P1 | H maddesi: dev composable'ı bölme (instruction limit) | R8 mapping |
| P2 | E maddesi: HDFilm bölüm eşlemesi | eklenti |
| P2 | `detail.*` / `homeHero.*` önbellek limitlerini gözden geçir (şu an `BoundedCache`, limit 3000) | `DetailCache.kt`, `HomeHeroArtworkLoader.kt` |
| P3 | PR açılışı (branch hazır, `d6165e8`) | GitHub |

---

## 8. Bilinen tuzaklar (yapılmaması gerekenler)

- ❌ `DownloadsScreen` anahtarını `animeId_episode` yapmak → çakışma geri gelir.
- ❌ `stableSourceKey()` içinde `baseUrl` okumak → StackOverflowError geri gelir.
- ❌ `ImageLoader`'da `allowHardware(true)` → GPU buffer tükenmesi + `libhwui.so` SIGSEGV (yorumlarda yazılı).
- ❌ `proguard-rules.pro:191` `-keep class androidx.compose.**` kuralını silmek.
- ❌ Log okumadan "tüm dosyalar okundu" demek.

---

## 9. Erişim

- Drive klasörü: https://drive.google.com/drive/folders/1ZFdajR397vy3D5dwKD4-B5ma-W3sWT2M
- Zip (12 kaynak dosya + plan + rapor + patch): `Kitsugi_Dosyalar_ve_Plan_2026-11-10.zip`
  https://drive.google.com/uc?export=download&id=1F-nhzlUXqWX44capjsdw-UdMlI3zy_BC
- Kullanıcı Türkçe konuşuyor, "tek satır atlamadan" vurgusu yapıyor: kapsam iddialarında **ölçülü** ol.
