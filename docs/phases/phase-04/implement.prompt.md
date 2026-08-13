# Phase 04 DeepSeek Implementation Prompt

기준 commit: `<BASE_COMMIT>`

`project-invariants.md`, Architecture v1.2, ADR-003, ADR-004, Phase 04 brief를 읽어라. `infra/terraform/`과 Phase 결과 문서만 수정하라.

VPC와 네 계층 Subnet, IGW, Route, NAT Profile, SG Chain, SSM Instance Profile을 정의하라. Bastion, SSH 22, DB Internet Route, 무의미한 Module 계층을 만들지 마라. `fmt`, `validate`, 정적 보안 검사를 실행하고 결과를 `docs/phases/phase-04/result.md`에 기록하라. `terraform apply`는 실행하지 마라.
