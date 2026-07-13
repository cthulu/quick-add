package nl.freshlytyped.keepquickadd.calendar

class InputNormalizer {

    private val aliasMap = mapOf(
        "tomm" to "tomorrow",
        "tmrw" to "tomorrow",
        "tom" to "tomorrow",
        "tue" to "tuesday",
        "tues" to "tuesday",
        "wed" to "wednesday",
        "thurs" to "thursday",
        "fri" to "friday",
        "sat" to "saturday",
        "sun" to "sunday",
        "mon" to "monday",
        "evening" to "evening",
        "eve" to "evening",
        "morn" to "morning",
        "ton" to "tonight",
        "tonight" to "tonight",
        "nite" to "night",
        "1a" to "1 am",
        "2a" to "2 am",
        "3a" to "3 am",
        "4a" to "4 am",
        "5a" to "5 am",
        "6a" to "6 am",
        "7a" to "7 am",
        "8a" to "8 am",
        "9a" to "9 am",
        "2am" to "2 am",
        "3am" to "3 am",
        "4am" to "4 am",
        "5am" to "5 am",
        "6am" to "6 am",
        "7am" to "7 am",
        "8am" to "8 am",
        "9am" to "9 am",
        "10am" to "10 am",
        "11am" to "11 am",
        "12am" to "12 am",
        "1p" to "1 pm",
        "2p" to "2 pm",
        "3p" to "3 pm",
        "4p" to "4 pm",
        "5p" to "5 pm",
        "6p" to "6 pm",
        "7p" to "7 pm",
        "8p" to "8 pm",
        "9p" to "9 pm",
        "2pm" to "2 pm",
        "3pm" to "3 pm",
        "4pm" to "4 pm",
        "5pm" to "5 pm",
        "6pm" to "6 pm",
        "7pm" to "7 pm",
        "8pm" to "8 pm",
        "9pm" to "9 pm",
        "10pm" to "10 pm",
        "11pm" to "11 pm",
        "12pm" to "12 pm"
    )

    data class Replacement(
        val originalStart: Int,
        val originalEnd: Int,
        val normalizedStart: Int,
        val normalizedEnd: Int
    )

    data class NormalizedInput(
        val text: String,
        val replacements: List<Replacement> = emptyList()
    ) {
        fun mapToOriginal(normStart: Int, normEnd: Int): List<IntRange> {
            if (replacements.isEmpty()) {
                return listOf(normStart until normEnd)
            }

            val origStart = mapNormalizedPositionToOriginal(normStart, snapToEnd = false)
            val origEnd = mapNormalizedPositionToOriginal(normEnd, snapToEnd = true)

            return if (origStart < origEnd) {
                listOf(origStart until origEnd)
            } else {
                emptyList()
            }
        }

        private fun mapNormalizedPositionToOriginal(normPos: Int, snapToEnd: Boolean): Int {
            var offset = 0
            for (rep in replacements) {
                if (normPos >= rep.normalizedEnd) {
                    offset += (rep.originalEnd - rep.originalStart) -
                        (rep.normalizedEnd - rep.normalizedStart)
                } else if (snapToEnd) {
                    if (normPos > rep.normalizedStart) {
                        return rep.originalEnd
                    }
                    break
                } else {
                    if (normPos >= rep.normalizedStart) {
                        return rep.originalStart
                    }
                    break
                }
            }
            return normPos + offset
        }
    }

    fun normalize(input: String): NormalizedInput {
        if (input.isEmpty()) {
            return NormalizedInput(input)
        }

        var current = input

        // Find all alias matches in the current text
        data class Match(val origStart: Int, val origEnd: Int, val alias: String, val replacement: String)
        
        val allMatches = mutableListOf<Match>()
        for ((alias, replacement) in aliasMap) {
            val regex = Regex("\\b${Regex.escape(alias)}\\b", RegexOption.IGNORE_CASE)
            for (match in regex.findAll(current)) {
                allMatches.add(Match(match.range.first, match.range.last + 1, alias, replacement))
            }
        }

        // Sort by position
        allMatches.sortBy { it.origStart }

        // Apply replacements from right to left to preserve positions
        val replacements = mutableListOf<Replacement>()
        for (match in allMatches.asReversed()) {
            current = current.substring(0, match.origStart) + match.replacement + current.substring(match.origEnd)
        }

        // Recalculate positions after all replacements
        var currentPos = 0
        var originalPos = 0
        for (match in allMatches.sortedBy { it.origStart }) {
            val lengthBefore = match.origStart - originalPos
            val aliasLength = match.alias.length
            val replacementLength = match.replacement.length
            
            currentPos += lengthBefore
            replacements.add(
                Replacement(
                    match.origStart,
                    match.origEnd,
                    currentPos,
                    currentPos + replacementLength
                )
            )
            currentPos += replacementLength
            originalPos = match.origEnd
        }

        // Add final unmatched section
        currentPos += current.length - originalPos

        return NormalizedInput(
            text = current,
            replacements = replacements.toList()
        )
    }

    fun getAliasMap(): Map<String, String> = aliasMap.toMap()
}
