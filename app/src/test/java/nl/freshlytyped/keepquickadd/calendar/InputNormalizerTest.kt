package nl.freshlytyped.keepquickadd.calendar

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import org.junit.Test

class InputNormalizerTest {

    private val normalizer = InputNormalizer()

    @Test
    fun testNormalizeEmptyInput() {
        val result = normalizer.normalize("")
        assertEquals("", result.text)
    }

    @Test
    fun testNormalizePreservesLeadingTrailingSpaces() {
        val result = normalizer.normalize("  hello world  ")
        assertEquals("  hello world  ", result.text)
    }

    @Test
    fun testNormalizePreservesSpaces() {
        val result = normalizer.normalize("hello    world   test")
        assertEquals("hello    world   test", result.text)
    }

    @Test
    fun testNormalizeTommAlias() {
        val result = normalizer.normalize("meeting tomm at 3pm")
        assertEquals("meeting tomorrow at 3 pm", result.text)
    }

    @Test
    fun testNormalizeTmrwAlias() {
        val result = normalizer.normalize("lunch tmrw")
        assertEquals("lunch tomorrow", result.text)
    }

    @Test
    fun testNormalizeTomAlias() {
        val result = normalizer.normalize("call tom at noon")
        assertEquals("call tomorrow at noon", result.text)
    }

    @Test
    fun testNormalizeDayOfWeekAliases() {
        val testCases = mapOf(
            "tue" to "tuesday",
            "tues" to "tuesday",
            "wed" to "wednesday",
            "thurs" to "thursday",
            "fri" to "friday",
            "sat" to "saturday",
            "sun" to "sunday",
            "mon" to "monday"
        )

        for ((alias, full) in testCases) {
            val result = normalizer.normalize("meeting next $alias")
            assertEquals("meeting next $full", result.text)
        }
    }

    @Test
    fun testNormalizeSingleDigitTimeAliases() {
        val amCases = mapOf(
            "1a" to "1 am",
            "2a" to "2 am",
            "3a" to "3 am",
            "4a" to "4 am",
            "5a" to "5 am",
            "6a" to "6 am",
            "7a" to "7 am",
            "8a" to "8 am",
            "9a" to "9 am"
        )

        for ((alias, full) in amCases) {
            val result = normalizer.normalize("at $alias")
            assertEquals("at $full", result.text)
        }

        val pmCases = mapOf(
            "1p" to "1 pm",
            "2p" to "2 pm",
            "3p" to "3 pm",
            "4p" to "4 pm",
            "5p" to "5 pm",
            "6p" to "6 pm",
            "7p" to "7 pm",
            "8p" to "8 pm",
            "9p" to "9 pm"
        )

        for ((alias, full) in pmCases) {
            val result = normalizer.normalize("at $alias")
            assertEquals("at $full", result.text)
        }
    }

    @Test
    fun testNormalizeTimeAliases() {
        val testCases = mapOf(
            "2am" to "2 am",
            "3pm" to "3 pm",
            "10am" to "10 am",
            "12pm" to "12 pm"
        )

        for ((alias, full) in testCases) {
            val result = normalizer.normalize("at $alias")
            assertEquals("at $full", result.text)
        }
    }

    @Test
    fun testNormalizeCaseInsensitive() {
        val result = normalizer.normalize("Meeting TOMM At 3PM")
        // Aliases should be case-insensitive
        assertTrue(result.text.contains("tomorrow"))
        assertTrue(result.text.contains("3 pm"))
    }

    @Test
    fun testNormalizeMultipleAliases() {
        val result = normalizer.normalize("dinner tomm at 7pm")
        assertEquals("dinner tomorrow at 7 pm", result.text)
    }

    @Test
    fun testGetAliasMap() {
        val aliasMap = normalizer.getAliasMap()
        assertNotNull(aliasMap)
        assertTrue(aliasMap.size > 0)
        assertEquals("tomorrow", aliasMap["tomm"])
        assertEquals("tomorrow", aliasMap["tmrw"])
        assertEquals("tuesday", aliasMap["tue"])
        assertEquals("tuesday", aliasMap["tues"])
        assertEquals("3 pm", aliasMap["3pm"])
        assertEquals("3 am", aliasMap["3a"])
        assertEquals("7 pm", aliasMap["7p"])
    }

    @Test
    fun testNormalizeNoChanges() {
        val input = "Meeting tomorrow at 3 pm"
        val result = normalizer.normalize(input)
        assertEquals(input, result.text)
    }

    @Test
    fun testNormalizePreservesStructure() {
        val result = normalizer.normalize("Team sync tomm at 10am in conference room")
        assertEquals("Team sync tomorrow at 10 am in conference room", result.text)
    }

    @Test
    fun testNormalize3p() {
        val result = normalizer.normalize("3p")
        assertEquals("3 pm", result.text)
    }

    @Test
    fun testNormalizeWed() {
        val result = normalizer.normalize("wed")
        assertEquals("wed should expand to wednesday", "wednesday", result.text)
        assertEquals("Should have 1 replacement", 1, result.replacements.size)
    }

    @Test
    fun testNormalizeTeamLunch3pOnWed() {
        val result = normalizer.normalize("team lunch 3p on wed")
        assertEquals("team lunch 3 pm on wednesday", result.text)
        assertEquals("Should have 2 replacements (3p and wed)", 2, result.replacements.size)
    }
}
