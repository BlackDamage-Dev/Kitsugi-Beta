package com.kitsugi.animelist.data.sync

/**
 * Liste içe aktarımında **silme** kararının saf (yan etkisiz) yardımcıları.
 *
 * Liste yenilemesi iki yönlü bir uzlaştırmadır: uzakta **yeni** kayıt olabilir (ekleme),
 * kayıt **değişebilir** (güncelleme) veya uzakta **silinmiş** olabilir. İlk ikisi delta
 * sorgusuyla görülebilir; **silme görülemez** çünkü delta yanıtı yalnızca hâlâ var olan
 * satırları içerir. Bu nesne, "hangi yerel satır silinebilir?" sorusunun tek yerden ve
 * test edilebilir biçimde yanıtlanmasını sağlar.
 *
 * Yetim silmeyi tehlikeli kılan üç durum vardır; üçü de burada elenir:
 *  1. **Bekleyen yerel düzenleme** — çevrimdışı yapılmış, uzaklara henüz gönderilememiş
 *     değişiklik. Uzak veri onu ezmemeli, satır da silinmemeli (iş kuyrukta duruyor).
 *  2. **Eksik/kesik yanıt** — sunucu sayfa sınırında yarım döndüyse "uzakta yok" sanıp
 *     kütüphaneyi silmek veri kaybıdır. Örtüşme oranı düşükse silme yapılmaz.
 *  3. **Kasıtlı süzgeç** — MAL `nsfw=false` ile +18 kayıtları **yanıta hiç koymaz**;
 *     bunlar silme adayı sayılırsa her yenilemede kullanıcının +18 listesi silinir.
 */
object ImportReconciler {

    /**
     * Otomatik yenilemede silmeye izin verilen en düşük örtüşme oranı.
     *
     * Çekilen liste, yerel satır sayısının %30'unun altındaysa yanıtın yarım geldiği
     * varsayılır ve silme yapılmaz (yalnızca ekleme/güncelleme uygulanır). Kullanıcının
     * açıkça başlattığı "baştan yenile"de bu koruma uygulanmaz — orada niyet nettir.
     */
    const val MIN_DELETION_COVERAGE = 0.30

    /**
     * Silme adayları: uzak listede eşleşmeyen VE korunması gereken satır olmayan kayıtlar.
     *
     * @param eligibleIds silme adayı olabilecek yerel satırlar (kaynak + tür süzgecinden geçmiş)
     * @param matchedIds uzak listede bir karşılığı bulunan yerel satırlar
     * @param protectedIds bekleyen yerel düzenlemesi olan satırlar
     */
    fun deletionCandidateIds(
        eligibleIds: Collection<Int>,
        matchedIds: Set<Int>,
        protectedIds: Set<Int>
    ): List<Int> = eligibleIds.filter { it !in matchedIds && it !in protectedIds }

    /**
     * Yarım/kesik yanıt koruması.
     *
     * @param existingCount yereldeki (silme adayı olabilecek) satır sayısı
     * @param fetchedCount bu turda sunucudan gelen satır sayısı
     * @param enforce `false` ise (kullanıcı "baştan yenile" dediyse) koruma atlanır
     */
    fun coverageAllowsDeletion(
        existingCount: Int,
        fetchedCount: Int,
        enforce: Boolean
    ): Boolean {
        if (!enforce || existingCount <= 0) return true
        return fetchedCount >= existingCount * MIN_DELETION_COVERAGE
    }

    /**
     * Satırın **sunucuda daha önce görüldüğü** kanıtlanmış mı?
     *
     * [previousSnapshot] boşsa hiç tam çekim yapılmamıştır → hiçbir satır "sunucudan
     * geldiği kanıtlanmış" sayılmaz ve silme yapılmaz (ilk tam çekim her şeyi deftere
     * yazar; silme bir sonraki turda mümkün olur). Bu, yerelde eklenip henüz sunucuya
     * gönderilememiş kayıtların yanlışlıkla silinmesini yapısal olarak imkânsız kılar.
     */
    fun serverOriginConfirmed(
        keys: Set<String>,
        previousSnapshot: Set<String>
    ): Boolean = previousSnapshot.isNotEmpty() && keys.any { it in previousSnapshot }

    /**
     * **Diriliş koruması**: yerelde silinmiş ama DELETE işi henüz uzaklara ulaşmamış bir
     * kayıt, liste yenilendiğinde uzakta hâlâ göründüğü için geri eklenmemelidir.
     *
     * Kullanıcı çevrimdışıyken sildi → satır yerelden gitti, DELETE kuyruğa düştü →
     * ağ gelince liste yenilendi → sunucu kaydı hâlâ döndürdü → satır "yeni" sanılıp geri
     * eklenirdi ("sildiğim şey geri geldi"). Artık bekleyen DELETE'lerin kimlik anahtarları
     * tombstone sayılır ve o kayıtlar eklenmez.
     */
    fun isResurrection(
        importedKeys: Set<String>,
        pendingDeleteKeys: Set<String>
    ): Boolean = importedKeys.isNotEmpty() && pendingDeleteKeys.isNotEmpty() &&
        importedKeys.any { it in pendingDeleteKeys }
}
