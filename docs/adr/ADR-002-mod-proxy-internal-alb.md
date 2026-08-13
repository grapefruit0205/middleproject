# ADR-002: mod_proxy_http와 Internal ALB

- Date: 2026-08-13
- Status: Accepted
- Domain: Architecture
- Impact: High

## Context

Apache와 Tomcat을 Proxy 또는 mod_jk로 연결할 수 있다. 프로젝트는 4주 일정과 15분 발표 안에서 보안과 장애 처리를 설명해야 한다.

## Decision

Apache의 `mod_proxy_http`가 `/api/*`를 Internal ALB DNS로 전달한다. Internal ALB는 HTTP 8080 Tomcat Target Group과 Readiness Health Check를 관리한다.

## Options considered

- `mod_proxy_http + Internal ALB`: HTTP 관측성, Health Check, 설정 단순성
- `mod_jk + Internal NLB`: AJP Secret과 8009 보안, L4 운영이 추가됨
- Apache 직접 Balancing: Internal ALB와 책임이 중복되고 Target 변경을 Apache가 알아야 함

## Consequences

Apache는 WAS 인스턴스 주소를 알지 않는다. Internal ALB가 Target 변경을 처리한다. 교수자가 mod_jk 자체를 요구할 때 새 ADR로 AJP/NLB 전환을 기록한다.
