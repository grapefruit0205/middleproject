# Phase 18 Brief: Official Public-Transport Integration

## Goal

승인된 서울 열린데이터광장 및 공공데이터포털 TAGO API를 Port/Adapter 뒤에 연결해
지하철, 시내버스, 시외버스, 고속버스 조회를 MCP와 Android에서 제공한다. 공식 조회
API가 확보되지 않은 KTX/SRT/항공/예매 기능은 검증된 공식 웹 예약 화면으로 이관한다.

`provider-contracts.md`가 Phase 18에서 호출할 수 있는 외부 작업의 유일한 allowlist다.

## Scope

- 서울 실시간 지하철 도착, TAGO 지하철 역/시간표, 근처 버스정류소, 버스 도착/노선,
  시외버스 운행 및 고속버스 도착 조회 Adapter
- 교통수단, 출발지, 목적지, 기준일시, 현재 위치에 필요한 명시적 질문과 입력 검증
- 공통 `TransportOption` 계약으로 모드, 경로/노선, 출발/도착 예정시각, 환승,
  선택적 가격, 출처, 조회시각, 만료시각, 공식 예약 링크 정규화
- timeout, rate limit, 인증 실패, 잘못된 Provider JSON/XML, 빈 결과의 typed partial failure
- 응답 종류별 짧은 cache TTL과 Provider provenance
- MCP read-only 도구 및 Device Token으로 보호되는 Android read-only API
- Android foreground 위치 권한, 현재 위치 기반 근처 버스정류소 조회, 수동 입력 fallback
- AWS Secrets Manager의 기존 `reminder-platform/phase18/public-data-api-keys`를 WAS 역할만
  읽도록 하는 Terraform 설정과 bootstrap 환경계약
- Fake contract test, WireMock/MockWebServer 수준의 Adapter test, 명시적으로 제한된 실제 smoke test
- 코레일톡/코레일, SRT, 티머니/고속버스, 항공 공식 예약 화면 HTTPS 링크

## Non-goals

- 자동 예매, 결제, 좌석 확보 주장
- 약관을 위반하는 scraping
- Provider Credential 커밋
- Provider key, 전체 요청 URL, 위도/경도, Device Token을 로그에 기록
- Android APK에 Provider key 포함
- 현재 위치의 백그라운드 수집 또는 저장
- 승인되지 않은 역 좌표를 사용해 가장 가까운 지하철역이라고 추정
- 서울 실시간 지하철의 HTTP-only endpoint를 기본 활성화하거나 키를 평문 전송
- Phase 17 Core Infra PASS 취소

## Security boundary

- 시크릿 JSON 필드는 정확히 `seoulOpenDataKey`, `dataGoKrServiceKey`다.
- Spring Boot는 시크릿을 메모리에서만 읽고 값이나 credential 포함 URL을 로그로 남기지 않는다.
- Terraform state에는 시크릿 값이 들어가면 안 된다. ARN/name과 IAM 권한만 관리한다.
- TAGO는 HTTPS와 Decoding 일반 인증키를 사용한다.
- 공식 서울 실시간 지하철 endpoint는 2026-08-17 확인 시 HTTP만 응답했으므로 Adapter는
  구현하되 `enabled=false`가 기본이다. HTTPS 지원이 확인되기 전 실제 배포에서 켜지 않는다.

## Definition of Done

- [ ] `provider-contracts.md`의 8개 작업만 실제 Provider 호출로 구현한다.
- [ ] 교통수단별 결과를 같은 `TransportOption` 계약으로 반환한다.
- [ ] 시간대와 날짜 경계를 서울 기준으로 검증한다.
- [ ] Provider 일부 실패가 다른 결과를 숨기지 않는다.
- [ ] 실제 조회 결과에 출처와 조회 시각을 표시한다.
- [ ] 키/위치/Device Token이 로그, Git, APK, Terraform state에 없다.
- [ ] Android의 위치 권한 거절 시 수동 출발지 입력으로 정상 동작한다.
- [ ] MCP와 Android API는 read-only 검색과 공식 링크 반환만 수행한다.
- [ ] Terraform fmt/validate/plan과 최소권한 IAM 테스트가 통과한다.
- [ ] Codex 검증 뒤에만 Terraform apply와 실제 Provider smoke test를 수행한다.
- [ ] 공식 API가 없는 경로는 검색 또는 예약 화면으로 이관한다.

## Recommended commits

- `feat(phase-18): integrate official public transport providers`
- `test(phase-18): verify transport adapters and credential boundaries`
