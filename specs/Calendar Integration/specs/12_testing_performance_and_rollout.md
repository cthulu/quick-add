# Step 12: Testing, Performance, and Rollout

## Goal
Harden the feature with automated tests.

## Scope
- Unit, integration, and UI tests.

## Technical Details

### Unit Tests
Cover:
- Normalization aliases (`tomm`, `tmrw`, spacing variants)
- Parse state classification (`NONE/PARTIAL/RESOLVED/ERROR`)
- Title extraction from mixed input
- Timezone conversion correctness

Test phrases (minimum set):
- "tomorrow"
- "tomm"
- "in 1 day"
- "next saturday"
- "at 3pm"
- "Dinner with Sam tomorrow at 7pm"

### Integration Tests
- `DateParserService` + UI state pipeline
- Calendar insert repository/service with mocked resolver
- Permission branching behavior

### UI Tests
- Typing updates highlight spans correctly
- Red highlight appears only on date/time ranges

## Deliverables
- Automated test suite updates.

## Acceptance Criteria
- Test suite passes in CI.
