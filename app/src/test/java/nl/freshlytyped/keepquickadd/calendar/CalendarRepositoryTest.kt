package nl.freshlytyped.keepquickadd.calendar

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.graphics.Color
import android.net.Uri
import android.provider.CalendarContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
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
    fun `findAllCalendars returns all visible calendars including read-only`() {
        val cursor = calendarCursor(
            CalendarRow(1, "Writable", color = 0xFF226699.toInt()),
            CalendarRow(2, "Shared read-only")
        )
        whenever(contentResolver.query(
            any(), any(), eq("${CalendarContract.Calendars.VISIBLE} = 1"), isNull(), isNull()
        )).thenReturn(cursor)

        val calendars = repository.findAllCalendars()

        assertEquals(listOf(1L, 2L), calendars.map { it.id })
        assertEquals(0xFF226699.toInt(), calendars.first().color)
    }

    @Test
    fun `findEventsOnDay returns only non-all-day occurrences`() {
        val cursor = instanceCursor(
            InstanceRow(1, "All day thing", allDay = 1, calendarId = 7L, begin = dtStart, end = dtEnd),
            InstanceRow(2, "Time block", allDay = 0, calendarId = 7L, begin = dtStart, end = dtEnd)
        )
        whenever(contentResolver.query(any(), any(), any(), any(), anyOrNull())).thenReturn(cursor)

        val events = repository.findEventsOnDay(dtStart - 1_000, dtEnd + 1_000, listOf(calendar(7L)))

        assertEquals(listOf(2L), events.map { it.id })
    }

    @Test
    fun `findEventsOnDay maps title and start-end times`() {
        val cursor = instanceCursor(
            InstanceRow(9, "Standup", allDay = 0, begin = 10_000, end = 11_000)
        )
        whenever(contentResolver.query(any(), any(), any(), any(), anyOrNull())).thenReturn(cursor)

        val event = repository.findEventsOnDay(0, 20_000, listOf(calendar(1L), calendar(2L))).single()

        assertEquals("Standup", event.title)
        assertEquals(10_000L, event.startTime.toInstant().toEpochMilli())
        assertEquals(11_000L, event.endTime.toInstant().toEpochMilli())
    }

    @Test
    fun `findEventsOnDay returns expanded occurrences of recurring events`() {
        val cursor = instanceCursor(
            InstanceRow(5, "Team sync", allDay = 0, begin = 100_000, end = 110_000)
        )
        whenever(contentResolver.query(any(), any(), any(), any(), anyOrNull())).thenReturn(cursor)

        val events = repository.findEventsOnDay(0, 200_000, listOf(calendar(1L)))
        val uriCaptor = argumentCaptor<Uri>()
        val projectionCaptor = argumentCaptor<Array<String>>()
        val selectionCaptor = argumentCaptor<String>()
        val selectionArgsCaptor = argumentCaptor<Array<String>>()
        val sortOrderCaptor = argumentCaptor<String>()
        verify(contentResolver).query(
            uriCaptor.capture(),
            projectionCaptor.capture(),
            selectionCaptor.capture(),
            selectionArgsCaptor.capture(),
            sortOrderCaptor.capture()
        )

        assertEquals(1, events.size)
        assertEquals(100_000L, events.single().startTime.toInstant().toEpochMilli())
        assertEquals(listOf("0", "200000"), uriCaptor.firstValue.pathSegments.takeLast(2))
        assertTrue(projectionCaptor.firstValue.contains(CalendarContract.Instances.EVENT_ID))
        assertEquals("${CalendarContract.Calendars.VISIBLE}=?", selectionCaptor.firstValue)
        assertEquals(listOf("1"), selectionArgsCaptor.firstValue.toList())
        assertEquals("${CalendarContract.Instances.BEGIN} ASC", sortOrderCaptor.firstValue)
    }

    @Test
    fun `findEventsOnDay drops the master occurrence of a moved exception`() {
        val cursor = instanceCursor(
            InstanceRow(5, "Team sync", allDay = 0, begin = 100_000, end = 110_000),
            InstanceRow(9, "Team sync", allDay = 0, originalId = "5", originalTime = 100_000, begin = 120_000, end = 130_000)
        )
        whenever(contentResolver.query(any(), any(), any(), any(), anyOrNull())).thenReturn(cursor)

        val events = repository.findEventsOnDay(0, 200_000, listOf(calendar(1L)))

        assertEquals(listOf(9L), events.map { it.id })
        assertEquals(120_000L, events.single().startTime.toInstant().toEpochMilli())
    }

    @Test
    fun `findEventsOnDay sorts occurrences chronologically`() {
        val cursor = instanceCursor(
            InstanceRow(3, "Late", allDay = 0, begin = 120_000, end = 130_000),
            InstanceRow(2, "Early", allDay = 0, begin = 100_000, end = 110_000)
        )
        whenever(contentResolver.query(any(), any(), any(), any(), anyOrNull())).thenReturn(cursor)

        val events = repository.findEventsOnDay(0, 200_000, listOf(calendar(1L)))

        assertEquals(listOf(2L, 3L), events.map { it.id })
    }

    @Test
    fun `findEventsOnDay maps the calendar color and name`() {
        val cursor = instanceCursor(
            InstanceRow(9, "Standup", allDay = 0, calendarId = 7, begin = 10_000, end = 11_000)
        )
        whenever(contentResolver.query(any(), any(), any(), any(), anyOrNull())).thenReturn(cursor)

        val event = repository.findEventsOnDay(0, 20_000, listOf(calendar(7L, 0xFF226699.toInt()))).single()

        assertEquals(0xFF226699.toInt(), event.calendarColor)
        assertEquals("Calendar 7", event.calendarName)
    }

    @Test
    fun `findEventsOnDay excludes occurrences from calendars outside requested set`() {
        val cursor = instanceCursor(
            InstanceRow(9, "Standup", allDay = 0, calendarId = 99, begin = 10_000, end = 11_000)
        )
        whenever(contentResolver.query(any(), any(), isNull(), isNull(), anyOrNull())).thenReturn(cursor)

        val events = repository.findEventsOnDay(0, 20_000, listOf(calendar(1L)))

        assertTrue(events.isEmpty())
    }

    @Test
    fun `findEventsOnDay returns empty without querying when no calendars`() {
        val events = repository.findEventsOnDay(dtStart, dtEnd, emptyList())

        assertTrue(events.isEmpty())
        verify(contentResolver, never()).query(any(), any(), any(), any(), any())
    }

    private fun calendar(id: Long, color: Int = Color.GRAY) =
        CalendarRepository.CalendarInfo(id, "Calendar $id", "", color)

    private fun calendarCursor(vararg rows: CalendarRow): MatrixCursor {
        return MatrixCursor(projection).apply {
            rows.forEach { row ->
                addRow(arrayOf<Any?>(row.id, row.name, row.ownerAccount, row.color))
            }
        }
    }

    private fun instanceCursor(vararg rows: InstanceRow): MatrixCursor {
        return MatrixCursor(instanceProjection).apply {
            rows.forEach { row ->
                addRow(arrayOf<Any?>(row.id, row.title, row.allDay, row.originalId, row.originalTime, row.begin, row.end, row.calendarId))
            }
        }
    }

    private data class CalendarRow(
        val id: Long,
        val name: String,
        val ownerAccount: String = "",
        val color: Int = Color.GRAY
    )

    private data class InstanceRow(
        val id: Long,
        val title: String,
        val allDay: Int,
        val originalId: String? = null,
        val originalTime: Long? = null,
        val begin: Long,
        val end: Long,
        val calendarId: Long = 1L
    )

    private companion object {
        val startTime = ZonedDateTime.of(2026, 9, 9, 10, 0, 0, 0, ZoneId.of("UTC"))
        val dtStart = startTime.toInstant().toEpochMilli()
        val dtEnd = startTime.plusMinutes(30).toInstant().toEpochMilli()

        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.OWNER_ACCOUNT,
            CalendarContract.Calendars.CALENDAR_COLOR
        )

        val instanceProjection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.ORIGINAL_ID,
            CalendarContract.Events.ORIGINAL_INSTANCE_TIME,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Events.CALENDAR_ID
        )
    }
}
