# Phase 20.8 — paired-device day-plan API and Android timeline

## Objective

Expose the confirmed daily itinerary to the paired Android companion and allow the
user to cancel one schedule item with the same owner, idempotency, and optimistic
version guarantees as the MCP boundary.

## Backend contract

- `GET /api/device/day-plans?date=yyyy-MM-dd` returns the paired owner’s plan for
  that date. If `date` is omitted, the server uses `Asia/Seoul` today.
- `GET /api/device/day-plans/{planId}` returns one owner-scoped plan or `404`.
- `POST /api/device/day-plans/{planId}/items/{sequence}/cancel` requires a bearer
  device token, `Idempotency-Key`, and `{"expectedVersion": N}`. It returns the
  revised plan and route preview. A stale version is a `409`.
- All schedule, reminder, and travel-leg rows are owner scoped. A cancelled item’s
  notification is cancelled and a versioned scheduler `DELETE` outbox row is emitted.

## Android behavior

- Refresh reads the day-plan projection along with trips and reminders.
- The paired screen shows a plain timeline with start/end and notification times,
  route legs, status, and a per-item cancel action.
- Cancellation sends a fresh idempotency key and refreshes the projection; no local
  alarm is created for the server-side schedule until the existing reminder policy
  says it is due.
- Timestamp parsing accepts both UTC (`Z`) and offset timestamps (`+09:00`).

## Non-goals

- No home-screen widget or visual redesign in this stage.
- No direct MCP call from Android; MCP remains the ChatGPT orchestration boundary.
- No automatic Android wake alarm. The server only returns the user’s
  `wakeAlarmRequested` decision in the MCP preview.

## Validation

- Backend: `DeviceDayPlanApiIntegrationTest` covers paired read, reminder projection,
  cancellation, version increment, and recomputed preview.
- Android: `DeviceApiClient`/repository/view-model contracts are extended without
  breaking existing fakes. Run `android\gradlew.bat test` and `assembleDebug` on a
  machine with Android SDK configured (`ANDROID_HOME` or `android/local.properties`).
