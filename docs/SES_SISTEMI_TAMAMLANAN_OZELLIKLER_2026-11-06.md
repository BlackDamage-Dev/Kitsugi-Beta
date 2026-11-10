# Ses Sistemi — Tamamlanan Özellikler (2026-11-06)

Bu belge, okuyucu müzik sisteminde **sırayla tamamlanan 8 özelliği** ve
mimari kararları kaydeder. Önceki raporlar (`OZELLIK_MATRISI_*`,
`DENETIM_RAPORU_*`, `UYGULAMA_RAPORU_*`) hâlâ geçerli.

---

## Mimari temel: tek kanal havuzu

Sistemin **değişmez kuralı**: sesin kaynağı ne olursa olsun (prosedürel
sentez, Jamendo, Openverse, Archive, Freesound, Audius, binaural) **aynı
kanal modeline** girer.

```
herhangi bir kaynak
    ↓
AmbientSound (ortak model)
    ↓
addChannel() → ChannelState
    ├─ ses seviyesi kaydırıcısı      ✅
    ├─ mute                          ✅
    ├─ kaldır                        ✅
    ├─ ana ses seviyesi              ✅
    ├─ tümünü durdur                 ✅
    └─ uyku zamanlayıcısı            ✅
```

`AmbientAudioEngine` kaynağın türünü **bilmez**. `ChannelVoice` arayüzü
iki arka uca ayrılır (`ProceduralVoice` = AudioTrack, `StreamVoice` =
ExoPlayer) ama panel bunu hiç görmez. Kaynak ayrımı yapan bir UI dalı
**yoktur** — eklenmesi de yapısal olarak engellenmiştir.

6 yuva: `MAX_AMBIENT_CHANNELS = 6`.

---

## Tamamlanan 8 özellik

### 1. Uyku zamanlayıcısı ⏱

| Özellik | Değer |
|---|---|
| Hazır süreler | 5, 10, 15, 30, 45, 60, 90, 120 dk |
| Özel süre | 5 – 240 dk (kaydırıcı) |
| Kapanış fadesi | 12 sn (yapılandırılabilir 0–60) |
| Kontroller | Duraklat / devam / +10 dk / iptal |

**Davranış:** Süre dolunca ses **aniden kesilmez**. Son 12 saniyede tüm
kanalların ses seviyesi doğrusal olarak sıfıra iner, *sonra* durur.
Uykuya dalmak üzere olan kullanıcıyı uyandırmamak için.

**Erişim:** Panel → "Süre" sekmesi (tam ekran sayaç + ilerleme çubuğu) ve
Karışım sekmesinde hızlı erişim (15/30/60 dk düğmeleri). Çalışırken
sekme etiketinde sayaç görünür: `⏱ 23:41`.

### 2. Karışım profilleri 💾

- Şu anki 6 kanal düzeni **tek dokunuşla** kaydedilir
- Ad verilir, listede özet görünür: `3 ses · Yağmur + Piyano`
- Yükleme: tüm kanallar durur, profil düzeni kurulur
- Silme, uzun süre saklanır (`MusicStateStore` → DataStore)

**Dışa/içe aktarma (JSON):**
- Paylaş → panoya kopyala
- İçe aktar → panodan oku, geçersizse kullanıcıya söyle

Profil **yalnızca** ses kimliği + seviye + mute saklar; ses dosyası veya
kaynağı değil. Uzak akışlar da prosedürel sesler de aynı profilde yaşar.

### 3. Arka plan çalma + bildirim 🔔

`AmbientPlaybackService` — `mediaPlayback` tipli foreground service.

**Neden:** Android 17 (API 37) arka planda ses için çalışan FGS şart
kılıyor. Aksi hâlde uygulama arka plana geçince sistem sesi keser.

Bildirimden yapılabilenler:
- Duraklat / devam
- Durdur
- Süreyi iptal
- Uygulamaya dön

Bildirim içeriği canlı: `"3 ses çalıyor · Yağmur, Piyano, Ateş · ⏱ 12:30"`

**Singleton motor:** `AmbientAudioEngine.get(context)` — süreç genelinde
tek örnek. Okuyucudan çıkınca ses devam eder (ayar açıksa, varsayılan
açık). Motoru ekran kapanınca `release()` etmek sesi keserdi.

**Yetki güvenliği:** Android 12+ arka plandan FGS başlatma kısıtı ve
Android 13+ bildirim izni nedeniyle başlatma başarısız olabilir. Bu
durumda uygulama **çökmez** — ses yalnızca ön planda çalar.

### 4. Favoriler + son çalınanlar ❤️

- "Senin İçin" sekmesinde **FAVORİLER** bölümü (en üstte)
- **SON ÇALINANLAR**: son 3 ses, kompakt kartlar
- Her öneri satırında kalp düğmesi
- Son çalınanlar son 20 kayıt (motor içi)
- Hepsi `MusicStateStore` ile kalıcı

### 5. Uzun bas → ses detay kartı 🔍

Bir sese uzun basınca açılan tam sayfa:

- **Başlık + kategori** (emoji ile)
- **Açıklama:** sesin ne olduğu, ne yaptığı
- **İDEAL ANLAR:** ne zaman işe yarar (3 madde, sese özel)
- **BUNUNLA İYİ GİDER:** ruh hâli yakınlığına göre 2 öneri
- **Atıf:** uzak seslerde lisans bilgisi
- **Karışıma ekle** düğmesi

Haptik geri bildirim (`HapticFeedbackType.LongPress`) ile birlikte.

Ses meta verisi (`SoundMeta`) türetilmiş: katalogda yalnızca teknik bilgi
var, "ne zaman işe yarar" bilgisi burada family + başlık eşleşmesinden
üretiliyor (yağmur, orman, ateş, piyano, enstrümantal, diğer).

### 6. Binaural beats 🧠

İki kulağa farklı frekans → beyin **farkı** duyar (vuruş).

| Bant | Vuruş | Etki |
|---|---|---|
| Delta | 2,5 Hz | derin uyku |
| Theta | 6 Hz | uykuya geçiş |
| Alfa | 10 Hz | gevşeme |
| Beta | 18 Hz | odaklanma |
| Gama | 40 Hz | yoğun dikkat |

- Taban frekans 200 Hz, seviye düşük tutuldu (0,12) — bu bir arka plan aracı
- **3 saniyede yumuşak giriş** — aniden başlamaz
- **Kulaklık gerekir:** hoparlörde iki kanal havada karışır, vuruş kaybolur.
  Panel bunu etikette belirtiyor: `"2,5 Hz — derin uyku (kulaklık)"`

### 7. Yumuşak limiter 🎚️

Altı kanal aynı anda çalarken toplam genlik 1.0'ı aşabilir. Sert kırpma
(`coerceIn(-1f, 1f)`) duyulabilir bozulma ("cızırtı") üretir.

```
|v| < 0,7  → doğrusal (sadakat, hiç dokunma)
|v| ≥ 0,7  → hiperbolik yumuşatma (tanh), asimptotik 1,0'a yaklaşır
```

Sonuç: hiçbir örnek 1,0'ı geçmez ama tepe noktaları **kesilmez** — taşma
yerine sıkışır.

### 8. Cihaz sesi uyarısı + haptik 🔊

Karışım sekmesinde ana ses kaydırıcısının altında:

- Cihaz medya sesi %35'in altındaysa uyarı: `"Cihaz sesi %18"` + **"Yükselt"** düğmesi
- Yükselt → cihaz sesi %85'e çekilir (sistem ses çubuğu görünür)

Uygulama içi ses seviyesi cihazın sesini aşamaz. "Sonuna kadar açtım ama
ses az" şikâyetinin sebebi buradadır.

---

## Panel yapısı (7 sekme)

| # | Sekme | İçerik |
|---|---|---|
| 1 | **Senin İçin** | Tür bazlı öneri · favoriler · son çalınanlar |
| 2 | **Ambiyans** | 24 doğa/ortam sesi (binaural dahil) |
| 3 | **Müzik** | Enstrümantal (piyano, kutu, koto, pad…) |
| 4 | **Çevrimiçi** | 5 uzak kaynak araması + kaynak durum noktaları |
| 5 | **Karışım** | 6 kanal · ana ses · cihaz sesi · hızlı süre · tümünü durdur |
| 6 | **Profil** | Kaydet · yükle · sil · dışa/içe aktar |
| 7 | **Süre** | Uyku zamanlayıcısı (tam ekran sayaç) |

Sekme etiketi canlı bilgi taşır: `Karışım (3)`, `⏱ 23:41`.

---

## Kalıcı durum

`MusicStateStore` — ayrı DataStore (`kitsugi_music_state`):

| Anahtar | İçerik |
|---|---|
| `favorites` | JSON dizi (ses kimlikleri) |
| `recent` | JSON dizi (en yeni başta, 20 sınır) |
| `profiles` | JSON dizi (ad, tarih, kanallar) |

Neden ayrı depo: `AppSettings` zaten devasa; JSON blob'ları oraya
karıştırmak o sınıfı kirletirdi.

---

## Doğrulanmamış noktalar (derleme gerekli)

Android SDK bu ortamda yok. Şunlar **grep ile doğrulandı** ama derlenmedi:

| Sembol | Durum |
|---|---|
| `ProceduralSoundPlayer.fadeOutAndStop(Long)` | ✅ var |
| `ProceduralSoundPlayer(context, soundId)` | ✅ var |
| `AtmospherePresets.resolveLayers/byId` | ✅ var |
| `ProceduralSoundCatalog.sounds/byId` | ✅ var |
| `KitsugiColors.AccentRed/TextMuted/Border` | ✅ var |
| `LocalKitsugiAccent` | ✅ var |
| `OutlinedTextFieldDefaults` | ✅ var |
| `Icons.Rounded.Search/Share/ContentPaste/DeleteOutline/Add` | ✅ (extended) |
| `LinearProgressIndicator(progress = {})` | ✅ var |
| `HapticFeedbackType.LongPress` | ✅ var |
| `settingsDataStore` uzantısı | ✅ var |
| `POST_NOTIFICATIONS` izin akışı | ✅ var (MainActivity:285) |
| `setHandleAudioBecomingNoisy` | ❌ **kaldırıldı** (mevcut değil) |

Tüm dosyalarda parantez/küme dengesi doğrulandı: `{}` = 0, `()` = 0.

---

## Sıradaki adım

`./gradlew :app:compileDebugKotlin` çıktısı gerekiyor. Hata çıkarsa
satır numarasıyla birlikte iletin.
