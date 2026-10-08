package io.github.submark.feature.money

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.submark.core.ui.navigation.AddPaymentRoute as AddPaymentDest
import io.github.submark.core.ui.navigation.BudgetSettingsRoute as BudgetDest
import io.github.submark.core.ui.navigation.CurrencyManagementRoute as CurrencyDest
import io.github.submark.core.ui.navigation.FinancialDetailRoute as FinancialDest
import io.github.submark.core.ui.navigation.HistoricalRatesRoute as HistoricalDest
import io.github.submark.core.ui.navigation.PaymentHistoryRoute as PaymentHistoryDest
import io.github.submark.core.ui.navigation.PaymentMethodManagementRoute as PaymentMethodDest
import io.github.submark.core.ui.navigation.PaymentRecordRoute as PaymentRecordDest
import io.github.submark.core.ui.navigation.SharedSettingsRoute as SharedSettingsDest
import io.github.submark.core.ui.navigation.StoredValueRecordsRoute as StoredValueDest
import io.github.submark.core.ui.navigation.StoredValueStatsRoute as StoredValueStatsDest
import io.github.submark.core.ui.navigation.WalletActivityRoute as WalletActivityDest
import io.github.submark.core.ui.navigation.WalletManagementRoute as WalletManagementDest
import io.github.submark.feature.money.ui.addpayment.AddPaymentRoute
import io.github.submark.feature.money.ui.budget.BudgetSettingsRoute
import io.github.submark.feature.money.ui.currency.CurrencyManagementRoute
import io.github.submark.feature.money.ui.financial.FinancialDetailRoute
import io.github.submark.feature.money.ui.historical.HistoricalRatesRoute
import io.github.submark.feature.money.ui.history.PaymentHistoryRoute
import io.github.submark.feature.money.ui.paymentmethod.PaymentMethodManagementRoute
import io.github.submark.feature.money.ui.record.PaymentRecordRoute
import io.github.submark.feature.money.ui.shared.SharedSettingsRoute
import io.github.submark.feature.money.ui.storedvalue.StoredValueRecordsRoute
import io.github.submark.feature.money.ui.storedvalue.StoredValueStatsRoute
import io.github.submark.feature.money.ui.wallet.WalletActivityRoute
import io.github.submark.feature.money.ui.wallet.WalletManagementRoute

/** Registers every screen owned by feature:money. */
fun NavGraphBuilder.moneyGraph(navController: NavController) {
    val back: () -> Unit = { navController.popBackStack() }

    composable<PaymentHistoryDest> {
        PaymentHistoryRoute(
            onBack = back,
            onOpenRecord = { navController.navigate(PaymentRecordDest(it)) },
            onAddPayment = { navController.navigate(AddPaymentDest(it)) },
        )
    }
    composable<PaymentRecordDest> {
        PaymentRecordRoute(
            onBack = back,
            onEdit = { subscriptionId, paymentId -> navController.navigate(AddPaymentDest(subscriptionId, paymentId)) },
            onOpenStoredValue = { navController.navigate(StoredValueDest(it)) },
            onOpenWalletActivity = { navController.navigate(WalletActivityDest(it)) },
        )
    }
    composable<AddPaymentDest> { AddPaymentRoute(onBack = back) }

    composable<StoredValueDest> { StoredValueRecordsRoute(onBack = back) }
    composable<StoredValueStatsDest> {
        StoredValueStatsRoute(
            onBack = back,
            onOpenRecords = { navController.navigate(StoredValueDest(it)) },
        )
    }

    composable<SharedSettingsDest> { SharedSettingsRoute(onBack = back) }

    composable<CurrencyDest> {
        CurrencyManagementRoute(
            onBack = back,
            onOpenHistoricalRates = { navController.navigate(HistoricalDest) },
        )
    }
    composable<HistoricalDest> { HistoricalRatesRoute(onBack = back) }

    composable<PaymentMethodDest> { PaymentMethodManagementRoute(onBack = back) }

    composable<WalletManagementDest> {
        WalletManagementRoute(
            onBack = back,
            onOpenActivity = { id -> navController.navigate(WalletActivityDest(id)) },
        )
    }
    composable<WalletActivityDest> { WalletActivityRoute(onBack = back) }

    composable<BudgetDest> { BudgetSettingsRoute(onBack = back) }
    composable<FinancialDest> { FinancialDetailRoute(onBack = back) }
}
