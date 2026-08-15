# Phase 15 Brief: Private ChatGPT Plugin

## Goal

출장 MCP Tool과 Skill을 개인 계정에서 설치하고 검증할 수 있는 비공개 ChatGPT Plugin으로 패키징한다.

## Scope

- `.codex-plugin/plugin.json`과 출장 Skill
- 개인 Marketplace 등록 정보
- ChatGPT Developer Mode와 Secure MCP Tunnel 연결
- Tunnel-only `/api/mcp` ingress
- 단일 Demo Owner Context와 `noauth` Tool 계약
- Tool Schema, annotation, model-readable result
- 쓰기 Tool 사용자 확인과 Idempotency Key
- MCP Inspector와 Prompt evaluation set

## Non-goals

- Universal 공개 제출과 공개 MCP Endpoint
- Cognito, OIDC, OAuth Client
- `.app.json` connection ID 커밋
- Plugin 내부 Business Logic 복제

## Definition of Done

- [ ] 개인 Marketplace에서 Plugin을 설치할 수 있다.
- [ ] Secure MCP Tunnel에서 Tool 목록과 호출이 동작한다.
- [ ] Public ALB의 `/api/mcp` 접근은 403 또는 404로 끝난다.
- [ ] 요청이 Demo Owner Context를 바꾸지 못한다.
- [ ] 쓰기 Tool이 확인 없이 실행되지 않는다.
- [ ] MCP Inspector와 ChatGPT Prompt evaluation 결과를 기록한다.

## Recommended commits

- `feat: package private trip copilot plugin`
- `test: enforce private mcp ingress contract`
