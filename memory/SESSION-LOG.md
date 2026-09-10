# SESSION LOG — append, dated

## 2026-09-10 — Publish accumulated Daylight work including design correction

- `confirmed`: D-026 authorizes commit/push to `codex/service-mvp-pastel-dashboard` on the existing GitHub origin.
- `observed`: 24 changed paths are the accumulated Daylight API/UI/preview, local routing, deployment tooling and documentation work. No browser state, build artifacts, environment files or credentials are staged. A limited credential-pattern scan of added diff lines found no matches; it is not a comprehensive security audit.
- `observed`: retained prior implementation/browser/deployment evidence without rerunning broad reviews. This publication does not deploy infrastructure or merge main. Commit/push result is verified against the remote branch after execution.

## 2026-09-10 — Preserve original Daylight design in all period views

- `confirmed`: D-025 corrects the prior list-style interpretation. Kept the original shell/colors/card treatment; daily/weekly share timeline rendering and monthly places cards in date cells. Both API source and public preview are updated without changing storage/API contracts.
- `observed`: local desktop/mobile browser checks covered a local-only test event, month detail, one/seven-column timelines and monthly cells. Syntax checks passed; no broad backend suite was run. Click CLI was absent, so no Click verification receipt is claimed.
- `observed`: Amplify job 4 SUCCEED; all five public assets exactly match upload inputs. Public daily/monthly UI confirmed. No Git commit/push, DNS, backend provisioning or real email performed in this correction. Existing staged changes were preserved.

## 2026-09-10 — Daylight Git publication follow-up

- `confirmed`: D-024 authorizes committing/pushing accumulated Daylight work on `codex/service-mvp-pastel-dashboard` to the existing origin.
- `observed`: pending files match the API integration, calendar views, static preview deployment and associated documentation from previous turns. No application logic is changed in this publication turn. Prior functionality/deployment observations remain historical evidence; publication checks focus on staged content, syntax and remote commit identity.
- `observed`: Click Evidence skill applied; `click-gate` is unavailable on PATH. Execution uses host-authorized Git, without a Click-specific approval or verification receipt.

## 2026-09-10 — UI-only Amplify follow-up

- `confirmed`: D-023 defers authenticated 3-Tier deployment until the domain follow-up; requested current UI on Amplify only.
- `observed`: kept API-connected source intact and added explicit `daylight/preview/` browser-local UI with current day/week/month views. Shared CSS/UI handlers retain desktop sizing, transparent navigation and mobile hamburger tools. Existing preview storage schema is unchanged; no user data is packaged.
- `observed`: syntax/diff checks passed; local browser displayed month and day views. Manual Amplify job 3 SUCCEED. All five public assets HTTP 200/SHA-256 match; public browser has all three view buttons, four members and browser-local notice. Public tab retained for the user.
- `observed`: only the named Daylight static app was deployed. No new infrastructure, DNS, emails, Git commit or push. Backend integration and the original 3-Tier direction remain pending, not replaced by static storage.

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

## 2026-09-08 — README architecture and frontend priorities

- `confirmed`: D-011 requests a GitHub README update plus explanation and recommendations, not implementation of new UI.
- `observed`: grounded the local/AWS diagrams in Compose, Apache proxy configuration, Terraform, worker activation flags, and the actual history/parse API. Distinguished the single-WAR application from physical three-tier deployment, local external-provider disablement, configurable HA from load-driven scaling, and provider acceptance from inbox receipt.
- Documented the frontend suggestions as proposals: automatic list/history refresh, confirmed rule-parser-to-form input, then global notification configuration guidance. The last item needs a scoped backend settings read API; no secret or user email setting was added.
- Preserved historical Phase evidence, explicitly labeled it separately from current S01–S05, and retained pending AWS/HTTPS/live-email boundaries. This session changes documentation only.
- Click initially could not write its observation lock in the sandbox; after reservation expiry, host escalation restored read access. Documentation checks and Git publication use host authority, not a Guarded approval contract.

## 2026-09-08 — automatic status refresh and local direct access

- `confirmed`: D-012 selects the first proposed frontend follow-up and removes the access-code step from the current local preview.
- `observed`: added a visibility/online-aware 30-second refresh hook for deadline lists and expanded history. It prevents overlapping requests, pauses during mutations and failed refreshes, preserves draft contents and the version captured when editing began, exposes last-check/manual-refresh/stale/offline states, and clears previously loaded records on a protected-mode 401.
- `observed`: local Compose now disables application authentication only behind loopback-published Apache and maps `LOCAL_OWNER_ID` to the demo owner so the existing PostgreSQL records remain accessible. AWS profile authentication and Terraform secrets were unchanged. No token was placed in frontend code or browser storage.
- `verified`: 23 frontend tests, TypeScript, and production/PWA build passed. WEB/WAS were rebuilt while preserving the DB volume; the browser opened directly and showed two prior records. Apache syntax passed; expected loopback/CLI requests returned 200 and bad Host/external Origin/different-port Origin/cross-site requests returned 403 without printing response data.
- `pending`: the next proposed frontend work is rule-parser-assisted one-sentence draft entry. AWS HTTPS and real SES receipt remain blocked in S05.

## 2026-09-08 — reader-first README architecture guide

- `confirmed`: the user requested a more readable README that explains the architecture clearly.
- `observed`: reorganized the README around the product purpose, working features, local quick start, physical WEB/WAS/DB boundaries, the AWS Terraform topology, the asynchronous delivery sequence, security limits, current evidence, and the next implementation order.
- `observed`: removed the long duplicated legacy phase table and redeployment walkthrough from the README while preserving their source material under `docs/phases` and the deployment runbooks. No application, infrastructure, credential, database, or runtime state was changed.
- `pending`: this documentation follow-up has not been committed or pushed. AWS HTTPS and real SES receipt remain the external S05 blockers.

## 2026-09-08 — user-edited README prose applied

- `confirmed`: the user supplied a polished README draft and requested that it replace the current wording.
- `observed`: applied the supplied structure and prose, restored repository-relative links that had been transformed into Google Search URLs during pasting, and narrowed unsupported wording so actual inbox receipt is not claimed as an independently persisted implemented state.
- `observed`: this follow-up changes README wording and this session record only; application code, runtime services, infrastructure, credentials, and data were not changed.
- `pending`: the combined local follow-up remains uncommitted and unpushed. AWS HTTPS and real SES receipt remain the external S05 blockers.

## 2026-09-08 — follow-up publication requested

- `confirmed`: D-013 authorizes one commit and push of the current D-012 implementation, the user-edited README, and their records to the existing `codex/service-mvp-pastel-dashboard` branch.
- `observed`: the pre-publication worktree contains only the previously implemented local-direct-access/automatic-refresh files and their documentation/progress records. The target remote remains `https://github.com/grapefruit0205/middleproject.git`.
- `verified`: commit `40dc9ea` was pushed to `origin/codex/service-mvp-pastel-dashboard`, and the remote branch advanced to that commit. The one-time D-013 authority is consumed and does not extend to main, AWS, DNS, live email, credentials, or later changes.

## 2026-09-10 — AWS 3-Tier draw.io architecture set

- `confirmed`: the user requested a vertical overview, separate detail pages at each tier transition, and a sixth page that overlays all detail on the overview structure.
- `observed`: created a six-page editable draw.io artifact covering the overview, Internet→WEB, WEB→WAS, WAS→Data/Async, Well-Architected source review, and an all-in-one detailed view. Supporting services are omitted from the first page but expanded on the relevant transition pages.
- `observed`: the AWS Diagram MCP is deprecated and produces PNG rather than editable draw.io. Its complex Graphviz requests returned an opaque file-not-created error; a reduced vertical core generated successfully and was visually inspected. The editable multi-page XML was authored alongside it.
- `verified`: XML parsing found six named pages and no duplicate cell IDs. The diagrams only document current Terraform intent; they do not claim a running AWS environment or Well-Architected certification.
- No application, Terraform, credential, runtime, database, AWS resource, Git commit, or Git push mutation was performed for this diagram task.

## 2026-09-10 — architecture atlas readability redesign

- `confirmed`: the user requested clearer arrows and detailed Korean explanations below every page, then allowed any format that best presents the architecture while referring to AWS Well-Architected.
- `observed`: created `docs/architecture/architecture-atlas.html` with six views, numbered directional paths, role-specific line styles, separate Korean reading guides, zoom, SVG download and current-view printing. The sixth view retains the overview's vertical axis with the supporting paths added on both sides. SVG files and complete page screenshots are in `docs/architecture/atlas/`.
- `observed`: clarified representative support edges, conceptual primary/standby placement, consumer-as-WAS-role, and the TLS 1.2/1.3 listener policy. AWS official pillar and fault-isolation references were checked. No AWS deployment was performed.
- `verified`: all six pages rendered in Chromium and were visually inspected; previous/next navigation and zoom worked, JavaScript exception collection was empty, and document overflow was absent at 1440px and 390px after fixing the navigation container. Checks used direct host execution; `click-gate` was unavailable, so no Click verification receipt is claimed.
- The HTML atlas is the current visual deliverable. Previous draw.io and MCP image artifacts remain as earlier format references.

## 2026-09-10 — Notion architecture learning publication

- `confirmed` subsequent learning revision: Phase 9 must support a 15-minute presentation and the explanations should be easier and subdivided by architecture flow. `observed`: revised the same hub and all nine Phase pages with simple introductions; preserved original technical material in collapsed reference blocks and all native child-page relationships. Created 13 concept subpages and 2 timed presentation-script subpages. Phase 9 is now `Phase 9 · 전체 흐름과 15분 발표` at https://app.notion.com/p/3d7d9d5f6b3a813090b5cbeb9af817b2 .
- `observed`: new pages isolate WEB entry, WAS/DB responses, NAT egress, security, HA, Outbox, Scheduler, SQS read/delete/retry/DLQ, SES versus receipt, CloudWatch/SNS, recovery and costs. They distinguish the deployed Daylight static preview from the unverified 3-Tier operational path, and SNS notification wiring remains explicitly unimplemented. Parent readbacks confirmed 9 Phase children and 15 nested subpages; inspected key child readbacks. Notion sharing permissions and application/infrastructure state were unchanged. Fifteen minutes is a rehearsal allocation, not a measured narration duration.

- `confirmed`: after reconnecting Notion to a new workspace, the user authorized creating a new top-level page and the previously specified Phase 1–9 architecture learning documents. Existing workspace pages, application code, infrastructure, deployment, and Git publication remain outside this action.
- `observed`: Notion self reported workspace ID ff4d9d5f-6b3a-8145-b31b-0003b39787d4, different from the earlier connection. Created hub https://app.notion.com/p/3d7d9d5f6b3a81359f18c9aeec3376ed and nine child pages with Korean explanations, vertical Mermaid flows, tables, source links at commit 40dc9ea7e23f20fc611391b88e2d02b1a2ca8a5d, and AWS official references.
- `observed`: source inspection distinguishes absent SNS/alarm_actions, Outbox FAILED versus reminder status, SQS-consumer DLQ versus Scheduler target DLQ, provider acceptance versus receipt, and the non-atomic SES-success/DB-commit failure window. No code fixes or live AWS tests were performed.
- `observed`: hub readback contains all nine child links; Phase 5 and 6 readbacks contain diagrams and tables without truncation. No browser-render visual validation is claimed. Page creation defaults to private; no public sharing or four-person access permissions were changed.

## 2026-09-10 — private static AWS hosting feasibility

- `confirmed`: the user is considering newly connecting AWS to host `docs/architecture/architecture-atlas.html` by link for a four-person audience.
- `observed`: the artifact is a self-contained 37,324-byte HTML file with no external script or stylesheet dependency; only explanatory AWS documentation links are external. Static hosting is feasible without deploying the Deadline Companion 3-Tier runtime.
- `observed`: the local AWS CLI currently returns `NoCredentials`; no AWS resources, access controls, DNS, credentials, Git state, or hosting settings were changed. Recommended low-complexity option is a password-protected Amplify Hosting branch; per-person revocation would require a stronger identity-aware design rather than one shared password.

## 2026-09-10 — AWS CLI connection for architecture hosting

- `confirmed`: the user asked for help connecting AWS for the prospective static architecture hosting flow.
- `verified`: browser-based `aws login` created local profile `architecture-hosting`; `sts get-caller-identity` returned account `528821350786` and principal `arn:aws:iam::528821350786:root`. A read-only Amplify `list-apps` call in `ap-northeast-2` succeeded and returned no existing apps.
- `observed`: no Amplify app, S3 bucket, CloudFront distribution, IAM identity, password, DNS record, deployment, Git commit, or push was created. Because the connected principal is the account root, deployment should proceed through a least-privilege identity rather than treating root as the routine workload identity.

## 2026-09-10 — user-proposed Korean atlas wording

- `confirmed`: the user supplied an HTML revision, asked for an assessment, and requested applying their Korean wording first.
- `observed`: adopted the added cost and sustainability explanations verbatim and the “검토 권장 순서” heading in the existing atlas. Added “설계 의도 · 효과 미측정” labels because cost savings and carbon effects have not been measured. The proposed CSS and keyboard behavior were not applied in this text-only update.
- `observed`: Chromium displayed six explanation items and two qualification labels on page 5, with no JavaScript exceptions or document overflow at 1440px and 390px. Updated and visually inspected the page-05 screenshot. No application, infrastructure, commit, or push changes were made.

## 2026-09-10 — password-protected Amplify atlas deployment

- `confirmed`: the user authorized deployment of the static architecture atlas with the temporary AWS root login and requested the expected cost. This authority covered only the requested hosting action.
- `verified`: created Amplify Hosting app `d2o7zaeglhdnqe` in `ap-northeast-2`, branch `main`, and completed manual deployment job `1` with status `SUCCEED`. The deployment includes `index.html` and `architecture-atlas.html`.
- `verified`: the hosted endpoint returned HTTP 401 without credentials and HTTP 200 with shared Basic Authentication. The authenticated response was 37,324 bytes and its SHA-256 matched `docs/architecture/architecture-atlas.html`.
- `observed`: the deployment uses one shared credential for the four intended viewers. It has no per-person revocation, custom domain, Git-connected continuous deployment, or long-lived root access key. Credentials are not recorded in repository files.
- `assumed`: at the current 37 KB document size and roughly 120 full page loads per month, usage outside any free allowance should remain below USD 0.01/month before tax under the published Amplify storage and transfer rates. Actual free-tier or credit eligibility and billing remain account-dependent and were not verified.
- No application runtime, 3-Tier infrastructure, database, DNS record, Git commit, or Git push was created or changed by this static-document deployment.

## 2026-09-10 — in-app browser Basic Authentication compatibility

- `observed`: the original Amplify URL displayed `ERR_INVALID_AUTH_CREDENTIALS` in the Codex in-app browser because its embedded Chromium surface did not present the HTTP Basic Authentication prompt. This was not a DNS, TLS, or Amplify availability failure.
- `verified`: the endpoint continued to return HTTP 401 without credentials and HTTP 200 with valid credentials. Opening the same endpoint with Basic Authentication credentials supplied in the URL loaded Architecture Atlas page 6 successfully in a new in-app browser tab.
- `observed`: embedding a shared credential in a URL makes that URL itself sensitive because it can remain in browser history or be forwarded. The credential value is intentionally not recorded in repository files.

## 2026-09-10 — Calendly-inspired local service redesign

- `confirmed`: D-014 records the user's new visual reference, superseding the prior pastel direction. Inspected `https://calendly.com/` and adapted its visual direction to the existing reminder service.
- `observed`: replaced the lavender sidebar and illustrations with horizontal navigation, a blue/navy hero, white cards, clear blue actions, schedule search within the list, a form and calendar. Updated browser/PWA theme colors and Korean document language. Existing data and business logic remain intact.
- `observed`: local WEB/WAS/DB containers were stopped. Restarted those existing containers, preserving their volume and configuration, then copied the new production frontend build into the WEB container. Docker Compose and click-gate were unavailable in this shell; existing Docker commands and host checks completed the task without a Click receipt. The Docker image itself was not rebuilt; future recreation should build from the updated repository sources.
- `verified`: TypeScript, 23 existing frontend tests and production/PWA build passed. Updated the existing heading assertion to check the main heading without binding the workflow test to marketing wording. Browser checks covered two persisted records, search/reset, history and form-focus navigation, with no JavaScript exceptions. Corrected a 5px decorative overflow and confirmed layout widths from 320px to 1440px. Opened the refreshed interface in the in-app browser.
- No AWS deployment, Git commit or push was performed for this local visual change.

## 2026-09-10 — Figmaboy local installation

- `confirmed`: the user requested finding and installing the Figmaboy plugin.
- `observed`: the matching official project is `0xmiki/figmaboy`. It is a local-first desktop design application with a bundled MCP server rather than a Codex plugin bundle. The latest published release checked in this session was `v0.5.5`.
- `verified`: downloaded the official Linux x86_64 AppImage, standalone `figmaboy-mcp`, and `SHA256SUMS`; both release assets passed the published SHA-256 checks. Installed the AppImage at `/home/grapefruit/.local/opt/figmaboy/Figmaboy_0.5.5.AppImage`, command link at `/home/grapefruit/.local/bin/figmaboy`, and MCP binary at `/home/grapefruit/.local/bin/figmaboy-mcp`.
- `verified`: `figmaboy-mcp --version` returned `0.5.5`. Registered a global enabled Codex MCP entry named `figmaboy` using the stable absolute binary path. A new external Codex session is required for its tools to enter the session tool catalog; live editing additionally requires Figmaboy to be open on a design.
- Project application files, AWS resources, Git commits, and Git remotes were not changed by this user-level tool installation.

## 2026-09-10 — reference-image calendar HTML/CSS prototype

- `confirmed`: the user supplied a coral-accent weekly calendar image and requested HTML/CSS code in that style.
- `observed`: created the standalone `docs/design/calendar-reference/` prototype with white sidebar, beige calendar, pastel time-positioned events, CSS geometric cover art and a detail dialog. Small external JavaScript handles dialogs, category/search filtering and nonpersistent sample creation. Sample dates/time are explicit; this is not connected to the application's API.
- `verified`: browser preview at `http://127.0.0.1:8088/calendar-reference/` displayed desktop/mobile layouts. Browser checks passed detail open/close, search, sample creation and reload; no JavaScript exceptions or mobile document overflow. The calendar intentionally scrolls horizontally on mobile. Schedule styles use stylesheet rules instead of inline HTML styles to work with the local Apache CSP. Screenshots: `/tmp/calendar-reference-desktop.png`, `/tmp/calendar-reference-mobile.png`.
- Existing service frontend, stored schedules and AWS hosting were not changed. Copied only the prototype directory into the existing local WEB container for preview; no Git commit/push.
- `observed`: the in-app browser's existing service worker routed the prototype URL under port 8088 to the service dashboard. Started a loopback-only static preview at `http://127.0.0.1:8092/` for this directory so it has a separate service-worker scope.

## 2026-09-10 — calendar reference placed in Figmaboy

- `confirmed`: the user asked to attach the preceding calendar draft to Figmaboy.
- `observed`: connected directly to the installed Figmaboy stdio MCP because this task's native tool catalog lacks the newly installed server. The active `MIDDLEPROJECT` file (`file_e84e823e-d15f-48f0-8251-16336a4b706a`) had an empty Page 1. Inspected capabilities and document state before mutation.
- `verified`: created frame `calendar-reference-frame` named `주간 캘린더 · HTML 시안 참고`, imported `/tmp/calendar-reference-desktop.png` as a persistent 1440×1000 image asset, focused the viewport and saved revision 5. MCP frame screenshot confirmed the image rendered successfully.
- This is a reference image in a frame; its text, buttons and cards are not separate editable layers. No native layer reconstruction or HTML import is claimed.

## 2026-09-10 — Daylight supplied calendar prototype

- `confirmed` D-022: retain physical 3-Tier; no consolidated profile. `observed`: user-browser CUA navigation to `127.0.0.1:8088/daylight/` displayed the legacy React SPA and old cached bundle, while direct HTTP returned current Daylight HTML. Added Workbox navigation exclusions for `/daylight`, `/api`, `/healthz`, disabled Apache fallback for the Daylight directory and disabled caching of service-worker registration/update scripts. Frontend build and Apache syntax passed; refreshed local files. The existing browser's old 127.0.0.1 origin still displayed the cached SPA during checks; the clean `localhost:8088/daylight/` origin visibly showed current Daylight, day/week/month controls and server DB connection. No browser storage was deleted. Do not claim the old origin cache was already cleared.
- `observed`: no issued Seoul ACM certificates; a kinetra.site hosted zone exists but user has not approved its use. User asked whether a domain is necessary. Official CloudFront documentation confirms default-domain HTTPS without a purchased domain; origin protection/TLS is separate and needs Terraform changes. No domain purchase, DNS update, AWS provisioning, public deployment or Git publication in this follow-up.

- `confirmed` D-020/D-021: mobile period-specific visibility plus authenticated functional Amplify deployment, low cost for four users, reusing Terraform. `observed`: mobile day/week/month layouts completed and tested with nonpersistent fixtures (see PRODUCT-TRUTH); existing dirty integration work preserved. Updated README view descriptions and the local WEB preview.
- `observed`: read-only AWS checks found the existing Daylight static app but no Seoul ALB/RDS instances; other projects' Cognito pools were left untouched. Existing Terraform can scale WEB/WAS to one each and RDS to Single-AZ, but retains two ALBs and NAT. AWS Price List API rates yield USD 132.13/month for only those four core resource groups at 730 hours, before storage/network/auth/email/tax extras. Recorded source and breakdown in `docs/runbooks/daylight-deployment-assessment.md`. Physical consolidation is only a proposal pending Q-007; no billable provisioning or public-site replacement performed.

- `confirmed` D-018/D-019: Daylight is the HTML/CSS/JavaScript face of the existing backend, and API name is now Daylight API. README must present one service rather than two independent products. `observed`: connected the existing single-owner CRUD/cancel/history endpoints at `/daylight/` in the local WEB container and image definition; kept the old React UI at `/`. Reused PostgreSQL/outbox/notification engine without enabling providers or changing auth. Unsupported team recipients/profiles and event metadata are not presented as saved.
- `observed`: real browser/API integration passed once with one test event, including lost-response idempotent replay, cross-browser server read, conflict preservation, update/cancel and history. The test event is cancelled with audit records retained. Added only a focused mock-response check after the inline retry button follow-up. README links/fences, syntax, whitespace, API identity and packaging source checks passed; the static-only deploy guard rejected before AWS access. No broad code review loop, build/deployment, live email or Git publication. Runtime WAS still uses its preexisting artifact; Daylight API naming is updated in source/config/docs and will apply to WAS logs on its next rebuild.

- `observed` documentation follow-up: rebuilt the root README around Daylight's purpose, the separate static prototype and single-owner backend, two vertical architecture flows, local startup, current limits and Notion learning/presentation links. All nine relative documentation links resolve, fenced code blocks are balanced, and `git diff --check` passes. No application code, AWS deployment or access settings changed; full runtime tests were intentionally not repeated for this documentation-only change. D-017 authorizes publication on the existing service branch.

- `confirmed`: the user requested Daylight branding, a menu open/close control, and an empty existing-notification state in the three supplied files under `/home/grapefruit/.gemini/antigravity/scratch/calendar-app/`.
- `observed`: updated those files directly; removed hardcoded demo events, the initial open detail dialog, fabricated notification text/count and fixed sample current-time marker. Kept category editing/storage. Added desktop/mobile sidebar controls, Escape/backdrop closing and persisted menu preference. Corrected claims of live integration and persistent event storage.
- `observed`: Playwright checks passed branding, zero initial events/notifications, closed dialogs, menu toggling/persistence, mobile controls, temporary event creation and empty reload with no JavaScript errors. Screenshots: `/tmp/daylight-desktop.png`, `/tmp/daylight-mobile.png`; check script `/tmp/daylight-check.mjs`.
- `observed`: loopback preview runs at `http://127.0.0.1:8093/`. This standalone prototype still uses reference July 2023 dates and has no backend API connection; new events are nonpersistent. No existing server data, service frontend, AWS resources or Git publication changed.

## 2026-09-10 — Daylight four-member team calendar prototype

- `confirmed` later layout request: PC browser 100% should look approximately like the prior 130%, and mobile should expose PC tools in the left hamburger menu. `observed`: expanded desktop canvas/typography/controls without browser zoom or transforms; actual sidebar/time-row dimensions increase. Mobile relocates the existing date/search controls (no duplicate state), and includes calendar-return and team-management actions alongside existing creation, inbox and category tools. Repository `daylight/` and scratch preview files match.
- `observed`: `/tmp/daylight-layout-check.mjs` passed focused checks at widths 320, 390, 700, 1024, 1440 and 1920 for sizing, control relocation, date/search/team interactions and no page overflow/errors. Corrected only the test's missing asynchronous media-change wait after its first run. Visually inspected desktop and mobile-menu screenshots. No Git publication or AWS deployment was performed for this layout follow-up.

- `confirmed` follow-up: requested transparent date selectors. `observed`: removed the year/month/day controls' visible background and border in the scratch `styles.css`, retained readable option backgrounds and keyboard focus outline. Focused browser check passed all three transparent computed styles and month selection. No behavior or backend changes.

- `confirmed`: the user requested four selectable team members with editable names/roles, removal of My workspace, and a calendar driven by the current year with year/month/day selectors. They proposed notifying selected team members upon registration.
- `observed`: updated the supplied scratch calendar files and added `team-calendar.js`. The browser date now selects the initial week; all seven days and 24 hours are represented. Date selection clamps month lengths/leap years. Four stable member IDs retain editable names and roles. Events and recipient-specific registration notifications persist in browser storage; notification counts/read state follow the selected preview member. Existing sample data is not seeded.
- `observed`: `/tmp/daylight-team-check.mjs` passed focused browser checks for dates/leap years/year rollover, member editing, Sunday registration, recipient targeting/read status, reload persistence, cross-tab storage, overlapping cards and mobile controls without page errors. Visually inspected `/tmp/daylight-team-desktop.png` and `/tmp/daylight-team-mobile.png`.
- `observed`: no backend mutation or external email occurred. `DeadlineController` remains single-owner and its creation DTO has no team or recipients. `SesEmailNotificationSender` already supplies an email transport, but team authorization and per-recipient notification work remain unimplemented. Team selection is explicitly not authentication; storage synchronization is only within the same browser origin, not cross-device sharing.
- `confirmed`: the follow-up asks whether cross-device email notifications can be added; this is a capability question, not authorization to send live email or provision AWS. Click Evidence instructions were followed with ordinary host tools; `click-gate` was unavailable, so no Click-specific receipt is claimed.

## 2026-09-10 — Git publication preparation and Daylight AWS deployment

- `confirmed` later publication follow-up (D-016): requested committing/pushing the current transparent navigation and desktop/mobile layout work plus instructions for inviting three GitHub collaborators. Current application source is unchanged since the passing responsive browser checks, so those results are reused without another broad test run. Invitations, main merge and AWS redeployment are not performed in this follow-up.

- `confirmed` subsequent styling request: make `‹ 오늘 ›` transparent as well. `observed`: updated repository and scratch CSS to remove navigation button backgrounds/borders, including hover borders; keyboard focus retained. This follow-up is local only, not a new Git publication or AWS deployment.

- `confirmed` next follow-up: the user explicitly requested deploying that styling update and asked about other visitors' access. `observed`: submitted manual Amplify job `2` to the same Daylight app; unauthenticated public CSS returned HTTP 200 and its SHA-256 matched the updated repository stylesheet. Access is public, but browser-local calendar data remains separate per visitor. No new Git commit/push was requested in this follow-up.

- `confirmed`: D-015 authorizes publication of accumulated changes and the current Daylight static site to the connected AWS account.
- `observed`: copied the four scratch source files byte-for-byte into versioned `daylight/`, added scope/hosting documentation and a manual deployment script with account checking and a four-file upload allowlist. No credentials, presigned URLs, user browser data or backend data are packaged. The pending React redesign and architecture/design artifacts are included in the publication scope.
- `observed`: used the existing temporary `architecture-hosting` CLI session (root principal) for the explicitly requested static hosting operation. Created separate app `daylight-team-calendar` (`d1za53r0rfy3x6`), branch `main`, in `ap-northeast-2`. Job `1` reached `SUCCEED`; public URL is `https://main.d1za53r0rfy3x6.amplifyapp.com/`. No IAM keys, backend infrastructure, DNS records or live emails were created. The architecture atlas app was not altered.
- `observed`: TypeScript, all 23 frontend tests and production build passed. Daylight syntax checks passed after correcting a working-directory-only check invocation error. All four public asset responses were HTTP 200 and SHA-256-identical to committed-source candidates. No runtime asset changes were made after those checks.
- `observed`: deployment uses manual uploads, not Git-connected continuous deployment. Visitors' browser-local state is not synchronized across devices; deployment does not migrate local data. Hosting is publicly accessible and AWS usage billing applies. Sites tooling was not used because the user explicitly requested the existing AWS account. `click-gate` was unavailable; no Click-specific verification receipt is claimed.
