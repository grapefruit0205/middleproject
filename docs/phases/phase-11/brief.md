# Phase 11 Brief: HA Test, Final Demo, Portfolio

## Goal

2-AZ 장애 대응과 핵심 사용자 흐름을 검증하고 15분 발표 증거를 고정한다.

## Scope

- WEB/WAS 단일 인스턴스 장애 테스트
- RDS Multi-AZ 장애 조치 관찰
- Scheduler/Provider 실패와 복구
- 비용 Snapshot과 Resource Cleanup
- README, Diagram, Demo Script, Evidence

## Non-goals

- 무중단 보장 주장
- Multi-Region DR
- 발표 직전 신규 기능

## Definition of Done

- [ ] 실패 전·중·후 Timestamp와 로그 증거
- [ ] RTO/RPO 관찰값과 한계 기록
- [ ] 15분 리허설 완료
- [ ] 비용과 삭제 대상 확인
- [ ] Codex 최종 PASS

## Recommended commits

- `test: capture two-az failure evidence`
- `docs: finalize demo and portfolio narrative`
