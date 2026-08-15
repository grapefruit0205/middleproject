# Phase 12 Result

- Baseline commit: `0e2825e5b2d447617dd5203bb4960ae3abca5322`
- Implementation branch: `codex/phase-12-trip-domain-mcp-foundation`
- Reviewed worktree HEAD at repair time: `8feb83866963301acc0ddd015db342b9e87c8a75`
- Review verdict addressed: `REVISE` (docs/phases/phase-12/review.md) — attempt 1 findings
- Status: Codex final review PASS

## Review findings addressed

### P1: Route trip MCP tools before reminder-only lookup
`McpAdapterController.call()` now dispatches `create_trip_draft`, `answer_trip_question`,
`confirm_trip`, `cancel_trip` before any reminder-only argument access. Reminder ownership
access checks (`reminders.find(id, user)` for `get_reminder` / `update_reminder` /
`cancel_reminder` / `get_delivery_status`) are unchanged. Covered by
`TripMcpIntegrationTest` (create → answer → confirm → cancel flow) and existing
`McpAdapterIntegrationTest` ownership tests.

### P1: Preserve transaction semantics and correct the failure fixtures
`TripConfirmationIntegrationTest.failedConfirmationLeavesNoPartialState` now inserts a
deterministic `trip_outbox` row for the exact `scheduler_version` the confirmation would
write, instead of failing without a fixture. `failedConfirmationRollsBackToPreviousStateAfterDeterministicCollision`
keeps its version-3 collision. Both now assert the Trip returns to `DRAFT` (not
`AWAITING_CONFIRMATION`), that pre-existing `DRAFT_CREATED` / `DRAFT_ANSWERED` events
survive, and that the failed attempt adds no `AWAITING_CONFIRMATION` / `CONFIRMED` event,
no NotificationPolicy, no Reminder, and no new outbox row. Production code was not changed
to preserve a partial state.

### P1: Test domain events by type instead of deleting valid history
All event assertions were rewritten to assert per-type counts. A successful confirmation
records exactly one `DRAFT_CREATED`, one `AWAITING_CONFIRMATION`, and one `CONFIRMED`
event. Idempotent replay keeps each type at one and does not duplicate Reminder,
NotificationPolicy, or outbox rows. Failed-confirmation assertions distinguish pre-existing
events from events the failed attempt would have created.

### P1: Exercise idempotency conflicts in the intended scope
`TripConfirmationIntegrationTest.sameKeyWithDifferentPayloadIsRejected` now sends a
different confirmation payload against the same Trip and same confirmation idempotency
key, asserting 409 and literal row counts (1 trip, 1 reminder, 1 policy, 3 events,
1 outbox). The draft-key conflict is covered separately by
`draftKeyConflictRejectsSecondDraftWithoutCreatingRows` (1 trip, 0 reminders, 0 policies,
1 event, 0 outbox). The MCP retry test
(`TripMcpIntegrationTest.confirmRetryReturnsSameResultAndNoDuplicateRows`) replays the
same confirmation and asserts one of each row. Note: raw JSON string equality across an
idempotent replay is not asserted because the stored response serializes
`OffsetDateTime` in UTC while the live response retains the `+09:00` offset; semantic
fields (id, status, confirmationId, version) are compared instead.

### P1: Scope all Trip reads to the configured Demo Owner
`TripService.all()` now uses `trips.findAllByOwner(demoOwner.ownerId())` and
`TripService.find(id)` uses `trips.findByIdForOwner(id, demoOwner.ownerId())`. The
unscoped `findAll` / `findById` / `delete` repository methods were removed.
`TripConfirmationIntegrationTest.listAndGetAreScopedToTheConfiguredDemoOwner` and
`TripRestControllerTest.listAndGetAreScopedToTheConfiguredDemoOwner` insert a row with a
different owner and assert it is absent from list and unavailable by ID (404). No
client-supplied owner ID is accepted (`demoOwnerIsUsedAndClientProvidedOwnerIsIgnored`).

### P2: Keep REST and MCP validation in the shared application boundary
`TripService.createDraft` now validates nonblank `departure` / `destination` (max 200),
required `departureAt`, and `returnAt >= departureAt` before any persistence. Both REST
(`TripController`) and MCP (`McpAdapterController`) go through the service, so both
adapters receive identical behavior. `returnAt` remains optional in REST (`DraftRequest`)
and is now optional in the MCP `create_trip_draft` schema and `validate()` — a draft may
not know its return time. MCP validation failures return a JSON-RPC error and persist
nothing (`TripMcpIntegrationTest.validationErrorsReturnJsonRpcErrorWithoutPersistingTrip`,
`draftCanBeCreatedWithoutReturnAt`).

## Additional fix discovered during repair
The cancel flow wrote a DELETE outbox row with `expected_version = version - 1`,
`scheduler_version = version`, which violates the `trip_outbox_version_check` constraint
(`scheduler_version = expected_version` for DELETE). `TripService.cancel()` now writes
`expected_version = scheduler_version = cancelled.version()`, matching the existing
`ReminderService` DELETE convention (`enqueue(id, "DELETE", v, v)`). This surfaced only
because the earlier event-count assertions failed before the cancel assertions ran.

## Verification

### Focused Phase 12 classes (after fixes)
Command (PowerShell):

```powershell
.\gradlew.bat test `
  --tests "com.middleproject.reminder.TripDomainTest" `
  --tests "com.middleproject.reminder.TripRestControllerTest" `
  --tests "com.middleproject.reminder.TripMcpIntegrationTest" `
  --tests "com.middleproject.reminder.TripConfirmationIntegrationTest" `
  --no-daemon
```

Result: `BUILD SUCCESSFUL` — 25 tests, 0 failed (11 TripConfirmationIntegrationTest +
4 TripDomainTest + 6 TripMcpIntegrationTest + 4 TripRestControllerTest). The five-class
run including `McpAdapterIntegrationTest` also passed in a separate invocation.

### Full suite
Command: `.\gradlew.bat clean test --no-daemon`

Codex reran this command with `POSTGRES_TEST_URL`, `POSTGRES_TEST_USERNAME`, and
`POSTGRES_TEST_PASSWORD` supplied only to the process from a temporary local PostgreSQL
16.15 cluster. Result: `BUILD SUCCESSFUL` — 120 tests, 0 failures, 0 errors, 0 skipped.
`Postgres16IntegrationTest` executed all 8 tests with 0 failures and verified seven
successful Flyway migrations, including V7. The temporary database, generated password
file, log, and data directory were removed after PostgreSQL was stopped.

### Whitespace
Command: `git diff --check`

Result: exit code 0, no output (`DIFF_CHECK_OK`).

## Remaining limitations
- The four focused Trip test classes use H2 in PostgreSQL compatibility mode. The real
  PostgreSQL 16.15 run separately proves V7 migration compatibility and executes the
  existing production concurrency, idempotency, ownership, and delivery integration tests.
- The idempotent MCP replay is compared on semantic fields rather than raw JSON strings
  because stored responses serialize offsets in UTC (see P1 above).
- No AWS/Terraform changes were made. Codex generated one process-local PostgreSQL test
  password, did not print or persist it in Git, and removed its temporary file after use.

## Git state
- Command Code created no commits. Before Codex final review, all implementation changes
  remained uncommitted and `HEAD` was `8feb83866963301acc0ddd015db342b9e87c8a75`.
