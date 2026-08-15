# Phase 15 Codex Review

- Decision: PASS
- Branch: `codex/phase-15-private-chatgpt-plugin`
- Baseline: `6dd02dd63d53308c3e5f719ccfcf2dd41d6861da`
- Reviewer: Codex main agent

## Accepted scope

- Repository/private `trip-copilot` plugin and personal marketplace metadata
- `plan-business-trip` skill with confirmation-first write flow and stable idempotency keys
- Local-only noauth MCP companion configuration
- Deployment-fixed `DemoOwnerContext` for MCP ownership and auditing
- Model-readable tool descriptions, closed schemas, MCP annotations, text content, and `structuredContent`
- Deterministic prompt evaluation and Phase 17 tunnel handoff documentation

## Independent evidence

- Prompt evaluation: 10 cases / 105 checks passed.
- Official plugin validator: passed with process-local PyYAML.
- Official skill validator: passed with process-local PyYAML and `PYTHONUTF8=1`.
- Focused MCP suites: 4 suites / 33 tests / 0 failures / 0 errors / 0 skipped.
- `backend` clean test: 33 suites / 219 tests / 0 failures / 0 errors / 8 skipped.
- All 8 skips are the environment-gated `Postgres16IntegrationTest`; its corrected Phase 15 source contract was reviewed but not executed without `POSTGRES_TEST_*`.
- Phase 12+ orchestration Pester suite: 8 passed / 0 failed / 0 skipped.
- `git diff --check`: passed.
- Phase path scope: 0 rejected paths.
- Secret-pattern scan: 0 matching files.
- `.app.json`: absent as required.

## Review corrections

Codex rejected intermediate worker results until these issues were corrected:

1. A fixed-owner idempotency test counted the pre-existing seed reminder incorrectly.
2. The prompt-evaluation script resolved the wrong root and initially used incompatible check data.
3. Tool descriptions were missing and the audit API retained a misleading ignored-user overload.
4. Read-only tools were documented as idempotent while the emitted annotation was false.
5. The prompt evaluator duplicated a hardcoded annotation catalog instead of deriving it from the Java adapter.
6. The skipped PostgreSQL MCP test used the wrong owner and conflicting optimistic versions.
7. The PostgreSQL audit-reference assertion issued two identical audited reads but expected one nulled row.
8. The result document overstated what the local plugin validator proves about MCP transport fields.

## Deferred evidence

- The Codex CLI plugin installation command could not be executed from this desktop environment because the packaged `codex.exe` returned an OS access-denied error. Manifest, marketplace, plugin, and skill ingestion contracts passed their official local validators, but a real install is not claimed here.
- Live ChatGPT Developer Mode, Secure MCP Tunnel, MCP Inspector, Public ALB `/api/mcp` rejection, and live PostgreSQL 16 verification remain Phase 17/environment-gated checks.

No AWS resource or public endpoint was created in Phase 15.
