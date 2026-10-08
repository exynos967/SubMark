package io.github.submark.feature.money.data

import io.github.submark.core.data.currency.CurrencyRepository
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.domain.CurrencyConverter
import io.github.submark.core.model.Currency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import javax.inject.Inject

/** What most money screens need to display amounts: reporting currency, symbols and current rates. */
data class MoneyEnv(
    val defaultCode: String,
    val currencies: Map<String, Currency>,
    val converter: CurrencyConverter,
) {
    val symbols: Map<String, String> get() = currencies.mapValues { it.value.symbol }

    fun symbol(code: String): String? = currencies[code]?.symbol

    fun toDefault(amount: BigDecimal, code: String): BigDecimal? = converter.convert(amount, code, defaultCode)

    companion object {
        val EMPTY = MoneyEnv("USD", emptyMap(), CurrencyConverter(emptyMap()))
    }
}

class MoneyEnvSource @Inject constructor(
    private val currencies: CurrencyRepository,
    private val settings: SettingsRepository,
) {
    fun observe(): Flow<MoneyEnv> = combine(
        settings.settings.map { it.money.defaultCurrencyCode }.distinctUntilChanged(),
        currencies.observeCurrencies(),
        currencies.observeConverter(),
    ) { default, list, converter -> MoneyEnv(default, list.associateBy { it.code }, converter) }
}
