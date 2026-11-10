package com.kitsugi.animelist.data.cloudstream

import okhttp3.Call
import okhttp3.Callback
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class PluginNetworkFailureGuardTest {
    private val request = Request.Builder().url("https://example.com/").build()

    @Test
    fun asyncLinkageFailureBecomesIoFailureWithoutRetry() {
        val cause = NoClassDefFoundError("DiziBoxPlayerParser")
        val attempts = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor(PluginNetworkFailureGuard)
            .addInterceptor(Interceptor { attempts.incrementAndGet(); throw cause }).build()
        val failure = AtomicReference<IOException>()
        val done = CountDownLatch(1)
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { failure.set(e); done.countDown() }
            override fun onResponse(call: Call, response: Response) { response.close(); done.countDown() }
        })
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertSame(cause, failure.get()?.cause)
        assertEquals(1, attempts.get())
    }

    @Test
    fun initializerAndRuntimeFailuresAreWrappedButIoIsPreserved() {
        val failures = listOf(ExceptionInInitializerError("bad regex"), IllegalArgumentException("bad request"))
        for (cause in failures) {
            val client = OkHttpClient.Builder().addInterceptor(PluginNetworkFailureGuard)
                .addInterceptor(Interceptor { throw cause }).build()
            try { client.newCall(request).execute(); fail("expected IOException") }
            catch (e: IOException) { assertSame(cause, e.cause) }
        }
        val original = IOException("offline")
        val client = OkHttpClient.Builder().addInterceptor(PluginNetworkFailureGuard)
            .addInterceptor(Interceptor { throw original }).build()
        try { client.newCall(request).execute(); fail("expected IOException") }
        catch (e: IOException) { assertSame(original, e) }
    }

    @Test
    fun successAndFatalVmErrorsAreNotChanged() {
        val client = OkHttpClient.Builder().addInterceptor(PluginNetworkFailureGuard)
            .addInterceptor(Interceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body("ok".toResponseBody()).build()
            }).build()
        client.newCall(request).execute().use { assertEquals("ok", it.body!!.string()) }
        val fatal = OutOfMemoryError("test")
        val fatalClient = OkHttpClient.Builder().addInterceptor(PluginNetworkFailureGuard)
            .addInterceptor(Interceptor { throw fatal }).build()
        try { fatalClient.newCall(request).execute(); fail("expected fatal error") }
        catch (e: OutOfMemoryError) { assertSame(fatal, e) }
    }
}
