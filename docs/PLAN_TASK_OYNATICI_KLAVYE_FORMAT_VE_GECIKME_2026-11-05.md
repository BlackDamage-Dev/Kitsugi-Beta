# PLAN / TASK — Oynatıcı Klavye, Parça Formatları ve Altyazı Gecikmesi

**Tarih:** 2026-11-05  
**Branch:** `arena/b82318d9-kitsugi-beta`  
**Kapsam:** Tam ekran oynatıcı, MPV/Media3 parça meta verileri, altyazı önbelleği ve klavye kontrolleri

## 1. Kullanıcı gereksinimleri

1. Bozuk “Altyazı gecikmesi” Birincil / İkincil / İkisi Birden açılır menüsünü normal, düzgün konumlanan ve kullanılabilir bir kontrol hâline getirmek.
2. Parlaklık ve ses dahil oynatıcı klavye kontrollerini gerçek eylemlere bağlamak, tuşları kullanıcıya göstermek ve kapsamlı bir kısayol rehberi sunmak.
3. Altyazılar panelinde gömülü, harici ve eklenti kaynaklı altyazıların gerçek biçimlerini rozetlerle göstermek.
4. Ses Parçaları panelinde gömülü ve harici ses parçalarının gerçek codec/biçimlerini rozetlerle göstermek.

## 2. Tamamlanan işler

### 2.1 Altyazı gecikmesi seçici

- Başlık ve kapatma eylemi ayrı bir satıra alındı.
- Birincil / İkincil / İkisi Birden seçimi için kart genişliğinde ayrı bir seçim alanı oluşturuldu.
- Menü, dar başlık metni yerine bu seçim alanına bağlandı.
- Menü genişliği 280 dp ile sınırlandı; mevcut `KitsugiDropdownMenu` konumlandırması kullanıldı.
- Erişilebilir kapatma açıklaması ve taşma koruması eklendi.

### 2.2 Klavye kontrolleri

- `PlayerKeyboardShortcuts.kt` ile merkezi eylem, tuş eşleme ve tekrar politikası oluşturuldu.
- Ses:
  - Kontroller kapalıyken `↑ / ↓`
  - Kontroller açıkken de `0 / 9`
  - Donanım ses tuşları
  - `M`: sessize al / önceki duyulabilir seviyeye dön
- Parlaklık:
  - `Ctrl + ↑ / ↓`: yüzde 5 adımlar
  - Klavyedeki özel parlaklık tuşları
- Oynatma/gezinme:
  - `Space`, `K`: oynat / duraklat
  - `← / →`, `J / L`: 10 saniye
  - `Shift + ← / →`: 60 saniye
  - `N / P`: sonraki / önceki bölüm
  - `Tab`: kontrolleri göster / gizle
- Hız, parçalar ve araçlar:
  - `[ / ]`, `\`, `U`: hız işlemleri
  - `S`, `D`, `A`, `#`, `Q`: altyazı/ses/kalite işlemleri
  - `E`, `C`, `O`, `G`, `I`, `F`, `Z`, `R`, `X`: ilgili oynatıcı araçları
- `H`, `F1` ve üretilen gerçek `?` karakteri ile kısayol rehberi açılır.
- Kısayol rehberi kaydırılabilir, bölümlenmiş Türkçe bir dialog olarak eklendi; OSD’de dokunulabilir keşif metni de bulunur.
- Kontroller görünürken D-pad/Enter odak navigasyonu korunur; Space/Tab global eylemlerini sürdürür.
- Basılı tutma tekrarı yalnızca sürekli kontrollerde etkinleştirildi; panel/dialog kapanışında ana oynatıcı odağı geri yüklenir.
- Eşlenmeyen tuşlar, kontroller kapalıyken MPV `input.conf` yapılandırmasına iletilir.

### 2.3 Altyazı ve ses format rozetleri

- `TrackFormatUtils.kt` ortak biçim çözümleyicisi eklendi.
- Motor codec/MIME bilgisi, yanıltıcı dosya uzantısı veya eklenti bildiriminden önce değerlendirilir.
- ASS/SSA, SRT, VTT, TTML/DFXP, PGS, VobSub, MicroDVD ve diğer altyazı biçimleri için kısa etiketler üretilir.
- AAC, AC-3, E-AC-3/JOC, DTS/DTS-HD, TrueHD, FLAC, Opus, Vorbis, ALAC ve diğer ses biçimleri desteklenir.
- Önceden bilinmeyen nadir codec’lerde motorun gerçek codec adı gösterilir; bilinmeyen URL’ler uydurma SRT olarak etiketlenmez.
- Harici altyazı önbelleğinde içerik imzası Content-Type ve URL uzantısından önce kullanılır.
- UTF-8/UTF-16 WebVTT, ASS/SSA, TTML, SAMI, MicroDVD, SRT ve SBV imzaları tanınır.
- Düz `srt`, `sub`, `m4a` gibi eklenti biçim bildirimleri desteklenir; uzantı ayrıştırması `Locale.ROOT` kullanır.
- MPV ve Media3 parça meta verileri `TrackOption` üzerinden panellere taşınır.
- Harici altyazı meta verisi yalnızca gerçek harici parça kimliği/yolu ile eşleştirilir; aynı ada sahip gömülü parçaya eklenti etiketi yapışmaz.
- Ses satırlarında codec rozetiyle birlikte Mono, Stereo, 5.1, 7.1 veya kanal sayısı gösterilir.
- Dil adları tekrarlanmadan, uzun başlıklar ve rozetler taşma korumasıyla gösterilir.

## 3. Eklenen testler

- `app/src/test/java/com/kitsugi/animelist/core/player/TrackFormatUtilsTest.kt`
- `app/src/test/java/com/kitsugi/animelist/ui/screens/fullscreen/controls/PlayerKeyboardShortcutsTest.kt`

Bağımsız Kotlin derleyicisi ve çalışma zamanı harness’larıyla üretim kaynakları doğrulandı:

- **TrackFormatUtils: 33 kontrol geçti.**
- **PlayerKeyboardShortcuts: 31 kontrol geçti.**
- Değişen Kotlin dosyaları ktfmt ayrıştırmasından geçti.
- `git diff --check` temiz tamamlandı.

## 4. Doğrulama sınırları ve kalan işler

- `:app:compileFossDebugKotlin` denendi; Gradle 8.13 dağıtımı `services.gradle.org` adresinden indirilirken SSL el sıkışması kesildi. Bu nedenle tam Android derlemesi ve repository JUnit görevleri çalıştırılamadı.
- Aşağıdaki maddeler gerçek cihaz/emülatörde görsel ve işlevsel olarak doğrulanmalıdır:
  - Gecikme menüsünün farklı ekran boyutlarında konumu
  - Ses ve parlaklık tuşları ile OSD geri bildirimi
  - Odak navigasyonu, tekrar eden tuşlar ve dialog kapanışı
  - PiP eylemi ve kısayol rehberi
  - Gömülü/harici/eklenti parçalarında gerçek format rozetleri
  - Farklı klavye düzenlerinde `?` kısayolu

## 5. ZIP içeriği

ZIP, bu belgeyle birlikte çalışma sırasında değiştirilen veya eklenen 24 Kotlin/test dosyasını özgün klasör yapılarıyla içerir. Derleme çıktıları, geçici araçlar ve büyük veri dosyaları eklenmemiştir.
