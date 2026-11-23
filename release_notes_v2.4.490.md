# Kitsugi Beta v2.4.490 — "Tümünü Gör" Sayfalaması, Toplu Ekle Butonunun Kaldırılması ve Rozet Uyumu

[TR]
### 🐞 Düzeltmeler ve İyileştirmeler (v2.4.490)

- **TÜMÜNÜ GÖR — TMDB artık sonsuz kaydırıyor (kök neden: hata = liste sonu):**
  - `TmdbDiscoverClient` boş/başarısız yanıtları **60 dakika** boyunca "başarılı" gibi önbellekliyordu; tek bir 429 hız sınırı, o sorguyu 1 saat boyunca boş bırakıyordu. Boş sonuçlar artık yalnızca **60 saniye** tutuluyor.
  - `FullScreenMediaGridPage` boş sayfayı doğrudan "liste bitti" sayıp `hasMorePages = false` yazıyordu. Yeni kural: **boş sayfa ile liste sonu ayrı şeylerdir.** Son sayfa tam dolu geldiyse boş yanıt geçici hata olarak işaretlenir, sayfalama durumu korunur ve kullanıcıya "Tekrar dene" gösterilir. Kısmi sayfadan sonra gelen boşluk gerçek sondur.
  - Ağ/429 hatasında **600 ms ve 1200 ms** arayla en fazla **2 sessiz tekrar deneme** yapılır; başarılı sayfada bütçe tazelenir.
  - Hata varken otomatik kaydırma yüklemesi durur (kaynağı döven istek fırtınası yok).
  - TMDB birleşik uçları iki 20'lik API sayfasını birleştirip `take(20)` yapıyordu; kuyruktaki öğeler kayboluyordu. Artık birleşik sayfa tam ve kayıpsız dönüyor. `page=500` sonrası istek atılmıyor (TMDB bu sınırın ötesinde 400 döner).
  - Filtreli/sıralı sorgularda `runCatching` ile yutulan hatalar "Tümünü Gör" için yukarı taşınır (`orThrow`), böylece hata ile "bu filtrede sonuç yok" ayrışır. Keşfet ekranı eski davranışı korur.
  - Eklenti (CS3) "Tümünü Gör" ızgarasında da yanıt alınamadığında liste kalıcı olarak kapanmıyor; hata gösterilip tekrar denenebiliyor.

- **TÜMÜNÜ GÖR — telefonda da sayfalanıyor:**
  - Telefon, raf ne olursa olsun yalnızca hazır öğeleri gösteren **sonlu** ızgara açıyordu; bu yüzden aynı sayfa tablet/TV'de sonsuz kaydırırken telefonda ilk 20 öğede duruyordu.
  - Temiz katalog raflarında (MAL, TMDB, AniList, Kitsu, Shikimori, Simkl, Bangumi) artık gerçek sayfalanan ızgara açılır. Karışık/kişisel raflar (Simkl listeleri, "Tümü" birleşimleri, novel rafları) sayfalanan sorguyla aynı içeriği getiremeyeceği için sonlu ızgarada kalır.
  - Hazır sonuçlarla açılan sayfada sayfa 1 yeniden çekilmez; devam edebilmek için sonraki sayfa önden hazırlanır (`PRIME_NEXT_PAGE_THRESHOLD`).

- **"Tümünü listeye ekle" düğmesi kaldırıldı:**
  - Izgara başlığı, liste başlığı ve yüzen başlıktaki üç `PlaylistAdd` düğmesi ile `onAddAllToList` parametresi ve `AppRoot` bağlantısı silindi. Poster köşesindeki tek tek hızlı ekleme korunur.

- **Rozetler iki görünümde birebir aynı (liste görünümü referans):**
  - Yeni **tek kaynak** dosya `KitsugiMediaCardStats.kt`: `KitsugiScoreBadge`, `KitsugiMediaCardStatBlock`, `KitsugiStatProgressBar`, `KitsugiMediaStatSpec`.
  - Kare görünümde puan posterin üzerinde **siyah zeminli ayrı bir kutu** ("★ 8/10") olarak çiziliyordu; liste görünümünde ise kartın içinde **kutusuz yıldız + sayı** ("★ 8"). Artık iki görünüm de liste görünümünün rozetini kullanır: 16 dp sarı yıldız (`0xFFFFB800`) + kalın sayı, dolgu/kontur yok.
  - Kare görünümün metin alanı sabit yükseklikte olduğu için aynı blok `compact` modda tek satıra sığdırılır (`0/12 bölüm` + `★ 8.00` + 4 dp çubuk); bileşenler, renkler ve tipografi liste görünümüyle aynıdır. TV ve yatay varyantlar da aynı bloğu kullanır.
  - Poster üzerindeki siyah puan şeridi tamamen kaldırıldı.
  - Liste kartına eksik olan `scoreFormat` parametresi eklendi; puan biçimi iki görünümde aynı (`8/10`, `8.00`).
  - Sıra numarası rozeti (`#n`) kare görünümde mevsimsel gridde gizleniyordu; artık iki görünümde de aynı kaynaktan (`index + 1`) gelir.

- **Başlık düzeni:**
  - Düz "Geri" metin düğmesi yerine yuvarlak `KitsugiCircularBackButton` (38 dp) — başlıkla aynı satırda.
  - Başlığın altında kaynak kimliği satırı: 16 dp platform logosu + kısa ad (`Trend Animeler` → `MAL`).

---

[EN]
### 🐞 Fixes & Improvements (v2.4.490)

- **SEE-ALL — TMDB now paginates indefinitely (root cause: error treated as end-of-list):**
  - `TmdbDiscoverClient` cached empty/failed responses as successes for **60 minutes**; a single 429 blanked that query for an hour. Empty results now live for only **60 seconds**.
  - `FullScreenMediaGridPage` treated an empty page as "end of list" and set `hasMorePages = false`. New rule: **an empty page is not proof of the end.** If the previous page was full, an empty response is flagged as a transient error, paging state is preserved, and the user gets "Retry". An empty response after a partial page is a genuine end.
  - Up to **2 silent retries** (600 ms / 1200 ms) on transient failures; the budget resets on a successful page.
  - Auto-load-on-scroll pauses while an error is pending, preventing request storms.
  - TMDB merged endpoints fused two 20-item API pages and then `take(20)`, dropping the tail. Merged pages are now complete and lossless. Requests stop past `page=500` (TMDB returns 400 beyond that).
  - Filtered/sorted queries no longer swallow errors for see-all (`orThrow`), so "error" and "no results in this filter" are distinct. The explore screen keeps its forgiving behaviour.
  - Addon (CS3) see-all grids no longer terminate permanently when a response is missing; the error is shown and the page can be retried.

- **SEE-ALL — pagination on phones:**
  - The phone always opened a **finite** grid with only already-loaded items, so the same page scrolled forever on tablet/TV but stopped after 20 items on phones.
  - Clean catalog shelves (MAL, TMDB, AniList, Kitsu, Shikimori, Simkl, Bangumi) now open the genuinely paginated grid. Mixed/personal shelves (Simkl lists, ALL merges, novel shelves) stay finite because a paginated query cannot reproduce their contents.
  - Seeded pages do not re-fetch page 1; the next page is primed so scrolling continues immediately (`PRIME_NEXT_PAGE_THRESHOLD`).

- **"Add all to list" button removed:** all three `PlaylistAdd` buttons (grid header, list header, floating header), the `onAddAllToList` parameter, and the `AppRoot` wiring are gone. The per-card quick-add corner badge remains.

- **Badges are now identical across views (list view is the reference):**
  - New single source `KitsugiMediaCardStats.kt` with `KitsugiScoreBadge`, `KitsugiMediaCardStatBlock`, `KitsugiStatProgressBar`, `KitsugiMediaStatSpec`.
  - The grid card drew the score on the poster inside a **black scrim box** ("★ 8/10") while the list card used an **unboxed star + number** in the card body ("★ 8"). Both views now use the list view's badge: a 16 dp yellow star (`0xFFFFB800`) plus bold number, with no fill or outline.
  - Grid text areas are fixed-height, so the same block is rendered in `compact` mode on one line (`0/12 bölüm` + `★ 8.00` + 4 dp bar) with identical colours and typography. TV and landscape variants use the same block.
  - The poster score strip was removed entirely.
  - The list card gained the missing `scoreFormat` parameter so both views format scores identically (`8/10`, `8.00`).
  - The rank badge (`#n`) is no longer hidden in seasonal grids, so both views use the same `index + 1` source.

- **Header layout:** circular `KitsugiCircularBackButton` (38 dp) inline with the title, plus a source-identity row beneath it (16 dp platform logo + short name).
