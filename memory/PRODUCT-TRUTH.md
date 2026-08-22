# PRODUCT TRUTH — middleproject

Evidence class: repository code, tests, infrastructure definitions, and checked-in documentation. Operational production state requires runtime evidence and is not inferred from code.

Rule: every entry carries evidence (code path, test, screenshot), a date, and the date it was last checked against the code. External claims may be sourced **only** from the Implemented section. Re-confirm any entry checked more than 90 days ago before using it in a claim. Code states are never blended: implemented / wired / operational / verified (see the ballast proof-standard skill).

## Implemented

<!-- ## <capability> — <state: implemented|wired|operational|verified> — <YYYY-MM-DD>
Evidence: <code path / test / screenshot>
Checked: <YYYY-MM-DD> — re-confirm against the code once this is over 90 days old -->

## EC2 Apache–Tomcat–RDS three-tier infrastructure — state: implemented; historically verified, not currently operational — 2026-08-22

Evidence: `infra/terraform/main.tf`, `tier.tf`, `security.tf`, and bootstrap templates implement Public ALB → private Apache WEB ASG → Internal ALB → private external-Tomcat WAS ASG → isolated RDS. `docs/phases/phase-11/result.md` and `review.md` record one healthy live baseline; `README.md` records its 2026-08-15 teardown.
Checked: 2026-08-22 — direct code/document inspection; no live AWS rerun.

## Reminder core and REST/MCP application surfaces — state: implemented — 2026-08-22

Evidence: backend domain/application/web source, Flyway V1–V6 migrations, and checked-in integration/unit tests. REST and MCP reuse reminder application services; MCP rejects a missing `Principal` but production identity wiring is excluded from this claim.
Checked: 2026-08-22 — direct code inspection; backend test rerun unavailable because Java is absent.

## Scheduler/outbox/queue/delivery reliability mechanisms — state: implemented — 2026-08-22

Evidence: `SchedulerOutboxService`, Scheduler/SQS adapters, idempotency lease/fencing migrations and services, delivery consumer/service, SQS/DLQ Terraform, and phase 06–08 results/reviews.
Checked: 2026-08-22 — direct code/document inspection; no live provider delivery rerun.

## Host and request observability configuration — state: wired; historically observed — 2026-08-22

Evidence: `infra/terraform/observability.tf`, `security.tf`, Apache/Tomcat CloudWatch Agent bootstrap, correlation filter and metrics code. Phase 11 records log ingestion, correlation propagation, alarm recovery, and no drift during the historical baseline.
Checked: 2026-08-22 — configuration inspected; stack currently absent.

## React readiness smoke page — state: verified locally — 2026-08-22

Evidence: `frontend/src/App.tsx`; on 2026-08-22 Vitest passed 4/4, Vite/PWA build and `verify:build` passed, and production dependency audit reported 0 vulnerabilities.
Checked: 2026-08-22.

## Not implemented

<!-- Listed explicitly so absence is a fact, not a gap. Copy must not claim these.
Checked: <YYYY-MM-DD> — this section goes stale in the other direction: a line left here after the
capability shipped makes you claim less than you have earned. Sweep it on the same 90-day clock. -->

- A currently running AWS environment. The Phase 11 stack was torn down on 2026-08-15. Checked: 2026-08-22 (`README.md`).
- Demonstrated WEB/WAS failure recovery, RDS failover, measured RTO/RPO, final 15-minute rehearsal, and a final Phase 11 PASS. Checked: 2026-08-22 (`docs/phases/phase-11/result.md`, `review.md`).
- Public ALB rejection of `/api/mcp`. The public listener currently has only a default forward action. Checked: 2026-08-22 (`infra/terraform/tier.tf`, `web.sh.tftpl`).
- Secure MCP Tunnel, Demo Owner production identity wiring, Android pairing/device tokens, and Trip Copilot Phase 12–18 application features. Checked: 2026-08-22 (ADR-005, Phase 12–18 briefs, source tree).
- Authentication/authorization wiring for the public REST API and a production provider for the MCP controller's `Principal`. Checked: 2026-08-22 (`backend/build.gradle.kts`, controllers, WAS bootstrap).
- Demand-driven WEB/WAS scaling. Both ASGs are fixed at two and no scaling policy exists. Checked: 2026-08-22 (`infra/terraform/tier.tf`).
- Separate RDS admin/migration/runtime database principals. WAS currently consumes the RDS managed-master secret. Checked: 2026-08-22 (`tier.tf`, `was.sh.tftpl`).
- Feature-complete reminder frontend. The React app is a readiness smoke page only. Checked: 2026-08-22 (`frontend/src/App.tsx`, `README.md`).
- WAF, VPC interface endpoints, Kubernetes, Kafka, and microservices. These remain later scope, not present capability. Checked: 2026-08-22 (`architecture-v1.2.md`).

## Permanently excluded

<!-- Decided against. Copy must never imply these. Link the ledger decision: D-### -->
