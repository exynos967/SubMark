package io.github.submark.core.data

import com.google.common.truth.Truth.assertThat
import io.github.submark.core.data.service.SubscriptionDraft
import io.github.submark.core.model.ExportBundle
import io.github.submark.core.model.PaymentKind
import io.github.submark.core.model.RestoreMode
import io.github.submark.core.model.SharedMember
import io.github.submark.core.model.SubscriptionKind
import io.github.submark.core.model.WalletKind
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class ExportServiceTest {
    private lateinit var h: TestHarness

    @Before fun setUp() = runTest {
        h = TestHarness(LocalDate.of(2026, 3, 15))
        h.seeder.seed()
    }

    @After fun tearDown() = h.close()

    private suspend fun populate(): String {
        val tag = h.tags.create("Streaming").orFail()
        val wallet = h.wallets.create("Card", WalletKind.BALANCE_TRACKED, "USD", BigDecimal("100")).orFail()
        val id = h.create(
            SubscriptionDraft(h.subscription(start = LocalDate.of(2026, 1, 10), walletId = wallet.id), tagIds = listOf(tag.id), generateHistory = true),
        )
        h.subscriptions.markPaid(id, newCycle = false).orFail()
        h.shared.enable(id, "Me").orFail()
        h.shared.addMember(SharedMember(subscriptionId = id, name = "Alex", joinedAt = h.time.today, createdAt = h.time.now(), updatedAt = h.time.now())).orFail()
        return id
    }

    private fun ExportBundle.normalized() = copy(createdAt = java.time.Instant.EPOCH)

    @Test fun exactRoundTrip_restoresIdenticalData() = runTest {
        val id = populate()
        val original = h.export.export()
        val decoded = h.export.decode(h.export.encode(original)).orFail()
        assertThat(decoded).isEqualTo(original)

        // Diverge: delete a subscription, add another one.
        h.subscriptions.delete(id).orFail()
        h.create(SubscriptionDraft(h.subscription(name = "Extra")))
        h.listener.calls.clear()

        val result = h.export.import(decoded, RestoreMode.EXACT).orFail()

        assertThat(result.counts["subscriptions"]).isEqualTo(original.subscriptions.size)
        assertThat(result.counts["paymentRecords"]).isEqualTo(original.paymentRecords.size)
        val restored = h.export.export()
        fun <T> sameRows(a: List<T>, b: List<T>) = assertThat(a).containsExactlyElementsIn(b)
        sameRows(restored.subscriptions, original.subscriptions)
        sameRows(restored.paymentRecords, original.paymentRecords)
        sameRows(restored.walletTransactions, original.walletTransactions)
        sameRows(restored.wallets, original.wallets)
        sameRows(restored.sharedMembers, original.sharedMembers)
        sameRows(restored.subscriptionTags, original.subscriptionTags)
        sameRows(restored.categories, original.categories)
        sameRows(restored.currencies, original.currencies)
        assertThat(h.listener.calls).containsExactly(null)
    }

    @Test fun merge_insertsOnlyMissingIds() = runTest {
        populate()
        val bundle = h.export.export()
        val merged = h.export.import(bundle, RestoreMode.MERGE).orFail()
        assertThat(merged.isEmpty).isTrue()

        val replaced = h.export.import(bundle, RestoreMode.REPLACE_MATCHING).orFail()
        assertThat(replaced.counts["subscriptions"]).isEqualTo(bundle.subscriptions.size)
    }

    @Test fun decode_rejectsNewerVersion() {
        val text = """{"exportVersion":99,"appVersion":"x","createdAt":"2026-01-01T00:00:00Z"}"""
        assertThat(h.export.decode(text).isSuccess).isFalse()
    }

    /** Backups from before the stored-value kind was removed still restore, converted like the database migration. */
    @Test fun decode_upgradesStoredValueFromOlderVersions() = runTest {
        populate()
        val root = Json.parseToJsonElement(h.export.encode(h.export.export())).jsonObject
        fun JsonElement.withKind(kind: String, vararg extra: Pair<String, JsonElement>) =
            JsonArray(jsonArray.map { JsonObject(it.jsonObject + ("kind" to JsonPrimitive(kind)) + extra) })
        val legacy = JsonObject(
            root + mapOf(
                "subscriptions" to root.getValue("subscriptions").withKind("STORED_VALUE", "storedValueBalance" to JsonPrimitive("30")),
                "paymentRecords" to root.getValue("paymentRecords").withKind("STORED_VALUE_DEPOSIT"),
                "storedValueRecords" to JsonArray(listOf(JsonObject(mapOf("id" to JsonPrimitive("r"))))),
            ),
        )

        val bundle = h.export.decode(legacy.toString()).orFail()

        assertThat(bundle.subscriptions).isNotEmpty()
        assertThat(bundle.subscriptions.map { it.kind }.toSet()).containsExactly(SubscriptionKind.REGULAR)
        assertThat(bundle.paymentRecords).isNotEmpty()
        assertThat(bundle.paymentRecords.map { it.kind }.toSet()).containsExactly(PaymentKind.IN_APP_PURCHASE)
    }
}
