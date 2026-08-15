# Phase 13 DeepSeek Implementation Prompt

기준 commit: `<BASE_COMMIT>`

Project invariants, ADR-005, Phase 13 brief를 읽어라. 테스트를 먼저 작성하고 Fake Geocoding/Route Adapter로 자차 출장 Vertical Slice를 구현하라. Provider Port는 Domain/Application과 분리하고 외부 실패를 typed result로 반환하라.

읽기 Provider는 연결 2초, 응답 5초, 최대 1회 지수 백오프 계약을 지켜라. 실제 Credential, 무단 scraping, 예약·결제, AWS 변경을 추가하지 마라. Git commit이나 push를 실행하지 마라. 검증 명령과 결과를 `docs/phases/phase-13/result.md`에 기록하라.
