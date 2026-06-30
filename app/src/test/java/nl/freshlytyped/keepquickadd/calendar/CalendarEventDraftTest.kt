package nl.freshlytyped.keepquickadd.calendar

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class CalendarEventDraftTest {

    @Test
    fun testCalendarEventDraftCreation() {
        val draft = CalendarEventDraft(
            rawInput = "Team sync tomorrow at 3pm",
            titleText = "Team sync",
            parsedStart = ZonedDateTime.now().plusDays(1),
            parseState = ParseState.RESOLVED
        )

        assertEquals("Team sync tomorrow at 3pm", draft.rawInput)
        assertEquals("Team sync", draft.titleText)
        assertNotNull(draft.parsedStart)
        assertEquals(ParseState.RESOLVED, draft.parseState)
    }

    @Test
    fun testCalendarEventDraftWithDefaults() {
        val draft = CalendarEventDraft(rawInput = "Some event")

        assertEquals("Some event", draft.rawInput)
        assertNull(draft.titleText)
        assertNull(draft.parsedStart)
        assertNull(draft.parsedEnd)
        assertEquals(ParseState.NONE, draft.parseState)
        assertTrue(draft.matchedRanges.isEmpty())
    }

    @Test
    fun testParseStateEnum() {
        assertEquals(4, ParseState.values().size)
        assertTrue(ParseState.values().contains(ParseState.NONE))
        assertTrue(ParseState.values().contains(ParseState.PARTIAL))
        assertTrue(ParseState.values().contains(ParseState.RESOLVED))
        assertTrue(ParseState.values().contains(ParseState.ERROR))
    }

    @Test
    fun testTimezoneId() {
        val draft = CalendarEventDraft(
            rawInput = "Meeting",
            timezoneId = "America/New_York"
        )

        assertEquals("America/New_York", draft.timezoneId)
    }

    @Test
    fun testMatchedRanges() {
        val ranges = listOf(5..10, 15..20)
        val draft = CalendarEventDraft(
            rawInput = "Event tomorrow at 3pm",
            matchedRanges = ranges
        )

        assertEquals(2, draft.matchedRanges.size)
        assertEquals(5..10, draft.matchedRanges[0])
        assertEquals(15..20, draft.matchedRanges[1])
    }
}
