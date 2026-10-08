package io.github.submark.feature.share.data

import io.github.submark.core.model.BillingCycle
import io.github.submark.core.model.Category
import io.github.submark.core.model.CycleUnit
import io.github.submark.core.model.IconType
import io.github.submark.core.model.Money
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionPrefill
import io.github.submark.core.model.SystemCategory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Versioned QR payload: `submark://import?v=1&d=<base64url(deflate(json))>`.
 * The JSON is a compact DTO; notes/website are present only when the sharer opted in.
 */
object QrPayloadCodec {

    const val SCHEME_PREFIX = "submark://import"
    const val CURRENT_VERSION = 1
    /** Deflate stays effective only up to a point; reject absurd payloads up front. */
    private const val MAX_DECODED_BYTES = 64 * 1024
    private const val MAX_INFLATED_BYTES = 256 * 1024

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    @Serializable
    data class Payload(
        val v: Int = CURRENT_VERSION,
        val name: String,
        val kind: SubscriptionKind = SubscriptionKind.REGULAR,
        /** Price as plain string to keep JSON small and exact. */
        val price: String? = null,
        val currency: String? = null,
        val cycle: BillingCycle? = null,
        val cycleCount: Int? = null,
        val cycleUnit: CycleUnit? = null,
        /** System category key (e.g. "VIDEO"); custom categories are not portable. */
        val category: String? = null,
        val iconType: IconType? = null,
        val iconValue: String? = null,
        val website: String? = null,
        val notes: String? = null,
        val sharer: String? = null,
        val description: String? = null,
        val children: List<Payload> = emptyList(),
    )

    sealed interface DecodeResult {
        data class Success(val payload: Payload) : DecodeResult
        data object NotSubMark : DecodeResult
        data object UnsupportedVersion : DecodeResult
        data object Malformed : DecodeResult
    }

    /** Builds the payload for [sub]; [includePrivate] keeps website/notes, otherwise drops them. */
    fun fromSubscription(
        sub: Subscription,
        category: Category?,
        sharerName: String,
        description: String,
        includePrivate: Boolean,
        children: List<Subscription> = emptyList(),
        childCategories: Map<String, Category?> = emptyMap(),
    ): Payload = Payload(
        name = sub.name,
        kind = sub.kind,
        price = sub.price.toPlainString(),
        currency = sub.currencyCode,
        cycle = sub.billingCycle,
        cycleCount = sub.customCycleCount,
        cycleUnit = sub.customCycleUnit,
        category = category?.systemKey?.name,
        iconType = sub.iconType?.takeIf { it in portableIconTypes },
        iconValue = sub.iconValue?.takeIf { sub.iconType in portableIconTypes },
        website = sub.website?.takeIf { includePrivate },
        notes = sub.note?.takeIf { includePrivate },
        sharer = sharerName.ifBlank { null },
        description = description.ifBlank { null },
        children = children.map { child ->
            fromSubscription(
                sub = child,
                category = childCategories[child.id],
                sharerName = "",
                description = "",
                includePrivate = includePrivate,
            )
        },
    )

    /** Encodes to the full `submark://import?...` string. */
    fun encode(payload: Payload): String {
        val raw = json.encodeToString(Payload.serializer(), payload).encodeToByteArray()
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(raw)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            val n = deflater.deflate(buffer)
            out.write(buffer, 0, n)
        }
        deflater.end()
        val data = Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
        return "$SCHEME_PREFIX?v=${payload.v}&d=$data"
    }

    /** Decodes a `submark://import` string; tolerant of surrounding whitespace. */
    fun decode(text: String): DecodeResult {
        val trimmed = text.trim()
        if (!trimmed.startsWith(SCHEME_PREFIX)) return DecodeResult.NotSubMark
        val query = trimmed.substringAfter('?', "")
        val params = query.split('&').mapNotNull {
            val idx = it.indexOf('=')
            if (idx <= 0) null else it.substring(0, idx) to it.substring(idx + 1)
        }.toMap()
        val version = params["v"]?.toIntOrNull() ?: return DecodeResult.Malformed
        if (version != CURRENT_VERSION) return DecodeResult.UnsupportedVersion
        val data = params["d"] ?: return DecodeResult.Malformed

        val compressed = try {
            Base64.getUrlDecoder().decode(data)
        } catch (e: IllegalArgumentException) {
            return DecodeResult.Malformed
        }
        if (compressed.size > MAX_DECODED_BYTES) return DecodeResult.Malformed

        val raw = try {
            inflate(compressed)
        } catch (e: Exception) {
            return DecodeResult.Malformed
        } ?: return DecodeResult.Malformed

        if (raw.size > MAX_INFLATED_BYTES) return DecodeResult.Malformed

        return try {
            val payload = json.decodeFromString(Payload.serializer(), raw.decodeToString())
            if (payload.v != CURRENT_VERSION) DecodeResult.UnsupportedVersion else DecodeResult.Success(payload)
        } catch (e: Exception) {
            DecodeResult.Malformed
        }
    }

    private fun inflate(compressed: ByteArray): ByteArray? {
        val inflater = Inflater()
        inflater.setInput(compressed)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        return try {
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) return null
                }
                out.write(buffer, 0, n)
                if (out.size() > MAX_INFLATED_BYTES) return null
            }
            out.toByteArray()
        } finally {
            inflater.end()
        }
    }

    /** Maps the payload to a [SubscriptionPrefill] for the add-subscription form. */
    fun toPrefill(payload: Payload): SubscriptionPrefill = SubscriptionPrefill(
        name = payload.name,
        kind = when (payload.kind) {
            // WISHLIST/stored value imports as regular subscriptions.
            SubscriptionKind.WISHLIST, SubscriptionKind.STORED_VALUE -> SubscriptionKind.REGULAR
            else -> payload.kind
        },
        price = payload.price?.let { runCatching { BigDecimal(it) }.getOrNull() },
        currencyCode = payload.currency,
        billingCycle = payload.cycle,
        customCycleCount = payload.cycleCount,
        customCycleUnit = payload.cycleUnit,
        systemCategory = payload.category?.let { key -> SystemCategory.entries.firstOrNull { it.name == key } },
        iconType = payload.iconType?.takeIf { it in portableIconTypes },
        iconValue = payload.iconValue,
        website = payload.website,
        note = payload.notes,
        children = payload.children.map { toPrefill(it) },
    )

    /** Icon types safe to carry in a QR code (URL/SYMBOL/EMOJI per spec). */
    private val portableIconTypes = setOf(IconType.SYMBOL, IconType.EMOJI, IconType.URL)
}
