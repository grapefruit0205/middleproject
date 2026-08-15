# Phase 11 Result: Deployed Baseline Verification

- Status: `DEPLOYED_BASELINE_VERIFIED_HA_EXPERIMENTS_PENDING`
- Apply base commit: `3a5c77d`
- Apply started: `2026-08-15T02:04:58Z` / `2026-08-15 11:04:58 +09:00` (Asia/Seoul)
- Baseline verified: `2026-08-15T02:58:12Z` / `2026-08-15 11:58:12 +09:00` (Asia/Seoul)
- Region: `ap-northeast-2`
- AWS account: `[MASKED]` (approved account matched)

## Outcome

The approved real-certificate Plan was applied and created the 90-resource HA baseline. The first boot exposed four deployment defects: a Windows-authored ZIP was not portable to Linux `unzip`, Apache heredoc expansion treated `$1` as an unset shell argument, the Tomcat bootstrap inserted a Valve inside a multiline XML opening tag, and it evaluated the Secrets Manager lookup too early. Narrow behavior tests reproduced the three template defects before the source was fixed.

The frontend artifact was repacked with portable ZIP entry separators. Corrective plans changed only the frontend object and WEB/WAS launch/Auto Scaling resources (`0 add / 5 change / 0 destroy`, then `0 add / 2 change / 0 destroy`). Failed bootstrap instances were replaced through their owning Auto Scaling Groups with desired capacity preserved. The final baseline serves the frontend and backend over HTTPS and has no Terraform drift.

The WEB and WAS health-check grace periods and rolling-refresh warmups were reduced from 600 to 300 seconds in a later `0 add / 2 change / 0 destroy` apply. Terraform updated only the two Auto Scaling Groups in place and started no instance refresh. Both tiers retained two healthy targets, all ten alarms stayed `OK`, and the post-apply plan reported no changes.

This is not the final Phase 11 PASS. No deliberate WEB/WAS failure experiment, RDS reboot/failover, Scheduler/Provider failure experiment, 15-minute rehearsal, cost snapshot, Terraform destroy, or post-cleanup inventory ran.

## Pre-Apply verification

| Check | Result |
|---|---|
| Required documents | Phase 10/11 source documents, runbook, architecture, and invariants read |
| Git base | Exact required commit; worktree initially clean |
| AWS identity | Account `[MASKED]` and region matched approved values |
| Approved real-certificate Plan | `90 add, 0 change, 0 destroy` |
| Approved Plan SHA-256 | `E77C058547CFC93858F6DED60F021E9E144022522EEFD44CC27278624E1D6519` |
| Fake/approved Plan actions | Identical resource addresses/actions; 0 action differences |
| Frontend artifact SHA-256 | `2505216995B0BD63EC12D8DE8436764C916F0F50C307EA9E3702F61CFD897474`; matched the ignored recorded metadata |
| Backend artifact SHA-256 | `473244081CEE08D2536CB8C6C0CF0AD3B75C8B359BA5F715A058A4D9528864F7`; matched the ignored recorded metadata |
| Deployed portable frontend SHA-256 | `0472119E25F0EF585E45E06BEAAC97F8B7ED29C8E6251B712710C7251EA1A710`; 12 entries, no backslash entry names, Linux extraction passed |
| Terraform contract and Linux bootstrap Pester suite | `10/10` passed, `0` failed |
| Terraform `fmt -check` | PASS |
| Terraform `validate` | PASS |
| Checkov 3.3.8 policy scan | `188` passed, `0` failed using the Phase 10 documented exclusions |

Post-apply Terraform planning returned exit code `0`: `No changes. Your infrastructure matches the configuration.`

## Observed AWS baseline

- WEB Auto Scaling Group: desired 2, InService 2, healthy 2; target group healthy 2.
- WAS Auto Scaling Group: desired 2, InService 2, healthy 2; target group healthy 2.
- RDS: `available`, Multi-AZ, encrypted, and not publicly accessible.
- HTTPS checks: `/`, `/healthz`, and `/api/actuator/health/readiness` returned HTTP 200; readiness reported `UP` and the proxied response included `X-Correlation-Id`.
- CloudWatch: Apache access/error, application, and Tomcat access log streams exist. All ten project alarms returned `OK` after the corrective rolling replacement completed.
- SSM managed-instance connectivity was online. No Session Manager session was opened, so the SSM session log group still has no stream and is not session-delivery evidence.
- WEB/WAS Auto Scaling: health-check grace period 300 seconds and rolling-refresh warmup 300 seconds; no instance replacement occurred during this tuning apply.

The short-lived imported certificate is `ISSUED` and expires at `2026-08-16 17:19:19` Asia/Seoul. It is suitable only for this bounded test; a trusted domain certificate is required for a normal browser/PWA deployment.

## Evidence gaps

Because the controlled HA experiments and cleanup did not run, none of the following is proven:

- WEB or WAS single-instance availability or recovery
- RDS Multi-AZ failover behavior
- Scheduler or Provider failure, retry, terminal, or recovery behavior
- Any observed RTO or RPO
- End-to-end SSM Session Manager log delivery
- Deliberately induced alarm transitions for the planned failure cases
- A live demo or completed 15-minute rehearsal
- Final runtime cost or Cost Explorer outcome
- Terraform teardown, AWS cleanup, or cleanup duration
- Zero downtime or any availability guarantee

The demo and portfolio documents are pending drafts, not completed evidence.

## Next gate

The stack is live and chargeable. Continue only with the separately bounded experiments in the runbook, or generate and review a destroy plan before teardown. Do not present the deployed baseline as completed HA evidence.
