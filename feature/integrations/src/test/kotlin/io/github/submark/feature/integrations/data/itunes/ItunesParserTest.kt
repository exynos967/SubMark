package io.github.submark.feature.integrations.data.itunes

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.math.BigDecimal

class ItunesParserTest {

    @Test
    fun `lookup returns complete price`() {
        val body = """{"resultCount":1,"results":[{"trackId":123,"trackName":"App","price":4.99,
            |"formattedPrice":"$4.99","currency":"USD"}]}""".trimMargin()
        val result = ItunesParser.toAppPrice(body, "us")
        assertThat(result.isSuccess).isTrue()
        val price = result.getOrThrow()
        assertThat(price.region).isEqualTo("US")
        assertThat(price.price).isEqualTo(BigDecimal.valueOf(4.99))
        assertThat(price.currency).isEqualTo("USD")
        assertThat(price.formattedPrice).isEqualTo("$4.99")
    }

    @Test
    fun `free app returns zero price`() {
        val body = """{"resultCount":1,"results":[{"trackId":1,"trackName":"Free","price":0.0,
            |"formattedPrice":"Free","currency":"USD"}]}""".trimMargin()
        val result = ItunesParser.toAppPrice(body, "cn")
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().price).isEqualTo(BigDecimal.valueOf(0.0))
    }

    @Test
    fun `missing price is incomplete - no record may be written`() {
        val body = """{"resultCount":1,"results":[{"trackId":1,"trackName":"X","currency":"USD"}]}"""
        val result = ItunesParser.toAppPrice(body, "us")
        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as ItunesException).error).isEqualTo(ItunesError.IncompletePrice)
    }

    @Test
    fun `missing currency is incomplete`() {
        val body = """{"resultCount":1,"results":[{"trackId":1,"trackName":"X","price":9.99}]}"""
        assertThat(
            (ItunesParser.toAppPrice(body, "us").exceptionOrNull() as ItunesException).error,
        ).isEqualTo(ItunesError.IncompletePrice)
    }

    @Test
    fun `no items in storefront`() {
        val body = """{"resultCount":0,"results":[]}"""
        assertThat(
            (ItunesParser.toAppPrice(body, "jp").exceptionOrNull() as ItunesException).error,
        ).isEqualTo(ItunesError.NoItems)
    }

    @Test
    fun `invalid json is a parse error`() {
        assertThat(
            (ItunesParser.toAppPrice("garbage", "us").exceptionOrNull() as ItunesException).error,
        ).isEqualTo(ItunesError.Parse)
    }

    @Test
    fun `search results tolerate unknown fields`() {
        val results = ItunesParser.parseResults(
            """{"resultCount":1,"results":[{"trackId":1,"trackName":"App","artworkUrl512":"https://a/b.png",
               "sellerName":"Dev","newField":{"x":1}}]}""",
        )
        assertThat(results).hasSize(1)
        assertThat(results!![0].artworkUrl512).isEqualTo("https://a/b.png")
        assertThat(results[0].sellerName).isEqualTo("Dev")
    }

    @Test
    fun `app id mismatch rows without trackId are skipped`() {
        val body = """{"resultCount":2,"results":[{"wrapperType":"track"},{"trackId":5,"trackName":"Y","price":1.0,"currency":"USD"}]}"""
        val price = ItunesParser.toAppPrice(body, "us").getOrThrow()
        assertThat(price.trackName).isEqualTo("Y")
    }
}
