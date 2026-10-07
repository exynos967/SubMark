package io.github.submark.core.data

import com.google.common.truth.Truth.assertThat
import io.github.submark.core.data.result.DataError
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.data.result.InvalidReason
import io.github.submark.core.data.service.ExtendBy
import io.github.submark.core.data.service.SubscriptionDraft
import io.github.submark.core.model.BundlePaymentSyncMode
import io.github.submark.core.model.BundleRole
import io.github.submark.core.model.MarkTiming
import io.github.submark.core.model.PaymentKind
import io.github.submark.core.model.PaymentSource
import io.github.submark.core.model.RenewalType
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class SubscriptionServiceTest {
    private lateinit var h: TestHarness

    @Before fun setUp() = runTest {
        h = TestHarness(LocalDate.of(2026, 3, 15))
        h.seeder.seed()
    }

    @After fun tearDown() = h.close()

    @Test fun pastStartWithoutHistory_nextIsFirstOccurrenceFromToday() = runTest {
        val id = h.create(SubscriptionDraft(h.subscription(start = LocalDate.of(2026, 1, 31))))
        val sub = h.get(id)
        assertThat(sub.cycleAnchorDate).isEqualTo(LocalDate.of(2026, 1, 31))
        assertThat(sub.nextPaymentDate).isEqualTo(LocalDate.of(2026, 3, 31))
        assertThat(h.db.paymentDao().getForSubscription(id)).isEmpty()
    }

    @Test fun historyGeneration_recordsEveryPastOccurrence() = runTest {
        val id = h.create(SubscriptionDraft(h.subscription(start = LocalDate.of(2026, 1, 10)), generateHistory = true))
        val records = h.db.paymentDao().getForSubscription(id)
        assertThat(records.map { it.paymentDate }).containsExactly(
            LocalDate.of(2026, 1, 10), LocalDate.of(2026, 2, 10), LocalDate.of(2026, 3, 10),
        )
        assertThat(records.map { it.source }.toSet()).containsExactly(PaymentSource.HISTORY_GENERATED)
        val sub = h.get(id)
        assertThat(sub.lastPaymentDate).isEqualTo(LocalDate.of(2026, 3, 10))
        assertThat(sub.nextPaymentDate).isEqualTo(LocalDate.of(2026, 4, 10))
    }

    @Test fun markPaidEarly_originalCycleKeepsSchedule() = runTest {
        val id = h.create(SubscriptionDraft(h.subscription(start = LocalDate.of(2026, 3, 20))))
        val outcome = h.subscriptions.markPaid(id, newCycle = false).orFail()
        assertThat(outcome.timing).isEqualTo(MarkTiming.EARLY_ORIGINAL_CYCLE)
        val sub = h.get(id)
        assertThat(sub.cycleAnchorDate).isEqualTo(LocalDate.of(2026, 3, 20))
        assertThat(sub.nextPaymentDate).isEqualTo(LocalDate.of(2026, 4, 20))
        assertThat(sub.lastPaymentDate).isEqualTo(LocalDate.of(2026, 3, 15))
        val record = h.db.paymentDao().get(outcome.paymentRecordId!!)!!
        assertThat(record.paymentDate).isEqualTo(LocalDate.of(2026, 3, 15))
        assertThat(record.originalDueDate).isEqualTo(LocalDate.of(2026, 3, 20))
        assertThat(record.amount).isEqualTo(BigDecimal("10"))
        assertThat(record.prevNextPaymentDate).isEqualTo(LocalDate.of(2026, 3, 20))
    }

    @Test fun markPaidOverdue_newCycleRebasesOnToday() = runTest {
        val id = h.create(SubscriptionDraft(h.subscription(start = LocalDate.of(2026, 3, 1))))
        h.time.today = LocalDate.of(2026, 4, 10)
        val outcome = h.subscriptions.markPaid(id, newCycle = true).orFail()
        assertThat(outcome.timing).isEqualTo(MarkTiming.OVERDUE_NEW_CYCLE)
        val sub = h.get(id)
        assertThat(sub.cycleAnchorDate).isEqualTo(LocalDate.of(2026, 4, 10))
        assertThat(sub.nextPaymentDate).isEqualTo(LocalDate.of(2026, 5, 10))
    }

    @Test fun markPaidOverdue_originalCycleMayStayOverdue() = runTest {
        val id = h.create(SubscriptionDraft(h.subscription(start = LocalDate.of(2026, 3, 1))))
        h.time.today = LocalDate.of(2026, 5, 10)
        // Due 4/1 (first occurrence on/after creation day 3/15); ORIGINAL_CYCLE moves to 5/1, still before today.
        h.subscriptions.markPaid(id, newCycle = false).orFail()
        val sub = h.get(id)
        assertThat(sub.nextPaymentDate).isEqualTo(LocalDate.of(2026, 5, 1))
        assertThat(io.github.submark.core.domain.BillingCalculator.isOverdue(sub, h.time.today)).isTrue()
    }

    @Test fun processDue_backfillsMissedAutoOccurrences() = runTest {
        h.time.today = LocalDate.of(2026, 1, 10)
        val id = h.create(SubscriptionDraft(h.subscription(start = LocalDate.of(2026, 1, 15), renewal = RenewalType.AUTO)))
        h.time.today = LocalDate.of(2026, 3, 15)
        h.listener.calls.clear()

        val summary = h.subscriptions.processDue()

        assertThat(summary.autoMarkedCount).isEqualTo(3)
        val records = h.db.paymentDao().getForSubscription(id).sortedBy { it.paymentDate }
        assertThat(records.map { it.paymentDate to it.source }).containsExactly(
            LocalDate.of(2026, 1, 15) to PaymentSource.SYSTEM_OVERDUE,
            LocalDate.of(2026, 2, 15) to PaymentSource.SYSTEM_OVERDUE,
            LocalDate.of(2026, 3, 15) to PaymentSource.SYSTEM_AUTO,
        ).inOrder()
        assertThat(h.get(id).nextPaymentDate).isEqualTo(LocalDate.of(2026, 4, 15))
        assertThat(h.listener.calls).containsExactly(setOf(id))
        // Running again is a no-op.
        assertThat(h.subscriptions.processDue().autoMarkedCount).isEqualTo(0)
    }

    @Test fun processDue_manualStaysOverdue_trialReported_expiredPaused() = runTest {
        h.time.today = LocalDate.of(2026, 1, 1)
        val manual = h.create(SubscriptionDraft(h.subscription(name = "Manual", start = LocalDate.of(2026, 1, 5))))
        val trial = h.create(
            SubscriptionDraft(
                h.subscription(name = "Trial", start = LocalDate.of(2026, 1, 1), renewal = RenewalType.TRIAL)
                    .copy(trialStartDate = LocalDate.of(2026, 1, 1), trialDays = 14),
            ),
        )
        val expiring = h.create(SubscriptionDraft(h.subscription(name = "Old", start = LocalDate.of(2026, 1, 2)).copy(endDate = LocalDate.of(2026, 2, 1))))
        h.time.today = LocalDate.of(2026, 3, 15)

        val summary = h.subscriptions.processDue()

        assertThat(summary.autoMarkedCount).isEqualTo(0)
        assertThat(h.db.paymentDao().getForSubscription(manual)).isEmpty()
        assertThat(summary.trialEndedIds).containsExactly(trial)
        assertThat(summary.expiredNames).containsExactly("Old")
        assertThat(h.get(expiring).status).isEqualTo(SubscriptionStatus.PAUSED)

        val resolved = h.subscriptions.resolveTrial(trial, RenewalType.MANUAL).orFail()
        assertThat(resolved.nextPaymentDate).isEqualTo(LocalDate.of(2026, 2, 15))
        val record = h.db.paymentDao().getForSubscription(trial).single()
        assertThat(record.source).isEqualTo(PaymentSource.TRIAL_EXPIRED)
        assertThat(record.paymentDate).isEqualTo(LocalDate.of(2026, 1, 15))
        assertThat(h.get(trial).renewalType).isEqualTo(RenewalType.MANUAL)
    }

    @Test fun walletChargeInsufficient_nothingSaved() = runTest {
        val wallet = h.wallets.create("Card", io.github.submark.core.model.WalletKind.BALANCE_TRACKED, "USD", BigDecimal("5")).orFail()
        val id = h.create(SubscriptionDraft(h.subscription(start = LocalDate.of(2026, 3, 15), walletId = wallet.id)))
        val before = h.get(id)

        val result = h.subscriptions.markPaid(id, newCycle = false)

        val error = (result as DataResult.Failure).error
        assertThat(error).isInstanceOf(DataError.InsufficientFunds::class.java)
        assertThat(h.db.paymentDao().getForSubscription(id)).isEmpty()
        assertThat(h.get(id)).isEqualTo(before)
        assertThat(h.wallets.get(wallet.id)!!.balance).isEqualTo(BigDecimal("5"))
        assertThat(h.db.walletDao().countTransactions(wallet.id)).isEqualTo(1) // opening balance only
    }

    @Test fun walletCharge_successDebitsWalletAndLinksRecord() = runTest {
        val wallet = h.wallets.create("Card", io.github.submark.core.model.WalletKind.BALANCE_TRACKED, "USD", BigDecimal("25")).orFail()
        val id = h.create(SubscriptionDraft(h.subscription(start = LocalDate.of(2026, 3, 15), walletId = wallet.id)))
        val outcome = h.subscriptions.markPaid(id, newCycle = false).orFail()
        val record = h.db.paymentDao().get(outcome.paymentRecordId!!)!!
        assertThat(record.walletTransactionId).isNotNull()
        assertThat(h.wallets.get(wallet.id)!!.balance).isEqualTo(BigDecimal("15"))
    }

    @Test fun processDue_walletFailureStopsBackfillAndReports() = runTest {
        val wallet = h.wallets.create("Card", io.github.submark.core.model.WalletKind.BALANCE_TRACKED, "USD", BigDecimal("15")).orFail()
        h.time.today = LocalDate.of(2026, 1, 10)
        val id = h.create(SubscriptionDraft(h.subscription(start = LocalDate.of(2026, 1, 15), renewal = RenewalType.AUTO, walletId = wallet.id)))
        h.time.today = LocalDate.of(2026, 3, 15)

        val summary = h.subscriptions.processDue()

        assertThat(summary.autoMarkedCount).isEqualTo(1)
        assertThat(summary.walletFailures.map { it.subscriptionId }).containsExactly(id)
        assertThat(h.get(id).nextPaymentDate).isEqualTo(LocalDate.of(2026, 2, 15))
        assertThat(h.wallets.get(wallet.id)!!.balance).isEqualTo(BigDecimal("5"))
    }

    @Test fun bundleSmartSync_marksOnlyChildrenDueNearMain() = runTest {
        h.settings.update { it.copy(subscriptions = it.subscriptions.copy(bundlePaymentSyncMode = BundlePaymentSyncMode.SMART)) }
        val near = SubscriptionDraft(h.subscription(name = "Near", start = LocalDate.of(2026, 3, 17)))
        val far = SubscriptionDraft(h.subscription(name = "Far", start = LocalDate.of(2026, 3, 28)))
        val mainId = h.create(SubscriptionDraft(h.subscription(name = "Main", start = LocalDate.of(2026, 3, 15)), children = listOf(near, far)))
        val children = h.db.subscriptionDao().getChildren(mainId)
        assertThat(h.get(mainId).bundleRole).isEqualTo(BundleRole.MAIN)
        assertThat(children.map { it.bundleRole }.toSet()).containsExactly(BundleRole.CHILD)

        val outcome = h.subscriptions.markPaid(mainId, newCycle = false).orFail()

        val nearId = children.first { it.name == "Near" }.id
        assertThat(outcome.syncedChildIds).containsExactly(nearId)
        val synced = h.db.paymentDao().getForSubscription(nearId).single()
        assertThat(synced.source).isEqualTo(PaymentSource.BUNDLE_SYNC)
        assertThat(synced.bundleParentPaymentId).isEqualTo(outcome.paymentRecordId)
    }

    @Test fun deleteMain_cascadesChildren_andReversesWallet() = runTest {
        val wallet = h.wallets.create("Card", io.github.submark.core.model.WalletKind.BALANCE_TRACKED, "USD", BigDecimal("50")).orFail()
        val mainId = h.create(
            SubscriptionDraft(
                h.subscription(name = "Main", start = LocalDate.of(2026, 3, 15), walletId = wallet.id),
                children = listOf(SubscriptionDraft(h.subscription(name = "Child"))),
            ),
        )
        h.subscriptions.markPaid(mainId, newCycle = false).orFail()
        assertThat(h.subscriptions.hasWalletCharges(mainId)).isTrue()

        val outcome = h.subscriptions.delete(mainId, reverseWalletCharges = true).orFail()

        assertThat(outcome.deletedIds).hasSize(2)
        assertThat(h.db.subscriptionDao().getAll()).isEmpty()
        assertThat(h.wallets.get(wallet.id)!!.balance).isEqualTo(BigDecimal("50"))
        assertThat(h.db.walletDao().getAllTransactions().all { it.subscriptionId == null }).isTrue()
    }

    @Test fun extend_requiresSingleCycle_andDetectsStale() = runTest {
        val id = h.create(
            SubscriptionDraft(h.subscription(start = LocalDate.of(2026, 3, 1)).copy(isSingleCycle = true, endDate = LocalDate.of(2026, 4, 1), billingCycle = null)),
        )
        val sub = h.get(id)
        val stale = h.subscriptions.extend(id, ExtendBy.Days(10), null, sub.updatedAt.minusSeconds(1))
        assertThat(stale.errorOrNull()).isEqualTo(DataError.Stale)

        val newEnd = h.subscriptions.extend(id, ExtendBy.Months(1), BigDecimal("3"), sub.updatedAt).orFail()

        assertThat(newEnd).isEqualTo(LocalDate.of(2026, 5, 1))
        val record = h.db.paymentDao().getForSubscription(id).single()
        assertThat(record.kind).isEqualTo(PaymentKind.EXTENSION)
        assertThat(record.extensionFrom).isEqualTo(LocalDate.of(2026, 4, 1))
        assertThat(record.amount).isEqualTo(BigDecimal("3"))
    }

    @Test fun update_startDateLockedOncePaymentsExist() = runTest {
        val id = h.create(SubscriptionDraft(h.subscription(start = LocalDate.of(2026, 3, 15))))
        h.subscriptions.markPaid(id, newCycle = false).orFail()
        val sub = h.get(id)
        val result = h.subscriptions.update(SubscriptionDraft(sub.copy(startDate = LocalDate.of(2026, 3, 1))))
        assertThat(result.errorOrNull()).isEqualTo(DataError.Invalid(InvalidReason.START_DATE_LOCKED))
    }

    @Test fun duplicateAppStoreId_isReportedUnlessAllowed() = runTest {
        h.create(SubscriptionDraft(h.subscription(name = "A").copy(appStoreId = "123")))
        val dup = h.subscriptions.create(SubscriptionDraft(h.subscription(name = "B").copy(appStoreId = "123")))
        assertThat(dup.errorOrNull()).isInstanceOf(DataError.DuplicateAppStoreId::class.java)
        assertThat(h.subscriptions.create(SubscriptionDraft(h.subscription(name = "B").copy(appStoreId = "123"), allowDuplicateAppStoreId = true)).isSuccess).isTrue()
    }

    @Test fun lifetime_recordsPurchase_andWishlistActivation() = runTest {
        val lifetime = h.create(SubscriptionDraft(h.subscription(kind = SubscriptionKind.LIFETIME, cycle = null, price = "99")))
        assertThat(h.db.paymentDao().getForSubscription(lifetime).single().kind).isEqualTo(PaymentKind.LIFETIME_PURCHASE)
        assertThat(h.get(lifetime).nextPaymentDate).isNull()

        val wish = h.create(SubscriptionDraft(h.subscription(kind = SubscriptionKind.WISHLIST, start = LocalDate.of(2026, 1, 1))))
        assertThat(h.get(wish).status).isEqualTo(SubscriptionStatus.PAUSED)
        h.subscriptions.activateWishlist(wish, toLifetime = false).orFail()
        val activated = h.get(wish)
        assertThat(activated.kind).isEqualTo(SubscriptionKind.REGULAR)
        assertThat(activated.status).isEqualTo(SubscriptionStatus.ACTIVE)
        assertThat(activated.startDate).isEqualTo(LocalDate.of(2026, 3, 15))
        assertThat(activated.nextPaymentDate).isEqualTo(LocalDate.of(2026, 3, 15))
    }

    @Test fun restoreFromArchive_restartsCycleToday() = runTest {
        val id = h.create(SubscriptionDraft(h.subscription(start = LocalDate.of(2026, 1, 1)).copy(endDate = LocalDate.of(2026, 3, 1))))
        h.subscriptions.pause(id).orFail()
        h.subscriptions.restoreFromArchive(id).orFail()
        val sub = h.get(id)
        assertThat(sub.status).isEqualTo(SubscriptionStatus.ACTIVE)
        assertThat(sub.cycleAnchorDate).isEqualTo(LocalDate.of(2026, 3, 15))
        assertThat(sub.nextPaymentDate).isEqualTo(LocalDate.of(2026, 3, 15))
        assertThat(sub.endDate).isNull()
    }
}
