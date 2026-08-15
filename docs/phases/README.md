# Phase Roadmap and Prompt Contract

## File roles

- `brief.md`: Codex가 승인한 구현 계약
- `implement.prompt.md`: OpenCode CLI의 DeepSeek에 전달하는 실행 프롬프트
- `review.template.md`: Codex 검토 형식
- `result.template.md`: 구현자가 남길 검증 증거 형식

`brief.md`, `review.md`, `result.md`는 실행 프롬프트가 아니다. DeepSeek가 직접 실행하는 문서는 `implement.prompt.md`다.

## Phase map

| Phase | Goal | Depends on |
|---|---|---|
| 00 | Architecture와 작업 규약 | 없음 |
| 01 | Local Application Foundation | 00 |
| 02 | Reminder Core와 Calendar | 01 |
| 03 | Natural Language Parsing | 02 |
| 04 | AWS Network와 Terraform | 00 |
| 05 | Apache/Tomcat/RDS 3-Tier 배포 | 01, 04 |
| 06 | Scheduler Integration | 02, 05 |
| 07 | Notification Delivery | 06 |
| 08 | Reliability와 Idempotency | 06, 07 |
| 09 | MCP Adapter | 02, 08 |
| 10 | Observability와 Security | 05, 08 |
| 11 | HA Test, Demo, Portfolio | 03, 09, 10 |
| 12 | Trip Domain & MCP Foundation | 08, 09, 10 |
| 13 | Private Car Vertical Slice | 12 |
| 14 | Travel Context & Recommendations | 12, 13 |
| 15 | Private ChatGPT Plugin | 12, 13, 14 |
| 16 | Android Companion | 12, 15 |
| 17 | AWS 3-Tier E2E & Evidence | 13, 14, 15, 16, 기존 11 |
| 18 | Real Intercity Transport Providers | 14, 17 |

## Handoff rule

각 구현 프롬프트의 `<BASE_COMMIT>`을 실행 전 실제 SHA로 바꾼다. DeepSeek는 허용 경로 밖의 변경이 필요하면 코드를 수정하지 않고 이유를 보고한다.

Phase 12~18은 `tools/orchestration/phases-12-plus.json`을 사용한다. CMDC 구현 모델은 모든 Phase에서 `deepseek/deepseek-v4-flash`를 사용한다. effort는 `max`, 최초·수정 시도는 각각 100턴이며 Phase당 최대 2회다. Codex 메인이 각 Phase를 `gpt-5.6-sol` / `high`로 독립 검증하며 상태는 `.orchestration/phase-12-18-state.json`에 분리한다.

## Shared review template

Codex는 [review.template.md](review.template.md)를 복사해 각 Phase의 `review.md`를 만든다. 구현자는 [result.template.md](result.template.md)를 복사해 `result.md`를 만든다.
