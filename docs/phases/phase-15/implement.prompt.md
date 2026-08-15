# Phase 15 DeepSeek Implementation Prompt

기준 commit: `<BASE_COMMIT>`

Project invariants, ADR-005, Phase 15 brief를 읽어라. 저장소의 Plugin 구조와 MCP Adapter를 확인하고 비공개 출장 코파일럿 Plugin, Skill, Marketplace 항목을 구현하라. `.app.json`의 실제 connection ID와 Tunnel Credential은 커밋하지 마라.

MCP Tool은 단일 소유자 Tunnel 연결에서 `noauth`를 사용한다. Public ALB에 `/api/mcp`를 공개하거나 Cognito/OIDC를 추가하지 마라. 쓰기 Tool annotation, 확인, Idempotency를 검증하라. Git commit이나 push를 실행하지 마라. 결과를 `docs/phases/phase-15/result.md`에 기록하라.
