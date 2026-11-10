# Kitsugi Beta v2.4.372

[English notes below]

---

## 🇹🇷 Türkçe Sürüm Notları

### 1. Manga Eklentilerini Toplu Silme
- **Seçim & Toplu İşlem Modu (`MangaExtensionTab` & `MangaViewModel`):** Yüklü manga eklentileri sekmesine "Toplu sil" modu eklendi. Kullanıcılar onay kutularıyla tek tek veya "Tümünü seç / bırak" butonuyla tüm eklentileri seçebilir.
- **Onay Penceresi ve Güvenlik:** Seçilen eklentileri kaldırmadan önce silinecek eklenti sayısını gösteren açık bir onay penceresi açılır. Aynı APK paketine bağlı tüm alt kaynaklar birlikte güvenle kaldırılır; yerleşik kaynaklar silinmez.
- **Detaylı Raporlama:** Silme işlemi arka planda sırayla gerçekleştirilir; işlem sonunda başarısız olan paket sayısı ekranda bildirilir.

### 2. Oynatıcıda Video Kalitesi ve Sağlayıcı Kaynakları Ayrımı
- **Ayrı Oynatıcı Butonları (`TopRightPlayerControls`):** Oynatıcı sağ üst menüsünde **Kalite** (yüksek kalite ikonu) ve **Kaynaklar** (video kütüphanesi ikonu) olarak iki ayrı buton yerleştirildi.
- **Gerçek Video Parçaları (`VideoQualitySheet` & `Media3PlayerEngine`):** "Kalite" menüsü yalnızca o anda oynatılan videonun Media3 tarafından desteklenen gerçek çözünürlüklerini (1080p, 720p vb., bitrate bilgisi ile) ve "Otomatik" adaptif seçeneğini listeler. Video URL'si veya sağlayıcı yeniden yüklenmeden kalite anında uygulanır.
- **Bağımsız Akış Kaynakları (`SourcesSheet`):** "Kaynaklar" penceresi eklentilerden gelen farklı video sağlayıcılarını ve alternatif bağlantıları listeler. Hata kurtarma ve alternatif kaynak arama akışları bu pencereye bağlandı.
- **Override Temizleme:** Bölüm veya kaynak değiştirildiğinde eski videodan kalan kalite override'ı otomatik temizlenir.

### 3. Rapidrame / Close HLS Çözücü İyileştirmesi
- **Adaptif Master & Çoklu Kalite (`KitsugiCdnJwExtractor`):** HLS master listelerindeki tüm kalite varyantları korunarak tekdüze sahte "1080p" etiketi kaldırıldı; çözünürlük bilgileri gerçek master playlist'ten okunur.

### 4. Cihaz Uyumluluk Notu
- Bu proje `minSdkVersion = 26` (Android 8.0 ve üzeri) ve ARM 32/64 bit mimarileri destekler. Eski Android sürümlerinde paket ayrıştırma hatası alınabilir.

---

## 🇬🇧 English Release Notes

### 1. Manga Bulk Extension Uninstall
- **Batch Selection Mode (`MangaExtensionTab` & `MangaViewModel`):** Added a "Bulk delete" mode to the installed manga extensions tab with checkboxes and a "Select / deselect all" toggle.
- **Confirmation & Safety Guard:** A confirmation dialog displays the exact count of extensions to be removed. All sources belonging to the same APK package are removed together; built-in sources are strictly protected.
- **Execution & Feedback:** Deletions are processed sequentially on an IO dispatcher, reporting the count of any failed removals.

### 2. Player Video Quality vs. Stream Sources Separation
- **Distinct Top-Right Controls (`TopRightPlayerControls`):** Separated the controls into **Quality** (HighQuality icon) and **Sources** (VideoLibrary icon).
- **Native Video Tracks (`VideoQualitySheet` & `Media3PlayerEngine`):** The Quality sheet inspects and displays only the true video renditions of the currently loaded video (resolution and bitrate) alongside an "Automatic" adaptive option. Tracks switch instantly without reloading the URL or provider.
- **Provider Sources (`SourcesSheet`):** The Sources sheet exclusively manages alternate provider streams. Stream fallback and error recovery open the Sources sheet.
- **Track State Hygiene:** Switching sources or episodes automatically clears previous video track overrides.

### 3. Rapidrame / Close Extractor Enhancement
- **Preserved HLS Master & Renditions (`KitsugiCdnJwExtractor`):** Standard HLS master playlists preserve adaptive streaming without fabricating a 1080p label when the stream is lower resolution or multi-variant.

### 4. Device Compatibility
- The application requires Android 8.0 (API level 26) or newer and 32/64-bit ARM processors.
