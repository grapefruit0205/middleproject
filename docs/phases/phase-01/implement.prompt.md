# Phase 01 DeepSeek Implementation Prompt

기준 commit: `<BASE_COMMIT>`

먼저 `docs/architecture/project-invariants.md`와 `docs/phases/phase-01/brief.md`를 읽어라. `frontend/`, `backend/`, CI 설정만 변경하라.

Java 21과 Spring Boot 3.5를 사용해 외장 Tomcat 10.1에 배포할 WAR를 만들어라. Embedded Tomcat 전용 JAR 구조로 바꾸지 마라. Readiness Endpoint와 최소 빌드 테스트를 추가하라.

빌드와 테스트 명령을 실행하고 Exit Code를 `docs/phases/phase-01/result.md`에 기록하라. Architecture 변경이 필요하면 구현을 중단하고 이유를 보고하라.
