# Phase 07 Result: Notification Delivery

- Implementation baseline: `dfb5ff94fe3cc3edb6c2974ac8b35fae5fc77c86`
- Branch: `codex/phase-07-notification-delivery`
- Implementer model: `gpt-5.6-luna` / `max`
- Reviewed: 2026-08-14 (Asia/Seoul)

## Outcome

Phase 07 adds a notification-provider boundary and a production SES email path without treating provider acceptance as user acknowledgement.

- Added an explicit `NotificationSender` port for EMAIL/PUSH and an SES v2 adapter.
- Persisted each delivery attempt with a correlation ID, delivery key, channel, recipient, provider message ID, result classification, error, and completion time.
- Added a PostgreSQL row-lock delivery claim and active-delivery uniqueness guard so concurrent workers do not invoke the provider twice for the same completed delivery.
- Kept unavailable EMAIL/PUSH providers retryable so SQS messages remain available for retry; unsupported channels are recorded as terminal failures.
- Transitioned successful sends to `DELIVERED`, never directly to `ACKNOWLEDGED`.
- Added conditional least-privilege SES IAM: email-disabled plans contain no SES statement, while enabled plans grant only `ses:SendEmail` on the configured Seoul SES identity ARN.
- Wired notification configuration into the deployed WAS template without enabling live email by default.

## Independent verification

| Verification | Result |
|---|---|
| `backend\\gradlew.bat clean test bootWar --no-daemon` | PASS — 59 tests, 0 failures, 0 errors, 3 environment-gated skips. |
| Local ephemeral PostgreSQL `16.15` (`server_version_num=160015`) | PASS — all 3 PostgreSQL integration tests ran, with 0 failures/errors/skips and three Flyway migrations. |
| Two-worker duplicate-delivery test | PASS — the second worker remained blocked while the first held the production row lock; after release, Provider invocation count was exactly 1 and the second result was `ALREADY_DELIVERED`. |
| `NotificationPersistenceIntegrationTest`, five independent reruns | PASS — all five H2 migrated-schema runs completed successfully. |
| SES request contract | PASS — from address, recipient, subject, body, and returned provider message ID were verified. |
| WAR inspection | PASS — `ROOT.war` contains SES v2 and `V3__phase_07_notifications.sql`; it contains no H2 runtime JAR. |
| Terraform format/init/validate | PASS — backendless validation completed without `apply`. |
| Backendless Terraform plans | PASS — email-disabled and email-enabled development plans each reported 65 creates, HA reported 64 creates; no changes or destroys. |
| Terraform notification probes | PASS — disabled policy emitted no SES statement; enabled policy emitted exactly one identity-scoped `ses:SendEmail` statement; invalid-region identity and incomplete enabled settings were rejected. |
| Retained network/profile probes | PASS — local, invalid `/24`, duplicate-AZ, and non-Seoul-AZ inputs were rejected; the alternate valid `/16` input planned successfully. |

## Review corrections

The final implementation resolves all findings from the first Codex review:

1. Concurrent delivery uses a production PostgreSQL `FOR UPDATE` claim and has a deterministic two-worker PostgreSQL 16 test.
2. Missing known providers are retryable and do not cause SQS deletion; unknown channels remain terminal.
3. SES permission is conditional and scoped to the exact configured identity rather than `*`.
4. Attempt persistence and reminder transitions are exercised against migrated H2 and PostgreSQL schemas.
5. The SES adapter has a concrete request/response contract test.
6. This document is the single coherent evidence ledger; the Codex verdict is recorded separately in `review.md`.

## Failed-command history

- The first focused notification run failed because H2 did not support the initial PostgreSQL partial-index syntax and test fixtures did not supply the new locked state. The schema was changed to a portable nullable unique active key and fixtures were corrected.
- A later focused run failed because two assertions still expected the raw JSON payload instead of the deterministic delivery key. The assertions were corrected.
- The first persistence-class run failed because scheduler fixture wiring and disabled-sender setup were incomplete. The test configuration was completed.
- A clean full build initially reported three persistence failures because newly created outbox rows could be microscopically later than the Java clock. Tests now set `available_at` explicitly in the past; five focused reruns passed.
- An exact PostgreSQL test filter initially found no test because an existing `@Test` annotation had been lost. The annotation was restored and the test ran successfully.
- The first PostgreSQL class run had one outbox timing failure while the duplicate-provider and `SKIP LOCKED` tests passed. PostgreSQL fixtures now set `available_at` explicitly; the full class subsequently passed.
- Early Terraform wrappers failed because of argument placement, an uninitialized S3 backend, or sensitive JSON-plan elision. Corrected isolated backendless plans and a non-sensitive policy-expression probe supplied the final evidence.

## Reliability boundary

The row lock prevents simultaneous workers from sending the same completed delivery concurrently. It does not claim crash-proof exactly-once delivery if the process stops after SES accepts a message but before the database transaction commits. Phase 08 owns recovery, replay safety, and operational handling for that unavoidable provider/transaction boundary.

## Safety

No Terraform apply, AWS resource mutation, live SES send, SES identity change, credential creation, or secret persistence occurred during Phase 07. Local PostgreSQL verification used an ephemeral container that was removed after the test.
