package io.github.submark.feature.backup.data

import com.google.common.truth.Truth.assertThat
import io.github.submark.core.model.MemberStatus
import io.github.submark.core.model.SharedMember
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MemberCleanupTest {

    private fun member(id: String, subId: String, name: String, creator: Boolean = false, created: Long = 0) = SharedMember(
        id = id, subscriptionId = subId, name = name, isCreator = creator,
        status = MemberStatus.ACTIVE, joinedAt = LocalDate.EPOCH,
        createdAt = Instant.EPOCH.plusSeconds(created), updatedAt = Instant.EPOCH,
    )

    @Test
    fun `orphans are members without a subscription`() {
        val members = listOf(
            member("m1", "s1", "A"),
            member("m2", "gone", "B"),
            member("m3", "gone", "C"),
        )
        val result = MemberCleanup.orphans(members, setOf("s1"))
        assertThat(result.map { it.id }).containsExactly("m2", "m3")
    }

    @Test
    fun `duplicates keep the earliest member of each group`() {
        val members = listOf(
            member("m1", "s1", "Alice", created = 100),
            member("m2", "s1", "alice", created = 50), // earlier → kept (normalized name match)
            member("m3", "s1", "Bob", created = 10),
            member("m4", "s2", "Alice", created = 1), // different subscription
        )
        val result = MemberCleanup.duplicates(members)
        assertThat(result.victims.map { it.id }).containsExactly("m1")
        assertThat(result.groupCount).isEqualTo(1)
    }

    @Test
    fun `duplicates never mark the creator`() {
        val members = listOf(
            member("m1", "s1", "Alice", creator = true, created = 100),
            member("m2", "s1", "Alice", created = 1),
        )
        val result = MemberCleanup.duplicates(members)
        assertThat(result.victims.map { it.id }).containsExactly("m2")
    }

    @Test
    fun `no duplicates yields empty`() {
        val members = listOf(member("m1", "s1", "A"), member("m2", "s1", "B"))
        val result = MemberCleanup.duplicates(members)
        assertThat(result.victims).isEmpty()
        assertThat(result.groupCount).isEqualTo(0)
    }
}
