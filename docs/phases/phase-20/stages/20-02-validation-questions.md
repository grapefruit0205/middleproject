# Phase 20.2 — Draft validation and one-question conversation boundary

## Objective

Validate a partially collected day-plan draft without writing to PostgreSQL or calling a
place/transit provider. Return one deterministic next question so the MCP conversation can
collect missing values in a predictable order.

## Required behavior

- Default the user-facing timezone to `Asia/Seoul`; reject an invalid IANA timezone.
- Require a plan date, an explicit origin label, and at least one schedule item.
- For each item, collect title, time type, fixed/deadline start when applicable, place, a
  duration or end time, and the requested travel mode.
- Reject negative durations, reversed times, items outside the selected plan date, and
  overlapping fixed-time items.
- Return exactly one `nextQuestion`/`questions[0]` when a missing field is present.
- Return a stable issue code/path for invalid data so the MCP adapter can explain the issue
  without leaking persistence details.
- Keep the operation pure: no DB writes, provider calls, notifications, alarms, or background
  location access.

## Gate

`DayPlanValidationServiceTest` must pass. The service must remain independent of repository,
HTTP, provider, notification, and scheduler adapters.
