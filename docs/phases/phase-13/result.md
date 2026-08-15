# Phase Result

- Phase: 13 — Private-car vertical slice
- Branch: codex/phase-13-private-car-vertical-slice
- Base commit: 1b32fae
- Result commit: recorded by the accepted Phase 13 branch HEAD
- Implementer: DeepSeek V4 Flash via Command Code CLI

## Changed files

New (all under `backend/`):

- `backend/src/main/java/com/middleproject/reminder/domain/GeoPoint.java`
- `backend/src/main/java/com/middleproject/reminder/domain/PrivateCarPlanningInput.java`
- `backend/src/main/java/com/middleproject/reminder/domain/PrivateCarRoute.java`
- `backend/src/main/java/com/middleproject/reminder/domain/ProviderOutcome.java`
- `backend/src/main/java/com/middleproject/reminder/domain/RoutePlan.java`
- `backend/src/main/java/com/middleproject/reminder/domain/RoutePlanRequest.java`
- `backend/src/main/java/com/middleproject/reminder/port/GeocodingPort.java`
- `backend/src/main/java/com/middleproject/reminder/port/RouteProviderPort.java`
- `backend/src/main/java/com/middleproject/reminder/port/PrivateCarRouteRepository.java`
- `backend/src/main/java/com/middleproject/reminder/application/PrivateCarPlanningService.java`
- `backend/src/main/java/com/middleproject/reminder/application/ProviderCallPolicy.java`
- `backend/src/main/java/com/middleproject/reminder/infrastructure/provider/DeterministicGeocodingPort.java`
- `backend/src/main/java/com/middleproject/reminder/infrastructure/provider/DeterministicRouteProviderPort.java`
- `backend/src/main/java/com/middleproject/reminder/infrastructure/provider/ProviderCallPolicyClient.java`
- `backend/src/main/java/com/middleproject/reminder/infrastructure/persistence/JdbcPrivateCarRouteRepository.java`
- `backend/src/main/java/com/middleproject/reminder/web/PrivateCarPlanningController.java`
- `backend/src/main/resources/db/migration/V8__phase_13_private_car_route.sql`
- `backend/src/test/java/com/middleproject/reminder/support/FakeGeocodingPort.java`
- `backend/src/test/java/com/middleproject/reminder/support/FakeRouteProviderPort.java`
- `backend/src/test/java/com/middleproject/reminder/support/PrivateCarFixtures.java`
- `backend/src/test/java/com/middleproject/reminder/support/AdjustableClock.java`
- `backend/src/test/java/com/middleproject/reminder/PrivateCarPlanningTest.java`
- `backend/src/test/java/com/middleproject/reminder/PrivateCarPlanningIntegrationTest.java`
- `backend/src/test/java/com/middleproject/reminder/PrivateCarRestControllerTest.java`
- `backend/src/test/java/com/middleproject/reminder/PrivateCarMcpIntegrationTest.java`

Modified:

- `backend/src/main/java/com/middleproject/reminder/application/TripService.java` (private-car question answering)
- `backend/src/main/java/com/middleproject/reminder/infrastructure/persistence/JdbcTripRepositories.java` (draft-context persistence)
- `backend/src/main/java/com/middleproject/reminder/web/McpAdapterController.java` (3 private-car tools, schema fix, proposalId pattern)
- `backend/src/test/java/com/middleproject/reminder/McpAdapterIntegrationTest.java` (schema assertions)
- `docs/phases/phase-13/result.md` (this file)

Non-Phase-13 working-tree changes (made by Codex at the user's request, not Phase 13 implementation files):

- `tools/orchestration/phases-12-plus.json` (per-phase invocation cap raised from 2 to 10)
- `tools/orchestration/tests/Phase12PlusOrchestrator.Tests.ps1` (Pester coverage for the raised cap)

## Commands executed

| Command | Exit code | Summary |
|---|---:|---|
| `.\gradlew.bat test --tests "com.middleproject.reminder.PrivateCarMcpIntegrationTest" --no-daemon` | 0 | 5 tests green after semantic replay assertions (historical early run) |
| `.\gradlew.bat test --tests "com.middleproject.reminder.PrivateCarPlanningTest" --tests "com.middleproject.reminder.PrivateCarPlanningIntegrationTest" --tests "com.middleproject.reminder.PrivateCarRestControllerTest" --tests "com.middleproject.reminder.PrivateCarMcpIntegrationTest" --tests "com.middleproject.reminder.McpAdapterIntegrationTest" --no-daemon` | 1 | historical intermediate run: 42 tests, 1 failure — `McpAdapterIntegrationTest.initializeAndToolsListExposeExactlySixClosedSchemas` (line 90) still expected `format=uuid` for `proposalId`/`confirmationId` |
| same command, after updating the schema test | 0 | historical intermediate run: 42 tests, 0 failures |
| same command, after the focused correction tests were added | 0 | focused Phase 13 suite (5 classes): **47 tests, 0 failures, 0 errors, 0 skipped** — includes post-TTL same-key replay, fresh-key expired rejection, and malformed proposal ID boundary coverage |
| `.\gradlew.bat clean test --no-daemon` | 0 | full suite: **157 tests, 0 failures, 0 errors, 8 skipped** |
| `Invoke-Pester tools/orchestration/tests/Phase12PlusOrchestrator.Tests.ps1` | 0 | Pester: 8 passed, 0 failed, 0 skipped |

Note: the final full-suite XML shows **157 tests, 0 failures, 0 errors, 8 skipped** across 28 suites. All 8 skipped tests belong to the environment-gated `Postgres16IntegrationTest` because `POSTGRES_TEST_*` was not supplied; this run does not claim 0 ignored, and does not claim actual PostgreSQL V8 validation.

## Acceptance evidence

RED evidence (from the prior Codex baseline runs, recorded in the phase report):

- Initial vertical-slice compile: missing types (`GeoPoint`, `PrivateCarPlanningInput`, `PrivateCarRoute`, `ProviderOutcome`, `RoutePlan`, `RoutePlanRequest`, the three ports, `PrivateCarPlanningService`, the provider adapters) — the slice did not exist yet.
- First application-context boot: 13 `NoSuchBeanDefinitionException` failures. These came from the nested test `@Configuration` replacing the application source, not from all production beans being unregistered.
- Baseline test runs: the three baseline-test failures (`PrivateCarPlanningTest`, `PrivateCarPlanningIntegrationTest`, `PrivateCarRestControllerTest`) were invalid baseline/test assumptions — preview intentionally allows a missing lead, and pre-existing `DRAFT_CREATED`/`DRAFT_ANSWERED` events affected zero-count assertions — not missing types.
- MCP timestamp-format comparison: `PrivateCarMcpIntegrationTest.confirmToolPersistsExactOnceAndMatchesService` failed at line 141 — raw JSON string equality between first response and idempotent replay is invalid because the first live response keeps offsets like `2030-01-01T10:00:00+09:00` while the deserialized replay normalizes to `2030-01-01T01:00:00Z`; both are the same instant.

GREEN commands and observed counts (all executed this session):

- `PrivateCarMcpIntegrationTest`: BUILD SUCCESSFUL (5 tests, historical early run).
- Focused Phase 13 suite (5 classes): **47 tests, 0 failures, 0 errors, 0 skipped**. The final correction added 5 focused tests beyond the earlier 42-test state: post-TTL same-key replay, fresh-key expired rejection, and malformed proposal ID boundary coverage.
- Full `clean test`: **157 tests, 0 failures, 0 errors, 8 skipped** (skips are the environment-gated PostgreSQL suite), no missing/ambiguous-bean failures — production `DeterministicGeocodingPort`/`DeterministicRouteProviderPort` beans coexist with test `@Primary` fakes; all context-loading integration tests boot the application context.
- Pester `tools/orchestration/tests/Phase12PlusOrchestrator.Tests.ps1`: 8 passed, 0 failed, 0 skipped (orchestration cap 2 → 10 change).

Feature/file summary:

- Private-car vertical slice: draft question walk (`origin` → `destination` → `departureAt` → `private_car.reminder_lead_minutes`), route preview with stable SHA-256 proposal id and 10-minute TTL provenance, exact-once confirmation (one transaction: trip DRAFT→AWAITING_CONFIRMATION→CONFIRMED, one `private_car_routes` row, one event/reminder/policy/outbox row, due time = recommended departure − lead).
- Deterministic provider adapters and `ProviderCallPolicyClient` retry wrapper: exactly one retry for TIMEOUT/RATE_LIMITED, no retry for EMPTY/MALFORMED, no real HTTP/backoff sleep in this phase; `ProviderCallPolicy` defines the production contract (2 s connect / 5 s response timeouts, max 1 retry, exponential backoff).
- REST + MCP adapters both call the same `PrivateCarPlanningService` methods; MCP exposes `next_private_car_question`, `preview_private_car_route`, `confirm_private_car_route` with closed schemas.
- MCP schema fix: `tripId` keeps `format=uuid`; `proposalId` (SHA-256) is a string with `pattern=[0-9a-f]{64}` and `minLength`/`maxLength=64`; `confirmationId` (arbitrary string) is a plain string with no `format` — matching the runtime validation, which always excluded them from UUID parsing. Tests assert `tripId` is UUID-format, `proposalId` carries the pattern, and `confirmationId` carries no `format`.
- `ProviderCallPolicyClient` unbounded mutable call-history removed; retry evidence now comes only from fake-port `callCount()` assertions.
- Idempotent replay after TTL: temporal freshness/future checks moved inside the idempotent action so a completed confirmation can be replayed with the same key and payload even after the preview TTL; syntax/range validation still runs before idempotency, and a first-time stale/future request still fails before any business persistence.
- Input hardening: `proposalId` must match exactly lowercase SHA-256 hex `[0-9a-f]{64}` at the shared service boundary, in the REST `ConfirmRequest`, and in the MCP schema/validation; malformed IDs are rejected (REST 400, MCP -32602) before any provider or business writes.

Security/scope checks:

- `git diff --check`: clean (LF→CRLF warnings only).
- Secret scan over 32 changed/untracked files (tracked diff plus all new backend files): 0 credential/private-key pattern hits.
- Implementation changes limited to `backend/**` plus this result doc; the two `tools/orchestration` changes were made by Codex at the user's request to raise the per-phase invocation cap from 2 to 10. They are not Phase 13 implementation files, but they are part of the current working tree.

## Known limitations

- Providers are deterministic in-memory fakes; no real maps credentials or network calls exist yet (the phase deliberately defines the policy contract only).
- The idempotent MCP replay serializes timestamps in a normalized zone; clients must compare instants, not raw strings (covered by the semantic assertions in `confirmToolPersistsExactOnceAndMatchesService`).
- `McpAdapterIntegrationTest` uses deprecated `@SpyBean` (pre-existing, warning only).
- The PostgreSQL-gated suite (`Postgres16IntegrationTest`) was skipped in the full run because `POSTGRES_TEST_*` was not supplied; V8 migration was not validated against real PostgreSQL in this session.

## Handoff to Codex

All evidence-backed checks are green in this session: focused Phase 13 suite 47/47, full suite 157/157 with 8 environment-gated skips (PostgreSQL suite), Pester 8/8. Codex owns the accepted Phase 13 commit and push; the reviewed change set includes the phase implementation plus the Codex-made orchestration cap change (2 → 10) requested by the user. Non-goals of this phase, explicitly out of scope: real maps credentials/network calls, public transit, travel recommendations, Android, and AWS changes.

구현자가 추정으로 PASS를 선언하지 않는다. 실행하지 못한 명령과 이유를 기록한다.
