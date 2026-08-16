# Phase 17 Architecture Review v2

## 검토 대상

- 시각 설계: `docs/architecture/phase-17-architecture-visual-spec-v2.md`
- 편집 원본: `docs/architecture/phase-17-aws-three-tier-v2.drawio`
- 발표용 렌더링: `docs/architecture/phase-17-aws-three-tier-v2.png`
- 통합/운영 렌더링: `docs/architecture/phase-17-aws-three-tier-operations-v2.png`
- 사실 원천: `infra/terraform/*.tf`, `infra/terraform/templates/*`

## Context7 설계 근거

Context7 MCP에서 `AWS Architecture Icons`를 resolve한 결과, High reputation의 `/awslabs/aws-icons-for-plantuml`을 선택했다. 다음 다섯 주제를 각각 `query-docs`로 조회했다.

1. AWS Cloud, VPC, Availability Zone, public/private subnet의 중첩 경계
2. ALB, EC2 Auto Scaling, RDS Multi-AZ 관계
3. EventBridge/SQS/DLQ 비동기 흐름
4. Secrets Manager/IAM/KMS/Systems Manager/CloudWatch 운영·보안 표현
5. 공식 AWS 아이콘 이름, 그룹, 레이아웃 규칙

조회 결과에서 적용한 핵심 원칙:

- `AWS Cloud → VPC → Availability Zone → Subnet` 순서의 중첩 그룹
- AZ마다 subnet과 instance를 실제 배치에 맞게 포함
- ASG는 두 AZ의 instance를 하나의 논리 그룹으로 묶음
- 직교 연결선과 일관된 흐름 방향
- 공식 AWS Architecture Icons와 AWS 계열 색상 사용

Context7 예시는 표현 근거로만 사용했고, 실제 리소스·포트·CIDR은 Terraform으로 확인했다.

## Terraform 대조 결과

### Page 1

- Region `ap-northeast-2`, VPC `10.20.0.0/16`
- `ap-northeast-2a`, `ap-northeast-2c`
- Public/WEB/WAS/DB subnet 8개와 CIDR
- Internet Gateway, Regional NAT Gateway
- Public ALB, Internal ALB
- WEB ASG와 Apache EC2 2대
- WAS ASG와 Spring Boot/Tomcat EC2 2대
- RDS PostgreSQL 16 Multi-AZ, endpoint, active primary, standby
- ACM certificate

애플리케이션 연결은 `WAS → RDS endpoint → active primary`로 표현했다. standby에는 synchronous replication 관계만 있으며 애플리케이션 직접 연결선은 없다.

### Page 2

- OpenAI Secure MCP Tunnel과 WEB loopback endpoint
- public `/api/mcp = 403`
- EventBridge Scheduler, encrypted reminder SQS, encrypted DLQ
- Firebase Cloud Messaging과 Android push
- S3 artifact bucket과 별도 ALB access-log bucket
- Secrets Manager, IAM, KMS
- Systems Manager Session Manager, CloudWatch, ACM

## 누락 및 추정 검사

- 요구 라벨 누락: 0
- Terraform에 없는 서비스 추가: 0
- Route 53 대신 외부 Gabia DNS 사용
- CloudFront, WAF, Cognito, ECS/Fargate, EKS, Aurora, Lambda, OpenTelemetry Collector 미포함
- account ID, API key, private key marker 미포함

## XML 및 이미지 검사

| 검사 | 결과 |
|---|---:|
| Draw.io pages | 2 |
| 전체 `mxCell` | 152 |
| 연결선 | 32 |
| 깨진 edge source/target | 0 |
| 내장 AWS SVG image cell | 28 |
| 복원 불가능한 SVG | 0 |
| 요구 라벨 누락 | 0 |
| 금지 서비스 | 0 |
| 민감정보 marker | 0 |
| Page 1 PNG | 1920×1080 |
| Page 2 PNG | 1920×1080 |

공식 AWS 2026-07-31 SVG를 percent-encoded data URI로 Draw.io 파일 안에 포함했다. 외부 이미지 URL에 의존하지 않는다.

## 1차 시각 검토

발견한 문제:

- Page 1 상단 AWS/Region 제목이 Internet Gateway/NAT 카드와 가까웠다.
- ALB/ASG의 긴 그룹명이 subnet 제목 및 연결선 라벨과 경쟁했다.
- 오른쪽 Public/WAS/DB subnet 제목 일부가 중앙 ALB/RDS 요소 아래로 들어갔다.
- Internal ALB와 WAS 카드 사이 간격이 좁아 `:8080` 라벨이 붙어 보였다.

수정 내용:

- 상단 서비스 카드를 오른쪽으로 이동해 AWS/Region 제목 영역을 확보했다.
- 긴 그룹 제목을 제거하고 중앙의 짧은 Multi-AZ/ASG 배지로 교체했다.
- subnet 제목을 자동 그룹 제목에서 독립 좌·우 정렬 텍스트로 전환했다.
- 핵심 화살표 라벨을 흰색 독립 슬롯으로 배치했다.
- Internal ALB 높이와 WAS 시작 위치를 조정해 실제 간격을 만들었다.

## 2차 시각 검토

- PASS — AWS/Region/VPC/AZ/subnet 계층이 분리되어 보인다.
- PASS — Public, WEB, WAS, DB tier가 색과 위치로 구분된다.
- PASS — 핵심 요청 경로가 가장 굵고 먼저 보인다.
- PASS — subnet 제목이 중앙 ALB, ASG, RDS 카드와 겹치지 않는다.
- PASS — 아이콘과 텍스트가 잘리거나 서로 덮이지 않는다.
- PASS — Private MCP, reminder/push, operations 영역이 서로 독립적이다.
- PASS — 핵심 흐름의 연결선이 다른 카드 내부를 관통하지 않는다.
- PASS — 1920×1080 발표 화면에서 서비스명과 포트를 읽을 수 있다.

## 남아 있는 제한사항

- PNG는 Draw.io Desktop이 설치되지 않은 환경에서 동일 좌표·동일 공식 SVG를 사용하는 SVG preview를 Microsoft Edge headless로 렌더링했다.
- Draw.io는 파일을 연 뒤 사용자가 도형을 이동하면 자체 orthogonal router가 연결선의 일부 waypoint를 다시 계산할 수 있다.
- route table, security group, launch template, instance profile의 개별 box는 발표용 가독성을 위해 의도적으로 생략했다. 정확한 세부값은 Terraform이 사실 원천이다.
