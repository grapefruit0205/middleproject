# Phase 20.4 — Read-only full-day timeline preview

## Objective

Combine fixed schedule events and the Stage 20.3 travel legs into the ordered timeline shown to
the user before any write or notification scheduling.

## Required behavior

- Preserve place-selection, provider-unavailable, and invalid-timeline states; never display a
  partial result as a confirmed plan.
- Interleave travel and event entries in Asia/Seoul chronological order.
- Show the notification time for each event using the requested lead or the 15-minute default.
- Expose whether a wake-up system alarm was requested, while keeping its creation user-driven.
- Carry provider/source/handoff provenance into travel entries.
- Remain read-only: no day-plan rows, notification rows, outbox rows, FCM calls, or scheduler
  operations.

## Gate

`DayPlanPreviewServiceTest` and the complete Gradle test suite must pass before Stage 20.5.
