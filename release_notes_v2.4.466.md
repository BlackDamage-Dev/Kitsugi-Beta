# Kitsugi Beta v2.4.466 — Ortam Sesi (Ambient) Düzeltmeleri ve Geliştirmeleri

Bu sürümde manga okuyucu içindeki **Ortam Sesi (Ambient Audio)** modülünün tüm akış ve kararlılık sorunları giderildi, panel ergonomisi yeniden tasarlandı ve indirilen seslerin genel depolama / İndirmeler alanında görünmesi sağlandı.

---

### 🎧 1. Kesintisiz Çevrimiçi Geçiş & Aday Sırası (`AmbientAutoplayStarter.kt`)
- **Sorun:** Çevrimiçi müzik açıkken önce cihaz içi ses başlıyor, parça bulununca cihaz içi ses hemen kapatılıyor (`stopAllChannels()`), ancak çevrimiçi akış asenkron yüklenirken bağlantı/kota/dosya hatası verirse oynatıcı sessiz kalıyor ve kullanıcı ortada müziksiz kalıyordu.
- **Çözüm:** *Önce hazırla, hazır olunca geç* modeli getirildi.
  - Cihaz içi atmosfer çalmaya devam ederken çevrimiçi aday sessizce (`volume = 0f`) hazırlanır.
  - Oynatma durumu `PLAYING` olana kadar (en fazla 12 sn) beklenir.
  - Çalma doğrulandığında cihaz içi sesler kapatılır ve çevrimiçi parçanın sesi açılır.
  - İlk aday açılamazsa aday kaldırılır ve sıradaki aday denenir (en fazla 3 aday).
  - Hiçbir aday açılamazsa yerel atmosfer kesintisiz çalmaya devam eder ve bilgi bandında durum kullanıcıya bildirilir (*"Çevrimiçi parça bulunamadı/açılamadı — cihaz içi ses devam ediyor"*).

---

### 🚪 2. Okuyucudan Çıkışta Müziğin Otomatik Durdurulması (`MangaReaderScreen.kt`)
- **Sorun:** Okuma ekranından çıkıldığında, geri basıldığında veya okuyucu kapatıldığında ortam sesi çalmaya devam ediyordu.
- **Çözüm:** Okuyucu kompozisyonunun ömrüne bağlı `DisposableEffect(Unit)` eklendi:
  - Okuyucudan çıkıldığında `musicEngine.stopAllChannels()` ve `AmbientPlaybackService.stop(context)` otomatik olarak tetiklenerek ses ve bildirim servisi anında sonlandırılır.
  - Aynı manga içinde bölüm geçişleri kompozisyonu bozmadığı için müzik kesintisiz devam eder.
  - Panel açılır penceresine `sheetGesturesEnabled = false` verilerek yatay kaydırma ile alttan çekip kapatma hareketinin çakışması engellendi.

---

### 🎛️ 3. Üst Bar Sabitlemesi & 8 Sekmeli Yatay Pager Düzeni (`MangaAmbientPanel.kt`)
- **Sabit Başlık ve Kontrol Şeridi:** Panel başlığı, çal/duraklat, sustur, süre ve kapat kısayolları ekranın üstünde sabit tutulur.
- **Ayrı Ayarlar Sekmesi:** Paneldeki anahtarlar (*Ortam sesi, Otomatik başlat, Sadece kulaklıkta, Sessiz modda çalma, Çevrimiçi kaynakları kullan*) "Senin İçin" sekmesini kalabalıklaştırmaktan çıkarılıp en sondaki yeni **Ayarlar** sekmesine taşındı.
- **8 Sekmeli HorizontalPager:** `Senin İçin`, `Ambiyans`, `Müzik`, `Çevrimiçi`, `Karışım`, `Profil`, `Süre` ve `Ayarlar` sekmeleri arasında parmakla sağa/sola akıcı geçiş yapabilme; sekme seçildiğinde otomatik yumuşak kaydırma.

---

### 📁 4. İndirilen Seslerin Görünürlüğü & Depolama Köprüsü (`AmbientDownloadManager.kt`)
- **Sorun:** İndirilen ortam sesleri yalnızca uygulamanın iç dizinine (`filesDir/ambient/`) hash adlarıyla kaydedildiği için `İndirmeler > Sesler` sekmesinde `Sesler (0)` görünüyordu.
- **Çözüm:**
  - İndirilen her ortam parçası `Download/Kitsugi/Audio/Kitsugi_Ortam_<Başlık> - <Alt başlık>.mp3` altına kopyalanarak Android `MediaScanner` ile sisteme taranır.
  - `ambient/exports.json` dizininde açık kopyalar takip edilir.
  - Ses silindiğinde veya tümü temizlendiğinde genel depolamadaki dosya da temizlenir.
