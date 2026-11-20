package com.kitsugi.animelist.core.memory

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.util.Log
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Uygulama genelindeki bellek baskısı yöneticisi.
 *
 * Android, bellek azaldığında `onTrimMemory(level)` ile uygulamayı UYARIR; uygulama bu uyarıya
 * önbelleklerini boşaltarak cevap vermezse sistem süreci hiçbir rapor bırakmadan öldürür
 * (LMKD). Kitsugi'de bu geri çağrı hiç dinlenmiyordu.
 *
 * Bu nesne:
 *  1. Tüm [BoundedCache]'leri ve kaydedilen temizleyicileri tutar (zayıf referansla — sızıntı yok).
 *  2. `onTrimMemory` seviyesine göre önbellekleri küçültür / boşaltır (Coil görsel önbelleği dahil).
 *  3. [watchdogTick] ile Java heap'i düzenli ölçer; %80'i geçerse sistem uyarısını beklemeden
 *     kendiliğinden temizlik yapar. Bu, "gezdikçe şişip sonunda OOM ile çökme" senaryosunun
 *     asıl sigortasıdır.
 */
object KitsugiMemoryGuard {

    private const val TAG = "KitsugiMemoryGuard"

    interface Trimmable {
        /** 0f → tamamen boşalt; 0.5f → yarıya indir. */
        fun trimTo(fraction: Float)
        fun trimName(): String
    }

    private val trimmables = CopyOnWriteArrayList<WeakReference<Trimmable>>()

    /** Lambda temizleyicileri GC'ye kurban gitmesin diye güçlü referansla tutulur. */
    private val strongClearers = CopyOnWriteArrayList<Trimmable>()

    @Volatile private var lastAutoTrimMs = 0L

    fun register(t: Trimmable) {
        trimmables.add(WeakReference(t))
    }

    /** Sınıf/nesne dışı önbellekler (Coil, OkHttp bağlantı havuzu vb.) için kalıcı temizleyici. */
    fun registerClearer(name: String, onTrim: (fraction: Float) -> Unit) {
        strongClearers.add(object : Trimmable {
            override fun trimTo(fraction: Float) = onTrim(fraction)
            override fun trimName() = name
        })
    }

    internal fun clearer(name: String, clear: () -> Unit): Trimmable {
        val t = object : Trimmable {
            override fun trimTo(fraction: Float) { if (fraction <= 0.5f) clear() }
            override fun trimName() = name
        }
        strongClearers.add(t)
        return t
    }

    /** Application.onTrimMemory'den çağrılır. */
    @Suppress("DEPRECATION")
    fun onTrimMemory(level: Int) {
        val fraction = when {
            // Ön planda ama sistem kritik derecede sıkışık / arka planda öldürülmek üzere
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> 0f
            level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE -> 0f
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> 0.25f
            level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> 0.5f
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> 0f
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> 0.25f
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> 0.5f
            else -> return
        }
        trimAll(fraction, "onTrimMemory($level)")
    }

    fun onLowMemory() = trimAll(0f, "onLowMemory")

    /**
     * Java heap kullanımını ölçer; tavanın %80'ini aşmışsa önbellekleri yarıya, %90'ını aşmışsa
     * tamamen boşaltır. Ucuz bir işlemdir (sadece Runtime sayaçları okunur).
     */
    fun watchdogTick() {
        val rt = Runtime.getRuntime()
        val max = rt.maxMemory()
        val committed = rt.totalMemory()
        if (max <= 0L || committed <= 0L) return
        val used = committed - rt.freeMemory()
        val ratioOfMax = used.toDouble() / max

        // v2.4.492 — GC FIRTINASI DÜZELTMESİ
        //
        // Eski ölçüt yalnızca `used / maxMemory()` idi. Kullanıcı cihazında heap tavanı
        // 512 MB olduğu için 83 MB kullanım "sadece %16" görünüyor ve koruma HİÇ
        // tetiklenmiyordu. Oysa ART, ayrılan toplam alan (committed ≈107 MB) içinde sürekli
        // LOS (Large Object Space) tahsisi yapıyordu: 2026-11-20 günlüğünde her ~300 ms'de
        // bir `freed … LOS objects 33-57MB` çöp toplaması, 200 ms duraklamalarla.
        // Bu fırtına sürerken `MPEG4Writer` native yazıcısı çökme noktasına geldi.
        //
        // Artık committed alan da ölçülür: kullanım, ayrılmış alanın %85'ini aşarsa ya da
        // tavanın %70'ine ulaşırsa temizlik devreye girer. Böylece cihaz 512 MB heap'e sahip
        // olsa bile ani LOS baskısı fark edilir.
        val ratioOfCommitted = used.toDouble() / committed
        val pressured = ratioOfMax >= 0.70 || ratioOfCommitted >= 0.85
        val now = System.currentTimeMillis()
        if (!pressured || now - lastAutoTrimMs < 5_000L) return
        lastAutoTrimMs = now
        val fraction = if (ratioOfMax >= 0.90 || ratioOfCommitted >= 0.96) 0f else 0.5f
        trimAll(
            fraction,
            "heap ${"%.0f".format(ratioOfMax * 100)}%/${"%.0f".format(ratioOfCommitted * 100)}% " +
                "(${used shr 20}MB/${committed shr 20}MB, tavan ${max shr 20}MB)"
        )
    }

    fun trimAll(fraction: Float, reason: String) {
        var count = 0
        val dead = ArrayList<WeakReference<Trimmable>>()
        for (ref in trimmables) {
            val t = ref.get()
            if (t == null) { dead += ref; continue }
            runCatching { t.trimTo(fraction); count++ }
        }
        trimmables.removeAll(dead.toSet())
        for (t in strongClearers) runCatching { t.trimTo(fraction); count++ }
        Log.w(TAG, "Bellek temizliği [$reason] → ${count} önbellek ${if (fraction <= 0f) "boşaltıldı" else "%${((1 - fraction) * 100).toInt()} küçültüldü"}")
    }

    /** Teşhis raporları için kısa özet. */
    fun describe(context: Context? = null): String = buildString {
        val rt = Runtime.getRuntime()
        val used = (rt.totalMemory() - rt.freeMemory()) shr 20
        append("heap=${used}MB/${rt.maxMemory() shr 20}MB")
        if (context != null) {
            runCatching {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
                append(" sysAvail=${mi.availMem shr 20}MB lowMemory=${mi.lowMemory}")
            }
        }
        val caches = trimmables.mapNotNull { it.get() }.filterIsInstance<BoundedCache<*, *>>()
        val top = caches.sortedByDescending { it.size }.take(8)
        if (top.isNotEmpty()) append(" caches=").append(top.joinToString { "${it.name}:${it.size}" })
    }
}
