package nl.freshlytyped.keepquickadd.calendar

import org.natty.DateGroup
import org.natty.Parser
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date

/**
 * Natural language date/time parser using Natty library.
 * Parses user input into structured datetime objects with associated
 * metadata like matched text ranges and confidence scores.
 */
class NattyDateParserService(
    private val normalizer: InputNormalizer = InputNormalizer(),
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : DateParserService {

    private val nattySingleton = Parser()

    override fun parse(input: String, now: ZonedDateTime): ParseResult {
        if (input.isBlank()) {
            return ParseResult(state = ParseState.NONE)
        }

        return try {
            val normalizedInput = normalizer.normalize(input)
            val dateGroups = nattySingleton.parse(normalizedInput.text)

            if (dateGroups.isEmpty()) {
                return ParseResult(state = ParseState.NONE)
            }

            val primaryDateGroup = dateGroups.first()
            val matchedText = primaryDateGroup.text.trim()
            val matchedRanges = extractMatchedRanges(input, matchedText, normalizedInput.text)

            // Extract start and end dates from the DateGroup
            // Default duration: 1 hour if no end time provided
            val dates = primaryDateGroup.dates
            val (startTime, endTime, state) = when {
                dates.size >= 2 -> {
                    Triple(
                        dateToZonedDateTime(dates[0]),
                        dateToZonedDateTime(dates[1]),
                        ParseState.RESOLVED
                    )
                }
                dates.size == 1 -> {
                    val start = dateToZonedDateTime(dates[0])
                    val end = start.plusHours(1)
                    Triple(start, end, ParseState.RESOLVED)
                }
                else -> Triple(null, null, ParseState.PARTIAL)
            }

            // Extract title by removing matched date segment from input
            val titleText = extractTitle(input, matchedText)

            ParseResult(
                start = startTime,
                end = endTime,
                titleText = titleText,
                matchedRanges = matchedRanges,
                state = state,
                confidence = calculateConfidence(primaryDateGroup),
                diagnostics = "Natty matched: '$matchedText' in input"
            )
        } catch (e: Exception) {
            ParseResult(
                state = ParseState.ERROR,
                diagnostics = "Parse error: ${e.message}"
            )
        }
    }

    /**
     * Convert a Java Date to ZonedDateTime using the configured timezone.
     */
    private fun dateToZonedDateTime(date: Date): ZonedDateTime {
        return Instant.ofEpochMilli(date.time)
            .atZone(zoneId)
    }

    /**
     * Extract matched text ranges from the original input.
     * Finds the position of matched text in the original (non-normalized) input.
     */
    private fun extractMatchedRanges(original: String, matchedText: String, normalized: String): List<IntRange> {
        if (matchedText.isEmpty()) {
            return emptyList()
        }

        // First try to find matched text in the original input
        val originalIndex = original.indexOf(matchedText, ignoreCase = true)
        if (originalIndex >= 0) {
            val endIndex = (originalIndex + matchedText.length).coerceAtMost(original.length)
            return listOf(originalIndex until endIndex)
        }

        // Fallback: search in normalized input
        val normalizedIndex = normalized.indexOf(matchedText, ignoreCase = true)
        if (normalizedIndex >= 0) {
            val endIndex = (normalizedIndex + matchedText.length).coerceAtMost(normalized.length)
            return listOf(normalizedIndex until endIndex)
        }

        // If exact match not found, return empty list
        return emptyList()
    }

    /**
     * Extract title by removing the matched date/time portion from input.
     * Returns null if input is entirely date/time (no title text).
     */
    private fun extractTitle(input: String, matchedText: String): String? {
        if (matchedText.isEmpty()) {
            return input.trim().takeIf { it.isNotEmpty() }
        }

        val title = input.replace(matchedText, "", ignoreCase = true).trim()
        return title.takeIf { it.isNotEmpty() }
    }

    /**
     * Calculate a confidence score (0-1) based on Natty's matched groups.
     * Heuristic: if Natty matched recursively or recursively extracted, higher confidence.
     */
    private fun calculateConfidence(dateGroup: DateGroup): Float {
        // Natty doesn't expose explicit confidence, so we use a simple heuristic
        // Recursive matches are generally more reliable
        val isRecursive = dateGroup.isRecurring
        return if (isRecursive) 0.85f else 0.90f
    }
}
