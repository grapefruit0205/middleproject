# Phase 10 Command Code Implementation Prompt

기준 commit: `<BASE_COMMIT>`

먼저 다음 문서를 읽어라.

- `docs/architecture/project-invariants.md`
- `docs/architecture/architecture-v1.2.md`
- `docs/adr/ADR-003-ssm-over-bastion.md`
- `docs/phases/phase-10/brief.md`
- `docs/superpowers/specs/2026-08-14-phase-10-observability-security-design.md`
- `docs/superpowers/plans/2026-08-14-phase-10-observability-security-plan.md`

Java 동작은 red-green-refactor 순서로 구현하고 각 RED와 GREEN 명령의 실제 결과를 기록하라. Public ALB부터 Apache와 Internal ALB를 거쳐 Tomcat까지 Correlation ID와 ALB trace Root를 전달하고 구조화 로그에 기록하라. Scheduler, source SQS, DLQ, Delivery Failure Alarm을 정의하라. CloudWatch Agent, Logs, Metrics, ALB access logs, IAM, IMDSv2, 암호화된 `gp3` root EBS, SSM 세션 기록을 승인된 설계대로 구현하라.

OpenTelemetry, WAF, 유료 Interface Endpoint, SNS 구독은 추가하지 마라. `terraform apply`나 AWS 변경 명령을 실행하지 마라. 자격 증명, Secret 값, Terraform state를 읽거나 기록하지 마라. Checkov와 정적 계약 검사, Gradle, PostgreSQL 16, Terraform 검증 결과를 `result.md`에 사실대로 남겨라. 라이브 로그와 Alarm 전이는 확인했다고 주장하지 마라. 로컬 구현을 마치면 Codex 검토를 위해 종료하라.
