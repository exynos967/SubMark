package io.github.submark.feature.money

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.submark.core.ui.navigation.AddPaymentRoute as AddPaymentDest
import io.github.submark.core.ui.navigation.PaymentHistoryRoute as PaymentHistoryDest
import io.github.submark.core.ui.navigation.PaymentRecordRoute as PaymentRecordDest
import io.github.submark.core.ui.navigation.StoredValueRecordsRoute as StoredValueRecordsDest
import io.github.submark.core.ui.navigation.WalletActivityRoute as WalletActivityDest
import io.github.submark.feature.money.ui.addpayment.AddPaymentRoute
import io.github.submark.feature.money.ui.history.PaymentHistoryRoute
import io.github.submark.feature.money.ui.record.PaymentRecordRoute

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
            onOpenStoredValue = { navController.navigate(StoredValueRecordsDest(it)) },
            onOpenWalletActivity = { navController.navigate(WalletActivityDest(it)) },
        )
    }
    composable<AddPaymentDest> { AddPaymentRoute(onBack = back) }
}
