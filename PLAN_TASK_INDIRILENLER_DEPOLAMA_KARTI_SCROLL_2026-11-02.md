# PLAN & TASK — İndirilenler: Depolama Kartı Kaydırma Animasyonu

**Tarih:** 2026-11-02 · **Durum:** ✅ Kod tamam — CI derleme/test doğrulaması başarılı; cihaz üstü son kontrol kullanıcıda
**Dal:** `arena/8a13db8e-kitsugi-beta`
**Baz commit:** `6bbd40eaf82b174303ed87bc28e7a86a500e07d5`
**İş commitleri:** `f18f046a`, `8e5819d0`
**CI:** https://github.com/f26901964-eng/Kitsugi-Beta/actions/runs/41360037446

---

## 1. Problem

İndirilenler ekranındaki “Cihazdaki indirmeler” depolama kartı, liste kaydırılırken sabit kalıyordu.
Kullanıcı, kartın aşağı kaydırmada yumuşakça gizlenmesini, yukarı kaydırmada geri gelmesini istedi.

Ek bulgu: bu çalışma dalındaki eski `DownloadsScreen.kt` kopyasında görseldeki depolama kartı hiç yoktu.
Kart ve güncel İndirilenler arayüzü, açık geliştirme dalındaki (`feat/golge-bahcesi-integration`) yeni ekranda bulunuyordu.
Bu yüzden yalnızca animasyon eklemek yerine güncel ekran ve gerekli bağımlılıkları bu dala taşındı.

## 2. Hedef davranış

- Aşağı kaydırma: depolama kartı yumuşakça kapanır.
- Yukarı kaydırma: kart yumuşakça geri açılır.
- Kart kapandığında yalnızca görünürlük değişmez; kapladığı yükseklik de kaldırılır.
- Sekme bandı ve liste, boşalan alanı kullanır; üst üste binme olmaz.
- Boş sayfa, yatay sekme kaydırması ve liste tepesinde gereksiz gizlenme olmaz.
- Sekme/arama/filtre/sıralama değişince ve liste tepesine dönünce kart tekrar görünür.
- Her sekme kendi kaydırma konumunu korur.

## 3. Görev listesi

### A. Güncel İndirilenler ekranını taşıma
- [x] `DownloadsScreen.kt` güncel sürümü taşındı (depolama kartı, arama, filtre, sıralama, çoklu seçim).
- [x] `DownloadsImagesTab.kt` ve `DownloadsUiPrefs.kt` taşındı.
- [x] Gerekli ikon/top-action bileşenleri taşındı: `KitsugiIcons`, `KitsugiIconAssets`, `KitsugiTopActionBar`, `KitsugiIconKit`.
- [x] 3B ikon PNG varlıkları ve lisans/atıf dosyaları eklendi.
- [x] İndirme satırlarının ihtiyaç duyduğu `StreamLabelSanitizer` eklendi.
- [x] Kuyruk sırası taşıma yardımcıları `AnimeDownloadManager` içine eklendi.
- [x] TR/EN eksik metinler tamamlandı.

### B. Kaydırma durumu ve politika
- [x] `DownloadsOverviewScroll.kt` oluşturuldu.
- [x] Video/altyazı için `LazyListState`, resimler için `LazyGridState` aktif sekmeye bağlandı.
- [x] Kaydırma konumları sekme değişimi ve yapılandırma değişiminde korunuyor.
- [x] Ham parmak hareketi yerine listenin tükettiği dikey kaydırma kullanılıyor.
- [x] Yön eşiği/hysteresis: gizleme 28 dp, gösterme 14 dp.
- [x] Boş içerik, yatay pager hareketi ve sıfır/hatalı delta kartı gizlemiyor.
- [x] Fling devamı (`SideEffect`) dikkate alınıyor; jest bitiminde kısmi yol sıfırlanıyor.
- [x] Liste tepesine dönünce veya son öğe silinince kart görünür hale geliyor.
- [x] Sekme/arama/filtre/sıralama değişiminde kart yeniden gösteriliyor.

### C. Animasyon ve yerleşim
- [x] `DownloadsOverviewReveal` ile `AnimatedVisibility` kullanıldı.
- [x] Yükseklik animasyonu: expand/shrink vertically, overshoot yok.
- [x] Fade + çok hafif dikey slide eklendi.
- [x] Kartın dikey padding’i de animasyonla kaldırılıyor.
- [x] Sekme bandı sabit kalıyor; liste alanı kalan yüksekliği kullanıyor.
- [x] İndirilenler sekme bandında güncel 3B ikon görünümü etkinleştirildi.

### D. Depolama bilgisi doğruluğu
- [x] Boş alan okuması ana thread dışında (`Dispatchers.IO`) yapılıyor.
- [x] İndirme sürerken 5 sn’de bir tazeleniyor.
- [x] Video/altyazı/resim sayısı ve tamamlanan indirme değişiminde yeniden okunuyor.
- [x] Kart üzerindeki tür bazlı adet/boyut ve toplam korunuyor.

### E. Test ve CI
- [x] JVM politika testleri: `DownloadsOverviewScrollPolicyTest`.
- [x] Android cihaz regresyon testi: `DownloadsOverviewScrollTest`.
- [x] `StreamLabelSanitizerTest` CI kapsamına alındı.
- [x] CI’ya `ui.screens.offline.*` testleri ve androidTest derleme adımı eklendi.
- [x] İlk CI derleyici hataları düzeltildi (`onPreFling` Velocity, free-space LaunchedEffect, StreamLabelSanitizer).
- [x] Son CI başarılı: Debug + R8 Release APK ve DEX doğrulaması.

## 4. Doğrulama özeti

CI run: `41360037446` — **success**

- `BUILD SUCCESSFUL in 21m 7s`
- Debug APK: 22 dex, 409,516 method body, 11,229,841 instruction; VerifyError = 0
- Release APK: 5 dex, 253,171 method body, 7,235,048 instruction; VerifyError = 0
- Android cihaz testi derlendi; fiziksel cihaz/emulatör üstünde çalıştırma bu paketleme görevinin kapsamında değildi.

## 5. Kullanıcı cihazında önerilen son kontrol

- [ ] Videolar sekmesinde aşağı/yukarı kaydırma.
- [ ] Altyazılar sekmesinde aşağı/yukarı kaydırma.
- [ ] Resimler ızgarasında aşağı/yukarı kaydırma.
- [ ] Boş sekmede kartın görünür kalması.
- [ ] Sekme değiştirince kartın geri gelmesi.
- [ ] Arama/filtre/sıralama değişince kartın geri gelmesi.
- [ ] Liste tepesine dönünce kartın geri gelmesi.
- [ ] Hızlı fling ve küçük parmak titremesinde aç/kapa olmaması.
- [ ] Kart kapanınca sekme bandı/liste ile çakışmaması.

## 6. Riskler ve geri alma

- En büyük fark, eski dalda bulunmayan güncel İndirilenler ekranının taşınmasıdır; bu nedenle değişiklik seti geniştir.
- İndirme/oynatma servis davranışı değiştirilmedi; yalnızca kuyruk taşıma yardımcıları eklendi.
- Geri alma: iş commitleri (`f18f046a`, `8e5819d0`) tersine uygulanabilir.

## 7. Değişen dosyalar

Aşağıdaki liste baz commit ile son commit arasındaki tüm değişiklikleri içerir. ZIP içinde dosyalar depo köküne göre aynı yollarda bulunur.

```text
.github/workflows/android-build.yml
PLAN_TASK_INDIRILENLER_DEPOLAMA_KARTI_SCROLL_2026-11-02.md
app/src/androidTest/java/com/kitsugi/animelist/ui/screens/offline/DownloadsOverviewScrollTest.kt
app/src/main/java/com/kitsugi/animelist/data/local/AnimeDownloadManager.kt
app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiIconKit.kt
app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiSettingsComponents.kt
app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiTopActionBar.kt
app/src/main/java/com/kitsugi/animelist/ui/icons/KitsugiIconAssets.kt
app/src/main/java/com/kitsugi/animelist/ui/icons/KitsugiIcons.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsImagesTab.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsOverviewScroll.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsScreen.kt
app/src/main/java/com/kitsugi/animelist/ui/screens/offline/DownloadsUiPrefs.kt
app/src/main/java/com/kitsugi/animelist/utils/StreamLabelSanitizer.kt
app/src/main/res/drawable-nodpi/ic_kitsugi_back.png
app/src/main/res/drawable-nodpi/ic_kitsugi_bell.png
app/src/main/res/drawable-nodpi/ic_kitsugi_bookmark.png
app/src/main/res/drawable-nodpi/ic_kitsugi_boy.png
app/src/main/res/drawable-nodpi/ic_kitsugi_bulb.png
app/src/main/res/drawable-nodpi/ic_kitsugi_calender.png
app/src/main/res/drawable-nodpi/ic_kitsugi_card.png
app/src/main/res/drawable-nodpi/ic_kitsugi_chart.png
app/src/main/res/drawable-nodpi/ic_kitsugi_chat.png
app/src/main/res/drawable-nodpi/ic_kitsugi_chat_text.png
app/src/main/res/drawable-nodpi/ic_kitsugi_clock.png
app/src/main/res/drawable-nodpi/ic_kitsugi_colour_palette.png
app/src/main/res/drawable-nodpi/ic_kitsugi_computer.png
app/src/main/res/drawable-nodpi/ic_kitsugi_copy.png
app/src/main/res/drawable-nodpi/ic_kitsugi_explorer.png
app/src/main/res/drawable-nodpi/ic_kitsugi_flash.png
app/src/main/res/drawable-nodpi/ic_kitsugi_folder.png
app/src/main/res/drawable-nodpi/ic_kitsugi_headphone.png
app/src/main/res/drawable-nodpi/ic_kitsugi_heart.png
app/src/main/res/drawable-nodpi/ic_kitsugi_key.png
app/src/main/res/drawable-nodpi/ic_kitsugi_lab.png
app/src/main/res/drawable-nodpi/ic_kitsugi_link.png
app/src/main/res/drawable-nodpi/ic_kitsugi_lock.png
app/src/main/res/drawable-nodpi/ic_kitsugi_locker.png
app/src/main/res/drawable-nodpi/ic_kitsugi_magic_trick.png
app/src/main/res/drawable-nodpi/ic_kitsugi_megaphone.png
app/src/main/res/drawable-nodpi/ic_kitsugi_mobile.png
app/src/main/res/drawable-nodpi/ic_kitsugi_moon.png
app/src/main/res/drawable-nodpi/ic_kitsugi_next.png
app/src/main/res/drawable-nodpi/ic_kitsugi_pause.png
app/src/main/res/drawable-nodpi/ic_kitsugi_pencil.png
app/src/main/res/drawable-nodpi/ic_kitsugi_picture.png
app/src/main/res/drawable-nodpi/ic_kitsugi_pin.png
app/src/main/res/drawable-nodpi/ic_kitsugi_play.png
app/src/main/res/drawable-nodpi/ic_kitsugi_puzzle.png
app/src/main/res/drawable-nodpi/ic_kitsugi_rocket.png
app/src/main/res/drawable-nodpi/ic_kitsugi_setting.png
app/src/main/res/drawable-nodpi/ic_kitsugi_sheild.png
app/src/main/res/drawable-nodpi/ic_kitsugi_sphere.png
app/src/main/res/drawable-nodpi/ic_kitsugi_star.png
app/src/main/res/drawable-nodpi/ic_kitsugi_sun.png
app/src/main/res/drawable-nodpi/ic_kitsugi_thumb_up.png
app/src/main/res/drawable-nodpi/ic_kitsugi_tick.png
app/src/main/res/drawable-nodpi/ic_kitsugi_toggle.png
app/src/main/res/drawable-nodpi/ic_kitsugi_tool.png
app/src/main/res/drawable-nodpi/ic_kitsugi_trash_can.png
app/src/main/res/drawable-nodpi/ic_kitsugi_travel.png
app/src/main/res/drawable-nodpi/ic_kitsugi_video_camera.png
app/src/main/res/drawable-nodpi/ic_kitsugi_wifi.png
app/src/main/res/drawable-nodpi/ic_kitsugi_zoom.png
app/src/main/res/values-en/strings.xml
app/src/main/res/values/strings.xml
app/src/test/java/com/kitsugi/animelist/ui/screens/offline/DownloadsOverviewScrollPolicyTest.kt
app/src/test/java/com/kitsugi/animelist/utils/StreamLabelSanitizerTest.kt
docs/IKON_SETI.md
third_party/3dicons_LICENSE.txt
third_party/3dicons_NOTICE.txt
```
