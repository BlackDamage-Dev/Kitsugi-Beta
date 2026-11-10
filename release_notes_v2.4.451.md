# Kitsugi Beta v2.4.451

## [TR] Türkçe Değişiklik Günlüğü

### 1. Oynatıcı Motor Bağımsız OSD ve Klavye Yönetimi
- **Motor Bağımsız `isPausedByUser` Sözleşmesi:** Media3 ve MPV oynatıcı motorları için duraklatma durumu standardize edildi. Media3'te ileri/geri sarma (`seek`) ve arabelleğe alma (`buffering`) esnasında oynatıcı kontrollerinin (OSD) kendiliğinden açılıp kapanma sorunu kökten çözüldü.
- **Sadece Durdur/Başlat ile OSD Kontrolü:** İleri/geri sarma, ses, parlaklık ve diğer kısayollardan OSD açma tetikleyicileri kaldırıldı (yalnızca hafif durum rozeti görünür). Oynatıcı kontrolleri artık yalnızca oynatmayı durdurduğunuzda açılır, devam ettirdiğinizde otomatik gizlenir.
- **Arayüz Açıkken Kesintisiz Kısayol Yakalama:** OSD kontrolleri veya alt paneller/pencereler açıkken klavye kısayollarının odak kaybı yaşamadan kesintisiz çalışması sağlandı. Açık olan alt pencereler aynı kısayol tuşuyla veya `Esc` ile temiz biçimde kapanır.
- **D-Pad / QWERTY Klavye Tanıma:** Fiziksel klavyelerin yön tuşları nedeniyle Android kumandası olarak yanlış sınıflandırılması düzeltildi.
- **`seekBy` Süre Koruması:** Akış başında toplam süre henüz bilinmiyorken (`dur <= 0L`) ileri sarmanın konumu sıfırlaması engellendi.

### 2. Birleşik Stremio Altyazı Ortaklık Protokolü (`StremioSubtitleRouting`)
- **Ortaklık Protokolü Entegrasyonu:** Tüm Stremio eklentileri (`OpenSubtitles v3`, `YTS Subtitles`, `AltyaziDB`, `TurkceAltyazi`, `AniSub.co` vb.) için kaynak tipleri ve kimlik önekleri (`tt`, `tmdb:`, `kitsu:`, özel önekler) tam bağlandı.
- **Otomatik Eklenti Tohumlama:** Veritabanında hiç altyazı eklentisi bulunmadığında varsayılan Stremio altyazı eklentileri otomatik olarak tohumlanır.
- **4-Segment ve 3-Segment Geri Dönüş Desteği:** Gerçek dosya adı veya hash varsa önce 4-segment URL, hata veya boş sonuçta 3-segment kanonik URL otomatik olarak denenir.
- **AniSub Tek Birleşik Sorgu:** Anime başlık varyantları tek sorguda birleştirildi, boş yanıt önbellek zehirlenmesi ve `CancellationException` yutulması giderildi.
- **Gelişmiş Altyazı Önbelleği ve Arşiv Desteği:** `IPv4FirstDns`, `.gz` ve `.zip` sıkıştırılmış altyazı paketlerini otomatik açma, P2P/HLS hash kontrolünde kilitlenmeleri önleme ve atomik dosya yazımı eklendi.
- **Binder Güvenliği:** Çok sayıda eklenti veya kaynak listelendiğinde oluşan `TransactionTooLargeException` çökmelerine karşı güvenli aktarım köprüsü eklendi.

---

## [EN] English Changelog

### 1. Engine-Independent OSD & Keyboard Management
- **Engine-Independent `isPausedByUser` Contract:** Standardized pause state detection across Media3 and MPV engines. Fixed issue where player controls (OSD) flickered open/closed during seeking and buffering in Media3.
- **OSD Controls on Play/Pause Only:** Removed intrusive OSD opening from seek, volume, brightness, and other shortcut actions (only a subtle status badge is shown). Player controls now show exclusively when playback is paused and hide when resumed.
- **Seamless Keyboard Shortcuts with OSD Open:** Keyboard shortcuts now work without interruption even when OSD or bottom sheets are open. Open sheets and dialogs cleanly toggle closed using the same shortcut key or `Esc`.
- **Keyboard vs D-Pad Detection:** Fixed classification of physical QWERTY keyboards carrying D-Pad source flags.
- **`seekBy` Safeguard:** Fixed seek behavior at stream startup when duration is not yet resolved (`dur <= 0L`).

### 2. Unified Stremio Subtitle Partnership Protocol (`StremioSubtitleRouting`)
- **Partnership Protocol Integration:** Fully integrated resource types and ID prefix matching (`tt`, `tmdb:`, `kitsu:`, custom prefixes) for all Stremio subtitle addons (`OpenSubtitles v3`, `YTS Subtitles`, `AltyaziDB`, `TurkceAltyazi`, `AniSub.co`, etc.).
- **Automatic Addon Seeding:** Automatically seeds default Stremio subtitle addons if the database has none installed.
- **4-Segment to 3-Segment Fallback:** Automatically tries 4-segment queries when real release hints exist, seamlessly falling back to canonical 3-segment queries on 404 or empty results.
- **AniSub Unified Queries:** Consolidated title variants into a single query per video; fixed empty-result cache poisoning and unhandled `CancellationException`.
- **Enhanced Subtitle Cache & Archive Extraction:** Added `IPv4FirstDns`, automatic extraction for `.gz` and `.zip` subtitle packages, skipped hash computation on P2P/HLS to prevent stalls, and atomic disk writes.
- **Binder IPC Protection:** Guarded against `TransactionTooLargeException` when passing large addon/source collections.
