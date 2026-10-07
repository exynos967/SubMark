package io.github.submark.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.submark.core.data.backup.ExportService
import io.github.submark.core.data.change.ChangeNotifier
import io.github.submark.core.data.change.SubscriptionChangeListener
import io.github.submark.core.data.currency.CurrencyRepository
import io.github.submark.core.data.network.AppInfo
import io.github.submark.core.data.repository.CustomFieldRepository
import io.github.submark.core.data.repository.TagRepository
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.data.result.TransactionRunner
import io.github.submark.core.data.seed.DataSeeder
import io.github.submark.core.data.seed.SystemCategories
import io.github.submark.core.data.service.PaymentService
import io.github.submark.core.data.service.SharedService
import io.github.submark.core.data.service.StoredValueService
import io.github.submark.core.data.service.SubscriptionDraft
import io.github.submark.core.data.service.SubscriptionService
import io.github.submark.core.data.service.WalletLedger
import io.github.submark.core.data.service.WalletService
import io.github.submark.core.data.settings.AppSettings
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.database.SubMarkDatabase
import io.github.submark.core.model.BillingCycle
import io.github.submark.core.model.RenewalType
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class FakeTime(var today: LocalDate) : TimeProvider {
    private var tick = 0L
    override fun today(): LocalDate = today
    /** Strictly increasing within a day so creation order is observable. */
    override fun now(): Instant = today.atTime(12, 0).toInstant(ZoneOffset.UTC).plusMillis(tick++)
    override fun zone(): ZoneId = ZoneOffset.UTC
}

class FakeSettings(initial: AppSettings = AppSettings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state
    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}

class RecordingListener : SubscriptionChangeListener {
    val calls = mutableListOf<Set<String>?>()
    override suspend fun onSubscriptionsChanged(ids: Set<String>?) {
        calls += ids
    }
}

/** Wires the data layer by hand over an in-memory database. */
class TestHarness(start: LocalDate = LocalDate.of(2026, 3, 15)) {
    private val context: Context = ApplicationProvider.getApplicationContext()
    val db: SubMarkDatabase = Room.inMemoryDatabaseBuilder(context, SubMarkDatabase::class.java).allowMainThreadQueries().build()
    val time = FakeTime(start)
    val settings = FakeSettings()
    val listener = RecordingListener()
    val notifier = ChangeNotifier(setOf(listener))
    private val tx = TransactionRunner(db)

    val currencies = CurrencyRepository(
        db.currencyDao(), db.subscriptionDao(), db.paymentDao(), db.walletDao(), settings, emptyList(), time, tx,
    )
    private val ledger = WalletLedger(db.walletDao(), currencies, time)
    val tags = TagRepository(db.tagDao(), tx, notifier, time)
    val customFields = CustomFieldRepository(db.customFieldDao(), tx, time)
    val wallets = WalletService(db.walletDao(), db.subscriptionDao(), ledger, currencies, tx, notifier, time)
    val shared = SharedService(db.sharedDao(), db.subscriptionDao(), tx, notifier, time)
    val storedValue = StoredValueService(db.storedValueDao(), db.subscriptionDao(), db.paymentDao(), ledger, currencies, tx, notifier, time)
    val payments = PaymentService(db.paymentDao(), db.subscriptionDao(), db.walletDao(), db.storedValueDao(), storedValue, ledger, tx, notifier, time)
    val subscriptions = SubscriptionService(
        db.subscriptionDao(), db.paymentDao(), db.walletDao(), db.subscriptionExtrasDao(), tags, customFields,
        payments, storedValue, shared, settings, tx, notifier, time,
    )
    val seeder = DataSeeder(db.categoryDao(), db.paymentMethodDao(), db.currencyDao(), settings)
    val export = ExportService(
        db.categoryDao(), db.tagDao(), db.customFieldDao(), db.paymentMethodDao(), db.currencyDao(), db.walletDao(),
        db.subscriptionDao(), db.subscriptionExtrasDao(), db.paymentDao(), db.storedValueDao(), db.sharedDao(),
        db.priceMonitorDao(), db.popularRepositoryDao(), db.iconRepositoryDao(), db.apiBudgetDao(), db.serviceConnectionDao(),
        seeder, AppInfo(context), Json { ignoreUnknownKeys = true; encodeDefaults = true }, tx, notifier, time,
    )

    fun close() = db.close()

    fun subscription(
        name: String = "Netflix",
        price: String = "10",
        start: LocalDate = time.today,
        kind: SubscriptionKind = SubscriptionKind.REGULAR,
        cycle: BillingCycle? = BillingCycle.MONTHLY,
        renewal: RenewalType = RenewalType.MANUAL,
        walletId: String? = null,
    ) = Subscription(
        name = name, kind = kind, price = BigDecimal(price), currencyCode = "USD", billingCycle = cycle,
        renewalType = renewal, startDate = start, categoryId = SystemCategories.OTHER_ID, walletId = walletId,
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    suspend fun create(draft: SubscriptionDraft): String = subscriptions.create(draft).orFail()

    suspend fun get(id: String): Subscription = db.subscriptionDao().get(id)!!
}

fun <T> DataResult<T>.orFail(): T = when (this) {
    is DataResult.Success -> value
    is DataResult.Failure -> throw AssertionError("expected success but got $error")
}
