package nl.freshlytyped.keepquickadd.calendar

import java.time.ZonedDateTime

data class ParseResult(
    val start: ZonedDateTime? = null,
    val end: ZonedDateTime? = null,
    val titleText: String? = null,
    val matchedRanges: List<IntRange> = emptyList(),
    val state: ParseState = ParseState.NONE,
    val confidence: Float? = null,
    val diagnostics: String? = null
)
