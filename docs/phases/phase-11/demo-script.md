# Phase 11 Fifteen-Minute Demo Script — Pending Draft

- Status: `PRE_APPLY_VERIFIED_AWAITING_TERRAFORM_APPLY`
- Script state: `PENDING_REHEARSAL`
- Evidence state: not completed

This is a proposed run-of-show, not proof that deployment or experiments occurred. Do not present placeholders as observations. If Apply and approved experiments have not completed, use the safe opening below and make no live HA claim.

## Preconditions before rehearsal

- A separately approved real-certificate Plan has been applied.
- The certificate remains issued and usable.
- Baseline health and non-personal test data are verified.
- Every failure experiment has separate impact, cost, recovery, and cleanup approval.
- Redacted evidence exists for every claim.
- Measured values replace every `[PENDING]` placeholder.

## Run-of-show

| Time | Segment | Pending speaker/action script | Evidence needed |
|---:|---|---|---|
| `00:00–01:00` | Problem and Source of Truth | Explain that creation, scheduling, dispatch, delivery, and acknowledgement are distinct states, with PostgreSQL as Source of Truth. State the evidence boundary. | Approved architecture and state model |
| `01:00–04:00` | Three-tier path | Trace `User -> Public ALB -> Apache WEB -> Internal ALB -> external Tomcat WAS -> RDS`; show private tiers, two AZs, SG references, SSM-only administration, and HTTPS entry. | Deployed topology, target health, private-address, SG, and SSM evidence `[PENDING]` |
| `04:00–08:00` | Core reminder flow | Use a non-personal fixture; show REST/MCP service reuse, Outbox, Scheduler-to-SQS dispatch, persisted attempt state, and one masked Correlation ID. | Request, DB, schedule/queue, and redacted logs `[PENDING]` |
| `08:00–11:00` | Failure and recovery | Present already captured WEB/WAS, RDS, Scheduler, and Provider timelines. State only observed impact and recovery; do not reproduce destructive actions during the presentation. | `P11-EXP-01`–`05`, timestamps, measured limitations `[PENDING]` |
| `11:00–14:00` | Observability, security, HA, cost | Show bounded logs/metrics, natural Alarm transitions, SSM logging, IMDSv2, private compute, RDS Multi-AZ, and observed cost. Separate planned controls from observations. | CloudWatch/SSM, Alarm, inventory, and cost evidence `[PENDING]` |
| `14:00–15:00` | Limits and close | Report limitations, unresolved risks, cleanup status, and remaining resources. Do not claim zero downtime. | Post-cleanup inventory and final evidence index `[PENDING]` |

## Current safe opening

> Phase 11 is pre-Apply verified and awaiting separate Terraform Apply approval. The approved Plan contains 90 additions, no changes, and no destroys, and its integrity and build artifacts were verified locally. The application stack is absent, so HA behavior, RTO/RPO, observability ingestion, Alarm transitions, cost, rehearsal, and cleanup are not yet proven.

## Rehearsal acceptance checklist

- [ ] Runtime is 15 minutes or less.
- [ ] Every live claim maps to a redacted Evidence Index entry.
- [ ] Every `[PENDING]` is replaced by an observation or explicit gap.
- [ ] Account IDs, ARNs, IPs, DNS names, emails, tokens, recipients, private paths, and personal data are absent.
- [ ] No mutation or failure injection occurs during the presentation.
- [ ] Zero-downtime language is absent.
- [ ] RTO/RPO values are measured, not inferred.
- [ ] Cleanup and remaining-resource status are truthful.

No rehearsal was performed in this session.
