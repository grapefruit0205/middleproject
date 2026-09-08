# PRODUCT TRUTH — middleproject

Evidence class: repository code, tests, infrastructure definitions, and checked-in documentation. Operational production state requires runtime evidence and is not inferred from code.

Rule: every entry carries evidence (code path, test, screenshot), a date, and the date it was last checked against the code. External claims may be sourced **only** from the Implemented section. Re-confirm any entry checked more than 90 days ago before using it in a claim. Code states are never blended: implemented / wired / operational / verified (see the ballast proof-standard skill).

## Implemented

<!-- ## <capability> — <state: implemented|wired|operational|verified> — <YYYY-MM-DD>
Evidence: <code path / test / screenshot>
Checked: <YYYY-MM-DD> — re-confirm against the code once this is over 90 days old -->

## EC2 Apache–Tomcat–RDS three-tier infrastructure — state: implemented; historically verified, not currently operational — 2026-09-07

Evidence: `infra/terraform/main.tf`, `tier.tf`, `security.tf`, and bootstrap templates implement Public ALB → private Apache WEB ASG → Internal ALB → private external-Tomcat WAS ASG → isolated RDS. The public listener rejects `/api/mcp`, WEB/WAS capacities and RDS Multi-AZ are configurable with HA defaults, and WAS/migration IAM and DB secrets are separated. `docs/phases/phase-11/result.md` records an older live baseline; no 2026-09-07 AWS apply was authorized.
Checked: 2026-09-07 — Terraform fmt/validate and source contracts passed; live AWS state remains absent.

## Deadline aggregate REST service and dashboard — state: verified locally — 2026-09-07

Evidence: `DeadlineService`, `DeadlineController`, Flyway V1–V7, and the React dashboard implement transactional registration/list/detail/update/stateful cancellation, idempotent replay, optimistic 409 conflicts, loading/empty/error/mobile states, and persistent history. S05 exercised the flow through Apache and external Tomcat against PostgreSQL 16 and repeated it in the browser.
Checked: 2026-09-07 — full local build plus API/browser integration passed.

## Scheduler/outbox/queue/delivery reliability mechanisms — state: implemented and locally verified; external providers unverified — 2026-09-07

Evidence: `SchedulerOutboxService`, Scheduler/SQS adapters, idempotency lease/fencing, delivery consumer/service, SQS/DLQ Terraform, V7 outcome migration, and current tests. Provider timeout persists `DELIVERY_UNKNOWN`/`OUTCOME_UNKNOWN` and is not blindly resent; provider acceptance is not labeled as receipt.
Checked: 2026-09-07 — full test suite and local disabled-provider history passed; no live AWS Scheduler/SQS/SES rerun.

## Single-owner API security — state: implemented and profile-dependent — 2026-09-08

Evidence: `OwnerTokenAuthenticationFilter`, `SecurityConfiguration`, `DeadlineController`, `application-aws.yml`, and `SingleOwnerSecurityIntegrationTest` enforce a minimum-length server-configured Bearer token and principal ownership when security is enabled. `compose.yaml` deliberately disables that filter only for the loopback local profile, retains `LOCAL_OWNER_ID` as the demo owner, and does not pass a token. `infra/local/httpd/reminder.conf` restricts the local WEB to loopback Host and same-origin/CLI API requests.
Checked: 2026-09-08 — protected-mode security tests passed in the prior full run; current local no-auth API returned 200 while bad Host, external/different-port Origin, and cross-site requests returned 403. Public/AWS authentication was not disabled or live-tested.

## Local Apache–external Tomcat–PostgreSQL runtime — state: verified and operational locally — 2026-09-08

Evidence: `compose.yaml`, `infra/local/`, `.env.example`, and `docs/runbooks/service-mvp.md`. The three tiers expose only Apache at `127.0.0.1:8088`, separate admin/migrator/runtime DB roles, and retain PostgreSQL data. The current local profile uses the preserved demo-owner identity without an access code.
Checked: 2026-09-08 — WEB/WAS were rebuilt without deleting the DB volume; browser direct-open showed the two existing owner records and refresh controls. Apache syntax and 200/403 request-boundary checks passed.

## Host and request observability configuration — state: wired; historically observed — 2026-08-22

Evidence: `infra/terraform/observability.tf`, `security.tf`, Apache/Tomcat CloudWatch Agent bootstrap, correlation filter and metrics code. Phase 11 records log ingestion, correlation propagation, alarm recovery, and no drift during the historical baseline.
Checked: 2026-08-22 — configuration inspected; stack currently absent.

## React deadline dashboard — state: verified locally — 2026-09-08

Evidence: `frontend/src/App.tsx`, `App.test.tsx`, `hooks/useAutoRefresh.ts`, `hooks/useAutoRefresh.test.ts`, `components/DeadlineCalendar.tsx`, and `styles.css`. The pastel dashboard uses actual saved data for counts and filters. It refreshes the list and expanded history every 30 seconds only while visible/online, refreshes on tab/network return, prevents overlapping reads and mutation races, preserves a draft's optimistic-lock baseline, shows last-check/error/stale state, and clears loaded data if protected-mode authentication expires.
Checked: 2026-09-08 — 23 frontend tests, TypeScript and production/PWA build passed; direct browser open displayed existing records without a local access-code form.

## Architecture documentation and rule-based parsing boundary — state: source-confirmed — 2026-09-08

Evidence: `compose.yaml`, `infra/local/httpd/reminder.conf`, `infra/terraform/main.tf`, `tier.tf`, `variables.tf`, `application/ReminderWorkers.java`, `application/DeadlineHistoryService.java`, `web/ReminderCommandController.java`, `port/ReminderCommandParser.java`, `infrastructure/parsing/DeterministicReminderCommandParser.java`, and `frontend/src/App.tsx`. The application is a single WAR with in-process asynchronous workers; some application services use JDBC directly. Local external workers/email are disabled. The parse REST endpoint is a bounded deterministic parser, not an LLM. The current frontend continuously refreshes list/history but does not call the parse endpoint or expose global notification settings; existing per-deadline history exposes configuration flags, not provider health.
Checked: 2026-09-08 — source inspection only; no new runtime test, AWS inventory query, deployment, or email send. README separates current local evidence from Terraform intent and historical Phase evidence.

## Not implemented

<!-- Listed explicitly so absence is a fact, not a gap. Copy must not claim these.
Checked: <YYYY-MM-DD> — this section goes stale in the other direction: a line left here after the
capability shipped makes you claim less than you have earned. Sweep it on the same 90-day clock. -->

- A currently running AWS environment. The Phase 11 stack was torn down on 2026-08-15. Checked: 2026-08-22 (`README.md`).
- Demonstrated WEB/WAS failure recovery, RDS failover, measured RTO/RPO, final 15-minute rehearsal, and a final Phase 11 PASS. Checked: 2026-08-22 (`docs/phases/phase-11/result.md`, `review.md`).
- Secure MCP Tunnel, OAuth/multi-user identity, Android pairing/device tokens, and Trip Copilot Phase 12–18 application features. AWS/public mode still has only a server-configured single-owner token; the local loopback profile has no authentication. Checked: 2026-09-08.
- Demand-driven WEB/WAS autoscaling policies. Desired/min/max capacity is configurable, but no load metric scaling policy exists. Checked: 2026-09-07 (`infra/terraform/tier.tf`).
- A verified live AWS HTTPS deployment and a real SES inbox receipt for the current code. These are S05-A2/A3 and await explicit external authority/configuration. Checked: 2026-09-07 (`progress.json`).
- WAF, VPC interface endpoints, Kubernetes, Kafka, and microservices. These remain later scope, not present capability. Checked: 2026-08-22 (`architecture-v1.2.md`).
- MyWiki knowledge capture, canonical maintenance, semantic retrieval, versioning, provenance, conflict handling, ChatGPT plugin, and MyWiki web UI. Only product/design evaluation records exist; no MyWiki runtime code is implemented. Checked: 2026-08-23 (`docs/product/mywiki/step-01-idea-and-differentiation.md`, repository source tree).

## Permanently excluded

<!-- Decided against. Copy must never imply these. Link the ledger decision: D-### -->
