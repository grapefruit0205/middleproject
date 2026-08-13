# ADR-001: EC2 기반 Apache WEB과 외장 Tomcat WAS

- Date: 2026-08-13
- Status: Accepted
- Domain: Architecture
- Impact: High

## Context

과제 자료는 Apache 2.4 설치, Tomcat/OpenJDK 설치, WEB-WAS 연동을 평가 범위로 제시한다. 컨테이너는 실행 환경을 단순화하지만 서버 설치와 프로세스 운영 증거를 가린다.

## Decision

WEB과 WAS를 별도 EC2 Auto Scaling Group으로 운영한다. WEB에는 Apache 2.4와 React/PWA를 배치한다. WAS에는 Java 21, 외장 Tomcat 10.1, Spring Boot WAR를 배치한다.

## Options considered

- ECS Fargate: 호스트 운영 부담은 적지만 과제의 서버 구축 증거가 약하다.
- EC2 Embedded Tomcat JAR: Tomcat을 사용하지만 외장 WAS 구축 요구를 충분히 보여주지 못한다.
- EC2 External Tomcat WAR: 평가 항목과 요청 경로를 직접 보여준다.

## Consequences

팀은 OS 패치, Launch Template, 서비스 시작, 로그 수집을 관리한다. 대신 WEB/WAS 역할, 포트, Health Check, 장애 교체를 발표에서 증명할 수 있다.
