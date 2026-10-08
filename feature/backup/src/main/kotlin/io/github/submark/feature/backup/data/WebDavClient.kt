package io.github.submark.feature.backup.data

import android.util.Xml
import io.github.submark.core.model.BackupProfile
import io.github.submark.core.model.SecretKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import javax.inject.Inject
import javax.inject.Singleton

/** Expected failures of a WebDAV operation; the UI maps each case to a localized message. */
sealed interface WebDavError {
    data object InvalidUrl : WebDavError
    data object PlainHttpNotAllowed : WebDavError
    data object HostNotLocal : WebDavError
    data class Http(val code: Int, val message: String) : WebDavError
    /** PROPFIND body was not a parseable multistatus document. */
    data object MalformedResponse : WebDavError
    data class Io(val message: String?) : WebDavError
    /** Operation interrupted (cancel). */
    data object Canceled : WebDavError
    /** Authentication is configured incorrectly (no password found). */
    data object MissingCredentials : WebDavError
}

class WebDavException(val error: WebDavError) : Exception("WebDAV error: $error")

/** One entry of a PROPFIND (Depth-1) listing. */
data class DavEntry(
    /** Path relative to the requested collection, e.g. `submark-20240101.zip`; "" for the collection itself. */
    val name: String,
    val size: Long?,
    val isCollection: Boolean,
)

/**
 * Minimal WebDAV client over OkHttp: PROPFIND (Depth 0/1), MKCOL (via [ensureCollections], recursive),
 * PUT, GET, DELETE with Basic auth. Host rule: HTTPS everywhere, plain HTTP only for local hosts when
 * the profile allows it — see [HostRules].
 */
@Singleton
class WebDavClient @Inject constructor(private val http: OkHttpClient) {

    private val xmlMediaType = "application/xml; charset=utf-8".toMediaType()

    /** Root URL of the profile backup directory (`serverUrl` + `/remotePath/`), or an error. */
    fun rootUrl(profile: BackupProfile): Pair<HttpUrl?, WebDavError?> {
        val base = profile.serverUrl.trim().toHttpUrlOrNull() ?: return null to WebDavError.InvalidUrl
        return when (val v = HostRules.check(base, profile.allowHttpLocal)) {
            HostRules.Verdict.Ok -> {
                val b = base.newBuilder()
                // Drop the trailing empty segment ("" ) that a "/"-terminated path produces so
                // remotePath appends cleanly, then re-add one at the end.
                if (base.pathSegments.lastOrNull() == "") b.removePathSegment(base.pathSize - 1)
                profile.remotePath.trim().trim('/').split('/').filter { it.isNotBlank() }.forEach { seg ->
                    b.addPathSegment(seg)
                }
                b.addPathSegment("")
                b.build() to null
            }
            HostRules.Verdict.InvalidUrl -> null to WebDavError.InvalidUrl
            HostRules.Verdict.PlainHttpNotAllowed -> null to WebDavError.PlainHttpNotAllowed
            HostRules.Verdict.HostNotLocal -> null to WebDavError.HostNotLocal
        }
    }

    private fun auth(profile: BackupProfile, password: String?): String =
        Credentials.basic(profile.username, password ?: "")

    private fun request(profile: BackupProfile, password: String?): Request.Builder =
        Request.Builder().header("Authorization", auth(profile, password))

    /** Executes and maps expected errors; throws [WebDavException]. */
    private fun <T> execute(profile: BackupProfile, password: String?, block: (Request.Builder) -> Request, use: (okhttp3.Response) -> T): T {
        val req = try {
            block(request(profile, password))
        } catch (e: IllegalArgumentException) {
            throw WebDavException(WebDavError.InvalidUrl)
        }
        return try {
            http.newCall(req).execute().use { resp ->
                if (resp.code !in 200..299) {
                    throw WebDavException(WebDavError.Http(resp.code, resp.message))
                }
                use(resp)
            }
        } catch (e: WebDavException) {
            throw e
        } catch (e: InterruptedIOException) {
            Thread.currentThread().interrupt()
            throw WebDavException(WebDavError.Canceled)
        } catch (e: IOException) {
            throw WebDavException(WebDavError.Io(e.message))
        }
    }

    /** PROPFIND Depth 0: exists + size of a single resource. Returns null size for collections/props missing. */
    suspend fun propFind(profile: BackupProfile, password: String?, url: HttpUrl, depth: Int = 0): Pair<Boolean, Long?> =
        withContext(Dispatchers.IO) {
            execute(profile, password, { b ->
                b.url(url).method("PROPFIND", PROPFIND_BODY.toRequestBody(xmlMediaType)).header("Depth", depth.toString()).build()
            }) { resp ->
                val body = resp.body ?: throw WebDavException(WebDavError.MalformedResponse)
                val entries = parseMultiStatus(body.byteStream(), url)
                val self = entries.firstOrNull { it.name.isEmpty() }
                (self != null) to (self?.size)
            }
        }

    /** PROPFIND Depth 1: direct children of a collection. */
    suspend fun list(profile: BackupProfile, password: String?, collection: HttpUrl): List<DavEntry> =
        withContext(Dispatchers.IO) {
            execute(profile, password, { b ->
                b.url(collection).method("PROPFIND", PROPFIND_BODY.toRequestBody(xmlMediaType)).header("Depth", "1").build()
            }) { resp ->
                val body = resp.body ?: throw WebDavException(WebDavError.MalformedResponse)
                parseMultiStatus(body.byteStream(), collection).filter { it.name.isNotEmpty() }
            }
        }

    /** MKCOL for every missing segment of [profile.remotePath]. */
    suspend fun ensureCollections(profile: BackupProfile, password: String?): Unit = withContext(Dispatchers.IO) {
        val base = profile.serverUrl.trim().toHttpUrlOrNull() ?: throw WebDavException(WebDavError.InvalidUrl)
        var current = base
        val segments = profile.remotePath.trim().trim('/').split('/').filter { it.isNotBlank() }
        for (seg in segments) {
            current = current.newBuilder().addPathSegment(seg).build()
            val exists = try {
                val (ok, _) = propFind(profile, password, current)
                ok
            } catch (e: WebDavException) {
                if (e.error is WebDavError.Http && (e.error.code == 404 || e.error.code == 409)) false else throw e
            }
            if (!exists) {
                execute(profile, password, { b -> b.url(current).method("MKCOL", null).build() }) { }
            }
        }
    }

    /** PUT a local file. */
    suspend fun put(profile: BackupProfile, password: String?, url: HttpUrl, file: File, contentType: String = "application/octet-stream") =
        withContext(Dispatchers.IO) {
            execute(profile, password, { b ->
                b.url(url).put(file.asRequestBody(contentType.toMediaType())).build()
            }) { }
        }

    /** PUT text (used for the manifest). */
    suspend fun putText(profile: BackupProfile, password: String?, url: HttpUrl, text: String) =
        withContext(Dispatchers.IO) {
            execute(profile, password, { b ->
                b.url(url).put(text.toRequestBody("application/json; charset=utf-8".toMediaType())).build()
            }) { }
        }

    /** GET into a local file; caller deletes on failure. */
    suspend fun get(profile: BackupProfile, password: String?, url: HttpUrl, target: File): Unit =
        withContext(Dispatchers.IO) {
            execute(profile, password, { b -> b.url(url).get().build() }) { resp ->
                val body = resp.body ?: throw WebDavException(WebDavError.MalformedResponse)
                body.byteStream().use { input -> target.outputStream().use { input.copyTo(it) } }
            }
        }

    suspend fun getText(profile: BackupProfile, password: String?, url: HttpUrl): String = withContext(Dispatchers.IO) {
        execute(profile, password, { b -> b.url(url).get().build() }) { resp ->
            val body = resp.body ?: throw WebDavException(WebDavError.MalformedResponse)
            body.string()
        }
    }

    /** DELETE; 404 is treated as already-gone. */
    suspend fun delete(profile: BackupProfile, password: String?, url: HttpUrl): Unit = withContext(Dispatchers.IO) {
        try {
            execute(profile, password, { b -> b.url(url).delete().build() }) { }
        } catch (e: WebDavException) {
            if (e.error is WebDavError.Http && e.error.code == 404) return@withContext else throw e
        }
    }

    /** One step of the connection test. */
    enum class TestStep { READ, CREATE_DIRECTORY, WRITE_PROBE, VERIFY_PROBE, DELETE_PROBE }

    data class TestStepResult(val step: TestStep, val ok: Boolean, val error: WebDavError? = null)

    /**
     * Connection test: PROPFIND (read), MKCOL if missing, PUT a probe, GET the probe back, DELETE it.
     * Stops on the first failing step and reports every step attempted.
     */
    suspend fun testConnection(profile: BackupProfile, password: String?): List<TestStepResult> {
        val results = mutableListOf<TestStepResult>()
        val (root, rootErr) = rootUrl(profile)
        if (root == null) {
            results += TestStepResult(TestStep.READ, false, rootErr)
            return results
        }
        suspend fun step(kind: TestStep, block: suspend () -> Unit): Boolean = try {
            block()
            results += TestStepResult(kind, true)
            true
        } catch (e: WebDavException) {
            results += TestStepResult(kind, false, e.error)
            false
        }
        if (!step(TestStep.READ) { propFind(profile, password, root) }) return results
        if (!step(TestStep.CREATE_DIRECTORY) { ensureCollections(profile, password) }) return results
        val probe = root.newBuilder().addPathSegment(".submark-probe-${System.currentTimeMillis()}").build()
        val probeFile = File.createTempFile("submark-probe", ".tmp").apply { writeText("submark-probe") }
        try {
            if (!step(TestStep.WRITE_PROBE) { put(profile, password, probe, probeFile, "text/plain") }) return results
            val probeBack = File.createTempFile("submark-probe-dl", ".tmp")
            try {
                if (!step(TestStep.VERIFY_PROBE) { get(profile, password, probe, probeBack) }) return results
            } finally {
                probeBack.delete()
            }
            step(TestStep.DELETE_PROBE) { delete(profile, password, probe) }
        } finally {
            probeFile.delete()
        }
        return results
    }
}

/** Parses a WebDAV Depth-1 multistatus body; the collection itself appears as an entry with an empty [DavEntry.name]. */
internal fun parseMultiStatus(stream: InputStream, collectionUrl: HttpUrl, parser: XmlPullParser = Xml.newPullParser()): List<DavEntry> =
    MultiStatusParser.parse(stream, collectionUrl, parser)

private val PROPFIND_BODY =
    """<?xml version="1.0" encoding="utf-8" ?>""" +
        """<D:propfind xmlns:D="DAV:"><D:prop><D:displayname/><D:resourcetype/><D:getcontentlength/></D:prop></D:propfind>"""

/** Small XML reader for DAV multistatus responses; namespace-tolerant (matches local names). */
object MultiStatusParser {

    fun parse(stream: InputStream, collectionUrl: HttpUrl, parser: XmlPullParser): List<DavEntry> {
        val results = mutableListOf<RawResponse>()
        var current: RawResponse? = null
        var tag: String? = null
        try {
            parser.setInput(stream, null)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (local(parser.name)) {
                        "response" -> { current = RawResponse() }
                        "href", "getcontentlength", "displayname" -> tag = local(parser.name)
                        "collection" -> current?.isCollection = true
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim().orEmpty()
                        val c = current
                        if (c != null && text.isNotEmpty()) {
                            when (tag) {
                                "href" -> c.href += text
                                "getcontentlength" -> if (c.size == null) c.size = text.toLongOrNull()
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> when (local(parser.name)) {
                        "response" -> { current?.let(results::add); current = null }
                        "href", "getcontentlength", "displayname" -> tag = null
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            throw WebDavException(WebDavError.MalformedResponse)
        }

        return results.mapNotNull { it.toEntry(collectionUrl) }
    }

    private fun local(name: String?): String = name?.substringAfterLast(':')?.lowercase() ?: ""

    private class RawResponse {
        var href: String = ""
        var size: Long? = null
        var isCollection: Boolean = false

        fun toEntry(collectionUrl: HttpUrl): DavEntry? {
            if (href.isEmpty()) return null
            // href may be absolute (scheme+host) or root-relative. Percent-decoded path segments
            // relative to the collection path give the entry's name.
            val decoded = try {
                java.net.URLDecoder.decode(href, Charsets.UTF_8.name())
            } catch (e: Exception) { href }
            val path = decoded
                .removePrefix(collectionUrl.scheme + "://" + collectionUrl.host)
                .let { p -> if (':' in p.take(8)) p.substringAfter('/', "") else p } // strip :port when present
                .ifEmpty { decoded }
            val collectionPath = collectionUrl.encodedPath.let { java.net.URLDecoder.decode(it, Charsets.UTF_8.name()) }
            val rel = path.removePrefix(collectionPath).removePrefix(collectionPath.removeSuffix("/"))
            val name = rel.trim('/')
            return DavEntry(name = name, size = size, isCollection = isCollection)
        }
    }
}
