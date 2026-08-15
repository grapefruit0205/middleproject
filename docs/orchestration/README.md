# Phase 01-10 Command Code Orchestrator

Codex Desktop owns phase state and review decisions. Command Code implements one phase per process. Phases 01-09 retain `gpt-5.6-luna`, `max` effort, 100 turns, and three invocations. The restarted Phase 10 uses `gpt-5.6-luna`, `max` effort, 50 turns for the initial attempt, 30 turns for one repair, and two invocations total. Every print-mode run uses `--auto-accept` and `--yolo`. Command Code 1.24.0 otherwise blocks file-write and shell tools even when auto-accept is active. The prompt and post-run HEAD, branch, review-file, and path-scope checks remain mandatory because `--yolo` removes the CLI permission prompt.

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
model: gpt-5.6-luna
effort: max
maxTurns: 50
attemptNumber: 1
autoAccept: True
workingDirectory: C:\middleproject
```

Confirm that `arguments` contains both `--auto-accept` and `--yolo`. The runner uses `--yolo` only for the local Command Code process. It does not grant live AWS authority.

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

## Phase 12-18 Trip Copilot extension

Phase 12-18은 기존 실행 이력을 바꾸지 않도록 별도 manifest와 state를 사용합니다.

| Setting | Value |
| --- | --- |
| Manifest | `tools/orchestration/phases-12-plus.json` |
| State | `.orchestration/phase-12-18-state.json` |
| CMDC implementation model | Phase 12~18 `deepseek/deepseek-v4-flash` |
| Effort | `max` |
| Turn budget | 구현·수정 호출마다 100턴 |
| Invocation limit | Phase당 유효 호출 최대 10회 |
| Codex main review | Phase 12~18 `gpt-5.6-sol` / `high` |

`attempt`는 CMDC 프로세스 결과를 runner가 회수한 호출만 센다. 정상 종료, non-zero 종료, `max_turns`, Git·허용 경로 위반은 유효 호출이다. 시작 거부, 실행 전 예외, 결과 없이 끊긴 프로세스는 횟수를 쓰지 않는다. 100턴 안에 구현이 끝나지 않거나 Codex 검토에서 결함이 발견되면, 구체적인 실패 증거를 기록하고 남은 호출 예산 안에서 새로운 100턴 보정 호출을 실행한다. 중단 상태를 복구하기 전에는 활성 CMDC 프로세스가 없고 새 작업 트리 변경이 없는지 Codex가 확인해야 한다.

실행 전 설치된 CMDC가 모델을 제공하는지 확인합니다.

```powershell
cmdc --version
cmdc status
cmdc --list-models | Select-String -SimpleMatch 'deepseek/deepseek-v4-flash'
```

Phase 12 시작 브랜치는 검증된 기준 commit에서 만듭니다.

```powershell
$verifiedBaseline = (git rev-parse HEAD).Trim()
git switch -c codex/phase-12-trip-domain-mcp-foundation $verifiedBaseline
```

PowerShell에서 manifest와 state 경로를 고정하고 dry-run을 먼저 실행합니다.

```powershell
$phase12Manifest = (Resolve-Path '.\tools\orchestration\phases-12-plus.json').Path
$phase12State = Join-Path (Resolve-Path '.').Path '.orchestration\phase-12-18-state.json'

& .\tools\orchestration\Invoke-Phase.ps1 `
  -Phase 12 `
  -RepositoryRoot C:\middleproject `
  -ManifestPath $phase12Manifest `
  -StatePath $phase12State `
  -DryRun | Format-List
```

dry-run에서 model, effort, maxTurns, branch, baseline, `--auto-accept`, `--yolo`를 확인한 뒤 한 번만 실행합니다.

Codex 메인 검증은 전 Phase에서 `gpt-5.6-sol` / `high`로 시작합니다. 예상하지 않은 Terraform change/destroy, IAM·KMS·Security Group 위험, 설계 계약 충돌, 외부 Provider의 인증·재시도·멱등성 불확실성이 발견된 경우에만 해당 Phase를 `Sol/xhigh`로 다시 검증합니다. 서브 에이전트는 증거 수집을 보조할 수 있지만 최종 PASS를 판정하지 않습니다.

```powershell
& .\tools\orchestration\Invoke-Phase.ps1 `
  -Phase 12 `
  -RepositoryRoot C:\middleproject `
  -ManifestPath $phase12Manifest `
  -StatePath $phase12State
```

Codex는 `result.md`, stdout/stderr, 전체 diff와 테스트를 검토하고 `review.md`를 작성합니다. 검토 결정을 기록할 때도 같은 manifest를 전달해야 합니다.

```powershell
& .\tools\orchestration\Set-PhaseReview.ps1 `
  -RepositoryRoot C:\middleproject `
  -StatePath $phase12State `
  -ManifestPath $phase12Manifest `
  -Decision REVISE `
  -Reason 'Address the unresolved Codex findings'
```

PASS 후 Codex가 검증 파일을 커밋하고 그 SHA를 `-NextBaselineCommit`에 전달하면 state가 다음 Phase의 branch와 baseline으로 이동합니다. 실제 Git branch는 Codex가 manifest의 이름으로 별도 생성합니다.

Phase 17은 `requiresExternalApproval`을 사용합니다. CMDC는 Terraform fmt, validate, 정적 검사, plan과 Runbook 준비까지만 수행합니다. Terraform apply, 장애 주입, RDS failover, Alarm 변경, destroy는 저장된 plan과 비용 경계를 검토한 뒤 Codex 또는 사용자가 실행합니다. Cognito/OIDC는 Phase 12-18 범위에 포함하지 않습니다.

Phase 18은 선택 확장입니다. Phase 17 Core Infra PASS 뒤 실제 Provider API 사용 승인이 없으면 Fake contract test와 설정 경계까지만 수행합니다.
