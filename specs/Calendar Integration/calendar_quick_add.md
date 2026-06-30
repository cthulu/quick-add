# Calendar Quick Add Specs Index

This index links the Calendar Quick Add implementation specs in execution order and clarifies dependencies for AI agents implementing the work iteratively.

## Ordered Steps
1. `specs/8_calendar_quick_add_foundation.md`
2. `specs/9_natural_language_parsing_with_natty.md`
3. `specs/10_realtime_parsing_and_highlighting.md`
4. `specs/11_calendarcontract_integration_and_permissions.md`
5. `specs/12_testing_performance_and_rollout.md`

## Dependency Graph
- Step 8 is required before all later steps.
- Step 9 depends on Step 8 parser interfaces and draft models.
- Step 10 depends on Step 9 parse outputs (`matchedRanges`, `ParseState`, parsed datetime).
- Step 11 depends on Step 9 parse state and Step 10 UI state behavior for save enablement.
- Step 12 depends on all prior steps.

## Agent Implementation Notes
- Keep all parsing on-device and off the main thread.
- Preserve Kotlin + JVM 21 constraints and Android SDK 36 target.
- Maintain compatibility with existing Keep quick-add trigger flow.
- Implement in small, mergeable PRs aligned to each step.
- Do not skip acceptance criteria; each step should be verifiable independently.

## Suggested PR Strategy
- PR 1: Step 8 only
- PR 2: Step 9 only
- PR 3: Step 10 only
- PR 4: Step 11 only
- PR 5: Step 12 only

This sequencing keeps risk low and allows rollback at feature boundaries.
