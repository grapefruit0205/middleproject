# Phase 01-10 Command Code Orchestrator

Codex Desktop owns phase state and review decisions. Command Code implements one phase per process. Phases 01-09 retain `gpt-5.6-luna`, `max` effort, 100 turns, and three invocations. Phase 10 uses `gpt-5.6-terra`, `xhigh`, 50 turns for the initial attempt, 30 turns for one repair, and two invocations total. Every run uses `--auto-accept`.

The runner does not push, merge, apply Terraform, mutate AWS, store credentials, edit architecture records, write Codex reviews, or create Git commits. It stops when another coding-agent writer is active.

## Files

| Path | Purpose |
| --- | --- |
| `tools/orchestration/phases.json` | Phase branches, inputs, allowlists, result paths, and limits |
| `tools/orchestration/Invoke-Phase.ps1` | Runs one implementation attempt and moves state to `REVIEWING`, `READY`, or `BLOCKED` |
| `tools/orchestration/Set-PhaseReview.ps1` | Records the Codex review decision and advances state |
| `tools/orchestration/PhaseOrchestrator.psm1` | Prompt, process, state, and change-scope functions |
| `.orchestration/state.json` | Ignored durable state |
| `.orchestration/runs/` | Ignored stdout and stderr logs |

## Preflight

Run these commands in PowerShell 7 from `C:\middleproject`:

```powershell
cmdc --version
cmdc status
cmdc --list-models
git status --short --branch
```

Close the existing interactive `cmdc` session with `Ctrl+C` before the first real attempt. If you cannot reach its terminal, inspect the exact process ID and command line before stopping that one process. Do not use a broad process kill.

The repository must be on the branch declared for the requested phase. Phase 10 uses `codex/phase-10-observability-security`.

## Dry Run

This command validates the branch, baseline, prompt inputs, and paid invocation arguments. It does not call Command Code or create runtime state.

```powershell
& .\tools\orchestration\Invoke-Phase.ps1 `
  -Phase 10 `
  -RepositoryRoot C:\middleproject `
  -StatePath C:\middleproject\.orchestration\phase-10-state.json `
  -DryRun | Format-List
```

Confirm these values in the output:

```text
status: DRY_RUN
model: gpt-5.6-terra
effort: xhigh
maxTurns: 50
attemptNumber: 1
autoAccept: True
workingDirectory: C:\middleproject
```

## Run One Attempt

```powershell
& .\tools\orchestration\Invoke-Phase.ps1 `
  -Phase 10 `
  -RepositoryRoot C:\middleproject `
  -StatePath C:\middleproject\.orchestration\phase-10-state.json
```

The script starts one `cmdc` process and waits for it to exit. It compares the working tree before and after that process. It moves to `BLOCKED` if the process changes `HEAD`, switches branches, edits a disallowed path, or touches the Codex review.

Inspect state and logs after the command returns:

```powershell
Get-Content .\.orchestration\phase-10-state.json -Raw | ConvertFrom-Json | Format-List
Get-ChildItem .\.orchestration\runs | Sort-Object LastWriteTime -Descending
```

## Independent Review

Codex Desktop reviews every changed file, reruns the phase tests and builds, and compares the evidence with `docs/phases/phase-NN/result.md`. Command Code cannot write or approve `review.md`.

Write the review in this form:

```markdown
# Phase NN Review

- Reviewer: Codex Desktop
- Verdict: PASS

## Evidence

- `<command>`: passed

## Findings

- None.
```

Use `REVISE`, `BLOCKED`, or `AWAITING_APPROVAL` instead of `PASS` when the evidence requires it.

### Request a repair

Write concrete findings to the active phase review, then run:

```powershell
& .\tools\orchestration\Set-PhaseReview.ps1 `
  -RepositoryRoot C:\middleproject `
  -StatePath C:\middleproject\.orchestration\state.json `
  -Decision REVISE `
  -Reason 'Address the unresolved findings in review.md'
```

Run `Invoke-Phase.ps1` for the same phase again. The runner supplies the review as read-only repair input. Phases 01-09 share a three-invocation limit. Phase 10 permits only the 50-turn initial attempt and one 30-turn repair.

### Pass and advance

Stage only the active phase allowlist plus its Codex review. Commit the verified files locally, capture the commit SHA, and record `PASS`:

```powershell
git add -- <verified-phase-files> docs/phases/phase-NN/review.md
git commit -m 'feat: complete phase NN'
$nextBaseline = (git rev-parse HEAD).Trim()

& .\tools\orchestration\Set-PhaseReview.ps1 `
  -RepositoryRoot C:\middleproject `
  -StatePath C:\middleproject\.orchestration\state.json `
  -Decision PASS `
  -Reason 'Independent review and verification passed' `
  -NextBaselineCommit $nextBaseline
```

For Phases 01 through 09, create the next branch from that reviewed commit. Read the exact name from `phases.json`:

```powershell
git switch -c codex/phase-02-reminder-core-calendar $nextBaseline
```

Phase 10 ends in `COMPLETE` only after its external approval gate and live evidence pass. Omit `-NextBaselineCommit` for the terminal Phase 10 decision.

## AWS Approval Gate

Phases 04, 05, and 10 carry `requiresExternalApproval` in the manifest. The review entry point rejects PASS until the state records explicit approval.

Phase 04 may write Terraform and run format, validation, plan, and static checks. Neither agent may run `terraform apply` or mutate AWS during Phase 04.

Phase 05 may prepare deployment configuration and an exact execution plan. Before any AWS mutation, Codex Desktop records the gate:

```powershell
& .\tools\orchestration\Set-PhaseReview.ps1 `
  -RepositoryRoot C:\middleproject `
  -StatePath C:\middleproject\.orchestration\state.json `
  -Decision AWAITING_APPROVAL `
  -Reason 'User approval required for the exact AWS plan, region, resources, and cost boundary'
```

The user reviews the exact action, target AWS account and region, resource list, deletion plan, and cost limit. After the user approves those details, record the approval:

```powershell
& .\tools\orchestration\Set-PhaseReview.ps1 `
  -RepositoryRoot C:\middleproject `
  -StatePath C:\middleproject\.orchestration\state.json `
  -Decision RESUME_AFTER_APPROVAL `
  -Reason 'User approved the documented AWS action and cost boundary' `
  -ExternalApproval
```

Codex Desktop or the user runs only the approved external command, captures evidence, and finishes the review. Command Code does not receive live AWS authority. Phase 10 uses `.orchestration/phase-10-state.json` and stops at the same gate before deployment, log injection, Alarm state changes, instance replacement, or Secret access.

## Recovery

Read the state path supplied to `Invoke-Phase.ps1` after a Desktop or PowerShell restart. Phase 10 uses `.orchestration/phase-10-state.json` so the completed Phase 01-09 state remains intact.

- `READY`: rerun the phase named in state.
- `REVIEWING`: inspect the saved logs and working tree; do not start another implementation attempt.
- `AWAITING_APPROVAL`: wait for the user. Do not infer approval from an earlier general message.
- `BLOCKED`: inspect the recorded reason and working tree. Do not delete or revert files automatically.
- `COMPLETE`: stop the loop.

If state names a different phase than the branch, stop and inspect Git history and state. Do not rewrite state by hand. The runner never pushes or merges. Codex pushes verified commits under the project's standing backup instruction; merging into `main` requires an explicit review decision.
