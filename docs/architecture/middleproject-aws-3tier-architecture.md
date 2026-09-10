# Middleproject AWS 3-Tier 다이어그램

이 문서는 현재 저장소의 Terraform을 기준으로 작성한 아키텍처 도식의 범위와 읽는 순서를 설명합니다. 현재 실행 중인 AWS 배포 상태나 AWS Well-Architected 인증·준수 판정을 의미하지 않습니다.

## 산출물

- **현재 권장 문서:** [Architecture Atlas](./architecture-atlas.html) — 브라우저에서 직접 열 수 있는 독립 HTML 파일. 외부 스크립트·폰트·네트워크 호출 없이 실행됩니다.
- [전체 흐름 이미지](./atlas/page-01.png) / [통합 상세 이미지](./atlas/page-06.png): 그림 아래 한글 해설까지 포함한 화면 이미지.
- [전체 흐름 SVG](./atlas/page-01.svg) / [통합 상세 SVG](./atlas/page-06.svg): 확대해도 선명한 벡터 파일. HTML의 `SVG 저장`으로 각 도식도 저장할 수 있습니다.
- 이전 형식 보관: [draw.io 원본](./middleproject-aws-3tier-architecture.drawio), [AWS Diagram MCP 개요 PNG](./middleproject-aws-3tier-vertical-overview.png). 최신 시각 구성은 HTML·SVG를 기준으로 읽습니다.

화살표 번호는 각 페이지 아래 한글 해설과 연결됩니다. 주황 실선은 요청·데이터, 보라 파선은 비동기, 초록 파선은 배포·네트워크, 분홍 점선은 보안·관측 지원을 나타냅니다. 같은 역할의 인스턴스에 적용되는 지원 연결은 대표선으로 묶었습니다.

## 페이지 구성

| 페이지 | 용도 |
| --- | --- |
| 01 전체 흐름 | 발표·설명용 세로형 핵심 경로 |
| 02 Internet → WEB | Public ALB, ACM, NAT, WEB ASG와 경계 통제 |
| 03 WEB → WAS | Apache reverse proxy, Internal ALB, WAS ASG와 SG 포트 |
| 04 WAS → Data · Async | RDS Multi-AZ, Flyway, Secrets, Scheduler, SQS/DLQ, SES |
| 05 Well-Architected | 여섯 원칙별 소스에서 확인된 통제와 보완점 |
| 06 통합 상세도 | 01 페이지의 세로 축에 02~04 페이지의 구성을 모두 겹친 검토용 단일 화면 |

## 사용과 확인

왼쪽 메뉴에서 페이지를 바꾸고, `원본 크기`로 확대하거나 `SVG 저장`으로 도식만 내려받을 수 있습니다. `인쇄`는 현재 페이지를 인쇄합니다. 작은 화면에서는 도식 내부를 이동해 읽고 한글 해설은 한 열로 표시합니다. 5페이지는 HTML 비교표이므로 SVG 저장 대신 인쇄나 제공된 PNG를 사용합니다.

2026-09-10: 실제 Chromium에서 6개 화면을 렌더링하고 시각 확인했습니다. 페이지 전환·확대 동작, 1440px/390px 문서 가로 넘침 없음, JavaScript 예외 없음이 확인되었습니다. 그림의 기호는 서비스 이름을 명시한 자체 벡터 디자인입니다.

## 현재 소스에서 확인한 핵심 구조

- 서울 리전의 VPC와 두 Availability Zone
- Public, private WEB, private WAS, isolated DB의 네 subnet tier
- `User → Public ALB → WEB ASG → Internal ALB → WAS ASG → RDS PostgreSQL` 경로
- WEB/WAS ASG의 두 AZ 분산과 RDS Multi-AZ 기본값
- Security Group 체인: `443 → 80 → 80 → 8080 → 5432`
- IAM 역할 분리, Secrets Manager, SSM, 저장 데이터 암호화, CloudWatch 로그·알람
- EventBridge Scheduler, SQS, DLQ, Outbox/idempotency, 선택적 SES 발송

근거 소스:

- [`infra/terraform/main.tf`](../../infra/terraform/main.tf)
- [`infra/terraform/tier.tf`](../../infra/terraform/tier.tf)
- [`infra/terraform/security.tf`](../../infra/terraform/security.tf)
- [`infra/terraform/observability.tf`](../../infra/terraform/observability.tf)

## 아직 배포 또는 운영 증거가 확인되지 않은 항목

- AWS WAF
- VPC endpoints
- 지표 기반 Auto Scaling 정책과 검증된 부하 기준선
- 복원·장애 조치 시험, 명시적 RTO/RPO, DR game day

## AWS 공식 기준

- [AWS Well-Architected Framework — The pillars](https://docs.aws.amazon.com/wellarchitected/latest/framework/the-pillars-of-the-framework.html)
- [Reliability Pillar — Workload architecture](https://docs.aws.amazon.com/wellarchitected/latest/reliability-pillar/workload-architecture.html)
- [Reliability Pillar — Use fault isolation to protect your workload](https://docs.aws.amazon.com/wellarchitected/latest/reliability-pillar/use-fault-isolation-to-protect-your-workload.html)
- [Reliability Pillar — Shared responsibility model for resiliency](https://docs.aws.amazon.com/wellarchitected/latest/reliability-pillar/shared-responsibility-model-for-resiliency.html)
