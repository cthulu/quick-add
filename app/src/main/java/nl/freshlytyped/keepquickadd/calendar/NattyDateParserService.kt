package nl.freshlytyped.keepquickadd.calendar

import org.natty.DateGroup
import org.natty.Parser
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import java.util.TimeZone

/**
 * Natural language date/time parser using Natty library.
 * Parses user input into structured datetime objects with associated
 * metadata like matched text ranges and confidence scores.
 */
class NattyDateParserService(
    private val normalizer: InputNormalizer = InputNormalizer(),
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : DateParserService {

    private val nattySingleton = Parser(TimeZone.getTimeZone(zoneId))

    override fun parse(input: String, now: ZonedDateTime): ParseResult {
        if (input.isBlank()) {
            return ParseResult(state = ParseState.NONE)
        }

        return try {
            val normalizedInput = normalizer.normalize(input)
            val referenceNow = now.withZoneSameInstant(zoneId)
            val dateGroups = nattySingleton.parse(normalizedInput.text, Date.from(referenceNow.toInstant()))

            if (dateGroups.isEmpty()) {
                return ParseResult(state = ParseState.NONE)
            }

            // Collect matched text from all date groups for better highlighting
            val allMatchedTexts = dateGroups.map { it.text.trim() }
            val combinedMatchedText = allMatchedTexts.joinToString(" ")
            val timeOnly = isTimeOnlyPhrase(normalizedInput.text)
            val ambiguousHour = containsAmbiguousHour(normalizedInput.text)
            val explicitDate = containsExplicitDateReference(normalizedInput.text)
            val preferFuture = !explicitDate && (timeOnly || ambiguousHour)
            
            val matchedRanges = findAllMatchedRanges(input, allMatchedTexts, normalizedInput)

            // Extract start and end dates from the DateGroups
            // If multiple groups exist, use first for date, second for time
            val dates = mutableListOf<Date>()
            for (group in dateGroups) {
                dates.addAll(group.dates)
            }

            val (startTime, endTime, state) = when {
                dates.size >= 2 -> {
                    val start = preferAfternoonForAmbiguousHour(
                        dateToZonedDateTime(dates[0]),
                        normalizedInput.text
                    )
                    val end = preferAfternoonForAmbiguousHour(
                        dateToZonedDateTime(dates[1]),
                        normalizedInput.text
                    )
                    // Adjust endpoints independently. Do not normalize the interval as a
                    // whole: explicit dates are authoritative, while bare overnight times
                    // may legitimately resolve to different calendar days.
                    Triple(
                        adjustIfInPast(start, referenceNow, preferFuture),
                        adjustIfInPast(end, referenceNow, preferFuture),
                        ParseState.RESOLVED
                    )
                }
                dates.size == 1 -> {
                    val start = preferAfternoonForAmbiguousHour(
                        dateToZonedDateTime(dates[0]),
                        normalizedInput.text
                    )
                    val adjustedStart = adjustIfInPast(start, referenceNow, preferFuture)
                    val end = adjustedStart.plusHours(1)
                    Triple(adjustedStart, end, ParseState.RESOLVED)
                }
                else -> Triple(null, null, ParseState.PARTIAL)
            }

            // Extract title by removing all matched date segments from input
            val titleText = extractTitle(input, matchedRanges)

            ParseResult(
                start = startTime,
                end = endTime,
                titleText = titleText,
                matchedRanges = matchedRanges,
                state = state,
                diagnostics = "Natty matched: '$combinedMatchedText' in input (${dateGroups.size} groups)"
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
     * If a bare time is in the past relative to 'now', adjust that endpoint to the next day.
     * Explicit date references are never adjusted. For a bare overnight range, this is
     * intentionally applied to each endpoint rather than to the range as a whole.
     */
    private fun adjustIfInPast(
        parsed: ZonedDateTime,
        now: ZonedDateTime,
        adjustPastTime: Boolean
    ): ZonedDateTime {
        if (!adjustPastTime) return parsed
        val timeOnCurrentDate = parsed.withYear(now.year)
            .withMonth(now.monthValue)
            .withDayOfMonth(now.dayOfMonth)
        return if (timeOnCurrentDate.isBefore(now)) timeOnCurrentDate.plusDays(1) else timeOnCurrentDate
    }

    /**
     * Date references define their own day; only bare times are assumed to mean today.
     */
    private fun containsExplicitDateReference(text: String): Boolean {
        val dateReferencePattern = Regex(
            """\b(?:yesterday|today|tomorrow|tonight|last|next|this|monday|tuesday|wednesday|thursday|friday|saturday|sunday|january|february|march|april|may|june|july|august|september|october|november|december|\d{1,2}[/-]\d{1,2}(?:[/-]\d{2,4})?)\b""",
            RegexOption.IGNORE_CASE
        )
        return dateReferencePattern.containsMatchIn(text)
    }

    private fun isTimeOnlyPhrase(text: String): Boolean {
        if (containsExplicitDateReference(text)) return false
        return Regex(
            "^\\s*(?:from\\s+)?(?:at\\s+)?\\d{1,2}(?::\\d{2})?\\s*(?:am|pm|a|p)\\s*(?:(?:to|until)\\s+(?:at\\s+)?\\d{1,2}(?::\\d{2})?\\s*(?:am|pm|a|p))?\\s*$",
            RegexOption.IGNORE_CASE
        ).matches(text)
    }

    private fun containsAmbiguousHour(text: String): Boolean {
        return Regex(
            "\\b(?:at|from|to|until)\\s+(?:[1-9]|1[0-2])(?!\\d)(?:[:.]\\d{2})?(?!\\s*(?:am|pm|a|p)\\b)",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(text)
    }

    private fun preferAfternoonForAmbiguousHour(
        parsed: ZonedDateTime,
        text: String
    ): ZonedDateTime {
        if (!containsAmbiguousHour(text)) return parsed

        val preferredHour = when (parsed.hour) {
            0 -> 12
            in 1..11 -> parsed.hour + 12
            else -> parsed.hour
        }
        return parsed.withHour(preferredHour)
    }

    /**
     * Find all matched ranges for multiple matched texts and combine them.
     */
    private fun findAllMatchedRanges(
        original: String,
        matchedTexts: List<String>,
        normalizedInput: InputNormalizer.NormalizedInput
    ): List<IntRange> {
        val allRanges = mutableListOf<IntRange>()
        for (matchedText in matchedTexts) {
            allRanges.addAll(mapNormalizedRangesToOriginal(original, matchedText, normalizedInput))
        }
        return allRanges.sortedBy { it.first }
    }

    /**
     * Map matched ranges from normalized text back to original input positions
     * using the replacement tracking from InputNormalizer.
     * Also expands ranges to include adjacent time-related words.
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

        var normStart = normIndex
        var normEnd = (normIndex + matchedText.length).coerceAtMost(normalizedInput.text.length)

        // Expand the range to include adjacent time-related words
        val timeRelatedWords = setOf("evening", "morning", "afternoon", "night", "tonight", "dawn", "dusk")
        val normalizedLower = normalizedInput.text.lowercase()

        // Expand backward
        var checkPos = normStart - 1
        while (checkPos >= 0) {
            val wordStart = normalizedLower.lastIndexOf(' ', checkPos) + 1
            val wordEnd = checkPos + 1
            val word = normalizedLower.substring(wordStart, wordEnd)
            if (timeRelatedWords.contains(word.trim())) {
                normStart = wordStart
                checkPos = wordStart - 2
            } else {
                break
            }
        }

        // Expand forward
        checkPos = normEnd
        while (checkPos < normalizedLower.length) {
            val spacePos = normalizedLower.indexOf(' ', checkPos)
            val wordEnd = if (spacePos < 0) normalizedLower.length else spacePos
            val word = normalizedLower.substring(checkPos, wordEnd)
            if (timeRelatedWords.contains(word.trim())) {
                normEnd = wordEnd
                checkPos = wordEnd + 1
            } else {
                break
            }
        }

        // Use the normalizer's mapping to convert back to original
        return normalizedInput.mapToOriginal(normStart, normEnd)
    }

    /**
     * Extract title by removing the matched date/time portions from input.
     * Uses matched ranges (mapped to original positions) to reliably strip
     * date tokens even when normalization altered the text.
     * Returns null if input is entirely date/time (no title text).
     */
    private fun extractTitle(input: String, matchedRanges: List<IntRange>): String? {
        if (matchedRanges.isEmpty()) {
            return input.trim().replace("\\s+".toRegex(), " ")
                .takeIf { it.isNotEmpty() }
        }

        val sortedRanges = matchedRanges.sortedBy { it.first }
        val result = StringBuilder()
        var lastEnd = 0
        for (range in sortedRanges) {
            val start = range.first.coerceAtLeast(0)
            val end = (range.last + 1).coerceAtMost(input.length)
            if (start > lastEnd) {
                result.append(input.substring(lastEnd, start))
            }
            lastEnd = maxOf(lastEnd, end)
        }
        if (lastEnd < input.length) {
            result.append(input.substring(lastEnd))
        }

        val title = result.toString().trim().replace("\\s+".toRegex(), " ")
        return title.takeIf { it.isNotEmpty() }
    }
}
