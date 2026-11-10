# KÖK NEDEN RAPORU — LazyVerticalGrid "Sonsuz Yükseklik" Çökmesi (Açılışta Ani Kapanma)

**Tarih:** 2026-10-22
**Cihaz:** POCO 2311DRK48G (duchamp), Android 16 (API 36), arm64-v8a
**Uygulama:** `2.4.314-beta.29874702` (çökme anında) → kullanıcı **2.4.344-beta.29877655**'e güncelledi (12:29)
**Belirti:** "ark telefonunda uygulama çöktü gene" — uygulama açılırken/ilk ekranda aniden kapanıyor, ardından çökme ekranı geliyor.
**Paket:** `com.kitsugi.animelist`

---

## 0. Kısa cevap (TL;DR)

**Çökme:** `java.lang.IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints`

**Kök neden:** Bir `LazyVerticalGrid` (dikey kaydırılabilir ızgara), bir **`LazyColumn`/`LazyRow` item'ının kök düğümü** olarak yerleşiyor. LazyList, item'larını **`maxHeight = ∞`** ile ölçer; Compose ise dikey kaydırılabilir bir bileşeni ∞ yükseklikle ölçmeye izin vermez ve bu istisnayı atar. `Modifier.wrapContentHeight()` bu durumda **∞'ü sınırlamaz** (yalnızca `minHeight=0` yapar), `userScrollEnabled = false` da **kontrolü atlamaz** — ikisi de çökmez.

Stack izi, çöken ızgaranın tam profilini veriyor:

* İç zincir: `FillNode` → `WrapContentNode` → `SimpleGraphicsLayerModifier` → `LazyGrid` ölçü ilkesi (`LazyGrid.kt:194` kontrol noktası).
* Izgarada `LazyLayoutBeyondBoundsProviderModifierNode` **YOK** → ızgara **`userScrollEnabled = false`** ile oluşturulmuş (LazyList kaynağı: beyondBounds yalnızca `userScrollEnabled=true` iken eklenir).
* Dış zincir: `FillNode` → `PaddingNode` → beyondBounds → `LazyList` ölçü ilkesi (`LazyListMeasure.kt:224`) → `AndroidComposeView.dispatchDraw` → yani **çizim (measure) sırasında**, bir LazyColumn item ölçümü içinde patlıyor.

**Koddaki karşılığı:** `GridHomeContent.kt` — "V2-D03 Ana ekran grid görünümü": `LazyVerticalGrid(userScrollEnabled = false, modifier = Modifier.wrapContentHeight())` ile liste içine gömülmeye tasarlanmış ızgara. Aynı çökme sınıfı daha önce iki kez görüldü ve belgelendi:
* `KOK_NEDEN_RAPORU_WIKI_OKUYUCU_VE_GALERI_SINIRSIZ_YUKSEKLIK_2026-10-13.md` (ScrollNode varyantı — wiki okuyucu),
* `ProfileActivityComponents.kt:496-501` — favori genişletme levhasındaki ızgara için birebir aynı istisna yorumlanmış ve `height(sabit)` ile düzeltilmiş ("LazyVerticalGrid dikey olarak SINIRLI yükseklik ister… Infinity max height gelirse 'Vertically scrollable component was measured with an infinity maximum height' çöküşü oluşur").

**Uygulanan düzeltmeler (bu depo, v2.4.340 tabanı):** §5. Cihazdaki **2.4.344** derlemesindeki çağrı noktasına taşınması gereken talimat §6'da (sürüm farkı önemli!).

---

## 1. Okunan dosyalar (Drive klasörü `1bVnlq-UHvIN751UrOjKvPzTe7TbEFNfu`)

| Dosya | Boyut | İçerik | Durum |
|---|---|---|---|
| `Kitsugi_Crash_Report (7).txt` | 9,907 B | **Java çökmesi**: `IllegalStateException` + tam stack + "BAŞLATMA AŞAMASI" bloğu | ✅ tamamı okundu |
| `device_info (9).txt` | 240 B | v2.4.344-beta.29877655 [release], POCO 2311DRK48G, Android 16 (API 36), 8 MB/512 MB boş RAM, saat 2026-10-22 12:32:16 | ✅ tamamı okundu |
| `live_logcat_report (8).txt` | 8,497 B | 12:32 canlı logcat: CS repo 404'leri, 1.364 sn main-thread `SynchronizedLazyImpl` kilit beklemesi, `onTrimMemory(20)`, `:crash` süreci ekranı | ✅ tamamı okundu |
| `crash_history (7).txt` | 2,505,831 B | 294 dilimlik oturum/çökme geçmişi (10-09 → 10-22) + son PROC MAPS dökümü | ⚠️ yapı haritası çıkarıldı; tüm çökme raporu başlıkları ve ölüm kayıtları okundu (bkz. §1.1); MIUI logcat gürültüsü satırları (TurboSchedMonitor/MiResource/MiuiMultiWindowUtils vb., dosyanın büyük kısmı) tarandı |

### 1.1 `crash_history` yapı haritası ve bulunan TÜM kayıtlar

Dosya `=== Eski geçmiş kısaltıldı ===` ile başlıyor; dilimler 0→293. Tarama sonunda bulunan **her rapor başlığı**:

| # | Zaman | Süreç / Sürüm | Kayıt türü | Son ekran | Yaşam / ortam | Ölüm sebebi |
|---|---|---|---|---|---|---|
| 1 | 10-09 | pid 20568 · — | BACKGROUND EXIT | — | arka plan | SEBEP BULUNAMADI (sessiz SIGKILL) |
| 2 | 10-12 → 10-15 (dilim 50/150 arası) | — | (eski geçmiş log gürültüsü) | — | — | rapor başlığı yok |
| 3 | **10-22 10:14:22** | pid 27544 · **2.4.314** | **UNCLEAN EXIT** | **`baslangic`** | oturum 08:25:27→08:25:32 (5 sn), **ÖN PLANDA → gerçek çökme** | SEBEP BULUNAMADI* |
| 4 | **10-22 12:28:12** | pid 23878 · **2.4.314** | **UNCLEAN EXIT** | **`baslangic`** | oturum 10:14:22→10:14:22 (0 sn), **ÖN PLANDA → gerçek çökme** | SEBEP BULUNAMADI* |
| 5 | **10-22 12:29:22** | pid 7290 · **2.4.314** | BACKGROUND EXIT | `Tab(tab=Explore)` | oturum 12:28:12→12:28:42 (30 sn), arka plan | SEBEP BULUNAMADI (sessiz SIGKILL) |
| 6 | **10-22 12:31:58** | pid 15352 · **2.4.344** | **UNCLEAN EXIT** | `Tab(tab=Profile)` (anlık görüntü: `Tab(tab=MyList)`) | oturum 12:29:22→12:31:52 (150 sn), arka plan | SEBEP BULUNAMADI (sessiz SIGKILL — sistem/arka plan kısıtlaması; cihazda 8 MB boş RAM) |

\* "SEBEP BULUNAMADI" yalnızca unclean-analizcisinin logcat penceresinde FATAL satırı bulamaması demektir (log arabelleği bu cihazda sürekli MIUI gürültüsüyle dönüyor). Kayıtlar **"ÖN PLANDAYDI → gerçek çökme"** diyor: bunlar sessiz değil, gerçek çökmeler.

**Anahtar gözlem:** #3 ve #4 **açılışta** (`SON EKRAN: baslangic`, eylem izi yok — ilk kare çizilmeden) ve **ön planda** ölüyor; ikisi de Java istisnası dosyasıyla (`Kitsugi_Crash_Report (7).txt`) eşleşiyor. 12:32:06'da açılan çökme ekranı (`:crash` pid 21474) ise raporu **dışa aktarmak için** açılmış (kullanıcı "hataları kontrol et" için dosyaları paylaşmış); `TransportRuntime … bytes written` satırı dışa aktarmayı doğruluyor.

### 1.2 Sürüm/oturum zaman çizelgesi (10-22)

```
08:25:27  pid 27544 (v2.4.314) açılış → 08:25:32 AÇILIŞTA ÇÖKME (baslangic, ön plan)
10:14:22  pid 23878 (v2.4.314) açılış → anında ÇÖKME (baslangic, ön plan)
12:28:12  pid 7290  (v2.4.314) açılış; APK yolu ...mZB52B7aOOX... → 12:28:18 Explore
12:28:42  pid 7290 sessiz ölüm (arka plan)
12:29:22  pid 15352 (v2.4.344 — UYGULAMA GÜNCELLENDİ, APK yolu ...2FdgsYf3dMlq...)
          kullanıcı 150 sn aktif gezinir (Explore→ApiResultDetail→MyList→Profile→Settings→…)
12:31:52  pid 15352 sessiz ölüm (arka plan; LMKD olası — 8 MB boş RAM)
~12:32:00 pid 20182 açılış (eklenti yüklemesi logları)
12:32:06  :crash (pid 21474) → KitsugiCrashActivity açıldı, rapor dışa aktarıldı (12:32:16)
```

> Rapor dosyasındaki `Zaman: 2026-07-21 10:55:02` alanı **güvenilmez** (eski yazıcının tarih biçeği hatası veya saha saati); gerçek zaman çizelgesi logcat damgalarından yukarıdaki gibidir.

---

## 2. Çökme imzası ve stack → kaynak eşlemesi

```
java.lang.IllegalStateException: Vertically scrollable component was measured with
an infinity maximum height constraints, which is disallowed. One of the common
reasons is nesting layouts like LazyColumn and Column(Modifier.verticalScroll()). ...

  at androidx.compose.foundation.internal.InlineClassHelperKt.throwIllegalStateException
  at androidx.compose.foundation.CheckScrollableContainerConstraintsKt.checkScrollableContainerConstraints-K40F9xA
  at androidx.compose.foundation.lazy.grid.LazyGridKt$rememberLazyGridMeasurePolicy$1$1.measure-0kLqBqw(LazyGrid.kt:194)
      ↑ ÇÖKEN: bir LazyVerticalGrid'in ölçüm ilkesi — maxHeight=∞
  at androidx.compose.ui.layout.LayoutNodeSubcompositionsState$createMeasurePolicy$1.measure(SubcomposeLayout.kt:955)
  at androidx.compose.ui.node.InnerNodeCoordinator.measure
  at androidx.compose.ui.graphics.SimpleGraphicsLayerModifier.measure        ← ızgaranın grafik katmanı
  at androidx.compose.foundation.layout.WrapContentNode.measure(Size.kt:1029) ← wrapContentHeight()/wrapContentSize()
  at androidx.compose.foundation.layout.FillNode.measure(Size.kt:721)         ← fillMaxWidth()/fillMaxSize()
  at androidx.compose.ui.node.MeasurePassDelegate$performMeasureBlock$1.invoke ← ITEM ölçümü
  at androidx.compose.foundation.lazy.layout.LazyLayoutMeasuredItemProvider.getPlaceables
  at androidx.compose.foundation.lazy.LazyListMeasuredItemProvider.getAndMeasure
  at androidx.compose.foundation.lazy.LazyListMeasureKt.measureLazyList(LazyListMeasure.kt:224)
  at androidx.compose.foundation.lazy.LazyListKt$rememberLazyListMeasurePolicy$1$1.measure(LazyList.kt:353)
      ↑ DIŞ KAP: bir LazyColumn/LazyRow — item'larını maxHeight=∞ ile ölçüyor
  at androidx.compose.foundation.layout.PaddingNode.measure(Padding.kt:449)   ← dış liste .padding(...)
  at androidx.compose.foundation.layout.FillNode.measure(Size.kt:721)         ← dış liste fillMax*()
  ...
  at androidx.compose.ui.platform.AndroidComposeView.dispatchDraw             ← ilk çizim (measure+layout) sırasında
```

**Stack'in söyledikleri (kanıtlı):**

1. Çöken düğüm **kesin olarak bir `LazyVerticalGrid`** (`LazyGridKt$rememberLazyGridMeasurePolicy`, dikey kontrol).
2. Doğrudan üstü bir **LazyList item ölçümü** (`getAndMeasure`/`measureLazyList`) — yani ızgara `LazyColumn.item { ... }` içeriğinin **kök layout düğümü**. Arada Column/Box ölçü ilkesi yok (zincirde yalnızca modifier düğümleri var).
3. Izgaranın kendi modifier zinciri: `fillMax*` → `wrapContent*` → grafik katmanı. `wrapContentHeight()`, gelen `maxHeight=∞`'i **değiştirmez** (kaynak: `Size.kt` — "Allow the content to measure at its desired height without regard for the incoming measurement **minimum** height constraint"; yalnızca `unbounded=true` iken maksimum da yok sayılır, o da ayrı bir hataya yol açar).
4. Izgara zincirinde **`LazyLayoutBeyondBoundsProviderModifierNode` yok**; dış listede var. AndroidX `LazyList.kt`/`LazyGrid.kt` kaynağı: beyondBounds modifier'ı yalnızca `userScrollEnabled` true iken eklenir → **çöken ızgara `userScrollEnabled = false`**.
5. Dış listenin `fillMax* + padding(...)` zinciri, "Listem" sayfasının LazyColumn'una (`MyListComponents.kt`: `Modifier.fillMaxSize().padding(horizontal = ...)`) birebir uyar; `KitsugiSession` anlık görüntüsü de `Tab(tab=MyList)`. (Not: dış zincir tek başına kesin kanıt değil; `fillMax*.padding()` kullanan birkaç LazyColumn daha var.)

**Compose davranışı (kanıt):** LazyList item kısıtları `maxHeight = Constraints.Infinity`'dir; bir item içine doğrudan dikey kaydırılabilir (LazyGrid/LazyColumn/`Column(verticalScroll)`) konulması klasik çökmedir. `heightIn(max=…)`/`height(…)` gibi **sonlu** kısıt veren modifier'lar sorunu çözer; `wrapContentHeight()` çözmez.

---

## 3. Depodaki birebir kalıp

`app/src/main/java/com/kitsugi/animelist/ui/screens/home/components/GridHomeContent.kt` (V2-D03 "Ana ekran grid görünümü"):

```kotlin
LazyVerticalGrid(
    columns = GridCells.Fixed(columns),
    ...
    userScrollEnabled = false,                       // ← beyondBounds yok → stack'teki imza
    modifier = Modifier.wrapContentHeight()          // ← WrapContentNode; ∞'ü sınırlamıyor
) { ... }
```

* Projedeki **tek** `userScrollEnabled = false`'lu `LazyVerticalGrid` bu (diğerleri TV sayfaları/pager'lar).
* Projedeki **tek** `wrapContentHeight()`'lı ızgara yine bu.
* Bileşen, adı ve dokümantasyonu gereği **bir liste içine gömülmek üzere** yazıldı ("Ana ekran grid görünümü", `modifier` parametresi dışarıdan) — yani tam da `LazyColumn.item {}` senaryosu.
* Aynı hatanın Sheet/Column zincirinde daha önce gerçekleştığı ve `height(sabit)` ile çözüldüğü `ProfileActivityComponents.kt:496-501`'de yazılı (kod içi yorum birebir bu istisnayı anıyor).

**Sürüm farkı (önemli):** Arkadaşın telefonunda **2.4.344** var; bu depo **2.4.340**'ta. Çöken çağrı noktası büyük olasılıkla 2.4.341–344 arası eklenen/güncellenen bir "ana ekran / Listem ızgara görünümü" (grid düzeni) — örn. `MyListGroupedContent`/`MyListFlatContent` içine `item { LazyVerticalGrid(userScrollEnabled=false, Modifier.fillMaxWidth().wrapContentHeight()) }` olarak yerleştirilen ızgara ("Listem 2 sütun" düzeninin ızgara varyantı). Bu depodaki yaşayan en yakın ata kalıp `GridHomeContent`'tir; §5'teki düzeltme şablonu oraya uygulandı ve §6'daki talimatla yeni sürüme taşınmalıdır.

---

## 4. Neden "açılışta" çöküyor?

* Kayıtlar #3/#4: `SON EKRAN: baslangic`, **eylem izi yok** — yani ilk Compose karesi çizilirken (tab breadcrumb'ları daha yazılmadan) patlıyor. `dispatchDraw → measureAndLayout` da bunu doğruluyor: ilk çizim geçişi.
* Açılışta son kullanılan sekme/ızgara düzeni geri yüklenir; ızgara item'ı ölçülür ölçülmez istisna fırlar → süreç kapanır → kullanıcı "açılışta çöküyor" görür.
* Rapor sonundaki "Son aşama: attachBaseContext" **bu vakada doğru yöne işaret ediyor** (açılışta ölüm) ama **teknik olarak bozuk bir kanıttır** — bkz. §7.1.

---

## 5. Uygulanan düzeltmeler

### 5.1 `GridHomeContent.kt` — ızgara yüksekliği satır sayısından hesaplanıp kesin `height(...)` verildi
`wrapContentHeight()` (sınırsız) yerine:
* Hücre genişliği ekran genişliğinden türetilir (yatay 16+16 dolgu, 8.dp sütun arası).
* Veri kartı yüksekliği: 14+14 iç dolgu + 2:3 poster + 182.dp sabit metin bloğu (`KitsugiExploreMediaCard(forceVertical)` düzeni — metin bloğu sabit olduğu için fontScale'den etkilenmez).
* `gridYüksekliği = satır × kartYüksekliği + (satır-1) × 8.dp`, üstüne `coerceAtMost` güvenlik sınırı.
* Sonuç: ızgara her koşulda **sonlu** maxHeight alır; item içine gömülüyken bile ölçüm çökmez, içerik kırpılmaz.

### 5.2 `ProfileSelection.kt` — aynı kalıptaki ızgaraya `heightIn(max = 520.dp)` sınırı (+ yorum).

### 5.3 `KitsugiApplication.kt` — teşhis hatası düzeltildi (§7.1): `:crash` süreci artık `StartupStage` iz dosyasını "attachBaseContext" ile ezmek.

---

## 6. 2.4.344+ üzerindeki çöken çağrı noktası için talimat

Yeni sürümde aşağıdaki kalıbı arayın (Listem/ana ekran ızgara görünümü):

```kotlin
item {   // veya items(...) içeriği — bir LazyListScope item'ı
    LazyVerticalGrid(
        columns = ...,
        userScrollEnabled = false,
        modifier = Modifier.fillMaxWidth().wrapContentHeight()   // ← ÇÖKEN KALIP
    ) { ... }
}
```

**Çözüm (birebir uygulanacak):**

```kotlin
// wrapContentHeight() YERINE — satır sayısından sonlu yükseklik:
val rowCount = ceil(itemCount / columns.toFloat()).toInt().coerceAtLeast(1)
val gridHeight = /* satır yüksekliği hesabı — GridHomeContent'teki rememberGridHomeEmbedHeight ile aynı */
LazyVerticalGrid(
    columns = ...,
    userScrollEnabled = false,
    modifier = Modifier.fillMaxWidth().height(gridHeight)   // ✓ sonlu maxHeight
) { ... }
```

Alternatif (ProfileActivityComponents.kt:499-508'deki onaylı kalıp): ekran yüksekliğine bağlı sabit yükseklik + iç kaydırma:

```kotlin
val gridHeight = (LocalConfiguration.current.screenHeightDp * 0.7f).dp
LazyVerticalGrid(
    state = gridState,
    modifier = Modifier.fillMaxWidth().height(gridHeight),   // ✓
    ...
)
```

**Asla yapılmayacaklar:** `wrapContentHeight()`/`wrapContentSize()` ile "kurtarmaya çalışma" (sınırlamaz), `wrapContentSize(unbounded = true)` (daha kötü), `Modifier.weight(1f, fill = false)` altında ızgara (sonsuz ölçüm).

**Kural (kod incelemesi için):** Bir `LazyVerticalGrid`/`LazyColumn`/`Column(verticalScroll)` yalnızca şu durumlarda item içine konabilir: (a) `height/heightIn/aspectRatio+width` ile sonlu yükseklik verilmişse, veya (b) item içeriği doğrudan o scrollable değil, sonlu yükseklikli bir sarmalayıcı ise.

---

## 7. Yan bulgular (teşhis sistemi hataları)

### 7.1 "Son aşama: attachBaseContext" raporlarda YANLIŞ kanıt üretiyordu — DÜZELTİLDİ
* `KitsugiApplication.attachBaseContext()` **her süreçte** (çökme ekranı `:crash` süreci dahil) `StartupStage.mark(base, "attachBaseContext")` yazıyordu; `:crash`'in `onCreate`'i process-guard ile erken döndüğü için iz dosyası "attachBaseContext"te kalıyordu.
* `KitsugiCrashLogger.readCrashLog()` ise "Son aşama" bloğunu **okuma anında** (raporu çökme ekranı görüntülerken, yani `:crash` sürecinde) dosyadan üretiyordu → her raporda "Son aşama: attachBaseContext" görünüyordu; gerçek açılış aşaması gizleniyordu.
* **Düzeltme:** `:crash` süreci iz dosyasını artık yazmıyor (`detectCrashProcess()`). Çöken ana sürecin bıraktığı gerçek aşama rapora yansır.

### 7.2 Unclean-exit analizcisi Java çökmelerini "SEBEP BULUNAMADI" yazabiliyor
Ölüm nedeni yalnızca logcat penceresinden aranıyor; bu cihazda arabellek MIUI gürültüsüyle hızla dönüyor. #3/#4 kayıtlarında "ÖN PLANDA → gerçek çökme" denmesine rağmen FATAL satırı bulunamamış. Gelecek iyileştirme: analizci `crash_log.txt`'nin oturum PID'siyle eşleşip eşleşmediğine de bakmalı (bilgi notu).

### 7.3 Rapordaki `Zaman:` alanı güvenilmez
`Kitsugi_Crash_Report` içindeki `2026-07-21 10:55:02` gerçek olay zamanı değil (biçim hatası/eski yazıcı). Zaman çizelgesi daima logcat damgalarından kurulmalıdır (bu raporda §1.2 öyle yapıldı).

---

## 8. Doğrulama

1. **Kod:** `GridHomeContent` / `ProfileSelection` ızgaraları artık her koşulda sonlu `maxHeight` alıyor (hesap → `height(...)` / `heightIn(max=...)`). `userScrollEnabled=false` korundu (davranış: iç kaydırma yok; tüm satırlar gösterilir).
2. **Teşhis:** `:crash` süreci iz dosyasını ezmiyor; sonraki çökme raporlarında "Son aşama" gerçek değeri gösterecek.
3. **Saha:** 2.4.344'teki çağrı noktası §6 kalıbıyla düzeltildikten sonra yeni APK ile şu senaryo tekrarlanmalı: uygulama tamamen kapatılır → Listem/ana ekran ızgara görünümü seçiliyken açılır → ilk karede çökme olmamalı. Ayrıca 3× "arka plandan öldürülüp yeniden açılış" testi önerilir (bu cihazda LMKD sık tetikleniyor).

---

## 9. Özet tablo

| Sorun | Kanıt | Durum |
|---|---|---|
| LazyVerticalGrid, LazyColumn item'ında `maxHeight=∞` ile ölçülüyor → `IllegalStateException` | `Kitsugi_Crash_Report (7).txt` stack (LazyGrid.kt:194 + LazyListMeasure.kt:224) | ✅ kök neden kesin |
| Kalıp: `userScrollEnabled=false` + `wrapContentHeight()` (sınırlamıyor) | Stack'te beyondBounds yok + WrapContentNode; `GridHomeContent.kt` | ✅ düzeltildi (§5.1) |
| Aynı kalıp `ProfileSelection.kt`'te de vardı | Kod taraması | ✅ düzeltildi (§5.2) |
| Açılışta çökme (08:25, 10:14 — v2.4.314) | unclean kayıtları #3/#4 (`baslangic`, ön plan) | ✅ açıklandı (§4) |
| "Son aşama: attachBaseContext" yanlış kanıtı | `StartupStage` + `:crash` süreci ezmesi | ✅ düzeltildi (§7.1) |
| Cihaz 2.4.344 > depo 2.4.340 — çöken çağrı noktası yeni sürümde | `device_info` + sürüm alanları | ⚠️ §6 talimatı yeni sürüme uygulanmalı |
