# Kitsugi Beta v2.4.345

## 🇹🇷 Türkçe

### 📱 Wiki Açılır Sayfası Artık Mobil Düzende Açılıyor
- **Varsayılan görünüm mobil:** Özgün wiki sayfası artık masaüstü düzeniyle değil, sitenin kendi mobil/duyarlı düzeniyle açılır. Metinler okunaklı boyutta gelir, yatay kaydırma gerekmez.
- **Seçim hatırlanıyor:** Menüden "Masaüstü düzeni" veya "Mobil düzen" seçildiğinde tercih kalıcı olarak kaydedilir (`wiki_desktop_layout`). Yeni sekmeler, yeni wiki sayfaları ve uygulama yeniden başlatıldıktan sonra da **en son ne seçildiyse o düzen** açılır.
- **Sekmeler arası tutarlılık:** Yeni sekme ve sekme değişimlerinde düzen tercihi korunur.

### ↔️ Sayfa Kendini Ekrana Sığdırıyor
- **Mobilde site viewport'u korunur:** Yalnızca ekrandan geniş, sabit piksel genişliği dayatan belgeler `width=device-width`'e çevrilir; diğer sayfaların düzenine dokunulmaz.
- **Masaüstü düzeninde otomatik ölçekleme:** 1200 px'lik masaüstü düzeni `initial-scale` ile cihaz genişliğine oturtulur; sayfa 1:1 oranda taşmaz.
- **Taşan öğeler tek tek düzeltilir:** Ekrandan geniş görsel, tablo, `pre` ve geniş bloklar **yalnızca gerçekten taşıyorsa** işaretlenip kendi içinde kaydırılabilir hâle getirilir. Metin, bağlantı, nitelik ve site betikleri değiştirilmez; içerik hiçbir zaman kırpılmaz (`overflow-x: hidden` kullanılmaz).
- **Geç gelen görseller / çeviri sonrası genişleme:** Yerleşim `resize`, `load` ve çeviri yazımlarından sonra yeniden ölçülür; kaydırma konumu bozulmaz.

### ⚡️ Otomatik Çeviri Ciddi Biçimde Hızlandı
- **Paralel boru hattı (pipeline):** Önceden her parti için "iste → bekle → yaz" sırası vardı; artık **aynı anda 3 çeviri partisi** uçuşta olur ve sonuçlar geldikçe yazılır. Ağ beklemesi sayfayı kilitlemez.
- **Daha büyük partiler:** DOM köprüsü tur başına 8 düğüm yerine **12 düğüm** (1200 karaktere kadar) toplar; daha az istek, daha çok metin.
- **Tekrarlar tek istekte:** Aynı metin sayfada birden çok geçiyorsa (gezinme etiketleri, tekrar eden satırlar) **tek** ağ isteği yapılır, sonuç tüm düğümlere dağıtılır.
- **Kaydırırken çeviri durmuyor:** Kaydırma olayları artık tarayıcıyı her karede baştan başlatmıyor (gecikmeli yeniden tarama). Yazma 120 ms'lik kısa bir pencereyle ertelenir ve kaybolmaz, sürekli yeniden denenir.
- **Hata artık boru hattını durdurmuyor:** Tek bir partinin/isteğin hatası yalnızca o partiyi etkiler; düğümler sonra yeniden denenir. Zaman aşımında "bekliyor" işaretleri temizlenir, metin bir daha hiç istenmeyen durumda kalmaz.
- **Boşta daha az yoklama:** Çevrilecek metin kalmadığında 450 ms, tarama sürerken 90 ms yoklama; gereksiz JS çağrıları azaltıldı.

---

## 🇬🇧 English

### 📱 Wiki Page Opens in Mobile Layout
- **Mobile by default:** The original wiki page no longer opens in a cramped desktop layout; it uses the site's own responsive/mobile layout with readable text and no horizontal scrolling.
- **Choice is remembered:** Toggling "Desktop layout"/"Mobile layout" is persisted (`wiki_desktop_layout`). New tabs, new wiki pages and app restarts open in **the last selected layout**.
- **Consistent across tabs:** Layout preference survives tab switches and duplicated tabs.

### ↔️ The Page Fits the Screen Automatically
- **Site viewport preserved on mobile:** Only documents that hard-code a fixed pixel width wider than the device are rewritten to `width=device-width`; untouched pages keep their own layout.
- **Desktop mode is auto-scaled:** The 1200 px desktop layout is fitted with `initial-scale`, so it no longer overflows at 1:1.
- **Only overflowing elements are corrected:** Wide images, tables, `<pre>` and wide blocks are marked **only when they actually overflow** and become scrollable within themselves. Text, links, attributes and site scripts are never rewritten and content is never clipped (no `overflow-x: hidden`).
- **Late images / translated layout changes:** Layout is re-measured on `resize`, `load` and after translation writes without disturbing the scroll position.

### ⚡️ Automatic Translation Is Much Faster
- **Pipelined:** Up to **three DOM batches in flight** at once, applied as they arrive, instead of a strict "request → wait → write" loop.
- **Larger batches:** The DOM bridge now returns up to **12 nodes** (~1200 chars) per round, cutting the number of requests.
- **Repeated text is requested once:** Identical strings are translated with a single request and fanned out to every matching node.
- **Translation keeps flowing while scrolling:** Scroll events no longer restart the scanner on every frame (debounced rescan); writes are briefly deferred (120 ms) and retried, never dropped.
- **Failures are isolated:** A slow or failing batch no longer stalls the pipeline; a time-out clears "pending" flags so text can be retried instead of being stuck forever.
- **Leaner idle polling:** 450 ms idle / 90 ms while scanning, with the cosmetic ad layer refreshed at most once per second.
