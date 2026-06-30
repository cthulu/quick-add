# Step 9: Natural Language Parsing with Natty

## Goal
Integrate Natty as the natural-language parser backend and produce structured parse results from freeform input. Include a normalization layer for common shorthand/typos (e.g., "tomm").

## Scope
- Add Natty dependency and parser adapter implementation.
- Convert Natty output into app-level `ParseResult`.
- Implement pre-parse normalization and fuzzy aliases for user shorthand.
- Keep execution local and fast.

## Non-Goals
- UI highlighting polish is handled in next step.
- Calendar insertion is handled in step 11.

## Technical Details

### Dependency
Add Natty dependency (community-maintained artifact):
- Group: `io.github.natty-parser`
- Artifact: `natty`
- Version: latest stable approved by project constraints

Ensure compatibility with:
- Kotlin + JVM 21
- Android SDK 36 target

### Parser Adapter
Implement:

```kotlin
class NattyDateParserService : DateParserService
```

Flow:
1. Normalize input (see below).
2. Parse via Natty `Parser`.
3. Read first best `DateGroup` (or multiple if needed).
4. Convert `Date` to `ZonedDateTime` using device/default timezone.
5. Extract matched text region from Natty metadata where available.
6. Infer title by removing matched date segment from raw input.

### Normalization Layer
Implement deterministic preprocessing before Natty:
- lowercase
- trim/reduce repeated spaces
- alias replacements:
  - `tomm` -> `tomorrow`
  - `tmrw` -> `tomorrow`
  - `sat` -> `saturday` (if standalone token)
- convert "in 1 day" variants to parser-friendly spacing/form

Keep mapping table configurable for future expansion.

### ParseResult Contract
Return:
- `start: ZonedDateTime?`
- `end: ZonedDateTime?`
- `titleText: String?`
- `matchedRanges: List<IntRange>`
- `state: ParseState` (`NONE/PARTIAL/RESOLVED/ERROR`)
- `confidence: Float?` (optional heuristic)

State guidelines:
- `NONE`: no date tokens detected
- `PARTIAL`: time/date phrase detected but not fully resolvable
- `RESOLVED`: concrete datetime parsed
- `ERROR`: parser failure/exception

### Performance Constraints
- Parse on background thread (coroutines dispatcher/worker thread).
- Avoid blocking UI thread.
- Target median parse under 200ms for typical quick-add input.

## Deliverables
- Natty integrated and wrapped in `DateParserService`.
- Normalization module + shorthand alias support.
- Unit tests for key phrases and timezone conversion.

## Acceptance Criteria
- Inputs parse successfully:
  - "tomorrow"
  - "tomm"
  - "in 1 day"
  - "next Saturday"
  - "at 3pm"
  - "Meeting tomorrow at 3pm"
- Parsed start time resolves correctly in local timezone.
- App remains responsive while parsing.
