# 🎵 KİTSUGİ — OKUYUCU MÜZİK & AMBİYANS SİSTEMİ
## Kapsamlı Araştırma ve Uygulama Planı

**Tarih:** 2026-11-06
**Durum:** Araştırma tamamlandı — karar bekliyor
**Kapsam:** Manga okuyucuya melodi butonu + açılır panel + içeriğe göre otomatik öneri yapan geniş ses kütüphanesi
**İlgili dosyalar:** `MangaReaderScreen.kt`, `MangaReaderViewModel.kt`, `KitsugiSheetOrDialog.kt`

---

## 1. YÖNETİCİ ÖZETİ (TL;DR)

İstediğin özellik **teknik olarak tamamen yapılabilir** ve ücretsiz/açık kaynak kaynaklarla **sıfır lisans riskiyle** kurulabilir — ama "tek bir API bağla, sonsuz müzik gelsin" yaklaşımı **çalışmaz**. Sebebi raporda detaylı; özet:

| Bulgu | Sonuç |
|---|---|
| Ücretsiz müzik API'lerinin **hepsi** ticari kullanımda kısıtlı veya ücretli | Ya ücretsiz kalacaksın ya lisans ödeyeceksin |
| **Pixabay'ın API'sinde ses endpoint'i YOK** (sadece görsel/video) | "Ücretsiz dev kütüphane" hayali burada bitiyor |
| **Free Music Archive API 2014'te kapatıldı** | Ölü seçenek |
| Jamendo API ücretsiz ama **"offline/cache amaçlı uygulama" yasak** | Çevrimdışı mod = sözleşme ihlali |
| Freesound API ücretsiz ama **sadece ticari olmayan** kullanım | İleride premium eklersen yeniden lisans gerekir |
| **Openverse API anahtarsız, CC0 filtreli, `attribution` alanı hazır geliyor** | En temiz "canlı katalog" seçeneği |
| **Doğa sesleri prosedürel olarak üretilebilir** (yağmur, ateş, rüzgar, dalga) | Lisans riski **sıfır**, boyut **~0 MB**, sınırsız çeşitlilik |

### 🏆 Önerim: 3 Katmanlı Hibrit Mimari

```
KATMAN 0 — Kitsugi Sound Engine (prosedürel, cihaz-içi, lisans riski yok)   ← OMURGA
             yağmur / ateş / rüzgar / dalga / kuş / kahve / gürültü + üretken melodi
                    ↓
KATMAN 1 — Paketlenmiş CC0 Ambiyans Paketi (~12–18 MB, çevrimdışı çalışır)   ← GARANTİ
             30–40 adet yüksek kalite döngü, APK içinde
                    ↓
KATMAN 2 — Canlı Katalog (Jamendo + Openverse + Internet Archive)          ← GENİŞLİK
             binlerce parça, akış (streaming), içeriğe göre otomatik öneri
```

Bu mimarinin kritik avantajı: **internet yoksa da çalışır, API kotası bitse de çalışır, lisans değişse de çalışır.** Üstteki iki katman senin malın, alttaki katman sadece "bonus genişlik".

### 🎯 Fark yaratan öneri
**"Atmosfer Preset"lerı** — sadece şarkı listesi değil, *sahne* listesi:

> **"Romantik Yağmur"** = Yağmur (%70) + Piyano (%35) + hafif Şömine (%15)
> **"Shounen Antrenman"** = Taiko (%50) + Şehir gece (%25) + Epic strings (%60)
> **"Korku Gecesi"** = Dark drone (%55) + Fırtına (%40) + Kalp atışı (%10)

Manga türüne göre bu presetler otomatik önerilir. Kullanıcı 2 kanallı karıştırıcıyla (Müzik % ↔ Ambiyans %) kendi karışımını yapar. **Bu özellik rakiplerin hiçbirinde yok** — Tachiyomi/Mihon/Aniyomi ekosisteminde ambiyans özelliği bulunmuyor.

---

## 2. İSTEĞİN TEKNİK ÇÖZÜMLEMESİ

Ekran görüntüsündeki alt çubukta **5 buton** var:

```
[okuma yönü]  [döndür]  [sığdır]  [ayarlar]
                                     ↑ buraya MÜZİK butonu
```

Öneri: mevcut 4 butona **5.** olarak `Icons.Rounded.MusicNote` eklenir (veya `GraphicEq` / `SpatialAudio`). Yeni buton `showMusicPanel = true` state'ini açar → `KitsugiSheetOrDialog` içinde müzik paneli.

**Tespit edilen mevcut durum (kod taraması):**

| Bileşen | Durum |
|---|---|
| `MangaReaderScreen.kt` | ✅ 696 satır, alt bar `Row` içinde `SpaceEvenly` — 5. buton sığar |
| `KitsugiSheetOrDialog` | ✅ TV/mobil uyumlu, focus yönetimi hazır — yeniden kullanılabilir |
| `MangaDetails.genre: List<String>` | ✅ **Öneri motorunun yakıtı burada** (tür listesi zaten var) |
| `media3-exoplayer` + `media3-session 1.8.0` | ✅ Ses çalma ve arka plan bildirimi altyapısı HAZIR |
| `OkHttp 4.12` + `kotlinx-serialization` | ✅ API katmanı için yeni bağımlılık GEREKMEZ |
| DataStore | ✅ Ayar/tercih saklama mevcut |
| Mevcut müzik/ambiyans kodu | ❌ **Yok** — sıfırdan yazılacak (temiz sayfa, çakışma riski yok) |
| `minSdk 26 / targetSdk 36` | ⚠️ Android 14+ için `FOREGROUND_SERVICE_MEDIA_PLAYBACK` izni şart |

---

## 3. SES KAYNAĞI ARAŞTIRMASI (Detaylı)

### 3.1 Karşılaştırma Tablosu — Müzik API'leri

| Kaynak | API Anahtarı | Ücretsiz Limit | Ticari Kullanım | Ses URL'i Döner? | Kitsugi Uygunluğu |
|---|---|---|---|---|---|
| **Openverse** (WordPress) | ❌ Gerekmez | 20 istek/dk, 200 istek/gün (anonim) | ✅ CC0/BY filtrelenebilir | ✅ `cdn.freesound.org` MP3 | 🟢🟢🟢 **EN İYİ** |
| **Jamendo** | ✅ `client_id` (ücretsiz) | 35.000 istek/**ay** | ❌ Non-commercial | ✅ `audio` MP3 | 🟢🟢 **İYİ** (atıf şart) |
| **Internet Archive** | ❌ Gerekmez | Pratik sınır yok | ⚠️ Item'a göre değişir | ✅ Range destekli | 🟢🟢 **İYİ** |
| **Audius** | ⚠️ Anahtarsız da çalışır | 10 istek/sn, 500k/ay | ⚠️ Belirsiz | ✅ 320kbps | 🟡 **ORTA** (risk) |
| **Freesound** (direkt) | ✅ API key | 60/dk, 2.000/gün | ❌ **Sadece non-commercial** | ✅ Preview MP3 | 🟡 **DİKKAT** |
| **Pixabay** | ✅ API key | 100/60sn | ✅ Evet | ❌ **Ses endpoint'i YOK** | 🔴 **KULLANILAMAZ** |
| **Free Music Archive** | — | — | — | — | 🔴 **API ÖLÜ (2014)** |
| **ccMixter** | ❌ | Sınır yok | ⚠️ CC'ye göre | ✅ | 🟡 Niş |
| **Wikimedia Commons** | ❌ | Cömert | ✅ CC | ✅ OGG/MP3 | 🟡 Katalog küçük |
| **Last.fm / MusicBrainz** | ✅ | Cömert | ✅ | ❌ **Metadata only** | ⚪ Sadece etiket için |

### 3.2 API Detayları

#### 🟢 Openverse — Birincil öneri
```
GET https://api.openverse.org/v1/audio/?q=rain&license=cc0&page_size=20
→ 200 OK   (API key YOK, CORS açık)
Header: x-ratelimit-limit-anon_burst: 20/min
        x-ratelimit-limit-anon_sustained: 200/day
```
Canlı test yanıtı (doğrulandı):
```json
{
  "result_count": 240,
  "results": [{
    "title": "Rain Sound and Rainforest.mp3",
    "url": "https://cdn.freesound.org/previews/457/457447_9518146-hq.mp3",
    "creator": "INNORECORDS",
    "license": "cc0",
    "provider": "freesound",
    "duration": 240079,
    "attribution": "\"Rain Sound and Rainforest.mp3\" by INNORECORDS is marked with CC0 1.0…"
  }]
}
```
**Neden en iyi:**
- Anahtar yok → kullanıcıya "API key gir" demek yok
- `license=cc0` filtresi → **atıf bile gerekmez**
- `attribution` alanı **hazır hukuki metin** → ekranda göster, iş bitti
- Freesound, Jamendo, Wikimedia, ccMixter'ı **tek endpoint'te** birleştiriyor
- `category=music` / `source=freesound` / `duration` filtreleri mevcut

**Dezavantaj:** Anonim limit 200/gün. Çözüm → `LruCache` + 24 saat kalıcı önbellek + `length` filtresiyle sonuç sayısını azaltmak. Yeterli.

#### 🟢 Jamendo — Müzik kalitesi en yüksek
```
GET https://api.jamendo.com/v3.0/tracks/
    ?client_id=XXX&format=json&limit=20
    &fuzzytags=ambient+chill&vocalinstrumental=instrumental
    &speed=low+verylow&durationbetween=120_600&boost=popularity_month
```
**Özel parametreler (öneri motoru için altın):**
- `tags` (hepsi eşleşmeli) / `fuzzytags` (herhangi biri)
- `vocalinstrumental=instrumental` → **vokalsiz parça** (okurken sözler dikkat dağıtır!)
- `speed=verylow|low|medium|high|veryhigh`
- `acousticelectric=acoustic`
- `durationbetween=min_max`
- `boost=popularity_month|listenerstotal|releasedate`

> ⚠️ **Kritik lisans notu:** Jamendo API sözleşmesi şunları **zorunlu kılar:**
> 1. Sanatçı + Jamendo atfı
> 2. Her parça için kendi sayfasına geri bağlantı
> 3. **"Caching veya offline erişim amaçlı tasarlanmış uygulama" YASAK**
> → Yani Jamendo parçaları **yalnızca stream** edilebilir, indirilemez. Bu mimariye gömülü olmalı.

#### 🟢 Internet Archive — Ambiyans cenneti
```
GET https://archive.org/advancedsearch.php
    ?q=mediatype:(audio)+AND+licenseurl:*publicdomain*+AND+(rain+OR+forest)
    &fl[]=identifier&fl[]=title&fl[]=licenseurl
    &rows=50&output=json
→ https://archive.org/download/<identifier>/<dosya>.mp3   (HTTP Range destekler)
```
- Anahtar yok, kota pratikte yok
- `licenseurl:*publicdomain*` filtresi → temiz lisans
- **ABD Balık ve Yaban Hayatı Servisi** doğa kayıtları (kuş, kurt, kurbağa) public domain
- Dezavantaj: ses kalitesi tutarsız, metadata kirli → **önden küratörlük şart**

#### 🟡 Audius — Yedek plan
- Anahtarsız `?app_name=Kitsugi` ile çalışır, kayıtla 10 istek/sn
- 320 kbps, modern katalog
- **Risk:** Kullanıcı yüklemesi içerik; bazı parçalar telifli olabilir, platform sorumluluk almıyor

#### 🟡 Freesound (direkt kullanım) — TEMKİNLİ
> Resmî şartlar: *"Freesound API'sini **yalnızca ticari olmayan** amaçlarla ücretsiz kullanabilirsiniz."*
>
> Repoda `KitsugiPremiumSheet.kt` var → ticari niyet sinyali. **Freesound'a doğrudan bağlanma.** Onun yerine **Openverse üzerinden CC0 filtresiyle** aynı seslere ulaş (Openverse zaten Freesound CDN'inden servis ediyor ve CC0 olanları işaretliyor).

---

### 3.3 🔑 Gizli Kahraman: Prosedürel Ses Üretimi

Araştırmanın en değerli bulgusu bu. "Rahatlatıcı doğa sesleri"nin **tamamı** matematikle üretilebilir:

| Ses | DSP Tarifi | Gerçekçilik |
|---|---|---|
| Beyaz/Pembe/Kahverengi gürültü | IIR filtre + Voss-McCartney | ⭐⭐⭐⭐⭐ (birebir) |
| Yağmur | Band-pass gürültü (400–4000 Hz) + rastgele damla transient'ları | ⭐⭐⭐⭐⭐ |
| Şiddetli yağmur / fırtına | Üstteki + alçak frekans gürültü zarfı + ara sıra gök gürültüsü | ⭐⭐⭐⭐ |
| Rüzgar | Dar bantlı gürültü, merkez frekansı yavaş LFO ile gezinen | ⭐⭐⭐⭐ |
| Okyanus dalgası | Kahverengi gürültü × 0.1 Hz sinüs zarfı | ⭐⭐⭐⭐ |
| Şömine | Kahverengi gürültü + Poisson dağılımlı "çıtırtı" impulsleri | ⭐⭐⭐⭐ |
| Kuş cıvıltısı | FM chirp sentezi, rastgele zamanlama | ⭐⭐⭐ |
| Kafe uğultusu | Formant filtreli gürültü + Bardak sesleri | ⭐⭐⭐ |
| **Üretken melodi** | Pentatonik dizi + Markov zinciri arpej + reverb | ⭐⭐⭐⭐ |

**Kazanç:**
- **Lisans riski: SIFIR** (senin ürettiğin ses)
- **APK boyutu: ~0 MB** (kod ile üretiliyor, dosya yok)
- **Sınırsız çeşitlilik** — her açılışta farklı
- **Çevrimdışı: %100**
- **Kota: yok**
- **Fark yaratan özellik** — "Kitsugi Ambient Engine" markası

**Maliyet:** `AudioTrack` streaming modunda PCM üretimi. Yaklaşık **300–500 satır** DSP kodu. Zor değil, iyi tanımlı bir problem.

> 💡 **Strateji:** Katman 0'ı prosedürel yap → uygulamanın *her zaman çalışan* bir temeli olsun. Katman 1 ve 2 üstüne "süs".

---

## 4. 🧠 İÇERİĞE GÖRE ÖNERİ MOTORU

Senin asıl istediğin şey bu: *"okuduğumuz içeriğe bağlı olarak seçebileceğimiz şarkılar"*.

### 4.1 Duygu Vektörü (Mood Vector)

Her manga ve her ses için 5 boyutlu vektör:

```kotlin
data class MoodProfile(
    val energy:  Float,   // 0 = sakin        → 1 = hareketli
    val valence: Float,   // 0 = karanlık     → 1 = aydınlık
    val tension: Float,   // 0 = huzurlu      → 1 = gergin
    val warmth:  Float,   // 0 = soğuk/uzay   → 1 = sıcak/akustik
    val focus:   Float    // 0 = dikkat dağıtır → 1 = odak dostu (vokalsiz)
)
```

### 4.2 Tür → Duygu Eşleme Tablosu

`MangaDetails.genre` alanından otomatik okunur:

| Tür | energy | valence | tension | warmth | Otomatik öneri |
|---|---|---|---|---|---|
| Action / Shounen | 0.85 | 0.65 | 0.60 | 0.45 | Epic Orchestral, Taiko, Şehir Gecesi |
| Romance | 0.30 | 0.75 | 0.15 | 0.85 | **Piyano + Yağmur + Şömine** |
| Slice of Life | 0.25 | 0.80 | 0.10 | 0.90 | Lo-Fi, Kafe, Kuş Sesleri |
| Horror / Thriller | 0.35 | 0.12 | 0.95 | 0.10 | Dark Ambient, Drone, Fırtına, Kalp Atışı |
| Fantasy / Adventure | 0.60 | 0.70 | 0.35 | 0.65 | Kelt, Taverna, Orman, Rüzgar |
| Sci-Fi | 0.50 | 0.45 | 0.40 | 0.15 | Synth Pad, Uzay Drone |
| Mystery | 0.35 | 0.30 | 0.80 | 0.30 | Noir Caz, Yağmur, Saat Tıkırtısı |
| Comedy | 0.65 | 0.95 | 0.05 | 0.70 | Upbeat Akustik, Pizzicato |
| Sports | 0.95 | 0.70 | 0.55 | 0.40 | DnB Enstrümantal, Rock |
| Psychological | 0.25 | 0.20 | 0.85 | 0.15 | Minimal Piyano, Drone, Sessizlik |
| Isekai | 0.55 | 0.72 | 0.30 | 0.55 | Fantastik Ambiyans, Ortaçağ |

**Çoklu tür:** Vektörler ağırlıklı ortalama alınır (`genre` listesindeki sıra = ağırlık).
**Tür yoksa:** Nötr profil (0.4, 0.5, 0.3, 0.5, 0.8) → "Sakin Başlangıç" önerilir.
**Bölüm bazlı ince ayar (v2):** chapter adında `"final"`, `"savaş"`, `"wedding"` gibi anahtar kelimeler → türü geçersiz kılar.

### 4.3 Eşleştirme Algoritması

```
1. mood = genreToMood(mangaDetails.genre)          // türden hesapla
2. adaylar = offlineLibrary + apiKatalog           // her adayın da MoodProfile'i var
3. skor = cosineSimilarity(mood, aday.mood) * 0.7
        + popularityBoost * 0.2
        + userAffinity * 0.1                       // beğeni geçmişi (DataStore)
4. Çeşitlilik filtresi: aynı "aile"den en fazla 2 parça (MmrRerank)
5. Top 6 → panelde göster
```

Doğruluk tahmini: **kural tabanlı v1 ile %70–80 isabet.** LLM'e gerek yok. (İleride opsiyonel.)

### 4.4 Preset (Atmosfer) Sistemi

```kotlin
data class AtmospherePreset(
    val id: String,
    val name: String,                    // "Romantik Yağmur"
    val emoji: String,                   // "🌧️"
    val layers: List<SoundLayer>         // çok katmanlı karışım
)

data class SoundLayer(
    val soundId: String,                 // "rain_soft" | "piano_nocturne" | "fire"
    val volume: Float,                   // 0.0–1.0
    val fadeInMs: Long = 2000
)

val ROMANTIC_RAIN = AtmospherePreset(
    id = "romantic_rain", name = "Romantik Yağmur", emoji = "🌧️",
    layers = listOf(
        SoundLayer("rain_soft", 0.70f),
        SoundLayer("piano_ambient", 0.35f),
        SoundLayer("fire_crackle", 0.15f)
    )
)
```

**12 hazır preset önerisi:**

| Emoji | Preset | Katmanlar |
|---|---|---|
| 🌧️ | Romantik Yağmur | yağmur %70 + piyano %35 |
| ⚔️ | Antrenman | taiko %50 + şehir %25 + epic %60 |
| 👻 | Korku Gecesi | drone %55 + fırtına %40 + kalp %10 |
| ☕ | Kafe Sabahı | kafe %60 + lo-fi %40 + kuş %10 |
| 🌊 | Sahilde | dalga %75 + gitar %30 |
| 🌲 | Derin Orman | orman %70 + rüzgar %20 + kuş %15 |
| 🔥 | Şömine | ateş %80 + piyano %25 |
| 🌌 | Uzay | synth pad %60 + drone %30 |
| 🌃 | Gece Şehri | şehir %50 + lo-fi %50 + yağmur %25 |
| 📚 | Sınav Modu | pembe gürültü %45 + kahve %20 |
| 🎐 | Japonya | koto %40 + rüzgar çanı %25 + yağmur %30 |
| 💤 | Uyku | kahverengi gürültü %60 + yağmur %20 |

---

## 5. 🏗️ ÖNERİLEN MİMARİ

### 5.1 Modül Yapısı

```
data/music/
├── SoundRegistry.kt              // Tüm seslerin metadata (id, mood, süre, lisans, kaynak)
├── AtmospherePresets.kt          // 12 hazır preset
├── MoodProfile.kt                // Duygu vektörü + cosine similarity
├── GenreMoodMapper.kt            // MangaDetails.genre → MoodProfile
├── RecommendationEngine.kt       // Skorlama + MMR çeşitlilik
├── LocalSoundLibrary.kt          // Katman 1: paketlenmiş CC0 sesler
└── remote/
    ├── OpenverseAudioApi.kt      // Anahtar yok
    ├── JamendoAudioApi.kt        // client_id
    ├── ArchiveAudioApi.kt        // Anahtar yok
    └── AudioSearchCache.kt       // 24s LRU + disk cache

core/music/
├── AmbientAudioEngine.kt         // Çift ExoPlayer + mix + fade + loop
├── ProceduralSoundEngine.kt      // Katman 0: AudioTrack DSP
├── NoiseGenerator.kt             // Beyaz/pembe/kahverengi
├── NatureSynth.kt                // Yağmur/ateş/rüzgar/dalga
├── GenerativeMelody.kt           // Markov zinciri + pentatonik
├── AmbientMediaSession.kt        // media3-session + bildirim
└── AmbientAudioService.kt        // Foreground service

ui/screens/manga/components/
├── MangaAmbientButton.kt         // Alt bardaki 🎵 buton
├── MangaAmbientSheet.kt          // Açılır panel
├── AtmosphereCard.kt             // Preset kartı
├── SoundMixerSlider.kt           // 2 kanallı karıştırıcı
└── AmbientMiniPlayer.kt          // Okuyucu üstünde ince durum çubuğu
```

### 5.2 Çalma Akışı

```
Kullanıcı 🎵'ye basar
        ↓
MangaAmbientSheet açılır (KitsugiSheetOrDialog)
        ↓
RecommendationEngine.recommend(mangaDetails.genre)  ← türü okur, mood hesaplar
        ↓
"Senin İçin" sekmesi: 6 öneri + 12 preset
        ↓
Kullanıcı preset seçer → AmbientAudioEngine.play(preset)
        ↓
    ┌───────────────┴───────────────┐
    ↓                               ↓
KATMAN 0/1 (offline)          KATMAN 2 (online)
ExoPlayer #1: Müzik           Ağ isteği → Openverse/Jamendo
ExoPlayer #2: Ambiyans        → stream URL → ExoPlayer
    ↓                               ↓
    └────────────┬──────────────────┘
                 ↓
        MixerAudioProcessor (setVolume ile mix)
                 ↓
        AmbientAudioService (foreground)
                 ↓
        Medya bildirimi: ⏸ ⏭  "Romantik Yağmur"
```

### 5.3 Teknik Detaylar (kritik noktalar)

| Konu | Çözüm | Not |
|---|---|---|
| **Gapless loop** | `LoopingMediaSource` (aynı kaynak için gapless) | `setRepeatMode(ONE)` küçük boşluk bırakır |
| **Crossfade** | İki player arasında 800ms `volume` animasyonu | Tık sesi olmaz |
| **Mix (2 kanal)** | İki ayrı `ExoPlayer` + `setVolume()` | `MixingAudioProcessor`'dan basit ve güvenli |
| **Fade in/out** | Coroutine + `animate` ile `player.volume` | 0→hedef, 1500ms |
| **Audio Focus** | `AUDIOFOCUS_GAIN`, `setWillPauseWhenDucked(false)` | Telefon gelince duraklat |
| **Arka plan** | `media3-session` **zaten bağımlılıkta** | Sıfır ek kütüphane |
| **Android 14+** | `FOREGROUND_SERVICE_MEDIA_PLAYBACK` + `POST_NOTIFICATIONS` | Manifest + runtime izin |
| **Ekran kapanınca** | Foreground service devam eder | Pil: ExoPlayer yerel dosyada çok hafif |
| **Prosedürel ses** | `AudioTrack` STREAM mode, 48kHz float | Düşük gecikme |
| **Kulaklık çıkarılınca** | `ACTION_AUDIO_BECOMING_NOISY` → duraklat | Zorunlu UX |
| **Okuyucudan çıkış** | Ayar: "Devam et" / "Durdur" | DataStore |

### 5.4 Panel Tasarımı (Wireframe)

```
┌─────────────────────────────────────────────┐
│  🎵  Ortam Sesi                        ✕    │
│  Şu an: Romantik Yağmur  ·  ▶ 02:14         │
├─────────────────────────────────────────────┤
│  [ Senin İçin ]  [ Ambiyans ]  [ Müzik ]  [Karışım] │
├─────────────────────────────────────────────┤
│  💡 Romance türüne göre önerildi            │
│                                             │
│  ┌────────┐ ┌────────┐ ┌────────┐          │
│  │  🌧️    │ │  🔥    │ │  ☕    │          │
│  │Romantik│ │ Şömine │ │Kafe    │          │
│  │ Yağmur │ │        │ │Sabahı  │          │
│  └────────┘ └────────┘ └────────┘          │
│  ┌────────┐ ┌────────┐ ┌────────┐          │
│  │  🎹    │ │  🌲    │ │  🌊    │          │
│  │ Piyano │ │ Orman  │ │ Sahil  │          │
│  └────────┘ └────────┘ └────────┘          │
├─────────────────────────────────────────────┤
│  KARIŞIM                                    │
│  Müzik    ▁▁▁▁▁▁▁●▁▁▁▁▁▁▁  35%             │
│  Ambiyans ▁▁▁▁▁▁▁▁▁▁▁▁●▁▁  70%             │
│  Ana Ses   ▁▁▁▁▁▁▁▁▁▁▁●▁▁▁  80%             │
├─────────────────────────────────────────────┤
│  ⏱️ Zamanlayıcı: 30dk ▾   🎧 Otomatik öneri ✓│
│  Kaynak: Openverse CC0 · Jamendo            │
└─────────────────────────────────────────────┘
```

---

## 6. ⚖️ LİSANS & HUKUKİ RİSK TABLOSU

| Kaynak | Risk | Önlem |
|---|---|---|
| **Openverse CC0** | 🟢 Yok | `attribution` alanını panelde göster (CC0'da zorunlu değil ama şeffaflık) |
| **Openverse CC-BY** | 🟡 Düşük | `attribution` metnini **aynen** göster + kaynak linki |
| **Jamendo** | 🟡 Orta | Sanatçı + Jamendo atfı, parça linki, **offline cache YASAK**, ticari olursa lisans al |
| **Freesound direkt** | 🟠 Yüksek | `KitsugiPremiumSheet` var → ticari sayılır. **Bağlanma**, Openverse üzerinden git |
| **Audius** | 🟠 Yüksek | Kullanıcı yüklemesi, telifli içerik riski. Ana katalog yapma, yedek tut |
| **Internet Archive** | 🟡 Orta | `licenseurl:*publicdomain*` filtresi şart, item bazlı kontrol |
| **Prosedürel (Katman 0)** | 🟢 **Sıfır** | Senin eserin |
| **Paketlenmiş CC0 (Katman 1)** | 🟢 Sıfır | Kaynak + lisans dosyasını `assets/licenses/` altında sakla |

> ⚠️ **Genel kural:** "Royalty-free" ≠ "telifsiz". Her parçanın lisansı **ayrı ayrı** okunmalı. Jamendo'da bile parça bazında CC BY / BY-NC / BY-SA farklı.

---

## 7. 📅 FAZ PLANI & Efor Tahmini

| Faz | İçerik | Süre | Bağımlılık | Risk |
|---|---|---|---|---|
| **Faz 0** | Araştırma & karar | ✅ Bitti | — | — |
| **Faz 1** 🎯 | Buton + boş panel + `AmbientAudioEngine` (tek player, fade, loop) + 8 hazır offline ses | **2–3 gün** | — | Düşük |
| **Faz 2** | Çift kanal karıştırıcı + 12 preset + zamanlayıcı + foreground service + bildirim | **3–4 gün** | Faz 1 | Orta (izinler) |
| **Faz 3** | Öneri motoru v1 (tür → mood → skor) + "Senin İçin" sekmesi | **1–2 gün** | Faz 1 | Düşük |
| **Faz 4** | Canlı katalog: Openverse + Jamendo + Internet Archive + cache + atıf UI | **4–5 gün** | Faz 3 | Orta (ağ/lisans) |
| **Faz 5** | Prosedürel motor: gürültü + yağmur/ateş/rüzgar/dalga + üretken melodi | **5–7 gün** | Faz 1 | **Yüksek** (DSP) |
| **Faz 6** | Kişiselleştirme: beğeni, geçmiş, ayarlar, TV desteği, TR/EN string | **2–3 gün** | Faz 1–4 | Düşük |

**Toplam MVP (Faz 1+2+3): ~6–9 gün** → *kullanılabilir, çevrimdışı çalışan, içeriğe göre öneren* sistem.
**Tam sürüm (Faz 1–5): ~17–24 gün.**

> 🎯 **Önerim:** Faz 1→2→3 ile başla. Faz 4 ve 5'i ayrı sürümlere bırak. Faz 1'i **bugün başlatabiliriz.**

---

## 8. 🎁 BONUS: Ek Fikirler

| Fikir | Değer |
|---|---|
| **Zamanlayıcı (uyku modu)** | Gece okuyanlar için 15/30/60dk sonra fade-out |
| **Sahne algılama (v3)** | Sayfa parlaklık analizi → karanlık sayfa = karanlık ses |
| **Kulaklık önerisi** | Ses açıldığında "kulaklık takınca daha iyi" ipucu |
| **Sesli geri bildirim** | Sayfa geçişinde çok hafif kağıt hışırtısı (opsiyonel, kapalı varsayılan) |
| **Bölüm sonu** | Bölüm bitince müzik yumuşakça yükselir ("outro") |
| **Anime oynatıcıya da aynı motor** | Kod yeniden kullanımı %100 — sadece okuyucuya özel değil |
| **TV/Android TV desteği** | `KitsugiSheetOrDialog` zaten TV uyumlu → ekstra iş yok |
| **Paylaşılabilir preset** | "Kendi atmosferimi paylaş" → derin link |

---

## 9. ❓ KARAR NOKTALARI (Senin seçmen gerekenler)

| # | Soru | Seçenekler |
|---|---|---|
| **1** | **Kapsam** | (A) MVP: sadece offline + preset · (B) MVP + canlı katalog · (C) Tam: + prosedürel motor |
| **2** | **Ticari mi?** | (A) Tamamen ücretsiz kalacak → Freesound dahil her şey açık · (B) Premium plan var/olacak → Freesound'u çıkar, Jamendo lisansı gerekir |
| **3** | **Arayüz** | (A) Aynı sheet içinde sekmeler · (B) Ayrı tam ekran · (C) Sheet + mini player |
| **4** | **Başlangıç** | Bugün Faz 1'e başlayayım mı? |

---

## 10. 📚 KAYNAKÇA (doğrulanmış, 2026-11-06)

- [Openverse API — canlı test yanıtı, limit header'ları](https://api.openverse.org/v1/audio/?q=rain&license=cc0&page_size=2) — anonim 20/dk, 200/gün
- [Freesound APIv2 — Genel Bakış & Throttling](https://freesound.org/docs/api/overview.html) — 60/dk, 2.000/gün
- [Freesound API — Kullanım Şartları](https://freesound.org/docs/api/terms_of_use.html) — **"yalnızca ticari olmayan amaçlarla ücretsiz"**
- [Freesound API — Kimlik Doğrulama (PKCE zorunluluğu)](https://freesound.org/docs/api/authentication.html)
- [Jamendo API — tracks method parametreleri](https://developer.jamendo.com/v3.0/tracks) — `vocalinstrumental`, `speed`, `durationbetween`, `boost`
- [Jamendo API Lisans Şartları](https://help-licensing.jamendo.com/hc/en-us/articles/20699346005661-Jamendo-API) — 35.000 istek/ay, non-commercial
- [Pixabay API dokümantasyonu](https://pixabay.com/api/docs/) — **ses endpoint'i yok**, robot/otomatik toplama yasak
- [Free Music Archive API — Deactivated](https://www.programmableweb.com/api/free-music-archive)
- [Internet Archive advancedsearch + licenseurl filtresi](https://archive.org/advancedsearch.php) — public domain filtresi

---

*Rapor sonu. Kararları verdiğinde kodlamaya geçebiliriz.*
