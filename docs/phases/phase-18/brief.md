# Phase 18 Brief: Real Intercity Transport Providers

## Goal

승인된 공식 API를 사용해 KTX/SRT, 고속버스, 항공 시간표와 가격 조회를 Provider Adapter로 추가한다.

## Scope

- 철도, 버스, 항공 Provider Port와 Adapter
- 교통수단 선택에 따른 질문과 검색 조건
- 출발·도착 시각, 환승, 가격 정규화
- timeout, rate limit, 인증 실패, 빈 결과 처리
- Provider provenance와 cache TTL
- Fake contract test와 제한된 실제 Provider test
- 자차 Provider와 같은 Application Service 조합

## Non-goals

- 자동 예매, 결제, 좌석 확보 주장
- 약관을 위반하는 scraping
- Provider Credential 커밋
- Phase 17 Core Infra PASS 취소

## Definition of Done

- [ ] 교통수단별 결과를 같은 Route Option 계약으로 반환한다.
- [ ] 시간대와 날짜 경계를 서울 기준으로 검증한다.
- [ ] Provider 일부 실패가 다른 결과를 숨기지 않는다.
- [ ] 실제 조회 결과에 출처와 조회 시각을 표시한다.
- [ ] 공식 API가 없는 경로는 검색 또는 예약 화면으로 이관한다.

## Recommended commits

- `feat: add intercity transport providers`
- `test: normalize transport provider results`
