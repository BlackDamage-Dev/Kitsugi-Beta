# Kitsugi Beta v2.4.456 - Manga Keşfet Ekranı Tema Uyumu

Bu sürümde Manga Keşfet ekranı (`MangaBrowseScreen`) ve TV türevi (`TvMangaBrowseScreen`), uygulamanın semantik renk paleti (`KitsugiColors`) ve dinamik vurgu sistemi (`LocalKitsugiAccent`) ile tam görsel uyuma kavuşturulmuştur. Mevcut çok adlı yerel arama ve dil özellikleri eksiksiz korunmuştur.

---

### 🎨 Görsel ve Tema Geliştirmeleri
- **Kararlı Pastel Kaynak Renkleri (`mangaSourceTint`)**: Her manga eklentisi için kararlı bir pastel renk türetilerek kaynak çipinde, sonuç kartı rozetinde ve yükleme iskeletinde tutarlı bir görsel kimlik sağlandı.
- **Medya Kartı Standardizasyonu**:
  - **Telefon Dikey Mod:** 2:3 en/boy oranında poster, 20 dp yuvarlak köşe, `kitsugiNeonGlow` neon çerçevesi, tema `Surface` zemini ve altta 66 dp sabit başlık + kaynak satırı.
  - **Tablet / Yatay Mod:** Uygulamanın "Tümünü Gör" ızgaralarıyla uyumlu, kompakt poster + başlık satırı (ekran genişliğine göre 130/145/160 dp).
- **Modern Shimmer & İskelet Yükleme**: Elle yazılmış animasyonlar yerine uygulamanın ortak `KitsugiShimmerProvider` ve `KitsugiShimmerBlock` bileşenleri kullanıldı; iskelet kartlar gerçek kartlarla birebir aynı ölçülere ve kaynak rengine kavuşturuldu.
- **Dinamik Aurora Arka Plan**: İlk sonucun kapağı arka planda hafifçe belirirken, seçili tema vurgu renginden yumuşak bir aurora geçişi uygulandı.
- **Kaynak Çipleri Yenilendi**: 16 dp köşe, `Surface` zemin, durum noktası, ayrı sayı rozeti ve hata durumunda `AccentRed` + hata kodu hapı (`KTS-SRC-…`).
- **Boş Durumlar & Yönlendirme**: Arama sonucu bulunamadığında ortak `KitsugiEmptyState`; eklenti bulunmadığında ise gradyan ikonlu ve `KitsugiButton` butonlu premium boş durum kartı.

---

### 📺 Android TV Uyumu (`TvMangaBrowseScreen`)
- TV ekranındaki sabit renkler (`AccentBlue`, `Color.White`, `Color.Black`), uygulamanın dinamik semantik tokenları (`KitsugiColors.Accent`, `KitsugiColors.Scrim`, `KitsugiColors.TextPrimary`, `KitsugiColors.Surface`) ile değiştirildi.
- Yerel CBZ/CBR ve PDF açıcı düğmeleri korundu.

---

### 🌐 Çok Dilli Arama & Özellik Koruması
- AniList çok dilli arama planı ("Yerel / Orijinal ad" modu), denenen başlık sayıları ve detaylı arama bilgisi diyaloğu (`MangaSearchInfoDialog`) hiçbir kayıp olmadan korundu.
- 9 yeni keşfet ve sıralama etiketi hem Türkçe hem İngilizce dil kaynaklarına eklendi.
