# Phase 11 Evidence Index

- Status: `DEPLOYED_BASELINE_VERIFIED_HA_EXPERIMENTS_PENDING`
- Apply base commit: `3a5c77d`
- Boundary: approved Apply, corrective rolling replacement, and non-destructive baseline verification
- Sensitive identifiers, ARNs, private paths, credentials, certificate material, state, and raw Plan contents are omitted or masked.

## Verified evidence

| ID | Evidence | Result | Source class |
|---|---|---|---|
| `P11-PRE-001` | Base and initial worktree | Exact base; initially clean | Git read-only |
| `P11-PRE-002` | AWS account/region | `[MASKED]` matched; `ap-northeast-2` matched | AWS read-only |
| `P11-PRE-003` | Approved Plan summary | `90 add, 0 change, 0 destroy` | Ignored local metadata |
| `P11-PRE-004` | Approved Plan integrity | SHA-256 `E77C058547CFC93858F6DED60F021E9E144022522EEFD44CC27278624E1D6519` | Ignored local artifact |
| `P11-PRE-005` | Fake/approved comparison | Identical addresses/actions; 0 action differences | Ignored local metadata |
| `P11-PRE-006` | Frontend artifact | SHA-256 `2505216995B0BD63EC12D8DE8436764C916F0F50C307EA9E3702F61CFD897474`; recorded match | Ignored local artifact |
| `P11-PRE-007` | Backend artifact | SHA-256 `473244081CEE08D2536CB8C6C0CF0AD3B75C8B359BA5F715A058A4D9528864F7`; recorded match | Ignored local artifact |
| `P11-PRE-008` | Terraform contracts | Pester `7/7` passed, `0` failed | Local execution |
| `P11-PRE-009` | Terraform checks | `fmt -check` PASS; `validate` PASS | Local execution |
| `P11-PRE-010` | Terraform policy scan | Checkov 3.3.8: `188` passed, `0` failed using the Phase 10 documented exclusions | Local execution |
| `P11-PRE-011` | AWS application inventory | Approved Phase 11 application resource categories absent | AWS read-only |
| `P11-PRE-012` | Imported ACM metadata | ARN `[MASKED]`; imported; `ISSUED`; unused; expires `2026-08-16 17:19:19` Asia/Seoul | AWS read-only |
| `P11-APPLY-001` | Approved Plan Apply | `90 add, 0 change, 0 destroy` completed | Terraform/AWS mutation |
| `P11-FIX-001` | Linux bootstrap regression tests | RED reproduced; final Pester suite `10/10` passed | Local execution |
| `P11-FIX-002` | Portable frontend artifact | SHA-256 `0472119E25F0EF585E45E06BEAAC97F8B7ED29C8E6251B712710C7251EA1A710`; Linux extraction passed | Ignored local artifact |
| `P11-FIX-003` | Corrective rolling plans | `0/5/0`, then `0/2/0`; limited to artifact, launch templates, and Auto Scaling Groups | Terraform/AWS mutation |
| `P11-BASELINE-001` | WEB baseline | ASG 2/2 healthy; target group 2 healthy; `/` and `/healthz` HTTP 200 | AWS/curl read-only |
| `P11-BASELINE-002` | WAS baseline | ASG 2/2 healthy; target group 2 healthy; readiness HTTP 200 `UP` | AWS/curl read-only |
| `P11-BASELINE-003` | Data baseline | RDS available, Multi-AZ, encrypted, private | AWS read-only |
| `P11-BASELINE-004` | Correlation and observability | Proxied readiness returned `X-Correlation-Id`; four application log classes had streams; all 10 alarms returned `OK` | AWS/curl read-only |
| `P11-BASELINE-005` | Post-apply drift | Terraform detailed-exitcode `0`; no changes | Terraform read-only |
| `P11-TUNE-001` | ASG timing reduction | Reviewed `0/2/0` plan changed only WEB/WAS ASGs from 600 to 300 seconds; no instance refresh; health remained 2/2 per tier | Terraform/AWS mutation and read-only verification |

Baseline verification timestamp: `2026-08-15T02:58:12Z` / `2026-08-15 11:58:12 +09:00` Asia/Seoul.

No raw command output is committed. Ignored runtime material remains outside Git; private key, certificate, tfvars, Plan, state, artifact, ARN, and absolute-path contents are not reproduced here.

## Pending live evidence

| ID | Required evidence | Status | Reason |
|---|---|---|---|
| `P11-BASELINE` | Healthy deployed WEB/WAS/RDS baseline and approved read | PASS | HTTP 200/UP, healthy target groups, private Multi-AZ RDS, and no-drift plan observed |
| `P11-EXP-01` | WEB single-instance failure and observed recovery | PENDING | No failure injection ran |
| `P11-EXP-02` | WAS single-instance failure and observed recovery | PENDING | No failure injection ran |
| `P11-EXP-03` | RDS Multi-AZ failover and observed recovery | PENDING | No RDS reboot/failover ran |
| `P11-EXP-04` | Isolated Scheduler failure and recovery | PENDING | No test schedule mutation ran |
| `P11-EXP-05` | Safe Provider failure, retry, and recovery/terminal evidence | PENDING | No safe live experiment ran |
| `P11-OBS` | Logs, metrics, Correlation ID, SSM delivery, Alarm transitions | PARTIAL | Logs, Correlation ID, and alarm recovery observed; SSM Session log delivery and controlled transitions remain pending |
| `P11-RTO-RPO` | Measured RTO/RPO with limitations | PENDING | No experiment timestamps exist |
| `P11-DEMO` | Completed 15-minute rehearsal | PENDING | Script is a draft only |
| `P11-COST` | Observed cost snapshot | PENDING | Stack is running; Cost Explorer is delayed and no final snapshot was captured |
| `P11-CLEANUP` | Destroy Plan, approved teardown, post-cleanup inventory | PENDING | Stack is still running |
| `P11-REVIEW` | Independent final verdict | PENDING | Phase 11 Definition of Done is unmet |

A Plan is intent, not runtime evidence. No pending row may become PASS without timestamped, redacted observations from an approved live run.
