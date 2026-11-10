# KÖK NEDEN RAPORU — LİSTEM BOŞ IZGARA + REGEX ÇÖKMELERİ (2026-11-04)

Drive'daki çökme raporları (crash_history, Kitsugi_Crash_Report, logcat_at_crash,
live_logcat_report) baştan sona incelendi. İki ayrı çökme sınıfı ve bir liste
görüntüleme hatası tespit edildi; üçü de düzeltildi.

---

## 1) LİSTEM: "844 sonuç" yazıyor ama hiçbir kart görünmüyor

### Belirti
Listem sayfasında sonuç sayacı dolu (ör. 844) fakat altındaki alan tamamen boş.
Özellikle **poster ızgarası** düzeninde (`grid_2col`) ve/veya yatay tablette.

**Ekran görüntüsü teyidi:** Sağ üstteki düzen ikonu `GridView` (dört kare). Kodda
`"grid_2col" -> Icons.Rounded.GridView` eşlemesi olduğundan aktif düzen **grid_2col**.
Ayrıca hiçbir durum başlığı ("İzleniyor" vb.) görünmüyor → düz akış + ızgara satırları
0 yüksekliğe çökmüş. Bulgular kök nedenle birebir örtüşüyor.

### Kök neden
`MyListGridContent.kt` içindeki ızgara satırları şu kalıpla çiziliyordu:

```kotlin
Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
    KitsugiMediaEntryCard(modifier = Modifier.weight(1f).fillMaxHeight(), ...)
}
```

`IntrinsicSize.Min`, satır yüksekliğini çocukların **iç (intrinsic) yüksekliğinden**
hesaplar. Ancak `weight(1f)` ile genişliği dağıtılan çocukların iç-yükseklik ölçümünde
genişlik **0** gelir; posterdeki `aspectRatio(2f/3f)` de `yükseklik = genişlik × 1.5`
hesapladığı için **0** döner. Sonuç: satırın tamamı **0 piksele çöker**, sayaç dolu
görünür ama hiçbir kart çizilmez. Uygulamadaki diğer tüm poster ızgaraları
(`GridHomeContent`, `FullScreenMediaGridPage`, vb.) bu yüzden `LazyVerticalGrid`
kullanır ve bu kalıba düşmez.

### Çözüm
- Izgara satırları artık `IntrinsicSize.Min` + `fillMaxHeight()` yerine **kendi
  içeriğiyle ölçülüyor** ve `verticalAlignment = Alignment.Top` ile üstten hizalanıyor
  (posterler diğer ızgaralarla aynı şekilde hizalı). Hem gruplu (`MyListGroupedContent`)
  hem düz (`MyListFlatContent`) ızgara kolu düzeltildi.
- `KitsugiMediaEntryCard` içinde rozetleri alta itmek için kullanılan
  `Spacer(Modifier.weight(1f))` (artık sınırsız yükseklikte anlamsız) sabit
  `Spacer(Modifier.height(4.dp))` ile değiştirildi.

Sonuç: kartlar her düzen/ekran boyutında görünür; sayaç ile liste tutarlı.

---

## 2) ÇÖKME: TranslationManager — `ExceptionInInitializerError` (regex)

### Kanıt (2026-11-04 01:18)
```
java.lang.ExceptionInInitializerError
  at ...TranslationManager$translateTo$2.invokeSuspend
Caused by: java.util.regex.PatternSyntaxException: ... near index 32
  ^\s*(?:\[\[[^\]]+\]\]|\{\{[^}]+}}|\[https?://[^\]]+])\s*$
```

### Kök neden
Wikitext-satırı desenindeki **kaçırılmamış `}}`**. Bu desen JVM `java.util.regex`'te
geçerli görünür ama Android'in **ICU** regex motoru geçersiz sayar. Desen dosya düzeyi
bir `val` içinde derlenince sınıfın `<clinit>`'i patlar → `ExceptionInInitializerError`
→ uygulama çöker. (Python/JVM'de derlenip Android ICU'da reddedildiği doğrulandı.)

### Çözüm (`KitsugiTranslateUtils.kt`)
- Desen ICU uyumlu hale getirildi: `\}\}` (tüm köşeli ayraçlar kaçırıldı).
- Tüm çeviri regex'leri `safeTranslationRegex(...)` yardımcısına alındı: desen
  derlenemezse asla eşleşmeyen `(?!)` regex'ine düşer → bu sınıf bir daha **asla**
  çökmez (en kötü ihtimalle bazı teknik değerler çeviriye gönderilir).

---

## 3) ÇÖKME: DiziBox eklentisi — `NoClassDefFoundError` (regex, eklenti kaynaklı)

### Kanıt (2026-11-03, birden çok)
```
java.lang.NoClassDefFoundError: com.keyiflerolsun.DiziBoxPlayerParser
  at com.keyiflerolsun.DiziBoxWire.isDiziBoxHost
  ... okhttp3 ... OkHttp Dispatcher thread
Caused by: ExceptionInInitializerError
Caused by: PatternSyntaxException ...  (bozuk objectPattern regex'i)
```

### Kök neden
Eklentinin (`Kitsugi-Plugins`) eski bir derlemesindeki bozuk regex, sınıfın static
init'ini patlatıyor; sonraki her erişim `NoClassDefFoundError` fırlatıyor. Bu hata
**OkHttp Dispatcher iş parçacığında** yakalanmadan yayılınca tüm süreç ölüyordu.
Not: eklenti kaynağı v46'da `objectPattern` zaten düzeltilmiş durumda; cihazdaki önbellek
eski derlemeydi.

### Çözüm (`CsRuntimeInit.kt`) — uygulamayı eklenti hatalarına karşı bağışıklaştırma
En dıştaki uygulama interceptor'ı `chain.proceed(...)` artık `try/catch` ile sarıldı:
- `LinkageError` (`ExceptionInInitializerError`, `NoClassDefFoundError`, ...) ve
  `RuntimeException` → `IOException`'a çevrilir. OkHttp ve eklentinin ağ katmanı
  `IOException`'ı zaten zarifçe karşılar (boş sonuç döner); tek bir bozuk eklenti
  artık süreci öldüremez.
- Gerçek ağ hataları (`IOException`) yakalanmaz, normal akışta ilerler.
Bu, yalnızca DiziBox'ı değil **herhangi bir eklentinin** ağ sırasındaki hatasını kapsar.

---

## Değişen dosyalar
- `ui/screens/mylist/MyListGridContent.kt` — ızgara satırları içerikle ölçülür (çökme yok)
- `ui/components/KitsugiMediaEntryCard.kt` — ızgara rozet boşluğu sabit yükseklik
- `utils/KitsugiTranslateUtils.kt` — ICU uyumlu wikitext regex'i + güvenli regex yardımcısı
- `data/cloudstream/CsRuntimeInit.kt` — eklenti hataları ağ hatasına çevrilir (çökme kalkanı)

## Doğrulama
- Düzeltilen wikitext deseni ve `(?!)` yedeği Python `re` ile test edildi (ICU bu
  yapıları aynı işler); eski kaçırılmamış `}}` deseninin JVM'de derlenip ICU'da
  reddedildiği teyit edildi.
- Bu depoda Android SDK/JDK bulunmadığından tam Gradle derlemesi yapılamadı; değişiklikler
  dar kapsamlı ve sözdizimi gözden geçirildi.

## Önemli not — sürüm uyumsuzluğu
Depo `2.4.340` iken cihazdaki çökme raporları `2.4.410-beta` gösteriyor. Yani cihazdaki
kurulum bu depodan **daha yeni** bir kaynaktan derlenmiş. Düzeltmelerin cihaza yansıması
için uygulamanın **bu güncellenmiş depodan yeniden derlenip** kurulması gerekir.