package com.keepquickadd

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeepRepositoryTest {

    private lateinit var context: Context
    private lateinit var mockServer: MockWebServer
    private lateinit var repository: KeepRepository
    private lateinit var settings: AppSettings

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // Clear prefs between tests
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("keep_cache", Context.MODE_PRIVATE)
            .edit().clear().commit()

        mockServer = MockWebServer()
        mockServer.start()

        settings = AppSettings(context)
        settings.backendUrl = mockServer.url("").toString().trimEnd('/')

        repository = KeepRepository(context)
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    // --- getLists ---

    @Test
    fun `getLists returns parsed list from server`() = runTest {
        mockServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""[{"id":"list-001","title":"Groceries"},{"id":"list-002","title":"Todo"}]""")
        )

        val result = repository.getLists(forceRefresh = true)

        assertTrue(result.isSuccess)
        val lists = result.getOrThrow()
        assertEquals(2, lists.size)
        assertEquals(KeepList("list-001", "Groceries"), lists[0])
        assertEquals(KeepList("list-002", "Todo"), lists[1])
    }

    @Test
    fun `getLists caches result and returns from cache on second call`() = runTest {
        mockServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""[{"id":"list-001","title":"Groceries"}]""")
        )

        // First call fetches from server
        repository.getLists(forceRefresh = true)
        // Second call should use cache (no more server responses queued)
        val result = repository.getLists(forceRefresh = false)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
        // Only one request was made
        assertEquals(1, mockServer.requestCount)
    }

    @Test
    fun `getLists returns failure on server error`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(500))

        val result = repository.getLists(forceRefresh = true)

        assertTrue(result.isFailure)
    }

    @Test
    fun `getLists falls back to stale cache on network failure`() = runTest {
        // Populate cache first
        mockServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""[{"id":"list-001","title":"Groceries"}]""")
        )
        repository.getLists(forceRefresh = true)

        // Simulate network failure
        mockServer.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START))

        val result = repository.getLists(forceRefresh = true)

        // Should succeed with stale cached data
        assertTrue(result.isSuccess)
        assertEquals("Groceries", result.getOrThrow()[0].title)
    }

    // --- cache freshness ---

    @Test
    fun `isCacheFresh returns false when cache is empty`() {
        assertFalse(repository.isCacheFresh())
    }

    @Test
    fun `isCacheFresh returns true immediately after fetch`() = runTest {
        mockServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""[{"id":"list-001","title":"Groceries"}]""")
        )
        repository.getLists(forceRefresh = true)

        assertTrue(repository.isCacheFresh())
    }

    @Test
    fun `getCacheAge returns -1 when no cache`() {
        assertEquals(-1L, repository.getCacheAge())
    }

    @Test
    fun `getCacheAge returns recent value after fetch`() = runTest {
        mockServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""[{"id":"list-001","title":"Groceries"}]""")
        )
        repository.getLists(forceRefresh = true)

        val age = repository.getCacheAge()
        assertTrue(age >= 0)
        assertTrue(age < 5000) // should be within 5 seconds
    }

    @Test
    fun `clearCache resets freshness`() = runTest {
        mockServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""[{"id":"list-001","title":"Groceries"}]""")
        )
        repository.getLists(forceRefresh = true)
        assertTrue(repository.isCacheFresh())

        repository.clearCache()

        assertFalse(repository.isCacheFresh())
        assertEquals(-1L, repository.getCacheAge())
    }

    // --- addItem ---

    @Test
    fun `addItem sends correct request and returns success`() = runTest {
        mockServer.enqueue(MockResponse()
            .setResponseCode(201)
            .setBody("""{"message":"Added"}""")
        )

        val result = repository.addItem("list-001", "Milk")

        assertTrue(result.isSuccess)
        val request = mockServer.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.path!!.contains("list-001/items"))
        assertTrue(request.body.readUtf8().contains("Milk"))
    }

    @Test
    fun `addItem returns failure on server error`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(404))

        val result = repository.addItem("list-999", "Milk")

        assertTrue(result.isFailure)
    }

    // --- testConnection ---

    @Test
    fun `testConnection returns success for healthy server`() = runTest {
        mockServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""{"status":"ok"}""")
        )

        val result = repository.testConnection(mockServer.url("").toString())

        assertTrue(result.isSuccess)
    }

    @Test
    fun `testConnection returns failure for unhealthy server`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(503))

        val result = repository.testConnection(mockServer.url("").toString())

        assertTrue(result.isFailure)
    }
}
