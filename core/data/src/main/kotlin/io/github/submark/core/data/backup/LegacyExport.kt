package io.github.submark.core.data.backup

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Rewrites values that older app versions exported but this one no longer has, mirroring the database
 * migrations: stored-value subscriptions become regular ones and their deposits become extra purchases;
 * a fixed payment day is folded into the anchor. Fields of removed data are simply ignored on decode.
 */
internal object LegacyExport {
    fun upgrade(root: JsonElement): JsonElement {
        if (root !is JsonObject) return root
        return JsonObject(
            root.mapValues { (key, value) ->
                if (value !is JsonArray) return@mapValues value
                when (key) {
                    "subscriptions" -> JsonArray(value.map { upgradeSubscription(it) })
                    "paymentRecords" -> JsonArray(value.map { renameKind(it, "STORED_VALUE_DEPOSIT", "IN_APP_PURCHASE") })
                    else -> value
                }
            },
        )
    }

    private fun upgradeSubscription(item: JsonElement): JsonElement {
        val renamed = renameKind(item, "STORED_VALUE", "REGULAR")
        if (renamed !is JsonObject) return renamed
        val next = renamed["nextPaymentDate"]
        val hasFixedDay = renamed["fixedPaymentDay"].let { it != null && it !is JsonNull }
        return if (hasFixedDay && next != null && next !is JsonNull) JsonObject(renamed + ("cycleAnchorDate" to next)) else renamed
    }

    private fun renameKind(item: JsonElement, from: String, to: String): JsonElement {
        if (item !is JsonObject || item["kind"]?.jsonPrimitive?.contentOrNull != from) return item
        return JsonObject(item + ("kind" to JsonPrimitive(to)))
    }
}
