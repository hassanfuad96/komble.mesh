package com.bitchat.android.printer

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

data class DiscoveredPrinter(
    val name: String,
    val host: String,
    val port: Int,
    val serviceType: String
)

class PrinterDiscoveryManager(private val context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val listeners = mutableListOf<NsdManager.DiscoveryListener>()

    fun startDiscovery(
        serviceTypes: List<String> = listOf(
            "_pdl-datastream._tcp.", // HP JetDirect/raw (9100)
            "_printer._tcp.",        // Generic printer service
            "_ipps._tcp.",           // IPP over TLS
            "_ipp._tcp."             // IPP
        ),
        onFound: (DiscoveredPrinter) -> Unit,
        onError: (String) -> Unit
    ) {
        // Create a separate discovery listener per service type
        serviceTypes.forEach { type ->
            val discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                    onError("Discovery start failed: $serviceType code=$errorCode")
                }

                override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                    onError("Discovery stop failed: $serviceType code=$errorCode")
                }

                override fun onDiscoveryStarted(serviceType: String?) {
                    Log.d(TAG, "Discovery started: $serviceType")
                }

                override fun onDiscoveryStopped(serviceType: String?) {
                    Log.d(TAG, "Discovery stopped: $serviceType")
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    // Resolve to obtain host and port
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                            onError("Resolve failed: ${serviceInfo?.serviceName} code=$errorCode")
                        }

                        override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                            val hostAddr = resolvedInfo.host?.hostAddress ?: return
                            val port = resolvedInfo.port
                            val name = resolvedInfo.serviceName ?: "Printer"
                            val type = resolvedInfo.serviceType ?: type
                            onFound(
                                DiscoveredPrinter(
                                    name = name,
                                    host = hostAddr,
                                    port = port,
                                    serviceType = type
                                )
                            )
                        }
                    })
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
                }
            }

            listeners.add(discoveryListener)
            try {
                nsdManager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            } catch (e: Exception) {
                onError("discoverServices exception: ${e.message}")
            }
        }
    }

    fun stopDiscovery() {
        listeners.forEach { listener ->
            try { nsdManager.stopServiceDiscovery(listener) } catch (_: Exception) {}
        }
        listeners.clear()
    }

    companion object { private const val TAG = "PrinterDiscovery" }
}