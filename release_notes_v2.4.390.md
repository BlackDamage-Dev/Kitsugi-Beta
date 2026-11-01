# Kitsugi Beta v2.4.390

[TR]
### 🚀 Yenilikler ve Öne Çıkan Geliştirmeler

Bu sürümde; **Arama Ortak Filtre Mimarisi 3. Denetim Turu**, **Yayın Ekranı Kaynak & Dinamik Sıralama Paneli**, **İzleme Geçmişi Baştan Aşağı Yenilemesi** ve **Eklenti Metin Temizleyici (StreamLabelSanitizer)** yer alıyor!

---

#### 🔍 Arama Ortak Filtre Mimarisi (3. Tur İyileştirmeleri & Düzeltmeleri)
- **Puan Ölçeği Uyuşmazlığı ve Çift Yönlü Normalizasyon:** Ortak model 0–100 puan ölçeği kullanırken MAL, TMDB, Shikimori ve Bangumi 0–10 bekliyordu. "Puan 70+" seçildiğinde Jikan'a `min_score=70` veya TMDB'ye `vote_average.gte=70` gitmesi sebebiyle boş sonuç dönmesi sorunu `canonicalScoreToTen` ve `tenToCanonical` çift yönlü çeviricisi ile kökten çözüldü. Dört motorda da puan doğru ölçekte iletilir; ayrıca kaynakta seçilen 0–10 puan ortak kaydırıcıda otomatik olarak 10 katına (ör. 7.0 → 70) dönüştürülerek tam senkronize görünür.
- **Stüdyo & Kurum Arama Paneli (`StudioProducerSheet`):** Yapımcı, stüdyo ve kurumların seçilebildiği, anlık filtreleme ve arama destekli yeni alt panel entegre edildi:
  - AniList, MAL ve TMDB için kurum filtreleri eksiksiz bağlandı.
  - Kurum aramasını desteklemeyen kaynaklar (Shikimori, Bangumi vb.) için kullanıcı dostu bilgilendirme kartı eklendi.
- **AniList & Bangumi Gelişmiş Filtre Entegrasyonu:**
  - AniList "Lisansörler" çipi (`licensedBy_in`), "minimum etiket güveni" (`minimumTagRank`) ve cilt aralığı (`volumesGreater`/`volumesLesser`) facade üzerinden GraphQL isteğine bağlandı.
  - Bangumi "yalnızca çizgi roman/manga" anahtarı ve minimum puan filtresi motor seviyesinde bağlandı.
- **Ortak Aralıklar:** Yıl ve puan aralıkları tüm desteklenen kaynak motorlarına standart biçimde beslenir.

---

#### 📺 Yayın Ekranı Kaynak Paneli & Dinamik Sıralama (`StreamSourceSheet`)
- **Modern Kaynak & Sıralama Paneli (`StreamSourceSheet`):** Yayın seçimi ekranında sağ üstteki butonla açılan, kaynakların durumunu ve akış seçeneklerini yöneten yeni alt panel:
  - Kaliteye göre sıralama (4K, 1080p, 720p...)
  - Dosya boyutuna göre sıralama
  - Eklenti ping süresine ve varsayılan sıraya göre filtreleme
- **Kaydırmaya Duyarlı Başlık (`Scroll-Aware Header Reveal`):** Bölüm listesi veya kaynak listesi aşağı kaydırıldığında üst aksiyon çubuğu ve başlık yumuşak bir animasyonla belirir veya gizlenir.
- **Tüm Kaynakları Yeniden Yoklama (`refreshAll`):** Önbelleğe takılmadan veya tek tek yenilemek zorunda kalmadan tüm eklenti ve kaynakları tek dokunuşla sıfırdan yeniden tarama imkânı.
- **Ölü Buton Düzeltmesi:** Stremio eklentilerinde "⚡️" ön eki olmadığı için çalışmayan tekil eklenti yenileme butonu mantıksal olarak düzeltildi; CloudStream ve Stremio eklentileri doğru filtrelenerek yanıt vermeyen buton sorunu giderildi.

---

#### 📜 İzleme Geçmişi Yenilemesi & Eklenti Metin Temizliği
- **Eklenti Metin Temizleyici (`StreamLabelSanitizer`):** Eklentilerden ve kazıyıcılardan gelen `.fa-secondary{opacity:.4}` gibi stil sınıfları, HTML etiketleri ve CSS çöp karakterlerini temizleyen regex motoru:
  - Oynatıcı ses/altyazı parça adlarında ham stil kodlarının görünmesi engellendi.
  - İndirilenler ekranı ve indirme bildirim özetlerinde çöp metinlerin yer alması önlendi.
  - Eski ve yeni tüm geçmiş verileri gösterim ve yazma anında sterilize edildi.
- **İzleme Geçmişi 6 Sıralama Modu (`WatchHistoryQuery`):**
  - En Son İzlenen (varsayılan)
  - En Eski İzlenen
  - Başlık (A-Z)
  - Başlık (Z-A)
  - İlerleme Yüzdesi
  - İzleme Süresi
- **Dizi/Anime Bazlı Gruplama & Çoklu Seçim:**
  - Aynı dizinin veya animenin bölümleri tek başlık altında toplanabilir.
  - Uzun basışla çoklu seçim moduna geçilebilir, seçilenler topluca geçmişten temizlenebilir.
- **Silme İşlemini Geri Alma (Undo Bar):** Yanlışlıkla silinen geçmiş kayıtlarını anında geri getiren akıcı geri alma çubuğu.
- **İnsansı Zaman Etiketleri:** "5 dakika önce", "Dün", "Geçen hafta" gibi anlaşılır ve yerelleştirilmiş göreceli zaman etiketleri.
- **Geri Tuşu Davranışı:** Çoklu seçim veya arama açıkken sistem geri tuşuna basıldığında ekrandan çıkmak yerine önce aktif seçim veya arama kapatılır.

---

[EN]
### 🚀 What's New & Highlights

This release brings the **Search Common Filters Architecture Round 3**, **Stream Screen Source Panel & Dynamic Sorting**, **Watch History Overhaul**, and the **Stream Text Sanitizer (StreamLabelSanitizer)**!

---

#### 🔍 Search Common Filters Architecture (Round 3 Polish & Bugfixes)
- **Score Scale Discrepancy & Bidirectional Normalization:** kitsugi's canonical filter model operates on a 0–100 scale, while MAL, TMDB, Shikimori, and Bangumi expect 0–10. Queries like "Score 70+" were previously forwarding `min_score=70` to Jikan or `vote_average.gte=70` to TMDB, yielding empty results. This is fully resolved with `canonicalScoreToTen` and `tenToCanonical` bidirectional converters. All four search engines now receive correctly scaled scores, and scores set on individual engines reflect accurately on the canonical slider (e.g., 7.0 becomes 70).
- **Studio & Institution Sheet (`StudioProducerSheet`):** A new bottom sheet allowing users to search and select studios and production institutions:
  - Full institution filtering integrated for AniList, MAL, and TMDB.
  - Helpful descriptive state for sources that do not support institution filtering (Shikimori, Bangumi, etc.).
- **AniList & Bangumi Advanced Filters:**
  - AniList "Licensors" chip (`licensedBy_in`), "minimum tag rank" (`minimumTagRank`), and volume range (`volumesGreater`/`volumesLesser`) are wired directly to GraphQL queries.
  - Bangumi "Manga only" switch and minimum score filters are directly passed to the search engine.
- **Standardized Ranges:** Year and score range filtering across all supported search engines.

---

#### 📺 Stream Screen Source Panel & Dynamic Sorting (`StreamSourceSheet`)
- **Modern Source & Sorting Sheet (`StreamSourceSheet`):** A dedicated bottom sheet accessible from the top-right action button on the stream selection screen:
  - Sort streams by resolution/quality (4K, 1080p, 720p, etc.)
  - Sort by file size
  - Filter and sort by plugin ping latency or default priority
- **Scroll-Aware Header Reveal:** The top action bar and title dynamically and smoothly appear or collapse as the user scrolls through episodes or stream results.
- **Force Refresh All Sources (`refreshAll`):** Perform a complete re-fetch across all plugins and scrapers with a single tap, bypassing cache misses and stale results.
- **Dead Button Resolution:** Fixed a silent failure where Stremio plugins (lacking the "⚡️" prefix) could not be re-probed individually. CloudStream and Stremio plugins are now properly distinguished.

---

#### 📜 Watch History Overhaul & Stream Text Sanitization
- **Stream Text Sanitizer (`StreamLabelSanitizer`):** Robust regex-based cleaning engine removing scraper artifacts, CSS classes like `.fa-secondary{opacity:.4}`, and HTML fragments:
  - Cleaned stream audio and subtitle track labels inside the video player.
  - Sanitized episode titles in the Downloads queue and system notifications.
  - Retrospective and runtime sanitization across all watch history records.
- **6 Watch History Sorting Modes (`WatchHistoryQuery`):**
  - Recently Watched (default)
  - Oldest Watched
  - Title (A-Z)
  - Title (Z-A)
  - Completion Percentage
  - Watch Duration
- **Series Grouping & Multi-Select:**
  - Cleanly collapse episodes of the same show under a single group header.
  - Multi-select mode with bulk deletion.
- **Undo Snackbar Bar:** Immediate undo action for accidental deletions.
- **Humanized Relative Timestamps:** Friendly localized timestamps ("5 minutes ago", "Yesterday", "Last week").
- **Back Key Navigation:** Pressing the system back button smoothly dismisses multi-selection or search before navigating away.
