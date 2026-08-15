# Phase 17 Brief: AWS 3-Tier E2E and Evidence

## Goal

출장 코파일럿으로 AWS 3-Tier, 비동기 알림, 관측성, 장애 복구를 검증하고 비용 리소스를 철거한다.

## Scope

- Secure MCP Tunnel Client의 private WEB ingress
- Public ALB `/api/mcp` 차단
- Android/Dashboard Public ALB 경로
- WEB, Internal ALB, WAS, RDS 계층 증거
- Pairing과 Device Token 강제
- Scheduler, SQS, FCM/SES 전달과 ACK
- Correlation ID 기반 로그와 지표
- WEB/WAS 단일 인스턴스 장애와 ASG 복구
- Provider timeout, SQS duplicate, DLQ/Alarm 전이
- Terraform fmt, validate, plan, no-drift, destroy plan
- 비용과 cleanup inventory 증거

## Non-goals

- CMDC의 Terraform apply나 AWS 변경
- Cognito/OIDC와 공개 MCP Endpoint
- Multi-Region DR와 무중단 보장
- 승인되지 않은 RDS failover

## External approval gates

1. Terraform apply
2. WEB/WAS 장애 주입
3. RDS Multi-AZ failover
4. Alarm state mutation
5. Terraform destroy

## Definition of Done

- [ ] 저장된 apply plan과 destroy plan을 Codex가 검토한다.
- [ ] Secure MCP Tunnel 요청이 RDS까지 도달한다.
- [ ] Public ALB MCP 요청과 미페어링 Device 요청을 거부한다.
- [ ] 페어링된 Android가 Trip을 조회하고 알림을 ACK한다.
- [ ] Correlation ID로 WEB, WAS, Queue, Delivery를 추적한다.
- [ ] WEB/WAS 장애의 관찰 RTO와 한계를 기록한다.
- [ ] 검증 후 destroy와 프로젝트 inventory 0을 확인한다.

## Recommended commits

- `feat: prepare trip copilot aws integration`
- `docs: capture phase 17 evidence`
