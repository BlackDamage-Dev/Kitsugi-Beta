# KÖK NEDEN RAPORU — Eklentiler neden katalog kaynağına göre çalışıp Torrentio neden hep çalışıyor? + ORTAK KÜME ÇÖZÜMÜ

**Tarih:** 2026-11-04 · **Sürüm:** v2.4.341 · **Dal:** `arena/1b6df94d-kitsugi-beta`

**Şikâyet (özet):** Torrentio; film/dizi/anime fark etmeksizin sonuçları anında, eksiksiz sunuyor. CloudStream eklentilerinde ise aynı eser, eklentinin kendi aramasından bulununca çalışıyor ama AniList/TMDB/MAL üzerinden girince (veya detayda katalog kaynağı değişince) bazı eklentiler (AnimeCix, Anizm, TrAnimeIzle) video verisi veremiyor. HDFilmCehennemi genelde sorunsuzken FilmMakinesi filmlerde çalışıp dizilerde tıkanabiliyor. Soru: sorun uygulamada mı eklentilerde mi; hepsi nasıl ortak bir kümede çalışır?

---

## 1. İki mimari arasındaki gerçek fark

| Aşama | Stremio/Torrentio yolu | CloudStream (kazıyan/scraper) yolu |
|---|---|---|
| Girdi | Tek kesin kimlik: IMDb `tt...` (+sezon/bölüm) veya `kitsu:...` | Başlık + alternatif başlıklar + yıl + (varsa) ID'ler |
| İstek sayısı | **1 HTTP GET**: `/stream/{type}/{id}.json` (`AddonStreamClient.fetchStreams`) | Eklenti başına zincir: arama (≤18 varyant) → `load` → (dizi ise bölüm eşleme/sezon gezinmesi) → `loadLinks` → extractor/embed çözümleme |
| İşi yapan | Sunucu tarafı önceden indekslenmiş katalog | Her eklentinin kendi `search/load/loadLinks` kodu (CloudStream `MainAPI` açık metodları; her eklenti kendisi override eder) |
| Eşleme | Yok — kimlik birebir | `CsTitleMatcher` benzerlik eşiği + `syncData` ile ID doğrulaması |
| Başarısızlık yüzeyi | Kimlik çözülemezse boş (o da olur: IMDb çözülemezse Stremio yolu da boş döner) | Zincirin her halkası ayrı kopabilir |

Kod kanıtı: `StreamViewModel.kt` kimliği tek seferde çözüp `StremioVideoId.forContent` ile `tt...:S:E` üretir ve paralel tekil istekler atar. Buna karşılık `CsStreamRunner.runGetStreams` eklenti başına sırayla şu halkaları çalıştırır: tür filtresi → native ID (`supportedSyncNames`/`getLoadUrl`, yalnızca beyan eden eklentilerde) → başlık varyant araması → eşik eşleme → syncData doğrulaması → `loadAndExtractStreams` (film: doğrudan `loadLinks`; dizi: `CsEpisodeMatcher` bölüm eşleme + gerekirse sezon sayfasına gezinme) → embed extractor zinciri.

## 2. "Kaynak değiştirince tıkanıyor"un kök nedeni

Sorun tek taraflı değil; **iki tarafın birleşimi**:

1. **Uygulama tarafı (değiştirilebilir girdiler):** Detay sayfasında seçilen katalog kaynağı, İzle hattına giden veriyi değiştirir:
   - AniList: her sezon ayrı girdi (sezona hep "1" denir), romaji/İngilizce başlıklar, MAL/AniList ID'leri.
   - TMDB: tek girdi, çok sezon; Türkçe başlık olasılığı; TMDB/IMDb ID'leri.
   - Eklentinin kendi araması: **doğrudan içerik URL'si** (`getStreamsForUrl`) — arama ve eşleme halkaları hiç çalışmaz. **O yüzden "eklentiden buldum, çalışıyor; AniList'ten girdim, çalışmıyor."**
2. **Eklenti tarafı (her birinin kendi mantığı):** `supportedSyncNames` beyan etmeyen eklenti ID ile açılamaz; başlık aramasına mahkûmdur. Arama sorgusunun dili/biçimi (romaji vs Türkçe vs ASCII), sitenin o anki HTML yapısı, bölüm listesini doldurma biçimi eklentiden eklentiye değişir.
3. **Film ≠ Dizi mekaniği:** Filmde içerik sayfasının kendisi video sayfasıdır (tek adım). Dizide sayfa → sezon/bölüm eşleme → bölüm sayfası zinciri gerekir. FilmMakinesi'nin filmlerde çalışıp dizilerde tıkanması bu yüzdendir: kopan halka bölüm eşlemesi/sezon gezinmesidir, video çıkarımı değil. AnimeCix'in AniList dışında tıkanması da aynı desenin anime tarafıdır (kimlik/başlık beklentisi AniList modeline göre kuruluyken TMDB modeliyle gelen girdi eşleşmez).

**Sonuç:** Tek bir "bozuk yer" yok; zincir, girdi modeline duyarlı. Torrentio hızlı/kesin çünkü zinciri yok; kimliği sunucuda indeksli.

## 3. Ortak küme çözümü (v2.4.341 ile uygulandı)

Eklentilerin içini tek tek yamamak yerine, Torrentio'nun determinizmi uygulama tarafında ortak bir katmanla yeniden kuruldu:

1. **Kanonik kimlik zarfı — `CsUnifiedIdentity`:** Eser hangi kaynaktan gelirse gelsin; bilinen tüm kimlik uzayları (IMDb, TMDB film/dizi ayrımlı, MAL, AniList, Kitsu, Bangumi) + başlık/yıl takma adı tek bir anahtar kümesine indirgenir.
2. **Öğrenen eklenti-URL hafızası — `CsProviderUrlMemory` (kalıcı):** Bir eklenti bir eserin içerik sayfasını bir kez bulduğunda (yol önemsiz: kendi araması/native ID/başlık eşlemesi), URL bu anahtarların **hepsine** kaydedilir.
3. **Yeni çözüm sırası (`CsStreamRunner.runGetStreams`):** **Hafıza (doğrudan URL) → Native ID → Başlık arama/eşleme.** Kullanıcı esere başka katalog kaynağından girse bile çözülen kimliklerden biri ortaksa kayıt bulunur, kırılgan halkalar hiç çalışmaz. Ölü URL kaydı düşürülür ve alt yollar aynen denenir — hafıza engel değil kestirmedir.
4. **Öğrenme noktaları:** (a) `StreamViewModel` doğrudan-URL (eklenti araması) başarısı, (b) native `getLoadUrl` başarısı, (c) başlık arama+eşleme başarısı.

### Dürüst sınırlar
- Hafıza **ilk bulunuşu** hızlandırmaz; ilk kez hiç bulunamamış bir eserde çözüm yine eklentinin arama/eşleme gücüne kalır. Ortak küme, "bir kez bulundu mu bir daha kaybolmaz" garantisidir.
- Sitesi kapanmış/yapısı tamamen değişmiş eklentiyi uygulama tarafı kurtaramaz; o eklentinin kendi güncellemesi gerekir (ayrı repo: Kitsugi-Plugins).
- Kimlik çözümleyicisi (ARM/TMDB/Wikidata) hiçbir ortak kimlik üretemezse anahtar yalnızca başlık+yıla düşer; o da yoksa eski davranış geçerlidir.

## 4. Değişen dosyalar

| Dosya | Değişiklik |
|---|---|
| `data/cloudstream/CsUnifiedIdentity.kt` | **Yeni.** Kanonik kimlik + takma ad anahtarları |
| `data/cloudstream/CsProviderUrlMemory.kt` | **Yeni.** Kalıcı (SharedPreferences) + oturum içi öğrenen URL hafızası (1500 kayıt budama) |
| `data/cloudstream/CsStreamRunner.kt` | Çözüm sırasına hafıza adımı; native ve eşleme başarılarında öğrenme |
| `data/cloudstream/CsRuntimeInit.kt` | Uygulama açılışında hafızayı yükle |
| `ui/screens/stream/StreamViewModel.kt` | Doğrudan-URL başarısında kimliklerle öğrenme |
| `app/build.gradle.kts` | 2.4.340 → 2.4.341 |
| `app/src/test/.../CsUnifiedIdentityTest.kt`, `CsProviderUrlMemoryTest.kt` | **Yeni** birim testler |

## 5. Doğrulama

- Birim testler: `./gradlew :app:testFossDebugUnitTest --tests "*CsUnifiedIdentityTest" --tests "*CsProviderUrlMemoryTest"` (bu çalışma ortamında JDK/Android SDK olmadığından derleme cihaz/CI tarafında koşulmalıdır).
- Cihaz senaryosu: (1) AnimeCix aramasından bir anime aç → oynat. (2) Aynı animeyi AniList detayından aç → logda `🧠 Ortak küme hafızası isabeti` gör ve kaynakların aramasız geldiğini doğrula. (3) Kaynağı TMDB'ye çevirip tekrar aç → yine hafıza isabeti beklenir.
