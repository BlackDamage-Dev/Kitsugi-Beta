# PLAN_TASK — CloudStream eklentilerinde video, ses ve altyazı denetimi

**Tarih:** 2026-10-26  
**Dal:** `arena/a5660049-kitsugi-beta`  
**Kapsam:** AnimeciX v24, Anizium v25, FilmMakinesi v72, HDFilmCehennemi v63, TrAnimeIzle v27, DiziBox v27, HDFilmIzle v11, Anizm v2  
**Durum:** Kod ve audit raporu güncellendi; canlı sağlayıcı/cihaz oynatması ve Gradle testleri doğrulanmadı.

## Amaç

Sekiz eklentinin arama/API/HTML ayrıştırma, header-cookie-JS, extractor zinciri, `ExtractorLink` metadata'sı, video/kalite, ses ve altyazı yollarını eklenti sürümü bazında incelemek; kanıtlanan eksik desteği genel player iyileştirmesiyle karıştırmadan gidermek. Dağıtılmış `.cs3` binary incelemesi kaynak snapshot'ıyla eşdeğerlik veya sağlayıcının canlı çalışma kanıtı sayılmaz. Denetlenmeyen sürümlere Anizium'a özel davranış uygulanmamalıdır.

## Plan ve uygulama durumu

- [x] Sekiz hedef eklentinin belirtilen sürümlerini envantere al; DEX'ten edinilen statik ipuçlarını çalışma zamanı kanıtından ayır.
- [x] Plugin sürüm profillerini yüklü manifest sürümüne bağla; Anizium domain/auth shim'ini yalnız denetlenmiş v25 ile sınırla.
- [x] Extractor ve parent-page header'larını birleştirme, Referer/Origin fallback'i ve istek header'larını taşıma yollarını iyileştir.
- [x] Eklenti metadata'sından kalite/ses ipuçlarını taşı; harici ses/altyazı, player ve URL format sınıflandırma yollarını güncelle.
- [x] `.mpd` adreslerinin HLS sayılmaması ve DASH olarak sınıflandırılması için düzeltme/test ekle.
- [x] Anime bölüm eşlemesinde Subbed/Dubbed `episodeData` kovalarını ayrı adaylar olarak işle; aynı `episodeData` değerini tekilleştir ve normal/sezon-gezinme yollarında adayları ayrı `loadLinks` çağrılarıyla dene.
- [x] Eşleştirici, runner, player, plugin profili, subtitle/audio ve yönlendirme davranışları için regresyon testleri ekle.
- [x] Sağlayıcı bazlı bulguları ve açık sınırları `EKLENTI_KAYNAK_BAGIMSIZ_KIMLIK_VE_ALTYAZI_TESHIS_2026-10-25.md` raporunda belgele.
- [ ] Gradle unit testlerini ve Android build'i JDK/Gradle bulunan ortamda çalıştır.
- [ ] Gerçek sağlayıcı sayfaları/API'leri ve Android cihazlarda playback, ses/altyazı/kalite seçimini doğrula.

## Açık noktalar ve kapsam sınırları

- Anizium'da Subbed/Dubbed status kovalarındaki farklı bölüm payload'ları artık deneniyor; bu, `sound_group` semantiğinin ya da manifest içi çoklu audio track seçeneklerinin Anizium'a özgü olarak doğrulandığı anlamına gelmez.
- AnimeciX `/secure/best-video` endpoint'i tek başına tüm kalite varyantlarının ayrı linkler olarak sunulduğunu kanıtlamaz.
- Anizm ve TrAnimeIzle için sezon bilgisi olmayan bölüm callback'lerinde S2+ bölüm offset'i tahmin edilmez; sezon eşleşmesi kanıtlanmadan yanlış bölüme yönlendirme yapılmamalıdır.
- `.cs3` statik incelemesi canlı site/API davranışı değildir; sağlayıcı alan adı, WAF, oturum ve değişen extractor davranışları ayrıca doğrulanmalıdır.

## Doğrulama

- `git diff --check`: başarılı.
- Tree-sitter Kotlin parse: değişmiş/yeni 68 Kotlin dosyasında 0 `ERROR`/`MISSING` düğümü.
- Gradle/JUnit ve Android build: bu ortamda Java/JDK/Gradle runtime bulunmadığından çalıştırılmadı.
- Canlı site ve cihaz playback testi: yapılmadı; tamlık garantisi verilmez.

## Dosya envanteri

Bu çalışma alanındaki tüm değişmiş ve yeni dosyaların tam yolu, durumu ve arşiv listesi ZIP içindeki `ZIP_MANIFEST.txt` dosyasındadır. `.git` geçmişi, ignored/build çıktıları ve değişmemiş repo dosyaları arşive alınmaz.
