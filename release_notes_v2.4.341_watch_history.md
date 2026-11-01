# Kitsugi Beta v2.4.341

## 🇹🇷 Türkçe

### 🕘 İzleme Geçmişi Yeniden Tasarlandı — "Daha Derli Toplu" Paketi
- **Arama:** Başlık, eklenti (kaynak) ve akış etiketinde arama. Türkçe karakter duyarsız eşleşme: `kisi` yazınca **Kişi** de bulunur.
- **Sıralama menüsü (6 mod):** Son izlenenler · En eski · **Devam edenler önce** · İlerleme (çok → az) · Başlık A-Z · Kaynak A-Z. Seçim kalıcıdır.
- **Filtre çipleri:** Tümü / Devam edenler / İzlenenler / Başlanmamış + Diziler / Filmler + kaynak (eklenti) filtresi. Sonuç boşsa "Filtreleri temizle" kısayolu çıkar.
- **Diziye göre gruplama:** Aynı yapımın bölümleri tek kartta toplanır; kart açıldığında bölüm bölüm ilerleme görünür, tek dokunuşla o bölüme atlanır. Hızlı "Devam et" düğmesi kaldığın yeri açar.
- **Tarih bölümleri:** Bugün · Dün · Bu hafta · Bu ay · Daha eski başlıkları listeyi böler.
- **Özet şerit:** Kayıt sayısı, devam edenler, başlık sayısı ve yaklaşık toplam izleme süresi tek bakışta.
- **Toplu seçim:** Üst çubuktan "Seç"; kartlara dokunarak çoklu seçim, tümünü seç, seçilenleri sil.
- **Geri al (Undo):** Bölüm, dizi veya tüm geçmiş silindiğinde 6 saniyelik "Geri al" şeridi çıkar; silme tek dokunuşla geri alınır. Yanlışlıkla silme riski ortadan kalkar.
- **Uzun basma işlem menüsü:** Devam et / Baştan başlat / Seç / Bu bölümü kaldır / Başlığı kaldır.
- **Otomatik davranış:** Tamamlanmış bir bölüm açılınca baştan başlar; yarıda kalan bölüm kaldığı yerden devam eder. Kart üzerindeki "Anında" rozeti, kayıtlı akış adresinin hâlâ taze olduğunu (doğrudan oynatma) gösterir.
- **Zaman etiketleri insan dilinde:** "Az önce", "12 dk önce", "Bugün 21:40", "Dün 23:48", "29 Eki 2026 · 21:40".
- **Kart düzeni sadeleşti:** Tek satır kaynak etiketi (tekrarsız) + tek satır rozetler; tek dokunuşla silme kaldırıldı, silme artık işlem menüsünde.

### 🧼 Bozuk Kaynak Metinleri Temizlendi
- Eklentilerden gelen `HDFilmCehennemi · Close · .fa-secondary{opacity:.4} DUAL` gibi metinlerdeki **CSS/HTML artıkları** artık gösterilmiyor.
- Yeni `StreamLabelSanitizer`: CSS kuralları (`{…}`), `<style>/<script>` gövdeleri, HTML etiketleri, HTML varlıkları ve sıfır genişlikli karakterler temizlenir; tekrar eden parçalar (`DUAL` iki kez) ve mükerrer ayırıcılar sadeleşir.
- Temizlik **hem gösterimde hem kayıt anında** uygulanır: kaynak seçici kartı ve izleme geçmişi aynı temiz metni görür. `Spider-Man.No.Way.Home.1080p` gibi gerçek başlıklar korunur.
- Ses türü (Dual / Dublaj / Altyazılı) metinden çıkarılıp ayrı bir rozet olarak gösterilir.

### 🔧 Teknik Notlar
- `WatchHistoryManager`: `removeMany`, `removeSeries` ve `restore` eklendi — toplu silme tek disk yazmasıyla yapılır, geri alma izleme zamanına göre yeniden sıralar.
- `WatchHistoryQuery` (yeni): sıralama, filtreleme, gruplama, tarih bölümleri ve istatistikler Compose'dan bağımsız saf fonksiyonlar hâline getirildi.
- `WatchHistoryPreferences` (yeni): sıralama/filtre/gruplama tercihleri küçük bir SharedPreferences dosyasında saklanır (DataStore şeması büyütülmedi).
- Liste anahtarları artık `animeId + episode` (önce `watchedAtMs` de vardı) — her ilerleme güncellemesinde kartın yeniden kurulması engellendi.

---

## 🇬🇧 English

### 🕘 Watch History Redesigned — "Tidier by Default" Pack
- **Search** across titles, addons (sources) and stream labels, with Turkish-aware matching (`kisi` finds **Kişi**).
- **Six sort modes:** recently watched · oldest · **continue first** · progress (high to low) · title A-Z · source A-Z. The choice is remembered.
- **Filter chips:** all / in progress / watched / not started, plus series / movies and a source (addon) filter, with a one-tap "Clear filters" shortcut when nothing matches.
- **Group by series:** episodes of the same title collapse into one card; expand to see per-episode progress and jump straight to an episode, or use the quick "Continue" button.
- **Date sections:** Today · Yesterday · This week · This month · Earlier.
- **Summary strip:** entries, in-progress count, titles and an approximate total watch time.
- **Multi-select:** start from the toolbar, tap to select, select all, delete selected.
- **Undo:** deleting an episode, a title or the whole history shows a 6-second "Undo" bar — accidental deletions are recoverable.
- **Long-press actions:** continue / start over / select / remove this episode / remove this title.
- **Smarter playback:** completed episodes restart from the beginning, partially watched ones resume. The "Instant" badge shows when the stored stream URL is still fresh enough to play directly.
- **Human-friendly timestamps:** "Just now", "12 min ago", "Today 21:40", "Yesterday 23:48", "29 Oct 2026 · 21:40".
- **Simpler cards:** one deduplicated source line and one badge row; one-tap deletion was removed (deletion lives in the action menu).

### 🧼 Broken Source Labels Cleaned Up
- CSS/HTML debris such as `HDFilmCehennemi · Close · .fa-secondary{opacity:.4} DUAL` is no longer displayed.
- New `StreamLabelSanitizer` strips CSS rules, `<style>`/`<script>` bodies, HTML tags, entities and zero-width characters, and deduplicates repeated segments; real titles such as `Spider-Man.No.Way.Home.1080p` are preserved.
- Sanitising runs both when displaying and when recording, so the stream picker and watch history show identical clean text.
- Audio type (Dual / Dub / Sub) is extracted from the label and shown as its own badge.

### 🔧 Technical Notes
- `WatchHistoryManager`: added `removeMany`, `removeSeries` and `restore` — batch deletion performs a single disk write, and undo restores by watch time.
- `WatchHistoryQuery` (new): sorting, filtering, grouping, date sections and stats are Compose-free pure functions.
- `WatchHistoryPreferences` (new): sort/filter/grouping preferences live in a small SharedPreferences file (no DataStore schema growth).
- List keys are now `animeId + episode` (previously included `watchedAtMs`), preventing card rebuilds on every progress tick.
