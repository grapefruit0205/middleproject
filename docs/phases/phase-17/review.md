# Phase 17 Codex Review

- Verdict: BLOCKED — physical Android E2E remains
- Reviewer: Codex main agent
- Date: 2026-08-16 KST

## Review conclusion

The repository implementation, local validation, trusted HTTPS deployment, private ingress boundary, pairing authorization, correlation evidence, controlled WEB/WAS recovery, Secure MCP Tunnel, user-confirmed ChatGPT MCP connection, Firebase server configuration, and no-drift check are accepted for the Phase 17 AWS baseline.

Phase 17 is not PASS because no Android emulator or physical device is currently connected. Server-to-device FCM reception, the Android local alarm, and delivery ACK therefore have not been exercised end to end.

## Independently verified evidence

- Phase 17 Pester contracts: 8 passed, 0 failed.
- Backend full test/WAR build: successful.
- Frontend install/build/build verification: successful.
- Android unit tests plus debug/release APK builds: successful with an injected non-secret HTTPS base URL.
- Terraform fmt, init/validate, reviewed `91 add` plan, apply, no-drift detailed-exitcode 0, reviewed `91 destroy` plan, and destroy: successful.
- Public MCP: 403.
- Private MCP initialize/tools: 200, 17 tools.
- Device API: unauthenticated 401; paired 200; disconnect 204; revoked token 401.
- WEB recovery: 89 seconds with zero public health failures.
- WAS recovery: 169 seconds with zero readiness failures.
- Terraform state and project-scoped AWS inventory after cleanup: 0.
- Trusted HTTPS `trip.tripjunseok.site`: frontend, health, and readiness 200; public MCP 403.
- Secure MCP Tunnel: active on both replacement WEB instances; private initialize 200 and identical 17-tool lists.
- ChatGPT private MCP connector: creation and connection completed according to user confirmation.
- Terraform permission apply: 0 added, 2 changed, 0 destroyed; rolling refresh Successful; post-apply no-drift.
- Both WEB and both WAS target groups: healthy.

## Security and architecture review

- Tunnel and push integrations are disabled by default.
- Secret permissions are exact-resource and conditional on the corresponding integration.
- No runtime API key, Firebase Admin JSON, device token, pairing code, Terraform state, tfvars, or plan is tracked.
- Public `/api/mcp` remains denied while the intended Tunnel listener is loopback-only.
- The Android backend URL is non-secret, build-time configurable, and no ephemeral deployment URL is hardcoded.
- The ignored Firebase client configuration activates its Gradle plugin only when supplied.
- No unrelated `saa-*` AWS resources were changed or removed.

## Resume condition

Resume Phase 17 with the current short-lived stack by connecting an Android device/emulator, building against `https://trip.tripjunseok.site`, and running pairing → FCM → local alarm → ACK. Then perform no-drift and inventory-zero cleanup before changing the verdict to PASS.
