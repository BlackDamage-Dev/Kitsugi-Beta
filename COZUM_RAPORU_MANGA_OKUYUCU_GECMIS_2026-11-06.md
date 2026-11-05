# Çözüm Raporu — Manga Bölüm Detay · Okuyucu · Geçmiş Sayfası (2026-11-06)

Sürüm dalı: `arena/f6a2450e-kitsugi-beta` · İlk teslim: `d4c65c4` · İnceleme turu: `5ce06df`

---

## 1) Manga bölüm detay sayfası

### 1.1 Kaydırmaya duyarlı üst bar (2. ve 3. görsel mantığı)
Anime/film detay sayfasındaki mekanik ile **aynı** davranış:

| Durum | Görünen |
|---|---|
| Sayfa tepede | Yalnızca yüzen geri düğmesi (hero üstünde) |
| Hero + aksiyon + açıklama + wiki kartı geçilince | Üst bar yumuşakça aşağı süzülür: geri oku, **seri adı**, yazar · kaynak · bölüm sayısı alt satırı, yenile düğmesi |
| Geri yukarı kaydırınca | Üst bar sessizce kaybolur |

- Ortak bileşen: `ui/components/KitsugiCollapsibleTopBar.kt`
  (eklenti izleme sayfasındaki `CollapsibleTopBarBox` buradan **çıkarıldı**, iki taraf da aynı kodu kullanıyor → davranış birebir aynı).
- Bar görünürken sticky "Bölümler" başlığı barın altına iner (üst üste binme yok).

### 1.2 Bölüm listesi araç çubuğu (1. görseldeki yapı + eksikleri)
- **Sticky başlık:** `Bölümler (232)` + sıralama etiketi + 3 ikon: `⇅` sıralama · `✓` okunanları filtrele · `↻` yenile.
- **Bölüm arama:** yuvarlak alan, "Bölüm ara (ör. 42)"; bölüm adı, numara ve tarayıcı adında arar.
- **Okunmamış filtresi:** arama alanının sağında tek dokunuşla aç/kapa.
- **Kaynak/tarayıcı çipleri:** `Tümü | MangaciX | Şımarık Fansub | …` — etiketler eklentinin `scanlator` alanından **olduğu gibi** türetilir (veri uydurulmadı).
- **Sıralama:** Yeni → Eski (varsayılan), Eski → Yeni, Son güncellenen.
- **Filtre boş sonuç verirse:** "Bu filtrelere uyan bölüm yok" + **Filtreleri temizle** (sessiz boş liste yok).
- **Bölüm satırı:** yarım kalan bölümde ince ilerleme çubuğu + `Sayfa 12/40`; sağdaki tik'e dokunmak bölümü okundu/okunmadı işaretler (Room + okuma geçmişi senkron).
- **Yukarı çık FAB'ı:** Keşfet sayfasındaki `KitsugiScrollToTopFab` **birebir** kullanıldı.

### 1.3 Diğer
- Hero yüksekliği gerçek ölçülere göre sıkıştırıldı (yazar satırı üst bara taşındı) → üst bar eşikleri (index 4/2) güvenilir, titreme yok.
- Okuyucudan dönüşte bölüm listesi **yeniden çekilmez**; yalnızca Room'daki ilerleme tazelenir (`refreshTick`), ağ trafiği yok, kaydırma konumu bozulmaz.

---

## 2) Manga okuyucu (4. ve 5. görsel mantığı)

- **Sayfa galerisi (alt barda 📄 düğmesi + üst barda ▦ + sayfaya uzun basış):** Adaptif ızgara, her karede sayfa numarası, seçili sayfa vurgusu; `100+` sayfalık bölümde slider sürüklemek yerine görerek atlama. Galeri açıldığında **o an okunan sayfaya** kaydırılır; hatalı sayfaya dokunmak onu yeniden dener.
  > İnceleme turunda, alt barda 60dp'lik küçük resim şeridi (ve yerine konan 26dp'lik şerit) kaldırıldı: slider aynı işi zaten yapıyordu, iki şerit üst üste binince okuma alanı daralıyordu. Yerine tek dokunuşluk **galeri** düğmesi geldi — küçük resimler zaten galeride, tam boyutta.
- **Hızlı yukarı kaydırma:** Webtoon modunda aşağı kaydırınca Keşfet ile aynı yuvarlak düğme belirir → en üstteki sayfaya yumuşak dönüş. Düğme, okuyucunun kalın alt barının üstünde duracak şekilde konumlanır (yeni `bottomBarHeight` parametresi, varsayılan davranış değişmedi).
- Üst bardan da "galeri" kısayolu eklendi.
- Galeri/şerit küçük resimleri okuyucunun disk önbelleğini kullanır → **ek indirme yok**.

---

## 3) Geçmiş sayfası: izlenen + okunan ayrımı

### 3.1 Yeni veri katmanı
| Bileşen | Açıklama |
|---|---|
| `manga_read_history` tablosu | `id (mangaUrl\|chapterUrl)`, kaynak adı, seri adı/kapa, bölüm adı, son sayfa, toplam sayfa, tamamlandı, `updatedAt` |
| Room **v30** + `MIGRATION_29_30` | Geriye dönük uyumlu göç; mevcut okuma ilerlemesi korunur |
| `MangaReadHistoryManager` | Ateşle-ve-unut yazma (IO + mutex), hata yutulur, 300 kayıt sınırı |
| `MangaReadHistoryDao` | getAll / remove / removeForManga / clearAll / trimTo |

Yazma noktaları: okuyucu `onPageChanged` (500 ms debounce) ve detay ekranındaki "okundu işaretle".

### 3.2 Ekran
`ui/screens/history/WatchHistoryScreen.kt` yeniden düzenlendi, mantık `HistoryViewModel`'e taşındı:

- **Sekmeler:** `Tümü · İzlenen · Okunan` (sayaçlı).
- **İzlenen:** mevcut izleme geçmişi — kart tasarımı ve dokunma davranışı (direkt oynatma / kaynak taramasına dönüş, "Anında" rozeti, kaldığı yer çubuğu) **birebir korundu**.
- **Okunan:** aynı görsel dil — kapak, seri adı, `Bölüm: 42. Bölüm`, kaynak rozeti (ör. MangaciX), `Tamamlandı / Devam ediyor`, `12/40 sayfa` + ilerleme çubuğu, tarih.
  - Dokununca **manga detay sayfası** açılır → oradan "Devam Et" ile kaldığı sayfaya dönülür.
  - Kaynak eklentisi yüklü değilse bilgilendirme gösterilir.
- **Temizleme:** seçili sekmeye göre (İzlenen / Okunan / Tümü) onaylı silme; tek kayıt silme kartın sağından.
- **Yukarı çık FAB'ı** eklendi (Keşfet bileşeni).

---

## 4) Değişen / eklenen dosyalar

**Yeni**
- `data/local/MangaReadHistoryEntity.kt`
- `data/local/MangaReadHistoryManager.kt`
- `data/manga/ChapterSorting.kt`
- `ui/components/KitsugiCollapsibleTopBar.kt`
- `ui/screens/history/HistoryViewModel.kt`
- `ui/screens/manga/MangaChapterFilter.kt`
- `ui/screens/manga/components/MangaChapterTools.kt`
- `ui/screens/manga/components/MangaDetailTopBar.kt`

**Güncellenen**
- `ui/screens/manga/MangaDetailScreen.kt`, `MangaDetailViewModel.kt`
- `ui/screens/manga/MangaReaderScreen.kt`, `MangaReaderViewModel.kt`
- `ui/screens/history/WatchHistoryScreen.kt`
- `ui/screens/stream/StreamScreenContent.kt` (ortak üst bar bileşenine geçiş)
- `ui/components/KitsugiScrollToTopFab.kt` (`bottomBarHeight` parametresi)
- `data/local/KitsugiDatabase.kt` (v30 + göç)
- `AppRoot.kt`, `AppRootDetailPages.kt`, `AppRootMangaPages.kt`, `ui/app/AppNavigationState.kt`
- `KitsugiApplication.kt` (okuma geçmişi arka planda başlatılır)
- `res/values/strings.xml`, `res/values-en/strings.xml` (42 yeni anahtar, TR/EN parite tam)

---

## 5) Doğrulama notları

- Kotlin derlemesi bu ortamda yapılamadı (Android SDK/Gradle önbelleği yok); bu yüzden:
  - Tüm yeni/değişen dosyalarda parantez/blok dengesi kontrol edildi.
  - `strings.xml` XML olarak parse edildi, **mükerrer anahtar yok**, TR/EN anahtar setleri birebir eşit.
  - Kullanılan tüm `R.string.*` anahtarlarının kaynakta var olduğu doğrulandı.
  - Kullanılmayan importlar temizlendi; yeni kullanılan API'lerin (Compose M3 `stickyHeader`, `LazyVerticalGrid`, theme gradient `Text/Icon/LinearProgressIndicator`) imzaları repodaki mevcut kullanımlarla karşılaştırıldı.
- Room şeması: `version = 30` + `MIGRATION_29_30`; debug'da mevcut `fallbackToDestructiveMigration` davranışı korunuyor, release'te göç zorunlu.

---

## 6) İnceleme turu — "yer kaplayan bloklar, eksikler" (2026-11-06, 2. tur)

Kullanıcının "boşuna yer kaplayan büyük blok var mı, her şey derli toplu mu, eksik bir şey var mı"
sorusu üzerine tüm üç ekran baştan tarandı. Yapılanlar:

### Okuyucu (`MangaReaderScreen.kt`)
| Değişiklik | Neden |
|---|---|
| Alt bardaki küçük resim şeridi **tamamen kaldırıldı** (60dp → 26dp → hiç) | Slider zaten tüm sayfalara atlıyor; iki şerit üst üste binince sayfa alanı daralıyordu. Alt bar 3 satırdan 2 satıra indi (−32dp). |
| Alt bara **galeri (📄)** düğmesi eklendi, ayarlar düğmesi yanına | Şerit kalktıktan sonra galeri tek dokunuş uzakta kalsın. |
| Bölüm ayarları sheet'i %90 → **%62** yükseklik + kaydırılabilir içerik | 4 satırlık ayar için ekranın tamamı karartılıyordu; küçük ekranda parlaklık sürgüsü de kesiliyordu. |
| **Bölüm seçici**: üst barda bölüm adı + ⌄ → açılır liste | Okuyucudan çıkmadan bölüm değiştirme (geçerli bölüm accent ile işaretli, otomatik ona kaydırılır). |
| **Bölüm sonu geçiş kartı**: menü kapalıyken son sayfada "← Önceki / Sonraki bölüm →" düğmeleri | 100+ sayfalık bölümü bitirip alt barı aramak gereksizdi. |
| **Uzun basış → galeri** (`MangaPageItem` + pager/webtoon okuyuculara `onLongPressPage`) | "Görsellere tıklayınca galeri" isteğinin ekranın ortasına dokunma davranışını bozmayan karşılığı (ortaya dokunma menüyü açıyor). |
| **Ekranı açık tut** (`keepScreenOn`) + `ON_PAUSE` / `onDispose`'ta **ilerleme flush** | Uzun bölümde ekran kapanıyordu; 500 ms debounce yüzünden son sayfa kaydedilmeden çıkılabiliyordu. |
| Galeri küçük resmi önbelleği `remember(...)` ile sabitlendi | `getImageFile` her karede `lastModified`'a dokunuyordu (LRU şişmesi). |
| Galeri tam ekran `Dialog` + `decorFitsSystemWindows = false` + durum/gezinme çubuğu dolgusu | Kenar boşlukları ve kesilen alt sıra. |
| Kullanılmayan `ReadingMode.label()`, boşta kalan importlar temizlendi | Derleme uyarısı/kirlilik. |

### Bölüm detay (`MangaDetailScreen.kt`)
| Değişiklik | Neden |
|---|---|
| Hero yüksekliği ekrana göre (200 / 260 / 292dp) + kapak oranı `1.42×` | Sabit 300dp küçük telefonlarda bölüm listesinin yarısını yiyordu. |
| Açıklama **4 satırla sınırlı** + "Devamını göster / Daha az" (yalnızca gerçekten taşıyorsa, `onTextLayout.hasVisualOverflow` ile ölçülür) | Bazı kaynaklar 2000+ karakterlik özet veriyor; bölüm listesi ekranın çok altına itiliyordu. |
| Sticky başlığa **🔍 hızlı arama** (açılır kapanır alan) | 232 bölümlük listede aşağıdayken aramaya erişim yoktu. |
| Sticky başlığa **✓✓ "görünenleri okundu işaretle"** (`MangaDetailViewModel.markAllRead`) — hepsi okunduysa geri alır | 200+ bölümü tek tek tiklemek gerçekçi değil; filtre + toplu işaretleme birlikte çalışır ve okuma geçmişine de yazılır. |
| Sıralama değişiminde kaydırma artık görünür öğe anahtarından hesaplanıyor | Sabit indeks (4/5) koşullu öğeler yüzünden yanlış konuma gidebiliyordu. |

### Manga arama (`MangaBrowseScreen.kt`)
| Değişiklik | Neden |
|---|---|
| Üstteki 76dp'lik "bilgi kartı" → **tek satır kompakt şerit** (40×58 kapak + başlık + "N sonuç · M kaynak") | Arama alanını ekranın altına itiyordu; kullanıcının istediği "sayı + kaynak" satırı korundu. |
| Arama alanına **temizle (✕)** düğmesi | Metni silmek için klavyeyi açmak gerekiyordu. |

### Geçmiş (`WatchHistoryScreen.kt`)
- "İzlenen · Okunan" grupları, kaynak rozeti, ilerleme çubuğu, tek kayıt silme ve sekme bazlı temizleme korundu;
  okunan karta dokunma davranışı yorumla netleştirildi (detay → "Devam Et").

### TV paralelliği
- `TvMangaReaderScreen`: `keepScreenOn` + `flushProgress` (arka plana geçişte kayıt) ve alt HUD'da ince ilerleme çubuğu.
- `TvMangaDetailScreen`: bölüm **sıralama** ve **okunmamış filtresi** düğmeleri, filtrelenmiş liste, yarım kalan bölümlerde ilerleme çubuğu, boş sonuç durumu.

### Doğrulama (bu tur)
- `parse` edilebilir blok dengesi kontrolü tüm değişen Kotlin dosyalarında temiz.
- `R.string.*` anahtarlarının tamamı kaynakta mevcut; TR/EN anahtar setleri eşit (17.739 anahtar), mükerrer yok.
- Room şeması `app/schemas/.../30.json` üretildi (v29 + `manga_read_history` tablosu, indeksler ve CREATE SQL birebir uyumlu).
- **Not:** Bu ortamda Android SDK/Gradle yok; APK derlemesi yine yapılamadı. Derleme doğrulaması için
  `./gradlew :app:assembleDebug` çalıştırılmalı.
