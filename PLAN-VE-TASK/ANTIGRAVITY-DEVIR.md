# ANTIGRAVITY DEVİR DOSYASI — Kitsugi-Beta çapraz liste senkronizasyonu

Tarih: 2026-10-22
Repo: `f26901964-eng/Kitsugi-Beta`
Dal: `arena/71836ee9-kitsugi-beta`
Bu paketin dayandığı commit: `c08094d`

> ZIP içinde `.git` yoktur. Mümkünse GitHub'dan aynı dal ve commit ile çalış;
> ZIP, kodun tamamı + güncel plan/task/kanıt dosyalarının anlık görüntüsüdür.

## Antigravity'e yapıştırılacak görev

```text
Kitsugi-Beta reposunda arena/71836ee9-kitsugi-beta dalındaki c08094d commit'inden devam et.

Önce kodu değiştirmeden çalıştır:
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest

Derleme veya test hata verirse hiçbirini gizleme. Her hatayı; dosya+satır, hata metni,
kök neden ve en küçük düzeltme olarak listele. Düzelt, aynı komutu yeniden çalıştır.
Derleme/test gerçekten geçmeden APK üretme.

Bu işin konusu tek kayıtta yapılan ekleme/düzenleme/silmenin AniList, MAL, Kitsu,
Shikimori, Bangumi ve Simkl'e doğru yayılmasıdır. Yanlış kimlik eşleşmesi, sessiz
atlama, ilerlemenin gerilemesi ve kullanıcının son işleminin geri alınması kabul edilemez.

Önce şu dosyaları oku:
- KANIT_RAPORU_ALAN_BAZLI_ESITLEME_2026-10-22.md (özellikle §19, §20, §21)
- 02_TASK_TAKIP.csv
- 03_HATA_KAYDI.csv
- 06_ILERLEME_GUNLUGU.md

Metinle kod çelişirse kod doğrudur; çelişkiyi ayrıca raporla. Resmî API dokümanı >
açık kaynak referans (MALSync/Aniyomi vb.) > forum/topluluk sıralamasını kullan.

Compile+unit test geçtikten sonra APK aşamasına geç:
1. Mevcut Gradle/version/signing yapılandırmasını önce oku. Sürüm veya imza
   bilgisi uydurma. İmza/secret yoksa release APK'yı zorlama; debug APK üret ve
   eksik yapılandırmayı açıkça raporla.
2. Üretilen APK'nın yolunu, boyutunu ve SHA-256 değerini ver.
3. Değiştirdiğin her dosyayı ve nedenini listele.
4. Kalan her engeli "kod okuması", "model koşturması" veya "gerçek çalıştırma"
   ayrımıyla dürüstçe belirt. Çalıştırmadığın teste "geçti" deme.

Sonraki öncelikler:
- T13: AuthViewModel orkestrasyon testi
- T04: kaynak bazlı kimlik önbelleği ve yanlış eşleşme denetimi
- T03: kısmi yeniden bağlanmada bayat token
- T22: kardeş satır aynası entegrasyon testi
- T10/T11: favori yazım doğrulamaları
- T14: 6 kaynak x 3 tur uçtan uca idempotans senaryosu
- Çalışma-zamanı: T06 (Simkl memo/delete), T07 (Bangumi rate=0), T09/E60 (Kitsu)

Her tur sonunda: yeni hata numarasıyla kök neden+düzeltme+test kanıtı, güncel
rapor bölümü, build/test çıktısı özeti ve kalan riskleri ver. "Her şey tamam" diye
kapatma; "hâlâ neyi kaçırmış olabilirim?" sorusuyla bir saldırı senaryosu daha dene.
```

## Kesinlikle bozulmayacak sözleşmeler

- İlerleme/cilt/tekrar sayısı birleşimde gerilemez; `Completed -> Planned` dönmez.
- Son kullanıcı işlemi kazanır; `Dropped/Paused` yapışkandır.
- Aktif tekrar sırasında ilerleme en güncel tekrar satırından gelir (max değil).
- Başlangıç en erken, bitiş en geç tarihtir.
- Her sıralama/eşitlik kararının son anahtarı satır `id`sidir; tiebreak'siz
  `maxByOrNull { updatedAt }` veya benzeri yazılmaz.
- `isRepeating == (status == Repeating)` korunur.
- Not/etiket/öncelik birleştirilmez; favori yalnız ekleme yönünde taşınır.
- Desteklenmeyen alana istek atılmaz; atlanan her kaynak görünür gerekçeyle raporlanır.
- Kuyrukta "bağlı değil" işi düşmez; kullanıcı tarafından kapatılmış kaynak başarı sayılır.
- 429 her metotta sınırlı yeniden denenir; 5xx/IOException yalnız idempotent fiillerde.
- Simkl'e asla `dropped` gönderilmez; doğru değer `notinteresting`dir.
- Kitsu puanı 0/1 olamaz; aralık 2..20 ve temizleme `null`dır.
- MAL seçicisine var olmayan `is_favorited` alanı korumasız eklenmez.
- Bangumi/Simkl'de tekrar bayrağı için durum zorlanmaz.

## Bilinen açık kanıt boşlukları

Bu paketin hazırlandığı ortamda JDK/Gradle/Android SDK yoktu; bu yüzden önceki
çalışmada derleme ve JVM birim testleri **çalıştırılmadı**. Antigravity'nin ilk
görevi bunu kapatmaktır. Ayrıca gerçek hesap/API gerektiren T06, T07, T09 ve E60
çalışma-zamanı doğrulamaları açık kalır. Token/şifre isteme; yalnızca kullanıcının
o ortamda zaten yapılandırdığı kimlik doğrulamayı kullan.

## Beklenen çıktılar

- Gradle compile + unit test tam sonucu
- Varsa hata bazında dosya+satır ve kök neden tablosu
- APK yolu/boyutu/SHA-256 (yalnız testler gerçekten geçerse)
- Değişen dosyalar + commit bilgisi
- 02_TASK_TAKIP ve 03_HATA_KAYDI güncellemeleri için önerilen satırlar
- Kalan riskler ve sıradaki 3 iş
