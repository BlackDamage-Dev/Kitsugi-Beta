# Plan / Task — Anizm ve TrAnimeIzle oynatma

Tarih: 4 Kasım 2026  
Depo: Kitsugi-Beta  
Ayrıntılı kaynak incelemesi: `docs/ANIZM_TRANIME_PLAYBACK_AUDIT_2026-11-04.md`

## Yapılanlar

- [x] Uygulamada CloudStream embed çözümlemesine Referer ve başlık aktarımını koru; extractor başlıklarını büyük/küçük harf duyarsız birleştir.
- [x] Kaynak doğrulama isteklerinde eklentinin User-Agent, Origin ve diğer başlıklarını koru; belirsiz HTTP sonuçlarını doğrudan ölü kaynak sayma.
- [x] URL'leri yalnızca adreslerine göre değil; başlık, kalite, harici ses, tür ve interceptor verisiyle birlikte tekilleştir.
- [x] Açıkça belirtilmiş HLS/DASH türünü ve `extractorData` değerini kaynak listesinden Media3'e taşı; adaptif manifestleri paralel byte-range veri kaynağından uzak tut.
- [x] Oynatıcıda yeniden deneme ve kaynak değişiminde kaynak bilgisi ve ilk harici ses URL'sini koru.
- [x] Başlık, format ve tekilleştirme testlerini ekle; çevrimdışı JVM test betiği oluştur.
- [x] Anizm ve TrAnimeIzle kaynak kodlarını, kullanıcının görüntülerini ve erişilebilen HTML başlangıçlarını incele; CloudStream ve Aniyomi ile karşılaştır.

## Doğrulama

- [x] `scripts/test_cs_playback_offline.sh`: 14 test geçti. Testler gerçek Kotlin politika/sanitizer kodunu kullanır; Android, StreamSource modeli ve JUnit için taklit nesneler kullanılır.
- [x] `git diff --check` temiz; değişen Kotlin dosyalarında tree-sitter sözdizimi hata düğümü bulunmadı.
- [ ] Tam Android/Gradle derlemesi: Android SDK ve gerekli bağımlılık erişimi bu ortamda bulunmadığından çalıştırılmadı.
- [ ] Gerçek cihazda Media3/MPV, canlı host, manifest/segment ve görüntü+ses testleri yapılmadı.

## Kalan işler ve sınırlamalar

- [ ] Ayrı Kitsugi-Plugins deposundaki TrAnimeIzle eklentisinde URL bazlı tekilleştirme, HLS master/varyant davranışı ve extractor ses/interceptor meta verisi kaybını düzelt ve test et. Bu ZIP eklenti paketini güncellemez veya yayımlamaz.
- [ ] Tüm harici ses parçalarını ve parça başlıklarını uygulamada destekle. Şu anda yalnızca ilk harici ses URL'si aktarılır; MPV, Media3'ün OkHttp interceptor'ını kullanmaz.
- [ ] SendVid'in MP4 içine gömdüğü hata klibi için güvenilir doğrulama yöntemi araştır. Sadece kısa süre veya küçük dosya boyutuna bakarak gerçek videoları eleme.
- [ ] Görsellerdeki kaynakları canlı ve her motor için ayrı doğrula. Silinmiş, kota/oturum isteyen veya sağlayıcı tarafından engellenmiş dosyalar uygulama değişikliğiyle geri getirilemez.

Bu çalışma, bütün kaynakların sorunsuz oynadığını veya canlı test edildiğini iddia etmez.
