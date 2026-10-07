package io.github.submark.core.data.currency

import io.github.submark.core.data.result.DataError
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.data.result.InvalidReason
import io.github.submark.core.data.result.TransactionRunner
import io.github.submark.core.data.result.abort
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.database.dao.CurrencyDao
import io.github.submark.core.database.dao.PaymentDao
import io.github.submark.core.database.dao.SubscriptionDao
import io.github.submark.core.database.dao.WalletDao
import io.github.submark.core.domain.CurrencyConverter
import io.github.submark.core.model.Currency
import io.github.submark.core.model.HistoricalRate
import io.github.submark.core.model.RateMode
import io.github.submark.core.model.RateProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class SuggestedRate(val usdRate: BigDecimal, val provider: RateProvider, val date: LocalDate)

data class HistoricalRateStats(val count: Int, val oldest: LocalDate?, val newest: LocalDate?)

/**
 * Currencies, current rates (units per 1 USD) and the historical-rate cache.
 * Rate sources are tried in order: Frankfurter, fawazahmed0 (jsDelivr), fawazahmed0 (pages.dev).
 */
@Singleton
class CurrencyRepository @Inject internal constructor(
    private val dao: CurrencyDao,
    private val subscriptionDao: SubscriptionDao,
    private val paymentDao: PaymentDao,
    private val walletDao: WalletDao,
    private val settings: SettingsRepository,
    private val sources: List<@JvmSuppressWildcards ExchangeRateSource>,
    private val time: TimeProvider,
    private val tx: TransactionRunner,
) {
    private val refreshMutex = Mutex()

    fun observeCurrencies(): Flow<List<Currency>> = dao.observeAll()

    fun observeEnabled(): Flow<List<Currency>> = dao.observeAll().map { list -> list.filter { it.isEnabled } }

    /** Enabled currencies with the default currency first. */
    fun observeEnabledSorted(): Flow<List<Currency>> =
        combine(observeEnabled(), settings.settings.map { it.money.defaultCurrencyCode }.distinctUntilChanged()) { list, def ->
            list.sortedWith(compareBy<Currency> { it.code != def }.thenBy { it.sortOrder }.thenBy { it.code })
        }

    suspend fun get(code: String): Currency? = dao.get(code)

    /** Snapshot converter over the current stored rates. */
    suspend fun converter(): CurrencyConverter = toConverter(dao.getAll())

    fun observeConverter(): Flow<CurrencyConverter> = dao.observeAll().map(::toConverter)

    /** Fetches latest rates for every AUTO-mode currency. Returns the number of currencies updated. */
    suspend fun refreshRates(): DataResult<Int> = refreshMutex.withLock {
        val targets = dao.getAll().filter { it.rateMode == RateMode.AUTO && it.code != CurrencyConverter.USD }
        if (targets.isEmpty()) return DataResult.Success(0)
        val found = mutableMapOf<String, Pair<BigDecimal, RateProvider>>()
        var lastError: IOException? = null
        for (source in sources) {
            val missing = targets.filter { it.code !in found }
            if (missing.isEmpty()) break
            try {
                val snapshot = source.latest()
                missing.forEach { c -> snapshot.usdRates[c.code]?.let { found[c.code] = it to snapshot.provider } }
            } catch (e: IOException) {
                lastError = e
            }
        }
        if (found.isEmpty() && lastError != null) return DataResult.Failure(DataError.Network(lastError.message))
        val now = time.now()
        val updated = targets.mapNotNull { c ->
            found[c.code]?.let { (rate, provider) -> c.copy(usdRate = rate, rateProvider = provider, rateUpdatedAt = now) }
        }
        dao.upsertAll(updated)
        DataResult.Success(updated.size)
    }

    /** Current rate for [code] from the first provider that knows it, for the custom-currency form. */
    suspend fun fetchSuggestedRate(code: String): DataResult<SuggestedRate> {
        val normalized = code.trim().uppercase()
        if (normalized == CurrencyConverter.USD) return DataResult.Success(SuggestedRate(BigDecimal.ONE, RateProvider.FRANKFURTER, time.today()))
        var anySucceeded = false
        var lastError: IOException? = null
        for (source in sources) {
            try {
                val snapshot = source.latest()
                anySucceeded = true
                snapshot.usdRates[normalized]?.let { return DataResult.Success(SuggestedRate(it, snapshot.provider, snapshot.date)) }
            } catch (e: IOException) {
                lastError = e
            }
        }
        return if (anySucceeded) {
            DataResult.Failure(DataError.UnsupportedCurrency(normalized))
        } else {
            DataResult.Failure(DataError.Network(lastError?.message))
        }
    }

    suspend fun enable(code: String): DataResult<Unit> = setEnabled(code, true)

    suspend fun disable(code: String): DataResult<Unit> {
        if (code == defaultCode()) return DataResult.Failure(DataError.Invalid(InvalidReason.DEFAULT_CURRENCY))
        return setEnabled(code, false)
    }

    /** Makes [code] the reporting currency. Rates are USD-based, so nothing is rebased. */
    suspend fun setDefault(code: String): DataResult<Unit> {
        val result = setEnabled(code, true)
        if (result.isSuccess) settings.update { it.copy(money = it.money.copy(defaultCurrencyCode = code)) }
        return result
    }

    /** [usdRate] = units of the new currency per 1 USD (see [CurrencyConverter.usdRateFromDefault]). */
    suspend fun addCustom(
        code: String,
        name: String,
        symbol: String,
        usdRate: BigDecimal,
        rateMode: RateMode = RateMode.MANUAL,
    ): DataResult<Currency> = tx.run {
        val normalized = code.trim().uppercase()
        if (!CODE_PATTERN.matches(normalized)) abort(InvalidReason.CURRENCY_CODE_INVALID)
        if (name.isBlank() || symbol.isBlank()) abort(InvalidReason.BLANK_NAME)
        validateRate(usdRate)
        if (dao.get(normalized) != null) abort(InvalidReason.CURRENCY_EXISTS)
        val maxOrder = dao.getAll().maxOfOrNull { it.sortOrder } ?: 0
        Currency(
            code = normalized, name = name.trim(), symbol = symbol.trim(), isCustom = true, isEnabled = true,
            usdRate = usdRate, rateMode = rateMode, rateUpdatedAt = time.now(), sortOrder = maxOrder + 1,
        ).also { dao.upsert(it) }
    }

    /** Edits a currency. Name and symbol are only changed for custom currencies. */
    suspend fun update(
        code: String,
        name: String? = null,
        symbol: String? = null,
        usdRate: BigDecimal? = null,
        rateMode: RateMode? = null,
    ): DataResult<Currency> = tx.run {
        val current = dao.get(code) ?: abort(DataError.NotFound)
        if ((name != null || symbol != null) && !current.isCustom) abort(InvalidReason.NOT_CUSTOM)
        if (name != null && name.isBlank() || symbol != null && symbol.isBlank()) abort(InvalidReason.BLANK_NAME)
        usdRate?.let { validateRate(it) }
        current.copy(
            name = name?.trim() ?: current.name,
            symbol = symbol?.trim() ?: current.symbol,
            usdRate = usdRate ?: current.usdRate,
            rateMode = rateMode ?: current.rateMode,
            rateUpdatedAt = if (usdRate != null) time.now() else current.rateUpdatedAt,
            rateProvider = if (usdRate != null) null else current.rateProvider,
        ).also { dao.upsert(it) }
    }

    /** Deletes a custom currency; blocked while subscriptions, payments or wallets use it. */
    suspend fun delete(code: String): DataResult<Unit> {
        if (code == defaultCode()) return DataResult.Failure(DataError.Invalid(InvalidReason.DEFAULT_CURRENCY))
        return tx.run {
            val current = dao.get(code) ?: abort(DataError.NotFound)
            if (!current.isCustom) abort(InvalidReason.NOT_CUSTOM)
            val uses = subscriptionDao.countWithCurrency(code) + paymentDao.countWithCurrency(code) + walletDao.countWithCurrency(code)
            if (uses > 0) abort(DataError.InUse(uses))
            dao.deleteByCode(code)
        }
    }

    // ---- historical rates ----

    fun observeHistoricalStats(): Flow<HistoricalRateStats> =
        dao.observeHistoricalCount().map { HistoricalRateStats(it, dao.oldestHistoricalDate(), dao.newestHistoricalDate()) }

    /**
     * Units of [code] per USD on [date]: cache, then providers, then the current rate as a fallback.
     * MANUAL-mode currencies always use their current rate.
     */
    suspend fun historicalRate(code: String, date: LocalDate): BigDecimal? {
        if (code == CurrencyConverter.USD) return BigDecimal.ONE
        val currency = dao.get(code)
        if (currency?.rateMode == RateMode.MANUAL) return currency.usdRate
        dao.getHistorical(date, code)?.let { return it.usdRate }
        if (date < time.today()) fetchAndCache(date, setOf(code))
        return dao.getHistorical(date, code)?.usdRate ?: currency?.usdRate
    }

    /** Converter with rates as of [date] for every known currency (missing ones fall back to current). */
    suspend fun historicalConverter(date: LocalDate): CurrencyConverter {
        val currencies = dao.getAll()
        if (date >= time.today()) return toConverter(currencies)
        val autoCodes = currencies.filter { it.rateMode == RateMode.AUTO && it.code != CurrencyConverter.USD }.map { it.code }.toSet()
        var cached = dao.getHistoricalRange(date, date).associate { it.code to it.usdRate }
        val missingEnabled = currencies.filter { it.isEnabled && it.code in autoCodes && it.code !in cached }.map { it.code }.toSet()
        if (missingEnabled.isNotEmpty()) {
            fetchAndCache(date, missingEnabled)
            cached = dao.getHistoricalRange(date, date).associate { it.code to it.usdRate }
        }
        val rates = currencies.mapNotNull { c ->
            val rate = if (c.code in autoCodes) cached[c.code] ?: c.usdRate else c.usdRate
            rate?.let { c.code to it }
        }.toMap()
        return CurrencyConverter(rates)
    }

    /**
     * Warms the cache for the last [days] days. Uses one range request when the first source supports it,
     * otherwise one request per day. [onProgress] receives (done, total). Returns rows stored.
     */
    suspend fun preloadRecent(days: Int = PRELOAD_DAYS, onProgress: (Int, Int) -> Unit = { _, _ -> }): DataResult<Int> {
        val today = time.today()
        val from = today.minusDays(days.toLong())
        val to = today.minusDays(1)
        val knownCodes = dao.getAll().filter { it.rateMode == RateMode.AUTO }.map { it.code }.toSet()
        val ranged = sources.firstNotNullOfOrNull { source ->
            try {
                source.range(from, to).takeIf { it.isNotEmpty() }
            } catch (e: IOException) {
                null
            }
        }
        if (ranged != null) {
            val rows = fillDays(ranged, from, to).flatMap { (day, snapshot) -> snapshot.toRows(day, knownCodes) }
            dao.upsertHistorical(rows)
            onProgress(days, days)
            return DataResult.Success(rows.size)
        }
        var stored = 0
        var failures = 0
        val dates = generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.toList()
        dates.forEachIndexed { index, day ->
            val count = fetchAndCache(day, knownCodes)
            if (count == 0) failures++
            stored += count
            onProgress(index + 1, dates.size)
        }
        return if (failures == dates.size && dates.isNotEmpty()) {
            DataResult.Failure(DataError.Network(null))
        } else {
            DataResult.Success(stored)
        }
    }

    /** Drops cache rows fetched more than six months ago. */
    suspend fun cleanupHistorical() {
        dao.deleteHistoricalFetchedBefore(time.today().minusMonths(CACHE_MONTHS).atStartOfDay(time.zone()).toInstant())
    }

    suspend fun clearHistorical() = dao.clearHistorical()

    // ---- internals ----

    private suspend fun defaultCode() = settings.settings.first().money.defaultCurrencyCode

    private suspend fun setEnabled(code: String, enabled: Boolean): DataResult<Unit> = tx.run {
        val current = dao.get(code) ?: CurrencyCatalog.describe(code)?.let { CurrencyCatalog.toCurrency(it, enabled, 0) }
            ?: abort(DataError.NotFound)
        dao.upsert(current.copy(isEnabled = enabled))
    }

    private fun validateRate(rate: BigDecimal) {
        if (rate.signum() <= 0 || rate >= MAX_RATE) abort(InvalidReason.RATE_OUT_OF_RANGE)
    }

    /** Fetches [date] from the sources in order until [wanted] is covered; caches all known codes. */
    private suspend fun fetchAndCache(date: LocalDate, wanted: Set<String>): Int {
        val knownCodes = dao.getAll().map { it.code }.toSet()
        val missing = wanted.toMutableSet()
        var stored = 0
        for (source in sources) {
            if (missing.isEmpty()) break
            val snapshot = try {
                source.historical(date)
            } catch (e: IOException) {
                continue
            }
            val rows = snapshot.toRows(date, knownCodes)
            dao.upsertHistorical(rows)
            stored += rows.size
            missing.removeAll(snapshot.usdRates.keys)
        }
        return stored
    }

    private fun RateSnapshot.toRows(day: LocalDate, codes: Set<String>): List<HistoricalRate> {
        val now = time.now()
        return usdRates.filterKeys { it in codes && it != CurrencyConverter.USD }
            .map { (code, rate) -> HistoricalRate(day, code, rate, provider, now) }
    }

    /** Carries the last business-day snapshot forward over weekends and holidays. */
    private fun fillDays(snapshots: List<RateSnapshot>, from: LocalDate, to: LocalDate): List<Pair<LocalDate, RateSnapshot>> {
        val byDate = snapshots.associateBy { it.date }
        var current: RateSnapshot? = snapshots.firstOrNull()
        return generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.mapNotNull { day ->
            byDate[day]?.let { current = it }
            current?.let { day to it }
        }.toList()
    }

    private fun toConverter(currencies: List<Currency>) =
        CurrencyConverter(currencies.mapNotNull { c -> c.usdRate?.let { c.code to it } }.toMap())

    companion object {
        const val PRELOAD_DAYS = 30
        const val CACHE_MONTHS = 6L
        private val MAX_RATE = BigDecimal(1_000_000)
        private val CODE_PATTERN = Regex("^[A-Z0-9]{2,10}$")
    }
}
