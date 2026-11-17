@file:Suppress("SpellCheckingInspection")

package texteditor.server.data

// SPDX-License-Identifier: GPL-3.0-or-later

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.NetworkInterface

data class NetworkAddress(
    val address: String,
    val interfaceName: String,
    val isIpv6: Boolean = false,
)

class NetworkInfoProvider {
    private val interfacePriorityPrefixes = listOf(
        "wlan",
        "wlp",
        "swlan",
        "ap",
        "softap",
        "wigig",
        "eth",
        "enp",
        "eno",
        "ens",
        "p2p",
        "rndis",
        "usb",
        "lan",
        "rmnet",
        "ccmni",
        "ppp",
        "wwan",
    )

    fun scanAddresses(): List<NetworkAddress> {
        val addresses = mutableListOf<NetworkAddress>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces?.hasMoreElements() == true) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue

                val ifaceAddresses = iface.inetAddresses
                for (addr in ifaceAddresses) {
                    if (addr.isLoopbackAddress) continue
                    val host = addr.hostAddress ?: continue
                    when (addr) {
                        is Inet4Address -> {
                            addresses.add(
                                NetworkAddress(host, iface.name, isIpv6 = false),
                            )
                        }

                        is Inet6Address -> {
                            if (addr.isLinkLocalAddress) continue
                            val host = addr.hostAddress?.substringBefore('%') ?: continue
                            addresses.add(
                                NetworkAddress(host, iface.name, true),
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // caller handles an empty list
        }
        return addresses
    }

    fun pickDefault(addresses: List<NetworkAddress>): NetworkAddress? {
        val sorted = sortByPreference(addresses)
        for (prefix in interfacePriorityPrefixes) {
            val match = sorted.firstOrNull {
                !it.isIpv6 && it.interfaceName.lowercase().startsWith(prefix)
            }
            if (match != null) return match
        }
        return sorted.firstOrNull { !it.isIpv6 } ?: sorted.firstOrNull()
    }

    private fun sortByPreference(addresses: List<NetworkAddress>): List<NetworkAddress> =
        addresses.sortedWith(
            compareBy<NetworkAddress> {
                if (it.isIpv6) 1 else 0
            }.thenBy { it.interfaceName },
        )
}
