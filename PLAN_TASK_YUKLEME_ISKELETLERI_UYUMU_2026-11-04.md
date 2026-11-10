# Plan / Task — Yükleme iskeletlerinin içerikle uyumu

Tarih: 4 Kasım 2026  
Dal: `arena/b7c980b6-kitsugi-beta`  
Temel commit: `a6dda9d6df6e6babf3f93d9750ff7331c4bf6dfe`

## Amaç
Yüklenme sırasında gösterilen iskeletlerin, yerlerine gelecek kartların düzeni, ölçüleri, boşlukları ve ekran yönüyle mümkün olduğunca uyumlu olması; içerik geldiğinde yerleşim sıçramalarının azaltılması.

## Yapılanlar
- [x] Keşfet raflarında sabit dikey iskelet yerine yön ve TV görünümüne duyarlı medya iskeleti kullanıldı; yatay kaydırma, kart aralığı ve kenar boşlukları içerikle eşleştirildi.
- [x] Tüm kaynaklar görünümündeki yükleme rafı aynı duyarlı düzene geçirildi.
- [x] Keşfet ve arama medya raflarında kullanılan kart genişlikleri ve yatay poster boyutları ortaklaştırıldı; dikey ve TV metin alanları gerçek kartla paylaşıldı.
- [x] Ana ekranın poster rafındaki yükleme kartları kullanılan gerçek kart genişliğine ve en-boy oranına uyarlandı.
- [x] Aramada karakter/personel ve stüdyo için yükleme satırları ilgili sonuç düzenine uyarlandı.
- [x] Yayın takviminin dikey ızgara iskeletinde başlık ve zaman alanları gerçek kartla ortak ölçüleri kullanacak şekilde düzeltildi.
- [x] Detay ekranında ilişki ve öneri poster ölçüleri ve boşlukları, bölüm küçük görselleri ve sezon seçimine göre iskelet düzeltildi.
- [x] Galeri iskeleti gerçek galeri başlığı, kaynak satırı ve örnek poster şeridi geometrisine yaklaştırıldı.
- [x] TV detayındaki raf aralıkları, istatistik ve inceleme iskeletleri ilgili TV düzenine uyarlandı.
- [x] Wiki iskeletindeki alan satırlarının boşlukları ve ortak shimmer saati düzeltildi.
- [x] Keşfet rafları, takvim kartları ve detay kartları için Android Compose yerleşim testleri; Keşfet ölçüleri için birim testleri eklendi.

## Doğrulama / kalan işler
- [x] `git diff --check`: başarılı.
- [ ] Gradle derlemesi ve birim testleri çalıştırılacak.
- [ ] Android cihaz/emülatörde eklenen Compose testleri çalıştırılacak; telefon/tablet dikey-yatay ve TV görünümleri gözle kontrol edilecek.
- Ortamda Java ve Android SDK bulunmadığı için derleme ve Android testleri burada çalıştırılamadı (`./gradlew --version` Java bulunamadığı için başarısız).
- Bilinmeyen içerik sayısı, metin uzunluğu, galeri kategorileri ve yazı ölçeği nedeniyle tüm veri durumlarında birebir aynı yükseklik garanti edilemez; uygulama genelinde sıfır kusur iddiası yapılmamaktadır.

## Paket kullanımı
ZIP, yalnızca değişen/yeni kaynak ve test dosyalarını ve bu planı içerir; proje köküne göre özgün yollar korunur. Tam proje, derleme çıktıları ve bağımlılıklar dahil değildir. Mevcut checkout üzerine uygulanırken dosyaları kendi yollarına yerleştirin.
