package io.github.submark.core.data.backup

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Rewrites values that older app versions exported but this one no longer has, mirroring the database
 * migrations: stored-value subscriptions become regular ones and their deposits become extra purchases.
 * Fields of removed tables (storedValueRecords, storedValueBalance) are simply ignored on decode.
 */
internal object LegacyExport {
    private val renames = mapOf(
        "subscriptions" to ("STORED_VALUE" to "REGULAR"),
        "paymentRecords" to ("STORED_VALUE_DEPOSIT" to "IN_APP_PURCHASE"),
    )

    fun upgrade(root: JsonElement): JsonElement {
        if (root !is JsonObject) return root
        return JsonObject(
            root.mapValues { (key, value) ->
                val (from, to) = renames[key] ?: return@mapValues value
                if (value !is JsonArray) return@mapValues value
                JsonArray(value.map { item -> renameKind(item, from, to) })
            },
        )
    }

    private fun renameKind(item: JsonElement, from: String, to: String): JsonElement {
        if (item !is JsonObject || item["kind"]?.jsonPrimitive?.contentOrNull != from) return item
        return JsonObject(item + ("kind" to JsonPrimitive(to)))
    }
}
