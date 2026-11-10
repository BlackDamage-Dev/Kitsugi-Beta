# Plan / Task — Video Oynatma ve AnimeThemes Açılış Düzeltmeleri

**Tarih:** 2026-10-24  
**Branch:** `arena/98771a3d-kitsugi-beta`

## Amaç

Detay sayfasındaki YouTube fragmanlarının kaynağa göre bazen hiç açılmaması, 00:00'da kalması ve AnimeThemes açılış/kapanış videolarının çok uzun sürede başlamasıyla ilgili nedenleri farklı oynatma yollarını kapsayacak şekilde araştırmak ve düzeltmek.

## Kapsam

- Mobil satır içi YouTube oynatıcı
- Mobil tam ekran fragman overlay ve fullscreen player
- Android TV detay fragman/tema overlay
- Android TV ana sayfa fragman önizlemesi
- YouTube extraction, kaynak seçimi, imzalı URL cache'i ve chunk aktarımı
- AnimeThemes video seçimi ve kompakt/TV başlangıç tamponları

## Tespit Edilen Nedenler ve Uygulanan Çözümler

### 1. Eski ve kırılgan YouTube istemci yolu

- Eski `android_vr` yolu kaldırıldı.
- `tv_samsung`, `visionos`, `ios` ve `web_embedded` istemci kimlikleri, sürümleri, cihaz alanları ve User-Agent değerleri güncel upstream sözleşmeleriyle eşitlendi.
- İstemci istekleri paralelleştirildi ve her isteğe bağlı zaman aşımı uygulandı.

### 2. Tek URL'ye bağımlı oynatma

- `TrailerPlaybackSource` artık kaynak başlıklarını ve bağımsız alternatifleri taşıyor.
- Ses ve görüntü yalnızca aynı istemciden gelen URL'lerle eşleştiriliyor.
- Çözülmemiş `n` parametreli ve 1080p üzeri adaylar geriye alınıyor.
- Kaynak hatasında önce farklı URL deneniyor; ses var/görüntü yok durumunda aynı kaynakla alternatif decoder deneniyor.
- Fallback döngüleri sınırlandırıldı ve aynı kaynak/motor çiftinin tekrar denenmesi engellendi.

### 3. Oynatma yolları arasında farklı MediaSource davranışı

- Ortak `TrailerMediaSourceFactory` eklendi.
- Mobil overlay, TV detay ve TV ana sayfa için aynı HLS MIME, request header, range ve ses/görüntü birleştirme kuralları uygulandı.
- Varsayılan `setMediaItem` yolunun imzalı YouTube başlıklarını atlaması engellendi.
- Fullscreen prepare sırasında kaynak-özel başlıklar kullanılacak şekilde düzeltildi.

### 4. Bayat imzalı URL cache'i

- Başarılı extraction sonuçları için küçük, süreli LRU cache eklendi.
- Cache geçerliliği primary/alternatif bütün video ve ses URL'lerinin en erken `expire` değerine bağlandı.
- HLS path ve progressive query expiry biçimleri desteklendi.
- TV servisindeki süresiz başarı cache'i kaldırıldı; genel TMDB cache'inin expiry kontrolünü atlaması engellendi.
- Retry sırasında ilgili cache katmanları temizleniyor.
- Fullscreen YouTube retry artık video kimliğiyle yeni extraction yapıyor.

### 5. AnimeThemes yavaş açılış

- Desteklenen çözünürlükler 2K/4K'dan önce seçiliyor.
- Kompakt önizleme için 720p'ye yakın ve aynı özelliklerde daha küçük dosyalar tercih ediliyor.
- Tek 1080p seçenek varsa oynatılabilirliği korunuyor.
- Kompakt başlangıç tamponu 1 saniyeye, rebuffer hedefi 2 saniyeye düşürüldü; fullscreen ayarları değiştirilmedi.
- TV önizleme başlangıç tamponu 5 saniyeden 1 saniyeye indirildi.
- TV'de 50 Mbps başlangıç tahmini ve en yüksek bit hızını zorlama kaldırıldı; uyarlanabilir kalite etkinleştirildi.

### 6. Sessiz chunk hataları ve callback yaşam döngüsü

- YouTube chunk açma hatasının `EOF` olarak yutulması kaldırıldı; hata kaynak fallback'ine ulaşabiliyor.
- Media3 error callback içinden `stop()` çağırma yerine temizlik sonraki composition/disposal turuna bırakıldı.

## Testler ve Kontroller

- 50 odaklı JVM testi başarıyla çalıştı; 0 başarısız.
- Kapsam: istemci sözleşmeleri, kaynak sıralama, aynı-istemci ses/görüntü eşleşmesi, source fallback, sınırlı deneme, header birleştirme, HLS parsing, expiry/LRU ve AnimeThemes seçim politikası.
- Extractor ve ortak MediaSource fabrikası ayrı Kotlin tip kontrollerinden geçirildi.
- `git diff --check` temiz.

## Kalan Doğrulama

- Tam Android Gradle derlemesi bu ortamda `services.gradle.org` SSL el sıkışma hatası nedeniyle çalıştırılamadı.
- Fiziksel Android cihaz ve canlı YouTube/AnimeThemes CDN oynatma testi yapılamadı.
- Bu nedenle cihaz üzerindeki nihai oynatma doğrulaması ayrıca yapılmalıdır.

## Teslim Arşivi

Arşiv, bu plan/task dosyasıyla birlikte `git status` çıktısındaki bütün değiştirilmiş ve yeni kaynak/test dosyalarını repository-relative klasör yapılarıyla içerir.
