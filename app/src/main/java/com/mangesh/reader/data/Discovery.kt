package com.mangesh.reader.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.InetAddress
import kotlin.coroutines.resume

/**
 * Finds the Mac's server on the LAN over Bonjour (`_vibes._tcp`, advertised by
 * ~/vibes/server/serve.py). Android apps often cannot resolve `.local` names
 * and the Mac's DHCP address moves, so the name in Settings is only a fallback.
 */
object Discovery {
    private const val TYPE = "_vibes._tcp."

    /** "http://192.168.1.188:8642" or null if nothing answered within the timeout. */
    suspend fun find(context: Context, timeoutMs: Long = 3000): String? {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return null
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                var done = false
                lateinit var listener: NsdManager.DiscoveryListener
                fun finish(result: String?) {
                    if (done) return
                    done = true
                    try { nsd.stopServiceDiscovery(listener) } catch (_: Exception) {}
                    cont.resume(result)
                }
                listener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(type: String) {}
                    override fun onDiscoveryStopped(type: String) {}
                    override fun onStartDiscoveryFailed(type: String, code: Int) { finish(null) }
                    override fun onStopDiscoveryFailed(type: String, code: Int) {}
                    override fun onServiceLost(info: NsdServiceInfo) {}
                    override fun onServiceFound(info: NsdServiceInfo) {
                        if (info.serviceType.trimEnd('.') != TYPE.trimEnd('.')) return
                        @Suppress("DEPRECATION")
                        nsd.resolveService(info, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(i: NsdServiceInfo, code: Int) {}
                            override fun onServiceResolved(i: NsdServiceInfo) {
                                val addrs: List<InetAddress> =
                                    if (Build.VERSION.SDK_INT >= 34) i.hostAddresses else listOfNotNull(i.host)
                                val addr = addrs.firstOrNull { it is Inet4Address } ?: addrs.firstOrNull() ?: return
                                val host = addr.hostAddress?.substringBefore('%') ?: return
                                finish("http://${if (host.contains(':')) "[$host]" else host}:${i.port}")
                            }
                        })
                    }
                }
                cont.invokeOnCancellation { try { nsd.stopServiceDiscovery(listener) } catch (_: Exception) {} }
                try {
                    nsd.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
                } catch (e: Exception) {
                    finish(null)
                }
            }
        }
    }
}
