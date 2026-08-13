# Phase 05 Result: Apache/Tomcat/RDS Three-Tier Deployment

## Outcome

Implemented and live-verified the approved `development` profile in AWS account `********1416`, region `ap-northeast-2`. The full path `Public ALB -> Apache WEB -> Internal ALB -> external Tomcat WAS -> RDS PostgreSQL` returned HTTP 200. WEB and WAS each had two healthy private targets split across `ap-northeast-2a` and `ap-northeast-2c`, and all four instances were online in SSM.

The temporary stack and imported test certificate were then completely removed. No remote Git operation or production S3 backend initialization occurred.

## Implementation

- Two-AZ Public/WEB/WAS/DB network tiers and strict SG-reference chain.
- Public HTTPS ALB, Apache 2.4 WEB ASG, internal HTTP ALB, external Tomcat 10.1 WAS ASG, and PostgreSQL 16.14 gp3 Multi-AZ RDS.
- Private WEB/WAS instances with IMDSv2 and SSM only; no public IP or SSH rule.
- Terraform-managed private/versioned/encrypted S3 artifacts with checksum tracking and launch dependencies.
- RDS-managed master password; WAS receives only the secret ARN in user data and fetches the value at service start.
- Bounded bootstrap retries, a 600-second ASG health grace period, Tomcat bundled-app cleanup before `ROOT.war`, and ASG rolling refresh on Launch Template changes.
- `local` is rejected for the deployed application tier because it has no supported outbound bootstrap path; `development` and `ha` plans remain valid.

## Independent pre-deployment verification

| Check | Result |
|---|---|
| Frontend tests and production build | PASS; 4 tests, Vite build exit 0 |
| Gradle Wrapper `test bootWar` | PASS; exit 0 |
| Production WAR packaging | PASS; no H2 or embedded Tomcat libraries |
| Terraform format/validate | PASS |
| Development backendless plan | PASS; 60 create, 0 update, 0 delete |
| HA backendless plan | PASS; 59 create, 0 update, 0 delete |
| Local plan | Expected rejection; exit 1 |
| Rendered WEB/WAS Bash syntax | PASS using Git Bash |
| Secret, state, backend, no-apply, scope, and diff checks | PASS |

## Approval and live evidence

- The orchestrator recorded `AWAITING_APPROVAL`, then resumed using the user's explicit advance approval for Phase 05.
- Exact approved boundary: account ending `1416`, Seoul region, development profile, two-hour window, conservative `$2` cost ceiling, and immediate teardown.
- One two-day self-signed test certificate was imported into ACM solely for the HTTPS acceptance test; its key stayed in ignored runtime storage and was never printed or committed.
- The real-ARN saved plan matched the reviewed plan exactly: 60 creates, 0 updates, 0 deletes, no replacements, and identical resource-type counts.
- `terraform apply` completed with `60 added, 0 changed, 0 destroyed`.
- Public `/`, `/healthz`, `/api/events`, and `/api/actuator/health/readiness` each returned 200.
- WEB and WAS target groups each reported two healthy targets, one per AZ.
- All four EC2 instances had no public IPv4 address and reported SSM `Online`.
- RDS reported `available`, PostgreSQL 16.14, gp3, Multi-AZ true, publicly accessible false, and only the RDS SG.
- Live SG inspection confirmed the exact chain: internet -> Public ALB 443 -> WEB 80 -> internal ALB 80 -> WAS 8080 -> RDS 5432, with no SSH rule.
- Post-acceptance Terraform plan returned exit 0 with no changes.

## Live defect found and corrected

The first WAS boot exposed an Amazon Linux 2023 package conflict: the AMI already provided `curl-minimal`, while user data requested the conflicting `curl` package. Both WAS cloud-init runs failed at that package install. SSM diagnostics confirmed `/usr/bin/curl` was already available.

The template now omits the redundant package. The fix plan contained only three in-place updates (WAS Launch Template and the two ASGs), with no create/delete/replace actions. After the update, each failed WAS was replaced sequentially with Launch Template v2; both replacement targets became healthy and all four public checks returned 200.

## Teardown evidence

- Saved destroy plan: 0 creates, 0 updates, 60 deletes.
- Destroy apply: `0 added, 0 changed, 60 destroyed`.
- Terraform state list is empty.
- Direct AWS checks show no project VPC, active/pending NAT, ALB, RDS instance, ASG, S3 bucket, or test ACM certificate.
- The deleted NAT reports `deleted`; stale Resource Groups Tagging API entries point only to deleted/nonexistent resources.
- The local test certificate, private key, and ARN file were removed from ignored runtime storage.

No credentials, secrets, Terraform state, or build artifacts are tracked by Git.
