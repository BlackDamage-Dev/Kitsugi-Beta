# KİTSUGİ BETA — TÜM EKRANLAR DAİRESEL KOYU-CAM (KEŞFET) UI BİRLEŞTİRME RAPORU (`2026-11-23`)

Bu paket, **Kitsugi-Beta** Android Compose arayüzünün tamamını (5 ana sekme, tüm alt ayar sayfaları, tüm detay/karakter/kadro/stüdyo/iskelet/hata sayfaları, 7 platform profili, tam ekran kategori/eklenti ızgaraları, akış seçici, bildirimler, indirmeler, izleme geçmişi, yayın takvimi, istatistikler, hakkında, koleksiyonlar, manga vitrin/kütüphane/kaynak ekranları ve tüm tam ekran diyaloglar) **Keşfet (`Explore`) dairesel koyu-cam tasarım sisteminde** birleştiren tam kod paketidir.

---

## 1. Hızlı Kurulum (Projeye Uygulama)

`Kitsugi-Beta` depo kök dizinindeyken (`settings.gradle.kts` ve `app/` klasörünün bulunduğu dizin):

```bash
unzip -o Kitsugi_UI_Dairesel_Cam_Birlesik_Paket_2026-11-23.zip -d .
```

Tüm `79` Kotlin kaynak dosyası tam göreli yollarıyla (`app/src/main/java/com/kitsugi/animelist/...`) doğrudan yerine oturur.

---

## 2. Mimari Kısıtlar & Doğrulama Özeti

- **`+0` XML `<string>` Kaynağı:** `app/src/main/res/values*/strings.xml` dosyalarının hiçbirine yeni anahtar eklenmemiştir (`0` kaynak dosyası değişikliği; `64K R$string` ve `aapt2` sınırı %100 korunmuştur).
- **Sıfır Eski Bar / Kare Buton Kalıntısı:**
  - `ui/` altında kalan ham Material `TopAppBar` / `CenterAlignedTopAppBar` çağrısı: **`0`**
  - `ui/screens/` altında kalan ham Material `FloatingActionButton` çağrısı: **`0`**
  - `ui/` altında kalan köşeli `KitsugiTopActionButton(..., cornerRadius = ...)` ezmesi: **`0`**
- **Korunan Tek İstisna:** Tam ekran video oynatıcı (`fullscreen/controls/`) ve tam ekran manga/roman okuyucu (`manga/reader/`, `MangaReaderControls.kt`) sinematik oynatma/okuma arayüzleri (immersive HUD) bilinçli olarak korunmuştur.

---

## 3. Temel Ortak Bileşenler & Token Sözleşmesi

1. **`ui/components/KitsugiTopActionBar.kt`**
   - `KitsugiTopActionTokens`: `ButtonSize = 38.dp` (`CompactButtonSize = 34.dp`), `CornerRadius = 999.dp` (`CircleShape`), `HeaderHorizontalPadding = 13.dp`, `HeaderTopPaddingPortrait = 6.dp`, `HeaderTopPaddingLandscape = 4.dp`, `ContainerColor`, `BorderColor`.
   - `KitsugiIconGlassButton`: Tüm geri (`<-`), kapat (`X`), yenile, arama, filtre, görünüm, paylaş ve araç butonları için ortak dairesel koyu-cam bileşen.
   - `KitsugiSourceSelectorPill`: Keşfet, Listem, Arama ve Profil ekranlarında ortak dairesel koyu-cam kaynak/motor seçici kapsül (`min 48.dp` dokunma alanı).
   - `KitsugiTopActionRow`: Kanonik sıralamalı (`FILTER(10)` → `SEARCH(15)` → `REFRESH(20)` → ... → `HISTORY(91)` → `DOWNLOADS(92)` → `RANDOM(95)` → **`NOTIFICATIONS(100)` en sağda**) ve dar ekranlarda (`< 345dp` / `< 315dp`) sağdaki küresel butonları sabit tutup taşan sol aksiyonları `•••` (`R.string.daha_fazla`) menüsüne katlayan ortak satır.
   - `KitsugiDetailHeaderActions`: Tüm detay sayfalarında hem hero hem kaydırmalı yapışkan (sticky) üst barda aynı `[Paylaş, Favori, Galeri]` sırasını koruyan ortak bileşen.

2. **`ui/navigation/AppBottomBar.kt` & `ui/components/KitsugiScrollToTopFab.kt`**
   - `KitsugiBottomBarTokens`: Dairesel koyu-cam yüzen alt navigasyon hapı (`AppBottomBar`), yatay mod güvenli alan sol rayı (`AppNavigationRail`), `ExpandedControlOffset = 76.dp`, `CollapsedControlOffset = 14.dp`, `PortraitListBottomSpacer = 96.dp`, `LandscapeListBottomSpacer = 32.dp`.
   - `KitsugiScrollToTopFab` & `KitsugiFloatingFilterPill`: Yüzen alt bar ile senkron kayan sağ-alt yukarı-çık ve sol-alt kategori/durum hapı; **mini ses oynatıcı (`KitsugiAudioPlayerManager`) görünür olduğunda tüm ekranlarda otomatik `+68.dp` yukarı kalkarak çakışmayı önler**.

3. **Kök Pencere Boşlukları & `fixedNavBar` (`AppRoot.kt`)**
   - Kök `Scaffold` `Row` üzerinde `.padding(rootConsumedPadding).consumeWindowInsets(rootConsumedPadding)` uygulanarak alt sekmelerde ve detay sayfalarında çift/üçlü durum çubuğu (`statusBarsPadding`) boşluğu ortadan kaldırıldı; bağımsız `Activity` olarak da açılabilen ekranlar (`WatchHistoryScreen`, `DownloadsScreen`, `KitsugiNotificationsScreen`) tek başına açıldığında da güvenli kalacak şekilde korundu.
   - `appSettings.fixedNavBar == true` olduğunda kaydırmayla alt bar gizleme devre dışı bırakılıp bar sabitlendi.

---

## 4. Önerilen Yerel Derleme ve Test Komutları

```bash
./gradlew :app:testFossDebugUnitTest \
  --tests 'com.kitsugi.animelist.ui.components.KitsugiTopActionOrderTest' \
  --tests 'com.kitsugi.animelist.ui.screens.explore.mobile.*' \
  --tests 'com.kitsugi.animelist.ui.screens.mylist.MyListContentPageTest'

./gradlew assembleFossDebug
```
