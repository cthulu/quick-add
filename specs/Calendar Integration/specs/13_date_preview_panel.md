# Step 13: Date Preview Panel

## Goal
When the user types text that resolves to a date/time, show a floating preview panel above the input area displaying existing calendar events around that time. This helps users spot scheduling conflicts before saving.

## Scope
- Query existing events from visible calendars around the parsed time window.
- Display matching events in a scrollable panel between the input field and action buttons.
- Auto-hide the panel when the parsed text no longer resolves to a date/time.

## Non-Goals
- Modifying or editing existing events.
- Showing events from hidden or inaccessible calendars.
- All-day events or past events.

## Technical Details

### Two-Stage Debounce
- **Parse + highlight**: 120ms debounce (unchanged from step 10).
- **Event lookup**: 300ms debounce. Resets on every keystroke. The lookup runs its own parse of the input, then queries.

Each stage tracks its own revision counter; cancel the previous job on new input and apply the result only if the revision is still the latest.

### Event Query Logic
Given the parsed event's start and end time (e.g., `14:00–15:00` on Nov 4):

1. **Query**: the identified day (midnight to midnight) from all visible calendars (the set shown in the dropdown, excluding hidden calendars).
2. **Exclude all-day events** (from the provider query).
3. **Candidate buffer**: keep events that overlap the parsed window ±2 hours. For `14:00–15:00`, that is `12:00–17:00`.
4. **Exclude past events**: drop events where `endTime < now`.
5. **Sort**: Chronologically, earliest first.
6. **Limit**: If ≤6 candidates, show all. If >6, keep the 6 closest to the parsed window, then re-sort chronologically.
7. **Empty results**: Keep the panel hidden (`GONE`).

### Panel Visibility Rules
- `RESOLVED` parse + non-empty query results → panel `VISIBLE`.
- `RESOLVED` parse + empty results → panel `GONE`.
- Any other parse state (`NONE`, `PARTIAL`, `ERROR`) → panel hidden.
- Both showing and hiding go through the 300ms lookup debounce, so the panel settles after the user stops typing.

### UI Layout
- Panel sits above `etEventInput` in `layout_calendar_floating_widget.xml` (top of the input layout).
- **Header**: Day label, e.g. `Wednesday, Nov 4`.
- **Body**: `RecyclerView` listing event rows.
- Each row displays: `start_time – end_time  title` (e.g. `13:00 – 14:30  Client call`).
- Compact padding, subtle dividers between rows.
- Match the existing widget aesthetic (rounded corners, widget background color, dynamic Material colors).

### New Files
| File | Purpose |
|---|---|
| `calendar/CalendarEventItem.kt` | Data class: `id`, `title`, `startTime`, `endTime` |
| `calendar/EventPreviewSelection.kt` | Pure buffer/proximity selection logic (unit tested) |
| `calendar/EventPreviewAdapter.kt` | `ListAdapter` for event rows |
| `res/layout/layout_event_preview_panel.xml` | Panel container: day header + `RecyclerView` |
| `res/layout/layout_event_preview_row.xml` | Single event row layout |

### Modified Files
| File | Changes |
|---|---|
| `calendar/CalendarRepository.kt` | Add `findEventsOnDay(dayStartMillis, dayEndMillis, calendarIds)` |
| `calendar/CalendarQuickAddActivity.kt` | Add 300ms lookup debounce; query & populate panel on `RESOLVED`; hide on other states |
| `res/layout/layout_calendar_floating_widget.xml` | Insert preview panel above the text input |

### Performance
- Query runs on `Dispatchers.Default` (off main thread).
- 300ms debounce prevents excessive queries during rapid typing.
- Cancel previous job on new input to avoid stale results.
- If calendars finish loading after the first lookup ran with an empty set, the lookup re-runs with the current input.

### Testing
- `EventPreviewSelectionTest` covers buffer filtering, past-event exclusion, chronological sorting, and the closest-six trim.
- `CalendarRepositoryTest` covers `findEventsOnDay`: all-day exclusion, field mapping, and the empty-calendar early return.

## Deliverables
- Preview panel appears when date/time is parsed, showing surrounding events.
- Panel auto-hides when text no longer resolves to a date/time.
- All-day and past events are excluded.
- Calendar query respects hidden calendar settings.

## Acceptance Criteria
- Typing "meeting tomorrow at 2pm" shows the preview panel with events around 12:00–17:00 on tomorrow.
- Panel displays up to 6 events, sorted chronologically.
- When more than 6 events exist, the 6 closest to the parsed start time are shown.
- Panel hides shortly after the user deletes the date/time text (gated by the 300ms lookup debounce).
- All-day events and past events do not appear in the preview.
- Only events from visible (non-hidden) calendars are shown.
- No jank or ANRs from the query loop during rapid typing.

## Dependencies
- Depends on steps 8–11 (foundation, parsing, highlighting, calendar integration).
- Uses existing `CalendarRepository` and `CalendarPreferences` infrastructure.
