# 02 — Yedi kaynak / dört tür / devam doğruluğu

Bu tablo mevcut `ExploreViewModel` + `AllSourcesExplore.sourceSections` ve `FullScreenMediaGridPage` kodu okunarak hazırlandı; canlı API sonucu veya endpoint kullanım şartları doğrulanmadı. ✓ mevcut aday raf; — bu uygulama akışında bilinen raf yok; ? mevcut semantik veya sayfalama doğrulanmalı. Yükleyicide bir rafın tanımlı olması her zaman dolacağı anlamına gelmez.

| Kaynak | Diziler | Filmler | Anime | Manga | Özel dikkat |
|---|---|---|---|---|---|
| AniList | — | — | ✓ popüler/trend/sezon/yayın/yaklaşan + anime filmi | ✓ popüler/trend/yayın/yeni/puan/roman | Tek seçimde Kitsu tam/kısmi fallback var; gerçek öğe kaynağını koru. `NOVELS` var; `MANHWA_MANHUA` için FullScreen sayfalaması boş. |
| MAL/Jikan | — | — | ✓ en iyi/trend/sezon/yayın/yaklaşan + anime filmi | ✓ en iyi/trend/yayın/yeni/manhwa/roman | Sorgu hızı; bazı endpoint'lerde yetişkin filtresi sayfa devamında tekrar doğrulanmalı. |
| TMDB | ✓ trend/popüler/en yüksek puan | ✓ trend/popüler/en yüksek puan | ✓ anime aday rafları | — | Anime endpoint TV + film döndürebilir: `MediaType.Movie` bir anime filmi olabilir. Başka tarafta aynı film genel film listesinde de olabilir; görsel sekme = kaynak rafı semantiği, salt `type` değil. |
| Simkl | ✓ trend/en iyi/yayında | ✓ trend/yeni | ✓ trend/en iyi/yayında | — | İlk raf `getBestMedia`, grid bazı kategoride farklı çağrı/finite chart; eşitlik test edilmeden Tümünü gör kabul değil. Girişli kişisel listeler ayrı. |
| Kitsu | — | — | ✓ popüler/trend/puan/sezon/yayın/yaklaşan | ✓ popüler/trend/puan/yayın/yeni | Ortak takvim kendi yayın saati garantisi değildir. |
| Shikimori | — | — | ✓ en iyi/trend/sezon/yayın/yaklaşan | ✓ en iyi/trend/yayın/manhwa/roman | Yetişkin filtreleri ve sayfa devamı; erişim/limitler. |
| Bangumi | ✓ REAL dizi | ✓ REAL film | ✓ en iyi/trend/puan/sezon/yayın | ✓ en iyi/trend/puan/yayın | `bangumiSubjectType`: 1 kitap, 2 anime, 6 REAL; dizi/film ayrımı REAL endpoint semantiğiyle doğrulanmalı. |
| Tümü | ✓ TMDB/Simkl/Bangumi | ✓ TMDB/Simkl/Bangumi | ✓ kaynak bazlı raflar | ✓ kaynak bazlı raflar | Her gerçek kaynak ayrı etiket + category; kaynakların sayıları/puanları toplanıp global trend gibi sunulmaz. |

## Eski adların gerçek anlamı — koddan örnekler
`sourceSections(TMDB)`: `AIRING_ANIME` → Trend Diziler, `TOP_MANGA` → Popüler Diziler, `MOVIE_ANIME` → Trend Filmler, `PUBLISHING_MANGA` → En Yüksek Puanlı Filmler, `TRENDING_MANGA` → En Yüksek Puanlı Animeler. `sourceSections(SIMKL)`: `TOP_ANIME` → Trend Diziler, `TOP_MANGA` → En İyi Diziler, `MOVIE_ANIME` → Trend Filmler, `TRENDING_MANGA` → Yayındaki Animeler, `UPCOMING_ANIME` → Yeni Filmler. Aynı enum farklı platformda farklı anlam taşır; sadece isim veya sadece `JikanSearchResult.type` kullanmak yeterli değildir.

TMDB `getTrendingMedia` anime rafı hem TV hem film sorgular; `TmdbDiscoverClient.parseTmdbDiscoverList` film sonuçlarına `Movie` atayabilir. **Anime rafının içindeki Movie = anime filmi olabilir**; film-dizi-anime ayrımı için önce rafın kaynak/kategori semantiği, sonra sonucu doğrulama gerekir. Çelişki/kimlik belirsizse yanlış sekmeye zorla aktarma; test et ve kontrollü sakla. Anime filmleri anime rafında kalır; TMDB'nin genel film rafında ayrı listelenmesi mevcut kaynak kapsamına bağlıdır; bu bir çapraz-kaynak birleştirme değildir.

## 32 kombinasyonun işletimi
Her satır × her tür için test koşulur (8 kaynak modu × 4 tür = 32). Desteklenen seçili kaynakta veri varsa yalnız ilgili gerçek raflar; kesin destek yoksa "bu kaynakta bu tür bulunmuyor" + kaynak seçimi. Destek var ama sonuç boşsa boş sonuç; başarısız ağ ise hata/yeniden dene; önbellek varsa eski veri + uyarı; yükleniyorsa skeleton. **Önemli:** Bugün birçok loader hatayı `runCatching().getOrDefault(emptyList())` ile yutuyor. Bu yüzden boş/başarısız ayrımı henüz her rafta güvenilir değil; yalandan "desteklenmiyor" demek yasak. Yapılabilir ayrım için `09` görevinde önce doğrulama ve gerekiyorsa eklemeli durum modeli planlanır.

## Devam sayfası ilkesi
- `AppRoot.openFullScreenSection` source + enum + sonuç taşır; ilk sayfanın türü/sıralaması sayfa 2 ve filtreli sorgularda da korunmalı.
- `TOP_ANIME`/`TOP_MANGA` gibi enum tek başına yetersiz; source+category ve gerektiğinde **ayrı mobil shelf origin/query spec** olmalı. Karma rafı türe bölüp mevcut genel endpoint ile devam ettirme; destek yoksa tür-özel raf kullan veya sonlu listeyi doğru etiketle.
- Özellikle Simkl `fetchPage`/`LaunchedEffect` gerçek dönüşleri ve tekrarlar; AniList manhwa `emptyList()`; TMDB karma/yaklaşan, Bangumi REAL ve server/local filtre sınırları fixture ve canlı denemeyle ayrı test edilmeli. Kişisel raf tanımlaması `title.contains` ile yapılıyor; metin değişikliği yanlış sayfalamaya yol açabilir. Bu akışı bozmadan explicit kimlik için ayrı tasarım kararı gerekir.
