package com.kitsugi.animelist.data.cloudstream

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * OkHttp async calls only deliver IOExceptions to onFailure; unchecked exceptions and
 * linkage failures from dynamically loaded provider interceptors otherwise escape its
 * dispatcher thread and can terminate the process. Do not retry: the downstream chain
 * may already have sent a non-idempotent request or consumed its response.
 */
internal object PluginNetworkFailureGuard : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = try {
        chain.proceed(chain.request())
    } catch (error: LinkageError) {
        throw IOException("CloudStream network interceptor linkage failure", error)
    } catch (error: RuntimeException) {
        throw IOException("CloudStream network interceptor failure", error)
    }
}
