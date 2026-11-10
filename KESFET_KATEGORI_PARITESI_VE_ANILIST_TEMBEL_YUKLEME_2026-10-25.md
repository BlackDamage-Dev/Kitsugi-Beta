# Keşfet kategori eşliği ve AniList talep üzerine yükleme

**Tarih:** 2026-10-25  
**Kapsam:** Seçili kaynak + Tümü görünümündeki kategori kısayolları/raf eşliği; AniList raflarının görünür oldukça getirilmesi; mevcut API'lerin sağladığı eksik manga türleri.

## Sonuç

Kategori kısayolları artık ayrı, sabit bir liste değil. Her kaynakta hem kısayol hem aşağıdaki içerik rafı aynı `ExploreSourceSection` listesinden üretiliyor. Böylece desteklenmeyen/boş bir kategori, veri rafı varmış gibi gösterilmiyor; kategoriye tıklamak boş başlangıç listesiyle tam ekranı açsa bile tam ekran ilk sayfayı kaynaktan istiyor.

AniList'in keşfet ekranı artık 15 desteklenen rafın modelini koruyor. Açılışta altı temel anime rafı tek alias sorgusunda istenir; diğer raflar LazyColumn'da görünür olduklarında 180 ms'lik kısa bir birleştirme penceresiyle en fazla altışar alias halinde çekilir. İstekler parça parça ve sırayla gönderilir; hata durumunda her raf için tekil yedek istek fırtınası başlatılmaz. Tam ekran kategoriye tıklanınca ilk sayfa, aşağı kaydırıldıkça devam sayfaları yüklenir.

## Kaynak yetenek denetimi

| Kaynak | Korunan / tamamlanan raflar |
|---|---|
| AniList | Popüler, trend ve yüksek puanlı anime; yayındaki/yaklaşan anime; anime filmi; bu sezon; yeni anime; popüler/trend/yüksek puanlı/yayındaki/yeni manga; manhwa/manhua (KR + CN menşei); novel |
| MyAnimeList | Anime ve manga sıralamaları, yayın durumları, sezon/film, yeni eklenenler; manhwa/manhua ve novel türleri |
| Kitsu | Anime/manga sıralamaları ve yayın durumları; API subtype filtresiyle manhwa/manhua ve novel rafları |
| Shikimori | Anime/manga ve yerel tür rafları; `id_desc` sıralamasıyla yeni eklenen anime/manga rafları |
| Bangumi | Anime/manga, sezon/yayın, REAL TV/film; `BOOK_COMIC` tarih sıralamasıyla yeni manga; `BOOK_NOVEL` roman rafı |
| TMDB | Kaynağın sunduğu anime odaklı film, film/dizi, popüler/yüksek puanlı ve yaklaşan kataloglar; gerçek film/dizi grupları korunur |
| Simkl | Anime, film ve dizi trend/popüler/yayındaki rafları; video rafları manga grubu altında gösterilmez |

TMDB ve Simkl gerçek manga kataloğu sunmadığından manga rafı uydurulmaz; kendi film/dizi katalogları aynı ortak kısayol/raf bileşeninde doğru medya grubuyla gösterilir.

## AniList istek bütçesi

- İlk görünüm: 6 kategori → 1 alias GraphQL isteği.
- Kaydırma: yalnız görünür olan desteklenen raflar sıraya alınır; kısa debounce yakın rafları tek istekte birleştirir.
- Manhwa/manhua: tek kullanıcı rafı, `countryOfOrigin` için `KR` ve `CN` alias'ları; bir ülke isteği başarısızsa kategori tekrar denenebilir.
- Tam ekran: mevcut sayfalama sözleşmesi korunur; yeni sayfa yalnız kullanıcı kaydırdığında çağrılır.
- Başarısız raf: otomatik tekrar döngüsü yok; raf üzerinde açık bir yeniden dene eylemi bulunur.

## Değişen ana noktalar

- `ExploreCategoriesSection` ve `sourceExploreSections` aynı kategori modelini kullanır.
- `AllSourcesExploreContent` de aynı raf satırı bileşenini kullanır; kaynaklar kendi başlıkları altında kalır.
- `ExploreViewModel` AniList kategori durumlarını (yüklenmedi/yükleniyor/yüklendi/başarısız) tutar ve başarılı parçaları bellek/disk önbelleğine birleştirir.
- Kitsu, Bangumi ve Shikimori'nin sağlayabildiği eksik tür/yeni eklenen rafları kaynak istemcileri, keşfet payload'u ve tam ekran sayfalamasına bağlanmıştır.

## Doğrulama

`AllSourcesExploreTest` için kategori sırası, AniList'in 15 raf modelinin korunması, başlangıçta altı raflık sınır, KR/CN manhwa sorguları, kaynak ayrımı ve yeni yerel tür rafları testleri eklendi. Kotlin kaynakları Tree-sitter ile sözdizimsel olarak tarandı; sandbox'ta JDK/Java bulunmadığından Gradle derlemesi/birim testleri çalıştırılamadı.
