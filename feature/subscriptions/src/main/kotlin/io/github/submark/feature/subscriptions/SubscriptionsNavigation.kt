package io.github.submark.feature.subscriptions

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import io.github.submark.core.ui.navigation.ArchiveRoute
import io.github.submark.core.ui.navigation.CategoryManagementRoute
import io.github.submark.core.ui.navigation.CustomFieldManagementRoute
import io.github.submark.core.ui.navigation.SubscriptionDetailRoute
import io.github.submark.core.ui.navigation.SubscriptionEditRoute
import io.github.submark.core.ui.navigation.SubscriptionsRoute
import io.github.submark.core.ui.navigation.TabDestination
import io.github.submark.core.ui.navigation.TagFolderRoute
import io.github.submark.core.ui.navigation.TagManagementRoute
import io.github.submark.core.ui.navigation.screen
import io.github.submark.feature.subscriptions.ui.archive.ArchiveScreenRoute
import io.github.submark.feature.subscriptions.ui.detail.SubscriptionDetailScreenRoute
import io.github.submark.feature.subscriptions.ui.edit.SubscriptionEditScreenRoute
import io.github.submark.feature.subscriptions.ui.folder.TagFolderScreenRoute
import io.github.submark.feature.subscriptions.ui.list.SubscriptionListScreenRoute
import io.github.submark.feature.subscriptions.ui.manage.category.CategoryManagementScreenRoute
import io.github.submark.feature.subscriptions.ui.manage.field.CustomFieldManagementScreenRoute
import io.github.submark.feature.subscriptions.ui.manage.tag.TagManagementScreenRoute

/** Registers every destination owned by the subscriptions feature, including the Subscriptions tab. */
fun NavGraphBuilder.subscriptionsGraph(navController: NavController) {
    val navigate: (Any) -> Unit = { route -> navController.navigate(route) }
    val back: () -> Unit = { navController.popBackStack() }

    screen<SubscriptionsRoute> {
        TabDestination {
            SubscriptionListScreenRoute(onNavigate = navigate)
        }
    }
    screen<SubscriptionDetailRoute> {
        SubscriptionDetailScreenRoute(onBack = back, onNavigate = navigate)
    }
    screen<SubscriptionEditRoute> { entry ->
        SubscriptionEditScreenRoute(backStackEntry = entry, onBack = back, onNavigate = navigate)
    }
    screen<ArchiveRoute> {
        ArchiveScreenRoute(onBack = back, onNavigate = navigate)
    }
    screen<TagFolderRoute> {
        TagFolderScreenRoute(onBack = back, onNavigate = navigate)
    }
    screen<CategoryManagementRoute> { entry ->
        CategoryManagementScreenRoute(backStackEntry = entry, onBack = back, onNavigate = navigate)
    }
    screen<TagManagementRoute> { entry ->
        TagManagementScreenRoute(backStackEntry = entry, onBack = back, onNavigate = navigate)
    }
    screen<CustomFieldManagementRoute> {
        CustomFieldManagementScreenRoute(onBack = back)
    }
}
