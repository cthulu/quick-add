package com.keepquickadd

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppSettingsTest {

    private lateinit var settings: AppSettings

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
        settings = AppSettings(context)
    }

    // --- backendUrl ---

    @Test
    fun `backendUrl returns default when not set`() {
        assertEquals(AppSettings.DEFAULT_BACKEND_URL, settings.backendUrl)
    }

    @Test
    fun `backendUrl persists saved value`() {
        settings.backendUrl = "http://192.168.1.100:8000"
        assertEquals("http://192.168.1.100:8000", settings.backendUrl)
    }

    @Test
    fun `backendUrl trims trailing slash`() {
        settings.backendUrl = "http://192.168.1.100:8000/"
        assertEquals("http://192.168.1.100:8000", settings.backendUrl)
    }

    @Test
    fun `isConfigured returns false when not set`() {
        assertFalse(settings.isConfigured())
    }

    @Test
    fun `isConfigured returns true after setting url`() {
        settings.backendUrl = "http://10.0.2.2:8000"
        assertTrue(settings.isConfigured())
    }

    // --- lastSelectedListName ---

    @Test
    fun `lastSelectedListName is null when not set`() {
        assertNull(settings.lastSelectedListName)
    }

    @Test
    fun `lastSelectedListName persists saved value`() {
        settings.lastSelectedListName = "Groceries"
        assertEquals("Groceries", settings.lastSelectedListName)
    }

    @Test
    fun `lastSelectedListName can be overwritten`() {
        settings.lastSelectedListName = "Groceries"
        settings.lastSelectedListName = "Todo"
        assertEquals("Todo", settings.lastSelectedListName)
    }

    // --- getLists / saveLists ---

    @Test
    fun `getLists returns default list when none configured`() {
        val lists = settings.getLists()
        assertEquals(1, lists.size)
        assertEquals(AppSettings.DEFAULT_LIST_NAME, lists[0])
    }

    @Test
    fun `saveLists and getLists roundtrips correctly`() {
        settings.saveLists(listOf("Groceries", "Todo", "Ideas"))
        val lists = settings.getLists()
        assertEquals(3, lists.size)
        assertEquals("Groceries", lists[0])
        assertEquals("Todo", lists[1])
        assertEquals("Ideas", lists[2])
    }

    @Test
    fun `saveLists keeps first entry even if blank`() {
        settings.saveLists(listOf("", "Todo"))
        val lists = settings.getLists()
        assertEquals(AppSettings.DEFAULT_LIST_NAME, lists[0])
    }

    @Test
    fun `saveLists removes blank entries after first`() {
        settings.saveLists(listOf("Groceries", "", "Ideas", ""))
        val lists = settings.getLists()
        assertEquals(2, lists.size)
        assertEquals("Groceries", lists[0])
        assertEquals("Ideas", lists[1])
    }

    @Test
    fun `saveLists always has at least one entry`() {
        settings.saveLists(emptyList())
        val lists = settings.getLists()
        assertEquals(1, lists.size)
        assertEquals(AppSettings.DEFAULT_LIST_NAME, lists[0])
    }
}
