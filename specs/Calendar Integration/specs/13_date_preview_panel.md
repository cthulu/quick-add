# Step 13: Date Preview Panel

## Goal
When the user types text that resolves to a date/time, show a floating preview panel above the input area displaying existing calendar events around that time. This helps users spot scheduling conflicts before saving.

## Scope
- Query existing events from visible calendars around the parsed time window.
- Display matching events in a scrollable panel above the input field.
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

1. **Query**: the provider's `Instances` time-range (midnight to midnight of the identified day) from all visible calendars (the set shown in the dropdown, excluding hidden calendars). `Events` holds one row per event series, so a recurring event's later occurrences would never match a day query on it; `Instances` holds one row per occurrence (the provider expands recurrences), which covers one-time and recurring events uniformly.
2. **Exclude all-day events** (via the joined `ALL_DAY` column).
3. **Dedupe moved occurrences**: if the provider returns both a master occurrence and the exception that replaced it, keep only the exception (keyed on `ORIGINAL_ID` + `ORIGINAL_INSTANCE_TIME`).
4. **Candidate buffer**: keep events that overlap the parsed window ±2 hours. For `14:00–15:00`, that is `12:00–17:00`.
5. **Exclude past events**: drop events where `endTime < now`.
6. **Sort**: Chronologically, earliest first.
7. **Limit**: If ≤6 candidates, show all. If >6, keep the 6 closest to the parsed window, then re-sort chronologically.
8. **Empty results**: Keep the panel hidden (`GONE`).

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
| `calendar/EventPreviewAdapter.kt` | `ListAdapter` for event rows (diff key = event id + start time, since one event can appear twice in a day) |
| `res/layout/layout_event_preview_panel.xml` | Panel container: day header + `RecyclerView` |
| `res/layout/layout_event_preview_row.xml` | Single event row layout |

### Modified Files
| File | Changes |
|---|---|
| `calendar/CalendarRepository.kt` | Add `findEventsOnDay(dayStartMillis, dayEndMillis, calendarIds)` querying the `Instances` time-range (expands recurring events) and mapping occurrences |
| `calendar/CalendarQuickAddActivity.kt` | Add 300ms lookup debounce; query & populate panel on `RESOLVED`; hide on other states |
| `res/layout/layout_calendar_floating_widget.xml` | Insert preview panel above the text input |

### Performance
- Query runs on `Dispatchers.Default` (off main thread).
- 300ms debounce prevents excessive queries during rapid typing.
- Cancel previous job on new input to avoid stale results.
- If calendars finish loading after the first lookup ran with an empty set, the lookup re-runs with the current input.

### Testing
- `EventPreviewSelectionTest` covers buffer filtering, past-event exclusion, chronological sorting, and the closest-six trim.
- `CalendarRepositoryTest` covers `findEventsOnDay`: all-day exclusion, field mapping, recurring-event expansion, moved-exception dedupe, chronological sorting, and the empty-calendar early return.

## Deliverables
- Preview panel appears when date/time is parsed, showing surrounding events.
- Panel auto-hides when text no longer resolves to a date/time.
- All-day and past events are excluded.
- Calendar query respects hidden calendar settings.

## Acceptance Criteria
- Typing "meeting tomorrow at 2pm" shows the preview panel with events around 12:00–17:00 on tomorrow.
- A recurring event (e.g., a weekly meeting whose first occurrence is in the past) appears when an occurrence falls in the buffer.
- Panel displays up to 6 events, sorted chronologically.
- When more than 6 events exist, the 6 closest to the parsed start time are shown.
- Panel hides shortly after the user deletes the date/time text (gated by the 300ms lookup debounce).
- All-day events and past events do not appear in the preview.
- Only events from visible (non-hidden) calendars are shown.
- No jank or ANRs from the query loop during rapid typing.

## Dependencies
- Depends on steps 8–11 (foundation, parsing, highlighting, calendar integration).
- Uses existing `CalendarRepository` and `CalendarPreferences` infrastructure.
