# Kitsugi Beta v2.4.477 — HLS İndirme 429 Hız Sınırı Çözümü, Cihaz Uyumlu Format & Doğrulama

[English description below]

---

## 🇹🇷 Türkçe Sürüm Notları

Bu sürümde, video indirme (çevrimdışı izleme) motorunda yaşanan **HTTPS 429 (Too Many Requests)** kaynaklı indirme kesilmeleri giderilmiş, **cihazın donanımsal/yazılımsal olarak oynatamayacağı video ve ses formatlarının indirilmesini engelleyen akıllı varyant seçimi** ve indirme sonrası **kapsayıcı/kodek doğrulama** sistemi eklenmiştir.

### 🚀 1. HLS İndirmelerinde HTTP 429 (Hız Sınırı) Kök Çözümü
- **Uyarlanabilir İstek Aralığı (`HlsDownloadPolicy` & `HlsSegmentFetcher`):** HDFilmCehennemi, FilmMakinesi ve benzeri kaynakların kullandığı Close/Rapid CDN ailelerinde segment istekleri arasındaki minimum bekleme süresi düzenlendi.
- **Sunucu `Retry-After` ve Üstel Geri Çekilme (Exponential Backoff):** Sunucudan 429 (veya 503/509) hız sınırı yanıtı geldiğinde sunucunun `Retry-After` başlığına (saniye veya HTTP tarihi) tam uyulur. Başlık yoksa rastgele gecikmeli (jitter) üstel geri çekilme uygulanır.
- **Kesintisiz Devam (Safe Resume):** İndirme hız sınırı veya geçici ağ kopması nedeniyle beklemeye alındığında indirilen segmentler korunur; indirme baştan başlamaz, kaldığı yerden güvenle sürdürülür.
- **Otomatik Yeniden Deneme & Öncelikli Servis:** 429 alındığında indirme işi iptal edilmez; 5sn → 20sn → 60sn artan aralıklarla kuyrukta bekletilir ve servis uyanık kalarak zamanı gelince indirmeyi kendiliğinden sürdürür.

### 📱 2. Cihazın Desteklediği Formatta İndirme (`DeviceCodecSupport`)
- **Donanımsal / Yazılımsal Çözücü Taraması (`MediaCodecList`):** Cihazın sistem çözücüleri taranarak H.264 (AVC), HEVC (H.265), AV1, VP9, AAC, AC-3, E-AC-3 ve Opus format destekleri tespit edilir ve önbelleğe alınır.
- **Uyumsuz Varyantların Ön Elenmesi:** HLS master playlist taranırken cihazın donanımında bulunmayan (örneğin AV1 veya desteklenmeyen çok kanallı ses) varyantlar otomatik olarak elenir; cihazın sorunsuz oynatabileceği en yüksek kaliteli varyant seçilir.
- **Doğru Konteyner Uzantısı:** Akışın gerçek kapsayıcısına göre `.mp4`, `.ts` veya `.m4a` uzantısı belirlenir.

### 🛡️ 3. İndirme Sonrası Oynatılabilirlik Doğrulaması (`DownloadVerifier`)
- **MediaExtractor ile Başlık Doğrulaması:** İndirme tamamlandığında dosya oynatılabilirlik açısından hızlıca taranır; bozuk, boş veya cihazın oynatamayacağı akışlar "başarılı" olarak işaretlenmez.
- **Net Hata ve Uyarı Mesajları:** Oynatılamayan dosyalarda kullanıcıya genel "Hata oluştu" yerine hangi kodek veya biçimin desteklenmediği açıkça bildirilir.

### ⏱️ 4. İndirilenler Ekranı & Bildirim İyileştirmeleri
- **Geri Sayım Göstergesi:** Hız sınırı nedeniyle beklemeye alınan indirmelerde mor renkli canlı geri sayım göstergesi ("Kaynak yavaşlattı — X sn sonra devam edecek").
- **Ayrıntılı Hata Açıklamaları:** Hata alan indirmelerde hatanın gerçek nedeni (hız sınırı, disk dolu, erişim engeli vb.) kart üzerinde gösterilir.
- **Bekleme Bildirimi:** Bildirim çubuğunda "⏳ Kaynak hız sınırı, X sn sonra devam edecek" bilgilendirmesi.

---

## 🇬🇧 English Release Notes

This release resolves the **HTTP 429 (Too Many Requests)** download stalls in the offline video download subsystem, introduces **smart hardware-aware codec variant selection (`DeviceCodecSupport`)** to prevent downloading unplayable media formats, and adds **post-download playability verification (`DownloadVerifier`)**.

### 🚀 1. HTTP 429 (Rate Limit) Mitigation for HLS Downloads
- **Adaptive Request Intervals (`HlsDownloadPolicy` & `HlsSegmentFetcher`):** Enforces host-level request rate pacing tuned specifically for strict streaming CDNs (such as Close/Rapid CDN families).
- **Retry-After Header & Exponential Backoff with Jitter:** When HTTP 429/503/509 is encountered, the server's `Retry-After` header is strictly parsed and respected; otherwise, exponential backoff with full jitter is applied.
- **Safe Resumption:** When throttled or interrupted, partially downloaded data is safely preserved and resumed from where it left off rather than starting over.
- **Intelligent Auto-Retry Service:** Rate-limited downloads are temporarily scheduled (5s → 20s → 60s) instead of failing immediately; the download service automatically resumes once the cooldown expires.

### 📱 2. Hardware-Aware Codec Variant Selection (`DeviceCodecSupport`)
- **System Codec Inspection (`MediaCodecList`):** Probes device capabilities for H.264 (AVC), HEVC (H.265), AV1, VP9, AAC, AC-3, E-AC-3, and Opus decoding.
- **Pre-filtering Incompatible Variants:** HLS master playlists are filtered against device codec capabilities, ensuring unsupported formats (e.g. AV1 or unsupported multichannel audio codecs) are bypassed in favor of the highest quality supported stream.
- **Precise Container Extensions:** Correct container extensions (`.mp4`, `.ts`, `.m4a`) are assigned based on stream structure.

### 🛡️ 3. Post-Download Playability Verification (`DownloadVerifier`)
- **Fast Header Inspection via `MediaExtractor`:** Verifies audio and video tracks upon download completion; corrupt or unplayable files are not marked as completed.
- **Actionable Error Feedback:** Replaces vague generic error messages with specific diagnostics on unsupported codecs or container defects.

### ⏱️ 4. Downloads Screen & Notification UX
- **Live Retry Countdown:** Visual timer for throttled downloads ("Source throttled — resuming in X s").
- **Descriptive Error Cards:** Explains exact failure reasons (rate limit, storage full, 403 expired link) directly on download items.
- **Ongoing Throttle Notification:** Displays clean notification updates while waiting for rate-limit cooldown.
