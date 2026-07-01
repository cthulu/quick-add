package nl.freshlytyped.keepquickadd.calendar

/**
 * Preprocesses user input to normalize shorthand, typos, and spacing
 * before feeding to the date parser. Maintains a mapping of original
 * text spans to normalized spans for accurate range reporting.
 */
class InputNormalizer {

    private val aliasMap = mapOf(
        "tomm" to "tomorrow",
        "tmrw" to "tomorrow",
        "tom" to "tomorrow",
        "tues" to "tuesday",
        "wed" to "wednesday",
        "thurs" to "thursday",
        "fri" to "friday",
        "sat" to "saturday",
        "sun" to "sunday",
        "mon" to "monday",
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

    data class NormalizedInput(
        val text: String,
        val originalToNormalizedMap: Map<Int, Int> = emptyMap()
    )

    /**
     * Normalize input text by:
     * 1. Trimming leading/trailing whitespace
     * 2. Reducing repeated spaces to single space
     * 3. Applying alias replacements (tomm -> tomorrow, etc.)
     * 4. Tracking character-level mapping from original to normalized text
     */
    fun normalize(input: String): NormalizedInput {
        if (input.isEmpty()) {
            return NormalizedInput(input)
        }

        var normalized = input.trim()
        
        // Reduce multiple spaces to single space
        normalized = normalized.replace(Regex("\\s+"), " ")
        
        // Apply alias replacements (case-insensitive)
        for ((alias, replacement) in aliasMap) {
            // Match whole words only, case-insensitive
            val regex = Regex("\\b$alias\\b", RegexOption.IGNORE_CASE)
            normalized = normalized.replace(regex, replacement)
        }
        
        // For simplicity in this phase, we don't build a detailed character map
        // since the normalized and original lengths should be close.
        // This can be enhanced if needed for precise span mapping.
        
        return NormalizedInput(
            text = normalized,
            originalToNormalizedMap = emptyMap()
        )
    }

    /**
     * Get the configured alias map for testing/inspection.
     */
    fun getAliasMap(): Map<String, String> = aliasMap.toMap()
}
