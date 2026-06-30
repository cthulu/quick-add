# Step 10: Realtime Parsing and Inline Date Highlighting

## Goal
Provide live visual feedback while typing by highlighting recognized date/time portions with a red background in the input area, and show parsed preview state.

## Scope
- Parse continuously as user types.
- Highlight matched date/time spans with red background.
- Render parse status and resolved datetime preview.
- Handle rapid typing without jank.

## Non-Goals
- Event insertion/save logic (step 11).

## Technical Details

### Realtime Parse Loop
- Attach text-change listener to input.
- Debounce parse requests (`120-180ms` recommended).
- Cancel previous parse job on new input.
- Keep parser work off main thread.
- Apply result only if input revision matches latest version.

### Highlight Rendering
Approach depends on UI stack:
- View system: `SpannableStringBuilder` + `BackgroundColorSpan` on `matchedRanges`.
- Compose: `AnnotatedString` with span style background.

Highlight style:
- Red background for date/time segments.
- Ensure text remains readable (contrast checked).
- Preserve cursor position and editing experience.

### Parse Feedback UI
Under input field, show a status line:
- `NONE`: "No date/time detected"
- `PARTIAL`: "Date/time detected, keep typing..."
- `RESOLVED`: formatted datetime preview, e.g. "Sat, Jul 4, 3:00 PM"
- `ERROR`: "Couldn't parse date/time"

### Range Integrity
- Ranges must map to original user-visible input.
- If normalization changes text length, keep index mapping from normalized string back to raw text.
- Avoid out-of-bounds spans when input mutates quickly.

### UX Rules
- Never block typing for parsing.
- Do not auto-rewrite user input while typing.
- Highlight only parser-recognized date/time text, not full input.
- Keep title text unhighlighted.

## Deliverables
- Live parser integration with debouncing.
- Red inline highlight for recognized date/time tokens.
- Parse state/preview indicator in popup.

## Acceptance Criteria
- User sees red highlight appear/disappear as date phrases are typed/edited.
- Highlight remains stable during fast typing and deletions.
- Parse preview updates in near real-time.
- No crashes or ANRs from parsing loop.
