# PLAN & TASK — Wiki okuyucu tema uyumu ve şerit sadeleştirmesi

Tarih: 2026-10-23
Dal: `arena/d608bb0a-kitsugi-beta`
Başlangıç commit'i: `76eff11`
Sürüm hedefi: `v2.4.353`

---

## 1. Kullanıcı isteği (ham)

> "Wiki açılır sayfasında işaretlediğim bölge ve wiki açılır sayfası görünümü teması
> uygulamam ile daha uyumlu ve derli toplu hale neden getirilmedi? Ayrıca benim aklıma
> gelmeyen mantıksal şekilde şunlar da olsa dediğin özellikleri de ekleyebilirsin."

İşaretlenen bölge (ekran görüntüsü):

| Satır | İçerik |
| --- | --- |
| 1 | `Özgün sayfa` · `Sade okuyucu` · `Mobil düzen` · `Yenile` (mod çipleri) |
| 2 | `kiminonawa.fandom.com · Özgün tema; çeviri ve reklam engeli sayfa araçlarından yönetilir.` |
| 3 | `Orijinal göster` + `Reklam engeli: açık (13)` (iki tam genişlik çip) + `⋮` |

**Tespit:** Bu yerleşim 2026-10-21 tarihli ikinci turda kaynak koddan kaldırılmıştı
(`ReaderBrowserMenu`, `ReaderTabStrip`, `WikiPageToolsDialog`). Görüntü eski bir APK'ya ait.
Yine de "tema uyumu + derli topluluk" isteği somut olarak ele alındı.

---

## 2. Plan (neden → ne)

| # | Sorun | Kök neden | Planlanan çözüm | Durum |
| --- | --- | --- | --- | --- |
| 1 | Wiki sayfası uygulama temasıyla uyuşmuyor | `wikiPageTheme` varsayılanı `SITE` | Varsayılan `APP`; `SITE` seçeneği korunur, kayıtlı tercih ezilmez | ✅ |
| 2 | Sekme şeridi "kutu ormanı" gibi | Aktif sekme opak arka plan, pasif sekme yarı opak | Aktif: %16 vurgu dolgusu + %40 çerçeve; pasif: saydam | ✅ |
| 3 | Üst bölge bilgi taşıyor ama yer kaplıyor | Alan adı + açıklama ayrı satırdaydı | Bilgi ⋮ menüsünün başlığına taşındı (kilit + host + mod) | ✅ |
| 4 | Sayfa araçları tek sütun yığın | Ayırıcı çizgili düz liste | Başlıklı gruplar: Çeviri ve gizlilik / Görünüm / Sayfa | ✅ |
| 5 | Yükleme bitince içerik zıplıyor | `LinearProgressIndicator` 4 dp yer kaplayıp kayboluyordu | Her zaman 2 dp ayrılmış ince çubuk (elle çizim) | ✅ |
| 6 | Bul çubuğu temayla uyumsuz | Nötr çerçeve, köşesiz alan | 12 dp yuvarlak, vurgu odak rengi, ayrı yüzey zemini | ✅ |
| 7 | Ayar penceresi vurguları nötr | `TextPrimary` seçili renk | Radyo/slider/FAB vurgu rengine bağlandı | ✅ |
| 8 | FAB ikonu açık vurguda okunmuyor | Sabit `Color.White` | `onAccentColor()` kontrast hesabı | ✅ |

---

## 3. Eklenen mantıksal özellikler ("aklıma gelmeyenler")

| Özellik | Neden mantıklı | Nerede | Durum |
| --- | --- | --- | --- |
| **Bağlantıyı paylaş** | Uzun makaleyi dışarı taşımak için tarayıcıya çıkmak gerekiyordu | ⋮ menüsü | ✅ |
| **Bağlantıyı kopyala** | Paylaşım hedefi yoksa pano yeterli; kısa onay gösterilir | ⋮ menüsü | ✅ |
| **Sekmeler (n) listesi** | 12 sekmede şerit daralıyor, gezinmek zorlaşıyor | ⋮ menüsü — yalnızca n>1 iken | ✅ |
| **Yazıyı büyüt / küçült** | Her 10 puanda bir diyalog açmak angarya | Sayfa araçları → Görünüm | ✅ |
| **Okurken ekranı açık tut** | Uzun okumada ekran sönüyordu | Sayfa araçları → Görünüm (sekme bazlı, oturumluk) | ✅ |
| **Ayarlar → Okuma görünümü** | Ayar yalnızca okuyucu içinden erişilebiliyordu | Ayarlar → Fandom Wiki | ✅ |
| **Menü başlığı** | Host/mode bilgisi kaybolmasın ama yer kaplamasın | ⋮ menüsü üstü | ✅ |

---

## 4. Dosya bazlı görev listesi

| Dosya | Değişiklik | Durum |
| --- | --- | --- |
| `data/settings/AppSettings.kt` | `wikiPageTheme` varsayılanı `APP` | ✅ |
| `data/settings/SettingsDataStore.kt` | Okuma varsayılanı `APP` | ✅ |
| `ui/.../wiki/FandomWikiReader.kt` | Menü başlığı, paylaş/kopyala, sekme listesi, şerit görselleri | ✅ |
| `ui/.../wiki/WikiPageTools.kt` | Gruplu araç sayfası, hızlı yazı boyutu, ekran açık tut, pencere `internal` API | ✅ |
| `ui/.../wiki/WikiOriginalPage.kt` | `keepScreenOn` oturum alanı, 2 dp sabit ilerleme çubuğu | ✅ |
| `ui/.../wiki/WikiFindBar.kt` | Temalı ve yuvarlatılmış arama alanı | ✅ |
| `ui/.../wiki/FandomWikiSettingsSection.kt` | Ayarlardan okuma görünümü girişi | ✅ |
| `res/values/strings.xml`, `values-en/strings.xml` | 14 yeni metin + 2 güncelleme (TR/EN eşit) | ✅ |
| `docs/WIKI_READER_CHROME_THEME_2026-10-23.md` | Teknik rapor | ✅ |
| `docs/WIKI_READING_USABILITY_2026-10-21.md` | Varsayılan tema notu güncellendi | ✅ |
| `release_notes_v2.4.353.md` | TR/EN sürüm notu | ✅ |

---

## 5. Doğrulama planı

| Kontrol | Yöntem | Durum |
| --- | --- | --- |
| Kotlin derlemesi | GitHub Actions `android-build.yml` (foss debug + R8 release + DEX) | ⏳ CI |
| Kullanıcı metinleri | TR/EN anahtar eşitliği betiği (17552 = 17552) | ✅ |
| Biçim | `git diff --check` | ✅ |
| Cihazda görsel kabul | APK kurulumu → ⋮ menüsü, sekme listesi, paylaşım, ekran açık tut | ⏳ kullanıcı |

### CI'da yakalanıp düzeltilen iki hata

1. `FandomWikiReader.kt` — `ButtonDefaults` importu eksikti (`Unresolved reference`).
2. `WikiPageTools.kt` — `onCheckedChange` için `Function1<Boolean, Unit>` beklenirken
   `Function0<Unit>` verilmişti; `{ onToggleKeepScreenOn(!keepScreenOn) }` yapıldı.

---

## 6. Riskler ve bilinçli sınırlar

- **Varsayılan tema değişikliği** yalnızca hiç seçim yapmamış kullanıcıyı etkiler;
  `SITE` seçmiş kullanıcının tercihi korunur.
- **Tema değişimi site sözleşmesine bağlı**: Fandom `theme-fandomdesktop-*` ve modern
  Wikipedia `skin-theme-clientpref-*` sınıfları. Bu sözleşmeyi taşımayan eski/özel
  görünümlerde site renkleri korunur ve kullanıcıya "tema sınırlı" satırı gösterilir.
  Görsellere filtre uygulanmaz.
- **Ekranı açık tut** ayarlara yazılmaz; uygulama yeniden başlarsa kapanır (pil güvenliği).
- **Paylaş/kopyala** özgün görünümde WebView'in gerçek adresini kullanır; sade okuyucuda
  sayfanın wiki adresini. Adres çözülemezse eylemler devre dışıdır.
- **Sekme listesi** yalnızca okuma amaçlıdır; sekme sırası değiştirme/sürükleme yok.

---

## 7. Sonraki adımlar (öneri)

- [ ] Cihazda ⋮ menüsü ve sekme listesinin TV/dokunmatik düzenlerinde denenmesi
- [ ] Koyu tema + açık site kombinasyonunda `SITE` modunun gerçek sayfalarla sınanması
- [ ] `wikiPageTheme` varsayılanı için ayarlarda tek seferlik bilgi notu (isteğe bağlı)
- [ ] Release: `release.yml` → `2.4.353`
