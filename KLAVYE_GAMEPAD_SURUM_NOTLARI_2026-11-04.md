# Kitsugi Beta v2.4.341

## 🇹🇷 Türkçe

### ⌨️ Klavye Desteği Baştan Yazıldı — "Arayüz Gelip Gitmesin"

Kullanıcı bildirimi: *"klavye ile oynatıcımda bir şeyleri yönetirken sürekli oynatıcı
arayüzüm gelip gitmesin, sadece tetiklediğim tuşun özelliği görünüp yok olsun."*

- **Tam OSD artık tuş basımıyla açılmıyor.** Eskiden her klavye olayında
  `showControls()` çağrılıyor, üst bar + alt bar + seekbar 5 saniyeliğine açılıyordu.
  Artık yalnızca **tetiklenen eylemin küçük göstergesi** (ikon + değer) ekranın
  alt-orta bölgesinde belirip kayboluyor. Tam arayüz yalnızca `O` / `Tab` /
  `C` (ayarlar) gibi bilinçli tuşlarla açılıyor.
- **Tek tuş = tek eylem, motordan bağımsız.** Kısayollar artık Kitsugi tarafında
  uygulanıyor: Media3 (ExoPlayer) motorunda da MPV motorunda da aynı şekilde çalışıyor.
  Önceden harfler MPV'ye ham gidiyordu (mpv'de `q` = çıkış, `s` = ekran görüntüsü gibi
  masaüstü varsayımları), Media3'te ise tamamen ölüydü.
- **Yanlışlıkla çıkış koruması:** `Esc` / `Q` / gamepad `B` artık onay istiyor
  ("Oynatıcıdan çıkılsın mı?"). Tek harf basımıyla izleme oturumu kaybedilmiyor.
- **Kısayol yardım ekranı:** `?` (veya `H`) ile gruplanmış tam liste; oynatma durmaz.
  Üst barda "Klavye: ? ile tüm kısayollar" ipucu gösterilir.

### 🎮 Kapsamlı Kısayol Seti (mpv + Just Player + mpv-android referanslı)

| Tuş | Eylem |
|---|---|
| Boşluk / Enter / medya tuşu / gamepad A | Oynat–durakla |
| ← / → | ±5 sn (ayarlanabilir) |
| ↑ / ↓ | ±60 sn (ayarlanabilir) |
| Shift + ← / → · Ctrl + ← / → · `,` `.` | ±1 sn hassas arama |
| `[` / `]` | Hız ∓/± %10 |
| `-` / `=` | Hızı yarıla / ikiye katla (mpv `{` `}` karşılığı) |
| `\` | Hızı 1× yap |
| `m` · `9` / `0` | Sessiz · ses güçlendirme |
| `j` · `v` · `z` / `Shift+z` · `y` | Altyazı parçası · görünürlük · gecikme ±100 ms · ses parçası |
| PgUp / PgDn · Shift+PgUp/PgDn · gamepad L1/R1 | Bölüm · bölüm işareti |
| `a` · `f` · `i` · `s` | Görüntü oranı · yönlendirme · istatistik · ekran görüntüsü |
| `o` / `Tab` · `l` · `?` / `h` · `Esc` / `q` | Arayüz · kilit · yardım · çıkış (onaylı) |

- **Basılı tutma ivmesi:** Ok tuşları veya ses tuşu basılı tutulduğunda adım giderek
  büyür ve gösterge **birikimli toplam süreyi** gösterir (Just Player davranışı);
  aşırı hızlı sürücü tekrarları 45 ms eşiğiyle süzülür (mpv-android yaklaşımı).
- **Oynat/duraklat tuşunu basılı tutmak titreme yapmaz** — yalnızca ilk basış işlenir.
- **Donanım ses tuşları bilinçli olarak yakalanmıyor:** sistem ses kontrolü bozulmaz.
- **Gamepad:** A = oynat/duraklat, B = çıkış onayı, L1/R1 = bölüm işareti,
  L2/R2 = sar, D-pad = arama.
- **TV kumandası çakışması çözüldü:** gerçek klavye/kol algılanana kadar kumandanın
  ok tuşları menü gezinmesinde kalır; fiziksel klavye takılınca kısayollar anında devreye girer.
- **Göstergede tuş rozeti:** her kısayol, tetiklendiği tuşu (ör. `Shift + →`) sağ üst
  köşede gösterir; tuşları deneyerek öğrenebilirsiniz.
- **Media3 için medya tuşu köprüsü:** kulaklık düğmesi / Bluetooth oynat-duraklat /
  TV kumandası artık ExoPlayer motorunda da çalışır.

### 🎨 Gösterge Konumları ve Tema Uyumu Düzeltildi
Kullanıcı geri bildirimi: *"ikon göstertmeleri buglu bozuk şekilde çıkmasın… konumları hep
yanlıştı, salak saçma konumlarda çıkabiliyordu, temamla uyumlu olsun."*

- **Alt gösterge konumu artık hesaplanıyor:** sabit `56dp`/`132dp` yerine altyazının
  gerçek dikey konumu (Ayarlar → Altyazı → `pos`), OSD'nin görünürlüğü ve alt barın
  yüksekliği birlikte hesaba katılıyor. Altyazıyı yukarı taşıyan kullanıcıda gösterge
  artık altyazının **üstünde** duruyor; TV/tablet'te ortaya düşmüyor.
- **Çift dokunma sarma balonu:** üst bar açıkken başlıkla çakışıyordu → OSD durumuna
  göre konumlanıyor; en-boy oranı balonu sarma balonuyla üst üste binmiyor.
- **Tema uyumu:** gösterge yüzeyi sabit `#050510` yerine `playerSurfaceColor()` (oynatıcı
  panelleriyle **aynı** formül); kenarlık, ikon, tuş rozeti, ilerleme çubuğu ve yardım
  ekranı grupları artık **seçili vurgu rengini** (gradyan dahil) kullanıyor.
- **İlerleme çubuğu eklendi:** sarma (konum/süre), ses (seviye) ve hız (0,25×–4×)
  göstergelerinde ince bir bar — "ileri geri sarma barı" isteği.
- **Yardım ekranı dokunmayla kapanıyor** (fiziksel klavyesi olmayanlar için).
- **`KitsugiColors.Accent` varsayılan parametre hatası düzeltildi:** bu bir `@Composable`
  getter olduğu için varsayılan argüman olarak yazılamaz; `null` + gövdede çözümlemeye
  çevrildi (derleme hatası olacaktı).

### 🧭 Yeni "Klavye" Ayarlar Sekmesi
Oynatıcı Ayarları → **Klavye** (ayarlar aramasında da bulunur):

- Klavye kısayolları aç/kapa (kapalıyken tuşlar MPV'ye ham gider, kendi
  `input.conf` eşlemeleriniz çalışmaya devam eder).
- Harici klavyeyi otomatik algıla: dokunmatik kullanımda D-pad davranışı bozulmaz.
- Gamepad desteği aç/kapa.
- Gösterge: aç/kapa, süre çarpanı (0,5× … 2×), "klavye tam arayüzü açsın" (eski davranış).
- Arama adımları: normal / hassas / uzun (sn) + basılı tutarak hızlandırma.
- **Kısayol listesi:** her eylem, tuşuyla listelenir; dokunarak tek tek susturulabilir.
- **Hazır ön ayarlar:** Varsayılan (mpv uyumlu) · VLC tarzı · Sade.

### 📝 Android Tuş Kodu Notu
mpv'nin `{` `}` `#` `Backspace` kısayolları Android'de **tuş kodu olarak yok**; Kitsugi
bunları `-` `=` `y` `\` ile eşledi ve yardım ekranında doğru etiketleri gösteriyor.
Kare kare ileri/geri adımı iki motorun da API'sinde bulunmadığı için `,` `.` tuşları
1 sn'lik hassas aramaya bağlandı (yanlış vaat yok).

### 🧱 Kod Tabanı
- Yeni çekirdek modüller: `core/player/input/` (`PlayerShortcut`, `PlayerShortcutMap`,
  `PlayerKeyRouter`, `PlayerShortcutSettings`, `PlayerOsdDurations`).
- Yeni UI: `KeyboardActionIndicator`, `KeyboardShortcutsHelpOverlay`,
  `PlayerKeyboardController`, `PlayerKeyboardTab`.
- 400 satırlık iç içe `when` bloğu tek bir yönlendiriciye taşındı; tüm varsayılanlar
  saf (pure) tablo halinde ve tek yerden test edilebilir.

---

## 🇬🇧 English

### ⌨️ Keyboard Support Rewritten — "No More UI Flashing"

User report: *"when I control things with the keyboard, my player UI keeps popping in and
out; only the feature I triggered should appear and vanish."*

- **Full OSD no longer opens on key press.** Every key event used to call
  `showControls()`, flashing the top bar, bottom bar and seekbar for 5 seconds.
  Now only a **compact indicator** for the triggered action appears bottom-center
  and fades away. The full UI opens only via intentional keys (`O`, `Tab`, `C`).
- **One key = one action, engine-agnostic.** Shortcuts are applied by Kitsugi itself,
  so they behave identically on Media3 (ExoPlayer) and MPV. Previously letters were
  forwarded raw to MPV (`q` = quit, `s` = screenshot…), and were completely dead on Media3.
- **Accidental-exit protection:** `Esc` / `Q` / gamepad `B` now ask for confirmation.
- **Shortcut help overlay:** `?` (or `H`) opens a grouped cheat sheet without pausing,
  and the top bar hints "Keyboard: ? for all shortcuts".

### 🎮 Comprehensive Shortcut Set (mpv + Just Player + mpv-android inspired)
Space/Enter/media/Gamepad A = play-pause · ←/→ = ±5 s · ↑/↓ = ±60 s · Shift/Ctrl+arrows
and `,` `.` = ±1 s precise · `[` `]` = speed ∓10% · `-` `=` = halve/double ·
`\` = 1× · `m` mute · `9`/`0` volume boost · `j` `v` `z` `Shift+z` `y` subtitle/audio tracks ·
PgUp/PgDn episodes · Shift+PgUp/PgDn · L1/R1 chapters · `a` aspect · `f` orientation ·
`i` stats · `s` screenshot · `o`/`Tab` UI · `l` lock · `?` help · `Esc`/`q` exit (confirmed).

- **Hold-to-accelerate:** held keys grow their step and the indicator shows the
  accumulated total (Just Player model), with 45 ms repeat de-bouncing (mpv-android model).
- **Play/pause never flickers** while held.
- **Hardware volume keys are deliberately not captured**, so system volume keeps working.
- **Gamepad:** A play/pause, B confirmed exit, L1/R1 chapters, L2/R2 seek.
- **Media3 media-key bridge** for headset/Bluetooth/TV-remote play-pause.

### 🎨 Indicator Positions and Theme Harmony Fixed
User feedback: *"the icon indicators shouldn't come out broken… their positions were
always wrong, they could appear in nonsense places, make them match my theme."*

- **Bottom indicator position is now calculated** from the real subtitle `pos`, OSD
  visibility and bottom-bar height instead of hard-coded `56dp`/`132dp`.
- **Double-tap seek bubble** no longer collides with the top bar title; the aspect-ratio
  bubble offsets itself when both are visible.
- **Theme harmony:** indicator surface uses `playerSurfaceColor()` (same formula as player
  panels); border, icon, key badge, progress bar and help-sheet groups all use the
  current accent (gradient aware).
- **Progress bar added** for seek / volume / speed indicators.
- **Help sheet is dismissible by tap** as well as any key.
- **Fixed a real compile error:** `KitsugiColors.Accent` is a `@Composable` getter and
  cannot be used as a default parameter value.

### 🧭 New "Keyboard" Settings Tab
Enable/disable shortcuts, external keyboard auto-detection, gamepad support, indicator
on/off + duration scale + optional legacy full-OSD behaviour, seek step sizes,
hold-to-accelerate, per-action mute list, and presets (Default / VLC-style / Minimal).

### 📝 Android Key-Code Note
mpv's `{` `}` `#` `Backspace` shortcuts **do not exist** as Android key codes; Kitsugi maps
them to `-` `=` `y` `\` and shows the correct labels in the help sheet. Frame-by-frame
stepping has no API in either engine, so `,` `.` are bound to 1-second precise seeking
(no false promises).

### 🧱 Codebase
New `core/player/input/` modules, `PlayerKeyboardController`, `PlayerKeyboardFeedback`
and `PlayerKeyboardTab`; the old 400-line nested `when` block became a single router with
a pure, testable default table.
