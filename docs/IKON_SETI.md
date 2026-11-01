# Kitsugi İkon Seti (3B · saydam zemin · arka plansız)

Bu belge uygulamadaki **ayar satırı / sekme / gezinme çubuğu** ikonlarının kaynağını,
lisansını, eşleme tablosunu ve sıfırdan nasıl yeniden üretileceğini anlatır.

## Neden bu set?

Önceki durumda her satır tek renkli bir Material vektör ikonu kullanıyordu ve ikon,
satır rengiyle doldurulmuş **42 dp'lik yuvarlak bir zemin** üzerine 20 dp boyutunda
çiziliyordu. Küçük ekranlarda bunlar "◯", "•", "✓" gibi anlamsız lekelere dönüşüyor,
satırın ne yaptığını anlatmıyordu. Ara adımda kullanılan emoji seti de "premium"
durmayıp uygulamanın koyu arayüzünden kopuyordu.

Yeni yaklaşımın kuralları:

- **Arka plan yok.** Renkli daire / plaka tamamen kaldırıldı; ikon doğrudan kart
  zemini üzerinde durur, arkası saydamdır.
- **3B + cam görünüm.** Tüm set tek bir sanat yönetimiyle üretilmiş 3B render'lardır:
  cam yüzey, mor → pembe → turuncu degrade, yumuşak iç ışık. Kitsugi'nin koyu teması
  (`#080A10`) ve turuncu vurgusu (`#FB923C`) ile aynı ailedendir.
- **İşlev odaklı.** İkon satırın işlevini doğrudan anlatır (bağlantı = zincir,
  eklenti = yapboz, depolama = çöp kutusu, indirme = bavul, geçmiş = saat, ...).
- **Tek aile, tek ölçek.** Tüm görseller saydam kenarlarından kırpılır, hafif gölge
  kalıntıları temizlenir, alan tabanlı optik dengeyle aynı kutuya (%92 doldurma,
  192×192 tuval) oturtulur. Böylece satır satır boyut zıplaması olmaz.
- **Aynı set her yerde.** Ayarlar satırları, oynatıcı ayarları sekmeleri, alt gezinme
  çubuğu, liste/detay eylemleri ve diyaloglar aynı varlıkları paylaşır — ekranlar
  arasında üslup farkı yoktur.

## Kaynak ve lisans

- Set: **3Dicons** — https://3dicons.co (Vijay Verma)
- Lisans: **CC0 1.0 Universal** (kamu malı; atıf gerekmez, ticari kullanım serbest)
  — tam metin: `third_party/3dicons_LICENSE.txt`
- Render'ların indirildiği ayna depo: https://github.com/Vyshnav2255/notion-icons-3d
  (Apache-2.0; atıf notu: `third_party/3dicons_NOTICE.txt`)
- Stil: `front/gradient` (ön açı, cam degrade). Denenip **reddedilen** stiller:
  `front/premium` (metallik, renksiz), `front/color` (mat, cansız), `iso/premium`.
- Yalnızca PNG render'ları alınır; başka hiçbir varlık kopyalanmaz.

## Dosya yapısı

```
app/src/main/res/drawable-nodpi/ic_kitsugi_*.png   -> uygulamaya gömülen 192×192 PNG'ler (50 adet)
app/src/main/java/com/kitsugi/animelist/ui/icons/KitsugiIconAssets.kt
                                                  -> KitsugiArt enum'u, Material adı eşlemesi, çizim
tools/iconpack/fetch_3dicons.py                   -> varlıkları GitHub Contents API'den indirir
tools/iconpack/raw3d/                             -> indirilen ham PNG'ler (depoya girmez)
tools/iconpack/normalize.js                       -> kırpar, dengeler, drawable-nodpi'ye yazar
tools/iconpack/icons.map.js                       -> mantıksal anahtar -> 3dicons adı + Türkçe etiket
tools/iconpack/mock_previews.js                   -> ekran önizlemeleri (PNG) üretir
tools/iconpack/sheet.js                           -> tüm setin etiketli sayfası (docs/ikon-seti-onizleme.png)
tools/iconpack/theme_matrix.js                    -> tema/vurgu uyum sayfası (docs/ikon-tema-uyumu.png)
third_party/3dicons_LICENSE.txt                   -> CC0 1.0 lisans metni
third_party/3dicons_NOTICE.txt                    -> ayna depo / Apache-2.0 atıf notu
docs/IKON_SETI.md                                 -> bu belge
```

Varlık adı = 3dicons kaynak adı, tireler alt çizgiye çevrilir
(`video-camera` → `ic_kitsugi_video_camera.png`).

## Üretim

```bash
cd tools/iconpack
npm install          # sharp
npm run fetch        # 50 PNG -> raw3d/
npm run normalize    # 50 ikon -> app/src/main/res/drawable-nodpi (192×192, saydam)
npm run preview      # ekran önizleme PNG'leri
npm run sheet        # docs/ikon-seti-onizleme.png
npm run theme        # docs/ikon-tema-uyumu.png (koyu/AMOLED/açık + 6 vurgu rengi)
```

Yeni bir ikon eklemek için:

1. `fetch_3dicons.py` içindeki `NAMES` listesine 3dicons adını ekleyin. Adlar
   `https://api.github.com/repos/Vyshnav2255/notion-icons-3d/contents/icons/front/gradient`
   listesinden alınır; sette `calender`, `sheild`, `zoom`, `picture` gibi yazımlar
   orijinalindedir.
2. `icons.map.js` içine `KEYMAP` + `LABELS` satırı ekleyin.
3. `npm run fetch && npm run normalize`, ardından `KitsugiIconAssets.kt` içine enum
   girdisi + Material adı eşlemesi ekleyin.

> `raw.githubusercontent.com` bu geliştirme ortamından kapalıdır; indirme, Contents
> API listesi + `Accept: application/vnd.github.raw` başlığı ile yapılır.

## Eşleme tablosu (satır → 3dicons varlığı)

| Anahtar | Kullanıldığı yer | 3dicons |
|---|---|---|
| `account` | Kitsugi Hesabı | `card` |
| `connections` | Hesap Bağlantıları | `link` |
| `palette` | Görünüm & Tercihler | `colour-palette` |
| `player` | Oynatıcı | `video-camera` |
| `play` | Oynat | `play` |
| `pause` | Duraklat | `pause` |
| `next` | Sonraki | `next` |
| `rewind` | Geri sar | `back` |
| `extensions` | Eklentiler & Kaynaklar | `puzzle` |
| `integrations` | Harici Entegrasyonlar | `sphere` |
| `network` | Ağ / DNS | `wifi` |
| `sync` | Eşitleme | `wifi` |
| `database` | Veri ve Yedekleme | `locker` |
| `storage` | Depolama | `trash-can` |
| `tune` | İndirme Ayarları | `setting` |
| `download` | İndirmeler | `travel` |
| `history` | İzleme Geçmişi | `clock` |
| `timer` | Zamanlayıcı | `clock` |
| `info` | Hakkında | `bulb` |
| `feedback` | Geri Bildirim | `megaphone` |
| `warning` | Platform Hata Kodları | `sheild` |
| `help` | Yardım | `chat` |
| `memo` | Not / Başlık | `pencil` |
| `gestures` | Hareketler | `magic-trick` |
| `decoder` | Kod çözücü | `tool` |
| `subtitles` | Altyazılar | `chat-text` |
| `audio` | Ses | `headphone` |
| `buttons` | Özel Butonlar | `toggle` |
| `code` | Kod Düzenleyici | `computer` |
| `advanced` | Gelişmiş | `lab` |
| `speed` | Hız | `rocket` |
| `sun` | Aydınlık tema | `sun` |
| `moon` | Karanlık tema | `moon` |
| `tv` | TV / Yayın | `computer` |
| `key` | Şifre / Anahtar | `key` |
| `star` | Puan / Yıldız | `star` |
| `image` | Görsel | `picture` |
| `heart` | Favori | `heart` |
| `clipboard` | Kopyala | `copy` |
| `chart` | İstatistik | `chart` |
| `check_circle` | Tamamlandı | `tick` |
| `lock` | Güvenlik | `lock` |
| `folder` | Klasör | `folder` |
| `bell` | Bildirim | `bell` |
| `calendar` | Takvim | `calender` |
| `sparkles` | Yenilik | `flash` |
| `thumbup` | Beğeni | `thumb-up` |
| `mobile` | Mobil cihaz | `mobile` |
| `pin` | Konum | `pin` |
| `nav_explore` | Keşfet (gezinme) | `explorer` |
| `nav_list` | Listem (gezinme) | `bookmark` |
| `nav_search` | Arama (gezinme) | `zoom` |
| `nav_profile` | Profil (gezinme) | `boy` |
| `nav_settings` | Ayarlar (gezinme) | `setting` |

## Çizim kuralları

| Bağlam | 3B ikon boyutu | Not |
|---|---|---|
| Ayar satırı | 30 dp (32 dp slot) | Metin hizası sabit kalsın diye slot hep aynı |
| Sekme şeridi | 21 dp | Seçili sekmede vurgu rengi + alt etiket |
| Alt gezinme çubuğu | 21 dp (seçili 24 dp) | Seçili değilken %70 alfa |
| Liste / detay eylemi | 24–26 dp | Metin düğmelerinde glif boyutu |
| Satır ayırıcı girintisi | 62 dp | İkon + boşluk hizası |

Eşleşme bulunamazsa `KitsugiRowIcon` **arka plansız**, satır rengiyle boyanmış tek
renkli glife düşer. Close, ArrowBack, Check, ExpandMore, Chevron*, Add, FilterList,
OpenInNew gibi kontrol mikro-ikonları bilerek 2B çizgi olarak kalır; onlar "eylem"
ikonudur, "konu" ikonu değil.

## Önizleme

- Tüm set (etiketli): **`docs/ikon-seti-onizleme.png`** (`npm run sheet`)
- Ekran mock'ları: `npm run preview` → `onizleme_ayarlar.png`, `onizleme_oynatici.png`

## Teknik notlar

- Varlıklar `drawable-nodpi` içinde: DPI ölçeklemesi yok, tek kopya → APK şişmez
  (50 ikon, toplam ~390 KB).
- `KitsugiArt.fromVector()` Material ikon adından otomatik eşleme yapar; 150+ çağrı
  yerini tek tek değiştirmek gerekmez. Yanlış eşleşme riski olan yerlerde
  `art = KitsugiArt.X` ile açıkça belirtilir.
- Aynı varlık birden fazla anahtar tarafından paylaşılabilir (ör. `wifi` → Ağ +
  Eşitleme, `clock` → Geçmiş + Zamanlayıcı, `computer` → Kod + TV).

## Tema ve renk seçimi uyumu (otomatik)

İkon paleti ile ekranın rengi iki yönlü bağlanır; ikisi de **kullanıcının seçimine
göre otomatik** çalışır:

1. **Ton kaydırması (otomatik renk uyumu).** Setin doğal ortalama tonu 303,7°
   (menekşe-mor). Tema kökü, seçili vurgu renginin tonunu hesaplar ve ikonu yalnızca
   *ton* ekseninde o kadar döndürür (`hueRotationMatrix`). Doygunluk ve parlaklık
   korunur; cam/3B görünüm bozulmaz, sadece renk ailesi değişir.
   - Mint → turkuaz, pembe → magenta, mor → mavi-menekşe, mavi → mavi, yeşil → yeşil,
     turuncu → kırmızı-turuncu ailesi.
   - Vurgu **iki renkli gradyan** ise iki rengin ortası esas alınır (renk seçicideki
     `customAccentColor` + `customAccentColor2`).
   - Gri/beyaz gibi **doygunluğu %12'nin altındaki** vurgularda ton kaydırması
     yapılmaz; palet doğal kalır (renksiz bir vurguda ikonu rastgele renge çevirmek
     anlamsız olurdu).
   - Kapatmak için: `KitsugiAnimeListTheme(iconsFollowAccent = false)` — tek parametre,
     varsayılan `true`.
2. **Durum katmanı.** Seçili sekme/gezinme öğesinde vurgu renginden hâle
   (`accentGlow = true`), seçili olmayanda %80–86 alfa.

Tema moduna göre zemin davranışı:

| Durum | Davranış | Neden |
|---|---|---|
| Koyu tema (`#080A10`) | Görsel olduğu gibi, ek katman yok | Cam parlaklıkları koyu zeminde zaten okunuyor |
| AMOLED (`#000000`) | Görsel olduğu gibi | Saf siyahta degrade kontrastı en yüksek |
| Açık tema (`#F3F4F6` / `#FFFFFF`) | İkonun arkasına **plaka değil**, yumuşak radyal gölge (`%24 → saydam`) | Beyaz cam parlamaları beyaz zeminde kaybolur; gölge ikonu "havada" tutar |

Görsel kanıt: **`docs/ikon-tema-uyumu.png`** (`npm run theme`) — doğal palet
(koyu/AMOLED/açık), 6 vurgu renginde otomatik ton, ve "doğal ↔ otomatik" karşılaştırması.

## Nerede 3B, nerede 2B?

**Kural (üçüncü turda değişti): 2B yok.** Uygulamadaki bütün ikon çağrıları 3B sete
bağlı — konu ikonları **ve** kontrol glifleri aynı cam/gradyan ailesinden.

- **Konu ikonları (3dicons `front/gradient`, 50 varlık):** ayar satırları, bölüm
  başlıkları, oynatıcı sekmeleri, gezinme çubuğu, boş durumlar, rozetler.
- **Nesne glifleri (9 varlık, aynı dilde üretildi):** eşitleme, ince ayar (kaydırıcı),
  indirme, yükleme, kronometre, kod, TV, etiket, bulut yedeği. 3dicons'ta bu
  kavramların görseli yoktu; wifi/bavul/monitör gibi alakasız ikonlar kullanılıyordu.
- **Kontrol glifleri (46 varlık, aynı dilde üretildi):** kapat, geri, onay, chevron,
  ok, artı/eksi, filtre, sırala, görünüm modu (ızgara/liste/akış/karusel), taşma,
  dışa aç, tam ekran, kırpma, döndürme, oran, bulanıklık, hizalama, kenarlık,
  tümünü seç, sürükle, yoğunluk, klavye, giriş, ana sayfa, yaprak, kar, pasta,
  kamera, göz/gizle ve metin biçimi harfleri (B I A S # “).
- **Boyut:** konu 30 dp satır / 21 dp sekme / 21–24 dp gezinme; kontrol glifleri
  14–30 dp. Glifler 128×128 üretildi ve **12–30 dp aralığında** okunur
  (`docs/ikon-kontrol-boyut.png`).

## Kapsam denetimi (kaynaktan sayıldı)

`node tools/iconpack/coverage_report.js` → **`docs/ikon-kapsam-denetimi.png`**

| Ölçüm | Değer |
|---|---|
| Kotlin dosyası | 1278 |
| Toplam ikon çağrısı (`Icons.*`) | **1533** |
| 3B sete bağlı | **1533 (%100,0)** — 268 farklı ikon adının tamamı |
| Tek renkli (mono) kalan | **0** — hedef tuttu |
| Tek giriş noktasından geçen dosya | 229 |
| **3B yolundan geçmeyen dosya** | **0** |
| 3B varlık (enum) | **143** — 88 3dicons render'ı + 9 nesne glifi + 46 kontrol glifi |
| Eşlenen Material adı | 428 satır / 268 benzersiz ad — tek tablo: `NAME_MAP` |
| Eşlenmemiş ad | **0** |

Giriş noktaları:

| Yol | Kapsam |
|---|---|
| `ui/theme/gradient/Icon` (proje sarmalayıcısı) | 200 dosya — çağrı değişmeden 3B |
| `KitsugiIcon` (Material3 imzasıyla birebir, `… as Icon` alias) | 30 dosya |
| `KitsugiArtIcon` / `KitsugiRowIcon` | satır, sekme, gezinme, boş durum, kart eylemleri |
| `KitsugiSettingsItem` | tüm ayar diyalogları |

### Kontrol glifleri neden üretildi?

3dicons paketinde kapat / chevron / artı / filtre / sıralama / görünüm değiştirme
işaretleri **yok** (122 dosyalık aynada `plus`, `tick`, `back`, `next`, `zoom`,
`thumb-up`, `thumb-down` dışında kontrol işareti bulunmuyor). Üçüncü turda bu
boşluk `tools/iconpack/control_glyphs.js` ile kapatıldı: glifler setin ölçülen
paletiyle (`#E9A57E → #CF6A86 → #B04F9B → #8A35AB → #7043B2`), aynı üst parlama,
alt gölge ve koyu alt çizgi kurallarıyla çizilip 128 px saydam PNG'ye render
edildi. Sonuç: 385 mono çağrı 3B'ye geçti, **mono 0**.

Görseller:

- `docs/ikon-kontrol-baglam.png` — glifler gerçek ekran bağlamında (satır oku,
  arama temizle, filtre/sırala, görünüm segmenti, açılır bölüm, metin araç çubuğu,
  adet kontrolü, düzenleme şeridi).
- `docs/ikon-kontrol-boyut.png` — 12/14/16/18/20/24/30 dp okunabilirlik testi.
- `docs/ikon-kontrol-sayfasi.png` — 46 glifin adlarıyla tam sayfası.

Ayrıntılı eşleme tablosu: **`docs/IKON_KONTROL_GLIFLERI.md`**.
İşlev haritası görseli: **`docs/ikon-islev-haritasi.png`** (128 varlık + bağlı adlar).

Yeni bir ekranda 3B ikon gerekiyorsa iki yol var:

```kotlin
// 1) Satır/başlık gibi ImageVector alan yerlerde:
KitsugiRowIcon(icon = Icons.Rounded.Download, tint = tint, artSize = 30.dp)

// 2) Doğrudan çizim:
KitsugiArtIcon(art = KitsugiArt.Download, size = 26.dp)
KitsugiArtIcon(art = KitsugiArt.NavSettings, size = 24.dp, accentGlow = selected)
```
