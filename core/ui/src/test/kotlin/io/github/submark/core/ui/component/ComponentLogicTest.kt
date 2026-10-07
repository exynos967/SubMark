package io.github.submark.core.ui.component

import com.google.common.truth.Truth.assertThat
import io.github.submark.core.model.BundleRole
import io.github.submark.core.model.Currency
import io.github.submark.core.model.RenewalType
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionStatus
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class ComponentLogicTest {
    private val today = LocalDate.of(2026, 3, 10)
    private val sub = Subscription(
        name = "x", price = BigDecimal.TEN, currencyCode = "USD", startDate = today.minusMonths(2),
        categoryId = "c", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH, nextPaymentDate = today.plusDays(3),
    )

    @Test fun `mark paid situation`() {
        assertThat(MarkPaidSituation.of(today, today)).isEqualTo(MarkPaidSituation.OnTime)
        assertThat(MarkPaidSituation.of(today.plusDays(4), today)).isEqualTo(MarkPaidSituation.Early(4))
        assertThat(MarkPaidSituation.of(today.minusDays(2), today)).isEqualTo(MarkPaidSituation.Overdue(2))
    }

    @Test fun `badges derived from subscription`() {
        assertThat(SubscriptionBadge.of(sub, today)).isEmpty()
        assertThat(SubscriptionBadge.of(sub, today, includeAuto = true)).containsExactly(SubscriptionBadge.AUTO)
        val busy = sub.copy(
            nextPaymentDate = today.minusDays(1),
            renewalType = RenewalType.TRIAL,
            isShared = true,
            bundleRole = BundleRole.CHILD,
            kind = SubscriptionKind.STORED_VALUE,
        )
        assertThat(SubscriptionBadge.of(busy, today)).containsExactly(
            SubscriptionBadge.OVERDUE, SubscriptionBadge.TRIAL, SubscriptionBadge.SHARED,
            SubscriptionBadge.STORED_VALUE, SubscriptionBadge.CHILD,
        ).inOrder()
        assertThat(SubscriptionBadge.of(sub.copy(status = SubscriptionStatus.PAUSED, nextPaymentDate = today.minusDays(1)), today))
            .containsExactly(SubscriptionBadge.PAUSED)
        assertThat(SubscriptionBadge.of(sub.copy(kind = SubscriptionKind.LIFETIME, billingCycle = null), today))
            .containsExactly(SubscriptionBadge.LIFETIME)
    }

    @Test fun `currency sections`() {
        val list = listOf(
            Currency("USD", "US Dollar", "$", sortOrder = 1),
            Currency("CNY", "Chinese Yuan", "¥", sortOrder = 0),
            Currency("PTS", "Points", "P", isCustom = true),
        )
        val all = currencySections(list, "", defaultCode = "USD")
        assertThat(all.default?.code).isEqualTo("USD")
        assertThat(all.builtIn.map { it.code }).containsExactly("CNY")
        assertThat(all.custom.map { it.code }).containsExactly("PTS")
        val searched = currencySections(list, "yuan", defaultCode = "USD")
        assertThat(searched.default).isNull()
        assertThat(searched.builtIn.map { it.code }).containsExactly("CNY")
        assertThat(currencySections(list, "¥", null).builtIn.map { it.code }).containsExactly("CNY")
    }
}
