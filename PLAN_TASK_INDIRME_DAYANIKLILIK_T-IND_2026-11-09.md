# PLAN TASK — İndirme Hattı Dayanıklılığı (T-IND-1 / T-IND-2 / T-IND-3)
Tarih: 2026-11-09 · Dal: `arena/1aa6d85c-kitsugi-beta` · Commits: `6bae6c4`, `c319aee`, `b073f7b`
Bağlı rapor: `KOK_NEDEN_VE_COZUM_RAPORU_HDFC_FM_INDIRME_DNS_429_2026-11-09.md`
Eklenti patch'i: `KITSUGI_PLUGINS_HDFC64_FM74_CDN_PROBE_FIX.patch`

## Görevler ve durum
| # | Görev | Kök neden | Durum |
|---|---|---|---|
| 1 | Kaynak politikası + hata sınıflama + backoff/Retry-After/tempo (`DownloadResilience.kt`) | KN-2 | ✅ |
| 2 | Retry çarpımını kıran `downloadClient` (RetryInterceptor yok) | KN-2 | ✅ |
| 3 | `master.txt`/`playlist.txt` → HLS kararı | KN-3 | ✅ |
| 4 | Segment önce belleğe sonra dosyaya (kısmi yazım yok) | KN-4 | ✅ |
| 5 | Ölü CDN host'unda embed'den adres tazeleme (`DownloadStreamResolver.kt`) + `recoverUrl` damgası | KN-1 | ✅ |
| 6 | Sistem DNS NXDOMAIN → DoH zinciri (`IPv4FirstDns`) | KN-5 | ✅ |
| 7 | Gerçek hata mesajı modelde + İndirmeler ekranında | KN-6 | ✅ |
| 8 | Yerleşik extractor önekleri: hdfilmcehennemi.mobi / rapid.filmmakinesi.to / rplayer | KN-1 | ✅ |
| 9 | Journal'lı crash-proof resume (`SegmentJournal.kt`): duplikasyon/yarım dilim imkânsız | KN-7 | ✅ |
| 10 | Standart HLS: AES-128 çözüm, BYTERANGE aralık, DRM temiz red (`HlsPlaylistParser.kt`) | KN-8 | ✅ |
| 11 | Bölüm silinirken `.jl` defteri temizliği (`DownloadExportNaming`) | KN-7 | ✅ |
| 12 | Eklenti: master probe + yedek master seçimi (playmix tuzak eleme), sürüm 64/74 | KN-1 | ✅ patch |
| 13 | Birim testler: `DownloadResilienceTest`, `SegmentJournalTest`, `HlsPlaylistParserTest` | — | ✅ |

## Cihazda doğrulama
1. HDFC/FM'den bölüm indir → indirme sürerken 10 sn uçak modu → backoff ile devam etmeli.
2. Kuyruğa al, beklet, "devam et" → journal logu: `journal resume: dilim=N/…`; dosya MX'te temiz oynar.
3. Logcat tag'leri: `AnimeDownloader`, `DownloadStreamResolver`, `KitsugiCdnJwExtractor`.
4. CI: `./gradlew :app:testDebugUnitTest --tests "com.kitsugi.animelist.core.download.*"`.

## Bilinen sınırlar
- Ayrı ses rendition'lu fMP4 mux'ı cihazda yapılamaz (HDFC/FM mux'lı TS verir, etkilenmez).
- DRM (SAMPLE-AES/WIDEVINE) bilinçli indirilemez.
- Eski bozuk dosyalar silinip yeniden indirilmeli.
