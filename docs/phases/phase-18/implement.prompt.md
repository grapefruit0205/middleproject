# Phase 18 Gemini Implementation Prompt

기준 commit: `<BASE_COMMIT>`

Project invariants, Phase 18 brief, `provider-contracts.md`, 기존 Phase 13~17 Port/Adapter,
MCP, Device Token, Terraform 경계를 읽어라. `provider-contracts.md`의 8개 공식 작업만
Port/Adapter 뒤에 연결하고 각 동작마다 실패하는 테스트를 먼저 실행해 RED를 증명하라.
교통수단별 응답을 공통 `TransportOption`으로 정규화하고 출처, 조회 시각, 만료 시각,
공식 예약 링크를 보존하라. Provider 한 곳의 실패는 typed partial failure로 격리하라.

AWS Secrets Manager 값은 읽거나 출력하지 마라. Terraform에는 기존 시크릿 ARN/name,
WAS 최소권한, bootstrap 환경변수만 추가하고 값이나 SecretString을 state/user-data에 넣지 마라.
서울 실시간 지하철 HTTP-only Adapter는 구현·테스트하되 기본 비활성화하라. Android는
foreground 위치 권한과 수동 fallback만 사용하며 Provider key를 포함하지 않는다.

예약·결제, 좌석 보장, 무단 scraping, Credential 커밋, 미승인 endpoint, 백그라운드 위치,
Terraform apply, AWS mutation을 추가하지 마라. KTX/SRT/항공은 검증된 공식 HTTPS 예약
링크만 반환한다. 관련 backend, Android, plugin, Terraform, runbook 테스트/빌드를 실행하고
변경 때문에 발생한 실패를 수정하라. Git commit이나 push를 실행하지 마라. 실제 secret
value나 device token을 결과에 기록하지 말고 `docs/phases/phase-18/result.md`에 검증 증거와
남은 live smoke 항목을 기록하라.
