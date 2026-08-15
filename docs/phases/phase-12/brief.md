# Phase 12 Brief: Trip Domain and MCP Foundation

## Goal

출장 계획의 상태와 확정 트랜잭션을 PostgreSQL에 저장하고 REST와 MCP가 공유할 Application Service를 만든다.

## Scope

- Trip, TripEvent, NotificationPolicy, Reminder, Outbox 영속 모델
- `DRAFT`, `AWAITING_CONFIRMATION`, `CONFIRMED`, `CANCELLED`, `EXPIRED` 전이
- 출장 초안 생성, 조회, 확정, 취소 Use Case
- 구조화된 질문과 답변을 누적하는 Draft Context
- MCP Tool Schema와 기존 MCP Adapter 확장
- 배포 환경에 고정한 Demo Owner Context
- Idempotency Key와 Version 기반 낙관적 잠금
- PostgreSQL migration과 통합 테스트

## Non-goals

- 실제 지도, 날씨, 교통 Provider 호출
- Plugin 패키징과 Android 앱
- Cognito, OIDC, 공개 MCP Endpoint
- AWS 배포

## Definition of Done

- [ ] Trip 확정이 TripEvent, NotificationPolicy, Reminder, Outbox를 한 트랜잭션으로 저장한다.
- [ ] 실패한 확정 요청이 부분 상태를 남기지 않는다.
- [ ] 같은 Idempotency Key가 중복 Trip이나 Reminder를 만들지 않는다.
- [ ] REST와 MCP가 같은 Application Service를 호출한다.
- [ ] 상태 전이와 낙관적 잠금 충돌을 테스트한다.
- [ ] 전체 채팅 원문과 Secret을 저장하거나 로그에 남기지 않는다.

## Recommended commits

- `feat: add trip planning domain`
- `test: verify trip confirmation transaction`
