package nl.freshlytyped.keepquickadd.calendar

import java.time.ZonedDateTime

/**
 * A single time-specific calendar event, used for the date preview panel.
 * All-day and fully-past events are excluded upstream, so only this shape is needed.
 * [calendarColor] tints the row's dot; [calendarName] is stored for future use but not displayed.
 */
data class CalendarEventItem(
    val id: Long,
    val title: String,
    val startTime: ZonedDateTime,
    val endTime: ZonedDateTime,
    val calendarColor: Int,
    val calendarName: String
)
