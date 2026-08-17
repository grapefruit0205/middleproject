# 15-Minute Presentation Runbook

## Presentation rule

The audience must understand one business problem, one 3-Tier request path, one
safe agent workflow, one asynchronous notification path, and one set of measured
results. Do not narrate the repository phase history or explain all seven AI layers
individually.

Target speaking time is **13:30**. The remaining **1:30** is reserved for a slow live
response, slide transition, or one short question.

## Timeline

| Time | Slide / action | Required message |
| ---: | --- | --- |
| 00:00-00:45 | 1. Problem and result | People describe a day conversationally, but travel order, reminders, and later changes are easy to miss. This system turns one conversation into a confirmed and revisable itinerary. |
| 00:45-01:30 | 2. Scope | ChatGPT performs orchestration through MCP; the project’s own AWS data plane stores only confirmed structured schedule data and serves Android. |
| 01:30-04:30 | 3. AWS 3-Tier architecture | Explain Public ALB → private WEB → Internal ALB → private WAS → isolated RDS, two AZs, and why each boundary exists. |
| 04:30-06:15 | 4. Agent transaction | Explain one-question validation, read-only preview, explicit confirmation, owner scope, idempotency, and atomic PostgreSQL write. |
| 06:15-07:45 | 5. Async notification and change | Explain Outbox → Scheduler → SQS/DLQ → WAS worker → FCM. Cancellation writes SQL first and emits versioned DELETE/UPSERT work; SQS is not the edit API. |
| 07:45-10:45 | 6. Live demonstration | Preview one three-item plan, confirm it, refresh Android, cancel one item, and show the revised timeline. |
| 10:45-12:00 | 7. Security and operations | Private MCP tunnel, public MCP denial, chained security groups, Secrets Manager, SSM, correlation IDs, logs, and alarms. Group controls; do not enumerate them. |
| 12:00-13:15 | 8. Evidence and outcome | Show test result, healthy targets/no-drift evidence, notification outcome, measured elapsed time, and known cost window. Distinguish measured values from targets. |
| 13:15-13:30 | 9. Close | Restate that the contribution is not another chatbot: it is an agent connected to an operable 3-Tier system with safe state changes and measurable outcomes. |

## Slide 1 — problem and result

Say:

> 여러 일정과 이동을 말로 설명하는 것은 쉽지만, 확정된 시간표로 만들고 일정이
> 바뀌었을 때 기존 알림까지 안전하게 없애는 것은 별개의 운영 문제입니다.
> Trip Copilot은 대화를 실제 일정 데이터와 변경 가능한 알림으로 연결합니다.

Do not introduce public transport APIs, widgets, model comparisons, or all previous
phases on this slide.

## Slide 2 — product boundary

Show only:

```text
ChatGPT MCP: 질문·도구 선택·미리보기·확정
AWS backend: 검증·저장·예약·감사
Android: 확정 일정 조회·알림·취소
```

Clarify that ChatGPT conversation history is not silently copied to PostgreSQL.

## Slide 3 — AWS 3-Tier architecture

Use one diagram and this order:

1. Public ALB is the trusted HTTPS entry for Android/browser traffic.
2. WEB is private and stateless; Apache proxies REST and exposes MCP only to a
   loopback tunnel client.
3. Internal ALB prevents direct public access to WAS and performs readiness routing.
4. WAS contains Spring Boot business rules, MCP/REST adapters, provider clients, and
   the notification worker.
5. RDS is isolated, encrypted, Multi-AZ, and accepts PostgreSQL only from WAS.
6. WEB and WAS each have two ASG-managed instances across two AZs.

Finish with four reasons: **보안 경계, 장애 격리, 독립 교체, 상태 중앙화**.

## Slide 4 — selective 7 Layer mapping

Do not create seven implementation slides. Use one compact mapping:

| 7 Layer group | This project |
| --- | --- |
| 01 Infrastructure | Terraform-managed AWS 3-Tier, two AZs, RDS, Scheduler/SQS, CloudWatch |
| 02-04 Model/Data/Business context | ChatGPT remains external; closed MCP schemas and `DayPlan–ScheduleItem–TravelLeg–Reminder` provide structured context without RAG |
| 05 Agent Operations | Preview, confirm, cancel, retry, idempotency, version checks, and audit |
| 06 Governance/Security | Private MCP, device bearer token, owner scope, least-privilege IAM, Secrets Manager, explicit approval |
| 07 Outcome | E2E completion, duplicate prevention, stale-notification prevention, elapsed time, and deployment cost window |

The important sentence is: **7 Layer is an evaluation lens, not seven new services.**

## Slide 5 — synchronous and asynchronous paths

### Synchronous business transaction

```text
preview_day_plan (read-only)
→ user confirms
→ confirm_day_plan
→ one PostgreSQL transaction
   ├─ DayPlan / ScheduleItem / TravelLeg
   ├─ Reminder / NotificationPolicy
   └─ schedule_outbox UPSERT
```

### Asynchronous delivery

```text
Outbox reconciler
→ EventBridge Scheduler
→ due time
→ SQS / DLQ
→ WAS worker
→ FCM
```

For a changed/cancelled item, WAS updates PostgreSQL and inserts versioned
`DELETE`/`UPSERT` outbox work. Do not say that Android sends schedule edits directly
to SQS.

## Slide 6 — demonstration script

1. Submit the fixed three-item scenario from `phase-20-scope-freeze.md`.
2. Answer at most one place-selection question.
3. Show the timeline and notification times before confirmation.
4. State that no confirmed plan/reminder/outbox row exists at preview.
5. Confirm once and show the MCP result.
6. Refresh Android and show the ordered itinerary.
7. Cancel the middle item with the current plan version.
8. Show the compacted Android timeline and the old notification’s cancelled state.

Do not debug a provider or device on stage. If any live step exceeds 20 seconds,
switch to the prerecorded sequence and continue explaining the same evidence.

## Slide 7 — security and reliability

Limit the spoken list to five controls:

1. Public MCP denied; Secure MCP Tunnel uses a WEB loopback listener.
2. Security boundaries allow only ALB → WEB → Internal ALB → WAS → RDS.
3. Secrets are loaded from exact Secrets Manager ARNs; no SSH ingress is used.
4. Explicit approval, owner scope, idempotency, and optimistic versions protect
   state-changing tools.
5. Outbox, SQS/DLQ, status re-checks, correlation IDs, and alarms protect delivery.

Keep detailed IAM policies, log retention, bucket controls, and individual alarms in
backup slides.

## Slide 8 — evidence and outcome

Use a small scorecard. Populate it only with measured values collected during Phase
21:

| Indicator | Baseline / target | Measured result |
| --- | --- | --- |
| Manual itinerary setup time | Measure the same three-item scenario manually | TBD |
| MCP-to-confirmed-plan elapsed time | Compare with the manual baseline | TBD |
| Canonical E2E completion | 1 complete flow without manual DB repair | TBD |
| Duplicate confirmed rows on replay | 0 | TBD |
| Stale notification after cancellation | 0 | TBD |
| Android confirmation-to-visible latency | Record p50/p95 only after repeated runs | TBD |
| Deployment cost/window | Use actual AWS billing evidence and runtime window | TBD |

Do not extrapolate a yearly saving from a single-user demonstration. Present the
measurement method and the observed demo result.

## Backup slides / expected questions

- Why two ALBs instead of direct Spring Boot exposure?
- Why Outbox plus Scheduler plus SQS?
- Why PostgreSQL instead of storing state in ChatGPT?
- Why no RAG, pgvector, Bedrock, or AgentCore?
- Why is Cognito/OIDC absent from the private single-owner demo?
- Why are the ASGs fixed at two instances rather than load-scaled?
- What is the teardown and cost-control process?

## Final sentence

> 핵심은 AI가 문장을 생성했다는 사실이 아니라, 제한된 권한으로 실제 업무 상태를
> 변경하고, 실패와 중복을 통제하며, 그 결과를 AWS와 Android에서 검증할 수 있게
> 만들었다는 점입니다.
