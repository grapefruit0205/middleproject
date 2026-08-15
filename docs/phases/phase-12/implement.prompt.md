# Phase 12 DeepSeek Implementation Prompt

기준 commit: `<BASE_COMMIT>`

`docs/architecture/project-invariants.md`, `docs/adr/ADR-005-private-mcp-tunnel-and-device-pairing.md`, Phase 12 brief를 읽어라. 테스트를 먼저 추가하고 실패 이유를 확인한 뒤 Trip Domain, PostgreSQL migration, Application Service, REST/MCP Adapter를 구현하라. Gradle Wrapper로 테스트를 실행하라.

확정 트랜잭션은 TripEvent, NotificationPolicy, Reminder, Outbox를 함께 저장해야 한다. Demo Owner ID는 배포 환경에서 주입하고 클라이언트가 보낸 임의 사용자 ID를 신뢰하지 마라. 전체 채팅 원문, Credential, Secret을 저장하지 마라.

실제 Provider 호출, Plugin, Android, Cognito/OIDC, Terraform, AWS 변경을 구현하지 마라. Git commit이나 push를 실행하지 마라. 실행한 검증 명령과 실제 결과를 `docs/phases/phase-12/result.md`에 기록하고 Codex 검토를 요청하라.
