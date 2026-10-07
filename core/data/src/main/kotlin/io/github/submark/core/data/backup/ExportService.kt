package io.github.submark.core.data.backup

import io.github.submark.core.data.change.ChangeNotifier
import io.github.submark.core.data.network.AppInfo
import io.github.submark.core.data.result.DataError
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.data.result.InvalidReason
import io.github.submark.core.data.result.TransactionRunner
import io.github.submark.core.data.seed.DataSeeder
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.database.dao.ApiBudgetDao
import io.github.submark.core.database.dao.CategoryDao
import io.github.submark.core.database.dao.CurrencyDao
import io.github.submark.core.database.dao.CustomFieldDao
import io.github.submark.core.database.dao.IconRepositoryDao
import io.github.submark.core.database.dao.PaymentDao
import io.github.submark.core.database.dao.PaymentMethodDao
import io.github.submark.core.database.dao.PopularRepositoryDao
import io.github.submark.core.database.dao.PriceMonitorDao
import io.github.submark.core.database.dao.ServiceConnectionDao
import io.github.submark.core.database.dao.SharedDao
import io.github.submark.core.database.dao.StoredValueDao
import io.github.submark.core.database.dao.SubscriptionDao
import io.github.submark.core.database.dao.SubscriptionExtrasDao
import io.github.submark.core.database.dao.TagDao
import io.github.submark.core.database.dao.WalletDao
import io.github.submark.core.model.ExportBundle
import io.github.submark.core.model.ImportResult
import io.github.submark.core.model.RestoreMode
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds and restores [ExportBundle]s (shared by manual export and WebDAV backup).
 * Excluded: secrets, settings, historical-rate cache and backup profiles/jobs.
 */
@Singleton
class ExportService @Inject internal constructor(
    private val categoryDao: CategoryDao,
    private val tagDao: TagDao,
    private val customFieldDao: CustomFieldDao,
    private val paymentMethodDao: PaymentMethodDao,
    private val currencyDao: CurrencyDao,
    private val walletDao: WalletDao,
    private val subscriptionDao: SubscriptionDao,
    private val extrasDao: SubscriptionExtrasDao,
    private val paymentDao: PaymentDao,
    private val storedValueDao: StoredValueDao,
    private val sharedDao: SharedDao,
    private val priceMonitorDao: PriceMonitorDao,
    private val popularRepositoryDao: PopularRepositoryDao,
    private val iconRepositoryDao: IconRepositoryDao,
    private val apiBudgetDao: ApiBudgetDao,
    private val serviceConnectionDao: ServiceConnectionDao,
    private val seeder: DataSeeder,
    private val appInfo: AppInfo,
    private val json: Json,
    private val tx: TransactionRunner,
    private val notifier: ChangeNotifier,
    private val time: TimeProvider,
) {
    /** Snapshot of every exported table, read in one transaction for consistency. */
    suspend fun export(): ExportBundle = (tx.run { readBundle() } as DataResult.Success).value

    fun encode(bundle: ExportBundle): String = json.encodeToString(ExportBundle.serializer(), bundle)

    fun decode(text: String): DataResult<ExportBundle> {
        val bundle = try {
            json.decodeFromString(ExportBundle.serializer(), text)
        } catch (e: SerializationException) {
            return DataResult.Failure(DataError.Invalid(InvalidReason.MALFORMED_EXPORT, e.message))
        } catch (e: IllegalArgumentException) {
            return DataResult.Failure(DataError.Invalid(InvalidReason.MALFORMED_EXPORT, e.message))
        }
        if (bundle.exportVersion > ExportBundle.CURRENT_VERSION) {
            return DataResult.Failure(DataError.Invalid(InvalidReason.UNSUPPORTED_EXPORT_VERSION, bundle.exportVersion.toString()))
        }
        return DataResult.Success(bundle)
    }

    /**
     * Restores [bundle] in one transaction. MERGE inserts rows whose ids are missing; REPLACE_MATCHING upserts;
     * EXACT clears every covered table first. Counts are rows written per bundle field.
     */
    suspend fun import(bundle: ExportBundle, mode: RestoreMode): DataResult<ImportResult> {
        if (bundle.exportVersion > ExportBundle.CURRENT_VERSION) {
            return DataResult.Failure(DataError.Invalid(InvalidReason.UNSUPPORTED_EXPORT_VERSION, bundle.exportVersion.toString()))
        }
        val result = tx.run {
            if (mode == RestoreMode.EXACT) clearCoveredTables()
            writeBundle(bundle, mode)
        }
        if (result.isSuccess) {
            seeder.seed()
            notifier.notifyChanged(null)
        }
        return result
    }

    private suspend fun readBundle() = ExportBundle(
        appVersion = appInfo.versionName,
        createdAt = time.now(),
        categories = categoryDao.getAll(),
        tags = tagDao.getAll(),
        tagFolders = tagDao.getFolders(),
        tagFolderTags = tagDao.getFolderTags(),
        customFields = customFieldDao.getDefinitions(),
        customFieldOptions = customFieldDao.getOptions(),
        paymentMethods = paymentMethodDao.getAll(),
        currencies = currencyDao.getAll(),
        wallets = walletDao.getAll(),
        subscriptions = subscriptionDao.getAll(),
        subscriptionTags = tagDao.getSubscriptionTags(),
        customFieldValues = customFieldDao.getAllValues(),
        subscriptionPhotos = extrasDao.getAllPhotos(),
        customReminders = extrasDao.getAllReminders(),
        paymentRecords = paymentDao.getAll(),
        walletTransactions = walletDao.getAllTransactions(),
        storedValueRecords = storedValueDao.getAll(),
        sharedConfigs = sharedDao.getAllConfigs(),
        sharedMembers = sharedDao.getAllMembers(),
        priceMonitors = priceMonitorDao.getAll(),
        priceRecords = priceMonitorDao.getAllRecords(),
        popularRepositories = popularRepositoryDao.getAll(),
        iconRepositories = iconRepositoryDao.getAll(),
        apiBudgetConfigs = apiBudgetDao.getAll(),
        serviceConnections = serviceConnectionDao.getAll(),
    )

    /** Children before parents; subscription deletes cascade to their dependent rows. */
    private suspend fun clearCoveredTables() {
        tagDao.deleteAllFolderTags()
        tagDao.deleteAllSubscriptionTags()
        customFieldDao.deleteAllValues()
        customFieldDao.deleteAllOptions()
        extrasDao.deleteAllPhotos()
        extrasDao.deleteAllReminders()
        paymentDao.deleteAll()
        storedValueDao.deleteAll()
        sharedDao.deleteAllMembers()
        sharedDao.deleteAllConfigs()
        priceMonitorDao.clearRecords()
        priceMonitorDao.deleteAll()
        walletDao.deleteAllTransactions()
        subscriptionDao.deleteAll()
        tagDao.deleteAllFolders()
        tagDao.deleteAll()
        customFieldDao.deleteAll()
        categoryDao.deleteAll()
        paymentMethodDao.deleteAll()
        currencyDao.deleteAll()
        walletDao.deleteAll()
        popularRepositoryDao.deleteAll()
        iconRepositoryDao.deleteAll()
        apiBudgetDao.deleteAll()
        serviceConnectionDao.deleteAll()
    }

    /** Parents before children so foreign keys hold. */
    private suspend fun writeBundle(b: ExportBundle, mode: RestoreMode): ImportResult {
        val counts = linkedMapOf<String, Int>()
        suspend fun <T> put(key: String, items: List<T>, insertIgnore: suspend (List<T>) -> List<Long>, upsert: suspend (List<T>) -> Unit) {
            if (items.isEmpty()) {
                counts[key] = 0
                return
            }
            counts[key] = if (mode == RestoreMode.MERGE) {
                insertIgnore(items).count { it != -1L }
            } else {
                upsert(items)
                items.size
            }
        }
        put("categories", b.categories, categoryDao::insertIgnoreAll, categoryDao::upsertAll)
        put("tags", b.tags, tagDao::insertIgnoreAll, tagDao::upsertAll)
        put("tagFolders", b.tagFolders, tagDao::insertIgnoreFolders, tagDao::upsertFolders)
        put("tagFolderTags", b.tagFolderTags, tagDao::insertIgnoreFolderTags, tagDao::upsertFolderTags)
        put("customFields", b.customFields, customFieldDao::insertIgnoreAll, customFieldDao::upsertAll)
        put("customFieldOptions", b.customFieldOptions, customFieldDao::insertIgnoreOptions, customFieldDao::upsertOptions)
        put("paymentMethods", b.paymentMethods, paymentMethodDao::insertIgnoreAll, paymentMethodDao::upsertAll)
        put("currencies", b.currencies, currencyDao::insertIgnoreAll, currencyDao::upsertAll)
        put("wallets", b.wallets, walletDao::insertIgnoreAll, walletDao::upsertAll)
        put("subscriptions", b.subscriptions, subscriptionDao::insertIgnoreAll, subscriptionDao::upsertAll)
        put("subscriptionTags", b.subscriptionTags, tagDao::insertIgnoreSubscriptionTags, tagDao::upsertSubscriptionTags)
        put("customFieldValues", b.customFieldValues, customFieldDao::insertIgnoreValues, customFieldDao::upsertValues)
        put("subscriptionPhotos", b.subscriptionPhotos, extrasDao::insertIgnorePhotos, extrasDao::upsertPhotos)
        put("customReminders", b.customReminders, extrasDao::insertIgnoreReminders, extrasDao::upsertReminders)
        put("paymentRecords", b.paymentRecords, paymentDao::insertIgnoreAll, paymentDao::upsertAll)
        put("walletTransactions", b.walletTransactions, walletDao::insertIgnoreTransactions, walletDao::upsertTransactions)
        put("storedValueRecords", b.storedValueRecords, storedValueDao::insertIgnoreAll, storedValueDao::upsertAll)
        put("sharedConfigs", b.sharedConfigs, sharedDao::insertIgnoreConfigs, sharedDao::upsertConfigs)
        put("sharedMembers", b.sharedMembers, sharedDao::insertIgnoreMembers, sharedDao::upsertMembers)
        put("priceMonitors", b.priceMonitors, priceMonitorDao::insertIgnoreAll, priceMonitorDao::upsertAll)
        put("priceRecords", b.priceRecords, priceMonitorDao::insertIgnoreRecords, priceMonitorDao::upsertRecords)
        put("popularRepositories", b.popularRepositories, popularRepositoryDao::insertIgnoreAll, popularRepositoryDao::upsertAll)
        put("iconRepositories", b.iconRepositories, iconRepositoryDao::insertIgnoreAll, iconRepositoryDao::upsertAll)
        put("apiBudgetConfigs", b.apiBudgetConfigs, apiBudgetDao::insertIgnoreAll, apiBudgetDao::upsertAll)
        put("serviceConnections", b.serviceConnections, serviceConnectionDao::insertIgnoreAll, serviceConnectionDao::upsertAll)
        return ImportResult(counts)
    }
}
