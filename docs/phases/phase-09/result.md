# Phase 09 Result

## Outcome

The existing Reminder application service is exposed through exactly six MCP tools:

- `create_reminder`
- `list_reminders`
- `get_reminder`
- `update_reminder`
- `cancel_reminder`
- `get_delivery_status`

The adapter uses only the server-authenticated `Principal`; caller-controlled identity headers are ignored. Owner-scoped repository operations prevent cross-user reads and mutations, and ownerless REST-created reminders are not exposed through MCP.

MCP initialization validates the `2025-03-26` lifecycle payload, negotiates unsupported client versions to the supported server version, accepts `notifications/initialized`, publishes closed input schemas, and validates UUID, idempotency-key, and bounded integer inputs. Tool execution reuses the existing idempotent `ReminderService`; retries return the same result without creating another Reminder.

V6 adds Reminder ownership and durable MCP audit records. Audit references use `ON DELETE SET NULL`, and unbounded JSON-RPC identifiers or rejected tool names remain auditable. Unexpected internal failures are logged and returned as stable, sanitized JSON-RPC errors. Delivery-status keys are explicitly mapped so H2 and PostgreSQL return the same contract.

## Changed paths

- `backend/src/main/java/com/middleproject/reminder/application/McpAuditService.java`
- `backend/src/main/java/com/middleproject/reminder/application/McpReminderQueryService.java`
- `backend/src/main/java/com/middleproject/reminder/application/ReminderService.java`
- `backend/src/main/java/com/middleproject/reminder/infrastructure/persistence/JdbcDeliveryStatusRepository.java`
- `backend/src/main/java/com/middleproject/reminder/infrastructure/persistence/JdbcMcpAuditRepository.java`
- `backend/src/main/java/com/middleproject/reminder/infrastructure/persistence/JdbcRepositories.java`
- `backend/src/main/java/com/middleproject/reminder/port/DeliveryStatusRepository.java`
- `backend/src/main/java/com/middleproject/reminder/port/McpAuditRepository.java`
- `backend/src/main/java/com/middleproject/reminder/port/ReminderRepository.java`
- `backend/src/main/java/com/middleproject/reminder/web/McpAdapterController.java`
- `backend/src/main/resources/db/migration/V6__phase_09_mcp_adapter.sql`
- `backend/src/test/java/com/middleproject/reminder/McpAdapterIntegrationTest.java`
- `backend/src/test/java/com/middleproject/reminder/Postgres16IntegrationTest.java`

## Final verification

1. `backend\gradlew.bat clean test bootWar --no-daemon` with a fresh PostgreSQL 16.15 database: PASS.
2. Test reports: 17 suites, 86 tests, 0 failures, 0 errors, 0 skipped.
3. `Postgres16IntegrationTest`: 8 tests, 0 failures, 0 errors, 0 skipped; Flyway V1-V6, ownership, retry idempotency, audit persistence, and audit-reference deletion behavior executed on PostgreSQL 16.15.
4. `McpAdapterIntegrationTest`: 10 tests covering initialization, lifecycle notification, the six closed schemas, input validation, authenticated identity, spoof resistance, authorization, retry, audit, deletion, sanitized errors, and stable delivery-status fields.
5. `ROOT.war`: Flyway V1-V6 present; H2 absent from production libraries.
6. `git diff --check`, Phase 09 path scope, generated-artifact, and secret scans: PASS.
7. The temporary PostgreSQL container was removed. No AWS resource, credential, Terraform state, or live deployment was changed.
