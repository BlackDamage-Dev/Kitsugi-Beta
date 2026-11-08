> **2026-11-08 v2 öncelik notu:** Onaylı görsel birebir hedef; `14_ONAYLI_GORSEL_SADAKAT_PLANI_V2.md` ve diğer ajan için `04_TEK_BELGE_DIGER_AJAN.md` geçerlidir. Bu dosyadaki v1 görsel çelişkileri tarihsel kalmıştır. V2: hazır raf/pill zorunlu değil, mobil-only hero görseli var; UI dosyaları/sahiplik aynı.

# Keşfet yeniden tasarımı — ANA PLAN (yalnız mobil)

**Tarih:** 2026-11-08 · **Temel commit:** `051c4b6c1e03ba96c9e6ddcb903c75186a7cf350` · **Durum:** ARAŞTIRMA/PLAN; hiçbir uygulama davranışı değiştirilmedi, hiçbir cihaz testi yapılmadı. Kullanıcının seçtiği görsel: [Drive'da onaylı referans](https://drive.google.com/file/d/12-pwLJS1rRMvVUPvCkuwB3c0I6fa52Q0/view). Görsel bir konsepttir, gerçek içerik veya her durumda bire bir ekran değildir.

## Sınır ve temel kararlar
- Yalnız telefon/tablet Keşfet: üstte kaynak seçimi, **Diziler / Filmler / Anime / Manga** sekmeleri; türe uygun vitrin; alt alta yatay raflar. Sekmeye dokunma ve yana kaydırma; kart rafları kendi içinde yana kayar.
- Kaynaklar: AniList, MAL/Jikan, TMDB, Simkl, Kitsu, Shikimori, Bangumi; **Tümü** seçimidir, sekizinci kaynak değildir. Kullanıcının son kaynak tercihi korunur. İlgisiz veri göstermemek, sahte sıralama üretmemek, gerçek veri ve rozetlerini korumak esastır.
- **TV kapsam dışı:** TV ekranı, gezinme, D-pad, TV tasarımı veya TV'ye özel test çalışması planlanmaz. TV'nin de kullandığı ortak Kotlin API'leri/sözleşmeleri kırılmayacak; mobil eklemeler ayrı dosyalarda ve mevcut imzalar değiştirilmeden yapılacak. Ortak katmanda davranış değişikliği kaçınılmaz çıkarsa iş durur ve önce kullanıcıdan kapsam kararı alınır. Derlemenin bütünü yine de geçmelidir.
- Aşağıdaki dokümanlar sırasıyla okunup **bir aşama bitirilip test edilmeden sonraki aşamaya geçilmez**. Tahmin edilemeyen canlı API sonuçları ve cihaz çeşitliliği nedeniyle sıfır hata garantisi verilmez.

## Teslimat haritası
1. `01_ETKI_ENVANTERI.md`: etkilenen/korunacak dosyalar ve akışlar.
2. `02_YEDI_KAYNAK_MATRISI.md`: kaynak + raf + tür + sayfalama gerçekleri ve 8×4 kontrol matrisi.
3. `03_ACIK_KAYNAK_KOD_ARASTIRMASI.md`: kod düzeyinde kanıtlar, sabit commitler, arşiv ve lisans sınırları.
4. `04_TASK_TEMEL_VE_GUVENCE.md`: mevcut davranış fotoğrafı ve test zemini.
5. `05_TASK_MODEL_VE_KAYNAKLAR.md`: kaynak/anlam/tür adaptörü.
6. `06_TASK_GEZINME_VE_DURUM.md`: sekme, gesture ve durum koruma.
7. `07_TASK_GORSEL_RAF_VITRIN.md`: onaylanan görsel dil, kart, hero, raf.
8. `08_TASK_DETAY_VE_SAYFALAMA.md`: tıklama, kaynağa bağlı Tümünü gör, filtre/sayfa.
9. `09_TASK_VERI_HATA_PERFORMANS.md`: loading, cache, concurrency, hata ve kaynak kurtarma.
10. `10_TASK_ENTEGRASYON_VE_ERISILEBILIRLIK.md`: ayarlar, kişisel listeler, takvim, dil, erişilebilirlik.
11. `11_TASK_KABUL_VE_YAYIN.md`: regresyon, cihaz testi, kontrollü geçiş ve geri dönüş.
12. `12_RISK_VE_KARAR_KAYDI.md`: yüksek riskler ve uygulamadan önce kararlaştırılacak maddeler.

## Her görevde uygulanacak çalışma standardı
- [ ] Değişiklikten önce ilgili dosyayı/güncel `git status`u tekrar oku; kullanıcı işini koru.
- [ ] Her değişikliği küçük ve sınırları belli tut; ilgili testleri önce/beraber yaz; test sonucunu ve test edilemeyen kısmı görev dosyasına kaydet.
- [ ] Derleme/test başarısızsa veya beklenen durum gözlenemiyorsa aşamayı tamamlandı sayma; sessiz fallback ve sahte başarı yok.
- [ ] Mevcut kaynak kimlikleri, kullanıcı verisi, ayarlar, cache, filtreleme, listeye ekleme ve geri navigasyonu korunur.
- [ ] Geri dönüş seçeneği: her aşama ayrı izlenebilir diff; **mevcut uygulama akışı korunarak**, yeni mobil ekran önce geliştirici/derleme bayrağı ile kontrol edilsin (bayrağın yeri ve persist edilip edilmeyeceği Aşama 0'da belirlenir). Varsayılan değişmeden önce tüm mobil kabul testleri geçmeli. Üretime yayılmış bir bayrak varsa temizliği ancak stabilizasyon sonrası planlanır.

## Şu anki doğrulama durumu
Sadece statik kod incelemesi ve harici depo seçili kaynak dosyalarının incelenmesi yapıldı. Bu ortamda `java` yok, `ANDROID_HOME`/`ANDROID_SDK_ROOT` boş; Gradle, Android derlemesi, emülatör ve gerçek cihaz testleri **çalıştırılmadı**. Kullanıcı cihazı/CI gerekli. Referans kodları inceleme içindir; toplama tüm dünyadaki tüm projeleri kapsamaz ve kaynak arşivi tam repo değildir.
