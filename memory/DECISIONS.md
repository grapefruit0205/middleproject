# DECISIONS — append-only ledger

Rules: only user-confirmed decisions are recorded. Nothing is edited or deleted. A changed decision gets a **new** entry that `supersedes D-xxx`, and the old entry receives exactly one added line: `→ superseded by D-yyy (date)`. Sequential ids, never reused. (Full protocol: ballast decision-ledger skill.)

---

## D-001 · Adopt the ballast memory structure — 2026-08-22 (user, project setup)

This project uses `memory/` as its durable brain: decisions in this ledger, unresolved items in OPEN-QUESTIONS, per-session notes in SESSION-LOG. Standing decisions are followed without relitigating; changes go through the supersede protocol.

## D-002 · Use a three-tier architecture — 2026-08-22 (user, repository architecture analysis)

The target architecture will use a three-tier structure. The concrete WEB, WAS, data, availability, and deployment choices remain open until the repository-grounded options are compared.

## D-003 · Build MyWiki in ordered, atomic Git steps — 2026-08-23 (user, implementation workflow)

Start MyWiki from the first ordered step in the supplied master prompt. Complete and verify one atomic step at a time, record it in the repository, and create a Git commit for each completed record. This decision does not choose whether MyWiki replaces the current reminder product, lives beside it, or moves to a separate repository.

## D-004 · Build MyWiki in a separate Git repository — 2026-08-23 (user, repository placement)

MyWiki does not replace or live beside the reminder product in `middleproject`. Continue it in a dedicated Git repository. The reversible records created here are handoff inputs; MyWiki runtime work belongs only in the new repository.

## D-005 · Validate the owner's ChatGPT-to-Knowledge-Commit loop first — 2026-08-23 (user, MVP focus)

The first MVP user is the repository owner. The repeated job to validate is completing a learning or design conversation in ChatGPT and then committing the valuable result into maintained knowledge through Knowledge Commit.

## D-006 · Prepare a resumable middleproject service implementation handoff — 2026-09-07 (user)

The user requested a Markdown implementation prompt and `progress.json` for completing the current reminder service direction, with continued implementation across phase boundaries. Create these artifacts for `middleproject`, not MyWiki. A phase boundary alone must not require another user approval during a subsequently authorized implementation run; a new session resumes from recorded state.

This request authorizes preparing the handoff, not executing the service phases, provisioning AWS, sending email, or making Git commits/pushes. Detailed single-owner demo defaults are provisional A-003, not additional confirmed product decisions. Existing physical three-tier decisions remain unchanged.

## D-007 · Start the continuous service-mvp implementation run — 2026-09-07 (user)

The user explicitly instructed the agent to execute `SERVICE_IMPLEMENTATION_PROMPT.md` and `progress.json`, starting at S01 and continuing across phase boundaries. This authorizes local in-scope implementation and proportional verification for S01–S04, followed by the local portion of S05.

This does not authorize AWS apply/destroy, DNS changes, live email sending, Git commits, or Git pushes. Those authorities remain false in `progress.json` until explicitly granted. External S05 evidence may remain blocked while local implementation continues.

## D-008 · Treat email-provider timeout as an unknown terminal outcome — 2026-09-07 (user-approved implementation prompt)

The active service prompt requires uncertain provider outcomes to be distinguished and forbids blind resend. Therefore a provider timeout is persisted as `DELIVERY_UNKNOWN`/`OUTCOME_UNKNOWN`, shown separately from success and failure, and is not automatically retried through SQS. A provider acceptance remains distinct from actual inbox receipt or user read evidence.

This favors duplicate-mail prevention. A future provider-status lookup or explicit operator retry policy may supersede it, but neither is part of the current MVP.

## D-009 · Apply the supplied pastel dashboard visual direction — 2026-09-08 (user)

→ superseded by D-014 (2026-09-10)

The user asked to change the web design to the feel of the attached dashboard image. Apply its warm cream background, lavender navigation, rounded pastel cards, soft dimensional details and friendly illustration to the existing reminder web service. This authorizes the frontend redesign while preserving the existing reminder workflow.

## D-010 · Publish the current service implementation and redesign — 2026-09-08 (user)

The user explicitly requested GitHub commit and push if the current work had not yet been published. This grants Git commit/push authority for the current pending middleproject service implementation, pastel redesign, and accompanying records to the existing origin repository. It is not standing authority for future unrelated changes, a main-branch merge, AWS provisioning, DNS changes, or live email. Preserve existing local history and publish on a dedicated service branch.

## D-011 · Publish README architecture documentation and explain frontend priorities — 2026-09-08 (user)

The user requested adding the architecture to the GitHub README, explaining the current structure, and recommending the next frontend implementation. This authorizes a documentation update and publication to the existing service branch, not a main merge, frontend implementation, architecture migration, AWS provisioning, or live email. Proposed frontend priorities remain recommendations, not confirmed product decisions.

## D-012 · Implement status refresh and remove the local access-code step — 2026-09-08 (user)

The user accepted the next frontend implementation and asked to remove access-code login. Implement automatic deadline/history refresh as the first recommended frontend step. The current loopback-only local preview opens without a code and retains its fixed owner; this does not authorize an unauthenticated public/AWS deployment or decide the future public identity model.

## D-013 · Publish the status-refresh and README follow-up — 2026-09-08 (user)

The user explicitly requested committing and pushing the current pending changes. This grants one-time Git commit and push authority for the D-012 automatic-refresh/local-direct-access implementation, the user-edited architecture README, and their accompanying project records on the existing `codex/service-mvp-pastel-dashboard` branch.

This is not standing publication authority and does not authorize a main-branch merge, AWS provisioning or teardown, DNS changes, live email, credentials, or unrelated future changes.

## D-014 · Apply a Calendly-inspired service visual direction — 2026-09-10 (user)

Supersedes D-009. The user requested redesigning the local service at `http://127.0.0.1:8088/` with `https://calendly.com/` as the reference. Apply a white/navy/blue palette, generous spacing, top navigation, clear calls to action and structured schedule cards while preserving the existing reminder workflows and local access model. This authorizes local implementation and preview refresh.

## D-015 · Publish accumulated changes and deploy Daylight static preview — 2026-09-10 (user)

The user explicitly requested committing/pushing the work so far and deploying the current Daylight site to the connected AWS account. This grants one-time publication of the pending service visual changes, architecture/design documents, Daylight source and associated records on the existing service branch, plus a separate AWS static Daylight deployment. Preserve the existing architecture atlas site. This does not authorize a main merge, full 3-Tier provisioning, live email delivery or treating the prototype's member selector as authentication. The static site contains no seeded personal events and still stores visitor data only in their own browser.

## D-016 · Publish Daylight sizing and mobile-menu follow-up — 2026-09-10 (user)

The user explicitly requested committing and pushing all current changes and instructions for adding three GitHub collaborators. Publish the pending transparent navigation, larger desktop sizing, mobile-menu controls and their records on the existing service branch. Explain collaborator invitations but do not grant access without identified recipients and an invitation request. This turn does not request a main merge or a new AWS deployment.
