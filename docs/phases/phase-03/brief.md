# Phase 03 Brief: Natural Language Parsing

## Goal

자연어를 구조화된 Reminder Command로 바꾸고 서버 규칙으로 검증한다.

## Scope

- Parser Port와 Provider Adapter
- JSON Schema 기반 결과
- 시간대와 모호한 날짜 처리
- Parser 실패와 Business Validation 실패 분리
- Fixture 기반 테스트

## Non-goals

- LLM 응답을 저장 성공으로 취급
- Provider 종속 로직을 Domain에 배치

## Definition of Done

- [ ] 정상·모호·잘못된 입력 Fixture
- [ ] 서울 시간대 변환 검증
- [ ] Parser 실패가 DB 저장을 만들지 않음
- [ ] 구조화 결과를 사용자가 확인 가능

## Recommended commits

- `feat: add structured reminder parser port`
- `test: cover ambiguous natural language input`
