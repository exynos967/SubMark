package io.github.submark.core.data.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Single source of "now" so billing logic is testable. Never call `LocalDate.now()` directly. */
interface TimeProvider {
    fun today(): LocalDate
    fun now(): Instant
    fun zone(): ZoneId
}

@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun zone(): ZoneId = ZoneId.systemDefault()
    override fun now(): Instant = Instant.now()
    override fun today(): LocalDate = LocalDate.now(zone())
}
