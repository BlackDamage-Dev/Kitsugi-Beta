# İŞ 2 — MOBİL KEŞFET GÖRSEL BİLEŞENLERİ · V2 TESLİM RAPORU

**Tarih:** 2026-11-08 · **Durum:** TESLİM EDİLDİ — cihaz testleri **BLOKLU** (ortamda JDK/Android SDK/cihaz yok; ayrıntı §9)

---

## 1. Kaynaklar ve çalışma kopyası

| Öğe | Değer |
|---|---|
| Tek geçerli talimat | V2: `https://docs.google.com/document/d/1Xtrh9S34zky7oVfFXwsC6OZS6mcJt0MDJR4N23n43Y8` (tamamı okundu) |
| Onaylı görsel referans | Drive `17_vONg0AYXWi6jGHhNJ2FwhOO7xD-7_v` — "02 — ONAYLANAN GÖRSEL REFERANS.png" (768×1360 px = 384×680 dp @2x). İndirildi ve **görüldü**; piksel ölçümleri alındı. |
| Temel commit | `051c4b6c1e03ba96c9e6ddcb903c75186a7cf350` (doğrulandı: `git rev-parse HEAD`) |
| Çalışma kopyası | Bu iş, ortak çalışma dizini yerine **ayrı, izole bir klon** içinde yapıldı (Arena oturum klonu, `/home/user/Kitsugi-Beta`). Klon yalnız bu ajana aittir; başka ajanla eşzamanlı paylaşılmaz. Talimat gereği **branch oluşturulmadı/değiştirilmedi, commit/push/merge yapılmadı**; değişiklikler yalnız aşağıdaki 5 yeni dosyadır (`git status` ile doğrulandı). |
| Eski v1 | Kullanılmadı. v1'in `KitsugiHorizontalMediaSection(showTitle=false)` + `ExploreSourceEngineSelectorPill` zorunluluğu V2'de kaldırıldığı için uygulanmadı. |

Erişim sorunu yaşanmadı: v2 belgesi, onaylı görsel ve proje kodu okundu. Görsel olmadan uyum iddiası yok (§10'daki karşılaştırma görseli gerçekten görüldü).

## 2. Teslim edilen dosyalar (yalnız 5, tamamı YENİ; önceden hiçbiri yoktu)

1. `app/src/main/java/com/kitsugi/animelist/ui/screens/explore/mobile/MobileExploreVisuals.kt`
2. `app/src/main/res/values/mobile_explore_strings.xml`
3. `app/src/main/res/values-en/mobile_explore_strings.xml`
4. `app/src/androidTest/java/com/kitsugi/animelist/ui/screens/explore/mobile/MobileExploreVisualsTest.kt`
5. `docs/KESFET_MOBIL_PLAN/IKI_IS_PARCACIGI/IS_2_TESLIM_RAPORU.md` (bu rapor)

ZIP: `KESFET_MOBIL_IS_2_V2_TESLIM.zip` — beş dosya, yollar aynen korunarak paketlendi. Ana ekrana bağlama YAPILMADI.

## 3. Dondurulan sözleşme (aynen korundu)

Paket: `com.kitsugi.animelist.ui.screens.explore.mobile` · tek Kotlin dosyası · hepsi `internal` + `@Composable`:

```kotlin
@Composable internal fun MobileExploreHeader(
    selectedPlatform: ExplorePlatform,
    onSourceClick: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
)
@Composable internal fun MobileExploreTabs(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
)
@Composable internal fun MobileExploreShelf(
    title: String, source: ExplorePlatform, results: List<JikanSearchResult>,
    isLoading: Boolean, alreadyInList: (JikanSearchResult) -> Boolean,
    getMediaEntry: (JikanSearchResult) -> MediaEntry?,
    onItemClick: (JikanSearchResult) -> Unit, onLongClickItem: (JikanSearchResult) -> Unit,
    onSeeAllClick: (() -> Unit)?, titleLanguage: String, scoreFormat: String,
    hideScores: Boolean, blurAdultMedia: Boolean, modifier: Modifier = Modifier,
)
@Composable internal fun MobileExploreHero(
    item: JikanSearchResult?, isLoading: Boolean, blurAdultMedia: Boolean,
    onItemClick: (JikanSearchResult) -> Unit, modifier: Modifier = Modifier,
)
```

Tipler mevcut projeden: `ExplorePlatform`, `JikanSearchResult`, `MediaEntry`, `RowScope`, `Modifier`. İmzalarda tek taraflı değişiklik yok. Yardımcı `private` composable'lar (poster kartı, kaynak etiketi, sınır rengi, durum ikonu/rengi) ve `internal MobileExploreTestTags` aynı dosyadadır.

## 4. Bileşenler ve referans ölçüleri

Referanstan piksel ölçümü (÷2 = dp) → uygulamaya alınan değer:

| Öğe | Referans (ölçülen) | Uygulanan |
|---|---|---|
| Yatay kenar boşluğu | 26 px → 13 dp | 13 dp (header, tabs, hero, raf; ayırıcı tam genişlik) |
| Hero | ~718×306 px → ~359×153 dp, oran ≈2,34 | `aspectRatio(2.3f)`, köşe 10 dp, ince 1 dp sınır |
| Hero alt gradient | alttan siyaha | alt %55'te `Transparent → Black@0.82` |
| İncele butonu | ~94×48 px → 47×24 dp görsel | görsel ~kompakt (14/6 dp padding), **dokunma alanı ≥48 dp** |
| Sekme alt çizgisi | 111×~2 px → 55,5×1 dp; metin+yan boşluklar | çizgi = metin + 2×11 dp; yükseklik 2 dp; seçili = accent |
| Sekme aralığı | merkezler ~76,5 dp | kutu genişliği + 21 dp aralık (≈aynı ritim) |
| Kart | 168×266 px → 84×133 dp | **84 dp genişlik, 84:135 oran** (hedef 84×135), köşe 8 dp |
| Kart aralığı | ~14 px → 7 dp | 7 dp |
| 384 dp'de görünürlük | 4 tam + 5.'nin ~7 dp'si | 13+4×84+3×7=370 dp → 5. kart 377 dp'de başlar ✓ |
| Raf başlığı | kalın beyaz + küçük kaynak + sağda Tümünü gör | 17 sp Bold + 12 sp TextSecondary + 12 sp see-all |
| Poster meta | "tür • yıl", ~12 sp, alt gradient | 11 sp, alt %34 gradient `Black@0.78`, 6/5 dp padding |

Davranış kuralları:
- **Header:** `actions` slotu aynen çizilir; sahte ikon/rota üretilmez. Marka yazısı `weight(1f)` + ellipsis → dar ekranda actions yerini korur. `ALL` → yerelleştirilmiş **Tümü/All**; diğerleri `shortName` (TMDB, Simkl, MAL…). Pil tıklaması `onSourceClick` (sheet İŞ 1). Global üst bar mükerrerliği İŞ 1 denetimindedir.
- **Tabs:** 4 sekme; seçili = accent yazı + ince alt çizgi + `Role.Tab`/`selected` semantiği. Geçersiz index (−5, 99) çökmez, hiçbir sekme seçili görünmez. Yatay swipe/pager YOK (İŞ 1). Dar ekran/büyük fontta sıra `horizontalScroll` ile erişilebilir kalır.
- **Hero:** `item != null` → önce `backdropUrl`, boşsa `imageUrl`; backdrop yüklenemezse `onLoadingFailed` ile poster'e düşer. Gövde ve İncele aynı `onItemClick(item)`'ı çağırır; tür/kaynak değiştirilmez. `null` → placeholder (yüzey + pasif CTA), `isLoading` → shimmer; ikisinde de buton **tıklanamaz**. NSFW: mevcut `KitsugiNsfwImage` (`blurAdultMedia` + `isAdult`, `highQuality=true`). Parametre ne gelirse o gösterilir (kitap dahil); seçim İŞ 1'in.
- **Shelf:** `onSeeAllClick == null` → Tümünü gör yok. `source=ALL` çökmez, yerelleştirilmiş Tümü. Kendi `LazyRow`'u (ortak kart/tema dosyalarına dokunulmadı); `kitsugiLazyKey("mex", index)` ile kaynak+tür+kimlik anahtarı. `rememberSaveable(title)` ile raf başlığı değişince konum sıfırlanır. Landscape'de de kart **daima dikey**.
- **Boş/stale politikası:** boş+yükleniyor → 5 kartlık shimmer; dolu+yükleniyor → mevcut veri korunur (shimmer yok); boş+yüklenmiyor → raf hiç çizilmez (politika parent/İŞ 1).
- **Kart verisi:** başlık `PreferenceHelpers.getDisplayTitle(titleLanguage)` (ROMAJI/ENGLISH/NATIVE, Latin-tercihli kaynak kuralları dahil). Tür/yıl **gerçekse** "tür • yıl"; eksik parça/ayraç atlanır (uydurma yok; tür `toTurkishGenre()` ile dile uyumlu). Artwork yoksa/yüklenemezse **görünen başlık fallback'i**; başlık her durumda semantikte. Skor: `hideScores` → rozet yok; giriş puanı (`getMediaEntry`) öncelikli, yoksa kaynak puanı; `PreferenceHelpers.formatScore` (POINT_10/100/5/3/STARS) aynen. Listede olma: accent ✓ rozeti; giriş varsa durum ikoni + yerelleştirilmiş durum açıklaması. Aynı `result` click/long-click callback'ine gider.
- Ağ/katalog/navigation YOK; yalnız Coil'in normal resim indirmesi.

## 5. Renk / tipografi / tema

Tüm renkler tema altyapısından: `KitsugiColors.Background/Surface/SurfaceSoft/TextPrimary/TextSecondary/TextMuted/Border`, `LocalKitsugiAccent`, `KitsugiColors.OnAccent`, `AccentBrush` (gradyan vurgu modu destekli), durum renkleri `KitsugiColors.Accent*`. İnce çerçeve `TextMuted@0.38`. Yazılar gradyan-uyumlu `theme.gradient.Text`.

**Bilinen tema farkları (V2 gereği token kullanımı):** referans zemin ≈`#0E1118`, uygulama koyu zemini `#080A10`; referans mint ≈`#9BD1B9` (daha yeşil), uygulama mint accent `#C8F4EF` (daha açık/camgöbeği). Onaylı koyu+mint kombinasyonunda düzen sadık, tonlar uygulama temasına bağlıdır; diğer temalarda (açık, AMOLED, pembe vb.) uygulama tercihine uyar. Referansın sıkışık (condensed) fontu proje varlıklarında yok; yeni font dosyası 5 dosya sınırını aşacağı için eklenmedi — `FontFamily.Default` + referans boyutları (26/17/16/14/12/11 sp) kullanıldı.

## 6. Erişilebilirlik

- Pil, İncele, Tümünü gör: görsel kompakt, **dokunma kutusu ≥48×48 dp**; sekmeler 48 dp yükseklik; kartlar 84×135 dp.
- Semantik: pil "Kaynak seç: Tümü"; hero `onClickLabel=İncele`; kart başlığı `contentDescription`; skor rozeti "Puan: …"; durum rozeti yerelleştirilmiş durum adı; shimmer'larda "Yükleniyor" açıklaması; mükerrer okuma için görsel CD'leri null.
- Büyük font (1.5×) + 320 dp'de sekmeler kaydırılarak erişilebilir/kliklenebilir (testli).

## 7. Stringler

Yeni adların tümü `mobile_explore_` önekli, TR (`values`) + EN (`values-en`): brand, source_all, source_select, tab_series/movies/anime/manga, hero_caption, hero_cta, see_all, in_list, score_cd, loading. Büyük başlık mevcut `R.string.kesfet` (TR "Keşfet" / EN "Explore") yeniden kullanıldı. Referanstaki kurgusal poster/başlıklar hiçbir yere sabit kodlanmadı.

## 8. Testler (39 instrumented vaka)

Dosya: `MobileExploreVisualsTest.kt` · `createComposeRule` · **ağsız**: Coil singleton'a `Interceptor` ile sabit bitmap dönen sahte yükleyici (`setUnsafe`, sonra geri alınır); fixture URL'leri `.invalid`; shimmer saati sabit `State` ile dondurulur.

Kapsam: header marka/ALL/aksiyon slotu + kaynak kısaltması (2) · 4 sekme label/index/seçililik + geçersiz ±index (3) · hero boş/loading/dolu/İncele/gövde callback + yetişkin blur + 2.3 oran (6) · raf başlık/kaynak/Tümünü gör null-dolu/ALL (3) · kart click+long-click, boş+loading shimmer, stale koruma, boş politika (4) · görünür başlık fallback, tür/yıl/ayraç atlama (3), listede/durum rozeti (2), skor biçimi/gizleme/POINT_100/giriş önceliği (4), başlık dili değişimi (1), yetişkin kart tıklaması (1) · açık+ farklı accent / koyu tema (2) · 320/360/384/tablet/landscape dikey poster/büyük font (6) · 48 dp dokunma ölçümü (1) vb.

**Çalıştırma komutları (JDK+SDK+cihaz olan ortamda):**
```
./gradlew :app:tasks --all
./gradlew :app:assembleFossDebug
./gradlew :app:connectedFossDebugAndroidTest   # veya connectedGmsDebugAndroidTest
```
Flavor'lar `foss`/`gms` (dimension `version`, `app/build.gradle.kts`); görev adları AGP standardından beklenen adlardır — `:app:tasks` bu ortamda çalıştırılamadığı için **doğrulanamadı**.

## 9. Test sonuçları — DÜRÜST DURUM: BLOKLU

Bu teslim ortamında **JDK yok, Android SDK yok, cihaz/emülatör yok**. Kanıt (`./gradlew :app:tasks --all` çıktısı):
```
ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
```
`JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT` boş; `/usr/lib/jvm` ve Android Sdk dizinleri yok. Ayrıca sandbox ağ kısıtı yalnız GitHub/npm/PyPI hosts'larına izin verir; Gradle 8.13 dağıtımı (services.gradle.org) ve Android SDK (dl.google.com) bu ortamdan indirilemez.

**Bu nedenle 39 testin hiçbiri ÇALIŞTIRILMADI; "geçti" denmiyor. Derleme de doğrulanmadı.** Yapılabilen statik doğrulamalar:
- Her iki Kotlin dosyasında sözdizimi dengası (string/yorum ayıklı brace/paren/bracket) ✓
- Her iki XML `minidom` ile parse ✓
- İmza/API referansları repo kaynağından tek tek doğrulandı (`KitsugiNsfwImage`, `KitsugiShimmerBlock`, `kitsugiLazyKey`, `PreferenceHelpers`, `localizedLabel`, tema lokalleri, `LazyListState.Saver` deseni) ✓
- Coil **3.5.0** API'leri (SingletonImageLoader.setUnsafe/get, Interceptor.Chain, SuccessResult, Bitmap.asImage, ComponentRegistry.add(interceptor)) GitHub kaynak arşivinden doğrulandı ✓
- `git status`: yalnız izin verilen 5 yeni dosya; mevcut dosyalarda değişiklik yok ✓

## 10. Önizleme ve referans karşılaştırması

`is2-referans-karsilastirma.png` (ZIP dışında ayrıca teslim edildi): solda onaylı referans, sağda **açıkça "ŞEMATİK ÖNİZLEME — Compose/cihaz render'ı değildir" etiketli** PIL çizimi (bileşen dp ölçüleri + uygulama tema tokenları + fixture veri). Gerçek Compose screenshot ALINAMADI (JDK/SDK/cihaz yok); tek görsel sadakat kanıtı sayılmaz, gerçek cihaz kapısı İŞ 1'indedir.

Görülen farklar (dürüst liste):
1. **Dikey ritim daha uzun:** 48 dp dokunma satırları (V2 erişilebilirlik şartı) header/sekme/raf başlığını referansın kompakt satırlarından uzun yapar; 680 dp'de referans 2 raf + alt nav gösterirken önizlemede 2. raf kısmen kesilir. Alt navigasyon zaten bu 4 bileşenin parçası değil (mevcut app/İŞ 1).
2. **Renk tonları:** zemin daha koyu (`#080A10` vs ≈`#0E1118`), mint daha açık (`#C8F4EF` vs ≈`#9BD1B9`) — token kullanımı gereği (§5).
3. **Tipografi:** referansın condensed fontu yerine Default/DejaVu → başlıklar daha geniş.
4. **Ek rozetler:** gerçek skor/durum/listede rozetleri referansta yok; V2 "gerçek score/durum gerektiğinde gösterilir" şartı için kompakt ve erişilebilir biçimde eklendi (veri yoksa görünmez).
5. Geometri (kenar 13 dp, 84×135 kart, 7 dp aralık, 4+yalak 5. kart, hero 2.3:1, pill/sekme/raf düzeni) referansla eşleşir. Önizlemede meta taşması PIL'in ellipsis yapmamasından; Compose'da `maxLines=1 + Ellipsis` var.

## 11. Riskler ve kısıtlar

- **Derlenmemiş kod riski:** ortamda Kotlin derleyicisi yok; olası küçük derleme düzeltmeleri entegrasyon ortamında yine yalnız bu 5 dosya içinde yapılmalıdır (sözleşme değişmez).
- Dikey ritim farkı (§10.1) gerçek cihazda İŞ 1 ile birlikte ayarlanmalı; gerekirse boşluklar bu dosya içindeki sabitlerden güncellenir (sınır değişikliği gerekirse durup sorulur).
- `actions` içeriği, kaynak sheet'i, pager, gerçek veri seçimi, global üst bar denetimi, alt navigasyon: İŞ 1. Bu dosya hiçbirini taklit etmez.
- NSFW blur'un **görsel** doğrulaması cihaz/screenshot ister; testler kararlılık+callback düzeyindedir.

## 12. İŞ 1 entegrasyon notları

- Önerilen yerleşim: `Column { MobileExploreHeader(...); MobileExploreTabs(...); Spacer(5.dp); MobileExploreHero(...); Spacer(15.dp); raflar… }` — raflar arası ~20 dp. Bileşenler kendi 13 dp yatay kenarını taşır; dışarıdan yatay padding VERMEYİN.
- Rafları `KitsugiShimmerProvider(active = herhangiYükleniyor)` ile sarmak tek shimmer saati paylaşımı sağlar (paylaşım yoksa bileşen kendi saatini kurar, yine çalışır).
- Hero `item` seçimi, `onSourceClick` sheet'i, sekme↔pager bağlantısı, `onSeeAllClick` rotaları ve boş/hata politikası parent'ta bağlanır. `titleLanguage/scoreFormat/hideScores/blurAdultMedia` mevcut ViewModel tercihlerinden aynen geçirilir.
- Test etiketleri: `MobileExploreTestTags` (header, source_pill, tabs, tab_0..3, hero, hero_cta, hero_loading, shelf, shelf_loading, see_all, card, card_score, card_status).

## 13. Sınır uyumu beyanı

Gerçek ekran/pager/kaynak/hesap/servis/detay/grid/cache, TV, Gradle, ortak bileşen/tema/navigasyon dosyalarına **dokunulmadı**; yeni bağımlılık eklenmedi; kullanıcı değişiklikleri silinmedi; branch/commit/push/merge yapılmadı; ana ekrana bağlama yapılmadı. Sözleşme veya sınır değişikliği gerekmedi; gerekirse durup entegrasyon sorumlusuna sorulacak.
