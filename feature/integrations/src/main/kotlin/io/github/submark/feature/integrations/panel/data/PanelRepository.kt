package io.github.submark.feature.integrations.panel.data

import io.github.submark.core.data.secret.SecretStore
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.database.dao.ApiBudgetDao
import io.github.submark.core.database.dao.ServiceConnectionDao
import io.github.submark.core.model.ApiBudgetConfig
import io.github.submark.core.model.ApiServiceType
import io.github.submark.core.model.SecretKeys
import io.github.submark.core.model.ServiceConnection
import io.github.submark.core.model.ServiceType
import io.github.submark.core.model.newId
import io.github.submark.feature.integrations.panel.data.budget.DeepSeekClient
import io.github.submark.feature.integrations.panel.data.budget.NewApiClient
import io.github.submark.feature.integrations.panel.data.budget.PackyClient
import io.github.submark.feature.integrations.panel.data.budget.ZaiClient
import io.github.submark.feature.integrations.panel.data.service.ClashClient
import io.github.submark.feature.integrations.panel.data.service.EmbyClient
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/** Form fields for creating/updating an API budget config (secret handled separately). */
data class ApiBudgetForm(
    val id: String? = null,
    val name: String,
    val serviceType: ApiServiceType,
    val apiKey: String,
    val baseUrl: String?,
    val userId: String?,
    val currencyCode: String,
    val monthlyBudget: BigDecimal?,
    val dailyBudget: BigDecimal?,
    val alertThreshold: Int,
    val enabled: Boolean,
)

/** Form fields for creating/updating a service connection (secrets handled separately). */
data class ServiceConnectionForm(
    val id: String? = null,
    val name: String,
    val type: ServiceType,
    val url: String,
    val username: String?,
    val password: String?,
    val apiKey: String?,
    val enabled: Boolean,
    val autoRefresh: Boolean,
    val refreshIntervalMinutes: Int,
    val colorHex: String?,
)

/**
 * Panel data access: budget configs + service connections, their secrets, and live refresh
 * against providers. Secrets never enter the database or the snapshot JSON.
 */
@Singleton
class PanelRepository @Inject constructor(
    private val apiBudgetDao: ApiBudgetDao,
    private val serviceDao: ServiceConnectionDao,
    private val secretStore: SecretStore,
    private val time: TimeProvider,
    private val deepSeek: DeepSeekClient,
    private val newApi: NewApiClient,
    private val packy: PackyClient,
    private val zai: ZaiClient,
    private val clash: ClashClient,
    private val emby: EmbyClient,
) {
    fun observeBudgets(): Flow<List<ApiBudgetConfig>> = apiBudgetDao.observeAll()
    fun observeServices(): Flow<List<ServiceConnection>> = serviceDao.observeAll()

    suspend fun getBudget(id: String): ApiBudgetConfig? = apiBudgetDao.get(id)
    suspend fun getService(id: String): ServiceConnection? = serviceDao.get(id)

    suspend fun budgetSecret(id: String): String? = secretStore.get(SecretKeys.apiBudget(id))
    suspend fun servicePassword(id: String): String? = secretStore.get(SecretKeys.servicePassword(id))
    suspend fun serviceApiKey(id: String): String? = secretStore.get(SecretKeys.serviceApiKey(id))

    /** Creates or updates a budget config; stores the secret when non-blank (blank keeps the old one). */
    suspend fun saveBudget(form: ApiBudgetForm): ApiBudgetConfig {
        val existing = form.id?.let { apiBudgetDao.get(it) }
        val now = time.now()
        // One config per service type (also enforced by the unique index).
        if (existing == null || existing.serviceType != form.serviceType) {
            val clashing = apiBudgetDao.getAll().firstOrNull { it.serviceType == form.serviceType && it.id != form.id }
            if (clashing != null) throw DuplicateServiceTypeException(form.serviceType)
        }
        val config = ApiBudgetConfig(
            id = existing?.id ?: newId(),
            name = form.name.trim(),
            serviceType = form.serviceType,
            baseUrl = form.baseUrl?.trim()?.ifBlank { null },
            userId = form.userId?.trim()?.ifBlank { null },
            currencyCode = form.currencyCode.trim().uppercase().ifBlank { "USD" },
            monthlyBudget = form.monthlyBudget,
            dailyBudget = form.dailyBudget,
            alertThreshold = form.alertThreshold,
            enabled = form.enabled,
            lastUpdatedAt = existing?.lastUpdatedAt,
            lastError = existing?.lastError,
            snapshotJson = existing?.snapshotJson,
            createdAt = existing?.createdAt ?: now,
        )
        apiBudgetDao.upsert(config)
        if (form.apiKey.isNotBlank()) secretStore.put(SecretKeys.apiBudget(config.id), form.apiKey.trim())
        return config
    }

    suspend fun deleteBudget(id: String) {
        apiBudgetDao.get(id)?.let { apiBudgetDao.delete(it) }
        secretStore.remove(SecretKeys.apiBudget(id))
    }

    /** Creates or updates a service connection; blank secrets keep the stored ones. */
    suspend fun saveService(form: ServiceConnectionForm): ServiceConnection {
        val existing = form.id?.let { serviceDao.get(it) }
        val now = time.now()
        val connection = ServiceConnection(
            id = existing?.id ?: newId(),
            name = form.name.trim(),
            type = form.type,
            url = form.url.trim(),
            username = form.username?.trim()?.ifBlank { null },
            enabled = form.enabled,
            autoRefresh = form.autoRefresh,
            refreshIntervalMinutes = form.refreshIntervalMinutes.coerceAtLeast(15),
            colorHex = form.colorHex,
            lastRefreshAt = existing?.lastRefreshAt,
            lastError = existing?.lastError,
            snapshotJson = if (existing != null && existing.type == form.type && existing.url == form.url.trim()) {
                existing.snapshotJson
            } else {
                null // type or endpoint changed: old snapshot no longer describes this connection
            },
            sortOrder = existing?.sortOrder ?: 0,
            createdAt = existing?.createdAt ?: now,
        )
        serviceDao.upsert(connection)
        if (!form.password.isNullOrBlank()) secretStore.put(SecretKeys.servicePassword(connection.id), form.password.trim())
        if (!form.apiKey.isNullOrBlank()) secretStore.put(SecretKeys.serviceApiKey(connection.id), form.apiKey.trim())
        // Password/auth cleared explicitly are removed when the other auth mode is used.
        if (form.password != null && form.password.isBlank()) Unit // keep-as-is semantics: blank = unchanged
        return connection
    }

    suspend fun deleteService(id: String) {
        serviceDao.get(id)?.let { serviceDao.delete(it) }
        secretStore.remove(SecretKeys.servicePassword(id))
        secretStore.remove(SecretKeys.serviceApiKey(id))
    }

    suspend fun setBudgetEnabled(id: String, enabled: Boolean) {
        val config = apiBudgetDao.get(id) ?: return
        apiBudgetDao.upsert(config.copy(enabled = enabled))
    }

    suspend fun setServiceEnabled(id: String, enabled: Boolean) {
        val connection = serviceDao.get(id) ?: return
        serviceDao.upsert(connection.copy(enabled = enabled))
    }

    /** Fetches the provider payload for [config] and persists the snapshot (or the error). */
    suspend fun refreshBudget(config: ApiBudgetConfig): Result<BudgetSnapshot> {
        val key = secretStore.get(SecretKeys.apiBudget(config.id))
        if (key.isNullOrBlank()) {
            val err = PanelFetchException(PanelErrorReason.MISSING_CONFIG)
            recordBudgetError(config, err)
            return Result.failure(err)
        }
        return try {
            val snapshot = fetchBudgetSnapshot(config, key)
            apiBudgetDao.upsert(config.copy(
                snapshotJson = SnapshotCodec.encodeBudget(snapshot),
                lastUpdatedAt = snapshot.fetchedAt,
                lastError = null,
            ))
            Result.success(snapshot)
        } catch (e: PanelFetchException) {
            recordBudgetError(config, e)
            Result.failure(e)
        } catch (e: Exception) {
            val err = PanelFetchException(PanelErrorReason.UNKNOWN, e.message, e)
            recordBudgetError(config, err)
            Result.failure(err)
        }
    }

    /** Fetch without persisting — used by the edit screen's "Test connection". */
    suspend fun testBudget(form: ApiBudgetForm): BudgetSnapshot {
        val key = form.apiKey.ifBlank { form.id?.let { secretStore.get(SecretKeys.apiBudget(it)) } }
        if (key.isNullOrBlank()) throw PanelFetchException(PanelErrorReason.MISSING_CONFIG)
        val probe = ApiBudgetConfig(
            id = form.id ?: "probe",
            name = form.name,
            serviceType = form.serviceType,
            baseUrl = form.baseUrl?.trim()?.ifBlank { null },
            userId = form.userId?.trim()?.ifBlank { null },
            currencyCode = form.currencyCode,
            monthlyBudget = form.monthlyBudget,
            dailyBudget = form.dailyBudget,
            alertThreshold = form.alertThreshold,
            enabled = form.enabled,
            createdAt = time.now(),
        )
        return fetchBudgetSnapshot(probe, key)
    }

    private suspend fun fetchBudgetSnapshot(config: ApiBudgetConfig, key: String): BudgetSnapshot {
        val now = time.now()
        return when (config.serviceType) {
            ApiServiceType.DEEPSEEK -> deepSeek.fetch(key, now)
            ApiServiceType.NEWAPI -> {
                val base = config.baseUrl ?: throw PanelFetchException(PanelErrorReason.MISSING_CONFIG)
                newApi.fetch(key, base, config.userId, now)
            }
            ApiServiceType.VAPI -> {
                val base = config.baseUrl ?: NewApiClient.VAPI_DEFAULT_BASE_URL
                newApi.fetch(key, base, config.userId, now).copy(serviceType = "VAPI")
            }
            ApiServiceType.PACKY -> packy.fetch(key, config.baseUrl, now)
            ApiServiceType.ZAI -> zai.fetch(key, now).snapshot
        }
    }

    /** Fetches the service connection payload and persists it (or the error). */
    suspend fun refreshService(connection: ServiceConnection): Result<ServiceSnapshot> {
        return try {
            val snapshot = fetchServiceSnapshot(connection)
            serviceDao.upsert(connection.copy(
                snapshotJson = when (snapshot) {
                    is ClashSnapshot -> SnapshotCodec.encodeClash(snapshot)
                    is EmbySnapshot -> SnapshotCodec.encodeEmby(snapshot)
                },
                lastRefreshAt = when (snapshot) {
                    is ClashSnapshot -> snapshot.fetchedAt
                    is EmbySnapshot -> snapshot.fetchedAt
                },
                lastError = null,
            ))
            Result.success(snapshot)
        } catch (e: PanelFetchException) {
            recordServiceError(connection, e)
            Result.failure(e)
        } catch (e: Exception) {
            val err = PanelFetchException(PanelErrorReason.UNKNOWN, e.message, e)
            recordServiceError(connection, err)
            Result.failure(err)
        }
    }

    /** Fetch without persisting — used for the manual cooldown-aware Ping test and edit "Test". */
    suspend fun testService(form: ServiceConnectionForm): ServiceSnapshot {
        val id = form.id ?: "probe"
        val password = when {
            !form.password.isNullOrBlank() -> form.password.trim()
            form.id != null -> servicePassword(form.id)
            else -> null
        }
        val apiKey = when {
            !form.apiKey.isNullOrBlank() -> form.apiKey.trim()
            form.id != null -> serviceApiKey(form.id)
            else -> null
        }
        val probe = ServiceConnection(
            id = id,
            name = form.name,
            type = form.type,
            url = form.url.trim(),
            username = form.username?.trim()?.ifBlank { null },
            createdAt = time.now(),
        )
        return fetchServiceSnapshot(probe, password, apiKey)
    }

    private suspend fun fetchServiceSnapshot(
        connection: ServiceConnection,
        password: String? = null,
        apiKey: String? = null,
    ): ServiceSnapshot {
        val now = time.now()
        return when (connection.type) {
            ServiceType.CLASH -> clash.fetch(connection.url, now)
            ServiceType.EMBY -> emby.fetch(
                connection.url,
                connection.username,
                password ?: servicePassword(connection.id),
                apiKey ?: serviceApiKey(connection.id),
                now,
            )
        }
    }

    private suspend fun recordBudgetError(config: ApiBudgetConfig, e: PanelFetchException) {
        apiBudgetDao.upsert(config.copy(lastError = e.reason.name, lastUpdatedAt = time.now()))
    }

    private suspend fun recordServiceError(connection: ServiceConnection, e: PanelFetchException) {
        serviceDao.upsert(connection.copy(lastError = e.reason.name, lastRefreshAt = time.now()))
    }

    companion object {
        const val MIN_REFRESH_INTERVAL_MINUTES = 15
        const val DEFAULT_REFRESH_INTERVAL_MINUTES = 15
    }
}
