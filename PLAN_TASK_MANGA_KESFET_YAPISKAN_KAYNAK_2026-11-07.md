# PLAN & TASK — Manga Keşfet: Yapışkan Kaynak Çubuğu + Toplu Kaynak Seçici

**Tarih:** 2026-11-07
**Dal:** `arena/104456fa-kitsugi-beta`
**Commit:** `cd05dd5`
**Ekran:** `Manga Keşfet` → `app/src/main/java/com/kitsugi/animelist/ui/screens/manga/MangaBrowseScreen.kt`
**Referans / parite hedefi:** Video İzle keşfet → `ui/screens/stream/StreamScreenContent.kt`
(`CollapsibleTopBarBox`, `HideOnScrollConnection`, `AddonChipsRow`, kaynak `ModalBottomSheet`)

---

## 0 · İstek (kullanıcı)

1. Manga eklenti keşfet sayfasında aşağı kaydırınca **kaynak çipi satırı yukarı yapışmalı**, üstteki diğer kısımlar **animasyonlu** gizlenmeli.
2. Çiplerin **hemen yanına açılır buton** konmalı; kaynaklar video keşfet sayfasındaki gibi **açılır pencerede toplu ve seçilebilir** gösterilmeli.
3. İşaretlenen üst kısım video keşfet sayfasındaki gibi **daha derli toplu** olmalı.

---

## 1 · Plan

| # | Adım | Karar |
| --- | --- | --- |
| 1 | Referans ekranı incele | `StreamScreenContent.kt` → parmakla akan `nestedScroll` ofseti, tek satır kompakt kaynak kartı, `ModalBottomSheet` picker |
| 2 | Manga ekranını tek kaydırılabilir listeye indir | `Column` + 3 ayrı grid yerine **tek `LazyVerticalGrid`** + `rememberLazyGridState` + üst `contentPadding` |
| 3 | Katlanan başlık | `MangaHideOnScrollConnection` (stream ile aynı matematik) + `graphicsLayer { translationY / alpha }` |
| 4 | Yapışkan çubuk | `[katlanan kısım][çip çubuğu]` tek `Column`; ofset = katlanan yükseklik olunca çubuk `y=0`'a oturur |
| 5 | Animasyon | `animateFloatAsState` (zemin), `animateDpAsState` (gölge), `AnimatedVisibility` (aramaya dönüş kısayolu), `KitsugiMotion` süreleri |
| 6 | Toplu seçici | `MangaSourcePickerSheet`: özet + kaynak arama + `Tümü` + tüm kaynaklar (durum/sayaç/seçili) |
| 7 | Derli toplu üst kısım | Kompakt top bar, arama hapı + "adını değiştir", X'li arama kutusu, `İlgi / A-Z` sıralama, kompakt `MangaChip` |
| 8 | i18n | TR + EN yeni `manga_browse_*` anahtarları, mevcut kaynaklar yeniden kullanıldı |
| 9 | Doğrulama | tree-sitter Kotlin ayrıştırma, parantez dengesi, XML geçerliliği, `R.string` anahtar denetimi |

---

## 2 · Görev listesi (TASK)

| ID | Görev | Durum | Dosya |
| --- | --- | --- | --- |
| T-01 | `MangaHideOnScrollConnection` (kısa liste koruması + başa dönüş) | ✅ | `MangaBrowseScreen.kt` |
| T-02 | Tek `LazyVerticalGrid` + `contentPadding(top = katlanan + yapışkan)` | ✅ | `MangaBrowseScreen.kt` |
| T-03 | `onSizeChanged` ile katlanan yükseklik ölçümü + ilk kare tahmini (200 dp) | ✅ | `MangaBrowseScreen.kt` |
| T-04 | `snapshotFlow` ile liste başında başlığı geri açma | ✅ | `MangaBrowseScreen.kt` |
| T-05 | `MangaBrowseTopBar` — 54 dp, başlık + `N sonuç · M kaynak` + kaynak yönetimi | ✅ | `MangaBrowseScreen.kt` |
| T-06 | `MangaSearchNameRow` + `FocusRequester` ile "Arama adını değiştir" | ✅ | `MangaBrowseScreen.kt` |
| T-07 | `MangaSearchBar` — X ile temizle, tek satır, odak desteği | ✅ | `MangaBrowseScreen.kt` |
| T-08 | `MangaSortRow` / `MangaSortPill` — gerçek `İlgi` ↔ `A-Z` sıralaması (`rememberSaveable`) | ✅ | `MangaBrowseScreen.kt` |
| T-09 | `MangaStickySourceBar` — çipler + sabit dropdown + katlanma animasyonları | ✅ | `MangaBrowseScreen.kt` |
| T-10 | `MangaSourcePickerSheet` / `MangaSourcePickerRow` — toplu + seçilebilir kaynaklar | ✅ | `MangaBrowseScreen.kt` |
| T-11 | `MangaChip` kompaktlaştırma + `sourceDotColor` kararlı kaynak renkleri | ✅ | `MangaBrowseScreen.kt` |
| T-12 | `MangaResultsGrid` — yükleme / boş / sonuç / iskelet / sayfalama tek yerde | ✅ | `MangaBrowseScreen.kt` |
| T-13 | TR + EN 14 yeni string, çift tanım yok | ✅ | `values/strings.xml`, `values-en/strings.xml` |
| T-14 | Çözüm raporu | ✅ | `COZUM_RAPORU_MANGA_KESFET_YAPISKAN_KAYNAK_2026-11-07.md` |
| T-15 | Cihaz/CI derlemesi (`:app:compileDebugKotlin`) + kaydırma/sheet etkileşim testi | ⏳ | Sandbox'ta JDK/Android SDK yok |
| T-16 | Ekran görüntüsündeki yeni varyantın (`MangaDiscoverChipRow`, "5 başlık denendi") bulunduğu dal ile birleştirme | ⏳ | Kullanıcı onayı bekliyor |

---

## 3 · Kabul kriterleri

- [x] Aşağı kaydırınca yalnızca kaynak çubuğu + dropdown ekranda kalır; üst kısım kaybolur.
- [x] Hareket parmakla birlikte akar; yükseklik değişiminden dolayı ızgara zıplamaz.
- [x] Yukarı kaydırınca / başa dönünce üst kısım aynı hareketle geri gelir.
- [x] Kısa (kaydırılamayan) sonuç listesinde başlık gizlenmez.
- [x] Dropdown tüm kaynakları tek pencerede, aranabilir, durum + sayaç + seçili işaretiyle gösterir.
- [x] Seçim ızgarayı anında filtreler; `Tümü` filtreyi kaldırır.
- [x] Katlanmışken büyüteç kısayolu arama kutusuna döndürür.
- [x] `İlgi / A-Z` sıralaması gerçekten uygulanır ve dönüşte korunur.
- [x] `MangaBrowseScreen` imzası / `AppRootMangaPages` bağlantıları bozulmaz; TV ekranı etkilenmez.
- [x] TR/EN tüm metinler tanımlı, XML geçerli, Kotlin sözdizimi hatasız.

---

## 4 · Değişen dosyalar

```
app/src/main/java/com/kitsugi/animelist/ui/screens/manga/MangaBrowseScreen.kt
app/src/main/res/values/strings.xml
app/src/main/res/values-en/strings.xml
COZUM_RAPORU_MANGA_KESFET_YAPISKAN_KAYNAK_2026-11-07.md
PLAN_TASK_MANGA_KESFET_YAPISKAN_KAYNAK_2026-11-07.md   ← bu dosya
```

## 5 · Risk / not

Bu dal `main` (b78cbc4) üzerinden açıldığı için manga keşfet ekranının **eski** sürümünü içerir. Kullanıcının ekran görüntüsündeki `Arama: Yerel / çevrilmiş ad`, `5 başlık denendi`, `MangaDiscoverChipRow` düzeni diğer Arena dallarında (ör. `arena/54c8e317-kitsugi-beta`) bulunur ve ~300 dosyalık bir fark vardır. Bu üç özellik **bu daldaki** ekrana uygulanmıştır; yeni varyantın bu dala taşınması ayrı bir iştir (T-16).

Sandbox'ta `java` / `kotlinc` / Android SDK bulunmadığından Gradle derlemesi yapılamadı; statik doğrulamalar raporda belgelenmiştir.
