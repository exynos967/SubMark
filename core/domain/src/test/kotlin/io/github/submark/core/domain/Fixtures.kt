package io.github.submark.core.domain

import io.github.submark.core.model.MemberStatus
import io.github.submark.core.model.SharedMember
import io.github.submark.core.model.Subscription
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

internal val EPOCH: Instant = Instant.parse("2026-01-01T00:00:00Z")

internal fun d(iso: String): LocalDate = LocalDate.parse(iso)

internal fun bd(v: String): BigDecimal = BigDecimal(v)

internal fun sub(startDate: LocalDate = d("2026-01-01"), price: String = "10"): Subscription =
    Subscription(
        name = "Test",
        price = BigDecimal(price),
        currencyCode = "USD",
        startDate = startDate,
        categoryId = "cat",
        createdAt = EPOCH,
        updatedAt = EPOCH,
    )

internal fun member(
    id: String,
    creator: Boolean = false,
    status: MemberStatus = MemberStatus.ACTIVE,
    ratio: String? = null,
    fixed: String? = null,
): SharedMember = SharedMember(
    id = id,
    subscriptionId = "s",
    name = id,
    status = status,
    isCreator = creator,
    ratioPercent = ratio?.let(::BigDecimal),
    fixedAmount = fixed?.let(::BigDecimal),
    joinedAt = d("2026-01-01"),
    createdAt = EPOCH,
    updatedAt = EPOCH,
)
