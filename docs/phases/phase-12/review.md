# Phase 12 Review

- Reviewer: Codex Desktop (`gpt-5.6-sol` / `high`)
- Verdict: PASS
- Implementation baseline: `0e2825e5b2d447617dd5203bb4960ae3abca5322`
- Reviewed worktree HEAD: `8feb83866963301acc0ddd015db342b9e87c8a75`
- Invocation accounting: effective attempt 2 of 2

## Verification evidence

Command Code ran the four focused Phase 12 classes after the repair:

```powershell
.\gradlew.bat test `
  --tests "com.middleproject.reminder.TripDomainTest" `
  --tests "com.middleproject.reminder.TripRestControllerTest" `
  --tests "com.middleproject.reminder.TripMcpIntegrationTest" `
  --tests "com.middleproject.reminder.TripConfirmationIntegrationTest" `
  --no-daemon
```

Result: 25 tests, 0 failures.

Codex created a temporary local PostgreSQL 16.15 cluster, supplied its generated test
credential through process-local environment variables, and ran:

```powershell
.\gradlew.bat clean test --no-daemon
```

Result: `BUILD SUCCESSFUL`, 120 tests, 0 failures, 0 errors, 0 skipped.
`Postgres16IntegrationTest` ran 8 tests with no failures and confirmed Flyway V1 through
V7. Codex stopped PostgreSQL and removed the temporary password file, log, and data
directory after the run.

Codex also checked:

- `git diff --check`: exit 0
- Phase allowlist: 26 changed paths, all under `backend/**` or `docs/phases/phase-12/**`
- changed-file secret scan: no private key, AWS access key, or embedded credential match
- generated artifact scan: no build, WAR, class, PostgreSQL data, or log file in Git status

## Acceptance criteria

1. `TripService.confirm` persists the Trip transition, Trip events, Event,
   NotificationPolicy, Reminder, and Trip outbox row in one transaction.
2. Deterministic outbox collisions roll the Trip back to `DRAFT` and leave no partial
   confirmation event, policy, or reminder.
3. Draft and confirmation idempotency tests prove that retries do not duplicate Trip,
   Reminder, NotificationPolicy, event, or outbox rows. A reused key with a different
   payload returns conflict.
4. REST and MCP call the same `TripService`. The service validates locations and times,
   and both adapters accept an unknown return time.
5. The state machine and version checks reject invalid transitions and stale cancel
   requests. Trip list and get operations use the configured Demo Owner.
6. The implementation stores structured draft answers and identifiers. It does not add
   raw chat logging, credentials, Secrets, AWS changes, or Terraform changes.

## Prior REVISE findings

The repair resolved each attempt 1 finding:

- Trip MCP tools dispatch before reminder-only lookup.
- Confirmation failure tests use deterministic collisions and assert full rollback.
- Tests count each event type and preserve valid draft history.
- Idempotency conflict tests use the matching operation scope.
- Trip reads use the fixed Demo Owner context.
- Shared service validation covers bounded locations and chronological trip times.

The MCP replay assertion compares business fields because Jackson normalizes stored
`OffsetDateTime` values to UTC. This representation difference does not create duplicate
rows or change the returned Trip state.

## Findings

No blocking or repair finding remains for Phase 12.
