# Stage 20.6 — Revision, cancellation, and stale-notification protection

## Objective

Allow the conversational flow to remove an itinerary item after confirmation without leaving a
stale notification or stale route timeline behind.

## Contract

- `DayPlanRevisionService.cancelItem` is owner-scoped, idempotent, and guarded by the day-plan
  optimistic version.
- The stored draft context is used to rebuild the remaining route preview before any mutation is
  committed. A failed route/place resolution leaves the existing plan untouched.
- The removed item transitions out of the active schedule, its cancellable reminder transitions
  to `CANCELLED`, and a versioned `DELETE` outbox row is emitted.
- Remaining schedule item identifiers are retained, their sequence is compacted, and travel legs
  are replaced with the recomputed provider result.
- Consumers still re-check reminder status/version before delivery; an older `UPSERT` becomes
  stale after cancellation and cannot create a new notification.
- Removing the only item is rejected; a dedicated whole-plan cancellation command is required.

## Verification

- `DayPlanRevisionServiceIntegrationTest` covers optimistic-version rejection, idempotent replay,
  canceled reminder/outbox creation, and remaining-leg reconstruction.
