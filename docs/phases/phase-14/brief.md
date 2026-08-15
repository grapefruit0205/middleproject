# Phase 14 Brief: Travel Context and Recommendations

## Goal

출장 출발 시각과 목적지에 맞춘 날씨, 준비물, 숙박, 맛집, 명소 후보를 출처와 함께 제공한다.

## Scope

- Weather, Place Search Provider Port와 Fake Adapter
- 당일 출발과 전일 출발의 날씨 범위 계산
- 날씨 기반 준비물 규칙
- 전일 출발과 장거리 조건의 숙박 추천
- 출장 후 일정 제안과 사용자 동의 흐름
- 거리, 가격, 평점 기준 정렬
- 부분 성공 결과와 Provider provenance

## Non-goals

- 예약, 결제, 자동 구매
- 광고 순위 조작과 무단 scraping
- 실제 교통 시간표 Provider
- Plugin 패키징과 Android

## Definition of Done

- [ ] 출발일 규칙에 맞는 날씨 날짜를 조회한다.
- [ ] 장거리 전일 출발일 때만 숙박 후보를 제안한다.
- [ ] 사용자가 후속 일정을 거절하면 추천 Tool 호출을 중단한다.
- [ ] Provider 일부 실패 시 성공 결과와 실패 사유를 함께 반환한다.
- [ ] 추천 결과에 Provider, 조회 시각, 평점 또는 가격의 출처를 표시한다.

## Recommended commits

- `feat: add trip context recommendations`
- `test: preserve partial provider results`
