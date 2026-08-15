# Phase 12-18 Trip Copilot Orchestration Design

## 목적

기존 Phase 01-10 실행 이력과 상태 파일을 보존하면서 출장 코파일럿 Phase 12-18을 같은 Codex 검토 게이트로 실행한다. Command Code는 구현만 담당하고 Codex Desktop이 변경 범위, 테스트, Git 반영, 다음 Phase 진행을 결정한다.

## 결정

Phase 12-18은 `tools/orchestration/phases-12-plus.json`과 `.orchestration/phase-12-18-state.json`을 사용한다. 공통 PowerShell 모듈은 재사용하고 `Invoke-Phase.ps1`과 `Set-PhaseReview.ps1`이 명시적인 manifest 경로를 받도록 확장한다. 기존 `phases.json`의 내용과 Phase 01-10 기본 동작은 바꾸지 않는다.

CMDC 1.24.0이 제공하는 전체 모델 식별자 `deepseek/deepseek-v4-flash`를 Phase 12~18 구현에 사용한다. 각 Phase는 `max` effort, 최초 100턴, 수정 100턴, 최대 2회 호출을 사용한다. 첫 실행 전 `cmdc --list-models` 결과에 해당 식별자가 없으면 유료 호출을 시작하지 않는다.

## Phase 범위

| Phase | 계약 | 주요 허용 경로 | 외부 승인 |
| --- | --- | --- | --- |
| 12 | Trip Domain & MCP Foundation | `backend/**` | 없음 |
| 13 | Private Car Vertical Slice | `backend/**` | 없음 |
| 14 | Travel Context & Recommendations | `backend/**` | 없음 |
| 15 | Private ChatGPT Plugin | `plugins/trip-copilot/**`, `.agents/plugins/**`, `backend/**` | 없음 |
| 16 | Android Companion | `android/**`, `backend/**` | 없음 |
| 17 | AWS 3-Tier E2E & Evidence | 애플리케이션, Plugin, Android, Terraform, Runbook | 필요 |
| 18 | Real Intercity Transport Providers | `backend/**`, Provider Runbook | 없음 |

각 Phase 디렉터리는 Codex가 소유하는 `brief.md`와 CMDC가 읽는 `implement.prompt.md`를 커밋한다. 구현자는 허용 경로 안의 `result.md`만 작성한다. Codex는 `review.md`를 작성하며 CMDC는 이 파일을 수정할 수 없다.

최종 검토와 PASS 판정은 전체 대화와 Phase 이력을 가진 Codex 메인 작업이 담당한다. 서브 에이전트는 테스트 로그나 Terraform 정적 분석처럼 범위가 좁은 증거 수집만 보조하며 독자적으로 PASS를 선언하지 않는다. Phase 12~18 검토는 `gpt-5.6-sol` / `high`로 시작한다. 예상하지 않은 Terraform change/destroy, IAM·KMS·Security Group 위험, 설계 계약 충돌, 외부 Provider의 인증·재시도·멱등성 불확실성이 남는 경우에만 해당 Phase를 `Sol/xhigh`로 재검증하며 `max`를 기본값으로 사용하지 않는다.

## 접근 통제 계약

Phase 12-18은 단일 소유자 비공개 시연으로 제한한다. Cognito와 OIDC를 구현하지 않는다.

- ChatGPT Plugin은 Secure MCP Tunnel로 Apache WEB Tier에 연결한다.
- Public ALB는 `/api/mcp`를 거부한다.
- MCP 서버는 배포 환경에 고정한 Demo Owner Context를 사용한다.
- Android는 5분 이내 만료하는 일회용 Pairing Code를 Device Token으로 교환한다.
- 서버는 Pairing Code와 Device Token의 hash만 저장한다.
- Android는 Device Token을 Keystore에 저장한다.
- 공개 배포나 다중 사용자 지원은 OAuth 2.1 IdP 도입 전까지 범위에서 제외한다.

Tool 실행 확인은 접근 통제를 대신하지 않는다. 일정 확정, 취소, 알림 변경 Tool은 접근 경로 검증과 별도로 사용자 확인과 Idempotency Key를 요구한다.

## 상태와 실행

Phase 12 전용 브랜치에서 첫 실행을 시작한다. 각 PASS 후 Codex가 검증 결과를 커밋하고 다음 Phase 브랜치를 만든다. runner는 다음 조건에서 멈춘다.

- CMDC가 HEAD나 브랜치를 변경함
- 허용 경로 밖의 파일을 변경함
- CMDC가 Codex `review.md`를 변경함
- 호출 횟수를 소진함
- Phase 17이 AWS 변경 승인에 도달함

Phase 17의 Terraform apply, 장애 주입, RDS failover, Alarm 변경, destroy는 CMDC가 실행하지 않는다. Codex 또는 사용자가 저장된 plan과 비용 경계를 검토한 뒤 별도 명령으로 실행한다.

## 테스트

Pester 테스트는 유료 모델을 호출하지 않고 다음 계약을 검증한다.

- Phase 12-18 manifest 순서와 문서 경로
- Phase 12~18 DeepSeek V4 Flash 모델, effort, 턴, 호출 제한
- Phase별 branch, allowlist, 외부 승인 표시
- custom manifest를 사용한 dry-run 인자
- custom manifest를 사용한 PASS 전이와 terminal Phase 판정
- 기존 Phase 01-10 manifest의 회귀 방지
- 별도 state 경로의 Git ignore

## 완료 조건

- Phase 12-18의 brief와 구현 프롬프트가 저장소에 존재한다.
- `Invoke-Phase.ps1`과 `Set-PhaseReview.ps1`이 같은 custom manifest를 사용한다.
- Phase 12 dry-run이 `deepseek/deepseek-v4-flash`, `max`, 100턴을 출력한다.
- Pester 전체 테스트와 `git diff --check`가 통과한다.
- README가 Phase 12 시작 명령, Codex 검토, Phase 17 승인 경계를 설명한다.
- 변경을 작업 브랜치에 커밋하고 GitHub에 push한다.
