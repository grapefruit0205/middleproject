# Phase 17 DeepSeek Implementation Prompt

기준 commit: `<BASE_COMMIT>`

Project invariants, ADR-005, Phase 17 brief, `docs/runbooks/phase-11-ha-test.md`를 읽어라. 출장 코파일럿 배포 구성, Secure MCP Tunnel private ingress, Public ALB MCP 차단, Android Pairing 강제, 관측성, 검증 Runbook을 구현하라. Terraform은 fmt, validate, 정적 검사, fake 또는 승인 변수 기반 plan까지만 실행하라.

Terraform apply, AWS 변경, 장애 주입, RDS failover, Alarm 변경, destroy를 실행하지 마라. Cognito/OIDC를 추가하지 마라. Secret 값과 Tunnel Credential을 출력하거나 커밋하지 마라. Git commit이나 push를 실행하지 마라. pre-apply 증거와 남은 승인 작업을 `docs/phases/phase-17/result.md`에 기록하라.
