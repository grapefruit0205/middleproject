# Phase 10 Brief: Observability and Security Hardening

## Goal

요청, Scheduler, Delivery, 인프라 상태를 추적하고 최소 보안 기준을 검증한다.

## Scope

- Correlation ID와 구조화 로그
- ALB, Apache, Tomcat, Application 지표
- Scheduler/Delivery/DLQ Alarm
- IAM 최소 권한, Secret, IMDSv2
- SSM 세션 기록

## Non-goals

- 상용 SIEM
- 필요 근거 없는 WAF와 Interface Endpoint

## Definition of Done

- [ ] 한 요청을 계층별 로그에서 추적
- [ ] 주요 실패 Alarm 테스트
- [ ] SSH/Bastion/Public WEB-WAS 없음
- [ ] Secret Scan과 Terraform Security Scan 통과

## Recommended commits

- `feat: add end-to-end correlation and metrics`
- `security: harden iam instances and secrets`
