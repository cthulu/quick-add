# Step 11: CalendarContract Integration and Permissions

## Goal
Create Android calendar events from parsed quick-add input, including permission handling and calendar selection fallback.

## Scope
- Request and handle calendar permissions.
- Insert event using `CalendarContract`.
- Default to primary calendar where possible.
- Confirm event details before insert.

## Non-Goals
- Advanced recurrence support (can be future step).

## Technical Details

### Permissions
Handle runtime permissions:
- `android.permission.WRITE_CALENDAR`
- `android.permission.READ_CALENDAR` (if needed for calendar lookup)

Flow:
1. On save action, check permission.
2. If denied, request permission with rationale.
3. If permanently denied, show guidance to app settings.

### Calendar Selection
- Query calendars and prefer:
  1. Primary visible calendar
  2. First writable visible calendar
- If no writable calendar exists, show user-facing error state.

### Event Insert
Use `ContentResolver.insert(CalendarContract.Events.CONTENT_URI, values)` with:
- `DTSTART`
- `DTEND` (optional if absent; use default duration policy)
- `TITLE`
- `EVENT_TIMEZONE`
- optional `DESCRIPTION` / `EVENT_LOCATION` if available later

Default duration policy (if no end parsed):
- e.g., 60 minutes from start (configurable constant)

### User Confirmation
Before insertion show confirmation summary:
- Title
- Parsed date/time
- Target calendar (if available)
- Confirm / Cancel actions

### Error Handling
- Permission denied
- Parse unresolved (`ParseState != RESOLVED`)
- Insert failure (`null` URI or exception)

All failures should present actionable, user-friendly message.

## Deliverables
- End-to-end event creation flow from quick add popup.
- Runtime permission UX.
- Confirmation dialog and success/failure feedback.

## Acceptance Criteria
- With permission granted and resolved parse, event is inserted successfully.
- Event appears in device calendar app.
- Permission denial path does not crash and is recoverable.
- If parsing is unresolved, save is blocked with clear guidance.
