package io.github.submark.feature.analytics

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.submark.core.ui.navigation.AnalyticsCustomizationRoute as AnalyticsCustomizationDest
import io.github.submark.core.ui.navigation.AnalyticsRoute as AnalyticsDest
import io.github.submark.core.ui.navigation.FinancialDetailRoute
import io.github.submark.core.ui.navigation.StoredValueStatsRoute
import io.github.submark.core.ui.navigation.SubscriptionAnalyticsRoute as SubscriptionAnalyticsDest
import io.github.submark.feature.analytics.ui.AnalyticsCustomizationRoute
import io.github.submark.feature.analytics.ui.AnalyticsRoute
import io.github.submark.feature.analytics.ui.SubscriptionAnalyticsRoute

/** Registers every screen owned by feature:analytics. */
fun NavGraphBuilder.analyticsGraph(navController: NavController) {
    val back: () -> Unit = { navController.popBackStack() }

    composable<AnalyticsDest> {
        AnalyticsRoute(
            onCustomize = { navController.navigate(AnalyticsCustomizationDest) },
            onFinancialDetail = { navController.navigate(FinancialDetailRoute) },
            onStoredValueStats = { navController.navigate(StoredValueStatsRoute) },
        )
    }

    composable<AnalyticsCustomizationDest> {
        AnalyticsCustomizationRoute(onBack = back)
    }

    composable<SubscriptionAnalyticsDest> {
        SubscriptionAnalyticsRoute(onBack = back)
    }
}
