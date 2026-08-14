# Phase 11 Evidence Index

- Status: `PRE_APPLY_VERIFIED_AWAITING_TERRAFORM_APPLY`
- Base commit: `ef38b44176a282a7da8ce48a378ba56d2c6306b4`
- Boundary: local and read-only pre-Apply verification only
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

Verification timestamp: `2026-08-14T15:21:09Z` / `2026-08-15 00:21:09 +09:00` Asia/Seoul.

No raw command output is committed. Ignored runtime material remains outside Git; private key, certificate, tfvars, Plan, state, artifact, ARN, and absolute-path contents are not reproduced here.

## Pending live evidence

| ID | Required evidence | Status | Reason |
|---|---|---|---|
| `P11-BASELINE` | Healthy deployed WEB/WAS/RDS baseline and approved read | PENDING | Terraform Apply did not run |
| `P11-EXP-01` | WEB single-instance failure and observed recovery | PENDING | No failure injection ran |
| `P11-EXP-02` | WAS single-instance failure and observed recovery | PENDING | No failure injection ran |
| `P11-EXP-03` | RDS Multi-AZ failover and observed recovery | PENDING | No RDS reboot/failover ran |
| `P11-EXP-04` | Isolated Scheduler failure and recovery | PENDING | No test schedule mutation ran |
| `P11-EXP-05` | Safe Provider failure, retry, and recovery/terminal evidence | PENDING | No safe live experiment ran |
| `P11-OBS` | Logs, metrics, Correlation ID, SSM delivery, Alarm transitions | PENDING | No deployed ingestion or transition exists |
| `P11-RTO-RPO` | Measured RTO/RPO with limitations | PENDING | No experiment timestamps exist |
| `P11-DEMO` | Completed 15-minute rehearsal | PENDING | Script is a draft only |
| `P11-COST` | Observed cost snapshot | PENDING | No application stack was applied |
| `P11-CLEANUP` | Destroy Plan, approved teardown, post-cleanup inventory | PENDING | No stack was created or destroyed |
| `P11-REVIEW` | Independent final verdict | PENDING | Phase 11 Definition of Done is unmet |

A Plan is intent, not runtime evidence. No pending row may become PASS without timestamped, redacted observations from an approved live run.
