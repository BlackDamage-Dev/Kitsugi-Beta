# Kitsugi Beta v2.4.468 — Evrensel Video ve Ses İndirme Motoru & Otomatik Kodek Dönüştürme (AAC-LC / M4A)

Bu sürümde, tüm video eklentileri ve akış kaynakları için evrensel video ve bağımsız ses indirme motoru, uyumsuz/çok kanallı ses kodeklerini evrensel AAC-LC (.m4a) biçimine otomatik dönüştürme ve İndirilenler ekranında Sesler sekmesi eklendi.

---

### 🎧 1. %100 Eklenti ve Akış Kaynağı Kapsaması
- **Desteklenen Tüm Akış Türleri:** CloudStream3 ve Kitsugi-Plugins eklentileri, WebView sniffer, Stremio Debrid & P2P Torrent, YouTube / GoogleVideo, HLS (`.m3u8`), MPEG-DASH (`.mpd`) ve uzak konteynerler (`MKV`, `MP4`, `WebM`, `TS`, `FLV`, `AVI`).
- **Ayrık & Gömülü Ses Eşleştirme:** Master ve varyant playlist eşleştirmesi (`X-Kitsugi-Master-Url`), `#EXT-X-MEDIA:TYPE=AUDIO` ayrık ses parçaları veya doğrudan gömülü sesler tek bir evrensel video dosyasında birleştirilir; sessiz video oluşması engellenir.
- **Akış Biçimi Koklayıcı (`StreamFormatSniffer`):** URL uzantısına bağımlı kalmadan ilk bayt imzaları (magic bytes) ile tespit; sahte PNG/JPEG/GIF/BMP başlıkları ardına gizlenmiş 188-bayt MPEG-TS parçalarını temizleme (`sanitizeDisguisedTsSegment`) ve Packed Audio ID3v2 etiketlerini ayıklama (`stripLeadingId3Tags`).
- **CDN ve Güvenlik Uyumluluğu:** Cloudflare çerezleri, IPv4-öncelikli DNS, CloseLoad/Rapidrame için otomatik `Origin` ve `googlevideo.com` 10 MB parça indirme desteği.

---

### 🔄 2. Otomatik Evrensel Ses Kodek Dönüştürücü (`AAC-LC` / `.m4a`)
- **Uyumsuz Kodekleri Otomatik Dönüştürme:** Cihazların donanımsal lisans veya oynatıcı kısıtları nedeniyle açamadığı `E-AC-3` (Dolby Digital Plus / Atmos), `AC-3`, `DTS` / `DTS-HD`, `TrueHD` / `MLP`, `Opus`, `Vorbis`, `FLAC`, `ALAC`, `PCM` / `WAV`, `MP2`, `WMA`, `AMR` ses akışları arka planda otomatik olarak evrensel **Stereo/Mono `AAC-LC (.m4a)`** formatına dönüştürülür.
- **FFmpeg Çözücü Köprüsü (`KitsugiFfmpegAudioBridge`):** Dahili `lib-decoder-ffmpeg-release.aar` (`libffmpegJNI.so`) üzerinden üretici (OEM) lisansına bağımlı olmadan tüm tescilli ve kayıpsız ses formatlarını saf 16-bit PCM akışına çözer.
- **5.1 / 7.1 Surround Downmix (ITU-R BS.775):** Çok kanallı sinema seslerinde konuşma/diyalog (`Center`) kanalını kaybetmeden dengeli stereo sese dönüştürür; 96/192 kHz yüksek örnekleme hızlarını 48 kHz'e normalize eder.
- **Kayıpsız Doğrudan Koruma:** Zaten standart olan 1–2 kanallı `AAC-LC` ve `MP3` ses akışları işlemci gücü ve süre harcamadan doğrudan kopyalanır.

---

### 📥 3. Bağımsız Ses İndirme & "Sesler" Yönetim Sekmesi
- **Tek Dokunuşla Ses İndirme (🎧):** Hem akış seçim ekranında (`StreamCard`) hem de tam ekran video oynatıcının `Ses Parçaları` panelinde (`AudioTracksSheet`) bağımsız ses indirme butonu. Çok dilli akışlarda istenen dil seçilebilir.
- **İndirilenler Ekranı — 4. Sekme: "Sesler":** İndirilen sesleri dahili oynatıcıda dinleme, harici uygulamalarla paylaşma, duraklatma/devam ettirme ve silme desteği.
- **Depolama Yönetimi:** `Download/Kitsugi/Audio` dizininde düzenli saklama, Depolama Ayarları ekranında bağımsız ses boyutu hesaplama ve MediaStore senkronizasyonu.

---

### 🌐 4. Uzak Konteyner Ön İnceleme & P2P Torrent İndirme
- **Hafif Konteyner Taraması:** Uzak `MKV`, `MP4`, `WebM`, `TS`, `FLV`, `AVI` dosyalarında tüm videoyu indirmeden HTTP Range sorgusu ile ses kanallarını ve dillerini keşfeder (`AudioTrackInspector`).
- **P2P Arka Plan Akışı:** P2P torrent kaynaklarında ses indirme başlatıldığında `P2pStreamingEngine` üzerinden geçici yerel akış açar ve indirme bittiğinde kaynakları güvenle serbest bırakır.
