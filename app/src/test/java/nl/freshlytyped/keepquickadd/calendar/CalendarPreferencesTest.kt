package nl.freshlytyped.keepquickadd.calendar

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CalendarPreferencesTest {

    private lateinit var context: Context
    private lateinit var preferences: CalendarPreferences

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("calendar_quick_add", Context.MODE_PRIVATE)
            .edit().clear().commit()
        preferences = CalendarPreferences(context)
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("calendar_quick_add", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `selected calendar id persists`() {
        preferences.saveSelectedCalendar(42L)

        assertEquals(42L, CalendarPreferences(context).getSelectedCalendarId())
    }

    @Test
    fun `available remembered calendar is restored`() {
        val remembered = calendar(2L, "Remembered")
        preferences.saveSelectedCalendar(remembered.id)

        assertEquals(remembered, preferences.selectCalendar(listOf(calendar(1L, "First"), remembered)))
    }

    @Test
    fun `missing remembered calendar falls back to first and updates preference`() {
        preferences.saveSelectedCalendar(99L)
        val firstVisibleWritable = calendar(1L, "First")

        assertEquals(firstVisibleWritable, preferences.selectCalendar(listOf(firstVisibleWritable, calendar(2L, "Second"))))
        assertEquals(firstVisibleWritable.id, preferences.getSelectedCalendarId())
    }

    @Test
    fun `no available calendar returns null`() {
        preferences.saveSelectedCalendar(99L)

        assertNull(preferences.selectCalendar(emptyList()))
        assertEquals(99L, preferences.getSelectedCalendarId())
    }

    @Test
    fun `hidden calendars are excluded from available calendars`() {
        val hidden = calendar(2L, "Hidden")
        val visible = calendar(1L, "Visible")

        assertEquals(
            listOf(visible),
            preferences.filterHiddenCalendars(listOf(hidden, visible), setOf(hidden.id))
        )
    }

    @Test
    fun `hidden remembered calendar falls back to first visible calendar`() {
        val hidden = calendar(2L, "Hidden")
        val visible = calendar(1L, "Visible")
        preferences.saveSelectedCalendar(hidden.id)

        val filtered = preferences.filterHiddenCalendars(listOf(hidden, visible), setOf(hidden.id))

        assertEquals(visible, preferences.selectCalendar(filtered))
        assertEquals(visible.id, preferences.getSelectedCalendarId())
    }

    @Test
    fun `remembered selection survives recreation with a fresh preferences instance`() {
        val remembered = calendar(2L, "Remembered")
        preferences.saveSelectedCalendar(remembered.id)

        val recreatedPreferences = CalendarPreferences(context)

        assertEquals(remembered, recreatedPreferences.selectCalendar(listOf(calendar(1L, "First"), remembered)))
    }

    private fun calendar(id: Long, name: String) =
        CalendarRepository.CalendarInfo(id, name, "$name@example.com")
}
