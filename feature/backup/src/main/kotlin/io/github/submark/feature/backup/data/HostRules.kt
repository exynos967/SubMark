package io.github.submark.feature.backup.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * WebDAV endpoint validation. HTTPS is always fine; plain HTTP is accepted only when the profile
 * explicitly allows it [allowHttpLocal] and the host is loopback, RFC1918, link-local or `.local`.
 * Pure logic — unit-tested without Android.
 */
object HostRules {

    sealed interface Verdict {
        data object Ok : Verdict
        data object InvalidUrl : Verdict
        /** http:// without a local host. */
        data object PlainHttpNotAllowed : Verdict
        /** http allowed by profile but the host is public. */
        data object HostNotLocal : Verdict
    }

    fun check(url: String, allowHttpLocal: Boolean): Verdict {
        val http = url.toHttpUrlOrNull() ?: return Verdict.InvalidUrl
        return check(http, allowHttpLocal)
    }

    fun check(url: HttpUrl, allowHttpLocal: Boolean): Verdict {
        if (url.scheme == "https") return Verdict.Ok
        if (url.scheme != "http") return Verdict.InvalidUrl
        if (!allowHttpLocal) return Verdict.PlainHttpNotAllowed
        return if (isLocalHostName(url.host)) Verdict.Ok else Verdict.HostNotLocal
    }

    /**
     * True for loopback (127.0.0.0/8, ::1), RFC1918 (10/8, 172.16/12, 192.168/16),
     * link-local (169.254/16, fe80::/10) and `.local` mDNS names, plus bare `localhost`.
     * IPv6 addresses may carry brackets; a %-zone id is stripped.
     */
    fun isLocalHostName(host: String): Boolean {
        var h = host.trim().lowercase()
        if (h.isEmpty()) return false
        if (h == "localhost" || h.endsWith(".local")) return true
        if (h.startsWith("[") && h.endsWith("]")) h = h.substring(1, h.length - 1)
        // Strip an IPv6 zone id (fe80::1%wlan0).
        val zoneIdx = h.indexOf('%')
        if (zoneIdx > 0) h = h.substring(0, zoneIdx)

        if (h.contains(':')) return isLocalIpv6(h)
        return isLocalIpv4(h)
    }

    private fun isLocalIpv6(h: String): Boolean {
        if (h == "::1" || h == "0:0:0:0:0:0:0:1") return true
        // Expand enough to test the two leading hextets: loopback and link-local fe80::/10.
        val parts = h.split("::")
        val head = parts[0]
        val first = head.substringBefore(':').toIntOrNull(16) ?: return false
        val headCount = if (head.isEmpty()) 0 else head.split(':').size
        // fe80::/10 → top 10 bits are 1111111010 → first hextet in fe80..febf.
        if (first in 0xfe80..0xfebf) return true
        // ::1 with the leading "::" requires every other hextet to be zero and the last to be 1.
        if (parts.size == 2) {
            val tail = parts[1]
            val tailParts = if (tail.isEmpty()) emptyList() else tail.split(':')
            if (headCount == 0 && tailParts.size == 1 && tailParts[0].toIntOrNull(16) == 1) return true
        }
        // IPv4-mapped IPv6, e.g. ::ffff:127.0.0.1.
        val mapped = h.substringAfterLast(':').takeIf { it.contains('.') }
        if (mapped != null && (h.startsWith("::ffff:") || h.startsWith("0:0:0:0:0:ffff:"))) {
            return isLocalIpv4(mapped)
        }
        return false
    }

    private fun isLocalIpv4(h: String): Boolean {
        val segs = h.split('.')
        if (segs.size != 4) return false
        val b = segs.map { it.toIntOrNull()?.takeIf { n -> n in 0..255 } ?: return false }
        return when {
            b[0] == 127 -> true // loopback
            b[0] == 10 -> true // RFC1918
            b[0] == 172 && b[1] in 16..31 -> true // RFC1918
            b[0] == 192 && b[1] == 168 -> true // RFC1918
            b[0] == 169 && b[1] == 254 -> true // link-local
            else -> false
        }
    }
}
