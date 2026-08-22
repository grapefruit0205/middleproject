# Stage 20.5 — Explicit confirmation and notification scheduling

## Objective

Persist a complete, user-confirmed day plan and create its notification schedule as one
idempotent transaction. A preview alone never creates database rows, reminders, scheduler
outbox messages, or a device wake alarm.

## Contract

- `DayPlanConfirmationService.confirm` requires an explicit confirmation identifier and an
  idempotency key.
- The route provider is called once. The typed route result is the source of truth for both the
  presentation timeline and persisted `travel_legs` provenance.
- State transitions are `DRAFT -> PROPOSED -> CONFIRMED` with optimistic version checks.
- Each fixed schedule item creates one event, one `PUSH` policy, one owner-scoped reminder and
  one `UPSERT` scheduler-outbox row. The due time is `starts_at - lead_minutes`.
- Repeating the same request returns the stored result without duplicate plan, reminder, or
  outbox rows. Reusing the key with a different payload is rejected.
- The wake-up alarm flag is retained in the returned preview only; Android alarm creation is a
  client concern and is not performed by the server.
- Scheduled push delivery resolves the owner from the reminder first, falling back to the trip
  owner for legacy trip reminders.

## Verification

- `DayPlanConfirmationServiceIntegrationTest` proves atomic persistence, owner scoping and
  idempotent replay.
- The full Gradle test suite must pass before advancing to Stage 20.6.
