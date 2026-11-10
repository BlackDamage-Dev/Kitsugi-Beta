# PLAN TASK — AniSub.co altyazı eklentisi: ID eşleştirme ve paket bölüm seçimi

**Tarih:** 2026-10-22  
**Durum:** Uygulandı; Android/Gradle testleri bu çalışma ortamında Java/JDK bulunmadığından çalıştırılamadı.

## Kullanıcı ihtiyacı

AniSub.co eklentisi IMDb veya AniList kimliğiyle doğru animeyi bulmalı; Türkçe altyazı paketlerinde oynatılan bölüme ait altyazıları paketten ayıklayıp birden fazla uygun sonucu gösterebilmeli. Paket içinden yanlışlıkla ilk dosyanın seçilmesi önlenmeli.

## İnceleme / mevcut akış

- Uygulamadaki `AddonStreamClient` AniSub manifest isteğini özel arama akışına yönlendiriyor ve oyuncudan `aniListId`, anime başlığı ve bölüm numarasını alabiliyor.
- Depodaki AniSub CLI kaynak/README materyali, arama için Inertia tabanlı `/tum-altyazilar?q=...` sayfalamasını ve indirme bilgisinin `/api/subtitles/{id}/download` üzerinden alındığını gösteriyor. Yanıtın indirme URL'si arşiv içeriğine yönlendirebiliyor.
- Önceki uygulama tek arama sayfasıyla sınırlıydı; boş başlıkta ID fallback'i güvenilir değildi; toplu altyazı adı bölüm filtresinden geçse bile arşivden yalnızca ilk/eşleşmeyen dosya dönebiliyordu.

## Uygulama planı ve tamamlananlar

1. **Kimlikten doğru başlığa ulaşma**
   - AniList ID’si parametreden veya `anilist:<id>` biçiminden alınır; başlık yoksa AniList GraphQL ile başlık çözülür.
   - IMDb `tt...` kimliğinde başlık yoksa Cinemeta meta endpoint'iyle başlık çözülür.
   - AniSub kayıtlarındaki AniList ID varsa kesin kimlik eşleştirmesi uygulanır; başlık karşılaştırması fallback olarak kullanılır.
2. **Arama ve bölüm eşleştirme**
   - AniSub Inertia sürümü ana sayfadan okunur.
   - Arama sayfaları sınırlı sayıda taranır; bölüm ve anime eşleşmeleri filtrelenir.
   - `S01E05`, `2x05`, `Episode/Bölüm 05`, aralıklar ve batch/sezon paket adları desteklenir.
3. **Paket arşivinden sonuçları seçme**
   - Eşleşen `.ass`, `.ssa`, `.srt` ve `.vtt` dosyalarının tamamı ayrı altyazı sonucu olarak döndürülür.
   - Bir paket için bölüm dosyası eşleşmesi yoksa ilgisiz ilk dosyaya sessiz fallback yapılmaz.
   - Arşiv dışında doğrudan gelen altyazı içeriği de desteklenir; boyut ve çıkarım sınırları uygulanır.
   - AniSub Türkçe kaynağı olduğundan sonuç dili `tr` olarak normalize edilir.
4. **Otomatik testler**
   - Release adı, bölüm aralığı, yaygın dosya adı biçimleri, çoklu dosya seçimi ve güvenli fallback için `AniSubSubtitleMatcherTest` eklendi.

## Değişen dosyalar

- `app/src/main/java/com/kitsugi/animelist/data/remote/AddonStreamClient.kt`
- `app/src/main/java/com/kitsugi/animelist/data/remote/AniSubSubtitleMatcher.kt`
- `app/src/test/java/com/kitsugi/animelist/data/remote/AniSubSubtitleMatcherTest.kt`
- `PLAN_TASK_ANI_SUB_CO_IMPROVEMENTS_2026-10-22.md` (bu dosya)

## Doğrulama / sınırlamalar

- `git diff --check`: başarılı.
- `./gradlew :app:testDebugUnitTest --tests 'com.kitsugi.animelist.data.remote.AniSubSubtitleMatcherTest'`: başlatılamadı; ortamda `java`/`JAVA_HOME` yok.
- AniSub canlı servisine uçtan uca istekle doğrulama yapılmadı; servis yanıt biçimi depoda bulunan istemci/CLI materyaline göre ele alındı.
