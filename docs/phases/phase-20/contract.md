# Phase 20 Contract · Daily Itinerary MVP

## Core invariants

1. All persisted timestamps are timezone-aware and interpreted in `Asia/Seoul` for user-facing day plans.
2. A preview is read-only. No confirmed DayPlan, Reminder, NotificationPolicy, Scheduler outbox entry, or Android mutation is created by preview.
3. A confirmation is explicit, idempotent, owner-scoped, and atomically persists the plan, items, travel legs, and notification policies for time-anchored items. Flexible items are stored without an invented route time or notification.
4. A cancelled or superseded item remains as a `CANCELLED` record and cannot produce a new notification. Consumers re-check status and version immediately before delivery.
5. The wake alarm is a user-controlled system-clock action. Ordinary schedule items use app Notification policies, not system clock alarms.
6. Route estimates distinguish scheduled/future data from real-time arrival data and never claim reservations or guaranteed arrival.
7. Existing Trip, Reminder, MCP authentication, idempotency, outbox, and device-token ownership rules remain unchanged.

## Domain shape

`DayPlan` owns ordered `ScheduleItem` records and `TravelLeg` records. A `ScheduleItem` contains an owner, local date, title, place reference, fixed/flexible time semantics, duration, status, order, and version. A `TravelLeg` connects adjacent items or the saved origin to an item and stores mode, estimate, buffer, provider provenance, and fetched-at time.

## Required Stage 20.1 output

- Domain records/enums with validation and transition rules.
- PostgreSQL Flyway migration(s) for the domain shape and indexes.
- Ports/repositories sufficient for owner-scoped insert/read/update of the new records.
- Focused domain and repository contract tests.
- No MCP adapter, Android UI, external provider, Terraform, or unrelated refactor in Stage 20.1.

## Device and MCP acceptance boundary

- `preview_day_plan` is read-only and accepts only a bounded serialized draft.
- `confirm_day_plan` requires a confirmation identifier and idempotency key; it
  returns the persisted plan, preview, and reminder identifiers.
- `cancel_day_plan_item` requires an owner-scoped plan id, sequence, expected plan
  version, and idempotency key; a stale version returns a conflict.
- Paired devices may read only their owner’s day plans and may cancel an item via
  the versioned REST endpoint. The device never receives an MCP credential.
- The Android companion displays the plan timeline and notification metadata; it
  does not create a system wake alarm for ordinary schedule items.
