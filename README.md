# Reliable Multi-Channel Reminder Platform

자연어로 일정과 알림 정책을 만들고, 예약·전송·확인 상태를 추적하는 4인 팀 프로젝트다.

현재 단계는 **Phase 0: Architecture & Project Planning**이다. 애플리케이션 코드와 Terraform은 아직 작성하지 않는다.

## 승인된 Architecture v1.2

```text
User
-> Public ALB
-> Apache WEB Tier
-> Internal ALB
-> External Tomcat WAS Tier
-> RDS PostgreSQL Multi-AZ
```

- WEB: EC2 Auto Scaling Group, Apache HTTP Server 2.4, React/PWA
- WAS: EC2 Auto Scaling Group, Java 21, Tomcat 10.1, Spring Boot 3.5 WAR
- WEB-WAS 연동: `mod_proxy_http`와 Internal ALB
- 관리 접근: SSM Session Manager, SSH/Bastion 없음
- NAT: 개발 Single Zonal NAT, HA 검증 Regional NAT Gateway Automatic
- 알림: EventBridge Scheduler, SQS/DLQ, SES, Push Provider

## 문서 시작점

- [Architecture v1.2](docs/architecture/architecture-v1.2.md)
- [Project Invariants](docs/architecture/project-invariants.md)
- [Architecture Decisions](docs/adr/README.md)
- [Phase Roadmap and Prompt Contract](docs/phases/README.md)

Git 문서가 기술적 Source of Truth다. Notion은 같은 내용을 팀이 탐색하고 실행하기 위한 프로젝트 허브로 사용한다.
