package io.github.submark.core.data.service

import io.github.submark.core.data.change.ChangeNotifier
import io.github.submark.core.data.result.DataError
import io.github.submark.core.data.result.DataResult
import io.github.submark.core.data.result.InvalidReason
import io.github.submark.core.data.result.TransactionRunner
import io.github.submark.core.data.result.abort
import io.github.submark.core.data.time.TimeProvider
import io.github.submark.core.database.dao.SharedDao
import io.github.submark.core.database.dao.SubscriptionDao
import io.github.submark.core.domain.SplitCalculator
import io.github.submark.core.model.MemberStatus
import io.github.submark.core.model.SharedConfig
import io.github.submark.core.model.SharedMember
import io.github.submark.core.model.SplitMode
import io.github.submark.core.model.Subscription
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/** Shared subscriptions: sharing on/off, members, split mode and the user's own share. */
@Singleton
class SharedService @Inject internal constructor(
    private val sharedDao: SharedDao,
    private val subscriptionDao: SubscriptionDao,
    private val tx: TransactionRunner,
    private val notifier: ChangeNotifier,
    private val time: TimeProvider,
) {
    fun observeConfig(subscriptionId: String): Flow<SharedConfig?> = sharedDao.observeConfig(subscriptionId)

    /** Creator first, then sort order. */
    fun observeMembers(subscriptionId: String): Flow<List<SharedMember>> = sharedDao.observeMembers(subscriptionId)

    fun observeAllConfigs(): Flow<List<SharedConfig>> = sharedDao.observeAllConfigs()

    fun observeAllMembers(): Flow<List<SharedMember>> = sharedDao.observeAllMembers()

    /** Turns sharing on with EQUAL split and a creator member named [creatorName] (the user). Idempotent. */
    suspend fun enable(subscriptionId: String, creatorName: String): DataResult<Unit> = mutate(subscriptionId) { sub ->
        if (creatorName.isBlank()) abort(InvalidReason.BLANK_NAME)
        if (sharedDao.getConfig(sub.id) != null) return@mutate
        val now = time.now()
        sharedDao.upsertConfig(SharedConfig(sub.id, SplitMode.EQUAL))
        sharedDao.upsertMember(
            SharedMember(
                subscriptionId = sub.id, name = creatorName.trim(), isCreator = true, ratioPercent = HUNDRED,
                joinedAt = time.today(), createdAt = now, updatedAt = now,
            ),
        )
        subscriptionDao.upsert(sub.copy(isShared = true, updatedAt = now))
    }

    /** Turns sharing off and deletes all members (irreversible). */
    suspend fun disable(subscriptionId: String): DataResult<Unit> = mutate(subscriptionId) { sub ->
        sharedDao.deleteMembers(sub.id)
        sharedDao.deleteConfig(sub.id)
        subscriptionDao.upsert(sub.copy(isShared = false, updatedAt = time.now()))
    }

    suspend fun updateDescription(subscriptionId: String, description: String?): DataResult<Unit> = mutate(subscriptionId) { sub ->
        val config = sharedDao.getConfig(sub.id) ?: abort(InvalidReason.NOT_SHARED)
        sharedDao.upsertConfig(config.copy(description = description?.takeIf { it.isNotBlank() }))
    }

    /** Switching to EQUAL rebalances ratios; CREATOR_PAYS needs a creator member. */
    suspend fun setSplitMode(subscriptionId: String, mode: SplitMode): DataResult<Unit> = mutate(subscriptionId) { sub ->
        val config = sharedDao.getConfig(sub.id) ?: abort(InvalidReason.NOT_SHARED)
        val members = sharedDao.getMembers(sub.id)
        if (mode == SplitMode.CREATOR_PAYS && members.none { it.isCreator }) abort(InvalidReason.CREATOR_REQUIRED)
        sharedDao.upsertConfig(config.copy(splitMode = mode))
        if (mode == SplitMode.EQUAL) rebalance(members)
    }

    /** Adds a member; ids, timestamps and order are assigned here. */
    suspend fun addMember(member: SharedMember): DataResult<SharedMember> {
        var saved: SharedMember? = null
        val result = mutate(member.subscriptionId) { sub ->
            val config = sharedDao.getConfig(sub.id) ?: abort(InvalidReason.NOT_SHARED)
            validate(member)
            val now = time.now()
            val members = sharedDao.getMembers(sub.id)
            val created = member.copy(
                isCreator = false, sortOrder = (members.maxOfOrNull { it.sortOrder } ?: 0) + 1,
                name = member.name.trim(), createdAt = now, updatedAt = now,
            )
            sharedDao.upsertMember(created)
            if (config.splitMode == SplitMode.EQUAL) rebalance(members + created)
            saved = created
        }
        return when (result) {
            is DataResult.Success -> DataResult.Success(saved!!)
            is DataResult.Failure -> result
        }
    }

    /** Updates member details; the creator flag cannot change. */
    suspend fun updateMember(member: SharedMember): DataResult<Unit> = mutate(member.subscriptionId) { sub ->
        val members = sharedDao.getMembers(sub.id)
        val current = members.firstOrNull { it.id == member.id } ?: abort(DataError.NotFound)
        validate(member)
        val updated = member.copy(isCreator = current.isCreator, name = member.name.trim(), createdAt = current.createdAt, updatedAt = time.now())
        sharedDao.upsertMember(updated)
        if (sharedDao.getConfig(sub.id)?.splitMode == SplitMode.EQUAL) rebalance(members.map { if (it.id == updated.id) updated else it })
    }

    /** The creator cannot be deleted. */
    suspend fun deleteMember(subscriptionId: String, memberId: String): DataResult<Unit> = mutate(subscriptionId) { sub ->
        val members = sharedDao.getMembers(sub.id)
        val current = members.firstOrNull { it.id == memberId } ?: abort(DataError.NotFound)
        if (current.isCreator) abort(InvalidReason.CREATOR_CANNOT_BE_DELETED)
        sharedDao.deleteMember(current)
        if (sharedDao.getConfig(sub.id)?.splitMode == SplitMode.EQUAL) rebalance(members - current)
    }

    /** The user's (creator's) share of one cycle, in the subscription currency. Equals the price when not shared. */
    suspend fun userShare(subscriptionId: String): BigDecimal? = subscriptionDao.get(subscriptionId)?.let { userShareOf(it) }

    internal suspend fun userShareOf(sub: Subscription): BigDecimal {
        if (!sub.isShared) return sub.price
        val config = sharedDao.getConfig(sub.id) ?: return sub.price
        val members = sharedDao.getMembers(sub.id)
        val creator = members.firstOrNull { it.isCreator } ?: return sub.price
        return SplitCalculator.shares(sub.price, config.splitMode, members)[creator.id] ?: sub.price
    }

    private fun validate(member: SharedMember) {
        if (member.name.isBlank()) abort(InvalidReason.BLANK_NAME)
        member.ratioPercent?.let { if (it.signum() < 0 || it > HUNDRED) abort(InvalidReason.PERCENT_OUT_OF_RANGE) }
        member.fixedAmount?.let { if (it.signum() < 0) abort(InvalidReason.NEGATIVE_AMOUNT) }
    }

    /** EQUAL mode keeps ratios in sync: active members split 100%, others get 0. */
    private suspend fun rebalance(members: List<SharedMember>) {
        val ratios = SplitCalculator.equalRatios(members)
        val now = time.now()
        sharedDao.upsertMembers(
            members.map { m ->
                val ratio = if (m.status == MemberStatus.ACTIVE) ratios[m.id] else BigDecimal.ZERO
                if (ratio == m.ratioPercent) m else m.copy(ratioPercent = ratio, updatedAt = now)
            },
        )
    }

    private suspend fun mutate(subscriptionId: String, block: suspend (Subscription) -> Unit): DataResult<Unit> {
        val result = tx.run {
            val sub = subscriptionDao.get(subscriptionId) ?: abort(DataError.NotFound)
            block(sub)
        }
        if (result.isSuccess) notifier.notifyChanged(subscriptionId)
        return result
    }

    private companion object {
        val HUNDRED = BigDecimal(100)
    }
}
