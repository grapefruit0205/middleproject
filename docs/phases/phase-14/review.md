# Phase 14 Codex Review

- Decision: PASS
- Branch: `codex/phase-14-travel-context-recommendations`
- Baseline: `bed83f523573d360a9496bb5937f59623c135b89`
- Implementer: DeepSeek V4 Flash via Command Code CLI
- Reviewer and Git gatekeeper: Codex

## Acceptance review

- Weather dates follow SAME_DAY and PREVIOUS_DAY rules in ascending order.
- Packing items are derived deterministically from successful forecasts.
- Accommodation search runs only for PREVIOUS_DAY plus a confirmed private-car route of at least 200,000 m.
- Provider failures remain typed and preserve successful sibling results.
- Follow-up recommendations invoke no place provider unless consent is ACCEPTED.
- Consent decisions are owner-scoped, optimistic, and idempotent; same-key/different-payload requests return conflict.
- Concurrent context calls leave exactly one PROPOSED consent row.
- REST and MCP use the same `TravelRecommendationService` and expose closed, type-checked inputs.
- MCP continues to require the authenticated principal for audit and does not trust `X-User-Id` as the data owner.
- Results include provider/source/fetchedAt provenance and price/rating sources when those values exist.

## Independent verification

- `cd backend; .\gradlew.bat clean test --no-daemon`: BUILD SUCCESSFUL.
- XML aggregation: 215 tests across 33 suites, 0 failures, 0 errors, 8 skipped.
- Phase 14 six-suite aggregation: 68 tests, 0 failures, 0 errors, 0 skipped.
- All 8 skips belong to the environment-gated `Postgres16IntegrationTest`; `POSTGRES_TEST_*` was not supplied.
- `Invoke-Pester -Script .\tools\orchestration\tests\Phase12PlusOrchestrator.Tests.ps1 -PassThru`: 8 passed, 0 failed, 0 skipped.
- `git diff --check`: clean.
- Credential/private-key pattern scan: no matches.
- Changed implementation paths are limited to `backend/**`; Phase evidence is limited to `docs/phases/phase-14/**`.

## Review findings resolved

- REST JSON coercion was localized and replaced with strict controller-boundary validation; the attempted global Jackson override and probe test were removed.
- REST/MCP equivalence tests now observe the same consent state and replay the same idempotency key.
- Consent proposal insertion is race-safe: PostgreSQL uses `ON CONFLICT DO NOTHING`; H2 duplicate handling only suppresses a violation when the exact row is confirmed to exist.
- Unexpected H2 integrity violations are rethrown instead of being hidden.
- The persistence implementation remains package-private; integration tests depend on the `TravelConsentRepository` port.
- Documentation now distinguishes context overwrite protection from a deliberate consent change made with a fresh idempotency key.

## Known limitation

Actual PostgreSQL V9 execution was not performed in this review because the gated database variables were unavailable. The PostgreSQL SQL path is reviewed but not claimed as live-database evidence. Weather and place providers remain deterministic, credential-free adapters by Phase 14 design.
