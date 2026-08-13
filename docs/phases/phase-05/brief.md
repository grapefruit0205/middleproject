# Phase 05 Brief: Apache/Tomcat/RDS 3-Tier Deployment

## Goal

Public ALB, Apache WEB, Internal ALB, 외장 Tomcat WAS, RDS 경로를 두 AZ에 배포한다.

## Scope

- WEB/WAS Launch Template와 Auto Scaling Group
- Apache 2.4 정적 제공과 `mod_proxy_http`
- Java 21, Tomcat 10.1, Spring Boot WAR
- Public/Internal ALB와 Health Check
- RDS PostgreSQL Multi-AZ와 Secret 관리

## Non-goals

- mod_jk/AJP
- Apache가 WAS IP를 직접 관리하는 구성
- Public WEB/WAS IP

## Definition of Done

- [ ] 전체 요청 경로 200
- [ ] `/api/*`만 Internal ALB로 전달
- [ ] WEB/WAS Target이 두 AZ에서 Healthy
- [ ] SSM 접속 가능, SSH 규칙 없음
- [ ] RDS는 WAS SG에서만 접근

## Recommended commits

- `infra: provision apache web tier`
- `infra: provision external tomcat was tier`
- `infra: connect rds multi-az`
