# Kitsugi Beta v2.4.464 — Manga Bölüm Sayfası Düzenlemesi

Android uygulamasındaki telefon manga detay / bölüm listesi ekranı Kitsugi karanlık temasına ve kart tasarım diline tam uyumlu hâle getirildi.

### Yenilikler & İyileştirmeler
1. **Tek Bölüm Arama Alanı (`ChapterSearchField`)**:
   - Bölüm adı, numarası ve çevirmen/scanlator bilgisi içinde arama.
   - Hızlı temizleme butonu.
2. **Durum Filtre Çipleri**:
   - `Tümü`, `Okunmadı`, `Devam ediliyor`, `Okundu` çipleriyle anında filtreleme.
3. **Kitsugi Temalı Sıralama Alt Paneli (`ModalBottomSheet`)**:
   - Açılır menü yerine modern alt panel.
   - `Eskiden yeniye` (1 → N), `Kaynak sırası` (N → 1), `Son güncellenen`, `İsim · A’dan Z’ye`, `İsim · Z’den A’ya` seçenekleri.
   - Bölüm başlık görünüm modu (Numara / Başlık) seçimi.
4. **Okuma İlerlemesi Özeti (`ChapterProgressSummary`)**:
   - Toplam ve okunan bölüm sayısı (`$read / $total okundu`).
   - İlerleme çubuğu ve devam edilen bölüm sayısı bilgisi (`$inProgress bölümde okumaya devam ediyorsun`).
   - Eksik bölüm numarası uyarıları.
5. **Tema Uyumlu Bölüm Kartları**:
   - Okundu ve devam ediliyor göstergeleri, zarif çerçeveler.
   - Boş arama/filtre durumunda açıklayıcı kart ve tek tıkla filtreleri sıfırlama düğmesi.
6. **Mevcut Tüm Yetenekler Korundu**:
   - Çevrimdışı CBZ indirme butonları, yer imi (★), kitaplık entegrasyonu, Fandom Wiki kartı, çoklu çevirmen seçici sheet.
