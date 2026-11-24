# Force Landscape — Komut Denetim Raporu (v2.23.3)

Xiaomi Pad 6 (pipa) · MIUI 14 (V140) · Android 13 (SDK 33) · SukiSU Ultra

Bu rapor, modülün sisteme gönderdiği **her komutun** gerçek AOSP 13 sözdizimiyle
karşılaştırılmasının sonucudur. Denetim yöntemi: sahte `wm` / `am` / `settings` /
`dumpsys` araçları **gerçek AOSP 13 kadar katı** yazıldı (yanlış komut → `Error` + rc=1),
sonra modülün bütün çağrıları bunlardan geçirildi.

---

## 1. Geçerli komutlar (doğrulandı)

| Amaç | Komut | Kaynak |
|------|-------|--------|
| Yönü serbest bırak | `wm user-rotation free` | `wm help` (12L/13) |
| Yönü kilitle | `wm user-rotation lock 0..3` | aynı |
| Uygulama isteğini yok say | `wm set-ignore-orientation-request [-d N] true\|false` | aynı |
| Aynı ayarı oku | `wm get-ignore-orientation-request [-d N]` | aynı |
| Rotasyonu kullanıcıya sabitle | `wm fixed-to-user-rotation [-d N] enabled\|disabled` | aynı |
| Çoklu pencere | `wm set-multi-window-config --supportsNonResizable -1\|0\|1` | `WindowManagerShellCommand.java` |
| Aynı ayarı oku / sıfırla | `wm get-multi-window-config` · `wm reset-multi-window-config` | aynı |
| Boyut / yoğunluk | `wm size` · `wm density` | `wm help` |
| Yeniden boyutlanabilirlik (kalıcı) | `settings put global force_resizable_activities 1` | AOSP ayar anahtarı |
| Otomatik döndürme | `settings put system accelerometer_rotation 0\|1` | AOSP |
| Kullanıcı rotasyonu | `settings put system user_rotation 0..3` | AOSP |
| Compat değişikliği aç/kapa | `am compat enable\|disable\|reset [--no-kill] <SAYISAL id> <pkg>` | `ActivityManagerShellCommand` |
| Compat durumu | `am compat state <SAYISAL id> <pkg>` | aynı |
| Compat durumu (yedek) | `cmd platform_compat <işlem> <SAYISAL id> <pkg>` | aynı |
| Görev boyutu | `am task resize <taskId> <l> <t> <r> <b>` | aynı |
| Compat dökümü | `dumpsys platform_compat` | `dumpsys -l` |

## 2. Düzeltilen hatalar (bu denetimde bulundu)

| # | Eski (hatalı) | Yeni (doğru) | Etki |
|---|---------------|--------------|------|
| 1 | `wm fixed-to-user-rotation -d 0 default` | `... disabled` | `default` geçersiz değer; "sensör" ve "kapalı" modları sessizce bozuluyordu |
| 2 | `am compat enable OVERRIDE_MIN_ASPECT_RATIO <pkg>` | `am compat enable 174042980 <pkg>` | `am compat` **sayısal** id ister; ad geçirilince `Error: bad change id` |
| 3 | Tüm change adları → `174042936` | Resmî harita (aşağıda) | **En büyük hata:** "ekrana yay" açarken 3. adım kapıcıyı kapatıyordu → özellik hiç çalışmıyordu |
| 4 | `case "*ENABLED*"` | `DISABLED` / `LOGGED` önce | `*ENABLED*` deseni `DISABLED` kelimesini de yakalıyordu → her override "açık" okunuyordu |
| 5 | `_w=$(komut); _ok=$?` | `if _w=$(komut); then …` | POSIX'te atama sonrası `$?` **her zaman 0** → komut reddetse de "başarılı" |
| 6 | dumpsys 1 sn bayat önbellek + sadece paket adı | change-id eşleşmesi + tazeleme | "6 paket uygulandı" derken sistemde **0 override** vardı (yanlış pozitif) |
| 7 | `fl_am_cmd` hata yolunda sessiz | Her deneme `events.log`'a | "Neden olmadı" sorusunun cevabı hiçbir yerde yoktu |

## 3. Change-id haritası (resmî)

Kaynak: `developer.android.com` → *Device compatibility mode* + *Compatibility framework changes*

| Ad | ID | Not |
|----|----|-----|
| `FORCE_RESIZE_APP` | `174042936` | Yeniden boyutlanabilirlik |
| `OVERRIDE_MIN_ASPECT_RATIO` | `174042980` | **Kapıcı** — açık olmadan hiçbir oran override'ı işlemez |
| `OVERRIDE_MIN_ASPECT_RATIO_LARGE` | `180326787` | 16:9 (ekranı doldurma) |
| `OVERRIDE_MIN_ASPECT_RATIO_PORTRAIT_ONLY` | `203647190` | Varsayılan **açık**; yatayda override'ı engeller → kapatılır |
| `OVERRIDE_ANY_ORIENTATION_TO_USER` | `310816437` | Android 14+ |

ROM farklı numara kullanıyorsa `config.conf`'a yazılır:

```conf
COMPAT_ID=174042936
FL_ID_GATE=174042980
FL_ID_LARGE=180326787
FL_ID_PONLY=203647190
```

## 4. Test edilen / edilmeyen

**Test edildi (taklit cihaz, katı araçlar):**
- `customize.sh` → `post-fs-data.sh` → `service.sh` → `action.sh`: rc=0
- Temiz kurulum + reboot sonrası kalıcılık
- `webui.sh` tüm 48 komutu, regresyon: **0 hata**
- 73 UI kontrolü, Olaylar sekmesi + 4 düğme
- "Ekrana yay" aç/kapa: 3 change-id doğru yazılıyor, geri alma çalışıyor
- Toplu `apply`: 6 hedef → sistemde 6 gerçek override

**Test EDİLMEDİ (gerçek donanım gerekir):**
- Gerçek Xiaomi Pad 6'da `am compat` davranışı
- SukiSU'nun WebUI köprüsünde `ksu.exec` gecikmesi
- MIUI'nin agresif process kill'inin arka plan servisine etkisi
- Gerçek `wm` çıktı biçimleri (taklit cihazda üretildi)

> Gerçek cihazda ilk kontrol: WebUI → **Tanı** → `compat_testi` satırı `CALISIYOR`
> demeli. Demiyorsa change-id haritası ROM'da farklıdır; **Olaylar** sekmesi
> hangi id'nin reddedildiğini satır satır yazar.

## 5. Log altyapısı

| Dosya | İçerik |
|-------|--------|
| `landscape.log` (+ `.1`–`.3`) | İnsan-okur işlem günlüğü, 512 KB × 4 kuşak |
| `events.log` | Her komut denemesi, JSON satır |
| `errors.log` | Yalnızca başarısızlar, JSON satır |
| `crashes.log` | Çökme/sinyal: cihaz + root + SELinux + son log |
| `son_hata.txt` | Son hatanın tek satır özeti (WebUI okur) |
| `/sdcard/force_landscape_crash.txt` | `crash-rapor` komutunun tek dosyalık çıktısı |
