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

## AWS 3-Tier and Well-Architected diagrams — state: source-confirmed documentation, not operational certification — 2026-09-10

Evidence: `docs/architecture/middleproject-aws-3tier-architecture.drawio` contains six pages: a vertical overview, three tier-transition details, a six-pillar source review, and an all-in-one detailed view. `docs/architecture/middleproject-aws-3tier-vertical-overview.png` is the AWS-icon overview generated through the AWS Diagram MCP. Diagram content is grounded in `infra/terraform/main.tf`, `tier.tf`, `security.tf`, and `observability.tf`; gaps are explicitly marked rather than drawn as deployed controls.
Checked: 2026-09-10 — XML parsed with six named pages and no duplicate cell IDs; the PNG was visually inspected. No AWS apply or live inventory check was performed.

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

## Architecture atlas — state: verified presentation artifact — 2026-09-10

Evidence: `docs/architecture/architecture-atlas.html` contains six rendered views with numbered arrows and Korean flow explanations. `docs/architecture/atlas/` contains the SVG exports and complete page screenshots. The atlas supersedes the earlier draw.io visual presentation while retaining its six-view organization.
Checked: 2026-09-10 — Chromium rendered all six views; navigation and zoom passed, no JavaScript exceptions were observed, and desktop/mobile document overflow checks passed. This verifies the artifact, not live AWS operation or certification.

## Password-protected architecture atlas hosting — state: operational and verified — 2026-09-10

Evidence: AWS Amplify Hosting app `d2o7zaeglhdnqe`, production branch `main`, manual deployment job `1`, and `docs/architecture/architecture-atlas.html`. The public endpoint requires shared HTTP Basic Authentication; the hosted document is presentation material and is separate from the Deadline Companion application runtime.
Checked: 2026-09-10 — deployment job reported `SUCCEED`; an unauthenticated request returned HTTP 401, an authenticated request returned HTTP 200 with 37,324 bytes, and the hosted file SHA-256 matched the local source. No custom domain, Git-connected continuous deployment, or per-person identity control is configured.

## Calendly-inspired service interface — state: verified locally — 2026-09-10

Evidence: `frontend/src/App.tsx`, `styles.css`, `App.test.tsx`, `index.html`, `public/favicon.svg`, and `vite.config.ts`. D-014 replaces the pastel presentation with top navigation, a navy/blue hero, white schedule cards, inline schedule search, a registration panel and calendar. The existing registration, editing, cancellation, history, authentication and refresh logic is preserved.
Checked: 2026-09-10 — TypeScript passed, 23 frontend tests passed and the production/PWA build passed. Restarted the existing local containers and copied the built frontend into the WEB container. Chromium confirmed two existing records, search/reset, history and form focus; no page errors or document overflow at 320/390/540/780/1024/1440 px. Desktop/mobile screenshots are temporary evidence at `/tmp/middleproject-calendly-desktop.png` and `/tmp/middleproject-calendly-mobile.png`. The in-app browser displayed the new interface at `http://127.0.0.1:8088/?design=blue-20260910` after its earlier cached page updated.

## Reference-image weekly calendar prototype — state: verified standalone presentation — 2026-09-10

Evidence: `docs/design/calendar-reference/index.html`, `styles.css`, `script.js` and `README.md`. A separate sample calendar presents pastel events and detail dialogs with search/category filtering and nonpersistent sample creation. It is not the application's active frontend and has no backend integration.
Checked: 2026-09-10 — Chromium rendered desktop/mobile previews, verified detail open/close, search and sample creation, and reported no page errors or mobile document overflow. Temporary screenshots: `/tmp/calendar-reference-desktop.png`, `/tmp/calendar-reference-mobile.png`.

## Daylight supplied prototype — state: observed locally — 2026-09-10

- The supplied `index.html`, `styles.css`, and `script.js` under `/home/grapefruit/.gemini/antigravity/scratch/calendar-app/` now display Daylight, an empty initial calendar/notification state and a desktop/mobile sidebar toggle with persisted preference. Category editing remains present.
- Evidence: `/tmp/daylight-check.mjs` passed browser checks; `/tmp/daylight-desktop.png` and `/tmp/daylight-mobile.png` were visually inspected. Preview: `http://127.0.0.1:8093/`.
- Boundary: standalone presentation only, reference July 2023 week, no backend API integration or notification delivery; newly added preview events disappear on reload. Existing backend records were not deleted.

## Daylight team-calendar follow-up — state: observed locally — 2026-09-10

- Supersedes the preceding prototype's fixed-date/nonpersistent-event limitations: scratch `team-calendar.js` implements a current-date seven-day calendar, year/month/day controls, four editable selectable member profiles, persistent local events and targeted local registration notifications with read state. Screens identify the local-only and non-authenticated nature of member selection.
- Evidence: `/tmp/daylight-team-check.mjs` passed; desktop/mobile screenshots `/tmp/daylight-team-desktop.png`, `/tmp/daylight-team-mobile.png` were visually inspected. Local preview remains port 8093. No backend/public deployment was modified.
- Not implemented for Daylight: shared server team records, authenticated team membership, cross-device synchronization, live recipient email delivery or scheduled team alerts. Existing backend SES transport alone does not establish those capabilities.

## Daylight static AWS hosting — state: observed operational — 2026-09-10

- Later local-only UI follow-up: larger desktop typography/controls and mobile drawer date/search/team shortcuts are implemented in repository/scratch source and browser-checked (`/tmp/daylight-layout-check.mjs`, `/tmp/daylight-large-desktop.png`, `/tmp/daylight-mobile-tools.png`). These layout changes are not yet deployed; the published site remains at manual job 2's transparent-navigation revision.

- Public URL: `https://main.d1za53r0rfy3x6.amplifyapp.com/`. Separate Amplify app `d1za53r0rfy3x6`, Seoul region, branch `main`, manual job `1` returned `SUCCEED`. The existing architecture atlas app was not modified.
- Evidence: all four deployed files returned HTTP 200 and matched the SHA-256 of the repository `daylight/` assets. This confirms static deployment only; team authentication, server persistence and live email remain unimplemented for Daylight.

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
