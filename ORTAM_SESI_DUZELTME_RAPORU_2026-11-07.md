# Ortam Sesi Düzeltme Raporu — 2026-11-07

## ⚠️ Önce en önemli bulgu: özellik `main` dalında DEĞİL

Haklıydın, özür dilerim — ilk bakışta aceleyle "yok" dedim. Ortam Sesi bu deponun
`main` dalında (`v2.4.340`, bu oturumun tabanı) gerçekten yok; ama depoda **var**:

- Özellik **`arena/54c8e317-kitsugi-beta`** dalında (v2.4.457 tabanı, PR #38 →
  `feat/golge-bahcesi-integration`) ve **`arena/161df920-kitsugi-beta`** /
  `v2.4.462` çizgisinde geliştirilmiş.
- Dosyalar: `core/music/*` (AmbientAudioEngine, AmbientAutoplayStarter,
  AmbientDownloadManager, AmbientPlaybackService…), `data/music/*`,
  `ui/screens/manga/components/MangaAmbientPanel.kt`.
- Telefona kurduğun sürüm bu çizgiden. Bu yüzden düzeltmeler **o kodun üzerinde**
  yapıldı ve `fix_ortam_sesi_v2.4.457.patch` olarak teslim ediliyor.
- Benim bu dalda sıfırdan yazdığım ilk deneme silindi (çalışma ağacı temiz).

### Yama nasıl uygulanır?

```bash
git checkout arena/54c8e317-kitsugi-beta   # veya v2.4.462 çizgisi
git apply fix_ortam_sesi_v2.4.457.patch
```

Yama 4 dosyayı değiştirir; `git apply --check` ile doğrulandı.

> **v2.4.462 çizgisinde:** Motor, indirme ve panel dosyaları aynen uygulanır.
> Yalnızca okuyucu hunku uymaz (461'de okuyucu değişti): aynı blok
> `MangaReaderScreen.kt`'de `musicContinueOnExit` LaunchedEffect'inin
> (satır ~900) altına elle yapıştırılır; ayrıca
> `import com.kitsugi.animelist.core.music.AmbientPlaybackService`
> (yoksa `androidx.compose.runtime.DisposableEffect`) eklenir.

---

## 1) "Çevrimiçi açıkken önce cihaz içi çalıyor, sonra duruyor, uygun parça başlamıyor"

**Kök neden** — `AmbientAutoplayStarter.start()`:
1. Cihaz içi preset başlıyor (tasarım gereği anında ses olsun diye).
2. Çevrimiçi aday bulununca **`engine.stopAllChannels()` hemen çağrılıyor** — yerel öldürülüyor.
3. `addChannel()` yalnızca yuvayı ayırır; hazırlık **asenkron**. Akış açılamazsa
   (Audius 403/anahtar, yanlış Archive dosyası, Openverse kotası, zaman aşımı)
   kanal `ERROR` durumunda kalıyor. `addChannel` çoktan başarıyla döndüğü için
   yerel geri konmuyor → **sessizlik**. Hata yalnızca Karışım sekmesinde görünüyordu.

**Düzeltme** (önce hazırla, hazır olunca değiştir):
- Çevrimiçi aday, yerel çalmaya devam ederken **sessiz (0f)** eklenir.
- Kanal durumu `PLAYING` olana kadar beklenir (12 sn tavan, 150 ms yoklama).
- Gerçekten çalıyorsa yereller bırakılır, çevrimiçinin sesi açılır.
- Açılamazsa aday kaldırılır, sıradaki aday denenir (en fazla 3).
- Hiçbiri açılmazsa yerel **kesintisiz** devam eder ve üst banda nedeni yazılır:
  *"Çevrimiçi parça açılamadı — cihaz içi ses devam ediyor"*.

## 2) "Okuyucudan çıkınca / geri gidince / kapatınca müzik dursun"

**Kök neden:** Motor + ön plan servisi okuyucu kapansa da çalmayı sürdürüyordu
(`musicContinueOnExit` tasarımı); okuyucuda çıkışı dinleyen hiçbir şey yoktu.

**Düzeltme** — `MangaReaderScreen`'e:
```kotlin
DisposableEffect(Unit) {
    onDispose {
        musicEngine.stopAllChannels()
        AmbientPlaybackService.stop(context)
    }
}
```
Geri tuşu, kapatma, başka ekrana geçiş — hepsinde durur. Bölüm geçişleri aynı
kompozisyonda olduğu için müzik kesilmez.

## 3) Panel düzeni (2. resim)

- **Çalma çubuğu her zaman üstte:** Başlık + kısayol şeridi (çal/duraklat, sustur,
  süre, durdur) ve sekme bandı artık içeriğin dışında, sabit.
- **Anahtarlar ayrı sayfada:** `Ortam sesi / Otomatik başlat / Sadece kulaklıkta /
  Sessiz modda çalma / Çevrimiçi kaynakları kullan` anahtarları eskiden yalnızca
  "Senin İçin" sekmesinin üstündeydi → sekme bandında yeni **Ayarlar** sayfasına taşındı.
- **Sağa-sola kaydırma:** İçerik `HorizontalPager` oldu; banttan dokunmak sayfayı
  kaydırır, sayfayı kaydırmak bandı senkron kaydırır.
  Sekmeler: Senin İçin · Ambiyans · Müzik · Çevrimiçi · Karışım · Profil · Süre · **Ayarlar**

> Not: Okuyucu, paneli `KitsugiSheetOrDialog(sheetGesturesEnabled = false)` ile
> açarsa yatay kaydırma sheet'in kapatma sürüklemesiyle çakışmaz; mevcut çağrı
> varsayılanı kullanıyor, cihazda çakışma hissedersen orada `false` geçmek yeterli.

## 4) "İndirdiğim ortam sesleri İndirmeler > Sesler'de görünmüyor" (3. resim)

**Kök neden:** `AmbientDownloadManager` indirmeleri yalnızca
**`filesDir/ambient/`** altına, **SHA-1 hash'li dosya adlarıyla** yazıyordu.
Sesler sekmesinin tarayıcısı (`scanDownloadedAudioItems`) ise
`DownloadStorage.audioRoots` (**Download/Kitsugi/Audio** vb.) + MediaStore'a bakıyor.
İki küme hiç kesişmediği için sekme hep **Sesler (0)** gösteriyordu.

**Düzeltme:** Her indirme artık anlamlı adlı bir kopyayla
**`Download/Kitsugi/Audio`** altına da yazılıyor
(`Kitsugi_Ortam_<Başlık> - <Alt başlık>.mp3`, MediaScanner ile taranıyor).
`exports.json` hangi kopyanın hangi sese ait olduğunu tutar; tekli silme ve
"hepsini sil" açık kopyayı da kaldırır. Kopyalama başarısız olursa indirme yine
başarılı sayılır (çevrimdışı çalma iç kopyadan sürer, kullanıcı mağdur olmaz).

> Eski (bu yamadan önce) indirdiklerin yalnızca iç alanda olduğu için başlıkları
> bilinmiyor; onları Sesler'de görmek için bir kez yeniden indirmen yeterli.

---

## Değişen dosyalar (yama içeriği)

| Dosya | Düzeltme |
|---|---|
| `core/music/AmbientAutoplayStarter.kt` | Hazır-olunca-değiştir geçişi, 3 aday denemesi, görünür geri bildirim |
| `core/music/AmbientDownloadManager.kt` | `Download/Kitsugi/Audio` köprüsü (exports.json), silmede birlikte temizlik |
| `ui/screens/manga/MangaReaderScreen.kt` | Çıkışta kesin durdurma (motor + servis) |
| `ui/screens/manga/components/MangaAmbientPanel.kt` | Sabit üst bar, Ayarlar sayfası, HorizontalPager kaydırma |

## Cihazda doğrulama listesi

1. Çevrimiçini aç → manga aç: yerel başlayacak; parça hazır olunca **kesintisiz**
   geçiş yapmalı; açılamazsa yerel sürmeli ve barda nedeni yazmalı.
2. Çevrimiçi sekmesinden bilerek bozuk/zayıf kaynak çal → yerel susmamalı.
3. Okuyucudan geri git / kapat → müzik ve bildirim anında gitmeli.
4. Panelde sayfalar arasında kaydır; Ayarlar sayfasındaki anahtarlar çalışmalı;
   çalma çubuğu her sayfada üstte kalmalı.
5. Bir ses indir → İndirmeler > **Sesler** sekmesinde görünüp çalınmalı;
   panelden silince sekmeden de düşmeli.
