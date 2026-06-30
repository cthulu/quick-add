# Step 12: Testing, Performance, and Rollout

## Goal
Harden the feature with automated tests, performance validation, and phased rollout controls.

## Scope
- Unit, integration, and UI tests.
- Parsing latency validation and regression guardrails.
- Rollout strategy with observability.

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
- Parsed preview updates according to parse state
- Save button enabled only when `RESOLVED`

### Performance Validation
Measure:
- median and p95 parse latency on representative devices/emulators
- dropped frames/jank during fast typing
- memory growth after repeated popup open/close

Targets:
- parse p95 <= 200ms for typical quick-add strings
- no noticeable input lag during typing

### Rollout / Guardrails
- Feature flag for calendar quick add.
- Log parser outcomes and save outcomes:
  - parse state frequency
  - permission denial rate
  - insert success/failure ratio
- Define rollback criteria (e.g., crash increase, parse failure spikes).

## Deliverables
- Automated test suite updates.
- Performance report with measured metrics.
- Feature flag + monitoring hooks.

## Acceptance Criteria
- Test suite passes in CI.
- Performance targets met or documented with follow-up tasks.
- Feature can be enabled progressively with low risk.
