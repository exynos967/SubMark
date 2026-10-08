package io.github.submark.feature.backup.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

class PrunePolicyTest {

    private val t0 = Instant.parse("2024-06-01T00:00:00Z")
    private fun at(daysAfter: Int) = t0.plusSeconds(daysAfter.toLong() * 24 * 3600)
    private fun backup(name: String, daysAfter: Int) = name to at(daysAfter)

    @Test
    fun `single backup is never deleted`() {
        val result = PrunePolicy.selectForDeletion(listOf(backup("a", 400)), keepCount = 1, keepDays = 1, now = at(500))
        assertThat(result).isEmpty()
    }

    @Test
    fun `newest is never deleted even when too old`() {
        val list = listOf(backup("a", 0), backup("b", 10))
        val result = PrunePolicy.selectForDeletion(list, keepCount = 10, keepDays = 1, now = at(500))
        assertThat(result).containsExactly("a")
    }

    @Test
    fun `keeps keepCount newest`() {
        val list = listOf(backup("d4", 4), backup("d3", 3), backup("d2", 2), backup("d1", 1), backup("d0", 0))
        // keepCount=2 → keep 2 newest overall.
        val result = PrunePolicy.selectForDeletion(list, keepCount = 2, keepDays = 0, now = at(10))
        assertThat(result).containsExactly("d2", "d1", "d0")
    }

    @Test
    fun `deletes older than keepDays`() {
        val list = listOf(backup("new", 9), backup("old", 1))
        val result = PrunePolicy.selectForDeletion(list, keepCount = 0, keepDays = 5, now = at(10))
        assertThat(result).containsExactly("old")
    }

    @Test
    fun `zero limits keep everything except nothing`() {
        val list = listOf(backup("a", 0), backup("b", 1), backup("c", 2))
        val result = PrunePolicy.selectForDeletion(list, keepCount = 0, keepDays = 0, now = at(100))
        assertThat(result).isEmpty()
    }

    @Test
    fun `either condition triggers deletion`() {
        // "tooOld" is beyond keepCount AND older than keepDays; "oldButRanked" is within both limits.
        val list = listOf(
            backup("newest", 100),
            backup("withinDays", 99),
            backup("oldButRanked", 50),
            backup("tooOld", 1),
        )
        val result = PrunePolicy.selectForDeletion(list, keepCount = 3, keepDays = 60, now = at(100))
        assertThat(result).containsExactly("tooOld")
    }

    @Test
    fun `beyond keepCount alone deletes`() {
        val list = listOf(
            backup("newest", 100),
            backup("rank2", 99),
            backup("rank3", 98),
        )
        val result = PrunePolicy.selectForDeletion(list, keepCount = 2, keepDays = 365, now = at(100))
        assertThat(result).containsExactly("rank3")
    }
}
