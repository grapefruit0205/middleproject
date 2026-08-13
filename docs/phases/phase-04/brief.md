# Phase 04 Brief: AWS Network and Terraform

## Goal

서울 리전 2-AZ Network, Route, Security Group, 관리 접근을 Terraform으로 정의한다.

## Scope

- VPC, Public/WEB/WAS/DB Subnet
- IGW, Route Table, 환경별 NAT
- Public/Internal ALB Security Group Chain
- SSM Instance Profile
- Backend State 설계와 비용 태그

## Non-goals

- EC2 애플리케이션 배포
- Bastion과 SSH 규칙
- 과도한 Terraform Module 추상화

## Definition of Done

- [ ] `terraform fmt`, `validate` 통과
- [ ] DB Route Table에 인터넷 기본 경로 없음
- [ ] SG에서 Public ALB 외 `0.0.0.0/0` 인바운드 없음
- [ ] 개발/HA NAT 변수가 구분됨

## Recommended commits

- `infra: define two-az network baseline`
- `infra: add least-privilege security group chain`
