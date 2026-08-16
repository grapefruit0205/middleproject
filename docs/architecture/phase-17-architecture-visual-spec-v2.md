# Phase 17 Architecture Visual Specification v2

## 목적과 독자

이 다이어그램은 Trip Copilot의 AWS 3-Tier 고가용성 구조를 발표 화면에서 10초 안에 설명하기 위한 자료다. 주 독자는 프로젝트 평가자, AWS 인프라 검토자, 애플리케이션 개발자다. 세부 설정을 전부 나열하기보다 배치 경계, 핵심 요청 경로, 비공개 MCP, 비동기 알림, 운영 통제를 정확하고 읽기 쉽게 전달한다.

Terraform 코드가 단일 사실 원천이다. 이 문서는 `infra/terraform`을 시각적으로 표현하는 계약이며 Terraform에 없는 리소스를 추가하지 않는다.

## Context7에서 확인한 표현 원칙

Context7의 `/awslabs/aws-icons-for-plantuml` 자료를 주제별로 조회했다.

- AWS Cloud 안에 VPC를 두고, VPC 안에 Availability Zone과 subnet을 중첩한다.
- AZ마다 public/private subnet을 명시하고 NAT/instance를 해당 경계 안에 둔다.
- Auto Scaling Group은 AZ별 EC2 instance를 하나의 논리 그룹으로 묶는다.
- 직교 연결선(`linetype ortho`)과 왼쪽→오른쪽 또는 위→아래의 일관된 방향을 사용한다.
- 공식 AWS Architecture Icons의 서비스·리소스 아이콘과 AWS 계열 색상을 사용한다.

Context7는 표현 방식의 근거로만 사용한다. 실제 서비스 존재 여부, 포트, CIDR, 보안 관계는 Terraform에서 확인한다.

## 페이지 구성

### Page 1 — AWS 3-Tier Overview

전달 메시지: 두 Availability Zone에 WEB/WAS가 분산되고, 격리된 Multi-AZ PostgreSQL까지 단계적으로 접근하는 고가용성 3-Tier 구조다.

- 16:9, 1920×1080 가로 캔버스
- 외부 이용자는 상단 왼쪽, Gabia DNS는 상단 중앙에 둔다.
- AWS Cloud → Seoul Region → VPC의 중첩 경계를 사용한다.
- AZ는 좌우 두 열, tier는 위에서 아래 네 행으로 정렬한다.
- Public, WEB private, WAS private, DB isolated subnet의 CIDR을 모두 표기한다.
- Public ALB와 Internal ALB는 양쪽 AZ에 걸쳐 있음을 가로 그룹으로 표현한다.
- WEB/WAS ASG는 양쪽 AZ의 EC2를 하나의 점선 그룹으로 묶는다.
- RDS endpoint는 애플리케이션 접속점으로 별도 표기하고 active primary에만 연결한다.
- standby에는 동기 복제선만 연결한다.
- Page 1의 굵은 선은 사용자 요청 경로만 표현한다.

핵심 요청 경로:

`Browser/PWA 또는 Android → Gabia DNS → Public ALB :443 → Apache WEB :80 → Internal ALB :80 → Spring Boot WAS :8080 → RDS endpoint → PostgreSQL :5432`

### Page 2 — MCP, Reminder and Operations

전달 메시지: 공개 MCP 포트를 만들지 않으면서 ChatGPT tool call을 처리하고, 일정 기반 알림을 SQS로 신뢰성 있게 전달하며, AWS 운영 서비스로 통제한다.

세 개의 독립적인 가로 영역으로 구성한다.

1. Private MCP
   - ChatGPT → OpenAI Secure MCP Tunnel → WEB tunnel-client → loopback Apache → Internal ALB → WAS MCP adapter
   - WEB → OpenAI 연결은 outbound-only로 표기한다.
   - `Public /api/mcp = 403` 보안 배지를 배치한다.
2. Reminder and Push
   - WAS → EventBridge Scheduler → reminder SQS → WAS consumer → Firebase Cloud Messaging → Android
   - reminder SQS → DLQ 실패 경로를 아래쪽 짧은 화살표로 분리한다.
   - WAS consumer → PostgreSQL delivery state를 보조 관계로 표시한다.
3. Operations and Security
   - Delivery assets: artifact S3, ALB access-log S3
   - Runtime security: Secrets Manager, IAM, KMS
   - Operations: Systems Manager, CloudWatch, ACM
   - 관리·관측·secret 관계는 점선으로 표시한다.

## 실제 컴포넌트

### Network and Compute

- AWS Region `ap-northeast-2`
- VPC `10.20.0.0/16`
- Internet Gateway
- Regional NAT Gateway
- Public ALB, Internal ALB
- WEB ASG: Apache EC2 2대, min/desired/max `2/2/2`
- WAS ASG: Spring Boot/Tomcat EC2 2대, min/desired/max `2/2/2`

### Subnets

| Tier | ap-northeast-2a | ap-northeast-2c |
|---|---|---|
| Public | `10.20.0.0/24` | `10.20.1.0/24` |
| WEB private | `10.20.10.0/24` | `10.20.11.0/24` |
| WAS private | `10.20.20.0/24` | `10.20.21.0/24` |
| DB isolated | `10.20.30.0/24` | `10.20.31.0/24` |

### Data, Integration, Security and Operations

- RDS PostgreSQL 16 Multi-AZ
- EventBridge Scheduler schedule group
- encrypted reminder SQS와 DLQ, `maxReceiveCount = 5`
- S3 artifact bucket과 별도 ALB access-log bucket
- Secrets Manager: DB, Firebase Admin, tunnel runtime secret
- IAM: WEB, WAS, Scheduler scoped roles
- KMS/service-managed encryption at rest
- Systems Manager Session Manager, inbound SSH 없음
- CloudWatch log group 5개와 alarm 10개
- ACM certificate for `trip.tripjunseok.site`

### External

- Browser/PWA, Android
- Gabia DNS
- ChatGPT/OpenAI Secure MCP Tunnel
- Google Firebase Cloud Messaging

## 스타일 토큰

| 용도 | 값 |
|---|---|
| 배경 | `#F7F9FC` |
| 제목 | `#161E2D` |
| 기본 텍스트/연결선 | `#232F3E` |
| AWS/강조 | `#FF9900` |
| Cloud/Region 경계 | `#7D8998`, `#D5DBDB` |
| VPC | `#248814`, fill `#F4FBF4` |
| Public subnet | `#147EBA`, fill `#EAF5FB` |
| WEB subnet | `#2E8540`, fill `#EDF8EF` |
| WAS subnet | `#D86613`, fill `#FFF3E8` |
| DB subnet | `#8C4FFF`, fill `#F5EFFF` |
| MCP | `#7C3AED` |
| Reminder | `#C026D3` |
| Push | `#F57C00` |
| 운영 관계 | `#64748B`, 점선 |

- AWS 아이콘은 56–64px로 유지한다.
- 서비스명은 최대 두 줄, 아이콘 아래 중앙 정렬한다.
- 긴 설명문과 큰 메모 상자는 사용하지 않는다.
- 핵심 흐름은 3px 실선, 보조 흐름은 2px 실선, 운영 관계는 1.5px 점선이다.
- 선은 직교하고 컴포넌트나 라벨을 관통하지 않는다.
- 그림 가장자리 36px 이상, 주요 그룹 사이 24px 이상의 여백을 유지한다.

## 의도적 생략

- Terraform resource ID, ARN, 계정 ID, secret 값
- route table과 security group의 개별 resource box
- launch template, instance profile의 개별 box
- health check와 alarm의 세부 threshold
- Route 53, CloudFront, WAF, Cognito, ECS/Fargate, EKS, Aurora, Lambda, OpenTelemetry Collector

보안 그룹 관계는 핵심 경로의 포트 라벨로 요약한다. 세부 구현은 Terraform과 별도 요구사항 문서에 남긴다.

## 생성 후 검증 체크리스트

- [ ] 두 페이지 모두 1920×1080이며 목적이 명확히 다르다.
- [ ] AWS Cloud → Region → VPC → AZ → subnet 계층이 즉시 보인다.
- [ ] 모든 subnet CIDR과 AZ가 정확하다.
- [ ] Public/Internal ALB와 WEB/WAS ASG가 두 AZ에 걸쳐 표현된다.
- [ ] RDS standby로 향하는 애플리케이션 접속선이 없다.
- [ ] 핵심 경로가 다른 관계보다 먼저 보인다.
- [ ] MCP, reminder/DLQ, FCM 흐름이 서로 교차하지 않는다.
- [ ] 운영 관계는 점선이며 핵심 흐름을 가리지 않는다.
- [ ] AWS 공식 SVG가 파일 안에 내장되어 있다.
- [ ] 텍스트 잘림, 겹침, 선 관통이 없다.
- [ ] 금지 서비스와 비밀정보가 없다.
- [ ] Draw.io XML의 모든 edge source/target이 유효하다.
