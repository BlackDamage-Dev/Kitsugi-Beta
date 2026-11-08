# 20 — Yedi kaynak kod kontrolü ve puan/başlık düzeltmeleri

> **Görsel düzeltme 2026-11-08:** Bu belge önceki kod anını anlatır. Onaylı referansta poster üzerinde yıldız/puan veya durum rozeti yoktur. Sonraki `21_ONAYLI_GORSEL_KARSILASTIRMASI.md` uyarınca yeni ana Keşfet rafından görünür rozetler kaldırıldı; puan ve durum erişilebilirlik semantiğinde kaldı. Finite grid ayrı bir ekrandır. Gerçek ekran hâlâ bağlı değildir; güncel test sayıları için 21. belgeye bakın.

2026-11-08 · Mobil Keşfet · `arena/7e4f350c-kitsugi-beta`

## Dürüst durum

Bu denetim yeni ekranı gerçek Keşfet sayfasına bağlamaz; Android build, gerçek API ve cihaz testleri yapılmadı. Kod parçası hazırlanması, tüm uygulamanın hazır veya hatasız olduğu anlamına gelmez. Önceki raporlardaki test sayıları o zamanın anlık durumudur.

## Somut bulunanlar ve düzeltmeler

1. **Puan ölçeği:** `SimklApiClient` discovery dönüşümünde bazı puanlar (örn. 8,5 → 85) 100'lük, Compose `PreferenceHelpers.formatScore` ise 1–10 bekliyor. 85, `POINT_5`/`STARS` için negatif tekrar sayısına ve çökme riskine yol açabiliyor. Yalnız yeni mobil raf ve finite grid görüntülemesinde 11–100 değeri 1–10'a yuvarlanır; geçersiz değer gizlenir. Kaydedilen veri, kimlik ve callback'teki orijinal sonuç değiştirilmez; entry puanı önceliklidir. **Belirsizlik:** 1–10 değerleri zaten 10'luk kabul edilir; Simkl parser'larından hangilerinin düşük 100'lük değer veya zenginleştirilmiş 10'luk değer ürettiği kaynak düzeyinde açıkça belirlenmeden 1–10 aralığı güvenle ayrıştırılamaz. Gerçek servis/format davranışı doğrulanmalı; ortak eski kartlar bu mobil düzeltme kapsamına alınmadı.
2. **Başlık yerelleştirmesi:** Yeni `MobileExploreLabels.kt`, mevcut `sourceSections` raf adlarının ve kişisel Simkl film/dizi raflarının TR/EN kaynaklarını eşler; görünen başlık, kimlik ya da endpoint kararı değildir. Eski helper'ın atladığı `Yayındaki Mangalar` ve mobil Simkl `Yaklaşan Animeler` da eşlenir. Bilinmeyen katalog başlığı sessizce başka kimliğe eşlenmez; çağırana orijinal başlığa dönme seçeneği verilir.
3. **Test güvenilirliği:** İzole koşum artık `JikanSearchResult`, `MediaType`, `ExploreSourceState` tanımlarını gerçek depo kodundan çıkarır; `ExplorePayload`, platform enum, kaynak bölümleri ve mobil kod gerçekten derlenir. JUnit, R kaynak kimlikleri, PlatformErrorCode ve navigasyon hâlâ stand-in; Compose, ViewModel, Android ve ağ bu koşuma girmez. Gerçek TR/EN XML'de başlık kaynaklarının mevcut olduğu da doğrulanır.
4. **Kaynak matrisi:** Yedi kaynağın tüm `sourceSections` kategorilerine temsilî öğeler konan, TMDB karma rafları, Bangumi iki ek türü ve Simkl yaklaşan anime alanını içeren ek test eklendi. Bu yalnız sentetik veridir; canlı sonuç ya da sayfa 2 testi değildir.

## Yapılan kontroller

- **32/32 izole Kotlin kontrolü geçti** (stand-in sınırları yukarıda). Tümü + yedi kaynak × dört tür yetenekleri, kaynak/tür kimliği, özel fallback, hata/cache, hero, finite-grid yönlendirme, puan aralıkları ve başlık kaynakları kapsanır.
- 23/23 Kotlin dosyası tree-sitter ile sözdizimi yönünden ayrıştırıldı; bash syntax ve `git diff --check` geçti. Type-check/Compose plugin/Android derlemesi değildir.
- İki ek cihaz testi (100'lük skorla yıldız, orijinal sonuç callback'i) yazıldı: **54 cihaz testi yazılı, 0 çalıştırılmış**.

## Kullanıcı kabulünü engelleyen açık konular

- Gerçek ViewModel snapshot'ı ve yeni sayfa henüz uygulamanın gerçek Keşfet ekranına bağlanmadı. Kaynak sheet, pull-to-refresh, hata/retry, aktif hesap, rastgele, bildirim, detay/uzun basma, takvim/okuyucu, FAB, alt/üst bar ve geri dönüş entegrasyonu eksik.
- Şu an sadece yüklenmiş sonuçları belirten finite grid köprüsü var. Gerçek sayfa 2 ve eski full grid özellikleri, tüm raflar için doğrulanmadı; başlık/kaynak/sekmeye bağlı yanlış endpoint kullanılmamalı.
- 52 önceki + 2 yeni cihaz testi, Compose derlemesi, FOSS/GMS build ve mevcut uygulama regresyonları Android SDK/cihaz ve erişilebilir Gradle gerektirir. Canlı katalog API'leri bu ağ ortamında test edilmedi.
- Gerçek cihaz ekran görüntüsü, kart snap hareketi, bar animasyonları, büyük font ve erişilebilirlik kontrolü yapılmadan görsel ya da hareket birebir ilan edilemez.
