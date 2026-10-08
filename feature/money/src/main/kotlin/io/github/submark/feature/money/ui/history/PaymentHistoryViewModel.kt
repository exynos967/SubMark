package io.github.submark.feature.money.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.submark.core.data.repository.SubscriptionRepository
import io.github.submark.core.data.service.PaymentService
import io.github.submark.core.model.PaymentRecord
import io.github.submark.core.model.PaymentStatus
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.ui.format.DateLabels
import io.github.submark.core.ui.navigation.PaymentHistoryRoute
import io.github.submark.feature.money.data.MoneyEnv
import io.github.submark.feature.money.data.MoneyEnvSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.math.BigDecimal
import java.time.format.FormatStyle
import javax.inject.Inject

data class PaymentHistoryUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val subscriptionName: String = "",
    val canAdd: Boolean = false,
    val query: String = "",
    val records: List<PaymentRecord> = emptyList(),
    val totalRecordCount: Int = 0,
    val successCount: Int = 0,
    val totalPaid: BigDecimal = BigDecimal.ZERO,
    /** SUCCESS records that could not be converted (no rate) and are missing from [totalPaid]. */
    val unconvertedCount: Int = 0,
    val env: MoneyEnv = MoneyEnv.EMPTY,
)

@HiltViewModel
class PaymentHistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    subscriptions: SubscriptionRepository,
    payments: PaymentService,
    envSource: MoneyEnvSource,
) : ViewModel() {
    val subscriptionId: String = savedStateHandle.toRoute<PaymentHistoryRoute>().subscriptionId
    private val query = MutableStateFlow("")

    val uiState: StateFlow<PaymentHistoryUiState> = combine(
        subscriptions.observe(subscriptionId),
        payments.observeForSubscription(subscriptionId),
        envSource.observe(),
        query,
    ) { sub, records, env, q ->
        if (sub == null) return@combine PaymentHistoryUiState(loading = false, missing = true)
        val sorted = records.sortedWith(compareByDescending<PaymentRecord> { it.paymentDate }.thenByDescending { it.createdAt })
        val success = sorted.filter { it.status == PaymentStatus.SUCCESS }
        val converted = success.map { env.toDefault(it.amount, it.currencyCode) }
        PaymentHistoryUiState(
            loading = false,
            subscriptionName = sub.name,
            canAdd = sub.kind != SubscriptionKind.WISHLIST,
            query = q,
            records = sorted.filter { PaymentSearch.matches(it, q) },
            totalRecordCount = sorted.size,
            successCount = success.size,
            totalPaid = converted.filterNotNull().fold(BigDecimal.ZERO, BigDecimal::add),
            unconvertedCount = converted.count { it == null },
            env = env,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaymentHistoryUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }
}

/** Search over date (ISO or localized), amount and note. */
object PaymentSearch {
    fun matches(record: PaymentRecord, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        val date = record.paymentDate
        return date.toString().contains(q) ||
            DateLabels.formatDate(date, FormatStyle.MEDIUM).contains(q, ignoreCase = true) ||
            record.amount.toPlainString().contains(q.replace(',', '.')) ||
            record.note?.takeUnless { it.startsWith("@") }?.contains(q, ignoreCase = true) == true ||
            record.iapItemName?.contains(q, ignoreCase = true) == true
    }
}
