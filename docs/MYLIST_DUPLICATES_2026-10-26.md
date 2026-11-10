# Listem tekrar eden kartlar

## Kodda doğrulanan nedenler
- Tümü gruplama kullanırken altı kaynak sekmesi ham satırları kullanıyordu. Aynı kaynaktaki geçmiş içe aktarma kopyaları kart, sayaç ve istatistiklerde ayrı kalıyordu.
- Kaynak sekmesinde arama, Bangumi dışında tüm kaynakların ham satırlarına açılıyordu; kaynak kapsamı bozuluyordu.
- Kimlik köprüsü gerçek MAL kimliği bulunan satırların önbelleğini hiç okumuyordu. O satırdaki bilinen diğer kaynak kimlikleri farklı dildeki kopyaya ulaşamıyordu.

## Değişiklik
Arka plan hesabı hem birleşik listeyi hem kaynak içinde tekilleştirilmiş altı listeyi birlikte yayınlar. Pager, arama, filtre, istatistik ve kaynak seçici sayaçları bunları kullanır. Kaynakların durum/ilerlemesi birbirinden alınmaz. Aynı kaynak önceliğinde en son güncellenmiş satır temsil edilir; eşitlikte kayıt ID'si kullanılır. Kimlik köprüsü tüm satırları okur; mevcut kimlik uzayını ezmeme ve farklı kimlik/sezon/tür korumaları korunur.

Veritabanından kayıt silinmez, uzak hesaplarda değişiklik yapılmaz. Bilinen kimlik veya güvenli başlık eşleşmesi olmayan kayıtlar tahminle birleştirilmez. Kullanıcının cihaz veritabanı burada bulunmadığından görsellerdeki her çiftin kimlikleri tek tek doğrulanamaz.

## Doğrulama
Altı kaynakta tekrarlar, güncelleme önceliği, kaynak kapsamı/ilerleme izolasyonu ve çakışan sezonlar için regresyon testleri eklendi. `git diff --check` başarılı. Gradle testi denendi; ortamda Java/JAVA_HOME olmadığı için başlayamadı. Android derlemesi ve cihaz doğrulaması yapılmadı.
