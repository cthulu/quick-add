package nl.freshlytyped.keepquickadd

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
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

    private lateinit var context: Context
    private lateinit var settings: AppSettings

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Clear all prefs before each test
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
        settings = AppSettings(context)
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    // --- PubNub keys ---

    @Test
    fun `publishKey returns empty string when not set`() {
        assertEquals("", settings.publishKey)
    }

    @Test
    fun `publishKey persists trimmed value`() {
        settings.publishKey = "  pub-c-test-key  "
        assertEquals("pub-c-test-key", settings.publishKey)
    }

    @Test
    fun `subscribeKey persists trimmed value`() {
        settings.subscribeKey = "  sub-c-test-key  "
        assertEquals("sub-c-test-key", settings.subscribeKey)
    }

    @Test
    fun `apiKey persists trimmed value`() {
        settings.apiKey = "  some-auth-token  "
        assertEquals("some-auth-token", settings.apiKey)
    }

    @Test
    fun `channel returns default when not set`() {
        assertEquals(AppSettings.DEFAULT_CHANNEL, settings.channel)
    }

    @Test
    fun `channel persists value`() {
        settings.channel = "my-custom-channel"
        assertEquals("my-custom-channel", settings.channel)
    }

    @Test
    fun `isConfigured returns false when keys missing`() {
        assertFalse(settings.isConfigured())
    }

    @Test
    fun `isConfigured returns false when only publishKey set`() {
        settings.publishKey = "pub-c-test"
        assertFalse(settings.isConfigured())
    }

    @Test
    fun `isConfigured returns true when both keys set`() {
        settings.publishKey = "pub-c-test"
        settings.subscribeKey = "sub-c-test"
        assertTrue(settings.isConfigured())
    }

    // --- lastSelectedListName ---

    @Test
    fun `lastSelectedListName returns null when not set`() {
        assertNull(settings.lastSelectedListName)
    }

    @Test
    fun `lastSelectedListName persists set value`() {
        settings.lastSelectedListName = "Groceries"
        assertEquals("Groceries", settings.lastSelectedListName)
    }

    // --- getLists / saveLists ---

    @Test
    fun `getLists returns single default list when nothing saved`() {
        val lists = settings.getLists()
        assertEquals(1, lists.size)
        assertEquals(AppSettings.DEFAULT_LIST_NAME, lists[0])
    }

    @Test
    fun `saveLists persists multiple list names`() {
        settings.saveLists(listOf("Groceries", "TODO", "Shopping"))
        assertEquals(listOf("Groceries", "TODO", "Shopping"), settings.getLists())
    }

    @Test
    fun `saveLists keeps first entry even if blank by replacing with default`() {
        settings.saveLists(listOf("", "TODO"))
        val lists = settings.getLists()
        assertEquals(AppSettings.DEFAULT_LIST_NAME, lists[0])
        assertEquals("TODO", lists[1])
    }

    @Test
    fun `saveLists trims and removes blank non-first entries`() {
        settings.saveLists(listOf("Groceries", "  ", "TODO", ""))
        assertEquals(listOf("Groceries", "TODO"), settings.getLists())
    }

    @Test
    fun `saveLists overwrites previous lists`() {
        settings.saveLists(listOf("A", "B", "C"))
        settings.saveLists(listOf("X", "Y"))
        assertEquals(listOf("X", "Y"), settings.getLists())
    }
}
