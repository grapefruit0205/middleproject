# Phase 09 Codex Review

- Verdict: PASS
- Reviewer: Codex Desktop
- Reviewed at: 2026-08-14 (Asia/Seoul)

## Final decision

Phase 09 satisfies the MCP Adapter brief. REST and MCP reuse `ReminderService`; the MCP boundary exposes only the six approved tools; authenticated principals are owner-scoped; tool inputs are closed and validated; retries reuse the Phase 08 idempotency implementation; and successful, denied, and malformed calls are auditable without coupling audit rows to Reminder retention.

## Resolved findings

1. Caller-controlled identity headers were removed from authentication. The adapter derives identity only from the server-authenticated `Principal`, and spoofed headers do not change authorization.
2. `mcp_audit.reminder_id` uses `ON DELETE SET NULL`, preserving the existing delete path while retaining the audit record.
3. Unexpected exception details are logged server-side and never returned to MCP clients.
4. Initialization validates the declared MCP payload, negotiates to the supported version, advertises the tools capability, and accepts the required initialized notification without a JSON-RPC response body.
5. Oversized integral versions are rejected instead of overflowing into a Java `long`.
6. Delivery-status result keys are explicitly mapped and do not depend on JDBC-driver column-label casing.
7. Arbitrarily long JSON-RPC IDs and rejected tool names remain auditable; input length cannot bypass the audit insert.
8. PostgreSQL 16 executes the ownership, spoof resistance, retry idempotency, audit, Flyway V1-V6, and deletion-reference contracts rather than leaving them H2-only.

## Independent evidence

- Clean Gradle `test bootWar` with PostgreSQL 16.15 connected: PASS — 86 tests, 0 failures, 0 errors, 0 skipped.
- PostgreSQL 16.15 suite: PASS — 8 tests, 0 failures/errors/skips, six Flyway migrations.
- Focused MCP suite after Codex boundary fixes: PASS — 10 tests.
- TDD evidence: version negotiation, initialized notification, stable delivery keys, and audit length boundaries failed before their production fixes and passed afterward.
- WAR: V1-V6 present; H2 absent from production libraries.
- Exactly six approved tool names are advertised; no SQL, shell, SSH, arbitrary HTTP, credential, or secret tool exists.
- Diff whitespace, phase scope, secret, generated-artifact, and temporary-container cleanup checks: PASS.
- No AWS resource, credential, Terraform state, or live deployment was changed.

## Operational boundary

The adapter intentionally requires a verified Servlet `Principal`; deployment must supply that principal through a trusted container or upstream authentication integration. Sending `X-User-Id` or similar headers is never sufficient authentication.

## References

- MCP 2025-03-26 lifecycle: https://modelcontextprotocol.io/specification/2025-03-26/basic/lifecycle
- MCP HTTP authorization: https://modelcontextprotocol.io/specification/2025-06-18/basic/authorization
