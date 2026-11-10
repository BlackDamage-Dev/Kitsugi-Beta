# Plan / Task — Manga görsel okuyucu

**Başlangıç:** `ab81c9ef889e9246a5bceca22f2d2a8da6144f53`  
**Çalışma dalı:** `arena/8f2880f6-kitsugi-beta`  
**Kapsam:** Kitsugi uygulamasının manga/manhwa görsel indirme, önbellek, çözümleme ve gösterim hattı. MangaTR ve Mangacix eklenti depolarında bu paket kapsamında değişiklik yapılmadı.

## Yapılan işler

- [x] Okuyucuyu Coil 3 uyumlu Telephoto adaptörüne taşı; eski Coil 2 kullanan diğer ekranların bağımlılığını koru.
- [x] Görsel isteklerinde Mihon eklentisinin kendi `getImage` / istemci / interceptor yolunu koru.
- [x] Decode hatasında tekrar deneme göster; hatalı önbelleği temizle ve yeniden indirmeyi başlat.
- [x] AVIF, HEIF ve JXL için Tachiyomi native decoder entegrasyonu; eski Android'de GIF ve sistemin desteklediği animasyonlara öncelik.
- [x] Native görüntüleri uyumsuz Android bölgesel decoder yoluna zorlamadan önizleme olarak sun.
- [x] Önbellek kimliğini kaynak/bölüm/sayfa ile ayır; silinen önbelleği geri yükle ve tekrar deneme/ön yükleme yarışını önle.
- [x] Webtoon sayfa boyutunu en-boy oranından hesapla, uzun görsel önizlemesini sınırla; arşiv sayfalarını dosya önbelleğine al.
- [x] Parlaklık değerini sınırla; görsel hata günlüklerinden URL gibi hassas bilgileri çıkar.
- [x] Görsel akışı, kaynak karşılaştırması ve bilinen sınırlar için `MANGA_OKUYUCU_GORSEL_ANALIZI.md` hazırla.

## Kalan işler ve sınırlar

- [ ] Kullanıcının tercihine göre güncel dosyalarla derleme ve gerçek cihaz doğrulaması. Daha önceki CI, son yerel değişikliklerden önceki `b3d95e7` commit'inde başarılıydı; paketin son hâlini doğrulamaz.
- [ ] MangaTR ve Mangacix ile gerçek bölümlerde görsellerin indirilmesi ve ekranda çizilmesi, hata/tekrar deneme ve uzun sayfaların kullanıcı tarafından kontrolü.
- [ ] MangaTR eklentisinin parçalı görsel interceptor'ında kapanmış response ve recycle edilmiş bitmap hata yollarının ayrı eklenti deposunda düzeltilmesi.
- [ ] Çok uzun native-format görsellerde zoom kalitesi/bellek; hareketli AVIF/HEIF varyantları ve cihaz/ABI uyumluluğu değerlendirmesi.
- [ ] TIFF, JPEG 2000, SVG ve diğer özel formatların gerekliliği, güvenli decoder seçimi ve eski eklenti API uyumluluğu araştırması.

**Durum:** Kod değişiklikleri, son yerel düzenlemeler ve araştırma raporu pakete dahildir. Bu paket, tüm eklenti ve formatların kesin olarak çalıştığı veya son kodun cihazda test edildiği anlamına gelmez. Mevcut kaynak dosyaları depo yolları korunarak sunulur; çalışma alanında bulunan diğer dosyalar pakete alınmaz.
