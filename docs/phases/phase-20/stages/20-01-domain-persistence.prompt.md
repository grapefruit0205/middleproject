# Stage 20.1 · DayPlan domain and persistence

Use `gpt-5.6-luna` for this stage only. Do not implement later Phase 20 stages.

## Objective

Implement the minimum DayPlan, ScheduleItem, and TravelLeg domain and PostgreSQL persistence foundation described in `docs/phases/phase-20/contract.md`.

## Repository context

- Java 21, Spring Boot 3.5, Gradle Kotlin DSL, external Tomcat WAR.
- PostgreSQL migrations are under `backend/src/main/resources/db/migration` and use Flyway.
- Existing Trip, Reminder, owner-scoped repositories, optimistic versions, and idempotency conventions are authoritative.
- Preserve the existing architecture and package boundaries.

## Requirements

- Model explicit lifecycle/status enums and reject invalid transitions.
- Model Asia/Seoul day-plan semantics without storing local timestamps as ambiguous strings.
- Validate required owner, date, title/order, and place/time invariants.
- Add a Flyway migration with foreign keys, status checks, version columns, created/updated timestamps, and owner/date/order indexes.
- Add ports and JDBC repositories following existing repository conventions. Reads and writes must be owner-scoped.
- Add focused tests first, run them RED, implement the minimum code, then run GREEN. Do not weaken existing tests.
- Keep preview/confirmation orchestration, MCP, provider calls, Android, and notifications for later stages.

## Constraints

- Do not modify existing migration files; add a new migration.
- Do not modify `README.md`, phase contracts, orchestration manifests, Terraform, or plugin Skill files in this stage.
- Do not commit or push.
- Do not add dependencies unless the existing build cannot implement the requirement without one.
- Do not invent API keys, external provider behavior, or background location access.

## Validation

- Run focused domain tests and repository tests.
- Run `backend\\gradlew.bat test --no-daemon --console=plain`.
- Run `backend\\gradlew.bat bootWar --no-daemon --console=plain` if the full test suite passes.
- Report changed files, test commands, and any remaining limitation.
