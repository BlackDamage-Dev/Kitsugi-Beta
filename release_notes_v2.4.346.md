# Kitsugi Beta v2.4.346

## 🇹🇷 Türkçe

### 🔄 Shikimori Çapraz Eşitleme (Cross-Sync) ve Durum Önceliği
- **Ortak MAL Kimlik Eşlemesi:** Shikimori anime/manga `target_id` değerinin resmî API sözleşmesine uygun olarak MAL kimlik ekseninde çözümlenmesi sağlandı. Romaji ve İngilizce farklı başlıklara sahip yapımlar (ör. Zom 100) artık aynı eşitleme grubuna dahil ediliyor.
- **Akıllı Durum Birleştirme (`CrossSyncMergePolicy`):** Tamamlanma güvenceleri korunurken, eski kuralın bayat bir "İzleniyor" kaydını daha yeni "Bırakıldı" veya "Duraklatıldı" kararlarına kalıcı olarak tercih etmesi düzeltildi. En güncel kullanıcı kararı tüm platformlara doğru yayılıyor.

### 🌸 Shikimori Detay, Bilgi Kartı ve Topluluk İyileştirmeleri
- **Zenginleştirilmiş Bilgi Bölümü:** Format, sezon, yayınlanan bölüm sayısı, sonraki bölüm tarihi/saati, manga cilt ve yayıncı bilgileri eklendi.
- **Çok Dilli Başlıklar & Temizlik:** İngilizce, Japonca, Romaji, Rusça adlar ayrıştırıldı; `[null]` veya boş dizelerin metin olarak görünmesi engellendi.
- **Lisanslayanlar & Fansub/Fandub Grupları:** İlgili çeviri ve lisans grupları Bilgi sekmesine bağlandı. Film ve dizilerde stüdyo, süre ve yaş sınırının yanlışlıkla gizlenmesi önlendi.
- **Yerel Topluluk İstatistikleri:** Shikimori'nin kendi puan ve liste-durumu dağılım istatistikleri harici yedeklerden önce önceliklendirildi. Boş alanların kutu kirliliği yaratmaması için dinamik istatistik kartı getirildi.
- **Forum ve Harici Bağlantılar:** Shikimori konuları forum sekmesine taşındı ve `/api/comments` yanıt akışı bağlandı. Manga harici bağlantıları etkinleştirildi; Vikipedi linklerine dil etiketi eklendi.

### 🎬 Detay Sayfası Küçük Oynatıcı & MPV / Media3 İyileştirmeleri
- **Gelişmiş Satır İçi Oynatıcı:** Fragman ve açılış/kapanış (OP/ED) temaları için küçük oynatıcı arayüzü pürüzsüzleştirildi. Ses açma/kapama, tam ekran geçişi ve oynatma durumu kontrolleri optimize edildi.
- **MPV ve Media3 Yüzey Desteği:** `PlayerEngine`, `MpvPlayerEngine`, `Media3PlayerEngine` ve `KitsugiMpvSurfaceView` üzerinde küçük oynatıcı yüzey entegrasyonu güçlendirildi; oynatıcı değişimlerinde donma veya kaynak çakışması önlendi.
- **YouTube Bağlantı Tanıma:** Standart watch, embed, shorts, youtu.be ve zaman damgalı video URL'lerinin tamamını hatasız ayrıştıran Regex motoru güncellendi; `YouTubeUrlParsingTest` ve `InlinePlayerSupportTest` ile doğrulandı.

### 🦊 Kitsu Bilgi Kapsamı & Yayın Detayları
- **Eksiksiz Kitsu Yayın Bilgileri:** Kitsu API detaylarında bölüm sayısı, yayın durumu, yayınlandığı sezon/yıl, yaş derecelendirmesi ve içerik uyarıları detay sayfasına tam uyumlu hâle getirildi.
- **Çoklu Dil Etiket Desteği:** Yayın durumu ve yaş etiketleri Türkçe ve İngilizce yerelleştirme kaynaklarıyla senkronize edildi.

### 📝 Ani-Sub Altyazı Eşleme & İyileştirmeler
- **Akıllı Altyazı Eşleştirici (`AniSubSubtitleMatcher`):** Zip paketleri ve arşivlerdeki altyazı dosyaları için bölüm numarası ve başlık benzerlik skoru hesaplama motoru eklendi.
- **Türkçe Altyazı Önceliği:** Ani-Sub üzerinden gelen altyazılarda Türkçe varyantlar (`.srt`, `.ass`) doğru sıra ve zamanlama ile akış istemcisine bağlandı.

---

## 🇬🇧 English

### 🔄 Shikimori Cross-Sync & Status Merging Fix
- **Shared MAL Identity Resolution:** Harmonized Shikimori anime/manga target IDs with the official MAL identity contract. Different localized/Romaji titles (e.g. Zom 100) now cleanly converge into the same cross-sync group.
- **Dynamic Status Merge Policy (`CrossSyncMergePolicy`):** Fixed stale "Watching" records overriding newer "Dropped" or "Paused" statuses, while preserving completion safeguards.

### 🌸 Shikimori Details, Info Card & Community Enhancements
- **Comprehensive Info Fields:** Added format, broadcast season, episodes aired, next episode countdown/date, manga volume count, and publishers.
- **Multi-Language Titles & Cleanups:** Parsed English, Japanese, Romaji, and Russian aliases while stripping any `[null]` artifacts.
- **Licensors & Fansub/Fandub Groups:** Connected licensor and fansub groups to the Info card. Fixed studio, runtime, and age ratings being hidden on movies/TV specials.
- **Native Statistics Priority:** Prioritized Shikimori native rating and list distribution stats over fallback providers, with a dynamic clean statistics card.
- **Forum & External Links:** Connected Shikimori topics to the forum feed with `/api/comments` reply loading. Activated manga external links and added language tags for Wikipedia.

### 🎬 Detail Screen Mini Player & MPV / Media3 Enhancements
- **Enhanced Inline Player:** Smoother playback experience for trailers and opening/ending (OP/ED) themes. Optimized mute/unmute, fullscreen toggle, and playback controls.
- **MPV and Media3 Surface Support:** Strengthened inline player surface integration across `PlayerEngine`, `MpvPlayerEngine`, `Media3PlayerEngine`, and `KitsugiMpvSurfaceView`, preventing freezes or surface collisions during stream transitions.
- **Robust YouTube URL Parsing:** Upgraded regex pattern matching across all YouTube formats (standard watch, shorts, embed, shortlinks, timestamped links), verified via `YouTubeUrlParsingTest` and `InlinePlayerSupportTest`.

### 🦊 Kitsu Information Scope & Airing Details
- **Comprehensive Kitsu Metadata:** Enhanced Kitsu details mapping with full coverage of episode count, airing status, broadcast season/year, age ratings, and content advisories.
- **Localized Badges:** Airing status and age classifications are fully harmonized with Turkish and English locale strings.

### 📝 Ani-Sub Subtitle Matching Engine
- **Smart Subtitle Matcher (`AniSubSubtitleMatcher`):** Introduced title similarity and episode numbering heuristics for matching extracted subtitle streams from zip archives.
- **Turkish Subtitle Priority:** Streamlined Turkish subtitle matching (`.srt`, `.ass`) with prioritized scoring for seamless playback.
