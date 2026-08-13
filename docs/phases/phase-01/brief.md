# Phase 01 Brief: Local Application Foundation

## Goal

React/PWA와 Spring Boot WAR가 로컬 Apache 및 외장 Tomcat 환경에서 실행되는 최소 기반을 만든다.

## Scope

- Frontend와 Backend 디렉터리
- Java 21, Spring Boot 3.5, WAR 패키징
- 외장 Tomcat 배포 가능한 `SpringBootServletInitializer`
- `/actuator/health/readiness`
- 로컬 PostgreSQL과 기본 CI

## Non-goals

- Reminder Business Logic
- AWS 배포
- Embedded Tomcat만 사용하는 배포

## Definition of Done

- [ ] WAR 빌드 성공
- [ ] 외장 Tomcat에서 Health Endpoint 200
- [ ] Frontend 빌드 성공
- [ ] Secret 없는 `.env.example`

## Recommended commits

- `build: create frontend and backend foundations`
- `feat: support external tomcat war deployment`
