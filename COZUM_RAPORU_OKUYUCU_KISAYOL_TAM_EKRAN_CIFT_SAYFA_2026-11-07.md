# Çözüm Raporu — Okuyucu Kısayolları, Tam Ekran Çıkış Bug'ı, Webtoon Yakınlaştırma, Otomatik Kaydırma Hızı, Sayfa Sayacı ve Çift Sayfa Birleştirme

**Tarih:** 2026-11-07
**Kapsam:** Manga okuyucu (`MangaReaderScreen` + bileşenleri)

---

## 1) Klavye kısayol butonu ayarlar paneline taşındı, yerine "Yön değiştirme" kısayolu geri kondu

- Alt kontrol barındaki **klavye kısayol butonu kaldırıldı**; işlevi **Okuyucu Ayarları**
  panelindeki "Klavye Kısayolları" butonuna taşındı (panelde `Klavye Kısayolları` başlıklı
  kısayol listesi açılır: ←/→, A/D, W/S, Space, Page Up/Down, Home/End).
- Klavye kısayolları **çalışır durumda**: fiziksel klavye ile sayfa çevirme (okuma yönüne
  göre), dikey kaydırma, kontrol göster/gizle (Space), bölüm değiştirme (Page Up/Down),
  ilk/son sayfa (Home/End) desteklenir (`onPreviewKeyEvent` + `FocusRequester`).
- Klavye butonunun yerine **"Yön değiştir" (SwapHoriz)** kısayol butonu geri kondu:
  RTL↔LTR ve Webtoon↔Dikey arasında tek dokunuşla geçiş yapar.
- Ekran döndürme ve sığdırma aksiyonları bar dışına alındı; "Ekran Döndürme" artık ayarlar
  panelindeki bir satır/aksiyon olarak durur (referans arayüzdeki gibi).

**Alt bar yeni düzen:** `[Okuma yönü] [Tam ekran] [Otomatik kaydırma] [Yön değiştir] [Ayarlar]`

## 2) Tam ekran modu çıkınca sistem çubukları geri gelmiyor (bug) — DÜZELTİLDİ

- Okuyucuda "Tam ekran" (⛶) düğmesi Android sistem çubuklarını (üst/alt bar) gizler/gösterir.
- **Kök düzeltme:** `DisposableEffect(onDispose)` içinde okuyucu ekranından **her çıkışta**
  (geri tuşu, navigasyon, kapanış) sistem çubukları `WindowInsetsControllerCompat.show(systemBars())`
  ile mutlaka geri getirilir. Uygulama tam ekranda takılı kalamaz.

## 3) Webtoon yakınlaştırma çalışmıyordu (mod seçilince sürekli yakınlaşmış kalıyordu) — DÜZELTİLDİ

- "Webtoon yakınlaştırma" ayarı eklendi (Okuyucu Ayarları → Webtoon seçenekleri).
- **Kapalıyken:** görsel normal boyutta (tüm şeritler aynı genişliğe sığar, `FillWidth`)
  ve zoom jesti tamamen devre dışı — sabit, normal boyut.
- **Açıkken:** iki parmak / çift dokunuşla yakınlaştırma (Telephoto) çalışır.
- Mod veya ayar değişiminde zoom durumu `key(readingMode, webtoonZoomEnabled)` ile
  **sıfırlanır**; webtoona girişte görüntü "sabit yakınlaşmış" kalmaz.

## 4) Webtoon otomatik kaydırma hızı — 500 dp/sn'ye kadar

- Otomatik kaydırma (▶ düğmesi, webtoonda) için ayarlarda **"Otomatik kaydırma hızı"**
  sürgüsü: **20–500 dp/sn** (varsayılan 160). `SettingsDataStore` içinde `20..500`
  aralığına kırpılarak saklanır (`manga_auto_scroll_speed`).
- Kaydırma kare-bazlı (`withFrameNanos`) ve dp/sn cinsinden gerçek hızda çalışır;
  sayfa sonunda otomatik durur.

## 5) Kontroller gizliyken sayfa sayacı — köşeye taşındı, duplicate kaldırıldı

- "Kontroller gizliyken sayfa sayacı" ayarı eklendi.
- Sayaç artık **tek örnek** ve **sağ alt köşede**: `MangaPageItem` içindeki eski köşe
  sayfa numarası metni (ikinci/duplicate gösterim) **kaldırıldı**.
- Değerler her iki moda da uyar:
  - tek sayfa: `2 / 52`
  - çift sayfa (iki sayfa yan yana): `2-3 / 52` (yay aralığı doğru gösterilir)

## 6) "İki sayfa yan yana" + otomatik sayfa birleştirme (kitap görünümü)

- Ayarlar: **"İki sayfa yan yana"** ve **"İlk sayfayı tek göster"** (kapak tek) anahtarları.
- Yatay (LTR/RTL) **ve** dikey mod fark etmeksizin çalışır; RTL'de ilk sayfa sağda durur.
- **Otomatik birleştirme:** iki sayfanın birleşim yerinde (iç kenarlarda) beyaz boşluk
  varsa sayfalar `MangaSpreadStitcher` ile tek görselde birleştirilir — aradaki beyaz
  kenar kırpılıp iki sayfa yapıştırılır, **kitap gibi** kesintisiz yay görünümü oluşur.
  Boşluk yoksa sayfalar yan yana normal gösterilir. Sonuçlar LRU bellekte tutulur.
  "Sayfaları birleştir (kitap görünümü)" anahtarıyla kapatılabilir.

---

## Değişen dosyalar

| Dosya | Değişiklik |
|---|---|
| `data/settings/AppSettings.kt` | 6 yeni manga okuyucu alanı + getter |
| `data/settings/SettingsDataStore.kt` | Anahtarlar, okuma, setter'lar (hız 20–500 clamp) |
| `ui/screens/manga/MangaReaderViewModel.kt` | UiState + setter'lar |
| `ui/screens/manga/MangaReaderScreen.kt` | Alt bar 5 düğme, ayarlar paneli, tam ekran + çıkış fix, sayfa sayacı, klavye kısayolları, otomatik kaydırma, spread yönetimi |
| `ui/screens/manga/components/MangaDualSpread.kt` | **YENİ:** çift sayfa yay + `MangaSpreadStitcher` (beyaz boşluk algılama + birleştirme) |
| `ui/screens/manga/components/MangaPagerReader.kt` | Spread (1–2 sayfa) destekli pager |
| `ui/screens/manga/components/MangaPageItem.kt` | Duplicate sayaç kaldırıldı; webtoon zoom açık/kapalı davranışı |
| `ui/screens/manga/components/MangaWebtoonReader.kt` | `webtoonZoomEnabled` parametresi |

## Kalıcı ayarlar (DataStore)

`manga_dual_page`, `manga_dual_page_first_solo`, `manga_stitch_dual_pages`,
`manga_webtoon_zoom`, `manga_auto_scroll_speed`, `manga_hidden_page_counter`
