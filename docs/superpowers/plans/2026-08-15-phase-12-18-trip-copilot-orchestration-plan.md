# Phase 12-18 Trip Copilot Orchestration Implementation Plan

## 목표

Phase 12-18 계약을 커밋하고 기존 PowerShell runner가 별도 manifest와 state를 사용해 DeepSeek V4 Flash 구현 시도를 실행하도록 확장한다.

## 안전 경계

- 테스트는 fake CMDC만 사용한다.
- 이 작업에서 Phase 12 구현과 AWS 변경을 시작하지 않는다.
- 기존 `tools/orchestration/phases.json` 값과 Phase 01-10 실행 계약을 유지한다.
- CMDC는 commit, push, Codex review, Terraform apply를 수행하지 않는다.

## 작업 순서

1. `docs/phases/phase-12`부터 `phase-18`까지 `brief.md`와 `implement.prompt.md`를 작성한다.
2. Pester에 custom manifest 조회, dry-run, state 전이 테스트를 추가한다.
3. 새 테스트가 기존 runner의 하드코딩 때문에 실패하는지 확인한다.
4. `Invoke-Phase.ps1`과 `Set-PhaseReview.ps1`에 `-ManifestPath`를 추가한다.
5. `tools/orchestration/phases-12-plus.json`을 추가한다.
6. Pester 테스트를 다시 실행하고 기존 Phase 01-10 회귀가 없는지 확인한다.
7. `docs/orchestration/README.md`와 `docs/phases/README.md`에 Phase 12+ 절차를 추가한다.
8. Phase 12 dry-run에서 모델, effort, 100턴, branch, state 경로를 확인한다.
9. 전체 diff, JSON parse, PowerShell parse, Pester, `git diff --check`를 검증한다.
10. 구현 커밋을 만들고 브랜치를 GitHub에 push한다.

## 테스트가 잡아야 할 오류

- runner가 custom manifest를 무시하고 기존 `phases.json`을 읽음
- Phase 12를 매개변수 검증에서 거부함
- review runner가 실행 runner와 다른 manifest를 사용함
- Phase 17 외부 승인 표시가 빠짐
- DeepSeek V4 Flash 모델 또는 100턴 제한이 CMDC 인자에 전달되지 않음
- Phase 18 PASS 뒤 state가 `COMPLETE`로 바뀌지 않음
- Phase 01-10 기본 실행이 바뀜

## 검증 명령

```powershell
Invoke-Pester .\tools\orchestration\tests\PhaseOrchestrator.Tests.ps1
Get-Content .\tools\orchestration\phases-12-plus.json -Raw | ConvertFrom-Json | Out-Null
& .\tools\orchestration\Invoke-Phase.ps1 -Phase 12 -RepositoryRoot C:\middleproject -ManifestPath .\tools\orchestration\phases-12-plus.json -StatePath .\.orchestration\phase-12-18-state.json -DryRun
git diff --check
```
