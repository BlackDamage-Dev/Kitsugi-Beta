# PLAN & TASK — Günlük Sistemi (Log) · v2.4.342

> Bu dosya, **"arka planda olan biten her şeyi günlük alabilsin"** isteği için yapılan
> dördüncü turun planı, kabul kriterleri, değişen dosya listesi ve doğrulama adımlarını
> içerir. Yanında `kaynak-degisiklikleri.zip` (değişen tüm dosyalar) ile birlikte verilir.

**Tarih:** 2026-10-31
**Sürüm:** 2.4.342
**Dal:** `arena/df3b9268-kitsugi-beta`
**Kapsam:** Günlük/tanı altyapısı + tüm liste kaynaklarının kayıtları

---

## 1. Kullanıcı isteği (birebir)

> "peki bu kaynaklar ile ilgili oluşabilcek tüm kayıtlar hatalar veya sorunlarda dahil log
> kayıtlarına eksikszi bir biçimde o sırada yaşanan arka plan sorunlarını elle alıp
> kopyalayamadığımız için bu log kısmının her türlü sorunu sıkıntıyı hatayı uyarıyı vs her
> şeyi alabilmesi lazım ... tabiki çökme donma vs yapmadan"

**Çıkarılan gereksinimler**

| # | Gereksinim | Durum |
|---|-----------|-------|
| R1 | Günlük **kalıcı** olmalı; uygulama kapansa/çökse de satırlar durmalı | ✅ |
| R2 | Eşitleme/ağ/kuyruk/worker/çökme/donma **hepsi** kaydedilmeli (AniList, MAL, Kitsu, Shikimori, Simkl, Bangumi, TMDB, manga+eklenti) | ✅ |
| R3 | Kullanıcı **elle kopyalamak zorunda kalmadan** günlüğe ulaşabilmeli (ekran + dosya paylaşımı) | ✅ |
| R4 | **Çökme yapmamalı**; günlük yazımı uygulamayı düşürmemeli | ✅ |
| R5 | **Donma yapmamalı**; yazma UI iş parçacığını bekletmemeli, kuyruk sınırsız büyümemeli | ✅ |
| R6 | Donmalar da **kayda geçmeli** (ANR sonrası sebep bulunabilsin) | ✅ |
| R7 | Depolama şişmemeli (üst sınır + döndürme) | ✅ |
| R8 | Gizli bilgi (token, sorgu dizesi, gövde) günlüğe **yazılmamalı** | ✅ |

---

## 2. Kök neden analizi (neden eski günlük yetmiyordu)

1. **"Günlükler" ekranı `logcat -d` okuyordu.** Android 10+ üzerinde uygulama yalnızca kendi
   süreç satırlarını görebilir; ayrıca logcat halka tamponu dolduğunda **eski satırlar
   silinir**. Yoğun kullanımda birkaç dakika önceki arka plan hatası kanıtsız kalıyordu.
2. **Dosyaya yazan tek yol** (`FileLoggingTree`) yalnızca oynatıcı ve birkaç tanı noktasından
   çağrılıyordu → eşitleme, ağ, kuyruk ve worker hataları **dosyaya hiç girmiyordu**.
3. Ekran yalnızca son **1500** satırı tutuyordu; gece boyu süren 429 fırtınası görünmüyordu.
4. **Donmalar** için hiçbir kayıt yoktu: ANR/`LMKD` kill sonrası yalnızca "temiz kapanmadı"
   notu kalıyor, **hangi kodun kilitlediği** kayboluyordu.
5. Ekmek kırıntısı dosyası **her kullanıcı eyleminde** baştan yazılıyordu (gereksiz disk yükü).

---

## 3. Yapılan iş (dosya dosya)

### 3.1 Yeni dosyalar

| Dosya | Görev |
|-------|-------|
| `core/diagnostics/KitsugiLog.kt` | Tek yazma yolu: bellek halkası (3000) + kalıcı dosya (4 MB'da döner, ~8 MB tavan), bloklamayan kuyruk (dolu ise at+say), tekrar sıkıştırma, 10 kanal (`APP/SYNC/NET/SOURCE/WORK/PLAYER/CRASH/FREEZE/USER/SYSTEM`), asla istisna fırlatmaz |
| `core/diagnostics/UiFreezeWatchdog.kt` | Ana iş parçacığını saniyede 1 yoklar; >2,5 sn yanıt yoksa **yığın izi** ile FREEZE, ≥8 sn → ERROR. Donma başına en fazla 5 sn'de bir kayıt |
| `core/diagnostics/LogcatCapture.kt` | `logcat -v time --pid=<pid>` akışı → kalıcı günlük. Kendi `KitsugiLog` satırları ayıklanır, saniyede ≤300 satır, `logcat` yoksa tek satır not düşüp susar |
| `core/network/KitsugiNetworkLogInterceptor.kt` | Her ağ denemesi: yöntem + host + yol + HTTP kodu + süre + `Retry-After`/`X-RateLimit-*`. Sorgu/başlık/gövde **yazılmaz** |
| `ui/components/SourceSyncStatusCard.kt` | (3. tur) Bildirimler ekranındaki kaynak durum kartı |
| `data/auth/SourceSyncStatus.kt` | (3. tur) 6 kaynağın kalıcı eşitleme durumu + halk dili metinler |
| `data/auth/AniListGate.kt`, `data/local/ExternalSyncCoalescer.kt` | (1–2. tur) kota kapısı + yazma birleştirme |
| `app/src/test/.../AniListGateTest.kt` | Kota kapısı birim testleri |
| `ANILIST_KOTA_KOK_NEDEN_RAPORU_2026-10-31.md` | Tüm turların kök neden raporu (§8–§10) |
| `release_notes_v2.4.342.md` | Sürüm notları (TR/EN) |

### 3.2 Değiştirilen dosyalar (özet)

- **Besleyiciler:** `SourceSyncStatus` (başarı/başarısızlık), `AniListGate` (429 + `X-RateLimit-Remaining=0`),
  `PendingSyncDrainer` (tur başı/sonu, kalan iş), `PlatformFailureRegistry` (KTS-… kodu),
  `MangaLogger` (manga kaynakları), `FileLoggingTree` + `PlayerLogger` (oynatıcı), `KitsugiCrashHandler`
  (çökme + 2 sn flush), `KitsugiSessionSupervisor` (sessiz ölüm, ekran geçişi, kırıntı yazımı 1,5 sn'ye sınırlandı)
- **Ağ:** `KitsugiHttpClient` (paylaşılan istemci → tüm liste kaynakları), `TmdbApiClient`
- **Başlatma:** `KitsugiApplication` → `KitsugiLog.init` en erken noktada, `UiFreezeWatchdog`,
  `LogcatCapture` (1,5 sn gecikmeli)
- **Görüntüleyici:** `DeveloperLogsDialog` → "Uygulama günlüğü" varsayılan kaynak, konu filtreleri
  (Eşitleme/Ağ/Kaynak/Arka plan/Donma/Çökme/Sistem/Diğer), istatistik satırı, **dosya olarak paylaş**,
  temizleme kalıcı günlüğü de kapsar, boş durum açıklaması
- **Çökme ekranı:** `KitsugiCrashActivity` → kalıcı günlük rapora eklenir
- **3. tur:** `MyListScreen`/`MyListViewModel` (kaynak şeridi), `KitsugiNotificationsScreen` (kart),
  `AuthViewModel` (içe aktarma hataları), profil ViewModel'leri (kota mesajı), `strings.xml` ×2, `app/build.gradle.kts` (2.4.342)

---

## 4. "Donma yapmama" tasarımı

- Tüm yazma **tek** daemon iş parçacığında (`KitsugiLogIO`, `MIN_PRIORITY`), kuyruk 4096;
  **doluysa satır atılır ve `dropped` sayacı artar** → ana iş parçacığı asla beklemez.
- Görüntüleyici: IO dispatcher + `RandomAccessFile` ile yalnızca son ~512 KB; ekranda en fazla
  3000 satır, `LazyColumn`, satır başına `SelectionContainer` (bilinen Compose seçim çökmesi çözümü korunur).
- `logcat` alt süreci **`waitFor` ile beklenmez**; okuma kesilirse akış biter.
- Kırıntı (breadcrumb) dosyası 1,5 sn'de bir yazılır; çökme anında `flushNow` zorlar.
- Donma bekçisi ana iş parçacığına saniyede **tek boş `post`** bırakır.

## 5. Gizlilik

- Günlüğe **yazılmayanlar:** istek sorgu dizeleri, Authorization/çerez başlıkları, gövde içerikleri,
  OAuth jetonları. Yalnızca host + yol (120 karakterle sınırlı) yazılır.

---

## 6. Kabul kriterleri (test edilebilir)

| # | Kriter | Nasıl doğrulanır |
|---|--------|------------------|
| K1 | Günlük dosyası oluşur ve uygulama yeniden açıldığında korunur | `files/diagnostics/kitsugi_runtime.log` > 0 KB |
| K2 | Eşitleme sonucu kayda geçer | Listem'de kayıt düzenle → "Eşitleme" filtresinde `SYNC` satırı |
| K3 | Ağ kanıtı var | Aynı anda "Ağ" filtresinde `GET/POST … → HTTP 200` + süre |
| K4 | Kota görünür | 429 alındığında `istek sınırı doldu (HTTP 429)` + `retry-after` |
| K5 | Çökme kayda geçer | Zorla çökme (test) → `CRASH` kanalında `ÇÖKME: …` + yığın izi |
| K6 | Donma kayda geçer | 3 sn ana iş parçacığı kilidi → `FREEZE` kanalında yığın izi |
| K7 | Sessiz ölüm kayda geçer | Uygulamayı zorla durdur → açılışta `SYSTEM` kanalında "Önceki oturum temiz kapanmadı" |
| K8 | Dosya paylaşımı çalışır | Günlükler ▸ Paylaş → dosya eki olarak gider |
| K9 | Depolama sınırı | Dosya 4 MB'ı aşınca `.1` yedeğine döner, toplam ≈8 MB |
| K10 | Donma/ANR yok | Günlük açıkken 10 dk gezinme; `FREEZE` satırı **artmamalı** |

## 7. Doğrulama adımları (kullanıcı tarafı)

```bash
# 1) Derleme + birim testleri
./gradlew :app:testDebugUnitTest

# 2) Kurulum
./gradlew :app:installDebug
```

Uygulama içinde: **Ayarlar ▸ Sistem ▸ Geliştirici günlükleri**
1. Kaynak: **Uygulama günlüğü** (varsayılan)
2. Konu: **Eşitleme** → kayıt düzenleyin, satır gelmeli
3. Konu: **Ağ** → `POST graphql.anilist.co → HTTP 200` satırı
4. Sağ üst **Paylaş** → dosya eki olarak `kitsugi_runtime.log`
5. Uygulamayı zorla durdurup açın → **Sistem** konusunda önceki oturum notu

## 8. Bilinen sınırlar / sonraki adımlar

- Bazı ROM'larda `logcat` uygulamaya kapalıdır → sistem köprüsü sessizce devre dışı kalır;
  uygulama içi kayıtlar (SYNC/NET/WORK/CRASH/FREEZE) **her cihazda** çalışır.
- WorkManager işleri şu an merkezî köprüden değil, kendi `Log.*` satırlarından (sistem köprüsü)
  ve `PendingSyncDrainer` üzerinden kaydediliyor; ileride ortak bir `WorkerFactory` sarmalayıcısı
  eklenebilir.
- Günlük dosyası kullanıcıya açık bir "dışa aktarma klasörü"ne taşınmıyor (gizlilik); paylaşım
  FileProvider ile yapılır.

---

## 9. Zip içeriği

`kaynak-degisiklikleri-v2.4.342.zip`

```
PLAN_TASK_GUNLUK_SISTEMI_v2.4.342.md      ← bu dosya
ANILIST_KOTA_KOK_NEDEN_RAPORU_2026-10-31.md
release_notes_v2.4.342.md
app/build.gradle.kts
app/src/main/java/... (değişen/yeni tüm .kt dosyaları, klasör yapısı korunarak)
app/src/main/res/values/strings.xml
app/src/main/res/values-en/strings.xml
app/src/test/java/.../AniListGateTest.kt
DIFF_vs_main.patch                        ← ham git diff (inceleme için)
```
