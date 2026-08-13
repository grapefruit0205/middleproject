# ADR-003: SSM Session Manager, Bastion 제거

- Date: 2026-08-13
- Status: Accepted
- Domain: Security
- Impact: Medium

## Context

Private WEB/WAS 인스턴스를 관리할 방법이 필요하다. Bastion은 Public EC2, SSH 키, 22번 규칙, 패치 대상을 추가한다.

## Decision

WEB/WAS 관리는 SSM Session Manager로 제한한다. 인스턴스에 SSM Agent와 최소 IAM Instance Profile을 부여하고 세션 기록을 CloudWatch Logs 또는 S3에 남긴다.

## Consequences

팀은 Bastion과 SSH 키를 관리하지 않는다. IAM과 MFA가 관리 접근의 통제점이 된다. Port Forwarding 세션은 명령 로그가 남지 않으므로 감사가 필요한 작업은 기본 셸 세션을 사용한다.
