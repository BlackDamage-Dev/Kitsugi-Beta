# 19 — Kaydırma ve animasyon uyumu

Tarih: 2026-11-08 · Yalnız mobil. Bu çalışma henüz gerçek Keşfet'e bağlanmadı ve Android cihazda çalıştırılmadı.

## Bulgu ve uygulanan değişiklikler

1. **Yatay raf ritmi/snap:** Önceki Keşfet `KitsugiHorizontalMediaSection` mobil `LazyRow` içinde `KitsugiScrollDefaults.rememberRowSnapFling(lazyListState)` kullanıyor. Yeni `MobileExploreShelf` başlangıçta normal `LazyRow` idi; aynı davranış yoktu. Şimdi aynı yardımcı, aynı `SnapPosition.Start` ve rafın kendi 13dp içerik dolgusuyla bağlandı. Yeterli içerikte parmak bırakıldığında en yakın kartın başlangıç padding'ine oturması amaçlanır; en son kartta scroll sınırları bunu kısıtlayabilir. Gerçek cihazda his ve görünüm test edilmedi.
2. **Üst sekmeler:** Mevcut yeni `HorizontalPager` parmakla sayfa geçişi; sekme tıklandığında `animateScrollToPage` kullanıyor. Sekme/raf yatay hareket çakışması gerçek cihazda denenmedi.
3. **Alt bar ve FAB:** Eski uygulamada AppRoot'un `nestedScroll(rememberScrollConnection(...))` bağlantısı dikey kaydırmayı izliyor; görünürlük animasyonu ve sabit bar ayarı AppRoot'ta. Eski Explore listesinde alt boşluk 100dp ve `KitsugiScrollToTopFab` aktif listenin konumuna/alt bara göre davranıyor. Yeni bağımsız hostta aynı 100dp liste alt boşluğu, aktif sayfayı izleyen isteğe bağlı aynı FAB, yumuşak başa dönüş ve `onScrollReset` köprüsü hazırlandı. **Ana ekrana henüz kurulmadığı için bu üst seviye bağlantılar çalışır durumda değildir.** Entegratör mevcut AppRoot nested-scroll bağlantısını kullanmalı; ikinci bağlantıyı ekleyerek barı iki kez tetiklememeli, FAB opsiyonunu açmalı, mevcut `bottomBarScrollState`, fixedNavBar ayarı ve `onScrollReset` değerlerini geçmelidir.
4. **Üst alan:** Yeni görsel host header ve sekmeleri sabit bir Column içinde tutuyor. Bu henüz eski Keşfet'in sticky kaynak seçicisiyle birebir mekanik eşdeğerliği veya kullanıcının istediği üst barın yumuşak girip çıkma davranışı olarak onaylanmış değildir. Gerçek ekran entegrasyonunda mevcut üst davranış ve referans düzen birlikte incelenmeli, gerekirse animasyon eklenmeli; tasarım/erişilebilirlik ve dikey alan test edilmeden tamamlandı denmemeli.
5. **Çakışma önlemi:** Mevcut AppRoot mobil `LocalBringIntoViewSpec` touch-passthrough sağlar. Yeni host gerçek AppRoot ağacına bağlanınca bu ortak davranışı kullanmalı; yatay kart kaydırma, dikey liste, pager, refresh, fokus/uzun basma ve sistem geri jesti arasındaki çakışmalar test edilmelidir.

## Test durumu

- Yeni üç cihaz testi yazıldı: yatay swipe sonrası karta hizalanma, aktif sayfadan animasyonlu başa dönüş, mevcut nested-scroll bağlantısına dikey hareket iletimi. **Hiçbiri çalıştırılmadı.** En son toplam 52 cihaz testi yazılı; 0 çalıştırılmış. Testlerde gerçek ekran boyutu, animasyon süresi ve kaydırma sınırları cihazda ayrıca kontrol edilmeli.
- 27 izole saf Kotlin kontrolü stand-in'lerle tekrar geçti; bu kaydırma/Compose testi değildir. 19 Kotlin dosyası tree-sitter ile ayrıştırıldı, `git diff --check` geçti; gerçek Android/Compose derlemesi yapılmadı.
- SDK/cihaz ve Gradle dağıtımı eksik: FOSS/GMS gerçek build, yavaş/hızlı fling, düşük kare hızı, erişilebilirlik, farklı yoğunluklar ve farklı büyüklükte ekranlar, kullanıcının gördüğü üst/alt barlar ile son görsel/motion kabulü açık.
