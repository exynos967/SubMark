package io.github.submark.feature.analytics.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.repository.SubscriptionRepository
import io.github.submark.core.data.service.PaymentService
import io.github.submark.core.data.service.SharedService
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.domain.BillingCalculator
import io.github.submark.core.domain.CostCalculator
import io.github.submark.core.model.PaymentStatus
import io.github.submark.core.model.RenewalType
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionStatus
import io.github.submark.core.ui.navigation.SubscriptionAnalyticsRoute
import io.github.submark.feature.analytics.data.LifecycleNumbers
import io.github.submark.feature.analytics.data.PaymentPoint
import io.github.submark.feature.analytics.data.lifecycleNumbers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

data class SubscriptionAnalyticsUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val subscription: Subscription? = null,
    val isLifetime: Boolean = false,
    val lifecycle: LifecycleNumbers? = null,
    // Trial / paused
    val trialActive: Boolean = false,
    val trialEndDate: LocalDate? = null,
    val paused: Boolean = false,
    // Projected spending (subscription currency)
    val projectedCurrentCycle: BigDecimal? = null,
    val projectedWeekly: BigDecimal? = null,
    val projectedMonthly: BigDecimal? = null,
    val projectedQuarterly: BigDecimal? = null,
    val projectedSemiAnnual: BigDecimal? = null,
    val projectedAnnual: BigDecimal? = null,
    // Shared
    val isShared: Boolean = false,
    val memberCount: Int = 0,
    val userShare: BigDecimal? = null,
    // Lifetime
    val daysOwned: Long = 0,
    val dailyHoldingCost: BigDecimal? = null,
)

@HiltViewModel
class SubscriptionAnalyticsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    subscriptions: SubscriptionRepository,
    payments: PaymentService,
    shared: SharedService,
    time: TimeProvider,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<SubscriptionAnalyticsRoute>()

    val uiState: StateFlow<SubscriptionAnalyticsUiState> = combine(
        subscriptions.observeItem(route.subscriptionId),
        payments.observeForSubscription(route.subscriptionId),
        shared.observeMembers(route.subscriptionId),
    ) { item, records, members ->
        val sub = item?.subscription ?: return@combine SubscriptionAnalyticsUiState(loading = false, notFound = true)
        val today = time.today()
        val success = records.filter { it.status == PaymentStatus.SUCCESS }
        val points = success.map { PaymentPoint(it.paymentDate, it.amount) }

        if (sub.kind == SubscriptionKind.LIFETIME) {
            val daysOwned = (today.toEpochDay() - sub.startDate.toEpochDay()).coerceAtLeast(1)
            SubscriptionAnalyticsUiState(
                loading = false,
                subscription = sub,
                isLifetime = true,
                daysOwned = daysOwned,
                dailyHoldingCost = CostCalculator.lifetimeDailyCost(sub.price, daysOwned),
            )
        } else {
            val cycle = BillingCalculator.cycleLength(sub)
            val lifecycle = lifecycleNumbers(
                payments = points,
                startDate = sub.startDate,
                today = today,
                price = sub.price,
                nextPaymentDate = sub.nextPaymentDate,
            )
            val activeMembers = members.count { it.status == io.github.submark.core.model.MemberStatus.ACTIVE }.coerceAtLeast(1)
            SubscriptionAnalyticsUiState(
                loading = false,
                subscription = sub,
                isLifetime = false,
                lifecycle = lifecycle,
                trialActive = sub.renewalType == RenewalType.TRIAL &&
                    BillingCalculator.trialEndDate(sub)?.let { it >= today } == true,
                trialEndDate = BillingCalculator.trialEndDate(sub),
                paused = sub.status == SubscriptionStatus.PAUSED,
                projectedCurrentCycle = sub.price,
                projectedWeekly = cycle?.let { CostCalculator.weekly(sub.price, it) },
                projectedMonthly = cycle?.let { CostCalculator.monthly(sub.price, it) },
                projectedQuarterly = cycle?.let { CostCalculator.monthly(sub.price, it).multiply(BigDecimal(3)) },
                projectedSemiAnnual = cycle?.let { CostCalculator.monthly(sub.price, it).multiply(BigDecimal(6)) },
                projectedAnnual = cycle?.let { CostCalculator.annualize(sub.price, it) },
                isShared = sub.isShared,
                memberCount = activeMembers,
                userShare = null,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionAnalyticsUiState())
}
