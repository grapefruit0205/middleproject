# Phase 07 Brief: Notification Delivery

## Goal

Email을 전송하고 Push를 추가할 수 있는 Provider 경계를 만든다.

## Scope

- NotificationSender Port
- SES Email Adapter
- Push Adapter
- Attempt와 Provider Response 영속화
- Retry 가능한 오류 분류

## Non-goals

- SMS
- Provider 성공 응답을 User ACK로 취급

## Definition of Done

- [ ] Email 성공·실패 테스트
- [ ] Provider Timeout 기록
- [ ] Attempt마다 Correlation ID 존재
- [ ] Push 비활성화 시 Email 경로에 영향 없음

## Recommended commits

- `feat: add notification provider boundary`
- `feat: deliver email through ses`
