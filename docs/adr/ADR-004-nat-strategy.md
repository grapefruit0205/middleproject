# ADR-004: 환경별 NAT 전략

- Date: 2026-08-13
- Status: Accepted
- Domain: Architecture
- Impact: Medium

## Context

과제는 NAT Gateway를 요구한다. 단일 NAT는 비용이 낮지만 NAT가 위치한 AZ 장애 시 WEB/WAS의 Outbound가 중단된다. Regional NAT는 워크로드가 사용하는 AZ마다 비용이 발생한다.

## Decision

- 개발/PoC: Single Zonal NAT를 짧은 시간 사용하고 단일 장애점을 문서화한다.
- HA 검증/최종 데모: Regional NAT Gateway Automatic을 사용한다.
- 로컬 개발: NAT를 사용하지 않는다.

## Consequences

팀은 비용과 HA를 환경별로 분리한다. 발표에서는 Regional NAT를 비용 절감 수단으로 설명하지 않는다. Terraform 변수와 비용 태그로 환경 차이를 명시한다.
