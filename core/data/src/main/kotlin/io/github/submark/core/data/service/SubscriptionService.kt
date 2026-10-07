package io.github.submark.core.data.service

import io.github.submark.core.data.change.ChangeNotifier
import io.github.submark.core.data.repository.CustomFieldRepository
import io.github.submark.core.data.repository.TagRepository
import io.github.submark.core.data.result.DataError
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.data.result.InvalidReason
import io.github.submark.core.data.result.TransactionRunner
import io.github.submark.core.data.result.abort
import io.github.submark.core.data.settings.SettingsRepository
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.database.dao.PaymentDao
import io.github.submark.core.database.dao.SubscriptionDao
import io.github.submark.core.database.dao.SubscriptionExtrasDao
import io.github.submark.core.database.dao.WalletDao
import io.github.submark.core.domain.BillingCalculator
import io.github.submark.core.model.BillingCycle
import io.github.submark.core.model.BundlePaymentSyncMode
import io.github.submark.core.model.BundleRole
import io.github.submark.core.model.MarkTiming
import io.github.submark.core.model.PaymentKind
import io.github.submark.core.model.PaymentRecord
import io.github.submark.core.model.PaymentSource
import io.github.submark.core.model.RenewalType
import io.github.submark.core.model.Subscription
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.SubscriptionStatus
import io.github.submark.core.model.WalletTxnStatus
import io.github.submark.core.model.newId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** Subscription lifecycle and billing operations. Every mutation is one transaction followed by a change notification. */
@Singleton
class SubscriptionService @Inject internal constructor(
    private val subscriptionDao: SubscriptionDao,
    private val paymentDao: PaymentDao,
    private val walletDao: WalletDao,
    private val extrasDao: SubscriptionExtrasDao,
    private val tags: TagRepository,
    private val customFields: CustomFieldRepository,
    private val payments: PaymentService,
    private val storedValue: StoredValueService,
    private val shared: SharedService,
    private val settings: SettingsRepository,
    private val tx: TransactionRunner,
    private val notifier: ChangeNotifier,
    private val time: TimeProvider,
) {
    private val dueMutex = Mutex()

    /** Another subscription with the same App Store id, or null. */
    suspend fun findDuplicateAppStoreId(appStoreId: String?, excludeId: String = ""): Subscription? =
        appStoreId?.takeIf { it.isNotBlank() }?.let { subscriptionDao.findByAppStoreId(it, excludeId) }

    /** Creates a subscription (or a bundle MAIN with its children). Returns the new (main) id. */
    suspend fun create(draft: SubscriptionDraft): DataResult<String> {
        val prefs = settings.settings.first().subscriptions
        val ids = mutableSetOf<String>()
        val result = tx.run {
            val main = createInTx(draft, prefs.wishlistEnabled, parent = null)
            ids += main.id
            if (draft.children.isNotEmpty()) {
                draft.children.forEach { ids += createInTx(it, prefs.wishlistEnabled, parent = main).id }
            }
            main.id
        }
        if (result.isSuccess) notifier.notifyChanged(ids)
        return result
    }

    /**
     * Saves the edit form. Start date is locked once payments exist; schedule fields are recomputed when
     * cycle/anchor fields change. Balance, sharing, bundle role and timestamps are preserved. Children are edited separately.
     */
    suspend fun update(draft: SubscriptionDraft): DataResult<Unit> {
        val id = draft.subscription.id
        val result = tx.run {
            val old = subscriptionDao.get(id) ?: abort(DataError.NotFound)
            var new = normalize(draft.subscription, settings.settings.first().subscriptions.wishlistEnabled)
            validate(new)
            if (!draft.allowDuplicateAppStoreId) checkDuplicate(new)
            val hasPayments = paymentDao.countForSubscription(id) > 0
            if (hasPayments && new.startDate != old.startDate) abort(InvalidReason.START_DATE_LOCKED)
            val now = time.now()
            new = new.copy(
                createdAt = old.createdAt, updatedAt = now, storedValueBalance = old.storedValueBalance, isShared = old.isShared,
                bundleRole = old.bundleRole, parentId = old.parentId, calendarEventId = old.calendarEventId,
                pausedAt = when {
                    new.status == SubscriptionStatus.ACTIVE -> null
                    old.status == SubscriptionStatus.PAUSED -> old.pausedAt
                    else -> now
                },
            )
            new = reschedule(old, new, hasPayments)
            subscriptionDao.upsert(new)
            tags.replaceLinks(id, draft.tagIds)
            customFields.writeValues(id, new.categoryId, draft.customFieldValues)
        }
        if (result.isSuccess) notifier.notifyChanged(id)
        return result
    }

    /** True when payments of the subscription (or its children) are charged to non-deleted wallets. */
    suspend fun hasWalletCharges(id: String): Boolean {
        val ids = listOf(id) + subscriptionDao.getChildren(id).map { it.id }
        return ids.flatMap { paymentDao.getForSubscription(it) }.mapNotNull { it.walletTransactionId }.any { txnId ->
            val txn = walletDao.getTransaction(txnId)
            txn != null && txn.status == WalletTxnStatus.COMMITTED && walletDao.get(txn.walletId)?.deletedAt == null
        }
    }

    /**
     * Deletes the subscription and, for a bundle MAIN, its children. Wallet charges are refunded when
     * [reverseWalletCharges], otherwise kept as detached history.
     */
    suspend fun delete(id: String, reverseWalletCharges: Boolean = false): DataResult<DeleteOutcome> {
        val result = tx.run {
            val sub = subscriptionDao.get(id) ?: abort(DataError.NotFound)
            val targets = listOf(sub) + subscriptionDao.getChildren(sub.id)
            val ids = targets.map { it.id }
            val photos = extrasDao.getPhotos(ids).map { it.fileName }
            for (target in targets) {
                if (reverseWalletCharges) {
                    paymentDao.getForSubscription(target.id).mapNotNull { it.walletTransactionId }
                        .forEach { payments.reverseWalletInTx(it, SystemNotes.SUBSCRIPTION_DELETED) }
                }
                walletDao.detachSubscription(target.id)
                subscriptionDao.deleteById(target.id)
            }
            DeleteOutcome(ids.toSet(), photos)
        }
        result.getOrNull()?.let { notifier.notifyChanged(it.deletedIds) }
        return result
    }

    /** PAUSED; reminders stop and nothing is auto-marked. Lifetime: only toggles "in use". */
    suspend fun pause(id: String): DataResult<Unit> = mutate(id) { sub ->
        if (sub.status == SubscriptionStatus.PAUSED) return@mutate sub
        sub.copy(status = SubscriptionStatus.PAUSED, pausedAt = time.now())
    }

    /** ACTIVE again. Missed cycles are not charged: a past next date moves to the first occurrence from today. */
    suspend fun activate(id: String): DataResult<Unit> = mutate(id) { sub ->
        if (sub.kind == SubscriptionKind.WISHLIST) abort(InvalidReason.WISHLIST_NOT_ALLOWED)
        val active = sub.copy(status = SubscriptionStatus.ACTIVE, pausedAt = null)
        val next = active.nextPaymentDate
        val cycle = BillingCalculator.cycleLength(active)
        val anchor = active.cycleAnchorDate
        if (next != null && next < time.today() && cycle != null && anchor != null && !active.isSingleCycle) {
            val moved = BillingCalculator.firstOccurrenceAfter(anchor, cycle, time.today(), inclusive = true, fixedDay = active.fixedPaymentDay)
            active.copy(nextPaymentDate = capByEnd(moved, active.endDate))
        } else {
            active
        }
    }

    /** Restore from the archive: ACTIVE, cycle restarts today (anchor = next = today), an expired end date is cleared. */
    suspend fun restoreFromArchive(id: String): DataResult<Unit> = mutate(id) { sub ->
        if (sub.kind == SubscriptionKind.WISHLIST) abort(InvalidReason.WISHLIST_NOT_ALLOWED)
        val today = time.today()
        val end = sub.endDate?.takeIf { it >= today }
        if (sub.kind == SubscriptionKind.LIFETIME) {
            sub.copy(status = SubscriptionStatus.ACTIVE, pausedAt = null, endDate = end)
        } else {
            sub.copy(status = SubscriptionStatus.ACTIVE, pausedAt = null, endDate = end, cycleAnchorDate = today, nextPaymentDate = today)
        }
    }

    /**
     * Marks the current due occurrence paid today. [newCycle] re-bases the schedule on today (NEW_CYCLE);
     * otherwise the original schedule is kept. Shared subscriptions record the user's share. A wallet-linked
     * subscription is charged; if that fails nothing is saved. A bundle MAIN applies the payment sync preference.
     */
    suspend fun markPaid(id: String, newCycle: Boolean, source: PaymentSource = PaymentSource.USER_MANUAL): DataResult<MarkPaidOutcome> {
        val syncMode = settings.settings.first().subscriptions.bundlePaymentSyncMode
        val result = tx.run {
            val sub = subscriptionDao.get(id) ?: abort(DataError.NotFound)
            if (!isMarkable(sub) || sub.renewalType == RenewalType.TRIAL) abort(InvalidReason.NOT_MARKABLE)
            val today = time.today()
            val due = sub.nextPaymentDate!!
            val timing = BillingCalculator.timingOf(due, today, newCycle)
            val marked = markInTx(sub, due, today, timing, source, chargeWallet = true, bundleParentPaymentId = null)
            val synced = if (sub.bundleRole == BundleRole.MAIN) syncChildren(sub, due, today, newCycle, syncMode, marked.paymentId) else emptyList()
            MarkPaidOutcome(timing, marked.paymentId, marked.subscription.nextPaymentDate, synced)
        }
        result.getOrNull()?.let { notifier.notifyChanged(setOf(id) + it.syncedChildIds) }
        return result
    }

    /**
     * Extends a single-cycle subscription's end date. Always writes an EXTENSION record (amount = [fee] or 0);
     * never charges a wallet. Fails with [DataError.Stale] if the subscription changed since [expectedUpdatedAt].
     */
    suspend fun extend(id: String, by: ExtendBy, fee: BigDecimal?, expectedUpdatedAt: Instant): DataResult<LocalDate> {
        val result = tx.run {
            val sub = subscriptionDao.get(id) ?: abort(DataError.NotFound)
            if (sub.updatedAt != expectedUpdatedAt) abort(DataError.Stale)
            val end = sub.endDate
            if (!sub.isSingleCycle || end == null || sub.kind == SubscriptionKind.LIFETIME) abort(InvalidReason.NOT_EXTENDABLE)
            if (fee != null && fee.signum() < 0) abort(InvalidReason.NEGATIVE_AMOUNT)
            val today = time.today()
            val newEnd = when (by) {
                is ExtendBy.Days -> if (by.days in 1..1000) end.plusDays(by.days.toLong()) else abort(InvalidReason.EXTEND_VALUE_OUT_OF_RANGE)
                is ExtendBy.Months -> if (by.months in 1..120) end.plusMonths(by.months.toLong()) else abort(InvalidReason.EXTEND_VALUE_OUT_OF_RANGE)
                is ExtendBy.Until -> if (by.date > end && by.date > today) by.date else abort(InvalidReason.EXTEND_DATE_INVALID)
            }
            val now = time.now()
            paymentDao.upsert(
                PaymentRecord(
                    subscriptionId = sub.id, amount = fee ?: BigDecimal.ZERO, currencyCode = sub.currencyCode,
                    paymentDate = today, kind = PaymentKind.EXTENSION, extensionFrom = end, extensionTo = newEnd,
                    prevEndDate = sub.endDate, prevNextPaymentDate = sub.nextPaymentDate, prevLastPaymentDate = sub.lastPaymentDate,
                    createdAt = now, updatedAt = now,
                ),
            )
            val next = if (sub.renewalType == RenewalType.AUTO && sub.nextPaymentDate != null) newEnd else sub.nextPaymentDate
            subscriptionDao.upsert(sub.copy(endDate = newEnd, nextPaymentDate = next, updatedAt = now))
            newEnd
        }
        if (result.isSuccess) notifier.notifyChanged(id)
        return result
    }

    /**
     * Resolves an ended trial: records the trial-end payment (source TRIAL_EXPIRED) and switches the
     * renewal type to [renewal] (AUTO or MANUAL). Stored-value subscriptions deduct instead.
     */
    suspend fun resolveTrial(id: String, renewal: RenewalType): DataResult<MarkPaidOutcome> {
        if (renewal == RenewalType.TRIAL) return DataResult.Failure(DataError.Invalid(InvalidReason.INVALID_RENEWAL_TYPE))
        val result = tx.run {
            val sub = subscriptionDao.get(id) ?: abort(DataError.NotFound)
            if (sub.renewalType != RenewalType.TRIAL) abort(InvalidReason.NOT_IN_TRIAL)
            val trialEnd = BillingCalculator.trialEndDate(sub) ?: abort(InvalidReason.NOT_IN_TRIAL)
            if (trialEnd > time.today()) abort(InvalidReason.NOT_IN_TRIAL)
            val resolved = sub.copy(renewalType = renewal, cycleAnchorDate = sub.cycleAnchorDate ?: trialEnd, nextPaymentDate = sub.nextPaymentDate ?: trialEnd)
            if (BillingCalculator.cycleLength(resolved) == null) abort(InvalidReason.NOT_MARKABLE)
            val marked = markInTx(resolved, trialEnd, trialEnd, MarkTiming.ON_TIME, PaymentSource.TRIAL_EXPIRED, chargeWallet = true, bundleParentPaymentId = null)
            MarkPaidOutcome(MarkTiming.ON_TIME, marked.paymentId, marked.subscription.nextPaymentDate, emptyList())
        }
        if (result.isSuccess) notifier.notifyChanged(id)
        return result
    }

    /**
     * Converts a wishlist item to a subscription (REGULAR, scheduled from today) or a lifetime purchase
     * (purchase payment recorded, optionally charged to [walletId]).
     */
    suspend fun activateWishlist(id: String, toLifetime: Boolean, walletId: String? = null): DataResult<Unit> {
        val result = tx.run {
            val sub = subscriptionDao.get(id) ?: abort(DataError.NotFound)
            if (sub.kind != SubscriptionKind.WISHLIST) abort(InvalidReason.NOT_WISHLIST)
            val today = time.today()
            val now = time.now()
            var converted = sub.copy(
                kind = if (toLifetime) SubscriptionKind.LIFETIME else SubscriptionKind.REGULAR,
                status = SubscriptionStatus.ACTIVE, pausedAt = null, startDate = today, updatedAt = now,
                billingCycle = if (toLifetime) null else sub.billingCycle ?: BillingCycle.MONTHLY,
                renewalType = if (sub.renewalType == RenewalType.TRIAL) RenewalType.AUTO else sub.renewalType,
                endDate = sub.endDate?.takeIf { it > today },
            )
            if (toLifetime) converted = converted.copy(fixedPaymentDay = null, isSingleCycle = false)
            validate(converted)
            converted = applyInitialSchedule(converted, today, generateHistory = false)
            subscriptionDao.upsert(converted)
            if (toLifetime) recordLifetimePurchase(converted, walletId)
        }
        if (result.isSuccess) notifier.notifyChanged(id)
        return result
    }

    /**
     * App start / daily work: auto-marks AUTO-renew occurrences up to today (one record per missed
     * occurrence: SYSTEM_AUTO for today, SYSTEM_OVERDUE for the past; stored value deducts instead),
     * reports ended trials, and pauses ACTIVE subscriptions whose end date has passed.
     */
    suspend fun processDue(): ProcessDueSummary = dueMutex.withLock {
        val today = time.today()
        val changed = mutableSetOf<String>()
        val trialEnded = mutableListOf<String>()
        val failures = mutableListOf<WalletFailure>()
        var marked = 0
        for (sub in subscriptionDao.getActiveDueOnOrBefore(today)) {
            when (sub.renewalType) {
                RenewalType.TRIAL -> trialEnded += sub.id
                RenewalType.MANUAL -> Unit
                RenewalType.AUTO -> {
                    var current = sub
                    var guard = 0
                    while (guard++ < MAX_BACKFILL) {
                        val due = current.nextPaymentDate ?: break
                        if (due > today) break
                        val source = if (due == today) PaymentSource.SYSTEM_AUTO else PaymentSource.SYSTEM_OVERDUE
                        val step = tx.run { markInTx(current, due, due, MarkTiming.ON_TIME, source, chargeWallet = true, bundleParentPaymentId = null) }
                        when (step) {
                            is DataResult.Success -> {
                                current = step.value.subscription
                                marked++
                                changed += sub.id
                            }
                            is DataResult.Failure -> {
                                failures += WalletFailure(sub.id, sub.name, step.error)
                                break
                            }
                        }
                    }
                }
            }
        }
        val expired = subscriptionDao.getActiveExpired(today)
        if (expired.isNotEmpty()) {
            tx.run {
                val now = time.now()
                expired.forEach { subscriptionDao.upsert(it.copy(status = SubscriptionStatus.PAUSED, pausedAt = now, updatedAt = now)) }
            }
            changed += expired.map { it.id }
        }
        if (changed.isNotEmpty()) notifier.notifyChanged(changed)
        ProcessDueSummary(marked, expired.map { it.id }, expired.map { it.name }, trialEnded, failures)
    }

    // ---- internals ----

    private data class Marked(val subscription: Subscription, val paymentId: String?)

    private fun isMarkable(sub: Subscription) =
        sub.status == SubscriptionStatus.ACTIVE &&
            (sub.kind == SubscriptionKind.REGULAR || sub.kind == SubscriptionKind.STORED_VALUE) &&
            sub.nextPaymentDate != null && BillingCalculator.cycleLength(sub) != null

    /** Records one occurrence as paid (or deducted) and advances the schedule. Must run inside a transaction. */
    private suspend fun markInTx(
        sub: Subscription,
        due: LocalDate,
        paidOn: LocalDate,
        timing: MarkTiming,
        source: PaymentSource,
        chargeWallet: Boolean,
        bundleParentPaymentId: String?,
    ): Marked {
        val outcome = BillingCalculator.markPaid(sub, due, paidOn, timing)
        val now = time.now()
        var paymentId: String? = null
        var base = sub
        if (sub.kind == SubscriptionKind.STORED_VALUE) {
            base = storedValue.deductInTx(sub, due).second
        } else {
            val record = PaymentRecord(
                subscriptionId = sub.id, amount = shared.userShareOf(sub), currencyCode = sub.currencyCode, paymentDate = paidOn,
                source = source, markTiming = timing, originalDueDate = due.takeIf { timing != MarkTiming.ON_TIME },
                prevEndDate = sub.endDate, prevNextPaymentDate = sub.nextPaymentDate, prevLastPaymentDate = sub.lastPaymentDate,
                bundleParentPaymentId = bundleParentPaymentId, createdAt = now, updatedAt = now,
            )
            paymentId = payments.insertWithWallet(record, sub.walletId.takeIf { chargeWallet }).id
        }
        val updated = base.copy(
            cycleAnchorDate = outcome.anchor, nextPaymentDate = outcome.nextPaymentDate,
            lastPaymentDate = outcome.lastPaymentDate, updatedAt = now,
        )
        subscriptionDao.upsert(updated)
        return Marked(updated, paymentId)
    }

    /** Bundle payment sync: ALWAYS all due children, SMART those due within ±3 days of the main, NEVER none. Wallets are not charged. */
    private suspend fun syncChildren(
        main: Subscription,
        mainDue: LocalDate,
        paidOn: LocalDate,
        newCycle: Boolean,
        mode: BundlePaymentSyncMode,
        mainPaymentId: String?,
    ): List<String> {
        if (mode == BundlePaymentSyncMode.NEVER) return emptyList()
        return subscriptionDao.getChildren(main.id)
            .filter { isMarkable(it) && it.renewalType != RenewalType.TRIAL }
            .filter { mode == BundlePaymentSyncMode.ALWAYS || abs(ChronoUnit.DAYS.between(mainDue, it.nextPaymentDate!!)) <= SMART_SYNC_DAYS }
            .map { child ->
                val due = child.nextPaymentDate!!
                markInTx(child, due, paidOn, BillingCalculator.timingOf(due, paidOn, newCycle), PaymentSource.BUNDLE_SYNC, chargeWallet = false, mainPaymentId)
                child.id
            }
    }

    private suspend fun createInTx(draft: SubscriptionDraft, wishlistEnabled: Boolean, parent: Subscription?): Subscription {
        val now = time.now()
        val today = time.today()
        val role = when {
            parent != null -> BundleRole.CHILD
            draft.children.isNotEmpty() -> BundleRole.MAIN
            draft.subscription.bundleRole == BundleRole.MAIN -> abort(InvalidReason.BUNDLE_NEEDS_CHILD)
            else -> BundleRole.NONE
        }
        var sub = normalize(draft.subscription, wishlistEnabled).copy(
            bundleRole = role, parentId = parent?.id, createdAt = now, updatedAt = now,
            storedValueBalance = BigDecimal.ZERO, isShared = false, calendarEventId = null,
        )
        if (sub.status == SubscriptionStatus.PAUSED) sub = sub.copy(pausedAt = now)
        validate(sub)
        if (!draft.allowDuplicateAppStoreId) checkDuplicate(sub)
        if (subscriptionDao.get(sub.id) != null) sub = sub.copy(id = newId())

        val generateHistory = draft.generateHistory && sub.kind == SubscriptionKind.REGULAR
        val schedule = if (isRecurring(sub)) BillingCalculator.initialSchedule(sub, today, generateHistory) else null
        sub = applyInitialSchedule(sub, today, generateHistory)
        subscriptionDao.upsert(sub)

        schedule?.historicalDates?.forEach { date ->
            paymentDao.upsert(
                PaymentRecord(
                    subscriptionId = sub.id, amount = sub.price, currencyCode = sub.currencyCode, paymentDate = date,
                    source = PaymentSource.HISTORY_GENERATED, createdAt = now, updatedAt = now,
                ),
            )
        }
        if (sub.kind == SubscriptionKind.LIFETIME) recordLifetimePurchase(sub, draft.purchaseWalletId)
        val deposit = draft.initialDeposit
        if (sub.kind == SubscriptionKind.STORED_VALUE && deposit != null && deposit.signum() > 0) {
            storedValue.depositInTx(sub, deposit, sub.currencyCode, null, draft.initialDepositWalletId, isInitial = true, date = minOf(sub.startDate, today))
        }
        tags.replaceLinks(sub.id, draft.tagIds)
        customFields.writeValues(sub.id, sub.categoryId, draft.customFieldValues)
        return sub
    }

    private suspend fun recordLifetimePurchase(sub: Subscription, walletId: String?) {
        val now = time.now()
        val record = PaymentRecord(
            subscriptionId = sub.id, amount = sub.price, currencyCode = sub.currencyCode, paymentDate = sub.startDate,
            kind = PaymentKind.LIFETIME_PURCHASE, createdAt = now, updatedAt = now,
        )
        payments.insertWithWallet(record, walletId)
        subscriptionDao.upsert(sub.copy(lastPaymentDate = sub.startDate, updatedAt = now))
    }

    private fun applyInitialSchedule(sub: Subscription, today: LocalDate, generateHistory: Boolean): Subscription {
        if (!isRecurring(sub)) return sub.copy(cycleAnchorDate = null, nextPaymentDate = null, lastPaymentDate = null)
        val schedule = BillingCalculator.initialSchedule(sub, today, generateHistory)
        return sub.copy(cycleAnchorDate = schedule.anchor, nextPaymentDate = schedule.nextPaymentDate, lastPaymentDate = schedule.lastPaymentDate)
    }

    /** Recomputes schedule fields after an edit. */
    private fun reschedule(old: Subscription, new: Subscription, hasPayments: Boolean): Subscription {
        if (!isRecurring(new)) return new.copy(cycleAnchorDate = null, nextPaymentDate = null, lastPaymentDate = old.lastPaymentDate)
        val scheduleChanged = old.kind != new.kind || old.billingCycle != new.billingCycle ||
            old.customCycleCount != new.customCycleCount || old.customCycleUnit != new.customCycleUnit ||
            old.isSingleCycle != new.isSingleCycle || old.startDate != new.startDate ||
            old.renewalType != new.renewalType && (old.renewalType == RenewalType.TRIAL || new.renewalType == RenewalType.TRIAL) ||
            old.trialStartDate != new.trialStartDate || old.trialDays != new.trialDays ||
            (new.isSingleCycle && old.endDate != new.endDate) ||
            (new.isSingleCycle && old.renewalType != new.renewalType)
        val kept = new.copy(cycleAnchorDate = old.cycleAnchorDate, nextPaymentDate = old.nextPaymentDate, lastPaymentDate = old.lastPaymentDate)
        val today = time.today()
        if (scheduleChanged) {
            if (!hasPayments || new.isSingleCycle) {
                val schedule = BillingCalculator.initialSchedule(new, today, generateHistory = false)
                return kept.copy(cycleAnchorDate = schedule.anchor, nextPaymentDate = schedule.nextPaymentDate)
            }
            val cycle = BillingCalculator.cycleLength(new) ?: return kept
            val last = old.lastPaymentDate
            return if (last != null) {
                kept.copy(cycleAnchorDate = last, nextPaymentDate = capByEnd(BillingCalculator.occurrence(last, cycle, 1, new.fixedPaymentDay), new.endDate))
            } else {
                val anchor = old.cycleAnchorDate ?: BillingCalculator.firstBillingDate(new)
                kept.copy(
                    cycleAnchorDate = anchor,
                    nextPaymentDate = capByEnd(BillingCalculator.firstOccurrenceAfter(anchor, cycle, today, inclusive = true, fixedDay = new.fixedPaymentDay), new.endDate),
                )
            }
        }
        if (old.endDate != new.endDate) {
            val cycle = BillingCalculator.cycleLength(new) ?: return kept
            val anchor = old.cycleAnchorDate ?: BillingCalculator.firstBillingDate(new)
            // A previously capped (null) next date reappears when the end date moves out or is cleared.
            val next = old.nextPaymentDate ?: run {
                val after = old.lastPaymentDate ?: anchor.minusDays(1)
                BillingCalculator.firstOccurrenceAfter(anchor, cycle, after, inclusive = false, fixedDay = new.fixedPaymentDay)
            }
            return kept.copy(nextPaymentDate = capByEnd(next, new.endDate))
        }
        return kept
    }

    private fun isRecurring(sub: Subscription) = sub.kind == SubscriptionKind.REGULAR || sub.kind == SubscriptionKind.STORED_VALUE

    private fun normalize(sub: Subscription, wishlistEnabled: Boolean): Subscription {
        var s = sub.copy(name = sub.name.trim())
        if (s.kind == SubscriptionKind.WISHLIST && !wishlistEnabled) s = s.copy(kind = SubscriptionKind.REGULAR)
        when (s.kind) {
            SubscriptionKind.WISHLIST -> s = s.copy(status = SubscriptionStatus.PAUSED)
            SubscriptionKind.LIFETIME -> s = s.copy(billingCycle = null, isSingleCycle = false, fixedPaymentDay = null)
            else -> Unit
        }
        if (s.billingCycle != BillingCycle.CUSTOM) s = s.copy(customCycleCount = null, customCycleUnit = null)
        if (s.renewalType != RenewalType.TRIAL) s = s.copy(trialStartDate = null, trialDays = null)
        return s
    }

    private fun validate(sub: Subscription) {
        if (sub.name.isBlank()) abort(InvalidReason.BLANK_NAME)
        if (sub.price.signum() < 0) abort(InvalidReason.NEGATIVE_AMOUNT)
        val end = sub.endDate
        if (end != null && end <= sub.startDate) abort(InvalidReason.END_NOT_AFTER_START)
        if (sub.isSingleCycle && end == null) abort(InvalidReason.SINGLE_CYCLE_REQUIRES_END)
        if (sub.billingCycle == BillingCycle.CUSTOM && (sub.customCycleCount ?: 0) <= 0 || sub.billingCycle == BillingCycle.CUSTOM && sub.customCycleUnit == null) {
            abort(InvalidReason.CUSTOM_CYCLE_REQUIRED)
        }
        if (isRecurring(sub) && sub.billingCycle == null && !sub.isSingleCycle) abort(InvalidReason.BILLING_CYCLE_REQUIRED)
        if (sub.renewalType == RenewalType.TRIAL && (sub.trialStartDate == null || (sub.trialDays ?: 0) <= 0)) abort(InvalidReason.TRIAL_FIELDS_REQUIRED)
        sub.fixedPaymentDay?.let { day ->
            val monthBased = sub.billingCycle in MONTH_BASED_CYCLES && !sub.isSingleCycle
            if (day !in 1..31 || !monthBased) abort(InvalidReason.FIXED_DAY_UNSUPPORTED)
        }
    }

    private suspend fun checkDuplicate(sub: Subscription) {
        findDuplicateAppStoreId(sub.appStoreId, sub.id)?.let { abort(DataError.DuplicateAppStoreId(it)) }
    }

    private suspend fun mutate(id: String, change: suspend (Subscription) -> Subscription): DataResult<Unit> {
        val result = tx.run {
            val sub = subscriptionDao.get(id) ?: abort(DataError.NotFound)
            val updated = change(sub)
            if (updated != sub) subscriptionDao.upsert(updated.copy(updatedAt = time.now()))
        }
        if (result.isSuccess) notifier.notifyChanged(id)
        return result
    }

    private fun capByEnd(next: LocalDate?, end: LocalDate?): LocalDate? = next?.takeIf { end == null || it <= end }

    private companion object {
        const val SMART_SYNC_DAYS = 3L
        const val MAX_BACKFILL = 1000
        val MONTH_BASED_CYCLES = setOf(BillingCycle.MONTHLY, BillingCycle.QUARTERLY, BillingCycle.SEMIANNUALLY, BillingCycle.ANNUALLY)
    }
}
