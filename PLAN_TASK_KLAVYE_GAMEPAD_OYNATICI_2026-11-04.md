# PLAN_TASK — Klavye & Gamepad Desteğinin Yeniden Yazılması

**Tarih:** 2026-11-04
**Sürüm:** v2.4.341-beta
**Dal:** `arena/f0b1355d-kitsugi-beta`

---

## 1. Kullanıcı bildirimi

> "klavye ile oynatıcımda bir şeyleri yönetirken sürekli oynatıcı arayüzüm gelip gitmesin,
> sadece tetiklediğim tuşun özelliği görünüp yok olsun."
>
> "Klavye desteğini arşa çıkarmak istiyorum, oynatıcım için daha iyi ve kapsamlı hale
> getirmek istiyorum. Araştırmalarını eksiksiz yapıp düzgün yapabilir misin? Hem görünüm
> hem de kod tabanında uygun şekillerde."

İki ayrı iş:
1. **Hata (bug):** klavye kullanımında tam OSD sürekli açılıp kapanıyor.
2. **Özellik:** klavye/gamepad desteğini açık kaynak oynatıcılar seviyesine çıkarmak.

---

## 2. Kök neden analizi

`KitsugiFullscreenPlayerScreen.onPreviewKeyEvent` (eski hâli, `a6dda9d`):

| Satır | Kod | Sonuç |
|-------|-----|-------|
| — | `viewModel.showControls()` her `KeyDown`'da | Üst bar + alt bar + seekbar 5 sn açılır → **flicker** |
| — | `Key.DirectionLeft` → sabit `-10_000` ms | `seekStepMs` ayarı yok sayılıyordu |
| — | `Key.DirectionUp/Down` → yalnızca `showControls()` | Tuş **hiçbir iş yapmıyordu** |
| — | `playerEngine.handleKey(event.nativeKeyEvent)` | Harfler MPV'ye ham: `q` = çıkış, `s` = screenshot |

Media3 motorunda `handleKey` varsayılan `false` döndüğü için **tüm harf kısayolları ölüydü**.

---

## 3. Araştırma (açık kaynak referanslar)

| Proje | İncelenen | Alınan karar |
|-------|-----------|--------------|
| **mpv** (`etc/input.conf`, manual) | Varsayılan tuş tablosu | `SPACE`, `←/→`, `Shift+←/→`, `↑/↓`, `[ ]`, `{ }`, `j v z Z #`, `m`, `a`, `s`, `i`, `o`, `q` |
| **VLC** | "?" yardım ekranı, `f` tam ekran | `?`/`h` ile yardım katmanı; ön ayar olarak VLC tarzı |
| **Just Player** (`PlayerActivity.java`) | `keySeekStart` birikimi, hold-to-seek | Basılı tutmada **birikimli** toplam süre göstergesi + ivme |
| **mpv-android** (`MPVView.kt`) | `keydown`/`keyup` + modifier iletimi | Basılı tutma süzgeci (45 ms), `KeyUp` ile durum temizliği |
| **aniyomi-mpv-lib** | `input.conf` semantiği | Kullanıcı eşlemesinin (custom bindings) varsayılanı ezmesi |

### Android'de bulunmayan mpv tuşları → Kitsugi karşılıkları

`aosp-mirror/platform_frameworks_base` → `KeyEvent.java` (340 `KEYCODE_*`) ile **tek tek doğrulandı**:

| mpv | Android | Kitsugi |
|-----|---------|---------|
| `{` / `}` | `KEYCODE_LEFT_BRACE` / `RIGHT_BRACE` **YOK** | `-` / `=` (`KEYCODE_MINUS` / `EQUALS`) |
| `#` | `KEYCODE_NUMBER_SIGN` **YOK** (yalnızca `POUND`) | `y` |
| `Backspace` | `KEYCODE_BACKSPACE` **YOK**, doğrusu `KEYCODE_DEL` | `\` (Backspace metin silme davranışını tetikliyor) |
| `frame-step` | İki motorun da API'si yok | `,` / `.` → 1 sn hassas arama |

Bu doğrulama sırasında **derleme hatası olacak 4 sabit** yakalandı ve düzeltildi.

---

## 4. Mimari

```
core/player/input/
├── PlayerShortcut.kt          eylem enum'u (36) + gruplar + PlayerKeyBinding + keyCodeLabel
├── PlayerShortcutMap.kt       varsayılan tablo, resolve(), ön ayarlar, onay gerektirenler
├── PlayerKeyRouter.kt         tuş → eylem, basılı tutma durumu, 45 ms süzgeç
├── PlayerShortcutSettings.kt  DataStore metni ↔ tablo (bozuk metin oynatıcıyı kilitlemez)
├── PlayerOsdDurations.kt      PULSE 900 / NORMAL 1600 / LONG 2600 ms
└── ExternalInputDetector.kt   klavye mi kumanda mı kolu mu? (source + deviceId + keyCode)

ui/screens/fullscreen/
├── components/PlayerKeyboardFeedback.kt   küçük gösterge + kaydırılabilir yardım katmanı
└── controls/PlayerKeyboardController.kt   eylem uygulayıcı + `keyboardActionIcon()`

ui/components/player/PlayerKeyboardTab.kt  "Klavye & Gamepad" ayar sayfası
```

**Kritik tasarım kararı:** Denetleyici `callbacksProvider: () -> PlayerKeyboardCallbacks`
alır, değer kopyası almaz. Oynatıcı ekranı saniyede bir yeniden çizildiği için kopyalanan
`currentEpisode` bayatlıyor ve "sonraki bölüm" yanlış bölümü açıyordu.

---

## 5. Davranış sözleşmesi

| Sorun | Yeni davranış |
|-------|---------------|
| OSD flicker | Tuş basımı **asla** `showControls()` çağırmaz. Yalnızca `indicator` güncellenir. |
| Motor farkı | Eylemler Kitsugi tarafında uygulanır; Media3 ve MPV aynı davranır. |
| Tek harf = oturum kaybı | `Esc` / `q` / `B` → onay diyaloğu |
| Media3'te medya tuşları ölü | `Media3PlayerEngine.handleKey` köprüsü eklendi |
| TV kumandasında ok tuşu çakışması | `ExternalInputDetector`: gerçek klavye/kol görülene kadar D-pad kısayolları sahiplenilmez |
| Ses tuşlarını yakalama | Donanım `VOLUME_UP/DOWN` **bilinçli olarak bağlanmadı** (sistem sesi bozulmasın) |
| `m` sesi en yükseğe fırlatıyordu | mpv `cycle mute` gibi **önceki seviyeye** döner |
| Arayüzü keşfedilebilirlik | Üst barda "Klavye: ? ile tüm kısayollar"; göstergede tuş rozeti |

---

## 6. Ayarlar

`PlayerSettingsSubScreen.Klavye` (ayarlar aramasında bulunur):

- Klavye kısayolları aç/kapa · harici klavye otomatik algılama · gamepad
- Gösterge: aç/kapa · süre çarpanı (0,5× … 2×) · "eski davranış" (tam OSD)
- Arama adımları: ok / hassas / uzun (sn) · basılı tutarak hızlandırma
- Kısayol listesi: 36 eylem, tuşuyla; dokunarak **tek tek** susturma
- Ön ayarlar: Varsayılan (mpv) · VLC tarzı · Sade

DataStore biçimi:
- `keyboardDisabledShortcuts` = `"EXIT,SCREENSHOT"` (eylem adları)
- `keyboardCustomBindings` = `"shift+keycode22=SEEK_FORWARD_PRECISE"`

---

## 6b. İkinci tur: konum ve tema düzeltmeleri (kullanıcı geri bildirimi)

Kullanıcı: *"ikon göstertmeleri buglu bozuk şekilde çıkmasın… konumları hep yanlıştı,
salak saçma konumlarda çıkabiliyordu, temamla uyumlu olsun."*

### Kök nedenler

| Belirti | Kök neden | Düzeltme |
|---------|-----------|----------|
| Gösterge yanlış yerde | Sabit `padding(bottom = if (controlsShown) 132.dp else 56.dp)` — altyazı konumu hiç dikkate alınmıyordu | Altyazının `pos` değerinden hesaplanan gerçek alt boşluk + OSD payı |
| Çift dokunma balonu başlıkla çakışıyor | Sabit `top = 80.dp` üst bar yüksekliğine göre değil | `controlsShown` → 72dp / kapalı → 28dp; iki balon üst üste binmiyor |
| Tema uyumsuz kutu | Yüzey `Color(0xFF050510)` sabiti, vurgu rengi hiç kullanılmıyordu | `playerSurfaceColor()` + vurgu kenarlık/ikon/çubuk |
| İlerleme çubuğu yok | Hiç eklenmemişti | Sarma/ses/hız için `progress` alanı + ince bar |
| Derleme hatası | `accentColor: Color = KitsugiColors.Accent` — bu bir `@Composable` getter | `Color? = null` + gövdede `?: KitsugiColors.Accent` |

### Yardım ekranı arama denemesi (geri alındı)
Arama alanı eklendi ama oynatıcı `onPreviewKeyEvent`'i **yardım ekranı açıkken her tuşu
yutuyor** (tek tuş = kapat). Metin alanına yazmak imkânsız olduğu için geri alındı;
yerine dokunarak kapatma ve gruplu liste korundu. Not: arama için tuş yutmayı devre dışı
bırakmak, `?` ekranını klavye ile kontrol edilemez hâle getirirdi.

## 7. Doğrulama (derleme ortamı yok)

Bu ortamda JDK/Android SDK yok; `./gradlew assembleDebug` çalıştırılamadı.
Yapılan statik doğrulamalar:

1. **Parantez/süslü denge** — 9 yeni dosyada `{ ( [` sayaçları sıfır.
2. **`KEYCODE_*` sabitleri** — 340 gerçek sabit listesine karşı taranan tüm depo: 0 geçersiz.
3. **`PlayerShortcutAction` eksiksizliği** — 36 eylem `perform()` ve `keyboardActionIcon()`
   içinde `when` exhaustive; eksik yok.
4. **Callback alanları** — ekran ↔ `PlayerKeyboardCallbacks` birebir; kullanılmayan/fazla yok.
5. **İkon adları** — Repoda `Icons.Rounded.*` olarak kullanılan adlarla çapraz kontrol.
6. **Ayar anahtarları** — 13 anahtar: yazma (setter) ↔ okuma (parse) ↔ ayar sayfası ↔ ekran.
7. **Yönlendirme zinciri** — arama girdisi → `PlayerSettingsSubScreen.Klavye` → sekme → `when` dalı.

**Kullanıcı tarafında doğrulanması gerekenler:** gösterge konumu/ölçeği, yardım katmanının
telefonda kaydırılabilirliği, `?` ipucunun okunabilirliği (TV'de 10-foot mesafede).
