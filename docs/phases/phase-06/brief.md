# Phase 06 Brief: Scheduler Integration

## Goal

Reminder 시각을 EventBridge Scheduler에 등록하고 SQS를 통해 WAS로 전달한다.

## Scope

- Scheduler Port와 AWS Adapter
- `schedule_outbox`
- SQS와 DLQ
- 등록 Retry와 Reconciliation
- Schedule 취소·수정 Version 검증

## Non-goals

- Spring Thread가 시각을 기다리는 구조
- 알림 Provider 전송

## Definition of Done

- [ ] DB Commit 후 Scheduler 실패 복구 테스트
- [ ] 중복 Scheduler 이벤트 무해
- [ ] 취소된 Reminder가 전송되지 않음
- [ ] DLQ와 Alarm 기준 문서화

## Recommended commits

- `feat: persist scheduler outbox`
- `feat: integrate eventbridge scheduler through sqs`
