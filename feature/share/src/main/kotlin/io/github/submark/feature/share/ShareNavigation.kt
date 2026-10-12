package io.github.submark.feature.share

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import io.github.submark.core.ui.navigation.FinancialReportPosterRoute as FinancialReportPosterDest
import io.github.submark.core.ui.navigation.QrImportRoute as QrImportDest
import io.github.submark.core.ui.navigation.ShareSubscriptionRoute as ShareSubscriptionDest
import io.github.submark.core.ui.navigation.SubscriptionEditRoute
import io.github.submark.core.ui.navigation.screen
import io.github.submark.feature.share.ui.qrimport.QrImportRoute
import io.github.submark.feature.share.ui.report.FinancialReportPosterRoute
import io.github.submark.feature.share.ui.share.ShareSubscriptionRoute

/** Registers every screen owned by feature:share. */
fun NavGraphBuilder.shareGraph(navController: NavController) {
    val back: () -> Unit = { navController.popBackStack() }

    screen<ShareSubscriptionDest> {
        ShareSubscriptionRoute(onBack = back)
    }

    screen<FinancialReportPosterDest> {
        FinancialReportPosterRoute(onBack = back)
    }

    screen<QrImportDest> {
        QrImportRoute(
            onBack = back,
            onImport = { prefillJson ->
                navController.navigate(SubscriptionEditRoute(prefillJson = prefillJson)) {
                    popUpTo<QrImportDest> { inclusive = true }
                }
            },
        )
    }
}
