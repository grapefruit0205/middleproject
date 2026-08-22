# OPEN QUESTIONS — registered, not remembered

Rule: anything unresolved gets a row here the moment it surfaces. A question is closed only by linking the decision (or finding) that resolved it — never by silently disappearing.

| ID | Question | Opened | Status |
|---|---|---|---|
| Q-001 | What workload, availability target, monthly infrastructure budget, and operations capacity must the target three-tier architecture support? | 2026-08-22 | open |
| Q-002 | Which proposed three-tier architecture should become the implementation baseline after the repository analysis? | 2026-08-22 | open |
| Q-003 | Should MyWiki replace the current reminder product in `middleproject`, be added beside it, or be built in a separate repository? | 2026-08-23 | closed → D-004 |
| Q-004 | Which first user segment and repeated job will be used to falsify or validate MyWiki's retention and differentiation hypotheses? | 2026-08-23 | closed → D-005 |

## Readings in force — assumed, not decided

Rule: when work proceeds on a reading the user never confirmed (silence, a subject change, an "ok" that could mean anything), it is registered here with the user's words quoted — never in DECISIONS.md. One-way-door actions wait while a row is open. A row closes into a `D-` entry on confirmation, or is dropped — and what was built on it swept — on contradiction. (Protocol: ballast decision-ledger skill, *Provisional readings*.)

| ID | User's words (verbatim) | Our reading (`assumed`) | Breaks if wrong | Ends when | Relied on in |
|---|---|---|---|---|---|
| A-001 | "목표는 3 tier architecture 구조를 사용하는건데 이 구조에서 어떤 것이 좋을지 고민하고 있어" — 2026-08-22 | First produce a repository-grounded recommendation; do not change the deployed architecture until the user chooses it. | The user expected immediate implementation rather than analysis. | The user selects an option or explicitly requests implementation. | `memory/goal/three-tier-architecture.md` |
| A-002 | "처음 단계부터 하나씩 구현 시작하자. git 에 repo 해서 하나씩 기록할 때마다 commit 도 할거야." — 2026-08-23 | Use the current repository on a separate branch for reversible MyWiki research, design, and memory records; do not repurpose existing reminder code or push remote changes until repository placement is confirmed. | The user intended a new repository immediately, or intended the current product to be replaced without a separate decision. | closed by D-004 (2026-08-23) | `memory/goal/mywiki.md`, `docs/product/mywiki/step-01-idea-and-differentiation.md` |
