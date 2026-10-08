package io.github.submark.feature.backup.data

import com.google.common.truth.Truth.assertThat
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MultiStatusParserTest {

    private val root = "https://dav.example.com/remote.php/dav/files/u/SubMark/".toHttpUrl()

    private fun parse(xml: String) = MultiStatusParser.parse(xml.byteInputStream(), root, android.util.Xml.newPullParser())

    @Test
    fun `parses children with sizes and collections`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>/remote.php/dav/files/u/SubMark/</D:href>
                <D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>
              </D:response>
              <D:response>
                <D:href>/remote.php/dav/files/u/SubMark/submark-20240101-120000.bin</D:href>
                <D:propstat><D:prop><D:getcontentlength>12345</D:getcontentlength><D:resourcetype/></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>
              </D:response>
              <D:response>
                <D:href>/remote.php/dav/files/u/SubMark/submark-20240101-120000.bin.manifest.json</D:href>
                <D:propstat><D:prop><D:getcontentlength>512</D:getcontentlength><D:resourcetype/></D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()
        val entries = parse(xml)
        val self = entries.first { it.name.isEmpty() }
        assertThat(self.isCollection).isTrue()
        val payload = entries.first { it.name == "submark-20240101-120000.bin" }
        assertThat(payload.size).isEqualTo(12345L)
        assertThat(payload.isCollection).isFalse()
        val manifest = entries.first { it.name.endsWith(".manifest.json") }
        assertThat(manifest.size).isEqualTo(512L)
    }

    @Test
    fun `absolute hrefs are handled`() {
        val xml = """
            <?xml version="1.0"?>
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>https://dav.example.com/remote.php/dav/files/u/SubMark/submark-x.json</D:href>
                <D:propstat><D:prop><D:getcontentlength>10</D:getcontentlength></D:prop></D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()
        val entries = parse(xml)
        assertThat(entries.map { it.name }).containsExactly("submark-x.json")
    }

    @Test
    fun `malformed xml throws`() {
        try {
            parse("not xml at all <<<")
            throw AssertionError("expected WebDavException")
        } catch (e: WebDavException) {
            assertThat(e.error).isEqualTo(WebDavError.MalformedResponse)
        }
    }
}
