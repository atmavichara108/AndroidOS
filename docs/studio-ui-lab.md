# Studio UI Lab

The Studio slice is a debug-only, side-effect-free Compose preview under
`ru.rudra.androidos.pa.studio`. `StudioActivity` is included only in debug
builds and uses a separate launcher entry point. This is not shared production
UI yet.

## Scenarios

- `Empty inbox` checks empty-state hierarchy.
- `Captured note` checks the normal approval surface.
- `Long transcript` checks wrapping and dense text.
- `Storage error` checks recoverable failure copy.

The preview never accesses Room, starts recording, schedules alarms, or writes
changes. `SIMULATED` is always visible so a preview cannot be mistaken for live
user data. Integration into app navigation must happen only after the
production UI state/ViewModel boundary is agreed with the PA runtime owner.

Actions run through a deterministic pure reducer. Approvals are deduplicated,
retry resolves the simulated error, and reset returns to the selected fixture.
Replay is session-only: it is an inspector aid and is not a durable export or
persisted event log.
