# Phase Result

- Phase: 15 — Private ChatGPT Plugin (correction pass 3)
- Branch: codex/phase-15-private-chatgpt-plugin
- Base commit: 6dd02dd (Phase 12–14 progress docs; working tree was already carrying the Phase 15 implementation)
- Result commit: not created (no commit/push per instructions)
- Implementer: Command Code CLI (focused correction on existing working tree, from Codex source-level review evidence)

## Changed files

The Phase 15 implementation lives entirely in the plugin, marketplace, backend, and this
result doc. No commit was made; the working tree is intentionally left uncommitted for
Codex review.

Backend (modified, all under `backend/`):

- `backend/src/main/java/com/middleproject/reminder/web/McpAdapterController.java` —
  `tools/list` now sets `idempotentHint` from the union of the read-only set and the
  replayable-write set: `READ_ONLY_TOOLS.contains(name) || IDEMPOTENT_TOOLS.contains(name)`.
  All six read-only tools (`list_reminders`, `get_reminder`, `get_delivery_status`,
  `next_private_car_question`, `preview_private_car_route`, `get_trip_recommendations`)
  therefore advertise `idempotentHint=true`; the write/destructive/open-world
  classifications are unchanged. All 16 tools keep the closed schemas, nonblank
  top-level descriptions, `structuredContent` + `text` results, and the 4-argument
  fixed-owner audit call.
- `backend/src/test/java/com/middleproject/reminder/McpAdapterIntegrationTest.java` —
  `annotationClassificationIsTruthful` now asserts all six read-only tools carry
  `idempotentHint=true` (in addition to the existing read-only/destructive assertions),
  and the existing retry/owner/audit assertions are unchanged.
- `backend/src/test/java/com/middleproject/reminder/Postgres16IntegrationTest.java` —
  repaired the environment-gated `postgresMcpOwnershipIdempotencyAuditAndFlywayEvidence`
  test so it is logically runnable under PostgreSQL 16 (see the note below): the seed is
  created under `demo-owner`; hostile `Principal`/`X-User-Id` are proven ignored via
  read-only calls (`list_reminders`, `get_reminder`, `get_delivery_status`); the mutation
  evidence uses a separate demo-owner reminder with a coherent update(expectedVersion=0) →
  cancel(expectedVersion=1) flow so the optimistic lock never goes stale; the audit
  reference-clearing assertion deletes the untouched version-0 seed reminder, whose single
  `get_reminder` audit row loses the reference via `on delete set null`. The previous pass
  had issued a redundant second `get_reminder` for the same seed right before the delete,
  which would have left two nulled `get_reminder` audit rows and failed the
  `count(*) = 1` assertion once `POSTGRES_TEST_*` enabled the suite; that duplicate call
  was removed (the hostile-identity `get_reminder` already proves the read contract).
- `backend/src/main/java/com/middleproject/reminder/application/McpAuditService.java` and
  the other MCP integration tests were already corrected in the previous pass (fixed-owner
  audit, single-owner noauth expectations); they are unchanged in this pass.

Plugin + marketplace (new, untracked):

- `plugins/trip-copilot/.codex-plugin/plugin.json`
- `plugins/trip-copilot/.mcp.json` — minimal documented local noauth HTTP shape: only
  `type: "http"` and `url: "http://127.0.0.1:8080/api/mcp"`. No `auth`, `headers`, or
  OAuth fields. The official local plugin validator only checks that `mcpServers` and
  each server entry are objects, so this file's inner shape is intentionally the simplest
  documented noauth transport form; the validator result does not prove deep noauth
  transport compatibility. Localhost is local-only; live ChatGPT tunnel validation
  remains Phase 17.
- `plugins/trip-copilot/skills/plan-business-trip/SKILL.md`
- `plugins/trip-copilot/skills/plan-business-trip/agents/openai.yaml`
- `plugins/trip-copilot/scripts/validate_prompt_evaluation.py` — the tool catalog is no
  longer duplicated in Python. Catalog names, top-level descriptions, and the four
  annotation hints are parsed directly from `McpAdapterController.java`
  (`TOOL_NAMES`, `TOOL_DESCRIPTIONS`, `READ_ONLY_TOOLS`, `DESTRUCTIVE_TOOLS`,
  `IDEMPOTENT_TOOLS`, `OPEN_WORLD_TOOLS`), and the `idempotentHint` source expression
  `READ_ONLY_TOOLS.contains(name) || IDEMPOTENT_TOOLS.contains(name)` is verified
  exactly. A regression that drops read-only tools from the idempotent set fails the
  evaluation (verified: the six read-only idempotency checks fail against the pre-fix
  Java and pass after the production fix).
- `plugins/trip-copilot/tests/prompt-evaluation/evaluation-set.json` — the
  `no-write-before-confirmation` and `annotation-truthfulness` cases now include the six
  read-only `idempotentHint=true` checks; every case stays deterministic and repository
  grounded.
- `.agents/plugins/marketplace.json` — private personal marketplace entry for
  `trip-copilot` (local source, `authentication: ON_USE`, no credentials).
- `docs/phases/phase-15/result.md` — this file.

No `.app.json` exists anywhere in `plugins/` or `.agents/`. No OAuth/Cognito client, no
credentials, and no business-logic duplication were added: the plugin only describes how
to drive the existing backend MCP tools safely.

## Commands executed (this pass)

| Command | Exit code | Summary |
|---|---:|---|
| `python plugins/trip-copilot/scripts/validate_prompt_evaluation.py` | 0 | Prompt evaluation passed: 10 cases / 105 checks. A temporary probe that reverted the controller to `idempotentHint = IDEMPOTENT_TOOLS.contains(name)` failed the evaluation (union-expression guard), and the restored fix passed again — the read-only idempotency checks are meaningful. |
| `python .../plugin-creator/scripts/validate_plugin.py plugins/trip-copilot` (with PYTHONPATH to `.orchestration/runtime/phase-15-validator-deps`) | 0 | Plugin validation passed. The validator checks `mcpServers` and each server entry are objects; it does not validate inner `auth`/`headers` fields, so this proves the companion envelope/plugin ingestion contract only, not deep noauth transport compatibility. |
| `python .../skill-creator/scripts/quick_validate.py plugins/trip-copilot/skills/plan-business-trip` (with PYTHONPATH + `PYTHONUTF8=1`) | 0 | Skill is valid. |
| `.\gradlew.bat test --tests McpAdapterIntegrationTest --tests PrivateCarMcpIntegrationTest --tests TravelRecommendationMcpIntegrationTest --tests TripMcpIntegrationTest --no-daemon --console=plain` | 0 | Focused MCP suites green. |
| `.\gradlew.bat test --no-daemon --console=plain` | 0 | Full backend suite: 33 suites, 219 tests, 0 failures, 0 errors, 8 skipped. All 8 skips are the environment-gated `Postgres16IntegrationTest` (no `POSTGRES_TEST_*` supplied). The PostgreSQL 16 MCP contract was NOT executed in this environment; its source contract was corrected but live PostgreSQL verification remains gated unless `POSTGRES_TEST_URL`/`POSTGRES_TEST_USERNAME`/`POSTGRES_TEST_PASSWORD` are available. |
| `git diff --check` | 0 | Clean. |
| Credential/`.app.json` scan over all changed/untracked in-scope files | 0 hits | No `.app.json`, no AWS key / private-key / token / secret patterns. |

The validator runs used the process-local `.orchestration/runtime/phase-15-validator-deps`
(`PYTHONPATH`) and a process-local `PYTHONUTF8=1`; no global or user Python configuration
was changed.

## Acceptance evidence

- Idempotency hints: `tools/list` marks all six read-only tools `idempotentHint=true`
  (exact source expression `READ_ONLY_TOOLS.contains(name) || IDEMPOTENT_TOOLS.contains(name)`
  at `McpAdapterController.java`), while `readOnlyHint`, `destructiveHint`, and
  `openWorldHint` keep the previous truthful classification. The focused
  `annotationClassificationIsTruthful` test asserts each of the six read-only tools has
  `idempotentHint=true`.
- Prompt evaluation: 10 cases / 105 checks, all deterministic and repository-grounded.
  The catalog facts are derived from `McpAdapterController.java`, not from a duplicated
  Python truth table; the six read-only idempotency checks were demonstrated to fail
  against the pre-fix Java and pass only after the production fix.
- Single-owner noauth contract: `McpAdapterController` never reads `Principal`; every
  request resolves to the deployment-fixed `DemoOwnerContext.ownerId()`. Tests prove a
  null Principal works and hostile `Principal`/`X-User-Id` cannot change owner or audit
  identity.
- Tool contract: 16 tools, closed input schemas (`additionalProperties: false`), every
  entry has `name`, nonblank top-level `description`, `inputSchema`, and `annotations`
  (`title` + four boolean hints). Results carry `structuredContent` plus model-readable
  `text`; provider partial failures/provenance are surfaced. No booking/payment claims;
  the skill instructs explicit affirmative confirmation before every non-read-only tool,
  stable idempotency keys on retry, and decline stops.
- Skill/agent: both official validators accept the plugin and the skill
  (`plugin.json` envelope plus `.mcp.json` server object; SKILL.md frontmatter).
- `.mcp.json`: minimal documented local noauth HTTP shape (`type: "http"` +
  `url: "http://127.0.0.1:8080/api/mcp"`), local-only. The official local validator's
  pass covers the companion envelope/plugin ingestion contract only; it does not prove
  deep noauth transport compatibility with ChatGPT. Live ChatGPT Developer Mode / Secure
  MCP Tunnel validation is Phase 17.

## Known limitations

- The `Postgres16IntegrationTest` suite is `@EnabledIfEnvironmentVariable`-gated on
  `POSTGRES_TEST_URL`/`POSTGRES_TEST_USERNAME`/`POSTGRES_TEST_PASSWORD` and was skipped
  (8 tests) in this environment. The MCP ownership/idempotency/audit test was repaired
  so it runs correctly when those variables are provided against PostgreSQL 16; this
  pass does not claim a live PostgreSQL 16 MCP contract run.
- Live ChatGPT Developer Mode, the Secure MCP Tunnel, MCP Inspector against the tunnel,
  and the Public ALB 403/404 check are deferred to Phase 17: no AWS/tunnel
  infrastructure exists in this environment, so those DoD items could not be executed.
  This session does not claim any live ChatGPT/tunnel/ALB check passed. The `.mcp.json`
  localhost URL is for deterministic local installation/smoke testing only (per
  SKILL.md deployment note).
- The official skill validator's `quick_validate.py` calls `SKILL.md.read_text()` with
  the locale default encoding (cp949 on this Windows machine), which cannot decode the
  UTF-8 skill; it required a process-local `PYTHONUTF8=1` override. That is a validator
  environment limitation, not a repository defect; no global/user Python configuration
  was changed.
- `McpAdapterIntegrationTest` still uses the deprecated `@SpyBean` (pre-existing,
  warning only).

## Handoff to Codex

All evidence-backed checks executed in this pass are green: prompt evaluation 10/10 cases
(105 checks), official plugin validator (envelope/ingestion contract), official skill
validator, focused MCP suites, full backend suite 219 tests with only the 8
environment-gated PostgreSQL skips, `git diff --check` clean, credential/`.app.json`
scan clean. The PostgreSQL 16 MCP contract was corrected at the source level but not
executed here; live PostgreSQL verification remains gated on `POSTGRES_TEST_*`. The four
live infrastructure checks (ChatGPT Developer Mode, Secure MCP Tunnel, MCP Inspector,
Public ALB 403/404) are honestly recorded as not executed because the Phase 17
AWS/tunnel infrastructure is absent. No commit or push was made; the working tree is
left uncommitted for Codex review.
