# 📋 PLAN / TASK — Manga Keşfet: “Yerel / çevrilmiş ad” araması + kompakt keşif üst bölümü + “Tümü” filtresi kalıcılığı

**Durum:** Kod tarafı TAMAMLANDI ✅ · Derleme/cihaz doğrulaması BEKLİYOR ⏳ (ortamda JDK/Gradle/Android SDK yok)
**Dal:** `arena/41cee537-kitsugi-beta` · **Tarih:** 2026-11-06
**Kullanıcı talebi:**
1. Manga keşifteki “Yerel / çevrilmiş ad” düğmesi düzgün çalışmıyor: İngilizce (veya orijinal) ad varsa Türkçeye çevrilip eklentiler **Türkçe adla** aransın; çünkü Manga-TR gibi Türkçe eklentiler yalnızca Türkçe başlıkta sonuç döndürüyor. Sorun ve mantık kusursuz olsun.
2. Manga eklenti keşif ekranının üst bölümü (başlık + kaynak alanı), video eklenti keşif ekranındaki gibi **derli toplu** olsun; dikey **ve** yatayda uyumlu, kompakt bir üst bar.
3. Manga kartlarına tekrar tekrar girip dönünce ekran kendiliğinden “Tümü”den çıkıp **belirli bir eklenti seçilmiş gibi** davranıyor; filtre kalıcılığı düzgün çözülsün.

---

## 🎯 Sorun 1 — “Yerel / çevrilmiş ad” gerçekten Türkçe adla aramıyordu

### Kök nedenler (kodda doğrulandı)

1. **Tek adla arama:** `MangaBrowseViewModel` aramayı yalnızca `fetchSearchManga(page, query)` ile
   yapıyordu. Kullanıcı “The Greatest Estate Developer” yazınca Manga-TR gibi eklentilere de bu
   İngilizce ad gidiyordu; eklenti Türkçe başlık indekslediği için boş dönüyordu.
2. **Buton yalnızca görseldi:** “Yerel / çevrilmiş ad” düğmesi bir `enum` bayrağını çeviriyor,
   hiçbir ad çözümlemesi/çeviri yapmıyordu → kullanıcı gözünde “buton çalışmıyor”.
3. **Sıralama hatası:** İlk uygulanan sürümde varyant listesi `sorgu + yerel + orijinal` şeklinde
   kurulduğu için **sorgunun kendisi** listenin başına geçiyor, çevrilmiş Türkçe ad ancak ikinci
   denemede aranıyordu. Yavaş eklentilerde/ilk istek boş dönse bile buton “hiçbir şey yapmamış”
   gibi görünüyordu. (Bu tur içinde tespit edilip düzeltildi.)
4. **Eleme tek ada göre:** Sonuç filtresi tek sorguya göre skorluyordu; kaynak Türkçe adla aranıp
   İngilizce adla kart döndürdüğünde kart eleniyordu.
5. **Hata yutulması:** Varyantlardan biri hata fırlattığında döngü sessizce `continue` ediyor,
   tüm varyantlar hata verse bile boş sonuç başarı sayılıyordu → kaynak sağlığı/kota sayaçları
   yanlış güncelleniyordu.

### Çözüm

**Yeni dosya: `data/manga/MangaTitleVariantResolver.kt`**
- `MangaSearchTitleMode` (`LOCALIZED` / `ORIGINAL`) — hangi ad önce denenir.
- `MangaSearchPlan` — `query`, `mode`, `variants` (deneme sırası), `localizedTitles`,
  `originalTitles`; `matchTitles` (elemeye kabul edilen tüm adlar), `safeVariants()`,
  `trimmedForDisplay(maxLength)`.
- `MangaTitleVariantBuilder.build(...)` — **saf** (ağ yok) varyant üretici:
  - `LOCALIZED`: `yerel(çevrilmiş) → sorgu → arındırılmış çekirdek → orijinal`
  - `ORIGINAL`: `orijinal → sorgu → arındırılmış çekirdek → yerel`
  - Büyük/küçük harf, noktalama ve Türkçe karakter farkıyla **tekilleştirme** (`dedupe`),
    1 karakterlik/boş adların atılması, kaynak başına `MAX_VARIANTS = 5` sınırı.
- `MangaTitleVariantResolver.planFor(context, query, mode, extensionLanguage)`:
  - AniList GraphQL’den `romaji / english / native / synonyms` çözümlenir (7 sn timeout).
  - Çeviri hedefi, **yüklü eklentilerin baskın dili** (`extensionLanguage`); TR eklentileri
    çoğunluktaysa `"tr"`. Eklenti dili yoksa `KitsugiTranslatePrefs.resolvedTarget()`.
  - AniList eşanlamlıları arasında hazır Türkçe ad varsa (ör. “Dahi Malikane Geliştiricisi”)
    **çeviri ağına hiç çıkmadan** yerel ad olarak kullanılır → çeviri servisi kapalı/yavaş
    olsa bile mod çalışır.
  - Non-Latin (Hangul/CJK/Kiril) sorgu orijinal sayılır ve doğrudan çeviriye verilir.
  - 30 dakika TTL’li, 64 kayıtlı LRU önbellek; anahtar `mode|hedefDil|sorgu`.
  - **Hiçbir hata aramayı engellemez:** istisna → `MangaSearchPlan.identity` (sorgunun kendisi).

**`MangaBrowseViewModel`**
- `searchSourceWithFallback(...)`: kaynak başına `plan.safeVariants()` **sırayla** denenir, ilk
  **dolu** sonuçta durur; ardından canonical `core`/`ascii` son çare. Tüm denemeler hata verirse
  ilk istisna yukarı fırlatılır (`recordSearchFailure` doğru çalışır).
- `SEARCH_TIMEOUT_MS = 45_000` (hem ilk arama hem sayfalama); zaman aşımı anlamlı bir
  `IOException`’a çevrilir.
- `filterResultsTwoPass`: sıkı skor → tutmazsa gevşek skor; kabul listesi `plan.matchTitles`
  (sorgu + tüm varyantlar).
- `patchState(..., errorCode = SourceFailureClassifier.classifyCode(e))` → çipte gerçek
  `KTS-SRC-xxxx` kodu görünür.
- `resolvePlan(...)` + `dominantSourceLanguage(sources)`.

**`MangaSearchCoordinator` / `MangaSourceRepository`**
- `postProcess(source, queries: List<String>, mangas, relaxScoring)` çok adlı eleme: her sonuç
  kabul listesindeki **en iyi** skorla değerlendirilir; yerel adla aranıp orijinal adla dönen kart
  kaybolmaz. Tek adlı imza korunur (geriye uyumlu).

**Arayüz**
- Arama modu çipi + “Arama adını değiştir” ipucu; “Arama ayrıntıları” diyaloğu denenen adları ve
  eklenti başına gerçekte kullanılan adı gösterir (butonun çalıştığı buradan doğrulanır).
- Sonuç yoksa doğrudan diğer moda geçiş çipi sunulur.

**Testler (JVM, ağ yok)**
- `app/src/test/.../MangaTitleVariantBuilderTest.kt` — 12 test: mod sırası, tekilleştirme,
  `MAX_VARIANTS`, boş/kısa ad, `matchTitles`, `identity`, non-Latin algılama, Türkçe ad algılama,
  görüntü kısaltma.
- `app/src/test/.../MangaMultiTitleSearchTest.kt` — sıralı deneme + ilk isabette durma, yerel ad
  boşsa orijinal ada düşme, orijinal modun önceliği, hata toleransı, çok adlı skorlama.

---

## 🎯 Sorun 2 — Keşif üst bölümü video keşif ekranı hizasına getirildi

**Yeni dosya: `ui/screens/manga/components/MangaDiscoverHeader.kt`**
- `MangaDiscoverTopBar`: tek satır — küçük geri düğmesi, başlık + canlı özet alt satırı,
  sağda “çözümleniyor” göstergesi ve iki küçük yuvarlak eylem (bilgi / eklenti yönetimi).
  Yükseklik içerik uzunluğundan bağımsız; ızgara ekranın çoğunu kullanır.
- `MangaTitleModeChip`: `Translate` ikonlu, seçili modu vurgulayan hap düğme.
- `MangaDiscoverSearchPanel`: **tek `Surface` kart** içinde mod çipi + arama alanı + kaynak çipleri.
  - Dikey: çip solda, ipucu sağda; arama alanı tam genişlik.
  - **Yatay (`compact = true`):** çip + arama alanı + bilgi ikonu **aynı satırda**; üst bölüm
    ızgaradan yükseklik çalmaz. Çip genişliği `172.dp` ile sınırlandığı için arama alanı
    sıkışmaz.
  - Arama alanı: odak/durum renkleri, tam genişlik, temizle düğmesi.
- `MangaDiscoverChipRow`: yatay kaydırılan çip şeridi (bar yüksekliğini büyütmez).
- `MangaDiscoverSortRow` + `SortPill`: “Sırala: İlgi / A-Z” hap düğmeleri ve sağda “Aranan: …”.

**`MangaBrowseScreen`**
- Eski dev başlık + ayrı arama kutusu + ayrı çip satırı düzeni kaldırıldı; tek kompakt blok.
- Kart yükseklikleri: dikey 210 dp, yatay `compact` 168 dp (iskelet ve shimmer kartları dahil).
- Eklenti yoksa: tek satır bilgi + doğrudan “Eklentileri düzenle” eylemi (ölü `EmptySourcesHint`
  bileşeni ve `KitsugiButton` importu silindi).
- Ayrıntı diyaloğu `heightIn(max = 480.dp)` + kaydırma: küçük ekran/yatayda taşmaz.

---

## 🎯 Sorun 3 — “Tümü”den kendiliğinden çıkma

### Kök nedenler
1. `refreshSources()` ilk kaynağı **otomatik seçiyordu** → ekran her açılışta/geri dönüşte
   “bir eklentiye tıklanmış” gibi görünüyordu.
2. Popüler modda **“Tümü” çipi yoktu**; yalnızca tek tek kaynaklar vardı, dolayısıyla seçili
   olmayan durum belirsizdi.
3. Popüler modda “Tümü” seçiliyken birleşik liste boş hesaplanıyordu (`selFilter` null → `popular`
   eşlenmiyordu) → kullanıcı boş ızgara görüp filtre değiştirmeye zorlanıyordu. “Tümü” artık en
   iyi 3 kaynağın popüler listesini birleştirir; her kart hangi kaynaktan geldiğini
   `manga.source` üzerinden taşır (yanlış eklentiyle detay açılması engellenir).
4. Kart açıp geri dönüşte `LaunchedEffect(initialQuery)` aynı sorguyu tekrar uyguluyor, arama
   sırasında filtre sıfırlanıyordu.
5. Mod/sıralama seçimi `reset()` sonrası kayboluyordu.

### Çözüm
- `selectSourceFilter(source)`: aynı seçimde **erken çıkış** (Tümü ↔ Tümü dahil). Popüler modda
  liste yeniden doldurulur; **arama modunda hiçbir ağ isteği yapılmaz**, filtre yalnızca görünümü
  süzer (filtre kayması imkânsız).
- `reset()`: filtre **bilerek** `null` (“Tümü”); mod ve sıralama `persistedTitleMode` /
  `persistedSortMode` ile korunur.
- `refreshSources()`: kaynak listesini tazeler, seçimi **korur**, otomatik seçim yapmaz.
- `LaunchedEffect(initialQuery)`: sorgu yalnızca bir kez (`lastInitialQuery`) uygulanır.
- `LaunchedEffect(ui.sources.isEmpty(), …)`: eklentiler geç yüklenirse kaynaklar tazelenir
  (seçim yine korunur) → çipler boş kalmaz.
- Filtre kimliği ada değil `stableSourceKey()` (paket adı / host) üzerine kurulu.
- TV ekranı (`TvMangaBrowseScreen`) aynı davranışa çekildi: popüler modda “Tümü” çipi eklendi,
  bayat `popular` eşlemesi düzeltildi, kaynak çiplerindeki sayaçlar gerçek popüler sayımı gösterir.

---

## 🧩 Yeni / değişen metin anahtarları
`values/strings.xml` ve `values-en/strings.xml` (parite ✅ — `17719 strings and 4 arrays`):
`manga_discover_title_placeholder`, `_mode_localized`, `_mode_original`, `_mode_localized_hint`,
`_mode_original_hint`, `_mode_resolved`, `_mode_chip_hint`, `_resolving_titles`, `_summary`,
`_popular_summary`, `_sort_relevance`, `_sort_title`, `_sort_label`, `_all`, `_no_extensions_title`,
`_no_result`, `_no_result_hint`, `_source_missing_plural`, `_managed_in_settings`, `_more_info`,
`_tried_titles`, `_tried_count`, `_using_title`.
Diyalog “Tamam” düğmesi mevcut `settings_ok` anahtarını kullanır.

---

## 📁 Değişen dosyalar
| Dosya | Durum |
|---|---|
| `data/manga/MangaTitleVariantResolver.kt` | **YENİ** — mod, plan, saf varyant üretici, AniList + çeviri çözümleyici, önbellek |
| `ui/screens/manga/components/MangaDiscoverHeader.kt` | **YENİ** — kompakt üst bar, mod çipi, arama paneli, çip/sıralama satırları |
| `data/manga/MangaSearchCoordinator.kt` | Çok adlı `postProcess` (en iyi skor) |
| `data/manga/MangaSourceRepository.kt` | `postProcessSearchResults(List<String>)` |
| `ui/screens/manga/MangaBrowseViewModel.kt` | Varyantlı arama, iki geçişli eleme, tek kaynaklı popüler, filtre kalıcılığı |
| `ui/screens/manga/MangaBrowseScreen.kt` | Kompakt düzen, Tümü çipi, bilgi diyaloğu, boş durum, yatay kartlar |
| `ui/tv/manga/TvMangaBrowseScreen.kt` | Tümü çipi + popüler liste düzeltmesi, app context’li Factory |
| `AppRoot.kt` | `MangaBrowseViewModel.Factory(repository, appContext)` |
| `res/values/strings.xml`, `res/values-en/strings.xml` | 25 yeni `manga_discover_*` anahtarı |
| `app/src/test/.../MangaTitleVariantBuilderTest.kt` | **YENİ** — 12 test |
| `app/src/test/.../MangaMultiTitleSearchTest.kt` | **YENİ** — sıralı arama/eşleme testleri |

---

## ✅ Doğrulama
- `python3 scripts/check_localization_resource_parity.py` → **in sync (17719 strings, 4 arrays)**.
- Tüm değişen `.kt` dosyaları: parantez/blok denge kontrolü temiz (yorum/string ayıklanmış).
- Varyant sıralaması, saf mantığın birebir Python simülasyonuyla test beklentilerine karşı
  doğrulandı: `LOCALIZED` → `[çevrilmiş, sorgu, orijinal]`, `ORIGINAL` → `[orijinal, sorgu, çevrilmiş]`.
- ⏳ **Derleme/`./gradlew test` çalıştırılamadı** — ortamda JDK/Gradle/kotlinc/Android SDK yok.
  İlk derlemede özellikle şunlar izlenmeli: `TextFieldDefaults.colors(...)` parametre adları,
  `MangaCard`/`SkeletonCard`/`MangaCardShimmer(compact)` çağrıları, `MangaSource.pkgName`
  varsayılanı (test `FakeSource` override ediyor).

## ⚠️ Kalan riskler / notlar
- AniList veya çeviri servisi erişilemezse plan yalnızca sorgunun kendisine düşer (tasarım gereği
  arama bloklanmaz); bu durumda Türkçe eklenti için sonuç yine boş olabilir — diyalog denenen
  adları göstererek bunu görünür kılar.
- Çeviri hedefi eklenti çoğunluğuna göre seçilir; kullanıcı hiç TR eklentisi kurmadıysa hedef
  cihaz dili olur (İngilizce eklentiler için doğru davranış).
- Popüler “Tümü” görünümü ilk 3 kaynağı birleştirir (`POPULAR_SOURCE_LIMIT`) — ağ maliyeti ile
  çeşitlilik dengesi; istenirse artırılabilir.
