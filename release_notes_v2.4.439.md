# Kitsugi-Beta v2.4.439

## 📚 Kapsamlı Manga Modülü Yenilemesi, Çevirmen Gruplama & Çok Dilli Arama

Bu sürümde manga okuyucu, arama motoru, bölüm yönetimi ve kullanıcı arayüzü 4 kapsamlı aşamada baştan sona modernize edildi.

### 🌟 Öne Çıkan Değişiklikler

#### 1. Manga Okuyucu Tam Ekran & Yeni Okuma Modları
- **Gerçek Tam Ekran Deneyimi (`MangaReaderSystemUi`)**: Okuyucu açıldığında sistem durum çubuğu, gezinme çubuğu ve tablet görev çubukları (`hide(systemBars)`) tam olarak gizlenir; çıkıldığında sistem durumu temiz bir şekilde geri yüklenir.
- **Yeniden Yapılandırılmış Dokunma Bölgeleri**: Sol/sağ/orta bölgeleri; uzun basma ile menü açma, çift dokunma ve çimdikleme (pinch-to-zoom) sezgisel hale getirildi.
- **Dikey Sayfalama Modu (`MangaVerticalPagerReader`)**: Webtoon ve yatay pager seçeneklerine ek olarak gerçek dikey sayfalama modu eklendi.
- **Sığdırma ve Renk Filtreleri**: Genişliğe/yüksekliğe sığdırmanın yanı sıra Doldur/Kırp (fill/crop) ve Orijinal boyut modları; Sıcak, Soğuk ve Sepya renk filtreleri ve Ayna (mirror) modu eklendi.
- **Webtoon Sayfa Takibi**: Debounce optimizasyonu ve `MangaPageSizeMemory` ile dinamik en-boy oranı korunarak ani sayfa zıplamaları önlendi.

#### 2. Çoklu Çevirmen (Scanlator) Gruplaması & Bölüm Arama
- **Akıllı Bölüm Gruplaması (`MangaChapterGrouping`)**: Aynı bölüm numarasına sahip farklı çevirmen gruplarının bölümleri tek bir satırda toplanır; tıklandığında seçim diyalogu (`MangaChapterChoiceDialog`) açılır.
- **Kalıcı Çevirmen Tercihi (`MangaScanlatorPreferences`)**: Bir seri için seçtiğiniz çeviri grubu hatırlanır; sonraki bölümlerde otomatik olarak tercih edilir.
- **Bölüm İçi Arama & Filtreler**: Bölüm numarasına ve başlığına göre anlık filtreleme (örn. "42" sorgusu 42.0 ve 42.5'i bulur), okunmamış bölümler filtresi ve çevirmen çipleri.
- **Tümünü Okundu İşaretleme Onayı**: Yanlışlıkla basmaları önlemek için onay diyalogu eklendi.
- **Son Arama Geçmişi (`MangaSearchHistory`)**: Manga aramalarında geçmiş sorgular kaydedilir ve tek tıkla yeniden aranabilir.

#### 3. Tema, Kontrast ve Bellek İyileştirmeleri
- **Dinamik Tema Uyumu**: Sabit mor renkler (`0xFF7C4DFF`) tamamen temizlendi; uygulamanın seçili tema rengi (`LocalKitsugiAccent`) ve kontrast metin rengi (`OnAccent`) dinamik olarak uygulanır.
- **Duyarlı Yükseklikler**: Farklı ekran ve yönelimlerde (dikey/yatay) esnek yerleşimler.
- **ZIP/CBZ Arşiv Sayfası İzolasyonu**: Yerel arşivlerden okunan görseller önbellek izolasyonuyla optimize edildi.

#### 4. Çok Dilli Arama & Dil Öncelikli Kaynak Sıralaması
- **Gelişmiş Çok Dilli Arama (`MangaSearchAlternates`)**: İngilizce, Romaji ve Japonca başlık varyantlarıyla fallback arama mekanizması; sonuç bulunamadığında alternatif adlar otomatik olarak taranır.
- **Sayfalama Senkronizasyonu**: Sonraki sayfalar ilk eşleşen `resolvedQuery` ile devam eder.
- **Uygulama Dil Önceliği**: Kurulu kaynaklar uygulama diline (`LocaleCache`) uygun olarak önceliklendirilir.
- **Durum Etiketleri & %100 Dil Eşliği**: `MangaStatusLabels` ile manga durumları tek elden yönetilir; TR ve EN `strings.xml` tam simetriye sahiptir (her ikisi de 18.490 anahtar).

### 📦 İndirme
- **APK**: `Kitsugi-Beta-v2.4.439-foss.apk` (116.0 MB)
