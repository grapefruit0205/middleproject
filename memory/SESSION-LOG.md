# SESSION LOG — append, dated

One short section per working session: what was worked on, what was decided (with D-### links), what's pending. When context resets, this file is the recovery path — write it for the next session's reader.

---

## 2026-08-22

- Project initialized with the ballast memory structure (D-001).
- Started a repository-grounded evaluation of the target three-tier architecture.
- Swept repository documentation, ADRs, frontend, backend, Terraform, phase evidence, Git state, and available local tooling at commit `a013e1b`.
- Verified reusable architecture findings in `memory/knowledge/three-tier-architecture.md` using repository evidence and opened upstream AWS/Apache/Spring primary sources; no external verifier was configured, so confirmed claims are self-gated.
- Produced `docs/architecture/three-tier-assessment-2026-08-22.md`. Conditional recommendation: retain the current physical Apache–Tomcat–RDS topology and adopt the hardened A+ target; do not migrate to the managed alternative unless the assignment/demo constraints are retired.
- Recorded current product truth, including the absent public `/api/mcp` listener denial, missing production identity wiring, RDS master credential use by WAS, fixed 2/2 ASGs, incomplete HA evidence, and the torn-down AWS state.
- Local verification observed: frontend Vitest 4/4, Vite/PWA build, build-contract verification, and production dependency audit all passed. Backend, Terraform, and Pester reruns were unavailable because Java, Terraform, and PowerShell are absent; live AWS checks were unavailable because the stack is torn down.
- Zero-context rehearsal converged in three rounds. Round 3 selected A+, separated selected/implemented/HA-verified states, and classified P0/P1/P2 without a blocking stall.
- Pending: user confirmation of A+ (Q-002) and, for anything beyond an ephemeral demonstration, workload/SLO/budget/operator constraints (Q-001). No runtime architecture code was changed under provisional reading A-001.

## 2026-08-23

- Re-ran `ballast:brain-init` as requested. The full scaffold, product truth file, and session-start blocks were already present, so no templates were overwritten and no duplicate blocks were added.
- The mentioned MyWiki master-prompt document was not treated as an instruction or executed; this request applied only to the current `middleproject` workspace.
- The user then invoked the full Ballast goal pipeline for MyWiki and confirmed ordered, one-record-per-commit execution (D-003). The attached master prompt was treated as requirements input, identified by SHA-256 `67d1a2ac767dfac1de2557818b0542540b8db307b91a78107595227c68de8faf`.
- Created branch `codex/mywiki-foundation` and preserved the existing Ballast/three-tier work in baseline commit `3264be8` (`chore: initialize ballast project memory`). Repository-local Git identity was restored from the repository's existing author history because no identity was configured.
- Registered Q-003 (current vs separate MyWiki repository), Q-004 (first user/repeated job), and provisional A-002. Existing reminder runtime code was not changed and no remote push occurred.
- Completed master-prompt Step 1 in `docs/product/mywiki/step-01-idea-and-differentiation.md`. Official OpenAI documentation confirmed bounded `@` plugin invocation in ChatGPT Work, structured MCP tool arguments, OAuth, and write-safety contracts. Official Notion, Mem, Recall, and Tana pages showed that the broad AI Second Brain bundle is not differentiated; Tana's current-memory/proposal/MCP story is a close overlap.
- Step 1 verdict: Conditional GO only for a proposal-first, auditable Knowledge Commit hypothesis. Silent canonical overwrite, automatic merge, a full AWS production stack, Aurora commitment, and graph database remain outside the next MVP scope.
- Recorded reusable findings in `memory/knowledge/mywiki-market-and-openai-plugin.md` with self-gated and vendor-published labels. No external Ballast researcher was configured.
- Zero-context rehearsal round 1 passed cleanly: the executor derived the Conditional GO boundary, all required Step 2 decisions, and the runtime no-start condition without a blocking stall.
- Archived the outgoing three-tier checkpoint and replaced `memory/CHECKPOINT.md` with the MyWiki Step 1 return point. Next: answer Q-003/Q-004 and execute Step 2.
- The user closed Q-003 and Q-004: MyWiki belongs in a separate repository (D-004), and the first repeated job is the owner's ChatGPT learning/design conversation → Knowledge Commit loop (D-005). Provisional A-002 ended.
- Initialized `/home/grapefruit/dev/mywiki` at commit `1df2993`, importing the original source prompt, Step 1 assessment, goal skeleton, and verified research without reminder runtime code.
- Recorded the proposed Codex working-model policy in the new repository at commit `945e866`: Terra/medium by default, Sol/high for hard or high-risk review, and Luna only for stable mechanical work. This is a proposal, not a user-confirmed decision, and MyWiki runtime-model selection remains deferred to Step 24.
- Replaced this repository's checkpoint with a durable handoff boundary. All further MyWiki steps continue in the dedicated repository.

## 2026-09-07 — middleproject service handoff

- `confirmed`: D-006 requests a Markdown implementation prompt and resumable `progress.json`, not immediate service execution.
- `observed`: inspected the existing parser interface, reminder services/controllers, runtime profiles, frontend scripts, and architecture invariants to ground the handoff in the existing code.
- Added `SERVICE_IMPLEMENTATION_PROMPT.md` and `progress.json`: S01 registration/list UI, S02 changes/cancellation/reliability, S03 server-side delivery/history, S04 protected deployment code, S05 final installation/AWS/email demonstration. All implementation phases start pending; no fabricated test or deployment results are entered.
- `assumed`: A-003 records the single-owner/one-email scope and lightweight phase workflow. Q-005 holds external deployment/email prerequisites. Bedrock and distillation remain a nonblocking deferred backlog.
- Archived the previous checkpoint and routed current-session recovery to the middleproject service track, preserving the separate MyWiki boundary.
- Click Evidence guidance was read; a callable `click-gate` verification CLI was unavailable in this environment. Any artifact validation is direct host execution, not a Click verification receipt. No new approval contract was requested.
- `observed`: direct Node validation passed for JSON syntax, five ordered phases, 15 matching acceptance IDs, pending-only initial state, real resume paths, deferred AI scope, and byte-identical preservation of the old checkpoint. `git diff --check` passed. These are artifact checks, not service-runtime verification.
- No application implementation, runtime tests, installation checks, AWS mutation, email, Git commit, or push was performed for this artifact-authoring task.

## 2026-09-07 — service-mvp implementation run

- `confirmed`: D-007 starts continuous local implementation from S01 under the prepared prompt and progress contract.
- Marked `progress.json` as `running` with S01 `in_progress`. AWS/DNS/live-email/Git commit/push authorities remain false.
- Implemented S01–S04: transactional deadline aggregate create/read/update/cancel, idempotency and optimistic conflict handling, persistent scheduler/delivery history, explicit unknown provider outcome without blind resend (D-008), single-owner Bearer security, public MCP denial, separated DB/IAM roles, configurable HA, and reproducible local Apache/external-Tomcat/PostgreSQL tiers.
- Completed the S05 local path. Frontend passed 11 tests, production/PWA build and production audit; backend produced `ROOT.war` with 106 tests, zero failures/errors and nine environment-gated skips. The live PostgreSQL 16 compose path exercised the skipped DB boundary.
- `verified`: only WEB was bound to loopback, DB was healthy, protected API returned 401 without auth, replayed create returned the same ID and one row, update returned 200, stale update 409, cancellation persisted as version 2 after WAS restart, and disabled external delivery never appeared as success.
- `verified`: browser authentication → registration → refresh/re-authentication → update → history → cancellation → latest history completed. The run exposed stale open history after cancellation; it was fixed with a focused regression test and the final history showed `CANCELLED` plus pending cancellation work.
- `blocked`: `progress.json` now records S05-A1 complete and S05-A2/A3 pending. No AWS resource, DNS record, external email, Git commit, or push was created. Q-005 remains the external authorization/configuration boundary.

## 2026-09-08 — pastel web redesign

- `confirmed`: D-009 applies the user's visual reference to the current reminder UI.
- `observed`: rebuilt the dashboard and authentication screen around cream/lavender clay-style cards, a local generated bunny asset, responsive navigation, live aggregate counts, search, and calendar date/state filtering. Calendar implementation was delegated as one independent component.
- `observed`: existing CRUD/auth/history behavior and new filtering passed 12 frontend tests; TypeScript and production/PWA build passed. Browser checks confirmed search, month navigation, date filtering/reset, loaded images, and no horizontal document overflow at actual CSS widths 320px and approximately 400px.
- The built static files were copied to the existing local `middleproject-web-1` container for the user's preview. No new backend service or cloud resource was started. AWS/real-email completion remains pending in S05.
- Click Evidence recorded the npm checks under host authority; image generation used the built-in tool. The asset path and exact generation prompt are in `docs/design/pastel-dashboard.md`.

## 2026-09-08 — GitHub publication

- `confirmed`: D-010 authorizes committing and pushing the current implementation and redesign to the existing middleproject origin. The publication target is `codex/service-mvp-pastel-dashboard`; main and external S05 authorities remain unchanged.
- `observed`: before publication the worktree held the service implementation and visual redesign, while origin/main remained at `a013e1b`. Existing local documentation commits are preserved in the new branch history.
- `observed`: a bounded publication-safety check found no private-key or common credential-token patterns in the 59 candidate files. `.env` and generated build/dependency/Terraform state directories are ignored; example secrets are blank or explicit placeholders and security-test values are fixtures.
- This publication adds no application code changes and does not rerun the already completed frontend/backend validation. Git commit identity and remote branch equality are checked directly after push rather than inferred from this pre-publication record.
