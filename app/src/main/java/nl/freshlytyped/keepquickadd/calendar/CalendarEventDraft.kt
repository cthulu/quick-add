package nl.freshlytyped.keepquickadd.calendar

import java.time.ZonedDateTime

data class CalendarEventDraft(
    val rawInput: String,
    val titleText: String? = null,
    val parsedStart: ZonedDateTime? = null,
    val parsedEnd: ZonedDateTime? = null,
    val parseState: ParseState = ParseState.NONE,
    val matchedRanges: List<IntRange> = emptyList(),
    val timezoneId: String = "UTC"
)
