package nl.freshlytyped.keepquickadd.calendar

import java.time.ZonedDateTime

interface DateParserService {
    fun parse(input: String, now: ZonedDateTime = ZonedDateTime.now()): ParseResult
}
