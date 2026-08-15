# Phase Result

- Phase: 14 — Travel-context follow-up recommendations
- Branch: codex/phase-14-travel-context-recommendations
- Base commit: bed83f5 (Phase 13 accepted HEAD)
- Result commit: recorded by the accepted Phase 14 branch HEAD
- Implementer: DeepSeek V4 Flash via Command Code CLI

## Changed files

New (all under `backend/`):

- `backend/src/main/java/com/middleproject/reminder/domain/ConsentStatus.java`
- `backend/src/main/java/com/middleproject/reminder/domain/DepartureTiming.java`
- `backend/src/main/java/com/middleproject/reminder/domain/FollowUpConsent.java`
- `backend/src/main/java/com/middleproject/reminder/domain/PlaceCandidate.java`
- `backend/src/main/java/com/middleproject/reminder/domain/PlaceCategory.java`
- `backend/src/main/java/com/middleproject/reminder/domain/PlaceSearchRequest.java`
- `backend/src/main/java/com/middleproject/reminder/domain/PostTripRecommendationResult.java`
- `backend/src/main/java/com/middleproject/reminder/domain/ProviderFailure.java`
- `backend/src/main/java/com/middleproject/reminder/domain/RecommendationSort.java`
- `backend/src/main/java/com/middleproject/reminder/domain/TravelContextResult.java`
- `backend/src/main/java/com/middleproject/reminder/domain/TravelRecommendationRules.java`
- `backend/src/main/java/com/middleproject/reminder/domain/WeatherCondition.java`
- `backend/src/main/java/com/middleproject/reminder/domain/WeatherForecast.java`
- `backend/src/main/java/com/middleproject/reminder/port/PlaceSearchProviderPort.java`
- `backend/src/main/java/com/middleproject/reminder/port/TravelConsentRepository.java`
- `backend/src/main/java/com/middleproject/reminder/port/WeatherProviderPort.java`
- `backend/src/main/java/com/middleproject/reminder/application/TravelRecommendationService.java`
- `backend/src/main/java/com/middleproject/reminder/infrastructure/persistence/JdbcTravelConsentRepository.java`
- `backend/src/main/java/com/middleproject/reminder/infrastructure/provider/DeterministicWeatherProviderPort.java`
- `backend/src/main/java/com/middleproject/reminder/infrastructure/provider/DeterministicPlaceSearchProviderPort.java`
- `backend/src/main/java/com/middleproject/reminder/web/TravelRecommendationController.java`
- `backend/src/main/resources/db/migration/V9__phase_14_travel_recommendation_consent.sql`
- `backend/src/test/java/com/middleproject/reminder/support/FakeWeatherProviderPort.java`
- `backend/src/test/java/com/middleproject/reminder/support/FakePlaceSearchProviderPort.java`
- `backend/src/test/java/com/middleproject/reminder/TravelRecommendationDomainTest.java`
- `backend/src/test/java/com/middleproject/reminder/TravelRecommendationServiceIntegrationTest.java`
- `backend/src/test/java/com/middleproject/reminder/TravelRecommendationRestIntegrationTest.java`
- `backend/src/test/java/com/middleproject/reminder/TravelRecommendationMcpIntegrationTest.java`
- `backend/src/test/java/com/middleproject/reminder/TravelRecommendationConcurrencyIntegrationTest.java` (final reliability correction)

Modified:

- `backend/src/main/java/com/middleproject/reminder/web/McpAdapterController.java` (3 travel tools, closed schemas with enum validation)
- `backend/src/test/java/com/middleproject/reminder/McpAdapterIntegrationTest.java` (16-tool schema assertions, travel enum/boolean schema checks)
- `docs/phases/phase-14/result.md` (this file)

## Architecture

- `TravelRecommendationService` is the single business boundary. All three operations
  (`context`, `recordConsent`, `recommend`) enforce trip ownership via
  `TripRepository.findByIdForOwner` against the fixed `DemoOwnerContext` owner; no client
  user id is trusted.
- `context` fetches one weather forecast per rule date (SAME_DAY → departure date,
  PREVIOUS_DAY → departure − 1 and departure, ascending), derives packing items from
  forecast conditions (RAIN → umbrella + waterproof footwear, SNOW/FREEZING → warm
  outerwear, HOT → hydration, deduplicated in a stable order), and searches
  accommodations only for PREVIOUS_DAY trips whose confirmed route is at least
  `ACCOMMODATION_MIN_DISTANCE` (200,000 m). A PROPOSED consent row is inserted when the
  trip has none; `context` never overwrites an existing ACCEPTED/DECLINED row. Provider
  outcomes are typed `ProviderOutcome` records; failures become `ProviderFailure(stage,
  category, kind)` entries and successful forecasts/candidates are retained — partial
  success is preserved, and provider failures never abort the operation.
- `recordConsent` is idempotent in the shared idempotency scope `travel-consent:<tripId>:<owner>`:
  the same key + payload replays the stored decision; the same key + different payload is
  rejected with 409. It requires an existing consent row (created by the `context`
  proposal flow; 409 otherwise) and applies the decision with optimistic version locking
  (`setDecision` with expected version), so concurrent decisions serialize on the version
  and the loser gets 409. The code does not restrict the existing row's status to
  PROPOSED: a fresh idempotency key intentionally records the new decision, so ACCEPTED
  can become DECLINED or vice versa. ACCEPTED/DECLINED are protected from being
  overwritten by `context` only; they are not globally terminal.
- `recommend` short-circuits with empty lists and no provider calls unless consent is
  ACCEPTED; with ACCEPTED consent it searches restaurants and attractions independently,
  sorts each by DISTANCE/PRICE/RATING (nulls last, name tie-break, max 5), and keeps
  partial success plus typed failures.
- `JdbcTravelConsentRepository` is a `@Repository` over `JdbcTemplate` with the
  `travel_recommendation_consent` table (V9 migration): one row per (trip_id, owner_id)
  via a unique constraint, status constrained to PROPOSED/ACCEPTED/DECLINED, optimistic
  `version` column, and an owner/created_at index.
- Production provider adapters are deterministic in-memory implementations
  (`DeterministicWeatherProviderPort`, `DeterministicPlaceSearchProviderPort`) so the
  application context boots without network access or credentials; tests override them
  with `@Primary` fakes.

## REST + MCP tools

REST (`/api/trips/{tripId}/travel`, `TravelRecommendationController`):

- `POST /context` — body `{"departureTiming": "SAME_DAY"|"PREVIOUS_DAY", "sort":
  "DISTANCE"|"PRICE"|"RATING"}` → `TravelContextResult`. The controller validates a
  closed JSON shape (exact field set, nonblank strings, declared enums) because default
  Jackson coercion would otherwise accept wrong-typed or unknown fields.
- `POST /consent` — header `Idempotency-Key` (nonblank, ≤ 200 chars) + body
  `{"accepted": true|false}` → `FollowUpConsent`.
- `GET /recommendations?sort=...` — → `PostTripRecommendationResult`.

MCP (3 new tools in `McpAdapterController`, all with closed schemas
`additionalProperties: false` and exact required sets):

- `get_trip_travel_context` — `tripId` (uuid), `departureTiming` (enum SAME_DAY /
  PREVIOUS_DAY), `sort` (enum DISTANCE / PRICE / RATING).
- `record_trip_followup_consent` — `tripId` (uuid), `accepted` (boolean),
  `idempotencyKey` (minLength 1, maxLength 200).
- `get_trip_recommendations` — `tripId` (uuid), `sort` (enum).

Both adapters delegate to the same `TravelRecommendationService` methods; the REST and
MCP integration tests assert byte-identical structured results for the same inputs. MCP
calls are audited (tool name, principal, outcome) like the other tools; validation
failures return `-32602` before any provider or business write, and unknown/other-owner
trips return a business error without leaking the trip id.

## Consent rules

- `context` calls `insertProposedIfAbsent`; existing ACCEPTED/DECLINED rows are
  protected from `context`: they are never overwritten or resurrected by it (covered by
  `contextNeverOverwritesAcceptedOrDeclined`). They are not globally terminal —
  `recordConsent` with a fresh idempotency key can change ACCEPTED to DECLINED or vice
  versa.
- Final reliability correction: the repository's insert-if-absent is now race-safe and
  does not silently swallow unrelated integrity failures.
  `JdbcTravelConsentRepository.insertProposedIfAbsent` originally used
  `INSERT ... SELECT ... WHERE NOT EXISTS`, which let two concurrent context requests
  both observe absence and one fail on the unique (trip_id, owner_id) constraint. On H2
  (PostgreSQL mode, which does not accept `ON CONFLICT`) it now uses the same pattern the
  codebase already uses elsewhere (`JdbcIdempotencyAdapter`, `ReminderDeliveryService`):
  the `WHERE NOT EXISTS` insert with the `DataIntegrityViolationException` catch scoped
  strictly to the H2 insert — after a caught violation the repository re-reads the exact
  (trip_id, owner_id) row and returns empty only when it exists (the expected duplicate
  race); an integrity violation for a row that does not exist (e.g. a trip_id violating
  the foreign key) is rethrown so unrelated FK/check/length problems stay visible. H2
  does not abort the transaction on that error. On real PostgreSQL it uses the atomic
  `INSERT ... VALUES ... ON CONFLICT (trip_id, owner_id) DO NOTHING` with no integrity
  catch, which never raises a constraint error, so no PostgreSQL transaction is ever left
  aborted and resumed. The product/database branch is evaluated once per call. The port
  contract is unchanged: a row is created exactly once, the second caller gets
  `Optional.empty()` and reads the existing row.
- `TravelRecommendationConcurrencyIntegrationTest` invokes `context` for the same owned
  trip from two threads in separate Spring transactions, released together by a
  `CountDownLatch` (30-second bounded await, no sleeps), using the deterministic
  production adapters (no mutable fakes). It proves both calls succeed with
  `PROPOSED` and exactly one consent row. RED evidence: with the old `WHERE NOT EXISTS`
  SQL this test fails (one thread hits the unique constraint); with the correction it
  passes and was stable across 5 consecutive runs.
- The service integration test additionally proves that an integrity violation for a
  non-existent (trip_id, owner_id) row (missing trip FK) is rethrown rather than
  converted into an empty result, while an existing-row duplicate race returns empty
  (`duplicateConsentRaceIsReportedAsAbsentButUnrelatedIntegrityViolationIsRethrown`).

## Provenance / partial-success behavior

- Every `WeatherForecast` and `PlaceCandidate` carries provider/source/fetchedAt
  provenance; candidates additionally carry priceSource and ratingSource when price or
  rating is present (enforced in the record constructor). All records are immutable,
  `List.copyOf` defensive copies, no setters.
- Failures are typed and never exceptions: weather per date, place per category, with
  `ProviderOutcome.Kind` (TIMEOUT, RATE_LIMITED, EMPTY, MALFORMED). A failed forecast or
  search does not discard successful siblings; `contextLeavesTripsRemindersOutboxAndRoutesUnchangedExceptConsentRow`
  proves `context` mutates nothing except the consent row.

## RED/GREEN evidence

RED:

- The focused Phase 14 suite was green before this correction (65 tests). The defect was
  a latent concurrency race in `insertProposedIfAbsent`, not an existing test failure.
- Correction RED: `TravelRecommendationConcurrencyIntegrationTest` (new) failed against
  the original `INSERT ... SELECT ... WHERE NOT EXISTS` SQL — one of the two concurrent
  transactions hit the unique (trip_id, owner_id) constraint
  (`org.springframework.dao.DuplicateKeyException`), proving the test exposes the race.
- Correction GREEN: the same test passes with the corrected repository (H2 branch +
  PostgreSQL `ON CONFLICT` branch) and was stable across 5 consecutive runs.
- Final correction GREEN: the repository test demonstrates the H2 branch returns empty
  for the exact duplicate race and rethrows an unrelated integrity violation
  (`duplicateConsentRaceIsReportedAsAbsentButUnrelatedIntegrityViolationIsRethrown`).

GREEN commands and observed counts (all executed this session):

- `.\gradlew.bat test --tests "com.middleproject.reminder.TravelRecommendationConcurrencyIntegrationTest" --no-daemon` — BUILD SUCCESSFUL (1 test; also run 5× consecutively, all green).
- Final correction checks: `.\gradlew.bat test --tests "com.middleproject.reminder.TravelRecommendationConcurrencyIntegrationTest" --tests "com.middleproject.reminder.TravelRecommendationServiceIntegrationTest" --no-daemon` — BUILD SUCCESSFUL (25 tests: 1 concurrency + 24 service-integration).
- Focused Phase 14 suite (6 classes: `TravelRecommendationDomainTest`,
  `TravelRecommendationServiceIntegrationTest`, `TravelRecommendationRestIntegrationTest`,
  `TravelRecommendationMcpIntegrationTest`, `TravelRecommendationConcurrencyIntegrationTest`,
  `McpAdapterIntegrationTest`): **68 tests, 0 failures, 0 errors, 0 skipped** (65 prior
  tests + 1 concurrency test + 2 final service-integration tests; Codex independently
  reran and aggregated these six suites after the final port-boundary correction).
- `.\gradlew.bat clean test --no-daemon`: **215 tests across 33 suites, 0 failures, 0
  errors, 8 skipped**. All 8 skipped tests belong to the environment-gated
  `Postgres16IntegrationTest` because `POSTGRES_TEST_*` was not supplied; this run does
  not claim 0 ignored, and does not claim actual PostgreSQL V9 validation.
- `Invoke-Pester tools/orchestration/tests/Phase12PlusOrchestrator.Tests.ps1` — 8 passed,
  0 failed, 0 skipped.

## Security/scope checks

- `git diff --check`: clean.
- Credential-pattern scan over all 32 changed/untracked implementation/result files (tracked diff plus all new
  backend files): 0 credential/private-key pattern hits.
- Implementation changes limited to `backend/**` plus this result doc. No commit or push
  was made; the working tree is intentionally left uncommitted for Codex review.
- No real credentials, secrets, Terraform state, or personal data were read or written.
- Scope discipline: no unrelated refactors; only the one obviously unused import
  (`jakarta.validation.Valid`) was removed from `TravelRecommendationController`.

## Known limitations

- Providers are deterministic in-memory fakes; no real weather/place-search credentials
  or network calls exist yet.
- The H2 branch of `insertProposedIfAbsent` relies on `INSERT ... SELECT ... WHERE NOT
  EXISTS` plus catching the duplicate-key violation (the established codebase pattern for
  H2, which does not accept `ON CONFLICT`); the catch is scoped to the H2 insert and only
  converts the violation to empty when the exact (trip_id, owner_id) row exists, so
  unrelated integrity failures are rethrown. The real-PostgreSQL branch is the atomic
  `ON CONFLICT DO NOTHING` form without any integrity catch. Actual PostgreSQL V9
  migration validation was not run in this session (`POSTGRES_TEST_*` not supplied), so
  the `ON CONFLICT` branch is verified by code review and the shared codebase precedent
  (`JdbcIdempotencyAdapter`, `ReminderDeliveryService`), not by a live PostgreSQL run.
- `McpAdapterIntegrationTest` uses deprecated `@SpyBean` (pre-existing, warning only).
- The concurrency test uses the deterministic production adapters because the `@Primary`
  fakes are not thread-safe; this keeps the test deterministic and free of sleeps.

## Handoff to Codex

All evidence-backed checks are green in this session: focused Phase 14 suite 68/68,
full suite 215/215 with 8 environment-gated skips (PostgreSQL suite), Pester 8/8,
`git diff --check` clean, credential scan clean. The consent proposal insert is now
race-safe, only converts the exact duplicate race to empty, and is covered by a
deterministic concurrency test that demonstrably fails against the previous SQL, plus a
focused test proving unrelated integrity violations are rethrown. Codex owns the accepted
Phase 14 commit and push. Non-goals of this phase, explicitly out of scope: real
weather/place-search providers, credentials, and network calls; actual PostgreSQL V9
validation; Android; and AWS changes.

구현자가 추정으로 PASS를 선언하지 않는다. 실행하지 못한 명령과 이유를 기록한다.
