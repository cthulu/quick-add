package nl.freshlytyped.keepquickadd.calendar

import java.time.Duration
import java.time.ZonedDateTime

/**
 * Pure selection logic for the date preview panel.
 *
 * Considers the events on the identified day and picks the ones that are still upcoming
 * and fall within a buffer around the parsed event window. When there are more candidates
 * than fit, the ones closest to the parsed window are kept, then ordered chronologically
 * for display.
 */
object EventPreviewSelection {

    private const val DEFAULT_WINDOW_HOURS = 2L
    private const val DEFAULT_MAX_EVENTS = 6

    fun select(
        events: List<CalendarEventItem>,
        parsedStart: ZonedDateTime,
        parsedEnd: ZonedDateTime,
        now: ZonedDateTime,
        windowHours: Long = DEFAULT_WINDOW_HOURS,
        maxEvents: Int = DEFAULT_MAX_EVENTS
    ): List<CalendarEventItem> {
        val bufferStart = parsedStart.minusHours(windowHours)
        val bufferEnd = parsedEnd.plusHours(windowHours)

        val candidates = events
            .filter { overlaps(it, bufferStart, bufferEnd) }
            .filter { it.endTime >= now }

        if (candidates.size <= maxEvents) {
            return candidates.sortedBy { it.startTime }
        }

        return candidates
            .sortedBy { distanceToParsed(it, parsedStart, parsedEnd) }
            .take(maxEvents)
            .sortedBy { it.startTime }
    }

    /** True when the event intersects the [bufferStart, bufferEnd] candidate window. */
    private fun overlaps(event: CalendarEventItem, bufferStart: ZonedDateTime, bufferEnd: ZonedDateTime): Boolean =
        event.startTime <= bufferEnd && event.endTime >= bufferStart

    /** Gap between the event and the parsed window; zero when the event overlaps it. */
    private fun distanceToParsed(event: CalendarEventItem, parsedStart: ZonedDateTime, parsedEnd: ZonedDateTime): Duration {
        if (event.endTime < parsedStart) return Duration.between(event.endTime, parsedStart)
        if (event.startTime > parsedEnd) return Duration.between(parsedEnd, event.startTime)
        return Duration.ZERO
    }
}
