# PLAN / TASK — İzleme Geçmişi Sayfası Yenileme ("daha kullanışlı" paketi)

**Tarih:** 2026-11-01
**Sürüm:** v2.4.341 (`appVersionName`)
**Dal:** `arena/479e387c-kitsugi-beta` (temel: `6bbd40e`, çalışma commit'i `fb16f1b`)
**Tema:** İzleme Geçmişi ekranı + eklenti kaynak metinlerinin temizlenmesi

**Şikâyet / istek (kullanıcı):**
> "İzleme geçmişi sayfasını daha kullanışlı yap. Sıralama özellikleri ekle ve senin aklına gelen,
> sayfayı daha derli toplu, kullanıcı dostu ve mantıksal kılacak güzel özellikler ekle."

Ek olarak gözlemlenen hata: geçmişte kaynak satırı
`Yayın: HDFilmCehennemi · Close · .fa-secondary{opacity:.4} DUAL` gibi **HTML/CSS artığı** ile
görünüyordu (eklentiler kaynak adını sitenin HTML'inden topluyor).

---

## 1. Yapılan işler (özellik listesi)

### 1.1 Sıralama (6 mod, kalıcı)
| Mod | Davranış |
|---|---|
| `RECENT` | Son izlenenler önce (varsayılan) |
| `OLDEST` | En eski önce |
| `CONTINUE_FIRST` | Devam edenler → başlanmamışlar → tamamlananlar; devam edenlerde **kalan süre azalan** |
| `PROGRESS` | İlerleme yüzdesi çok → az |
| `TITLE_ASC` | Başlık A-Z (yerel `Collator`, TR karakter duyarlı) |
| `SOURCE` | Kaynak/eklenti A-Z |

Eşitlikte ikincil ölçüt `watchedAtMs` azalan. Seçim `WatchHistoryPreferences` ile kalıcı.

### 1.2 Filtreler
- **Arama:** başlık + eklenti adı + birleşik kaynak etiketi. Türkçe karakter duyarsız
  (`kisi` → **Kişi**, `KİŞİ` → **Kişi**).
- **İlerleme çipleri:** Tümü / Devam edenler / İzlenenler / Başlanmamış.
- **Medya çipleri:** Hepsi / Diziler / Filmler.
- **Kaynak (eklenti) filtresi:** geçmişte görülen kaynaklardan seçim; kaynak kaybolursa filtre
  otomatik sıfırlanır (`LaunchedEffect(availableSources)`).
- Sonuç boşsa "Filtreleri temizle" kısayollu boş durum ekranı.

### 1.3 Diziye göre gruplama
- Aynı `animeId` altındaki bölümler tek kartta; kart açılınca bölüm satırları (ilerleme, "Devam et").
- Grup alt yazısı: dizide `S2 · E5 · 12 bölüm`, filmde yalnız **Film** (yanlış "Film · 1 bölüm" ifadesi yok).
- Seçim moduna girildiğinde gruplama otomatik devre dışı (her bölüm tek tek seçilebilsin).

### 1.4 Tarih bölümleri
- `Bugün / Dün / Bu hafta (<7 gün) / Bu ay (<30 gün) / Daha eski`.
- Yalnız **sıralama = Son izlenenler** ve gruplama kapalıyken uygulanır (sıralamayı bozmamak için
  ardışık gruplama yapılır, kayıt yeniden sıralanmaz).

### 1.5 Özet şerit (istatistik)
Toplam kayıt · devam eden · farklı başlık · toplam izleme süresi (`12 sa 40 dk` gibi).
Küçük ekranlarda taşma yok (`12.sp` + `Ellipsis`).

### 1.6 Toplu işlem ve geri alma
- Üst çubukta **Seç**: kartlara dokunarak çoklu seçim, tümünü seç, seçilenleri sil.
- Silinen her işlem için **6 saniyelik "Geri al"** şeridi (`UndoBar`), `WatchHistoryManager.restore` ile.
- Tek kayıt için uzun basma/menü: **Devam et / Baştan başlat / Seç / Bu bölümü kaldır / Başlığı kaldır**.
- "Tümünü temizle" onay diyaloğu + geri alma.

### 1.7 Oynatma mantığı
- `isDirectPlayable`: kayıtlı akış adresi **30 dk** içinde ve **bu uygulama oturumunda** yazıldıysa
  doğrudan oynatıcı açılır ("Anında" rozeti), aksi hâlde kaynak seçici otomatik oynatmayla başlar.
- Tamamlanmış kayıt (%92+) açılınca baştan başlar; yarıda kalan kaldığı yerden devam eder.
- "Baştan başlat", kaynak seçici yolunda da çalışır: `WatchHistoryManager.resetPosition` yalnız
  konumu sıfırlar, `watchedAtMs`'e dokunmaz (bayat akışın "taze" görünmesini engeller).

### 1.8 Kaynak metni temizliği (`StreamLabelSanitizer`)
- Silinen: CSS kural blokları (`{…}` içinde `:`/`;` varsa), `@media`/`@keyframes`, `<style>/<script>`
  gövdeleri, HTML etiketleri, `class`/`style` öznitelikleri, CSS sınıfı görünümlü parçalar
  (`.fa-secondary`), HTML varlıkları (çözülür), sıfır genişlikli karakterler.
- Korunan: `.mp4`, `Spider-Man.No.Way.Home.1080p` gibi gerçek başlık parçaları.
- `·` / `•` / `|` ayırıcıları normalize edilir, boş ve **tekrar eden parçalar** (harf duyarsız)
  atılır, sonuç 120 karakterle sınırlanır.
- Ses türü `Dual > Dublaj > Altyazılı` sırasıyla tespit edilip ayrı **rozet** olarak gösterilir.
- Kullanım noktaları: kaynak seçici kartı (`StreamCard`), geçmişe yazma (`KitsugiStreamScreen`),
  oynatıcı ses/altyazı parça adı (`KitsugiFullscreenPlayerScreen`), indirme kuyruğu ve indirme
  bildirimi (`AnimeDownloadService`), indirilenler kartı (eski kayıtlar için gösterimde).

### 1.9 Küçük UX düzeltmeleri
- Sistem geri tuşu önce seçim modundan çıkar / aramayı kapatır (`BackHandler`), ekrandan çıkmaz.
- Arama kapatılınca sorgu sıfırlanır; üst çubuk ikonları 40 dp dokunma alanı; tüm ikonlarda
  `contentDescription`.
- Liste anahtarı `animeId + episode` (her ilerleme güncellemesinde kart yeniden kurulmuyor).
- Yükleme yolunda `(animeId, bölüm)` mükerrerleri `dedupeByKey` ile temizlenir → "Duplicate key" çökmesi engellendi.

---

## 2. Teknik kararlar

| # | Karar | Gerekçe |
|---|---|---|
| K1 | Sıralama/filtre/gruplama mantığı saf Kotlin `WatchHistoryQuery` nesnesinde | Compose'dan bağımsız → birim testi yazılabilir, ekran sadeleşir |
| K2 | Tercihler `SharedPreferences` (`watch_history_prefs`) | DataStore şemasını büyütmemek için |
| K3 | `Collator` çağrısı `runCatching` ile korunuyor | `KitsugiStrings.locale()` birim testinde (Android'siz JVM) çöküyordu |
| K4 | `resetPosition` ayrı fonksiyon, `updateProgress` değil | `updateProgress` `watchedAtMs`'i de güncelleyip bayat akışı "taze" gösteriyordu |
| K5 | Temizlik hem yazma hem gösterim anında | Eski kayıtlar (indirmeler) de düzeltsin |
| K6 | Gruplama yalnız `animeId` üzerinden | Başlık değişse bile bölümler aynı kartta kalsın |

---

## 3. Değişen / eklenen dosyalar (17)

**Yeni**
- `app/src/main/java/com/kitsugi/animelist/ui/screens/history/WatchHistoryQuery.kt` (407 satır)
- `app/src/main/java/com/kitsugi/animelist/ui/screens/history/WatchHistoryPreferences.kt`
- `app/src/main/java/com/kitsugi/animelist/utils/StreamLabelSanitizer.kt`
- `app/src/test/java/com/kitsugi/animelist/ui/screens/history/WatchHistoryQueryTest.kt` (~18 test)
- `app/src/test/java/com/kitsugi/animelist/utils/StreamLabelSanitizerTest.kt` (8 test)
- `release_notes_v2.4.341.md`, bu plan dosyası

**Değişen**
- `ui/screens/history/WatchHistoryScreen.kt` (tam yeniden yazım, ~1850 satır)
- `data/local/WatchHistoryManager.kt` (`removeMany`, `removeSeries`, `restore`, `resetPosition`, `dedupeByKey`)
- `ui/screens/stream/KitsugiStreamScreen.kt`, `ui/screens/stream/StreamCard.kt` (sanitize entegrasyonu)
- `ui/screens/fullscreen/KitsugiFullscreenPlayerScreen.kt` (parça adı temizliği)
- `ui/screens/offline/DownloadsScreen.kt`, `core/download/AnimeDownloadService.kt` (temiz gösterim)
- `res/values/strings.xml`, `res/values-en/strings.xml` (70 `wh_*` anahtarı, TR = EN)
- `app/build.gradle.kts` (`appVersionName = 2.4.341`), `RELEASE_NOTES.md`

---

## 4. Doğrulama durumu

- **Gradle / Android derleme: ÇALIŞTIRILMADI** — sandbox'ta JDK, Gradle ve Android SDK yok
  (`which java` / `ls /usr/lib/jvm` boş).
- Kotlin sözdizimi: 12 dosya tree-sitter-kotlin (0.26.0) ile hatasız.
- `values/strings.xml` + `values-en/strings.xml`: XML parse temiz; 70 `wh_*` anahtarı TR = EN;
  kodda kullanılan `R.string.*` referanslarının tamamı mevcut; kullanılmayan anahtar 0;
  `%1$s`/`%1$d` format argümanları kodla uyumlu.
- İkon/erişilebilirlik: ekrandaki 10 `IconButton`'ın tümünde `contentDescription` var,
  kullanılan `Icons.*` importları eksiksiz.
- Commit: `fb16f1b` → `origin/arena/479e387c-kitsugi-beta`.

### Kullanıcıda çalıştırılacak komutlar
```bash
./gradlew testFossDebugUnitTest --tests "*WatchHistoryQueryTest" --tests "*StreamLabelSanitizerTest"
./gradlew :app:assembleFossDebug        # veya :app:assembleGmsRelease
```
> Not: `assembleGmsBeta` diye bir görev yok; build type'lar `debug`/`release`, flavor'lar `foss`/`gms`.

---

## 5. Kalan riskler / sonraki adımlar

1. **İlk gerçek derleme** yapılmadı. En olası hata yüzeyi: `WatchHistoryScreen` içindeki
   `WatchHistoryQuery` çağrı imzaları (`applyFilters`, `withSections`, `availableSources`).
2. `WatchHistoryQueryTest` beklentileri ilk kez gerçek JVM'de sınanacak
   (`"S1 · E3"`, `stats.watchedMs = 1050L`, `dayOffset`/`startOfDay`, `sourceLabel` birleşimi).
3. `StreamLabelSanitizer` gerçek eklenti çıktısıyla cihazda doğrulanmalı — yeni bir çöp deseni
   görülürse `sanitize()` içine tek satır kural eklenir.
4. Cihazda kontrol listesi: seçim modunda geri tuşu, gruplama + seçim geçişi, "Baştan başlat"
   (hem doğrudan oynatma hem kaynak seçici yolu), 6 sn geri alma, TR/EN dil geçişi.
