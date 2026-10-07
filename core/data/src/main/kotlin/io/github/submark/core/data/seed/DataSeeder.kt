package io.github.submark.core.data.seed

import io.github.submark.core.data.currency.CurrencyCatalog
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.database.dao.CategoryDao
import io.github.submark.core.database.dao.CurrencyDao
import io.github.submark.core.database.dao.PaymentMethodDao
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inserts preset rows that are missing. Idempotent and safe on every app start:
 * existing rows (including user edits such as hidden or recolored presets) are never overwritten.
 */
@Singleton
class DataSeeder @Inject constructor(
    private val categoryDao: CategoryDao,
    private val paymentMethodDao: PaymentMethodDao,
    private val currencyDao: CurrencyDao,
    private val settings: SettingsRepository,
) {
    suspend fun seed() {
        categoryDao.insertIgnoreAll(SystemCategories.defaults)
        paymentMethodDao.insertIgnoreAll(SystemPaymentMethods.defaults)
        seedCurrencies()
    }

    private suspend fun seedCurrencies() {
        val firstRun = currencyDao.count() == 0
        val defaultCode = settings.settings.first().money.defaultCurrencyCode
        val enabledOnFirstRun = (CurrencyCatalog.initiallyEnabled + defaultCode).toSet()
        val catalogue = CurrencyCatalog.entries.toMutableList()
        if (catalogue.none { it.code == defaultCode }) CurrencyCatalog.describe(defaultCode)?.let { catalogue += it }
        val rows = catalogue.mapIndexed { index, entry ->
            CurrencyCatalog.toCurrency(entry, enabled = firstRun && entry.code in enabledOnFirstRun, sortOrder = index)
        }
        currencyDao.insertIgnoreAll(rows)
        // The default currency must always exist and be enabled, e.g. after an import that removed it.
        currencyDao.get(defaultCode)?.takeIf { !it.isEnabled }?.let { currencyDao.upsert(it.copy(isEnabled = true)) }
    }
}
