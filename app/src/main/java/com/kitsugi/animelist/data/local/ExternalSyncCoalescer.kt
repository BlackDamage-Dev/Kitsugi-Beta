package com.kitsugi.animelist.data.local

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Canlı (uzak) eşitlemeleri **kayıt başına tek yazmaya** indirger ve tüm yazmaları sıraya sokar.
 *
 * ## Neden? (v2.4.341)
 * "Listem" ekranında her dokunuş — bölüm +1, puan kaydırıcısı, durum değişimi, not yazımı —
 * `MediaEntryRepository.update()` üzerinden **anında** `ExternalListSyncManager.syncEntry()`
 * çağırıyordu. O çağrı altı platforma birden yayılıyor; AniList tarafında kayıt başına
 * 1–4 istek (kimlik doğrulama + SaveMediaListEntry + favori sorgusu) üretiyordu.
 *
 * Kullanıcı "+1"e üç kez bastığında ya da puan kaydırıcısını sürüklediğinde bu, saniyeler
 * içinde 10+ AniList isteğine dönüşüyor; AniList'in fiilî 30 istek/dk limiti anında doluyor
 * ve 60 sn'lik ceza penceresi ("kota doldu") başlıyordu.
 *
 * ## Davranış
 *  - Yerel veritabanı yazımı **her zaman anında** yapılır (UI gecikmez).
 *  - Uzak eşitleme [QUIET_PERIOD_MS] sessizlikten sonra çalışır; aynı kayıt için yeni bir
 *    düzenleme gelirse önceki bekleyen iş **iptal edilir** → yalnızca son hâl gönderilir.
 *  - Tüm uzak yazmalar tek bir [serial] mutex'ten geçer: farklı kayıtlar aynı anda
 *    fırlatılmaz, kota patlaması oluşmaz.
 *  - Silme işi `quietMs = 0` ile kuyruğa girer: bekleyen bir güncellemenin **arkasından**
 *    çalışır (sıra korunur), böylece silinen kayıt uzakta yeniden "diriltilemez".
 */
internal object ExternalSyncCoalescer {

    /** Art arda gelen düzenlemelerin tek yazmaya birleşmesi için sessizlik süresi. */
    const val QUIET_PERIOD_MS = 1_200L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val guard = Any()
    private val pending = mutableMapOf<String, Job>()
    private val serial = Mutex()

    /**
     * [key] için (genelde `entry-<id>`) eşitlemeyi planlar. Aynı anahtarda bekleyen iş varsa
     * iptal edilir. [block] tüm uzak yazmalarla aynı sırada (mutex içinde) çalışır.
     */
    fun schedule(key: String, quietMs: Long = QUIET_PERIOD_MS, block: suspend () -> Unit) {
        val job = scope.launch(start = CoroutineStart.LAZY) {
            if (quietMs > 0L) delay(quietMs)
            serial.withLock { block() }
        }
        synchronized(guard) {
            pending.remove(key)?.cancel()
            pending[key] = job
        }
        job.invokeOnCompletion {
            synchronized(guard) {
                if (pending[key] === job) pending.remove(key)
            }
        }
        job.start()
    }

    fun cancel(key: String) {
        synchronized(guard) { pending.remove(key)?.cancel() }
    }

    /** Bekleyen işlerin bitmesini beklemeden, verilen bloğu sıraya sokar. */
    suspend fun runSerially(block: suspend () -> Unit) {
        serial.withLock { block() }
    }
}
