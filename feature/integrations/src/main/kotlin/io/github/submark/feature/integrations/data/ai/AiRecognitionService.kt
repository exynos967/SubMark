package io.github.submark.feature.integrations.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.submark.core.data.secret.SecretStore
import io.github.submark.core.data.settings.AiProvider
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.model.BillingCycle
import io.github.submark.core.model.SecretKeys
import io.github.submark.core.model.SubscriptionPrefill
import io.github.submark.core.model.SystemCategory
import io.github.submark.core.model.SubscriptionKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** One recognized subscription candidate from a screenshot. */
data class RecognitionResult(
    val name: String,
    val price: BigDecimal? = null,
    val currency: String? = null,
    val billingCycle: BillingCycle? = null,
    val isLifetime: Boolean = false,
    val category: SystemCategory? = null,
    val firstPaymentDate: LocalDate? = null,
    val expirationDate: LocalDate? = null,
    val website: String? = null,
    val notes: String? = null,
    val confidence: Double? = null,
) {
    fun toPrefill(): SubscriptionPrefill = SubscriptionPrefill(
        name = name,
        kind = if (isLifetime) SubscriptionKind.LIFETIME else SubscriptionKind.REGULAR,
        price = price,
        currencyCode = currency?.uppercase(),
        billingCycle = if (isLifetime) null else billingCycle,
        systemCategory = category,
        website = website,
        note = notes,
        startDate = firstPaymentDate,
        endDate = expirationDate,
    )
}

@Serializable
private data class AiItem(
    val name: String? = null,
    val price: kotlinx.serialization.json.JsonElement? = null,
    val currency: String? = null,
    val billingCycle: String? = null,
    val isLifetime: Boolean? = null,
    val isPermanent: Boolean? = null,
    val category: String? = null,
    val firstPaymentDate: String? = null,
    val expirationDate: String? = null,
    val website: String? = null,
    val notes: String? = null,
    val description: String? = null,
    val confidence: kotlinx.serialization.json.JsonElement? = null,
)

@Serializable
private data class ChatMessage(val role: String, val content: kotlinx.serialization.json.JsonElement)

@Serializable
private data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.0,
    @kotlinx.serialization.SerialName("response_format") val responseFormat: kotlinx.serialization.json.JsonObject? = null,
)

@Serializable
private data class ChatChoice(val message: ChatMessage? = null)

@Serializable
private data class ChatResponse(val choices: List<ChatChoice> = emptyList())

sealed interface AiError {
    /** Provider/key/model/endpoint missing. */
    data object InvalidConfig : AiError
    data object Disabled : AiError
    data object ImageProcessing : AiError
    data class Http(val code: Int) : AiError
    data object Network : AiError
    data object EmptyResponse : AiError
    data object InvalidJson : AiError
}

/**
 * Screenshot → subscription recognition through any OpenAI-compatible chat-completions API.
 * Key lives in [SecretKeys.AI_API_KEY]; provider/endpoint/model in [SettingsRepository].
 */
@Singleton
class AiRecognitionService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val secrets: SecretStore,
    private val settings: SettingsRepository,
    private val json: Json,
) {
    data class ActiveConfig(
        val provider: AiProvider,
        val apiKey: String,
        val endpoint: String,
        val model: String,
    ) {
        /** Qwen presets do not reliably honour `response_format`; omit it there. */
        val supportsJsonObjectFormat: Boolean get() = provider != AiProvider.QWEN
    }

    suspend fun apiKey(): String? = secrets.get(SecretKeys.AI_API_KEY)?.takeIf { it.isNotBlank() }

    suspend fun setApiKey(key: String) = secrets.put(SecretKeys.AI_API_KEY, key.trim())

    suspend fun clearApiKey() = secrets.remove(SecretKeys.AI_API_KEY)

    fun defaultEndpoint(provider: AiProvider): String = when (provider) {
        AiProvider.OPENAI -> "https://api.openai.com/v1"
        AiProvider.QWEN -> "https://dashscope.aliyuncs.com/compatible-mode/v1"
        AiProvider.CUSTOM -> ""
    }

    fun modelPresets(provider: AiProvider): List<String> = when (provider) {
        AiProvider.OPENAI -> listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo")
        AiProvider.QWEN -> listOf("qwen3.6-flash", "qwen3.6-plus", "qwen3.7-plus")
        AiProvider.CUSTOM -> emptyList()
    }

    /** Resolves the effective configuration; null pieces are reported via [AiError.InvalidConfig]. */
    suspend fun activeConfig(): Result<ActiveConfig> {
        val integration = settings.settings.first().integrations
        if (!integration.aiEnabled) return Result.failure(AiException(AiError.Disabled))
        val provider = integration.aiProvider ?: return Result.failure(AiException(AiError.InvalidConfig))
        val key = apiKey() ?: return Result.failure(AiException(AiError.InvalidConfig))
        val endpoint = (integration.aiEndpoint?.takeIf { it.isNotBlank() } ?: defaultEndpoint(provider))
            .takeIf { it.isNotBlank() }
            ?: return Result.failure(AiException(AiError.InvalidConfig))
        val model = integration.aiModel?.takeIf { it.isNotBlank() }
            ?: modelPresets(provider).firstOrNull()
            ?: return Result.failure(AiException(AiError.InvalidConfig))
        return Result.success(ActiveConfig(provider, key, endpoint.trimEnd('/'), model))
    }

    /** Verifies key+endpoint+model with a minimal non-image request. */
    suspend fun testConnection(): Result<Unit> {
        val config = activeConfig().getOrElse { return Result.failure(it) }
        val body = json.encodeToString(
            ChatRequest.serializer(),
            ChatRequest(
                model = config.model,
                messages = listOf(
                    ChatMessage("user", kotlinx.serialization.json.JsonPrimitive("Reply with: ok")),
                ),
            ),
        )
        return post(config, body).map { }
    }

    /** Reads [imagePath]/uri → base64 JPEG (long side ≤ 1280 px). */
    suspend fun encodeImage(path: String): String? = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(path, bounds) }.getOrNull()
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (maxSide / (sample * 2) > 1280) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = runCatching { BitmapFactory.decodeFile(path, opts) }.getOrNull() ?: return@withContext null
        val scale = if (maxOf(decoded.width, decoded.height) > 1280) {
            1280f / maxOf(decoded.width, decoded.height)
        } else 1f
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else decoded
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /** Copies a content uri (photo picker) into the cache and returns the file path. */
    suspend fun importImage(uri: android.net.Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = java.io.File(context.cacheDir, "ai").apply { mkdirs() }
            val file = java.io.File(dir, "${io.github.submark.core.model.newId()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { input.copyTo(it) }
            } ?: return@withContext null
            file.absolutePath
        }.getOrNull()
    }

    /** Runs recognition over a local image path. */
    suspend fun recognize(imagePath: String): Result<List<RecognitionResult>> {
        val config = activeConfig().getOrElse { return Result.failure(it) }
        val base64 = encodeImage(imagePath) ?: return Result.failure(AiException(AiError.ImageProcessing))
        val content = kotlinx.serialization.json.buildJsonArray {
            add(kotlinx.serialization.json.buildJsonObject {
                put("type", kotlinx.serialization.json.JsonPrimitive("text"))
                put("text", kotlinx.serialization.json.JsonPrimitive(USER_PROMPT))
            })
            add(kotlinx.serialization.json.buildJsonObject {
                put("type", kotlinx.serialization.json.JsonPrimitive("image_url"))
                put("image_url", kotlinx.serialization.json.buildJsonObject {
                    put("url", kotlinx.serialization.json.JsonPrimitive("data:image/jpeg;base64,$base64"))
                })
            })
        }
        val request = ChatRequest(
            model = config.model,
            messages = listOf(
                ChatMessage("system", kotlinx.serialization.json.JsonPrimitive(SYSTEM_PROMPT)),
                ChatMessage("user", content),
            ),
            responseFormat = if (config.supportsJsonObjectFormat) {
                kotlinx.serialization.json.buildJsonObject { put("type", kotlinx.serialization.json.JsonPrimitive("json_object")) }
            } else null,
        )
        val body = json.encodeToString(ChatRequest.serializer(), request)
        return post(config, body).mapCatching { text ->
            parseResults(text).takeIf { it.isNotEmpty() } ?: throw AiException(AiError.InvalidJson)
        }
    }

    private suspend fun post(config: ActiveConfig, body: String): Result<String> = withContext(Dispatchers.IO) {
        val url = runCatching { "${config.endpoint}/chat/completions".toHttpUrl() }
            .getOrElse { return@withContext Result.failure(AiException(AiError.InvalidConfig)) }
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.apiKey}")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.failure(AiException(AiError.Http(resp.code)))
                val raw = resp.body?.string() ?: return@withContext Result.failure(AiException(AiError.EmptyResponse))
                val parsed = try {
                    json.decodeFromString<ChatResponse>(raw)
                } catch (e: Exception) {
                    return@withContext Result.failure(AiException(AiError.InvalidJson))
                }
                val content = parsed.choices.firstOrNull()?.message?.content
                    ?: return@withContext Result.failure(AiException(AiError.EmptyResponse))
                val text = (content as? kotlinx.serialization.json.JsonPrimitive)?.content
                    ?: return@withContext Result.failure(AiException(AiError.InvalidJson))
                if (text.isBlank()) Result.failure(AiException(AiError.EmptyResponse)) else Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(AiException(AiError.Network))
        }
    }

    /**
     * Tolerant parse of the model's reply: strips ``` fences, accepts a bare JSON array or an
     * object that wraps one (`subscriptions` / `results` / `items` / `data` or the first array value).
     */
    fun parseResults(text: String): List<RecognitionResult> = parse(text, json)

    companion object {
        private val defaultJson = Json { ignoreUnknownKeys = true }

        /** Pure variant of [parseResults] for tests. */
        fun parse(text: String, json: Json = defaultJson): List<RecognitionResult> {
            val cleaned = text.trim()
                .removePrefix("```json").removePrefix("```JSON").removePrefix("```")
                .removeSuffix("```")
                .trim()
            val element = try {
                json.parseToJsonElement(cleaned)
            } catch (e: Exception) {
                // Some models prepend prose; try the first JSON-looking slice.
                val start = cleaned.indexOfFirst { it == '[' || it == '{' }
                val end = cleaned.indexOfLast { it == ']' || it == '}' }
                if (start < 0 || end <= start) return emptyList()
                try {
                    json.parseToJsonElement(cleaned.substring(start, end + 1))
                } catch (e2: Exception) {
                    return emptyList()
                }
            }
            val array: kotlinx.serialization.json.JsonArray? = when (element) {
                is kotlinx.serialization.json.JsonArray -> element
                is kotlinx.serialization.json.JsonObject -> {
                    listOf("subscriptions", "results", "items", "data")
                        .firstNotNullOfOrNull { (element[it] as? kotlinx.serialization.json.JsonArray) }
                        ?: element.values.filterIsInstance<kotlinx.serialization.json.JsonArray>().firstOrNull()
                        // Single object result.
                        ?: kotlinx.serialization.json.JsonArray(listOf(element))
                }
                else -> null
            } ?: return emptyList()
            return array.mapNotNull { el ->
                val item = try {
                    json.decodeFromJsonElement(AiItem.serializer(), el)
                } catch (e: Exception) {
                    return@mapNotNull null
                }
                val name = item.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                RecognitionResult(
                    name = name,
                    price = item.price?.asBigDecimal(),
                    currency = item.currency?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(),
                    billingCycle = parseCycle(item.billingCycle),
                    isLifetime = item.isLifetime ?: item.isPermanent ?: false,
                    category = parseCategory(item.category),
                    firstPaymentDate = parseDate(item.firstPaymentDate),
                    expirationDate = parseDate(item.expirationDate),
                    website = item.website?.trim()?.takeIf { it.isNotEmpty() },
                    notes = (item.notes ?: item.description)?.trim()?.takeIf { it.isNotEmpty() },
                    confidence = item.confidence?.asDouble()?.coerceIn(0.0, 1.0),
                )
            }
        }

        private fun kotlinx.serialization.json.JsonElement.asBigDecimal(): BigDecimal? =
            (this as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBigDecimalOrNull()

        private fun kotlinx.serialization.json.JsonElement.asDouble(): Double? =
            (this as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()

        private fun parseCycle(raw: String?): BillingCycle? = when (raw?.trim()?.lowercase()) {
            "weekly" -> BillingCycle.WEEKLY
            "monthly" -> BillingCycle.MONTHLY
            "quarterly" -> BillingCycle.QUARTERLY
            "semiannually", "semiannual", "semi-annually", "halfyearly" -> BillingCycle.SEMIANNUALLY
            "annually", "yearly" -> BillingCycle.ANNUALLY
            "custom" -> BillingCycle.CUSTOM
            else -> null
        }

        private fun parseCategory(raw: String?): SystemCategory? = when (raw?.trim()?.lowercase()) {
            "video" -> SystemCategory.VIDEO
            "music" -> SystemCategory.MUSIC
            "entertainment" -> SystemCategory.ENTERTAINMENT
            "gaming", "games", "game" -> SystemCategory.GAMING
            "productivity" -> SystemCategory.PRODUCTIVITY
            "utility", "utilities" -> SystemCategory.UTILITY
            "ai" -> SystemCategory.AI
            "news" -> SystemCategory.NEWS
            "lifestyle" -> SystemCategory.LIFESTYLE
            "other" -> SystemCategory.OTHER
            else -> null
        }

        private fun parseDate(raw: String?): LocalDate? =
            raw?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

        const val SYSTEM_PROMPT =
            "You extract subscription information from screenshots of app store pages, pricing pages " +
                "and payment screens. Reply ONLY with JSON — either an array or an object with a " +
                "\"subscriptions\" array — where each item has: name (string, required), price (number), " +
                "currency (ISO-4217 code), billingCycle (one of weekly, monthly, quarterly, semiannually, " +
                "annually, custom), isLifetime (boolean, true for one-time purchases), category (one of " +
                "video, music, entertainment, gaming, productivity, utility, ai, news, lifestyle, other), " +
                "firstPaymentDate and expirationDate (YYYY-MM-DD or null), website (URL or null), " +
                "notes (string or null), confidence (0..1). Omit fields you cannot determine. " +
                "Return an empty array when the screenshot shows no subscription."

        private const val USER_PROMPT = "Extract every subscription visible in this screenshot."
    }
}

class AiException(val error: AiError) : Exception(error.toString())
