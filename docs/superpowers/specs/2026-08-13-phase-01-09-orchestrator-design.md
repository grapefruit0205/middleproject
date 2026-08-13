# Phase 01–09 Command Code Orchestrator Design

## Purpose

Automate the project workflow from the current Phase 01 implementation through Phase 09 without letting the implementation agent approve its own work. Command Code (`cmdc`) implements one phase at a time with `gpt-5.6-luna`, maximum reasoning effort, and auto-accept enabled. Codex Desktop remains the outer supervisor and independently verifies every phase before the next phase starts.

The orchestrator preserves the existing Phase 01 backend commits and untracked frontend work. It does not restart Phase 01 or discard local changes.

## Scope

The automation covers:

- Phase 01 through Phase 09 implementation
- one Command Code session per phase
- phase-specific prompt composition from the committed brief and implementation prompt
- local command and test execution
- implementation logs and machine-readable state
- independent Codex review
- at most three Command Code invocations per phase: one initial attempt and two retry or repair attempts
- local Git commits and phase branch transitions after `PASS`

The automation does not grant unattended authority for:

- `terraform apply`, AWS resource creation, modification, or deletion
- purchases, billing changes, or cost-bearing service activation
- secret entry or credential creation
- remote push, pull request creation, or merge
- architecture or invariant changes
- destructive Git history operations

## Selected Approach

Codex Desktop is the state-machine owner. It invokes a repository-owned PowerShell runner for exactly one Command Code implementation attempt, waits for the process to exit, then performs the review itself. This keeps implementation and approval independent while retaining the repository and conversation context in one Desktop goal.

Two alternatives were rejected:

1. A single interactive `cmdc` session running every phase cannot provide an independent review gate and can continue after a false success report.
2. A standalone script using `codex exec` as reviewer is easier to run headlessly, but it moves the required review away from Codex Desktop and makes user intervention less visible.

Official OpenAI documentation supports long-running Desktop goals with explicit outcomes, constraints, and verification criteria. It also warns against concurrent write access to the same files. The design therefore runs only one writer at a time.

## Components

### Phase manifest

`tools/orchestration/phases.json` defines Phase 01–09 metadata:

- phase number and slug
- brief and implementation-prompt paths
- allowed implementation paths
- expected result and review paths
- whether external approval may be required
- target branch name

The manifest is data only. It does not contain secrets, credentials, or shell fragments.

### Prompt composer

The PowerShell module reads the phase brief, the committed implementation prompt, `project-invariants.md`, and the current baseline commit. It produces a runtime prompt that:

- replaces `<BASE_COMMIT>` without editing the committed prompt
- names the exact allowed paths
- records the selected model and execution mode
- forbids push, merge, rebase, reset, architecture changes, and live AWS mutation
- requires truthful commands and exit codes in the phase `result.md`
- instructs Command Code to preserve valid existing work and start at the first incomplete acceptance criterion

For Phase 01 only, the prompt acknowledges the existing backend commits and frontend working tree so the agent continues rather than recreates them.

### Command Code runner

`tools/orchestration/Invoke-Phase.ps1` runs one attempt:

```text
cmdc -p <runtime-prompt>
  --model gpt-5.6-luna
  --effort max
  --auto-accept
  --max-turns 100
  --output-format json
```

The runner supports dependency injection for a fake `cmdc` executable during tests. It captures stdout, stderr, exit code, start and finish timestamps, baseline commit, and working-tree snapshots. Runtime logs live under a Git-ignored `.orchestration/` directory.

The runner refuses to start when another Command Code or OpenCode writer is active. The existing interactive Command Code process must therefore be closed before the first orchestrated attempt.

### Codex Desktop supervisor

`docs/orchestration/phase-01-09-desktop-goal.md` contains the goal prompt used in this Desktop task. For each phase, Codex Desktop:

1. confirms the expected branch and baseline commit;
2. invokes the one-phase runner;
3. checks the runner exit code and implementation log;
4. inspects every changed and untracked file;
5. rejects changes outside the phase allowlist;
6. reruns the phase's relevant tests and builds independently;
7. compares observed evidence with `result.md`;
8. writes `review.md` with `PASS`, `REVISE`, `BLOCKED`, or `AWAITING_APPROVAL`;
9. commits a passing phase locally and creates the next phase branch;
10. starts the next phase only after `PASS`.

Command Code never edits `review.md` and never decides whether a phase passed.

## State Machine

The durable state file uses these states:

```text
READY
  -> IMPLEMENTING
  -> REVIEWING
      -> PASS -> next phase READY
      -> REVISE -> IMPLEMENTING (maximum two repairs)
      -> AWAITING_APPROVAL -> REVIEWING after user approval
      -> BLOCKED -> stop
```

Unexpected process termination, invalid JSON output, out-of-scope changes, a missing result file, or exhausted repair attempts produce `BLOCKED`. The orchestrator never interprets those conditions as success.

State updates are atomic: write a temporary file, then replace the current state file. Each transition records the timestamp, phase, attempt, branch, baseline commit, and reason.

## Git Strategy

Phase 01 continues on `codex/phase-01-local-foundation`. Later phases use separate `codex/phase-NN-<slug>` branches created from the preceding phase's reviewed commit.

The supervisor stages only files verified for the active phase. It does not stage `.commandcode/`, `.orchestration/`, credentials, build outputs, or unrelated user changes. It does not reset, clean, or discard the working tree.

Remote push and merge are separate user-authorized operations. The local phase chain can complete through Phase 09 without mutating the GitHub repository.

## External-Action Gates

Phase 04 may generate Terraform and run formatting, validation, plan, and static checks, but it must not apply infrastructure.

Phase 05 may generate deployment configuration and an execution plan. Because its definition of done includes a live two-AZ request path and healthy targets, the supervisor changes the state to `AWAITING_APPROVAL` before any real AWS mutation. The user must explicitly approve the exact plan, expected resources, region, and cost boundary. Without that approval, Phase 05 remains incomplete and Phase 06 does not start.

Phases 06 and 07 use mocks or recorded fixtures unless the user separately authorizes a limited AWS or provider sandbox call. Missing live credentials are recorded as blockers, not bypassed.

## Failure Handling

- Command Code nonzero exit: record the output and consume one of the phase's two retry or repair attempts when the failure is transient; otherwise block.
- Test or build failure: write a `REVISE` review with exact evidence and resume the same phase with only the review findings.
- Out-of-scope file: block immediately and require Codex or user inspection. Never delete or revert it automatically.
- Architecture conflict: block and propose an ADR; do not continue implementation.
- Concurrent writer: refuse to start until the process is stopped.
- Missing executable, authentication, or model: block with the exact failed command and exit code.
- Invocation limit reached: block after the initial attempt and two retry or repair attempts to prevent an unbounded cost loop.

## Testing Strategy

The implementation follows test-first development. Tests use a fake Command Code executable and a temporary Git repository; they do not spend model credits or touch the project working tree.

Required tests:

- manifest contains exactly Phase 01–09 and valid file paths
- runtime prompt contains the real baseline SHA, model, effort, phase documents, allowlist, and safety prohibitions
- Phase 01 prompt preserves the current partial implementation
- runner captures stdout, stderr, exit code, and timestamps
- nonzero implementation exit cannot transition to review success
- out-of-scope changes produce `BLOCKED`
- all retries and `REVISE` repairs share a limit of two attempts after the initial invocation
- `AWAITING_APPROVAL` cannot advance without explicit approval
- only `PASS` advances to the next phase
- Phase 09 `PASS` terminates the application implementation loop
- dry-run mode performs no Command Code invocation and no Git mutation

The final verification includes the full orchestration test suite, a dry run against the real repository, PowerShell syntax checks, JSON validation, and a Git diff review.

## Acceptance Criteria

The orchestrator is ready to use when:

- a documented Desktop goal can start at the current Phase 01 state and target Phase 09;
- `cmdc` is always invoked with `gpt-5.6-luna`, `max`, and auto-accept;
- implementation and review roles cannot be conflated;
- the next phase cannot start without a recorded Codex `PASS`;
- two failed repair attempts stop the loop;
- live AWS and other external mutations require explicit user approval;
- runtime artifacts and Command Code metadata are excluded from Git;
- all runner tests pass without calling a paid model;
- dry-run output shows the planned Phase 01 invocation without changing the repository.

## Operational Note

Codex Desktop must remain open for the long-running goal. If the Desktop task pauses or the machine restarts, the next run reads the durable state and Git branch before resuming. It never trusts the state file alone when Git and recorded state disagree.
