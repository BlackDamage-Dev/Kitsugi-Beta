package com.kitsugi.animelist.data.auth

/**
 * Kitsu'nun `progress cannot exceed length of media` (HTTP 422) kuralı için
 * tek seferlik, ölçülmüş uzunluğa kırpma ve yeniden yazma politikası.
 *
 * Ağ/başarısızlık sonucu başarı gibi gösterilmez. Yalnız sunucunun açık uzunluk
 * hatasında, öğrenilen cap istenenden küçükse ikinci yazım yapılır.
 */
internal object KitsuProgressLengthGuard {
    data class Outcome(
        val result: KitsuApiClient.KitsuWriteResult,
        val cappedProgressTo: Int? = null
    )

    suspend fun write(
        requestedProgress: Int,
        write: suspend (Int) -> KitsuApiClient.KitsuWriteResult,
        fetchLength: suspend () -> Int?
    ): Outcome {
        val first = write(requestedProgress)
        if (first.success || !KitsuApiClient.isMediaLengthError(first.errorMessage)) {
            return Outcome(first)
        }
        val cap = fetchLength() ?: return Outcome(first)
        if (cap >= requestedProgress) return Outcome(first)
        val capped = write(cap)
        return Outcome(capped, cappedProgressTo = cap.takeIf { capped.success })
    }
}
