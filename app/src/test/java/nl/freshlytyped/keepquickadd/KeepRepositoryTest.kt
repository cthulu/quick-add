package nl.freshlytyped.keepquickadd

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
    fun `addItem fails when publishKey not configured`() = runBlocking {
        settings.publishKey = ""
        val baseUrl = mockServer.url("/").toString().trimEnd('/')
        val result = withMockedBaseUrl(baseUrl) { repository.addItem("Inbox", "Test") }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("PubNub keys") == true)
    }

    @Test
    fun `addItem returns failure with friendly message on 400`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(400))
        val baseUrl = mockServer.url("/").toString().trimEnd('/')
        val result = withMockedBaseUrl(baseUrl) { repository.addItem("Inbox", "Test") }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Bad request") == true)
    }

    @Test
    fun `addItem returns failure with auth message on 403`() = runBlocking {
        mockServer.enqueue(MockResponse().setResponseCode(403))
        val baseUrl = mockServer.url("/").toString().trimEnd('/')
        val result = withMockedBaseUrl(baseUrl) { repository.addItem("Inbox", "Test") }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Authentication") == true)
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
