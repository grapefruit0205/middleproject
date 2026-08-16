# Reliable Multi-Channel Reminder Platform

자연어로 일정과 알림 정책을 만들고, 예약·전송·확인 상태를 추적하는 멀티채널 리마인더 플랫폼입니다.

현재 `Phase 00~09`는 구현과 독립 검증을 완료했습니다. `Phase 10` Observability and Security Hardening은 로컬 검증과 Phase 11 AWS 기준선에서 로그·메트릭·알람 수집을 확인했습니다. `Phase 11`은 실제 HA 스택 배포와 애플리케이션 기준선 검증까지 완료했습니다. 출장 코파일럿은 `Phase 12` Trip Domain/MCP 기반, `Phase 13` 자차 이동, `Phase 14` 여행 맥락·추천, `Phase 15` 비공개 ChatGPT Plugin 패키징, `Phase 16` Android Companion까지 PASS했습니다. `Phase 17`은 신뢰된 HTTPS 도메인, AWS 3-Tier 재배포, OpenAI Secure MCP Tunnel과 ChatGPT 비공개 MCP 연결, Firebase 서버 설정까지 검증했으며 Android 실기기의 FCM·로컬 알람·ACK E2E만 남아 있습니다.

> AWS 상태: 2026-08-16 KST에 Phase 17 스택을 `ap-northeast-2`에 다시 배포했습니다. `https://trip.tripjunseok.site`가 Public ALB에 연결되어 있고 WEB/WAS 각 2대, Multi-AZ RDS, ALB, NAT Gateway 등 과금 리소스가 실행 중입니다. Terraform 사후 plan은 no-drift이며, 테스트가 끝나면 Runbook에 따라 철거해야 합니다.

현재 배포 화면은 연결 확인용 smoke test입니다. 화면의 `Backend ready`는 Public ALB → WEB → Internal ALB → WAS readiness 경로가 정상임을 뜻합니다. 일정 등록·조회·알림 설정용 프런트엔드 화면은 아직 구현하지 않았으며, 현재 기능 경계는 REST API와 MCP Adapter입니다.

## Architecture

```text
Android / Ops Dashboard
  -> Public ALB
  -> Apache WEB Tier
  -> Internal ALB
  -> External Tomcat WAS Tier
  -> RDS PostgreSQL Multi-AZ

ChatGPT Private Plugin
  -> Secure MCP Tunnel
  -> Apache WEB Tier
  -> Internal ALB -> WAS -> RDS

EventBridge Scheduler -> SQS / DLQ -> WAS -> Notification Provider
```

Public ALB는 `/api/mcp`를 거부합니다. Phase 12~18의 비공개 단일 사용자 시연은 Cognito/OIDC 대신 Secure MCP Tunnel과 Android 일회용 기기 페어링을 사용합니다. 공개 배포 또는 다중 사용자 지원에는 OAuth 2.1 IdP가 필요합니다.

- Frontend: React/PWA, Apache HTTP Server 2.4
- Backend: Java 21, Spring Boot 3.5, Gradle Kotlin DSL, Gradle Wrapper
- Runtime: external Tomcat 10.1, executable WAR가 아닌 `ROOT.war` 배포
- Database: PostgreSQL 16, Flyway
- Infrastructure: Terraform, AWS 서울 리전, 2개 AZ, Private WEB/WAS, DB 격리
- Operations: SSM Session Manager, SSH/Bastion 없음
- Scheduling: EventBridge Scheduler, transactional Outbox, SQS/DLQ

상세 제약은 [Project Invariants](docs/architecture/project-invariants.md), 구조 설명은 [Architecture v1.2](docs/architecture/architecture-v1.2.md)에서 확인할 수 있습니다.

## Phase progress

| Phase | 상태 | 완료 내용 | 검증 커밋 |
|---|---|---|---|
| 00 · Architecture | ✅ PASS | Architecture v1.2, ADR, 프로젝트 불변 조건과 검토 규약 확정 | `fb5cc0a` |
| 01 · Local Foundation | ✅ PASS | React 기반 화면, Spring Boot WAR, 외장 Tomcat, PostgreSQL readiness 기반 구축 | `cbc55e6` |
| 02 · Reminder Core | ✅ PASS | Event·Policy·Reminder CRUD, 상태 전이, 낙관적 잠금, Idempotency 구현 | `390676c` |
| 03 · Natural Language Parsing | ✅ PASS | 자연어 명령 파싱, Asia/Seoul 기준 시간 처리, JSON Schema 경계와 fixture 검증 | `1911fa5` |
| 04 · AWS Network | ✅ PASS | 서울 리전 2-AZ VPC, WEB/WAS/DB Subnet·Route·Security Group Terraform 구현 | `9c195f7` |
| 05 · Three-tier Deployment | ✅ PASS | Public ALB → Apache → Internal ALB → Tomcat → RDS 실배포 검증 후 전체 철거 | `bc183de` |
| 06 · Scheduler Integration | ✅ PASS | Scheduler Port, transactional Outbox, SQS/DLQ, 다중 WAS 동시성·재조정 구현 | `dfb5ff9` |
| 07 · Notification Delivery | ✅ PASS | SES Email·Push Provider 경계, Attempt 영속화, 동시 중복 발송 방지, 최소 권한 SES 정책 구현 | `222d6d7` |
| 08 · Reliability | ✅ PASS | Idempotency lease·fencing, 원자적 결과 재사용, 장애 복구 Matrix, DLQ Runbook 구현 | `81cfc51` |
| 09 · MCP Adapter | ✅ PASS | 동일 Application Service 기반 6개 제한 Tool, Principal 인증·소유권 인가, Schema·Lifecycle·Retry·Audit 구현 | `1ff7c23` |
| 10 · Observability & Security | 🟡 Live 기준선 확인 | Correlation ID, ECS JSON 로그, Micrometer, CloudWatch Agent·Logs·Metrics·Alarms, SSM/IAM/IMDSv2 보강; Phase 11 배포에서 로그 수집·Correlation ID·알람 정상 복귀 확인 | `3ea2443`; main 병합 `690b3bf` |
| 11 · HA Test, Final Demo & Portfolio | 🟡 기준선 검증·철거 완료 | Terraform `90 add / 0 change / 0 destroy` 적용 후 Linux bootstrap 결함을 테스트 우선으로 수정; WEB/WAS 각 2대, RDS Multi-AZ, ASG health grace·warmup 300초, HTTPS·readiness·Terraform no-drift 검증 완료. 장애 실험·리허설은 미실행했으며, 2026-08-15 KST에 `90 destroy` 후 잔존 inventory `0`을 확인 | 기준 `3a5c77d`; main 병합 `f5b5e13` |
| 12 · Trip Domain & MCP Foundation | ✅ PASS | Trip 상태·질문 흐름, 확정 트랜잭션, Demo Owner Context, REST/MCP 공통 Application Service | `1b32fae` |
| 13 · Private Car Vertical Slice | ✅ PASS | 자차 경로 preview·확정, 권장 출발 시각·알림, 결정적 Fake Provider, REST/MCP 공통 서비스 | `bed83f5` |
| 14 · Travel Context & Recommendations | ✅ PASS | 날씨·준비물, 장거리 전일 숙박, 동의 기반 맛집·명소, 부분 성공·출처·동시성 안전성 | `4c1f608` |
| 15 · Private ChatGPT Plugin | ✅ PASS | 개인 Marketplace용 Plugin·Skill, 고정 Demo Owner noauth MCP, Tool 설명·annotation, 확인 우선 쓰기 흐름, 결정론적 Prompt evaluation | `d57a314` |
| 16 · Android Companion | ✅ PASS | Kotlin/Compose, 5분 일회용 페어링, Keystore 토큰, Trip·Reminder·Delivery, FCM 수명주기, 정확/대체 알람·재등록·ACK | `5bb2895` |
| 17 · AWS 3-Tier E2E & Evidence | 🟡 Android 실기기 검증 대기 | `trip.tripjunseok.site` HTTPS, 3-Tier HA, Public MCP 차단, Secure MCP Tunnel 양쪽 AZ active, 17 tools, ChatGPT 비공개 MCP 연결, Firebase 서버 설정, no-drift 완료. FCM·로컬 알람·ACK 실기기 E2E 잔여 | 이번 커밋 |
| 18 · Real Intercity Providers | 📋 선택 확장 | 공식 철도·버스·항공 Provider Adapter | 구현 전 |

각 단계의 구현 증거와 Codex 독립 검토는 [`docs/phases`](docs/phases) 아래 `result.md`와 `review.md`에 기록합니다. Phase는 검토 결과가 `PASS`일 때만 다음 단계의 기준 커밋이 됩니다.

## AWS live deployment and teardown

2026-08-16 KST 현재 Phase 17 환경이 AWS 계정 끝자리 `1416`, 서울 리전에 실행 중입니다.

- Public URL: `https://trip.tripjunseok.site`
- WEB/WAS: 각 2대, 두 가용 영역의 Target 모두 healthy
- Secure MCP Tunnel: WEB 양쪽에서 active, `tools/list` 17개
- ChatGPT: 비공개 MCP 커넥터 생성·연결 완료(사용자 확인)
- Firebase push 설정: 활성화, 프로젝트 `trip-copilot-1ff7c`
- Terraform: 사후 detailed-exitcode `0`, no-drift
- Public `/api/mcp`: `403`, loopback MCP만 Tunnel에 허용

EC2/EBS, Multi-AZ RDS, Public/Internal ALB, NAT Gateway, CloudWatch, S3 등에서 비용이 발생합니다. Android E2E 또는 시연이 끝나면 아래 철거 절차와 [`Phase 17 E2E Runbook`](docs/runbooks/phase-17-trip-copilot-e2e.md)을 사용해 destroy plan을 검토하고 제거합니다. Cost Explorer는 지연되므로 철거 완료는 live inventory로 판정합니다.

### Redeploy prerequisites

재배포 전 [`Phase 11 HA Test Runbook`](docs/runbooks/phase-11-ha-test.md)을 검토하고 다음 값을 새로 승인합니다.

- AWS 계정과 `ap-northeast-2` 리전
- 신뢰되는 도메인과 같은 리전의 유효한 ACM 인증서 ARN
- 전용 S3 Terraform backend 설정 파일 경로
- Git에서 제외된 HA tfvars와 backend/frontend artifact 경로
- 예상 비용 상한, 실행 시간, 철거 담당자와 절대 종료 시각

`infra/terraform/backend.hcl.example`은 형식 예시일 뿐 실제 backend가 아닙니다. 인증서 ARN, tfvars, backend 설정, Terraform state, plan, WAR/ZIP, credential은 커밋하지 않습니다. 임시 환경에서만 두 S3 `force_destroy` 값을 허용하며, RDS 최종 스냅샷과 삭제 보호 여부도 plan 전에 명시적으로 결정합니다.

### 1. Rebuild and pin artifacts

```powershell
Push-Location frontend
npm ci --include=dev
npm test
npm run build
npm run verify:build
Pop-Location

Push-Location backend
.\gradlew.bat clean test bootWar --no-daemon
Pop-Location
```

`frontend/dist`는 Linux에서 풀 수 있도록 ZIP entry가 `/` 구분자를 사용하게 패키징하고, `backend/build/libs/ROOT.war`와 함께 Git에서 제외된 고정 runtime 경로로 복사합니다. 두 파일의 SHA-256을 기록한 뒤 plan과 apply 사이에는 다시 빌드하지 않습니다.

### 2. Initialize, plan, review, and apply

아래 경로는 예시입니다. 실제 승인된 파일 경로로 바꾸되 값 자체는 Git이나 터미널 기록에 노출하지 않습니다.

```powershell
$tfDir = (Resolve-Path 'infra/terraform').Path
$backendConfig = 'C:\approved\reminder-platform-backend.hcl'
$tfvarsPath = 'C:\approved\reminder-platform-ha.tfvars'
$planPath = 'C:\approved\reminder-platform-ha.plan'

aws sts get-caller-identity --query Account --output text
aws configure get region

terraform -chdir="$tfDir" fmt -check
terraform -chdir="$tfDir" init -input=false -reconfigure -backend-config="$backendConfig"
terraform -chdir="$tfDir" validate
terraform -chdir="$tfDir" plan -input=false -no-color -var-file="$tfvarsPath" -out="$planPath"
terraform -chdir="$tfDir" show -no-color "$planPath"
```

리소스 수, RDS Multi-AZ/삭제 정책, WEB/WAS 용량, NAT, 두 S3 bucket의 삭제 정책과 예상 비용을 검토한 뒤에만 저장된 plan을 적용합니다. placeholder 또는 fake 인증서 plan은 절대 적용하지 않습니다.

```powershell
terraform -chdir="$tfDir" apply -input=false "$planPath"
```

적용 후 Public ALB → WEB → Internal ALB → WAS readiness, CloudWatch 수집, Terraform no-drift를 확인합니다. 브라우저 경고가 발생하는 self-signed 단기 인증서는 공개 서비스나 ChatGPT MCP endpoint에 사용하지 않습니다.

### 3. Teardown after the test window

먼저 destroy plan을 저장해 대상과 개수를 검토한 후 그 파일만 적용합니다. `-auto-approve`는 사용하지 않습니다.

```powershell
$destroyPlanPath = 'C:\approved\reminder-platform-ha-destroy.plan'

terraform -chdir="$tfDir" plan -destroy -input=false -no-color `
  -var-file="$tfvarsPath" -out="$destroyPlanPath"
terraform -chdir="$tfDir" show -no-color "$destroyPlanPath"
terraform -chdir="$tfDir" apply -input=false "$destroyPlanPath"
terraform -chdir="$tfDir" state list
```

마지막으로 Runbook의 post-cleanup inventory 명령으로 EC2/EBS, RDS와 백업, ALB, NAT/EIP, S3, SQS, Scheduler, CloudWatch를 확인합니다. state가 비어 있는 것만으로 AWS에 수동 생성 리소스가 없다고 단정하지 않습니다.

## Local verification

백엔드는 저장소에 포함된 Gradle Wrapper로 빌드합니다.

```powershell
cd backend
.\gradlew.bat clean test bootWar --no-daemon
```

성공하면 외장 Tomcat에 배포할 WAR가 `backend/build/libs/ROOT.war`에 생성됩니다.

Terraform은 원격 State나 실제 AWS 변경 없이 포맷과 구문을 확인할 수 있습니다.

```powershell
terraform -chdir=infra/terraform fmt -check
terraform -chdir=infra/terraform init -backend=false
terraform -chdir=infra/terraform validate
```

실제 AWS 변경에는 별도의 검토와 승인이 필요합니다. Credential, Secret, Terraform State는 Git에 저장하지 않습니다.

## Repository guide

- [`frontend`](frontend): React/PWA WEB 리소스
- [`backend`](backend): Spring Boot 애플리케이션, 마이그레이션, 테스트
- [`infra/terraform`](infra/terraform): AWS 네트워크와 애플리케이션 계층
- [`docs/architecture`](docs/architecture): 승인된 Architecture와 불변 조건
- [`docs/adr`](docs/adr): Architecture Decision Records
- [`docs/phases`](docs/phases): Phase별 계약, 구현 결과, 독립 검토
- [`tools/orchestration`](tools/orchestration): Phase 01~10과 Phase 12~18 구현·검증 오케스트레이터
- [`plugins/trip-copilot`](plugins/trip-copilot): 로컬 MCP 연결과 출장 계획 Skill을 포함한 비공개 Plugin 패키지

Git 저장소가 기술적 Source of Truth이며, Notion의 `Reliable Multi-Channel Reminder Platform · Project Hub`는 탐색과 프로젝트 운영을 위한 허브로 사용합니다.
