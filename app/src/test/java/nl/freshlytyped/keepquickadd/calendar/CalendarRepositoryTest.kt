package nl.freshlytyped.keepquickadd.calendar

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
class CalendarRepositoryTest {

    private lateinit var contentResolver: ContentResolver
    private lateinit var repository: CalendarRepository

    @Before
    fun setup() {
        val context = mock<Context>()
        contentResolver = mock()
        whenever(context.contentResolver).thenReturn(contentResolver)
        repository = CalendarRepository(context)
    }

    @Test
    fun `findWritableCalendar prefers primary writable visible calendar`() {
        val cursor = calendarCursor(
            CalendarRow(1, "First", 1, CalendarContract.Calendars.CAL_ACCESS_EDITOR),
            CalendarRow(2, "Primary", 1, CalendarContract.Calendars.CAL_ACCESS_EDITOR, isPrimary = true)
        )
        whenever(contentResolver.query(
            any(), any(), eq("${CalendarContract.Calendars.VISIBLE} = 1"), isNull(), isNull()
        )).thenReturn(cursor)

        assertEquals(2L, repository.findWritableCalendar()?.id)
    }

    @Test
    fun `findWritableCalendar falls back to first writable visible calendar`() {
        val cursor = calendarCursor(
            CalendarRow(1, "First", 1, CalendarContract.Calendars.CAL_ACCESS_EDITOR),
            CalendarRow(2, "Read-only primary", 1, CalendarContract.Calendars.CAL_ACCESS_READ),
            CalendarRow(3, "Second", 1, CalendarContract.Calendars.CAL_ACCESS_EDITOR)
        )
        whenever(contentResolver.query(
            any(), any(), eq("${CalendarContract.Calendars.VISIBLE} = 1"), isNull(), isNull()
        )).thenReturn(cursor)

        assertEquals(1L, repository.findWritableCalendar()?.id)
    }

    @Test
    fun `findWritableCalendar returns null when no visible calendar is writable`() {
        val cursor = calendarCursor(
            CalendarRow(1, "Read only", 1, CalendarContract.Calendars.CAL_ACCESS_READ)
        )
        whenever(contentResolver.query(
            any(), any(), eq("${CalendarContract.Calendars.VISIBLE} = 1"), isNull(), isNull()
        )).thenReturn(cursor)

        assertEquals(null, repository.findWritableCalendar())
    }

    @Test
    fun `findAllCalendars returns only writable calendars from provider result`() {
        val cursor = calendarCursor(
            CalendarRow(1, "Writable", 1, CalendarContract.Calendars.CAL_ACCESS_EDITOR),
            CalendarRow(2, "Read only", 1, CalendarContract.Calendars.CAL_ACCESS_READ)
        )
        whenever(contentResolver.query(
            any(), any(), eq("${CalendarContract.Calendars.VISIBLE} = 1"), isNull(), isNull()
        )).thenReturn(cursor)

        assertEquals(listOf(1L), repository.findAllCalendars().map { it.id })
    }

    @Test
    fun `insertEvent leaves alarm configuration to the calendar provider`() {
        whenever(contentResolver.insert(any(), any()))
            .thenReturn(Uri.parse("content://com.android.calendar/events/42"))

        val eventId = repository.insertEvent(
            calendarId = 7L,
            title = "Planning",
            startTime = ZonedDateTime.of(2026, 9, 9, 10, 0, 0, 0, ZoneId.of("UTC")),
            endTime = null,
            timezone = "UTC"
        )

        val valuesCaptor = argumentCaptor<ContentValues>()
        verify(contentResolver).insert(eq(CalendarContract.Events.CONTENT_URI), valuesCaptor.capture())
        verify(contentResolver, never()).insert(eq(CalendarContract.Reminders.CONTENT_URI), any())

        assertEquals(42L, eventId)
        assertFalse(valuesCaptor.firstValue.containsKey(CalendarContract.Events.HAS_ALARM))
    }

    @Test
    fun `insertEvent returns null when calendar provider returns null URI`() {
        whenever(contentResolver.insert(any(), any())).thenReturn(null)

        val eventId = repository.insertEvent(
            calendarId = 7L,
            title = "Planning",
            startTime = startTime,
            endTime = null,
            timezone = "UTC"
        )

        assertEquals(null, eventId)
    }

    @Test
    fun `insertEvent returns null when calendar provider throws`() {
        doThrow(IllegalStateException("provider unavailable"))
            .whenever(contentResolver)
            .insert(any(), any())

        val eventId = repository.insertEvent(
            calendarId = 7L,
            title = "Planning",
            startTime = startTime,
            endTime = null,
            timezone = "UTC"
        )

        assertEquals(null, eventId)
    }

    private fun calendarCursor(vararg rows: CalendarRow): MatrixCursor {
        return MatrixCursor(projection).apply {
            rows.forEach { row ->
                addRow(arrayOf<Any?>(row.id, row.name, row.visible, row.ownerAccount, row.accessLevel, if (row.isPrimary) 1 else 0))
            }
        }
    }

    private data class CalendarRow(
        val id: Long,
        val name: String,
        val visible: Int,
        val accessLevel: Int,
        val ownerAccount: String = "",
        val isPrimary: Boolean = false
    )

    private companion object {
        val startTime = ZonedDateTime.of(2026, 9, 9, 10, 0, 0, 0, ZoneId.of("UTC"))

        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.OWNER_ACCOUNT,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.IS_PRIMARY
        )
    }
}
