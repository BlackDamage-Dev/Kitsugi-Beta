# Kitsugi Beta v2.4.465 — Manga İndirmeleri: Kaynak / Çevirmen Rozetleri ve Kapak Görselleri

İndirmeler > **Manga** sekmesindeki bölüm kartları ve seri başlıkları, aynı kaynaktan inse dahi farklı çevirmen kopyalarını net olarak ayırt eden renkli minik rozetler ve indirilen ilk sayfadan türetilen kapak görselleriyle zenginleştirildi.

### Yenilikler & İyileştirmeler
1. **Seri Başlığı ("Bar") (`MangaOfflineGroupHeader`)**:
   - **Kapak Görseli**: Serinin ilk indirilen sayfası kapak olarak gösterilir (ilk bölüm iniyorsa sıradaki tamamlanan bölümün ilk sayfası; hiç yoksa kitap yer tutucusu).
   - **Sabit Renkli Kaynak Rozeti**: Kaynak adının karmasından üretilen 8 renkli sabit palet (ör. Manga-TR her zaman aynı renkte).
   - **Dil Rozeti**: Kaynak dili (TR/EN/…).
   - **Çevirmen Rozetleri**: Tema vurgu renginde çevirmen rozeti ve dar alanlarda `+N` taşma rozeti. Çevirmen yoksa soluk "Bilinmeyen" rozeti.
   - **Özet Bilgisi**: Toplam bölüm sayısı, disk kullanımı ve eksik indirme varsa `X/Y hazır` göstergesi.
2. **Bölüm Kartı (`MangaOfflineChapterCard`)**:
   - **Bölüm Küçük Kapağı**: Bölümün indirilen ilk sayfası 44×60 dp küçük kapak olarak sol tarafta.
   - **Ayrı Kaynak & Çevirmen Rozetleri**: Her satırda bağımsız kaynak ve çevirmen rozeti; böylece aynı bölüm birden çok çevirmenden inse dahi asla karışmaz.
   - **Bölüm Numarası & Çevirmen Sıralaması**: Aynı numaralı bölümler alt alta listelenir.
   - **Çevrimdışı Hazır Vurgusu**: Tamamlanan bölümlerde kart çerçevesi ve durum etiketi tema vurgu rengiyle belirginleştirilir; 4 dp yuvarlak uçlu ilerleme çubuğu.
   - **Mevcut Tüm Eylemler Korundu**: Çevrimdışı oku, klasörü göster, duraklat/devam et, silme diyaloğu.
3. **Erişilebilirlik (TalkBack)**:
   - Rozetler ekran okuyucuya `"Kaynak: Manga-TR"`, `"Çevirmen: Şımarık Fansub"` şeklinde açıkça okunur.
