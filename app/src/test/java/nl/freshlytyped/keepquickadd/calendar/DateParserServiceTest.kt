package nl.freshlytyped.keepquickadd.calendar

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

class DateParserServiceTest {

    @Test
    fun testNoopParserReturnsNoneState() {
        val parser = NoopDateParserService()
        val result = parser.parse("tomorrow at 3pm")

        assertNotNull(result)
        assertEquals(ParseState.NONE, result.state)
    }

    @Test
    fun testNoopParserEmptyInput() {
        val parser = NoopDateParserService()
        val result = parser.parse("")

        assertEquals(ParseState.NONE, result.state)
    }

    @Test
    fun testNoopParserComplexInput() {
        val parser = NoopDateParserService()
        val result = parser.parse("Team sync tomorrow at 3pm in conference room A")

        assertEquals(ParseState.NONE, result.state)
        assertNull(result.start)
        assertNull(result.end)
        assertNull(result.titleText)
        assertTrue(result.matchedRanges.isEmpty())
    }

    @Test
    fun testParseResultDefaults() {
        val result = ParseResult()

        assertNull(result.start)
        assertNull(result.end)
        assertNull(result.titleText)
        assertTrue(result.matchedRanges.isEmpty())
        assertEquals(ParseState.NONE, result.state)
        assertNull(result.confidence)
        assertNull(result.diagnostics)
    }

    @Test
    fun testParseResultWithData() {
        val now = ZonedDateTime.now()
        val result = ParseResult(
            start = now.plusDays(1),
            end = now.plusDays(1).plusHours(1),
            titleText = "Meeting",
            matchedRanges = listOf(0..6),
            state = ParseState.RESOLVED,
            confidence = 0.95f,
            diagnostics = "Parsed successfully"
        )

        assertNotNull(result.start)
        assertNotNull(result.end)
        assertEquals("Meeting", result.titleText)
        assertEquals(1, result.matchedRanges.size)
        assertEquals(ParseState.RESOLVED, result.state)
        assertEquals(0.95f, result.confidence)
        assertEquals("Parsed successfully", result.diagnostics)
    }

    @Test
    fun testParserServiceInterface() {
        val parser: DateParserService = NoopDateParserService()

        // Verify the interface is correctly implemented
        assertNotNull(parser)
        val result = parser.parse("test")
        assertNotNull(result)
    }
}
