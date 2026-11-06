# Kitsugi Beta v2.4.449

## 🇹🇷 Türkçe Güncelleme Notları

### 1. Manga Bölüm İndirmeleri & Arka Plan Kuyruk Yöneticisi (`MangaDownloadManager`)
- **Eklentiye Özel Güvenli İndirme Hattı**:
  - Manga bölümleri indirilirken eklentinin kendi `getImage` akışı, özel `Referer` başlıkları, oturum çerezleri ve ağ kesicileri birebir korunur.
  - Manga ve bölüm klasörleri otomatik olarak oluşturulur. Sayfalar sıfır dolgulu ve sıralı olarak adlandırılır (`<Manga Adı>_Bölüm_<Numara>_Sayfa_<0001>.png`).
  - İndirilen içerikler genel resimlerden ayrı olarak `Pictures/Kitsugi/Manga/` dizininde düzenli olarak saklanır.
  - Android WorkManager tabanlı `MangaDownloadWorker` sayesinde uygulama kapatılsa dahi indirmeler arka planda kaldığı yerden devam eder; duraklatma, devam etme ve eksik sayfaları yeniden indirme desteği mevcuttur.

### 2. Format & Bellek Güvenliği (`MangaPageExport`)
- **Gerçek Format Dönüşümü**:
  - Doğrudan kullanılabilen JPEG, PNG ve GIF dosyaları orijinal halleriyle korunur.
  - WebP ve cihaz tarafından çözümlenebilen diğer tüm biçimler gerçek PNG formatına dönüştürülerek depolanır.
  - Sahte uzantılı HTML veya bozuk veriler reddedilir; bozuk içeriklerin sahte `.jpg` adıyla başarılı gösterilmesi engellenir.

### 3. İndirmeler → Manga Sekmesi & Çevrimdışı Okuma (`DownloadedMangaSource`)
- **İndirmeler Ekranında 5. Sekme ("Manga")**:
  - İndirilen manga bölümleri için canlı ilerleme, duraklatma/devam ettirme, eksik sayfaları yeniden indirme, manga klasörünü dosya yöneticisinde açma ve bölüm silme işlemleri eklendi.
  - Genel indirmeler özet kartına Manga sayaçları ve depolama boyutu hapı entegre edildi.
  - Tamamlanan bölümler eklentiye veya internete ihtiyaç duymadan doğrudan `DownloadedMangaSource` üzerinden mevcut manga okuyucusunda açılır.
  - Okuyucu önbelleğini temizlemek kalıcı indirmeleri asla silmez.

### 4. Keiyoushi, Kotatsu & Mangitto Uyumluluğu (`extensionMemo` Köprüsü)
- **Bölüm Bilgisi Kaybı Önleme**:
  - Mangitto ve modern Madara kaynaklarının ihtiyaç duyduğu bölüm kimlikleri ve meta verileri `MangaChapter.extensionMemo` köprüsüyle korunur.
  - Bölüm indirme ve sayfa listesi getirme sırasında eklentinin beklediği `memo` verisi eksiksiz aktarılır.

### 5. Okuyucu Sistem Barları & Otomatik Tam Ekran (`MangaReaderSystemBars`)
- **Akıllı Sistem Barı Yönetimi**:
  - Okuyucuda üst ve alt barlar kapandığında Android sistem barları (durum çubuğu ve navigasyon çubuğu) akıcı şekilde gizlenir.
  - Okuyucu ayarlarına "Okuyucuyu tam ekran başlat" seçeneği (`manga_auto_fullscreen`) eklendi; üst barda tek dokunuşla tam ekrana geçiş / tam ekrandan çıkış düğmesi yerleştirildi.
  - Dikey sayfalama modunda gerçek `VerticalPager` ve üst/alt dokunma bölgeleriyle akıcı gezinme sağlandı.

### 6. Mimari ve Kod Kalitesi
- **Zero Material Icons**:
  - `app/src/main` genelinde `androidx.compose.material.icons.*` sıfırlandı; %100 `KitsugiIcons as Icons` standardı uygulandı.
- **%100 Dil Simetrisi**:
  - Türkçe ve İngilizce dillerinde tüm manga indirme ve okuyucu dizgeleri birebir simetrik olarak eşitlendi.
- **Test ve Performans**:
  - Sekme performans koruyucusu (`kitsugi_pager_perf_guard.py`) ve birim testleri başarıyla doğrulandı.

---

## 🇬🇧 English Release Notes

### 1. Manga Chapter Downloads & WorkManager Pipeline (`MangaDownloadManager`)
- **Extension-Specific Resilient Download Stream**:
  - Manga chapter downloads preserve extension-specific `getImage` requests, custom referer headers, cookies, and network interceptors.
  - Structured folders created automatically under `Pictures/Kitsugi/Manga/` with zero-padded ordered pages (`<Manga Title>_Bölüm_<Number>_Sayfa_<0001>.png`).
  - Durable Android WorkManager background service (`MangaDownloadWorker`) allows resumable downloads even if the application is closed, with pause, resume, and redownload capabilities.

### 2. Format & Memory Safety (`MangaPageExport`)
- **True Image Conversion**:
  - Valid JPEG, PNG, and GIF formats are preserved directly.
  - WebP and other platform-decodable formats are converted into authentic PNG images.
  - Corrupted responses and HTML error pages masquerading as `.jpg` are rejected.

### 3. Downloads → Manga Tab & Offline Reader (`DownloadedMangaSource`)
- **Dedicated 5th "Manga" Tab**:
  - Full chapter download management: live progress, pause/resume, redownload missing pages, open directory in system file manager, and chapter deletion.
  - Downloads storage overview card updated with dedicated Manga count and storage pill.
  - Completed chapters launch seamlessly inside the offline reader (`DownloadedMangaSource`) without requiring internet connectivity or active extensions.
  - Clearing reader cache never evicts durable chapter downloads.

### 4. Keiyoushi, Kotatsu & Mangitto Compatibility (`extensionMemo` Bridge)
- **Preserved Chapter Metadata**:
  - Retains chapter IDs and metadata required by Mangitto and modern Madara sources via `MangaChapter.extensionMemo`.
  - Seamless round-trip conversion between domain models and extension chapter identities.

### 5. Reader System Bars & Auto Fullscreen (`MangaReaderSystemBars`)
- **Seamless Immersive Experience**:
  - Automatically hides status and navigation bars when reader controls are dismissed.
  - Added "Start reader in fullscreen" preference (`manga_auto_fullscreen`) and instant top-bar fullscreen toggle.
  - Restored real `VerticalPager` with top/bottom tap regions for vertical reading mode.

### 6. Architecture & Localization
- **Zero Material Icons**:
  - Zero imports of `androidx.compose.material.icons.*` across `app/src/main`, fully standardized on `KitsugiIcons as Icons`.
- **100% Localization Parity**:
  - Turkish and English language resources synchronized with zero missing keys.
- **Verified Stability**:
  - All unit tests and pager performance guards validated.
