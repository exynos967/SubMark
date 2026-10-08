package io.github.submark.feature.integrations.panel.data.service

import io.github.submark.feature.integrations.panel.data.ClashSnapshot
import java.time.Instant

/**
 * Parser for a Clash subscription URL's `subscription-userinfo` response header:
 * `upload=123; download=456; total=1099511627776; expire=1735689600`
 *
 * - upload/download/total are byte counts; expire is epoch seconds.
 * - Keys are matched case-insensitively; unknown keys are ignored.
 * - Returns a snapshot with [ClashSnapshot.hasTrafficInfo] = false when the header is absent
 *   or carries no usable values (spec: "link valid but no traffic statistics").
 */
object SubscriptionUserinfo {

    data class Parsed(
        val upload: Long?,
        val download: Long?,
        val total: Long?,
        val expireEpochSeconds: Long?,
    ) {
        val hasAny: Boolean get() = upload != null || download != null || total != null || expireEpochSeconds != null
    }

    fun parse(header: String?): Parsed? {
        if (header.isNullOrBlank()) return null
        val map = buildMap {
            header.split(';').forEach { part ->
                val eq = part.indexOf('=')
                if (eq <= 0) return@forEach
                val key = part.substring(0, eq).trim().lowercase()
                val value = part.substring(eq + 1).trim().toLongOrNull() ?: return@forEach
                put(key, value)
            }
        }
        if (map.isEmpty()) return null
        return Parsed(
            upload = map["upload"],
            download = map["download"],
            total = map["total"],
            expireEpochSeconds = map["expire"],
        )
    }

    fun toSnapshot(header: String?, now: Instant): ClashSnapshot {
        val parsed = parse(header)
        if (parsed == null || !parsed.hasAny) return ClashSnapshot(hasTrafficInfo = false, fetchedAt = now)
        return ClashSnapshot(
            hasTrafficInfo = true,
            uploadBytes = parsed.upload ?: 0,
            downloadBytes = parsed.download ?: 0,
            totalBytes = parsed.total?.takeIf { it > 0 },
            expireAt = parsed.expireEpochSeconds?.takeIf { it > 0 }?.let(Instant::ofEpochSecond),
            fetchedAt = now,
        )
    }
}
