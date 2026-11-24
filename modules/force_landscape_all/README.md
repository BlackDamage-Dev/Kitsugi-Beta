# Force Landscape - All Apps  |  v2.23.3

Xiaomi Pad 6 (pipa) · MIUI 14 / Android 13 · SukiSU Ultra

Dikey çalışan uygulamaları **tam ekran yataya** zorlar, kendi kendine yatay
çalışan oyunlara/video uygulamalarına **dokunmaz**. Arayüz **WebUI**.

## WebUI nerede?

**SukiSU Ultra → Modüller → Force Landscape → WebUI**

| Sekme | Ne var |
|---|---|
| **Durum** | Büyük sayaçlar (letterbox çözülen / kalan / hatalı), yön modu, ekran, katman testi. Uyarı bandı: katman çalışmıyorsa sebebi söyler. Hızlı işlemler: *Şimdi uygula · Letterbox'ı onar · Katmanı test et · Rapor+log indir · Duraklat* |
| **Uygulamalar** | Arama + filtre (Hepsi / Yatay / Otomatik / **Dikey** / **Bekleyen**). Her uygulamada **Yatay · Dokunma · Oto**, ayrıca **⚡ hemen uygula** ve **⏹ uygulamayı kapat**. Toplu: “filtredekileri yatay yap”. |
| **Letterbox** | Katmanın canlı durumu: uygulandı/kalan/hata, **Onar** düğmesi, **paket bazlı sonuç listesi** ve **son hatalar** kutusu. |
| **Log** | 200/500/2000 satır, **canlı takip** (3 sn), kopyala, `/sdcard/Download`'a kaydet, temizle; altta katman testinin ham çıktısı. |
| **Ayarlar** | Mod (Akıllı / Sadece seçtiklerim / Hepsi / Kapalı), yön (Sensör / Kilit / Kapalı), uygula, onar, rapor, seçimleri kopyala, duraklat/devam, geri al, yeniden başlat. |
| **Yardım** | Katlanır bölümler: “hâlâ dar sütun”, “oyun bozuldu”, “test ÇALIŞMIYOR”, dosya yolları, geri dönüş, dürüst sınırlar. |

Action düğmesi de duruyor: kısa özet + katman testi yazar (WebUI açılmadan bakmak için).

## v2.8.1 — logolar

Uygulama simgeleri gelmiyordu; üç hata üst üste binmişti:
(1) `ksu://icon/<paket>` isteği hiç yapılmıyordu — kod önce `ksu.getPackagesInfo`
varlığına bakıp "destekleniyor mu" kararı veriyordu ve SukiSU Ultra'nın WebUI
köprüsünde bu fonksiyon yok; (2) `loading="lazy"` yüzünden liste yeniden çizilirken
resim istekleri başlamıyordu; (3) hangi şemanın çalıştığı hiç ölçülmüyordu.
Artık açılışta `ksu://icon` ve `mui://icon` görünmez birer prob ile ölçülür,
çalışan kullanılır, olmazsa harf kutusu (kırık resim asla görünmez). Durum
sekmesinde **logo motoru** satırı + **🔍 logo testi** düğmesi var.
Ayrıca onay penceresi dışarı tıklanınca asılı kalan promise hatası düzeltildi.

## v2.8'de ne düzeldi (cihaz logu + bugreport kanıtlı)

Sahadaki `force_landscape_20261029-194456.log` ve `bugreport` tek tek tarandı.
Bulunan **6 gerçek hata** ve yapılan düzeltmeler:

| # | Hata (kanıt) | v2.8'de |
|---|---|---|
| 1 | Uygulama ekranın **ortasında** kalıyordu: `MiuiEmbeddingWindow: MID_BOUND=SkRegion((719,0,2162,1800))`, `MiuiActivityEmbeddingController: ... in middle`, `ro.config.miui_activity_embedding_enable=true`. Bu, AOSP letterbox'ı **değil**; compat/oran ayarları bunu kaldırmıyor. | `resetprop -n ro.config.miui_activity_embedding_enable false` + modül `system.prop`; `post-fs-data.sh` açılışta yazıyor. WebUI **Durum** sekmesinde anahtar + gerçek durum rozeti. |
| 2 | "Ekrana yay" seçilse bile yaymıyordu: `webui: ekrana-yay AC ... -> MIN:am-nokill LARGE:am-nokill P-ONLY:am-nokill` yazıp **doğrulama yapmadan** başarı sayıyordu. | Her uygulamada `am compat state` ile **sistemden doğrulama**; tutmazsa `fail` yazar. Sağlayıcı patlarsa `cmd platform_compat`'a düşer. |
| 3 | Uygulamaya özgü zorlama **kapatılamıyordu**: `webui: ... -> never` yalnızca listeye yazıyor, uygulanmış katmanı geri almıyordu. | `never`/`auto` seçilince `fl_revert_pkg`: letterbox + ekrana-yay katmanı geri alınır, listelerden çıkarılır, uygulama durdurulur — **anında**. |
| 4 | `fl_embed_check` hep `?` dönüyordu (paket adı ile `MID_BOUND` **aynı satırda** aranıyordu; gerçek dökümde ayrı satırda). Bu yüzden "ortada kaldı" hiç tespit edilemiyordu. | Pencere **blok blok** okunur (awk). `pencere|orta|1443x1800` / `pencere|tam` / `pencere|?`. |
| 5 | Sahte başarı: `am compat ... -> Failed transaction (2147483646)` hata sayılmıyor, 87/87 "uygulandı" rakamı state dosyasından geliyordu. Ayrıca tur sonunda yanlış `DIKKAT - hedef var ama tek paket islenmedi` uyarısı çıkıyordu (kuyruk zaten boşken). | Hata desenleri genişletildi, `fl_change_state` ile gerçek durum okunuyor; uyarı yalnızca **gerçekten** kalan paket varsa yazılıyor. |
| 6 | Eski hatalar (11:45–11:54, 16:34) 19:44 raporunda **güncel** gibi görünüyordu. | Başarılı turda `compat_errors.txt` sıfırlanır; raporda yalnız o turun hataları. |

Yeni komutlar (WebUI arka ucu): `verify <paket>`, `revert <paket>`, `miui-embed off|on`.

## v2.1'de düzeltilen asıl hata

Sahadaki v1.9/v2.0 logunda **letterbox katmanı hiç uygulanmamıştı**
(`compat paket: 0`, `kalan: 94`) ve *neden* uygulanmadığı logda yazmıyordu:
test başarısız olunca katman sessizce atlanıyor, tek bir hata bile kaydedilmiyordu.

v2.1'de:

1. Her paket **3 yöntemle** denenir:
   `am compat enable --no-kill` → `am compat enable` → `cmd platform_compat enable`
2. İlk 3 pakette ve tur sonunda sonuç **sistemden doğrulanır**
   (`dumpsys platform_compat`); komut “başarılı” dese bile sistemde görünmüyorsa hata sayılır.
3. Her paketin sonucu `compat_status.txt`, son 20 hata `compat_errors.txt` dosyasına yazılır
   ve WebUI'de **görünür**.
4. Döngü hatada sessizce kesilmez: üst üste 8 hatada durur ve bunu yazar.
5. Süre bütçesi testten **sonra** başlar; tur başına en az 6 paket garanti.
6. Açılışta `system_server` kararlı değilse (sahadaki logda 22 saniyede 4 kez
   yeniden başlamıştı) not düşülür ve işlem yine de yapılır.

## Karar mantığı (tek nokta)

```
never.txt (Dokunma)        → dokunma
sistem paketleri           → dokunma
force.txt (Yatay)          → zorla          ← WebUI'de “Yatay”
yerleşik 22 oyun listesi   → dokunma        (zorla dersen yine zorlanır)
mod: akıllı     → yönü dikey/bilinmiyor olanlara zorla
     seçtiklerim→ sadece force.txt
     hepsi      → iki “dokunma” hariç herkes
     kapalı     → compat katmanı durur (yön katmanı çalışır)
```

Her kararın gerekçesi `webui.sh apps` çıktısının 5. sütununda döner
(“sen seçtin”, “oyun listesinde”, “kendi dikey çalışıyor” …).

## Dosyalar

| yol | ne |
|---|---|
| `/data/adb/force_landscape/config.conf` | ayarlar (WebUI okur/yazar) |
| `/data/adb/force_landscape/force.txt` | “Yatay” listesi |
| `/data/adb/force_landscape/never.txt` | “Dokunma” listesi |
| `/data/adb/force_landscape/compat_applied.txt` | uygulandığı doğrulanmış paketler |
| `/data/adb/force_landscape/compat_status.txt` | paket → ok/hata + yöntem (v2.1) |
| `/data/adb/force_landscape/compat_errors.txt` | son 20 hata (v2.1) |
| `/data/adb/force_landscape/landscape.log` | modül logu |
| `/data/adb/force_landscape/state.txt` | son uygulama özeti |
| `/sdcard/force_landscape_rapor.txt` | tanı raporu |
| `/sdcard/Download/force_landscape_<tarih>.log` | paylaşılabilir rapor+log |

## Geri dönüş

- **Duraklat**: WebUI → Ayarlar → Duraklat (veya `touch /data/adb/modules/force_landscape_all/disable` + reboot)
- **Tümünü geri al**: WebUI → Ayarlar → Tümünü geri al (compat overrides `am compat reset` ile silinir)
- **Kaldır**: yöneticiden kaldır; XML yedeği `.fl.bak`'tan geri konur

## Sınırlar (dürüst)

- Android 13'te uygulama bazlı **yön** override'ı yok; “Yatay” seçimi resize izni verir,
  yönü ekran katmanı belirler.
- Compat katmanı ROM'a bağlı. `am compat` engellenirse XML yedeği kullanılır ve
  **bir sonraki açılışta** etkili olur.
- Tam ekrana yayılmayı desteklemeyen oyunlara zorlanmaz (yerleşik 22 oyun + “Dokunma”).

## v2.2'de WebUI için yapılan düzeltmeler (saha bulgularına göre)

Saha logu şunu gösterdi: `compat paket: 0 · kalan: 0` ve `compat_applied.txt` 0 bayt.
Yani letterbox katmanı cihazda hiç uygulanmamış; ayrıca uygulama listesi WebUI'de
boş geliyordu. Nedenleri ve çözümleri:

| Belirti | Kök neden | v2.2 çözümü |
|---|---|---|
| Hiçbir uygulama görünmüyor | Liste her açılışta 100+ paket için tek tek `pm`/`dumpsys` çağırıyordu; yavaş cihazda köprü zaman aşımına takılıp boş dönüyordu | Liste `apps_cache.txt` içinde önceden hesaplanır; WebUI sadece okur (~0.05 s) |
| Aşırı geç açılıyor | Açılışta `status` (wm+dumpsys+logcat) ve tam paket taraması seri çalışıyordu (sim: 2,5 s + 2,5 s) | `status` artık `state.txt`'ten okur; canlı ölçüm yalnız "Yenile"de (`status-full`) |
| Log sayfası çalışmıyor | Log sekmesi açılırken ağır `test-ham` çıktısı aynı çağrıda alınıyor, hata olursa tüm sayfa düşüyordu | Log ayrı, ham test ayrı düğme; hata yakalanıp kutu içinde gösterilir |
| `force: 0 / never: 0` satırı bozuk | Sayaç biçimlendirmesi | Düzeltildi |
| `state.txt.tmp` artık kalıyor | Yeniden adlandırma yarıda kalıyordu | `state.txt.tmp` her yazımdan sonra silinir |

Ek olarak: uygulama listesi hata verirse "Yeniden oluştur" düğmesi (`apps-refresh`)
ve sebep metni gösterilir; mod değişince liste kararları otomatik tazelenir.

## v2.3 — WebUI baştan yazıldı

Saha geri bildirimi: "cihaz bilgisi düzgün gelmiyor, uygulama bilgisi yok, log çalışmıyor".
Nedenleri ve çözümleri:

| Sorun | Kök neden | v2.3 |
|---|---|---|
| Cihaz bilgisi eksik/yanlış | Durum ekranı yalnız `state.txt`'ten okuyordu; o da sadece "Uygula" sonrası yazılıyordu | Yeni **Cihaz** sekmesi: model, ROM/MIUI, çekirdek, ABI, SELinux, RAM, /data, pil+sıcaklık, ekran, açılış süresi — hepsi canlı tek çağrıda |
| Uygulamalar sadece paket adı | Liste yalnız paket/karar içeriyordu | Her satırda okunabilir ad, sürüm, boyut, badge'ler; **ⓘ** ile sürüm/hedefSDK/kurulum tarihi/APK yolu/çalışan PID/başlatıcı |
| Aç/kapa yok | — | **Uygulamayı aç / Durdur / ↻ Yeniden başlat** (ayarın etkili olması için gerekli) |
| Log sayfası çalışmıyor | Tek `tail` çağrısı + sekme her yenilendiğinde içerik sıfırlanıyordu | 6 kaynak (modül/hatalar/paket durumu/sistem logcat/test/yön), satır sayısı, arama, kopyala, dosyaya kaydet, **log tanısı**; `tail` bağımlılığı kaldırıldı (saf awk) |
| "Uygula" ekranı kilitliyordu | 96 paket tek çağrıda, süre 1-2 dk | **Parçalı uygulama**: 12'lik dilimler + ilerleme çubuğu; sayfa donmaz, "kalan" canlı düşer |
| 8 hatada tüm tur iptal | `break` | Hata → yöntem değiştir (am-nokill → kalıcı enable → platform_compat) → 20 hatada dur, XML yedeği hazırla, hata metinlerini sakla |
| Otomatik yenileme odak/scroll bozuyordu | 8 sn'de tüm sekme yeniden çiziliyordu | Otomatik yenileme yalnız başlık rozetlerini günceller; sekme içeriğine dokunmaz |
| Dosya izinleri | `compat_errors.txt` gibi dosyalar 0600 idi | Tüm durum dosyaları 0644 (`fl_perms_fix`) |

Ek olarak: Tanı ekranı (13 kontrol, tek ekran), yedek al/yükle, toplu seçim
("görünenleri zorla/bırak"), sıralama, filtre çipleri, karanlık/aydınlık tema.

## v2.4 — WebUI'nin "görünmez" çalışma hatası düzeltildi

WebUI'nin kaynağını (KernelSU `WebViewInterface.kt`, `WebViewHelper.kt`) okuyup dört
gerçek uyumluluk hatası buldum ve kapattım:

| Bulgu | Neden önemliydi | v2.4 |
|---|---|---|
| `ksu.exec(cmd)` **senkron** ve JS iş parçacığını bloklar | "Uygula" 12 pakette 8–10 s duruyordu: dönen ikon, ilerleme çubuğu, tıklama **hiç çalışmıyordu** → "dondu" hissi | `exec(cmd, opts, callback)` asenkron imzası kullanılır; arayüz **hiç donmaz**, ilerleme canlı akar |
| Bazı yöneticiler string, bazıları `{errno,stdout,stderr}`, bazıları Promise döner | Tek tip kabul etmek "boş çıktı" demek | Açılışta **zararsız `ping`** ile köprü tipi tespit edilir; uygun yol seçilir (asenkron → senkron → promise). Desteklenmiyorsa ekranda açıkça yazar |
| `window.confirm()` WebView'de desteklenmeyebilir (sessizce `false` döner) | "Tümünü geri al" gibi düğmeler **hiçbir şey yapmıyormuş** gibi görünür | Kendi onay penceremiz yazıldı; kodda `confirm(` çağrısı yok |
| Sayfa `https://mui.kernelsu.org` üzerinden servis edilir, `location.pathname` gerçek yol değil | Sabit dizin yolu yanlış olursa her komut başarısız | Dizin `ksu.moduleInfo().moduleDir`'den alınır |
| `pm list packages -3` bazı ROM'larda boş | "Hiçbir uygulamayı görmüyor" | 4 kademeli yedek: `-3` → `-3 --user 0` → `cmd package list packages -3` → `pm list packages` |

Ayrıca **gerçek uygulama adı ve ikonu**: yönetici `getPackagesInfo` veriyorsa
uygulamanın kendi adı ve ikonu (`ksu://icon/<paket>`) kullanılır; vermiyorsa
yerleşik isim sözlüğü + harf avatar devreye girer. Durum sekmesinde hangi köprünün
kullanıldığı yazar.

## v2.5 — "87 paket" saha hatasının KÖK NEDENİ bulundu ve kapatıldı

Cihazdan gelen bugreport (`logcat.txt`) incelendi. Kanıt:

```
avc: denied { read } for path="/data/adb/force_landscape/compat_queue.txt"
     scontext=u:r:system_server:s0 tclass=file permissive=0
cmd: Failure calling service activity:   Failed transaction (2147483646)
cmd: Failure calling service platform_compat: Failed transaction (2147483646)
```

**Ne oluyordu:** Paket listesi `while ... done < compat_queue.txt` ile okunuyordu.
Döngünün içinden çağrılan `am` / `cmd` / `dumpsys` süreçleri **stdin olarak bu
dosyayı devralıyordu**. `system_server` alanında çalışan bu süreçler `/data/adb`
altını okuyamadığı için SELinux okumayı reddediyor, binder çağrısı
"Failed transaction" ile düşüyordu. Sonuç: **88 paketten 1'i uygulanıyor,
87'si "kalan" kalıyordu.**

**Çözüm:** Tüm sistem komutları (`am`, `cmd`, `pm`, `dumpsys`, `settings`, `wm`,
`device_config`) artık `</dev/null` ile çalışıyor. Ayrıca kuyruk döngüleri
`for` döngüsüne çevrildi (ikinci savunma katmanı). `fl_restore` içindeki
`am compat reset` döngüsü de aynı hatadan etkileniyordu, o da düzeldi.

Aynı turda bulunan küçük hatalar:

| Hata | Etki | Düzeltme |
|---|---|---|
| Log satırı `cut -f2` ile paket adını basıyordu | "compat HATA com.x -> com.x" (hata metni kayıp) | `cut -f3-` → gerçek hata metni |
| Tur kesildiğinde tek satır ipucu | Sebep belirsiz kalıyordu | Ayrı satırda son hata metni + Tanı yönlendirmesi |
| İlk uygulamadan önce Durum sekmesi boştu | Ekran/ignore alanları boş görünüyordu | `state.txt` boşsa **canlı** değer okunur |

## v2.7 — "ekrana yay" modu + cihaz bilgisi ve logo düzeltmeleri

Sahadan gelen ekran görüntüleri (v2.5 kurulu) ve iki modül logu satır satır
incelendi. Bulunan ve kapatılan hatalar:

| Bulgu | Kök neden | v2.7 çözümü |
|---|---|---|
| Cihaz sekmesi tamamen `—`, `Android —(API —)`, `NaN °C`, `Ekran yönü ?` | `boot()` **`loadSys()` çağırmıyordu**; `SYS` boş nesneyle çiziliyordu | Açılışta `loadSys()`; ayrıca Cihaz sekmesi boşsa kendisi yükler |
| Eksik alanlar çöp değerle yazılıyordu | `g()` yardımcısı boş değeri `—` yapıp satırı yine çiziyordu | Değer yoksa **satır hiç çizilmez**; hiç değer yoksa uyarı + tek "oku" düğmesi |
| Sıcaklık `NaN °C` | `Number("")/10` | `parseInt` + `isNaN` kontrolü, `>250` ise /10 |
| Uygulama listesinde harf kutusu | Logo yalnız `ICONS && MG[pkg]` iken deneniyordu; WebUIX'te `listPackages` yok | Logo **koşulsuz** denenir: `ksu://icon/<pkg>` → `mui://icon/<pkg>` → harf |
| Satır düğmeleri okunaksız (`⚡ ⏹ ⓘ`) | Sadece emoji, etiket yok | İkon + alt etiket: `↔️ yatay`, `⤢ yay`, `🚫 bırak`, `ℹ️ bilgi` |
| Tanı raporunda `uygulanan mod: unknown`, `ignore-orientation: unknown` | Rapor `tani`/`report` ayrı kabukta üretiliyor, `FL_MODE` orada boş | Rapor `state.txt`'ten son bilinen değeri okur |
| Arayüzde `v2.3` / `v2.5` etiketleri | Eski sabit metinler | Hepsi `v2.7` |

### Yeni: uygulama başına "EKRANA YAY" (komple yatay / tam ekran)

Her uygulama satırındaki **⤢ yay** düğmesi (ve ayrıntı penceresindeki aynı düğme)
o uygulamanın letterbox bantlarını kaldırır:

| Katman | Ne yapar |
|---|---|
| `OVERRIDE_MIN_ASPECT_RATIO` | Kapıcı — oran override'larının hepsi buna bağlı |
| `OVERRIDE_MIN_ASPECT_RATIO_LARGE` | Uygulamanın "en fazla 16:9" sınırını kaldırır → ekran boydan boya dolar |
| `OVERRIDE_MIN_ASPECT_RATIO_PORTRAIT_ONLY` | **kapatılır** (yatay tutarken override işlesin) |
| `OVERRIDE_ANY_ORIENTATION_TO_USER` | Android 14+ : yön + boyut + oran kısıtlarını komple kaldırır |

- Üç yöntem sırayla denenir: `am compat --no-kill` → `am compat --no-kill` (sondaki bayrak biçimi) → `cmd platform_compat`.
- Başarısız olursa **liste dışına alınır**, uygulama eski hâlinde kalır; hiçbir şey bozulmaz.
- Ayar anında geçerli olsun diye uygulama kapatılır (`am force-stop`) — bir sonraki açılışta tam ekran.
- Geri alma: her change id için `am compat reset` + `PORTRAIT_ONLY` varsayılana döner.
- Liste: `/data/adb/force_landscape/appmode.txt` · durum: `appmode_state.txt`
- Toplu geri alma: WebUI → Ayarlar → **Geri al** (`restore`) ekrana-yay katmanını da sıfırlar.

### Komutlar (webui.sh)

```sh
webui.sh appmode <paket> on|off     # tek uygulama aç/kapat (OK|paket|on|yöntem)
webui.sh appmode-list               # #appmode|<sayı> + paket|durum satırları
webui.sh apps                       # 10. sütun artık "ekrana yay" (0/1)
```

### Test

`node uitest/run27.js` → 66 yeni kontrol (cihaz bilgisi, logolar, düğmeler,
appmode aç/kapat, apply entegrasyonu, tanı raporu, geri alma, sözdizimi).
Eski paketler: `run24.js` (151), `run25.js` (432), `run25b.js` (7) → toplam **656 kontrol**.

## v2.7.1 — kendi denetimimde çıkan 6 düzeltme

v2.7'yi teslim ettikten sonra kodu baştan sona bir daha taradım. Bulunanlar:

| Bulgu | Neden önemliydi | Düzeltme |
|---|---|---|
| Logo `onerror` **satır içi** nitelikteydi | Bazı WebUI çalıştırıcıları CSP ile satır içi işleyicileri engeller → logo gelmezse **kırık resim simgesi** kalabilirdi | Belge düzeyinde `error` (yakalama fazı) ve `load` dinleyicisi eklendi; iki şema da tutmazsa **mutlaka** harfe düşer |
| Logoların gerçekten gelip gelmediği görülemiyordu | "Logo var mı?" sorusu tahmine kalıyordu | Durum sekmesine **"şu an: N logo yüklendi, M harfe düştü"** sayacı eklendi |
| `fl_appmode_count` boş dosyada `grep -c` → `"0\n0"` döndürüyordu | `state.txt`'ye fazladan satır, `restore-backup` çıktısında bozuk alan | `case` ile tek satıra sabitlendi |
| Yedekleme "ekrana yay" listesini taşımıyordu | Telefon değiştirince/geri yükleyince seçimler kaybolacaktı | `backup` / `restore-backup` artık `appmode|paket` satırlarını da taşıyor (çıktı: `OK|force|never|appmode`) |
| Her açılış kontrol noktasında katman baştan yazılıyordu | 20 seçili uygulama × 3 kontrol × 4 komut = gereksiz binder trafiği | Zaten açıksa tek sorgu ile geçiliyor (ölçüldü: appmode için 0 komut) |
| Yapılandırılmış `tani` ekrana-yay katmanını görmüyordu | "Katman gerçekten etkin mi?" cevabı yoktu | `KONTROL|OK|ekrana-yay|N/M paket etkin` satırı eklendi |


## v2.14.0 — dogrulanmis duzeltmeler

- **"Yatay zorla" artik gercekten uygular:** tek pakete tum katmanlar (letterbox + MIUI mailbox kapatma + yatay kurali + ekrana-yay) uygulanir, her katman ayri olculur ve sonuc ekranda gosterilir. Eskiden sadece listeye yaziliyordu.
- **Yon butonlari dogru calisir:** "Sensor" eskiden sensori kapatip ekrani dikeye kilitliyordu. Artik yon tek fonksiyondan uygulanir, geri okunur; "Otomatik" secenegi de calisir. `ROTATION_MODE=off` artik yeniden basinca kilide donmez.
- **MIUI kurallari dogru dosyaya yazilir:** Android 13'te yetkili dosya `/data/system/cloudFeature_*` kopyasidir; /product erofs salt-okunurdur. Kurallar cloud dosyasina yazilir, geri okunarak dogrulanir.
- **Katmanlar birbirinden bagimsiz:** "mailbox kapat" artik yatay kuralini silmez; "yay kapat" mailbox kuralini silmez.
- **Kaldirma/geri-al eksiksiz:** sadece mailbox'i degismis paketler de temizlenir; compat XML'i sifirdan yazilmaz, ilk yedek (.fl.bak) geri konur.
- **Paketleme:** `skip_mount` kaldirildi; ZIP artik tum scriptleri calistirilabilir (0755) icerir, izleyici (watch.sh) sessizce olmez, baslamazsa log'a yazar.
- **Yedekleme:** mailbox listesini de tasir; geri yukleme listeleri sadece yazmakla kalmaz, gercekten uygular.

## Dogrulama durumu (dürüst not)

Bu surum, gercek komut ciktilarini taklit eden bir test ortaminda (sahte `wm/settings/am/dumpsys/pm/getprop`) 37 uctan uca senaryo ile dogrulandi: yon modlari, tek-uygulama zorla, toplu secim, mailbox ac/kapat, yedekle/geri-yukle, hepsini-geri-al, kaldirma. Bu, cihaz testi DEGILDIR. Kesin kanit: kurulumdan sonra WebUI → uygulama detayinda **"kesin test"**; pencere tam ekransa modul calisiyor demektir. Kural dosyalarinin MIUI tarafindan okundugunun kaniti icin bir kez yeniden baslat gerekebilir (WebUI bunu acikca soyler).


## v2.15.0 — tum WebUI butonlari tek tek denetlendi

- WebUI'daki **47 dugme/komut** tek tek arka ucuyla eslestirildi, her biri test ortaminda calistirildi.
- "HER SEYI UYGULA" artik paketi listelere ekler ve letterbox katmanini da yazar (eskiden acilista yeniden uygulanmiyordu); "HER SEYI GERI AL" paketi Force listesinden de cikarir (eskiden sonraki Uygula geri zorluyordu).
- Genel yatay kilidi (Kontrol > kilit) artik sonucu dogrular; basarisizsa "oldu" demez.
- Izleyici WebUI'dan acilirken de calistirilabilirlik yedegi kullanir; baslamazsa loga yazar.
- Ekrana-yay basarisizliginda kismi compat degeri temizlenir.
- MIUI kural yenileme, alicisi olmayan yayini "basarili" saymaz; kilit modunda ekranin gercekten yatay oldugu olculur.
- Arayuz surum etiketi (UI_VER) guncellendi; Durum sekmesindeki sahte "modul farkli" uyarisi giderildi.

## Dogrulama durumu (dürüst not)

Surum, gercek komut ciktilarini taklit eden test ortaminda **47 uctan uca senaryo** ile dogrulandi (yon modlari, tek/toplu zorla, HER SEYI UYGULA/GEMI AL, mailbox, kilit, izleyici ac/kapat, yedekle/geri-yukle, hepsini-geri-al, kaldirma). Bu cihaz testi DEGILDIR; kesin kanit WebUI > uygulama detayi > "kesin test"tir.


## v2.16.0 — ASIL DUZELTME: uygulamaya ozel katman artik CANLI uygulaniyor

Arastirma ile bulundu (sothx/coolapk; Xiaomi muhendislerinin hata ayiklama komutlari): MIUI'nin kendi servisi kurallari calisma aninda uygular:

```
cmd miui_embedding_window update-rule  <paket> fullRule::nra:cr:rcr:nr
cmd miui_embedding_window set-fixedOri <paket> disable::true
```

- v2.15 ve oncesi yalnizca kural **dosyasi** yazip "hemen gecerli" diyordu. MIUI bu dosyalari acilista okudugu icin, yeniden baslatmadan (ve bazen hic) uygulamada degisiklik gorunmuyordu. "Uygulamaya ozel hicbir sey calismiyor" sikayetinin sebebi buydu.
- Artik her per-app islemde **dosya (kalicilik) + canli servis komutu (aninda etki)** birlikte kullanilir; geri alma da canli yapilir (`set-fixedOri disable::false`). Sonuc satirinda `canli=ok|yok` acikca yazar; servis yoksa dosya yolu calismaya devam eder ve bu da acikca soylenir.
- `am compat` deneme sirasi belgeli sozdizimine cevrildi (`am compat enable <degisim> <paket>` once); ROM'un "Usage:" reddini basari sanma ihtimali kaldirildi.
- Kesin test artik "hemen gecerli" iddiasinda bulunmaz; kuralin dosyada oldugunu soyler, canli olcum ayridir.

## Dogrulama durumu (dürüst not)

57 uctan uca senaryo sahte-komut ortaminda gecti; yeni senaryolar canli servis komutlarinin dogru pakete, dogru bicimde gittigini ve servis yokken davranisin durust oldugunu kapsar. Cihaz testi DEGILDIR. Cihazdaki kesin kanit: WebUI > uygulama > "kesin test" + logdaki `miui-cmd:` satirlari (servisin cevabi orada birebir yazar).


## v2.17.0 — sahte basari avcisi

- `fl_force_apply` artik kritik katman basarisizsa hata kodu doner; **Yatay zorla / apply-one / toplu secim** bunu `HATA` olarak bildirir (eskiden basarisizlik ozet satirinin icinde gizleniyor, arayuz "oldu" diyordu).
- `mode` yazdigini geri okur; `restore` sonucu olcer; toplu secim ve yedek yukleme arka uc hatasini yutmaz; ekrana-yay basarisizligi loga gercek sebebiyle duser.
- Ilke: her dugme ya sonucu **olcer** ya da olmadigini **soyler**. Calismayan sey landscape.log'a sebebiyle yazilir (MIUI servis cevaplari `miui-cmd:` satirlarinda birebir gorulur).

## Dürüstlük notu

Bu modul cihazi elimde olmadan "%100 calisir" sozu veremez; kimse veremez. Verilebilecek soz sudur: **bir sey calismazsa arayuz onu basari gibi gostermez ve log gercek sebebi yazar.** Cihaz kaniti: WebUI > uygulama > kesin test; tutmazsa Kayıtlar > log dosyasini gonder, `miui-cmd:` ve `force-apply:` satirlari sorunu satiriyla gosterir.


## v2.18.0 — ASIL kök neden bulundu (motor tuzağı)

Saha geri bildirimi ("tek uygulamada bile per-app çalışmıyor") üzerine kök neden bulundu:

- **v2.8'den beri** `post-fs-data.sh`, varsayılan config'deki `MIUI_NO_EMBEDDING=true`
  yüzünden **her açılışta** `ro.config.miui_activity_embedding_enable=false`
  yazıyordu. Yani modül, MIUI'nin kural motorunu kendisi kapatıyordu.
- Ama uygulamaya özel yatay kurallarını (`embedded_rules_list` /
  `fixed_orientation_list`) işleyen servis **tam olarak bu motor**.
  Motor kapalıyken dosyaya yazmak da (v2.14) canlı servis komutu da
  (v2.16) hiçbir işe yaramaz. Global yön kilidi motordan bağımsız
  çalıştığı için arıza sürümler boyu gizlendi.

Düzeltme:

- Açılışta: `force.txt`/`mailbox.txt` listesinde uygulama varsa motor
  **açılır** (config ne derse desin); liste boşsa prop'a dokunulmaz.
  `config.default.conf` varsayılanı da `false` oldu.
- Her "Yatay zorla" özetinde artık `motor=...` alanı var; motor kapalıysa
  açma denenir (`kapali-acilamadi` ise işlem HATA döner, sahte başarı yok).
- Kesin test **ADIM0**'da motoru ölçer: kapalıysa sebebi açıkça yazar.

Not: Motor açılışta servis tarafından okunmuş olabilir; bu yüzden
güncellemeden sonra **bir kez yeniden başlatmak** gerekir. Sonrasında
kesin test ADIM0 `OK|miui-motor|acik` göstermeli.


## v2.19.0 — MIUI protokolüne tam uyum + simetrik geri alma

Saha kanıtı (v2.18.0, Xiaomi Pad 6): motor artık AÇIK, fullRule dosyada,
mailbox kapalı — ama geri alma tutmuyor ve "Yatay zorla" oyunu
sağa-sola geriyordu. Kod denetiminde bulunanlar:

- `fl_revert_pkg`, oran katmanını (`fl_appmode_clear`) hiç geri
  almıyordu → gerdirme kalıcı oluyordu. Artık çağrılıyor.
- "Yatay zorla" MIUI cihazda AOSP oran override'larını da yazıyordu
  (gerdirme bundan). Artık **MIUI'nin kendi protokolü** tek başına:
  embedded `fullRule` + fixedOri `disable="true"` dosyada,
  `cmd miui_embedding_window` ile canlı, motor açık. (sothx/coolapk
  reçetesi birebir.) Gerdirme yalnızca "ekrana yay"/"HER ŞEYİ UYGULA"da.
- Geri alma artık **geri okunarak** doğrulanıyor: MIUI dosyalarında
  satırımız kaldıysa işlem `HATA|miui-dosya-temizlenemedi` döner;
  canlı geri almanın sonucu loga yazılır (`canli-geri-al:`).
- "Kural kanıtı" çıktısına MIUI servisinin **cihazdan alınan kendi
  yardım metni** (`yardim|`) ve son canlı komut cevapları (`canli-log|`)
  eklendi — protokol uygunluğu tahmine değil cihaz çıktısına dayanır.
- Detay ekranındaki yanıltıcı "MIUI orta pencere: AÇIK — ortada kalır"
  satırı "MIUI motoru (kural servisi): AÇIK ✓" olarak düzeltildi;
  ortada kalma artık yalnız mailbox satırından okunur.


## v2.20.0 — cihaz logu kanıtıyla canlı geri alma düzeltmesi

Kullanıcının gönderdiği cihaz logu (`force_landscape_20261030-105956.log`)
şunları **kanıtladı**:

- Açılışta motor açık, servis ayakta; `update-rule` / `set-fixedOri`
  uygulama komutları cihazda `Update ... successful` dönüyor.
- **Ama** geri almada kullandığımız boş parametre reddediliyor:
  `update-rule <pkg> fullRule::` → `Error! Please check RULE::PARAM.`
  Yani canlı kural hiç düşmüyor, dosya temiz olsa bile servis
  belleğindeki kural yeniden başlatana kadar zorlamaya devam
  ediyordu. "Dokunma'ya basıyorum, eski hâline dönmüyor" şikâyetinin
  sebebi buydu (v2.19'daki `fl_appmode_clear` eksikliğiyle birlikte).

Düzeltme: canlı geri alma artık aday biçimleri tek tek deniyor,
servisin cevabını okuyor (`successful`/`Error`), ilk kabul edileni
kullanıyor ve hangisinin tuttuğunu loga yazıyor
(`canli-geri-al: ... bicim=...`). Hiçbiri tutmazsa sonuç
`canli=kismi-yeniden-baslat` diye açıkça söyleniyor; "tamamen geri
alındı" yalanı yok.

Log ayrıca şunu da doğruladı: WebUI üzerinden yapılan her işlem
(yazan her komut ve her hata) `landscape.log`'a düşüyor; log
dosyasındaki 10:58–10:59 satırları kullanıcının bastığı düğmelerle
birebir eşleşiyor. (Salt okuma amaçlı liste/durum sorguları loglanmaz.)


## v2.21.0 — log kapsamı tamamlandı

Kullanıcı sorusu: "WebUI'daki her işlem, her detay, her arka plan
işlemi loga düşüyor mu?" Denetim sonucu ve kapatılan boşluklar:

- **Giriş kaydı:** Yazma yapan her WebUI komutu, çalışmadan önce
  `webui-cmd: <komut> <argümanlar>` satırıyla loga girer.
- **Hata tuzağı:** Bir komut 0 dışında kodla biterse (handler yarıda
  ölse bile) EXIT trap'i `webui-cmd HATA rc=<kod>: ...` yazar.
  Okuma komutları (15 sn'de bir gelen `status` gibi) girişte sessizdir
  ama hataları yine tuzağa takılır. (`kesin`in rc=2'si "kanıt yok"
  demektir, hata sayılmaz.)
- **Rotasyon:** Log 192KB'a ulaşınca eskiden siliniyordu; artık
  `landscape.log.1`'e taşınır. "TAM LOG" çıktısı ikisini birden verir.
- **İzleyici:** `ölçülemedi` sonuçları da artık loga düşer.
- **Köprü hataları:** Komut betiğe hiç ulaşamazsa (ksu köprüsü errno /
  zaman aşımı), WebUI tarafı da hatayı loga yazmayı dener
  (`webui-js:` satırları).

Tek istisna: betiğin kendisi çalıştırılamıyorsa (dosya bozuk/izin),
o an shell tarafı log yazamaz; hata WebUI'da görünür ve JS tarafı
yazmayı dener. Bunun dışında başarısız olan hiçbir işlem logsuz kalmaz.


## v2.22.0 — SukiSU tarzı teşhis paketi + büyük log penceresi

Kullanıcı talebi: log paylaşımı SukiSU Ultra'daki gibi olsun, boyut
sınırı cömert olsun.

- Log rotasyonu artık **512KB × 4 kuşak** (~2MB pencere): dolunca
  zincir kayar, en eski kuşak düşer. Log görüntüleyici tüm kuşakları
  birleşik okur (eski satırlar da görünür).
- Yeni **`bundle`** komutu (Kayıtlar > "Teşhis paketini kopyala"):
  config + listeler + MIUI kural kanıtı + servisin yardım metni +
  seçili paketlerin kural satırları + durum + compat hataları,
  tek metin hâlinde. Bir şey bozulduğunda bu paketi göndermek yeterli.
- **Dosyaya kaydet (`save`)** artık: Tanı Raporu + Teşhis Paketi +
  TAM LOG (tüm kuşaklar, eskiden yeniye) — tek dosya, indirilip
  paylaşmaya hazır.

Not: Modül logu modülün kendi işlemlerini kapsar. Çekirdek/KernelSU
tarafının logları SukiSU'nun kendi "log paylaş"ındadır; ikisi
birbirini tamamlar.

## v2.23.0 — uygulamaya özel YÖN kilidi (asıl istek) + sessiz hata avı

Kullanıcı isteği: "sadece yatay zorla değil, tek uygulamayı **dikeye**
de kilitleyebileyim; serbest de bırakabileyim, üstelik reboot'tan sonra
kalsın." Ayrıca "bir yere dokununca tak diye etki etsin" şartı korunuyor.

### Yeni: uygulama başına ▮ dikey / ▬ yatay / ⤢ serbest

MIUI'nin **gerçek** şeması kullanılıyor (uydurma alan yok):

| seçim | `cloudFeature_fixed_orientation_list.xml` | `cloudFeature_embedded_rules_list.xml` |
|---|---|---|
| **dikey** | `disable="true" allPortrait="true"` | satır **silinir** (yatay kural dikeyle savaşmasın) |
| **yatay** | `disable="true" allPortrait="false"` | `fullRule="nra:cr:rcr:nr"` |
| **serbest** | satır **silinir** | satır **silinir** |

- Her yazımdan sonra dosya **geri okunur**; okunan değer istenenle
  uyuşmazsa `HATA|...` döner ve arayüz düğmeyi **eski hâline** alır.
  Yani "yazdım ama tutmadı" durumu asla "✓" görünmez.
- Aynı anda `cmd miui_embedding_window set-fixedOri/update-rule` ile
  **canlı** uygulanır (reboot beklemez), sonra uygulama `am force-stop`
  ile yeniden başlatılır → ekranda anında görünür.
- Seçim `yon.txt` içinde kalıcı (`pkg<TAB>dikey|yatay|serbest`), boot'ta
  `fl_yon_pass` + `fl_mb_reapply` tazeler.
- WebUI'de üç düğme: uygulama satırında (hızlı) **ve** uygulama detay
  penceresinde (açıklamalı). Dokunur dokunmaz düğme boyanır, işlem
  bitince gerçek durum doğrulanır.

### Düzeltilen gerçek hatalar (bu turda bulundu)

1. **"Dikey seçtim, reboot'ta yatay oldu"** — `fl_mb_reapply` boot'ta her
   kayıt için koşulsuz YATAY kuralı yazıyordu. Artık `yon.txt`'deki seçim
   okunup doğru kural yazılıyor; listede `yon` bilgisi olmayan paketler
   de tazeleniyor.
2. **`restore-backup` yedeği bozuyordu** — geri yükleme bloğu `_f`, `_n`,
   `_b` gibi genel adlar kullanıyordu; çağırdığı fonksiyonlar (POSIX sh'de
   değişkenler **globaldir**) bunları ezip döngüyü `0` paketine çeviriyor,
   log'a `force-apply(miui): 0` yazıyordu. Tümü `_rs_` önekli benzersiz
   adlara taşındı.
3. **Yedekte yön seçimi yoktu** — `uygulamayon|<pkg>|<yon>` satırları
   eklendi; geri yükleme `yon.txt`'yi yazıp her paket için gerçekten
   uyguluyor (`yon=N` çıktısı).
4. **Bilinmeyen komut sessizce "başarılı" dönüyordu** — artık
   `HATA|bilinmeyen-komut` + rc=1 (yazım hatalı düğme boş iş yapmaz).
5. **Kurulu olmayan paket kural dosyasını kirletiyordu** — `set`, `appmode`,
   `yon` artık paketi sorgular; yoksa `HATA|paket-kurulu-degil` döner ve
   XML'e **satır yazmaz**.
6. **Listelerdeki çöp satırlar** (`"0"` gibi) açılışta sessizce temizlenir
   (`fl_lists_clean`), ekleme yolunda da `fl_pkg_sane` denetimi var.
7. **`save`** dizin arıyor: `/sdcard/Download → /storage/emulated/0/Download
   → /sdcard → /storage/emulated/0 → modül dizini`.
8. **Rapor dosyası** `/sdcard` yoksa artık modül dizinine düşüyor (eskiden
   "Directory nonexistent" hatası verip raporu kaybediyordu).
9. **`fl_miui_cmd_revert`** aday sırası: önce `fullRule::default`, en sonda
   parametresiz çağrı (bazı sürümler parametresiz çağrıya kullanım metni
   basıp rc=0 dönüyor → "kural düştü" yanılgısı).
10. **`miui-embed off`** seçili paket varsa uyarı basıyor (per-app kurallar
    o servis kapalıyken işlenmez).
11. **`yon-list`** boş dosyada boş çıktı yerine `# kayit yok` dönüyor.

### Doğrulama (bu sürümde koşulan testler)

- `sh -n` tüm betikler · `node --check` WebUI JS: temiz.
- **48 komutluk uçtan uca regresyon** (`regress.sh`): 0 hata. Her koşuda
  listeler sıfırdan kurulur, tekrarlanabilir.
- **jsdom ile gerçek tıklama testi**: 100 ms içinde düğme yanıyor,
  arka uç `secim=dikey allPortrait=true` / `yatay → kural=nra:cr:rcr:nr` /
  `serbest → satırlar silindi`; hata yolunda düğme **eski hâline dönüyor**;
  7 sekme gezildi, JS hatası yok.
- **Boot zinciri**: `service.sh` sıfırdan koştu; reboot sonrası
  `com.whatsapp → yatay (fullRule=nra:cr:rcr:nr)`,
  `com.instagram.android → dikey (allPortrait=true)` **korundu**.
- **Yedek/geri yükleme**: force/never/appmode/mailbox/yon beşi de birebir
  geri geldi (`OK|1|2|1|yon=3`).

## v2.23.3 — "her düğme tek tek test edildi" turu (2 gerçek hata daha)

Bu turda arayüzdeki **37 `data-act` düğmesi + 14 kontrol anahtarı + detay
penceresindeki 15 düğme + 4 yön modu + 4 letterbox modu + 6 filtre + 3
sıralama** tek tek gerçekten tıklandı (jsdom, gerçek arka uç komutlarıyla).
**72/73 doğrudan geçti; kalan 1 de ayrıca elle doğrulandı.** İki gerçek hata
bu sayede çıktı:

### 1) İşlem sürerken TÜM SAYFA siliniyordu (`busyStart`)
`busyStart()` → `render()` → `paintBusy()` zinciri `#view.innerHTML`'i
"işlem sürüyor…" ile **eziyordu**. Sonuç:
- Uygulama detay penceresi, işlem sürerken **yok oluyordu** → kanıt/tam ekran/
  mailbox düğmelerine bastıktan sonra pencere kapanıyor, geri açmak gerekiyordu.
- Herhangi bir düğmeye basınca ekran bir an **boşalıyordu** ("bastım, bir şey
  olmadı" hissinin bir sebebi daha).

**Düzeltme:** "işlem sürüyor" artık `#busyLayer` adlı **ayrı katman**; sayfa
yerinde kalır, katman üstte yarı saydam durur. `render()` busy iken de çizer.

### 2) Yedek metni siliniyordu → "Yedekten yükle" hep "alan boş" diyordu
`backup()` metni `<textarea>`'ya yazıyordu, ama saniyeler içinde gelen
`render()` textarea'yı **yeniden çizip içeriği siliyordu**. Kullanıcı
"Seçimleri kopyala"ya basıp "Yedekten yükle"ye bastığında **her zaman**
"alan boş — önce yedek al veya yapıştır" görüyordu.

**Düzeltme:** metin `LAST_BACKUP` değişkeninde tutulur, render sonrası kutuya
geri konur, `backupLoad()` kutuyu boş bulursa `LAST_BACKUP`'tan okur.
Ayrıca `SHELL`/`SCRIPT` okunamazsa artık sessizce boş komut çalıştırmıyor.

### 3) Panoya kopyalama sessizce başarısız oluyordu
WebView'de `navigator.clipboard` çoğu zaman yok, `execCommand("copy")` sessizce
`false` döner. Eski kod sonucu **hiç kontrol etmiyordu** → "kopyalandı"
diyemiyor ama hiçbir şey de yapmıyordu. Artık: clipboard → execCommand →
**metni ekranda aç** (elle seç-kopyala). Böylece yedek/teşhis paketi her
koşulda eline geçer.

### Test kapsamı (bu turda gerçekten tıklananlar)

```
37 data-act düğmesi (apply, repair, test, diag, revertAll, kilitOff, watchOff,
  miuiEmbedOff, logoTest, save, report, appsRefresh, selectForce, selectSkip,
  letterboxReload, compatReset, rawtest, tamKontrol, kontrolReload, rulesReload,
  logreload, logauto, logcopy, bundlecopy, clearlog, logstat, sysReload,
  sysCopy, pause, restore, backup, restoreBackup, backupLoad, tani, ...)
14 kontrol anahtarı (Aç + Kapat)  ·  7 anahtar grubu doğrulandı
15 detay penceresi düğmesi (force/never/auto/yon/appmode/fullscreen/hepsi/
  hepsikapat/kesin/proof/softreboot/mailbox/open/stop/restart)
4 yön modu + 4 letterbox modu + 6 filtre + 3 sıralama
6 yön seçimi (2 paket × dikey/yatay/serbest) — arka uçtan doğrulandı
8 sekme · ekranda undefined/NaN yok · JS hatası yok
```

### 4) Yön seçimi kendini onarmıyordu (EN KRİTİK)
Sahadaki "dikey seçtim, sonra yatay oldu" şikayetinin bir sebebi daha bulundu:
`yon.txt` herhangi bir sebeple silinir/boşalırsa (temizlik betiği, modül
güncellemesi, elle müdahale, disk hatası) **seçim tamamen kayboluyor** ve
`mailbox.txt`'te kalan kayıt yüzünden uygulama **eski yatay kuralına** geri
dönüyordu. Bu sessizce oluyordu.

**Düzeltme — üç katmanlı kurtarma:**
1. Her yazımda `yon.bak` **ikinci kopya** tutulur.
2. `fl_prepare` → `fl_yon_restore`: `yon.txt` boşsa **yedekten** geri yükler.
3. `fl_yon_recover` + `fl_mb_reapply`: ikisi de yoksa **kural dosyalarından
   tahmin eder** (`allPortrait="true"` → dikey, `fullRule` var → yatay) ve
   seçimi yeniden yazar. Ayrıca seçimi olmayan bir kayıt için boot'ta
   `fl_miui_apply` çağrılmadan önce **dosyadaki gerçek duruma bakılır**;
   dosyada dikey kilidi duruyorsa yataya **çevrilmez**.

Test (uçtan uca):
```
A) yon.txt silindi            -> yon.bak'tan geri geldi, uygulama DIKEY kaldi
B) yon.txt + yon.bak silindi   -> kural dosyasindan kurtarildi, DIKEY kaldi
```## v2.23.3 — "komut denetimi" turu (5 gerçek hata daha)

Bu sürümde **sahte AOSP araçları katı hale getirildi**: yanlış komut artık `Error: ...` + rc=1
döner. Modülün 48 çağrısı bu araçlardan geçirildi. Sonuç: önceki "0 hata" sonuçları **yalan
söylüyordu**, çünkü sahte `wm`/`am` her şeyi kabul ediyordu.

### Bulunan ve düzeltilen hatalar

| # | Hata | Etkisi | Düzeltme |
|---|------|--------|----------|
| 1 | `fl_id_num()` **tüm** change adlarını aynı sayıya (`174042936`) eşliyordu | "Ekrana yay" açarken 3. adım (`disable PORTRAIT_ONLY`) **kapıcıyı kapatıyordu** → özellik kendi kendini iptal ediyordu, cihazda hiç çalışmıyordu | Resmî harita: kapıcı `174042980`, LARGE `180326787`, PORTRAIT_ONLY `203647190`, FORCE_RESIZE `174042936`, ANY_ORIENTATION `310816437` |
| 2 | `_w=$(fl_am_cmd …); _ok=$?` | POSIX sh'de atamadan sonra `$?` **her zaman 0** → komut reddetse de "başarılı" | `if _w=$(…); then _ok=0; else _ok=1; fi` |
| 3 | `fl_compat_dump` 1 **saniyelik** bayat önbellek + `fl_compat_verify` sadece paket adı arıyordu | "6 paket uygulandı, 0 hata" derken sistemde **0 override** vardı (yanlış pozitif) | Önbellek yalnız doğrulama içinde + change-id eşleşmesi aranıyor |
| 4 | `"*ENABLED*"` deseni `DISABLED` kelimesini de yakalıyordu | Her override "açık" okunuyordu → arayüz yalan söylüyordu | `DISABLED`/`LOGGED` önce denenir |
| 5 | `am compat enable/reset` **sayısal** id ister, kod id **adı** geçiriyordu | `Error: bad change id` → hiçbir override yazılmıyordu | `fl_id_num()` ad→sayı çevirisi, 4 çağrının tamamında |

### Tek noktadan okuma

`wm get-ignore-orientation-request` ve `wm …-multi-window-config` artık **tek yardımcıdan**
(`fl_get_ignore`) geçiyor, ROM farkı için yedekli (`-d 0` → argümansız).

### Kanıt (taklit cihaz, katı araçlar)

```
$ webui.sh appmode com.instagram.android on
OK|com.instagram.android|on|MIN:am LARGE:am P-ONLY:am dogrulandi:gate=enabled,large=enabled
   OVERRIDE_MIN_ASPECT_RATIO                enabled|am-compat    (174042980)
   OVERRIDE_MIN_ASPECT_RATIO_LARGE          enabled|am-compat    (180326787)
   OVERRIDE_MIN_ASPECT_RATIO_PORTRAIT_ONLY  disabled|am-compat   (203647190)
$ webui.sh apply        → 6 hedef → sistemde 6 gerçek override
$ ./regress.sh          → SONUC: 0 hata (48 çağrı, katı araçlar)
$ node final.js         → 1 hata / 73 (yalnız act:kontrolReload toast'i)
```

**Dürüst not:** bunlar taklit ortamda geçti. Gerçek Xiaomi Pad 6'da ilk kurulumdan sonra
`Tanı` sekmesindeki `compat_testi` satırı `CALISIYOR` demeli; demiyorsa change-id haritası
ROM'da farklıdır ve o satır bunu açıkça yazar.

## v2.23.3 — "kimse sessiz kalmaz" turu

Bu sürümün tek amacı: **hiçbir hata sessizce yutulmaz.** Her başarısız sistem komutu
en ince ayrıntısına kadar kayda düşer.

### Kaydedilen 9 alan (her hata için)

| Alan | Ne yazıyor | Örnek |
|------|-----------|-------|
| `t` | zaman (ms) | `2026-11-24T13:46:23.639` |
| `lv` | seviye | `ERROR` / `WARN` / `DENY` |
| `pkg` | ilgili paket | `com.spotify.music` |
| `cmd` | **tam komut** | `am compat enable 174042980 com.spotify.music` |
| `rc` | çıkış kodu | `1` |
| `out` | **ham çıktı** | `Error: bad change id` |
| `why` | **sebep (insan dilinde)** | "PlatformCompat bu change-id'yi tanımıyor…" |
| `fix` | **çözüm** | "FL_ID_GATE değerini config.conf'a yaz…" |
| `fn:ln` | **suçlu (kod yeri)** | `fl_am_cmd:3358` |

### Sebep motoru — tanınan kalıplar

`bad change id` · `unknown option/command` · `permission denied` · `read-only file system` ·
`no space left` · `failure/error/exception` · **sessiz çıktı (rc=0 + boş çıktı)**

Her biri için ayrı insan-dili sebep + yapılacak iş yazılır. Tanınmayan çıktı bile
"Beklenmedik çıktı — ham çıktı kayıtlı, geliştiriciye ilet" olarak düşer.

### Çökme tuzağı

`landscape.sh` sonuna `trap` kuruldu: `EXIT`, `INT`, `TERM`, `HUP`, `SEGV`, `BUS`, `ABRT`.
Betik çöker veya öldürülürse `crashes.log`'a şunlar yazılır: cihaz modeli, SDK, root uid,
SELinux, argv, **son bağlam (fonksiyon:satır)**, son 20 log satırı, son 5 olay, disk durumu.
`webui.sh` bu dosyayı kaynak olarak yüklediği için **WebUI süreci de aynı koruma altında.**

### Sessiz yutma kaldırıldı

Eskiden `fl_am_cmd` ve `fl_compat_try` hata yolunda **hiçbir şey yazmıyordu** — "neden
olmadı" sorusunun cevabı hiçbir yerde yoktu. Artık 4 yöntemin **her denemesi** tek tek
`WARN` olarak, hepsi başarısız olursa `DENY` olarak kaydedilir.

### WebUI: yeni "Olaylar" sekmesi

- Sekme üstünde **kırmızı sayaç** (kayıt varsa)
- Her kayıt kart: seviye rengi · zaman · paket · komut · ham çıktı · sebep · çözüm · kod yeri
- **Yenile** · **Crash raporu** · **Raporu kopyala** · **Temizle**
- Boşsa: "Kayıtlı hata yok" (yeşil) — yalan söylemez, gerçekten yoksa yok der

### Crash raporu (`crash-rapor`)

Tek dosya, tek komut — kullanıcı bunu kopyalayıp gönderebilir:

```
/sdcard/force_landscape_crash.txt
```

İçinde: sürüm · zaman · uptime · **cihaz** (model/device/MIUI/sdk/fingerprint) · ekran ·
SELinux · uid · PATH · disk · root yöneticisi · **çökme kayıtları** · **son 40 hata** ·
son 80 işlem logu · durum dosyaları özeti · son 10 olay (JSON).

### Kanıt (taklit cihaz)

```
$ webui.sh olay-say                    → 2
$ webui.sh status | grep hata_sayisi   → hata_sayisi|2 · cokme_var|1
$ webui.sh crash-rapor                 → OK|/sdcard/force_landscape_crash.txt|20155 bayt
$ node olay.js                         → 0 hata / 17 kontrol (Olaylar sekmesi + 4 düğme)
$ ./regress.sh                         → SONUC: 0 hata
```

Örnek gerçek kayıt:
```json
{"t":"2026-11-24T13:46:23.639","lv":"DENY","fn":"fl_am_cmd","ln":3358,
 "pkg":"com.spotify.music","cmd":"am compat enable badid com.spotify.music","rc":1,
 "out":"Error: bad change id: badid",
 "why":"PlatformCompat bu change-id'yi tanimiyor (ROM surumu farkli ya da id haritasi degismis)",
 "fix":"FL_ID_GATE / FL_ID_LARGE / FL_ID_PONLY degerlerini config.conf'a yaz; Tani > compat_testi satirina bak"}
```


