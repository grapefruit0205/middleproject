# Phase 20.3 — Place resolution and read-only travel-leg preview

## Objective

Resolve explicit origin and destination labels to confirmed coordinates and build a read-only
travel-leg timeline between fixed schedule anchors.

## Required behavior

- Reuse the existing place discovery boundary; if a query returns multiple candidates, return a
  selection request and stop before any route provider call.
- Accept explicitly confirmed coordinates without re-resolving them.
- Normalize common Korean/English modes (`지하철`, `버스`, `자차`, `KTX`, etc.) to stable mode
  identifiers.
- Call only the read-only route provider boundary. Do not persist routes, plans, reminders, or
  notification policies.
- Compute arrival with a fixed 10-minute safety buffer and derive departure from the provider
  duration. Reject a leg that cannot fit after the preceding fixed item.
- Return provider/source/handoff provenance and never describe an estimate as a booking or
  guarantee.
- Fail closed when a provider is disabled, empty, malformed, or unsupported.

## Gate

`DayPlanRoutePreviewServiceTest` and the complete Gradle test suite must pass. The service must
remain independent of persistence and scheduler adapters.
