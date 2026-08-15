# Phase 13 Brief: Private Car Vertical Slice

## Goal

자차 출장 대화에서 출발지, 출발 시각, 목적지를 확정하고 경로와 출발 알림을 저장한다.

## Scope

- 자차 이동 질문 상태와 누락 정보 계산
- Geocoding과 Route Provider Port
- Fake Provider 기반 거리, 예상 시간, 통행료 결과
- 교통량을 반영한 권장 출발 시각
- 경로 선택과 Reminder 정책 확정
- Provider timeout, rate limit, 빈 결과, 잘못된 응답 처리
- 조회 출처와 조회·만료 시각 저장

## Non-goals

- 실제 TMAP/Kakao Credential
- 대중교통 시간표와 예약
- 숙박, 맛집, 명소 추천
- Android와 AWS 배포

## Definition of Done

- [ ] 누락된 출장 정보에 따라 한 번에 한 질문을 반환한다.
- [ ] Fake Route Provider로 자차 경로와 권장 출발 시각을 계산한다.
- [ ] 사용자가 확인한 경로와 Reminder만 PostgreSQL에 확정한다.
- [ ] Provider 실패가 저장된 Trip 상태를 손상시키지 않는다.
- [ ] Provider 결과에 출처와 만료 시각이 포함된다.

## Recommended commits

- `feat: plan private car trips`
- `test: handle route provider failures`
