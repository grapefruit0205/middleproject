# Phase 06 DeepSeek Implementation Prompt

기준 commit: `<BASE_COMMIT>`

`project-invariants.md`와 Phase 06 brief를 읽어라. Scheduler 호출을 Adapter로 분리하고 Reminder 저장과 Outbox를 한 트랜잭션에 넣어라. EventBridge Scheduler는 SQS를 호출하고 WAS 소비자는 ID, Version, Idempotency Key를 검증해야 한다.

애플리케이션 Thread나 Timer로 대기하지 마라. AWS 실패 테스트는 Mock과 제한된 PoC를 분리하라. 실행 결과를 `result.md`에 기록하라.
