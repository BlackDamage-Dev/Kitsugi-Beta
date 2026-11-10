# PLAN TASK — Simkl Başlık, Detay Performansı ve Bangumi Kurum Düzeltmeleri

**Tarih:** 2026-10-24  
**Dal:** `arena/bcfa72a1-kitsugi-beta`  
**Durum:** Kod uygulaması ve odaklı doğrulama tamamlandı; cihaz üzerinde performans ölçümü ve tam Android derlemesi bu ortamda çalıştırılamadı.

## Kapsam

Bu çalışma üç bildirilen sorunu birbirinden bağımsız nedenlerle ele alır:

1. Simkl arama/liste sonuçlarında İngilizce başlık tercihi seçiliyken Romaji başlığın gösterilmesi.
2. API sonucu ve liste kaydı medya detaylarında yeniden kompozisyon, ölçüm, görsel ve ilk açılış kaynaklı takılma/donma/kare düşmesi.
3. Bangumi stüdyo, yapımcı, yayıncı, ağ ve dergi/organizasyon sayfalarının veri getirememesi ve `KTS-COM-1020` hatasına düşmesi.

## Uygulanan işler

### 1. Simkl başlık varyantları

- [x] Metin aramasına `extended=full` eklendi; ayrı İngilizce ve Romaji alanları isteniyor.
- [x] `SimklTitleVariants.kt` ile ortak başlık ayrıştırması eklendi.
- [x] Sekiz sonuç oluşturma yolu aynı ayrıştırıcıya bağlandı.
- [x] Ham Romaji başlığın `titleEnglish` alanına kopyalanması durduruldu.
- [x] English, Romaji, Native ve alternatif başlıklar bağımsız korunuyor.
- [x] JSON `null` değerlerinin `"null"` metni gibi okunması engellendi.
- [x] Regresyon: ham `Kaijuu 8 Gou` + English `Kaiju No. 8`; ENGLISH/ROMAJI/NATIVE seçimleri doğrulandı.

### 2. Medya detayı performansı

- [x] Karakter, ekip, ilişki, öneri, istatistik, yorum ve bölüm `StateFlow` abonelikleri yalnızca ilgili pager sayfasına taşındı.
- [x] Arka plandaki sekme zenginleştirmelerinin hero, özet ve diğer sekmeleri gereksiz yeniden compose etmesi azaltıldı.
- [x] `DetailPageScaffold` içinde swipe offset fraction ile sayfa yüksekliği interpolasyonu kaldırıldı; her kaydırma karesindeki tekrarlı sınırsız-yükseklik ölçümü kesildi.
- [x] Karakter, ekip, öneri ve ilişki listelerinde ilk oluşturulan öğe sayısı 12 ile sınırlandı; kalan öğeler **Daha Fazla Yükle** ile açılıyor.
- [x] Dönüşümsüz hero görsellerinde hardware bitmap etkinleştirildi.
- [x] Bitmap-blur gereken yollarda software bitmap zorunluluğu korundu.
- [x] API detayında TMDB/Fanart hero çözümleme ve preload, birincil detayın ilk gösterimini bekletmeyecek şekilde arka plana alındı.
- [x] Ekran değişimi sırasında eski hero çözümlemesinin yeni sonuca yazılması fetch-key korumasıyla engellendi.
- [x] Mevcut liste kaydı taramaları `remember` ile memoize edildi ve tedarikçi lambda'nın liste değişiminde yenilenmesi sağlandı.

### 3. Bangumi kurum detayları

- [x] Kurum kökü ve yapımlar için resmî v0 `fetchPersonDetail()` akışı birincil kaynak yapıldı.
- [x] Deneysel p1, v0 yapım listesi boş/erişilemez olduğunda eksik bölümleri tamamlayan yedek olarak kullanılıyor.
- [x] v0/p1 metadata ve yapım listeleri `BangumiOrganizationFallback.kt` ile birleştiriliyor; v0 verisi p1 hatasında korunuyor.
- [x] Tıklanan kurum adı; ana ad, native, romanized, English ve alternatif adlarla doğrulanıyor.
- [x] ID/ad uyuşmazlığında ada göre yeniden çözümleme yapılıyor.
- [x] Type-2 şirket ve type-1 kişi kayıtlarına ek olarak type-3 grup/organizasyon çözümlemesi eklendi.
- [x] Coroutine iptali ağ hatası gibi yutulmuyor; sayfadan ayrılan istek iptal edilebiliyor.

## Doğrulama sonuçları

- **16 odaklı test:** 16 geçti, 0 başarısız.
  - Simkl keşif/ayrıştırma testleri ve yeni başlık-dili regresyonları.
  - Bangumi v0/p1 birleştirme, yedek ve kurum kimliği testleri.
  - Mevcut stüdyo kaynak/kimlik testleri.
- **18 değiştirilen Kotlin dosyası:** sözdizimi ayrıştırmasından geçti; yeni sözdizimi hatası yok.
- `git diff --check`: temiz.
- Kaynak invariant denetimleri:
  - Sekiz Simkl sonuç yolu varyant ayrıştırıcısını kullanıyor.
  - Metin araması `extended=full` içeriyor.
  - Medya pager yüksekliği `currentPageOffsetFraction` okumuyor.

## Doğrulama sınırları

- Bu ortamda ön yüklü Android SDK yoktu ve dış Maven/Google bağımlılık ana makinelerine erişim yoktu; bu nedenle tam Android/Gradle derlemesi çalıştırılamadı.
- Odaklı testler taşınabilir Kotlin/Java araçlarıyla, gerçek üretim yardımcı kodları ve gerçek JSON-java ayrıştırıcısı kullanılarak çalıştırıldı.
- Canlı Simkl/Bangumi API cevapları ve cihaz üzerinde FPS/kare süresi ölçümü ayrıca doğrulanmalıdır.

## Değişen dosyalar

- `app/src/main/java/com/kitsugi/animelist/AppRootDetailPages.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/BangumiOrganizationFallback.kt` (yeni)
- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiBangumiDetailClient.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/KitsugiStudioClient.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/SimklApiClient.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/SimklTitleVariants.kt` (yeni)
- `app/src/main/java/com/kitsugi/animelist/ui/components/HeroArtworkImagePolicy.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/components/KitsugiNsfwImage.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailPage.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/ApiResultDetailViewModel.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/CharactersTab.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/DetailIncrementalList.kt` (yeni)
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/DetailPageScaffold.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/MediaEntryDetailPage.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/RecommendationsTab.kt`
- `app/src/main/java/com/kitsugi/animelist/ui/screens/detail/RelationsTab.kt`
- `app/src/test/java/com/kitsugi/animelist/data/remote/KitsugiStudioBangumiFallbackTest.kt` (yeni)
- `app/src/test/java/com/kitsugi/animelist/data/remote/SimklExploreParsingTest.kt`
- `PLAN_TASK_SIMKL_DETAY_VE_BANGUMI_DUZELTMELERI_2026-10-24.md` (bu belge)
