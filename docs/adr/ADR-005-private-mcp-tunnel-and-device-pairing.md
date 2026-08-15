# ADR-005: Private MCP Tunnel and Device Pairing

- Status: Accepted
- Date: 2026-08-15

## Context

Phase 12-18은 인프라 검증용 단일 소유자 출장 코파일럿을 추가한다. ChatGPT Plugin은 공개 제출하지 않으며 일정 생성과 취소 Tool을 제공한다. 기존 공개 ALB에 익명 MCP 쓰기 Endpoint를 열면 외부 사용자가 일정 데이터를 변경할 수 있다. Cognito와 완전한 OAuth 2.1 흐름은 단일 사용자 시연 범위를 키운다.

## Decision

ChatGPT Developer Mode는 Secure MCP Tunnel로 WEB Tier의 Apache에 연결한다. Tunnel Client는 Apache의 private listener로 요청을 전달하고 Apache는 `/api/mcp`를 Internal ALB로 proxy한다. Public ALB는 `/api/mcp`를 403 또는 404로 거부한다.

MCP 서버는 배포 환경에 고정한 Demo Owner Context를 사용한다. Phase 12-18은 Cognito와 OIDC를 배포하지 않는다. 쓰기 Tool은 사용자 확인과 Idempotency Key를 요구한다.

Android Companion은 Plugin 또는 운영 Dashboard가 만든 5분 만료 일회용 Pairing Code를 교환한다. 서버는 Demo Owner와 Device에 귀속된 Opaque Device Token을 발급한다. 서버는 Code와 Token의 hash만 저장하고 Android는 Token을 Keystore에 보관한다. 기기 연결 해제는 서버 Token 폐기와 로컬 Token 삭제를 함께 수행한다.

## Consequences

- Plugin 요청은 Public ALB를 지나지 않지만 Apache WEB, Internal ALB, Tomcat WAS, RDS 계층은 유지한다.
- Android와 운영 Dashboard는 기존 Public ALB 3-Tier 경로를 사용한다.
- 단일 소유자 시연은 사용자 가입과 로그인 화면을 구현하지 않는다.
- 공개 배포나 다중 사용자 지원에는 OAuth 2.1 IdP와 사용자별 소유권 검증이 필요하다.
- Phase 17은 Tunnel 경로 성공, Public ALB MCP 차단, 미페어링 Device 거부 증거를 보존한다.
