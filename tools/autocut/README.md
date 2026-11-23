# KITSUGI AUTOCUT v1.0

**Windows 10 için, çift tıklayınca çalışan, tamamen yerel otomatik video editörü.**

Klasörü izler → kayıtları analiz eder → en iyi anları bulur → keser → **o ana uygun memeyi** koyar → **telifsiz müziği** beat'e göre ekler → altyazı/efekt uygular → isterse **YouTube'a yükler**.

Bulut yok. Kredi yok. Filigran yok. Limit yok. Hiçbir veri dışarı gitmiyor.

---

## Kutudan çıkar çıkmaz gelenler

Program ilk açılışta **boş değil**, hazır paketle gelir:

| Ne | Kaç | Nerede |
|---|---|---|
| Meme resmi (ace, clutch, hype, whiff, fail, scary) | 6 | `data/memes/` |
| Telifsiz müzik (epic 120, hype 150, scary 200 BPM) | 3 | `data/music/` |
| Yazı meme fallback'i (resim hiç olmasa da çalışır) | 16 | kodun içinde |

Yani hiçbir şey eklemeden **BASLAT**'a basınca meme + müzikli video üretir.
Kendi meme'lerini ve müziklerini sonra üstüne eklersin.

---

## Ne yapıyor?

| Özellik | Detay |
|---|---|
| 📁 **Klasör izleme** | OBS replay klasörünü izler, yeni kayıt düşünce otomatik işler |
| 🎯 **Otomatik kurgu** | Her kaydın ses analizini yapar, **aksiyon anlarını** bulur, en iyilerini seçer |
| ✂️ **Uzun kayıttan çok bölüm** | 30–40 dakikalık kaydın sadece ilk 20 saniyesini değil, **en hareketli 3–6 bölümünü** alır |
| 🧠 **Uygun yere uygun meme** | Bölümün türüne bakar (ace / clutch / whiff / hype / scary) ve **o türe ait** memeyi tam tepe anında gösterir |
| 🏚️ **Korku tanıma** | Klasör/dosya adında `backrooms`, `horror`, `korku` geçiyorsa **scary** meme + **scary** müzik seçer |
| 🔤 **Otomatik yazı** | "ACE!", "1v4", "KAÇTI!", "KOŞ!" callout'ları tepe anında ekrana basılır |
| 🎵 **Telifsiz müzik** | Sadece güvenli lisanslı parçalar; beat'leri analiz eder, **beat'e göre flash**, ses ducking |
| ✨ **Efekt** | Tepe anında zoom-punch + shake + beyaz flaş |
| 📝 **Altyazı** | faster-whisper ile yerel SRT (opsiyonel) |
| 📤 **Otomatik yükleme** | YouTube Data API v3, private/unlisted/public, zamanlama |
| 🖥️ **Arayüz** | Tkinter, karanlık tema, log, ilerleme çubuğu, müzik seçim kutusu |

---

## Kurulum (tek sefer, ~2 dakika)

1. **Python 3.10+** → [python.org](https://python.org) → kurulumda **"Add python.exe to PATH"** işaretle
2. **`baslat.bat`** dosyasına çift tıkla

`baslat.bat` ilk açılışta kendisi `venv` oluşturur ve gerekli paketleri kurar.
FFmpeg'i ayrıca kurmana gerek yok: `imageio-ffmpeg` paketi kendi binary'sini getirir.

> İstersen sistem FFmpeg'i: `winget install Gyan.FFmpeg`

### İsteğe bağlı
```bat
pip install -r requirements-youtube.txt     :: YouTube otomatik yükleme
pip install -r requirements-whisper.txt     :: otomatik altyazı
```

---

## Kullanım (3 adım)

1. **Kaynak klasör** → OBS replay'lerinin olduğu klasör (örn. `C:\YouTube\Kayit`)
2. **Çıkış klasör** → videoların yazılacağı yer (örn. `C:\YouTube\Cikti`)
3. **▶ BASLAT**

Çıktı: `Cikti\autocut_20261123_184500.mp4` — 9:16 (Shorts) veya 16:9.

### Test
**"TEST (10 sn)"** → 2 kliple 10 saniyelik örnek üretir, sonucu hemen görürsün.

### Otomatik mod
**"Klasörü izle"** kutusunu işaretle → arka planda bekler, OBS'e yeni replay düşünce **tek kliplik Shorts** üretir.

---

## Meme kütüphanesi

`data/memes/` içine resim at, yanına **aynı isimle** `.json` koy:

```
data/memes/
├── ace_01.jpg
├── ace_01.json     -> {"tags": ["ace"], "text": "ACE", "callout": "ACE!"}
```

| Etiket | Ne zaman seçilir |
|---|---|
| `ace` | 4+ tepe noktası olan bölüm |
| `clutch` | 3 tepe noktası |
| `hype` | 2 tepe noktası / genel |
| `whiff` | tepe noktası bölümün sonuna yakın |
| `scary` | klasör adında backrooms/horror/korku geçiyor |
| `fail`, `chill`, `troll` | elle etiketleme için |

- **Json koymazsan** dosya adından etiket çıkar (`ace_01.jpg` → `ace`)
- **Hiç resmin yoksa** program yazı meme'lerini kullanır (ACE, 1v4, KAÇTI, KOŞ…)
- Resimli meme varsa **resimli olan kazanır**, yazı kartı yedektir

---

## Telifsiz müzik

`data/music/` içine mp3/wav at, yanına `.json`:

```json
{
  "title": "Epic Ambient",
  "artist": "X",
  "license": "youtube-audio-library",
  "source": "https://studio.youtube.com/audio-library",
  "bpm": 128,
  "mood": "epic"
}
```

**Güvenli lisanslar:** `youtube-audio-library`, `pixabay`, `mixkit`, `cc0`, `original`
Bilinmeyen lisanslı parça **hiçbir zaman seçilmez** — claim yeme riski sıfır.

`mood` alanı: `epic`, `hype`, `scary`, `chill`. Program kurgunun türüne göre **ruhu uyan** parçayı seçer (Backrooms kaydı → scary beat).

> NCS gibi "no copyright" parçalar Content ID claim eder, reklam geliri NCS'e gider.
> En garantisi **YouTube Studio → Audio Library** ("Attribution not required" filtresi).

---

## YouTube bağlantısı

1. [console.cloud.google.com](https://console.cloud.google.com) → proje oluştur
2. **APIs & Services → Library → YouTube Data API v3 → Enable**
3. **OAuth consent screen** → External → Testing → kendi Gmail'ini test user ekle
4. **Credentials → Create Credentials → OAuth client ID → Desktop app** → `client_secret.json` indir, bu klasöre koy
5. `pip install -r requirements-youtube.txt`
6. Programda **YouTube** sekmesi → **"Baglan / Dogrula"** → tarayıcıdan izin ver

> **7 gün tuzaığı:** OAuth ekranı Testing'de kalırsa refresh token 7 günde düşer ve
> `invalid_grant` hatası verir. Sürekli otomasyon için Google Cloud'da
> **Publish app → In production** yap. Kendi hesabın için doğrulama zorunlu değil;
> "unverified app" uyarısından **Advanced → continue** ile geçilir.

Yükleme: **private** (önerilen) / unlisted / public. İstersen ISO 8601 ile zamanla.

---

## Performans (tek geçişli render)

Kurgu **tek FFmpeg komutunda** yapılır: ölçekle → birleştir → efekt → meme → müzik → encode.
Ara dosya yok, iki kez kodlama yok.

| Donanım | 45 sn Shorts (1080x1920) |
|---|---|
| Sadece CPU (libx264 veryfast) | ~15–40 sn |
| NVIDIA GPU (NVENC, otomatik seçilir) | ~5–15 sn |
| AMD GPU (AMF) | ~10–25 sn |

Program donanım kodlayıcıyı **gerçekten deneyerek** seçer (`pick_encoder`), çalışmazsa libx264'e döner.

---

## Dosyalar

| Dosya | İş |
|---|---|
| `app.py` | Masaüstü arayüz (Tkinter) + CLI + klasör izleme |
| `engine.py` | Kurgu motoru: analiz, bölüm seçimi, meme yerleşimi, render |
| `memes.py` | Meme kütüphanesi, etiket eşleme |
| `music.py` | Müzik kütüphanesi, lisans filtresi, ruh seçimi |
| `metadata.py` | Başlık/açıklama/etiket (Ollama veya şablon) |
| `uploader.py` | YouTube Data API v3 yükleme |
| `core.py` | FFmpeg bulma, komut çalıştırma, encoder seçimi |
| `config.json` | Ayarlar (arayüzden otomatik yazılır) |
| `baslat.bat` | Tek tıkla başlat |

### Komut satırı (GUI'siz)
```bat
python app.py --headless --source "C:\YouTube\Kayit" --out "C:\YouTube\Cikti\a.mp4"
python app.py --headless --file   "C:\YouTube\Kayit\klip.mp4" --out "C:\YouTube\Cikti\b.mp4"
```

---

## Sorun giderme

| Sorun | Çözüm |
|---|---|
| `Python bulunamadi` | Python'u PATH'e ekleyerek yeniden kur |
| `ffmpeg bulunamadi` | `pip install imageio-ffmpeg` veya `winget install Gyan.FFmpeg` |
| `No module named tkinter` | Python'u yeniden kur, `tcl/tk` seçeneğini işaretle |
| Video üretilmiyor | Kaynak klasörde en az 2 sn'lik video var mı bak |
| Müzik çalmıyor | `.json` içindeki `license` güvenli listede mi kontrol et |
| Meme görünmüyor | Log'da "X meme" satırına bak; `data/memes/` boşsa yazı kartı kullanılır |
| `invalid_grant` (YouTube) | OAuth Testing'de kalmış → Publish app, `token.json` sil, tekrar bağlan |
| Render yavaş | NVIDIA/AMD varsa NVENC/AMF otomatik kullanılır |

---

## Lisans / uyarı

- Program **kendi kaydettiğin** oyun görüntüleri için. Başkasının videosunu izinsiz kullanma.
- Riot (Valorant) ve Valve (CS2) kendi oynadığın içeriğin reklam gelirini serbest bırakıyor; başkasının VOD'unu yeniden yayınlamak yasak.
- Backrooms: Kane Pixels'in videosunu/filmini kullanma; kendi görüntünü üret ("Backrooms" adı markalı, konsept kamu malı).
- YouTube "inauthentic content" politikası: aynı şablonu 50 kez basan kanal para kazanamaz. Her videoya özgün bir dokunuş ekle.
- `data/music/` içindeki 3 parça **bu program tarafından sentezlenmiştir**, üçüncü taraf hakkı yoktur (`license: original`).
