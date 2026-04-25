package com.keepquickadd

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
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
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
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

    // --- addItem ---

    @Test
    fun `addItem sends correct request with list name and text`() = runTest {
        mockServer.enqueue(MockResponse()
            .setResponseCode(201)
            .setBody("""{"message":"Added"}""")
        )

        val result = repository.addItem("Groceries", "Milk")

        assertTrue(result.isSuccess)
        val request = mockServer.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/items", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("Milk"))
        assertTrue(body.contains("Groceries"))
    }

    @Test
    fun `addItem returns failure on 404 (list not found)`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(404))

        val result = repository.addItem("NonExistentList", "Milk")

        assertTrue(result.isFailure)
    }

    @Test
    fun `addItem returns failure on network error`() = runTest {
        mockServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val result = repository.addItem("Groceries", "Milk")

        assertTrue(result.isFailure)
    }

    @Test
    fun `addItem returns failure on server error`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(500))

        val result = repository.addItem("Groceries", "Milk")

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
        assertEquals("/health", mockServer.takeRequest().path)
    }

    @Test
    fun `testConnection returns failure for unhealthy server`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(503))

        val result = repository.testConnection(mockServer.url("").toString())

        assertTrue(result.isFailure)
    }

    @Test
    fun `testConnection trims trailing slash from url`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        repository.testConnection(mockServer.url("").toString() + "/")

        assertEquals("/health", mockServer.takeRequest().path)
    }
}
