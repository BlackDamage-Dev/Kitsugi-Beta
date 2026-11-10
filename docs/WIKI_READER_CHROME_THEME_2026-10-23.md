# Wiki açılır sayfası — tema uyumu ve şerit sadeleştirmesi

Tarih: 2026-10-23

İlgili önceki çalışmalar:

- [Özgün düzen mimarisi](WIKI_ORIGINAL_LAYOUT_2026-10-21.md)
- [DOM çevirisi ve reklam engelleme](WIKI_DOM_TRANSLATION_ADBLOCK_2026-10-21.md)
- [Okuma teması ve kullanılabilirlik](WIKI_READING_USABILITY_2026-10-21.md)

## 1. Kullanıcı bildirimi

Ekran görüntüsünde wiki penceresinin üst bölgesi hâlâ şu satırlarla kaplıydı:

- `Özgün sayfa` · `Sade okuyucu` · `Mobil düzen` · `Yenile` (mod çipleri)
- `kiminonawa.fandom.com · Özgün tema; çeviri ve reklam engeli sayfa araçlarından yönetilir.`
- `Orijinal göster` + `Reklam engeli: açık (13)` (iki tam genişlik çip) + `⋮`

Bu yerleşim 2026-10-21 tarihli ikinci turda kaynak koddan **kaldırılmıştı**; görüntüdeki
kurulum daha eski bir APK'ya ait. Yine de kullanıcının asıl isteği — “wiki penceresi
uygulama temasıyla uyumlu ve derli toplu olsun” — bu çalışmada somut olarak ele alındı:

- Görünür denetim sayısı aynı kaldı (yalnızca sekme şeridi + `⋮`), ek bir satır eklenmedi.
- Renkler, dolgular ve vurgular uygulama paletine bağlandı.
- Sayfa içeriğinin kendisi de varsayılan olarak uygulama temasını izliyor.

## 2. Yapılan değişiklikler

### 2.1 Varsayılan okuma teması uygulamayı izler

| Önce | Sonra |
| --- | --- |
| `wikiPageTheme = "SITE"` | `wikiPageTheme = "APP"` |

- Yeni kurulumda wiki sayfası uygulamanın açık/koyu paletiyle aynı görünür; koyu temada
  açık bir Fandom sayfası ekranı yakmaz, sayfa ile şerit arasında renk kırılması olmaz.
- `SITE` seçeneği durur: özgün site görünümünü isteyen kullanıcı tek dokunuşla döner.
- Tercih zaten kayıtlıysa kullanıcının seçimi **ezilmez**; yalnızca hiç seçim yapmamış
  kullanıcılar yeni varsayılanı alır (`SettingsDataStore` aynı anahtarı okur).
- Tema sınıfı değişimi yine yalnızca Fandom/Wikipedia'nın bilinen sözleşmeleriyle yapılır;
  görsellere filtre uygulanmaz, içerik yeniden stillendirilmez.

### 2.2 Sekme şeridi

- Aktif sekme: vurgu renginin %16 saydam dolgusu + %40 vurgu çerçevesi.
- Pasif sekme: **tamamen saydam**. Sekme sayısı arttığında şerit “kutu ormanına” dönmez.
- Kapat (`×`) yalnızca aktif sekmede veya sekme yeterince genişken görünür.
- Şerit dolgusu 4/6 dp; alt çizgi tek hairline olarak kalır.

### 2.3 `⋮` menüsü

- Menünün en üstünde tek satırlık bağlam başlığı: **kilit ikonu + alan adı** ve altında
  `Özgün sayfa · Masaüstü düzeni` gibi kısa mod bilgisi. Eski sürümde ekranın üçte birini
  kaplayan alan adı/açıklama satırının işlevini devralır.
- Yeni eylemler:
  - **Bağlantıyı paylaş** (`ACTION_SEND` + chooser)
  - **Bağlantıyı kopyala** (pano + kısa onay)
  - **Sekmeler (n)** — yalnızca birden fazla sekme varken görünür; sekme listesinde başlık,
    aktif sekme vurgusu, tek dokunuşla geçiş ve satır bazlı kapatma.
- Bağlantı adresi özgün görünümde WebView'in gerçek adresidir; sade okuyucuda sayfanın
  wiki adresi kullanılır. Adres yoksa eylemler devre dışıdır (boş paylaşım yok).

### 2.4 Sayfa araçları sayfası

Eski hâli tek sütun hâlinde yığılmış satırlardı. Artık uygulama genelindeki ayar kartlarıyla
aynı dilde **başlıklı gruplar**:

1. **Çeviri ve gizlilik** — çeviri, reklam engeli
2. **Görünüm** — görünüm/yazı boyutu penceresi (mevcut yüzdeyle), yazıyı büyüt, yazıyı küçült,
   okurken ekranı açık tut
3. **Sayfa** — sayfada bul, hakkında

- **Yazıyı büyüt/küçült** 10 puanlık adımlarla 80–180 aralığında çalışır ve doğrudan kaydeder;
  her seferinde diyalog açmak gerekmez.
- **Okurken ekranı açık tut** oturumludur: yalnızca o sekmenin WebView'ine uygulanır
  (`View.keepScreenOn`), ayarlara yazılmaz, sekme kapanınca biter.
- Durum/uyarı satırları grubun dışında, aynı vurgu noktasıyla listelenmeye devam eder.

### 2.5 Yükleme çizgisi ve arama çubuğu

- İlerleme çizgisi artık **her zaman 2 dp** yer kaplar; yükleme bitince WebView yukarı
  sıçramaz. Material bileşenin 4 dp yüksekliği taşacağı için çubuk elle çizilir.
- Sayfada bul alanı yuvarlatıldı; odak/işaretçi rengi uygulama vurgusuna bağlandı.

### 2.6 Ayarlardan okuma görünümü

- **Ayarlar → Tercihler → Fandom Wiki** bölümüne `Okuma görünümü` satırı eklendi; değer
  olarak etkin tema ve yüzde gösterilir (ör. `Uygulama temasını izle · %100`).
- Aynı pencere (`WikiReadingSettingsDialog`) hem okuyucudan hem ayarlardan açılır; iki yol
  da `SettingsDataStore.setWikiReadingAppearance` üzerinden aynı anahtarları yazar.
  Önceki sürümde bu ayara yalnızca okuyucunun ⋮ menüsünden ulaşılabiliyordu.
- Penceredeki seçim vurguları artık uygulama vurgu rengini kullanır.

## 3. Eklenen metinler

Türkçe ve İngilizce kaynaklara eklendi (`values`, `values-en`):

`wiki_share_link`, `wiki_copy_link`, `wiki_link_copied`, `wiki_tabs_title`,
`wiki_keep_screen_on`, `wiki_keep_screen_on_desc`, `wiki_text_larger`, `wiki_text_smaller`,
`wiki_tools_section_content`, `wiki_tools_section_view`, `wiki_tools_section_page`,
`wiki_reading_appearance_desc`, `wiki_reading_appearance_value`.

`wiki_theme_explanation` ve `wiki_original_hint` yeni varsayılanı anlatacak şekilde
güncellendi. İki dil dosyası anahtar sayısı ve adları bakımından eşittir.

## 4. Doğrulama durumu

- Kaynak düzeyinde değişiklikler `git diff --check` ile ve iki dil dosyasının anahtar
  eşitliği bir betikle denetlendi.
- Bu ortamda JDK/Android SDK bulunmadığı için **yerel Gradle derlemesi çalıştırılmadı**.
  `arena/**` dalına yapılan itme, depodaki `android-build.yml` iş akışını tetikler; APK
  derlemesi ve DEX doğrulaması orada yapılır. Sonuç bu belgeye ayrıca işlenmelidir.
- Cihazda görsel kabul testi yapılmadı. Sekme listesi, paylaşım sayfası ve keep-screen-on
  davranışı gerçek cihazda doğrulanmalıdır.

## 5. Not: eski APK görüntüsü

Ekran görüntüsündeki üst şerit bu depodaki güncel kaynakla üretilemez: ilgili composable'lar
(`ReaderBrowserMenu`, `ReaderTabStrip`, `WikiPageToolsDialog`) 2026-10-21 sürümünde
sadeleştirilmiştir. Görüntünün alındığı cihazda güncel APK'nın kurulu olduğundan emin
olunması gerekir.
