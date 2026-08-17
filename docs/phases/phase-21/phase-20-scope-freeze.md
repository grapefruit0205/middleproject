# Phase 20 Scope Freeze

## Decision

Phase 20 is already large enough to prove the product. Keep the implemented daily
itinerary slice and finish its Android/AWS evidence. Do not add Android approval
notifications, more personalization data, or another AI/data platform before the
15-minute presentation.

The core story is deliberately one workflow:

```text
ChatGPT MCP preview
→ explicit confirmation
→ PostgreSQL atomic persistence
→ Scheduler Outbox
→ EventBridge Scheduler
→ SQS/DLQ
→ WAS notification worker
→ FCM
→ Android timeline
→ versioned cancellation and stale-notification prevention
```

## Keep in Phase 20

| Capability | Why it remains |
| --- | --- |
| `DayPlan → ScheduleItem → TravelLeg → Reminder` domain | It is the minimal structured business model and acts as the project’s lightweight ontology. |
| Fixed, deadline, and flexible time semantics | A daily itinerary cannot safely calculate travel and notification times without distinguishing them. |
| Deterministic one-question validation | It demonstrates that the agent resolves missing business facts instead of guessing. |
| Read-only `preview_day_plan` | It is the human-in-the-loop safety boundary and proves that a proposal has no scheduling side effects. |
| Explicit, idempotent `confirm_day_plan` | It is the single write boundary and connects MCP, PostgreSQL, reminders, and Outbox. |
| Per-item PUSH policy and Scheduler Outbox | It proves the asynchronous infrastructure rather than merely displaying a generated plan. |
| Versioned item cancellation | It demonstrates a real operational edge case: an obsolete notification must not fire. |
| Three MCP tools only | Preview, confirm, and cancel are sufficient to show read, write, and destructive operations. |
| Paired Android read/cancel timeline | It proves that the result leaves ChatGPT and reaches the project’s own client and data plane. |

## Complete before calling Phase 20 finished

1. Configure an Android SDK and run `android\gradlew.bat test`.
2. Build `assembleDebug`, install the APK, pair the device, and load a confirmed plan.
3. Confirm one plan through ChatGPT MCP and verify the owner-scoped PostgreSQL rows.
4. Verify the Outbox reaches Scheduler and the due notification follows the
   Scheduler → SQS → WAS → FCM path.
5. Cancel one item and prove that the old reminder is cancelled, a versioned
   `DELETE` outbox row exists, and the Android timeline is recalculated.
6. Capture correlation-safe evidence without recording a device token, API key,
   raw credential, or unnecessary location payload.

## Explicitly defer from Phase 20

| Deferred item | Reason |
| --- | --- |
| Android notification buttons for plan approval | It duplicates the existing ChatGPT confirmation boundary and requires a new temporary proposal table, expiry worker, FCM approval payload, Device API, WorkManager retry, and second state machine. |
| Full itinerary editing in Android | It adds form validation and conflict resolution but does not improve the infrastructure proof. Changes can remain conversational through MCP. |
| Preferred-mode, reusable-template, and packing-note CRUD | Useful personalization, but not required for the first itinerary E2E. Existing origin favorites are sufficient for the demo. |
| Home-screen transit widget as a primary demo | It is an existing supporting feature, not part of the daily-itinerary acceptance path. |
| Live booking, payment, KTX/SRT/air inventory | Provider and legal scope is unrelated to the infrastructure objective; official handoff links remain enough. |
| User export/delete APIs | Record the data-lifecycle policy now; implementation belongs to a later public/multi-user phase. |
| A large ROI dashboard | Phase 21 needs a small measured scorecard, not a new analytics platform. |

## Permanently exclude unless the product problem changes

- RAG, vector embeddings, pgvector, and Obsidian synchronization.
- Full ChatGPT conversation capture.
- Background location tracking.
- A graph database or RDF/OWL ontology engine.
- Self-hosted models, GPU infrastructure, EKS, and multi-agent orchestration.

These technologies do not improve the current structured scheduling workflow and
would add new failure modes that cannot be defended in a 15-minute presentation.

## Primary demo scenario

Use no more than three fixed items and one cancellation:

> 내일 노량진에서 출발해서 오전 9시 강남 병원, 오전 11시 점심 약속,
> 오후 4시 대학로 공연 일정이 있어. 대중교통으로 이동하고 각 일정 15분 전에 알려줘.

The agent may ask for missing place choices one at a time. After the full preview,
confirm once, show the Android timeline, cancel the lunch item, and show the revised
timeline and notification state. If a live external provider is unstable, use the
prevalidated recording rather than changing the demo scope during the presentation.

## What not to claim

- Do not call the fixed `min=2`, `desired=2`, `max=2` groups demand-based autoscaling.
  They provide ASG-managed replacement, rolling refresh, and two-AZ capacity.
- Do not claim that the current private Demo Owner design is public multi-tenant
  authentication.
- Do not claim reservations, guaranteed arrival, or measured ROI without evidence.
- Do not claim Android PASS until the SDK build and physical-device E2E are complete.
