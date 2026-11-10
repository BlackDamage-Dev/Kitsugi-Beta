package com.kitsugi.animelist.core.network

import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Custom DNS that reorders resolved addresses to place IPv4 (Inet4Address)
 * before IPv6 (Inet6Address). This avoids 60s timeout delays on networks
 * with broken IPv6 routing.
 */
class IPv4FirstDns(private val delegate: Dns? = null) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val activeDelegate = delegate ?: DnsManager.getActiveDns()
        val addresses = try {
            activeDelegate.lookup(hostname)
        } catch (e: Exception) {
            // Fallback to system DNS if DoH query fails
            if (activeDelegate !== Dns.SYSTEM) {
                try {
                    Dns.SYSTEM.lookup(hostname)
                } catch (ex: Exception) {
                    fallbackLookup(hostname, e)
                }
            } else {
                fallbackLookup(hostname, e)
            }
        }
        return addresses.sortedBy { if (it is Inet4Address) 0 else 1 }
    }

    private fun fallbackLookup(hostname: String, originalError: Exception): List<InetAddress> {
        if (hostname.equals("codeberg.org", ignoreCase = true)) {
            return runCatching { listOf(InetAddress.getByName("217.197.84.140")) }.getOrElse { throw originalError }
        }
        runCatching { return Dns.SYSTEM.lookup(hostname) }

        // T-IND-1 (2026-11-09): Sistem DNS'i de çözümediyse DoH zincirine düş.
        // NEDEN: Türk kaynaklarının rotasyonlu CDN host'ları (srvN.cdnimagesNNNN.cyou,
        // sNNN.rapidrame.com …) bazı sağlayıcı DNS'lerinde NXDOMAIN / boş cevap alıyor ve
        // indirme "Unable to resolve host … No address associated with hostname" ile ölüyordu.
        // DoH örnekleri bootstrap IP'leriyle kurulduğu için burada isim çözümüne gerek yoktur
        // (sonsuz özyineleme riski yok). Örnekler tembel ve önbellekli kurulur: DNS hatası
        // yolu sıcak döngüde değildir, ama yine de her istekte yeniden kurulmaz.
        dohFallbacks().forEach { doh ->
            runCatching {
                val resolved = doh.lookup(hostname)
                if (resolved.isNotEmpty()) return resolved
            }
        }
        throw originalError
    }

    private val dohFallbackChain: List<Dns> by lazy {
        listOfNotNull(
            runCatching { DohProviders.cloudflare() }.getOrNull(),
            runCatching { DohProviders.google() }.getOrNull()
        )
    }

    private fun dohFallbacks(): List<Dns> = dohFallbackChain
}
