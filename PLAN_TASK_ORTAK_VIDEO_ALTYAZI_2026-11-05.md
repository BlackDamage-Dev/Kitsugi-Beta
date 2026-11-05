# Ortak video ve altyazı akışı — plan ve görevler

## Amaç
TMDB, Simkl ve diğer kataloglardan açılan içeriklerin video ve altyazı sağlayıcılarına mevcut kimlik, başlık, sezon ve bölüm bilgileriyle yönlendirilmesi; yanlış altyazı eşleşmesi ve gereksiz ağ işlemlerinin azaltılması.

## Uygulanan değişiklikler
- CloudStream video sağlayıcılarının katalog türü nedeniyle aramadan önce elenmesi gevşetildi; arama hatalarının diğer başlık varyantlarını erken engellemesi önlendi.
- İzleme ve oynatıcı yollarında bilinen IMDb ve Bangumi kimlikleri aktarıldı; Kitsu/AniList kimlik alanları ayrıştırıldı.
- Altyazı sorgularında kimlik, alternatif başlık, içerik türü ve bölüm kullanımı düzenlendi; manifestin altyazı türüne göre yönlendirme eklendi.
- AniSub tarafından indirilen yerel altyazı dosyalarının açılması düzeltildi; isim ve sezon eşleştirmesi sıkılaştırıldı, çok dosyalı arşivde rastgele dosya seçimi engellendi.
- Altyazı hash işlemi sorgular arasında paylaşıldı ve HTTP aralık yanıtı denetlendi; video değişiminde eski altyazı araması iptal edildi.
- Bu alanlar için birim testleri eklendi.

## Doğrulama ve kalan işler
- [x] `git diff --check` çalıştırıldı.
- [ ] Birim testleri ve Android derlemesi çalıştırılmalı: bu çalışma ortamında Java ve Android SDK bulunmadığından çalıştırılamadı.
- [ ] Yedi katalogdan aynı içerik/sezon/bölüm açılarak mobil ve TV oynatıcılarında, dört varsayılan altyazı sağlayıcısı ve video sağlayıcılarıyla uçtan uca test edilmeli.
- [ ] IMDb/Kitsu/AniList/Bangumi kimlikleri eksik veya çözümlenemediğinde, alternatif adlar ve sezon eşleşmeleri ayrı ayrı doğrulanmalı.
- [ ] Altyazı indirme, yerel AniSub dosyası, arşivde doğru bölüm seçimi, dil filtresi, video değiştirme ve zaman uyumu kontrol edilmeli.
- [ ] Sağlayıcıların erişimi, manifestleri, mevcut altyazıları ve gerçek hata günlükleri cihazda doğrulanmalı; tüm sağlayıcılarda sonuç veya eş zamanlama garanti edilmez.

Not: ZIP değişen/yeni çalışma ağacı dosyalarının tamamını depo içi yollarıyla içerir; APK veya derlenmiş uygulama içermez.
