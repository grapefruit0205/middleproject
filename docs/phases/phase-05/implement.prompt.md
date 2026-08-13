# Phase 05 DeepSeek Implementation Prompt

기준 commit: `<BASE_COMMIT>`

`docs/architecture/project-invariants.md`, Architecture v1.2, ADR-001, ADR-002, Phase 05 brief를 읽어라. Apache는 React 정적 파일과 `/api/*` Reverse Proxy만 담당한다. Proxy 대상은 Internal ALB DNS다. WAS는 Java 21과 외장 Tomcat 10.1에서 `ROOT.war`를 실행한다.

User Data에 Secret을 쓰지 마라. Launch Template와 ASG를 사용하고 Health Check 경로를 분리하라. Terraform Plan과 구성 검사를 실행하되 사용자 승인 없는 Apply는 하지 마라. 결과와 수동 검증 절차를 `result.md`에 기록하라.
