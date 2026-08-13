# Phase 09 Brief: MCP Adapter

## Goal

기존 Reminder Capability를 제한된 MCP Tool로 노출한다.

## Scope

- `create_reminder`
- `list_reminders`
- `get_reminder`
- `update_reminder`
- `cancel_reminder`
- `get_delivery_status`
- Tool별 인증·인가와 Audit

## Non-goals

- SQL, Shell, SSH, 임의 HTTP Tool
- MCP 전용 Business Logic

## Definition of Done

- [ ] REST와 MCP가 같은 Application Service 사용
- [ ] 사용자별 데이터 권한 테스트
- [ ] Tool Schema Validation
- [ ] MCP Retry가 중복 Reminder를 만들지 않음

## Recommended commits

- `feat: expose reminder use cases through mcp`
- `test: enforce mcp tool authorization`
