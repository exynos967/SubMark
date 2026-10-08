package io.github.submark.feature.integrations.panel.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant

/**
 * Hand-rolled (en/de)coding for [BudgetSnapshot]/[ClashSnapshot]/[EmbySnapshot] so the stored JSON
 * stays stable and independent of data-class renames. Unknown keys on decode are ignored.
 */
object SnapshotCodec {

    private val json = Json { ignoreUnknownKeys = true }

    fun encodeBudget(snapshot: BudgetSnapshot): String {
        val balances = JsonArray(snapshot.balances.map { b ->
            JsonObject(LinkedHashMap<String, JsonElement>().apply {
                putStr("ccs", b.currencyCode)
                putStr("t", b.total)
                putStr("g", b.granted)
                putStr("tu", b.toppedUp)
            })
        })
        val quotas = JsonArray(snapshot.quotas.map { q ->
            JsonObject(LinkedHashMap<String, JsonElement>().apply {
                putStr("k", q.kind)
                putStr("u", q.used.toString())
                putStr("l", q.limit.toString())
                q.percentageUsed?.let { putStr("p", it.toString()) }
                q.resetAt?.let { putStr("r", it.epochSecond.toString()) }
                putStr("c", if (q.isCount) "1" else "0")
            })
        })
        val obj = LinkedHashMap<String, JsonElement>().apply {
            put("v", JsonPrimitive(1))
            putStr("st", snapshot.serviceType)
            put("b", balances)
            putStr("ua", snapshot.usedAmount)
            putStr("la", snapshot.limitAmount)
            putStr("cc", snapshot.currencyCode)
            putStr("ds", snapshot.dailySpentAmount)
            snapshot.totalRequests?.let { putStr("tr", it.toString()) }
            snapshot.totalTokens?.let { putStr("tt", it.toString()) }
            put("q", quotas)
            snapshot.expiryAt?.let { putStr("ex", it.epochSecond.toString()) }
            snapshot.accessUntil?.let { putStr("au", it.epochSecond.toString()) }
            putStr("aul", if (snapshot.accessUnlimited) "1" else "0")
            putStr("pn", snapshot.planName)
            putStr("role", snapshot.userRole)
            putStr("us", snapshot.userStatus)
            putStr("aid", snapshot.accountId)
            putStr("f", snapshot.fetchedAt.epochSecond.toString())
            putStr("ba", if (snapshot.isBalanceAvailable) "1" else "0")
            putStr("raw", snapshot.rawJson)
        }
        return JsonObject(obj).toString()
    }

    fun decodeBudget(text: String): BudgetSnapshot? = runCatching {
        val o = json.parseToJsonElement(text).jsonObject
        val balances = (o["b"] as? JsonArray)?.mapNotNull { el ->
            (el as? JsonObject)?.let { b ->
                BudgetSnapshot.Balance(
                    currencyCode = b.strKey("ccs") ?: return@let null,
                    total = b.strKey("t") ?: return@let null,
                    granted = b.strKey("g"),
                    toppedUp = b.strKey("tu"),
                )
            }
        } ?: emptyList()
        val quotas = (o["q"] as? JsonArray)?.mapNotNull { el ->
            (el as? JsonObject)?.let { q ->
                BudgetSnapshot.QuotaWindow(
                    kind = q.str("k") ?: return@let null,
                    used = q.numLong("u") ?: 0,
                    limit = q.numLong("l") ?: 0,
                    percentageUsed = q.numLong("p")?.toInt(),
                    resetAt = q.numLong("r")?.let(Instant::ofEpochSecond),
                    isCount = q.str("c") == "1",
                )
            }
        } ?: emptyList()
        BudgetSnapshot(
            serviceType = o.strKey("st") ?: return null,
            balances = balances,
            usedAmount = o.strKey("ua"),
            limitAmount = o.strKey("la"),
            currencyCode = o.strKey("cc") ?: "USD",
            dailySpentAmount = o.strKey("ds"),
            totalRequests = o.numLong("tr"),
            totalTokens = o.numLong("tt"),
            quotas = quotas,
            expiryAt = o.numLong("ex")?.let(Instant::ofEpochSecond),
            accessUntil = o.numLong("au")?.let(Instant::ofEpochSecond),
            accessUnlimited = o.strKey("aul") == "1",
            planName = o.strKey("pn"),
            userRole = o.strKey("role"),
            userStatus = o.strKey("us"),
            accountId = o.strKey("aid"),
            fetchedAt = o.numLong("f")?.let(Instant::ofEpochSecond) ?: return null,
            isBalanceAvailable = o.strKey("ba") != "0",
            rawJson = o.strKey("raw") ?: "",
        )
    }.getOrNull()

    fun encodeClash(s: ClashSnapshot): String {
        val obj = LinkedHashMap<String, JsonElement>().apply {
            put("v", JsonPrimitive(1))
            putStr("t", "clash")
            putStr("info", if (s.hasTrafficInfo) "1" else "0")
            putStr("up", s.uploadBytes.toString())
            putStr("dl", s.downloadBytes.toString())
            s.totalBytes?.let { putStr("tot", it.toString()) }
            s.expireAt?.let { putStr("ex", it.epochSecond.toString()) }
            putStr("f", s.fetchedAt.epochSecond.toString())
        }
        return JsonObject(obj).toString()
    }

    fun encodeEmby(s: EmbySnapshot): String {
        val obj = LinkedHashMap<String, JsonElement>().apply {
            put("v", JsonPrimitive(1))
            putStr("t", "emby")
            putStr("sn", s.serverName)
            putStr("ver", s.version)
            putStr("os", s.operatingSystem)
            putStr("un", s.userName)
            putStr("adm", if (s.isAdmin) "1" else "0")
            s.lastActiveAt?.let { putStr("la", it.epochSecond.toString()) }
            s.latencyMs?.let { putStr("lat", it.toString()) }
            putStr("ok", if (s.reachable) "1" else "0")
            putStr("f", s.fetchedAt.epochSecond.toString())
        }
        return JsonObject(obj).toString()
    }

    fun decodeService(text: String): ServiceSnapshot? = runCatching {
        val o = json.parseToJsonElement(text).jsonObject
        when (o.strKey("t")) {
            "clash" -> ClashSnapshot(
                hasTrafficInfo = o.strKey("info") != "0",
                uploadBytes = o.numLong("up") ?: 0,
                downloadBytes = o.numLong("dl") ?: 0,
                totalBytes = o.numLong("tot"),
                expireAt = o.numLong("ex")?.let(Instant::ofEpochSecond),
                fetchedAt = o.numLong("f")?.let(Instant::ofEpochSecond) ?: return@runCatching null,
            )
            "emby" -> EmbySnapshot(
                serverName = o.strKey("sn"),
                version = o.strKey("ver"),
                operatingSystem = o.strKey("os"),
                userName = o.strKey("un"),
                isAdmin = o.strKey("adm") == "1",
                lastActiveAt = o.numLong("la")?.let(Instant::ofEpochSecond),
                latencyMs = o.numLong("lat"),
                reachable = o.strKey("ok") != "0",
                fetchedAt = o.numLong("f")?.let(Instant::ofEpochSecond) ?: return null,
            )
            else -> null
        }
    }.getOrNull()
}

private fun MutableMap<String, JsonElement>.putStr(key: String, value: String?) {
    if (value != null && value.isNotEmpty()) this[key] = JsonPrimitive(value)
}

private fun JsonObject.strKey(key: String): String? =
    (this[key] as? JsonPrimitive)?.jsonPrimitive?.content
        ?.takeIf { this[key] !is kotlinx.serialization.json.JsonNull && it.isNotEmpty() }
private fun JsonObject.numLong(key: String): Long? =
    (this[key] as? JsonPrimitive)?.longOrNull ?: (this[key] as? JsonPrimitive)?.content?.toLongOrNull()
