package nl.freshlytyped.keepquickadd

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.SocketTimeoutException
import java.net.UnknownHostException

@RunWith(RobolectricTestRunner::class)
class KeepRepositoryTest {

    private lateinit var context: Context
    private lateinit var mockServer: MockWebServer
    private lateinit var settings: AppSettings
    private lateinit var repository: KeepRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()

        mockServer = MockWebServer()
        mockServer.start()

        settings = AppSettings(context)
        settings.publishKey = "pub-c-test"
        settings.subscribeKey = "sub-c-test"
        settings.channel = "test-channel"

        repository = KeepRepository(context)
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    // --- testConnection ---

    @Test
    fun `testConnection succeeds against mock PubNub returning 200`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("[1,\"Sent\",\"123\"]"))
        // Override base URL by hitting mockServer directly via testConnection
        val baseUrl = mockServer.url("/").toString().trimEnd('/')
        val result = withMockedBaseUrl(baseUrl) {
            repository.testConnection("pub-c-test", "sub-c-test", "", "test-channel")
        }
        assertTrue(result.isSuccess)
    }

    @Test
    fun `testConnection returns failure on 403`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(403).setBody("[0,\"Forbidden\",\"0\"]"))
        val baseUrl = mockServer.url("/").toString().trimEnd('/')
        val result = withMockedBaseUrl(baseUrl) {
            repository.testConnection("pub-c-test", "sub-c-test", "bad-key", "test-channel")
        }
        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
        assertEquals(
            KeepFailureMapper.messageFor(KeepFailureCategory.CONFIGURATION),
            result.exceptionOrNull()?.message
        )
    }

    @Test
    fun `failure mapper categorizes network and server failures with safe messages`() {
        val cases = listOf(
            KeepFailureMapper.categoryFor(UnknownHostException("private.example.test")) to KeepFailureCategory.OFFLINE,
            KeepFailureMapper.categoryFor(SocketTimeoutException("token=secret")) to KeepFailureCategory.TIMEOUT,
            KeepFailureMapper.categoryFor(400) to KeepFailureCategory.CONFIGURATION,
            KeepFailureMapper.categoryFor(503) to KeepFailureCategory.SERVER
        )

        cases.forEach { (actual, expected) -> assertEquals(expected, actual) }

        val messages = cases.map { (category, _) -> KeepFailureMapper.messageFor(category) }
        messages.forEach { message ->
            assertFalse(message.contains("private.example.test"))
            assertFalse(message.contains("token=secret"))
            assertFalse(message.contains("UnknownHostException"))
            assertFalse(message.contains("503"))
        }
    }

    // --- addItem ---

    @Test
    fun `addItem sends POST with correct PubNub publish URL and message body`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("[1,\"Sent\",\"123\"]"))
        val baseUrl = mockServer.url("/").toString().trimEnd('/')

        val result = withMockedBaseUrl(baseUrl) {
            repository.addItem("Groceries", "Milk")
        }
        assertTrue("addItem should succeed but got: ${result.exceptionOrNull()?.message}", result.isSuccess)

        val req = mockServer.takeRequest()
        assertEquals("POST", req.method)
        // URL contains /publish/pub-c-test/sub-c-test/0/test-channel/0
        assertTrue("Path was: ${req.path}",
            req.path?.startsWith("/publish/pub-c-test/sub-c-test/0/test-channel/0") == true)
        // Body contains the formatted message
        val body = req.body.readUtf8()
        assertTrue("Body was: $body", body.contains("add Milk to the list Groceries"))
    }

    @Test
    fun `addItem encodes reserved characters in PubNub URL path segments`() = runBlocking {
        settings.publishKey = "pub/key?"
        settings.subscribeKey = "sub#key%"
        settings.channel = "channel/name?"
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("[1,\"Sent\",\"123\"]"))
        val baseUrl = mockServer.url("/").toString().trimEnd('/')

        val result = withMockedBaseUrl(baseUrl) { repository.addItem("Inbox", "Test") }
        assertTrue("addItem should succeed but got: ${result.exceptionOrNull()?.message}", result.isSuccess)

        val req = mockServer.takeRequest()
        assertEquals(
            "/publish/pub%2Fkey%3F/sub%23key%25/0/channel%2Fname%3F/0",
            req.path
        )
    }

    @Test
    fun `addItem fails when publishKey not configured`() = runBlocking {
        settings.publishKey = ""
        val baseUrl = mockServer.url("/").toString().trimEnd('/')
        val result = withMockedBaseUrl(baseUrl) { repository.addItem("Inbox", "Test") }
        assertTrue(result.isFailure)
        assertEquals(
            KeepFailureMapper.messageFor(KeepFailureCategory.CONFIGURATION),
            result.exceptionOrNull()?.message
        )
    }

    @Test
    fun `addItem returns failure with friendly message on 400`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(400))
        val baseUrl = mockServer.url("/").toString().trimEnd('/')
        val result = withMockedBaseUrl(baseUrl) { repository.addItem("Inbox", "Test") }
        assertTrue(result.isFailure)
        assertEquals(
            KeepFailureMapper.messageFor(KeepFailureCategory.CONFIGURATION),
            result.exceptionOrNull()?.message
        )
    }

    @Test
    fun `addItem returns failure with auth message on 403`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(403))
        val baseUrl = mockServer.url("/").toString().trimEnd('/')
        val result = withMockedBaseUrl(baseUrl) { repository.addItem("Inbox", "Test") }
        assertTrue(result.isFailure)
        assertEquals(
            KeepFailureMapper.messageFor(KeepFailureCategory.CONFIGURATION),
            result.exceptionOrNull()?.message
        )
    }

    @Test
    fun `addItem returns safe server message on 500`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(500).setBody("secret host and credentials"))
        val baseUrl = mockServer.url("/").toString().trimEnd('/')
        val result = withMockedBaseUrl(baseUrl) { repository.addItem("Inbox", "Test") }

        assertTrue(result.isFailure)
        assertEquals(
            KeepFailureMapper.messageFor(KeepFailureCategory.SERVER),
            result.exceptionOrNull()?.message
        )
        assertFalse(result.exceptionOrNull()?.message?.contains("500") == true)
    }

    @Test
    fun `addItem sends apiKey as x-api-key header when set`() = runBlocking {
        settings.apiKey = "my-auth-token"
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("[1,\"Sent\",\"123\"]"))
        val baseUrl = mockServer.url("/").toString().trimEnd('/')

        val result = withMockedBaseUrl(baseUrl) { repository.addItem("Inbox", "Test") }
        assertTrue(result.isSuccess)
        val req = mockServer.takeRequest()
        assertEquals("my-auth-token", req.getHeader("x-api-key"))
        // No auth in query string
        assertTrue("Path should NOT contain auth query param: ${req.path}",
            req.path?.contains("auth=") != true)
    }

    /**
     * Helper that reflectively swaps AppSettings.PUBNUB_BASE_URL with the mock
     * server URL for the duration of the supplied block. This avoids polluting
     * production code with test-only seams.
     */
    private suspend inline fun <T> withMockedBaseUrl(url: String, block: () -> T): T {
        // @JvmField on a Companion val places the field on the outer class.
        val field = AppSettings::class.java.getDeclaredField("PUBNUB_BASE_URL")
        field.isAccessible = true
        val original = field.get(null) as String
        return try {
            field.set(null, url)
            block()
        } finally {
            field.set(null, original)
        }
    }
}
