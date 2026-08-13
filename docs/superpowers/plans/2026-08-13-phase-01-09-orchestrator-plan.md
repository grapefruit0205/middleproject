# Phase 01–09 Command Code Orchestrator Implementation Plan

## Objective

Build a Windows PowerShell orchestration package that lets Codex Desktop supervise one `cmdc` implementation attempt at a time from Phase 01 through Phase 09. The package must enforce phase boundaries, persist state, cap paid invocations, and stop for independent review or external approval.

## Constraints

- Preserve the current Phase 01 branch, commits, and untracked frontend work.
- Do not terminate or modify the existing interactive Command Code session during implementation.
- Do not invoke a paid model while testing the orchestrator.
- Use `gpt-5.6-luna`, `--effort max`, and `--auto-accept` in the real run plan.
- Never give `cmdc` permission to push, merge, rebase, reset, change Architecture, or mutate live AWS resources.
- Only Codex Desktop may record the review decision and advance the state.
- A phase receives at most three `cmdc` invocations: initial, retry/repair 1, retry/repair 2.
- Phase 05 stops at `AWAITING_APPROVAL` before live AWS changes.
- Runtime files live under Git-ignored `.orchestration/`.

## Tooling

- PowerShell 7.6-compatible scripts
- Pester 3.4-compatible tests already installed on the workstation
- temporary Git repositories and fake `cmdc` scripts for tests
- no additional package installation

## Task 1: Manifest and Runtime Exclusions

### Files

- Create: `tools/orchestration/phases.json`
- Create: `tools/orchestration/tests/PhaseOrchestrator.Tests.ps1`
- Create: `.gitignore`

### RED

Add tests that load the real manifest and assert observable contracts:

- exactly Phase 01–09 exist in order;
- every phase references existing brief and implementation-prompt files;
- result and review paths match the phase number;
- Phase 04 and Phase 05 are marked for external-action handling;
- `.commandcode/` and `.orchestration/` are ignored by Git.

Run:

```powershell
Invoke-Pester tools/orchestration/tests/PhaseOrchestrator.Tests.ps1
```

Confirm failure because the manifest and root ignore rules do not exist.

### GREEN

Add the smallest manifest and ignore rules that satisfy the tests. Keep phase allowlists data-only and rooted in the repository.

## Task 2: Manifest Loading and Prompt Composition

### Files

- Create: `tools/orchestration/PhaseOrchestrator.psm1`
- Modify: `tools/orchestration/tests/PhaseOrchestrator.Tests.ps1`

### RED

Add behavior tests for:

- resolving a phase by number;
- rejecting Phase 00, Phase 10, and unknown phases;
- composing a runtime prompt with a literal baseline SHA;
- including the model, effort, phase allowlist, invariant path, brief, and implementation prompt;
- including Phase 01 continuation guidance;
- prohibiting remote Git mutation, architecture changes, and live AWS mutation.

The production mutation each test catches is a wrong phase, omitted safety boundary, wrong baseline, or loss of current Phase 01 work.

Run the focused tests and confirm failure because the module or functions are absent. Implement only `Get-PhaseDefinition` and `New-PhaseRuntimePrompt`, then rerun to GREEN.

## Task 3: Durable State Machine and Invocation Limit

### Files

- Modify: `tools/orchestration/PhaseOrchestrator.psm1`
- Modify: `tools/orchestration/tests/PhaseOrchestrator.Tests.ps1`

### RED

Test real state files in a temporary directory:

- `READY -> IMPLEMENTING -> REVIEWING -> PASS` is valid;
- `PASS` advances to the next phase's `READY`;
- Phase 09 `PASS` produces terminal `COMPLETE`;
- `REVISE` returns to `READY` for the same phase;
- transient execution failure consumes the same retry budget;
- a fourth invocation is rejected;
- `AWAITING_APPROVAL` cannot leave without an explicit approval flag;
- invalid or skipped transitions are rejected;
- state writes leave valid JSON and no temporary file behind.

Implement atomic `Read-OrchestrationState`, `Write-OrchestrationState`, and `Move-OrchestrationState` functions minimally.

## Task 4: Path-Scope Guard

### Files

- Modify: `tools/orchestration/PhaseOrchestrator.psm1`
- Modify: `tools/orchestration/tests/PhaseOrchestrator.Tests.ps1`

### RED

Use literal changed-path fixtures to verify:

- each phase accepts its declared implementation roots and result file;
- `review.md`, Architecture, ADR, `.commandcode/`, `.orchestration/`, and unrelated files are rejected;
- path traversal and absolute paths are rejected;
- case and separator normalization works on Windows.

Implement `Test-PhaseChangeScope` returning a structured result with allowed and rejected paths.

## Task 5: Command Code Process Boundary

### Files

- Create: `tools/orchestration/tests/fixtures/fake-cmdc.ps1`
- Modify: `tools/orchestration/PhaseOrchestrator.psm1`
- Modify: `tools/orchestration/tests/PhaseOrchestrator.Tests.ps1`

### RED

Execute the module against a fake command that mirrors the relevant Command Code boundary. Assert on the runner's results, not calls to the fake:

- arguments include the exact model, effort, auto-accept, max turns, print mode, and JSON output mode;
- stdout and stderr are saved separately;
- the real child exit code is returned;
- timestamps and baseline metadata are present;
- nonzero exit cannot produce `REVIEWING` as a success state;
- dry-run produces a plan without starting the fake process;
- an active external writer blocks a non-dry run.

Implement `New-CommandCodeRunPlan`, `Get-ActiveAgentWriter`, and `Invoke-CommandCodeAttempt`.

## Task 6: Entry Points and Review Decisions

### Files

- Create: `tools/orchestration/Invoke-Phase.ps1`
- Create: `tools/orchestration/Set-PhaseReview.ps1`
- Modify: `tools/orchestration/tests/PhaseOrchestrator.Tests.ps1`

### RED

Run the real entry points in temporary repositories and assert:

- `Invoke-Phase.ps1 -DryRun` emits a Phase 01 plan and does not change Git or state;
- a successful fake implementation moves the state to `REVIEWING`;
- a failed fake implementation records failure and preserves the phase;
- only `Set-PhaseReview.ps1` can record `PASS`, `REVISE`, `BLOCKED`, or `AWAITING_APPROVAL`;
- `PASS` requires an existing phase `review.md` whose decision is `PASS`;
- Phase 05 cannot receive `PASS` while external approval evidence is absent;
- Phase 09 `PASS` terminates rather than creating Phase 10.

Implement the smallest argument validation and module calls needed to pass.

## Task 7: Desktop Goal and Operator Runbook

### Files

- Create: `docs/orchestration/phase-01-09-desktop-goal.md`
- Create: `docs/orchestration/README.md`

Document:

- how to stop the existing interactive `cmdc` process;
- prerequisites and authentication checks;
- starting or resuming the Desktop goal;
- the exact review loop and branch naming;
- commands for dry-run, one real attempt, review recording, repair, resume, and status inspection;
- AWS approval boundaries;
- recovery after app restart or state/Git mismatch;
- the fact that remote push and merge are separate actions.

Human-facing prose is reviewed directly rather than tested with source-text assertions. Commands shown in the runbook are exercised by the integration tests or final dry run.

## Task 8: Full Verification and Commit

Run fresh:

```powershell
Invoke-Pester tools/orchestration/tests/PhaseOrchestrator.Tests.ps1
Get-Content tools/orchestration/phases.json -Raw | ConvertFrom-Json | Out-Null
& tools/orchestration/Invoke-Phase.ps1 -Phase 1 -RepositoryRoot C:\middleproject -DryRun
git diff --check
git status --short
```

Confirm:

- zero failed tests;
- manifest parses;
- dry-run selects `gpt-5.6-luna`, `max`, auto-accept, Phase 01, and the current baseline;
- dry-run makes no state, Git, or implementation changes;
- only orchestrator files, docs, and root ignore rules are staged;
- the user's `.commandcode/` and `frontend/` work is not staged.

Commit the implementation and tests only after the evidence is green.
