# Reliable Multi-Channel Reminder Platform

자연어로 일정과 알림 정책을 만들고, 예약·전송·확인 상태를 추적하는 멀티채널 리마인더 플랫폼입니다.

현재 `Phase 00~08`은 구현과 독립 검증을 완료했습니다. 다음은 `Phase 09` MCP Adapter 단계입니다.

> AWS 상태: Phase 05에서 승인된 검증 배포를 완료한 뒤 전부 철거했습니다. 현재 프로젝트 VPC, ALB, RDS, Auto Scaling Group은 배포되어 있지 않습니다.

## Architecture

```text
User
  -> Public ALB
  -> Apache WEB Tier
  -> Internal ALB
  -> External Tomcat WAS Tier
  -> RDS PostgreSQL Multi-AZ

EventBridge Scheduler -> SQS / DLQ -> WAS -> Notification Provider
```

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
| 09 · MCP Adapter | ⏳ 예정 | REST와 동일한 Application Service를 사용하는 MCP Adapter, 인증·감사 | 예정 |

각 단계의 구현 증거와 Codex 독립 검토는 [`docs/phases`](docs/phases) 아래 `result.md`와 `review.md`에 기록합니다. Phase는 검토 결과가 `PASS`일 때만 다음 단계의 기준 커밋이 됩니다.

## Local verification

백엔드는 저장소에 포함된 Gradle Wrapper로 빌드합니다.

```powershell
cd backend
.\gradlew.bat clean test bootWar --no-daemon
```

성공하면 외장 Tomcat에 배포할 WAR가 `backend/build/libs/ROOT.war`에 생성됩니다.

Terraform은 원격 State나 실제 AWS 변경 없이 포맷과 구문을 확인할 수 있습니다.

```powershell
terraform -chdir=infra/terraform fmt -check -recursive
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
- [`tools/orchestration`](tools/orchestration): Phase 01~09 구현·검증 오케스트레이터

Git 저장소가 기술적 Source of Truth이며, Notion의 `Reliable Multi-Channel Reminder Platform · Project Hub`는 탐색과 프로젝트 운영을 위한 허브로 사용합니다.
