package com.slncloth.shop.server

import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Discovers local IPv4 addresses that could be advertised to other phones.
 *
 * There is no portable Android API for "the hotspot address": it varies by manufacturer
 * and Android version (192.168.43.1 on older releases, 192.168.x.x on newer ones, or a
 * random range). We therefore enumerate interfaces, prefer hotspot-like names/addresses
 * and always let the owner override the address manually.
 */
object LocalAddresses {

    data class Candidate(
        val address: String,
        val interfaceName: String,
        val isHotspotLike: Boolean,
    )

    fun candidates(): List<Candidate> {
        val result = mutableListOf<Candidate>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return result
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        val host = address.hostAddress ?: continue
                        val name = iface.name.lowercase()
                        val hotspotLike = name.contains("ap") || name.contains("swlan") ||
                            name.contains("tether") || name.contains("wlan") ||
                            host.startsWith("192.168.43.")
                        result += Candidate(host, iface.name, hotspotLike)
                    }
                }
            }
        } catch (e: Exception) {
            // Network enumeration can throw on restricted devices; fall back to loopback.
        }
        return result.sortedWith(
            compareByDescending<Candidate> { it.isHotspotLike }.thenBy { it.address }
        )
    }

    /** Auto choice: a hotspot-looking interface first, then any local address. */
    fun preferred(): Candidate? = candidates().firstOrNull()

    fun isValidIpv4(value: String): Boolean {
        val parts = value.trim().split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            val n = part.toIntOrNull() ?: return@all false
            part.isNotEmpty() && part.length <= 3 && n in 0..255
        }
    }
}
