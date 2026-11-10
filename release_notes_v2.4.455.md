# Kitsugi Beta v2.4.455 - Gelişmiş Açık Kaynak Okuyucu ve Yeni Belge Formatları

Bu sürümde açık kaynak manga ve belge okuyucularının (Mihon, Moon+ Reader vb.) en güçlü özellikleri Kitsugi Beta'ya entegre edilmiş; hem görsel (Manga/Webtoon/Çizgi Roman) hem de metin (Roman/Light Novel/PDF) deneyimi baştan sona modernize edilmiştir.

---

### 📖 Görsel Okuyucu Geliştirmeleri (Manga, Çizgi Roman, Webtoon)
- **3×3 Özel Dokunma Izgarası (Custom Tap Grid)**: Ekranın 9 bölgesine bağımsız olarak Sonraki, Önceki, Menü veya Etkisiz eylemleri atanabilir; tek elle kullanım (solak/sağlak) ve uzun basma desteği eklendi.
- **Çift Sayfa Modu (Double Page Spread)**: Yatay ve dikey modlarda iki sayfayı yan yana görüntüleme; ilk sayfayı (kapak) tek gösterme (Cover Alone) seçeneği.
- **Webtoon Gelişmiş Denetimleri**: Aşırı dikey uzun şeritler için bellek optimizasyonu (extreme strip protection), ekrana göre adımlı dokunma kaydırması, ayarlanabilir hızlı/yavaş otomatik kaydırma (auto-scroll) ve bölüm sonu geçiş kartı.
- **Kenar Kırpma (Crop Borders)**: Sayfa kenarlarındaki gereksiz boşlukları otomatik tespit edip kırpan algoritma.
- **Geniş Sayfa Döndürme (Wide Image Rotation)**: En/boy oranı geniş olan iki sayfalık çizimleri isteğe bağlı saat yönünde veya tersinde otomatik döndürme.
- **Gelişmiş Renk Matrisi**: Doygunluk (Saturation), Kontrast (Contrast), Renk Tonu (Hue), Gri Tonlama ve Ters Çevirme (Inverted) ayarları.
- **Ses Tuşları ile Gezinme**: Fiziksel ses açma/kısma tuşlarıyla sayfa çevirme veya Webtoon kaydırma, isteğe bağlı yön tersine çevirme.
- **Kesintisiz İlerleme Kaydı**: Arka plana geçişte veya uygulamadan çıkışta Room veritabanına anında flush edilen güvenli ilerleme kaydı.

---

### 📚 Yeni Yerel Belge ve Roman (Novel) Formatları
- **EPUB Okuyucu**: Bölüm arama çubuğu, bölüm sonu otomatik geçiş ve biçimlendirilmiş metin akışı.
- **FB2 ve FB2.ZIP Desteği**: Standart FictionBook formatı ve arşiv içi doğrudan okuma.
- **CBZ / ZIP & CBR / RAR Arşivleri**: Yerel çizgi roman arşivlerini doğrudan açma, dahili zip/rar (Junrar) ayrıştırıcı.
- **Yerel PDF Okuyucu**: `PdfRenderer` ile yüksek çözünürlüklü ve bellek dostu sayfa render politikası.
- **Düz Metin (TXT) Okuyucu**: Hafif ve hızlı metin novel okuma motoru.

---

### 🛠️ Zengin Roman & Metin Araçları
- **Tipografi ve Temalar**: OLED Siyah, Koyu, Sepya, Açık ve Yeşil temalar; yazı tipi boyutu ve satır yüksekliği kontrolü.
- **Metin Arama ve Vurgulama**: Roman içinde kelime/cümle arama ve eşleşen kısımları renkli vurgulama.
- **Yer İmleri ve Notlar**: İlerleme noktalarını kaydetme, not ekleme, JSON olarak dışa aktarma ve geri yükleme.
- **Dahili Metin Okuma (TTS)**: Hız, perde ve sistem ses motoru/dil seçimi ile sesli kitap deneyimi.
- **Anlık Metin Değiştirme (Text Replacement)**: Okuma esnasında seçili kelime/ifadeleri dinamik olarak değiştirme kuralı.

---

### 📺 Android TV Entegrasyonu
- TV Manga ve Doküman okuyucu ekranlarına tam kumanda/D-pad desteği, otomatik kaydırma ve genişletilmiş ayar menüsü.

---

### 🎵 Ambiyans & Harici Entegrasyonlar
- Manga okuma ekranındaki Ortam Sesi / Ambiyans müzik motoru, 6 ses kaynağı ve çevirmen seçim protokolü tam uyumla korundu.
