# Kitsugi Beta v2.4.469 — Manga Keşfet Yumuşak Kayan Başlık, Kısayol Aç/Kapa Politikası & Hızlı Parlaklık Şeridi

Bu sürümde, Manga Keşfet sayfasındaki başlık kaydırma akışı tek eksenli ızgara mimarisine kavuşturuldu, okuyucuda panelleri açan kısayol tuşlarıyla kapatma (toggle) desteği eklendi ve alt çubuğa tek dokunuşla açılan hızlı parlaklık şeridi entegre edildi.

---

### 🌸 1. Manga Keşfet — Kaydırmayla Birlikte Akan Başlık & Yapışkan Kaynak Çubuğu
- **Tek Eksenli Akıcı Düzen:** Üst blok (üst bar + arama + sıralama + auth bandı) artık `LazyVerticalGrid`'in 0. öğesi olarak içerikle birlikte parmak hareketine birebir senkronize akar. Başlık bittiğinde listenin zıplaması ya da başlığın geri gelmemesi sorunu kökten çözüldü.
- **Yumuşak Sabitlenen Çip Çubuğu:** Kaynak çipleri başlığın ölçülen konumuna göre kayar; başlık bittiğinde ekranın tepesinde sabitlenir (`offset` layout fazında okunur, kaydırma sırasında gereksiz recomposition ve kasma oluşmaz).
- **Z-Index Katmanlı Zemin:** Tam genişlik zemin gradyanı `zIndex(-1f)` ile ızgara altında çalışır; kenar boşluklarında iz bırakmaz ve başlık yukarı kayarken içerik %35, perde %25 yumuşakça solar.
- **Temizleme:** Kaydırma hareketini yutan eski `MangaHideOnScrollConnection` kaldırıldı.

---

### ⌨️ 2. Manga Okuyucu — "Açtığın Tuş, Kapatır" (Aç/Kapa Paneller)
- **Kısayol ile Kapatma:**
  - `S` → Okuyucu Ayarları aç / kapat
  - `C` → Bölümler panelini aç / kapat
  - `G` → Sayfaya Git kutusunu aç / kapat
  - `H` / `?` / `F1` → Klavye Kısayolları panelini aç / kapat
- **Güvenli Odak Koruması:** Panel açıkken yalnızca onu açan tuş paneli kapatır. Diğer tuşlar, sayı alanları veya sürgüler klavye odağını kaybetmez.
- **Kısayollar Bilgilendirmesi:** Kısayollar panelinin en üstüne aç/kapa kuralını belirten açıklama metni eklendi (TR + EN tam parite).
- **Regresyon Koruması:** `MangaReaderRegressionCases` altına panel aç/kapa davranışı için birim testleri eklendi.

---

### ☀️ 3. Manga Okuyucu — Alt Çubuk Hızlı Parlaklık Şeridi
- **Hızlı Erişim Düğmesi:** Alt kontrol çubuğuna parlaklık butonu eklendi; tıklandığında `expandVertically + fadeIn` ve `shrinkVertically + fadeOut` animasyonlarıyla yumuşakça açılan şerit gelir.
- **Zengin Kontroller:** Düşük parlaklık ikonu → kaydırılabilir sürgü (`%10..%100`) → yüzde göstergesi → tek dokunuşla `%100`'e sıfırlama butonu (Restart) → yüksek parlaklık ikonu.
- **Otomatik Kapanma:** Kontroller veya bir panel kapandığında şerit kendiliğinden gizlenir.
- **Tam Senkronizasyon:** Ayarlar panelindeki "Parlaklık" ayarı ile aynı durumu paylaşır; her iki taraftan yapılan değişiklik anında yansır.
