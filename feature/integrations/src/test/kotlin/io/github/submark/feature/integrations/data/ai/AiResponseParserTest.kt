package io.github.submark.feature.integrations.data.ai

import com.google.common.truth.Truth.assertThat
import io.github.submark.core.model.BillingCycle
import io.github.submark.core.model.SystemCategory
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class AiResponseParserTest {

    @Test
    fun `bare array`() {
        val results = AiRecognitionService.parse(
            """[{"name":"Netflix","price":15.49,"currency":"usd","billingCycle":"monthly","confidence":0.9}]""",
        )
        assertThat(results).hasSize(1)
        val r = results[0]
        assertThat(r.name).isEqualTo("Netflix")
        assertThat(r.price).isEqualTo(BigDecimal("15.49"))
        assertThat(r.currency).isEqualTo("USD")
        assertThat(r.billingCycle).isEqualTo(BillingCycle.MONTHLY)
        assertThat(r.confidence).isWithin(0.001).of(0.9)
    }

    @Test
    fun `fenced json is stripped`() {
        val results = AiRecognitionService.parse(
            "```json\n[{\"name\":\"Spotify\",\"price\":9.99,\"currency\":\"USD\"}]\n```",
        )
        assertThat(results.map { it.name }).containsExactly("Spotify")
    }

    @Test
    fun `object-wrapped arrays are accepted`() {
        for (key in listOf("subscriptions", "results", "items", "data")) {
            val results = AiRecognitionService.parse("{\"$key\":[{\"name\":\"X\"}]}")
            assertThat(results.map { it.name }).containsExactly("X")
        }
        // First array value as fallback.
        assertThat(
            AiRecognitionService.parse("{\"meta\":{},\"list\":[{\"name\":\"Y\"}]}").map { it.name },
        ).containsExactly("Y")
    }

    @Test
    fun `single object becomes one result`() {
        val results = AiRecognitionService.parse("""{"name":"Solo","price":1,"currency":"EUR"}""")
        assertThat(results).hasSize(1)
        assertThat(results[0].currency).isEqualTo("EUR")
    }

    @Test
    fun `prose around json is tolerated`() {
        val results = AiRecognitionService.parse(
            "Here are the subscriptions I found: [{\"name\":\"Prose\"}] Hope this helps!",
        )
        assertThat(results.map { it.name }).containsExactly("Prose")
    }

    @Test
    fun `isLifetime and isPermanent both map`() {
        assertThat(AiRecognitionService.parse("""[{"name":"A","isLifetime":true}]""")[0].isLifetime).isTrue()
        assertThat(AiRecognitionService.parse("""[{"name":"A","isPermanent":true}]""")[0].isLifetime).isTrue()
        assertThat(AiRecognitionService.parse("""[{"name":"A"}]""")[0].isLifetime).isFalse()
    }

    @Test
    fun `dates and category parsed`() {
        val r = AiRecognitionService.parse(
            """[{"name":"A","firstPaymentDate":"2026-01-15","expirationDate":"2027-01-15","category":"gaming"}]""",
        )[0]
        assertThat(r.firstPaymentDate).isEqualTo(LocalDate.of(2026, 1, 15))
        assertThat(r.expirationDate).isEqualTo(LocalDate.of(2027, 1, 15))
        assertThat(r.category).isEqualTo(SystemCategory.GAMING)
    }

    @Test
    fun `items without a name are dropped`() {
        val results = AiRecognitionService.parse("""[{"price":1},{"name":"Kept"},{"name":"  "}]""")
        assertThat(results.map { it.name }).containsExactly("Kept")
    }

    @Test
    fun `confidence clamped and price as string accepted`() {
        val r = AiRecognitionService.parse("""[{"name":"A","price":"12.50","confidence":1.7}]""")[0]
        assertThat(r.price).isEqualTo(BigDecimal("12.50"))
        assertThat(r.confidence).isEqualTo(1.0)
    }

    @Test
    fun `garbage yields empty list`() {
        assertThat(AiRecognitionService.parse("not json at all")).isEmpty()
        assertThat(AiRecognitionService.parse("42")).isEmpty()
        assertThat(AiRecognitionService.parse("{}")).isEmpty()
    }

    @Test
    fun `unknown cycle maps to null`() {
        val r = AiRecognitionService.parse("""[{"name":"A","billingCycle":"biweekly"}]""")[0]
        assertThat(r.billingCycle).isNull()
    }
}
