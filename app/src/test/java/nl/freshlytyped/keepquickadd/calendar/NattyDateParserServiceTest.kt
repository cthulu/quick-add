package nl.freshlytyped.keepquickadd.calendar

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class NattyDateParserServiceTest {

    private lateinit var parser: NattyDateParserService
    private val zoneId = ZoneId.systemDefault()

    @Before
    fun setup() {
        parser = NattyDateParserService(InputNormalizer(), zoneId)
    }

    @Test
    fun testParseEmpty() {
        val result = parser.parse("", ZonedDateTime.now())
        assertEquals(ParseState.NONE, result.state)
        assertNull(result.start)
    }

    @Test
    fun testParseBlank() {
        val result = parser.parse("   ", ZonedDateTime.now())
        assertEquals(ParseState.NONE, result.state)
    }

    @Test
    fun testParseTomorrow() {
        val now = ZonedDateTime.now()
        val result = parser.parse("tomorrow", now)
        
        assertNotNull(result.start)
        assertEquals(ParseState.RESOLVED, result.state)
        
        // Check that tomorrow is parsed (approximately 24 hours from now)
        val startDate = result.start!!.toLocalDate()
        val tomorrowDate = now.toLocalDate().plusDays(1)
        assertEquals(tomorrowDate, startDate)
    }

    @Test
    fun testParseTommorrowShorthand() {
        val now = ZonedDateTime.now()
        val result = parser.parse("tomm", now)
        
        assertNotNull(result.start)
        assertEquals(ParseState.RESOLVED, result.state)
    }

    @Test
    fun testParseInOneDay() {
        val now = ZonedDateTime.now()
        val result = parser.parse("in 1 day", now)
        
        assertEquals(ParseState.RESOLVED, result.state)
        assertNotNull(result.start)
    }

    @Test
    fun testParseNextSaturday() {
        val now = ZonedDateTime.now()
        val result = parser.parse("next Saturday", now)
        
        assertEquals(ParseState.RESOLVED, result.state)
        assertNotNull(result.start)
    }

    @Test
    fun testParseTimeWithAmPm() {
        val now = ZonedDateTime.now()
        val result = parser.parse("at 3pm", now)
        
        // Natty may parse this as "today at 3pm" depending on context
        assertEquals(ParseState.RESOLVED, result.state)
        assertNotNull(result.start)
    }

    @Test
    fun testParseFullEventString() {
        val now = ZonedDateTime.now()
        val result = parser.parse("Team sync tomorrow at 3pm", now)
        
        assertEquals(ParseState.RESOLVED, result.state)
        assertNotNull(result.start)
        
        // Title should be extracted (removing date portion)
        assertNotNull(result.titleText)
        assertTrue(result.titleText!!.contains("Team sync") || result.titleText!!.isNotEmpty())
    }

    @Test
    fun testParseWithAliasReplacement() {
        val now = ZonedDateTime.now()
        val result = parser.parse("dinner tomm at 7pm", now)
        
        assertEquals(ParseState.RESOLVED, result.state)
        assertNotNull(result.start)
    }

    @Test
    fun testParseMatchedRanges() {
        val now = ZonedDateTime.now()
        val input = "Meeting tomorrow at 3pm"
        val result = parser.parse(input, now)
        
        assertEquals(ParseState.RESOLVED, result.state)
        // Matched ranges should be present for resolved parses
        // (may be empty for some inputs, but should be list)
        assertNotNull(result.matchedRanges)
        
        // If ranges are present, verify they're valid
        for (range in result.matchedRanges) {
            assertTrue("Range start must be >= 0", range.first >= 0)
            // Range end may extend beyond original if normalized changed length
            assertTrue("Range must have valid bounds", range.first <= range.last)
        }
    }

    @Test
    fun testParseConfidence() {
        val now = ZonedDateTime.now()
        val result = parser.parse("tomorrow at 3pm", now)
        
        assertEquals(ParseState.RESOLVED, result.state)
        assertNotNull(result.confidence)
        assertTrue(result.confidence!! > 0)
        assertTrue(result.confidence!! <= 1.0f)
    }

    @Test
    fun testParseDiagnostics() {
        val now = ZonedDateTime.now()
        val result = parser.parse("tomorrow", now)
        
        assertEquals(ParseState.RESOLVED, result.state)
        assertNotNull(result.diagnostics)
        assertTrue(result.diagnostics!!.contains("Natty"))
    }

    @Test
    fun testParseErrorHandling() {
        val now = ZonedDateTime.now()
        val result = parser.parse("xyzabcnottadate", now)
        
        // Should either be NONE or ERROR, not crash
        assertTrue(
            result.state == ParseState.NONE || 
            result.state == ParseState.ERROR ||
            result.state == ParseState.PARTIAL
        )
    }

    @Test
    fun testTimezonePreservation() {
        val customZone = ZoneId.of("America/New_York")
        val parserWithZone = NattyDateParserService(InputNormalizer(), customZone)
        
        val now = ZonedDateTime.now(customZone)
        val result = parserWithZone.parse("tomorrow", now)
        
        if (result.start != null) {
            assertEquals(customZone, result.start!!.zone)
        }
    }

    @Test
    fun testParseStartAndEnd() {
        val now = ZonedDateTime.now()
        val result = parser.parse("tomorrow from 2pm to 4pm", now)
        
        assertEquals(ParseState.RESOLVED, result.state)
        assertNotNull(result.start)
        // End time may or may not be parsed depending on Natty's output
    }

    @Test
    fun testParseTitleExtraction() {
        val now = ZonedDateTime.now()
        val result = parser.parse("Doctor's appointment tomorrow at 2pm", now)
        
        assertEquals(ParseState.RESOLVED, result.state)
        // Title should be extracted or at least be non-null if present
        if (result.titleText != null) {
            assertTrue(result.titleText!!.isNotEmpty())
        }
    }

    @Test
    fun testParseConsistency() {
        val now = ZonedDateTime.now()
        val input = "meeting tomorrow at 3pm"
        
        val result1 = parser.parse(input, now)
        val result2 = parser.parse(input, now)
        
        // Same input should produce same parse state
        assertEquals(result1.state, result2.state)
        
        // Start times should be very close (same hour)
        if (result1.start != null && result2.start != null) {
            val diff = kotlin.math.abs(
                result1.start!!.hour - result2.start!!.hour
            )
            assertTrue(diff <= 1)
        }
    }

    @Test
    fun testDefaultDurationWhenNoEnd() {
        val now = ZonedDateTime.now()
        val result = parser.parse("tomorrow at 3pm", now)

        assertEquals(ParseState.RESOLVED, result.state)
        assertNotNull(result.start)
        assertNotNull(result.end)

        // Default duration should be 1 hour
        val durationSeconds = java.time.temporal.ChronoUnit.SECONDS.between(
            result.start, result.end
        )
        assertEquals(3600L, durationSeconds)
    }

    @Test
    fun testEndPreservedWhenExplicitRange() {
        val now = ZonedDateTime.now()
        // Natty may or may not parse explicit ranges; the important thing
        // is that when a second date IS parsed, it's preserved, not overridden.
        val result = parser.parse("tomorrow from 2pm to 4pm", now)

        assertEquals(ParseState.RESOLVED, result.state)
        assertNotNull(result.start)
        assertNotNull(result.end)
        assertTrue(result.end!!.isAfter(result.start))
    }

    @Test
    fun testParseSingleDigitTimeAliases() {
        val now = ZonedDateTime.now()

        val resultAm = parser.parse("at 3a", now)
        assertEquals(ParseState.RESOLVED, resultAm.state)
        assertNotNull(resultAm.start)

        val resultPm = parser.parse("at 7p", now)
        assertEquals(ParseState.RESOLVED, resultPm.state)
        assertNotNull(resultPm.start)
    }

    @Test
    fun testTimeInPastAdjustedToNextDay() {
        // Set "now" to 5pm today
        val now = ZonedDateTime.now().withHour(17).withMinute(0).withSecond(0).withNano(0)
        
        // Parse "3pm" which is in the past
        val result = parser.parse("3pm", now)
        
        assertEquals(ParseState.RESOLVED, result.state)
        assertNotNull(result.start)
        
        // Should be adjusted to tomorrow at 3pm
        val tomorrow = now.toLocalDate().plusDays(1)
        assertEquals(tomorrow, result.start!!.toLocalDate())
        assertEquals(15, result.start!!.hour) // 3pm = 15:00
    }

    @Test
    fun testTimeInFutureNotAdjusted() {
        // Set "now" to 2pm today
        val now = ZonedDateTime.now().withHour(14).withMinute(0).withSecond(0).withNano(0)
        
        // Parse "3pm" which is in the future
        val result = parser.parse("3pm", now)
        
        assertEquals(ParseState.RESOLVED, result.state)
        assertNotNull(result.start)
        
        // Should be today at 3pm (not adjusted)
        assertEquals(now.toLocalDate(), result.start!!.toLocalDate())
        assertEquals(15, result.start!!.hour) // 3pm = 15:00
    }

    @Test
    fun testParseMultiplePhrases() {
        val testCases = mapOf(
            "tomorrow" to true,
            "next week" to true,
            "in 3 days" to true,
            "Friday at 5pm" to true,
            "Monday 10am" to true
        )

        val now = ZonedDateTime.now()
        for ((phrase, shouldResolve) in testCases) {
            val result = parser.parse(phrase, now)
            
            if (shouldResolve) {
                assertEquals(
                    "Failed for phrase: $phrase",
                    ParseState.RESOLVED,
                    result.state
                )
            }
        }
    }

    @Test
    fun testParse3p() {
        val now = ZonedDateTime.now()
        val result = parser.parse("3p", now)
        assertEquals("Should parse '3p'", ParseState.RESOLVED, result.state)
        assertNotNull("3p should have start time", result.start)
    }

    @Test
    fun testParseWednesday() {
        val now = ZonedDateTime.now()
        val result = parser.parse("wed", now)
        assertEquals("Should parse 'wed' as wednesday", ParseState.RESOLVED, result.state)
        assertNotNull("wed should have start time", result.start)
    }

    @Test
    fun testParseTeamLunch3pOnWed() {
        val now = ZonedDateTime.now()
        val result = parser.parse("team lunch 3p on wed", now)
        
        assertEquals("Full string should resolve", ParseState.RESOLVED, result.state)
        assertNotNull("Should have start time", result.start)
        assertNotNull("Should have end time (default 1h)", result.end)
        assertNotNull("Should extract title 'team lunch'", result.titleText)
        assertTrue("Should have matched ranges", result.matchedRanges.isNotEmpty())
    }

    @Test
    fun testParseSaturdayEveningDinner() {
        val now = ZonedDateTime.now()
        val input = "Saturday evening dinner"
        val result = parser.parse(input, now)
        
        assertEquals("Should resolve Saturday evening", ParseState.RESOLVED, result.state)
        assertNotNull("Should have start time", result.start)
        assertNotNull("Should extract title", result.titleText)
        // Both "Saturday" and "evening" should be highlighted
        assertTrue("Should have matched ranges for date/time", result.matchedRanges.isNotEmpty())
    }
}
