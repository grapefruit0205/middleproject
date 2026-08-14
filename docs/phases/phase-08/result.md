# Phase 08 Result: Reliability and Idempotency

- Baseline: `345f9fe2b0c995a6133d0ce6dc4654b6f9cab54f`
- Branch: `codex/phase-08-reliability-idempotency`
- Implementer model: `gpt-5.6-luna` / `max`
- Reviewed: 2026-08-14 (Asia/Seoul)

## Outcome

Phase 08 makes duplicate, retry, partial-failure, and concurrent-update paths recoverable without claiming exactly-once delivery.

- Idempotency records now distinguish `IN_PROGRESS`, `COMPLETED`, and `FAILED`, retain bounded failure attempts and errors, and allow the same request to retry after a failed action.
- A 30-second lease makes abandoned `IN_PROGRESS` reservations reclaimable. Each reserve or claim receives a new fencing token; stale workers cannot complete or fail a newer claim.
- Successful idempotency completion participates in the surrounding application transaction, so a commit-phase failure rolls back both the business write and `COMPLETED` response.
- Identical live requests wait for a bounded period and reuse the committed response. The approved same-key contract remains two identical HTTP 200 responses and one aggregate row for the tested concurrent request.
- Existing Outbox reconciliation, notification attempt history, delivery receipts, optimistic versions, SQS/DLQ configuration, and bounded retry classifications are exercised as one failure matrix.
- The DLQ runbook validates queue identities, requires explicit backlog/rate approval, uses the actual SQS move-task rate option, and defines cancellation/stop conditions.

## Independent verification

| Verification | Result |
|---|---|
| `backend\\gradlew.bat clean test bootWar --no-daemon` | PASS — 75 tests, 0 failures, 0 errors, 7 PostgreSQL-environment skips. |
| Fresh PostgreSQL 16.15 | PASS — 7 tests, 0 failures, 0 errors, 0 skips; 5 Flyway migrations. |
| Concurrent same-key HTTP create | PASS on PostgreSQL — both responses HTTP 200 with identical bodies and one event row. |
| Outer transaction failure/retry | PASS — business write and completion roll back together; failed action is retryable and an expired reservation can be reclaimed. |
| Lease fencing | PASS — a stale claimant cannot overwrite or fail the newer completion, and its business transaction rolls back. |
| V4→V5 legacy upgrade | PASS — a pre-existing 200-character `IN_PROGRESS` key migrates to a bounded claim token. |
| WAR inspection | PASS — V4/V5 migrations included; no H2 runtime JAR. |
| Terraform format/init/validate | PASS — backendless, with no apply. |
| AWS CLI skeleton validation | PASS — `MaxNumberOfMessagesPerSecond` and cancel-task `TaskHandle` match the runbook. |
| Scope, whitespace, secret, generated-artifact checks | PASS — 11 changed files, 0 out-of-scope files, 0 high-confidence secret findings, 0 forbidden artifacts. |

## Failure and correction history

- The first implementation weakened the accepted concurrent same-key test to allow one HTTP 409. Codex rejected the regression; the final test again requires two identical HTTP 200 responses and one row.
- The first implementation committed reservations without a recovery lease. The second added lease recovery and production transaction tests.
- The second implementation made `complete()` a new transaction and omitted claim fencing. Codex rejected the premature success commit and stale-worker overwrite window; the final implementation makes completion atomic with the business transaction and conditions completion/failure on the current token.
- Early new rollback fixtures failed twice because the test event insert omitted required columns. The fixture was corrected before the clean suite passed.
- The implementation agent accidentally wrote Git diagnostic output to a root file named `$null`. The orchestration scope guard blocked the run; Codex inspected and removed that agent-created file, recorded the recovery in orchestration history, and verified all remaining paths were allowed.
- Codex's first maximum-length legacy migration probe failed on PostgreSQL because `legacy-<200-character-key>` exceeded `varchar(36)`. The migration now uses a fixed legacy marker, and the focused probe plus all 7 PostgreSQL tests pass.
- The initial DLQ example used the nonexistent `--max-number-of-messages` option and described a single-message move. The final runbook uses `--max-number-of-messages-per-second`, states that the task applies to the eligible backlog, and documents cancellation.

## Reliability boundaries

- Provider acceptance and the database transaction are not a distributed atomic operation; Phase 07/08 do not claim exactly-once notification delivery.
- The idempotency wait is bounded and a lease may expire during a stalled request. Fencing prevents an expired claimant from committing stale application/idempotency state when it resumes.
- Idempotency, delivery-receipt, and notification-attempt records currently have no automatic retention cleanup; they remain until a separately authorized policy is implemented.

## Safety

No Terraform apply, AWS mutation, live SQS redrive, live provider send, credential/secret operation, or remote deployment occurred. PostgreSQL verification used an ephemeral local container that was removed afterward.
