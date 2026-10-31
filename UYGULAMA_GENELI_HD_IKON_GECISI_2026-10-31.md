# Uygulama Geneli HD İkon Geçişi — 2026-10-31

## Amaç
Uygulamadaki **tüm** tıklanır alanların (butonlar, ikon butonlar, açılır-kapanır
kontroller, ayarlar satırları, alt sayfalar, diyaloglar, TV ekranları) ikonlarını:
1. tek ortak mantığa bağlamak,
2. **arka plansız, HD vektör** hâle getirmek,
3. tema/vurgu rengiyle (düz + gradyan) otomatik uyumlu kılmak,
4. açılır-kapanır alanlarda **animasyonlu** hâle getirmek.

## 1) Tek kaynak: `ui/icons/KitsugiIcons.kt`
- **208 HD ikon** (204 Lucide çizgi ikonu + heart/star/bookmark/bell dolu varyantları),
  24dp ızgara, 2dp çizgi, yuvarlak uçlar — hepsi `ImageVector` olarak **lazy** üretilir.
- **226 uyumluluk adı**: eski Material adları (`Icons.Rounded.Star`, `Icons.AutoMirrored.Rounded.ArrowBack`, …)
  aynı HD ikona bağlandı → mevcut kod değişmeden çalışır.
- Dolgu semantiği korundu: `Icons.Favorite` → dolu kalp, `Icons.FavoriteBorder` → çizgi kalp;
  `Star/StarBorder`, `Bookmark/BookmarkBorder` aynı şekilde.
- Hiçbir ikonda gömülü renk/arka plan yok: renk daima kullanıldığı yerin `tint` değerinden
  (ve `LocalKitsugiAccent` gradyan fırçasından) gelir.

## 2) Göç: 251 dosya
- `import androidx.compose.material.icons.Icons` → `import com.kitsugi.animelist.ui.icons.KitsugiIcons as Icons`
- `Icons.Rounded.X`, `Icons.Default.X`, `Icons.Filled.X`, `Icons.AutoMirrored.Rounded.X`,
  `androidx.compose.material.icons.Icons.Rounded.X` → `Icons.X`
- Tek tek ikon importları ve `rounded.*` joker importları kaldırıldı.
- Doğrulama: kodda **kalan Material ikon importu: 0**; kullanılan 286 aday ikon adının
  tamamı `KitsugiIcons` içinde mevcut (eksik: 0).

## 3) Animasyonlu kontroller: `ui/components/KitsugiIconKit.kt`
- `KitsugiChevronIcon`: açılır/kapanır alanlarda **tek chevron 0°↔180° yay animasyonuyla döner**
  (eski `if (expanded) ExpandLess else ExpandMore` sıçraması kaldırıldı) — 20 kullanım noktası.
- `KitsugiIconButton`: basınca %14 küçülen, tema duyarlı, arka plansız ortak ikon butonu.
- `KitsugiStateIcon`: iki durumlu ikonlarda (oynat/duraklat, göster/gizle) yumuşak geçiş.
- Üst çubuk butonları (`KitsugiTopActionButton`) seçili durumda dolu varyanta geçer
  (`FAVORITE` → `HeartSolid`) + basınç animasyonu.

## 4) Emoji ile çizilen "ikon"lar HD vektöre çevrildi
| Yer | Önce | Sonra |
|---|---|---|
| Keşfet çevrimdışı bandı | 📡 emoji | `Icons.Satellite` |
| Kitsu giriş diyaloğu | 🦊 emoji | Marka logosu (`KitsugiPlatformLogo`) |
| Shikimori giriş diyaloğu | 🌸 emoji | Marka logosu |
| Eklenti tanılama | 🔬 emoji | `Icons.FlaskConical` |
| TV QR girişi | 📱 emoji | `Icons.Smartphone` |
| Üst çubuk rastgele | 🎲 emoji | `Icons.Dices` |

Kalan emoji kullanımları **ikon değil**, platform/ülke bayrakları ve içerik etiketleridir
(ör. 🇹🇷 ülke filtresi, ⭐ puan metni, ★ favori rozet metni) — bunlar metin olduğu için korundu.

## 5) Tema uyumu
- Tüm ikonlar `ui.theme.gradient.Icon` üzerinden çizilir → vurgu rengi veya gradyan modu
  otomatik uygulanır, `tint` verilen yerlerde o renk kullanılır.
- Ayarlar sayfası satırları (`KitsugiSettingsItem`), arama sonuçları, alt sayfalar, TV
  ekranları dahil her yer yeni setten besleniyor.
- Arka planı olan ikon kutuları (vurgu dairesi vb.) korunuyor; **ikonun kendisi asla arka plan taşımıyor.**

## Doğrulama
- 251 dosyada parantez/köşeli parantez dengesi HEAD ile birebir aynı (yapısal bozulma yok).
- `KitsugiIcons` içinde yinelenen ad yok; tüm yollar geçerli SVG path sözdiziminde.
- İkon önizlemesi: `gorseller/kitsugi_hd_ikon_seti_preview.png` (208 ikon).

## Notlar
- Material ikon bağımlılığı Gradle'dan **kaldırılmadı** (zararsız); artık hiçbir kod onu kullanmıyor.
- Lisans: Lucide ikonları ISC lisanslıdır; uygulama içi "Hakkında" bölümüne eklenmesi önerilir.
