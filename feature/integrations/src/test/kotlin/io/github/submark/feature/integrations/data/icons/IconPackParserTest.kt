package io.github.submark.feature.integrations.data.icons

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class IconPackParserTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(raw: String): IconPack? = try {
        json.decodeFromString<IconPack>(raw)
    } catch (e: Exception) {
        null
    }

    @Test
    fun `parses documented format`() {
        val pack = parse(
            """{"name":"Brand Pack","icons":[
                |{"name":"Netflix","url":"https://cdn.x/netflix.png"},
                |{"name":"Spotify","url":"https://cdn.x/spotify.png"}]}""".trimMargin(),
        )
        assertThat(pack).isNotNull()
        assertThat(pack!!.name).isEqualTo("Brand Pack")
        assertThat(pack.icons).hasSize(2)
        assertThat(pack.icons[0].url).isEqualTo("https://cdn.x/netflix.png")
    }

    @Test
    fun `tolerates unknown keys and missing name`() {
        val pack = parse("""{"extra":1,"icons":[{"name":"A","url":"https://x/a.png","foo":"bar"}]}""")
        assertThat(pack).isNotNull()
        assertThat(pack!!.icons).hasSize(1)
    }

    @Test
    fun `rejects invalid json`() {
        assertThat(parse("not json")).isNull()
        assertThat(parse("{\"icons\": 5}")).isNull()
    }

    @Test
    fun `domain normalization`() {
        val svc = WebsiteIconService(NoOpClient.client)
        assertThat(svc.normalizeDomain("https://www.Apple.com/store?x=1")).isEqualTo("www.apple.com")
        assertThat(svc.normalizeDomain("apple.com")).isEqualTo("apple.com")
        assertThat(svc.normalizeDomain("not a domain")).isNull()
        assertThat(svc.normalizeDomain("noDots")).isNull()
        assertThat(svc.normalizeDomain("  ")).isNull()
    }

    @Test
    fun `candidate order ddg then google then site`() {
        val svc = WebsiteIconService(NoOpClient.client)
        val candidates = svc.candidates("apple.com")
        assertThat(candidates[0]).isEqualTo("https://icons.duckduckgo.com/ip3/apple.com.ico")
        assertThat(candidates[1]).isEqualTo("https://www.google.com/s2/favicons?domain=apple.com&sz=128")
        assertThat(candidates[2]).isEqualTo("https://apple.com/favicon.ico")
    }
}

/** Tests never touch the network; resolve() is not exercised here. */
private object NoOpClient {
    val client = okhttp3.OkHttpClient.Builder().build()
}
