package com.kitsugi.animelist.utils

import android.content.Context
import androidx.annotation.StringRes
import com.kitsugi.animelist.KitsugiApplication
import java.util.Locale

/**
 * Composable olmayan bağlamlarda (ViewModel, servis, Worker, veri katmanı,
 * yardımcı sınıflar) dil dosyasından metin çözer.
 *
 * **Neden gerekli?**
 * `stringResource()` yalnızca @Composable gövdesinde çağrılabilir. Uygulamanın
 * üretim/veri katmanı (ör. `AuthViewModel`, `KitsuSyncManager`, bildirim
 * oluşturucular) kullanıcıya gösterilecek metni `String` olarak ürettiği için
 * bu katmanlarda `context.getString(...)` kullanılması gerekiyordu; fakat
 * her sınıfta erişilebilir bir `Context` bulunmuyor.
 *
 * **Dil davranışı**
 * Metin, o anda etkin olan yapılandırılmış kaynaklardan (`KitsugiApplication`
 * `getResources()` override'ı aktif Activity'nin kaynaklarını döner) çözülür.
 * Uygulama dili değiştirildiğinde Activity yeniden kurulduğu için metinler de
 * yeni dille yeniden çözülür.
 *
 * **Geri dönüş zinciri**
 * 1. Aktif Activity'nin kaynakları (doğru dil)
 * 2. Uygulama örneğinin kaynakları
 * 3. `attachBaseContext` anında saklanan uygulama bağlamı
 *
 * Hiçbiri yoksa (teorik olarak süreç kurulumunun en başı) çökme yerine boş
 * metin döner; bu durumda ekrana boş metin basılır, uygulama çalışmaya devam eder.
 */
object KitsugiStrings {

    @Volatile
    private var fallbackContext: Context? = null

    /**
     * Süreçteki en erken noktada ([KitsugiApplication.attachBaseContext])
     * çağrılır. Böylece ContentProvider'lar ve WorkManager gibi Application
     * `onCreate` öncesinde çalışan bileşenler de metin çözebilir.
     */
    internal fun install(context: Context?) {
        val app = context?.applicationContext ?: context
        if (app != null) fallbackContext = app
    }

    private fun resources(): android.content.res.Resources? =
        KitsugiApplication.activeActivity?.resources
            ?: KitsugiApplication.getInstance()?.resources
            ?: fallbackContext?.applicationContext?.resources

    /** Returns the locale configured for the app's currently active resources. */
    @JvmStatic
    fun locale(): Locale {
        val locales = resources()?.configuration?.locales
        return if (locales != null && locales.size() > 0) locales[0] else Locale.getDefault()
    }

    /** Kaynak metni, isteğe bağlı biçimlendirme argümanlarıyla döner. */
    @JvmStatic
    fun get(@StringRes resId: Int, vararg formatArgs: Any?): String {
        val resources = resources() ?: return ""
        if (formatArgs.isEmpty()) {
            return resources.getString(resId)
        }
        // 2026-11-02: bir çağrı yerinde argüman sayısı uyuşmazsa (örn. "%3$s") Android
        // MissingFormatArgumentException fırlatıyor ve bu sync akışını çökertiyordu.
        // Güvenlik: uyuşmazlıkta ham metni argümanlarla birlikte döndür, asla fırlatma.
        return try {
            resources.getString(resId, *formatArgs)
        } catch (_: java.util.IllegalFormatException) {
            val raw = runCatching { resources.getString(resId) }.getOrDefault("")
            if (raw.isBlank()) formatArgs.joinToString(" ") { it?.toString().orEmpty() }
            else raw + " [" + formatArgs.joinToString(", ") { it?.toString().orEmpty() } + "]"
        }
    }

    /** Kaynak metni döner; kaynak bulunamazsa [fallback] kullanılır. */
    @JvmStatic
    fun getOrFallback(@StringRes resId: Int, fallback: String, vararg formatArgs: Any?): String {
        val value = get(resId, *formatArgs)
        return value.ifBlank { fallback }
    }
}
