# Phase 02 Brief: Reminder Core and Calendar

## Goal

구조화된 Event, Reminder, Notification Policy와 상태 전이를 구현한다.

## Scope

- Domain Model과 DB Migration
- CRUD REST API
- 서버 측 Validation
- Optimistic Lock과 Idempotency Key
- 단위·통합 테스트

## Non-goals

- 자연어 해석
- Scheduler 또는 Provider 호출

## Definition of Done

- [ ] 유효·무효 상태 전이 테스트
- [ ] 중복 생성 방지 테스트
- [ ] 동일 요청 재시도 시 같은 결과
- [ ] DB가 Source of Truth

## Recommended commits

- `feat: model reminder lifecycle`
- `feat: expose reminder application API`
