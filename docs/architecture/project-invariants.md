# Project Invariants

Status: Accepted  
Architecture version: 1.2  
Approved: 2026-08-13

DeepSeek와 Codex는 모든 Phase에서 이 문서를 먼저 읽는다. 변경이 필요하면 구현을 멈추고 ADR을 제안한다.

## Infrastructure invariants

1. 서비스 경로는 `User -> Public ALB -> Apache WEB -> Internal ALB -> Tomcat WAS -> RDS`를 유지한다.
2. 서울 리전 `ap-northeast-2`와 최소 2개 AZ를 사용한다.
3. WEB, WAS, DB는 서로 다른 Subnet 계층과 Security Group을 사용한다.
4. WEB/WAS 인스턴스는 Private Subnet에 두며 Public IP를 부여하지 않는다.
5. DB Subnet에는 인터넷 기본 경로를 두지 않는다.
6. 관리 접근은 SSM Session Manager만 사용한다. Bastion과 SSH 22번 규칙을 만들지 않는다.
7. WEB은 Apache HTTP Server 2.4, WAS는 외장 Tomcat 10.1을 사용한다.
8. Apache는 `/api/*`만 Internal ALB로 전달하고 Business Logic을 포함하지 않는다.
9. Spring Boot 애플리케이션은 WAR로 빌드하여 외장 Tomcat에 배포한다.
10. Terraform State, Secret, Credential은 Git에 저장하지 않는다.

## Application invariants

1. RDS의 애플리케이션 상태가 Source of Truth다.
2. LLM Parsing, DB Save, Scheduler Registration, Delivery, ACK는 서로 다른 상태다.
3. REST와 MCP는 같은 Application Service를 호출한다.
4. WAS는 Stateless하게 유지하고 사용자 세션을 인스턴스 메모리에 저장하지 않는다.
5. 모든 쓰기 요청은 Idempotency 전략을 가진다.
6. 알림 Provider를 Domain/Application Logic과 분리한다.
7. Scheduler 등록과 DB 저장 사이의 불일치는 Outbox와 Reconciliation으로 복구한다.
8. SQS 소비자는 중복 전달을 처리한다.

## Delivery invariants

1. DeepSeek는 Phase별 feature branch에서만 구현한다.
2. DeepSeek는 기준 commit SHA와 허용 경로를 프롬프트에 기록한다.
3. Codex는 diff, 테스트, 보안, Acceptance Criteria를 직접 검증한다.
4. Codex PASS 전 결과를 다음 Phase의 기반으로 사용하지 않는다.
5. `main`에는 검증된 결과만 병합한다.
