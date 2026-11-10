# PLAN / TASK — Ortam Sesi: akış sağlamlığı, format kapsamı ve otomatik başlatma

**Tarih:** 2026-11-07
**Dal:** `arena/54c8e317-kitsugi-beta` (PR #38 → `feat/golge-bahcesi-integration`)
**Sürüm:** v2.4.457 · **Derleme:** ✅ BUILD SUCCESSFUL (debug + R8 release, DEX VerifyError = 0)

**Şikâyet (kullanıcı):**
1. Okuyucudaki **Ortam Sesi** sekmesinde bir sesi çalınca sekmedeki tüm ses bozuluyor / bir daha ses çıkmıyordu.
2. **Çevrimiçi** kaynaklardan (Openverse / Audius / Internet Archive) çalan ses ya hiç başlamıyor ya da uygulamayı donduruyordu.
3. Panel "Tür/açıklama tanımadı — dengeli başlangıç" diyordu; eşleşme tür **ve** etiketten, 7 kaynağın tamamı kullanılarak yapılmalıydı.
4. (Bu tur) Sekmenin **tamamı** sorunsuz çalışmalı; **hangi formatta olursa olsun** ses çalmalı, donma/takılma olmamalı; **otomatik başlat** açıkken okunan içeriğe uyan ses — **çevrimiçi kaynaklar dahil** — seçilmeli.

---

## 1. Kök nedenler (KN)

| # | Kök neden | Belirti |
|---|---|---|
| KN-1 | `AmbientAudioEngine.stopAllChannels()` → `AmbientPlaybackService` `ACTION_STOP` → servis `onStartCommand` içinde tekrar `stopAllChannels()` → sonsuz ping-pong; servis her turda yeniden `startForeground` | Sekme bir kez kullanıldıktan sonra ses tamamen kesiliyor, UI kilitleniyor |
| KN-2 | `ProceduralSoundPlayer`, `AudioTrack.write()` hatasında döngüde dönüp %100 CPU tüketiyordu | Isınma + donma + ses yok |
| KN-3 | Her `ExoPlayer` `handleAudioFocus = true`; 6 kanal birbirini `LOSS_TRANSIENT`'a düşürüyordu | Karışımda sesler birbirini kesiyor ("zortlama") |
| KN-4 | `StreamVoice.start()` **ana thread'de** `Thread.sleep(20)` ile 4 saniyeye kadar bekliyordu | Çevrimiçi parçaya dokununca uygulama donuyor |
| KN-5 | `usesCleartextTraffic=false`, fakat CC arşivleri `http://` döndürüyor; `DefaultHttpDataSource` yönlendirme izlemiyordu | Çevrimiçi ses "hazır" olmuyor, sessiz kanal |
| KN-6 | `slotGeneration` yoktu: geç biten hazırlık başka sese ait yuvaya yazıyor ve **hiç serbest bırakılmayan** çalar bırakıyordu | "Durdurduğum ses çalmaya devam ediyor, sonra hiç ses çalmıyor" |
| KN-7 | Eşleşme yalnızca `MangaDetails.genre` üzerinden yapılıyordu; tür vermeyen kaynaklarda kalıcı "tanınmadı" | Yanlış "dengeli başlangıç" banner'ı |
| KN-8 | `musicAutoPlayHeadphonesOnly` / `musicAutoPlayMuteOnSilentDevice` `AppSettings`'te tanımlı ama **hiç okunmuyor**, DataStore anahtarı ve setter'ı yok | Ölü ayar: kullanıcı açıp kapatsa da davranış değişmiyor |
| KN-9 | `musicAutoRecommend`, `musicChannelVolume`, `musicAmbientVolume` sadece ayar ekranında görünüyor, çalmayı etkilemiyordu | Ayarlar "çalışmıyor" hissi |
| KN-10 | Otomatik başlatma çevrimiçi kaynakları **hiç** kullanmıyordu; duygu → arama metni dönüşümü yoktu | "Otomatik başlat içeriğe uysun" isteği karşılanmıyordu |
| KN-11 | `MusicCatalogClient.searchArchive` yalnızca `licenseurl:*publicdomain*` filtreliyor, `sort` vermiyordu; `subject` dizisi ham JSON metnine dönüşüyordu | Archive sonuçları neredeyse rastgele / ilgisiz |
| KN-12 | Çevrimiçi kanal sayısı sınırsızdı (6 kanal = 6 ExoPlayer + 6 HTTP) | Zayıf cihazda ses kesintisi (underrun) ve arayüzde takılma |

---

## 2. Yapılan düzeltmeler (T)

### Tur 1 — kilitlenme / donma / eşleşme
- [x] **T1 — Servis ping-pong'u kesildi** (`AmbientPlaybackService.kt`, `AmbientAudioEngine.kt`): `stopAllChannels(fromService = true)` + `serviceStarted` bayrağı.
- [x] **T2 — Prosedürel ses motoru** (`ProceduralSoundPlayer.kt`): `AudioTrack.write()` hatasında `ERROR` durumuna geçiş, `CancellationException` yutulmuyor; CPU döngüsü bitti.
- [x] **T3 — Ses odağı tek merkezde** (`AmbientAudioEngine.kt`): tüm kanallar `handleAudioFocus = false`, odak motorun.
- [x] **T4 — Yuvanın nesil sayacı** (`slotGeneration`): geç biten hazırlık kendi çalarını serbest bırakıyor; `fadeJobs` ile yarışan fade'ler iptal ediliyor; `ERROR` kanal kaldırılabiliyor.
- [x] **T5 — `StreamVoice` askıya alınabilir hazırlık**: `Thread.sleep` yerine `awaitReadyOrError()` + `READY_TIMEOUT_MS = 10 sn`; `AudioTrack` kurulumu `Dispatchers.Default`.
- [x] **T6 — http→https yükseltmesi** (akış + indirme): `usesCleartextTraffic=false` ile uyumlu.
- [x] **T7 — 7 kaynaklı tür/etiket zenginleştirme** (`MangaMoodEnricher.kt`): MAL id → MAL/Jikan, AniList, Shikimori, Kitsu, Bangumi, TMDB, Simkl; 12 saat önbellek; 8 sn toplam / 5,5 sn kaynak tavanı; nötr yedek. Panel çipi: `eşleşme: <güven> · 7 kaynak tarandı · N tür/etiket`.

### Tur 2 — format, akış sağlamlığı, otomatik başlatma
- [x] **T8 — Akış veri kaynağı** (`AmbientAudioEngine.kt: StreamVoice`): paylaşılan **OkHttp** istemcisi (`OkHttpDataSource`) + `DefaultDataSource.Factory(context, http)` bileşkesi.
  - IPv4-öncelikli DNS (IPv6 AAAA dönüp yol olmayan ağlarda akış başlamıyordu), bağlantı havuzu/HTTP2, `Accept-Encoding: identity` (byte-range doğruluğu).
  - `KitsugiHttpClient.streamClient`: `callTimeout` yok (uzun parça), bağlan 15 sn / oku 20 sn, `RetryInterceptor` kaldırıldı (çift deneme yok).
- [x] **T9 — 3 kademeli yeniden deneme**: `Timeout`, ağ hataları, `IO_UNSPECIFIED`, `PlayerError` → 400/800 ms bekleyip tekrar; `BAD_HTTP_STATUS` (404/403) tekrar edilmiyor.
- [x] **T10 — Anlaşılır hata metni**: `ChannelVoice.errorHint()`; panelde hata mesajı + **"Yeniden dene"** düğmesi (`retryChannel`).
- [x] **T11 — Tampon ayarları**: `DefaultLoadControl` 30 sn min / 120 sn max / 4 sn ön tampon / 12 sn yeniden-tampon.
- [x] **T12 — Ekran kapanınca akış**: `setWakeMode(C.WAKE_MODE_NETWORK)` + `WAKE_LOCK` izni.
- [x] **T13 — Uzak kanal sınırı 3** (`MAX_REMOTE_CHANNELS`), aşılırsa sessiz `-1` yerine kullanıcıya Toast (`lastRejection` akışı).
- [x] **T14 — Çalma listesi çözümü** (`MusicCatalogRepository`): `.m3u` / `.pls` ilk geçerli medya adresine açılıyor; `#EXTM3U` (HLS) oynatıcıya bırakılıyor; ağ işi `Dispatchers.IO`.
- [x] **T15 — Kaynak harmanlama**: her kaynak en fazla 12 sonuç, ardından **round-robin** birleştirme + başlık/sanatçı tekilleştirme (tek kaynak diğerlerini ezmiyor).
- [x] **T16 — Audius**: `duration` saniye (×1000) — gerçek yanıtla doğrulandı; `is_streamable`/`is_delete`/`is_available`/`access.stream`/`track_cid` kapıları; kimlik doğrulama `x-api-key`.
- [x] **T17 — Openverse**: `tags` hem nesne hem metin; süre **ms** (çarpma yok); başlık da etiket havuzuna; anonim kota (20/dk, 200/gün) korunuyor.
- [x] **T18 — Internet Archive**: `mediatype:(audio)` + `collection:(opensource_audio)` + `sort[]=downloads desc`; üç kademeli daralma (public domain → koleksiyon → yalnızca ses); `subject` dizisi düzgün ayrıştırılıyor; mood aile çıkarımı.
- [x] **T19 — Otomatik başlatma kapıları** (`AutoplayGate.kt`): kulaklık (kablolu/BT-A2DP/BT-SCO/BLE/USB/işitme cihazı) ve sessiz mod; **yeni DataStore anahtarları + setter'lar + panel anahtarları**.
- [x] **T20 — Otomatik başlatmada çevrimiçi eşleşme** (`AmbientAutoplayStarter.kt` + `MoodSearchQuery.kt`): önce **anında** cihaz içi karışım, 8 sn içinde duyguya uygun gerçek parça bulunursa yumuşak geçiş. Duygu vektörü → İngilizce arama metni (kaynak etiketleri İngilizce).
- [x] **T21 — Ölü ayarlar canlandı**: `musicAutoRecommend` (kapalıysa içerik analizi ve zenginleştirme yok), `musicChannelVolume` / `musicAmbientVolume` (kanal eklerken gerçek seviye).
- [x] **T22 — Panel geri bildirimi**: "Yeniden dene", kanal eklenemedi Toast'ı, arama sırasında çift istek engeli.
- [x] **T23 — Derleme altyapısı**: Kotlin daemon `-Xmx4g → -Xmx6g` (CI'da `Couldn't transform method node / OutOfMemoryError` alınıyordu); otomatik başlatma mantığı 1700 satırlık composable'dan çıkarıldı.

---

## 3. API doğrulamaları (varsayım değil, gerçek yanıt)

| Kaynak | Doğrulanan yanıt | Karar |
|---|---|---|
| Audius | `GET /v1/tracks/search?query=ambient` → `"duration":509` (8:29), `"is_streamable":true`, `track_cid`, `access.stream` | **saniye** → ×1000; ölü parçalar listeye alınmıyor |
| Openverse | `GET /v1/audio/?q=rain&license=cc0` → `"duration":240079` (4 dk), `tags:[{name,accuracy}]`, `X-RateLimit-Limit-anon_burst: 20/min`, `…sustained: 200/day` | süre **ms** (çarpma yok), `page_size ≤ 20` |
| Internet Archive | `advancedsearch.php?q=mediatype:(audio)+AND+collection:(opensource_audio)+AND+(rain ambience)&sort[]=downloads desc` → `numFound: 57`, gerçek ses öğeleri | sorgu şablonu doğrulandı |
| Jamendo / Freesound | Jamendo `duration` saniye; Freesound `previews.preview-hq-mp3` | dönüşümler mevcut haliyle doğru |

---

## 4. Değişen dosyalar

| Dosya | Tür | Açıklama |
|---|---|---|
| `app/src/main/java/com/kitsugi/animelist/core/music/AmbientAudioEngine.kt` | M | OkHttp akış kaynağı, yeniden deneme, tampon, wake mode, uzak kanal sınırı, retry API, hata ipucu |
| `app/src/main/java/com/kitsugi/animelist/core/music/AmbientAutoplayStarter.kt` | **A** | Otomatik başlatma orkestrasyonu (cihaz içi + çevrimiçi eşleşme) |
| `app/src/main/java/com/kitsugi/animelist/core/music/AutoplayGate.kt` | **A** | Kulaklık / sessiz mod kapıları |
| `app/src/main/java/com/kitsugi/animelist/core/music/AmbientPlaybackService.kt` | M | `ACTION_STOP` → `stopAllChannels(fromService = true)` |
| `app/src/main/java/com/kitsugi/animelist/core/music/ProceduralSoundPlayer.kt` | M | Hata durumunda döngü kırma |
| `app/src/main/java/com/kitsugi/animelist/core/music/AmbientDownloadManager.kt` | M | İndirmede http→https |
| `app/src/main/java/com/kitsugi/animelist/core/network/KitsugiHttpClient.kt` | M | `streamClient` (uzun akış için ayrı istemci) |
| `app/src/main/java/com/kitsugi/animelist/data/music/MangaMoodEnricher.kt` | **A** | 7 kaynaklı tür/etiket zenginleştirme |
| `app/src/main/java/com/kitsugi/animelist/data/music/MoodSearchQuery.kt` | **A** | Duygu → çevrimiçi arama metni |
| `app/src/main/java/com/kitsugi/animelist/data/music/MusicCatalogRepository.kt` | M | Round-robin harmanlama, `searchForMood`, çalma listesi çözümü, IO |
| `app/src/main/java/com/kitsugi/animelist/data/music/remote/MusicCatalogClient.kt` | M | Audius / Openverse / Archive düzeltmeleri |
| `app/src/main/java/com/kitsugi/animelist/data/settings/AppSettings.kt` | M | Yeni `musicAutoPlayIncludeOnline`, sessiz-mod varsayılanı |
| `app/src/main/java/com/kitsugi/animelist/data/settings/SettingsDataStore.kt` | M | 3 yeni anahtar + 3 setter |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/manga/MangaReaderScreen.kt` | M | Kapılar, `AmbientAutoplayStarter`, ölü ayarların uygulanması, reddetme Toast'ı |
| `app/src/main/java/com/kitsugi/animelist/ui/screens/manga/components/MangaAmbientPanel.kt` | M | 3 yeni anahtar, "Yeniden dene", hata metni |
| `app/src/main/AndroidManifest.xml` | M | `WAKE_LOCK` izni |
| `.github/workflows/android-build.yml` | M | Kotlin daemon 6 GB, hata tanı desenleri |

---

## 5. Derleme kanıtı

- Workflow: `🏗️ Android Derleme Doğrulama (geçici)` · commit `9ef5232` · run **42329917923**
- `BUILD SUCCESSFUL in 25m 48s`
- `[DEX-OK] …v2.4.457-foss.apk (debug): 23 dex, 441.810 metot, 12.243.754 komut, VerifyError = 0`
- `[DEX-OK] …v2.4.457-foss.apk (release, R8): 5 dex, 272.423 metot, 7.903.902 komut, VerifyError = 0`

---

## 6. Cihaz doğrulama listesi (kullanıcıya bırakıldı)

1. Aynı preset içinde 6 kez ses değiştir — ses kesilmemeli, UI donmamalı.
2. 6 kanallı karışım + master ses sürükle; hiçbir kanal kendiliğinden susmamalı.
3. **Çevrimiçi** sekmesinden Internet Archive ve Openverse'dan birer parça çal (yavaş ağda da).
4. `Çevrimiçi kaynakları kullan` **açık** iken türsüz bir manga aç — cihaz içi ses hemen başlamalı, uygun parça bulunursa geçiş yapmalı.
5. Kulaklık takılı değilken `Sadece kulaklıkta` açık → otomatik başlamamalı; kulaklığı tak → çalmalı.
6. Ekranı kapatıp 2 dk bekle → akış sürmeli; kulaklığı çıkar → ses durmalı.
7. Okuyucudan çık → bildirimden durdur → okuyucuya dön → tekrar çal.
8. Logcat etiketleri: `AmbientAudioEngine`, `StreamVoice`, `AmbientAutoplay`, `AmbientPlaybackService`, `MoodSearchQuery`/`MangaMoodEnricher`.

---

## 7. Kapsam dışı / bilinen sınırlar

- Freesound ve Jamendo **kullanıcı anahtarı** (BYOK) gerektirir; varsayılan kapalıdır.
- `Çevrimiçi kaynakları kullan` bilinçli olarak **varsayılan kapalı**: mobil veride arama + akış trafiği oluşturur.
- Archive öğeleri çok parçalı albüm olabilir; gerçek dosya adı `/metadata/<id>` çağrısıyla çözülür ve ilk uygun MP3/OGG seçilir.
- Gerçek ses duyumu, gecikme ve arka plan sürekliliği yalnızca **cihazda** doğrulanabilir; bu paket statik + derleme kanıtı içerir.
