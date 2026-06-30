# Step 8: Calendar Quick Add Foundation

## Goal
Introduce a new "Calendar Quick Add" popup that is triggered similarly to the existing Google Keep quick-add flow, without yet creating calendar events. This step establishes UI flow, data contracts, and extension points for later phases.

## Scope
- Add a new action entry point for Calendar quick add.
- Reuse the existing popup launch pattern used by Keep quick add.
- Build a dedicated draft state model for calendar entry text and parse state.
- No CalendarContract writes in this step.

## Non-Goals
- No real date parsing yet.
- No event insertion to Android calendar.
- No permission flow.

## Technical Details

### Trigger and Navigation
1. Identify the existing Keep quick-add trigger path (broadcast, click handler, pending intent, activity/fragment/dialog).
2. Add a parallel trigger for Calendar quick add using the same lifecycle and safety checks.
3. Ensure this new action is distinguishable in analytics/logging (`source=quick_add_calendar`).

### UI Container
- Create a popup/sheet/dialog component equivalent to Keep quick add.
- Input area initially accepts freeform text for "title + date/time phrase".
- Add placeholder hint examples:
  - "Team sync tomorrow at 3pm"
  - "Dentist next Saturday 10am"

### Domain Model
Create a draft model used throughout later phases:

```kotlin
data class CalendarEventDraft(
    val rawInput: String,
    val titleText: String?,
    val parsedStart: ZonedDateTime?,
    val parsedEnd: ZonedDateTime?,
    val parseState: ParseState,
    val matchedRanges: List<IntRange>,
    val timezoneId: String
)

enum class ParseState {
    NONE, PARTIAL, RESOLVED, ERROR
}
```

Notes:
- `matchedRanges` are ranges in `rawInput` for date/time tokens recognized by parser.
- `timezoneId` defaults to device timezone.

### Parsing Abstraction (Stub)
Define an interface now, with temporary stub implementation:

```kotlin
interface DateParserService {
    fun parse(input: String, now: ZonedDateTime = ZonedDateTime.now()): ParseResult
}
```

`ParseResult` should include:
- parsed start/end (nullable),
- extracted title text (nullable),
- matched text ranges,
- parse state,
- optional parser diagnostics string.

Use a noop parser for this phase that always returns `ParseState.NONE`.

## Deliverables
- New Calendar quick-add popup accessible from trigger.
- `CalendarEventDraft` and parser interfaces introduced.
- Feature-flag or guarded routing in place for safe incremental rollout.

## Acceptance Criteria
- User can open Calendar quick-add from the same UX family as Keep quick add.
- Popup renders and accepts typing with no crashes.
- Internal state updates as user types.
- No calendar permissions requested in this step.
