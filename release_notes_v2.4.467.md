# Kitsugi Beta v2.4.467 — Okuyucu Kısayolları, Tam Ekran Çıkış Düzeltmesi, Webtoon Yakınlaştırma & Çift Sayfa Birleştirme

Bu sürümde manga okuyucusunda arayüz ergonomisi, tam ekran kararlılığı, webtoon yakınlaştırma ve çift sayfa (kitap görünümü) iyileştirmeleri gerçekleştirildi.

---

### ⌨️ 1. Alt Bar Sadeleştirmesi & "Yön Değiştir" Kısayolu
- Alt kontrol barındaki klavye butonu kaldırılarak **Okuyucu Ayarları** paneli içine "Klavye Kısayolları" butonu olarak taşındı (`← / →`, `↑ / ↓`, `A / D`, `W / S`, `Space`, `Page Up / Down`, `Home / End`).
- Alt bara tek dokunuşla yön değiştiren **"Yön Değiştir" (⇄)** butonu eklendi:
  - Yatay modda: `RTL ↔ LTR` arasında hızlı geçiş.
  - Dikey modda: `Webtoon ↔ Dikey` arasında hızlı geçiş.
- **Yeni 5 Butonlu Alt Bar:** `[Okuma Yönü] [Tam Ekran] [Otomatik Kaydırma] [Yön Değiştir] [Ayarlar]`.

---

### ⛶ 2. Tam Ekran Çıkışında Sistem Çubukları Restorasyonu
- Tam ekran (⛶) modundan çıkıldığında, geri basıldığında veya başka ekrana geçildiğinde sistem çubuklarının (durum ve gezinme barları) geri gelmemesi sorunu kökten çözüldü.
- `DisposableEffect(onDispose)` içinde `WindowCompat.getInsetsController().show(WindowInsetsCompat.Type.systemBars())` çağrısı zorunlu ve koşulsuz hâle getirildi.

---

### 🔍 3. Webtoon Yakınlaştırma & Sıfırlama
- "Webtoon yakınlaştırma" ayarı eklendi:
  - **Kapalı:** Görsel tam ekran genişliğine sığdırılır, yakınlaştırma kilitlenir.
  - **Açık:** İki parmakla ve çift dokunuşla yakınlaştırma serbesttir.
- Okuma modu değiştiğinde veya webtoon'a girildiğinde yakınlaştırma sıfırlanarak "sabit yakınlaşmış kalma" sorunu giderildi.

---

### ⚡ 4. Webtoon Otomatik Kaydırma Hızı (20–500 dp/sn)
- Otomatik kaydırma hızı için 20–500 dp/sn aralığında (varsayılan 160) hassas ayar sürgüsü eklendi.
- Kare-bazlı kaydırma motoru genişletilmiş hız aralığını destekleyecek şekilde güncellendi.

---

### 🔢 5. Tekil Sağ Alt Sayfa Sayacı
- Sayfa görsellerinin üzerindeki eski mükerrer köşe rozeti kaldırıldı.
- Sağ alt köşeye tek ve temiz bir sayfa sayacı yerleştirildi:
  - Tek sayfada: `2 / 52`
  - Çift sayfada: `2-3 / 52`
- "Kontroller gizliyken sayfa sayacı" ayarı ile isteğe bağlı olarak açılıp kapatılabilir.

---

### 📖 6. Çift Sayfa Yayılımı & Otomatik Sayfa Birleştirme (Kitap Görünümü)
- "İki sayfa yan yana" ve "İlk sayfayı tek göster" ayarları eklendi (RTL modunda ilk sayfa sağda yer alır).
- **Akıllı Kenar Birleştirme (`MangaSpreadStitcher`):** İki sayfanın birleşim kenarlarında beyaz boşluk algılanırsa sayfalar tek bir kesintisiz yay görselinde birleştirilir; boşluk yoksa yan yana sergilenir.
- "Sayfaları birleştir (kitap görünümü)" ayarı ile açılıp kapatılabilir.
