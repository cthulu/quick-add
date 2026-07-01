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
            val matchedRanges = mapNormalizedRangesToOriginal(input, matchedText, normalizedInput)

            // Extract start and end dates from the DateGroup
            // Default duration: 1 hour if no end time provided
            val dates = primaryDateGroup.dates
            val (startTime, endTime, state) = when {
                dates.size >= 2 -> {
                    val start = dateToZonedDateTime(dates[0])
                    val end = dateToZonedDateTime(dates[1])
                    Triple(
                        adjustIfInPast(start, now),
                        adjustIfInPast(end, now),
                        ParseState.RESOLVED
                    )
                }
                dates.size == 1 -> {
                    val start = dateToZonedDateTime(dates[0])
                    val adjustedStart = adjustIfInPast(start, now)
                    val end = adjustedStart.plusHours(1)
                    Triple(adjustedStart, end, ParseState.RESOLVED)
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
     * If the parsed datetime is in the past relative to 'now', adjust it to the next day.
     * This handles cases like "3pm" when it's already past 3pm today.
     */
    private fun adjustIfInPast(parsed: ZonedDateTime, now: ZonedDateTime): ZonedDateTime {
        return if (parsed.isBefore(now)) {
            parsed.plusDays(1)
        } else {
            parsed
        }
    }

    /**
     * Map matched ranges from normalized text back to original input positions
     * using the replacement tracking from InputNormalizer.
     */
    private fun mapNormalizedRangesToOriginal(
        original: String,
        matchedText: String,
        normalizedInput: InputNormalizer.NormalizedInput
    ): List<IntRange> {
        if (matchedText.isEmpty()) {
            return emptyList()
        }

        // Find the matched text position in the normalized string
        val normIndex = normalizedInput.text.indexOf(matchedText, ignoreCase = true)
        if (normIndex < 0) {
            return emptyList()
        }

        val normEnd = (normIndex + matchedText.length).coerceAtMost(normalizedInput.text.length)

        // Use the normalizer's mapping to convert back to original
        return normalizedInput.mapToOriginal(normIndex, normEnd)
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
