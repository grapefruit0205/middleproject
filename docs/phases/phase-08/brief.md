# Phase 08 Brief: Reliability and Idempotency

## Goal

재시도, 중복, 부분 실패, 동시 수정을 복구 가능한 상태로 만든다.

## Scope

- API/MCP Idempotency
- SQS 중복 소비 방지
- Retry, DLQ, Reconciliation
- Optimistic Lock
- Failure Scenario Test Matrix

## Non-goals

- Event Sourcing, CQRS, Workflow Engine
- Exactly-once 전송 주장

## Definition of Done

- [ ] Double Submit와 Client Retry 테스트
- [ ] DB 성공/Scheduler 실패 복구 테스트
- [ ] Provider Timeout 후 재처리 테스트
- [ ] Concurrent Update 충돌 테스트
- [ ] DLQ Replay 절차 문서화

## Recommended commits

- `feat: harden idempotent reminder processing`
- `test: cover partial failure recovery`
