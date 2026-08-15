# Phase 11 Portfolio Narrative — Pending Draft

- Status: `DEPLOYED_BASELINE_VERIFIED_HA_EXPERIMENTS_PENDING`
- Narrative state: `PENDING_LIVE_EVIDENCE`
- Publication state: not ready as a completed HA case study

## Evidence-backed statement available now

I deployed the reminder platform as an ephemeral two-AZ baseline in `ap-northeast-2` from a reviewed `90 add, 0 change, 0 destroy` Terraform Plan. The live run exposed four packaging/bootstrap defects at the Windows-to-Linux boundary. I reproduced the template failures with focused behavior tests, applied two narrowly scoped corrective plans without destroys, and verified WEB/WAS target health, HTTPS frontend and readiness responses, private encrypted Multi-AZ RDS, Correlation ID propagation, CloudWatch ingestion, alarm recovery, and a no-drift Terraform plan.

This statement demonstrates a healthy deployed baseline and evidence-driven repair. It does not yet demonstrate HA recovery, measured RTO/RPO, final cost, rehearsal, or cleanup.

## Pending final narrative script

> I designed an explicit path through Public ALB, Apache WEB, Internal ALB, external Tomcat WAS, and PostgreSQL RDS. WEB and WAS remain stateless and private while PostgreSQL holds reminder state. Scheduler, Outbox, SQS/DLQ, idempotency, and Provider boundaries separate persistence from external delivery.
>
> Before deployment, I pinned the exact Plan and artifact hashes, verified infrastructure contracts, separated fake- and real-certificate Plans, and required independent approval for Apply, each failure injection, recovery, and teardown. During the approved run, I observed `[WEB RESULT PENDING]`, `[WAS RESULT PENDING]`, `[RDS RESULT PENDING]`, `[SCHEDULER RESULT PENDING]`, and `[PROVIDER RESULT PENDING]`.
>
> Measured recovery observations were `[RTO/RPO PENDING]`, with `[LIMITATIONS PENDING]`. Correlation across ALB, Apache, Tomcat, application logs, metrics, and Alarms showed `[OBSERVABILITY RESULT PENDING]`. Observed cost was `[COST PENDING]`, and cleanup left `[CLEANUP RESULT PENDING]`.

The quoted narrative remains a draft until every placeholder maps to timestamped, redacted evidence.

## Claims not yet allowed

- The platform survived a deliberately injected WEB or WAS instance failure.
- RDS failed over within any duration.
- Scheduler or Provider failures recovered correctly in AWS.
- RTO or RPO has been measured.
- Logs and metrics were ingested or correlated across deployed layers.
- Alarms transitioned as designed.
- The demo was rehearsed or completed.
- The run met a cost target.
- Cleanup completed.
- The system achieved zero downtime or an availability guarantee.

## Finalization gate

Publish a completed Phase 11 portfolio narrative only after the remaining experiments, measured observations, cost evidence, teardown verification, a completed 15-minute rehearsal, and independent final review. Until then, the accurate status is deployed baseline verified with HA experiments pending.
