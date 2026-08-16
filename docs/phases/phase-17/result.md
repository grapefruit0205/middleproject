# Phase 17 Result: AWS 3-Tier E2E and Evidence

## Status

`PARTIAL — trusted HTTPS, Secure MCP Tunnel, and Firebase server configuration are complete; physical Android FCM/alarm/ACK acceptance remains.`

Phase 17 must not be marked PASS until the remaining Android flow is exercised on an emulator or physical device.

## Implemented

- Public Apache denies `/api/mcp`; a separate loopback-only Apache listener proxies MCP for Secure MCP Tunnel client use.
- Tunnel installation is opt-in, pinned to an official release URL and SHA-256, and loads its runtime key from one exact Secrets Manager ARN at service start.
- WEB receives no tunnel secret permission when the tunnel is disabled.
- Firebase Admin credentials are loaded from one exact Secrets Manager ARN into memory and the temporary byte buffer is cleared after Firebase initialization.
- Server-side FCM delivery resolves the latest active, unexpired device registration for the Trip owner and sends a bounded data-only payload.
- Retryable Firebase provider failures are separated from terminal provider failures.
- WAS receives the fixed demo owner and non-secret Firebase configuration through systemd environment values; push IAM is opt-in and exact-resource scoped.
- Android accepts a non-secret deployment URL through `-PtripCopilotBaseUrl=...` instead of hardcoding an ephemeral ALB address.
- Android processes the ignored `google-services.json` only when supplied; builds remain valid when FCM is intentionally disabled.

## Test-first evidence

- Tunnel/Firebase/Android infrastructure contract: first observed RED failures, then `8 passed / 0 failed` GREEN, including the tunnel launcher ownership regression.
- FCM sender, credential provider, and Firebase gateway were introduced from compile/test RED states and brought to GREEN.
- PUSH owner routing was introduced from a focused failing service test and brought to GREEN.

## Local validation

- Backend: `clean test bootWar` — BUILD SUCCESSFUL; `ROOT.war` produced.
- Frontend: `npm ci --include=dev`, `npm run build`, `npm run verify:build` — all successful; zero reported npm vulnerabilities.
- Android: `clean testDebugUnitTest assembleDebug assembleRelease -PtripCopilotBaseUrl=https://trip.example.com` — BUILD SUCCESSFUL, 97 tasks; generated BuildConfig contains the supplied URL; debug and unsigned release APKs produced.
- Terraform source: `fmt -check`, backend-free `init`, and `validate` successful.
- Terraform plan with external integrations disabled: `91 add / 0 change / 0 destroy`.

## AWS evidence — 2026-08-16 KST

- Applied the reviewed plan in account ending `1416`, region `ap-northeast-2`.
- Deployed two WEB and two WAS instances, Public/Internal ALBs, PostgreSQL Multi-AZ, one development NAT Gateway, SQS/DLQ, Scheduler, CloudWatch, S3, and supporting network/IAM resources.
- Public health, frontend, and readiness returned 200; public `/api/mcp` returned 403.
- An SSM-executed request from private WEB initialized MCP, listed all 17 tools, created one pairing code, exchanged it without printing the code or token, and verified:
  - unauthenticated Device API: 401
  - paired Trip list: 200
  - disconnect: 204
  - revoked token: 401
- Correlation evidence reached WAS access logs for private MCP and public Device requests; application logs contained structured request completion, ALB trace root, status, route, and elapsed time.
- All 10 CloudWatch alarms were OK; the reminder queue was empty and its DLQ redrive policy was present.
- Controlled single-instance recovery, with desired capacity unchanged:
  - WEB: two healthy targets restored in 89 seconds; 0 health request failures.
  - WAS: two healthy targets restored in 169 seconds; 0 readiness failures.
- Post-recovery Terraform detailed-exitcode was 0: no drift.

## Cleanup evidence

- Fresh destroy plan: `0 add / 0 change / 91 destroy`.
- Apply result: `0 added / 0 changed / 91 destroyed` at approximately 14:54 KST, before the 18:11 KST absolute deadline.
- Terraform state entries: 0.
- Live inventory: 0 project-scoped non-terminated EC2, EBS, ASG, launch templates, ALB/target groups, NAT, RDS/manual snapshots, S3, SQS/DLQ, Scheduler groups, CloudWatch logs/alarms, IAM roles, SSM documents, and VPC resources.
- Unrelated `saa-*` resources and the pre-existing imported ACM test certificate were not deleted.

## Current live deployment — 2026-08-16 KST

- Redeployed the Phase 17 stack in account ending `1416`, region `ap-northeast-2`.
- Connected the trusted ACM certificate and Gabia DNS name `https://trip.tripjunseok.site`.
- Enabled Firebase server delivery for project `trip-copilot-1ff7c` with the Admin credential stored in an exact-resource Secrets Manager secret.
- Enabled the dedicated OpenAI Secure MCP Tunnel on both WEB instances without recording its runtime credential in Git.
- Corrected the root-owned tunnel launcher to group `tunnel-client` with mode `0750`, applied Terraform with `0 add / 2 change / 0 destroy`, and completed a `Successful` rolling refresh to Launch Template v2.
- Both replacement WEB instances report `tunnel-client` active, initialize private MCP with HTTP 200, and return the same 17 tools.
- ChatGPT private MCP connector creation and connection were completed and confirmed by the user; infrastructure-side tool discovery was independently verified on both WEB instances.
- Public frontend, health, and readiness return 200; public `/api/mcp` returns 403.
- Both WEB and both WAS targets are healthy; the post-apply Terraform detailed-exitcode is 0 with no drift.
- The stack remains live and cost-bearing until the Android acceptance test and reviewed teardown are complete.

## Remaining acceptance work

1. Build and sign an APK with `https://trip.tripjunseok.site`, then connect an emulator or physical device.
2. Pair the Android app, register FCM, create a reminder, receive its push/local alarm, and ACK it.
3. Capture correlation and delivery-state evidence without exposing pairing codes, tokens, or credentials.
4. Repeat no-drift and destroy/inventory-zero checks. Only then change `review.md` to PASS.
