package com.iblu01.portallauncher

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log

/** A Home Assistant instance found on the local network. */
data class HaInstance(val name: String, val url: String)

/** mDNS/zeroconf service type Home Assistant advertises. */
private const val SERVICE_TYPE = "_home-assistant._tcp."
private const val TAG = "HaMdnsDiscovery"

/**
 * Build the HA base URL from a resolved service.
 * Prefers the TXT `internal_url`/`base_url` record; falls back to host:port.
 * Trailing slash trimmed to match Prefs.haUrl behavior. Returns null if unusable.
 */
fun deriveHaUrl(host: String?, port: Int, txt: Map<String, String>): String? {
    val advertised = (txt["internal_url"] ?: txt["base_url"])?.trim()?.takeIf { it.isNotEmpty() }
    val url = advertised ?: run {
        if (host.isNullOrBlank() || port <= 0) return null
        "http://$host:$port"
    }
    return url.trimEnd('/')
}

private fun NsdServiceInfo.txtMap(): Map<String, String> =
    attributes.orEmpty().mapNotNull { (k, v) ->
        if (v == null) null else k to String(v, Charsets.UTF_8)
    }.toMap()

/**
 * Wraps [NsdManager] discovery + resolve for HA instances.
 *
 * Callbacks arrive on a binder thread; results are marshalled to the main thread
 * before invoking [onUpdate]. NSD only resolves one service at a time on older
 * APIs, so resolves are serialized through a pending queue.
 *
 * Always call [stop] (e.g. from a DisposableEffect) — a leaked DiscoveryListener
 * throws on the next start and spams logcat.
 */
class HaMdnsDiscovery(context: Context) {

    private val nsd = context.applicationContext
        .getSystemService(Context.NSD_SERVICE) as? NsdManager

    private val main = Handler(Looper.getMainLooper())

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var onUpdate: ((List<HaInstance>) -> Unit)? = null

    /** De-duped results keyed by URL. Touched only on the main thread. */
    private val found = LinkedHashMap<String, HaInstance>()

    /** Resolve queue + in-flight guard. Touched only on the main thread. */
    private val pending = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    fun start(onUpdate: (List<HaInstance>) -> Unit) {
        val manager = nsd ?: return
        if (discoveryListener != null) return // already running
        this.onUpdate = onUpdate
        found.clear()
        pending.clear()
        resolving = false

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "start discovery failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "stop discovery failed: $errorCode")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                main.post { enqueueResolve(service) }
            }

            override fun onServiceLost(service: NsdServiceInfo) {}
        }
        discoveryListener = listener
        try {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.w(TAG, "discoverServices threw", e)
            discoveryListener = null
        }
    }

    fun stop() {
        val manager = nsd ?: return
        discoveryListener?.let {
            try {
                manager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                Log.w(TAG, "stopServiceDiscovery threw", e)
            }
        }
        discoveryListener = null
        onUpdate = null
        pending.clear()
        resolving = false
        found.clear()
    }

    private fun enqueueResolve(service: NsdServiceInfo) {
        pending.addLast(service)
        resolveNext()
    }

    private fun resolveNext() {
        if (resolving) return
        val manager = nsd ?: return
        val service = pending.removeFirstOrNull() ?: return
        resolving = true
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                main.post {
                    resolving = false
                    resolveNext()
                }
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                main.post {
                    val url = deriveHaUrl(
                        host = serviceInfo.host?.hostAddress,
                        port = serviceInfo.port,
                        txt = serviceInfo.txtMap(),
                    )
                    if (url != null && discoveryListener != null) {
                        val name = serviceInfo.serviceName.ifBlank { url }
                        if (found.put(url, HaInstance(name, url)) == null) {
                            onUpdate?.invoke(found.values.toList())
                        }
                    }
                    resolving = false
                    resolveNext()
                }
            }
        }
        try {
            manager.resolveService(service, resolveListener)
        } catch (e: Exception) {
            Log.w(TAG, "resolveService threw", e)
            resolving = false
            resolveNext()
        }
    }
}
