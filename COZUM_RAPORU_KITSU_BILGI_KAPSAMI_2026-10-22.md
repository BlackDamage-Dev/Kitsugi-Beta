# ÇÖZÜM RAPORU — Kitsu "Bilgiler" Kapsamı Genişletme (2026-10-22)

## 1. Şikâyet / Belirti

Kitsu kaynağının detay sayfasındaki **Bilgiler** kartı diğer kaynaklara (AniList/MAL) kıyasla çok boş:
Durum, Sezon, Başlangıç, Bitiş, Stüdyo, Süre, Yaş Sınırı, İngilizce, Diğer Adlar dışında neredeyse
hiç alan yoktu. "Harici bağlantılar" kartında yalnızca **Kitsu** bağlantısı vardı; **İstatistikler**
kartında "Oy Sayısı" hep bilinmiyordu. Manga sayfaları iyice yalnıştı: istatistik kartı hiç
görünmüyor, format/cilt/dergi bilgisi yoktu.

## 2. Kök Neden Denetimi (Kitsu API'de var olan ama hiç alınmayan alanlar)

Kitsu JSON:API tek istekte `include=` ile çok daha fazla veri veriyor; uygulama yalnızca
`include=categories` istiyor ve `attributes` içindeki pek çok alanı okumuyordu:

| Kitsu alanı | Eski durum | Yeni durum |
|---|---|---|
| `subtype` | Yalnızca arama kartı alt başlığında; detayda YOK | `format`/`rawFormat` → Bilgiler'e **"Format"** satırı (TV/Film/OVA/ONA/Özel/Müzik/Manga/Manhwa/Manhua/Novel/One-Shot/Doujin/OEL) |
| `season` + `seasonYear` | Okunmuyor; sezon başlangıç tarihinden **tahmin** ediliyordu | `rawSeason`/`seasonYear` → yetkili sezon verisi + sezona tıklayarak arama |
| `ratingFrequencies` | Okunmuyor → "Oy Sayısı" istatistiği boş | Toplanıp `scoredBy` → İstatistikler kartında gerçek oy sayısı |
| `animeProductions.producer` | İstem yok; stüdyo ancak TMDB zenginleştirmesinden geliyordu | **Stüdyo / Yapımcılar / Lisanslayanlar** çipleri Kitsu'nun kendi üretim verisinden (TMDB önceliğinde) |
| `mappings` | İstem yok → Harici bağlantılar yalnızca Kitsu; `realMalId` hiç bilinmiyordu | **MyAnimeList / AniList / AniDB** bağlantıları Harici Bağlantılar'a + **gerçek MAL ID** → Jikan/AniList zenginleştirmesi, yedek zincirler ve çapraz senkron güçlendi |
| `titles.en_us` | Okunmuyor (bazı kayıtlarda `en` yoktu) | İngilizce ad için `en` → `en_us` → canonical zinciri |
| `titles.zh_cn` | Okunmuyor (donghua'da ja_jp yok) | Özgün ad için `ja_jp` → `zh_cn` zinciri |
| `posterImage` (detay) | `medium` (küçük çözünürlük) | `large` → `original` → `medium` |
| Manga: `volumeCount` | Okunmuyor | **"Cilt"** satırı |
| Manga: `serialization` | Okunmuyor | Stüdyolar kartında **"Dergiler"** çipi (ad-temelli dergi araması) |
| Manga: `averageRating`→puan kartı | Yalnızca `score`; kart gizliydi | `meanScore`/`averageScore`/`members`/`favorites`/`rank`/`popularityRank`/`scoredBy` → İstatistikler kartı görünür |
| Manga: `abbreviatedTitles` | Okunmuyor | **"Diğer Adlar"** satırı |
| Manga: `status` tba/unreleased | Eşleşme yok (ham değer basılıyordu) | "Bilinmiyor" / "Yayınlanmadı" |

Bilinçli olarak **alınmayanlar** (denetim kapsamında değerlendirildi):
- `streamingLinks` (Crunchyroll vb. resmî yayın bağlantıları): bölge kilitli, çoğu TR'den ölü; faz 2 adayı.
- `totalLength`: `episodeLength × episodeCount` ile gereksiz tekrar.
- `tba` alanı: niş, kafa karıştırıcı.

## 3. Yapılan Değişiklikler

1. **`KitsuClient.kt`**
   - `fetchAnimeDetail` / `fetchAnimeDetailByTitle`: `include=categories,animeProductions.producer,mappings`
     (tek istek — ek gidiş-dönüş yok).
   - Yeni `kitsuSubtypeToTurkish()` ortak çevirisi (arama kartı + detay aynı etiketi kullanır).
   - Yeni `parseKitsuMappings()`: mappings → (gerçek MAL ID, MAL/AniList/AniDB bağlantıları).
     Bağlantı URL'leri doğrulanmış sağlayıcılarla sınırlı; `anime-planet` gibi slug tabanlı/ölü
     bağlantı üretebilecekler bilinçli atlanır.
   - Yeni `parseKitsuProductions()`: stüdyo/yapımcı/lisanslayan çipleri. Kitsu kurum ID'leri MAL
     uzayıyla aynı DEĞİLDİR → çipler `source="kitsu"` taşır; tıklamada StudioSourceSupport'un
     ad-temelli Jikan çözümü + `namesMatch` koruması devreye girer (yanlış stüdyo sayfası açılamaz).
   - `parseKitsuAnime`: yukarıdaki tüm alanlar + `scoredBy` (ratingFrequencies toplamı) doldurulur.
2. **`KitsuExploreClient.kt`**
   - Manga detayı `include=categories,mappings`; `parseMangaDetail` cilt/dergi/puan istatistikleri/
     diğer adlar/realMalId/bağlantılar/format ile zenginleştirildi; poster large-first.
   - Arama eşleyicisindeki kopya subtype çevirisi ortak fonksiyona bağlandı.
3. **`KitsugiDetailClient.kt`** — Kitsu önbellek anahtarı `_ks1` → `_ks2`: eski (alanları eksik)
   önbellek satırları 24 saat beklemeden anında yenilenir.
4. **UI (`ApiInfoSection` + `EntryInfoSection`)** — Bilgiler kartına **Format**, **Bölüm Sayısı**
   (mangada "Bölüm") ve **Cilt** satırları eklendi; alan boşsa satır hiç basılmaz (diğer kaynakların
   sayfaları da bu satırlardan kazanır).
5. **strings.xml (TR) + values-en/strings.xml (EN)** — `detail_info_format`, `detail_info_episodes`,
   `detail_info_chapters`, `detail_info_volumes`.

## 4. Kullanıcıya Etkisi (Kitsu sayfası, ör. Chainsaw Man: Reze Arc)

- **Bilgiler:** Durum, Sezon, Başlangıç, Bitiş, **Format (Film)**, Stüdyo (Kitsu'nun kendi verisi),
  Yapımcılar, Süre, Yaş Sınırı, İngilizce, Diğer Adlar.
- **İstatistikler:** Puan Sırası + **gerçek Oy Sayısı** + Takip Edenler + Popülerlik Sırası.
- **Harici bağlantılar:** Kitsu + **MyAnimeList + AniList + AniDB**.
- **Ekip/İlişkiler/Grafikler sekmeleri:** realMalId artık Kitsu'dan geldiği için Jikan tabanlı
  sekmeler ve zenginleştirme daha güvenilir çalışır.
- **Manga/Manhwa sayfaları:** Format, Bölüm, Cilt, Dergi çipi, tam istatistik kartı, Diğer Adlar.

## 5. Risk ve Güvenceler

- JSON:API `include` genişlemesi tek istekte kaldığı için ağ maliyeti ihmal edilebilir (yanıt büyür,
  istek sayısı artmaz).
- Kitsu kurum çipleri için kimlik uzayı çakışması `namesMatch` + ad-aramasıyla engellendi.
- Önbellek sürümü (`_ks2`) yükseltildi → karışık eski/yeni veri dönemi yaşanmaz.
- Dergi çipi Kitsu'da ID taşımaz → `magazineId=0` ile ad-temelli dergi aramasına düşer
  (SearchViewModel `takeIf { it > 0 }` koruması bunu zaten destekler).
- `realMalId` yalnızca mappings'teki pozitif sayısal değerle yazılır; tüm tüketiciler
  `takeIf { it > 0 }` / `1..99_999_999` aralık korumasıyla çalışır.

## 6. Doğrulama

- 5 Kotlin dosyası dize/yorum duyarlı parantez denetiminden geçti; 2 strings.xml XML olarak doğrulandı.
- Kotlin/Gradle derlemesi bu ortamda SDK olmadığı için çalıştırılamadı; satır satır tip denetimi
  yapıldı (KitsugiMediaDetail alan adları, Triple/Pair destructuring, org.json API'leri).
- Mantık simülasyonu: Reze (anime filmi) satır seti, manhwa satır seti, ratingFrequencies toplamı,
  mappings → MAL/AniList/AniDB bağlantı zinciri beklendiği gibi üretildi.
