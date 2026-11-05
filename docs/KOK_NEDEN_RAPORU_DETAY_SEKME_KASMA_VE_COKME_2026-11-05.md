# KÖK NEDEN RAPORU — Detay Sayfası Sekme Kasma & Çökme
**Tarih:** 2026-11-05
**Kapsam:** Tüm kaynak detay sayfaları (AniList / Bangumi / MAL / Kitsu / Shikimori / Simkl / TMDB) — **Resimler ↔ Videolar** sekmeleri arasında yatay kaydırma ve dikey kaydırma
**İncelenen raporlar:** Google Drive / `1ZO3A3nabx_1X4j8ipMesd_KsLlED-v17` (7 dosya)

---

## 0. TL;DR — 3 satırda özet

1. **Kasma:** Sekme kaydırırken `pagerState.currentPageOffsetFraction` **kompozisyonda** okunuyordu. Bu değer her karede değiştiği için **her karede** tüm sekme ağacı (HorizontalPager + görünür sekme içeriği) yeniden kompoze ediliyordu. 245 görselli Resimler sekmesi açıkken kare süresi **1263 ms**'ye çıkıyordu (cihaz logu: `PerfMonitor doFrame time=1263ms`). → *Yükseklik artık yalnızca layout fazında okunuyor; kaydırırken kompozisyon hiç çalışmıyor.*
2. **Kasma #2:** Galeri kartında her thumbnail için `items.indexOf(item)` çağrılıyordu → 245 görselde O(n²) ≈ 30–60 bin nesne karşılaştırması, her kompozisyonda ana thread'de. → *Öğeler bir kez indeksleniyor.*
3. **Çökme:** `NullPointerException: Parameter specified as non-null is null: MediaVideoItem.<init>, parameter alternateUrls` — Videolar sekmesi kompoze olurken `KitsugiMediaVideoAggregator.seedFromDetail` null bir listeyi non-null parametreye geçiriyor. Kalıcı çözüm §3'te; **bu repoda o sınıf yok** (bkz. §9 — sürüm farkı).

---

## 1. İncelenen raporlar ve her birinin söylediği

| Dosya | Boyut | Kritik bulgu |
|---|---|---|
| `device_info (23).txt` | 277 B | **Xiaomi 23043RP34C (pipa), Android 13 (API 33), arm64-v8a**, uygulama **v2.4.426-beta.29897246 (release)** |
| `Kitsugi_Crash_Report (22).txt` | 69 KB | Çökme anı: `2026-11-05 03:10:23.547`, thread **main**, uptime 1718 s, bellek **118 MB / 512 MB**, son ekran **"Galeri: Date A Live (231 görsel)"**, üçüncü parti thread'lerde `image.tmdb.org` / `assets.fanart.tv` istekleri |
| `live_logcat_report (22).txt` | 66 KB | **`E/ComposeInternal: Error was captured in composition`** — aynı NPE **iki kez** (03:10:23.529 ve .532) tekrar denendi, sonra süreç öldü; ardından crash-reporter process'i (`animelist:crash`, PID 10861) ayağa kalktı |
| `logcat_at_crash (18).txt` | 96 KB | Çökmeden **10 saniye önce** (03:10:13) galeri gezinmesi: `sayfa 16/79 → 17/79`; aynı anda `assets.fanart.tv` istekleri **`IOException: Canceled`** (iptal edilen görsel yüklemeleri) ve `image.tmdb.org` için **`EHOSTUNREACH`** (IPv6 rotası yok, 25 sn sonra düşüyor) |
| `crash_history (21).txt` | 709 KB | Kare süresi kanıtları: `PerfMonitor doFrame: time=1236ms`, `time=1263ms`, `time=1598ms` (`longMsg ... c=kotlinx.coroutines.internal.DispatchedContinuation`), `time=564ms`, `time=394ms`; ayrıca `Slow Operation: Activity ... onStop took 418ms` |
| `kitsugi_debug (20).log` | 224 KB | Oynatıcı/`PlayerLoadingDiag` kayıtları — bu çökmeyle ilgisiz; `403`/`9001` kaynak hataları (ayrı konu) |
| `kitsugi_runtime (7).log` | 2.3 MB | Tam çalışma logu; yukarıdaki iki dosyadaki çökme zinciriyle aynı olay penceresini kapsıyor |

**Zaman çizelgesi (03:10:13 → 03:10:23):**
```
03:10:13  ekran → Galeri: Date A Live (231 görsel)      ← galeri açıldı
03:10:13  galeri gezinme: sayfa 16/79
03:10:14  galeri gezinme: sayfa 17/79                   ← parmak hâlâ kaydırıyor
03:10:13  fanart.tv: IOException: Canceled (x N)        ← iptal edilen görseller
03:10:23  ComposeInternal: Error was captured in composition (NPE)  x2
03:10:23  main thread öldü → crash reporter process'i başladı
```
Yani: **galeri açıkken alttaki detay sayfası yeniden kompoze oldu** (galerinin `collectAsState`/`LaunchedEffect` trafiği + pager durumu), bu sırada **Videolar sekmesi** sayfası yeniden kompoze edildi ve `seedFromDetail` içinde NPE fırladı. Compose bu hatayı iki kez yakalayıp yeniden denedi, üçüncüsünde süreç düştü.

---

## 2. Kök neden #1 — Sekme kaydırırken her karede TAM yeniden kompozisyon

### Kanıt
`crash_history (21).txt`:
```
W Looper: PerfMonitor doFrame : time=1263ms vsyncFrame=0 latency=1ms ...
W Looper: PerfMonitor longMsg : seq=22267 plan=23:16:58.544 late=1ms wall=1263ms running=1224ms
```
60 fps için kare bütçesi 16.6 ms. **1263 ms = 76 kare birden kaçırıldı.** "running=1224 ms" → iş **ana thread'de** (kompozisyon/ölçüm), IO'da değil.

### Neden oluyordu (eski kod — `DetailPageScaffold.kt`, dikey düzen)
```kotlin
item(key = "content") {
    val currentPage = pagerState.currentPage                          // ⚠ state okuma
    val currentPageOffset = pagerState.currentPageOffsetFraction       // ⚠ HER KAREDE değişir
    ...
    val interpolatedHeightDp = remember(currentHeightPx, targetHeightPx, currentPageOffset) { ... }
    HorizontalPager(
        modifier = Modifier.fillMaxWidth().layout { measurable, constraints ->
            ... interpolatedHeightDp ...                               // ⚠ her karede yeni lambda
        }
    ) { page -> ... pageContent(page) ... }
}
```
`currentPageOffsetFraction` kompozisyonda okunduğu için snapshot sistemi şunu yapıyordu:

1. parmak her hareket ettiğinde `item(key="content")` kapsamı **geçersiz**,
2. kapsam yeniden kompoze → `HorizontalPager` yeniden çağrılır,
3. `Modifier.layout { ... }` ve sayfa içerik lambda'sı **her karede yeni örnek** → pager "item content değişti" der,
4. görünür sekme (Resimler: 245 görsel / ~15 yatay ray; Videolar: oynatıcı kartları) **her karede** yeniden kompoze + sınırsız (`Constraints.Infinity`) yükseklikle yeniden ölçülür.

Aynı desen 3 yerde daha vardı: `CharacterDetailPage.kt`, `StaffDetailPage.kt` (Karakterler/Ekip detayları) ve `KitsugiImageGalleryDialog.kt` → `GalleryImagePage` (tam ekran galeride sayfa geçişi). Profil ekranlarında (AniList/Kitsu/MAL/Shikimori/Simkl/KitsugiUserProfile) da aynı desen var — bkz. §7 "sıradaki adım".

### Çözüm (uygulandı)
Yükseklik bir `derivedStateOf` içinde tutuluyor ve **yalnızca `Modifier.layout` içinde** okunuyor. Compose snapshot sistemi faz-eşleşmeli çalışır: **layout fazındaki okuma sadece layout'u geçersiz kılar, kompozisyonu atlar.**

```kotlin
// ui/utils/DetailPagerHeightPolicy.kt (yeni, tek kaynak)
val pagerHeightPxState = rememberDetailPagerContentHeightPx(pagerState, pageHeights) // derivedStateOf
...
Modifier.fillMaxWidth().detailPagerHeight(pagerHeightPxState, minPagerHeightPx) // okuma layout içinde
```

Ayrıca `pagerState.currentPage` de kompozisyondan çıkarıldı:
- `DetailPageScaffold` / `ApiResultDetailPage` / `MediaEntryDetailPage`: `val selectedTab = pagerState.currentPage` **kaldırıldı**; sekme değişimi `snapshotFlow { pagerState.currentPage }.distinctUntilChanged()` ile dinleniyor,
- sekme çipleri seçim durumunu **kendi item kapsamında** okuyor (`pagerState.currentPage == index`) → yalnızca görünür çipler kompoze olur,
- `page == 2` için gereksiz `selectedTab == DETAIL_VIDEO_PAGE_INDEX` kontrolü kaldırıldı (hem gereksiz state okuması hem de kaydırma sırasında bir kare boş içerik demekti).

**Beklenen kazanç:** kaydırma sırasında kompozisyon **0**, yalnızca layout. Kare süresi hedefi ≤ 16 ms.

---

## 3. Kök neden #2 — ÇÖKME: `MediaVideoItem.<init>`, `alternateUrls` null

### Tam stack (kısaltılmamış, `live_logcat_report (22).txt`)
```
E/ComposeInternal: Error was captured in composition.
java.lang.NullPointerException: Parameter specified as non-null is null:
    method com.kitsugi.animelist.data.remote.MediaVideoItem.<init>, parameter alternateUrls
    at com.kitsugi.animelist.data.remote.MediaVideoItem.<init>(Unknown Source:28)
    at com.kitsugi.animelist.data.remote.MediaVideoItem.<init>(SourceFile:15)
    at com.kitsugi.animelist.data.remote.KitsugiMediaVideoAggregator.seedFromDetail(Unknown Source:233)
    at com.kitsugi.animelist.data.remote.KitsugiMediaVideoAggregator.seedFromDetail$default(Unknown Source:5)
    at r7.xb.e(Unknown Source:230)          ← @Composable (Videolar sekmesi)
    at r7.a1.k(Unknown Source:145)
    at androidx.compose.foundation.pager.PagerLazyLayoutItemProvider.Item$lambda$0
    at androidx.compose.runtime.Recomposer.performRecompose
    ...
    at android.view.Choreographer.doFrame
```
`PagerLazyLayoutItemProvider.Item` → hata **pager'ın bir sayfası kompoze olurken** atılıyor. `r7.xb.e` obfuscate edilmiş bir `@Composable`; `r7.a1.k` de onun alt composable'ı. Yani **Videolar sekmesi** (veya içindeki video grubu) sayfası.

### Neden null geliyor?
`MediaVideoItem(...)` Kotlin `data class`; `alternateUrls` **non-null `List<String>`** olarak tanımlı ve **varsayılan değeri yok**. Kotlin, null kontrolünü **yalnızca** doğrudan Kotlin çağrılarında derleyici tarafından elenir; şu durumlarda kontrol **çalışır** ve NPE fırlatır:

1. değer **platform tipi** (Java/JSON/`JSONObject.optJSONArray` çıktısı) üzerinden geliyorsa (Kotlin `List<String>!` kabul eder, `null` geçebilir),
2. `seedFromDetail` içinde **varsayılan parametre** kullanılıyorsa (`$default` sınıfı — stack'te `seedFromDetail$default` görünüyor) ve o varsayılan `null`,
3. nesne **Gson/reflection** ile oluşturulup daha sonra `copy()`/constructor ile yeniden kuruluyorsa.

`seedFromDetail$default` çağrısı tek başına bunu kanıtlıyor: `seedFromDetail`'in bir parametresi **verilmemiş** (default kullanılmış) ve bu zincirde `MediaVideoItem`'a `alternateUrls = null` ulaşmış.

### Kalıcı düzeltme (yeni kaynak ağacında uygulanacak — birebir)

**a) Modeli null'a dayanıklı yap** (`MediaVideoItem` / `KitsugiMediaVideoAggregator.kt`):
```kotlin
// ÖNCE
data class MediaVideoItem(
    val url: String,
    val alternateUrls: List<String>,      // ⚠ null gelebiliyor
    ...
)

// SONRA — önerilen: nullable + okurken normalize
data class MediaVideoItem(
    val url: String,
    val alternateUrls: List<String>? = emptyList(),
    ...
) {
    /** URL listesi: null asla dışarı sızmaz. */
    val safeAlternateUrls: List<String> get() = alternateUrls.orEmpty().filter { it.isNotBlank() }
}
```
Alternatif (daha az dokunuş): tipi `List<String>` bırakıp **her kurulum yerinde** `alternateUrls = candidateAltUrls.orEmpty()` yaz. Ancak platform tipi geliyorsa yine kaçak olur — bu yüzden **nullable + `orEmpty()`** güvenli olan.

**b) `seedFromDetail` içindeki kurulumları normalize et:**
```kotlin
alternateUrls = (item.alternateUrls ?: item.url.takeIf { it.isNotBlank() }?.let(::listOf) ?: emptyList())
```

**c) Emniyet kemeri (asıl koruma):** `seedFromDetail`'i tek bir girişte topla:
```kotlin
fun seedFromDetail(detail: KitsugiMediaDetail?, mediaTitle: String = ""): List<MediaVideoItem> {
    if (detail == null) return emptyList()
    return runCatching { buildItems(detail, mediaTitle) }
        .getOrElse { t ->
            KitsugiLog.w("MediaVideoAggregator", "seedFromDetail düştü: ${t.message}")
            emptyList()          // ← sekme boş görünür, uygulama ölmez
        }
}
```
> Not: Compose içinde **fırlatılan** istisnayı `runCatching` ile yakalayıp yutmak Composition'ı "zehirler"; bu yüzden yakalama **veri katmanında** (composable dışında) yapılmalı — yukarıdaki gibi.

**d) Neden Videolar sekmesi galeri açıkken kompoze oldu?** Detay ekranı galerinin altında canlı kalıyor; galeri `pagerState`/`collectAsState` trafiği detay ekranını yeniden kompoze ediyor, `pageContent` lambda'sı yenileniyor ve **o an hangi sekme seçiliyse** (ya da swipe artığı) o sayfa yeniden kompoze ediliyor. §2'deki düzeltme bu tetiklemeyi büyük ölçüde azaltır; kalan riski (c) maddesi tamamen kapatır.

---

## 4. Kök neden #3 — Galeri kartında O(n²) iş

`EntryDetailTabContents.kt` → `DetailGalleryCard`:
```kotlin
itemsIndexed(sourceItems) { _, item ->
    val mainIndex = items.indexOf(item)   // ⚠ her thumbnail için 245 elemanlı tarama
```
245 görselde **≈30.000 (en kötü 60.000)** `equals()` karşılaştırması; üstelik galeri 5 kaynaktan **parça parça** geldiği için liste her güncellendiğinde tekrar. `GalleryItem` data class'ı 3–4 alanlı olduğundan ucuz değil.

**Çözüm (uygulandı):** öğeler bir kez `withIndex()` ile gruplanıyor, indeks öğeyle taşınıyor; `LazyRow` öğelerine `key` + `contentType` eklendi (geri dönüşüm/skip iyileşir), ray kaydırma durumu kaynak grubuna bağlandı.

---

## 5. Kök neden #4 — Galeri (tam ekran) sayfa geçişinde SubcomposeLayout her karede

`KitsugiImageGalleryDialog.kt` → `GalleryImagePage`:
```kotlin
val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)  // ⚠ kompozisyon
val dominoScale = ...; val dominoAlpha = ...; val dominoTranslationX = ...
```
Bu değerler **kompozisyonda** okunduğu için, galeride parmak kaydırırken sayfa **her karede** yeniden kompoze ediliyordu — üstelik sayfa gövdesi `BoxWithConstraints` (**SubcomposeLayout**) içerdiği için her karede **alt kompozisyon** da yeniden kuruluyordu. `sayfa 16/79 → 17/79` gezinmesi sırasındaki takılmaların kaynağı bu.

**Çözüm (uygulandı):** domino efekti `graphicsLayer { ... }` bloğuna taşındı — okuma **çizim fazında**; kaydırırken yalnızca katman özellikleri değişiyor, kompozisyon/subkompozisyon çalışmıyor.

---

## 6. Bu repoda yapılan değişiklikler (dosya dosya)

| Dosya | Değişiklik |
|---|---|
| **`ui/utils/DetailPagerHeightPolicy.kt`** *(yeni)* | `rememberDetailPagerContentHeightPx()` (derivedStateOf), `rememberDetailPagerMinHeightPx()`, `Modifier.detailPagerHeight()` — yükseklik interpolasyonunun tek kaynağı, layout-fazı okuması |
| `ui/screens/detail/DetailPageScaffold.kt` | Portre pager yüksekliği layout fazına taşındı; `selectedTab` kompozisyon okuması kaldırıldı (`snapshotFlow`); çipler kendi kapsamında okuyor; çip `key`'leri; `currentPageOffsetFraction` artık kompozisyonda okunmuyor |
| `ui/screens/detail/ApiResultDetailPage.kt` | `val selectedTab = pagerState.currentPage` kaldırıldı; `loadTab` `snapshotFlow` ile; Videolar sayfasındaki gereksiz `selectedTab` guard'ı kaldırıldı |
| `ui/screens/detail/MediaEntryDetailPage.kt` | Aynı üç düzeltme |
| `ui/screens/detail/CharacterDetailPage.kt` / `StaffDetailPage.kt` | Aynı pager yükseklik düzeltmesi (ortak yardımcıya bağlandı) |
| `ui/screens/detail/EntryDetailTabContents.kt` | `items.indexOf(item)` (O(n²)) kaldırıldı → indeksli gruplama; `LazyRow` `key` + `contentType`; ray durumu kaynak grubuna bağlı |
| `ui/components/KitsugiImageGalleryDialog.kt` | Galeri sayfası domino efekti `graphicsLayer` (çizim fazı) |

Hiçbir özellik kaldırılmadı: sekme sırası, sekme çubuğunun otomatik ortalanması, dinamik pager yüksekliği, sticky başlık, galeri kategorileri, "+18" bulanıklık, TV D-pad davranışı aynı; yalnızca **nerede hesaplandığı** değişti.

---

## 7. Doğrulama planı (cihazda)

1. **Sekme kaydırma:** Date A Live (231 görsel) → Resimler ↔ Videolar arası 10 kez ileri-geri kaydır. `adb shell dumpsys gfxinfo com.kitsugi.animelist reset` + tekrar → `Janky frames` **< %5**, `90th percentile < 16 ms` beklenir.
2. **Dikey kaydırma:** Resimler sekmesinde en alta in, hızlı fling; `PerfMonitor doFrame` satırı **> 100 ms olmamalı**.
3. **Çökme:** Videolar sekmesi + galeri aç/kapa + hızlı sekme değişimi 20 tekrar → `E/ComposeInternal: Error was captured in composition` **hiç görünmemeli** (§3(a)-(c) uygulandıktan sonra).
4. **Bellek:** 10 dakika sekme/rage gezinme sonrası `118 MB` civarında kalmalı (artış trendi olmamalı).
5. **Regresyon:** TV (D-pad) modunda sekme geçişi, yatay/dikey dönüşüm, pull-to-refresh, galeri kategorisi filtresi, "+18" bulanıklık.

### Ölçüm komutu (kısa)
```bash
adb shell dumpsys gfxinfo com.kitsugi.animelist framestats | head -40
adb logcat -s KitsugiLog ComposeInternal PerfMonitor
```

---

## 8. Sıradaki adım (bu turda kapsam dışı bırakıldı)

Aynı "kompozisyonda `currentPageOffsetFraction`" deseni **6 profil ekranında** da duruyor:
`AniListProfileContent.kt:308`, `KitsuProfileContent.kt:272`, `KitsugiUserProfileScreen.kt:325`, `MalProfileContent.kt:338`, `ShikimoriProfileContent.kt:261`, `SimklProfileContent.kt:238`.
Bunlar aynı yeni yardımcıyla (`rememberDetailPagerContentHeightPx` + `Modifier.detailPagerHeight`) 10 dakikada düzeltilebilir; istenirse bir sonraki turda yapılır.
Ayrıca **daha büyük mimari iyileştirme:** sekme içeriği şu an tek bir `LazyColumn` öğesi içinde ve **sınırsız yükseklikle** ölçüldüğü için sekmeye girildiğinde tüm galeri satırları aynı anda kompoze oluyor (geri dönüşüm yok). Kalıcı çözüm, her sekmeyi kendi `LazyColumn`'ına alıp pager'ı ekran yüksekliğine sabitlemektir; bu davranış değiştiren bir refactor olduğu için bu turda yapılmadı (özellik bozmama kuralı).

---

## 9. ⚠️ Sürüm uyuşmazlığı — aksiyon gerekiyor

| | Değer |
|---|---|
| Bu repodaki kaynak (`app/build.gradle.kts`) | **2.4.340** |
| En son release notes | `release_notes_v2.4.340.md` |
| Çökme raporundaki cihaz sürümü | **2.4.426-beta.29897246** |

Çökme stack'indeki `KitsugiMediaVideoAggregator` ve `MediaVideoItem` sınıfları **bu repoda yok** (`git grep` ile tüm ağaçta arandı: 0 eşleşme — `app/src`, `repo/`, `app/libs/*.aar` dahil). Yani bu çalışma alanı, cihazdaki sürümden **~86 sürüm geride** bir ayna.

Bunun pratik sonucu:
- §2, §4, §5'teki **performans düzeltmeleri bu ağaçta uygulandı ve aynı dosyalar 2.4.426'da da var** (detay iskeleti, galeri kartı, galeri diyaloğu) → yeni ağaca taşındığında aynı kazancı verir.
- §3'teki **çökme düzeltmesi**, `KitsugiMediaVideoAggregator.kt` dosyası bu depoya gelmeden birebir uygulanamaz. Dosyanın bulunduğu güncel ağaç push edilirse (veya dosya paylaşılırsa) aynı turda yamalayabilirim.

**İstenen:** `app/build.gradle.kts` sürümü ile cihaz sürümü eşleşecek şekilde güncel kaynak ağacını bu depoya aktarın; kalan tek satırı (§3a) ben uygularım.

---

# İKİNCİ TUR — TÜM SEKME/PAGER ALANLARININ TAM DENETİMİ
**Tarih:** 2026-11-05 (ek tur)
**Soru:** "Bu hataları kapsayan başka sekme/pager alanı kaldı mı? Kesin ve net olsun."

## 10. Denetim yöntemi

Ağaçtaki **26 `HorizontalPager`/`VerticalPager` kurulumunun tamamı** tek tek tarandı:

1. `currentPageOffsetFraction` (kaydırma boyunca **her karede** değişen tek değer) kompozisyonda okunuyor mu?
2. Sayfa yükseklik interpolasyonu + `Constraints.Infinity` ölçümü var mı?
3. Sonsuz yükseklikle ölçülen bir pager **sayfasının içinde** dikey kaydırılabilir bileşen (`LazyColumn`, `LazyVerticalGrid`, `verticalScroll`) var mı? → **çökme** sınıfı
4. Lazy item içinde `indexOf` (O(n²)) var mı?
5. `beyondViewportPageCount` ile kaç sayfa bellekte?

Bu 5 kuralın tamamı `scripts/kitsugi_pager_perf_guard.py` dosyasına döküldü (aşağıda §12).

## 11. Alan alan sonuç tablosu

| # | Sekme/pager alanı | Dosya | Durum |
|---|---|---|---|
| 1 | API kaynaklı detay (AniList/Bangumi/MAL/Kitsu/Shikimori/Simkl/TMDB) | `DetailPageScaffold.kt` | ✅ **Düzeltildi** (1. tur) — her karede tam kompozisyon vardı |
| 2 | Liste tabanlı detay | `MediaEntryDetailPage.kt`, `ApiResultDetailPage.kt` | ✅ **Düzeltildi** (1. tur) — `selectedTab` okuması + gereksiz guard |
| 3 | Karakter detayı | `CharacterDetailPage.kt` | ✅ **Düzeltildi** (1. tur) |
| 4 | Ekip (staff) detayı | `StaffDetailPage.kt` | ✅ **Düzeltildi** (1. tur) |
| 5 | Tam ekran galeri — sayfa pager'ı | `KitsugiImageGalleryDialog.kt` | ✅ **Düzeltildi** (1. tur, çizim fazına taşındı) |
| 6 | Tam ekran galeri — "diğer görseller" listesi | `KitsugiImageGalleryDialog.kt` | ✅ **Düzeltildi** (2. tur) — `filteredItems.indexOf()` O(n·m) kaldırıldı |
| 7 | Profil — AniList | `AniListProfileContent.kt` | ✅ **Düzeltildi** (2. tur) |
| 8 | Profil — MAL | `MalProfileContent.kt` | ✅ **Düzeltildi** (2. tur) |
| 9 | Profil — Kitsu | `KitsuProfileContent.kt` | ✅ **Düzeltildi** (2. tur) |
| 10 | Profil — Shikimori | `ShikimoriProfileContent.kt` | ✅ **Düzeltildi** (2. tur) |
| 11 | Profil — Simkl | `SimklProfileContent.kt` | ✅ **Düzeltildi** (2. tur) |
| 12 | Profil — Kitsugi kullanıcı | `KitsugiUserProfileScreen.kt` | ✅ **Düzeltildi** (2. tur) |
| 13 | Ana sayfa hero şeridi | `KitsugiHeroSection.kt` | ✅ Zaten doğru — `heroPageVisibility` yalnızca `drawBehind` / `graphicsLayer` içinde okunuyor |
| 14 | Listem sekmeleri | `MyListScreen.kt` | ✅ Temiz — offset okuması yok, `beyondViewportPageCount` varsayılan (0) |
| 15 | Bildirimler (platform + AniList pager) | `KitsugiNotificationsScreen.kt` | ✅ Temiz |
| 16 | İndirilenler sekmeleri | `DownloadsScreen.kt` | ✅ Temiz |
| 17 | Yayın takvimi (gün sekmeleri) | `KitsugiAiringCalendarScreen.kt` | ✅ Temiz |
| 18 | Ayarlar sekmeleri — Sistem | `KitsugiSystemSettingsDialog.kt` | ✅ Temiz |
| 19 | Ayarlar sekmeleri — Tercihler | `KitsugiPreferencesSettingsDialog.kt` | ✅ Temiz |
| 20 | Ayarlar sekmeleri — Entegrasyonlar | `KitsugiIntegrationsSettingsDialog.kt` | ✅ Temiz |
| 21 | Ayarlar sekmeleri — Eklentiler | `KitsugiAddonsSettingsDialog.kt` | ✅ Temiz |
| 22 | Ayarlar sekmeleri — Oynatıcı | `KitsugiPlayerSettingsDialog.kt` | ✅ Temiz |
| 23 | Hesap alt sayfaları | `AccountSettingsSubPages.kt` | ✅ Temiz |
| 24 | Platform hata kataloğu | `PlatformErrorCatalogScreen.kt` | ✅ Temiz |
| 25 | Altyazı ayar paneli göstergesi | `SubtitleSettingsPanel.kt` | ✅ Temiz (yalnızca gösterge çizimi) |
| 26 | Manga okuyucu pager'ları | `MangaPagerReader.kt`, `MangaReaderScreen.kt`, `TvMangaReaderScreen.kt` | ✅ Temiz — `beyondViewportPageCount = 2` bilinçli ön-yükleme; offset kompozisyonda okunmuyor |

**Sonuç: per-frame kompozisyon üreten tek bir nokta kalmadı.** `scripts/kitsugi_pager_perf_guard.py` çıktısı: `0 hata, 0 uyarı, 1 bilgi`.

### Ek doğrulama — çökme sınıfı ("infinity + dikey kaydırılabilir")
`Constraints.Infinity` ile ölçülen tüm sayfaların içerik ağaçları tarandı: hiçbirinde `LazyColumn` / `LazyVerticalGrid` / `verticalScroll` yok.
Tek istisna zaten bilinçli olarak çözülmüş durumda: **Fandom wiki okuyucusu** kendi `Dialog` penceresinde çiziliyor (`FandomWikiReader.kt` içindeki "KRİTİK (çökme düzeltmesi)" notu) — dialog penceresi her zaman sınırlı kısıt verir.

### Bilerek dokunulmayanlar (kasma sebebi DEĞİL)
- `pagerState.currentPage` kompozisyonda okunan yerler (sekme vurgusu, `LaunchedEffect` anahtarı, `when (page)`): sayfa başına **1 kez** tetiklenir, kare başına değil. `--strict` ile listelenebilir.
- `TvDetailScreen.kt` tema satırındaki `openings.indexOf(theme)`: liste uzunluğu ≤ ~20, etkisi ölçülemez.
- `AllSourcesExploreContent.kt` içindeki `ExplorePlatform.sources.indexOf(platform)`: sabit uzunlukta enum listesi.

## 12. Regresyon kalkanı — `scripts/kitsugi_pager_perf_guard.py`

Bu hata sınıfı bir daha sessizce geri dönmesin diye otomatik denetleyici eklendi.

```bash
python3 scripts/kitsugi_pager_perf_guard.py            # tüm ağaç
python3 scripts/kitsugi_pager_perf_guard.py --strict   # currentPage okumalarını da listele
```
Çıkış kodu `0` = temiz, `1` = ihlal (CI'a/commit hook'una bağlanabilir).

Denetlediği kurallar:

| Kural | Seviye | Açıklama |
|---|---|---|
| `currentPageOffsetFraction` kompozisyonda | **FAIL** | Her karede tam yeniden kompozisyon (1263 ms'lik karelerin kaynağı) |
| Lazy item içinde `indexOf` | WARN | O(n²) nesne karşılaştırması |
| Anahtarsız döngüde `rememberLazyListState()` | WARN | Kaydırma konumu yanlış satıra atlar |
| `Constraints.Infinity` + dikey kaydırılabilir aynı dosyada | INFO | Sonsuz kısıt çökmesi riski (`IllegalStateException`) |

Onaylı istisnalar dosya başındaki `ALLOW_FILES` listesinde, gerekçesiyle birlikte duruyor.

## 13. Bu turda değişen dosyalar (2. tur)

| Dosya | Değişiklik |
|---|---|
| `ui/screens/profile/AniListProfileContent.kt` | Pager yüksekliği layout fazına taşındı |
| `ui/screens/profile/MalProfileContent.kt` | Aynı |
| `ui/screens/profile/KitsuProfileContent.kt` | Aynı |
| `ui/screens/profile/ShikimoriProfileContent.kt` | Aynı |
| `ui/screens/profile/SimklProfileContent.kt` | Aynı |
| `ui/screens/profile/KitsugiUserProfileScreen.kt` | Aynı |
| `ui/components/KitsugiImageGalleryDialog.kt` | "Diğer görseller" listesindeki `indexOf` O(n²) kaldırıldı, `key` eklendi |
| `scripts/kitsugi_pager_perf_guard.py` *(yeni)* | Kalıcı regresyon denetleyicisi |
