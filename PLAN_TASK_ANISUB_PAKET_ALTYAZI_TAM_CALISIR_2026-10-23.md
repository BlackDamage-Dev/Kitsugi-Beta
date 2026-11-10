# PLAN_TASK — AniSub.co Paket Altyazılarının Tam Çalışır Hâle Getirilmesi (2026-10-23)

## Amaç

AniSub.co eklentisi etkin olduğunda; film, dizi ve özellikle anime içeriklerinde AniSub üzerindeki Türkçe altyazılar bulunacak, altyazı paket hâlindeyse indirilecek/açılacak veya paketin içindeki doğru bölüm dosyası seçilecek ve oynatıcıda Türkçe altyazı olarak görünecektir.

## Canlı teşhis özeti

- [x] AniSub `manifest.json` canlı olarak doğrulandı.
- [x] Resmî Stremio ucunun yalnızca IMDb `tt` kimliklerini tanıdığı doğrulandı.
- [x] Stremio indirme ucunun paketlerde bölümden bağımsız olarak ilk dosyayı verdiği doğrulandı; bu nedenle düz Stremio akışının paketlerde yeterli olmadığı tespit edildi.
- [x] `/api/subtitles?q=` arama API'sinin kayıt ve AniList kimliği döndürdüğü doğrulandı.
- [x] `/api/subtitle/{id}/archive` ve `.../archive/download?file=` uçlarının ZIP paket içinden tek bölüm dosyası verdiği doğrulandı.
- [x] RAR paketlerde sunucu arşiv API'sinin destek vermediği; RAR açılışının cihazda yapılması gerektiği doğrulandı.
- [x] Tek dosyalık kayıtların CDN üzerinden doğrudan `.ass/.srt` verdiği doğrulandı.

## Tespit edilen kök nedenler

- [x] Eski Inertia HTML kazıma akışı ve güncel API alanlarıyla uyumsuz DTO/filtre mantığı.
- [x] Cookie sürekliliği olmadan Laravel/Inertia isteği yapılması riski.
- [x] Yalnızca ZIP açılabilmesi; RAR ve düz metin altyazıların elenmesi.
- [x] Paketten çıkan `file://` sonucunun `SubtitleFileCache` tarafından HTTP isteği sanılması ve oyuncuya hiç ulaşamaması.
- [x] Uzantısız ASS URL'lerinin SRT sanılması.
- [x] Bölüm bulunamadığında paketteki ilk dosyanın yanlış bölüm olarak döndürülmesi.
- [x] Yalnızca belirli kimlik türlerinde arama yapılması ve aynı içerik için paralel sorgularda gereksiz tekrarlar.

## Uygulanan kod görevleri

- [x] Yeni `AniSubSubtitleClient` oluşturuldu.
  - [x] IMDb ile resmî Stremio ucundan aday keşfi.
  - [x] Başlık ve başlık varyantlarıyla `/api/subtitles?q=` keşfi.
  - [x] AniList ID ile kesin eşleştirme; yoksa başlık benzerliği.
  - [x] Türkçe aday önceliği ve dil etiketlerinin normalizasyonu.
  - [x] ZIP paketlerinde arşiv API'si üzerinden doğru tek bölüm dosyası.
  - [x] RAR/ZIP/GZIP paketlerinde cihazda indirme + açma yedeği.
  - [x] Tek Dosya `.ass`, `.ssa`, `.srt`, `.vtt` kayıtlarını doğrudan kullanma.
  - [x] Daha önce açılmış paketleri `cacheDir/anisub_subs/{subtitleId}` altında yeniden kullanma.
  - [x] Paralel aynı içerik sorgularını kanonik anahtarla tekilleştirme ve 10 dakikalık sonuç önbelleği.
  - [x] Yanlış bölüm döndürmeme kuralı.

- [x] Yeni `AniSubEpisodeMatcher` oluşturuldu.
  - [x] `SxxEyy`, `NxNN`, `- NN`, `[NN]`, `(NN)`, `Ep NN`, `Bölüm NN`, `_NN_` adlandırmaları.
  - [x] Çözünürlük, codec, yıl, FPS, CRC ve başlık içi sayılarının ayıklanması.
  - [x] `12.5` ara bölümlerinin tam bölüm sayılmaması.
  - [x] `Paket`, `Bölüm: NN`, `1-13` kayıt metalarının bölüm uygunluğu.
  - [x] Birden fazla eşleşmede ASS ve güncel revizyon önceliği.

- [x] Yeni `SubtitleArchiveExtractor` oluşturuldu.
  - [x] Magic byte ile ZIP/RAR/7z/GZIP/düz metin tanıma.
  - [x] ZIP için UTF-8, windows-1254, Shift_JIS, windows-1252 deneme sırası.
  - [x] RAR için Junrar ile cihazda açma.
  - [x] Dosya sayısı/boyutu sınırları ve yolları düzleştirerek zip-slip koruması.

- [x] `AddonStreamClient` içindeki eski AniSub kazıma akışı kaldırıldı.
- [x] AniSub manifesti gerçek manifestten okunur hâle getirildi; ağ hatasında ihtiyati tanım bırakıldı.
- [x] `SubtitleResponseItem.format` alanı eklendi.
- [x] `SubtitleRepositoryImpl`, eklentinin bildirdiği biçimi URL tahmininden önce kullanacak şekilde güncellendi.
- [x] `SubtitleFileCache`, `file://` ve mutlak yerel yolları indirmeye çalışmadan kabul edecek şekilde güncellendi.
- [x] `AnimeDownloader`, çevrimdışı indirmede yerel altyazıları videonun yanına kopyalayacak şekilde güncellendi.
- [x] RAR için `com.github.junrar:junrar:7.5.5` bağımlılığı eklendi.
- [x] AniSub bölüm/Arşiv mantığına JVM birim testleri eklendi.
- [x] CI test filtresine AniSub test paketi eklendi.
- [x] Ayrıntılı kök neden ve çözüm raporu yazıldı.

## Paketteki değişen dosyalar

1. `.github/workflows/android-build.yml`
2. `COZUM_RAPORU_ANISUB_PAKET_ALTYAZI_2026-10-23.md`
3. `PLAN_TASK_ANISUB_PAKET_ALTYAZI_TAM_CALISIR_2026-10-23.md`
4. `app/build.gradle.kts`
5. `app/src/main/java/com/kitsugi/animelist/core/download/AnimeDownloader.kt`
6. `app/src/main/java/com/kitsugi/animelist/core/player/SubtitleFileCache.kt`
7. `app/src/main/java/com/kitsugi/animelist/data/remote/AddonStreamClient.kt`
8. `app/src/main/java/com/kitsugi/animelist/data/remote/anisub/AniSubEpisodeMatcher.kt`
9. `app/src/main/java/com/kitsugi/animelist/data/remote/anisub/AniSubSubtitleClient.kt`
10. `app/src/main/java/com/kitsugi/animelist/data/remote/anisub/SubtitleArchiveExtractor.kt`
11. `app/src/main/java/com/kitsugi/animelist/data/repository/SubtitleRepositoryImpl.kt`
12. `app/src/test/java/com/kitsugi/animelist/data/remote/anisub/AniSubEpisodeMatcherTest.kt`
13. `app/src/test/java/com/kitsugi/animelist/data/remote/anisub/SubtitleArchiveExtractorTest.kt`

## Kullanıcıda kalan doğrulama görevleri

> Not: Kullanıcının 2026-10-23 tarihli isteğiyle derleme ve cihaz testleri kullanıcıya bırakıldı. Aşağıdaki kutular test edildikçe işaretlenmelidir.

- [x] `./gradlew :app:testFossDebugUnitTest --tests 'com.kitsugi.animelist.data.remote.anisub.*'` başarılı.
- [ ] Debug APK derlemesi başarılı.
- [ ] ZIP Paket örneği: paketin 2. ve sonraki bölümlerinden biri açıldığında, 1. bölüm değil ilgili bölümün Türkçe altyazısı geliyor.
- [ ] RAR Paket örneği: paket cihazda açılıyor ve ilgili bölümün Türkçe altyazısı geliyor.
- [ ] Tek bölüm ASS kaydı doğrudan geliyor.
- [ ] Tek bölüm SRT kaydı doğrudan geliyor.
- [ ] IMDb kimliği bulunan anime/filmde AniSub sonucu listeleniyor.
- [ ] IMDb kimliği olmadan AniList/Kitsu/başlık yoluyla oynatılan animede AniSub sonucu listeleniyor.
- [ ] Paketin içermediği bir bölümde yanlış bölüm altyazısı gösterilmiyor.
- [ ] Aynı paketin ikinci kez açılan farklı bölümünde yerel önbellekten hızlı sonuç geliyor.
- [ ] Aynı bölümün birden fazla Türkçe sürümü varsa sonuçlar birbirini ezmeden listeleniyor.
- [ ] Çevrimdışı video indirmede AniSub altyazısı videonun yanına doğru uzantıyla kaydediliyor.
- [ ] Başka çalışan altyazı eklentilerinde gerileme yok (özellikle OpenSubtitles, AltyaziDB, YTS).

## Kabul kriterleri

- AniSub üzerinde eşleşen Türkçe altyazı varsa oynatıcı altyazı listesinde Türkçe olarak görünmesi.
- ZIP/RAR Pakette oynatılan bölümle aynı bölüm dosyasının seçilmesi.
- Yanlış bölüm dosyasının sessizce gösterilmemesi.
- Tek dosyalık ASS/SRT kayıtlarının paketlenmiş sanılmadan çalışması.
- Açılmış paketlerin sonraki bölümlerde yeniden indirilmeden kullanılması.
- Diğer altyazı eklentilerinin davranışında değişiklik olmaması.

## Bilinen teknik sınır

- 7z paketler için cihazda açıcı eklenmedi. Böyle bir kayıtla karşılaşılırsa film/1. bölüm dışında yanlış bölüm riski nedeniyle sonuç üretilmez. ZIP ve RAR, canlı örneklerde görülen ana Paket biçimleridir ve ikisi de kapsanmıştır.
