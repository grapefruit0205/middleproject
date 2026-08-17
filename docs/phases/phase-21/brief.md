# Phase 21 · Operational Proof and 15-Minute Presentation

## Objective

Freeze the product and infrastructure story at the smallest scope that proves an
operational AI-assisted daily-itinerary workflow within a 15-minute presentation.
Phase 21 does not add another product vertical. It closes the evidence gaps left by
Phase 20, maps the existing AWS 3-Tier platform to the relevant parts of the 7 Layer
AI Stack, and produces a rehearsable presentation with measured rather than invented
outcomes.

## One-sentence project claim

> Terraform으로 구축한 AWS 3-Tier 고가용성 환경에서 ChatGPT가 private MCP를 통해
> 하루 일정을 미리보기·확정하고, PostgreSQL과 Outbox가 상태를 일관되게 저장하며,
> EventBridge Scheduler·SQS·FCM이 Android 알림을 전달하는 일정 오케스트레이션
> 플랫폼입니다.

## Scope

- Phase 20 acceptance slice closure: MCP preview/confirm, PostgreSQL persistence,
  Android timeline, versioned cancellation, and stale-notification prevention.
- One end-to-end AWS demonstration and one prerecorded fallback.
- A concise 3-Tier explanation centered on security boundaries, high availability,
  state ownership, and asynchronous notification delivery.
- A selective 7 Layer mapping. The framework is used to explain the system; it is
  not a requirement to add seven new technology stacks.
- Four evidence categories: correctness, reliability, security, and measurable
  outcome.
- A Terraform scope audit that separates core presentation architecture from
  implementation detail and genuine cleanup candidates.

## Non-goals

- Android notification-based approval or a second approval state machine.
- Full Android itinerary editing or a UI redesign.
- RAG, pgvector, embeddings, Obsidian, or silent ChatGPT-history capture.
- Bedrock, AgentCore, ECS, EKS, a self-hosted model, or multi-agent orchestration.
- A new vector database, cache, queue, load balancer, or network tier.
- Public multi-user identity, Cognito/OIDC, organization tenancy, payments, or
  transport booking.
- Presenting every Terraform resource, alarm, IAM action, test, and historical
  Phase 00-19 decision.

## Phase 21 work gates

1. **Scope freeze** — accept the keep/defer/remove decisions in
   `phase-20-scope-freeze.md`.
2. **Android closure** — run Android unit tests and `assembleDebug` with a configured
   SDK, install the APK, and verify the paired-device timeline.
3. **AWS E2E proof** — run one controlled ChatGPT MCP → PostgreSQL → Android flow and
   one cancellation/revision flow without exposing secrets or personal payloads.
4. **Operational evidence** — capture the correlation ID, MCP outcome, database state,
   outbox transition, scheduler/SQS state, notification outcome, target health, and
   Terraform no-drift result.
5. **Outcome evidence** — record a manual baseline and an automated result for the
   same scenario. Do not claim savings that were not measured.
6. **Presentation rehearsal** — complete the script in `presentation-15min.md` in
   13 minutes 30 seconds, retaining 90 seconds of contingency.

## Acceptance criteria

- The primary narrative can be explained without introducing an unimplemented AWS
  service or an unmeasured ROI claim.
- The 3-Tier request path and the asynchronous notification path are each explained
  once and use the same terms as the Terraform code.
- A preview performs no confirmed business write; confirmation is explicit,
  owner-scoped, atomic, and idempotent.
- Cancellation prevents the old reminder from being scheduled or delivered and
  produces evidence of a versioned `DELETE` outbox operation.
- The Android build and physical-device result are either PASS or shown honestly as
  an environment-gated limitation.
- Terraform remains formatted and valid; live AWS changes require a separately
  reviewed plan.

## Commit boundary

This documentation decision is independent of the existing uncommitted Phase 20
application changes. A documentation-only commit must not accidentally stage those
backend or Android files. Phase 20 code receives its own verification and commit
after the Android gate is resolved.
