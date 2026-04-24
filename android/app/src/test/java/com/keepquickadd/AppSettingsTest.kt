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
        // Use a fresh prefs instance per test by clearing it
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
        settings = AppSettings(context)
    }

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

    @Test
    fun `lastSelectedListId is null when not set`() {
        assertNull(settings.lastSelectedListId)
    }

    @Test
    fun `lastSelectedListId persists saved value`() {
        settings.lastSelectedListId = "list-001"
        assertEquals("list-001", settings.lastSelectedListId)
    }

    @Test
    fun `lastSelectedListId can be overwritten`() {
        settings.lastSelectedListId = "list-001"
        settings.lastSelectedListId = "list-002"
        assertEquals("list-002", settings.lastSelectedListId)
    }
}
