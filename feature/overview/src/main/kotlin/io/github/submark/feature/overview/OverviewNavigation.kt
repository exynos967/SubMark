package io.github.submark.feature.overview

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.submark.core.ui.navigation.AnalyticsRoute
import io.github.submark.core.ui.navigation.FinancialDetailRoute
import io.github.submark.core.ui.navigation.FinancialReportPosterRoute
import io.github.submark.core.ui.navigation.GlobalSearchRoute as GlobalSearchDest
import io.github.submark.core.ui.navigation.OverviewCustomizationRoute as OverviewCustomizeDest
import io.github.submark.core.ui.navigation.OverviewRoute as OverviewDest
import io.github.submark.core.ui.navigation.PaymentRecordRoute
import io.github.submark.core.ui.navigation.SubscriptionDetailRoute
import io.github.submark.core.ui.navigation.SubscriptionEditRoute
import io.github.submark.core.ui.navigation.SubscriptionsRoute
import io.github.submark.core.ui.navigation.WalletActivityRoute
import io.github.submark.core.ui.navigation.WalletManagementRoute
import io.github.submark.feature.overview.ui.customize.OverviewCustomizationRoute
import io.github.submark.feature.overview.ui.overview.OverviewRoute
import io.github.submark.feature.overview.ui.search.GlobalSearchRoute

/** Registers every screen owned by feature:overview. */
fun NavGraphBuilder.overviewGraph(navController: NavController) {
    val back: () -> Unit = { navController.popBackStack() }

    composable<OverviewDest> {
        OverviewRoute(
            onBack = back,
            onSearch = { navController.navigate(GlobalSearchDest) },
            onCustomize = { navController.navigate(OverviewCustomizeDest) },
            onAddSubscription = { navController.navigate(SubscriptionEditRoute()) },
            onSubscriptions = { navController.navigate(SubscriptionsRoute) },
            onAnalytics = { navController.navigate(AnalyticsRoute) },
            onWalletManagement = { navController.navigate(WalletManagementRoute) },
            onWalletTopUp = { navController.navigate(WalletActivityRoute(it)) },
            onWalletExpense = { navController.navigate(WalletActivityRoute(it)) },
            onFinancialDetail = { navController.navigate(FinancialDetailRoute) },
            onFinancialReport = { navController.navigate(FinancialReportPosterRoute) },
        )
    }
    composable<OverviewCustomizeDest> {
        OverviewCustomizationRoute(onBack = back)
    }
    composable<GlobalSearchDest> {
        GlobalSearchRoute(
            onBack = back,
            onOpenSubscription = { navController.navigate(SubscriptionDetailRoute(it)) },
            onOpenPayment = { navController.navigate(PaymentRecordRoute(it)) },
        )
    }
}
