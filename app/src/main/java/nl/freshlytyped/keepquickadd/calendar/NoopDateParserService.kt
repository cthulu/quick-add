package nl.freshlytyped.keepquickadd.calendar

import java.time.ZonedDateTime

class NoopDateParserService : DateParserService {
    override fun parse(input: String, now: ZonedDateTime): ParseResult {
        return ParseResult(state = ParseState.NONE)
    }
}
