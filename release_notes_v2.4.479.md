# Kitsugi Beta v2.4.479 — İndirme Hattı Dayanıklılığı (T-IND), SegmentJournal ve DoH Fallback

[TR]
### 🚀 Yenilikler ve İyileştirmeler (v2.4.479)
- **T-IND-1 (Rotasyonlu CDN Host Kurtarma & DoH Fallback):**
  - HDFilmCehennemi, FilmMakinesi ve benzeri kaynaklarda rotasyonlu CDN sunucuları (`srvN.cdnimagesNNNN.cyou`, `sNNN.rapidrame.com`) kuyrukta beklerken düştüğünde (`UnknownHostException` / 404), indirme durmak yerine `DownloadStreamResolver` ile embed kaynağından otomatik olarak taze canlı akış URL'si alır ve kaldığı dilimden devam eder.
  - Türk ISS'lerinde `.cyou` alan adlarında yaşanan NXDOMAIN DNS engellerine karşı `IPv4FirstDns` içine DoH (DNS over HTTPS) yedek zinciri (Cloudflare → Google) eklendi.
  - Kaynak HTTP 429 hız sınırı döndüğünde denemeler `DownloadResilience` politikasına (250ms segment pacing + Retry-After + exponential backoff + full jitter) göre yönetilir; 9x retry patlaması önlendi (`downloadClient`).
- **T-IND-2 (Çökmeye ve Kesintiye Dayanıklı Segment Defteri — SegmentJournal):**
  - Resuming sırasında TS zaman damgası geri sarmasını ve oynatıcılarda (MX Player / ExoPlayer) videonun donup kasmasını engelleyen `.jl` segment defteri eklendi.
  - Her segment diske yazılıp flush edildikten sonra atomik olarak deftere işlenir; yarım kalan dilimler otomatik kırpılır.
  - Bölüm silinirken `.jl` defteri de otomatik olarak temizlenir (`DownloadExportNaming`).
- **T-IND-3 (Standart HLS BYTERANGE & AES-128 Şifre Çözümü):**
  - `#EXT-X-BYTERANGE` desteklenerek tek dosyalı aralık akışlarının mükerrer tam indirilmesi engellendi.
  - `#EXT-X-KEY` (AES-128) şifreli HLS segmentleri indirme sırasında otomatik çözülür. DRM (SAMPLE-AES / Widevine) akışları ise diske bozuk dosya yazılmadan temiz ve net hata mesajıyla kullanıcıya bildirilir.
- **Doğrulama ve Cihaz Uyumluluğu:**
  - `DeviceCodecSupport` ve `DownloadVerifier` motoru korunarak dosya tamamlandığında oynatılabilirlik doğrulanır.
  - Tüm indirme alt sistem testleri (`DownloadResilienceTest`, `SegmentJournalTest`, `HlsPlaylistParserTest`, `DeviceCompatibilitySelectionTest`) 80/80 başarılı.

---

[EN]
### 🚀 What's New & Improvements (v2.4.479)
- **T-IND-1 (Rotating CDN Host Recovery & DoH Fallback):**
  - Seamless stream re-resolution via `DownloadStreamResolver` when rotating CDN hosts (`srvN.cdnimagesNNNN.cyou`, `sNNN.rapidrame.com`) expire while in queue.
  - Added DoH (DNS over HTTPS) fallback chain (Cloudflare → Google) in `IPv4FirstDns` to bypass NXDOMAIN filtering on Turkish ISPs.
  - Segment pacing (250ms), Retry-After parsing, and exponential backoff with full jitter to eliminate HTTP 429 cascade loops via dedicated `downloadClient`.
- **T-IND-2 (Crash-Proof Segment Journaling — SegmentJournal):**
  - Introduced `.jl` journal file tracking to guarantee exact resume alignment and eliminate stuttering/freezing video playback caused by duplicated MPEG-TS segments.
  - Partial segment corruption trimmed automatically upon resume.
  - Journal files cleanly purged when downloads are deleted (`DownloadExportNaming`).
- **T-IND-3 (Standard HLS BYTERANGE & AES-128 Decryption):**
  - Full support for `#EXT-X-BYTERANGE` range requests and `#EXT-X-MAP` initialization segments.
  - Transparent `#EXT-X-KEY` (AES-128) decryption with key caching and sequence-derived IV.
  - Clean error reporting for unsupported DRM (SAMPLE-AES / Widevine) streams.
- **Verification & Compatibility:**
  - Integrated `DownloadVerifier` ensuring downloaded media is playable with device hardware decoders.
  - 100% test pass rate (80/80 tests) across download resilience test suite.
