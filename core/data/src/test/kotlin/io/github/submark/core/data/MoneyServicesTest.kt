package io.github.submark.core.data

import com.google.common.truth.Truth.assertThat
import io.github.submark.core.data.result.DataError
import io.github.submark.core.data.result.InvalidReason
import io.github.submark.core.data.service.NewPayment
import io.github.submark.core.data.service.SubscriptionDraft
import io.github.submark.core.model.DateAdjustmentMode
import io.github.submark.core.model.PaymentKind
import io.github.submark.core.model.RenewalType
import io.github.submark.core.model.SplitMode
import io.github.submark.core.model.StoredValueRecordType
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionStatus
import io.github.submark.core.model.WalletKind
import io.github.submark.core.model.WalletTxnStatus
import io.github.submark.core.model.WalletTxnType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class MoneyServicesTest {
    private lateinit var h: TestHarness

    @Before fun setUp() = runTest {
        h = TestHarness(LocalDate.of(2026, 3, 15))
        h.seeder.seed()
    }

    @After fun tearDown() = h.close()

    @Test fun addPayment_advancesSchedule_deleteRevertsDatesAndWallet() = runTest {
        val wallet = h.wallets.create("Card", WalletKind.BALANCE_TRACKED, "USD", BigDecimal("100")).orFail()
        val id = h.create(SubscriptionDraft(h.subscription(start = LocalDate.of(2026, 3, 1))))
        val before = h.get(id)
        assertThat(before.nextPaymentDate).isEqualTo(LocalDate.of(2026, 4, 1))

        val outcome = h.payments.add(
            NewPayment(id, BigDecimal("12"), "USD", LocalDate.of(2026, 3, 14), walletId = wallet.id),
        ).orFail()

        assertThat(outcome.nextPaymentDate).isEqualTo(LocalDate.of(2026, 4, 14))
        assertThat(outcome.walletCharged).isTrue()
        assertThat(h.get(id).lastPaymentDate).isEqualTo(LocalDate.of(2026, 3, 14))
        assertThat(h.wallets.get(wallet.id)!!.balance).isEqualTo(BigDecimal("88"))

        h.payments.delete(outcome.record.id).orFail()

        val reverted = h.get(id)
        assertThat(reverted.nextPaymentDate).isEqualTo(before.nextPaymentDate)
        assertThat(reverted.lastPaymentDate).isNull()
        assertThat(h.wallets.get(wallet.id)!!.balance).isEqualTo(BigDecimal("100"))
        val txns = h.db.walletDao().getAllTransactions()
        assertThat(txns.single { it.type == WalletTxnType.EXPENSE }.status).isEqualTo(WalletTxnStatus.REVERSED)
        assertThat(txns.single { it.type == WalletTxnType.REFUND }.signedDelta).isEqualTo(BigDecimal("12"))
    }

    @Test fun deleteOlderPayment_doesNotRevertNewerState() = runTest {
        val id = h.create(SubscriptionDraft(h.subscription(start = LocalDate.of(2026, 3, 1))))
        val first = h.payments.add(NewPayment(id, BigDecimal("10"), "USD", LocalDate.of(2026, 3, 1))).orFail()
        h.payments.add(NewPayment(id, BigDecimal("10"), "USD", LocalDate.of(2026, 3, 10))).orFail()
        val beforeDelete = h.get(id)

        h.payments.delete(first.record.id).orFail()

        assertThat(h.get(id).nextPaymentDate).isEqualTo(beforeDelete.nextPaymentDate)
        assertThat(h.get(id).lastPaymentDate).isEqualTo(LocalDate.of(2026, 3, 10))
    }

    @Test fun addPayment_endDateAdjustmentReactivatesPaused() = runTest {
        val id = h.create(SubscriptionDraft(h.subscription(start = LocalDate.of(2026, 1, 1)).copy(endDate = LocalDate.of(2026, 3, 1))))
        h.subscriptions.pause(id).orFail()
        val outcome = h.payments.add(
            NewPayment(
                id, BigDecimal("5"), "USD", LocalDate.of(2026, 3, 15), inAppPurchase = true,
                dateAdjustment = DateAdjustmentMode.END_DATE, adjustmentTargetDate = LocalDate.of(2026, 6, 1),
            ),
        ).orFail()
        assertThat(outcome.reactivated).isTrue()
        assertThat(outcome.record.kind).isEqualTo(PaymentKind.IN_APP_PURCHASE)
        assertThat(h.get(id).endDate).isEqualTo(LocalDate.of(2026, 6, 1))
        assertThat(h.get(id).status).isEqualTo(SubscriptionStatus.ACTIVE)

        val invalid = h.payments.add(
            NewPayment(id, BigDecimal("5"), "USD", LocalDate.of(2026, 3, 15), dateAdjustment = DateAdjustmentMode.END_DATE, adjustmentTargetDate = LocalDate.of(2026, 5, 1)),
        )
        assertThat(invalid.errorOrNull()).isEqualTo(DataError.Invalid(InvalidReason.ADJUSTMENT_TARGET_INVALID))
    }

    @Test fun editPayment_rechargesWallet() = runTest {
        val wallet = h.wallets.create("Card", WalletKind.SETTLEMENT_ONLY, "USD").orFail()
        val id = h.create(SubscriptionDraft(h.subscription()))
        val added = h.payments.add(NewPayment(id, BigDecimal("10"), "USD", LocalDate.of(2026, 3, 15), walletId = wallet.id)).orFail().record
        val edited = h.payments.edit(
            io.github.submark.core.data.service.PaymentEdit(added.id, BigDecimal("4"), "USD", added.paymentDate, added.status, inAppPurchase = false),
        ).orFail()
        assertThat(edited.walletTransactionId).isNotEqualTo(added.walletTransactionId)
        assertThat(h.wallets.get(wallet.id)!!.balance).isEqualTo(BigDecimal("-4"))
    }

    @Test fun storedValue_depositDeductAndGuardedDelete() = runTest {
        val id = h.create(
            SubscriptionDraft(
                h.subscription(kind = SubscriptionKind.STORED_VALUE, start = LocalDate.of(2026, 3, 15), renewal = RenewalType.AUTO),
                initialDeposit = BigDecimal("30"),
            ),
        )
        assertThat(h.get(id).storedValueBalance).isEqualTo(BigDecimal("30"))
        val deposit = h.db.storedValueDao().getForSubscription(id).single()
        assertThat(deposit.isInitial).isTrue()
        val depositPayment = h.db.paymentDao().get(deposit.paymentRecordId!!)!!
        assertThat(depositPayment.kind).isEqualTo(PaymentKind.STORED_VALUE_DEPOSIT)

        // Auto deduction: balance only, no new payment record.
        val summary = h.subscriptions.processDue()
        assertThat(summary.autoMarkedCount).isEqualTo(1)
        assertThat(h.get(id).storedValueBalance).isEqualTo(BigDecimal("20"))
        assertThat(h.get(id).nextPaymentDate).isEqualTo(LocalDate.of(2026, 4, 15))
        assertThat(h.db.paymentDao().getForSubscription(id)).hasSize(1)
        assertThat(h.db.storedValueDao().getForSubscription(id).count { it.type == StoredValueRecordType.DEDUCTION }).isEqualTo(1)

        // Removing the 30 deposit would leave -10.
        val blocked = h.storedValue.deleteRecord(deposit.id)
        assertThat(blocked.errorOrNull()).isEqualTo(DataError.Invalid(InvalidReason.BALANCE_WOULD_BE_NEGATIVE))

        val topUp = h.storedValue.topUp(id, BigDecimal("5"), "USD").orFail()
        assertThat(h.get(id).storedValueBalance).isEqualTo(BigDecimal("25"))
        h.storedValue.deleteRecord(topUp.id).orFail()
        assertThat(h.get(id).storedValueBalance).isEqualTo(BigDecimal("20"))
        assertThat(h.db.paymentDao().get(topUp.paymentRecordId!!)).isNull()
        assertThat(h.storedValue.recomputeBalance(id).orFail()).isEqualTo(BigDecimal("20"))
    }

    @Test fun storedValueTopUp_walletFailureSavesNothing() = runTest {
        val wallet = h.wallets.create("Cash", WalletKind.BALANCE_TRACKED, "USD", BigDecimal("1")).orFail()
        val id = h.create(SubscriptionDraft(h.subscription(kind = SubscriptionKind.STORED_VALUE)))
        val result = h.storedValue.topUp(id, BigDecimal("5"), "USD", walletId = wallet.id)
        assertThat(result.errorOrNull()).isInstanceOf(DataError.InsufficientFunds::class.java)
        assertThat(h.db.storedValueDao().getForSubscription(id)).isEmpty()
        assertThat(h.db.paymentDao().getForSubscription(id)).isEmpty()
        assertThat(h.get(id).storedValueBalance).isEqualTo(BigDecimal.ZERO)
    }

    @Test fun walletKinds_creditLimitAndDeactivationUnlinks() = runTest {
        val credit = h.wallets.create("Credit", WalletKind.CREDIT, "USD", creditLimit = BigDecimal("50")).orFail()
        h.wallets.deduct(credit.id, BigDecimal("40")).orFail()
        assertThat(h.wallets.deduct(credit.id, BigDecimal("20")).errorOrNull()).isInstanceOf(DataError.InsufficientFunds::class.java)
        assertThat(h.wallets.get(credit.id)!!.balance).isEqualTo(BigDecimal("-40"))

        val id = h.create(SubscriptionDraft(h.subscription(walletId = credit.id)))
        h.wallets.deactivate(credit.id).orFail()
        assertThat(h.get(id).walletId).isNull()
        assertThat(h.wallets.deduct(credit.id, BigDecimal("1")).errorOrNull()).isEqualTo(DataError.Invalid(InvalidReason.WALLET_INACTIVE))
    }

    @Test fun sharedSubscription_recordsUserShare() = runTest {
        val id = h.create(SubscriptionDraft(h.subscription(price = "10", start = LocalDate.of(2026, 3, 15))))
        h.shared.enable(id, "Me").orFail()
        val other = io.github.submark.core.model.SharedMember(
            subscriptionId = id, name = "Alex", joinedAt = h.time.today, createdAt = h.time.now(), updatedAt = h.time.now(),
        )
        h.shared.addMember(other).orFail()
        h.shared.addMember(other.copy(id = io.github.submark.core.model.newId(), name = "Sam")).orFail()
        assertThat(h.shared.userShare(id)).isEqualTo(BigDecimal("3.34"))

        val outcome = h.subscriptions.markPaid(id, newCycle = false).orFail()
        assertThat(h.db.paymentDao().get(outcome.paymentRecordId!!)!!.amount).isEqualTo(BigDecimal("3.34"))

        h.shared.setSplitMode(id, SplitMode.CREATOR_PAYS).orFail()
        assertThat(h.shared.userShare(id)).isEqualTo(BigDecimal("10"))
        val creator = h.db.sharedDao().getMembers(id).single { it.isCreator }
        assertThat(h.shared.deleteMember(id, creator.id).errorOrNull()).isEqualTo(DataError.Invalid(InvalidReason.CREATOR_CANNOT_BE_DELETED))
        h.shared.disable(id).orFail()
        assertThat(h.db.sharedDao().getMembers(id)).isEmpty()
        assertThat(h.get(id).isShared).isFalse()
    }
}
