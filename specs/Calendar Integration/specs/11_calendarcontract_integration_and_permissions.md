# Step 11: CalendarContract Integration and Permissions

## Goal
Hand parsed quick-add input off to the calendar app as a pre-filled event draft, including read-only permission handling and calendar selection for the draft pre-fill.

## Scope
- Request and handle the `READ_CALENDAR` runtime permission (read-only access for the event preview).
- Open a pre-filled event draft in the calendar app via `ACTION_INSERT`.
- Pre-fill the draft with the user's selected calendar where available.

## Non-Goals
- Writing events directly through `CalendarContract`. The app never creates events itself; the user reviews and saves in the calendar app.
- Advanced recurrence support (can be future step).

## Technical Details

### Permissions
Handle runtime permission:
- `android.permission.READ_CALENDAR`

`WRITE_CALENDAR` is intentionally not requested: the app only reads calendars for the event preview and hands a draft to the calendar app.

Flow:
1. When the calendar quick-add activity opens, check permission.
2. If denied, request permission with rationale.
3. If permanently denied, show guidance to app settings.

### Calendar Selection
- Query all visible calendars on the device (writable and read-only) and prefer:
  1. Last selected calendar (persisted)
  2. First visible calendar
- The selection is only used to pre-fill the draft's `CALENDAR_ID` extra; the calendar app may ignore it.

### Event Draft
Use an `ACTION_INSERT` intent against `CalendarContract.Events.CONTENT_URI` with:
- `TITLE`
- `EXTRA_EVENT_BEGIN_TIME`
- `EXTRA_EVENT_END_TIME`
- optional `CALENDAR_ID` from the selected calendar

Default duration policy (if no end parsed):
- 60 minutes from start (configurable constant)

### Error Handling
- Permission denied
- Parse unresolved (`ParseState != RESOLVED`)
- No app able to handle the draft intent

All failures should present actionable, user-friendly message.

## Deliverables
- End-to-end draft hand-off flow from quick add popup.
- Runtime permission UX.
- Success/failure feedback.

## Acceptance Criteria
- With a resolved parse, submitting opens the calendar app with a pre-filled event draft.
- Permission denial path does not crash and is recoverable.
- If parsing is unresolved, submit is blocked with clear guidance.
