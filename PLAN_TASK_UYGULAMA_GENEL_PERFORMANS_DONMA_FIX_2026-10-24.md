# PLAN TASK — Uygulama geneli performans / donma / oynatıcı yaşam döngüsü

**Tarih:** 2026-10-24  
**Durum:** Kısmi uygulama yapıldı; tam audit, derleme ve cihaz doğrulaması açık.  
**Kapsam:** Günlüklerde görülen ana-thread Simkl çağrısının yanı sıra uygulama genelinde sınırsız cache, list/scroll iş yükü, senkron ağ erişimi, player worker ve buffer yaşam döngüsü risklerini incelemek.

## Hedefler

- UI thread üzerinde senkron ağ/disk işi bırakmamak.
- Uzun oturumlarda heap büyümesini sınırlamak ve bellek baskısında geçici cache'leri temizlemek.
- Paralel range player kodunda worker yarışını, spin-wait'i, görev sızıntısını ve eksik range ilerlemesini önlemek.
- Sunulan altı Drive raporunu satır satır inceleyip her bulguyu mevcut koda, dış servise veya eksik kaynak sürümüne eşlemek.
- Sonuçları cihaz/CI doğrulamasıyla teyit etmek; doğrulanmamış iyileştirmeleri tamamlandı diye sunmamak.

## Bu değişiklik paketinde yapılanlar

- `KitsugiSimklDetailClient`: bloklayan Simkl/TMDB `execute()` çağrıları IO dispatcher'ına alındı; cancellation korunuyor; cache kapasitesi sınırlandı.
- Uzun ömürlü AniList, Bangumi, hero artwork, home hero ve Kitsu negative-ID cache'leri sınırlandırıldı.
- CloudStream DDoS-Guard host state cache/set'leri boyutlandı; kilitler sabit stripe havuzuna alındı.
- `ParallelRangeDataSource`: concurrent buffer pool, gerçek range Future'ı, timeout'lı bekleme, sliding prefetch, kaynak başına sınırlandırılmış executor, close-time cancellation ve bellek baskısı buffer temizliği eklendi. Bağımsız upstream factory yoksa tek bağlantı fallback'i kullanılıyor.
- `StreamSpeedTester`, range kaynaklarına bağımsız `DataSource.Factory` iletiyor.
- Ayrıntılı geçici bulgular ve kanıt sınırları: `docs/audits/GOOGLE_DRIVE_PERFORMANCE_LOGS_2026-10-24.md`.

## Açık işler

1. Altı Drive raporunun tamamını **satır satır** yeniden gözden geçir; her bulguyu dosya/satır ve checkout koduyla eşleştir. Özellikle stack'siz 4,754 ms Looper olayı ve AniList `updatedAt_greater` sahibi için kaynak/sürüm eşleşmesini bul.
2. Tüm `.execute()`, `Thread.sleep`, `runBlocking`, disk okuma/yazma ve ağır dönüşüm call-site'larını UI dispatcher bağlamında denetle; `RetryInterceptor` backoff etkisini ve güvenli retry stratejisini ayrıca değerlendir.
3. My List/Explore/Search/Downloads/profile ve player ekranlarında recomposition, ana-thread hesaplama, tekrarlı istek ve yaşam döngüsü ölçümü yap.
4. Uygun unit/regression testlerini ekle/çalıştır; Android APK build ve DEX doğrulamasını çalıştır.
5. Cihazda Xiaomi 23043RP34C / Android 13 veya eşdeğer cihazda cold-start, liste kaydırma, arama/keşfet, detay geçişi ve video oynatma/seek/stall testlerini kaydet.
6. Rapor ve test sonuçlarından sonra kök nedenleri güncelle; dış CDN/provider gecikmesini uygulama içi performans kusurundan ayır.

## Doğrulama durumu

- `git diff --check`: geçti.
- Değişen kaynaklar için statik assertion/inceleme: geçti.
- Gradle build ve unit test: çalıştırılamadı; ortamda Java/JAVA_HOME yok.
- Cihaz doğrulaması: yapılmadı.
- Drive raporlarının satır satır incelemesi: **tamamlanmadı**.

Bu dosya bir çalışma planı ve ara durum kaydıdır; tüm performans sorunlarının çözüldüğü iddiası değildir.
