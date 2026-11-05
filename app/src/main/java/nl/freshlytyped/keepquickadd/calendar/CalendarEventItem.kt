package nl.freshlytyped.keepquickadd.calendar

import java.time.ZonedDateTime

/**
 * A single time-specific calendar event, used for the date preview panel.
 * All-day and fully-past events are excluded upstream, so only this shape is needed.
 */
data class CalendarEventItem(
    val id: Long,
    val title: String,
    val startTime: ZonedDateTime,
    val endTime: ZonedDateTime
)
