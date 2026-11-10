package com.kitsugi.animelist.data.local

import com.kitsugi.animelist.data.auth.SyncField

/**
 * Bekleyen eşitleme kuyruğunun SAF karar kuralları (T02).
 *
 * [PendingSyncDrainer] Android `Context`'i, Room'u ve ağ yazıcılarını bilir; bu yüzden
 * kuyruk sözleşmeleri daha önce birim testiyle hiç doğrulanamıyordu (yalnız kod okuması).
 * Kararlar buraya, yan etkisiz ve I/O'suz olarak taşındı — [CrossSyncMergePolicy] ve
 * `SiblingMirrorPolicy` ile aynı desen. Drainer yalnızca bu kuralları uygular.
 *
 * Sözleşmeler (kuyruk hiçbir koşulda kullanıcı niyetini sessizce kaybetmez):
 *
 *  - Bir iş ancak GERÇEKTEN gönderildiğinde (`needsRetry == false`) kuyruktan düşer.
 *    "Bağlı değil" bir bağlantı boşluğudur → iş kuyrukta kalır (bkz. `SyncResult`).
 *  - **Yetki hatası deneme bütçesini yakmaz**: oturum süresi dolmuşken yapılan bir
 *    düzenleme, kullanıcı yeniden bağlanana kadar bekler; 5 denemeyi yetki hatasıyla
 *    tüketip donmaz (zaten yeniden bağlanmada `freshBudget` da bütçeyi tazeler).
 *  - [MAX_RETRIES] dolan iş SİLİNMEZ; yalnızca otomatik denemesi durur.
 *  - Aynı satırın yeni işi eski işlerin ALAN kümelerini yutar (birleşim), ezmez (E15).
 *  - Doğrudan (çevrimiçi) yazım başarılı olunca kuyruktaki işten yalnızca O YAZIMIN
 *    taşıdığı alanlar düşülür; işin geri kalan alanları kuyrukta kalır (E73).
 */
object PendingSyncPolicy {

    /** Bir işin arka arkaya en çok kaç kez otomatik deneneceği (iş asla silinmez). */
    const val MAX_RETRIES = 5

    /** Bir kuyruk işinin denemesi sonunda verilecek karar. */
    enum class RetryDecision {
        /** Gönderildi (ya da gönderilmesi bilinçli olarak gerekmiyor): işi kuyruktan sil. */
        DROP,

        /** Gönderilemedi: iş kalsın ve deneme sayacını bir artır. */
        KEEP_INCREMENT,

        /** Yetki hatası: iş kalsın ama deneme sayacını ARTIRMA (bütçe yanmaz). */
        KEEP_WITHOUT_INCREMENT
    }

    /** Deneme bütçesi dolmamış iş bir sonraki boşaltmada denenebilir. */
    fun isEligible(retryCount: Int): Boolean = retryCount < MAX_RETRIES

    /**
     * Yetki (token) hatası metni mi?
     *
     * Yazıcılar yetki hatasını tek biçimde bildirmiyor: kimi HTTP kodunu mesaja gömüyor
     * ("MAL API hatası: 401"), kimi yerelleştirilmiş metin ("token geçersiz") üretiyor,
     * kimi `IOException` mesajında "Unauthorized" taşıyor. Bu yüzden kontrol, mevcut
     * davranışı AYNEN koruyarak üç alt dizge üzerinden yapılır — artık TEK yerden
     * (eskiden drainer + repository'de üç kopyaydı).
     */
    fun isAuthErrorText(text: String?): Boolean =
        text != null && (
            text.contains("401") ||
                text.contains("token geçersiz") ||
                text.contains("Unauthorized")
            )

    /** Metinlerden herhangi biri yetki hatasıysa `true`. */
    fun isAuthError(texts: Iterable<String?>): Boolean = texts.any { isAuthErrorText(it) }

    /**
     * Bir denemenin kuyruk kararı.
     *
     * @param hasResult çağrı bir `SyncResult` döndürdü mü (fırlatmadı ve `null` değil)?
     * @param needsRetry sonucun kendi "tekrar denenmeli" bayrağı (`hasResult` yanlışsa
     *                   çağıran `true` geçmelidir: sonucu olmayan iş başarı sayılamaz).
     * @param errorTexts sonuçtaki hata metinleri.
     * @param thrownMessage çağrı fırlattıysa istisna mesajı. **(E74)** Eski karar yalnız
     *                   `errorTexts`'e bakıyordu; `prepare()` aşamasında (token/ayar okuma)
     *                   fırlatılan bir yetki istisnası sayaç yakıyordu.
     */
    fun decision(
        hasResult: Boolean,
        needsRetry: Boolean,
        errorTexts: List<String>,
        thrownMessage: String?
    ): RetryDecision = when {
        hasResult && !needsRetry -> RetryDecision.DROP
        isAuthError(errorTexts) || isAuthErrorText(thrownMessage) ->
            RetryDecision.KEEP_WITHOUT_INCREMENT
        else -> RetryDecision.KEEP_INCREMENT
    }

    /**
     * Kuyruğa yeni iş girerken alan birleştirme: yeni iş, aynı satırın eski işlerinin
     * alanlarını YUTAR. Çevrimdışıyken önce "ilerleme", sonra "not" değiştirildiyse
     * ikinci iş birincisini silse bile `{PROGRESS, NOTES}` birlikte gönderilir (E15).
     */
    fun mergedFields(
        newFields: Set<SyncField>,
        olderFieldSets: Iterable<Set<SyncField>>
    ): Set<SyncField> = olderFieldSets.fold(newFields) { acc, older -> acc + older }

    /**
     * Doğrudan yazım başarılı olduktan sonra kuyrukta kalması gereken alanlar (E73).
     *
     * Eski davranış aynı satırın kuyruk işini BÜTÜNÜYLE siliyordu: çevrimdışıyken
     * kuyruğa giren `{NOTES}` işi, çevrimiçiyken yalnız `{PROGRESS}` içeren başarılı
     * bir düzenlemeden sonra siliniyor ve not hedeflere hiç ulaşmadan kayboluyordu.
     * Artık yalnızca gönderildiği bilinen alanlar düşülür; geri kalan alanlar (anlık
     * görüntüsündeki değerleriyle) kuyrukta kalır. Düşülen alanların anlık görüntüdeki
     * bayat değerleri de böylece bir daha yazılmaz (ilerleme geriye çekilmez).
     */
    fun remainingFields(
        queuedFields: Set<SyncField>,
        syncedFields: Set<SyncField>
    ): Set<SyncField> = queuedFields - syncedFields

    /**
     * Kuyruk kaydı bu yerel satıra mı ait?
     *
     * `id` tek başına yetmez: aynı sayısal kimlik farklı kaynak/tür satırlarında da
     * bulunabilir (bkz. `removeFromQueueIfPresent`'in eski yorumu). `entryId <= 0`
     * (henüz DB kimliği olmayan satır) hiçbir kayıtla eşleştirilmez; ayrı işler
     * birleştirilmeden kuyrukta kalır (yanlış satırları birleştirmektense güvenli taraf).
     */
    fun sameRow(
        entryId: Int,
        entrySource: String,
        entryTypeName: String,
        queuedId: Int,
        queuedSource: String,
        queuedTypeName: String
    ): Boolean =
        entryId > 0 &&
            queuedId == entryId &&
            queuedSource == entrySource &&
            queuedTypeName == entryTypeName
}
