# OPEN QUESTIONS — registered, not remembered

Rule: anything unresolved gets a row here the moment it surfaces. A question is closed only by linking the decision (or finding) that resolved it — never by silently disappearing.

| ID | Question | Opened | Status |
|---|---|---|---|
| Q-001 | What workload, availability target, monthly infrastructure budget, and operations capacity must the target three-tier architecture support? | 2026-08-22 | open |
| Q-002 | Which proposed three-tier architecture should become the implementation baseline after the repository analysis? | 2026-08-22 | open |
| Q-003 | Should MyWiki replace the current reminder product in `middleproject`, be added beside it, or be built in a separate repository? | 2026-08-23 | closed → D-004 |
| Q-004 | Which first user segment and repeated job will be used to falsify or validate MyWiki's retention and differentiation hypotheses? | 2026-08-23 | closed → D-005 |
| Q-005 | Which AWS environment, execution window/budget, HTTPS configuration, SES identities, recipient, and explicit execution permissions will be used for service-mvp S05? Local S01–S05-A1 is complete; this now blocks only S05-A2/A3. | 2026-09-07 | open; `progress.json` S05-B1/B2; related to Q-001 |
| Q-006 | If the service is later exposed beyond loopback, which user-facing identity flow should replace the removed local access-code step while preserving authenticated ownership? | 2026-09-08 | open; local direct access is D-012, AWS Bearer remains until superseded |
| Q-007 | For four-user authenticated Daylight deployment, retain the physical three-tier Terraform or consolidate? | 2026-09-10 | closed → D-022, retain physical 3-Tier |
| Q-008 | Choose the public HTTPS ingress for the retained 3-Tier: a user-authorized domain/ACM certificate, or an AWS default CloudFront HTTPS address with an explicitly secured origin path. Team invitations and SES identities remain inputs for functional deployment. | 2026-09-10 | deferred → D-023: UI-only Amplify now; revisit after user prepares domain. No domain/DNS changes authorized yet |

## Readings in force — assumed, not decided

Rule: when work proceeds on a reading the user never confirmed (silence, a subject change, an "ok" that could mean anything), it is registered here with the user's words quoted — never in DECISIONS.md. One-way-door actions wait while a row is open. A row closes into a `D-` entry on confirmation, or is dropped — and what was built on it swept — on contradiction. (Protocol: ballast decision-ledger skill, *Provisional readings*.)

| ID | User's words (verbatim) | Our reading (`assumed`) | Breaks if wrong | Ends when | Relied on in |
|---|---|---|---|---|---|
| A-004 | "데이터, 사용범위,알림은 dealine companion으로 연결해야 하는데" — 2026-09-10 | `assumed`: connect the existing single-owner API first without inventing a public team identity flow. Preserve the intended four-member product as the next contract extension; clearly disable unsupported recipient/profile fields in the connected UI. | The user expected all team authentication and per-recipient delivery implemented in this same change. | Team identity and recipient implementation scope is chosen; Q-006 remains open. | Daylight API integration; D-018 |
| A-001 | "목표는 3 tier architecture 구조를 사용하는건데 이 구조에서 어떤 것이 좋을지 고민하고 있어" — 2026-08-22 | First produce a repository-grounded recommendation; do not change the deployed architecture until the user chooses it. | The user expected immediate implementation rather than analysis. | The user selects an option or explicitly requests implementation. | `memory/goal/three-tier-architecture.md` |
| A-002 | "처음 단계부터 하나씩 구현 시작하자. git 에 repo 해서 하나씩 기록할 때마다 commit 도 할거야." — 2026-08-23 | Use the current repository on a separate branch for reversible MyWiki research, design, and memory records; do not repurpose existing reminder code or push remote changes until repository placement is confirmed. | The user intended a new repository immediately, or intended the current product to be replaced without a separate decision. | closed by D-004 (2026-08-23) | `memory/goal/mywiki.md`, `docs/product/mywiki/step-01-idea-and-differentiation.md` |
| A-003 | "그럼 현재 방향에서 가장 먼저 해야할 서비스 완성을 위한 프롬프트를 md 형태로 만들어서 각 phase 가 완료되어도 계속 구현이 진행될수 잇도록 progress.json도 포함해줘" — 2026-09-07 | `assumed`: draft a single-owner deadline/reminder web demo with one email reminder per event and Asia/Seoul time. Preserve the existing three-tier backend; defer Bedrock and model distillation. Use targeted implementation checks and leave final installation/release checking to S05, without the legacy independent phase-review loop. | The intended audience is multi-user, multiple reminders are essential, or AI integration is a prerequisite rather than follow-up. | The user confirms or revises the implementation scope; irreversible/external actions still require their own authority. | `SERVICE_IMPLEMENTATION_PROMPT.md`, `progress.json` |
