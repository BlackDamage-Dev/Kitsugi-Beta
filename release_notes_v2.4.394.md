# Kitsugi Beta v2.4.394

[TR]
### 🚀 Yenilikler ve Hata Düzeltmeleri

Bu sürümde; **Profil Sekmesi Üst Çubuğu Sağ Hizalama** ve **Çok Yollu Vektör İkonlarının Çizim Kaybı (Path Loss)** düzeltmeleri yer alıyor!

---

#### 📱 1. Profil Üst Çubuğu Sağ Hizalama Onarımı
- **Butonların Sağa Yapışmama Sorunu Giderildi:** Profil sekmesindeki kaynak seçici hap butonu (`ProfileSourceSelectorPill`), önceden `weight(1f, fill = false)` ve ayrı bir `Spacer(weight(1f))` ile konumlandırılmıştı. Compose'un ölçüm politikasında `fill = false` olan ağırlıklı eleman dilimini tam doldurmadığında artan boşluk satır sonuna itilerek butonların sağında ~288 dp'lik boşluk bırakıyor ve aksiyon butonlarını ortada "havada" bırakıyordu.
- **Düzgün Hizalama:** Hap buton artık ağırlığı tam dolduran sarmalayıcı bir `Row` içine alındı; böylece istatistik ve bildirim aksiyon butonları ekranın sağ köşe dolgusuna (padding) tam yaslanmış olarak konumlanır.

---

#### 🎨 2. İkon Yol Kaybı (Path Loss) Düzeltmesi (`KitsugiIcons`)
- **İkonların "Tek Çubuk / Tek Çentik" Görünmesi Çözüldü:** `KitsugiIcons` içindeki `ImageVector` üretim mekanizmasında tek bir `PathParser` örneği tüm yollar için yeniden kullanılıyordu. `PathParser.parsePathString()` dâhilî düğüm listesini temizleyip aynı referansı döndürdüğü için, çok parçalı ikonların tüm yolları son parçanın düğümlerine işaret ediyordu (saat/geçmiş = tek dikey çubuk, indirme = tek "v", bilgi = tek nokta).
- **Eksiksiz Çizim:** Artık her yol parçası için bağımsız taze parser ve kopyalanmış düğüm listesi (`.toList()`) kullanılarak tüm çizgi ikonların eksiksiz ve doğru çizilmesi sağlandı.

---

[EN]
### 🚀 Improvements & Bug Fixes

This release resolves the **Profile Screen Top Bar Alignment** and fixes the **Multi-Path Vector Icon Rendering Bug (Path Loss)**!

---

#### 📱 1. Profile Top Bar Right-Alignment Fix
- **Fixed Floating Action Buttons:** Previously, `ProfileSourceSelectorPill` used `weight(1f, fill = false)` alongside an extra `Spacer(weight(1f))`. Under Jetpack Compose's row measure policy, the unfilled portion of the weighted slot was not redistributed, causing ~288 dp of trailing whitespace that pushed action buttons inward.
- **Accurate Edge Alignment:** The source selector pill is now enclosed in a weight-filling wrapper `Row`, ensuring action buttons (Statistics & Notifications) firmly anchor to the far right edge adjacent to screen padding.

---

#### 🎨 2. Vector Icon Path Loss Fix (`KitsugiIcons`)
- **Restored Complete Multi-Path Icons:** The `ImageVector` builder in `KitsugiIcons` was reusing a single `PathParser` instance across multiple path elements. Because `PathParser` mutates and returns the same internal node buffer, all previous paths in a composite icon were overwritten by the final path (causing clock to look like a single vertical stroke, download like a single arrow notch, info like a dot, etc.).
- **Independent Node Parsing:** Each path now uses a dedicated parser instance and copies its node list (`.toList()`), fully restoring the visual fidelity of all vector line icons.
