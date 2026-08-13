# Phase 07 DeepSeek Implementation Prompt

기준 commit: `<BASE_COMMIT>`

`project-invariants.md`와 Phase 07 brief를 읽어라. Business Logic은 NotificationSender Port만 호출하게 하고 SES/Push SDK 코드를 Infrastructure Adapter에 둬라. 전송 시도, Provider Message ID, 오류 분류를 DB에 남겨라.

SMS를 구현하지 마라. Provider Secret을 코드와 Git에 넣지 마라. Mock 테스트와 허용된 Sandbox 테스트 결과를 `result.md`에 기록하라.
