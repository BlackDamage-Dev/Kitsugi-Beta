# Derin Araştırma v2 — "Kırılmayan" YouTube Music / anime OST kaynağı

**Tarih:** 9 Kasım 2026 · **Sürüm:** 2.4.481 · **Kapsam:** Kitsugi ortam sesi paneli için 10–11. kaynak
**Soru:** YouTube Music'e veya anime OST'lerine kırılmayan, sorun çıkarmayan bir yoldan erişmek mümkün mü?

---

## 0. Tek paragraflık cevap

**Evet, iki tane var ve biri bugün eklendi.** (1) **AnimeThemes.moe** — animenin *kendi* OP/ED şarkısının `.ogg` ses dosyasını doğrudan CDN'den veriyor; çözücü, token, imza yok; 2022'den beri aynı sözleşme. Bu kaynağı **v2.4.481'de panele ekledim** (10. kaynak). (2) **YouTube Music tarafı için "kırılmayan" tek gerçekçi yol NewPipe Extractor**: 7 Kasım 2026'da 0.27.0 çıktı, Java 17 (projemiz zaten 17), JitPack (projemizde zaten tanımlı), PoToken desteği `PoTokenProvider` arayüzüyle açık; LibreTube/NewPipe aynı yolu **cihaz içi WebView + BotGuard** ile çözüyor. Yani YT Music'i kendi çıkarıcımızla değil, bu kütüphaneyle kurmak doğru mimari.

---

## 1. Karar matrisi (11 aday, 2026-11 itibarıyla)

| # | Yol | Ne veriyor | Bakım/kırılma | Bağımlılık | Karar |
|---|-----|-----------|---------------|-----------|-------|
| 1 | **AnimeThemes.moe audio** | Anime OP/ED'nin gerçek ses kaydı | **Yok denecek kadar az** (2019'dan beri, audio 2022) | Yok (HTTP+JSON) | ✅ **Eklendi** |
| 2 | **NewPipe Extractor 0.27.0** | YouTube + SoundCloud + Bandcamp + PeerTube, arama & akış | Düşük: her YouTube kırılmasından **günler sonra** yeni sürüm | JitPack (hazır) + desugaring | ⭐ Önerilen YT yolu |
| 3 | zemer-cipher (Kotlin) | Cipher + BotGuard PoToken + uzaktan player config | Çok düşük (config uzaktan, APK'sız onarım) | `com.zemer:cipher` | ⭐ YT akışı için tamamlayıcı |
| 4 | Bizim `InAppYouTubeExtractor` | YouTube akışı (fragmanlarda bugün çalışıyor) | Orta: client'lar eskir, elle güncelleme | Yok (repoda) | 🟡 Yedek katman |
| 5 | Music Assistant "YT Music (Native)" | Tam YT Music (cookie + V8 solver) | Düşük | Python/sunucu | ❌ Mobilde kullanılamaz |
| 6 | Spotify Connect | Tüm Spotify kataloğu, resmî player | **Sıfır** (resmî API) | Premium + Spotify kurulu | 🟢 Yasal alternatif |
| 7 | SoundCloud api-v2 | OST cover/lo-fi, tam akış | Orta (client_id rotasyonu) | Yok | 🟡 NewPipe ile bedava gelir |
| 8 | Piped / Invidious | — | **Ölü** (Eki 2026: `audioStreams: []`) | — | ❌ |
| 9 | ytmusicapi (Python) | — | OAuth Ağu 2025'ten beri 400 | — | ❌ |
| 10 | Deezer / iTunes | 30 sn önizleme | Düşük | Yok | ❌ ambiyans için yetersiz |
| 11 | bgutil PO token sunucusu | YT PoToken üretimi | Orta | Ayrı sunucu (Docker) | ❌ mobilde yok |

---

## 2. YouTube Music: 2025–2026 zaman çizelgesi (neden eski yollar öldü)

| Tarih | Olay | Sonuç |
|-------|------|-------|
| 2025-03 | Google **PO Token (Proof of Origin)** zorunluluğu | `web_music` formatları tokensız 403 (GVS token) |
| 2025-08 | `ytmusicapi` OAuth yolu **400 INVALID_ARGUMENT** | Python yolu OAuth ile çöp (issue #813 hâlâ açık) |
| 2025 sonu | Music Assistant PO Token Generator eklentisi şart oldu | Sunucusuz kurulum imkânsız hâle geldi |
| 2026-02 | **zemer-cipher** doğdu (Metrolist 2 gün sonra aldı) | WebView ile cipher + PoToken, **cihaz içi** |
| 2026-10 | TV OAuth token'ları WEB_REMIX'te 400 | "TV client ile giriş" yolu kapandı |
| 2026-10-30 | Music Assistant **"YT Music (Native)"**: cookie + SAPISIDHASH + gömülü V8 solver | PO token sunucusu **olmadan** çalışıyor |
| 2026-11-05 | TVHTML5 + Bearer ile kişisel veriler çalışıyor, WEB_REMIX 400 | Karışık client stratejisi şart |
| 2026-11-07 | **NewPipe Extractor 0.27.0** | Android/IOS client metodları kaldırıldı (PoToken üretilemiyor), web client + PoToken öneriliyor; Java 17 |

**Çıkarım:** "Tek bir sabit uç nokta" devri bitti. Kırılmayan çözüm = **hızlı güncellenen kütüphane + çoklu client + PoToken üretimi + uzaktan yapılandırma**. Bu dördü birden yalnızca NewPipe/zemer-cipher/LibreTube hattında var.

---

## 3. AnimeThemes.moe — "animenin kendi müziği" (✅ eklendi)

### 3.1 Ne veriyor?
- Anime **OP (opening) ve ED (ending)** şarkılarının **ses dosyası**: `https://animethemes.moe/audio/Bakemonogatari-OP1.ogg`
- API `Audio` kaynağı: `basename, filename, path, size, mimetype, link, views_count`
- Global arama: `GET https://api.animethemes.moe/search?q=<isim>` → `search.anime[]`, `search.songs[]`, `search.videos[]`
- Tema detayı: `/anime?filter[slug]=…&include=animethemes.song.artists,animethemes.animethemeentries.videos.audio`

### 3.2 Neden "kırılmaz"?
- YouTube'a hiç dokunmuyor: imza/cipher/PO token/SABR yok.
- Dosyalar **statik CDN** üzerinde; indirilebilir → çevrimdışı çalışır (panel indirme özelliği de çalışıyor).
- API sözleşmesi 2019'dan, `audio` 2022'den beri aynı.
- Zaten uygulamanın içinde kullanılıyor: `KitsugiAnimeThemesClient` detay sayfasında OP/ED video linklerini bu API'den alıyor.

### 3.3 Bu sürümde ne yapıldı?
- Yeni `data/ambient/AnimeThemesAudioSource.kt`:
  - okunan içeriğin adıyla `search` → en iyi eşleşen anime (kelime örtüşmesi puanı, yanlış animeyi seçmemek için eşik),
  - OP/ED listesi → `audio.link` (yoksa `basename`'den türetilen `.ogg`),
  - `provider = "animethemes"`, `fileType = "ogg"`, `downloadUrl` dolu.
- `AmbientAudioSource.ANIMETHEMES("animethemes", "Anime OP/ED")` — kaynak listesi otomatik 10'a çıktı.
- Repository dağıtımı + `sourceWarning` + panelde "Animenin kendi OP/ED şarkısı · indirilebilir" yetenek satırı.

---

## 4. YouTube Music için önerilen mimari (3 katman)

```
[1] AnimeThemes  →  anime OP/ED (kesin çalışır, indirilebilir)
        │
[2] NewPipe Extractor (YouTube + SoundCloud)
        ├─ PoToken: cihaz içi WebView + BotGuard (LibreTube/NewPipe modeli)
        ├─ Çoklu client fallback (web / web_music / tv_embedded / android_vr)
        └─ Player config uzaktan güncellenebilir (zemer-cipher modeli)
        │
[3] Bizim InAppYouTubeExtractor (yedek)  →  HLS/DASH/progressive fallback
```

**Kritik tasarım kararları**
1. **Arama**: `youtubei/v1/search` (`filter=music_songs` eşdeğeri params) — NewPipe'ın `YoutubeSearchExtractor`'ı bunu hazır veriyor.
2. **Akış**: `YoutubeStreamExtractor` → audio-only adaptive (m4a/opus) → yoksa HLS manifest → yoksa progressive.
3. **PoToken**: `PoTokenProvider` implementasyonu; WebView gizli, yalnızca YouTube token istediğinde oluşturulur (LibreTube davranışı). Token yoksa kütüphane "best effort" ile devam eder.
4. **İndirme**: YT kaynağında **kapalı** (panelde "yalnızca akış" rozeti). İndirme, AnimeThemes + 9 açık lisans kaynağında çalışmaya devam eder.
5. **Hata yönetimi**: kaynak başına `try/catch`, parça başına `onPlayerError` → sonraki adaya geç; hiçbir durumda tüm kuyruk düşmez.
6. **Rate limit**: arama başına 1 istek, 15 dk cache, `recordPlayed` ile tekrar engelleme.

**Bağımlılık maliyeti**
- `implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.27.0")` (JitPack **zaten** tanımlı)
- `isCoreLibraryDesugaringEnabled = true` + `desugar_jdk_libs_nio` (minSdk 26 < 33 olduğu için)
- APK'ya ~1.5–2 MB

---

## 5. Kırılma senaryoları ve karşı önlemler

| Senaryo | Belirti | Önlem | Kullanıcı ne görür |
|---|---|---|---|
| YouTube player JS döndü | Yeni parçalarda 403 | NewPipe sürüm güncellemesi (günler içinde) + bizim extractor fallback | Sadece o parça atlanır, müzik devam eder |
| PoToken zorunlu | `audioStreams` boş | WebView PoToken; başarısızsa HLS manifest | "YouTube şu an yanıt vermedi" uyarısı |
| Rate limit (429) | Arama boş | Cache + tekrar engelleme | "Biraz sonra tekrar dene" |
| Bölgesel kısıt | Parça çalınmıyor | Sonraki parçaya geç | Sessiz atlama |
| İnternet yok | Hepsi boş | İndirilenler + cihaz içi prosedürel motor | Kesintisiz devam |
| AnimeThemes'te kayıt yok | Boş liste | Diğer kaynaklar + cihaz içi motor | Uyarı satırı |

---

## 6. Uygulama planı (YT Music katmanı, sıradaki adım)

| Adım | Dosya | İş | Risk |
|---|---|---|---|
| 1 | `app/build.gradle.kts` | NewPipe Extractor + desugaring | Düşük (JitPack hazır) |
| 2 | `data/ambient/YouTubeMusicAudioSource.kt` | Arama + akış eşleme (`AmbientAudioTrack`) | Orta |
| 3 | `data/ambient/poToken/` | `PoTokenProvider` + gizli WebView | Orta |
| 4 | `YoutubeStreamExtractor.setPoTokenProvider` + `NewPipe.init` | Başlatma | Düşük |
| 5 | `AmbientAudioSource` + panel + ayarlar | 11. kaynak, "deneysel" etiketi | Düşük |
| 6 | Belge + sürüm notu | | Düşük |

**Tahmini süre:** 1 tur (yaklaşık 400–600 satır), test cihazda ilk çalıştırmada PoToken üretimi doğrulanmalı.

---

## 7. Kaynaklar

1. NewPipe Extractor sürümleri: https://github.com/TeamNewPipe/NewPipeExtractor/releases
2. NewPipe Extractor 0.27.0 analizi: https://freedom.tech/posts/2026-11-07-newpipe-extractor-0-27-0/
3. `YoutubeStreamExtractor.setPoTokenProvider` (javadoc): https://teamnewpipe.github.io/NewPipeExtractor/javadoc/org/schabi/newpipe/extractor/services/youtube/extractors/YoutubeStreamExtractor.html
4. LibreTube PoToken (WebView) PR: https://github.com/libre-tube/LibreTube/pull/7069
5. NewPipe PoToken PR: https://github.com/TeamNewPipe/NewPipe/pull/11955
6. zemer-cipher (cipher + PoToken + uzaktan config): https://github.com/ZemerTeam/zemer-cipher
7. Metrolist (Kotlin YT Music istemcisi): https://github.com/MetrolistGroup/Metrolist
8. BgUtils (BotGuard/WebPO araştırması): https://github.com/LuanRT/BgUtils
9. yt-dlp PO Token rehberi: https://github.com/yt-dlp/yt-dlp/wiki/PO-Token-Guide
10. ytmusicapi OAuth 400: https://github.com/sigma67/ytmusicapi/issues/813
11. Music Assistant "YT Music (Native)": https://github.com/music-assistant/server/pull/7309
12. AnimeThemes API — Audio kaynağı: https://api-docs.animethemes.moe/content/audio/
13. AnimeThemes API — Global arama: https://api-docs.animethemes.moe/search/
14. AnimeThemes audio duyurusu (2022-07): https://animethemes.moe/blog/status_2022_07
15. Piped'in bloklanması: https://github.com/felipeteixeirams/music-player/pull/33
16. Spotify Start/Resume Playback: https://developer.spotify.com/documentation/web-api/reference/start-a-users-playback

---

## 8. Uygulama notu (v2.4.482) — YouTube Music eklendi, **yeni bağımlılık yok**

Araştırmadaki "NewPipe Extractor" yolu en dayanıklı seçenekti, ancak APK'ya yeni bir Gradle
bağımlılığı (ve desugaring) ekliyor ve bu ortamda derleme doğrulanamıyor. Bu yüzden ilk sürüm
**repoda zaten çalışan** `InAppYouTubeExtractor` üzerine kuruldu:

| Parça | Uygulama |
|---|---|
| Arama | `youtubei/v1/search` (WEB client) — `YouTubeMusicAudioSource`, api key youtube.com'dan bir kez okunup önbelleklenir |
| Ses adresi | `kitsugi-yt://<videoId>` işaretçisi; **çalma anında** `extractPlaybackSource` ile çözülür |
| Başlıklar | `ResolvingDataSource` ile imzalı adres + istemci UA'sı birlikte uygulanır |
| Sıralama | OST/OP/ED/theme/lyric sonuçları öne, fragman/inceleme/reaction sonuçları geriye |
| İndirme | Kapalı (imzalı adresler kısa ömürlü) |
| Hata | Parça başına atlama, kaynak başına uyarı; kuyruk asla düşmez |

**Sonraki adım (opsiyonel):** NewPipe Extractor + WebView PoToken katmanı. Yalnızca mevcut
çıkarıcı YouTube tarafında tamamen kırılırsa gerekli; o zaman `v0.27.0` + `desugar_jdk_libs_nio`
eklenip aynı arayüzün arkasına ikinci bir çözücü olarak konur.
