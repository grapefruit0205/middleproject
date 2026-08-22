# Phase 20 result

## Accepted slice

- Natural-language draft model with fixed/flexible time semantics and owner-scoped
  PostgreSQL persistence.
- Read-only route/place preview followed by explicit, idempotent MCP confirmation.
- Per-item PUSH notification policies and scheduler outbox entries.
- Versioned item cancellation that cancels the associated reminder, emits a
  scheduler delete, compacts the timeline, and recomputes travel legs.
- MCP tools: `preview_day_plan`, `confirm_day_plan`, and `cancel_day_plan_item`.
- Paired-device REST read/cancel endpoints and Android timeline display.

## Verification evidence

- `backend\gradlew.bat test --no-daemon --console=plain` — PASS.
- `DeviceDayPlanApiIntegrationTest` — PASS.
- `McpAdapterIntegrationTest` — PASS as part of the full suite.
- `git diff --check` — PASS.
- Android source and JVM contract tests were updated, but this workstation has no
  Android SDK (`ANDROID_HOME`/`ANDROID_SDK_ROOT` are unset), so `android\gradlew.bat
  test` and APK assembly remain an environment-gated handoff.

## Explicit limitations

- Route estimation is provider-backed where configured; it does not promise a
  ticket, reservation, or guaranteed arrival.
- Normal schedule notifications are app/FCM notifications. A wake alarm is a
  separate user-controlled Android action.
- RAG, vector embeddings, and Obsidian integration are outside the project scope.
