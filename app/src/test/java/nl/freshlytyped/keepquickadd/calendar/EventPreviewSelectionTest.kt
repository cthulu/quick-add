package nl.freshlytyped.keepquickadd.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class EventPreviewSelectionTest {

    private val zone = ZoneId.of("UTC")
    private val day = LocalDate.of(2026, 11, 4)

    private fun at(hour: Int, minute: Int = 0): ZonedDateTime =
        day.atTime(hour, minute).atZone(zone)

    private fun event(id: Long, startHour: Int, startMin: Int = 0, endHour: Int, endMin: Int = 0) =
        CalendarEventItem(id, "Event $id", at(startHour, startMin), at(endHour, endMin), 0xFF226699.toInt(), "Calendar")

    private val parsedStart = at(14, 0)
    private val parsedEnd = at(15, 0)

    @Test
    fun `keeps in-buffer events and sorts chronologically`() {
        val events = listOf(
            event(1, 8, 0, 9, 0),      // before buffer, past
            event(2, 11, 0, 12, 30),   // in buffer
            event(3, 13, 0, 14, 30),   // in buffer, overlaps parsed
            event(4, 15, 30, 16, 30),  // in buffer, after parsed
            event(5, 18, 0, 19, 0)     // after buffer
        )

        val selected = EventPreviewSelection.select(events, parsedStart, parsedEnd, now = at(10, 0))

        assertEquals(listOf(2L, 3L, 4L), selected.map { it.id })
    }

    @Test
    fun `excludes fully past events`() {
        val events = listOf(
            event(1, 13, 0, 14, 30),   // ended before now
            event(2, 15, 0, 16, 0),    // in progress
            event(3, 16, 0, 17, 0)     // upcoming
        )

        val selected = EventPreviewSelection.select(events, parsedStart, parsedEnd, now = at(15, 0))

        assertEquals(listOf(2L, 3L), selected.map { it.id })
    }

    @Test
    fun `keeps tomorrow events near the parsed time`() {
        val tomorrowStart = day.plusDays(1).atTime(13, 0).atZone(zone)
        val tomorrowEnd = tomorrowStart.plusHours(1)
        val tomorrowEvent = CalendarEventItem(
            9L,
            "Lunch",
            tomorrowStart,
            tomorrowEnd,
            0xFF226699.toInt(),
            "Calendar"
        )

        val selected = EventPreviewSelection.select(
            events = listOf(tomorrowEvent),
            parsedStart = tomorrowStart,
            parsedEnd = tomorrowEnd,
            now = at(10, 0)
        )

        assertEquals(listOf(9L), selected.map { it.id })
    }

    @Test
    fun `excludes events outside the buffer`() {
        val events = listOf(
            event(1, 10, 0, 11, 0),   // before buffer
            event(2, 13, 0, 13, 30),  // in buffer
            event(3, 17, 30, 18, 0)   // after buffer
        )

        val selected = EventPreviewSelection.select(events, parsedStart, parsedEnd, now = at(9, 0))

        assertEquals(listOf(2L), selected.map { it.id })
    }

    @Test
    fun `keeps only the closest six when more fit`() {
        val events = listOf(
            event(1, 12, 0, 12, 10),   // farthest (1h50m from parsed)
            event(2, 12, 30, 12, 40),
            event(3, 13, 0, 13, 10),
            event(4, 13, 30, 13, 40),
            event(5, 14, 0, 15, 0),    // overlaps parsed (distance 0)
            event(6, 15, 30, 15, 40),
            event(7, 16, 30, 16, 50)
        )

        val selected = EventPreviewSelection.select(events, parsedStart, parsedEnd, now = at(9, 0))

        // Event 1 is the farthest from the parsed window and is dropped.
        assertEquals(listOf(2L, 3L, 4L, 5L, 6L, 7L), selected.map { it.id })
        assertTrue(selected.map { it.id }.contains(1L).not())
    }

    @Test
    fun `returns empty when there are no events`() {
        val selected = EventPreviewSelection.select(emptyList(), parsedStart, parsedEnd, now = at(9, 0))
        assertTrue(selected.isEmpty())
    }
}
